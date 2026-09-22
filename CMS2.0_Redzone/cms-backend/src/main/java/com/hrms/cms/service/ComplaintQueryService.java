package com.hrms.cms.service;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import com.hrms.cms.security.RequestIdentity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Query/correspondence threads between a Regulated Entity and RBI (CEPC/RBIOS) on a complaint.
 *
 * Covers UST853 (extension requests), UST854 (document requests with a checklist),
 * UST855 (meeting requests), UST856 (unread state) and UST857 (tamper-evident history).
 *
 * Replaces the former ReResponseTracker.queryText single slot, which was overwritten on every
 * new query. Messages are append-only: there is no update or delete path in this class, and a
 * correction is made by posting a follow-up message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ComplaintQueryService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintQueryRepository queryRepository;
    private final ComplaintQueryMessageRepository messageRepository;
    private final ComplaintQueryDocItemRepository docItemRepository;
    private final ComplaintQuerySlotRepository slotRepository;
    private final ComplaintQueryReadRepository readRepository;
    private final ReResponseTrackerRepository trackerRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final NotificationService notificationService;

    private static final String CFG_MAX_EXTENSION_DAYS = "cms.query.max_extension_days";
    private static final String CFG_MAX_MEETING_SLOTS = "cms.query.max_meeting_slots";
    private static final String CFG_MAX_CHECKLIST_ITEMS = "cms.query.max_checklist_items";

    private static final Set<String> VALID_TYPES = Set.of(
            ComplaintQuery.TYPE_CLARIFICATION,
            ComplaintQuery.TYPE_EXTENSION_REQUEST,
            ComplaintQuery.TYPE_DOCUMENT_REQUEST,
            ComplaintQuery.TYPE_MEETING_REQUEST);

    // ═══════════════════════════════════════════════════════════════
    // Raising a thread
    // ═══════════════════════════════════════════════════════════════

    /**
     * Opens a new query thread. The opening body becomes the thread's first message, so the
     * thread always has at least one attributed, timestamped entry.
     */
    @Transactional
    public ComplaintQuery raiseQuery(String complaintNumber,
                                     String entityCode,
                                     RequestIdentity identity,
                                     String queryType,
                                     String subject,
                                     String body,
                                     NewQueryPayload payload) {

        if (!VALID_TYPES.contains(queryType)) {
            throw new IllegalArgumentException("Unsupported queryType: " + queryType);
        }
        requireText(subject, "Subject is required");
        requireText(body, "Message body is required");

        Complaint complaint = requireComplaint(complaintNumber, entityCode);

        // The side that raises a thread is awaiting the other side's reply.
        String counterparty = identity.isRe() ? ComplaintQuery.SIDE_RBI : ComplaintQuery.SIDE_RE;

        ComplaintQuery query = ComplaintQuery.builder()
                .complaintId(complaint.getId())
                .entityCode(complaint.getEntityCode())
                .queryType(queryType)
                .subject(subject.trim())
                .direction(identity.isRe() ? "RE_TO_RBI" : "RBI_TO_RE")
                .pendingWith(counterparty)
                .status(ComplaintQuery.STATUS_OPEN)
                .raisedByUserId(identity.getUserId())
                .raisedByName(identity.getDisplayName())
                .raisedByRole(identity.getPrimaryRole())
                .raisedBySide(identity.getSide())
                .raisedAt(LocalDateTime.now())
                .build();

        applyTypePayload(query, queryType, payload, identity);

        query = queryRepository.save(query);
        appendMessage(query, identity, body, ComplaintQueryMessage.KIND_MESSAGE);
        persistTypeChildren(query, queryType, payload, identity);

        // The raiser has necessarily seen their own opening message.
        markThreadRead(query.getId(), identity.getUserId());

        recordTimeline(complaint, "QUERY_RAISED_" + queryType, identity,
                queryType + ": " + subject.trim());
        notifyCounterparty(complaint, query, identity, "notification.query.raised");

        log.info("Query {} raised on complaint {} by {} ({})",
                query.getId(), complaintNumber, identity.getUserId(), identity.getSide());
        return query;
    }

    private void applyTypePayload(ComplaintQuery query, String queryType,
                                  NewQueryPayload payload, RequestIdentity identity) {
        if (ComplaintQuery.TYPE_EXTENSION_REQUEST.equals(queryType)) {
            if (payload == null || payload.getProposedDeadline() == null) {
                throw new IllegalArgumentException("A proposed new deadline is required for an extension request");
            }
            if (!payload.getProposedDeadline().isAfter(LocalDateTime.now())) {
                throw new IllegalArgumentException("The proposed new deadline must be in the future");
            }
            requireText(payload.getExtensionReason(), "A reason is required for an extension request");

            int maxDays = intConfig(CFG_MAX_EXTENSION_DAYS, 30);
            long requestedDays = ChronoUnit.DAYS.between(LocalDateTime.now(), payload.getProposedDeadline());
            if (requestedDays > maxDays) {
                throw new IllegalArgumentException(
                        "The proposed deadline exceeds the maximum extension of " + maxDays + " days");
            }

            query.setProposedDeadline(payload.getProposedDeadline());
            query.setExtensionReason(payload.getExtensionReason().trim());
            query.setDecision(ComplaintQuery.DECISION_PENDING);
        }

        if (ComplaintQuery.TYPE_MEETING_REQUEST.equals(queryType)) {
            requireText(payload == null ? null : payload.getMeetingPurpose(),
                    "A stated purpose is required for a meeting request");
            if (payload.getProposedSlots() == null || payload.getProposedSlots().isEmpty()) {
                throw new IllegalArgumentException("At least one proposed date/time is required");
            }
            int maxSlots = intConfig(CFG_MAX_MEETING_SLOTS, 5);
            if (payload.getProposedSlots().size() > maxSlots) {
                throw new IllegalArgumentException("At most " + maxSlots + " proposed slots are allowed");
            }
            query.setMeetingPurpose(payload.getMeetingPurpose().trim());
            query.setMeetingOutcome(ComplaintQuery.MEETING_PENDING);
        }

        if (ComplaintQuery.TYPE_DOCUMENT_REQUEST.equals(queryType)) {
            if (payload == null || payload.getChecklistItems() == null || payload.getChecklistItems().isEmpty()) {
                throw new IllegalArgumentException("A document request must name at least one document");
            }
            int maxItems = intConfig(CFG_MAX_CHECKLIST_ITEMS, 20);
            if (payload.getChecklistItems().size() > maxItems) {
                throw new IllegalArgumentException("At most " + maxItems + " checklist items are allowed");
            }
        }
    }

    private void persistTypeChildren(ComplaintQuery query, String queryType,
                                     NewQueryPayload payload, RequestIdentity identity) {
        if (ComplaintQuery.TYPE_DOCUMENT_REQUEST.equals(queryType)) {
            int order = 0;
            for (ChecklistItemPayload item : payload.getChecklistItems()) {
                requireText(item.getLabel(), "Each checklist item needs a document name");
                docItemRepository.save(ComplaintQueryDocItem.builder()
                        .queryId(query.getId())
                        .itemLabel(item.getLabel().trim())
                        .itemDescription(item.getDescription())
                        .displayOrder(order++)
                        .resolved(false)
                        .build());
            }
        }

        if (ComplaintQuery.TYPE_MEETING_REQUEST.equals(queryType)) {
            int order = 0;
            for (SlotPayload slot : payload.getProposedSlots()) {
                if (slot.getStart() == null) {
                    throw new IllegalArgumentException("Each proposed slot needs a start date/time");
                }
                slotRepository.save(ComplaintQuerySlot.builder()
                        .queryId(query.getId())
                        .proposedStart(slot.getStart())
                        .proposedEnd(slot.getEnd())
                        .slotStatus(ComplaintQuerySlot.SLOT_PROPOSED)
                        .proposedBySide(identity.getSide())
                        .displayOrder(order++)
                        .build());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Replying (UST857)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Appends a reply. Posting flips which side the thread is pending with, which is what makes
     * the "awaiting my response" filter self-maintaining.
     */
    @Transactional
    public ComplaintQueryMessage postReply(Long queryId, String entityCode,
                                           RequestIdentity identity, String body) {
        requireText(body, "Message body is required");

        ComplaintQuery query = requireQuery(queryId, entityCode);
        if (ComplaintQuery.STATUS_RESOLVED.equals(query.getStatus())) {
            throw new IllegalStateException("This thread is resolved and cannot take further replies");
        }

        ComplaintQueryMessage message = appendMessage(query, identity, body,
                ComplaintQueryMessage.KIND_MESSAGE);

        query.setPendingWith(identity.isRe() ? ComplaintQuery.SIDE_RBI : ComplaintQuery.SIDE_RE);
        queryRepository.save(query);

        markThreadRead(queryId, identity.getUserId());

        Complaint complaint = complaintRepository.findById(query.getComplaintId()).orElse(null);
        if (complaint != null) {
            notifyCounterparty(complaint, query, identity, "notification.query.replied");
        }
        return message;
    }

    /**
     * The single insertion point for thread messages. Author fields come from the server-resolved
     * identity, never from the request body, and postedAt is server time.
     */
    private ComplaintQueryMessage appendMessage(ComplaintQuery query, RequestIdentity identity,
                                                String body, String kind) {
        return messageRepository.save(ComplaintQueryMessage.builder()
                .queryId(query.getId())
                .body(body.trim())
                .authorUserId(identity.getUserId())
                .authorName(identity.getDisplayName())
                .authorRole(identity.getPrimaryRole())
                .authorSide(identity.getSide())
                .messageKind(kind)
                .postedAt(LocalDateTime.now())
                .build());
    }

    // ═══════════════════════════════════════════════════════════════
    // Extension request decision (UST853)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Approves or rejects an extension request. Only the RBI side may decide.
     *
     * On approval the SLA clock is paused and the tracker's window moves to the granted deadline.
     * On rejection the original deadline is untouched. Either way the decision is recorded as a
     * SYSTEM message so it appears inline in the thread history.
     */
    @Transactional
    public ComplaintQuery decideExtension(Long queryId, RequestIdentity identity,
                                          boolean approve, LocalDateTime grantedDeadline,
                                          String decisionReason) {
        if (!identity.isRbi()) {
            throw new SecurityException("Only RBI users may decide an extension request");
        }

        ComplaintQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new NoSuchElementException("Query not found: " + queryId));

        if (!ComplaintQuery.TYPE_EXTENSION_REQUEST.equals(query.getQueryType())) {
            throw new IllegalArgumentException("This thread is not an extension request");
        }
        if (!ComplaintQuery.DECISION_PENDING.equals(query.getDecision())) {
            throw new IllegalStateException("This extension request has already been decided");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint complaint = complaintRepository.findById(query.getComplaintId())
                .orElseThrow(() -> new NoSuchElementException("Complaint not found for query " + queryId));

        if (approve) {
            LocalDateTime effective = grantedDeadline != null ? grantedDeadline : query.getProposedDeadline();
            if (!effective.isAfter(now)) {
                throw new IllegalArgumentException("The granted deadline must be in the future");
            }
            int maxDays = intConfig(CFG_MAX_EXTENSION_DAYS, 30);
            if (ChronoUnit.DAYS.between(now, effective) > maxDays) {
                throw new IllegalArgumentException(
                        "The granted deadline exceeds the maximum extension of " + maxDays + " days");
            }

            query.setDecision(ComplaintQuery.DECISION_APPROVED);
            query.setGrantedDeadline(effective);
            pauseSlaClock(complaint, effective, now);
        } else {
            requireText(decisionReason, "A reason is required when rejecting an extension request");
            query.setDecision(ComplaintQuery.DECISION_REJECTED);
        }

        query.setDecidedBy(identity.getUserId());
        query.setDecidedAt(now);
        query.setDecisionReason(decisionReason == null ? null : decisionReason.trim());
        query.setStatus(ComplaintQuery.STATUS_RESOLVED);
        query.setPendingWith(ComplaintQuery.PENDING_NONE);
        query.setResolvedAt(now);
        query.setResolvedBy(identity.getUserId());
        queryRepository.save(query);

        String summary = approve
                ? "Extension approved. New deadline: " + query.getGrantedDeadline()
                : "Extension rejected. Reason: " + query.getDecisionReason();
        appendMessage(query, identity, summary, ComplaintQueryMessage.KIND_SYSTEM);

        recordTimeline(complaint,
                approve ? "EXTENSION_APPROVED" : "EXTENSION_REJECTED", identity, summary);
        notifyCounterparty(complaint, query, identity,
                approve ? "notification.query.extension_approved" : "notification.query.extension_rejected");

        return query;
    }

    /**
     * Pauses the RE response SLA and moves the window to the granted deadline.
     *
     * slaPauseMinutes accumulates rather than overwrites, so a second approved extension does not
     * discard the first pause. This is the only writer of these columns.
     */
    private void pauseSlaClock(Complaint complaint, LocalDateTime newDeadline, LocalDateTime now) {
        trackerRepository.findByComplaintId(complaint.getId()).ifPresent(tracker -> {
            LocalDateTime previousExpiry = tracker.getWindowExpiresAt();
            if (previousExpiry != null && newDeadline.isAfter(previousExpiry)) {
                long added = ChronoUnit.MINUTES.between(previousExpiry, newDeadline);
                Long existing = tracker.getSlaPauseMinutes();
                tracker.setSlaPauseMinutes((existing == null ? 0L : existing) + added);
            }
            tracker.setSlaPaused(true);
            tracker.setSlaPausedAt(now);
            tracker.setWindowExpiresAt(newDeadline);
            tracker.setExtensionGranted(true);
            if (tracker.getForwardedAt() != null) {
                tracker.setExtensionDays(
                        (int) ChronoUnit.DAYS.between(tracker.getForwardedAt(), newDeadline));
            }
            trackerRepository.save(tracker);
        });

        // Complaint.reResponseDeadline is a LocalDate, so the granted instant is truncated to its day.
        complaint.setReResponseDeadline(newDeadline.toLocalDate());
        complaintRepository.save(complaint);
    }

    // ═══════════════════════════════════════════════════════════════
    // Document checklist (UST854)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Marks a checklist item resolved. An item can only be resolved once a document is attached,
     * so the checklist cannot be cleared without evidence.
     */
    @Transactional
    public ComplaintQueryDocItem resolveChecklistItem(Long itemId, String entityCode,
                                                      RequestIdentity identity, Long attachmentId) {
        ComplaintQueryDocItem item = docItemRepository.findById(itemId)
                .orElseThrow(() -> new NoSuchElementException("Checklist item not found: " + itemId));

        ComplaintQuery query = requireQuery(item.getQueryId(), entityCode);

        if (attachmentId == null) {
            throw new IllegalArgumentException(
                    "A document must be attached before this checklist item can be marked provided");
        }
        if (item.isResolved()) {
            return item;
        }

        item.setResolved(true);
        item.setResolvedAt(LocalDateTime.now());
        item.setResolvedBy(identity.getUserId());
        item.setAttachmentId(attachmentId);
        docItemRepository.save(item);

        appendMessage(query, identity,
                "Provided requested document: " + item.getItemLabel(),
                ComplaintQueryMessage.KIND_SYSTEM);

        // Once every requested document is in, the thread stops awaiting the recipient.
        if (docItemRepository.countByQueryIdAndResolvedFalse(query.getId()) == 0) {
            query.setStatus(ComplaintQuery.STATUS_RESOLVED);
            query.setPendingWith(ComplaintQuery.PENDING_NONE);
            query.setResolvedAt(LocalDateTime.now());
            query.setResolvedBy(identity.getUserId());
        } else {
            query.setPendingWith(identity.isRe() ? ComplaintQuery.SIDE_RBI : ComplaintQuery.SIDE_RE);
        }
        queryRepository.save(query);

        return item;
    }

    // ═══════════════════════════════════════════════════════════════
    // Meeting scheduling (UST855)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Records the recipient's response to a meeting request: accept a slot, counter-propose, or
     * decline with a reason. No calendar invite is created — this phase is tracking only.
     */
    @Transactional
    public ComplaintQuery respondToMeeting(Long queryId, String entityCode, RequestIdentity identity,
                                           String action, Long acceptedSlotId,
                                           List<SlotPayload> counterSlots, String declineReason) {

        ComplaintQuery query = requireQuery(queryId, entityCode);
        if (!ComplaintQuery.TYPE_MEETING_REQUEST.equals(query.getQueryType())) {
            throw new IllegalArgumentException("This thread is not a meeting request");
        }
        if (!ComplaintQuery.MEETING_PENDING.equals(query.getMeetingOutcome())) {
            throw new IllegalStateException("This meeting request has already been settled");
        }

        LocalDateTime now = LocalDateTime.now();
        Complaint complaint = complaintRepository.findById(query.getComplaintId()).orElse(null);
        String summary;

        switch (action == null ? "" : action.toUpperCase(Locale.ROOT)) {
            case "ACCEPT" -> {
                ComplaintQuerySlot slot = slotRepository.findById(
                                Objects.requireNonNull(acceptedSlotId, "acceptedSlotId is required"))
                        .orElseThrow(() -> new NoSuchElementException("Slot not found: " + acceptedSlotId));
                if (!slot.getQueryId().equals(queryId)) {
                    throw new IllegalArgumentException("That slot does not belong to this thread");
                }
                slot.setSlotStatus(ComplaintQuerySlot.SLOT_ACCEPTED);
                slotRepository.save(slot);
                slotRepository.findByQueryIdAndSlotStatus(queryId, ComplaintQuerySlot.SLOT_PROPOSED)
                        .forEach(other -> {
                            other.setSlotStatus(ComplaintQuerySlot.SLOT_SUPERSEDED);
                            slotRepository.save(other);
                        });
                query.setMeetingOutcome(ComplaintQuery.MEETING_ACCEPTED);
                query.setStatus(ComplaintQuery.STATUS_RESOLVED);
                query.setPendingWith(ComplaintQuery.PENDING_NONE);
                query.setResolvedAt(now);
                query.setResolvedBy(identity.getUserId());
                summary = "Meeting accepted for " + slot.getProposedStart();
            }
            case "DECLINE" -> {
                requireText(declineReason, "A reason is required when declining a meeting request");
                slotRepository.findByQueryIdAndSlotStatus(queryId, ComplaintQuerySlot.SLOT_PROPOSED)
                        .forEach(slot -> {
                            slot.setSlotStatus(ComplaintQuerySlot.SLOT_DECLINED);
                            slotRepository.save(slot);
                        });
                query.setMeetingOutcome(ComplaintQuery.MEETING_DECLINED);
                query.setMeetingDeclineReason(declineReason.trim());
                query.setStatus(ComplaintQuery.STATUS_RESOLVED);
                query.setPendingWith(ComplaintQuery.PENDING_NONE);
                query.setResolvedAt(now);
                query.setResolvedBy(identity.getUserId());
                summary = "Meeting declined. Reason: " + declineReason.trim();
            }
            case "COUNTER" -> {
                if (counterSlots == null || counterSlots.isEmpty()) {
                    throw new IllegalArgumentException("At least one alternative slot is required");
                }
                int maxSlots = intConfig(CFG_MAX_MEETING_SLOTS, 5);
                if (counterSlots.size() > maxSlots) {
                    throw new IllegalArgumentException("At most " + maxSlots + " proposed slots are allowed");
                }
                // Supersede rather than mutate, so the original proposal stays visible.
                slotRepository.findByQueryIdAndSlotStatus(queryId, ComplaintQuerySlot.SLOT_PROPOSED)
                        .forEach(slot -> {
                            slot.setSlotStatus(ComplaintQuerySlot.SLOT_SUPERSEDED);
                            slotRepository.save(slot);
                        });
                int order = 0;
                for (SlotPayload payload : counterSlots) {
                    if (payload.getStart() == null) {
                        throw new IllegalArgumentException("Each alternative slot needs a start date/time");
                    }
                    slotRepository.save(ComplaintQuerySlot.builder()
                            .queryId(queryId)
                            .proposedStart(payload.getStart())
                            .proposedEnd(payload.getEnd())
                            .slotStatus(ComplaintQuerySlot.SLOT_PROPOSED)
                            .proposedBySide(identity.getSide())
                            .displayOrder(order++)
                            .build());
                }
                query.setMeetingOutcome(ComplaintQuery.MEETING_COUNTER_PROPOSED);
                query.setPendingWith(identity.isRe() ? ComplaintQuery.SIDE_RBI : ComplaintQuery.SIDE_RE);
                summary = "Alternative time(s) proposed";
            }
            default -> throw new IllegalArgumentException("action must be ACCEPT, DECLINE or COUNTER");
        }

        queryRepository.save(query);
        appendMessage(query, identity, summary, ComplaintQueryMessage.KIND_SYSTEM);

        if (complaint != null) {
            recordTimeline(complaint, "MEETING_" + query.getMeetingOutcome(), identity, summary);
            notifyCounterparty(complaint, query, identity, "notification.query.meeting_updated");
        }
        // A counter-proposal reopens the negotiation, so the outcome is deliberately left
        // PENDING-equivalent by keeping status OPEN above.
        if (ComplaintQuery.MEETING_COUNTER_PROPOSED.equals(query.getMeetingOutcome())) {
            query.setMeetingOutcome(ComplaintQuery.MEETING_PENDING);
            queryRepository.save(query);
        }
        return query;
    }

    // ═══════════════════════════════════════════════════════════════
    // Reading / unread state (UST856)
    // ═══════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public List<ComplaintQuery> getThreadsForComplaint(String complaintNumber, String entityCode) {
        Complaint complaint = requireComplaint(complaintNumber, entityCode);
        return entityCode == null
                ? queryRepository.findByComplaintIdOrderByRaisedAtDesc(complaint.getId())
                : queryRepository.findByComplaintIdAndEntityCodeOrderByRaisedAtDesc(
                        complaint.getId(), entityCode);
    }

    @Transactional(readOnly = true)
    public List<ComplaintQueryMessage> getMessages(Long queryId, String entityCode) {
        requireQuery(queryId, entityCode);
        return messageRepository.findByQueryIdOrderByPostedAtAscIdAsc(queryId);
    }

    @Transactional(readOnly = true)
    public List<ComplaintQueryDocItem> getChecklist(Long queryId) {
        return docItemRepository.findByQueryIdOrderByDisplayOrderAscIdAsc(queryId);
    }

    @Transactional(readOnly = true)
    public List<ComplaintQuerySlot> getSlots(Long queryId) {
        return slotRepository.findByQueryIdOrderByDisplayOrderAscIdAsc(queryId);
    }

    /** Threads awaiting a reply from this caller's side. */
    @Transactional(readOnly = true)
    public List<ComplaintQuery> getAwaitingMyResponse(RequestIdentity identity, String entityCode) {
        return identity.isRe()
                ? queryRepository.findByEntityCodeAndPendingWithAndStatusOrderByRaisedAtAsc(
                        entityCode, ComplaintQuery.SIDE_RE, ComplaintQuery.STATUS_OPEN)
                : queryRepository.findByPendingWithAndStatusOrderByRaisedAtAsc(
                        ComplaintQuery.SIDE_RBI, ComplaintQuery.STATUS_OPEN);
    }

    /**
     * The count behind both the "awaiting my response" filter and the query bell badge. Both read
     * this one method so the two numbers cannot drift apart.
     */
    @Transactional(readOnly = true)
    public long countAwaitingMyResponse(RequestIdentity identity, String entityCode) {
        return identity.isRe()
                ? queryRepository.countByEntityCodeAndPendingWithAndStatus(
                        entityCode, ComplaintQuery.SIDE_RE, ComplaintQuery.STATUS_OPEN)
                : queryRepository.countByPendingWithAndStatus(
                        ComplaintQuery.SIDE_RBI, ComplaintQuery.STATUS_OPEN);
    }

    /** Complaint ids carrying a thread awaiting this side — lets a list view badge rows in one query. */
    @Transactional(readOnly = true)
    public Set<Long> getComplaintIdsAwaitingResponse(RequestIdentity identity, String entityCode) {
        String side = identity.isRe() ? ComplaintQuery.SIDE_RE : ComplaintQuery.SIDE_RBI;
        return new HashSet<>(queryRepository.findComplaintIdsPendingWith(
                side, ComplaintQuery.STATUS_OPEN, identity.isRe() ? entityCode : null));
    }

    /** True when the thread has messages the user has not yet seen. */
    @Transactional(readOnly = true)
    public boolean isUnreadFor(Long queryId, String userId) {
        Long newest = messageRepository.findMaxIdByQueryId(queryId);
        if (newest == null) {
            return false;
        }
        return readRepository.findByQueryIdAndUserId(queryId, userId)
                .map(r -> r.getLastReadMessageId() == null || r.getLastReadMessageId() < newest)
                .orElse(true);
    }

    @Transactional
    public void markThreadRead(Long queryId, String userId) {
        Long newest = messageRepository.findMaxIdByQueryId(queryId);
        ComplaintQueryRead receipt = readRepository.findByQueryIdAndUserId(queryId, userId)
                .orElseGet(() -> ComplaintQueryRead.builder()
                        .queryId(queryId)
                        .userId(userId)
                        .build());
        receipt.setLastReadMessageId(newest);
        receipt.setLastReadAt(LocalDateTime.now());
        readRepository.save(receipt);
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private Complaint requireComplaint(String complaintNumber, String entityCode) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));
        // entityCode is null for RBI callers, who are not scoped to a single entity.
        if (entityCode != null && !entityCode.equals(complaint.getEntityCode())) {
            throw new SecurityException("Access denied: complaint does not belong to entity " + entityCode);
        }
        return complaint;
    }

    private ComplaintQuery requireQuery(Long queryId, String entityCode) {
        ComplaintQuery query = queryRepository.findById(queryId)
                .orElseThrow(() -> new NoSuchElementException("Query not found: " + queryId));
        if (entityCode != null && !entityCode.equals(query.getEntityCode())) {
            throw new SecurityException("Access denied: query does not belong to entity " + entityCode);
        }
        return query;
    }

    private void recordTimeline(Complaint complaint, String action, RequestIdentity identity, String remarks) {
        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action(action.length() > 50 ? action.substring(0, 50) : action)
                .performedBy(identity.getUserId())
                .remarks(remarks)
                .fromStatus(complaint.getStatus())
                .toStatus(complaint.getStatus())
                .build());
    }

    private void notifyCounterparty(Complaint complaint, ComplaintQuery query,
                                    RequestIdentity identity, String titleKey) {
        // The recipient is the other side; for RE-raised threads the RBI owner is the assignee.
        String target = identity.isRe() ? complaint.getAssignedOfficer() : query.getRaisedByUserId();
        if (target == null || target.isBlank()) {
            log.debug("No notification target for query {} on complaint {}",
                    query.getId(), complaint.getComplaintNumber());
            return;
        }
        notificationService.send(
                target,
                "QUERY",
                titleKey,
                query.getSubject(),
                complaint.getComplaintNumber(),
                "COMPLAINT",
                "/complaints/" + complaint.getComplaintNumber() + "/queries/" + query.getId());
    }

    private int intConfig(String key, int fallback) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        log.warn("Config {} is not a number: {} — using {}", key, v, fallback);
                        return fallback;
                    }
                })
                .orElse(fallback);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Request payloads
    // ═══════════════════════════════════════════════════════════════

    @lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
    public static class NewQueryPayload {
        private LocalDateTime proposedDeadline;
        private String extensionReason;
        private String meetingPurpose;
        private List<SlotPayload> proposedSlots;
        private List<ChecklistItemPayload> checklistItems;
    }

    @lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
    public static class SlotPayload {
        private LocalDateTime start;
        private LocalDateTime end;
    }

    @lombok.Getter @lombok.Setter @lombok.NoArgsConstructor
    public static class ChecklistItemPayload {
        private String label;
        private String description;
    }
}
