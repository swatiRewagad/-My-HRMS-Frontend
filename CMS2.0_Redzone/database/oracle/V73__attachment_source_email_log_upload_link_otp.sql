-- ============================================================================
-- V73 (Oracle): Attachment provenance, email-log columns, upload-link OTP state
-- Session S6 (UST589, UST593, UST599)
--
-- Oracle counterpart of MySQL database/V75. The two directories' V-numbers are
-- deliberately NOT in sync.
--
-- Every column is NULL-able: all three tables hold existing rows with no
-- recoverable value for any of them, so a NOT NULL addition would fail outright.
-- Guarded via USER_TAB_COLUMNS / USER_INDEXES so the script is safe to re-run;
-- ORA-01430 (column exists) and ORA-00955 (name in use) are also tolerated to
-- cover a concurrent run.
-- ============================================================================

DECLARE
  PROCEDURE add_col(p_table VARCHAR2, p_col VARCHAR2, p_ddl VARCHAR2) IS
    v_exists NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = p_table AND COLUMN_NAME = p_col;
    IF v_exists = 0 THEN
      EXECUTE IMMEDIATE p_ddl;
    END IF;
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE = -1430 THEN NULL; ELSE RAISE; END IF;
  END;
BEGIN
  -- ── 1. COMPLAINT_ATTACHMENTS: source distinguishability (UST589) ──
  --
  -- The table had eight columns and NONE recorded who supplied a document or in
  -- what capacity, while the CEPC screen displayed an UPLOADED_BY value fabricated
  -- in the browser from the logged-in username — a column that did not exist, and
  -- was wrong for every document somebody else had uploaded.
  --
  -- Shape copied from APPEAL_ATTACHMENTS, which already carried this triple.
  add_col('COMPLAINT_ATTACHMENTS', 'UPLOADED_BY',
    'ALTER TABLE COMPLAINT_ATTACHMENTS ADD (UPLOADED_BY VARCHAR2(200) NULL)');

  -- OFFICER | COMPLAINANT | REGULATED_ENTITY | SYSTEM. Separate from UPLOADED_BY
  -- because the display rule is about PROVENANCE, not identity.
  add_col('COMPLAINT_ATTACHMENTS', 'SOURCE',
    'ALTER TABLE COMPLAINT_ATTACHMENTS ADD (SOURCE VARCHAR2(30) NULL)');

  add_col('COMPLAINT_ATTACHMENTS', 'DOCUMENT_TYPE',
    'ALTER TABLE COMPLAINT_ATTACHMENTS ADD (DOCUMENT_TYPE VARCHAR2(40) NULL)');

  -- ── 2. SIMULATED_EMAILS: per-complaint email log support (UST593) ──
  --
  -- COMPLAINT_ID and its index ALREADY EXISTED and were populated on ingestion; no
  -- query ever read them, which is why a per-complaint email log was unobtainable
  -- despite the data being present. Only these two display columns are new.
  add_col('SIMULATED_EMAILS', 'TEMPLATE_USED',
    'ALTER TABLE SIMULATED_EMAILS ADD (TEMPLATE_USED VARCHAR2(200) NULL)');

  add_col('SIMULATED_EMAILS', 'CC_RECIPIENTS',
    'ALTER TABLE SIMULATED_EMAILS ADD (CC_RECIPIENTS VARCHAR2(1000) NULL)');

  -- ── 3. UPLOAD_LINKS: dual-OTP verification state (UST599) ──
  --
  -- Whether the OTPs had been satisfied previously existed ONLY as an Angular
  -- signal, while the upload endpoint checked no OTP at all — so verification was
  -- decorative and the endpoint was open to anyone holding the token. The upload
  -- path now refuses while this is NULL.
  --
  -- Deliberately NOT backfilled: existing rows predate the dual OTP and must not be
  -- treated as verified.
  add_col('UPLOAD_LINKS', 'OTP_VERIFIED_AT',
    'ALTER TABLE UPLOAD_LINKS ADD (OTP_VERIFIED_AT TIMESTAMP NULL)');
END;
/

-- Index for the source filter on the attachments tab.
DECLARE
  v_exists NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_exists FROM USER_INDEXES
   WHERE INDEX_NAME = 'IDX_ATTACHMENT_SOURCE';
  IF v_exists = 0 THEN
    EXECUTE IMMEDIATE 'CREATE INDEX IDX_ATTACHMENT_SOURCE ON COMPLAINT_ATTACHMENTS (SOURCE)';
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

-- ---------------------------------------------------------------------------
-- 4. Config for the UST604 forward restriction and the UST656 domain allowlist
--
-- Both are CONFIG rather than literals so an operator can correct them without a
-- redeploy. Defaults reproduce current behaviour exactly: the roles the frontend
-- already exempted, and the two domains the previous validation endpoint accepted.
-- ---------------------------------------------------------------------------

DECLARE
  TYPE t_row IS RECORD (k VARCHAR2(100), v VARCHAR2(500), d VARCHAR2(500));
  TYPE t_tab IS TABLE OF t_row;
  v_rows t_tab := t_tab(
    t_row('notification.upload_link.restricted_forward_roles',
          'RBIO_DEPUTY_OMBUDSMAN,RBIO_OMBUDSMAN,DEPUTY_OMBUDSMAN,OMBUDSMAN',
          'UST604: roles a complaint may NOT be forwarded to while a secure upload link is active'),
    t_row('email.outbound.allowed_domains', 'rbi.org.in,rbi.gov.in',
          'UST656: the only domains outbound complaint email may be addressed to'),
    t_row('email.outbound.from_address', 'noreply@rbi.org.in',
          'Sender address recorded on outbound complaint email')
  );
  v_exists NUMBER;
BEGIN
  FOR i IN 1 .. v_rows.COUNT LOOP
    SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG WHERE CONFIG_KEY = v_rows(i).k;
    IF v_exists = 0 THEN
      INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_AT)
      VALUES (v_rows(i).k, v_rows(i).v, v_rows(i).d, SYSTIMESTAMP);
    END IF;
  END LOOP;
  COMMIT;
END;
/
