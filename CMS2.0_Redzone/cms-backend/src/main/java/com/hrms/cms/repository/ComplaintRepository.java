package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.repository.projection.AssistanceRailProjections;
import com.hrms.cms.repository.projection.DashboardProjections;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * Counts one complaint-number series. Used by the dev seeders to decide whether their OWN rows are
     * already present, which a count of the whole table cannot answer: this database carries several
     * unrelated series ({@code CMP-*}, {@code N2026*}, {@code CEPC/*}), so a total-row threshold is
     * tripped by other people's data and silently disables the seeder for good.
     */
    long countByComplaintNumberStartingWith(String prefix);
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

    /**
     * Open complaints past their SLA deadline that have not yet had a breach escalation raised.
     *
     * <p>Backs {@link com.hrms.cms.service.SlaBreachEscalationService}. Three guards, each load-bearing:
     *
     * <p><b>{@code slaBreachEscalatedAt IS NULL}</b> — the once-only marker. Keeps an
     * already-escalated complaint out of every later sweep, so the query itself is idempotent rather
     * than relying on a downstream consumer to deduplicate. Note this bounds the SCAN; it is the
     * conditional UPDATE in {@link #claimSlaBreachEscalation} that makes the notification once-only
     * under CONCURRENT replicas.
     *
     * <p><b>{@code slaDeadline IS NOT NULL}</b> — explicit, not incidental. A NULL deadline is not a
     * breach, it is an unknown, and {@code NULL < :now} is UNKNOWN (so excluded) on both engines
     * anyway. Stating it documents that the complaints with no deadline at all are a SEPARATE,
     * separately-tracked gap and not something this sweep silently swallows.
     *
     * <p><b>{@code LOWER(c.status) NOT IN :closedStatuses}</b> — COMPLAINTS stores lowercase status
     * strings, and the caller passes the canonical lowercase vocabulary from
     * {@link com.hrms.cms.service.RbioStatusVocabulary}. {@code LOWER()} is what makes this
     * Oracle-safe: MySQL's case-insensitive collation would forgive a casing error, Oracle would not.
     * Measured, the uppercase form that is correct for COMPLAINT_MASTER's Java enum selects 2119 rows
     * here instead of 518 — it would re-escalate ~1601 ALREADY-CLOSED complaints. See the test
     * {@code pinsTheCaseSensitivityTrap}.
     *
     * <p>Oldest breach first so that if the batch cap truncates the set, the longest-suffering
     * complaint is served first rather than last.
     */
    @Query("""
           SELECT c FROM Complaint c
           WHERE c.slaBreachEscalatedAt IS NULL
             AND c.slaDeadline IS NOT NULL
             AND c.slaDeadline < :now
             AND LOWER(c.status) NOT IN :closedStatuses
           ORDER BY c.slaDeadline ASC
           """)
    List<Complaint> findSlaBreachCandidates(@Param("now") LocalDateTime now,
                                            @Param("closedStatuses") Collection<String> closedStatuses,
                                            Pageable pageable);

    /**
     * Stamps the breach marker, but ONLY if it is still unset. Returns the number of rows affected:
     * 1 means this caller won the claim, 0 means someone else already escalated this complaint.
     *
     * <p><b>This is the multi-replica safety mechanism.</b> There is no ShedLock table, so the sweep
     * runs on every replica simultaneously. Filtering stamped rows out of
     * {@link #findSlaBreachCandidates} alone would NOT be enough: two replicas can both SELECT the
     * same unstamped row before either writes, and both would notify. Making the write a conditional
     * UPDATE turns it into a compare-and-swap that the database adjudicates — exactly one replica can
     * observe 1 affected row, and only that one sends the notification.
     *
     * <p>Deliberately a targeted UPDATE rather than loading the entity and calling {@code save()}.
     * {@link Complaint} carries {@code @Version record_version}, so a read-modify-write would throw
     * {@code OptimisticLockException} whenever an officer happened to be editing the same complaint —
     * letting unrelated human activity suppress an SLA escalation. A system marker has no business
     * contending with an officer's edit, and this UPDATE touches one column and nothing else.
     *
     * <p>Same shape as {@code AssistanceJobLockRepository.acquire:58-64}, which already uses an
     * affected-row count as a lock claim in this module.
     */
    @Modifying
    @Query("""
           UPDATE Complaint c
              SET c.slaBreachEscalatedAt = :escalatedAt
            WHERE c.id = :id
              AND c.slaBreachEscalatedAt IS NULL
           """)
    int claimSlaBreachEscalation(@Param("id") Long id,
                                 @Param("escalatedAt") LocalDateTime escalatedAt);

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
     *
     * <p>{@code COALESCE(LENGTH(TRIM(x)), 0) = 0} and NOT {@code x IS NULL OR TRIM(x) = ''}. This is the
     * INVERSE polarity of the blank guards elsewhere in this file and so it fails differently on Oracle:
     * because Oracle stores {@code ''} AS NULL the {@code IS NULL} half still catches the empty rows, so
     * the count does not collapse to 0 — but a WHITESPACE-ONLY {@code assigned_officer} diverges.
     * {@code TRIM(' ') = ''} is true on MySQL and UNKNOWN on Oracle (because {@code TRIM(' ')} is NULL
     * there), so such a complaint counts as unassigned on MySQL and SILENTLY VANISHES from the
     * unassigned backlog on Oracle. The {@code COALESCE} form folds NULL, {@code ''} and all-whitespace
     * to 0 on both engines, which is exactly what {@code isBlank()} meant.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.DashboardProjections$KeyCount("
            + "c.department, COUNT(c)) FROM Complaint c "
            + "WHERE c.department IS NOT NULL AND c.status IN :statuses "
            + "AND COALESCE(LENGTH(TRIM(c.assignedOfficer)), 0) = 0 "
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
     * The six columns the rail needs about the complaint being viewed.
     *
     * <p>Plan: {@code ref} on {@code idx_complaint_number} (unique), one row. This exists instead of
     * {@code findByComplaintNumber} because that hydrates all ~105 columns and six {@code TEXT} bodies
     * to read six scalars, on every staff screen load.
     *
     * <p>{@code status} is the next-action prior's key, and adding it costs nothing measurable: the row
     * is already being fetched by its unique index, so this is one more scalar off a row the query
     * reads anyway rather than a second lookup.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$RailContext("
            + "c.complaintNumber, c.complainantEmail, c.entityCode, c.closureClause, c.categoryId, "
            + "c.status) "
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
     * <p>The blank-email guard is not cosmetic: 25 rows store {@code ''} rather than NULL, and without
     * it each of those complainants would be told the other 24 were "their" earlier complaints. The
     * caller must ALSO refuse to invoke this with a blank email, so the control exists on both sides.
     *
     * <p>The guard is spelled {@code LENGTH(TRIM(x)) > 0} rather than {@code TRIM(x) <> ''} because
     * Oracle treats {@code ''} as NULL, so {@code <> ''} is {@code <> NULL} — UNKNOWN for every row, and
     * this count would be a flat 0 in production while passing on MySQL. {@code LENGTH(TRIM(x)) > 0}
     * behaves identically on both engines.
     */
    @Query("SELECT COUNT(c) FROM Complaint c "
            + "WHERE c.complainantEmail = :email AND LENGTH(TRIM(c.complainantEmail)) > 0 "
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
            + "WHERE c.categoryId = :categoryId AND c.closedAt IS NOT NULL AND c.createdAt IS NOT NULL "
            // ORDER BY is load-bearing, not cosmetic. The caller passes a Pageable cap, and without an
            // ordering the rows returned are whatever the index yields first — measured as ASCENDING
            // closed_at off idx_arm_category_closed. So once a category passes the cap, the median
            // closure time would freeze permanently on the OLDEST N closures ever recorded and never
            // track current practice again. DESC makes the cap keep the most recent instead, and the
            // same index serves it in reverse at no cost.
            + "ORDER BY c.closedAt DESC")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<AssistanceRailProjections.ClosureWindow> findClosureWindowsForCategory(
            @Param("categoryId") Long categoryId, Pageable pageable);

    /**
     * One officer's open queue, counted: how many, how many near a deadline, how many already past one.
     *
     * <h3>A COUNT, not a hydrate — and that is the whole point of this signature</h3>
     * Three scalars from ONE aggregate statement. The obvious alternative — fetch the officer's open
     * complaints and tally them in Java — is the recorded {@code SeniorDashboardService} defect in a new
     * place: it would load up to 600 rows of a 105-column table with six {@code TEXT} bodies to produce
     * two integers, on a screen load. There is consequently NO {@link Pageable} here and no row cap,
     * because there are no rows: the result set is exactly one. The timeout hint is still declared,
     * because an aggregate over a bad plan can stall just as long as a list over one.
     *
     * <h3>MEASURED PLAN (MySQL 8.4, cms_db, 4403 rows)</h3>
     * {@code type=range}, {@code key=idx_complaints_officer_status_deadline} (V117 / oracle V115),
     * {@code key_len=925}, {@code rows=131}, {@code filtered=100.00},
     * {@code Extra='Using where; Using index'} — a covering range, so the 105-column row is never
     * touched. Without that index the optimiser picks {@code idx_complaint_status} at
     * {@code rows=1208, filtered=10.00}: correct, but it reads a quarter of the table and then filters
     * for the officer, because no existing index leads with {@code assigned_officer}.
     *
     * <h3>Three date comparisons and not one, because the schema has three deadlines</h3>
     * {@code sla_deadline} is the field with data (3870 of 4403 rows; 811 of 1186 open).
     * {@code re_response_deadline} — the field Brief 21 §5.3.4 actually prescribes — is populated on
     * THIRTEEN rows, one of them open, so a query written to the brief's letter would count almost
     * nothing. It is still ORed in rather than dropped: it is the field that will carry the RE clock
     * once the entity-response path writes it, and including it now costs one more covering column.
     * {@code current_stage_deadline} is deliberately NOT read — measured against cms_db it equals
     * {@code sla_deadline} on 173 of its 174 open rows and is EARLIER on none, so it would add a column
     * to the index to change no answer.
     *
     * <h3>No function wraps any indexed column</h3>
     * The horizons arrive pre-computed as a {@code LocalDateTime} and a {@code LocalDate} and are
     * compared directly. An {@code UPPER}/{@code TRIM} on {@code assignedOfficer}, or date arithmetic on
     * the deadline columns, would defeat this index exactly as {@code UPPER(TRIM(c.entityCode))} defeats
     * {@code idx_complaint_entity_code} elsewhere in this file. The {@code LocalDate} horizon is passed
     * separately rather than derived in JPQL for the same reason — and because JPQL has no portable
     * cast between the two.
     *
     * <h3>The officer is a PARAMETER here and is resolved by the caller, which is load-bearing</h3>
     * This finder has no "all officers" mode and no nullable-officer branch. The recorded defect at
     * {@code EmailSyndicationApiController:451} is a client-supplied owner that, when omitted, returned
     * every row in the system; a queue finder that treated a null officer as "everyone" would be that
     * defect with worse consequences, since this one answers to a staff token. The service refuses a
     * blank owner before calling, and {@code AssistanceRailController} takes the id from
     * {@code RequestIdentityResolver} only.
     *
     * @param officer        the RESOLVED officer id, exactly as stored in {@code assigned_officer}
     * @param closedStatuses the statuses that mean "shut", from {@code RbioStatusVocabulary} rather than
     *                       a literal — two hardcoded copies of this list had already drifted apart once
     * @param horizon        the instant at or before which a {@code sla_deadline} counts as at risk
     * @param horizonDate    the same horizon as a DATE, for {@code re_response_deadline} which is a
     *                       {@code date} column. Passed in rather than truncated here so no function
     *                       touches the column.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$DeadlineTriage("
            + "COUNT(c), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline <= :horizon) "
            + "          OR (c.reResponseDeadline IS NOT NULL AND c.reResponseDeadline <= :horizonDate) "
            + "         THEN 1L ELSE 0L END), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline < :now) "
            + "          OR (c.reResponseDeadline IS NOT NULL AND c.reResponseDeadline < :nowDate) "
            + "         THEN 1L ELSE 0L END)) "
            + "FROM Complaint c "
            + "WHERE c.assignedOfficer = :officer AND c.status NOT IN :closedStatuses")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    AssistanceRailProjections.DeadlineTriage countDeadlineTriageForOfficer(
            @Param("officer") String officer,
            @Param("closedStatuses") Collection<String> closedStatuses,
            @Param("now") LocalDateTime now,
            @Param("nowDate") LocalDate nowDate,
            @Param("horizon") LocalDateTime horizon,
            @Param("horizonDate") LocalDate horizonDate);

    // ═══════════════════════════════════════════════════════════════════════════════════════════════
    // ASSISTANCE RAIL — §5.3.5 ENTITY PATTERN
    //
    // Two finders, and the split between them IS the §6.2 contract for this prior:
    //   * findEntityPatternContext is the REQUEST path. One unique-key seek for four scalars off the
    //     row the rail is already rendering beside. It aggregates NOTHING.
    //   * findEntityPatternRows is the REFRESH path, called only from the scheduled job, keyset-paged.
    //     It is the only place the register is scanned, and it happens hours before any officer looks.
    //
    // The live form of this signal — COUNT(*) WHERE entity_code = ? AND status NOT IN (...) — is
    // recorded as DROPPED in AssistanceRailService, and the REASON has been corrected: an earlier note
    // here said entity_code carried no usable index (type=ALL), and that is FALSE.
    // MEASURED: idx_complaint_entity_code exists and the live query plans as type=ref, rows=155.
    // It is dropped because §5.3.5 says "from a scheduled rollup only... Never computed live" and §6.2
    // says rollups are computed on a schedule and never on request — the rail renders on every staff
    // screen load, so an aggregate here is an aggregate on the critical path of every page. Neither
    // finder below reinstates it: the counting lives in ASSISTANCE_ENTITY_PATTERN, keyed so the read is
    // a seek on tens of rows rather than a 155-row aggregate per page view.
    // ═══════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * The four columns that key the entity-pattern rollup read, plus the filing date.
     *
     * <p>Plan: {@code ref} on {@code idx_complaint_number} (unique), one row — the same access path
     * {@link #findRailContext} uses, on the same row, which is why this costs a seek rather than a scan.
     *
     * <p>A SECOND projection rather than four more fields on {@code RailContext}: that record is
     * constructed positionally at sixteen sites in {@code AssistanceRailServiceTest}, and widening it
     * would also widen what the other four priors can see — the opposite of the direction its own javadoc
     * argues for. See {@code EntityPatternContext}.
     *
     * <p>{@code entityCode} comes back RAW. The caller normalises it through
     * {@code AssistanceEntityAliasNormaliser} before seeking the rollup; this query does NOT wrap it in
     * {@code UPPER(TRIM(...))}, both because the value here is a projected scalar rather than a predicate
     * and because the rollup column it will be compared against was normalised on the write side
     * precisely so no read has to.
     *
     * <p>{@code department} is read FROM THE COMPLAINT and is the tenancy fence the service ANDs into the
     * rollup query. Deliberately not a parameter anywhere in this feature: the recorded defect at
     * {@code EmailSyndicationApiController:451} is a caller-supplied scope that, omitted, disclosed
     * everything.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$EntityPatternContext("
            + "c.entityCode, c.department, c.rbioOfficeCode, c.groundOfComplaintId, c.createdAt) "
            + "FROM Complaint c WHERE c.complaintNumber = :complaintNumber")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    Optional<AssistanceRailProjections.EntityPatternContext> findEntityPatternContext(
            @Param("complaintNumber") String complaintNumber);

    /**
     * One page of complaints for the entity-pattern REFRESH, keyset-paged on the primary key.
     *
     * <h3>Called only from the scheduled job. Never from a request.</h3>
     * §6.2: "rollups are computed on a schedule, never on request". This is the scan that §5.3.5's
     * "never computed live" exists to move off the request path, and the only caller is
     * {@code AssistanceEntityPatternRefreshService}.
     *
     * <h3>KEYSET, not offset</h3>
     * {@code WHERE c.id > :afterId ORDER BY c.id} with a {@link Pageable} cap. An offset page would
     * re-read rows as the register grows underneath a long refresh and would degrade quadratically on the
     * deep pages. Plan: {@code range} on {@code PRIMARY}, which needs no index that does not already
     * exist — and no index on {@code entity_code} would help, because the job reads every row by design
     * so there is nothing for it to seek.
     *
     * <h3>The WHERE clause filters only what cannot be keyed, and nothing else</h3>
     * A complaint with no {@code entity_code} cannot belong to an entity's cohort and one with no
     * {@code created_at} cannot belong to a quarter, so both are excluded here rather than in Java —
     * that is 290 of 4,403 rows not carried into heap. {@code status} is NOT filtered: the refresh needs
     * the closed rows too, because they are the DENOMINATOR. A query that returned only open complaints
     * would make the rollup unable to say "17 of 240", which §5.1 requires it to say.
     *
     * <h3>{@code status} comes back as TEXT, and that is deliberate</h3>
     * The open/closed decision happens in Java against {@code RbioStatusVocabulary}. Encoding the closed
     * list into this query would mint a THIRD copy of a vocabulary that has already drifted apart once in
     * this codebase — and a rollup whose definition of "open" disagreed with the rest of the system would
     * report closed cases as live ones against a named entity.
     *
     * @param afterId  the last id of the previous page, 0 to start
     * @param pageable always {@code PageRequest.of(0, PAGE_SIZE)}; the §6.2 row cap, which here also
     *                 bounds the job's heap so it does not scale with the register
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceRailProjections$EntityPatternRow("
            + "c.id, c.entityCode, c.department, c.rbioOfficeCode, c.groundOfComplaintId, c.status, "
            + "c.createdAt) "
            + "FROM Complaint c "
            + "WHERE c.id > :afterId AND LENGTH(TRIM(c.entityCode)) > 0 "
            + "AND c.createdAt IS NOT NULL "
            + "ORDER BY c.id")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = DASHBOARD_TIMEOUT_MS))
    List<AssistanceRailProjections.EntityPatternRow> findEntityPatternRows(
            @Param("afterId") Long afterId, Pageable pageable);
}
