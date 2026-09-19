-- ============================================================
-- CMS 2.0 — Full RBIO Complaint Lifecycle Seed Data
-- Target: H2 / MySQL (dev-local profile)
-- Scenario: Physical letter complaint against SBI, assigned
--           to RBIO Dealing Officer, MAINTAINABLE determination
-- ============================================================
-- Run order: after V1-V20 migrations and V2 seed data (BANKS,
--            COMPLAINT_CATEGORIES must already exist).
-- Uses explicit IDs (1001+) to avoid collisions with existing
-- auto-generated rows.
-- ============================================================

-- ============================================================
-- 1. REGULATED_ENTITIES (FK target for RE_RESPONSE_TRACKER)
-- ============================================================
INSERT INTO REGULATED_ENTITIES (
    ID, NAME, NAME_NORMALIZED, DEPARTMENT, ENTITY_TYPE, CITY, STATE, STATUS,
    NODAL_OFFICER_NAME, NODAL_OFFICER_EMAIL, NODAL_OFFICER_PHONE, NODAL_OFFICER_DESIGNATION,
    PNO_NAME, PNO_EMAIL, PNO_PHONE,
    REGISTRATION_DATE, LAST_LOGIN_AT, PORTAL_ENABLED, CREATED_AT
) VALUES (
    1001, 'State Bank of India - Nagpur Main Branch', 'state bank of india - nagpur main branch',
    'RBIO', 'PUBLIC_SECTOR', 'Nagpur', 'Maharashtra', 'active',
    'Suresh Patil', 'suresh.patil@sbi.co.in', '9876543210', 'Chief Manager',
    'Anita Deshmukh', 'anita.deshmukh@sbi.co.in', '9876543211',
    TIMESTAMP '2024-01-15 10:00:00', TIMESTAMP '2026-09-18 14:30:00', 1, CURRENT_TIMESTAMP
);

-- ============================================================
-- 2. COMPLAINT_NUMBER_SEQUENCE
-- ============================================================
INSERT INTO COMPLAINT_NUMBER_SEQUENCE (
    ID, OFFICE_CODE, FINANCIAL_YEAR, LAST_SEQUENCE, UPDATED_AT
) VALUES (
    1001, 'RBIO-NGP', '202627', 42, CURRENT_TIMESTAMP
);

-- ============================================================
-- 3. COMPLAINTS (main table — all ~98 columns populated)
-- ============================================================
INSERT INTO COMPLAINTS (
    ID, COMPLAINT_NUMBER, ORIGIN_DRAFT_ID,
    COMPLAINANT_NAME, COMPLAINANT_EMAIL, COMPLAINANT_PHONE, COMPLAINANT_ADDRESS,
    COMPLAINANT_STATE, COMPLAINANT_DISTRICT, COMPLAINANT_PINCODE,
    BANK_ID, REGULATED_ENTITY_ID,
    ENTITY_NAME, ENTITY_TYPE, AMOUNT_INVOLVED,
    ENTITY_CATEGORY, ENTITY_BSR_CODE, ENTITY_PINCODE, ENTITY_STATE, ENTITY_DISTRICT,
    ENTITY_CITY, ENTITY_BRANCH_NAME, ENTITY_BRANCH_CATEGORY, ENTITY_ADDRESS, COSMOS_CODE,
    BANK_BRANCH, ACCOUNT_NUMBER, CATEGORY_ID, CATEGORY_NAME,
    SUBJECT, DESCRIPTION, RELIEF_SOUGHT,
    STATUS, PRIORITY, FILING_TYPE,
    BANK_COMPLAINT_REFERENCE, BANK_COMPLAINT_DATE,
    ASSIGNED_OFFICER, ASSIGNED_OFFICER_NAME, DEPARTMENT, ASSIGNED_ROLE,
    ENTITY_CODE, WORKFLOW_STAGE,
    PRIOR_RE_COMPLAINT, RE_COMPLAINT_DATE, RE_COMPLAINT_REFERENCE, RE_REPLIED_AND_DISSATISFIED,
    TRIAGE_SIGNAL, TRIAGE_FLAGS, ELIGIBILITY_TIMELINE,
    MAINTAINABILITY_DETERMINATION, MAINTAINABILITY_DETERMINED_BY, MAINTAINABILITY_DETERMINED_AT,
    AWARD_AMOUNT,
    SLA_DEADLINE, SLA_PRIORITY,
    CONCILIATION_DATE, CONCILIATION_OUTCOME,
    ADJUDICATION_DATE, ADJUDICATION_OUTCOME,
    CLOSURE_CAUSE, CUSTOM_CLOSURE_TEXT, CLOSURE_LETTER_SENT_AT,
    CLOSURE_CLAUSE, CLOSURE_CLAUSE_DESCRIPTION,
    CLOSURE_AUTHORITY_NAME, CLOSURE_AUTHORITY_DESIGNATION,
    PROPOSED_ACTION, PROPOSED_CLAUSE,
    FORWARDED_OFFICE_CODE, PRE_FORWARD_OFFICER, PRE_FORWARD_ROLE,
    COMPLAINT_STATUS_ON_PORTAL, SPEAKING_ORDER_GENERATED,
    GIST_OF_CASE, GIST_OF_CASE_REGIONAL,
    REOPEN_COUNT, LAST_REOPENED_AT,
    ADVISORY_TEXT, ADVISORY_ISSUED_AT, NOTICE_13_1_ISSUED_AT,
    IMPLEADED_PARTIES, COMPENSATION_TYPE, SCHEME_VERSION,
    CURRENT_STAGE_DEADLINE, STAGE_ASSIGNED_AT,
    RE_RESPONSE_DEADLINE, LAST_STATUS_CHANGE_DATE,
    REGIONAL_OFFICE, IS_READ, HAS_ATTACHMENT, CREATED_BY,
    FILED_AT, RESOLVED_AT, CLOSED_AT, ESCALATED_AT,
    CREATED_AT, UPDATED_AT
) VALUES (
    1001, 'RBIO-NGP/2627/000042', '100042',
    'Rajesh Kumar', 'rajesh.kumar@gmail.com', '9988776655', '45, MG Road, Sadar, Nagpur',
    'Maharashtra', 'Nagpur', '440001',
    1, 1001,
    'State Bank of India', 'PUBLIC_SECTOR', 125000.00,
    'Scheduled Commercial Bank', '0440012', '440001', 'Maharashtra', 'Nagpur',
    'Nagpur', 'Nagpur Main Branch', 'METRO', '1, Civil Lines, Nagpur 440001', 'SBI-NGP-001',
    'Nagpur Main Branch', '20012345678', 1, 'ATM/Debit Card',
    'ATM cash not dispensed but account debited Rs 1,25,000',
    'On 10-Sep-2026, I attempted to withdraw Rs 1,25,000 from SBI ATM at MG Road, Nagpur (ATM ID: SBI-NGP-0042). The transaction was initiated but cash was not dispensed. However, my savings account 20012345678 was debited with the full amount. I immediately contacted the branch and lodged a complaint (Ref: SBI-NGP-ATM-2026-0918) but have not received any resolution despite 30 days having passed. I request immediate reversal of the debited amount along with interest for the delay period.',
    'Immediate reversal of Rs 1,25,000 debited from my savings account along with applicable interest and compensation for mental agony.',
    'REGISTERED', 'HIGH', 'PHYSICAL_LETTER',
    'SBI-NGP-ATM-2026-0918', TIMESTAMP '2026-09-10 00:00:00',
    'rbio_do1', 'Amit Sharma', 'RBIO', 'RBIO_DO',
    'SBI-440001', 'ASSESSMENT',
    1, DATE '2026-09-10', 'SBI-NGP-ATM-2026-0918', 1,
    'GREEN', '{"autoTriageScore":85,"keywords":["atm","cash not dispensed","debit"]}',
    '{"step1":"Filed with RE on 2026-09-10","step2":"Reply received on 2026-09-12","step3":"Dissatisfied, filed with RBIO on 2026-09-15"}',
    'MAINTAINABLE', 'rbio_do1', TIMESTAMP '2026-09-16 10:30:00',
    NULL,
    TIMESTAMP '2026-10-15 23:59:59', 'HIGH',
    NULL, NULL,
    NULL, NULL,
    NULL, NULL, NULL,
    NULL, NULL,
    NULL, NULL,
    'MAINTAINABLE', '16(2)(a)',
    NULL, NULL, NULL,
    'Under Process', NULL,
    'Complainant approached ATM on 10-Sep-2026. Cash not dispensed but account debited Rs 1,25,000. RE acknowledged but failed to reverse within 30 days.',
    NULL,
    0, NULL,
    NULL, NULL, NULL,
    NULL, 'MONETARY', 'RB-IOS-2026',
    TIMESTAMP '2026-10-01 23:59:59', TIMESTAMP '2026-09-16 10:30:00',
    DATE '2026-10-15', TIMESTAMP '2026-09-16 10:30:00',
    'RBIO-NGP', 0, 1, 'crpc_deo1',
    TIMESTAMP '2026-09-15 09:00:00', NULL, NULL, NULL,
    TIMESTAMP '2026-09-15 09:00:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 4. COMPLAINT_TIMELINE (3 state transitions)
-- ============================================================
INSERT INTO COMPLAINT_TIMELINE (ID, COMPLAINT_ID, ACTION, PERFORMED_BY, REMARKS, FROM_STATUS, TO_STATUS, PERFORMED_AT) VALUES
(1001, 1001, 'CREATE', 'crpc_deo1', 'Physical letter complaint received and registered via CRPC', NULL, 'NEW_COMPLAINT', TIMESTAMP '2026-09-15 09:00:00');

INSERT INTO COMPLAINT_TIMELINE (ID, COMPLAINT_ID, ACTION, PERFORMED_BY, REMARKS, FROM_STATUS, TO_STATUS, PERFORMED_AT) VALUES
(1002, 1001, 'ASSIGN', 'crpc_deo1', 'Auto-assigned to RBIO Dealing Officer Amit Sharma via round-robin', 'NEW_COMPLAINT', 'ASSIGNED', TIMESTAMP '2026-09-15 09:05:00');

INSERT INTO COMPLAINT_TIMELINE (ID, COMPLAINT_ID, ACTION, PERFORMED_BY, REMARKS, FROM_STATUS, TO_STATUS, PERFORMED_AT) VALUES
(1003, 1001, 'REGISTER', 'rbio_do1', 'Complaint registered after eligibility assessment. Determined MAINTAINABLE under clause 16(2)(a).', 'ASSIGNED', 'REGISTERED', TIMESTAMP '2026-09-16 10:30:00');

-- ============================================================
-- 5. COMPLAINT_ATTACHMENTS (scanned letter)
-- ============================================================
INSERT INTO COMPLAINT_ATTACHMENTS (
    ID, COMPLAINT_ID, FILE_NAME, ORIGINAL_NAME, CONTENT_TYPE, FILE_SIZE, STORAGE_PATH, UPLOADED_AT
) VALUES (
    1001, 1001,
    'a3f5b8c2-9d4e-4f1a-b7e6-8c2d1e0f3a5b.pdf',
    'Rajesh_Kumar_Complaint_Letter.pdf',
    'application/pdf', 524288,
    '/storage/complaints/1001/a3f5b8c2-9d4e-4f1a-b7e6-8c2d1e0f3a5b.pdf',
    TIMESTAMP '2026-09-15 09:00:00'
);

-- ============================================================
-- 6. COMPLAINT_ELIGIBILITY_ANSWERS (clause-10 assessment)
-- ============================================================
INSERT INTO COMPLAINT_ELIGIBILITY_ANSWERS (
    ID, COMPLAINT_ID, DRAFT_ID, REGULATED_ENTITY_ID,
    FILED_WITH_RE, RECEIVED_REPLY, SENT_REMINDER,
    IS_SUB_JUDICE, ALREADY_SETTLED, THROUGH_ADVOCATE,
    PENDING_BEFORE_OMBUDSMAN, SETTLED_BY_OMBUDSMAN,
    STAFF_OF_RE, PREVIOUSLY_FILED_WITH_CEPC,
    EMPLOYEE_OF_RE, EMPLOYER_RELATIONSHIP,
    ENTITY_REGULATED_BY_RBI,
    NOT_DIRECTLY_ADDRESSED_TO_OMBUDSMAN,
    NOT_REGISTERED_WITH_ENTITY,
    FRIVOLOUS_VEXATIOUS_THREATENING,
    PENDING_BEFORE_COURT,
    SETTLED_BEFORE_COURT,
    COMPLAINANT_IS_ADVOCATE,
    COMPLAINT_AGAINST_MANAGEMENT,
    FILED_WITH_CEPC_OR_RBI,
    DISPUTE_BETWEEN_RES,
    COMPLETE_INFORMATION_UNAVAILABLE,
    PROPOSED_COMPLAINT_TYPE,
    FIRST_FILED_WITH_RE_DATE,
    ANSWERED_AT
) VALUES (
    1001, 1001, '100042', 1001,
    'yes', 'yes', 'yes',
    'no', 'no', 'no',
    'no', 'no',
    'no', 'no',
    'no', 'no',
    'true',
    'false',
    'false',
    'false',
    'false',
    'false',
    'false',
    'false',
    'false',
    'false',
    'false',
    'MAINTAINABLE',
    DATE '2026-09-10',
    TIMESTAMP '2026-09-16 10:25:00'
);

-- ============================================================
-- 7. COMPLAINT_ADDITIONAL_DETAILS
-- ============================================================
INSERT INTO COMPLAINT_ADDITIONAL_DETAILS (
    ID, COMPLAINT_ID, DRAFT_ID,
    AGE, GENDER, COMPLAINANT_CATEGORY, IS_COMPLAINANT_SELF,
    ORGANIZATION_NAME, ORG_LANDLINE,
    HAS_ACCOUNT_WITH_RE, ACCOUNT_TYPE,
    SAVINGS_ACCOUNT_NUMBER, ATM_DEBIT_CARD_NUMBER,
    CARD_NUMBER, CREDIT_CARD_NUMBER, IS_CREDIT_CARD_COMPLAINT,
    LOAN_ACCOUNT_NUMBER, IS_WALLET_COMPLAINT, WALLET_NAME,
    IS_BUSINESS_CORRESPONDENT,
    TRANSACTION_REF_NUMBER, DISPUTE_DATE, COMPENSATION_SOUGHT,
    RECEIVED_REPLY_FROM_ENTITY, REPLY_DATE, REMINDER_DATE,
    SUB_CATEGORY1, SUB_CATEGORY2,
    CREATED_AT, UPDATED_AT
) VALUES (
    1001, 1001, '100042',
    42, 'MALE', 'INDIVIDUAL', 'yes',
    NULL, NULL,
    'yes', 'SAVINGS',
    '20012345678', '4567XXXXXXXX8901',
    '4567XXXXXXXX8901', NULL, 'no',
    NULL, 'no', NULL,
    'no',
    'SBI-NGP-ATM-2026-0918', DATE '2026-09-10', 125000.00,
    'yes', DATE '2026-09-12', DATE '2026-09-20',
    'Cash not dispensed', 'ATM debited but cash not received',
    TIMESTAMP '2026-09-15 09:00:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 8. COMPLAINT_REPRESENTATIVES
-- ============================================================
INSERT INTO COMPLAINT_REPRESENTATIVES (
    ID, COMPLAINT_ID, DRAFT_ID,
    HAS_AUTH_REP, AUTHORIZE_REPRESENTATIVE, THROUGH_ADVOCATE,
    REP_NAME, REP_PHONE, REP_EMAIL, REP_ADDRESS,
    REP_CITY, REP_DISTRICT, REP_STATE, REP_PINCODE,
    CREATED_AT
) VALUES (
    1001, 1001, '100042',
    'yes', 'yes', 'no',
    'Priya Kumar', '9876501234', 'priya.kumar@gmail.com', '45, MG Road, Sadar, Nagpur',
    'Nagpur', 'Nagpur', 'Maharashtra', '440001',
    TIMESTAMP '2026-09-15 09:00:00'
);

-- ============================================================
-- 9. COMPLAINT_RBIO_FORM_DATA (RBIO officer editable fields)
-- ============================================================
INSERT INTO COMPLAINT_RBIO_FORM_DATA (
    ID, COMPLAINT_ID, DRAFT_ID,
    RECEIPT_DATE, MODE_OF_RECEIPT, COMMENTS,
    COMPLAINT_CPGRAM, CPGRAM_NUMBER,
    MODULE_NAME, ENTITY_COUNTRY, BRANCH_CENTER_NAME,
    OTHER_ENTITY_NAME, REGISTRATION_WITH_RBI_DATE,
    COMPLAINT_REGISTRATION_DATE_VALID, DATE_OF_FILING_COMPLAINT,
    LEGAL_CASE_FILED, LEGAL_FILING_DATE,
    PRE_ENQUIRY_RECEIVED, HIGH_PRIORITY_COMPLAINT, LOAN_DISPOSAL_AMOUNT,
    VERNACULAR_LANGUAGE, ADDITIONAL_COMMENTS,
    COMPLAINT_REGARDING_PENSION, ATM_CREDIT_DEBIT_CARD,
    SCHEME_FLAG, RBO_CGPC_OLD, GROUNDS_FLAG,
    FREE_MARKED_COMPLAINT,
    CREATED_AT, UPDATED_AT
) VALUES (
    1001, 1001, '100042',
    DATE '2026-09-15', 'PHYSICAL_LETTER', 'Complaint letter received via post, original scanned and attached.',
    'no', NULL,
    'ATM Operations', 'India', 'Nagpur Central',
    NULL, DATE '2024-01-15',
    'yes', DATE '2026-09-10',
    'no', NULL,
    'no', 'yes', NULL,
    NULL, 'High value ATM dispute. Complainant has provided ATM CCTV footage request reference.',
    'no', 'yes',
    NULL, NULL, 'ATM_CASH_NOT_DISPENSED',
    'no',
    TIMESTAMP '2026-09-15 09:00:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 10. COMPLAINT_COMMENTS (3 comments — DO, Reviewer, NO-targeted)
-- ============================================================
INSERT INTO COMPLAINT_COMMENTS (
    ID, COMPLAINT_NUMBER, AUTHOR, INITIALS, TEXT, ROLE, COLOR,
    NO_RECORD_NUMBER, TARGET, CREATED_AT
) VALUES (
    1001, 'RBIO-NGP/2627/000042',
    'Amit Sharma', 'AS',
    'Eligibility assessment completed. Entity is regulated by RBI. Complainant filed with RE on 10-Sep-2026 and received unsatisfactory reply on 12-Sep-2026. Complaint is MAINTAINABLE under clause 16(2)(a). Forwarding to RE for response under Clause 13(1).',
    'RBIO_DO', '#6366f1',
    NULL, NULL,
    TIMESTAMP '2026-09-16 10:35:00'
);

INSERT INTO COMPLAINT_COMMENTS (
    ID, COMPLAINT_NUMBER, AUTHOR, INITIALS, TEXT, ROLE, COLOR,
    NO_RECORD_NUMBER, TARGET, CREATED_AT
) VALUES (
    1002, 'RBIO-NGP/2627/000042',
    'Meena Iyer', 'MI',
    'Reviewed the assessment. ATM transaction failure is a straightforward case. RE should be able to resolve via CBS reversal. SLA compliance to be monitored closely given the high amount.',
    'RBIO_REVIEWER', '#2563eb',
    NULL, NULL,
    TIMESTAMP '2026-09-16 14:20:00'
);

INSERT INTO COMPLAINT_COMMENTS (
    ID, COMPLAINT_NUMBER, AUTHOR, INITIALS, TEXT, ROLE, COLOR,
    NO_RECORD_NUMBER, TARGET, CREATED_AT
) VALUES (
    1003, 'RBIO-NGP/2627/000042',
    'Amit Sharma', 'AS',
    'Please expedite the CBS reversal and share the ATM EJ log within 3 working days.',
    'RBIO_DO', '#7c3aed',
    '0001001', 'NO',
    TIMESTAMP '2026-09-17 11:00:00'
);

-- ============================================================
-- 11. NODAL_OFFICER_RECORDS (with V19 + V20 columns)
-- ============================================================
INSERT INTO NODAL_OFFICER_RECORDS (
    ID, RECORD_NUMBER, COMPLAINT_NUMBER,
    ENTITY_NAME, NODAL_OFFICER_NAME,
    PNO_NAME, PNO_EMAIL, PNO_PHONE,
    DESIGNATION, EMAIL, PHONE,
    STATUS, ASSIGNED_TO,
    ADVISORY_COMPLIANCE_DATE, DISPUTE_AMOUNT,
    COMPENSATION_LOSS, COMPENSATION_MENTAL,
    AWARD_IMPLEMENTATION_DATE, AWARD_ACCEPTANCE_DATE,
    NOTICE_131_COMPLY_DATE, FORWARDED_TO_RE_AT,
    CREATED_AT, LAST_MODIFIED_AT
) VALUES (
    1001, '0001001', 'RBIO-NGP/2627/000042',
    'State Bank of India - Nagpur Main Branch', 'Suresh Patil',
    'Anita Deshmukh', 'anita.deshmukh@sbi.co.in', '9876543211',
    'Chief Manager', 'suresh.patil@sbi.co.in', '9876543210',
    'INFORMATION_REQUIRED', 'rbio_do1',
    DATE '2026-10-05', 125000.00,
    NULL, NULL,
    NULL, NULL,
    DATE '2026-10-02', TIMESTAMP '2026-09-17 10:00:00',
    TIMESTAMP '2026-09-16 10:40:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 12. CONCILIATION_MEETINGS (one scheduled meeting)
-- ============================================================
INSERT INTO CONCILIATION_MEETINGS (
    ID, COMPLAINT_ID,
    MEETING_STATUS, MEETING_DATE, MEETING_TIME,
    ACCEPTED_BY_COMPLAINANT, ACCEPTED_BY_ENTITY, CONDUCTED_THROUGH_VC,
    MEETING_COMMENTS, COMMENTS,
    CREATED_BY, UPDATED_BY,
    CREATED_AT, UPDATED_AT
) VALUES (
    1001, 1001,
    'SCHEDULED', DATE '2026-10-10', '11:00',
    'yes', 'yes', 'yes',
    NULL, 'Conciliation meeting scheduled via video conference. Both parties have confirmed availability. Agenda: review ATM EJ log and CBS entries.',
    'rbio_do1', 'rbio_do1',
    TIMESTAMP '2026-09-18 15:00:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 13. RE_RESPONSE_TRACKER
-- ============================================================
INSERT INTO RE_RESPONSE_TRACKER (
    ID, COMPLAINT_ID, REGULATED_ENTITY_ID,
    FORWARDED_AT, RESPONDED_AT,
    WINDOW_DAYS, WINDOW_EXPIRES_AT,
    BREACHED, EX_PARTE_ELIGIBLE,
    NOTES,
    RESPONSE_TEXT, QUERY_TEXT, QUERY_RAISED_AT,
    EXTENSION_GRANTED, EXTENSION_DAYS,
    CREATED_AT, UPDATED_AT
) VALUES (
    1001, 1001, 1001,
    TIMESTAMP '2026-09-17 10:00:00', NULL,
    15, TIMESTAMP '2026-10-02 23:59:59',
    0, 0,
    'Clause 13(1) notice issued to SBI Nagpur Main Branch. 15-day response window. EJ log and CBS reversal status requested.',
    NULL,
    'Please provide: (1) ATM Electronic Journal log for ATM ID SBI-NGP-0042 for 10-Sep-2026 12:00-13:00 hrs, (2) CBS transaction details for account 20012345678, (3) Status of reversal request if initiated.',
    TIMESTAMP '2026-09-17 10:00:00',
    0, NULL,
    TIMESTAMP '2026-09-17 10:00:00', CURRENT_TIMESTAMP
);

-- ============================================================
-- 14. OFFICER_AVAILABILITY (the assigned DO)
-- ============================================================
INSERT INTO OFFICER_AVAILABILITY (
    ID, USER_ID, ROLE, ACTIVE, ON_LEAVE,
    LEAVE_START_DATE, LEAVE_END_DATE, LEAVE_REASON,
    CURRENT_WORKLOAD, MAX_WORKLOAD, OFFICE_CODE, UPDATED_AT
) VALUES (
    1001, 'rbio_do1', 'RBIO_DO', 1, 0,
    NULL, NULL, NULL,
    12, 20, 'RBIO-NGP', CURRENT_TIMESTAMP
);

-- ============================================================
-- Done. Verify with:
--   SELECT * FROM COMPLAINTS WHERE ID = 1001;
--   SELECT * FROM COMPLAINT_TIMELINE WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_ATTACHMENTS WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_ELIGIBILITY_ANSWERS WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_ADDITIONAL_DETAILS WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_REPRESENTATIVES WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_RBIO_FORM_DATA WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM COMPLAINT_COMMENTS WHERE COMPLAINT_NUMBER = 'RBIO-NGP/2627/000042';
--   SELECT * FROM NODAL_OFFICER_RECORDS WHERE COMPLAINT_NUMBER = 'RBIO-NGP/2627/000042';
--   SELECT * FROM CONCILIATION_MEETINGS WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM RE_RESPONSE_TRACKER WHERE COMPLAINT_ID = 1001;
--   SELECT * FROM OFFICER_AVAILABILITY WHERE USER_ID = 'rbio_do1';
--   SELECT * FROM COMPLAINT_NUMBER_SEQUENCE WHERE OFFICE_CODE = 'RBIO-NGP';
-- ============================================================
