package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintEligibilityAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintEligibilityAnswerRepository
        extends JpaRepository<ComplaintEligibilityAnswer, Long> {

    List<ComplaintEligibilityAnswer> findByComplaintNumber(String complaintNumber);

    Optional<ComplaintEligibilityAnswer> findByComplaintNumberAndQuestionKey(String complaintNumber,
                                                                            String questionKey);
}
