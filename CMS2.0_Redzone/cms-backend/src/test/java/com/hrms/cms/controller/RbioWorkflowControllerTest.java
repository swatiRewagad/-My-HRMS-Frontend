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
class RbioWorkflowControllerTest {

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
                .complaintNumber("CMP-20260706-789012")
                .complainantName("Test Complainant")
                .complainantEmail("citizen@example.com")
                .complainantPhone("9876543210")
                .subject("RBIO Test Complaint")
                .description("Complaint against bank for service deficiency")
                .status("assigned")
                .priority("HIGH")
                .department("RBIO")
                .assignedRole("RBIO_OFFICER")
                .assignedOfficer("rbio-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task Retrieval
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/workflow/rbio/tasks")
    class GetRbioTasks {

        @Test
        @DisplayName("should return tasks filtered by role")
        void shouldReturnTasksByRole() throws Exception {
            when(complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    eq("RBIO"), eq("RBIO_OFFICER"), any()))
                    .thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/v1/workflow/rbio/tasks")
                            .param("role", "RBIO_OFFICER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].complaintNumber").value("CMP-20260706-789012"))
                    .andExpect(jsonPath("$.data[0].assignedRole").value("RBIO_OFFICER"));
        }

        @Test
        @DisplayName("should return tasks filtered by officer")
        void shouldReturnTasksByOfficer() throws Exception {
            when(complaintRepository.findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    eq("RBIO"), eq("rbio-officer-1"), any()))
                    .thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/v1/workflow/rbio/tasks")
                            .param("officer", "rbio-officer-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].assignedOfficer").value("rbio-officer-1"));
        }

        @Test
        @DisplayName("should return empty list when no tasks match")
        void shouldReturnEmptyWhenNoTasks() throws Exception {
            when(complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(eq("RBIO"), any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/workflow/rbio/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // RBIO Assignment
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/workflow/rbio/assign/{complaintNumber}")
    class RbioAssignment {

        @Test
        @DisplayName("should assign complaint to specific officer")
        void shouldAssignToOfficer() throws Exception {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("officer", "rbio-officer-2");
            request.put("role", "RBIO_OFFICER");
            request.put("actor", "rbio-admin-1");

            mockMvc.perform(post("/api/v1/workflow/rbio/assign/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.assignedTo").value("rbio-officer-2"))
                    .andExpect(jsonPath("$.data.role").value("RBIO_OFFICER"));

            verify(complaintRepository).save(argThat(c ->
                    "rbio-officer-2".equals(c.getAssignedOfficer()) &&
                    "RBIO_OFFICER".equals(c.getAssignedRole()) &&
                    "assigned".equals(c.getStatus())
            ));
        }

        @Test
        @DisplayName("should assign complaint with specific role")
        void shouldAssignWithRole() throws Exception {
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("officer", "rbio-supervisor-1");
            request.put("role", "RBIO_SUPERVISOR");
            request.put("actor", "rbio-admin-1");

            mockMvc.perform(post("/api/v1/workflow/rbio/assign/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.assignedTo").value("rbio-supervisor-1"))
                    .andExpect(jsonPath("$.data.role").value("RBIO_SUPERVISOR"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Individual Workflow Actions (via RbioWorkflowService delegation)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/workflow/rbio/action/{complaintNumber} - Individual Actions")
    class WorkflowActions {

        @BeforeEach
        void setUpMock() {
            // All RBIO actions are delegated to RbioWorkflowService
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);
        }

        @Test
        @DisplayName("ACCEPT - should delegate to service and return in_progress")
        void acceptShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ACCEPT");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ACCEPT"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "Accepted");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"))
                    .andExpect(jsonPath("$.data.action").value("ACCEPT"));

            verify(rbioWorkflowService).performAction(eq("CMP-20260706-789012"), eq("ACCEPT"), any());
        }

        @Test
        @DisplayName("TAKE_ACTION - should delegate and return in_progress")
        void takeActionShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "TAKE_ACTION");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("TAKE_ACTION"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "TAKE_ACTION", "actor", "rbio-officer-1", "remarks", "Taking action"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("RESOLVE - should delegate and return resolved")
        void resolveShouldSetResolved() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "RESOLVE");
            result.put("newStatus", "resolved");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("RESOLVE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "RESOLVE", "actor", "rbio-officer-1", "remarks", "Resolved"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("resolved"));
        }

        @Test
        @DisplayName("REJECT - should delegate and return rejected")
        void rejectShouldSetRejected() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "REJECT");
            result.put("newStatus", "rejected");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("REJECT"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REJECT", "actor", "rbio-officer-1", "remarks", "Not maintainable"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("rejected"));
        }

        @Test
        @DisplayName("ESCALATE - should delegate and return escalated with RBIO_SUPERVISOR")
        void escalateShouldSetEscalated() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ESCALATE");
            result.put("newStatus", "escalated");
            result.put("assignedRole", "RBIO_SUPERVISOR");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ESCALATE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ESCALATE", "actor", "rbio-officer-1", "remarks", "Complex case"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("escalated"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_SUPERVISOR"));
        }

        @Test
        @DisplayName("REQUEST_INFO - should delegate and return info_requested")
        void requestInfoShouldSetInfoRequested() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "REQUEST_INFO");
            result.put("newStatus", "info_requested");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("REQUEST_INFO"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REQUEST_INFO", "actor", "rbio-officer-1", "remarks", "Need bank statement"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("info_requested"));
        }

        @Test
        @DisplayName("SCHEDULE_MEETING - should delegate and keep status unchanged")
        void scheduleMeetingShouldOnlyUpdateStage() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "SCHEDULE_MEETING");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("SCHEDULE_MEETING"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "SCHEDULE_MEETING", "actor", "rbio-officer-1", "remarks", "Friday"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("FORWARD_TO_CONCILIATION - should delegate and return conciliation with RBIO_CONCILIATOR")
        void forwardToConciliationShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "FORWARD_TO_CONCILIATION");
            result.put("newStatus", "conciliation");
            result.put("assignedRole", "RBIO_CONCILIATOR");
            result.put("assignedOfficer", "rbio-conciliator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("FORWARD_TO_CONCILIATION"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_CONCILIATION", "actor", "rbio-officer-1", "remarks", "Needs conciliation"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("conciliation"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_CONCILIATOR"));
        }

        @Test
        @DisplayName("ISSUE_ADVISORY - should delegate and return advisory_issued")
        void issueAdvisoryShouldSetAdvisoryIssued() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ISSUE_ADVISORY");
            result.put("newStatus", "advisory_issued");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ISSUE_ADVISORY"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ISSUE_ADVISORY", "actor", "rbio-officer-1", "remarks", "Advisory"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("advisory_issued"));
        }

        @Test
        @DisplayName("APPROVE - should delegate and return approved")
        void approveShouldSetApproved() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "APPROVE");
            result.put("newStatus", "approved");
            result.put("assignedRole", "RBIO_CONCILIATOR");
            result.put("assignedOfficer", "rbio-supervisor-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("APPROVE"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE", "actor", "rbio-supervisor-1", "remarks", "Approved"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("approved"));
        }

        @Test
        @DisplayName("RETURN_TO_OFFICER - should delegate and return with RBIO_OFFICER role")
        void returnToOfficerShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "RETURN_TO_OFFICER");
            result.put("newStatus", "returned");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("RETURN_TO_OFFICER"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "RETURN_TO_OFFICER", "actor", "rbio-supervisor-1", "remarks", "Need details"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("returned"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_OFFICER"));
        }

        @Test
        @DisplayName("FORWARD_TO_ADJUDICATION - should delegate and return adjudication with RBIO_ADJUDICATOR")
        void forwardToAdjudicationShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "FORWARD_TO_ADJUDICATION");
            result.put("newStatus", "adjudication");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("FORWARD_TO_ADJUDICATION"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_ADJUDICATION", "actor", "rbio-supervisor-1", "remarks", "To adjudication"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("adjudication"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_ADJUDICATOR"));
        }

        @Test
        @DisplayName("REASSIGN - should delegate and update assignedOfficer")
        void reassignShouldUpdateOfficer() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "REASSIGN");
            result.put("newStatus", "assigned");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-3");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("REASSIGN"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "REASSIGN");
            request.put("actor", "rbio-admin-1");
            request.put("remarks", "Reassigning");
            request.put("targetUser", "rbio-officer-3");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("assigned"))
                    .andExpect(jsonPath("$.data.assignedOfficer").value("rbio-officer-3"));
        }

        @Test
        @DisplayName("CONCILIATION_SUCCESS - should delegate and return conciliated")
        void conciliationSuccessShouldSetConciliated() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "CONCILIATION_SUCCESS");
            result.put("newStatus", "conciliated");
            result.put("assignedRole", "RBIO_CONCILIATOR");
            result.put("assignedOfficer", "rbio-conciliator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("CONCILIATION_SUCCESS"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CONCILIATION_SUCCESS", "actor", "rbio-conciliator-1", "remarks", "Settlement"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("conciliated"));
        }

        @Test
        @DisplayName("CONCILIATION_FAILED - should delegate and escalate to RBIO_ADJUDICATOR")
        void conciliationFailedShouldEscalate() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "CONCILIATION_FAILED");
            result.put("newStatus", "escalated");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("CONCILIATION_FAILED"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CONCILIATION_FAILED", "actor", "rbio-conciliator-1", "remarks", "No agreement"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("escalated"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_ADJUDICATOR"));
        }

        @Test
        @DisplayName("ESCALATE_TO_ADJUDICATION - should delegate and return adjudication")
        void escalateToAdjudicationShouldTransition() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ESCALATE_TO_ADJUDICATION");
            result.put("newStatus", "adjudication");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ESCALATE_TO_ADJUDICATION"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ESCALATE_TO_ADJUDICATION", "actor", "rbio-conciliator-1", "remarks", "Escalating"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("adjudication"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_ADJUDICATOR"));
        }

        @Test
        @DisplayName("ADJUDICATION_AWARD - should delegate and return adjudicated")
        void adjudicationAwardShouldSetAdjudicated() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ADJUDICATION_AWARD");
            result.put("newStatus", "adjudicated");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_AWARD"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "ADJUDICATION_AWARD");
            request.put("actor", "rbio-adjudicator-1");
            request.put("remarks", "Award issued");
            request.put("awardAmount", "500000");
            request.put("compensationType", "CONSEQUENTIAL_LOSS");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("adjudicated"));
        }

        @Test
        @DisplayName("ADJUDICATION_REJECT - should delegate and return rejected")
        void adjudicationRejectShouldSetRejected() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ADJUDICATION_REJECT");
            result.put("newStatus", "rejected");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_REJECT"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ADJUDICATION_REJECT", "actor", "rbio-adjudicator-1", "remarks", "Not substantiated"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("rejected"));
        }

        @Test
        @DisplayName("ISSUE_NOTICE_13_1 - should delegate and return success")
        void issueNotice131ShouldDelegate() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "ISSUE_NOTICE_13_1");
            result.put("newStatus", "adjudication");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ISSUE_NOTICE_13_1"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ISSUE_NOTICE_13_1", "actor", "rbio-adjudicator-1", "remarks", "Notice S.13(1)"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("IMPLEAD_PARTY - should delegate and return success")
        void impleadPartyShouldDelegate() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "IMPLEAD_PARTY");
            result.put("newStatus", "adjudication");
            result.put("assignedRole", "RBIO_ADJUDICATOR");
            result.put("assignedOfficer", "rbio-adjudicator-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("IMPLEAD_PARTY"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "IMPLEAD_PARTY");
            request.put("actor", "rbio-adjudicator-1");
            request.put("remarks", "Adding insurance");
            request.put("partyName", "ABC Insurance Ltd");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("REOPEN - should delegate and return in_progress")
        void reopenShouldSetInProgress() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "REOPEN");
            result.put("newStatus", "in_progress");
            result.put("assignedRole", "RBIO_OFFICER");
            result.put("assignedOfficer", "rbio-officer-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("REOPEN"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REOPEN", "actor", "rbio-admin-1", "remarks", "New evidence"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }

        @Test
        @DisplayName("CLOSE_COMPLAINT - should delegate and return closed")
        void closeComplaintShouldSetClosed() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("complaintNumber", "CMP-20260706-789012");
            result.put("action", "CLOSE_COMPLAINT");
            result.put("newStatus", "closed");
            result.put("assignedRole", "RBIO_ADMIN");
            result.put("assignedOfficer", "rbio-admin-1");

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("CLOSE_COMPLAINT"), any()))
                    .thenReturn(result);

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CLOSE_COMPLAINT", "actor", "rbio-admin-1", "remarks", "Final closure"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("closed"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Officer Flow: Accept -> Resolve
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Officer Flow: Accept -> Resolve")
    class OfficerFlow {

        @Test
        @DisplayName("should complete officer direct resolution flow via service delegation")
        void shouldCompleteOfficerFlow() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            // Step 1: ACCEPT
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ACCEPT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ACCEPT",
                            "newStatus", "in_progress", "assignedRole", "RBIO_OFFICER", "assignedOfficer", "rbio-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "Accepted"))))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));

            // Step 2: RESOLVE
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("RESOLVE"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "RESOLVE",
                            "newStatus", "resolved", "assignedRole", "RBIO_OFFICER", "assignedOfficer", "rbio-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "RESOLVE", "actor", "rbio-officer-1", "remarks", "Resolved directly"))))
                    .andExpect(jsonPath("$.data.newStatus").value("resolved"));

            verify(rbioWorkflowService, times(2)).performAction(eq("CMP-20260706-789012"), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Escalation Flow: Accept -> Escalate -> Approve -> Adjudication -> Award
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Escalation Flow")
    class EscalationFlow {

        @Test
        @DisplayName("should complete full escalation through adjudication award")
        void shouldCompleteEscalationFlow() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            // Step 1: ACCEPT
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ACCEPT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ACCEPT",
                            "newStatus", "in_progress", "assignedRole", "RBIO_OFFICER", "assignedOfficer", "rbio-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "Accepted"))))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));

            // Step 2: ESCALATE
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ESCALATE"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ESCALATE",
                            "newStatus", "escalated", "assignedRole", "RBIO_SUPERVISOR", "assignedOfficer", "rbio-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ESCALATE", "actor", "rbio-officer-1", "remarks", "Complex"))))
                    .andExpect(jsonPath("$.data.newStatus").value("escalated"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_SUPERVISOR"));

            // Step 3: APPROVE by Supervisor
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("APPROVE"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "APPROVE",
                            "newStatus", "approved", "assignedRole", "RBIO_CONCILIATOR", "assignedOfficer", "rbio-supervisor-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "APPROVE", "actor", "rbio-supervisor-1", "remarks", "Approved"))))
                    .andExpect(jsonPath("$.data.newStatus").value("approved"));

            // Step 4: FORWARD_TO_ADJUDICATION
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("FORWARD_TO_ADJUDICATION"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "FORWARD_TO_ADJUDICATION",
                            "newStatus", "adjudication", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_ADJUDICATION", "actor", "rbio-supervisor-1", "remarks", "To adjudication"))))
                    .andExpect(jsonPath("$.data.newStatus").value("adjudication"));

            // Step 5: ADJUDICATION_AWARD
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_AWARD"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ADJUDICATION_AWARD",
                            "newStatus", "adjudicated", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            Map<String, String> awardReq = new HashMap<>();
            awardReq.put("action", "ADJUDICATION_AWARD");
            awardReq.put("actor", "rbio-adj-1");
            awardReq.put("remarks", "Award of 5L");
            awardReq.put("awardAmount", "500000");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(awardReq)))
                    .andExpect(jsonPath("$.data.newStatus").value("adjudicated"));

            verify(rbioWorkflowService, times(5)).performAction(eq("CMP-20260706-789012"), any(), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Conciliation Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Conciliation Flow")
    class ConciliationFlow {

        @Test
        @DisplayName("should complete conciliation: forward -> success/failure paths")
        void shouldCompleteConciliationFlow() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            // Step 1: FORWARD_TO_CONCILIATION
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("FORWARD_TO_CONCILIATION"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "FORWARD_TO_CONCILIATION",
                            "newStatus", "conciliation", "assignedRole", "RBIO_CONCILIATOR", "assignedOfficer", "rbio-conciliator-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_CONCILIATION", "actor", "rbio-officer-1", "remarks", "Conciliation"))))
                    .andExpect(jsonPath("$.data.newStatus").value("conciliation"))
                    .andExpect(jsonPath("$.data.assignedRole").value("RBIO_CONCILIATOR"));

            // Step 2: CONCILIATION_SUCCESS
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("CONCILIATION_SUCCESS"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "CONCILIATION_SUCCESS",
                            "newStatus", "conciliated", "assignedRole", "RBIO_CONCILIATOR", "assignedOfficer", "rbio-conciliator-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CONCILIATION_SUCCESS", "actor", "rbio-conciliator-1", "remarks", "Agreement reached"))))
                    .andExpect(jsonPath("$.data.newStatus").value("conciliated"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Adjudication Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Adjudication Flow")
    class AdjudicationFlow {

        @Test
        @DisplayName("should complete adjudication with award (including amount)")
        void shouldCompleteAdjudicationWithAward() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            // Forward to adjudication
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("FORWARD_TO_ADJUDICATION"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "FORWARD_TO_ADJUDICATION",
                            "newStatus", "adjudication", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "FORWARD_TO_ADJUDICATION", "actor", "rbio-supervisor-1", "remarks", "Adjudication"))))
                    .andExpect(jsonPath("$.data.newStatus").value("adjudication"));

            // Award
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_AWARD"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ADJUDICATION_AWARD",
                            "newStatus", "adjudicated", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            Map<String, String> awardReq = new HashMap<>();
            awardReq.put("action", "ADJUDICATION_AWARD");
            awardReq.put("actor", "rbio-adj-1");
            awardReq.put("remarks", "Award granted");
            awardReq.put("awardAmount", "1500000");
            awardReq.put("compensationType", "CONSEQUENTIAL_LOSS");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(awardReq)))
                    .andExpect(jsonPath("$.data.newStatus").value("adjudicated"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Advisory Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Advisory Flow")
    class AdvisoryFlow {

        @Test
        @DisplayName("should issue advisory with ADVISORY closure cause")
        void shouldIssueAdvisory() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ISSUE_ADVISORY"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ISSUE_ADVISORY",
                            "newStatus", "advisory_issued", "assignedRole", "RBIO_OFFICER", "assignedOfficer", "rbio-officer-1"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "ISSUE_ADVISORY");
            request.put("actor", "rbio-officer-1");
            request.put("remarks", "Advisory to bank");
            request.put("advisoryText", "Bank advised to resolve within 15 days");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.newStatus").value("advisory_issued"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Notice 13(1) Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Notice 13(1) Flow")
    class Notice131Flow {

        @Test
        @DisplayName("should issue notice and update stage to NOTICE_13_1_ISSUED via service")
        void shouldIssueNotice() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ISSUE_NOTICE_13_1"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ISSUE_NOTICE_13_1",
                            "newStatus", "adjudication", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "ISSUE_NOTICE_13_1", "actor", "rbio-adj-1", "remarks", "Notice under S.13(1)"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.action").value("ISSUE_NOTICE_13_1"));

            verify(rbioWorkflowService).performAction(eq("CMP-20260706-789012"), eq("ISSUE_NOTICE_13_1"), any());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Implead Party Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Implead Party Flow")
    class ImpleadFlow {

        @Test
        @DisplayName("should add impleaded party via service delegation")
        void shouldAddImpleadedParty() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("IMPLEAD_PARTY"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "IMPLEAD_PARTY",
                            "newStatus", "adjudication", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "IMPLEAD_PARTY");
            request.put("actor", "rbio-adj-1");
            request.put("remarks", "Impleading insurer");
            request.put("partyName", "XYZ Insurance Co.");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.action").value("IMPLEAD_PARTY"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Reopen Flow
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Reopen Flow")
    class ReopenFlow {

        @Test
        @DisplayName("should close and then reopen complaint back to in_progress")
        void shouldCloseAndThenReopen() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            // Step 1: Close
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("CLOSE_COMPLAINT"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "CLOSE_COMPLAINT",
                            "newStatus", "closed", "assignedRole", "RBIO_ADMIN", "assignedOfficer", "rbio-admin-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "CLOSE_COMPLAINT", "actor", "rbio-admin-1", "remarks", "Closed"))))
                    .andExpect(jsonPath("$.data.newStatus").value("closed"));

            // Step 2: Reopen
            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("REOPEN"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "REOPEN",
                            "newStatus", "in_progress", "assignedRole", "RBIO_OFFICER", "assignedOfficer", "rbio-officer-1"));

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("action", "REOPEN", "actor", "rbio-admin-1", "remarks", "New evidence"))))
                    .andExpect(jsonPath("$.data.newStatus").value("in_progress"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Compensation Validation
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Compensation Validation")
    class CompensationValidation {

        @Test
        @DisplayName("should pass when award amount is within cap")
        void shouldPassWhenWithinCap() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_AWARD"), any()))
                    .thenReturn(Map.of("complaintNumber", "CMP-20260706-789012", "action", "ADJUDICATION_AWARD",
                            "newStatus", "adjudicated", "assignedRole", "RBIO_ADJUDICATOR", "assignedOfficer", "rbio-adj-1"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "ADJUDICATION_AWARD");
            request.put("actor", "rbio-adj-1");
            request.put("remarks", "Award of 2 Lakh");
            request.put("awardAmount", "200000");
            request.put("compensationType", "TIME_HARASSMENT");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.newStatus").value("adjudicated"));
        }

        @Test
        @DisplayName("should return error when award exceeds compensation cap")
        void shouldReturnErrorWhenExceedsCap() throws Exception {
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);

            when(rbioWorkflowService.performAction(eq("CMP-20260706-789012"), eq("ADJUDICATION_AWARD"), any()))
                    .thenThrow(new IllegalArgumentException(
                            "Award amount Rs 5000000 exceeds the maximum permitted cap of Rs 3000000 for compensation type 'CONSEQUENTIAL_LOSS'"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "ADJUDICATION_AWARD");
            request.put("actor", "rbio-adj-1");
            request.put("remarks", "Excessive award");
            request.put("awardAmount", "5000000");
            request.put("compensationType", "CONSEQUENTIAL_LOSS");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("exceeds the maximum permitted cap")));
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
            when(rbioWorkflowService.isRbioAction(any())).thenReturn(true);
            when(rbioWorkflowService.performAction(eq("NON-EXISTENT"), eq("ACCEPT"), any()))
                    .thenThrow(new IllegalArgumentException("Complaint not found: NON-EXISTENT"));

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "rbio-officer-1", "remarks", "");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/NON-EXISTENT")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Complaint not found")));
        }

        @Test
        @DisplayName("should return error for unknown action (falls through to inline handling)")
        void shouldReturnErrorForUnknownAction() throws Exception {
            when(rbioWorkflowService.isRbioAction("INVALID_ACTION")).thenReturn(false);
            // When action is not RBIO, controller falls through to inline handling
            when(complaintRepository.findByComplaintNumber("CMP-20260706-789012"))
                    .thenReturn(Optional.of(sampleComplaint));

            Map<String, String> request = Map.of("action", "INVALID_ACTION", "actor", "rbio-officer-1", "remarks", "");

            mockMvc.perform(post("/api/v1/workflow/rbio/action/CMP-20260706-789012")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Unknown action")));
        }
    }
}
