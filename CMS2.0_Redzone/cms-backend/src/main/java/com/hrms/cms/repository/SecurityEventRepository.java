package com.hrms.cms.repository;

import com.hrms.cms.entity.SecurityEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface SecurityEventRepository extends JpaRepository<SecurityEvent, Long> {

    long countBySubjectAndEventTypeAndOccurredAtAfter(String subject, String eventType, LocalDateTime after);

    long countBySubjectAndOccurredAtAfter(String subject, LocalDateTime after);

    Page<SecurityEvent> findBySubjectOrderByOccurredAtDesc(String subject, Pageable pageable);

    Page<SecurityEvent> findByOrderByOccurredAtDesc(Pageable pageable);

    @Query("SELECT COUNT(DISTINCT e.entityCode) FROM SecurityEvent e "
            + "WHERE e.subject = :subject AND e.occurredAt > :after AND e.entityCode IS NOT NULL")
    long countDistinctEntityCodes(@Param("subject") String subject, @Param("after") LocalDateTime after);

    @Modifying
    @Query("DELETE FROM SecurityEvent e WHERE e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
