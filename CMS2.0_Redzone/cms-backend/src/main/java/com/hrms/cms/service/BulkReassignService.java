package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reassigns a complaint to a named officer, one complaint per transaction.
 *
 * <p><b>Why this exists.</b> {@code CrpcHeadController.bulkReassign} was a stub: it counted the ids in the
 * request and returned {@code {"status":"reassigned","count":N}} without touching a repository. The ops-head
 * screen therefore reported N complaints reassigned while none of them moved.
 *
 * <p><b>Why {@code REQUIRES_NEW}.</b> A bulk operation must not lose the whole batch because one complaint is
 * closed or missing. Each reassignment commits independently, so the caller can report exactly which
 * complaints moved and which did not — the alternative is a single transaction where one failure silently
 * rolls back reassignments the user was told had succeeded.
 *
 * <p>Note the self-invocation hazard this deliberately avoids: the per-complaint method is called from a
 * DIFFERENT bean (the controller), so the {@code REQUIRES_NEW} proxy actually applies. Called from a loop
 * inside this same class it would be bypassed entirely and every reassignment would share one transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkReassignService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final NotificationService notificationService;
    private final RbioStatusVocabulary statusVocabulary;

    /**
     * Reassigns one complaint, refusing a closed one.
     *
     * <p>A closed complaint is refused rather than reassigned: handing a decided case to a new officer would
     * put it back in their queue with nothing to do, and the reopen path is the mechanism for genuinely
     * revisiting it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reassign(String complaintNumber, String targetUser, String actor) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Complaint not found: " + complaintNumber));

        String status = complaint.getStatus();
        if (status != null && statusVocabulary.closedStatuses().stream().anyMatch(status::equalsIgnoreCase)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Complaint " + complaintNumber + " is " + status + " and cannot be reassigned. "
                            + "Reopen it first.");
        }

        // Read before the write: the timeline records old→new and the assignee is about to be overwritten.
        String previousOwner = complaint.getAssignedOfficer();
        if (targetUser.equals(previousOwner)) {
            // Not an error, and deliberately not a no-op that reports success either: the complaint already
            // belongs to the target, so there is nothing to record and nobody to notify.
            return;
        }

        complaint.setAssignedOfficer(targetUser);
        complaintRepository.save(complaint);

        complaintService.addDetailedTimeline(complaint.getId(), "BULK_REASSIGN", actor, "CRPC_HEAD",
                "Reassigned in bulk by the CRPC Head", status, complaint.getStatus(),
                "assignedOfficer", previousOwner, targetUser,
                null, null, TimelineEventSource.MANUAL);

        try {
            notificationService.send(targetUser, "NEW_ASSIGNMENT",
                    "Complaint assigned to you",
                    "Complaint " + complaintNumber + " has been assigned to you by the CRPC Head.",
                    complaintNumber, "COMPLAINT", "/workflow/rbio/complaint/" + complaintNumber);
        } catch (Exception e) {
            // The reassignment stands: a notification failure must not roll back a completed handover, which
            // would leave the complaint with its original owner while the caller was told it had moved.
            log.warn("Complaint {} reassigned to {} but the notification failed: {}",
                    complaintNumber, targetUser, e.getMessage());
        }
    }
}
