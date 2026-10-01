-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V99 — Staff draft ownership, the autosave interval, and edit presence
-- Session S7 (UST673, UST674, UST675)
--
-- Re-runnable; information_schema guards throughout. Procedure prefix s7d_. Oracle counterpart V97.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- 1. WHY STAFF_DRAFT EXISTS RATHER THAN REUSING EMAIL_DRAFTS
--
--   UST674 wants "save in draft" at five workflow milestones — Register, Assessment, Conciliation,
--   Forward and Final Decision — each "visible only to the user who saved it". Only the first two
--   have anything today, and neither is really a draft:
--
--     * Register: rbio-create-complaint.component.ts:524 posts to /workflow/rbio/create-complaint
--       with status:'DRAFT'. WorkflowController.java:338 OVERRIDES that to "assigned", applies an
--       SLA clock and writes a CREATED timeline row. So "save draft" creates a LIVE, ASSIGNED
--       complaint with a real number. The status key is dead. There is also no resume path at all.
--     * Assessment: PUT /email-syndication/drafts/{id} works, but EMAIL_DRAFTS is an EMAIL intake
--       record — it is keyed to an inbound message and carries 60 intake-specific columns. The
--       Conciliation / Forward / Final Decision milestones have no inbound email, so they have no
--       EMAIL_DRAFTS row to update and cannot borrow one.
--
--   Hence a milestone-agnostic table keyed on (MILESTONE, OWNER_USER_ID, COMPLAINT_NUMBER).
--
-- 2. OWNERSHIP IS THE POINT, AND IT MUST BE SERVER-RESOLVED
--
--   No draft table in this schema has an authorship column. EMAIL_DRAFTS.ASSIGNED_TO and
--   .PROCESSED_BY are ASSIGNMENT and LAST-ACTOR, not "who saved this draft" — a draft you saved but
--   that is assigned to a colleague appears in THEIR list, not yours. Worse, the listing endpoint
--   takes the owner as a client-supplied query parameter, and
--   EmailSyndicationApiController.java:451 returns findAllByOrderByCreatedAtDesc() — EVERY draft in
--   the system — when that parameter is simply omitted.
--
--   OWNER_USER_ID is therefore written from the resolved JWT identity server-side, never from a
--   request field, and every read filters on it. The pattern copied is rbio-home's ASSIGNED_TO_ME,
--   which resolves the caller's own id on the server rather than trusting a username parameter.
--
-- 3. WHY THERE IS NO RECORD_LOCK TABLE (UST675)
--
--   The story says "prevents the second user from saving until the first completes or releases the
--   record", which reads like a lease. A lease is rejected as the ENFORCEMENT mechanism:
--
--     * A lease needs an expiry or a crashed browser locks a complaint forever. An expiry short
--       enough to be safe (a minute or two) is short enough to fire mid-edit, which converts a
--       concurrency problem into silent data loss — strictly worse.
--     * Enforcement already exists and is correct. Wave 0 added @Version to Complaint
--       (Complaint.java:135-138, column record_version, primitive long) and GlobalExceptionHandler
--       :225-237 maps the conflict to 409 with messageKey common.error_record_changed and
--       retryable:true. That is the authoritative "you cannot save over someone else's write".
--       A lease on top would be a SECOND answer to the same question, and two authorisation
--       decisions in different places is how they come to disagree.
--
--   So COMPLAINT_EDIT_PRESENCE is ADVISORY ONLY — it powers the UST675 *warning* ("X is editing
--   this") so the second user learns on OPEN rather than after typing. It never blocks a save.
--   Saving is gated by the version check alone. Because it is advisory, a stale row is harmless,
--   which is exactly why it can have a short heartbeat without risking data loss.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s7d_add_index;
DELIMITER //
CREATE PROCEDURE s7d_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══ 1. STAFF_DRAFT ══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS STAFF_DRAFT (
    ID               BIGINT       NOT NULL AUTO_INCREMENT,
    MILESTONE        VARCHAR(30)  NOT NULL COMMENT 'REGISTER | ASSESSMENT | CONCILIATION | FORWARD | FINAL_DECISION',
    OWNER_USER_ID    VARCHAR(200) NOT NULL COMMENT 'Resolved from the JWT server-side. NEVER from a request field',
    COMPLAINT_NUMBER VARCHAR(50)  NULL     COMMENT 'NULL for REGISTER — no complaint exists yet at that milestone',
    FORM_DATA_JSON   LONGTEXT     NOT NULL COMMENT 'The in-progress field values, restored verbatim on resume',
    DRAFT_STATUS     VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT | IN_PROGRESS — drives the UST674 label',
    SAVE_SOURCE      VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL | AUTOSAVE — an AUTOSAVE row is discarded on save-and-proceed (UST673)',
    CREATED_AT       DATETIME(6)  NULL,
    UPDATED_AT       DATETIME(6)  NULL,
    PRIMARY KEY (ID),
    -- One draft per owner per milestone per complaint. This is what makes a repeated autosave an
    -- UPDATE rather than an unbounded pile of rows: a 2-minute timer over an 8-hour shift would
    -- otherwise leave 240 rows per officer per complaint.
    -- COMPLAINT_NUMBER is nullable and MySQL treats NULLs as distinct in a UNIQUE KEY, so REGISTER
    -- drafts (which have no complaint yet) are deliberately NOT constrained by this key. The
    -- REGISTER path is keyed by owner alone in application code.
    UNIQUE KEY UK_STAFF_DRAFT_OWNER_MILESTONE (MILESTONE, OWNER_USER_ID, COMPLAINT_NUMBER)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s7d_add_index('STAFF_DRAFT', 'IDX_STAFF_DRAFT_OWNER', 'OWNER_USER_ID');
CALL s7d_add_index('STAFF_DRAFT', 'IDX_STAFF_DRAFT_COMPLAINT', 'COMPLAINT_NUMBER');

-- ═══ 2. COMPLAINT_EDIT_PRESENCE — advisory only, never blocks a save ═════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_EDIT_PRESENCE (
    ID               BIGINT       NOT NULL AUTO_INCREMENT,
    COMPLAINT_NUMBER VARCHAR(50)  NOT NULL,
    USER_ID          VARCHAR(200) NOT NULL COMMENT 'Resolved from the JWT server-side',
    DISPLAY_NAME     VARCHAR(250) NULL     COMMENT 'Shown in the UST675 warning so the second user knows WHO to coordinate with',
    HEARTBEAT_AT     DATETIME(6)  NOT NULL COMMENT 'Refreshed while the form is open. A row older than the staleness window is ignored, not deleted',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_EDIT_PRESENCE (COMPLAINT_NUMBER, USER_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s7d_add_index('COMPLAINT_EDIT_PRESENCE', 'IDX_EDIT_PRESENCE_HEARTBEAT', 'HEARTBEAT_AT');

-- ═══ 3. SYSTEM_CONFIG rows ═══════════════════════════════════════════════════════════════════
-- UST673 requires the autosave interval to be configurable, not a compiled-in literal. Read through
-- SystemConfigService.getInt (30s TTL local cache, typed, logged fallback) — deliberately NOT
-- TimelineConfigService, which rejects any key not prefixed "timeline.", is uncached, returns String,
-- and whose default map returns "30" for ANY unknown key. A wrong-but-plausible interval is the worst
-- possible failure mode for a timer, so a mechanism that invents one is unusable here.
--
-- NOT EXISTS-guarded per the V16:269 convention: a re-run must not overwrite an operator's tuning.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.draft.autosave_interval_seconds', '120',
       'Staff form auto-save interval in seconds (UST673). 0 disables auto-save entirely.',
       'V99_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.draft.autosave_interval_seconds');

-- How long an edit-presence heartbeat stays meaningful. Must exceed the client heartbeat period or
-- an actively-open form would flicker in and out of "being edited".
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.draft.edit_presence_stale_seconds', '90',
       'Seconds after which an edit-presence heartbeat is treated as stale and no longer warned about (UST675).',
       'V99_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.draft.edit_presence_stale_seconds');

-- UST617/671: the date-range bounds, server-side. The Angular component hardcodes 365 and 1
-- (report-builder.component.ts:31-32) and enforces them on a single (blur) handler whose return
-- value is discarded. Those are the values transcribed here so the server agrees with the UI it is
-- replacing as the authority; making them config means tuning does not need a release.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reports.max_date_range_days', '365',
       'Maximum report date-range span in days; a longer range is auto-capped from the From Date (UST617/671).',
       'V99_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reports.max_date_range_days');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reports.min_date_range_days', '1',
       'Minimum report date-range span in days; a shorter range is rejected (UST617/671).',
       'V99_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reports.min_date_range_days');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.reports.max_in_values', '100',
       'Maximum comma-separated values accepted for the IN operator (UST618).',
       'V99_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.reports.max_in_values');

DROP PROCEDURE IF EXISTS s7d_add_index;
