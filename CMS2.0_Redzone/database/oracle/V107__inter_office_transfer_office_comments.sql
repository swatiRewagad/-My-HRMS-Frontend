-- ============================================================
-- V107 — "Sent from office comments" on the inter-office transfer row
-- Oracle counterpart of MySQL V111.
-- ============================================================
--
-- See database/V111__inter_office_transfer_office_comments.sql for the full rationale. In brief:
-- the Forward milestone's Other Office option requires Reason for Transfer (already stored in
-- INTER_OFFICE_TRANSFERS.REASON) and a separately-stored Sent from Office Comments value, which had
-- no column anywhere.
--
-- Nullable (shared ddl-auto database). Re-running is safe: guarded on USER_TAB_COLUMNS.
-- ============================================================

DECLARE
    PROCEDURE add_column(p_table VARCHAR2, p_column VARCHAR2, p_definition VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_column;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD (' || p_column || ' ' || p_definition || ')';
        END IF;
    END;
BEGIN
    add_column('INTER_OFFICE_TRANSFERS', 'OFFICE_COMMENTS', 'CLOB');
END;
/
