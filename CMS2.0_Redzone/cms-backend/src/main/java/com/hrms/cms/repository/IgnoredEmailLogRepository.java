package com.hrms.cms.repository;

import com.hrms.cms.entity.IgnoredEmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface IgnoredEmailLogRepository extends JpaRepository<IgnoredEmailLog, Long> {

    List<IgnoredEmailLog> findByOrderByReceivedAtDesc();

    @Query("SELECT l FROM IgnoredEmailLog l WHERE "
            + "(:senderEmail IS NULL OR LOWER(l.senderEmail) LIKE LOWER(CONCAT('%', :senderEmail, '%'))) AND "
            + "(:ruleId IS NULL OR l.matchedRuleId = :ruleId) AND "
            + "(:from IS NULL OR l.receivedAt >= :from) AND "
            + "(:to IS NULL OR l.receivedAt <= :to) "
            + "ORDER BY l.receivedAt DESC")
    List<IgnoredEmailLog> search(@Param("senderEmail") String senderEmail,
                                 @Param("ruleId") Long ruleId,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    long countByMatchedRuleId(Long matchedRuleId);
}
