-- ═══════════════════════════════════════════════════════════════════════════
-- V17 — Security enforcement, PII masking, anomaly detection, retention
--        (B1, UST873, UST875, UST877, UST890)
--
-- MySQL 8.4. Safe to re-run: tables use CREATE TABLE IF NOT EXISTS, indexes and
-- config/translation rows are guarded on information_schema / NOT EXISTS.
-- MySQL 8.4 has no ADD COLUMN IF NOT EXISTS, so column adds use the
-- information_schema + PREPARE idiom used by V9.
--
-- Oracle counterpart: database/oracle/V16__security_enforcement_and_retention.sql
-- (V-numbers are deliberately offset by one between the two dialects.)
-- ═══════════════════════════════════════════════════════════════════════════

-- ───────────────────────── UST875: PII reveal audit ─────────────────────────
-- Separate from AUDIT_LOG because that table requires a complaint number, while a
-- reveal can happen on a list view or export where no single complaint applies.
CREATE TABLE IF NOT EXISTS PII_REVEAL_AUDIT (
    ID                BIGINT       NOT NULL AUTO_INCREMENT,
    USER_ID           VARCHAR(200) NOT NULL,
    DISPLAY_NAME      VARCHAR(200),
    ROLES             VARCHAR(500),
    COMPLAINT_NUMBER  VARCHAR(50),
    FIELDS_REVEALED   VARCHAR(1000) NOT NULL,
    CONTEXT           VARCHAR(100),
    JUSTIFICATION     VARCHAR(1000),
    IP_ADDRESS        VARCHAR(50),
    REVEALED_AT       DATETIME(6)  NOT NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ──────────────────── UST873: security events and alerts ────────────────────
-- Individual events are stored because a threshold cannot be evaluated from log
-- lines; SECURITY_ALERT is the aggregate raised once a threshold trips.
CREATE TABLE IF NOT EXISTS SECURITY_EVENT (
    ID                BIGINT       NOT NULL AUTO_INCREMENT,
    EVENT_TYPE        VARCHAR(60)  NOT NULL,
    SUBJECT           VARCHAR(200) NOT NULL,
    USER_ID           VARCHAR(200),
    COMPLAINT_NUMBER  VARCHAR(50),
    ENTITY_CODE       VARCHAR(50),
    REQUEST_PATH      VARCHAR(500),
    IP_ADDRESS        VARCHAR(50),
    DETAILS           TEXT,
    OCCURRED_AT       DATETIME(6)  NOT NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS SECURITY_ALERT (
    ID                    BIGINT       NOT NULL AUTO_INCREMENT,
    ALERT_TYPE            VARCHAR(60)  NOT NULL,
    SEVERITY              VARCHAR(20)  NOT NULL,
    SUBJECT               VARCHAR(200) NOT NULL,
    SUBJECT_TYPE          VARCHAR(50),
    EVENT_COUNT           INT          NOT NULL,
    THRESHOLD             INT          NOT NULL,
    WINDOW_LABEL          VARCHAR(60),
    DETAILS               TEXT,
    IP_ADDRESS            VARCHAR(50),
    STATUS                VARCHAR(20)  NOT NULL,
    ACKNOWLEDGED_BY       VARCHAR(200),
    ACKNOWLEDGED_AT       DATETIME(6),
    ACKNOWLEDGEMENT_NOTE  VARCHAR(1000),
    RAISED_AT             DATETIME(6)  NOT NULL,
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ─────────────────── UST877: credential revocation list ───────────────────
-- Doubles as the server-side revocation list. This API validates JWTs offline and
-- never introspects per request, so without these rows a revoked user's existing
-- access token would keep working until its natural expiry.
CREATE TABLE IF NOT EXISTS CREDENTIAL_REVOCATION (
    ID                          BIGINT       NOT NULL AUTO_INCREMENT,
    USERNAME                    VARCHAR(200) NOT NULL,
    KEYCLOAK_USER_ID            VARCHAR(100),
    REASON                      VARCHAR(50)  NOT NULL,
    NOTES                       VARCHAR(1000),
    INITIATED_BY                VARCHAR(200) NOT NULL,
    REVOKED_AT                  DATETIME(6)  NOT NULL,
    ACTIVE                      TINYINT(1)   NOT NULL DEFAULT 1,
    RESTORED_AT                 DATETIME(6),
    RESTORED_BY                 VARCHAR(200),
    ACCOUNT_DISABLED            TINYINT(1)   NOT NULL DEFAULT 0,
    SESSIONS_TERMINATED         TINYINT(1)   NOT NULL DEFAULT 0,
    SESSIONS_TERMINATED_COUNT   INT,
    FAILURE_DETAIL              VARCHAR(1000),
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ───────────────────── UST890: retention and deletion log ─────────────────────
CREATE TABLE IF NOT EXISTS RETENTION_POLICY (
    ID                        BIGINT       NOT NULL AUTO_INCREMENT,
    CATEGORY                  VARCHAR(60)  NOT NULL,
    TARGET_TABLE              VARCHAR(100) NOT NULL,
    TIMESTAMP_COLUMN          VARCHAR(60)  NOT NULL,
    RETENTION_DAYS            INT          NOT NULL,
    AUDIT_CATEGORY            TINYINT(1)   NOT NULL DEFAULT 0,
    REDACT_INSTEAD_OF_DELETE  TINYINT(1)   NOT NULL DEFAULT 0,
    REDACT_COLUMNS            VARCHAR(1000),
    ENABLED                   TINYINT(1)   NOT NULL DEFAULT 1,
    DESCRIPTION               VARCHAR(500),
    UPDATED_BY                VARCHAR(200),
    UPDATED_AT                DATETIME(6),
    PRIMARY KEY (ID),
    CONSTRAINT uk_retention_category UNIQUE (CATEGORY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Written even for dry runs: once the data is gone this log is the only evidence
-- that it existed and why it was removed.
CREATE TABLE IF NOT EXISTS DELETION_LOG (
    ID              BIGINT       NOT NULL AUTO_INCREMENT,
    CATEGORY        VARCHAR(60)  NOT NULL,
    TARGET_TABLE    VARCHAR(100) NOT NULL,
    RETENTION_DAYS  INT          NOT NULL,
    CUTOFF_DATE     DATETIME(6)  NOT NULL,
    ROWS_AFFECTED   INT          NOT NULL,
    ACTION          VARCHAR(20)  NOT NULL,
    DRY_RUN         TINYINT(1)   NOT NULL DEFAULT 1,
    EXECUTED_BY     VARCHAR(200) NOT NULL,
    EXECUTED_AT     DATETIME(6)  NOT NULL,
    DETAILS         VARCHAR(2000),
    SUCCEEDED       TINYINT(1)   NOT NULL DEFAULT 1,
    ERROR_DETAIL    VARCHAR(2000),
    PRIMARY KEY (ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ───────────────────────────── Indexes ─────────────────────────────
DROP PROCEDURE IF EXISTS add_security_indexes;
DELIMITER //
CREATE PROCEDURE add_security_indexes()
BEGIN
    DECLARE idx_missing INT;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PII_REVEAL_AUDIT'
                          AND INDEX_NAME = 'idx_reveal_user');
    IF idx_missing THEN CREATE INDEX idx_reveal_user ON PII_REVEAL_AUDIT(USER_ID); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PII_REVEAL_AUDIT'
                          AND INDEX_NAME = 'idx_reveal_complaint');
    IF idx_missing THEN CREATE INDEX idx_reveal_complaint ON PII_REVEAL_AUDIT(COMPLAINT_NUMBER); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'PII_REVEAL_AUDIT'
                          AND INDEX_NAME = 'idx_reveal_at');
    IF idx_missing THEN CREATE INDEX idx_reveal_at ON PII_REVEAL_AUDIT(REVEALED_AT); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SECURITY_EVENT'
                          AND INDEX_NAME = 'idx_secevent_subject');
    IF idx_missing THEN CREATE INDEX idx_secevent_subject ON SECURITY_EVENT(SUBJECT); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SECURITY_EVENT'
                          AND INDEX_NAME = 'idx_secevent_type');
    IF idx_missing THEN CREATE INDEX idx_secevent_type ON SECURITY_EVENT(EVENT_TYPE); END IF;

    -- Composite: the threshold query filters subject + type + time together.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SECURITY_EVENT'
                          AND INDEX_NAME = 'idx_secevent_lookup');
    IF idx_missing THEN
        CREATE INDEX idx_secevent_lookup ON SECURITY_EVENT(SUBJECT, EVENT_TYPE, OCCURRED_AT);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SECURITY_ALERT'
                          AND INDEX_NAME = 'idx_alert_status');
    IF idx_missing THEN CREATE INDEX idx_alert_status ON SECURITY_ALERT(STATUS); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SECURITY_ALERT'
                          AND INDEX_NAME = 'idx_alert_suppress');
    IF idx_missing THEN
        CREATE INDEX idx_alert_suppress ON SECURITY_ALERT(SUBJECT, ALERT_TYPE, RAISED_AT);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CREDENTIAL_REVOCATION'
                          AND INDEX_NAME = 'idx_revoke_user');
    IF idx_missing THEN CREATE INDEX idx_revoke_user ON CREDENTIAL_REVOCATION(USERNAME); END IF;

    -- The revocation-list refresh reads exactly this predicate on every cache miss.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'CREDENTIAL_REVOCATION'
                          AND INDEX_NAME = 'idx_revoke_active');
    IF idx_missing THEN CREATE INDEX idx_revoke_active ON CREDENTIAL_REVOCATION(ACTIVE); END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'DELETION_LOG'
                          AND INDEX_NAME = 'idx_deletion_at');
    IF idx_missing THEN CREATE INDEX idx_deletion_at ON DELETION_LOG(EXECUTED_AT); END IF;
END //
DELIMITER ;

CALL add_security_indexes();
DROP PROCEDURE IF EXISTS add_security_indexes;

-- ═══════════════════════════════════════════════════════════════════════════
-- Tunable thresholds. No hardcoded security values in code — all read from
-- SYSTEM_CONFIG at runtime so an operator can retune without a redeploy.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.pii.masking_enabled', 'true',
       'Master switch for complainant PII masking on API responses (UST875)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.pii.masking_enabled');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.pii.masked_fields',
       'complainantName,complainantPhone,complainantEmail,complainantAddress,accountNumber',
       'Comma-separated complainant fields masked by default (UST875)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.pii.masked_fields');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.pii.reveal_roles',
       'ADMIN,CEPC_DO,CEPC_OFFICER,CEPC_INCHARGE,CEPC_SUPERVISOR,CEPC_CLOSING_AUTHORITY,RBIO_OFFICER,RBIO_SUPERVISOR,RBIO_CONCILIATOR,RBIO_ADJUDICATOR',
       'Roles permitted to request an audited PII reveal; RE roles are excluded by design (UST875)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.pii.reveal_roles');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.pii.require_justification', 'true',
       'Require a typed reason before revealing complainant PII (UST875)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.pii.require_justification');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.pii.min_justification_length', '10',
       'Minimum characters in a PII reveal justification (UST875)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.pii.min_justification_length');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.enabled', 'true',
       'Master switch for suspicious-access detection (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.enabled');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.window_minutes', '10',
       'Sliding window in minutes over which security events are counted (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.window_minutes');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.denied_access_threshold', '10',
       'Denied-access events by one subject in the window before an alert is raised (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.denied_access_threshold');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.cross_entity_threshold', '3',
       'Cross-entity access attempts before an alert is raised; lower because it implies probing (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.cross_entity_threshold');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.pii_reveal_threshold', '25',
       'Authorised PII reveals by one user in the window before it looks like harvesting (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.pii_reveal_threshold');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.distinct_entity_threshold', '3',
       'Distinct entities touched in the window before enumeration is suspected (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.distinct_entity_threshold');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.anomaly.alert_suppression_minutes', '60',
       'Minutes before the same subject/type raises another alert, so a sustained attack cannot flood the console (UST873)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.anomaly.alert_suppression_minutes');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.retention.enabled', 'true',
       'Whether the nightly retention sweep evaluates policies (UST890)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.retention.enabled');

-- Deliberately false: the sweep reports what it would remove until an operator
-- turns this on. cms_db is shared and a wrong policy would be irreversible.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.retention.destructive_enabled', 'false',
       'When false the retention sweep is a dry run and deletes nothing (UST890)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.retention.destructive_enabled');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.security.retention.batch_limit', '5000',
       'Maximum rows one retention run may affect per policy (UST890)',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.security.retention.batch_limit');

-- ═══════════════════════════════════════════════════════════════════════════
-- Retention policies. Audit categories get 7 years (2555 days) independent of
-- the operational data they describe, so purging a complaint cannot erase the
-- record of who acted on it.
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'AUDIT_LOG', 'AUDIT_LOG', 'TIMESTAMP', 2555, 1, 0, 1,
       'Workflow audit trail — 7 year statutory retention, independent of complaint retention',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'AUDIT_LOG');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'PII_REVEAL_AUDIT', 'PII_REVEAL_AUDIT', 'REVEALED_AT', 2555, 1, 0, 1,
       'PII reveal trail — 7 year retention',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'PII_REVEAL_AUDIT');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'CONFIG_AUDIT_LOG', 'CONFIG_AUDIT_LOG', 'CHANGED_AT', 2555, 1, 0, 1,
       'Configuration change trail — 7 year retention',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'CONFIG_AUDIT_LOG');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'SECURITY_EVENT', 'SECURITY_EVENT', 'OCCURRED_AT', 730, 1, 0, 1,
       'Raw security events — 2 years; the derived alerts are kept longer',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'SECURITY_EVENT');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'SECURITY_ALERT', 'SECURITY_ALERT', 'RAISED_AT', 2555, 1, 0, 1,
       'Security alerts — 7 year retention',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'SECURITY_ALERT');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'IN_APP_NOTIFICATION', 'IN_APP_NOTIFICATIONS', 'CREATED_AT', 365, 0, 0, 1,
       'In-app notifications — 1 year, transient operational data',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'IN_APP_NOTIFICATION');

-- Redacts rather than deletes: deleting a closed complaint would orphan its
-- history, attachments and query threads. PII is cleared, the shell is kept.
-- Disabled by default because the column list must be confirmed against the
-- live schema before anything touches COMPLAINTS.
INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, REDACT_COLUMNS,
                              ENABLED, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'COMPLAINT_PII', 'COMPLAINTS', 'CLOSED_AT', 2555, 0, 1,
       'COMPLAINANT_NAME,COMPLAINANT_EMAIL,COMPLAINANT_PHONE,COMPLAINANT_ADDRESS,ACCOUNT_NUMBER',
       0,
       'Redacts complainant PII on complaints closed over 7 years ago, preserving the record shell. Disabled pending sign-off.',
       'V17_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'COMPLAINT_PII');

-- ═══════════════════════════════════════════════════════════════════════════
-- Translation keys for the security console and the reveal / deactivation UI.
-- Keys only; per-locale values are seeded by SecurityTranslationSeeder for all
-- ten supported locales (en,hi,mr,bn,te,ta,gu,ur,kn,ml).
-- ═══════════════════════════════════════════════════════════════════════════

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.reveal_button', 'security', 'Button revealing masked complainant details', 'Reveal details'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.reveal_button');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.reveal_reason_label', 'security', 'Label for the reveal justification field', 'Reason for viewing'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.reveal_reason_label');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.reveal_logged_notice', 'security', 'Warning that reveals are recorded',
       'This action is recorded against your name.'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.reveal_logged_notice');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.masked_hint', 'security', 'Hint shown beside a masked value', 'Hidden to protect personal data'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.masked_hint');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.reveal_not_permitted', 'security', 'Error when the role may not reveal PII',
       'You are not permitted to reveal complainant details.'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.reveal_not_permitted');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.alerts_title', 'security', 'Security alerts console heading', 'Security alerts'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.alerts_title');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.alerts_empty', 'security', 'Empty state for the alerts list', 'No security alerts'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.alerts_empty');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.acknowledge', 'security', 'Acknowledge an alert', 'Acknowledge'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.acknowledge');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.severity', 'security', 'Alert severity column', 'Severity'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.severity');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'security.subject', 'security', 'Alert subject column', 'User or IP'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'security.subject');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.deactivate_title', 'team', 'Safe deactivation dialog heading', 'Deactivate team member'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.deactivate_title');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.deactivate_open_records', 'team', 'Warning that open records must move',
       'This officer still holds open complaints. Choose who they transfer to.'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.deactivate_open_records');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.reassign_to_label', 'team', 'Successor picker label', 'Reassign open complaints to'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.reassign_to_label');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.revoke_access_label', 'team', 'Checkbox to also revoke credentials',
       'Also revoke sign-in access immediately'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.revoke_access_label');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.deactivate_confirm', 'team', 'Confirm safe deactivation', 'Deactivate'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.deactivate_confirm');

INSERT INTO TRANSLATION_KEYS (code, module, description, default_value)
SELECT 'team.deactivate_cancel', 'team', 'Cancel deactivation', 'Cancel'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM (SELECT code FROM TRANSLATION_KEYS) k
                               WHERE k.code = 'team.deactivate_cancel');
