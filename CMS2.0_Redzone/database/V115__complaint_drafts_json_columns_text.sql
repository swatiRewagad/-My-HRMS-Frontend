-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V115 — Widen COMPLAINT_DRAFTS.eligibility_answers_json / form_data_json to TEXT
--
-- WHY THIS CORRECTIVE MIGRATION EXISTS
--
--   The ComplaintDraft entity declares both columns with columnDefinition = "TEXT", but the live
--   table (created earlier by ddl-auto=update, before that columnDefinition was added) had them as
--   TINYTEXT — 255 bytes. ddl-auto=update only adds missing columns/tables; it never widens an
--   existing column's type, so every database created before the entity change is stuck narrow.
--
--   The citizen portal's multi-step wizard draft save (POST /api/v1/complaints/drafts) serialises
--   the whole form state — eligibility answers, complainant/entity/complaint/representative
--   sections, file metadata — into these two columns. Anything past step 2 or 3 exceeds 255 bytes,
--   so the insert fails with "Data truncation: Data too long for column 'eligibility_answers_json'"
--   and the citizen's draft is never saved.
--
--   Oracle has no counterpart: V5__complete_ddl_dml.sql already created both columns as CLOB, and
--   the Oracle profile runs ddl-auto=validate, so that schema never drifted from the entity.
--
-- Re-runnable: MODIFY COLUMN is unconditional and idempotent — a column already TEXT (or wider) is
-- unaffected by re-running this.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

ALTER TABLE complaint_drafts
    MODIFY eligibility_answers_json TEXT,
    MODIFY form_data_json TEXT;
