-- V114: Assistance — the closure-clause affinity rollup
-- Oracle version (mirrors database/V116__assistance_clause_recommendation.sql; the two directories'
-- V-numbers are not in sync — this is the twin of MySQL V116, not of any V114 there)
--
-- Brief 21 §5.3.2: "Closure-clause recommendation, ranked by historical co-occurrence with (category,
-- ground, entity type, resolution path), honouring restricted_to_roles. Reorder or annotate the existing
-- <select> rather than adding new UI."
--
-- THE FULL RATIONALE LIVES IN THE MYSQL TWIN and is not duplicated here, because two copies of a
-- 100-line argument diverge. What follows is the Oracle-specific part plus the measured summary a reader
-- of THIS file needs in order not to mistake a defensibly-silent feature for a broken one.
--
-- ═══ THE BRIEF'S KEY, IN ONE PARAGRAPH ═══
-- Measured 2026-10-07 against the MySQL dev database (the only engine with populated data available —
-- these are properties of the dataset, not of the engine, but they have NOT been re-measured against an
-- Oracle instance and that is stated rather than implied):
--   * "ground" — GROUND_OF_COMPLAINT_ID is populated on 0 of 4403 complaints. The column exists and
--     nothing writes it. Kept as a sentinel-bearing key part so the rollup sharpens without a migration.
--   * "category" — on 65 of the 877 clause-bearing rows (7%). Sentinel-bearing.
--   * "entity type" — the TYPE resolved through REGULATED_ENTITIES, not the dirty ENTITY_CODE (the same
--     entity appears as 'Punjab National Bank' and 'PNB'; keying on the code would split one real entity
--     across two cohorts and undercount it). Resolves on 1 of 877 rows today.
--   * "resolution path" — NO SUCH COLUMN. CLOSURE_CAUSE is the best-populated candidate and is
--     DISQUALIFIED BY LEAKAGE, not by sparsity: it is written in the same CLOSE_COMPLAINT call as the
--     CLOSURE_CLAUSE it would predict, so it is unknowable when an officer is picking a clause.
--     MAINTAINABILITY_DETERMINATION is substituted — thin (24 of 877) but decided BEFORE closure.
--   * SCHEME_VERSION is ADDED to the key and is the one dimension never wildcarded: a citation under one
--     Scheme is not evidence about another Scheme's clause vocabulary.
--
-- ═══ WHAT THE FLOORS LEAVE, MEASURED ═══ (MIN_COHORT_SAMPLE = 5, MIN_CLAUSE_SHARE = 0.05 in the service)
--   cohorts in the ladder 6 → clearing a 5-sample floor 5 → (cohort, clause) rows 13 → clearing the
--   share floor 5 rows across 5 cohorts and 2 distinct clauses.
-- FIVE ROWS is the honest size of this rollup today. The register holds FIVE distinct CLOSURE_CLAUSE
-- values across 877 rows, of which 15(1)(a) accounts for 833. Only 15(1)(a) and 16(2)(a) ever rank, and
-- 15(1)(a) is restricted to 'OMBUDSMAN,RBIO_ADMIN,ADMIN' — so for every other role it is filtered out of
-- the recommendation entirely, which is the restriction working rather than the feature failing.
--
-- ═══ ORACLE-SPECIFIC NOTES ═══
-- Applied BY HAND — there is no Flyway — so idempotency lives in this file. Oracle has no
-- CREATE TABLE IF NOT EXISTS, so each DDL is wrapped in a PL/SQL block guarded on USER_TABLES, which is
-- the pattern V113 established. No separate CREATE INDEX is needed: Oracle backs a UNIQUE constraint
-- with an index automatically, so UK_ACA_COHORT is both the constraint and the lookup index.
--
-- SENTINELS, NOT NULLS, and this is NOT merely a MySQL accommodation. Oracle does not enforce a
-- composite UNIQUE constraint across rows where any keyed column is NULL, so a NULL dimension would make
-- this key non-idempotent on BOTH engines and every refresh would append a fresh set of wildcard rows
-- instead of updating them. NULL is also not comparable with '=', so a nullable key column would make
-- the wildcard rows unreachable by any seek.
--
-- TEXT DIMENSIONS ARE NORMALISED ON WRITE (upper-cased in Java by the refresh), and the cross-engine
-- reason is specific: this database's vocabularies mix cases, MySQL's utf8mb4_unicode_ci collation folds
-- comparisons for free and Oracle's default collation does not. A value stored verbatim and compared
-- verbatim would match in dev and MISS in production. Normalising on write also means the read never
-- needs UPPER(column), which would defeat the index on both engines (§6.2).
--
-- The lease table ASSISTANCE_JOB_LOCK is NOT re-created here — oracle V113 owns it, and two migrations
-- owning one DDL is how a column width comes to differ between two environments. This file only seeds an
-- additional named lease row, because this refresh is a second job and two jobs sharing one lease row
-- would make each block the other for no reason: whichever fired first would hold the lease and the
-- other would skip its cycle and log "another pod holds the lease", which is both false and the hardest
-- kind of bug to see — a job that is merely never running.
--
-- NOT APPLIED as shipped. The read path reports an unranked picker when the table is absent or empty and
-- the frontend leaves the <select> in its existing order, so an unapplied migration degrades this
-- feature to invisible rather than breaking the closure picker.

-- ── The rollup ───────────────────────────────────────────────────────────────────────────────────
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_CLAUSE_AFFINITY';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ASSISTANCE_CLAUSE_AFFINITY (
                ID              NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,

                -- Widths matched to the SOURCE columns so a value cannot be silently truncated on the
                -- way in: COMPLAINTS.SCHEME_VERSION VARCHAR2(20), DEPARTMENT VARCHAR2(20),
                -- MAINTAINABILITY_DETERMINATION VARCHAR2(30), CLOSURE_CLAUSE VARCHAR2(100).
                -- ENTITY_TYPE is wider than ENTITY_CODE because it holds
                -- REGULATED_ENTITIES.ENTITY_TYPE, not the complaint''s own code.
                --
                -- SCHEME_VERSION is never a wildcard: pooling two Schemes'' clause vocabularies is a
                -- correctness bug, not a loss of precision. A complaint carrying none falls back to the
                -- SAME default ClosureClauseAccessService uses, which is what keeps the rollup and the
                -- picker in agreement.
                SCHEME_VERSION  VARCHAR2(20)  NOT NULL,

                -- ''*'' = not department-specific. CEPC, RBIO and CRPC run different machines with
                -- different clause vocabularies, and the department is known long before closure.
                DEPARTMENT      VARCHAR2(20)  DEFAULT ''*'' NOT NULL,

                -- 0 = not category-specific. A sentinel and NOT NULL — see the header on why NULL
                -- cannot be used in a composite UNIQUE constraint on either engine.
                CATEGORY_KEY    NUMBER(19)    DEFAULT 0 NOT NULL,

                -- 0 = not ground-specific. The sentinel on EVERY row this rollup can write today:
                -- GROUND_OF_COMPLAINT_ID is populated on 0 of 4403 complaints. Carried so the rollup
                -- sharpens without a migration when something starts writing it.
                GROUND_KEY      NUMBER(19)    DEFAULT 0 NOT NULL,

                -- ''*'' = not entity-type-specific. Holds REGULATED_ENTITIES.ENTITY_TYPE (a controlled
                -- vocabulary), NOT the complaint''s dirty ENTITY_CODE. ''*'' rather than '''' because an
                -- empty string is what the dirty source column already uses for "no entity" on some
                -- rows — and on Oracle an empty string IS NULL, which a key column may not be.
                ENTITY_TYPE     VARCHAR2(100) DEFAULT ''*'' NOT NULL,

                -- ''*'' = not path-specific. Holds MAINTAINABILITY_DETERMINATION; the better-populated
                -- CLOSURE_CAUSE is disqualified by leakage, not by sparsity. See the header.
                RESOLUTION_PATH VARCHAR2(40)  DEFAULT ''*'' NOT NULL,

                -- The clause, as the RAW value from COMPLAINTS.CLOSURE_CLAUSE (''15(1)(a)''). A machine
                -- key: the client matches it against the <option> values the picker already renders.
                -- NOT case-folded, unlike every other text column here, because a statutory citation
                -- has one spelling and CLOSURE_CLAUSE_MASTER.CLAUSE_CODE is the authority for it. The
                -- read matches these against the master and silently ignores any that do not match, so
                -- a junk value in the source column cannot put a clause into an officer''s picker that
                -- the master does not hold.
                CLAUSE_CODE     VARCHAR2(100) NOT NULL,

                -- The numerator and the denominator. BOTH, always, and both NOT NULL: Brief 21''s
                -- position is that "a bare recommendation with no denominator will be distrusted,
                -- correctly", so the schema does not permit storing one without the other.
                --
                -- COHORT_TOTAL is denormalised onto every row of a cohort rather than derived by
                -- summing them, so the read needs no second aggregate and a partially-refreshed cohort
                -- cannot show a numerator from this pass against a denominator built from the last.
                OCCURRENCES     NUMBER(19)    NOT NULL,
                COHORT_TOTAL    NUMBER(19)    NOT NULL,

                -- When the refresh that wrote this row ran. Read by nothing in the request path — it
                -- exists so a rollup that quietly stopped refreshing is diagnosable, which is the one
                -- failure a scheduled job has that an endpoint does not: stale counts look exactly like
                -- correct counts. It is also the stale sweep''s only predicate.
                REFRESHED_AT    TIMESTAMP(6)  NOT NULL,

                CONSTRAINT PK_ASSISTANCE_CLAUSE_AFFINITY PRIMARY KEY (ID),

                -- The key IS the lookup and the idempotency. Oracle backs a UNIQUE constraint with an
                -- index automatically, so this is also the only index the read needs. The refresh
                -- upserts against this constraint rather than truncating — a TRUNCATE plus reload would
                -- leave every closure picker unranked for the duration of every refresh.
                --
                -- CLAUSE_CODE is the SEVENTH column and not part of the lookup predicate. Two jobs for
                -- it: it makes the constraint one-row-per-(cohort, clause), which is what lets the
                -- refresh find and overwrite a specific clause''s counts instead of accumulating them;
                -- and because it is the trailing key part, the read''s ORDER BY CLAUSE_CODE is served
                -- by the index itself with no sort.
                CONSTRAINT UK_ACA_COHORT UNIQUE (SCHEME_VERSION, DEPARTMENT, CATEGORY_KEY, GROUND_KEY,
                                                 ENTITY_TYPE, RESOLUTION_PATH, CLAUSE_CODE)
            )';
    END IF;
END;
/

-- ── This refresh's own lease row ─────────────────────────────────────────────────────────────────
-- ASSISTANCE_JOB_LOCK is created by oracle V113 and is NOT redefined here. Only the row is added, under
-- a SEPARATE lease name from 'assistance-next-action-refresh' — see the header for why sharing one row
-- would silently stop one of the two jobs.
--
-- Dated in the past so the first pod to start can take it immediately. Guarded on the table EXISTING as
-- well as on the row's absence, so applying this file before V113 inserts nothing and raises nothing —
-- and the refresh service treats a missing lease row as "I did not get the lock" and does nothing, which
-- is the correct behaviour for a half-applied schema.
--
-- EXECUTE IMMEDIATE rather than a plain INSERT, because a static INSERT against a table that may not
-- exist fails at COMPILE time inside a PL/SQL block — the guard on USER_TABLES would never be reached.
DECLARE
    v_table NUMBER;
    v_row   NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_JOB_LOCK';
    IF v_table > 0 THEN
        EXECUTE IMMEDIATE
            'SELECT COUNT(*) FROM ASSISTANCE_JOB_LOCK '
            || 'WHERE LOCK_NAME = ''assistance-clause-affinity-refresh'''
            INTO v_row;
        IF v_row = 0 THEN
            EXECUTE IMMEDIATE
                'INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT) '
                || 'VALUES (''assistance-clause-affinity-refresh'', '
                || 'TIMESTAMP ''1970-01-01 00:00:00'', NULL, NULL)';
        END IF;
    END IF;
END;
/

COMMIT;

-- ── No new index on COMPLAINTS, and that is a measured result ────────────────────────────────────
-- The refresh reads the clause-bearing complaints keyset-paged on the primary key
-- (WHERE ID > :after ORDER BY ID), which needs no index that does not already exist. A covering index
-- on (CLOSURE_CLAUSE, DEPARTMENT, CATEGORY_ID, ENTITY_CODE, SCHEME_VERSION) was considered and
-- rejected: the job reads EVERY qualifying row by design, so there is nothing for it to seek, and the
-- index would be another B-tree maintained on every single complaint write for the benefit of one job
-- that runs every six hours.
--
-- The REQUEST path touches COMPLAINTS only through the complaint the officer already has open — the
-- closure form has it in hand — and then reads ASSISTANCE_CLAUSE_AFFINITY by UK_ACA_COHORT. It issues no
-- aggregate and no scan, which is the entire point of precomputing.
--
-- MEASURED PLAN, STATED HONESTLY: the EXPLAIN recorded in the MySQL twin (access_type=ref,
-- key=UK_ACA_COHORT, all six leading key parts used, using_filesort=false, 2 rows examined) was taken on
-- MySQL 8.4, because that is the only engine with populated data available to this session. It has NOT
-- been re-measured on Oracle. The shape is an equality seek on the leading columns of a unique index
-- followed by an ordered read of its trailing column, which Oracle serves the same way — but that is
-- reasoning, not a measurement, and is labelled as such rather than presented as a verified plan.
