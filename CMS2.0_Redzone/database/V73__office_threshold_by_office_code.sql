-- ============================================================
-- V73 — OFFICE_THRESHOLD_CONFIG: re-key to OFFICE_CODE_MASTER and seed all 24 offices
-- Session S3 (UST464-467, UST755)
-- ============================================================
--
-- WHY THIS MIGRATION EXISTS
--
--   1. OFFICE_THRESHOLD_CONFIG had NO MySQL migration at all. It existed on dev only because
--      Hibernate ddl-auto materialised it from the entity, which means it was EMPTY (0 rows
--      verified in cms_db). OfficeRoutingService.routeToOffice therefore returned NOT_FOUND for
--      every office, and InterOfficeTransferService's increment/decrement calls were silent
--      no-ops against a missing row. The routing logic was written but could never have worked
--      on dev.
--
--   2. THE KEY SPACES DID NOT MEET. The Oracle seed used officeId values 'RBIO-MUM'/'RBIO-DEL'/
--      'RBIO-CHN'/'RBIO-KOL'/'CEPC' — five synthetic ids covering four cities plus a department.
--      OMBUDSMAN_OFFICE_MASTER holds the real 24 offices keyed by OFFICE_NAME with no code column
--      of any kind. So four of twenty-four offices had a capacity row and twenty had none, and
--      there was no join key to fix it with.
--
--      OFFICE_CODE_MASTER.OFFICE_CODE is used instead, because it is the only stable short code in
--      the schema AND it is already the value stored on COMPLAINTS.rbio_office_code (backfilled by
--      V36, which validates against this same master). Re-keying onto it means a complaint row, a
--      complaint number and a capacity row all name the office the same way. Every one of the 24
--      OMBUDSMAN_OFFICE_MASTER names resolves to exactly one active code under hyphen/space
--      normalisation ('Mumbai-I' -> 'Mumbai I'), verified with zero unmatched rows before writing
--      this file. The seed below derives the mapping with that join rather than hardcoding 24
--      pairs, so it cannot drift from the masters.
--
--      OFFICE_CODE '023' (New Delhi III) deliberately gets NO row: it is a code without a seeded
--      ombudsman office, and inventing a capacity for an office that does not exist would let
--      complaints route to a jurisdiction that cannot hear them.
--
--   3. OVERFLOW ORDER IS DATA, NOT AN ACCIDENT. overflow_sequence_order was previously left at its
--      primitive default of 0 for any row not explicitly seeded, and the service orders the
--      overflow walk by that column — so every unseeded office tied at 0 and the walk order was
--      whatever the DB happened to return. Order is assigned here from OFFICE_CODE so it is
--      deterministic and reproducible.
--
--   4. overflow_target_office is seeded as an explicit chain and the service now HONOURS it (it
--      previously ignored the column entirely and re-scanned the whole department). A NULL target
--      means "end of chain" and is meaningful, not missing.
--
-- MAX_THRESHOLD IS A PLACEHOLDER PENDING RBI CAPACITY DATA. There is no authoritative per-office
-- caseload capacity anywhere in this repo or in the Scheme text. Seeding a guessed per-office
-- number would silently divert a citizen's complaint away from its lawful territorial office once
-- the guess was exceeded. Instead every office gets the SAME conservative value drawn from
-- SYSTEM_CONFIG (office.threshold.default_max, seeded below), so behaviour is uniform and
-- explainable until RBI supplies real figures, and changing it is an admin config edit rather than
-- a migration. Flagged in the batch report as an assumption requiring confirmation.
--
-- Re-running is safe: MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so every ALTER is
-- guarded on information_schema and every INSERT is INSERT ... WHERE NOT EXISTS.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 0. Guard helpers
-- ─────────────────────────────────────────────────────────────
DROP PROCEDURE IF EXISTS s3_add_column;
DROP PROCEDURE IF EXISTS s3_add_index;

DELIMITER $$

CREATE PROCEDURE s3_add_column(
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

CREATE PROCEDURE s3_add_index(
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
-- 1. Table (ddl-auto may already have created it; this makes the schema explicit and replayable)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS OFFICE_THRESHOLD_CONFIG (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    office_id               VARCHAR(50)  NOT NULL,
    office_name             VARCHAR(200) NOT NULL,
    department              VARCHAR(20)  NOT NULL,
    max_threshold           INT          NOT NULL DEFAULT 100,
    current_count           INT          NOT NULL DEFAULT 0,
    overflow_sequence_order INT          NOT NULL DEFAULT 0,
    overflow_target_office  VARCHAR(50)  NULL,
    active                  BIT(1)       NOT NULL DEFAULT b'1',
    updated_by              VARCHAR(100) NULL,
    updated_at              DATETIME(6)  NULL,
    CONSTRAINT uq_office_threshold_office_id UNIQUE (office_id)
);

-- Departmental overflow walk reads (department, active, overflow_sequence_order).
CALL s3_add_index('OFFICE_THRESHOLD_CONFIG', 'idx_office_threshold_dept_order',
                  'department, active, overflow_sequence_order');

-- ─────────────────────────────────────────────────────────────
-- 2. Config keys (admin-tunable; no restart, SYSTEM_CONFIG is uncached)
-- ─────────────────────────────────────────────────────────────
-- NOTE: existing TimelineConfigService only accepts keys prefixed 'timeline.' and validates 1-365,
-- so these are read through SystemConfigService (typed getInt with fallback) instead. The keys are
-- namespaced 'office.' so they cannot collide with another session's config.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'office.threshold.default_max', '500',
       'Default per-office concurrent complaint capacity before overflow. Placeholder pending RBI capacity data.',
       'V73_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'office.threshold.default_max');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'office.threshold.enforcement_enabled', 'true',
       'When false, routeToOffice records the office but never diverts to an overflow office.',
       'V73_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'office.threshold.enforcement_enabled');

-- ─────────────────────────────────────────────────────────────
-- 3. Retire the synthetic Oracle-only office ids
-- ─────────────────────────────────────────────────────────────
-- These five rows only ever existed in the Oracle seed. They are deactivated rather than deleted so
-- that any Oracle environment already carrying them stops routing through them without losing the
-- historical row. Scoped BY office_id, never by name.
UPDATE OFFICE_THRESHOLD_CONFIG
   SET active = b'0',
       updated_by = 'V73_migration',
       updated_at = NOW()
 WHERE office_id IN ('RBIO-MUM', 'RBIO-DEL', 'RBIO-CHN', 'RBIO-KOL');

-- ─────────────────────────────────────────────────────────────
-- 4. Seed one capacity row per real ombudsman office, keyed by OFFICE_CODE
-- ─────────────────────────────────────────────────────────────
-- The join is the point: it derives office_id from OFFICE_CODE_MASTER and office_name from
-- OMBUDSMAN_OFFICE_MASTER, so this seed cannot invent an office that is absent from either master.
INSERT INTO OFFICE_THRESHOLD_CONFIG
    (office_id, office_name, department, max_threshold, current_count,
     overflow_sequence_order, overflow_target_office, active, updated_by, updated_at)
SELECT ocm.OFFICE_CODE,
       oom.OFFICE_NAME,
       'RBIO',
       CAST(COALESCE((SELECT config_value FROM SYSTEM_CONFIG
                       WHERE config_key = 'office.threshold.default_max'), '500') AS UNSIGNED),
       0,
       CAST(ocm.OFFICE_CODE AS UNSIGNED),
       NULL,
       b'1',
       'V73_migration',
       NOW()
  FROM OMBUDSMAN_OFFICE_MASTER oom
  JOIN OFFICE_CODE_MASTER ocm
    ON REPLACE(UPPER(ocm.OFFICE_NAME), '-', ' ') = REPLACE(UPPER(oom.OFFICE_NAME), '-', ' ')
   AND ocm.IS_ACTIVE = 1
 WHERE oom.IS_ACTIVE = 1
   AND NOT EXISTS (SELECT 1 FROM OFFICE_THRESHOLD_CONFIG t WHERE t.office_id = ocm.OFFICE_CODE);

-- CEPC is a department, not a territorial office, so it keeps its own single row.
INSERT INTO OFFICE_THRESHOLD_CONFIG
    (office_id, office_name, department, max_threshold, current_count,
     overflow_sequence_order, overflow_target_office, active, updated_by, updated_at)
SELECT 'CEPC', 'CEPC', 'CEPC',
       CAST(COALESCE((SELECT config_value FROM SYSTEM_CONFIG
                       WHERE config_key = 'office.threshold.default_max'), '500') AS UNSIGNED),
       0, 1, NULL, b'1', 'V73_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM OFFICE_THRESHOLD_CONFIG WHERE office_id = 'CEPC');

-- ─────────────────────────────────────────────────────────────
-- 5. Overflow chain — each RBIO office overflows to the next by sequence order
-- ─────────────────────────────────────────────────────────────
-- Deterministic and derived, so a replay produces an identical chain. The highest-ordered office
-- keeps a NULL target, meaning "end of chain": the service then falls back to its all-full
-- behaviour rather than looping forever.
UPDATE OFFICE_THRESHOLD_CONFIG t
  JOIN (
        SELECT cur.office_id,
               (SELECT nxt.office_id
                  FROM OFFICE_THRESHOLD_CONFIG nxt
                 WHERE nxt.department = cur.department
                   AND nxt.active = b'1'
                   AND nxt.overflow_sequence_order > cur.overflow_sequence_order
                 ORDER BY nxt.overflow_sequence_order ASC
                 LIMIT 1) AS next_office
          FROM OFFICE_THRESHOLD_CONFIG cur
         WHERE cur.department = 'RBIO' AND cur.active = b'1'
       ) chain ON chain.office_id = t.office_id
   SET t.overflow_target_office = chain.next_office,
       t.updated_by = 'V73_migration',
       t.updated_at = NOW()
 WHERE t.overflow_target_office IS NULL
   AND chain.next_office IS NOT NULL;

DROP PROCEDURE IF EXISTS s3_add_column;
DROP PROCEDURE IF EXISTS s3_add_index;
