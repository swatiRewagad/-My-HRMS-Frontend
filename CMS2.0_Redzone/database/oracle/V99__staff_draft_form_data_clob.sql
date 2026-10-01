-- ============================================================
-- V99 — Ensure STAFF_DRAFT.FORM_DATA_JSON is a CLOB
-- Session S7 (UST673, UST674)
-- Oracle counterpart of MySQL V101. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   The MySQL counterpart repairs real damage: the entity mapped this column as a bare `@Lob String`,
--   which that dialect resolves to TINYTEXT (255 bytes), and ddl-auto=update duly narrowed the LONGTEXT
--   the migration had created — silently truncating any staff draft containing a complaint description.
--   The entity now pins columnDefinition = "LONGTEXT".
--
--   Oracle is NOT affected the same way: V97 created the column as CLOB and Oracle has no TINYTEXT, so
--   there is nothing to widen. This migration exists for two narrower reasons:
--
--     1. To assert the invariant in the Oracle tree too, so a replay against a clean database proves the
--        column is a CLOB rather than leaving it to be inferred from the MySQL side.
--     2. Because columnDefinition = "LONGTEXT" is now on the entity, and LONGTEXT is not an Oracle type.
--        Under the Oracle profile ddl-auto is `validate` (never `update`), so Hibernate will not attempt
--        to apply it — but if anyone ever runs ddl-auto=update against Oracle, that DDL would fail loudly
--        rather than silently. Recording it here is what makes that failure diagnosable.
--
-- Guarded on USER_TAB_COLUMNS so a re-run is safe, and takes no action when the column is already a CLOB.
-- ============================================================

DECLARE
    v_type  VARCHAR2(128);
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'STAFF_DRAFT' AND COLUMN_NAME = 'FORM_DATA_JSON';

    IF v_count = 0 THEN
        -- V97 has not run, or ran against a different schema. Adding the column here rather than failing
        -- keeps the two trees independently replayable.
        EXECUTE IMMEDIATE 'ALTER TABLE STAFF_DRAFT ADD (FORM_DATA_JSON CLOB)';
    ELSE
        SELECT DATA_TYPE INTO v_type FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'STAFF_DRAFT' AND COLUMN_NAME = 'FORM_DATA_JSON';

        -- Oracle cannot MODIFY a VARCHAR2 to CLOB in place, so a narrower type needs a copy-through. This
        -- is not expected to trigger — V97 creates a CLOB — but leaving it unhandled would mean a schema
        -- that drifted had no repair path at all.
        IF v_type <> 'CLOB' THEN
            EXECUTE IMMEDIATE 'ALTER TABLE STAFF_DRAFT ADD (FORM_DATA_JSON_TMP CLOB)';
            EXECUTE IMMEDIATE 'UPDATE STAFF_DRAFT SET FORM_DATA_JSON_TMP = FORM_DATA_JSON';
            EXECUTE IMMEDIATE 'ALTER TABLE STAFF_DRAFT DROP COLUMN FORM_DATA_JSON';
            EXECUTE IMMEDIATE 'ALTER TABLE STAFF_DRAFT RENAME COLUMN FORM_DATA_JSON_TMP TO FORM_DATA_JSON';
        END IF;
    END IF;
END;
/
