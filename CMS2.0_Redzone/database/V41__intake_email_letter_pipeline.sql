-- V41: Inbound email & letter intake — recipient headers, DB-backed ignore list, suppression audit,
--      real deduplication, OCR gating.
-- MySQL version. Oracle twin: database/oracle/V39__intake_email_letter_pipeline.sql
--
-- Context: the intake pipeline could not satisfy its own user stories.
--
--   1. RECIPIENT HEADERS WERE DISCARDED. To/CC/BCC arrived at the ingest API, were used once for a
--      CRPC routing rule, then dropped. Every Exceptional-Email-Master rule written against To, CC,
--      BCC or Subject was therefore unenforceable — the data it matched on did not survive the
--      request. cms-mail-intake (the Netty SMTP receiver) already POSTs toRecipients/ccRecipients/
--      bccRecipients to /api/v1/email-syndication/ingest, so these columns make an existing
--      producer's output usable rather than adding speculative fields.
--
--   2. MESSAGE_ID WAS NOT UNIQUE and the ingest path overwrote it with a fresh random UUID, so the
--      same message redelivered created a second draft and cross-restart idempotency was impossible.
--      cms-mail-intake supplies a stable 'mail-intake-<id>', which this constraint turns into real
--      once-only delivery. Pre-existing duplicate/NULL values are repaired before the index is added,
--      because adding a unique index to dirty data fails and would abort the migration.
--
--   3. THE IGNORE LIST LIVED IN A SYNCHRONIZED ARRAYLIST IN THE CONTROLLER and was lost on every
--      restart, while this table sat unused. The rules are now read from here on each ingestion, so
--      an admin edit takes effect on the next email with no downtime. The model was sender-only;
--      From/To/CC/BCC/Subject plus a counter-rule (EXCEPTION_PATTERN) are added. MATCH_FIELD
--      defaults to 'FROM' so existing rows keep their present meaning without a data migration.
--
--   4. SUPPRESSED EMAILS LEFT NO TRACE. A dropped citizen email was unauditable and the ignored
--      count on the stats endpoint was the literal 0. IGNORED_EMAIL_LOG records the sender, subject,
--      timestamp AND the identity of the rule that suppressed it, which is what makes the report
--      and its CSV export possible.
--
--   5. OCR GATING. OCR ran before language detection, so it could never be suppressed, and detection
--      read the email body only — a vernacular scanned letter was never detected as vernacular.
--      OCR_SKIP_REASON and REQUIRES_MANUAL_ENTRY record the fail-closed decision to route content to
--      a human instead of guessing at a legal record.
--
--      NOTE: no handwriting classifier exists in this system and none is claimed here. The gate is
--      script + confidence based.
--
--   6. SUGGESTED-RELATED accept/dismiss had nowhere to persist, so the decision died on reload.
--
-- Re-running is safe: every ALTER is guarded on information_schema and every backfill is
-- conditional. MySQL 8.4 has no ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS.

-- ═══════════════════════════════════════════════════════════════════════════
-- Guard helpers
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS s2b_add_column;
DELIMITER //
CREATE PROCEDURE s2b_add_column(IN p_table VARCHAR(64), IN p_col VARCHAR(64), IN p_type VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_col) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_col, ' ', p_type);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS s2b_add_index;
DELIMITER //
CREATE PROCEDURE s2b_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS s2b_add_unique;
DELIMITER //
CREATE PROCEDURE s2b_add_unique(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE UNIQUE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. EMAIL_DRAFTS — recipient headers, threading, OCR gating, suggestion decision
-- ═══════════════════════════════════════════════════════════════════════════
CALL s2b_add_column('EMAIL_DRAFTS', 'to_recipients',   'VARCHAR(1000) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'cc_recipients',   'VARCHAR(1000) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'bcc_recipients',  'VARCHAR(1000) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'reply_to',        'VARCHAR(200) NULL');

-- Threading. Without in-reply-to/references the "thread" was a generated UUID per message, so
-- replies on one matter appeared as unrelated drafts.
CALL s2b_add_column('EMAIL_DRAFTS', 'in_reply_to',     'VARCHAR(500) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'email_references', 'TEXT NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'attachment_count', 'INT NULL');

-- OCR gating (fail-closed manual-entry routing)
CALL s2b_add_column('EMAIL_DRAFTS', 'ocr_skip_reason',       'VARCHAR(40) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'requires_manual_entry', 'TINYINT(1) NOT NULL DEFAULT 0');

-- Suggested-related accept/dismiss
CALL s2b_add_column('EMAIL_DRAFTS', 'suggested_related_draft_id',   'VARCHAR(100) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'suggested_related_decision',   'VARCHAR(20) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'suggested_related_decided_by', 'VARCHAR(200) NULL');
CALL s2b_add_column('EMAIL_DRAFTS', 'suggested_related_decided_at', 'DATETIME(6) NULL');

-- Repair before constraining: a blank messageId is meaningless, and duplicates must be collapsed
-- to one owner or the unique index cannot be created.
UPDATE EMAIL_DRAFTS SET message_id = NULL WHERE message_id = '';

UPDATE EMAIL_DRAFTS d
JOIN (
    SELECT message_id, MIN(id) AS keep_id
    FROM EMAIL_DRAFTS
    WHERE message_id IS NOT NULL
    GROUP BY message_id
    HAVING COUNT(*) > 1
) dup ON d.message_id = dup.message_id AND d.id <> dup.keep_id
SET d.message_id = CONCAT('legacy-dup-', d.id);

CALL s2b_add_unique('EMAIL_DRAFTS', 'uk_email_drafts_message_id', 'message_id');
CALL s2b_add_index('EMAIL_DRAFTS', 'idx_draft_manual_entry', 'requires_manual_entry');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. EMAIL_DRAFT_ATTACHMENTS — link a duplicate's attachments to the parent complaint
-- ═══════════════════════════════════════════════════════════════════════════
-- Story: on a confirmed duplicate, create NO new draft and attach the email and its files to the
-- EXISTING parent. draft_id stays populated so provenance is not lost.
CALL s2b_add_column('EMAIL_DRAFT_ATTACHMENTS', 'linked_complaint_number', 'VARCHAR(50) NULL');
CALL s2b_add_index('EMAIL_DRAFT_ATTACHMENTS', 'idx_draft_att_parent', 'linked_complaint_number');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. EMAIL_IGNORE_LIST — Exceptional Email Master
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS email_ignore_list (
    id                BIGINT NOT NULL AUTO_INCREMENT,
    email_pattern     VARCHAR(300) NOT NULL,
    pattern_type      VARCHAR(20)  NOT NULL DEFAULT 'EXACT',
    reason            VARCHAR(500) NULL,
    added_by          VARCHAR(100) NULL,
    is_active         BIT(1)       NOT NULL DEFAULT b'1',
    created_at        DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- MATCH_FIELD defaults to FROM so every pre-existing sender-only rule keeps its current behaviour.
CALL s2b_add_column('email_ignore_list', 'match_field',       "VARCHAR(20) NOT NULL DEFAULT 'FROM'");
CALL s2b_add_column('email_ignore_list', 'to_pattern',        'VARCHAR(300) NULL');
CALL s2b_add_column('email_ignore_list', 'cc_pattern',        'VARCHAR(300) NULL');
CALL s2b_add_column('email_ignore_list', 'bcc_pattern',       'VARCHAR(300) NULL');
CALL s2b_add_column('email_ignore_list', 'subject_pattern',   'VARCHAR(500) NULL');
CALL s2b_add_column('email_ignore_list', 'exception_pattern', 'VARCHAR(500) NULL');

CALL s2b_add_index('email_ignore_list', 'idx_ignore_active', 'is_active');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. IGNORED_EMAIL_LOG — suppression audit (report + CSV export)
-- ═══════════════════════════════════════════════════════════════════════════
-- matched_rule_* are denormalised on purpose: the report must remain truthful about which rule
-- suppressed a mail even after that rule is edited or deleted.
CREATE TABLE IF NOT EXISTS IGNORED_EMAIL_LOG (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    sender_email          VARCHAR(200) NULL,
    subject               VARCHAR(500) NULL,
    to_recipients         VARCHAR(500) NULL,
    cc_recipients         VARCHAR(500) NULL,
    bcc_recipients        VARCHAR(500) NULL,
    message_id            VARCHAR(200) NULL,
    matched_rule_id       BIGINT       NULL,
    matched_rule_pattern  VARCHAR(300) NULL,
    matched_rule_field    VARCHAR(20)  NULL,
    matched_rule_type     VARCHAR(20)  NULL,
    matched_rule_reason   VARCHAR(500) NULL,
    received_at           DATETIME(6)  NULL,
    created_at            DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s2b_add_index('IGNORED_EMAIL_LOG', 'idx_ignored_email_sender',   'sender_email');
CALL s2b_add_index('IGNORED_EMAIL_LOG', 'idx_ignored_email_rule',     'matched_rule_id');
CALL s2b_add_index('IGNORED_EMAIL_LOG', 'idx_ignored_email_received', 'received_at');

DROP PROCEDURE IF EXISTS s2b_add_column;
DROP PROCEDURE IF EXISTS s2b_add_index;
DROP PROCEDURE IF EXISTS s2b_add_unique;
