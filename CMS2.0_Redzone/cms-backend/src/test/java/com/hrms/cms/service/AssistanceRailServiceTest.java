package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.dto.AssistanceRailResponse.Signal;
import com.hrms.cms.entity.AssistanceRailMemory;
import com.hrms.cms.repository.AssistanceRailMemoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Assistance rail: Tier 0 isolation, Tier 1 priors and graceful degradation (Brief 21).
 *
 * <p>The two load-bearing properties are proved by REFUSAL tests rather than happy paths: a Tier 0
 * lookup that leaked across users would still pass a test that only asked "does my own memory come
 * back", and a rail that threw would still pass a test that only asserted the success shape.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceRailService")
class AssistanceRailServiceTest {

    private static final String COMPLAINT = "CMP-20260928-340970";

    @Mock private AssistanceRailMemoryRepository memoryRepository;
    @Mock private ComplaintRepository complaintRepository;

    private AssistanceRailService service;

    @BeforeEach
    void setUp() {
        service = new AssistanceRailService(memoryRepository, complaintRepository);
        // Default: the complaint exists but carries nothing any Tier 1 prior can report on, so each
        // test below adds only the one fact it is about.
        when(complaintRepository.findRailContext(anyString()))
                .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                        COMPLAINT, null, null, null, null)));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private AssistanceRailMemory memoryFor(String owner, String section, String draft,
                                           LocalDateTime lastViewed) {
        return AssistanceRailMemory.builder()
                .ownerUserId(owner)
                .complaintNumber(COMPLAINT)
                .lastSection(section)
                .draftText(draft)
                .lastViewedAt(lastViewed)
                .build();
    }

    private List<Signal> kinds(AssistanceRailResponse response, String kind) {
        return response.signals().stream().filter(s -> kind.equals(s.kind())).toList();
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Tier 0 isolation")
    class Tier0IsolationTests {

        /**
         * The core privacy property: the query is keyed on the OWNER, so officer B's row is never a
         * candidate. Asserted on the arguments passed to the repository, because that is where the
         * scoping either exists or does not — a test that only checked the returned signals would
         * pass against a complaint-only lookup that happened to return the right row.
         */
        @Test
        @DisplayName("one officer's memory is never read with another officer's id")
        void tier0IsScopedToTheResolvedOwner() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(eq("alice"), eq(COMPLAINT)))
                    .thenReturn(Optional.of(memoryFor("alice", "Assessment", "alice unsaved text",
                            LocalDateTime.now().minusDays(2))));
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(eq("bob"), eq(COMPLAINT)))
                    .thenReturn(Optional.of(memoryFor("bob", "Conciliation", "bob unsaved text",
                            LocalDateTime.now().minusDays(2))));

            AssistanceRailResponse aliceRail = service.rail(COMPLAINT, "alice");
            AssistanceRailResponse bobRail = service.rail(COMPLAINT, "bob");

            assertThat(kinds(aliceRail, AssistanceRailService.KIND_UNSAVED_DRAFT))
                    .singleElement()
                    .satisfies(s -> assertThat(s.detail()).isEqualTo("alice unsaved text"));
            assertThat(kinds(bobRail, AssistanceRailService.KIND_UNSAVED_DRAFT))
                    .singleElement()
                    .satisfies(s -> assertThat(s.detail()).isEqualTo("bob unsaved text"));

            // Neither officer's text appears in the other's rail.
            assertThat(aliceRail.signals()).noneMatch(
                    s -> s.detail() != null && s.detail().contains("bob"));
            assertThat(bobRail.signals()).noneMatch(
                    s -> s.detail() != null && s.detail().contains("alice"));
        }

        /**
         * Officer A asking for a complaint where only officer B has memory gets NO tier 0 — not
         * officer B's. This is the test that would fail if the repository ever gained a
         * complaint-only finder and the service reached for it as a fallback.
         */
        @Test
        @DisplayName("an officer with no memory row gets no tier 0, not somebody else's")
        void tier0DoesNotFallBackToAnotherOfficersRow() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(eq("alice"), eq(COMPLAINT)))
                    .thenReturn(Optional.of(memoryFor("alice", "Assessment", "alice unsaved text",
                            LocalDateTime.now().minusDays(2))));
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(eq("carol"), eq(COMPLAINT)))
                    .thenReturn(Optional.empty());

            AssistanceRailResponse carolRail = service.rail(COMPLAINT, "carol");

            assertThat(carolRail.signals()).noneMatch(s -> s.tier() == 0);
            assertThat(carolRail.glow()).isFalse();
        }

        /**
         * Case folding must not merge two principals.
         *
         * <p>Under MySQL's utf8mb4_0900_ai_ci a unique key on (owner, complaint) treats 'Alice' and
         * 'alice' as ONE row. The service normalises before querying, so both resolve to the same
         * key deterministically on every engine — the alternative being that MySQL merges them and
         * Oracle does not.
         */
        @Test
        @DisplayName("the owner key is normalised, so case cannot create a second identity")
        void ownerIsNormalisedBeforeLookup() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(eq("alice"), eq(COMPLAINT)))
                    .thenReturn(Optional.of(memoryFor("alice", "Assessment", "alice unsaved text",
                            LocalDateTime.now().minusDays(2))));

            AssistanceRailResponse rail = service.rail(COMPLAINT.toLowerCase(), "ALICE");

            verify(memoryRepository).findByOwnerUserIdAndComplaintNumber("alice", COMPLAINT);
            assertThat(kinds(rail, AssistanceRailService.KIND_UNSAVED_DRAFT)).hasSize(1);
        }

        @Test
        @DisplayName("an unresolved caller gets no tier 0 at all")
        void unresolvedOwnerYieldsNoTier0() {
            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(rail.signals()).noneMatch(s -> s.tier() == 0);
            verify(memoryRepository, never()).findByOwnerUserIdAndComplaintNumber(any(), any());
        }

        @Test
        @DisplayName("a recent visit does not produce a last-viewed signal")
        void recentVisitIsSuppressed() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.of(memoryFor("alice", null, null,
                            LocalDateTime.now().minusSeconds(20))));

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice");

            assertThat(kinds(rail, AssistanceRailService.KIND_LAST_VIEWED)).isEmpty();
            // Nothing else to say either, so the rail stays dark rather than glowing with noise.
            assertThat(rail.glow()).isFalse();
        }

        @Test
        @DisplayName("an older visit does produce a last-viewed signal")
        void olderVisitIsReported() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.of(memoryFor("alice", null, null,
                            LocalDateTime.now().minusDays(3))));

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice");

            assertThat(kinds(rail, AssistanceRailService.KIND_LAST_VIEWED))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.tier()).isZero();
                        assertThat(s.title()).contains("3 days ago");
                        assertThat(s.count()).isNull();
                    });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Tier 0 write path")
    class Tier0WriteTests {

        @Test
        @DisplayName("the owner is stored normalised, and from the caller")
        void writeStoresNormalisedOwner() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.empty());

            boolean written = service.rememberVisit(COMPLAINT, "ALICE", "Assessment", "half a sentence");

            assertThat(written).isTrue();
            ArgumentCaptor<AssistanceRailMemory> captor =
                    ArgumentCaptor.forClass(AssistanceRailMemory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getOwnerUserId()).isEqualTo("alice");
            assertThat(captor.getValue().getComplaintNumber()).isEqualTo(COMPLAINT);
            assertThat(captor.getValue().getDraftText()).isEqualTo("half a sentence");
            // Server-assigned, so a client cannot claim a visit that did not happen.
            assertThat(captor.getValue().getLastViewedAt()).isNotNull();
        }

        /**
         * A blank owner is refused rather than attributed to a placeholder.
         *
         * <p>A placeholder owner would pool several officers' unsaved text under one key, which is the
         * same disclosure the per-user key exists to prevent — arrived at from the write side.
         */
        @Test
        @DisplayName("a write with no resolvable owner is refused, not attributed to a placeholder")
        void writeWithoutOwnerIsRefused() {
            assertThat(service.rememberVisit(COMPLAINT, null, "Assessment", "text")).isFalse();
            assertThat(service.rememberVisit(COMPLAINT, "   ", "Assessment", "text")).isFalse();
            verify(memoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("a write with no complaint number is refused")
        void writeWithoutComplaintIsRefused() {
            assertThat(service.rememberVisit(null, "alice", "Assessment", "text")).isFalse();
            verify(memoryRepository, never()).save(any());
        }

        /**
         * Clearing the box is a fact to record, not a no-op.
         *
         * <p>If null meant "leave the old value alone", the rail would keep offering to restore text
         * the officer had already saved or deliberately deleted.
         */
        @Test
        @DisplayName("a null draft clears the stored draft rather than preserving it")
        void nullDraftClearsStoredText() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.of(memoryFor("alice", "Assessment", "old text",
                            LocalDateTime.now().minusDays(1))));

            service.rememberVisit(COMPLAINT, "alice", "Conciliation", null);

            ArgumentCaptor<AssistanceRailMemory> captor =
                    ArgumentCaptor.forClass(AssistanceRailMemory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getDraftText()).isNull();
            assertThat(captor.getValue().getLastSection()).isEqualTo("Conciliation");
        }

        @Test
        @DisplayName("over-long draft text is truncated, not rejected")
        void longDraftIsTruncated() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.empty());

            String huge = "x".repeat(AssistanceRailMemory.MAX_DRAFT_CHARS + 500);
            assertThat(service.rememberVisit(COMPLAINT, "alice", "Assessment", huge)).isTrue();

            ArgumentCaptor<AssistanceRailMemory> captor =
                    ArgumentCaptor.forClass(AssistanceRailMemory.class);
            verify(memoryRepository).save(captor.capture());
            assertThat(captor.getValue().getDraftText())
                    .hasSize(AssistanceRailMemory.MAX_DRAFT_CHARS);
        }

        /**
         * The unique key is the multi-pod lock, so losing the insert race is an expected path.
         *
         * <p>Two pods handling the same officer leaving two tabs both find no row and both insert; one
         * loses on the constraint. That must become an update, not an error an officer sees.
         */
        @Test
        @DisplayName("losing the insert race is retried as an update, not surfaced")
        void insertRaceIsRetried() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"))
                    .thenAnswer(inv -> inv.getArgument(0));

            assertThat(service.rememberVisit(COMPLAINT, "alice", "Assessment", "text")).isTrue();
        }

        @Test
        @DisplayName("a persistent write failure reports false rather than throwing")
        void persistentWriteFailureIsReportedNotThrown() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.empty());
            when(memoryRepository.save(any()))
                    .thenThrow(new RuntimeException("database is gone"));

            assertThat(service.rememberVisit(COMPLAINT, "alice", "Assessment", "text")).isFalse();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Tier 1 priors")
    class Tier1Tests {

        @Test
        @DisplayName("earlier complaints by the same complainant are counted")
        void complainantHistoryIsReported() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(
                    eq("mohan.kumar@gmail.com"), eq(COMPLAINT))).thenReturn(4L);

            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.tier()).isEqualTo(1);
                        assertThat(s.title()).isEqualTo("This complainant has 4 earlier complaints");
                        assertThat(s.count()).isEqualTo(4L);
                        assertThat(s.link()).startsWith("/search?complainantEmail=");
                    });
            assertThat(rail.glow()).isTrue();
        }

        /**
         * 21 rows store {@code ''} rather than NULL for the email.
         *
         * <p>Without this guard every one of those complainants would be told the other 20 were
         * "their" earlier complaints — a wrong fact presented with the authority of a count.
         */
        @Test
        @DisplayName("a blank complainant email is not used as a grouping key")
        void blankComplainantEmailIsNotQueried() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "   ", null, null, null)));

            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
            verify(complaintRepository, never())
                    .countOtherComplaintsByComplainantEmail(any(), any());
        }

        @Test
        @DisplayName("a first-time complainant produces no history signal")
        void firstTimeComplainantIsSilent() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "new@example.com", null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(0L);

            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
        }

        @Test
        @DisplayName("same-clause closures against the same entity are counted")
        void entityClausePrecedentIsReported() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, "HDFC Bank", "15(1)(a)", null)));
            when(complaintRepository.countClosedUnderSameClauseForEntity(
                    eq("15(1)(a)"), eq("HDFC Bank"), eq(COMPLAINT))).thenReturn(3L);

            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_ENTITY_CLAUSE_PRECEDENT))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.tier()).isEqualTo(1);
                        assertThat(s.title())
                                .isEqualTo("3 complaints against this entity closed under 15(1)(a)");
                        assertThat(s.count()).isEqualTo(3L);
                    });
        }

        /**
         * Silent on an open complaint, and that is correct rather than a gap: 2276 of 2769 rows carry
         * no closure clause because they are not closed, and there is no precedent to report before a
         * clause has been chosen.
         */
        @Test
        @DisplayName("a complaint with no closure clause yields no precedent signal")
        void precedentNeedsAClause() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, "HDFC Bank", null, null)));

            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_ENTITY_CLAUSE_PRECEDENT)).isEmpty();
            verify(complaintRepository, never())
                    .countClosedUnderSameClauseForEntity(any(), any(), any());
        }

        @Test
        @DisplayName("the category median is reported once the sample is large enough")
        void categoryMedianIsReported() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, 5L)));
            LocalDateTime filed = LocalDateTime.of(2026, 1, 1, 9, 0);
            when(complaintRepository.findClosureWindowsForCategory(eq(5L), any()))
                    .thenReturn(List.of(
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(20)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(30)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(40)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(50))));

            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.count()).isEqualTo(30L);
                        assertThat(s.title()).isEqualTo("This category closes in 30 days typically");
                        assertThat(s.detail()).contains("5 closed complaints");
                    });
        }

        /**
         * Six of the ten populated categories have 3 or fewer closed complaints. A median of two cases
         * has the authority of a statistic and the content of an anecdote, and the officer cannot tell
         * which from the rail, so it is withheld.
         */
        @Test
        @DisplayName("too small a sample withholds the median rather than showing a weak one")
        void smallSampleIsWithheld() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, 5L)));
            LocalDateTime filed = LocalDateTime.of(2026, 1, 1, 9, 0);
            when(complaintRepository.findClosureWindowsForCategory(eq(5L), any()))
                    .thenReturn(List.of(
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(20))));

            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME)).isEmpty();
        }

        /**
         * Seeded rows genuinely hold {@code closed_at} values that precede {@code created_at}
         * (category 5 averages -40 days). Those are dropped, not clamped to zero: clamping would
         * report a same-day closure that never happened and drag the median toward it.
         */
        @Test
        @DisplayName("negative durations are discarded, and can drop the sample below the floor")
        void negativeDurationsAreDiscarded() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, 5L)));
            LocalDateTime filed = LocalDateTime.of(2026, 1, 1, 9, 0);
            when(complaintRepository.findClosureWindowsForCategory(eq(5L), any()))
                    .thenReturn(List.of(
                            new AssistanceRailProjections.ClosureWindow(filed, filed.minusDays(40)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.minusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(20)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(30))));

            // Three valid of five. Below MIN_CLOSURE_SAMPLE, so nothing is claimed — rather than a
            // median computed from a sample the negatives silently shrank.
            assertThat(kinds(service.rail(COMPLAINT, null),
                    AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME)).isEmpty();
        }

        @Test
        @DisplayName("a complaint with no category yields no median signal")
        void medianNeedsACategory() {
            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME)).isEmpty();
            verify(complaintRepository, never()).findClosureWindowsForCategory(anyLong(), any());
        }

        /** The projection exists so the rail never hydrates a 105-column entity on a screen load. */
        @Test
        @DisplayName("the rail never loads a whole Complaint entity")
        void railUsesTheProjectionNotTheEntity() {
            service.rail(COMPLAINT, "alice");

            verify(complaintRepository).findRailContext(COMPLAINT);
            verify(complaintRepository, never()).findByComplaintNumber(any());
            verify(complaintRepository, never()).findAll();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Graceful degradation")
    class DegradationTests {

        /**
         * The central non-functional requirement: a rail failure must never block an officer.
         *
         * <p>Letting the exception escape would be worse than a blank rail — GlobalExceptionHandler
         * maps a bare RuntimeException to HTTP 400, so the officer's valid request would be reported
         * as a client error.
         */
        @Test
        @DisplayName("a tier 1 failure yields an empty signal list, not an exception")
        void tier1FailureDegradesToEmpty() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenThrow(new RuntimeException("query timeout"));

            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(rail.signals()).isEmpty();
            assertThat(rail.glow()).isFalse();
            assertThat(rail.count()).isZero();
            assertThat(rail.complaintNumber()).isEqualTo(COMPLAINT);
        }

        @Test
        @DisplayName("a tier 0 failure does not cost the tier 1 priors")
        void tier0FailureLeavesTier1Intact() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenThrow(new RuntimeException("memory table missing"));
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(2L);

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice");

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).hasSize(1);
            assertThat(rail.signals()).noneMatch(s -> s.tier() == 0);
        }

        /**
         * Per-SIGNAL granularity, not per-tier.
         *
         * <p>One prior whose index is missing in a given environment must cost exactly that prior. The
         * alternative — one guard around all three — means a single slow query silently empties the
         * rail, which is indistinguishable from "nothing to say".
         */
        @Test
        @DisplayName("one failing prior does not suppress the others")
        void oneFailingPriorDoesNotSuppressTheRest() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenThrow(new RuntimeException("index missing"));
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
            assertThat(kinds(rail, AssistanceRailService.KIND_ENTITY_CLAUSE_PRECEDENT)).hasSize(1);
        }

        @Test
        @DisplayName("an unknown complaint number yields an empty rail, not a 404")
        void unknownComplaintYieldsEmptyRail() {
            when(complaintRepository.findRailContext(anyString())).thenReturn(Optional.empty());

            AssistanceRailResponse rail = service.rail("CMP-DOES-NOT-EXIST", "alice");

            assertThat(rail.signals()).isEmpty();
            assertThat(rail.glow()).isFalse();
        }

        @Test
        @DisplayName("a blank complaint number yields an empty rail")
        void blankComplaintYieldsEmptyRail() {
            assertThat(service.rail(null, "alice").signals()).isEmpty();
            assertThat(service.rail("  ", "alice").glow()).isFalse();
        }

        /** glow and count are derived, so they cannot disagree with the signal list. */
        @Test
        @DisplayName("glow and count always match the signal list")
        void glowAndCountAreDerived() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(4L);
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = service.rail(COMPLAINT, null);

            assertThat(rail.count()).isEqualTo(rail.signals().size()).isEqualTo(2);
            assertThat(rail.glow()).isTrue();
        }

        /** No signal may claim a tier the contract does not define. */
        @Test
        @DisplayName("every signal is tier 0 or tier 1 — there is no tier 2")
        void onlyTwoTiersExist() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.of(memoryFor("alice", "Assessment", "unsaved",
                            LocalDateTime.now().minusDays(2))));
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(4L);
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice");

            assertThat(rail.signals()).isNotEmpty();
            assertThat(rail.signals()).allMatch(s -> s.tier() == 0 || s.tier() == 1);
            // Every signal carries a non-blank kind and title; the client keys icons and i18n off kind.
            assertThat(rail.signals()).allMatch(s -> s.kind() != null && !s.kind().isBlank());
            assertThat(rail.signals()).allMatch(s -> s.title() != null && !s.title().isBlank());
        }
    }
}
