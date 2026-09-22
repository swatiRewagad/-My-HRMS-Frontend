package com.hrms.cms.service;

import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.ReassignmentHistory;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import com.hrms.cms.repository.ReassignmentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Applies a single reassignment in its own transaction, so one failure cannot roll back a batch
 * (UST839).
 *
 * <p><b>Why this is a separate bean.</b> Spring's {@code @Transactional} is proxy-based, so a
 * REQUIRES_NEW method invoked from another method of the same class runs in the caller's transaction
 * — the annotation is silently ignored. A try/catch in the caller is not sufficient either: once a
 * JPA exception has escaped a repository call the surrounding transaction is already marked
 * rollback-only, so the records that "succeeded" are discarded at commit and the caller reports a
 * partial success that never happened. Isolating each item behind a real proxy boundary is the only
 * arrangement where the surviving records actually survive. There was no existing example of this in
 * cms-backend to copy — the one bulk operation that exists, {@code CrpcWorkflowService
 * .bulkMarkNotAComplaint}, has exactly the all-or-nothing bug described above.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReassignmentExecutor {

    private final NodalOfficerRecordRepository recordRepository;
    private final ReassignmentHistoryRepository historyRepository;
    private final ReassignmentNotificationService notificationService;

    /** Thrown when the caller's expected version does not match the stored one. */
    public static class ConflictException extends RuntimeException {
        public ConflictException(String message) {
            super(message);
        }
    }

    /**
     * Moves one record to a new owner and appends a history row.
     *
     * @param expectedVersion the version the caller read; null skips the check for callers that
     *                        legitimately have no prior read (a PNO approving a queued request
     *                        acts on the request, not on a version the requester saw).
     * @return the history row written
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReassignmentHistory apply(Long recordId,
                                     Long expectedVersion,
                                     String toUserId,
                                     String toUserName,
                                     String reason,
                                     String triggerType,
                                     Long requestId,
                                     String performedBy,
                                     String performedByName) {

        NodalOfficerRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new NoSuchElementException("Record " + recordId + " not found"));

        // Checked before the write as well as by @Version. The explicit comparison produces a
        // message naming the record, which the batch response needs; relying on the flush alone
        // would only tell us that something somewhere conflicted.
        if (expectedVersion != null
                && record.getVersion() != null
                && !expectedVersion.equals(record.getVersion())) {
            throw new ConflictException(
                    "Record " + recordId + " was modified by someone else (expected version "
                    + expectedVersion + ", found " + record.getVersion() + ")");
        }

        String fromUserId = record.getAssignedTo();
        if (toUserId.equals(fromUserId)) {
            throw new IllegalArgumentException(
                    "Record " + recordId + " is already assigned to " + toUserId);
        }

        String fromUserName = record.getNodalOfficerName();
        record.setAssignedTo(toUserId);
        if (toUserName != null && !toUserName.isBlank()) {
            record.setNodalOfficerName(toUserName);
        }

        try {
            recordRepository.saveAndFlush(record);
        } catch (ObjectOptimisticLockingFailureException e) {
            // Lost the race between the read above and this flush.
            throw new ConflictException(
                    "Record " + recordId + " was modified by someone else during the update");
        }

        ReassignmentHistory history = historyRepository.save(ReassignmentHistory.builder()
                .nodalOfficerRecordId(recordId)
                .complaintNumber(record.getComplaintNumber())
                .entityCode(record.getEntityCode())
                .fromUserId(fromUserId)
                .fromUserName(fromUserName)
                .toUserId(toUserId)
                .toUserName(toUserName)
                .triggerType(triggerType)
                .reassignmentRequestId(requestId)
                .reason(reason)
                .performedBy(performedBy)
                .performedByName(performedByName)
                .build());

        notificationService.notifyReassignment(fromUserId, toUserId,
                                               record.getComplaintNumber(), recordId);
        return history;
    }
}
