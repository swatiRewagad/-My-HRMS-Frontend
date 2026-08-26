package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintCommentRepository extends JpaRepository<ComplaintComment, Long> {
    List<ComplaintComment> findByComplaintNumberOrderByCreatedAtDesc(String complaintNumber);
    List<ComplaintComment> findByNoRecordNumberOrderByCreatedAtDesc(String noRecordNumber);
}
