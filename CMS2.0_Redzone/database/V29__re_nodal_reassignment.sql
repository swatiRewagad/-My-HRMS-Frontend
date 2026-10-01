-- V29: RE nodal officer reassignment (UST838, UST839, UST840, UST841, UST842, UST843, UST844, UST845)
-- MySQL version
--
-- Context: this cluster was entirely unbuilt. The only "bulk reassign" in the codebase was
-- CrpcHeadController.bulkReassign, a stub that returned {"status":"reassigned"} and persisted
-- nothing, and it was officer-side rather than RE-side in any case.
--
-- Six things this migration establishes:
--
--   1. NODAL_OFFICER_RECORDS gets an explicit MySQL definition. It previously existed ONLY in the
--      Oracle DDL (database/oracle/V5__complete_ddl_dml.sql:906); on MySQL the table was created
--      implicitly by Hibernate ddl-auto. That worked but left dev and prod schemas with no shared
--      written source, so a column added on one side could silently be absent on the other. Created
--      IF NOT EXISTS, so an existing Hibernate-made table is left exactly as it is.
--
--   2. VERSION on NODAL_OFFICER_RECORDS — the optimistic lock for UST839, and the first @Version
--      column in cms-backend. NULL on existing rows is intentional: Hibernate treats a null version
--      as unversioned and seeds it on first write, so there is no backfill and legacy writers keep
--      working. A DEFAULT would not help, because the rows already exist.
--
--   3. ENTITY_CODE on NODAL_OFFICER_RECORDS. The table previously identified an entity only by the
--      free-text ENTITY_NAME, but RequestIdentity supplies an entity *code*, so server-side
--      authorisation was not possible: two similarly-named entities could not be told apart. Left
--      NULL for historical rows, and the service refuses a record whose code is null rather than
--      assuming it belongs to the caller.
--
--   4. ENTITY_USERS — the candidate directory (UST841). Sourced from the database rather than
--      Keycloak because the `cms` realm contains no RE_* users and no RE_* roles at all, so
--      enumerating the identity provider returns nothing. RE_ROLE is a column, not a hardcoded Java
--      list, because the NO-vs-Contact-Person distinction is master data. NOTE: seeding real RE users
--      into the realm remains a prerequisite for actual SSO; this table is the source of record for
--      who exists, not a substitute for authentication.
--
--   5. REASSIGNMENT_REQUESTS / _CLARIFICATIONS / REASSIGNMENT_HISTORY. The reason column is
--      immutable at the JPA layer (updatable = false) per UST840; clarifications are separate
--      append-only rows. History is a separate table from requests because a report must count
--      movements that actually happened, not intents — counting requests would include rejections.
--      History is deliberately NOT written to COMPLAINT_TIMELINE: an entity-internal staffing change
--      is not part of the complaint narrative shared beyond the entity.
--
--   6. NOTIFICATION_DELIVERY_LOG — one append-only row per delivery attempt (UST845). A separate
--      table rather than columns on IN_APP_NOTIFICATIONS, because delivery is one-to-many (multiple
--      attempts, multiple channels) and would not fit on the notification row without discarding
--      earlier attempts.
--
-- Re-running is safe: every ALTER is guarded on information_schema and every seed is
-- INSERT ... WHERE NOT EXISTS (MySQL 8.4 has no ADD COLUMN IF NOT EXISTS or CREATE INDEX IF NOT
-- EXISTS). Guards tolerate a table Hibernate already created.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. NODAL_OFFICER_RECORDS — explicit definition, matching entity/NodalOfficerRecord.java
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS NODAL_OFFICER_RECORDS (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_number    VARCHAR(50)  NOT NULL,
    entity_name         VARCHAR(200) NULL,
    nodal_officer_name  VARCHAR(200) NULL,
    pno_name            VARCHAR(200) NULL,
    designation         VARCHAR(100) NULL,
    email               VARCHAR(200) NULL,
    phone               VARCHAR(20)  NULL,
    status              VARCHAR(30)  NOT NULL DEFAULT 'INFORMATION_REQUIRED',
    assigned_to         VARCHAR(200) NULL,
    created_at          DATETIME(6)  NULL,
    last_modified_at    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    INDEX idx_no_complaint (complaint_number),
    INDEX idx_no_entity (entity_name),
    INDEX idx_no_status (status),
    INDEX idx_no_last_modified (last_modified_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2 + 3. VERSION and ENTITY_CODE on NODAL_OFFICER_RECORDS
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_nodal_reassign_columns;
DELIMITER //
CREATE PROCEDURE add_nodal_reassign_columns()
BEGIN
    DECLARE col_missing BOOLEAN;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
                          AND COLUMN_NAME = 'version');
    IF col_missing THEN
        -- NULL, not 0: Hibernate seeds a null version on first write, so existing rows need no
        -- backfill and writers that predate optimistic locking are unaffected.
        ALTER TABLE NODAL_OFFICER_RECORDS ADD COLUMN version BIGINT NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
                          AND COLUMN_NAME = 'entity_code');
    IF col_missing THEN
        ALTER TABLE NODAL_OFFICER_RECORDS ADD COLUMN entity_code VARCHAR(50) NULL;
    END IF;
END //
DELIMITER ;

CALL add_nodal_reassign_columns();
DROP PROCEDURE IF EXISTS add_nodal_reassign_columns;

-- The workload query filters (entity_code, assigned_to) and excludes statuses; these two indexes
-- cover both it and the entity-scoped record list.
DROP PROCEDURE IF EXISTS add_nodal_reassign_indexes;
DELIMITER //
CREATE PROCEDURE add_nodal_reassign_indexes()
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
           AND INDEX_NAME = 'idx_no_entity_code_assigned') THEN
        CREATE INDEX idx_no_entity_code_assigned
            ON NODAL_OFFICER_RECORDS (entity_code, assigned_to);
    END IF;

    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'NODAL_OFFICER_RECORDS'
           AND INDEX_NAME = 'idx_no_entity_code_status') THEN
        CREATE INDEX idx_no_entity_code_status
            ON NODAL_OFFICER_RECORDS (entity_code, status);
    END IF;
END //
DELIMITER ;

CALL add_nodal_reassign_indexes();
DROP PROCEDURE IF EXISTS add_nodal_reassign_indexes;

-- Backfill ENTITY_CODE from the complaint the record belongs to.
--
-- The join key is COMPLAINT_NUMBER, deliberately NOT the entity name. Name matching was the obvious
-- approach and is wrong here for two independent reasons, both verified against the live schema:
-- REGULATED_ENTITIES has no ENTITY_CODE column at all (it carries NAME plus nodal/PNO contact
-- fields), so there is nothing to copy from; and a near-match on a free-text bank name that guessed
-- wrong would attach a record to the wrong entity and expose it to an entity that must never see it.
-- COMPLAINTS.ENTITY_CODE is the authoritative value the RE portal already scopes on, and
-- COMPLAINT_NUMBER is an exact key, so this cannot mis-attribute a row.
--
-- Records whose complaint has no entity code are left NULL and stay invisible to entity-scoped
-- callers, which is the safe direction to fail in.
DROP PROCEDURE IF EXISTS backfill_nodal_entity_code;
DELIMITER //
CREATE PROCEDURE backfill_nodal_entity_code()
BEGIN
    IF (SELECT COUNT(*) > 0 FROM information_schema.TABLES
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS') THEN
        UPDATE NODAL_OFFICER_RECORDS n
           JOIN COMPLAINTS c
             ON c.complaint_number = n.complaint_number
           SET n.entity_code = c.entity_code
         WHERE n.entity_code IS NULL
           AND c.entity_code IS NOT NULL;
    END IF;
END //
DELIMITER ;

CALL backfill_nodal_entity_code();
DROP PROCEDURE IF EXISTS backfill_nodal_entity_code;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. ENTITY_USERS — candidate directory (UST841)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS ENTITY_USERS (
    id               BIGINT       NOT NULL AUTO_INCREMENT,

    -- The login id this person authenticates as, matched against RequestIdentity.getUserId().
    user_id          VARCHAR(200) NOT NULL,
    entity_code      VARCHAR(50)  NOT NULL,
    display_name     VARCHAR(200) NULL,
    email            VARCHAR(200) NULL,
    designation      VARCHAR(100) NULL,

    -- NODAL_OFFICER | CONTACT_PERSON | PNO. A column rather than a Java enum: the realm has no
    -- Contact Person role to mirror and operations must be able to correct this without a redeploy.
    re_role          VARCHAR(30)  NOT NULL,

    -- Inactive users are retained so historical reassignments still resolve to a name, but are never
    -- offered as a target. Deleting the row would leave the UST844 report showing a bare user id.
    active           TINYINT(1)   NOT NULL DEFAULT 1,

    -- Flat, nullable label. There is no region hierarchy anywhere in the entity model, so candidate
    -- authorisation is by entity_code alone; this is display/optional-narrowing only. Inventing a
    -- hierarchy would imply a scoping rule the server does not enforce.
    territory        VARCHAR(100) NULL,

    created_at       DATETIME(6)  NULL,
    last_modified_at DATETIME(6)  NULL,

    PRIMARY KEY (id),
    CONSTRAINT uk_entity_user UNIQUE (user_id, entity_code),
    INDEX idx_eu_entity_code (entity_code),
    INDEX idx_eu_user_id (user_id),
    INDEX idx_eu_entity_active (entity_code, active),
    INDEX idx_eu_role (re_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5a. REASSIGNMENT_REQUESTS (UST839, UST840, UST842, UST843)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS REASSIGNMENT_REQUESTS (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    nodal_officer_record_id   BIGINT       NOT NULL,

    -- Denormalised so the row survives the record being reassigned again.
    complaint_number          VARCHAR(50)  NOT NULL,
    entity_code               VARCHAR(50)  NOT NULL,

    from_user_id              VARCHAR(200) NULL,
    to_user_id                VARCHAR(200) NOT NULL,

    -- Names captured at request time, not resolved on read: an officer who later leaves must still
    -- appear by name in the audit trail.
    from_user_name            VARCHAR(200) NULL,
    to_user_name              VARCHAR(200) NULL,

    -- IMMUTABLE (UST840). Mapped updatable = false in JPA, so no service, admin endpoint or bulk
    -- correction can rewrite it. A reassignment reason is what the PNO relied on when approving; if
    -- it could be edited afterwards the approval would attest to a rationale never actually reviewed.
    -- Corrections are appended to REASSIGNMENT_CLARIFICATIONS.
    reason                    TEXT         NULL,

    -- PENDING | APPROVED | REJECTED | WITHDRAWN
    -- WITHDRAWN is distinct from REJECTED: withdrawing your own request is legitimate but is not an
    -- independent refusal, and conflating them would overstate the review that took place.
    status                    VARCHAR(20)  NOT NULL,

    requested_by              VARCHAR(200) NOT NULL,
    requested_by_name         VARCHAR(200) NULL,
    requested_at              DATETIME(6)  NULL,

    decided_by                VARCHAR(200) NULL,
    decided_at                DATETIME(6)  NULL,
    decision_comment          TEXT         NULL,

    -- Destination officer's workload snapshotted at request time. Recomputing at approval time would
    -- show the PNO a different number from the one the requester acted on.
    to_user_workload_at_request INT        NULL,

    PRIMARY KEY (id),
    INDEX idx_rr_status (status),
    INDEX idx_rr_entity_status (entity_code, status),
    INDEX idx_rr_requested_by (requested_by),
    INDEX idx_rr_record (nodal_officer_record_id),
    INDEX idx_rr_requested_at (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5b. REASSIGNMENT_CLARIFICATIONS — append-only (UST840)
--     This table is the reason the reason column above can be immutable.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS REASSIGNMENT_CLARIFICATIONS (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    reassignment_request_id  BIGINT       NOT NULL,
    note                     TEXT         NULL,

    -- Attribution resolved server-side from RequestIdentity, never taken from the request body.
    added_by                 VARCHAR(200) NOT NULL,
    added_by_name            VARCHAR(200) NULL,
    added_by_side            VARCHAR(10)  NULL,
    added_at                 DATETIME(6)  NULL,

    PRIMARY KEY (id),
    INDEX idx_rc_request (reassignment_request_id),
    INDEX idx_rc_added_at (added_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 5c. REASSIGNMENT_HISTORY — append-only record of movements that took effect (UST844)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS REASSIGNMENT_HISTORY (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    nodal_officer_record_id BIGINT       NOT NULL,
    complaint_number        VARCHAR(50)  NOT NULL,
    entity_code             VARCHAR(50)  NOT NULL,

    from_user_id            VARCHAR(200) NULL,
    from_user_name          VARCHAR(200) NULL,
    to_user_id              VARCHAR(200) NOT NULL,
    to_user_name            VARCHAR(200) NULL,

    -- APPROVED_REQUEST | DIRECT
    trigger_type            VARCHAR(30)  NOT NULL,

    -- NULL for a direct move, which has no request behind it.
    reassignment_request_id BIGINT       NULL,

    -- Copied from the request rather than joined, so the report reads without a join and stays
    -- correct if the request is later superseded.
    reason                  TEXT         NULL,

    performed_by            VARCHAR(200) NOT NULL,
    performed_by_name       VARCHAR(200) NULL,
    reassigned_at           DATETIME(6)  NULL,

    PRIMARY KEY (id),
    INDEX idx_rh_entity_code (entity_code),
    INDEX idx_rh_complaint (complaint_number),
    INDEX idx_rh_from_user (from_user_id),
    INDEX idx_rh_to_user (to_user_id),
    INDEX idx_rh_reassigned_at (reassigned_at),
    INDEX idx_rh_entity_at (entity_code, reassigned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 6. NOTIFICATION_DELIVERY_LOG — one append-only row per delivery attempt (UST845)
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS NOTIFICATION_DELIVERY_LOG (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,

    -- Nullable by design: a failure that happened before the notification could be persisted still
    -- has to be recorded, and that is precisely the case worth logging.
    notification_id          BIGINT        NULL,

    recipient_user_id        VARCHAR(200)  NOT NULL,

    -- Translation key of the notification, so the log is readable without re-deriving the text.
    notification_type        VARCHAR(200)  NULL,

    -- IN_APP for now. A column rather than an assumption, so adding email needs no schema change.
    channel                  VARCHAR(20)   NOT NULL,

    -- SENT | FAILED
    status                   VARCHAR(20)   NOT NULL,

    -- Truncated at the service boundary; a stack trace does not belong in an audit row.
    error_message            VARCHAR(1000) NULL,
    related_complaint_number VARCHAR(50)   NULL,
    attempted_at             DATETIME(6)   NULL,

    PRIMARY KEY (id),
    INDEX idx_ndl_notification (notification_id),
    INDEX idx_ndl_recipient (recipient_user_id),
    INDEX idx_ndl_status (status),
    INDEX idx_ndl_attempted_at (attempted_at),
    INDEX idx_ndl_type (notification_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ═══════════════════════════════════════════════════════════════════════════
-- 7. Configurable thresholds. No hardcoded values in code — all read via SystemConfigService.
--    Prefixed cms.reassign.* so they cannot collide with another session's keys.
-- ═══════════════════════════════════════════════════════════════════════════

-- The excluded-status list for the UST838 workload count. In SYSTEM_CONFIG rather than Java because
-- this codebase already contains FOUR disagreeing hardcoded CLOSED_STATUSES lists
-- (WorkflowController:57, NotificationScheduledTasks:29, AppealWorkflowService:36,
-- AppealController:28); a fifth would guarantee the workload figure drifts from what the rest of the
-- system considers closed, with no way to correct it without a redeploy.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.workload.excluded_statuses',
       'DRAFT,CLOSED,RESOLVED,REJECTED,WITHDRAWN,ADJUDICATED,CONCILIATED,ORDER_PASSED,NON_MAINTAINABLE',
       'Record statuses excluded from the RE nodal officer workload count — drafts and closed work (UST838)',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.workload.excluded_statuses');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.bulk.max_records', '50',
       'Maximum records permitted in one bulk reassignment or bulk approval action (UST839, UST843)',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.bulk.max_records');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.page_size.default', '20',
       'Default page size for reassignment request lists (UST842, UST843)',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.page_size.default');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.page_size.max', '100',
       'Upper bound on a client-requested page size, so a caller cannot request an unbounded page',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.page_size.max');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.reason.min_length', '10',
       'Minimum characters in a reassignment reason. The reason is immutable once submitted (UST840), so it must be substantive at entry.',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.reason.min_length');

-- When true (the default), even a PNO's own reassignment is queued as a request rather than applied
-- immediately. Defaulting to true is the conservative choice: it keeps a two-step record for every
-- movement until an operator deliberately relaxes it.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reassign.require_pno_approval', 'true',
       'Whether a PNO-initiated reassignment still requires an explicit approval step (UST843)',
       'V29_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reassign.require_pno_approval');
