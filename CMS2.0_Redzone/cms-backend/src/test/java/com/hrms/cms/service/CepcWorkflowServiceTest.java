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
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CepcWorkflowServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintService complaintService;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private CepcSlaService cepcSlaService;
    @Mock private CepcAuditService cepcAuditService;

    @InjectMocks
    private CepcWorkflowService cepcWorkflowService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-123456")
                .complainantName("Test Complainant")
                .complainantEmail("test@example.com")
                .subject("CEPC Test Complaint")
                .status("assigned")
                .priority("MEDIUM")
                .department("CEPC")
                .assignedRole("CEPC_DO")
                .assignedOfficer("do-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // isCepcAction()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isCepcAction()")
    class IsCepcAction {

        @ParameterizedTest
        @ValueSource(strings = {
                "ACCEPT", "REQUEST_INFO", "INFO_RECEIVED", "FORWARD_DEPT",
                "COMMENTS_RECEIVED", "SCHEDULE_MEETING", "SUBMIT_FOR_REVIEW",
                "APPROVE_REVIEW", "SEND_BACK_DO", "SEND_BACK_REVIEWER",
                "SEND_BACK_INCHARGE", "APPROVE_CLOSURE", "CLOSE_COMPLAINT",
                "REASSIGN", "ESCALATE", "FORWARD_TO_CONTACT", "CONTACT_RESPONSE",
                "CONTACT_REASSIGN", "FORWARD_TO_INCHARGE", "FORWARD_TO_CLOSING_AUTHORITY",
                "FORWARD_TO_OTHER_OFFICE", "FORWARD_TO_REGULATORY_BODY", "REOPEN"
        })
        @DisplayName("should recognize valid CEPC actions")
        void shouldRecognizeValidActions(String action) {
            assertThat(cepcWorkflowService.isCepcAction(action)).isTrue();
        }

        @Test
        @DisplayName("should recognize case-insensitive actions")
        void shouldRecognizeCaseInsensitive() {
            assertThat(cepcWorkflowService.isCepcAction("accept")).isTrue();
            assertThat(cepcWorkflowService.isCepcAction("Submit_For_Review")).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(cepcWorkflowService.isCepcAction(null)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown actions")
        void shouldReturnFalseForUnknown() {
            assertThat(cepcWorkflowService.isCepcAction("INVALID_ACTION")).isFalse();
            assertThat(cepcWorkflowService.isCepcAction("")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // validateRoleAuthorization()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateRoleAuthorization()")
    class ValidateRoleAuthorization {

        @ParameterizedTest
        @CsvSource({
                "CEPC_DO, ACCEPT, true",
                "CEPC_DO, REQUEST_INFO, true",
                "CEPC_DO, INFO_RECEIVED, true",
                "CEPC_DO, FORWARD_DEPT, true",
                "CEPC_DO, COMMENTS_RECEIVED, true",
                "CEPC_DO, SCHEDULE_MEETING, true",
                "CEPC_DO, SUBMIT_FOR_REVIEW, true",
                "CEPC_DO, FORWARD_TO_CONTACT, true",
                "CEPC_DO, FORWARD_TO_INCHARGE, true",
                "CEPC_DO, CLOSE_COMPLAINT, false",
                "CEPC_DO, APPROVE_REVIEW, false",
                "CEPC_DO, APPROVE_CLOSURE, false"
        })
        @DisplayName("CEPC_DO role authorization")
        void cepcDoAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "CEPC_REVIEWER, APPROVE_REVIEW, true",
                "CEPC_REVIEWER, SEND_BACK_DO, true",
                "CEPC_REVIEWER, FORWARD_TO_CLOSING_AUTHORITY, true",
                "CEPC_REVIEWER, CLOSE_COMPLAINT, false",
                "CEPC_REVIEWER, ACCEPT, false",
                "CEPC_REVIEWER, APPROVE_CLOSURE, false"
        })
        @DisplayName("CEPC_REVIEWER role authorization")
        void cepcReviewerAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "CEPC_INCHARGE, APPROVE_CLOSURE, true",
                "CEPC_INCHARGE, SEND_BACK_REVIEWER, true",
                "CEPC_INCHARGE, SEND_BACK_DO, true",
                "CEPC_INCHARGE, REASSIGN, true",
                "CEPC_INCHARGE, CLOSE_COMPLAINT, false",
                "CEPC_INCHARGE, ACCEPT, false"
        })
        @DisplayName("CEPC_INCHARGE role authorization")
        void cepcInchargeAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "CEPC_CLOSING_AUTHORITY, CLOSE_COMPLAINT, true",
                "CEPC_CLOSING_AUTHORITY, SEND_BACK_INCHARGE, true",
                "CEPC_CLOSING_AUTHORITY, REOPEN, true",
                "CEPC_CLOSING_AUTHORITY, FORWARD_TO_OTHER_OFFICE, true",
                "CEPC_CLOSING_AUTHORITY, FORWARD_TO_REGULATORY_BODY, true",
                "CEPC_CLOSING_AUTHORITY, ACCEPT, false",
                "CEPC_CLOSING_AUTHORITY, APPROVE_REVIEW, false"
        })
        @DisplayName("CEPC_CLOSING_AUTHORITY role authorization")
        void cepcClosingAuthorityAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "CEPC_CONTACT_PERSON, CONTACT_RESPONSE, true",
                "CEPC_CONTACT_PERSON, CONTACT_REASSIGN, true",
                "CEPC_CONTACT_PERSON, CLOSE_COMPLAINT, false",
                "CEPC_CONTACT_PERSON, ACCEPT, false"
        })
        @DisplayName("CEPC_CONTACT_PERSON role authorization")
        void cepcContactPersonAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "CEPC_ADMIN, REASSIGN, true",
                "CEPC_ADMIN, ESCALATE, true",
                "CEPC_ADMIN, CLOSE_COMPLAINT, true",
                "CEPC_ADMIN, REOPEN, true"
        })
        @DisplayName("CEPC_ADMIN role authorization")
        void cepcAdminAuthorization(String role, String action, boolean expected) {
            assertThat(cepcWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return false for null role")
        void shouldReturnFalseForNullRole() {
            assertThat(cepcWorkflowService.validateRoleAuthorization(null, "ACCEPT")).isFalse();
        }

        @Test
        @DisplayName("should return false for null action")
        void shouldReturnFalseForNullAction() {
            assertThat(cepcWorkflowService.validateRoleAuthorization("CEPC_DO", null)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown role")
        void shouldReturnFalseForUnknownRole() {
            assertThat(cepcWorkflowService.validateRoleAuthorization("UNKNOWN_ROLE", "ACCEPT")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // performAction() - State Transitions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performAction() - State Transitions")
    class PerformAction {

        @BeforeEach
        void setUpMocks() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("ACCEPT should set status to in_progress and stage to EXAMINATION")
        void acceptShouldTransitionToInProgress() {
            Map<String, String> params = Map.of("action", "ACCEPT", "actor", "do-officer-1", "remarks", "Accepted");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "ACCEPT", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getStatus()).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("EXAMINATION");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("do-officer-1");
            verify(cepcSlaService).applySlaDeadline(sampleComplaint);
        }

        @Test
        @DisplayName("REQUEST_INFO should set status to info_requested")
        void requestInfoShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "REQUEST_INFO", "actor", "do-1", "remarks", "Need info");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "REQUEST_INFO", params);

            assertThat(result.get("newStatus")).isEqualTo("info_requested");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("AWAITING_INFO");
        }

        @Test
        @DisplayName("INFO_RECEIVED should set status back to in_progress")
        void infoReceivedShouldTransition() {
            sampleComplaint.setStatus("info_requested");
            Map<String, String> params = Map.of("action", "INFO_RECEIVED", "actor", "do-1", "remarks", "Got it");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "INFO_RECEIVED", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("EXAMINATION");
        }

        @Test
        @DisplayName("FORWARD_DEPT should set status to forwarded")
        void forwardDeptShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "FORWARD_DEPT", "actor", "do-1", "remarks", "Forwarding");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "FORWARD_DEPT", params);

            assertThat(result.get("newStatus")).isEqualTo("forwarded");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("DEPT_CONSULTATION");
        }

        @Test
        @DisplayName("COMMENTS_RECEIVED should return to in_progress")
        void commentsReceivedShouldTransition() {
            sampleComplaint.setStatus("forwarded");
            Map<String, String> params = Map.of("action", "COMMENTS_RECEIVED", "actor", "do-1", "remarks", "Got comments");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "COMMENTS_RECEIVED", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
        }

        @Test
        @DisplayName("SCHEDULE_MEETING should only update stage")
        void scheduleMeetingShouldOnlyUpdateStage() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "SCHEDULE_MEETING", "actor", "do-1", "remarks", "Meeting");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "SCHEDULE_MEETING", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("MEETING_SCHEDULED");
        }

        @Test
        @DisplayName("SUBMIT_FOR_REVIEW should assign to CEPC_REVIEWER via round-robin")
        void submitForReviewShouldAssignReviewer() {
            sampleComplaint.setStatus("in_progress");
            when(keycloakUserService.getUsersByRole("CEPC_REVIEWER"))
                    .thenReturn(List.of(Map.of("userId", "reviewer-1")));

            Map<String, String> params = Map.of("action", "SUBMIT_FOR_REVIEW", "actor", "do-1", "remarks", "Ready");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "SUBMIT_FOR_REVIEW", params);

            assertThat(result.get("newStatus")).isEqualTo("reviewer_review");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_REVIEWER");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("reviewer-1");
        }

        @Test
        @DisplayName("APPROVE_REVIEW should assign to CEPC_INCHARGE")
        void approveReviewShouldAssignIncharge() {
            sampleComplaint.setStatus("reviewer_review");
            sampleComplaint.setAssignedRole("CEPC_REVIEWER");
            when(keycloakUserService.getUsersByRole("CEPC_INCHARGE"))
                    .thenReturn(List.of(Map.of("userId", "incharge-1")));

            Map<String, String> params = Map.of("action", "APPROVE_REVIEW", "actor", "reviewer-1", "remarks", "OK");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "APPROVE_REVIEW", params);

            assertThat(result.get("newStatus")).isEqualTo("incharge_review");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_INCHARGE");
        }

        @Test
        @DisplayName("SEND_BACK_DO should revert to CEPC_DO with specified targetUser")
        void sendBackDoShouldRevertToDo() {
            sampleComplaint.setStatus("reviewer_review");
            Map<String, String> params = new HashMap<>();
            params.put("action", "SEND_BACK_DO");
            params.put("actor", "reviewer-1");
            params.put("remarks", "Incomplete");
            params.put("targetUser", "do-officer-1");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "SEND_BACK_DO", params);

            assertThat(result.get("newStatus")).isEqualTo("sent_back");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_DO");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("do-officer-1");
        }

        @Test
        @DisplayName("APPROVE_CLOSURE should set awaiting_closure")
        void approveClosureShouldTransition() {
            sampleComplaint.setStatus("incharge_review");
            Map<String, String> params = Map.of("action", "APPROVE_CLOSURE", "actor", "incharge-1", "remarks", "Approved");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "APPROVE_CLOSURE", params);

            assertThat(result.get("newStatus")).isEqualTo("awaiting_closure");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_CLOSING_AUTHORITY");
        }

        @Test
        @DisplayName("CLOSE_COMPLAINT should set closed and set closedAt/resolvedAt")
        void closeComplaintShouldSetDates() {
            sampleComplaint.setStatus("awaiting_closure");
            Map<String, String> params = Map.of("action", "CLOSE_COMPLAINT", "actor", "ca-1", "remarks", "Done");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "CLOSE_COMPLAINT", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(sampleComplaint.getClosedAt()).isNotNull();
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("REASSIGN should update assignedOfficer and set status to assigned")
        void reassignShouldUpdateOfficer() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "REASSIGN");
            params.put("actor", "admin-1");
            params.put("remarks", "Reassigning");
            params.put("targetUser", "do-officer-2");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "REASSIGN", params);

            assertThat(result.get("newStatus")).isEqualTo("assigned");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("do-officer-2");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("REASSIGNED");
        }

        @Test
        @DisplayName("ESCALATE should set escalated and set escalatedAt")
        void escalateShouldSetDate() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "ESCALATE", "actor", "admin-1", "remarks", "SLA breach");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "ESCALATE", params);

            assertThat(result.get("newStatus")).isEqualTo("escalated");
            assertThat(sampleComplaint.getEscalatedAt()).isNotNull();
        }

        @Test
        @DisplayName("FORWARD_TO_CONTACT should set role to CEPC_CONTACT_PERSON")
        void forwardToContactShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "FORWARD_TO_CONTACT");
            params.put("actor", "do-1");
            params.put("remarks", "Forward");
            params.put("targetUser", "contact-person-1");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "FORWARD_TO_CONTACT", params);

            assertThat(result.get("newStatus")).isEqualTo("forwarded_to_contact");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_CONTACT_PERSON");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("contact-person-1");
        }

        @Test
        @DisplayName("CONTACT_RESPONSE should revert to CEPC_DO in_progress")
        void contactResponseShouldRevert() {
            sampleComplaint.setStatus("forwarded_to_contact");
            sampleComplaint.setAssignedRole("CEPC_CONTACT_PERSON");
            Map<String, String> params = Map.of("action", "CONTACT_RESPONSE", "actor", "contact-1", "remarks", "Response");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "CONTACT_RESPONSE", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(result.get("assignedRole")).isEqualTo("CEPC_DO");
        }

        @Test
        @DisplayName("REOPEN should clear resolvedAt/closedAt and set status to in_progress")
        void reopenShouldClearDates() {
            sampleComplaint.setStatus("closed");
            sampleComplaint.setResolvedAt(LocalDateTime.now().minusDays(1));
            sampleComplaint.setClosedAt(LocalDateTime.now().minusDays(1));
            Map<String, String> params = Map.of("action", "REOPEN", "actor", "admin-1", "remarks", "Reopening");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "REOPEN", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getResolvedAt()).isNull();
            assertThat(sampleComplaint.getClosedAt()).isNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("REOPENED");
            verify(cepcSlaService).applySlaDeadline(sampleComplaint);
        }

        @Test
        @DisplayName("FORWARD_TO_OTHER_OFFICE should set forwarded_external")
        void forwardToOtherOfficeShouldTransition() {
            sampleComplaint.setStatus("awaiting_closure");
            Map<String, String> params = new HashMap<>();
            params.put("action", "FORWARD_TO_OTHER_OFFICE");
            params.put("actor", "ca-1");
            params.put("remarks", "Not our jurisdiction");
            params.put("targetOffice", "Mumbai Office");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "FORWARD_TO_OTHER_OFFICE", params);

            assertThat(result.get("newStatus")).isEqualTo("forwarded_external");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("FORWARDED_OTHER_OFFICE");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("Mumbai Office");
        }

        @Test
        @DisplayName("FORWARD_TO_REGULATORY_BODY should set forwarded_external")
        void forwardToRegulatoryBodyShouldTransition() {
            sampleComplaint.setStatus("awaiting_closure");
            Map<String, String> params = new HashMap<>();
            params.put("action", "FORWARD_TO_REGULATORY_BODY");
            params.put("actor", "ca-1");
            params.put("remarks", "SEBI matter");
            params.put("targetBody", "SEBI");

            Map<String, Object> result = cepcWorkflowService.performAction("CMP-20260706-123456", "FORWARD_TO_REGULATORY_BODY", params);

            assertThat(result.get("newStatus")).isEqualTo("forwarded_external");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("FORWARDED_REGULATORY_BODY");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("SEBI");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unknown action")
        void shouldThrowForUnknownAction() {
            // Override save behavior for this test - the exception is thrown before save is called
            reset(complaintRepository);
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            Map<String, String> params = Map.of("action", "INVALID", "actor", "do-1", "remarks", "");

            assertThatThrownBy(() -> cepcWorkflowService.performAction("CMP-20260706-123456", "INVALID", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown CEPC action");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for non-existent complaint")
        void shouldThrowForNonExistentComplaint() {
            // Override findByComplaintNumber for this test
            reset(complaintRepository);
            when(complaintRepository.findByComplaintNumber("NONEXIST"))
                    .thenReturn(Optional.empty());

            Map<String, String> params = Map.of("action", "ACCEPT", "actor", "do-1", "remarks", "");

            assertThatThrownBy(() -> cepcWorkflowService.performAction("NONEXIST", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Complaint not found");
        }

        @Test
        @DisplayName("should save complaint and add timeline on every action")
        void shouldSaveAndAddTimeline() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "REQUEST_INFO", "actor", "do-1", "remarks", "Need docs");

            cepcWorkflowService.performAction("CMP-20260706-123456", "REQUEST_INFO", params);

            verify(complaintRepository).save(sampleComplaint);
            verify(complaintService).addTimeline(eq(1L), eq("REQUEST_INFO"), eq("do-1"),
                    eq("Need docs"), eq("in_progress"), eq("info_requested"));
        }

        @Test
        @DisplayName("should log audit on every action")
        void shouldLogAudit() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "REQUEST_INFO");
            params.put("actor", "do-1");
            params.put("remarks", "Need docs");
            params.put("userRole", "CEPC_DO");

            cepcWorkflowService.performAction("CMP-20260706-123456", "REQUEST_INFO", params);

            verify(cepcAuditService).logActionAsync(
                    eq("CMP-20260706-123456"), eq("REQUEST_INFO"), eq("do-1"),
                    eq("CEPC_DO"), eq("Need docs"), any(), eq("in_progress"), eq("info_requested"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getAvailableActions()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAvailableActions()")
    class GetAvailableActions {

        @Test
        @DisplayName("CEPC_DO with assigned status should include ACCEPT")
        void doWithAssignedShouldIncludeAccept() {
            sampleComplaint.setStatus("assigned");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_DO");

            assertThat(actions).contains("ACCEPT");
        }

        @Test
        @DisplayName("CEPC_DO with in_progress status should include examination actions")
        void doWithInProgressShouldIncludeExaminationActions() {
            sampleComplaint.setStatus("in_progress");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_DO");

            assertThat(actions).contains("REQUEST_INFO", "FORWARD_DEPT", "SCHEDULE_MEETING",
                    "SUBMIT_FOR_REVIEW", "FORWARD_TO_CONTACT", "FORWARD_TO_INCHARGE");
        }

        @Test
        @DisplayName("CEPC_REVIEWER with reviewer_review status should include APPROVE_REVIEW and SEND_BACK_DO")
        void reviewerWithReviewStatusShouldIncludeActions() {
            sampleComplaint.setStatus("reviewer_review");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_REVIEWER");

            assertThat(actions).contains("APPROVE_REVIEW", "SEND_BACK_DO");
        }

        @Test
        @DisplayName("CEPC_CLOSING_AUTHORITY with awaiting_closure should include CLOSE_COMPLAINT")
        void closingAuthWithAwaitingClosureShouldIncludeClose() {
            sampleComplaint.setStatus("awaiting_closure");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_CLOSING_AUTHORITY");

            assertThat(actions).contains("CLOSE_COMPLAINT", "SEND_BACK_INCHARGE");
        }

        @Test
        @DisplayName("should return empty for null role")
        void shouldReturnEmptyForNullRole() {
            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", null);

            assertThat(actions).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent complaint")
        void shouldReturnEmptyForNonExistent() {
            when(complaintRepository.findByComplaintNumber("NONEXIST"))
                    .thenReturn(Optional.empty());

            List<String> actions = cepcWorkflowService.getAvailableActions("NONEXIST", "CEPC_DO");

            assertThat(actions).isEmpty();
        }

        @Test
        @DisplayName("CEPC_CONTACT_PERSON should only have actions in forwarded_to_contact status")
        void contactPersonShouldOnlyActInForwardedStatus() {
            sampleComplaint.setStatus("forwarded_to_contact");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_CONTACT_PERSON");

            assertThat(actions).contains("CONTACT_RESPONSE", "CONTACT_REASSIGN");
        }

        @Test
        @DisplayName("CEPC_CONTACT_PERSON should have no actions in other statuses")
        void contactPersonShouldHaveNoActionsInOtherStatus() {
            sampleComplaint.setStatus("in_progress");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = cepcWorkflowService.getAvailableActions("CMP-20260706-123456", "CEPC_CONTACT_PERSON");

            assertThat(actions).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // calculateSlaDueDate()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("calculateSlaDueDate()")
    class CalculateSlaDueDate {

        @Test
        @DisplayName("should delegate to CepcSlaService with complaint's createdAt and priority")
        void shouldDelegateToSlaService() {
            LocalDateTime created = LocalDateTime.of(2025, 7, 7, 10, 0);
            sampleComplaint.setCreatedAt(created);
            sampleComplaint.setPriority("HIGH");

            LocalDateTime expectedDeadline = LocalDateTime.of(2025, 7, 28, 18, 0);
            when(cepcSlaService.calculateDeadline(created, "HIGH")).thenReturn(expectedDeadline);

            LocalDateTime result = cepcWorkflowService.calculateSlaDueDate(sampleComplaint);

            assertThat(result).isEqualTo(expectedDeadline);
            verify(cepcSlaService).calculateDeadline(created, "HIGH");
        }

        @Test
        @DisplayName("should use current time when createdAt is null")
        void shouldUseCurrentTimeWhenCreatedAtNull() {
            sampleComplaint.setCreatedAt(null);
            sampleComplaint.setPriority("MEDIUM");

            when(cepcSlaService.calculateDeadline(any(), eq("MEDIUM")))
                    .thenReturn(LocalDateTime.now().plusDays(30));

            LocalDateTime result = cepcWorkflowService.calculateSlaDueDate(sampleComplaint);

            assertThat(result).isNotNull();
            verify(cepcSlaService).calculateDeadline(any(), eq("MEDIUM"));
        }
    }
}
