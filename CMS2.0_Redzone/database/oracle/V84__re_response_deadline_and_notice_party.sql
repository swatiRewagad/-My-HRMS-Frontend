-- ============================================================
-- V84 — RE response deadlines, the 13(1) notice addressee, and the overdue flag
-- Session S3 (UST779, UST780, UST637, UST638). Oracle counterpart of MySQL V86.
-- ============================================================
--
-- See database/V86__re_response_deadline_and_notice_party.sql for the full rationale. In brief:
--
--   * RE_RESPONSE_DEADLINE existed but had ONE writer (the extension-grant path), so it was NULL
--     everywhere and the 30-minute sweep — which filters on IS NOT NULL — swept an empty set. No
--     unresponsive entity was ever chased.
--   * NOTICE_13_1_TARGET_PARTY closes a silent data loss: the screen has always sent targetParty and the
--     side effect never read it, so the addressee of a statutory communication was discarded on a 200.
--   * RE_RESPONSE_OVERDUE is persisted rather than computed per request because the grid whitelists
--     sortable columns — a value derived in Java cannot be ordered by the database — and because a browser
--     comparing two dates can be wrong about the timezone. The sweep clears it as well as setting it, which
--     is what makes the highlight disappear once the entity responds.
--
-- A DATE, not a timestamp: the officer picks a calendar day and the Scheme speaks in days.
--
-- All columns NULLABLE (shared ddl-auto database) and nothing is backfilled: inventing a deadline for a
-- historical complaint would assert that an entity was given a window it never received.
--
-- Re-running is safe: guarded on USER_TAB_COLUMNS / USER_INDEXES.
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

    PROCEDURE add_index(p_index VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_index;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;

    PROCEDURE add_config(p_key VARCHAR2, p_value VARCHAR2, p_description VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_count = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_description, 'V84_migration', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    add_column('COMPLAINTS', 'NOTICE_13_1_TARGET_PARTY', 'VARCHAR2(250)');
    add_column('COMPLAINTS', 'RE_RESPONSE_OVERDUE', 'NUMBER(1)');
    add_column('NODAL_OFFICER_RECORDS', 'RE_RESPONSE_DEADLINE', 'DATE');
    add_column('NODAL_OFFICER_RECORDS', 'DEADLINE_COMMUNICATION', 'VARCHAR2(50)');

    add_index('IDX_NO_RE_DEADLINE',
        'CREATE INDEX IDX_NO_RE_DEADLINE ON NODAL_OFFICER_RECORDS (RE_RESPONSE_DEADLINE)');
    add_index('IDX_COMPLAINT_RE_DEADLINE',
        'CREATE INDEX IDX_COMPLAINT_RE_DEADLINE ON COMPLAINTS (RE_RESPONSE_DEADLINE)');
    add_index('IDX_COMPLAINT_RE_OVERDUE',
        'CREATE INDEX IDX_COMPLAINT_RE_OVERDUE ON COMPLAINTS (RE_RESPONSE_OVERDUE)');

    -- UST638: a SYSTEM_CONFIG row rather than a property, because an @Scheduled placeholder resolves once
    -- at bean creation and changing it would need a restart.
    add_config('re.deadline.sweep_interval_minutes', '30',
        'Minutes between RE-deadline overdue sweeps. Read on every fire, so a change needs no restart.');

    -- UST637: a hex colour cannot go through TimelineConfigController, which accepts only 'timeline.' keys
    -- with integer values between 1 and 365.
    add_config('re.deadline.overdue_highlight_colour', '#b91c1c',
        'Row highlight for a complaint whose RE response is overdue (UST637).');

    COMMIT;
END;
/
