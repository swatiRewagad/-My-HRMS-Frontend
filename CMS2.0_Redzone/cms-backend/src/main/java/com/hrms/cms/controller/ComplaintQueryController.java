package com.hrms.cms.controller;

import com.hrms.cms.entity.*;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ComplaintInternalNoteService;
import com.hrms.cms.service.ComplaintQueryService;
import com.hrms.cms.service.ComplaintQueryService.ChecklistItemPayload;
import com.hrms.cms.service.ComplaintQueryService.NewQueryPayload;
import com.hrms.cms.service.ComplaintQueryService.SlotPayload;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Query/correspondence threads on a complaint, shared by the RE portal and the RBI (CEPC/RBIOS)
 * side. Covers UST853, UST854, UST855, UST856, UST857 and UST860.
 *
 * Identity is always resolved server-side via {@link RequestIdentityResolver}; nothing in the
 * request body is trusted for attribution. RE callers are additionally scoped to their own entity
 * code, so one entity cannot read another's threads.
 */
@RestController
@RequestMapping("/api/v1/complaint-queries")
@RequiredArgsConstructor
@Slf4j
public class ComplaintQueryController {

    private final ComplaintQueryService queryService;
    private final ComplaintInternalNoteService internalNoteService;
    private final RequestIdentityResolver identityResolver;

    // ═══════════════════════════════════════════════════════════════
    // Threads
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> listThreads(@PathVariable String complaintNumber,
                                                           HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            String scope = entityScope(identity, request);
            List<ComplaintQuery> threads = queryService.getThreadsForComplaint(complaintNumber, scope);

            List<Map<String, Object>> items = new ArrayList<>();
            for (ComplaintQuery thread : threads) {
                items.add(summarise(thread, identity));
            }
            return ResponseEntity.ok(Map.of("success", true, "count", items.size(), "threads", items));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/complaint/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> raiseThread(@PathVariable String complaintNumber,
                                                           @RequestBody Map<String, Object> body,
                                                           HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            String queryType = str(body.get("queryType"));
            NewQueryPayload payload = buildPayload(body);

            ComplaintQuery thread = queryService.raiseQuery(
                    complaintNumber,
                    entityScope(identity, request),
                    identity,
                    queryType == null ? ComplaintQuery.TYPE_CLARIFICATION : queryType,
                    str(body.get("subject")),
                    str(body.get("body")),
                    payload);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("success", true, "queryId", thread.getId(),
                            "thread", summarise(thread, identity)));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    /** Full thread with its messages. Opening a thread marks it read for the caller (UST856). */
    @GetMapping("/{queryId}")
    public ResponseEntity<Map<String, Object>> getThread(@PathVariable Long queryId,
                                                         HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            String scope = entityScope(identity, request);
            List<ComplaintQueryMessage> messages = queryService.getMessages(queryId, scope);

            List<Map<String, Object>> renderedMessages = new ArrayList<>();
            for (ComplaintQueryMessage m : messages) {
                renderedMessages.add(new LinkedHashMap<>(Map.of(
                        "id", m.getId(),
                        "body", m.getBody(),
                        "authorUserId", m.getAuthorUserId(),
                        "authorName", m.getAuthorName(),
                        "authorRole", m.getAuthorRole() == null ? "" : m.getAuthorRole(),
                        "authorSide", m.getAuthorSide(),
                        "messageKind", m.getMessageKind(),
                        "postedAt", m.getPostedAt().toString())));
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("messages", renderedMessages);
            response.put("checklist", renderChecklist(queryId));
            response.put("slots", renderSlots(queryId));

            queryService.markThreadRead(queryId, identity.getUserId());
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/{queryId}/messages")
    public ResponseEntity<Map<String, Object>> reply(@PathVariable Long queryId,
                                                      @RequestBody Map<String, Object> body,
                                                      HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ComplaintQueryMessage message = queryService.postReply(
                    queryId, entityScope(identity, request), identity, str(body.get("body")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "messageId", message.getId(),
                    "postedAt", message.getPostedAt().toString()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Extension decision (UST853) — RBI only
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/{queryId}/extension-decision")
    public ResponseEntity<Map<String, Object>> decideExtension(@PathVariable Long queryId,
                                                                @RequestBody Map<String, Object> body,
                                                                HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            Boolean approve = body.get("approve") instanceof Boolean b ? b : null;
            if (approve == null) {
                return badRequest(new IllegalArgumentException("approve must be true or false"));
            }
            ComplaintQuery thread = queryService.decideExtension(
                    queryId, identity, approve,
                    parseDateTime(body.get("grantedDeadline")),
                    str(body.get("decisionReason")));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("decision", thread.getDecision());
            response.put("grantedDeadline",
                    thread.getGrantedDeadline() == null ? null : thread.getGrantedDeadline().toString());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Document checklist (UST854)
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/checklist-items/{itemId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveChecklistItem(@PathVariable Long itemId,
                                                                     @RequestBody Map<String, Object> body,
                                                                     HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            Long attachmentId = body.get("attachmentId") instanceof Number n ? n.longValue() : null;
            ComplaintQueryDocItem item = queryService.resolveChecklistItem(
                    itemId, entityScope(identity, request), identity, attachmentId);
            return ResponseEntity.ok(Map.of(
                    "success", true, "itemId", item.getId(), "resolved", item.isResolved()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Meeting response (UST855)
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/{queryId}/meeting-response")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> respondToMeeting(@PathVariable Long queryId,
                                                                 @RequestBody Map<String, Object> body,
                                                                 HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            List<SlotPayload> counterSlots = null;
            if (body.get("counterSlots") instanceof List<?> raw) {
                counterSlots = new ArrayList<>();
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> m) {
                        counterSlots.add(toSlot((Map<String, Object>) m));
                    }
                }
            }
            Long acceptedSlotId = body.get("acceptedSlotId") instanceof Number n ? n.longValue() : null;

            ComplaintQuery thread = queryService.respondToMeeting(
                    queryId, entityScope(identity, request), identity,
                    str(body.get("action")), acceptedSlotId, counterSlots,
                    str(body.get("declineReason")));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("meetingOutcome", thread.getMeetingOutcome());
            response.put("status", thread.getStatus());
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Awaiting-my-response filter and badge count (UST856)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/awaiting-my-response")
    public ResponseEntity<Map<String, Object>> awaitingMyResponse(HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        String scope = entityScope(identity, request);
        List<ComplaintQuery> threads = queryService.getAwaitingMyResponse(identity, scope);

        List<Map<String, Object>> items = new ArrayList<>();
        for (ComplaintQuery thread : threads) {
            items.add(summarise(thread, identity));
        }
        return ResponseEntity.ok(Map.of(
                "success", true,
                // Deliberately the same source as the bell count, so the two cannot disagree.
                "count", queryService.countAwaitingMyResponse(identity, scope),
                "threads", items));
    }

    @GetMapping("/awaiting-my-response/count")
    public ResponseEntity<Map<String, Object>> awaitingCount(HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(Map.of("success", true, "count",
                queryService.countAwaitingMyResponse(identity, entityScope(identity, request))));
    }

    /** Complaint numbers needing this caller's reply, so a list view can badge rows in one call. */
    @GetMapping("/awaiting-my-response/complaint-ids")
    public ResponseEntity<Map<String, Object>> awaitingComplaintIds(HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        return ResponseEntity.ok(Map.of("success", true, "complaintIds",
                queryService.getComplaintIdsAwaitingResponse(identity, entityScope(identity, request))));
    }

    @PostMapping("/{queryId}/mark-read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long queryId,
                                                         HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        queryService.markThreadRead(queryId, identity.getUserId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal notes (UST860) — entity side only
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/complaint/{complaintNumber}/internal-notes")
    public ResponseEntity<Map<String, Object>> listNotes(@PathVariable String complaintNumber,
                                                          HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            List<ComplaintInternalNote> notes = internalNoteService.getNotes(
                    complaintNumber, entityScope(identity, request), identity);

            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> items = new ArrayList<>();
            for (ComplaintInternalNote note : notes) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", note.getId());
                item.put("body", note.getBody());
                item.put("authorUserId", note.getAuthorUserId());
                item.put("authorName", note.getAuthorName());
                item.put("createdAt", note.getCreatedAt().toString());
                item.put("editCount", note.getEditCount());
                // Whether the pencil shows is the server's call, not the browser's.
                item.put("editable", note.isEditable(now)
                        && identity.getUserId().equals(note.getAuthorUserId()));
                item.put("editLockedAt", note.getEditLockedAt().toString());
                items.add(item);
            }
            return ResponseEntity.ok(Map.of("success", true, "notes", items,
                    "editWindowMinutes", internalNoteService.editWindowMinutes()));
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PostMapping("/complaint/{complaintNumber}/internal-notes")
    public ResponseEntity<Map<String, Object>> addNote(@PathVariable String complaintNumber,
                                                        @RequestBody Map<String, Object> body,
                                                        HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ComplaintInternalNote note = internalNoteService.addNote(
                    complaintNumber, entityScope(identity, request), identity, str(body.get("body")));
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true, "noteId", note.getId(),
                    "editLockedAt", note.getEditLockedAt().toString()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    @PutMapping("/internal-notes/{noteId}")
    public ResponseEntity<Map<String, Object>> editNote(@PathVariable Long noteId,
                                                         @RequestBody Map<String, Object> body,
                                                         HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            ComplaintInternalNote note = internalNoteService.editNote(
                    noteId, entityScope(identity, request), identity, str(body.get("body")));
            return ResponseEntity.ok(Map.of(
                    "success", true, "noteId", note.getId(), "editCount", note.getEditCount()));
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        } catch (IllegalStateException e) {
            return conflict(e);
        } catch (NoSuchElementException e) {
            return notFound(e);
        } catch (SecurityException e) {
            return forbidden(e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE callers are pinned to the entity code the resolver derived (JWT claim preferred over
     * header); RBI callers are unscoped (null), since they legitimately see every entity's threads.
     *
     * An RE caller whose entity cannot be established is refused rather than defaulted — a wrong
     * default here would expose another entity's correspondence.
     */
    private String entityScope(RequestIdentity identity, HttpServletRequest request) {
        if (!identity.isRe()) {
            return null;
        }
        if (identity.getEntityCode() == null || identity.getEntityCode().isBlank()) {
            throw new SecurityException("Entity code could not be established for this RE user");
        }
        return identity.getEntityCode();
    }

    private Map<String, Object> summarise(ComplaintQuery thread, RequestIdentity identity) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", thread.getId());
        item.put("queryType", thread.getQueryType());
        item.put("subject", thread.getSubject());
        item.put("direction", thread.getDirection());
        item.put("pendingWith", thread.getPendingWith());
        item.put("status", thread.getStatus());
        item.put("raisedByName", thread.getRaisedByName());
        item.put("raisedBySide", thread.getRaisedBySide());
        item.put("raisedAt", thread.getRaisedAt().toString());
        item.put("awaitingMe", identity.getSide().equals(thread.getPendingWith())
                && ComplaintQuery.STATUS_OPEN.equals(thread.getStatus()));
        item.put("unread", queryService.isUnreadFor(thread.getId(), identity.getUserId()));

        if (ComplaintQuery.TYPE_EXTENSION_REQUEST.equals(thread.getQueryType())) {
            item.put("proposedDeadline", thread.getProposedDeadline() == null
                    ? null : thread.getProposedDeadline().toString());
            item.put("decision", thread.getDecision());
            item.put("grantedDeadline", thread.getGrantedDeadline() == null
                    ? null : thread.getGrantedDeadline().toString());
            item.put("decisionReason", thread.getDecisionReason());
        }
        if (ComplaintQuery.TYPE_MEETING_REQUEST.equals(thread.getQueryType())) {
            item.put("meetingPurpose", thread.getMeetingPurpose());
            item.put("meetingOutcome", thread.getMeetingOutcome());
            item.put("meetingDeclineReason", thread.getMeetingDeclineReason());
        }
        return item;
    }

    private List<Map<String, Object>> renderChecklist(Long queryId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComplaintQueryDocItem item : queryService.getChecklist(queryId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("label", item.getItemLabel());
            row.put("description", item.getItemDescription());
            row.put("resolved", item.isResolved());
            row.put("attachmentId", item.getAttachmentId());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> renderSlots(Long queryId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ComplaintQuerySlot slot : queryService.getSlots(queryId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", slot.getId());
            row.put("proposedStart", slot.getProposedStart().toString());
            row.put("proposedEnd", slot.getProposedEnd() == null ? null : slot.getProposedEnd().toString());
            row.put("slotStatus", slot.getSlotStatus());
            row.put("proposedBySide", slot.getProposedBySide());
            out.add(row);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private NewQueryPayload buildPayload(Map<String, Object> body) {
        NewQueryPayload payload = new NewQueryPayload();
        payload.setProposedDeadline(parseDateTime(body.get("proposedDeadline")));
        payload.setExtensionReason(str(body.get("extensionReason")));
        payload.setMeetingPurpose(str(body.get("meetingPurpose")));

        if (body.get("proposedSlots") instanceof List<?> raw) {
            List<SlotPayload> slots = new ArrayList<>();
            for (Object o : raw) {
                if (o instanceof Map<?, ?> m) {
                    slots.add(toSlot((Map<String, Object>) m));
                }
            }
            payload.setProposedSlots(slots);
        }

        if (body.get("checklistItems") instanceof List<?> raw) {
            List<ChecklistItemPayload> items = new ArrayList<>();
            for (Object o : raw) {
                ChecklistItemPayload item = new ChecklistItemPayload();
                if (o instanceof Map<?, ?> m) {
                    item.setLabel(str(m.get("label")));
                    item.setDescription(str(m.get("description")));
                } else if (o != null) {
                    // Accept a plain list of names as a convenience.
                    item.setLabel(o.toString());
                }
                items.add(item);
            }
            payload.setChecklistItems(items);
        }
        return payload;
    }

    private SlotPayload toSlot(Map<String, Object> map) {
        SlotPayload slot = new SlotPayload();
        slot.setStart(parseDateTime(map.get("start")));
        slot.setEnd(parseDateTime(map.get("end")));
        return slot;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            // A date-only value is accepted and taken as end of that day, so "extend to the 30th"
            // means the whole of the 30th rather than midnight at its start.
            if (text.length() == 10) {
                return java.time.LocalDate.parse(text).atTime(23, 59, 59);
            }
            return LocalDateTime.parse(text.replace(" ", "T").replace("Z", ""));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable date/time: " + text);
        }
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    private ResponseEntity<Map<String, Object>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false, "message", "Could not establish caller identity"));
    }

    private ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> forbidden(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false, "message", e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> notFound(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false, "message", e.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> conflict(Exception e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "success", false, "message", e.getMessage()));
    }
}
