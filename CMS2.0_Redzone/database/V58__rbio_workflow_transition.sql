-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V58 — RBIO_MILESTONE_MASTER + RBIO_WORKFLOW_TRANSITION + COMPLAINTS.milestone (Wave 0 foundation)
--
-- Re-runnable, guarded through information_schema. Procedure prefix w0b_.
--
-- WHY: RBIO action dispatch is two hardcoded switch statements (performAction cases, executeAction
-- cases) plus a THIRD switch (isActionValidForState) that answers the same question for the read-only
-- "what can I do" endpoint. Three switches over one vocabulary means the UI can offer an action the
-- server refuses, and refuse one the server allows — the exact disagreement AA had before its
-- transition table. This table makes the disagreement unrepresentable: enforcement and advertisement
-- both resolve from these rows.
--
-- Rows are seeded by RbioWorkflowTransitionSeeder (@Order(21)), NOT by this file. That is deliberate
-- and it is the load-bearing decision of this migration:
--   * There is no Flyway here. database/*.sql is hand-run, so a DB where V58 was never applied is a
--     normal occurrence. If the ROWS lived in SQL, an unseeded database would leave the RBIO workflow
--     with an EMPTY transition table — every action refused, the module completely dead, where today
--     the switch always works. A Java seeder runs on every boot and cannot be forgotten.
--   * Sessions S1-S7 add actions as ROWS. If rows lived here they would all edit one .sql file and
--     silently lose each other's work. Each session instead ships its own seeder class at its own
--     @Order, matching the established one-seeder-per-session convention.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS w0b_add_index;
DROP PROCEDURE IF EXISTS w0b_add_column;
DELIMITER //
CREATE PROCEDURE w0b_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
CREATE PROCEDURE w0b_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. RBIO_MILESTONE_MASTER — the five coarse phases
--
-- A milestone is the phase a citizen is told about ("your complaint is in Conciliation"); a status is
-- the fine-grained internal state. Seeded HERE, not in Java: unlike transitions, the five milestones
-- are a closed set that no session extends, so there is no concurrent-edit hazard and no benefit to
-- deferring them.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_MILESTONE_MASTER (
    MILESTONE_CODE  VARCHAR(30)  NOT NULL,
    LABEL_EN        VARCHAR(150) NOT NULL,
    TRANSLATION_KEY VARCHAR(150),
    DISPLAY_ORDER   INT          NOT NULL DEFAULT 999,
    IS_ACTIVE       CHAR(1)      NOT NULL DEFAULT 'Y',
    PRIMARY KEY (MILESTONE_CODE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO RBIO_MILESTONE_MASTER (MILESTONE_CODE, LABEL_EN, TRANSLATION_KEY, DISPLAY_ORDER, IS_ACTIVE)
SELECT * FROM (
    SELECT 'REGISTER'       AS c, 'Register'       AS l, 'rbio.milestone.register'       AS k, 1 AS o, 'Y' AS a
    UNION ALL SELECT 'ASSESSMENT',     'Assessment',     'rbio.milestone.assessment',     2, 'Y'
    UNION ALL SELECT 'CONCILIATION',   'Conciliation',   'rbio.milestone.conciliation',   3, 'Y'
    UNION ALL SELECT 'FORWARD',        'Forward',        'rbio.milestone.forward',        4, 'Y'
    UNION ALL SELECT 'FINAL_DECISION', 'Final Decision', 'rbio.milestone.final_decision', 5, 'Y'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM RBIO_MILESTONE_MASTER m WHERE m.MILESTONE_CODE = seed.c
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. RBIO_WORKFLOW_TRANSITION — THE transition table
--
-- FROM_STATUS is the LEGACY lowercase value (COMPLAINTS.status as actually written), not a
-- RBIO_STATUS_MASTER code. The table has to match live rows to be enforceable, and live rows hold the
-- legacy vocabulary. NULL FROM_STATUS = "from any status" (needed by REASSIGN/CLOSE_COMPLAINT, which
-- today are guarded by a NOT-IN list rather than an enumeration of sources).
--
-- NULL TO_STATUS means "status unchanged" — a real case, e.g. SCHEDULE_MEETING and ISSUE_NOTICE_13_1
-- move the stage and set a timestamp but deliberately leave status alone. NULL must therefore mean
-- unchanged, NOT "not configured", or those actions would wipe the status.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_WORKFLOW_TRANSITION (
    ID               BIGINT      NOT NULL AUTO_INCREMENT,

    ACTION_CODE      VARCHAR(50) NOT NULL,
    ROLE_NAME        VARCHAR(50) NOT NULL,
    FROM_STATUS      VARCHAR(30),               -- NULL = any
    TO_STATUS        VARCHAR(30),               -- NULL = unchanged
    TO_STAGE         VARCHAR(50),               -- COMPLAINTS.workflow_stage
    TO_MILESTONE     VARCHAR(30),
    ASSIGN_TO_ROLE   VARCHAR(50),               -- literal role, or the sentinel below
    -- '__NEXT__' = resolve through the rank ladder at runtime rather than naming a role here.
    -- ESCALATE/APPROVE are relative moves ("up one"), so a literal would need one row per source rank
    -- and would silently stop working when a rank is inserted into the ladder.
    ASSIGN_STRATEGY  VARCHAR(20),               -- NULL | ROUND_ROBIN | TARGET_PARAM | KEEP
    REQUIRES_COMMENT CHAR(1)     NOT NULL DEFAULT 'N',
    -- Comma-separated param names that must be present and non-blank. This is how the award-amount
    -- refusal survives the switch removal: ADJUDICATION_AWARD declares awardAmount|compensationAmount
    -- as required, so a missing amount is refused by the table instead of defaulting to a zero award.
    REQUIRED_PARAMS  VARCHAR(255),
    IS_TERMINAL      CHAR(1)     NOT NULL DEFAULT 'N',
    -- Named Java hook for effects a table cannot express: cap validation, monetary parsing, list
    -- append. Absent = no side effect beyond the column writes above.
    SIDE_EFFECT      VARCHAR(50),
    SLA_STAGE        VARCHAR(50),               -- arg to RbioSlaService.applyStageSla
    CLOSURE_CAUSE    VARCHAR(50),
    DISPLAY_ORDER    INT         NOT NULL DEFAULT 999,
    IS_ACTIVE        CHAR(1)     NOT NULL DEFAULT 'Y',
    SCHEME_VERSION   VARCHAR(20) NOT NULL DEFAULT 'RBIOS_2021',
    OWNED_BY         VARCHAR(20),               -- seeding session, for traceability
    PRIMARY KEY (ID),
    -- The natural key. FROM_STATUS is nullable and MySQL unique keys treat NULLs as distinct, so this
    -- does not prevent duplicate any-status rows; the seeder is insert-if-absent and owns that.
    UNIQUE KEY UK_RBIO_TRANSITION (ACTION_CODE, ROLE_NAME, FROM_STATUS)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL w0b_add_index('RBIO_WORKFLOW_TRANSITION', 'IDX_RBIO_TRANS_LOOKUP', 'ACTION_CODE, ROLE_NAME, IS_ACTIVE');
CALL w0b_add_index('RBIO_WORKFLOW_TRANSITION', 'IDX_RBIO_TRANS_ROLE', 'ROLE_NAME, IS_ACTIVE');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. COMPLAINTS.milestone + optimistic-lock version
--
-- Both NULLABLE, per the shared-database rule: ddl-auto never drops a column, so one session's NOT
-- NULL is permanent for everyone and would break every other session's inserts.
--
-- record_version backs UST675 record locking. NULL on every existing row: Hibernate treats a null
-- @Version as "not yet versioned" and initialises it on first write, so legacy rows are adopted
-- lazily rather than needing a backfill UPDATE across the whole table.
-- ═══════════════════════════════════════════════════════════════════════════
CALL w0b_add_column('COMPLAINTS', 'milestone',      'VARCHAR(30) NULL');
CALL w0b_add_column('COMPLAINTS', 'record_version', 'BIGINT NULL DEFAULT 0');

-- Backfill. The entity maps record_version as a PRIMITIVE long precisely because Hibernate does NOT
-- lazily initialise a null @Version — it reads null and increments it, which failed EVERY write to a
-- pre-existing complaint with `Cannot invoke "java.lang.Long.longValue()" because "current" is null`.
-- The primitive already handles that on read; this makes the stored data agree, so a row's version is
-- never null in the database either.
UPDATE COMPLAINTS SET record_version = 0 WHERE record_version IS NULL;

CALL w0b_add_index('COMPLAINTS', 'idx_complaint_milestone', 'milestone');

DROP PROCEDURE IF EXISTS w0b_add_index;
DROP PROCEDURE IF EXISTS w0b_add_column;
