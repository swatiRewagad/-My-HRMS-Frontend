package com.hrms.cms.controller;

import com.hrms.cms.dto.TimelineEntryDto;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.service.CepcAuditService;
import com.hrms.cms.service.ComplaintEmailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
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

        List<Map<String, Object>> emails = new ArrayList<>();
        for (SimulatedEmail e : complaintEmailService.getComplaintEmails(complaintOpt.get())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("messageId", e.getMessageId());
            row.put("threadId", e.getThreadId());
            // ONE direction vocabulary: INBOUND | OUTBOUND. Three were in use for the same idea
            // (SENT / RECEIVED / INBOUND-OUTBOUND), so a shared renderer could not read them all.
            row.put("direction", e.getDirection());
            row.put("from", e.getFromEmail());
            row.put("to", e.getToEmail());
            row.put("cc", e.getCcRecipients());
            row.put("subject", e.getSubject());
            row.put("body", e.getBody());
            row.put("templateUsed", e.getTemplateUsed());
            row.put("sentAt", e.getSentAt() != null ? e.getSentAt() : e.getReceivedAt());
            row.put("status", e.getStatus());
            emails.add(row);
        }

        return ResponseEntity.ok(envelope(emails));
    }

    /**
     * POST /{complaintNumber}/emails — compose, reply, reply-all or forward (UST590-592, UST656).
     *
     * <p>THE FIRST REAL SEND PATH. The only compose UI in the product resolved its send with a
     * {@code setTimeout} and pushed the result into a local signal; nothing was persisted and a refresh
     * lost it.
     *
     * <p>Recipient-domain enforcement happens INSIDE the send, in
     * {@link ComplaintEmailService#sendEmail}, so it cannot be skipped by a client that declines to call
     * a validation endpoint first. That was the previous design: validation was a separate advisory
     * endpoint the client called after it had already decided, purely to write a log line.
     */
    @PostMapping("/{complaintNumber}/emails")
    public ResponseEntity<?> sendEmail(@PathVariable String complaintNumber,
                                       @RequestBody Map<String, Object> body,
                                       @RequestHeader(value = "X-User-Name", required = false) String actor,
                                       HttpServletRequest request) {

        Optional<Complaint> complaintOpt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaintOpt.isEmpty()) {
            return notFound(complaintNumber);
        }

        List<String> recipients = toStringList(body.get("recipients"));
        String subject = asString(body.get("subject"));
        String messageBody = asString(body.get("body"));
        Long templateId = asLong(body.get("templateId"));
        Long inReplyToId = asLong(body.get("inReplyToId"));

        try {
            SimulatedEmail sent = complaintEmailService.sendEmail(
                    complaintOpt.get(), recipients, subject, messageBody,
                    actor == null ? "unknown" : actor, templateId, inReplyToId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", sent.getId());
            data.put("messageId", sent.getMessageId());
            data.put("threadId", sent.getThreadId());
            data.put("direction", sent.getDirection());
            data.put("templateUsed", sent.getTemplateUsed());
            return ResponseEntity.ok(envelope(data));

        } catch (ComplaintEmailService.EmailRecipientRejectedException e) {
            // 422: the request is understood and authorised but its content is not permitted. The
            // rejected addresses come back MASKED — echoing them would leak the very addresses the
            // domain restriction exists to keep out of RBI systems and logs.
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("success", false);
            error.put("messageKey", e.getMessageKey());
            error.put("message", e.getMessage());
            error.put("rejectedRecipients", e.getRejectedMasked());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
        }
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

    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Arrays.stream(s.split(",")).map(String::trim).filter(v -> !v.isEmpty()).toList();
        }
        return List.of();
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) return null;
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
