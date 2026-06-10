package com.hrms.cms.repository;

import com.hrms.cms.entity.EmailDraftAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmailDraftAttachmentRepository extends JpaRepository<EmailDraftAttachment, Long> {
    List<EmailDraftAttachment> findByDraftIdOrderByCreatedAtAsc(String draftId);
}
