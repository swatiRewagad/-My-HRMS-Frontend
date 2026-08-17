# Build Prompt — Configurable Case Assignment Rules Engine ("Assignment Studio")

> **How to use this document:** paste the whole thing into Claude Code as the initial task brief.
> Work through it **phase by phase** (Section 18). Do not skip ahead. After each phase, run the
> tests, summarise what was built, and wait for confirmation before starting the next phase.

---

## 1. Mission

Build a **metadata-driven case assignment engine** for a Complaint Management System (CMS), plus an
**Excel-like web console** that lets non-technical business users author, test, approve and publish
the assignment rules.

Today, assignment logic is (or would be) hard-coded as nested `if/else` in Java. That is
unmaintainable: every policy change needs a code change, a release, and a regression cycle. After
this build, a business user must be able to change *who gets which complaint* by editing a grid in
the browser and getting it approved — **with zero code deployment**.

The engine answers exactly one question, fast and explainably:

> Given the attributes of a complaint (amount, branch, regulated entity, category, state, channel,
> escalation level, …), **which user or which group should this task be assigned to, and why?**

---

## 2. Non-negotiable product principles

1. **No new dimension requires code.** Adding "product type" or "complainant category" as a routing
   dimension = inserting a row in an attribute registry table. It then appears as a new column in
   the grid and becomes usable in conditions. No recompile.
2. **Resolution never fails.** If no rule matches, the ruleset default fires. If the default's
   target is unavailable, the system fallback queue fires. The API returns 200 with an outcome in
   every case except malformed input.
3. **Every decision is explainable and auditable.** The response and the persisted decision log say
   which ruleset version, which rule row, and which distribution strategy produced the assignee.
4. **Rules are versioned and go through maker–checker.** A published version is immutable. Editing
   creates a new draft. Rollback is a forward-only republish.
5. **Business users must be able to simulate before they publish.** Single-case trace and bulk
   what-if against historical cases, showing what would change.
6. **The grid must feel like Excel.** Paste from a spreadsheet, fill down, export to `.xlsx`,
   keyboard navigation, inline validation.

---

## 3. Assumptions & tech stack (correct me only if the repo says otherwise)

| Area | Choice |
|---|---|
| Language / runtime | Java 17 (LTS), Maven multi-module |
| Backend framework | Spring Boot 3.2.x — Web, Validation, Data JPA, Cache, Actuator, Security |
| Persistence | Oracle 19c, Hibernate 6, Flyway for migrations (Oracle-compatible SQL only) |
| Caching | Caffeine (in-process). Optional Redis only if the repo already uses it |
| Frontend | Angular 17+ (standalone components, signals), TypeScript strict mode |
| Grid | AG Grid **Community** + a thin `GridAdapter` abstraction (see §15.2) |
| Excel I/O | Backend: Apache POI. Frontend: SheetJS (`xlsx`) for client-side export only |
| API style | REST/JSON, OpenAPI 3 spec generated via springdoc |
| Auth | Assume an existing JWT/OAuth2 resource server; read roles from token claims |
| Tests | JUnit 5, AssertJ, Mockito, Testcontainers (Oracle XE) for repo/integration tests; Jest + Playwright on the frontend |

**Do not introduce a rules engine dependency (Drools, BAMOE, Easy Rules, MVEL, SpEL-based eval, or
any LLM).** The evaluator is a small, deterministic, hand-written decision-table interpreter. It
must be readable by an auditor. If you feel tempted to add an expression language, stop and ask.

**Do not invent libraries or versions.** If you need a dependency not listed above, ask first.

---

## 4. Glossary

| Term | Meaning |
|---|---|
| **Decision Point** | A named place in the workflow that needs an assignee, e.g. `COMPLAINT_INTAKE`, `ESCALATION_L2`, `APPEAL_REVIEW`, `QA_SAMPLING`. Each has its own ruleset. |
| **Attribute** | A routing dimension registered in metadata, e.g. `claimAmount`, `branchCode`, `regulatedEntityId`, `complaintCategory`. |
| **Context** | The map of attribute values for one complaint, supplied by the caller at resolve time. |
| **Rule** | One row of the decision table: a set of conditions + an outcome + a priority. |
| **Condition** | One cell: `attribute operator value(s)`. An absent condition means **ANY** (wildcard). |
| **Outcome** | What to assign to: a user, a group, a role-within-an-org-unit, a queue, or an ordered fallback chain. |
| **RuleSet** | All rules for one decision point, for one tenant. |
| **RuleSetVersion** | An immutable, versioned, effective-dated snapshot of a ruleset. |
| **Hit policy** | How to behave when several rules match. |
| **Distribution strategy** | How to pick one user out of a group when the outcome is a group but a single user is required. |

---

## 5. Conceptual model — the decision table

Model the rules as a **DMN-style decision table**, not as a general expression language.

```
                 │ claimAmount │ regEntityType │ category      │ state       ║ → Outcome            │ Prio
─────────────────┼─────────────┼───────────────┼───────────────┼─────────────╫──────────────────────┼──────
 R-001 Hi-value  │ >= 500000   │ ANY           │ ANY           │ ANY         ║ GROUP: SENIOR_ADJ     │ 10
 R-002 Card/West │ [0, 500000) │ BANK          │ IN(CARD, ATM) │ IN(MH, GJ)  ║ GROUP: ORBIO_MUMBAI   │ 20
 R-003 NBFC      │ ANY         │ NBFC          │ ANY           │ ANY         ║ GROUP: NBFC_CELL      │ 30
 R-004 Named SME │ ANY         │ ANY           │ EQ(DIGITAL)   │ ANY         ║ USER: officer.rao     │ 40
─────────────────┴─────────────┴───────────────┴───────────────┴─────────────╨──────────────────────┴──────
 DEFAULT (ruleset-level, mandatory, not a row)                               ║ GROUP: GENERAL_POOL
```

**Semantics the engine must implement exactly:**

- A rule matches iff **all** of its conditions evaluate true against the context (AND across
  columns). Wildcard columns always match.
- Multi-value operators (`IN`, `CONTAINS_ANY`) are OR **within** a cell. This gives business users
  OR without needing a rule-builder tree.
- **Hit policies** (per ruleset, default `FIRST`):
  - `FIRST` — evaluate in ascending `priority`, then ascending `rowOrder`; return the first match.
  - `PRIORITY_SPECIFICITY` — among all matches, pick lowest `priority`; break ties by **specificity**
    (fewest wildcard columns), then `rowOrder`. Deterministic.
  - `UNIQUE` — exactly one rule may match; more than one is a runtime error → falls back to default
    and raises a `RULE_CONFLICT` alert. Validation blocks publishing if overlap is detectable.
  - `COLLECT` — return all matching outcomes (used for broadcast/multi-assign decision points).
- **Null / missing attribute semantics** — be explicit and test it:
  - Context value missing or null + condition present → condition is **false**, except for
    `IS_NULL`, `NOT_IN`, `IS_NOT_NULL` which handle it per their definition.
  - Wildcard (no condition) matches even when the attribute is missing.
  - `NOT_IN` on a null value evaluates **true** (a null is not in the set). Document this in the UI
    tooltip because it surprises people.
- **Range semantics** — `BETWEEN(lo, hi)` is **inclusive lower, exclusive upper: `[lo, hi)`**. This
  is deliberate so that consecutive money bands cannot leave a gap or overlap at the boundary. The
  grid must render it as `[lo, hi)` and the validator must use these semantics for gap analysis.
- **Numeric comparison** uses `BigDecimal.compareTo` (never `equals`, never `double`).
- **String comparison** is case-insensitive and trimmed by default; the attribute registry can flag
  an attribute as case-sensitive.
- Evaluation is **side-effect free and pure** apart from round-robin counters, which are applied
  only after a rule is chosen.

---

## 6. Attribute registry (this is what makes it code-free)

Table-driven definition of every dimension that can be used as a column.

Fields per attribute:

- `code` (e.g. `claimAmount`), `label`, `description`, `helpText`
- `dataType`: `STRING | NUMBER | MONEY | DATE | DATETIME | BOOLEAN | ENUM | STRING_LIST`
- `sourcePath` — JSON path into the resolve request context (`$.complaint.claimAmount`) so callers
  can send a nested payload
- `required` — must be present in context; if a required attribute is absent, log a warning and
  treat as null (never throw)
- `valueSource`: `FREE_TEXT | STATIC_LIST | LOOKUP_TABLE | LOOKUP_API`
  - `STATIC_LIST` → child table of allowed values (code, label, sortOrder, active)
  - `LOOKUP_API` → a registered, allow-listed internal endpoint returning `{code,label}` for the
    grid's typeahead (e.g. branch master, regulated entity master). Server-side allow-list only —
    never let the registry point at an arbitrary URL.
- `allowedOperators` — subset of §7, derived by default from `dataType`, overridable
- `caseSensitive`, `indexable` (used by the compiler, §9), `displayOrder`, `active`
- `piiFlag` — if true, values are masked in decision logs and exports

Seed the registry with (adjust names to the existing CMS domain if the repo already has them):
`claimAmount`, `complaintCategory`, `complaintSubCategory`, `regulatedEntityId`,
`regulatedEntityType`, `branchCode`, `state`, `district`, `zone`, `channel`, `language`,
`escalationLevel`, `complainantType`, `isSeniorCitizen`, `isRepeatComplaint`, `daysOpen`,
`sourceSystem`.

**Acceptance test for this section:** inserting one row into `RULE_ATTRIBUTE` (+ its value list)
and refreshing the browser makes a new, fully functional column available in the grid, with the
right editor and the right operator list, with no Java or TypeScript change.

---

## 7. Operator catalogue

Implement each as a small class behind `interface ConditionOperator { boolean test(Object ctxValue, ConditionValue cv); }`,
registered in a map keyed by operator code. Each operator declares which data types it supports.

| Data type | Operators |
|---|---|
| STRING | `EQ`, `NEQ`, `IN`, `NOT_IN`, `STARTS_WITH`, `ENDS_WITH`, `CONTAINS`, `IS_NULL`, `IS_NOT_NULL` |
| ENUM | `EQ`, `NEQ`, `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL` |
| NUMBER / MONEY | `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN` (`[lo,hi)`), `IN`, `NOT_IN`, `IS_NULL` |
| DATE / DATETIME | `EQ`, `BEFORE`, `ON_OR_BEFORE`, `AFTER`, `ON_OR_AFTER`, `BETWEEN`, `OLDER_THAN_DAYS`, `WITHIN_LAST_DAYS`, `IS_NULL` |
| BOOLEAN | `IS_TRUE`, `IS_FALSE`, `IS_NULL` |
| STRING_LIST | `CONTAINS_ANY`, `CONTAINS_ALL`, `NOT_CONTAINS`, `IS_EMPTY` |

Deliberately **excluded**: regex, arbitrary expressions, cross-attribute comparison
(`amount > threshold_of_other_field`). If a genuine need appears, it is a new *derived attribute*
computed by the caller or by a registered derivation, not an expression in the table. Say so in the
ADR.

---

## 8. Outcomes and target resolution

### 8.1 Outcome types

| Type | Payload | Meaning |
|---|---|---|
| `USER` | userId | Assign directly to a named user |
| `GROUP` | groupId + `assignMode` | Assign to a group |
| `ROLE_IN_ORG_UNIT` | roleCode + orgUnitCode (or `orgUnitFrom: <attributeCode>`) | e.g. "the Nodal Officer of the branch on the complaint" — resolved dynamically |
| `QUEUE` | queueCode | Park in a work queue for pull-based pickup |
| `CHAIN` | ordered list of the above | Try each until one resolves to an available target |

`assignMode` for `GROUP`: `AS_GROUP` (leave it to the group, pull model) or `PICK_MEMBER` (engine
selects one user now).

### 8.2 Distribution strategies (used when `PICK_MEMBER`)

`ROUND_ROBIN`, `LEAST_ACTIVE_CASES`, `CAPACITY_WEIGHTED`, `RANDOM`, `SKILL_MATCH`, `LANGUAGE_MATCH`.

- Round-robin counter must be **persisted and atomic** across nodes: `UPDATE RR_COUNTER SET
  LAST_INDEX = ... WHERE GROUP_ID = ? AND VERSION = ?` with optimistic retry, or a
  `SELECT ... FOR UPDATE` in a short transaction. Write a concurrency test with 50 threads proving
  fair distribution and no duplicate index.
- `LEAST_ACTIVE_CASES` / `CAPACITY_WEIGHTED` call an injectable `WorkloadProvider` port. Provide a
  default JPA implementation reading open-case counts, and make it easy to swap.

### 8.3 Availability & eligibility filters (applied to candidate users, in order)

1. User active and not locked
2. Not on leave / out-of-office for the current date (honour a `DELEGATION` record → substitute)
3. Below `maxConcurrentCases` if configured
4. **Conflict-of-interest exclusions** — a separate, small exclusion ruleset: never assign a case to
   a user who (a) is the complainant, (b) belongs to the org unit being complained against,
   (c) previously recused on this case, (d) is on the case's exclusion list. These are hard
   exclusions applied *after* rule matching and are themselves configurable rows.
5. Skill/language match if the strategy requires it

If filtering leaves zero candidates → fall through the `CHAIN` → ruleset default → system fallback
queue. Record every fallthrough reason in the decision log; emit a metric and (configurable) alert.

---

## 9. The evaluation engine

### 9.1 Compilation

On publish (and on cache miss), compile the persisted version into an immutable in-memory
`CompiledRuleSet`:

- Parse and type-coerce every condition value **once**, at compile time, not per request. A
  published version with an uncoercible value must fail validation, not fail at runtime.
- Pre-sort rules by `(priority, rowOrder)`.
- Build a pruning index: for attributes flagged `indexable` and used with `EQ`/`IN`, build
  `Map<attrCode, Map<value, BitSet ruleIds>>` plus a `BitSet` of rules that wildcard that attribute.
  Candidate set = intersection of (matching-value ∪ wildcard) bitsets across indexed attributes.
  Then linear-scan only the candidates for the remaining conditions.
- If a ruleset has fewer than 50 rules, skip indexing and scan linearly — measure, don't assume.

### 9.2 Caching & multi-node refresh

- `Caffeine` cache keyed `(tenantId, decisionPoint, asOfDate)`; value = `CompiledRuleSet`.
- A lightweight `RULE_SET_PUBLICATION` table holds `(tenantId, decisionPoint, activeVersionId,
  publishedAt)`. A scheduled poller (default 30s, configurable) compares the cached version id and
  swaps atomically. Also expose `POST /internal/rules/cache/refresh` for immediate invalidation and
  call it on publish from the publishing node.
- Cache swap must be atomic (build new object, then replace reference). Never mutate a compiled set.

### 9.3 Effective dating

Resolve requests may carry `asOf` (default `now`). Only versions where
`effectiveFrom <= asOf < effectiveTo` and `status = PUBLISHED` are eligible. Exactly one must be
eligible; overlapping effective windows must be blocked at publish time.

### 9.4 Performance targets

- p99 ≤ 25 ms server-side for a ruleset of 5,000 rules and 20 attributes, warm cache, excluding
  workload-provider calls.
- Zero DB reads on the hot path except the round-robin counter and workload lookups.
- Include a JMH benchmark module or a repeatable load test proving the number.

---

## 10. Governance: versioning and maker–checker

State machine for `RuleSetVersion`:

```
DRAFT ──submit──> PENDING_APPROVAL ──approve──> APPROVED ──publish──> PUBLISHED ──> SUPERSEDED
  ↑                     │                                                │
  └────── reject ───────┘                                       (immutable from here)
                                                    ARCHIVED (never published, discarded draft)
```

Rules:

- Only one `PUBLISHED` version per (tenant, decision point, effective window).
- `PUBLISHED` rows are **immutable** — enforce with a DB trigger or repository guard, and test it.
- "Edit published rules" = clone the published version into a new `DRAFT` with `version+1`.
- The **maker cannot be the checker** — reject with 403 and a clear message.
- Approval captures checker id, timestamp, and mandatory remarks. Rejection requires remarks.
- Publish supports **scheduled activation** (`effectiveFrom` in the future).
- **Rollback** = create a new draft cloned from an older version, auto-approve path is *not*
  allowed; it goes through the same maker–checker. (An emergency `EMERGENCY_PUBLISH` right may skip
  the checker but must raise a high-severity audit event and notify. Make this a config flag,
  default off.)
- Every state transition writes an `AUDIT_EVENT` row with before/after JSON snapshot, actor, IP,
  and correlation id. Audit is append-only.

---

## 11. Validation & static analysis (run on save, hard-gate on submit)

Return findings as `{severity: ERROR|WARNING|INFO, code, ruleIds[], attributeCode, message, suggestedFix}`.
`ERROR` blocks submission; `WARNING` requires an explicit acknowledgement checkbox with a reason.

Implement at minimum:

| Code | Severity | Check |
|---|---|---|
| `TYPE_MISMATCH` | ERROR | Value not coercible to the attribute's data type |
| `OPERATOR_NOT_ALLOWED` | ERROR | Operator not permitted for that data type/attribute |
| `UNKNOWN_ATTRIBUTE` | ERROR | Column refers to an inactive/deleted attribute |
| `INVALID_ENUM_VALUE` | ERROR | Value not in the attribute's allowed list |
| `TARGET_NOT_FOUND` | ERROR | Referenced user/group/queue/role doesn't exist or is inactive |
| `NO_DEFAULT` | ERROR | Ruleset has no enabled default outcome |
| `EMPTY_RULE` | ERROR | All columns wildcard (that's the default's job, not a row's) |
| `CHAIN_CYCLE` | ERROR | Fallback chain loops |
| `DUPLICATE_RULE` | ERROR | Two rules with identical condition sets |
| `OVERLAP` | WARNING (ERROR under `UNIQUE` hit policy) | Two rules whose condition sets intersect — compute per attribute: sets intersect, numeric intervals intersect, wildcard intersects everything |
| `UNREACHABLE` | WARNING | Rule fully shadowed by a higher-priority rule whose conditions are a superset-match |
| `RANGE_GAP` | WARNING | Numeric bands for an attribute, holding other columns equal, leave an uncovered interval |
| `PRIORITY_COLLISION` | INFO | Two rules share a priority under `FIRST` |
| `HIGH_FANOUT_DEFAULT` | INFO | Simulation shows >X% of traffic hitting the default |

Overlap detection can be O(n²) on rule count — cap at a configurable rule count (default 2,000) and
run it asynchronously with a progress indicator beyond that.

---

## 12. Simulation & what-if (build this properly, it's what earns business trust)

1. **Single-case trace** — POST a context (or pick a real case id), get back: outcome, matched rule,
   plus a per-rule trace showing which condition failed first for every non-matching rule. Render as
   a highlighted grid.
2. **Bulk what-if** — upload a CSV/XLSX of contexts (or select a date range of historical
   complaints), run against a chosen **draft** version, and produce:
   - distribution by outcome (counts + %) with a bar chart
   - % hitting the default
   - rules that never fired (dead rules)
   - **diff vs the currently published version**: "N of M cases change assignee", drill-down table
3. Bulk runs are asynchronous jobs with a status endpoint and downloadable XLSX report. Simulation
   must **never** mutate round-robin counters or write decision logs (use a `dryRun` flag threaded
   through the engine, and unit-test that it doesn't).

---

## 13. Observability, audit & explainability

- **Decision log** — one row per resolve call: correlation id, decision point, tenant, ruleset
  version, matched rule id + code, outcome type + target, distribution strategy, candidate count,
  exclusions applied, fallback reason (if any), latency ms, timestamp, caller service, and the
  context with PII-flagged attributes masked. Partition by month; provide a retention/purge job with
  a configurable retention (default 7 years — regulatory) and archival hook.
- A **Decision Log Explorer** screen: filter by date, decision point, rule, assignee, fallback-only.
  Export to XLSX.
- **Metrics** (Micrometer): resolve latency histogram, resolves by outcome type, default-hit rate,
  fallback rate, rule-conflict count, cache hit rate, compile duration, per-rule fire counts.
- **Structured logs** with correlation id propagated from the caller header (`X-Correlation-Id`).
- Actuator health indicator that goes DOWN if no published ruleset is loadable for a decision point.

---

## 14. API contract

Base path `/api/v1/assignment`. Generate OpenAPI; all DTOs are records with Bean Validation.

### 14.1 The hot path

```
POST /resolve
{
  "decisionPoint": "COMPLAINT_INTAKE",
  "tenantId": "RBI-CMS",
  "caseRef": "CMP-2026-0001234",
  "asOf": "2026-08-16T10:15:00Z",          // optional
  "traceLevel": "NONE" | "MATCHED" | "FULL", // default NONE
  "dryRun": false,
  "context": {                              // free-form; mapped via attribute sourcePath
    "complaint": { "claimAmount": 750000, "category": "CARD", "channel": "PORTAL" },
    "entity":    { "regulatedEntityId": "RE1042", "regulatedEntityType": "BANK" },
    "geo":       { "state": "MH", "branchCode": "BR0091" }
  }
}

200 →
{
  "outcome": {
    "type": "GROUP",
    "groupId": "ORBIO_MUMBAI",
    "assignedUserId": "u.rao",            // present only when PICK_MEMBER
    "assignMode": "PICK_MEMBER"
  },
  "explanation": {
    "matchedRuleId": 4021, "matchedRuleCode": "R-002", "matchedRuleName": "Card, West zone",
    "ruleSetVersion": 17, "hitPolicy": "FIRST",
    "distributionStrategy": "LEAST_ACTIVE_CASES",
    "candidatesConsidered": 12, "candidatesExcluded": 3,
    "exclusionReasons": ["ON_LEAVE:2", "AT_CAPACITY:1"],
    "fallbackApplied": false,
    "evaluatedAt": "2026-08-16T10:15:00.031Z",
    "latencyMs": 6
  },
  "trace": [ /* only when traceLevel != NONE */ ]
}
```

Also: `POST /resolve/batch` (list of contexts, capped at 500).

**Client resilience contract:** document that callers should treat this as a critical-path
dependency with a short timeout (500 ms) and their own last-resort fallback queue. Provide a
`assignment-client` module with a Feign/RestClient wrapper, timeout, retry (idempotent, max 2), and
circuit breaker.

### 14.2 Authoring & governance

```
GET    /attributes                                  # registry, for grid columns
GET    /attributes/{code}/values?q=                 # typeahead for LOOKUP_* attributes
GET    /rulesets?decisionPoint=&tenantId=
GET    /rulesets/{id}/versions
GET    /rulesets/{id}/versions/{v}                  # full grid payload
POST   /rulesets/{id}/versions                      # create draft (optionally clone from version)
PUT    /rulesets/{id}/versions/{v}/rules            # bulk save of the grid (ETag/optimistic lock)
PATCH  /rulesets/{id}/versions/{v}/rules/{ruleId}
POST   /rulesets/{id}/versions/{v}/rules/reorder
DELETE /rulesets/{id}/versions/{v}/rules/{ruleId}
PUT    /rulesets/{id}/versions/{v}/default
POST   /rulesets/{id}/versions/{v}/validate
POST   /rulesets/{id}/versions/{v}/submit
POST   /rulesets/{id}/versions/{v}/approve | /reject
POST   /rulesets/{id}/versions/{v}/publish          # supports effectiveFrom
GET    /rulesets/{id}/versions/{a}/diff/{b}
POST   /rulesets/{id}/versions/{v}/import           # xlsx/csv, returns dry-run diff first
GET    /rulesets/{id}/versions/{v}/export           # xlsx
POST   /simulate/single
POST   /simulate/bulk                               # async → jobId
GET    /simulate/jobs/{jobId}
GET    /decision-logs?...                           # paged, filterable, exportable
GET    /audit-events?...
```

Errors follow RFC 7807 `application/problem+json` with a stable `code` field.

---

## 15. Frontend — "Assignment Studio"

### 15.1 Screens

1. **Rulesets list** — decision point, tenant, active version, last published by/when, pending
   approvals badge, default-hit rate sparkline from metrics.
2. **Rule Editor (the grid)** — the centrepiece, spec below.
3. **Column manager** — pick which registered attributes appear as columns; reorder; hide unused.
   Selection is saved per ruleset version (a hidden column with conditions is an error — warn).
4. **Version history & diff** — side-by-side / unified diff with row-level added/removed/changed
   highlighting and cell-level change markers.
5. **Approval inbox** — checker view: read-only grid + validation findings + diff vs published +
   approve/reject with remarks. Must show the maker's submission note.
6. **Simulator** — single-case form (auto-generated from the attribute registry) with trace
   visualisation; bulk upload tab with the diff report.
7. **Decision Log Explorer** — filterable table, drill into one decision's full explanation, export.
8. **Fallback & defaults config** — ruleset default, system fallback queue, distribution strategy
   defaults, exclusion rules.

### 15.2 Grid requirements (Rule Editor)

Structure: one **row per rule**; one **column per selected attribute**; sticky right-hand columns
for Outcome, Priority, Effective From/To, Enabled, Description; sticky left column for row handle +
rule code.

Must-have interactions:

- **Cell editors driven by attribute metadata**: number/money → operator dropdown + value(s), with a
  dedicated dual-input editor for `BETWEEN` rendering `[lo, hi)`; enum → multi-select checklist with
  search; lookup → server-side typeahead with debounce; date → date picker + relative-days option;
  boolean → tri-state (True / False / Any); string list → chip input.
- **Wildcard** is the default empty state, rendered as a muted `Any`. Clearing a cell returns it to
  wildcard. Make this visually distinct from "empty string".
- **Compact cell rendering**: `≥ 5,00,000`, `[0 – 5,00,000)`, `in (CARD, ATM, UPI +2)` with full
  value on hover. Use Indian digit grouping for money.
- **Paste from Excel** — parse TSV from the clipboard across a selected range, coerce per column
  type, show a pre-apply preview of what parsed and what failed. This is the single most requested
  feature; do not ship the grid without it.
- **Fill down / fill series** on a selected range (Ctrl+D), copy (Ctrl+C), undo/redo stack (Ctrl+Z /
  Ctrl+Y) covering at least 50 operations.
- **Row operations**: insert above/below, duplicate, delete (soft, with undo), multi-select,
  drag-reorder with automatic priority renumbering (renumber in steps of 10 so manual insertion is
  easy), bulk enable/disable.
- **Inline validation** — red cell border + tooltip on the cell; a findings panel at the bottom
  listing all ERRORs/WARNINGs with click-to-navigate to the offending cell. Re-validate on blur
  (client-side, cheap checks) and on demand (server-side, full analysis).
- **Search & filter** across rules; "show only rules that match this test context" mode driven by
  the simulator.
- **Import/export** — export the current grid to `.xlsx` with a locked header, a hidden metadata
  sheet (ruleset id, version, attribute codes), and a `README` sheet explaining operator syntax.
  Import accepts the same format, always shows a dry-run diff, never applies silently.
- **Unsaved-changes guard** on navigation; autosave draft every 60s to the server as a draft version.
- **Optimistic locking**: if the version changed underneath, show a conflict dialog with a diff and
  the choice to reload or export-my-changes.
- **Read-only mode** for checkers and viewers, driven by role.
- Handle 2,000+ rows smoothly — virtualised rows and columns, no re-render storms. Test with a 5,000
  row fixture.

### 15.3 Grid library note

Use AG Grid **Community**. Range selection, fill handle, and the clipboard module are Enterprise
features — implement the clipboard/fill-down behaviour yourself over Community's cell-selection API,
and isolate all grid-vendor calls behind a `GridAdapter` service interface so the vendor can be
swapped (or upgraded to Enterprise if a licence exists) without touching feature code. Do not
silently pull in an Enterprise-licensed package. If you conclude Community genuinely cannot deliver
the paste behaviour, stop and report before choosing an alternative.

### 15.4 UX principles

- Business users, not developers. No JSON, no expression syntax, no regex, ever visible.
- Every operator has plain-language help (`Between 0 and 5,00,000 — includes 0, excludes 5,00,000`).
- Destructive actions are undoable or confirmed.
- Show the *effect* of a change, not just the change: after editing, a one-click "what changes?"
  against last month's cases.
- Full keyboard navigation; accessible (WCAG 2.1 AA), proper ARIA on the grid, focus management in
  dialogs.

---

## 16. Data model (Oracle 19c)

Create Flyway migrations. Use `NUMBER(19)` ids from sequences, `VARCHAR2(n CHAR)`, `TIMESTAMP(6)
WITH TIME ZONE` for instants, `CLOB` with `IS JSON` check constraints for JSON payloads. Prefix
tables per the repo's existing convention (assume `ASGN_` if none exists).

Tables:

- `ASGN_ATTRIBUTE` — the registry (§6)
- `ASGN_ATTRIBUTE_VALUE` — static allowed values
- `ASGN_RULE_SET` — id, tenant, decisionPoint, name, hitPolicy, description, active
- `ASGN_RULE_SET_VERSION` — id, ruleSetId, versionNo, status, effectiveFrom, effectiveTo,
  makerId/At/Remarks, checkerId/At/Remarks, publishedBy/At, compiledSnapshot CLOB, checksum
- `ASGN_RULE` — id, versionId, ruleCode, name, description, priority, rowOrder, enabled,
  effectiveFrom, effectiveTo
- `ASGN_RULE_CONDITION` — id, ruleId, attributeCode, operator, valueText, valueNumFrom, valueNumTo,
  valueDateFrom, valueDateTo, valueList CLOB *(store normalised so SQL-side analysis and reporting
  are possible — the compiled CLOB snapshot on the version is an optimisation, not the source of truth)*
- `ASGN_RULE_OUTCOME` — ruleId (or versionId for the default), outcomeType, targetType, targetId,
  assignMode, distributionStrategy, chainOrder
- `ASGN_RULE_SET_DEFAULT` — versionId + outcome columns (or reuse `ASGN_RULE_OUTCOME` with a null ruleId)
- `ASGN_EXCLUSION_RULE` — configurable conflict-of-interest exclusions
- `ASGN_RULE_SET_PUBLICATION` — tenant, decisionPoint, activeVersionId, publishedAt (cache watch table)
- `ASGN_RR_COUNTER` — groupId, strategyKey, lastIndex, version (optimistic lock)
- `ASGN_DECISION_LOG` — partitioned by month, indexed on (decisionPoint, createdAt), (caseRef), (matchedRuleId)
- `ASGN_AUDIT_EVENT` — append-only; entityType, entityId, action, actor, at, beforeJson, afterJson, correlationId
- `ASGN_SIM_JOB` / `ASGN_SIM_RESULT` — bulk simulation jobs

Indexes: `ASGN_RULE(versionId, priority, rowOrder)`, `ASGN_RULE_CONDITION(ruleId)`,
`ASGN_RULE_CONDITION(attributeCode)`, unique `(ruleSetId, versionNo)`, unique partial index ensuring
one PUBLISHED version per effective window (enforce in code + a function-based unique index).

---

## 17. Security, multi-tenancy, NFRs

- Every table carries `TENANT_ID`; every query filters on it. Add a Hibernate filter/interceptor so
  it cannot be forgotten. Cross-tenant access attempts are audited and rejected.
- Roles: `RULE_VIEWER`, `RULE_MAKER`, `RULE_CHECKER`, `RULE_ADMIN` (registry + system fallback),
  `ASSIGNMENT_CONSUMER` (resolve only, service-to-service).
- Method-level `@PreAuthorize` on every mutating endpoint; deny by default.
- Input hardening: cap rules per version (5,000), conditions per rule (50), `IN` list size (500),
  batch resolve size (500), import file size (5 MB). Reject beyond limits with clear messages.
- No user-supplied string ever reaches an expression evaluator, SQL, or an outbound URL. Lookup APIs
  come from a server-side allow-list only.
- PII masking in logs/exports for attributes flagged `piiFlag`.
- Availability: the resolve endpoint must survive the authoring DB being slow — it serves from cache
  and only touches the DB for counters/workload.
- Timezone: store instants in UTC, render in `Asia/Kolkata`, and make the display zone configurable.
- i18n-ready labels on the frontend (en + hi at minimum in the resource bundles, even if hi is a stub).

---

## 18. Build phases — do these in order

Each phase ends with: green tests, an updated `README`, and a short written summary. **Stop and
report at the end of each phase.**

**Phase 0 — Scaffolding & ADRs**
Maven multi-module skeleton (`-api`, `-domain`, `-engine`, `-persistence`, `-service`, `-web`,
`-client`, plus `frontend/`), Spring Boot app boots, Flyway wired, Angular app boots, CI-friendly
`mvn verify`. Write ADR-001 (why a decision table, not a rules engine or if/else), ADR-002 (hit
policy & null semantics), ADR-003 (grid library choice).
*Acceptance:* `mvn verify` and `ng build` both pass on a clean checkout.

**Phase 1 — Domain, persistence, engine core**
Attribute registry, ruleset/version/rule/condition/outcome model, Flyway migrations, repositories,
the operator catalogue, the compiler, and the `FIRST` + `PRIORITY_SPECIFICITY` hit policies. Default
outcome. `POST /resolve` returning USER/GROUP with explanation.
*Acceptance:* a golden-file test suite — a CSV of ~60 contexts mapped to expected outcomes,
including every operator, every null case, both `BETWEEN` boundaries, and the default path — passes
end to end. Engine module has ≥90% line coverage.

**Phase 2 — Authoring APIs + the grid**
CRUD APIs for drafts, bulk grid save with optimistic locking, the Angular Rule Editor with
metadata-driven editors, wildcard handling, row reorder, inline validation, paste-from-Excel,
xlsx import/export.
*Acceptance:* a business user can build the §5 example table from scratch in the UI, save it, and
`POST /resolve` returns the expected outcome without any backend restart. 5,000-row fixture scrolls
smoothly.

**Phase 3 — Governance**
Version state machine, maker–checker, immutability enforcement, effective dating, scheduled publish,
diff view, approval inbox, audit events, cache refresh across nodes.
*Acceptance:* an integration test proves a published version cannot be mutated, that maker≠checker
is enforced, and that a publish on node A is picked up by node B within the poll interval.

**Phase 4 — Validation, simulation, explainability**
Full static analysis (§11), single-case trace, bulk what-if with diff-vs-published, decision log +
explorer, metrics, health indicator.
*Acceptance:* overlap/unreachable/gap findings are correct on a deliberately broken fixture ruleset;
bulk simulation of 10,000 contexts completes and produces a downloadable diff report; simulation
provably does not advance round-robin counters.

**Phase 5 — Distribution, exclusions, hardening**
All distribution strategies with the atomic RR counter, availability/OOO/delegation filters,
conflict-of-interest exclusions, chains, batch resolve, the `assignment-client` module with timeout
+ circuit breaker, load test proving the §9.4 targets, security review pass, docs.
*Acceptance:* 50-thread round-robin fairness test passes; p99 target met and recorded in the README.

---

## 19. Testing requirements

- **Unit**: every operator including null, boundary, type-coercion and case-sensitivity cases.
- **Property-based** (jqwik or hand-rolled generators): for any random ruleset + context, `FIRST`
  returns the lowest-`(priority,rowOrder)` matching rule; a resolve *always* returns an outcome.
- **Golden-file**: CSV/XLSX fixtures of context → expected outcome, easy for a BA to extend. These
  double as regression tests when rules change.
- **Repository/integration**: Testcontainers Oracle XE, real Flyway migrations, immutability trigger.
- **Concurrency**: round-robin fairness; concurrent draft edits hitting the optimistic lock.
- **Contract**: OpenAPI schema snapshot test so the client module can't drift.
- **Frontend**: Jest unit tests for cell editors and the paste parser (with real Excel clipboard
  samples, including Indian-format numbers with commas and ₹ symbols); Playwright e2e for the
  author → validate → submit → approve → publish → resolve happy path.

---

## 20. Deliverables

- Working code per the phase plan, all tests green.
- Flyway migrations + a seed script with the §6 attributes and a realistic starter ruleset.
- OpenAPI spec (generated) + a Postman/`.http` collection.
- ADRs 001–003 (plus any you add) in `/docs/adr`.
- `README.md`: architecture diagram (Mermaid), local setup, how to add a new attribute (the
  code-free path), how to add a new decision point, how to add a new operator (the one path that
  *does* need code), runbook for the fallback/alerting behaviour.
- A one-page **business user guide** (Markdown + the xlsx template) explaining the grid, operators,
  wildcard, `[lo, hi)` ranges, and the approval flow.

---

## 21. Explicit non-goals for this build

Out of scope — note them in the README as future work, don't build them:
workflow/BPM orchestration, SLA timers and escalation scheduling, notification delivery,
case reassignment/rebalancing jobs, ML-based routing, a general expression language,
cross-attribute comparisons, a graphical rule-tree builder.

---

## 22. Working agreement with me (the requester)

- Follow the phase plan; **stop and summarise after each phase**.
- If a requirement here conflicts with something already in the repo, **the repo's existing
  convention wins** — flag the conflict and proceed with the repo convention.
- If something is genuinely ambiguous, ask **one batched set of questions** rather than guessing
  silently or asking one at a time.
- Never add a dependency, a framework, or an alternative grid library without asking.
- Prefer boring, readable, testable code over cleverness. An auditor should be able to read
  `RuleEvaluator` and understand it in ten minutes.
- Write the test first for anything in §5 (semantics) — those are the rules that must never
  silently change.

---

## Appendix A — Starter ruleset for `COMPLAINT_INTAKE` (seed data)

Hit policy `FIRST`. Priorities in steps of 10.

| Prio | Code | Name | claimAmount | regEntityType | complaintCategory | state | escalationLevel | Outcome |
|---|---|---|---|---|---|---|---|---|
| 10 | R-001 | Very high value | `>= 2500000` | Any | Any | Any | Any | GROUP `SENIOR_ADJUDICATION` (PICK_MEMBER, LEAST_ACTIVE_CASES) |
| 20 | R-002 | High value banks | `[500000, 2500000)` | `EQ BANK` | Any | Any | Any | GROUP `BANK_HIGH_VALUE` (AS_GROUP) |
| 30 | R-003 | Digital fraud fast-track | Any | Any | `IN (UPI, CARD_FRAUD, NET_BANKING)` | Any | Any | GROUP `DIGITAL_FRAUD_CELL` (PICK_MEMBER, SKILL_MATCH) |
| 40 | R-004 | NBFC cell | Any | `EQ NBFC` | Any | Any | Any | GROUP `NBFC_CELL` |
| 50 | R-005 | West zone general | `[0, 500000)` | Any | Any | `IN (MH, GJ, GA)` | Any | GROUP `ORBIO_MUMBAI` |
| 60 | R-006 | South zone general | `[0, 500000)` | Any | Any | `IN (KA, TN, KL, AP, TS)` | Any | GROUP `ORBIO_CHENNAI` |
| 70 | R-007 | Appeal to AA | Any | Any | Any | Any | `EQ APPEAL` | ROLE_IN_ORG_UNIT `APPELLATE_AUTHORITY` @ orgUnitFrom `zone` |
| 80 | R-008 | Senior citizen priority | Any | Any | Any | Any | Any *(isSeniorCitizen = True)* | GROUP `PRIORITY_DESK` |
| — | DEFAULT | — | — | — | — | — | — | QUEUE `GENERAL_INTAKE_POOL` |

Note for the seed: R-008 as written is shadowed by earlier rules — **leave it in deliberately** as a
fixture for the `UNREACHABLE` validation check, and add a test asserting the validator flags it.

## Appendix B — Worked resolve example

Context: `claimAmount = 750000`, `regEntityType = BANK`, `complaintCategory = CARD_FRAUD`,
`state = MH`, `escalationLevel = L1`.

- R-001: `750000 >= 2500000` → false.
- R-002: `750000 ∈ [500000, 2500000)` → true; `BANK` → true. **Match.** Hit policy `FIRST` stops here.
- Outcome: GROUP `BANK_HIGH_VALUE`, `AS_GROUP` → no member selection, no RR counter advanced.
- Explanation records ruleId R-002, version, and that R-003 (digital fraud) was never reached —
  which is exactly the kind of surprise the trace view and the overlap warning exist to surface to
  the business user before publishing.
