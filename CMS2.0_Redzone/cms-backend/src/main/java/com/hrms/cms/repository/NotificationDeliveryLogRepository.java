package com.hrms.cms.repository;

import com.hrms.cms.entity.NotificationDeliveryLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationDeliveryLogRepository
        extends JpaRepository<NotificationDeliveryLog, Long> {

    List<NotificationDeliveryLog> findByNotificationIdOrderByAttemptedAtAsc(Long notificationId);

    Page<NotificationDeliveryLog> findByRecipientUserIdOrderByAttemptedAtDesc(String recipientUserId,
                                                                             Pageable pageable);

    Page<NotificationDeliveryLog> findByStatusOrderByAttemptedAtDesc(String status,
                                                                    Pageable pageable);

    List<NotificationDeliveryLog> findByRelatedComplaintNumberOrderByAttemptedAtDesc(
            String relatedComplaintNumber);

    long countByStatus(String status);
}
