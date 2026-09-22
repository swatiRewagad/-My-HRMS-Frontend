package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled sweeps over the RE Activity Status ladder: the overdue flip (UST849) and the stuck-record
 * nudge (UST850).
 *
 * <p><b>Why this lives in cms-backend rather than cms-sla-monitor-service.</b> That service queries
 * {@code COMPLAINT_MASTER}, which belongs to cms-ingestion and stores an uppercase
 * {@code ComplaintStatus} enum. The RE activity ladder lives on {@code COMPLAINTS}, a different
 * table owned by this module. The SLA monitor therefore cannot see these rows at all, let alone
 * write them, and it holds only a raw EntityManager and a KafkaTemplate with no transaction. Putting
 * the sweep here is what makes UST849's "one trigger" real: the status flip and the escalation
 * notification happen in a single transaction against the same datasource, so a record can never be
 * marked overdue without the escalation being raised, or vice versa.
 *
 * <p>UST817's note that nudges must not replace formal SLA escalation is honoured by keeping the two
 * paths distinct: the overdue flip escalates, nudges do not, and an already-OVERDUE record is not
 * nudgeable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReActivitySweepService {

    static final String CONFIG_ESCALATION_ENABLED = "cms.re.activity.overdue_escalation_enabled";
    static final String CONFIG_NUDGE_ENABLED = "cms.re.activity.nudge_enabled";
    static final String CONFIG_SWEEP_BATCH_SIZE = "cms.re.activity.sweep_batch_size";
    static final int FALLBACK_BATCH_SIZE = 500;

    private final ComplaintRepository complaintRepository;
    private final ReResponseTrackerRepository trackerRepository;
    private final ReActivityStatusService activityStatusService;
    private final SystemConfigRepository systemConfigRepository;
    private final NotificationService notificationService;

    /**
     * Flips records whose response window has expired to OVERDUE and escalates, in one transaction
     * (UST849). Returns the number of records flipped.
     *
     * <p>Only records that have NOT reached RESPONSE_SUBMITTED are considered, and the transition
     * itself is idempotent — a record already OVERDUE cannot advance to OVERDUE again, so repeated
     * sweeps neither re-notify nor duplicate timeline rows. That matters because the equivalent code
     * in cms-sla-monitor-service has no idempotency marker and re-publishes on every tick.
     */
    @Transactional
    public int sweepOverdue() {
        if (!booleanConfig(CONFIG_ESCALATION_ENABLED, true)) {
            log.debug("RE activity overdue sweep disabled by {}", CONFIG_ESCALATION_ENABLED);
            return 0;
        }

        List<ReResponseTracker> expired = trackerRepository.findPendingBreaches(LocalDateTime.now());
        int flipped = 0;

        for (ReResponseTracker tracker : expired) {
            Complaint complaint = complaintRepository.findById(tracker.getComplaintId()).orElse(null);
            if (complaint == null) {
                continue;
            }

            ReActivityStatus current = activityStatusService.currentStatusOf(complaint);
            if (current == ReActivityStatus.RESPONSE_SUBMITTED) {
                // The window expired but the entity did answer — not an overdue activity record.
                continue;
            }

            boolean changed = activityStatusService.recordActivity(complaint, ReActivityStatus.OVERDUE,
                    "SYSTEM", TimelineEventSource.AUTOMATIC,
                    "Response window expired with the record at " + current.name());

            if (changed) {
                escalate(complaint, current);
                flipped++;
            }
        }

        if (flipped > 0) {
            log.info("RE activity overdue sweep flipped {} record(s) of {} expired window(s)",
                    flipped, expired.size());
        }
        return flipped;
    }

    /**
     * Nudges the owning officer about records stuck in an early activity status past the threshold
     * that was snapshotted when the record entered that status (UST850).
     *
     * <p>The snapshot is the point of the story: reading the live config here instead would mean an
     * admin lowering the threshold retroactively makes months of history nudge-due, and raising it
     * silently forgives records that were already overdue for a nudge.
     */
    @Transactional
    public int sweepNudges() {
        if (!booleanConfig(CONFIG_NUDGE_ENABLED, true)) {
            log.debug("RE activity nudge sweep disabled by {}", CONFIG_NUDGE_ENABLED);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        int batchSize = intConfig(CONFIG_SWEEP_BATCH_SIZE).orElse(FALLBACK_BATCH_SIZE);

        List<Complaint> candidates = complaintRepository.findNudgeCandidates(
                ReActivityStatus.nudgeableStatuses(),
                org.springframework.data.domain.PageRequest.of(0, batchSize));

        int nudged = 0;
        for (Complaint complaint : candidates) {
            ReActivityStatus status = activityStatusService.currentStatusOf(complaint);
            if (!status.isNudgeable()) {
                continue;
            }

            Integer thresholdDays = complaint.getReActivityNudgeDays();
            LocalDateTime enteredAt = complaint.getReActivityChangedAt();
            if (thresholdDays == null || thresholdDays <= 0 || enteredAt == null) {
                // Never entered a status through the service, so there is no snapshot to judge
                // against. Guessing a threshold here would defeat the snapshot rule.
                continue;
            }

            long daysStuck = Duration.between(enteredAt, now).toDays();
            if (daysStuck < thresholdDays) {
                continue;
            }

            nudge(complaint, status, daysStuck);
            complaint.setReActivityNudgedAt(now);
            complaintRepository.save(complaint);
            nudged++;
        }

        if (nudged > 0) {
            log.info("RE activity nudge sweep notified on {} stuck record(s)", nudged);
        }
        return nudged;
    }

    private void escalate(Complaint complaint, ReActivityStatus statusAtExpiry) {
        String owner = complaint.getAssignedOfficer();
        if (owner == null || owner.isBlank()) {
            log.warn("Complaint {} went OVERDUE with no assigned officer to escalate to",
                    complaint.getComplaintNumber());
            return;
        }
        notificationService.send(owner, "RE_ACTIVITY_OVERDUE",
                "RE has not responded within the deadline",
                "The regulated entity has not submitted a response for complaint "
                        + complaint.getComplaintNumber() + ". Last recorded activity: "
                        + statusAtExpiry.name() + ".",
                complaint.getComplaintNumber(), "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber());
    }

    private void nudge(Complaint complaint, ReActivityStatus status, long daysStuck) {
        String owner = complaint.getAssignedOfficer();
        if (owner == null || owner.isBlank()) {
            return;
        }
        notificationService.send(owner, "RE_ACTIVITY_NUDGE",
                "RE progress has stalled",
                "Complaint " + complaint.getComplaintNumber() + " has been at "
                        + status.name() + " for " + daysStuck + " day(s). Consider following up "
                        + "with the entity.",
                complaint.getComplaintNumber(), "COMPLAINT",
                "/complaint/" + complaint.getComplaintNumber());
    }

    private boolean booleanConfig(String key, boolean fallback) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .map(value -> Boolean.parseBoolean(value.trim()))
                .orElse(fallback);
    }

    private Optional<Integer> intConfig(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .flatMap(value -> {
                    try {
                        return Optional.of(Integer.parseInt(value.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Config {} is not a number: {} — ignoring", key, value);
                        return Optional.empty();
                    }
                });
    }
}
