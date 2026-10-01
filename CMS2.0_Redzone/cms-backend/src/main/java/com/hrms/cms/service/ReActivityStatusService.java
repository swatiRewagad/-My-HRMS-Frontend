package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.entity.TimelineEventSource;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * The single write path for the RE Activity Status ladder (UST846).
 *
 * Every transition goes through {@link #recordActivity} so three invariants hold everywhere:
 * the ladder only moves forward, a timeline row is always written alongside the status, and the
 * nudge threshold is snapshotted at the moment the record enters the status (UST850).
 *
 * RE actions must never set the status by name — they declare what happened and this class derives
 * the level. That is what makes the "RE cannot manually set the status" requirement structural
 * rather than a validation rule someone can forget to apply on a new endpoint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReActivityStatusService {

    /**
     * Nudge thresholds are per-status because "opened but untouched for 3 days" and "documents
     * uploaded but not submitted for 3 days" are not equally concerning. Falls back to the generic
     * key, then to the compiled default, so a missing row degrades rather than throws.
     */
    static final String CONFIG_PREFIX = "cms.re.activity.nudge_days.";
    static final String CONFIG_DEFAULT_KEY = "cms.re.activity.nudge_days.default";
    static final int FALLBACK_NUDGE_DAYS = 3;

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final SystemConfigRepository systemConfigRepository;

    /**
     * Advances the ladder if {@code target} is genuinely ahead of where the record already is.
     * Returns true only when the status actually changed.
     *
     * <p>Runs in its own transaction (REQUIRES_NEW) when called for a read-only action such as
     * opening a record: the caller's transaction is marked read-only and would otherwise reject
     * the write, and an activity signal failing must never fail the RE user's actual request.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordActivity(Long complaintId, ReActivityStatus target, String actor,
                                  TimelineEventSource source, String remarks) {
        Complaint complaint = complaintRepository.findById(complaintId).orElse(null);
        if (complaint == null) {
            log.warn("RE activity {} ignored: complaint {} not found", target, complaintId);
            return false;
        }
        return applyTransition(complaint, target, actor, source, remarks);
    }

    /**
     * Same transition, for callers that already hold the loaded entity inside a writable
     * transaction. Avoids a second SELECT and keeps the status change atomic with the caller's
     * own writes — the response-submitted path needs that, because a status flip that survived a
     * rolled-back response would misreport the entity as having answered.
     */
    public boolean recordActivity(Complaint complaint, ReActivityStatus target, String actor,
                                  TimelineEventSource source, String remarks) {
        return applyTransition(complaint, target, actor, source, remarks);
    }

    private boolean applyTransition(Complaint complaint, ReActivityStatus target, String actor,
                                    TimelineEventSource source, String remarks) {
        ReActivityStatus current = currentStatusOf(complaint);

        if (!current.canAdvanceTo(target)) {
            // Not an error: re-opening an already-reviewed record is normal and must not regress
            // the badge, nor spam the history with a row per page view.
            log.debug("RE activity for complaint {} stays at {} (rejected advance to {})",
                    complaint.getId(), current, target);
            return false;
        }

        int nudgeDays = resolveNudgeDays(target);

        complaint.setReActivityStatus(target);
        complaint.setReActivityChangedAt(LocalDateTime.now());
        complaint.setReActivityNudgeDays(nudgeDays);
        // Cleared on every transition so the next status gets its own nudge allowance.
        complaint.setReActivityNudgedAt(null);
        complaintRepository.save(complaint);

        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("RE_ACTIVITY_" + target.name())
                .performedBy(actor == null || actor.isBlank() ? "RE_PORTAL" : actor)
                .remarks(remarks)
                .fromStatus(current.name())
                .toStatus(target.name())
                .eventSource(source)
                .build());

        log.info("RE activity for complaint {}: {} → {} (nudge threshold {}d, {})",
                complaint.getId(), current, target, nudgeDays, source);
        return true;
    }

    /**
     * A record that has never been touched has no stored value. Treating null as NOT_OPENED rather
     * than backfilling every historical row keeps the migration cheap and means the ladder reads
     * correctly for complaints that predate this feature.
     */
    public ReActivityStatus currentStatusOf(Complaint complaint) {
        return complaint.getReActivityStatus() == null
                ? ReActivityStatus.NOT_OPENED
                : complaint.getReActivityStatus();
    }

    /**
     * The threshold to snapshot for a status being entered. Per-status key wins, then the generic
     * default key, then the compiled fallback.
     */
    int resolveNudgeDays(ReActivityStatus status) {
        return intConfig(CONFIG_PREFIX + status.name().toLowerCase())
                .or(() -> intConfig(CONFIG_DEFAULT_KEY))
                .orElse(FALLBACK_NUDGE_DAYS);
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
