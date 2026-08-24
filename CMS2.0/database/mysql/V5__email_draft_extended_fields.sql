-- V5: Add extended fields to EMAIL_DRAFTS for physical-letter workflow
-- Matches EmailDraft.java entity with Spring CamelCaseToUnderscoresNamingStrategy
-- Run against cms_db MySQL database

ALTER TABLE email_drafts

  -- ═══ Eligibility & Proposed Action ═══
  ADD COLUMN proposed_complaint_type VARCHAR(50) NULL,
  ADD COLUMN not_complaint_reason VARCHAR(200) NULL,
  ADD COLUMN eligibility_questions_json TEXT NULL,

  -- ═══ Entity Details (expanded) ═══
  ADD COLUMN entity_category VARCHAR(100) NULL,
  ADD COLUMN entity_type_detail VARCHAR(100) NULL,
  ADD COLUMN entity_bsr_code VARCHAR(50) NULL,
  ADD COLUMN entity_pincode VARCHAR(10) NULL,
  ADD COLUMN entity_country VARCHAR(100) NULL,
  ADD COLUMN entity_state VARCHAR(100) NULL,
  ADD COLUMN entity_district VARCHAR(100) NULL,
  ADD COLUMN entity_city VARCHAR(100) NULL,
  ADD COLUMN entity_branch_name VARCHAR(200) NULL,
  ADD COLUMN entity_branch_category VARCHAR(100) NULL,
  ADD COLUMN entity_address VARCHAR(500) NULL,
  ADD COLUMN entity_branch_center_name VARCHAR(200) NULL,
  ADD COLUMN cosmos_code VARCHAR(50) NULL,
  ADD COLUMN asset_size VARCHAR(50) NULL,
  ADD COLUMN is_deposit_taking BIT(1) NULL,
  ADD COLUMN is_asset_above100_cr BIT(1) NULL,
  ADD COLUMN is_liquidated BIT(1) NULL,

  -- ═══ Complainant Extended — Basic Identification ═══
  ADD COLUMN other_entity_name VARCHAR(200) NULL,
  ADD COLUMN date_of_registration_with_rbi VARCHAR(30) NULL,

  -- ═══ Complainant Extended — Complaint Classification ═══
  ADD COLUMN complaint_category VARCHAR(100) NULL,
  ADD COLUMN complaint_sub_category1 VARCHAR(100) NULL,
  ADD COLUMN complaint_sub_category2 VARCHAR(100) NULL,
  ADD COLUMN date_of_filing_complaint VARCHAR(30) NULL,
  ADD COLUMN complaint_reg_date_valid VARCHAR(10) NULL,

  -- ═══ Complainant Extended — Reminder & Financial Details ═══
  ADD COLUMN reminder_sent_by_complainant VARCHAR(10) NULL,
  ADD COLUMN disputed_amount_involved VARCHAR(50) NULL,
  ADD COLUMN date_of_filing_for_financial VARCHAR(30) NULL,
  ADD COLUMN compensation_sought VARCHAR(50) NULL,
  ADD COLUMN loan_disposal_amount VARCHAR(50) NULL,

  -- ═══ Complainant Extended — Additional Information ═══
  ADD COLUMN additional_comments TEXT NULL,
  ADD COLUMN crpc_proposed_action VARCHAR(100) NULL,
  ADD COLUMN vernacular_language_detail VARCHAR(200) NULL,

  -- ═══ Legal & Case Details ═══
  ADD COLUMN legal_case_filed VARCHAR(10) NULL,
  ADD COLUMN legal_date_of_filing VARCHAR(30) NULL,
  ADD COLUMN pre_enquiry_received VARCHAR(10) NULL,

  -- ═══ Flags & Indicators ═══
  ADD COLUMN high_priority_complaint VARCHAR(10) NULL,
  ADD COLUMN is_regarding_pension VARCHAR(10) NULL,
  ADD COLUMN is_against_business_correspondent VARCHAR(10) NULL,
  ADD COLUMN is_atm_credit_debit_card VARCHAR(10) NULL,
  ADD COLUMN scheme_flag VARCHAR(10) NULL,
  ADD COLUMN is_free_marked_complaint VARCHAR(10) NULL,

  -- ═══ Complaint Linkage ═══
  ADD COLUMN current_complaint_number VARCHAR(100) NULL,
  ADD COLUMN received_reply_within30days VARCHAR(20) NULL,

  -- ═══ Declaration ═══
  ADD COLUMN declaration_accepted BIT(1) NULL;
