package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceRailResponse;
import com.hrms.cms.dto.AssistanceRailResponse.Signal;
import com.hrms.cms.entity.AssistanceNextAction;
import com.hrms.cms.entity.AssistanceRailMemory;
import com.hrms.cms.repository.AssistanceNextActionRepository;
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
    @Mock private AssistanceNextActionRepository nextActionRepository;

    private AssistanceRailService service;

    @BeforeEach
    void setUp() {
        service = new AssistanceRailService(memoryRepository, complaintRepository,
                nextActionRepository);
        // Default: the complaint exists but carries nothing any Tier 1 prior can report on, so each
        // test below adds only the one fact it is about. The sixth component is the CURRENT status,
        // left null so the next-action prior stays silent unless a test is about it.
        when(complaintRepository.findRailContext(anyString()))
                .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                        COMPLAINT, null, null, null, null, null)));
        when(memoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /**
     * The rail as every test here called it before the next-action prior added a roles parameter.
     *
     * <p>Passes NO roles, which keeps those tests testing what they were written to test — Tier 0 and
     * the three role-independent priors. A helper rather than appending {@code , null} at 25 call
     * sites so that "this caller has no roles" reads as a deliberate condition rather than as a third
     * argument nobody looked at.
     */
    private AssistanceRailResponse railFor(String complaintNumber, String ownerUserId) {
        return service.rail(complaintNumber, ownerUserId, null);
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

            AssistanceRailResponse aliceRail = railFor(COMPLAINT, "alice");
            AssistanceRailResponse bobRail = railFor(COMPLAINT, "bob");

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

            AssistanceRailResponse carolRail = railFor(COMPLAINT, "carol");

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

            AssistanceRailResponse rail = railFor(COMPLAINT.toLowerCase(), "ALICE");

            verify(memoryRepository).findByOwnerUserIdAndComplaintNumber("alice", COMPLAINT);
            assertThat(kinds(rail, AssistanceRailService.KIND_UNSAVED_DRAFT)).hasSize(1);
        }

        @Test
        @DisplayName("an unresolved caller gets no tier 0 at all")
        void unresolvedOwnerYieldsNoTier0() {
            AssistanceRailResponse rail = railFor(COMPLAINT, null);

            assertThat(rail.signals()).noneMatch(s -> s.tier() == 0);
            verify(memoryRepository, never()).findByOwnerUserIdAndComplaintNumber(any(), any());
        }

        @Test
        @DisplayName("a recent visit does not produce a last-viewed signal")
        void recentVisitIsSuppressed() {
            when(memoryRepository.findByOwnerUserIdAndComplaintNumber(any(), any()))
                    .thenReturn(Optional.of(memoryFor("alice", null, null,
                            LocalDateTime.now().minusSeconds(20))));

            AssistanceRailResponse rail = railFor(COMPLAINT, "alice");

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

            AssistanceRailResponse rail = railFor(COMPLAINT, "alice");

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
                            COMPLAINT, "mohan.kumar@gmail.com", null, null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(
                    eq("mohan.kumar@gmail.com"), eq(COMPLAINT))).thenReturn(4L);

            AssistanceRailResponse rail = railFor(COMPLAINT, null);

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
                            COMPLAINT, "   ", null, null, null, null)));

            AssistanceRailResponse rail = railFor(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
            verify(complaintRepository, never())
                    .countOtherComplaintsByComplainantEmail(any(), any());
        }

        @Test
        @DisplayName("a first-time complainant produces no history signal")
        void firstTimeComplainantIsSilent() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "new@example.com", null, null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(0L);

            assertThat(kinds(railFor(COMPLAINT, null),
                    AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
        }

        @Test
        @DisplayName("same-clause closures against the same entity are counted")
        void entityClausePrecedentIsReported() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, "HDFC Bank", "15(1)(a)", null, null)));
            when(complaintRepository.countClosedUnderSameClauseForEntity(
                    eq("15(1)(a)"), eq("HDFC Bank"), eq(COMPLAINT))).thenReturn(3L);

            assertThat(kinds(railFor(COMPLAINT, null),
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
         * Silent on an open complaint, and that is correct rather than a gap: 3526 of 4403 rows carry
         * no closure clause because they are not closed, and there is no precedent to report before a
         * clause has been chosen.
         */
        @Test
        @DisplayName("a complaint with no closure clause yields no precedent signal")
        void precedentNeedsAClause() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, "HDFC Bank", null, null, null)));

            assertThat(kinds(railFor(COMPLAINT, null),
                    AssistanceRailService.KIND_ENTITY_CLAUSE_PRECEDENT)).isEmpty();
            verify(complaintRepository, never())
                    .countClosedUnderSameClauseForEntity(any(), any(), any());
        }

        @Test
        @DisplayName("the category median is reported once the sample is large enough")
        void categoryMedianIsReported() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, 5L, null)));
            LocalDateTime filed = LocalDateTime.of(2026, 1, 1, 9, 0);
            when(complaintRepository.findClosureWindowsForCategory(eq(5L), any()))
                    .thenReturn(List.of(
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(20)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(30)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(40)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(50))));

            assertThat(kinds(railFor(COMPLAINT, null),
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
                            COMPLAINT, null, null, null, 5L, null)));
            LocalDateTime filed = LocalDateTime.of(2026, 1, 1, 9, 0);
            when(complaintRepository.findClosureWindowsForCategory(eq(5L), any()))
                    .thenReturn(List.of(
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(10)),
                            new AssistanceRailProjections.ClosureWindow(filed, filed.plusDays(20))));

            assertThat(kinds(railFor(COMPLAINT, null),
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
                            COMPLAINT, null, null, null, 5L, null)));
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
            assertThat(kinds(railFor(COMPLAINT, null),
                    AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME)).isEmpty();
        }

        @Test
        @DisplayName("a complaint with no category yields no median signal")
        void medianNeedsACategory() {
            AssistanceRailResponse rail = railFor(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_CATEGORY_CLOSURE_TIME)).isEmpty();
            verify(complaintRepository, never()).findClosureWindowsForCategory(anyLong(), any());
        }

        /** The projection exists so the rail never hydrates a 105-column entity on a screen load. */
        @Test
        @DisplayName("the rail never loads a whole Complaint entity")
        void railUsesTheProjectionNotTheEntity() {
            railFor(COMPLAINT, "alice");

            verify(complaintRepository).findRailContext(COMPLAINT);
            verify(complaintRepository, never()).findByComplaintNumber(any());
            verify(complaintRepository, never()).findAll();
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    /**
     * The next-action prior.
     *
     * <p>Weighted towards what the signal must NOT do. The rollup is mined from what happened rather
     * than from what the workflow permits, so it can legitimately name an action the state machine
     * would now refuse — which makes "carries no link and commits nothing" a correctness property
     * (§5.1's "suggest, highlight, do not auto-select"), not a styling choice.
     */
    @Nested
    @DisplayName("Tier 1: next-action prior")
    class NextActionTests {

        private static final String STATUS = "in_progress";
        private static final String ROLE = "CEPC_DO";

        private void complaintAt(String status, Long categoryId) {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, categoryId, status)));
        }

        private AssistanceNextAction cohort(String action, long occurrences, long total,
                                            long categoryKey) {
            return AssistanceNextAction.builder()
                    .fromStatus(STATUS)
                    .performedByRole(ROLE)
                    .categoryKey(categoryKey)
                    .action(action)
                    .occurrences(occurrences)
                    .cohortTotal(total)
                    .refreshedAt(LocalDateTime.now().minusHours(1))
                    .build();
        }

        private List<Signal> nextAction(java.util.Collection<String> roles) {
            return kinds(service.rail(COMPLAINT, "alice", roles),
                    AssistanceRailService.KIND_NEXT_ACTION);
        }

        @Test
        @DisplayName("reports the stored winner with its numerator, denominator and share")
        void reportsTheCohortWithItsDenominator() {
            complaintAt(STATUS, null);
            when(nextActionRepository.findRailCandidates(eq(STATUS), any(), any()))
                    .thenReturn(List.of(cohort("SUBMIT_FOR_REVIEW", 1062, 1200,
                            AssistanceNextAction.CATEGORY_AGNOSTIC)));

            assertThat(nextAction(List.of(ROLE))).singleElement().satisfies(s -> {
                assertThat(s.tier()).isEqualTo(1);
                // The ACTION is the raw timeline value, untranslated: the client matches it against
                // workflow-action-bar's own button ids, and a localised value would match nothing.
                assertThat(s.params()).containsEntry(AssistanceRailService.PARAM_ACTION,
                        "SUBMIT_FOR_REVIEW");
                // count is the NUMERATOR in both places, because the seeded i18n value reads
                // "{{action}} followed in {{count}} of {{total}} comparable cases". A denominator
                // here would render "1200 of 1200" in all 13 locales while the English title stayed
                // correct — a defect visible only to officers not reading English.
                assertThat(s.params()).containsEntry(AssistanceRailService.PARAM_COUNT, "1062");
                assertThat(s.count()).isEqualTo(1062);
                assertThat(s.params()).containsEntry(AssistanceRailService.PARAM_TOTAL, "1200");
                assertThat(s.params()).containsEntry(AssistanceRailService.PARAM_PERCENT, "89");
                assertThat(s.title()).contains("1062 of 1200");
            });
        }

        /**
         * §5.1 is a correctness requirement here, not a UX preference: the rollup can name an action
         * the workflow would refuse, so the rail must not offer a route that pre-selects one.
         */
        @Test
        @DisplayName("carries no link, so nothing in the rail can steer a transition")
        void suggestsWithoutOfferingAnAction() {
            complaintAt(STATUS, null);
            when(nextActionRepository.findRailCandidates(eq(STATUS), any(), any()))
                    .thenReturn(List.of(cohort("ACCEPT", 1748, 1748,
                            AssistanceNextAction.CATEGORY_AGNOSTIC)));

            assertThat(nextAction(List.of(ROLE))).singleElement()
                    .satisfies(s -> assertThat(s.link()).isNull());
        }

        /** The sentinel's whole purpose: the specific cohort is the better answer when one exists. */
        @Test
        @DisplayName("a category-specific cohort beats the category-agnostic fallback")
        void prefersTheCategorySpecificCohort() {
            complaintAt(STATUS, 5L);
            when(nextActionRepository.findRailCandidates(eq(STATUS), any(), any()))
                    .thenReturn(List.of(
                            // The agnostic row rests on far more observations, and still loses: it is
                            // a less precise answer to the question actually being asked.
                            cohort("SUBMIT_FOR_REVIEW", 1062, 1200,
                                    AssistanceNextAction.CATEGORY_AGNOSTIC),
                            cohort("ESCALATE", 7, 9, 5L)));

            assertThat(nextAction(List.of(ROLE))).singleElement()
                    .satisfies(s -> assertThat(s.params())
                            .containsEntry(AssistanceRailService.PARAM_ACTION, "ESCALATE"));
        }

        /**
         * Between equally specific cohorts the better-EVIDENCED one wins, not the higher percentage.
         * A 100%-of-5 cohort is weaker than an 85%-of-200 one, and preferring the share would
         * systematically surface the thinnest cohorts in the table.
         */
        @Test
        @DisplayName("at equal specificity the larger denominator wins, not the higher share")
        void prefersTheLargerDenominator() {
            complaintAt(STATUS, null);
            when(nextActionRepository.findRailCandidates(eq(STATUS), any(), any()))
                    .thenReturn(List.of(
                            cohort("REOPEN", 5, 5, AssistanceNextAction.CATEGORY_AGNOSTIC),
                            cohort("APPROVE_REVIEW", 170, 200,
                                    AssistanceNextAction.CATEGORY_AGNOSTIC)));

            assertThat(nextAction(List.of(ROLE))).singleElement()
                    .satisfies(s -> assertThat(s.params())
                            .containsEntry(AssistanceRailService.PARAM_ACTION, "APPROVE_REVIEW"));
        }

        /**
         * Silence, not a role-agnostic average. "What people usually do from this status" pooled
         * across a dealing official and an Ombudsman describes neither, and the rail cannot caveat it.
         */
        @Test
        @DisplayName("no roles means no signal, and no query at all")
        void withoutARoleTheSignalIsSilent() {
            complaintAt(STATUS, null);

            assertThat(nextAction(null)).isEmpty();
            assertThat(nextAction(List.of())).isEmpty();
            assertThat(nextAction(List.of("   "))).isEmpty();
            verify(nextActionRepository, never()).findRailCandidates(any(), any(), any());
        }

        /** A complaint whose status is unknown has no cohort to look up; the rollup is keyed on it. */
        @Test
        @DisplayName("no status means no signal, and no query at all")
        void withoutAStatusTheSignalIsSilent() {
            complaintAt(null, null);

            assertThat(nextAction(List.of(ROLE))).isEmpty();
            verify(nextActionRepository, never()).findRailCandidates(any(), any(), any());
        }

        /**
         * The common case on real data, and the one an unapplied V115 also produces: an empty table.
         * Both are a missing signal rather than a failure — measured, cohorts exist for only two of
         * the register's statuses, so this prior is silent on most complaints by DATA and not by bug.
         */
        @Test
        @DisplayName("an empty rollup is one fewer signal, never an error")
        void anEmptyRollupIsNormal() {
            complaintAt(STATUS, null);
            when(nextActionRepository.findRailCandidates(any(), any(), any()))
                    .thenReturn(List.of());

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice", List.of(ROLE));

            assertThat(kinds(rail, AssistanceRailService.KIND_NEXT_ACTION)).isEmpty();
            assertThat(rail.complaintNumber()).isEqualTo(COMPLAINT);
        }

        /**
         * One statement, not a seek per role. A staff token carries several real roles plus Keycloak's
         * {@code offline_access} and {@code default-roles-cms}, so looping would issue up to sixteen
         * queries on a screen load to answer one question.
         */
        @Test
        @DisplayName("a multi-role caller costs one query, with every role in it")
        void readsAllRolesInOneQuery() {
            complaintAt(STATUS, null);
            when(nextActionRepository.findRailCandidates(any(), any(), any()))
                    .thenReturn(List.of());

            service.rail(COMPLAINT, "alice",
                    List.of(ROLE, "CEPC_REVIEWER", "offline_access", "default-roles-cms"));

            ArgumentCaptor<List<String>> roles = ArgumentCaptor.forClass(List.class);
            verify(nextActionRepository, org.mockito.Mockito.times(1))
                    .findRailCandidates(eq(STATUS), roles.capture(), any());
            assertThat(roles.getValue())
                    .containsExactlyInAnyOrder(ROLE, "CEPC_REVIEWER", "offline_access",
                            "default-roles-cms");
        }

        /**
         * The sentinel is ALWAYS one of the keys asked for, even when the complaint has a category —
         * otherwise a categorised complaint would lose the fallback that exists for nearly every
         * cohort today, and the signal would be silent precisely where the data is richest.
         */
        @Test
        @DisplayName("the agnostic sentinel is always among the category keys requested")
        void alwaysAsksForTheFallback() {
            complaintAt(STATUS, 5L);
            when(nextActionRepository.findRailCandidates(any(), any(), any()))
                    .thenReturn(List.of());

            service.rail(COMPLAINT, "alice", List.of(ROLE));

            ArgumentCaptor<List<Long>> keys = ArgumentCaptor.forClass(List.class);
            verify(nextActionRepository).findRailCandidates(eq(STATUS), any(), keys.capture());
            assertThat(keys.getValue())
                    .containsExactlyInAnyOrder(5L, AssistanceNextAction.CATEGORY_AGNOSTIC);
        }

        /** A rollup read that fails costs this one signal and leaves the other priors standing. */
        @Test
        @DisplayName("a failing rollup read does not take the other priors with it")
        void rollupFailureIsContained() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", null, null, null, STATUS)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(4L);
            when(nextActionRepository.findRailCandidates(any(), any(), any()))
                    .thenThrow(new RuntimeException("ASSISTANCE_NEXT_ACTION does not exist"));

            AssistanceRailResponse rail = service.rail(COMPLAINT, "alice", List.of(ROLE));

            assertThat(kinds(rail, AssistanceRailService.KIND_NEXT_ACTION)).isEmpty();
            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).hasSize(1);
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

            AssistanceRailResponse rail = railFor(COMPLAINT, null);

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
                            COMPLAINT, "mohan.kumar@gmail.com", null, null, null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(2L);

            AssistanceRailResponse rail = railFor(COMPLAINT, "alice");

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
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenThrow(new RuntimeException("index missing"));
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = railFor(COMPLAINT, null);

            assertThat(kinds(rail, AssistanceRailService.KIND_COMPLAINANT_HISTORY)).isEmpty();
            assertThat(kinds(rail, AssistanceRailService.KIND_ENTITY_CLAUSE_PRECEDENT)).hasSize(1);
        }

        @Test
        @DisplayName("an unknown complaint number yields an empty rail, not a 404")
        void unknownComplaintYieldsEmptyRail() {
            when(complaintRepository.findRailContext(anyString())).thenReturn(Optional.empty());

            AssistanceRailResponse rail = railFor("CMP-DOES-NOT-EXIST", "alice");

            assertThat(rail.signals()).isEmpty();
            assertThat(rail.glow()).isFalse();
        }

        @Test
        @DisplayName("a blank complaint number yields an empty rail")
        void blankComplaintYieldsEmptyRail() {
            assertThat(railFor(null, "alice").signals()).isEmpty();
            assertThat(railFor("  ", "alice").glow()).isFalse();
        }

        /** glow and count are derived, so they cannot disagree with the signal list. */
        @Test
        @DisplayName("glow and count always match the signal list")
        void glowAndCountAreDerived() {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(4L);
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = railFor(COMPLAINT, null);

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
                            COMPLAINT, "mohan.kumar@gmail.com", "HDFC Bank", "15(1)(a)", null, null)));
            when(complaintRepository.countOtherComplaintsByComplainantEmail(any(), any()))
                    .thenReturn(4L);
            when(complaintRepository.countClosedUnderSameClauseForEntity(any(), any(), any()))
                    .thenReturn(3L);

            AssistanceRailResponse rail = railFor(COMPLAINT, "alice");

            assertThat(rail.signals()).isNotEmpty();
            assertThat(rail.signals()).allMatch(s -> s.tier() == 0 || s.tier() == 1);
            // Every signal carries a non-blank kind and title; the client keys icons and i18n off kind.
            assertThat(rail.signals()).allMatch(s -> s.kind() != null && !s.kind().isBlank());
            assertThat(rail.signals()).allMatch(s -> s.title() != null && !s.title().isBlank());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    /**
     * The next-action preference order, isolated one tier at a time.
     *
     * <h3>Why these sit apart from {@link NextActionTests}</h3>
     * {@code BEST_COHORT} is three rules applied in sequence, and a fixture that lets two of them
     * agree proves neither. The tests above establish the signal's SHAPE on realistic data, where the
     * winner is the better-evidenced row and also, incidentally, the alphabetically-first one. These
     * tests separate the tiers deliberately: each one sets the rules it is NOT about to disagree with
     * the rule it is about, so a comparator missing a tier — or applying them in the wrong order —
     * fails here rather than passing by coincidence.
     *
     * <p>The specific confound worth naming: between two equally-specific cohorts, "larger denominator"
     * and "alphabetically first" must be tested on fixtures where they point at DIFFERENT rows, in both
     * directions. Otherwise a comparator that dropped the denominator rule entirely and sorted only by
     * name would still be green.
     */
    @Nested
    @DisplayName("Tier 1: next-action cohort preference, tier by tier")
    class NextActionPreferenceTests {

        private static final String STATUS = "assigned";
        private static final long CATEGORY = 7L;

        private void complaintAt(Long categoryId) {
            when(complaintRepository.findRailContext(anyString()))
                    .thenReturn(Optional.of(new AssistanceRailProjections.RailContext(
                            COMPLAINT, null, null, null, categoryId, STATUS)));
        }

        private AssistanceNextAction cohort(long categoryKey, String action, long occurrences,
                                            long total) {
            return AssistanceNextAction.builder()
                    .fromStatus(STATUS)
                    .performedByRole("CEPC_DO")
                    .categoryKey(categoryKey)
                    .action(action)
                    .occurrences(occurrences)
                    .cohortTotal(total)
                    .refreshedAt(LocalDateTime.now().minusHours(1))
                    .build();
        }

        /** The action the rail chose, from the params the client actually reads. */
        private String chosenAction(AssistanceNextAction... candidates) {
            when(nextActionRepository.findRailCandidates(anyString(), any(), any()))
                    .thenReturn(List.of(candidates));
            List<Signal> signals = kinds(
                    service.rail(COMPLAINT, "alice", List.of("CEPC_DO", "CEPC_REVIEWER")),
                    AssistanceRailService.KIND_NEXT_ACTION);
            assertThat(signals).hasSize(1);
            return signals.get(0).params().get(AssistanceRailService.PARAM_ACTION);
        }

        // ─── Tier 1: specificity, with both other rules arguing against it ─────────────────────

        /**
         * The category-specific row wins even when it is the WEAKER and the LATER-named candidate.
         *
         * <p>Both lower tiers are set against it on purpose: the agnostic row rests on 200 observations
         * against 5 and sorts first alphabetically, so the only rule that can produce this answer is
         * specificity, and it has to be applied first.
         */
        @Test
        @DisplayName("specificity wins against both a larger denominator and an earlier name")
        void specificityOutranksEvidenceAndName() {
            complaintAt(CATEGORY);

            assertThat(chosenAction(
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "AA_BROAD_WINNER", 170, 200),
                    cohort(CATEGORY, "ZZ_NARROW_WINNER", 5, 5)))
                    .isEqualTo("ZZ_NARROW_WINNER");
        }

        /**
         * A categorised complaint with NO specific cohort still gets the fallback.
         *
         * <p>This is the half of the sentinel that is easy to lose: preferring the specific row must
         * not mean requiring one, or the signal would go silent on exactly the complaints the category
         * key was added to serve better.
         */
        @Test
        @DisplayName("a categorised complaint with no specific cohort falls back to the sentinel")
        void fallsBackToTheSentinelWhenNoSpecificCohortExists() {
            complaintAt(CATEGORY);

            assertThat(chosenAction(
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "FORWARD", 170, 200)))
                    .isEqualTo("FORWARD");
        }

        // ─── Tier 2: evidence, in BOTH alphabetical directions ────────────────────────────────

        /**
         * At equal specificity the larger denominator wins — here it is also alphabetically LAST.
         *
         * <p>The 5-of-5 cohort is a 100% winner and the 170-of-200 is 85%, so a comparator preferring
         * the SHARE would pick the thin one; a comparator that only sorted by name would too. This
         * fixture rejects both.
         */
        @Test
        @DisplayName("the larger denominator wins even when its action sorts last")
        void evidenceOutranksNameDescending() {
            complaintAt(null);

            assertThat(chosenAction(
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "AA_PERFECT_OF_FIVE", 5, 5),
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "ZZ_BROAD_COHORT", 170, 200)))
                    .isEqualTo("ZZ_BROAD_COHORT");
        }

        /** The mirror fixture: the names swapped, so the answer cannot be the alphabet either way. */
        @Test
        @DisplayName("the larger denominator wins when its action sorts first as well")
        void evidenceOutranksNameAscending() {
            complaintAt(null);

            assertThat(chosenAction(
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "ZZ_PERFECT_OF_FIVE", 5, 5),
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "AA_BROAD_COHORT", 170, 200)))
                    .isEqualTo("AA_BROAD_COHORT");
        }

        /**
         * The evidence rule applies WITHIN the specific tier too, not only among the fallbacks.
         *
         * <p>Reachable whenever the caller holds more than one role that has acted from this status —
         * each role contributes its own category-specific cohort, and they are then separated by the
         * second rule rather than by result-set order.
         */
        @Test
        @DisplayName("two category-specific cohorts are separated by their denominators")
        void evidenceAlsoRanksWithinTheSpecificTier() {
            complaintAt(CATEGORY);

            assertThat(chosenAction(
                    cohort(CATEGORY, "AA_THIN", 9, 9),
                    cohort(CATEGORY, "ZZ_THICK", 60, 90),
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "MM_HUGE", 900, 1000)))
                    .isEqualTo("ZZ_THICK");
        }

        // ─── Tier 3: the name, which exists only to be deterministic ──────────────────────────

        /**
         * Identical specificity and identical denominators resolve by NAME, repeatably.
         *
         * <p>Asked ten times over the same unordered pair, because the property is stability rather
         * than the particular letter that wins. A rail that answered differently between two identical
         * requests would be reported as untrustworthy, and nothing in the logs would show it.
         */
        @Test
        @DisplayName("an exact tie resolves to the same name on every one of ten requests")
        void nameTieIsStableAcrossRepeatedRequests() {
            complaintAt(null);
            AssistanceNextAction zulu =
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "ZZ_ESCALATE", 40, 50);
            AssistanceNextAction alpha =
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "AA_ACCEPT", 40, 50);

            for (int request = 0; request < 10; request++) {
                assertThat(chosenAction(zulu, alpha)).isEqualTo("AA_ACCEPT");
            }
        }

        /**
         * A tie at EQUAL denominators but different numerators still resolves by name.
         *
         * <p>Deliberate: the share is not a tiebreak at any tier. 45-of-50 and 40-of-50 are equally
         * well evidenced, and the comparator does not reach for the percentage to separate them —
         * which is the same reasoning that keeps a 100%-of-5 from beating an 85%-of-200.
         */
        @Test
        @DisplayName("the share is not a tiebreak, even at an equal denominator")
        void shareIsNeverATiebreak() {
            complaintAt(null);

            assertThat(chosenAction(
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "ZZ_STRONGER", 45, 50),
                    cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "AA_WEAKER", 40, 50)))
                    .isEqualTo("AA_WEAKER");
        }

        // ─── the shape of the chosen row ──────────────────────────────────────────────────────

        /**
         * The chosen cohort's counts travel together, so no officer sees a numerator from one row
         * beside a denominator from another.
         */
        @Test
        @DisplayName("the reported counts all come from the one cohort that was chosen")
        void reportedCountsComeFromTheChosenRowAlone() {
            complaintAt(CATEGORY);
            when(nextActionRepository.findRailCandidates(anyString(), any(), any()))
                    .thenReturn(List.of(
                            cohort(AssistanceNextAction.CATEGORY_AGNOSTIC, "FORWARD", 1062, 1200),
                            cohort(CATEGORY, "ESCALATE", 7, 9)));

            assertThat(kinds(service.rail(COMPLAINT, "alice", List.of("CEPC_DO")),
                    AssistanceRailService.KIND_NEXT_ACTION))
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.count()).isEqualTo(7L);
                        assertThat(s.params())
                                .containsEntry(AssistanceRailService.PARAM_ACTION, "ESCALATE")
                                .containsEntry(AssistanceRailService.PARAM_COUNT, "7")
                                .containsEntry(AssistanceRailService.PARAM_TOTAL, "9")
                                // 7/9 = 77.8%, rounded not truncated.
                                .containsEntry(AssistanceRailService.PARAM_PERCENT, "78");
                        assertThat(s.title()).contains("7 of 9");
                        // Still no link, whichever tier won the preference. §5.1 forbids the rail
                        // steering a transition, and this prior is the only one that names an action
                        // the officer can commit with one click elsewhere on the screen.
                        assertThat(s.link()).isNull();
                    });
        }
    }
}
