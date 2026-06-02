# CMS 1.0 → CMS 2.0 Data Migration Strategy

**Document Version:** 1.0  
**Date:** 2026-06-02  
**Prepared By:** CMS2.0 Development Team  
**Status:** Draft (Pending DIT/CEPD Sign-off per NFR-012)

---

## 1. Executive Summary

This document outlines the data migration strategy from CMS 1.0 (monolithic, MySQL-based) to CMS 2.0 (microservices, event-driven architecture). The migration covers ~7 core tables and their associated file attachments, ensuring zero data loss and full traceability.

---

## 2. Source & Target Architecture Comparison

### 2.1 CMS 1.0 (Source)
- **Database:** MySQL 8+ (single schema `cms_db`)
- **Architecture:** Monolithic Spring Boot application
- **Tables:** COMPLAINTS, COMPLAINT_ATTACHMENTS, COMPLAINT_TIMELINE, BANKS, COMPLAINT_CATEGORIES, CMS_USERS, FORM_CONFIGS, SIMULATED_EMAILS
- **ID Strategy:** AUTO_INCREMENT (BIGINT)
- **Timestamps:** `LocalDateTime` (no timezone)
- **Status Values:** Free-text strings (`pending`, `in_progress`, `resolved`, `closed`, `escalated`)
- **File Storage:** Local filesystem (path in `storage_path` column)

### 2.2 CMS 2.0 (Target)
- **Database:** PostgreSQL / Oracle (per Data Centre policy) - separate schemas per microservice
- **Architecture:** Microservices (Ingestion, Rules, Eligibility, Audit, Outbox)
- **Core Tables:** COMPLAINT_MASTER, COMPLAINT_HISTORY, ATTACHMENT_METADATA, OUTBOX_EVENT
- **ID Strategy:** SEQUENCE-based (with explicit generators)
- **Timestamps:** `Instant` (UTC, timezone-aware)
- **Status Values:** Enum (`NEW`, `ASSIGNED`, `IN_PROGRESS`, `UNDER_REVIEW`, `ESCALATED`, `RESOLVED`, `CLOSED`)
- **File Storage:** Object Storage (S3-compatible) with checksum verification
- **Event Sourcing:** Kafka-based via Outbox pattern

---

## 3. Field Mapping

### 3.1 COMPLAINTS → COMPLAINT_MASTER

| CMS 1.0 Field | CMS 2.0 Field | Transformation |
|---|---|---|
| `id` | — | Not migrated (new sequence-based ID) |
| `complaint_number` | `COMPLAINT_ID` | Direct mapping (retain original IDs for trackability) |
| — | `CHANNEL` | Set to `WEB_PORTAL` (default for CMS1.0 portal complaints) |
| `category_id` → category.name | `CATEGORY` | Map category name to `ComplaintCategory` enum |
| `status` | `STATUS` | Transform: `pending`→`NEW`, `in_progress`→`IN_PROGRESS`, `resolved`→`RESOLVED`, `closed`→`CLOSED`, `escalated`→`ESCALATED` |
| `priority` | `PRIORITY` | Transform: `low`→`LOW`, `medium`→`MEDIUM`, `high`→`HIGH` |
| `complainant_name` | `COMPLAINANT_NAME` | Direct |
| `complainant_email` | `COMPLAINANT_EMAIL` | Direct |
| `complainant_phone` | `COMPLAINANT_PHONE` | Direct |
| `bank_id` → bank.name | `ENTITY_NAME` | Resolve bank name via JOIN |
| — | `ENTITY_TYPE` | Set to `BANK` (default) |
| `subject` | `SUBJECT` | Direct |
| `description` | `DESCRIPTION` | Direct (truncate if >4000 chars, store overflow in history) |
| — | `AMOUNT_INVOLVED` | NULL (not captured in CMS1.0) |
| `bank_complaint_date` | `TRANSACTION_DATE` | Convert LocalDateTime → LocalDate |
| — | `JURISDICTION_CODE` | Derive from complainant state/pincode |
| `assigned_officer` | `ASSIGNED_TO` | Direct |
| — | `ASSIGNED_TEAM` | Derive from officer mapping table |
| — | `SLA_DUE_DATE` | Calculate: `filed_at` + 30 days |
| — | `RESOLUTION_SUMMARY` | Extract from last timeline entry with `action='RESOLVED'` |
| `resolved_at` | `RESOLVED_AT` | Convert LocalDateTime → Instant (assume IST) |
| `closed_at` | `CLOSED_AT` | Convert LocalDateTime → Instant (assume IST) |
| `created_at` | `CREATED_AT` | Convert LocalDateTime → Instant (assume IST) |
| `updated_at` | `UPDATED_AT` | Convert LocalDateTime → Instant (assume IST) |
| — | `CREATED_BY` | Set to `MIGRATION_CMS1` |
| — | `VERSION` | Set to `0` |

### 3.2 COMPLAINT_TIMELINE → COMPLAINT_HISTORY

| CMS 1.0 Field | CMS 2.0 Field | Transformation |
|---|---|---|
| `id` | — | New sequence ID |
| `complaint_id` → complaint.complaint_number | `COMPLAINT_ID` | Resolve to complaint_number string |
| `from_status` | `PREVIOUS_STATUS` | Map to enum (same as status mapping) |
| `to_status` | `NEW_STATUS` | Map to enum |
| `action` | `ACTION` | Direct |
| `remarks` | `REMARKS` | Direct |
| `performed_by` | `PERFORMED_BY` | Direct (default: `SYSTEM` if null) |
| `performed_at` | `PERFORMED_AT` | Convert LocalDateTime → Instant (assume IST) |

### 3.3 COMPLAINT_ATTACHMENTS → ATTACHMENT_METADATA

| CMS 1.0 Field | CMS 2.0 Field | Transformation |
|---|---|---|
| `id` | — | New sequence ID |
| `complaint_id` → complaint.complaint_number | `COMPLAINT_ID` | Resolve to complaint_number string |
| `original_name` | `FILE_NAME` | Direct |
| `content_type` | `CONTENT_TYPE` | Direct |
| `file_size` | `FILE_SIZE` | Direct |
| `storage_path` | `STORAGE_PATH` | New path after S3 upload (see Section 5) |
| — | `CHECKSUM` | Calculate SHA-256 during file copy |
| — | `UPLOADED_BY` | Set to `MIGRATION_CMS1` |
| `uploaded_at` | `UPLOADED_AT` | Convert LocalDateTime → Instant |

### 3.4 BANKS (Reference Data - Re-seeded)

Banks will be re-seeded in CMS 2.0 with enhanced data. Cross-reference mapping table created for complaint FK resolution.

### 3.5 COMPLAINT_CATEGORIES (Reference Data - Remapped)

CMS 1.0 has hierarchical categories (parent_id). CMS 2.0 uses a flat enum. Mapping:

| CMS 1.0 Category | CMS 2.0 Enum |
|---|---|
| ATM / Debit Card | `ATM` |
| Credit Card | `CREDIT_CARD` |
| Internet Banking | `UPI` |
| Mobile Banking / UPI | `UPI` |
| Loan / Advances | `LOAN` |
| Deposit Accounts | `DEPOSIT` |
| Pension | `GENERAL` |
| Remittance / Transfer | `NEFT_RTGS` |
| Insurance | `INSURANCE` |
| Others | `GENERAL` |

### 3.6 CMS_USERS (Not Migrated to Ingestion Service)

User data will be migrated to Keycloak (IAM). A separate user migration script will:
1. Create Keycloak realm users from CMS_USERS table
2. Set temporary passwords requiring change on first login
3. Map phone numbers as verified identifiers

---

## 4. Migration Approach

### 4.1 Strategy: **Big Bang with Parallel Run**

```
Phase 1: Parallel Run (2 weeks)
├── CMS 1.0 continues to serve production traffic
├── CMS 2.0 receives a copy of all new complaints (dual-write via DB trigger)
└── Migration scripts run in background for historical data

Phase 2: Cutover (Maintenance Window)
├── CMS 1.0 set to read-only
├── Final delta sync (complaints created during Phase 1)
├── DNS switch to CMS 2.0
└── CMS 1.0 retained in read-only mode for 90 days
```

### 4.2 Why Not Incremental/Trickle Migration?

- Complaint lifecycle is tightly coupled (complaint + timeline + attachments must move together)
- CMS 1.0 has no event infrastructure to support real-time replication
- Data volume is manageable for batch migration (~100K-500K records estimated)

---

## 5. Migration Phases

### Phase 0: Pre-Migration (Week 1-2)

| Task | Owner | Deliverable |
|---|---|---|
| Freeze CMS 1.0 schema changes | Dev Team | Change freeze notice |
| Export production record counts | DBA | Baseline counts for validation |
| Set up CMS 2.0 target database | Infra | Schemas created, sequences initialized |
| Provision S3 bucket for attachments | Infra | Bucket with versioning enabled |
| Build & test migration scripts | Dev Team | Tested scripts in staging |
| Create rollback plan | Dev Team | Documented rollback procedure |
| DIT/CEPD sign-off on this document | PMO | Signed NFR-012 compliance |

### Phase 1: Reference Data Migration (Day 1)

```sql
-- 1. Migrate Banks → Create mapping table
INSERT INTO bank_migration_map (cms1_bank_id, cms2_entity_name)
SELECT id, name FROM cms1.BANKS WHERE status = 'active';

-- 2. Seed CMS 2.0 reference data (banks, categories defined in code enums)
-- Already handled by CMS 2.0 application startup
```

### Phase 2: Historical Complaint Migration (Day 1-3)

**Batch Size:** 5,000 records per batch  
**Parallelism:** 4 threads  
**Order:** Oldest first (preserves chronological integrity)

```
For each batch of COMPLAINTS:
  1. SELECT complaints with JOINs (bank name, category name)
  2. Transform fields per Section 3 mapping
  3. INSERT into COMPLAINT_MASTER
  4. SELECT related COMPLAINT_TIMELINE entries
  5. Transform and INSERT into COMPLAINT_HISTORY
  6. Record batch progress in migration_audit table
```

### Phase 3: Attachment Migration (Day 2-4)

```
For each COMPLAINT_ATTACHMENT record:
  1. Read file from CMS 1.0 filesystem path
  2. Calculate SHA-256 checksum
  3. Upload to S3 with key: complaints/{complaint_id}/{filename}
  4. INSERT metadata into ATTACHMENT_METADATA with new storage path
  5. Verify checksum post-upload
  6. Log success/failure in migration_audit
```

### Phase 4: Delta Sync (Cutover Day)

```
1. Set CMS 1.0 to maintenance mode
2. Identify records created/modified since Phase 2 start
   (WHERE created_at > migration_start_timestamp OR updated_at > migration_start_timestamp)
3. Run same migration logic for delta records
4. Verify counts match
5. Switch DNS
```

### Phase 5: Post-Migration Validation (Day 1-3 post-cutover)

See Section 7.

---

## 6. Status Value Mapping

### CMS 1.0 → CMS 2.0 Status Transformation

```java
public ComplaintStatus mapStatus(String cms1Status) {
    return switch (cms1Status.toLowerCase()) {
        case "pending"     -> ComplaintStatus.NEW;
        case "assigned"    -> ComplaintStatus.ASSIGNED;
        case "in_progress" -> ComplaintStatus.IN_PROGRESS;
        case "under_review"-> ComplaintStatus.UNDER_REVIEW;
        case "escalated"   -> ComplaintStatus.ESCALATED;
        case "resolved"    -> ComplaintStatus.RESOLVED;
        case "closed"      -> ComplaintStatus.CLOSED;
        default            -> ComplaintStatus.NEW; // Unknown statuses → NEW for manual review
    };
}
```

### Timestamp Conversion

```java
// CMS 1.0 stores LocalDateTime (assumed IST, Asia/Kolkata)
public Instant convertTimestamp(LocalDateTime cms1Time) {
    if (cms1Time == null) return null;
    return cms1Time.atZone(ZoneId.of("Asia/Kolkata")).toInstant();
}
```

---

## 7. Validation & Reconciliation

### 7.1 Automated Checks (Run post-migration)

| Check | Query | Expected |
|---|---|---|
| Total complaint count | `SELECT COUNT(*) FROM COMPLAINT_MASTER WHERE CREATED_BY='MIGRATION_CMS1'` | = CMS 1.0 COMPLAINTS count |
| No null complaint IDs | `SELECT COUNT(*) FROM COMPLAINT_MASTER WHERE COMPLAINT_ID IS NULL` | 0 |
| Status distribution matches | Compare GROUP BY STATUS counts | Match within ±0 |
| Timeline entries migrated | `SELECT COUNT(*) FROM COMPLAINT_HISTORY WHERE PERFORMED_BY LIKE '%MIGRATION%'` | = CMS 1.0 COMPLAINT_TIMELINE count |
| Attachments migrated | `SELECT COUNT(*) FROM ATTACHMENT_METADATA WHERE UPLOADED_BY='MIGRATION_CMS1'` | = CMS 1.0 COMPLAINT_ATTACHMENTS count |
| Attachment checksums valid | Verify random 10% sample checksums against S3 | 100% match |
| No orphan attachments | Attachments without matching complaint | 0 |

### 7.2 Manual Spot Checks

- Pick 50 random complaints from CMS 1.0
- Verify all fields, timeline, and attachments exist correctly in CMS 2.0
- Verify complaint tracking works via CMS 2.0 public portal using old complaint numbers

### 7.3 Reconciliation Report

Generate a reconciliation report with:
- Source vs target counts per table
- Records that failed migration (with error reasons)
- Warnings (e.g., truncated descriptions, unknown categories)

---

## 8. Rollback Plan

| Scenario | Action |
|---|---|
| Migration script fails mid-batch | Resume from last successful batch (tracked in `migration_audit`) |
| Data corruption detected post-cutover | Switch DNS back to CMS 1.0 (read-only period allows this) |
| CMS 2.0 application issues | Revert DNS; CMS 1.0 still operational in parallel |
| Partial attachment failure | Re-run attachment migration for failed records only |

**Point of No Return:** Once CMS 2.0 starts accepting NEW complaints post-cutover, rollback requires reverse-migrating new CMS 2.0 complaints back to CMS 1.0. This is costly — validate thoroughly before removing CMS 1.0 read-only instance.

---

## 9. Data Retention & Archival (NFR-008)

- Migrated data retains original `created_at` timestamps
- Retention policy: 7 years from complaint closure date
- Archived complaints (>7 years) moved to cold storage (S3 Glacier)
- Migration audit logs retained for 2 years

---

## 10. Security Considerations (NFR-013)

- Migration scripts run on private network only (no public internet access)
- Database credentials stored in Vault, rotated after migration
- PII fields (name, email, phone, address) encrypted at rest in CMS 2.0
- File transfer to S3 uses TLS 1.2+ (encryption in transit)
- Migration user has time-limited credentials (revoked post-migration)
- Audit log of all migration operations maintained

---

## 11. Performance Considerations (NFR-007)

- Migration runs during off-peak hours (10 PM - 6 AM IST)
- Batch size tuned to avoid lock contention on CMS 1.0 production DB
- CMS 2.0 database indexes created AFTER bulk insert (faster loading)
- S3 upload uses multipart upload for files >100MB
- Progress monitoring via Grafana dashboard

---

## 12. Dependencies & Prerequisites

| Dependency | Required By | Status |
|---|---|---|
| CMS 2.0 database schemas deployed | Phase 1 | Pending |
| S3 bucket provisioned with IAM policies | Phase 3 | Pending |
| Keycloak realm configured | Phase 1 | Pending |
| Network connectivity (CMS1 DB → CMS2 DB) | Phase 2 | Pending |
| CMS 1.0 read-only mode capability | Cutover | Pending |
| DIT/CEPD document sign-off | Pre-migration | Pending |
| VAPT clearance for CMS 2.0 | Pre-cutover | Pending |

---

## 13. Timeline

```
Week 1-2:  Pre-migration (scripts, testing, sign-offs)
Week 3:    Reference data + Historical migration in staging
Week 4:    UAT validation on migrated data
Week 5:    Production migration (Phase 1-3)
Week 5+1d: Delta sync + Cutover
Week 5-7:  Parallel monitoring period
Week 8:    CMS 1.0 decommission approval
```

---

## 14. Risk Register

| Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|
| Data loss during file transfer | Critical | Low | SHA-256 checksum verification; retry failed uploads |
| Status mapping ambiguity | Medium | Medium | Manual review queue for unknown statuses |
| Downtime exceeds maintenance window | High | Low | Pre-validated scripts; rehearsal run on staging |
| CMS 1.0 schema changes during migration | High | Low | Schema freeze enforced from Day 1 |
| Large attachments timeout during S3 upload | Medium | Medium | Multipart upload; increased timeout; retry logic |
| Complaint number conflicts | Critical | Very Low | CMS 2.0 uses same IDs; UNIQUE constraint prevents duplicates |

---

## 15. Sign-Off

| Role | Name | Date | Signature |
|---|---|---|---|
| DIT Representative | | | |
| CEPD Representative | | | |
| ReBIT Project Lead | | | |
| DBA Lead | | | |
| Security (RMD) | | | |

---

*This document satisfies NFR-012: "ReBIT shall define a comprehensive Data Migration Strategy document, which must be reviewed and signed off by the DIT/CEPD before initiating the data migration exercise."*
