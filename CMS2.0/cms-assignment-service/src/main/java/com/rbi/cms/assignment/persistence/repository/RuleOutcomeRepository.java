package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRuleOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RuleOutcomeRepository extends JpaRepository<AsgnRuleOutcome, Long> {

    Optional<AsgnRuleOutcome> findByRuleId(Long ruleId);

    List<AsgnRuleOutcome> findByRuleIdIn(List<Long> ruleIds);

    Optional<AsgnRuleOutcome> findByVersionIdAndIsDefault(Long versionId, boolean isDefault);
}
