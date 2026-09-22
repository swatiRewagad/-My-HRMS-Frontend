-- V36: AA parent-complaint search — RBIO office column, Ground-of-Complaint master, search indexes
-- MySQL version. Oracle twin: database/oracle/V34__aa_parent_search_office_and_ground.sql
--
-- Context: the Appellate Authority must find the PARENT complaint an appeal is filed against. The
-- only complaint search in the product is a three-field LIKE over subject / complaint number /
-- complainant name (ComplaintRepository.search). The AA stories need combinable, independent filters
-- on RBIO office, closure clause, category and ground of complaint, and none of those four is
-- answerable today.
--
--   1. COMPLAINTS.rbio_office_code is new. The originating office is encoded INSIDE the complaint
--      number: ComplaintNumberGeneratorService formats N{FY(6)}{officeCode(3)}{seq(6)}. Substringing
--      that in a WHERE clause would be unindexable and would silently mis-parse every other number
--      format, so the office becomes a real, indexed column resolved against OFFICE_CODE_MASTER.
--
--      THE BACKFILL IS DELIBERATELY PARTIAL. Only 28 of the ~1,346 live rows use the N-format. 1,268
--      are legacy 'CMP-20260528-100007' and 60 are 'CMS-DEMO-1000'; neither shape carries any office
--      information at all. Those rows keep a NULL office rather than a guessed one. An invented office
--      code is an invented territorial jurisdiction on a citizen's complaint, and jurisdiction decides
--      which Ombudsman may lawfully hear an appeal — a wrong value is worse than an honest blank. The
--      office filter therefore returns "no office recorded" for legacy rows, which is the truth.
--
--      The backfill also verifies the parsed code against OFFICE_CODE_MASTER, so a code that is not a
--      real office (a mis-parse, or a number minted before the master was seeded) stays NULL too.
--
--   2. GROUND_OF_COMPLAINT_MASTER is new, and COMPLAINTS.ground_of_complaint_id references it. There
--      was no ground-of-complaint concept anywhere in the schema. It is a master table rather than an
--      enum so that the Scheme's grounds can change without a code release, and because the AA search
--      dropdown must be sourced from data, never from a hardcoded list.
--
--      Grounds are seeded from the RB-IOS 2021 complaint-category vocabulary already present in
--      complaint_categories, NOT invented: each seeded row mirrors a category that citizens can
--      already file under. Scheme CLAUSE numbers are deliberately absent from this table — a ground of
--      complaint is an operational grouping, not a statutory citation, and no clause number is
--      guessed anywhere in this migration.
--
--   3. Search indexes. complainant_phone and complainant_name were unindexed but are both search
--      fields in the AA stories (mobile exact, name partial). entity_code was unindexed despite being
--      the PNO's authorisation scope — an unindexed scoping predicate on a table that will hold the
--      national complaint volume is a latent outage, not just slow.
--
-- Re-running is safe: every ALTER is guarded on information_schema and every seed is
-- INSERT ... WHERE NOT EXISTS (MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS).

-- ═══════════════════════════════════════════════════════════════════════════
-- 0. Guard helpers
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS s2a_add_column;
DROP PROCEDURE IF EXISTS s2a_add_index;

DELIMITER //
CREATE PROCEDURE s2a_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //

CREATE PROCEDURE s2a_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. GROUND_OF_COMPLAINT_MASTER
-- ═══════════════════════════════════════════════════════════════════════════
-- label_key is the i18n key; label is the English fallback that TranslationService serves when a
-- locale has no row, so the column is NOT NULL. scheme_version + effective dates mirror
-- CLOSURE_CLAUSE_MASTER so a Scheme amendment arrives as data.
CREATE TABLE IF NOT EXISTS GROUND_OF_COMPLAINT_MASTER (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    ground_code     VARCHAR(60)  NOT NULL,
    label           VARCHAR(300) NOT NULL,
    label_key       VARCHAR(160) NULL,
    scheme_version  VARCHAR(20)  NOT NULL,
    sort_order      INT          NULL,
    active          TINYINT(1)   NOT NULL DEFAULT 1,
    effective_from  DATE         NULL,
    effective_to    DATE         NULL,
    created_at      DATETIME(6)  NULL,
    updated_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ground_scheme_code UNIQUE (scheme_version, ground_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s2a_add_index('GROUND_OF_COMPLAINT_MASTER', 'idx_ground_active', 'active');
CALL s2a_add_index('GROUND_OF_COMPLAINT_MASTER', 'idx_ground_scheme', 'scheme_version');

-- Seeded from the existing RB-IOS 2021 complaint-category vocabulary (complaint_categories), so every
-- ground corresponds to something citizens can already file under. RBIOS_2021 only: 2026 grounds are
-- not invented here. The `FROM (SELECT 1) d` form gives the derived table a single named column, which
-- MySQL requires -- a bare `SELECT NOW(6), NOW(6)` derived table collides on duplicate column names.

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'ATM_DEBIT_CARD', 'ATM / Debit Card', 'aa.ground.atm_debit_card', 'RBIOS_2021', 1, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'ATM_DEBIT_CARD');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'CREDIT_CARD', 'Credit Card', 'aa.ground.credit_card', 'RBIOS_2021', 2, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'CREDIT_CARD');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'INTERNET_BANKING', 'Internet Banking', 'aa.ground.internet_banking', 'RBIOS_2021', 3, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'INTERNET_BANKING');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'MOBILE_BANKING_UPI', 'Mobile Banking / UPI', 'aa.ground.mobile_banking_upi', 'RBIOS_2021', 4, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'MOBILE_BANKING_UPI');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'LOAN_ADVANCES', 'Loan / Advances', 'aa.ground.loan_advances', 'RBIOS_2021', 5, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'LOAN_ADVANCES');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'DEPOSIT_ACCOUNTS', 'Deposit Accounts', 'aa.ground.deposit_accounts', 'RBIOS_2021', 6, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'DEPOSIT_ACCOUNTS');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'PENSION', 'Pension', 'aa.ground.pension', 'RBIOS_2021', 7, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'PENSION');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'REMITTANCE_TRANSFER', 'Remittance / Transfer', 'aa.ground.remittance_transfer', 'RBIOS_2021', 8, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'REMITTANCE_TRANSFER');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'INSURANCE', 'Insurance', 'aa.ground.insurance', 'RBIOS_2021', 9, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'INSURANCE');

INSERT INTO GROUND_OF_COMPLAINT_MASTER
    (ground_code, label, label_key, scheme_version, sort_order, active, effective_from, created_at, updated_at)
SELECT 'OTHERS', 'Others', 'aa.ground.others', 'RBIOS_2021', 99, 1, DATE '2021-11-12', NOW(6), NOW(6)
  FROM (SELECT 1) AS d
 WHERE NOT EXISTS (SELECT 1 FROM GROUND_OF_COMPLAINT_MASTER
                    WHERE scheme_version = 'RBIOS_2021' AND ground_code = 'OTHERS');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. COMPLAINTS — office + ground columns
-- ═══════════════════════════════════════════════════════════════════════════
CALL s2a_add_column('COMPLAINTS', 'rbio_office_code',       'VARCHAR(10) NULL');
CALL s2a_add_column('COMPLAINTS', 'ground_of_complaint_id', 'BIGINT NULL');

CALL s2a_add_index('COMPLAINTS', 'idx_complaint_rbio_office',   'rbio_office_code');
CALL s2a_add_index('COMPLAINTS', 'idx_complaint_ground',        'ground_of_complaint_id');
-- Search predicates from the AA stories that had no index.
CALL s2a_add_index('COMPLAINTS', 'idx_complaint_phone',         'complainant_phone');
CALL s2a_add_index('COMPLAINTS', 'idx_complaint_name',          'complainant_name');
CALL s2a_add_index('COMPLAINTS', 'idx_complaint_entity_code',   'entity_code');
CALL s2a_add_index('COMPLAINTS', 'idx_complaint_reopened_at',   'reopened_at');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. Office backfill — N-format numbers only, validated against OFFICE_CODE_MASTER
-- ═══════════════════════════════════════════════════════════════════════════
-- N{FY:6}{office:3}{seq:6} => office code is characters 8..10 of a 16-character number.
-- The join to OFFICE_CODE_MASTER means a mis-parse cannot invent an office: a parsed code that is not
-- a real active office simply leaves the column NULL. Legacy 'CMP-'/'CMS-DEMO-' numbers are untouched
-- by the length/prefix predicate and keep a NULL office, which is the honest answer for them.
UPDATE COMPLAINTS c
   JOIN OFFICE_CODE_MASTER o
     ON o.OFFICE_CODE = SUBSTRING(c.complaint_number, 8, 3)
    AND o.IS_ACTIVE = 1
    SET c.rbio_office_code = o.OFFICE_CODE
 WHERE c.rbio_office_code IS NULL
   AND c.complaint_number LIKE 'N%'
   AND CHAR_LENGTH(c.complaint_number) = 16
   AND SUBSTRING(c.complaint_number, 2, 6) REGEXP '^[0-9]{6}$'
   AND SUBSTRING(c.complaint_number, 8, 3) REGEXP '^[0-9]{3}$';

DROP PROCEDURE IF EXISTS s2a_add_column;
DROP PROCEDURE IF EXISTS s2a_add_index;
