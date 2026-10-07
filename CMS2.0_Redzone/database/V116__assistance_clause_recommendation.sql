-- V116: Assistance — the closure-clause affinity rollup
-- MySQL version. Oracle twin: database/oracle/V114__assistance_clause_recommendation.sql
--
-- Brief 21 §5.3.2: "Closure-clause recommendation, ranked by historical co-occurrence with (category,
-- ground, entity type, resolution path), honouring restricted_to_roles. Reorder or annotate the existing
-- <select> rather than adding new UI."
--
-- Tier 1, like its sibling V115. This is a COUNT of which clauses the register already cited in a
-- comparable situation, with the denominator attached. No inference over case text, no model artifact.
-- §6.2's governing rule — "rollups are computed on a schedule, never on request" — is why a table exists
-- rather than a GROUP BY behind the closure form.
--
-- This is NOT the entity-clause-precedent rail signal that already ships. That one reports a single COUNT
-- beside the complaint ("23 earlier complaints against this entity cited 16(2)(a)") and reorders nothing.
-- This RANKS the clause vocabulary for the picker. A reader comparing the two by name would wrongly
-- conclude §5.3.2 had shipped.
--
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- THE BRIEF'S RANKING KEY IS NOT AVAILABLE ON THIS SCHEMA. MEASURED 2026-10-07 against cms_db.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════════
-- Taken key part by key part. Where the brief and the code disagree, the code wins; this is the code
-- winning, written down rather than bent to match.
--
-- 1. "ground" — THE COLUMN EXISTS AND NOTHING WRITES IT.
--    COMPLAINTS.ground_of_complaint_id is populated on 0 of 4403 rows. Not 'few': ZERO. It is kept as a
--    key part carrying a sentinel anyway — the brief names it, GROUND_OF_COMPLAINT_MASTER exists, and a
--    key column costs nothing until it is written, at which point the rollup sharpens with no migration.
--    Dropping it would make adding it later a schema change. But it must be said plainly: on today's
--    data GROUND_KEY is the sentinel on every row this rollup will ever write.
--
-- 2. "category" — PRESENT BUT NEARLY EMPTY, so it is a key part carrying a SENTINEL.
--    877 rows carry a closure_clause and only 65 of those also carry a category_id (7%). Keying on
--    category as REQUIRED would produce cohorts covering 65 closures out of 877. Hence CATEGORY_KEY = 0
--    meaning "not category-specific", exactly as V115 does, for exactly the same measured reason.
--
-- 3. "entity type" — THE TYPE, NOT THE CODE, and it resolves on almost nothing.
--    entity_code is free text and documented-dirty: the same entity appears as 'Punjab National Bank'
--    and 'PNB'. Keying on the CODE would split one real entity across two cohorts and undercount it.
--    So the key carries REGULATED_ENTITIES.entity_type, resolved through RegulatedEntity.normalize —
--    the same normalisation the entity applied to its own name on write, which is what lets
--    'HDFC Bank' find 'HDFC BANK'. It will NOT make 'PNB' find 'Punjab National Bank'; that is an ALIAS
--    problem and belongs to the entity-code data migration owned elsewhere. It is handled where it is
--    read, not fixed globally, and the surviving dirt is reported rather than silently corrected.
--      MEASURED: of the 877 clause-bearing rows, 876 hold 'Test Bank Ltd' — which matches no registered
--    entity — and 1 holds 'HDFC Bank', which does resolve. So ENTITY_TYPE is the sentinel on 876 of 877
--    rows and the dimension contributes essentially nothing today.
--
-- 4. "resolution path" — NO SUCH COLUMN, and its nearest neighbour is DISQUALIFIED BY LEAKAGE.
--    The closest candidate is closure_cause (RESOLVED / ADMIN_CLOSED / NON_MAINTAINABLE), populated on
--    875 of 877 clause-bearing rows. It cannot be used, and the reason is not sparsity:
--    CepcWorkflowService and RbioWorkflowService set closure_cause and closure_clause in the SAME
--    CLOSE_COMPLAINT call. So the "feature" is written at the same instant as the label it would
--    predict. Measured consequence at RECOMMENDATION time, which is the only time that matters: a
--    rollup keyed on it would score beautifully in a backtest and be unanswerable for almost every
--    complaint an officer actually has open, because an open complaint has no closure_cause yet.
--      maintainability_determination is used instead. It is thin — 24 of 877 clause-bearing rows — but
--    it is decided BEFORE closure and is therefore knowable when the officer is picking a clause, and it
--    separates the one genuinely distinct clause population in the data. Thin and HONEST is preferred to
--    well-populated and unreadable. That is a substitution, not the brief's column renamed.
--
-- 5. "scheme version" — ADDED, and it is the one dimension that is never wildcarded.
--    Not in the brief's key. A citation under one Scheme is not evidence about a different Scheme's
--    clause vocabulary, and CLOSURE_CLAUSE_MASTER is itself scheme-scoped and date-bounded so that a
--    complaint is judged under the Scheme in force when it was created. Pooling schemes would be the one
--    place a wildcard is a correctness bug rather than a loss of precision.
--
-- So the key is (SCHEME_VERSION, DEPARTMENT, CATEGORY_KEY, GROUND_KEY, ENTITY_TYPE, RESOLUTION_PATH) and
-- the ranked value is CLAUSE_CODE.
--
-- ═══ ONE ROW PER (COHORT, CLAUSE) — UNLIKE V115, AND DELIBERATELY ═══
-- The next-action rollup stores only the winning action, because the rail shows one suggestion. This
-- rollup must store the whole surviving DISTRIBUTION, because the deliverable is an ORDERING of a
-- <select> with 15 options: a single winner could annotate one option and would leave the other
-- fourteen in the master's order, which is not a ranking. MAX_CLAUSES_PER_COHORT in the entity caps how
-- many a read will accept, so the lookup stays bounded rather than becoming a sort over a distribution.
--
-- ═══ THE SENTINEL LADDER ═══
-- Each closure is counted into a ladder of cohorts, from the most specific combination its own data
-- supports down to (scheme, department) and (scheme, *):
--   L4  scheme + department + category + ground + entityType + resolutionPath
--   L3  scheme + department + category + ground + entityType
--   L2  scheme + department + category + ground
--   L1  scheme + department
--   L0  scheme
-- The read walks the same ladder downward and STOPS at the first level clearing the floors, so a
-- recommendation is always drawn from the most specific evidence available and never mixes two levels.
-- That is also how the brief's "larger denominator wins" is enforced: a 5-closure cohort at L4 and an
-- 821-closure cohort at L1 are never in the same ordering, so the thin one cannot outrank the thick one
-- on share. The thin one wins only when it is ALSO more specific, which is a different and defensible
-- claim — it is evidence about cases more like this one.
--   A level whose specific dimensions are all absent produces the SAME key as the level below, which is
-- why the refresh de-duplicates the ladder per closure. Counting a row twice under one key would
-- multiply numerator AND denominator and leave the share unchanged while making the DENOMINATOR a lie —
-- precisely the number §5.1 says an officer must be able to trust.
--
-- SENTINELS, NOT NULLS. A composite UNIQUE key containing a NULL prevents duplicates on neither MySQL
-- nor Oracle, so a NULL dimension would make this key non-idempotent and every refresh would append a
-- fresh set of wildcard rows instead of updating them. NULL is also not comparable with '=', so a
-- nullable key column would make the wildcard rows unreachable by any seek. No real category_id or
-- ground_of_complaint_id is 0 (both columns are AUTO_INCREMENT from 1).
--
-- ═══ THE FLOORS, AND WHAT THEY LEAVE — MEASURED, SIMULATED IN SQL AGAINST cms_db ═══
-- (the service holds the authoritative constants: MIN_COHORT_SAMPLE = 5, MIN_CLAUSE_SHARE = 0.05)
--   cohorts in the whole ladder ................................  6
--   cohorts clearing cohort_total >= 5 .........................  5
--   (cohort, clause) rows in those cohorts ..................... 13
--   + clause share >= 5% .......................................  5 rows / 5 cohorts / 2 clauses
-- FIVE ROWS is the honest size of this rollup against today's register. It is small because the register
-- holds FIVE distinct closure_clause values across 877 rows, of which 15(1)(a) accounts for 833. A
-- richer-looking table could only be produced by lowering the floors until anecdotes qualified.
--
-- ═══ WHAT THIS CANNOT SAY, MEASURED — read this before calling the feature broken ═══
-- A feature that is silent for a defensible reason is indistinguishable from one that is broken, and the
-- next reader will test this against this database.
--
-- A. ONLY TWO CLAUSES EVER RANK: 15(1)(a) and 16(2)(a). The other thirteen in CLOSURE_CLAUSE_MASTER have
--    never been cited often enough to clear the share floor, and several have never been cited at all.
--    So the picker is reordered on at most 2 of its 15 options.
-- B. 15(1)(a) IS RESTRICTED TO 'OMBUDSMAN,RBIO_ADMIN,ADMIN'. §5.3.2 and §4 both require
--    restricted_to_roles to be honoured, so for every other role that clause is filtered out of the
--    recommendation entirely — leaving 16(2)(a) alone, and leaving the RBIO cohorts (whose only clause is
--    15(1)(a)) with nothing to offer. An RBIO_OFFICER therefore gets NO reordering on an RBIO complaint.
--    That is the restriction working, not the feature failing.
-- C. THE DISTRIBUTION IS DEGENERATE, WHICH IS A PROPERTY OF SEEDED DATA. 15(1)(a) holds 94.6% of the
--    largest cohort and 100% of the RBIO one. A ranking over a near-constant label is correct and nearly
--    useless; the mechanism is built with its floors, and on THIS data it has not been exercised against
--    a distribution that would test them. Said plainly rather than reported as a passing test.
-- D. 4000+ COMPLAINTS CARRY NO CLAUSE AT ALL (877 of 4403 do). They are invisible to this rollup, which
--    is correct — a complaint with no citation is no evidence about citations. Many closed ones are also
--    unappealable as a result, which is a recorded defect elsewhere and not this migration's to fix.
--
-- ═══ IDEMPOTENCY, AND THE LOCK ═══
-- Applied BY HAND — there is no Flyway in this project — so idempotency lives in this file. CREATE TABLE
-- is guarded with IF NOT EXISTS. No secondary index is created, so the MySQL errno-1061 problem V115
-- solves with an information_schema guard does not arise here; see the foot of the file for why no index
-- is needed beyond the unique constraint.
--
-- The lease table ASSISTANCE_JOB_LOCK is NOT re-created here — V115 owns it, and two migrations owning
-- one DDL is how a column width comes to differ between two environments. This file only seeds an
-- additional named lease row, because this refresh is a second job and two jobs sharing one lease row
-- would make each block the other for no reason: whichever fired first would hold the lease and the
-- other would skip its cycle and log "another pod holds the lease", which is both false and the hardest
-- kind of bug to see — a job that is merely never running. The INSERT is guarded on its own absence and
-- on the table existing, so applying V116 before V115 degrades to "no lease row" rather than to an
-- error — and the refresh service treats a missing lease row as "I did not get the lock" and does
-- nothing, which is the correct behaviour for a half-applied schema.
--
-- NOT APPLIED to cms_db as shipped. The read path reports an unranked picker when the table is absent or
-- empty, and the frontend leaves the <select> in its existing order, so an unapplied migration degrades
-- this feature to invisible rather than breaking the closure picker.

-- ── The rollup ───────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ASSISTANCE_CLAUSE_AFFINITY (
    id              BIGINT       NOT NULL AUTO_INCREMENT,

    -- Widths matched to the SOURCE columns so a value cannot be silently truncated on the way in:
    -- COMPLAINTS.scheme_version varchar(20), department varchar(20), entity_code varchar(50),
    -- maintainability_determination varchar(30), closure_clause varchar(100). ENTITY_TYPE is wider than
    -- entity_code because it holds REGULATED_ENTITIES.entity_type, not the complaint's code.
    --
    -- Never a wildcard. See header item 5: pooling two Schemes' clause vocabularies is a correctness
    -- bug, not a loss of precision. A complaint carrying no scheme_version falls back to the SAME
    -- default ClosureClauseAccessService uses, which is what keeps the rollup and the picker in
    -- agreement. MEASURED: 4092 of 4403 complaints carry no scheme_version, so that fallback decides
    -- the scheme for almost every row.
    SCHEME_VERSION  VARCHAR(20)  NOT NULL,

    -- '*' = not department-specific. DEPARTMENT stands in for nothing in the brief's key; it is here
    -- because CEPC, RBIO and CRPC run different machines with different clause vocabularies, and it is
    -- known long before closure. MEASURED: 876 of 877 clause-bearing rows carry one.
    DEPARTMENT      VARCHAR(20)  NOT NULL DEFAULT '*',

    -- 0 = not category-specific. A SENTINEL and NOT NULL — see the header on why NULL cannot be used
    -- in a composite UNIQUE key on either engine.
    CATEGORY_KEY    BIGINT       NOT NULL DEFAULT 0,

    -- 0 = not ground-specific. MEASURED as the sentinel on EVERY row this rollup can write today:
    -- COMPLAINTS.ground_of_complaint_id is populated on 0 of 4403 rows. Carried so the rollup sharpens
    -- without a migration when something starts writing it.
    GROUND_KEY      BIGINT       NOT NULL DEFAULT 0,

    -- '*' = not entity-type-specific. Holds REGULATED_ENTITIES.entity_type (a controlled vocabulary of
    -- 145 typed entities), NOT the complaint's dirty entity_code. '*' rather than '' because an empty
    -- string is what the dirty source column already uses for "no entity" on some rows — reusing it
    -- would merge "no entity recorded" with "all entities".
    ENTITY_TYPE     VARCHAR(100) NOT NULL DEFAULT '*',

    -- '*' = not path-specific. Holds maintainability_determination; see header item 4 for why the
    -- better-populated closure_cause is disqualified by leakage rather than by sparsity.
    RESOLUTION_PATH VARCHAR(40)  NOT NULL DEFAULT '*',

    -- The clause, as the RAW value from COMPLAINTS.closure_clause ('15(1)(a)'). A machine key: the
    -- client matches it against the <option> values the picker already renders, so it must be the
    -- clause_code vocabulary and not prose. NOT case-folded, unlike every other text column here,
    -- because a statutory citation has one spelling and CLOSURE_CLAUSE_MASTER.clause_code is the
    -- authority for it. The read matches these against the master and silently ignores any that do not
    -- match, so a junk value in the source column cannot put a clause into an officer's picker that the
    -- master does not hold. The displayed label comes from CLOSURE_CLAUSE_MASTER and the sentence around
    -- it from the i18n bundle; nothing here is shown to an officer directly.
    CLAUSE_CODE     VARCHAR(100) NOT NULL,

    -- The numerator and the denominator. BOTH, always, and both NOT NULL: Brief 21's position is that
    -- "a bare recommendation with no denominator will be distrusted, correctly", so the schema does not
    -- permit storing one without the other. "15(1)(a), used in 777 of 821 comparable closures" is a
    -- statement an officer can weigh; "15(1)(a) recommended" is not.
    --
    -- COHORT_TOTAL is denormalised onto every row of a cohort rather than derived by summing them, so
    -- the read needs no second aggregate and so a partially-refreshed cohort cannot show a numerator
    -- from this pass against a denominator built from the last.
    OCCURRENCES     BIGINT       NOT NULL,
    COHORT_TOTAL    BIGINT       NOT NULL,

    -- When the refresh that wrote this row ran. Read by nothing in the request path — it exists so a
    -- rollup that quietly stopped refreshing is diagnosable, which is the one failure a scheduled job
    -- has that an endpoint does not: stale counts look exactly like correct counts. It is also the
    -- stale sweep's only predicate.
    REFRESHED_AT    DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),

    -- The key IS the lookup and the idempotency. The read is one equality seek on the six leading
    -- columns, and the refresh upserts against this constraint rather than truncating — a TRUNCATE plus
    -- reload would leave every closure picker unranked for the duration of every refresh, a
    -- self-inflicted degradation on a feature whose contract is to be there when the form opens.
    --
    -- CLAUSE_CODE is the SEVENTH column and not part of the lookup predicate. Two jobs for it: it makes
    -- the constraint one-row-per-(cohort, clause), which is what lets the refresh find and overwrite a
    -- specific clause's counts instead of accumulating them; and because it is the trailing key part,
    -- the read's ORDER BY CLAUSE_CODE is served by the index itself with no filesort.
    --
    -- MEASURED PLAN for the request-path read, against a scratch table carrying the five rows this
    -- rollup would hold on today's register (EXPLAIN FORMAT=JSON, MySQL 8.4):
    --
    --   EXPLAIN SELECT * FROM ASSISTANCE_CLAUSE_AFFINITY
    --    WHERE SCHEME_VERSION='RBIOS_2021' AND DEPARTMENT='CEPC' AND CATEGORY_KEY=0 AND GROUND_KEY=0
    --      AND ENTITY_TYPE='*' AND RESOLUTION_PATH='*' ORDER BY CLAUSE_CODE;
    --   access_type=ref  key=UK_ACA_COHORT  key_len=744  rows_examined_per_scan=2  filtered=100.00
    --   used_key_parts=[SCHEME_VERSION, DEPARTMENT, CATEGORY_KEY, GROUND_KEY, ENTITY_TYPE,
    --                   RESOLUTION_PATH]
    --   using_filesort=false
    --
    -- All six leading key parts used, no filesort, no table scan.
    UNIQUE KEY UK_ACA_COHORT (SCHEME_VERSION, DEPARTMENT, CATEGORY_KEY, GROUND_KEY, ENTITY_TYPE,
                              RESOLUTION_PATH, CLAUSE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── This refresh's own lease row ─────────────────────────────────────────────────────────────────
-- ASSISTANCE_JOB_LOCK is created by V115 and is NOT redefined here. Only the row is added, under a
-- SEPARATE lease name from 'assistance-next-action-refresh' — see the header for why sharing one row
-- would silently stop one of the two jobs.
--
-- Dated in the past so the first pod to start can take it immediately. Guarded on the table EXISTING as
-- well as on the row's absence, so applying this file before V115 inserts nothing and raises nothing.
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-clause-affinity-refresh', TIMESTAMP('1970-01-01 00:00:00'), NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-clause-affinity-refresh'
);

ANALYZE TABLE ASSISTANCE_CLAUSE_AFFINITY;

-- ── No new index on COMPLAINTS, and that is a measured result ────────────────────────────────────
-- The refresh reads the clause-bearing complaints keyset-paged on the PRIMARY KEY
-- (WHERE id > :after ORDER BY id), which needs no index that does not already exist. A covering index
-- on (closure_clause, department, category_id, entity_code, scheme_version) was considered and
-- rejected: the job reads EVERY qualifying row by design, so there is nothing for it to seek, and the
-- index would be another B-tree maintained on every single complaint write for the benefit of one job
-- that runs every six hours.
--
-- The REQUEST path touches COMPLAINTS only through the complaint the officer already has open — the
-- closure form has it in hand — and then reads ASSISTANCE_CLAUSE_AFFINITY by UK_ACA_COHORT. It issues
-- no aggregate and no scan, which is the entire point of precomputing.
