-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V108 — FR-G-013: Non-Maintainable complaints get a Case ID instead of a Complaint Number
--
-- WHY THIS EXISTS
--
--   The BRD requires that a complaint closed as Non-Maintainable at the eligibility-screening stage
--   must NOT receive a complaint number, but must receive a Case ID instead, so the citizen can still
--   download a closure letter referencing something. COMPLAINTS.complaint_number was NOT NULL UNIQUE,
--   so a non-maintainable row had nowhere to go without either inventing a fake complaint number (which
--   defeats the BRD's own distinction) or relaxing that constraint — hence this migration.
--
--   CASE_ID_SEQUENCE mirrors COMPLAINT_NUMBER_SEQUENCE (V7) exactly, as a separate counter: Case IDs and
--   Complaint Numbers are legally distinct identifier spaces and must not consume each other's sequence
--   values.
--
-- Re-runnable: every ALTER is guarded on information_schema and the CREATE TABLEs use IF NOT EXISTS
-- (MySQL 8.4 has no ADD COLUMN IF NOT EXISTS). Procedure prefix v108_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS v108_add_column;
DROP PROCEDURE IF EXISTS v108_add_index;

DELIMITER //
CREATE PROCEDURE v108_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE v108_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE UNIQUE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. COMPLAINTS — new columns, and complaint_number relaxed to nullable
-- ═══════════════════════════════════════════════════════════════════════════
CALL v108_add_column('COMPLAINTS', 'case_id', 'VARCHAR(50) NULL');
CALL v108_add_column('COMPLAINTS', 'non_maintainable_clause_code', 'VARCHAR(100) NULL');

CALL v108_add_index('COMPLAINTS', 'idx_complaint_case_id', 'case_id');

-- MySQL unique indexes allow multiple NULLs, so relaxing NOT NULL does not weaken uniqueness among
-- rows that do carry a complaint number.
DROP PROCEDURE IF EXISTS v108_relax_complaint_number;
DELIMITER //
CREATE PROCEDURE v108_relax_complaint_number()
BEGIN
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
           AND COLUMN_NAME = 'complaint_number' AND IS_NULLABLE = 'NO') > 0 THEN
        ALTER TABLE COMPLAINTS MODIFY COLUMN complaint_number VARCHAR(50) NULL;
    END IF;
END //
DELIMITER ;

CALL v108_relax_complaint_number();
DROP PROCEDURE IF EXISTS v108_relax_complaint_number;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. CASE_ID_SEQUENCE — mirrors COMPLAINT_NUMBER_SEQUENCE (V7), a separate counter
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS CASE_ID_SEQUENCE (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    OFFICE_CODE VARCHAR(10) NOT NULL,
    FINANCIAL_YEAR VARCHAR(6) NOT NULL,
    LAST_SEQUENCE INT NOT NULL DEFAULT 0,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_case_id_office_fy (OFFICE_CODE, FINANCIAL_YEAR)
);

DROP PROCEDURE IF EXISTS v108_add_column;
DROP PROCEDURE IF EXISTS v108_add_index;
