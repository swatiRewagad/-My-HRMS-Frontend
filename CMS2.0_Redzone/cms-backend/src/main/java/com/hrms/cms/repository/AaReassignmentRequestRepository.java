package com.hrms.cms.repository;

import com.hrms.cms.entity.AaReassignmentRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AaReassignmentRequestRepository extends JpaRepository<AaReassignmentRequest, Long> {

    List<AaReassignmentRequest> findByRequestedByOrderByRequestedAtDescIdDesc(String requestedBy);

    List<AaReassignmentRequest> findByStatusOrderByRequestedAtAscIdAsc(String status);

    List<AaReassignmentRequest> findByAppealNumberOrderByRequestedAtDescIdDesc(String appealNumber);

    /** Guards against a second open request for a record that is already awaiting a decision. */
    Optional<AaReassignmentRequest> findFirstByAppealNumberAndStatus(String appealNumber, String status);

    long countByStatus(String status);
}
