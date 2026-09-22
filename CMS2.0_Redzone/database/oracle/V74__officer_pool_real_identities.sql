-- ============================================================
-- V74 — WF_OFFICER_POOL: add rows for the REAL Keycloak identities
-- Session S3 (UST471 round-robin, UST450 on-leave exclusion). Oracle counterpart of MySQL V76.
-- ============================================================
--
-- See database/V76__officer_pool_real_identities.sql for the full rationale. In brief: the pool's
-- seeded user ids (rbio.officer1..4, cepc.officer1..3) do not exist in the Keycloak realm, whose real
-- members are rbio.officer / rbio.officer.mum / rbio_officer_002 / cepc_do1 / ... — zero overlap,
-- verified against the live realm. COMPLAINTS.assigned_officer holds the REALM vocabulary.
--
-- DurableRoundRobinAssigner therefore takes candidates from Keycloak and uses this table only to
-- EXCLUDE inactive / on-leave officers. These rows exist so leave and deactivation can be recorded
-- against the people who actually receive work; without them UST450 has nothing to act on.
--
-- Existing rows are deliberately NOT deleted or rewritten: another session may depend on them, and
-- they are inert for assignment because Keycloak never offers them as candidates.
--
-- max_workload keeps the table default. No authoritative per-officer caseload figure exists, and a
-- guessed cap would silently stop assigning work to a real officer. Flagged for RBI confirmation.
--
-- Re-running is safe: guarded DDL via USER_INDEXES, and every INSERT is guarded on
-- (USER_ID, ROLE_GROUP).
-- ============================================================

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES WHERE INDEX_NAME = 'IDX_OFFICER_POOL_ROLE_USER';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_OFFICER_POOL_ROLE_USER ON WF_OFFICER_POOL (ROLE_GROUP, USER_ID)';
    END IF;
END;
/

-- ─────────────────────────────────────────────────────────────
-- Officers, keyed on the realm's actual usernames
-- ─────────────────────────────────────────────────────────────
DECLARE
    PROCEDURE add_officer(p_user_id  VARCHAR2,
                          p_display  VARCHAR2,
                          p_group    VARCHAR2,
                          p_office   VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM WF_OFFICER_POOL
         WHERE USER_ID = p_user_id AND ROLE_GROUP = p_group;
        IF v_exists = 0 THEN
            INSERT INTO WF_OFFICER_POOL
                (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE,
                 CURRENT_WORKLOAD, MAX_WORKLOAD)
            VALUES (p_user_id, p_display, p_group, p_office, 1, 0, 0, 40);
        END IF;
    END;

    PROCEDURE add_pointer(p_group VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM WF_ASSIGNMENT_COUNTER WHERE ROLE_GROUP = p_group;
        IF v_exists = 0 THEN
            INSERT INTO WF_ASSIGNMENT_COUNTER
                (ROLE_GROUP, LAST_ASSIGNED_INDEX, LAST_ASSIGNED_USER_ID, UPDATED_AT)
            VALUES (p_group, 0, NULL, SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    -- RBIO_OFFICER: the public-portal filing path.
    add_officer('rbio.officer',      'RBIO Officer',         'RBIO_OFFICER', NULL);
    add_officer('rbio.officer.mum',  'RBIO Officer Mumbai',  'RBIO_OFFICER', 'MUMBAI');
    add_officer('rbio.officer.del',  'RBIO Officer Delhi',   'RBIO_OFFICER', 'DELHI');
    add_officer('rbio_officer_002',  'RBIO Officer 002',     'RBIO_OFFICER', NULL);
    add_officer('rbio_officer_003',  'RBIO Officer 003',     'RBIO_OFFICER', NULL);
    -- ORBIO is the RBIO-module Ombudsman; its officers carry RBIO roles by design.
    add_officer('orbio_officer_001', 'ORBIO Officer 001',    'RBIO_OFFICER', NULL);

    -- CEPC_DO: the CEPC dealing-officer pool.
    add_officer('cepc_do1', 'CEPC Dealing Officer 1', 'CEPC_DO', NULL);
    add_officer('cepc_do2', 'CEPC Dealing Officer 2', 'CEPC_DO', NULL);

    -- Rotation pointers. The initialiser bean creates these on first use; seeding them documents
    -- which role groups are rotated and keeps a clean database consistent. A NULL
    -- LAST_ASSIGNED_USER_ID reads as "no rotation history, start at the head of the ordered list".
    add_pointer('RBIO_OFFICER');
    add_pointer('CEPC_DO');
    add_pointer('DEO');

    COMMIT;
END;
/
