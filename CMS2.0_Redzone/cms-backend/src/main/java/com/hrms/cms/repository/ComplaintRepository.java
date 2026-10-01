package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import com.hrms.cms.repository.projection.DashboardProjections;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

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

    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /** Milliseconds. A missing index must fail fast rather than hold a Hikari connection. */
    String DASHBOARD_TIMEOUT_MS = "5000";

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // SENIOR-DASHBOARD AGGREGATES
    //
    // These replaced five findAll() calls in SeniorDashboardService, each of which loaded all 105
    // columns of every row — including six TEXT bodies — to compute counts in Java.
    //
    // ─── WHY JPQL AND NOT NATIVE SQL ───
    // The deployed profiles (prod, openshift) run OracleDialect; only dev-local runs MySQL. Native
    // GROUP BY SQL would work locally and break in the environment that matters, so every query below
    // is JPQL with a constructor expression.
    //
    // ─── CASE FOLDING IS A REAL HAZARD HERE, AND IS NOT INTRODUCED ───
    // COMPLAINTS is utf8mb4_0900_ai_ci, so a SQL GROUP BY folds case where the Java
    // Collectors.groupingBy it replaces did not. priority genuinely holds both 'MEDIUM' (2177 rows)
    // and 'medium' (252), plus 'high' (39) / 'HIGH' (20), 'low' (19) and 'CRITICAL' (2) — six stored
    // spellings that MySQL reports as four. Grouping it in SQL MERGES buckets the old code kept
    // apart: that is a behaviour change dressed as an optimisation.
    //   The fix is NOT "SELECT DISTINCT then count each value", which was tried and MEASURED to fail:
    // DISTINCT itself folds case (returns 4 rows) and `priority = 'MEDIUM'` / `= 'medium'` BOTH return
    // 2428, the merged total. Under this collation no query can address one spelling. So
    // findAllPriorityValues() projects the bare column — the one shape that applies no comparison
    // operator and therefore preserves the stored bytes — and the caller tallies case-sensitively in
    // Java. See that method's javadoc for the cost analysis.
    //   department, status and entity_code were MEASURED to have no case collisions (3/3, 22/22,
    // 31/31 distinct under CAST(... AS BINARY) vs plain), so those group in SQL safely. That
    // measurement is a snapshot of dev data, not a constraint the schema enforces; if a mixed-case
    // department is ever inserted these counts will silently merge two buckets.
    //
    // ─── CAPS AND TIMEOUTS ───
    // The grouped queries return at most a few dozen rows. The two that return per-row data are
    // capped by Pageable at the call site and every query carries a 5s timeout, so a missing index
    // surfaces as a fast failure rather than a stalled request pinning a Hikari connection.
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /** One row per distinct status (22 today), for the pipeline counts. */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.status, COUNT(c)) FROM Complaint c GROUP BY c.status")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.KeyCount> countGroupedByStatus();

    /**
     * Department backlog for the given statuses.
     *
     * <p>Null departments are excluded, matching the Java filter this replaced — 22 rows have none.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.department, COUNT(c)) FROM Complaint c "
            + "WHERE c.department IS NOT NULL AND c.status IN :statuses GROUP BY c.department")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.KeyCount> countByDepartmentForStatuses(
            @Param("statuses") Collection<String> statuses);

    /** Backlog per category id; names are resolved outside SQL from the 10-row category table. */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$IdCount("
            + "c.categoryId, COUNT(c)) FROM Complaint c "
            + "WHERE c.categoryId IS NOT NULL AND c.status IN :statuses "
            + "GROUP BY c.categoryId ORDER BY COUNT(c) DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.IdCount> countByCategoryForStatuses(
            @Param("statuses") Collection<String> statuses);

    /**
     * Unassigned backlog per department.
     *
     * <p>Blank is treated as unassigned alongside NULL because both occur and the replaced Java
     * predicate ({@code == null || isBlank()}) treated them alike.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.department, COUNT(c)) FROM Complaint c "
            + "WHERE c.department IS NOT NULL AND c.status IN :statuses "
            + "AND (c.assignedOfficer IS NULL OR TRIM(c.assignedOfficer) = '') "
            + "GROUP BY c.department")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.KeyCount> countUnassignedByDepartment(
            @Param("statuses") Collection<String> statuses);

    /** Status breakdown per department; neither column has case collisions (measured). */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$DeptStatusCount("
            + "c.department, c.status, COUNT(c)) FROM Complaint c "
            + "WHERE c.department IS NOT NULL AND c.status IS NOT NULL "
            + "GROUP BY c.department, c.status")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.DeptStatusCount> countByDepartmentAndStatus();

    /** Volume per regulated entity; entity_code has no case collisions (measured 31/31). */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.entityCode, COUNT(c)) FROM Complaint c "
            + "WHERE c.entityCode IS NOT NULL GROUP BY c.entityCode ORDER BY COUNT(c) DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.KeyCount> countByEntity();

    /** Per-entity counts restricted to a status list. */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.entityCode, COUNT(c)) FROM Complaint c "
            + "WHERE c.entityCode IS NOT NULL AND c.status IN :statuses "
            + "GROUP BY c.entityCode ORDER BY COUNT(c) DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.KeyCount> countByEntityForStatuses(
            @Param("statuses") Collection<String> statuses);

    /**
     * EVERY stored priority value, one row per complaint, NOT grouped and NOT de-duplicated.
     *
     * <p>This looks wasteful and is deliberate. Two earlier attempts at a cheaper shape were both
     * MEASURED to be wrong on MySQL, because {@code utf8mb4_0900_ai_ci} folds case in far more places
     * than {@code GROUP BY}:
     *
     * <ul>
     *   <li>{@code SELECT DISTINCT c.priority} returns FOUR rows, not six — it collapsed
     *       {@code MEDIUM}/{@code medium} and {@code HIGH}/{@code high} and then arbitrarily reported
     *       whichever spelling it encountered first ({@code medium}, {@code high}).
     *   <li>{@code WHERE c.priority = :priority} cannot address one spelling either:
     *       {@code = 'MEDIUM'} and {@code = 'medium'} both returned 2428, i.e. the sum of both
     *       buckets. So a "count one exact spelling" query does not exist under this collation.
     * </ul>
     *
     * <p>A bare column projection is the only portable shape that preserves the stored bytes, because
     * no comparison or grouping operator is applied to the column at all. The caller tallies with a
     * case-SENSITIVE Java map, which reproduces the {@code Collectors.groupingBy} buckets exactly.
     *
     * <p>The cost is acceptable and bounded: this is ONE {@code VARCHAR(20)} column, served by a
     * covering index-only scan of {@code idx_complaint_priority} (measured: {@code type=index},
     * {@code Extra='Using where; Using index'} — the 105-column row is never touched). Roughly 50 KB
     * of strings at present volume against the ~6 MB of entity rows the {@code findAll()} it replaced
     * had to hydrate. A {@code CAST(... AS BINARY)} group-by would be cheaper still but is MySQL-only
     * syntax, and the deployed profiles run Oracle.
     */
    @Query("SELECT c.priority FROM Complaint c WHERE c.priority IS NOT NULL")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<String> findAllPriorityValues();

    /**
     * The two timestamps every bar of the 12-week trend is computed from, for rows inside the window.
     *
     * <p>ONE round trip, deliberately, rather than the 24 {@code COUNT(*)} queries (12 weeks x filed
     * and resolved) that the obvious decomposition implies. Measured on the dev dataset: the 24-count
     * form cost 1.58 s per dashboard render against 0.18 s for this projection — roughly 9x worse —
     * because {@code resolved_at} CARRIES NO INDEX (verified against
     * {@code information_schema.STATISTICS}), so each of the 12 resolved-counts degrades to a full
     * table scan: {@code type=ALL, key=NULL, rows=2524}. Twelve full scans plus twelve round trips
     * beats one scan only in the abstract.
     *
     * <p>The bucketing therefore stays in Java, where it is 24 comparisons per row over two
     * {@code DATETIME} columns. That is not the anti-pattern this change set exists to remove: what
     * was expensive about the {@code findAll()} here was hydrating 105 columns and six {@code TEXT}
     * bodies per row, not the arithmetic.
     *
     * <p>An index on {@code resolved_at} would make the 24-count form viable and is a defensible
     * follow-up, but it is NOT added here: {@code resolved_at} is written on every resolution and no
     * other query in the codebase filters on it, so the write cost would buy one dashboard panel.
     *
     * <p>The window predicate is an {@code OR} across two columns, so it cannot be index-sought
     * either ({@code type=ALL}); it is kept because it bounds the result set as history grows, which
     * is what protects the heap. At present volume it excludes almost nothing (2520 of 2522 rows) —
     * i.e. today this is a full scan of two narrow columns, and it is priced as one.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$TimestampPair("
            + "c.createdAt, c.resolvedAt) FROM Complaint c "
            + "WHERE c.createdAt >= :windowStart OR c.resolvedAt >= :windowStart")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.TimestampPair> findTrendTimestampsSince(
            @Param("windowStart") LocalDateTime windowStart);

    /**
     * The three columns TAT needs, for active complaints only, newest first.
     *
     * <p>Replaces {@code findAll()} followed by a Java filter. The caller passes a {@link Pageable}
     * carrying a hard row cap, so this can never return an unbounded list; ordering by {@code
     * createdAt DESC} makes the cap drop the OLDEST rows, and the caller reports when it bites.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$TatRow("
            + "c.entityCode, c.createdAt, c.resolvedAt) FROM Complaint c "
            + "WHERE c.createdAt IS NOT NULL AND c.status NOT IN :closedStatuses "
            + "ORDER BY c.createdAt DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<DashboardProjections.TatRow> findActiveTatRows(
            @Param("closedStatuses") Collection<String> closedStatuses, Pageable pageable);

    /** Active-complaint count, so the caller can tell whether its row cap truncated the TAT set. */
    @Query("SELECT COUNT(c) FROM Complaint c "
            + "WHERE c.createdAt IS NOT NULL AND c.status NOT IN :closedStatuses")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    long countActiveForTat(@Param("closedStatuses") Collection<String> closedStatuses);

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // ASSISTANCE RAIL — TIER 1 PRIORS (Brief 21)
    //
    // The rail renders on EVERY staff complaint screen load, so these are held to a harder standard
    // than the dashboard aggregates above: each one must be served by an index seek, and the brief's
    // own rule is that a prior which cannot be is dropped rather than shipped slow. Every query below
    // was EXPLAIN-verified against the dev dataset (2769 rows) and the plan is recorded per method.
    //
    // ─── WHAT IS NOT HERE, AND WHY ───
    // No text similarity, no embedding lookup, no per-request scan of description or subject. Those
    // are the Tier 2 the brief excludes, and a LIKE '%...%' over a TEXT column would be a full table
    // scan wearing a cheap-looking disguise.
    //
    // ─── COLLATION ───
    // entity_code and closure_clause are compared with plain equality, NOT folded with UPPER(). On
    // MySQL the utf8mb4_0900_ai_ci collation already folds them, and wrapping the column in UPPER()
    // would defeat the index on BOTH engines — turning a 461-row seek into a 2509-row scan — for a
    // normalisation MySQL performs for free. The measured consequence is that on Oracle these counts
    // are case-SENSITIVE where on MySQL they are not; entity_code was measured at 31/31 distinct
    // values under CAST(... AS BINARY), i.e. no case collisions exist to merge today.
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * The five columns the rail needs about the complaint being viewed.
     *
     * <p>Plan: {@code ref} on {@code idx_complaint_number} (unique), one row. This exists instead of
     * {@code findByComplaintNumber} because that hydrates all ~105 columns and six {@code TEXT} bodies
     * to read five scalars, on every staff screen load.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$RailContext("
            + "c.complaintNumber, c.complainantEmail, c.entityCode, c.closureClause, c.categoryId) "
            + "FROM Complaint c WHERE c.complaintNumber = :complaintNumber")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    Optional<AssistanceRailProjections.RailContext> findRailContext(
            @Param("complaintNumber") String complaintNumber);

    /**
     * How many OTHER complaints this complainant has filed, matched on email.
     *
     * <p>Plan: {@code ref} on {@code idx_complaint_email}, {@code Extra='Using index'} — a covering
     * index-only seek that never touches the 105-column row.
     *
     * <p>Email is the ONLY join key, and phone is deliberately not matched even though it would widen
     * recall: {@code complainant_phone} CARRIES NO INDEX (verified against
     * information_schema.STATISTICS), so adding {@code OR c.complainantPhone = :phone} converts this
     * seek into {@code type=ALL} over the whole table — an OR across two columns cannot be
     * index-sought even if both were indexed. Measured: 1 row examined with email alone against 2509
     * with the OR. Per the brief's rule the constraint wins, and the recall gap is reported rather
     * than quietly paid for.
     *
     * <p>The blank-email guard is not cosmetic: 21 rows store {@code ''} rather than NULL, and without
     * it each of those complainants would be told the other 20 were "their" earlier complaints. The
     * caller must ALSO refuse to invoke this with a blank email, so the control exists on both sides.
     */
    @Query("SELECT COUNT(c) FROM Complaint c "
            + "WHERE c.complainantEmail = :email AND TRIM(c.complainantEmail) <> '' "
            + "AND c.complaintNumber <> :excludeComplaintNumber")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    long countOtherComplaintsByComplainantEmail(
            @Param("email") String email,
            @Param("excludeComplaintNumber") String excludeComplaintNumber);

    /**
     * How many complaints against the SAME regulated entity closed under the SAME clause.
     *
     * <p>Plan: {@code ref} on {@code idx_arm_clause_entity} (added by V112 / oracle V109),
     * {@code key_len=606}, both predicates sought, {@code Extra='Using index'}. Without that composite
     * the plan falls back to {@code idx_complaint_closure_clause} alone and filters 461 rows for the
     * entity — correct, but 461 row reads instead of a covering seek.
     *
     * <p>Precedent, not prediction. It reports what the register already contains; it does not suggest
     * a clause, which would need the inference the brief excludes.
     */
    @Query("SELECT COUNT(c) FROM Complaint c "
            + "WHERE c.closureClause = :closureClause AND c.entityCode = :entityCode "
            + "AND c.complaintNumber <> :excludeComplaintNumber")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    long countClosedUnderSameClauseForEntity(
            @Param("closureClause") String closureClause,
            @Param("entityCode") String entityCode,
            @Param("excludeComplaintNumber") String excludeComplaintNumber);

    /**
     * Filed/closed timestamp pairs for closed complaints in one category, for the median prior.
     *
     * <p>Plan: {@code range} on {@code idx_arm_category_closed} (category_id, closed_at, created_at),
     * {@code Extra='Using where; Using index'} — covering, so the row is never touched. The
     * {@link Pageable} the caller passes carries a hard cap, so this cannot return an unbounded list
     * as history grows.
     *
     * <p>The pairs come back rather than a pre-computed day count because the subtraction is NOT
     * portable: MySQL spells it {@code TIMESTAMPDIFF}, Oracle returns an INTERVAL from plain
     * subtraction, and JPQL has no portable date-difference function. So the arithmetic happens in
     * Java, over a capped row set.
     *
     * <p>MEDIAN and not AVG, forced by measured data: the seeded rows include {@code closed_at} values
     * that PRECEDE {@code created_at} (category 5 averages -40 days over 3 rows). A mean is dragged by
     * those; a median over the non-negative subset is not. The caller filters and reports the sample
     * size, so a figure derived from three rows is not presented as describing the category.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$ClosureWindow("
            + "c.createdAt, c.closedAt) FROM Complaint c "
            + "WHERE c.categoryId = :categoryId AND c.closedAt IS NOT NULL AND c.createdAt IS NOT NULL")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<AssistanceRailProjections.ClosureWindow> findClosureWindowsForCategory(
            @Param("categoryId") Long categoryId, Pageable pageable);
}
