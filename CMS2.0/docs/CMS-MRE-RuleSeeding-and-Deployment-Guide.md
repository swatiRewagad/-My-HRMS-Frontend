# CMS 2.0 — MRE Rule Seeding, Technical Flow, Developer Guide & Deployment

> **Scheme Reference:** Reserve Bank – Integrated Ombudsman Scheme (RB-IOS), 2026  
> **Effective Date:** July 1, 2026  
> **Document Version:** 5.0.0  
> **Last Updated:** 2026-07-02

---

## Table of Contents

1. [DDL — MRE Rules Schema](#1-ddl--mre-rules-schema)
2. [DML — Rule Seed Data (RB-IOS 2026)](#2-dml--rule-seed-data-rb-ios-2026)
3. [Technical Flow — MRE Assessment Pipeline](#3-technical-flow--mre-assessment-pipeline)
4. [Developer Guidelines](#4-developer-guidelines)
5. [Infrastructure Setup for OpenShift Deployment](#5-infrastructure-setup-for-openshift-deployment)
6. [Operators to Install on OpenShift](#6-operators-to-install-on-openshift)
7. [Developer Local-to-Dev Environment Connectivity](#7-developer-local-to-dev-environment-connectivity)
8. [Local Docker Desktop & Application Setup](#8-local-docker-desktop--application-setup)

---

## 1. DDL — MRE Rules Schema

### 1.1 Core Tables (Oracle-compatible)

```sql
-- ============================================================
-- CMS MRE Rules Engine — Schema DDL
-- Database: Oracle 21c (XE or Enterprise)
-- Scheme Basis: RB-IOS 2026, Clauses 9-16
-- ============================================================

-- SEQUENCES
CREATE SEQUENCE MRE_RULE_CATEGORY_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE MRE_RULE_DEFINITION_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE MRE_RULE_HISTORY_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE SEQUENCE MRE_DEPLOYMENT_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

-- ============================================================
-- TABLE: MRE_RULE_CATEGORY
-- ============================================================
CREATE TABLE MRE_RULE_CATEGORY (
    ID                NUMBER(19) DEFAULT MRE_RULE_CATEGORY_SEQ.NEXTVAL PRIMARY KEY,
    CATEGORY_CODE     VARCHAR2(50) NOT NULL UNIQUE,
    CATEGORY_NAME     VARCHAR2(200) NOT NULL,
    DESCRIPTION       VARCHAR2(1000),
    IS_ACTIVE         NUMBER(1) DEFAULT 1 NOT NULL,
    DISPLAY_ORDER     NUMBER(5) DEFAULT 0,
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP
);
CREATE INDEX IDX_RULE_CAT_ACTIVE ON MRE_RULE_CATEGORY(IS_ACTIVE, DISPLAY_ORDER);

-- ============================================================
-- TABLE: MRE_RULE_DEFINITION
-- Stores each rule with DRL content, version, maker-checker
-- ============================================================
CREATE TABLE MRE_RULE_DEFINITION (
    ID                NUMBER(19) DEFAULT MRE_RULE_DEFINITION_SEQ.NEXTVAL PRIMARY KEY,
    RULE_CODE         VARCHAR2(50) NOT NULL UNIQUE,
    RULE_NAME         VARCHAR2(300) NOT NULL,
    CATEGORY_ID       NUMBER(19) NOT NULL,
    DRL_CONTENT       CLOB NOT NULL,
    SALIENCE          NUMBER(5) DEFAULT 10,
    VERSION           NUMBER(10) DEFAULT 1 NOT NULL,
    STATUS            VARCHAR2(20) DEFAULT 'DRAFT' NOT NULL,
    EFFECTIVE_FROM    TIMESTAMP,
    EFFECTIVE_TO      TIMESTAMP,
    CREATED_BY        VARCHAR2(100) NOT NULL,
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_BY        VARCHAR2(100),
    UPDATED_AT        TIMESTAMP,
    APPROVED_BY       VARCHAR2(100),
    APPROVED_AT       TIMESTAMP,
    CONSTRAINT FK_RULE_CATEGORY FOREIGN KEY (CATEGORY_ID) REFERENCES MRE_RULE_CATEGORY(ID),
    CONSTRAINT CHK_RULE_STATUS CHECK (STATUS IN ('DRAFT','PENDING_REVIEW','ACTIVE','INACTIVE','ARCHIVED'))
);
CREATE INDEX IDX_RULE_DEF_STATUS ON MRE_RULE_DEFINITION(STATUS);
CREATE INDEX IDX_RULE_DEF_CATEGORY ON MRE_RULE_DEFINITION(CATEGORY_ID, STATUS);

-- ============================================================
-- TABLE: MRE_RULE_HISTORY
-- ============================================================
CREATE TABLE MRE_RULE_HISTORY (
    ID                NUMBER(19) DEFAULT MRE_RULE_HISTORY_SEQ.NEXTVAL PRIMARY KEY,
    RULE_ID           NUMBER(19) NOT NULL,
    VERSION           NUMBER(10) NOT NULL,
    DRL_CONTENT       CLOB NOT NULL,
    CHANGE_REASON     VARCHAR2(1000),
    ACTION            VARCHAR2(50) NOT NULL,
    CHANGED_BY        VARCHAR2(100) NOT NULL,
    CHANGED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT FK_HISTORY_RULE FOREIGN KEY (RULE_ID) REFERENCES MRE_RULE_DEFINITION(ID)
);
CREATE INDEX IDX_RULE_HIST_RULE ON MRE_RULE_HISTORY(RULE_ID, VERSION);

-- ============================================================
-- TABLE: MRE_DEPLOYMENT
-- ============================================================
CREATE TABLE MRE_DEPLOYMENT (
    ID                NUMBER(19) DEFAULT MRE_DEPLOYMENT_SEQ.NEXTVAL PRIMARY KEY,
    DEPLOYMENT_ID     VARCHAR2(50) NOT NULL UNIQUE,
    RULES_DEPLOYED    NUMBER(10) NOT NULL,
    STATUS            VARCHAR2(20) NOT NULL,
    DEPLOYED_BY       VARCHAR2(100) NOT NULL,
    DEPLOYED_AT       TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    ERROR_MESSAGE     VARCHAR2(2000),
    CONSTRAINT CHK_DEPLOY_STATUS CHECK (STATUS IN ('SUCCESS','FAILED','ROLLED_BACK'))
);
CREATE INDEX IDX_DEPLOYMENT_STATUS ON MRE_DEPLOYMENT(STATUS, DEPLOYED_AT);

-- ============================================================
-- TABLE: MRE_GROUND_CONFIG
-- Configurable grounds from RB-IOS 2026 Clause 10
-- ============================================================
CREATE TABLE MRE_GROUND_CONFIG (
    ID                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    GROUND_CODE       VARCHAR2(50) NOT NULL UNIQUE,
    CLAUSE_REF        VARCHAR2(20) NOT NULL,
    DESCRIPTION       VARCHAR2(500) NOT NULL,
    IS_ACTIVE         NUMBER(1) DEFAULT 1 NOT NULL,
    EVALUATION_ORDER  NUMBER(5) DEFAULT 0,
    FAIL_MESSAGE      VARCHAR2(1000),
    PASS_MESSAGE      VARCHAR2(1000),
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX IDX_GROUND_ACTIVE ON MRE_GROUND_CONFIG(IS_ACTIVE, EVALUATION_ORDER);

-- ============================================================
-- TABLE: MRE_WINDOW_CONFIG
-- Per-category window days (Clause 10(1)(f))
-- ============================================================
CREATE TABLE MRE_WINDOW_CONFIG (
    ID                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    CATEGORY_CODE     VARCHAR2(50) NOT NULL,
    ENTITY_TYPE       VARCHAR2(50) DEFAULT 'ALL',
    WINDOW_DAYS       NUMBER(5) NOT NULL,
    FILING_DEADLINE   NUMBER(5) DEFAULT 90 NOT NULL,
    WINDOW_BASIS      VARCHAR2(20) DEFAULT 'CALENDAR' NOT NULL,
    IS_ACTIVE         NUMBER(1) DEFAULT 1 NOT NULL,
    EFFECTIVE_FROM    DATE NOT NULL,
    EFFECTIVE_TO      DATE,
    CREATED_BY        VARCHAR2(100),
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT CHK_WINDOW_BASIS CHECK (WINDOW_BASIS IN ('CALENDAR','BUSINESS'))
);
CREATE UNIQUE INDEX IDX_WINDOW_UNIQUE ON MRE_WINDOW_CONFIG(CATEGORY_CODE, ENTITY_TYPE, EFFECTIVE_FROM);

-- ============================================================
-- TABLE: MRE_EXCLUSION_CONFIG
-- Matters excluded under Clause 10(2)
-- ============================================================
CREATE TABLE MRE_EXCLUSION_CONFIG (
    ID                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    EXCLUSION_CODE    VARCHAR2(50) NOT NULL UNIQUE,
    CLAUSE_REF        VARCHAR2(20) NOT NULL,
    DESCRIPTION       VARCHAR2(500) NOT NULL,
    KEYWORD_PATTERNS  VARCHAR2(2000),
    IS_ACTIVE         NUMBER(1) DEFAULT 1 NOT NULL,
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

-- ============================================================
-- TABLE: MRE_ENTITY_COVERAGE
-- Regulated Entities covered under Clause 1(3)
-- ============================================================
CREATE TABLE MRE_ENTITY_COVERAGE (
    ID                NUMBER(19) GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ENTITY_TYPE       VARCHAR2(50) NOT NULL,
    ENTITY_SUBTYPE    VARCHAR2(100),
    MIN_ASSET_CRORE   NUMBER(15,2),
    MIN_DEPOSIT_CRORE NUMBER(15,2),
    MUST_HAVE_CUSTOMER_INTERFACE NUMBER(1) DEFAULT 0,
    MUST_ACCEPT_DEPOSITS NUMBER(1) DEFAULT 0,
    CLAUSE_REF        VARCHAR2(20) NOT NULL,
    IS_COVERED        NUMBER(1) DEFAULT 1 NOT NULL,
    EFFECTIVE_FROM    DATE DEFAULT DATE '2026-07-01' NOT NULL,
    NOTES             VARCHAR2(500),
    CREATED_AT        TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

COMMIT;
```

### 1.2 Complaint Master Extensions (for MRE fields)

```sql
-- ALTER TABLE for MRE-related fields on COMPLAINT_MASTER
ALTER TABLE COMPLAINT_MASTER ADD PRIOR_RE_COMPLAINT NUMBER(1) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD RE_COMPLAINT_DATE DATE DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD RE_COMPLAINT_REFERENCE VARCHAR2(200) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD RE_REPLIED_AND_DISSATISFIED NUMBER(1) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD TRIAGE_SIGNAL VARCHAR2(10) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD TRIAGE_FLAGS CLOB DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD ELIGIBILITY_TIMELINE CLOB DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD MAINTAINABILITY_DETERMINATION VARCHAR2(30) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD MAINTAINABILITY_DETERMINED_BY VARCHAR2(200) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD MAINTAINABILITY_DETERMINED_AT TIMESTAMP DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD AWARD_AMOUNT NUMBER(15,2) DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD RE_LAST_COMMUNICATION_DATE DATE DEFAULT NULL;
ALTER TABLE COMPLAINT_MASTER ADD SAME_GRIEVANCE_PENDING NUMBER(1) DEFAULT 0;

CREATE INDEX IDX_COMPLAINT_RE_DATE ON COMPLAINT_MASTER(RE_COMPLAINT_DATE);
CREATE INDEX IDX_COMPLAINT_TRIAGE ON COMPLAINT_MASTER(TRIAGE_SIGNAL);
CREATE INDEX IDX_COMPLAINT_MAINTAINABILITY ON COMPLAINT_MASTER(MAINTAINABILITY_DETERMINATION);
```

---

## 2. DML — Rule Seed Data (RB-IOS 2026)

### 2.1 MRE Ground Configuration (from Clauses 9, 10, 16)

```sql
-- ============================================================
-- SEED: MRE_GROUND_CONFIG
-- Mapped to RB-IOS 2026 Clause 10 (Maintainability Grounds)
-- ============================================================

-- Clause 10(1)(a): Complaint must be addressed directly to Ombudsman
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('NOT_ADDRESSED_DIRECTLY', '10(1)(a)', 'Complaint not addressed directly to RBI Ombudsman (merely endorsed/CC)', 1, 1,
  'Complaint is merely endorsed/CC to RBI — not addressed directly as required by Clause 10(1)(a)',
  'Complaint is addressed directly to the RBI Ombudsman');

-- Clause 10(1)(b): Not through advocate (unless advocate is complainant)
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_THROUGH_ADVOCATE', '10(1)(b)', 'Complaint filed through an advocate who is not the complainant', 1, 2,
  'Complaint filed through an advocate (non-complainant) — not permitted under Clause 10(1)(b)',
  'Complaint filed by complainant personally or through authorised representative (non-advocate)');

-- Clause 10(1)(d): Abusive/frivolous/vexatious
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('ABUSIVE_FRIVOLOUS', '10(1)(d)', 'Complaint is abusive, frivolous, or vexatious in nature', 1, 3,
  'Complaint appears abusive/frivolous/vexatious — non-maintainable under Clause 10(1)(d)',
  'Complaint is not abusive, frivolous, or vexatious');

-- Clause 10(1)(e): Prior RE complaint required
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('NO_PRIOR_RE_COMPLAINT', '10(1)(e)', 'Complainant has not first complained to the Regulated Entity', 1, 4,
  'Complainant has not first approached the Regulated Entity as required by Clause 10(1)(e)',
  'Complainant has lodged prior complaint with the Regulated Entity');

-- Clause 10(1)(f): 30-day window / RE timeline not elapsed
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEFORE_WINDOW', '10(1)(f)', 'Filed before RE response window (30 days or RBI/NPCI/Card Network timeline) elapsed', 1, 5,
  'Complaint filed before the 30-day (or applicable) window has elapsed — Clause 10(1)(f)',
  'Filed after the applicable window period has elapsed');

-- Clause 10(1)(g): 90-day filing deadline
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEYOND_DEADLINE', '10(1)(g)', 'Filed beyond 90 days from window expiry or last RE communication', 1, 6,
  'Complaint filed beyond 90 days of timeline expiry / last RE communication — Clause 10(1)(g)',
  'Filed within 90 days of the deadline reference date');

-- Clause 10(1)(h)/(i): Same grievance pending/decided at Ombudsman
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('SAME_GRIEVANCE_PENDING_OMBUDSMAN', '10(1)(h)(i)', 'Same grievance already pending or decided by the Office of RBI Ombudsman', 1, 7,
  'Same grievance is already pending or has been dealt with on merits by the Ombudsman — Clause 10(1)(h)/(i)',
  'No duplicate grievance found at the Ombudsman office');

-- Clause 10(1)(j)/(k): Same grievance in court/tribunal
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('SAME_GRIEVANCE_PENDING_COURT', '10(1)(j)(k)', 'Same grievance pending or decided by Court/Tribunal/Arbitrator', 1, 8,
  'Same grievance is pending/decided by a Court, Tribunal, or Arbitrator — Clause 10(1)(j)/(k)',
  'No parallel proceedings found in any court or tribunal');

-- Clause 10(1)(l): Limitation Act 1963
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('RE_COMPLAINT_BEYOND_LIMITATION', '10(1)(l)', 'Complaint to RE made after expiry of Limitation Act 1963 period', 1, 9,
  'Complaint to the Regulated Entity was made beyond the Limitation Act 1963 period (3 years)',
  'RE complaint is within the Limitation Act period');

-- Clause 1(3): Entity coverage
INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('ENTITY_NOT_COVERED', '1(3)', 'Entity not covered under RB-IOS 2026 Scheme', 1, 10,
  'Entity is not a Regulated Entity covered under the Integrated Ombudsman Scheme — Clause 1(3)',
  'Entity is covered under the RB-IOS 2026 Scheme');
```

### 2.2 MRE Exclusion Configuration (Clause 10(2))

```sql
-- ============================================================
-- SEED: MRE_EXCLUSION_CONFIG
-- Matters excluded from Scheme per Clause 10(2)
-- ============================================================

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('COMMERCIAL_JUDGMENT', '10(2)(a)', 'Matters related to commercial judgment or decision of a Regulated Entity', 
  'commercial decision|business judgment|credit decision|loan rejection decision', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('VENDOR_DISPUTE', '10(2)(b)', 'Dispute between a vendor and a Regulated Entity', 
  'vendor|supplier|contractor|service provider dispute', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('MANAGEMENT_GRIEVANCE', '10(2)(c)', 'Grievances against Management or Executives of a Regulated Entity', 
  'management|director|CEO|board|executive conduct', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('JUDICIAL_COMPLIANCE', '10(2)(d)', 'Grievance arising from RE action in compliance with judicial/statutory orders', 
  'court order|statutory direction|NCLT|NCLAT|tribunal order', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('NOT_RBI_PURVIEW', '10(2)(e)', 'Service not within the regulatory purview of the Reserve Bank', 
  'SEBI|IRDAI|PFRDA|mutual fund|stock|insurance (non-NBFC)', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('INTER_RE_DISPUTE', '10(2)(f)', 'Dispute between Regulated Entities', 
  'inter-bank|bank vs bank|RE vs RE|clearing dispute', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('EMPLOYEE_DISPUTE', '10(2)(g)', 'Dispute involving employee-employer relationship of a Regulated Entity', 
  'employee|termination|salary|HR|employment', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('CIC_REMEDY_EXISTS', '10(2)(h)', 'Grievance for which remedy exists in Section 18 of CIC Act 2005', 
  'CIBIL|credit score|credit information|CIC Section 18', 1);

INSERT INTO MRE_EXCLUSION_CONFIG (EXCLUSION_CODE, CLAUSE_REF, DESCRIPTION, KEYWORD_PATTERNS, IS_ACTIVE)
VALUES ('ENTITY_NOT_IN_SCHEME', '10(2)(i)', 'Grievance pertaining to customers of RE not included under the Scheme', 
  'housing finance|CIC excluded|IDF-NBFC|NOFHC|primary dealer', 1);

COMMIT;
```

### 2.3 Entity Coverage Configuration (Clause 1(3))

```sql
-- ============================================================
-- SEED: MRE_ENTITY_COVERAGE
-- RB-IOS 2026 Clause 1(3)(a)-(d)
-- ============================================================

-- Clause 1(3)(a): Banks
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'Commercial Bank', NULL, '1(3)(a)', 1, 'All Commercial Banks covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'Regional Rural Bank', NULL, '1(3)(a)', 1, 'All RRBs covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'State Co-operative Bank', NULL, '1(3)(a)', 1, 'All State Co-op Banks covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'Central Co-operative Bank', NULL, '1(3)(a)', 1, 'All Central Co-op Banks covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'Scheduled Primary Urban Co-operative Bank', NULL, '1(3)(a)', 1, 'All Scheduled PCBs covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_DEPOSIT_CRORE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('BANK', 'Non-Scheduled Primary Urban Co-operative Bank', 50, '1(3)(a)', 1, 'Non-Scheduled PCBs with deposits >= Rs 50 crore');

-- Clause 1(3)(b): NBFCs
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_ASSET_CRORE, MUST_ACCEPT_DEPOSITS, MUST_HAVE_CUSTOMER_INTERFACE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Deposit-accepting NBFC', NULL, 1, 0, '1(3)(b)(i)', 1, 'All deposit-accepting NBFCs covered');

INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, MIN_ASSET_CRORE, MUST_ACCEPT_DEPOSITS, MUST_HAVE_CUSTOMER_INTERFACE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Customer-facing NBFC (>=100Cr)', 100, 0, 1, '1(3)(b)(ii)', 1, 'NBFCs with customer interface and assets >= Rs 100 crore');

-- Excluded NBFCs
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Housing Finance Company', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Core Investment Company (CIC)', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'IDF-NBFC', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'NBFC-IFC', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'NOFHC', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Primary Dealer', '1(3)(b)', 0, 'Excluded from Scheme');
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('NBFC', 'Mortgage Guarantee Company', '1(3)(b)', 0, 'Excluded from Scheme');

-- Clause 1(3)(c): PPI Issuers
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('PPI', 'Non-bank PPI Issuer', '1(3)(c)', 1, 'All Non-bank Prepaid Payment Instruments Issuers covered');

-- Clause 1(3)(d): CIC
INSERT INTO MRE_ENTITY_COVERAGE (ENTITY_TYPE, ENTITY_SUBTYPE, CLAUSE_REF, IS_COVERED, NOTES)
VALUES ('CIC', 'Credit Information Company', '1(3)(d)', 1, 'All Credit Information Companies covered');

COMMIT;
```

### 2.4 Window Configuration (Clause 10(1)(f))

```sql
-- ============================================================
-- SEED: MRE_WINDOW_CONFIG
-- RB-IOS 2026: 30 days default, or per RBI/NPCI/Card Network
-- Filing deadline: 90 days (Clause 10(1)(g))
-- ============================================================

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEFAULT', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('UPI_MOBILE', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('IMPS', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NACH', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('CREDIT_CARD', 'ALL', 60, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEBIT_CARD', 'ALL', 60, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('ATM_DEBIT', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('LOAN', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NEFT_RTGS', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEPOSIT', 'ALL', 30, 90, 'CALENDAR', 1, DATE '2026-07-01', 'system');

COMMIT;
```

### 2.5 Maintainability Rule Definitions (Clause 10 mapped to DRL)

```sql
-- ============================================================
-- SEED: MRE_RULE_DEFINITION — Maintainability Category
-- Updated for RB-IOS 2026 (filing deadline = 90 days per Cl.10(1)(g))
-- ============================================================

-- MRE-001: Entity coverage (Clause 1(3))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-001', 'Check entity coverage under RB-IOS 2026 (Clause 1(3))', 7,
'rule "Entity coverage check - Clause 1(3)"
  salience 100
  when
    $c : Complaint(entityCode != null)
    $e : EntityRegistry(code == $c.entityCode, coveredUnderScheme == false)
  then
    $c.addMreGround("ENTITY_NOT_COVERED", "FAIL", "1(3)",
      "Entity " + $c.getEntityCode() + " is not covered under RB-IOS 2026 Scheme (Clause 1(3))");
    $c.setObjectivelyNonMaintainable(true);
end', 100, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-002: Prior RE complaint (Clause 10(1)(e))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-002', 'Check prior RE complaint requirement (Clause 10(1)(e))', 7,
'rule "Prior RE complaint check - Clause 10(1)(e)"
  salience 95
  when
    $c : Complaint(priorReComplaint == false || priorReComplaint == null)
  then
    $c.addMreGround("NO_PRIOR_RE_COMPLAINT", "FAIL", "10(1)(e)",
      "Complainant has not first complained to the Regulated Entity as required by Clause 10(1)(e)");
    $c.setObjectivelyNonMaintainable(true);
end', 95, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-003: RE window not elapsed (Clause 10(1)(f))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-003', 'Check RE window elapsed (Clause 10(1)(f) — 30 days or per RBI/NPCI/Card)', 7,
'rule "RE window not elapsed - Clause 10(1)(f)"
  salience 90
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   reRepliedAndDissatisfied == false,
                   daysSinceReComplaint < applicableWindowDays)
  then
    $c.addMreGround("FILED_BEFORE_WINDOW", "FAIL", "10(1)(f)",
      "Filed " + $c.getDaysSinceReComplaint() + " days after RE complaint but " +
      $c.getApplicableWindowDays() + "-day window has not elapsed (Clause 10(1)(f))");
    $c.setObjectivelyNonMaintainable(true);
end', 90, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-004: Filing deadline exceeded (Clause 10(1)(g) — 90 days)
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-004', 'Check filing deadline — 90 days (Clause 10(1)(g))', 7,
'rule "Filing deadline exceeded - Clause 10(1)(g)"
  salience 85
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   daysSinceWindowExpiry > 90)
  then
    $c.addMreGround("FILED_BEYOND_DEADLINE", "FAIL", "10(1)(g)",
      "Filed " + $c.getDaysSinceWindowExpiry() + " days after window expiry/last RE communication (limit: 90 days per Clause 10(1)(g))");
    $c.setObjectivelyNonMaintainable(true);
end', 85, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-005: Limitation Act period (Clause 10(1)(l) — 3 years)
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-005', 'Check Limitation Act 1963 period (Clause 10(1)(l))', 7,
'rule "Limitation period exceeded - Clause 10(1)(l)"
  salience 80
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   yearsSinceReComplaint > 3)
  then
    $c.addMreGround("RE_COMPLAINT_BEYOND_LIMITATION", "FAIL", "10(1)(l)",
      "RE complaint date exceeds 3-year Limitation Act 1963 period (Clause 10(1)(l))");
    $c.setObjectivelyNonMaintainable(true);
end', 80, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-006: Duplicate grievance at Ombudsman (Clause 10(1)(h)/(i))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-006', 'Check duplicate grievance at Ombudsman (Clause 10(1)(h)(i))', 7,
'rule "Duplicate grievance - Clause 10(1)(h)/(i)"
  salience 75
  when
    $c : Complaint(sameGrievancePendingOrDecidedOmbudsman == true)
  then
    $c.addMreGround("SAME_GRIEVANCE_PENDING_OMBUDSMAN", "FAIL", "10(1)(h)/(i)",
      "Same grievance already pending or dealt with on merits by the Ombudsman");
    $c.setObjectivelyNonMaintainable(true);
end', 75, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- MRE-007: Same grievance in court/tribunal (Clause 10(1)(j)/(k))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-007', 'Check parallel court/tribunal proceedings (Clause 10(1)(j)(k))', 7,
'rule "Court/tribunal parallel proceedings - Clause 10(1)(j)/(k)"
  salience 70
  when
    $c : Complaint(sameGrievancePendingOrDecidedCourt == true)
  then
    $c.addMreGround("SAME_GRIEVANCE_PENDING_COURT", "FAIL", "10(1)(j)/(k)",
      "Same grievance pending/decided by a Court, Tribunal, or Arbitrator (excluding criminal proceedings per Explanation 1)");
    $c.setObjectivelyNonMaintainable(true);
end', 70, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

-- Compensation Rules (Clause 8(3))
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-001', 'Consequential loss cap Rs 30 lakh (Clause 8(3))', 8,
'rule "Consequential loss cap - Clause 8(3)"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(consequentialLoss > 3000000)
  then
    $award.setConsequentialLoss(3000000);
    $award.addNote("Capped at Rs 30,00,000 per Clause 8(3) of RB-IOS 2026");
end', 10, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-002', 'Time/harassment compensation cap Rs 3 lakh (Clause 8(3))', 8,
'rule "Time/harassment cap - Clause 8(3)"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(timeHarassmentAmount > 300000)
  then
    $award.setTimeHarassmentAmount(300000);
    $award.addNote("Capped at Rs 3,00,000 per Clause 8(3) of RB-IOS 2026");
end', 10, 1, 'ACTIVE', TIMESTAMP '2026-07-01 00:00:00', 'system', SYSTIMESTAMP, 'supervisor', SYSTIMESTAMP);

COMMIT;
```

---

## 3. Technical Flow — MRE Assessment Pipeline

### 3.1 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                        RBIO OFFICER PORTAL                          │
│  (Angular 20 — localhost:4200/staff/rbio/tasks)                     │
│                                                                     │
│  ┌───────────┐    ┌────────────────┐    ┌──────────────────────┐   │
│  │ RBIO Grid │───>│ Task Detail    │───>│ MRE Copilot Panel    │   │
│  │ Component │    │ (task-action)  │    │ (Maintainability)    │   │
│  └───────────┘    └────────────────┘    └──────────┬───────────┘   │
└───────────────────────────────────────────────────┬───────────────── │
                                                    │ HTTP GET
                                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    CMS BACKEND (Spring Boot 3.x)                    │
│                                                                     │
│  ┌─────────────────┐        ┌────────────────────────────────┐     │
│  │ CopilotController│──────>│ MaintainabilityCopilotService  │     │
│  │ /api/v1/copilot │        │  ┌──────────────────────────┐  │     │
│  │ /maintainability │        │  │ MaintainabilityRulesEngine│  │     │
│  │ /{complaintId}  │        │  │ (6 objective grounds)     │  │     │
│  └─────────────────┘        │  └──────────────────────────┘  │     │
│                             │  ┌──────────────────────────┐  │     │
│  ┌─────────────────┐        │  │ SimilarCasesService      │  │     │
│  │ MreController   │        │  │ (OpenSearch precedents)   │  │     │
│  │ /api/v1/mre     │        │  └──────────────────────────┘  │     │
│  │ /evaluate       │        │  ┌──────────────────────────┐  │     │
│  │ /config         │        │  │ CompensationPrecedent    │  │     │
│  └─────────────────┘        │  │ (Rs 30L/3L caps)         │  │     │
│                             │  └──────────────────────────┘  │     │
│                             └────────────────────────────────┘     │
└───────────────────────────────┬─────────────────────────────────────┘
                                │
           ┌────────────────────┼────────────────────┐
           ▼                    ▼                    ▼
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│  Oracle DB       │  │  OpenSearch      │  │  Keycloak        │
│  (MRE_* tables)  │  │  (complaint idx) │  │  (Auth/Roles)    │
│  (COMPLAINT_*)   │  │  (similar-cases) │  │  (RBIO_OFFICER)  │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```

### 3.2 MRE Evaluation Flow (Step-by-Step)

```
1. RBIO Officer clicks on complaint → Opens task-action component
2. Officer clicks "MRE Copilot — Maintainability Assessment" button
3. Frontend calls: GET /api/v1/copilot/maintainability/{complaintId}
4. CopilotController resolves complaint ID
5. MaintainabilityCopilotService.generateSuggestion():
   a. Loads Complaint entity from DB
   b. Calls IntakeTriageService.evaluateWithoutPersisting(complaint)
      → Builds ComplaintFacts from complaint entity
      → MaintainabilityRulesEngine.evaluate(facts):
         i.   evaluateEntityCoverage()    — Clause 1(3)
         ii.  evaluateNoPriorReComplaint() — Clause 10(1)(e)
         iii. evaluateFiledBeforeWindow()  — Clause 10(1)(f)
         iv.  evaluateFiledBeyondDeadline()— Clause 10(1)(g)
         v.   evaluateLimitationPeriod()   — Clause 10(1)(l)
         vi.  evaluateSameGrievancePending()— Clause 10(1)(h-k)
      → Returns MreVerdict with OverallSignal
   c. Finds precedent cases via OpenSearch
   d. Derives suggested determination
   e. Builds draft rationale text
   f. Gets compensation band (Clause 8(3) caps)
6. Returns CopilotResponse JSON to frontend
7. Frontend renders verdict, grounds, precedents, rationale
8. Officer reviews, edits rationale, confirms/overrides determination
9. POST /api/v1/copilot/maintainability/{id}/decision persists decision
```

### 3.3 MRE Ground Evaluation Logic

| Ground | Clause | Condition | Signal |
|--------|--------|-----------|--------|
| ENTITY_NOT_COVERED | 1(3) | Entity not in MRE_ENTITY_COVERAGE registry | FAIL |
| NO_PRIOR_RE_COMPLAINT | 10(1)(e) | `priorReComplaint == false` | FAIL |
| FILED_BEFORE_WINDOW | 10(1)(f) | `daysSinceReComplaint < windowDays` AND `reRepliedAndDissatisfied == false` | FAIL |
| FILED_BEYOND_DEADLINE | 10(1)(g) | `daysSinceWindowExpiry > 90` | FAIL |
| RE_COMPLAINT_BEYOND_LIMITATION | 10(1)(l) | `yearsSinceReComplaint > 3` | FAIL |
| SAME_GRIEVANCE_PENDING | 10(1)(h-k) | Duplicate found in system or court | FAIL |

### 3.4 Overall Signal Computation

```
IF any ground == FAIL → OBJECTIVELY_NON_MAINTAINABLE (RED)
ELSE IF any ground == NEEDS_REVIEW → NEEDS_HUMAN_REVIEW (AMBER)  
ELSE → OBJECTIVELY_CLEAR (GREEN)
```

---

## 4. Developer Guidelines

### 4.1 Project Structure

```
CMS2.0/
├── cms-backend/              # Monolith Spring Boot (main complaint CRUD + MRE)
├── cms-api-gateway/          # API Gateway (Spring Cloud Gateway)
├── cms-assignment-service/   # Round-robin assignment
├── cms-eligibility-service/  # Pre-filing eligibility check
├── cms-ingestion-service/    # Multi-channel intake (email, physical, portal)
├── cms-notification-service/ # SMS/Email notifications
├── cms-outbox-publisher/     # Transactional outbox → Kafka
├── cms-rules-service/        # Dynamic DRL rule management
├── cms-search-service/       # OpenSearch adapter
├── cms-sla-monitor-service/  # SLA deadline tracking
├── cms-storage-service/      # Attachment storage (S3/NFS)
├── cms-workflow-service/     # State machine / Camunda BPM
├── cms-portal-frontend/      # Angular 20 (RBIO/CEPC/CRPC officer portals)
├── cms-frontend/             # Public-facing citizen portal
├── cms-infrastructure/       # Shared infra configs
├── database/                 # Flyway migration scripts (V1-V4)
├── deployment/
│   ├── docker/               # docker-compose.yml for local dev
│   └── openshift/            # OpenShift deployment manifests
└── docs/                     # Documentation
```

### 4.2 Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Frontend (Staff) | Angular | 20.x |
| Frontend (Public) | Angular | 20.x |
| Backend | Spring Boot | 3.3.x |
| Database | Oracle XE/Enterprise | 21c |
| Search | OpenSearch | 2.18.0 |
| Auth/IAM | Keycloak | 26.0 |
| Messaging | Apache Kafka | 3.7.0 (KRaft) |
| Cache | Hazelcast | 5.4.x |
| Workflow | Camunda BPM | 7.21.x |
| Monitoring | Prometheus + Grafana | 2.54 / 11.3 |
| Container | Docker / Podman | 26.x / 4.x |
| Orchestrator | OpenShift | 4.14+ |

### 4.3 Building the Project

```bash
# Backend (from CMS2.0/ root)
mvn clean install -DskipTests

# Individual service
cd cms-backend && mvn spring-boot:run

# Frontend (Staff portal)
cd cms-portal-frontend && npm install && ng serve --port 4200

# Frontend (Public portal)
cd cms-frontend && npm install && ng serve --port 4201
```

### 4.4 Key API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/copilot/maintainability/{id}` | MRE Copilot assessment |
| POST | `/api/v1/copilot/maintainability/{id}/decision` | Record officer decision |
| POST | `/api/v1/mre/evaluate` | Raw MRE evaluation (no complaint lookup) |
| GET | `/api/v1/mre/config` | Current MRE configuration |
| GET | `/api/v1/workflow/rbio/all-tasks?officer={id}` | RBIO officer task grid |
| POST | `/api/v1/workflow/rbio/action/{complaintNumber}` | Execute workflow action |
| GET | `/api/v1/complaints/{complaintNumber}` | Get complaint detail |

### 4.5 Adding a New MRE Ground

1. Add enum value to `MreGround.java`
2. Add evaluation method in `MaintainabilityRulesEngine.java`
3. Call the new method in `evaluate()` 
4. Insert row into `MRE_GROUND_CONFIG` table
5. Insert DRL rule into `MRE_RULE_DEFINITION` table
6. Update frontend copilot panel if ground needs special rendering

### 4.6 Configuration Properties

```yaml
# application.yml
cms:
  mre:
    version: 2
    re-window-days: 30
    npci-window-days: 30
    card-network-window-days: 60
    filing-deadline-days: 90     # Clause 10(1)(g) — 90 days
    limitation-period-years: 3   # Limitation Act 1963
    window-basis: CALENDAR       # CALENDAR or BUSINESS
```

---

## 5. Infrastructure Setup for OpenShift Deployment

### 5.1 Namespace & Project

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: rbi-cms
  labels:
    app.kubernetes.io/part-of: rbi-cms
    environment: production
```

```bash
oc new-project rbi-cms --display-name="RBI CMS 2.0" --description="Complaint Management System"
```

### 5.2 ConfigMap

```yaml
# configmap-common.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: cms-common-config
  namespace: rbi-cms
data:
  DB_HOST: "oracle-db-service.rbi-cms.svc.cluster.local"
  DB_PORT: "1521"
  DB_SERVICE: "CMSDB"
  KAFKA_BOOTSTRAP_SERVERS: "kafka-service.rbi-cms.svc.cluster.local:9092"
  KEYCLOAK_ISSUER_URI: "http://keycloak-service.rbi-cms.svc.cluster.local:8180/realms/cms"
  KEYCLOAK_JWK_URI: "http://keycloak-service.rbi-cms.svc.cluster.local:8180/realms/cms/protocol/openid-connect/certs"
  OPENSEARCH_HOST: "opensearch-service.rbi-cms.svc.cluster.local"
  OPENSEARCH_PORT: "9200"
  OPENSEARCH_SCHEME: "http"
  STORAGE_ROOT: "/data/cms/storage"
  SMTP_HOST: "smtp-relay.rbi-cms.svc.cluster.local"
  SMTP_PORT: "587"
  MRE_FILING_DEADLINE_DAYS: "90"
  MRE_RE_WINDOW_DAYS: "30"
  MRE_LIMITATION_PERIOD_YEARS: "3"
```

### 5.3 Secrets

```yaml
# secret-db.yaml
apiVersion: v1
kind: Secret
metadata:
  name: cms-db-credentials
  namespace: rbi-cms
type: Opaque
stringData:
  DB_USER: "cms_app"
  DB_PASSWORD: "<PRODUCTION_PASSWORD>"
  SMTP_USER: "cms-notifications"
  SMTP_PASSWORD: "<PRODUCTION_PASSWORD>"
```

### 5.4 Deployment Manifests

```yaml
# deployment-cms-backend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cms-backend
  namespace: rbi-cms
  labels:
    app: cms-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: cms-backend
  template:
    metadata:
      labels:
        app: cms-backend
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
        - name: cms-backend
          image: registry.rbi.internal/cms/cms-backend:1.0.0
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: cms-common-config
            - secretRef:
                name: cms-db-credentials
          resources:
            requests:
              cpu: 500m
              memory: 1Gi
            limits:
              cpu: 2000m
              memory: 2Gi
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 90
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 45
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: cms-backend-service
  namespace: rbi-cms
spec:
  selector:
    app: cms-backend
  ports:
    - port: 8080
      targetPort: 8080
  type: ClusterIP
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: cms-backend-route
  namespace: rbi-cms
spec:
  host: cms-api.rbi.org.in
  to:
    kind: Service
    name: cms-backend-service
  port:
    targetPort: 8080
  tls:
    termination: edge
    insecureEdgeTerminationPolicy: Redirect
```

### 5.5 Apply Manifests

```bash
# Apply in order
oc apply -f deployment/openshift/namespace.yaml
oc apply -f deployment/openshift/configmap-common.yaml
oc apply -f deployment/openshift/secret-db.yaml
oc apply -f deployment/openshift/deployment-api-gateway.yaml
oc apply -f deployment/openshift/deployment-cms-backend.yaml
oc apply -f deployment/openshift/deployment-workflow-service.yaml
oc apply -f deployment/openshift/deployment-ingestion-service.yaml
oc apply -f deployment/openshift/deployment-eligibility-service.yaml
oc apply -f deployment/openshift/deployment-outbox-publisher.yaml
```

---

## 6. Operators to Install on OpenShift

### 6.1 Required Operators

| Operator | Purpose | Source |
|----------|---------|--------|
| **Red Hat AMQ Streams (Strimzi)** | Apache Kafka management | OperatorHub (Red Hat) |
| **OpenSearch Operator** | OpenSearch cluster lifecycle | OperatorHub (Community) |
| **Keycloak Operator (RHSSO)** | Identity & Access Management | OperatorHub (Red Hat) |
| **Prometheus Operator** | Metrics collection & alerting | Built into OCP monitoring stack |
| **Grafana Operator** | Dashboard provisioning | OperatorHub (Community) |
| **Oracle Database Operator** | Oracle DB on Kubernetes | Oracle Container Registry |
| **Cert-Manager** | TLS certificate automation | OperatorHub (Community) |
| **Web Terminal Operator** | In-cluster terminal access | OperatorHub (Red Hat) |
| **Red Hat Service Mesh (Istio)** | Service mesh (optional) | OperatorHub (Red Hat) |

### 6.2 Installation Commands

```bash
# 1. AMQ Streams (Kafka)
oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: amq-streams
  namespace: openshift-operators
spec:
  channel: stable
  name: amq-streams
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF

# 2. OpenSearch Operator
oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: opensearch-operator
  namespace: openshift-operators
spec:
  channel: stable
  name: opensearch-operator
  source: community-operators
  sourceNamespace: openshift-marketplace
EOF

# 3. Keycloak (RHSSO)
oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: rhsso-operator
  namespace: openshift-operators
spec:
  channel: stable
  name: rhsso-operator
  source: redhat-operators
  sourceNamespace: openshift-marketplace
EOF

# 4. Cert-Manager
oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: cert-manager
  namespace: openshift-operators
spec:
  channel: stable
  name: cert-manager
  source: community-operators
  sourceNamespace: openshift-marketplace
EOF

# 5. Grafana Operator
oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: grafana-operator
  namespace: openshift-operators
spec:
  channel: v5
  name: grafana-operator
  source: community-operators
  sourceNamespace: openshift-marketplace
EOF
```

### 6.3 Kafka Cluster CR (via AMQ Streams)

```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: cms-kafka
  namespace: rbi-cms
spec:
  kafka:
    version: 3.7.0
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
    storage:
      type: persistent-claim
      size: 50Gi
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 10Gi
```

---

## 7. Developer Local-to-Dev Environment Connectivity

### 7.1 Prerequisites

- **VPN**: Connected to RBI internal network (Cisco AnyConnect / FortiClient)
- **oc CLI**: OpenShift CLI installed (`choco install openshift-cli`)
- **kubectl**: Kubernetes CLI (`choco install kubernetes-cli`)
- **AWS CLI**: For SSO authentication (if using AWS-hosted OpenShift)

### 7.2 Login to OpenShift Dev Cluster

```bash
# Option 1: Token-based login (get token from OpenShift web console)
oc login https://api.ocp-dev.rbi.internal:6443 --token=<YOUR_TOKEN>

# Option 2: SSO login
oc login https://api.ocp-dev.rbi.internal:6443 --web

# Verify
oc whoami
oc project rbi-cms
```

### 7.3 Port-Forward Services to Local

```bash
# Forward Oracle DB (dev environment)
oc port-forward svc/oracle-db-service 1521:1521 -n rbi-cms &

# Forward Kafka
oc port-forward svc/kafka-service 9092:9092 -n rbi-cms &

# Forward Keycloak
oc port-forward svc/keycloak-service 8180:8180 -n rbi-cms &

# Forward OpenSearch
oc port-forward svc/opensearch-service 9200:9200 -n rbi-cms &
```

### 7.4 Local application.yml (connecting to dev via port-forward)

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/CMSDB
    username: cms_app
    password: <DEV_PASSWORD>
  kafka:
    bootstrap-servers: localhost:9092

spring.security.oauth2.resourceserver.jwt:
  issuer-uri: http://localhost:8180/realms/cms
  jwk-set-uri: http://localhost:8180/realms/cms/protocol/openid-connect/certs

cms:
  opensearch:
    host: localhost
    port: 9200
    scheme: http
  mre:
    version: 2
    re-window-days: 30
    filing-deadline-days: 90
    limitation-period-years: 3
    window-basis: CALENDAR
```

### 7.5 Remote Debugging

```bash
# Enable JPDA debug on a running pod
oc set env deployment/cms-backend JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Port-forward debug port
oc port-forward deployment/cms-backend 5005:5005 -n rbi-cms

# IntelliJ IDEA: Run → Edit Configurations → Remote JVM Debug → localhost:5005
```

### 7.6 Log Streaming

```bash
# Tail logs from a specific pod
oc logs -f deployment/cms-backend -n rbi-cms

# Stern (multi-pod log tailing)
stern cms-backend -n rbi-cms --tail 50
```

---

## 8. Local Docker Desktop & Application Setup

### 8.1 Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker Desktop | 4.30+ | `winget install Docker.DockerDesktop` |
| Java JDK | 21 (Temurin) | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Node.js | 22.x LTS | `winget install OpenJS.NodeJS.LTS` |
| Angular CLI | 20.x | `npm install -g @angular/cli` |
| Maven | 3.9+ | `winget install Apache.Maven` |
| Git | Latest | `winget install Git.Git` |

### 8.2 Docker Desktop Configuration

1. Open Docker Desktop → Settings → Resources
2. Set: **CPUs: 4+**, **Memory: 8 GB+**, **Disk: 60 GB+**
3. Enable WSL 2 Backend (Windows)
4. Enable Kubernetes (optional, for `kubectl` testing)

### 8.3 Start Infrastructure (docker-compose)

```bash
cd CMS2.0/deployment/docker

# Start all infrastructure services
docker compose up -d

# Verify all services are healthy
docker compose ps

# Expected services:
#   cms-oracle         → localhost:1521
#   cms-kafka          → localhost:9092
#   cms-keycloak       → localhost:8180
#   cms-opensearch     → localhost:9200
#   cms-kafka-ui       → localhost:8090
#   cms-prometheus     → localhost:9090
#   cms-grafana        → localhost:3000
#   cms-opensearch-dashboards → localhost:5601
```

### 8.4 Initialize Database

```bash
# Wait for Oracle to be ready (takes ~60s first time)
docker exec -it cms-oracle bash -c "
  sqlplus cms_app/cms_app_password@//localhost:1521/XEPDB1 <<EOF
  @/container-entrypoint-initdb.d/01_create_schemas.sql
  EXIT;
EOF
"

# Apply Flyway migrations
cd CMS2.0/cms-backend
mvn flyway:migrate -Dflyway.url=jdbc:oracle:thin:@localhost:1521/XEPDB1 \
  -Dflyway.user=cms_app -Dflyway.password=cms_app_password \
  -Dflyway.locations=filesystem:../database
```

### 8.5 Configure Keycloak

```bash
# Access Keycloak admin: http://localhost:8180
# Login: admin / admin
# Create realm: "cms"
# Create clients:
#   - cms-backend (confidential, service account)
#   - cms-portal (public, SPA)
# Create roles:
#   - RBIO_OFFICER, RBIO_SUPERVISOR, RBIO_CONCILIATOR, RBIO_ADJUDICATOR
#   - CEPC_DO, CEPC_REVIEWER, CEPC_INCHARGE, CEPC_CLOSING_AUTHORITY, CEPC_ADMIN
#   - CRPC_DEO
#   - ADMIN
# Create test users:
#   - rbio_officer_001 (role: RBIO_OFFICER, department: RBIO)
#   - rbio_officer_002 (role: RBIO_OFFICER, department: RBIO)
#   - cepc_do_001 (role: CEPC_DO, department: CEPC)
```

### 8.6 Run Backend

```bash
cd CMS2.0/cms-backend

# With local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or via IntelliJ:
#   Main class: com.hrms.cms.CmsApplication
#   Active profiles: local
#   VM options: -Xmx1g
```

### 8.7 Run Frontend (Staff Portal)

```bash
cd CMS2.0/cms-portal-frontend

npm install
ng serve --port 4200 --open

# Access: http://localhost:4200/staff/rbio/tasks
```

### 8.8 Verify MRE Assessment

```bash
# 1. Login as rbio_officer_002 at http://localhost:4200/staff/login
# 2. Navigate to RBIO Complaints grid
# 3. Click on a complaint to open detail view
# 4. Click "MRE Copilot — Maintainability Assessment"
# 5. Verify grounds are evaluated against RB-IOS 2026 clauses

# Direct API test:
curl -X POST http://localhost:8080/api/v1/mre/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "entityCode": "SBI",
    "entityType": "BANK",
    "categoryCode": "ATM_DEBIT",
    "priorReComplaint": true,
    "reComplaintDate": "2026-05-15",
    "reComplaintReference": "SBI/CMP/2026/001",
    "reRepliedAndDissatisfied": false,
    "filingDate": "2026-07-02",
    "sameGrievancePendingOrDecided": false
  }'
```

### 8.9 Troubleshooting

| Issue | Resolution |
|-------|-----------|
| Oracle not starting | Increase Docker memory to 8GB+ |
| Kafka consumer lag | Check `cms-kafka-ui` at localhost:8090 |
| Keycloak 401/403 | Verify realm name is "cms" and token not expired |
| MRE shows "Service unavailable" | Backend not running or Copilot endpoint throwing exception |
| OpenSearch connection refused | `docker compose restart opensearch`, wait 30s |
| Frontend CORS error | Backend must have `@CrossOrigin` or proxy config |

### 8.10 Stopping Everything

```bash
# Stop infrastructure
cd CMS2.0/deployment/docker
docker compose down

# Stop and remove volumes (fresh start)
docker compose down -v
```

---

## Appendix A: MRE Properties Reference (RB-IOS 2026 Mapping)

| Property | Value | RB-IOS 2026 Clause |
|----------|-------|-------------------|
| `cms.mre.re-window-days` | 30 | 10(1)(f) — "30 days" |
| `cms.mre.npci-window-days` | 30 | 10(1)(f) — "time specified by NPCI" |
| `cms.mre.card-network-window-days` | 60 | 10(1)(f) — "Card Network guidelines" |
| `cms.mre.filing-deadline-days` | 90 | 10(1)(g) — "within 90 days" |
| `cms.mre.limitation-period-years` | 3 | 10(1)(l) — Limitation Act 1963 |
| Compensation cap (consequential) | Rs 30,00,000 | 8(3) |
| Compensation cap (time/harassment) | Rs 3,00,000 | 8(3) |
| RE written response deadline | 15 days | 14(2) |
| Award acceptance period | 30 days | 15(4) |
| Appeal period | 30 days | 17(2)/17(3) |

## Appendix B: RBIO Officer Workflow States

```
PENDING → ASSIGNED → IN_PROGRESS → {ESCALATED, RESOLVED, REJECTED}
                                      ↓
                               CONCILIATION → {CONCILIATED, ADJUDICATION}
                                                            ↓
                                                     ADJUDICATED (Award)
```

## Appendix C: Known Issue — MRE "Service unavailable"

The "UNABLE_TO_ASSESS" / "Service unavailable" shown in the screenshot indicates the Copilot endpoint returned an error. Root causes:

1. **SimilarCasesService** fails if OpenSearch is unreachable
2. **IntakeTriageService.evaluateWithoutPersisting()** throws NPE if complaint lacks required fields
3. **CompensationPrecedentService** fails if no precedent index exists

**Fix:** Ensure OpenSearch is running and the `cms-complaints` index is created. Verify complaint has `entityCode`, `registeredAt`, and `priorReComplaint` fields populated.
