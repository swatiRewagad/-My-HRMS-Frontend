package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintRbioFormData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintRbioFormDataRepository extends JpaRepository<ComplaintRbioFormData, Long> {

    Optional<ComplaintRbioFormData> findByComplaintId(Long complaintId);
}
