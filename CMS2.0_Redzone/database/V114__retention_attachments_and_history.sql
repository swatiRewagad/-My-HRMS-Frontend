-- V114 — declare the 7-year retention obligation over complaint ATTACHMENTS and HISTORY.
--
-- WHY THIS EXISTS: V17 seeded seven RETENTION_POLICY rows, and the only one covering complaint data
-- is COMPLAINT_PII over COMPLAINTS. Attachments and the timeline had NO policy row at all. That is
-- not a harmless omission:
--
--   * RetentionService refuses a policy whose TARGET_TABLE is outside its compiled allowlist, and
--     that allowlist ALREADY contains COMPLAINT_ATTACHMENTS / ATTACHMENT_METADATA. So a future admin
--     could add a row keeping attachments for 30 days and the engine would execute it happily —
--     nothing declared that the evidence attached to a complaint is subject to the same 7 years as
--     the complaint itself.
--   * The retention screen lists policies. A data class with no row is invisible there, so the gap
--     could not be noticed by the person responsible for it.
--
-- The periods match the existing COMPLAINT_PII row exactly (2555 days = 7 years), because this is a
-- declaration of an obligation that already applies, not a new policy decision.
--
-- ENABLED = 0, deliberately, matching COMPLAINT_PII. These rows exist so the obligation is declared,
-- listed and auditable; actually purging citizen complaint data is a separate operational decision
-- that must be taken deliberately by an administrator, not switched on by a migration. The nightly
-- sweep is dry-run by default in any case (cms.security.retention.destructive_enabled).
--
-- TIMESTAMP_COLUMN: measured from the row's own timestamp, because neither table carries the parent
-- complaint's closure date. NOTE this differs from COMPLAINT_PII, which measures from CLOSED_AT. An
-- attachment uploaded at filing on a complaint that stays open for two years therefore reaches 2555
-- days before its parent does. Keeping the child longer than the parent would require a join the
-- engine's single-table model cannot express, so the honest statement is that these two rows are a
-- FLOOR. Raised as an open point rather than silently approximated.
--
-- AUDIT_CATEGORY = 1 on BOTH rows. This is the flag that says "this is part of the evidential
-- record", and it is what makes the 2555-day period coherent rather than contradictory: the
-- retention screen's own rule (asserted by e2e/admin/retention.spec.ts) is that an OPERATIONAL
-- category must be shorter-lived than the audit trail, because operational data retained for seven
-- years defeats the point of the distinction. Attached evidence and the record of what was done to a
-- complaint are not operational data that happens to be kept a long time — they are the proof an
-- investigator needs, and they are useless if they expire before the complaint they belong to. Were
-- these rows marked operational, declaring the statutory 7 years on them would simultaneously
-- violate the operational-vs-audit rule, which is how the contradiction first surfaced.
--
-- Idempotent (WHERE NOT EXISTS on the unique CATEGORY), so re-running changes nothing and an
-- operator's later edit to the period is never overwritten.
--
-- Depends on RetentionService.ALLOWED_TABLES containing COMPLAINT_TIMELINE — added in the same
-- change. Without it the history row below would be declared and then refused at run time.

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'COMPLAINT_ATTACHMENT', 'COMPLAINT_ATTACHMENTS', 'UPLOADED_AT', 2555, 1, 0, 0,
       'Evidence attached to a complaint - 7 year retention, matching the complaint record itself',
       'V114_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'COMPLAINT_ATTACHMENT');

INSERT INTO RETENTION_POLICY (CATEGORY, TARGET_TABLE, TIMESTAMP_COLUMN, RETENTION_DAYS,
                              AUDIT_CATEGORY, REDACT_INSTEAD_OF_DELETE, ENABLED, DESCRIPTION,
                              UPDATED_BY, UPDATED_AT)
SELECT 'COMPLAINT_HISTORY', 'COMPLAINT_TIMELINE', 'PERFORMED_AT', 2555, 1, 0, 0,
       'Record of what was done to a complaint - 7 year retention; AUDIT_CATEGORY so a purge treats it as a trail',
       'V114_MIGRATION', NOW()
 WHERE NOT EXISTS (SELECT 1 FROM RETENTION_POLICY WHERE CATEGORY = 'COMPLAINT_HISTORY');
