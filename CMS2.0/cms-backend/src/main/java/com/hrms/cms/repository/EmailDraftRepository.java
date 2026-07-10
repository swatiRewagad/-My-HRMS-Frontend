package com.hrms.cms.repository;

import com.hrms.cms.entity.EmailDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT d.assignedTo AS deo, COUNT(d) AS total, " +
           "SUM(CASE WHEN d.status = 'ASSIGNED' THEN 1 ELSE 0 END) AS pending, " +
           "SUM(CASE WHEN d.status = 'SENT_FOR_APPROVAL' THEN 1 ELSE 0 END) AS sentForApproval, " +
           "SUM(CASE WHEN d.status = 'NOT_A_COMPLAINT' THEN 1 ELSE 0 END) AS notAComplaint " +
           "FROM EmailDraft d WHERE d.assignedTo IS NOT NULL GROUP BY d.assignedTo")
    List<Map<String, Object>> getDeoWorkloadStats();
}
