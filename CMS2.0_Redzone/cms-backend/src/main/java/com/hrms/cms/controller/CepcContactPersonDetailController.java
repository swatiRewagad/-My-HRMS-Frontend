package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.ComplaintEmailRequest;
import com.hrms.cms.entity.CepcContactPerson;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.CepcContactPersonRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcConciliationService;
import com.hrms.cms.service.ComplaintEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Conciliation and Email Communication tabs of one contact person's own detail screen — reached by
 * clicking a row on the Contact Entity tab's contact-person list.
 *
 * <p>Deliberately a new small controller alongside {@link CepcContactPersonController} rather than more
 * methods on {@link CepcConciliationController} or {@link ComplaintCorrespondenceController}: those two
 * serve the complaint-wide tabs of the same names, which stay exactly as they are. A contact person's own
 * meeting series and email thread are separate records — see {@code CepcConciliationMeeting
 * .contactPersonId} and {@code SimulatedEmail.contactPersonId} — never mirrored onto the complaint.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints/contact-persons/{id}")
@RequiredArgsConstructor
public class CepcContactPersonDetailController {

    private final CepcContactPersonRepository contactPersonRepository;
    private final ComplaintRepository complaintRepository;
    private final CepcConciliationService conciliationService;
    private final ComplaintEmailService complaintEmailService;
    private final CepcIdentityResolver identity;

    // ─────────────────────────── Conciliation ───────────────────────────

    @GetMapping("/conciliation")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> getConciliation(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(body(true, "OK", conciliationService.readForContactPerson(id)));
        } catch (CepcConciliationService.ContactPersonNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        }
    }

    @PutMapping("/conciliation")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONCILIATOR", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> putConciliation(
            @PathVariable Long id, @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> data = conciliationService.updateForContactPerson(
                    id, payload == null ? Map.of() : payload, identity.resolveActor());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcConciliationService.ContactPersonNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcConciliationService.InvalidMeetingException e) {
            return ResponseEntity.badRequest().body(body(false, e.getMessage(), null));
        }
    }

    // ─────────────────────────── Email Communication ───────────────────────────

    @GetMapping("/emails")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<?> getEmails(@PathVariable Long id) {
        Optional<CepcContactPerson> contactOpt = contactPersonRepository.findById(id);
        if (contactOpt.isEmpty()) {
            return contactNotFound(id);
        }

        List<Map<String, Object>> emails = complaintEmailService.getContactPersonEmails(id)
                .stream().map(ComplaintCorrespondenceController::toEmailRow).toList();
        return ResponseEntity.ok(envelope(emails));
    }

    @GetMapping("/emails/{emailId}")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<?> getEmailThread(@PathVariable Long id, @PathVariable Long emailId) {
        Optional<CepcContactPerson> contactOpt = contactPersonRepository.findById(id);
        if (contactOpt.isEmpty()) {
            return contactNotFound(id);
        }
        Optional<SimulatedEmail> emailOpt = complaintEmailService.findOnContactPerson(id, emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(id, emailId);
        }

        SimulatedEmail email = emailOpt.get();
        List<Map<String, Object>> messages = complaintEmailService.thread(email)
                .stream().map(ComplaintCorrespondenceController::toEmailRow).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threadId", email.getThreadId());
        data.put("contactPersonId", id);
        data.put("subject", email.getSubject());
        data.put("status", email.getStatus());
        data.put("messageCount", messages.size());
        data.put("email", ComplaintCorrespondenceController.toEmailRow(email));
        data.put("messages", messages);
        return ResponseEntity.ok(envelope(data));
    }

    @PostMapping("/emails")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<?> sendEmail(@PathVariable Long id, @Valid @RequestBody ComplaintEmailRequest body) {
        Optional<CepcContactPerson> contactOpt = contactPersonRepository.findById(id);
        if (contactOpt.isEmpty()) {
            return contactNotFound(id);
        }
        Optional<Complaint> complaintOpt =
                complaintRepository.findByComplaintNumber(contactOpt.get().getComplaintNumber());
        if (complaintOpt.isEmpty()) {
            return complaintNotFound(contactOpt.get().getComplaintNumber());
        }

        try {
            SimulatedEmail saved = complaintEmailService.composeForContactPerson(
                    complaintOpt.get(), id, body, identity.resolveActor());
            return ResponseEntity.ok(envelope(ComplaintCorrespondenceController.toEmailRow(saved)));
        } catch (ComplaintEmailService.EmailRecipientRejectedException e) {
            return recipientRejected(e);
        }
    }

    @PutMapping("/emails/{emailId}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<?> updateEmail(@PathVariable Long id, @PathVariable Long emailId,
                                         @Valid @RequestBody ComplaintEmailRequest body) {
        Optional<CepcContactPerson> contactOpt = contactPersonRepository.findById(id);
        if (contactOpt.isEmpty()) {
            return contactNotFound(id);
        }
        Optional<Complaint> complaintOpt =
                complaintRepository.findByComplaintNumber(contactOpt.get().getComplaintNumber());
        if (complaintOpt.isEmpty()) {
            return complaintNotFound(contactOpt.get().getComplaintNumber());
        }
        Optional<SimulatedEmail> emailOpt = complaintEmailService.findOnContactPerson(id, emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(id, emailId);
        }

        try {
            SimulatedEmail saved = complaintEmailService.updateDraft(
                    complaintOpt.get(), emailOpt.get(), body, identity.resolveActor());
            return ResponseEntity.ok(envelope(ComplaintCorrespondenceController.toEmailRow(saved)));
        } catch (ComplaintEmailService.EmailRecipientRejectedException e) {
            return recipientRejected(e);
        } catch (ComplaintEmailService.EmailNotEditableException e) {
            return lifecycleConflict(e);
        }
    }

    @PostMapping("/emails/{emailId}/retry")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<?> retryEmail(@PathVariable Long id, @PathVariable Long emailId) {
        Optional<CepcContactPerson> contactOpt = contactPersonRepository.findById(id);
        if (contactOpt.isEmpty()) {
            return contactNotFound(id);
        }
        Optional<Complaint> complaintOpt =
                complaintRepository.findByComplaintNumber(contactOpt.get().getComplaintNumber());
        if (complaintOpt.isEmpty()) {
            return complaintNotFound(contactOpt.get().getComplaintNumber());
        }
        Optional<SimulatedEmail> emailOpt = complaintEmailService.findOnContactPerson(id, emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(id, emailId);
        }

        try {
            SimulatedEmail saved = complaintEmailService.retry(
                    complaintOpt.get(), emailOpt.get(), identity.resolveActor());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", SimulatedEmail.STATUS_SENT.equals(saved.getStatus())
                    ? "The email has been sent."
                    : "The email could not be sent. " + saved.getLastError());
            response.put("data", ComplaintCorrespondenceController.toEmailRow(saved));
            return ResponseEntity.ok(response);
        } catch (ComplaintEmailService.EmailNotEditableException e) {
            return lifecycleConflict(e);
        }
    }

    // ─────────────────────────── Shared response helpers ───────────────────────────

    private ResponseEntity<?> recipientRejected(ComplaintEmailService.EmailRecipientRejectedException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", e.getMessageKey());
        error.put("message", e.getMessage());
        error.put("rejectedRecipients", e.getRejectedMasked());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    private ResponseEntity<?> lifecycleConflict(ComplaintEmailService.EmailNotEditableException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", e.getMessageKey());
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    private ResponseEntity<?> contactNotFound(Long id) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.contact_person.error_not_found");
        error.put("message", "Contact person " + id + " was not found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private ResponseEntity<?> complaintNotFound(String complaintNumber) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.error_not_found");
        error.put("message", "No complaint found with number " + complaintNumber);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private ResponseEntity<?> emailNotFound(Long contactPersonId, Long emailId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.email.error_not_found");
        error.put("message", "No email " + emailId + " on contact person " + contactPersonId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private Map<String, Object> body(boolean success, String message, Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("data", data);
        out.put("timestamp", LocalDateTime.now().toString());
        return out;
    }

    private Map<String, Object> envelope(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }
}
