package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnDecisionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DecisionLogRepository extends JpaRepository<AsgnDecisionLog, Long> {

    List<AsgnDecisionLog> findByDecisionPointOrderByCreatedAtDesc(String decisionPoint, Pageable pageable);

    List<AsgnDecisionLog> findByCaseRefOrderByCreatedAtDesc(String caseRef);

    List<AsgnDecisionLog> findByMatchedRuleCodeOrderByCreatedAtDesc(String matchedRuleCode, Pageable pageable);

    @Query("SELECT d FROM AsgnDecisionLog d WHERE d.tenantId = :tenantId ORDER BY d.createdAt DESC")
    List<AsgnDecisionLog> findRecentByTenant(@Param("tenantId") String tenantId, Pageable pageable);
}
