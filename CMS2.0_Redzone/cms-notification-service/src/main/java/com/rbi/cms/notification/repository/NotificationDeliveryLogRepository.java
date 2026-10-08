package com.rbi.cms.notification.repository;

import com.rbi.cms.notification.entity.NotificationDeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLog, Long> {

    /**
     * Settles a queued attempt, and reports whether it actually settled one.
     *
     * <p>A bulk update rather than find-mutate-save so the {@code status = 'PENDING'} guard and the
     * write are one statement. Two consumers redelivered the same Kafka message would otherwise both
     * read PENDING and both write, and the second would overwrite a SENT row with FAILED. The returned
     * row count is how the listener tells "I settled it" from "someone already did".
     *
     * <p>Matching on PENDING also makes redelivery safe in the normal case: Kafka guarantees at-least-once,
     * so the same dispatch WILL occasionally arrive twice, and the second attempt must be a no-op rather
     * than a second email.
     */
    @Modifying
    @Query("UPDATE NotificationDeliveryLog d SET d.status = :status, d.errorMessage = :errorMessage, "
            + "d.settledAt = :settledAt WHERE d.dispatchRef = :dispatchRef AND d.status = 'PENDING'")
    int settle(@Param("dispatchRef") String dispatchRef,
               @Param("status") String status,
               @Param("errorMessage") String errorMessage,
               @Param("settledAt") LocalDateTime settledAt);
}
