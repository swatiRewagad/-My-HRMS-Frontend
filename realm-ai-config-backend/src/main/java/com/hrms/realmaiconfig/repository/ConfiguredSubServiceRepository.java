package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.ConfiguredSubService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguredSubServiceRepository extends JpaRepository<ConfiguredSubService, Long> {
    List<ConfiguredSubService> findByConfiguredServiceId(Long configuredServiceId);
    void deleteByConfiguredServiceIdIn(List<Long> configuredServiceIds);
}
