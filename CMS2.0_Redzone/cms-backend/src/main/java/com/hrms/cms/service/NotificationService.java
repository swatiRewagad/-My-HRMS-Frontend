package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.InAppNotification;
import com.hrms.cms.entity.NotificationDeliveryLog;
import com.hrms.cms.entity.NotificationEventChannel;
import com.hrms.cms.repository.InAppNotificationRepository;
import com.hrms.cms.repository.NotificationDeliveryLogRepository;
import com.hrms.cms.repository.NotificationEventChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    /** Channel names, matching NotificationDeliveryLog.channel. */
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";

    private final InAppNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final NotificationEventChannelRepository eventChannelRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final OutboundMessagePort outboundMessagePort;

    @Async
    @Transactional
    public void send(String targetUserId, String type, String title, String message,
                     String relatedEntityId, String relatedEntityType, String actionUrl) {
        InAppNotification notification = InAppNotification.builder()
                .targetUserId(targetUserId)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .actionUrl(actionUrl)
                .build();
        notification = notificationRepository.save(notification);

        messagingTemplate.convertAndSendToUser(
                targetUserId, "/queue/notifications",
                Map.of("id", notification.getId(), "type", type, "title", title, "message", message)
        );
    }

    /**
     * Raises one event to every configured recipient, on every channel the matrix enables
     * (UST658-661, UST663-668).
     *
     * <p>A separate method rather than a change to {@link #send}: that method has 30+ existing callers
     * which each address exactly one user id and must keep behaving identically. Widening it in place
     * would make every one of those call sites fan out silently.
     *
     * <p>Two things happen here that {@code send} cannot do. First, role names are expanded to real
     * user ids — see {@link NotificationRecipientResolver} for why role-addressed notifications were
     * previously delivered to nobody. Second, the channel decision comes from
     * NOTIFICATION_EVENT_CHANNEL, so "this event does not send email" is a row an assertion can read
     * rather than an absence of code.
     *
     * <p>Every attempt appends a {@link NotificationDeliveryLog} row per channel. That is what makes a
     * bell-only story verifiable: the test asserts no EMAIL row exists, instead of trusting that no
     * email code path was reached.
     *
     * <p>Not {@code @Async}, unlike {@link #send}: callers in scheduled jobs need the delivery-log rows
     * committed within the job's transaction so a test can observe them deterministically. An async
     * fan-out would make every assertion a race.
     *
     * @param recipientTokens roles, user ids or placeholders — see NotificationConfigService
     * @param complaint       context for per-complaint placeholders; may be null
     */
    @Transactional
    public void raiseEvent(Collection<String> recipientTokens, String type, String title, String message,
                           String relatedEntityId, String relatedEntityType, String actionUrl,
                           Complaint complaint) {

        Set<String> targets = recipientResolver.resolve(recipientTokens, complaint);
        if (targets.isEmpty()) {
            log.warn("Notification {} for {} resolved to no recipients — nothing sent", type, relatedEntityId);
            return;
        }

        NotificationEventChannel matrix = channelFor(type);

        for (String target : targets) {
            Long notificationId = null;

            if (matrix.isInApp()) {
                notificationId = persistInApp(target, type, title, message,
                        relatedEntityId, relatedEntityType, actionUrl);
            }

            if (matrix.isEmail()) {
                dispatch(CHANNEL_EMAIL, notificationId, target, type, title, message, relatedEntityId);
            }
            if (matrix.isSms()) {
                dispatch(CHANNEL_SMS, notificationId, target, type, title, message, relatedEntityId);
            }
        }
    }

    private Long persistInApp(String target, String type, String title, String message,
                              String relatedEntityId, String relatedEntityType, String actionUrl) {
        InAppNotification notification = notificationRepository.save(InAppNotification.builder()
                .targetUserId(target)
                .type(type)
                .title(title)
                .message(message)
                .relatedEntityId(relatedEntityId)
                .relatedEntityType(relatedEntityType)
                .actionUrl(actionUrl)
                .build());

        String failure = null;
        try {
            messagingTemplate.convertAndSendToUser(target, "/queue/notifications",
                    Map.of("id", notification.getId(), "type", type, "title", title, "message", message));
        } catch (Exception e) {
            // The row is already persisted, so the bell shows it on the next poll. A push failure is
            // logged as a failed attempt rather than rethrown — losing the whole notification because a
            // websocket was closed would be worse than delivering it late.
            failure = truncate(e.getMessage());
            log.debug("STOMP push failed for {} ({}): {}", target, type, e.getMessage());
        }

        log(notification.getId(), target, type, NotificationDeliveryLog.CHANNEL_IN_APP,
                failure == null ? NotificationDeliveryLog.STATUS_SENT : NotificationDeliveryLog.STATUS_FAILED,
                failure, relatedEntityId);

        return notification.getId();
    }

    private void dispatch(String channel, Long notificationId, String target, String type,
                          String title, String message, String relatedEntityId) {
        try {
            outboundMessagePort.send(channel, target, title, message, relatedEntityId);
            log(notificationId, target, type, channel, NotificationDeliveryLog.STATUS_SENT, null,
                    relatedEntityId);
        } catch (Exception e) {
            log(notificationId, target, type, channel, NotificationDeliveryLog.STATUS_FAILED,
                    truncate(e.getMessage()), relatedEntityId);
            log.warn("{} dispatch failed for {} ({}): {}", channel, target, type, e.getMessage());
        }
    }

    /**
     * The matrix row for an event, defaulting to in-app only.
     *
     * <p>An unconfigured event defaults to the bell ALONE rather than to email. An event that should
     * have emailed but did not is a missing notification; an event that emails a citizen when nobody
     * decided it should is an unwanted disclosure of case activity. The safe default is the quieter one.
     */
    private NotificationEventChannel channelFor(String type) {
        try {
            return eventChannelRepository.findByEventTypeAndIsActive(type, "Y")
                    .orElseGet(() -> defaultMatrix(type));
        } catch (Exception e) {
            log.warn("Channel matrix unavailable for {} — defaulting to in-app only: {}", type, e.getMessage());
            return defaultMatrix(type);
        }
    }

    private NotificationEventChannel defaultMatrix(String type) {
        return NotificationEventChannel.builder().eventType(type).inAppEnabled("Y")
                .emailEnabled("N").smsEnabled("N").build();
    }

    private void log(Long notificationId, String recipient, String type, String channel,
                     String status, String error, String complaintNumber) {
        try {
            deliveryLogRepository.save(NotificationDeliveryLog.builder()
                    .notificationId(notificationId)
                    .recipientUserId(recipient)
                    .notificationType(type)
                    .channel(channel)
                    .status(status)
                    .errorMessage(error)
                    .relatedComplaintNumber(complaintNumber)
                    .build());
        } catch (Exception e) {
            // Never let an audit-write failure suppress the notification itself.
            log.warn("Delivery-log write failed for {}/{}: {}", recipient, channel, e.getMessage());
        }
    }

    /** NotificationDeliveryLog.errorMessage is 1000 chars; a stack trace must not fail the insert. */
    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }

    @Transactional(readOnly = true)
    public Page<InAppNotification> getNotifications(String userId, int page, int size) {
        return notificationRepository.findByTargetUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(String userId) {
        return notificationRepository.countByTargetUserIdAndIsReadFalse(userId);
    }

    @Transactional(readOnly = true)
    public List<InAppNotification> getUnread(String userId) {
        return notificationRepository.findByTargetUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public int markAllRead(String userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }

    @Transactional
    public int markRead(List<Long> ids, String userId) {
        return notificationRepository.markReadByIds(ids, userId);
    }
}
