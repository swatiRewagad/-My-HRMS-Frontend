-- V115: Assistance rail — the entity-pattern rollup, and the lease its refresh job runs under
-- Oracle version. MySQL twin: database/V117__assistance_entity_pattern_rollup.sql
-- (the two directories' V-numbers are not in sync — this is the twin of MySQL V117, not of any V115
-- there.)
--
-- Brief 21 §5.3.5: "Entity pattern alert, from a scheduled rollup only: 'this entity has N open cases
-- on this ground this quarter.' Never computed live."
--
-- TIER 1, and a COUNT. Open cases in one (entity, ground, quarter, department, office) window with the
-- window's total beside it. No inference over case text and no model artifact.
--
-- ═══ "NEVER COMPUTED LIVE" IS THE BRIEF'S RULE, NOT A WORKAROUND FOR A MISSING INDEX ═══
-- Stated carefully because an earlier draft of this header claimed ENTITY_CODE was unindexed, and that
-- was wrong on the one engine where it could be checked. MEASURED on the MySQL dev database:
-- idx_complaint_entity_code EXISTS and the equivalent live query plans as a seek (type=ref,
-- rows=155), not a scan. NOT RE-MEASURED ON ORACLE — no Oracle instance was reachable — so whether
-- the corresponding index exists in an Oracle environment is UNVERIFIED here.
--
-- Either way the justification for this table is the brief's rule and not a query plan: §5.3.5 says
-- "from a scheduled rollup only... Never computed live" and §6.2 says rollups are computed on a
-- schedule and never on request. The rail renders on EVERY staff screen load, so an aggregate here is
-- an aggregate on the critical path of every page in the product. The rollup turns that into a
-- unique-key seek on a table of tens of rows.
--
-- ═══ DEPARTMENT IS A TENANCY FENCE AND IS PART OF THE KEY ═══
-- A disclosure control, not a modelling preference. §5.3.5 is a CROSS-COMPLAINT disclosure: it tells
-- an officer about OTHER complainants' live cases against a named entity. Brief 21 §4 requires the
-- restrictive default until a human rules otherwise, so the department is part of the KEY:
--   * There is NO row aggregated across departments, so no read can accidentally serve one — the
--     restriction is a property of the SCHEMA and not of whichever query gets written against it.
--   * AssistanceRailService additionally refuses to read a department other than the one on the
--     complaint in front of the officer, so the fence holds on both sides. Removing either half is a
--     disclosure change and needs the ruling recorded in the findings as open ask 3.
-- A future ruling permitting cross-department visibility would add a SENTINEL department row the way
-- OFFICE_CODE already carries one. It must NOT be implemented by dropping the predicate.
--
-- MEASURED (MySQL) on the 4,112 eligible rows: CEPC 2,831 (663 open) / RBIO 1,223 (247) / CRPC 58 (17).
--
-- ═══ NOTHING COMPLAINANT-IDENTIFYING CAN REACH THIS TABLE ═══
-- Verifiable by inspection of the column list: five keys, two counts, one timestamp. No complainant
-- name, phone, email, address, account or card number; no complaint number; no free text. A row cannot
-- identify anyone even if the whole table were dumped — the payload is a count and a denominator about
-- a REGULATED ENTITY, which is not personal data.
--
-- ═══ ENTITY_KEY IS NORMALISED ON THE WRITE SIDE, AND A CASE FOLD WOULD NOT HAVE DONE IT ═══
-- Measured against the MySQL dev database — the only engine with populated data reachable — ENTITY_CODE
-- holds 33 distinct values over 4,113 non-blank rows, and UPPER(TRIM(...)) collapses those 33 to 33.
-- There are NO case collisions, so a case fold buys nothing. What actually costs recall is SYNONYMY:
--     HDFC / HDFC Bank            ICICI / ICICI Bank
--     AXIS / Axis Bank            CANARA / Canara Bank
--     PNB / Punjab National Bank  SBI / State Bank of India
--     BOB / Bank of Baroda        UNION / Union Bank / Union Bank of India
--     INDIAN (Indian Bank)        KOTAK (Kotak Mahindra Bank)
-- 'PNB' and 'Punjab National Bank' share no character position, so no folding function merges them.
-- The normalisation is an explicit ALIAS TABLE applied on the WRITE side in Java, where it can be read
-- and unit-tested (AssistanceEntityAliasNormaliser). Measured effect: 33 distinct raw values become 24
-- normalised keys.
--   ENTITY_KEY holds the normalised form and the read normalises through the SAME function before
-- seeking. On ORACLE the write-side normalisation removes a REAL defect rather than a cosmetic one:
-- MySQL's utf8mb4_0900_ai_ci collation folds case for free, so 'HDFC Bank' and 'HDFC BANK' are one key
-- there, while Oracle's default collation is case-SENSITIVE and would hold them apart. Upper-casing
-- before storing is what makes the two engines produce identical rows.
--
-- NOT a global fix to ENTITY_CODE — the brief assigns that data migration elsewhere. Nothing here
-- UPDATEs COMPLAINTS. The alias table is hand-written, finite and deliberately NOT fuzzy: a similarity
-- match merges two different banks on a bad day, and a wrong count presented as a pattern is worse than
-- no signal at all.
--
-- ═══ GROUND_KEY IS A SENTINEL, BECAUSE THE BRIEF'S OWN DIMENSION IS EMPTY ═══
-- GROUND_OF_COMPLAINT_ID is populated on 0 of 4,403 complaints. Zero. So the brief's "on this ground"
-- dimension cannot be keyed as required without shipping an empty table. 0 means "this window is not
-- ground-specific": every complaint counts into the agnostic row AND into its ground row where it has
-- one (today: never), and the read prefers the specific row. The day grounds start being written the
-- signal sharpens with no schema or code change, and until then the rail's sentence says "across all
-- grounds" rather than implying a filter that is not happening.
--
-- 0 AND NOT NULL, and the reason holds on BOTH engines though it is usually described as a MySQL quirk:
-- Oracle does not enforce a composite UNIQUE constraint across rows where any keyed column is NULL, so
-- a NULL ground would let every refresh append a fresh set of agnostic rows instead of updating them.
-- The sentinel keeps one row per window, identically here and on MySQL. Same argument for OFFICE_CODE's
-- '*', which is also distinguishable from a blank that got in by accident where '' would not be.
--
-- ═══ QUARTER_KEY IS THE COMPLAINT'S OWN FILING QUARTER, WHICH IS WHY THERE IS NO CLIFF ═══
-- The brief says "this quarter" and this column implements exactly that: yyyyQ, 20263 being 2026 Q3,
-- from CREATED_AT — when the complaint was FILED.
--
-- The obvious objection to a calendar quarter is the CLIFF: at 00:00 on 1 July a "current quarter"
-- count resets and an entity with 500 live cases is reported as having 2. It does not apply here,
-- because the read does not ask for the CURRENT quarter — it asks for the quarter of the complaint in
-- front of the officer. A complaint filed on 30 June goes on reading its own Q2 window for as long as
-- it exists, so the number is the cohort that complaint actually belongs to and it never changes under
-- them. That is also why there is no WINDOW_DAYS column: a rolling window would be a second, different
-- window concept layered on a key that already has one.
--
-- COMPUTED IN JAVA, not in SQL, and on Oracle that is not merely tidiness: QUARTER() does not exist
-- here, the equivalent is TO_CHAR(CREATED_AT,'Q'), and a value computed by two different dialects has
-- to be byte-identical or the two engines hold different keys for the same complaint.
--
-- MEASURED (MySQL) on the eligible rows: 20261=26, 20263=2,442, 20264=1,644.
--
-- ═══ OFFICE_CODE IS SENTINELLED FOR COVERAGE, WHERE DEPARTMENT IS KEYED FOR CORRECTNESS ═══
-- RBIO_OFFICE_CODE is populated on 410 of 4,403 rows (9%), so keying on it as REQUIRED would make this
-- feature silent on 91% of the register. Both rows are written — agnostic always, office-specific where
-- that office clears the floor on its own — and the read prefers the specific one. Measured at the
-- floor: 13 agnostic windows qualify against 5 office-specific ones. No real office code is '*' (the
-- measured values are 011, 013, 014, 016, 017, 021, C01).
--
-- ═══ "OPEN" COMES FROM RBIO_STATUS_MASTER, NOT FROM A LITERAL LIST ═══
-- Two hardcoded copies of the closed-status vocabulary previously existed in this codebase and they
-- DISAGREED — one held six values and the other four, omitting 'adjudicated' and 'conciliated', so a
-- complaint closed by an award counted as open to one of them. The refresh resolves "closed" through
-- RbioStatusVocabulary (RBIO_STATUS_MASTER.IS_CLOSED='Y') and "open" is its complement. Measured on the
-- MySQL dev register: eight closed statuses, leaving 927 of the 4,112 eligible rows open. The discarded
-- alternative — a literal LOWER(status) NOT IN ('closed','withdrawn','rejected') — gives 941; the
-- 14-row difference is complaints closed by adjudication, conciliation or a regulator forward, which
-- that shorter list would have counted as live cases against the entity.
--   The comparison happens in JAVA and not in SQL, which matters more here than on MySQL: COMPLAINTS
-- holds lowercase status values while the ComplaintStatus enum is UPPERCASE, and on Oracle a predicate
-- written in the enum's spelling matches NOTHING — there is no ai_ci collation to rescue it.
--
-- ═══ THE k>=5 FLOOR, AND WHAT IT COSTS ═══
-- A window is stored only if it holds at least 5 OPEN cases — the same floor as
-- AssistanceRailService.MIN_CLOSURE_SAMPLE, for the same reason. Measured on the MySQL dev register: of
-- 43 (entity, department, quarter) windows holding at least one open case, 13 clear the floor covering
-- 880 open cases out of 3,993 complaints, plus 5 office-specific windows covering 64 of 131. Eighteen
-- rows in total. 'TEST BANK LTD' alone accounts for 682 of the 880 and 'TEST_BANK_001' for another 64,
-- so the floor has NOT been exercised against a realistic distribution of entities. Said plainly.
--
-- Re-running is safe: every CREATE is guarded on USER_TABLES and the seed row on its own absence.
-- TIMESTAMP(6) where MySQL uses DATETIME(6), NUMBER(19) where MySQL uses BIGINT, matching the siblings.
--
-- NOT APPLIED to any environment as shipped, and NO ORACLE INSTANCE WAS REACHABLE to apply it to — so
-- unlike its MySQL twin this file has not been executed even once. Stated rather than implied. The rail
-- reads the table through a guarded signal and reports nothing when it is absent, so an unapplied
-- migration costs exactly one signal.

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_ENTITY_PATTERN';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ASSISTANCE_ENTITY_PATTERN (
                ID             NUMBER(19) GENERATED BY DEFAULT AS IDENTITY,

                -- The NORMALISED entity: upper-cased, trimmed and alias-resolved. Width matched to
                -- COMPLAINTS.ENTITY_CODE''s own VARCHAR2(50) so a value cannot be silently truncated:
                -- normalisation maps an alias to its canonical name, and ''PUNJAB NATIONAL BANK'' is
                -- longer than the ''PNB'' it came from.
                ENTITY_KEY     VARCHAR2(50)  NOT NULL,

                -- UPPER(TRIM(COMPLAINTS.DEPARTMENT)). Width matched to its VARCHAR2(20).
                -- THE TENANCY FENCE. Part of the key, so there is no cross-department row to serve.
                -- Read the header before changing anything about this column.
                DEPARTMENT     VARCHAR2(20)  NOT NULL,

                -- The FILING quarter as yyyyQ: 20263 is 2026 Q3. Computed in Java from CREATED_AT so
                -- the value is byte-identical on both engines; see the header.
                QUARTER_KEY    NUMBER(10)    NOT NULL,

                -- A real COMPLAINTS.GROUND_OF_COMPLAINT_ID, or 0 for "not ground-specific" — which
                -- today is every row, the source column being populated on 0 of 4,403 complaints.
                GROUND_KEY     NUMBER(19)    DEFAULT 0 NOT NULL,

                -- UPPER(TRIM(COMPLAINTS.RBIO_OFFICE_CODE)), or ''*'' for the office-agnostic window.
                -- Width matched to its VARCHAR2(10). Sentinelled for COVERAGE: the source column is
                -- populated on 410 of 4,403 rows.
                OFFICE_CODE    VARCHAR2(10)  DEFAULT ''*'' NOT NULL,

                -- The NUMERATOR: complaints in this window that are NOT closed.
                OPEN_CASES     NUMBER(19)    NOT NULL,

                -- The DENOMINATOR: complaints in this window, open or closed. Both, always — §5.1''s
                -- position is that a bare figure with no denominator will be distrusted, correctly, and
                -- ''14 open'' and ''14 of 16 open'' are different claims of which only the second is
                -- checkable by the officer reading it.
                WINDOW_TOTAL   NUMBER(19)    NOT NULL,

                -- When the refresh that wrote this row ran. Read by nothing in the request path; it
                -- exists so a rollup that quietly stopped refreshing is diagnosable, which is the one
                -- failure a scheduled job has that an endpoint does not — stale counts look exactly
                -- like correct ones. The stale sweep also keys on it.
                REFRESHED_AT   TIMESTAMP(6)  NOT NULL,

                CONSTRAINT PK_ASSISTANCE_ENTITY_PATTERN PRIMARY KEY (ID),

                -- The key IS the lookup and the idempotency. Oracle backs a UNIQUE constraint with an
                -- index automatically, so the read is an equality seek on the leading three columns
                -- with two small IN lists on the sentinelled GROUND_KEY and OFFICE_CODE — one index
                -- range, at most four rows; and the refresh upserts against this constraint rather
                -- than truncating, because a TRUNCATE plus reload would leave the rail silent for the
                -- duration of every refresh.
                --
                -- COLUMN ORDER IS THE READ''S ORDER: the three equalities lead so the IN lists are
                -- filtered from the same index range rather than driving separate seeks.
                CONSTRAINT UK_AEP_COHORT UNIQUE
                    (ENTITY_KEY, DEPARTMENT, QUARTER_KEY, GROUND_KEY, OFFICE_CODE)
            )';
    END IF;
END;
/

-- ASSISTANCE_JOB_LOCK is created by oracle V113 and is guarded here rather than redeclared differently:
-- two migrations creating one table is how the second comes to disagree with the first. This block
-- creates it only if V113 has not run, and the shape is copied from V113 verbatim.
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'ASSISTANCE_JOB_LOCK';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE ASSISTANCE_JOB_LOCK (
                LOCK_NAME    VARCHAR2(100) NOT NULL,
                LOCKED_UNTIL TIMESTAMP(6)  NOT NULL,
                LOCKED_BY    VARCHAR2(200),
                LOCKED_AT    TIMESTAMP(6),
                CONSTRAINT PK_ASSISTANCE_JOB_LOCK PRIMARY KEY (LOCK_NAME)
            )';
    END IF;
END;
/

-- A SEPARATE lock name from the other rollups', not a shared one. They read different tables, take
-- different amounts of time and fail independently; one lease would mean a slow next-action refresh
-- silently skipped this one, and the operator would see an empty table with nothing in the log to say
-- why.
-- The row must EXIST for a conditional UPDATE to be able to win it, and is dated in the past so the
-- first pod to start can take it immediately.
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-entity-pattern-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-entity-pattern-refresh'
);

COMMIT;

-- ── No new index on COMPLAINTS, and that is a decision rather than an omission ───────────────────
-- The refresh walks COMPLAINTS keyset-paged on the primary key (WHERE id > :after ORDER BY id), which
-- needs no index that does not already exist. An index on ENTITY_CODE would serve nothing here: the job
-- reads every qualifying row by design, so there is nothing to seek.
--
-- Nor is a missing index the argument for this table — on MySQL the equivalent index exists and the
-- live query plans as a seek. The argument is §5.3.5's "never computed live" and §6.2's schedule-only
-- rule. The request path reads ASSISTANCE_ENTITY_PATTERN by its unique key and touches COMPLAINTS only
-- for the one row the rail is already rendering beside.

-- ═══ WHAT THIS ROLLUP CANNOT SAY ═══
-- Measured on MySQL, because that is the only engine with populated data available. These are properties
-- of the dev DATASET rather than of the engine, so they are reported here too — but they have NOT been
-- re-measured against an Oracle instance, and that is stated rather than implied.
--
-- 1. THE GROUND DIMENSION IS ENTIRELY ABSENT — 0 of 4,403 complaints carry GROUND_OF_COMPLAINT_ID. The
--    brief's sentence says "on this ground"; this rollup can only say "across all grounds" today, and
--    the service's English says so rather than implying a filter that is not happening.
--
-- 2. THE OFFICE DIMENSION IS THIN — 410 of 4,403 rows carry RBIO_OFFICE_CODE, and 5 office-specific
--    windows clear the floor against 13 agnostic ones. The sentinel row is what makes the signal
--    answerable at all today; the specific rows are the mechanism by which it sharpens later.
--
-- 3. THE WINDOWS ARE DOMINATED BY SEEDED ENTITIES — 'TEST BANK LTD' holds 682 of the 880 open cases in
--    qualifying windows and 'TEST_BANK_001' another 64. Five windows look like real banks.
--
-- 4. THE ALIAS TABLE IS HAND-WRITTEN AND FINITE. A new abbreviation typed tomorrow is a NEW entity to
--    this rollup and will quietly SPLIT that entity's count — two windows where there should be one,
--    both possibly under the floor so the signal simply goes quiet. The cost of refusing to guess.
--
-- 5. A COMPLAINT IN A QUIET QUARTER GETS NO SIGNAL. QUARTER_KEY is the complaint's own filing quarter,
--    so a complaint filed in a quarter where its entity did not reach 5 open cases reads nothing at all
--    even if that entity is busy in the next quarter. Correct scope, and a narrower one than "this
--    entity's backlog".
--
-- 6. THE SIGNAL IS DESCRIPTIVE, NOT A JUDGEMENT. "N of M complaints against this entity are open" is
--    arithmetic about the register's own backlog — not a finding against the entity and not evidence.
--    The service's prose is phrased as a report for that reason.
