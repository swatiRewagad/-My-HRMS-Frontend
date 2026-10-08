-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V109 — CEPC_LEGAL_CASE: the legal-case dossier behind the Legal Case sidebar icon
--
-- ONE ROW PER COMPLAINT, UNLIKE CEPC_CONTACT_PERSONS
--
--   The panel this backs has a single "Update Details" button and one set of fields, not an
--   add/list UI, so COMPLAINT_NUMBER is UNIQUE here — every save is an upsert of that one row, the
--   same shape RBIO_LEGAL_CASE already uses for its (much thinner) sub-judice tracker.
--
-- NOT THE SAME TABLE AS RBIO_LEGAL_CASE
--
--   RBIO_LEGAL_CASE exists already but carries only 6 fields (case number, court, status, filing/
--   hearing dates, remarks) — a sub-judice flag, not a dossier. This CEPC screen needs 13 fields
--   (parties, region, advocate, next hearing date, monetary claim, etc.) that have no home
--   there, and that table's naming/role-guard is RBIO-branded throughout. A parallel table avoids
--   both problems rather than overloading RBIO_LEGAL_CASE with CEPC-only columns.
--
-- WHY THE GUARD READS information_schema WITH UPPER(TABLE_NAME), AND WHY THE CREATE IS LOWERCASE
--
--   Unchanged from V103/V104/V106's headers: Hibernate folds @Table names to lowercase, and on
--   MySQL 8 information_schema.TABLES.TABLE_NAME is binary-collated, so a bare comparison against
--   an uppercase literal matches nothing. This is a genuinely new table, so the CREATE below
--   actually runs — and with lower_case_table_names=0 (the Linux default, and the dev container's
--   setting) an uppercase CREATE would leave ddl-auto=update to build a lowercase twin beside it
--   and strand this one empty.
--
--   Do not copy this form into database/oracle/, which needs the bare comparison for the opposite
--   folding reason.
--
-- Re-runnable. Procedure prefix cepc_v109_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v109_create_table;
DELIMITER //
CREATE PROCEDURE cepc_v109_create_table()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_LEGAL_CASE') THEN
        CREATE TABLE cepc_legal_case (
            ID                              BIGINT       NOT NULL AUTO_INCREMENT,
            COMPLAINT_NUMBER                VARCHAR(50)  NOT NULL,
            CASE_NUMBER                     VARCHAR(100) NULL,
            COURT_NAME                      VARCHAR(200) NULL,
            PARTIES_OF_CASE                 VARCHAR(500) NULL,
            REGION_OF_LEGAL_TEAM            VARCHAR(100) NULL,
            RBI_FIRST_RESPONDENT            TINYINT(1)   NULL,
            APPEARANCE_REQUIRED             TINYINT(1)   NULL,
            SUBJECT_MATTER                  TEXT         NULL,
            ADVOCATE_NAME                   VARCHAR(200) NULL,
            ASSISTANT_LEGAL_ADVISOR         VARCHAR(200) NULL,
            NEXT_HEARING_DATE               DATE         NULL,
            PRESENT_STATUS                  TEXT         NULL,
            ACTION_TAKEN_SO_FAR             TEXT         NULL,
            ACTION_TO_BE_TAKEN              TEXT         NULL,
            MONETARY_CLAIM_DETAILS          TEXT         NULL,
            CREATED_BY                      VARCHAR(200) NULL,
            LAST_MODIFIED_BY                VARCHAR(200) NULL,
            CREATED_AT                      DATETIME(6)  NULL,
            LAST_MODIFIED_AT                DATETIME(6)  NULL,
            PRIMARY KEY (ID),
            -- One dossier per complaint: every save is an upsert against this constraint.
            UNIQUE KEY UK_CEPC_LEGAL_CASE_COMPLAINT (COMPLAINT_NUMBER)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END //
DELIMITER ;

CALL cepc_v109_create_table();
DROP PROCEDURE IF EXISTS cepc_v109_create_table;
