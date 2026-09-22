-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V54 — SYSTEM_CONFIG switches for the two statutory guards on an AA final order
--
-- Session S4 (final QA gate). Oracle counterpart of MySQL V56. Re-runnable: the local seed_config
-- procedure inserts only when the key is absent, so re-running never overwrites a tuned value.
--
-- WHY THESE EXIST
-- The S4 gate found that AA enforced NO award ceiling of any kind — an Appellate Authority could
-- record an award of any size — and that the appellant's declared "related court trial" flag was
-- persisted at registration and then never read, so an order could issue on a matter before a court
-- with nothing recording that the point was considered. Both gaps appeared covered by
-- e2e/aa/backlog-traceable.spec.ts, which in fact drove PASS_AWARD — an action that does not exist in
-- AA — and swallowed the resulting error.
--
-- WHY BOTH DEFAULT TO OFF — read before "tidying" these to a real value
-- The ENFORCEMENT MECHANISM is engineering and is delivered, tested and mutation-verified
-- (AaAppealOrderServiceTest). The VALUES are questions of Scheme law:
--   * max_award_amount  — the ceiling figure. 3000000/300000 appear in RbioCompensationService as
--                         hardcoded constants of unverified provenance. Arming AA with a guessed
--                         figure would either cap a citizen's lawful statutory award or permit an
--                         unlawful one. Both are worse than the documented status quo.
--   * block_sub_judice  — whether the AA may proceed while a matter is before a court, and who may
--                         authorise it, is a legal question. There is no Legal Cases module in this
--                         product, so the appellant's declared flag is the only signal available.
--
-- 0 / false therefore means "unenforced, exactly as today". Arming either after legal sign-off is a
-- one-row config change with no code release, so this migration is behaviour-neutral by design.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

DECLARE
    PROCEDURE seed_config(p_key VARCHAR2, p_value VARCHAR2, p_desc VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = p_key;
        IF v_exists = 0 THEN
            INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
            VALUES (p_key, p_value, p_desc, 'V54', SYSTIMESTAMP);
        END IF;
    END;
BEGIN
    seed_config('cms.aa.order.max_award_amount', '0',
        'Maximum award in rupees an AA order may record. 0 = UNENFORCED (current behaviour). NOT a figure from the RBIOS Scheme - requires legal sign-off before being armed. Set to the statutory ceiling to refuse an over-cap award with HTTP 422 aa.order.error_award_cap_exceeded.');
    seed_config('cms.aa.order.block_sub_judice', 'false',
        'Whether an order is refused on an appeal whose appellant declared a related court trial, unless a ground is stated. FALSE = UNENFORCED (current behaviour): an order CAN today be passed on a sub-judice matter. Requires legal sign-off on whether the AA may proceed and who may authorise it. When true, the stated ground is persisted on the order as the audit record of the decision.');
    COMMIT;
END;
/
