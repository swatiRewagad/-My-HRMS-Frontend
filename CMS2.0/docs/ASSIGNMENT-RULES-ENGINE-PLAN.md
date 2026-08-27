# Assignment Rules Engine — Implementation Plan

**Module:** `cms-assignment-service` (backend) + `cms-portal-frontend` (UI)  
**Status:** Approved for implementation  
**Date:** 2026-08-17  

---

## 1. Current State & Key Decisions

### What Exists Today

| Aspect | Current |
|--------|---------|
| Assignment | `cms-assignment-service` uses Drools (KIE) with hardcoded `.drl` files |
| Rules | `cms-rules-service` stores DRL in DB, compiles dynamically — remains **untouched** |
| Frontend | Angular 21 + PrimeNG 21 standalone components, signals |
| DB | Oracle 21c, identity-generated IDs, manual `V*__` migration scripts in `/database/oracle/` |
| Java | Java 21, Spring Boot 3.4.1 |
| Auth | Keycloak 26, JWT, roles from `realm_access.roles` |

### Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | Replace Drools in `cms-assignment-service` with metadata-driven decision-table engine | Drools is opaque to business users; every routing change requires a developer. See ADR-001 |
| 2 | Leave `cms-rules-service` untouched | It handles eligibility/routing DRL — different concern |
| 3 | UI lives in `cms-portal-frontend` under admin section | Consistent with existing rule-editor/rule-tester location |
| 4 | AG Grid Community for the decision-table grid | PrimeNG Table lacks Excel-like paste/fill-down. See ADR-003 |
| 5 | Continue manual `V*__` Oracle scripts (no Flyway wiring) | Repo convention |
| 6 | Single-tenant now (`RBI-CMS` hardcoded default), multi-tenant configurable | `TENANT_ID` column on all tables, but no Hibernate tenant filter yet. Switchable via config |
| 7 | Java 21, Spring Boot 3.4.1, Angular 21 | Match repo versions, not the prompt's older versions |

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│  cms-portal-frontend (Angular 21)                                       │
│  └── /admin/assignment-studio/*                                         │
│       ├── Ruleset List                                                  │
│       ├── Rule Editor (AG Grid Community + GridAdapter)                  │
│       ├── Version Diff                                                  │
│       ├── Approval Inbox                                                │
│       ├── Simulator (single + bulk)                                     │
│       ├── Decision Log Explorer                                         │
│       └── Fallback & Defaults Config                                    │
└────────────────────────┬────────────────────────────────────────────────┘
                         │ /api/v1/assignment/*
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  cms-assignment-service (Port 8085)                                     │
│                                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │   web/      │  │  service/   │  │   engine/    │  │ persistence/│  │
│  │ Controllers │→ │ Governance  │→ │  Compiler    │  │ Repositories│  │
│  │ REST + RFC  │  │ Simulation  │  │  Evaluator   │  │ Entities    │  │
│  │ 7807 errors │  │ Validation  │  │  Operators   │  │ Tenant fltr │  │
│  └─────────────┘  └─────────────┘  └──────────────┘  └─────────────┘  │
│                                                                         │
│  ┌───────────────────────────────────────┐                              │
│  │ Caffeine Cache (CompiledRuleSet)      │                              │
│  │ Key: (tenantId, decisionPoint, asOf)  │                              │
│  │ Refresh: 30s poller + manual endpoint │                              │
│  └───────────────────────────────────────┘                              │
└─────────────────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────────────────┐
│  Oracle 21c                                                             │
│  Tables: ASGN_ATTRIBUTE, ASGN_RULE_SET, ASGN_RULE, ASGN_DECISION_LOG…  │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Multi-Tenancy Strategy

**Current approach:** Single-tenant with future-proofing.

- Every `ASGN_*` table includes a `TENANT_ID VARCHAR2(50 CHAR) DEFAULT 'RBI-CMS' NOT NULL` column.
- All repository queries include `WHERE TENANT_ID = :tenantId`.
- A `TenantContext` utility resolves the tenant from:
  1. A configurable property `cms.assignment.default-tenant=RBI-CMS` (used now)
  2. (Future) JWT claim or request header `X-Tenant-Id`
- No Hibernate multi-tenant filter wired yet — just explicit query parameters.
- To enable full multi-tenancy later: add a `TenantInterceptor` that sets `TenantContext` from the token, and wire a Hibernate `@FilterDef`.

```java
// Current — single tenant, configurable
@Component
public class TenantContext {
    @Value("${cms.assignment.default-tenant:RBI-CMS}")
    private String defaultTenant;
    
    public String getCurrentTenant() {
        // Future: read from SecurityContext or ThreadLocal
        return defaultTenant;
    }
}
```

---

## 4. File Layout (New & Modified)

```
cms-assignment-service/
  pom.xml                          ← MODIFIED: remove Drools, add Caffeine, POI, jqwik
  src/main/java/com/rbi/cms/assignment/
    AssignmentServiceApplication.java          ← EXISTS (keep)
    config/
      CacheConfig.java
      SecurityConfig.java
      MetricsConfig.java
      TenantContext.java
    domain/
      enums/
        HitPolicy.java             (FIRST, PRIORITY_SPECIFICITY, UNIQUE, COLLECT)
        VersionStatus.java         (DRAFT, PENDING_APPROVAL, APPROVED, PUBLISHED, SUPERSEDED, ARCHIVED)
        DataType.java              (STRING, NUMBER, MONEY, DATE, DATETIME, BOOLEAN, ENUM, STRING_LIST)
        OperatorCode.java          (EQ, NEQ, IN, NOT_IN, GT, GTE, LT, LTE, BETWEEN, ...)
        OutcomeType.java           (USER, GROUP, ROLE_IN_ORG_UNIT, QUEUE, CHAIN)
        AssignMode.java            (AS_GROUP, PICK_MEMBER)
        DistributionStrategy.java  (ROUND_ROBIN, LEAST_ACTIVE_CASES, CAPACITY_WEIGHTED, ...)
        ValueSource.java           (FREE_TEXT, STATIC_LIST, LOOKUP_TABLE, LOOKUP_API)
      entity/
        AsgnAttribute.java
        AsgnAttributeValue.java
        AsgnRuleSet.java
        AsgnRuleSetVersion.java
        AsgnRule.java
        AsgnRuleCondition.java
        AsgnRuleOutcome.java
        AsgnRuleSetDefault.java
        AsgnRuleSetPublication.java
        AsgnExclusionRule.java
        AsgnRrCounter.java
        AsgnDecisionLog.java
        AsgnAuditEvent.java
        AsgnSimJob.java
        AsgnSimResult.java
    engine/
      compiler/
        RuleSetCompiler.java
        CompiledRuleSet.java       (immutable record)
        CompiledRule.java
        CompiledCondition.java
      evaluator/
        RuleEvaluator.java         (the hot path — <25ms p99)
        EvaluationContext.java
        EvaluationResult.java
        TraceEntry.java
      operator/
        ConditionOperator.java     (interface)
        OperatorRegistry.java
        impl/                      (one class per operator group)
          StringOperators.java
          NumericOperators.java
          DateOperators.java
          BooleanOperators.java
          ListOperators.java
      hitpolicy/
        HitPolicyStrategy.java     (interface)
        FirstHitPolicy.java
        PrioritySpecificityHitPolicy.java
        UniqueHitPolicy.java
        CollectHitPolicy.java
      distribution/
        DistributionStrategyHandler.java  (interface)
        RoundRobinStrategy.java
        LeastActiveCasesStrategy.java
        CapacityWeightedStrategy.java
        RandomStrategy.java
        WorkloadProvider.java      (port/interface)
        JpaWorkloadProvider.java   (default impl)
    persistence/
      repository/
        AttributeRepository.java
        AttributeValueRepository.java
        RuleSetRepository.java
        RuleSetVersionRepository.java
        RuleRepository.java
        RuleConditionRepository.java
        RuleOutcomeRepository.java
        PublicationRepository.java
        DecisionLogRepository.java
        AuditEventRepository.java
        RrCounterRepository.java
        SimJobRepository.java
    service/
      AssignmentResolveService.java    ← the hot path orchestrator
      RuleSetAuthoringService.java     ← CRUD, grid save, import/export
      GovernanceService.java           ← state machine, maker-checker
      ValidationService.java           ← static analysis (§11)
      SimulationService.java           ← single-case trace, bulk what-if
      CacheRefreshService.java         ← poller + manual refresh
      DecisionLogService.java          ← query, export
      AuditService.java                ← append audit events
      AttributeRegistryService.java    ← manage attributes
      AvailabilityService.java         ← user filters, OOO, delegation
      ExclusionService.java            ← conflict-of-interest
    web/
      ResolveController.java           ← POST /resolve, /resolve/batch
      AuthoringController.java         ← rulesets, versions, rules CRUD
      AttributeController.java         ← GET /attributes, /attributes/{code}/values
      GovernanceController.java        ← submit, approve, reject, publish
      SimulationController.java        ← single, bulk, job status
      DecisionLogController.java       ← GET /decision-logs
      AuditController.java             ← GET /audit-events
      InternalController.java          ← POST /internal/rules/cache/refresh
    dto/
      request/
        ResolveRequest.java
        BatchResolveRequest.java
        RuleSetCreateRequest.java
        RuleBulkSaveRequest.java
        SimulateSingleRequest.java
        SimulateBulkRequest.java
        ImportRequest.java
      response/
        ResolveResponse.java
        ExplanationDto.java
        TraceDto.java
        ValidationFindingDto.java
        RuleSetDto.java
        VersionDiffDto.java
        SimJobStatusDto.java
  src/main/resources/
    application.yml
    application-dev-local.yml
  src/test/java/com/rbi/cms/assignment/
    engine/
      operator/          ← unit tests for every operator
      evaluator/         ← golden-file tests (CSV-driven)
      compiler/          ← compilation + type coercion tests
      hitpolicy/         ← hit policy semantics tests
    service/
      GovernanceServiceTest.java     ← state machine, immutability
      ValidationServiceTest.java     ← all 14 checks
      SimulationServiceTest.java     ← dry-run flag, no side effects
    web/
      ResolveControllerIntegrationTest.java
    concurrency/
      RoundRobinFairnessTest.java    ← 50 threads
    golden/
      golden-contexts.csv            ← 60+ test contexts
      expected-outcomes.csv

cms-portal-frontend/src/app/
  components/admin/assignment-studio/
    assignment-studio.routes.ts
    ruleset-list/
      ruleset-list.component.ts
      ruleset-list.component.html
      ruleset-list.component.scss
    rule-editor/
      rule-editor.component.ts       ← AG Grid centrepiece
      rule-editor.component.html
      rule-editor.component.scss
      cell-editors/
        operator-value-editor.component.ts
        enum-multi-select-editor.component.ts
        lookup-typeahead-editor.component.ts
        date-editor.component.ts
        boolean-editor.component.ts
        money-range-editor.component.ts
      cell-renderers/
        compact-condition-renderer.component.ts
        outcome-renderer.component.ts
        wildcard-renderer.component.ts
      paste-handler.service.ts       ← clipboard TSV parsing
      undo-redo.service.ts           ← 50-op stack
      grid-adapter.service.ts        ← vendor abstraction
    column-manager/
      column-manager.component.ts
    version-diff/
      version-diff.component.ts
      version-diff.component.html
    approval-inbox/
      approval-inbox.component.ts
      approval-inbox.component.html
    simulator/
      simulator.component.ts
      simulator.component.html
      trace-visualizer.component.ts
      bulk-upload.component.ts
    decision-log-explorer/
      decision-log-explorer.component.ts
      decision-log-explorer.component.html
    fallback-config/
      fallback-config.component.ts
  services/
    assignment-studio.service.ts     ← API client
    grid-adapter.service.ts          ← re-exported from assignment-studio/

database/oracle/
  V7__assignment_engine_schema.sql   ← all ASGN_* tables, sequences, indexes
  V8__assignment_engine_seed.sql     ← attribute registry + Appendix A ruleset

docs/adr/
  ADR-001-decision-table-not-rules-engine.md
  ADR-002-hit-policy-and-null-semantics.md
  ADR-003-grid-library-choice.md
```

---

## 5. Database Tables Summary

All tables prefixed `ASGN_`. IDs from sequences (`ASGN_*_SEQ`). `TENANT_ID` on every table.

| Table | Purpose | Key Columns |
|-------|---------|-------------|
| `ASGN_ATTRIBUTE` | Routing dimension registry | code, label, dataType, sourcePath, valueSource, caseSensitive, piiFlag |
| `ASGN_ATTRIBUTE_VALUE` | Static allowed values for ENUM/LIST attributes | attributeCode, valueCode, valueLabel, sortOrder, active |
| `ASGN_RULE_SET` | One per decision point | tenantId, decisionPoint, name, hitPolicy, active |
| `ASGN_RULE_SET_VERSION` | Immutable versioned snapshot | ruleSetId, versionNo, status, effectiveFrom/To, maker/checker/publisher |
| `ASGN_RULE` | One row per decision table row | versionId, ruleCode, name, priority, rowOrder, enabled |
| `ASGN_RULE_CONDITION` | One cell in the decision table | ruleId, attributeCode, operator, valueText, valueNumFrom/To, valueList |
| `ASGN_RULE_OUTCOME` | Assignment target | ruleId (or null for default), outcomeType, targetId, assignMode, distributionStrategy, chainOrder |
| `ASGN_RULE_SET_DEFAULT` | Mandatory fallback per version | versionId + outcome columns |
| `ASGN_EXCLUSION_RULE` | Conflict-of-interest rules | type, condition, active |
| `ASGN_RULE_SET_PUBLICATION` | Cache watch table | tenantId, decisionPoint, activeVersionId, publishedAt |
| `ASGN_RR_COUNTER` | Round-robin state (atomic) | groupId, strategyKey, lastIndex, version |
| `ASGN_DECISION_LOG` | Partitioned audit of every resolve | caseRef, matchedRuleId, outcome, latencyMs, contextJson (PII masked) |
| `ASGN_AUDIT_EVENT` | Append-only governance audit | entityType, entityId, action, actor, beforeJson, afterJson |
| `ASGN_SIM_JOB` | Bulk simulation job tracking | status, contextCount, completedAt |
| `ASGN_SIM_RESULT` | Simulation output rows | jobId, contextIndex, matchedRuleId, outcome |

---

## 6. API Endpoints

Base: `/api/v1/assignment`

### Hot Path (consumed by other services)
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/resolve` | Resolve one case → assignee |
| POST | `/resolve/batch` | Resolve up to 500 cases |

### Authoring (Assignment Studio UI)
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/attributes` | Registry for grid columns |
| GET | `/attributes/{code}/values` | Typeahead for lookup attributes |
| GET | `/rulesets` | List rulesets |
| GET | `/rulesets/{id}/versions` | Version history |
| GET | `/rulesets/{id}/versions/{v}` | Full grid payload |
| POST | `/rulesets/{id}/versions` | Create draft (clone) |
| PUT | `/rulesets/{id}/versions/{v}/rules` | Bulk grid save (ETag) |
| PATCH | `/rulesets/{id}/versions/{v}/rules/{ruleId}` | Single rule update |
| POST | `/rulesets/{id}/versions/{v}/rules/reorder` | Reorder rows |
| DELETE | `/rulesets/{id}/versions/{v}/rules/{ruleId}` | Soft delete rule |
| PUT | `/rulesets/{id}/versions/{v}/default` | Set default outcome |
| POST | `/rulesets/{id}/versions/{v}/validate` | Full static analysis |
| POST | `/rulesets/{id}/versions/{v}/submit` | Submit for approval |
| POST | `/rulesets/{id}/versions/{v}/approve` | Approve |
| POST | `/rulesets/{id}/versions/{v}/reject` | Reject with remarks |
| POST | `/rulesets/{id}/versions/{v}/publish` | Publish (supports effectiveFrom) |
| GET | `/rulesets/{id}/versions/{a}/diff/{b}` | Version diff |
| POST | `/rulesets/{id}/versions/{v}/import` | XLSX import (dry-run) |
| GET | `/rulesets/{id}/versions/{v}/export` | XLSX export |

### Simulation
| Method | Path | Purpose |
|--------|------|---------|
| POST | `/simulate/single` | Single-case with trace |
| POST | `/simulate/bulk` | Async bulk job |
| GET | `/simulate/jobs/{jobId}` | Job status + download |

### Observability
| Method | Path | Purpose |
|--------|------|---------|
| GET | `/decision-logs` | Paged, filterable log explorer |
| GET | `/audit-events` | Governance audit trail |
| POST | `/internal/rules/cache/refresh` | Force cache reload |

---

## 7. Phase Plan

### Phase 0 — Scaffolding & ADRs
**Scope:**
- Restructure `cms-assignment-service` packages (remove Drools deps)
- Add dependencies: Caffeine, Apache POI, Bean Validation, springdoc
- Add AG Grid Community + SheetJS to `cms-portal-frontend`
- Create Angular route shell for `/admin/assignment-studio`
- Write ADR-001, ADR-002, ADR-003
- Verify: `mvn compile` + `ng build` pass

**Exit criteria:** Both projects compile cleanly on a fresh build.

---

### Phase 1 — Domain, Persistence, Engine Core
**Scope:**
- All JPA entities + enums
- Oracle DDL migration script (`V7__assignment_engine_schema.sql`)
- Seed data script (`V8__assignment_engine_seed.sql`) with §6 attributes + Appendix A ruleset
- Attribute registry repository + service
- Operator catalogue (all operators from §7)
- `RuleSetCompiler` → `CompiledRuleSet`
- `RuleEvaluator` with FIRST + PRIORITY_SPECIFICITY hit policies
- Caffeine cache + publication table watcher
- `POST /resolve` endpoint (USER/GROUP outcome, explanation)
- Golden-file test suite (~60 contexts)
- Engine module ≥90% line coverage

**Exit criteria:** Golden-file CSV tests pass end-to-end. Resolve returns correct outcomes for all Appendix A scenarios.

---

### Phase 2 — Authoring APIs + The Grid
**Scope:**
- Backend: full CRUD APIs, bulk save with optimistic lock, XLSX import/export
- Frontend: AG Grid rule editor with metadata-driven editors, paste-from-Excel, fill-down, undo/redo, row operations, inline validation, column manager, compact renderers
- Autosave draft every 60s, unsaved-changes guard
- 5,000-row virtualised scrolling test

**Exit criteria:** A user can build the §5 example table from scratch in the browser, save, and `POST /resolve` returns the expected outcome without backend restart. 5,000 rows scroll smoothly.

---

### Phase 3 — Governance
**Scope:**
- Version state machine (DRAFT → ... → PUBLISHED → SUPERSEDED)
- Maker–checker enforcement
- Immutability of PUBLISHED versions (guard + test)
- Effective dating + overlap blocking at publish
- Scheduled publish (effectiveFrom in future)
- Cache refresh: 30s poller + immediate endpoint
- Diff view + approval inbox in the UI
- Audit events for all state transitions

**Exit criteria:** Integration test proves published version cannot be mutated, maker ≠ checker is enforced, publish on node A is visible to node B within poll interval.

---

### Phase 4 — Validation, Simulation, Explainability
**Scope:**
- Full static analysis (all 14 checks from §11)
- Single-case trace with per-rule pass/fail
- Bulk what-if (async, diff vs published, dead-rule detection)
- Decision Log + Explorer (paged, filterable, XLSX export)
- Metrics (Micrometer): latency histogram, default-hit rate, cache hit rate
- Actuator health indicator
- Frontend: simulator panel, bulk upload, trace visualiser, log explorer

**Exit criteria:** Overlap/unreachable/gap findings correct on a deliberately broken fixture. Bulk simulation of 10,000 contexts completes with downloadable diff. Simulation does NOT advance round-robin counters.

---

### Phase 5 — Distribution, Exclusions, Hardening
**Scope:**
- All 6 distribution strategies + atomic RR counter
- Availability/OOO/delegation filters
- Conflict-of-interest exclusion ruleset
- CHAIN outcome with fallback cascade
- Batch resolve endpoint (500 cap)
- `assignment-client` module (RestClient + timeout + circuit breaker)
- 50-thread round-robin fairness test
- p99 ≤ 25ms load test (5,000 rules, 20 attributes)
- Security: @PreAuthorize, input caps, PII masking, tenant filter
- Docs: README, business user guide, XLSX template

**Exit criteria:** 50-thread RR fairness passes. p99 target met and recorded. Security review pass.

---

## 8. Dependencies (New)

### Backend (pom.xml changes)

**Remove:**
- `org.drools:drools-core`
- `org.drools:drools-compiler`
- `org.drools:drools-mvel`
- `org.kie:kie-api`
- `org.kie:kie-internal`

**Add:**
- `com.github.ben-manes.caffeine:caffeine`
- `org.apache.poi:poi-ooxml:5.2.5`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui:${springdoc.version}`
- `io.micrometer:micrometer-registry-prometheus`
- `net.jqwik:jqwik:1.8.x` (test scope)

### Frontend (package.json additions)
- `ag-grid-community: ^32.x`
- `xlsx: ^0.18.x` (SheetJS, client-side export only)

---

## 9. Roles & Security

| Role | Permissions |
|------|-------------|
| `RULE_VIEWER` | Read rulesets, versions, decision logs |
| `RULE_MAKER` | Create/edit drafts, submit for approval, run simulations |
| `RULE_CHECKER` | Approve/reject submitted versions (cannot be the maker) |
| `RULE_ADMIN` | Manage attribute registry, system fallback, exclusion rules |
| `ASSIGNMENT_CONSUMER` | Call `/resolve` only (service-to-service) |

Map to existing Keycloak roles: `ADMIN` → `RULE_ADMIN`, `INCHARGE`/`RBIO_SUPERVISOR` → `RULE_CHECKER`, `DO`/`RBIO_OFFICER` → `RULE_MAKER`.

---

## 10. Non-Goals (Documented Future Work)

- Workflow/BPM orchestration
- SLA timers and escalation scheduling
- Notification delivery
- Case reassignment/rebalancing jobs
- ML-based routing
- General expression language
- Cross-attribute comparisons
- Graphical rule-tree builder
- Full multi-tenant Hibernate filter (ready to add, not wired)

---

## 11. Risk & Mitigation

| Risk | Mitigation |
|------|-----------|
| AG Grid Community lacks range selection / fill handle | Implement fill-down via custom keyboard handler over cell-selection API. If clipboard parsing proves impossible on Community, stop and report before switching |
| 5,000-rule performance | Pruning index (BitSet intersection) for indexed attributes; benchmark with JMH in Phase 5 |
| Oracle-specific SQL | All DDL uses Oracle syntax; no Flyway multi-dialect needed since Oracle is the only target |
| Drools removal breaks existing Kafka listener | The `ComplaintIngestedAssignmentListener` will be refactored to call the new `AssignmentResolveService` instead of the old `AssignmentService` |
