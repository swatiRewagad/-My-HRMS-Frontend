package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintAdditionalDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintAdditionalDetailRepository extends JpaRepository<ComplaintAdditionalDetail, Long> {

    Optional<ComplaintAdditionalDetail> findByComplaintId(Long complaintId);
}
