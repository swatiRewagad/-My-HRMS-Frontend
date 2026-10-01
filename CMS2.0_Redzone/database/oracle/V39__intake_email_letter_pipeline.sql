-- V39: Inbound email & letter intake — recipient headers, DB-backed ignore list, suppression audit,
--      real deduplication, OCR gating.
-- Oracle version — twin of MySQL V41. Oracle numbering runs behind MySQL by convention; the two
-- directories are not in step and each takes its own next free number.
--
-- Context: the intake pipeline could not satisfy its own user stories.
--
--   1. RECIPIENT HEADERS WERE DISCARDED. To/CC/BCC arrived at the ingest API, were used once for a
--      CRPC routing rule, then dropped. Every Exceptional-Email-Master rule written against To, CC,
--      BCC or Subject was therefore unenforceable. cms-mail-intake (the Netty SMTP receiver) already
--      POSTs these fields to /api/v1/email-syndication/ingest, so the columns make an existing
--      producer's output usable rather than adding speculative fields.
--
--   2. MESSAGE_ID WAS NOT UNIQUE and the ingest path overwrote it with a fresh random UUID, so a
--      redelivered message created a second draft. Pre-existing duplicates and blanks are repaired
--      before the constraint is added, since a unique index over dirty data fails.
--
--   3. THE IGNORE LIST LIVED IN AN IN-MEMORY LIST IN THE CONTROLLER and was lost on restart while
--      this table sat unused. Rules are now read from the database per ingestion, so an admin edit
--      applies to the next email with no downtime. MATCH_FIELD defaults to 'FROM' so existing
--      sender-only rows keep their meaning without a data migration.
--
--   4. SUPPRESSED EMAILS LEFT NO TRACE and the ignored count was a literal 0. IGNORED_EMAIL_LOG
--      records sender, subject, timestamp AND the rule that suppressed the mail.
--
--   5. OCR GATING. OCR ran before language detection so it could never be suppressed, and detection
--      read the body only. OCR_SKIP_REASON / REQUIRES_MANUAL_ENTRY record the fail-closed decision.
--      No handwriting classifier exists in this system and none is claimed: the gate is script and
--      confidence based.
--
--   6. SUGGESTED-RELATED accept/dismiss had nowhere to persist.
--
-- Re-running is safe: every DDL is guarded on USER_TAB_COLUMNS / USER_INDEXES / USER_TABLES.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. EMAIL_DRAFTS
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    PROCEDURE add_column(p_table VARCHAR2, p_col VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = UPPER(p_table) AND COLUMN_NAME = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD ' || p_col || ' ' || p_type;
        END IF;
    END;
BEGIN
    add_column('EMAIL_DRAFTS', 'TO_RECIPIENTS',   'VARCHAR2(1000)');
    add_column('EMAIL_DRAFTS', 'CC_RECIPIENTS',   'VARCHAR2(1000)');
    add_column('EMAIL_DRAFTS', 'BCC_RECIPIENTS',  'VARCHAR2(1000)');
    add_column('EMAIL_DRAFTS', 'REPLY_TO',        'VARCHAR2(200)');
    add_column('EMAIL_DRAFTS', 'IN_REPLY_TO',     'VARCHAR2(500)');
    add_column('EMAIL_DRAFTS', 'EMAIL_REFERENCES', 'CLOB');
    add_column('EMAIL_DRAFTS', 'ATTACHMENT_COUNT', 'NUMBER(10)');
    add_column('EMAIL_DRAFTS', 'OCR_SKIP_REASON', 'VARCHAR2(40)');
    add_column('EMAIL_DRAFTS', 'REQUIRES_MANUAL_ENTRY', 'NUMBER(1) DEFAULT 0 NOT NULL');
    add_column('EMAIL_DRAFTS', 'SUGGESTED_RELATED_DRAFT_ID',   'VARCHAR2(100)');
    add_column('EMAIL_DRAFTS', 'SUGGESTED_RELATED_DECISION',   'VARCHAR2(20)');
    add_column('EMAIL_DRAFTS', 'SUGGESTED_RELATED_DECIDED_BY', 'VARCHAR2(200)');
    add_column('EMAIL_DRAFTS', 'SUGGESTED_RELATED_DECIDED_AT', 'TIMESTAMP');

    add_column('EMAIL_DRAFT_ATTACHMENTS', 'LINKED_COMPLAINT_NUMBER', 'VARCHAR2(50)');
END;
/

-- Repair before constraining.
UPDATE EMAIL_DRAFTS SET MESSAGE_ID = NULL WHERE MESSAGE_ID IS NOT NULL AND TRIM(MESSAGE_ID) IS NULL;

UPDATE EMAIL_DRAFTS d
   SET MESSAGE_ID = 'legacy-dup-' || TO_CHAR(d.ID)
 WHERE d.MESSAGE_ID IS NOT NULL
   AND d.ID <> (SELECT MIN(x.ID) FROM EMAIL_DRAFTS x WHERE x.MESSAGE_ID = d.MESSAGE_ID);

DECLARE
    PROCEDURE add_index(p_index VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = UPPER(p_index);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END;
BEGIN
    add_index('UK_EMAIL_DRAFTS_MESSAGE_ID',
              'CREATE UNIQUE INDEX UK_EMAIL_DRAFTS_MESSAGE_ID ON EMAIL_DRAFTS (MESSAGE_ID)');
    add_index('IDX_DRAFT_MANUAL_ENTRY',
              'CREATE INDEX IDX_DRAFT_MANUAL_ENTRY ON EMAIL_DRAFTS (REQUIRES_MANUAL_ENTRY)');
    add_index('IDX_DRAFT_ATT_PARENT',
              'CREATE INDEX IDX_DRAFT_ATT_PARENT ON EMAIL_DRAFT_ATTACHMENTS (LINKED_COMPLAINT_NUMBER)');
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. EMAIL_IGNORE_LIST — Exceptional Email Master
-- ═══════════════════════════════════════════════════════════════════════════
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'EMAIL_IGNORE_LIST';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE EMAIL_IGNORE_LIST (
                ID              NUMBER GENERATED BY DEFAULT AS IDENTITY,
                EMAIL_PATTERN   VARCHAR2(300) NOT NULL,
                PATTERN_TYPE    VARCHAR2(20)  DEFAULT ''EXACT'' NOT NULL,
                REASON          VARCHAR2(500),
                ADDED_BY        VARCHAR2(100),
                IS_ACTIVE       NUMBER(1)     DEFAULT 1 NOT NULL,
                CREATED_AT      TIMESTAMP,
                CONSTRAINT PK_EMAIL_IGNORE_LIST PRIMARY KEY (ID)
            )';
    END IF;
END;
/

DECLARE
    PROCEDURE add_column(p_table VARCHAR2, p_col VARCHAR2, p_type VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = UPPER(p_table) AND COLUMN_NAME = UPPER(p_col);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD ' || p_col || ' ' || p_type;
        END IF;
    END;
BEGIN
    add_column('EMAIL_IGNORE_LIST', 'MATCH_FIELD',       'VARCHAR2(20) DEFAULT ''FROM'' NOT NULL');
    add_column('EMAIL_IGNORE_LIST', 'TO_PATTERN',        'VARCHAR2(300)');
    add_column('EMAIL_IGNORE_LIST', 'CC_PATTERN',        'VARCHAR2(300)');
    add_column('EMAIL_IGNORE_LIST', 'BCC_PATTERN',       'VARCHAR2(300)');
    add_column('EMAIL_IGNORE_LIST', 'SUBJECT_PATTERN',   'VARCHAR2(500)');
    add_column('EMAIL_IGNORE_LIST', 'EXCEPTION_PATTERN', 'VARCHAR2(500)');
END;
/

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. IGNORED_EMAIL_LOG — suppression audit (report + CSV export)
-- ═══════════════════════════════════════════════════════════════════════════
-- MATCHED_RULE_* are denormalised on purpose: the report must stay truthful about which rule
-- suppressed a mail even after that rule is edited or deleted.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'IGNORED_EMAIL_LOG';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE TABLE IGNORED_EMAIL_LOG (
                ID                   NUMBER GENERATED BY DEFAULT AS IDENTITY,
                SENDER_EMAIL         VARCHAR2(200),
                SUBJECT              VARCHAR2(500),
                TO_RECIPIENTS        VARCHAR2(500),
                CC_RECIPIENTS        VARCHAR2(500),
                BCC_RECIPIENTS       VARCHAR2(500),
                MESSAGE_ID           VARCHAR2(200),
                MATCHED_RULE_ID      NUMBER,
                MATCHED_RULE_PATTERN VARCHAR2(300),
                MATCHED_RULE_FIELD   VARCHAR2(20),
                MATCHED_RULE_TYPE    VARCHAR2(20),
                MATCHED_RULE_REASON  VARCHAR2(500),
                RECEIVED_AT          TIMESTAMP,
                CREATED_AT           TIMESTAMP,
                CONSTRAINT PK_IGNORED_EMAIL_LOG PRIMARY KEY (ID)
            )';
    END IF;
END;
/

DECLARE
    PROCEDURE add_index(p_index VARCHAR2, p_ddl VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = UPPER(p_index);
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_ddl;
        END IF;
    END;
BEGIN
    add_index('IDX_IGNORED_EMAIL_SENDER',
              'CREATE INDEX IDX_IGNORED_EMAIL_SENDER ON IGNORED_EMAIL_LOG (SENDER_EMAIL)');
    add_index('IDX_IGNORED_EMAIL_RULE',
              'CREATE INDEX IDX_IGNORED_EMAIL_RULE ON IGNORED_EMAIL_LOG (MATCHED_RULE_ID)');
    add_index('IDX_IGNORED_EMAIL_RECEIVED',
              'CREATE INDEX IDX_IGNORED_EMAIL_RECEIVED ON IGNORED_EMAIL_LOG (RECEIVED_AT)');
    add_index('IDX_IGNORE_ACTIVE',
              'CREATE INDEX IDX_IGNORE_ACTIVE ON EMAIL_IGNORE_LIST (IS_ACTIVE)');
END;
/
