package com.hrms.cms.repository;

import com.hrms.cms.entity.InAppNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, Long> {
    Page<InAppNotification> findByTargetUserIdOrderByCreatedAtDesc(String targetUserId, Pageable pageable);
    List<InAppNotification> findByTargetUserIdAndIsReadFalseOrderByCreatedAtDesc(String targetUserId);
    long countByTargetUserIdAndIsReadFalse(String targetUserId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.targetUserId = :userId AND n.isRead = false")
    int markAllReadByUserId(@Param("userId") String userId);

    @Modifying
    @Query("UPDATE InAppNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id IN :ids AND n.targetUserId = :userId")
    int markReadByIds(@Param("ids") List<Long> ids, @Param("userId") String userId);

    List<InAppNotification> findByRelatedEntityIdAndType(String relatedEntityId, String type);
}
