-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V70 — S3: reopen authority + reason vocabulary as CONFIGURATION (UST550-551).
--       Oracle counterpart of MySQL V72.
--
-- See MySQL V72 for the rationale. The load-bearing point: these rows are the SOURCE but not the only
-- line of defence — RbioWorkflowService carries the same two values as compiled-in fallbacks, so a
-- database missing these rows still restricts reopening to the Ombudsman rather than allowing everyone.
-- A missing configuration row must never widen an authority.
--
-- Insert-if-absent, so re-running is safe AND so this never overwrites a value an operator has since
-- changed. That is why there is no UPDATE here.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_exists = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V70', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    seed_config('cms.rbio.reopen.roles',
                'RBIO_OMBUDSMAN,RBIO_ADMIN',
                'Roles permitted to reopen a closed RBIO complaint (UST550). RBIO_ADMIN retains the ability it held before the restriction so administrative correction of a mis-closure remains possible.');

    seed_config('cms.rbio.reopen.reasons',
                'APPELLATE_AUTHORITY,COURT_ORDER,CORRECTION_REQUIRED',
                'Permitted reopen reasons (UST551). The UI renders these as its dropdown; the server refuses any value outside the list.');
END;
/
