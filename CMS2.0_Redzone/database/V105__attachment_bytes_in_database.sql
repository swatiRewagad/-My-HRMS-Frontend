-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V105 — COMPLAINT_ATTACHMENT_DATA: attachment bytes held in the database
--
-- WHY THIS EXISTS
--
--   Attachment bytes were written to a folder under cms.attachments.root-path and only a path was kept in
--   COMPLAINT_ATTACHMENTS. The documents therefore lived outside every guarantee the database gives:
--   nothing in a database backup, restore or replica contained them, and a row could — and in this
--   deployment did — outlive the file it pointed at, which the bundle endpoint reports as a missing
--   attachment. Bytes now go here instead.
--
-- WHY A SECOND TABLE AND NOT A FILE_DATA COLUMN ON COMPLAINT_ATTACHMENTS
--
--   A LOB on that table would be read whenever a row of it is read. @Basic(fetch = LAZY) does not help:
--   lazy basic fetching needs Hibernate's bytecode enhancer, which this build does not run, so the
--   annotation is ignored without complaint. The attachments sidebar lists names, types and sizes for a
--   complaint — with the bytes on the same row, rendering that list would pull up to the whole 25MB
--   per-complaint budget out of the database to display filenames.
--
-- EXISTING ATTACHMENTS ARE NOT MIGRATED
--
--   Their bytes are on a filesystem this script cannot read. Rows keep their STORAGE_PATH and no row is
--   created here for them; FileStorageService falls back to that path whenever this table has no row for
--   an attachment, so old and new attachments both download. Only rows created after this change have
--   STORAGE_PATH NULL, and that NULL is what says "the bytes are in the database".
--
-- WHY LONGBLOB
--
--   The enforced per-file cap is 5MB (FileStorageConfig.maxFileSize, overridable from SYSTEM_CONFIG), so
--   MEDIUMBLOB's 16MB would do today. LONGBLOB costs nothing extra per row — the length prefix is one byte
--   wider — and an administrator raising the cap past 16MB must not silently start truncating documents.
--
-- WHY THE GUARD READS information_schema WITH UPPER(TABLE_NAME)
--
--   Unchanged from V103/V104, whose headers set out the full argument: on MySQL 8
--   information_schema.TABLES.TABLE_NAME is utf8mb3_bin (binary), so a bare comparison against an
--   uppercase literal matches nothing and the CREATE would run against an already-present table.
--
--   Do not copy this form into database/oracle/ — that tree needs the bare comparison, for the opposite
--   folding reason. See the Oracle counterpart, V103.
--
-- Re-runnable. Procedure prefix cepc_v105_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v105_create_table;
DELIMITER //
CREATE PROCEDURE cepc_v105_create_table()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'COMPLAINT_ATTACHMENT_DATA') THEN
        -- LOWERCASE on purpose: Hibernate folds @Table("COMPLAINT_ATTACHMENT_DATA") to
        -- complaint_attachment_data, and under lower_case_table_names=0 (the Linux default, and the dev
        -- container's setting) that is a DIFFERENT table from the uppercase one. Creating it uppercase
        -- would leave ddl-auto=update to create a lowercase second table and write into that instead,
        -- stranding this one empty — and would fail outright under ddl-auto=validate. Oracle folds the
        -- opposite way and V103 is uppercase there; do not align the two.
        CREATE TABLE complaint_attachment_data (
            -- Shares COMPLAINT_ATTACHMENTS.ID — one row of bytes per attachment, no surrogate key.
            ATTACHMENT_ID BIGINT   NOT NULL,
            FILE_DATA     LONGBLOB NOT NULL,
            PRIMARY KEY (ATTACHMENT_ID)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END //
DELIMITER ;

CALL cepc_v105_create_table();
DROP PROCEDURE IF EXISTS cepc_v105_create_table;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- COMPLAINT_ATTACHMENTS.STORAGE_PATH must be nullable
--
--   Rows created from now on have no file, so they have no path. The column is left in place rather than
--   dropped: it is the only thing that locates the documents attached before this change.
--
--   No FK from COMPLAINT_ATTACHMENT_DATA to COMPLAINT_ATTACHMENTS. The application deletes the bytes and
--   the row in one transaction (FileStorageService.deleteAttachment), and the existing table carries no FK
--   to COMPLAINTS either — adding one here would be the only FK in this area and would fail on any
--   database where an earlier orphan already exists.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v105_relax_storage_path;
DELIMITER //
CREATE PROCEDURE cepc_v105_relax_storage_path()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    -- The table as the server actually spells it; see this file's header for why the case matters.
    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'COMPLAINT_ATTACHMENTS' LIMIT 1;

    IF v_table IS NOT NULL THEN
        -- v_table holds the server's own spelling, so this comparison needs no UPPER() wrapping.
        IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                      AND COLUMN_NAME = 'STORAGE_PATH' AND IS_NULLABLE = 'NO') THEN
            SET @sql = CONCAT('ALTER TABLE `', v_table, '` MODIFY COLUMN STORAGE_PATH VARCHAR(1000) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;
    END IF;
END //
DELIMITER ;

CALL cepc_v105_relax_storage_path();
DROP PROCEDURE IF EXISTS cepc_v105_relax_storage_path;
