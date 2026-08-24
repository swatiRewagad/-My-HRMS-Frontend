package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRuleCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleConditionRepository extends JpaRepository<AsgnRuleCondition, Long> {

    List<AsgnRuleCondition> findByRuleId(Long ruleId);

    List<AsgnRuleCondition> findByRuleIdIn(List<Long> ruleIds);
}
