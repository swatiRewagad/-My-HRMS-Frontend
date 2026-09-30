package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, Long> {

    /**
     * The whole thread for a complaint, unfiltered. Deliberately NOT filtered in the query: the
     * visibility rule is one method in {@code ComplaintCommentService} so it has exactly one place to
     * get wrong. A repository-level filter would be a second, divergent copy of it.
     */
    List<ComplaintComment> findByComplaintIdOrderByCreatedAtAsc(Long complaintId);

    List<ComplaintComment> findByParentIdOrderByCreatedAtAsc(Long parentId);
}
