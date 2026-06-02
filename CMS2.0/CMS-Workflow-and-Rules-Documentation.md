# CMS 2.0 - Workflow & Business Rules Documentation

## Table of Contents
1. [Complaint Lifecycle Workflow (BPMN)](#complaint-lifecycle-workflow-bpmn)
2. [Complaint Status States](#complaint-status-states)
3. [Kafka Event Topics](#kafka-event-topics)
4. [Business Rules (Drools)](#business-rules-drools)
5. [SLA Configuration](#sla-configuration)
6. [Service Architecture](#service-architecture)

---

## Complaint Lifecycle Workflow (BPMN)

**Process ID:** `com.rbi.cms.complaint-lifecycle`  
**File:** `cms-workflow-service/src/main/resources/processes/complaint-lifecycle.bpmn2`

### Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        COMPLAINT LIFECYCLE WORKFLOW                          │
└─────────────────────────────────────────────────────────────────────────────┘

[START: Complaint Ingested]
         │
         ▼
┌─────────────────────┐
│   Auto Assignment   │  ◄── Drools Business Rule Task (ruleFlowGroup: assignment)
│  (BusinessRuleTask) │      Assigns team based on category/priority
└─────────────────────┘
         │
         ▼
┌─────────────────────────────┐
│  Officer Review & Accept    │ ◄───────────────────────────────┐
│       (UserTask)            │                                  │
│  Owner: #{assignedTeam}     │                                  │
└─────────────────────────────┘                                  │
         │              │                                        │
         │              └── [SLA Timer: 30 Days] ────────────┐   │
         │                  (BoundaryEvent, non-cancelling)   │   │
         ▼                                                   │   │
┌─────────────────────────┐                                  │   │
│  Investigation Gateway  │                                  │   │
│   (ExclusiveGateway)    │                                  │   │
└─────────────────────────┘                                  │   │
    │                  │                                     │   │
    │ (no escalation)  │ (escalation reason present)         │   │
    ▼                  ▼                                     │   │
┌──────────────┐  ┌──────────────────┐                      │   │
│   Draft      │  │  Escalated       │ ◄────────────────────┘   │
│  Resolution  │  │   Review         │                          │
│  (UserTask)  │  │  (UserTask)      │──────────────────────────┘
└──────────────┘  └──────────────────┘    (Reassignment back)
    │                  │
    └────────┬─────────┘
             ▼
┌─────────────────────────────┐
│    Resolution Gateway       │
│    (ExclusiveGateway -      │
│     Converging)             │
└─────────────────────────────┘
             │
             ▼
┌─────────────────────────────┐
│  Notify Customer of         │  ◄── ServiceTask (java implementation)
│  Resolution                 │      Handler: com.rbi.cms.workflow.handler.NotificationHandler
└─────────────────────────────┘
             │
             ▼
┌─────────────────────────────┐
│  Wait for Confirmation      │  ◄── IntermediateCatchEvent (Timer: 7 days)
│  (7 days auto-close)        │
└─────────────────────────────┘
             │
             ▼
[END: Complaint Closed]
```

### Process Variables

| Variable | Type | Description |
|----------|------|-------------|
| `complaintId` | String | Unique complaint reference number |
| `category` | String | Complaint category (ATM, UPI, LOAN, etc.) |
| `priority` | String | Priority level (LOW, MEDIUM, HIGH, CRITICAL) |
| `assignedTeam` | String | Team assigned by Drools rules |
| `assignedOfficer` | String | Specific officer assigned |
| `resolutionSummary` | String | Resolution details drafted by officer |
| `escalationReason` | String | Reason for escalation (if any) |

### Gateway Conditions

| Gateway | Condition | Target |
|---------|-----------|--------|
| Investigation Gateway | `#{escalationReason == null}` | Draft Resolution (normal path) |
| Investigation Gateway | `#{escalationReason != null}` | Escalated Review |

---

## Complaint Status States

**Enum:** `com.rbi.cms.common.enums.ComplaintStatus`

```
NEW ──► ASSIGNED ──► IN_PROGRESS ──► UNDER_REVIEW ──► RESOLVED ──► CLOSED
                          │                                ▲
                          │                                │
                          └──► ESCALATED ──────────────────┘
```

| Status | Description | Trigger |
|--------|-------------|---------|
| `NEW` | Complaint just registered | Complaint submission |
| `ASSIGNED` | Auto-assigned to team/officer | Drools assignment rules executed |
| `IN_PROGRESS` | Officer accepted and started investigation | Officer accepts task |
| `UNDER_REVIEW` | Resolution being drafted | Officer submits investigation findings |
| `ESCALATED` | SLA breached or manually escalated | SLA timer (30 days) or manual escalation |
| `RESOLVED` | Resolution completed, customer notified | Resolution drafted and approved |
| `CLOSED` | Complaint lifecycle complete | Customer confirmation or 7-day auto-close |

---

## Kafka Event Topics

**Configuration:** `com.rbi.cms.common.config.KafkaTopics`

| Topic | Event | Producer Service | Consumer Service(s) |
|-------|-------|-----------------|---------------------|
| `complaint.ingested` | New complaint registered | Ingestion Service | Workflow Service, Assignment Service |
| `complaint.assigned` | Complaint assigned to team | Assignment Service | Workflow Service |
| `complaint.inprogress` | Officer started work | Workflow Service | Audit Service |
| `complaint.escalated` | SLA breach / priority escalation | Workflow Service | Assignment Service, Notification Service |
| `complaint.resolved` | Resolution completed | Workflow Service | Notification Service, Audit Service |
| `complaint.closed` | Complaint closed | Workflow Service | Audit Service, Search Service |
| `complaint.dlq` | Failed event processing | Any Service | Monitoring/Alerting |

### Event Payload Structure

```json
{
  "eventId": "UUID",
  "complaintId": "CMP-YYYYMMDD-NNNNNN",
  "previousStatus": "NEW",
  "currentStatus": "ASSIGNED",
  "assignedTo": "ATM_TEAM",
  "payload": "{\"category\":\"ATM\",\"priority\":\"HIGH\"}",
  "occurredAt": "2026-05-26T12:47:10Z",
  "correlationId": "UUID"
}
```

---

## Business Rules (Drools)

### 1. Eligibility Rules

**File:** `cms-eligibility-service/src/main/resources/rules/eligibility-rules.drl`  
**Package:** `com.rbi.cms.eligibility.rules`  
**Fact Class:** `com.rbi.cms.eligibility.dto.EligibilityFact`

These rules determine whether a customer is eligible to file a complaint with RBI Ombudsman.

| # | Rule Name | Salience | Condition | Result | Reason Code |
|---|-----------|----------|-----------|--------|-------------|
| 1 | Court Matter Pending | 100 | `courtMatterPending == true` | NOT ELIGIBLE | `COURT_MATTER_PENDING` |
| 2 | Bank Not Approached | 90 | `approachedBank == false` | NOT ELIGIBLE | `BANK_NOT_APPROACHED` |
| 3 | Waiting Period Not Completed | 80 | `waitingPeriodCompleted == false` AND `approachedBank == true` | NOT ELIGIBLE | `WAITING_PERIOD_NOT_COMPLETED` |
| 4 | Duplicate Complaint | 70 | `duplicateComplaint == true` | NOT ELIGIBLE | `DUPLICATE_COMPLAINT` |
| 5 | All Checks Passed | 10 | All conditions met | ELIGIBLE | `ALL_CHECKS_PASSED` |

**Eligibility Fact Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `courtMatterPending` | boolean | Whether matter is sub-judice in court |
| `approachedBank` | boolean | Customer has approached bank first |
| `waitingPeriodCompleted` | boolean | 30-day waiting period after bank approach |
| `duplicateComplaint` | boolean | Similar complaint already exists |
| `jurisdictionCode` | String | Jurisdiction identifier |
| `complaintCategory` | String | Category of complaint |
| `eligible` | boolean | Final eligibility result (starts true) |
| `reasonCode` | String | Machine-readable reason |
| `reasonMessage` | String | Human-readable explanation |

---

### 2. Assignment Rules

**File:** `cms-assignment-service/src/main/resources/rules/assignment-rules.drl`  
**Package:** `com.rbi.cms.rules.assignment`  
**Fact Class:** `com.rbi.cms.common.dto.AssignmentFact`

These rules auto-assign complaints to the appropriate team based on category and priority.

| # | Rule Name | Salience | Condition | Assigned Team | Notes |
|---|-----------|----------|-----------|---------------|-------|
| 1 | ATM Complaints | 100 | `category == "ATM"` | `ATM_TEAM` | ATM/debit card issues |
| 2 | UPI Complaints | 100 | `category == "UPI"` | `DIGITAL_TEAM` | UPI/digital payment issues |
| 3 | NEFT/RTGS Complaints | 100 | `category == "NEFT_RTGS"` | `PAYMENT_SYSTEMS_TEAM` | Fund transfer issues |
| 4 | Loan Complaints | 100 | `category == "LOAN"` | `LENDING_TEAM` | Loan/EMI related issues |
| 5 | Credit Card Complaints | 100 | `category == "CREDIT_CARD"` | `CARDS_TEAM` | Credit card disputes |
| 6 | High Priority Auto-Escalate | 50 | `priority == "HIGH"` OR `priority == "CRITICAL"` | Escalated (level 1) | Auto-escalates high priority |
| 7 | Default Assignment | 10 | `assignedTeam == null` | `GENERAL_TEAM` | Fallback for unmatched categories |

**Assignment Fact Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `complaintId` | String | Complaint reference number |
| `category` | String | Complaint category |
| `priority` | String | Priority level |
| `jurisdictionCode` | String | Geographic jurisdiction |
| `amountInvolved` | Double | Transaction amount |
| `assignedTeam` | String | (Output) Team assigned |
| `assignedOfficer` | String | (Output) Specific officer |
| `escalated` | boolean | (Output) Whether auto-escalated |
| `escalationLevel` | int | (Output) Escalation level |

---

### 3. Escalation Rules

**File:** `cms-rules-service/src/main/resources/rules/escalation-rules.drl`  
**Package:** `com.rbi.cms.rules.escalation`  
**Fact Class:** `com.rbi.cms.common.dto.EscalationFact`

These rules handle SLA-based escalation and high-value complaint routing.

| # | Rule Name | Salience | Condition | Escalation Level | Action |
|---|-----------|----------|-----------|-----------------|--------|
| 1 | SLA Breach Level 1 (80% elapsed) | 100 | `slaPercentElapsed >= 80` AND `< 100` AND `level == 0` | 1 | `NOTIFY_OFFICER` |
| 2 | SLA Breach Level 2 (100% elapsed) | 90 | `slaPercentElapsed >= 100` AND `level < 2` | 2 | `REASSIGN_SUPERVISOR` |
| 3 | SLA Breach Level 3 (150% elapsed) | 80 | `slaPercentElapsed >= 150` AND `level < 3` | 3 | `ESCALATE_MANAGEMENT` |
| 4 | High Value Complaint (> 10 Lakh) | 70 | `amountInvolved > 1,000,000` AND `level < 2` | 2 | `ASSIGN_SENIOR_OFFICER` |

**Escalation Fact Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `complaintId` | String | Complaint reference number |
| `category` | String | Complaint category |
| `priority` | String | Priority level |
| `slaPercentElapsed` | double | Percentage of SLA time elapsed |
| `amountInvolved` | double | Transaction amount in INR |
| `currentDaysOpen` | int | Days since complaint was filed |
| `escalationLevel` | int | (Output) Escalation tier (0-3) |
| `escalationAction` | String | (Output) Action to take |
| `escalationMessage` | String | (Output) Human-readable message |

**Escalation Matrix:**

```
          Time Elapsed
    0%      80%     100%     150%
    │        │       │        │
    ├────────┼───────┼────────┤
    │ Normal │ L1:   │ L2:    │ L3:
    │        │ Notify│ Reassign│ Escalate to
    │        │Officer│Supervisor│ Management
    └────────┴───────┴────────┘

    High Value (> ₹10L): Directly assigned to Senior Officer (Level 2)
```

---

## SLA Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| Default SLA Period | 30 days | From complaint registration to resolution |
| SLA Timer (BPMN) | P30D | Boundary timer on Officer Review task |
| Auto-Close Timer | P7D (7 days) | After resolution notification to customer |
| Level 1 Warning | 80% (Day 24) | Notify assigned officer |
| Level 2 Breach | 100% (Day 30) | Reassign to supervisor |
| Level 3 Critical | 150% (Day 45) | Escalate to management |

---

## Service Architecture

### Event Flow

```
┌──────────────┐    Kafka: complaint.ingested    ┌──────────────────┐
│  Ingestion   │ ──────────────────────────────► │  Workflow Service │
│  Service     │                                  │  (Port: 8083)    │
│  (Port:8082) │                                  └──────────────────┘
└──────────────┘                                          │
       │                                                  │
       │         Kafka: complaint.ingested                │
       │                                                  │
       ▼                                                  ▼
┌──────────────────┐    Kafka: complaint.assigned   Manages BPMN
│  Assignment      │ ──────────────────────────►   process instance
│  Service         │                               and state transitions
│  (Port: 8085)    │
└──────────────────┘
       │
       │ Drools: assignment-rules.drl
       ▼
  Category → Team mapping
  Priority → Auto-escalation
```

### Dev-Local Profile (Testing)

In `dev-local` profile:
- **Ingestion Service:** Uses `DirectKafkaEventPublisher` (publishes directly to Kafka, bypassing outbox pattern)
- **Workflow Service:** Uses `DevLocalWorkflowService` (logs workflow steps instead of jBPM engine)
- **Assignment Service:** Full Drools rules engine active
- **Database:** H2 in-memory (Oracle compatibility mode)
- **Kafka:** Required (localhost:9092)

### Production Profile

In production:
- **Ingestion Service:** Uses `OutboxEventPublisher` (transactional outbox pattern for guaranteed delivery)
- **Workflow Service:** Uses `WorkflowService` with jBPM runtime engine (full BPMN execution)
- **Assignment Service:** Full Drools rules engine
- **Database:** Oracle/PostgreSQL
- **Kafka:** Managed cluster with replication

---

## API Endpoints (Workflow)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/workflow/{complaintId}/transition` | Transition complaint to next state |
| POST | `/api/v1/workflow/{complaintId}/escalate` | Manually escalate a complaint |

### Transition Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `complaintId` | PathVariable | Yes | Complaint reference number |
| `targetStatus` | RequestParam | Yes | Target ComplaintStatus enum value |
| `remarks` | RequestParam | No | Transition remarks |

### Escalation Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `complaintId` | PathVariable | Yes | Complaint reference number |
| `reason` | RequestParam | Yes | Reason for escalation |
