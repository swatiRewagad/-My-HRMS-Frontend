-- ============================================================
-- CMS 2.0 — V10: Forward / Proposed-Action Workflow Fields on
--                complaints, and NO/PNO Comment Targeting on
--                complaint_comments (H2 dev-local / MySQL)
--
-- NOTE: table/column names use lowercase to match the names
-- Hibernate's ddl-auto=update actually created at runtime.
-- If a column already exists on your database (e.g. it was
-- added earlier via ddl-auto=update), the corresponding line
-- below will fail with "Duplicate column name" — that is safe
-- to ignore; skip that line and continue with the rest.
-- ============================================================

-- 1. ALTER complaints: proposed action/clause capture + inter-office
--    forward tracking (RBIO Forward tab / CRPC Head transfer workflow)
ALTER TABLE complaints ADD COLUMN proposed_action VARCHAR(100);
ALTER TABLE complaints ADD COLUMN proposed_clause VARCHAR(100);
ALTER TABLE complaints ADD COLUMN forwarded_office_code VARCHAR(10);
ALTER TABLE complaints ADD COLUMN pre_forward_officer VARCHAR(200);
ALTER TABLE complaints ADD COLUMN pre_forward_role VARCHAR(50);

-- 2. ALTER complaint_comments: scope a comment to a specific Nodal Officer
--    Record and target it at the NO (Nodal Officer) or PNO (Principal Nodal
--    Officer) — used by the Nodal Officer Record detail's real comment thread.
ALTER TABLE complaint_comments ADD COLUMN no_record_number VARCHAR(50);
ALTER TABLE complaint_comments ADD COLUMN target VARCHAR(10);

CREATE INDEX idx_comment_no_record ON complaint_comments (no_record_number);

-- No seed/reference data required for this increment.
