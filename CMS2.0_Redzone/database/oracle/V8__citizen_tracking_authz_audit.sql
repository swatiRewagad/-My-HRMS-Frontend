-- V8: Citizen tracking authorization audit (UST98 / UST99)
-- Oracle version
--
-- AUDIT_LOG already exists in the JPA shape (see V4__complete_oracle_ddl.sql:310), and the
-- legacy generic entity-audit table is already separated as AUDIT_LOG_SERVICE. This migration
-- only adds the composite index that supports the citizen tracking forensic queries.
--
-- Citizen tracking access is written to AUDIT_LOG with:
--   ACTION = TRACK_VIEWED         (complaint status viewed via the public tracker)
--   ACTION = TRACK_LIST_DENIED    (attempt to list complaints for another mobile number)
--   ACTION = WITHDRAW_DENIED      (attempt to withdraw another complainant's complaint)
--   ACTOR_ROLE = CITIZEN | STAFF | PUBLIC

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES
     WHERE INDEX_NAME = 'IDX_AUDIT_COMPLAINT_ACTION_TS';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE
            'CREATE INDEX IDX_AUDIT_COMPLAINT_ACTION_TS ON AUDIT_LOG(COMPLAINT_NUMBER, ACTION, TIMESTAMP)';
    END IF;
END;
/
