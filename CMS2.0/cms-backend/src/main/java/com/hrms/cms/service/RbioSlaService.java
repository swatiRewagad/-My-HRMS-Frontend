package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * SLA (Service Level Agreement) service for RBIO (RBI Ombudsman) complaints.
 * <p>
 * RBIO SLA rules (in business days):
 * - Officer initial assessment: 30 business days
 * - Conciliation: 30 business days from assignment
 * - Adjudication: 60 business days from assignment to Award
 * - Total complaint lifecycle: max 120 business days
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioSlaService {

    private final BusinessHoursService businessHoursService;
    private final ComplaintRepository complaintRepository;

    /** Officer initial assessment: 30 business days */
    private static final int OFFICER_ASSESSMENT_DAYS = 30;

    /** Conciliation stage: 30 business days */
    private static final int CONCILIATION_DAYS = 30;

    /** Adjudication stage: 60 business days */
    private static final int ADJUDICATION_DAYS = 60;

    /** Total complaint lifecycle: 120 business days */
    private static final int TOTAL_LIFECYCLE_DAYS = 120;

    /**
     * Statuses at which the SLA clock stops. Held uppercase and compared case-insensitively because
     * this backend writes lowercase statuses ({@code "closed"}) while portal/search payloads carry
     * the uppercase enum names ({@code "COMPLAINT_CLOSED"}).
     */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "RESOLVED", "CLOSED", "REJECTED", "WITHDRAWN", "ADJUDICATED", "CONCILIATED",
            "COMPLAINT_CLOSED", "COMPLAINT_SETTLED", "COMPLAINT_WITHDRAWN", "COMPLAINT_REJECTED"
    );

    private static final Map<String, Integer> STAGE_BUSINESS_DAYS = Map.of(
            "OFFICER_ASSESSMENT", OFFICER_ASSESSMENT_DAYS,
            "EXAMINATION", OFFICER_ASSESSMENT_DAYS,
            "CONCILIATION", CONCILIATION_DAYS,
            "ADJUDICATION", ADJUDICATION_DAYS
    );

    /**
     * Calculate the SLA deadline based on start time and workflow stage.
     *
     * @param from  the start time (e.g., assignment time for the stage)
     * @param stage the workflow stage (OFFICER_ASSESSMENT, CONCILIATION, ADJUDICATION)
     * @return the deadline as LocalDateTime
     */
    public LocalDateTime calculateDeadline(LocalDateTime from, String stage) {
        if (from == null) {
            from = LocalDateTime.now();
        }

        int businessDays = STAGE_BUSINESS_DAYS.getOrDefault(
                stage != null ? stage.toUpperCase() : "", OFFICER_ASSESSMENT_DAYS);
        int businessHours = businessDays * businessHoursService.getBusinessHoursPerDay();

        return businessHoursService.calculateDueDate(from, businessHours);
    }

    /**
     * Check if the current stage SLA has been breached for a complaint.
     *
     * @param complaint the complaint to check
     * @return true if the stage deadline is past
     */
    public boolean isBreached(Complaint complaint) {
        LocalDateTime deadline = getEffectiveDeadline(complaint);
        if (deadline == null) return false;

        return LocalDateTime.now().isAfter(deadline);
    }

    /**
     * Get percentage progress for the current stage.
     *
     * @param complaint the complaint
     * @return percentage (0-100+) of current stage time used
     */
    public double getStageProgress(Complaint complaint) {
        LocalDateTime stageStart = getStageStartTime(complaint);
        LocalDateTime deadline = getEffectiveDeadline(complaint);

        if (stageStart == null || deadline == null) return 0.0;

        long totalHours = businessHoursService.calculateElapsedBusinessHours(stageStart, deadline);
        if (totalHours <= 0) return 100.0;

        LocalDateTime now = LocalDateTime.now();
        long elapsedHours = businessHoursService.calculateElapsedBusinessHours(stageStart, now);

        return Math.min(100.0, (elapsedHours * 100.0) / totalHours);
    }

    /**
     * Get percentage of the total 120-day lifecycle used.
     *
     * @param complaint the complaint
     * @return percentage (0-100+) of total lifecycle time used
     */
    public double getOverallProgress(Complaint complaint) {
        LocalDateTime createdAt = complaint.getCreatedAt();
        if (createdAt == null) return 0.0;

        int totalBusinessHours = TOTAL_LIFECYCLE_DAYS * businessHoursService.getBusinessHoursPerDay();
        LocalDateTime now = LocalDateTime.now();
        long elapsedHours = businessHoursService.calculateElapsedBusinessHours(createdAt, now);

        return Math.min(100.0, (elapsedHours * 100.0) / totalBusinessHours);
    }

    /**
     * Apply the stage-specific SLA deadline to the complaint.
     *
     * @param complaint the complaint to update
     * @param stage     the workflow stage
     */
    public void applyStageSla(Complaint complaint, String stage) {
        LocalDateTime startTime = LocalDateTime.now();
        complaint.setStageAssignedAt(startTime);

        LocalDateTime deadline = calculateDeadline(startTime, stage);
        complaint.setCurrentStageDeadline(deadline);
        complaint.setSlaDeadline(deadline);

        log.info("RBIO SLA applied: complaint={}, stage={}, deadline={}",
                complaint.getComplaintNumber(), stage, deadline);
    }

    /**
     * Get compliance statistics for RBIO department.
     *
     * @return map with keys: total, breached, atRisk, onTrack
     */
    public Map<String, Long> getComplianceStats() {
        List<Complaint> activeComplaints = complaintRepository
                .findByDepartmentAndStatusNotInOrderByCreatedAtDesc(
                        "RBIO",
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
                double progress = getStageProgress(c);
                if (progress >= 85.0) {
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
     * Render the remaining SLA time the way the officer portal displays it, e.g. {@code "-12 Days"}
     * once breached. Returns {@code "Closed"} for terminal statuses and {@code null} when no
     * deadline can be established - never a misleading {@code "0 Days"}.
     */
    public String formatBreachIn(Complaint complaint) {
        String status = complaint.getStatus();
        if (status != null && TERMINAL_STATUSES.contains(status.toUpperCase())) {
            return "Closed";
        }

        LocalDateTime deadline = getEffectiveDeadline(complaint);
        if (deadline == null) return null;

        long days = ChronoUnit.DAYS.between(LocalDate.now(), deadline.toLocalDate());
        return days + (Math.abs(days) == 1 ? " Day" : " Days");
    }

    public LocalDateTime getEffectiveDeadline(Complaint complaint) {
        // Use pre-calculated currentStageDeadline if available
        if (complaint.getCurrentStageDeadline() != null) {
            return complaint.getCurrentStageDeadline();
        }

        // Fall back to slaDeadline
        if (complaint.getSlaDeadline() != null) {
            return complaint.getSlaDeadline();
        }

        // Fall back to calculating from stageAssignedAt or createdAt
        LocalDateTime startTime = getStageStartTime(complaint);
        if (startTime != null) {
            String stage = complaint.getWorkflowStage();
            return calculateDeadline(startTime, stage);
        }

        return null;
    }

    private LocalDateTime getStageStartTime(Complaint complaint) {
        if (complaint.getStageAssignedAt() != null) {
            return complaint.getStageAssignedAt();
        }
        return complaint.getCreatedAt();
    }
}
