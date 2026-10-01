-- ============================================================
-- V76 — WF_OFFICER_POOL: add rows for the REAL Keycloak identities
-- Session S3 (UST471 round-robin, UST450 on-leave exclusion)
-- ============================================================
--
-- WHY THIS EXISTS
--
--   WF_OFFICER_POOL carries the active / on-leave state that UST450 and UST471 depend on, but its
--   seeded user ids do not exist in the Keycloak realm. Verified against the live realm:
--
--     pool rows for RBIO_OFFICER : rbio.officer1, rbio.officer2, rbio.officer3, rbio.officer4
--     realm members of RBIO_OFFICER : rbio.officer, rbio.officer.mum, rbio.officer.del,
--                                     rbio_officer_002, rbio_officer_003, orbio_officer_001
--
--   Zero overlap. The same is true for CEPC (pool has cepc.officer1..3; the realm's CEPC_DO members
--   are cepc_do1 and cepc_do2). And COMPLAINTS.assigned_officer holds the REALM vocabulary
--   (rbio_officer_001, cepc_do_001, cepc_incharge1 ...), not the pool's.
--
--   So the pool could not be used as the candidate source: assigning from it would hand complaints to
--   accounts that cannot log in, and the officer who should act would never see the case.
--   DurableRoundRobinAssigner therefore takes CANDIDATES FROM KEYCLOAK and uses the pool only to
--   EXCLUDE officers marked inactive or on leave. An officer with no pool row is eligible, because
--   absence of a row is absence of evidence rather than evidence of unavailability.
--
--   That design is correct but leaves UST450 toothless for anyone without a row: there is no row to
--   set on_leave on. This migration closes that by adding a row per REAL realm member, so leave and
--   deactivation can actually be recorded against the people who receive work.
--
-- WHAT THIS DELIBERATELY DOES NOT DO
--
--   It does not delete or rewrite the existing rbio.officer1-style rows. Another session may rely on
--   them, ddl-auto never reverts, and the reassignment cluster (UST838-845) already counts workload
--   per entityCode against them. They are left in place: they are inert for assignment because they
--   are not realm members, so Keycloak never offers them as candidates.
--
--   It does not set max_workload to anything meaningful. There is no authoritative per-officer
--   caseload figure from RBI, so the column keeps the table default (40) and capacity is NOT enforced
--   by the assigner. Recording a guessed cap would silently stop assigning work to a real officer.
--   Flagged for RBI confirmation.
--
--   Identities are the realm's CURRENT members, captured at authoring time. This is seed data for a
--   dev/SIT database, not a source of truth — the realm remains authoritative, and an officer added to
--   the realm later simply has no pool row and is eligible by default, which is the safe direction.
--
-- Re-running is safe: every INSERT is guarded with NOT EXISTS on (user_id, role_group). MySQL 8.4 has
-- no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so the index guard uses information_schema.
-- ============================================================

DROP PROCEDURE IF EXISTS s3_pool_add_index;

DELIMITER $$
CREATE PROCEDURE s3_pool_add_index(
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

-- The assigner reads the whole pool for a role group and filters in Java, so (role_group, user_id) is
-- the access path. An index already exists on (role_group, is_active, is_on_leave); this one supports
-- the exclusion lookup and keeps user_id ordering cheap.
CALL s3_pool_add_index('WF_OFFICER_POOL', 'idx_officer_pool_role_user', 'role_group, user_id');

-- ─────────────────────────────────────────────────────────────
-- RBIO_OFFICER — the public-portal filing path (ComplaintRoutingService.routeFromPublicPortal)
-- ─────────────────────────────────────────────────────────────
INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'rbio.officer', 'RBIO Officer', 'RBIO_OFFICER', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='rbio.officer' AND role_group='RBIO_OFFICER');

INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'rbio.officer.mum', 'RBIO Officer Mumbai', 'RBIO_OFFICER', 'MUMBAI', 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='rbio.officer.mum' AND role_group='RBIO_OFFICER');

INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'rbio.officer.del', 'RBIO Officer Delhi', 'RBIO_OFFICER', 'DELHI', 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='rbio.officer.del' AND role_group='RBIO_OFFICER');

INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'rbio_officer_002', 'RBIO Officer 002', 'RBIO_OFFICER', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='rbio_officer_002' AND role_group='RBIO_OFFICER');

INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'rbio_officer_003', 'RBIO Officer 003', 'RBIO_OFFICER', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='rbio_officer_003' AND role_group='RBIO_OFFICER');

-- ORBIO is the RBIO-module Ombudsman; its officers carry RBIO roles by design (see identityHeadersFor
-- in the e2e helpers, which maps orbio* to RBIO roles for the same reason).
INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'orbio_officer_001', 'ORBIO Officer 001', 'RBIO_OFFICER', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='orbio_officer_001' AND role_group='RBIO_OFFICER');

-- ─────────────────────────────────────────────────────────────
-- CEPC_DO — the CEPC dealing-officer pool
-- ─────────────────────────────────────────────────────────────
INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'cepc_do1', 'CEPC Dealing Officer 1', 'CEPC_DO', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='cepc_do1' AND role_group='CEPC_DO');

INSERT INTO WF_OFFICER_POOL (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload)
SELECT 'cepc_do2', 'CEPC Dealing Officer 2', 'CEPC_DO', NULL, 1, 0, 0, 40
WHERE NOT EXISTS (SELECT 1 FROM WF_OFFICER_POOL WHERE user_id='cepc_do2' AND role_group='CEPC_DO');

-- ─────────────────────────────────────────────────────────────
-- Rotation pointers for the groups this session assigns within.
-- ─────────────────────────────────────────────────────────────
-- AaAssignmentCounterInitialiser creates these on first use, so this is belt-and-braces for a clean
-- database; it also documents which role groups are rotated. last_assigned_user_id stays NULL, which
-- the assigner reads as "no rotation history, start at the head of the ordered list".
INSERT INTO wf_assignment_counter (role_group, last_assigned_index, last_assigned_user_id, updated_at)
SELECT 'RBIO_OFFICER', 0, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM wf_assignment_counter WHERE role_group='RBIO_OFFICER');

INSERT INTO wf_assignment_counter (role_group, last_assigned_index, last_assigned_user_id, updated_at)
SELECT 'CEPC_DO', 0, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM wf_assignment_counter WHERE role_group='CEPC_DO');

INSERT INTO wf_assignment_counter (role_group, last_assigned_index, last_assigned_user_id, updated_at)
SELECT 'DEO', 0, NULL, NOW()
WHERE NOT EXISTS (SELECT 1 FROM wf_assignment_counter WHERE role_group='DEO');

DROP PROCEDURE IF EXISTS s3_pool_add_index;
