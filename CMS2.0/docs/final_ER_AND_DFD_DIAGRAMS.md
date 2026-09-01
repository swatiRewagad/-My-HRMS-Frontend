# RBI CMS 2.0 — ER Diagram & Data Flow Diagrams

---

## 1. Entity Relationship (ER) Diagram

### 1.1 Complete ER Diagram (All 30+ Tables)

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    RBI CMS 2.0 — ENTITY RELATIONSHIP DIAGRAM                                │
└─────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

                                          ┌──────────────────────┐
                                          │   COMPLAINT_CATEGORIES│
                                          ├──────────────────────┤
                                          │ PK id                │
                                          │    name              │
                                          │ FK parent_id ────┐   │
                                          │    description   │   │
                                          │    status        │   │
                                          │    sort_order    │   │
                                          └──────────┬───────┘   │
                                                     │ self-ref  │
                                                     └───────────┘
                                                     │
                                                     │ 1:N (categoryId)
                                                     ▼
┌───────────────────┐     1:N      ┌────────────────────────────────────────────────────────┐     1:N      ┌──────────────────────┐
│      BANKS        │─────────────▶│                    COMPLAINTS                          │◀─────────────│  REGULATED_ENTITIES  │
├───────────────────┤              ├────────────────────────────────────────────────────────┤              ├──────────────────────┤
│ PK id             │              │ PK id                                                  │              │ PK id                │
│    name           │              │ UK complaintNumber                                     │              │    name              │
│    code           │              │ FK bankId ──────────────────────────────────────────────┼──────────────│    nameNormalized    │
│    type           │              │ FK categoryId                                          │              │    department (RBIO/ │
│    status         │              │    complainantName, Email, Phone, Address              │              │                CEPC) │
│                   │              │    complainantState, complainantDistrict               │              │    entityType        │
└───────────────────┘              │    subject, description, reliefSought                  │              │    nodalOfficerName  │
                                   │    status, priority, filingType                        │              │    nodalOfficerEmail │
                                   │    department, assignedOfficer, assignedRole           │              │    pnoName, pnoEmail │
                                   │    entityCode, workflowStage                          │              │    portalEnabled     │
                                   │    priorReComplaint, reComplaintDate                   │              └──────────────────────┘
                                   │    reComplaintReference, reRepliedAndDissatisfied      │
                                   │    triageSignal, maintainabilityDetermination          │
                                   │    awardAmount, slaDeadline                           │
                                   │    closureClause, closureAuthorityName                │
                                   │    conciliationDate, adjudicationDate                 │
                                   │    createdAt, updatedAt, resolvedAt, closedAt         │
                                   └────────────────────────────┬───────────────────────────┘
                                        │           │           │           │           │
                                        │           │           │           │           │
                      ┌─────────────────┘           │           │           │           └─────────────────┐
                      │                             │           │           │                             │
                      ▼ 1:N                         ▼ 1:N       ▼ 1:N       ▼ 1:N                         ▼ 1:N
┌─────────────────────────────┐  ┌──────────────────────┐ ┌─────────────┐ ┌──────────────────────┐ ┌──────────────────────┐
│   COMPLAINT_TIMELINE        │  │ COMPLAINT_ATTACHMENTS│ │  AUDIT_LOG  │ │ RE_RESPONSE_TRACKER  │ │ INTER_OFFICE_TRANSFERS│
├─────────────────────────────┤  ├──────────────────────┤ ├─────────────┤ ├──────────────────────┤ ├──────────────────────┤
│ PK id                       │  │ PK id                │ │ PK id       │ │ PK id                │ │ PK id                │
│ FK complaintId              │  │ FK complaintId       │ │    complaint │ │ FK complaintId       │ │    complaintNumber   │
│    action                   │  │    fileName          │ │    Number   │ │ FK regulatedEntityId │ │    fromOffice        │
│    performedBy              │  │    originalName      │ │    action   │ │    forwardedAt       │ │    toOffice          │
│    remarks                  │  │    contentType       │ │    actor    │ │    respondedAt       │ │    transferType      │
│    fromStatus, toStatus     │  │    fileSize          │ │    actorRole│ │    windowDays        │ │    status            │
│    performedAt              │  │    storagePath       │ │    timestamp│ │    breached          │ │    reason            │
└─────────────────────────────┘  │    uploadedAt        │ │    metadata │ │    exParteEligible   │ │    requestedBy       │
                                 └──────────────────────┘ │    ipAddress│ │    responseText      │ │    approvedBy        │
                                                          └─────────────┘ │    queryText         │ └──────────────────────┘
                                                                          │    extensionGranted  │
                                                                          └──────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                        APPEALS SUBSYSTEM                                                    │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐      1:N      ┌──────────────────────────────┐
│                  APPEALS                        │──────────────▶│       APPEAL_TIMELINE        │
├────────────────────────────────────────────────┤               ├──────────────────────────────┤
│ PK id                                          │               │ PK id                        │
│ UK appealNumber                                │               │    appealNumber (FK logical) │
│    originalComplaintNumber (FK to COMPLAINTS)  │               │    action                    │
│    classificationType (APPEAL/REPRESENTATION)  │               │    performedBy               │
│    appealGround, reliefSought                  │               │    performedByRole           │
│    appellantName, Email, Phone                 │               │    remarks                   │
│    status, assignedOfficer, assignedRole       │               │    fromStatus, toStatus      │
│    hearingDate, hearingVenue                   │               │    performedAt               │
│    orderDate, orderSummary, orderOutcome       │               └──────────────────────────────┘
│    awardModifiedAmount, closureCause           │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   CRPC / EMAIL INTAKE SUBSYSTEM                                             │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐      1:N      ┌──────────────────────────────┐
│               EMAIL_DRAFTS                     │──────────────▶│    EMAIL_DRAFT_ATTACHMENTS   │
├────────────────────────────────────────────────┤               ├──────────────────────────────┤
│ PK id                                          │               │ PK id                        │
│ UK draftId                                     │               │    draftId (FK logical)      │
│    threadId, messageId                         │               │    fileName, fileType        │
│    senderEmail, subject, body                  │               │    fileSize, storagePath     │
│    complainantName, Phone, Address             │               │    ocrText, ocrConfidence    │
│    complainantState, District, Pincode         │               │    uploadedBy                │
│    cpgramsNumber, category                     │               └──────────────────────────────┘
│    status, assignedTo                          │
│    entityName, entityType                      │
│    deoDecision, deoRemarks                     │
│    reviewerAssignedTo, reviewerDecision        │
│    convertedComplaintId                        │
│    closureClause, schemeVersion                │
│    detectedLanguage, isVernacular              │
│    translatedBody, translationConfidence       │
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                               COMPLAINT NUMBER GENERATION SUBSYSTEM                                         │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────┐         ┌─────────────────────────┐         ┌──────────────────────────────┐
│ OMBUDSMAN_OFFICE_MASTER │         │   OFFICE_CODE_MASTER    │         │ COMPLAINT_NUMBER_SEQUENCE    │
├─────────────────────────┤         ├─────────────────────────┤         ├──────────────────────────────┤
│ PK id                   │         │ PK id                   │         │ PK id                        │
│    officeName           │────────▶│ UK officeCode           │────────▶│    officeCode                │
│    jurisdiction (TEXT)  │  maps   │    officeName           │  tracks │    financialYear             │
│    isActive             │  name   │    officeType           │  seq    │    lastSequence              │
│                         │  to     │    counter              │  per    │ UK (officeCode, financialYear)│
└─────────────────────────┘  code   │    threshold            │  FY     └──────────────────────────────┘
                                    │    isActive             │
                                    └────────────┬────────────┘
                                                 │
                              ┌───────────────────┴───────────────────┐
                              │                                       │
                              ▼                                       ▼
               ┌──────────────────────────────┐       ┌──────────────────────────────────┐
               │   OFFICE_OVERFLOW_MAPPING    │       │ OFFICE_GLOBAL_THRESHOLD_CONFIG   │
               ├──────────────────────────────┤       ├──────────────────────────────────┤
               │ PK id                        │       │ PK id (always = 1)               │
               │ UK officeCode                │       │    thresholdValue (default 2)    │
               │    officeName                │       │    updatedBy                     │
               │    priority1OfficeName       │       └──────────────────────────────────┘
               │    priority2OfficeName       │
               │    isActive                  │
               └──────────────────────────────┘

┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 SUPPORTING / INFRASTRUCTURE TABLES                                          │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────┘

┌────────────────────┐  ┌──────────────────────┐  ┌────────────────────┐  ┌────────────────────────┐
│      PINCODES      │  │  NODAL_OFFICER_RECORDS│  │     HOLIDAYS       │  │  ROUND_ROBIN_POINTERS  │
├────────────────────┤  ├──────────────────────┤  ├────────────────────┤  ├────────────────────────┤
│ PK id              │  │ PK id                │  │ PK id              │  │ PK id                  │
│    pincode         │  │    complaintNumber   │  │ UK holidayDate     │  │ UK poolKey             │
│    officeName      │  │    entityName        │  │    name            │  │    currentIndex        │
│    district        │  │    nodalOfficerName  │  │    type            │  │    updatedAt           │
│    state           │  │    status            │  │    year            │  └────────────────────────┘
│    region          │  │    assignedTo        │  │    isNational      │
│    division        │  └──────────────────────┘  └────────────────────┘
│    officeType      │
└────────────────────┘

┌────────────────────────┐  ┌──────────────────────┐
│  IN_APP_NOTIFICATIONS  │  │   SIMULATED_EMAILS   │
├────────────────────────┤  ├──────────────────────┤
│ PK id                  │  │ PK id                │
│    targetUserId        │  │    from, to          │
│    type                │  │    subject, body     │
│    title, message      │  │    type, sentAt      │
│    relatedEntityId     │  └──────────────────────┘
│    relatedEntityType   │
│    isRead, readAt      │
└────────────────────────┘
```

### 1.2 Relationship Summary Table

| Parent Table              | Child Table                | Relationship | Join Column              |
|--------------------------|---------------------------|--------------|--------------------------|
| COMPLAINTS               | COMPLAINT_TIMELINE        | 1:N          | complaintId              |
| COMPLAINTS               | COMPLAINT_ATTACHMENTS     | 1:N          | complaintId              |
| COMPLAINTS               | RE_RESPONSE_TRACKER       | 1:N          | complaintId              |
| COMPLAINTS               | AUDIT_LOG                 | 1:N          | complaintNumber          |
| COMPLAINTS               | INTER_OFFICE_TRANSFERS    | 1:N          | complaintNumber          |
| COMPLAINTS               | NODAL_OFFICER_RECORDS     | 1:N          | complaintNumber          |
| COMPLAINTS               | APPEALS                   | 1:N          | originalComplaintNumber  |
| APPEALS                  | APPEAL_TIMELINE           | 1:N          | appealNumber             |
| EMAIL_DRAFTS             | EMAIL_DRAFT_ATTACHMENTS   | 1:N          | draftId                  |
| BANKS                    | COMPLAINTS                | 1:N          | bankId                   |
| COMPLAINT_CATEGORIES     | COMPLAINTS                | 1:N          | categoryId               |
| COMPLAINT_CATEGORIES     | COMPLAINT_CATEGORIES      | Self-ref     | parentId                 |
| REGULATED_ENTITIES       | RE_RESPONSE_TRACKER       | 1:N          | regulatedEntityId        |
| OMBUDSMAN_OFFICE_MASTER  | OFFICE_CODE_MASTER        | 1:1 (name)   | officeName (logical)     |
| OFFICE_CODE_MASTER       | COMPLAINT_NUMBER_SEQUENCE | 1:N          | officeCode               |
| OFFICE_CODE_MASTER       | OFFICE_OVERFLOW_MAPPING   | 1:1          | officeCode               |

---

## 2. Data Flow Diagrams (Feature-Wise)

### 2.1 DFD Level 0 — System Context

```
┌─────────────┐                                                        ┌─────────────────┐
│ Complainant │─── File Complaint / Track Status / Eligibility Check ──▶│                 │
│ (Citizen)   │◀── Complaint Number / Status Updates / SMS/Email ───────│                 │
└─────────────┘                                                        │                 │
                                                                       │   RBI CMS 2.0   │
┌─────────────┐                                                        │                 │
│ RBIO/CEPC   │─── Assign / Process / Close / Transfer ────────────────▶│   COMPLAINT     │
│ Officers    │◀── Dashboard / Case List / Notifications ──────────────│   MANAGEMENT    │
└─────────────┘                                                        │   SYSTEM        │
                                                                       │                 │
┌─────────────┐                                                        │                 │
│ RE (Bank)   │─── Submit Response / View Forwarded Cases ─────────────▶│                 │
│ Nodal Officer│◀── Case Details / RE Response Deadline ────────────────│                 │
└─────────────┘                                                        │                 │
                                                                       │                 │
┌─────────────┐                                                        │                 │
│ AA (Appellate│─── Hear Appeal / Issue Order ──────────────────────────▶│                 │
│ Authority)  │◀── Appeal List / Case History ─────────────────────────│                 │
└─────────────┘                                                        └─────────────────┘
```

---

### 2.2 DFD: Feature 1 — Eligibility Check

```
┌─────────────┐                                                        
│ Complainant │                                                        
└──────┬──────┘                                                        
       │ 1. Start eligibility wizard                                   
       ▼                                                               
┌──────────────────────────┐        ┌──────────────────────┐          
│ 1.1 Load Questions       │───────▶│ D1: QUESTION_MASTER  │          
│     (active, ordered)    │◀───────│    (6 questions)     │          
└──────────────┬───────────┘        └──────────────────────┘          
               │ 2. Submit answers                                    
               ▼                                                       
┌──────────────────────────┐                                          
│ 1.2 Evaluate Eligibility │                                          
│     - Check expected_answer                                         
│     - Apply rules        │                                          
└──────┬───────────────────┘                                          
       │ 3. Store audit record                                        
       ▼                                                               
┌──────────────────────────┐        ┌──────────────────────┐          
│ 1.3 Log Result           │───────▶│ D2: ELIGIBILITY_AUDIT│          
│     (ELIGIBLE/NOT)       │        │    (immutable log)   │          
└──────┬───────────────────┘        └──────────────────────┘          
       │                                                              
       ├── ELIGIBLE → Proceed to Filing Form (Feature 2)              
       └── NOT_ELIGIBLE → Display Reason + Standard Response          
```

---

### 2.3 DFD: Feature 2 — Complaint Filing & Routing

```
┌─────────────┐
│ Complainant │
└──────┬──────┘
       │ Submit complaint form (state, district, entity, details)
       ▼
┌────────────────────────────────────────────────────────────────────────────────────┐
│ 2.1 Validate & Route                                                               │
│                                                                                    │
│  ┌─────────────────────────┐                                                       │
│  │ Validate Prior-RE Fields│ ← priorReComplaint, reComplaintDate, reference       │
│  └────────────┬────────────┘                                                       │
│               │                                                                    │
│  ┌────────────▼────────────┐      ┌────────────────────────────┐                   │
│  │ Resolve Department      │─────▶│ D3: REGULATED_ENTITIES     │                   │
│  │ (RBIO / CEPC / CRPC)   │◀─────│ (entity → department map)  │                   │
│  └────────────┬────────────┘      └────────────────────────────┘                   │
│               │                                                                    │
│  ┌────────────▼────────────────────────────────────────────────────────────────┐   │
│  │ 2.2 Generate Complaint Number (see Feature 3 DFD below)                     │   │
│  └────────────┬────────────────────────────────────────────────────────────────┘   │
│               │                                                                    │
│  ┌────────────▼────────────┐      ┌────────────────────────────┐                   │
│  │ 2.3 Assign Officer      │─────▶│ D4: ROUND_ROBIN_POINTERS   │                   │
│  │     (round-robin)       │◀─────│ (per-role assignment index)│                   │
│  └────────────┬────────────┘      └────────────────────────────┘                   │
│               │                                                                    │
└───────────────┼────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌────────────────────────────┐      ┌────────────────────────────┐
│ 2.4 Save Complaint         │─────▶│ D5: COMPLAINTS             │
│     + Timeline Entry       │─────▶│ D6: COMPLAINT_TIMELINE     │
│     + Publish Event        │─────▶│ D7: OUTBOX_EVENT → Kafka   │
└────────────────────────────┘      └────────────────────────────┘
```

---

### 2.4 DFD: Feature 3 — Complaint Number Generation

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ 3.0 COMPLAINT NUMBER GENERATION                                                     │
│                                                                                     │
│  INPUT: department, complainantState, complainantDistrict, isVernacularOrCrpc       │
│                                                                                     │
│  ┌─────────────────────────────────────┐      ┌──────────────────────────────────┐  │
│  │ 3.1 Resolve Office Name             │─────▶│ D8: OMBUDSMAN_OFFICE_MASTER      │  │
│  │     state + district → officeName   │◀─────│     (24 offices + jurisdictions) │  │
│  └───────────────────┬─────────────────┘      └──────────────────────────────────┘  │
│                      │                                                              │
│  ┌───────────────────▼─────────────────┐      ┌──────────────────────────────────┐  │
│  │ 3.2 Resolve Office Code             │─────▶│ D9: OFFICE_CODE_MASTER           │  │
│  │     officeName → 3-digit code       │◀─────│     (25 offices + codes)         │  │
│  └───────────────────┬─────────────────┘      └──────────────────────────────────┘  │
│                      │                                                              │
│  ┌───────────────────▼─────────────────┐      ┌──────────────────────────────────┐  │
│  │ 3.3 Apply Threshold Overflow        │─────▶│ D10: OFFICE_OVERFLOW_MAPPING     │  │
│  │     (skip if vernacular/CRPC)       │─────▶│ D11: OFFICE_GLOBAL_THRESHOLD_CFG │  │
│  │     target → p1 → p2 → reset       │◀─────│ D9: OFFICE_CODE_MASTER (counter) │  │
│  └───────────────────┬─────────────────┘      └──────────────────────────────────┘  │
│                      │                                                              │
│  ┌───────────────────▼─────────────────┐      ┌──────────────────────────────────┐  │
│  │ 3.4 Get Next Sequence               │─────▶│ D12: COMPLAINT_NUMBER_SEQUENCE   │  │
│  │     (PESSIMISTIC_WRITE lock)        │◀─────│     (per officeCode + FY)        │  │
│  └───────────────────┬─────────────────┘      └──────────────────────────────────┘  │
│                      │                                                              │
│  OUTPUT: "N" + FY(6) + officeCode(3) + sequence(6)                                  │
│  Example: N202627013000001                                                          │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.5 DFD: Feature 4 — Threshold Overflow Routing

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ 4.0 THRESHOLD OVERFLOW DECISION                                                     │
│                                                                                     │
│  INPUT: targetOfficeCode (from step 3.2)                                            │
│                                                                                     │
│  ┌───────────────────────────────────┐     ┌──────────────────────────────────────┐ │
│  │ 4.1 Read Global Threshold         │────▶│ D11: OFFICE_GLOBAL_THRESHOLD_CONFIG  │ │
│  │     (configurable, default=2)     │◀────│     id=1, thresholdValue             │ │
│  └──────────────┬────────────────────┘     └──────────────────────────────────────┘ │
│                 │                                                                   │
│  ┌──────────────▼────────────────────┐     ┌──────────────────────────────────────┐ │
│  │ 4.2 Check Target Office Counter   │────▶│ D9: OFFICE_CODE_MASTER               │ │
│  │     (lock FOR UPDATE)             │◀────│     counter vs threshold             │ │
│  └──────────────┬────────────────────┘     └──────────────────────────────────────┘ │
│                 │                                                                   │
│        ┌────────┴─────────┐                                                        │
│        │counter<threshold?│                                                        │
│        └────────┬─────────┘                                                        │
│          YES    │    NO                                                             │
│           │     │                                                                   │
│           │     ▼                                                                   │
│           │  ┌────────────────────────────┐  ┌──────────────────────────────────┐   │
│           │  │ 4.3 Lookup Overflow Mapping │─▶│ D10: OFFICE_OVERFLOW_MAPPING     │   │
│           │  └───────────┬────────────────┘  │     priority1, priority2          │   │
│           │              │                   └──────────────────────────────────┘   │
│           │     ┌────────┴────────┐                                                │
│           │     │p1.ctr<threshold?│─── YES ──▶ Assign to Priority1, p1.counter++   │
│           │     └────────┬────────┘                                                │
│           │              │ NO                                                      │
│           │     ┌────────┴────────┐                                                │
│           │     │p2.ctr<threshold?│─── YES ──▶ Assign to Priority2, p2.counter++   │
│           │     └────────┬────────┘                                                │
│           │              │ NO                                                      │
│           │              ▼                                                          │
│           │  ┌────────────────────────────────┐                                    │
│           │  │ 4.4 ALL MET → RESET            │                                    │
│           │  │ Reset counters for all 3       │                                    │
│           │  │ Assign to target, counter=1    │                                    │
│           │  └────────────────────────────────┘                                    │
│           │                                                                        │
│           ▼                                                                        │
│  ┌─────────────────────────────┐                                                   │
│  │ Assign to target            │                                                   │
│  │ target.counter++            │                                                   │
│  └─────────────────────────────┘                                                   │
│                                                                                     │
│  OUTPUT: assignedOfficeCode (may differ from target if overflow triggered)           │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.6 DFD: Feature 5 — CRPC (Email/Physical Letter) Processing

```
┌──────────────────┐    ┌──────────────────┐
│  Email Server    │    │ Physical Letter  │
│  (IMAP/SMTP)    │    │ (Scanned PDF)    │
└────────┬─────────┘    └────────┬─────────┘
         │                       │
         └───────────┬───────────┘
                     │ Ingest
                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 5.1 Ingestion & OCR                                                      │
│                                                                          │
│  ┌──────────────────────┐      ┌──────────────────────────────────────┐  │
│  │ Parse Email/Scan PDF │─────▶│ D13: EMAIL_DRAFTS (status=ASSIGNED) │  │
│  │ Extract: name, phone,│─────▶│ D14: EMAIL_DRAFT_ATTACHMENTS        │  │
│  │ address, entity,     │      └──────────────────────────────────────┘  │
│  │ OCR text, language   │                                                │
│  └──────────────────────┘      ┌──────────────────────────────────────┐  │
│                                │ D15: PADDLE_OCR (Python service)     │  │
│  Detect language → translate   └──────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 5.2 DEO (Data Entry Operator) Processing                                 │
│                                                                          │
│  DEO reviews extracted data:                                             │
│  - Confirm/correct complainant details                                   │
│  - Categorize complaint                                                  │
│  - Mark: complaint / not-a-complaint / sub-judice                       │
│  - Decision: FORWARD_TO_REVIEWER / NON_MAINTAINABLE / CLOSE             │
│                                                                          │
│  Update D13: EMAIL_DRAFTS (deoDecision, deoRemarks)                     │
└──────────────────────┬───────────────────────────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 5.3 Reviewer Approval                                                    │
│                                                                          │
│  Reviewer validates DEO decision:                                        │
│  - Approve → Convert to formal complaint (Feature 2)                     │
│  - Reject / Send back to DEO                                            │
│  - Reassign to different office                                          │
│                                                                          │
│  Update D13: EMAIL_DRAFTS (reviewerDecision, convertedComplaintId)       │
└──────────────────────┬───────────────────────────────────────────────────┘
                       │ If approved
                       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 5.4 Convert to Complaint                                                 │
│                                                                          │
│  - Generate complaint number (Feature 3 — isVernacularOrCrpc=TRUE)       │
│  - Create COMPLAINTS record                                              │
│  - Route to target department (RBIO/CEPC)                                │
│  - Link: EMAIL_DRAFTS.convertedComplaintId = complaint.complaintNumber   │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### 2.7 DFD: Feature 6 — Complaint Processing & SLA

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ 6.0 COMPLAINT PROCESSING LIFECYCLE                                               │
│                                                                                  │
│  ┌─────────────┐                                                                 │
│  │ RBIO/CEPC   │                                                                 │
│  │ Officer     │                                                                 │
│  └──────┬──────┘                                                                 │
│         │                                                                        │
│         ▼                                                                        │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ 6.1 Review & Classify                                                    │    │
│  │     - MRE evaluation (maintainability check)                             │    │
│  │     - Priority assessment (senior citizen, fraud, high-value)            │    │
│  │     - SLA computation                                                    │    │
│  └──────────────────────────┬───────────────────────────────────────────────┘    │
│                             │                                                    │
│         ┌───────────────────┼───────────────────────────┐                       │
│         ▼                   ▼                           ▼                       │
│  ┌──────────────┐  ┌────────────────────┐  ┌──────────────────────────┐        │
│  │ NON-         │  │ MAINTAINABLE       │  │ TRANSFER                 │        │
│  │ MAINTAINABLE │  │                    │  │ (Inter-department)       │        │
│  │ → Close with │  │ 6.2 Forward to RE  │  │                          │        │
│  │   reasons    │  │ (30-day window)    │  │ D: INTER_OFFICE_TRANSFERS│        │
│  └──────────────┘  └────────┬───────────┘  └──────────────────────────┘        │
│                             │                                                    │
│         ┌───────────────────▼───────────────────────────┐                       │
│         │ RE RESPONSE TRACKING                          │                       │
│         │                                               │                       │
│         │ ┌───────────────────────────────────────────┐ │                       │
│         │ │ D: RE_RESPONSE_TRACKER                    │ │                       │
│         │ │ - forwardedAt, windowExpiresAt            │ │                       │
│         │ │ - RE responds / window expires            │ │                       │
│         │ │ - Breach → exParteEligible                │ │                       │
│         │ └───────────────────────────────────────────┘ │                       │
│         └────────────────────┬──────────────────────────┘                       │
│                              │                                                   │
│         ┌────────────────────┼────────────────────────────┐                     │
│         ▼                    ▼                            ▼                     │
│  ┌──────────────┐  ┌────────────────────┐  ┌────────────────────────┐          │
│  │ Conciliation │  │ Direct Resolution  │  │ Adjudication/Award     │          │
│  │ (mediated)   │  │ (RE complies)      │  │ (officer decision)     │          │
│  └──────┬───────┘  └────────┬───────────┘  └──────────┬─────────────┘          │
│         │                   │                         │                         │
│         └───────────────────┼─────────────────────────┘                         │
│                             ▼                                                    │
│  ┌──────────────────────────────────────────────────────────────────────────┐    │
│  │ 6.3 Closure                                                              │    │
│  │     - Set closureClause, closureAuthorityName                            │    │
│  │     - Generate closure letter                                            │    │
│  │     - Send notification (SMS/Email)                                      │    │
│  │     - Status → CLOSED                                                    │    │
│  │     - Log to COMPLAINT_TIMELINE + AUDIT_LOG                              │    │
│  └──────────────────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.8 DFD: Feature 7 — Appeals (Appellate Authority)

```
┌─────────────┐
│ Complainant │ (dissatisfied with RBIO/CEPC outcome)
└──────┬──────┘
       │ File Appeal / Representation
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 7.1 Appeal Filing                                                        │
│                                                                          │
│  - Link to originalComplaintNumber                                       │
│  - Classify: APPEAL or REPRESENTATION                                    │
│  - Generate appealNumber                                                 │
│  - Assign to AA_REGISTRAR                                                │
│                                                                          │
│  Store: D16: APPEALS                                                     │
│  Log:   D17: APPEAL_TIMELINE                                             │
└───────────────────────┬──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 7.2 Hearing & Order                                                      │
│                                                                          │
│  AA_BENCH_OFFICER / AA_AUTHORITY:                                        │
│  - Schedule hearing (hearingDate, hearingVenue)                           │
│  - Conduct hearing                                                       │
│  - Issue order (orderDate, orderSummary, orderOutcome)                   │
│  - Modify award if applicable (awardModifiedAmount)                      │
│                                                                          │
│  Outcomes: UPHELD / MODIFIED / SET_ASIDE / REMANDED                      │
└───────────────────────┬──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 7.3 Closure                                                              │
│                                                                          │
│  - Set closureCause                                                      │
│  - Update original complaint if award modified                           │
│  - Notify complainant + RE                                               │
│  - Status → CLOSED                                                       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### 2.9 DFD: Feature 8 — Notifications & Audit

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ 8.0 NOTIFICATIONS & AUDIT                                                        │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐         │
│  │ EVENT TRIGGERS:                                                     │         │
│  │ • Complaint filed    • Status changed    • SLA breach              │         │
│  │ • Transfer requested • RE response due   • Closure                 │         │
│  │ • Appeal filed       • Assignment change                           │         │
│  └────────────────────────────────┬────────────────────────────────────┘         │
│                                   │                                              │
│              ┌────────────────────┼───────────────────────┐                     │
│              ▼                    ▼                       ▼                     │
│  ┌────────────────────┐ ┌─────────────────────┐ ┌────────────────────────┐      │
│  │ IN_APP_NOTIFICATIONS│ │  SIMULATED_EMAILS   │ │     AUDIT_LOG          │      │
│  │                    │ │  (SMS/Email)        │ │                        │      │
│  │ - Officer bell     │ │  - Complainant SMS  │ │ - Every action logged  │      │
│  │ - Transfer pending │ │  - Status email     │ │ - Actor, role, IP      │      │
│  │ - 3-day no-action  │ │  - Closure letter   │ │ - Previous/new state   │      │
│  │ - Duplicate warning│ │  - RE notification  │ │ - Immutable trail      │      │
│  └────────────────────┘ └─────────────────────┘ └────────────────────────┘      │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

### 2.10 DFD: Feature 9 — RE (Regulated Entity) Portal

```
┌─────────────────┐
│ RE Nodal Officer│
└────────┬────────┘
         │ Login via Keycloak (RE_NODAL_OFFICER role)
         ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 9.1 View Forwarded Cases                                                 │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────┐         │
│  │ D: RE_RESPONSE_TRACKER (where regulatedEntityId = current)  │         │
│  │ Show: complaintNumber, subject, forwardedAt, deadline       │         │
│  └─────────────────────────────────────────────────────────────┘         │
└───────────────────────┬──────────────────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 9.2 Submit Response                                                      │
│                                                                          │
│  - Enter responseText                                                    │
│  - Upload supporting documents                                           │
│  - Raise query (queryText) if clarification needed                       │
│  - Request extension (extensionDays)                                     │
│                                                                          │
│  Update: RE_RESPONSE_TRACKER.respondedAt, responseText                   │
│  Notify: RBIO/CEPC officer of RE response                                │
└──────────────────────────────────────────────────────────────────────────┘
```

---

### 2.11 DFD: Feature 10 — Pincode-Based Office Resolution

```
┌─────────────┐
│ Complainant │ enters pincode in filing form
└──────┬──────┘
       │
       ▼
┌──────────────────────────────────────────────────────────────────────────┐
│ 10.1 Pincode Lookup                                                      │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ D: PINCODES table                                                  │  │
│  │ pincode → state, district, officeName                              │  │
│  │                                                                    │  │
│  │ Auto-fill: complainantState, complainantDistrict                   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  This feeds into Feature 3 (complaint number generation)                 │
│  state + district → office resolution → office code → number             │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Table Count Summary

| Category                         | Tables | Purpose                            |
|---------------------------------|--------|------------------------------------|
| Core Complaint                  | 5      | COMPLAINTS, TIMELINE, ATTACHMENTS, AUDIT_LOG, DRAFT |
| Appeals                         | 2      | APPEALS, APPEAL_TIMELINE           |
| CRPC (Email/Letter)             | 2      | EMAIL_DRAFTS, EMAIL_DRAFT_ATTACHMENTS |
| Master Data                     | 4      | BANKS, COMPLAINT_CATEGORIES, REGULATED_ENTITIES, PINCODES |
| Complaint Number Generation     | 5      | OMBUDSMAN_OFFICE, OFFICE_CODE, SEQUENCE, OVERFLOW, THRESHOLD |
| RE Tracking                     | 2      | RE_RESPONSE_TRACKER, NODAL_OFFICER_RECORDS |
| Inter-Office                    | 1      | INTER_OFFICE_TRANSFERS             |
| Notifications                   | 2      | IN_APP_NOTIFICATIONS, SIMULATED_EMAILS |
| Infrastructure                  | 3      | HOLIDAYS, ROUND_ROBIN_POINTERS, SHEDLOCK |
| **Total**                       | **~30**|                                    |

---

*Document generated: August 2026 | RBI CMS 2.0*
