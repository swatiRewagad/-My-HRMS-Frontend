package com.hrms.cms.repository;

import com.hrms.cms.entity.CepcAssessmentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Reads for the CEPC "Comments" box and the nodal record "To NO" / "To PNO" threads.
 */
@Repository
public interface CepcAssessmentCommentRepository extends JpaRepository<CepcAssessmentComment, Long> {

    /** Complaint-level feed: oldest first, matching how the Assessment tab appends new comments to the end. */
    List<CepcAssessmentComment> findByComplaintNumberAndNodalRecordNumberIsNullOrderByCreatedAtAsc(
            String complaintNumber);

    /** A nodal record's own thread: newest first, matching how the nodal panel prepends new comments. */
    List<CepcAssessmentComment> findByNodalRecordNumberOrderByCreatedAtDesc(String nodalRecordNumber);
}
