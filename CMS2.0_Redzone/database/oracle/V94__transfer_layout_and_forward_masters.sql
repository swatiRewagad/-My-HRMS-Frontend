-- ============================================================
-- V94 — Transfer provenance/layout columns, and the three missing forwarding masters
-- Session S5 (UST556-568, 632-634, 759-761, 765-766, 770-772). Oracle counterpart of MySQL V96.
-- ============================================================
--
-- See database/V96__transfer_layout_and_forward_masters.sql for the full rationale. In brief:
--
--   * INTER_OFFICE_TRANSFERS recorded WHO and WHEN but not WHAT CHANGED. UST634/568/565 require the
--     conversion history to carry the old layout, the new layout, the approving CRPC Head and the
--     timestamp; the first two had nowhere to live. The origin module was inferred from a string prefix on
--     the destination office id, and since offices are keyed by a NUMERIC OFFICE_CODE ("013") that match
--     fell through to "RBIO" for every real office — so an RBIO->CEPC conversion recorded itself as
--     RBIO->RBIO.
--
--   * WHAT "LAYOUT" MEANS. There is no pre-existing layout concept in this codebase ("layout" appears only
--     as i18n keys for page chrome and two Bengaluru locality names). Ruling: layout = the complaint's
--     OWNING MODULE (RBIO | CEPC | CEPD), i.e. the DEPARTMENT column, which is what selects the field set
--     and the workflow vocabulary. Stored as an explicit from/to pair rather than re-derived, because a
--     derivation cannot be evidenced after the fact and this particular one was provably wrong.
--
--   * ASSIGNED_OFFICER on the transfer row: approval previously wrote the destination OFFICE CODE into
--     COMPLAINTS.ASSIGNED_OFFICER — a non-user value in a user column — so "who owns this now" had no
--     answer after a transfer.
--
--   * OVERFLOW_ACCEPTED: OfficeRoutingService.incrementOffice now returns FALSE when the destination is at
--     capacity, and its only production call site discarded that verdict, so a transfer could silently
--     overfill an office. The transfer is now refused; this column records a deliberate approver override
--     so an over-capacity office is auditable rather than invisible.
--
--   * REGULATORY_BODY_MASTER did not exist in ANY form — the frontend called an endpoint no controller
--     implements and swallowed the 404 into an empty dropdown, while the screen claimed "Only bodies from
--     the validated master list can be selected". EMAIL_VERIFIED is the point of UST766 and every seeded
--     body starts 'N': seeding 'Y' would assert a verification nobody performed and immediately permit
--     forwarding a citizen's complaint to an unconfirmed mailbox.
--
--   * RBI_DEPARTMENT_MASTER replaces a hardcoded nine-element array (including a literal 'Other').
--     DEPARTMENT_ROUTING_MASTER cannot serve as the picker: its rows are BANKS mapped to an owning
--     department, and it has no contact column.
--
--   * CEPC offices: OFFICE_CODE_MASTER.OFFICE_TYPE is 'BO' on every row, so "the list of CEPC offices" had
--     no data source and the CEPC branch of the transfer form could not be populated. New rows use
--     OFFICE_TYPE='CEPC' and C0* codes, outside the numeric RBIO code space, so a CEPC office can never
--     collide with an OFFICE_CODE a complaint already carries. Existing 'BO' rows are untouched.
--
-- All new columns nullable (shared ddl-auto database).
-- Re-running is safe: guarded on USER_TABLES / USER_TAB_COLUMNS / USER_INDEXES, and every seed is
-- existence-checked.
-- ============================================================

DECLARE
    PROCEDURE add_table(p_table VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = p_table;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;

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
BEGIN
    -- ── 1. Transfer provenance, layout conversion and destination assignment ──
    add_column('INTER_OFFICE_TRANSFERS', 'ORIGIN_MODULE',       'VARCHAR2(20)');
    add_column('INTER_OFFICE_TRANSFERS', 'FROM_LAYOUT',         'VARCHAR2(20)');
    add_column('INTER_OFFICE_TRANSFERS', 'TO_LAYOUT',           'VARCHAR2(20)');
    add_column('INTER_OFFICE_TRANSFERS', 'ASSIGNED_OFFICER',    'VARCHAR2(200)');
    add_column('INTER_OFFICE_TRANSFERS', 'ASSIGNMENT_STRATEGY', 'VARCHAR2(40)');
    add_column('INTER_OFFICE_TRANSFERS', 'ASSIGNMENT_REASON',   'VARCHAR2(500)');
    add_column('INTER_OFFICE_TRANSFERS', 'FROM_OFFICE_CODE',    'VARCHAR2(10)');
    add_column('INTER_OFFICE_TRANSFERS', 'TO_OFFICE_CODE',      'VARCHAR2(10)');
    add_column('INTER_OFFICE_TRANSFERS', 'LANGUAGE',            'VARCHAR2(10)');
    add_column('INTER_OFFICE_TRANSFERS', 'TARGET_DEPARTMENT',   'VARCHAR2(100)');
    add_column('INTER_OFFICE_TRANSFERS', 'TARGET_BODY_ID',      'NUMBER');
    add_column('INTER_OFFICE_TRANSFERS', 'TARGET_BODY_NAME',    'VARCHAR2(250)');
    add_column('INTER_OFFICE_TRANSFERS', 'OVERFLOW_ACCEPTED',   'CHAR(1)');

    add_index('IDX_IOT_STATUS_REQUESTED',
        'CREATE INDEX IDX_IOT_STATUS_REQUESTED ON INTER_OFFICE_TRANSFERS (STATUS, REQUESTED_AT)');

    -- The RBI department a complaint was forwarded to. Its own column rather than ASSIGNED_OFFICER, which is
    -- what the CEPC forward arms use — that leaves a department NAME in a user column.
    add_column('COMPLAINTS', 'FORWARDED_TO_DEPARTMENT', 'VARCHAR2(100)');

    -- ── 2. REGULATORY_BODY_MASTER (UST766) ──
    add_table('REGULATORY_BODY_MASTER',
        'CREATE TABLE REGULATORY_BODY_MASTER ('
        || ' ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,'
        || ' BODY_CODE VARCHAR2(30) NOT NULL,'
        || ' BODY_NAME VARCHAR2(250) NOT NULL,'
        || ' CONTACT_EMAIL VARCHAR2(320),'
        || ' EMAIL_VERIFIED CHAR(1),'
        || ' VERIFIED_BY VARCHAR2(200),'
        || ' VERIFIED_AT TIMESTAMP,'
        || ' CONTACT_PHONE VARCHAR2(30),'
        || ' ADDRESS VARCHAR2(500),'
        || ' JURISDICTION VARCHAR2(250),'
        || ' IS_ACTIVE CHAR(1),'
        || ' CREATED_BY VARCHAR2(100),'
        || ' CREATED_AT TIMESTAMP,'
        || ' UPDATED_AT TIMESTAMP)');

    add_index('IDX_REG_BODY_CODE',
        'CREATE INDEX IDX_REG_BODY_CODE ON REGULATORY_BODY_MASTER (BODY_CODE)');
    add_index('IDX_REG_BODY_ACTIVE',
        'CREATE INDEX IDX_REG_BODY_ACTIVE ON REGULATORY_BODY_MASTER (IS_ACTIVE)');

    -- ── 3. RBI_DEPARTMENT_MASTER (UST761/534/527-528) ──
    add_table('RBI_DEPARTMENT_MASTER',
        'CREATE TABLE RBI_DEPARTMENT_MASTER ('
        || ' ID NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,'
        || ' DEPT_CODE VARCHAR2(30) NOT NULL,'
        || ' DEPT_NAME VARCHAR2(250) NOT NULL,'
        || ' CONTACT_EMAIL VARCHAR2(320),'
        || ' ASSIGN_ROLE_GROUP VARCHAR2(50),'
        || ' DESCRIPTION VARCHAR2(500),'
        || ' IS_ACTIVE CHAR(1),'
        || ' DISPLAY_ORDER NUMBER(10),'
        || ' CREATED_BY VARCHAR2(100),'
        || ' CREATED_AT TIMESTAMP)');

    add_index('IDX_RBI_DEPT_CODE',
        'CREATE INDEX IDX_RBI_DEPT_CODE ON RBI_DEPARTMENT_MASTER (DEPT_CODE)');
    add_index('IDX_RBI_DEPT_ACTIVE',
        'CREATE INDEX IDX_RBI_DEPT_ACTIVE ON RBI_DEPARTMENT_MASTER (IS_ACTIVE)');

    COMMIT;
END;
/

-- ─────────────────────────────────────────────────────────────
-- Seed data (existence-checked individually so a partial previous run completes cleanly)
-- ─────────────────────────────────────────────────────────────
DECLARE
    PROCEDURE add_body(p_code VARCHAR2, p_name VARCHAR2, p_jurisdiction VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM REGULATORY_BODY_MASTER WHERE BODY_CODE = p_code;
        IF v_count = 0 THEN
            -- EMAIL_VERIFIED = 'N' deliberately; see the header.
            INSERT INTO REGULATORY_BODY_MASTER
                (BODY_CODE, BODY_NAME, CONTACT_EMAIL, EMAIL_VERIFIED, JURISDICTION, IS_ACTIVE,
                 CREATED_BY, CREATED_AT)
            VALUES (p_code, p_name, NULL, 'N', p_jurisdiction, 'Y', 'V94_migration', SYSTIMESTAMP);
        END IF;
    END;

    PROCEDURE add_dept(p_code VARCHAR2, p_name VARCHAR2, p_order NUMBER) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM RBI_DEPARTMENT_MASTER WHERE DEPT_CODE = p_code;
        IF v_count = 0 THEN
            INSERT INTO RBI_DEPARTMENT_MASTER
                (DEPT_CODE, DEPT_NAME, ASSIGN_ROLE_GROUP, IS_ACTIVE, DISPLAY_ORDER, CREATED_BY, CREATED_AT)
            VALUES (p_code, p_name, 'CRPC_DEO', 'Y', p_order, 'V94_migration', SYSTIMESTAMP);
        END IF;
    END;

    PROCEDURE add_office(p_code VARCHAR2, p_name VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM OFFICE_CODE_MASTER WHERE OFFICE_CODE = p_code;
        IF v_count = 0 THEN
            INSERT INTO OFFICE_CODE_MASTER (OFFICE_CODE, OFFICE_NAME, OFFICE_TYPE, IS_ACTIVE)
            VALUES (p_code, p_name, 'CEPC', 1);
        END IF;
    END;

    PROCEDURE add_config(p_key VARCHAR2, p_value VARCHAR2, p_description VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_count = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_description, 'V94_migration', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    add_body('SEBI',  'Securities and Exchange Board of India',
             'Securities markets, listed companies, mutual funds');
    add_body('IRDAI', 'Insurance Regulatory and Development Authority of India',
             'Insurance policies, claims and intermediaries');
    add_body('PFRDA', 'Pension Fund Regulatory and Development Authority',
             'National Pension System and pension funds');
    add_body('NHB',   'National Housing Bank', 'Housing finance companies');

    add_dept('DOS',  'Department of Supervision', 10);
    add_dept('DOR',  'Department of Regulation', 20);
    add_dept('CEPD', 'Consumer Education and Protection Department', 30);
    add_dept('DPSS', 'Department of Payment and Settlement Systems', 40);
    add_dept('FIDD', 'Financial Inclusion and Development Department', 50);
    add_dept('DCM',  'Department of Currency Management', 60);
    add_dept('FED',  'Foreign Exchange Department', 70);
    add_dept('DOA',  'Department of Audit', 80);

    -- ── 4. CEPC offices become addressable (UST556/563) ──
    add_office('C01', 'CEPC Mumbai');
    add_office('C02', 'CEPC New Delhi');
    add_office('C03', 'CEPC Chennai');
    add_office('C04', 'CEPC Kolkata');
    add_office('C05', 'CEPC Chandigarh');

    -- Capacity rows for the new CEPC offices, so a transfer INTO one faces the same threshold test as a
    -- transfer into an RBIO office. Without a row, routeToOffice returns NOT_FOUND and refuses — which
    -- fails closed correctly, but would make every CEPC destination permanently unusable.
    INSERT INTO OFFICE_THRESHOLD_CONFIG
        (OFFICE_ID, OFFICE_NAME, DEPARTMENT, MAX_THRESHOLD, CURRENT_COUNT, OVERFLOW_SEQUENCE_ORDER, ACTIVE)
    SELECT o.OFFICE_CODE, o.OFFICE_NAME, 'CEPC', 500, 0, 900, 1
      FROM OFFICE_CODE_MASTER o
     WHERE o.OFFICE_TYPE = 'CEPC'
       AND NOT EXISTS (SELECT 1 FROM OFFICE_THRESHOLD_CONFIG t WHERE t.OFFICE_ID = o.OFFICE_CODE);

    -- ── 5. Configuration ──
    -- Default false: capacity exists to be respected, and silently discarding the capacity verdict is the
    -- defect being fixed. When an administrator permits the override it is recorded on the transfer row.
    add_config('transfer.allow_over_capacity_override', 'false',
        'Whether a CRPC Head may approve a transfer into an office at its threshold. Recorded on the transfer row when used.');

    -- Default true, deliberately: UST766 restricts forwarding to bodies with VERIFIED email ids, and every
    -- seeded body starts unverified.
    add_config('forward.require_verified_body_email', 'true',
        'Refuse a forward to a regulatory body whose contact email is not verified (UST766).');

    COMMIT;
END;
/
