-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V50 — seed the AA role groups into WF_OFFICER_POOL (session S2C handoff to S3)
--
-- The assignment engine draws candidates from WF_OFFICER_POOL by ROLE_GROUP. That table was created by
-- cms-workflow-service and is populated for CRPC/RBIO/CEPC only, so every AA assignment correctly but
-- uselessly returned UNASSIGNED_POOL_EMPTY: the engine works, there was simply nobody to assign to.
--
-- Seeded here rather than left to S3 because the engine is S2C's deliverable and an engine that can
-- never place work is not a finished deliverable.
--
-- The user ids match the accounts provisioned by deployment/provision-aa-roles.sh, so a developer who
-- has run that script gets a working pool with no further action.
--
-- Thresholds are deliberately MODEST (not 0). 0 means UNLIMITED in this table, and an unlimited default
-- would let a single officer absorb an entire backlog silently — the opposite of the per-officer cap
-- these stories exist to enforce. Operators tune them per officer from the AA admin console.
--
-- Re-runnable.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO wf_officer_pool
    (user_id, display_name, role_group, regional_office, is_active, is_on_leave,
     current_workload, max_workload, skill_languages)
SELECT * FROM (
    SELECT 'aa_do_001'          AS user_id, 'AA Dealing Officer 1' AS display_name,
           'AA_DO'              AS role_group, NULL AS regional_office,
           1 AS is_active, 0 AS is_on_leave, 0 AS current_workload, 20 AS max_workload,
           NULL AS skill_languages
    UNION ALL SELECT 'aa_reviewer_001', 'AA Reviewer 1 (tier 1)', 'AA_REVIEWER', NULL, 1, 0, 0, 15, NULL
    UNION ALL SELECT 'aa_reviewer_002', 'AA Reviewer 2 (tier 2)', 'AA_REVIEWER', NULL, 1, 0, 0, 15, NULL
    UNION ALL SELECT 'aa_secretariat_001', 'AA Secretariat 1', 'AA_SECRETARIAT', NULL, 1, 0, 0, 25, NULL
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM wf_officer_pool p
     WHERE p.user_id = seed.user_id AND p.role_group = seed.role_group
);
