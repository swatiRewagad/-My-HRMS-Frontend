package com.hrms.cms.service;

import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSimulationServiceTest {

    @Mock private SimulatedEmailRepository emailRepository;
    @Mock private ComplaintRepository complaintRepository;
    @Mock private ComplaintTimelineRepository timelineRepository;

    @InjectMocks
    private EmailSimulationService emailService;

    @Nested
    class ReceiveEmail {

        @Test
        void shouldCreateComplaintAndReturnThread() {
            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("user@example.com");
            request.setFromName("John Doe");
            request.setSubject("ATM Issue");
            request.setBody("Money not dispensed from ATM");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = emailService.receiveEmail(request);

            assertThat(result).containsKey("threadId");
            assertThat(result).containsKey("complaintNumber");
            assertThat(result.get("status")).isEqualTo("AWAITING_FORM");
            assertThat((List<?>) result.get("emails")).hasSize(2);

            verify(complaintRepository).save(argThat(c ->
                    "user@example.com".equals(c.getComplainantEmail()) &&
                    "awaiting_details".equals(c.getStatus()) &&
                    "email".equals(c.getFilingType())
            ));
        }

        @Test
        void shouldExtractNameFromEmailWhenNameNull() {
            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("jane.smith@example.com");
            request.setSubject("Test");
            request.setBody("Body");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            emailService.receiveEmail(request);

            verify(complaintRepository).save(argThat(c ->
                    c.getComplainantName().equals("Jane smith")
            ));
        }

        @Test
        void shouldGenerateComplaintNumberWithPrefix() {
            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("test@test.com");
            request.setFromName("Test");
            request.setSubject("Test");
            request.setBody("Body");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = emailService.receiveEmail(request);

            String complaintNumber = (String) result.get("complaintNumber");
            assertThat(complaintNumber).startsWith("CMS-");
            assertThat(complaintNumber).hasSize(19); // CMS-yyyyMMdd-XXXXXX
        }

        @Test
        void shouldSaveTimelineEntry() {
            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("test@test.com");
            request.setFromName("Test");
            request.setSubject("Test");
            request.setBody("Body");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(5L);
                return c;
            });
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            emailService.receiveEmail(request);

            verify(timelineRepository).save(argThat(t ->
                    t.getComplaintId().equals(5L) &&
                    "filed".equals(t.getAction()) &&
                    "Email System".equals(t.getPerformedBy())
            ));
        }

        @Test
        void shouldSendAutoReplyWithFormLink() {
            IncomingEmailRequest request = new IncomingEmailRequest();
            request.setFromEmail("test@test.com");
            request.setFromName("Test User");
            request.setSubject("My Complaint");
            request.setBody("Body");

            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> {
                Complaint c = inv.getArgument(0);
                c.setId(1L);
                return c;
            });
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            emailService.receiveEmail(request);

            verify(emailRepository, times(2)).save(argThat(email ->
                    email.getDirection() == null ||
                    "INBOUND".equals(email.getDirection()) ||
                    ("OUTBOUND".equals(email.getDirection()) &&
                     email.getSubject().contains("Re:") &&
                     email.getAttachmentUrl() != null)
            ));
        }
    }

    @Nested
    class ReceiveFormReply {

        @Test
        void shouldUpdateComplaintWithFormDetails() {
            SimulatedEmail firstEmail = SimulatedEmail.builder()
                    .id(1L).threadId("thread-1").complaintNumber("CMS-20260515-ABC123")
                    .fromEmail("user@test.com").direction("INBOUND").build();

            Complaint complaint = Complaint.builder()
                    .id(1L).complaintNumber("CMS-20260515-ABC123").status("awaiting_details").build();

            EmailReplyWithFormRequest request = new EmailReplyWithFormRequest();
            request.setThreadId("thread-1");
            request.setFromEmail("user@test.com");
            request.setSubject("Re: Test");
            request.setComplainantName("John Doe");
            request.setComplainantPhone("9876543210");
            request.setComplainantAddress("123 Main St");
            request.setBankId(1L);
            request.setBankBranch("Main Branch");
            request.setAccountNumber("1234567890");
            request.setCategoryId(2L);
            request.setDescription("Detailed issue");
            request.setReliefSought("Refund");

            when(emailRepository.findByThreadIdOrderBySentAtAsc("thread-1"))
                    .thenReturn(List.of(firstEmail))
                    .thenReturn(List.of(firstEmail));
            when(complaintRepository.findByComplaintNumber("CMS-20260515-ABC123"))
                    .thenReturn(Optional.of(complaint));
            when(complaintRepository.save(any(Complaint.class))).thenAnswer(inv -> inv.getArgument(0));
            when(emailRepository.save(any(SimulatedEmail.class))).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any(ComplaintTimeline.class))).thenAnswer(inv -> inv.getArgument(0));

            Map<String, Object> result = emailService.receiveFormReply(request);

            assertThat(result.get("status")).isEqualTo("COMPLETED");
            assertThat(result.get("complaintNumber")).isEqualTo("CMS-20260515-ABC123");

            verify(complaintRepository).save(argThat(c ->
                    "John Doe".equals(c.getComplainantName()) &&
                    "pending".equals(c.getStatus()) &&
                    c.getBankId().equals(1L)
            ));
        }

        @Test
        void shouldThrowWhenThreadNotFound() {
            EmailReplyWithFormRequest request = new EmailReplyWithFormRequest();
            request.setThreadId("nonexistent");

            when(emailRepository.findByThreadIdOrderBySentAtAsc("nonexistent"))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> emailService.receiveFormReply(request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Thread not found");
        }

        @Test
        void shouldSaveTimelineOnFormReply() {
            SimulatedEmail firstEmail = SimulatedEmail.builder()
                    .id(1L).threadId("t-1").complaintNumber("CMS-001").build();
            Complaint complaint = Complaint.builder().id(1L).complaintNumber("CMS-001").build();

            EmailReplyWithFormRequest request = new EmailReplyWithFormRequest();
            request.setThreadId("t-1");
            request.setFromEmail("u@e.com");
            request.setSubject("Re: Test");
            request.setComplainantName("Name");
            request.setComplainantPhone("123");
            request.setComplainantAddress("Addr");
            request.setBankId(1L);
            request.setCategoryId(1L);
            request.setDescription("desc");

            when(emailRepository.findByThreadIdOrderBySentAtAsc("t-1"))
                    .thenReturn(List.of(firstEmail));
            when(complaintRepository.findByComplaintNumber("CMS-001"))
                    .thenReturn(Optional.of(complaint));
            when(complaintRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(emailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(timelineRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            emailService.receiveFormReply(request);

            verify(timelineRepository).save(argThat(t ->
                    "details_received".equals(t.getAction()) &&
                    "awaiting_details".equals(t.getFromStatus()) &&
                    "pending".equals(t.getToStatus())
            ));
        }
    }

    @Nested
    class GetAllThreads {

        @Test
        void shouldGroupByThreadId() {
            SimulatedEmail email1 = SimulatedEmail.builder()
                    .id(1L).threadId("t-1").complaintNumber("CMS-001").fromEmail("a@b.com")
                    .subject("Sub1").direction("INBOUND").build();
            SimulatedEmail email2 = SimulatedEmail.builder()
                    .id(2L).threadId("t-1").direction("OUTBOUND").build();

            when(emailRepository.findByDirectionOrderBySentAtDesc("INBOUND"))
                    .thenReturn(List.of(email1));
            when(emailRepository.findByThreadIdOrderBySentAtAsc("t-1"))
                    .thenReturn(List.of(email1, email2));

            List<Map<String, Object>> threads = emailService.getAllThreads();

            assertThat(threads).hasSize(1);
            assertThat(threads.get(0).get("threadId")).isEqualTo("t-1");
            assertThat(threads.get(0).get("emailCount")).isEqualTo(2);
        }

        @Test
        void shouldReturnEmptyListWhenNoEmails() {
            when(emailRepository.findByDirectionOrderBySentAtDesc("INBOUND"))
                    .thenReturn(Collections.emptyList());

            List<Map<String, Object>> threads = emailService.getAllThreads();

            assertThat(threads).isEmpty();
        }
    }

    @Nested
    class GetThread {

        @Test
        void shouldReturnThreadWithEmails() {
            SimulatedEmail email = SimulatedEmail.builder()
                    .id(1L).threadId("t-1").complaintNumber("CMS-001")
                    .fromEmail("a@b.com").subject("Test").direction("INBOUND")
                    .status("PROCESSED").build();

            when(emailRepository.findByThreadIdOrderBySentAtAsc("t-1"))
                    .thenReturn(List.of(email));

            Map<String, Object> result = emailService.getThread("t-1");

            assertThat(result.get("threadId")).isEqualTo("t-1");
            assertThat(result.get("complaintNumber")).isEqualTo("CMS-001");
            assertThat((List<?>) result.get("emails")).hasSize(1);
        }

        @Test
        void shouldThrowWhenThreadNotFound() {
            when(emailRepository.findByThreadIdOrderBySentAtAsc("missing"))
                    .thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> emailService.getThread("missing"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Thread not found");
        }
    }

    @Nested
    class GetInboxAndSent {

        @Test
        void shouldReturnInboundEmails() {
            SimulatedEmail inbound = SimulatedEmail.builder().id(1L).direction("INBOUND").build();
            when(emailRepository.findByDirectionOrderBySentAtDesc("INBOUND"))
                    .thenReturn(List.of(inbound));

            List<SimulatedEmail> result = emailService.getInbox();

            assertThat(result).hasSize(1);
        }

        @Test
        void shouldReturnOutboundEmails() {
            SimulatedEmail outbound = SimulatedEmail.builder().id(1L).direction("OUTBOUND").build();
            when(emailRepository.findByDirectionOrderBySentAtDesc("OUTBOUND"))
                    .thenReturn(List.of(outbound));

            List<SimulatedEmail> result = emailService.getSent();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    class GetStats {

        @Test
        void shouldComputeStats() {
            SimulatedEmail awaitingEmail = SimulatedEmail.builder()
                    .id(1L).threadId("t-1").complaintNumber("CMS-001").fromEmail("a@b.com")
                    .subject("S1").direction("INBOUND").build();
            SimulatedEmail completedInbound1 = SimulatedEmail.builder()
                    .id(2L).threadId("t-2").complaintNumber("CMS-002").fromEmail("c@d.com")
                    .subject("S2").direction("INBOUND").build();
            SimulatedEmail completedInbound2 = SimulatedEmail.builder()
                    .id(3L).threadId("t-2").direction("INBOUND").build();

            when(emailRepository.findByDirectionOrderBySentAtDesc("INBOUND"))
                    .thenReturn(List.of(awaitingEmail, completedInbound1, completedInbound2));
            when(emailRepository.findByThreadIdOrderBySentAtAsc("t-1"))
                    .thenReturn(List.of(awaitingEmail));
            when(emailRepository.findByThreadIdOrderBySentAtAsc("t-2"))
                    .thenReturn(List.of(completedInbound1, completedInbound2));

            Map<String, Object> stats = emailService.getStats();

            assertThat(stats.get("totalThreads")).isEqualTo(2);
            assertThat(stats.get("awaitingForm")).isEqualTo(1L);
            assertThat(stats.get("completed")).isEqualTo(1L);
        }
    }

    @Nested
    class GetFormTemplate {

        @Test
        void shouldReturnTemplateForComplaint() {
            Complaint complaint = Complaint.builder()
                    .id(1L).complaintNumber("CMS-001").complainantEmail("test@test.com").build();
            when(complaintRepository.findByComplaintNumber("CMS-001"))
                    .thenReturn(Optional.of(complaint));

            Map<String, Object> template = emailService.getFormTemplate("CMS-001");

            assertThat(template.get("complaintNumber")).isEqualTo("CMS-001");
            assertThat(template.get("complainantEmail")).isEqualTo("test@test.com");
            assertThat((List<?>) template.get("fields")).isNotEmpty();
        }

        @Test
        void shouldThrowWhenComplaintNotFound() {
            when(complaintRepository.findByComplaintNumber("INVALID"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> emailService.getFormTemplate("INVALID"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("Complaint not found");
        }
    }
}
