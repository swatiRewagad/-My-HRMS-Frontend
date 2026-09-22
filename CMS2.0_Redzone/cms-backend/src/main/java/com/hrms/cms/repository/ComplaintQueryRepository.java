package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintQueryRepository extends JpaRepository<ComplaintQuery, Long> {

    List<ComplaintQuery> findByComplaintIdOrderByRaisedAtDesc(Long complaintId);

    List<ComplaintQuery> findByComplaintIdAndEntityCodeOrderByRaisedAtDesc(Long complaintId, String entityCode);

    /**
     * Threads awaiting a reply from the given side, scoped to one entity.
     * Used for the RE-side "awaiting my response" filter (UST856).
     */
    List<ComplaintQuery> findByEntityCodeAndPendingWithAndStatusOrderByRaisedAtAsc(
            String entityCode, String pendingWith, String status);

    /** Threads awaiting a reply from the given side across all entities — RBI-side filter (UST856). */
    List<ComplaintQuery> findByPendingWithAndStatusOrderByRaisedAtAsc(String pendingWith, String status);

    long countByEntityCodeAndPendingWithAndStatus(String entityCode, String pendingWith, String status);

    long countByPendingWithAndStatus(String pendingWith, String status);

    /**
     * Complaint ids that have at least one thread awaiting the given side, so a list view can show
     * the unread badge without issuing a query per row.
     */
    @Query("SELECT DISTINCT q.complaintId FROM ComplaintQuery q "
         + "WHERE q.pendingWith = :pendingWith AND q.status = :status "
         + "AND (:entityCode IS NULL OR q.entityCode = :entityCode)")
    List<Long> findComplaintIdsPendingWith(@Param("pendingWith") String pendingWith,
                                           @Param("status") String status,
                                           @Param("entityCode") String entityCode);
}
