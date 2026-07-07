package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.AppealTimelineRepository;
import com.hrms.cms.repository.ComplaintRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AppealWorkflowService.
 */
@ExtendWith(MockitoExtension.class)
class AppealWorkflowServiceTest {

    @Mock private AppealRepository appealRepository;
    @Mock private AppealTimelineRepository appealTimelineRepository;
    @Mock private AppealEligibilityService eligibilityService;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private KeycloakUserService keycloakUserService;

    @InjectMocks
    private AppealWorkflowService appealWorkflowService;

    private Complaint closedComplaint;

    @BeforeEach
    void setUp() {
        closedComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260601-100001")
                .complainantName("Test Appellant")
                .complainantEmail("appellant@example.com")
                .subject("Credit Card Overcharge")
                .description("Bank charged excessive fees")
                .status("closed")
                .priority("HIGH")
                .department("RBIO")
                .entityCode("HDFC001")
                .closedAt(LocalDateTime.now().minusDays(10))
                .createdAt(LocalDateTime.now().minusDays(60))
                .updatedAt(LocalDateTime.now().minusDays(10))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // fileAppeal()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("fileAppeal()")
    class FileAppeal {

        @Test
        @DisplayName("should create appeal with correct fields and return result map")
        void shouldCreateAppealWithCorrectFields() {
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(keycloakUserService.getUsersByRole("AA_REGISTRAR"))
                    .thenReturn(List.of(Map.of("userId", "registrar-1")));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test Appellant");
            request.put("appellantEmail", "appellant@example.com");
            request.put("appealGround", "Dissatisfied with closure");
            request.put("reliefSought", "Full refund");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result).containsKey("appealNumber");
            assertThat(result.get("classificationType")).isEqualTo("APPEAL");
            assertThat(result.get("status")).isEqualTo("filed");
            assertThat(result.get("assignedRole")).isEqualTo("AA_REGISTRAR");
            verify(appealRepository).save(any(Appeal.class));
            verify(appealTimelineRepository).save(any(AppealTimeline.class));
        }

        @Test
        @DisplayName("should generate appeal number with correct prefix")
        void shouldGenerateAppealNumberWithPrefix() {
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(keycloakUserService.getUsersByRole("AA_REGISTRAR")).thenReturn(Collections.emptyList());
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat((String) result.get("appealNumber")).startsWith("APL-");
        }

        @Test
        @DisplayName("should assign to AA_REGISTRAR role by default")
        void shouldAssignToRegistrarByDefault() {
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(keycloakUserService.getUsersByRole("AA_REGISTRAR"))
                    .thenReturn(List.of(Map.of("userId", "reg-user-1")));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("assignedRole")).isEqualTo("AA_REGISTRAR");
            assertThat(result.get("assignedOfficer")).isEqualTo("reg-user-1");
        }

        @Test
        @DisplayName("should throw when originalComplaintNumber is missing")
        void shouldThrowWhenComplaintNumberMissing() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("originalComplaintNumber is required");
        }

        @Test
        @DisplayName("should throw when classificationType is missing")
        void shouldThrowWhenClassificationTypeMissing() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classificationType is required");
        }

        @Test
        @DisplayName("should throw when classificationType is invalid")
        void shouldThrowWhenClassificationTypeInvalid() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "INVALID");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("classificationType must be APPEAL or REPRESENTATION");
        }

        @Test
        @DisplayName("should throw when appellantName is missing")
        void shouldThrowWhenAppellantNameMissing() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appellantName is required");
        }

        @Test
        @DisplayName("should throw when appealGround is missing")
        void shouldThrowWhenAppealGroundMissing() {
            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("appealGround is required");
        }

        @Test
        @DisplayName("should throw when eligibility check fails")
        void shouldThrowWhenNotEligible() {
            Map<String, Object> eligibility = Map.of("eligible", false, "reason", "Complaint is still active");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            assertThatThrownBy(() -> appealWorkflowService.fileAppeal(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Appeal not eligible");
        }

        @Test
        @DisplayName("should set REPRESENTATION type correctly")
        void shouldSetRepresentationType() {
            Map<String, Object> eligibility = Map.of("eligible", true, "reason", "OK");
            when(eligibilityService.checkEligibility("CMP-20260601-100001")).thenReturn(eligibility);
            when(keycloakUserService.getUsersByRole("AA_REGISTRAR")).thenReturn(Collections.emptyList());
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "REPRESENTATION");
            request.put("appellantName", "Test");
            request.put("appealGround", "Advisory grounds");

            Map<String, Object> result = appealWorkflowService.fileAppeal(request);

            assertThat(result.get("classificationType")).isEqualTo("REPRESENTATION");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // performAction() — State Transitions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performAction() - State Transitions")
    class PerformAction {

        @Test
        @DisplayName("ACCEPT should transition to under_review")
        void acceptShouldTransitionToUnderReview() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "OK");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "ACCEPT", params);

            assertThat(result.get("action")).isEqualTo("ACCEPT");
            assertThat(result.get("newStatus")).isEqualTo("under_review");
            assertThat(result.get("workflowStage")).isEqualTo("UNDER_REVIEW");
        }

        @Test
        @DisplayName("REJECT should set status to rejected")
        void rejectShouldSetRejected() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "Time-barred");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REJECT", params);

            assertThat(result.get("newStatus")).isEqualTo("rejected");
            assertThat(result.get("workflowStage")).isEqualTo("REJECTED");
        }

        @Test
        @DisplayName("REJECT should throw when remarks are blank")
        void rejectShouldThrowWhenRemarksBlank() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "REJECT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Rejection reason");
        }

        @Test
        @DisplayName("ASSIGN_TO_BENCH should assign to bench officer role")
        void assignToBenchShouldAssign() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakUserService.getUsersByRole("AA_BENCH_OFFICER"))
                    .thenReturn(List.of(Map.of("userId", "bench-1")));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "Assigning");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "ASSIGN_TO_BENCH", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_BENCH_OFFICER");
            assertThat(result.get("workflowStage")).isEqualTo("ASSIGNED_TO_BENCH");
        }

        @Test
        @DisplayName("SCHEDULE_HEARING should set hearing date and status")
        void scheduleHearingShouldSetDateAndStatus() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "bench-1");
            params.put("actorRole", "AA_BENCH_OFFICER");
            params.put("hearingDate", "2026-07-20T10:00:00");
            params.put("hearingVenue", "Room A");
            params.put("remarks", "Scheduling");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "SCHEDULE_HEARING", params);

            assertThat(result.get("newStatus")).isEqualTo("hearing_scheduled");
            assertThat(result.get("workflowStage")).isEqualTo("HEARING_SCHEDULED");
        }

        @Test
        @DisplayName("SCHEDULE_HEARING should throw when hearingDate missing")
        void scheduleHearingShouldThrowWhenNoDate() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "bench-1");
            params.put("actorRole", "AA_BENCH_OFFICER");
            params.put("remarks", "Scheduling");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "SCHEDULE_HEARING", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("hearingDate is required");
        }

        @Test
        @DisplayName("FORWARD_TO_AUTHORITY should change assigned role")
        void forwardToAuthorityShouldChangeRole() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakUserService.getUsersByRole("AA_AUTHORITY"))
                    .thenReturn(List.of(Map.of("userId", "authority-1")));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "bench-1");
            params.put("actorRole", "AA_BENCH_OFFICER");
            params.put("remarks", "Forwarding");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "FORWARD_TO_AUTHORITY", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_AUTHORITY");
            assertThat(result.get("workflowStage")).isEqualTo("FORWARDED_TO_AUTHORITY");
        }

        @Test
        @DisplayName("PASS_ORDER should set order_passed status with outcome")
        void passOrderShouldSetStatus() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", "AA_AUTHORITY");
            params.put("orderOutcome", "UPHELD");
            params.put("orderSummary", "Original order upheld");
            params.put("remarks", "Order upheld");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "PASS_ORDER", params);

            assertThat(result.get("newStatus")).isEqualTo("order_passed");
            assertThat(result.get("workflowStage")).isEqualTo("ORDER_PASSED");
        }

        @Test
        @DisplayName("PASS_ORDER should throw when orderOutcome is missing")
        void passOrderShouldThrowWhenNoOutcome() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", "AA_AUTHORITY");
            params.put("remarks", "Order");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "PASS_ORDER", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("orderOutcome is required");
        }

        @Test
        @DisplayName("PASS_ORDER with MODIFIED outcome and awardModifiedAmount")
        void passOrderModifiedShouldSetAmount() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", "AA_AUTHORITY");
            params.put("orderOutcome", "MODIFIED");
            params.put("awardModifiedAmount", "100000");
            params.put("remarks", "Modified amount");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "PASS_ORDER", params);

            assertThat(result.get("newStatus")).isEqualTo("order_passed");
            verify(appealRepository).save(argThat(a -> a.getAwardModifiedAmount() != null));
        }

        @Test
        @DisplayName("REMAND_TO_OMBUDSMAN should close appeal and reopen complaint")
        void remandToOmbudsmanShouldReopenComplaint() {
            Appeal appeal = createAppeal("hearing_scheduled");
            appeal.setOriginalComplaintNumber("CMP-20260601-100001");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(complaintRepository.findByComplaintNumber("CMP-20260601-100001"))
                    .thenReturn(Optional.of(closedComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", "AA_AUTHORITY");
            params.put("remarks", "Remanding");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REMAND_TO_OMBUDSMAN", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("REMANDED");
            verify(complaintRepository).save(argThat(c -> "in_progress".equals(c.getStatus())));
        }

        @Test
        @DisplayName("DISMISS should close appeal")
        void dismissShouldCloseAppeal() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", "AA_AUTHORITY");
            params.put("remarks", "Not maintainable");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "DISMISS", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("DISMISSED");
        }

        @Test
        @DisplayName("CLOSE by admin should close with closureCause")
        void closeShouldCloseWithCause() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "admin-1");
            params.put("actorRole", "AA_ADMIN");
            params.put("closureCause", "ADMIN_CLOSED");
            params.put("remarks", "Admin closure");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "CLOSE", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(result.get("workflowStage")).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("REOPEN should set status back to under_review")
        void reopenShouldSetUnderReview() {
            Appeal appeal = createAppeal("closed");
            appeal.setClosedAt(LocalDateTime.now());
            appeal.setClosureCause("ADMIN_CLOSED");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "admin-1");
            params.put("actorRole", "AA_ADMIN");
            params.put("remarks", "Reopening");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "REOPEN", params);

            assertThat(result.get("newStatus")).isEqualTo("under_review");
            assertThat(result.get("workflowStage")).isEqualTo("REOPENED");
        }

        @Test
        @DisplayName("should throw for unknown action")
        void shouldThrowForUnknownAction() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "");
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "INVALID", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown action");
        }

        @Test
        @DisplayName("should throw when appeal not found")
        void shouldThrowWhenAppealNotFound() {
            when(appealRepository.findByAppealNumber("APL-NONEXIST")).thenReturn(Optional.empty());

            Map<String, String> params = Map.of("actor", "registrar-1", "actorRole", "", "remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-NONEXIST", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("should throw when action not permitted for role")
        void shouldThrowWhenActionNotPermittedForRole() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "PASS_ORDER", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not permitted for role");
        }

        @Test
        @DisplayName("should reject classificationType change attempt (immutability)")
        void shouldRejectClassificationTypeChange() {
            Appeal appeal = createAppeal("filed");
            appeal.setClassificationType("APPEAL");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", "AA_REGISTRAR");
            params.put("remarks", "Change");
            params.put("classificationType", "REPRESENTATION");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("immutable");
        }

        @Test
        @DisplayName("SEND_BACK_REGISTRAR should reassign to registrar")
        void sendBackRegistrarShouldReassign() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            when(keycloakUserService.getUsersByRole("AA_REGISTRAR"))
                    .thenReturn(List.of(Map.of("userId", "registrar-1")));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "bench-1");
            params.put("actorRole", "AA_BENCH_OFFICER");
            params.put("remarks", "Need more info");

            Map<String, Object> result = appealWorkflowService.performAction("APL-001", "SEND_BACK_REGISTRAR", params);

            assertThat(result.get("assignedRole")).isEqualTo("AA_REGISTRAR");
            assertThat(result.get("workflowStage")).isEqualTo("SENT_BACK_TO_REGISTRAR");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Role-Action Authorization (via performAction)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Role-Action Authorization")
    class RoleActionAuthorization {

        @ParameterizedTest
        @CsvSource({
                "AA_REGISTRAR, ACCEPT",
                "AA_REGISTRAR, REJECT",
                "AA_REGISTRAR, ASSIGN_TO_BENCH",
                "AA_REGISTRAR, REQUEST_DOCUMENTS"
        })
        @DisplayName("AA_REGISTRAR should be allowed these actions")
        void aaRegistrarAllowedActions(String role, String action) {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(keycloakUserService.getUsersByRole(anyString())).thenReturn(Collections.emptyList());

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", role);
            params.put("remarks", "test");
            // Need hearingDate for SCHEDULE_HEARING; not needed for REGISTRAR actions

            // Should NOT throw about "not permitted"
            assertThatCode(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest
        @CsvSource({
                "AA_REGISTRAR, PASS_ORDER",
                "AA_REGISTRAR, DISMISS",
                "AA_REGISTRAR, SCHEDULE_HEARING"
        })
        @DisplayName("AA_REGISTRAR should be denied these actions")
        void aaRegistrarDeniedActions(String role, String action) {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "registrar-1");
            params.put("actorRole", role);
            params.put("remarks", "test");

            assertThatThrownBy(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not permitted for role");
        }

        @ParameterizedTest
        @CsvSource({
                "AA_AUTHORITY, PASS_ORDER",
                "AA_AUTHORITY, REMAND_TO_OMBUDSMAN",
                "AA_AUTHORITY, DISMISS",
                "AA_AUTHORITY, SCHEDULE_HEARING"
        })
        @DisplayName("AA_AUTHORITY should be allowed these actions")
        void aaAuthorityAllowedActions(String role, String action) {
            Appeal appeal = createAppeal("hearing_scheduled");
            appeal.setOriginalComplaintNumber("CMP-20260601-100001");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));
            when(appealRepository.save(any(Appeal.class))).thenAnswer(inv -> inv.getArgument(0));
            lenient().when(complaintRepository.findByComplaintNumber(anyString()))
                    .thenReturn(Optional.of(closedComplaint));
            lenient().when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> params = new HashMap<>();
            params.put("actor", "authority-1");
            params.put("actorRole", role);
            params.put("remarks", "test");
            params.put("orderOutcome", "UPHELD");
            params.put("hearingDate", "2026-07-20T10:00:00");

            assertThatCode(() -> appealWorkflowService.performAction("APL-001", action, params))
                    .doesNotThrowAnyException();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getAvailableActions()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAvailableActions()")
    class GetAvailableActions {

        @Test
        @DisplayName("should return REGISTRAR actions for AA_REGISTRAR on active appeal")
        void shouldReturnRegistrarActions() {
            Appeal appeal = createAppeal("filed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001", "AA_REGISTRAR");

            assertThat(actions).contains("ACCEPT", "REJECT", "ASSIGN_TO_BENCH", "REQUEST_DOCUMENTS");
            assertThat(actions).doesNotContain("PASS_ORDER", "DISMISS");
        }

        @Test
        @DisplayName("should return BENCH_OFFICER actions for AA_BENCH_OFFICER")
        void shouldReturnBenchOfficerActions() {
            Appeal appeal = createAppeal("under_review");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001", "AA_BENCH_OFFICER");

            assertThat(actions).contains("SCHEDULE_HEARING", "PREPARE_BRIEF", "FORWARD_TO_AUTHORITY", "SEND_BACK_REGISTRAR");
        }

        @Test
        @DisplayName("should return AUTHORITY actions for AA_AUTHORITY")
        void shouldReturnAuthorityActions() {
            Appeal appeal = createAppeal("hearing_scheduled");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001", "AA_AUTHORITY");

            assertThat(actions).contains("PASS_ORDER", "SCHEDULE_HEARING", "REMAND_TO_OMBUDSMAN", "DISMISS");
        }

        @Test
        @DisplayName("should return empty list for closed appeal (non-admin)")
        void shouldReturnEmptyForClosedNonAdmin() {
            Appeal appeal = createAppeal("closed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001", "AA_BENCH_OFFICER");

            assertThat(actions).isEmpty();
        }

        @Test
        @DisplayName("should return ADMIN actions for AA_ADMIN even on closed appeal")
        void shouldReturnAdminActionsOnClosed() {
            Appeal appeal = createAppeal("closed");
            when(appealRepository.findByAppealNumber("APL-001")).thenReturn(Optional.of(appeal));

            List<String> actions = appealWorkflowService.getAvailableActions("APL-001", "AA_ADMIN");

            assertThat(actions).contains("REASSIGN", "CLOSE", "REOPEN");
        }

        @Test
        @DisplayName("should return empty list when appeal not found")
        void shouldReturnEmptyWhenNotFound() {
            when(appealRepository.findByAppealNumber("APL-NONEXIST")).thenReturn(Optional.empty());

            List<String> actions = appealWorkflowService.getAvailableActions("APL-NONEXIST", "AA_REGISTRAR");

            assertThat(actions).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getStats()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getStats()")
    class GetStats {

        @Test
        @DisplayName("should return correct statistics counts")
        void shouldReturnCorrectStats() {
            when(appealRepository.count()).thenReturn(50L);
            when(appealRepository.findByStatus("filed")).thenReturn(List.of(createAppeal("filed")));
            when(appealRepository.findByStatus("under_review")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("hearing_scheduled")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("order_passed")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("closed")).thenReturn(Collections.emptyList());
            when(appealRepository.findByStatus("rejected")).thenReturn(Collections.emptyList());

            Map<String, Object> stats = appealWorkflowService.getStats();

            assertThat(stats.get("total")).isEqualTo(50L);
            assertThat(stats.get("filed")).isEqualTo(1);
            assertThat(stats).containsKeys("total", "filed", "underReview", "hearingScheduled", "orderPassed", "closed", "rejected");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getTimeline()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getTimeline()")
    class GetTimeline {

        @Test
        @DisplayName("should return timeline entries for appeal")
        void shouldReturnTimelineEntries() {
            AppealTimeline entry = AppealTimeline.builder()
                    .appealNumber("APL-001")
                    .action("FILED")
                    .performedBy("SYSTEM")
                    .toStatus("filed")
                    .performedAt(LocalDateTime.now())
                    .build();
            when(appealTimelineRepository.findByAppealNumberOrderByPerformedAtDesc("APL-001"))
                    .thenReturn(List.of(entry));

            List<AppealTimeline> timeline = appealWorkflowService.getTimeline("APL-001");

            assertThat(timeline).hasSize(1);
            assertThat(timeline.get(0).getAction()).isEqualTo("FILED");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper
    // ═══════════════════════════════════════════════════════════════════

    private Appeal createAppeal(String status) {
        return Appeal.builder()
                .id(1L)
                .appealNumber("APL-001")
                .originalComplaintNumber("CMP-20260601-100001")
                .classificationType("APPEAL")
                .appealGround("Test grounds")
                .reliefSought("Test relief")
                .appellantName("Test Appellant")
                .appellantEmail("test@example.com")
                .status(status)
                .priority("high")
                .assignedRole("AA_REGISTRAR")
                .assignedOfficer("registrar-1")
                .workflowStage("FILED")
                .filedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
