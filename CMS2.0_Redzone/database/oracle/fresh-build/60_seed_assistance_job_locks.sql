-- ════════════════════════════════════════════════════════════════════════════════════════════════
-- 60 - ASSISTANCE_JOB_LOCK lease rows
--
-- Part of database/oracle/fresh-build/. Generated from the LIVE dev MySQL database
-- (cms_db) so that the Oracle office deployment and the MySQL dev box hold the same
-- reference data. Transactional data is excluded entirely -- no complaints, no appeals,
-- no timeline rows, and in particular none of the 2195 AIQA-% demo complaints.
--
-- PREREQUISITES
--   * Database character set AL32UTF8. Run 01_preflight_checks.sql first; it refuses to
--     continue on anything else.
--   * NLS_LANG=.AL32UTF8 exported in the client environment BEFORE sqlplus starts, or
--     every Devanagari/Tamil/Bengali character in this file lands as a question mark and
--     the load will appear to succeed.
--   * This file is UTF-8. Do not re-save it in a single-byte encoding.
--
-- IDEMPOTENCY
--   Every statement is INSERT ... SELECT ... FROM DUAL WHERE NOT EXISTS (<natural key>),
--   copied from the idiom in database/oracle/V113__assistance_next_action_rollup.sql.
--   Re-running inserts nothing and raises nothing.
--   Re-running deliberately does NOT overwrite existing rows. That is a choice: these are
--   operator-editable masters (clause wording, thresholds, office names), and a re-run of
--   the build must not silently revert a correction someone made in production. To force
--   dev values back over prod values you must write the UPDATE by hand and mean it.
--
-- SURROGATE IDs ARE NOT SEEDED. Every target table takes its ID from an Oracle IDENTITY
--   column or a sequence, and the application's entities use GenerationType.IDENTITY.
--   Carrying MySQL's ID values across would leave the identity generator's high-water mark
--   below the seeded values, and the first row the application inserted would collide on
--   the primary key. The natural key is what the application actually looks these up by.
-- ════════════════════════════════════════════════════════════════════════════════════════════════

SET DEFINE OFF
SET SQLBLANKLINES ON
WHENEVER SQLERROR EXIT FAILURE ROLLBACK


-- ----------------------------------------------------------------------------------------------
-- ASSISTANCE_JOB_LOCK  (5 lease rows)
-- *** THESE ROWS MUST EXIST OR THE ROLLUPS NEVER REFRESH, AND NOTHING REPORTS AN ERROR. ***
--
-- ASSISTANCE_JOB_LOCK is a LEASE table, not a flag table. A pod acquires a job with a
-- conditional UPDATE whose WHERE clause includes the expiry; the database picks the winner by
-- returning 1 or 0 affected rows. A conditional UPDATE can only win a row that ALREADY EXISTS.
-- So a missing lock row does not cause a crash -- it causes every pod to lose, forever. The
-- rollup never runs, the assistance rail reads an empty table, and the symptom is indistinguishable
-- from "the data has not changed". This is the quietest failure in the build, which is why these
-- rows are SQL and not left to the application.
--
-- Hazelcast clustering is deliberately disabled (so there is no leader election to fall back on)
-- and ShedLock is a dependency of cms-outbox-publisher rather than cms-backend, so this table is
-- the only thing standing between the scheduled rollups and two pods racing each other.
--
-- WHY THESE FIVE NAMES, AND NOT THE FOUR IN THE DEV DATABASE
-- The authority is entity/AssistanceJobLock.java, which declares the lock names as constants, plus
-- the service code. That gives five. The live dev MySQL database holds FOUR, and they are not a
-- subset -- the sets disagree in both directions:
--   * dev HAS 'assistance-clause-prior-refresh', which appears in NO migration and in NO Java
--     constant on either engine. It is an obsolete name, presumably renamed to
--     'assistance-clause-affinity-refresh' and never cleaned up. It is NOT seeded here.
--   * dev LACKS 'assistance-category-prior-refresh' and 'assistance-complainant-history-refresh'.
--     Both are declared in code and both are seeded by the Oracle migrations (V118 and V119) and
--     by the MySQL migrations. Their absence from dev means those two rollups have never once
--     acquired their lease ON THE DEV BOX and have therefore never run there.
-- That last point is a DEV-ENVIRONMENT defect, not an Oracle one, and it is reported rather than
-- fixed -- but it is the reason this file is keyed on the code constants instead of on a SELECT
-- from MySQL. Copying dev would have propagated the gap to Oracle.
--
-- WHY THE LEASES ARE SET TO THE EPOCH AND NOT COPIED
-- The dev rows carry a live lease: LOCKED_UNTIL in the near future, LOCKED_BY = 'unknown-host',
-- LOCKED_AT a few seconds earlier. Copying those values would ship a lock held by a machine that
-- does not exist on the office network, and every rollup would sit idle until the stale lease
-- expired. TIMESTAMP '1970-01-01 00:00:00' with a NULL holder means already-expired, so the first
-- pod to start takes the lock immediately. This matches what
-- database/oracle/V113__assistance_next_action_rollup.sql does for its own seed row.
--
-- Oracle V113/V114/V115/V118/V119 each seed their own lock row with the same NOT EXISTS guard, so
-- this file is redundant where those migrations have run and is the safety net where they have not.
-- Natural key: LOCK_NAME (it is also the primary key)
-- ----------------------------------------------------------------------------------------------
-- assistance-next-action-refresh           <- entity/AssistanceJobLock.java:53  LOCK_NAME_NEXT_ACTION_REFRESH
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-next-action-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-next-action-refresh');
-- assistance-clause-affinity-refresh       <- entity/AssistanceJobLock.java:66  LOCK_NAME_CLAUSE_AFFINITY_REFRESH
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-clause-affinity-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-clause-affinity-refresh');
-- assistance-entity-pattern-refresh        <- entity/AssistanceJobLock.java:77  LOCK_NAME_ENTITY_PATTERN_REFRESH
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-entity-pattern-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-entity-pattern-refresh');
-- assistance-category-prior-refresh        <- entity/AssistanceJobLock.java:88  LOCK_NAME_CATEGORY_PRIOR_REFRESH
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-category-prior-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-category-prior-refresh');
-- assistance-complainant-history-refresh   <- referenced in service code; no named constant
INSERT INTO ASSISTANCE_JOB_LOCK (LOCK_NAME, LOCKED_UNTIL, LOCKED_BY, LOCKED_AT)
SELECT 'assistance-complainant-history-refresh', TIMESTAMP '1970-01-01 00:00:00', NULL, NULL FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM ASSISTANCE_JOB_LOCK WHERE LOCK_NAME = 'assistance-complainant-history-refresh');

COMMIT;

-- ── Verification ────────────────────────────────────────────────────────────────────────────────
-- Expected row counts after this script (at least -- a pre-existing environment may hold more):
--   SELECT COUNT(*) FROM ASSISTANCE_JOB_LOCK;               -- >= 5

