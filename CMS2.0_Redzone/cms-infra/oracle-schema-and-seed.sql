-- ============================================================================
-- CMS 2.0 - Oracle Database Schema & Full Seed Data
-- Compatible with Oracle 19c+
-- Generated: 2026-07-07
-- ============================================================================

-- ─── Cleanup (drop existing objects) ─────────────────────────────────────────
BEGIN
  FOR t IN (SELECT table_name FROM user_tables WHERE table_name IN (
    'TRANSLATIONS','TRANSLATION_KEYS','COMPLAINT_TIMELINE','COMPLAINT_ATTACHMENTS',
    'COMPLAINTS','COMPLAINT_CATEGORIES','BANKS','REGULATED_ENTITIES','HOLIDAYS',
    'OTP_ATTEMPTS','CAPTCHA_SESSIONS','LOGIN_COOLOFFS','CITIZEN_EMAIL_VERIFICATIONS',
    'AUDIT_LOG','FORM_CONFIGS','EMAIL_DRAFTS','EMAIL_DRAFT_ATTACHMENTS',
    'EMAIL_IGNORE_LIST','EXTRACTION_RULE','SIMULATED_EMAILS','APPEALS',
    'APPEAL_TIMELINE','RE_RESPONSE_TRACKER','REPORT_DEFINITIONS','REPORT_SCHEDULES'
  )) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.table_name || ' CASCADE CONSTRAINTS';
  END LOOP;
  FOR s IN (SELECT sequence_name FROM user_sequences WHERE sequence_name LIKE 'SEQ_%') LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.sequence_name;
  END LOOP;
END;
/

-- ─── Sequences ───────────────────────────────────────────────────────────────
CREATE SEQUENCE seq_banks START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_complaint_categories START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_regulated_entities START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_complaints START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_complaint_timeline START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_complaint_attachments START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_translation_keys START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_translations START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_holidays START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_otp_attempts START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_captcha_sessions START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_login_cooloffs START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_citizen_email_verifications START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_audit_log START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_form_configs START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_email_drafts START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_email_draft_attachments START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_email_ignore_list START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_extraction_rule START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_simulated_emails START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_appeals START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_appeal_timeline START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_re_response_tracker START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_report_definitions START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE seq_report_schedules START WITH 1 INCREMENT BY 1 NOCACHE;

-- ─── Tables ──────────────────────────────────────────────────────────────────

CREATE TABLE banks (
  id              NUMBER(19) DEFAULT seq_banks.NEXTVAL PRIMARY KEY,
  code            VARCHAR2(50),
  created_at      TIMESTAMP(6),
  name            VARCHAR2(300) NOT NULL,
  status          VARCHAR2(20),
  type            VARCHAR2(100)
);
CREATE INDEX idx_bank_type ON banks(type);
CREATE INDEX idx_bank_status ON banks(status);

CREATE TABLE complaint_categories (
  id              NUMBER(19) DEFAULT seq_complaint_categories.NEXTVAL PRIMARY KEY,
  created_at      TIMESTAMP(6),
  description     VARCHAR2(500),
  name            VARCHAR2(200) NOT NULL,
  parent_id       NUMBER(19),
  sort_order      NUMBER(10),
  status          VARCHAR2(20)
);
CREATE INDEX idx_category_parent ON complaint_categories(parent_id);
CREATE INDEX idx_category_status ON complaint_categories(status);

CREATE TABLE regulated_entities (
  id                        NUMBER(19) DEFAULT seq_regulated_entities.NEXTVAL PRIMARY KEY,
  city                      VARCHAR2(100),
  created_at                TIMESTAMP(6),
  department                VARCHAR2(10) NOT NULL,
  entity_type               VARCHAR2(100),
  name                      VARCHAR2(500) NOT NULL,
  name_normalized           VARCHAR2(500),
  state                     VARCHAR2(50),
  status                    VARCHAR2(20),
  last_login_at             TIMESTAMP(6),
  nodal_officer_designation VARCHAR2(100),
  nodal_officer_email       VARCHAR2(200),
  nodal_officer_name        VARCHAR2(200),
  nodal_officer_phone       VARCHAR2(20),
  pno_email                 VARCHAR2(200),
  pno_name                  VARCHAR2(200),
  pno_phone                 VARCHAR2(20),
  portal_enabled            NUMBER(1) DEFAULT 0 NOT NULL,
  registration_date         TIMESTAMP(6)
);
CREATE INDEX idx_re_department ON regulated_entities(department);
CREATE INDEX idx_re_name ON regulated_entities(name);
CREATE INDEX idx_re_entity_type ON regulated_entities(entity_type);

CREATE TABLE complaints (
  id                            NUMBER(19) DEFAULT seq_complaints.NEXTVAL PRIMARY KEY,
  account_number                VARCHAR2(100),
  assigned_officer              VARCHAR2(200),
  assigned_role                 VARCHAR2(50),
  bank_branch                   VARCHAR2(300),
  bank_complaint_date           TIMESTAMP(6),
  bank_complaint_reference      VARCHAR2(200),
  bank_id                       NUMBER(19),
  category_id                   NUMBER(19),
  closed_at                     TIMESTAMP(6),
  complainant_address           VARCHAR2(500),
  complainant_email             VARCHAR2(200),
  complainant_name              VARCHAR2(200) NOT NULL,
  complainant_phone             VARCHAR2(20),
  complaint_number              VARCHAR2(50) NOT NULL,
  created_at                    TIMESTAMP(6),
  department                    VARCHAR2(20),
  description                   CLOB,
  entity_code                   VARCHAR2(50),
  escalated_at                  TIMESTAMP(6),
  filed_at                      TIMESTAMP(6),
  filing_type                   VARCHAR2(50),
  priority                      VARCHAR2(20),
  relief_sought                 CLOB,
  resolved_at                   TIMESTAMP(6),
  status                        VARCHAR2(30) NOT NULL,
  subject                       VARCHAR2(500) NOT NULL,
  updated_at                    TIMESTAMP(6),
  workflow_stage                 VARCHAR2(50),
  award_amount                  NUMBER(15,2),
  eligibility_timeline          CLOB,
  maintainability_determination VARCHAR2(30),
  maintainability_determined_at TIMESTAMP(6),
  maintainability_determined_by VARCHAR2(200),
  prior_re_complaint            NUMBER(1),
  re_complaint_date             DATE,
  re_complaint_reference        VARCHAR2(200),
  re_replied_and_dissatisfied   NUMBER(1),
  triage_flags                  CLOB,
  triage_signal                 VARCHAR2(10),
  adjudication_date             TIMESTAMP(6),
  adjudication_outcome          VARCHAR2(100),
  advisory_issued_at            TIMESTAMP(6),
  advisory_text                 CLOB,
  closure_cause                 VARCHAR2(50),
  compensation_type             VARCHAR2(30),
  conciliation_date             TIMESTAMP(6),
  conciliation_outcome          VARCHAR2(100),
  current_stage_deadline        TIMESTAMP(6),
  impleaded_parties             VARCHAR2(1000),
  last_reopened_at              TIMESTAMP(6),
  notice_13_1_issued_at         TIMESTAMP(6),
  reopen_count                  NUMBER(10),
  scheme_version                VARCHAR2(20),
  sla_deadline                  TIMESTAMP(6),
  sla_priority                  VARCHAR2(10),
  stage_assigned_at             TIMESTAMP(6)
);
CREATE UNIQUE INDEX idx_complaint_number ON complaints(complaint_number);
CREATE INDEX idx_complaint_status ON complaints(status);
CREATE INDEX idx_complaint_priority ON complaints(priority);
CREATE INDEX idx_complaint_email ON complaints(complainant_email);
CREATE INDEX idx_complaint_category ON complaints(category_id);
CREATE INDEX idx_complaint_bank ON complaints(bank_id);
CREATE INDEX idx_complaint_created ON complaints(created_at);
CREATE INDEX idx_complaint_status_created ON complaints(status, created_at);
CREATE INDEX idx_complaint_re_date ON complaints(re_complaint_date);
CREATE INDEX idx_complaint_triage ON complaints(triage_signal);
CREATE INDEX idx_complaint_maintainability ON complaints(maintainability_determination);
CREATE INDEX idx_complaint_award ON complaints(award_amount);

CREATE TABLE complaint_timeline (
  id              NUMBER(19) DEFAULT seq_complaint_timeline.NEXTVAL PRIMARY KEY,
  action          VARCHAR2(50) NOT NULL,
  complaint_id    NUMBER(19) NOT NULL,
  from_status     VARCHAR2(30),
  performed_at    TIMESTAMP(6),
  performed_by    VARCHAR2(200),
  remarks         CLOB,
  to_status       VARCHAR2(30)
);
CREATE INDEX idx_timeline_complaint ON complaint_timeline(complaint_id);
CREATE INDEX idx_timeline_performed_at ON complaint_timeline(performed_at);

CREATE TABLE complaint_attachments (
  id              NUMBER(19) DEFAULT seq_complaint_attachments.NEXTVAL PRIMARY KEY,
  complaint_id    NUMBER(19) NOT NULL,
  content_type    VARCHAR2(100),
  file_name       VARCHAR2(500) NOT NULL,
  file_size       NUMBER(19),
  original_name   VARCHAR2(500) NOT NULL,
  storage_path    VARCHAR2(1000),
  uploaded_at     TIMESTAMP(6)
);
CREATE INDEX idx_attachment_complaint ON complaint_attachments(complaint_id);

CREATE TABLE translation_keys (
  id              NUMBER(19) DEFAULT seq_translation_keys.NEXTVAL PRIMARY KEY,
  code            VARCHAR2(255) NOT NULL,
  created_at      TIMESTAMP(6),
  default_value   CLOB,
  description     VARCHAR2(500),
  module          VARCHAR2(100),
  updated_at      TIMESTAMP(6)
);
CREATE UNIQUE INDEX idx_tkey_code ON translation_keys(code);
CREATE INDEX idx_tkey_module ON translation_keys(module);

CREATE TABLE translations (
  id                  NUMBER(19) DEFAULT seq_translations.NEXTVAL PRIMARY KEY,
  locale              VARCHAR2(10) NOT NULL,
  updated_at          TIMESTAMP(6),
  value               CLOB NOT NULL,
  translation_key_id  NUMBER(19) NOT NULL,
  CONSTRAINT fk_trans_key FOREIGN KEY (translation_key_id) REFERENCES translation_keys(id)
);
CREATE UNIQUE INDEX idx_trans_key_locale ON translations(translation_key_id, locale);
CREATE INDEX idx_trans_locale ON translations(locale);

CREATE TABLE holidays (
  id              NUMBER(19) DEFAULT seq_holidays.NEXTVAL PRIMARY KEY,
  holiday_date    DATE NOT NULL,
  name            VARCHAR2(200) NOT NULL,
  is_national     NUMBER(1),
  type            VARCHAR2(50),
  year            NUMBER(10) NOT NULL
);
CREATE UNIQUE INDEX idx_holiday_date ON holidays(holiday_date);
CREATE INDEX idx_holiday_year ON holidays(year);

CREATE TABLE otp_attempts (
  id              NUMBER(19) DEFAULT seq_otp_attempts.NEXTVAL PRIMARY KEY,
  attempt_count   NUMBER(10) NOT NULL,
  channel         VARCHAR2(10) NOT NULL,
  created_at      TIMESTAMP(6) NOT NULL,
  email           VARCHAR2(200),
  expires_at      TIMESTAMP(6) NOT NULL,
  mobile_number   VARCHAR2(15) NOT NULL,
  otp_hash        VARCHAR2(128) NOT NULL,
  session_id      VARCHAR2(100) NOT NULL,
  used            NUMBER(1) NOT NULL,
  used_at         TIMESTAMP(6)
);
CREATE INDEX idx_otp_mobile ON otp_attempts(mobile_number);
CREATE INDEX idx_otp_session ON otp_attempts(session_id);
CREATE INDEX idx_otp_expiry ON otp_attempts(expires_at);
CREATE INDEX idx_otp_mobile_active ON otp_attempts(mobile_number, used, expires_at);

CREATE TABLE captcha_sessions (
  id              NUMBER(19) DEFAULT seq_captcha_sessions.NEXTVAL PRIMARY KEY,
  answer_hash     VARCHAR2(128) NOT NULL,
  captcha_token   VARCHAR2(64) NOT NULL,
  captcha_type    VARCHAR2(20) NOT NULL,
  created_at      TIMESTAMP(6) NOT NULL,
  expires_at      TIMESTAMP(6) NOT NULL,
  used            NUMBER(1) NOT NULL
);
CREATE UNIQUE INDEX idx_captcha_token ON captcha_sessions(captcha_token);
CREATE INDEX idx_captcha_expiry ON captcha_sessions(expires_at);

CREATE TABLE login_cooloffs (
  id                  NUMBER(19) DEFAULT seq_login_cooloffs.NEXTVAL PRIMARY KEY,
  client_ip           VARCHAR2(45) NOT NULL,
  cooloff_seconds     NUMBER(10) NOT NULL,
  cooloff_until       TIMESTAMP(6) NOT NULL,
  created_at          TIMESTAMP(6) NOT NULL,
  failed_attempts     NUMBER(10) NOT NULL,
  fingerprint_hash    VARCHAR2(128) NOT NULL,
  last_attempt_at     TIMESTAMP(6) NOT NULL,
  mobile_number       VARCHAR2(15) NOT NULL
);
CREATE INDEX idx_cooloff_fingerprint ON login_cooloffs(fingerprint_hash);
CREATE INDEX idx_cooloff_mobile ON login_cooloffs(mobile_number);
CREATE INDEX idx_cooloff_ip ON login_cooloffs(client_ip);
CREATE INDEX idx_cooloff_expiry ON login_cooloffs(cooloff_until);

CREATE TABLE citizen_email_verifications (
  id                  NUMBER(19) DEFAULT seq_citizen_email_verifications.NEXTVAL PRIMARY KEY,
  created_at          TIMESTAMP(6) NOT NULL,
  email               VARCHAR2(200) NOT NULL,
  expires_at          TIMESTAMP(6) NOT NULL,
  mobile_number       VARCHAR2(15) NOT NULL,
  verification_token  VARCHAR2(128) NOT NULL,
  verified            NUMBER(1) NOT NULL,
  verified_at         TIMESTAMP(6)
);
CREATE INDEX idx_email_verify_mobile ON citizen_email_verifications(mobile_number);
CREATE INDEX idx_email_verify_email ON citizen_email_verifications(email);
CREATE INDEX idx_email_verify_token ON citizen_email_verifications(verification_token);

CREATE TABLE audit_log (
  id                  NUMBER(19) DEFAULT seq_audit_log.NEXTVAL PRIMARY KEY,
  action              VARCHAR2(50) NOT NULL,
  actor               VARCHAR2(200) NOT NULL,
  actor_role          VARCHAR2(50),
  complaint_number    VARCHAR2(50) NOT NULL,
  ip_address          VARCHAR2(50),
  metadata            CLOB,
  new_state           VARCHAR2(50),
  previous_state      VARCHAR2(50),
  remarks             CLOB,
  timestamp           TIMESTAMP(6) NOT NULL
);
CREATE INDEX idx_audit_complaint ON audit_log(complaint_number);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_actor ON audit_log(actor);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);

CREATE TABLE form_configs (
  id              NUMBER(19) DEFAULT seq_form_configs.NEXTVAL PRIMARY KEY,
  active          NUMBER(1) NOT NULL,
  created_at      TIMESTAMP(6),
  form_key        VARCHAR2(100) NOT NULL,
  form_name       VARCHAR2(200),
  schema_json     CLOB,
  updated_at      TIMESTAMP(6),
  version         VARCHAR2(50),
  CONSTRAINT uk_form_key UNIQUE (form_key)
);

CREATE TABLE email_drafts (
  id                          NUMBER(19) DEFAULT seq_email_drafts.NEXTVAL PRIMARY KEY,
  amount_involved             NUMBER(15,2),
  assigned_to                 VARCHAR2(200),
  body                        CLOB,
  category                    VARCHAR2(50),
  complainant_address         VARCHAR2(500),
  complainant_district        VARCHAR2(100),
  complainant_name            VARCHAR2(200),
  complainant_phone           VARCHAR2(20),
  complainant_pincode         VARCHAR2(10),
  complainant_state           VARCHAR2(100),
  complaint_summary           VARCHAR2(500),
  converted_complaint_id      VARCHAR2(50),
  cpgrams_number              VARCHAR2(50),
  created_at                  TIMESTAMP(6),
  deo_decision                VARCHAR2(30),
  deo_remarks                 CLOB,
  detected_language           VARCHAR2(50),
  draft_id                    VARCHAR2(100) NOT NULL,
  entity_name                 VARCHAR2(100),
  entity_type                 VARCHAR2(30),
  is_duplicate                NUMBER(1) NOT NULL,
  is_vernacular               NUMBER(1) NOT NULL,
  language_name               VARCHAR2(100),
  message_id                  VARCHAR2(200),
  mode_of_receipt             VARCHAR2(30),
  non_maintainable_reason     VARCHAR2(100),
  ocr_confidence              NUMBER(10) NOT NULL,
  ocr_extracted_fields_json   CLOB,
  ocr_processed               NUMBER(1) NOT NULL,
  parent_complaint_id         VARCHAR2(50),
  processed_by                VARCHAR2(200),
  received_at                 TIMESTAMP(6),
  reviewer_assigned_to        VARCHAR2(200),
  reviewer_decision           VARCHAR2(30),
  reviewer_remarks            CLOB,
  sender_email                VARCHAR2(200),
  status                      VARCHAR2(30),
  subject                     VARCHAR2(500),
  target_office               VARCHAR2(100),
  thread_id                   VARCHAR2(100),
  translated_body             CLOB,
  translation_confidence      NUMBER(5,2),
  updated_at                  TIMESTAMP(6),
  branch_name                 VARCHAR2(200),
  CONSTRAINT uk_draft_id UNIQUE (draft_id)
);
CREATE INDEX idx_draft_thread ON email_drafts(thread_id);
CREATE INDEX idx_draft_status ON email_drafts(status);
CREATE INDEX idx_draft_assigned ON email_drafts(assigned_to);
CREATE INDEX idx_draft_sender ON email_drafts(sender_email);

CREATE TABLE email_draft_attachments (
  id              NUMBER(19) DEFAULT seq_email_draft_attachments.NEXTVAL PRIMARY KEY,
  created_at      TIMESTAMP(6),
  draft_id        VARCHAR2(100) NOT NULL,
  file_name       VARCHAR2(500) NOT NULL,
  file_size       NUMBER(19) NOT NULL,
  file_type       VARCHAR2(100),
  ocr_confidence  NUMBER(10) NOT NULL,
  ocr_text        CLOB,
  storage_path    VARCHAR2(1000),
  uploaded_by     VARCHAR2(200)
);
CREATE INDEX idx_draft_att_draft ON email_draft_attachments(draft_id);

CREATE TABLE email_ignore_list (
  id              NUMBER(19) DEFAULT seq_email_ignore_list.NEXTVAL PRIMARY KEY,
  added_by        VARCHAR2(100),
  created_at      TIMESTAMP(6),
  email_pattern   VARCHAR2(300) NOT NULL,
  is_active       NUMBER(1) NOT NULL,
  pattern_type    VARCHAR2(20) NOT NULL,
  reason          VARCHAR2(500)
);

CREATE TABLE extraction_rule (
  id              NUMBER(19) DEFAULT seq_extraction_rule.NEXTVAL PRIMARY KEY,
  created_at      TIMESTAMP(6),
  created_by      VARCHAR2(100),
  description     VARCHAR2(500),
  extract_group   NUMBER(10),
  is_active       NUMBER(1) NOT NULL,
  pattern         VARCHAR2(2000) NOT NULL,
  pattern_type    VARCHAR2(20) NOT NULL,
  priority        NUMBER(10) NOT NULL,
  rule_code       VARCHAR2(100) NOT NULL,
  rule_name       VARCHAR2(200) NOT NULL,
  source_scope    VARCHAR2(20) NOT NULL,
  target_field    VARCHAR2(100) NOT NULL,
  transform       VARCHAR2(100),
  updated_at      TIMESTAMP(6),
  updated_by      VARCHAR2(100),
  CONSTRAINT uk_rule_code UNIQUE (rule_code)
);
CREATE INDEX idx_rule_active_priority ON extraction_rule(is_active, priority DESC);
CREATE INDEX idx_rule_target_field ON extraction_rule(target_field, is_active);

CREATE TABLE simulated_emails (
  id              NUMBER(19) DEFAULT seq_simulated_emails.NEXTVAL PRIMARY KEY,
  attachment_url  VARCHAR2(500),
  body            CLOB,
  complaint_id    NUMBER(19),
  complaint_number VARCHAR2(50),
  direction       VARCHAR2(10) NOT NULL,
  from_email      VARCHAR2(200) NOT NULL,
  message_id      VARCHAR2(100) NOT NULL,
  processed_at    TIMESTAMP(6),
  received_at     TIMESTAMP(6),
  sent_at         TIMESTAMP(6),
  status          VARCHAR2(20) NOT NULL,
  subject         VARCHAR2(500) NOT NULL,
  thread_id       VARCHAR2(100) NOT NULL,
  to_email        VARCHAR2(200) NOT NULL,
  CONSTRAINT uk_email_message_id UNIQUE (message_id)
);
CREATE INDEX idx_email_thread ON simulated_emails(thread_id);
CREATE INDEX idx_email_direction ON simulated_emails(direction);
CREATE INDEX idx_email_complaint ON simulated_emails(complaint_id);
CREATE INDEX idx_email_complaint_number ON simulated_emails(complaint_number);
CREATE INDEX idx_email_sent_at ON simulated_emails(sent_at);

CREATE TABLE appeals (
  id                          NUMBER(19) DEFAULT seq_appeals.NEXTVAL PRIMARY KEY,
  appeal_ground               CLOB,
  appeal_number               VARCHAR2(50) NOT NULL,
  appellant_email             VARCHAR2(200),
  appellant_name              VARCHAR2(200) NOT NULL,
  appellant_phone             VARCHAR2(20),
  assigned_officer            VARCHAR2(200),
  assigned_role               VARCHAR2(50),
  award_modified_amount       NUMBER(15,2),
  classification_type         VARCHAR2(20) NOT NULL,
  closed_at                   TIMESTAMP(6),
  closure_cause               VARCHAR2(50),
  created_at                  TIMESTAMP(6),
  filed_at                    TIMESTAMP(6),
  hearing_date                TIMESTAMP(6),
  hearing_venue               VARCHAR2(500),
  order_date                  TIMESTAMP(6),
  order_outcome               VARCHAR2(30),
  order_summary               CLOB,
  original_complaint_number   VARCHAR2(50) NOT NULL,
  priority                    VARCHAR2(20),
  relief_sought               CLOB,
  status                      VARCHAR2(30) NOT NULL,
  updated_at                  TIMESTAMP(6),
  workflow_stage               VARCHAR2(50),
  CONSTRAINT uk_appeal_number UNIQUE (appeal_number)
);
CREATE INDEX idx_appeal_original_complaint ON appeals(original_complaint_number);
CREATE INDEX idx_appeal_status ON appeals(status);
CREATE INDEX idx_appeal_assigned_role ON appeals(assigned_role);
CREATE INDEX idx_appeal_assigned_officer ON appeals(assigned_officer);
CREATE INDEX idx_appeal_created ON appeals(created_at);

CREATE TABLE appeal_timeline (
  id                  NUMBER(19) DEFAULT seq_appeal_timeline.NEXTVAL PRIMARY KEY,
  action              VARCHAR2(50) NOT NULL,
  appeal_number       VARCHAR2(50) NOT NULL,
  from_status         VARCHAR2(30),
  performed_at        TIMESTAMP(6),
  performed_by        VARCHAR2(200),
  performed_by_role   VARCHAR2(50),
  remarks             CLOB,
  to_status           VARCHAR2(30)
);
CREATE INDEX idx_appeal_timeline_number ON appeal_timeline(appeal_number);
CREATE INDEX idx_appeal_timeline_performed_at ON appeal_timeline(performed_at);

CREATE TABLE re_response_tracker (
  id                  NUMBER(19) DEFAULT seq_re_response_tracker.NEXTVAL PRIMARY KEY,
  breached            NUMBER(1) NOT NULL,
  complaint_id        NUMBER(19) NOT NULL,
  created_at          TIMESTAMP(6),
  ex_parte_eligible   NUMBER(1) NOT NULL,
  forwarded_at        TIMESTAMP(6) NOT NULL,
  notes               VARCHAR2(500),
  regulated_entity_id NUMBER(19) NOT NULL,
  responded_at        TIMESTAMP(6),
  updated_at          TIMESTAMP(6),
  window_days         NUMBER(10) NOT NULL,
  window_expires_at   TIMESTAMP(6),
  extension_days      NUMBER(10),
  extension_granted   NUMBER(1),
  query_raised_at     TIMESTAMP(6),
  query_text          CLOB,
  response_text       CLOB
);
CREATE INDEX idx_re_tracker_complaint ON re_response_tracker(complaint_id);
CREATE INDEX idx_re_tracker_re ON re_response_tracker(regulated_entity_id);
CREATE INDEX idx_re_tracker_breached ON re_response_tracker(breached);
CREATE INDEX idx_re_tracker_forwarded ON re_response_tracker(forwarded_at);

CREATE TABLE report_definitions (
  id                  NUMBER(19) DEFAULT seq_report_definitions.NEXTVAL PRIMARY KEY,
  chart_type          VARCHAR2(30),
  created_at          TIMESTAMP(6),
  dashboard_widget    NUMBER(1) NOT NULL,
  display_order       NUMBER(10) NOT NULL,
  owner_username      VARCHAR2(200) NOT NULL,
  query_definition    CLOB NOT NULL,
  sentence            VARCHAR2(500) NOT NULL,
  title               VARCHAR2(200),
  updated_at          TIMESTAMP(6)
);
CREATE INDEX idx_report_owner ON report_definitions(owner_username);
CREATE INDEX idx_report_type ON report_definitions(chart_type);

CREATE TABLE report_schedules (
  id                      NUMBER(19) DEFAULT seq_report_schedules.NEXTVAL PRIMARY KEY,
  active                  NUMBER(1) NOT NULL,
  created_at              TIMESTAMP(6),
  delivery_slot           VARCHAR2(10) NOT NULL,
  frequency               VARCHAR2(20) NOT NULL,
  last_sent_at            TIMESTAMP(6),
  next_scheduled_at       TIMESTAMP(6),
  owner_username          VARCHAR2(200) NOT NULL,
  recipient_email         VARCHAR2(200) NOT NULL,
  report_definition_id    NUMBER(19) NOT NULL
);
CREATE INDEX idx_schedule_slot ON report_schedules(delivery_slot);
CREATE INDEX idx_schedule_active ON report_schedules(active);
CREATE INDEX idx_schedule_owner ON report_schedules(owner_username);

-- ═══════════════════════════════════════════════════════════════════════════════
-- SEED DATA
-- ═══════════════════════════════════════════════════════════════════════════════

-- ─── Banks (12 major banks) ──────────────────────────────────────────────────
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (1, 'SBI', SYSTIMESTAMP, 'State Bank of India', 'active', 'public');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (2, 'PNB', SYSTIMESTAMP, 'Punjab National Bank', 'active', 'public');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (3, 'BOB', SYSTIMESTAMP, 'Bank of Baroda', 'active', 'public');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (4, 'CANARA', SYSTIMESTAMP, 'Canara Bank', 'active', 'public');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (5, 'UNION', SYSTIMESTAMP, 'Union Bank of India', 'active', 'public');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (6, 'HDFC', SYSTIMESTAMP, 'HDFC Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (7, 'ICICI', SYSTIMESTAMP, 'ICICI Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (8, 'AXIS', SYSTIMESTAMP, 'Axis Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (9, 'KOTAK', SYSTIMESTAMP, 'Kotak Mahindra Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (10, 'INDUSIND', SYSTIMESTAMP, 'IndusInd Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (11, 'YES', SYSTIMESTAMP, 'Yes Bank', 'active', 'private');
INSERT INTO banks (id, code, created_at, name, status, type) VALUES (12, 'IDBI', SYSTIMESTAMP, 'IDBI Bank', 'active', 'public');

-- ─── Complaint Categories (10 categories) ───────────────────────────────────
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (1, SYSTIMESTAMP, 'Issues related to ATM transactions and debit cards', 'ATM / Debit Card', NULL, 1, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (2, SYSTIMESTAMP, 'Issues related to credit card transactions and billing', 'Credit Card', NULL, 2, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (3, SYSTIMESTAMP, 'Issues with online banking services', 'Internet Banking', NULL, 3, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (4, SYSTIMESTAMP, 'Issues with mobile banking apps and UPI transactions', 'Mobile Banking / UPI', NULL, 4, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (5, SYSTIMESTAMP, 'Issues with loan processing, EMI, interest rates', 'Loan / Advances', NULL, 5, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (6, SYSTIMESTAMP, 'Issues with savings, current, or fixed deposit accounts', 'Deposit Accounts', NULL, 6, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (7, SYSTIMESTAMP, 'Pension related grievances', 'Pension', NULL, 7, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (8, SYSTIMESTAMP, 'Issues with fund transfers, NEFT, RTGS, IMPS', 'Remittance / Transfer', NULL, 8, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (9, SYSTIMESTAMP, 'Insurance related complaints', 'Insurance', NULL, 9, 'active');
INSERT INTO complaint_categories (id, created_at, description, name, parent_id, sort_order, status) VALUES (10, SYSTIMESTAMP, 'Other banking related complaints', 'Others', NULL, 10, 'active');

-- ─── Regulated Entities (145 entities - NBFCs, Cooperative Banks, PSOs, Banks) ─
-- CEPC Department - NBFCs (35)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (1, 'CEPC', 'NBFC', 'Bajaj Finance Limited', 'bajaj finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (2, 'CEPC', 'NBFC', 'Muthoot Finance Ltd', 'muthoot finance ltd', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (3, 'CEPC', 'NBFC', 'Manappuram Finance Limited', 'manappuram finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (4, 'CEPC', 'NBFC', 'Shriram Finance Limited', 'shriram finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (5, 'CEPC', 'NBFC', 'Mahindra & Mahindra Financial Services Limited', 'mahindra & mahindra financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (6, 'CEPC', 'NBFC', 'Tata Capital Financial Services Limited', 'tata capital financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (7, 'CEPC', 'NBFC', 'L&T Finance Limited', 'l&t finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (8, 'CEPC', 'NBFC', 'Piramal Capital & Housing Finance Limited', 'piramal capital & housing finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (9, 'CEPC', 'NBFC', 'HDB Financial Services Limited', 'hdb financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (10, 'CEPC', 'NBFC', 'Aditya Birla Finance Limited', 'aditya birla finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (11, 'CEPC', 'NBFC', 'Fullerton India Credit Company Limited', 'fullerton india credit company limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (12, 'CEPC', 'NBFC', 'IIFL Finance Limited', 'iifl finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (13, 'CEPC', 'NBFC', 'Hero FinCorp Limited', 'hero fincorp limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (14, 'CEPC', 'NBFC', 'Cholamandalam Investment and Finance Company Limited', 'cholamandalam investment and finance company limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (15, 'CEPC', 'NBFC', 'Sundaram Finance Limited', 'sundaram finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (16, 'CEPC', 'NBFC', 'Muthoot Fincorp Limited', 'muthoot fincorp limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (17, 'CEPC', 'NBFC', 'Ujjivan Financial Services Limited', 'ujjivan financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (18, 'CEPC', 'NBFC', 'CreditAccess Grameen Limited', 'creditaccess grameen limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (19, 'CEPC', 'NBFC', 'Arohan Financial Services Limited', 'arohan financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (20, 'CEPC', 'NBFC', 'Satin Creditcare Network Limited', 'satin creditcare network limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (21, 'CEPC', 'NBFC', 'Asirvad Microfinance Limited', 'asirvad microfinance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (22, 'CEPC', 'NBFC', 'Annapurna Finance Private Limited', 'annapurna finance private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (23, 'CEPC', 'NBFC', 'Fusion Micro Finance Limited', 'fusion micro finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (24, 'CEPC', 'NBFC', 'Northern Arc Capital Limited', 'northern arc capital limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (25, 'CEPC', 'NBFC', 'Five Star Business Finance Limited', 'five star business finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (26, 'CEPC', 'NBFC', 'Home First Finance Company India Limited', 'home first finance company india limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (27, 'CEPC', 'NBFC', 'Aptus Value Housing Finance India Limited', 'aptus value housing finance india limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (28, 'CEPC', 'NBFC', 'AAVAS Financiers Limited', 'aavas financiers limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (29, 'CEPC', 'NBFC', 'Can Fin Homes Limited', 'can fin homes limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (30, 'CEPC', 'NBFC', 'India Shelter Finance Corporation Limited', 'india shelter finance corporation limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (31, 'CEPC', 'NBFC', 'PNB Housing Finance Limited', 'pnb housing finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (32, 'CEPC', 'NBFC', 'LIC Housing Finance Limited', 'lic housing finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (33, 'CEPC', 'NBFC', 'HDFC Credila Financial Services Limited', 'hdfc credila financial services limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (34, 'CEPC', 'NBFC', 'Indiabulls Housing Finance Limited', 'indiabulls housing finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (35, 'CEPC', 'NBFC', 'Repco Home Finance Limited', 'repco home finance limited', 'active', 0);
-- CEPC Department - Cooperative Banks (24)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (36, 'CEPC', 'Cooperative Bank', 'Saraswat Co-operative Bank Limited', 'saraswat co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (37, 'CEPC', 'Cooperative Bank', 'Cosmos Co-operative Bank Limited', 'cosmos co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (38, 'CEPC', 'Cooperative Bank', 'Shamrao Vithal Co-operative Bank Limited', 'shamrao vithal co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (39, 'CEPC', 'Cooperative Bank', 'Bharat Co-operative Bank (Mumbai) Limited', 'bharat co-operative bank (mumbai) limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (40, 'CEPC', 'Cooperative Bank', 'TJSB Sahakari Bank Limited', 'tjsb sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (41, 'CEPC', 'Cooperative Bank', 'Bassein Catholic Co-operative Bank Limited', 'bassein catholic co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (42, 'CEPC', 'Cooperative Bank', 'Citizen Credit Co-operative Bank Limited', 'citizen credit co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (43, 'CEPC', 'Cooperative Bank', 'Abhyudaya Co-operative Bank Limited', 'abhyudaya co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (44, 'CEPC', 'Cooperative Bank', 'Apna Sahakari Bank Limited', 'apna sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (45, 'CEPC', 'Cooperative Bank', 'Janata Sahakari Bank Limited', 'janata sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (46, 'CEPC', 'Cooperative Bank', 'New India Co-operative Bank Limited', 'new india co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (47, 'CEPC', 'Cooperative Bank', 'Nutan Nagarik Sahakari Bank Limited', 'nutan nagarik sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (48, 'CEPC', 'Cooperative Bank', 'Kalupur Commercial Co-operative Bank Limited', 'kalupur commercial co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (49, 'CEPC', 'Cooperative Bank', 'Mehsana Urban Co-operative Bank Limited', 'mehsana urban co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (50, 'CEPC', 'Cooperative Bank', 'Rajkot Nagrik Sahakari Bank Limited', 'rajkot nagrik sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (51, 'CEPC', 'Cooperative Bank', 'Surat People''s Co-operative Bank Limited', 'surat people''s co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (52, 'CEPC', 'Cooperative Bank', 'Ahmednagar Merchants Co-operative Bank Limited', 'ahmednagar merchants co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (53, 'CEPC', 'Cooperative Bank', 'Sangli Urban Co-operative Bank Limited', 'sangli urban co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (54, 'CEPC', 'Cooperative Bank', 'Rajarambapu Sahakari Bank Limited', 'rajarambapu sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (55, 'CEPC', 'Cooperative Bank', 'Dombivli Nagari Sahakari Bank Limited', 'dombivli nagari sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (56, 'CEPC', 'Cooperative Bank', 'Thane Bharat Sahakari Bank Limited', 'thane bharat sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (57, 'CEPC', 'Cooperative Bank', 'GP Parsik Sahakari Bank Limited', 'gp parsik sahakari bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (58, 'CEPC', 'Cooperative Bank', 'Maharashtra State Co-operative Bank Limited', 'maharashtra state co-operative bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (59, 'CEPC', 'Cooperative Bank', 'The AP Mahesh Co-operative Urban Bank Limited', 'the ap mahesh co-operative urban bank limited', 'active', 0);
-- CEPC Department - Payment System Operators (13)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (60, 'CEPC', 'Payment System Operator', 'PhonePe Private Limited', 'phonepe private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (61, 'CEPC', 'Payment System Operator', 'Google Pay (Google India Digital Services)', 'google pay (google india digital services)', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (62, 'CEPC', 'Payment System Operator', 'Paytm Payments Bank Limited', 'paytm payments bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (63, 'CEPC', 'Payment System Operator', 'Amazon Pay (India) Private Limited', 'amazon pay (india) private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (64, 'CEPC', 'Payment System Operator', 'BharatPe (Resilient Innovations Private Limited)', 'bharatpe (resilient innovations private limited)', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (65, 'CEPC', 'Payment System Operator', 'MobiKwik Systems Limited', 'mobikwik systems limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (66, 'CEPC', 'Payment System Operator', 'Freecharge Payment Technologies Private Limited', 'freecharge payment technologies private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (67, 'CEPC', 'Payment System Operator', 'Razorpay Software Private Limited', 'razorpay software private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (68, 'CEPC', 'Payment System Operator', 'PayU Payments Private Limited', 'payu payments private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (69, 'CEPC', 'Payment System Operator', 'Pine Labs Private Limited', 'pine labs private limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (70, 'CEPC', 'Payment System Operator', 'Slice (Quadrillion Finance Private Limited)', 'slice (quadrillion finance private limited)', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (71, 'CEPC', 'Payment System Operator', 'Jupiter Money (Amica Financial Technologies)', 'jupiter money (amica financial technologies)', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (72, 'CEPC', 'Payment System Operator', 'CRED (Dreamplug Technologies Private Limited)', 'cred (dreamplug technologies private limited)', 'active', 0);
-- CEPC Department - Additional NBFCs (5)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (73, 'CEPC', 'NBFC', 'Navi Finserv Limited', 'navi finserv limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (74, 'CEPC', 'NBFC', 'Poonawalla Fincorp Limited', 'poonawalla fincorp limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (75, 'CEPC', 'NBFC', 'JM Financial Products Limited', 'jm financial products limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (76, 'CEPC', 'NBFC', 'Reliance Commercial Finance Limited', 'reliance commercial finance limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (77, 'CEPC', 'NBFC', 'SMFG India Credit Company Limited', 'smfg india credit company limited', 'active', 0);
-- CEPC Department - Foreign Banks (10)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (78, 'CEPC', 'Foreign Bank', 'Deutsche Bank AG', 'deutsche bank ag', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (79, 'CEPC', 'Foreign Bank', 'Standard Chartered Bank', 'standard chartered bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (80, 'CEPC', 'Foreign Bank', 'Citibank N.A.', 'citibank n.a.', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (81, 'CEPC', 'Foreign Bank', 'HSBC Limited', 'hsbc limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (82, 'CEPC', 'Foreign Bank', 'Barclays Bank PLC', 'barclays bank plc', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (83, 'CEPC', 'Foreign Bank', 'DBS Bank India Limited', 'dbs bank india limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (84, 'CEPC', 'Foreign Bank', 'Bank of America', 'bank of america', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (85, 'CEPC', 'Foreign Bank', 'JP Morgan Chase Bank N.A.', 'jp morgan chase bank n.a.', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (86, 'CEPC', 'Foreign Bank', 'BNP Paribas', 'bnp paribas', 'active', 0);
-- RBIO Department - Public Sector Banks (13)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (87, 'RBIO', 'Public Sector Bank', 'State Bank of India', 'state bank of india', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (88, 'RBIO', 'Public Sector Bank', 'Punjab National Bank', 'punjab national bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (89, 'RBIO', 'Public Sector Bank', 'Bank of Baroda', 'bank of baroda', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (90, 'RBIO', 'Public Sector Bank', 'Canara Bank', 'canara bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (91, 'RBIO', 'Public Sector Bank', 'Union Bank of India', 'union bank of india', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (92, 'RBIO', 'Public Sector Bank', 'Bank of India', 'bank of india', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (93, 'RBIO', 'Public Sector Bank', 'Indian Bank', 'indian bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (94, 'RBIO', 'Public Sector Bank', 'Central Bank of India', 'central bank of india', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (95, 'RBIO', 'Public Sector Bank', 'Indian Overseas Bank', 'indian overseas bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (96, 'RBIO', 'Public Sector Bank', 'UCO Bank', 'uco bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (97, 'RBIO', 'Public Sector Bank', 'Bank of Maharashtra', 'bank of maharashtra', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (98, 'RBIO', 'Public Sector Bank', 'Punjab & Sind Bank', 'punjab & sind bank', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (99, 'RBIO', 'Public Sector Bank', 'IDBI Bank Limited', 'idbi bank limited', 'active', 0);
-- RBIO Department - Private Sector Banks (21)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (100, 'RBIO', 'Private Sector Bank', 'HDFC Bank Limited', 'hdfc bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (101, 'RBIO', 'Private Sector Bank', 'ICICI Bank Limited', 'icici bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (102, 'RBIO', 'Private Sector Bank', 'Axis Bank Limited', 'axis bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (103, 'RBIO', 'Private Sector Bank', 'Kotak Mahindra Bank Limited', 'kotak mahindra bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (104, 'RBIO', 'Private Sector Bank', 'IndusInd Bank Limited', 'indusind bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (105, 'RBIO', 'Private Sector Bank', 'Yes Bank Limited', 'yes bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (106, 'RBIO', 'Private Sector Bank', 'IDFC First Bank Limited', 'idfc first bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (107, 'RBIO', 'Private Sector Bank', 'Bandhan Bank Limited', 'bandhan bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (108, 'RBIO', 'Private Sector Bank', 'Federal Bank Limited', 'federal bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (109, 'RBIO', 'Private Sector Bank', 'RBL Bank Limited', 'rbl bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (110, 'RBIO', 'Private Sector Bank', 'South Indian Bank Limited', 'south indian bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (111, 'RBIO', 'Private Sector Bank', 'City Union Bank Limited', 'city union bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (112, 'RBIO', 'Private Sector Bank', 'Karur Vysya Bank Limited', 'karur vysya bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (113, 'RBIO', 'Private Sector Bank', 'Tamilnad Mercantile Bank Limited', 'tamilnad mercantile bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (114, 'RBIO', 'Private Sector Bank', 'DCB Bank Limited', 'dcb bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (115, 'RBIO', 'Private Sector Bank', 'Dhanlaxmi Bank Limited', 'dhanlaxmi bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (116, 'RBIO', 'Private Sector Bank', 'CSB Bank Limited', 'csb bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (117, 'RBIO', 'Private Sector Bank', 'Nainital Bank Limited', 'nainital bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (118, 'RBIO', 'Private Sector Bank', 'Jammu & Kashmir Bank Limited', 'jammu & kashmir bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (119, 'RBIO', 'Private Sector Bank', 'Karnataka Bank Limited', 'karnataka bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (120, 'RBIO', 'Private Sector Bank', 'Lakshmi Vilas Bank Limited', 'lakshmi vilas bank limited', 'active', 0);
-- RBIO Department - Small Finance Banks (11)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (121, 'RBIO', 'Small Finance Bank', 'Au Small Finance Bank Limited', 'au small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (122, 'RBIO', 'Small Finance Bank', 'Equitas Small Finance Bank Limited', 'equitas small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (123, 'RBIO', 'Small Finance Bank', 'Ujjivan Small Finance Bank Limited', 'ujjivan small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (124, 'RBIO', 'Small Finance Bank', 'Jana Small Finance Bank Limited', 'jana small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (125, 'RBIO', 'Small Finance Bank', 'Suryoday Small Finance Bank Limited', 'suryoday small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (126, 'RBIO', 'Small Finance Bank', 'Fincare Small Finance Bank Limited', 'fincare small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (127, 'RBIO', 'Small Finance Bank', 'ESAF Small Finance Bank Limited', 'esaf small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (128, 'RBIO', 'Small Finance Bank', 'North East Small Finance Bank Limited', 'north east small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (129, 'RBIO', 'Small Finance Bank', 'Capital Small Finance Bank Limited', 'capital small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (130, 'RBIO', 'Small Finance Bank', 'Unity Small Finance Bank Limited', 'unity small finance bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (131, 'RBIO', 'Small Finance Bank', 'Shivalik Small Finance Bank Limited', 'shivalik small finance bank limited', 'active', 0);
-- RBIO Department - Payments Banks (5)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (132, 'RBIO', 'Payments Bank', 'Airtel Payments Bank Limited', 'airtel payments bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (133, 'RBIO', 'Payments Bank', 'India Post Payments Bank Limited', 'india post payments bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (134, 'RBIO', 'Payments Bank', 'Fino Payments Bank Limited', 'fino payments bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (135, 'RBIO', 'Payments Bank', 'Jio Payments Bank Limited', 'jio payments bank limited', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (136, 'RBIO', 'Payments Bank', 'NSDL Payments Bank Limited', 'nsdl payments bank limited', 'active', 0);
-- RBIO Department - Payment Infrastructure (2)
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (137, 'RBIO', 'Payment Infrastructure', 'National Payments Corporation of India', 'national payments corporation of india', 'active', 0);
INSERT INTO regulated_entities (id, department, entity_type, name, name_normalized, status, portal_enabled) VALUES (138, 'RBIO', 'Payment Infrastructure', 'Clearing Corporation of India Limited', 'clearing corporation of india limited', 'active', 0);

-- ─── Holidays (India 2026 - National + RBI holidays) ─────────────────────────
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-01-26', 'Republic Day', 1, 'NATIONAL', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-03-10', 'Maha Shivaratri', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-03-14', 'Holi', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-03-31', 'Id-ul-Fitr', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-04-02', 'Ram Navami', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-04-03', 'Good Friday', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-04-14', 'Dr. Ambedkar Jayanti', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-04-21', 'Mahavir Jayanti', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-05-01', 'May Day', 0, 'RBI', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-05-12', 'Buddha Purnima', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-06-07', 'Eid ul-Adha (Bakrid)', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-07-06', 'Muharram', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-08-15', 'Independence Day', 1, 'NATIONAL', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-08-16', 'Janmashtami', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-09-04', 'Milad-un-Nabi', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-10-02', 'Mahatma Gandhi Jayanti', 1, 'NATIONAL', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-10-02', 'Dussehra', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-10-21', 'Diwali (Lakshmi Puja)', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-10-22', 'Diwali (Govardhan Puja)', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-11-04', 'Guru Nanak Jayanti', 1, 'GAZETTED', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-11-15', 'Guru Tegh Bahadur Martyrdom', 0, 'RBI', 2026);
INSERT INTO holidays (id, holiday_date, name, is_national, type, year) VALUES (seq_holidays.NEXTVAL, DATE '2026-12-25', 'Christmas', 1, 'GAZETTED', 2026);

-- ─── Extraction Rules ────────────────────────────────────────────────────────
INSERT INTO extraction_rule (id, created_at, created_by, description, extract_group, is_active, pattern, pattern_type, priority, rule_code, rule_name, source_scope, target_field, transform) VALUES (1, SYSTIMESTAMP, 'admin', 'Extracts 6-digit Indian PIN code only when near address/pin keywords', 1, 1, '(?:pin\s*(?:code)?|PIN|pincode|postal|zip|address)[:\s-]*([1-8]\d{5})\b', 'REGEX', 2, 'extract_pincode', 'Extract Pincode', 'BOTH', 'complainantPincode', NULL);
INSERT INTO extraction_rule (id, created_at, created_by, description, extract_group, is_active, pattern, pattern_type, priority, rule_code, rule_name, source_scope, target_field, transform) VALUES (2, SYSTIMESTAMP, 'admin', 'Fallback: matches any 6-digit number starting with 1-8', 1, 1, '\b([1-8]\d{5})\b', 'REGEX', 1, 'extract_pincode_fallback', 'Extract Pincode (fallback)', 'BOTH', 'complainantPincode', NULL);
INSERT INTO extraction_rule (id, created_at, created_by, description, extract_group, is_active, pattern, pattern_type, priority, rule_code, rule_name, source_scope, target_field, transform) VALUES (3, SYSTIMESTAMP, 'admin', 'Detects bank name from common abbreviations in email text', 0, 1, 'Punjab National Bank,PNB,State Bank of India,SBI,ICICI Bank,HDFC Bank,Axis Bank,Bank of Baroda,Canara Bank,Union Bank of India,Bank of Maharashtra,Indian Bank,Federal Bank', 'KEYWORD_LIST', 1, 'entity_name_bank_keywords', 'Entity Name - Bank Keywords', 'BOTH', 'entityName', 'UPPERCASE');
INSERT INTO extraction_rule (id, created_at, created_by, description, extract_group, is_active, pattern, pattern_type, priority, rule_code, rule_name, source_scope, target_field, transform) VALUES (4, SYSTIMESTAMP, 'admin', 'Extracts branch name from Branch: pattern', 1, 1, '(?:Branch|branch)[: ]*([A-Za-z ,.''"-]+?)(?:\n|$|Phone|Name|Bank)', 'REGEX', 1, 'branch_name_regex', 'Branch Name Extraction', 'BODY', 'branchName', 'TRIM');

-- ─── Email Ignore List ───────────────────────────────────────────────────────
INSERT INTO email_ignore_list (id, added_by, created_at, email_pattern, is_active, pattern_type, reason) VALUES (1, 'admin', SYSTIMESTAMP, 'spam@test.com', 1, 'EXACT', 'spam sender');

-- ─── Indian States & UTs ─────────────────────────────────────────────────────
-- States and districts are served from LocationController.java (hardcoded map).
-- Pincode lookups use external API (api.postalpincode.in). No DB table required.
-- 36 States/UTs served: AP, AR, AS, BR, CG, GA, GJ, HR, HP, JH, KA, KL, MP, MH,
--   MN, ML, MZ, NL, OD, PB, RJ, SK, TN, TG, TR, UP, UK, WB, DL, CH, JK, LA, PY,
--   AN, DN, LD

-- ─── Form Configs ───────────────────────────────────────────────────────────
INSERT INTO form_configs (id, form_key, form_name, schema_json, active, version, created_at, updated_at)
VALUES (seq_form_configs.NEXTVAL, 'raise-complaint', 'Raise a Complaint Form', '{"steps":[{"id":"step1","title":"complaint.step1_title","fields":[{"name":"complainantName","type":"text","label":"complaint.name","required":true,"maxLength":100},{"name":"complainantEmail","type":"email","label":"complaint.email","required":false},{"name":"complainantPhone","type":"tel","label":"login.mobile_label","required":true,"pattern":"^[6-9]\\d{9}$"},{"name":"complainantPincode","type":"text","label":"complaint.pincode","required":true,"pattern":"^[1-8]\\d{5}$"},{"name":"complainantState","type":"select","label":"complaint.state","required":true,"optionsSource":"api:/api/v1/location/states"},{"name":"complainantDistrict","type":"select","label":"complaint.district","required":true,"optionsSource":"api:/api/v1/location/districts?state={complainantState}"},{"name":"complainantAddress","type":"textarea","label":"complaint.address","required":true,"maxLength":500}]},{"id":"step2","title":"complaint.step2_title","fields":[{"name":"entityType","type":"select","label":"complaint.entity_type","required":true,"options":["Commercial Bank","NBFC","Cooperative Bank","Payment System Operator","Foreign Bank","Small Finance Bank","Payments Bank","Credit Information Company"]},{"name":"entityName","type":"autocomplete","label":"complaint.entity_name","required":true,"optionsSource":"api:/api/v1/regulated-entities?type={entityType}"},{"name":"branchName","type":"text","label":"complaint.branch","required":false},{"name":"accountNumber","type":"text","label":"complaint.account_number","required":false}]},{"id":"step3","title":"complaint.step3_title","fields":[{"name":"categoryId","type":"select","label":"complaint.category","required":true,"optionsSource":"api:/api/v1/categories"},{"name":"description","type":"textarea","label":"complaint.facts","required":true,"minLength":50,"maxLength":5000},{"name":"attachments","type":"file","label":"complaint.attachments","required":false,"accept":".pdf,.jpg,.jpeg,.png","maxSize":5242880,"maxFiles":5}]}]}', 1, '1.0', SYSTIMESTAMP, SYSTIMESTAMP);

-- ─── Reset sequences to max+1 ───────────────────────────────────────────────
BEGIN
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_banks RESTART START WITH 13';
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_complaint_categories RESTART START WITH 11';
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_regulated_entities RESTART START WITH 146';
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_extraction_rule RESTART START WITH 5';
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_email_ignore_list RESTART START WITH 2';
  EXECUTE IMMEDIATE 'ALTER SEQUENCE seq_form_configs RESTART START WITH 2';
END;
/

COMMIT;

-- ═══════════════════════════════════════════════════════════════════════════════
-- TRANSLATION DATA (118 keys x 10 locales)
-- Run separately: @oracle-translations-seed.sql
-- ═══════════════════════════════════════════════════════════════════════════════
-- Translation keys and locale values are in a separate file.
-- Execute after this script:  @oracle-translations-seed.sql
