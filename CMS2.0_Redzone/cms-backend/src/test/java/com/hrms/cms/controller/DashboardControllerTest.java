package com.hrms.cms.controller;

import com.hrms.cms.dto.DashboardResponse;
import com.hrms.cms.service.ComplaintService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ComplaintService complaintService;

    @Test
    void shouldReturnDashboardStats() throws Exception {
        DashboardResponse dashboard = DashboardResponse.builder()
                .totalComplaints(100)
                .pendingComplaints(30)
                .inProgressComplaints(20)
                .resolvedComplaints(25)
                .closedComplaints(15)
                .escalatedComplaints(10)
                .highPriority(20)
                .mediumPriority(50)
                .lowPriority(30)
                .build();

        when(complaintService.getDashboard()).thenReturn(dashboard);

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalComplaints").value(100))
                .andExpect(jsonPath("$.pendingComplaints").value(30))
                .andExpect(jsonPath("$.inProgressComplaints").value(20))
                .andExpect(jsonPath("$.resolvedComplaints").value(25))
                .andExpect(jsonPath("$.closedComplaints").value(15))
                .andExpect(jsonPath("$.escalatedComplaints").value(10))
                .andExpect(jsonPath("$.highPriority").value(20))
                .andExpect(jsonPath("$.mediumPriority").value(50))
                .andExpect(jsonPath("$.lowPriority").value(30));
    }
}
