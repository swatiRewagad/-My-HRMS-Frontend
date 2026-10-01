# RB-IOS QA Session E — login, complaints grid, status-code filters, advance search, assignment routing

You are one of **three parallel Claude sessions** (D, E, F) converting a set of ~250 manual QA test
cases for the RB-IOS (RBIO / Ombudsman office) module into Playwright E2E specs in the CMS 2.0
repository, and **fixing the product where a case legitimately fails**.

Repo: `c:/Projects/My-HRMS-Frontend/CMS2.0_Redzone`
Frontend: `cms-portal-frontend/` (Angular). Backend: `cms-backend/` (Spring Boot, package
`com.hrms.cms`). A second small service lives in `cms-workflow-service/` (`com.rbi.cms.workflow`).

Sessions D and F are running at the same time against the same database and the same Keycloak.
**Read "Shared-state rules" before you touch anything.**

---

## Your scope

You own everything from the login page up to and including the complaints **grid**: authentication,
launch/browser/responsive behaviour, per-role status-code filters, advance search, RBIOS-vs-CEPC data
isolation, and the **assignment routing engine** (round-robin, entity/category mapping, fallback to
Ombudsman Admin, scheme-coverage routing). You do **not** own the workflow ladder (that is F) or user
administration (that is D).

### What the audit found (verify before trusting — it is a point-in-time reading)

**Advance search EXISTS and is already partly tested — read the existing spec before writing one.**
- Frontend: `src/app/components/rbio/rbio-home/rbio-home.component.ts` (`showAdvancedSearch`,
  `applyAdvancedSearch()`, `clearAdvancedSearch()`); template modal in `rbio-home.component.html`,
  plus an in-grid empty-state re-open. A twin exists in `staff/rbio-tasks` and a CRPC one in
  `crpc/deo-home`.
- Backend: `RbioComplaintListController.java` `GET /api/v1/rbio/complaints` declares **13**
  advance-search params: `complaintNumber, complainantName, complainantMobile, complainantEmail,
  statusCode, complaintId, fromEmailId, subject, modeOfReceipt, entityName, nodalOfficerName,
  categoryId, reportedFrom, reportedTo`.
- **All nine RBIO roles** see advance search — its `@RbioRoleGuard` admits `RBIO_OFFICER,
  RBIO_SUPERVISOR, RBIO_CONCILIATOR, RBIO_ADJUDICATOR, RBIO_ADMIN, RBIO_DEALING_OFFICIAL,
  RBIO_REVIEWER, RBIO_DEPUTY_OMBUDSMAN, RBIO_OMBUDSMAN`. There is no per-role hiding.
- Match semantics (`service/RbioComplaintListService.java`): **partial/substring** on
  `complainantName`, `entityName`, `subject`; **EXACT** on `complainantMobile`, `complainantEmail`,
  `fromEmailId` (deliberate anti-PII-enumeration), exact on `complaintNumber` (upper-cased) and
  numeric `complaintId`.
- **Two refusals your manual cases will hit:** a partial term shorter than `MIN_PARTIAL_TERM` is
  refused with **HTTP 400** + `messageKey: "rbio.search.error_term_too_short"`; and
  **`nodalOfficerName` is refused outright** with `"rbio.search.error_nodal_officer_unsupported"`
  because COMPLAINTS has no nodal-officer column. A manual case expecting NO-name search to *work*
  fails by design.
- "No results found" is returned as **data, not hardcoded UI**: the controller sets `searchApplied`
  and `emptyMessageKey` = `"rbio.search.no_results"` when criteria were supplied, vs
  `"rbio.grid.no_complaints"` for an empty queue. Fields staying populated is the explicit intent of
  that pair.
- Existing coverage: `e2e/rbio/s1-home-grid.spec.ts` has
  `describe('Advance search is matched server-side (UST439-442)')` with 11 tests, including the
  single-character refusal, the nodal-officer refusal, and empty-search-vs-empty-queue. **Extend it
  in a new file; do not duplicate it.**

**Status-code filters EXIST, are fully data-driven, and the manual counts are all WRONG.**
- Defined in a **boot-time seeder**, not a frontend constant and not raw SQL:
  `cms-backend/src/main/java/com/hrms/cms/config/RbioStatusMasterSeeder.java` (`@Order(20)`) seeds
  `RBIO_STATUS_MASTER` + `RBIO_STATUS_ROLE_VISIBILITY`. `database/V57__rbio_status_master.sql`
  creates them **empty** — the seeder is the source of truth because there is no Flyway.
  `database/V63__rbio_status_vocabulary_and_category_cleanup.sql` tops up 8 more statuses and their
  role visibility.
- Endpoint: `GET /api/v1/rbio/status-filters?role=` → `RbioComplaintListService.filtersFor(role)`,
  which skips any status whose `IS_ACTIVE != 'Y'` and also returns `sortableFields`.
- Frontend: `src/app/services/rbio-status-filter.service.ts` **fails closed with no compiled-in
  fallback** — by design: *"If the server cannot say which statuses a role may see, the honest answer
  is 'no status filter is available', not a guess."*
- **Measured actual counts vs the manual sheet's assertions:**

  | Role | Manual case says | Actual (seeder + V63) |
  |---|---|---|
  | Ombudsman Admin (`RBIO_ADMIN`) | 18 | **42** |
  | Dealing Official | 16 | **21** |
  | Reviewer | 9 | **22** |
  | Deputy Ombudsman | 11 | **22** |
  | Ombudsman | 19 | **24** |

  Every role shares a 9-code base list: `ALL, ASSIGNED_TO_ME, CREATED_BY_ME, NEW_COMPLAINT, ASSIGNED,
  IN_PROGRESS, INFO_REQUESTED, MEETING_SCHEDULED, REOPENED`. `RBIO_ADMIN` gets `grant(ADMIN,
  allCodes, "ALL")` — literally every row. Note `ALL`, `ASSIGNED_TO_ME`, `CREATED_BY_ME` are **caller
  predicates, not statuses**; subtracting those 3 still does not reconcile.
- **Do not hardcode counts in your specs.** `e2e/rbio/s1-home-grid.spec.ts` already got this right by
  asserting *properties*: every offered code exists in the master, different roles get different
  lists, each role has exactly one default landing filter, an unknown code returns no rows rather
  than everything. **Follow that pattern.** The counts are a question for me: bring me the
  role-by-role diff and ask whether the seeder or the manual sheet is authoritative.
- **`CREATED_BY_ME` matches nothing by design** — `RbioComplaintListService` adds
  `cb.disjunction()` because COMPLAINTS has no author column. Several manual cases list "Complaint
  Created by Me" as a working filter. That is a real gap; report it, do not paper over it.

**RBIOS-vs-CEPC isolation EXISTS and is already tested.**
- Enforced in the query itself — `RbioComplaintListService` adds an unconditional
  `cb.equal(root.get("department"), "RBIO")` with the comment that without it the endpoint would
  serve CEPC and AA complaints to an RBIO officer, *"a cross-module data leak, not merely a wrong
  list."*
- Caller identity comes from the **token**, never the query string; `assignedTo` is a *filter*, not
  the scope, so a caller cannot widen their view by sending someone else's id.
- Existing coverage: `e2e/rbio/s1-home-grid.spec.ts`
  `describe('Authorization is enforced server-side (UST440)')` — CEPC officer refused, roleless caller
  refused, list never returns a non-RBIO complaint, `ASSIGNED_TO_ME` scoped to the caller. Plus
  `e2e/cepc/dashboard-grid.spec.ts` on the other side.

**"RB-IOS 2026" IS NOT THE OPERATIVE SCHEME. This invalidates several of your manual cases.**
- `service/mre/MreEntityCoverageService.java` has
  `@Value("${cms.eligibility.scheme-version:RBIOS_2021}")`, with a comment that hardcoded
  `"RBIOS_2026"` literals *"nam[ed] a Scheme not in force; those were removed, and this must not
  reintroduce the same class of error."* Every RBIO status and transition row is seeded
  `schemeVersion = "RBIOS_2021"`.
- Coverage routing itself is good: entity's `department == "RBIO"` → RBIO; exists-but-outside-Scheme →
  CEPC; **unknown entity → NOT covered** (the old code wrongly defaulted unmatched entities to RBIO);
  and an entity name matching both a CEPC and an RBIO row is **AMBIGUOUS and refused, not guessed**
  (worked example: `HDFC Credila Financial Services Limited` vs `HDFC Bank`).
- **Stale `RBIOS_2026` defaults still linger in the frontend** —
  `admin/master-data/master-data.component.ts` (a category-form default!),
  `crpc/draft-assessment`, `crpc/auto-closure`, `services/crpc-workflow.service.ts`, and options in
  `admin/communication-templates` / `crpc/in-charge-dashboard`. `AutoClosureService` still branches on
  `"RBIOS_2026"` — dead code given the config default. **Cleaning these up is in your scope**, but
  `admin/master-data/*` is shared with **D** — coordinate through me before editing it.
- Write all routing assertions against **`RBIOS_2021`**. Manual cases phrased "covered by the RB-IOS
  2026 scheme" need re-baselining; flag them.

**Assignment routing: the engine is well built; the trace log is NOT PERSISTED.**
- `service/OfficeAssignmentStrategyService.java` has `fallbackToOmbudsmanAdmin(officeId, strategy,
  why)` returning `OUTCOME_ADMIN_FALLBACK`, or `OUTCOME_UNASSIGNED` when the office has no admin — it
  deliberately does **not** borrow another office's admin, *"an admin in a different office has no
  jurisdiction."* `findOmbudsmanAdmin` requires `isActive() && !isOnLeave() && officeId.equals(...)`.
- **Four distinct fallback triggers, each with its own reason string** — ideal test hooks:
  round-robin exhausted; no subject to map on; no active mapping for the category/entity at that
  office; mapped officer inactive or on leave (and then the complaint goes to the **admin, not a
  substitute officer**, because the mapping was a deliberate assignment).
- Strategy validation refuses unknown names rather than silently behaving as round-robin, and
  `setStrategy` requires a **non-blank reason**. Endpoint:
  `OfficeAssignmentStrategyController.java` `/api/v1/admin/office-assignment`, strategies
  `ROUND_ROBIN | ENTITY_MAPPING | CATEGORY_MAPPING`.
- **The trace log does not exist.** Grep for `OfficeAssignmentAudit|assignment-trace|
  assignment/trace|resolutionHistory` finds only that service and its unit test. Every fallback is an
  **SLF4J log line only** — no table, no entity, no endpoint. The manual cases asking for a
  retrievable trace showing timestamp, complaint ID, category checked, mapping rule triggered, and
  initial + final assigned officer are a **genuine build item**. The closest existing surface is
  `GET /api/v1/complaints/{complaintNumber}/assignment-history` (custody history for send-backs,
  and it hardcodes `isOnLeave=false`).

### A live bug in your area, worth fixing

`cms-workflow-service/.../service/RoundRobinAssignmentService.java` (~lines 38-44): when the
on-leave-filtered eligible set is empty it falls back to `findByRoleGroupAndActiveTrue(roleGroup)`,
which **drops the on-leave filter entirely**, so on-leave officers still receive work:

```java
if (eligible.isEmpty()) {
    log.warn("[ASSIGN] No eligible officers ... Falling back to all active.");
    eligible = officerPoolRepository.findByRoleGroupAndActiveTrue(roleGroup);
}
```

`cms-backend`'s `OfficeAssignmentStrategyService` correctly falls back to the **Ombudsman Admin**
instead. This is the UST450 defect — fixed in cms-backend, never applied to the workflow-service
copy. **`cms-workflow-service/` is yours;** session D owns the *other* on-leave bug
(`/assignable-users` filtering in `WorkflowController.java`) — do not touch that file.

Note on-leave status lives in **three disagreeing places**: `OfficerPool.IS_ON_LEAVE` on
`WF_OFFICER_POOL` (documented as authoritative), `AaOfficerPool.onLeave` (what cms-backend's
assigners actually read), and `RbioStaffProfile.onLeaveOn(LocalDate)` (date ranges, **no writer** —
D is building that writer). Auto-assignment filtering **is** implemented in
`DurableRoundRobinAssigner.unavailableInPool()` and `OfficeAssignmentStrategyService.isUnavailable()`
(`!row.isActive() || row.isOnLeave()`; the pool is an *exclusion list*, so a missing row means
available).

### Your case list (condensed from the manual sheet — ~85 cases)

**E1 — Launch and login** (collapse the many repeated "user is able to launch/login" rows)
1. Launch the app in a supported browser and log in with valid credentials for each of: RBIO DO,
   Reviewer, Deputy Ombudsman, Ombudsman, Ombudsman Admin.
2. Login page renders with zero UI distortion; username, password and login button visible and
   aligned.
3. Renders identically across browsers without layout breakage. *(Only `chromium` is configured in
   `playwright.config.ts` — if you add projects, that is a config change shared with D and F: ask me
   first. Otherwise report this case as chromium-only and say so.)*
4. Invalid / malformed URL → 404 or connection error, app does not launch.
5. `http://` is redirected to `https://`, or blocked if strict HSTS is active. *(Local dev is plain
   HTTP — assess honestly whether this is testable here and say so rather than faking it.)*
6. Responsive rendering: resize to the lowest allowed resolution / dual monitors; inputs scale without
   cutting off text or overlapping.
7. Submit is **disabled** until both username and password are entered (test each half separately).
8. Invalid username, invalid password, and both → authentication-failure message, user stays on the
   login page.
9. Case sensitivity enforced on username and on password (toggle one letter's case).
10. Whitespace-only username/password rejected with an error.
11. More than 50 characters in username or password rejected.
12. Password field is masked.
13. Successful login redirects to the **complaint dashboard**.
14. Direct URL access with no session (e.g. `/complaints/home` in a fresh window) is refused with
    "session expired or access denied" — must not render the screen.

**E2 — Grid scope and isolation**
15. An RBIO user sees **only** RBIOS complaints.
16. A CEPC user **cannot** see RBIOS complaints and sees only CEPC ones.
17. A brand-new role/user with zero complaints gets a clean empty state ("No records found" / "Zero
    complaints assigned") with no layout distortion.
18. Clicking a complaint navigates to the detail screen.

**E3 — Advance search**
19. Advance search is displayed on the complaints screen to the Ombudsman user and is clickable.
20. Searching by the available fields works as expected.
21. Invalid / non-matching data shows "no results found".
22. Search fields **remain populated** so the user can adjust and retry.

**E4 — Role-specific status-code filters** (one block per role: Ombudsman Admin, Dealing Official,
Reviewer, Deputy Ombudsman, Ombudsman)
23. The KPI/status dropdown shows that role's codes. **Assert properties, not the manual counts** —
    and report the diff.
24. Selecting a specific status code dynamically refreshes the grid, and rows strictly match the
    filter.
25. A role must **not** be able to view another role's codes or access those records
    ("unauthorized/denied access").
26. A status code with no matching records renders cleanly with "No complaints found in this status."
27. **Real-time status shift:** a complaint meeting a new status condition appears under the new code
    and **not** the previous one after a manual refresh.

**E5 — "Assigned to Me" scoping**
28. A DO logs in and sees the "Assigned to me" filter; it lists only their own records.
29. Five newly assigned complaints appear for that DO in real time and are notified.
30. DEO/DO user 2 **cannot** see complaints assigned to user 1.
31. A DEO must not see status codes outside their role (e.g. "Ombudsman Decision", "Award Passed").
32. A DEO with no complaints gets "No complaints currently assigned to you." with no distortion.

**E6 — Routing, fallback and trace**
33. Ombudsman Admin sees the list of active users for RBIOS and CRPC.
34. A new RBIO complaint is routed to an **active** Dealing Official per the threshold and logic.
35. A new CRPC complaint is routed to an active CRPC DEO per the same logic.
36. When the mapped DO is unavailable (inactive or on leave) the complaint **falls back to Ombudsman
    Admin** — for RBIO and for the CRPC DEO case.
37. The Ombudsman Admin **receives a fallback notification** in each case.
38. A complaint whose category has **no mapping in master data** safely triggers the fallback to
    Ombudsman Admin.
39. **Trace log** retrievable from the complaint's summary → history icon, showing timestamp,
    complaint ID, category checked, master-data mapping rule triggered, and **initial and final**
    assigned officer ID. *(Build item — see above.)*
40. Round-Robin, Entity Name and Category assignment logic **all** skip inactive/on-leave users.

**E7 — Scheme-coverage routing**
41. An entity **not** under the Scheme routes to the **CEPC** DO, and the events are traced in
    history.
42. An entity **under** the Scheme does **not** route to CEPC; the corresponding RBIO DO receives it.
43. In real time, complaints land in the correct queue per scheme coverage, verifiable from the
    summary view's history icon.
    **Re-baseline 41-43 against `RBIOS_2021`, not 2026.**

---

## Shared-state rules — read before writing anything

Three sessions share one database (`cms_db` on 3306), one Keycloak (9090), and the repo.

- **Your git worktree:** work in an isolated worktree. `cms-backend/target/` is a single directory;
  concurrent `mvn` in the same checkout produces **phantom compile errors blaming files you never
  touched**. If you see one, verify with an isolated `javac` (JDK 17, `-encoding UTF-8`,
  `-proc:full`) before believing it.
- **Your backend port is 8095.** Start it with `deployment/run-test-backend.sh 8095`, which passes
  `-Dcms.hazelcast.cluster-name=cms-claude-8095` and `-Dhazelcast.discovery.enabled=false`. Note
  `-Dhz.cluster-name` is **silently ignored** — wrong property name, node joins the user's cluster,
  cache assertions become meaningless. **Port 8092 belongs to the user — never restart or stop it.**
  8094 is `cms-workflow-service`; you own its code but **ask me before restarting it**, since D and F
  read from it. D has 8093, F has 8096.
- **The script lies about failure.** `run-test-backend.sh <port> --restart` prints
  `ERROR: backend failed to start` and exits 1 **while the app is healthy**, because its readiness
  poll greps a log still holding the previous run's `BUILD FAILURE`. Verify with `netstat` + `curl`.
- **Keycloak on 9090 appeared DOWN when this brief was written.** Exactly one session may start it and
  exactly one may run `deployment/provision-aa-roles.sh` (read-modify-write on
  `unmanagedAttributePolicy`). **Coordinate through me.** Your E1 block needs real Keycloak logins —
  so you are the most likely session to need it. **Ask before starting it.**
- **There is no Flyway.** Schema comes from `ddl-auto: update`; `database/*.sql` is hand-run. **New
  columns must always be nullable**, stagger backend startups, one session per entity. The
  assignment-trace table for case 39 is **yours**.
- **Your reserved numbers** (highest currently in use: MySQL V109, Oracle V106, seeder `@Order` 66):
  - MySQL migrations **V115-V119**, Oracle **V112-V116**. Write both; Oracle variants are not
    optional here.
  - Seeder `@Order` **75-79**. One new seeder class per session — repo convention. **Never edit
    another session's seeder.** Translation keys are idempotent by `existsByCode`, so two sessions
    using the same code silently keep the first text, which looks like a UI bug. Prefix yours
    `rbio.grid.*` / `rbio.search.*` (extend existing keys rather than inventing parallel ones).
  - New spec files named `e2e/rbio/rbios-e-*.spec.ts`. **Do not edit the existing 19 specs under
    `e2e/rbio/`** except to fix a test you provably broke — and `s1-home-grid.spec.ts` is the one you
    will be most tempted to edit. Extend in your own file instead.
  - Seeding helpers: create `e2e/utils/helpers-rbios-e.ts`. **Do not edit
    `e2e/utils/test-data.ts`** (~1589 lines, a known chokepoint) — import from it.
  - Complaint subjects you seed must start with `RBIOS-E `. Mobile numbers you use must be in the
    range `98765_2____` — OTP cooldown and duplicate detection both key on mobile, and D/F use other
    ranges.
- **Chokepoint files owned by ONE session.** `RbioComplaintListController.java`,
  `RbioComplaintListService.java`, `RbioStatusMasterSeeder.java`, `OfficeAssignmentStrategyService.java`,
  `MreEntityCoverageService.java` and all of `cms-workflow-service/` are **yours**.
  `WorkflowController.java` belongs to **D**; `RbioWorkflowService.java`, `RbioTransitionRegistry.java`,
  `RbioLadderActions.java` belong to **F**. `admin/master-data/*` is **D's**. Ask me before crossing.

## Running the suite

From `cms-portal-frontend`, or the `chromium` project is not found:

```
API_BASE_URL=http://localhost:8095 \
UI_BASE_URL=http://localhost:4202 APP_BASE_URL=http://localhost:4202 \
WORKFLOW_BASE_URL=http://localhost:8094 \
PW_OUTPUT_DIR=/c/tmp/pw_E PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_E_report \
npx playwright test e2e/rbio/rbios-e-... --project=chromium --reporter=line
```

- **Both `UI_BASE_URL` and `APP_BASE_URL` are required.** `playwright.config.ts` reads only
  `UI_BASE_URL`; ten specs under `e2e/public/` read `APP_BASE_URL` and default to 4200. Setting one
  gave 47 fake `ERR_CONNECTION_REFUSED` failures.
- **Use dev-server port 4202.** A dev-server port must be in **both** the `dev-local` CORS allowlist
  **and** the Keycloak `cms-frontend` client's `redirectUris`. `application-dev-local.yml` hardcodes
  `cms.cors.allowed-origins: http://localhost:4200,4201,4202,4300` **with no `${}` placeholder**, so
  `CMS_CORS_ORIGINS` and `-Dcms.cors.allowed-origins` are both silently ignored. An unlisted origin
  gets `403 Invalid CORS request` on every XHR: pages render empty, translation keys show raw, and
  "Complaint not found" appears for records that exist. Verify with
  `curl -s -o /dev/null -w "%{http_code}" -X OPTIONS <api>/api/v1/i18n/translations/en -H "Origin:
  http://localhost:4202" -H "Access-Control-Request-Method: GET"` — must be 200, not 403.
  Ports 4200 and 4202 are already up; **do not run `ng serve` yourself.**
  Note `e2e/rbio/harness.ts` deliberately hardcodes `ALLOWED_ORIGIN='http://localhost:4200'` because
  it asserts CORS behaviour — leave that alone.
- **Import `test` and `expect` from `../rbio/harness`** (or `../fixtures` outside `e2e/rbio/`),
  **never from `@playwright/test`**. A spec importing raw Playwright writes to your backend but reads
  from the user's 8092, because the Angular bundle has `apiBaseUrl: 'http://localhost:8082'` compiled
  in and only the custom `page` fixture rewrites it.
- **Never `--headed` for bulk runs.** Run per directory and aggregate; the single full-suite command
  can buffer indefinitely through `tail`.
- **Roughly half the suite cannot produce screenshots** — 29 specs (398 tests) are pure API tests
  using the `request` fixture. When a browser test fails, read
  `test-results/<test>/test-failed-1.png` with the Read tool. Screenshots have repeatedly beaten
  log-reading here: they revealed an unexpected login page, a validation banner, and an
  authenticated-but-empty profile, each overturning a wrong hypothesis.
- Your E1 block is browser-heavy. A `:has-text("X")` locator matching a longer superstring
  ("Nodal Officer" vs "Principal Nodal Officer") trips strict mode — use `.first()` or an exact match.
- Bulk suites can trip a **429 anti-automation throttle**. If you see 429s, slow down rather than
  removing the throttle.

## Assertion rules specific to this module

- **RBIO workflow writes frequently answer HTTP 200 with `{"success": false}`.** A refused write is
  *not* a 4xx. **Assert on the body.** Only paths using `ResponseStatusException` give real error
  statuses. Note the *search* refusals above genuinely are 400s — that is deliberate and different.
- **Never hardcode vocabularies.** Read status filters from `GET /api/v1/rbio/status-filters?role=`
  and clauses from `GET /api/v1/workflow/closure-clauses`.
- **A 404 may be a stale JVM, not a missing endpoint.** Check the JVM start time against
  `target/classes` mtimes first. Four endpoints once looked exactly like phantom endpoints and three
  were real after a restart.
- **A skip is not a pass.** Report passed / failed / skipped separately.
  `e2e/utils/auth.ts:isKeycloakAvailable()` returns false on any throw and callers `test.skip()`, so a
  Keycloak outage yields a green but vacuous staff suite — which would silently gut your entire E1
  block.
- **Never attribute a mass of identical UI-locator failures to N defects** before driving one in a real
  browser. 25 `aa` failures once had exactly one cause: an unauthenticated browser context.
- Seeded users: `_001`-style accounts use password `test123`; `cms.admin` / `rbio.*` use `Test@1234`;
  legacy snake/dotted users use `password`. Relevant to you: `rbio_do_001`, `rbio_reviewer_001`,
  `rbio_dyombudsman_001`, `rbio_ombudsman_001`, `orbio_admin_001` (RBIO_ADMIN+SUPER_ADMIN),
  `cepc_do1`/`cepc_reviewer1` (`password`) for the isolation tests, `deo_001/2/3` (DEO) for E5, and
  `rbio.supervisor.mum` as the only usable second-office identity. Six accounts have **no password
  credential at all** (`rbio.adjudicator`, `rbio.conciliator`, `rbio.officer.del`,
  `rbio.officer.mum`, `rbio.supervisor.del`, `rbio_officer_002`, `rbio_officer_003`) — they return
  `Invalid user credentials`, which is not a bug. Re-probe rather than trusting this list.
- `identityHeadersFor(actor, scope)` in `e2e/utils/test-data.ts` derives role from actor name; scope is
  `CEPC | RBIO | AA | RE` and an actor name alone is ambiguous (`admin_001` drives both CEPC and RBIO).
  `rbio_reviewer_001` returns `CEPC_REVIEWER` without an explicit scope.
- Useful helpers already in `test-data.ts`: `createRbioComplaint`, `performRbioAction`,
  `advanceRbioToStatus`, `rbioGetComplaint`, `rbioReadComplaintColumns`, `fileComplaintForOffice`,
  `getOfficeThresholds`, `setOfficeThreshold`, `resetOfficeCounters`, `cleanupRbioComplaint`.
  `e2e/rbio/harness.ts` also exports `ageComplaintPastSla(complaintNumber, daysOld)`.

## How to work

1. **Verify the audit above against today's code first.** Report anything that has changed. A
   contradicted finding is a valuable result, not a setback.
2. Then work in order: E1 (login/launch), E2-E3 (grid, isolation, search — mostly extending existing
   coverage), E4-E5 (status filters, scoping), E6-E7 (routing, fallback, trace, scheme coverage).
3. **Write the failing test before the fix.** For every manual case that contradicts intended
   behaviour — especially the status-code counts, `CREATED_BY_ME`, and the RB-IOS 2026 framing — record
   it and **ask me** rather than reshaping the product to match the sheet.
4. **Run only the specs you touched while you work.** Run the full `e2e/rbio` directory once at the
   end. Do not run the whole suite between increments.
5. **Never `git push`, never `git stash`, never `rm -rf`, never `taskkill`.** Do not stash to "prove a
   baseline" — it has destroyed work here before.
6. The backend test baseline was **971 tests, 0 failures** as of 2026-09-16 and ~1310 by 2026-09-21.
   **If a backend test fails, assume it is yours.** Do not excuse it as pre-existing without
   verifying on a clean tree.
7. Write findings continuously to `docs/prompts/findings-RBIOS-E.md`: per case —
   **PASS / FAIL / NOT-BUILT / CONTRADICTS-SPEC**, the evidence, and what you changed. The
   contradictions list is the highest-value output of this session.

Report at the end: cases automated, cases passing, defects fixed, build items completed, the
role-by-role status-code diff, and the list of open decisions you need from me.
