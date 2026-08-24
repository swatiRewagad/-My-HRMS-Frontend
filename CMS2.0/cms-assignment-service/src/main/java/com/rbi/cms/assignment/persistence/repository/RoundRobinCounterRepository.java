package com.rbi.cms.assignment.persistence.repository;

import com.rbi.cms.assignment.domain.entity.AsgnRoundRobinCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoundRobinCounterRepository extends JpaRepository<AsgnRoundRobinCounter, Long> {

    Optional<AsgnRoundRobinCounter> findByTenantIdAndGroupIdAndStrategyKey(
            String tenantId, String groupId, String strategyKey);
}
