package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRuleSetPublication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PublicationRepository extends JpaRepository<AsgnRuleSetPublication, Long> {

    Optional<AsgnRuleSetPublication> findByTenantIdAndDecisionPoint(String tenantId, String decisionPoint);
}
