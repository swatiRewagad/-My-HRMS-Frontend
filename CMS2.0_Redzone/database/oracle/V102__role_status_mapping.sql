-- ============================================================
-- V102 — ROLE_STATUS_MAPPING: the CEPC status dropdown, per role
--
-- Oracle counterpart of MySQL V104. The two directories' V-numbers are NOT in sync.
--
-- WHY THIS EXISTS
--
--   The CEPC dashboard's status dropdown has to answer "which entries may a CEPC_REVIEWER pick, and in what
--   order". Until now that answer was a VISIBLE_TO_ROLES string on each CEPC_DASHBOARD_FILTER row — one
--   comma-separated role list per status. This file moves it to a row per (role, status) pair, which is what
--   the requirement actually is, and adds the SEQUENCE the old shape had no way to express: the old
--   DISPLAY_ORDER was per status and therefore global, so every role got the same ordering.
--
-- DIVISION OF LABOUR — read before adding a status code anywhere
--
--   ROLE_STATUS_MAPPING says WHO sees a code and WHERE it sits.
--   CEPC_DASHBOARD_FILTER (dimension STATUS) says WHAT the code selects, and carries its label.
--
--   Both are required. CepcComplaintSearchService resolves the selected code against CEPC_DASHBOARD_FILTER
--   and fails CLOSED when it finds nothing — no error, no rows, forever. A code seeded into
--   ROLE_STATUS_MAPPING with no filter row is therefore a dropdown entry that silently matches nothing,
--   which is why DepartmentStatusController withholds such entries and logs a warning rather than serving
--   them.
--
-- SECTION 3 REPLACES THE STATUS VOCABULARY — this is the part that changes existing behaviour
--
--   The STATUS-dimension codes were display labels ('All Complaints', 'Sent Back to Me'). They are now
--   normalised codes (ALL_COMPLAINTS, SENT_BACK_TO_CEPC_DO) because they arrive from ROLE_STATUS_MAPPING and
--   the label is carried separately in LABEL_EN. The old rows cannot simply be left in place: they would be
--   unreachable — nothing offers them any more — while still occupying (DIMENSION, FILTER_CODE) slots and
--   reading as live configuration to the next person.
--
--   So section 3 DELETES the STATUS-dimension rows and lets CepcDashboardFilterSeeder re-insert the new
--   vocabulary on the next boot. Scoped to DIMENSION = 'STATUS' on purpose: the TAB and KPI rows are
--   unchanged and share codes with the old status rows ('Meeting Scheduled' is both), so an unscoped delete
--   would silently break the tabs and the KPI cards.
--
--   These are seeder-owned reference rows, not user data — the seeder is the source of truth for them and
--   re-creates them if-absent on every boot in every environment. Nothing user-entered is deleted.
--
-- NO SEED ROWS HERE
--
--   RoleStatusMappingSeeder carries no @Profile, so it runs in every environment including production and
--   inserts the rows if-absent on every boot. Duplicating them here would mean two sources for one matrix,
--   and UQ_ROLE_STATUS would turn a drift into a failed migration rather than a mere disagreement. The table
--   is created empty on purpose — the same argument V101 makes for CEPC_DASHBOARD_FILTER.
--
-- ORACLE-SPECIFIC NOTES
--
--   SEQUENCE is an Oracle keyword but NOT a reserved word, so it is legal unquoted as a column name and is
--   left unquoted here — every other identifier in this tree is unquoted too, and Oracle folds them all to
--   uppercase, which is the spelling Hibernate's @Column(name = "SEQUENCE") emits. MySQL V104 backticks it
--   for readability only; the two resolve to the same column.
--
--   CREATED_AT and UPDATED_AT are TIMESTAMP, matching the entity's LocalDateTime. They are NOT
--   TIMESTAMP WITH TIME ZONE: that is what a ZonedDateTime field would map to, and no other entity in this
--   schema uses one.
--
--   Guarded on USER_TABLES / USER_TAB_COLUMNS / USER_INDEXES so a re-run is safe. Those guards compare
--   TABLE_NAME against bare uppercase literals, which is correct HERE and must not be "fixed" to match
--   MySQL V104's UPPER() form — see V101's header for the full argument. Same code shape, opposite folding
--   direction.
-- ============================================================

-- ============================================================
-- 1. ROLE_STATUS_MAPPING
-- ============================================================

DECLARE
    PROCEDURE add_table(p_table VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = p_table;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;

    PROCEDURE add_index(p_index VARCHAR2, p_sql VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = p_index;
        IF v_count = 0 THEN
            EXECUTE IMMEDIATE p_sql;
        END IF;
    END;
BEGIN
    -- ─────────────────────────────────────────────────────────
    -- ROLE_NAME is spelled as CepcIdentityResolver.CEPC_ROLES spells it: CEPC_INCHARGE, not
    -- CEPC_IN_CHARGE. resolveCepcRole() returns that spelling and the lookup is an equality match, so a
    -- differently-spelled row is a row no caller ever sees.
    --
    -- STATUS_CODE must match an active CEPC_DASHBOARD_FILTER row with DIMENSION = 'STATUS'. See the header.
    --
    -- SEQUENCE is the position in THIS role's dropdown, ascending. Per-role, which is the point of the
    -- table: CEPC_DASHBOARD_FILTER.DISPLAY_ORDER is per status and so cannot differ between roles.
    -- ─────────────────────────────────────────────────────────
    add_table('ROLE_STATUS_MAPPING', '
        CREATE TABLE ROLE_STATUS_MAPPING (
            ID          NUMBER GENERATED BY DEFAULT AS IDENTITY,
            ROLE_NAME   VARCHAR2(50)  NOT NULL,
            STATUS_CODE VARCHAR2(50)  NOT NULL,
            DESCRIPTION VARCHAR2(255),
            SEQUENCE    NUMBER(10)    NOT NULL,
            CREATED_AT  TIMESTAMP     NOT NULL,
            UPDATED_AT  TIMESTAMP     NOT NULL,
            CONSTRAINT PK_ROLE_STATUS_MAPPING PRIMARY KEY (ID),
            -- One row per role per status. This is what makes the seeder''s insert-if-absent idempotent.
            CONSTRAINT UQ_ROLE_STATUS UNIQUE (ROLE_NAME, STATUS_CODE)
        )');

    -- Every read is "the dropdown for one role, in order".
    add_index('IDX_ROLE_STATUS_ROLE',
        'CREATE INDEX IDX_ROLE_STATUS_ROLE ON ROLE_STATUS_MAPPING (ROLE_NAME, SEQUENCE)');
END;
/

-- ============================================================
-- 2. CEPC_DASHBOARD_FILTER — drop VISIBLE_TO_ROLES
--
--    Role visibility now lives in ROLE_STATUS_MAPPING. The column is dropped rather than left unused
--    because two places answering "may this role pick this status" is exactly the drift this change set out
--    to remove, and the next reader has no way to tell which one is live.
-- ============================================================

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'CEPC_DASHBOARD_FILTER' AND COLUMN_NAME = 'VISIBLE_TO_ROLES';
    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE CEPC_DASHBOARD_FILTER DROP COLUMN VISIBLE_TO_ROLES';
    END IF;
END;
/

-- ============================================================
-- 3. CEPC_DASHBOARD_FILTER — clear the old STATUS vocabulary
--
--    Scoped to DIMENSION = 'STATUS'. The TAB and KPI rows must survive: they are unchanged, and they share
--    filter codes with the old status rows, so an unscoped delete would empty the dashboard's tabs and KPI
--    cards as well. CepcDashboardFilterSeeder re-inserts the new STATUS rows on the next boot.
-- ============================================================

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'CEPC_DASHBOARD_FILTER';
    IF v_count > 0 THEN
        -- Re-runnable: on a second run the surviving rows are the new vocabulary, which the seeder will
        -- simply re-insert again. Harmless, if briefly empty between this statement and the next boot.
        EXECUTE IMMEDIATE 'DELETE FROM CEPC_DASHBOARD_FILTER WHERE DIMENSION = ''STATUS''';
        COMMIT;
    END IF;
END;
/
