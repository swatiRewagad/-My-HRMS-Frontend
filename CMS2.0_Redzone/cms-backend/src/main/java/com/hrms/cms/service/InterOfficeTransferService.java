package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.InterOfficeTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Inter-office transfers and their CRPC Head approval (UST556-568, 632-634, 759-761, 770-772).
 *
 * <p><b>What was wrong with the approval path.</b> {@code approveTransfer} mutated exactly two complaint
 * fields, and both were wrong:
 *
 * <ol>
 *   <li>{@code setAssignedOfficer(targetOffice)} put an OFFICE CODE in a USER column. No officer was
 *       resolved, no round-robin ran, nobody at the destination was notified — so after a transfer the
 *       complaint had no real owner and "who is working on this" had no answer.</li>
 *   <li>{@code setDepartment(resolveDepartment(targetOffice))} derived the layout from a string PREFIX on the
 *       office id. Offices are keyed by a numeric OFFICE_CODE ("013"), so every real office fell through to
 *       "RBIO" and UST633's RBIO&lt;-&gt;CEPC conversion could never occur.</li>
 * </ol>
 *
 * <p>Meanwhile {@code status}, {@code workflowStage} and {@code rbioOfficeCode} were all left stale — so a
 * transferred complaint still claimed its original office (UST760's "region field updated" never happened) —
 * and NO timeline row was written at all, which {@code ComplaintTimeline}'s own javadoc records as a known
 * gap. UST634/568/565 require the conversion history to be visible to every role afterwards.
 *
 * <p><b>Capacity is now honoured.</b> {@code OfficeRoutingService.incrementOffice} returns false when the
 * destination is at its threshold, and this service was its only production caller — it discarded the result,
 * making this the one write path able to push an office past its declared capacity.
 *
 * <p><b>This is the ONE transfer mechanism.</b> {@code CepcWorkflowService}'s three external-forward arms
 * moved complaints directly, created no transfer row, and set {@code forwarded_external} where the status
 * master declares {@code sent_to_other} — so transferred complaints never appeared in the CRPC Head's queue
 * and no approval was possible. Those arms now delegate here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterOfficeTransferService {

    /** The status a complaint takes while it waits for the CRPC Head (UST557, 560). */
    private static final String STATUS_SENT_TO_OTHER = "sent_to_other";
    private static final String STAGE_SENT_TO_OTHER = "SENT_TO_OTHER_OFFICE";

    private static final String CFG_ALLOW_OVER_CAPACITY = "transfer.allow_over_capacity_override";

    private final InterOfficeTransferRepository transferRepo;
    private final ComplaintRepository complaintRepository;
    private final OfficeRoutingService officeRoutingService;
    private final NotificationService notificationService;
    private final ComplaintService complaintService;
    private final SystemConfigService systemConfigService;

    /**
     * Resolves a real Dealing Officer at the destination office (UST561, 770, 632).
     *
     * <p>Optional so this service stays constructible in unit tests without the Keycloak-backed assignment
     * stack. When absent the transfer still completes but records no owner, which is visible in the timeline
     * rather than silently wrong — see {@link #resolveDestinationOfficer}.
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private OfficeAssignmentStrategyService officeAssignmentStrategyService;

    /** Validates destinations and answers what layout an office belongs to. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ForwardTargetService forwardTargetService;

    /**
     * Custody history, so a transfer's handover is recorded on the same trail send-backs read (UST759).
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private RbioCaseAssignmentHistoryService assignmentHistoryService;

    // ══════════════════════════════════════════════════════════════════
    // Requesting
    // ══════════════════════════════════════════════════════════════════

    /** Backwards-compatible overload for callers that carry no language. */
    @Transactional
    public InterOfficeTransfer requestTransfer(String complaintNumber, String fromOffice, String toOffice,
                                               String transferType, String reason, String requestedBy) {
        return requestTransfer(complaintNumber, fromOffice, toOffice, transferType, reason, requestedBy, null);
    }

    /**
     * Records a PENDING transfer and moves the complaint to "Sent to Other Office" (UST557, 560, 564).
     *
     * <p>The complaint does NOT move to the destination here. UST564 requires the transfer to enter the CRPC
     * Head's approval queue BEFORE reaching the destination Dealing Officer, so ownership passes to the Head
     * and the destination officer is resolved only on approval.
     *
     * <p>{@code previousOwner} is captured NOW rather than at approval. That is what makes a rejection able to
     * return the complaint to the officer who held it — see the field's own comment.
     */
    @Transactional
    public InterOfficeTransfer requestTransfer(String complaintNumber, String fromOffice, String toOffice,
                                               String transferType, String reason, String requestedBy,
                                               String language) {
        if (reason == null || reason.isBlank()) {
            // UST559/556: the Reason for Transfer is mandatory before Save and Proceed. Enforced here as well
            // as at the action layer, because this method is reachable from the CRPC Head controller too.
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.reason_required: a reason for transfer is required");
        }

        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Complaint not found: " + complaintNumber));

        if (forwardTargetService != null) {
            forwardTargetService.assertOfficeExists(toOffice);
        }

        String previousStatus = complaint.getStatus();
        String previousOwner = complaint.getAssignedOfficer();
        String fromLayout = complaint.getDepartment() == null
                ? ForwardTargetService.LAYOUT_RBIO : complaint.getDepartment();
        String toLayout = forwardTargetService != null
                ? forwardTargetService.layoutForOffice(toOffice) : fromLayout;

        InterOfficeTransfer transfer = transferRepo.save(InterOfficeTransfer.builder()
                .complaintNumber(complaintNumber)
                .fromOffice(fromOffice)
                .toOffice(toOffice)
                .transferType(transferType)
                .reason(reason)
                .requestedBy(requestedBy)
                .status("PENDING")
                .previousOwner(previousOwner)
                .originModule(fromLayout)
                .fromLayout(fromLayout)
                .toLayout(toLayout)
                .fromOfficeCode(complaint.getRbioOfficeCode())
                .toOfficeCode(toOffice)
                .language(language)
                .build());

        // UST557/560: ownership transfers to the CRPC operational head on Save and Proceed and the status
        // updates immediately. sent_to_other is the legacy value RBIO_STATUS_MASTER declares for
        // SENT_TO_OTHER_OFFICE; the CEPC arms wrote forwarded_external here, which is why the CRPC queue
        // (which filters on sent_to_other) never saw a transferred complaint.
        complaint.setStatus(STATUS_SENT_TO_OTHER);
        complaint.setWorkflowStage(STAGE_SENT_TO_OTHER);
        complaintRepository.save(complaint);

        complaintService.addDetailedTimeline(complaint.getId(), "TRANSFER_REQUESTED", requestedBy, null,
                reason, previousStatus, STATUS_SENT_TO_OTHER,
                "assignedOfficer", previousOwner, "CRPC_HEAD",
                null, toOffice, TimelineEventSource.MANUAL);

        // raiseEvent expands a ROLE token to its real members. The previous code called send("CRPC_HEAD", ..),
        // which addresses a single userId — so the literal string "CRPC_HEAD" was stored as the recipient and
        // no actual Head was ever notified.
        try {
            notificationService.raiseEvent(List.of("CRPC_HEAD"), "TRANSFER_REQUEST",
                    "Inter-office transfer request",
                    "Transfer request for " + complaintNumber + " from " + fromOffice + " to " + toOffice,
                    complaintNumber, "COMPLAINT", "/crpc/ops-head", complaint);
        } catch (Exception e) {
            // The transfer is recorded either way: a notification failure must not lose the request, which
            // would leave the complaint in sent_to_other with nothing in the queue to resolve it.
            log.warn("Transfer {} recorded but the CRPC Head notification failed: {}",
                    transfer.getId(), e.getMessage());
        }

        return transfer;
    }

    // ══════════════════════════════════════════════════════════════════
    // Approving
    // ══════════════════════════════════════════════════════════════════

    /**
     * Approves a transfer: converts the layout, claims capacity, assigns a real officer and records history
     * (UST561, 632-634, 568, 565, 760, 770-772).
     *
     * <p>Ordering is deliberate. Capacity is claimed BEFORE anything is mutated, so a refusal leaves both the
     * transfer and the complaint untouched — the alternative is a half-applied transfer whose complaint has
     * moved but whose row still says PENDING.
     *
     * <p>UST632/633 require NO action by the Dealing Official or the Ombudsman for the conversion to happen:
     * everything below is performed by the approving Head in one step, and there is deliberately no
     * "Switch Process" action anywhere in the codebase for a DO to invoke.
     */
    @Transactional
    public InterOfficeTransfer approveTransfer(Long transferId, String approvedBy, String overrideToOffice) {
        InterOfficeTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transfer not found: " + transferId));

        if (!"PENDING".equals(transfer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer already resolved");
        }

        String targetOffice = (overrideToOffice != null && !overrideToOffice.isBlank())
                ? overrideToOffice.trim() : transfer.getToOffice();

        // An overridden destination is validated too. The override exists so a Head can redirect a
        // mis-addressed transfer, which is exactly the path most likely to name an office that does not exist.
        if (forwardTargetService != null) {
            forwardTargetService.assertOfficeExists(targetOffice);
        }

        // ── Capacity, before any mutation ──
        // incrementOffice returns false when the destination is at its threshold. Discarding that verdict was
        // the defect: it made this the only write path able to exceed a declared capacity.
        boolean capacityClaimed = officeRoutingService.incrementOffice(targetOffice);
        boolean overrideAllowed = systemConfigService.getBoolean(CFG_ALLOW_OVER_CAPACITY, false);

        if (!capacityClaimed && !overrideAllowed) {
            // 409: the request is well-formed but conflicts with the destination's state. The transfer stays
            // PENDING so the Head can redirect it rather than the complaint being stranded.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "rbio.forward.error.destination_at_capacity: office " + targetOffice + " is at its "
                            + "configured capacity. Redirect the transfer to another office, or have an "
                            + "administrator permit over-capacity transfers.");
        }

        Complaint complaint = complaintRepository.findByComplaintNumber(transfer.getComplaintNumber())
                .orElse(null);
        if (complaint == null) {
            // Capacity was claimed above; release it rather than leaking a slot for a complaint that no
            // longer exists.
            if (capacityClaimed) {
                officeRoutingService.decrementOffice(targetOffice);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Complaint not found: " + transfer.getComplaintNumber());
        }

        // Read BEFORE mutating — the timeline records old→new and the assignee is about to be overwritten.
        String previousOwner = complaint.getAssignedOfficer();
        String previousStatus = complaint.getStatus();
        String previousOfficeCode = complaint.getRbioOfficeCode();
        String fromLayout = complaint.getDepartment() == null
                ? ForwardTargetService.LAYOUT_RBIO : complaint.getDepartment();

        // ── The layout conversion (UST633, 632, 771, 772) ──
        String toLayout = forwardTargetService != null
                ? forwardTargetService.layoutForOffice(targetOffice)
                : fromLayout;

        // ── A real officer at the destination (UST561, 770, 632) ──
        OfficeAssignmentStrategyService.Resolution assignment = resolveDestinationOfficer(complaint, targetOffice);

        transfer.setToOffice(targetOffice);
        transfer.setToOfficeCode(targetOffice);
        transfer.setStatus("APPROVED");
        transfer.setApprovedBy(approvedBy);
        transfer.setResolvedAt(LocalDateTime.now());
        transfer.setFromLayout(fromLayout);
        transfer.setToLayout(toLayout);
        transfer.setFromOfficeCode(previousOfficeCode);
        transfer.setOverflowAccepted(capacityClaimed ? "N" : "Y");
        if (transfer.getPreviousOwner() == null) {
            transfer.setPreviousOwner(previousOwner);
        }
        if (assignment != null) {
            transfer.setAssignedOfficer(assignment.officerId());
            transfer.setAssignmentStrategy(assignment.strategy());
            transfer.setAssignmentReason(assignment.reason());
        }
        transferRepo.save(transfer);

        // ── The complaint ──
        complaint.setDepartment(toLayout);
        // UST760: the region field is updated. rbioOfficeCode IS the jurisdiction key — there is no `region`
        // column on Complaint, and this was previously left stale so a transferred complaint kept claiming
        // its original office.
        complaint.setRbioOfficeCode(targetOffice);
        // The complaint leaves the holding status the request put it in and becomes workable again at the
        // destination. Left as sent_to_other it would sit in the CRPC queue forever, already approved.
        complaint.setStatus("assigned");
        complaint.setWorkflowStage("ASSIGNED");

        if (assignment != null && assignment.isAssigned()) {
            complaint.setAssignedOfficer(assignment.officerId());
        } else {
            // Never leave an office code in the officer column. Blanking it is honest: the complaint is at the
            // destination office with no individual owner yet, which a queue can pick up. Writing the office
            // code there is what made the previous behaviour untraceable.
            complaint.setAssignedOfficer(null);
            log.warn("Transfer {} approved into {} but no officer could be resolved — the complaint is "
                    + "unassigned at the destination", transferId, targetOffice);
        }
        complaintRepository.save(complaint);

        officeRoutingService.decrementOffice(transfer.getFromOffice());

        // ── History (UST634, 568, 565) ──
        // Two rows, because two distinct facts changed and a single row can carry only one field name. The
        // layout row is what UST634 asks for: old layout, new layout, approving Head, timestamp.
        complaintService.addDetailedTimeline(complaint.getId(), "TRANSFER_APPROVED", approvedBy, "CRPC_HEAD",
                "Transfer approved: " + transfer.getFromOffice() + " → " + targetOffice
                        + (capacityClaimed ? "" : " (destination over capacity, permitted by configuration)"),
                previousStatus, complaint.getStatus(),
                "rbioOfficeCode", previousOfficeCode, targetOffice,
                null, targetOffice, TimelineEventSource.MANUAL);

        complaintService.addDetailedTimeline(complaint.getId(), "LAYOUT_CONVERTED", approvedBy, "CRPC_HEAD",
                "Complaint layout converted from " + fromLayout + " to " + toLayout
                        + " on transfer approval.",
                previousStatus, complaint.getStatus(),
                "department", fromLayout, toLayout,
                null, targetOffice, TimelineEventSource.AUTOMATIC);

        if (assignment != null && assignment.isAssigned()) {
            complaintService.addDetailedTimeline(complaint.getId(), "TRANSFER_ASSIGNED", approvedBy, "CRPC_HEAD",
                    "Assigned at the destination office by " + assignment.strategy() + ": "
                            + assignment.reason(),
                    previousStatus, complaint.getStatus(),
                    "assignedOfficer", previousOwner, assignment.officerId(),
                    null, targetOffice, TimelineEventSource.AUTOMATIC);

            if (assignmentHistoryService != null) {
                assignmentHistoryService.recordAssignment(complaint, complaint.getAssignedRole(),
                        assignment.officerId(), "TRANSFER_APPROVED", approvedBy);
            }

            notifyDestinationOfficer(complaint, assignment.officerId(), targetOffice);
        }

        log.info("Transfer {} approved: {} → {} ({} → {} layout), officer {}", transferId,
                transfer.getFromOffice(), targetOffice, fromLayout, toLayout,
                assignment != null ? assignment.officerId() : "unassigned");
        return transfer;
    }

    /**
     * An officer at the destination office, via the office's configured strategy.
     *
     * <p>Uses {@code OfficeAssignmentStrategyService}, which honours each office's ROUND_ROBIN /
     * ENTITY_MAPPING / CATEGORY_MAPPING configuration and falls back to that office's Ombudsman Admin. Its
     * pointer is durable and cluster-safe; the four in-memory counters elsewhere in this codebase are per-pod
     * and rotate differently on every instance.
     */
    private OfficeAssignmentStrategyService.Resolution resolveDestinationOfficer(Complaint complaint,
                                                                                String targetOffice) {
        if (officeAssignmentStrategyService == null) {
            return null;
        }
        try {
            // entityCode, matching how ComplaintService already calls this at registration — the
            // ENTITY_MAPPING strategy keys on the same value there.
            return officeAssignmentStrategyService.resolveOfficer(
                    targetOffice, complaint.getEntityCode(), complaint.getCategoryId());
        } catch (Exception e) {
            // Logged and treated as unassigned rather than failing the approval. The transfer is a decision
            // the Head has already taken; refusing it because an assignment lookup failed would leave the
            // complaint in the source office with an approved transfer row.
            log.warn("Could not resolve a destination officer at {} for {}: {}",
                    targetOffice, complaint.getComplaintNumber(), e.getMessage());
            return null;
        }
    }

    /** UST561/770/632: the destination Dealing Officer is notified of the new assignment. */
    private void notifyDestinationOfficer(Complaint complaint, String officerId, String targetOffice) {
        try {
            notificationService.send(officerId, "NEW_ASSIGNMENT",
                    "Complaint transferred to you",
                    "Complaint " + complaint.getComplaintNumber() + " has been transferred to office "
                            + targetOffice + " and assigned to you.",
                    complaint.getComplaintNumber(), "COMPLAINT",
                    "/workflow/rbio/complaint/" + complaint.getComplaintNumber());
        } catch (Exception e) {
            log.warn("Transfer of {} assigned to {} but the notification failed: {}",
                    complaint.getComplaintNumber(), officerId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Rejecting
    // ══════════════════════════════════════════════════════════════════

    /**
     * Rejects a transfer and returns the complaint to its previous owner (UST526, 567).
     *
     * <p>The comment is genuinely mandatory here. The controller's {@code @RequestParam String comment} makes
     * the parameter required but accepts a blank one, so "rejected with mandatory comments" was satisfiable
     * by {@code ?comment=}.
     *
     * <p>The restore now works because {@code previousOwner} is captured at REQUEST time. It was previously
     * set only inside the approval path, so on rejection it was null and the {@code != null} guard skipped the
     * restore silently.
     */
    @Transactional
    public InterOfficeTransfer rejectTransfer(Long transferId, String rejectedBy, String rejectionComment) {
        if (rejectionComment == null || rejectionComment.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.forward.error.rejection_comment_required: a comment is required to reject a transfer");
        }

        InterOfficeTransfer transfer = transferRepo.findById(transferId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Transfer not found: " + transferId));

        if (!"PENDING".equals(transfer.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transfer already resolved");
        }

        transfer.setStatus("REJECTED");
        transfer.setApprovedBy(rejectedBy);
        transfer.setRejectionComment(rejectionComment);
        transfer.setResolvedAt(LocalDateTime.now());
        transferRepo.save(transfer);

        Complaint complaint = complaintRepository.findByComplaintNumber(transfer.getComplaintNumber())
                .orElse(null);
        if (complaint != null) {
            String heldBy = complaint.getAssignedOfficer();
            String previousStatus = complaint.getStatus();

            // Returned to the officer who held it when the transfer was requested. When that is unknown the
            // complaint is left unassigned at its own office rather than being handed to an arbitrary holder
            // of the role — a rejected transfer must not silently change who owns the file.
            String restoreTo = transfer.getPreviousOwner();
            complaint.setAssignedOfficer(restoreTo);

            // The complaint comes out of the holding status the request put it in. Left as sent_to_other it
            // would sit in the CRPC queue after the Head had already refused it.
            complaint.setStatus("assigned");
            complaint.setWorkflowStage("ASSIGNED");
            complaintRepository.save(complaint);

            complaintService.addDetailedTimeline(complaint.getId(), "TRANSFER_REJECTED", rejectedBy, "CRPC_HEAD",
                    rejectionComment, previousStatus, complaint.getStatus(),
                    "assignedOfficer", heldBy, restoreTo,
                    null, transfer.getToOffice(), TimelineEventSource.MANUAL);

            if (restoreTo != null && !restoreTo.isBlank()) {
                try {
                    notificationService.send(restoreTo, "TRANSFER_REJECTED",
                            "Transfer request rejected",
                            "The transfer of complaint " + complaint.getComplaintNumber()
                                    + " was rejected by the CRPC Head: " + rejectionComment,
                            complaint.getComplaintNumber(), "COMPLAINT",
                            "/workflow/rbio/complaint/" + complaint.getComplaintNumber());
                } catch (Exception e) {
                    log.warn("Transfer {} rejected but notifying {} failed: {}",
                            transferId, restoreTo, e.getMessage());
                }
            }
        }

        log.info("Transfer {} rejected by {}: {}", transferId, rejectedBy, rejectionComment);
        return transfer;
    }

    // ══════════════════════════════════════════════════════════════════
    // Reads
    // ══════════════════════════════════════════════════════════════════

    public List<InterOfficeTransfer> getPendingTransfers() {
        return transferRepo.findByStatusOrderByRequestedAtDesc("PENDING");
    }

    public List<InterOfficeTransfer> getTransferHistory(String complaintNumber) {
        return transferRepo.findByComplaintNumberOrderByRequestedAtDesc(complaintNumber);
    }

    public long getPendingCount() {
        return transferRepo.countByStatus("PENDING");
    }
}
