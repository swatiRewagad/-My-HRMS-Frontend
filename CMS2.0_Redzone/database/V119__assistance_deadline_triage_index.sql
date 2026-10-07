-- V119: Assistance rail — the two indexes that make the deadline-triage counts covering seeks
-- MySQL version. Oracle twin: database/oracle/V117__assistance_deadline_triage_index.sql
-- (the two directories' V-numbers are not in sync; this file's twin is oracle V117, not any V119 there)
--
-- Brief 21 §5.3.4: "Deadline triage. re_response_deadline and re_response_overdue are indexed (V86).
-- '3 of your 14 cases breach within 48h' is a count over an existing index."
--
-- ═══ RESERVED NUMBERS WERE V117 / ORACLE V115, AND BOTH WERE TAKEN ═══
-- This session was allocated MySQL V117 and Oracle V115. By the time the files were written, a
-- concurrent session had shipped V117__assistance_entity_pattern_rollup.sql and
-- oracle/V115__assistance_entity_pattern_rollup.sql, and another had taken V118 / oracle V116. So this
-- pair is V119 / oracle V117 — the next free numbers on BOTH sides, re-checked immediately before the
-- files were created and again before the commit. Recorded because "V-numbers get stolen mid-session" is
-- a known hazard here and the next reader will wonder why these are not the numbers the brief names.
--
-- ═══ THE BRIEF'S INDEX CLAIM IS FALSE, AND information_schema SAYS SO ═══
-- §5.3.4 asserts these columns are "indexed (V86)". MEASURED against the live cms_db
-- (information_schema.STATISTICS, 2026-10-07) the COMPLAINTS table carries 40 index rows, and NONE of
-- V86's three indexes is among them in any letter case: idx_complaint_re_deadline,
-- idx_complaint_re_overdue and idx_no_re_deadline do not exist.
--
--   * re_response_deadline — NOT INDEXED. V86 does CALL s3_dl_add_index('COMPLAINTS',
--     'idx_complaint_re_deadline', 're_response_deadline'), so the brief read the FILE correctly. The
--     file never ran.
--   * sla_deadline — NOT INDEXED by any migration. No file has ever asked for one.
--   * assigned_officer / assigned_role — indexed only as the SECOND column of V111's two composites.
--
-- WHY V86 NEVER RAN, since its COLUMNS do exist and that makes this look partially applied. V86 also
-- seeds two SYSTEM_CONFIG rows (re.deadline.sweep_interval_minutes, re.deadline.overdue_highlight_colour)
-- stamped updated_by='V86_migration'. NEITHER IS PRESENT, and they are plain INSERT ... SELECT ...
-- WHERE NOT EXISTS with no guard that could skip them on a first run. The columns are therefore the work
-- of ddl-auto: update, which creates columns from the entity mapping and NEVER creates indexes — and
-- every V86 column is mapped on an entity (Complaint.reResponseOverdue,
-- NodalOfficerRecord.reResponseDeadline and .deadlineCommunication).
--
-- An earlier draft of this header blamed a case-sensitivity bug in V86's information_schema guard under
-- lower_case_table_names=1. That is WRONG and is corrected here rather than quietly deleted, because it
-- is the conclusion a reader will reach independently: information_schema.STATISTICS.TABLE_NAME collates
-- utf8mb3_tolower_ci, so the guard matches 'COMPLAINTS' and 'complaints' identically — measured both
-- ways, same row count each — and V111's indexes, written with the same uppercase literal, DID land.
-- The guard works; the file never ran.
--
-- The general rule, which is worth more than this feature: A COLUMN EXISTING IS NOT EVIDENCE THAT ITS
-- MIGRATION RAN. Any index named in a hand-applied file must be verified in information_schema before a
-- query is designed to rely on it.
--
-- ═══ TWO INDEXES, BECAUSE THE SERVICE ISSUES TWO QUERIES ═══
-- AssistanceQueueService reads an officer's queue two ways and never with an OR: rows assigned to them
-- BY NAME, and rows sitting with their ROLE in their departments. Both predicates carry department as a
-- hard TENANCY filter — not an optimisation, a correctness requirement, because SEVEN officers in this
-- register hold complaints across more than one department (rbio_officer_002 has 22 rows spanning CEPC
-- and RBIO, officer.crpc.5 spans three) and counting a case the officer cannot open would corrupt the
-- denominator they are asked to act on.
--
--   idx_complaints_dept_officer_status_dl (department, assigned_officer, status,
--                                          sla_deadline, re_response_deadline)
--   idx_complaints_dept_role_status_dl    (department, assigned_role,    status,
--                                          sla_deadline, re_response_deadline)
--
-- ═══ COLUMN ORDER ═══
-- 1. department LEADS, because it is an IN over a tiny bounded set (the caller's own role-derived
--    departments: at most CEPC, CRPC, RBIO) and it is present on BOTH queries. It is also the leading
--    column of V111's two composites, so these are strict EXTENSIONS of indexes the optimiser already
--    chooses rather than rival shapes it has to be talked into.
-- 2. assigned_officer (equality) / assigned_role (IN) second — the selective predicate.
-- 3. status third, a range (NOT IN over the terminal vocabulary from RBIO_STATUS_MASTER). InnoDB seeks
--    on index columns up to the first range column and filters after it, so status must follow the
--    selective predicate and precede anything that is only read.
-- 4+5. The two deadline columns are trailing COVERING columns, never sought. They are in the key so
--    Extra reads 'Using index' and the engine never touches the 105-column row — which, with six TEXT
--    bodies on this table, is the difference between 130 index entries and 130 full row reads. They
--    CANNOT be ordering columns: the SUM(CASE WHEN ...) compares them, it does not range over them.
--
-- ═══ MEASURED — AND THE ROLE-POOL PLAN IS WHY THIS FILE WAS REVISED ═══
-- An earlier revision of this migration created a single index leading with assigned_officer and argued
-- that the department predicate had to be omitted. That was wrong on both counts: the service always
-- passes department, and the index was measurably never chosen. Live plans, after ANALYZE TABLE:
--
--   OFFICER query, BEFORE (with the officer-leading index present and the live V111 composites):
--     → type=range  key=idx_complaints_dept_officer_status  key_len=1008
--       rows=130  filtered=100.00  Extra='Using index condition'
--     The optimiser picked V111's composite and left idx_complaints_officer_status_deadline in
--     possible_keys unchosen. The seek was already fine; the read was NOT covering.
--   OFFICER query, AFTER (proved on a full row-for-row temp copy of COMPLAINTS):
--     → type=range  key=idx_complaints_dept_officer_status_dl  key_len=1008
--       rows=130  filtered=100.00  Extra='Using where; Using index'   ← COVERING
--
--   ROLE-POOL query, BEFORE — THE §6.3 DEFECT THIS FILE EXISTS TO FIX:
--     → type=ref    key=idx_complaints_dept_officer_status  key_len=83  ref=const
--       rows=2231  filtered=5.40  Extra='Using index condition; Using where'
--     The optimiser degrades to a ref on the WRONG composite (the officer one, on its department prefix
--     alone) and discards 94.6% of what it reads. Half the table scanned to answer one aggregate.
--   ROLE-POOL query, AFTER:
--     → type=range  key=idx_complaints_dept_role_status_dl  key_len=408
--       rows=856  filtered=100.00  Extra='Using where; Using index'   ← COVERING
--
-- Wall clock on the temp copy, SHOW PROFILES: 0.0547s cold first touch, then 0.0037s for the role pool
-- (846 rows counted) and 0.0023s for the 597-row officer queue. The officer query returns 10 of 126 for
-- cepc_do1, which is the figure the service's javadoc records.
--
-- current_stage_deadline is deliberately NOT in either index. Measured on the open rows that carry one:
-- it EQUALS sla_deadline on 173 of 174 and is EARLIER on NONE, so a sixth column would widen every index
-- entry to change no answer. The one row where it differs is LATER, which cannot pull a case into the
-- at-risk count.
--
-- re_response_deadline IS carried despite being populated on THIRTEEN of 4403 rows (one of them open)
-- and re_response_overdue being true on ZERO. It is the field the brief prescribes, it costs 4 bytes per
-- entry (nullable DATE), and carrying it means the signal sharpens by itself once the entity-response
-- path starts writing it — no schema or code change.
--
-- ═══ COST ═══
-- Two secondary indexes on a 4403-row table, each a five-column extension of an index that already
-- exists. Key lengths are the measured 1008 and 408 bytes, so both stay well under 5 MB at present
-- volume. Write amplification is one extra B-tree maintenance per INSERT and per UPDATE touching
-- department, the assignment columns, status or either deadline — and every workflow transition touches
-- status, so this is NOT free. It is justified by the read landing on every staff dashboard load, and by
-- the role-pool plan above: 2231 rows at 5.40% filtered is the kind of plan §6.2 rejects outright
-- regardless of how fast it runs on 4403 rows.
--
-- V111's two three-column composites are deliberately LEFT IN PLACE rather than dropped as redundant
-- prefixes. Dropping an index is not reversible inside a hand-applied file, other callers choose them
-- (RbioComplaintListService, the CEPC dashboard counts), and a DROP in a migration that exists to ADD a
-- read path is the kind of change that gets applied at 2am and regretted. Reported, not done.
--
-- Re-running is safe. MySQL has no CREATE INDEX IF NOT EXISTS (a plain CREATE INDEX fails with errno
-- 1061 on the second run), so each statement is guarded on information_schema.STATISTICS and executed
-- through a prepared statement, following V111 and V115. These files are applied BY HAND — there is no
-- Flyway in this project — so idempotency has to live inside the file.
--
-- Each DDL below is deliberately ONE string literal and not two joined with '||'. MySQL reads '||' as
-- logical OR unless PIPES_AS_CONCAT is in sql_mode, so the concatenated form sets @ddl to 0 and PREPARE
-- then fails with 1064 on a file that looks correct. CONCAT() would also work; a single literal is used
-- because it cannot be broken by a later edit re-introducing the pipes.
--
-- NOT APPLIED to any environment as shipped. The endpoint reads through a guarded try/catch and reports
-- nothing on failure, so an unapplied migration costs a slower query, not an error.

-- ── idx_complaints_dept_officer_status_dl — serves countOfficerQueue ────────────────────────────────
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_complaints_dept_officer_status_dl'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_complaints_dept_officer_status_dl ON COMPLAINTS (department, assigned_officer, status, sla_deadline, re_response_deadline)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── idx_complaints_dept_role_status_dl — serves countRolePoolQueue ─────────────────────────────────
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_complaints_dept_role_status_dl'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_complaints_dept_role_status_dl ON COMPLAINTS (department, assigned_role, status, sla_deadline, re_response_deadline)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The optimiser will not choose a brand-new index until it has statistics for it. Without this the
-- EXPLAIN above still names V111's composites and the migration looks ineffective.
ANALYZE TABLE COMPLAINTS;

-- ═══ WHAT THIS SIGNAL CANNOT SAY ON TODAY'S DATA, MEASURED ═══
-- Recorded because a signal that is silent for a defensible reason is indistinguishable from one that is
-- broken, and the next reader will test it against this database.
--
-- 1. THE SIGNAL FIRES FOR THREE OFFICERS OUT OF 45. Open queues (status NOT IN the terminal values the
--    live RBIO_STATUS_MASTER reports) with at least one case inside a 48h horizon:
--      cepc_do1          10 of 126 open   (all 10 already overdue)
--      rbio.officer       4 of  26 open   (all 4 already overdue)
--      rbio_officer_001   1 of 142 open   (already overdue)
--    cepc_do_001, the largest queue at 597 open, has ZERO — its seeded sla_deadline values all land
--    2026-10-27 or later. So the signal is correct and almost entirely silent. With the brief's
--    re_response_deadline alone it would fire for NOBODY.
--
-- 2. THE HORIZON BUCKET IS EMPTY AND THE OVERDUE BUCKET IS NOT. Over the open complaints carrying an
--    sla_deadline: 563 are 30+ days out, 251 are 7-30 days, 15 are already overdue, and TWO sit in the
--    2-7 day band. Nothing at all falls strictly inside 48h without already having passed. Every count
--    this signal reports today is therefore an overdue count, which is why `overdue` is a qualifier on
--    the same sentence rather than a separate signal — on this data a standalone "breaching soon" signal
--    would never once have rendered.
--
-- 3. 375 OF 1186 OPEN COMPLAINTS CARRY NO DEADLINE OF ANY KIND. They are counted in the DENOMINATOR
--    (they are open cases in the officer's queue) and can never enter the numerator. That is correct —
--    "3 of your 14" should mean 14 open cases, not 14 cases that happen to have a deadline — but it
--    makes the ratio pessimistic about coverage rather than about risk. `pending` (315 rows) is the
--    worst offender: 3 of 315 have an sla_deadline.
--
-- 4. assigned_officer IS NOT ALWAYS A PERSON. Six values are team or desk labels — 'RBIO OFFICER Team'
--    (24 rows), 'DEO Team' (15), 'Team Alpha'/'Team Beta'/'Team Gamma', 'Escalation Desk'. None of
--    those will ever match a resolved userId from RequestIdentityResolver, so their cases fall through
--    to the ROLE-POOL query rather than the officer one — which is the right outcome, and the second
--    reason the role-pool index above matters. NOT worked around further: mapping a team label onto its
--    members would be the signal inventing an assignment the register does not record.
