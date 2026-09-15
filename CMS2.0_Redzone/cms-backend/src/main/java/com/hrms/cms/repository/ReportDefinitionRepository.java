package com.hrms.cms.repository;

import com.hrms.cms.entity.ReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportDefinitionRepository extends JpaRepository<ReportDefinition, Long> {

    List<ReportDefinition> findByOwnerUsernameOrderByDisplayOrderAsc(String ownerUsername);

    List<ReportDefinition> findByOwnerUsernameAndDashboardWidgetTrueOrderByDisplayOrderAsc(String ownerUsername);
}
