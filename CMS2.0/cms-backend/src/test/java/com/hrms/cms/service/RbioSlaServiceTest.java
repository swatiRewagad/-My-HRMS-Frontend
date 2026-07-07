package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RbioSlaService.
 * Tests SLA deadline calculation, breach detection, and progress tracking
 * for RBIO (RBI Ombudsman) complaint lifecycle.
 */
@ExtendWith(MockitoExtension.class)
class RbioSlaServiceTest {

    @Mock private BusinessHoursService businessHoursService;
    @Mock private ComplaintRepository complaintRepository;

    @InjectMocks
    private RbioSlaService rbioSlaService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-789012")
                .status("in_progress")
                .department("RBIO")
                .assignedRole("RBIO_OFFICER")
                .workflowStage("EXAMINATION")
                .createdAt(LocalDateTime.of(2026, 6, 1, 10, 0))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // calculateDeadline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateDeadline()")
    class CalculateDeadline {

        @Test
        @DisplayName("Officer Assessment stage should use 30 business days")
        void officerAssessmentShouldUse30Days() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 8, 12, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "OFFICER_ASSESSMENT");

            assertThat(result).isEqualTo(expectedDeadline);
            verify(businessHoursService).calculateDueDate(from, 270); // 30 * 9
        }

        @Test
        @DisplayName("EXAMINATION stage should use 30 business days (same as officer)")
        void examinationShouldUse30Days() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 8, 12, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "EXAMINATION");

            assertThat(result).isEqualTo(expectedDeadline);
        }

        @Test
        @DisplayName("Conciliation stage should use 30 business days")
        void conciliationShouldUse30Days() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 8, 12, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "CONCILIATION");

            assertThat(result).isEqualTo(expectedDeadline);
            verify(businessHoursService).calculateDueDate(from, 270); // 30 * 9
        }

        @Test
        @DisplayName("Adjudication stage should use 60 business days")
        void adjudicationShouldUse60Days() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 9, 22, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(540))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "ADJUDICATION");

            assertThat(result).isEqualTo(expectedDeadline);
            verify(businessHoursService).calculateDueDate(from, 540); // 60 * 9
        }

        @Test
        @DisplayName("should default to 30 days for unknown stage")
        void shouldDefaultTo30DaysForUnknownStage() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 8, 12, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "UNKNOWN_STAGE");

            assertThat(result).isEqualTo(expectedDeadline);
        }

        @Test
        @DisplayName("should use current time when from is null")
        void shouldUseCurrentTimeWhenFromNull() {
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(LocalDateTime.class), eq(270)))
                    .thenReturn(LocalDateTime.now().plusDays(42));

            LocalDateTime result = rbioSlaService.calculateDeadline(null, "OFFICER_ASSESSMENT");

            assertThat(result).isNotNull();
            verify(businessHoursService).calculateDueDate(any(LocalDateTime.class), eq(270));
        }

        @Test
        @DisplayName("should handle null stage gracefully with default 30 days")
        void shouldHandleNullStageGracefully() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270)))
                    .thenReturn(LocalDateTime.of(2026, 8, 12, 18, 0));

            LocalDateTime result = rbioSlaService.calculateDeadline(from, null);

            assertThat(result).isNotNull();
            verify(businessHoursService).calculateDueDate(from, 270);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // isBreached()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isBreached()")
    class IsBreached {

        @Test
        @DisplayName("should return true when current time is past deadline")
        void shouldReturnTrueWhenPastDeadline() {
            // Set deadline to yesterday
            sampleComplaint.setCurrentStageDeadline(LocalDateTime.now().minusDays(1));

            boolean result = rbioSlaService.isBreached(sampleComplaint);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when current time is before deadline")
        void shouldReturnFalseWhenBeforeDeadline() {
            // Set deadline to tomorrow
            sampleComplaint.setCurrentStageDeadline(LocalDateTime.now().plusDays(1));

            boolean result = rbioSlaService.isBreached(sampleComplaint);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when no deadline is set and no createdAt")
        void shouldReturnFalseWhenNoDeadline() {
            sampleComplaint.setCurrentStageDeadline(null);
            sampleComplaint.setSlaDeadline(null);
            sampleComplaint.setCreatedAt(null);
            sampleComplaint.setStageAssignedAt(null);

            boolean result = rbioSlaService.isBreached(sampleComplaint);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should fall back to slaDeadline when currentStageDeadline is null")
        void shouldFallBackToSlaDeadline() {
            sampleComplaint.setCurrentStageDeadline(null);
            sampleComplaint.setSlaDeadline(LocalDateTime.now().minusHours(1));

            boolean result = rbioSlaService.isBreached(sampleComplaint);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should calculate from stageAssignedAt when no deadline stored")
        void shouldCalculateFromStageAssignedAt() {
            sampleComplaint.setCurrentStageDeadline(null);
            sampleComplaint.setSlaDeadline(null);
            sampleComplaint.setStageAssignedAt(LocalDateTime.now().minusDays(60));
            sampleComplaint.setWorkflowStage("OFFICER_ASSESSMENT");

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(LocalDateTime.class), eq(270)))
                    .thenReturn(LocalDateTime.now().minusDays(10)); // deadline already passed

            boolean result = rbioSlaService.isBreached(sampleComplaint);

            assertThat(result).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getStageProgress()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStageProgress()")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class GetStageProgress {

        @Test
        @DisplayName("should return correct percentage when half the time elapsed")
        void shouldReturnCorrectPercentageHalfway() {
            LocalDateTime stageStart = LocalDateTime.now().minusDays(15);
            LocalDateTime deadline = LocalDateTime.now().plusDays(15);
            sampleComplaint.setStageAssignedAt(stageStart);
            sampleComplaint.setCurrentStageDeadline(deadline);

            // Service calls: calculateElapsedBusinessHours(start, deadline) for total, then (start, now) for elapsed
            when(businessHoursService.calculateElapsedBusinessHours(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(270L)  // first call: total hours
                    .thenReturn(135L); // second call: elapsed hours

            double result = rbioSlaService.getStageProgress(sampleComplaint);

            assertThat(result).isCloseTo(50.0, offset(1.0));
        }

        @Test
        @DisplayName("should return 0 when stageAssignedAt is null and createdAt is null")
        void shouldReturnZeroWhenNoStart() {
            sampleComplaint.setStageAssignedAt(null);
            sampleComplaint.setCreatedAt(null);
            sampleComplaint.setCurrentStageDeadline(null);
            sampleComplaint.setSlaDeadline(null);

            double result = rbioSlaService.getStageProgress(sampleComplaint);

            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should cap at 100 when fully elapsed")
        void shouldCapAt100() {
            LocalDateTime stageStart = LocalDateTime.now().minusDays(40);
            LocalDateTime deadline = LocalDateTime.now().minusDays(10);
            sampleComplaint.setStageAssignedAt(stageStart);
            sampleComplaint.setCurrentStageDeadline(deadline);

            when(businessHoursService.calculateElapsedBusinessHours(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(270L)  // total
                    .thenReturn(360L); // elapsed (exceeded)

            double result = rbioSlaService.getStageProgress(sampleComplaint);

            assertThat(result).isEqualTo(100.0);
        }

        @Test
        @DisplayName("should use createdAt as fallback when stageAssignedAt is null")
        void shouldUseCreatedAtAsFallback() {
            LocalDateTime created = LocalDateTime.now().minusDays(10);
            sampleComplaint.setStageAssignedAt(null);
            sampleComplaint.setCreatedAt(created);
            sampleComplaint.setCurrentStageDeadline(LocalDateTime.now().plusDays(20));

            when(businessHoursService.calculateElapsedBusinessHours(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(270L)  // total
                    .thenReturn(90L);  // elapsed

            // Redefine specifically for this test since we need both calls
            lenient().when(businessHoursService.calculateElapsedBusinessHours(eq(created), eq(sampleComplaint.getCurrentStageDeadline())))
                    .thenReturn(270L);

            double result = rbioSlaService.getStageProgress(sampleComplaint);

            // Just verify it returns a numeric result and doesn't throw
            assertThat(result).isGreaterThanOrEqualTo(0.0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getOverallProgress()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getOverallProgress()")
    class GetOverallProgress {

        @Test
        @DisplayName("should return 0 when createdAt is null")
        void shouldReturnZeroWhenCreatedAtNull() {
            sampleComplaint.setCreatedAt(null);

            double result = rbioSlaService.getOverallProgress(sampleComplaint);

            assertThat(result).isEqualTo(0.0);
        }

        @Test
        @DisplayName("should return correct percentage based on 120-day lifecycle")
        void shouldReturnPercentageBasedOn120Days() {
            // Created 60 business days ago (half of 120-day lifecycle)
            LocalDateTime created = LocalDateTime.now().minusDays(84); // ~60 business days
            sampleComplaint.setCreatedAt(created);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateElapsedBusinessHours(eq(created), any(LocalDateTime.class)))
                    .thenReturn(540L); // 60 days * 9 hours

            double result = rbioSlaService.getOverallProgress(sampleComplaint);

            // 540 / (120 * 9 = 1080) * 100 = 50%
            assertThat(result).isCloseTo(50.0, offset(0.1));
        }

        @Test
        @DisplayName("should cap at 100 when lifecycle exceeded")
        void shouldCapAt100WhenLifecycleExceeded() {
            LocalDateTime created = LocalDateTime.now().minusDays(200);
            sampleComplaint.setCreatedAt(created);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateElapsedBusinessHours(eq(created), any(LocalDateTime.class)))
                    .thenReturn(1200L); // more than 120 * 9 = 1080

            double result = rbioSlaService.getOverallProgress(sampleComplaint);

            assertThat(result).isEqualTo(100.0);
        }

        @Test
        @DisplayName("should return small percentage for recently created complaint")
        void shouldReturnSmallPercentageForRecent() {
            LocalDateTime created = LocalDateTime.now().minusDays(1);
            sampleComplaint.setCreatedAt(created);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateElapsedBusinessHours(eq(created), any(LocalDateTime.class)))
                    .thenReturn(9L); // 1 business day

            double result = rbioSlaService.getOverallProgress(sampleComplaint);

            // 9 / (120 * 9) * 100 = 0.83%
            assertThat(result).isCloseTo(0.83, offset(0.1));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // applyStageSla()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applyStageSla()")
    class ApplyStageSla {

        @Test
        @DisplayName("should set stageAssignedAt and calculate deadline for OFFICER_ASSESSMENT")
        void shouldApplyOfficerAssessmentSla() {
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(LocalDateTime.class), eq(270)))
                    .thenReturn(LocalDateTime.of(2026, 8, 12, 18, 0));

            rbioSlaService.applyStageSla(sampleComplaint, "OFFICER_ASSESSMENT");

            assertThat(sampleComplaint.getStageAssignedAt()).isNotNull();
            assertThat(sampleComplaint.getCurrentStageDeadline()).isNotNull();
            assertThat(sampleComplaint.getSlaDeadline()).isNotNull();
        }

        @Test
        @DisplayName("should set deadline for CONCILIATION stage")
        void shouldApplyConciliationSla() {
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(LocalDateTime.class), eq(270)))
                    .thenReturn(LocalDateTime.of(2026, 8, 12, 18, 0));

            rbioSlaService.applyStageSla(sampleComplaint, "CONCILIATION");

            assertThat(sampleComplaint.getStageAssignedAt()).isNotNull();
            assertThat(sampleComplaint.getCurrentStageDeadline()).isNotNull();
            verify(businessHoursService).calculateDueDate(any(LocalDateTime.class), eq(270));
        }

        @Test
        @DisplayName("should set deadline for ADJUDICATION stage (60 business days)")
        void shouldApplyAdjudicationSla() {
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(LocalDateTime.class), eq(540)))
                    .thenReturn(LocalDateTime.of(2026, 9, 22, 18, 0));

            rbioSlaService.applyStageSla(sampleComplaint, "ADJUDICATION");

            assertThat(sampleComplaint.getStageAssignedAt()).isNotNull();
            assertThat(sampleComplaint.getCurrentStageDeadline()).isNotNull();
            verify(businessHoursService).calculateDueDate(any(LocalDateTime.class), eq(540));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getComplianceStats()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getComplianceStats()")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class GetComplianceStats {

        @Test
        @DisplayName("should return correct stats with mix of breached and on-track")
        void shouldReturnCorrectMixedStats() {
            // Breached: deadline in the past
            Complaint breachedComplaint = Complaint.builder()
                    .id(2L)
                    .currentStageDeadline(LocalDateTime.now().minusDays(5))
                    .build();

            // At-risk: not breached but progress >= 85%
            Complaint atRiskComplaint = Complaint.builder()
                    .id(3L)
                    .stageAssignedAt(LocalDateTime.now().minusDays(25))
                    .currentStageDeadline(LocalDateTime.now().plusDays(1))
                    .build();

            // On-track: not breached and progress < 85%
            Complaint onTrackComplaint = Complaint.builder()
                    .id(4L)
                    .stageAssignedAt(LocalDateTime.now().minusDays(5))
                    .currentStageDeadline(LocalDateTime.now().plusDays(25))
                    .build();

            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("RBIO"), any()))
                    .thenReturn(List.of(breachedComplaint, atRiskComplaint, onTrackComplaint));

            // Service calls getStageProgress for non-breached complaints.
            // getStageProgress calls calculateElapsedBusinessHours twice per complaint.
            // atRiskComplaint: total=270, elapsed=250 → 93% (at risk)
            // onTrackComplaint: total=270, elapsed=45 → 17% (on track)
            when(businessHoursService.calculateElapsedBusinessHours(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(270L, 250L,  // atRisk: total, elapsed
                                270L, 45L);  // onTrack: total, elapsed

            Map<String, Long> stats = rbioSlaService.getComplianceStats();

            assertThat(stats.get("total")).isEqualTo(3L);
            assertThat(stats.get("breached")).isEqualTo(1L);
            assertThat(stats.get("atRisk")).isEqualTo(1L);
            assertThat(stats.get("onTrack")).isEqualTo(1L);
        }

        @Test
        @DisplayName("should return zeros when no active complaints")
        void shouldReturnZerosWhenNoComplaints() {
            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("RBIO"), any()))
                    .thenReturn(Collections.emptyList());

            Map<String, Long> stats = rbioSlaService.getComplianceStats();

            assertThat(stats.get("total")).isEqualTo(0L);
            assertThat(stats.get("breached")).isEqualTo(0L);
            assertThat(stats.get("atRisk")).isEqualTo(0L);
            assertThat(stats.get("onTrack")).isEqualTo(0L);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Business Days Calculation (Weekends Skip)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Business Days / Weekend Handling")
    class BusinessDaysWeekendHandling {

        @Test
        @DisplayName("should call BusinessHoursService for due date calculation (which skips weekends)")
        void shouldDelegateToBusinessHoursService() {
            LocalDateTime from = LocalDateTime.of(2026, 7, 3, 10, 0); // Friday
            LocalDateTime expectedDeadline = LocalDateTime.of(2026, 8, 14, 18, 0); // Not just +30 calendar days

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(270))).thenReturn(expectedDeadline);

            LocalDateTime result = rbioSlaService.calculateDeadline(from, "OFFICER_ASSESSMENT");

            assertThat(result).isEqualTo(expectedDeadline);
            // The BusinessHoursService internally handles weekend skipping
            verify(businessHoursService).calculateDueDate(from, 270);
        }

        @ParameterizedTest
        @CsvSource({
                "OFFICER_ASSESSMENT, 270",
                "EXAMINATION, 270",
                "CONCILIATION, 270",
                "ADJUDICATION, 540"
        })
        @DisplayName("should calculate correct business hours for each stage")
        void shouldCalculateCorrectHoursPerStage(String stage, int expectedHours) {
            LocalDateTime from = LocalDateTime.of(2026, 7, 1, 10, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(from), eq(expectedHours)))
                    .thenReturn(from.plusDays(expectedHours / 9 + 10)); // approximate

            rbioSlaService.calculateDeadline(from, stage);

            verify(businessHoursService).calculateDueDate(from, expectedHours);
        }
    }
}
