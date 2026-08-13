# Complaint Number Generation - Developer Guidance

## 1. Overview

The RBI CMS generates unique complaint numbers using a structured format that encodes the filing type, financial year, assigned office, and sequence number. The system includes threshold-based load balancing across offices using priority overflow mapping.

---

## 2. Complaint Number Format

```
N + FY + OfficeCode + Sequence
```

| Segment      | Length | Example   | Description                          |
|-------------|--------|-----------|--------------------------------------|
| Prefix      | 1      | `N`       | Fixed prefix (all complaints)        |
| FY          | 6      | `202627`  | Financial year (Apr-Mar)             |
| OfficeCode  | 3      | `013`     | 3-digit office code from master      |
| Sequence    | 6      | `000001`  | Zero-padded, per office per FY       |

**Example:** `N202627013000001` = Complaint filed in FY 2026-27, assigned to Mumbai-I (013), sequence 1.

### Financial Year Calculation

| Date       | Month | FY String |
|-----------|-------|-----------|
| Aug 2026  | >= 4  | `202627`  |
| Jan 2027  | < 4   | `202627`  |
| Apr 2027  | >= 4  | `202728`  |

---

## 3. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│  COMPLAINT FILING (Frontend)                                            │
│  Captures: complainantState, complainantDistrict, entityName, filingType│
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ POST /api/complaints
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  ComplaintService.fileComplaint()                                         │
│                                                                          │
│  1. Resolve Department (RBIO/CEPC) via entity name match                 │
│  2. Detect isVernacularOrCrpc (EMAIL / PHYSICAL_LETTER)                  │
│  3. Call ComplaintNumberGeneratorService                                  │
└──────────────────────────────┬───────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────┐
│  ComplaintNumberGeneratorService.generateComplaintNumber()                │
│                                                                          │
│  Step 1: resolveOfficeName(department, state, district)                  │
│          ┌──────────────────────────────────────────────┐                │
│          │  OMBUDSMAN_OFFICE_MASTER                     │                │
│          │  - Search by district first (split cities)   │                │
│          │  - Then by state with exclusion parsing      │                │
│          │  - Fallback: "New Delhi I"                   │                │
│          └──────────────────────────────────────────────┘                │
│                                                                          │
│  Step 2: resolveOfficeCode(officeName)                                   │
│          ┌──────────────────────────────────────────────┐                │
│          │  OFFICE_CODE_MASTER                          │                │
│          │  - Exact match by name (isActive = true)     │                │
│          │  - Fallback: hyphen-to-space normalization   │                │
│          │  - Default: "014" (New Delhi I)              │                │
│          └──────────────────────────────────────────────┘                │
│                                                                          │
│  Step 3: applyThresholdOverflow(officeCode)                              │
│          [Only for non-vernacular, non-CRPC complaints]                  │
│          ┌──────────────────────────────────────────────┐                │
│          │  OFFICE_CODE_MASTER (counter)                │                │
│          │  OFFICE_OVERFLOW_MAPPING (priorities)        │                │
│          │  OFFICE_GLOBAL_THRESHOLD_CONFIG              │                │
│          │                                              │                │
│          │  IF target.counter < threshold:              │                │
│          │      target.counter++ → return targetCode    │                │
│          │  ELSE IF priority1.counter < threshold:      │                │
│          │      priority1.counter++ → return p1Code     │                │
│          │  ELSE IF priority2.counter < threshold:      │                │
│          │      priority2.counter++ → return p2Code     │                │
│          │  ELSE:                                       │                │
│          │      RESET all 3 counters to 0               │                │
│          │      target.counter = 1 → return targetCode  │                │
│          └──────────────────────────────────────────────┘                │
│                                                                          │
│  Step 4: getNextSequence(officeCode, FY)                                 │
│          ┌──────────────────────────────────────────────┐                │
│          │  COMPLAINT_NUMBER_SEQUENCE                   │                │
│          │  - PESSIMISTIC_WRITE lock for thread safety  │                │
│          │  - Increment lastSequence atomically         │                │
│          │  - Create new row if first for office+FY     │                │
│          └──────────────────────────────────────────────┘                │
│                                                                          │
│  Step 5: Format → "N" + FY + officeCode + %06d(sequence)                │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Office Resolution Logic

### 4.1 State/District to Office

The system resolves the complainant's location to one of 24 RBI Ombudsman offices:

1. **District-first search**: Handles split-jurisdiction cities (Mumbai-I/II, Kolkata-I/II, Chennai-I/II, New Delhi-I/II)
2. **State search**: If district doesn't produce unique match
3. **Exclusion-clause parsing**: For states served by multiple offices (e.g., UP split across Dehradun, Kanpur, New Delhi-II)
4. **Default fallback**: "New Delhi I" (code 014)

### 4.2 Split-State Resolution (Uttar Pradesh Example)

| Office        | Districts Served                                                          |
|---------------|--------------------------------------------------------------------------|
| Dehradun (017)| Saharanpur, Shamli, Muzaffarnagar, Baghpat, Meerut, Bijnor, Amroha      |
| New Delhi-II (016)| Ghaziabad, Gautam Buddha Nagar                                       |
| Kanpur (011)  | All other UP districts (catch-all with "excluding" clause)               |

### 4.3 Office Name to Code Mapping

The `resolveOfficeCode()` method:
- Exact match on `OFFICE_CODE_MASTER.OFFICE_NAME` where `IS_ACTIVE = 1`
- Fallback: Replace hyphens with spaces (e.g., "Mumbai-I" → "Mumbai I") and retry
- Final fallback: code "014" (New Delhi I)

---

## 5. Threshold Overflow Logic

### 5.1 Purpose

Distributes complaint load evenly across offices within regional groups. When one office reaches its threshold, complaints overflow to priority offices.

### 5.2 Configuration

| Table                          | Purpose                                                |
|-------------------------------|--------------------------------------------------------|
| `OFFICE_GLOBAL_THRESHOLD_CONFIG` | Single row (id=1), configurable by Super Admin       |
| `OFFICE_CODE_MASTER.COUNTER`     | Current complaint count for the office in current cycle |
| `OFFICE_OVERFLOW_MAPPING`        | Priority 1 and Priority 2 overflow destinations       |

### 5.3 Algorithm

```
function applyThresholdOverflow(targetOfficeCode):
    threshold = OFFICE_GLOBAL_THRESHOLD_CONFIG.THRESHOLD_VALUE  (default: 2)
    target = OFFICE_CODE_MASTER[targetOfficeCode]  (with PESSIMISTIC_WRITE lock)
    
    IF target.counter < threshold:
        target.counter++
        RETURN targetOfficeCode
    
    mapping = OFFICE_OVERFLOW_MAPPING[targetOfficeCode]
    IF no mapping exists:
        target.counter = 1 (reset)
        RETURN targetOfficeCode
    
    priority1 = OFFICE_CODE_MASTER[mapping.priority1Code]
    IF priority1.counter < threshold:
        priority1.counter++
        RETURN priority1Code
    
    priority2 = OFFICE_CODE_MASTER[mapping.priority2Code]
    IF priority2.counter < threshold:
        priority2.counter++
        RETURN priority2Code
    
    // ALL 3 offices reached threshold — RESET cycle
    RESET counters to 0 for [target, priority1, priority2]
    target.counter = 1
    RETURN targetOfficeCode
```

### 5.4 Vernacular/CRPC Exception

Complaints filed via **EMAIL** or **PHYSICAL_LETTER** (vernacular/CRPC flow):
- Are assigned directly to the target office
- Do **NOT** increment the counter
- Do **NOT** trigger overflow

### 5.5 Overflow Groups (24 offices in 4 regional clusters)

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
| Office              | Code | Priority 1          | Priority 2   |
|--------------------|------|---------------------|--------------|
| Bengaluru          | 002  | Thiruvananthapuram  | Hyderabad    |
| Chennai-I          | 006  | Chennai-II          | Bengaluru    |
| Chennai-II         | 024  | Hyderabad           | Chennai-I    |
| Hyderabad          | 009  | Chennai-I           | Thiruvananthapuram |
| Thiruvananthapuram | 015  | Bengaluru           | Chennai-II   |

**Western Region:**
| Office     | Code | Priority 1 | Priority 2 |
|-----------|------|------------|------------|
| Ahmedabad | 001  | Raipur     | Mumbai-I   |
| Bhopal    | 004  | Ahmedabad  | Mumbai-II  |
| Mumbai-I  | 013  | Mumbai-II  | Bhopal     |
| Mumbai-II | 021  | Mumbai-I   | Raipur     |
| Raipur    | 019  | Bhopal     | Ahmedabad  |

---

## 6. Database Schema

### 6.1 Tables

| Table                           | Purpose                                         | Key Columns                              |
|--------------------------------|-------------------------------------------------|------------------------------------------|
| `OMBUDSMAN_OFFICE_MASTER`      | State/district → office jurisdiction mapping     | ID, OFFICE_NAME, JURISDICTION            |
| `OFFICE_CODE_MASTER`           | Office → 3-digit code + counter                  | OFFICE_CODE (unique), COUNTER, THRESHOLD |
| `COMPLAINT_NUMBER_SEQUENCE`    | Sequential number tracker per office+FY          | OFFICE_CODE, FINANCIAL_YEAR, LAST_SEQUENCE |
| `OFFICE_OVERFLOW_MAPPING`      | Priority overflow routing per office             | OFFICE_CODE, PRIORITY1/2_OFFICE_NAME     |
| `OFFICE_GLOBAL_THRESHOLD_CONFIG` | Single-row global threshold config             | THRESHOLD_VALUE (default 2)              |

### 6.2 Concurrency Handling

- `COMPLAINT_NUMBER_SEQUENCE`: Accessed with `@Lock(PESSIMISTIC_WRITE)` to prevent duplicate sequence numbers under concurrent requests.
- `OFFICE_CODE_MASTER.COUNTER`: Locked with `findByOfficeCodeForUpdate()` (pessimistic write) before incrementing.
- Counter reset uses `@Modifying` batch update query.

---

## 7. Java Service Classes

| Class | Package | Responsibility |
|-------|---------|---------------|
| `ComplaintNumberGeneratorService` | `com.hrms.cms.service` | Orchestrates the full generation flow |
| `ComplaintService` | `com.hrms.cms.service` | Calls generator during `fileComplaint()` |
| `ComplaintRoutingService` | `com.hrms.cms.service` | Resolves department (RBIO/CEPC) from entity |

### Entity Classes

| Entity | Table |
|--------|-------|
| `OmbudsmanOfficeMaster` | `OMBUDSMAN_OFFICE_MASTER` |
| `OfficeCodeMaster` | `OFFICE_CODE_MASTER` |
| `ComplaintNumberSequence` | `COMPLAINT_NUMBER_SEQUENCE` |
| `OfficeOverflowMapping` | `OFFICE_OVERFLOW_MAPPING` |
| `OfficeGlobalThresholdConfig` | `OFFICE_GLOBAL_THRESHOLD_CONFIG` |

### Repository Interfaces

| Repository | Key Methods |
|-----------|-------------|
| `OmbudsmanOfficeMasterRepository` | `findByJurisdictionContainingState(String)` |
| `OfficeCodeMasterRepository` | `findByOfficeCodeForUpdate(String)`, `resetCounters(List<String>)` |
| `ComplaintNumberSequenceRepository` | `findByOfficeCodeAndFinancialYearForUpdate(String, String)` |
| `OfficeOverflowMappingRepository` | `findByOfficeCodeAndIsActiveTrue(String)` |
| `OfficeGlobalThresholdConfigRepository` | `findById(1)` |

---

## 8. Incremental SQL Scripts

Execute in order for existing deployments:

| Script | Purpose |
|--------|---------|
| `V5__ombudsman_office_master.sql` | Create `OMBUDSMAN_OFFICE_MASTER` + seed 24 offices |
| `V6__office_code_master.sql` | Create `OFFICE_CODE_MASTER` + seed 25 office codes |
| `V7__complaint_number_sequence.sql` | Create `COMPLAINT_NUMBER_SEQUENCE` |
| `V8__office_threshold_overflow.sql` | Add COUNTER/THRESHOLD to OFFICE_CODE_MASTER, create OVERFLOW_MAPPING + GLOBAL_THRESHOLD_CONFIG |

### Fresh Deployment

Use the consolidated script: `database/INIT__complaint_number_tables.sql`

This single file creates all 5 tables with seed data in one execution.

---

## 9. Configuration

### Global Threshold (Super Admin)

```sql
-- View current threshold
SELECT * FROM OFFICE_GLOBAL_THRESHOLD_CONFIG;

-- Update threshold (e.g., set to 5)
UPDATE OFFICE_GLOBAL_THRESHOLD_CONFIG SET THRESHOLD_VALUE = 5, UPDATED_BY = 'admin_user' WHERE ID = 1;
```

### Office Counter Management

```sql
-- View all office counters
SELECT OFFICE_NAME, OFFICE_CODE, COUNTER FROM OFFICE_CODE_MASTER ORDER BY OFFICE_CODE;

-- Reset specific office counter
UPDATE OFFICE_CODE_MASTER SET COUNTER = 0 WHERE OFFICE_CODE = '013';

-- Reset all counters (new financial year)
UPDATE OFFICE_CODE_MASTER SET COUNTER = 0;
```

### Sequence Management

```sql
-- View current sequences
SELECT * FROM COMPLAINT_NUMBER_SEQUENCE ORDER BY FINANCIAL_YEAR DESC, OFFICE_CODE;

-- Sequences do NOT need manual reset at FY boundary (new rows created automatically)
```

---

## 10. Testing Checklist

| Scenario | Expected Behavior |
|----------|-------------------|
| Mumbai pincode 400064 (Mumbai Suburban) | Resolves to Mumbai-I (013) |
| Pune pincode (Maharashtra, not Mumbai) | Resolves to Mumbai-II (021) |
| Kanpur pincode 208004 | Resolves to Kanpur (011), not Dehradun |
| Meerut district (UP) | Resolves to Dehradun (017) |
| Ghaziabad district (UP) | Resolves to New Delhi-II (016) |
| Target office threshold met | Overflows to Priority 1 |
| Priority 1 also met | Overflows to Priority 2 |
| All 3 offices met | Resets all 3 counters, assigns to target |
| EMAIL filing type | No counter increment, direct assignment |
| PHYSICAL_LETTER filing type | No counter increment, direct assignment |
| Concurrent requests | No duplicate sequences (pessimistic lock) |
| New financial year (April 1) | New sequence row created, starts at 1 |

---

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Wrong office selected | District not in jurisdiction text | Add district to OMBUDSMAN_OFFICE_MASTER.JURISDICTION |
| Office code "014" used unexpectedly | Office name mismatch (hyphen vs space) | Verify OFFICE_CODE_MASTER.OFFICE_NAME matches exactly |
| Duplicate complaint number error | Sequence table out of sync | Set LAST_SEQUENCE to MAX existing sequence for that office+FY |
| Counter not incrementing | Vernacular/CRPC complaint | Expected behavior — these bypass threshold |
| Overflow not working | Missing OFFICE_OVERFLOW_MAPPING row | Insert mapping for the target office code |
| All complaints going to same office | Global threshold too high | Lower THRESHOLD_VALUE in config table |
