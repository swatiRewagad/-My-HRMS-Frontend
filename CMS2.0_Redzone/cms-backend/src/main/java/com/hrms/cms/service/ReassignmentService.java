package com.hrms.cms.service;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import com.hrms.cms.security.RequestIdentity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * RE nodal-officer reassignment: candidates, requests, decisions and conflict-safe bulk moves
 * (UST838–UST843).
 *
 * <p>Every method takes a {@link RequestIdentity} resolved server-side by the controller. Nothing in
 * a request body decides who the caller is, which entity they belong to, or whether they may act —
 * an RE caller who could name their own entity code would be able to reassign another entity's
 * records.
 *
 * <p>Scoping rule, applied here rather than in the browser: an RE caller is pinned to
 * {@code identity.getEntityCode()}; an RBI/ADMIN caller may pass an explicit entity code and is
 * refused if they do not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReassignmentService {

    static final String CFG_BULK_MAX = "cms.reassign.bulk.max_records";
    static final String CFG_PAGE_SIZE = "cms.reassign.page_size.default";
    static final String CFG_PAGE_SIZE_MAX = "cms.reassign.page_size.max";
    static final String CFG_REASON_MIN = "cms.reassign.reason.min_length";
    static final String CFG_REQUIRE_APPROVAL = "cms.reassign.require_pno_approval";

    private static final int FALLBACK_BULK_MAX = 50;
    private static final int FALLBACK_PAGE_SIZE = 20;
    private static final int FALLBACK_PAGE_SIZE_MAX = 100;
    private static final int FALLBACK_REASON_MIN = 10;

    private final NodalOfficerRecordRepository recordRepository;
    private final EntityUserRepository entityUserRepository;
    private final ReassignmentRequestRepository requestRepository;
    private final ReassignmentClarificationRepository clarificationRepository;
    private final ReassignmentHistoryRepository historyRepository;
    private final ReassignmentExecutor executor;
    private final WorkloadService workloadService;
    private final SystemConfigService systemConfigService;

    // ═══════════════════════════════════════════════════════════════
    // Candidates (UST841)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Officers who may receive a record, each with the workload figure from {@link WorkloadService}
     * so the popup and the dashboard cannot disagree (UST838).
     *
     * <p>Filters applied, all server-side: same entity, active only, and the current owner excluded —
     * offering to reassign a record to the person who already holds it is not a choice, and allowing
     * it would create a history row recording a move that did not happen.
     *
     * @param role optional narrowing to NODAL_OFFICER / CONTACT_PERSON / PNO; null means all roles
     */
    @Transactional(readOnly = true)
    public List<Candidate> findCandidates(RequestIdentity identity, String entityCode,
                                          Long excludeRecordId, String role, String search) {
        String scope = resolveScope(identity, entityCode);

        List<EntityUser> users = (role == null || role.isBlank())
                ? entityUserRepository.findByEntityCodeAndActiveTrueOrderByDisplayNameAsc(scope)
                : entityUserRepository.findByEntityCodeAndReRoleAndActiveTrueOrderByDisplayNameAsc(
                        scope, role.trim().toUpperCase());

        String currentOwner = null;
        if (excludeRecordId != null) {
            currentOwner = recordRepository.findById(excludeRecordId)
                    .filter(r -> scope.equals(r.getEntityCode()))
                    .map(NodalOfficerRecord::getAssignedTo)
                    .orElse(null);
        }

        Map<String, Integer> workloads = workloadService.workloadForEntity(scope);
        String needle = (search == null || search.isBlank()) ? null : search.trim().toLowerCase();

        List<Candidate> candidates = new ArrayList<>();
        for (EntityUser user : users) {
            if (currentOwner != null && currentOwner.equals(user.getUserId())) {
                continue;
            }
            if (needle != null && !matches(user, needle)) {
                continue;
            }
            Candidate c = new Candidate();
            c.setUserId(user.getUserId());
            c.setDisplayName(user.getDisplayName());
            c.setEmail(user.getEmail());
            c.setDesignation(user.getDesignation());
            c.setReRole(user.getReRole());
            c.setTerritory(user.getTerritory());
            c.setWorkload(workloads.getOrDefault(user.getUserId(), 0));
            candidates.add(c);
        }
        return candidates;
    }

    private boolean matches(EntityUser user, String needle) {
        return contains(user.getDisplayName(), needle)
            || contains(user.getUserId(), needle)
            || contains(user.getEmail(), needle)
            || contains(user.getDesignation(), needle);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    // ═══════════════════════════════════════════════════════════════
    // Raising requests (UST840, UST842)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Raises a reassignment request, or performs the move immediately when the caller is a PNO and
     * {@code cms.reassign.require_pno_approval} does not demand a second pair of eyes.
     *
     * <p>The reason is validated here and then never editable — {@link ReassignmentRequest#getReason}
     * is mapped {@code updatable = false} (UST840).
     */
    @Transactional
    public RequestOutcome raiseRequest(RequestIdentity identity, String entityCode,
                                       Long recordId, Long expectedVersion,
                                       String toUserId, String reason) {
        String scope = resolveScope(identity, entityCode);

        if (toUserId == null || toUserId.isBlank()) {
            throw new IllegalArgumentException("re.reassign.error.target_required");
        }
        int minLength = systemConfigService.getInt(CFG_REASON_MIN, FALLBACK_REASON_MIN);
        if (reason == null || reason.trim().length() < minLength) {
            throw new IllegalArgumentException("re.reassign.error.reason_too_short");
        }

        NodalOfficerRecord record = requireRecord(recordId, scope);
        EntityUser target = requireActiveTarget(scope, toUserId);

        if (toUserId.equals(record.getAssignedTo())) {
            throw new IllegalArgumentException("re.reassign.error.already_assigned");
        }
        if (!requestRepository.findByNodalOfficerRecordIdAndStatus(
                recordId, ReassignmentRequest.STATUS_PENDING).isEmpty()) {
            throw new ConflictingStateException("re.reassign.error.already_pending");
        }

        int workloadNow = workloadService.workloadFor(scope, toUserId);

        ReassignmentRequest request = requestRepository.save(ReassignmentRequest.builder()
                .nodalOfficerRecordId(recordId)
                .complaintNumber(record.getComplaintNumber())
                .entityCode(scope)
                .fromUserId(record.getAssignedTo())
                .fromUserName(record.getNodalOfficerName())
                .toUserId(toUserId)
                .toUserName(target.getDisplayName())
                .reason(reason.trim())
                .status(ReassignmentRequest.STATUS_PENDING)
                .requestedBy(identity.getUserId())
                .requestedByName(identity.getDisplayName())
                .toUserWorkloadAtRequest(workloadNow)
                .build());

        RequestOutcome outcome = new RequestOutcome();
        outcome.setRequest(request);

        // A PNO moving a record inside their own entity is the approver, so requiring them to
        // approve their own request would be a formality with no independent review in it. The
        // request row is still written first, so the audit trail shows the same shape either way.
        boolean requireApproval = systemConfigService.getBoolean(CFG_REQUIRE_APPROVAL, true);
        if (isPno(identity) && !requireApproval) {
            executor.apply(recordId, expectedVersion, toUserId, target.getDisplayName(),
                           request.getReason(), ReassignmentHistory.TRIGGER_DIRECT,
                           request.getId(), identity.getUserId(), identity.getDisplayName());
            request.setStatus(ReassignmentRequest.STATUS_APPROVED);
            request.setDecidedBy(identity.getUserId());
            request.setDecidedAt(java.time.LocalDateTime.now());
            requestRepository.save(request);
            outcome.setApplied(true);
        }
        return outcome;
    }

    /** Append-only clarification on an existing request (UST840). */
    @Transactional
    public ReassignmentClarification addClarification(RequestIdentity identity, String entityCode,
                                                      Long requestId, String note) {
        String scope = resolveScope(identity, entityCode);
        if (note == null || note.trim().isEmpty()) {
            throw new IllegalArgumentException("re.reassign.error.clarification_required");
        }
        ReassignmentRequest request = requireRequest(requestId, scope);

        return clarificationRepository.save(ReassignmentClarification.builder()
                .reassignmentRequestId(request.getId())
                .note(note.trim())
                .addedBy(identity.getUserId())
                .addedByName(identity.getDisplayName())
                .addedBySide(identity.getSide())
                .build());
    }

    @Transactional(readOnly = true)
    public List<ReassignmentClarification> getClarifications(RequestIdentity identity,
                                                            String entityCode, Long requestId) {
        String scope = resolveScope(identity, entityCode);
        requireRequest(requestId, scope);
        return clarificationRepository.findByReassignmentRequestIdOrderByAddedAtAsc(requestId);
    }

    /** UST842 — the caller's own requests. Scoped by user id, not by a client-supplied filter. */
    @Transactional(readOnly = true)
    public Page<ReassignmentRequest> myRequests(RequestIdentity identity, String status,
                                                int page, int size) {
        int pageSize = clampPageSize(size);
        if (status == null || status.isBlank()) {
            return requestRepository.findByRequestedByOrderByRequestedAtDesc(
                    identity.getUserId(), PageRequest.of(Math.max(page, 0), pageSize));
        }
        return requestRepository.findByRequestedByAndStatusOrderByRequestedAtDesc(
                identity.getUserId(), status.trim().toUpperCase(),
                PageRequest.of(Math.max(page, 0), pageSize));
    }

    /** UST843 — the PNO approval queue for the caller's entity. */
    @Transactional(readOnly = true)
    public Page<ReassignmentRequest> pendingApprovals(RequestIdentity identity, String entityCode,
                                                      int page, int size) {
        String scope = resolveScope(identity, entityCode);
        requirePno(identity);
        return requestRepository.findByEntityCodeAndStatusOrderByRequestedAtAsc(
                scope, ReassignmentRequest.STATUS_PENDING,
                PageRequest.of(Math.max(page, 0), clampPageSize(size)));
    }

    /** Withdrawing your own request. Not a rejection — no independent review took place. */
    @Transactional
    public ReassignmentRequest withdraw(RequestIdentity identity, String entityCode,
                                        Long requestId, String note) {
        String scope = resolveScope(identity, entityCode);
        ReassignmentRequest request = requireRequest(requestId, scope);

        if (!identity.getUserId().equals(request.getRequestedBy())) {
            throw new SecurityException("re.reassign.error.not_your_request");
        }
        if (!ReassignmentRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new ConflictingStateException("re.reassign.error.already_decided");
        }
        request.setStatus(ReassignmentRequest.STATUS_WITHDRAWN);
        request.setDecidedBy(identity.getUserId());
        request.setDecidedAt(java.time.LocalDateTime.now());
        request.setDecisionComment(note);
        return requestRepository.save(request);
    }

    // ═══════════════════════════════════════════════════════════════
    // Decisions and bulk actions (UST839, UST843)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Approves or rejects a set of requests, or applies a set of direct moves, isolating each item so
     * one conflict does not discard the rest (UST839).
     *
     * <p>The whole method is deliberately NOT transactional: each item is committed independently by
     * {@link ReassignmentExecutor}. A surrounding transaction would defeat that — the first JPA
     * exception would mark it rollback-only and the successful items would be discarded at commit
     * while the response claimed they succeeded.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkResult decideBulk(RequestIdentity identity, String entityCode,
                                 List<BulkItem> items, boolean approve, String comment) {
        String scope = resolveScope(identity, entityCode);
        requirePno(identity);

        BulkResult result = new BulkResult();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("re.reassign.error.nothing_selected");
        }
        int max = systemConfigService.getInt(CFG_BULK_MAX, FALLBACK_BULK_MAX);
        if (items.size() > max) {
            throw new IllegalArgumentException("re.reassign.error.bulk_limit_exceeded");
        }
        if (!approve && (comment == null || comment.trim().isEmpty())) {
            // A refusal the requester cannot understand is not reviewable.
            throw new IllegalArgumentException("re.reassign.error.rejection_comment_required");
        }

        for (BulkItem item : items) {
            try {
                if (approve) {
                    approveOne(identity, scope, item, comment);
                } else {
                    rejectOne(identity, scope, item, comment);
                }
                result.getSucceeded().add(item.getRequestId());
            } catch (ReassignmentExecutor.ConflictException e) {
                result.addFailure(item.getRequestId(), "re.reassign.error.conflict", e.getMessage());
                log.warn("Reassignment conflict on request {} by {}: {}",
                         item.getRequestId(), identity.getUserId(), e.getMessage());
            } catch (ConflictingStateException e) {
                result.addFailure(item.getRequestId(), e.getMessage(), e.getMessage());
                log.warn("Reassignment state conflict on request {}: {}",
                         item.getRequestId(), e.getMessage());
            } catch (SecurityException e) {
                result.addFailure(item.getRequestId(), "re.reassign.error.not_permitted", e.getMessage());
                log.warn("Reassignment denied on request {} for {}: {}",
                         item.getRequestId(), identity.getUserId(), e.getMessage());
            } catch (NoSuchElementException e) {
                result.addFailure(item.getRequestId(), "re.reassign.error.not_found", e.getMessage());
            } catch (IllegalArgumentException e) {
                result.addFailure(item.getRequestId(), e.getMessage(), e.getMessage());
            } catch (Exception e) {
                result.addFailure(item.getRequestId(), "re.reassign.error.unexpected", e.getMessage());
                log.error("Unexpected failure reassigning request {}: {}",
                          item.getRequestId(), e.getMessage(), e);
            }
        }
        return result;
    }

    private void approveOne(RequestIdentity identity, String scope, BulkItem item, String comment) {
        ReassignmentRequest request = requireRequest(item.getRequestId(), scope);
        if (!ReassignmentRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new ConflictingStateException("re.reassign.error.already_decided");
        }
        executor.apply(request.getNodalOfficerRecordId(),
                       item.getExpectedVersion(),
                       request.getToUserId(),
                       request.getToUserName(),
                       request.getReason(),
                       ReassignmentHistory.TRIGGER_APPROVED_REQUEST,
                       request.getId(),
                       identity.getUserId(),
                       identity.getDisplayName());

        request.setStatus(ReassignmentRequest.STATUS_APPROVED);
        request.setDecidedBy(identity.getUserId());
        request.setDecidedAt(java.time.LocalDateTime.now());
        request.setDecisionComment(comment);
        requestRepository.save(request);
    }

    private void rejectOne(RequestIdentity identity, String scope, BulkItem item, String comment) {
        ReassignmentRequest request = requireRequest(item.getRequestId(), scope);
        if (!ReassignmentRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new ConflictingStateException("re.reassign.error.already_decided");
        }
        request.setStatus(ReassignmentRequest.STATUS_REJECTED);
        request.setDecidedBy(identity.getUserId());
        request.setDecidedAt(java.time.LocalDateTime.now());
        request.setDecisionComment(comment);
        requestRepository.save(request);
    }

    /**
     * Direct moves by a PNO, one transaction per record (UST839). Same isolation rationale as
     * {@link #decideBulk}.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkResult reassignBulk(RequestIdentity identity, String entityCode,
                                   List<BulkItem> items, String toUserId, String reason) {
        String scope = resolveScope(identity, entityCode);
        requirePno(identity);

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("re.reassign.error.nothing_selected");
        }
        int max = systemConfigService.getInt(CFG_BULK_MAX, FALLBACK_BULK_MAX);
        if (items.size() > max) {
            throw new IllegalArgumentException("re.reassign.error.bulk_limit_exceeded");
        }
        int minLength = systemConfigService.getInt(CFG_REASON_MIN, FALLBACK_REASON_MIN);
        if (reason == null || reason.trim().length() < minLength) {
            throw new IllegalArgumentException("re.reassign.error.reason_too_short");
        }
        EntityUser target = requireActiveTarget(scope, toUserId);

        BulkResult result = new BulkResult();
        for (BulkItem item : items) {
            try {
                requireRecord(item.getRecordId(), scope);
                executor.apply(item.getRecordId(), item.getExpectedVersion(), toUserId,
                               target.getDisplayName(), reason.trim(),
                               ReassignmentHistory.TRIGGER_DIRECT, null,
                               identity.getUserId(), identity.getDisplayName());
                result.getSucceeded().add(item.getRecordId());
            } catch (ReassignmentExecutor.ConflictException e) {
                result.addFailure(item.getRecordId(), "re.reassign.error.conflict", e.getMessage());
                log.warn("Reassignment conflict on record {} by {}: {}",
                         item.getRecordId(), identity.getUserId(), e.getMessage());
            } catch (SecurityException e) {
                result.addFailure(item.getRecordId(), "re.reassign.error.not_permitted", e.getMessage());
            } catch (NoSuchElementException e) {
                result.addFailure(item.getRecordId(), "re.reassign.error.not_found", e.getMessage());
            } catch (IllegalArgumentException e) {
                result.addFailure(item.getRecordId(), e.getMessage(), e.getMessage());
            } catch (Exception e) {
                result.addFailure(item.getRecordId(), "re.reassign.error.unexpected", e.getMessage());
                log.error("Unexpected failure reassigning record {}: {}",
                          item.getRecordId(), e.getMessage(), e);
            }
        }
        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    // Scoping and guards — all server-side
    // ═══════════════════════════════════════════════════════════════

    /**
     * The entity this call operates on.
     *
     * <p>An RE caller is pinned to the code the resolver derived (JWT claim preferred over header)
     * and cannot widen it; a request-supplied code is refused outright rather than ignored, because
     * silently ignoring it would let a caller believe they had scoped a query that they had not.
     * RBI/ADMIN callers must name an entity — defaulting them to "all entities" on a write path is
     * how a bulk action ends up crossing entity boundaries.
     */
    public String resolveScope(RequestIdentity identity, String requestedEntityCode) {
        if (identity.isRe()) {
            String own = identity.getEntityCode();
            if (own == null || own.isBlank()) {
                throw new SecurityException("re.reassign.error.entity_unresolved");
            }
            if (requestedEntityCode != null && !requestedEntityCode.isBlank()
                    && !own.equals(requestedEntityCode.trim())) {
                throw new SecurityException("re.reassign.error.cross_entity");
            }
            return own;
        }
        if (!identity.hasAnyRole("ADMIN")) {
            throw new SecurityException("re.reassign.error.not_permitted");
        }
        if (requestedEntityCode == null || requestedEntityCode.isBlank()) {
            throw new IllegalArgumentException("re.reassign.error.entity_required");
        }
        return requestedEntityCode.trim();
    }

    /** PNO or ADMIN. RE_PNO is the realm role; PNO is the directory role for entity-modelled users. */
    private void requirePno(RequestIdentity identity) {
        if (identity.hasAnyRole("RE_PNO", "RE_ADMIN", "ADMIN")) {
            return;
        }
        throw new SecurityException("re.reassign.error.pno_only");
    }

    private boolean isPno(RequestIdentity identity) {
        return identity.hasAnyRole("RE_PNO", "RE_ADMIN", "ADMIN");
    }

    private NodalOfficerRecord requireRecord(Long recordId, String scope) {
        NodalOfficerRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new NoSuchElementException("Record " + recordId + " not found"));
        // A record with no entity code predates the column and cannot be proven to belong to this
        // caller, so it is refused rather than assumed to be theirs.
        if (record.getEntityCode() == null || !record.getEntityCode().equals(scope)) {
            throw new SecurityException("re.reassign.error.cross_entity");
        }
        return record;
    }

    private ReassignmentRequest requireRequest(Long requestId, String scope) {
        ReassignmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Request " + requestId + " not found"));
        if (!scope.equals(request.getEntityCode())) {
            throw new SecurityException("re.reassign.error.cross_entity");
        }
        return request;
    }

    private EntityUser requireActiveTarget(String scope, String toUserId) {
        EntityUser target = entityUserRepository.findByUserIdAndEntityCode(toUserId, scope)
                .orElseThrow(() -> new IllegalArgumentException("re.reassign.error.target_not_in_entity"));
        if (!target.isActive()) {
            throw new IllegalArgumentException("re.reassign.error.target_inactive");
        }
        return target;
    }

    public int clampPageSize(int requested) {
        int fallback = systemConfigService.getInt(CFG_PAGE_SIZE, FALLBACK_PAGE_SIZE);
        int max = systemConfigService.getInt(CFG_PAGE_SIZE_MAX, FALLBACK_PAGE_SIZE_MAX);
        if (requested <= 0) {
            return Math.min(fallback, max);
        }
        return Math.min(requested, max);
    }

    // ═══════════════════════════════════════════════════════════════
    // Payloads
    // ═══════════════════════════════════════════════════════════════

    /** Raised when the target is in a state that forbids the action; surfaced as HTTP 409. */
    public static class ConflictingStateException extends RuntimeException {
        public ConflictingStateException(String message) {
            super(message);
        }
    }

    @Getter @Setter
    public static class Candidate {
        private String userId;
        private String displayName;
        private String email;
        private String designation;
        private String reRole;
        private String territory;
        private int workload;
    }

    @Getter @Setter
    public static class BulkItem {
        private Long requestId;
        private Long recordId;
        /** The version the client read; null means "no prior read to protect". */
        private Long expectedVersion;
    }

    @Getter @Setter
    public static class RequestOutcome {
        private ReassignmentRequest request;
        /** True when the move was applied immediately rather than queued for approval. */
        private boolean applied;
    }

    /**
     * Per-item outcome of a bulk action. Failures name the item and carry a translation key, because
     * UST839 requires the response to say which records failed and why — a bare count would leave the
     * user unable to tell which of their selections took effect.
     */
    @Getter
    public static class BulkResult {
        private final List<Long> succeeded = new ArrayList<>();
        private final List<Map<String, Object>> failed = new ArrayList<>();

        void addFailure(Long id, String messageKey, String detail) {
            Map<String, Object> failure = new LinkedHashMap<>();
            failure.put("id", id);
            failure.put("messageKey", messageKey);
            failure.put("detail", detail);
            failed.add(failure);
        }
    }
}
