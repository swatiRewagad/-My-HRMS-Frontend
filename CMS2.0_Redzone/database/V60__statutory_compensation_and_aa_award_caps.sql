-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V60 — Statutory compensation caps (RBIO) and AA award ceilings.
--
-- RULING APPLIED (product owner, 2026-09-22):
--   RBIO combined compensation cap is Rs 33,00,000 = Rs 30,00,000 financial/consequential loss
--   plus Rs 3,00,000 mental harassment. All values remain configurable.
--
-- WHY THE COMBINED CAP CHANGES AND THE COMPONENTS DO NOT. The two component caps already summed to
-- 3300000 while the enforced combined ceiling was 3000000 — equal to the consequential-loss cap
-- alone. That made the harassment allowance partly unusable: a complainant awarded the full Rs 30
-- Lakh financial loss could receive NOTHING for mental harassment, because the combined ceiling was
-- already reached. This migration corrects only the combined figure.
--
--   AA order award ceiling mirrors RBIO exactly and is ARMED. It was 0, and 0 means "unenforced" in
--   AaAppealOrderService.validateAward, so an Appellate Authority order could record an award of any
--   size — the statutory limit existed only in prose. Shipping 0 is no longer acceptable.
--
-- NOT ARMED BY THIS MIGRATION: cms.aa.order.block_sub_judice stays false. Whether the AA may proceed
-- on a matter already before a court is a legal question that has NOT been answered. Do not arm it
-- by inference from this file.
--
-- Re-running is safe: the UPDATEs are scoped by CONFIG_KEY and are idempotent, and every INSERT is
-- guarded with WHERE NOT EXISTS. Scoping is BY KEY, never by description text.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

-- ── RBIO: raise the combined cap to the sum of its components ──────────────────────────────────
-- Guarded so a value an operator has since corrected by hand is not overwritten: only the specific
-- superseded figure is moved.
UPDATE SYSTEM_CONFIG
   SET CONFIG_VALUE = '3300000',
       DESCRIPTION = 'RBIO cap on combined compensation (rupees). Equals the SUM of the component caps: 3000000 consequential loss + 300000 time/harassment. Previously 3000000, which capped the two components below their sum and made the harassment allowance unusable once a full financial award was granted.',
       UPDATED_BY = 'V60_migration',
       UPDATED_AT = NOW()
 WHERE CONFIG_KEY = 'cms.rbio.compensation.max_combined'
   AND CONFIG_VALUE = '3000000';

-- Present on a database where the row was never seeded.
INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.rbio.compensation.max_combined', '3300000',
       'RBIO cap on combined compensation (rupees). Equals the SUM of the component caps: 3000000 consequential loss + 300000 time/harassment.',
       'V60_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.rbio.compensation.max_combined');

-- ── AA: split the single award cap into the same two components as RBIO ────────────────────────
-- One max_award_amount cannot express a 30L + 3L rule. The key structure mirrors RBIO so the two
-- tiers cannot drift apart.
INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.aa.order.max_financial_compensation', '3000000',
       'Ceiling on financial/consequential-loss compensation in an AA order (rupees). Mirrors cms.rbio.compensation.max_consequential_loss. ARMED.',
       'V60_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.aa.order.max_financial_compensation');

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.aa.order.max_harassment_compensation', '300000',
       'Ceiling on mental-harassment compensation in an AA order (rupees). Mirrors cms.rbio.compensation.max_time_harassment. ARMED.',
       'V60_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.aa.order.max_harassment_compensation');

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.aa.order.max_combined', '3300000',
       'Ceiling on the two AA compensation components combined (rupees). Must equal financial + harassment.',
       'V60_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.aa.order.max_combined');

-- ── AA: arm the order total ceiling ────────────────────────────────────────────────────────────
-- 0 previously meant no ceiling at all. Scoped to the superseded value so an operator who has
-- already set a deliberate figure keeps it.
UPDATE SYSTEM_CONFIG
   SET CONFIG_VALUE = '3300000',
       DESCRIPTION = 'Maximum award in rupees an AA order may record, applied to the order total. ARMED at the combined statutory ceiling (3000000 financial + 300000 harassment) per the 2026-09-22 ruling. 0 still disables the check, retaining an operator escape hatch.',
       UPDATED_BY = 'V60_migration',
       UPDATED_AT = NOW()
 WHERE CONFIG_KEY = 'cms.aa.order.max_award_amount'
   AND CONFIG_VALUE = '0';

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.aa.order.max_award_amount', '3300000',
       'Maximum award in rupees an AA order may record, applied to the order total. ARMED at the combined statutory ceiling. 0 disables the check.',
       'V60_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.aa.order.max_award_amount');

-- block_sub_judice is deliberately absent from this migration. See the header.
