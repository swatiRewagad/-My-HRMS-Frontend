package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcComplaintAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CepcComplaintAssessmentRepository extends JpaRepository<CepcComplaintAssessment, Long> {

    Optional<CepcComplaintAssessment> findByComplaintNumber(String complaintNumber);

    /** Batch form, so a list of records costs one query rather than one per row. */
    List<CepcComplaintAssessment> findByComplaintNumberIn(Collection<String> complaintNumbers);
}
