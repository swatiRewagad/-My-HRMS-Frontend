package com.rbi.cms.rules.repository;

import com.rbi.cms.rules.entity.RuleVersionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleVersionHistoryRepository extends JpaRepository<RuleVersionHistory, Long> {

    List<RuleVersionHistory> findByRuleIdOrderByVersionDesc(Long ruleId);
}
