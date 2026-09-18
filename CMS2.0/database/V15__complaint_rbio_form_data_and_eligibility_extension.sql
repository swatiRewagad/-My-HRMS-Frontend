-- V15: RBIO officer summary - editable form data + clause-10 assessment columns (MySQL)
--
-- Backs GET/PUT /api/complaints/rbio/{id}/summary. The summary groups everything the complainant
-- submitted into sections, but ~30 of its fields were never captured on COMPLAINTS: they either
-- only ever existed on EMAIL_DRAFTS (and were dropped when a CRPC draft was converted) or were
-- never collected at all. COMPLAINT_RBIO_FORM_DATA gives them a home the officer can edit.
--
-- The COMPLAINT_ELIGIBILITY_ANSWERS columns are the clause-10 maintainability questions the
-- officer answers. They are deliberately NOT folded onto the existing yes/no columns:
-- ALREADY_SETTLED means the RE settled the grievance (not a court), and STAFF_OF_RE is a
-- different question from "complaint against management".
--
-- Also fixes a latent bug: COMPLAINT_ID was non-UNIQUE here while the sibling tables from V14
-- (COMPLAINT_ADDITIONAL_DETAILS, COMPLAINT_REPRESENTATIVES) are UNIQUE, yet
-- ComplaintEligibilityAnswerRepository.findByComplaintId returns Optional<>. A second row for one
-- complaint makes every read of that complaint throw IncorrectResultSizeDataAccessException.
-- The DELETE below keeps the latest ANSWERED_AT per complaint; run it before the ALTER.
--
-- Oracle equivalent: database/oracle/V15__complaint_rbio_form_data_and_eligibility_extension.sql
-- Not idempotent: re-running fails with "table already exists" / "duplicate column name".
-- On dev-local, ddl-auto=update may have already applied all of this.

-- Dedupe before the unique constraint can be added.
DELETE cea FROM COMPLAINT_ELIGIBILITY_ANSWERS cea
  JOIN COMPLAINT_ELIGIBILITY_ANSWERS keep
    ON keep.complaint_id = cea.complaint_id
   AND (keep.answered_at > cea.answered_at
        OR (keep.answered_at = cea.answered_at AND keep.id > cea.id));

ALTER TABLE COMPLAINT_ELIGIBILITY_ANSWERS
    ADD CONSTRAINT uk_cea_complaint UNIQUE (complaint_id);

ALTER TABLE COMPLAINT_ELIGIBILITY_ANSWERS
    ADD COLUMN entity_regulated_by_rbi             VARCHAR(10)  NULL,
    ADD COLUMN not_directly_addressed_to_ombudsman VARCHAR(10)  NULL,
    ADD COLUMN not_registered_with_entity          VARCHAR(10)  NULL,
    ADD COLUMN frivolous_vexatious_threatening     VARCHAR(10)  NULL,
    ADD COLUMN pending_before_court                VARCHAR(10)  NULL,
    ADD COLUMN settled_before_court                VARCHAR(10)  NULL,
    ADD COLUMN complainant_is_advocate             VARCHAR(10)  NULL,
    ADD COLUMN complaint_against_management        VARCHAR(10)  NULL,
    ADD COLUMN filed_with_cepc_or_rbi              VARCHAR(10)  NULL,
    ADD COLUMN dispute_between_res                 VARCHAR(10)  NULL,
    ADD COLUMN complete_information_unavailable    VARCHAR(10)  NULL,
    ADD COLUMN proposed_complaint_type             VARCHAR(100) NULL,
    ADD COLUMN first_filed_with_re_date            DATE         NULL;

CREATE TABLE COMPLAINT_RBIO_FORM_DATA (
    id                                BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_id                      BIGINT        NOT NULL UNIQUE,
    draft_id                          VARCHAR(50)   NULL,
    receipt_date                      DATE          NULL,
    mode_of_receipt                   VARCHAR(50)   NULL,
    comments                          VARCHAR(4000) NULL,
    complaint_cpgram                  VARCHAR(10)   NULL,
    cpgram_number                     VARCHAR(100)  NULL,
    module_name                       VARCHAR(100)  NULL,
    entity_country                    VARCHAR(100)  NULL,
    branch_center_name                VARCHAR(200)  NULL,
    other_entity_name                 VARCHAR(300)  NULL,
    registration_with_rbi_date        DATE          NULL,
    complaint_registration_date_valid VARCHAR(10)   NULL,
    date_of_filing_complaint          DATE          NULL,
    legal_case_filed                  VARCHAR(10)   NULL,
    legal_filing_date                 DATE          NULL,
    pre_enquiry_received              VARCHAR(10)   NULL,
    high_priority_complaint           VARCHAR(10)   NULL,
    loan_disposal_amount              DECIMAL(15,2) NULL,
    vernacular_language               VARCHAR(100)  NULL,
    complaint_regarding_pension       VARCHAR(10)   NULL,
    atm_credit_debit_card             VARCHAR(10)   NULL,
    scheme_flag                       VARCHAR(50)   NULL,
    rbo_cgpc_old                      VARCHAR(100)  NULL,
    grounds_flag                      VARCHAR(100)  NULL,
    free_marked_complaint             VARCHAR(10)   NULL,
    created_at                        DATETIME(6)   NOT NULL,
    updated_at                        DATETIME(6)   NULL
);

CREATE INDEX idx_crfd_draft ON COMPLAINT_RBIO_FORM_DATA (draft_id);
