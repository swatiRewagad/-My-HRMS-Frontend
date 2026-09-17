-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V51 — AA workflow state machine: stage SLA config, appeal outbox, holiday parity (session S3A)
--
-- Re-runnable. MySQL 8.4 has no IF NOT EXISTS for ADD COLUMN or CREATE INDEX, so every DDL statement
-- is guarded through information_schema.
--
-- Procedure prefix is s3a_ — distinct from aa_ (V31) and s2c_ (V46), both of which are dropped at the
-- end of the file that defines them, so a concurrent session re-creating them would clash.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s3a_add_index;
DELIMITER //
CREATE PROCEDURE s3a_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. OUTBOX_EVENT — the transactional outbox, for MySQL
--
-- The table is declared in V1__initial_schema.sql, but that file is ORACLE syntax
-- (NUMBER(19) DEFAULT seq.NEXTVAL, CLOB, VARCHAR2) and was never translated, so the table simply does
-- not exist in a dev MySQL database. cms-backend had no outbox mapping at all and published appeal
-- events nowhere; this creates the MySQL equivalent so the same cms-outbox-publisher can drain it.
--
-- Column names and the STATUS vocabulary match the Oracle definition exactly — cms-outbox-publisher
-- reads both, so a divergence here would break it against one database or the other.
-- ═══════════════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS OUTBOX_EVENT (
    EVENT_ID       BIGINT       NOT NULL AUTO_INCREMENT,
    AGGREGATE_ID   VARCHAR(50)  NOT NULL,
    AGGREGATE_TYPE VARCHAR(50)  NOT NULL,
    EVENT_TYPE     VARCHAR(100) NOT NULL,
    TOPIC          VARCHAR(100) NOT NULL,
    PAYLOAD        TEXT         NOT NULL,
    STATUS         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    RETRY_COUNT    INT          DEFAULT 0,
    ERROR_MESSAGE  VARCHAR(2000),
    CORRELATION_ID VARCHAR(100),
    CREATED_AT     DATETIME(6)  NOT NULL,
    PUBLISHED_AT   DATETIME(6),
    PRIMARY KEY (EVENT_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s3a_add_index('OUTBOX_EVENT', 'IDX_OUTBOX_STATUS', 'STATUS, CREATED_AT');
CALL s3a_add_index('OUTBOX_EVENT', 'IDX_OUTBOX_AGGREGATE', 'AGGREGATE_ID, AGGREGATE_TYPE');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. SYSTEM_CONFIG — per-stage SLA durations, in WORKING days
--
-- Appeals had no in-flight deadline of any kind: timeline.appeal.filing_window_days and
-- extended_window_days are read only when deciding whether an appeal may be FILED. CEPC and RBIO both
-- hold their stage durations in Java constants, so changing a timeline needs a redeploy; these live in
-- SYSTEM_CONFIG so an operator can adjust them during an incident.
--
-- IMPORTANT: these are OPERATIONAL tracking targets, NOT statutory limits. No RBIOS text prescribing
-- per-stage AA timelines was available, so the values are deliberately conservative placeholders and
-- must be confirmed before go-live. Presenting an internal guess to a citizen as a legal entitlement
-- would be worse than showing no deadline at all.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT * FROM (
    SELECT 'cms.aa.sla.filed.days' AS k, '7' AS v,
           'Working days an appeal may sit awaiting AA_DO acceptance. OPERATIONAL target, not statutory.' AS d,
           'V51' AS u, NOW() AS t
    UNION ALL SELECT 'cms.aa.sla.under_review.days', '30',
           'Working days allowed for review once accepted. OPERATIONAL target, not statutory.',
           'V51', NOW()
    UNION ALL SELECT 'cms.aa.sla.hearing_scheduled.days', '30',
           'Working days from hearing scheduling to disposal. OPERATIONAL target, not statutory.',
           'V51', NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG sc WHERE sc.config_key = seed.k
);

-- ═══════════════════════════════════════════════════════════════════════════
-- 3. HOLIDAYS — dev parity with the Oracle seed
--
-- The MySQL tree has NO holidays DDL and no seed; the table exists in dev only because ddl-auto creates
-- it from the entity, and it is EMPTY. Working-day arithmetic therefore silently degrades to
-- weekday-only in dev-local, so an SLA test would pass against maths that never excludes a holiday.
--
-- These are the 2026 RBI/national holidays already seeded for Oracle in
-- database/oracle/V2__seed_data.sql. Seeded here for DEV PARITY so deadline behaviour can be verified
-- locally; the authoritative production calendar remains the Oracle seed, which RBI maintains.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO holidays (holiday_date, name, type, `year`, is_national)
SELECT * FROM (
    SELECT DATE '2026-01-26' AS hd, 'Republic Day'          AS nm, 'NATIONAL' AS ty, 2026 AS yr, 1 AS nat
    UNION ALL SELECT DATE '2026-03-25', 'Holi',                    'GAZETTED', 2026, 1
    UNION ALL SELECT DATE '2026-04-14', 'Dr Ambedkar Jayanti',     'GAZETTED', 2026, 1
    UNION ALL SELECT DATE '2026-05-01', 'Maharashtra Day',         'GAZETTED', 2026, 0
    UNION ALL SELECT DATE '2026-08-15', 'Independence Day',        'NATIONAL', 2026, 1
    UNION ALL SELECT DATE '2026-10-02', 'Gandhi Jayanti',          'NATIONAL', 2026, 1
    UNION ALL SELECT DATE '2026-10-20', 'Dussehra',                'GAZETTED', 2026, 1
    UNION ALL SELECT DATE '2026-11-08', 'Diwali',                  'GAZETTED', 2026, 1
    UNION ALL SELECT DATE '2026-12-25', 'Christmas Day',           'NATIONAL', 2026, 1
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM holidays h WHERE h.holiday_date = seed.hd
);

DROP PROCEDURE IF EXISTS s3a_add_index;
