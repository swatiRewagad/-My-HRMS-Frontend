package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRuleSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleSetRepository extends JpaRepository<AsgnRuleSet, Long> {

    List<AsgnRuleSet> findByTenantIdAndActive(String tenantId, boolean active);

    Optional<AsgnRuleSet> findByTenantIdAndDecisionPoint(String tenantId, String decisionPoint);
}
