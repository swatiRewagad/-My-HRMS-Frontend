package com.rbi.cms.ingestion.repository;

import com.rbi.cms.common.enums.OutboxEventStatus;
import com.rbi.cms.ingestion.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEventStatus status);

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.retryCount < :maxRetries ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents(@Param("status") OutboxEventStatus status, @Param("maxRetries") int maxRetries);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :status, o.publishedAt = :publishedAt WHERE o.eventId = :eventId")
    void markAsPublished(@Param("eventId") Long eventId, @Param("status") OutboxEventStatus status, @Param("publishedAt") Instant publishedAt);
}
