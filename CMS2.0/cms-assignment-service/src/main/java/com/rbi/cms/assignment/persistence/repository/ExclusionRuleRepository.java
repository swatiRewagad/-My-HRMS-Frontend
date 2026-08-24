package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnExclusionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExclusionRuleRepository extends JpaRepository<AsgnExclusionRule, Long> {

    List<AsgnExclusionRule> findByTenantIdAndActive(String tenantId, boolean active);

    List<AsgnExclusionRule> findByTenantIdOrderByCreatedAtDesc(String tenantId);
}
