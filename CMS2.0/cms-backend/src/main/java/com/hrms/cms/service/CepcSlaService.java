package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * SLA (Service Level Agreement) calculation service for CEPC complaints.
 * <p>
 * Business day calculation: 9-18 hrs, Mon-Fri, Asia/Kolkata timezone.
 * Excludes gazetted holidays from the Holiday table.
 * <p>
 * Priority-based deadlines (in business days):
 * - CRITICAL: 7 business days
 * - HIGH: 15 business days
 * - MEDIUM: 30 business days
 * - LOW: 45 business days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CepcSlaService {

    private final BusinessHoursService businessHoursService;
    private final ComplaintRepository complaintRepository;

    private static final Map<String, Integer> PRIORITY_BUSINESS_DAYS = Map.of(
            "CRITICAL", 7,
            "HIGH", 15,
            "MEDIUM", 30,
            "LOW", 45
    );

    private static final int DEFAULT_BUSINESS_DAYS = 30;

    /**
     * Calculate the SLA deadline based on creation time and priority.
     *
     * @param createdAt the complaint creation time
     * @param priority  the complaint priority (CRITICAL, HIGH, MEDIUM, LOW)
     * @return the deadline as LocalDateTime
     */
    public LocalDateTime calculateDeadline(LocalDateTime createdAt, String priority) {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        int businessDays = getBusinessDaysForPriority(priority);
        int businessHours = businessDays * businessHoursService.getBusinessHoursPerDay();

        return businessHoursService.calculateDueDate(createdAt, businessHours);
    }

    /**
     * Check if a complaint's SLA has been breached.
     */
    public boolean isBreached(Complaint complaint) {
        LocalDateTime deadline = getEffectiveDeadline(complaint);
        if (deadline == null) return false;

        return LocalDateTime.now().isAfter(deadline);
    }

    /**
     * Get the number of business days remaining before SLA breach.
     * Returns negative value if already breached.
     */
    public long getDaysRemaining(Complaint complaint) {
        LocalDateTime deadline = getEffectiveDeadline(complaint);
        if (deadline == null) return Long.MAX_VALUE;

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(deadline)) {
            // Already breached - return negative business days
            long elapsedHours = businessHoursService.calculateElapsedBusinessHours(deadline, now);
            return -(elapsedHours / businessHoursService.getBusinessHoursPerDay());
        }

        long remainingHours = businessHoursService.calculateElapsedBusinessHours(now, deadline);
        return remainingHours / businessHoursService.getBusinessHoursPerDay();
    }

    /**
     * Get compliance statistics for a department.
     *
     * @param department the department code (e.g., "CEPC")
     * @return map with keys: total, breached, atRisk, onTrack
     */
    public Map<String, Long> getComplianceStats(String department) {
        List<Complaint> activeComplaints = complaintRepository
                .findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                        department,
                        List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated")
                );

        long total = activeComplaints.size();
        long breached = 0;
        long atRisk = 0;
        long onTrack = 0;

        for (Complaint c : activeComplaints) {
            if (isBreached(c)) {
                breached++;
            } else {
                long daysRemaining = getDaysRemaining(c);
                if (daysRemaining <= 2) {
                    atRisk++;
                } else {
                    onTrack++;
                }
            }
        }

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("breached", breached);
        stats.put("atRisk", atRisk);
        stats.put("onTrack", onTrack);
        return stats;
    }

    /**
     * Calculate and set the SLA deadline on a complaint based on its priority.
     */
    public void applySlaDeadline(Complaint complaint) {
        String priority = normalizePriority(complaint.getPriority());
        complaint.setSlaPriority(priority);

        LocalDateTime startTime = complaint.getCreatedAt() != null
                ? complaint.getCreatedAt()
                : LocalDateTime.now();

        LocalDateTime deadline = calculateDeadline(startTime, priority);
        complaint.setSlaDeadline(deadline);
    }

    private LocalDateTime getEffectiveDeadline(Complaint complaint) {
        // Use pre-calculated slaDeadline if available
        if (complaint.getSlaDeadline() != null) {
            return complaint.getSlaDeadline();
        }

        // Fall back to calculating from createdAt + priority
        if (complaint.getCreatedAt() != null) {
            String priority = normalizePriority(complaint.getPriority());
            return calculateDeadline(complaint.getCreatedAt(), priority);
        }

        return null;
    }

    private int getBusinessDaysForPriority(String priority) {
        if (priority == null) return DEFAULT_BUSINESS_DAYS;
        return PRIORITY_BUSINESS_DAYS.getOrDefault(priority.toUpperCase(), DEFAULT_BUSINESS_DAYS);
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) return "MEDIUM";
        return priority.toUpperCase();
    }
}
