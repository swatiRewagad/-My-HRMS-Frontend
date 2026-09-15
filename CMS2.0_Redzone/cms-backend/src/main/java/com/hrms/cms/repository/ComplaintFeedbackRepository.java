package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintFeedbackRepository extends JpaRepository<ComplaintFeedback, Long> {

    Optional<ComplaintFeedback> findByComplaintNumber(String complaintNumber);

    boolean existsByComplaintNumber(String complaintNumber);

    List<ComplaintFeedback> findByOfficeCodeOrderBySubmittedAtDesc(String officeCode);

    List<ComplaintFeedback> findAllByOrderBySubmittedAtDesc();
}
