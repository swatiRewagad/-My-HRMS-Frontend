package com.rbi.cms.eligibility.repository;

import com.rbi.cms.eligibility.entity.EligibilityAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EligibilityAuditRepository extends JpaRepository<EligibilityAudit, Long> {

    List<EligibilityAudit> findBySessionIdOrderByEvaluatedAtDesc(String sessionId);
}
