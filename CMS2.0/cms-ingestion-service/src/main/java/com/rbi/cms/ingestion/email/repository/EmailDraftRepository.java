package com.rbi.cms.ingestion.email.repository;

import com.rbi.cms.ingestion.email.entity.EmailDraft;
import com.rbi.cms.ingestion.email.entity.EmailDraftStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailDraftRepository extends JpaRepository<EmailDraft, Long> {

    Optional<EmailDraft> findByDraftId(String draftId);

    Optional<EmailDraft> findByMessageId(String messageId);

    List<EmailDraft> findByStatusOrderByReceivedAtDesc(EmailDraftStatus status);

    List<EmailDraft> findByAssignedToOrderByReceivedAtDesc(String assignedTo);

    List<EmailDraft> findByStatusInOrderByReceivedAtDesc(List<EmailDraftStatus> statuses);

    @Query("SELECT d FROM EmailDraft d WHERE d.senderEmail = :senderEmail AND d.subject = :subject AND d.status NOT IN ('IGNORED', 'REJECTED')")
    List<EmailDraft> findBySenderEmailAndSubject(String senderEmail, String subject);

    @Query("SELECT d FROM EmailDraft d WHERE d.senderEmail = :senderEmail AND d.status NOT IN ('IGNORED', 'REJECTED') ORDER BY d.receivedAt DESC")
    List<EmailDraft> findBySenderEmail(String senderEmail);

    long countByAssignedToAndStatusIn(String assignedTo, List<EmailDraftStatus> statuses);

    long countByStatus(EmailDraftStatus status);
}
