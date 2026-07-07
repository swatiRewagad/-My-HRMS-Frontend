package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealTimeline;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.service.AppealEligibilityService;
import com.hrms.cms.service.AppealWorkflowService;
import com.hrms.cms.service.EncryptionKeyService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the Appellate Authority (AA) Appeal Controller.
 * Endpoints: /api/v1/appeals/
 */
@WebMvcTest(AppealController.class)
@AutoConfigureMockMvc(addFilters = false)
class AppealControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AppealRepository appealRepository;
    @MockBean private AppealWorkflowService appealWorkflowService;
    @MockBean private AppealEligibilityService appealEligibilityService;
    @MockBean private EncryptionKeyService encryptionKeyService;

    private Appeal sampleAppeal;

    @BeforeEach
    void setUp() {
        sampleAppeal = Appeal.builder()
                .id(1L)
                .appealNumber("APL-20260706-ABC123")
                .originalComplaintNumber("CMP-20260601-100001")
                .classificationType("APPEAL")
                .appealGround("Dissatisfied with closure")
                .reliefSought("Full refund")
                .appellantName("Test Appellant")
                .appellantEmail("appellant@example.com")
                .appellantPhone("9876543210")
                .status("filed")
                .priority("high")
                .assignedRole("AA_REGISTRAR")
                .assignedOfficer("registrar-1")
                .workflowStage("FILED")
                .filedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // POST /file — file appeal
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/appeals/file")
    class FileAppeal {

        @Test
        @DisplayName("should file appeal successfully and return appeal data")
        void shouldFileAppealSuccessfully() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("classificationType", "APPEAL");
            result.put("status", "filed");
            result.put("assignedRole", "AA_REGISTRAR");
            result.put("assignedOfficer", "registrar-1");
            result.put("filedAt", LocalDateTime.now().toString());

            when(appealWorkflowService.fileAppeal(any())).thenReturn(result);

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Test Appellant");
            request.put("appealGround", "Dissatisfied with closure");
            request.put("reliefSought", "Full refund");

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Appeal filed successfully"))
                    .andExpect(jsonPath("$.data.appealNumber").value("APL-20260706-ABC123"))
                    .andExpect(jsonPath("$.data.classificationType").value("APPEAL"))
                    .andExpect(jsonPath("$.data.status").value("filed"));
        }

        @Test
        @DisplayName("should file REPRESENTATION type appeal successfully")
        void shouldFileRepresentationSuccessfully() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-DEF456");
            result.put("classificationType", "REPRESENTATION");
            result.put("status", "filed");
            result.put("assignedRole", "AA_REGISTRAR");
            result.put("assignedOfficer", "registrar-1");
            result.put("filedAt", LocalDateTime.now().toString());

            when(appealWorkflowService.fileAppeal(any())).thenReturn(result);

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260605-200001");
            request.put("classificationType", "REPRESENTATION");
            request.put("appellantName", "Advisory Appellant");
            request.put("appealGround", "Advisory outcome not satisfactory");

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.classificationType").value("REPRESENTATION"));
        }

        @Test
        @DisplayName("should return error when service throws IllegalArgumentException")
        void shouldReturnErrorOnIllegalArgument() throws Exception {
            when(appealWorkflowService.fileAppeal(any()))
                    .thenThrow(new IllegalArgumentException("originalComplaintNumber is required"));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("originalComplaintNumber is required")));
        }

        @Test
        @DisplayName("should return error when appeal not eligible")
        void shouldReturnErrorWhenNotEligible() throws Exception {
            when(appealWorkflowService.fileAppeal(any()))
                    .thenThrow(new IllegalArgumentException("Appeal not eligible: Filing deadline exceeded"));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-OLD");
            request.put("classificationType", "APPEAL");
            request.put("appellantName", "Late Appellant");
            request.put("appealGround", "Late grounds");

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not eligible")));
        }

        @Test
        @DisplayName("should return error for invalid classificationType")
        void shouldReturnErrorForInvalidClassificationType() throws Exception {
            when(appealWorkflowService.fileAppeal(any()))
                    .thenThrow(new IllegalArgumentException("classificationType must be APPEAL or REPRESENTATION"));

            Map<String, String> request = new LinkedHashMap<>();
            request.put("originalComplaintNumber", "CMP-20260601-100001");
            request.put("classificationType", "INVALID_TYPE");
            request.put("appellantName", "Test");
            request.put("appealGround", "Grounds");

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("classificationType must be")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /eligibility/{complaintNumber}
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/eligibility/{complaintNumber}")
    class CheckEligibility {

        @Test
        @DisplayName("should return eligible result with suggestedType")
        void shouldReturnEligibleResult() throws Exception {
            Map<String, Object> eligibility = new LinkedHashMap<>();
            eligibility.put("eligible", true);
            eligibility.put("reason", "Complaint is eligible for appeal/representation.");
            eligibility.put("suggestedType", "APPEAL");
            eligibility.put("originalStatus", "closed");
            eligibility.put("closureDate", LocalDateTime.now().minusDays(10).toString());

            when(appealEligibilityService.checkEligibility("CMP-20260625-001")).thenReturn(eligibility);

            mockMvc.perform(get("/api/v1/appeals/eligibility/CMP-20260625-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Eligibility check complete"))
                    .andExpect(jsonPath("$.data.eligible").value(true))
                    .andExpect(jsonPath("$.data.suggestedType").value("APPEAL"));
        }

        @Test
        @DisplayName("should return ineligible when complaint not found")
        void shouldReturnIneligibleWhenNotFound() throws Exception {
            Map<String, Object> eligibility = new LinkedHashMap<>();
            eligibility.put("eligible", false);
            eligibility.put("reason", "Complaint not found: CMP-NONEXIST");

            when(appealEligibilityService.checkEligibility("CMP-NONEXIST")).thenReturn(eligibility);

            mockMvc.perform(get("/api/v1/appeals/eligibility/CMP-NONEXIST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.eligible").value(false))
                    .andExpect(jsonPath("$.data.reason", containsString("not found")));
        }

        @Test
        @DisplayName("should return ineligible when outside 30-day window")
        void shouldReturnIneligibleOutside30Days() throws Exception {
            Map<String, Object> eligibility = new LinkedHashMap<>();
            eligibility.put("eligible", false);
            eligibility.put("reason", "Filing deadline exceeded. Appeals must be filed within 30 days of closure.");

            when(appealEligibilityService.checkEligibility("CMP-OLD")).thenReturn(eligibility);

            mockMvc.perform(get("/api/v1/appeals/eligibility/CMP-OLD"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.eligible").value(false))
                    .andExpect(jsonPath("$.data.reason", containsString("30 days")));
        }

        @Test
        @DisplayName("should suggest REPRESENTATION for advisory complaints")
        void shouldSuggestRepresentationForAdvisory() throws Exception {
            Map<String, Object> eligibility = new LinkedHashMap<>();
            eligibility.put("eligible", true);
            eligibility.put("reason", "Complaint is eligible for appeal/representation.");
            eligibility.put("suggestedType", "REPRESENTATION");
            eligibility.put("originalStatus", "closed");

            when(appealEligibilityService.checkEligibility("CMP-ADVISORY")).thenReturn(eligibility);

            mockMvc.perform(get("/api/v1/appeals/eligibility/CMP-ADVISORY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.suggestedType").value("REPRESENTATION"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /{appealNumber}/status — public status
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/{appealNumber}/status")
    class GetStatus {

        @Test
        @DisplayName("should return appeal status info")
        void shouldReturnCorrectStatus() throws Exception {
            when(appealRepository.findByAppealNumber("APL-20260706-ABC123"))
                    .thenReturn(Optional.of(sampleAppeal));

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Appeal status retrieved"))
                    .andExpect(jsonPath("$.data.appealNumber").value("APL-20260706-ABC123"))
                    .andExpect(jsonPath("$.data.classificationType").value("APPEAL"))
                    .andExpect(jsonPath("$.data.status").value("filed"));
        }

        @Test
        @DisplayName("should return not found when appeal does not exist")
        void shouldReturnNotFoundForNonExistent() throws Exception {
            when(appealRepository.findByAppealNumber("APL-NONEXIST"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/appeals/APL-NONEXIST/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }

        @Test
        @DisplayName("should include hearingDate and orderOutcome when present")
        void shouldIncludeHearingDateAndOrderOutcome() throws Exception {
            sampleAppeal.setStatus("order_passed");
            sampleAppeal.setHearingDate(LocalDateTime.of(2026, 7, 15, 10, 0));
            sampleAppeal.setOrderOutcome("UPHELD");
            sampleAppeal.setOrderDate(LocalDateTime.of(2026, 7, 20, 14, 0));

            when(appealRepository.findByAppealNumber("APL-20260706-ABC123"))
                    .thenReturn(Optional.of(sampleAppeal));

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("order_passed"))
                    .andExpect(jsonPath("$.data.hearingDate").exists())
                    .andExpect(jsonPath("$.data.orderOutcome").value("UPHELD"))
                    .andExpect(jsonPath("$.data.orderDate").exists());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /tasks — staff task list
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/tasks")
    class GetTasks {

        @Test
        @DisplayName("should return tasks filtered by role")
        void shouldReturnTasksByRole() throws Exception {
            when(appealRepository.findByAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    eq("AA_REGISTRAR"), any()))
                    .thenReturn(List.of(sampleAppeal));

            mockMvc.perform(get("/api/v1/appeals/tasks")
                            .param("role", "AA_REGISTRAR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].assignedRole").value("AA_REGISTRAR"));
        }

        @Test
        @DisplayName("should return tasks filtered by officer")
        void shouldReturnTasksByOfficer() throws Exception {
            when(appealRepository.findByAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    eq("registrar-1"), any()))
                    .thenReturn(List.of(sampleAppeal));

            mockMvc.perform(get("/api/v1/appeals/tasks")
                            .param("officer", "registrar-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].assignedOfficer").value("registrar-1"));
        }

        @Test
        @DisplayName("should return all open tasks when no filter")
        void shouldReturnAllOpenTasks() throws Exception {
            when(appealRepository.findByStatusNotInOrderByCreatedAtDesc(any()))
                    .thenReturn(List.of(sampleAppeal));

            mockMvc.perform(get("/api/v1/appeals/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("should return empty list when no tasks match")
        void shouldReturnEmptyWhenNoTasks() throws Exception {
            when(appealRepository.findByAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    eq("AA_BENCH_OFFICER"), any()))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/appeals/tasks")
                            .param("role", "AA_BENCH_OFFICER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /{appealNumber} — full detail (staff)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/{appealNumber}")
    class GetFullDetail {

        @Test
        @DisplayName("should return full appeal detail with timeline")
        void shouldReturnFullAppealDetail() throws Exception {
            when(appealRepository.findByAppealNumber("APL-20260706-ABC123"))
                    .thenReturn(Optional.of(sampleAppeal));

            AppealTimeline timelineEntry = AppealTimeline.builder()
                    .appealNumber("APL-20260706-ABC123")
                    .action("FILED")
                    .performedBy("SYSTEM")
                    .remarks("Appeal filed")
                    .fromStatus(null)
                    .toStatus("filed")
                    .performedAt(LocalDateTime.now())
                    .build();

            when(appealWorkflowService.getTimeline("APL-20260706-ABC123"))
                    .thenReturn(List.of(timelineEntry));

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Appeal detail retrieved"))
                    .andExpect(jsonPath("$.data.appealNumber").value("APL-20260706-ABC123"))
                    .andExpect(jsonPath("$.data.classificationType").value("APPEAL"))
                    .andExpect(jsonPath("$.data.appellantName").value("Test Appellant"))
                    .andExpect(jsonPath("$.data.timeline", hasSize(1)))
                    .andExpect(jsonPath("$.data.timeline[0].action").value("FILED"));
        }

        @Test
        @DisplayName("should return not found for non-existent appeal")
        void shouldReturnNotFoundForNonExistent() throws Exception {
            when(appealRepository.findByAppealNumber("APL-NONEXIST"))
                    .thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/appeals/APL-NONEXIST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }

        @Test
        @DisplayName("should include all detail fields")
        void shouldIncludeAllDetailFields() throws Exception {
            sampleAppeal.setHearingDate(LocalDateTime.of(2026, 7, 15, 10, 0));
            sampleAppeal.setHearingVenue("Room A");
            sampleAppeal.setOrderDate(LocalDateTime.of(2026, 7, 20, 14, 0));
            sampleAppeal.setOrderSummary("Order summary text");
            sampleAppeal.setOrderOutcome("MODIFIED");
            sampleAppeal.setAwardModifiedAmount(new BigDecimal("75000.00"));

            when(appealRepository.findByAppealNumber("APL-20260706-ABC123"))
                    .thenReturn(Optional.of(sampleAppeal));
            when(appealWorkflowService.getTimeline("APL-20260706-ABC123"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.hearingVenue").value("Room A"))
                    .andExpect(jsonPath("$.data.orderSummary").value("Order summary text"))
                    .andExpect(jsonPath("$.data.orderOutcome").value("MODIFIED"))
                    .andExpect(jsonPath("$.data.awardModifiedAmount").value("75000.00"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // POST /{appealNumber}/action — workflow actions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/appeals/{appealNumber}/action")
    class WorkflowActions {

        @Test
        @DisplayName("ACCEPT action transitions status to under_review")
        void acceptShouldTransitionToUnderReview() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "ACCEPT");
            result.put("newStatus", "under_review");
            result.put("assignedRole", "AA_REGISTRAR");
            result.put("assignedOfficer", "registrar-1");
            result.put("workflowStage", "UNDER_REVIEW");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("ACCEPT"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "registrar-1", "actorRole", "AA_REGISTRAR", "remarks", "Accepted");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message", containsString("ACCEPT")))
                    .andExpect(jsonPath("$.data.newStatus").value("under_review"));
        }

        @Test
        @DisplayName("REJECT action requires remarks")
        void rejectShouldSetRejectedStatus() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "REJECT");
            result.put("newStatus", "rejected");
            result.put("assignedRole", "AA_REGISTRAR");
            result.put("assignedOfficer", "registrar-1");
            result.put("workflowStage", "REJECTED");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("REJECT"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "REJECT");
            request.put("actor", "registrar-1");
            request.put("actorRole", "AA_REGISTRAR");
            request.put("remarks", "Time-barred");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("rejected"));
        }

        @Test
        @DisplayName("ASSIGN_TO_BENCH assigns to bench officer")
        void assignToBenchShouldAssign() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "ASSIGN_TO_BENCH");
            result.put("newStatus", "under_review");
            result.put("assignedRole", "AA_BENCH_OFFICER");
            result.put("assignedOfficer", "bench-officer-1");
            result.put("workflowStage", "ASSIGNED_TO_BENCH");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("ASSIGN_TO_BENCH"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "ASSIGN_TO_BENCH");
            request.put("actor", "registrar-1");
            request.put("actorRole", "AA_REGISTRAR");
            request.put("remarks", "Assigning to Bench");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assignedRole").value("AA_BENCH_OFFICER"))
                    .andExpect(jsonPath("$.data.workflowStage").value("ASSIGNED_TO_BENCH"));
        }

        @Test
        @DisplayName("SCHEDULE_HEARING sets date and venue")
        void scheduleHearingShouldSetDateAndVenue() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "SCHEDULE_HEARING");
            result.put("newStatus", "hearing_scheduled");
            result.put("assignedRole", "AA_BENCH_OFFICER");
            result.put("assignedOfficer", "bench-officer-1");
            result.put("workflowStage", "HEARING_SCHEDULED");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("SCHEDULE_HEARING"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "SCHEDULE_HEARING");
            request.put("actor", "bench-officer-1");
            request.put("actorRole", "AA_BENCH_OFFICER");
            request.put("hearingDate", "2026-07-20T10:00:00");
            request.put("hearingVenue", "Conference Room B");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("hearing_scheduled"))
                    .andExpect(jsonPath("$.data.workflowStage").value("HEARING_SCHEDULED"));
        }

        @Test
        @DisplayName("FORWARD_TO_AUTHORITY changes assigned role")
        void forwardToAuthorityShouldChangeRole() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "FORWARD_TO_AUTHORITY");
            result.put("newStatus", "hearing_scheduled");
            result.put("assignedRole", "AA_AUTHORITY");
            result.put("assignedOfficer", "authority-1");
            result.put("workflowStage", "FORWARDED_TO_AUTHORITY");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("FORWARD_TO_AUTHORITY"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "FORWARD_TO_AUTHORITY", "actor", "bench-officer-1",
                    "actorRole", "AA_BENCH_OFFICER", "remarks", "Forwarding to AA");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assignedRole").value("AA_AUTHORITY"))
                    .andExpect(jsonPath("$.data.workflowStage").value("FORWARDED_TO_AUTHORITY"));
        }

        @Test
        @DisplayName("PASS_ORDER with UPHELD outcome")
        void passOrderUpheldShouldSetOutcome() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "PASS_ORDER");
            result.put("newStatus", "order_passed");
            result.put("assignedRole", "AA_AUTHORITY");
            result.put("assignedOfficer", "authority-1");
            result.put("workflowStage", "ORDER_PASSED");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("PASS_ORDER"), any()))
                    .thenReturn(result);

            Map<String, String> request = new HashMap<>();
            request.put("action", "PASS_ORDER");
            request.put("actor", "authority-1");
            request.put("actorRole", "AA_AUTHORITY");
            request.put("orderOutcome", "UPHELD");
            request.put("orderSummary", "Original order upheld");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("order_passed"))
                    .andExpect(jsonPath("$.data.workflowStage").value("ORDER_PASSED"));
        }

        @Test
        @DisplayName("REMAND_TO_OMBUDSMAN closes appeal")
        void remandToOmbudsmanShouldClose() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "REMAND_TO_OMBUDSMAN");
            result.put("newStatus", "closed");
            result.put("assignedRole", "AA_AUTHORITY");
            result.put("assignedOfficer", "authority-1");
            result.put("workflowStage", "REMANDED");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("REMAND_TO_OMBUDSMAN"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "REMAND_TO_OMBUDSMAN", "actor", "authority-1",
                    "actorRole", "AA_AUTHORITY", "remarks", "Remanding for fresh consideration");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("closed"))
                    .andExpect(jsonPath("$.data.workflowStage").value("REMANDED"));
        }

        @Test
        @DisplayName("DISMISS closes appeal")
        void dismissShouldCloseAppeal() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("appealNumber", "APL-20260706-ABC123");
            result.put("action", "DISMISS");
            result.put("newStatus", "closed");
            result.put("assignedRole", "AA_AUTHORITY");
            result.put("assignedOfficer", "authority-1");
            result.put("workflowStage", "DISMISSED");

            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("DISMISS"), any()))
                    .thenReturn(result);

            Map<String, String> request = Map.of("action", "DISMISS", "actor", "authority-1",
                    "actorRole", "AA_AUTHORITY", "remarks", "Not maintainable");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.newStatus").value("closed"))
                    .andExpect(jsonPath("$.data.workflowStage").value("DISMISSED"));
        }

        @Test
        @DisplayName("should return error when action is blank")
        void shouldReturnErrorWhenActionBlank() throws Exception {
            Map<String, String> request = Map.of("action", "", "actor", "registrar-1");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Action is required")));
        }

        @Test
        @DisplayName("should return error for unknown action")
        void shouldReturnErrorForUnknownAction() throws Exception {
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("INVALID"), any()))
                    .thenThrow(new IllegalArgumentException("Unknown action: INVALID"));

            Map<String, String> request = Map.of("action", "INVALID", "actor", "registrar-1", "remarks", "");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Unknown action")));
        }

        @Test
        @DisplayName("should return error when appeal not found for action")
        void shouldReturnErrorWhenAppealNotFound() throws Exception {
            when(appealWorkflowService.performAction(eq("APL-NONEXIST"), eq("ACCEPT"), any()))
                    .thenThrow(new IllegalArgumentException("Appeal not found: APL-NONEXIST"));

            Map<String, String> request = Map.of("action", "ACCEPT", "actor", "registrar-1", "remarks", "");

            mockMvc.perform(post("/api/v1/appeals/APL-NONEXIST/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }

        @Test
        @DisplayName("should return error when classificationType change attempted")
        void shouldReturnErrorWhenClassificationTypeChangeAttempted() throws Exception {
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("ACCEPT"), any()))
                    .thenThrow(new IllegalArgumentException("classificationType is immutable and cannot be changed after creation"));

            Map<String, String> request = new HashMap<>();
            request.put("action", "ACCEPT");
            request.put("actor", "registrar-1");
            request.put("classificationType", "REPRESENTATION");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("immutable")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /{appealNumber}/available-actions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/{appealNumber}/available-actions")
    class GetAvailableActions {

        @Test
        @DisplayName("should return available actions for role")
        void shouldReturnAvailableActionsForRole() throws Exception {
            when(appealWorkflowService.getAvailableActions("APL-20260706-ABC123", "AA_REGISTRAR"))
                    .thenReturn(List.of("ACCEPT", "REJECT", "ASSIGN_TO_BENCH", "REQUEST_DOCUMENTS"));

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123/available-actions")
                            .param("userRole", "AA_REGISTRAR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.appealNumber").value("APL-20260706-ABC123"))
                    .andExpect(jsonPath("$.data.userRole").value("AA_REGISTRAR"))
                    .andExpect(jsonPath("$.data.availableActions", hasSize(4)))
                    .andExpect(jsonPath("$.data.availableActions", hasItem("ACCEPT")));
        }

        @Test
        @DisplayName("should return empty list for closed appeal (non-admin)")
        void shouldReturnEmptyForClosedAppeal() throws Exception {
            when(appealWorkflowService.getAvailableActions("APL-20260706-ABC123", "AA_BENCH_OFFICER"))
                    .thenReturn(Collections.emptyList());

            mockMvc.perform(get("/api/v1/appeals/APL-20260706-ABC123/available-actions")
                            .param("userRole", "AA_BENCH_OFFICER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.availableActions", hasSize(0)));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /stats — statistics
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/appeals/stats")
    class GetStats {

        @Test
        @DisplayName("should return appeal statistics")
        void shouldReturnAppealStatistics() throws Exception {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("total", 50L);
            stats.put("filed", 15);
            stats.put("underReview", 10);
            stats.put("hearingScheduled", 8);
            stats.put("orderPassed", 12);
            stats.put("closed", 3);
            stats.put("rejected", 2);

            when(appealWorkflowService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/api/v1/appeals/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Appeal statistics"))
                    .andExpect(jsonPath("$.data.total").value(50))
                    .andExpect(jsonPath("$.data.filed").value(15))
                    .andExpect(jsonPath("$.data.underReview").value(10))
                    .andExpect(jsonPath("$.data.hearingScheduled").value(8))
                    .andExpect(jsonPath("$.data.orderPassed").value(12))
                    .andExpect(jsonPath("$.data.closed").value(3))
                    .andExpect(jsonPath("$.data.rejected").value(2));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Full Flow test
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full Appeal Workflow")
    class FullWorkflow {

        @Test
        @DisplayName("should complete full flow: file -> accept -> assign -> hearing -> order")
        void shouldCompleteFullAppealFlow() throws Exception {
            // Step 1: File
            Map<String, Object> fileResult = new LinkedHashMap<>();
            fileResult.put("appealNumber", "APL-20260706-ABC123");
            fileResult.put("classificationType", "APPEAL");
            fileResult.put("status", "filed");
            fileResult.put("assignedRole", "AA_REGISTRAR");
            fileResult.put("assignedOfficer", "registrar-1");
            fileResult.put("filedAt", LocalDateTime.now().toString());
            when(appealWorkflowService.fileAppeal(any())).thenReturn(fileResult);

            mockMvc.perform(post("/api/v1/appeals/file")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "originalComplaintNumber", "CMP-20260601-100001",
                                    "classificationType", "APPEAL",
                                    "appellantName", "Test", "appealGround", "Grounds"))))
                    .andExpect(jsonPath("$.data.status").value("filed"));

            // Step 2: Accept
            Map<String, Object> acceptResult = new LinkedHashMap<>();
            acceptResult.put("appealNumber", "APL-20260706-ABC123");
            acceptResult.put("action", "ACCEPT");
            acceptResult.put("newStatus", "under_review");
            acceptResult.put("assignedRole", "AA_REGISTRAR");
            acceptResult.put("assignedOfficer", "registrar-1");
            acceptResult.put("workflowStage", "UNDER_REVIEW");
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("ACCEPT"), any()))
                    .thenReturn(acceptResult);

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "action", "ACCEPT", "actor", "registrar-1", "remarks", "OK"))))
                    .andExpect(jsonPath("$.data.newStatus").value("under_review"));

            // Step 3: Assign to bench
            Map<String, Object> assignResult = new LinkedHashMap<>();
            assignResult.put("appealNumber", "APL-20260706-ABC123");
            assignResult.put("action", "ASSIGN_TO_BENCH");
            assignResult.put("newStatus", "under_review");
            assignResult.put("assignedRole", "AA_BENCH_OFFICER");
            assignResult.put("assignedOfficer", "bench-1");
            assignResult.put("workflowStage", "ASSIGNED_TO_BENCH");
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("ASSIGN_TO_BENCH"), any()))
                    .thenReturn(assignResult);

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "action", "ASSIGN_TO_BENCH", "actor", "registrar-1", "remarks", "Assigning"))))
                    .andExpect(jsonPath("$.data.assignedRole").value("AA_BENCH_OFFICER"));

            // Step 4: Schedule hearing
            Map<String, Object> hearingResult = new LinkedHashMap<>();
            hearingResult.put("appealNumber", "APL-20260706-ABC123");
            hearingResult.put("action", "SCHEDULE_HEARING");
            hearingResult.put("newStatus", "hearing_scheduled");
            hearingResult.put("assignedRole", "AA_BENCH_OFFICER");
            hearingResult.put("assignedOfficer", "bench-1");
            hearingResult.put("workflowStage", "HEARING_SCHEDULED");
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("SCHEDULE_HEARING"), any()))
                    .thenReturn(hearingResult);

            Map<String, String> hearingReq = new HashMap<>();
            hearingReq.put("action", "SCHEDULE_HEARING");
            hearingReq.put("actor", "bench-1");
            hearingReq.put("hearingDate", "2026-07-20T10:00:00");
            hearingReq.put("hearingVenue", "Room C");

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(hearingReq)))
                    .andExpect(jsonPath("$.data.newStatus").value("hearing_scheduled"));

            // Step 5: Pass order
            Map<String, Object> orderResult = new LinkedHashMap<>();
            orderResult.put("appealNumber", "APL-20260706-ABC123");
            orderResult.put("action", "PASS_ORDER");
            orderResult.put("newStatus", "order_passed");
            orderResult.put("assignedRole", "AA_AUTHORITY");
            orderResult.put("assignedOfficer", "authority-1");
            orderResult.put("workflowStage", "ORDER_PASSED");
            when(appealWorkflowService.performAction(eq("APL-20260706-ABC123"), eq("PASS_ORDER"), any()))
                    .thenReturn(orderResult);

            mockMvc.perform(post("/api/v1/appeals/APL-20260706-ABC123/action")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "action", "PASS_ORDER", "actor", "authority-1",
                                    "remarks", "Upheld", "orderOutcome", "UPHELD"))))
                    .andExpect(jsonPath("$.data.newStatus").value("order_passed"));

            verify(appealWorkflowService, times(4)).performAction(eq("APL-20260706-ABC123"), any(), any());
        }
    }
}
