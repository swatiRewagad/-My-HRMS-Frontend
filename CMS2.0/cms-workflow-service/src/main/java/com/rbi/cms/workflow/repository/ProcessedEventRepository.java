package com.rbi.cms.workflow.repository;

import com.rbi.cms.workflow.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedEvent e WHERE e.processedAt < :cutoff")
    int deleteEventsOlderThan(Instant cutoff);
}
