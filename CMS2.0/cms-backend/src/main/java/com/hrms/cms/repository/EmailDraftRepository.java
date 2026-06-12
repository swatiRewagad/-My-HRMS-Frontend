package com.hrms.cms.repository;

import com.hrms.cms.entity.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailDraftRepository extends JpaRepository<EmailDraft, Long> {
    Optional<EmailDraft> findByDraftId(String draftId);
    Optional<EmailDraft> findByThreadId(String threadId);
    List<EmailDraft> findByStatusOrderByCreatedAtDesc(String status);
    List<EmailDraft> findByAssignedToOrderByCreatedAtDesc(String assignedTo);
    List<EmailDraft> findByAssignedToAndStatusOrderByCreatedAtDesc(String assignedTo, String status);
    List<EmailDraft> findByProcessedByOrderByCreatedAtDesc(String processedBy);
    List<EmailDraft> findAllByOrderByCreatedAtDesc();
    List<EmailDraft> findBySenderEmailOrderByCreatedAtDesc(String senderEmail);
    long countByStatus(String status);
}
