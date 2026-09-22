-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V54 — AA hearing history, immutable order revisions, and the citizen-notice outbox
--
-- Session S3C. Re-runnable: CREATE TABLE uses IF NOT EXISTS, and every index / unique key is guarded
-- through information_schema, because MySQL 8.4 has no IF NOT EXISTS for CREATE INDEX or for
-- ALTER TABLE ... ADD CONSTRAINT.
--
-- Procedure names are prefixed s3c_ rather than reusing the aa_ helpers from V31 or the s2c_ ones
-- from V46. Both of those are DROPped at the end of the file that defines them, so a concurrently
-- running session re-creating or dropping them would clash with this script mid-flight.
--
-- WHY THESE THREE TABLES EXIST
--
--   APPEAL_HEARING    — append-only hearing history. APPEALS.hearing_date / hearing_venue were
--                       overwritten in place by SCHEDULE_HEARING, so a reschedule destroyed the fact
--                       that an earlier date had ever been fixed and notified to the parties. For a
--                       statutory hearing that is an audit failure: "the hearing was moved twice" and
--                       "the hearing was always on this date" must be different, provable facts. A
--                       superseded row is only STAMPED (superseded_at / superseded_by_id), never
--                       rewritten; the operative hearing is the one row with superseded_at IS NULL.
--
--   APPEAL_ORDER      — immutable order revisions. PASS_ORDER wrote APPEALS.order_* in place with no
--                       terminal guard, so a re-POST silently replaced the outcome, summary, amount
--                       and date of an order that had already ISSUED. An issued order is a legal
--                       instrument and cannot be edited away. A correction APPENDS revision N+1 with
--                       supersedes_order_id and a mandatory correction_reason.
--
--   AA_CITIZEN_NOTICE — a notice OUTBOX, not a send log. cms-backend has NO email or SMS gateway at
--                       all (the SMS path is a log.info TODO; cms-notification-service is a bare
--                       placeholder). So a notice is recorded as a PENDING OBLIGATION and nothing in
--                       Phase 1 ever marks it SENT — no screen can then truthfully claim delivery,
--                       and the system can always answer "what were we required to tell this citizen,
--                       and have we actually told them?".
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s3c_add_index;
DELIMITER //
CREATE PROCEDURE s3c_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS s3c_add_unique;
DELIMITER //
CREATE PROCEDURE s3c_add_unique(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('ALTER TABLE ', p_table, ' ADD CONSTRAINT ', p_index, ' UNIQUE (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. APPEAL_HEARING — one row per hearing EVENT (scheduled/rescheduled/adjourned/completed/cancelled)
--
-- Every hearing particular is updatable=false on the entity; only superseded_at / superseded_by_id
-- are writable, so JPA itself refuses to rewrite the date or venue a party was notified about. The
-- outcome of a hearing is recorded by APPENDING a COMPLETED/ADJOURNED row, not by mutating the
-- SCHEDULED one. presiding_officer is stored per event rather than read off the appeal, because the
-- appeal's assignee can change after a listing and the double-booking check must reflect who was
-- actually listed at the time.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS APPEAL_HEARING (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    appeal_number     VARCHAR(50)   NOT NULL,
    -- 1-based per appeal. Gives the parties a stable "third hearing" reference.
    sequence_no       INT           NOT NULL,
    -- SCHEDULED | RESCHEDULED | ADJOURNED | COMPLETED | CANCELLED
    event_type        VARCHAR(20)   NOT NULL,
    hearing_date      DATETIME(6)   NOT NULL,
    hearing_venue     VARCHAR(500)  NULL,
    -- IN_PERSON | VIDEO | HYBRID
    hearing_mode      VARCHAR(20)   NULL,
    presiding_officer VARCHAR(200)  NULL,
    -- Set only on COMPLETED/ADJOURNED rows: HEARD | ADJOURNED | NOT_HELD | CONCLUDED
    outcome           VARCHAR(40)   NULL,
    outcome_remarks   VARCHAR(2000) NULL,
    -- Why it was rescheduled/adjourned. Mandatory for those events at the service boundary.
    reason            VARCHAR(1000) NULL,
    -- NULL means this is the operative hearing row.
    superseded_at     DATETIME(6)   NULL,
    superseded_by_id  BIGINT        NULL,
    performed_by      VARCHAR(200)  NOT NULL,
    performed_by_role VARCHAR(50)   NULL,
    performed_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL s3c_add_index('APPEAL_HEARING', 'idx_ah_appeal', 'appeal_number');
CALL s3c_add_index('APPEAL_HEARING', 'idx_ah_active', 'appeal_number, superseded_at');
CALL s3c_add_index('APPEAL_HEARING', 'idx_ah_officer_date', 'presiding_officer, hearing_date');
CALL s3c_add_index('APPEAL_HEARING', 'idx_ah_performed_at', 'performed_at');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. APPEAL_ORDER — one row per order REVISION
--
-- ed_approval_given / ed_approval_by / ed_approval_at are COPIED onto the order as they stood when it
-- issued, not read live off the appeal: the appeal's approval columns can be re-registered, but what
-- approved THIS order cannot change afterwards. clause_code is validated against
-- CLOSURE_CLAUSE_MASTER at the service boundary rather than being free text, so the ground cited is
-- always a real clause of the Scheme in force.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS APPEAL_ORDER (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    appeal_number          VARCHAR(50)    NOT NULL,
    -- 1 = the original order; 2+ = successive corrections.
    revision_no            INT            NOT NULL,
    -- The revision this one corrects. NULL on revision 1.
    supersedes_order_id    BIGINT         NULL,
    -- Mandatory when revision_no > 1 — a correction without a stated reason is not auditable.
    correction_reason      VARCHAR(1000)  NULL,
    -- UPHELD | MODIFIED | SET_ASIDE | REMANDED | DISMISSED
    outcome                VARCHAR(30)    NOT NULL,
    order_summary          TEXT           NOT NULL,
    award_amount           DECIMAL(15,2)  NULL,
    clause_code            VARCHAR(40)    NULL,
    ground                 VARCHAR(300)   NULL,
    issuing_authority      VARCHAR(200)   NOT NULL,
    issuing_authority_role VARCHAR(50)    NULL,
    order_date             DATETIME(6)    NOT NULL,
    ed_approval_given      TINYINT(1)     NULL,
    ed_approval_by         VARCHAR(200)   NULL,
    ed_approval_at         DATETIME(6)    NULL,
    -- NULL means this is the operative order.
    superseded_at          DATETIME(6)    NULL,
    superseded_by_id       BIGINT         NULL,
    performed_by           VARCHAR(200)   NOT NULL,
    performed_by_role      VARCHAR(50)    NULL,
    performed_at           DATETIME(6)    NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL s3c_add_index('APPEAL_ORDER', 'idx_ao_appeal', 'appeal_number');
CALL s3c_add_index('APPEAL_ORDER', 'idx_ao_operative', 'appeal_number, superseded_at');
CALL s3c_add_index('APPEAL_ORDER', 'idx_ao_order_date', 'order_date');

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. AA_CITIZEN_NOTICE — outbox of notices OWED, one row per recipient per event
--
-- message_key holds a translation key, not rendered English: the citizen's locale is unknown when the
-- obligation arises, so the text is resolved at dispatch and no statutory wording is frozen into the
-- row. attempt_count / last_error / next_attempt_at exist now so the eventual gateway is a plain
-- dispatcher over this table and needs no schema change.
--
-- Distinct from NOTIFICATION_DELIVERY_LOG, which records ATTEMPTS already made on the working in-app
-- channel. This records an OBLIGATION whose channel does not exist yet, so its rows are mutable in
-- status only.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS AA_CITIZEN_NOTICE (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    appeal_number   VARCHAR(50)   NOT NULL,
    -- The workflow event that created the obligation, e.g. HEARING_SCHEDULED.
    event_code      VARCHAR(60)   NOT NULL,
    -- APPELLANT | RESPONDENT | OMBUDSMAN
    recipient_role  VARCHAR(30)   NOT NULL,
    recipient_name  VARCHAR(200)  NULL,
    -- Masked at the API boundary, never in the row — a notice we cannot address is a finding.
    recipient_email VARCHAR(200)  NULL,
    recipient_phone VARCHAR(30)   NULL,
    -- EMAIL | SMS | NONE
    channel         VARCHAR(20)   NOT NULL,
    message_key     VARCHAR(200)  NOT NULL,
    -- JSON of interpolation values (hearing date, venue, appeal number) for the key above.
    message_params  TEXT          NULL,
    -- PENDING | SENT | FAILED | CANCELLED. Only PENDING is reachable in Phase 1.
    status          VARCHAR(20)   NOT NULL,
    dedupe_key      VARCHAR(100)  NOT NULL,
    attempt_count   INT           NOT NULL,
    last_error      VARCHAR(1000) NULL,
    next_attempt_at DATETIME(6)   NULL,
    dispatched_at   DATETIME(6)   NULL,
    created_by      VARCHAR(200)  NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CALL s3c_add_index('AA_CITIZEN_NOTICE', 'idx_acn_appeal', 'appeal_number');
CALL s3c_add_index('AA_CITIZEN_NOTICE', 'idx_acn_status', 'status');
CALL s3c_add_index('AA_CITIZEN_NOTICE', 'idx_acn_dispatch', 'status, next_attempt_at');
-- No separate dedupe index: uk_acn_event_recipient below covers exactly these columns in the same
-- order, so a second index would only cost writes. The entity declares the unique constraint, not
-- an index, and this file matches that mapping.

-- LOAD-BEARING. The reminder sweep runs on every pod and there is NO distributed lock, so two pods
-- can decide to record the same notice in the same second. Idempotency therefore has to be enforced
-- by the database, not by application code: the second INSERT must be REJECTED. Do not drop this to
-- "fix" a duplicate-key error — that error is the guarantee working.
CALL s3c_add_unique('AA_CITIZEN_NOTICE', 'uk_acn_event_recipient',
                    'appeal_number, event_code, recipient_role, dedupe_key');

DROP PROCEDURE IF EXISTS s3c_add_index;
DROP PROCEDURE IF EXISTS s3c_add_unique;
