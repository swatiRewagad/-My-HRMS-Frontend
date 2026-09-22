package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RbioCaseAssignmentHistory;
import com.hrms.cms.repository.RbioCaseAssignmentHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The custody record of a case file: who held it, in which role, and who to give it back to.
 *
 * <p><b>This class owns the send-back mechanism (UST516-517, 531) and publishes it for S5's UST759.</b>
 * The contract is the three public lookups below — {@link #previousHolderFor},
 * {@link #originalHolderFor} and {@link #currentHolder}. Callers should not query the repository
 * directly, because "who held this before" and "is that person still able to receive it" are one
 * decision and splitting them is how a file gets handed to a disabled account.
 *
 * <p><b>Availability is decided here, not by the caller.</b> {@link #previousHolderFor} returns an
 * {@link Outcome} that distinguishes three cases a boolean cannot: there was never a previous holder, the
 * previous holder is available, or the previous holder exists but is no longer active. Only the middle
 * one may proceed automatically; the third must force manual selection rather than silently falling
 * through to round-robin, because quietly reassigning a case to a stranger and quietly leaving it with
 * the sender are both wrong in the same way — the officer who gets it is not the one the process names.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioCaseAssignmentHistoryService {

    private final RbioCaseAssignmentHistoryRepository historyRepository;
    private final KeycloakUserService keycloakUserService;

    /** Why a previous-holder lookup did or did not yield an officer to assign to. */
    public enum Availability {
        /** An active previous holder exists; assign to them. */
        AVAILABLE,
        /** Nobody has held this role on this file before; there is nothing to send back to. */
        NO_HISTORY,
        /** A previous holder exists but is disabled or absent from the directory. */
        INACTIVE
    }

    /**
     * The result of a previous-holder lookup.
     *
     * @param officerId    the officer to assign to, non-null only when {@code availability} is AVAILABLE
     * @param availability why this outcome was reached
     */
    public record Outcome(String officerId, Availability availability) {
        public boolean assignable() {
            return availability == Availability.AVAILABLE && officerId != null && !officerId.isBlank();
        }

        static Outcome available(String officerId) {
            return new Outcome(officerId, Availability.AVAILABLE);
        }

        static Outcome noHistory() {
            return new Outcome(null, Availability.NO_HISTORY);
        }

        static Outcome inactive(String officerId) {
            return new Outcome(officerId, Availability.INACTIVE);
        }
    }

    /**
     * Records that {@code officerId} now holds {@code complaint} in {@code roleName}, closing whatever
     * custody was open before.
     *
     * <p>Called on every assignment change, so the history is complete rather than only covering the
     * actions that happen to care about it. A gap would be indistinguishable from "never held", and the
     * send-back would silently degrade to manual selection.
     *
     * <p>A no-op when the officer and role are both unchanged: re-recording the same custody on an action
     * that does not move the file (ISSUE_ADVISORY, SCHEDULE_MEETING) would fabricate a release, and the
     * previous-holder query keys on releases.
     */
    @Transactional
    public void recordAssignment(Complaint complaint, String roleName, String officerId,
                                 String action, String actor) {
        if (complaint == null || officerId == null || officerId.isBlank() || roleName == null || roleName.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<RbioCaseAssignmentHistory> open = historyRepository.findOpenHolders(complaint.getComplaintNumber());

        boolean alreadyHeld = open.stream().anyMatch(
                h -> officerId.equals(h.getOfficerId()) && roleName.equalsIgnoreCase(h.getRoleName()));
        if (alreadyHeld && open.size() == 1) {
            return;
        }

        // Close every open row, not just the newest. Two open rows should be impossible, but if a torn
        // write ever produced them, leaving one open would make the next lookup ambiguous forever.
        for (RbioCaseAssignmentHistory holder : open) {
            holder.setReleasedAt(now);
        }
        historyRepository.saveAll(open);

        historyRepository.save(RbioCaseAssignmentHistory.builder()
                .complaintNumber(complaint.getComplaintNumber())
                .roleName(roleName)
                .officerId(officerId)
                .assignedByAction(action)
                .assignedBy(actor)
                .assignedAt(now)
                .build());
    }

    /**
     * The officer to send this file back to for {@code roleName}, and whether they can receive it.
     *
     * <p>Skips any previous holder who is no longer active rather than walking further back through the
     * history. Returning an officer from three handovers ago would be a guess about who should own a
     * statutory case file; forcing the sender to choose is the honest outcome.
     */
    public Outcome previousHolderFor(String complaintNumber, String roleName) {
        List<RbioCaseAssignmentHistory> rows = historyRepository.findPreviousHolders(
                complaintNumber, roleName, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Outcome.noHistory();
        }
        String officerId = rows.get(0).getOfficerId();
        return isActive(officerId) ? Outcome.available(officerId) : Outcome.inactive(officerId);
    }

    /**
     * The officer who ORIGINALLY processed this file in {@code roleName} — UST552's reopen target.
     *
     * <p>Deliberately the earliest holder, not the latest: a reopened complaint goes back to the official
     * who dealt with it, which after send-backs and escalations is not whoever touched it last.
     */
    public Outcome originalHolderFor(String complaintNumber, String roleName) {
        List<RbioCaseAssignmentHistory> rows = historyRepository.findOriginalHolders(
                complaintNumber, roleName, PageRequest.of(0, 1));
        if (rows.isEmpty()) {
            return Outcome.noHistory();
        }
        String officerId = rows.get(0).getOfficerId();
        return isActive(officerId) ? Outcome.available(officerId) : Outcome.inactive(officerId);
    }

    /** The open custody row, if the file is held by anyone. */
    public Optional<RbioCaseAssignmentHistory> currentHolder(String complaintNumber) {
        return historyRepository.findOpenHolders(complaintNumber).stream().findFirst();
    }

    /** The full custody trail, newest first, for the History tab. */
    public List<RbioCaseAssignmentHistory> trailFor(String complaintNumber) {
        return historyRepository.findByComplaintNumberOrderByAssignedAtDesc(complaintNumber);
    }

    /**
     * The response body for {@code GET /complaints/{id}/last-active-officer}.
     *
     * <p>Shaped to the {@code ReassignmentCandidate} interface the frontend already declares
     * ({@code rbio-workflow.service.ts:52-59}) rather than to a new shape, so the existing component
     * binds without change. {@code isOnLeave} is reported as false because no leave register exists in
     * this codebase — the field is answered honestly rather than omitted, since the frontend reads it.
     */
    public Map<String, Object> lastActiveOfficerPayload(String complaintNumber, String roleName) {
        Outcome outcome = previousHolderFor(complaintNumber, roleName);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("role", roleName);
        body.put("availability", outcome.availability().name());
        body.put("userId", outcome.officerId());
        body.put("isActive", outcome.availability() == Availability.AVAILABLE);
        body.put("isOnLeave", false);
        body.put("requiresManualSelection", !outcome.assignable());

        if (outcome.officerId() != null) {
            historyRepository
                    .findFirstByComplaintNumberAndOfficerIdOrderByAssignedAtDesc(complaintNumber, outcome.officerId())
                    .ifPresent(row -> body.put("lastActiveDate",
                            row.getReleasedAt() != null ? row.getReleasedAt().toString() : null));
            body.put("name", displayNameOf(outcome.officerId()));
        } else {
            body.put("name", null);
            body.put("lastActiveDate", null);
        }

        return body;
    }

    /**
     * Whether the officer can still receive a case file.
     *
     * <p>Fails CLOSED — an unreachable Keycloak yields false, which forces manual selection. The opposite
     * default would hand a live case file to an account nobody has verified is still enabled, and the
     * sender would never be told.
     */
    private boolean isActive(String officerId) {
        if (officerId == null || officerId.isBlank()) return false;
        try {
            String keycloakId = keycloakUserService.findUserId(officerId);
            if (keycloakId == null) return false;
            Map<String, Object> user = keycloakUserService.getUserById(keycloakId);
            return user != null && Boolean.TRUE.equals(user.getOrDefault("enabled", Boolean.FALSE));
        } catch (Exception e) {
            log.warn("Could not verify whether officer {} is active; treating as inactive: {}",
                    officerId, e.getMessage());
            return false;
        }
    }

    private String displayNameOf(String officerId) {
        try {
            String keycloakId = keycloakUserService.findUserId(officerId);
            if (keycloakId == null) return officerId;
            Map<String, Object> user = keycloakUserService.getUserById(keycloakId);
            if (user == null) return officerId;
            String first = String.valueOf(user.getOrDefault("firstName", "")).trim();
            String last = String.valueOf(user.getOrDefault("lastName", "")).trim();
            String full = (first + " " + last).trim();
            return full.isEmpty() ? officerId : full;
        } catch (Exception e) {
            return officerId;
        }
    }
}
