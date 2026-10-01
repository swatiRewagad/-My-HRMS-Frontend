package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioLegalCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RbioLegalCaseRepository extends JpaRepository<RbioLegalCase, Long> {

    Optional<RbioLegalCase> findByComplaintNumber(String complaintNumber);

    boolean existsByComplaintNumber(String complaintNumber);
}
