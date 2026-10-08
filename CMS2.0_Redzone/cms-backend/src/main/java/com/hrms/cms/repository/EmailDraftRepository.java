package com.hrms.cms.repository;

import com.hrms.cms.entity.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    long countByTargetOffice(String targetOffice);
    long countByTargetOfficeAndStatus(String targetOffice, String status);
    List<EmailDraft> findByIsVernacularTrueOrderByCreatedAtDesc();
    List<EmailDraft> findByAssignedToAndIsVernacularTrueOrderByCreatedAtDesc(String assignedTo);

    /**
     * Open draft count per assignee, for the DEO roster's {@code currentLoad}.
     *
     * <p>Grouped rather than one count per DEO: the roster endpoint renders every DEO, and a per-row count
     * would issue one query per officer on a screen the officer opens on every complaint.
     *
     * <p>{@code statuses} is passed in so the caller names {@link com.hrms.cms.entity.DraftStatus#OPEN}
     * explicitly. Counting every status instead would include CONVERTED work, so a DEO's load could only ever
     * rise — and automatic assignment balances on exactly this number.
     */
    @Query("SELECT d.assignedTo, COUNT(d) FROM EmailDraft d "
         + "WHERE d.assignedTo IS NOT NULL AND d.status IN :statuses GROUP BY d.assignedTo")
    List<Object[]> countOpenGroupedByAssignee(@Param("statuses") Collection<String> statuses);

    Optional<EmailDraft> findByMessageId(String messageId);
    boolean existsByMessageId(String messageId);
    long countByStatusIn(List<String> statuses);
    long countByIsDuplicateTrue();

    /** Duplicate detection: same sender AND exactly equal subject. */
    List<EmailDraft> findBySenderEmailIgnoreCaseAndSubjectOrderByCreatedAtDesc(String senderEmail, String subject);

    /**
     * Candidate related drafts for the same complainant, excluding the draft in hand. Ordering is
     * newest-first so the DO sees the most recent related matter first.
     */
    List<EmailDraft> findBySenderEmailIgnoreCaseAndDraftIdNotOrderByCreatedAtDesc(String senderEmail, String draftId);

    @Query("SELECT d.assignedTo AS deo, COUNT(d) AS total, " +
           "SUM(CASE WHEN d.status = 'ASSIGNED' THEN 1 ELSE 0 END) AS pending, " +
           "SUM(CASE WHEN d.status = 'SENT_FOR_APPROVAL' THEN 1 ELSE 0 END) AS sentForApproval, " +
           "SUM(CASE WHEN d.status = 'NOT_A_COMPLAINT' THEN 1 ELSE 0 END) AS notAComplaint " +
           "FROM EmailDraft d WHERE d.assignedTo IS NOT NULL GROUP BY d.assignedTo")
    List<Map<String, Object>> getDeoWorkloadStats();
}
