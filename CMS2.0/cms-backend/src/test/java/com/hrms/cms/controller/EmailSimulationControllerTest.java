package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.dto.simulation.EmailStatsResponse;
import com.hrms.cms.dto.simulation.EmailThreadResponse;
import com.hrms.cms.dto.simulation.EmailThreadSummary;
import com.hrms.cms.dto.simulation.FormFieldDescriptor;
import com.hrms.cms.dto.simulation.FormTemplateResponse;
import com.hrms.cms.dto.simulation.SimulatedEmailResponse;
import com.hrms.cms.service.EmailSimulationService;
import com.hrms.cms.service.EncryptionKeyService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmailSimulationController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmailSimulationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private EmailSimulationService emailService;
    // Not a collaborator of the controller: PiiDecryptionFilter needs it, and the slice registers filters.
    @MockBean private EncryptionKeyService encryptionKeyService;

    @Nested
    class ReceiveEmail {

        @Test
        void shouldReturnThreadResult() throws Exception {
            EmailThreadResponse result = EmailThreadResponse.builder()
                    .threadId("thread-1")
                    .complaintNumber("CMS-001")
                    .status("AWAITING_FORM")
                    .build();

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
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.threadId").value("thread-1"))
                    .andExpect(jsonPath("$.data.status").value("AWAITING_FORM"));
        }
    }

    @Nested
    class ReplyWithForm {

        @Test
        void shouldReturnCompletedStatus() throws Exception {
            EmailThreadResponse result = EmailThreadResponse.builder()
                    .threadId("thread-1")
                    .complaintNumber("CMS-001")
                    .status("COMPLETED")
                    .build();

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
                    .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        }
    }

    @Nested
    class GetThreads {

        @Test
        void shouldReturnAllThreads() throws Exception {
            EmailThreadSummary thread = EmailThreadSummary.builder()
                    .threadId("t-1")
                    .complaintNumber("CMS-001")
                    .emailCount(2)
                    .build();

            when(emailService.getAllThreads()).thenReturn(List.of(thread));

            mockMvc.perform(get("/api/email-simulation/threads"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].threadId").value("t-1"))
                    .andExpect(jsonPath("$.data[0].emailCount").value(2));
        }

        @Test
        void shouldReturnSingleThread() throws Exception {
            EmailThreadResponse thread = EmailThreadResponse.builder()
                    .threadId("t-1")
                    .complaintNumber("CMS-001")
                    .emails(List.of())
                    .build();

            when(emailService.getThread("t-1")).thenReturn(thread);

            mockMvc.perform(get("/api/email-simulation/threads/t-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.threadId").value("t-1"))
                    .andExpect(jsonPath("$.data.emails", hasSize(0)));
        }
    }

    @Nested
    class InboxAndSent {

        @Test
        void shouldReturnInbox() throws Exception {
            SimulatedEmailResponse email = SimulatedEmailResponse.builder()
                    .id(1L).messageId("m-1").threadId("t-1").fromEmail("a@b.com")
                    .toEmail("cms@rbi.org").subject("Test").direction("INBOUND").status("PROCESSED").build();
            when(emailService.getInbox()).thenReturn(List.of(email));

            mockMvc.perform(get("/api/email-simulation/inbox"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].direction").value("INBOUND"));
        }

        @Test
        void shouldReturnSent() throws Exception {
            SimulatedEmailResponse email = SimulatedEmailResponse.builder()
                    .id(2L).messageId("m-2").threadId("t-1").fromEmail("cms@rbi.org")
                    .toEmail("a@b.com").subject("Re: Test").direction("OUTBOUND").status("SENT").build();
            when(emailService.getSent()).thenReturn(List.of(email));

            mockMvc.perform(get("/api/email-simulation/sent"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(1)))
                    .andExpect(jsonPath("$.data[0].direction").value("OUTBOUND"));
        }
    }

    @Nested
    class FormTemplate {

        @Test
        void shouldReturnTemplate() throws Exception {
            FormTemplateResponse template = FormTemplateResponse.builder()
                    .complaintNumber("CMS-001")
                    .fields(List.of(FormFieldDescriptor.builder()
                            .key("name").label("Full Name").type("text").required(true).build()))
                    .build();

            when(emailService.getFormTemplate("CMS-001")).thenReturn(template);

            mockMvc.perform(get("/api/email-simulation/form-template/CMS-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.complaintNumber").value("CMS-001"))
                    .andExpect(jsonPath("$.data.fields", hasSize(1)))
                    .andExpect(jsonPath("$.data.fields[0].required").value(true));
        }
    }

    @Nested
    class Stats {

        @Test
        void shouldReturnStats() throws Exception {
            EmailStatsResponse stats = EmailStatsResponse.builder()
                    .totalThreads(5)
                    .awaitingForm(2L)
                    .completed(3L)
                    .build();

            when(emailService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/api/email-simulation/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalThreads").value(5))
                    .andExpect(jsonPath("$.data.awaitingForm").value(2))
                    .andExpect(jsonPath("$.data.completed").value(3));
        }
    }
}
