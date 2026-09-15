package com.hrms.cms.repository;

import com.hrms.cms.entity.ReportSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {

    List<ReportSchedule> findByActiveTrueAndDeliverySlot(String slot);

    List<ReportSchedule> findByOwnerUsername(String ownerUsername);

    boolean existsByReportDefinitionIdAndOwnerUsername(Long reportDefId, String owner);
}
