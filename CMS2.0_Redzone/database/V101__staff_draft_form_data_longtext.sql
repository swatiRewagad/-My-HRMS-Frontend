-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V101 — Repair STAFF_DRAFT.FORM_DATA_JSON to LONGTEXT
-- Session S7 (UST673, UST674)
--
-- WHY THIS CORRECTIVE MIGRATION EXISTS
--
--   V99 created FORM_DATA_JSON as LONGTEXT, which is correct. But the entity mapped it as a bare
--   `@Lob String`, and under this MySQL dialect Hibernate resolves that to TINYTEXT — 255 bytes. With
--   ddl-auto=update on the shared dev database, Hibernate then issued:
--
--       alter table staff_draft modify column form_data_json tinytext not null
--
--   It also created a SECOND, lowercase column: the entity's @Column(name = "FORM_DATA_JSON") and the
--   migration's quoted identifier did not agree on case for the purposes of Hibernate's schema scan, so
--   it added `form_data_json` alongside the existing `FORM_DATA_JSON`.
--
--   The consequence is silent data loss. A staff draft carrying a complaint description — the whole
--   point of the feature — would be truncated at 255 characters, and the truncation would only surface
--   when an officer tried to resume and found their work cut off mid-sentence. Caught in the startup
--   log rather than by a test, which is worth recording: the entity now pins
--   columnDefinition = "LONGTEXT" so the dialect cannot reinterpret it.
--
--   This migration is written to be safe whichever state the database is in: a fresh database created
--   from V99 (single LONGTEXT column), or one that ddl-auto has already altered (a TINYTEXT column,
--   possibly duplicated in the other case).
--
-- Re-runnable. Procedure prefix s7f_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s7f_repair_form_data;
DELIMITER //
CREATE PROCEDURE s7f_repair_form_data()
BEGIN
    DECLARE v_upper INT DEFAULT 0;
    DECLARE v_lower INT DEFAULT 0;

    -- information_schema.COLUMNS is case-insensitive on COLUMN_NAME under the default collation, so the
    -- two spellings are distinguished with a BINARY comparison. Without it both counts match the same
    -- column and the repair below would target the wrong one.
    SELECT COUNT(*) INTO v_upper FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'STAFF_DRAFT'
       AND BINARY COLUMN_NAME = 'FORM_DATA_JSON';

    SELECT COUNT(*) INTO v_lower FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'STAFF_DRAFT'
       AND BINARY COLUMN_NAME = 'form_data_json';

    -- Case 1: both columns exist. ddl-auto added the lowercase one. Copy across anything the duplicate
    -- captured before dropping it — a draft written through the entity landed in the lowercase column,
    -- so dropping it blindly would discard real drafts.
    IF v_upper = 1 AND v_lower = 1 THEN
        UPDATE STAFF_DRAFT
           SET FORM_DATA_JSON = form_data_json
         WHERE (FORM_DATA_JSON IS NULL OR FORM_DATA_JSON = '')
           AND form_data_json IS NOT NULL AND form_data_json <> '';

        ALTER TABLE STAFF_DRAFT DROP COLUMN form_data_json;
    END IF;

    -- Case 2: only the lowercase column exists. Rename it into place rather than adding a third.
    IF v_upper = 0 AND v_lower = 1 THEN
        ALTER TABLE STAFF_DRAFT CHANGE COLUMN form_data_json FORM_DATA_JSON LONGTEXT NOT NULL;
    END IF;

    -- Case 3 (and the tail of case 1): the canonical column exists but may have been narrowed to
    -- TINYTEXT. Widening is unconditional because it is idempotent and cheap, and because a column that
    -- is ALREADY LONGTEXT is unaffected.
    IF v_upper = 1 THEN
        ALTER TABLE STAFF_DRAFT MODIFY COLUMN FORM_DATA_JSON LONGTEXT NOT NULL;
    END IF;
END //
DELIMITER ;

CALL s7f_repair_form_data();
DROP PROCEDURE IF EXISTS s7f_repair_form_data;
