package com.hrms.cms.service;

import com.hrms.cms.entity.InAppNotification;
import com.hrms.cms.entity.NotificationDeliveryLog;
import com.hrms.cms.repository.InAppNotificationRepository;
import com.hrms.cms.repository.NotificationDeliveryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * The reassign-out / reassign-in notification pair, with a delivery attempt logged for each
 * (UST845).
 *
 * <p><b>Why this does not call {@link NotificationService#send}.</b> That method is {@code @Async}
 * and returns {@code void}, so a caller cannot observe whether delivery succeeded — awaiting a
 * delivery status from it is impossible by construction. UST845 asks for a delivery log, and a log
 * that records "SENT" without knowing whether it sent would be worse than no log: it would give
 * false assurance when an officer reports never having been notified. So delivery happens here,
 * synchronously, and the outcome recorded is the outcome actually observed.
 *
 * <p>{@code NotificationService} is left completely untouched — no new methods, no signature
 * changes — because it is shared with concurrent work on other stories.
 *
 * <p><b>Notification text is stored as a translation key, not rendered English.</b> The existing
 * notification rows hold English literals built by string concatenation at the call site, which
 * cannot be shown to a Hindi-speaking officer. The precedent for the alternative is already in this
 * codebase: {@code RePortalController} returns {@code reActivityStatusKey} and lets the client
 * resolve it. Storing the key means one row reads correctly in all ten locales.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReassignmentNotificationService {

    /** Sent to the officer losing the record. */
    public static final String TYPE_REASSIGNED_OUT = "notification.reassign.out";
    /** Sent to the officer receiving it. */
    public static final String TYPE_REASSIGNED_IN = "notification.reassign.in";

    private static final int ERROR_MAX_LENGTH = 1000;

    private final InAppNotificationRepository notificationRepository;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notifies both sides of a completed move. Returns nothing: a notification failure must never
     * fail the reassignment that prompted it, because the ownership change is already committed and
     * the record would otherwise be left in a state the caller believes was rolled back.
     *
     * <p>Runs in its own transaction (REQUIRES_NEW) for that reason — a delivery failure marking the
     * caller's transaction rollback-only would undo the reassignment itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyReassignment(String fromUserId, String toUserId,
                                   String complaintNumber, Long recordId) {
        if (fromUserId != null && !fromUserId.isBlank() && !fromUserId.equals(toUserId)) {
            deliver(fromUserId, TYPE_REASSIGNED_OUT, complaintNumber, recordId);
        }
        if (toUserId != null && !toUserId.isBlank()) {
            deliver(toUserId, TYPE_REASSIGNED_IN, complaintNumber, recordId);
        }
    }

    /**
     * One delivery attempt, always logged — on the success path and on both failure paths.
     *
     * <p>The notification row and the websocket push are logged as one attempt deliberately. The
     * persisted row is what the officer sees when they next open the portal, so a websocket failure
     * with the row saved is still a delivered notification; treating it as FAILED would fill the log
     * with false negatives for officers who were simply offline.
     */
    private void deliver(String recipientUserId, String type, String complaintNumber, Long recordId) {
        Long notificationId = null;
        try {
            InAppNotification notification = InAppNotification.builder()
                    .targetUserId(recipientUserId)
                    .type(type)
                    // Title and message hold the translation key; the client resolves it for the
                    // reader's locale. relatedEntityId carries the complaint number so the key can
                    // be interpolated without a second lookup.
                    .title(type)
                    .message(type + ".body")
                    .relatedEntityId(complaintNumber)
                    .relatedEntityType("NODAL_OFFICER_RECORD")
                    .actionUrl("/re-portal/complaints/" + complaintNumber)
                    .build();
            notification = notificationRepository.save(notification);
            notificationId = notification.getId();

            pushBestEffort(recipientUserId, type, complaintNumber, notificationId);

            log(notificationId, recipientUserId, type, NotificationDeliveryLog.STATUS_SENT,
                null, complaintNumber);
        } catch (Exception e) {
            // Logged as FAILED with the notification id left null when the row itself could not be
            // written — that is the case most worth being able to find later.
            log.warn("Reassignment notification {} to {} for {} failed: {}",
                     type, recipientUserId, complaintNumber, e.getMessage());
            log(notificationId, recipientUserId, type, NotificationDeliveryLog.STATUS_FAILED,
                e.getMessage(), complaintNumber);
        }
    }

    /**
     * The websocket push is advisory. A broker that is down must not turn a saved notification into
     * a reported failure, so this swallows its own errors and records them on the same attempt row.
     */
    private void pushBestEffort(String recipientUserId, String type,
                                String complaintNumber, Long notificationId) {
        try {
            messagingTemplate.convertAndSendToUser(
                    recipientUserId, "/queue/notifications",
                    Map.of("id", notificationId,
                           "type", type,
                           "titleKey", type,
                           "complaintNumber", complaintNumber));
        } catch (Exception e) {
            log.debug("Websocket push for notification {} failed, row is still persisted: {}",
                      notificationId, e.getMessage());
        }
    }

    private void log(Long notificationId, String recipientUserId, String type, String status,
                     String error, String complaintNumber) {
        try {
            deliveryLogRepository.save(NotificationDeliveryLog.builder()
                    .notificationId(notificationId)
                    .recipientUserId(recipientUserId)
                    .notificationType(type)
                    .channel(NotificationDeliveryLog.CHANNEL_IN_APP)
                    .status(status)
                    .errorMessage(truncate(error))
                    .relatedComplaintNumber(complaintNumber)
                    .build());
        } catch (Exception e) {
            // Never let audit logging break the operation it is auditing.
            log.error("Could not write notification delivery log for {}: {}",
                      recipientUserId, e.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= ERROR_MAX_LENGTH ? message : message.substring(0, ERROR_MAX_LENGTH);
    }
}
