-- V108: Composite indexes for the senior-dashboard role-queue and unassigned-queue reads
-- Oracle version (mirrors database/V111__complaints_dashboard_queue_indexes.sql; the two
-- directories' V-numbers are not in sync)
--
-- Context: COMPLAINTS carries 17 indexes already, but every one of them is single-column or leads
-- with STATUS. The queue reads that the staff dashboards issue on every page load all filter on
-- DEPARTMENT FIRST, then on an assignee column, then exclude a set of statuses:
--
--   findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc
--   findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc
--   findByDepartmentAndAssignedRoleOrderByCreatedAtDesc
--   findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc
--   ComplaintRepository.countUnassignedByDepartment  (DEPARTMENT + STATUS IN + assignee IS NULL)
--
-- None of those could be served by a composite; the optimiser's best option was IDX_COMPLAINT_STATUS
-- (22 distinct values over ~2.5k rows, so a third of the table for 'closed') or a full scan.
--
-- ═══ WHY DEPARTMENT LEADS, NOT STATUS ═══
-- Column order here is chosen from measured cardinality, and it is deliberately NOT the order a
-- glance at the query text suggests. DEPARTMENT has only 3 distinct values and STATUS has 22, so
-- leading with STATUS would normally be right. It is not, because the STATUS predicate in these
-- queries is `NOT IN (...)` / `IN (...)` — a range, not an equality. A B-tree can only be sought on
-- leading equality columns up to the first range predicate; anything after it is filtered, not
-- sought. Putting STATUS first would therefore strand ASSIGNED_ROLE and ASSIGNED_OFFICER as
-- non-index filters, which is the situation this migration exists to fix.
--   With (DEPARTMENT, ASSIGNED_ROLE, STATUS) the engine seeks on two equalities and then ranges over
-- STATUS within that narrow slice. Skew on DEPARTMENT is acceptable for the same reason: the second
-- equality column, not the first, is what makes the slice small.
--
-- ═══ WHY TWO INDEXES AND NOT ONE ═══
-- ASSIGNED_ROLE and ASSIGNED_OFFICER are separate columns with separate meanings (a role queue is
-- the pool a complaint sits in before an individual picks it up; an officer queue is one person's
-- work), and the callers above query them independently — some on role alone, some on officer alone.
-- A single (DEPARTMENT, ASSIGNED_ROLE, ASSIGNED_OFFICER, STATUS) index would serve the role queries
-- but leave the officer queries unable to reach STATUS, since ASSIGNED_ROLE would be an unconstrained
-- gap in the middle. Two three-column indexes is the correct shape, not redundancy.
--
-- ═══ ORACLE-SPECIFIC NOTE ON NULLS ═══
-- Oracle omits a row from a B-tree index when ALL of that index's key columns are NULL. For these
-- two indexes DEPARTMENT and STATUS are effectively always populated (STATUS is NOT NULL; 22 rows
-- have a NULL DEPARTMENT), so an all-NULL key cannot occur and the unassigned-queue predicate
-- `ASSIGNED_OFFICER IS NULL` stays indexed here — unlike a single-column index on ASSIGNED_OFFICER,
-- which could not serve it at all. That is an additional reason the composite is the right shape on
-- this dialect, and it is why the unassigned count is expected to use the officer index rather than
-- fall back to a full scan.
--
-- ═══ WHAT THIS DOES NOT FIX ═══
-- CREATED_AT is NOT appended to either index even though every caller above ends in
-- OrderByCreatedAtDesc. With a range predicate on STATUS the engine cannot also use a following
-- column for ordering, so a trailing CREATED_AT would be dead weight on every INSERT and would not
-- remove the sort. The remaining sort is over the already-narrowed slice (tens of rows, not
-- thousands) and is not worth a fourth column.
--   These indexes also do NOT help the five GROUP BY aggregates added to ComplaintRepository in the
-- same change: those touch every row by definition and are served by covering scans of the existing
-- single-column indexes. That is expected, not a shortfall.
--
-- Re-running is safe: every CREATE INDEX is guarded on USER_INDEXES, following the add_idx pattern
-- established in V107. These files are applied by hand (there is no Flyway in this project), so
-- idempotency has to be in the file itself.

DECLARE
    PROCEDURE add_idx(p_name VARCHAR2, p_ddl VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = p_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END;
BEGIN
    add_idx('IDX_COMPLAINTS_DEPT_ROLE_STATUS',
            'CREATE INDEX IDX_COMPLAINTS_DEPT_ROLE_STATUS ON COMPLAINTS (DEPARTMENT, ASSIGNED_ROLE, STATUS)');
    add_idx('IDX_COMPLAINTS_DEPT_OFFICER_STATUS',
            'CREATE INDEX IDX_COMPLAINTS_DEPT_OFFICER_STATUS ON COMPLAINTS (DEPARTMENT, ASSIGNED_OFFICER, STATUS)');
END;
/

-- The optimiser will not choose a brand-new index until it has statistics for it.
BEGIN
    DBMS_STATS.GATHER_TABLE_STATS(
        ownname          => USER,
        tabname          => 'COMPLAINTS',
        method_opt       => 'FOR ALL INDEXED COLUMNS SIZE AUTO',
        cascade          => TRUE);
END;
/
