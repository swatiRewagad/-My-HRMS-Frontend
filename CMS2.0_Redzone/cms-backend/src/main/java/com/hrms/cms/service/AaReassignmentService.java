package com.hrms.cms.service;

import com.hrms.cms.dto.AaAssignmentRequest;
import com.hrms.cms.dto.AaAssignmentResult;
import com.hrms.cms.entity.AaAssignmentRecord;
import com.hrms.cms.entity.AaReassignmentRequest;
import com.hrms.cms.repository.AaReassignmentRequestRepository;
import com.hrms.cms.repository.AaWorkloadRepository;
import com.hrms.cms.security.AaIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Story 11: an AA DO or Reviewer asks for a record to be reassigned; an AA Admin approves or rejects
 * it, or a config rule approves it on raise. The requester is told the outcome either way.
 *
 * Forked from the RE reassignment service rather than calling it. That class is 557 lines bound to
 * NodalOfficerRecord and EntityUser, scopes every query by entityCode, and guards on RE_PNO / RE_ADMIN;
 * appeals have none of those. Its state machine and its configurable-approval idea are reproduced here,
 * over appeal numbers and AA roles, so the RE flows (UST838-845) keep working untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaReassignmentService {

    /** When false, raising a request applies it immediately instead of queueing for an admin. */
    static final String CFG_REQUIRE_APPROVAL = "cms.aa.reassign.require_admin_approval";

    private final AaReassignmentRequestRepository requestRepository;
    private final AaWorkloadRepository workloadRepository;
    private final AaAssignmentEngine assignmentEngine;
    private final SystemConfigService systemConfigService;
    private final AaIdentityResolver identityResolver;
    private final NotificationService notificationService;

    /**
     * Raises a request to move {@code appealNumber} off its current holder.
     *
     * The requester must actually hold the record. Without that check any officer could push work off a
     * colleague's queue, which is a different operation with different authority (that is the admin's
     * manual assignment).
     */
    @Transactional
    public Map<String, Object> raise(String appealNumber, String toUserId, String reason) {
        if (isBlank(appealNumber)) {
            throw new IllegalArgumentException("aa.reassign.error_appeal_required");
        }
        if (isBlank(reason)) {
            throw new IllegalArgumentException("aa.reassign.error_reason_required");
        }

        String actor = identityResolver.resolveActor();
        String actorRole = identityResolver.resolveAaRole();
        if (isBlank(actor)) {
            throw new IllegalArgumentException("aa.reassign.error_identity_unresolved");
        }

        AaAssignmentRecord held = workloadRepository
                .findByAppealNumberAndReleasedAtIsNull(appealNumber.trim())
                .orElseThrow(() -> new IllegalArgumentException("aa.reassign.error_not_assigned"));

        boolean isAdmin = "AA_ADMIN".equals(actorRole);
        if (!isAdmin && !held.getAssignedUserId().equals(actor)) {
            throw new SecurityException("aa.reassign.error_not_holder");
        }

        // A second open request for the same record would let two admins decide it independently and
        // move it twice.
        requestRepository.findFirstByAppealNumberAndStatus(
                        appealNumber.trim(), AaReassignmentRequest.STATUS_PENDING)
                .ifPresent(existing -> {
                    throw new IllegalStateException("aa.reassign.error_already_pending");
                });

        AaReassignmentRequest request = AaReassignmentRequest.builder()
                .appealNumber(appealNumber.trim())
                .roleGroup(held.getRoleGroup())
                .fromUserId(held.getAssignedUserId())
                .toUserId(isBlank(toUserId) ? null : toUserId.trim())
                .reason(reason.trim())
                .status(AaReassignmentRequest.STATUS_PENDING)
                .requestedBy(actor)
                .requestedByRole(actorRole)
                .requestedAt(LocalDateTime.now())
                .autoApproved(false)
                .build();
        requestRepository.save(request);

        boolean requireApproval = systemConfigService.getBoolean(CFG_REQUIRE_APPROVAL, true);
        if (!requireApproval) {
            // Auto-approval per the configured rule. The row is still written first, so an
            // auto-approved move is as traceable as a decided one.
            request.setAutoApproved(true);
            // deciderIsAdmin is false: no admin adjudicated this, so a requester-named destination must
            // not be honoured here even though the request itself is approved.
            applyDecision(request, true, "auto-approved: admin approval not required by configuration",
                    "SYSTEM", false);
            return describe(request, "aa.reassign.auto_approved");
        }

        notifyAdmins(request);
        return describe(request, "aa.reassign.submitted");
    }

    /** AA Admin decides a pending request. */
    @Transactional
    public Map<String, Object> decide(Long requestId, boolean approve, String comment) {
        AaReassignmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("aa.reassign.error_request_not_found"));
        if (!request.isPending()) {
            throw new IllegalStateException("aa.reassign.error_not_pending");
        }
        String actor = identityResolver.resolveActor();
        // Only the endpoint guard admits AA_ADMIN/ADMIN here, but the flag is derived from the token
        // rather than assumed from the route, so the rule holds if the guard is ever widened.
        boolean deciderIsAdmin = identityResolver.resolveRoles().stream()
                .anyMatch(role -> "AA_ADMIN".equals(role) || "ADMIN".equals(role));
        applyDecision(request, approve, comment, actor, deciderIsAdmin);
        return describe(request, approve ? "aa.reassign.approved" : "aa.reassign.rejected");
    }

    /** The requester withdraws their own request. */
    @Transactional
    public Map<String, Object> withdraw(Long requestId) {
        AaReassignmentRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("aa.reassign.error_request_not_found"));
        String actor = identityResolver.resolveActor();

        // Withdrawal is the requester's own action. An admin wanting to end it rejects instead, which
        // records a decision rather than erasing the request.
        if (!request.getRequestedBy().equals(actor)) {
            throw new SecurityException("aa.reassign.error_not_requester");
        }
        if (!request.isPending()) {
            throw new IllegalStateException("aa.reassign.error_not_pending");
        }

        request.setStatus(AaReassignmentRequest.STATUS_WITHDRAWN);
        request.setDecidedBy(actor);
        request.setDecidedAt(LocalDateTime.now());
        requestRepository.save(request);
        return describe(request, "aa.reassign.withdrawn");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> myRequests() {
        String actor = identityResolver.resolveActor();
        return requestRepository.findByRequestedByOrderByRequestedAtDescIdDesc(actor).stream()
                .map(r -> describe(r, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingQueue() {
        return requestRepository
                .findByStatusOrderByRequestedAtAscIdAsc(AaReassignmentRequest.STATUS_PENDING).stream()
                .map(r -> describe(r, null))
                .toList();
    }

    /**
     * Applies an approve/reject decision.
     *
     * On approval the record goes back through the engine, so the threshold and eligibility rules still
     * apply.
     *
     * A destination named by the requester is honoured ONLY when an AA_ADMIN is deciding. Honouring it
     * unconditionally was a privilege escalation: with auto-approval configured on, a plain AA_DO could
     * name a colleague and obtain an admin-only, threshold-bypassing placement, audited as though the
     * requester were the admin. Without an admin decider the request is satisfied through ordinary
     * assignment instead, which respects the pool rules.
     *
     * @param deciderIsAdmin whether the acting decider holds AA_ADMIN. Passed in rather than re-derived
     *                       so the auto-approval path can state plainly that nobody adjudicated it.
     */
    private void applyDecision(AaReassignmentRequest request, boolean approve, String comment,
                               String actor, boolean deciderIsAdmin) {
        request.setStatus(approve
                ? AaReassignmentRequest.STATUS_APPROVED
                : AaReassignmentRequest.STATUS_REJECTED);
        request.setDecidedBy(isBlank(actor) ? "SYSTEM" : actor);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionComment(comment);

        if (approve) {
            if (!isBlank(request.getToUserId()) && deciderIsAdmin) {
                AaAssignmentResult manual = assignmentEngine.assignManually(
                        request.getAppealNumber(), request.getToUserId(),
                        "reassignment request #" + request.getId() + ": " + request.getReason());
                request.setResolvedToUserId(manual.getAssignedUserId());
            } else {
                if (!isBlank(request.getToUserId())) {
                    log.info("AA reassignment #{}: requested destination {} ignored because the decider "
                                    + "is not an AA_ADMIN; falling back to ordinary assignment",
                            request.getId(), request.getToUserId());
                }
                AaAssignmentResult result = assignmentEngine.assign(AaAssignmentRequest.builder()
                        .appealNumber(request.getAppealNumber())
                        .roleGroup(request.getRoleGroup())
                        .build());
                request.setResolvedToUserId(result.getAssignedUserId());

                // An exhausted pool can hand the record straight back to the same officer. Reporting
                // that as a completed reassignment would tell the requester their problem was solved
                // when nothing moved.
                if (!result.isAssigned()
                        || request.getFromUserId().equals(result.getAssignedUserId())) {
                    log.warn("AA reassignment #{} approved but {} could not be moved: {}",
                            request.getId(), request.getAppealNumber(), result.getOutcome());
                }
            }
        }
        requestRepository.save(request);
        notifyRequester(request, approve);
    }

    /** Story 11's tail: the requester is told the outcome, whoever decided it. */
    private void notifyRequester(AaReassignmentRequest request, boolean approved) {
        try {
            notificationService.send(request.getRequestedBy(), "REASSIGNMENT",
                    approved ? "aa.reassign.notify_approved" : "aa.reassign.notify_rejected",
                    request.getAppealNumber(), request.getAppealNumber(), "APPEAL",
                    "/aa/reassignments");
        } catch (Exception e) {
            log.warn("AA reassignment: could not notify requester {}: {}",
                    request.getRequestedBy(), e.getMessage());
        }
    }

    private void notifyAdmins(AaReassignmentRequest request) {
        try {
            notificationService.send("AA_ADMIN", "REASSIGNMENT", "aa.reassign.notify_pending_approval",
                    request.getAppealNumber(), request.getAppealNumber(), "APPEAL", "/aa/admin");
        } catch (Exception e) {
            log.warn("AA reassignment: could not alert AA_ADMIN for #{}: {}",
                    request.getId(), e.getMessage());
        }
    }

    private Map<String, Object> describe(AaReassignmentRequest request, String messageKey) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", request.getId());
        out.put("appealNumber", request.getAppealNumber());
        out.put("roleGroup", request.getRoleGroup());
        out.put("fromUserId", request.getFromUserId());
        out.put("toUserId", request.getToUserId());
        out.put("resolvedToUserId", request.getResolvedToUserId());
        out.put("reason", request.getReason());
        out.put("status", request.getStatus());
        out.put("requestedBy", request.getRequestedBy());
        out.put("requestedByRole", request.getRequestedByRole());
        out.put("requestedAt", request.getRequestedAt());
        out.put("decidedBy", request.getDecidedBy());
        out.put("decidedAt", request.getDecidedAt());
        out.put("decisionComment", request.getDecisionComment());
        out.put("autoApproved", request.isAutoApproved());
        if (messageKey != null) {
            out.put("messageKey", messageKey);
        }
        return out;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
