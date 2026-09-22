# CMS 2.0 — Intelligence & Analytics Enhancement Plan

> RB-IOS 2026 Compliance + Analytics Engine. Phased build for demo in ~9 days (target: 2026-07-10).
> Presented for Operator approval before implementation begins.

---

## Acknowledgement — Mocks vs. Site Theme

**The HTML spec mocks (`report-builder-mock.html`, `citizen-eligibility-wizard-mock.html`) are INTERACTION references only.** I will NOT copy or replicate their CSS, color palette, typography, or visual style. All new components will follow the existing CMS site theme:
- Global: `styles.scss` (font-family: Segoe UI/system, background: #fafafa, focus: #2460b9)
- PrimeIcons for icons
- Signal-based Angular 20 standalone components with `chart.js` for charts (already in use)
- WCAG 2.1 AA focus-visible, sr-only, reduced-motion patterns already established

The mocks guide behavior, states, and interaction flow only.

---

## Codebase Audit Summary (Phase 0)

### Existing Architecture

| Layer | Key Files | Notes |
|-------|-----------|-------|
| **Complaint Entity** | `entity/Complaint.java` | Has `bankComplaintReference`, `bankComplaintDate`, `status`, `priority`, `department`, `workflowStage`, `filedAt/resolvedAt/closedAt` |
| **Regulated Entity** | `entity/RegulatedEntity.java` | `name`, `entityType`, `department` (CEPC/RBIO), `state` |
| **Categories** | `entity/ComplaintCategory.java` | Hierarchical (parentId) |
| **Timeline** | `entity/ComplaintTimeline.java` | Action log per complaint |
| **Routing** | `service/ComplaintRoutingService.java` | CRPC→DEO/Reviewer→RBIO/CEPC, round-robin assignment |
| **TAT** | `service/TatCalculationService.java` + `BusinessHoursService.java` | Business-hours-aware SLA calculation, holiday-aware |
| **Similar Cases** | `service/OpenSearchSimilarCasesProvider.java` | OpenSearch MLT queries, provider pattern |
| **Dashboard** | `service/DashboardService.java` | Basic count-based, Hazelcast-cached |
| **Events** | `event/ComplaintEventPublisher.java` | Kafka topic `complaint.ingested` |
| **Cache** | `config/HazelcastCacheConfig.java` | Maps: dashboard(120s), categories(1h), translations(30m) |
| **i18n** | `service/TranslationService.java` | DB-driven, Hazelcast-cached, 10 languages |
| **Auth** | Keycloak SSO (staff), OTP (citizen) | Roles: DEO, REVIEWER, CRPC_HEAD, RBIO_OFFICER, CEPC_OFFICER |

### What Already Exists (reusable)
- `bankComplaintReference` and `bankComplaintDate` fields on Complaint ✓ (Phase 1 partially done)
- OpenSearch similarity infra ✓ (Phase 4 precedent retrieval)
- TAT/Business-hours calculation ✓ (Phase 5 timeline)
- Hazelcast distributed cache ✓ (MRE rule cache)
- Kafka event pipeline ✓ (future event-driven triggers)
- Chart.js in admin dashboard ✓ (Phase 7 charts)
- ComplaintRoutingService with entity→department resolution ✓

### What's Missing (to build)
- No `priorReComplaint` (yes/no) field — only reference/date exist
- No maintainability status/determination on Complaint
- No rules engine for RB-IOS grounds
- No report-builder semantic model / query compiler
- No pre-aggregation tables / materialized views
- No scheduled report/email infrastructure
- No Apache POI dependency (Excel generation)
- No eligibility wizard component

---

## Shared Architectural Spine: Maintainability Rules Engine (MRE)

```
┌─────────────────────────────────────────────────────────┐
│                 Maintainability Rules Engine             │
│  Deterministic • Versioned • Cached • Advisory-only     │
├─────────────────────────────────────────────────────────┤
│  Input: ComplaintFacts (structured)                      │
│  Output: MreVerdict (per-ground pass/fail/needs-review, │
│          clause references, overall signal)              │
├─────────────────────────────────────────────────────────┤
│  Consumed by:                                           │
│    Phase 3: Citizen eligibility wizard (self-check)     │
│    Phase 4: Officer co-pilot (objective grounds)        │
│    Phase 5: Intake triage (auto-flag at registration)   │
└─────────────────────────────────────────────────────────┘
```

**Objective grounds encoded:**
1. Entity not covered by the Scheme (Q13)
2. No prior complaint to the RE (Q16)
3. Filed before 30-day RE window elapsed (unless replied-and-dissatisfied) (Q17)
4. Filed beyond 90 days of timeline expiry / last RE communication (Q16/Q17)
5. Complaint to RE made after Limitation Act 1963 period (Q16)
6. Same grievance already pending/decided by Ombudsman or court (Q16)

**Not decided by MRE** (subjective, for Phase 4 precedent only):
- No deficiency in service / mere suggestion
- Commercial judgment, against management/executives
- Vendor/inter-RE/employer-employee disputes
- Abusive/frivolous/vexatious

---

## Phase Plan

### Phase 1 — New Complaint Fields: Prior-RE-Complaint Details
**Effort:** ~0.5 day | **Risk:** Low

**Changes:**
- Add to `Complaint.java`: `priorReComplaint` (Boolean), `reComplaintDate` (LocalDate), `reComplaintReference` (String), `reRepliedAndDissatisfied` (Boolean)
- Add index on `reComplaintDate` (used by MRE and analytics)
- Add to `FileComplaintRequest.java`: matching fields
- Server-side validation: date not future; if priorReComplaint=true → date + reference required
- i18n: add translation keys for all new labels/hints/validation messages (10 languages)
- Frontend: add fields to public file-complaint form

**Migration:** JPA `ddl-auto: update` will handle in dev; for prod, document ALTER TABLE.

---

### Phase 2 — Maintainability Rules Engine (MRE)
**Effort:** ~1.5 days | **Risk:** Medium (rule correctness critical)

**New files:**
- `service/mre/MaintainabilityRulesEngine.java` — core deterministic evaluator
- `service/mre/MreRuleConfig.java` — configurable windows/thresholds
- `service/mre/ComplaintFacts.java` — input DTO
- `service/mre/MreVerdict.java` — output DTO (per-ground verdicts + clause refs)
- `service/mre/MreGround.java` — enum of objective grounds
- `config/MreProperties.java` — `@ConfigurationProperties(prefix = "cms.mre")`

**application.yml additions:**
```yaml
cms:
  mre:
    version: 1
    re-window-days: ${MRE_RE_WINDOW:30}
    npci-window-days: ${MRE_NPCI_WINDOW:30}
    card-network-window-days: ${MRE_CARD_WINDOW:60}
    filing-deadline-days: ${MRE_FILING_DEADLINE:90}
    limitation-period-years: ${MRE_LIMITATION_YEARS:3}
    window-basis: ${MRE_WINDOW_BASIS:CALENDAR}  # CALENDAR or BUSINESS — Decision to confirm
```

**Hazelcast cache:** new map `mre-rules` (TTL 1h, invalidated on version bump)

**Contract:** MRE is advisory. It never decides. It informs. Logged and enforced.

**Tests:** Exhaustive unit tests for every ground — boundary dates, replied-and-dissatisfied override, entity-not-covered, etc.

---

### Phase 3 — Citizen Eligibility Wizard (DEFERRED — not in this iteration)
> The spec mentions a companion `citizen-eligibility-wizard-mock.html` which was not provided in this session. This phase is noted but will be implemented when that spec is available.

---

### Phase 4 — Officer Maintainability Co-Pilot
**Effort:** ~1.5 days | **Risk:** Medium

**Approach:** Rules + Precedent (no ML classifier for now — Decision confirmed)

**Backend:**
- `service/copilot/MaintainabilityCopilotService.java`
  - Calls MRE for objective grounds verdict
  - Calls `SimilarCasesService` for k most similar closed cases (reuses OpenSearch)
  - Assembles draft rationale template
- `controller/CopilotController.java` — `GET /api/v1/copilot/maintainability/{complaintId}`
- Response DTO: `{ mreVerdict, precedentCases[], draftRationale, suggestedDetermination }`
- Log suggestion vs final decision for quality review

**Frontend:**
- Co-pilot panel in the officer complaint-action view (`staff/rbio/task/:id`, `staff/cepc/task/:id`)
- Shows: MRE verdict with clause references, similar precedent cases with outcomes, editable draft rationale
- Officer must confirm/override — never auto-rejects

---

### Phase 5 — Intake Eligibility Triage & Timeline + Stretch Features
**Effort:** ~2.5 days | **Risk:** Medium | **DEMO CENTREPIECE**

**Backend:**
- On complaint registration (`ComplaintService.fileComplaint()`), run MRE and attach:
  - `eligibilityTimeline` JSON (RE-complaint-date → window-opens → file-by-deadline → current position)
  - `triageFlags` (objective grounds triggered)
  - `triageSignal` (GREEN/AMBER/RED)
- New fields on Complaint: `triageSignal` (String), `triageFlags` (JSON/TEXT), `eligibilityTimeline` (JSON/TEXT)
- `service/triage/IntakeTriageService.java` — orchestrates MRE + timeline computation

**Frontend:**
- Triage banner on case detail (all officer views) — green/amber with timeline visual
- Worklist filter: "Objectively questionable maintainability" (triageSignal = AMBER/RED)
- Timeline visual: horizontal bar showing RE-complaint-date → window → filing-date position

**Stretch Feature A: Compensation-Precedent Band** (CONFIRMED — build now)
- For a case being decided, show award band from similar past awarded cases
- "Comparable cases were awarded ₹X–₹Y" with caps: ₹30 lakh (consequential) + ₹3 lakh (time/harassment)
- Reuses Phase 4 similarity infra (OpenSearch)
- New field on Complaint: `awardAmount` (BigDecimal) for historical data
- `service/triage/CompensationPrecedentService.java`

**Stretch Feature B: RE-Responsiveness Radar** (CONFIRMED — build now)
- Track each RE's response time against its forwarding window
- Auto-flag breaches; offer ex-parte pathway when RE misses window
- New entity: `ReResponseTracker` (complaintId, reId, forwardedAt, respondedAt, breached)
- Feed into Phase 7 senior dashboard
- `service/triage/ReResponsivenessService.java`

---

### Phase 6 — Natural-Language Report & Widget Builder
**Effort:** ~3 days | **Risk:** High (largest build) | **Demo: vertical slice**

**Architecture:**
```
┌──────────────────────────────────────────────────────────────┐
│  Frontend: Token Composer Component                           │
│  (guided autocomplete from semantic model registry)          │
├──────────────────────────────────────────────────────────────┤
│  Backend API: /api/v1/reports                                │
│  ├── GET /semantic-model — returns allowed dimensions/measures│
│  ├── POST /compile — validates + returns compiled preview    │
│  ├── POST /execute — runs bounded, read-only query           │
│  ├── POST /save-widget — persists query def + chart type     │
│  ├── POST /schedule — creates scheduled email delivery       │
│  └── GET /my-widgets — user's saved widgets                  │
├──────────────────────────────────────────────────────────────┤
│  Service Layer                                               │
│  ├── SemanticModelRegistry — whitelisted dimensions/measures │
│  ├── QueryCompiler — IR → JPA Criteria (parameterized)       │
│  ├── QueryExecutor — bounded, read-only, auth-scoped         │
│  ├── ReportSchedulerService — off-hours batch (10PM–2AM)     │
│  └── ExcelExportService — Apache POI workbook generation     │
├──────────────────────────────────────────────────────────────┤
│  Safety Layer                                                │
│  ├── Authorization scope injected server-side (row filter)   │
│  ├── Max 5000 rows, 30s statement timeout                    │
│  ├── Read-only transaction                                   │
│  ├── Only whitelisted fields/operators/values                │
│  └── Parameterized — no string concatenation                 │
└──────────────────────────────────────────────────────────────┘
```

**Semantic Model (derived from complaint aggregate):**
- **Subjects:** list complaints, count, avg TAT, median TAT
- **Dimensions/Filters:** RE (from Bank/RegulatedEntity), RE category (entityType), region (state), status, category, maintainability, filing type, date ranges
- **Group-bys:** RE, region, month, status, category, deficiency type
- **Measures:** COUNT(*), AVG(tat_days), MEDIAN(tat_days)

**New dependencies (pom.xml):**
- `org.apache.poi:poi-ooxml:5.2.5` — Excel generation
- `org.springframework.boot:spring-boot-starter-mail` — email delivery

**Scheduled reports:**
- Fixed off-hours slots: 10PM, 11PM, 12AM, 1AM, 2AM (configurable)
- Spring `@Scheduled` cron jobs
- Per-report timeout + row cap; isolated execution; audit log
- Re-checks user authorization scope at send time

**Demo vertical slice:** 5-6 dimensions, token composer, grid results, one chart type (bar) saved as widget, one scheduled slot producing Excel.

---

### Phase 7 — Senior TAT / Pipeline / Bottleneck Dashboard
**Effort:** ~2 days | **Risk:** Medium (performance-critical)

**Approach:** Pre-aggregated summary tables + Hazelcast cache

**Pre-aggregation:**
- Summary table: `COMPLAINT_DAILY_SUMMARY` (date, re_id, category_id, region, status, count, avg_tat, median_tat, p90_tat)
- Refreshed by scheduled job (off-hours, same 10PM–2AM window)
- Dashboard reads ONLY from summary + cache, never live transactional tables during business hours

**Metrics (all from pre-aggregated data):**
- Inflow by RE / RE-category / region / category / time
- TAT distribution: average, median, p90 by RE, department, stage
- Pipeline funnel: registered → maintainability → forwarded → examined → settled/award/rejected → closed
- Aging buckets + SLA breach cases
- Maintainable vs non-maintainable ratios
- Trends over time

**Charts:** Bar/stacked, line/trend, funnel, heatmap — using Chart.js (already in stack)

**Filters:** Date range, RE, RE-category, region, department, officer, category, status

**Framing:** Process-first bottleneck view (which stage/queue, aging by category). Individual officer drill-down gated as "cases needing attention" — not a ranking. (Decision to confirm with Operator)

---

### Phase 8 — Cross-Cutting Hardening & Adversarial Verification
**Effort:** ~1 day | **Risk:** Low (testing phase)

- Full regression of existing journeys
- Performance re-check vs Phase 0 baseline
- Report builder adversarial: injection, unauthorized field access, row/time bounds, write attempts
- MRE boundary-date scenarios
- Scheduler stress: many reports on one slot, oversized results, duplicate definitions
- Accessibility sweep (keyboard, screen-reader, axe/pa11y)
- i18n coverage check for all new citizen-facing strings

---

## Dependencies & Sequencing

```
Phase 1 (fields) ──┐
                    ├── Phase 2 (MRE) ──┬── Phase 4 (co-pilot)
                    │                   ├── Phase 5 (triage) ← DEMO CENTREPIECE
                    │                   └── Phase 3 (wizard, deferred)
                    │
                    └── Phase 6 (report builder) ── Phase 7 (senior dashboard)
                                                          │
                                                     Phase 8 (hardening)
```

Phases 1→2→5→4 form the critical path for the demo.
Phase 6→7 can be parallelized with 4/5 after Phase 2 is done.

---

## Demo Priority (9-day constraint)

| Priority | Phase | What to show | Time |
|----------|-------|--------------|------|
| 1 | 1+2 | Fields + MRE keystone | Day 1-2 |
| 2 | 5 | Intake triage + timeline banner ("the wow") | Day 3-4 |
| 3 | 4 | Co-pilot (MRE + precedent + draft rationale) | Day 4-5 |
| 4 | 6 | Report builder vertical slice | Day 5-7 |
| 5 | 7 | Senior dashboard (few charts from pre-aggregated data) | Day 7-8 |
| 6 | 8 | Hardening | Day 8-9 |

**Narrative for the room:** One rule set from the new circular, applied from the citizen's first click through to the officer's closure, plus analytics the seniors can drive themselves — with no hit to performance.

---

## Decisions — CONFIRMED by Operator

| # | Decision | Confirmed Choice | Config to flip |
|---|----------|-----------------|----------------|
| 1 | MRE window basis | **Business days** (not calendar) | `cms.mre.window-basis=BUSINESS` / `CALENDAR` in application.yml |
| 2 | Phase 4 classifier | **Rules + precedent only** — no AI/ML | N/A |
| 3 | Report builder input | **Token composer only** — no NLP. Seed a rich, user-friendly token dictionary | N/A |
| 4 | Read replica | **Add MySQL read replica config** + maintain DB scripts for Oracle portability | `spring.datasource.readonly.*` |
| 5 | Phase 5 stretch: compensation band + RE-responsiveness | **INCLUDE in demo** — build now, not deferred | N/A |
| 6 | Phase 7 bottleneck framing | **Process-first** with gated individual drill-down | N/A |
| 7 | Similarity backend | **OpenSearch** — improve with right fields where applicable | N/A |
| 8 | Citizen wizard (Phase 3) | **Deferred** — no need to build | N/A |

**Cross-cutting directives (Operator):**
- Performance for **10,000 concurrent users** (staff + complainants) is a hard constraint
- All features must have **unit tests AND adversarial test scenarios**
- Maintain **DB migration scripts** for all schema changes (MySQL today, Oracle portability)
- MRE window basis must be **configurable** to flip between BUSINESS/CALENDAR days

---

## Risk Flags

| Risk | Mitigation |
|------|-----------|
| MRE rules may not exactly match RB-IOS 2026 Scheme text | Flagged as requiring legal/compliance sign-off; rules are configurable and versioned |
| Report builder query could be expensive | Bounded (5000 rows, 30s timeout), read-only, off-hours for scheduled, pre-aggregation for dashboard |
| OpenSearch may not be running during demo | Graceful fallback in SimilarCasesService; co-pilot degrades to MRE-only |
| 9-day timeline is tight for all 6 phases | Strict priority ordering; Phase 7 can be a few charts, Phase 6 a vertical slice |
| MySQL `ddl-auto: update` may struggle with JSON columns | Use `TEXT` with Jackson serialization, not MySQL JSON type |

---

## New Dependencies Required

| Dependency | Purpose | Phase |
|-----------|---------|-------|
| `org.apache.poi:poi-ooxml:5.2.5` | Excel workbook generation for scheduled reports | 6 |
| `org.springframework.boot:spring-boot-starter-mail` | Email delivery for scheduled reports | 6 |
| `jakarta.persistence-api` Criteria API | Already included via spring-boot-starter-data-jpa | 6 |

---

## File Organization (new packages)

```
com.hrms.cms
├── service/mre/          ← Phase 2: MaintainabilityRulesEngine
├── service/copilot/      ← Phase 4: MaintainabilityCopilotService
├── service/triage/       ← Phase 5: IntakeTriageService
├── service/report/       ← Phase 6: SemanticModel, QueryCompiler, QueryExecutor
├── service/analytics/    ← Phase 7: AggregationService, DashboardAnalyticsService
├── controller/           ← New endpoints: CopilotController, TriageController,
│                            ReportBuilderController, AnalyticsDashboardController
├── entity/               ← New: ReportDefinition, ReportSchedule, DailySummary
├── dto/                  ← New: MreVerdict, CopilotResponse, ReportRequest, etc.
└── config/               ← New: MreProperties, ReportSchedulerConfig
```

Frontend:
```
src/app/components/
├── report-builder/       ← Phase 6: token composer, results grid, widget save
├── analytics-dashboard/  ← Phase 7: senior TAT/pipeline dashboard
└── shared/
    ├── triage-banner/    ← Phase 5: reusable triage signal component
    └── copilot-panel/    ← Phase 4: reusable co-pilot sidebar
```

---

## Ready for Approval

Please confirm:
1. Overall approach and phasing is acceptable
2. The 8 decisions above (or provide your preferences)
3. Whether to proceed with Phase 1 implementation

I will not begin implementation until this plan is approved.
