package com.hrms.cms.repository;

import com.hrms.cms.entity.CategorizationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategorizationRuleRepository extends JpaRepository<CategorizationRule, Long> {
    List<CategorizationRule> findByStatusOrderByRuleOrderAsc(String status);
    List<CategorizationRule> findByCategoryIdAndStatus(Long categoryId, String status);
    List<CategorizationRule> findBySourceAndStatus(String source, String status);
    List<CategorizationRule> findAllByOrderByRuleOrderAsc();
}
