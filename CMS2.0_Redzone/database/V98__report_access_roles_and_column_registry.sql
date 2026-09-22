-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V98 — Report access roles + the report column registry
-- Session S7 (UST613, UST615, UST669, UST670)
--
-- Re-runnable. MySQL 8.4 has no IF NOT EXISTS for ADD COLUMN or CREATE INDEX, so every DDL is
-- guarded through information_schema. Procedure prefix is s7_ — distinct from w0_ (V57), aa_ (V31),
-- s2c_ (V46) and s3a_ (V51). They are dropped at end of file.
--
-- NOTE ON V-NUMBERING: this session was allotted V91-V95, but V91/V92/V95/V96/V97 were already taken
-- on disk by parallel sessions by the time this ran. V98-V102 is the next free block in this tree.
-- The MySQL and Oracle directories are NOT in sync; the Oracle counterpart is V96.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- WHY REPORT_ACCESS_ROLE EXISTS
--
--   The Angular report builder has a complete Super-Admin screen that calls
--   GET/POST/DELETE /api/v1/reports/access-roles. None of those endpoints exist, no entity exists and
--   no table exists. The screen has never worked. What makes it more than a missing feature is the
--   failure MODE: report-builder.component.ts:247 swallows the GET error
--   (`error: () => {}  // Non-critical`), so the list stays empty, and the canExport computed then
--   falls through to `return true`. An empty list therefore reads as "everyone may export", and the
--   screen shows a reassuring "No access roles configured" empty state while doing so. A permission
--   table whose absence grants permission is worse than no permission table at all.
--
--   UST615/669 name the report viewers (Secretary, Deputy Ombudsman, Ombudsman, Ombudsman Admin,
--   CEPD Admin, AA Admin, AA Secretariat) and which of them are view-only. Those role lists are
--   DATA here, not a Java array, precisely because the stories already disagree with each other:
--   UST615 lists Secretary as a report viewer while UST655 (session S3) removes Secretary as an
--   assignment target. That contradiction is FLAGGED, not resolved — and modelling the list as rows
--   means resolving it later is an UPDATE, not a release.
--
-- WHY IT IS NOT SYSTEM_CONFIG
--   SYSTEM_CONFIG is single-valued key/value. The client contract needs a stable numeric id per row
--   (DELETE /access-roles/{id}, and Angular's `track role.id`), which key/value cannot supply.
--
-- SHAPE follows RBIO_STATUS_ROLE_VISIBILITY (V57): a (scope, ROLE_NAME) unique pair plus per-pair
-- flags, CHAR(1) 'Y'/'N' for booleans per this schema's convention. CAN_EXPORT is exposed to the
-- client as a real JSON boolean by the entity, not as 'Y'/'N'.
--
-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- WHY REPORT_COLUMN_REGISTRY EXISTS
--
--   UST613 requires 62 report columns, each sourcing data per a defined Field Name mapping in the
--   "BRD Format and Logic Sheet". QueryCompiler.executeList emits exactly TEN, hardcoded as
--   row.put(...) calls in a Java stream. THE BRD SHEET IS NOT IN THIS REPOSITORY. Inventing 52
--   column definitions would be fabricating a specification for a regulator-facing report.
--
--   So this migration builds the MECHANISM and seeds only the ten columns that demonstrably exist
--   today, each mapped to its real Complaint entity field. Adding the remaining columns when the
--   sheet arrives is an INSERT per column, with no Java change. The 62-column assertion is left as
--   an explicit test.fixme rather than a passing test over invented columns.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS s7_add_index;
DELIMITER //
CREATE PROCEDURE s7_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_cols VARCHAR(255))
BEGIN
    IF (SELECT COUNT(*) = 0 FROM information_schema.STATISTICS
         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl := CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_cols, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- ═══ 1. REPORT_ACCESS_ROLE ═══════════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS REPORT_ACCESS_ROLE (
    ID          BIGINT       NOT NULL AUTO_INCREMENT,
    REPORT_TYPE VARCHAR(50)  NOT NULL COMMENT 'RBIO | CEPC | CRPC | AA | ALL — ALL means every report type',
    ROLE_NAME   VARCHAR(50)  NOT NULL COMMENT 'Keycloak realm role name, e.g. RBIO_OFFICER',
    CAN_VIEW    CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT 'Y/N — may run the report at all',
    CAN_EXPORT  CHAR(1)      NOT NULL DEFAULT 'N' COMMENT 'Y/N — may export output. View-only roles are N',
    CREATED_BY  VARCHAR(200) NULL,
    CREATED_AT  DATETIME(6)  NULL,
    UPDATED_BY  VARCHAR(200) NULL,
    UPDATED_AT  DATETIME(6)  NULL,
    PRIMARY KEY (ID),
    UNIQUE KEY UK_REPORT_ACCESS_ROLE (REPORT_TYPE, ROLE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s7_add_index('REPORT_ACCESS_ROLE', 'IDX_REPORT_ACCESS_ROLE_NAME', 'ROLE_NAME');

-- Seed the UST615/669 role list. CAN_EXPORT='N' encodes "view-only" for CEPD Admin and AA Admin
-- exactly as the story words it. AA_SECRETARIAT is seeded with full view+export and is a DISTINCT
-- row from AA_ADMIN, per UST669's explicit instruction that the two are not the same actor.
--
-- Guarded per row with NOT EXISTS so a re-run neither duplicates nor overwrites an operator's later
-- change. This is the insert-if-absent convention used by every seeder in this schema — and it means
-- correcting a value here does NOT fix a row already present; that needs a scoped UPDATE.
INSERT INTO REPORT_ACCESS_ROLE (REPORT_TYPE, ROLE_NAME, CAN_VIEW, CAN_EXPORT, CREATED_BY, CREATED_AT)
SELECT * FROM (
    SELECT 'ALL' AS t, 'ADMIN'           AS r, 'Y' AS v, 'Y' AS e, 'V98_MIGRATION' AS cb, NOW(6) AS ca UNION ALL
    SELECT 'ALL',       'RBIO_ADMIN',           'Y',      'Y',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'RBIO_OMBUDSMAN',       'Y',      'Y',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'RBIO_DEPUTY_OMBUDSMAN','Y',      'Y',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'RBIO_SECRETARY',       'Y',      'Y',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'AA_SECRETARIAT',       'Y',      'Y',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'CEPD_ADMIN',           'Y',      'N',      'V98_MIGRATION',       NOW(6)       UNION ALL
    SELECT 'ALL',       'AA_ADMIN',             'Y',      'N',      'V98_MIGRATION',       NOW(6)
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM REPORT_ACCESS_ROLE ex
     WHERE ex.REPORT_TYPE = seed.t AND ex.ROLE_NAME = seed.r
);

-- ═══ 2. REPORT_COLUMN_REGISTRY ═══════════════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS REPORT_COLUMN_REGISTRY (
    ID            BIGINT       NOT NULL AUTO_INCREMENT,
    COLUMN_KEY    VARCHAR(80)  NOT NULL COMMENT 'Key emitted in the JSON result row, e.g. complaintNumber',
    FIELD_NAME    VARCHAR(120) NOT NULL COMMENT 'BRD "Field Name" — the label the report shows',
    JPA_PATH      VARCHAR(120) NOT NULL COMMENT 'Complaint entity property this column sources from',
    VALUE_TYPE    VARCHAR(20)  NOT NULL DEFAULT 'STRING' COMMENT 'STRING | DATETIME | NUMBER',
    DISPLAY_ORDER INT          NOT NULL DEFAULT 999 COMMENT 'Column order in the output — LinkedHashMap order is the UI order',
    IS_DEFAULT    CHAR(1)      NOT NULL DEFAULT 'Y' COMMENT 'Y/N — included when the caller selects no explicit columns',
    IS_ACTIVE     CHAR(1)      NOT NULL DEFAULT 'Y',
    PRIMARY KEY (ID),
    UNIQUE KEY UK_REPORT_COLUMN_KEY (COLUMN_KEY)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CALL s7_add_index('REPORT_COLUMN_REGISTRY', 'IDX_REPORT_COLUMN_ORDER', 'DISPLAY_ORDER');

-- Seed ONLY the ten columns QueryCompiler.executeList already emits, in its existing insertion
-- order, each pointing at the real Complaint property it reads today. This is deliberately a
-- faithful transcription of current behaviour, so switching the compiler to the registry is a
-- refactor with no output change — which is what makes the switch verifiable.
--
-- FIELD_NAME values here are provisional labels, NOT BRD Field Names. The BRD Format and Logic
-- Sheet is absent from this repository, so the authoritative mapping is unknown. When it arrives,
-- these labels get an UPDATE and the remaining ~52 columns get INSERTs. No Java changes.
INSERT INTO REPORT_COLUMN_REGISTRY (COLUMN_KEY, FIELD_NAME, JPA_PATH, VALUE_TYPE, DISPLAY_ORDER, IS_DEFAULT, IS_ACTIVE)
SELECT * FROM (
    SELECT 'complaintNumber' AS ck, 'Complaint Number'  AS fn, 'complaintNumber' AS jp, 'STRING'   AS vt, 10 AS dord, 'Y' AS isd, 'Y' AS isa UNION ALL
    SELECT 'subject',              'Subject',                 'subject',               'STRING',         20,        'Y',        'Y'        UNION ALL
    SELECT 'status',               'Status',                  'status',                'STRING',         30,        'Y',        'Y'        UNION ALL
    SELECT 'priority',             'Priority',                'priority',              'STRING',         40,        'Y',        'Y'        UNION ALL
    SELECT 'department',           'Department',              'department',            'STRING',         50,        'Y',        'Y'        UNION ALL
    SELECT 'entityCode',           'Regulated Entity Code',   'entityCode',            'STRING',         60,        'Y',        'Y'        UNION ALL
    SELECT 'createdAt',            'Created On',              'createdAt',             'DATETIME',       70,        'Y',        'Y'        UNION ALL
    SELECT 'filedAt',              'Filed On',                'filedAt',               'DATETIME',       80,        'Y',        'Y'        UNION ALL
    SELECT 'resolvedAt',           'Resolved On',             'resolvedAt',            'DATETIME',       90,        'Y',        'Y'        UNION ALL
    SELECT 'triageSignal',         'Triage Signal',           'triageSignal',          'STRING',        100,        'Y',        'Y'
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM REPORT_COLUMN_REGISTRY ex WHERE ex.COLUMN_KEY = seed.ck
);

-- closedAt is filterable (closedDate -> closedAt in QueryCompiler.FIELD_MAPPING) but was never
-- projected, so a user could filter on a closure date they could not see. UST672 requires the
-- Complaint-Closed-On filter to work; a column the report cannot display makes that unverifiable.
INSERT INTO REPORT_COLUMN_REGISTRY (COLUMN_KEY, FIELD_NAME, JPA_PATH, VALUE_TYPE, DISPLAY_ORDER, IS_DEFAULT, IS_ACTIVE)
SELECT 'closedAt', 'Complaint Closed On', 'closedAt', 'DATETIME', 95, 'Y', 'Y'
 WHERE NOT EXISTS (SELECT 1 FROM REPORT_COLUMN_REGISTRY WHERE COLUMN_KEY = 'closedAt');

DROP PROCEDURE IF EXISTS s7_add_index;
