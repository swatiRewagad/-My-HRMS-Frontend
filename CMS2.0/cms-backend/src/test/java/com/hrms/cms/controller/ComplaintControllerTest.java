package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.UpdateComplaintRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.service.ComplaintService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ComplaintController.class)
class ComplaintControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ComplaintService complaintService;

    private Complaint sampleComplaint;

    @BeforeEach
    void setUp() {
        sampleComplaint = Complaint.builder()
                .id(1L)
                .complaintNumber("CMS-20260515-ABC123")
                .complainantName("John Doe")
                .complainantEmail("john@example.com")
                .subject("ATM Issue")
                .status("pending")
                .priority("medium")
                .build();
    }

    @Nested
    class GetAll {

        @Test
        void shouldReturnAllComplaints() throws Exception {
            when(complaintService.getAllComplaints()).thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/complaints"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].complaintNumber").value("CMS-20260515-ABC123"));
        }

        @Test
        void shouldFilterByStatus() throws Exception {
            when(complaintService.getByStatus("pending")).thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/complaints").param("status", "pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void shouldSearchByQuery() throws Exception {
            when(complaintService.searchComplaints("ATM")).thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/complaints").param("search", "ATM"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].subject").value("ATM Issue"));
        }

        @Test
        void shouldPrioritizeSearchOverStatus() throws Exception {
            when(complaintService.searchComplaints("ATM")).thenReturn(List.of(sampleComplaint));

            mockMvc.perform(get("/api/complaints")
                            .param("search", "ATM")
                            .param("status", "pending"))
                    .andExpect(status().isOk());

            verify(complaintService).searchComplaints("ATM");
            verify(complaintService, never()).getByStatus(anyString());
        }
    }

    @Nested
    class GetAllPaged {

        @Test
        void shouldReturnPagedResults() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), PageRequest.of(0, 20), 1);
            when(complaintService.getAllComplaintsPaged(any())).thenReturn(page);

            mockMvc.perform(get("/api/complaints/paged").param("page", "0").param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void shouldCapSizeAt100() throws Exception {
            Page<Complaint> page = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 100), 0);
            when(complaintService.getAllComplaintsPaged(any())).thenReturn(page);

            mockMvc.perform(get("/api/complaints/paged").param("size", "500"))
                    .andExpect(status().isOk());

            verify(complaintService).getAllComplaintsPaged(argThat(p -> p.getPageSize() == 100));
        }

        @Test
        void shouldFilterPagedByStatus() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), PageRequest.of(0, 20), 1);
            when(complaintService.getByStatusPaged(eq("pending"), any())).thenReturn(page);

            mockMvc.perform(get("/api/complaints/paged").param("status", "pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].status").value("pending"));
        }

        @Test
        void shouldSearchPaged() throws Exception {
            Page<Complaint> page = new PageImpl<>(List.of(sampleComplaint), PageRequest.of(0, 20), 1);
            when(complaintService.searchComplaintsPaged(eq("ATM"), any())).thenReturn(page);

            mockMvc.perform(get("/api/complaints/paged").param("search", "ATM"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class GetById {

        @Test
        void shouldReturnComplaintById() throws Exception {
            when(complaintService.getComplaint(1L)).thenReturn(sampleComplaint);

            mockMvc.perform(get("/api/complaints/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.complainantName").value("John Doe"));
        }

        @Test
        void shouldReturnErrorWhenNotFound() throws Exception {
            when(complaintService.getComplaint(99L)).thenThrow(new RuntimeException("Complaint not found"));

            mockMvc.perform(get("/api/complaints/99"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    class Track {

        @Test
        void shouldTrackByComplaintNumber() throws Exception {
            when(complaintService.getByComplaintNumber("CMS-20260515-ABC123")).thenReturn(sampleComplaint);

            mockMvc.perform(get("/api/complaints/track/CMS-20260515-ABC123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complaintNumber").value("CMS-20260515-ABC123"));
        }
    }

    @Nested
    class FileComplaint {

        @Test
        void shouldCreateComplaint() throws Exception {
            FileComplaintRequest request = new FileComplaintRequest();
            request.setComplainantName("Jane Doe");
            request.setComplainantEmail("jane@test.com");
            request.setSubject("Loan Issue");
            request.setPriority("high");

            when(complaintService.fileComplaint(any(FileComplaintRequest.class))).thenReturn(sampleComplaint);

            mockMvc.perform(post("/api/complaints")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complaintNumber").exists());
        }
    }

    @Nested
    class Update {

        @Test
        void shouldUpdateComplaint() throws Exception {
            UpdateComplaintRequest request = new UpdateComplaintRequest();
            request.setStatus("resolved");
            request.setRemarks("Fixed");

            sampleComplaint.setStatus("resolved");
            when(complaintService.updateComplaint(eq(1L), any(UpdateComplaintRequest.class)))
                    .thenReturn(sampleComplaint);

            mockMvc.perform(put("/api/complaints/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("resolved"));
        }
    }

    @Nested
    class Delete {

        @Test
        void shouldDeleteComplaint() throws Exception {
            doNothing().when(complaintService).deleteComplaint(1L);

            mockMvc.perform(delete("/api/complaints/1"))
                    .andExpect(status().isNoContent());

            verify(complaintService).deleteComplaint(1L);
        }
    }

    @Nested
    class GetTimeline {

        @Test
        void shouldReturnTimeline() throws Exception {
            ComplaintTimeline entry = ComplaintTimeline.builder()
                    .id(1L).complaintId(1L).action("filed").performedBy("System").build();
            when(complaintService.getTimeline(1L)).thenReturn(List.of(entry));

            mockMvc.perform(get("/api/complaints/1/timeline"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].action").value("filed"));
        }
    }
}
