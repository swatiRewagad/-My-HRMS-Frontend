package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for the SLA breach sweep.
 *
 * <p><b>Why unit level and what that does NOT cover.</b> cms-backend has no H2 on its test classpath
 * (it builds against {@code spring-boot-starter-parent} directly, not the CMS reactor root whose
 * {@code dependencyManagement} merely pins an H2 version) and it has no {@code @SpringBootTest} at
 * all — the single {@code @DataJpaTest} in the module is an {@code *IT} pointed at a real MySQL. So
 * the row-filtering done by {@code findSlaBreachCandidates}'s SQL cannot be executed here, and these
 * tests deliberately do not pretend otherwise: they pin the behaviour at the service seam, which is
 * where a future edit would actually break it.
 *
 * <p>Concretely, "a closed row is never escalated" and "a NULL-deadline row is skipped" are enforced
 * by the query's WHERE clause. What is pinned here is that the service hands that query the right
 * ingredients — the canonical LOWERCASE vocabulary rather than a hardcoded or uppercase list — and
 * that the sweep survives a deadline-less row if the predicate is ever loosened. The predicate itself
 * was verified by measurement against cms_db: it selects 518 rows, where the uppercase
 * case-sensitive form selects 2119 (≈1601 already-CLOSED complaints it would wrongly re-escalate).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SlaBreachEscalationService")
class SlaBreachEscalationServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private RbioStatusVocabulary statusVocabulary;
    @Mock private SystemConfigRepository systemConfigRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private SlaBreachEscalationService service;

    private static final List<String> CANONICAL_CLOSED =
            List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    private Complaint breached(Long id, LocalDateTime deadline) {
        Complaint c = new Complaint();
        c.setId(id);
        c.setComplaintNumber("CMP-TEST-" + id);
        c.setAssignedOfficer("officer1");
        c.setStatus("assigned");
        c.setSlaDeadline(deadline);
        return c;
    }

    private void vocabularyIsCanonical() {
        when(statusVocabulary.closedStatuses()).thenReturn(CANONICAL_CLOSED);
    }

    private void candidatesAre(List<Complaint> rows) {
        when(complaintRepository.findSlaBreachCandidates(
                any(LocalDateTime.class), anyCollection(), any(Pageable.class)))
                .thenReturn(rows);
    }

    @Nested
    @DisplayName("once-only escalation")
    class OnceOnly {

        /**
         * A breached row escalates, exactly one notification goes to the owning officer, and the
         * marker is claimed for that row's id.
         */
        @Test
        @DisplayName("a breached row escalates once and notifies its owning officer")
        void breachedRowEscalatesOnce() {
            vocabularyIsCanonical();
            Complaint c = breached(7L, LocalDateTime.now().minusDays(3));
            candidatesAre(List.of(c));
            when(complaintRepository.claimSlaBreachEscalation(eq(7L), any(LocalDateTime.class)))
                    .thenReturn(1);

            int escalated = service.sweepBreaches();

            assertThat(escalated).isEqualTo(1);
            verify(complaintRepository).claimSlaBreachEscalation(eq(7L), any(LocalDateTime.class));
            verify(notificationService, times(1)).send(
                    eq("officer1"), eq("SLA_BREACH_ESCALATION"), anyString(), anyString(),
                    eq("CMP-TEST-7"), eq("COMPLAINT"), anyString());
        }

        /**
         * The second sweep is a no-op. This is the mechanism under test: the marker written by the
         * first sweep takes the row out of the SELECT, so there is nothing left to notify about.
         * Pins that the sweep does not re-notify every 15 minutes forever — the failure mode that
         * would have made this fix worse than the silence it replaces.
         */
        @Test
        @DisplayName("a second sweep does not re-escalate an already-escalated row")
        void secondSweepDoesNotReEscalate() {
            vocabularyIsCanonical();
            Complaint c = breached(7L, LocalDateTime.now().minusDays(3));
            when(complaintRepository.claimSlaBreachEscalation(eq(7L), any(LocalDateTime.class)))
                    .thenReturn(1);

            // First tick finds it; second tick cannot, because the marker is now set.
            when(complaintRepository.findSlaBreachCandidates(
                    any(LocalDateTime.class), anyCollection(), any(Pageable.class)))
                    .thenReturn(List.of(c))
                    .thenReturn(List.of());

            int first = service.sweepBreaches();
            int second = service.sweepBreaches();

            assertThat(first).isEqualTo(1);
            assertThat(second).isZero();
            verify(notificationService, times(1)).send(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString());
        }

        /**
         * The second line of defence, and the reason the sweep is safe on every replica without a
         * ShedLock table. Here the SELECT still returns the row — i.e. the predicate was loosened or
         * two replicas raced between SELECT and UPDATE — and the conditional UPDATE reports 0 rows
         * affected. The loser must notify nobody.
         *
         * <p>Without this, two replicas ticking simultaneously could both SELECT an unstamped row and
         * both send, because excluding stamped rows from the query only makes SEQUENTIAL sweeps
         * idempotent.
         */
        @Test
        @DisplayName("losing the compare-and-swap sends nothing (multi-replica safety)")
        void losingTheClaimNotifiesNobody() {
            vocabularyIsCanonical();
            Complaint c = breached(7L, LocalDateTime.now().minusDays(3));
            candidatesAre(List.of(c));
            // Another replica got there first.
            when(complaintRepository.claimSlaBreachEscalation(eq(7L), any(LocalDateTime.class)))
                    .thenReturn(0);

            int escalated = service.sweepBreaches();

            assertThat(escalated).isZero();
            verifyNoInteractions(notificationService);
        }

        /**
         * Two replicas, same row, same tick: the database adjudicates and exactly one notification is
         * sent across both. Models the real concurrency by letting the first claim win and the second
         * lose, which is precisely what the conditional UPDATE guarantees.
         */
        @Test
        @DisplayName("two concurrent replicas produce exactly one notification")
        void twoReplicasNotifyOnce() {
            vocabularyIsCanonical();
            Complaint c = breached(7L, LocalDateTime.now().minusDays(3));
            candidatesAre(List.of(c));
            when(complaintRepository.claimSlaBreachEscalation(eq(7L), any(LocalDateTime.class)))
                    .thenReturn(1)   // replica A wins
                    .thenReturn(0);  // replica B loses

            int a = service.sweepBreaches();
            int b = service.sweepBreaches();

            assertThat(a).isEqualTo(1);
            assertThat(b).isZero();
            verify(notificationService, times(1)).send(
                    anyString(), anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("status vocabulary")
    class Vocabulary {

        /**
         * THE CASING TRAP, pinned.
         *
         * <p>COMPLAINTS stores lowercase status strings. The uppercase form that is correct for
         * COMPLAINT_MASTER's Java enum selects 2119 rows against COMPLAINTS instead of 518 — it would
         * re-escalate roughly 1601 complaints that are already closed. MySQL's case-insensitive
         * collation hides the error; Oracle, which is production, would not.
         *
         * <p>This asserts every value handed to the query is already lowercase, so reintroducing
         * {@code List.of("RESOLVED", "CLOSED")} — or wrapping the vocabulary in
         * {@code toUpperCase()} — fails here rather than in the office.
         */
        @Test
        @DisplayName("passes the canonical lowercase vocabulary, never an uppercase list")
        void pinsTheCaseSensitivityTrap() {
            vocabularyIsCanonical();
            candidatesAre(List.of());

            service.sweepBreaches();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(complaintRepository).findSlaBreachCandidates(
                    any(LocalDateTime.class), captor.capture(), any(Pageable.class));

            Collection<String> passed = captor.getValue();
            assertThat(passed).containsExactlyInAnyOrderElementsOf(CANONICAL_CLOSED);
            assertThat(passed).allSatisfy(s ->
                    assertThat(s).isEqualTo(s.toLowerCase()));
            // A closed status must be in the exclusion set, or closed complaints would escalate.
            assertThat(passed).contains("closed", "resolved", "adjudicated", "conciliated");
        }

        /**
         * The vocabulary comes from {@link RbioStatusVocabulary}, not from a copy inside the sweep.
         * Pins that an operator editing RBIO_STATUS_MASTER actually changes what the sweep considers
         * open — if the list were hardcoded here, this would still pass the canonical assertions above
         * while ignoring the table entirely.
         */
        @Test
        @DisplayName("a closed row is never escalated: the vocabulary is consulted, not hardcoded")
        void closedRowNeverEscalatedBecauseVocabularyIsConsulted() {
            when(statusVocabulary.closedStatuses())
                    .thenReturn(List.of("resolved", "closed", "bespoke_closed_state"));
            candidatesAre(List.of());

            service.sweepBreaches();

            verify(statusVocabulary).closedStatuses();

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(complaintRepository).findSlaBreachCandidates(
                    any(LocalDateTime.class), captor.capture(), any(Pageable.class));
            assertThat(captor.getValue()).contains("bespoke_closed_state");
        }

        /**
         * An empty vocabulary means "nothing is closed", which would make every past-deadline
         * complaint — including long-settled ones — look escalatable. The sweep must refuse the tick
         * rather than notify officers about decided cases.
         */
        @Test
        @DisplayName("refuses to sweep when the closed vocabulary resolves empty")
        void refusesOnEmptyVocabulary() {
            when(statusVocabulary.closedStatuses()).thenReturn(List.of());

            int escalated = service.sweepBreaches();

            assertThat(escalated).isZero();
            verify(complaintRepository, never()).findSlaBreachCandidates(
                    any(LocalDateTime.class), anyCollection(), any(Pageable.class));
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    @DisplayName("row guards")
    class RowGuards {

        /**
         * A NULL-deadline row is skipped rather than escalated or fatal.
         *
         * <p>The query already excludes these (417 open complaints had no deadline when this was
         * written, a separately-tracked gap). This pins the service's belt-and-braces guard: if a
         * later edit ever loosened the predicate, the sweep must not NPE dereferencing the deadline to
         * compute how late the complaint is — which would lose the genuine breaches batched with it.
         */
        @Test
        @DisplayName("a NULL-deadline row is skipped, claims nothing and does not break the tick")
        void nullDeadlineRowIsSkipped() {
            vocabularyIsCanonical();
            Complaint noDeadline = breached(1L, null);
            Complaint genuine = breached(2L, LocalDateTime.now().minusDays(1));
            candidatesAre(List.of(noDeadline, genuine));
            when(complaintRepository.claimSlaBreachEscalation(eq(2L), any(LocalDateTime.class)))
                    .thenReturn(1);

            int escalated = service.sweepBreaches();

            // The deadline-less row contributed nothing; the genuine breach still escalated.
            assertThat(escalated).isEqualTo(1);
            verify(complaintRepository, never()).claimSlaBreachEscalation(eq(1L), any(LocalDateTime.class));
            verify(notificationService, times(1)).send(
                    anyString(), anyString(), anyString(), anyString(),
                    eq("CMP-TEST-2"), anyString(), anyString());
        }

        /**
         * Breaching with nobody to tell is itself a finding, so the marker is still claimed (the row
         * must not return every 15 minutes to log the same warning forever) but no notification is
         * fabricated for a null officer.
         */
        @Test
        @DisplayName("an unassigned breached row is claimed but notifies nobody")
        void unassignedRowClaimsButDoesNotNotify() {
            vocabularyIsCanonical();
            Complaint orphan = breached(9L, LocalDateTime.now().minusDays(5));
            orphan.setAssignedOfficer(null);
            candidatesAre(List.of(orphan));
            when(complaintRepository.claimSlaBreachEscalation(eq(9L), any(LocalDateTime.class)))
                    .thenReturn(1);

            int escalated = service.sweepBreaches();

            assertThat(escalated).isEqualTo(1);
            verify(complaintRepository).claimSlaBreachEscalation(eq(9L), any(LocalDateTime.class));
            verifyNoInteractions(notificationService);
        }
    }

    @Nested
    @DisplayName("operability")
    class Operability {

        /** The kill switch must stop the sweep before it touches the database. */
        @Test
        @DisplayName("the SYSTEM_CONFIG kill switch stops the sweep without querying")
        void killSwitchStopsTheSweep() {
            SystemConfig off = new SystemConfig();
            off.setConfigKey(SlaBreachEscalationService.CONFIG_ENABLED);
            off.setConfigValue("false");
            when(systemConfigRepository.findByConfigKey(SlaBreachEscalationService.CONFIG_ENABLED))
                    .thenReturn(Optional.of(off));

            int escalated = service.sweepBreaches();

            assertThat(escalated).isZero();
            verify(complaintRepository, never()).findSlaBreachCandidates(
                    any(LocalDateTime.class), anyCollection(), any(Pageable.class));
            verifyNoInteractions(notificationService);
        }

        /**
         * The batch cap is configurable and bounds one tick. The sweep shares an unconfigured
         * scheduler pool with 48 other {@code @Scheduled} methods and the WebSocket broker, so an
         * unbounded scan here would be the second job able to starve the others.
         */
        @Test
        @DisplayName("the batch size is read from config and bounds the page requested")
        void batchSizeComesFromConfig() {
            vocabularyIsCanonical();
            SystemConfig size = new SystemConfig();
            size.setConfigKey(SlaBreachEscalationService.CONFIG_BATCH_SIZE);
            size.setConfigValue("25");
            when(systemConfigRepository.findByConfigKey(SlaBreachEscalationService.CONFIG_BATCH_SIZE))
                    .thenReturn(Optional.of(size));
            candidatesAre(List.of());

            service.sweepBreaches();

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(complaintRepository).findSlaBreachCandidates(
                    any(LocalDateTime.class), anyCollection(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(25);
        }

        /** A non-numeric config value must fall back, not throw and kill the tick. */
        @Test
        @DisplayName("a malformed batch size falls back to the default")
        void malformedBatchSizeFallsBack() {
            vocabularyIsCanonical();
            SystemConfig size = new SystemConfig();
            size.setConfigKey(SlaBreachEscalationService.CONFIG_BATCH_SIZE);
            size.setConfigValue("not-a-number");
            when(systemConfigRepository.findByConfigKey(SlaBreachEscalationService.CONFIG_BATCH_SIZE))
                    .thenReturn(Optional.of(size));
            candidatesAre(List.of());

            service.sweepBreaches();

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(complaintRepository).findSlaBreachCandidates(
                    any(LocalDateTime.class), anyCollection(), captor.capture());
            assertThat(captor.getValue().getPageSize())
                    .isEqualTo(SlaBreachEscalationService.FALLBACK_BATCH_SIZE);
        }

        /**
         * No Kafka, ever. {@link SlaBreachEscalationService} must not acquire a publisher — the
         * {@code complaint.escalated} topic feeds a self-sustaining loop
         * ({@code ComplaintEscalatedListener:27} → {@code KogitoWorkflowService:208} →
         * {@code KafkaWorkflowEventPublisher:48} → same topic) with no idempotency key and no status
         * guard. Pinned structurally so that "improving" the sweep by injecting
         * {@code ComplaintEventPublisher} fails this test and sends the author to the javadoc.
         */
        @Test
        @DisplayName("declares no Kafka publisher dependency")
        void declaresNoKafkaPublisher() {
            assertThat(SlaBreachEscalationService.class.getDeclaredFields())
                    .noneSatisfy(f -> assertThat(f.getType().getName())
                            .containsIgnoringCase("EventPublisher"));
            assertThat(SlaBreachEscalationService.class.getDeclaredFields())
                    .noneSatisfy(f -> assertThat(f.getType().getName())
                            .containsIgnoringCase("KafkaTemplate"));
        }
    }
}
