-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V71 — S3: RBIO ladder case-file tables + COMPLAINTS columns
--
--   RBIO_CASE_ASSIGNMENT_HISTORY  custody trail (send-back + reopen targets)
--   RBIO_ADDITIONAL_ENTITY        up to six additional entities per complaint (UST487-495)
--   RBIO_ACTION_OVERRIDE          field-level override history (UST474-475, 479-480, 483-484, 639-642)
--   RBIO_LEGAL_CASE               court proceedings (UST553)
--   COMPLAINTS                    deputy_decision, regulatory_body_name, advisory_complied_at,
--                                 reopen_reason, reopen_justification
--
-- Re-runnable, guarded through information_schema. Procedure prefix s3_.
--
-- WHY THESE TABLES EXIST: the RBIO frontend has always called
-- /complaints/{id}/additional-entities, /action-override, /legal-case and /last-active-officer. A grep
-- of cms-backend for any of those four returns ZERO. Every call 404'd into
-- `catchError(() => of(default))`, so the add-entity form accepted six entities that were never stored,
-- the History tab rendered an empty list, and the legal-case form reopened blank. Nothing looked broken.
--
-- WHY CUSTODY IS A TABLE AND NOT DERIVED FROM AUDIT_LOG: audit metadata does carry assignedOfficer, so
-- the trail is technically recoverable by replaying it. It is not used for that because the metadata is
-- an untyped TEXT blob and audit writes are @Async — a send-back performed straight after a forward
-- could read its own history before the row lands. An assignment decision cannot depend on an
-- eventually-consistent log.
--
-- ALL NEW COLUMNS ARE NULLABLE. Schema here comes from ddl-auto: update on a database shared by seven
-- sessions, and ddl-auto never reverts, so a NOT NULL would be permanent for everyone and would break
-- the other sessions' inserts.
--
-- The ROWS for the new workflow actions are NOT here — they are seeded by RbioLadderTransitionSeeder
-- (@Order 32), per the convention V58 establishes: a hand-run .sql that was never applied would leave
-- the workflow with actions missing, whereas a Java seeder runs on every boot and cannot be forgotten.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s3_add_column;
DROP PROCEDURE IF EXISTS s3_add_index;
DELIMITER //
CREATE PROCEDURE s3_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_def);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
CREATE PROCEDURE s3_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. RBIO_CASE_ASSIGNMENT_HISTORY — who held the file, in which role, until when
--
-- RELEASED_AT NULL means "holds it now". The previous-holder query keys on RELEASED_AT IS NOT NULL,
-- which is the clause that stops a send-back resolving to the CURRENT holder whenever the sender
-- happens to occupy the target role — i.e. a file "sent back" to the person who sent it.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_CASE_ASSIGNMENT_HISTORY (
    ID                 BIGINT       NOT NULL AUTO_INCREMENT,
    COMPLAINT_NUMBER   VARCHAR(50)  NOT NULL,
    ROLE_NAME          VARCHAR(50)  NOT NULL,
    OFFICER_ID         VARCHAR(100) NOT NULL,
    ASSIGNED_BY_ACTION VARCHAR(50),
    ASSIGNED_BY        VARCHAR(100),
    ASSIGNED_AT        DATETIME     NOT NULL,
    RELEASED_AT        DATETIME     NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s3_add_index('RBIO_CASE_ASSIGNMENT_HISTORY', 'IDX_RBIO_CAH_COMPLAINT', 'COMPLAINT_NUMBER');
CALL s3_add_index('RBIO_CASE_ASSIGNMENT_HISTORY', 'IDX_RBIO_CAH_LOOKUP',
                  'COMPLAINT_NUMBER, ROLE_NAME, RELEASED_AT');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. RBIO_ADDITIONAL_ENTITY — up to SIX per complaint
--
-- The cap is enforced in RbioAdditionalEntityService, not here: a row limit is not expressible as a
-- column constraint. It is deliberately NOT left to the browser, which is where it lived until now
-- (rbio-add-entity.component.ts disables its own control at six and a direct POST ignores that).
--
-- Not a delimited column: COMPLAINTS.impleaded_parties is a comma-joined VARCHAR(1000) that cannot carry
-- branch/type/category, cannot record who added a row, and cannot be counted reliably for a cap — an
-- entity name containing a comma would count twice.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_ADDITIONAL_ENTITY (
    ID                  BIGINT       NOT NULL AUTO_INCREMENT,
    COMPLAINT_NUMBER    VARCHAR(50)  NOT NULL,
    ENTITY_NAME         VARCHAR(200) NOT NULL,
    ENTITY_BRANCH       VARCHAR(200),
    ENTITY_TYPE         VARCHAR(100),
    ENTITY_CATEGORY     VARCHAR(100),
    -- Nullable: the form accepts a free-typed name, and refusing an entity absent from
    -- REGULATED_ENTITIES would block a legitimate complaint against a newly licensed one.
    REGULATED_ENTITY_ID BIGINT       NULL,
    CREATED_BY          VARCHAR(100),
    CREATED_BY_ROLE     VARCHAR(50),
    CREATED_AT          DATETIME     NOT NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s3_add_index('RBIO_ADDITIONAL_ENTITY', 'IDX_RBIO_ADDL_ENTITY_CN', 'COMPLAINT_NUMBER');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. RBIO_ACTION_OVERRIDE — field-level override history, append-only
--
-- OLD_VALUE is nullable: the FIRST role to set a field overrides nothing, and recording an empty string
-- as though it were a prior decision would invent a proposal nobody made.
--
-- Scope: FIELD-level only. Status-change history is ComplaintTimeline and belongs to another session.
-- Both surface in the same History tab and neither is derivable from the other — an overridden clause on
-- an unchanged status produces no timeline row at all.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_ACTION_OVERRIDE (
    ID                 BIGINT       NOT NULL AUTO_INCREMENT,
    COMPLAINT_NUMBER   VARCHAR(50)  NOT NULL,
    FIELD_NAME         VARCHAR(100) NOT NULL,
    OLD_VALUE          VARCHAR(500) NULL,
    NEW_VALUE          VARCHAR(500) NULL,
    OVERRIDDEN_BY      VARCHAR(100),
    OVERRIDDEN_BY_ROLE VARCHAR(50),
    OVERRIDDEN_AT      DATETIME     NOT NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s3_add_index('RBIO_ACTION_OVERRIDE', 'IDX_RBIO_OVERRIDE_CN', 'COMPLAINT_NUMBER');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. RBIO_LEGAL_CASE — one per complaint
--
-- COMPLAINT_NUMBER is UNIQUE because "is this matter before a court" has one answer at a time, and the
-- statutory sub-judice guard depends on that uniqueness.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS RBIO_LEGAL_CASE (
    ID                BIGINT        NOT NULL AUTO_INCREMENT,
    COMPLAINT_NUMBER  VARCHAR(50)   NOT NULL,
    CASE_NUMBER       VARCHAR(100)  NOT NULL,
    COURT_NAME        VARCHAR(200)  NOT NULL,
    CASE_STATUS       VARCHAR(50),
    FILING_DATE       DATE,
    NEXT_HEARING_DATE DATE,
    REMARKS           VARCHAR(2000),
    CREATED_BY        VARCHAR(100),
    CREATED_AT        DATETIME,
    UPDATED_BY        VARCHAR(100),
    UPDATED_AT        DATETIME,
    PRIMARY KEY (ID),
    UNIQUE KEY UK_RBIO_LEGAL_CASE_CN (COMPLAINT_NUMBER)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. COMPLAINTS — five new nullable columns
--
-- deputy_decision is SEPARATE from maintainability_determination on purpose: a complaint can be
-- maintainable AND rejected on its merits, so one column could not express both outcomes.
-- ═══════════════════════════════════════════════════════════════════════════
CALL s3_add_column('COMPLAINTS', 'deputy_decision',      'VARCHAR(30) NULL');
CALL s3_add_column('COMPLAINTS', 'regulatory_body_name', 'VARCHAR(200) NULL');
CALL s3_add_column('COMPLAINTS', 'advisory_complied_at', 'DATETIME NULL');
CALL s3_add_column('COMPLAINTS', 'reopen_reason',        'VARCHAR(50) NULL');
CALL s3_add_column('COMPLAINTS', 'reopen_justification', 'TEXT NULL');

DROP PROCEDURE IF EXISTS s3_add_column;
DROP PROCEDURE IF EXISTS s3_add_index;
