-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V104 — ROLE_STATUS_MAPPING: the CEPC status dropdown, per role
--
-- WHY THIS EXISTS
--
--   The CEPC dashboard's status dropdown has to answer "which entries may a CEPC_REVIEWER pick, and in
--   what order". Until now that answer was a VISIBLE_TO_ROLES string on each CEPC_DASHBOARD_FILTER row —
--   one comma-separated role list per status. This file moves it to a row per (role, status) pair, which
--   is what the requirement actually is, and adds the SEQUENCE the old shape had no way to express: the
--   old DISPLAY_ORDER was per status and therefore global, so every role got the same ordering.
--
-- DIVISION OF LABOUR — read before adding a status code anywhere
--
--   ROLE_STATUS_MAPPING says WHO sees a code and WHERE it sits.
--   CEPC_DASHBOARD_FILTER (dimension STATUS) says WHAT the code selects, and carries its label.
--
--   Both are required. CepcComplaintSearchService resolves the selected code against
--   CEPC_DASHBOARD_FILTER and fails CLOSED when it finds nothing — no error, no rows, forever. A code
--   seeded into ROLE_STATUS_MAPPING with no filter row is therefore a dropdown entry that silently
--   matches nothing, which is why DepartmentStatusController withholds such entries and logs a warning
--   rather than serving them.
--
-- SECTION 3 REPLACES THE STATUS VOCABULARY — this is the part that changes existing behaviour
--
--   The STATUS-dimension codes were display labels ('All Complaints', 'Sent Back to Me'). They are now
--   normalised codes (ALL_COMPLAINTS, SENT_BACK_TO_CEPC_DO) because they arrive from ROLE_STATUS_MAPPING
--   and the label is carried separately in LABEL_EN. The old rows cannot simply be left in place: they
--   would be unreachable — nothing offers them any more — while still occupying (DIMENSION, FILTER_CODE)
--   slots and reading as live configuration to the next person.
--
--   So section 3 DELETES the STATUS-dimension rows and lets CepcDashboardFilterSeeder re-insert the new
--   vocabulary on the next boot. Scoped to DIMENSION = 'STATUS' on purpose: the TAB and KPI rows are
--   unchanged and share codes with the old status rows ('Meeting Scheduled' is both), so an unscoped
--   delete would silently break the tabs and the KPI cards.
--
--   These are seeder-owned reference rows, not user data — the seeder is the source of truth for them and
--   re-creates them if-absent on every boot in every environment. Nothing user-entered is deleted.
--
-- NO SEED ROWS HERE
--
--   RoleStatusMappingSeeder carries no @Profile, so it runs in every environment including production and
--   inserts the rows if-absent on every boot. Duplicating them here would mean two sources for one
--   matrix, and UQ_ROLE_STATUS would turn a drift into a failed migration rather than a mere
--   disagreement. The table is created empty on purpose — the same argument V103 makes for
--   CEPC_DASHBOARD_FILTER.
--
-- WHY THE GUARDS READ information_schema WITH UPPER(TABLE_NAME)
--
--   Unchanged from V103, whose header sets out the full argument: Hibernate folds @Table names to
--   lowercase, and on MySQL 8 information_schema.TABLES.TABLE_NAME is utf8mb3_bin (binary), so a bare
--   comparison against an uppercase literal matches nothing and a `CREATE TABLE IF NOT EXISTS` would
--   create a second table under the uppercase name while the application kept writing to the lowercase
--   one. Every literal lookup below is therefore UPPER(TABLE_NAME) = '...'.
--
--   Do not copy this form into database/oracle/ — that tree needs the bare comparison, for the opposite
--   folding reason. See the Oracle counterpart, V102.
--
-- Re-runnable. Procedure prefix cepc_v104_.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 1. ROLE_STATUS_MAPPING
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v104_create_table;
DELIMITER //
CREATE PROCEDURE cepc_v104_create_table()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'ROLE_STATUS_MAPPING') THEN
        -- LOWERCASE on purpose, unlike V103's CREATEs. This is the one genuinely new table in the CEPC
        -- set, so this statement actually runs — V103's six all pre-existed as Hibernate had already
        -- created them, which is why its uppercase spelling never mattered. Hibernate folds
        -- @Table("ROLE_STATUS_MAPPING") to role_status_mapping, and with lower_case_table_names=0 (the
        -- Linux default, and the dev container's setting) that is a DIFFERENT table from the uppercase
        -- one: creating it uppercase leaves ddl-auto=update to create a lowercase second table, seed
        -- into that, and strand this one empty — and under ddl-auto=validate it fails outright.
        -- Lowercase is correct under lower_case_table_names=1 too, where it is folded anyway.
        -- Oracle is the opposite (unquoted folds UP) and V102 is uppercase there; do not align the two.
        CREATE TABLE role_status_mapping (
            ID          BIGINT       NOT NULL AUTO_INCREMENT,
            -- Spelled as CepcIdentityResolver.CEPC_ROLES spells it: CEPC_INCHARGE, not CEPC_IN_CHARGE.
            -- resolveCepcRole() returns that spelling and the lookup is an equality match, so a
            -- differently-spelled row is a row no caller ever sees.
            ROLE_NAME   VARCHAR(50)  NOT NULL,
            -- Must match an active CEPC_DASHBOARD_FILTER row with DIMENSION = 'STATUS'. See the header.
            STATUS_CODE VARCHAR(50)  NOT NULL,
            DESCRIPTION VARCHAR(255) NULL,
            -- Position in THIS role's dropdown, ascending. Per-role, which is the point of the table:
            -- CEPC_DASHBOARD_FILTER.DISPLAY_ORDER is per status and so cannot differ between roles.
            `SEQUENCE`  INT          NOT NULL,
            CREATED_AT  DATETIME(6)  NOT NULL,
            UPDATED_AT  DATETIME(6)  NOT NULL,
            PRIMARY KEY (ID),
            -- One row per role per status. This is what makes the seeder's insert-if-absent idempotent.
            UNIQUE KEY UQ_ROLE_STATUS (ROLE_NAME, STATUS_CODE),
            -- Every read is "the dropdown for one role, in order".
            KEY IDX_ROLE_STATUS_ROLE (ROLE_NAME, `SEQUENCE`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
    END IF;
END //
DELIMITER ;

CALL cepc_v104_create_table();
DROP PROCEDURE IF EXISTS cepc_v104_create_table;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 2. CEPC_DASHBOARD_FILTER — drop VISIBLE_TO_ROLES
--
--    Role visibility now lives in ROLE_STATUS_MAPPING. The column is dropped rather than left unused
--    because two places answering "may this role pick this status" is exactly the drift this change set
--    out to remove, and the next reader has no way to tell which one is live.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v104_drop_visible_to_roles;
DELIMITER //
CREATE PROCEDURE cepc_v104_drop_visible_to_roles()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    -- The table as the server actually spells it; see this file's header for why the case matters.
    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_DASHBOARD_FILTER' LIMIT 1;

    IF v_table IS NOT NULL THEN
        -- v_table holds the server's own spelling, so this comparison needs no UPPER() wrapping.
        IF EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = v_table
                      AND COLUMN_NAME = 'VISIBLE_TO_ROLES') THEN
            SET @sql = CONCAT('ALTER TABLE `', v_table, '` DROP COLUMN VISIBLE_TO_ROLES');
            PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
        END IF;
    END IF;
END //
DELIMITER ;

CALL cepc_v104_drop_visible_to_roles();
DROP PROCEDURE IF EXISTS cepc_v104_drop_visible_to_roles;

-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- 3. CEPC_DASHBOARD_FILTER — clear the old STATUS vocabulary
--
--    Scoped to DIMENSION = 'STATUS'. The TAB and KPI rows must survive: they are unchanged, and they
--    share filter codes with the old status rows, so an unscoped delete would empty the dashboard's tabs
--    and KPI cards as well. CepcDashboardFilterSeeder re-inserts the new STATUS rows on the next boot.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DROP PROCEDURE IF EXISTS cepc_v104_reset_status_vocabulary;
DELIMITER //
CREATE PROCEDURE cepc_v104_reset_status_vocabulary()
BEGIN
    DECLARE v_table VARCHAR(64) DEFAULT NULL;

    -- The table as the server actually spells it; see this file's header for why the case matters.
    SELECT TABLE_NAME INTO v_table FROM information_schema.TABLES
     WHERE TABLE_SCHEMA = DATABASE() AND UPPER(TABLE_NAME) = 'CEPC_DASHBOARD_FILTER' LIMIT 1;

    IF v_table IS NOT NULL THEN
        -- Re-runnable: on a second run the surviving rows are the new vocabulary, which the seeder will
        -- simply re-insert again. Harmless, if briefly empty between this statement and the next boot.
        SET @sql = CONCAT('DELETE FROM `', v_table, '` WHERE DIMENSION = ''STATUS''');
        PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
    END IF;
END //
DELIMITER ;

CALL cepc_v104_reset_status_vocabulary();
DROP PROCEDURE IF EXISTS cepc_v104_reset_status_vocabulary;
