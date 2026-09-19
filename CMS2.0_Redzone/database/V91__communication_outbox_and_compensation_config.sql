-- ============================================================
-- V91 — COMMUNICATION_OUTBOX + configurable RBIO compensation caps
-- Session S4 (UST504-509, 520, 541-542, 549, 757, 763-764, 506)
-- MySQL version. Oracle counterpart is V89. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- PART 1 — COMMUNICATION_OUTBOX
--
--   WHY THIS TABLE HAS TO EXIST. Outbound closure communication had no durable record anywhere.
--   CepcWorkflowService.autoDispatchClosureLetter generated the letter bytes, DISCARDED the return value,
--   stamped CLOSURE_LETTER_SENT_AT and wrote a log line — so the database recorded "sent" for a message
--   that was never addressed to any recipient and never handed to any transport. SMS was a log.info TODO
--   with no gateway client in any module. A complainant asking "why was I never informed my complaint was
--   closed" could not be answered from the data, and neither could an auditor.
--
--   Persisting the INTENT first and dispatching afterwards separates two failures that must not be
--   conflated: failing to DECIDE to communicate (a workflow defect, fixed in code) and failing to DELIVER
--   (a gateway problem, retried). The workflow transaction commits the row; a sender drains it later and
--   sets SENT. If the gateway is unavailable the row waits rather than the obligation being lost, which is
--   the entire point for a statutory closure communication.
--
--   WHY BODY AND SMS_TEXT ARE STORED RESOLVED, not just TEMPLATE_ID. The template pointer records
--   provenance, but a template edited next month must not retroactively change what the system says it
--   told a citizen last month. Both are kept: the pointer for provenance, the rendered text for evidence.
--
--   WHY SMS_TEXT IS A SEPARATE COLUMN rather than reusing BODY. An SMS is not a truncated email; it has
--   its own length budget and its own wording. Keeping both on one row lets an operator see exactly what
--   each channel was given for the same event.
--
--   WHAT SENT MEANS. SENT=1 with SENT_AT records that the transport ACCEPTED the message. The only
--   adapter today is LoggingOutboundMessageAdapter, which delivers nothing and says so loudly. Until a
--   real gateway adapter is supplied, a drained queue proves the system decided correctly and addressed
--   the right recipient — not that anybody received anything.
--
-- PART 2 — COMPENSATION CAPS AS CONFIG
--
--   RbioCompensationService held 3000000 / 300000 / 3000000 as private static final constants, so amending
--   a statutory figure meant a code change and a redeploy. The same numbers were independently duplicated
--   in seven other places, and CompensationPrecedentService derived a TOTAL of 3300000 by adding two of
--   them while RbioCompensationService enforced a COMBINED cap of 3000000 — so the copilot could suggest
--   an amount the blocking validator would then refuse.
--
--   These rows are seeded to EXACTLY the values the code already used, so applying this migration cannot
--   change a single award outcome. That is deliberate and follows the precedent set by
--   V56__aa_order_statutory_guards.sql, whose header already records that these figures are of unverified
--   provenance. Amending them is a legal decision; it is now a one-row UPDATE rather than a release.
--
-- Every DDL statement is guarded on information_schema so a re-run is safe (MySQL 8.4 has no
-- ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS). Wave 2 replays this against a clean database,
-- and that replay is the only proof this SQL matches what Hibernate's ddl-auto actually created.
-- ============================================================

CREATE TABLE IF NOT EXISTS COMMUNICATION_OUTBOX (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    communication_type  VARCHAR(60)  NOT NULL,
    channel             VARCHAR(20)  NOT NULL,
    template_id         BIGINT       NULL,
    recipient           VARCHAR(320) NOT NULL,
    sender              VARCHAR(320) NULL,
    subject             VARCHAR(500) NULL,
    body                LONGTEXT     NULL,
    sms_text            VARCHAR(1000) NULL,
    language            VARCHAR(10)  NULL,
    related_reference   VARCHAR(50)  NULL,
    sent                TINYINT(1)   NOT NULL DEFAULT 0,
    sent_at             DATETIME(6)  NULL,
    attempt_count       INT          NOT NULL DEFAULT 0,
    last_error          VARCHAR(1000) NULL,
    created_at          DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS s4_comm_outbox_indexes;

DELIMITER //
CREATE PROCEDURE s4_comm_outbox_indexes()
proc_body: BEGIN
    DECLARE idx_missing INT;

    -- The sender's queue predicate. Without this the drain does a full scan once the table has history,
    -- and the table only ever grows.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMMUNICATION_OUTBOX'
                          AND INDEX_NAME = 'idx_comm_outbox_sent');
    IF idx_missing THEN
        CREATE INDEX idx_comm_outbox_sent ON COMMUNICATION_OUTBOX(sent);
    END IF;

    -- "What was this complainant told, and when" — the audit question this table exists to answer.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMMUNICATION_OUTBOX'
                          AND INDEX_NAME = 'idx_comm_outbox_complaint');
    IF idx_missing THEN
        CREATE INDEX idx_comm_outbox_complaint ON COMMUNICATION_OUTBOX(related_reference);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMMUNICATION_OUTBOX'
                          AND INDEX_NAME = 'idx_comm_outbox_channel');
    IF idx_missing THEN
        CREATE INDEX idx_comm_outbox_channel ON COMMUNICATION_OUTBOX(channel);
    END IF;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMMUNICATION_OUTBOX'
                          AND INDEX_NAME = 'idx_comm_outbox_created');
    IF idx_missing THEN
        CREATE INDEX idx_comm_outbox_created ON COMMUNICATION_OUTBOX(created_at);
    END IF;
END //
DELIMITER ;

CALL s4_comm_outbox_indexes();
DROP PROCEDURE IF EXISTS s4_comm_outbox_indexes;

-- ============================================================
-- PART 2 — compensation caps and reporting bands as SYSTEM_CONFIG rows
--
-- Insert-if-absent so a re-run never overwrites a value an operator has deliberately changed. Values are
-- identical to the constants the code used, so this section is behaviour-neutral by construction.
-- ============================================================

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.max_consequential_loss', '3000000',
       'RBIO cap on consequential-loss compensation (rupees). Seeded to the value previously hardcoded in RbioCompensationService; provenance unverified, see V56.',
       'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.max_consequential_loss');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.max_time_harassment', '300000',
       'RBIO cap on time/mental-agony/harassment compensation (rupees). Seeded to the previously hardcoded value; provenance unverified.',
       'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.max_time_harassment');

-- Note this is NOT the sum of the two caps above. The enforced combined ceiling has always equalled the
-- consequential-loss cap; CompensationPrecedentService's 3300000 total was the outlier and is now removed.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.max_combined', '3000000',
       'RBIO cap on combined compensation (rupees). Deliberately equal to the consequential-loss cap, not the sum of the component caps.',
       'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.max_combined');

-- Reporting bands are configurable alongside the caps: MAXIMUM means "at or near the ceiling", so raising
-- a cap while leaving the bands fixed would silently redefine what every compensation band report means.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.band_low_upto', '100000',
       'Upper bound of the LOW compensation reporting band (rupees).', 'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.band_low_upto');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.band_medium_upto', '1000000',
       'Upper bound of the MEDIUM compensation reporting band (rupees).', 'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.band_medium_upto');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.compensation.band_high_upto', '2000000',
       'Upper bound of the HIGH compensation reporting band (rupees). Anything above is MAXIMUM.', 'V91', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.compensation.band_high_upto');
