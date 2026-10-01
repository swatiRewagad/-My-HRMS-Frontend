-- V111: Composite indexes for the senior-dashboard role-queue and unassigned-queue reads
-- MySQL version
--
-- Context: COMPLAINTS carries 17 indexes already, but every one of them is single-column or leads
-- with status. The queue reads that the staff dashboards issue on every page load all filter on
-- department FIRST, then on an assignee column, then exclude a set of statuses:
--
--   findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc
--   findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc
--   findByDepartmentAndAssignedRoleOrderByCreatedAtDesc
--   findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc
--   ComplaintRepository.countUnassignedByDepartment  (department + status IN + assignee IS NULL)
--
-- None of those could be served by a composite; the optimiser's best option was idx_complaint_status
-- (22 distinct values over ~2.5k rows, so a third of the table for 'closed') or a full scan.
--
-- ═══ WHY DEPARTMENT LEADS, NOT STATUS ═══
-- Column order here is chosen from measured cardinality, and it is deliberately NOT the order a
-- glance at the query text suggests. department has only 3 distinct values and status has 22, so
-- leading with status would normally be right. It is not, because the status predicate in these
-- queries is `NOT IN (...)` / `IN (...)` — a range, not an equality. InnoDB can only use index
-- columns for equality up to the first range predicate; anything after a range column is filtered,
-- not sought. Putting status first would therefore strand assigned_role and assigned_officer as
-- non-index filters, which is the situation this migration exists to fix.
--   With (department, assigned_role, status) the engine seeks on two equalities and then ranges over
-- status within that narrow slice. Skew on department is acceptable for the same reason: the second
-- equality column, not the first, is what makes the slice small.
--
-- ═══ WHY TWO INDEXES AND NOT ONE ═══
-- assigned_role and assigned_officer are separate columns with separate meanings (a role queue is
-- the pool a complaint sits in before an individual picks it up; an officer queue is one person's
-- work), and the callers above query them independently — some on role alone, some on officer alone.
-- A single (department, assigned_role, assigned_officer, status) index would serve the role queries
-- but leave the officer queries unable to reach status, since assigned_role would be an unconstrained
-- gap in the middle. Two three-column indexes is the correct shape, not redundancy.
--
-- ═══ WHAT THIS DOES NOT FIX ═══
-- createdAt is NOT appended to either index even though every caller above ends in
-- OrderByCreatedAtDesc. With a range predicate on status the engine cannot also use a following
-- column for ordering, so a trailing createdAt would be dead weight on every INSERT and would not
-- remove the sort. The remaining filesort is over the already-narrowed slice (tens of rows, not
-- thousands) and is not worth a fourth column.
--   These indexes also do NOT help the five GROUP BY aggregates added to ComplaintRepository in the
-- same change: those touch every row by definition and are served by covering scans of the existing
-- single-column indexes. That is expected, not a shortfall.
--
-- Cost: two secondary indexes on a table with ~2.5k rows and 105 columns. Index width is
-- 100 + 50 + 50 bytes of utf8mb4 VARCHAR plus the 8-byte PK, so both together add well under a
-- megabyte at present volume. Write amplification is two extra B-tree maintenances per INSERT and
-- per UPDATE that touches department, an assignee column or status.
--
-- Re-running is safe: MySQL has no CREATE INDEX IF NOT EXISTS, so each statement is guarded on
-- information_schema.STATISTICS and executed through a prepared statement. A plain CREATE INDEX
-- would fail with errno 1061 on the second run, and these files are applied by hand (there is no
-- Flyway in this project — database/*.sql is documentation plus a manual-apply artifact), so
-- idempotency has to be in the file itself.

-- ── idx_complaints_dept_role_status ──────────────────────────────────────────────────────────────
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_complaints_dept_role_status'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_complaints_dept_role_status ON COMPLAINTS (department, assigned_role, status)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── idx_complaints_dept_officer_status ───────────────────────────────────────────────────────────
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_complaints_dept_officer_status'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_complaints_dept_officer_status ON COMPLAINTS (department, assigned_officer, status)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The optimiser will not choose a brand-new index until it has statistics for it.
ANALYZE TABLE COMPLAINTS;
