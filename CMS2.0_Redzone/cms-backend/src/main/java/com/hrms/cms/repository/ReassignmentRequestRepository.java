package com.hrms.cms.repository;

import com.hrms.cms.entity.ReassignmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReassignmentRequestRepository extends JpaRepository<ReassignmentRequest, Long> {

    /** UST842 "My Requests" — what this user asked for, newest first. */
    Page<ReassignmentRequest> findByRequestedByOrderByRequestedAtDesc(String requestedBy,
                                                                     Pageable pageable);

    Page<ReassignmentRequest> findByRequestedByAndStatusOrderByRequestedAtDesc(String requestedBy,
                                                                              String status,
                                                                              Pageable pageable);

    /** UST843 PNO approval queue, entity-scoped server-side. */
    Page<ReassignmentRequest> findByEntityCodeAndStatusOrderByRequestedAtAsc(String entityCode,
                                                                            String status,
                                                                            Pageable pageable);

    Page<ReassignmentRequest> findByEntityCodeOrderByRequestedAtDesc(String entityCode,
                                                                    Pageable pageable);

    List<ReassignmentRequest> findByNodalOfficerRecordIdAndStatus(Long nodalOfficerRecordId,
                                                                 String status);

    long countByEntityCodeAndStatus(String entityCode, String status);
}
