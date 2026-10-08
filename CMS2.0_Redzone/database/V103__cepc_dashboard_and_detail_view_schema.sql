-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V103 — CEPC dashboard + complaint detail view: the six tables that had no DDL, and the two
--        tables the detail view outgrew
--
-- WHY THIS EXISTS
--
--   The CEPC dashboard and the six detail-view tabs were built against tables that exist in the dev
--   and Oracle-dev databases ONLY because those profiles run ddl-auto=update. Nothing in database/
--   creates them. Production runs ddl-auto=validate, so on a prod deployment the application does not
--   start with a missing table — it fails validation at boot with a schema mismatch, which is the
--   correct behaviour and precisely why this file has to exist.
--
--   V102 already added seven draft columns to CEPC_COMPLAINT_ASSESSMENT and noted, in its own header,
--   that the table it was altering had no CREATE TABLE anywhere. This is that CREATE TABLE, plus the
--   five others in the same position.
--
-- ORDERING INTERACTION WITH V102 — read before editing either file
--
--   V102 runs BEFORE this file and alters CEPC_COMPLAINT_ASSESSMENT. On a fresh database the table does
--   not exist when V102 runs, so V102 takes its documented "absent - skipping" branch and adds nothing.
--   That is why the CREATE TABLE below includes V102's seven columns (FINAL_DECISION_ACTION,
--   REJECT_WITHDRAW_SETTLE_SUB_ACTION, REJECT_WITHDRAW_SETTLE_REASON, CLOSURE_CLAUSE_DESCRIPTION,
--   CLOSURE_CLAUSE_DRAFT, AWARD_IMPLEMENTATION_DATE, FORWARD_DRAFT_JSON) rather than leaving them to
--   V102: on a fresh install nobody else adds them. On an existing dev database V102 has already added
--   them and this CREATE TABLE does not run at all. Both paths converge on the same shape.
--
--   Consequence: a column added to this table in future must go in BOTH files, or a fresh install and an
--   upgraded install end up with different schemas.
--
-- WHY THE TABLE GUARDS READ information_schema INSTEAD OF USING `CREATE TABLE IF NOT EXISTS`
--
--   Every one of these six tables was created by Hibernate, which folds the entity's @Table name to
--   LOWERCASE. On a server with lower_case_table_names=0 — the Linux default, and the case on the dev
--   MySQL container — table identifiers are case-sensitive, so `CREATE TABLE IF NOT EXISTS
--   COMPLAINT_COMMENTS` does NOT match the existing `complaint_comments`: the IF NOT EXISTS is satisfied,
--   MySQL creates a SECOND table under the uppercase name, and the application keeps writing to the
--   lowercase one while reports read the empty uppercase one. That failure is silent and would be
--   extremely unpleasant to diagnose.
--
--   So the guards below ask information_schema instead — but they must ask it case-INSENSITIVELY, and
--   that is NOT the default. On MySQL 8.0 information_schema is backed by the data dictionary and
--   TABLES.TABLE_NAME carries the utf8mb3_bin collation, which is BINARY: `TABLE_NAME =
--   'NODAL_OFFICER_RECORDS'` does not match the stored `nodal_officer_records` and returns no row.
--   (Under MySQL 5.x the column was utf8_general_ci and the bare comparison did work, which is why this
--   is easy to get wrong.) A guard that compares bare therefore concludes the table is ABSENT and takes
--   whichever branch that implies — skipping the ALTERs, or running the CREATE and producing exactly the
--   duplicate uppercase table this section set out to avoid. Verified on 8.0.46: the bare form created
--   six duplicates.
--
--   Every lookup against a literal name is therefore written UPPER(TABLE_NAME) = 'THE_NAME'. The column
--   guards compare against v_table instead, which holds the server's own spelling as returned by the
--   table lookup, so those need no wrapping. This is the same argument V102 makes for its ALTERs; it
--   applies with more force to CREATE.
--
-- NO SEED ROWS FOR CEPC_DASHBOARD_FILTER
--
--   CepcDashboardFilterSeeder carries no @Profile, so it runs in every environment including production
--   and inserts the 31 filter rows if-absent on every boot. Duplicating them here would mean two sources
--   for the same vocabulary, and the unique key (DIMENSION, FILTER_CODE) would make a drifted copy fail
--   the boot rather than merely disagree. The table is created empty on purpose.
--
-- Re-runnable. Procedure prefix cepc_v103_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1. The six missing tables
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v103_create_tables;
DELIMITER //
CREATE PROCEDURE cepc_v103_create_tables()
BEGIN
    -- ── CEPC_DASHBOARD_FILTER — the dashboard's status / tab / KPI vocabulary ──────────────────
    --
    -- Deliberately NOT rows in RBIO_STATUS_MASTER: RbioStatusMasterSeeder.seedVisibility() does
    -- findAll() then grants ADMIN every code it finds, so CEPC codes added there would surface in the
    -- RBIO admin filter bar. Separate table, separate vocabulary.
    --
    -- IS_ACTIVE and PENDING_ONLY are VARCHAR(1) 'Y'/'N' rather than BIT to match the Y/N convention the
    -- rest of the master tables in this schema use (see OFFICE_CODE_MASTER, CLOSURE_CLAUSE_MASTER).
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_DASHBOARD_FILTER') THEN
        CREATE TABLE CEPC_DASHBOARD_FILTER (
            ID                BIGINT        NOT NULL AUTO_INCREMENT,
            -- STATUS | TAB | KPI. One table for all three because they share a predicate language.
            DIMENSION         VARCHAR(10)   NOT NULL,
            FILTER_CODE       VARCHAR(80)   NOT NULL,
            LABEL_EN          VARCHAR(150)  NOT NULL,
            TRANSLATION_KEY   VARCHAR(150)  NULL,
            -- How PREDICATE_VALUES is interpreted. An unrecognised kind must fail CLOSED (no rows), not
            -- open: returning every complaint for a filter the code does not understand is how a
            -- restricted worklist leaks.
            PREDICATE_KIND    VARCHAR(30)   NOT NULL,
            PREDICATE_VALUES  VARCHAR(1000) NULL,
            WINDOW_FROM_DAYS  INT           NULL,
            WINDOW_TO_DAYS    INT           NULL,
            PENDING_ONLY      VARCHAR(1)    NOT NULL DEFAULT 'N',
            VISIBLE_TO_ROLES  VARCHAR(1000) NULL,
            DISPLAY_ORDER     INT           NOT NULL DEFAULT 0,
            IS_ACTIVE         VARCHAR(1)    NOT NULL DEFAULT 'Y',
            CREATED_AT        DATETIME(6)   NULL,
            PRIMARY KEY (ID),
            -- Scoped to the dimension, not global: 'All' is a legitimate code as both a TAB and a
            -- STATUS, and a single-column unique key would reject the second one.
            UNIQUE KEY UK_CEPC_FILTER_DIM_CODE (DIMENSION, FILTER_CODE)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- ── COMPLAINT_READ_STATE — per-user read/unread ────────────────────────────────────────────
    --
    -- A row means "this user has read this complaint"; absence means unread. Per USER, not per
    -- complaint: a boolean column on COMPLAINTS would make one officer opening a complaint mark it read
    -- for the whole office, and the previous implementation's localStorage made it read only on that one
    -- browser. Both were wrong in the same direction.
    --
    -- No FK to COMPLAINTS. The unread predicate is a correlated NOT EXISTS over this table on every
    -- dashboard query, and the enforcement a FK would add is not worth constraining the delete path of
    -- the busiest table in the schema.
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'COMPLAINT_READ_STATE') THEN
        CREATE TABLE COMPLAINT_READ_STATE (
            ID            BIGINT       NOT NULL AUTO_INCREMENT,
            COMPLAINT_ID  BIGINT       NOT NULL,
            USER_ID       VARCHAR(200) NOT NULL,
            FIRST_READ_AT DATETIME(6)  NULL,
            LAST_READ_AT  DATETIME(6)  NULL,
            PRIMARY KEY (ID),
            -- This is what makes POST /complaints/{id}/read idempotent under concurrent tabs.
            UNIQUE KEY UK_COMPLAINT_READ_STATE (COMPLAINT_ID, USER_ID),
            -- The NOT EXISTS subquery filters on USER_ID first.
            KEY IDX_COMPLAINT_READ_USER (USER_ID)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- ── CEPC_COMPLAINT_ASSESSMENT — the Summary and Final Decision tabs ────────────────────────
    --
    -- A side table rather than columns on COMPLAINTS, for a specific reason: COMPLAINTS carries
    -- @Version RECORD_VERSION, so an officer saving the Summary tab would take an optimistic-lock
    -- failure against any concurrent workflow transition on the same complaint — a routing event would
    -- discard the officer's unsaved assessment. One row per complaint, every column nullable, no
    -- @Version of its own.
    --
    -- Note the deliberately odd REPLY_WITHIN30DAYS: Hibernate's implicit naming strategy splits
    -- `replyWithin30Days` there (the digit run is not a word boundary it inserts an underscore after).
    -- The name is ugly and is kept because it is what the running dev and Oracle-dev databases already
    -- have; renaming it would need a data migration for no functional gain.
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_COMPLAINT_ASSESSMENT') THEN
        CREATE TABLE CEPC_COMPLAINT_ASSESSMENT (
            ID                                       BIGINT        NOT NULL AUTO_INCREMENT,
            COMPLAINT_NUMBER                         VARCHAR(100)  NOT NULL,

            -- Officer narrative
            OFFICER_COMMENTS                         VARCHAR(2000) NULL,
            ADDITIONAL_COMMENTS                      VARCHAR(2000) NULL,

            -- CPGRAMS cross-reference
            COMPLAINT_CPGRAM                         BIT(1)        NULL,
            CPGRAM_NUMBER                            VARCHAR(60)   NULL,

            -- Entity identification. Denormalised from REGULATED_ENTITIES on purpose: the officer may
            -- correct any of these for THIS complaint without editing the master the whole office reads.
            REGULATED_ENTITY_ID                      BIGINT        NULL,
            ENTITY_NAME                              VARCHAR(250)  NULL,
            OTHER_ENTITY_NAME                        VARCHAR(250)  NULL,
            ENTITY_CATEGORY                          VARCHAR(120)  NULL,
            MODULE_NAME                              VARCHAR(120)  NULL,
            BSR_CODE                                 VARCHAR(30)   NULL,
            ENTITY_BRANCH_NAME                       VARCHAR(200)  NULL,
            ENTITY_BRANCH_CATEGORY                   VARCHAR(120)  NULL,
            BRANCH_CENTER_NAME                       VARCHAR(200)  NULL,
            ENTITY_ADDRESS                           VARCHAR(500)  NULL,
            ENTITY_CITY                              VARCHAR(120)  NULL,
            ENTITY_DISTRICT                          VARCHAR(120)  NULL,
            ENTITY_STATE                             VARCHAR(80)   NULL,
            ENTITY_COUNTRY                           VARCHAR(80)   NULL,
            ENTITY_PINCODE                           VARCHAR(10)   NULL,
            REGISTRATION_WITH_RBI_DATE               DATE          NULL,

            -- Classification
            COMPLAINT_CATEGORY_TEXT                  VARCHAR(200)  NULL,
            COMPLAINT_SUB_CATEGORY1                  VARCHAR(200)  NULL,
            COMPLAINT_SUB_CATEGORY2                  VARCHAR(200)  NULL,
            PROPOSED_COMPLAINT_TYPE                  VARCHAR(60)   NULL,
            SCHEME_FLAG                              VARCHAR(60)   NULL,
            GROUNDS_FLAG                             VARCHAR(60)   NULL,
            RBO_CGPC_OLD                             VARCHAR(60)   NULL,
            VERNACULAR_LANGUAGE                      VARCHAR(60)   NULL,

            -- Maintainability / procedural flags
            COMPLAINT_REGISTRATION_DATE_VALID        BIT(1)        NULL,
            DATE_OF_FILING_COMPLAINT                 DATE          NULL,
            REMINDER_SENT                            BIT(1)        NULL,
            REPLY_WITHIN30DAYS                       VARCHAR(60)   NULL,
            LEGAL_CASE_FILED                         BIT(1)        NULL,
            PRE_ENQUIRY_RECEIVED                     BIT(1)        NULL,
            HIGH_PRIORITY_COMPLAINT                  BIT(1)        NULL,
            FREE_MARKED_COMPLAINT                    BIT(1)        NULL,
            COMPLAINT_REGARDING_PENSION              BIT(1)        NULL,
            COMPLAINT_AGAINST_BUSINESS_CORRESPONDENT BIT(1)        NULL,
            ATM_CREDIT_DEBIT_CARD                    BIT(1)        NULL,

            -- Money. DECIMAL, not DOUBLE: these feed closure MIS and a rounding artefact in a
            -- compensation figure is a defect, not a display quirk. COMPENSATION_SOUGHT is an INT
            -- because the client sends the Yes/No radio pair as 0/1 on that one field.
            DISPUTED_AMOUNT                          DECIMAL(15,2) NULL,
            COMPENSATION_SOUGHT                      INT           NULL,
            LOAN_DISPOSAL_AMOUNT                     DECIMAL(15,2) NULL,
            COMPENSATION_LOSS                        DECIMAL(15,2) NULL,
            COMPENSATION_MENTAL                      DECIMAL(15,2) NULL,

            -- Final Decision tab. The narrative fields are TEXT and not VARCHAR for two reasons: under
            -- utf8mb4 MySQL charges a VARCHAR against the 65535-BYTE row limit at 4 bytes per character,
            -- and this table has already hit that ceiling once (three 4000-char narrative columns cost
            -- 48KB and the table stopped accepting new columns); and an officer's speaking order is not
            -- reliably shorter than 4000 characters. They are TEXT rather than @Lob because a bare
            -- `@Lob String` resolves to TINYTEXT (255 bytes) under this dialect and ddl-auto=update
            -- would silently NARROW the column, truncating the order — see V101.
            GIST_OF_CASE                             TEXT          NULL,
            GIST_OF_CASE_REGIONAL                    TEXT          NULL,
            SPEAKING_ORDER_GENERATED                 BIT(1)        NULL,
            SPEAKING_ORDER_CONTENT                   TEXT          NULL,
            COMPLAINT_STATUS_ON_PORTAL               VARCHAR(60)   NULL,
            SYSTEMIC_ISSUE                           VARCHAR(500)  NULL,
            ADVISORY_COMPLIANCE_DATE                 DATE          NULL,
            AWARD_ACCEPTANCE_DATE                    DATE          NULL,
            CRPC_PROPOSED_ACTION                     VARCHAR(60)   NULL,
            PROPOSED_CLAUSE                          VARCHAR(60)   NULL,

            -- V102's seven. Present here because on a fresh database V102 runs first, finds no table and
            -- skips; see the ORDERING INTERACTION note in this file's header.
            FINAL_DECISION_ACTION                    VARCHAR(40)   NULL,
            REJECT_WITHDRAW_SETTLE_SUB_ACTION        VARCHAR(40)   NULL,
            REJECT_WITHDRAW_SETTLE_REASON            TEXT          NULL,
            CLOSURE_CLAUSE_DESCRIPTION               TEXT          NULL,
            -- Distinct from COMPLAINTS.CLOSURE_CLAUSE, which the terminal workflow arm writes. A draft
            -- save must not overwrite a clause the closure already committed.
            CLOSURE_CLAUSE_DRAFT                     VARCHAR(60)   NULL,
            -- Distinct from COMPLAINTS.AWARD_IMPLEMENTED_DATE, which records when the RE actually
            -- complied. This is the date the award sets for them to do it by.
            AWARD_IMPLEMENTATION_DATE                DATE          NULL,
            FORWARD_DRAFT_JSON                       TEXT          NULL,

            CREATED_AT                               DATETIME(6)   NULL,
            LAST_MODIFIED_AT                         DATETIME(6)   NULL,
            LAST_MODIFIED_BY                         VARCHAR(100)  NULL,

            PRIMARY KEY (ID),
            -- One assessment per complaint. Without this a double-submit from the Summary tab creates a
            -- second row and the read side silently picks one of the two.
            UNIQUE KEY UK_CEPC_ASSESSMENT_COMPLAINT (COMPLAINT_NUMBER)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- ── COMPLAINT_ELIGIBILITY_ANSWERS — the Summary tab's maintainability block ────────────────
    --
    -- Standalone, and NOT keyed off ELIGIBILITY_QUESTION_MASTER. That master serves the CITIZEN filing
    -- wizard's 14 questions; the officer panel asks a different set of 20, and two of those are answered
    -- with a DATE rather than yes/no. Keying to the master would force the officer set into the citizen
    -- vocabulary. QUESTION_KEY is the camelCase key the officer panel uses, stored verbatim.
    --
    -- BOOLEAN_ANSWER is nullable and an absent row is distinct from a `false` one: unanswered must not
    -- read back as No, because a false negative on maintainability is how a complaint gets wrongly
    -- rejected.
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'COMPLAINT_ELIGIBILITY_ANSWERS') THEN
        CREATE TABLE COMPLAINT_ELIGIBILITY_ANSWERS (
            ID               BIGINT       NOT NULL AUTO_INCREMENT,
            COMPLAINT_NUMBER VARCHAR(100) NOT NULL,
            QUESTION_KEY     VARCHAR(100) NOT NULL,
            BOOLEAN_ANSWER   BIT(1)       NULL,
            DATE_ANSWER      DATE         NULL,
            PRIMARY KEY (ID),
            -- One answer per question per complaint; makes the PUT an upsert rather than an append.
            UNIQUE KEY UK_ELIGIBILITY_ANSWER_QUESTION (COMPLAINT_NUMBER, QUESTION_KEY),
            KEY IDX_ELIGIBILITY_ANSWER_COMPLAINT (COMPLAINT_NUMBER)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- ── CEPC_CONCILIATION_MEETINGS — the Conciliation tab ──────────────────────────────────────
    --
    -- Deliberately NOT RBIO_MEETING. That entity is event-sourced: its columns are updatable=false and
    -- history is expressed through SUPERSEDED_AT / SUPERSEDED_BY_ID. The CEPC tab is an editable form,
    -- and reusing RBIO_MEETING would have meant either trading away that audit guarantee for the RBIO
    -- ladder or writing a superseding row on every keystroke-level save. Mutable rows here, with
    -- SEQUENCE_NO carrying the reschedule history instead.
    --
    -- No unique key on (COMPLAINT_NUMBER, SEQUENCE_NO): a complaint legitimately has several meetings
    -- and the service, not the schema, owns which one is current (the single OPEN one, status
    -- SCHEDULED or RESCHEDULED).
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_CONCILIATION_MEETINGS') THEN
        CREATE TABLE CEPC_CONCILIATION_MEETINGS (
            ID                      BIGINT        NOT NULL AUTO_INCREMENT,
            COMPLAINT_NUMBER        VARCHAR(100)  NOT NULL,
            SEQUENCE_NO             INT           NULL,
            -- SCHEDULED | RESCHEDULED | COMPLETED | CANCELLED. The first two are the OPEN pair the
            -- dashboard's Meeting Scheduled tab counts.
            MEETING_STATUS          VARCHAR(30)   NOT NULL,
            MEETING_DATE            DATE          NULL,
            -- HH:mm as text, not TIME: the form offers a fixed set of slot labels and storing them as
            -- TIME would invent a precision the officer never entered.
            MEETING_TIME            VARCHAR(10)   NULL,
            -- Nullable on purpose — three-valued. NULL means the party has not responded to the
            -- invitation, which is not the same as having declined it.
            ACCEPTED_BY_COMPLAINANT BIT(1)        NULL,
            ACCEPTED_BY_ENTITY      BIT(1)        NULL,
            CONDUCTED_THROUGH_VC    BIT(1)        NULL,
            -- Two separate narratives: the minutes of the meeting, and the officer's own note.
            MEETING_COMMENTS        VARCHAR(4000) NULL,
            COMMENTS                VARCHAR(4000) NULL,
            CREATED_BY              VARCHAR(100)  NULL,
            CREATED_AT              DATETIME(6)   NULL,
            UPDATED_BY              VARCHAR(100)  NULL,
            UPDATED_AT              DATETIME(6)   NULL,
            PRIMARY KEY (ID),
            KEY IDX_CEPC_CONCILIATION_COMPLAINT (COMPLAINT_NUMBER),
            KEY IDX_CEPC_CONCILIATION_SEQUENCE (COMPLAINT_NUMBER, SEQUENCE_NO)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;

    -- ── COMPLAINT_COMMENTS — officer notes, on complaints and on nodal records ─────────────────
    --
    -- One table for both kinds, discriminated by NO_RECORD_NUMBER and TARGET, carried over from the
    -- CMS2.0 implementation. NO_RECORD_NUMBER NULL means the note is on the complaint; set means it is
    -- on that nodal record, and TARGET ('NO' or 'PNO') says which officer it concerns. A reader that
    -- ignores those two columns shows an officer's note about the nodal officer as a note on the case,
    -- which is why IDX_COMMENT_NO_RECORD exists alongside IDX_COMMENT_COMPLAINT.
    --
    -- AUTHOR is the display name and AUTHOR_USER_ID the resolved principal; both are kept because the
    -- display name is what the UI renders and the user id is what an audit needs, and a rename in
    -- Keycloak must not rewrite history.
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'COMPLAINT_COMMENTS') THEN
        CREATE TABLE COMPLAINT_COMMENTS (
            ID               BIGINT        NOT NULL AUTO_INCREMENT,
            COMPLAINT_NUMBER VARCHAR(100)  NOT NULL,
            AUTHOR           VARCHAR(100)  NOT NULL,
            AUTHOR_USER_ID   VARCHAR(100)  NULL,
            INITIALS         VARCHAR(10)   NULL,
            TEXT             VARCHAR(2000) NOT NULL,
            ROLE             VARCHAR(50)   NULL,
            -- The avatar chip colour, as a CSS value.
            COLOR            VARCHAR(10)   NULL,
            NO_RECORD_NUMBER VARCHAR(50)   NULL,
            TARGET           VARCHAR(10)   NULL,
            CREATED_AT       DATETIME(6)   NULL,
            PRIMARY KEY (ID),
            KEY IDX_COMMENT_COMPLAINT (COMPLAINT_NUMBER),
            KEY IDX_COMMENT_NO_RECORD (NO_RECORD_NUMBER)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END //
DELIMITER ;

CALL cepc_v103_create_tables();
DROP PROCEDURE IF EXISTS cepc_v103_create_tables;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2. NODAL_OFFICER_RECORDS — the 17 columns the Contact Entity tab added
--
-- V29 created this table with 12 columns for the RE-reassignment screen; V81 added PROCESSING_OFFICE.
-- The CEPC Contact Entity tab projects a 30-field record, and these 17 are the difference. Every one is
-- nullable: existing rows predate the tab and there is no value to backfill them with that would not be
-- an invention.
--
-- RECORD_NUMBER is the important one. CepcNodalRecordService.forwardToRe resolves its target with
-- findByRecordNumber(...), so a record with a null RECORD_NUMBER cannot be addressed by
-- POST /nodal-records/{rn}/forward-to-re at all — the row is visible in the list and unusable.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v103_alter_nodal_records;
DELIMITER //
CREATE PROCEDURE cepc_v103_alter_nodal_records()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    -- The table as the server actually spells it; see this file's header for why the case matters.
    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'NODAL_OFFICER_RECORDS'
     LIMIT 1;

    IF v_table IS NULL THEN
        SELECT 'NODAL_OFFICER_RECORDS absent - skipping' AS note;
    ELSE
        SET @t = v_table;

        -- The address forward-to-re and the nodal-record comment routes use for a record.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'RECORD_NUMBER') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN RECORD_NUMBER VARCHAR(60) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Principal Nodal Officer contact detail. The NO's own EMAIL and PHONE already exist from V29;
        -- the PNO is a separate person and the escalation path needs both.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'PNO_EMAIL') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN PNO_EMAIL VARCHAR(200) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'PNO_MOBILE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN PNO_MOBILE VARCHAR(20) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Distinct from V81's PROCESSING_OFFICE: that is the office handling the complaint, this is the
        -- entity's own designated office for grievance correspondence.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'DESIGNATED_OFFICE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN DESIGNATED_OFFICE VARCHAR(100) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- The 13(1) response window, and which communication set it.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'RE_RESPONSE_DEADLINE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN RE_RESPONSE_DEADLINE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'DEADLINE_COMMUNICATION') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t,
                              '` ADD COLUMN DEADLINE_COMMUNICATION VARCHAR(50) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'SLA_DAYS') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN SLA_DAYS INT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Also on CEPC_COMPLAINT_ASSESSMENT, and deliberately duplicated: the tab shows the module on
        -- the nodal-record row itself, and a record can be raised against an entity module before an
        -- assessment row exists.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'MODULE_NAME') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN MODULE_NAME VARCHAR(120) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Y/N rather than BIT: the tab renders the stored token directly and an unanswered record must
        -- read back as neither, which a BIT with a default cannot express.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'ATM_COMPLAINT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN ATM_COMPLAINT VARCHAR(10) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Money. DECIMAL for the reason given on the assessment table: these figures are reported on.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'DISPUTE_AMOUNT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN DISPUTE_AMOUNT DECIMAL(15,2) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'COMPENSATION_LOSS') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN COMPENSATION_LOSS DECIMAL(15,2) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'COMPENSATION_MENTAL') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t,
                              '` ADD COLUMN COMPENSATION_MENTAL DECIMAL(15,2) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Decision dates mirrored onto the record so the Contact Entity list can show an outcome
        -- without joining the assessment table for every row.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'ADVISORY_COMPLIANCE_DATE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN ADVISORY_COMPLIANCE_DATE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'AWARD_IMPLEMENTATION_DATE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN AWARD_IMPLEMENTATION_DATE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'AWARD_ACCEPTANCE_DATE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN AWARD_ACCEPTANCE_DATE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- NOTICE131COMPLY_DATE, not NOTICE_131_COMPLY_DATE. Hibernate's implicit naming strategy does
        -- not insert an underscore after the digit run in `notice131ComplyDate`, so this is the name the
        -- running dev database has and the name the entity resolves to. Spelling it the readable way
        -- here would create a second, unused column under ddl-auto=update.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'NOTICE131COMPLY_DATE') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN NOTICE131COMPLY_DATE DATE NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- When the 13(1) notice actually went out. Separate from RE_RESPONSE_DEADLINE because a
        -- recomputed window must not lose the original dispatch time.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'FORWARDED_TO_RE_AT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN FORWARDED_TO_RE_AT DATETIME(6) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- RECORD_NUMBER is how forward-to-re addresses a record, so looking one up must not be a table
        -- scan. Not UNIQUE: existing rows all carry NULL and MySQL permits repeated NULLs in a unique
        -- index, but a future backfill collision would then fail the migration rather than the write,
        -- and a plain index is sufficient for the lookup.
        IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND INDEX_NAME = 'IDX_NO_RECORD_NUMBER') THEN
            SET @sql = CONCAT('CREATE INDEX IDX_NO_RECORD_NUMBER ON `', @t, '` (RECORD_NUMBER)');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;
    END IF;
END //
DELIMITER ;

CALL cepc_v103_alter_nodal_records();
DROP PROCEDURE IF EXISTS cepc_v103_alter_nodal_records;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3. SIMULATED_EMAILS — the six columns the Email Communication tab added
--
-- V75 added TEMPLATE_USED and CC_RECIPIENTS for the per-complaint email log. The tab needs six more:
-- without LAST_ERROR the Retry button has nothing to explain itself with, and without RETRY_COUNT there
-- is no way to stop retrying. IN_REPLY_TO_ID is what makes the list a thread rather than a flat log.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v103_alter_simulated_emails;
DELIMITER //
CREATE PROCEDURE cepc_v103_alter_simulated_emails()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'SIMULATED_EMAILS'
     LIMIT 1;

    IF v_table IS NULL THEN
        SELECT 'SIMULATED_EMAILS absent - skipping' AS note;
    ELSE
        SET @t = v_table;

        -- Matches V75's CC_RECIPIENTS width. A comma-separated list, not a child table: these are
        -- recipients of a sent message, never queried individually.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'BCC_RECIPIENTS') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN BCC_RECIPIENTS VARCHAR(1000) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Why the send failed, shown next to Retry. A FAILED row with no reason is not actionable: the
        -- officer cannot tell a rejected recipient domain from a transport outage.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'LAST_ERROR') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN LAST_ERROR VARCHAR(1000) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Distinct from SENT_AT and RECEIVED_AT, which are dispatch facts. A draft is edited repeatedly
        -- before either is set, and the tab orders drafts by this.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'UPDATED_AT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN UPDATED_AT DATETIME(6) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- The officer who owns this message. An inbound reply has to reach the officer holding the
        -- complaint rather than sit in a shared tray.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'ASSIGNED_TO') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN ASSIGNED_TO VARCHAR(100) NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- Self-reference by ID, deliberately without a FK: the parent of an inbound reply may have been
        -- purged by retention while the reply is still on the complaint file, and a FK would force the
        -- retention job to either cascade the delete or refuse it.
        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'IN_REPLY_TO_ID') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN IN_REPLY_TO_ID BIGINT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND COLUMN_NAME = 'RETRY_COUNT') THEN
            SET @sql = CONCAT('ALTER TABLE `', @t, '` ADD COLUMN RETRY_COUNT INT NULL');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        -- The tab loads one complaint's thread at a time; without this it scans the whole log.
        IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND INDEX_NAME = 'IDX_SIMULATED_EMAIL_COMPLAINT') THEN
            SET @sql = CONCAT('CREATE INDEX IDX_SIMULATED_EMAIL_COMPLAINT ON `', @t,
                              '` (COMPLAINT_NUMBER)');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                          AND INDEX_NAME = 'IDX_SIMULATED_EMAIL_THREAD') THEN
            SET @sql = CONCAT('CREATE INDEX IDX_SIMULATED_EMAIL_THREAD ON `', @t, '` (THREAD_ID)');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;
    END IF;
END //
DELIMITER ;

CALL cepc_v103_alter_simulated_emails();
DROP PROCEDURE IF EXISTS cepc_v103_alter_simulated_emails;
