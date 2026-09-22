-- V17: RE Activity Status ladder (UST846, UST847, UST848, UST849, UST850, UST851, UST852)
-- Oracle version — twin of MySQL V18. Oracle numbering runs one behind MySQL by convention.
--
-- Context: there was no activity-status concept at all. COMPLAINTS.STATUS is the regulatory
-- workflow state in lowercase snake_case ("pending", "re_responded"), which answers "where is this
-- complaint in the process" but not "has the entity actually started work on it" — the question
-- CEPC/RBI staff currently resolve by telephoning the bank (CEPC BRD Gap 04).
--
-- Four things this migration establishes:
--   1. RE_ACTIVITY_STATUS on COMPLAINTS, a separate 7-level ladder in UPPER_SNAKE_CASE. It is NOT
--      the same column as STATUS and never replaces it. New columns use uppercase deliberately:
--      the mixed-case legacy vocabulary is what produced status CSS classes that matched nothing.
--   2. RE_ACTIVITY_NUDGE_DAYS — the nudge threshold SNAPSHOTTED when a record enters a status, so
--      that later editing of the SYSTEM_CONFIG default cannot retroactively make historical records
--      nudge-due, nor silently forgive ones already past due (UST850).
--   3. COMPLAINT_TIMELINE.EVENT_SOURCE — an explicit AUTOMATIC/MANUAL discriminator (UST848).
--      Previously the only signal was PERFORMED_BY, written as "SYSTEM", "System" and "system" by
--      different services, so "what did the entity actually do" was unanswerable.
--   4. CONFIG_CHANGE_REQUEST — maker-checker staging for nudge thresholds (UST851). A threshold
--      governs when staff are told an entity has stalled, so one administrator raising it to 365
--      would suppress the signal estate-wide unnoticed.
--
-- Re-running is safe: every ALTER is guarded on USER_TAB_COLUMNS and every seed on a COUNT(*).

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. RE Activity Status ladder on COMPLAINTS, and 2. draft columns on the tracker
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_count NUMBER;

    PROCEDURE add_col(p_table VARCHAR2, p_name VARCHAR2, p_type VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD ' || p_name || ' ' || p_type;
        END IF;
    END;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'COMPLAINTS';
    IF v_count > 0 THEN
        -- NULL means "never touched" and is read as NOT_OPENED, so historical rows need no backfill.
        add_col('COMPLAINTS', 'RE_ACTIVITY_STATUS',     'VARCHAR2(30)');
        add_col('COMPLAINTS', 'RE_ACTIVITY_CHANGED_AT', 'TIMESTAMP(6)');
        add_col('COMPLAINTS', 'RE_ACTIVITY_NUDGE_DAYS', 'NUMBER(10)');
        add_col('COMPLAINTS', 'RE_ACTIVITY_NUDGED_AT',  'TIMESTAMP(6)');
    END IF;

    -- Draft response is kept separate from RESPONSE_TEXT: a draft is entity-private working
    -- material, whereas RESPONSE_TEXT is the submitted answer RBI acts on. Conflating them would
    -- make an unfinished draft look like a response on the staff side.
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'RE_RESPONSE_TRACKER';
    IF v_count > 0 THEN
        add_col('RE_RESPONSE_TRACKER', 'DRAFT_RESPONSE_TEXT', 'CLOB');
        add_col('RE_RESPONSE_TRACKER', 'DRAFT_SAVED_AT',      'TIMESTAMP(6)');
    END IF;
END;
/

-- The nudge sweep filters on (status, nudged_at IS NULL) and orders by changed_at, so this index
-- covers it. Age is deliberately NOT in the query — the threshold is per-row (see note 2 above).
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_COMPLAINT_RE_ACTIVITY';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_COMPLAINT_RE_ACTIVITY ON COMPLAINTS '
                       || '(RE_ACTIVITY_STATUS, RE_ACTIVITY_NUDGED_AT, RE_ACTIVITY_CHANGED_AT)';
    END IF;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. AUTOMATIC vs MANUAL discriminator on COMPLAINT_TIMELINE (UST848)
--    Backfilled from PERFORMED_BY, the only signal the old rows carry. Rows whose actor is
--    genuinely ambiguous are left MANUAL rather than guessed: over-claiming a row as system-derived
--    would misattribute a human decision in an audit trail.
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINT_TIMELINE' AND COLUMN_NAME = 'EVENT_SOURCE';

    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINT_TIMELINE ADD EVENT_SOURCE VARCHAR2(20) DEFAULT ''MANUAL''';

        -- 'RE_PORTAL' is deliberately NOT in this list. RePortalService writes that literal for
        -- queries an RE user actually raised, so classifying it AUTOMATIC would relabel real entity
        -- work as machine-generated — the exact misattribution this column exists to prevent.
        EXECUTE IMMEDIATE 'UPDATE COMPLAINT_TIMELINE SET EVENT_SOURCE = ''AUTOMATIC'' '
                       || 'WHERE UPPER(TRIM(NVL(PERFORMED_BY, ''''))) '
                       || 'IN (''SYSTEM'', ''SCHEDULER'', ''AUTO'', ''AUTOMATED'')';

        EXECUTE IMMEDIATE 'UPDATE COMPLAINT_TIMELINE SET EVENT_SOURCE = ''MANUAL'' '
                       || 'WHERE EVENT_SOURCE IS NULL';
        COMMIT;
    END IF;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. Maker-checker staging for config changes (UST851)
--    The existing CONFIG_AUDIT_LOG records what changed after the fact; this records the intent and
--    the two-person decision before it takes effect. CONFIG_AUDIT_LOG is still written on approval,
--    so it remains the single place to read "what actually changed".
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'CONFIG_CHANGE_REQUEST_SEQ';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE SEQUENCE CONFIG_CHANGE_REQUEST_SEQ START WITH 1 INCREMENT BY 1 NOCACHE';
    END IF;

    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'CONFIG_CHANGE_REQUEST';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE CONFIG_CHANGE_REQUEST (
                ID               NUMBER(19) DEFAULT CONFIG_CHANGE_REQUEST_SEQ.NEXTVAL PRIMARY KEY,
                CONFIG_KEY       VARCHAR2(100) NOT NULL,
                CURRENT_VALUE    VARCHAR2(500),
                PROPOSED_VALUE   VARCHAR2(500) NOT NULL,
                REASON           VARCHAR2(500),
                REQUESTED_BY     VARCHAR2(200) NOT NULL,
                REQUESTED_AT     TIMESTAMP(6)  NOT NULL,
                STATUS           VARCHAR2(20)  NOT NULL,
                DECIDED_BY       VARCHAR2(200),
                DECIDED_AT       TIMESTAMP(6),
                DECISION_REASON  VARCHAR2(500)
            )';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_CCR_STATUS ON CONFIG_CHANGE_REQUEST(STATUS)';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_CCR_KEY ON CONFIG_CHANGE_REQUEST(CONFIG_KEY)';
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_CCR_REQUESTED_AT ON CONFIG_CHANGE_REQUEST(REQUESTED_AT)';
    END IF;
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. Configurable thresholds. No hardcoded values in code — these are read from
--    SYSTEM_CONFIG at runtime and snapshotted onto the record at transition time.
--
--    Per-status rather than one global number because "opened but untouched for 3 days" and
--    "documents uploaded but not submitted for 3 days" are not equally concerning. Only the early
--    ladder levels are nudgeable: once a response is in, silence is not a problem, and nudging an
--    already-OVERDUE record would duplicate the SLA escalation that UST850 says nudges must not
--    replace.
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    PROCEDURE add_cfg(p_key VARCHAR2, p_val VARCHAR2, p_desc VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_exists = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_val, p_desc, 'V17_MIGRATION', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    add_cfg('cms.re.activity.nudge_days.default', '3',
            'Default days a record may sit in an early RE activity status before the owning officer is nudged (UST850)');
    add_cfg('cms.re.activity.nudge_days.not_opened', '2',
            'Days a forwarded record may sit unopened by the entity before nudging (UST850)');
    add_cfg('cms.re.activity.nudge_days.opened', '3',
            'Days a record may sit at Opened with no further entity progress before nudging (UST850)');
    add_cfg('cms.re.activity.nudge_days.under_review', '3',
            'Days a record may sit at Under Review before nudging (UST850)');
    add_cfg('cms.re.activity.nudge_days.response_being_prepared', '5',
            'Days a record may sit at Response Being Prepared before nudging (UST850)');
    add_cfg('cms.re.activity.nudge_enabled', 'true',
            'Master switch for the stuck-record nudge sweep (UST850)');
    add_cfg('cms.re.activity.overdue_escalation_enabled', 'true',
            'Master switch for the overdue flip and its escalation notification (UST849)');
    add_cfg('cms.re.activity.sweep_batch_size', '500',
            'Maximum records examined per nudge sweep run (UST850)');
    COMMIT;
END;
/
