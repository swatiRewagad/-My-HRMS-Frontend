package com.hrms.cms.service.triage;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.service.ReResponseDeadlineService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Tracks whether a Regulated Entity answered a forwarded complaint inside its statutory window, and flags
 * the ones that did not as eligible for an ex-parte decision.
 *
 * <h2>Why the window is not defined here</h2>
 * This class used to own its own copy of the window: 30 days hardcoded, multiplied by a literal 8 business
 * hours per day. Both were wrong, and they hid each other. {@code SYSTEM_CONFIG
 * timeline.re.response_deadline_days} is seeded 15 and is what {@link ReResponseDeadlineService} reads, so
 * the two services disagreed by a factor of two about the same statutory deadline — one set the date shown
 * to the officer and the entity, the other decided whether the entity had breached it. Separately, the
 * literal 8 against a 9-hour working day shortened the nominal window by an eighth (30 days ≈ 26.7).
 *
 * <p>Both numbers now come from {@link ReResponseDeadlineService}, which is the single definition. This is
 * delegation rather than a copied constant on purpose: a second copy is exactly how the original
 * disagreement arose, and the value is citizen-facing — it decides when an entity is chased and when a
 * complaint may proceed to adjudication without their reply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReResponsivenessService {

    private final ReResponseTrackerRepository trackerRepo;
    private final ReResponseDeadlineService deadlineService;

    @Transactional
    public ReResponseTracker trackForwarding(Complaint complaint, Long regulatedEntityId) {
        // Read once and pass it through: the stored windowDays and the stored windowExpiresAt must describe
        // the SAME window, and two independent config reads could straddle a change.
        int windowDays = deadlineService.responseWindowDays();
        LocalDateTime forwardedAt = LocalDateTime.now();
        LocalDateTime windowExpires = deadlineService.windowEndFrom(forwardedAt, windowDays);

        ReResponseTracker tracker = ReResponseTracker.builder()
                .complaintId(complaint.getId())
                .regulatedEntityId(regulatedEntityId)
                .forwardedAt(forwardedAt)
                .windowDays(windowDays)
                .windowExpiresAt(windowExpires)
                .breached(false)
                .exParteEligible(false)
                .build();

        return trackerRepo.save(tracker);
    }

    @Transactional
    public void recordResponse(Long complaintId) {
        trackerRepo.findByComplaintId(complaintId).ifPresent(tracker -> {
            tracker.setRespondedAt(LocalDateTime.now());
            boolean breached = tracker.getRespondedAt().isAfter(tracker.getWindowExpiresAt());
            tracker.setBreached(breached);
            trackerRepo.save(tracker);
            log.info("RE response recorded for complaint {}. Breached: {}", complaintId, breached);
        });
    }

    @Scheduled(cron = "0 0 22 * * *")
    @Transactional
    public void detectBreaches() {
        List<ReResponseTracker> pending = trackerRepo.findPendingBreaches(LocalDateTime.now());
        for (ReResponseTracker tracker : pending) {
            tracker.setBreached(true);
            tracker.setExParteEligible(true);
            tracker.setNotes("Auto-flagged: RE window expired without response");
            trackerRepo.save(tracker);
        }
        if (!pending.isEmpty()) {
            log.info("Detected {} new RE response breaches", pending.size());
        }
    }

    public ReRadarSummary getRadarForEntity(Long regulatedEntityId) {
        long total = trackerRepo.countByRegulatedEntityId(regulatedEntityId);
        long breached = trackerRepo.countByRegulatedEntityIdAndBreachedTrue(regulatedEntityId);

        List<ReResponseTracker> recent = trackerRepo.findByRegulatedEntityIdOrderByForwardedAtDesc(regulatedEntityId);
        double avgResponseHours = recent.stream()
                .filter(t -> t.getRespondedAt() != null)
                .mapToLong(t -> java.time.Duration.between(t.getForwardedAt(), t.getRespondedAt()).toHours())
                .average()
                .orElse(0);

        return ReRadarSummary.builder()
                .regulatedEntityId(regulatedEntityId)
                .totalForwarded(total)
                .totalBreached(breached)
                .breachRate(total > 0 ? (double) breached / total * 100 : 0)
                .averageResponseHours(avgResponseHours)
                .pendingResponses(recent.stream().filter(t -> t.getRespondedAt() == null && !t.isBreached()).count())
                .exParteEligibleCount(recent.stream().filter(ReResponseTracker::isExParteEligible).count())
                .build();
    }

    public List<ReResponseTracker> getBreachedCases() {
        return trackerRepo.findByBreachedTrueOrderByForwardedAtDesc();
    }

    @Getter
    @Builder
    public static class ReRadarSummary {
        private final Long regulatedEntityId;
        private final long totalForwarded;
        private final long totalBreached;
        private final double breachRate;
        private final double averageResponseHours;
        private final long pendingResponses;
        private final long exParteEligibleCount;
    }
}
