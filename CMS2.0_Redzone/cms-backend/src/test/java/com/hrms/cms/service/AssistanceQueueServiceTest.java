package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceQueueResponse;
import com.hrms.cms.repository.AssistanceQueueRepository;
import com.hrms.cms.repository.projection.AssistanceQueueProjections.QueueDeadlineCounts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AssistanceQueueService}: the floors, the two numbers, and the tenancy scope.
 *
 * <h2>Plain Mockito, not {@code @DataJpaTest}</h2>
 * This module has no H2 on its test classpath, so there is no in-memory database to run the JPQL
 * against. The repository is therefore mocked and what is under test is the DECISION layer: whether a
 * signal is emitted at all, what the two numbers mean, and — the part a mock proves better than a real
 * database would — exactly which scope arguments are handed to the query. The query PLANS are evidenced
 * by {@code EXPLAIN} against the live register and recorded on the repository's javadoc; a mock cannot
 * and does not claim to verify those.
 *
 * <h2>The clock is injected, so these tests do not depend on the day they run</h2>
 * The window boundary is the whole point of the feature, and a test that computed "today" from the
 * system clock would pass or fail depending on the date, which is the one thing a deadline test must
 * not do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceQueueService: deadline triage")
class AssistanceQueueServiceTest {

    /** A fixed "now". Every expected bound in this file is relative to it. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 7, 9, 0);
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-10-07T09:00:00Z"), ZoneOffset.UTC);

    private static final String OFFICER = "cepc_do_001";
    private static final Set<String> CEPC_ROLES = Set.of("CEPC_DO");

    @Mock private AssistanceQueueRepository repository;
    @Mock private RbioStatusVocabulary statusVocabulary;

    private AssistanceQueueService service;

    @BeforeEach
    void setUp() {
        when(statusVocabulary.closedStatuses())
                .thenReturn(List.of("resolved", "closed", "rejected", "withdrawn",
                        "adjudicated", "conciliated"));
        service = new AssistanceQueueService(repository, statusVocabulary, FIXED);
        ReflectionTestUtils.setField(service, "windowHours", 48);
    }

    /** Stubs the officer-assigned query with the given counts. */
    private void officerQueue(long total, long breaching, long overdue) {
        when(repository.countOfficerQueue(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new QueueDeadlineCounts(total, breaching, overdue)));
    }

    private void rolePool(long total, long breaching, long overdue) {
        when(repository.countRolePoolQueue(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Optional.of(new QueueDeadlineCounts(total, breaching, overdue)));
    }

    private AssistanceQueueResponse.Signal onlySignal(AssistanceQueueResponse response) {
        assertThat(response.signals()).hasSize(1);
        return response.signals().get(0);
    }

    @Nested
    @DisplayName("the floors: glow only when actionable")
    class Floors {

        /**
         * THE case the brief names explicitly: zero breaching produces NO SIGNAL, not a signal saying
         * zero. An ambient panel that reports the absence of a problem is what officers learn to
         * ignore, and once ignored it is worthless on the day it has something to say.
         *
         * <p>This is also the state of the live register — 4402 of 4403 complaints carry no deadline —
         * so this is the behaviour almost every real call exercises today.
         */
        @Test
        @DisplayName("zero breaching cases emit NO signal, never '0 of 597'")
        void zeroBreachingIsSilent() {
            officerQueue(597, 0, 0);

            AssistanceQueueResponse response = service.triage(OFFICER, CEPC_ROLES);

            assertThat(response.signals()).isEmpty();
            assertThat(response.glow()).isFalse();
            assertThat(response.count()).isZero();
        }

        /**
         * "1 of 1 case breaches" is not triage — it is the officer's only case, which they are already
         * looking at, and the sentence implies a prioritisation that does not exist.
         */
        @ParameterizedTest(name = "a queue of {0} is below the ratio floor")
        @CsvSource({"1,1", "2,1", "2,2"})
        @DisplayName("a queue smaller than three emits no signal even when everything breaches")
        void tinyQueuesAreSilent(long total, long breaching) {
            officerQueue(total, breaching, 0);

            assertThat(service.triage(OFFICER, CEPC_ROLES).signals()).isEmpty();
        }

        @Test
        @DisplayName("a queue of exactly three with one breaching DOES signal")
        void theFloorIsInclusive() {
            officerQueue(3, 1, 0);

            assertThat(service.triage(OFFICER, CEPC_ROLES).signals()).hasSize(1);
        }

        /**
         * A numerator above its denominator means the scope was computed wrong. Suppressed rather than
         * clamped: a clamped number would render a plausible sentence and hide the fault.
         */
        @Test
        @DisplayName("a numerator above the denominator is suppressed, not clamped")
        void impossibleCountsAreSuppressed() {
            officerQueue(3, 4, 0);

            assertThat(service.triage(OFFICER, CEPC_ROLES).signals()).isEmpty();
        }

        /** {@code glow} is derived from signal presence, so the two cannot drift apart. */
        @Test
        @DisplayName("glow is true exactly when a signal is present")
        void glowTracksSignalPresence() {
            officerQueue(14, 3, 0);
            assertThat(service.triage(OFFICER, CEPC_ROLES).glow()).isTrue();

            officerQueue(14, 0, 0);
            assertThat(service.triage(OFFICER, CEPC_ROLES).glow()).isFalse();
        }
    }

    @Nested
    @DisplayName("both numbers reach the client")
    class NumeratorAndDenominator {

        /** The brief's own example sentence, end to end. */
        @Test
        @DisplayName("'3 of your 14 cases breach within 48h'")
        void theBriefsExampleSentence() {
            officerQueue(14, 3, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.title()).isEqualTo("3 of your 14 cases breach within 48h");
            assertThat(signal.kind()).isEqualTo(AssistanceQueueService.KIND_DEADLINE_TRIAGE);
        }

        /**
         * THE TRAP, pinned on the actual numeric string.
         *
         * <p>The frontend's {@code labelParams} folds the signal's {@code count} FIELD into
         * {@code params['count']} only when params lacks the key. A kind whose field meant something
         * other than its {@code {{count}}} placeholder would therefore render a FALSE sentence in all 13
         * locales while the English {@code title} stayed correct — a bug invisible to anyone testing in
         * English. So this asserts the literal {@code "3"}, not {@code signal.count()}: comparing the
         * two fields to each other would pass if both were the denominator.
         */
        @Test
        @DisplayName("params['count'] is the NUMERATOR '3', and params['total'] the denominator '14'")
        void paramsCountIsTheNumeratorNotTheQueueSize() {
            officerQueue(14, 3, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.params().get("count"))
                    .as("the breaching count, the '3' — if this were '14' every locale would lie")
                    .isEqualTo("3");
            assertThat(signal.params().get("total"))
                    .as("the queue size, the '14'")
                    .isEqualTo("14");
            // And the FIELD agrees with the param, so the client's fold is a no-op rather than a
            // second source of truth.
            assertThat(signal.count()).isEqualTo(3L);
            assertThat(signal.params().get("count")).isEqualTo(String.valueOf(signal.count()));
        }

        /**
         * The denominator is not decoration. A locale or a client that dropped it would leave the
         * officer with a figure they cannot act on, which Brief 21 says will be distrusted, correctly.
         */
        @Test
        @DisplayName("params carries all four documented names, none null")
        void allFourParamsArePresent() {
            officerQueue(14, 3, 2);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.params())
                    .containsEntry("count", "3")
                    .containsEntry("total", "14")
                    .containsEntry("hours", "48")
                    .containsEntry("overdue", "2");
        }

        @Test
        @DisplayName("the overdue sub-count becomes prose, and is absent when nothing is overdue")
        void overdueDetailOnlyAppearsWhenSomethingIsOverdue() {
            officerQueue(14, 3, 2);
            assertThat(onlySignal(service.triage(OFFICER, CEPC_ROLES)).detail())
                    .isEqualTo("2 of these are already overdue");

            officerQueue(14, 3, 1);
            assertThat(onlySignal(service.triage(OFFICER, CEPC_ROLES)).detail())
                    .isEqualTo("1 of these is already overdue");

            officerQueue(14, 3, 0);
            assertThat(onlySignal(service.triage(OFFICER, CEPC_ROLES)).detail())
                    .as("'0 already overdue' is an answer to a question nobody asked")
                    .isNull();
        }

        /** Relative, never absolute: an absolute URL from the server is an open-redirect surface. */
        @Test
        @DisplayName("the link is a relative app route")
        void theLinkIsRelative() {
            officerQueue(14, 3, 0);

            assertThat(onlySignal(service.triage(OFFICER, CEPC_ROLES)).link())
                    .startsWith("/")
                    .doesNotStartWith("//")
                    .doesNotContain("://");
        }
    }

    @Nested
    @DisplayName("the configurable window")
    class Window {

        @Test
        @DisplayName("48h is the default and reaches both the sentence and all four query bounds")
        void theDefaultWindowIsFortyEightHours() {
            officerQueue(14, 3, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));
            assertThat(signal.params().get("hours")).isEqualTo("48");

            // The SLA column is a timestamp, so 48h is honoured to the HOUR, not rounded to a day.
            // The RE column is a date, so it gets the same two instants' calendar days.
            verify(repository).countOfficerQueue(eq(OFFICER), any(), any(),
                    eq(NOW), eq(NOW.plusHours(48)), eq(TODAY), eq(TODAY.plusDays(2)));
        }

        @Test
        @DisplayName("a 72h window widens the query bounds and the sentence together")
        void aWiderWindowIsHonouredEverywhere() {
            ReflectionTestUtils.setField(service, "windowHours", 72);
            officerQueue(14, 5, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.title()).isEqualTo("5 of your 14 cases breach within 72h");
            verify(repository).countOfficerQueue(eq(OFFICER), any(), any(),
                    eq(NOW), eq(NOW.plusHours(72)), eq(TODAY), eq(TODAY.plusDays(3)));
        }

        /**
         * A sub-day remainder is exact on the timestamp column and truncated on the date one.
         *
         * <p>36h from 09:00 is 21:00 tomorrow. {@code sla_deadline} is compared against that instant
         * exactly, so nothing is lost there. {@code re_response_deadline} stores no time of day, so its
         * bound is the horizon's own calendar DAY — tomorrow, not the day after. Extending it to the day
         * after would count an RE deadline up to 24h BEYOND the window the sentence claims, and a
         * warning that overstates its own horizon teaches officers the number is approximate. The
         * truncation can only under-report the RE leg by less than one day, and that leg is populated on
         * thirteen of 4403 rows; the timestamp leg, which carries the feature, is exact.
         */
        @Test
        @DisplayName("a sub-day window is exact on the timestamp bound and truncated on the date bound")
        void partialDaysAreExactOnTheTimestampAndTruncatedOnTheDate() {
            ReflectionTestUtils.setField(service, "windowHours", 36);
            officerQueue(14, 3, 0);

            service.triage(OFFICER, CEPC_ROLES);

            verify(repository).countOfficerQueue(eq(OFFICER), any(), any(),
                    eq(NOW), eq(NOW.plusHours(36)), eq(TODAY), eq(TODAY.plusDays(1)));
        }

        /**
         * A zero or negative setting would make the horizon equal to or earlier than {@code now} while
         * the sentence still CLAIMED a forward-looking window. Clamped rather than rejected, because a
         * typo'd ConfigMap must not take a screen down.
         */
        @ParameterizedTest(name = "windowHours={0} clamps to 1")
        @CsvSource({"0", "-5"})
        @DisplayName("a non-positive window clamps to one hour rather than inverting")
        void nonPositiveWindowsClamp(int configured) {
            ReflectionTestUtils.setField(service, "windowHours", configured);
            officerQueue(14, 3, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.params().get("hours")).isEqualTo("1");
            verify(repository).countOfficerQueue(eq(OFFICER), any(), any(),
                    eq(NOW), eq(NOW.plusHours(1)), eq(TODAY), eq(TODAY));
        }

        /**
         * The horizon is strictly AFTER the overdue boundary, which is what makes "breaching" a
         * superset of "overdue" rather than a disjoint bucket. A configuration that inverted them would
         * report an overdue count larger than the breaching count it is a subset of.
         */
        @Test
        @DisplayName("the overdue boundary is now, and the horizon is strictly after it")
        void theOverdueBoundaryIsNow() {
            officerQueue(14, 3, 0);

            service.triage(OFFICER, CEPC_ROLES);

            ArgumentCaptor<LocalDateTime> now = ArgumentCaptor.forClass(LocalDateTime.class);
            ArgumentCaptor<LocalDateTime> horizon = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(repository).countOfficerQueue(anyString(), any(), any(),
                    now.capture(), horizon.capture(), any(), any());

            assertThat(now.getValue()).isEqualTo(NOW);
            assertThat(horizon.getValue()).isAfter(now.getValue());
        }

        /**
         * The two legs must agree on both queries. A role pool that measured "breaching" over a
         * different window than the personal queue would make an officer's number change MEANING the
         * day their last assigned case closed, with nothing on screen to indicate the switch.
         */
        @Test
        @DisplayName("the role-pool query receives byte-identical bounds to the officer query")
        void bothQueriesShareTheSameWindow() {
            officerQueue(0, 0, 0);
            rolePool(100, 4, 0);

            service.triage(OFFICER, CEPC_ROLES);

            verify(repository).countOfficerQueue(eq(OFFICER), any(), any(),
                    eq(NOW), eq(NOW.plusHours(48)), eq(TODAY), eq(TODAY.plusDays(2)));
            verify(repository).countRolePoolQueue(any(), any(), any(),
                    eq(NOW), eq(NOW.plusHours(48)), eq(TODAY), eq(TODAY.plusDays(2)));
        }
    }

    @Nested
    @DisplayName("tenancy: an out-of-scope case is absent from the count")
    class Tenancy {

        /**
         * The per-role negative tests. Each asserts the DEPARTMENT scope handed to the query, which is
         * the mechanism by which another department's cases are absent from this officer's totals.
         *
         * <p>This is the right level to assert at, and the reason is worth stating: a mock repository
         * cannot demonstrate that a CEPC row is missing from an RBIO count, because the mock returns
         * whatever it is told to. What CAN be proved is that the query was asked a question whose answer
         * cannot include the other department — and since the predicate is
         * {@code department IN :departments}, pinning that argument pins the exclusion. The plan
         * evidence that the predicate is actually applied is the {@code EXPLAIN} on the repository.
         *
         * <p>Measured as load-bearing rather than theoretical: SEVEN officers in the live register hold
         * complaints in more than one department — {@code rbio_officer_002} has 22 rows across
         * {@code CEPC} and {@code RBIO}. Without the filter, "every row bearing my name" would include
         * cases the officer cannot open.
         */
        @ParameterizedTest(name = "{0} is scoped to {1} and nothing else")
        @CsvSource({
                "CEPC_DO,CEPC",
                "CEPC_REVIEWER,CEPC",
                "CEPC_INCHARGE,CEPC",
                "CEPC_CLOSING_AUTHORITY,CEPC",
                "RBIO_OFFICER,RBIO",
                "RBIO_ADJUDICATOR,RBIO",
                "RBIO_CONCILIATOR,RBIO",
                "RBIO_SUPERVISOR,RBIO",
                "RBIO_REVIEWER,RBIO",
                "RBIO_DEALING_OFFICIAL,RBIO",
                "CRPC_OFFICER,CRPC",
                "DEO,CRPC",
                "DO,CEPC"
        })
        @DisplayName("each role sees exactly its own department")
        void eachRoleIsScopedToItsOwnDepartment(String role, String expectedDepartment) {
            officerQueue(14, 3, 0);

            service.triage(OFFICER, Set.of(role));

            ArgumentCaptor<Collection<String>> departments = captureDepartments();
            assertThat(departments.getValue())
                    .as("%s must not be able to count another department's cases", role)
                    .containsExactly(expectedDepartment);
        }

        /**
         * The specific negative, stated as a non-containment rather than as a department list: an RBIO
         * adjudicator's query must be incapable of returning a CEPC row.
         */
        @Test
        @DisplayName("an RBIO role's scope EXCLUDES CEPC and CRPC")
        void rbioCannotSeeCepc() {
            officerQueue(15, 2, 0);

            service.triage("rbio.adjudicator", Set.of("RBIO_ADJUDICATOR"));

            assertThat(captureDepartments().getValue())
                    .doesNotContain("CEPC", "CRPC")
                    .containsExactly("RBIO");
        }

        @Test
        @DisplayName("a CEPC role's scope EXCLUDES RBIO and CRPC")
        void cepcCannotSeeRbio() {
            officerQueue(597, 4, 0);

            service.triage(OFFICER, CEPC_ROLES);

            assertThat(captureDepartments().getValue())
                    .doesNotContain("RBIO", "CRPC")
                    .containsExactly("CEPC");
        }

        /**
         * A genuinely multi-department officer gets BOTH, which is correct — those are both their
         * departments — and still not a third.
         */
        @Test
        @DisplayName("a multi-role officer gets the union of their departments, and no more")
        void multiRoleOfficersGetTheUnion() {
            officerQueue(22, 3, 0);

            service.triage("rbio_officer_002", Set.of("RBIO_OFFICER", "CEPC_DO"));

            assertThat(captureDepartments().getValue())
                    .containsExactlyInAnyOrder("RBIO", "CEPC")
                    .doesNotContain("CRPC");
        }

        /**
         * An unrecognised role contributes NOTHING rather than a wildcard, so a scoping gap fails
         * toward silence instead of toward every department. A citizen role lands here.
         */
        @Test
        @DisplayName("an unmapped role yields no scope, so no query runs at all")
        void anUnmappedRoleIsSilentRatherThanUnscoped() {
            AssistanceQueueResponse response =
                    service.triage("someone", Set.of("CITIZEN", "offline_access", "default-roles-cms"));

            assertThat(response.signals()).isEmpty();
            verifyNoInteractions(repository);
        }

        /** The {@code EmailSyndicationApiController:451} defect: no owner must never mean every row. */
        @Test
        @DisplayName("a null identity queries nothing rather than counting the whole register")
        void anUnresolvedCallerCountsNothing() {
            AssistanceQueueResponse response = service.triage(null, null);

            assertThat(response.signals()).isEmpty();
            verifyNoInteractions(repository);
        }

        @SuppressWarnings("unchecked")
        private ArgumentCaptor<Collection<String>> captureDepartments() {
            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(repository).countOfficerQueue(anyString(), captor.capture(), any(), any(), any(),
                    any(), any());
            return captor;
        }
    }

    @Nested
    @DisplayName("which queue is counted")
    class QueueSelection {

        /**
         * CHOSEN, not summed. A complaint bearing both the caller's name and their role would otherwise
         * be counted twice and could report a numerator above its denominator.
         */
        @Test
        @DisplayName("an officer with their own cases is NOT also counted against the role pool")
        void theOfficerQueueWinsWhenItIsNonEmpty() {
            officerQueue(14, 3, 0);
            rolePool(829, 40, 10);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.params().get("total")).isEqualTo("14");
            verify(repository, never()).countRolePoolQueue(any(), any(), any(), any(), any(),
                    any(), any());
        }

        /**
         * The fallback, so a supervisor or a newly-assigned officer with nothing in their name gets a
         * reading rather than silence.
         */
        @Test
        @DisplayName("an empty personal queue falls back to the role pool")
        void theRolePoolIsTheFallback() {
            officerQueue(0, 0, 0);
            rolePool(829, 5, 1);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage(OFFICER, CEPC_ROLES));

            assertThat(signal.params().get("total")).isEqualTo("829");
            assertThat(signal.params().get("count")).isEqualTo("5");
        }

        /** The whole role set reaches the IN list — never an arbitrary primary role. */
        @Test
        @DisplayName("the role pool query receives EVERY one of the caller's roles")
        void everyRoleReachesTheInList() {
            officerQueue(0, 0, 0);
            rolePool(100, 2, 0);

            service.triage(OFFICER, Set.of("CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> roles = ArgumentCaptor.forClass(Collection.class);
            verify(repository).countRolePoolQueue(any(), roles.capture(), any(), any(), any(),
                    any(), any());
            assertThat(roles.getValue())
                    .containsExactlyInAnyOrder("CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE");
        }

        @Test
        @DisplayName("a blank user id skips the personal query and goes straight to the pool")
        void aBlankUserIdUsesThePool() {
            rolePool(829, 6, 0);

            AssistanceQueueResponse.Signal signal = onlySignal(service.triage("  ", CEPC_ROLES));

            assertThat(signal.params().get("total")).isEqualTo("829");
            verify(repository, never()).countOfficerQueue(anyString(), any(), any(), any(), any(),
                    any(), any());
        }
    }

    @Nested
    @DisplayName("the closed-status vocabulary")
    class ClosedStatuses {

        /** The vocabulary comes from the operator-editable master, not from a constant here. */
        @Test
        @DisplayName("the status master's vocabulary is what the query excludes")
        void theVocabularyIsPassedThrough() {
            when(statusVocabulary.closedStatuses()).thenReturn(List.of("closed", "resolved"));
            officerQueue(14, 3, 0);

            service.triage(OFFICER, CEPC_ROLES);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
            verify(repository).countOfficerQueue(anyString(), any(), statuses.capture(), any(), any(),
                    any(), any());
            assertThat(statuses.getValue()).containsExactly("closed", "resolved");
        }

        /**
         * An empty {@code NOT IN} list is invalid JPQL, so an unseeded status master would throw rather
         * than degrade. The legacy six are substituted instead.
         */
        @Test
        @DisplayName("an empty vocabulary falls back to the legacy six rather than producing invalid JPQL")
        void anEmptyVocabularyFallsBack() {
            when(statusVocabulary.closedStatuses()).thenReturn(List.of());
            officerQueue(14, 3, 0);

            service.triage(OFFICER, CEPC_ROLES);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<String>> statuses = ArgumentCaptor.forClass(Collection.class);
            verify(repository).countOfficerQueue(anyString(), any(), statuses.capture(), any(), any(),
                    any(), any());
            assertThat(statuses.getValue())
                    .isNotEmpty()
                    .containsAll(RbioStatusVocabulary.legacyClosedStatuses());
        }
    }

    @Nested
    @DisplayName("fails silent")
    class FailsSilent {

        /**
         * §5.1 requires an ambient affordance to be off the critical path. A deadline warning that
         * throws beside a complaint an officer is trying to work is worse than no warning.
         */
        @Test
        @DisplayName("a repository failure degrades to an empty payload, never an exception")
        void aRepositoryFailureIsSwallowed() {
            when(repository.countOfficerQueue(anyString(), any(), any(), any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("query timeout"));

            AssistanceQueueResponse response = service.triage(OFFICER, CEPC_ROLES);

            assertThat(response.signals()).isEmpty();
            assertThat(response.glow()).isFalse();
        }

        /** A {@code SUM} over an empty row set is NULL in both MySQL and Oracle. */
        @Test
        @DisplayName("null aggregates from an empty queue normalise to zero rather than throwing")
        void nullAggregatesNormalise() {
            when(repository.countOfficerQueue(anyString(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Optional.of(new QueueDeadlineCounts(null, null, null)));
            rolePool(0, 0, 0);

            assertThat(service.triage(OFFICER, CEPC_ROLES).signals()).isEmpty();
        }

        @Test
        @DisplayName("an absent result row degrades to silence")
        void anEmptyOptionalIsSilent() {
            when(repository.countOfficerQueue(anyString(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());
            when(repository.countRolePoolQueue(any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Optional.empty());

            assertThat(service.triage(OFFICER, CEPC_ROLES).signals()).isEmpty();
        }
    }
}
