package com.hrms.cms.service;

import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrpcScheduledTasks {

    private final EmailDraftRepository draftRepository;
    private final NotificationService notificationService;
    private final ThresholdOverflowService thresholdService;

    @Scheduled(cron = "0 0 9 * * MON-FRI")
    @Transactional(readOnly = true)
    public void notifyPending3Days() {
        LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);
        List<EmailDraft> pendingDrafts = draftRepository.findByStatusOrderByCreatedAtDesc("ASSIGNED");

        pendingDrafts.stream()
                .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isBefore(threeDaysAgo))
                .forEach(draft -> {
                    notificationService.send(
                            draft.getAssignedTo(),
                            "PENDING_3DAY",
                            "Draft pending for 3+ days",
                            "Draft " + draft.getDraftId() + " has been pending since " + draft.getCreatedAt().toLocalDate(),
                            draft.getDraftId(),
                            "DRAFT",
                            "/crpc/draft/" + draft.getDraftId()
                    );
                });

        log.info("Sent 3-day pending notifications for {} drafts",
                pendingDrafts.stream().filter(d -> d.getCreatedAt() != null && d.getCreatedAt().isBefore(threeDaysAgo)).count());
    }

    @Scheduled(cron = "0 30 9 * * MON-FRI")
    public void checkDuplicateActivity() {
        List<EmailDraft> assigned = draftRepository.findByStatusOrderByCreatedAtDesc("ASSIGNED");

        assigned.stream()
                .filter(EmailDraft::isDuplicate)
                .forEach(draft -> {
                    notificationService.send(
                            draft.getAssignedTo(),
                            "DUPLICATE_ACTIVITY",
                            "Possible duplicate detected",
                            "Draft " + draft.getDraftId() + " may be a duplicate of " + draft.getParentComplaintId(),
                            draft.getDraftId(),
                            "DRAFT",
                            "/crpc/draft/" + draft.getDraftId()
                    );
                });
    }

    @Scheduled(cron = "0 0 10 * * MON-FRI")
    public void checkReviewerThresholds() {
        var workloads = thresholdService.getReviewerWorkload();
        workloads.forEach((reviewer, load) -> {
            thresholdService.checkAndRouteOverflow(reviewer);
        });
    }
}
