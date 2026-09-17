package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-stage deadlines for an appeal, in WORKING days.
 *
 * Appeals had no stage deadline of any kind: the two existing SYSTEM_CONFIG keys
 * (timeline.appeal.filing_window_days, extended_window_days) are read only when deciding whether an
 * appeal may be FILED at all, never once it is in flight. So an appeal could sit in under_review
 * indefinitely with nothing to show it was late.
 *
 * Durations come from SYSTEM_CONFIG rather than Java constants. CEPC and RBIO both hardcode theirs in
 * static maps, which means changing a statutory timeline needs a redeploy; an operator should be able to
 * do it during an incident.
 *
 * Working-day maths is delegated to BusinessHoursService — the one calculator in this codebase that
 * already understands weekends and the HOLIDAYS master. A second implementation would inevitably
 * disagree with the first about a public holiday.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaStageSlaService {

    static final String CFG_PREFIX = "cms.aa.sla.";

    /**
     * Defaults are conservative and deliberately not derived from the Scheme.
     *
     * No RBIOS text prescribing per-stage AA timelines was available, so these are OPERATIONAL targets
     * for internal tracking, not statutory limits. They must be reviewed before go-live — a deadline
     * presented to a citizen as a legal entitlement when it is really an internal guess would be worse
     * than showing none.
     */
    private static final Map<String, Integer> DEFAULT_STAGE_DAYS = Map.of(
            AppealStatus.FILED.getCode(), 7,
            AppealStatus.UNDER_REVIEW.getCode(), 30,
            AppealStatus.HEARING_SCHEDULED.getCode(), 30);

    private final SystemConfigService systemConfigService;
    private final BusinessHoursService businessHoursService;

    /** Working days allowed in {@code status}, or 0 when the stage is untracked (terminal states). */
    public int allowedDaysFor(String status) {
        if (status == null || status.isBlank()) {
            return 0;
        }
        String normalized = status.trim().toLowerCase();
        Integer fallback = DEFAULT_STAGE_DAYS.get(normalized);
        if (fallback == null) {
            return 0;
        }
        return systemConfigService.getInt(CFG_PREFIX + normalized + ".days", fallback);
    }

    /**
     * When the current stage is due, or null when the stage is untracked or the clock has no start.
     *
     * The clock starts from the last transition into the current stage, which is {@code updatedAt}. Using
     * createdAt would make every stage of a long-running appeal appear overdue the moment it entered.
     */
    public LocalDateTime deadlineFor(Appeal appeal) {
        int days = allowedDaysFor(appeal.getStatus());
        if (days <= 0) {
            return null;
        }
        LocalDateTime start = appeal.getUpdatedAt() != null ? appeal.getUpdatedAt() : appeal.getFiledAt();
        if (start == null) {
            return null;
        }
        // BusinessHoursService counts in business HOURS, so days are converted the same way CepcSlaService
        // and RbioSlaService do it.
        return businessHoursService.calculateDueDate(start, days * businessHoursService.getBusinessHoursPerDay());
    }

    /**
     * The SLA state of an appeal, in the shape the other modules already expose so the UI can render
     * appeals and complaints identically.
     *
     * daysRemaining is NEGATIVE when overdue, matching CepcSlaService.getDaysRemaining.
     */
    public Map<String, Object> describe(Appeal appeal) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", appeal.getStatus());
        int allowed = allowedDaysFor(appeal.getStatus());
        out.put("stageAllowedDays", allowed);

        if (allowed <= 0) {
            // Terminal or untracked: reporting "on track" would imply a clock that is not running.
            out.put("tracked", false);
            out.put("deadline", null);
            out.put("daysRemaining", null);
            out.put("breached", false);
            out.put("statusKey", "aa.sla.not_tracked");
            return out;
        }

        LocalDateTime deadline = deadlineFor(appeal);
        out.put("tracked", true);
        out.put("deadline", deadline);

        if (deadline == null) {
            out.put("daysRemaining", null);
            out.put("breached", false);
            out.put("statusKey", "aa.sla.not_tracked");
            return out;
        }

        LocalDateTime now = LocalDateTime.now();
        int perDay = businessHoursService.getBusinessHoursPerDay();
        boolean breached = now.isAfter(deadline);
        long remaining = breached
                ? -(businessHoursService.calculateElapsedBusinessHours(deadline, now) / perDay)
                : businessHoursService.calculateElapsedBusinessHours(now, deadline) / perDay;

        out.put("daysRemaining", remaining);
        out.put("breached", breached);
        // A translation key, not English, and three bands rather than two so the UI can warn BEFORE the
        // deadline rather than only reporting failure after it.
        out.put("statusKey", breached
                ? "aa.sla.breached"
                : (remaining <= Math.max(1, allowed / 5) ? "aa.sla.at_risk" : "aa.sla.on_track"));
        return out;
    }
}
