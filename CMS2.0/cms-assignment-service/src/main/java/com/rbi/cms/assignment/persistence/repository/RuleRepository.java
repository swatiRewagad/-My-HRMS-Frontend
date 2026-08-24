package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RuleRepository extends JpaRepository<AsgnRule, Long> {

    List<AsgnRule> findByVersionIdOrderByPriorityAscRowOrderAsc(Long versionId);

    List<AsgnRule> findByVersionIdAndEnabled(Long versionId, boolean enabled);
}
