# CMS 2.0 — Test Report

**Date:** 2026-09-16 (final)
**Scope:** security batch — B1 (authorization enforcement), UST873, UST875, UST877, UST887, UST890
**Baseline commit:** `ae87ad5`

---

## 1. Headline

| Suite | Result |
|---|---|
| Backend JUnit | **971 tests · 0 failures · 0 errors · BUILD SUCCESS** |
| Playwright — `admin` | **51 passed · 0 failed** (6 skipped: workflow-service down) |
| Playwright — `i18n` | **9 passed · 0 failed** |
| Playwright — `public` | **106 passed · 4 failed** (clause-interpolation content, not this batch) |
| Playwright — `re-portal` | **23+ passed · 0 auth failures** (2 `fixme`, see §6) |
| Playwright — `rbio` | 13 passed · 10 failed (UI locators; 0 auth errors) |
| Playwright — `cepc` | 8 passed · 20 failed (UI locators; 0 auth errors) |
| **Auth / PII / 500 errors remaining** | **0** |
| `mvn -o compile`, `npx ng build` | pass |

The 4 security specs added by this batch all pass: **masking 13 · security-enforcement 22 · retention 9 · safe-deactivation 6**.

---

## 2. Backend JUnit — before vs after

| | Tests | Failures | Errors | Failing classes |
|---|---|---|---|---|
| Baseline at `ae87ad5` (clean worktree) | 848 | 7 | 161 | **15** |
| **Now** | **971** | **0** | **0** | **0** |

### Attribution (clean-worktree diff)
`git worktree add /c/tmp/cms-baseline HEAD --detach`, run `mvn -o test` in both trees, compare failing
class names. Of 15 failing classes, **only `RePortalControllerTest` was caused by this work** (the new
`RevocationCheckFilter`); the other 14 were already broken. All 15 now pass.

### Root causes fixed
1. **`@WebMvcTest` instantiates servlet filters.** This app's filters carry heavy collaborators
   (`PiiDecryptionFilter`→`EncryptionKeyService`, `RevocationCheckFilter`→`CredentialRevocationService`);
   one missing bean fails the whole context, so every method errors for an unrelated reason.
   `@AutoConfigureMockMvc(addFilters=false)` does **not** help — it stops filters being *applied*, not
   *instantiated*. Fixed centrally via `src/test/java/com/hrms/cms/support/ControllerSliceTest.java`,
   adopted by all 11 controller tests.
2. **`@WebMvcTest` does not load `@Configuration`**, so Boot's default security applied inside slices
   (401 on public paths, 403 on POSTs via CSRF).
3. **Un-mocked Lombok constructor deps** in 4 service tests — fixed with `@Mock`, not `LENIENT`.
4. **`/api/v1/appeals/file` is multipart**; tests posted JSON, failing argument resolution.
5. **Two stale assertions** corrected against self-consistent production logic.

### Stale expectation found in a config test
`FileStorageConfigTest.shouldHaveCorrectDefaults` asserted a 50MB default file size, but NFR-006 caps a
single attachment at **2MB** — the production default and `application.yml` both say 2MB, and the field
carries a comment recording the change. Neither the test nor `application.yml` was mine
(`FileStorageConfig.java` was modified by another session); the test expectation was simply stale and is
now corrected to 2097152L.

### One regression caught and fixed mid-run
A concurrent session added a mandatory dependency to `GlobalExceptionHandler`. Because that class is a
`@RestControllerAdvice`, it loads into **every** slice — producing `Tests run: 971, Errors: 217` and
BUILD FAILURE from a single missing bean. Resolved (the owning session switched it to optional setter
injection); verified back to 971/0/0.

---

## 3. Failures this work legitimately caused, and how they were fixed

Closing the AOP guards' fail-open branch correctly broke **86 E2E tests** whose seed helpers sent no
identity at all. These were test defects — the previous pass depended on the vulnerability.

- `e2e/utils/test-data.ts` now exports **`identityHeadersFor(actor, scope)`**, used by all helpers. The
  role is derived from the actor's name so a step run as `cepc_reviewer_001` authenticates *as* a
  reviewer; hardcoding one privileged role would let tests pass while exercising the wrong identity.
- `scope` disambiguates actors reused across offices (`admin_001` drives both CEPC and RBIO).
- RE is matched **before** CEPC, or `re_nodal_001` falls through to `CEPC_DO`.

---

## 4. Production defects found and fixed

All in `config/GlobalExceptionHandler.java`, whose catch-all `RuntimeException` handler rewrote every
error as HTTP 400:

1. **403 was downgraded to 400.** Role guards throw `ResponseStatusException(FORBIDDEN)`, but clients
   saw "400 Bad Request" — indistinguishable from a malformed payload, so a denial could not be
   detected or alerted on. Added `ResponseStatusException` + `SecurityException` (→403) handlers.
2. **Missing request parameters surfaced as 500.** Added handlers for the four malformed-request
   exceptions (→400).
3. **A missing route returned 500.** An unmatched `/api/**` path reaches the static-resource handler and
   threw `NoResourceFoundException`, reported as 500 — 21 occurrences in one E2E run from a single absent
   endpoint (`/api/v1/masters/account-types`, called by `file-complaint.component.ts:793`). Now 404.
   **The absent endpoint itself is still absent** — either implement it or drop the frontend call.
4. **Config audit log could return a superseded row.** `findByConfigKeyOrderByChangedAtDesc` sorted on a
   second-precision column, so several edits within one second ordered arbitrarily and "the latest audit
   entry" was not reliably latest — bad in an audit trail. Now tie-broken by id.

---

## 5. Browser-only suites — why their numbers are not evidence

`e2e/aa`, `e2e/cepc`, `e2e/rbio` are ~100% `page.*`-driven (verified: zero `request.*`).

**`API_BASE_URL` only redirects Playwright's `request` fixture.** The browser reads
`src/environments/environment.ts` (`apiBaseUrl: http://localhost:8082`) and `proxy.conf.json` proxies
only `/api/pincode` — so these tests hit port **8082** regardless of what the run targets. Confirmed by
running an unmodified spec and getting identical failures.

**Now addressed everywhere.** `e2e/fixtures.ts` + `e2e/utils/api-redirect.ts` add a `page` fixture that
rewrites browser requests from 8082 to `$API_BASE_URL`. All suites now import from it — `re-portal`,
`public`, `i18n`, and (finally) the 22 specs across `aa`/`cepc`/`rbio`.

Measured effect of the migration (backend under test on 8093):

| Suite | Before | After |
|---|---|---|
| `public` | 82 pass · 28 fail | **106 pass · 4 fail** |
| `i18n` | 2 pass · 7 fail | **9 pass · 0 fail** |
| `rbio` | 10 pass · 12 fail | **13 pass · 10 fail** |
| `cepc` | 6 pass · 19 fail | **8 pass · 20 fail** |

`public` and `i18n` improved sharply because they genuinely depend on backend data. `cepc`/`rbio` barely
moved, which is itself the useful finding: **their remaining failures are not backend-related at all** —
0 auth/403/500 errors, and every message is a UI locator or timeout
(`page.waitForSelector` ×14, `toBeVisible`, `element(s) not found`). They belong to the CEPC/RBIO UI
feature areas, not to this security batch.

Fixed while here: two Playwright strict-mode violations in `e2e/rbio/sla.spec.ts`, where
`locator('app-rbio-sla-progress, .rbio-sla-progress')` matched the component's host tag *and* its own
class on that same element (3 of 4 tests in that file now pass).

---

## 6. RE portal — root cause corrected

An earlier version of this report attributed 6 RE-portal failures to "the realm has no RE users". **That
was wrong**, and a screenshot disproved it: it showed a logged-in user with the portal rendered. Three
real causes, now fixed:

1. **Tests signed in as `cms.admin`** — no RE role, no entity code — so the entity-scoped API refused and
   the page showed "Complaint not found". Repointed to the real account `re_pno_001` / `test123`.
2. **The `cms-frontend` Keycloak client had zero protocol mappers**, so the user's `entity_code`
   attribute never reached the JWT and the backend correctly returned 403 "Your regulated entity could
   not be determined". Added the mapper (approved), and added `cms-frontend` to the client list in
   `deployment/provision-aa-roles.sh` — it previously mapped only clients nothing signs in through.
3. **`entity_code` held `HDFC0001`**, but the backend resolves entities by *normalized name*
   (`findByNameNormalized`), which that matches nothing. Set to `HDFC Bank` (id 139).

Also fixed: an ambiguous `:has-text("Nodal Officer")` locator matching both "Nodal Officer" and
"Principal Nodal Officer" (Playwright strict-mode violation), and a save test that filled only Phone
while Name and Email are mandatory.

**Two genuine product defects remain, needing a decision — not test defects:**
- `GET /api/v1/re-portal/complaints/{n}` never returns `responseDeadline`, and the component derives
  `isResponseWindowOpen()` solely from it — so the textarea and submit button are permanently disabled.
  **No regulated entity can submit a response through the UI.** The API already returns
  `withinResponseWindow: true`; the component never reads it. 2 tests marked `fixme`.
- `FORWARD_DEPT` forwards a complaint without creating the `ReResponseTracker` row the RE portal is
  keyed on, so `getComplaintDetail` 404s. Only `POST /api/v1/triage/re-track/{id}` creates it. Worked
  around in the test seed helper; the product path is still broken.

---

## 7. Known-environmental failures — do not chase

| Item | Count | Reason |
|---|---|---|
| `public/eligibility-clause-interpolation` + `faq` | 4 | clause-interpolation content assertions, another story's area |
| `admin/safe-deactivation` | 6 skipped | cms-workflow-service (8094) not running — skipped, not failed |
| `re-portal/reassignment` | 1 | `reassignment-auth.ts` hardcodes port 8095, nothing listening |
| Stale-classloader `NoClassDefFoundError` | — | `mvn compile` rewrote `target/classes` under a live JVM; restart the backend |

---

## 8. Still open

- **No `SECURITY_ADMIN` role** in the realm; the security console is gated on `ADMIN`, the only
  non-operational role. A dedicated role is a realm change awaiting a decision.
- **RE cross-entity denials are not counted** by `AnomalyDetectionService` — the hook exists, but
  `RePortalService`/`RePortalController` were owned by a concurrent session and left untouched.
- **31 dead `@PreAuthorize` annotations** across 7 controllers (no `@EnableMethodSecurity`), 2 citing
  non-existent roles. Left dead per decision; a known second-layer gap.
- **Retention destructive purge is OFF** and the `COMPLAINT_PII` redaction policy is seeded **disabled**
  pending sign-off on its column list.
- **`database/V17` is not applied automatically.** There is no Flyway/Liquibase — schema comes from
  Hibernate `ddl-auto: update`, so V17's `RETENTION_POLICY` and `SYSTEM_CONFIG` seed rows must be loaded
  by hand. Their absence made 5 security tests fail on an empty DB until re-applied.

---

## 9. Artifacts

| Path | Contents |
|---|---|
| `Document/test-reports/backend-junit-full.log` | full `mvn -o test` output |
| `Document/test-reports/backend-junit-BASELINE-at-HEAD.log` | baseline run at `ae87ad5` (848/7/161) |
| `Document/test-reports/playwright-*.txt` | per-suite Playwright runs |
| `cms-backend/target/surefire-reports/` | per-class JUnit XML + text |
| `cms-portal-frontend/playwright-report/index.html` | Playwright HTML report (last run) |
| `cms-portal-frontend/test-results/<test>/test-failed-1.png` | failure screenshots — the fastest route to root cause in this work |

**Reproduce:**
```bash
# Backend
cd CMS2.0_Redzone/cms-backend && mvn -o test

# Backend under test for E2E (dev-local, isolated Hazelcast)
mvn -o spring-boot:run -Dspring-boot.run.profiles=dev-local \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8093 -Dhz.cluster-name=cms-claude-8093 -Dhazelcast.discovery.enabled=false"

# Seed data V17 depends on (no Flyway in this project)
mysql -ucms_user -pcms_pass cms_db < database/V17__security_enforcement_and_retention.sql

# E2E, per directory (a single whole-suite run buffers and looks hung).
# Must run from cms-portal-frontend or the "chromium" project is not found.
cd CMS2.0_Redzone/cms-portal-frontend
for d in admin public i18n re-portal aa cepc rbio; do
  API_BASE_URL=http://localhost:8093 WORKFLOW_BASE_URL=http://localhost:8094 \
    npx playwright test "e2e/$d" --project=chromium --reporter=line
done
```

**Environment:** Spring Boot 3.2.5 / Java 17 · MySQL 8.4 `cms_db` · Keycloak 26 realm `cms` on :9090 ·
backend under test :8093 · Angular dev server :4200. Port 8082 is a separate developer's instance and
was never targeted or modified.
