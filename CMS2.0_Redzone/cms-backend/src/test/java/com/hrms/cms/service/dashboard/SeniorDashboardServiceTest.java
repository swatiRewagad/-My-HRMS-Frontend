package com.hrms.cms.service.dashboard;

import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.HolidayRepository;
import com.hrms.cms.repository.projection.DashboardProjections;
import com.hrms.cms.service.BusinessHoursService;
import com.hrms.cms.service.TatCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Behaviour contract for the senior dashboard.
 *
 * <p>Rewritten alongside the {@code findAll()} → projection change: the service no longer reads
 * entities, so the mocks now return the aggregate projections. The ASSERTED VALUES are unchanged from
 * the entity-based version wherever the old assertion was still meaningful, which is the point — the
 * response shape and arithmetic are supposed to be identical.
 *
 * <p>{@code verify(repo, never()).findAll()} appears throughout deliberately. Without it a future
 * edit could reintroduce a whole-table read and every value assertion here would still pass.
 */
@ExtendWith(MockitoExtension.class)
class SeniorDashboardServiceTest {

    @Mock
    private ComplaintRepository complaintRepo;

    @Mock
    private ComplaintCategoryRepository categoryRepo;

    @Mock
    private HolidayRepository holidayRepo;

    private TatCalculationService tatService;
    private SeniorDashboardService service;

    @BeforeEach
    void setup() throws Exception {
        lenient().when(holidayRepo.existsByHolidayDate(any())).thenReturn(false);
        lenient().when(categoryRepo.findAll()).thenReturn(List.of());
        BusinessHoursService businessHours = new BusinessHoursService(holidayRepo);
        setField(businessHours, "businessHourStart", 9);
        setField(businessHours, "businessHourEnd", 18);
        setField(businessHours, "timezone", "Asia/Kolkata");
        tatService = new TatCalculationService(businessHours);
        service = new SeniorDashboardService(complaintRepo, categoryRepo, tatService);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static DashboardProjections.KeyCount kc(String key, long n) {
        return new DashboardProjections.KeyCount(key, n);
    }

    private static DashboardProjections.TatRow tat(String entity, LocalDateTime created) {
        return new DashboardProjections.TatRow(entity, created, null);
    }

    @Nested
    @DisplayName("Pipeline Summary")
    class PipelineTests {

        @Test
        void emptyRepo_returnsZeroes() {
            when(complaintRepo.countGroupedByStatus()).thenReturn(List.of());
            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("activeBacklog")).isEqualTo(0L);
            assertThat(result.get("resolutionRate")).isEqualTo(0L);
        }

        @Test
        void correctCounts() {
            // Same distribution as the entity-based version of this test: 2 pending, 1 each of
            // in_progress / resolved / closed / escalated.
            when(complaintRepo.countGroupedByStatus()).thenReturn(List.of(
                    kc("pending", 2), kc("in_progress", 1), kc("resolved", 1),
                    kc("closed", 1), kc("escalated", 1)));

            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("total")).isEqualTo(6L);
            assertThat(result.get("pending")).isEqualTo(2L);
            assertThat(result.get("inProgress")).isEqualTo(1L);
            assertThat(result.get("resolved")).isEqualTo(1L);
            assertThat(result.get("closed")).isEqualTo(1L);
            assertThat(result.get("escalated")).isEqualTo(1L);
            assertThat(result.get("activeBacklog")).isEqualTo(4L);
            assertThat(result.get("resolutionRate")).isEqualTo(33L);
        }

        @Test
        void totalIncludesStatusesNoPanelNamesExplicitly() {
            // 22 statuses exist in the live table but only 5 have a named field. 'total' is the SUM of
            // all groups, so an unnamed status must still be counted — otherwise resolutionRate is
            // computed against a denominator that silently omits most of the table.
            when(complaintRepo.countGroupedByStatus()).thenReturn(List.of(
                    kc("pending", 1), kc("assigned", 40), kc("conciliation", 9)));

            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("total")).isEqualTo(50L);
            assertThat(result.get("pending")).isEqualTo(1L);
            assertThat(result.get("activeBacklog")).isEqualTo(1L);
        }

        @Test
        void doesNotReadWholeTable() {
            when(complaintRepo.countGroupedByStatus()).thenReturn(List.of(kc("pending", 1)));
            service.getPipelineSummary();
            verify(complaintRepo, never()).findAll();
        }
    }

    @Nested
    @DisplayName("TAT Analytics")
    class TatTests {

        @Test
        void emptyRepo_returnsZeroes() {
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of());
            when(complaintRepo.countActiveForTat(anyList())).thenReturn(0L);

            Map<String, Object> result = service.getTatAnalytics();
            assertThat(result.get("totalActive")).isEqualTo(0);
            assertThat(result.get("breachRate")).isEqualTo(0L);
        }

        @Test
        void detectsBreachedComplaints() {
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of(
                    tat("SBI", LocalDateTime.now().minusDays(60)),
                    tat("HDFC", LocalDateTime.now().minusDays(2))));
            when(complaintRepo.countActiveForTat(anyList())).thenReturn(2L);

            Map<String, Object> result = service.getTatAnalytics();
            assertThat((long) result.get("breached")).isGreaterThanOrEqualTo(1);
            assertThat((int) result.get("totalActive")).isEqualTo(2);
        }

        @Test
        void closedAndWithdrawnAreExcludedByTheQuery() {
            // The exclusion moved from a Java filter into the query predicate, so what this can still
            // assert is that the service ASKS for the right exclusion set. Losing 'withdrawn' here
            // would inflate the active backlog on every management report.
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of(
                    tat("SBI", LocalDateTime.now().minusDays(5))));
            when(complaintRepo.countActiveForTat(anyList())).thenReturn(1L);

            Map<String, Object> result = service.getTatAnalytics();
            assertThat((int) result.get("totalActive")).isEqualTo(1);

            verify(complaintRepo).findActiveTatRows(
                    argThat(s -> s.containsAll(List.of("closed", "withdrawn"))), any());
            verify(complaintRepo, never()).findAll();
        }

        @Test
        void duplicateTimestampPairsAreCollapsedButStillCountedIndividually() {
            // The collapse is an optimisation, not a de-duplication: 741 live rows share 38 pairs, and
            // all 741 must still appear in the totals. Five rows with one identical pair => five rows
            // counted, one TAT computation.
            LocalDateTime created = LocalDateTime.now().minusDays(60);
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of(
                    tat("SBI", created), tat("SBI", created), tat("HDFC", created),
                    tat("PNB", created), tat("PNB", created)));
            when(complaintRepo.countActiveForTat(anyList())).thenReturn(5L);

            Map<String, Object> result = service.getTatAnalytics();
            assertThat((int) result.get("totalActive")).isEqualTo(5);
            assertThat((long) result.get("breached") + (long) result.get("atRisk")
                    + (long) result.get("onTrack")).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("Bottlenecks")
    class BottleneckTests {

        private void stubEmptyExcept() {
            lenient().when(complaintRepo.countByDepartmentForStatuses(anyList())).thenReturn(List.of());
            lenient().when(complaintRepo.countByCategoryForStatuses(anyList())).thenReturn(List.of());
            lenient().when(complaintRepo.findAllPriorityValues()).thenReturn(List.of());
            lenient().when(complaintRepo.countUnassignedByDepartment(anyList())).thenReturn(List.of());
        }

        @Test
        void groupsByDepartment() {
            stubEmptyExcept();
            when(complaintRepo.countByDepartmentForStatuses(anyList()))
                    .thenReturn(List.of(kc("RBIO", 2), kc("CEPC", 1)));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> byDept = (Map<String, Long>) result.get("backlogByDepartment");
            assertThat(byDept.get("RBIO")).isEqualTo(2L);
            assertThat(byDept.get("CEPC")).isEqualTo(1L);
            verify(complaintRepo, never()).findAll();
        }

        @Test
        void unassignedDetection() {
            stubEmptyExcept();
            when(complaintRepo.countUnassignedByDepartment(anyList()))
                    .thenReturn(List.of(kc("RBIO", 1)));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> unassignedMap = (Map<String, Long>) result.get("unassignedByDepartment");
            assertThat(unassignedMap.get("RBIO")).isEqualTo(1L);
            assertThat(unassignedMap.containsKey("CEPC")).isFalse();
        }

        @Test
        void backlogAsksForPendingAndInProgressOnly() {
            stubEmptyExcept();
            service.getBottlenecks();
            verify(complaintRepo).countByDepartmentForStatuses(
                    argThat(s -> s.size() == 2 && s.containsAll(List.of("pending", "in_progress"))));
            // Unassigned is 'pending' only, matching the predicate this replaced.
            verify(complaintRepo).countUnassignedByDepartment(argThat(s -> s.equals(List.of("pending"))));
        }

        @Test
        void priorityBucketsAreCaseSensitive() {
            // THE load-bearing test of this change set. MySQL collates COMPLAINTS case-insensitively,
            // so a SQL GROUP BY would report MEDIUM+medium as ONE bucket of 6. The service must keep
            // them apart, exactly as the Collectors.groupingBy it replaced did.
            stubEmptyExcept();
            when(complaintRepo.findAllPriorityValues()).thenReturn(List.of(
                    "MEDIUM", "MEDIUM", "MEDIUM", "MEDIUM", "medium", "medium",
                    "HIGH", "high", "CRITICAL"));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> byPriority = (Map<String, Long>) result.get("volumeByPriority");

            assertThat(byPriority).containsEntry("MEDIUM", 4L)
                    .containsEntry("medium", 2L)
                    .containsEntry("HIGH", 1L)
                    .containsEntry("high", 1L)
                    .containsEntry("CRITICAL", 1L);
            assertThat(byPriority).hasSize(5);
        }

        @Test
        void categoriesSharingADisplayNameAreSummedNotOverwritten() {
            // Grouping moved to category ID, but the panel reports NAMES. Unknown ids fold to
            // "Category-N"; two ids mapping to one name must add, or the panel under-reports.
            stubEmptyExcept();
            when(complaintRepo.countByCategoryForStatuses(anyList())).thenReturn(List.of(
                    new DashboardProjections.IdCount(1L, 5),
                    new DashboardProjections.IdCount(2L, 3)));

            com.hrms.cms.entity.ComplaintCategory c1 = new com.hrms.cms.entity.ComplaintCategory();
            c1.setId(1L);
            c1.setName("Cards");
            com.hrms.cms.entity.ComplaintCategory c2 = new com.hrms.cms.entity.ComplaintCategory();
            c2.setId(2L);
            c2.setName("Cards");
            when(categoryRepo.findAll()).thenReturn(List.of(c1, c2));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> byCategory = (Map<String, Long>) result.get("backlogByCategory");
            assertThat(byCategory).containsEntry("Cards", 8L);
        }
    }

    @Nested
    @DisplayName("Weekly Trend")
    class TrendTests {

        @Test
        void returns12Weeks() {
            when(complaintRepo.findTrendTimestampsSince(any())).thenReturn(List.of());
            List<Map<String, Object>> trend = service.getWeeklyTrend();
            assertThat(trend).hasSize(12);
            assertThat(trend.get(0).get("weekLabel")).isEqualTo("W-11");
            assertThat(trend.get(11).get("weekLabel")).isEqualTo("W-0");
            verify(complaintRepo, never()).findAll();
        }

        @Test
        void countsFiledInCorrectWeek() {
            when(complaintRepo.findTrendTimestampsSince(any())).thenReturn(List.of(
                    new DashboardProjections.TimestampPair(LocalDateTime.now().minusDays(3), null)));

            List<Map<String, Object>> trend = service.getWeeklyTrend();
            long totalFiled = trend.stream().mapToLong(w -> (long) w.get("filed")).sum();
            assertThat(totalFiled).isGreaterThanOrEqualTo(1);
        }

        @Test
        void bucketsAreHalfOpenSoNoRowIsCountedTwice() {
            // 12 contiguous windows over one row: it must land in exactly one bucket. An inclusive
            // upper bound would double-count rows landing on a boundary.
            when(complaintRepo.findTrendTimestampsSince(any())).thenReturn(List.of(
                    new DashboardProjections.TimestampPair(
                            LocalDateTime.now().minusWeeks(3), LocalDateTime.now().minusWeeks(1))));

            List<Map<String, Object>> trend = service.getWeeklyTrend();
            assertThat(trend.stream().mapToLong(w -> (long) w.get("filed")).sum()).isEqualTo(1);
            assertThat(trend.stream().mapToLong(w -> (long) w.get("resolved")).sum()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Entity Performance")
    class EntityTests {

        @Test
        void buildsDepartmentStatusMatrixFromFlatRows() {
            when(complaintRepo.countByEntity()).thenReturn(List.of(kc("SBI", 10)));
            when(complaintRepo.countByEntityForStatuses(anyList())).thenReturn(List.of(kc("SBI", 4)));
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of());
            when(complaintRepo.countByDepartmentAndStatus()).thenReturn(List.of(
                    new DashboardProjections.DeptStatusCount("RBIO", "pending", 3),
                    new DashboardProjections.DeptStatusCount("RBIO", "closed", 7),
                    new DashboardProjections.DeptStatusCount("CEPC", "pending", 2)));

            Map<String, Object> result = service.getEntityPerformance();
            @SuppressWarnings("unchecked")
            Map<String, Map<String, Long>> matrix =
                    (Map<String, Map<String, Long>>) result.get("statusByDepartment");

            assertThat(matrix.get("RBIO")).containsEntry("pending", 3L).containsEntry("closed", 7L);
            assertThat(matrix.get("CEPC")).containsEntry("pending", 2L).hasSize(1);
            verify(complaintRepo, never()).findAll();
        }

        @Test
        void breachTallyDropsNullEntityCodes() {
            when(complaintRepo.countByEntity()).thenReturn(List.of());
            when(complaintRepo.countByEntityForStatuses(anyList())).thenReturn(List.of());
            when(complaintRepo.countByDepartmentAndStatus()).thenReturn(List.of());
            when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of(
                    tat(null, LocalDateTime.now().minusDays(60)),
                    tat("SBI", LocalDateTime.now().minusDays(60))));

            Map<String, Object> result = service.getEntityPerformance();
            @SuppressWarnings("unchecked")
            Map<String, Long> breach = (Map<String, Long>) result.get("breachByEntity");
            assertThat(breach).doesNotContainKey(null);
            assertThat(breach.get("SBI")).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Adversarial Scenarios")
    class AdversarialTests {

        @Test
        void nullGroupKeysDoNotCrashOrBecomeMapKeys() {
            // Every aggregate query filters nulls out, but the assembled maps are serialised straight
            // to JSON, so a null key must not survive if a predicate is ever relaxed.
            lenient().when(complaintRepo.countGroupedByStatus()).thenReturn(List.of(kc(null, 4)));
            lenient().when(complaintRepo.countByDepartmentForStatuses(anyList()))
                    .thenReturn(List.of(kc(null, 2)));
            lenient().when(complaintRepo.countByCategoryForStatuses(anyList())).thenReturn(List.of());
            lenient().when(complaintRepo.findAllPriorityValues()).thenReturn(List.of());
            lenient().when(complaintRepo.countUnassignedByDepartment(anyList())).thenReturn(List.of());
            lenient().when(complaintRepo.findTrendTimestampsSince(any())).thenReturn(List.of());
            lenient().when(complaintRepo.findActiveTatRows(anyList(), any())).thenReturn(List.of());
            lenient().when(complaintRepo.countActiveForTat(anyList())).thenReturn(0L);

            assertThatCode(() -> service.getPipelineSummary()).doesNotThrowAnyException();
            assertThatCode(() -> service.getBottlenecks()).doesNotThrowAnyException();
            assertThatCode(() -> service.getTatAnalytics()).doesNotThrowAnyException();
            assertThatCode(() -> service.getWeeklyTrend()).doesNotThrowAnyException();

            @SuppressWarnings("unchecked")
            Map<String, Long> byDept =
                    (Map<String, Long>) service.getBottlenecks().get("backlogByDepartment");
            assertThat(byDept).doesNotContainKey(null);

            // A null-keyed group still contributes to 'total' — it is a real row.
            assertThat(service.getPipelineSummary().get("total")).isEqualTo(4L);
        }

        @Test
        void allSameStatus_noArithmeticError() {
            when(complaintRepo.countGroupedByStatus()).thenReturn(List.of(kc("pending", 100)));
            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("resolutionRate")).isEqualTo(0L);
            assertThat(result.get("activeBacklog")).isEqualTo(100L);
        }

        @Test
        void categoryLoadFailureDoesNotFailThePanel() {
            lenient().when(complaintRepo.countByDepartmentForStatuses(anyList())).thenReturn(List.of());
            lenient().when(complaintRepo.findAllPriorityValues()).thenReturn(List.of());
            lenient().when(complaintRepo.countUnassignedByDepartment(anyList())).thenReturn(List.of());
            when(complaintRepo.countByCategoryForStatuses(anyList()))
                    .thenReturn(List.of(new DashboardProjections.IdCount(7L, 3)));
            when(categoryRepo.findAll()).thenThrow(new RuntimeException("category table unavailable"));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> byCategory = (Map<String, Long>) result.get("backlogByCategory");
            assertThat(byCategory).containsEntry("Category-7", 3L);
        }
    }
}
