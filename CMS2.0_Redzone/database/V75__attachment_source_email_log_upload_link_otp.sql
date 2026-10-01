-- ============================================================================
-- V75: Attachment provenance, email-log columns, upload-link OTP state
-- Session S6 (UST589, UST593, UST599)
--
-- Companion to V74. Split rather than merged so the notification/timeline work
-- and the attachment/email/upload-link work can be reviewed independently.
--
-- Every column added here is NULLABLE, without exception. Schema also reaches the
-- shared dev database through Hibernate ddl-auto:update, and all three tables hold
-- existing rows with no recoverable value for any of these columns — a NOT NULL
-- addition would fail the ALTER outright.
--
-- Column names are snake_case to match Hibernate's default naming strategy, which
-- is what these tables already use (from_status, performed_at, etc.). Emitting
-- camelCase here would make ddl-auto create a SECOND, parallel set of columns and
-- the mapping would read the empty ones.
-- ============================================================================

SET @db := DATABASE();

-- ---------------------------------------------------------------------------
-- 1. COMPLAINT_ATTACHMENTS — source distinguishability (UST589)
--
-- The table had eight columns and NONE recorded who supplied a document or in
-- what capacity, while the CEPC screen displayed an `uploadedBy` value fabricated
-- in the browser from the logged-in username — a column that did not exist, and
-- was wrong for every document someone else had uploaded.
--
-- Shape copied from APPEAL_ATTACHMENTS, which already carried this triple.
-- ---------------------------------------------------------------------------

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
      AND COLUMN_NAME = 'uploaded_by') = 0,
  'ALTER TABLE COMPLAINT_ATTACHMENTS ADD COLUMN uploaded_by VARCHAR(200) NULL',
  'SELECT ''COMPLAINT_ATTACHMENTS.uploaded_by already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- OFFICER | COMPLAINANT | REGULATED_ENTITY | SYSTEM. Separate from uploaded_by
-- because the display rule is about PROVENANCE, not identity: staff must see at a
-- glance which documents came from outside RBI, and deriving that by
-- pattern-matching a username would break the moment a username format changed.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
      AND COLUMN_NAME = 'source') = 0,
  'ALTER TABLE COMPLAINT_ATTACHMENTS ADD COLUMN source VARCHAR(30) NULL',
  'SELECT ''COMPLAINT_ATTACHMENTS.source already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
      AND COLUMN_NAME = 'document_type') = 0,
  'ALTER TABLE COMPLAINT_ATTACHMENTS ADD COLUMN document_type VARCHAR(40) NULL',
  'SELECT ''COMPLAINT_ATTACHMENTS.document_type already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- Deliberately NO backfill. Existing rows genuinely have no recorded provenance,
-- and stamping them 'OFFICER' would assert a fact nobody verified — the UI shows
-- "Not recorded" for a NULL, which is the truthful answer.

-- Index for the source filter on the attachments tab.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_ATTACHMENTS'
      AND INDEX_NAME = 'idx_attachment_source') = 0,
  'CREATE INDEX idx_attachment_source ON COMPLAINT_ATTACHMENTS (source)',
  'SELECT ''idx_attachment_source already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. SIMULATED_EMAILS — per-complaint email log support (UST593)
--
-- COMPLAINT_ID and idx_email_complaint ALREADY EXISTED and were populated on
-- ingestion; no query ever read them, which is why a per-complaint email log was
-- unobtainable despite the data being present. Only the two display columns below
-- are new.
-- ---------------------------------------------------------------------------

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'SIMULATED_EMAILS'
      AND COLUMN_NAME = 'template_used') = 0,
  'ALTER TABLE SIMULATED_EMAILS ADD COLUMN template_used VARCHAR(200) NULL',
  'SELECT ''SIMULATED_EMAILS.template_used already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'SIMULATED_EMAILS'
      AND COLUMN_NAME = 'cc_recipients') = 0,
  'ALTER TABLE SIMULATED_EMAILS ADD COLUMN cc_recipients VARCHAR(1000) NULL',
  'SELECT ''SIMULATED_EMAILS.cc_recipients already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 3. UPLOAD_LINKS — dual-OTP verification state (UST599)
--
-- Whether the OTPs had been satisfied previously existed ONLY as an Angular
-- signal, while POST /upload/{token} checked no OTP at all — so verification was
-- decorative and the upload endpoint was open to anyone holding the token. The
-- upload path now refuses while this column is NULL.
--
-- Nullable, and deliberately NOT backfilled: existing rows predate the dual OTP
-- and must not be treated as verified.
-- ---------------------------------------------------------------------------

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'UPLOAD_LINKS'
      AND COLUMN_NAME = 'otp_verified_at') = 0,
  'ALTER TABLE UPLOAD_LINKS ADD COLUMN otp_verified_at DATETIME(6) NULL',
  'SELECT ''UPLOAD_LINKS.otp_verified_at already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 4. Config for the UST604 forward restriction and the UST656 domain allowlist
--
-- Both are CONFIG rather than literals so an operator can correct them without a
-- redeploy. Defaults reproduce current behaviour exactly: the roles the frontend
-- already exempted, and the two domains the previous validation endpoint accepted.
-- ---------------------------------------------------------------------------

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_at)
SELECT * FROM (
  SELECT 'notification.upload_link.restricted_forward_roles' AS k,
         'RBIO_DEPUTY_OMBUDSMAN,RBIO_OMBUDSMAN,DEPUTY_OMBUDSMAN,OMBUDSMAN' AS v,
         'UST604: roles a complaint may NOT be forwarded to while a secure upload link is active' AS d,
         NOW() AS t UNION ALL
  SELECT 'email.outbound.allowed_domains', 'rbi.org.in,rbi.gov.in',
         'UST656: the only domains outbound complaint email may be addressed to', NOW() UNION ALL
  SELECT 'email.outbound.from_address', 'noreply@rbi.org.in',
         'Sender address recorded on outbound complaint email', NOW()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG c WHERE c.config_key = seed.k);
