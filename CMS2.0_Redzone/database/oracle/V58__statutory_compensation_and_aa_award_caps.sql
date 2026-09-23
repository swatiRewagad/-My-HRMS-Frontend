-- ============================================================
-- V58 — Statutory compensation caps (RBIO) and AA award ceilings
-- Oracle counterpart of MySQL V60. The two directories' V-numbers are NOT in sync.
-- ============================================================
--
-- The rationale is recorded in full in the MySQL counterpart (database/V60). In brief:
--
-- RULING APPLIED (product owner, 2026-09-22): the RBIO combined compensation cap is Rs 33,00,000 =
--   Rs 30,00,000 financial/consequential loss + Rs 3,00,000 mental harassment, all configurable.
--   Only the combined figure changes. It was 3000000 — equal to the consequential-loss cap alone —
--   which capped the two components below their own sum, so a complainant awarded the full Rs 30 Lakh
--   financial loss could receive NOTHING for mental harassment.
--
-- The AA order ceiling mirrors RBIO and is ARMED. It was 0, and 0 means "unenforced" in
--   AaAppealOrderService.validateAward, so an AA order could record an award of any size.
--
-- NOT ARMED HERE: CMS.AA.ORDER.BLOCK_SUB_JUDICE stays false. Whether the AA may proceed on a matter
--   already before a court is a legal question that has NOT been answered. Do not arm it by inference.
--
-- Every statement is guarded so a re-run is safe: seeds are COUNT-checked by CONFIG_KEY and the
-- corrections are idempotent UPDATEs scoped BY CONFIG_KEY, never by description text.
-- ============================================================

DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_count NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_count FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_count = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V58_MIGRATION', SYSTIMESTAMP);
        END IF;
    END;

    -- Scoped to the superseded value so a figure an operator has already corrected by hand survives.
    PROCEDURE retune_config(p_key VARCHAR2, p_from VARCHAR2, p_to VARCHAR2, p_desc VARCHAR2) IS
    BEGIN
        UPDATE SYSTEM_CONFIG
           SET CONFIG_VALUE = p_to,
               DESCRIPTION  = p_desc,
               UPDATED_BY   = 'V58_MIGRATION',
               UPDATED_AT   = SYSTIMESTAMP
         WHERE CONFIG_KEY = p_key
           AND CONFIG_VALUE = p_from;
    END;
BEGIN
    -- ── RBIO: raise the combined cap to the sum of its components ──────────────────────────────
    retune_config('cms.rbio.compensation.max_combined', '3000000', '3300000',
        'RBIO cap on combined compensation (rupees). Equals the SUM of the component caps: 3000000 '
        || 'consequential loss + 300000 time/harassment. Previously 3000000, which capped the two '
        || 'components below their sum and made the harassment allowance unusable.');

    seed_config('cms.rbio.compensation.max_combined', '3300000',
        'RBIO cap on combined compensation (rupees). Equals the SUM of the component caps: 3000000 '
        || 'consequential loss + 300000 time/harassment.');

    -- ── AA: split the single award cap into the same two components as RBIO ────────────────────
    -- One max_award_amount cannot express a 30L + 3L rule. Mirroring the RBIO key structure keeps the
    -- two tiers from drifting apart.
    seed_config('cms.aa.order.max_financial_compensation', '3000000',
        'Ceiling on financial/consequential-loss compensation in an AA order (rupees). Mirrors '
        || 'cms.rbio.compensation.max_consequential_loss. ARMED.');

    seed_config('cms.aa.order.max_harassment_compensation', '300000',
        'Ceiling on mental-harassment compensation in an AA order (rupees). Mirrors '
        || 'cms.rbio.compensation.max_time_harassment. ARMED.');

    seed_config('cms.aa.order.max_combined', '3300000',
        'Ceiling on the two AA compensation components combined (rupees). Must equal financial + harassment.');

    -- ── AA: arm the order total ceiling ────────────────────────────────────────────────────────
    retune_config('cms.aa.order.max_award_amount', '0', '3300000',
        'Maximum award in rupees an AA order may record, applied to the order total. ARMED at the '
        || 'combined statutory ceiling (3000000 financial + 300000 harassment) per the 2026-09-22 '
        || 'ruling. 0 still disables the check, retaining an operator escape hatch.');

    seed_config('cms.aa.order.max_award_amount', '3300000',
        'Maximum award in rupees an AA order may record, applied to the order total. ARMED at the '
        || 'combined statutory ceiling. 0 disables the check.');
END;
/

COMMIT;
