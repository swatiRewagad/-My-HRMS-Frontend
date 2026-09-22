package com.hrms.cms.repository;

import com.hrms.cms.entity.AppealAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppealAttachmentRepository extends JpaRepository<AppealAttachment, Long> {

    List<AppealAttachment> findByAppealNumberOrderByUploadedAtDesc(String appealNumber);

    List<AppealAttachment> findByAppealNumberAndDocumentType(String appealNumber, String documentType);

    List<AppealAttachment> findBySourceDraftId(String sourceDraftId);
}
