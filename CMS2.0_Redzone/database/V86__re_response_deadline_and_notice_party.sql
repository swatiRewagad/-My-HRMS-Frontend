-- ============================================================
-- V86 — RE response deadlines, the 13(1) notice addressee, and the overdue flag
-- Session S3 (UST779, UST780, UST637, UST638)
-- ============================================================
--
-- WHY
--
--   COMPLAINTS.re_response_deadline already existed but had exactly ONE writer in the whole repository:
--   ComplaintQueryService's extension-grant path. So it was NULL on every complaint that had never been
--   granted an extension — all of them. NotificationScheduledTasks.checkReResponseDeadline filters on
--   `re_response_deadline IS NOT NULL`, so the 30-minute sweep swept an empty set and no unresponsive
--   entity was ever chased. The machinery existed and nothing fed it.
--
--   Issuing a 13(1) notice now sets the deadline (UST780). It is written to the NODAL_OFFICER_RECORDS row
--   as well, because the staleness escalations read the NO record: a deadline only on the complaint is
--   invisible to the job meant to act on it.
--
--   notice_13_1_target_party closes a silent data loss. The impleading screen has always sent
--   `targetParty` and the FX_NOTICE_13_1 side effect never read it, so the ADDRESSEE of a statutory
--   communication was discarded while the request answered 200. A notice whose recipient is unrecorded
--   cannot be evidenced later.
--
-- WHY A DATE AND NOT A TIMESTAMP
--
--   The officer picks a calendar day and the Scheme speaks in days. Storing a time would invent a
--   precision nobody decided, and would make "is it overdue?" depend on the hour a notice was issued.
--
-- WHY THE OVERDUE FLAG IS PERSISTED (UST637)
--
--   The complaint grid whitelists SORTABLE columns, so a value derived in Java after the page is fetched
--   cannot be ordered by the database — "show me the overdue ones first" would silently not work. It also
--   makes every client agree: a browser comparing two dates can be wrong about the timezone and can be
--   working from a stale page, which is no basis for a statutory window. The sweep CLEARS the flag as well
--   as setting it, which is what makes the highlight disappear once the entity responds.
--
-- ALL COLUMNS NULLABLE. ddl-auto=update runs against a shared database, so NOT NULL here would be
-- permanent for every other session and would break their inserts. Nothing is backfilled: inventing a
-- deadline for a historical complaint would assert that an entity was given a window it never received.
--
-- Re-running is safe: MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so every ALTER is guarded
-- on information_schema and every seed is INSERT ... WHERE NOT EXISTS.
-- ============================================================

DROP PROCEDURE IF EXISTS s3_dl_add_column;
DROP PROCEDURE IF EXISTS s3_dl_add_index;

DELIMITER $$

CREATE PROCEDURE s3_dl_add_column(
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

CREATE PROCEDURE s3_dl_add_index(
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
-- 1. The 13(1) notice addressee
-- ─────────────────────────────────────────────────────────────
CALL s3_dl_add_column('COMPLAINTS', 'notice_13_1_target_party', 'VARCHAR(250) NULL');

-- ─────────────────────────────────────────────────────────────
-- 2. The deadline on the NO record, plus which communication set it
-- ─────────────────────────────────────────────────────────────
CALL s3_dl_add_column('NODAL_OFFICER_RECORDS', 're_response_deadline', 'DATE NULL');
CALL s3_dl_add_column('NODAL_OFFICER_RECORDS', 'deadline_communication', 'VARCHAR(50) NULL');

-- ─────────────────────────────────────────────────────────────
-- 3. The server-computed overdue flag (UST637)
-- ─────────────────────────────────────────────────────────────
CALL s3_dl_add_column('COMPLAINTS', 're_response_overdue', 'BIT(1) NULL');

-- The sweep scans by deadline and the grid filters by the flag; without these both are full scans of a
-- table that will hold the national complaint volume.
CALL s3_dl_add_index('NODAL_OFFICER_RECORDS', 'idx_no_re_deadline', 're_response_deadline');
CALL s3_dl_add_index('COMPLAINTS', 'idx_complaint_re_deadline', 're_response_deadline');
CALL s3_dl_add_index('COMPLAINTS', 'idx_complaint_re_overdue', 're_response_overdue');

DROP PROCEDURE IF EXISTS s3_dl_add_column;
DROP PROCEDURE IF EXISTS s3_dl_add_index;

-- ─────────────────────────────────────────────────────────────
-- 4. UST638's sweep interval, and UST637's configurable highlight colour
-- ─────────────────────────────────────────────────────────────
-- The interval is a SYSTEM_CONFIG row rather than a property because a placeholder in @Scheduled is
-- resolved once at bean creation — changing it needs a restart, which UST638 forbids. Every other
-- scheduled job in this codebase is exactly that static shape; ReDeadlineSweepScheduler is the first with
-- a Trigger that re-reads its interval on each fire.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 're.deadline.sweep_interval_minutes', '30',
       'Minutes between RE-deadline overdue sweeps. Read on every fire, so a change needs no restart.',
       'V86_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 're.deadline.sweep_interval_minutes');

-- A hex colour cannot go through TimelineConfigController, which accepts only keys prefixed 'timeline.'
-- and values parsing as an integer between 1 and 365. Stored here so the UI reads it rather than
-- hardcoding a shade in SCSS, which is what every other status colour in the app currently does.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 're.deadline.overdue_highlight_colour', '#b91c1c',
       'Row highlight for a complaint whose RE response is overdue (UST637).',
       'V86_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 're.deadline.overdue_highlight_colour');
