package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.service.EncryptionKeyService;
import com.hrms.cms.service.ReNotificationService;
import com.hrms.cms.service.RePortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

/**
 * Tests for the RE (Regulated Entity) Portal Controller.
 * Endpoints: /api/v1/re-portal/
 */
@WebMvcTest(RePortalController.class)
@AutoConfigureMockMvc(addFilters = false)
class RePortalControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private RePortalService rePortalService;
    @MockBean private ReNotificationService reNotificationService;
    @MockBean private EncryptionKeyService encryptionKeyService;

    private Complaint sampleComplaint;
    private RegulatedEntity sampleEntity;
    private ReResponseTracker sampleTracker;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMP-20260706-100001")
                .complainantName("Test Citizen")
                .complainantEmail("citizen@example.com")
                .complainantPhone("9876543210")
                .subject("Service Deficiency")
                .description("Bank did not process my request")
                .status("assigned")
                .priority("MEDIUM")
                .department("RBIO")
                .entityCode("HDFC001")
                .assignedRole("RBIO_OFFICER")
                .assignedOfficer("rbio-officer-1")
                .workflowStage("CREATED")
                .createdAt(LocalDateTime.now().minusDays(5))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();

        sampleEntity = RegulatedEntity.builder()
                .id(10L)
                .name("HDFC Bank Ltd")
                .nameNormalized("HDFC BANK LTD")
                .department("RBIO")
                .entityType("BANK")
                .city("Mumbai")
                .state("Maharashtra")
                .status("active")
                .nodalOfficerName("John Doe")
                .nodalOfficerEmail("nodal@hdfc.com")
                .nodalOfficerPhone("9876543210")
                .nodalOfficerDesignation("VP Compliance")
                .portalEnabled(true)
                .build();

        sampleTracker = ReResponseTracker.builder()
                .id(1L)
                .complaintId(1L)
                .regulatedEntityId(10L)
                .forwardedAt(LocalDateTime.now().minusDays(5))
                .respondedAt(LocalDateTime.now())
                .windowDays(15)
                .breached(false)
                .responseText("Issue resolved")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /complaints — list complaints for entity
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/re-portal/complaints")
    class ListComplaints {

        @Test
        @DisplayName("should return paginated complaints for the entity")
        void shouldReturnPaginatedComplaints() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint));

            when(rePortalService.getComplaintsForEntity(eq("HDFC001"), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/re-portal/complaints")
                            .header("X-Entity-Code", "HDFC001")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].complaintNumber").value("CMP-20260706-100001"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("should return complaints filtered by status")
        void shouldReturnComplaintsFilteredByStatus() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint));

            when(rePortalService.getComplaintsForEntity(eq("HDFC001"), eq("assigned"), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/re-portal/complaints")
                            .header("X-Entity-Code", "HDFC001")
                            .param("status", "assigned"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)));
        }

        @Test
        @DisplayName("should return empty list when no complaints match entity")
        void shouldReturnEmptyForNoMatchingComplaints() throws Exception {
            Page<Complaint> emptyPage = new PageImpl<>(Collections.emptyList());

            when(rePortalService.getComplaintsForEntity(eq("UNKNOWN_ENTITY"), any(), any(Pageable.class)))
                    .thenReturn(emptyPage);

            mockMvc.perform(get("/api/v1/re-portal/complaints")
                            .header("X-Entity-Code", "UNKNOWN_ENTITY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(0)))
                    .andExpect(jsonPath("$.totalElements").value(0));
        }

        @Test
        @DisplayName("should support custom page size parameter")
        void shouldSupportCustomPageSize() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint));

            when(rePortalService.getComplaintsForEntity(eq("HDFC001"), any(), any(Pageable.class)))
                    .thenReturn(page);

            mockMvc.perform(get("/api/v1/re-portal/complaints")
                            .header("X-Entity-Code", "HDFC001")
                            .param("page", "0")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /complaints/{number} — single complaint detail
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/re-portal/complaints/{number}")
    class GetComplaintDetail {

        @Test
        @DisplayName("should return single complaint detail")
        void shouldReturnComplaintDetail() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(sampleComplaint);
            when(rePortalService.isWithinResponseWindow(eq("CMP-20260706-100001")))
                    .thenReturn(true);

            mockMvc.perform(get("/api/v1/re-portal/complaints/CMP-20260706-100001")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.complaintNumber").value("CMP-20260706-100001"))
                    .andExpect(jsonPath("$.data.subject").value("Service Deficiency"))
                    .andExpect(jsonPath("$.data.withinResponseWindow").value(true));
        }

        @Test
        @DisplayName("should return 404 when complaint not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(rePortalService.getComplaintDetail(eq("NON-EXISTENT"), eq("HDFC001")))
                    .thenThrow(new NoSuchElementException("Complaint not found: NON-EXISTENT"));

            mockMvc.perform(get("/api/v1/re-portal/complaints/NON-EXISTENT")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }

        @Test
        @DisplayName("should return 403 for complaint belonging to different entity")
        void shouldReturn403ForDifferentEntityComplaint() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("OTHER_ENTITY")))
                    .thenThrow(new SecurityException("Access denied: complaint does not belong to entity OTHER_ENTITY"));

            mockMvc.perform(get("/api/v1/re-portal/complaints/CMP-20260706-100001")
                            .header("X-Entity-Code", "OTHER_ENTITY"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Access denied")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // POST /complaints/{number}/respond — submit response
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/re-portal/complaints/{number}/respond")
    class SubmitResponse {

        @Test
        @DisplayName("should submit response successfully")
        void shouldSubmitResponseSuccessfully() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(sampleComplaint);
            when(rePortalService.respondToComplaint(eq("CMP-20260706-100001"), eq("We have resolved the issue."), eq("nodal-officer-1")))
                    .thenReturn(sampleTracker);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("response", "We have resolved the issue.");
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/respond")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Response submitted successfully"))
                    .andExpect(jsonPath("$.respondedAt").isNotEmpty());

            verify(reNotificationService).notifyResponseReceived(eq("CMP-20260706-100001"), eq("HDFC001"));
        }

        @Test
        @DisplayName("should return 409 when response already submitted")
        void shouldReturn409WhenAlreadyResponded() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(sampleComplaint);
            when(rePortalService.respondToComplaint(eq("CMP-20260706-100001"), eq("Late response."), eq("nodal-officer-1")))
                    .thenThrow(new IllegalStateException("Complaint already responded to on: 2026-07-01T10:00"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("response", "Late response.");
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/respond")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("already responded")));
        }

        @Test
        @DisplayName("should return 400 with empty response text")
        void shouldReturn400WithEmptyResponse() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("response", "");
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/respond")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Response text is required")));
        }

        @Test
        @DisplayName("should return 400 with null response text")
        void shouldReturn400WithNullResponse() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/respond")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Response text is required")));
        }

        @Test
        @DisplayName("should return 404 when complaint not found for response")
        void shouldReturn404WhenComplaintNotFoundForResponse() throws Exception {
            when(rePortalService.getComplaintDetail(eq("NON-EXISTENT"), eq("HDFC001")))
                    .thenThrow(new NoSuchElementException("Complaint not found: NON-EXISTENT"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("response", "Some response text.");
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/NON-EXISTENT/respond")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }

        @Test
        @DisplayName("should return 403 when entity does not own complaint")
        void shouldReturn403WhenEntityDoesNotOwnComplaint() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("OTHER_ENTITY")))
                    .thenThrow(new SecurityException("Access denied: complaint does not belong to entity OTHER_ENTITY"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("response", "Some response.");
            request.put("respondedBy", "nodal-officer-1");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/respond")
                            .header("X-Entity-Code", "OTHER_ENTITY")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Access denied")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /dashboard — stats
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/re-portal/dashboard")
    class Dashboard {

        @Test
        @DisplayName("should return dashboard stats with correct structure")
        void shouldReturnDashboardStats() throws Exception {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalForwarded", 25L);
            stats.put("pending", 5L);
            stats.put("responded", 15L);
            stats.put("breached", 2L);
            stats.put("avgResponseDays", 4.5);

            when(rePortalService.getDashboardStats(eq("HDFC001")))
                    .thenReturn(stats);

            mockMvc.perform(get("/api/v1/re-portal/dashboard")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalForwarded").value(25))
                    .andExpect(jsonPath("$.data.pending").value(5))
                    .andExpect(jsonPath("$.data.responded").value(15))
                    .andExpect(jsonPath("$.data.breached").value(2));
        }

        @Test
        @DisplayName("should return zero stats for new entity")
        void shouldReturnZeroStatsForNewEntity() throws Exception {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalForwarded", 0L);
            stats.put("pending", 0L);
            stats.put("responded", 0L);
            stats.put("breached", 0L);
            stats.put("avgResponseDays", 0.0);

            when(rePortalService.getDashboardStats(eq("NEW_ENTITY")))
                    .thenReturn(stats);

            mockMvc.perform(get("/api/v1/re-portal/dashboard")
                            .header("X-Entity-Code", "NEW_ENTITY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalForwarded").value(0));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /complaints/{number}/timeline — timeline
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/re-portal/complaints/{number}/timeline")
    class GetTimeline {

        @Test
        @DisplayName("should return timeline entries for complaint")
        void shouldReturnTimelineEntries() throws Exception {
            ComplaintTimeline entry = ComplaintTimeline.builder()
                    .id(1L)
                    .complaintId(1L)
                    .action("RE_RESPONDED")
                    .performedBy("nodal-officer-1")
                    .remarks("Response submitted")
                    .fromStatus("assigned")
                    .toStatus("re_responded")
                    .performedAt(LocalDateTime.now())
                    .build();

            when(rePortalService.getTimeline(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(List.of(entry));

            mockMvc.perform(get("/api/v1/re-portal/complaints/CMP-20260706-100001/timeline")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].action").value("RE_RESPONDED"))
                    .andExpect(jsonPath("$.data[0].performedBy").value("nodal-officer-1"));
        }

        @Test
        @DisplayName("should return 404 when complaint not found for timeline")
        void shouldReturn404WhenComplaintNotFoundForTimeline() throws Exception {
            when(rePortalService.getTimeline(eq("NON-EXISTENT"), eq("HDFC001")))
                    .thenThrow(new NoSuchElementException("Complaint not found: NON-EXISTENT"));

            mockMvc.perform(get("/api/v1/re-portal/complaints/NON-EXISTENT/timeline")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // POST /complaints/{number}/query — raise query
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/re-portal/complaints/{number}/query")
    class RaiseQuery {

        @Test
        @DisplayName("should raise clarification query successfully")
        void shouldRaiseClarificationQuery() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(sampleComplaint);
            doNothing().when(rePortalService).raiseQuery(eq("CMP-20260706-100001"), eq("Need more details about the transaction date"), eq("CLARIFICATION"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("queryType", "CLARIFICATION");
            request.put("queryText", "Need more details about the transaction date");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/query")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Query raised successfully"))
                    .andExpect(jsonPath("$.queryType").value("CLARIFICATION"));
        }

        @Test
        @DisplayName("should raise extension request successfully")
        void shouldRaiseExtensionRequest() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("HDFC001")))
                    .thenReturn(sampleComplaint);
            doNothing().when(rePortalService).raiseQuery(eq("CMP-20260706-100001"), eq("Need 7 more days to investigate"), eq("EXTENSION_REQUEST"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("queryType", "EXTENSION_REQUEST");
            request.put("queryText", "Need 7 more days to investigate");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/query")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.queryType").value("EXTENSION_REQUEST"));
        }

        @Test
        @DisplayName("should return 400 with empty query text")
        void shouldReturn400WithEmptyQueryText() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("queryType", "CLARIFICATION");
            request.put("queryText", "");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/query")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Query text is required")));
        }

        @Test
        @DisplayName("should return 400 for invalid query type")
        void shouldReturn400ForInvalidQueryType() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("queryType", "INVALID_TYPE");
            request.put("queryText", "Some query text");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/query")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("queryType must be")));
        }

        @Test
        @DisplayName("should return 403 if complaint not assigned to entity")
        void shouldReturn403IfComplaintNotForEntity() throws Exception {
            when(rePortalService.getComplaintDetail(eq("CMP-20260706-100001"), eq("OTHER_CODE")))
                    .thenThrow(new SecurityException("Access denied: complaint does not belong to entity OTHER_CODE"));

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("queryType", "CLARIFICATION");
            request.put("queryText", "Question about the complaint");

            mockMvc.perform(post("/api/v1/re-portal/complaints/CMP-20260706-100001/query")
                            .header("X-Entity-Code", "OTHER_CODE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Access denied")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // GET /profile — entity profile info
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/re-portal/profile")
    class GetProfile {

        @Test
        @DisplayName("should return entity profile info")
        void shouldReturnEntityProfile() throws Exception {
            when(rePortalService.getEntityProfile(eq("HDFC001")))
                    .thenReturn(sampleEntity);

            mockMvc.perform(get("/api/v1/re-portal/profile")
                            .header("X-Entity-Code", "HDFC001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("HDFC Bank Ltd"))
                    .andExpect(jsonPath("$.data.nodalOfficerName").value("John Doe"))
                    .andExpect(jsonPath("$.data.city").value("Mumbai"));
        }

        @Test
        @DisplayName("should return 404 for unknown entity code")
        void shouldReturn404ForUnknownEntity() throws Exception {
            when(rePortalService.getEntityProfile(eq("UNKNOWN")))
                    .thenThrow(new NoSuchElementException("Regulated entity not found: UNKNOWN"));

            mockMvc.perform(get("/api/v1/re-portal/profile")
                            .header("X-Entity-Code", "UNKNOWN"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("not found")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PUT /profile/nodal-officer — update nodal officer
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PUT /api/v1/re-portal/profile/nodal-officer")
    class UpdateNodalOfficer {

        @Test
        @DisplayName("should update nodal officer details")
        void shouldUpdateNodalOfficer() throws Exception {
            RegulatedEntity updatedEntity = RegulatedEntity.builder()
                    .id(10L)
                    .name("HDFC Bank Ltd")
                    .nodalOfficerName("Jane Smith")
                    .nodalOfficerEmail("jane.smith@hdfc.com")
                    .nodalOfficerPhone("9999888877")
                    .nodalOfficerDesignation("Senior VP")
                    .build();

            when(rePortalService.updateNodalOfficer(eq("HDFC001"), eq("Jane Smith"), eq("jane.smith@hdfc.com"), eq("9999888877"), eq("Senior VP")))
                    .thenReturn(updatedEntity);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", "Jane Smith");
            request.put("email", "jane.smith@hdfc.com");
            request.put("phone", "9999888877");
            request.put("designation", "Senior VP");

            mockMvc.perform(put("/api/v1/re-portal/profile/nodal-officer")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Nodal officer details updated"))
                    .andExpect(jsonPath("$.nodalOfficerName").value("Jane Smith"))
                    .andExpect(jsonPath("$.nodalOfficerEmail").value("jane.smith@hdfc.com"));
        }

        @Test
        @DisplayName("should return 400 with missing name field")
        void shouldReturn400WithMissingName() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("email", "jane.smith@hdfc.com");
            request.put("phone", "9999888877");

            mockMvc.perform(put("/api/v1/re-portal/profile/nodal-officer")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Nodal officer name is required")));
        }

        @Test
        @DisplayName("should return 400 with blank name field")
        void shouldReturn400WithBlankName() throws Exception {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("name", "   ");
            request.put("email", "jane.smith@hdfc.com");

            mockMvc.perform(put("/api/v1/re-portal/profile/nodal-officer")
                            .header("X-Entity-Code", "HDFC001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message", containsString("Nodal officer name is required")));
        }
    }
}
