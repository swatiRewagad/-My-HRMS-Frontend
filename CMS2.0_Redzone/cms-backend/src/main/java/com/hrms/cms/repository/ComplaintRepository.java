package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JpaSpecificationExecutor is required by the AA parent-complaint search: its filters (RBIO office,
 * closure clause, category, ground, appellant name/mobile/email) are independently optional and
 * freely combinable, which a fixed set of derived finders cannot express without a combinatorial
 * explosion of methods. See AaParentComplaintSearchService for the predicate builder.
 */
public interface ComplaintRepository
        extends JpaRepository<Complaint, Long>, JpaSpecificationExecutor<Complaint> {
    Optional<Complaint> findByComplaintNumber(String complaintNumber);
    List<Complaint> findByStatusOrderByCreatedAtDesc(String status);
    List<Complaint> findByComplainantEmailOrderByCreatedAtDesc(String email);
    List<Complaint> findByComplainantPhoneOrderByCreatedAtDesc(String phone);
    List<Complaint> findByCategoryIdOrderByCreatedAtDesc(Long categoryId);
    List<Complaint> findByBankIdOrderByCreatedAtDesc(Long bankId);
    List<Complaint> findAllByOrderByCreatedAtDesc();

    Page<Complaint> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<Complaint> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) OR c.complaintNumber LIKE CONCAT('%', :q, '%') OR LOWER(c.complainantName) LIKE LOWER(CONCAT('%', :q, '%'))")
    List<Complaint> search(@Param("q") String query);

    @Query("SELECT c FROM Complaint c WHERE LOWER(c.subject) LIKE LOWER(CONCAT('%', :q, '%')) OR c.complaintNumber LIKE CONCAT('%', :q, '%') OR LOWER(c.complainantName) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY c.createdAt DESC")
    Page<Complaint> searchPaged(@Param("q") String query, Pageable pageable);

    long countByStatus(String status);
    long countByPriority(String priority);
    long countByDepartment(String department);
    long countByDepartmentAndStatus(String department, String status);

    List<Complaint> findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedRole, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedOfficer, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndStatusOrderByCreatedAtDesc(String department, String status);

    List<Complaint> findByDepartmentAndStatusNotInOrderByCreatedAtDesc(String department, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerAndStatusOrderByCreatedAtDesc(
            String department, String assignedOfficer, String status);

    List<Complaint> findByStatusAndDepartmentIsNullOrderByCreatedAtDesc(String status);

    List<Complaint> findByDepartmentAndAssignedRoleAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
            String department, String assignedRole, String assignedOfficer, List<String> excludeStatuses);

    List<Complaint> findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc(String department, String assignedOfficer);

    List<Complaint> findByDepartmentOrderByCreatedAtDesc(String department);

    List<Complaint> findByDepartmentAndAssignedRoleOrderByCreatedAtDesc(String department, String assignedRole);

    List<Complaint> findByDepartmentAndStatusInOrderByCreatedAtDesc(String department, List<String> statuses);

    // RE Portal queries
    Page<Complaint> findByEntityCodeOrderByCreatedAtDesc(String entityCode, Pageable pageable);

    Page<Complaint> findByEntityCodeAndStatusOrderByCreatedAtDesc(String entityCode, String status, Pageable pageable);

    /**
     * Entity-scoped lookup across a SET of statuses (UST-S2A, story 5).
     *
     * The single-status variant above cannot express the PNO's parent-complaint search, which must
     * return complaints that are closed OR reopened — and reopen is recorded as
     * workflow_stage='REOPENED' with the status moved back to in_progress, so it is not a status value
     * at all. Both halves are therefore matched here.
     *
     * entityCode is compared case-insensitively because COMPLAINTS.entity_code is dirty: the same
     * regulated entity appears as a full name ('Punjab National Bank') and as a short code ('PNB'),
     * so an exact binary match would silently under-return a PNO's own complaints. Scoping remains a
     * server-side equality test on the caller's resolved claim — never a client-supplied value.
     */
    @Query("""
           SELECT c FROM Complaint c
           WHERE UPPER(TRIM(c.entityCode)) = UPPER(TRIM(:entityCode))
             AND (LOWER(c.status) IN :statuses OR UPPER(c.workflowStage) = :reopenedStage)
           ORDER BY c.createdAt DESC
           """)
    Page<Complaint> findByEntityCodeAndStatusInOrReopened(@Param("entityCode") String entityCode,
                                                         @Param("statuses") List<String> statuses,
                                                         @Param("reopenedStage") String reopenedStage,
                                                         Pageable pageable);

    // Scheduled notification queries
    List<Complaint> findByStatusAndLastStatusChangeDateBefore(String status, LocalDateTime cutoff);

    List<Complaint> findByStatusNotInAndLastStatusChangeDateBefore(List<String> excludeStatuses, LocalDateTime cutoff);

    @Query("SELECT c FROM Complaint c WHERE c.status NOT IN :closedStatuses AND c.createdAt < :cutoff AND c.department = :department")
    List<Complaint> findOpenComplaintsOlderThan(@Param("closedStatuses") List<String> closedStatuses,
                                                @Param("cutoff") LocalDateTime cutoff,
                                                @Param("department") String department);

    @Query("SELECT c FROM Complaint c WHERE c.reResponseDeadline IS NOT NULL AND c.reResponseDeadline < :today AND c.status NOT IN :closedStatuses")
    List<Complaint> findPastReResponseDeadline(@Param("today") LocalDate today,
                                              @Param("closedStatuses") List<String> closedStatuses);

    /**
     * Every complaint carrying a response deadline, whether overdue or not.
     *
     * <p>The sweep needs the ones that are NO LONGER overdue as well as the ones that are, because it clears
     * the flag as well as setting it — that is what makes the highlight disappear once the entity responds
     * (UST637). A query restricted to overdue rows could only ever set the flag, never remove it.
     */
    @Query("SELECT c FROM Complaint c WHERE c.reResponseDeadline IS NOT NULL")
    List<Complaint> findWithReResponseDeadline();

    /**
     * Complaints currently flagged overdue, for counts that do not re-derive the rule.
     *
     * <p>Reads the persisted flag rather than recomputing the comparison, so a dashboard count and the grid's
     * highlight can never disagree about who is late.
     */
    @Query("SELECT c FROM Complaint c WHERE c.reResponseOverdue = TRUE AND c.reResponseDeadline < :today")
    List<Complaint> findOverdueReResponses(@Param("today") LocalDate today);

    // Citizen portal: paginated queries by phone
    Page<Complaint> findByComplainantPhone(String phone, Pageable pageable);

    Page<Complaint> findByComplainantPhoneAndStatus(String phone, String status, Pageable pageable);

    /**
     * The citizen "Open" filter: a complaint the citizen is still waiting on.
     *
     * Ruled by the business owner — a complaint counts as Open until it is resolved. The settled
     * statuses are passed in rather than hardcoded, and the predicate EXCLUDES them rather than listing
     * the open ones: there are 23 distinct statuses in use, so an inclusive list would silently drop any
     * status added later, whereas exclusion defaults a newcomer to Open — the safe direction for a
     * citizen's own view of what is still outstanding.
     */
    Page<Complaint> findByComplainantPhoneAndStatusNotIn(
            String phone, Collection<String> statuses, Pageable pageable);

    /**
     * Duplicate pre-check for public filing: the same complainant (matched on phone OR email)
     * raising the same category against the same regulated entity while an earlier complaint is
     * still live. Terminal statuses are excluded so a citizen may re-file after closure.
     */
    @Query("""
           SELECT c FROM Complaint c
           WHERE (
                   (:phone IS NOT NULL AND c.complainantPhone = :phone)
                OR (:email IS NOT NULL AND LOWER(c.complainantEmail) = LOWER(:email))
           )
             AND (:bankId IS NULL OR c.bankId = :bankId)
             AND (:categoryId IS NULL OR c.categoryId = :categoryId)
             AND c.status NOT IN :terminalStatuses
             AND c.createdAt >= :since
           ORDER BY c.createdAt DESC
           """)
    List<Complaint> findPotentialDuplicates(@Param("phone") String phone,
                                            @Param("email") String email,
                                            @Param("bankId") Long bankId,
                                            @Param("categoryId") Long categoryId,
                                            @Param("terminalStatuses") List<String> terminalStatuses,
                                            @Param("since") LocalDateTime since);

    /**
     * Records sitting in an early RE activity status that have not yet been nudged for it (UST850).
     *
     * The age comparison is deliberately NOT in this query: the threshold is snapshotted per record
     * in reActivityNudgeDays, so "too old" is a per-row question that SQL cannot answer with a
     * single bind parameter. reActivityNudgedAt IS NULL keeps an already-nudged record out until it
     * next transitions, which is what stops the sweep re-notifying on every run.
     */
    @Query("""
           SELECT c FROM Complaint c
           WHERE c.reActivityStatus IN :statuses
             AND c.reActivityNudgedAt IS NULL
             AND c.reActivityChangedAt IS NOT NULL
             AND c.reActivityNudgeDays IS NOT NULL
           ORDER BY c.reActivityChangedAt ASC
           """)
    List<Complaint> findNudgeCandidates(@Param("statuses") List<ReActivityStatus> statuses,
                                        Pageable pageable);
}
