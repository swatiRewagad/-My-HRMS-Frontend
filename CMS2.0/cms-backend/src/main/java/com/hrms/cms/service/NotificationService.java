package com.hrms.cms.service;

import com.hrms.cms.entity.InAppNotification;
import com.hrms.cms.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final InAppNotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

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
