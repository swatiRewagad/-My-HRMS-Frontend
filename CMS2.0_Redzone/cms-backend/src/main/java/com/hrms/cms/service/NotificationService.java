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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        pushAfterCommit(targetUserId,
                Map.of("id", notification.getId(), "type", type, "title", title, "message", message));
    }

    /**
     * Publishes the websocket frame only once the row it refers to is COMMITTED.
     *
     * <p>WHY: the push used to be sent inline, still inside this method's {@code @Transactional}
     * boundary. The client treats a push as a signal to RE-READ over REST rather than as a row to
     * render (see the frontend NotificationService.onPush), and that re-read is a separate
     * connection which cannot see this transaction's uncommitted insert. The frame therefore raced
     * its own row: {@code GET /unread-count} answered with the count from BEFORE the notification
     * existed, the badge stayed one behind, and the only way to see the new item was the page
     * refresh the whole live channel exists to avoid. Reproduced end to end — CONNECT / CONNECTED /
     * SUBSCRIBE / MESSAGE all arrived correctly and the badge still did not move.
     *
     * <p>Deferring also removes a worse failure mode: a frame for a row whose transaction later
     * rolls back, which told an officer about work that does not exist and sent them to a 404.
     *
     * <p>With no active transaction (a direct unit-test call, or a caller outside a transaction) the
     * frame goes out immediately, so behaviour there is unchanged. Delivery failure is logged and
     * never propagated: the notification is already persisted and the REST path still serves it, so
     * a broker problem must not turn into a failed business operation.
     */
    private void pushAfterCommit(String targetUserId, Map<String, Object> payload) {
        Runnable push = () -> {
            try {
                messagingTemplate.convertAndSendToUser(targetUserId, "/queue/notifications", payload);
            } catch (Exception e) {
                log.warn("Could not push notification {} to {}: {}",
                        payload.get("id"), targetUserId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
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

    /**
     * KNOWN LIMITATION — the same before-commit push race {@link #pushAfterCommit} fixes in
     * {@link #send} is still present here, and is deliberately NOT fixed in the same change.
     *
     * <p>The push below is sent inside {@code raiseEvent}'s transaction, so a client that reacts by
     * re-reading over REST can still miss this row. The reason it is left alone is that the
     * {@link NotificationDeliveryLog} status written a few lines down is derived from whether this
     * very call threw. Deferring the push to {@code afterCommit} would mean logging SENT before the
     * outcome is known, which turns an audit row that currently records a real delivery attempt into
     * an optimistic guess. Choosing between "accurate log" and "no re-read race" on this path is an
     * audit-semantics decision, not a bug fix, so it is raised rather than taken unilaterally.
     *
     * <p>Impact is limited: this path serves the configurable multi-channel fan-out, whose consumers
     * read the delivery log rather than the live bell. The bell's own producer is {@link #send}.
     */
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
