package com.hrms.cms.service;

import com.hrms.cms.entity.AaAssignmentRecord;
import com.hrms.cms.repository.AaWorkloadRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 12: alerts the AA Admin about drafts that were assigned but never picked up.
 *
 * The backoff lives in SYSTEM_CONFIG rather than a PREFERENCE_MASTER table, because no such table
 * exists in this schema. SystemConfigService is the established typed, cached, operator-editable config
 * store, so it serves as the Preference Master here.
 *
 * Each record is escalated once: ESCALATED_AT is stamped as part of the sweep, so a draft that stays
 * unclaimed does not re-alert on every tick and bury the admin's inbox.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaEscalationSweepService {

    static final String CFG_ENABLED = "cms.aa.escalation.enabled";
    static final String CFG_BACKOFF_MINUTES = "cms.aa.escalation.unclaimed_backoff_minutes";

    /** 48 hours. Long enough that an officer's ordinary working day does not trigger an alert. */
    private static final int DEFAULT_BACKOFF_MINUTES = 2880;

    private final AaWorkloadRepository workloadRepository;
    private final SystemConfigService systemConfigService;
    private final NotificationService notificationService;

    /**
     * Escalates every draft that has sat unclaimed past the configured backoff.
     *
     * @return how many records were escalated
     */
    @Transactional
    public int sweepUnclaimed() {
        if (!systemConfigService.getBoolean(CFG_ENABLED, true)) {
            log.debug("AA escalation sweep is disabled by configuration");
            return 0;
        }

        int backoffMinutes = systemConfigService.getInt(CFG_BACKOFF_MINUTES, DEFAULT_BACKOFF_MINUTES);
        if (backoffMinutes <= 0) {
            // A zero or negative backoff would escalate everything the instant it is assigned, which is
            // certainly a misconfiguration rather than an intent.
            log.warn("AA escalation: backoff of {} minutes is not usable -- skipping sweep",
                    backoffMinutes);
            return 0;
        }

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(backoffMinutes);
        List<AaAssignmentRecord> stale = workloadRepository.findUnclaimedBefore(cutoff);
        if (stale.isEmpty()) {
            return 0;
        }

        int escalated = 0;
        for (AaAssignmentRecord record : stale) {
            try {
                record.setEscalatedAt(LocalDateTime.now());
                workloadRepository.save(record);

                notificationService.send("AA_ADMIN", "ESCALATION",
                        "aa.escalation.unclaimed_draft",
                        record.getAppealNumber(), record.getAppealNumber(), "DRAFT",
                        "/aa/admin");
                escalated++;
            } catch (RuntimeException e) {
                // One bad record must not abort the sweep, or a single failure hides every other
                // overdue draft.
                log.warn("AA escalation: could not escalate {}: {}",
                        record.getAppealNumber(), e.getMessage());
            }
        }

        log.info("AA escalation: {} unclaimed draft(s) escalated past a {}-minute backoff",
                escalated, backoffMinutes);
        return escalated;
    }

    /** Records that the assignee has opened it, which takes it out of the escalation window. */
    @Transactional
    public boolean markClaimed(String appealNumber, String userId) {
        return workloadRepository.findByAppealNumberAndReleasedAtIsNull(appealNumber)
                .filter(record -> record.getAssignedUserId().equals(userId))
                .map(record -> {
                    if (record.getClaimedAt() == null) {
                        record.setClaimedAt(LocalDateTime.now());
                        workloadRepository.save(record);
                    }
                    return true;
                })
                .orElse(false);
    }
}
