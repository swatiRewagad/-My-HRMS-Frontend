package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcDashboardFilter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CepcDashboardFilterRepository extends JpaRepository<CepcDashboardFilter, Long> {

    Optional<CepcDashboardFilter> findByDimensionAndFilterCode(String dimension, String filterCode);

    boolean existsByDimensionAndFilterCode(String dimension, String filterCode);

    List<CepcDashboardFilter> findByDimensionAndIsActiveOrderByDisplayOrderAsc(String dimension, String isActive);
}
