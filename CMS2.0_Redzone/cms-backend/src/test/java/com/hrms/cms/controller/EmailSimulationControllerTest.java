package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.service.EmailSimulationService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailSimulationController.class)
class EmailSimulationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EmailSimulationService emailService;

    @Nested
    class ReceiveEmail {

        @Test
        void shouldReturnThreadResult() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("threadId", "thread-1");
            result.put("complaintNumber", "CMS-001");
            result.put("status", "AWAITING_FORM");

            when(emailService.receiveEmail(any(IncomingEmailRequest.class))).thenReturn(result);

            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("user@test.com");
            request.setFromName("User");
            request.setSubject("Issue");
            request.setBody("Body");

            mockMvc.perform(post("/api/email-simulation/receive")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.threadId").value("thread-1"))
                    .andExpect(jsonPath("$.status").value("AWAITING_FORM"));
        }
    }

    @Nested
    class ReplyWithForm {

        @Test
        void shouldReturnCompletedStatus() throws Exception {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("threadId", "thread-1");
            result.put("complaintNumber", "CMS-001");
            result.put("status", "COMPLETED");

            when(emailService.receiveFormReply(any(EmailReplyWithFormRequest.class))).thenReturn(result);

            EmailReplyWithFormRequest request = new EmailReplyWithFormRequest();
            request.setThreadId("thread-1");
            request.setFromEmail("user@test.com");
            request.setSubject("Re: Issue");
            request.setComplainantName("John");
            request.setComplainantPhone("123");
            request.setComplainantAddress("Addr");
            request.setBankId(1L);
            request.setCategoryId(1L);
            request.setDescription("Details");

            mockMvc.perform(post("/api/email-simulation/reply-with-form")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
        }
    }

    @Nested
    class GetThreads {

        @Test
        void shouldReturnAllThreads() throws Exception {
            Map<String, Object> thread = new LinkedHashMap<>();
            thread.put("threadId", "t-1");
            thread.put("complaintNumber", "CMS-001");
            thread.put("emailCount", 2);

            when(emailService.getAllThreads()).thenReturn(List.of(thread));

            mockMvc.perform(get("/api/email-simulation/threads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].threadId").value("t-1"));
        }

        @Test
        void shouldReturnSingleThread() throws Exception {
            Map<String, Object> thread = new LinkedHashMap<>();
            thread.put("threadId", "t-1");
            thread.put("complaintNumber", "CMS-001");
            thread.put("emails", List.of());

            when(emailService.getThread("t-1")).thenReturn(thread);

            mockMvc.perform(get("/api/email-simulation/threads/t-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.threadId").value("t-1"));
        }
    }

    @Nested
    class InboxAndSent {

        @Test
        void shouldReturnInbox() throws Exception {
            SimulatedEmail email = SimulatedEmail.builder()
                    .id(1L).messageId("m-1").threadId("t-1").fromEmail("a@b.com")
                    .toEmail("cms@rbi.org").subject("Test").direction("INBOUND").status("PROCESSED").build();
            when(emailService.getInbox()).thenReturn(List.of(email));

            mockMvc.perform(get("/api/email-simulation/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        void shouldReturnSent() throws Exception {
            SimulatedEmail email = SimulatedEmail.builder()
                    .id(2L).messageId("m-2").threadId("t-1").fromEmail("cms@rbi.org")
                    .toEmail("a@b.com").subject("Re: Test").direction("OUTBOUND").status("SENT").build();
            when(emailService.getSent()).thenReturn(List.of(email));

            mockMvc.perform(get("/api/email-simulation/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    @Nested
    class FormTemplate {

        @Test
        void shouldReturnTemplate() throws Exception {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("complaintNumber", "CMS-001");
            template.put("fields", List.of(Map.of("key", "name", "label", "Full Name")));

            when(emailService.getFormTemplate("CMS-001")).thenReturn(template);

            mockMvc.perform(get("/api/email-simulation/form-template/CMS-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complaintNumber").value("CMS-001"))
                    .andExpect(jsonPath("$.fields", hasSize(1)));
        }
    }

    @Nested
    class Stats {

        @Test
        void shouldReturnStats() throws Exception {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("totalThreads", 5);
            stats.put("awaitingForm", 2L);
            stats.put("completed", 3L);

            when(emailService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/api/email-simulation/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalThreads").value(5))
                    .andExpect(jsonPath("$.awaitingForm").value(2))
                    .andExpect(jsonPath("$.completed").value(3));
        }
    }
}
