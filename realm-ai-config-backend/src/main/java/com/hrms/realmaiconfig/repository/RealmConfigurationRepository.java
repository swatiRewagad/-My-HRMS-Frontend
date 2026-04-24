package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.RealmConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RealmConfigurationRepository extends JpaRepository<RealmConfiguration, Long> {
    Optional<RealmConfiguration> findByRealmIdAndIsActiveTrue(String realmId);
}
