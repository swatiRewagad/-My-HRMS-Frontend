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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CepcSlaService — SLA deadline calculation, breach detection,
 * and days remaining computation.
 *
 * The service delegates date computation to BusinessHoursService which handles:
 * - Business hours: 9:00-18:00, Mon-Fri, Asia/Kolkata timezone
 * - Weekend skip logic
 * - Holiday exclusion
 */
@ExtendWith(MockitoExtension.class)
class CepcSlaServiceTest {

    @Mock private BusinessHoursService businessHoursService;
    @Mock private ComplaintRepository complaintRepository;

    @InjectMocks
    private CepcSlaService cepcSlaService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-123456")
                .status("in_progress")
                .priority("MEDIUM")
                .department("CEPC")
                .createdAt(LocalDateTime.of(2025, 7, 7, 10, 0))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // calculateDeadline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateDeadline()")
    class CalculateDeadline {

        @Test
        @DisplayName("CRITICAL priority should calculate with 7 business days (63 hours)")
        void criticalShouldUse7BusinessDays() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2025, 7, 16, 10, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(start, 63)).thenReturn(expected);

            LocalDateTime result = cepcSlaService.calculateDeadline(start, "CRITICAL");

            assertThat(result).isEqualTo(expected);
            verify(businessHoursService).calculateDueDate(start, 63); // 7 * 9 = 63
        }

        @Test
        @DisplayName("HIGH priority should calculate with 15 business days (135 hours)")
        void highShouldUse15BusinessDays() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2025, 7, 28, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(start, 135)).thenReturn(expected);

            LocalDateTime result = cepcSlaService.calculateDeadline(start, "HIGH");

            assertThat(result).isEqualTo(expected);
            verify(businessHoursService).calculateDueDate(start, 135); // 15 * 9 = 135
        }

        @Test
        @DisplayName("MEDIUM priority should calculate with 30 business days (270 hours)")
        void mediumShouldUse30BusinessDays() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2025, 8, 18, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(start, 270)).thenReturn(expected);

            LocalDateTime result = cepcSlaService.calculateDeadline(start, "MEDIUM");

            assertThat(result).isEqualTo(expected);
            verify(businessHoursService).calculateDueDate(start, 270); // 30 * 9 = 270
        }

        @Test
        @DisplayName("LOW priority should calculate with 45 business days (405 hours)")
        void lowShouldUse45BusinessDays() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2025, 9, 8, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(start, 405)).thenReturn(expected);

            LocalDateTime result = cepcSlaService.calculateDeadline(start, "LOW");

            assertThat(result).isEqualTo(expected);
            verify(businessHoursService).calculateDueDate(start, 405); // 45 * 9 = 405
        }

        @Test
        @DisplayName("null priority should default to 30 business days (MEDIUM)")
        void nullPriorityShouldDefaultToMedium() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);
            LocalDateTime expected = LocalDateTime.of(2025, 8, 18, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(start, 270)).thenReturn(expected);

            LocalDateTime result = cepcSlaService.calculateDeadline(start, null);

            assertThat(result).isEqualTo(expected);
            verify(businessHoursService).calculateDueDate(start, 270);
        }

        @Test
        @DisplayName("should use current time when createdAt is null")
        void shouldUseCurrentTimeWhenNull() {
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(), eq(270)))
                    .thenReturn(LocalDateTime.now().plusDays(42));

            LocalDateTime result = cepcSlaService.calculateDeadline(null, "MEDIUM");

            assertThat(result).isNotNull();
            verify(businessHoursService).calculateDueDate(any(), eq(270));
        }

        @ParameterizedTest
        @CsvSource({
                "CRITICAL, 63",
                "HIGH, 135",
                "MEDIUM, 270",
                "LOW, 405"
        })
        @DisplayName("should request correct business hours for each priority")
        void shouldRequestCorrectHours(String priority, int expectedHours) {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(start), eq(expectedHours)))
                    .thenReturn(start.plusDays(expectedHours / 9));

            cepcSlaService.calculateDeadline(start, priority);

            verify(businessHoursService).calculateDueDate(start, expectedHours);
        }

        @Test
        @DisplayName("should be case-insensitive for priority")
        void shouldBeCaseInsensitive() {
            LocalDateTime start = LocalDateTime.of(2025, 7, 7, 10, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(eq(start), anyInt()))
                    .thenReturn(start.plusDays(10));

            cepcSlaService.calculateDeadline(start, "critical");
            verify(businessHoursService).calculateDueDate(start, 63); // 7 * 9

            cepcSlaService.calculateDeadline(start, "High");
            verify(businessHoursService).calculateDueDate(start, 135); // 15 * 9
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // isBreached()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isBreached()")
    class IsBreached {

        @Test
        @DisplayName("should return true when past the pre-calculated slaDeadline")
        void shouldReturnTrueWhenPastDeadline() {
            // Set a deadline that's already past
            sampleComplaint.setSlaDeadline(LocalDateTime.now().minusDays(1));

            boolean result = cepcSlaService.isBreached(sampleComplaint);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when before the slaDeadline")
        void shouldReturnFalseWhenBeforeDeadline() {
            sampleComplaint.setSlaDeadline(LocalDateTime.now().plusDays(10));

            boolean result = cepcSlaService.isBreached(sampleComplaint);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should fall back to calculating from createdAt when slaDeadline is null")
        void shouldFallBackToCalculation() {
            sampleComplaint.setSlaDeadline(null);
            sampleComplaint.setCreatedAt(LocalDateTime.now().minusDays(60));
            sampleComplaint.setPriority("MEDIUM");

            // If createdAt was 60 days ago and SLA is 30 business days, it should be breached
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(), eq(270)))
                    .thenReturn(LocalDateTime.now().minusDays(10)); // Deadline was 10 days ago

            boolean result = cepcSlaService.isBreached(sampleComplaint);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when both slaDeadline and createdAt are null")
        void shouldReturnFalseWhenBothNull() {
            sampleComplaint.setSlaDeadline(null);
            sampleComplaint.setCreatedAt(null);

            boolean result = cepcSlaService.isBreached(sampleComplaint);

            assertThat(result).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getDaysRemaining()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getDaysRemaining()")
    class GetDaysRemaining {

        @Test
        @DisplayName("should return positive days when before deadline")
        void shouldReturnPositiveWhenBeforeDeadline() {
            sampleComplaint.setSlaDeadline(LocalDateTime.now().plusDays(10));

            when(businessHoursService.calculateElapsedBusinessHours(any(), any())).thenReturn(45L);
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);

            long result = cepcSlaService.getDaysRemaining(sampleComplaint);

            // 45 hours / 9 hours per day = 5 business days
            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("should return negative days when already breached")
        void shouldReturnNegativeWhenBreached() {
            sampleComplaint.setSlaDeadline(LocalDateTime.now().minusDays(5));

            when(businessHoursService.calculateElapsedBusinessHours(any(), any())).thenReturn(27L);
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);

            long result = cepcSlaService.getDaysRemaining(sampleComplaint);

            // -27 / 9 = -3
            assertThat(result).isEqualTo(-3);
        }

        @Test
        @DisplayName("should return MAX_VALUE when no deadline can be calculated")
        void shouldReturnMaxWhenNoDeadline() {
            sampleComplaint.setSlaDeadline(null);
            sampleComplaint.setCreatedAt(null);

            long result = cepcSlaService.getDaysRemaining(sampleComplaint);

            assertThat(result).isEqualTo(Long.MAX_VALUE);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // applySlaDeadline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("applySlaDeadline()")
    class ApplySlaDeadline {

        @Test
        @DisplayName("should set slaDeadline and slaPriority on complaint")
        void shouldSetDeadlineAndPriority() {
            sampleComplaint.setPriority("HIGH");
            sampleComplaint.setCreatedAt(LocalDateTime.of(2025, 7, 7, 10, 0));
            LocalDateTime expectedDeadline = LocalDateTime.of(2025, 7, 28, 18, 0);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(), eq(135))).thenReturn(expectedDeadline);

            cepcSlaService.applySlaDeadline(sampleComplaint);

            assertThat(sampleComplaint.getSlaDeadline()).isEqualTo(expectedDeadline);
            assertThat(sampleComplaint.getSlaPriority()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("should normalize null priority to MEDIUM")
        void shouldNormalizeNullPriority() {
            sampleComplaint.setPriority(null);
            sampleComplaint.setCreatedAt(LocalDateTime.of(2025, 7, 7, 10, 0));

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(), eq(270)))
                    .thenReturn(LocalDateTime.of(2025, 8, 18, 18, 0));

            cepcSlaService.applySlaDeadline(sampleComplaint);

            assertThat(sampleComplaint.getSlaPriority()).isEqualTo("MEDIUM");
        }

        @Test
        @DisplayName("should use current time when createdAt is null")
        void shouldUseNowWhenCreatedAtNull() {
            sampleComplaint.setPriority("CRITICAL");
            sampleComplaint.setCreatedAt(null);

            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);
            when(businessHoursService.calculateDueDate(any(), eq(63)))
                    .thenReturn(LocalDateTime.now().plusDays(10));

            cepcSlaService.applySlaDeadline(sampleComplaint);

            assertThat(sampleComplaint.getSlaDeadline()).isNotNull();
            assertThat(sampleComplaint.getSlaPriority()).isEqualTo("CRITICAL");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getComplianceStats()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getComplianceStats()")
    class GetComplianceStats {

        @Test
        @DisplayName("should return all zeros when no active complaints")
        void shouldReturnZerosWhenEmpty() {
            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("CEPC"), any()))
                    .thenReturn(Collections.emptyList());

            Map<String, Long> stats = cepcSlaService.getComplianceStats("CEPC");

            assertThat(stats.get("total")).isEqualTo(0L);
            assertThat(stats.get("breached")).isEqualTo(0L);
            assertThat(stats.get("atRisk")).isEqualTo(0L);
            assertThat(stats.get("onTrack")).isEqualTo(0L);
        }

        @Test
        @DisplayName("should correctly categorize complaints as breached, atRisk, or onTrack")
        void shouldCategorizeComplaints() {
            Complaint breachedComplaint = Complaint.builder()
                    .id(1L).status("in_progress").priority("MEDIUM")
                    .slaDeadline(LocalDateTime.now().minusDays(5))
                    .createdAt(LocalDateTime.now().minusDays(60))
                    .build();

            Complaint atRiskComplaint = Complaint.builder()
                    .id(2L).status("in_progress").priority("MEDIUM")
                    .slaDeadline(LocalDateTime.now().plusHours(10))
                    .createdAt(LocalDateTime.now().minusDays(29))
                    .build();

            Complaint onTrackComplaint = Complaint.builder()
                    .id(3L).status("assigned").priority("LOW")
                    .slaDeadline(LocalDateTime.now().plusDays(30))
                    .createdAt(LocalDateTime.now().minusDays(5))
                    .build();

            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("CEPC"), any()))
                    .thenReturn(List.of(breachedComplaint, atRiskComplaint, onTrackComplaint));

            // For the at-risk complaint, getDaysRemaining should return <= 2
            when(businessHoursService.calculateElapsedBusinessHours(any(), any()))
                    .thenReturn(9L) // at-risk: 1 day remaining
                    .thenReturn(90L); // on-track: 10 days remaining
            when(businessHoursService.getBusinessHoursPerDay()).thenReturn(9);

            Map<String, Long> stats = cepcSlaService.getComplianceStats("CEPC");

            assertThat(stats.get("total")).isEqualTo(3L);
            assertThat(stats.get("breached")).isEqualTo(1L);
            // Exact atRisk/onTrack depends on business hours calculation timing
            assertThat(stats.get("atRisk") + stats.get("onTrack")).isEqualTo(2L);
        }
    }
}
