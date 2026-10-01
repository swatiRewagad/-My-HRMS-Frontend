package com.hrms.cms.repository;

import com.hrms.cms.entity.ReassignmentClarification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReassignmentClarificationRepository
        extends JpaRepository<ReassignmentClarification, Long> {

    /** Oldest first: a clarification thread reads in the order it was written. */
    List<ReassignmentClarification> findByReassignmentRequestIdOrderByAddedAtAsc(
            Long reassignmentRequestId);

    long countByReassignmentRequestId(Long reassignmentRequestId);
}
