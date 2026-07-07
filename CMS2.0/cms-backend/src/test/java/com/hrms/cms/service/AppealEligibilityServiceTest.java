package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppealEligibilityService.
 * Validates appeal eligibility rules including 30-day window,
 * complaint status checks, and classification type determination.
 */
@ExtendWith(MockitoExtension.class)
class AppealEligibilityServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private AppealRepository appealRepository;

    @InjectMocks
    private AppealEligibilityService appealEligibilityService;

    private Complaint closedComplaintWithin30Days;
    private Complaint closedComplaintBeyond30Days;
    private Complaint openComplaint;
    private Complaint advisoryClosedComplaint;
    private Complaint nonAdvisoryClosedComplaint;

    @BeforeEach
    void setUp() {
        closedComplaintWithin30Days = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260625-001")
                .complainantName("Eligible Citizen")
                .subject("Refund not processed")
                .status("closed")
                .department("RBIO")
                .closedAt(LocalDateTime.now().minusDays(10))
                .createdAt(LocalDateTime.now().minusDays(50))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build();

        closedComplaintBeyond30Days = Complaint.builder()
                .id(2L)
                .complaintNumber("CMP-20260501-002")
                .complainantName("Late Citizen")
                .subject("Account closure issue")
                .status("closed")
                .department("RBIO")
                .closedAt(LocalDateTime.now().minusDays(45))
                .createdAt(LocalDateTime.now().minusDays(100))
                .updatedAt(LocalDateTime.now().minusDays(45))
                .build();

        openComplaint = Complaint.builder()
                .id(3L)
                .complaintNumber("CMP-20260703-003")
                .complainantName("Active Citizen")
                .subject("Ongoing complaint")
                .status("in_progress")
                .department("RBIO")
                .createdAt(LocalDateTime.now().minusDays(3))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        advisoryClosedComplaint = Complaint.builder()
                .id(4L)
                .complaintNumber("CMP-20260620-004")
                .complainantName("Advisory Citizen")
                .subject("Advisory matter")
                .status("closed")
                .department("RBIO")
                .advisoryText("This is advisory text content")
                .closedAt(LocalDateTime.now().minusDays(5))
                .createdAt(LocalDateTime.now().minusDays(30))
                .updatedAt(LocalDateTime.now().minusDays(5))
                .build();

        nonAdvisoryClosedComplaint = Complaint.builder()
                .id(5L)
                .complaintNumber("CMP-20260615-005")
                .complainantName("Normal Citizen")
                .subject("Normal resolution")
                .status("closed")
                .department("RBIO")
                .closedAt(LocalDateTime.now().minusDays(8))
                .createdAt(LocalDateTime.now().minusDays(45))
                .updatedAt(LocalDateTime.now().minusDays(8))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // checkEligibility() — Eligible Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkEligibility() - Eligible Cases")
    class EligibleCases {

        @Test
        @DisplayName("should be eligible when complaint closed within 30 days and no existing appeal")
        void shouldBeEligibleWhenClosedWithin30DaysNoExistingAppeal() {
            when(complaintRepository.findByComplaintNumber("CMP-20260625-001"))
                    .thenReturn(Optional.of(closedComplaintWithin30Days));
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260625-001"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260625-001");

            assertThat(result.get("eligible")).isEqualTo(true);
            assertThat(result.get("reason").toString()).contains("eligible");
            assertThat(result.get("suggestedType")).isEqualTo("APPEAL");
            assertThat(result.get("originalStatus")).isEqualTo("closed");
            assertThat(result).containsKey("closureDate");
        }

        @Test
        @DisplayName("should be eligible at exactly 30 days boundary")
        void shouldBeEligibleAtExactly30Days() {
            Complaint boundaryComplaint = Complaint.builder()
                    .id(10L)
                    .complaintNumber("CMP-BOUNDARY")
                    .complainantName("Boundary")
                    .subject("Test")
                    .status("closed")
                    .closedAt(LocalDateTime.now().minusDays(30))
                    .updatedAt(LocalDateTime.now().minusDays(30))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-BOUNDARY"))
                    .thenReturn(Optional.of(boundaryComplaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-BOUNDARY"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-BOUNDARY");

            assertThat(result.get("eligible")).isEqualTo(true);
        }

        @Test
        @DisplayName("should suggest REPRESENTATION when advisoryText is present")
        void shouldSuggestRepresentationWhenAdvisoryTextPresent() {
            when(complaintRepository.findByComplaintNumber("CMP-20260620-004"))
                    .thenReturn(Optional.of(advisoryClosedComplaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260620-004"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260620-004");

            assertThat(result.get("eligible")).isEqualTo(true);
            assertThat(result.get("suggestedType")).isEqualTo("REPRESENTATION");
        }

        @Test
        @DisplayName("should suggest APPEAL when advisoryText is null or blank")
        void shouldSuggestAppealWhenNoAdvisoryText() {
            when(complaintRepository.findByComplaintNumber("CMP-20260615-005"))
                    .thenReturn(Optional.of(nonAdvisoryClosedComplaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260615-005"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260615-005");

            assertThat(result.get("eligible")).isEqualTo(true);
            assertThat(result.get("suggestedType")).isEqualTo("APPEAL");
        }

        @ParameterizedTest
        @ValueSource(strings = {"closed", "resolved", "rejected", "adjudicated", "conciliated", "withdrawn"})
        @DisplayName("should be eligible for all terminal statuses within 30 days")
        void shouldBeEligibleForAllTerminalStatuses(String terminalStatus) {
            Complaint complaint = Complaint.builder()
                    .id(20L)
                    .complaintNumber("CMP-TERMINAL")
                    .complainantName("Citizen")
                    .subject("Test")
                    .status(terminalStatus)
                    .closedAt(LocalDateTime.now().minusDays(5))
                    .updatedAt(LocalDateTime.now().minusDays(5))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-TERMINAL"))
                    .thenReturn(Optional.of(complaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-TERMINAL"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-TERMINAL");

            assertThat(result.get("eligible")).isEqualTo(true);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // checkEligibility() — Ineligible Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("checkEligibility() - Ineligible Cases")
    class IneligibleCases {

        @Test
        @DisplayName("should return ineligible when complaint not found")
        void shouldReturnIneligibleWhenNotFound() {
            when(complaintRepository.findByComplaintNumber("CMP-NONEXIST"))
                    .thenReturn(Optional.empty());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-NONEXIST");

            assertThat(result.get("eligible")).isEqualTo(false);
            assertThat(result.get("reason").toString()).contains("not found");
        }

        @Test
        @DisplayName("should be ineligible when complaint is not in terminal status")
        void shouldBeIneligibleWhenComplaintNotTerminal() {
            when(complaintRepository.findByComplaintNumber("CMP-20260703-003"))
                    .thenReturn(Optional.of(openComplaint));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260703-003");

            assertThat(result.get("eligible")).isEqualTo(false);
            assertThat(result.get("reason").toString()).contains("still active");
        }

        @Test
        @DisplayName("should be ineligible when outside 30-day window")
        void shouldBeIneligibleWhenOutside30DayWindow() {
            when(complaintRepository.findByComplaintNumber("CMP-20260501-002"))
                    .thenReturn(Optional.of(closedComplaintBeyond30Days));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260501-002");

            assertThat(result.get("eligible")).isEqualTo(false);
            assertThat(result.get("reason").toString()).contains("deadline exceeded");
        }

        @Test
        @DisplayName("should be ineligible when active appeal already exists")
        void shouldBeIneligibleWhenActiveAppealExists() {
            when(complaintRepository.findByComplaintNumber("CMP-20260625-001"))
                    .thenReturn(Optional.of(closedComplaintWithin30Days));

            Appeal activeAppeal = Appeal.builder()
                    .appealNumber("APL-EXISTING")
                    .originalComplaintNumber("CMP-20260625-001")
                    .status("filed")
                    .build();
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260625-001"))
                    .thenReturn(List.of(activeAppeal));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260625-001");

            assertThat(result.get("eligible")).isEqualTo(false);
            assertThat(result.get("reason").toString()).contains("active appeal already exists");
        }

        @Test
        @DisplayName("should be eligible when existing appeal is in non-active status")
        void shouldBeEligibleWhenExistingAppealIsClosed() {
            when(complaintRepository.findByComplaintNumber("CMP-20260625-001"))
                    .thenReturn(Optional.of(closedComplaintWithin30Days));

            Appeal closedAppeal = Appeal.builder()
                    .appealNumber("APL-CLOSED")
                    .originalComplaintNumber("CMP-20260625-001")
                    .status("closed")
                    .build();
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260625-001"))
                    .thenReturn(List.of(closedAppeal));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260625-001");

            assertThat(result.get("eligible")).isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(strings = {"filed", "under_review", "hearing_scheduled"})
        @DisplayName("should be ineligible when appeal exists in active status")
        void shouldBeIneligibleForActiveAppealStatuses(String activeStatus) {
            when(complaintRepository.findByComplaintNumber("CMP-20260625-001"))
                    .thenReturn(Optional.of(closedComplaintWithin30Days));

            Appeal activeAppeal = Appeal.builder()
                    .appealNumber("APL-ACTIVE")
                    .originalComplaintNumber("CMP-20260625-001")
                    .status(activeStatus)
                    .build();
            when(appealRepository.findByOriginalComplaintNumber("CMP-20260625-001"))
                    .thenReturn(List.of(activeAppeal));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-20260625-001");

            assertThat(result.get("eligible")).isEqualTo(false);
        }

        @ParameterizedTest
        @ValueSource(strings = {"in_progress", "assigned", "pending", "escalated"})
        @DisplayName("should be ineligible for non-terminal complaint statuses")
        void shouldBeIneligibleForNonTerminalStatuses(String nonTerminalStatus) {
            Complaint complaint = Complaint.builder()
                    .id(30L)
                    .complaintNumber("CMP-ACTIVE")
                    .complainantName("Citizen")
                    .subject("Test")
                    .status(nonTerminalStatus)
                    .createdAt(LocalDateTime.now().minusDays(5))
                    .updatedAt(LocalDateTime.now().minusDays(1))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-ACTIVE"))
                    .thenReturn(Optional.of(complaint));

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-ACTIVE");

            assertThat(result.get("eligible")).isEqualTo(false);
            assertThat(result.get("reason").toString()).contains("still active");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Closure date fallback logic
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Closure date fallback")
    class ClosureDateFallback {

        @Test
        @DisplayName("should use resolvedAt when closedAt is null")
        void shouldUseResolvedAtFallback() {
            Complaint complaint = Complaint.builder()
                    .id(40L)
                    .complaintNumber("CMP-RESOLVED")
                    .complainantName("Citizen")
                    .subject("Test")
                    .status("resolved")
                    .closedAt(null)
                    .resolvedAt(LocalDateTime.now().minusDays(5))
                    .updatedAt(LocalDateTime.now().minusDays(3))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-RESOLVED"))
                    .thenReturn(Optional.of(complaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-RESOLVED"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-RESOLVED");

            assertThat(result.get("eligible")).isEqualTo(true);
        }

        @Test
        @DisplayName("should use updatedAt when both closedAt and resolvedAt are null")
        void shouldUseUpdatedAtFallback() {
            Complaint complaint = Complaint.builder()
                    .id(41L)
                    .complaintNumber("CMP-UPDATED")
                    .complainantName("Citizen")
                    .subject("Test")
                    .status("withdrawn")
                    .closedAt(null)
                    .resolvedAt(null)
                    .updatedAt(LocalDateTime.now().minusDays(10))
                    .build();

            when(complaintRepository.findByComplaintNumber("CMP-UPDATED"))
                    .thenReturn(Optional.of(complaint));
            when(appealRepository.findByOriginalComplaintNumber("CMP-UPDATED"))
                    .thenReturn(Collections.emptyList());

            Map<String, Object> result = appealEligibilityService.checkEligibility("CMP-UPDATED");

            assertThat(result.get("eligible")).isEqualTo(true);
        }
    }
}
