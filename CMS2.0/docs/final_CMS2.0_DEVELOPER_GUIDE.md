# RBI CMS 2.0 - Final Developer Guidance Document

## Version: 2.0.0 | Date: August 2026 | RB-IOS 2026 Scheme Compliant

---

## Table of Contents

1. [System Architecture](#1-system-architecture)
2. [Feature 1: Eligibility Wizard & Question Engine](#2-feature-1-eligibility-wizard--question-engine)
3. [Feature 2: Complaint Filing & Lifecycle](#3-feature-2-complaint-filing--lifecycle)
4. [Feature 3: Complaint Number Generation](#4-feature-3-complaint-number-generation)
5. [Feature 4: Threshold-Based Overflow Routing](#5-feature-4-threshold-based-overflow-routing)
6. [Feature 5: MRE Rules Engine (Maintainability)](#6-feature-5-mre-rules-engine-maintainability)
7. [Feature 6: Department Routing (RBIO/CEPC/CRPC)](#7-feature-6-department-routing-rbiocepcrpc)
8. [Feature 7: Prior RE Complaint Validation](#8-feature-7-prior-re-complaint-validation)
9. [Feature 8: Complaint Closure & Closure Letters](#9-feature-8-complaint-closure--closure-letters)
10. [Feature 9: Transactional Outbox (Kafka)](#10-feature-9-transactional-outbox-kafka)
11. [Feature 10: Audit Trail & History](#11-feature-10-audit-trail--history)
12. [Oracle Init Scripts](#12-oracle-init-scripts)
13. [Incremental Migration Scripts](#13-incremental-migration-scripts)

---

## 1. System Architecture

### 1.1 High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         PUBLIC USERS / COMPLAINANTS                           │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ HTTPS
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  FRONTENDS (Angular 21 + PrimeNG 21)                                         │
│  ┌─────────────────────────────────┐  ┌──────────────────────────────────┐   │
│  │ cms-portal-frontend (Port 4200) │  │ cms-frontend (Port 4201)         │   │
│  │ Public filing + Staff SSO       │  │ RBIO Officer portal              │   │
│  └─────────────────────────────────┘  └──────────────────────────────────┘   │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │ /api/*
                                 ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│  cms-api-gateway (Port 8080) — Spring Cloud Gateway                          │
│  Rate limiting, JWT validation, request routing                              │
└────────────────────────────────┬─────────────────────────────────────────────┘
                                 │
        ┌────────────────────────┼──────────────────────────┐
        ▼                        ▼                          ▼
┌───────────────┐  ┌──────────────────────────┐  ┌─────────────────────────┐
│ cms-backend   │  │ Microservices            │  │ Infrastructure          │
│ (Port 8082)   │  │                          │  │                         │
│               │  │ eligibility    : 8081    │  │ Oracle DB (1521)        │
│ Monolith:     │  │ ingestion      : 8082    │  │ Kafka (9092)            │
│ - Filing      │  │ workflow       : 8083    │  │ Keycloak 26 (9090)      │
│ - Routing     │  │ rules-engine   : 8084    │  │ OpenSearch (9200)       │
│ - MRE         │  │ assignment     : 8085    │  │ Redis (6379)            │
│ - Threshold   │  │ sla-monitor    : 8086    │  │                         │
│ - Closure     │  │ notification   : 8087    │  │                         │
│ - Timeline    │  │ audit          : 8088    │  │                         │
│ - Dashboard   │  │ outbox-pub     : 8089    │  │                         │
│               │  │ storage        : 8090    │  │                         │
│               │  │ search         : 8091    │  │                         │
│               │  │ paddle-ocr     : 5000    │  │                         │
└───────────────┘  └──────────────────────────┘  └─────────────────────────┘
```

### 1.2 Tech Stack

| Layer       | Technology                                    |
|------------|-----------------------------------------------|
| Frontend   | Angular 21, PrimeNG 21, keycloak-js 26, SCSS |
| Backend    | Java 21, Spring Boot 3.4, Spring Security     |
| Database   | Oracle 19c (prod), MySQL 8 (dev-local)        |
| Messaging  | Apache Kafka + Transactional Outbox            |
| Auth       | Keycloak 26 (OIDC/PKCE, realm: rbi-cms)       |
| Search     | OpenSearch 2.18                                |
| Rules      | Drools 8 (DRL-based)                           |
| OCR        | PaddleOCR (Python)                             |
| Cache      | Hazelcast (embedded)                           |
| Deployment | OpenShift 4.x                                  |

### 1.3 Keycloak Roles

| Department | Roles                                                                    |
|-----------|--------------------------------------------------------------------------|
| CEPC      | `DO`, `REVIEWER`, `INCHARGE`, `CA`, `ADMIN`, `CP`                       |
| RBIO      | `RBIO_OFFICER`, `RBIO_SUPERVISOR`, `RBIO_CONCILIATOR`, `RBIO_ADJUDICATOR`, `RBIO_ADMIN` |
| RE        | `RE_NODAL_OFFICER`, `RE_PNO`                                            |
| AA        | `AA_REGISTRAR`, `AA_BENCH_OFFICER`, `AA_AUTHORITY`, `AA_ADMIN`          |
| CRPC      | `DEO`, `REVIEWER`                                                        |

---

## 2. Feature 1: Eligibility Wizard & Question Engine

### 2.1 Purpose

Before filing a complaint, the complainant must pass an eligibility check based on RB-IOS 2026 grounds. The system evaluates answers against configurable rules.

### 2.2 Flow Diagram

```
┌──────────────┐     ┌────────────────────────┐     ┌──────────────────────┐
│ Complainant  │────▶│ Eligibility Wizard UI  │────▶│ Question Master API  │
│ (Browser)    │     │ Step-by-step questions │     │ GET /api/questions   │
└──────────────┘     └────────────────────────┘     └──────────┬───────────┘
                                                               │
                     ┌────────────────────────┐                │ Returns active
                     │ Submit answers         │◀───────────────┘ questions by
                     │ POST /api/eligibility  │                   display order
                     └────────────┬───────────┘
                                  │
                                  ▼
                     ┌────────────────────────┐
                     │ EligibilityService     │
                     │ - Evaluate rules       │
                     │ - Check expected_answer│
                     │ - Determine outcome    │
                     └────────────┬───────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    ▼                            ▼
          ┌──────────────┐            ┌──────────────────┐
          │ ELIGIBLE     │            │ NOT ELIGIBLE     │
          │ → Proceed to │            │ → Show reason    │
          │   filing form│            │ → Log to audit   │
          └──────────────┘            └──────────────────┘
                                               │
                                               ▼
                                    ┌──────────────────────┐
                                    │ ELIGIBILITY_AUDIT    │
                                    │ (immutable record)   │
                                    └──────────────────────┘
```

### 2.3 Key Tables

| Table              | Purpose                                      |
|-------------------|----------------------------------------------|
| `QUESTION_MASTER` | Configurable questions with expected answers |
| `ELIGIBILITY_AUDIT` | Immutable record of every check            |

### 2.4 Key Rules (Default Questions)

| Code              | Question                                              | Expected |
|-------------------|-------------------------------------------------------|----------|
| Q_COURT_MATTER    | Is the matter pending in any court/tribunal?          | NO       |
| Q_APPROACHED_BANK | Have you approached your bank?                        | YES      |
| Q_WAITING_PERIOD  | Has 30 days elapsed since you approached the bank?    | YES      |
| Q_DUPLICATE       | Have you already filed with RBI for the same issue?   | NO       |

---

## 3. Feature 2: Complaint Filing & Lifecycle

### 3.1 Purpose

Full complaint lifecycle: filing → routing → processing → resolution → closure. Supports web portal, email, and physical letter intake channels.

### 3.2 Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        COMPLAINT INTAKE CHANNELS                             │
│                                                                             │
│  ┌─────────────┐   ┌───────────────┐   ┌──────────────────┐               │
│  │ Web Portal  │   │ Email (OCR)   │   │ Physical Letter  │               │
│  │ WEB_PORTAL  │   │ EMAIL         │   │ PHYSICAL_LETTER  │               │
│  └──────┬──────┘   └──────┬────────┘   └────────┬─────────┘               │
└─────────┼──────────────────┼─────────────────────┼──────────────────────────┘
          │                  │                     │
          ▼                  ▼                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│  ComplaintService.fileComplaint()                                            │
│                                                                             │
│  1. Validate prior-RE fields                                                │
│  2. Resolve department (RBIO/CEPC) from entity name                         │
│  3. Generate complaint number (N + FY + OfficeCode + Seq)                   │
│  4. Route to assigned officer (round-robin)                                 │
│  5. Save complaint                                                          │
│  6. Add timeline entry                                                      │
│  7. Publish event to Kafka (via outbox)                                     │
└──────────────────────────────────┬──────────────────────────────────────────┘
                                   │
                                   ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                       COMPLAINT STATUS LIFECYCLE                             │
│                                                                             │
│  PENDING ──▶ IN_PROGRESS ──▶ RESOLVED ──▶ CLOSED                           │
│     │              │              │                                          │
│     │              ▼              │                                          │
│     │         ESCALATED ──────────┘                                          │
│     │                                                                       │
│     └──▶ REJECTED (non-maintainable)                                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.3 Key Tables

| Table                  | Purpose                                   |
|-----------------------|-------------------------------------------|
| `COMPLAINT_MASTER`    | Primary complaint entity                  |
| `COMPLAINT_HISTORY`   | State transition history                  |
| `ATTACHMENT_METADATA` | File attachments                          |
| `COMPLAINTS` (dev)    | Simplified complaint table (MySQL dev)    |

---

## 4. Feature 3: Complaint Number Generation

### 4.1 Format

```
N + FY(6) + OfficeCode(3) + Sequence(6)
Example: N202627013000001
```

| Segment    | Length | Example  | Description                     |
|-----------|--------|----------|---------------------------------|
| Prefix    | 1      | N        | Fixed                           |
| FY        | 6      | 202627   | Financial year (Apr-Mar cycle)  |
| OfficeCode| 3      | 013      | 3-digit code from master table  |
| Sequence  | 6      | 000001   | Zero-padded per office per FY   |

### 4.2 Financial Year Logic

```
Month >= 4 (Apr-Mar):  FY = currentYear + (currentYear+1)%100
                       Aug 2026 → "202627"
Month < 4 (Jan-Mar):  FY = (currentYear-1) + currentYear%100
                       Jan 2027 → "202627"
```

### 4.3 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ComplaintNumberGeneratorService                                              │
│                                                                              │
│  INPUT: department, complainantState, complainantDistrict, isVernacularOrCrpc│
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │ STEP 1: resolveOfficeName(department, state, district)              │     │
│  │                                                                     │     │
│  │  ┌────────────────────────────────────────────────────────────┐    │     │
│  │  │ OMBUDSMAN_OFFICE_MASTER (24 offices)                       │    │     │
│  │  │                                                            │    │     │
│  │  │ 1. Search district in JURISDICTION text                    │    │     │
│  │  │    (handles Mumbai-I/II, Kolkata-I/II, Chennai-I/II)       │    │     │
│  │  │                                                            │    │     │
│  │  │ 2. If no unique match → search by state name              │    │     │
│  │  │                                                            │    │     │
│  │  │ 3. Multiple matches → parse exclusion clauses             │    │     │
│  │  │    "excluding..." / "except..." logic                      │    │     │
│  │  │    (handles UP split: Dehradun/Kanpur/New Delhi-II)        │    │     │
│  │  │                                                            │    │     │
│  │  │ 4. Fallback → "New Delhi I" (code 014)                    │    │     │
│  │  └────────────────────────────────────────────────────────────┘    │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │ STEP 2: resolveOfficeCode(officeName)                               │     │
│  │                                                                     │     │
│  │  OFFICE_CODE_MASTER: name → 3-digit code                           │     │
│  │  Normalization: "Mumbai-I" → "Mumbai I" (hyphen→space fallback)    │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │ STEP 3: applyThresholdOverflow(officeCode)                          │     │
│  │         [SKIP if isVernacularOrCrpc = true]                         │     │
│  │                                                                     │     │
│  │  See Feature 4 below for detailed algorithm                         │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐     │
│  │ STEP 4: getNextSequence(assignedOfficeCode, financialYear)          │     │
│  │                                                                     │     │
│  │  COMPLAINT_NUMBER_SEQUENCE table                                    │     │
│  │  - PESSIMISTIC_WRITE lock (thread-safe)                             │     │
│  │  - Atomic increment of LAST_SEQUENCE                                │     │
│  │  - New row created if first for office+FY                           │     │
│  └─────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  OUTPUT: String.format("N%s%s%06d", FY, officeCode, sequence)                │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 4.4 Key Tables

| Table                        | Purpose                                |
|-----------------------------|----------------------------------------|
| `OMBUDSMAN_OFFICE_MASTER`   | 24 offices → jurisdiction mapping      |
| `OFFICE_CODE_MASTER`        | Office name → 3-digit code + counter   |
| `COMPLAINT_NUMBER_SEQUENCE` | Atomic sequence per office+FY          |

---

## 5. Feature 4: Threshold-Based Overflow Routing

### 5.1 Purpose

Distributes complaint workload evenly across offices within regional clusters. When one office reaches its configurable threshold, complaints overflow to priority offices.

### 5.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  applyThresholdOverflow(targetOfficeCode)                                    │
│                                                                              │
│  ┌─────────────────────────────────────────┐                                │
│  │ Read global threshold from              │                                │
│  │ OFFICE_GLOBAL_THRESHOLD_CONFIG (id=1)   │                                │
│  │ Default: 2                              │                                │
│  └───────────────────┬─────────────────────┘                                │
│                      │                                                       │
│                      ▼                                                       │
│  ┌─────────────────────────────────────────┐                                │
│  │ Lock target office (PESSIMISTIC_WRITE)  │                                │
│  │ Read COUNTER from OFFICE_CODE_MASTER    │                                │
│  └───────────────────┬─────────────────────┘                                │
│                      │                                                       │
│            ┌─────────┴──────────┐                                           │
│            │ counter < threshold?│                                           │
│            └─────────┬──────────┘                                           │
│              YES ┌───┘└───┐ NO                                              │
│                  ▼        ▼                                                  │
│  ┌──────────────────┐  ┌────────────────────────────────────────────────┐   │
│  │ counter++        │  │ Look up OFFICE_OVERFLOW_MAPPING               │   │
│  │ RETURN target    │  │ for this office code                          │   │
│  └──────────────────┘  └───────────────────┬────────────────────────────┘   │
│                                            │                                │
│                                 ┌──────────┴──────────┐                     │
│                                 │ Priority 1 counter  │                     │
│                                 │ < threshold?        │                     │
│                                 └──────────┬──────────┘                     │
│                                   YES ┌────┘└────┐ NO                       │
│                                       ▼          ▼                          │
│                        ┌──────────────────┐  ┌────────────────────────┐     │
│                        │ p1.counter++     │  │ Priority 2 counter    │     │
│                        │ RETURN priority1 │  │ < threshold?          │     │
│                        └──────────────────┘  └──────────┬─────────────┘     │
│                                                YES ┌────┘└────┐ NO          │
│                                                    ▼          ▼             │
│                                     ┌──────────────────┐  ┌───────────────┐ │
│                                     │ p2.counter++     │  │ ALL 3 MET!    │ │
│                                     │ RETURN priority2 │  │               │ │
│                                     └──────────────────┘  │ RESET all 3   │ │
│                                                           │ counters to 0 │ │
│                                                           │ target.ctr =1 │ │
│                                                           │ RETURN target │ │
│                                                           └───────────────┘ │
└──────────────────────────────────────────────────────────────────────────────┘

EXCEPTION: Vernacular (EMAIL) and CRPC (PHYSICAL_LETTER) complaints
           bypass this logic entirely — direct assignment, no counter impact.
```

### 5.3 Overflow Groups (4 Regional Clusters)

**Eastern Region:**
| Office       | Code | Priority 1  | Priority 2  |
|-------------|------|-------------|-------------|
| Bhubaneswar | 003  | Kolkata-II  | Patna       |
| Guwahati    | 008  | Bhubaneswar | Kolkata-I   |
| Kolkata-I   | 005  | Ranchi      | Kolkata-II  |
| Kolkata-II  | 022  | Kolkata-I   | Guwahati    |
| Patna       | 012  | Guwahati    | Ranchi      |
| Ranchi      | 018  | Patna       | Bhubaneswar |

**Northern Region:**
| Office       | Code | Priority 1   | Priority 2   |
|-------------|------|--------------|--------------|
| Chandigarh  | 007  | Dehradun     | New Delhi-I  |
| Dehradun    | 017  | Chandigarh   | Kanpur       |
| Jaipur      | 010  | Kanpur       | Chandigarh   |
| Jammu       | 020  | New Delhi-II | Jaipur       |
| Kanpur      | 011  | Jaipur       | Dehradun     |
| New Delhi-I | 014  | Shimla       | New Delhi-II |
| New Delhi-II| 016  | Jammu        | Shimla       |
| Shimla      | 023  | New Delhi-I  | Jammu        |

**Southern Region:**
| Office              | Code | Priority 1          | Priority 2          |
|--------------------|------|---------------------|---------------------|
| Bengaluru          | 002  | Thiruvananthapuram  | Hyderabad           |
| Chennai-I          | 006  | Chennai-II          | Bengaluru           |
| Chennai-II         | 024  | Hyderabad           | Chennai-I           |
| Hyderabad          | 009  | Chennai-I           | Thiruvananthapuram  |
| Thiruvananthapuram | 015  | Bengaluru           | Chennai-II          |

**Western Region:**
| Office     | Code | Priority 1 | Priority 2 |
|-----------|------|------------|------------|
| Ahmedabad | 001  | Raipur     | Mumbai-I   |
| Bhopal    | 004  | Ahmedabad  | Mumbai-II  |
| Mumbai-I  | 013  | Mumbai-II  | Bhopal     |
| Mumbai-II | 021  | Mumbai-I   | Raipur     |
| Raipur    | 019  | Bhopal     | Ahmedabad  |

### 5.4 Key Tables

| Table                            | Purpose                                  |
|---------------------------------|------------------------------------------|
| `OFFICE_CODE_MASTER`            | COUNTER + THRESHOLD per office           |
| `OFFICE_OVERFLOW_MAPPING`       | Priority 1 & 2 overflow destinations     |
| `OFFICE_GLOBAL_THRESHOLD_CONFIG`| Single-row global threshold (Super Admin)|

### 5.5 Admin Configuration

```sql
-- Change global threshold (Super Admin)
UPDATE OFFICE_GLOBAL_THRESHOLD_CONFIG SET THRESHOLD_VALUE = 5, UPDATED_BY = 'admin' WHERE ID = 1;

-- Reset all office counters (new cycle)
UPDATE OFFICE_CODE_MASTER SET COUNTER = 0;
```

---

## 6. Feature 5: MRE Rules Engine (Maintainability)

### 6.1 Purpose

Automated assessment of complaint maintainability per RB-IOS 2026 clauses Q13, Q16, Q17. Uses Drools (DRL) rule definitions stored in the database with maker-checker workflow.

### 6.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  MRE EVALUATION FLOW                                                         │
│                                                                              │
│  ┌──────────────────┐                                                        │
│  │ Complaint Filed  │                                                        │
│  └────────┬─────────┘                                                        │
│           │                                                                  │
│           ▼                                                                  │
│  ┌────────────────────────────────────────────────────────────┐              │
│  │ MRE Ground Evaluation (ordered by EVALUATION_ORDER)        │              │
│  │                                                            │              │
│  │  Ground 1: ENTITY_NOT_COVERED (Q13)                        │              │
│  │  → Is entity covered under RB-IOS 2026 Scheme?            │              │
│  │                                                            │              │
│  │  Ground 2: NO_PRIOR_RE_COMPLAINT (Q16)                     │              │
│  │  → Has complainant first approached the RE?                │              │
│  │                                                            │              │
│  │  Ground 3: FILED_BEFORE_WINDOW (Q17)                       │              │
│  │  → Has 30-day window elapsed? (or RE replied)              │              │
│  │                                                            │              │
│  │  Ground 4: FILED_BEYOND_DEADLINE (Q16/Q17)                 │              │
│  │  → Filed within 365 days of window expiry?                 │              │
│  │                                                            │              │
│  │  Ground 5: RE_COMPLAINT_BEYOND_LIMITATION (Q16)            │              │
│  │  → RE complaint within Limitation Act 1963 (3 years)?      │              │
│  │                                                            │              │
│  │  Ground 6: SAME_GRIEVANCE_PENDING (Q16)                    │              │
│  │  → Duplicate check with pending/decided cases              │              │
│  └────────────────────────────────────────────────────────────┘              │
│           │                                                                  │
│           ▼                                                                  │
│  ┌────────────────────────────────────────────────────────────┐              │
│  │ DETERMINATION                                              │              │
│  │                                                            │              │
│  │  ALL grounds PASS → MAINTAINABLE                           │              │
│  │  ANY ground FAIL  → NON_MAINTAINABLE (with reasons)        │              │
│  │  TRIAGE_SIGNAL: GREEN (clear pass) / RED (clear fail)      │              │
│  │                  AMBER (requires officer judgment)          │              │
│  └────────────────────────────────────────────────────────────┘              │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 Rule Management (Maker-Checker)

```
DRAFT ──▶ PENDING_REVIEW ──▶ ACTIVE ──▶ INACTIVE ──▶ ARCHIVED
  │              │
  │  (Rejected)  │
  └──────────────┘
```

### 6.4 Key Tables

| Table                  | Purpose                                           |
|-----------------------|---------------------------------------------------|
| `MRE_RULE_CATEGORY`  | Rule categories (Assignment, Escalation, MRE...) |
| `MRE_RULE_DEFINITION`| DRL content, version, status, maker-checker       |
| `MRE_RULE_HISTORY`   | Immutable change audit trail                      |
| `MRE_DEPLOYMENT`     | Rule deployment tracking                          |
| `MRE_GROUND_CONFIG`  | Configurable MRE grounds (Q13/Q16/Q17)           |
| `MRE_WINDOW_CONFIG`  | Window days per category/entity type              |

### 6.5 Window Configuration

| Category     | Window (days) | Filing Deadline | Basis     |
|-------------|---------------|-----------------|-----------|
| DEFAULT     | 30            | 365             | Calendar  |
| CREDIT_CARD | 60            | 365             | Calendar  |
| DEBIT_CARD  | 60            | 365             | Calendar  |
| ATM_DEBIT   | 30            | 365             | Calendar  |
| UPI_MOBILE  | 30            | 365             | Calendar  |

---

## 7. Feature 6: Department Routing (RBIO/CEPC/CRPC)

### 7.1 Purpose

Routes complaints to the correct department based on the Regulated Entity and filing channel.

### 7.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  ROUTING DECISION                                                            │
│                                                                              │
│  ┌────────────────────────┐                                                  │
│  │ Filing Channel?        │                                                  │
│  └───────────┬────────────┘                                                  │
│              │                                                               │
│    ┌─────────┼─────────────────────┐                                        │
│    ▼         ▼                     ▼                                        │
│  WEB_PORTAL  EMAIL          PHYSICAL_LETTER                                  │
│    │          │                     │                                        │
│    │          └──────────┬──────────┘                                        │
│    │                     │                                                   │
│    ▼                     ▼                                                   │
│  ┌──────────────┐   ┌──────────────────────────────────────────────────┐    │
│  │ RBIO Direct  │   │ CRPC Flow                                        │    │
│  │ Round-robin  │   │                                                  │    │
│  │ → RBIO_OFFICER│   │ DEO (Data Entry) → REVIEWER → Target Dept      │    │
│  └──────────────┘   │ Target determined by entity name matching:       │    │
│                     │                                                  │    │
│                     │ REGULATED_ENTITY table lookup:                    │    │
│                     │ • Exact match on normalized name                  │    │
│                     │ • Fuzzy match (contains)                          │    │
│                     │ • Not found → default RBIO                        │    │
│                     └──────────────────────────────────────────────────┘    │
│                                                                              │
│  INTER-DEPARTMENT TRANSFER:                                                  │
│  Officer can transfer complaint between RBIO ↔ CEPC with reason             │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 7.3 Assignment Rules (DRL)

| Rule Code | Trigger                                | Department |
|-----------|----------------------------------------|------------|
| ASGN-001  | ATM/Debit Card category                | RBIO       |
| ASGN-002  | UPI/IMPS/NEFT/NACH category            | CEPC       |
| ASGN-003  | Credit Card category                   | CEPC       |
| ASGN-004  | Loan/Insurance/Deposit category        | RBIO       |
| ASGN-005  | Uncategorized (default)                | CRPC       |

---

## 8. Feature 7: Prior RE Complaint Validation

### 8.1 Purpose

RB-IOS 2026 mandates that the complainant must first approach the Regulated Entity before filing with RBI. Validates Q16/Q17 compliance.

### 8.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  PRIOR RE COMPLAINT VALIDATION                                               │
│                                                                              │
│  ┌──────────────────────────┐                                                │
│  │ priorReComplaint = TRUE? │                                                │
│  └──────────────┬───────────┘                                                │
│          YES    │    NO → Non-maintainable (Q16)                             │
│                 ▼                                                             │
│  ┌──────────────────────────┐                                                │
│  │ reComplaintDate provided?│                                                │
│  │ (mandatory if prior=YES) │                                                │
│  └──────────────┬───────────┘                                                │
│                 ▼                                                             │
│  ┌──────────────────────────┐                                                │
│  │ reComplaintDate ≤ today? │                                                │
│  │ (cannot be in future)    │                                                │
│  └──────────────┬───────────┘                                                │
│                 ▼                                                             │
│  ┌──────────────────────────────────────────────────────┐                    │
│  │ WINDOW CHECK:                                         │                    │
│  │                                                       │                    │
│  │ IF reRepliedAndDissatisfied = TRUE:                   │                    │
│  │   → SKIP window check (complainant can file now)      │                    │
│  │                                                       │                    │
│  │ ELSE:                                                 │                    │
│  │   days_elapsed = today - reComplaintDate              │                    │
│  │   IF days_elapsed < window_days (30/60):              │                    │
│  │     → Non-maintainable (Q17 - window not elapsed)     │                    │
│  │   IF days_elapsed > 365 + window_days:                │                    │
│  │     → Non-maintainable (filed beyond deadline)        │                    │
│  └──────────────────────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Fields Added to COMPLAINTS Table

| Column                        | Type         | Purpose                               |
|------------------------------|--------------|---------------------------------------|
| `PRIOR_RE_COMPLAINT`         | BOOLEAN      | Whether complainant approached RE     |
| `RE_COMPLAINT_DATE`          | DATE         | Date of prior RE complaint            |
| `RE_COMPLAINT_REFERENCE`     | VARCHAR(200) | RE acknowledgement number             |
| `RE_REPLIED_AND_DISSATISFIED`| BOOLEAN      | RE replied but unsatisfactory         |

---

## 9. Feature 8: Complaint Closure & Closure Letters

### 9.1 Purpose

Formal closure with configurable closure clauses, authority sign-off, and auto-generated closure letters.

### 9.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  CLOSURE FLOW                                                                │
│                                                                              │
│  ┌────────────────────┐                                                      │
│  │ Resolution Reached │                                                      │
│  │ status = RESOLVED  │                                                      │
│  └─────────┬──────────┘                                                      │
│            │                                                                 │
│            ▼                                                                 │
│  ┌──────────────────────────────────────────────┐                            │
│  │ Closure Decision by Authority                 │                            │
│  │ - Select closure clause                       │                            │
│  │ - Enter custom closure text                   │                            │
│  │ - Authority name + designation                │                            │
│  └─────────┬────────────────────────────────────┘                            │
│            │                                                                 │
│            ▼                                                                 │
│  ┌──────────────────────────────────────────────┐                            │
│  │ Generate Closure Letter                       │                            │
│  │ - Template-based with scheme version          │                            │
│  │ - Include closure clause reference            │                            │
│  │ - Authority signature                         │                            │
│  └─────────┬────────────────────────────────────┘                            │
│            │                                                                 │
│            ▼                                                                 │
│  ┌──────────────────────────────────────────────┐                            │
│  │ Send Notification (SMS + Email)               │                            │
│  │ Set CLOSURE_LETTER_SENT_AT timestamp          │                            │
│  │ status = CLOSED                               │                            │
│  └──────────────────────────────────────────────┘                            │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 9.3 Fields Added

| Column                          | Type           | Purpose                      |
|--------------------------------|----------------|------------------------------|
| `CUSTOM_CLOSURE_TEXT`          | VARCHAR2(2000) | Officer-written closure text |
| `CLOSURE_LETTER_SENT_AT`      | TIMESTAMP      | When letter was dispatched   |
| `CLOSURE_CLAUSE`              | VARCHAR2(100)  | RB-IOS clause reference      |
| `CLOSURE_AUTHORITY_NAME`      | VARCHAR2(200)  | Signing authority name       |
| `CLOSURE_AUTHORITY_DESIGNATION`| VARCHAR2(200) | Signing authority title      |
| `RE_RESPONSE_DEADLINE`        | DATE           | RE response due date         |
| `LAST_STATUS_CHANGE_DATE`     | TIMESTAMP      | Last status transition time  |

---

## 10. Feature 9: Transactional Outbox (Kafka)

### 10.1 Purpose

Ensures reliable event publishing to Kafka using the transactional outbox pattern. Prevents message loss on service failures.

### 10.2 Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  OUTBOX PATTERN                                                              │
│                                                                              │
│  ┌──────────────────┐     ┌────────────────────────┐                        │
│  │ Service writes   │────▶│ OUTBOX_EVENT table     │                        │
│  │ complaint +      │     │ status = PENDING       │                        │
│  │ outbox event     │     │ (same DB transaction)  │                        │
│  │ in SAME TX       │     └──────────┬─────────────┘                        │
│  └──────────────────┘                │                                      │
│                                      │ Poll every 5s                        │
│                                      ▼                                      │
│  ┌──────────────────────────────────────────────────────────────────┐       │
│  │ cms-outbox-publisher (Port 8089)                                  │       │
│  │                                                                   │       │
│  │ 1. SELECT PENDING events (batch)                                  │       │
│  │ 2. Publish to Kafka topic                                         │       │
│  │ 3. Mark as PUBLISHED (set published_at)                           │       │
│  │ 4. On failure: increment retry_count (max 5)                      │       │
│  │ 5. After max retries: move to DLQ topic                           │       │
│  └──────────────────────────────────────────────────────────────────┘       │
│                                                                              │
│  KAFKA TOPICS:                                                               │
│  ┌────────────────────────────────────────────────────────────────────┐      │
│  │ complaint.ingested  │ complaint.assigned   │ complaint.inprogress  │      │
│  │ complaint.escalated │ complaint.resolved   │ complaint.closed      │      │
│  │ complaint.dlq (dead letter)                                        │      │
│  └────────────────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 10.3 Key Table

| Table          | Key Columns                                               |
|---------------|-----------------------------------------------------------|
| `OUTBOX_EVENT`| AGGREGATE_ID, EVENT_TYPE, TOPIC, PAYLOAD, STATUS, RETRY_COUNT |

---

## 11. Feature 10: Audit Trail & History

### 11.1 Purpose

Complete audit trail for all operations. Immutable log of every action performed on any entity.

### 11.2 Tables

| Table               | Purpose                                   |
|--------------------|-------------------------------------------|
| `AUDIT_LOG`        | System-wide audit of all operations       |
| `COMPLAINT_HISTORY`| Complaint-specific state transitions      |

### 11.3 Indexes for Performance

- `IDX_AUDIT_ENTITY(ENTITY_TYPE, ENTITY_ID)` — entity-based queries
- `IDX_AUDIT_USER(PERFORMED_BY, CREATED_AT)` — user activity queries
- `IDX_AUDIT_DATE(CREATED_AT)` — time-range queries

---

## 12. Oracle Init Scripts

### 12.1 Incremental Scripts (execute in order)

| Script | Purpose | Phase |
|--------|---------|-------|
| `V1__initial_schema.sql` | Core tables (COMPLAINT_MASTER, QUESTION_MASTER, ELIGIBILITY_AUDIT, OUTBOX_EVENT, AUDIT_LOG, SHEDLOCK) | 1 |
| `V2__phase1_prior_re_complaint_fields.sql` | Prior-RE columns, triage, maintainability, RE_RESPONSE_TRACKER | 1 |
| `V3__mre_rules_tables.sql` | MRE_RULE_CATEGORY, MRE_RULE_DEFINITION, MRE_RULE_HISTORY, MRE_DEPLOYMENT, MRE_GROUND_CONFIG, MRE_WINDOW_CONFIG | 4 |
| `V4__mre_rules_seed_data.sql` | Seed: 8 rule categories, 22 DRL rules, 6 MRE grounds, 11 window configs | 4 |
| `V5__ombudsman_office_master.sql` | OMBUDSMAN_OFFICE_MASTER + 24 offices seed | 5 |
| `V6__office_code_master.sql` | OFFICE_CODE_MASTER + 25 office codes seed | 5 |
| `V7__complaint_number_sequence.sql` | COMPLAINT_NUMBER_SEQUENCE | 5 |
| `V8__office_threshold_overflow.sql` | OFFICE_OVERFLOW_MAPPING, OFFICE_GLOBAL_THRESHOLD_CONFIG, COUNTER column | 5 |
| `oracle/V6__alter_tables_new_columns.sql` | Closure/tracking columns on COMPLAINTS + EMAIL_DRAFTS | 6 |

### 12.2 Consolidated Init Script

See: `database/final_INIT_ALL_TABLES.sql` (below)

---

## 13. Incremental Migration Scripts

### 13.1 Summary

| Phase | Feature                           | Tables Created/Modified                    |
|-------|-----------------------------------|--------------------------------------------|
| 1     | Core Schema + Eligibility         | QUESTION_MASTER, ELIGIBILITY_AUDIT, COMPLAINT_MASTER, COMPLAINT_HISTORY, ATTACHMENT_METADATA, OUTBOX_EVENT, AUDIT_LOG, SHEDLOCK |
| 1     | Prior RE Complaint                | ALTER COMPLAINTS (6 columns), RE_RESPONSE_TRACKER |
| 4     | MRE Rules Engine                  | MRE_RULE_CATEGORY, MRE_RULE_DEFINITION, MRE_RULE_HISTORY, MRE_DEPLOYMENT, MRE_GROUND_CONFIG, MRE_WINDOW_CONFIG |
| 5     | Complaint Number Generation       | OMBUDSMAN_OFFICE_MASTER, OFFICE_CODE_MASTER, COMPLAINT_NUMBER_SEQUENCE |
| 5     | Threshold Overflow Routing        | OFFICE_OVERFLOW_MAPPING, OFFICE_GLOBAL_THRESHOLD_CONFIG, ALTER OFFICE_CODE_MASTER |
| 6     | Closure & Tracking Columns        | ALTER COMPLAINTS (7 columns), ALTER EMAIL_DRAFTS (6 columns) |

---

## Appendix A: Java Service Map

| Service Class                         | Feature                      |
|--------------------------------------|------------------------------|
| `ComplaintService`                   | Filing, lifecycle, dashboard |
| `ComplaintNumberGeneratorService`    | Number generation + overflow |
| `ComplaintRoutingService`            | Department routing + transfer|
| `KeycloakUserService`               | Officer lookup for assignment|
| `ComplaintEventPublisher`            | Kafka outbox publishing      |

## Appendix B: Testing Checklist

| Test Case                                       | Expected                          |
|------------------------------------------------|-----------------------------------|
| File complaint from Mumbai (pincode 400064)     | Office: Mumbai-I (013)            |
| File complaint from Pune, Maharashtra           | Office: Mumbai-II (021)           |
| File complaint from Kanpur (208004)             | Office: Kanpur (011)              |
| File complaint from Meerut, UP                  | Office: Dehradun (017)            |
| File complaint from Ghaziabad, UP               | Office: New Delhi-II (016)        |
| Target office threshold met                     | Overflows to Priority 1           |
| All 3 offices in group meet threshold           | Resets all counters, restarts     |
| EMAIL filing type                               | No counter impact                 |
| PHYSICAL_LETTER filing type                     | No counter impact                 |
| Concurrent requests (same office)               | No duplicate sequences            |
| New FY (April 1)                                | New sequence row, starts at 1     |
| priorReComplaint=true, no date provided         | Validation error                  |
| RE complaint date in future                     | Validation error                  |
| Entity not in RE list                           | Default to RBIO                   |
| Credit card entity                              | Routes to CEPC                    |

---

*Document generated: August 2026 | RBI CMS 2.0 | RB-IOS 2026 Scheme*
