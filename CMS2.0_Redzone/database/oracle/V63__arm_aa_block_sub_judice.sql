-- ============================================================
-- V63 — ARM the AA sub-judice guard.
-- Oracle counterpart of MySQL V65. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V65). In brief:
--
-- RULING APPLIED (product owner, 2026-09-23): CMS.AA.ORDER.BLOCK_SUB_JUDICE is armed. This SUPERSEDES
--   the V54 and V58 headers, which recorded it as deliberately unenforced pending legal sign-off.
--
-- Armed, AaAppealOrderService refuses an order on an appeal whose appellant declared a related court
--   trial UNLESS a ground is stated, and persists that ground on APPEAL_ORDER.GROUND as the audit
--   record. It cannot block a lawful order, because stating a ground always permits one.
--
-- RE-RUNNABILITY, AND WHY THE UPDATE IS SCOPED ON UPDATED_BY AS WELL AS ON THE VALUE.
-- Scoping on CONFIG_VALUE = 'false' alone is NOT sufficient, and testing the re-run proved it. Unlike
-- the numeric caps in V58 — where the superseded '0' was a figure no operator would choose — 'false' is
-- exactly what an operator sets to DISARM the guard, so a value-only scope cannot distinguish "never
-- migrated" from "deliberately switched off" and every replay would re-arm it behind their back.
--
-- The correction therefore also requires the row to still carry its original migration stamp. Once a
-- human or the admin API has written to it, UPDATED_BY no longer matches and the decision stands.
-- ============================================================

DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_count = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V63_MIGRATION', SYSTIMESTAMP);
        END IF;
    END;

    -- Scoped to the superseded value AND to the original migration stamp, so a setting an operator has
    -- deliberately changed survives a replay. See the header for why the value alone is not enough.
    PROCEDURE retune_config(p_key VARCHAR2, p_from VARCHAR2, p_to VARCHAR2, p_desc VARCHAR2) IS
    BEGIN
        UPDATE SYSTEM_CONFIG
           SET CONFIG_VALUE = p_to,
               DESCRIPTION  = p_desc,
               UPDATED_BY   = 'V63_MIGRATION',
               UPDATED_AT   = SYSTIMESTAMP
         WHERE CONFIG_KEY = p_key
           AND CONFIG_VALUE = p_from
           AND (UPDATED_BY IS NULL OR UPDATED_BY IN ('V54', 'V54_MIGRATION'));
    END;
BEGIN
    retune_config('cms.aa.order.block_sub_judice', 'false', 'true',
        'Whether an order is refused on an appeal whose appellant declared a related court trial, '
        || 'unless a ground is stated. TRUE = ARMED per the 2026-09-23 ruling: the order is refused '
        || 'with HTTP 409 aa.order.error_sub_judice until reasoning is supplied, and the stated ground '
        || 'is persisted on APPEAL_ORDER.GROUND as the audit record of the decision. Set to false to '
        || 'restore the previous unenforced behaviour.');

    seed_config('cms.aa.order.block_sub_judice', 'true',
        'Whether an order is refused on an appeal whose appellant declared a related court trial, '
        || 'unless a ground is stated. ARMED. The stated ground is persisted on APPEAL_ORDER.GROUND '
        || 'as the audit record of the decision.');

    COMMIT;
END;
/
