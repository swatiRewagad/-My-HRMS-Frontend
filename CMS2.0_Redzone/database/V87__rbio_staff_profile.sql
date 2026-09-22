-- ============================================================
-- V87 — RBIO staff profile: designation, office posting, leave dates, closure eligibility
-- Session S1 (UST443-448, UST451-455, UST629-631)
-- ============================================================
--
-- WHY THIS TABLE HAS TO EXIST
--
--   There is no RBIO staff master anywhere in this system. Identity lives in Keycloak, and the only
--   officer-shaped table is cms-workflow-service's WF_OFFICER_POOL, which has exactly nine columns:
--   USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD,
--   MAX_WORKLOAD. Four things the stories require have nowhere to live:
--
--     * designation                 (UST443/444 Create User)
--     * office posting as a real FK (UST440 territory scoping — REGIONAL_OFFICE is free text with no FK)
--     * leave FROM/TO dates         (UST447/448/451 — leave is currently a single boolean with no dates
--                                    and no record of who set it)
--     * reviewer closure eligibility(UST629-631 — zero occurrences repo-wide before this)
--
--   Keycloak user attributes were the obvious alternative and are rejected deliberately: attribute
--   writes are a read-modify-write on the whole user, concurrent sessions lose each other's updates,
--   and Keycloak 26's unmanagedAttributePolicy silently drops unknown attributes. A statutory
--   permission flag (closure eligibility) must not depend on that.
--
-- WHY IT DOES NOT DUPLICATE WF_OFFICER_POOL
--
--   This is a PROFILE keyed on the Keycloak user id, not a second assignment pool. Workload and the
--   round-robin pointer stay in WF_OFFICER_POOL, which another session owns. Duplicating IS_ON_LEAVE
--   here would create two answers to "is this officer on leave" — exactly the divergence that made the
--   RE response window wrong in two services at once. IS_ON_LEAVE stays authoritative in
--   WF_OFFICER_POOL; this table records the DATES and the AUDITABLE WHO/WHEN that the boolean lacks.
--
-- WHY CLOSURE ELIGIBILITY DEFAULTS TO 'N'
--
--   UST629 requires every NEW Reviewer account to default to "No". A permission that defaults to
--   granted is the wrong way round for a control that decides whether a reviewer may finally close a
--   citizen's complaint. The column default enforces it even for rows inserted by a path that forgets.
--
-- ALL COLUMNS NULLABLE except the identity key and the two flags that carry defaults.
--   ddl-auto=update runs against a database shared by seven concurrent sessions, so NOT NULL would be
--   permanent for everyone and would break inserts made by code that does not know about this table.
--
-- Re-running is safe: MySQL 8.4 has no CREATE INDEX IF NOT EXISTS, so indexes are guarded on
-- information_schema and every seed is INSERT ... WHERE NOT EXISTS.
-- ============================================================

DROP PROCEDURE IF EXISTS s1_sp_add_column;
DROP PROCEDURE IF EXISTS s1_sp_add_index;

DELIMITER $$

CREATE PROCEDURE s1_sp_add_column(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE s1_sp_add_index(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- 1. The profile table
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS RBIO_STAFF_PROFILE (
    ID                      BIGINT       NOT NULL AUTO_INCREMENT,

    -- The Keycloak user id (preferred_username). The join key to the identity provider; this table
    -- never stores credentials.
    USER_ID                 VARCHAR(200) NOT NULL,
    DISPLAY_NAME            VARCHAR(250) NULL,
    EMAIL                   VARCHAR(250) NULL,

    -- UST443/444. Free text against no master because RBI designations are not enumerated anywhere in
    -- this system; a DESIGNATION_MASTER invented here would be a guess presented as reference data.
    DESIGNATION             VARCHAR(150) NULL,

    -- UST440 territory scoping. Holds OFFICE_CODE_MASTER.OFFICE_CODE — the same key
    -- COMPLAINTS.rbio_office_code uses, so a caller's office can be compared with a complaint's office
    -- without a translation step. Deliberately NOT the free-text REGIONAL_OFFICE spelling.
    OFFICE_CODE             VARCHAR(10)  NULL,

    -- The RBIO rank this profile describes, e.g. RBIO_DEALING_OFFICIAL. Roles themselves stay in
    -- Keycloak; this is the primary rank used for queue routing and for the per-role status filters.
    PRIMARY_ROLE            VARCHAR(50)  NULL,

    -- UST447/448/451. WF_OFFICER_POOL.IS_ON_LEAVE remains the authoritative boolean; these are the
    -- dates it cannot express, plus the attribution it does not record.
    LEAVE_FROM_DATE         DATE         NULL,
    LEAVE_TO_DATE           DATE         NULL,
    LEAVE_SET_BY            VARCHAR(200) NULL,
    LEAVE_SET_AT            DATETIME     NULL,

    -- UST629-631. 'N' default is the requirement, not a convenience: a reviewer must be granted the
    -- power to close a citizen's complaint explicitly.
    CLOSURE_ELIGIBLE        CHAR(1)      NOT NULL DEFAULT 'N',

    IS_ACTIVE               CHAR(1)      NOT NULL DEFAULT 'Y',

    CREATED_BY              VARCHAR(200) NULL,
    CREATED_AT              DATETIME     NULL,
    UPDATED_BY              VARCHAR(200) NULL,
    UPDATED_AT              DATETIME     NULL,

    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s1_sp_add_index('RBIO_STAFF_PROFILE', 'idx_rsp_office', 'OFFICE_CODE');
CALL s1_sp_add_index('RBIO_STAFF_PROFILE', 'idx_rsp_role', 'PRIMARY_ROLE');
CALL s1_sp_add_index('RBIO_STAFF_PROFILE', 'idx_rsp_closure', 'CLOSURE_ELIGIBLE');

-- The UNIQUE constraint is added separately: s1_sp_add_index creates a plain index, and a plain index
-- would let the duplicate through. Guarded so a re-run does not fail on the existing constraint.
SET @has_uk = (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'RBIO_STAFF_PROFILE'
                 AND CONSTRAINT_NAME = 'uk_rsp_user_unique'
                 AND CONSTRAINT_TYPE = 'UNIQUE');
SET @ddl = IF(@has_uk = 0,
    'ALTER TABLE RBIO_STAFF_PROFILE ADD CONSTRAINT uk_rsp_user_unique UNIQUE (USER_ID)',
    'SELECT ''uk_rsp_user_unique already present''');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ─────────────────────────────────────────────────────────────
-- 2. Audit of staff-administration actions
-- ─────────────────────────────────────────────────────────────
--
-- UST452-455 and UST630 require an audit entry per reassignment and per closure-eligibility change,
-- naming old owner, new owner, acting user and timestamp. Existing audit tables cannot carry it:
-- AUDIT_LOG.complaint_number is NOT NULL so it cannot represent a user-scoped change at all, and
-- AA_ASSIGNMENT_AUDIT is AA-namespaced with an APPEAL_NUMBER column. CONFIG_AUDIT_LOG has only six
-- columns and no description.
--
-- PERFORMED_BY is resolved from the JWT by the service, never accepted from the request body — the
-- AA precedent states plainly that accepting a caller-supplied actor is what made attribution
-- spoofable.
CREATE TABLE IF NOT EXISTS RBIO_STAFF_AUDIT (
    ID                  BIGINT        NOT NULL AUTO_INCREMENT,

    -- USER_CREATED, USER_UPDATED, ACTIVATION_CHANGED, LEAVE_CHANGED, CLOSURE_ELIGIBILITY_CHANGED,
    -- OWNER_REASSIGNED, ROLE_CHANGED, MASTER_CHANGED.
    ACTION              VARCHAR(50)   NOT NULL,

    -- The staff member the action was done TO. Nullable because some actions are pool-wide.
    SUBJECT_USER_ID     VARCHAR(200)  NULL,

    -- For OWNER_REASSIGNED (UST455): which complaint moved, and between whom.
    COMPLAINT_NUMBER    VARCHAR(50)   NULL,
    OLD_VALUE           VARCHAR(500)  NULL,
    NEW_VALUE           VARCHAR(500)  NULL,
    FIELD_NAME          VARCHAR(60)   NULL,

    -- UST459 requires a human-readable description retrievable for audit. CONFIG_AUDIT_LOG has no such
    -- column, which is why this table carries its own.
    DESCRIPTION         VARCHAR(1000) NULL,
    REASON              VARCHAR(1000) NULL,

    PERFORMED_BY        VARCHAR(200)  NOT NULL,
    PERFORMED_BY_ROLE   VARCHAR(50)   NULL,
    PERFORMED_AT        DATETIME      NOT NULL,

    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s1_sp_add_index('RBIO_STAFF_AUDIT', 'idx_rsa_subject', 'SUBJECT_USER_ID');
CALL s1_sp_add_index('RBIO_STAFF_AUDIT', 'idx_rsa_action', 'ACTION');
CALL s1_sp_add_index('RBIO_STAFF_AUDIT', 'idx_rsa_at', 'PERFORMED_AT');
CALL s1_sp_add_index('RBIO_STAFF_AUDIT', 'idx_rsa_complaint', 'COMPLAINT_NUMBER');

-- ─────────────────────────────────────────────────────────────
-- 3. Configuration
-- ─────────────────────────────────────────────────────────────
-- Thresholds and caps read from SYSTEM_CONFIG rather than compiled in, so RBI can change them without
-- a release. Values chosen to match the existing bulk-reassign cap (cms.reassign.bulk.max_records = 50)
-- so the two bulk paths do not disagree about what "too many" means.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_at)
SELECT 'rbio.staff.bulk.max_records', '50',
       'Maximum staff accounts a single bulk activate/leave/closure-eligibility action may affect', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'rbio.staff.bulk.max_records');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_at)
SELECT 'rbio.staff.closure_eligibility.default', 'N',
       'Closure-eligibility flag applied to a newly created Reviewer account (UST629). N per the story.', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'rbio.staff.closure_eligibility.default');

DROP PROCEDURE IF EXISTS s1_sp_add_column;
DROP PROCEDURE IF EXISTS s1_sp_add_index;
