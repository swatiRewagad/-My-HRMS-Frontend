-- V16: RE <-> RBI query/correspondence threads (UST853, UST854, UST855, UST856, UST857, UST860)
-- MySQL version
--
-- Context: RE query state was three scalar columns on a per-complaint singleton row
-- (RE_RESPONSE_TRACKER.query_text / query_raised_at). RePortalService.raiseQuery did a blind
-- setQueryText(), so every new query silently OVERWROTE the previous one — the opposite of the
-- tamper-evident history UST857 requires. There was no GET for queries and no RBI-side reply
-- endpoint at all, so the RBI side could not answer an RE query except as a timeline remark
-- string, and the author was the hardcoded literal "RE_PORTAL".
--
-- RE_RESPONSE_TRACKER.query_text/query_raised_at are left in place and are now legacy-read-only;
-- new writes go to COMPLAINT_QUERY / COMPLAINT_QUERY_MESSAGE. extension_granted/extension_days
-- were dead columns (no setter call existed anywhere in the tree) and are now driven by the
-- extension-request approval path.
--
-- Re-running is safe: every CREATE uses IF NOT EXISTS and every ALTER is guarded on
-- information_schema (MySQL 8.4 has no ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS).

-- ═══════════════════════════════════════════════════════════════════════════
-- Thread header. One row per query raised, in either direction.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_QUERY (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id        BIGINT       NOT NULL,
    entity_code         VARCHAR(50)  NOT NULL,

    -- CLARIFICATION | EXTENSION_REQUEST | DOCUMENT_REQUEST | MEETING_REQUEST
    query_type          VARCHAR(30)  NOT NULL,
    subject             VARCHAR(500) NOT NULL,

    -- RE_TO_RBI | RBI_TO_RE
    direction           VARCHAR(20)  NOT NULL,
    -- RE | RBI | NONE  -- drives the "awaiting my response" filter (UST856)
    pending_with        VARCHAR(10)  NOT NULL,
    -- OPEN | RESOLVED | CLOSED
    status              VARCHAR(20)  NOT NULL,

    raised_by_user_id   VARCHAR(100) NOT NULL,
    raised_by_name      VARCHAR(200) NOT NULL,
    raised_by_role      VARCHAR(50)  NULL,
    -- RE | RBI
    raised_by_side      VARCHAR(10)  NOT NULL,
    raised_at           DATETIME(6)  NOT NULL,

    resolved_at         DATETIME(6)  NULL,
    resolved_by         VARCHAR(100) NULL,

    -- ═══ EXTENSION_REQUEST payload (UST853) ═══
    proposed_deadline   DATETIME(6)  NULL,
    extension_reason    TEXT         NULL,
    -- PENDING | APPROVED | REJECTED
    decision            VARCHAR(20)  NULL,
    decided_by          VARCHAR(100) NULL,
    decided_at          DATETIME(6)  NULL,
    decision_reason     TEXT         NULL,
    granted_deadline    DATETIME(6)  NULL,

    -- ═══ MEETING_REQUEST payload (UST855) ═══
    meeting_purpose     TEXT         NULL,
    -- PENDING | ACCEPTED | DECLINED | COUNTER_PROPOSED
    meeting_outcome     VARCHAR(20)  NULL,
    meeting_decline_reason TEXT      NULL,

    created_at          DATETIME(6)  NULL,
    updated_at          DATETIME(6)  NULL,

    PRIMARY KEY (id),
    KEY idx_cq_complaint (complaint_id),
    KEY idx_cq_pending (pending_with, status),
    KEY idx_cq_entity (entity_code),
    KEY idx_cq_type (query_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Append-only messages (UST857). No UPDATE or DELETE path exists in the
-- application; corrections are made by posting a follow-up message.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_QUERY_MESSAGE (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    query_id            BIGINT       NOT NULL,
    body                TEXT         NOT NULL,

    author_user_id      VARCHAR(100) NOT NULL,
    author_name         VARCHAR(200) NOT NULL,
    author_role         VARCHAR(50)  NULL,
    -- RE | RBI
    author_side         VARCHAR(10)  NOT NULL,

    -- MESSAGE | SYSTEM  -- SYSTEM rows record decisions (approve/reject/accept/decline)
    message_kind        VARCHAR(20)  NOT NULL,

    -- DATETIME(6) so ordering is stable for messages posted in the same second (UST857)
    posted_at           DATETIME(6)  NOT NULL,

    PRIMARY KEY (id),
    KEY idx_cqm_query (query_id, posted_at),
    CONSTRAINT fk_cqm_query FOREIGN KEY (query_id) REFERENCES COMPLAINT_QUERY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Document-request checklist (UST854). attachment_id links the satisfying
-- upload, so a checklist item is only resolvable with evidence attached.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_QUERY_DOC_ITEM (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    query_id            BIGINT       NOT NULL,
    item_label          VARCHAR(300) NOT NULL,
    item_description    VARCHAR(1000) NULL,
    display_order       INT          NOT NULL DEFAULT 0,

    resolved            BIT(1)       NOT NULL DEFAULT b'0',
    resolved_at         DATETIME(6)  NULL,
    resolved_by         VARCHAR(100) NULL,
    attachment_id       BIGINT       NULL,

    PRIMARY KEY (id),
    KEY idx_cq_doc_query (query_id),
    CONSTRAINT fk_cqdoc_query FOREIGN KEY (query_id) REFERENCES COMPLAINT_QUERY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Meeting slots (UST855). Counter-proposals append new PROPOSED rows rather
-- than mutating the original, so the negotiation stays auditable.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_QUERY_SLOT (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    query_id            BIGINT       NOT NULL,
    proposed_start      DATETIME(6)  NOT NULL,
    proposed_end        DATETIME(6)  NULL,
    -- PROPOSED | ACCEPTED | DECLINED | SUPERSEDED
    slot_status         VARCHAR(20)  NOT NULL,
    -- RE | RBI
    proposed_by_side    VARCHAR(10)  NOT NULL,
    display_order       INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NULL,

    PRIMARY KEY (id),
    KEY idx_cq_slot_query (query_id),
    CONSTRAINT fk_cqslot_query FOREIGN KEY (query_id) REFERENCES COMPLAINT_QUERY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Per-user read receipts (UST856). Unread = last_read_message_id is null or
-- behind the thread's newest message, so the badge count and the bell count
-- are derived from one source.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_QUERY_READ (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    query_id             BIGINT       NOT NULL,
    user_id              VARCHAR(100) NOT NULL,
    last_read_message_id BIGINT       NULL,
    last_read_at         DATETIME(6)  NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_cq_read (query_id, user_id),
    KEY idx_cq_read_user (user_id),
    CONSTRAINT fk_cqread_query FOREIGN KEY (query_id) REFERENCES COMPLAINT_QUERY(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Entity-side internal notes (UST860). Deliberately a SEPARATE table from the
-- query thread: UST857 requires thread messages to be permanently immutable,
-- while UST860 allows the author a 5-minute edit window. Keeping them apart
-- means the tamper-evident table has no mutable rows at all.
-- edit_locked_at is written by the server at insert (created_at + window) and
-- is what the server checks on edit — never a client-supplied timestamp.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS COMPLAINT_INTERNAL_NOTE (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id        BIGINT       NOT NULL,
    entity_code         VARCHAR(50)  NOT NULL,
    body                TEXT         NOT NULL,

    author_user_id      VARCHAR(100) NOT NULL,
    author_name         VARCHAR(200) NOT NULL,
    author_role         VARCHAR(50)  NULL,

    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NULL,
    edit_locked_at      DATETIME(6)  NOT NULL,
    edit_count          INT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_cin_complaint (complaint_id),
    KEY idx_cin_entity (entity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ═══════════════════════════════════════════════════════════════════════════
-- Link in-thread uploads to the message that carried them (UST854), and record
-- the uploader. Reuses COMPLAINT_ATTACHMENTS so existing size/type/virus
-- validation applies unchanged.
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_attachment_query_columns;

DELIMITER //
CREATE PROCEDURE add_attachment_query_columns()
BEGIN
    DECLARE col_missing INT;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
                          AND COLUMN_NAME = 'query_message_id');
    IF col_missing THEN
        ALTER TABLE COMPLAINT_ATTACHMENTS ADD COLUMN query_message_id BIGINT NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
                          AND COLUMN_NAME = 'uploaded_by');
    IF col_missing THEN
        ALTER TABLE COMPLAINT_ATTACHMENTS ADD COLUMN uploaded_by VARCHAR(100) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
                          AND INDEX_NAME = 'idx_attach_query_msg');
    IF col_missing THEN
        CREATE INDEX idx_attach_query_msg ON COMPLAINT_ATTACHMENTS(query_message_id);
    END IF;
END //
DELIMITER ;

CALL add_attachment_query_columns();
DROP PROCEDURE IF EXISTS add_attachment_query_columns;

-- ═══════════════════════════════════════════════════════════════════════════
-- SLA clock pause for approved extensions (UST853).
-- The clock keeps running while a request is PENDING; on approval the server
-- pauses it and sets the new deadline. sla_pause_minutes accumulates so a
-- second extension does not lose the first pause.
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS add_tracker_sla_pause_columns;

DELIMITER //
CREATE PROCEDURE add_tracker_sla_pause_columns()
BEGIN
    DECLARE col_missing INT;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER'
                          AND COLUMN_NAME = 'sla_paused');
    IF col_missing THEN
        ALTER TABLE RE_RESPONSE_TRACKER ADD COLUMN sla_paused BIT(1) NULL DEFAULT b'0';
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER'
                          AND COLUMN_NAME = 'sla_paused_at');
    IF col_missing THEN
        ALTER TABLE RE_RESPONSE_TRACKER ADD COLUMN sla_paused_at DATETIME(6) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'RE_RESPONSE_TRACKER'
                          AND COLUMN_NAME = 'sla_pause_minutes');
    IF col_missing THEN
        ALTER TABLE RE_RESPONSE_TRACKER ADD COLUMN sla_pause_minutes BIGINT NULL DEFAULT 0;
    END IF;
END //
DELIMITER ;

CALL add_tracker_sla_pause_columns();
DROP PROCEDURE IF EXISTS add_tracker_sla_pause_columns;

-- ═══════════════════════════════════════════════════════════════════════════
-- Configurable limits. No hardcoded values in code — these are read from
-- SYSTEM_CONFIG at runtime.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.query.internal_note_edit_window_minutes', '5',
       'Minutes an author may edit their own internal note before it locks (UST860)',
       'V16_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.query.internal_note_edit_window_minutes');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.query.max_extension_days', '30',
       'Maximum extension days an RBI approver may grant on an RE response deadline (UST853)',
       'V16_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.query.max_extension_days');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.query.max_meeting_slots', '5',
       'Maximum proposed meeting slots per meeting-request query (UST855)',
       'V16_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.query.max_meeting_slots');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.query.max_checklist_items', '20',
       'Maximum checklist items per document-request query (UST854)',
       'V16_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG
                    WHERE config_key = 'cms.query.max_checklist_items');
