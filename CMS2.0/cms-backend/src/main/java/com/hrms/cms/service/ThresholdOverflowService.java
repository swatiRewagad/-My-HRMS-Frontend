package com.hrms.cms.service;

import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThresholdOverflowService {

    private final EmailDraftRepository draftRepository;
    private final NotificationService notificationService;

    @Value("${crpc.threshold.max-per-reviewer:30}")
    private int maxPerReviewer;

    @Value("${crpc.threshold.overflow-target:CRPC_HEAD}")
    private String overflowTarget;

    @Transactional(readOnly = true)
    public Map<String, Long> getReviewerWorkload() {
        List<EmailDraft> pending = draftRepository.findByStatusOrderByCreatedAtDesc("SENT_FOR_APPROVAL");
        return pending.stream()
                .filter(d -> d.getReviewerAssignedTo() != null)
                .collect(Collectors.groupingBy(EmailDraft::getReviewerAssignedTo, Collectors.counting()));
    }

    @Transactional
    public void checkAndRouteOverflow(String reviewerId) {
        long currentLoad = draftRepository.findByAssignedToAndStatusOrderByCreatedAtDesc(reviewerId, "SENT_FOR_APPROVAL").size();
        if (currentLoad > maxPerReviewer) {
            long overflow = currentLoad - maxPerReviewer;
            log.warn("Reviewer {} exceeds threshold ({}/{}). Overflow: {} items", reviewerId, currentLoad, maxPerReviewer, overflow);

            notificationService.send(
                    overflowTarget,
                    "ASSIGNMENT",
                    "Reviewer threshold breached",
                    "Reviewer " + reviewerId + " has " + currentLoad + " pending items (max: " + maxPerReviewer + "). " + overflow + " items need reassignment.",
                    reviewerId,
                    "TRANSFER",
                    "/crpc/ops-head"
            );
        }
    }

    @Transactional
    public int reassignOverflow(String fromReviewer, String toReviewer) {
        List<EmailDraft> pending = draftRepository.findByAssignedToAndStatusOrderByCreatedAtDesc(fromReviewer, "SENT_FOR_APPROVAL");
        if (pending.size() <= maxPerReviewer) return 0;

        List<EmailDraft> overflow = pending.subList(maxPerReviewer, pending.size());
        int count = 0;
        for (EmailDraft draft : overflow) {
            draft.setReviewerAssignedTo(toReviewer);
            draftRepository.save(draft);
            count++;
        }

        notificationService.send(toReviewer, "ASSIGNMENT",
                count + " drafts reassigned to you",
                "Overflow from " + fromReviewer + " — " + count + " drafts reassigned.",
                null, "TRANSFER", "/crpc/reviewer");

        log.info("Reassigned {} overflow drafts from {} to {}", count, fromReviewer, toReviewer);
        return count;
    }

    public int getMaxPerReviewer() {
        return maxPerReviewer;
    }
}
