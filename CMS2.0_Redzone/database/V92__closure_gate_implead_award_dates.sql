-- ============================================================
-- V92 — closure communication gate, IMPLEADED_PARTY, award/advisory dates
-- Session S4 (UST504-509, 520, 536-538, 543-546, 548, 576, 757, 762-764, 767)
-- MySQL version. Oracle counterpart is V90. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- PART 1 — DATE_OF_SENDING on COMPLAINTS.
--
--   The closure dialog collected a Date of Sending and POSTed it on every closure. A grep of cms-backend
--   for "dateOfSending" returned NOTHING: the value landed in an unread Map<String,String> and was
--   discarded. The signed letter the officer attached lived in an Angular signal<File> and was never
--   uploaded at all. So the UI blocked closure correctly while the server enforced nothing, and any direct
--   API call closed a complaint with no letter, no send date and no email to the complainant. This column
--   is what makes the date durable; the guard that requires it is armed by config (Part 5).
--
-- PART 2 — AWARD DATES.
--
--   AWARD_PASSED_DATE is distinct from the existing ADJUDICATION_DATE, which is written by BOTH
--   ADJUDICATION_AWARD and ADJUDICATION_REJECT — so it cannot answer "when was an award passed" without
--   also matching rejections. The implemented / not-implemented / lapse dates did not exist anywhere in the
--   repository (zero grep hits) and are what the award reporting fields hang off.
--
-- PART 3 — ADVISORY COLUMNS THAT ONLY EXISTED VIA ddl-auto.
--
--   ADVISORY_TEXT and ADVISORY_ISSUED_AT are written by the ISSUE_ADVISORY side effect but appear in NO
--   MySQL migration — they exist on dev only because Hibernate created them. Under the production profile's
--   ddl-auto: validate, a MySQL schema built from database/ alone would fail to start. ADVISORY_COMPLIED_AT
--   already has a migration (V71); these two were missed.
--
-- PART 4 — IMPLEADED_PARTY.
--
--   Impleading appended a name to COMPLAINTS.IMPLEADED_PARTIES, a VARCHAR(1000) CSV. The stories require
--   each impleaded entity to carry its own Nodal Officer contacts, its own closure clause and compensation,
--   and its own completeness state that closure must verify — none of which can hang off a substring of a
--   shared column. The PARTY_TYPE the UI already collected was being discarded for want of anywhere to put
--   it. The CSV column is deliberately NOT dropped: six other sessions read this schema and several tests
--   assert on it, so it stays maintained for existing readers while this table becomes authoritative for
--   per-party facts.
--
-- PART 5 — THE GATE IS ARMED BY CONFIG, DEFAULTING TO OFF.
--
--   Making a send date, a signed letter or a complainant email mandatory at closure changes citizen-facing
--   legal behaviour on a database where 1646 of 1647 closed complaints predate the requirement. Defaulting
--   ON would either break every existing closure path or silently grandfather the old rows. Following the
--   precedent of cms.aa.order.max_award_amount (0 = unenforced) and cms.aa.order.block_sub_judice
--   (false = unenforced), each requirement arms with a single UPDATE after sign-off. The enforcement is
--   fully built and tested — only the switch is off — so this migration is behaviour-neutral and cannot
--   break go-live.
--
-- Every statement is guarded on information_schema so a re-run is safe (MySQL 8.4 has no
-- ADD COLUMN IF NOT EXISTS). Every new column is NULLABLE, as required on a shared ddl-auto database.
-- ============================================================

DROP PROCEDURE IF EXISTS s4_closure_gate_columns;

DELIMITER //
CREATE PROCEDURE s4_closure_gate_columns()
proc_body: BEGIN
    DECLARE tbl_missing INT;
    DECLARE col_missing INT;

    SET tbl_missing := (SELECT COUNT(*) = 0 FROM information_schema.TABLES
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS');
    IF tbl_missing THEN
        -- Hibernate creates COMPLAINTS from the entity on first boot; nothing to alter yet.
        LEAVE proc_body;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'date_of_sending');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN date_of_sending DATE NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'award_passed_date');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN award_passed_date DATE NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'award_implemented_date');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN award_implemented_date DATE NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'award_not_implemented_date');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN award_not_implemented_date DATE NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'award_lapse_date');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN award_lapse_date DATE NULL;
    END IF;

    -- Written by ISSUE_ADVISORY since before this batch, but never declared in a MySQL migration.
    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'advisory_text');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN advisory_text TEXT NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'advisory_issued_at');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN advisory_issued_at DATETIME(6) NULL;
    END IF;
END //
DELIMITER ;

CALL s4_closure_gate_columns();
DROP PROCEDURE IF EXISTS s4_closure_gate_columns;

-- ============================================================
-- PART 4 — IMPLEADED_PARTY
-- ============================================================

CREATE TABLE IF NOT EXISTS IMPLEADED_PARTY (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_number        VARCHAR(50)  NOT NULL,
    party_name              VARCHAR(200) NOT NULL,
    party_type              VARCHAR(100) NULL,
    regulated_entity_id     BIGINT       NULL,
    nodal_officer_record_id BIGINT       NULL,
    data_status             VARCHAR(40)  NOT NULL,
    closure_clause          VARCHAR(40)  NULL,
    compensation_amount     DECIMAL(15,2) NULL,
    implead_reason          VARCHAR(1000) NULL,
    impleaded_by            VARCHAR(200) NULL,
    impleaded_by_role       VARCHAR(100) NULL,
    impleaded_at            DATETIME(6)  NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS s4_implead_indexes;

DELIMITER //
CREATE PROCEDURE s4_implead_indexes()
proc_body: BEGIN
    DECLARE idx_missing INT;

    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'IMPLEADED_PARTY'
                          AND INDEX_NAME = 'idx_implead_complaint');
    IF idx_missing THEN
        CREATE INDEX idx_implead_complaint ON IMPLEADED_PARTY(complaint_number);
    END IF;

    -- The closure gate queries on status, so it must not full-scan.
    SET idx_missing := (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'IMPLEADED_PARTY'
                          AND INDEX_NAME = 'idx_implead_status');
    IF idx_missing THEN
        CREATE INDEX idx_implead_status ON IMPLEADED_PARTY(data_status);
    END IF;
END //
DELIMITER ;

CALL s4_implead_indexes();
DROP PROCEDURE IF EXISTS s4_implead_indexes;

-- ============================================================
-- PART 5 — closure-gate switches, seeded OFF
--
-- 'false' = unenforced. Arming after legal sign-off is a single UPDATE per key, not a release.
-- ============================================================

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.closure.require_send_date', 'false',
       'When true, an RBIO closure is refused until the Date of Sending of the closure letter is recorded (UST509). Seeded false: arming changes citizen-facing legal behaviour and needs sign-off.',
       'V92', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.closure.require_send_date');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.closure.require_signed_letter', 'false',
       'When true, an RBIO closure is refused until an attachment with documentType=SIGNED_LETTER exists (UST507-508). Seeded false pending sign-off.',
       'V92', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.closure.require_signed_letter');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.rbio.closure.require_complainant_email', 'false',
       'When true, a direct RBIO closure is refused when the complainant has no email address, routing back via Facilitation/Rejection or Decision instead (UST549, UST764). Seeded false pending sign-off.',
       'V92', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.rbio.closure.require_complainant_email');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.communication.outbox.dispatch_enabled', 'true',
       'When true, the communication outbox scheduler drains queued email/SMS. Defaults TRUE, unlike the statutory guards: a queued closure communication that is never drained is an obligation silently unmet, and the transport is a no-op adapter until a real gateway is configured.',
       'V92', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.communication.outbox.dispatch_enabled');
