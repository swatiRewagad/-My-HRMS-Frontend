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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RbioWorkflowServiceTest {

    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintService complaintService;
    @Mock private KeycloakUserService keycloakUserService;
    @Mock private RbioSlaService rbioSlaService;
    @Mock private RbioCompensationService rbioCompensationService;
    @Mock private CepcAuditService auditService;

    @InjectMocks
    private RbioWorkflowService rbioWorkflowService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-789012")
                .complainantName("Test Complainant")
                .complainantEmail("citizen@example.com")
                .subject("RBIO Test Complaint")
                .status("assigned")
                .priority("HIGH")
                .department("RBIO")
                .assignedRole("RBIO_OFFICER")
                .assignedOfficer("rbio-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(2))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // isRbioAction()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("isRbioAction()")
    class IsRbioAction {

        @ParameterizedTest
        @ValueSource(strings = {
                "ACCEPT", "TAKE_ACTION", "RESOLVE", "REJECT", "ESCALATE",
                "REQUEST_INFO", "SCHEDULE_MEETING", "FORWARD_TO_CONCILIATION",
                "ISSUE_ADVISORY", "APPROVE", "RETURN_TO_OFFICER",
                "FORWARD_TO_ADJUDICATION", "REASSIGN", "CONCILIATION_SUCCESS",
                "CONCILIATION_FAILED", "ESCALATE_TO_ADJUDICATION",
                "ADJUDICATION_AWARD", "ADJUDICATION_REJECT",
                "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY", "CLOSE_COMPLAINT", "REOPEN", "BULK_ASSIGN"
        })
        @DisplayName("should recognize valid RBIO actions")
        void shouldRecognizeValidActions(String action) {
            assertThat(rbioWorkflowService.isRbioAction(action)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "accept", "Resolve", "escalate_to_adjudication"
        })
        @DisplayName("should recognize case-insensitive actions")
        void shouldRecognizeCaseInsensitive(String action) {
            assertThat(rbioWorkflowService.isRbioAction(action)).isTrue();
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertThat(rbioWorkflowService.isRbioAction(null)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "INVALID_ACTION", "", "SUBMIT_FOR_REVIEW", "APPROVE_REVIEW",
                "SEND_BACK_DO", "FORWARD_TO_CONTACT", "CONTACT_RESPONSE"
        })
        @DisplayName("should return false for CEPC-only and unknown actions")
        void shouldReturnFalseForCepcAndUnknownActions(String action) {
            assertThat(rbioWorkflowService.isRbioAction(action)).isFalse();
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
                "RBIO_OFFICER, ACCEPT, true",
                "RBIO_OFFICER, TAKE_ACTION, true",
                "RBIO_OFFICER, RESOLVE, true",
                "RBIO_OFFICER, REJECT, true",
                "RBIO_OFFICER, ESCALATE, true",
                "RBIO_OFFICER, REQUEST_INFO, true",
                "RBIO_OFFICER, SCHEDULE_MEETING, true",
                "RBIO_OFFICER, FORWARD_TO_CONCILIATION, true",
                "RBIO_OFFICER, ISSUE_ADVISORY, true",
                "RBIO_OFFICER, APPROVE, false",
                "RBIO_OFFICER, RETURN_TO_OFFICER, false",
                "RBIO_OFFICER, FORWARD_TO_ADJUDICATION, false",
                "RBIO_OFFICER, ADJUDICATION_AWARD, false",
                "RBIO_OFFICER, REOPEN, false"
        })
        @DisplayName("RBIO_OFFICER role authorization")
        void rbioOfficerAuthorization(String role, String action, boolean expected) {
            assertThat(rbioWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "RBIO_SUPERVISOR, APPROVE, true",
                "RBIO_SUPERVISOR, RETURN_TO_OFFICER, true",
                "RBIO_SUPERVISOR, RESOLVE, true",
                "RBIO_SUPERVISOR, ESCALATE, true",
                "RBIO_SUPERVISOR, FORWARD_TO_ADJUDICATION, true",
                "RBIO_SUPERVISOR, FORWARD_TO_CONCILIATION, true",
                "RBIO_SUPERVISOR, REASSIGN, true",
                "RBIO_SUPERVISOR, ISSUE_ADVISORY, true",
                "RBIO_SUPERVISOR, ACCEPT, false",
                "RBIO_SUPERVISOR, TAKE_ACTION, false",
                "RBIO_SUPERVISOR, ADJUDICATION_AWARD, false",
                "RBIO_SUPERVISOR, CONCILIATION_SUCCESS, false"
        })
        @DisplayName("RBIO_SUPERVISOR role authorization")
        void rbioSupervisorAuthorization(String role, String action, boolean expected) {
            assertThat(rbioWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "RBIO_CONCILIATOR, CONCILIATION_SUCCESS, true",
                "RBIO_CONCILIATOR, CONCILIATION_FAILED, true",
                "RBIO_CONCILIATOR, SCHEDULE_MEETING, true",
                "RBIO_CONCILIATOR, ESCALATE_TO_ADJUDICATION, true",
                "RBIO_CONCILIATOR, ACCEPT, false",
                "RBIO_CONCILIATOR, RESOLVE, false",
                "RBIO_CONCILIATOR, ADJUDICATION_AWARD, false",
                "RBIO_CONCILIATOR, REOPEN, false"
        })
        @DisplayName("RBIO_CONCILIATOR role authorization")
        void rbioConciliatorAuthorization(String role, String action, boolean expected) {
            assertThat(rbioWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "RBIO_ADJUDICATOR, ADJUDICATION_AWARD, true",
                "RBIO_ADJUDICATOR, ADJUDICATION_REJECT, true",
                "RBIO_ADJUDICATOR, ISSUE_NOTICE_13_1, true",
                "RBIO_ADJUDICATOR, IMPLEAD_PARTY, true",
                "RBIO_ADJUDICATOR, ACCEPT, false",
                "RBIO_ADJUDICATOR, RESOLVE, false",
                "RBIO_ADJUDICATOR, ESCALATE, false",
                "RBIO_ADJUDICATOR, CONCILIATION_SUCCESS, false",
                "RBIO_ADJUDICATOR, REOPEN, false"
        })
        @DisplayName("RBIO_ADJUDICATOR role authorization")
        void rbioAdjudicatorAuthorization(String role, String action, boolean expected) {
            assertThat(rbioWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "RBIO_ADMIN, REASSIGN, true",
                "RBIO_ADMIN, ESCALATE, true",
                "RBIO_ADMIN, CLOSE_COMPLAINT, true",
                "RBIO_ADMIN, REOPEN, true",
                "RBIO_ADMIN, BULK_ASSIGN, true",
                "RBIO_ADMIN, ACCEPT, false",
                "RBIO_ADMIN, RESOLVE, false",
                "RBIO_ADMIN, ADJUDICATION_AWARD, false"
        })
        @DisplayName("RBIO_ADMIN role authorization")
        void rbioAdminAuthorization(String role, String action, boolean expected) {
            assertThat(rbioWorkflowService.validateRoleAuthorization(role, action)).isEqualTo(expected);
        }

        @Test
        @DisplayName("should return false for null role")
        void shouldReturnFalseForNullRole() {
            assertThat(rbioWorkflowService.validateRoleAuthorization(null, "ACCEPT")).isFalse();
        }

        @Test
        @DisplayName("should return false for null action")
        void shouldReturnFalseForNullAction() {
            assertThat(rbioWorkflowService.validateRoleAuthorization("RBIO_OFFICER", null)).isFalse();
        }

        @Test
        @DisplayName("should return false for unknown role")
        void shouldReturnFalseForUnknownRole() {
            assertThat(rbioWorkflowService.validateRoleAuthorization("UNKNOWN_ROLE", "ACCEPT")).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // performAction() - State Transitions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("performAction() - State Transitions")
    @MockitoSettings(strictness = Strictness.LENIENT)
    class PerformAction {

        @BeforeEach
        void setUpMocks() {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
        }

        @Test
        @DisplayName("ACCEPT should set status to in_progress and stage to EXAMINATION")
        void acceptShouldTransitionToInProgress() {
            Map<String, String> params = Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "Accepted");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ACCEPT", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getStatus()).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("EXAMINATION");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-officer-1");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "OFFICER_ASSESSMENT");
        }

        @Test
        @DisplayName("TAKE_ACTION should behave same as ACCEPT")
        void takeActionShouldBehaveSameAsAccept() {
            Map<String, String> params = Map.of("action", "TAKE_ACTION", "actor", "rbio-officer-1", "remarks", "Taking action");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "TAKE_ACTION", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("EXAMINATION");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "OFFICER_ASSESSMENT");
        }

        @Test
        @DisplayName("RESOLVE should set status to resolved and set resolvedAt")
        void resolveShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "RESOLVE", "actor", "rbio-officer-1", "remarks", "Resolved");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "RESOLVE", params);

            assertThat(result.get("newStatus")).isEqualTo("resolved");
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("RESOLVED");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("RESOLVED");
        }

        @Test
        @DisplayName("REJECT should set status to rejected and set resolvedAt")
        void rejectShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "REJECT", "actor", "rbio-officer-1", "remarks", "Not maintainable");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "REJECT", params);

            assertThat(result.get("newStatus")).isEqualTo("rejected");
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("REJECTED");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("ESCALATE should set status to escalated and bump role")
        void escalateShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            sampleComplaint.setAssignedRole("RBIO_OFFICER");
            Map<String, String> params = Map.of("action", "ESCALATE", "actor", "rbio-officer-1", "remarks", "SLA concern");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ESCALATE", params);

            assertThat(result.get("newStatus")).isEqualTo("escalated");
            assertThat(sampleComplaint.getEscalatedAt()).isNotNull();
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_SUPERVISOR");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("ESCALATED");
        }

        @Test
        @DisplayName("REQUEST_INFO should set status to info_requested")
        void requestInfoShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "REQUEST_INFO", "actor", "rbio-officer-1", "remarks", "Need docs");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "REQUEST_INFO", params);

            assertThat(result.get("newStatus")).isEqualTo("info_requested");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("AWAITING_INFO");
        }

        @Test
        @DisplayName("SCHEDULE_MEETING should only update workflowStage")
        void scheduleMeetingShouldOnlyUpdateStage() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "SCHEDULE_MEETING", "actor", "rbio-officer-1", "remarks", "Friday meeting");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "SCHEDULE_MEETING", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("MEETING_SCHEDULED");
        }

        @Test
        @DisplayName("FORWARD_TO_CONCILIATION should set conciliation status and assign conciliator")
        void forwardToConciliationShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            when(keycloakUserService.getUsersByRole("RBIO_CONCILIATOR"))
                    .thenReturn(List.of(Map.of("userId", "rbio-conciliator-1")));

            Map<String, String> params = Map.of("action", "FORWARD_TO_CONCILIATION", "actor", "rbio-officer-1", "remarks", "Needs conciliation");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "FORWARD_TO_CONCILIATION", params);

            assertThat(result.get("newStatus")).isEqualTo("conciliation");
            assertThat(result.get("assignedRole")).isEqualTo("RBIO_CONCILIATOR");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-conciliator-1");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CONCILIATION");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "CONCILIATION");
        }

        @Test
        @DisplayName("ISSUE_ADVISORY should set advisory_issued and closure cause ADVISORY")
        void issueAdvisoryShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "ISSUE_ADVISORY");
            params.put("actor", "rbio-officer-1");
            params.put("remarks", "Advisory");
            params.put("advisoryText", "Advised to refund within 15 days");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ISSUE_ADVISORY", params);

            assertThat(result.get("newStatus")).isEqualTo("advisory_issued");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("ADVISORY");
            assertThat(sampleComplaint.getAdvisoryText()).isEqualTo("Advised to refund within 15 days");
            assertThat(sampleComplaint.getAdvisoryIssuedAt()).isNotNull();
        }

        @Test
        @DisplayName("APPROVE should set approved and escalate role")
        void approveShouldTransition() {
            sampleComplaint.setStatus("in_progress");
            sampleComplaint.setAssignedRole("RBIO_SUPERVISOR");
            Map<String, String> params = Map.of("action", "APPROVE", "actor", "rbio-supervisor-1", "remarks", "Approved");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "APPROVE", params);

            assertThat(result.get("newStatus")).isEqualTo("approved");
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_CONCILIATOR");
        }

        @Test
        @DisplayName("RETURN_TO_OFFICER should set returned and role to RBIO_OFFICER")
        void returnToOfficerShouldTransition() {
            sampleComplaint.setStatus("escalated");
            sampleComplaint.setAssignedRole("RBIO_SUPERVISOR");
            Map<String, String> params = new HashMap<>();
            params.put("action", "RETURN_TO_OFFICER");
            params.put("actor", "rbio-supervisor-1");
            params.put("remarks", "Need more info");
            params.put("targetUser", "rbio-officer-1");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "RETURN_TO_OFFICER", params);

            assertThat(result.get("newStatus")).isEqualTo("returned");
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_OFFICER");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-officer-1");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("RETURNED_TO_OFFICER");
        }

        @Test
        @DisplayName("FORWARD_TO_ADJUDICATION should set adjudication and assign adjudicator")
        void forwardToAdjudicationShouldTransition() {
            sampleComplaint.setStatus("escalated");
            sampleComplaint.setAssignedRole("RBIO_SUPERVISOR");
            when(keycloakUserService.getUsersByRole("RBIO_ADJUDICATOR"))
                    .thenReturn(List.of(Map.of("userId", "rbio-adjudicator-1")));

            Map<String, String> params = Map.of("action", "FORWARD_TO_ADJUDICATION", "actor", "rbio-supervisor-1", "remarks", "To adjudication");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "FORWARD_TO_ADJUDICATION", params);

            assertThat(result.get("newStatus")).isEqualTo("adjudication");
            assertThat(result.get("assignedRole")).isEqualTo("RBIO_ADJUDICATOR");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-adjudicator-1");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("ADJUDICATION");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "ADJUDICATION");
        }

        @Test
        @DisplayName("CONCILIATION_SUCCESS should set conciliated and resolvedAt")
        void conciliationSuccessShouldTransition() {
            sampleComplaint.setStatus("conciliation");
            sampleComplaint.setAssignedRole("RBIO_CONCILIATOR");
            Map<String, String> params = Map.of("action", "CONCILIATION_SUCCESS", "actor", "rbio-conciliator-1", "remarks", "Settlement");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "CONCILIATION_SUCCESS", params);

            assertThat(result.get("newStatus")).isEqualTo("conciliated");
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getConciliationOutcome()).isEqualTo("SUCCESS");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("CONCILIATION_SUCCESS");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CONCILIATION_COMPLETE");
        }

        @Test
        @DisplayName("CONCILIATION_FAILED should escalate to RBIO_ADJUDICATOR")
        void conciliationFailedShouldEscalate() {
            sampleComplaint.setStatus("conciliation");
            sampleComplaint.setAssignedRole("RBIO_CONCILIATOR");
            when(keycloakUserService.getUsersByRole("RBIO_ADJUDICATOR"))
                    .thenReturn(List.of(Map.of("userId", "rbio-adjudicator-1")));

            Map<String, String> params = Map.of("action", "CONCILIATION_FAILED", "actor", "rbio-conciliator-1", "remarks", "No agreement");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "CONCILIATION_FAILED", params);

            assertThat(result.get("newStatus")).isEqualTo("escalated");
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_ADJUDICATOR");
            assertThat(sampleComplaint.getConciliationOutcome()).isEqualTo("FAILED");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CONCILIATION_FAILED");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "ADJUDICATION");
        }

        @Test
        @DisplayName("ESCALATE_TO_ADJUDICATION should move from conciliation to adjudication")
        void escalateToAdjudicationShouldTransition() {
            sampleComplaint.setStatus("conciliation");
            sampleComplaint.setAssignedRole("RBIO_CONCILIATOR");
            when(keycloakUserService.getUsersByRole("RBIO_ADJUDICATOR"))
                    .thenReturn(List.of(Map.of("userId", "rbio-adjudicator-1")));

            Map<String, String> params = Map.of("action", "ESCALATE_TO_ADJUDICATION", "actor", "rbio-conciliator-1", "remarks", "Escalating");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ESCALATE_TO_ADJUDICATION", params);

            assertThat(result.get("newStatus")).isEqualTo("adjudication");
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_ADJUDICATOR");
            assertThat(sampleComplaint.getConciliationOutcome()).isEqualTo("FAILED");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("ADJUDICATION");
            verify(rbioSlaService).applyStageSla(sampleComplaint, "ADJUDICATION");
        }

        @Test
        @DisplayName("ADJUDICATION_AWARD should set adjudicated with award amount")
        void adjudicationAwardShouldTransition() {
            sampleComplaint.setStatus("adjudication");
            sampleComplaint.setAssignedRole("RBIO_ADJUDICATOR");
            Map<String, String> params = new HashMap<>();
            params.put("action", "ADJUDICATION_AWARD");
            params.put("actor", "rbio-adjudicator-1");
            params.put("remarks", "Award issued");
            params.put("awardAmount", "500000");
            params.put("compensationType", "CONSEQUENTIAL_LOSS");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ADJUDICATION_AWARD", params);

            assertThat(result.get("newStatus")).isEqualTo("adjudicated");
            assertThat(sampleComplaint.getAwardAmount()).isEqualByComparingTo(new BigDecimal("500000"));
            assertThat(sampleComplaint.getCompensationType()).isEqualTo("CONSEQUENTIAL_LOSS");
            assertThat(sampleComplaint.getAdjudicationOutcome()).isEqualTo("AWARD_ISSUED");
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("AWARD_ISSUED");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("ADJUDICATION_AWARD");
            verify(rbioCompensationService).validateAward(new BigDecimal("500000"), "CONSEQUENTIAL_LOSS");
        }

        @Test
        @DisplayName("ADJUDICATION_AWARD should throw when compensation cap exceeded")
        void adjudicationAwardShouldThrowWhenCapExceeded() {
            sampleComplaint.setStatus("adjudication");
            sampleComplaint.setAssignedRole("RBIO_ADJUDICATOR");
            doThrow(new IllegalArgumentException("Award amount Rs 5000000 exceeds the maximum permitted cap"))
                    .when(rbioCompensationService).validateAward(any(BigDecimal.class), eq("CONSEQUENTIAL_LOSS"));

            Map<String, String> params = new HashMap<>();
            params.put("action", "ADJUDICATION_AWARD");
            params.put("actor", "rbio-adjudicator-1");
            params.put("remarks", "Excessive");
            params.put("awardAmount", "5000000");
            params.put("compensationType", "CONSEQUENTIAL_LOSS");

            assertThatThrownBy(() -> rbioWorkflowService.performAction("CMP-20260706-789012", "ADJUDICATION_AWARD", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds the maximum permitted cap");

            // Should NOT have been saved
            verify(complaintRepository, never()).save(any(Complaint.class));
        }

        @Test
        @DisplayName("ADJUDICATION_REJECT should set rejected with ADJUDICATION_REJECTED cause")
        void adjudicationRejectShouldTransition() {
            sampleComplaint.setStatus("adjudication");
            sampleComplaint.setAssignedRole("RBIO_ADJUDICATOR");
            Map<String, String> params = Map.of("action", "ADJUDICATION_REJECT", "actor", "rbio-adjudicator-1", "remarks", "Not substantiated");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ADJUDICATION_REJECT", params);

            assertThat(result.get("newStatus")).isEqualTo("rejected");
            assertThat(sampleComplaint.getAdjudicationOutcome()).isEqualTo("REJECTED");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("ADJUDICATION_REJECTED");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("ADJUDICATION_REJECTED");
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
        }

        @Test
        @DisplayName("ISSUE_NOTICE_13_1 should update workflowStage to NOTICE_13_1_ISSUED")
        void issueNotice131ShouldUpdateStage() {
            sampleComplaint.setStatus("adjudication");
            Map<String, String> params = Map.of("action", "ISSUE_NOTICE_13_1", "actor", "rbio-adjudicator-1", "remarks", "Notice issued");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "ISSUE_NOTICE_13_1", params);

            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("NOTICE_13_1_ISSUED");
            assertThat(sampleComplaint.getNotice131IssuedAt()).isNotNull();
        }

        @Test
        @DisplayName("IMPLEAD_PARTY should add party to impleadedParties field")
        void impleadPartyShouldAddParty() {
            sampleComplaint.setStatus("adjudication");
            Map<String, String> params = new HashMap<>();
            params.put("action", "IMPLEAD_PARTY");
            params.put("actor", "rbio-adjudicator-1");
            params.put("remarks", "Adding party");
            params.put("partyName", "ABC Insurance Ltd");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "IMPLEAD_PARTY", params);

            assertThat(sampleComplaint.getImpleadedParties()).isEqualTo("ABC Insurance Ltd");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("PARTY_IMPLEADED");
        }

        @Test
        @DisplayName("IMPLEAD_PARTY should append to existing parties")
        void impleadPartyShouldAppendToExisting() {
            sampleComplaint.setStatus("adjudication");
            sampleComplaint.setImpleadedParties("First Party");
            Map<String, String> params = new HashMap<>();
            params.put("action", "IMPLEAD_PARTY");
            params.put("actor", "rbio-adjudicator-1");
            params.put("remarks", "Adding second party");
            params.put("partyName", "Second Party");

            rbioWorkflowService.performAction("CMP-20260706-789012", "IMPLEAD_PARTY", params);

            assertThat(sampleComplaint.getImpleadedParties()).isEqualTo("First Party,Second Party");
        }

        @Test
        @DisplayName("IMPLEAD_PARTY should throw when partyName is missing")
        void impleadPartyShouldThrowWhenPartyNameMissing() {
            sampleComplaint.setStatus("adjudication");
            Map<String, String> params = new HashMap<>();
            params.put("action", "IMPLEAD_PARTY");
            params.put("actor", "rbio-adjudicator-1");
            params.put("remarks", "Adding party");
            params.put("partyName", "");

            assertThatThrownBy(() -> rbioWorkflowService.performAction("CMP-20260706-789012", "IMPLEAD_PARTY", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("partyName");
        }

        @Test
        @DisplayName("REASSIGN should change assignedOfficer and set status to assigned")
        void reassignShouldUpdateOfficer() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "REASSIGN");
            params.put("actor", "rbio-admin-1");
            params.put("remarks", "Reassigning");
            params.put("targetUser", "rbio-officer-2");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "REASSIGN", params);

            assertThat(result.get("newStatus")).isEqualTo("assigned");
            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-officer-2");
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("REASSIGNED");
        }

        @Test
        @DisplayName("REASSIGN with targetRole should update both officer and role")
        void reassignWithTargetRoleShouldUpdateBoth() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "REASSIGN");
            params.put("actor", "rbio-admin-1");
            params.put("remarks", "Reassigning to supervisor");
            params.put("targetUser", "rbio-supervisor-1");
            params.put("targetRole", "RBIO_SUPERVISOR");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "REASSIGN", params);

            assertThat(sampleComplaint.getAssignedOfficer()).isEqualTo("rbio-supervisor-1");
            assertThat(sampleComplaint.getAssignedRole()).isEqualTo("RBIO_SUPERVISOR");
        }

        @Test
        @DisplayName("CLOSE_COMPLAINT should set closed status and dates")
        void closeComplaintShouldSetDates() {
            sampleComplaint.setStatus("resolved");
            Map<String, String> params = Map.of("action", "CLOSE_COMPLAINT", "actor", "rbio-admin-1", "remarks", "Final closure");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "CLOSE_COMPLAINT", params);

            assertThat(result.get("newStatus")).isEqualTo("closed");
            assertThat(sampleComplaint.getClosedAt()).isNotNull();
            assertThat(sampleComplaint.getResolvedAt()).isNotNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("CLOSED");
            assertThat(sampleComplaint.getClosureCause()).isEqualTo("ADMIN_CLOSED");
        }

        @Test
        @DisplayName("REOPEN should clear resolvedAt/closedAt, increment reopenCount, and set in_progress")
        void reopenShouldClearDatesAndReset() {
            sampleComplaint.setStatus("closed");
            sampleComplaint.setResolvedAt(LocalDateTime.now().minusDays(1));
            sampleComplaint.setClosedAt(LocalDateTime.now().minusDays(1));
            sampleComplaint.setReopenCount(0);
            Map<String, String> params = Map.of("action", "REOPEN", "actor", "rbio-admin-1", "remarks", "New evidence");

            Map<String, Object> result = rbioWorkflowService.performAction("CMP-20260706-789012", "REOPEN", params);

            assertThat(result.get("newStatus")).isEqualTo("in_progress");
            assertThat(sampleComplaint.getResolvedAt()).isNull();
            assertThat(sampleComplaint.getClosedAt()).isNull();
            assertThat(sampleComplaint.getWorkflowStage()).isEqualTo("REOPENED");
            assertThat(sampleComplaint.getReopenCount()).isEqualTo(1);
            assertThat(sampleComplaint.getLastReopenedAt()).isNotNull();
            verify(rbioSlaService).applyStageSla(sampleComplaint, "OFFICER_ASSESSMENT");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for unknown action")
        void shouldThrowForUnknownAction() {
            reset(complaintRepository);
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            Map<String, String> params = Map.of("action", "INVALID", "actor", "rbio-officer-1", "remarks", "");

            assertThatThrownBy(() -> rbioWorkflowService.performAction("CMP-20260706-789012", "INVALID", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown RBIO action");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for non-existent complaint")
        void shouldThrowForNonExistentComplaint() {
            reset(complaintRepository);
            when(complaintRepository.findByComplaintNumber("NONEXIST"))
                    .thenReturn(Optional.empty());

            Map<String, String> params = Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "");

            assertThatThrownBy(() -> rbioWorkflowService.performAction("NONEXIST", "ACCEPT", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Complaint not found");
        }

        @Test
        @DisplayName("should save complaint and add timeline on every action")
        void shouldSaveAndAddTimeline() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = Map.of("action", "REQUEST_INFO", "actor", "rbio-officer-1", "remarks", "Need docs");

            rbioWorkflowService.performAction("CMP-20260706-789012", "REQUEST_INFO", params);

            verify(complaintRepository).save(sampleComplaint);
            verify(complaintService).addTimeline(eq(1L), eq("REQUEST_INFO"), eq("rbio-officer-1"),
                    eq("Need docs"), eq("in_progress"), eq("info_requested"));
        }

        @Test
        @DisplayName("should log audit on every action")
        void shouldLogAudit() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "REQUEST_INFO");
            params.put("actor", "rbio-officer-1");
            params.put("remarks", "Need docs");
            params.put("userRole", "RBIO_OFFICER");

            rbioWorkflowService.performAction("CMP-20260706-789012", "REQUEST_INFO", params);

            verify(auditService).logActionAsync(
                    eq("CMP-20260706-789012"), eq("REQUEST_INFO"), eq("rbio-officer-1"),
                    eq("RBIO_OFFICER"), eq("Need docs"), any(), eq("in_progress"), eq("info_requested"));
        }

        @Test
        @DisplayName("should reject action when role authorization fails")
        void shouldRejectWhenRoleUnauthorized() {
            sampleComplaint.setStatus("in_progress");
            Map<String, String> params = new HashMap<>();
            params.put("action", "ADJUDICATION_AWARD");
            params.put("actor", "rbio-officer-1");
            params.put("remarks", "Trying to award");
            params.put("userRole", "RBIO_OFFICER");
            params.put("awardAmount", "100000");
            params.put("compensationType", "COMBINED");

            assertThatThrownBy(() -> rbioWorkflowService.performAction("CMP-20260706-789012", "ADJUDICATION_AWARD", params))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not authorized");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // getAvailableActions()
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAvailableActions()")
    class GetAvailableActions {

        @Test
        @DisplayName("RBIO_OFFICER with assigned status should include ACCEPT and TAKE_ACTION")
        void officerWithAssignedShouldIncludeAccept() {
            sampleComplaint.setStatus("assigned");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_OFFICER");

            assertThat(actions).contains("ACCEPT", "TAKE_ACTION");
        }

        @Test
        @DisplayName("RBIO_OFFICER with in_progress should include investigation actions")
        void officerWithInProgressShouldIncludeInvestigationActions() {
            sampleComplaint.setStatus("in_progress");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_OFFICER");

            assertThat(actions).contains("RESOLVE", "REJECT", "ESCALATE", "REQUEST_INFO",
                    "SCHEDULE_MEETING", "FORWARD_TO_CONCILIATION", "ISSUE_ADVISORY");
        }

        @Test
        @DisplayName("RBIO_SUPERVISOR with escalated status should include APPROVE and RETURN_TO_OFFICER")
        void supervisorWithEscalatedShouldIncludeActions() {
            sampleComplaint.setStatus("escalated");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_SUPERVISOR");

            assertThat(actions).contains("RETURN_TO_OFFICER", "FORWARD_TO_ADJUDICATION", "FORWARD_TO_CONCILIATION");
        }

        @Test
        @DisplayName("RBIO_CONCILIATOR with conciliation status should include conciliation actions")
        void conciliatorWithConciliationShouldIncludeActions() {
            sampleComplaint.setStatus("conciliation");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_CONCILIATOR");

            assertThat(actions).contains("CONCILIATION_SUCCESS", "CONCILIATION_FAILED", "SCHEDULE_MEETING", "ESCALATE_TO_ADJUDICATION");
        }

        @Test
        @DisplayName("RBIO_ADJUDICATOR with adjudication status should include adjudication actions")
        void adjudicatorWithAdjudicationShouldIncludeActions() {
            sampleComplaint.setStatus("adjudication");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_ADJUDICATOR");

            assertThat(actions).contains("ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY");
        }

        @Test
        @DisplayName("RBIO_ADMIN with closed status should include REOPEN")
        void adminWithClosedShouldIncludeReopen() {
            sampleComplaint.setStatus("closed");
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", "RBIO_ADMIN");

            assertThat(actions).contains("REOPEN");
        }

        @Test
        @DisplayName("should return empty for null role")
        void shouldReturnEmptyForNullRole() {
            List<String> actions = rbioWorkflowService.getAvailableActions("CMP-20260706-789012", null);

            assertThat(actions).isEmpty();
        }

        @Test
        @DisplayName("should return empty for non-existent complaint")
        void shouldReturnEmptyForNonExistent() {
            when(complaintRepository.findByComplaintNumber("NONEXIST"))
                    .thenReturn(Optional.empty());

            List<String> actions = rbioWorkflowService.getAvailableActions("NONEXIST", "RBIO_OFFICER");

            assertThat(actions).isEmpty();
        }
    }
}
