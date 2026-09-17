package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.repository.AppealRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Interim {@link AaWorkflowNotifier}, active only until S3C registers a real one.
 *
 * {@code @ConditionalOnMissingBean} means S3C simply declaring its own implementation replaces this with
 * no change here and no coordination — the seam swaps itself.
 *
 * What it does honestly: STAFF notifications go through NotificationService, which works end to end
 * today. What it does NOT do: pretend to reach the citizen. There is no SMS or email transport in this
 * deployment, so an appellant notification is logged as owed and left for S3C's delivery log. Writing a
 * bell row for a citizen who has no bell would be worse than doing nothing, because it would read as
 * "notified".
 */
@Configuration
@Slf4j
class AaWorkflowNotifierFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(AaWorkflowNotifier.class)
    AaWorkflowNotifier aaWorkflowNotifierFallback(NotificationService notificationService,
                                                  AppealRepository appealRepository) {
        return new AaWorkflowNotifierFallback(notificationService, appealRepository);
    }
}

@RequiredArgsConstructor
@Slf4j
class AaWorkflowNotifierFallback implements AaWorkflowNotifier {

    private final NotificationService notificationService;
    private final AppealRepository appealRepository;

    @Override
    public void notifyAppellant(String appealNumber, AaWorkflowEvent event) {
        // Deliberately not sent. The appellant is a citizen with no in-app inbox, and there is no
        // working email or SMS gateway, so there is nothing to send through. S3C persists these to a
        // delivery log; until then this is recorded as an outstanding obligation, not a success.
        String contact = appealRepository.findByAppealNumber(appealNumber)
                .map(Appeal::getAppellantEmail)
                .orElse("unknown");
        log.info("AA notification OWED to appellant of {} ({}) for {} — no citizen transport is "
                        + "configured; awaiting the S3C delivery log",
                appealNumber, contact, event.getMessageKey());
    }

    @Override
    public void notifyOfficer(String userId, String appealNumber, AaWorkflowEvent event) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        afterCommit(() -> {
            try {
                notificationService.send(userId, "ASSIGNMENT", event.getMessageKey(),
                        appealNumber, appealNumber, "APPEAL", "/aa/appeals/" + appealNumber);
            } catch (Exception e) {
                log.warn("AA notification: could not notify {} about {}: {}",
                        userId, appealNumber, e.getMessage());
            }
        });
    }

    @Override
    public void notifyRemandTarget(String userId, String complaintNumber, String appealNumber) {
        if (userId == null || userId.isBlank()) {
            log.warn("AA remand of {} has no target officer to notify — complaint {} may sit unowned",
                    appealNumber, complaintNumber);
            return;
        }
        afterCommit(() -> {
            try {
                notificationService.send(userId, "ESCALATION", AaWorkflowEvent.REMANDED.getMessageKey(),
                        complaintNumber, complaintNumber, "COMPLAINT",
                        "/complaints/" + complaintNumber);
            } catch (Exception e) {
                log.warn("AA remand: could not notify {} about {}: {}",
                        userId, complaintNumber, e.getMessage());
            }
        });
    }

    /**
     * Defers until the transaction commits.
     *
     * NotificationService.send is @Async on another bean and so commits its own transaction
     * immediately. Sending inline would notify an officer about a transition that then rolled back.
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
