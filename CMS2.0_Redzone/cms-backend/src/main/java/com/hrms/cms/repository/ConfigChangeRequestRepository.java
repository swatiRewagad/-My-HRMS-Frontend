package com.hrms.cms.repository;

import com.hrms.cms.entity.ConfigChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfigChangeRequestRepository extends JpaRepository<ConfigChangeRequest, Long> {

    List<ConfigChangeRequest> findByStatusOrderByRequestedAtDesc(String status);

    List<ConfigChangeRequest> findByConfigKeyOrderByRequestedAtDesc(String configKey);

    Optional<ConfigChangeRequest> findByConfigKeyAndStatus(String configKey, String status);
}
