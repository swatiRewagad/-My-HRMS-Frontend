package com.rbi.cms.rules.repository;

import com.rbi.cms.rules.entity.RuleDeploymentLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RuleDeploymentLogRepository extends JpaRepository<RuleDeploymentLog, Long> {

    List<RuleDeploymentLog> findAllByOrderByDeployedAtDesc();
}
