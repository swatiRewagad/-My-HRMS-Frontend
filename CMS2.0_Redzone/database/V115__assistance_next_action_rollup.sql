-- V115: Assistance rail — the next-action rollup, and the lease lock the refresh job runs under
-- MySQL version. Oracle twin: database/oracle/V113__assistance_next_action_rollup.sql
--
-- Brief 21 §5.3.1: "From immutable COMPLAINT_TIMELINE, aggregate P(action | from_status,
-- performed_by_role, category) into a rollup table refreshed on a schedule. The rail suggests the most
-- likely action with its denominator."
--
-- This is still TIER 1. It is a count, not a prediction in the modelling sense: the rail reports what
-- the register already did in this situation, with the denominator attached. There is no inference over
-- case text and no model artifact. §6.2's governing rule — "rollups are computed on a schedule, never
-- on request" — is why a table exists at all rather than a GROUP BY behind the endpoint.
--
-- ═══ WHY THE KEY IS NOT THE THREE COLUMNS THE BRIEF NAMES ═══
-- The brief's key is (from_status, performed_by_role, category). MEASURED against cms_db, that key is
-- almost empty:
--   * 16,989 timeline rows join to a complaint; only 722 of those complaints carry a category_id at all.
--   * Cohorts of (from_status, role, category) with >= 5 samples: 5.
--   * Cohorts of (from_status, role) with >= 5 samples: 34, covering 7,528 rows.
-- Keying on all three as REQUIRED would therefore ship a rollup that answers almost nothing, and the
-- rail would look broken when it was merely starved. Keying on two would throw away the category
-- precision the brief asked for on the rows that do have it.
--   So CATEGORY_KEY is part of the key but carries a SENTINEL: 0 means "this cohort is not
-- category-specific". The refresh writes both rows — one per real category that clears the floor, and
-- one category-agnostic row per (from_status, role) — and the read prefers the specific row, falling
-- back to the sentinel. Precision where the data supports it, coverage everywhere else, and the
-- fallback is a stored row rather than a second query shape.
--
-- 0 AND NOT NULL, deliberately. A NULL category would be the natural spelling, but NULL is not
-- comparable in a UNIQUE key: MySQL permits unlimited NULL-bearing duplicates, so the key would stop
-- being idempotent and every refresh would append a fresh set of agnostic rows. Oracle behaves the same
-- way for a composite containing NULL. The sentinel keeps one row per cohort on both engines. No real
-- category_id is 0 (verified: the column is AUTO_INCREMENT, minimum 1).
--
-- ═══ ONE ROW PER COHORT, NOT ONE PER ACTION ═══
-- Only the WINNING action is stored, with OCCURRENCES and COHORT_TOTAL beside it. §5.3's rule is that
-- every prior must be "answerable by a single keyed lookup", and the rail shows one suggestion with its
-- denominator — so the full distribution would be rows nothing reads, and would turn the lookup into a
-- sort. The denominator is preserved because the brief is explicit that "a bare recommendation with no
-- denominator will be distrusted, correctly": COHORT_TOTAL is what makes "84% of 217" sayable.
--
-- ═══ THE LOCK TABLE, AND WHY ONE HAD TO BE WRITTEN ═══
-- §6.2: in a multi-pod deployment exactly one pod may run a rollup, and Hazelcast clustering is
-- deliberately disabled so it cannot do leader election. ShedLock is NOT available here either — it is
-- a dependency of cms-outbox-publisher only, absent from cms-backend/pom.xml (and the SHEDLOCK table in
-- the Oracle DDL belongs to that other service). Both existing scheduled jobs in cms-backend say so in
-- their own comments and simply accept duplicate work.
--   So ASSISTANCE_JOB_LOCK is a LEASE, acquired by a conditional UPDATE whose WHERE clause includes the
-- expiry. The database decides the winner by returning 1 or 0 affected rows; no SELECT ... FOR UPDATE
-- and no advisory lock. A lease rather than a boolean because a pod killed mid-refresh would otherwise
-- hold a flag forever and silently stop the rollup refreshing — the failure that looks exactly like
-- "the data has not changed".
--
-- Re-running is safe. CREATE TABLE uses IF NOT EXISTS; MySQL has no CREATE INDEX IF NOT EXISTS, so the
-- index is guarded on information_schema.STATISTICS and run through a prepared statement (a plain
-- CREATE INDEX fails with errno 1061 on the second run). These files are applied BY HAND — there is no
-- Flyway in this project — so idempotency has to live in the file itself.
--
-- NOT APPLIED to cms_db as shipped. The rail reads this table through a guarded signal and reports
-- nothing when the table is empty or absent, so an unapplied migration degrades the rail by exactly one
-- signal rather than breaking it.

-- ── The rollup ───────────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ASSISTANCE_NEXT_ACTION (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,

    -- The three key columns, widths matched to COMPLAINT_TIMELINE's own so a value can never be
    -- silently truncated on the way into the rollup: action varchar(50), from_status varchar(30),
    -- performed_by_role varchar(50).
    FROM_STATUS        VARCHAR(30) NOT NULL,
    PERFORMED_BY_ROLE  VARCHAR(50) NOT NULL,

    -- 0 = not category-specific. See the header for why this is a sentinel and not NULL.
    CATEGORY_KEY       BIGINT      NOT NULL DEFAULT 0,

    -- The action that most often followed. Stored as the raw timeline value (ACCEPT,
    -- SUBMIT_FOR_REVIEW) because the client needs a machine key to match against the action bar's
    -- own ids; the rail's prose is built from the i18n bundle, not from this.
    ACTION             VARCHAR(50) NOT NULL,

    -- The numerator and the denominator. Both, always: a suggestion without its denominator is the
    -- failure mode §5.1 names, so the schema does not permit storing one without the other.
    OCCURRENCES        BIGINT      NOT NULL,
    COHORT_TOTAL       BIGINT      NOT NULL,

    -- When the refresh that wrote this row ran. Read by nothing in the request path — it exists so
    -- that a rollup which quietly stopped refreshing is diagnosable, which is the one failure a
    -- scheduled job has that an endpoint does not.
    REFRESHED_AT       DATETIME(6) NOT NULL,

    PRIMARY KEY (id),

    -- The key IS the lookup and the idempotency. The read is one equality seek on all three columns,
    -- and the refresh upserts against this constraint rather than truncating — a TRUNCATE plus reload
    -- would leave the rail silent for the duration of every refresh.
    UNIQUE KEY UK_ANA_COHORT (FROM_STATUS, PERFORMED_BY_ROLE, CATEGORY_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── The refresh lease ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ASSISTANCE_JOB_LOCK (
    LOCK_NAME    VARCHAR(100) NOT NULL,

    -- The lease expiry. A conditional UPDATE ... WHERE LOCKED_UNTIL <= now is the whole mechanism:
    -- InnoDB serialises the two pods' updates on the row and the loser is told it changed 0 rows.
    LOCKED_UNTIL DATETIME(6)  NOT NULL,

    -- Diagnostics only, never a predicate. Who held it last and from when, so a lease that keeps
    -- expiring mid-run names the pod it was expiring under.
    LOCKED_BY    VARCHAR(200) NULL,
    LOCKED_AT    DATETIME(6)  NULL,

    PRIMARY KEY (LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The row must EXIST for a conditional UPDATE to be able to win it. Seeding it here rather than
-- letting the job insert-on-miss keeps the acquire path a single statement with no race of its own —
-- two pods racing an INSERT would both have to handle the constraint violation, which is the more
-- complicated half of the problem the lease exists to avoid.
-- Dated in the past so the first pod to start can take it immediately.
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-next-action-refresh', TIMESTAMP('1970-01-01 00:00:00'), NULL, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-next-action-refresh'
);

-- ── No new index on COMPLAINT_TIMELINE, and that is a measured result ────────────────────────────
-- The refresh scans the timeline keyset-paged on the PRIMARY KEY (WHERE id > :after ORDER BY id),
-- which needs no index that does not already exist, and resolves each row's category by primary-key
-- lookup on COMPLAINTS. A covering index on (from_status, performed_by_role, action) was considered
-- and rejected: the job reads EVERY qualifying row by design, so there is nothing for it to seek, and
-- the index would be a fourth B-tree maintained on every workflow transition for the benefit of one
-- job that runs hourly.
--
-- The REQUEST path touches this table not at all. It reads ASSISTANCE_NEXT_ACTION by its unique key,
-- which is the point of precomputing.
ANALYZE TABLE ASSISTANCE_NEXT_ACTION;

-- ═══ WHAT THIS ROLLUP CANNOT SAY, MEASURED ═══
-- Recorded here because a rail that is silent for a defensible reason is indistinguishable from one
-- that is broken, and the next reader will test it against this database.
--
-- 1. performed_by_role IS POPULATED ON 43% OF THE TIMELINE — 7,531 of 17,433 rows. Every row without
--    it is invisible to this rollup, because the role is half the key. The column was added later than
--    the table and historical rows have no recoverable answer; it is not backfillable from
--    performed_by, which holds free text and "SYSTEM"/"System"/"system" interchangeably.
--
-- 2. CATEGORY PRECISION IS EFFECTIVELY ABSENT TODAY — 722 of 16,989 joinable rows. Hence the sentinel.
--    As category_id gets populated the specific rows start clearing the floor and the read begins
--    preferring them, with no schema change and no code change.
--
-- 3. THE WINNERS ARE LOPSIDED, WHICH IS A PROPERTY OF SEEDED DATA. Of the 34 cohorts clearing a
--    5-sample floor, 33 have a winner above 50% and 29 above 60%; several sit at 100% because the
--    seeder only ever emitted one action from that state. A confidence floor is still enforced in the
--    service — the brief requires one — but on THIS data it rejects almost nothing, so it has not been
--    exercised against a realistic distribution. Said plainly rather than reported as a passing test.
--
-- 4. THE ROLLUP IS ADVISORY AND THE SERVICE MUST KEEP IT THAT WAY. It is mined from what happened, not
--    from what is permitted: the from-status rules in the RBIO machine are advertisement only, and this
--    table would happily report a historically-common action that the workflow would now refuse. The
--    brief's "suggest, highlight, do not auto-select" is therefore a correctness requirement here, not
--    a UX preference — workflow-action-bar commits real transitions.
