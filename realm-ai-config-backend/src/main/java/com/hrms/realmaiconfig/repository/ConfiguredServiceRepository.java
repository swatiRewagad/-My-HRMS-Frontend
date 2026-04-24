package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.ConfiguredService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConfiguredServiceRepository extends JpaRepository<ConfiguredService, Long> {
    List<ConfiguredService> findByConfigurationId(Long configurationId);
    void deleteByConfigurationId(Long configurationId);
}
