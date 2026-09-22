-- ============================================================================
-- CMS (Complaint Management System) - Complete Database Scripts
-- Database: MySQL 8+
-- ============================================================================

-- ============================================================================
-- SECTION 1: DATABASE & USER SETUP
-- ============================================================================

CREATE DATABASE IF NOT EXISTS cms_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE cms_db;

-- ============================================================================
-- SECTION 2: TABLE CREATION (DDL)
-- ============================================================================

-- 2.1 BANKS
CREATE TABLE IF NOT EXISTS BANKS (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(300) NOT NULL,
    code        VARCHAR(50),
    type        VARCHAR(100),
    status      VARCHAR(20)  DEFAULT 'active',
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_bank_type   (type),
    INDEX idx_bank_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.2 COMPLAINT CATEGORIES
CREATE TABLE IF NOT EXISTS COMPLAINT_CATEGORIES (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    parent_id   BIGINT,
    status      VARCHAR(20)  DEFAULT 'active',
    sort_order  INT          DEFAULT 0,
    created_at  DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.3 COMPLAINTS
CREATE TABLE IF NOT EXISTS COMPLAINTS (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_number          VARCHAR(50)  NOT NULL,
    complainant_name          VARCHAR(200) NOT NULL,
    complainant_email         VARCHAR(200),
    complainant_phone         VARCHAR(20),
    complainant_address       VARCHAR(500),
    bank_id                   BIGINT,
    bank_branch               VARCHAR(300),
    account_number            VARCHAR(100),
    category_id               BIGINT,
    subject                   VARCHAR(500) NOT NULL,
    description               TEXT,
    relief_sought             TEXT,
    status                    VARCHAR(30)  NOT NULL DEFAULT 'pending',
    priority                  VARCHAR(20)  DEFAULT 'medium',
    filing_type               VARCHAR(50),
    bank_complaint_reference  VARCHAR(200),
    bank_complaint_date       DATETIME(6),
    assigned_officer          VARCHAR(200),
    filed_at                  DATETIME(6),
    resolved_at               DATETIME(6),
    closed_at                 DATETIME(6),
    escalated_at              DATETIME(6),
    created_at                DATETIME(6),
    updated_at                DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY  idx_complaint_number         (complaint_number),
    INDEX       idx_complaint_status         (status),
    INDEX       idx_complaint_priority       (priority),
    INDEX       idx_complaint_email          (complainant_email),
    INDEX       idx_complaint_category       (category_id),
    INDEX       idx_complaint_bank           (bank_id),
    INDEX       idx_complaint_created        (created_at),
    INDEX       idx_complaint_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.4 COMPLAINT ATTACHMENTS
CREATE TABLE IF NOT EXISTS COMPLAINT_ATTACHMENTS (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id  BIGINT       NOT NULL,
    file_name     VARCHAR(500) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    content_type  VARCHAR(100),
    file_size     BIGINT,
    storage_path  VARCHAR(1000),
    uploaded_at   DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_attachment_complaint (complaint_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.5 COMPLAINT TIMELINE (Audit Trail)
CREATE TABLE IF NOT EXISTS COMPLAINT_TIMELINE (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    complaint_id  BIGINT       NOT NULL,
    action        VARCHAR(50)  NOT NULL,
    performed_by  VARCHAR(200),
    remarks       TEXT,
    from_status   VARCHAR(30),
    to_status     VARCHAR(30),
    performed_at  DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_timeline_complaint   (complaint_id),
    INDEX idx_timeline_performed_at (performed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.6 FORM CONFIGS (Dynamic JSON Form Schemas)
CREATE TABLE IF NOT EXISTS FORM_CONFIGS (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    form_key    VARCHAR(100) NOT NULL,
    form_name   VARCHAR(200),
    schema_json JSON,
    active      BIT(1)       NOT NULL DEFAULT 1,
    version     VARCHAR(50),
    created_at  DATETIME(6),
    updated_at  DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_form_key (form_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- 2.7 SIMULATED EMAILS
CREATE TABLE IF NOT EXISTS SIMULATED_EMAILS (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    message_id        VARCHAR(100) NOT NULL,
    thread_id         VARCHAR(100) NOT NULL,
    from_email        VARCHAR(200) NOT NULL,
    to_email          VARCHAR(200) NOT NULL,
    subject           VARCHAR(500) NOT NULL,
    body              TEXT,
    direction         VARCHAR(10)  NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'UNREAD',
    complaint_id      BIGINT,
    complaint_number  VARCHAR(50),
    attachment_url    VARCHAR(500),
    sent_at           DATETIME(6),
    received_at       DATETIME(6),
    processed_at      DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_id            (message_id),
    INDEX idx_email_thread              (thread_id),
    INDEX idx_email_direction           (direction),
    INDEX idx_email_complaint           (complaint_id),
    INDEX idx_email_complaint_number    (complaint_number),
    INDEX idx_email_sent_at             (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================================
-- SECTION 3: SEED DATA (DML)
-- ============================================================================

-- 3.1 Seed Complaint Categories
INSERT INTO COMPLAINT_CATEGORIES (name, description, sort_order, status, created_at) VALUES
    ('ATM / Debit Card',       'Issues related to ATM transactions and debit cards',          1,  'active', NOW()),
    ('Credit Card',            'Issues related to credit card transactions and billing',       2,  'active', NOW()),
    ('Internet Banking',       'Issues with online banking services',                          3,  'active', NOW()),
    ('Mobile Banking / UPI',   'Issues with mobile banking apps and UPI transactions',         4,  'active', NOW()),
    ('Loan / Advances',        'Issues with loan processing, EMI, interest rates',             5,  'active', NOW()),
    ('Deposit Accounts',       'Issues with savings, current, or fixed deposit accounts',      6,  'active', NOW()),
    ('Pension',                'Pension related grievances',                                    7,  'active', NOW()),
    ('Remittance / Transfer',  'Issues with fund transfers, NEFT, RTGS, IMPS',                 8,  'active', NOW()),
    ('Insurance',              'Insurance related complaints',                                  9,  'active', NOW()),
    ('Others',                 'Other banking related complaints',                             10,  'active', NOW());


-- 3.2 Seed Banks
INSERT INTO BANKS (name, code, type, status, created_at) VALUES
    ('State Bank of India',    'SBI',      'public',  'active', NOW()),
    ('Punjab National Bank',   'PNB',      'public',  'active', NOW()),
    ('Bank of Baroda',         'BOB',      'public',  'active', NOW()),
    ('Canara Bank',            'CANARA',   'public',  'active', NOW()),
    ('Union Bank of India',    'UNION',    'public',  'active', NOW()),
    ('HDFC Bank',              'HDFC',     'private', 'active', NOW()),
    ('ICICI Bank',             'ICICI',    'private', 'active', NOW()),
    ('Axis Bank',              'AXIS',     'private', 'active', NOW()),
    ('Kotak Mahindra Bank',    'KOTAK',    'private', 'active', NOW()),
    ('IndusInd Bank',          'INDUSIND', 'private', 'active', NOW()),
    ('Yes Bank',               'YES',      'private', 'active', NOW()),
    ('IDBI Bank',              'IDBI',     'public',  'active', NOW());


-- 3.3 Seed Form Configuration (Raise Complaint Form Schema)
INSERT INTO FORM_CONFIGS (form_key, form_name, schema_json, active, version, created_at, updated_at) VALUES
('raise-complaint', 'Raise a Complaint', '{
  "formTitle": "Raise a Complaint",
  "steps": [
    {
      "stepNumber": 1,
      "title": "Tell Us About You",
      "description": "Share some basic details about yourself to help us contact you regarding your complaint.",
      "helpText": "Why are we asking this?",
      "fields": [
        {"key": "complainantCategory", "label": "Complainant Category", "type": "select", "required": true, "fullWidth": true, "options": [{"label": "Individual", "value": "individual"}, {"label": "Business", "value": "business"}, {"label": "Other", "value": "other"}]},
        {"key": "name", "label": "Name", "type": "text", "required": true, "placeholder": "Enter your full name"},
        {"key": "mobileNumber", "label": "Mobile Number", "type": "tel", "required": true, "placeholder": "Enter Mobile Number", "prefix": "+91", "hasVerify": true},
        {"key": "email", "label": "Email (Optional)", "type": "email", "required": false, "placeholder": "Enter email address"},
        {"key": "pincode", "label": "Pincode", "type": "text", "required": true, "placeholder": "Enter", "maxLength": 6},
        {"key": "district", "label": "District", "type": "select", "required": true, "optionsSource": "districts"},
        {"key": "state", "label": "State", "type": "select", "required": true, "optionsSource": "states"},
        {"key": "address", "label": "Address", "type": "textarea", "required": true, "fullWidth": true, "placeholder": "Enter", "rows": 3}
      ]
    },
    {
      "stepNumber": 2,
      "title": "Entity Details",
      "description": "Tell us about the bank or financial institution you want to complain against.",
      "helpText": "Why are we asking this?",
      "fields": [
        {"key": "entityType", "label": "Entity Type", "type": "select", "required": true, "options": [{"label": "Bank", "value": "bank"}, {"label": "NBFC", "value": "nbfc"}, {"label": "Payment System", "value": "payment"}]},
        {"key": "entityName", "label": "Entity Name", "type": "select", "required": true, "optionsSource": "banks"},
        {"key": "branch", "label": "Branch (if applicable)", "type": "text", "required": false, "placeholder": "Enter branch name"},
        {"key": "accountNumber", "label": "Account Number (if applicable)", "type": "text", "required": false, "placeholder": "Enter account number"}
      ]
    },
    {
      "stepNumber": 3,
      "title": "Final Step: Share Your Complaint",
      "description": "Describe your complaint, actions taken, responses received, and include supporting documents.",
      "helpText": "Why are we asking this?",
      "fields": [
        {"key": "complaintCategory", "label": "Complaint Category", "type": "select", "required": true, "optionsSource": "categories"},
        {"key": "subCategory1", "label": "Complaint Sub-Category 1", "type": "select", "required": false, "optionsSource": "subCategories1", "dependsOn": "complaintCategory"},
        {"key": "subCategory2", "label": "Complaint Sub-Category 2", "type": "select", "required": false, "optionsSource": "subCategories2", "dependsOn": "subCategory1"},
        {"key": "facts", "label": "Facts of the complaint", "type": "textarea", "required": true, "fullWidth": true, "placeholder": "Enter", "rows": 4},
        {"key": "isSubJudice", "label": "Is your complaint sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority?", "type": "radio", "required": true, "fullWidth": true, "options": [{"label": "Yes", "value": true}, {"label": "No", "value": false}]},
        {"key": "throughAdvocate", "label": "Is your complaint made through an advocate (unless you are yourself an advocate)?", "type": "radio", "required": true, "fullWidth": true, "options": [{"label": "Yes", "value": true}, {"label": "No", "value": false}]},
        {"key": "alreadyWithOmbudsman", "label": "Has your complaint already been dealt with or is under process on the same ground with the Ombudsman?", "type": "radio", "required": true, "fullWidth": true, "options": [{"label": "Yes", "value": true}, {"label": "No", "value": false}]},
        {"key": "regulatedEntityStaff", "label": "Is complaint from the staff of a regulated entity and involves employer employee relationship?", "type": "radio", "required": true, "fullWidth": true, "options": [{"label": "Yes", "value": true}, {"label": "No", "value": false}]},
        {"key": "attachments", "label": "Attachments", "type": "file", "required": false, "fullWidth": true, "accept": ".pdf,.jpg,.png", "multiple": true, "maxSize": 5242880, "hint": "Support formats: PDF, JPG, PNG. Maximum size: 5MB"},
        {"key": "authorizeRepresentative", "label": "If you want to authorize a representative to appear and make submission on your behalf before the Ombudsman, please select Yes and furnish the details of the Authorized Representative", "type": "radio", "required": false, "fullWidth": true, "options": [{"label": "Yes", "value": true}, {"label": "No", "value": false}]}
      ]
    }
  ],
  "preFilingModal": {
    "title": "Before Filing a Complaint",
    "subtitle": "SELECT WHICHEVER IS APPLICABLE",
    "options": [
      {"id": "not_contacted", "number": 1, "title": "I have not contacted my bank or financial institution", "description": "Select this option if you have not filed a complaint with your bank or financial institution yet."},
      {"id": "already_filed", "number": 2, "title": "I have filed a complaint with bank or financial institution", "description": "Select this option if you are not satisfied with the reply provided by your bank or financial institute or if they have not provided a response to your complaint in 30 days.", "conditionalFields": [{"key": "bankComplaintDate", "label": "When did you first file the complaint with your bank or financial institution?", "type": "date", "required": true}, {"key": "receivedReply", "label": "Have you received any reply from your bank or financial institution?", "type": "radio", "required": true, "options": [{"label": "Yes", "value": "yes"}, {"label": "No", "value": "no"}]}]}
    ]
  }
}', true, '1.0', NOW(), NOW());


-- ============================================================================
-- SECTION 4: USEFUL QUERIES FOR DEVELOPERS
-- ============================================================================

-- 4.1 Dashboard Statistics Query
SELECT
    COUNT(*)                                                      AS total_complaints,
    SUM(CASE WHEN status = 'pending'     THEN 1 ELSE 0 END)      AS pending_complaints,
    SUM(CASE WHEN status = 'in_progress' THEN 1 ELSE 0 END)      AS in_progress_complaints,
    SUM(CASE WHEN status = 'resolved'    THEN 1 ELSE 0 END)      AS resolved_complaints,
    SUM(CASE WHEN status = 'closed'      THEN 1 ELSE 0 END)      AS closed_complaints,
    SUM(CASE WHEN status = 'escalated'   THEN 1 ELSE 0 END)      AS escalated_complaints,
    SUM(CASE WHEN priority = 'high'      THEN 1 ELSE 0 END)      AS high_priority,
    SUM(CASE WHEN priority = 'medium'    THEN 1 ELSE 0 END)      AS medium_priority,
    SUM(CASE WHEN priority = 'low'       THEN 1 ELSE 0 END)      AS low_priority
FROM COMPLAINTS;

-- 4.2 Complaints with Bank & Category names
SELECT
    c.id, c.complaint_number, c.complainant_name, c.subject,
    c.status, c.priority, c.created_at,
    b.name AS bank_name,
    cat.name AS category_name
FROM COMPLAINTS c
LEFT JOIN BANKS b ON c.bank_id = b.id
LEFT JOIN COMPLAINT_CATEGORIES cat ON c.category_id = cat.id
ORDER BY c.created_at DESC;

-- 4.3 Complaint timeline/audit trail for a specific complaint
SELECT
    ct.action, ct.from_status, ct.to_status,
    ct.performed_by, ct.remarks, ct.performed_at
FROM COMPLAINT_TIMELINE ct
WHERE ct.complaint_id = ?
ORDER BY ct.performed_at ASC;

-- 4.4 Attachments for a complaint
SELECT
    ca.id, ca.file_name, ca.original_name,
    ca.content_type, ca.file_size, ca.storage_path, ca.uploaded_at
FROM COMPLAINT_ATTACHMENTS ca
WHERE ca.complaint_id = ?
ORDER BY ca.uploaded_at DESC;

-- 4.5 Email threads with complaint info
SELECT
    se.thread_id, se.from_email, se.to_email,
    se.subject, se.direction, se.status,
    se.complaint_number, se.sent_at
FROM SIMULATED_EMAILS se
ORDER BY se.thread_id, se.sent_at ASC;

-- 4.6 Banks by type
SELECT id, name, code, type FROM BANKS WHERE status = 'active' AND type = 'public';
SELECT id, name, code, type FROM BANKS WHERE status = 'active' AND type = 'private';

-- 4.7 Category hierarchy (root + sub-categories)
SELECT id, name, description, sort_order FROM COMPLAINT_CATEGORIES WHERE parent_id IS NULL AND status = 'active' ORDER BY sort_order;
SELECT id, name, description, parent_id   FROM COMPLAINT_CATEGORIES WHERE parent_id = ? AND status = 'active' ORDER BY sort_order;


-- ============================================================================
-- SECTION 5: DROP ALL TABLES (USE WITH CAUTION!)
-- ============================================================================

-- DROP TABLE IF EXISTS SIMULATED_EMAILS;
-- DROP TABLE IF EXISTS COMPLAINT_ATTACHMENTS;
-- DROP TABLE IF EXISTS COMPLAINT_TIMELINE;
-- DROP TABLE IF EXISTS COMPLAINTS;
-- DROP TABLE IF EXISTS COMPLAINT_CATEGORIES;
-- DROP TABLE IF EXISTS BANKS;
-- DROP TABLE IF EXISTS FORM_CONFIGS;
