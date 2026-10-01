# RB-IOS QA Session D — Ombudsman Admin: user administration, Master Management, reassignment

You are one of **three parallel Claude sessions** (D, E, F) converting a set of ~250 manual QA test
cases for the RB-IOS (RBIO / Ombudsman office) module into Playwright E2E specs in the CMS 2.0
repository, and **fixing the product where a case legitimately fails**.

Repo: `c:/Projects/My-HRMS-Frontend/CMS2.0_Redzone`
Frontend: `cms-portal-frontend/` (Angular). Backend: `cms-backend/` (Spring Boot, package
`com.hrms.cms`). A second small service lives in `cms-workflow-service/` (`com.rbi.cms.workflow`).

Sessions E and F are running at the same time against the same database and the same Keycloak.
**Read "Shared-state rules" before you touch anything.**

---

## Your scope

You own the **Ombudsman Admin** surface: creating and administering RBIO users, Master Management,
and complaint reassignment. This is the heaviest *build* session of the three — a pre-flight audit
found most of it does not exist yet.

### What the audit found (verify before trusting — it is a point-in-time reading)

**Genuinely NOT FOUND — these are build items, not test items:**
- No RBIO user create/edit form. Repo-wide grep for `Employee Details`, `Access Permission`,
  `Save & New`, `saveAndNew` hits **documentation only** — zero source hits.
- No RBIO user-management route in `cms-portal-frontend/src/app/app.routes.ts`. The only RBIO routes
  are `rbio`, `rbio/create-complaint`, `rbio/complaint/:id`, `rbio/supervisor-dashboard`, and
  `staff/rbio/tasks|task/:id|history|escalations`.
- `KeycloakUserController.java` (`/api/v1/keycloak`) is **read-only — 4 GETs**
  (`/users/deos`, `/users/reviewers`, `/users/all`, `/users/by-role`). No POST, no PUT.
  It **hardcodes `isOnLeave = false`** at roughly lines 25 and 42.
- **The data model already exists and is orphaned.** `cms-backend/.../entity/RbioStaffProfile.java`
  (designation, officeCode, primaryRole, leaveFromDate/ToDate, leaveSetBy/At, closureEligible,
  isActive) and `entity/RbioStaffAudit.java` (constants `ACTION_USER_CREATED`,
  `ACTION_ACTIVATION_CHANGED`, `ACTION_LEAVE_CHANGED`, `ACTION_OWNER_REASSIGNED`,
  `ACTION_ROLE_CHANGED`, `ACTION_MASTER_CHANGED`), with tables in
  `database/V87__rbio_staff_profile.sql` and `database/oracle/V85__rbio_staff_profile.sql`.
  **There is no service and no controller for either.** `RbioStaffAuditRepository` has no
  production reader or writer at all. **Build on these entities — do not invent new ones.**
- No "Change Owner" button anywhere. Grep for `Change Owner|changeOwner|change-owner|CHANGE_OWNER`
  returns only the `OWNER_REASSIGNED` audit constant, the SQL file, and docs.
- No RBIO **bulk** reassignment endpoint and no multi-select UI. (A real CRPC one exists at
  `POST /api/v1/crpc/head/bulk-reassign` backed by `service/BulkReassignService.java` — study it as
  prior art, but it is CRPC, driven by `crpc/ops-head/ops-head.component.ts`.)
- Master Management: `src/app/components/admin/master-data/master-data.component.ts` (route
  `admin/master-data`) has exactly **two tabs** — `'categories' | 'routing'`. No office-level
  config, no RBIO role create/modify, no office structure, no PNO/NO mapping editor, no
  config-change audit view. `MasterDataController.java` (`/api/v1/masters`) covers only categories
  and department-routing.
- No general config-change audit log. `CONFIG_AUDIT_LOG` exists but its only writer
  (`TimelineConfigService`) **refuses any key not prefixed `timeline.`**, and the table has six
  columns with no reason and no description field.
- The on-leave notification does not do what the manual case says. `NotificationScheduledTasks.java`
  `checkOnLeavePending()` is a `@Scheduled(cron = "0 0 8 * * MON-FRI")` job that scans for
  complaints whose `workflowStage == "ON_LEAVE"` — a stage **nothing ever sets**, because no
  endpoint marks a user on leave in cms-backend. And it emits **one notification per complaint**,
  not one notification listing a user's pending complaints.

**Existing behaviour that contradicts the manual cases — assert the real behaviour and record the
contradiction, do not "fix" it into the manual case's shape without asking:**
- `REASSIGN` is **not** Ombudsman-Admin-only. `service/RbioTransitionRegistry.java` grants it to
  `RBIO_ADMIN` **and** the supervisor / reviewer / deputy role sets. The manual cases assert
  "any user other than Ombudsman Admin should not be able to reassign". That is a real
  under-restriction — write the failing test, then **ask me** before narrowing the grant, because
  tightening it may break the S3 ladder send-backs that E and F depend on.
- `REASSIGN` is refused from `closed, resolved, rejected, withdrawn, adjudicated, conciliated`
  (`RbioTransitionRegistry.java` ~line 244, an exclusion set). Single reassignment itself works:
  action `REASSIGN` via `POST /api/v1/workflow/rbio/action/{complaintNumber}`, target from
  `targetUser` / `targetUserId`.

### Your case list (condensed from the manual sheet — ~95 cases)

Boilerplate "user is able to login" / "list of complaints is displayed" preamble rows repeat across
blocks. **Collapse them into one shared login assertion per role**; do not write 40 identical login
tests. Session E owns login/authentication testing proper.

**D1 — Create RBIO user (Ombudsman Admin)**
1. Ombudsman Admin can log in and has access to create RBIO users.
2. "Create user" button is displayed to the Ombudsman Admin.
3. Clicking it opens a form with **Employee Details** and **Access Permission** tabs.
4. Each tab's fields are clearly grouped and labelled.
5. Entering mandatory details and saving creates the new RBIO user.
6. Roles can be assigned under Access Permission — **single** role.
7. Roles can be assigned under Access Permission — **multiple** roles.
8. "Save & New" is displayed on the form.
9. Clicking "Save & New" creates the user **and resets the form** for the next onboarding.
10. With mandatory fields blank, validation errors block "Save & New".
11. Newly created user appears **immediately** in the office's user list — **no page refresh**.
12. The user-list entry reflects the assigned role(s) and scope.

**D2 — Active / Inactive**
13. Ombudsman Admin has access to mark users Active/Inactive.
14. Mark a **single** user Active/Inactive; status updates.
15. Mark **multiple** users Active/Inactive in **one bulk action**; statuses update.

**D3 — On Leave**
16. Ombudsman Admin has access to mark a user On Leave.
17. **No role other than Ombudsman Admin** can mark a user On Leave.
18. Mark a **single** user On Leave; status updates.
19. Mark **multiple** users On Leave in one bulk action; statuses update.

**D4 — On-leave notification and workload handover**
20. A notification is generated automatically when a user is marked On Leave **while holding open
    complaints**.
21. The Ombudsman Admin receives a notification **listing** that user's pending complaints, with a
    direct link for reassignment.
22. The Ombudsman Admin can view the list of pending complaints of users marked On Leave.
23. The Ombudsman Admin can reassign that workload.
24. After reassignment the pending count for on-leave users **decreases**.

**D5 — Reassignment / Change Owner**
25. Ombudsman Admin has access to reassign complaints; the complaint list is displayed.
26. A user other than Ombudsman Admin cannot reassign. *(Known to currently fail — see above.)*
27. "Change Owner" button is displayed.
28. Reassign a **single** complaint via Change Owner; new owner is shown.
29. Select **multiple** complaints and reassign via **bulk** Change Owner.
30. After reassignment the new-owner data is updated everywhere it is displayed.
31. Manual reassignment lists **only active** users.
32. On-leave / inactive users are **not** offered. *(See the live bug note below.)*
33. The user list **refreshes when a user's status changes** mid-flow.
34. Audit trail after reassignment records: **old owner, new owner, acting user, timestamp**, and an
    audit entry exists for **every** reassignment event.

**D6 — Master Management**
35. "Master Management" is displayed to the Ombudsman Admin and is clickable.
36. **No role other than Ombudsman Admin** has access to Master Management.
37. Office-level configuration is centralised there and changes save.
38. Ombudsman Admin can **create** Ombudsman roles from Master Management.
39. Ombudsman Admin can **modify role mappings**.
40. Ombudsman Admin can configure the **office structure**.
41. Newly created users and edited user details are reflected.
42. Ombudsman Admin can edit **PNO/NO user mappings**; mapping changes apply to **new NO record
    auto-creation going forward** (not retroactively).
43. All Master Management configuration changes are logged for audit — **each** change recorded with
    **user, timestamp and change description**, and the log is retrievable.

### A live bug in your area, worth fixing

`WorkflowController.java` (~lines 625-637) `GET /api/v1/workflow/assignable-users?role=` calls
`keycloakUserService.getUsersByRole(role)` and filters **only** on
`userId.toLowerCase().contains("secretary")`. There is **no active check and no on-leave check**.
Its upstream `KeycloakUserController` hardcodes `isOnLeave=false`, and
`service/RbioCaseAssignmentHistoryService.java` (~line 180) does the same with a candid comment:
*"isOnLeave is reported as false because no leave register exists"*. So every manual-assignment
dropdown in the product will offer unavailable staff. **Cases 31-33 depend on fixing this.** The
leave register you build in D1-D3 (on `RbioStaffProfile`) is what makes the fix possible.

Session E owns the *other* on-leave bug (in `cms-workflow-service`'s round-robin). Do not touch
`cms-workflow-service/`.

---

## Shared-state rules — read before writing anything

Three sessions share one database (`cms_db` on 3306), one Keycloak (9090), and the repo.

- **Your git worktree:** work in an isolated worktree so your `mvn` output cannot collide with E's
  and F's. `cms-backend/target/` is a single directory; concurrent `mvn` in the same checkout
  produces **phantom compile errors blaming files you never touched**.
- **Your backend port is 8093.** Start it with `deployment/run-test-backend.sh 8093`. That script
  passes the required `-Dcms.hazelcast.cluster-name=cms-claude-8093` and
  `-Dhazelcast.discovery.enabled=false`; without a distinct cluster name your node joins the user's
  Hazelcast cluster and every cache-dependent assertion becomes meaningless. **Port 8092 belongs to
  the user — never restart or stop it.** 8094 is `cms-workflow-service`. E has 8095, F has 8096.
- **The script lies about failure.** `run-test-backend.sh <port> --restart` prints
  `ERROR: backend failed to start` and exits 1 **while the app is healthy and serving**, because its
  readiness poll greps a log file still holding the previous run's `BUILD FAILURE`. Verify with
  `netstat` + `curl` before believing it.
- **Keycloak on 9090 appeared DOWN when this brief was written** (`/realms/cms/.well-known/...`
  returned nothing). Exactly **one** session may start it and exactly one may run
  `deployment/provision-aa-roles.sh` (its `unmanagedAttributePolicy` step is a read-modify-write).
  **Coordinate through me — do not run provisioning on your own initiative.** If you need it and are
  unsure whether another session has it, ask.
- **There is no Flyway.** Schema comes from `spring.jpa.hibernate.ddl-auto: update`; `database/*.sql`
  is hand-run. Consequences on a shared DB: **new columns must always be nullable**, stagger backend
  startups, and only one session changes a given entity. `RbioStaffProfile` / `RbioStaffAudit` are
  **yours** — E and F will not touch them.
- **Your reserved numbers** (highest currently in use: MySQL V109, Oracle V106, seeder `@Order` 66):
  - MySQL migrations **V110-V114**, Oracle **V107-V111**. Write both; the Oracle variants are not
    optional in this repo.
  - Seeder `@Order` **70-74**. One new seeder class per session — repo convention, stated in
    `AaRegisterTranslationSeeder.java`. **Never edit another session's seeder.** Translation keys are
    idempotent by `existsByCode`, so two sessions using the same key code silently keep the first
    text, which looks like a UI bug rather than a conflict. Prefix your new keys `rbio.admin.*`.
  - New spec files named `e2e/rbio/rbios-d-*.spec.ts`. **Do not edit the existing 19 specs under
    `e2e/rbio/`** except to fix a test you provably broke.
  - Seeding helpers: create `e2e/utils/helpers-rbios-d.ts`. **Do not edit `e2e/utils/test-data.ts`**
    (~1589 lines, a known chokepoint) — import from it.
  - Complaint subjects you seed must start with `RBIOS-D ` so E and F can tell your rows apart.
- **Chokepoint files owned by ONE session.** `WorkflowController.java` (~935 lines) is **yours** for
  the `assignable-users` fix — tell me if E or F needs it. `RbioWorkflowService.java`,
  `RbioTransitionRegistry.java` and `RbioLadderActions.java` belong to **F**; if case 26 requires a
  registry change, **ask me first**.

## Running the suite

From `cms-portal-frontend`, or the `chromium` project is not found:

```
API_BASE_URL=http://localhost:8093 \
UI_BASE_URL=http://localhost:4202 APP_BASE_URL=http://localhost:4202 \
WORKFLOW_BASE_URL=http://localhost:8094 \
PW_OUTPUT_DIR=/c/tmp/pw_D PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_D_report \
npx playwright test e2e/rbio/rbios-d-... --project=chromium --reporter=line
```

- **Both `UI_BASE_URL` and `APP_BASE_URL` are required.** `playwright.config.ts` reads only
  `UI_BASE_URL`, but ten specs under `e2e/public/` read `APP_BASE_URL` and default to 4200. Setting
  one gave 47 fake `ERR_CONNECTION_REFUSED` failures.
- **Use dev-server port 4202.** A dev-server port must be in **both** the `dev-local` CORS allowlist
  **and** the Keycloak `cms-frontend` client's `redirectUris`. `application-dev-local.yml` hardcodes
  `cms.cors.allowed-origins: http://localhost:4200,4201,4202,4300` **with no `${}` placeholder**, so
  `CMS_CORS_ORIGINS` and `-Dcms.cors.allowed-origins` are both silently ignored. An unlisted origin
  gets `403 Invalid CORS request` on every XHR and the page renders empty with raw translation keys —
  which looks exactly like a broken feature. Ports 4200 and 4202 are already up; **do not run
  `ng serve` yourself.**
- **Import `test` and `expect` from `../rbio/harness`** (or `../fixtures` outside `e2e/rbio/`),
  **never from `@playwright/test`**. A spec importing raw Playwright writes to your backend but reads
  from the user's 8092, because the Angular bundle has `apiBaseUrl: 'http://localhost:8082'` compiled
  in and only the custom `page` fixture rewrites it. `e2e/rbio/harness.ts` additionally forces
  `Origin: http://localhost:4200` on replayed requests and answers `OPTIONS` preflight itself.
- **Never `--headed` for bulk runs.** Run per directory and aggregate; the single full-suite command
  can buffer indefinitely through `tail`.
- **Roughly half the suite cannot produce screenshots** — 29 specs (398 tests) are pure API tests
  using the `request` fixture. When a browser test does fail, read
  `test-results/<test>/test-failed-1.png` with the Read tool; screenshots have repeatedly beaten
  log-reading here and overturned wrong hypotheses.

## Assertion rules specific to this module

- **RBIO workflow writes frequently answer HTTP 200 with `{"success": false}`.** A refused write is
  *not* a 4xx. **Assert on the body.** Only paths using `ResponseStatusException` give real error
  statuses (`requireComment` → 400; `PREVIOUS_HOLDER`-unavailable → 422). Refusals must **not** be
  raised as `IllegalArgumentException` — `WorkflowController.performAction` catches it and converts
  it to 200 + `success:false`, which Angular's error branch never sees.
- **Never hardcode vocabularies.** Read status filters from `GET /api/v1/rbio/status-filters?role=`
  and clauses from `GET /api/v1/workflow/closure-clauses`.
- **A 404 may be a stale JVM, not a missing endpoint.** Check the JVM start time against
  `target/classes` mtimes before concluding an endpoint does not exist.
- **A skip is not a pass.** Report passed / failed / skipped separately.
  `e2e/utils/auth.ts:isKeycloakAvailable()` returns false on any throw and callers `test.skip()`, so
  a Keycloak outage yields a green but vacuous staff suite.
- Seeded users: `_001`-style accounts use password `test123`; `cms.admin` / `rbio.*` use `Test@1234`;
  legacy snake/dotted users use `password`. Relevant to you:
  `orbio_admin_001` (RBIO_ADMIN + SUPER_ADMIN), `rbio_do_001` (RBIO_DEALING_OFFICIAL),
  `rbio_reviewer_001`, `rbio_dyombudsman_001`, `rbio_ombudsman_001`, `cms.admin` (ADMIN+SUPER_ADMIN).
  `rbio.supervisor.mum` is the only usable second-office identity. Re-probe rather than trusting this
  list — the realm is hand-edited between sessions.
- `identityHeadersFor(actor, scope)` in `e2e/utils/test-data.ts` derives role from actor name; scope
  is `CEPC | RBIO | AA | RE`. An actor name alone is ambiguous. Note `rbio_reviewer_001` returns
  `CEPC_REVIEWER` without an explicit scope.

## How to work

1. **Verify the audit above against today's code first.** Report anything that has changed. A
   contradicted finding is a valuable result, not a setback.
2. Then, in order: build the leave register and staff-profile API (D1-D3 backend), the admin UI, the
   notification (D4), reassignment + audit (D5), Master Management (D6). Each with specs.
3. **Write the failing test before the fix.** For every manual case that contradicts intended
   behaviour, do not silently reshape the product — record it and ask.
4. **Run only the specs you touched while you work.** Run the full `e2e/rbio` directory once at the
   end. Do not run the whole suite between increments.
5. **Never `git push`, never `git stash`, never `rm -rf`, never `taskkill`.** Do not stash to "prove
   a baseline" — it has destroyed work here before.
6. The backend test baseline was **971 tests, 0 failures** as of 2026-09-16 and ~1310 by 2026-09-21.
   **If a backend test fails, assume it is yours.** Do not excuse it as pre-existing without
   verifying on a clean tree.
7. Write findings continuously to `docs/prompts/findings-RBIOS-D.md`: per case —
   **PASS / FAIL / NOT-BUILT / CONTRADICTS-SPEC**, the evidence, and what you changed. The
   contradictions list is the highest-value output of this session.

Report at the end: cases automated, cases passing, defects fixed, build items completed, and the
list of open decisions you need from me.
