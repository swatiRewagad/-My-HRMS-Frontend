package com.rbi.cms.rules.repository;

import com.rbi.cms.rules.entity.RuleDefinition;
import com.rbi.cms.rules.entity.RuleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, Long> {

    Optional<RuleDefinition> findByRuleCode(String ruleCode);

    List<RuleDefinition> findByStatus(RuleStatus status);

    List<RuleDefinition> findByCategoryCode(String categoryCode);

    List<RuleDefinition> findByCategoryCodeAndStatus(String categoryCode, RuleStatus status);

    @Query("SELECT r FROM RuleDefinition r WHERE r.status = 'ACTIVE' " +
           "AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :now) " +
           "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :now)")
    List<RuleDefinition> findActiveEffectiveRules(@Param("now") Instant now);

    @Query("SELECT r FROM RuleDefinition r WHERE r.status = 'ACTIVE' " +
           "AND r.category.code = :categoryCode " +
           "AND (r.effectiveFrom IS NULL OR r.effectiveFrom <= :now) " +
           "AND (r.effectiveTo IS NULL OR r.effectiveTo >= :now)")
    List<RuleDefinition> findActiveEffectiveRulesByCategory(
            @Param("categoryCode") String categoryCode,
            @Param("now") Instant now);
}
