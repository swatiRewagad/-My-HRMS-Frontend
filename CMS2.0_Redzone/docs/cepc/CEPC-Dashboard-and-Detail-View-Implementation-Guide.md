# CEPC Dashboard & Complaint Detail View — Implementation Guide

**Audience:** engineers on other department modules (RBIO, CRPC, AA, RE) who need to bring a
dashboard + case-detail screen onto `cms-backend` and off the standalone search service.

**What this is:** the process we actually followed for CEPC, in the order we followed it, with the
mistakes called out. CEPC is the worked example — the phases transfer to any department module.

**Scope of the CEPC work:** move every dashboard and detail-view API into `cms-backend`, query MySQL
directly (**no OpenSearch anywhere in the path**), seed real `dev-local` data, and make all seven
dashboard tabs plus all six detail-view tabs return real rows with their role gates and
enable/disable rules working.

---

## 1. Read this before you start

### 1.1 Do not port the old `cms-search-service` verbatim

`CMS2.0/cms-search-service` looks like the obvious thing to copy. It is substantially broken, and
copying it reproduces every defect:

| Defect | Effect |
|---|---|
| `@JsonNaming(SnakeCaseStrategy)` on the request DTO | silently discards the camelCase body the Angular app sends, including `statusCode` |
| `"Complaint Assigned To Me"` never matches its switch case | the default view returns zero rows by construction |
| `X-Current-Officer` read but sent by no client | all user scoping is inert |
| `sort` not whitelisted | 500s and an injection surface |
| `createdDate` sent but `createdAt` indexed | sorting silently ignored |
| naked `LocalDate.parse` | 500 on any malformed date |
| `IOException` swallowed | returns 200 with an empty page, so failures look like "no data" |
| response keys misspelled (`pendingAtMeetingSchedule`) | UI reads `undefined` |

**Mirror `service/RbioComplaintListService.java` instead.** It is ~480 lines in the Redzone tree and
already does this correctly — the `SORTABLE` whitelist, the `record ListQuery(...)`, the
unconditional department `AND`, `parseDate`, and the fail-closed `applyStatusFilter`. Everything
below assumes you are copying that shape.

### 1.2 Know which enforcement actually works

`@PreAuthorize` is **inert in this codebase** — `@EnableMethodSecurity` is absent (see the comment in
`cms-backend/.../config/SecurityConfig.java`). Annotating a method with `@PreAuthorize` gives you no
protection and a false sense of it.

What works is the AOP guard pattern, one per department:
`security/CepcRoleGuard.java` + `security/CepcRoleGuardAspect.java` (siblings exist for RBIO, AA, RE).
Copy that pair for your module.

### 1.3 Fail closed, always

An unknown filter code must resolve to `cb.disjunction()` (zero rows), never "no filter" (all rows).
Returning everything for a code you do not recognise is a data-exposure bug, not lenient behaviour.
The same rule applies to an unparseable date and an unknown sort field.

---

## 2. Environment prerequisites

| Item | Value |
|---|---|
| Backend | `cms-backend`, port **8082**, profile `dev-local`, no context path |
| Database | MySQL 8 in Docker (`cms-mysql`), schema `cms_db` |
| Frontend | `cms-portal-frontend`, `ng serve` (4200/4300), `environment.apiBaseUrl = http://localhost:8082` |
| Keycloak | Docker `cms-keycloak`, **port 8180**, realm `cms` |
| Schema management | **hand-run SQL** in `database/` — there is no Flyway/Liquibase |

**Dev identity headers.** On `dev-local`, `cms.security.allow-dev-identity-headers=true`, so
`CmsPrincipalResolver` honours:

```
X-User-Id: cepc.officer1
X-User-Roles: CEPC_DO
```

The header is **`X-User-Roles`**, not `X-User-Authorities`. Getting this wrong authenticates you as
nobody and every response looks like an authorization bug.

**MySQL access.** The `mysql` CLI is not on PATH; go through Docker:

```bash
docker exec cms-mysql mysql -ucms_user -p<pass> cms_db -e "SELECT 1;"
# heredoc / file input needs -i:
docker exec -i cms-mysql mysql -ucms_user -p<pass> cms_db < database/V103__....sql
```

Run one `-e` statement per invocation; several in one call can fail.

---

## 3. Phase 0 — Unblockers (do these first)

These are pre-existing bugs that make parts of the dashboard *structurally* un-populatable. Fix them
before building anything, or you will spend days debugging a correct query returning zero rows.

1. **Conditional `@PrePersist`.** `entity/Complaint.java` `onCreate()` unconditionally overwrote
   `createdAt/updatedAt/filedAt/lastStatusChangeDate` with `now()`. Every backdated seed date was dead
   code, so SLA buckets and date ordering could not be seeded. Guard each assignment
   (`if (this.createdAt == null)`).
2. **Make `SEND_BACK_*` write one status.** Only `SEND_BACK_DO` wrote `status="sent_back"`; the
   reviewer and incharge variants wrote their own stage names, so the "Sent Back to Me" tab could
   never fill. Write `sent_back` in all three, and make the tab predicate
   `status='sent_back' OR workflowStage LIKE 'SENT_BACK%'` to cover rows already in the database.
3. **Add the missing `FORWARD_TO_RE` status arm.** It existed only in the notification switch, never
   in the status switch, so `assignedRole="RE"` was written nowhere and three tabs ("Pending with RE",
   "Sent to RE", "Response from RE") could never be non-zero.
4. **Add a thin identity resolver** (`security/CepcIdentityResolver.java`) wrapping
   `CmsPrincipalResolver.resolve()`, so services get the current `preferred_username` + roles from one
   place.

> **Lesson for your module:** before you write a query, confirm a row can actually reach the state
> your tab filters on. Grep for the status string in the workflow service and check something writes
> it.

---

## 4. Phase 1 — Dashboard search on `cms-backend`

### 4.1 A filter vocabulary table, not hardcoded strings

We added `CEPC_DASHBOARD_FILTER` (entity `CepcDashboardFilter`, seeder `CepcDashboardFilterSeeder`)
with a row per dashboard filter: `dimension` (`STATUS|TAB|KPI`), `filterCode`, `predicateKind`,
`predicateValues`, `windowFromDays/windowToDays`, `pendingOnly`, `visibleToRoles`, `displayOrder`.

**Do not add your codes to `RBIO_STATUS_MASTER`.** `RbioStatusMasterSeeder.seedVisibility()` does
`findAll()` then grants `ADMIN` every code it finds, so foreign codes leak into the RBIO admin filter
bar. Separate table, separate vocabulary.

The seven CEPC tab codes (the `filter_code` values are the wire contract):

| Tab | `filter_code` | `predicate_kind` |
|---|---|---|
| All | `All` | `NONE` |
| Draft | `Draft` | `STAFF_DRAFT` |
| Meeting Scheduled | `Meeting Scheduled` | `STAGE_IN` |
| Sent Back to Me | `Sent Back to Me` | `SENT_BACK_TO_ME` |
| Sent to RE | `Sent to RE` | `RE_PENDING` |
| Response from RE | `Response from RE` | `RE_ACTIVITY_IN` |
| Withdrawn Complaints | `Withdrawn Complaints` | `STATUS_IN` |

Two traps we hit:

- **"Draft" is `STAFF_DRAFT`**, not the citizen wizard's `COMPLAINT_DRAFTS`, and not
  `status='draft'` (the workflow controller overwrites that to `assigned`). It is a **separate code
  path**, not a `Specification<Complaint>`, because it queries a different table.
- **`SCHEDULE_MEETING` writes only `workflowStage`, never `status`.** So never filter the Meeting
  Scheduled tab on `status`.

### 4.2 The search service

`service/CepcComplaintSearchService.java`, mirroring the RBIO template:

- `record ListQuery(...)` for the parsed request
- a `SORTABLE` whitelist; unknown field → `createdAt DESC`, **never a 500**
- accept **both** `createdDate` and `createdAt` (the UI sends the former)
- `DEFAULT_PAGE_SIZE=20`, `MAX_PAGE_SIZE=200`, `MIN_PARTIAL_TERM=2`
- always `AND department='CEPC'`
- `parseFlexibleDate` (`dd-MM-yyyy` then `yyyy-MM-dd`); unparseable → zero rows, not a 500
- phone/email advanced search stays **exact-match** — a deliberate PII-enumeration guard, keep it
- manual `sort` parsing, never `@PageableDefault`

`Complaint` has **no JPA relationships**, so every cross-table predicate is a Criteria subquery.

### 4.3 Counts in two round-trips

`service/CepcDashboardCountService.java` computes all 13 counts in **two** queries: one JPQL
constructor expression of `COALESCE(SUM(CASE WHEN … THEN 1 ELSE 0 END),0)` aggregates, plus one
`STAFF_DRAFT` count.

**Counts run on the base scope only.** That is what makes the badges stay still when the user clicks
a tab or a KPI card — clicking filters the grid, not the badges. This is an explicit product
behaviour and it is also the easiest thing to break.

### 4.4 Enrichment without N+1

`service/CepcComplaintRowEnricher.java` does batch lookups only: `bankId`→`Bank`,
`categoryId`→`ComplaintCategory`, `complaintNumber`→`NodalOfficerRecord` (needs a
`findByComplaintNumberIn`), plus read state. Never per-row queries.

### 4.5 Per-user read/unread

`ComplaintReadState` entity + repository, unique on `(complaintId, userId)`, and an idempotent
`POST /api/v1/complaints/{id}/read`. The `unread` toggle is a correlated `NOT EXISTS`.

This replaced a `localStorage` implementation. The acceptance test is: mark a row read, **clear
localStorage**, hard-refresh — it must still be read, and a *different* user must still see it
unread.

### 4.6 The response envelope

```jsonc
{
  "success": true,
  "data": {
    "complaints": { "content": [ /* rows */ ], "totalElements": 56 },
    "kpiCounts":  { /* … */ },
    "tabCounts":  { "draft": 2, "meetingScheduled": 2, "sentBackToMe": 1,
                    "sentToRe": 3, "responseFromRe": 1, "withdrawnComplaints": 1 }
  }
}
```

Note `content` is nested under `data.complaints`, **not** at the top level.

Two per-row fields are different kinds of value and are easy to swap:

- `statusColor` is a **CSS class** — `status-progress`, `status-forwarded`
- `slaColor` is a **bare colour word** — `red`, `green`

`slaColor` is interpolated into `color-mix(in srgb, …)` in the table template. Put a class name there
and the cell renders **transparent** — a silent visual failure with no console error.

The KPI request key is **`kpi_cards`**, and KPI ids are human-readable strings, not enum codes:
`"Total Pending Complaints"`, `"Pending with Me"`, `"Pending with RE"`,
`"Pending at Meeting Scheduled"`, `"SLA Breached"`.

---

## 5. Phase 2 — The six detail-view tabs

The tab set is hard-coded buttons driven by an `assessmentTab` signal, not a config array.

| # | Tab | Condition that must work |
|---|---|---|
| 1 | Summary | always enabled |
| 2 | Conciliation | hidden for `HEAD`; enabled only for a `CEPC_DO` on a **non-terminal** complaint |
| 3 | Forward | hidden for `HEAD`; enabled only for `CLOSING_AUTHORITY` / `INCHARGE` |
| 4 | Final Decision | hidden for `HEAD` |
| 5 | Contact Entity | hidden for `HEAD`; **disabled** for `REVIEWER` / `INCHARGE`; has a nested detail view |
| 6 | Email Communication | **disabled** for `REVIEWER` / `INCHARGE` |

"With working conditions" means the gates are part of the deliverable, not decoration. Verify them.

### 5.1 The summary contract gates the whole screen

`GET/PUT /api/complaints/cepc/{id}/summary` is the bootstrap call. While it 404'd, `applySummary`
never ran, `isReadOnlyViewer` was forced `true`, and **every tab rendered empty regardless of its own
endpoint's health**. Build this first; until it works you cannot tell which other tabs are broken.

**Put the ~25 assessment fields in a side table, not on `COMPLAINTS`.** `Complaint` carries
`@Version recordVersion`, so writing assessment edits to the parent row makes the officer's Save
collide (409) with concurrent workflow transitions. We created `CEPC_COMPLAINT_ASSESSMENT`, one
nullable-column row per complaint (entity `CepcComplaintAssessment`).

The 20 eligibility answers had no home for a *submitted* complaint (`EligibilityQuestionMaster` is
masters-only; `ComplaintDraft.eligibilityAnswersJson` is draft-scoped), so we added
`COMPLAINT_ELIGIBILITY_ANSWERS` — standalone, keyed on **`questionKey`**.

**Acceptance test for the split:** `GET` the summary, `PUT` the identical body back, `GET` again and
diff. Any field that reads back null means the side-table split is leaking. We verified all 9 blocks
round-trip clean.

### 5.2 The rest of the tabs

- **Conciliation** — new mutable entity `CepcConciliationMeeting` → `CEPC_CONCILIATION_MEETINGS`.
  We deliberately did **not** reuse `RbioMeeting`: it is event-sourced with `updatable=false` columns
  and `supersededAt/ById`, and that audit guarantee should not be traded away for an editable form.
  `GET` returns `{current, history[]}` — handle `current: null` with a non-empty history.
  The `PUT` mirrors the outcome onto `Complaint.conciliationOutcome`/`conciliationDate` so the
  dashboard's Meeting Scheduled tab stays consistent.
- **Contact Entity** — project a 39-field `NodalRecord` DTO joining `NodalOfficerRecord` +
  `EntityOfficeNodalOfficer` + `RegulatedEntity` + `Complaint`. We added **17** nullable columns to
  `NODAL_OFFICER_RECORDS`. Note `noMobile`/`noEmail`/`noDesignation` are **not** columns — they come
  from the `EntityOfficeNodalOfficer` join.
- **`forward-to-re`** — route the status change through the shared workflow action
  (`performAction(..., "FORWARD_TO_RE", ...)`), *not* a bespoke update. That shared path is what makes
  the detail-view action and the dashboard's "Sent to RE" predicate agree. Proving that link is the
  single most valuable test on this screen.
  The payload key is **`status`** (not `statusCode`); codes are `INFORMATION_REQUIRED`,
  `ADVISORY_ISSUED`, `AWARD_PASS`, `13_1_NOTICE`, and `COMMUNICATES_TO_ENTITY` deliberately
  **excludes** `AWARD_PASS`.
- **Email Communication** — backed by `SimulatedEmail`. The list DTO is 23 keys; pin the status
  vocabulary to `DRAFT/PENDING/SENT/FAILED` because the UI buckets on it, and return the control flags
  the UI reads (`canReply`, `editable`, `canRetry`, `lastError`, `assignedTo`, `attachments[]`).
  **`to`/`cc`/`bcc` are comma-separated Strings, not arrays** — the form sends `this.emailTo.trim()`.
  Sending arrays gets you `Cannot deserialize value of type java.lang.String from Array value`.
- **Comments** — one `ComplaintComment` entity serves both complaint-level and nodal-record-level
  comments, discriminated on `noRecordNumber` + `target` (NO/PNO). In MySQL the text column is
  **`text`** and it keys on **`complaint_number`**, not `complaint_id`.

### 5.3 Endpoints that return 200 with an unusable shape

These are worse than 404s: the tab renders, so it looks healthy, but every control is dead. Audit for
them explicitly — compare the JSON keys against what the component reads. We found four, including a
send-email endpoint that read `recipients[]` while the form sent `from/to/cc/bcc/subject/body/status`,
so every send landed with empty recipients.

---

## 6. Phase 3 — `dev-local` seed data

`config/CepcDevSeeder.java`, `@Profile("dev-local")`, ordered after the base seeders, guarded by an
`existsBy…` check so it is insert-if-absent and safe to re-run.

18 complaints, ids **139-156**, numbered `N202526BLR000001`-`000018` via `N202526BLR%06d`, spread
across the complainants **raju, bharagav, arpitha, javeed, sudhi, naresh**.

> Use **slash-free** complaint numbers. An earlier `CEPC/DEV/2025/0001` convention needs URL encoding
> in every path segment and makes every curl and every route brittle.

**The status/stage/date mix is engineered so every tab count and every KPI is non-zero.** That is the
acceptance test for "all tabs return real data" — a tab showing 0 is indistinguishable from a tab
that is broken.

Seed every detail tab too, or you cannot tell an empty tab from a broken one:

| Tab | Seeded |
|---|---|
| Summary | assessment row for all 18 + eligibility answers on a subset |
| Conciliation | meetings on several, **plus one history-only row** to exercise `{current: null, history: […]}` |
| Final Decision | closure fields on #11 / #12 / #17 |
| Contact Entity | a `NodalOfficerRecord` for each, with the new columns populated |
| Email Communication | `DRAFT`, `SENT` and `FAILED` rows — the `FAILED` row with a `lastError` is what makes Retry testable |
| Cross-cutting | comments, 3-5 timeline rows each, attachments on a subset (so `withoutAttachments` bites), read state for **one** officer only — which proves the state is per-user |

---

## 7. Phase 4 — Frontend wiring

1. Point the dashboard at `${environment.apiBaseUrl}/api/v1/search/complaints/search` and delete
   `searchBaseUrl` from all `environments/environment*.ts` (the dashboard was its only consumer).
2. Delete the `search-service` route from `cms-api-gateway/src/main/resources/application.yml` — it
   shadows `cms-backend-catchall`.
3. Remove the localStorage read-state code; call `/read` and take `isRead` from the response.
4. **Render all seven tabs.** `tabsConfig` in `cepc-dashboard-tabs.component.ts` and
   `translateTabIdToStatus` in `cepc-dashboard.component.ts` are **two halves of one contract**: the
   tab `value` maps to a `filter_code`. We hit this twice —
   - a refactor that tidied `tabsConfig` silently dropped four tabs;
   - dropping a `case` from `translateTabIdToStatus` makes that tab fall through to `All` **while
     still showing its own badge count**, which looks like a backend bug and is not.

   If you add a tab, change both files and verify the string matches `filter_code` exactly.

---

## 8. Phase 5 — SQL for non-dev environments

`ddl-auto: update` covers `dev-local` only. Production runs `validate`, so a missing table is a
**boot failure**, which is correct and is exactly why these files must exist.

- MySQL: `database/V103__cepc_dashboard_and_detail_view_schema.sql`
- Oracle: `database/oracle/V101__cepc_dashboard_and_detail_view_schema.sql`

The two directories' V-numbers are **not** in sync. Both files are idempotent and guarded so a re-run
is a no-op.

### 8.1 The identifier-case trap — read this, it cost us a day

**MySQL 8:** `information_schema.TABLES.TABLE_NAME` carries the **`utf8mb3_bin`** collation — binary,
**case-sensitive**. Hibernate folds `@Table` names to **lowercase**. So:

```sql
-- WRONG on MySQL 8: matches nothing, concludes the table is absent,
-- runs the CREATE, and produces a duplicate uppercase table.
SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_NAME = 'NODAL_OFFICER_RECORDS';

-- RIGHT:
SELECT COUNT(*) FROM information_schema.TABLES WHERE UPPER(TABLE_NAME) = 'NODAL_OFFICER_RECORDS';
```

Under MySQL 5.x the column was `utf8_general_ci` and the bare comparison *did* work, which is why
this is easy to get wrong. On our first run the bare form created **six duplicate uppercase tables**
and skipped the columns and indexes it was supposed to add.

**Oracle is the opposite.** Unquoted identifiers fold to **uppercase**, so
`USER_TABLES.TABLE_NAME = 'NODAL_OFFICER_RECORDS'` is correct there and must **not** be "fixed" to
match MySQL. Same code shape, opposite folding direction. Both file headers say so; do not copy a
guard from one file into the other.

### 8.2 Other schema notes

- Every new column must be **nullable** — `ddl-auto: update` runs against a shared dev database with
  existing rows.
- Narrative fields are `TEXT`/`CLOB`, not `VARCHAR(4000)`.
- `TEXT` and `BIT(1)` are not Oracle types. Entities pin `columnDefinition = "TEXT"` for MySQL's
  sake; the Oracle file must use `CLOB`, and a nullable Java `Boolean` must be `NUMBER(1)` — **not**
  `CHAR(1)` 'Y'/'N', or every boolean reads back as a string.
- Do **not** duplicate seeder rows in SQL. `CepcDashboardFilterSeeder` carries no `@Profile`, so it
  runs everywhere and inserts if-absent; a second source for the same vocabulary turns drift into a
  failed migration.

---

## 9. Phase 6 — How to prove it works

Verification is the deliverable, not an afterthought. A screen that renders is not a screen that
works.

### 9.1 Dashboard, over HTTP

```bash
curl -s -X POST 'http://localhost:8082/api/v1/search/complaints/search?page=0&size=10&sort=createdAt,desc' \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: cepc.officer1' -H 'X-User-Roles: CEPC_DO' \
  -d '{"tabs":"All","unread":false,"withoutAttachments":false}'
```

Then assert, in this order:

1. **All seven tabs** — loop `tabs` over every `filter_code`. Each returns non-zero rows, **and
   `tabCounts` prints identically all seven times** (filter independence).
2. **Every KPI card** — the filtered row count must **equal** the matching `kpiCounts` aggregate. A
   disagreement here is the most likely bug in the count service.
3. **Read state** — per-user and idempotent: mark read as user A, re-query (count drops), confirm
   user B is unchanged, POST again (count does not move).
4. **Hostile input, all 200 with 0 rows and no 500s** — `"filedAt":"not-a-date"`,
   `"complaintId":"abc"`, `"statusCode":"NO_SUCH_CODE"` (**0 rows, not all rows**),
   `sort=DROP TABLE,desc` → falls back to `createdAt DESC`, `size=99999` → capped at 200, `page=-1` →
   page 0.
5. `sort=createdDate,asc` and `sort=createdAt,asc` must order identically.

### 9.2 Detail view, over HTTP

Re-run the endpoint inventory and confirm **0 of the ~45 calls 404**. Then:

- Summary round-trips all blocks (§5.1).
- Conciliation in all three shapes, including `{current: null, history: […]}`.
- Emails cover all four statuses with correct control flags; the `FAILED` row's retry increments
  `retryCount`.
- **The linkage test:** forward a complaint via the detail view's `forward-to-re`, then confirm it
  appears in the dashboard's "Sent to RE" tab. That proves the detail action and the dashboard
  predicate share one code path.

### 9.3 The conditions, not just the data

Assert the 403s, not only the 200s. For CEPC there are **three** independent layers and all three
must be exercised:

1. `@CepcRoleGuard` on the controller method — wrong role → 403
2. `CepcWorkflowService.validateRoleAuthorization(callerRole, action)` — role cannot perform the
   action → 403
3. `CepcEditPolicy.canEdit` / `isTerminal` — right role, but the record is not editable → 403

We verified `forward-to-re` 403 for a reviewer (layer 1) *and* 403 for a DO on a non-editable record
(layer 3), plus conciliation 403 on a terminal complaint.

### 9.4 UI verification

`ng serve`, log in, and click through as each role. The dashboard lives at `/cepc` —
`/cepc/dashboard` redirects there, and `CepcHomeComponent` wraps the dashboard component.

An e2e suite exists at `cms-portal-frontend/e2e/cepc/`. Two things to know:

- Supply credentials via the documented env vars (`CEPC_DO_USER`/`CEPC_DO_PASS`,
  `CEPC_REVIEWER_USER`/`CEPC_REVIEWER_PASS`, …). The committed defaults in `e2e/utils/auth.ts` do not
  match every realm.
- `isKeycloakAvailable()` gates the staff suites, and it probes `KEYCLOAK_URL`. That default was
  pointing at a port nothing listens on, so **every staff suite silently reported "skipped but
  passing"** for a long time. If a suite skips, treat it as a failure and find out why.

Useful selectors: `.kpi-card-surface` / `.metric-col-value` (KPI), `p-tab` + `.tab-count-badge`
(tabs), `tr.clickable-row` with `.row-read` / `.row-unread` (grid), `.sla-capsule` (SLA),
`.page-tabs .page-tab` (detail-view tabs).

### 9.5 Do not skip the non-`dev-local` security check

`DevLocalSecurityConfig` is `@Profile("dev-local")` and does `anyRequest().permitAll()`. **Every test
above passes even if your real `SecurityConfig` is completely wrong.** The real config is
`@Profile("!dev-local")`.

Restart without `dev-local` and assert, with real Keycloak tokens: **200** for a departmental role,
**403** for a citizen, **401** unauthenticated.

Two gotchas when you do: the default `issuer-uri` in `application.yml` points at a different port than
the Keycloak container publishes, so set `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWK_URI` explicitly; and
check your matcher **ordering** — specific department routes must sit *above* any broader
`/api/v1/keycloak/**`-style rule, or the broad rule wins and your role 403s.

---

## 10. File inventory

What a department module ends up owning. CEPC's actual files, as a template:

**Entities / repositories** — `CepcComplaintAssessment`, `CepcConciliationMeeting`,
`CepcDashboardFilter`, `ComplaintComment`, `ComplaintEligibilityAnswer`, `ComplaintReadState`
(each with a matching `…Repository`).

**Services** — `CepcComplaintSearchService`, `CepcDashboardCountService`,
`CepcComplaintRowEnricher`, `CepcComplaintSummaryService`, `CepcConciliationService`,
`CepcNodalRecordService`, `CepcEditPolicy`, `CepcSlaService`, `CepcAuditService`,
`CepcWorkflowService`.

**Controllers** — `CepcComplaintSearchController`, `CepcComplaintSummaryController`,
`CepcConciliationController`, `CepcNodalRecordController`, `CepcSendForApprovalController`.

**DTOs** — `dto/cepc/`: `CepcSearchRequest`, `CepcComplaintRow`, `CepcDashboardResponse`,
`CepcCountSnapshot`, `CepcDraftRow`, `ComplaintEmailRequest`. Plain Jackson, **no `@JsonNaming`**.

**Security** — `security/CepcRoleGuard`, `security/CepcRoleGuardAspect`,
`security/CepcIdentityResolver`.

**Config** — `config/CepcDashboardFilterSeeder` (all profiles), `config/CepcDevSeeder` (`dev-local`).

**Migrations** — `database/V103__…sql`, `database/oracle/V101__…sql`.

---

## 11. Checklist for your module

- [ ] Read `RbioComplaintListService` first; do not copy `cms-search-service`
- [ ] Confirm something actually writes every status your tabs filter on (Phase 0)
- [ ] `@PrePersist` is conditional, so seed dates survive
- [ ] Own filter table; do not add codes to another department's master
- [ ] Unknown code / bad date / bad sort all fail **closed**
- [ ] Counts computed on the base scope so badges do not move
- [ ] Enrichment is batched — no N+1
- [ ] Assessment-style fields in a side table, not on a `@Version`-carrying parent
- [ ] Summary (or your bootstrap call) built and round-tripping before anything else
- [ ] Shared workflow action for state changes, so dashboard predicates agree
- [ ] Response keys diffed field-for-field against what the component reads
- [ ] `slaColor` a bare colour word; `statusColor` a CSS class
- [ ] Seed so every tab and KPI is non-zero, and every detail tab has content
- [ ] Both halves of the tab contract updated (`tabsConfig` **and** the id→code mapping)
- [ ] MySQL guards use `UPPER(TABLE_NAME)`; Oracle guards stay bare uppercase
- [ ] New columns nullable; migrations idempotent
- [ ] Role gates asserted as 403s, all layers
- [ ] Non-`dev-local` security check run with real tokens
- [ ] No e2e suite is skipping

---

## 12. Reference — landmines we hit

| Symptom | Cause |
|---|---|
| Every response looks like an auth failure on `dev-local` | header is `X-User-Roles`, not `X-User-Authorities` |
| Migration reports "table absent" for a table that exists; duplicate uppercase tables appear | MySQL 8 `information_schema.TABLES.TABLE_NAME` is `utf8mb3_bin` (case-sensitive) |
| SLA cell renders transparent | a CSS class was returned in `slaColor`, which is interpolated into `color-mix()` |
| `Cannot deserialize value of type java.lang.String from Array value` | `to`/`cc`/`bcc` are comma-separated Strings, not arrays |
| "A status code is required to forward this record" | the payload key is `status`, not `statusCode` |
| A tab shows a badge count but lists unrelated rows | its `case` is missing from the id→code mapping, so it falls through to `All` |
| Officer's Save 409s intermittently | assessment fields written to the `@Version`-carrying `Complaint` row |
| A read-only field blanks itself and the next Save persists the blank | the field was assigned from a lookup projection that does not supply it |
| Every detail tab empty although its own endpoint is fine | the summary bootstrap call is failing and forcing read-only mode |
| A test suite "passes" but asserts nothing | it skipped on an availability probe pointing at the wrong port |
| Dates read back shifted ~5h30m | JDBC `serverTimezone=UTC` while the JVM runs IST (pre-existing) |
| `re_activity_status` stays NULL after forwarding | **by design** — null is mapped to `NOT_OPENED`, so the transition is a no-op |
