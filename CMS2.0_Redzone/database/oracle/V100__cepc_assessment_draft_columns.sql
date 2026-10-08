-- ============================================================
-- V100 — CEPC_COMPLAINT_ASSESSMENT: draft columns for the Final Decision and Forward tabs
-- Oracle counterpart of MySQL V102. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   The meta row's Save icon used to appear only on the Summary tab. Extending it to the Conciliation,
--   Forward and Final Decision tabs needs somewhere to put a decision that is still being drafted —
--   until now those two tabs' fields were persisted ONLY by their terminal action (send-for-approval,
--   which closes the complaint). Conciliation needed nothing: CEPC_CONCILIATION_MEETING already stores
--   all eight of its fields.
--
--   Six of the Final Decision fields already have columns here, plus the three money columns; this adds
--   only the six that did not, and one CLOB for the Forward draft.
--
-- ORACLE-SPECIFIC NOTES
--
--   The MySQL side's row-size argument does not apply to Oracle, which has no 65535-byte row limit. The
--   Forward draft is still a single CLOB here so both trees agree with the one entity mapping.
--
--   TEXT is not an Oracle type: the entity pins columnDefinition = "TEXT" for the MySQL dialect's sake,
--   so under an Oracle profile that DDL would fail loudly. That is safe in practice because the Oracle
--   profiles run ddl-auto=validate, never update — but it is why these columns must exist here as real
--   DDL rather than being left to Hibernate.
--
--   CEPC_COMPLAINT_ASSESSMENT has no CREATE TABLE anywhere in database/ — it exists only via
--   ddl-auto=update on the dev profiles. This script creates the columns when the table is present and
--   no-ops when it is not, so it can be replayed independently of that.
--
-- Guarded on USER_TAB_COLUMNS so a re-run is safe.
-- ============================================================

DECLARE
    v_table NUMBER;

    PROCEDURE add_column(p_name VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'CEPC_COMPLAINT_ASSESSMENT' AND COLUMN_NAME = p_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CEPC_COMPLAINT_ASSESSMENT ADD (' || p_name || ' ' || p_type || ')';
        END IF;
    END;
BEGIN
    SELECT COUNT(*) INTO v_table FROM USER_TABLES
     WHERE TABLE_NAME = 'CEPC_COMPLAINT_ASSESSMENT';

    IF v_table = 0 THEN
        DBMS_OUTPUT.PUT_LINE('CEPC_COMPLAINT_ASSESSMENT absent - skipping');
    ELSE
        add_column('FINAL_DECISION_ACTION',             'VARCHAR2(40)');
        add_column('REJECT_WITHDRAW_SETTLE_SUB_ACTION', 'VARCHAR2(40)');
        add_column('REJECT_WITHDRAW_SETTLE_REASON',     'CLOB');
        add_column('CLOSURE_CLAUSE_DESCRIPTION',        'CLOB');

        -- Deliberately not COMPLAINTS.CLOSURE_CLAUSE: that column is written by the terminal workflow arm
        -- and a draft save must not overwrite a clause the closure already committed.
        add_column('CLOSURE_CLAUSE_DRAFT',              'VARCHAR2(60)');

        -- Distinct from COMPLAINTS.AWARD_IMPLEMENTED_DATE, which records when the RE actually complied.
        add_column('AWARD_IMPLEMENTATION_DATE',         'DATE');

        add_column('FORWARD_DRAFT_JSON',                'CLOB');
    END IF;
END;
/
