-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V46 — AA assignment engine: placement records, audit trail, officer skills, stable pointer
--
-- Session S2C. Re-runnable: every DDL statement is guarded through information_schema, because
-- MySQL 8.4 has no IF NOT EXISTS for ADD COLUMN or CREATE INDEX.
--
-- Procedure names are prefixed s2c_ rather than reusing the aa_ helpers from V31. Those are dropped
-- at the end of the file that defines them, and a concurrent session re-creating them would clash.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s2c_add_column;
DELIMITER //
CREATE PROCEDURE s2c_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS s2c_add_index;
DELIMITER //
CREATE PROCEDURE s2c_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. AA_ASSIGNMENT_RECORD — one row per placement of an appeal with an officer
--
-- A row per placement rather than a column on APPEALS. The engine needs to know when a placement
-- happened, whether the assignee has opened it, whether it has been escalated, and whether it counts
-- against their threshold; and keeping history means a reassignment does not erase who held the record
-- before. Exactly one row per appeal should have RELEASED_AT NULL — that is the current holder.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS AA_ASSIGNMENT_RECORD (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    appeal_number     VARCHAR(50)  NOT NULL,
    assigned_user_id  VARCHAR(200) NOT NULL,
    role_group        VARCHAR(100) NOT NULL,
    -- ASSIGNED | VERNACULAR_OVERRIDE | ASSIGNED_AFTER_POINTER_RESET | ASSIGNED_UNDER_GRACE |
    -- MANUAL_OVERRIDE. Stored so a threshold breach or an override stays distinguishable from an
    -- ordinary placement after the fact.
    outcome           VARCHAR(40)  NOT NULL,
    -- A vernacular override must not consume the assignee's capacity: they were chosen because they
    -- are the only officer who can read the record.
    threshold_exempt  TINYINT(1)   NOT NULL DEFAULT 0,
    assigned_by       VARCHAR(200),
    assigned_at       DATETIME(6)  NOT NULL,
    claimed_at        DATETIME(6)  NULL,
    escalated_at      DATETIME(6)  NULL,
    released_at       DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s2c_add_index('AA_ASSIGNMENT_RECORD', 'idx_aaar_appeal', 'appeal_number');
CALL s2c_add_index('AA_ASSIGNMENT_RECORD', 'idx_aaar_holder', 'assigned_user_id, released_at');
CALL s2c_add_index('AA_ASSIGNMENT_RECORD', 'idx_aaar_unclaimed', 'released_at, claimed_at, assigned_at');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. AA_ASSIGNMENT_AUDIT — actor + timestamp + reason for every mutation of shared state
--
-- Not CONFIG_AUDIT_LOG, which has no reason column (config_key/old_value/new_value/changed_by/
-- changed_at only). Threshold edits, activation changes, manual assignments and above-threshold
-- placements all need to record WHY. The old->new field shape is copied from it so the two read alike.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS AA_ASSIGNMENT_AUDIT (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    action            VARCHAR(40)   NOT NULL,
    -- Null for pool-wide actions such as a pointer reset, which are about no single officer.
    subject_user_id   VARCHAR(200),
    role_group        VARCHAR(100),
    appeal_number     VARCHAR(50),
    field_name        VARCHAR(60),
    old_value         VARCHAR(500),
    new_value         VARCHAR(500),
    reason            VARCHAR(1000),
    -- Resolved server-side from the JWT. A client-supplied actor is spoofable and was the bug S1 closed.
    performed_by      VARCHAR(200)  NOT NULL,
    performed_by_role VARCHAR(50),
    performed_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s2c_add_index('AA_ASSIGNMENT_AUDIT', 'idx_aaa_audit_action', 'action');
CALL s2c_add_index('AA_ASSIGNMENT_AUDIT', 'idx_aaa_audit_subject', 'subject_user_id');
CALL s2c_add_index('AA_ASSIGNMENT_AUDIT', 'idx_aaa_audit_at', 'performed_at');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. WF_OFFICER_POOL — language skills
--
-- Extends the existing pool rather than introducing a parallel AA one: it already carries active,
-- on-leave and threshold state, is populated, and lives in this same schema even though
-- cms-workflow-service created it. Two pool tables would have to be kept in step by hand.
--
-- CSV of ISO codes ("bn,hi") rather than a join table, matching how multi-valued config is already
-- stored and read elsewhere. A pool of this size does not justify a join.
-- ═══════════════════════════════════════════════════════════════════════════
CALL s2c_add_column('wf_officer_pool', 'skill_languages', 'VARCHAR(200) NULL');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. WF_ASSIGNMENT_COUNTER — pointer that identifies an officer, not a list position
--
-- LAST_ASSIGNED_INDEX is left in place and untouched: cms-workflow-service still uses it for the
-- CRPC/RBIO/CEPC pools. It cannot serve AA because it was applied modulo a candidate list re-sorted by
-- workload on every call, so position 3 meant a different officer each time and the rotation was not
-- deterministic. A user id keeps its meaning across a re-sort, a pool change and a restart.
-- ═══════════════════════════════════════════════════════════════════════════
CALL s2c_add_column('wf_assignment_counter', 'last_assigned_user_id', 'VARCHAR(255) NULL');

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. SYSTEM_CONFIG — assignment tunables (the "Preference Master")
--
-- There is no PREFERENCE_MASTER table in this schema; SYSTEM_CONFIG is the established typed,
-- cached, auditable config store, so the escalation backoff and assignment policy live here.
--
-- grace_allowance defaults to 0: without an explicit operator decision, an exhausted pool must leave
-- work unassigned and visible rather than silently pushing officers past a threshold.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT * FROM (
    SELECT 'cms.aa.assignment.grace_allowance' AS k, '0' AS v,
           'Records an AA officer may exceed their threshold by when the pool is exhausted. 0 disables.' AS d,
           'V46' AS u, NOW() AS t
    UNION ALL SELECT 'cms.aa.assignment.auto_rebalance', 'false',
           'Whether lowering a threshold rebalances held records automatically, or waits for an admin.',
           'V46', NOW()
    UNION ALL SELECT 'cms.aa.escalation.unclaimed_backoff_minutes', '2880',
           'Minutes an assigned draft may sit unclaimed before the AA Admin is alerted. Default 48h.',
           'V46', NOW()
    UNION ALL SELECT 'cms.aa.escalation.enabled', 'true',
           'Master switch for the unclaimed-draft escalation sweep.', 'V46', NOW()
    UNION ALL SELECT 'cms.aa.reassign.require_admin_approval', 'true',
           'Whether an AA reassignment request needs admin approval, or is auto-approved on raise.',
           'V46', NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG sc WHERE sc.config_key = seed.k
);

DROP PROCEDURE IF EXISTS s2c_add_column;
DROP PROCEDURE IF EXISTS s2c_add_index;
