-- ============================================================
-- CMS 2.0 — V10: Forward / Proposed-Action Workflow Fields on
--                COMPLAINTS, and NO/PNO Comment Targeting on
--                COMPLAINT_COMMENTS (Oracle)
-- ============================================================

-- ─────────────────────────────────────────────────────────────
-- 1. ALTER COMPLAINTS: proposed action/clause capture + inter-office
--    forward tracking (RBIO Forward tab / CRPC Head transfer workflow)
-- ─────────────────────────────────────────────────────────────
ALTER TABLE COMPLAINTS ADD (proposed_action VARCHAR2(100));
ALTER TABLE COMPLAINTS ADD (proposed_clause VARCHAR2(100));
ALTER TABLE COMPLAINTS ADD (forwarded_office_code VARCHAR2(10));
ALTER TABLE COMPLAINTS ADD (pre_forward_officer VARCHAR2(200));
ALTER TABLE COMPLAINTS ADD (pre_forward_role VARCHAR2(50));

-- ─────────────────────────────────────────────────────────────
-- 2. ALTER COMPLAINT_COMMENTS: scope a comment to a specific Nodal
--    Officer Record and target it at the NO (Nodal Officer) or PNO
--    (Principal Nodal Officer) — used by the Nodal Officer Record
--    detail's real comment thread.
-- ─────────────────────────────────────────────────────────────
ALTER TABLE COMPLAINT_COMMENTS ADD (no_record_number VARCHAR2(50));
ALTER TABLE COMPLAINT_COMMENTS ADD (target VARCHAR2(10));

CREATE INDEX idx_comment_no_record ON COMPLAINT_COMMENTS (no_record_number);

-- No seed/reference data required for this increment.
