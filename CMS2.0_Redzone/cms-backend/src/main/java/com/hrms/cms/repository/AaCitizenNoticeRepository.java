package com.hrms.cms.repository;

import com.hrms.cms.entity.AaCitizenNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AaCitizenNoticeRepository extends JpaRepository<AaCitizenNotice, Long> {

    List<AaCitizenNotice> findByAppealNumberOrderByCreatedAtDescIdDesc(String appealNumber);

    List<AaCitizenNotice> findByAppealNumberAndEventCodeOrderByIdAsc(String appealNumber, String eventCode);

    /** Idempotency guard: a retried request must not create a second obligation. */
    boolean existsByAppealNumberAndEventCodeAndRecipientRoleAndDedupeKey(
            String appealNumber, String eventCode, String recipientRole, String dedupeKey);

    /**
     * The dispatch queue for a future gateway. Nothing in Phase 1 consumes it -- it exists so that
     * "what do we still owe citizens?" is answerable today.
     */
    @Query("""
            SELECT n FROM AaCitizenNotice n
             WHERE n.status = 'PENDING'
               AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)
             ORDER BY n.createdAt ASC
            """)
    List<AaCitizenNotice> findDispatchable(@Param("now") LocalDateTime now);

    long countByAppealNumberAndStatus(String appealNumber, String status);

    /**
     * Cancels still-undispatched notices for an event, used when a hearing is moved before its notice
     * ever went out. Scoped to PENDING so a notice already sent is never retro-cancelled.
     */
    @Query("""
            SELECT n FROM AaCitizenNotice n
             WHERE n.appealNumber = :appealNumber
               AND n.eventCode = :eventCode
               AND n.status = 'PENDING'
            """)
    List<AaCitizenNotice> findPendingForEvent(@Param("appealNumber") String appealNumber,
                                              @Param("eventCode") String eventCode);

    void deleteByAppealNumber(String appealNumber);
}
