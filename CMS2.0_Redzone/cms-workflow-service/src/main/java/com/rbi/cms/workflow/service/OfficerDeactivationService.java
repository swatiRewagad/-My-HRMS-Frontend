package com.rbi.cms.workflow.service;

import com.rbi.cms.workflow.entity.OfficerPool;
import com.rbi.cms.workflow.repository.OfficerPoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Safe deactivation of a team member (UST887).
 *
 * Deactivation used to be a bare {@code setActive(false)}, which stopped new assignments but
 * silently orphaned whatever the officer was already holding: those complaints stayed assigned to
 * someone who could no longer act on them, and nothing surfaced them.
 *
 * Reassignment of still-open records is therefore mandatory and happens in the same transaction as
 * the deactivation. Either the work moves and the officer is deactivated, or neither happens — a
 * partial outcome here is what produces invisible stranded complaints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OfficerDeactivationService {

    /**
     * Statuses that mean "no longer needs an owner", matching WorkflowController's CLOSED_STATUSES.
     * Anything else is treated as open, so an unrecognised status errs towards demanding reassignment.
     */
    private static final List<String> CLOSED_STATUSES = List.of(
            "resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    private final OfficerPoolRepository officerPoolRepository;
    private final JdbcTemplate jdbcTemplate;

    public static class OfficerNotFoundException extends RuntimeException {
        public OfficerNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when open records exist but no valid successor was supplied. */
    public static class ReassignmentRequiredException extends RuntimeException {
        private final List<String> openComplaints;

        public ReassignmentRequiredException(String message, List<String> openComplaints) {
            super(message);
            this.openComplaints = openComplaints;
        }

        public List<String> getOpenComplaints() {
            return openComplaints;
        }
    }

    public static class InvalidSuccessorException extends RuntimeException {
        public InvalidSuccessorException(String message) {
            super(message);
        }
    }

    public record DeactivationResult(String userId,
                                     String displayName,
                                     int reassignedCount,
                                     List<String> reassignedComplaints,
                                     String reassignedTo) {}

    /** Open records currently held by an officer, so the UI can warn before deactivating. */
    public List<String> findOpenComplaints(String userId) {
        String placeholders = String.join(",", java.util.Collections.nCopies(CLOSED_STATUSES.size(), "?"));
        String sql = "SELECT COMPLAINT_NUMBER FROM COMPLAINTS WHERE ASSIGNED_OFFICER = ? "
                + "AND (STATUS IS NULL OR LOWER(STATUS) NOT IN (" + placeholders + "))";

        Object[] args = new Object[CLOSED_STATUSES.size() + 1];
        args[0] = userId;
        for (int i = 0; i < CLOSED_STATUSES.size(); i++) {
            args[i + 1] = CLOSED_STATUSES.get(i);
        }

        try {
            return jdbcTemplate.queryForList(sql, String.class, args);
        } catch (Exception e) {
            log.error("Could not list open complaints for {}: {}", userId, e.getMessage());
            throw new IllegalStateException(
                    "Could not determine whether this officer still holds open complaints.", e);
        }
    }

    @Transactional
    public DeactivationResult deactivate(Long poolId, String reassignTo, String actor) {
        OfficerPool officer = officerPoolRepository.findById(poolId)
                .orElseThrow(() -> new OfficerNotFoundException("Officer " + poolId + " is not in the pool."));

        List<String> openComplaints = findOpenComplaints(officer.getUserId());

        if (!openComplaints.isEmpty()) {
            if (reassignTo == null || reassignTo.isBlank()) {
                throw new ReassignmentRequiredException(
                        "This officer still holds " + openComplaints.size()
                                + " open complaint(s). Choose who they should transfer to before deactivating.",
                        openComplaints);
            }
            validateSuccessor(officer, reassignTo);
            reassign(officer.getUserId(), reassignTo.trim(), actor, openComplaints);
        }

        officer.setActive(false);
        officerPoolRepository.save(officer);

        // Workload followed the officer, not the records; leaving it set would skew the round-robin
        // if the officer is ever reactivated.
        officer.setCurrentWorkload(0);
        officerPoolRepository.save(officer);

        log.info("Officer {} ({}) deactivated by {}; {} complaint(s) reassigned to {}",
                officer.getUserId(), poolId, actor, openComplaints.size(), reassignTo);

        return new DeactivationResult(officer.getUserId(), officer.getDisplayName(),
                openComplaints.size(), openComplaints, openComplaints.isEmpty() ? null : reassignTo);
    }

    private void validateSuccessor(OfficerPool officer, String reassignTo) {
        String successor = reassignTo.trim();
        if (successor.equals(officer.getUserId())) {
            throw new InvalidSuccessorException("An officer cannot be their own successor.");
        }
        Optional<OfficerPool> target = officerPoolRepository
                .findByRoleGroupAndActiveTrueAndOnLeaveFalse(officer.getRoleGroup()).stream()
                .filter(o -> successor.equals(o.getUserId()))
                .findFirst();
        if (target.isEmpty()) {
            // Reassigning to an inactive or on-leave officer would recreate the stranded-work
            // problem this story exists to fix.
            throw new InvalidSuccessorException(
                    "The chosen officer must be active, available, and in the same role group.");
        }
    }

    private void reassign(String fromUserId, String toUserId, String actor, List<String> complaints) {
        String placeholders = String.join(",", java.util.Collections.nCopies(complaints.size(), "?"));

        Object[] args = new Object[complaints.size() + 2];
        args[0] = toUserId;
        args[1] = fromUserId;
        for (int i = 0; i < complaints.size(); i++) {
            args[i + 2] = complaints.get(i);
        }

        int updated = jdbcTemplate.update(
                "UPDATE COMPLAINTS SET ASSIGNED_OFFICER = ? WHERE ASSIGNED_OFFICER = ? "
                        + "AND COMPLAINT_NUMBER IN (" + placeholders + ")", args);

        if (updated != complaints.size()) {
            // Rolls back the whole transaction: a partial move is exactly the stranded state to avoid.
            throw new IllegalStateException("Expected to reassign " + complaints.size()
                    + " complaint(s) but updated " + updated + "; deactivation rolled back.");
        }

        for (String complaintNumber : complaints) {
            writeTimeline(complaintNumber, fromUserId, toUserId, actor);
        }
    }

    /**
     * Leaves a per-complaint trace so the transfer is visible on the record, not only in the pool.
     *
     * Writes to COMPLAINT_TIMELINE — the table the ComplaintTimeline entity actually maps to. The
     * V1 Oracle DDL calls it COMPLAINT_HISTORY, which is not the live name.
     */
    private void writeTimeline(String complaintNumber, String fromUserId, String toUserId, String actor) {
        try {
            Map<String, Object> complaint = jdbcTemplate.queryForMap(
                    "SELECT ID, STATUS FROM COMPLAINTS WHERE COMPLAINT_NUMBER = ?", complaintNumber);
            Object complaintId = complaint.get("ID");
            Object status = complaint.get("STATUS");

            jdbcTemplate.update(
                    "INSERT INTO COMPLAINT_TIMELINE "
                            + "(COMPLAINT_ID, FROM_STATUS, TO_STATUS, ACTION, PERFORMED_BY, PERFORMED_AT, REMARKS, EVENT_SOURCE) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    complaintId, status, status, "REASSIGNED_ON_DEACTIVATION", actor,
                    LocalDateTime.now(),
                    "Reassigned from " + fromUserId + " to " + toUserId + " because " + fromUserId
                            + " was deactivated",
                    "AUTOMATIC");
        } catch (Exception e) {
            // The reassignment itself is the control; a missing history row must not undo it.
            log.warn("Could not write reassignment history for {}: {}", complaintNumber, e.getMessage());
        }
    }
}
