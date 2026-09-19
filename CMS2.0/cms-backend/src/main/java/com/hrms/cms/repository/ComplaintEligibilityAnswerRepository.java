package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintEligibilityAnswerRepository extends JpaRepository<ComplaintEligibilityAnswer, Long> {

    Optional<ComplaintEligibilityAnswer> findByComplaintId(Long complaintId);
}
