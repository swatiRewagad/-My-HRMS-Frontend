package com.hrms.cms.repository;

import com.hrms.cms.entity.NodalOfficerRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NodalOfficerRecordRepository extends JpaRepository<NodalOfficerRecord, Long> {

    List<NodalOfficerRecord> findByComplaintNumber(String complaintNumber);

    Optional<NodalOfficerRecord> findByRecordNumber(String recordNumber);

    List<NodalOfficerRecord> findAllByOrderByLastModifiedAtDesc();

    List<NodalOfficerRecord> findByStatus(String status);

    List<NodalOfficerRecord> findByStatusAndLastModifiedAtBefore(String status, LocalDateTime cutoff);

    List<NodalOfficerRecord> findByEntityName(String entityName);

    List<NodalOfficerRecord> findByAssignedTo(String assignedTo);
}
