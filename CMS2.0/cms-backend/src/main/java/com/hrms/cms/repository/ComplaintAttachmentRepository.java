package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ComplaintAttachmentRepository extends JpaRepository<ComplaintAttachment, Long> {
    List<ComplaintAttachment> findByComplaintId(Long complaintId);
}
