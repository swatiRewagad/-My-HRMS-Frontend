package com.hrms.cms.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.dto.TimelineEntryDto;
import com.hrms.cms.dto.cepc.ComplaintEmailRequest;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.ComplaintEmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Per-complaint correspondence and history: the Email Communication tab and the History tab
 * (UST590-597, UST656-657).
 *
 * <p>A new controller rather than more methods on {@code ComplaintController}: that class is a
 * chokepoint several sessions edit concurrently, and these routes are cohesive enough to stand alone.
 *
 * <p>Both tabs previously rendered nothing. The email tab had no template block at all, and the history
 * tab was wired to {@code /api/v1/complaints/{id}/action-override} — a route that DOES NOT EXIST in
 * cms-backend, whose 404 the Angular service swallowed with {@code catchError(() => of([]))}, so the tab
 * showed "No action overrides recorded" for every complaint that had ever existed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintCorrespondenceController {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ComplaintEmailService complaintEmailService;
    private final CepcAuditService auditService;
    private final RequestIdentityResolver requestIdentityResolver;

    /**
     * GET /{complaintNumber}/emails — the chronological email log (UST593).
     *
     * <p>Returns sent AND received in one ordered list with sender, recipients, subject, date and the
     * template used.
     */
    @GetMapping("/{complaintNumber}/emails")
    public ResponseEntity<?> getEmails(@PathVariable String complaintNumber) {
        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }

        List<Map<String, Object>> emails = complaintEmailService.getComplaintEmails(complaintOpt.get())
                .stream().map(ComplaintCorrespondenceController::toEmailRow).toList();

        return ResponseEntity.ok(envelope(emails));
    }

    /**
     * GET /{complaintNumber}/emails/{emailId} — the whole conversation behind one row.
     *
     * <p>The list gives one row per message; the reading pane needs the thread, so a reply can be read
     * against what it answers. Returning the messages here rather than having the client group the list
     * keeps threading a server decision — the client has no way to know that two messages share a thread
     * beyond a string it would have to trust.
     */
    @GetMapping("/{complaintNumber}/emails/{emailId}")
    public ResponseEntity<?> getEmailThread(@PathVariable String complaintNumber,
                                            @PathVariable Long emailId) {
        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }
        Optional<SimulatedEmail> emailOpt =
                complaintEmailService.findOnComplaint(complaintOpt.get(), emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(complaintNumber, emailId);
        }

        SimulatedEmail email = emailOpt.get();
        List<Map<String, Object>> messages = complaintEmailService.thread(email)
                .stream().map(ComplaintCorrespondenceController::toEmailRow).toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("threadId", email.getThreadId());
        data.put("complaintNumber", complaintNumber);
        data.put("subject", email.getSubject());
        data.put("status", email.getStatus());
        data.put("messageCount", messages.size());
        data.put("email", toEmailRow(email));
        data.put("messages", messages);
        return ResponseEntity.ok(envelope(data));
    }

    /**
     * POST /{complaintNumber}/emails — save a draft, or compose and send (UST590-592, UST656).
     *
     * <p>THE FIRST REAL SEND PATH. The only compose UI in the product resolved its send with a
     * {@code setTimeout} and pushed the result into a local signal; nothing was persisted and a refresh
     * lost it.
     *
     * <p><b>The request contract was also wrong in a way that made every send fail.</b> This read
     * {@code recipients[]} and {@code templateId}; the form posts {@code from/to/cc/bcc/subject/body/status}.
     * Nothing bound, so {@code recipients} was empty on arrival and the service's own
     * "at least one recipient is required" rejected the officer's message as addressed to nobody.
     *
     * <p>The response is the FULL row, not the five-field acknowledgement it used to be: the client reads
     * {@code status} off it to decide between "sent" and "queued", and re-points the reading pane at it.
     *
     * <p>Recipient-domain enforcement happens INSIDE the send path, so it cannot be skipped by a client that
     * declines to call a validation endpoint first. That was the previous design: validation was a separate
     * advisory endpoint the client called after it had already decided, purely to write a log line.
     */
    @PostMapping("/{complaintNumber}/emails")
    public ResponseEntity<?> sendEmail(@PathVariable String complaintNumber,
                                       @Valid @RequestBody ComplaintEmailRequest body,
                                       HttpServletRequest request) {

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }

        try {
            SimulatedEmail saved = complaintEmailService.compose(
                    complaintOpt.get(), body, actorName(request));
            return ResponseEntity.ok(envelope(toEmailRow(saved)));
        } catch (ComplaintEmailService.EmailRecipientRejectedException e) {
            return recipientRejected(e);
        }
    }

    /**
     * PUT /{complaintNumber}/emails/{emailId} — rewrite a draft, optionally sending it.
     *
     * <p>Same body as the POST, because it is the same form: a draft reopened for editing is the compose
     * form with values in it, and a second contract for the same screen would drift from the first.
     */
    @PutMapping("/{complaintNumber}/emails/{emailId}")
    public ResponseEntity<?> updateEmail(@PathVariable String complaintNumber,
                                         @PathVariable Long emailId,
                                         @Valid @RequestBody ComplaintEmailRequest body,
                                         HttpServletRequest request) {

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }
        Optional<SimulatedEmail> emailOpt =
                complaintEmailService.findOnComplaint(complaintOpt.get(), emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(complaintNumber, emailId);
        }

        try {
            SimulatedEmail saved = complaintEmailService.updateDraft(
                    complaintOpt.get(), emailOpt.get(), body, actorName(request));
            return ResponseEntity.ok(envelope(toEmailRow(saved)));
        } catch (ComplaintEmailService.EmailRecipientRejectedException e) {
            return recipientRejected(e);
        } catch (ComplaintEmailService.EmailNotEditableException e) {
            return lifecycleConflict(e);
        }
    }

    /**
     * POST /{complaintNumber}/emails/{emailId}/retry — attempt a failed dispatch again.
     *
     * <p>Without this a failed mail is a dead row: it cannot be edited (it is not a draft), replied to (it
     * never went out) or resent, so the officer's only option is to compose the whole message again.
     */
    @PostMapping("/{complaintNumber}/emails/{emailId}/retry")
    public ResponseEntity<?> retryEmail(@PathVariable String complaintNumber,
                                        @PathVariable Long emailId,
                                        HttpServletRequest request) {

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }
        Optional<SimulatedEmail> emailOpt =
                complaintEmailService.findOnComplaint(complaintOpt.get(), emailId);
        if (emailOpt.isEmpty()) {
            return emailNotFound(complaintNumber, emailId);
        }

        try {
            SimulatedEmail saved = complaintEmailService.retry(
                    complaintOpt.get(), emailOpt.get(), actorName(request));
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("message", SimulatedEmail.STATUS_SENT.equals(saved.getStatus())
                    ? "The email has been sent."
                    : "The email could not be sent. " + saved.getLastError());
            response.put("data", toEmailRow(saved));
            return ResponseEntity.ok(response);
        } catch (ComplaintEmailService.EmailNotEditableException e) {
            return lifecycleConflict(e);
        }
    }

    /**
     * One email in the shape the Email Communication tab reads.
     *
     * <p>Five of these fields were absent, and their absence disabled controls rather than hiding data:
     * {@code canReply}, {@code editable} and {@code canRetry} arrive as {@code undefined}, which is falsy,
     * so Reply, Edit and Retry were dead on every row. {@code date} was missing too, so every row's date
     * column was blank while {@code sentAt} sat beside it unread.
     *
     * <p><b>The three capability flags are the SERVER's word, not the client's inference.</b> Whether a mail
     * can be replied to depends on whether it actually went out, and the client cannot know that from a
     * status string it would have to interpret — it would have to re-implement the lifecycle rules, and the
     * service enforces them again anyway on the write. Sending them keeps one answer in one place.
     */
    static Map<String, Object> toEmailRow(SimulatedEmail e) {
        String status = e.getStatus() == null ? "" : e.getStatus();
        boolean inbound = ComplaintEmailService.DIRECTION_INBOUND.equalsIgnoreCase(e.getDirection());

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.getId());
        row.put("messageId", e.getMessageId());
        row.put("threadId", e.getThreadId());
        row.put("complaintNumber", e.getComplaintNumber());
        // ONE direction vocabulary: INBOUND | OUTBOUND. Three were in use for the same idea
        // (SENT / RECEIVED / INBOUND-OUTBOUND), so a shared renderer could not read them all.
        row.put("direction", e.getDirection());
        row.put("from", e.getFromEmail());
        row.put("to", e.getToEmail());
        row.put("cc", e.getCcRecipients());
        row.put("bcc", e.getBccRecipients());
        row.put("subject", e.getSubject());
        row.put("body", e.getBody());
        row.put("templateUsed", e.getTemplateUsed());
        row.put("sentAt", e.getSentAt() != null ? e.getSentAt() : e.getReceivedAt());
        row.put("updatedAt", e.getUpdatedAt());
        row.put("date", formatDate(e.effectiveTime()));
        row.put("status", status);
        row.put("assignedTo", e.getAssignedTo());
        row.put("lastError", e.getLastError());
        row.put("retryCount", e.retryCountOrZero());
        // A received mail can always be answered; an outbound one only once it has actually gone out.
        row.put("canReply", inbound || SimulatedEmail.STATUS_SENT.equalsIgnoreCase(status));
        row.put("editable", SimulatedEmail.STATUS_DRAFT.equalsIgnoreCase(status));
        row.put("canRetry", SimulatedEmail.STATUS_FAILED.equalsIgnoreCase(status));
        // Per-message attachments are not modelled — SIMULATED_EMAILS carries a single attachmentUrl and no
        // size. An empty list is the honest answer; a fabricated size would be shown to the officer as fact.
        row.put("attachments", attachmentsOf(e));
        return row;
    }

    static final ObjectMapper ATTACHMENT_MAPPER = new ObjectMapper();

    /**
     * The email's attachments, or nothing.
     *
     * <p>{@code attachmentUrl} carries two shapes. The compose form's own attachments (UST674/…) are a
     * JSON array of {@code {id, name, size}} written by {@code ComplaintEmailService.encodeAttachments} —
     * real documents in {@code COMPLAINT_ATTACHMENTS}, so preview/download resolve through the existing
     * {@code /api/files/*} endpoints. A bare string is the older shape: one URL and no size, as written by
     * the acknowledgement flow in {@code EmailSimulationService}, which this must keep rendering unchanged.
     */
    static List<Map<String, Object>> attachmentsOf(SimulatedEmail e) {
        String raw = e.getAttachmentUrl();
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        List<Map<String, Object>> parsed = parseAttachmentJson(raw);
        if (parsed != null) {
            return parsed;
        }

        // Legacy shape: one bare URL, no id and no size.
        int slash = raw.lastIndexOf('/');
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("name", slash >= 0 && slash < raw.length() - 1 ? raw.substring(slash + 1) : raw);
        attachment.put("size", "");
        attachment.put("url", raw);
        return List.of(attachment);
    }

    /** Null when {@code raw} is not the {@code [{id,name,size}, …]} shape, rather than a legacy URL. */
    private static List<Map<String, Object>> parseAttachmentJson(String raw) {
        List<Map<String, Object>> items;
        try {
            items = ATTACHMENT_MAPPER.readValue(raw, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return null;
        }

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map<String, Object> item : items) {
            Object id = item.get("id");
            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("id", id);
            attachment.put("name", item.getOrDefault("name", "attachment"));
            attachment.put("size", humanSize(item.get("size")));
            if (id != null) {
                attachment.put("url", "/api/files/download/" + id);
                attachment.put("previewUrl", "/api/files/stream/" + id);
            }
            result.add(attachment);
        }
        return result;
    }

    /** {@code size} arrives as a JSON number (bytes) or is absent; either way this must not throw. */
    private static String humanSize(Object sizeValue) {
        if (!(sizeValue instanceof Number number)) {
            return "";
        }
        long bytes = number.longValue();
        if (bytes <= 0) return "";
        return bytes >= 1024 * 1024
                ? String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                : Math.round(bytes / 1024.0) + " KB";
    }

    /** dd-MM-yyyy, the format the tab's date column renders directly. */
    static String formatDate(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(DISPLAY_DATE);
    }

    static final java.time.format.DateTimeFormatter DISPLAY_DATE =
            java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /**
     * 422: the request is understood and authorised but its content is not permitted.
     *
     * <p>The rejected addresses come back MASKED — echoing them would leak the very addresses the domain
     * restriction exists to keep out of RBI systems and logs.
     */
    private ResponseEntity<?> recipientRejected(
            ComplaintEmailService.EmailRecipientRejectedException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", e.getMessageKey());
        error.put("message", e.getMessage());
        error.put("rejectedRecipients", e.getRejectedMasked());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /** 409: the request conflicts with what the row has already become — a sent mail cannot be edited. */
    private ResponseEntity<?> lifecycleConflict(ComplaintEmailService.EmailNotEditableException e) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", e.getMessageKey());
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * 404 for an email that is not on this complaint.
     *
     * <p>Deliberately indistinguishable from an email that does not exist: the id is a global sequence, so
     * telling a caller that id 812 exists but belongs elsewhere confirms another complaint's correspondence
     * to someone probing for it.
     */
    private ResponseEntity<?> emailNotFound(String complaintNumber, Long emailId) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.email.error_not_found");
        error.put("message", "No email " + emailId + " on complaint " + complaintNumber);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * GET /{complaintNumber}/history — the real status/milestone history (UST594-596).
     *
     * <p>Reads COMPLAINT_TIMELINE, which is where status changes have always been written, and returns
     * the canonical {@link TimelineEntryDto} so one renderer can serve every module. Four existing read
     * paths disagreed on field names for these same rows — one even renamed {@code performedAt} to
     * {@code timestamp} and dropped {@code performedBy}.
     *
     * <p>STRICT CHRONOLOGICAL ORDER (UST594), ascending, so a reopen's later entries read as appended
     * rather than interleaved with the original handling.
     *
     * <p>A DTO, not the entity: the previous {@code /timeline} route returned the live managed
     * {@code ComplaintTimeline}, handing callers a mutable handle on an audit record.
     */
    @GetMapping("/{complaintNumber}/history")
    public ResponseEntity<?> getHistory(@PathVariable String complaintNumber) {
        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }

        List<TimelineEntryDto> history =
                timelineRepository.findByComplaintIdOrderByPerformedAtAscIdAsc(complaintOpt.get().getId())
                        .stream().map(TimelineEntryDto::from).toList();

        return ResponseEntity.ok(envelope(history));
    }

    /**
     * Refuses every attempt to modify a history entry, and RECORDS the attempt (UST597).
     *
     * <p>UST597 requires that direct API manipulation be rejected AND logged. Rejection alone was already
     * true by omission — no edit route existed — but "no endpoint" is indistinguishable from "endpoint we
     * forgot to secure" to anyone probing the API, and it produces a 404 that records nothing. An
     * explicit refusal that writes an audit row turns a silent probe into evidence.
     *
     * <p>Mapped for PUT, PATCH and DELETE on both the collection and an individual entry.
     */
    @RequestMapping(value = {"/{complaintNumber}/history", "/{complaintNumber}/history/{entryId}"},
            method = {RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<?> rejectHistoryTampering(
            @PathVariable String complaintNumber,
            @PathVariable(required = false) Long entryId,
            @RequestHeader(value = "X-User-Name", required = false) String actor,
            @RequestHeader(value = "X-User-Roles", required = false) String roles,
            HttpServletRequest request) {

        String method = request.getMethod();
        log.warn("UST597: rejected attempt to {} complaint history for {} (entry {}) by actor={}",
                method, complaintNumber, entryId, actor);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("httpMethod", method);
        metadata.put("path", request.getRequestURI());
        metadata.put("targetEntryId", entryId);
        metadata.put("outcome", "REJECTED");

        try {
            // Recorded on the complaint's own audit trail, so it surfaces beside the actions the actor
            // was permitted to take rather than only in a log file nobody reads.
            auditService.logAction(complaintNumber, "TIMELINE_TAMPER_REJECTED",
                    actor == null ? "anonymous" : actor, roles,
                    "Attempt to modify immutable complaint history was refused", metadata, null, null);
        } catch (Exception e) {
            log.error("Failed to record a history-tampering attempt for {}: {}",
                    complaintNumber, e.getMessage());
        }

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.history.error_immutable");
        error.put("message", "Complaint history is an immutable audit record and cannot be modified "
                + "or deleted. This attempt has been logged.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * The caller's display name, from the JWT the token interceptor already attaches to every
     * request — not an {@code X-User-Name} header nobody on the frontend ever sends. That header
     * fallback was always {@code null} in practice, which is why every composed/edited/retried email
     * was attributed to "unknown" regardless of who was actually logged in.
     */
    private String actorName(HttpServletRequest request) {
        RequestIdentity identity = requestIdentityResolver.resolve(request);
        return identity == null ? "unknown" : identity.getDisplayName();
    }

    private ResponseEntity<?> notFound(String complaintNumber) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("messageKey", "complaint.error_not_found");
        error.put("message", "No complaint found with number " + complaintNumber);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private Map<String, Object> envelope(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

}
