-- ============================================================
-- V84 — Per-office assignment strategy and its mappings
-- Session S3 (UST468, UST469, UST470, UST471, UST472)
-- ============================================================
--
-- WHY THIS EXISTS
--
--   Each Ombudsman office must be able to choose HOW its Dealing Officer is picked: rotate among the
--   office's officers, route by regulated entity, or route by complaint category — exactly one logic
--   active per office (UST468). None of this existed. The only "strategy" anywhere was a single global
--   environment variable, ASSIGNMENT_STRATEGY, declared in cms-infrastructure/openshift/configmaps.yaml
--   and read by NO Java code at all; and cms-assignment-service, which the name suggests would own this,
--   has no entities, no endpoints and a reassign() that only writes a log line.
--
-- TWO TABLES, NOT ONE
--
--   OFFICE_ASSIGNMENT_STRATEGY holds the SELECTION (which logic an office uses).
--   OFFICE_ASSIGNMENT_MAPPING holds the DATA (which subject goes to which officer).
--
--   Keeping them apart means switching an office from ENTITY_MAPPING to CATEGORY_MAPPING and back does
--   not destroy either set of mappings. Folding the strategy into the mapping rows would make "the
--   office's current logic" an emergent property of whatever rows happen to exist, which is unanswerable
--   when an office has both kinds loaded.
--
-- THE TWO UNIQUE KEYS ARE THE POINT
--
--   uk_oas_office enforces UST468's "exactly one active logic per office" in the DATABASE. Two active
--   strategies for one office is a state the domain has no answer for: whichever row the query returned
--   first would decide, so one complaint could route differently on two pods. A constraint makes it
--   unrepresentable rather than merely unlikely.
--
--   uk_oam_office_type_subject does the same for mappings: one subject must not resolve to two officers
--   at the same office.
--
-- OFFICE IDENTITY
--
--   office_id is OFFICE_CODE_MASTER.OFFICE_CODE (e.g. '013'), the same key COMPLAINTS.rbio_office_code
--   and OFFICE_THRESHOLD_CONFIG use, so a complaint, its capacity row and its strategy row all name the
--   office identically. Office NAMES are deliberately not used: the two office masters disagree on them
--   ('Mumbai-I' versus 'Mumbai I').
--
-- SUBJECT KEYS ARE STORED, NOT REFERENCED
--
--   No foreign key to REGULATED_ENTITIES, because it has no code column at all — the complaint itself
--   carries a free-text entity NAME in entity_code. The subject is therefore a NORMALISED name
--   (upper-cased, punctuation stripped, whitespace collapsed) so that 'H.D.F.C. Bank' and 'HDFC BANK'
--   are one key. Storing the raw name would make the mapping miss on any punctuation difference, and a
--   missed mapping falls back to the Ombudsman Admin — so a complaint would quietly land on an admin
--   instead of the named officer with nothing visibly wrong.
--
--   Category subjects are the COMPLAINT_CATEGORIES id as a string (that table is the live category
--   master on MySQL with 10 rows; CATEGORY_MASTER exists only in the Oracle DDL and is empty here).
--   Stored as a string so one column serves both mapping types.
--
-- NO SEED DATA, DELIBERATELY
--
--   Every office is left unconfigured, which the resolver treats as ROUND_ROBIN — the behaviour offices
--   have today, so this migration changes nothing until a Super Admin opts an office in. Seeding a
--   mapping would mean inventing which officer handles which bank, and that is an operational decision
--   belonging to each office, not to a migration.
--
-- Re-running is safe: MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so DDL is guarded on
-- information_schema and every seed would be INSERT ... WHERE NOT EXISTS.
-- ============================================================

DROP PROCEDURE IF EXISTS s3_oas_add_index;

DELIMITER $$
CREATE PROCEDURE s3_oas_add_index(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255), IN p_unique TINYINT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('CREATE ', IF(p_unique = 1, 'UNIQUE ', ''), 'INDEX ', p_index,
                          ' ON ', p_table, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$
DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- 1. The strategy selection: one row per office
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS OFFICE_ASSIGNMENT_STRATEGY (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    office_id   VARCHAR(20)  NOT NULL,
    strategy    VARCHAR(30)  NOT NULL DEFAULT 'ROUND_ROBIN',
    role_group  VARCHAR(50)  NOT NULL DEFAULT 'RBIO_OFFICER',
    updated_by  VARCHAR(100) NULL,
    reason      VARCHAR(500) NULL,
    updated_at  DATETIME(6)  NULL
);

CALL s3_oas_add_index('OFFICE_ASSIGNMENT_STRATEGY', 'uk_oas_office', 'office_id', 1);
CALL s3_oas_add_index('OFFICE_ASSIGNMENT_STRATEGY', 'idx_oas_strategy', 'strategy', 0);

-- ─────────────────────────────────────────────────────────────
-- 2. The mapping data: subject -> officer, per office
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS OFFICE_ASSIGNMENT_MAPPING (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    office_id         VARCHAR(20)  NOT NULL,
    mapping_type      VARCHAR(20)  NOT NULL,
    subject_key       VARCHAR(250) NOT NULL,
    subject_label     VARCHAR(250) NULL,
    target_officer_id VARCHAR(200) NOT NULL,
    active            BIT(1)       NOT NULL DEFAULT b'1',
    updated_by        VARCHAR(100) NULL,
    updated_at        DATETIME(6)  NULL
);

CALL s3_oas_add_index('OFFICE_ASSIGNMENT_MAPPING', 'uk_oam_office_type_subject',
                      'office_id, mapping_type, subject_key', 1);
-- The resolver's hot path: (office, type, subject, active).
CALL s3_oas_add_index('OFFICE_ASSIGNMENT_MAPPING', 'idx_oam_lookup',
                      'office_id, mapping_type, subject_key, active', 0);
-- Supports "which subjects does this officer hold?", needed before deactivating them.
CALL s3_oas_add_index('OFFICE_ASSIGNMENT_MAPPING', 'idx_oam_officer', 'target_officer_id', 0);

DROP PROCEDURE IF EXISTS s3_oas_add_index;
