package com.rbi.cms.ingestion.email.repository;

import com.rbi.cms.ingestion.email.entity.EmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailAttachmentRepository extends JpaRepository<EmailAttachment, Long> {

    List<EmailAttachment> findByDraftId(String draftId);
}
