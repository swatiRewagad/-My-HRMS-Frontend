package com.hrms.cms.service.triage;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReResponseTracker;
import com.hrms.cms.repository.ReResponseTrackerRepository;
import com.hrms.cms.service.BusinessHoursService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReResponsivenessService {

    private static final int DEFAULT_RE_RESPONSE_WINDOW_DAYS = 30;

    private final ReResponseTrackerRepository trackerRepo;
    private final BusinessHoursService businessHoursService;

    @Transactional
    public ReResponseTracker trackForwarding(Complaint complaint, Long regulatedEntityId) {
        int windowDays = DEFAULT_RE_RESPONSE_WINDOW_DAYS;
        LocalDateTime forwardedAt = LocalDateTime.now();
        LocalDateTime windowExpires = businessHoursService.calculateDueDate(forwardedAt, windowDays * 8);

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
