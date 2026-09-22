package com.hrms.cms.repository;

import com.hrms.cms.entity.ReportColumnDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportColumnDefinitionRepository extends JpaRepository<ReportColumnDefinition, Long> {

    List<ReportColumnDefinition> findByIsActiveOrderByDisplayOrderAsc(String isActive);
}
