-- V18: RE Activity Status ladder (UST846, UST847, UST848, UST849, UST850, UST851, UST852)
-- MySQL version
--
-- Context: there was no activity-status concept at all. COMPLAINTS.status is the regulatory
-- workflow state in lowercase snake_case ("pending", "re_responded"), which answers "where is this
-- complaint in the process" but not "has the entity actually started work on it" — the question
-- CEPC/RBI staff currently resolve by telephoning the bank (CEPC BRD Gap 04).
--
-- Four things this migration establishes:
--   1. RE_ACTIVITY_STATUS on COMPLAINTS, a separate 7-level ladder in UPPER_SNAKE_CASE. It is NOT
--      the same column as `status` and never replaces it. New columns use uppercase deliberately:
--      the mixed-case legacy vocabulary is what produced status CSS classes that matched nothing.
--   2. RE_ACTIVITY_NUDGE_DAYS — the nudge threshold SNAPSHOTTED when a record enters a status, so
--      that later editing of the SYSTEM_CONFIG default cannot retroactively make historical records
--      nudge-due, nor silently forgive ones already past due (UST850).
--   3. COMPLAINT_TIMELINE.EVENT_SOURCE — an explicit AUTOMATIC/MANUAL discriminator (UST848).
--      Previously the only signal was performedBy, written as "SYSTEM", "System" and "system" by
--      different services, so "what did the entity actually do" was unanswerable.
--   4. CONFIG_CHANGE_REQUEST — maker-checker staging for nudge thresholds (UST851). A threshold
--      governs when staff are told an entity has stalled, so one administrator raising it to 365
--      would suppress the signal estate-wide unnoticed.
--
-- Re-running is safe: every ALTER is guarded on information_schema and every seed is
-- INSERT ... WHERE NOT EXISTS (MySQL 8.4 has no ADD COLUMN IF NOT EXISTS).

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. RE Activity Status ladder on COMPLAINTS
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_re_activity_columns;
DELIMITER //
CREATE PROCEDURE add_re_activity_columns()
BEGIN
    DECLARE col_missing BOOLEAN;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 're_activity_status');
    IF col_missing THEN
        -- NULL means "never touched" and is read as NOT_OPENED, so historical rows need no backfill.
        ALTER TABLE COMPLAINTS ADD COLUMN re_activity_status VARCHAR(30) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 're_activity_changed_at');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN re_activity_changed_at DATETIME(6) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 're_activity_nudge_days');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN re_activity_nudge_days INT NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 're_activity_nudged_at');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN re_activity_nudged_at DATETIME(6) NULL;
    END IF;
END //
DELIMITER ;

CALL add_re_activity_columns();
DROP PROCEDURE IF EXISTS add_re_activity_columns;

-- The nudge sweep filters on (status, nudged_at IS NULL) and orders by changed_at, so this index
-- covers it. Age is deliberately NOT in the query — the threshold is per-row (see note 2 above).
DROP PROCEDURE IF EXISTS add_re_activity_index;
DELIMITER //
CREATE PROCEDURE add_re_activity_index()
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
           AND INDEX_NAME = 'idx_complaint_re_activity') THEN
        CREATE INDEX idx_complaint_re_activity
            ON COMPLAINTS (re_activity_status, re_activity_nudged_at, re_activity_changed_at);
    END IF;
END //
DELIMITER ;

CALL add_re_activity_index();
DROP PROCEDURE IF EXISTS add_re_activity_index;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. Draft response on RE_RESPONSE_TRACKER
--    Kept separate from response_text: a draft is entity-private working material, whereas
--    response_text is the submitted answer RBI acts on. Conflating them would make an unfinished
--    draft look like a response on the staff side.
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_tracker_draft_columns;
DELIMITER //
CREATE PROCEDURE add_tracker_draft_columns()
BEGIN
    DECLARE col_missing BOOLEAN;
    DECLARE table_present BOOLEAN;

    SET table_present := (SELECT COUNT(*) > 0 FROM information_schema.TABLES
                          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER');

    IF table_present THEN
        SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER'
                              AND COLUMN_NAME = 'draft_response_text');
        IF col_missing THEN
            ALTER TABLE RE_RESPONSE_TRACKER ADD COLUMN draft_response_text TEXT NULL;
        END IF;

        SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER'
                              AND COLUMN_NAME = 'draft_saved_at');
        IF col_missing THEN
            ALTER TABLE RE_RESPONSE_TRACKER ADD COLUMN draft_saved_at DATETIME(6) NULL;
        END IF;
    END IF;
END //
DELIMITER ;

CALL add_tracker_draft_columns();
DROP PROCEDURE IF EXISTS add_tracker_draft_columns;

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. AUTOMATIC vs MANUAL discriminator on COMPLAINT_TIMELINE (UST848)
--    Backfilled from performedBy, which is the only signal the old rows carry. Rows whose actor is
--    genuinely ambiguous are left MANUAL rather than guessed: over-claiming a row as system-derived
--    would misattribute a human decision in an audit trail.
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_timeline_event_source;
DELIMITER //
CREATE PROCEDURE add_timeline_event_source()
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINT_TIMELINE'
           AND COLUMN_NAME = 'event_source') THEN
        ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN event_source VARCHAR(20) NULL DEFAULT 'MANUAL';

        -- 'RE_PORTAL' is deliberately NOT in this list. RePortalService writes that literal for
        -- queries an RE user actually raised, so classifying it AUTOMATIC would relabel real entity
        -- work as machine-generated — the exact misattribution this column exists to prevent.
        UPDATE COMPLAINT_TIMELINE
           SET event_source = 'AUTOMATIC'
         WHERE UPPER(TRIM(COALESCE(performed_by, ''))) IN ('SYSTEM', 'SCHEDULER', 'AUTO', 'AUTOMATED');

        UPDATE COMPLAINT_TIMELINE
           SET event_source = 'MANUAL'
         WHERE event_source IS NULL;
    END IF;
END //
DELIMITER ;

CALL add_timeline_event_source();
DROP PROCEDURE IF EXISTS add_timeline_event_source;

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. Maker-checker staging for config changes (UST851)
--    The existing CONFIG_AUDIT_LOG records what changed after the fact; this records the intent and
--    the two-person decision before it takes effect. CONFIG_AUDIT_LOG is still written on approval,
--    so it remains the single place to read "what actually changed".
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS CONFIG_CHANGE_REQUEST (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    config_key       VARCHAR(100) NOT NULL,

    -- The value in force when the request was raised. Compared again at approval time so a request
    -- that went stale (someone else moved the value) is refused rather than silently overwriting.
    current_value    VARCHAR(500) NULL,
    proposed_value   VARCHAR(500) NOT NULL,
    reason           VARCHAR(500) NULL,

    requested_by     VARCHAR(200) NOT NULL,
    requested_at     DATETIME(6)  NOT NULL,

    -- PENDING | APPROVED | REJECTED | WITHDRAWN
    -- WITHDRAWN is distinct from REJECTED: withdrawing your own request is legitimate but is not an
    -- independent rejection, and conflating them would overstate the review that took place.
    status           VARCHAR(20)  NOT NULL,

    decided_by       VARCHAR(200) NULL,
    decided_at       DATETIME(6)  NULL,
    decision_reason  VARCHAR(500) NULL,

    PRIMARY KEY (id),
    INDEX idx_ccr_status (status),
    INDEX idx_ccr_key (config_key),
    INDEX idx_ccr_requested_at (requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_days.default', '3',
       'Default days a record may sit in an early RE activity status before the owning officer is nudged (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_days.default');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_days.not_opened', '2',
       'Days a forwarded record may sit unopened by the entity before nudging (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_days.not_opened');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_days.opened', '3',
       'Days a record may sit at Opened with no further entity progress before nudging (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_days.opened');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_days.under_review', '3',
       'Days a record may sit at Under Review before nudging (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_days.under_review');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_days.response_being_prepared', '5',
       'Days a record may sit at Response Being Prepared before nudging (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_days.response_being_prepared');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.nudge_enabled', 'true',
       'Master switch for the stuck-record nudge sweep (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.nudge_enabled');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.overdue_escalation_enabled', 'true',
       'Master switch for the overdue flip and its escalation notification (UST849)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.overdue_escalation_enabled');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.re.activity.sweep_batch_size', '500',
       'Maximum records examined per nudge sweep run (UST850)',
       'V18_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.re.activity.sweep_batch_size');
