package com.hrms.cms.repository;

import com.hrms.cms.entity.NodalOfficerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NodalOfficerRecordRepository extends JpaRepository<NodalOfficerRecord, Long> {

    List<NodalOfficerRecord> findByComplaintNumber(String complaintNumber);

    List<NodalOfficerRecord> findByStatus(String status);

    List<NodalOfficerRecord> findByStatusAndLastModifiedAtBefore(String status, LocalDateTime cutoff);

    List<NodalOfficerRecord> findByEntityName(String entityName);

    List<NodalOfficerRecord> findByAssignedTo(String assignedTo);
}
