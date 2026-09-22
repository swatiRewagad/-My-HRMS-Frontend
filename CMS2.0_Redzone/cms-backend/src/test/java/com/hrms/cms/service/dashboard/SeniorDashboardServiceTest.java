package com.hrms.cms.service.dashboard;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.HolidayRepository;
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
import static org.mockito.Mockito.*;

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

    private Complaint makeComplaint(String status, String dept, String priority, Long categoryId, LocalDateTime created) {
        Complaint c = new Complaint();
        c.setStatus(status);
        c.setDepartment(dept);
        c.setPriority(priority);
        c.setCategoryId(categoryId);
        c.setCreatedAt(created);
        return c;
    }

    @Nested
    @DisplayName("Pipeline Summary")
    class PipelineTests {

        @Test
        void emptyRepo_returnsZeroes() {
            when(complaintRepo.findAll()).thenReturn(List.of());
            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("total")).isEqualTo(0L);
            assertThat(result.get("activeBacklog")).isEqualTo(0L);
            assertThat(result.get("resolutionRate")).isEqualTo(0L);
        }

        @Test
        void correctCounts() {
            List<Complaint> data = List.of(
                    makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(5)),
                    makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(3)),
                    makeComplaint("in_progress", "CEPC", "MEDIUM", 2L, LocalDateTime.now().minusDays(10)),
                    makeComplaint("resolved", "RBIO", "LOW", 1L, LocalDateTime.now().minusDays(20)),
                    makeComplaint("closed", "CEPC", "MEDIUM", 2L, LocalDateTime.now().minusDays(30)),
                    makeComplaint("escalated", "RBIO", "HIGH", 3L, LocalDateTime.now().minusDays(2))
            );
            when(complaintRepo.findAll()).thenReturn(data);

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
    }

    @Nested
    @DisplayName("TAT Analytics")
    class TatTests {

        @Test
        void emptyRepo_returnsZeroes() {
            when(complaintRepo.findAll()).thenReturn(List.of());
            Map<String, Object> result = service.getTatAnalytics();
            assertThat(result.get("totalActive")).isEqualTo(0);
            assertThat(result.get("breachRate")).isEqualTo(0L);
        }

        @Test
        void detectsBreachedComplaints() {
            Complaint breached = makeComplaint("in_progress", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(60));
            Complaint onTrack = makeComplaint("pending", "CEPC", "LOW", 2L, LocalDateTime.now().minusDays(2));
            when(complaintRepo.findAll()).thenReturn(List.of(breached, onTrack));

            Map<String, Object> result = service.getTatAnalytics();
            assertThat((long) result.get("breached")).isGreaterThanOrEqualTo(1);
            assertThat((int) result.get("totalActive")).isEqualTo(2);
        }

        @Test
        void excludesClosedAndWithdrawn() {
            Complaint closed = makeComplaint("closed", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(60));
            Complaint withdrawn = makeComplaint("withdrawn", "CEPC", "LOW", 2L, LocalDateTime.now().minusDays(60));
            Complaint active = makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(5));
            when(complaintRepo.findAll()).thenReturn(List.of(closed, withdrawn, active));

            Map<String, Object> result = service.getTatAnalytics();
            assertThat((int) result.get("totalActive")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Bottlenecks")
    class BottleneckTests {

        @Test
        void groupsByDepartment() {
            List<Complaint> data = List.of(
                    makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now()),
                    makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now()),
                    makeComplaint("in_progress", "CEPC", "MEDIUM", 2L, LocalDateTime.now()),
                    makeComplaint("resolved", "RBIO", "LOW", 1L, LocalDateTime.now())
            );
            when(complaintRepo.findAll()).thenReturn(data);

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> byDept = (Map<String, Long>) result.get("backlogByDepartment");
            assertThat(byDept.get("RBIO")).isEqualTo(2L);
            assertThat(byDept.get("CEPC")).isEqualTo(1L);
        }

        @Test
        void unassignedDetection() {
            Complaint unassigned = makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now());
            unassigned.setAssignedOfficer(null);

            Complaint assigned = makeComplaint("pending", "CEPC", "MEDIUM", 2L, LocalDateTime.now());
            assigned.setAssignedOfficer("officer1");

            when(complaintRepo.findAll()).thenReturn(List.of(unassigned, assigned));

            Map<String, Object> result = service.getBottlenecks();
            @SuppressWarnings("unchecked")
            Map<String, Long> unassignedMap = (Map<String, Long>) result.get("unassignedByDepartment");
            assertThat(unassignedMap.get("RBIO")).isEqualTo(1L);
            assertThat(unassignedMap.containsKey("CEPC")).isFalse();
        }
    }

    @Nested
    @DisplayName("Weekly Trend")
    class TrendTests {

        @Test
        void returns12Weeks() {
            when(complaintRepo.findAll()).thenReturn(List.of());
            List<Map<String, Object>> trend = service.getWeeklyTrend();
            assertThat(trend).hasSize(12);
            assertThat(trend.get(0).get("weekLabel")).isEqualTo("W-11");
            assertThat(trend.get(11).get("weekLabel")).isEqualTo("W-0");
        }

        @Test
        void countsFiledInCorrectWeek() {
            Complaint recent = makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(3));
            when(complaintRepo.findAll()).thenReturn(List.of(recent));

            List<Map<String, Object>> trend = service.getWeeklyTrend();
            long totalFiled = trend.stream().mapToLong(w -> (long) w.get("filed")).sum();
            assertThat(totalFiled).isGreaterThanOrEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Adversarial Scenarios")
    class AdversarialTests {

        @Test
        void nullFieldsDoNotCrash() {
            Complaint nullFields = new Complaint();
            nullFields.setStatus("pending");
            nullFields.setCreatedAt(LocalDateTime.now());
            // department, priority, categoryId, assignedOfficer all null

            when(complaintRepo.findAll()).thenReturn(List.of(nullFields));
            assertThatCode(() -> service.getPipelineSummary()).doesNotThrowAnyException();
            assertThatCode(() -> service.getBottlenecks()).doesNotThrowAnyException();
            assertThatCode(() -> service.getTatAnalytics()).doesNotThrowAnyException();
            assertThatCode(() -> service.getWeeklyTrend()).doesNotThrowAnyException();
        }

        @Test
        void largeDataset_completesWithinReasonableTime() {
            List<Complaint> large = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                large.add(makeComplaint(
                        i % 3 == 0 ? "pending" : i % 3 == 1 ? "in_progress" : "resolved",
                        i % 2 == 0 ? "RBIO" : "CEPC",
                        "HIGH",
                        (long)(i % 5 + 1),
                        LocalDateTime.now().minusDays(i % 90)
                ));
            }
            when(complaintRepo.findAll()).thenReturn(large);

            long start = System.currentTimeMillis();
            service.getPipelineSummary();
            service.getBottlenecks();
            service.getWeeklyTrend();
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(5000);
        }

        @Test
        void allSameStatus_noArithmeticError() {
            List<Complaint> all = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                all.add(makeComplaint("pending", "RBIO", "HIGH", 1L, LocalDateTime.now().minusDays(1)));
            }
            when(complaintRepo.findAll()).thenReturn(all);

            Map<String, Object> result = service.getPipelineSummary();
            assertThat(result.get("resolutionRate")).isEqualTo(0L);
            assertThat(result.get("activeBacklog")).isEqualTo(100L);
        }
    }
}
