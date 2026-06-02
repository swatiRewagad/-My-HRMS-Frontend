# CMS 2.0 - Rules Management in Production-Grade Financial Systems

## Table of Contents
1. [How Financial Institutions Handle Business Rules](#how-financial-institutions-handle-business-rules)
2. [Production Architecture Pattern](#production-architecture-pattern)
3. [Our Implementation Design (Option A)](#our-implementation-design)
4. [Rule Lifecycle Management](#rule-lifecycle-management)
5. [Safety & Compliance Controls](#safety--compliance-controls)

---

## How Financial Institutions Handle Business Rules

### Industry Standard: Business Rules Management System (BRMS)

Production-grade financial systems (Banks, RBI, SEBI, Insurance) use a dedicated **BRMS layer** that separates business logic from application code. This allows compliance teams, operations managers, and business analysts to modify rules without developer involvement.

### Common Enterprise Solutions

| Solution | Used By | Approach |
|----------|---------|----------|
| **IBM ODM (Operational Decision Manager)** | SBI, HDFC, RBI internal | Central rule repository, versioning, decision tables, REST API |
| **Red Hat Decision Manager (Drools-based)** | ICICI, Axis Bank | KIE Server, rule repository (Git-backed), decision tables |
| **Pegasystems** | Insurance, Wealth management | Low-code rule authoring, case management |
| **FICO Blaze Advisor** | Credit scoring, Fraud detection | Specialized for financial decisioning |
| **Custom BRMS (Database-driven Drools)** | Many mid-size banks, NBFCs | What we are implementing — flexible, cost-effective |

### Key Principles in Financial Rule Management

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PRODUCTION RULE MANAGEMENT                        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. SEPARATION OF CONCERNS                                          │
│     Business rules NEVER live inside application code               │
│     Rules are externalized and managed independently                │
│                                                                     │
│  2. MAKER-CHECKER (4-EYE PRINCIPLE)                                 │
│     Rule Author (Maker) → Reviewer (Checker) → Deployed            │
│     No single person can create AND activate a rule                 │
│                                                                     │
│  3. VERSIONING & ROLLBACK                                           │
│     Every rule change creates a new version                         │
│     Previous versions retained for audit                            │
│     Instant rollback capability                                     │
│                                                                     │
│  4. AUDIT TRAIL                                                     │
│     Who changed what, when, and why                                 │
│     Required by RBI IT governance (RBI/2023-24/15)                  │
│                                                                     │
│  5. TESTING IN ISOLATION                                            │
│     Rules tested in sandbox before production                       │
│     Simulation mode: "what-if" analysis                             │
│                                                                     │
│  6. ZERO-DOWNTIME DEPLOYMENT                                        │
│     Rules hot-reloaded without service restart                      │
│     No impact on in-flight transactions                             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Production Architecture Pattern

### Enterprise BRMS Architecture (What Banks Use)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         RULE MANAGEMENT LAYER                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────────┐     │
│  │  Rule Author │    │  Rule Reviewer   │    │  Rule Admin       │     │
│  │  (Business   │───►│  (Compliance /   │───►│  (IT Operations)  │     │
│  │   Analyst)   │    │   Risk Team)     │    │                   │     │
│  └──────────────┘    └──────────────────┘    └───────────────────┘     │
│         │                     │                        │                │
│         ▼                     ▼                        ▼                │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │              RULES MANAGEMENT PORTAL (Admin UI)              │       │
│  │  • Rule Editor (DRL / Decision Table / Natural Language)     │       │
│  │  • Version Control & Diff View                               │       │
│  │  • Test Sandbox (Simulate rule execution)                    │       │
│  │  • Approval Workflow (Maker-Checker)                         │       │
│  │  • Deployment Pipeline                                       │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                              │                                          │
├──────────────────────────────┼──────────────────────────────────────────┤
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │              RULES REPOSITORY (Database)                     │       │
│  │                                                              │       │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │       │
│  │  │ Rule         │  │ Rule         │  │ Rule            │   │       │
│  │  │ Definitions  │  │ Versions     │  │ Audit Log       │   │       │
│  │  └──────────────┘  └──────────────┘  └─────────────────┘   │       │
│  │                                                              │       │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────────┐   │       │
│  │  │ Rule         │  │ Rule         │  │ Deployment      │   │       │
│  │  │ Categories   │  │ Test Cases   │  │ History         │   │       │
│  │  └──────────────┘  └──────────────┘  └─────────────────┘   │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                              │                                          │
├──────────────────────────────┼──────────────────────────────────────────┤
│                              ▼                                          │
│  ┌─────────────────────────────────────────────────────────────┐       │
│  │              RULE EXECUTION ENGINE                            │       │
│  │                                                              │       │
│  │  ┌──────────────────────────────────────────────────┐       │       │
│  │  │  Dynamic KieContainer (Hot-Reloadable)           │       │       │
│  │  │  • Compiles DRL at runtime                       │       │       │
│  │  │  • Atomic swap (old → new) on rule change        │       │       │
│  │  │  • In-flight requests complete on old rules      │       │       │
│  │  │  • New requests use updated rules                │       │       │
│  │  └──────────────────────────────────────────────────┘       │       │
│  │                                                              │       │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐    │       │
│  │  │ Assignment  │  │ Eligibility │  │ Escalation      │    │       │
│  │  │ Rules       │  │ Rules       │  │ Rules           │    │       │
│  │  └─────────────┘  └─────────────┘  └─────────────────┘    │       │
│  └─────────────────────────────────────────────────────────────┘       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### How Rule Changes Flow in Production

```
Step 1: Business Analyst creates/modifies rule in Admin Portal
           │
Step 2: System validates DRL syntax and runs test cases
           │
Step 3: Rule enters "PENDING_REVIEW" state
           │
Step 4: Compliance/Risk reviewer approves or rejects
           │
Step 5: On approval → Rule version incremented
           │
Step 6: Admin triggers deployment (or scheduled deployment window)
           │
Step 7: Rule Engine receives "reload" signal
           │
Step 8: New KieContainer compiled from latest active rules
           │
Step 9: Atomic swap — new container replaces old
           │
Step 10: Audit log records: who, what, when, why
```

---

## Our Implementation Design

### Database Schema

```sql
-- Rule category/group
CREATE TABLE RULE_CATEGORY (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(50) UNIQUE NOT NULL,    -- 'ASSIGNMENT', 'ELIGIBILITY', 'ESCALATION'
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Main rule definitions
CREATE TABLE RULE_DEFINITION (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_code       VARCHAR(100) UNIQUE NOT NULL,   -- 'ASSIGN_ATM_TEAM'
    rule_name       VARCHAR(200) NOT NULL,          -- 'ATM Complaints - Assign to ATM Team'
    category_id     BIGINT NOT NULL,
    drl_content     CLOB NOT NULL,                  -- Full DRL rule text
    salience        INT DEFAULT 100,
    version         INT DEFAULT 1,
    status          VARCHAR(20) DEFAULT 'DRAFT',    -- DRAFT, PENDING_REVIEW, ACTIVE, INACTIVE, ARCHIVED
    effective_from  TIMESTAMP,
    effective_to    TIMESTAMP,                      -- NULL = no expiry
    created_by      VARCHAR(100) NOT NULL,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100),
    updated_at      TIMESTAMP,
    approved_by     VARCHAR(100),
    approved_at     TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES RULE_CATEGORY(id)
);

-- Version history (immutable audit)
CREATE TABLE RULE_VERSION_HISTORY (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id         BIGINT NOT NULL,
    version         INT NOT NULL,
    drl_content     CLOB NOT NULL,
    change_reason   VARCHAR(500),
    changed_by      VARCHAR(100) NOT NULL,
    changed_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    action          VARCHAR(20) NOT NULL,           -- CREATED, MODIFIED, ACTIVATED, DEACTIVATED, ROLLED_BACK
    FOREIGN KEY (rule_id) REFERENCES RULE_DEFINITION(id)
);

-- Test cases for rules
CREATE TABLE RULE_TEST_CASE (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_id         BIGINT NOT NULL,
    test_name       VARCHAR(200) NOT NULL,
    input_json      CLOB NOT NULL,                  -- Test input as JSON
    expected_output CLOB NOT NULL,                  -- Expected result as JSON
    created_by      VARCHAR(100),
    FOREIGN KEY (rule_id) REFERENCES RULE_DEFINITION(id)
);

-- Deployment log
CREATE TABLE RULE_DEPLOYMENT_LOG (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    deployment_id   VARCHAR(50) NOT NULL,
    rules_snapshot  CLOB NOT NULL,                  -- All active rules at deployment time
    deployed_by     VARCHAR(100) NOT NULL,
    deployed_at     TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status          VARCHAR(20) NOT NULL,           -- SUCCESS, FAILED, ROLLED_BACK
    error_message   VARCHAR(2000)
);
```

### REST API Design

```
┌─────────────────────────────────────────────────────────────────────┐
│                    RULES MANAGEMENT API                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  GET    /api/v1/rules                    List all rules (filtered)  │
│  GET    /api/v1/rules/{id}               Get rule details           │
│  POST   /api/v1/rules                    Create new rule (DRAFT)    │
│  PUT    /api/v1/rules/{id}               Update rule (new version)  │
│  DELETE /api/v1/rules/{id}               Archive rule (soft delete) │
│                                                                     │
│  POST   /api/v1/rules/{id}/activate      Activate rule              │
│  POST   /api/v1/rules/{id}/deactivate    Deactivate rule            │
│  POST   /api/v1/rules/{id}/rollback      Rollback to prev version   │
│                                                                     │
│  POST   /api/v1/rules/validate           Validate DRL syntax        │
│  POST   /api/v1/rules/test               Execute rule in sandbox    │
│  POST   /api/v1/rules/deploy             Reload all active rules    │
│                                                                     │
│  GET    /api/v1/rules/{id}/history       Version history            │
│  GET    /api/v1/rules/categories         List rule categories       │
│  GET    /api/v1/rules/deployments        Deployment history         │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Dynamic Rule Engine (Hot Reload)

```java
// How the KieContainer is rebuilt at runtime (simplified)

@Service
public class DynamicRuleEngine {

    private volatile KieContainer kieContainer;  // Atomic reference for thread safety

    public void reloadRules(List<RuleDefinition> activeRules) {
        KieServices kieServices = KieServices.Factory.get();
        KieFileSystem kfs = kieServices.newKieFileSystem();

        for (RuleDefinition rule : activeRules) {
            kfs.write("src/main/resources/rules/" + rule.getRuleCode() + ".drl",
                      rule.getDrlContent());
        }

        KieBuilder builder = kieServices.newKieBuilder(kfs).buildAll();

        if (builder.getResults().hasMessages(Message.Level.ERROR)) {
            throw new RuleCompilationException(builder.getResults().getMessages());
        }

        // Atomic swap — thread-safe replacement
        KieContainer newContainer = kieServices.newKieContainer(
            builder.getKieModule().getReleaseId());
        KieContainer oldContainer = this.kieContainer;
        this.kieContainer = newContainer;

        // Old container still serves in-flight requests until GC
        if (oldContainer != null) {
            oldContainer.dispose();
        }
    }
}
```

---

## Rule Lifecycle Management

### State Machine for Rules

```
                    ┌──────────────────────────────────────────┐
                    │                                          │
                    ▼                                          │
┌───────┐    ┌──────────────┐    ┌────────┐    ┌──────────┐  │
│ DRAFT │───►│PENDING_REVIEW│───►│ ACTIVE │───►│ INACTIVE │  │
└───────┘    └──────────────┘    └────────┘    └──────────┘  │
    │              │                  │              │         │
    │              │ (Rejected)       │              │         │
    │              ▼                  │              ▼         │
    │         ┌────────┐             │         ┌──────────┐  │
    │         │ DRAFT  │             │         │ ARCHIVED │  │
    │         │(Revised)│            │         └──────────┘  │
    │         └────────┘             │                        │
    │                                │ (Rollback)             │
    └────────────────────────────────┼────────────────────────┘
                                     │
                                     ▼
                              Previous version
                              becomes ACTIVE
```

### Maker-Checker Workflow

```
┌──────────────────────────────────────────────────────────────────┐
│                    MAKER-CHECKER PROCESS                          │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  MAKER (Business Analyst / Operations Team)                      │
│  ├── Creates or modifies a rule                                  │
│  ├── Writes test cases                                           │
│  ├── Runs simulation in sandbox                                  │
│  └── Submits for review                                          │
│                                                                  │
│  CHECKER (Compliance Officer / Senior Manager)                   │
│  ├── Reviews rule logic                                          │
│  ├── Validates against regulatory requirements                   │
│  ├── Runs test cases independently                               │
│  ├── Approves OR rejects with comments                           │
│  └── On approval → Rule becomes ACTIVE                           │
│                                                                  │
│  SYSTEM (Automated)                                              │
│  ├── Validates DRL syntax on every save                          │
│  ├── Runs regression tests on activation                         │
│  ├── Hot-reloads KieContainer                                    │
│  ├── Publishes Kafka event: rule.deployed                        │
│  └── Records full audit trail                                    │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Safety & Compliance Controls

### For RBI-Regulated Systems

| Control | Implementation | Why |
|---------|---------------|-----|
| **Maker-Checker** | 2-person approval for rule activation | RBI IT Governance mandates dual control for business logic changes |
| **Immutable Audit** | `RULE_VERSION_HISTORY` table (append-only) | Regulatory audit requirement — who changed what and when |
| **Effective Dating** | `effective_from` / `effective_to` columns | Rules can be scheduled for future activation (e.g., new circular compliance) |
| **Rollback** | One-click revert to previous version | Immediate recovery if rule causes incorrect routing |
| **Sandbox Testing** | Test execution without affecting live data | Prevents untested rules from reaching production |
| **Rate Limiting** | Max N rule deployments per hour | Prevents accidental mass changes |
| **Change Window** | Rules can only be deployed during approved maintenance windows | Aligned with bank's change management process |
| **Regulatory Tagging** | Rules tagged with circular/regulation reference | Traceability to RBI circular that mandates the rule |

### Example: Adding a New Category Assignment Rule

```
Scenario: RBI issues new circular requiring "Digital Lending" complaints
          to be handled by a specialized team.

Step 1: Operations team logs into Rules Admin Portal

Step 2: Creates new rule:
         Name: "Digital Lending - Assign to Fintech Team"
         Category: ASSIGNMENT
         DRL:
           rule "Digital Lending Complaints"
               salience 100
               when
                   $fact : AssignmentFact(category == "DIGITAL_LENDING")
               then
                   $fact.setAssignedTeam("FINTECH_TEAM");
                   update($fact);
           end
         Regulatory Reference: RBI/2026-27/XX

Step 3: Adds test case:
         Input: { "category": "DIGITAL_LENDING", "priority": "MEDIUM" }
         Expected: { "assignedTeam": "FINTECH_TEAM" }

Step 4: Runs sandbox test → PASS

Step 5: Submits for review

Step 6: Compliance officer reviews, approves

Step 7: System hot-reloads rules → New complaints with
         category "DIGITAL_LENDING" now route to FINTECH_TEAM

Step 8: Audit log records the entire chain

         Total time: ~30 minutes (vs. days for code change + deployment)
```

---

## Comparison: Our Approach vs Enterprise BRMS

| Aspect | Our Implementation | Enterprise BRMS (IBM ODM / Red Hat) |
|--------|-------------------|--------------------------------------|
| **Cost** | Zero license cost (Drools OSS) | ₹50L - ₹2Cr/year licensing |
| **Rule Authoring** | DRL text editor in Admin UI | Visual decision tables, natural language |
| **Complexity** | Moderate (sufficient for CMS) | High (overkill for <500 rules) |
| **Scalability** | Horizontal (multiple service instances) | Built-in clustering |
| **Maker-Checker** | Custom implementation | Built-in workflow |
| **Audit** | Custom tables | Built-in compliance reporting |
| **Vendor Lock-in** | None (standard Drools DRL) | High |
| **Time to Implement** | 2-3 weeks | 2-3 months + training |
| **Best For** | Mid-size systems, 50-500 rules | Large banks with 1000+ rules |

---

## Summary

For CMS 2.0, the **Database-Driven Drools** approach gives us:
- ✅ Zero-downtime rule updates
- ✅ Full audit trail for RBI compliance
- ✅ Maker-checker approval workflow
- ✅ Version history with rollback
- ✅ Sandbox testing before activation
- ✅ No vendor lock-in
- ✅ Cost-effective for the scale of rules we manage
