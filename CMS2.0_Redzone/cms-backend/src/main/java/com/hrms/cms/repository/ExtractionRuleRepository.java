package com.hrms.cms.repository;

import com.hrms.cms.entity.ExtractionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractionRuleRepository extends JpaRepository<ExtractionRule, Long> {

    List<ExtractionRule> findByIsActiveTrueOrderByPriorityAsc();

    List<ExtractionRule> findAllByOrderByPriorityAsc();

    Optional<ExtractionRule> findByRuleCode(String ruleCode);

    boolean existsByRuleCode(String ruleCode);

    long countByIsActiveTrue();
}
