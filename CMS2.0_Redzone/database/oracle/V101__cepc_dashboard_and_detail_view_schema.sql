-- ============================================================
-- V101 — CEPC dashboard + complaint detail view: the six tables that had no DDL, and the two
--        tables the detail view outgrew
--
-- Oracle counterpart of MySQL V103. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   The CEPC dashboard and the six detail-view tabs were built against tables that exist in the dev and
--   Oracle-dev databases ONLY because those profiles run ddl-auto=update. Nothing in database/ creates
--   them. Production runs ddl-auto=validate, so on a prod deployment the application does not start with
--   a missing table — it fails validation at boot, which is correct behaviour and exactly why this file
--   has to exist.
--
--   V100 already added seven draft columns to CEPC_COMPLAINT_ASSESSMENT and noted, in its own header,
--   that the table it was altering had no CREATE TABLE anywhere. This is that CREATE TABLE, plus the
--   five others in the same position.
--
-- ORDERING INTERACTION WITH V100 — read before editing either file
--
--   V100 runs BEFORE this file. On a fresh database the table does not exist when V100 runs, so V100
--   takes its documented 'absent - skipping' branch and adds nothing. That is why the CREATE TABLE below
--   includes V100's seven columns rather than leaving them to V100: on a fresh install nobody else adds
--   them. On an existing Oracle-dev database V100 has already added them and this CREATE TABLE does not
--   run at all. Both paths converge on the same shape.
--
--   Consequence: a column added to this table in future must go in BOTH files, or a fresh install and an
--   upgraded install end up with different schemas. The same note appears in MySQL V103.
--
-- ORACLE-SPECIFIC NOTES
--
--   TEXT is not an Oracle type. The entities pin columnDefinition = "TEXT" for the MySQL dialect's sake,
--   which would fail loudly as Oracle DDL — safe in practice only because the Oracle profiles never run
--   ddl-auto=update, and the reason these columns must exist here as real DDL. They are CLOB here.
--
--   BIT(1) is likewise not an Oracle type. Hibernate maps a Java Boolean to NUMBER(1) under this
--   dialect, so the nullable three-valued flags below are NUMBER(1) and NOT CHAR(1) 'Y'/'N' — the two
--   are not interchangeable and a CHAR column would make every boolean read back as a string. The Y/N
--   columns that ARE CHAR(1) are the ones whose entities declare them as String; they are called out
--   individually.
--
--   MySQL's row-size argument for using TEXT over VARCHAR does not apply here (Oracle has no
--   65535-byte row limit), but the columns stay CLOB so both trees agree with the one entity mapping.
--
-- Guarded on USER_TABLES / USER_TAB_COLUMNS / USER_INDEXES so a re-run is safe.
--
--   Those guards compare TABLE_NAME against bare uppercase literals, which is correct HERE and must not
--   be "fixed" to match MySQL V103. Every identifier in this file is unquoted, so Oracle folds it to
--   uppercase before storing it, and the two pre-existing tables sections 2 and 3 alter were likewise
--   created unquoted (NODAL_OFFICER_RECORDS in V28/V5, SIMULATED_EMAILS in V1/V4/V5). The data
--   dictionary therefore holds exactly the spelling the literals use. V103 has to wrap the same lookups
--   in UPPER() for the opposite reason: Hibernate folds @Table names DOWN to lowercase on MySQL, and
--   information_schema.TABLES.TABLE_NAME is utf8mb3_bin, so a bare comparison there matches nothing.
--   Same code shape, opposite folding direction — do not copy either file's guard into the other.
-- ============================================================

-- ============================================================
-- 1. The six missing tables
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

    PROCEDURE add_index(p_index VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_index;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;
BEGIN
    -- ─────────────────────────────────────────────────────────
    -- CEPC_DASHBOARD_FILTER — the dashboard's status / tab / KPI vocabulary
    --
    -- Deliberately NOT rows in RBIO_STATUS_MASTER: RbioStatusMasterSeeder.seedVisibility() does findAll()
    -- then grants ADMIN every code it finds, so CEPC codes added there would surface in the RBIO admin
    -- filter bar. Separate table, separate vocabulary.
    --
    -- No seed rows here. CepcDashboardFilterSeeder carries no @Profile, so it runs in every environment
    -- including production and inserts the filter rows if-absent on every boot. Duplicating them here
    -- would mean two sources for one vocabulary, and UK_CEPC_FILTER_DIM_CODE would turn a drift into a
    -- failed migration rather than a mere disagreement. The table is created empty on purpose.
    --
    -- IS_ACTIVE and PENDING_ONLY are CHAR(1) 'Y'/'N' and not NUMBER(1): their entity fields are Strings,
    -- matching the Y/N convention of the other master tables in this schema.
    -- ─────────────────────────────────────────────────────────
    add_table('CEPC_DASHBOARD_FILTER', '
        CREATE TABLE CEPC_DASHBOARD_FILTER (
            ID               NUMBER GENERATED BY DEFAULT AS IDENTITY,
            DIMENSION        VARCHAR2(10)   NOT NULL,
            FILTER_CODE      VARCHAR2(80)   NOT NULL,
            LABEL_EN         VARCHAR2(150)  NOT NULL,
            TRANSLATION_KEY  VARCHAR2(150),
            PREDICATE_KIND   VARCHAR2(30)   NOT NULL,
            PREDICATE_VALUES VARCHAR2(1000),
            WINDOW_FROM_DAYS NUMBER,
            WINDOW_TO_DAYS   NUMBER,
            PENDING_ONLY     CHAR(1)        DEFAULT ''N'' NOT NULL,
            VISIBLE_TO_ROLES VARCHAR2(1000),
            DISPLAY_ORDER    NUMBER         DEFAULT 0 NOT NULL,
            IS_ACTIVE        CHAR(1)        DEFAULT ''Y'' NOT NULL,
            CREATED_AT       TIMESTAMP,
            CONSTRAINT PK_CEPC_DASHBOARD_FILTER PRIMARY KEY (ID),
            -- Scoped to the dimension, not global: ''All'' is a legitimate code as both a TAB and a
            -- STATUS, and a single-column unique key would reject the second one.
            CONSTRAINT UK_CEPC_FILTER_DIM_CODE UNIQUE (DIMENSION, FILTER_CODE)
        )');

    -- ─────────────────────────────────────────────────────────
    -- COMPLAINT_READ_STATE — per-user read/unread
    --
    -- A row means "this user has read this complaint"; absence means unread. Per USER, not per complaint:
    -- a boolean column on COMPLAINTS would make one officer opening a complaint mark it read for the
    -- whole office, and the previous implementation's localStorage made it read only on that one
    -- browser. Both were wrong in the same direction.
    --
    -- No FK to COMPLAINTS. The unread predicate is a correlated NOT EXISTS over this table on every
    -- dashboard query, and the enforcement a FK would add is not worth constraining the delete path of
    -- the busiest table in the schema.
    -- ─────────────────────────────────────────────────────────
    add_table('COMPLAINT_READ_STATE', '
        CREATE TABLE COMPLAINT_READ_STATE (
            ID            NUMBER GENERATED BY DEFAULT AS IDENTITY,
            COMPLAINT_ID  NUMBER(19)    NOT NULL,
            USER_ID       VARCHAR2(200) NOT NULL,
            FIRST_READ_AT TIMESTAMP,
            LAST_READ_AT  TIMESTAMP,
            CONSTRAINT PK_COMPLAINT_READ_STATE PRIMARY KEY (ID),
            -- This is what makes POST /complaints/{id}/read idempotent under concurrent tabs.
            CONSTRAINT UK_COMPLAINT_READ_STATE UNIQUE (COMPLAINT_ID, USER_ID)
        )');

    -- The NOT EXISTS subquery filters on USER_ID first.
    add_index('IDX_COMPLAINT_READ_USER',
        'CREATE INDEX IDX_COMPLAINT_READ_USER ON COMPLAINT_READ_STATE (USER_ID)');

    -- ─────────────────────────────────────────────────────────
    -- CEPC_COMPLAINT_ASSESSMENT — the Summary and Final Decision tabs
    --
    -- A side table rather than columns on COMPLAINTS, for a specific reason: COMPLAINTS carries
    -- @Version RECORD_VERSION, so an officer saving the Summary tab would take an optimistic-lock failure
    -- against any concurrent workflow transition on the same complaint — a routing event would discard
    -- the officer''s unsaved assessment. One row per complaint, every column nullable, no @Version.
    --
    -- Note the deliberately odd REPLY_WITHIN30DAYS: Hibernate''s implicit naming strategy splits
    -- `replyWithin30Days` there, and the name is kept because it is what the running databases have.
    -- The same applies to NOTICE131COMPLY_DATE on NODAL_OFFICER_RECORDS in section 2.
    -- ─────────────────────────────────────────────────────────
    add_table('CEPC_COMPLAINT_ASSESSMENT', '
        CREATE TABLE CEPC_COMPLAINT_ASSESSMENT (
            ID                                       NUMBER GENERATED BY DEFAULT AS IDENTITY,
            COMPLAINT_NUMBER                         VARCHAR2(100)  NOT NULL,

            OFFICER_COMMENTS                         VARCHAR2(2000),
            ADDITIONAL_COMMENTS                      VARCHAR2(2000),

            COMPLAINT_CPGRAM                         NUMBER(1),
            CPGRAM_NUMBER                            VARCHAR2(60),

            REGULATED_ENTITY_ID                      NUMBER(19),
            ENTITY_NAME                              VARCHAR2(250),
            OTHER_ENTITY_NAME                        VARCHAR2(250),
            ENTITY_CATEGORY                          VARCHAR2(120),
            MODULE_NAME                              VARCHAR2(120),
            BSR_CODE                                 VARCHAR2(30),
            ENTITY_BRANCH_NAME                       VARCHAR2(200),
            ENTITY_BRANCH_CATEGORY                   VARCHAR2(120),
            BRANCH_CENTER_NAME                       VARCHAR2(200),
            ENTITY_ADDRESS                           VARCHAR2(500),
            ENTITY_CITY                              VARCHAR2(120),
            ENTITY_DISTRICT                          VARCHAR2(120),
            ENTITY_STATE                             VARCHAR2(80),
            ENTITY_COUNTRY                           VARCHAR2(80),
            ENTITY_PINCODE                           VARCHAR2(10),
            REGISTRATION_WITH_RBI_DATE               DATE,

            COMPLAINT_CATEGORY_TEXT                  VARCHAR2(200),
            COMPLAINT_SUB_CATEGORY1                  VARCHAR2(200),
            COMPLAINT_SUB_CATEGORY2                  VARCHAR2(200),
            PROPOSED_COMPLAINT_TYPE                  VARCHAR2(60),
            SCHEME_FLAG                              VARCHAR2(60),
            GROUNDS_FLAG                             VARCHAR2(60),
            RBO_CGPC_OLD                             VARCHAR2(60),
            VERNACULAR_LANGUAGE                      VARCHAR2(60),

            COMPLAINT_REGISTRATION_DATE_VALID        NUMBER(1),
            DATE_OF_FILING_COMPLAINT                 DATE,
            REMINDER_SENT                            NUMBER(1),
            REPLY_WITHIN30DAYS                       VARCHAR2(60),
            LEGAL_CASE_FILED                         NUMBER(1),
            PRE_ENQUIRY_RECEIVED                     NUMBER(1),
            HIGH_PRIORITY_COMPLAINT                  NUMBER(1),
            FREE_MARKED_COMPLAINT                    NUMBER(1),
            COMPLAINT_REGARDING_PENSION              NUMBER(1),
            COMPLAINT_AGAINST_BUSINESS_CORRESPONDENT NUMBER(1),
            ATM_CREDIT_DEBIT_CARD                    NUMBER(1),

            -- NUMBER(15,2), not BINARY_DOUBLE: these feed closure MIS and a rounding artefact in a
            -- compensation figure is a defect, not a display quirk. COMPENSATION_SOUGHT is an integer
            -- because the client sends the Yes/No radio pair as 0/1 on that one field.
            DISPUTED_AMOUNT                          NUMBER(15,2),
            COMPENSATION_SOUGHT                      NUMBER(10),
            LOAN_DISPOSAL_AMOUNT                     NUMBER(15,2),
            COMPENSATION_LOSS                        NUMBER(15,2),
            COMPENSATION_MENTAL                      NUMBER(15,2),

            -- CLOB and not VARCHAR2: an officer''s speaking order is not reliably shorter than 4000
            -- characters, which is VARCHAR2''s ceiling in the default MAX_STRING_SIZE=STANDARD mode.
            GIST_OF_CASE                             CLOB,
            GIST_OF_CASE_REGIONAL                    CLOB,
            SPEAKING_ORDER_GENERATED                 NUMBER(1),
            SPEAKING_ORDER_CONTENT                   CLOB,
            COMPLAINT_STATUS_ON_PORTAL               VARCHAR2(60),
            SYSTEMIC_ISSUE                           VARCHAR2(500),
            ADVISORY_COMPLIANCE_DATE                 DATE,
            AWARD_ACCEPTANCE_DATE                    DATE,
            CRPC_PROPOSED_ACTION                     VARCHAR2(60),
            PROPOSED_CLAUSE                          VARCHAR2(60),

            -- V100''s seven. Present here because on a fresh database V100 runs first, finds no table and
            -- skips; see the ORDERING INTERACTION note in this file''s header.
            FINAL_DECISION_ACTION                    VARCHAR2(40),
            REJECT_WITHDRAW_SETTLE_SUB_ACTION        VARCHAR2(40),
            REJECT_WITHDRAW_SETTLE_REASON            CLOB,
            CLOSURE_CLAUSE_DESCRIPTION               CLOB,
            -- Distinct from COMPLAINTS.CLOSURE_CLAUSE, which the terminal workflow arm writes. A draft
            -- save must not overwrite a clause the closure already committed.
            CLOSURE_CLAUSE_DRAFT                     VARCHAR2(60),
            -- Distinct from COMPLAINTS.AWARD_IMPLEMENTED_DATE, which records when the RE actually
            -- complied. This is the date the award sets for them to do it by.
            AWARD_IMPLEMENTATION_DATE                DATE,
            FORWARD_DRAFT_JSON                       CLOB,

            CREATED_AT                               TIMESTAMP,
            LAST_MODIFIED_AT                         TIMESTAMP,
            LAST_MODIFIED_BY                         VARCHAR2(100),

            CONSTRAINT PK_CEPC_COMPLAINT_ASSESSMENT PRIMARY KEY (ID),
            -- One assessment per complaint. Without this a double-submit from the Summary tab creates a
            -- second row and the read side silently picks one of the two.
            CONSTRAINT UK_CEPC_ASSESSMENT_COMPLAINT UNIQUE (COMPLAINT_NUMBER)
        )');

    -- ─────────────────────────────────────────────────────────
    -- COMPLAINT_ELIGIBILITY_ANSWERS — the Summary tab''s maintainability block
    --
    -- Standalone, and NOT keyed off ELIGIBILITY_QUESTION_MASTER. That master serves the CITIZEN filing
    -- wizard''s 14 questions; the officer panel asks a different set of 20, two of which are answered
    -- with a DATE rather than yes/no. Keying to the master would force the officer set into the citizen
    -- vocabulary. QUESTION_KEY is the camelCase key the officer panel uses, stored verbatim.
    --
    -- BOOLEAN_ANSWER is nullable and an absent row is distinct from a 0 one: unanswered must not read
    -- back as No, because a false negative on maintainability is how a complaint gets wrongly rejected.
    -- ─────────────────────────────────────────────────────────
    add_table('COMPLAINT_ELIGIBILITY_ANSWERS', '
        CREATE TABLE COMPLAINT_ELIGIBILITY_ANSWERS (
            ID               NUMBER GENERATED BY DEFAULT AS IDENTITY,
            COMPLAINT_NUMBER VARCHAR2(100) NOT NULL,
            QUESTION_KEY     VARCHAR2(100) NOT NULL,
            BOOLEAN_ANSWER   NUMBER(1),
            DATE_ANSWER      DATE,
            CONSTRAINT PK_COMPLAINT_ELIGIBILITY_ANSW PRIMARY KEY (ID),
            -- One answer per question per complaint; makes the PUT an upsert rather than an append.
            CONSTRAINT UK_ELIGIBILITY_ANSWER_QUESTION UNIQUE (COMPLAINT_NUMBER, QUESTION_KEY)
        )');

    add_index('IDX_ELIGIBILITY_ANSWER_COMPLAINT',
        'CREATE INDEX IDX_ELIGIBILITY_ANSWER_COMPLAINT
             ON COMPLAINT_ELIGIBILITY_ANSWERS (COMPLAINT_NUMBER)');

    -- ─────────────────────────────────────────────────────────
    -- CEPC_CONCILIATION_MEETINGS — the Conciliation tab
    --
    -- Deliberately NOT RBIO_MEETING. That entity is event-sourced: its columns are updatable=false and
    -- history is expressed through SUPERSEDED_AT / SUPERSEDED_BY_ID. The CEPC tab is an editable form,
    -- and reusing RBIO_MEETING would have meant either trading away that audit guarantee for the RBIO
    -- ladder or writing a superseding row on every save. Mutable rows here, with SEQUENCE_NO carrying
    -- the reschedule history instead.
    --
    -- No unique key on (COMPLAINT_NUMBER, SEQUENCE_NO): a complaint legitimately has several meetings
    -- and the service, not the schema, owns which one is current (the single OPEN one, status SCHEDULED
    -- or RESCHEDULED).
    -- ─────────────────────────────────────────────────────────
    add_table('CEPC_CONCILIATION_MEETINGS', '
        CREATE TABLE CEPC_CONCILIATION_MEETINGS (
            ID                      NUMBER GENERATED BY DEFAULT AS IDENTITY,
            COMPLAINT_NUMBER        VARCHAR2(100) NOT NULL,
            SEQUENCE_NO             NUMBER,
            -- SCHEDULED | RESCHEDULED | COMPLETED | CANCELLED. The first two are the OPEN pair the
            -- dashboard''s Meeting Scheduled tab counts.
            MEETING_STATUS          VARCHAR2(30)  NOT NULL,
            MEETING_DATE            DATE,
            -- HH:mm as text, not an interval: the form offers a fixed set of slot labels and storing
            -- them as a time type would invent a precision the officer never entered.
            MEETING_TIME            VARCHAR2(10),
            -- Nullable on purpose — three-valued. NULL means the party has not responded to the
            -- invitation, which is not the same as having declined it.
            ACCEPTED_BY_COMPLAINANT NUMBER(1),
            ACCEPTED_BY_ENTITY      NUMBER(1),
            CONDUCTED_THROUGH_VC    NUMBER(1),
            -- Two separate narratives: the minutes of the meeting, and the officer''s own note.
            MEETING_COMMENTS        VARCHAR2(4000),
            COMMENTS                VARCHAR2(4000),
            CREATED_BY              VARCHAR2(100),
            CREATED_AT              TIMESTAMP,
            UPDATED_BY              VARCHAR2(100),
            UPDATED_AT              TIMESTAMP,
            CONSTRAINT PK_CEPC_CONCILIATION_MEETINGS PRIMARY KEY (ID)
        )');

    add_index('IDX_CEPC_CONCILIATION_COMPLAINT',
        'CREATE INDEX IDX_CEPC_CONCILIATION_COMPLAINT
             ON CEPC_CONCILIATION_MEETINGS (COMPLAINT_NUMBER)');

    add_index('IDX_CEPC_CONCILIATION_SEQUENCE',
        'CREATE INDEX IDX_CEPC_CONCILIATION_SEQUENCE
             ON CEPC_CONCILIATION_MEETINGS (COMPLAINT_NUMBER, SEQUENCE_NO)');

    -- ─────────────────────────────────────────────────────────
    -- COMPLAINT_COMMENTS — officer notes, on complaints and on nodal records
    --
    -- One table for both kinds, discriminated by NO_RECORD_NUMBER and TARGET, carried over from the
    -- CMS2.0 implementation. NO_RECORD_NUMBER NULL means the note is on the complaint; set means it is
    -- on that nodal record, and TARGET (''NO'' or ''PNO'') says which officer it concerns. A reader that
    -- ignores those two columns shows an officer''s note about the nodal officer as a note on the case,
    -- which is why IDX_COMMENT_NO_RECORD exists alongside IDX_COMMENT_COMPLAINT.
    --
    -- AUTHOR is the display name and AUTHOR_USER_ID the resolved principal; both are kept because the
    -- display name is what the UI renders and the user id is what an audit needs, and a rename in
    -- Keycloak must not rewrite history.
    --
    -- TEXT is a column name, not the MySQL type — it is not reserved in Oracle, but it is quoted-free
    -- here deliberately so the two trees keep the same identifier.
    -- ─────────────────────────────────────────────────────────
    add_table('COMPLAINT_COMMENTS', '
        CREATE TABLE COMPLAINT_COMMENTS (
            ID               NUMBER GENERATED BY DEFAULT AS IDENTITY,
            COMPLAINT_NUMBER VARCHAR2(100)  NOT NULL,
            AUTHOR           VARCHAR2(100)  NOT NULL,
            AUTHOR_USER_ID   VARCHAR2(100),
            INITIALS         VARCHAR2(10),
            TEXT             VARCHAR2(2000) NOT NULL,
            ROLE             VARCHAR2(50),
            -- The avatar chip colour, as a CSS value.
            COLOR            VARCHAR2(10),
            NO_RECORD_NUMBER VARCHAR2(50),
            TARGET           VARCHAR2(10),
            CREATED_AT       TIMESTAMP,
            CONSTRAINT PK_COMPLAINT_COMMENTS PRIMARY KEY (ID)
        )');

    add_index('IDX_COMMENT_COMPLAINT',
        'CREATE INDEX IDX_COMMENT_COMPLAINT ON COMPLAINT_COMMENTS (COMPLAINT_NUMBER)');

    add_index('IDX_COMMENT_NO_RECORD',
        'CREATE INDEX IDX_COMMENT_NO_RECORD ON COMPLAINT_COMMENTS (NO_RECORD_NUMBER)');
END;
/

-- ============================================================
-- 2. NODAL_OFFICER_RECORDS — the 17 columns the Contact Entity tab added
--
-- V28 created this table for the RE-reassignment screen; V79 added PROCESSING_OFFICE. The CEPC Contact
-- Entity tab projects a 30-field record, and these 17 are the difference. Every one is nullable:
-- existing rows predate the tab and there is no value to backfill them with that would not be an
-- invention.
--
-- RECORD_NUMBER is the important one. CepcNodalRecordService.forwardToRe resolves its target with
-- findByRecordNumber(...), so a record with a null RECORD_NUMBER cannot be addressed by
-- POST /nodal-records/{rn}/forward-to-re at all — the row is visible in the list and unusable.
-- ============================================================

DECLARE
    v_table NUMBER;

    PROCEDURE add_column(p_name VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'NODAL_OFFICER_RECORDS' AND COLUMN_NAME = p_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE NODAL_OFFICER_RECORDS ADD ('
                              || p_name || ' ' || p_type || ')';
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
    SELECT COUNT(*) INTO v_table FROM USER_TABLES WHERE TABLE_NAME = 'NODAL_OFFICER_RECORDS';

    IF v_table = 0 THEN
        DBMS_OUTPUT.PUT_LINE('NODAL_OFFICER_RECORDS absent - skipping');
    ELSE
        -- The address forward-to-re and the nodal-record comment routes use for a record.
        add_column('RECORD_NUMBER',             'VARCHAR2(60)');

        -- Principal Nodal Officer contact detail. The NO's own EMAIL and PHONE already exist from V28;
        -- the PNO is a separate person and the escalation path needs both.
        add_column('PNO_EMAIL',                 'VARCHAR2(200)');
        add_column('PNO_MOBILE',                'VARCHAR2(20)');

        -- Distinct from V79's PROCESSING_OFFICE: that is the office handling the complaint, this is the
        -- entity's own designated office for grievance correspondence.
        add_column('DESIGNATED_OFFICE',         'VARCHAR2(100)');

        -- The 13(1) response window, and which communication set it.
        add_column('RE_RESPONSE_DEADLINE',      'DATE');
        add_column('DEADLINE_COMMUNICATION',    'VARCHAR2(50)');
        add_column('SLA_DAYS',                  'NUMBER(10)');

        -- Also on CEPC_COMPLAINT_ASSESSMENT, and deliberately duplicated: the tab shows the module on
        -- the nodal-record row itself, and a record can be raised against an entity module before an
        -- assessment row exists.
        add_column('MODULE_NAME',               'VARCHAR2(120)');

        -- VARCHAR2 Y/N rather than NUMBER(1): its entity field is a String, and the tab renders the
        -- stored token directly — an unanswered record must read back as neither.
        add_column('ATM_COMPLAINT',             'VARCHAR2(10)');

        -- Money. NUMBER(15,2) for the reason given on the assessment table: these figures are reported on.
        add_column('DISPUTE_AMOUNT',            'NUMBER(15,2)');
        add_column('COMPENSATION_LOSS',         'NUMBER(15,2)');
        add_column('COMPENSATION_MENTAL',       'NUMBER(15,2)');

        -- Decision dates mirrored onto the record so the Contact Entity list can show an outcome without
        -- joining the assessment table for every row.
        add_column('ADVISORY_COMPLIANCE_DATE',  'DATE');
        add_column('AWARD_IMPLEMENTATION_DATE', 'DATE');
        add_column('AWARD_ACCEPTANCE_DATE',     'DATE');

        -- NOTICE131COMPLY_DATE, not NOTICE_131_COMPLY_DATE. Hibernate's implicit naming strategy does not
        -- insert an underscore after the digit run in `notice131ComplyDate`, so this is the name the
        -- entity resolves to. Spelling it the readable way here would leave the entity's column missing.
        add_column('NOTICE131COMPLY_DATE',      'DATE');

        -- When the 13(1) notice actually went out. Separate from RE_RESPONSE_DEADLINE because a
        -- recomputed window must not lose the original dispatch time.
        add_column('FORWARDED_TO_RE_AT',        'TIMESTAMP');

        -- RECORD_NUMBER is how forward-to-re addresses a record, so looking one up must not be a full
        -- scan. Not UNIQUE: existing rows all carry NULL, and a future backfill collision should fail the
        -- write rather than this migration. A plain index is sufficient for the lookup.
        add_index('IDX_NO_RECORD_NUMBER',
            'CREATE INDEX IDX_NO_RECORD_NUMBER ON NODAL_OFFICER_RECORDS (RECORD_NUMBER)');
    END IF;
END;
/

-- ============================================================
-- 3. SIMULATED_EMAILS — the six columns the Email Communication tab added
--
-- V75 (MySQL) / the corresponding Oracle baseline added TEMPLATE_USED and CC_RECIPIENTS for the
-- per-complaint email log. The tab needs six more: without LAST_ERROR the Retry button has nothing to
-- explain itself with, and without RETRY_COUNT there is no way to stop retrying. IN_REPLY_TO_ID is what
-- makes the list a thread rather than a flat log.
--
-- NOTE for an Oracle deployment: the SIMULATED_EMAILS in oracle/V1__complete_schema.sql is a much older
-- shape than the entity (FROM_ADDRESS rather than FROM_EMAIL, no THREAD_ID, no DIRECTION). That
-- divergence predates this work and is NOT repaired here — repairing it means renaming columns the
-- intake pipeline writes, which is a migration of its own with its own backfill. This file only adds the
-- six columns the Email Communication tab needs; an Oracle deployment must reconcile the base table
-- against com.hrms.cms.entity.SimulatedEmail before the tab will work end to end.
-- ============================================================

DECLARE
    v_table NUMBER;

    PROCEDURE add_column(p_name VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'SIMULATED_EMAILS' AND COLUMN_NAME = p_name;

        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE SIMULATED_EMAILS ADD (' || p_name || ' ' || p_type || ')';
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

    -- The thread and complaint indexes are only meaningful once the columns they cover exist, and on the
    -- old Oracle baseline they do not. Guarded rather than assumed.
    FUNCTION has_column(p_name VARCHAR2) RETURN BOOLEAN IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'SIMULATED_EMAILS' AND COLUMN_NAME = p_name;
        RETURN v_count > 0;
    END;
BEGIN
    SELECT COUNT(*) INTO v_table FROM USER_TABLES WHERE TABLE_NAME = 'SIMULATED_EMAILS';

    IF v_table = 0 THEN
        DBMS_OUTPUT.PUT_LINE('SIMULATED_EMAILS absent - skipping');
    ELSE
        -- Matches CC_RECIPIENTS' width. A comma-separated list, not a child table: these are recipients
        -- of a sent message, never queried individually.
        add_column('BCC_RECIPIENTS', 'VARCHAR2(1000)');

        -- Why the send failed, shown next to Retry. A FAILED row with no reason is not actionable: the
        -- officer cannot tell a rejected recipient domain from a transport outage.
        add_column('LAST_ERROR',     'VARCHAR2(1000)');

        -- Distinct from SENT_AT and RECEIVED_AT, which are dispatch facts. A draft is edited repeatedly
        -- before either is set, and the tab orders drafts by this.
        add_column('UPDATED_AT',     'TIMESTAMP');

        -- The officer who owns this message. An inbound reply has to reach the officer holding the
        -- complaint rather than sit in a shared tray.
        add_column('ASSIGNED_TO',    'VARCHAR2(100)');

        -- Self-reference by ID, deliberately without a FK: the parent of an inbound reply may have been
        -- purged by retention while the reply is still on the complaint file, and a FK would force the
        -- retention job to either cascade the delete or refuse it.
        add_column('IN_REPLY_TO_ID', 'NUMBER(19)');

        add_column('RETRY_COUNT',    'NUMBER(10)');

        -- The tab loads one complaint's thread at a time; without these it scans the whole log.
        IF has_column('COMPLAINT_NUMBER') THEN
            add_index('IDX_SIMULATED_EMAIL_COMPLAINT',
                'CREATE INDEX IDX_SIMULATED_EMAIL_COMPLAINT ON SIMULATED_EMAILS (COMPLAINT_NUMBER)');
        END IF;

        IF has_column('THREAD_ID') THEN
            add_index('IDX_SIMULATED_EMAIL_THREAD',
                'CREATE INDEX IDX_SIMULATED_EMAIL_THREAD ON SIMULATED_EMAILS (THREAD_ID)');
        END IF;
    END IF;
END;
/
