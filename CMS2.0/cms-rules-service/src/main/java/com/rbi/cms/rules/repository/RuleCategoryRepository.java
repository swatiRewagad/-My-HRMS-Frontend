package com.rbi.cms.rules.repository;

import com.rbi.cms.rules.entity.RuleCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RuleCategoryRepository extends JpaRepository<RuleCategory, Long> {
    Optional<RuleCategory> findByCode(String code);
}
