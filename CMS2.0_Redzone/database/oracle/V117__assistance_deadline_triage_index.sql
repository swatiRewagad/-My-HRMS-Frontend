-- V117: Assistance rail — the two indexes that make the deadline-triage counts covering seeks
-- Oracle version. MySQL twin: database/V119__assistance_deadline_triage_index.sql
-- (the two directories' V-numbers are not in sync; this file's twin is MySQL V119, not any V117 there)
--
-- Brief 21 §5.3.4: "Deadline triage. re_response_deadline and re_response_overdue are indexed (V86).
-- '3 of your 14 cases breach within 48h' is a count over an existing index."
--
-- ═══ RESERVED NUMBERS WERE MYSQL V117 / ORACLE V115, AND BOTH WERE TAKEN ═══
-- This session was allocated MySQL V117 and Oracle V115. By the time the files were written, concurrent
-- sessions had shipped oracle/V115__assistance_entity_pattern_rollup.sql and
-- oracle/V116__assistance_clause_affinity.sql (and MySQL V117 / V118). So this pair is MySQL V119 /
-- Oracle V117 — the next free numbers on BOTH sides, re-checked immediately before the files were
-- created and again before the commit.
--
-- ═══ THE BRIEF'S INDEX CLAIM IS FALSE, AND information_schema SAYS SO ═══
-- §5.3.4 asserts these columns are "indexed (V86)". Measured against the live MySQL cms_db — the only
-- engine in this estate with populated data reachable — COMPLAINTS carries 40 index rows and NONE of
-- V86's three is among them in any letter case: idx_complaint_re_deadline, idx_complaint_re_overdue and
-- idx_no_re_deadline do not exist.
--
--   * RE_RESPONSE_DEADLINE — NOT INDEXED. V86 does CALL s3_dl_add_index('COMPLAINTS',
--     'idx_complaint_re_deadline', 're_response_deadline'), so the brief read the FILE correctly. The
--     file never ran.
--   * SLA_DEADLINE — NOT INDEXED by any migration. No file has ever asked for one.
--   * ASSIGNED_OFFICER / ASSIGNED_ROLE — indexed only as the SECOND column of oracle V108's two
--     composites (DEPARTMENT, ASSIGNED_OFFICER, STATUS) and (DEPARTMENT, ASSIGNED_ROLE, STATUS).
--
-- WHY V86 NEVER RAN, since its COLUMNS do exist and that makes this look partially applied. V86 also
-- seeds two SYSTEM_CONFIG rows (re.deadline.sweep_interval_minutes, re.deadline.overdue_highlight_colour)
-- stamped UPDATED_BY='V86_migration'. NEITHER IS PRESENT, and they are plain INSERT ... SELECT ...
-- WHERE NOT EXISTS with no guard that could skip them on a first run. The columns are therefore the work
-- of ddl-auto: update, which creates columns from the entity mapping and NEVER creates indexes — and
-- every V86 column is mapped on an entity (Complaint.reResponseOverdue,
-- NodalOfficerRecord.reResponseDeadline and .deadlineCommunication).
--
-- The general rule, worth more than this feature: A COLUMN EXISTING IS NOT EVIDENCE THAT ITS MIGRATION
-- RAN. Any index named in a hand-applied file must be verified in the data dictionary before a query is
-- designed to rely on it. On Oracle that means USER_INDEXES / USER_IND_COLUMNS, not the .sql file.
--
-- ═══ TWO INDEXES, BECAUSE THE SERVICE ISSUES TWO QUERIES ═══
-- AssistanceQueueService reads an officer's queue two ways and never with an OR: rows assigned to them
-- BY NAME, and rows sitting with their ROLE in their departments. Both predicates carry DEPARTMENT as a
-- hard TENANCY filter — not an optimisation, a correctness requirement, because SEVEN officers in the
-- dev register hold complaints across more than one department (rbio_officer_002 has 22 rows spanning
-- CEPC and RBIO, officer.crpc.5 spans three) and counting a case the officer cannot open would corrupt
-- the denominator they are asked to act on.
--
--   IDX_COMPLAINTS_DEPT_OFF_STAT_DL  (DEPARTMENT, ASSIGNED_OFFICER, STATUS,
--                                     SLA_DEADLINE, RE_RESPONSE_DEADLINE)
--   IDX_COMPLAINTS_DEPT_ROLE_STAT_DL (DEPARTMENT, ASSIGNED_ROLE,    STATUS,
--                                     SLA_DEADLINE, RE_RESPONSE_DEADLINE)
--
-- The names are abbreviated and do NOT match the MySQL twin's
-- idx_complaints_dept_officer_status_dl / idx_complaints_dept_role_status_dl, deliberately: those are 37
-- and 34 characters and Oracle's identifier limit is 30 on 12.1 and earlier, which several deployed
-- instances in this estate still are. The two engines therefore carry DIFFERENT index names for the same
-- indexes, which is exactly why nothing in Java names an index — the query is portable JPQL and the
-- optimiser picks.
--
-- ═══ COLUMN ORDER ═══
-- 1. DEPARTMENT LEADS, because it is an IN over a tiny bounded set (the caller's own role-derived
--    departments: at most CEPC, CRPC, RBIO) and it is present on BOTH queries. It is also the leading
--    column of V108's two composites, so these are strict EXTENSIONS of indexes the optimiser already
--    chooses rather than rival shapes it has to be talked into.
-- 2. ASSIGNED_OFFICER (equality) / ASSIGNED_ROLE (IN) second — the selective predicate.
-- 3. STATUS third, a range (NOT IN over the terminal vocabulary resolved from RBIO_STATUS_MASTER). A
--    B-tree can be sought only on leading equality columns up to the first range predicate, so STATUS
--    must follow the selective predicate and precede anything that is only read.
-- 4+5. The two deadline columns are trailing COVERING columns, never sought. They are in the key so the
--    engine can answer the aggregate from the index alone and never touch the 105-column row — which,
--    with six CLOB/LONG text bodies on this table, is the difference between 130 index entries and 130
--    full row reads. They CANNOT be ordering columns: the SUM(CASE WHEN ...) compares them, it does not
--    range over them.
--
-- ═══ ORACLE-SPECIFIC NOTE ON NULLS, AND WHY IT DOES NOT BREAK THE COUNT ═══
-- Oracle omits a row from a B-tree index only when ALL of that index's key columns are NULL. STATUS is
-- effectively always populated, so no row these queries could match is index-omitted. DEPARTMENT is NULL
-- on 23 rows: those rows cannot satisfy DEPARTMENT IN (:departments) under any circumstances, so their
-- exclusion is the tenancy predicate working rather than an index artefact. The 375 open rows carrying NO
-- deadline of any kind ARE still in both indexes (their department, assignment and status are populated)
-- and so are still counted in the DENOMINATOR, which is the correct answer — see caveat 3 below.
--
-- ═══ NO MEASURED PLAN ON ORACLE, AND THAT IS STATED RATHER THAN IMPLIED ═══
-- NO ORACLE INSTANCE WAS REACHABLE, so this file has not been executed even once and its plans have not
-- been observed. The expected Oracle plan for each is an INDEX RANGE SCAN with no TABLE ACCESS BY INDEX
-- ROWID — all five columns each aggregate reads are in the key — but that is a PREDICTION, not a
-- measurement. The MySQL twin carries the measured before/after, including the §6.3 defect that caused
-- this file to be revised: the role-pool query degraded to type=ref on the WRONG composite at rows=2231
-- filtered=5.40, and the role index restores type=range rows=856 filtered=100.00 'Using index'.
--
-- An earlier revision of this migration created a single index leading with ASSIGNED_OFFICER and argued
-- that the DEPARTMENT predicate had to be omitted. That was wrong on both counts — the service always
-- passes department, and on MySQL the officer-leading index was measurably never chosen — and it is
-- corrected rather than quietly replaced because the reasoning was plausible enough to be repeated.
--
-- RE_RESPONSE_DEADLINE IS carried despite being populated on THIRTEEN of 4403 rows (one of them open)
-- and RE_RESPONSE_OVERDUE being true on ZERO. It is the field the brief prescribes, it costs one
-- nullable DATE per entry, and carrying it means the signal sharpens by itself once the entity-response
-- path starts writing it — no schema or code change.
--
-- CURRENT_STAGE_DEADLINE is deliberately NOT in either index. Measured on the open rows that carry one:
-- it EQUALS SLA_DEADLINE on 173 of 174 and is EARLIER on NONE, so a sixth column would widen every index
-- entry to change no answer. The one row where it differs is LATER, which cannot pull a case into the
-- at-risk count.
--
-- ═══ COST ═══
-- Two secondary indexes on a 4403-row table, each a five-column extension of an index that already
-- exists. Write amplification is one extra B-tree maintenance per INSERT and per UPDATE touching
-- DEPARTMENT, the assignment columns, STATUS or either deadline — and every workflow transition touches
-- STATUS, so this is NOT free. It is justified by the read landing on every staff dashboard load.
--
-- V108's two three-column composites are deliberately LEFT IN PLACE rather than dropped as redundant
-- prefixes. Dropping an index is not reversible inside a hand-applied file, other callers choose them,
-- and a DROP in a migration that exists to ADD a read path is the kind of change that gets applied at
-- 2am and regretted. Reported, not done.
--
-- Re-running is safe: each CREATE INDEX is guarded on USER_INDEXES through the add_idx pattern
-- established in V107/V108. These files are applied BY HAND — there is no Flyway in this project — so
-- idempotency has to live inside the file. Unlike MySQL, Oracle gathers statistics for a new index
-- automatically as part of CREATE INDEX, so there is no ANALYZE step here; the MySQL twin needs an
-- explicit ANALYZE TABLE or the optimiser keeps choosing the old composites.
--
-- NOT APPLIED to any environment as shipped. The endpoint reads through a guarded try/catch and reports
-- nothing on failure, so an unapplied migration costs a slower query, not an error.

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
    -- Serves countOfficerQueue.
    add_idx('IDX_COMPLAINTS_DEPT_OFF_STAT_DL',
            'CREATE INDEX IDX_COMPLAINTS_DEPT_OFF_STAT_DL ON COMPLAINTS (DEPARTMENT, ASSIGNED_OFFICER, STATUS, SLA_DEADLINE, RE_RESPONSE_DEADLINE)');

    -- Serves countRolePoolQueue — the query whose MySQL plan was the measured §6.3 defect.
    add_idx('IDX_COMPLAINTS_DEPT_ROLE_STAT_DL',
            'CREATE INDEX IDX_COMPLAINTS_DEPT_ROLE_STAT_DL ON COMPLAINTS (DEPARTMENT, ASSIGNED_ROLE, STATUS, SLA_DEADLINE, RE_RESPONSE_DEADLINE)');
END;
/

COMMIT;

-- ═══ WHAT THIS SIGNAL CANNOT SAY ON TODAY'S DATA ═══
-- Measured on MySQL, because that is the only engine with populated data available. These are properties
-- of the dev DATASET rather than of the engine, so they are reported here too — but they have NOT been
-- re-measured against an Oracle instance.
--
-- 1. THE SIGNAL FIRES FOR THREE OFFICERS OUT OF 45. Open queues with at least one case inside a 48h
--    horizon: cepc_do1 (10 of 126 open, all 10 already overdue), rbio.officer (4 of 26, all overdue),
--    rbio_officer_001 (1 of 142, overdue). cepc_do_001, the largest queue at 597 open, has ZERO — its
--    seeded SLA_DEADLINE values all land 2026-10-27 or later. The signal is correct and almost silent.
--    With the brief's RE_RESPONSE_DEADLINE alone it would fire for NOBODY.
--
-- 2. THE HORIZON BUCKET IS EMPTY AND THE OVERDUE BUCKET IS NOT. Over the open complaints carrying an
--    SLA_DEADLINE: 563 are 30+ days out, 251 are 7-30 days, 15 are already overdue, and TWO sit in the
--    2-7 day band. Nothing falls strictly inside 48h without already having passed. Every count this
--    signal reports today is therefore an overdue count, which is why `overdue` is a qualifier on the
--    same sentence rather than a separate signal — a standalone "breaching soon" signal would never once
--    have rendered on this data.
--
-- 3. 375 OF 1186 OPEN COMPLAINTS CARRY NO DEADLINE OF ANY KIND. They are counted in the DENOMINATOR
--    (they are open cases in the officer's queue) and can never enter the numerator. Correct — "3 of
--    your 14" should mean 14 open cases, not 14 cases that happen to have a deadline — but it makes the
--    ratio pessimistic about coverage rather than about risk. `pending` (315 rows) is the worst
--    offender: 3 of 315 carry an SLA_DEADLINE.
--
-- 4. ASSIGNED_OFFICER IS NOT ALWAYS A PERSON. Six values are team or desk labels — 'RBIO OFFICER Team'
--    (24 rows), 'DEO Team' (15), 'Team Alpha'/'Team Beta'/'Team Gamma', 'Escalation Desk'. None will
--    ever match a resolved userId from RequestIdentityResolver, so their cases fall through to the
--    ROLE-POOL query rather than the officer one — which is the right outcome, and the second reason the
--    role-pool index above matters. NOT worked around further: mapping a team label onto its members
--    would be the signal inventing an assignment the register does not record.
