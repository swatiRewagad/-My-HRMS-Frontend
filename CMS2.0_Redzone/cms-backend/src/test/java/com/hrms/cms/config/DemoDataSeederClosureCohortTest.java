package com.hrms.cms.config;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hrms.cms.service.AssistanceRailService.MIN_CLOSURE_SAMPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the demo fixture can actually support the measurement it is supposed to support.
 *
 * <h2>The gap this closes</h2>
 * The assistance rail's {@code category-closure-time} prior reports a median only when a category has
 * at least {@link com.hrms.cms.service.AssistanceRailService#MIN_CLOSURE_SAMPLE} closed complaints with
 * a non-negative filing-to-closure window. The seeder's 60 random rows could never clear that: one
 * status in six is "closed", spread across eight categories, which measured out at a maximum of
 * **three** closed rows in any single category. So the prior was structurally unable to fire on demo
 * data — not because the logic was wrong, but because the fixture had nothing for it to measure.
 *
 * <p>Two things were wrong and both are asserted here. The window arithmetic needed
 * {@code Complaint.onCreate()} to stop overwriting {@code createdAt} (see
 * {@code ComplaintTimestampTest}); the sample size needed this cohort.
 *
 * <p><b>The floor is referenced, not copied.</b> Importing {@code MIN_CLOSURE_SAMPLE} means that if
 * somebody raises the statistical guard to 10 this test fails and the fixture gets fixed, instead of
 * the two drifting apart silently — which is the failure mode that produced the gap in the first place.
 */
@ExtendWith(MockitoExtension.class)
class DemoDataSeederClosureCohortTest {

    @Mock
    private ComplaintRepository complaintRepo;

    @InjectMocks
    private DemoDataSeeder seeder;

    /**
     * Runs the seeder and returns what it would have written to the report-builder batch.
     *
     * <p>{@code atLeastOnce} and the FIRST captured batch, not {@code verify(...)} and the only one:
     * {@link Invariants#medianIsStableAcrossRuns} seeds twice on purpose, and a strict single-invocation
     * verify would fail there for a reason that has nothing to do with the fixture. The first invocation,
     * not the last, because {@code seedInternal} also calls {@code seedCepcDoComplaints} afterwards —
     * a separate fixture under its own guard and numbering scheme (CMP-* against a department/role
     * query, not CMS-DEMO-* against this seeder's own count) that this cohort's assertions are not about.
     * Invocations are cleared before each run so a second run's first batch is not the first run's.
     */
    private List<Complaint> seed() {
        org.mockito.Mockito.clearInvocations(complaintRepo);
        when(complaintRepo.countByComplaintNumberStartingWith(anyString())).thenReturn(0L);

        seeder.run();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Complaint>> captor = ArgumentCaptor.forClass(List.class);
        verify(complaintRepo, atLeastOnce()).saveAll(captor.capture());
        return captor.getAllValues().get(0);
    }

    /**
     * The closed cohort only, excluding the 60 random rows.
     *
     * <p>Identified by the name the cohort stamps on its rows. The two populations have to be
     * separable because they are held to different standards: the cohort is a measurement fixture and
     * is asserted tightly, whereas the random rows are report-builder filler whose pre-existing
     * quirks (see {@link Windows#randomRowsCanCloseInTheFuture}) are not this change's business.
     */
    private List<Complaint> cohortOnly(List<Complaint> all) {
        return all.stream()
                .filter(c -> c.getComplainantName() != null
                        && c.getComplainantName().startsWith("Demo Closed User"))
                .toList();
    }

    /** Closed rows that a closure-window query would actually accept. */
    private List<Complaint> measurableClosures(List<Complaint> all) {
        return all.stream()
                .filter(c -> c.getCategoryId() != null)
                .filter(c -> c.getClosedAt() != null && c.getCreatedAt() != null)
                .filter(c -> !c.getClosedAt().isBefore(c.getCreatedAt()))
                .toList();
    }

    @Nested
    @DisplayName("The fixture clears the rail's sample floor")
    class SampleFloor {

        @Test
        @DisplayName("every category with a cohort has at least MIN_CLOSURE_SAMPLE measurable closures")
        void everyCategoryClearsTheFloor() {
            Map<Long, Long> perCategory = measurableClosures(seed()).stream()
                    .collect(Collectors.groupingBy(Complaint::getCategoryId, Collectors.counting()));

            assertThat(perCategory).isNotEmpty();
            assertThat(perCategory.values())
                    .as("a category below the floor is one the rail stays silent on")
                    .allSatisfy(count -> assertThat(count)
                            .isGreaterThanOrEqualTo((long) MIN_CLOSURE_SAMPLE));
        }

        @Test
        @DisplayName("the cohort covers every category the subjects map to")
        void coversEveryCategory() {
            List<Complaint> all = seed();

            long categoriesPresent = all.stream()
                    .map(Complaint::getCategoryId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            long categoriesMeasurable = measurableClosures(all).stream()
                    .map(Complaint::getCategoryId)
                    .distinct()
                    .count();

            assertThat(categoriesMeasurable).isEqualTo(categoriesPresent);
        }
    }

    @Nested
    @DisplayName("Every cohort row carries a usable window")
    class Windows {

        @Test
        @DisplayName("closedAt is strictly after createdAt — the negative-window defect cannot recur")
        void noNegativeWindows() {
            List<Complaint> closedWithCategory = seed().stream()
                    .filter(c -> "closed".equals(c.getStatus()))
                    .filter(c -> c.getClosedAt() != null)
                    .toList();

            assertThat(closedWithCategory).isNotEmpty();
            assertThat(closedWithCategory).allSatisfy(c ->
                    assertThat(c.getClosedAt())
                            .as("closure before filing is what the rail's filter discards")
                            .isAfter(c.getCreatedAt()));
        }

        @Test
        @DisplayName("windows are whole days, so a day-granularity median is not rounded to zero")
        void windowsAreAtLeastOneDay() {
            assertThat(measurableClosures(seed())).allSatisfy(c ->
                    assertThat(Duration.between(c.getCreatedAt(), c.getClosedAt()).toDays())
                            .isGreaterThanOrEqualTo(1L));
        }

        @Test
        @DisplayName("no cohort row is dated in the future")
        void nothingInTheFuture() {
            LocalDateTime now = LocalDateTime.now();

            assertThat(cohortOnly(seed())).allSatisfy(c -> {
                assertThat(c.getCreatedAt()).isBeforeOrEqualTo(now);
                assertThat(c.getClosedAt()).isBeforeOrEqualTo(now);
            });
        }

        @Test
        @DisplayName("PRE-EXISTING: the random rows can close in the future — scoped, not fixed")
        void randomRowsCanCloseInTheFuture() {
            // Not a regression and not this change's business, but recorded rather than silently
            // excluded. The random generator picks createdAt as little as 0 days ago and then adds up
            // to 20 days of resolution and closure on top, so a handful of rows close after now(). At
            // seed 42 that is 2 of 60. They are harmless to the closure prior -- a future closure still
            // yields a POSITIVE window, so it is measured rather than discarded -- but a dashboard
            // showing "closed" complaints dated next week is a separate fixture wart worth knowing
            // about before someone reports it as a bug in the rail.
            List<Complaint> all = seed();
            List<Complaint> random = all.stream().filter(c -> !cohortOnly(all).contains(c)).toList();

            assertThat(random).hasSize(60);
            assertThat(random).allSatisfy(c ->
                    assertThat(c.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now()));
        }

        @Test
        @DisplayName("resolvedAt sits between filing and closure")
        void resolutionPrecedesClosure() {
            assertThat(measurableClosures(seed())).allSatisfy(c -> {
                assertThat(c.getResolvedAt()).isNotNull();
                assertThat(c.getResolvedAt()).isAfterOrEqualTo(c.getCreatedAt());
                assertThat(c.getResolvedAt()).isBeforeOrEqualTo(c.getClosedAt());
            });
        }
    }

    @Nested
    @DisplayName("Determinism and identity")
    class Invariants {

        @Test
        @DisplayName("the median per category is stable across runs")
        void medianIsStableAcrossRuns() {
            // A fixture whose median moves on every boot makes any assertion about it flaky, and the
            // figure is shown to an officer as "this category closes in N days".
            assertThat(mediansByCategory()).isEqualTo(mediansByCategory());
        }

        private Map<Long, Long> mediansByCategory() {
            return measurableClosures(seed()).stream().collect(Collectors.groupingBy(
                    Complaint::getCategoryId,
                    Collectors.collectingAndThen(
                            Collectors.mapping(
                                    c -> Duration.between(c.getCreatedAt(), c.getClosedAt()).toDays(),
                                    Collectors.toList()),
                            days -> days.stream().sorted().toList().get(days.size() / 2))));
        }

        @Test
        void complaintNumbersAreUnique() {
            List<String> numbers = seed().stream().map(Complaint::getComplaintNumber).toList();

            assertThat(numbers).doesNotHaveDuplicates();
            assertThat(numbers).allMatch(n -> n.startsWith("CMS-DEMO-"));
        }
    }

    @Nested
    @DisplayName("The skip guard counts this seeder's own series")
    class SkipGuard {

        @Test
        @DisplayName("skips when its own rows exist")
        void skipsWhenOwnRowsExist() {
            when(complaintRepo.countByComplaintNumberStartingWith(anyString())).thenReturn(60L);

            seeder.run();

            verify(complaintRepo, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
        }

        @Test
        @DisplayName("seeds even on a database full of other series")
        void seedsDespiteUnrelatedRows() {
            // The old guard was count() >= 80 over the whole table. The shared dev database holds ~3000
            // CMP-*/N2026*/CEPC-* rows, so it short-circuited permanently and the demo set could never
            // be rewritten -- which is why the corrupt timestamps persisted for as long as they did.
            when(complaintRepo.countByComplaintNumberStartingWith(anyString())).thenReturn(0L);

            seeder.run();

            verify(complaintRepo, atLeastOnce()).saveAll(org.mockito.ArgumentMatchers.anyList());
        }
    }
}
