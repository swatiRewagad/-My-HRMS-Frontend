-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V48 — seed the AA role groups into WF_OFFICER_POOL. Oracle counterpart of MySQL V50.
--
-- The assignment engine draws candidates by ROLE_GROUP, and this table was populated for
-- CRPC/RBIO/CEPC only, so every AA assignment returned UNASSIGNED_POOL_EMPTY — the engine worked, there
-- was simply nobody to assign to.
--
-- Thresholds are modest rather than 0, because 0 means UNLIMITED here and an unlimited default defeats
-- the per-officer cap these stories exist to enforce.
--
-- Re-runnable.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DECLARE
    PROCEDURE seed_officer(p_user_id     VARCHAR2,
                           p_display     VARCHAR2,
                           p_role_group  VARCHAR2,
                           p_threshold   NUMBER) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM WF_OFFICER_POOL
         WHERE USER_ID = p_user_id AND ROLE_GROUP = p_role_group;
        IF v_exists = 0 THEN
            INSERT INTO WF_OFFICER_POOL
                (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE,
                 IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD, SKILL_LANGUAGES)
            VALUES (p_user_id, p_display, p_role_group, NULL, 1, 0, 0, p_threshold, NULL);
        END IF;
    END;
BEGIN
    seed_officer('aa_do_001',          'AA Dealing Officer 1',   'AA_DO',          20);
    seed_officer('aa_reviewer_001',    'AA Reviewer 1 (tier 1)', 'AA_REVIEWER',    15);
    seed_officer('aa_reviewer_002',    'AA Reviewer 2 (tier 2)', 'AA_REVIEWER',    15);
    seed_officer('aa_secretariat_001', 'AA Secretariat 1',       'AA_SECRETARIAT', 25);
    COMMIT;
END;
/
