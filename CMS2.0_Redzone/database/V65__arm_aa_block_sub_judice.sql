-- ─────────────────────────────────────────────────────────────────────────────────────────────────
-- V65 — ARM the AA sub-judice guard.
--
-- RULING APPLIED (product owner, 2026-09-23): cms.aa.order.block_sub_judice is armed. This SUPERSEDES
-- the V56 and V60 headers, which recorded it as deliberately unenforced pending legal sign-off. Those
-- comments remain accurate history of why it shipped false; they are no longer the current ruling.
--
-- WHAT THE GUARD DOES. The appellant declares a related court trial at registration and it is stored
-- on APPEALS.HAS_RELATED_COURT_TRIAL. Nothing read it, so an order could issue on a matter before a
-- court with no record that the point was considered. Armed, AaAppealOrderService refuses the order
-- UNLESS a ground is stated, and the ground is persisted on APPEAL_ORDER.GROUND as the audit record.
--
-- WHY ARMING IS SAFE WITHOUT A RULING ON WHO MAY AUTHORISE IT. The guard cannot block a lawful order:
-- stating a ground always permits one. It converts a silent decision into a recorded one, which is why
-- the earlier concern -- that arming on a guess could refuse orders the AA is entitled to pass -- does
-- not apply. A refusal here means only that no reasoning was supplied.
--
-- RE-RUNNABILITY, AND WHY THE UPDATE IS SCOPED ON UPDATED_BY AS WELL AS ON THE VALUE.
-- Scoping on CONFIG_VALUE = 'false' alone is NOT sufficient here, and this was caught by testing the
-- re-run rather than assuming it. Unlike the numeric caps in V60 — where the superseded figure '0' was
-- a value no operator would choose deliberately — 'false' is exactly the value an operator sets when
-- they intend to DISARM the guard. A value-only scope therefore cannot tell "never migrated" from
-- "deliberately switched off", and every replay would silently re-arm a guard someone had turned off.
--
-- So the correction additionally requires that the row still carry its original migration stamp. Once
-- a human or the admin API has written to the row, UPDATED_BY no longer matches and their decision is
-- left alone. A second run of this file is a no-op because the stamp is then 'V65_migration'.
-- ─────────────────────────────────────────────────────────────────────────────────────────────────

UPDATE SYSTEM_CONFIG
   SET CONFIG_VALUE = 'true',
       DESCRIPTION = 'Whether an order is refused on an appeal whose appellant declared a related court trial, unless a ground is stated. TRUE = ARMED per the 2026-09-23 ruling: the order is refused with HTTP 409 aa.order.error_sub_judice until reasoning is supplied, and the stated ground is persisted on APPEAL_ORDER.GROUND as the audit record of the decision. Set to false to restore the previous unenforced behaviour.',
       UPDATED_BY = 'V65_migration',
       UPDATED_AT = NOW()
 WHERE CONFIG_KEY = 'cms.aa.order.block_sub_judice'
   AND CONFIG_VALUE = 'false'
   AND (UPDATED_BY IS NULL OR UPDATED_BY IN ('V56', 'V56_migration'));

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'cms.aa.order.block_sub_judice', 'true',
       'Whether an order is refused on an appeal whose appellant declared a related court trial, unless a ground is stated. ARMED. The stated ground is persisted on APPEAL_ORDER.GROUND as the audit record of the decision.',
       'V65_migration', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE CONFIG_KEY = 'cms.aa.order.block_sub_judice');
