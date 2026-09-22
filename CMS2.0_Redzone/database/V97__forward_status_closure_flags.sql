-- ============================================================
-- V97 — Make the three FORWARD statuses count as closed, and distinguish a regulator referral
-- Session S5 (UST628, 627, 661)
-- ============================================================
--
-- 1. WHY IS_CLOSED MUST BE 'Y' ON THE FORWARD STATUSES
--
--   UST628/627 require that on Save and Proceed the status changes to the relevant closed/forwarded code
--   automatically and NO further closure action by the Dealing Official is needed. But the three FORWARD
--   rows were seeded by RbioStatusMasterSeeder's `status(...)` helper, which does not set IS_CLOSED —
--   only its `closed(...)` helper does. So SENT_TO_OTHER_OFFICE, SENT_TO_OTHER_DEPT and SENT_TO_OTHER_RE
--   all carried IS_CLOSED='N'.
--
--   That is not cosmetic. RbioStatusVocabulary.closedStatuses() is THE authoritative closed list and it
--   selects on IS_CLOSED='Y'. Every reader of it therefore treated a forwarded complaint as still OPEN:
--   the reminder scheduler kept nudging officers about complaints that had left the office, the open-work
--   queues kept showing them, and no closure communication could fire for them.
--
--   SENT_TO_OTHER_OFFICE is deliberately EXCLUDED from this change. A transfer to another Ombudsman
--   office is NOT a closure — the complaint is still live under the Scheme, merely in another office's
--   jurisdiction, and UST564 has it waiting for CRPC Head approval. Marking it closed would drop a live
--   citizen complaint out of every work queue while it sat pending approval. Only the two genuinely
--   outbound destinations (another RBI department, an external regulator) end RBIO's handling.
--
-- 2. WHY SENT_TO_OTHER_RE NEEDS ITS OWN LEGACY_VALUE
--
--   SENT_TO_OTHER_RE was seeded with LEGACY_VALUE = NULL, while the FORWARD_TO_REGULATORY_BODY action
--   writes the literal 'forwarded_external' — which is SENT_TO_OTHER_DEPT's legacy value. So a complaint
--   referred to SEBI was indistinguishable from one sent to an internal RBI department, and the
--   citizen-facing tracker could not tell a complainant which had happened.
--
--   A NULL legacy value also means no live complaint can ever match the row, so the status was
--   unreachable: it existed in the vocabulary and described nothing.
--
--   The new value is 'forwarded_regulator'. Introducing a distinct value rather than reusing the shared
--   one is what makes the two outcomes separable; the RBIO action is updated to write it in the same
--   change, so the vocabulary and the writer agree.
--
-- Re-running is safe: every statement is an idempotent UPDATE scoped BY STATUS_CODE. Never by label —
-- labels are localised, so an English match would find none of the translated rows.
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. The two genuinely outbound forwards are closures
-- ─────────────────────────────────────────────────────────────
-- IS_TERMINAL stays 'N': a complaint forwarded out can still be reopened if it comes back, which is a
-- different question from whether RBIO currently considers the file shut.
UPDATE RBIO_STATUS_MASTER
   SET IS_CLOSED = 'Y'
 WHERE STATUS_CODE IN ('SENT_TO_OTHER_DEPT', 'SENT_TO_OTHER_RE');

-- ─────────────────────────────────────────────────────────────
-- 2. A regulator referral becomes distinguishable
-- ─────────────────────────────────────────────────────────────
-- Guarded so a re-run does not overwrite a value an operator has since corrected by hand.
UPDATE RBIO_STATUS_MASTER
   SET LEGACY_VALUE = 'forwarded_regulator'
 WHERE STATUS_CODE = 'SENT_TO_OTHER_RE'
   AND (LEGACY_VALUE IS NULL OR LEGACY_VALUE = 'forwarded_external');

-- ─────────────────────────────────────────────────────────────
-- 3. Citizen visibility
-- ─────────────────────────────────────────────────────────────
-- A complainant is entitled to know their complaint has left RBI's jurisdiction — that is the whole point
-- of UST766's awareness email. All three FORWARD statuses were already seeded citizen-visible; this
-- asserts it explicitly so a later reseed cannot quietly hide them.
UPDATE RBIO_STATUS_MASTER
   SET IS_CITIZEN_VISIBLE = 'Y'
 WHERE STATUS_CODE IN ('SENT_TO_OTHER_OFFICE', 'SENT_TO_OTHER_DEPT', 'SENT_TO_OTHER_RE');
