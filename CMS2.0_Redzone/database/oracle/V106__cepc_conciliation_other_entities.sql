-- ============================================================
-- V106 — CEPC_CONCILIATION_MEETINGS: other-entities fields
-- Oracle counterpart of MySQL V110. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   The Conciliation tab replaces its free-text "Conciliation Remarks" field with a structured
--   Yes/No — "Want to add other entities" — followed by up to six entity picks (Entity Name 1..6).
--   WANT_OTHER_ENTITIES carries the answer; OTHER_ENTITY_IDS/OTHER_ENTITY_NAMES carry the picks,
--   comma-separated and positionally paired, the same denormalisation COMPLAINTS uses for
--   ENTITY_NAME alongside its regulated-entity id — the ids are authoritative, the names are the
--   display label. A fixed cap of six does not warrant six pairs of columns or a child table.
--
-- Guarded on USER_TAB_COLUMNS so a re-run is safe.
-- ============================================================

DECLARE
    v_table NUMBER;

    PROCEDURE add_column(p_name VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'CEPC_CONCILIATION_MEETINGS' AND COLUMN_NAME = p_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CEPC_CONCILIATION_MEETINGS ADD (' || p_name || ' ' || p_type || ')';
        END IF;
    END;
BEGIN
    SELECT COUNT(*) INTO v_table FROM USER_TABLES
     WHERE TABLE_NAME = 'CEPC_CONCILIATION_MEETINGS';

    IF v_table = 0 THEN
        DBMS_OUTPUT.PUT_LINE('CEPC_CONCILIATION_MEETINGS absent - skipping');
    ELSE
        add_column('WANT_OTHER_ENTITIES', 'NUMBER(1)');
        add_column('OTHER_ENTITY_IDS',    'VARCHAR2(200)');
        add_column('OTHER_ENTITY_NAMES',  'VARCHAR2(1000)');
    END IF;
END;
/
