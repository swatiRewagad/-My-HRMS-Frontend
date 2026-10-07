package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.entity.AssistanceNextAction;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.AssistanceNextActionRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.projection.AssistanceRailProjections.TimelineAction;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The next-action rollup refresh: the kill switch, the lease, the floors and the dual count (Brief 21
 * §5.3.1).
 *
 * <p>The load-bearing properties here are all REFUSALS and INVARIANTS rather than happy paths. A job
 * that ran without the lease, counted an event once instead of twice, or stamped each row with its own
 * {@code now()} would still pass a test that only asked "did it write a row" — and each of those three
 * failures is invisible in production, because a stale or half-swept rollup looks exactly like a correct
 * one from the rail.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceNextActionRefreshService")
class AssistanceNextActionRefreshServiceTest {

    private static final String STATUS = "assigned";
    private static final String ROLE = "CEPC_DO";
    private static final long CATEGORY = 7L;

    @Mock private ComplaintTimelineRepository timelineRepository;
    @Mock private AssistanceNextActionRepository rollupRepository;
    @Mock private AssistanceJobLockRepository lockRepository;

    private AssistanceNextActionRefreshService service;

    @BeforeEach
    void setUp() {
        service = new AssistanceNextActionRefreshService(timelineRepository, rollupRepository,
                lockRepository);
        // Both are @Value FIELD-injected (the class is @RequiredArgsConstructor and Lombok does not
        // copy @Value onto generated constructor parameters), so a unit test has to set them here.
        ReflectionTestUtils.setField(service, "assistanceEnabled", true);
        ReflectionTestUtils.setField(service, "podIdentity", "pod-under-test");

        // Default: the lease is free and the timeline is empty. Each test adds only the one fact it is
        // about.
        when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(1);
        when(timelineRepository.findActionsForRollup(anyLong(), any())).thenReturn(List.of());
        when(rollupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────────────────────────

    /** {@code n} identical qualifying events, ids assigned from 1. */
    private static List<TimelineAction> events(int n, String action, Long categoryId) {
        List<TimelineAction> rows = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            rows.add(new TimelineAction((long) i, STATUS, ROLE, action, categoryId));
        }
        return rows;
    }

    /** Captures every row handed to {@code save}. */
    private List<AssistanceNextAction> savedRows() {
        ArgumentCaptor<AssistanceNextAction> captor =
                ArgumentCaptor.forClass(AssistanceNextAction.class);
        verify(rollupRepository, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private static AssistanceNextAction rowFor(List<AssistanceNextAction> rows, long categoryKey) {
        return rows.stream().filter(r -> r.getCategoryKey() == categoryKey).findFirst().orElse(null);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The §6.2 kill switch")
    class KillSwitchTests {

        /**
         * Off means off at the FIRST statement, not after the scan.
         *
         * <p>A disabled feature that still took an hourly lease and still scanned the timeline would
         * cost exactly what the switch exists to be able to stop, while reading in the log as if it had
         * been turned off.
         */
        @Test
        @DisplayName("a disabled rail takes no lease and issues no query")
        void disabledRefreshDoesNothingAtAll() {
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);

            assertThat(service.refresh()).isZero();

            verifyNoInteractions(lockRepository);
            verifyNoInteractions(timelineRepository);
            verifyNoInteractions(rollupRepository);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The lease")
    class LeaseTests {

        /**
         * Losing the lease means doing NOTHING, not doing it more carefully.
         *
         * <p>The whole point of the lease is that two pods do not interleave upserts against the same
         * cohort keys, so the loser must not reach the scan at all.
         */
        @Test
        @DisplayName("a lost lease yields no timeline scan and no write")
        void lostLeaseSkipsTheCycle() {
            when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(0);

            assertThat(service.refresh()).isZero();

            verify(timelineRepository, never()).findActionsForRollup(anyLong(), any());
            verify(rollupRepository, never()).save(any());
            verify(rollupRepository, never()).deleteStale(any());
            // Nothing was taken, so nothing is given back — releasing another pod's live lease would
            // readmit the concurrency the lease exists to prevent.
            verify(lockRepository, never()).release(anyString(), any());
        }

        @Test
        @DisplayName("the lease is contended for under the seeded lock name")
        void leaseUsesTheSeededLockName() {
            service.refresh();

            verify(lockRepository).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH), any(), any(), anyString());
        }

        @Test
        @DisplayName("a won lease proceeds to the scan")
        void wonLeaseRunsTheScan() {
            service.refresh();

            verify(timelineRepository).findActionsForRollup(eq(0L), any());
        }

        /**
         * The release is in a {@code finally}, and that is not stylistic.
         *
         * <p>A failed pass that kept its lease would block every pod for the full lease duration, so
         * one bad cycle would become ten minutes of no refresh at all.
         */
        @Test
        @DisplayName("the lease is released even when the recompute throws")
        void leaseIsReleasedAfterAFailedPass() {
            when(timelineRepository.findActionsForRollup(anyLong(), any()))
                    .thenThrow(new RuntimeException("ASSISTANCE_NEXT_ACTION does not exist"));

            // Never throws: a scheduled job has no caller to report to.
            assertThat(service.refresh()).isZero();

            verify(lockRepository).release(
                    eq(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH), any());
        }

        @Test
        @DisplayName("the lease is released after a successful pass too")
        void leaseIsReleasedAfterASuccessfulPass() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));

            assertThat(service.refresh()).isEqualTo(1);

            verify(lockRepository).release(
                    eq(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH), any());
        }

        /**
         * An acquire that THROWS is "I did not get the lock".
         *
         * <p>This is the unapplied-migration case: the lock table is missing, every statement fails,
         * and treating the error as permission to proceed would mean the one environment where the lock
         * is unavailable is the one where every pod refreshes at once.
         */
        @Test
        @DisplayName("an acquire that throws is treated as a lost lease, not as permission")
        void acquireFailureDoesNotProceed() {
            when(lockRepository.acquire(anyString(), any(), any(), anyString()))
                    .thenThrow(new RuntimeException("table ASSISTANCE_JOB_LOCK does not exist"));

            assertThat(service.refresh()).isZero();

            verify(timelineRepository, never()).findActionsForRollup(anyLong(), any());
            verify(rollupRepository, never()).save(any());
            verify(rollupRepository, never()).deleteStale(any());
        }

        /** A release that fails costs one idle cycle by design, so it must not surface. */
        @Test
        @DisplayName("a failed release does not turn a successful pass into a failure")
        void failedReleaseIsSurvivable() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));
            when(lockRepository.release(anyString(), any()))
                    .thenThrow(new RuntimeException("connection reset"));

            assertThat(service.refresh()).isEqualTo(1);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The floors")
    class FloorTests {

        /**
         * Four is an anecdote, five is a statistic, and the officer cannot tell which from the rail.
         */
        @Test
        @DisplayName("a cohort one event below the sample floor is not written")
        void justBelowTheSampleFloorIsNotWritten() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events((int) AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE - 1,
                            "ACCEPT", null));

            assertThat(service.refresh()).isZero();
            verify(rollupRepository, never()).save(any());
        }

        @Test
        @DisplayName("a cohort exactly at the sample floor is written")
        void exactlyAtTheSampleFloorIsWritten() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events((int) AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE,
                            "ACCEPT", null));

            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getAction()).isEqualTo("ACCEPT");
                assertThat(row.getOccurrences()).isEqualTo(5L);
                assertThat(row.getCohortTotal()).isEqualTo(5L);
            });
        }

        /**
         * A genuinely SPLIT cohort: 3 / 2 / 2 over seven events, so the plurality winner holds 43%.
         *
         * <p>Below half, "most officers did X" is simply false, and the rail has no room to caveat a
         * plurality. The cohort is withheld rather than reported with a low percentage nobody reads.
         */
        @Test
        @DisplayName("a split cohort whose winner holds under half is not written")
        void belowTheConfidenceFloorIsNotWritten() {
            List<TimelineAction> split = List.of(
                    new TimelineAction(1L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(2L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(3L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(4L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(5L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(6L, STATUS, ROLE, "FORWARD", null),
                    new TimelineAction(7L, STATUS, ROLE, "FORWARD", null));
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(split);

            // The cohort clears the SAMPLE floor (7 >= 5), so this proves the confidence floor and not
            // the one beside it.
            assertThat(split).hasSizeGreaterThanOrEqualTo(
                    (int) AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE);
            assertThat(service.refresh()).isZero();
            verify(rollupRepository, never()).save(any());
        }

        /** 3 of 5 is 60%, over the floor, and is reported with its denominator attached. */
        @Test
        @DisplayName("a cohort whose winner holds over half is written with both counts")
        void aboveTheConfidenceFloorIsWritten() {
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(List.of(
                    new TimelineAction(1L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(2L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(3L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(4L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(5L, STATUS, ROLE, "FORWARD", null)));

            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getAction()).isEqualTo("ACCEPT");
                assertThat(row.getOccurrences()).isEqualTo(3L);
                assertThat(row.getCohortTotal()).isEqualTo(5L);
                assertThat(row.confidence()).isEqualTo(0.6d);
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Dual counting and the category sentinel")
    class DualCountTests {

        /**
         * Every qualifying event counts TWICE, and that is what makes the sentinel work.
         *
         * <p>The category-specific row carries precision where the data supports it; the agnostic row
         * guarantees the read has something to fall back to. Counting once into whichever cohort
         * happened to apply would leave the sentinel empty for exactly the categorised complaints the
         * specific rows were supposed to improve.
         */
        @Test
        @DisplayName("a categorised cohort writes both the specific and the agnostic row")
        void categorisedEventsAreCountedTwice() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", CATEGORY));

            assertThat(service.refresh()).isEqualTo(2);

            List<AssistanceNextAction> rows = savedRows();
            assertThat(rows).hasSize(2);
            assertThat(rows).extracting(AssistanceNextAction::getCategoryKey)
                    .containsExactlyInAnyOrder(CATEGORY, AssistanceNextAction.CATEGORY_AGNOSTIC);

            // Both carry the SAME counts here, because every event in this fixture is categorised. The
            // two rows diverge in production only because some events have no category.
            assertThat(rows).allSatisfy(row -> {
                assertThat(row.getFromStatus()).isEqualTo(STATUS);
                assertThat(row.getPerformedByRole()).isEqualTo(ROLE);
                assertThat(row.getOccurrences()).isEqualTo(5L);
                assertThat(row.getCohortTotal()).isEqualTo(5L);
            });
            assertThat(rowFor(rows, AssistanceNextAction.CATEGORY_AGNOSTIC).isCategoryAgnostic())
                    .isTrue();
            assertThat(rowFor(rows, CATEGORY).isCategoryAgnostic()).isFalse();
        }

        @Test
        @DisplayName("an uncategorised cohort writes only the agnostic row")
        void uncategorisedEventsWriteOnlyTheSentinel() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));

            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row ->
                    assertThat(row.getCategoryKey())
                            .isEqualTo(AssistanceNextAction.CATEGORY_AGNOSTIC));
        }

        /**
         * The agnostic row aggregates ACROSS categories; a specific row only clears the floors where
         * that one category has enough history of its own.
         */
        @Test
        @DisplayName("the sentinel aggregates events a single category could not have carried alone")
        void sentinelAggregatesAcrossCategories() {
            List<TimelineAction> mixed = List.of(
                    new TimelineAction(1L, STATUS, ROLE, "ACCEPT", 7L),
                    new TimelineAction(2L, STATUS, ROLE, "ACCEPT", 8L),
                    new TimelineAction(3L, STATUS, ROLE, "ACCEPT", 9L),
                    new TimelineAction(4L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(5L, STATUS, ROLE, "ACCEPT", null));
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(mixed);

            // Only the sentinel clears the sample floor: no single category has five events.
            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getCategoryKey())
                        .isEqualTo(AssistanceNextAction.CATEGORY_AGNOSTIC);
                assertThat(row.getCohortTotal()).isEqualTo(5L);
            });
        }

        /** Status and role are both part of the key, so neither collapses into the other. */
        @Test
        @DisplayName("two roles acting from one status are two cohorts, not one")
        void statusAndRoleAreBothKeyed() {
            List<TimelineAction> rows = new ArrayList<>(events(5, "ACCEPT", null));
            for (int i = 1; i <= 5; i++) {
                rows.add(new TimelineAction(100L + i, STATUS, "RBIO_OFFICER", "AWARD", null));
            }
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(rows);

            assertThat(service.refresh()).isEqualTo(2);
            assertThat(savedRows()).extracting(AssistanceNextAction::getPerformedByRole)
                    .containsExactlyInAnyOrder(ROLE, "RBIO_OFFICER");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Keyset paging")
    class PagingTests {

        /**
         * The second page must start after the LAST id of the first.
         *
         * <p>A regression here is silent and total: re-sending the previous {@code afterId} double
         * counts every row on the page, and over-advancing skips events. Either way the rollup's
         * denominators are wrong and nothing in the rail can show it.
         */
        @Test
        @DisplayName("each page resumes after the last id of the previous page")
        void secondPageResumesAfterTheLastIdSeen() {
            int page = AssistanceNextActionRefreshService.PAGE_SIZE;
            List<TimelineAction> full = events(page, "ACCEPT", null);
            List<TimelineAction> tail = List.of(
                    new TimelineAction(page + 1L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(page + 2L, STATUS, ROLE, "ACCEPT", null));

            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(full);
            when(timelineRepository.findActionsForRollup(eq((long) page), any())).thenReturn(tail);

            assertThat(service.refresh()).isEqualTo(1);

            ArgumentCaptor<Long> afterId = ArgumentCaptor.forClass(Long.class);
            ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
            verify(timelineRepository, org.mockito.Mockito.times(2))
                    .findActionsForRollup(afterId.capture(), pageable.capture());

            // Starts at 0, resumes at the last id of the full page — not at the page NUMBER, and not
            // at the id it started from.
            assertThat(afterId.getAllValues()).containsExactly(0L, (long) page);
            assertThat(pageable.getAllValues()).allSatisfy(p ->
                    assertThat(p.getPageSize()).isEqualTo(page));

            // Every event from both pages landed in the one cohort: 2000 + 2.
            assertThat(savedRows()).singleElement().satisfies(row ->
                    assertThat(row.getCohortTotal()).isEqualTo(page + 2L));
        }

        /**
         * A short page ENDS the walk. Asking again would be a wasted statement every cycle, and a
         * repository returning the same short page forever would loop.
         */
        @Test
        @DisplayName("a short first page ends the walk in one query")
        void shortFirstPageStopsImmediately() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));

            service.refresh();

            verify(timelineRepository, org.mockito.Mockito.times(1))
                    .findActionsForRollup(anyLong(), any());
        }

        /** An exactly-full final page costs one extra empty read and then stops. */
        @Test
        @DisplayName("an exactly-full final page is followed by one empty read, then stops")
        void exactlyFullFinalPageTerminates() {
            int page = AssistanceNextActionRefreshService.PAGE_SIZE;
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(page, "ACCEPT", null));
            when(timelineRepository.findActionsForRollup(eq((long) page), any()))
                    .thenReturn(List.of());

            assertThat(service.refresh()).isEqualTo(1);
            verify(timelineRepository, org.mockito.Mockito.times(2))
                    .findActionsForRollup(anyLong(), any());
        }

        /** An empty timeline is a normal state, not a failure, and writes nothing. */
        @Test
        @DisplayName("an empty timeline writes nothing and still sweeps")
        void emptyTimelineIsNormal() {
            assertThat(service.refresh()).isZero();

            verify(rollupRepository, never()).save(any());
            // The sweep still runs: a rollup whose source rows all vanished must not keep reporting.
            verify(rollupRepository).deleteStale(any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("One stamp per pass, and the sweep")
    class StampAndSweepTests {

        /**
         * The sweep's predicate is "older than THIS run", so the pass must carry one instant.
         *
         * <p>A per-row {@code now()} would make rows written late in the pass look newer than rows
         * written early, and the sweep could not distinguish "not rewritten by this run" from "written
         * a few milliseconds sooner" — it would delete live cohorts.
         */
        @Test
        @DisplayName("every row written by one pass carries the same stamp, and the sweep uses it")
        void onePassOneStamp() {
            List<TimelineAction> rows = new ArrayList<>(events(5, "ACCEPT", CATEGORY));
            for (int i = 1; i <= 5; i++) {
                rows.add(new TimelineAction(100L + i, "in_progress", "RBIO_OFFICER", "AWARD", 8L));
            }
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(rows);

            LocalDateTime stamp = LocalDateTime.of(2026, 10, 6, 2, 0, 0);
            assertThat(service.recompute(stamp)).isEqualTo(4);

            assertThat(savedRows()).hasSize(4)
                    .allSatisfy(row -> assertThat(row.getRefreshedAt()).isEqualTo(stamp));
            verify(rollupRepository).deleteStale(stamp);
        }

        /**
         * The sweep is reached only after a COMPLETE pass.
         *
         * <p>A partial pass has stamped only the cohorts it got to, so sweeping on its stamp would
         * delete every cohort it had not reached — and the rail would go silent on them until the next
         * successful run.
         */
        @Test
        @DisplayName("a pass that fails mid-upsert does not reach the stale sweep")
        void partialPassDoesNotSweep() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", CATEGORY));
            when(rollupRepository.save(any()))
                    .thenThrow(new RuntimeException("deadlock on UK_ANA_COHORT"));

            assertThatThrownBy(() -> service.recompute(LocalDateTime.now()))
                    .isInstanceOf(RuntimeException.class);

            verify(rollupRepository, never()).deleteStale(any());
        }

        /** Through {@code refresh()} the same failure is swallowed, and still does not sweep. */
        @Test
        @DisplayName("the swallowed form of that failure also leaves the rollup as it was")
        void failedPassThroughRefreshDoesNotSweep() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));
            when(rollupRepository.save(any()))
                    .thenThrow(new RuntimeException("deadlock on UK_ANA_COHORT"));

            assertThat(service.refresh()).isZero();

            verify(rollupRepository, never()).deleteStale(any());
            verify(lockRepository).release(anyString(), any());
        }

        /**
         * An existing cohort is UPDATED rather than duplicated — the unique key is the idempotency, and
         * a truncate-and-reload would leave the rail silent for the duration of every refresh.
         */
        @Test
        @DisplayName("an existing cohort row is overwritten, not duplicated")
        void existingCohortIsUpdatedInPlace() {
            when(timelineRepository.findActionsForRollup(eq(0L), any()))
                    .thenReturn(events(5, "ACCEPT", null));
            AssistanceNextAction existing = AssistanceNextAction.builder()
                    .id(42L)
                    .fromStatus(STATUS)
                    .performedByRole(ROLE)
                    .categoryKey(AssistanceNextAction.CATEGORY_AGNOSTIC)
                    .action("REJECT")
                    .occurrences(9L)
                    .cohortTotal(9L)
                    .refreshedAt(LocalDateTime.of(2026, 1, 1, 0, 0))
                    .build();
            when(rollupRepository.findCohort(STATUS, ROLE, AssistanceNextAction.CATEGORY_AGNOSTIC))
                    .thenReturn(java.util.Optional.of(existing));

            service.refresh();

            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getId()).isEqualTo(42L);
                assertThat(row.getAction()).isEqualTo("ACCEPT");
                assertThat(row.getOccurrences()).isEqualTo(5L);
            });
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Tie-break stability")
    class TieBreakTests {

        /**
         * A tie must resolve the same way every time.
         *
         * <p>{@code HashMap} iteration order is unspecified, so without the alphabetical tiebreak two
         * runs over identical data could store different winners and the rail would appear to change
         * its advice for no reason — the failure an officer would describe as "it keeps telling me
         * something different", and which no log line records.
         */
        @Test
        @DisplayName("a tied cohort picks the alphabetically-first action")
        void tiedCohortPicksAlphabeticallyFirst() {
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(List.of(
                    // Fed REJECT-first so a first-seen-wins implementation would pick REJECT.
                    new TimelineAction(1L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(2L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(3L, STATUS, ROLE, "REJECT", null),
                    new TimelineAction(4L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(5L, STATUS, ROLE, "ACCEPT", null),
                    new TimelineAction(6L, STATUS, ROLE, "ACCEPT", null)));

            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getAction()).isEqualTo("ACCEPT");
                assertThat(row.getOccurrences()).isEqualTo(3L);
                // Exactly half, which is the floor and clears it, so the tie is genuinely reached.
                assertThat(row.getCohortTotal()).isEqualTo(6L);
            });
        }

        /**
         * A three-way split is refused, and refused on EVERY pass.
         *
         * <p>Ten passes rather than one because the thing that would break is order-dependence: a
         * winner chosen by {@code HashMap} iteration could clear the floor on some passes and not
         * others, so the rail would flicker between a suggestion and silence. The refusal has to be as
         * stable as the suggestion.
         */
        @Test
        @DisplayName("a three-way split is refused on every pass, not just the first")
        void splitCohortIsRefusedStably() {
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(List.of(
                    new TimelineAction(1L, STATUS, ROLE, "ZZ_ESCALATE", null),
                    new TimelineAction(2L, STATUS, ROLE, "ZZ_ESCALATE", null),
                    new TimelineAction(3L, STATUS, ROLE, "MM_FORWARD", null),
                    new TimelineAction(4L, STATUS, ROLE, "MM_FORWARD", null),
                    new TimelineAction(5L, STATUS, ROLE, "AA_ACCEPT", null),
                    new TimelineAction(6L, STATUS, ROLE, "AA_ACCEPT", null)));

            for (int pass = 0; pass < 10; pass++) {
                service.recompute(LocalDateTime.now());
            }

            // Three-way tie at 2 of 6 — which is BELOW the confidence floor, so nothing is written.
            // The stability that matters here is therefore the stability of the refusal.
            verify(rollupRepository, never()).save(any());
        }

        /** A two-way tie at exactly half is written, and the winner never varies. */
        @Test
        @DisplayName("ten passes over a tied cohort store one and the same winner")
        void repeatedPassesStoreTheSameWinner() {
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(List.of(
                    new TimelineAction(1L, STATUS, ROLE, "ZZ_ESCALATE", null),
                    new TimelineAction(2L, STATUS, ROLE, "ZZ_ESCALATE", null),
                    new TimelineAction(3L, STATUS, ROLE, "ZZ_ESCALATE", null),
                    new TimelineAction(4L, STATUS, ROLE, "MM_FORWARD", null),
                    new TimelineAction(5L, STATUS, ROLE, "MM_FORWARD", null),
                    new TimelineAction(6L, STATUS, ROLE, "MM_FORWARD", null)));

            for (int pass = 0; pass < 10; pass++) {
                service.recompute(LocalDateTime.now());
            }

            assertThat(savedRows()).hasSize(10)
                    .allSatisfy(row -> assertThat(row.getAction()).isEqualTo("MM_FORWARD"));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Normalisation")
    class NormalisationTests {

        /**
         * Stray whitespace would key a cohort no read could ever reach.
         *
         * <p>The rail looks the cohort up by the complaint's current status, which carries no trailing
         * space, so {@code "assigned "} would be a row that is written every hour and read never.
         */
        @Test
        @DisplayName("padded timeline values fold into the same cohort as clean ones")
        void paddedValuesAreTrimmedIntoOneCohort() {
            when(timelineRepository.findActionsForRollup(eq(0L), any())).thenReturn(List.of(
                    new TimelineAction(1L, " assigned ", " CEPC_DO ", " ACCEPT ", null),
                    new TimelineAction(2L, "assigned", "CEPC_DO", "ACCEPT", null),
                    new TimelineAction(3L, "assigned ", "CEPC_DO", "ACCEPT", null),
                    new TimelineAction(4L, "assigned", " CEPC_DO", "ACCEPT ", null),
                    new TimelineAction(5L, "assigned", "CEPC_DO", "ACCEPT", null)));

            assertThat(service.refresh()).isEqualTo(1);
            assertThat(savedRows()).singleElement().satisfies(row -> {
                assertThat(row.getFromStatus()).isEqualTo("assigned");
                assertThat(row.getPerformedByRole()).isEqualTo("CEPC_DO");
                assertThat(row.getAction()).isEqualTo("ACCEPT");
                assertThat(row.getCohortTotal()).isEqualTo(5L);
            });
        }
    }
}
