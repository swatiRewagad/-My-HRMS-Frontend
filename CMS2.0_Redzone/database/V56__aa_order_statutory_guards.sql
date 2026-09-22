-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V56 — SYSTEM_CONFIG switches for the two statutory guards on an AA final order
--
-- Session S4 (final QA gate). Re-runnable: insert-if-absent via SELECT ... WHERE NOT EXISTS, the same
-- idiom V55 uses, so re-running never overwrites a value an operator has since tuned.
--
-- WHY THESE EXIST
-- The S4 gate found that AA enforced NO award ceiling of any kind — an Appellate Authority could
-- record an award of any size — and that the appellant's declared "related court trial" flag was
-- persisted at registration and then never read, so an order could issue on a matter before a court
-- with nothing recording that the point was considered. Both gaps appeared to be covered by
-- e2e/aa/backlog-traceable.spec.ts, which in fact drove PASS_AWARD, an action that does not exist in
-- AA, and swallowed the resulting error.
--
-- WHY BOTH DEFAULT TO OFF — read before "tidying" these to a real value
-- The ENFORCEMENT MECHANISM is engineering and is delivered, tested and mutation-verified
-- (AaAppealOrderServiceTest). The VALUES are questions of Scheme law:
--   * max_award_amount  — the ceiling figure. 3000000/300000 appear in RbioCompensationService as
--                         hardcoded constants of unverified provenance. Arming AA with a guessed
--                         figure would either cap a citizen's lawful statutory award or permit an
--                         unlawful one. Both are worse than the documented status quo.
--   * block_sub_judice  — whether the AA may proceed at all while a matter is before a court, and who
--                         may authorise it, is a legal question. There is no Legal Cases module in
--                         this product, so the appellant's declared flag is the only signal available.
--
-- 0 / false therefore means "unenforced, exactly as today". Arming either after legal sign-off is a
-- one-row config change with no code release. Until then this migration is behaviour-neutral by
-- design, which is what makes it safe to ship ahead of that sign-off.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT seed.k, seed.v, seed.d, seed.u, seed.t FROM (
    SELECT 'cms.aa.order.max_award_amount' AS k, '0' AS v,
           'Maximum award in rupees an AA order may record. 0 = UNENFORCED (current behaviour). NOT a figure from the RBIOS Scheme - requires legal sign-off before being armed. Set to the statutory ceiling to refuse an over-cap award with HTTP 422 aa.order.error_award_cap_exceeded.' AS d,
           'V56' AS u, NOW() AS t
    UNION ALL SELECT 'cms.aa.order.block_sub_judice', 'false',
           'Whether an order is refused on an appeal whose appellant declared a related court trial, unless a ground is stated. FALSE = UNENFORCED (current behaviour): an order CAN today be passed on a sub-judice matter. Requires legal sign-off on whether the AA may proceed and who may authorise it. When true, the stated ground is persisted on the order as the audit record of the decision.',
           'V56', NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG sc WHERE sc.config_key = seed.k
);
