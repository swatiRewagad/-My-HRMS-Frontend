package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.service.CepcLegalCaseService;
import com.hrms.cms.support.ControllerSliceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for CepcLegalCaseController — the tightened bean-validation contract on
 * CepcLegalCaseRequest (pattern/size/date rules) and the InvalidRegionException -> 400 mapping.
 */
@ControllerSliceTest(CepcLegalCaseController.class)
@AutoConfigureMockMvc(addFilters = false)
class CepcLegalCaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CepcLegalCaseService legalCaseService;
    @MockBean private CepcIdentityResolver identityResolver;

    private static final String COMPLAINT_NUMBER = "CMP-20260706-123456";

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("caseNumber", "WP/123/2026");
        body.put("courtName", "High Court of Delhi");
        body.put("advocateName", "John Doe");
        body.put("nextHearingDate", LocalDate.now().plusDays(7).toString());
        return body;
    }

    @Test
    @DisplayName("POST rejects a caseNumber containing a disallowed symbol")
    void rejectsInvalidCaseNumber() throws Exception {
        Map<String, Object> body = validBody();
        body.put("caseNumber", "WP@123");

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("caseNumber")));

        verify(legalCaseService, never()).save(any(), any(), any());
    }

    @Test
    @DisplayName("POST rejects an advocateName containing digits")
    void rejectsInvalidAdvocateName() throws Exception {
        Map<String, Object> body = validBody();
        body.put("advocateName", "John3");

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("advocateName")));
    }

    @Test
    @DisplayName("POST rejects a nextHearingDate in the past")
    void rejectsPastHearingDate() throws Exception {
        Map<String, Object> body = validBody();
        body.put("nextHearingDate", LocalDate.now().minusDays(1).toString());

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("nextHearingDate")));
    }

    @Test
    @DisplayName("POST rejects a courtName over the tightened 150-character limit")
    void rejectsOverlongCourtName() throws Exception {
        Map<String, Object> body = validBody();
        body.put("courtName", "A".repeat(151));

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsStringIgnoringCase("courtName")));
    }

    @Test
    @DisplayName("POST returns 400 when the service rejects the region, not 404")
    void returns400ForInvalidRegion() throws Exception {
        when(legalCaseService.save(eq(COMPLAINT_NUMBER), any(), any()))
                .thenThrow(new CepcLegalCaseService.InvalidRegionException(
                        "Region of Legal Team Involved must be a valid RBI regional/branch office."));

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Region of Legal Team Involved must be a valid RBI regional/branch office."));
    }

    @Test
    @DisplayName("POST returns 404 when the complaint does not exist")
    void returns404ForUnknownComplaint() throws Exception {
        when(legalCaseService.save(eq(COMPLAINT_NUMBER), any(), any()))
                .thenThrow(new CepcLegalCaseService.ComplaintNotFoundException(
                        "Complaint " + COMPLAINT_NUMBER + " was not found."));

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET delegates to the service and returns 200")
    void getDelegatesToService() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", COMPLAINT_NUMBER);
        when(legalCaseService.find(COMPLAINT_NUMBER)).thenReturn(data);

        mockMvc.perform(get("/api/v1/complaints/legal-case").param("complaintNumber", COMPLAINT_NUMBER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.complaintNumber").value(COMPLAINT_NUMBER));
    }

    @Test
    @DisplayName("POST accepts a fully valid payload")
    void acceptsValidPayload() throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", COMPLAINT_NUMBER);
        when(legalCaseService.save(eq(COMPLAINT_NUMBER), any(), any())).thenReturn(data);

        mockMvc.perform(post("/api/v1/complaints/legal-case")
                        .param("complaintNumber", COMPLAINT_NUMBER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validBody())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
