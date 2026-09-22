-- V9: Citizen tracking authorization audit (UST98 / UST99)
-- MySQL version
--
-- Context: the AuditLog JPA entity (com.hrms.cms.entity.AuditLog) maps to AUDIT_LOG with the
-- shape COMPLAINT_NUMBER/ACTION/ACTOR/ACTOR_ROLE/TIMESTAMP/... The AUDIT_LOG created in
-- V1__initial_schema.sql is the legacy generic entity-audit shape (ENTITY_TYPE/ENTITY_ID/...)
-- used by cms-audit-service. Oracle already separates these as AUDIT_LOG (JPA) and
-- AUDIT_LOG_SERVICE (legacy). This migration brings MySQL in line so `ddl-auto: validate`
-- passes in non-dev environments.

-- ═══ 1. Move the legacy generic audit table aside (idempotent) ═══
SET @legacy_exists := (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG' AND COLUMN_NAME = 'ENTITY_TYPE'
);
SET @already_moved := (
    SELECT COUNT(*) FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG_SERVICE'
);
SET @sql := IF(@legacy_exists > 0 AND @already_moved = 0,
               'RENAME TABLE AUDIT_LOG TO AUDIT_LOG_SERVICE',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(@legacy_exists > 0 AND @already_moved > 0, 'DROP TABLE AUDIT_LOG', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ═══ 2. AUDIT_LOG in the JPA shape ═══
CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_number VARCHAR(50) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    actor_role VARCHAR(50) NULL,
    timestamp DATETIME NOT NULL,
    remarks TEXT NULL,
    metadata TEXT NULL,
    ip_address VARCHAR(50) NULL,
    previous_state VARCHAR(50) NULL,
    new_state VARCHAR(50) NULL
);

-- MySQL has no CREATE INDEX IF NOT EXISTS, and hibernate ddl-auto may already have created
-- the table together with its indexes. Each index is therefore guarded via information_schema.
-- Plain statements only (no DELIMITER / stored procedure) so any migration runner can execute this.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG'
                AND INDEX_NAME = 'idx_audit_complaint');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_audit_complaint ON AUDIT_LOG (complaint_number)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG'
                AND INDEX_NAME = 'idx_audit_action');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_audit_action ON AUDIT_LOG (action)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG'
                AND INDEX_NAME = 'idx_audit_actor');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_audit_actor ON AUDIT_LOG (actor)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG'
                AND INDEX_NAME = 'idx_audit_timestamp');
SET @sql := IF(@idx = 0, 'CREATE INDEX idx_audit_timestamp ON AUDIT_LOG (timestamp)', 'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Citizen tracking access is written here with:
--   action = TRACK_VIEWED         (complaint status viewed via the public tracker)
--   action = TRACK_LIST_DENIED    (attempt to list complaints for another mobile number)
--   action = WITHDRAW_DENIED      (attempt to withdraw another complainant's complaint)
--   actor_role = CITIZEN | STAFF | PUBLIC
-- Composite index supporting "who accessed this complaint, when" forensic queries.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'AUDIT_LOG'
                AND INDEX_NAME = 'idx_audit_complaint_action_ts');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_audit_complaint_action_ts ON AUDIT_LOG (complaint_number, action, timestamp)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
