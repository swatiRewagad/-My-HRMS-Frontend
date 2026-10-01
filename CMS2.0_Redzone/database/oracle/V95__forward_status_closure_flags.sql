-- ============================================================
-- V95 — Make the two outbound FORWARD statuses count as closed, and distinguish a regulator referral
-- Session S5 (UST628, 627, 661). Oracle counterpart of MySQL V97.
-- ============================================================
--
-- See database/V97__forward_status_closure_flags.sql for the full rationale. In brief:
--
--   * The three FORWARD rows were seeded by RbioStatusMasterSeeder's `status(...)` helper, which does not
--     set IS_CLOSED — only `closed(...)` does. RbioStatusVocabulary.closedStatuses() selects on
--     IS_CLOSED='Y' and is THE authoritative closed list, so every reader treated a forwarded complaint as
--     still OPEN: the reminder scheduler kept chasing officers about complaints that had left the office,
--     open queues kept listing them, and no closure communication could fire.
--
--   * SENT_TO_OTHER_OFFICE is deliberately NOT marked closed. A transfer to another Ombudsman office is
--     not a closure — the complaint is still live under the Scheme in another office's jurisdiction, and
--     UST564 has it waiting for CRPC Head approval. Marking it closed would drop a live citizen complaint
--     out of every work queue while it sat pending. Only the two outbound destinations end RBIO's handling.
--
--   * SENT_TO_OTHER_RE had LEGACY_VALUE = NULL while the action writes 'forwarded_external' — which is
--     SENT_TO_OTHER_DEPT's value. So a referral to SEBI was indistinguishable from an internal department
--     forward, and a NULL legacy value also meant no live complaint could ever match the row at all. It
--     now has its own value, and the writer is updated in the same change so the two agree.
--
-- IS_TERMINAL stays 'N': a forwarded complaint can still be reopened if it comes back, which is a
-- different question from whether RBIO currently considers the file shut.
--
-- Re-running is safe: every statement is an idempotent UPDATE scoped BY STATUS_CODE, never by label —
-- labels are localised, so an English match would find none of the translated rows.
-- ============================================================

BEGIN
    -- 1. The two genuinely outbound forwards are closures
    UPDATE RBIO_STATUS_MASTER
       SET IS_CLOSED = 'Y'
     WHERE STATUS_CODE IN ('SENT_TO_OTHER_DEPT', 'SENT_TO_OTHER_RE');

    -- 2. A regulator referral becomes distinguishable. Guarded so a re-run does not overwrite a value an
    --    operator has since corrected by hand.
    UPDATE RBIO_STATUS_MASTER
       SET LEGACY_VALUE = 'forwarded_regulator'
     WHERE STATUS_CODE = 'SENT_TO_OTHER_RE'
       AND (LEGACY_VALUE IS NULL OR LEGACY_VALUE = 'forwarded_external');

    -- 3. A complainant is entitled to know their complaint has left RBI's jurisdiction — the point of
    --    UST766's awareness email. Asserted explicitly so a later reseed cannot quietly hide them.
    UPDATE RBIO_STATUS_MASTER
       SET IS_CITIZEN_VISIBLE = 'Y'
     WHERE STATUS_CODE IN ('SENT_TO_OTHER_OFFICE', 'SENT_TO_OTHER_DEPT', 'SENT_TO_OTHER_RE');

    COMMIT;
END;
/
