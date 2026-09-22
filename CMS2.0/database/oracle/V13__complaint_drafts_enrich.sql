-- V13: Enrich COMPLAINT_DRAFTS with full wizard state for server-side draft retention
-- Adds columns to store: stepper raw form values, eligibility step, highest step reached,
-- checked account types, date display map, declaration state, and attachment metadata.

ALTER TABLE COMPLAINT_DRAFTS ADD (
    HIGHEST_STEP_REACHED    NUMBER(10),
    ELIGIBILITY_STEP        NUMBER(10),
    CHECKED_ACCOUNT_TYPES   VARCHAR2(500),
    DATE_DISPLAY_JSON       VARCHAR2(1000),
    DECLARATION_CHECKED     NUMBER(1) DEFAULT 0,
    DECLARATION2_CHECKED    NUMBER(1) DEFAULT 0,
    ATTACHMENT_META_JSON            CLOB
);

COMMENT ON COLUMN COMPLAINT_DRAFTS.HIGHEST_STEP_REACHED    IS 'Highest wizard step the user has visited';
COMMENT ON COLUMN COMPLAINT_DRAFTS.ELIGIBILITY_STEP        IS 'Current eligibility questionnaire step';
COMMENT ON COLUMN COMPLAINT_DRAFTS.CHECKED_ACCOUNT_TYPES   IS 'Comma-separated account type values (savings,loan,...)';
COMMENT ON COLUMN COMPLAINT_DRAFTS.DATE_DISPLAY_JSON       IS 'JSON map of date display strings {bankComplaintDate,reminderDate,replyDate}';
COMMENT ON COLUMN COMPLAINT_DRAFTS.DECLARATION_CHECKED     IS '1 if declaration (i) checked';
COMMENT ON COLUMN COMPLAINT_DRAFTS.DECLARATION2_CHECKED    IS '1 if declaration (ii) checked';
COMMENT ON COLUMN COMPLAINT_DRAFTS.ATTACHMENT_META_JSON    IS 'JSON array of attachment metadata [{name,type,size}]';
