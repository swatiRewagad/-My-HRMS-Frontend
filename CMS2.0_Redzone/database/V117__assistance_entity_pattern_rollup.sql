-- V117: Assistance rail — the entity-pattern rollup, and the lease its refresh job runs under
-- MySQL version. Oracle twin: database/oracle/V115__assistance_entity_pattern_rollup.sql
-- (the two directories' V-numbers are not in sync; this file's twin is oracle V115, not any V117 there)
--
-- Brief 21 §5.3.5: "Entity pattern alert, from a scheduled rollup only: 'this entity has N open cases
-- on this ground this quarter.' Never computed live."
--
-- TIER 1, and a COUNT. Open cases in one (entity, ground, quarter, department, office) window, with
-- that window's total beside it. No inference over case text, no model artifact, nothing about whether
-- the pattern MEANS anything. The rail reports the number and names its denominator; the officer
-- decides whether it matters.
--
-- ═══ "NEVER COMPUTED LIVE" IS THE BRIEF'S RULE, NOT A WORKAROUND FOR A MISSING INDEX ═══
-- Stated carefully, because an earlier draft of this header claimed entity_code was unindexed and that
-- was WRONG. MEASURED on cms_db: idx_complaint_entity_code EXISTS, and
--     EXPLAIN SELECT COUNT(*) FROM COMPLAINTS
--      WHERE entity_code='HDFC Bank' AND department='CEPC' AND created_at >= '2026-07-01'
-- plans as type=ref, key=idx_complaint_entity_code, rows=155 — a seek, not a scan.
--
-- So the live form of this signal is not catastrophic; it is merely FORBIDDEN. §5.3.5 says "from a
-- scheduled rollup only... Never computed live" and §6.2 says rollups are computed on a schedule and
-- never on request. This is the one item in §5.3 where the brief says so twice, and the reason is not
-- the plan of one query: it is that the rail renders on EVERY staff screen load, so an aggregate here
-- is an aggregate on the critical path of every page in the product, multiplied by however many
-- signals later get added beside it. The rollup turns that into a unique-key seek on a table of tens
-- of rows.
--
-- The honest consequence of the index existing is that this table is a RULE-FOLLOWING choice rather
-- than a forced one, and a reviewer who finds the live query acceptable is disagreeing with the brief
-- and not with this file.
--
-- ═══ DEPARTMENT IS A TENANCY FENCE AND IS PART OF THE KEY ═══
-- This is the one thing about this table that must not be "simplified" away, and it is a disclosure
-- control rather than a modelling preference. §5.3.5 is a CROSS-COMPLAINT disclosure: it tells an
-- officer about OTHER complainants' live cases against a named entity. Brief 21 §4 requires the
-- restrictive default until a human rules otherwise, so the department is part of the KEY and not a
-- column a read may choose to ignore:
--
--   * There is NO row aggregated across departments. A cross-department total is not stored, so no
--     read can accidentally serve one — the restriction is a property of the SCHEMA, not of whichever
--     query happens to get written against it.
--   * AssistanceRailService additionally refuses to read a department other than the one on the
--     complaint in front of the officer, so the fence holds on both sides. Removing either half is a
--     disclosure change and needs the ruling recorded in the findings as open ask 3.
--
-- MEASURED on the 4,112 eligible rows: CEPC 2,831 (663 open) / RBIO 1,223 (247 open) / CRPC 58 (17
-- open). So the fence is not theoretical — it keeps 2,831 CEPC cases out of an RBIO officer's entity
-- counts and vice versa.
--
-- A future ruling permitting cross-department visibility would add a SENTINEL department row the way
-- OFFICE_CODE already carries one. It must NOT be implemented by dropping the predicate.
--
-- ═══ NOTHING COMPLAINANT-IDENTIFYING CAN REACH THIS TABLE ═══
-- Verifiable by inspection of the column list below: five keys, two counts, one timestamp. No
-- complainant name, phone, email, address, account or card number; no complaint number; no free text
-- of any kind. A row cannot identify anyone even if the whole table were dumped. That is deliberate
-- and it is the reason this prior is shippable at all while §5.3.5's disclosure question is still
-- open — the payload is a count and a denominator about a REGULATED ENTITY, which is not personal
-- data.
--
-- ═══ ENTITY_KEY IS NORMALISED ON THE WRITE SIDE, AND A CASE FOLD WOULD NOT HAVE DONE IT ═══
-- The brief flags entity_code as dirty and it is worse than "mixed case". MEASURED on cms_db: 33
-- distinct raw values over 4,113 non-blank rows, and UPPER(TRIM(...)) collapses those 33 to... 33.
-- There are NO case collisions in this register at all, so a case fold buys exactly nothing.
--
-- What actually costs recall is SYNONYMY, which case folding cannot touch:
--     HDFC / HDFC Bank                       ICICI / ICICI Bank
--     AXIS / Axis Bank                       CANARA / Canara Bank
--     PNB / Punjab National Bank             SBI / State Bank of India
--     BOB / Bank of Baroda                   UNION / Union Bank / Union Bank of India
--     INDIAN (Indian Bank)                   KOTAK (Kotak Mahindra Bank)
-- 'PNB' and 'Punjab National Bank' share no character position, so no folding function merges them.
-- The normalisation is therefore an explicit ALIAS TABLE applied on the WRITE side, in Java, where it
-- can be read and unit-tested — see AssistanceEntityAliasNormaliser. MEASURED EFFECT: 33 distinct raw
-- values become 24 normalised keys.
--   ENTITY_KEY holds the NORMALISED form, and the read normalises the complaint's own entity_code
-- through the SAME function before seeking. That is the only way the two sides can agree: a rollup
-- keyed on raw text would hold 'PNB' and 'Punjab National Bank' as two entities and report half the
-- count to each, which is a WRONG number rather than a missing one.
--
-- On MySQL the write-side normalisation is partly redundant — utf8mb4_0900_ai_ci folds case for free,
-- so 'HDFC Bank' and 'HDFC BANK' would be one key anyway. On ORACLE the default collation is
-- case-SENSITIVE and would hold them apart, so normalising before storing is what makes the two
-- engines produce identical rows. Doing it in Java rather than in each dialect's SQL is why there is
-- one implementation to test instead of two to keep in step.
--
-- NOT a global fix to entity_code, and the distinction is the brief's own: "Do not 'fix' entity_code
-- globally — that is a data-migration project owned elsewhere. Handle the dirt where you read it, and
-- report it." Nothing here UPDATEs COMPLAINTS. The alias table is hand-written, finite and
-- hand-checked; it is deliberately NOT fuzzy, because a similarity match merges two different banks on
-- a bad day and a wrong count presented as a pattern is worse than no signal at all.
--
-- ═══ GROUND_KEY IS A SENTINEL, AND TODAY IT IS THE ONLY VALUE ═══
-- The brief's prescribed dimension is "on this ground". MEASURED: ground_of_complaint_id is populated
-- on 0 of 4,403 complaints. ZERO. The column exists (V36/V34 declare the master) and nothing writes
-- it, so that dimension is not merely sparse — it is EMPTY, and keying on it as REQUIRED would ship an
-- empty table.
--   0 therefore means "this window is not ground-specific". Every complaint counts into the agnostic
-- row AND into its ground row where it has one (today: never), and the read PREFERS the specific row.
-- The day grounds start being written the signal sharpens with no schema and no code change, and until
-- then the rail's sentence says "across all grounds" rather than implying a ground filter that is not
-- happening.
--
-- 0 AND NOT NULL, deliberately: a composite UNIQUE key containing a NULL prevents no duplicates on
-- MySQL and none on Oracle either, so a NULL ground would make this key non-idempotent and every
-- refresh would APPEND a fresh set of agnostic rows instead of updating them. Verified no real
-- ground id is 0 (the master's id is AUTO_INCREMENT from 1).
--
-- ═══ QUARTER_KEY IS THE COMPLAINT'S OWN FILING QUARTER, WHICH IS WHY THERE IS NO CLIFF ═══
-- The brief says "this quarter" and this column implements exactly that: yyyyQ, 20263 being 2026 Q3,
-- computed from created_at — when the complaint was FILED.
--
-- The obvious objection to a calendar quarter is the CLIFF: at 00:00 on 1 July a "current quarter"
-- count resets, and an entity with 500 live cases is reported as having 2. That objection does not
-- apply here, because the read does not ask for the CURRENT quarter — it asks for the quarter of the
-- complaint the officer is looking at. A complaint filed on 30 June goes on reading its own Q2 window
-- for as long as it exists. The number an officer sees is the cohort that complaint actually belongs
-- to, and it never changes under them.
--   That is also why there is no WINDOW_DAYS column. A rolling window would be a SECOND, different
-- window concept layered on a key that already has one, and it would have to be stored and then
-- restated in the rail's prose to avoid printing a window the count was not computed over. The
-- quarter is in the key, so the sentence can name it from the row itself.
--
-- Computed in JAVA, not by the database: QUARTER() and TO_CHAR are spelled differently on MySQL and
-- Oracle, and this value has to be byte-identical on both or the two engines hold different keys.
--
-- MEASURED distribution of the eligible rows: 20261=26, 20263=2,442, 20264=1,644 (and no 20262 rows
-- carry an entity). So the register spans three quarters and the two recent ones hold essentially all
-- of it.
--
-- ═══ OFFICE_CODE IS SENTINELLED FOR COVERAGE, WHERE DEPARTMENT IS KEYED FOR CORRECTNESS ═══
-- rbio_office_code is populated on 410 of 4,403 rows (9%), so keying on it as REQUIRED would make this
-- feature silent on 91% of the register. BOTH rows are therefore written — the office-agnostic one
-- always, and an office-specific one where that office clears the floor on its own — and the read
-- prefers the specific one. '*' rather than '' so a reader of the table can tell "agnostic" from
-- "blank string that got in by accident"; the two would otherwise be indistinguishable and one of them
-- is a bug. MEASURED: no real office code is '*' (the values are 011, 013, 014, 016, 017, 021, C01).
--
-- MEASURED at the floor: 13 agnostic windows qualify against 5 office-specific ones.
--
-- ═══ "OPEN" COMES FROM RBIO_STATUS_MASTER, NOT FROM A LITERAL LIST IN THIS FILE ═══
-- There were previously TWO hardcoded copies of the closed-status vocabulary in this codebase and they
-- DISAGREED — one held six values and the other four, omitting 'adjudicated' and 'conciliated', so a
-- complaint closed by an award counted as open to one of them. The refresh therefore resolves "closed"
-- through RbioStatusVocabulary, which reads RBIO_STATUS_MASTER.IS_CLOSED='Y', and "open" is its
-- complement.
--   MEASURED on cms_db: eight statuses are closed — closed, resolved, rejected, withdrawn,
-- adjudicated, conciliated, forwarded_external, forwarded_regulator — leaving 927 of the 4,112
-- eligible rows open (1,186 of all 4,403).
--   THE DISCARDED ALTERNATIVE, recorded rather than dropped: a literal
-- LOWER(status) NOT IN ('closed','withdrawn','rejected') gives 967 open among the eligible rows against
-- the vocabulary's 927. The 40-row difference is complaints closed by adjudication, conciliation or a
-- forward to a regulator, which that shorter list would have counted as LIVE cases against the entity —
-- i.e. it would over-report an entity's open backlog by 4%. The table-driven vocabulary is the correct
-- one and it is also the one an operator can correct without a deploy.
--   Note the vocabulary is resolved in JAVA, not in SQL: COMPLAINTS.status holds LOWERCASE values
-- while the ComplaintStatus enum is UPPERCASE, and a predicate written in the enum's spelling matches
-- nothing on MySQL's ai_ci collation except by accident and matches nothing AT ALL on Oracle. Keeping
-- the comparison in Java makes the two engines agree by construction.
--
-- ═══ THE k>=5 FLOOR, AND WHAT IT COSTS ═══
-- A window is stored only if it holds at least 5 OPEN cases. The same floor as
-- AssistanceRailService.MIN_CLOSURE_SAMPLE and AssistanceNextActionRefreshService.MIN_COHORT_SAMPLE,
-- for the same reason: "this entity has 2 open cases" is not a pattern, and an officer cannot tell a
-- pattern from an anecdote from the rail.
--   MEASURED COST, said plainly rather than reported as coverage. Of 43 (entity, department, quarter)
-- windows holding at least one open case, 13 clear the floor; they cover 880 open cases out of 3,993
-- complaints, plus 5 office-specific windows covering 64 of 131. So the whole table is EIGHTEEN ROWS
-- on this data.
--   And the distribution is an artifact of seeding: 'TEST BANK LTD' alone accounts for 682 of the 880
-- across five windows, and 'TEST_BANK_001' for another 64. The genuinely real-looking windows are
-- HDFC BANK/CEPC (54 of 110 in Q4, 35 of 43 in Q3), STATE BANK OF INDIA/RBIO (16 of 16, 10 of 13),
-- ICICI BANK/RBIO (7 of 8), AIRTEL PAYMENTS BANK LIMITED/RBIO (7 of 7) and PUNJAB NATIONAL BANK/RBIO
-- (5 of 5). The floor is enforced but has NOT been exercised against a realistic distribution of
-- entities.
--
-- Re-running is safe, and that is a requirement rather than a nicety — these files are applied BY HAND
-- (there is no Flyway here), so idempotency has to live in the file. CREATE TABLE IF NOT EXISTS, the
-- composite UNIQUE declared INLINE (MySQL has no CREATE INDEX IF NOT EXISTS and a plain CREATE INDEX
-- fails with errno 1061 on the second run), and the lock row seeded on its own absence.
--
-- NOT APPLIED to any environment as shipped — verified absent from cms_db at the time of writing. The
-- rail reads this through a guarded signal and reports nothing when the table is absent or empty, so
-- an unapplied migration costs the rail exactly one signal.

-- ── The rollup ───────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ASSISTANCE_ENTITY_PATTERN (
    ID             BIGINT       NOT NULL AUTO_INCREMENT,

    -- The NORMALISED entity: upper-cased, trimmed and alias-resolved. Width matched to
    -- COMPLAINTS.entity_code's own varchar(50) so a value cannot be silently truncated on the way in —
    -- normalisation maps an alias to its CANONICAL name, and 'PUNJAB NATIONAL BANK' is longer than the
    -- 'PNB' it came from.
    ENTITY_KEY     VARCHAR(50)  NOT NULL,

    -- A real COMPLAINTS.ground_of_complaint_id, or 0 for "not ground-specific" — which today is every
    -- row, the source column being populated on 0 of 4,403 complaints. See the header.
    GROUND_KEY     BIGINT       NOT NULL DEFAULT 0,

    -- The FILING quarter as yyyyQ: 20263 is 2026 Q3. Computed in Java from created_at, not by the
    -- database, so the value is byte-identical on MySQL and Oracle. See the header for why keying on
    -- the complaint's OWN quarter is what removes the calendar cliff.
    QUARTER_KEY    INT          NOT NULL,

    -- UPPER(TRIM(COMPLAINTS.department)). Width matched to its varchar(20).
    -- THE TENANCY FENCE. Part of the key, so there is no cross-department row to serve. Read the
    -- header before changing anything about this column.
    DEPARTMENT     VARCHAR(20)  NOT NULL,

    -- UPPER(TRIM(COMPLAINTS.rbio_office_code)), or '*' for the office-agnostic window. Width matched
    -- to its varchar(10). Sentinelled for COVERAGE: the source column is populated on 410 of 4,403
    -- rows, so requiring it would silence the feature on 91% of the register.
    OFFICE_CODE    VARCHAR(10)  NOT NULL DEFAULT '*',

    -- The NUMERATOR: complaints in this window that are NOT closed. The number the signal is about.
    OPEN_CASES     BIGINT       NOT NULL,

    -- The DENOMINATOR: complaints in this window, open or closed. Both, always — §5.1's position is
    -- that "a bare recommendation with no denominator will be distrusted, correctly", and "14 open"
    -- and "14 of 16 open" are different claims of which only the second is checkable by the officer
    -- reading it. The schema does not permit storing one without the other.
    WINDOW_TOTAL   BIGINT       NOT NULL,

    -- When the refresh that wrote this row ran. Read by NOTHING in the request path, and load-bearing
    -- for two things that are not the request: the stale sweep keys on it, and a rollup which quietly
    -- stopped refreshing is otherwise undiagnosable — stale counts look exactly like correct counts.
    REFRESHED_AT   DATETIME(6)  NOT NULL,

    PRIMARY KEY (ID),

    -- The key IS the lookup and the idempotency. The read is an equality seek on ENTITY_KEY,
    -- DEPARTMENT and QUARTER_KEY with two small IN lists on the sentinelled GROUND_KEY and
    -- OFFICE_CODE — one index range, at most four rows — and the refresh UPSERTS against this
    -- constraint rather than truncating. A TRUNCATE plus reload would leave the rail silent for the
    -- duration of every refresh, a self-inflicted outage on a feature whose contract is to be there
    -- when the screen loads.
    --
    -- COLUMN ORDER IS THE READ'S ORDER, not the key's conceptual order: the three equalities lead so
    -- the IN lists are filtered from the same index range rather than driving separate seeks.
    UNIQUE KEY UK_AEP_COHORT (ENTITY_KEY, DEPARTMENT, QUARTER_KEY, GROUND_KEY, OFFICE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── The refresh lease ────────────────────────────────────────────────────────────────────────────
-- ASSISTANCE_JOB_LOCK is created by V115 (and oracle V113) and is guarded here rather than redeclared
-- differently: two migrations creating one table is how the second comes to disagree with the first.
-- The shape below is copied from V115 verbatim and the CREATE only fires if V115 has not run.
CREATE TABLE IF NOT EXISTS ASSISTANCE_JOB_LOCK (
    LOCK_NAME    VARCHAR(100) NOT NULL,
    LOCKED_UNTIL DATETIME(6)  NOT NULL,
    LOCKED_BY    VARCHAR(200) NULL,
    LOCKED_AT    DATETIME(6)  NULL,
    PRIMARY KEY (LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A SEPARATE lock name, not a shared one. The rollups read different tables, take different amounts of
-- time and fail independently; one lease would mean a slow next-action refresh silently SKIPPED this
-- one, and the operator would see an empty table with nothing in the log to say why.
--
-- The row must EXIST for a conditional UPDATE to be able to win it, and is dated in the past so the
-- first pod to start can take it immediately. Seeding it here rather than letting the job
-- insert-on-miss keeps the acquire path a single statement with no race of its own.
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-entity-pattern-refresh', TIMESTAMP('1970-01-01 00:00:00'), NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-entity-pattern-refresh'
);

-- ── No new index on COMPLAINTS, and that is a decision rather than an omission ───────────────────
-- The refresh walks COMPLAINTS keyset-paged on the PRIMARY KEY (WHERE id > :after ORDER BY id), which
-- needs no index that does not already exist. An index on entity_code would serve NOTHING here: the
-- job reads every qualifying row by design, so there is nothing for it to seek.
--
-- idx_complaint_entity_code DOES already exist, which is why the header above does not use "no index"
-- as the justification for this table. The justification is §5.3.5's "never computed live" and §6.2's
-- schedule-only rule. The request path reads ASSISTANCE_ENTITY_PATTERN by its unique key and touches
-- COMPLAINTS only for the one row the rail is already rendering beside.
ANALYZE TABLE ASSISTANCE_ENTITY_PATTERN;

-- ═══ WHAT THIS ROLLUP CANNOT SAY, MEASURED ON cms_db ═══
-- Recorded here because a rail that is silent for a defensible reason is indistinguishable from one
-- that is broken, and the next reader will test it against this database.
--
-- 1. THE GROUND DIMENSION IS ENTIRELY ABSENT — 0 of 4,403 complaints carry ground_of_complaint_id. The
--    brief's sentence says "on this ground"; this rollup can only say "across all grounds" today, and
--    the service's English says so rather than implying a ground filter that is not happening. This is
--    a stated deviation from a verbatim requirement, not a silent one.
--
-- 2. THE OFFICE DIMENSION IS THIN — 410 of 4,403 rows carry rbio_office_code, and 5 office-specific
--    windows clear the floor against 13 agnostic ones. The sentinel row is what makes the signal
--    answerable at all today; the specific rows are the mechanism by which it sharpens later.
--
-- 3. THE WINDOWS ARE DOMINATED BY SEEDED ENTITIES — 'TEST BANK LTD' holds 682 of the 880 open cases in
--    qualifying windows and 'TEST_BANK_001' another 64. Five windows look like real banks.
--
-- 4. THE ALIAS TABLE IS HAND-WRITTEN AND FINITE. A new abbreviation typed into a complaint tomorrow is
--    a NEW entity to this rollup and will quietly SPLIT that entity's count — two windows where there
--    should be one, each with a smaller numerator, both possibly falling under the floor so the signal
--    just goes quiet. That is the cost of refusing to guess. It is the right trade, and it is a cost.
--
-- 5. A COMPLAINT IN A QUIET QUARTER GETS NO SIGNAL. Because QUARTER_KEY is the complaint's own filing
--    quarter, a complaint filed in a quarter where its entity did not reach 5 open cases reads nothing
--    at all — even if that entity has hundreds of open cases in the NEXT quarter. 20261 holds 26
--    eligible rows and no window in it clears the floor. The signal is about the cohort the complaint
--    belongs to, which is the correct scope and also a narrower one than "this entity's backlog".
--
-- 6. THE SIGNAL IS DESCRIPTIVE, NOT A JUDGEMENT. "N of M complaints against this entity are open" is
--    an arithmetic fact about the register's own backlog. It is not a finding against the entity, it is
--    not evidence, and the service's prose is phrased as a report for exactly that reason — an officer
--    reading it as a pre-judgement of the complaint in front of them would be reading something the
--    data does not say.
