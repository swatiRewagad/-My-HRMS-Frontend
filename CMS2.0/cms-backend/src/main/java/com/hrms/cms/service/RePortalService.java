package com.hrms.cms.service;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Business logic for the Regulated Entity (RE) portal.
 * Handles complaint viewing, response submission, dashboard stats,
 * and query/clarification workflows for RE nodal officers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RePortalService {

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final ReResponseTrackerRepository trackerRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;

    private static final int DEFAULT_RESPONSE_WINDOW_DAYS = 15;

    // ═══════════════════════════════════════════════════════════════
    // Complaint listing
    // ═══════════════════════════════════════════════════════════════

    /**
     * Retrieves paginated complaints forwarded to the specified entity.
     */
    @Transactional(readOnly = true)
    public Page<Complaint> getComplaintsForEntity(String entityCode, String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return complaintRepository.findByEntityCodeAndStatusOrderByCreatedAtDesc(entityCode, status, pageable);
        }
        return complaintRepository.findByEntityCodeOrderByCreatedAtDesc(entityCode, pageable);
    }

    /**
     * Retrieves a single complaint detail by complaint number, validating entity access.
     */
    @Transactional(readOnly = true)
    public Complaint getComplaintDetail(String complaintNumber, String entityCode) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));

        if (!entityCode.equals(complaint.getEntityCode())) {
            throw new SecurityException("Access denied: complaint does not belong to entity " + entityCode);
        }
        return complaint;
    }

    // ═══════════════════════════════════════════════════════════════
    // Response submission
    // ═══════════════════════════════════════════════════════════════

    /**
     * Submits RE response to a complaint. Validates the response window.
     */
    @Transactional
    public ReResponseTracker respondToComplaint(String complaintNumber, String response, String respondedBy) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));

        ReResponseTracker tracker = trackerRepository.findByComplaintId(complaint.getId())
                .orElseThrow(() -> new NoSuchElementException("No response tracker found for complaint: " + complaintNumber));

        if (tracker.getRespondedAt() != null) {
            throw new IllegalStateException("Complaint already responded to on: " + tracker.getRespondedAt());
        }

        if (!isWithinResponseWindow(tracker)) {
            log.warn("Response submitted after window expiry for complaint {}", complaintNumber);
            // Allow response but mark as late
        }

        tracker.setRespondedAt(LocalDateTime.now());
        tracker.setResponseText(response);
        trackerRepository.save(tracker);

        // Update complaint status
        String previousStatus = complaint.getStatus();
        complaint.setStatus("re_responded");
        complaintRepository.save(complaint);

        // Add timeline entry
        ComplaintTimeline timeline = ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("RE_RESPONDED")
                .performedBy(respondedBy)
                .remarks("Regulated entity submitted response")
                .fromStatus(previousStatus)
                .toStatus("re_responded")
                .build();
        timelineRepository.save(timeline);

        log.info("RE response submitted for complaint {} by {}", complaintNumber, respondedBy);
        return tracker;
    }

    // ═══════════════════════════════════════════════════════════════
    // Dashboard stats
    // ═══════════════════════════════════════════════════════════════

    /**
     * Computes dashboard statistics for the given entity.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboardStats(String entityCode) {
        // Get all trackers for this entity
        RegulatedEntity entity = regulatedEntityRepository.findByNameNormalized(
                RegulatedEntity.normalize(entityCode)).orElse(null);

        long totalForwarded = 0;
        long pending = 0;
        long responded = 0;
        long breached = 0;
        double avgResponseDays = 0.0;

        if (entity != null) {
            List<ReResponseTracker> trackers = trackerRepository
                    .findByRegulatedEntityIdOrderByForwardedAtDesc(entity.getId());

            totalForwarded = trackers.size();
            pending = trackers.stream().filter(t -> t.getRespondedAt() == null && !t.isBreached()).count();
            responded = trackers.stream().filter(t -> t.getRespondedAt() != null).count();
            breached = trackers.stream().filter(ReResponseTracker::isBreached).count();

            avgResponseDays = trackers.stream()
                    .filter(t -> t.getRespondedAt() != null)
                    .mapToLong(t -> ChronoUnit.DAYS.between(t.getForwardedAt(), t.getRespondedAt()))
                    .average()
                    .orElse(0.0);
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalForwarded", totalForwarded);
        stats.put("pending", pending);
        stats.put("responded", responded);
        stats.put("breached", breached);
        stats.put("avgResponseDays", Math.round(avgResponseDays * 10.0) / 10.0);
        return stats;
    }

    // ═══════════════════════════════════════════════════════════════
    // Query / Clarification
    // ═══════════════════════════════════════════════════════════════

    /**
     * RE raises a query or extension request on a complaint.
     */
    @Transactional
    public void raiseQuery(String complaintNumber, String queryText, String queryType) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));

        ReResponseTracker tracker = trackerRepository.findByComplaintId(complaint.getId())
                .orElseThrow(() -> new NoSuchElementException("No response tracker for complaint: " + complaintNumber));

        tracker.setQueryText(queryText);
        tracker.setQueryRaisedAt(LocalDateTime.now());
        trackerRepository.save(tracker);

        // Add timeline entry
        String action = "EXTENSION_REQUEST".equals(queryType) ? "RE_EXTENSION_REQUEST" : "RE_CLARIFICATION";
        ComplaintTimeline timeline = ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action(action)
                .performedBy("RE_PORTAL")
                .remarks(queryType + ": " + queryText)
                .fromStatus(complaint.getStatus())
                .toStatus(complaint.getStatus()) // status unchanged for queries
                .build();
        timelineRepository.save(timeline);

        log.info("RE raised {} for complaint {}", queryType, complaintNumber);
    }

    // ═══════════════════════════════════════════════════════════════
    // Timeline
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets complaint timeline visible to the RE.
     */
    @Transactional(readOnly = true)
    public List<ComplaintTimeline> getTimeline(String complaintNumber, String entityCode) {
        Complaint complaint = getComplaintDetail(complaintNumber, entityCode);
        return timelineRepository.findByComplaintIdOrderByPerformedAtDesc(complaint.getId());
    }

    // ═══════════════════════════════════════════════════════════════
    // Profile
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets the RE entity profile.
     */
    @Transactional(readOnly = true)
    public RegulatedEntity getEntityProfile(String entityCode) {
        return regulatedEntityRepository.findByNameNormalized(RegulatedEntity.normalize(entityCode))
                .orElseThrow(() -> new NoSuchElementException("Regulated entity not found: " + entityCode));
    }

    /**
     * Updates nodal officer contact details.
     */
    @Transactional
    public RegulatedEntity updateNodalOfficer(String entityCode, String name, String email, String phone, String designation) {
        RegulatedEntity entity = getEntityProfile(entityCode);
        entity.setNodalOfficerName(name);
        entity.setNodalOfficerEmail(email);
        entity.setNodalOfficerPhone(phone);
        entity.setNodalOfficerDesignation(designation);
        return regulatedEntityRepository.save(entity);
    }

    // ═══════════════════════════════════════════════════════════════
    // Response window check
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if the 15-day response window is still open for a complaint.
     */
    public boolean isWithinResponseWindow(String complaintNumber) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new NoSuchElementException("Complaint not found: " + complaintNumber));

        ReResponseTracker tracker = trackerRepository.findByComplaintId(complaint.getId())
                .orElseThrow(() -> new NoSuchElementException("No response tracker for complaint: " + complaintNumber));

        return isWithinResponseWindow(tracker);
    }

    private boolean isWithinResponseWindow(ReResponseTracker tracker) {
        if (tracker.getWindowExpiresAt() != null) {
            return LocalDateTime.now().isBefore(tracker.getWindowExpiresAt());
        }
        // Fallback: check forwardedAt + window days
        int windowDays = tracker.getWindowDays() > 0 ? tracker.getWindowDays() : DEFAULT_RESPONSE_WINDOW_DAYS;
        LocalDateTime deadline = tracker.getForwardedAt().plusDays(windowDays);
        return LocalDateTime.now().isBefore(deadline);
    }
}
