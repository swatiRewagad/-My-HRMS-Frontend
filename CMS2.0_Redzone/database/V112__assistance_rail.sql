-- V112: Assistance rail — Tier 0 per-user memory table, and the two Tier 1 prior indexes
-- MySQL version
--
-- Brief 21. The rail carries exactly two tiers: Tier 0 is per-user/per-complaint continuity (what this
-- officer was doing here last time) and Tier 1 is precomputed aggregate priors. There is no Tier 2 —
-- no inference, no embeddings, no vector column — and this migration deliberately leaves no room for
-- one. If a signal needs reasoning over case text to decide whether it is worth saying, it does not
-- belong on the rail and it does not belong in this schema.
--
-- ═══ WHY A NEW TABLE RATHER THAN REUSING STAFF_DRAFT ═══
-- STAFF_DRAFT (V99) is also per-owner and per-complaint, and the overlap is real, but it is the wrong
-- home for three reasons:
--   1. Its unique key is (MILESTONE, OWNER_USER_ID, COMPLAINT_NUMBER) — milestone-scoped by design,
--      because UST674 needs one draft per workflow milestone. The rail's memory is per SCREEN VISIT and
--      has no milestone; it would have to invent a fake one to key on.
--   2. FORM_DATA_JSON is NOT NULL and holds a serialised form. The rail records a section name and a
--      free-text fragment, so every row would carry a dummy JSON object to satisfy the constraint.
--   3. STAFF_DRAFT is resumable WORK. Deleting a draft loses an officer's typing. The rail's memory is
--      a convenience that can be dropped wholesale without harm. Mixing a disposable cache into the
--      table that holds unsaved work means neither can be pruned on its own schedule.
-- STAFF_DRAFT is left completely untouched.
--
-- ═══ COLLATION: WHY THE UNIQUE KEY IS utf8mb4_bin AND THE TABLE IS NOT ═══
-- The unique key (OWNER_USER_ID, COMPLAINT_NUMBER) is the privacy boundary for Tier 0 — it is what
-- makes it impossible for officer A to read officer B's unsaved text. Under this schema's prevailing
-- utf8mb4_0900_ai_ci, 'jdoe' and 'JDOE' compare EQUAL, so two distinct Keycloak principals would
-- collide onto ONE row and each would be served the other's draft. Oracle, which the deployed profiles
-- run, is case-sensitive by default and would NOT collide — so left alone the two engines would
-- disagree about who owns a row.
--   OWNER_USER_ID and COMPLAINT_NUMBER are therefore declared utf8mb4_bin, making the key exact here
-- and matching Oracle's behaviour. VERIFIED, not assumed: with this collation, inserting ('alice',
-- 'CMP-X') and ('ALICE','CMP-X') yields TWO distinct rows; under the ai_ci default the second insert
-- would collide onto the first.
--   Belt and braces: the application ALSO normalises both values (AssistanceRailMemory.normaliseOwner
-- lower-cases, normaliseComplaint upper-cases) on every read and write path.
--   The NORMALISATION is the primary control and this collation is the backstop, and the order matters
-- because ddl-auto:update — not this file — is what actually builds the dev-local schema. A developer
-- who drops this table and lets Hibernate recreate it gets the ai_ci default and no case-sensitive
-- key; the normalisation is then the only thing standing between officer A and officer B's unsaved
-- text. Note that an annotation CANNOT carry this collation (columnDefinition is emitted verbatim and
-- 'COLLATE utf8mb4_bin' is invalid against OracleDialect), which is why the two controls live in
-- different places rather than one.
--   The remaining columns stay on the table default — they are display text, never compared.
--
-- ═══ THE UNIQUE KEY IS ALSO THE MULTI-POD LOCK ═══
-- No advisory lock and no SELECT ... FOR UPDATE. The frontend writes this as the officer leaves a
-- screen, so two pods can process the same officer leaving two tabs at once; both find no row and both
-- INSERT, and the constraint decides. AssistanceRailService catches the loser's
-- DataIntegrityViolationException and retries as an UPDATE. That is why the key must exist in the
-- database and not merely in the entity.
--
-- ═══ THE TWO INDEXES ON COMPLAINTS ═══
-- Both are measured, not guessed. EXPLAIN output on the dev dataset (2769 rows) is recorded inline.
-- The third shipped prior (complainant history) needs NO new index: it is already a covering seek on
-- the existing idx_complaint_email.
--   Priors that were WANTED and DROPPED for want of a cheap plan are recorded at the bottom of this
-- file, so that the absence is a decision on the record rather than an oversight.
--
-- Re-running is safe. CREATE TABLE uses IF NOT EXISTS; MySQL has no CREATE INDEX IF NOT EXISTS, so
-- each index is guarded on information_schema.STATISTICS and executed through a prepared statement
-- (a plain CREATE INDEX fails with errno 1061 on the second run). These files are applied BY HAND —
-- there is no Flyway in this project, database/*.sql is documentation plus a manual-apply artifact —
-- so idempotency has to live in the file itself.

-- ── Tier 0: ASSISTANCE_RAIL_MEMORY ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ASSISTANCE_RAIL_MEMORY (
    id               BIGINT       NOT NULL AUTO_INCREMENT,

    -- Resolved from the JWT server-side and lower-cased, NEVER taken from a request payload.
    -- utf8mb4_bin so that two principals differing only in case cannot share a row.
    OWNER_USER_ID    VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,

    -- Upper-cased on write. Deliberately NOT a foreign key to COMPLAINTS: this is disposable
    -- continuity state, and an FK would make a complaint purge fail on rows that nobody needs kept.
    COMPLAINT_NUMBER VARCHAR(50)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,

    -- The tab or section the officer last had open, as the SCREEN names it. Opaque to the server:
    -- validating against an enum of known sections would make every new tab a backend release.
    LAST_SECTION     VARCHAR(100) NULL,

    -- Unsaved text left behind, truncated to 4000 chars by the application. VARCHAR and not TEXT:
    -- the rail shows a recognisable reminder that a draft exists, not the draft itself — STAFF_DRAFT
    -- is where resumable form state belongs, and duplicating it here would give an officer two places
    -- to resume from that can disagree. 4000 also fits Oracle's VARCHAR2 limit, so the twin migration
    -- needs no CLOB.
    DRAFT_TEXT       VARCHAR(4000) NULL,

    -- Server-assigned. A client-supplied timestamp would let a screen claim a visit that never
    -- happened, and "you were last here on..." is only worth showing if it is true.
    LAST_VIEWED_AT   DATETIME(6)  NOT NULL,
    UPDATED_AT       DATETIME(6)  NULL,

    PRIMARY KEY (id),

    -- The privacy boundary AND the multi-pod lock. See the header.
    UNIQUE KEY UK_ARM_OWNER_COMPLAINT (OWNER_USER_ID, COMPLAINT_NUMBER)

    -- No index on COMPLAINT_NUMBER alone, on purpose. The only read this table serves is
    -- "my memory for this complaint", which the unique key above already satisfies as a covering
    -- leading-column seek. A complaint-only index would exist solely to support a query that reads
    -- across owners — the one query Tier 0 must not have. AssistanceRailMemoryRepository declares no
    -- such finder either; the control is layered.
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── Tier 1 index: entity + clause precedent ──────────────────────────────────────────────────────
-- Serves "N complaints against this entity closed under this clause".
--
-- Measured. closure_clause already carries idx_complaint_closure_clause, and with that alone the plan
-- is  type=ref  key=idx_complaint_closure_clause  rows=461  filtered=10.00  Using where  — correct,
-- but 461 row reads to answer one count, on every staff screen load.
-- With this composite:  type=ref  key_len=606  rows=461  filtered=100.00  Extra='Using index'  — both
-- predicates sought and the index COVERS, so the 105-column row is never touched.
--
-- Column order is closure_clause FIRST and it is not arbitrary. Both predicates are equalities, so
-- either order can be sought; clause leads because entity_code is badly skewed in real data (2419 of
-- 2769 rows are one value, 'Test Bank Ltd') while closure_clause spreads across 5 values with the
-- largest at 461. Leading with the more selective column keeps the seeked slice small.
--   entity_code is NOT given its own index here. See the dropped-priors note below.
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_arm_clause_entity'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_arm_clause_entity ON COMPLAINTS (closure_clause, entity_code)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── Tier 1 index: category closure duration ──────────────────────────────────────────────────────
-- Serves "this category's median closure time is N days".
--
-- Measured:  type=range  key=idx_arm_category_closed  key_len=18  rows=3
--            Extra='Using where; Using index'  — covering, row never touched.
-- Without it the plan falls to idx_complaint_category (category_id only) and must read each row for
-- the two timestamps.
--
-- THREE columns and the order is forced by the shape of the query, not by the order they appear in it:
-- category_id is an equality so it must lead; closed_at follows because 'IS NOT NULL' is a RANGE and
-- InnoDB can seek equalities only up to the first range column; created_at is last purely so the index
-- COVERS — it is never a predicate, only a value the query selects. A trailing column that exists to
-- cover rather than to filter is the whole reason this is three columns wide and not two.
--
-- Write cost: one more B-tree maintained per INSERT and per UPDATE touching category_id or closed_at.
-- Narrow (BIGINT + two DATETIMEs) and well under a megabyte at present volume.
SET @idx_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'COMPLAINTS'
      AND INDEX_NAME   = 'idx_arm_category_closed'
);
SET @ddl = IF(@idx_exists = 0,
    'CREATE INDEX idx_arm_category_closed ON COMPLAINTS (category_id, closed_at, created_at)',
    'DO 0');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- The optimiser will not choose a brand-new index until it has statistics for it.
ANALYZE TABLE COMPLAINTS;

-- ═══ PRIORS THAT WERE WANTED AND ARE NOT HERE ═══
-- Recorded so the absence is a decision rather than an oversight. In each case a requirement and the
-- "must be cheap enough to run on every screen load" constraint collided, and the constraint won.
--
-- 1. COMPLAINANT HISTORY MATCHED ON PHONE AS WELL AS EMAIL.
--    Shipped on email only (covering seek on idx_complaint_email, 1 row examined). Phone would widen
--    recall — 2760 rows carry a phone against 2748 with a non-blank email — but complainant_phone has
--    NO index, and an OR across two columns cannot be index-sought even if both were indexed.
--    Measured: 1 row examined with email alone, 2509 (type=ALL) with the OR.
--    NOT fixed here because the fix is an index on a PII column that no other query in the codebase
--    needs, which is a decision to take deliberately rather than smuggle in under a rail feature.
--    CONSEQUENCE: a complainant who filed twice with different email addresses is reported as two
--    people. The recall gap is real and is reported, not hidden.
--
-- 2. "N COMPLAINTS AGAINST THIS ENTITY ARE STILL OPEN."
--    Dropped outright. entity_code has NO standalone index (verified: type=ALL, 2509 rows) and the
--    status half is an IN list, i.e. a range, so no seek is available on either column. An index on
--    entity_code alone would still leave the status filter unindexed, and (entity_code, status) would
--    be a fourth index on a table that already carries 20 for the benefit of one rail signal.
--    The clause-precedent prior above only works because closure_clause IS indexed and leads.
--
-- 3. ANYTHING OVER COMPLAINT TEXT — "similar wording", "reads like the batch closed last week".
--    This is the excluded Tier 2 and the honest version needs inference. A LIKE '%...%' over a TEXT
--    column is not a cheap stand-in for it: it is a full table scan that also does not work.
--
-- 4. REOPEN HISTORY AS ITS OWN SIGNAL.
--    Cheap enough (40 rows have reopen_count > 0) but dropped as redundant: it is already on the
--    complaint record the officer is looking at, so the rail would be repeating the screen.
--
-- ═══ A DATA GAP, NOT A LOGIC GAP ═══
-- Two of the three shipped priors are silent on most of the CURRENT dev data, and a reader testing the
-- rail should know that is expected:
--   * category_id is populated on only 79 of 2769 rows, so the closure-time prior cannot fire for the
--     other 2690. It is further gated at 5 closed complaints per category (six of the ten populated
--     categories have 3 or fewer), because a median of two cases carries the authority of a statistic
--     and the content of an anecdote.
--   * closure_clause is populated on 493 rows, so the precedent prior is silent on every complaint not
--     yet closed. That is correct behaviour: there is no precedent to report before a clause is chosen.
