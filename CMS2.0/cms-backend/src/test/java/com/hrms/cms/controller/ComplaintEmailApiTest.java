package com.hrms.cms.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.complaint.ComplaintEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.event.NotificationEventPublisher;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import com.hrms.cms.repository.OfficerAvailabilityRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import com.hrms.cms.security.CallerIdentity;
import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.EncryptionKeyService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.NodalOfficerRecordService;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.OfficerDirectoryService;
import com.hrms.cms.service.RbioHierarchyService;
import com.hrms.cms.service.triage.IntakeTriageService;
import com.rbi.cms.common.enums.DeliveryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Email Communication tab of the complaint detail screen: listing a complaint's mail, saving and
 * re-reading a draft, sending, and replying.
 */
@WebMvcTest(ComplaintApiV1Controller.class)
@AutoConfigureMockMvc(addFilters = false)
class ComplaintEmailApiTest {

    private static final String COMPLAINT_NUMBER = "CMS-20260101-ABC123";
    private static final Long COMPLAINT_ID = 42L;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private SimulatedEmailRepository simulatedEmailRepository;
    @MockBean private ComplaintRepository complaintRepository;
    @MockBean private CallerIdentity callerIdentity;
    @MockBean private NotificationEventPublisher notificationEventPublisher;

    // Collaborators of the controller that these endpoints do not touch.
    @MockBean private ComplaintService complaintService;
    @MockBean private BankRepository bankRepository;
    @MockBean private ComplaintCategoryRepository categoryRepository;
    @MockBean private RegulatedEntityRepository regulatedEntityRepository;
    @MockBean private ComplaintCommentRepository complaintCommentRepository;
    @MockBean private IntakeTriageService triageService;
    @MockBean private ComplaintRoutingService complaintRoutingService;
    @MockBean private KeycloakUserService keycloakUserService;
    @MockBean private OfficerAvailabilityRepository officerAvailabilityRepository;
    @MockBean private OfficeCodeMasterRepository officeCodeMasterRepository;
    @MockBean private RbioHierarchyService rbioHierarchyService;
    @MockBean private OfficerDirectoryService officerDirectoryService;
    @MockBean private com.hrms.cms.event.ComplaintEventPublisher complaintEventPublisher;
    @MockBean private NodalOfficerRecordService nodalOfficerRecordService;
    @MockBean private NotificationService notificationService;
    @MockBean private CepcAuditService auditService;
    // Not a collaborator: PiiDecryptionFilter needs it, and the slice registers filters.
    @MockBean private EncryptionKeyService encryptionKeyService;

    @BeforeEach
    void complaintExists() {
        when(complaintRepository.findByComplaintNumber(COMPLAINT_NUMBER))
                .thenReturn(Optional.of(Complaint.builder()
                        .id(COMPLAINT_ID)
                        .complaintNumber(COMPLAINT_NUMBER)
                        .build()));
        when(callerIdentity.username()).thenReturn("do.officer");
        when(simulatedEmailRepository.save(any(SimulatedEmail.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ComplaintEmailRequest validRequest(String status) {
        ComplaintEmailRequest request = new ComplaintEmailRequest();
        request.setFrom("cmssupportngp@rbi.org.in");
        request.setTo("nodal@bank.example.com");
        request.setSubject("Clarification sought on complaint");
        request.setBody("Please share the transaction log.");
        request.setStatus(status);
        return request;
    }

    /** deliveryStatus is derived from status so the lifecycle stays a single argument at every call site. */
    private SimulatedEmail email(Long id, String status, String threadId) {
        return SimulatedEmail.builder()
                .id(id)
                .messageId("msg-" + id)
                .threadId(threadId)
                .fromEmail("cmssupportngp@rbi.org.in")
                .toEmail("nodal@bank.example.com")
                .subject("Subject " + id)
                .body("Body " + id)
                .direction("OUTBOUND")
                .status(status)
                .deliveryStatus(DeliveryStatus.parse(status).orElse(null))
                .complaintId(COMPLAINT_ID)
                .complaintNumber(COMPLAINT_NUMBER)
                .sentAt(LocalDateTime.of(2026, 1, 2, 10, 0))
                .build();
    }

    @Nested
    class ListEmails {

        @Test
        void shouldSeparateDraftsFromSentAndFlagWhatCanBeRepliedTo() throws Exception {
            when(simulatedEmailRepository.findByComplaintNumberOrderBySentAtDesc(COMPLAINT_NUMBER))
                    .thenReturn(List.of(email(2L, "DRAFT", "thread-2"), email(1L, "SENT", "thread-1")));

            mockMvc.perform(get("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].status").value("DRAFT"))
                    .andExpect(jsonPath("$.data[0].canReply").value(false))
                    .andExpect(jsonPath("$.data[0].editable").value(true))
                    .andExpect(jsonPath("$.data[1].status").value("SENT"))
                    .andExpect(jsonPath("$.data[1].canReply").value(true))
                    .andExpect(jsonPath("$.data[1].editable").value(false))
                    // The activity list renders these directly, so they must not come back empty.
                    .andExpect(jsonPath("$.data[1].id").value(1))
                    .andExpect(jsonPath("$.data[1].date").value("02-01-2026"))
                    .andExpect(jsonPath("$.data[1].from").value("cmssupportngp@rbi.org.in"));
        }

        @Test
        void shouldReturnNotFoundForUnknownComplaint() throws Exception {
            when(complaintRepository.findByComplaintNumber("CMS-NOPE")).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/complaints/{n}/emails", "CMS-NOPE"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false));
        }
    }

    @Nested
    class SaveDraft {

        @Test
        void shouldPersistDraftAgainstTheComplaintNumberTheListIsReadBy() throws Exception {
            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.editable").value(true));

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());

            // The regression this guards: the draft used to be stored under the numeric complaint id while
            // the activity list reads by complaint number, so it never came back.
            assertThat(saved.getValue().getComplaintNumber()).isEqualTo(COMPLAINT_NUMBER);
            assertThat(saved.getValue().getComplaintId()).isEqualTo(COMPLAINT_ID);
            assertThat(saved.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.DRAFT);
            assertThat(saved.getValue().getStatus()).isEqualTo("DRAFT");
            assertThat(saved.getValue().getCreatedBy()).isEqualTo("do.officer");
        }

        @Test
        void shouldPersistCcAndBcc() throws Exception {
            ComplaintEmailRequest request = validRequest("DRAFT");
            request.setCc("reviewer@rbi.org.in");
            request.setBcc("archive@rbi.org.in");

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());
            assertThat(saved.getValue().getCcEmail()).isEqualTo("reviewer@rbi.org.in");
            assertThat(saved.getValue().getBccEmail()).isEqualTo("archive@rbi.org.in");
        }

        @Test
        void shouldGiveEachNewMailItsOwnThreadSoDraftsAndSentStaySeparateRows() throws Exception {
            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isCreated());

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());
            assertThat(saved.getValue().getThreadId()).isNotEqualTo("THR-" + COMPLAINT_NUMBER);
        }

        @Test
        void shouldQueueWhenStatusIsOmitted() throws Exception {
            ComplaintEmailRequest request = validRequest(null);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    // Omitting status means "send", which queues rather than asserting delivery.
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.canReply").value(false));
        }
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectMalformedRecipient() throws Exception {
            ComplaintEmailRequest request = validRequest("SENT");
            request.setTo("not-an-email");

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldRejectBlankSubject() throws Exception {
            ComplaintEmailRequest request = validRequest("SENT");
            request.setSubject("   ");

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldRejectUnknownStatus() throws Exception {
            ComplaintEmailRequest request = validRequest("ARCHIVED");

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldAcceptSeveralCommaSeparatedRecipients() throws Exception {
            ComplaintEmailRequest request = validRequest("SENT");
            request.setTo("one@bank.example.com, two@bank.example.com");

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        void shouldReturnNotFoundWhenComplaintDoesNotExist() throws Exception {
            when(complaintRepository.findByComplaintNumber("CMS-NOPE")).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", "CMS-NOPE")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("SENT"))))
                    .andExpect(status().isNotFound());

            verify(simulatedEmailRepository, never()).save(any());
        }
    }

    @Nested
    class Reply {

        @Test
        void shouldRefuseReplyingToAnUnsentDraft() throws Exception {
            when(simulatedEmailRepository.findById(7L)).thenReturn(Optional.of(email(7L, "DRAFT", "thread-7")));

            ComplaintEmailRequest request = validRequest("SENT");
            request.setInReplyToId(7L);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            "This email has not been sent yet, so it cannot be replied to. Send it first."));

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldJoinTheThreadOfTheMailBeingRepliedTo() throws Exception {
            when(simulatedEmailRepository.findById(8L)).thenReturn(Optional.of(email(8L, "SENT", "thread-8")));

            ComplaintEmailRequest request = validRequest("SENT");
            request.setInReplyToId(8L);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());
            assertThat(saved.getValue().getThreadId()).isEqualTo("thread-8");
        }

        @Test
        void shouldRefuseReplyingToAMailStillQueued() throws Exception {
            when(simulatedEmailRepository.findById(20L)).thenReturn(Optional.of(email(20L, "PENDING", "thread-20")));

            ComplaintEmailRequest request = validRequest("SENT");
            request.setInReplyToId(20L);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldRefuseReplyingToAFailedMail() throws Exception {
            when(simulatedEmailRepository.findById(21L)).thenReturn(Optional.of(email(21L, "FAILED", "thread-21")));

            ComplaintEmailRequest request = validRequest("SENT");
            request.setInReplyToId(21L);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldNotReplyAcrossComplaints() throws Exception {
            SimulatedEmail other = email(9L, "SENT", "thread-9");
            other.setComplaintNumber("CMS-OTHER");
            when(simulatedEmailRepository.findById(9L)).thenReturn(Optional.of(other));

            ComplaintEmailRequest request = validRequest("SENT");
            request.setInReplyToId(9L);

            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateDraft {

        @Test
        void shouldSaveFurtherEditsToADraft() throws Exception {
            when(simulatedEmailRepository.findById(3L)).thenReturn(Optional.of(email(3L, "DRAFT", "thread-3")));

            ComplaintEmailRequest request = validRequest("DRAFT");
            request.setSubject("Revised subject");

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 3L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.subject").value("Revised subject"));
        }

        @Test
        void shouldQueueADraftWhenStatusBecomesSent() throws Exception {
            when(simulatedEmailRepository.findById(4L)).thenReturn(Optional.of(email(4L, "DRAFT", "thread-4")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 4L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("SENT"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    // Not repliable until it has actually gone out.
                    .andExpect(jsonPath("$.data.canReply").value(false))
                    .andExpect(jsonPath("$.data.editable").value(false));
        }

        @Test
        void shouldLeaveTheCreationTimestampAloneWhenADraftIsSent() throws Exception {
            when(simulatedEmailRepository.findById(11L)).thenReturn(Optional.of(email(11L, "DRAFT", "thread-11")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 11L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("SENT"))))
                    .andExpect(status().isOk());

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());
            // sentAt is the activity list's sort key; moving it on send reorders the list under the officer.
            assertThat(saved.getValue().getSentAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
        }

        @Test
        void shouldRefuseEditingAQueuedMail() throws Exception {
            when(simulatedEmailRepository.findById(12L)).thenReturn(Optional.of(email(12L, "PENDING", "thread-12")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 12L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isConflict());

            verify(simulatedEmailRepository, never()).save(any());
        }

        @Test
        void shouldRefuseEditingAMailThatHasAlreadyGoneOut() throws Exception {
            when(simulatedEmailRepository.findById(5L)).thenReturn(Optional.of(email(5L, "SENT", "thread-5")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 5L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(
                            "This email is no longer a draft and can no longer be edited."));

            verify(simulatedEmailRepository, never()).save(any());
        }
    }

    @Nested
    class DispatchRequests {

        @Test
        void shouldRequestDispatchWhenSent() throws Exception {
            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("SENT"))))
                    .andExpect(status().isCreated());

            ArgumentCaptor<SimulatedEmail> published = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(notificationEventPublisher).publishEmailDispatchRequested(published.capture());
            assertThat(published.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(published.getValue().getComplaintNumber()).isEqualTo(COMPLAINT_NUMBER);
        }

        @Test
        void shouldRequestNoDispatchWhenSavingADraft() throws Exception {
            mockMvc.perform(post("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isCreated());

            // The guarantee that saving a draft can never send mail.
            verify(notificationEventPublisher, never()).publishEmailDispatchRequested(any());
        }

        @Test
        void shouldRequestDispatchWhenADraftIsSentViaPut() throws Exception {
            when(simulatedEmailRepository.findById(30L)).thenReturn(Optional.of(email(30L, "DRAFT", "thread-30")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 30L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("SENT"))))
                    .andExpect(status().isOk());

            verify(notificationEventPublisher).publishEmailDispatchRequested(any());
        }

        @Test
        void shouldRequestNoDispatchWhenADraftIsMerelyResaved() throws Exception {
            when(simulatedEmailRepository.findById(31L)).thenReturn(Optional.of(email(31L, "DRAFT", "thread-31")));

            mockMvc.perform(put("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 31L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest("DRAFT"))))
                    .andExpect(status().isOk());

            verify(notificationEventPublisher, never()).publishEmailDispatchRequested(any());
        }
    }

    @Nested
    class Retry {

        @Test
        void shouldRequeueAFailedMail() throws Exception {
            SimulatedEmail failed = email(40L, "FAILED", "thread-40");
            failed.setLastError("Connection refused");
            when(simulatedEmailRepository.findById(40L)).thenReturn(Optional.of(failed));

            mockMvc.perform(post("/api/v1/complaints/{n}/emails/{id}/retry", COMPLAINT_NUMBER, 40L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.canRetry").value(false));

            ArgumentCaptor<SimulatedEmail> saved = ArgumentCaptor.forClass(SimulatedEmail.class);
            verify(simulatedEmailRepository).save(saved.capture());
            assertThat(saved.getValue().getLastError()).isNull();
            verify(notificationEventPublisher).publishEmailDispatchRequested(any());
        }

        @Test
        void shouldRefuseRetryingAMailThatDidNotFail() throws Exception {
            when(simulatedEmailRepository.findById(41L)).thenReturn(Optional.of(email(41L, "SENT", "thread-41")));

            mockMvc.perform(post("/api/v1/complaints/{n}/emails/{id}/retry", COMPLAINT_NUMBER, 41L))
                    .andExpect(status().isConflict());

            verify(simulatedEmailRepository, never()).save(any());
            verify(notificationEventPublisher, never()).publishEmailDispatchRequested(any());
        }

        @Test
        void shouldNotRetryAcrossComplaints() throws Exception {
            SimulatedEmail other = email(42L, "FAILED", "thread-42");
            other.setComplaintNumber("CMS-OTHER");
            when(simulatedEmailRepository.findById(42L)).thenReturn(Optional.of(other));

            mockMvc.perform(post("/api/v1/complaints/{n}/emails/{id}/retry", COMPLAINT_NUMBER, 42L))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class LegacyRows {

        /**
         * Rows written before DELIVERY_STATUS existed, in an environment where the backfill has not run.
         * They must render exactly as they did before rather than losing their reply/edit affordances.
         */
        @Test
        void shouldFallBackToTheLegacyStatusColumn() throws Exception {
            SimulatedEmail legacySent = email(50L, "SENT", "thread-50");
            legacySent.setDeliveryStatus(null);
            SimulatedEmail legacyDraft = email(51L, "DRAFT", "thread-51");
            legacyDraft.setDeliveryStatus(null);

            when(simulatedEmailRepository.findByComplaintNumberOrderBySentAtDesc(COMPLAINT_NUMBER))
                    .thenReturn(List.of(legacySent, legacyDraft));

            mockMvc.perform(get("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("SENT"))
                    .andExpect(jsonPath("$.data[0].canReply").value(true))
                    .andExpect(jsonPath("$.data[1].status").value("DRAFT"))
                    .andExpect(jsonPath("$.data[1].editable").value(true));
        }

        /** An inbound row has no delivery lifecycle at all; its status must survive the mapping untouched. */
        @Test
        void shouldLeaveAnInboundStatusUnmapped() throws Exception {
            SimulatedEmail inbound = email(52L, "PROCESSED", "thread-52");
            inbound.setDirection("INBOUND");
            inbound.setDeliveryStatus(null);

            when(simulatedEmailRepository.findByComplaintNumberOrderBySentAtDesc(COMPLAINT_NUMBER))
                    .thenReturn(List.of(inbound));

            mockMvc.perform(get("/api/v1/complaints/{n}/emails", COMPLAINT_NUMBER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].status").value("PROCESSED"))
                    .andExpect(jsonPath("$.data[0].canReply").value(false))
                    .andExpect(jsonPath("$.data[0].editable").value(false));
        }
    }

    @Nested
    class ThreadDetail {

        @Test
        void shouldReturnEveryMessageInTheThread() throws Exception {
            when(simulatedEmailRepository.findById(1L)).thenReturn(Optional.of(email(1L, "SENT", "thread-1")));
            when(simulatedEmailRepository.findByThreadIdOrderBySentAtAsc("thread-1"))
                    .thenReturn(List.of(email(1L, "SENT", "thread-1"), email(6L, "SENT", "thread-1")));

            mockMvc.perform(get("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 1L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.threadId").value("thread-1"))
                    .andExpect(jsonPath("$.data.messageCount").value(2))
                    .andExpect(jsonPath("$.data.email.id").value(1))
                    .andExpect(jsonPath("$.data.messages.length()").value(2))
                    .andExpect(jsonPath("$.data.messages[1].body").value("Body 6"));
        }

        @Test
        void shouldNotExposeAnEmailBelongingToAnotherComplaint() throws Exception {
            SimulatedEmail other = email(10L, "SENT", "thread-10");
            other.setComplaintNumber("CMS-OTHER");
            when(simulatedEmailRepository.findById(10L)).thenReturn(Optional.of(other));

            mockMvc.perform(get("/api/v1/complaints/{n}/emails/{id}", COMPLAINT_NUMBER, 10L))
                    .andExpect(status().isNotFound());
        }
    }
}
