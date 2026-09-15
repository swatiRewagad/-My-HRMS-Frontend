package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.service.CepcSlaService;
import com.hrms.cms.service.CepcWorkflowService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.EncryptionKeyService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.RbioCompensationService;
import com.hrms.cms.service.RbioSlaService;
import com.hrms.cms.service.RbioWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WorkflowController.class)
@AutoConfigureMockMvc(addFilters = false)
class CepcWorkflowControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ComplaintRepository complaintRepository;
    @MockBean private ComplaintService complaintService;
    @MockBean private BankRepository bankRepository;
    @MockBean private KeycloakUserService keycloakUserService;
    @MockBean private ComplaintTimelineRepository complaintTimelineRepository;
    @MockBean private CepcWorkflowService cepcWorkflowService;
    @MockBean private CepcSlaService cepcSlaService;
    @MockBean private EncryptionKeyService encryptionKeyService;
    @MockBean private RbioWorkflowService rbioWorkflowService;
    @MockBean private RbioSlaService rbioSlaService;
    @MockBean private RbioCompensationService rbioCompensationService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-123456")
                .complainantName("Test Complainant")
                .complainantEmail("test@example.com")
                .complainantPhone("9876543210")
                .subject("CEPC Test Complaint")
                .description("Test description for CEPC workflow")
                .status("assigned")
                .priority("MEDIUM")
                .department("CEPC")
                .assignedRole("CEPC_DO")
                .assignedOfficer("do-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task Retrieval
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/workflow/cepc/tasks")
    class GetCepcTasks {

        @Test
        @DisplayName("should return tasks filtered by role")
        void shouldReturnTasksByRole() throws Exception {
            when(complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    eq("CEPC"), eq("CEPC_DO"), any()))
                    .thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/v1/workflow/cepc/tasks")
                            .param("role", "CEPC_DO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].complaintNumber").value("CMP-20260706-123456"))
                    .andExpect(jsonPath("$.data[0].assignedRole").value("CEPC_DO"));
        }

        @Test
        @DisplayName("should return tasks filtered by officer")
        void shouldReturnTasksByOfficer() throws Exception {
            when(complaintRepository.findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    eq("CEPC"), eq("do-officer-1"), any()))
                    .thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/v1/workflow/cepc/tasks")
                            .param("officer", "do-officer-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].assignedOfficer").value("do-officer-1"));
        }

        @Test
        @DisplayName("should return empty list when no tasks match")
        void shouldReturnEmptyWhenNoTasks() throws Exception {
            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("CEPC"), any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/workflow/cepc/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Complaint Creation
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/workflow/cepc/create-complaint")
    class CreateComplaint {

        @Test
        @DisplayName("should create complaint with valid data")
        void shouldCreateComplaintWithValidData() throws Exception {
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(10L);
                return c;
            });

            Map<String, String> request = new LinkedHashMap<>();
            request.put("complainantName", "Ravi Kumar");
            request.put("complainantEmail", "ravi@example.com");
            request.put("complainantPhone", "9876543210");
            request.put("subject", "Credit Card Overcharge");
            request.put("description", "Was charged twice for the same transaction");
            request.put("entityName", "HDFC Bank");
            request.put("priority", "HIGH");
            request.put("createdBy", "cepc-do-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/create-complaint")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.complaintNumber").exists())
                    .andExpect(jsonPath("$.data.status").value("assigned"))
                    .andExpect(jsonPath("$.data.department").value("CEPC"));

            verify(complaintRepository).save(argThat(c ->
                    "CEPC".equals(c.getDepartment()) &&
                    "CEPC_DO".equals(c.getAssignedRole()) &&
                    "assigned".equals(c.getStatus())
            ));
            verify(complaintService).addTimeline(eq(10L), eq("CREATED"), eq("cepc-do-1"),
                    any(), eq("new"), eq("assigned"));
        }

        @Test
        @DisplayName("should create complaint with defaults when fields are missing")
        void shouldCreateWithDefaultsWhenFieldsMissing() throws Exception {
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(11L);
                return c;
            });

            Map<String, String> request = new LinkedHashMap<>();
            request.put("subject", "Minimal complaint");

            mockMvc.perform(post("/api/v1/workflow/cepc/create-complaint")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("assigned"));

            verify(complaintRepository).save(argThat(c ->
                    "MEDIUM".equals(c.getPriority()) &&
                    "CEPC_MANUAL".equals(c.getFilingType())
            ));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Individual Workflow Actions (via CepcWorkflowService delegation)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/workflow/cepc/action/{complaintNumber} - Individual Actions")
    class WorkflowActions {

        @BeforeEach
        void setUpMock() {
            // All CEPC actions are delegated to CepcWorkflowService
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);
        }

        @Test
        @DisplayName("ACCEPT - should delegate to service and return in_progress")
        void acceptShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "ACCEPT");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("ACCEPT"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "do-officer-1", "remarks", "Accepted");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"))
                    .andExpect(jsonPath("$.data.action").value("ACCEPT"));

            verify(cepcWorkflowService).performAction(eq("CMP-20260706-123456"), eq("ACCEPT"), any());
        }

        @Test
        @DisplayName("REQUEST_INFO - should delegate and return info_requested")
        void requestInfoShouldSetInfoRequested() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "REQUEST_INFO");
            result.put("newStatus", "info_requested");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("REQUEST_INFO"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "REQUEST_INFO", "actor", "do-officer-1",
                    "remarks", "Need account statement");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("info_requested"));
        }

        @Test
        @DisplayName("INFO_RECEIVED - should delegate and return in_progress")
        void infoReceivedShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "INFO_RECEIVED");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("INFO_RECEIVED"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "INFO_RECEIVED", "actor", "do-officer-1", "remarks", "Received"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("FORWARD_DEPT - should delegate and return forwarded")
        void forwardDeptShouldSetForwarded() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_DEPT");
            result.put("newStatus", "forwarded");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_DEPT"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_DEPT", "actor", "do-officer-1", "remarks", "Forwarding"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("forwarded"));
        }

        @Test
        @DisplayName("COMMENTS_RECEIVED - should delegate and return in_progress")
        void commentsReceivedShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "COMMENTS_RECEIVED");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("COMMENTS_RECEIVED"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "COMMENTS_RECEIVED", "actor", "do-officer-1", "remarks", "Comments in"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("SCHEDULE_MEETING - should delegate and keep status unchanged")
        void scheduleMeetingShouldOnlyUpdateStage() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "SCHEDULE_MEETING");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SCHEDULE_MEETING"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "SCHEDULE_MEETING", "actor", "do-officer-1", "remarks", "Meeting on Friday"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("SUBMIT_FOR_REVIEW - should delegate and return reviewer_review")
        void submitForReviewShouldTransitionToReviewer() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "SUBMIT_FOR_REVIEW");
            result.put("newStatus", "reviewer_review");
            result.put("assignedRole", "CEPC_REVIEWER");
            result.put("assignedOfficer", "reviewer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SUBMIT_FOR_REVIEW"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "SUBMIT_FOR_REVIEW", "actor", "do-officer-1", "remarks", "Ready"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("reviewer_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_REVIEWER"));
        }

        @Test
        @DisplayName("APPROVE_REVIEW - should delegate and return incharge_review")
        void approveReviewShouldTransitionToIncharge() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "APPROVE_REVIEW");
            result.put("newStatus", "incharge_review");
            result.put("assignedRole", "CEPC_INCHARGE");
            result.put("assignedOfficer", "incharge-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("APPROVE_REVIEW"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE_REVIEW", "actor", "reviewer-1", "remarks", "Approved"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("incharge_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_INCHARGE"));
        }

        @Test
        @DisplayName("SEND_BACK_DO - should delegate and return sent_back with CEPC_DO role")
        void sendBackDoShouldTransitionToDo() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "SEND_BACK_DO");
            result.put("newStatus", "sent_back");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SEND_BACK_DO"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "SEND_BACK_DO");
            request.put("actor", "reviewer-1");
            request.put("remarks", "Need more analysis");
            request.put("targetUser", "do-officer-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("sent_back"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_DO"));
        }

        @Test
        @DisplayName("SEND_BACK_REVIEWER - should delegate and return reviewer_review")
        void sendBackReviewerShouldTransitionToReviewer() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "SEND_BACK_REVIEWER");
            result.put("newStatus", "reviewer_review");
            result.put("assignedRole", "CEPC_REVIEWER");
            result.put("assignedOfficer", "reviewer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SEND_BACK_REVIEWER"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "SEND_BACK_REVIEWER");
            request.put("actor", "incharge-1");
            request.put("remarks", "Need corrections");
            request.put("targetUser", "reviewer-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("reviewer_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_REVIEWER"));
        }

        @Test
        @DisplayName("SEND_BACK_INCHARGE - should delegate and return incharge_review")
        void sendBackInchargeShouldTransitionToIncharge() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "SEND_BACK_INCHARGE");
            result.put("newStatus", "incharge_review");
            result.put("assignedRole", "CEPC_INCHARGE");
            result.put("assignedOfficer", "incharge-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SEND_BACK_INCHARGE"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "SEND_BACK_INCHARGE");
            request.put("actor", "closing-auth-1");
            request.put("remarks", "Revisit needed");
            request.put("targetUser", "incharge-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("incharge_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_INCHARGE"));
        }

        @Test
        @DisplayName("APPROVE_CLOSURE - should delegate and return awaiting_closure")
        void approveClosureShouldTransitionToClosingAuthority() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "APPROVE_CLOSURE");
            result.put("newStatus", "awaiting_closure");
            result.put("assignedRole", "CEPC_CLOSING_AUTHORITY");
            result.put("assignedOfficer", "closing-auth-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("APPROVE_CLOSURE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE_CLOSURE", "actor", "incharge-1", "remarks", "Approved"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("awaiting_closure"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_CLOSING_AUTHORITY"));
        }

        @Test
        @DisplayName("CLOSE_COMPLAINT - should delegate and return closed")
        void closeComplaintShouldSetClosedStatus() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "CLOSE_COMPLAINT");
            result.put("newStatus", "closed");
            result.put("assignedRole", "CEPC_CLOSING_AUTHORITY");
            result.put("assignedOfficer", "closing-auth-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CLOSE_COMPLAINT"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CLOSE_COMPLAINT", "actor", "closing-auth-1", "remarks", "Closed"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("closed"));
        }

        @Test
        @DisplayName("REASSIGN - should delegate and return assigned with new officer")
        void reassignShouldUpdateOfficer() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "REASSIGN");
            result.put("newStatus", "assigned");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-2");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("REASSIGN"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "REASSIGN");
            request.put("actor", "supervisor-1");
            request.put("remarks", "Reassigning");
            request.put("targetUser", "do-officer-2");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("assigned"))
                    .andExpect(jsonPath("$.data.assignedOfficer").value("do-officer-2"));
        }

        @Test
        @DisplayName("FORWARD_TO_CONTACT - should delegate and return forwarded_to_contact")
        void forwardToContactShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_TO_CONTACT");
            result.put("newStatus", "forwarded_to_contact");
            result.put("assignedRole", "CEPC_CONTACT_PERSON");
            result.put("assignedOfficer", "contact-person-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_CONTACT"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "FORWARD_TO_CONTACT");
            request.put("actor", "do-officer-1");
            request.put("remarks", "Forward to entity contact");
            request.put("targetUser", "contact-person-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("forwarded_to_contact"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_CONTACT_PERSON"));
        }

        @Test
        @DisplayName("CONTACT_RESPONSE - should delegate and return in_progress with CEPC_DO")
        void contactResponseShouldReturnToDo() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "CONTACT_RESPONSE");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CONTACT_RESPONSE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CONTACT_RESPONSE", "actor", "contact-1", "remarks", "Response"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_DO"));
        }

        @Test
        @DisplayName("CONTACT_REASSIGN - should delegate and update assignedOfficer")
        void contactReassignShouldUpdateOfficer() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "CONTACT_REASSIGN");
            result.put("newStatus", "forwarded_to_contact");
            result.put("assignedRole", "CEPC_CONTACT_PERSON");
            result.put("assignedOfficer", "contact-person-2");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CONTACT_REASSIGN"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "CONTACT_REASSIGN");
            request.put("actor", "do-officer-1");
            request.put("remarks", "Reassign contact");
            request.put("targetUser", "contact-person-2");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assignedOfficer").value("contact-person-2"));
        }

        @Test
        @DisplayName("FORWARD_TO_INCHARGE - should delegate and return incharge_review")
        void forwardToInchargeShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_TO_INCHARGE");
            result.put("newStatus", "incharge_review");
            result.put("assignedRole", "CEPC_INCHARGE");
            result.put("assignedOfficer", "incharge-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_INCHARGE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_INCHARGE", "actor", "do-officer-1", "remarks", "Escalating"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("incharge_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_INCHARGE"));
        }

        @Test
        @DisplayName("FORWARD_TO_CLOSING_AUTHORITY - should delegate and return awaiting_closure")
        void forwardToClosingAuthorityShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_TO_CLOSING_AUTHORITY");
            result.put("newStatus", "awaiting_closure");
            result.put("assignedRole", "CEPC_CLOSING_AUTHORITY");
            result.put("assignedOfficer", "closing-auth-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_CLOSING_AUTHORITY"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_CLOSING_AUTHORITY", "actor", "reviewer-1", "remarks", "Ready"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("awaiting_closure"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_CLOSING_AUTHORITY"));
        }

        @Test
        @DisplayName("REOPEN - should delegate and return in_progress")
        void reopenShouldClearDatesAndResetStatus() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "REOPEN");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("REOPEN"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REOPEN", "actor", "supervisor-1", "remarks", "New evidence"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("ESCALATE - should delegate and return escalated")
        void escalateShouldSetEscalatedStatus() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "ESCALATE");
            result.put("newStatus", "escalated");
            result.put("assignedRole", "CEPC_DO");
            result.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("ESCALATE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ESCALATE", "actor", "do-officer-1", "remarks", "SLA breach"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("escalated"));
        }

        @Test
        @DisplayName("FORWARD_TO_OTHER_OFFICE - should delegate and return forwarded_external")
        void forwardToOtherOfficeShouldSetForwardedExternal() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_TO_OTHER_OFFICE");
            result.put("newStatus", "forwarded_external");
            result.put("assignedRole", "CEPC_CLOSING_AUTHORITY");
            result.put("assignedOfficer", "Mumbai Regional Office");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_OTHER_OFFICE"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "FORWARD_TO_OTHER_OFFICE");
            request.put("actor", "closing-auth-1");
            request.put("remarks", "Not in our jurisdiction");
            request.put("targetOffice", "Mumbai Regional Office");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("forwarded_external"));
        }

        @Test
        @DisplayName("FORWARD_TO_REGULATORY_BODY - should delegate and return forwarded_external")
        void forwardToRegulatoryBodyShouldSetForwardedExternal() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-123456");
            result.put("action", "FORWARD_TO_REGULATORY_BODY");
            result.put("newStatus", "forwarded_external");
            result.put("assignedRole", "CEPC_CLOSING_AUTHORITY");
            result.put("assignedOfficer", "SEBI");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_REGULATORY_BODY"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "FORWARD_TO_REGULATORY_BODY");
            request.put("actor", "closing-auth-1");
            request.put("remarks", "SEBI jurisdiction");
            request.put("targetBody", "SEBI");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("forwarded_external"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contact Person Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Contact Person Flow - Forward to Contact -> Response -> Back to DO")
    class ContactPersonFlow {

        @Test
        @DisplayName("should complete full contact person flow via service delegation")
        void shouldCompleteFullContactPersonFlow() throws Exception {
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);

            // Step 1: Forward to contact
            Map<String, Object> forwardResult = new LinkedHashMap<>();
            forwardResult.put("complaintNumber", "CMP-20260706-123456");
            forwardResult.put("action", "FORWARD_TO_CONTACT");
            forwardResult.put("newStatus", "forwarded_to_contact");
            forwardResult.put("assignedRole", "CEPC_CONTACT_PERSON");
            forwardResult.put("assignedOfficer", "contact-person-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("FORWARD_TO_CONTACT"), any()))
                    .thenReturn(forwardResult);

            Map<String, String> forwardReq = new HashMap<>();
            forwardReq.put("action", "FORWARD_TO_CONTACT");
            forwardReq.put("actor", "do-officer-1");
            forwardReq.put("remarks", "Please provide transaction details");
            forwardReq.put("targetUser", "contact-person-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(forwardReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("forwarded_to_contact"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_CONTACT_PERSON"));

            // Step 2: Contact responds
            Map<String, Object> responseResult = new LinkedHashMap<>();
            responseResult.put("complaintNumber", "CMP-20260706-123456");
            responseResult.put("action", "CONTACT_RESPONSE");
            responseResult.put("newStatus", "in_progress");
            responseResult.put("assignedRole", "CEPC_DO");
            responseResult.put("assignedOfficer", "do-officer-1");

            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CONTACT_RESPONSE"), any()))
                    .thenReturn(responseResult);

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CONTACT_RESPONSE", "actor", "contact-person-1",
                                            "remarks", "Details attached"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_DO"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Full Workflow Chain
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Workflow Chain - Accept through Closure")
    class FullWorkflowChain {

        @Test
        @DisplayName("should complete full chain: Accept -> Submit -> Approve -> Closure -> Close")
        void shouldCompleteFullWorkflowChain() throws Exception {
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);

            // Step 1: ACCEPT
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("ACCEPT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "ACCEPT",
                            "newStatus", "in_progress", "assignedRole", "CEPC_DO", "assignedOfficer", "do-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ACCEPT", "actor", "do-officer-1", "remarks", "Accepted"))))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));

            // Step 2: SUBMIT_FOR_REVIEW
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SUBMIT_FOR_REVIEW"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "SUBMIT_FOR_REVIEW",
                            "newStatus", "reviewer_review", "assignedRole", "CEPC_REVIEWER", "assignedOfficer", "reviewer-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "SUBMIT_FOR_REVIEW", "actor", "do-officer-1", "remarks", "Ready"))))
                    .andExpect(jsonPath("$.data.newStatus").value("reviewer_review"));

            // Step 3: APPROVE_REVIEW
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("APPROVE_REVIEW"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "APPROVE_REVIEW",
                            "newStatus", "incharge_review", "assignedRole", "CEPC_INCHARGE", "assignedOfficer", "incharge-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE_REVIEW", "actor", "reviewer-1", "remarks", "Approved"))))
                    .andExpect(jsonPath("$.data.newStatus").value("incharge_review"));

            // Step 4: APPROVE_CLOSURE
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("APPROVE_CLOSURE"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "APPROVE_CLOSURE",
                            "newStatus", "awaiting_closure", "assignedRole", "CEPC_CLOSING_AUTHORITY", "assignedOfficer", "ca-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE_CLOSURE", "actor", "incharge-1", "remarks", "Closure approved"))))
                    .andExpect(jsonPath("$.data.newStatus").value("awaiting_closure"));

            // Step 5: CLOSE_COMPLAINT
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CLOSE_COMPLAINT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "CLOSE_COMPLAINT",
                            "newStatus", "closed", "assignedRole", "CEPC_CLOSING_AUTHORITY", "assignedOfficer", "ca-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CLOSE_COMPLAINT", "actor", "ca-1", "remarks", "Closed"))))
                    .andExpect(jsonPath("$.data.newStatus").value("closed"));

            verify(cepcWorkflowService, times(5)).performAction(eq("CMP-20260706-123456"), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Send-Back Flows
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Send-Back Flows")
    class SendBackFlows {

        @BeforeEach
        void setUpMock() {
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);
        }

        @Test
        @DisplayName("Reviewer sends back to DO then DO resubmits")
        void reviewerSendsBackToDo() throws Exception {
            // Step 1: Send back
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SEND_BACK_DO"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "SEND_BACK_DO",
                            "newStatus", "sent_back", "assignedRole", "CEPC_DO", "assignedOfficer", "do-officer-1"));

            Map<String, String> sendBackReq = new HashMap<>();
            sendBackReq.put("action", "SEND_BACK_DO");
            sendBackReq.put("actor", "reviewer-1");
            sendBackReq.put("remarks", "Incomplete analysis");
            sendBackReq.put("targetUser", "do-officer-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sendBackReq)))
                    .andExpect(jsonPath("$.data.newStatus").value("sent_back"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_DO"));

            // Step 2: DO resubmits
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SUBMIT_FOR_REVIEW"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "SUBMIT_FOR_REVIEW",
                            "newStatus", "reviewer_review", "assignedRole", "CEPC_REVIEWER", "assignedOfficer", "reviewer-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "SUBMIT_FOR_REVIEW", "actor", "do-officer-1", "remarks", "Updated"))))
                    .andExpect(jsonPath("$.data.newStatus").value("reviewer_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_REVIEWER"));
        }

        @Test
        @DisplayName("Incharge sends back to reviewer")
        void inchargeSendsBackToReviewer() throws Exception {
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("SEND_BACK_REVIEWER"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "SEND_BACK_REVIEWER",
                            "newStatus", "reviewer_review", "assignedRole", "CEPC_REVIEWER", "assignedOfficer", "reviewer-1"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "SEND_BACK_REVIEWER");
            request.put("actor", "incharge-1");
            request.put("remarks", "Needs clarification");
            request.put("targetUser", "reviewer-1");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(jsonPath("$.data.newStatus").value("reviewer_review"))
                    .andExpect(jsonPath("$.data.assignedRole").value("CEPC_REVIEWER"))
                    .andExpect(jsonPath("$.data.assignedOfficer").value("reviewer-1"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Reopen Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reopen Flow")
    class ReopenFlow {

        @Test
        @DisplayName("should close and then reopen complaint")
        void shouldCloseAndThenReopen() throws Exception {
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);

            // Step 1: Close
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("CLOSE_COMPLAINT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "CLOSE_COMPLAINT",
                            "newStatus", "closed", "assignedRole", "CEPC_CLOSING_AUTHORITY", "assignedOfficer", "ca-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CLOSE_COMPLAINT", "actor", "ca-1", "remarks", "Closed"))))
                    .andExpect(jsonPath("$.data.newStatus").value("closed"));

            // Step 2: Reopen
            when(cepcWorkflowService.performAction(eq("CMP-20260706-123456"), eq("REOPEN"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-123456", "action", "REOPEN",
                            "newStatus", "in_progress", "assignedRole", "CEPC_DO", "assignedOfficer", "do-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REOPEN", "actor", "supervisor-1", "remarks", "New evidence"))))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error Cases
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error Cases")
    class ErrorCases {

        @Test
        @DisplayName("should return error when complaint not found (thrown by service)")
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(cepcWorkflowService.isCepcAction(any())).thenReturn(true);
            when(cepcWorkflowService.performAction(eq("NON-EXISTENT"), eq("ACCEPT"), any()))
                    .thenThrow(new IllegalArgumentException("Complaint not found: NON-EXISTENT"));

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "do-officer-1", "remarks", "");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/NON-EXISTENT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Complaint not found")));
        }

        @Test
        @DisplayName("should return error for unknown action (thrown by service)")
        void shouldReturnErrorForUnknownAction() throws Exception {
            when(cepcWorkflowService.isCepcAction("INVALID_ACTION")).thenReturn(false);
            // When action is not CEPC, controller falls through to inline handling
            // which expects to find the complaint
            when(complaintRepository.findByComplaintNumber("CMP-20260706-123456"))
                    .thenReturn(Optional.of(sampleComplaint));

            Map<String, String> request = Map.of("action", "INVALID_ACTION", "actor", "officer-1", "remarks", "");

            mockMvc.perform(post("/api/v1/workflow/cepc/action/CMP-20260706-123456")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Unknown action")));
        }
    }
}
