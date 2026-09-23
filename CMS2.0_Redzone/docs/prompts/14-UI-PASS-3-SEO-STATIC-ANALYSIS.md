# BATCH: PASS 3 — FINISH UI HOMOGENISATION WITH EVIDENCE, MAKE THE PUBLIC PORTAL FINDABLE, AND CLEAR STATIC-ANALYSIS DEBT. Run alone.

Three tasks, **in this order**. Task 1 finishes unfinished business and produces the visual evidence
pass 2 failed to deliver. Task 2 is new product work. Task 3 is a quality pass before UAT.

Everything below was measured **2026-09-22** at HEAD `f180732` (branch `CMS_21092026`).
**Re-verify before relying on any of it; this file ages.** Where a previous pass's report was wrong,
that is called out — do not trust an earlier report over the code.

---

## 0. RULINGS — SETTLED, DO NOT RE-ASK

| # | Decision | Ruling |
|---|---|---|
| 0.1 | `10(1)(g)` clause-citation collision | **Accepted as-is for now.** Leave it. Keep the note in `ClosureClauseMasterSeeder`. Do NOT redesign clause citation. |
| 0.2 | `cms.aa.order.block_sub_judice` | **FIX IT — see 1.2.** No longer config-OFF-pending-legal. |
| 0.3 | `category_master` vs `complaint_categories` | **Decided by the implementer — see 1.3.** `complaint_categories` is authoritative. |
| 0.4 | Upload size | **5 MB everywhere. The surviving 2 MB values are a BUG — see 1.4.** Not introduced by the UI work; it predates it. Fix it properly. |
| 0.5 | Clause labels | Remain **English-only**. A translated clause label is a legal statement. A guard test exists in `e2e/ui-homogenisation/localisation.spec.ts`; keep it green. |
| 0.6 | Citizen brand | `--citizen-*` tokens stay **distinct** from the staff palette. Do not flatten. |

---

## 1. TASK 1 — FINISH THE UI HOMOGENISATION, WITH SCREENSHOTS THIS TIME

### 1.0 What pass 2 actually delivered, and what it did not

**Delivered (committed in `f180732`):** `shared/task-grid` (config-driven, CEPC migrated);
`ui.grid.*`/`ui.col.*` keys in 11 locales; Punjabi genuinely served (`PA` added to
`SupportedLocale` — it had been resolving to `"en"`, so "pa is identical to English" was comparing
English to English); compensation ₹33L = ₹30L + ₹3L; AA award caps split and **armed** (they were `0`,
which meant no ceiling enforced at all); FRC clauses `10(1)(e)`/`10(1)(g)` created; FAQ raw-key defect
retired (public suite **107P/4F → 111P/0F**); `public/` + `admin/` tokenised (repo-wide residual hex
**5004 → 806**).

**NOT delivered — this task:**
- **ZERO screenshots.** No `D:\screenShots\ui-homog-pass2\`, no PNG written after 13:00, every suite
  run WITHOUT `PW_SCREENSHOT=on`. The prompt demanded a deliberate capture spec and a coverage table;
  none exists. **This is the single biggest gap and the first thing to fix.**
- **No pass-2 report at all.** `D:\screenShots\ui-homog\index.html` is pass 1's, dated 12:22.
- **RBIO, re-portal, AA grids unmigrated** (see 1.5 — RBIO is deliberate).
- **Shared detail frame never built.** Still 6 separate detail components.
- **`app-shell` in only 3 of 48 module templates.**

### 1.1 Screenshots and the report — THE PRIORITY

Pass 1 produced 483 PNGs but only **36 usable before/after pairs**, because pairing keyed on
Playwright's auto-generated output-directory name and any renamed or newly-passing test had no
counterpart. Pass 2 produced none at all. Fix the method, not just the volume:

1. **Write a dedicated capture spec** (e.g. `e2e/ui-homogenisation/zz-visual-capture.spec.ts`) that
   navigates to every screen and saves a **STABLE, HAND-CHOSEN filename** —
   `grid-cepc-1280.png`, `detail-rbio-1440.png`. Stable names are what make pairing reliable.
   Use `page.screenshot({ path })` explicitly; do not rely on Playwright's failure-only capture.
2. **Authenticate.** Most staff screens were never photographed because the browser was
   unauthenticated and redirected. Use `loginAsCepcRole` / `loginAsRbioRole` / `loginAsReRole` /
   `loginAsAaRole` from `e2e/utils/auth.ts`. For citizen routes use `loginCitizen` +
   `seedCitizenSession`, and note `/appeal` is really **`/public/appeal`** — a child of `public`.
   Pass 1 lost 8 tests to that one wrong URL.
3. **Capture at 1280 AND 1440**, and verify nothing clips or horizontally scrolls at 1024.
   Responsiveness was claimed in pass 1 without being photographed.
4. **Screens required:** every module's task grid (crpc, cepc, rbio, aa, re-portal); every complaint/
   appeal detail; the public home, file-complaint, track, FAQ, eligibility wizard; the language
   switcher open; a toast; one modal. Plus **the same grid in `pa`** to prove Gurmukhi renders.
5. **State the ceiling honestly:** only ~354 of 752 tests touch `page.`; the rest are pure API tests
   and **cannot** yield an image (`e2e/admin/` produced 0 PNGs from 106 passing tests).
6. **Coverage table is MANDATORY:** "N of M screens have both a before and an after image", and list
   every gap **with its reason**. A missing screenshot must be explained, never omitted.

Deliver a **self-contained** `D:\screenShots\ui-homog-pass3\index.html` — embedded CSS, no CDN,
**relative** image paths so the folder zips. Extend the committed
`scripts/qa/ui_homog_report.py` rather than writing a second generator.

**Baseline to compare against (measured, post-`f180732`):**

| dir | passed | failed | skipped |
|---|---|---|---|
| public | 111 | 0 | 0 |
| admin | 106 | 0 | 9 |
| cepc | 28 | 1 | 6 |
| rbio | 182 | 3 | 11 |
| aa | 159 | 19 | 23 |
| re-portal | 29 | 2 | 39 |
| i18n | 9 | 0 | 0 |
| ui-homogenisation | 35 | 0 | 0 |

Backend: **1310 tests, 0 failures.** `mvn -q test` suppresses the summary — aggregate
`target/surefire-reports/*.xml`. The one `cepc` failure (`workflow-actions.spec.ts:36`) predates all
UI work. `re-portal` **skips 39 of 70** — a skip is NOT a pass; report skips separately everywhere.

### 1.2 Fix `block_sub_judice` (ruling 0.2)

Current: `cms.aa.order.block_sub_judice = false` in `system_config`. The comment in
`database/V56__aa_order_statutory_guards.sql` states the consequence plainly: *"FALSE = UNENFORCED
(current behaviour): an order CAN today be passed on a sub-judice matter."*

Enforcement lives in `cms-backend/.../service/AaAppealOrderService.java` (`CFG_BLOCK_SUB_JUDICE`,
~line 45; award validation at ~line 331).

Required:
- **Set the default to `true`** and make it enforce: refuse an order on an appeal whose appellant
  declared a related court trial **unless a ground is stated**.
- When a ground IS stated, **persist it on the order** as the audit record of the decision — the
  migration comment already specifies this. An override with no recorded justification is worse than
  no override.
- Keep it **configurable** via `system_config`, exactly like the compensation caps.
- Migration in **both** MySQL and Oracle, re-runnable (`WHERE NOT EXISTS` / value-guarded `UPDATE`).
  Free MySQL V-numbers: **V65-V70, V77-V80, V88-V90, V93, V94**. Free Oracle: **V63-V68**.
- Add tests: an order on a sub-judice appeal **without** a ground is refused; **with** a ground it
  succeeds and the ground is persisted; setting the flag `false` restores the old behaviour.
- Return a translated message, not raw English. There is an existing key convention —
  `aa.order.error_award_cap_exceeded` — follow it.

### 1.3 Resolve the duplicate category tables (ruling 0.3)

Measured:

| table | rows | contents | entity → endpoint |
|---|---|---|---|
| `complaint_categories` | 10 | **The real categories** — ATM/Debit Card, Credit Card, Internet Banking, Mobile Banking/UPI, Loan/Advances, Deposit Accounts, Pension, Remittance/Transfer, Insurance, Others. Seeded 2026-06-26, all active. | `ComplaintCategory` → `GET /api/categories` |
| `category_master` | 14 | **100% test pollution.** Every row named `S1 authority e2e probe`, all `active=0`, created 2026-09-19 by an E2E run that never cleaned up. | `CategoryMaster` → `GET /api/v1/masters/categories` |

**`complaint_categories` is authoritative** — it holds the real data and its ten values already match
the ten seeded `aa.ground.*` translation keys.

The defect: **`public/file-complaint` reads `masters/categories`** — the polluted table. So a citizen
filing a complaint sources the category list from test probes. `aa/aa-appeal-search` calls both.

Required:
- Repoint every caller at the authoritative source.
- **Purge the 14 test rows.** Migration, both dialects, re-runnable, scoped on the probe name so it
  cannot delete real data.
- **Do NOT drop `category_master`** — propose retirement in the report and let the user decide. A
  schema drop is not a UI batch's call.
- Categories have **no `label_key`**, so they are English-only in an eleven-locale product. Add
  translation keys and pipe them.
- Add a test that the citizen form's category list contains no string matching `/e2e|probe|test/i`.

### 1.4 Upload size: eliminate every surviving 2 MB (ruling 0.4)

Pass 2 moved the limit to `system_config` (5 MB / 25 MB) and added
`GET /api/v1/config/upload-limits`. **It missed six places.** These are live and contradict the ruling:

**Client-side gates that reject files the server now accepts — REAL, user-visible:**
- `crpc/draft-assessment/draft-assessment.component.ts:994` → `if (file.size > 2 * 1024 * 1024)`
- `crpc/physical-letter/physical-letter.component.ts:310` and **:333** → same

**Config:**
- `cms-backend/src/main/resources/application-dev-local.yml:84` → `max-file-size: 2097152`

**Seeded text in ten locales (a baked digit cannot track configuration):**
- `AaRegisterTranslationSeeder:137` → `aa.upload.error_file_too_large` = "Each file must be 2 MB or smaller."
- `IntakeTranslationSeeder` → `intake.attachment_too_large` = "2MB" in **en, hi, mr, te, ta, gu, kn, ml**
  (lines ~106, 260, 389, 645, 776, 910, 1168, 1300)

Required:
- Replace the three client gates with `UploadLimitsService` (the Angular one) so the browser uses the
  configured value. Do not hardcode 5 either.
- `application-dev-local.yml` → 5242880, so dev matches production behaviour.
- Convert both message keys to a **`{{size}}` placeholder** interpolated from config, and add a
  **corrective UPDATE migration** in both dialects — seeders are insert-if-absent by key code, so
  editing the Java default does NOT fix rows that already exist.
- **Also check the microservices, which disagree:** `cms-ingestion-service` caps at **10MB**,
  `cms-storage-service` at **50MB**, `cms-backend` servlet at **50MB**. The servlet caps are the outer
  bound against a hostile multipart body and must stay ABOVE the product rule — but the ingestion
  service's 10MB is a third product limit. Reconcile or document why it differs.
- Verify by **direct API upload** of a 4 MB file (must pass) and a 6 MB file (must be refused with a
  translated message), not just through the UI.
- Add a test asserting **no source file** outside a comment contains `2 * 1024 * 1024` or `2097152`.

### 1.5 Grids: server-side paging first, then migrate (do NOT force RBIO)

**RBIO's grid must not be naively migrated.** It has capabilities `shared/task-grid` cannot express,
and forcing it would be a functional regression dressed as homogenisation:

| RBIO feature | shared/task-grid today |
|---|---|
| **Server-side pagination** (`totalElements` from the API) | Client-side only — slices an in-memory array |
| **SLA bands + band refinement** (UST436: `bandFor`, `selectedBands`, `refinementActive`) | None |
| **Preserved search criteria in the empty state** (UST442) | Generic "no records" |
| **Retry action on load error** | Error text only |

RBIO's column filters are **already** signal-backed — a previous session fixed that there — so RBIO
does not have the bug that justified consolidation.

**Do this, in order:**
1. **Teach the shared grid server-side paging**: optional `totalCount` input and `pageChange` output.
   When `totalCount` is supplied the grid delegates paging to the caller instead of slicing. Note the
   footer once reported "of 20" because 20 was the backend page size — the count must be the server's
   total, never the loaded row count.
2. Add an **empty-state slot** so a module can supply preserved-criteria/retry content.
3. Then migrate **re-portal → aa → rbio**, gating on `ng build` + that module's suite after each.
4. Use the existing `kind: 'custom'` + `cellTemplates` hook for module-specific cells (CEPC's SLA
   indicator needed it; consolidation had silently downgraded it to plain text).
5. **If a migration still cannot preserve behaviour, STOP and report it** rather than shipping a
   regression. That judgement is the point.

### 1.6 Build the shared detail frame, and roll out the shell

Six detail components each invent their own section order, tab strip and action placement:
`aa/aa-appeal-detail`, `cepc/cepc-complaint-detail`, `rbio/rbio-complaint-detail`,
`re-portal/re-complaint-detail`, `public/complaint-detail`, `email-syndication/draft-detail`.

Standardise on CRPC's frame: identity header → status + SLA strip → tabbed sections → action bar.
**Sections differ by module; the frame must not.** Then roll `app-shell` out from **3 of 48**
templates to all staff screens, with the left-panel role-filtered nav.

**Say plainly in the report:** the role-based menu is presentation only. It decides what a user is
*offered*, never what they may *do* — route guards and server `@PreAuthorize` remain the enforcement.

### 1.7 Fix the dead `computed()` filters still live elsewhere

The bug the shared grid fixed still exists in: `crpc/deo-home` (`columnSearchText`),
`report-builder` (`filterSearch`), `complaint-history` (`filters`), `task-action`
(`pastComplaintSearch`). A plain field read inside a `computed()` registers no dependency, so typing
does nothing. **Drive every filter test BY TYPING** — asserting on the class field passes against
broken code.

---

## 2. TASK 2 — MAKE THE PUBLIC PORTAL FINDABLE (SEO)

**Goal:** the citizen complaint portal should rank first for searches like "RBI complaint",
"banking ombudsman complaint", "complain against bank India".

**Produce a written PLAN first, then execute it.** Report the plan in chat before large changes, since
some options (SSR) are architectural.

### 2.1 Measured SEO baseline — the blocker is structural

| Fact | State |
|---|---|
| **SSR / prerender** | **NONE.** No `main.server.ts`, no `server.ts`, no `"server"` target, no prerender config in `angular.json`. |
| `robots.txt` | **Exists**, wired into `angular.json` assets. Allows `/`, disallows `/admin/ /staff/ /crpc/ /officer/ /re-portal/`. Declares `https://cms.rbi.org.in/sitemap.xml`. |
| `sitemap.xml` | **Exists**, wired in, but only **6 URLs** and all under `/public/*`. |
| Per-route `<title>` / meta | **NONE.** Zero calls to `Title.setTitle` or `Meta.updateTag` anywhere. Every route shares one static title from `index.html`. |
| JSON-LD structured data | **0 occurrences.** |
| `index.html` meta | Has description, keywords, OG tags, canonical `https://cms.rbi.org.in` — all **static and single-valued**. |

**The dominant problem: this is a client-rendered SPA.** A crawler fetching `/public/faq` receives an
empty `<app-root>` and the same generic title as every other page. Google executes some JS, but
rendering is deferred and unreliable, and other engines and social-preview bots largely do not. No
amount of meta-tag work fixes that alone.

### 2.2 Required work

1. **Decide and justify the rendering strategy.** Options, with the trade-off stated:
   - **Angular SSR** (`@angular/ssr`) — best crawlability; adds a Node process to the OpenShift
     deployment, which today serves the frontend from **RHEL8 nginx** with `envsubst` runtime config
     injection. That is a real infrastructure change — flag it, do not assume it is acceptable.
   - **Prerendering / SSG** of the ~8 static public routes — no new runtime, works with nginx, covers
     home/FAQ/eligibility/track-landing. **Recommended first step**; it fits the existing deployment.
   - Dynamic rendering for bots — extra moving part, generally discouraged now.
   Note that authenticated and per-complaint pages must **stay out** of the index.
2. **Per-route titles and meta**, driven from route data via `Title`/`Meta`: unique title, description,
   canonical, OG/Twitter tags. **Every string must be a translation key** — this is an eleven-locale
   product and hardcoding titles rebuilds the defect the i18n work just removed.
3. **`hreflang`** for the eleven locales, plus `<html lang>` set from the active locale. This is a
   major differentiator for an Indian-language government service and directly serves the users the
   Scheme exists for.
4. **Expand `sitemap.xml`**: all public routes × locales, with `changefreq`/`priority`/`lastmod`.
   Generate it from the route table so it cannot drift — do not hand-maintain it. Keep it wired in
   `angular.json`.
5. **JSON-LD**: `GovernmentOrganization` (RBI), `WebSite` + `SearchAction`, `FAQPage` for the FAQ
   (built from the same 18 active FAQ rows — do NOT reintroduce the retired ten), and
   `BreadcrumbList`. Validate the output.
6. **Semantic HTML and headings**: exactly one `<h1>` per page, ordered headings, descriptive link
   text, `alt` on meaningful images (decorative ones stay `alt=""`).
7. **Core Web Vitals** — a ranking factor. Measure LCP/CLS/INP with Lighthouse, report before/after.
   Known drags to check: the bundle size, `@stomp/stompjs` and `canvg` CommonJS bailouts already
   warned about at build, and the render-blocking Google Fonts `<link>` in `index.html`.
8. **Confirm `robots.txt` blocks every staff surface.** `/aa/`, `/cepc/`, `/rbio/`, `/admin/` — verify
   against `app.routes.ts` (84 routes), not against the existing list. A staff route that is merely
   guarded is still *crawlable* as a URL.

### 2.3 Deliverable
A short SEO section in the report: baseline vs after, Lighthouse SEO + performance scores, the
rendering decision **and its infrastructure consequence**, indexable URL count, and anything needing
RBI sign-off (canonical domain, whether staff subdomains should be split off).

---

## 3. TASK 3 — SONAR / COVERITY-CLASS STATIC ANALYSIS, FIRST PASS

**Constraint from the user: no SonarQube or Coverity server on this machine — too slow.** So use
**local plugins and offline analysers**, and where a rule can only be checked by reading code, do that
and say so.

### 3.1 Measured tooling baseline — nothing is installed

| Fact | State |
|---|---|
| `npm run lint` | **Script exists but ESLint is NOT a dependency and there is NO config.** The command cannot work today. |
| `angular-eslint` | Absent. |
| TypeScript strictness | **Already good**: `strict`, `strictTemplates`, `strictInjectionParameters`, `strictInputAccessModifiers` all on. |
| Backend analysers | **None.** No SpotBugs, PMD, Checkstyle, Error Prone or JaCoCo in `cms-backend/pom.xml` or the parent `pom.xml`. |
| Backend coverage | **Unmeasured** — no JaCoCo. 65 test classes vs **540 main classes**. |
| Frontend unit tests | **1 spec file in the entire `src/`.** Effectively zero unit coverage; the real safety net is the 752 Playwright tests. |

### 3.2 Install the tooling (this is most of the value)

- **Frontend:** add `angular-eslint` + `@typescript-eslint` with a config; make `npm run lint`
  actually run. Include `eslint-plugin-sonarjs` for Sonar-equivalent rules (cognitive complexity,
  duplicate branches, identical functions) — that is the closest offline substitute for Sonar.
- **Backend:** add **SpotBugs** (+ `find-sec-bugs` for the security rules Coverity would flag),
  **PMD**, and **JaCoCo** for a coverage number. All run offline via Maven, no server.
- Wire them as Maven/npm goals and **commit the configs** so the next session and CI can reproduce it.
- Record the **baseline count** before fixing anything, so the delta is measurable rather than claimed.

### 3.3 Known issues to start from (already measured)

- **126 `: any`** annotations in `src/app` (excluding specs) — weak typing, a standard Sonar finding.
- **34 empty catch blocks / `error: () => {}`** — swallowed errors. Some are deliberate (best-effort
  teardown); many hide real failures. The known pattern from this codebase is severe: `report-builder`
  swallowed a 404 and its `canExport` then fell through to `return true`, so "no rows" read as
  "everyone may export". **Treat each one as a potential silent-permission bug, not a style nit.**
- CommonJS bailouts at build: `@stomp/stompjs`, `canvg`, `core-js`.

### 3.4 Priority order for fixes

1. **Security-class first** (what Coverity/find-sec-bugs exist for): injection, path traversal, weak
   crypto, resource leaks, missing authz. **Cross-check against the UAT blockers in section 4 —
   several are already known and must not be "fixed" cosmetically.**
2. **Bug-class**: null dereference, unreachable code, ignored return values, the swallowed errors.
3. **Code-smell class**: cognitive complexity, duplication, `any`.
4. **Coverage**: report the JaCoCo number. **Do not chase a percentage by writing assertion-free
   tests** — a test that cannot fail is worse than no test. This codebase has had tautologies purged
   once (`expect(x || true)`, status sets accepting 404 as a pass); reintroducing them is a defect.

### 3.5 Deliverable
Baseline vs after counts per tool and severity; every fix applied; every issue deliberately NOT fixed
with its reason; the committed configs; and a statement of what a real Sonar/Coverity server would
still catch that an offline pass cannot.

---

## 4. UAT BLOCKERS — REPORT AND FIX THE PROFILE ONE; DO NOT PAPER OVER THE REST

Found while auditing. **The first is severe and in scope for this batch.**

### 4.1 🔴 The `prod` safety profile NEVER ACTIVATES — citizen auth is bypassable

`deployment/openshift/deployment-backend.yaml` sets `SPRING_PROFILES_ACTIVE: "openshift"`. There is
**no `openshift` profile block** in `application.yml` and **no `application-openshift.yml`**. So every
`prod`-only override is dead code and these defaults apply in a deployed environment:

| Setting | Value in effect under `openshift` | Consequence |
|---|---|---|
| `cms.auth.otp.dev-auto-populate` | **`true`** (`application.yml:186`, `${OTP_DEV_AUTO_POPULATE:true}`) | `POST /api/v1/citizen/auth/send-otp` **returns the OTP in the response body** (`CitizenAuthController:134`, and `:221` for email). Anyone can authenticate as any mobile number. |
| `spring.jpa.hibernate.ddl-auto` | **`update`** (`application.yml:34`) | Hibernate mutates the schema at boot. `prod` sets `validate`. |

`OTP_DEV_AUTO_POPULATE` is **set nowhere** in `deployment/` or `cms-infrastructure/`.

**Required:** make the deployed profile safe — either add an `openshift` profile block carrying the
`prod` overrides, set the env vars explicitly in the ConfigMap, or switch the deployment to `prod`.
Whichever route, **prove it**: add a test or startup assertion that `dev-auto-populate` is false
whenever the active profile is not `dev-local`. A config comment is not a control.

⚠️ **`ddl-auto` and the migrations are coupled.** Clean-DB replay applies only **15 of 60** migrations
and creates **55 of 113** tables; 58 tables exist *only* because Hibernate created them
(`V1`/`V3` contain Oracle `CREATE SEQUENCE … NOCACHE`, invalid in MySQL; `V5`/`V6` are not
re-runnable). So switching to `validate` **will fail to boot** until the migrations are fixed.
**Do not switch `ddl-auto` without fixing the migrations, and do not fix one and report the other as
done.** If both cannot be done here, fix the OTP exposure — which is independent — and escalate the
schema work with this coupling spelled out.

### 4.2 Also report (do not silently change)
- `KEYCLOAK_ADMIN_PASSWORD` defaults to **`admin`** (`application.yml:79`).
- CORS defaults to **localhost origins only** (`application.yml:165`) — a real UAT host breaks unless
  `CMS_CORS_ORIGINS` is set. Note that under `dev-local` these values are silently ignored.
- **25 `permitAll` matchers** in `SecurityConfig`. Re-audit before public exposure; adding
  `/api/v1/config/upload-limits` as GET-only was deliberate, and that GET-only pattern is the one to
  follow — `/api/v1/masters/**` once accepted anonymous writes.
- Demo-data honesty, to restate in the report: RBI master data was never supplied; the DSC is a
  deliberate mock whose citizen-facing wording must keep saying **"NOT signed"**; SMS writes an outbox
  row and sends nothing; Kafka is absent locally so `complaint.ingested` publishes fail by design.

---

## 5. ENVIRONMENT (verified 2026-09-22)

- Backend **8092**: `./deployment/run-test-backend.sh 8092 --restart`. **Two traps, both hit:**
  - `--restart` once stopped the old JVM then reported *"already listening … reusing it"* against the
    dying process, leaving **nothing running**. Confirm with `netstat -ano | grep 8092` **and**
    `curl /actuator/health`.
  - A **stale JVM is indistinguishable from a missing endpoint**. `target/classes` was once 6 days
    older than 270 source files; a new endpoint 404s until a real rebuild.
- **Translation bundles are cached per locale.** After a seeder change, a locale can serve a stale
  bundle (`pa` returned 1888 of 1915 keys). Restart, then re-check — do not conclude the seeder failed.
- Dev server **4202**; Keycloak **9090** (realm `cms`); MySQL `cms_db`
  (`/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe`, `cms_user/cms_pass`; `root/root` for DDL).
- **Never touch 8082 / 4200 (the user's) or 8096 / 8098 (other sessions').**
- **Run Playwright from `cms-portal-frontend`** or the chromium project is not found. A `cd` for a
  backend restart is enough to break it.
- Set **BOTH** `UI_BASE_URL` and `APP_BASE_URL` — the config reads the former, 10 public specs read
  the latter. Setting one caused 47 phantom `ERR_CONNECTION_REFUSED` failures.
- **Never restart the backend while a suite is running** — it voided a whole baseline once.
- When querying the DB, **filter on `is_active`/status** where the table has one. Counting all rows
  once produced a false "the FAQ fix did not work" conclusion.
- Shell-quote `LIKE 'ui.%'` patterns carefully; a mangled pattern once reported 0 seeded keys that
  existed. Prefer `REGEXP '^ui\\.'`.

---

## 6. HOW TO WORK

- **Do not `git commit` or `git push`.** The user reviews the diff, then pushes. A hook enforces this.
- ⚠️ **`development` is AHEAD of `CMS_21092026`.** It carries 4 commits from Raghavendra H and
  Swati Ramgire (17-18h old) including real security fixes — opaque error messages (CWE-209) and
  phone-based ownership validation on `/withdraw`. **A force-push would destroy them.** If branches
  must be reconciled, **merge**; flag it to the user rather than deciding.
- Work in **`CMS2.0_Redzone/`**, never `CMS2.0/`. `git status` shows **~2076 phantom deletions** of
  `CMS2.0/` — **expected; never commit them, never `git clean`.** 454 files exist only there.
  **Stage by name; never `git add -A`.**
- Other sessions' uncommitted work may be present. `git status --porcelain` first; **never revert or
  tidy files you did not touch.**
- Chokepoints, one editor at a time: `src/styles.scss`, `src/_primitives.scss`, `app.routes.ts`,
  `e2e/utils/test-data.ts`, anything under `shared/`, `index.html`.
- Seeders: **never edit another session's.** `@Order` 3-22, 32-34, 38, 42-44, **60, 61, 62** are
  taken — take **63+** and verify. Seeding is insert-if-absent by key code, so a duplicate silently
  keeps the FIRST text, and **correcting seeded text always needs a migration UPDATE**.
- Edit existing files; no parallel "v2" components, no backwards-compat shims — change the callers.
- Default to **no code comments**; add one only where the WHY is non-obvious.
- **When a test catches your regression, fix the code, not the test.** Pass 2 reworded three headers
  and downgraded a cell; existing tests caught it and the code was fixed. Conversely, when a test
  asserts a value the user has now overruled, update the assertion **and say so in the report**.
- **A flaky test is worse than no test.** One pass-2 spec raced a re-render; it was made to wait on
  observable state, then verified over three consecutive runs. Do that.

---

## 7. DEFINITION OF DONE

**Task 1**
- [ ] Named before/after screenshots for every screen at 1280 and 1440, plus one `pa` grid.
- [ ] `D:\screenShots\ui-homog-pass3\index.html`, self-contained, with a **screenshot coverage table
      listing every gap and its reason**.
- [ ] `block_sub_judice` armed, override ground persisted, configurable, tested, both dialects.
- [ ] Category tables resolved; citizen form off the polluted table; probe rows purged; keys added;
      retirement of `category_master` proposed not executed.
- [ ] **Zero** surviving 2 MB limits: 3 client gates, `application-dev-local.yml`, 2 seeded keys in
      10 locales; microservice limits reconciled or documented; verified by direct 4 MB / 6 MB upload.
- [ ] Shared grid gains server-side paging + empty-state slot; re-portal/aa/rbio migrated **or** a
      documented reason why not.
- [ ] Shared detail frame used by all 6 detail views; `app-shell` on every staff screen (from 3/48).
- [ ] Dead `computed()` filters fixed in the 4 remaining components, proven by typing.
- [ ] E2E no worse than §1.1 per directory, **skips reported separately**; backend **1310/0**;
      `ng build` clean.

**Task 2**
- [ ] Rendering strategy decided, justified, and its infrastructure impact stated.
- [ ] Per-route titles/meta/canonical/OG from translation keys; `hreflang` + `<html lang>` for 11 locales.
- [ ] `sitemap.xml` generated from the route table, all public routes × locales.
- [ ] JSON-LD: GovernmentOrganization, WebSite+SearchAction, FAQPage (18 active rows), BreadcrumbList.
- [ ] One `<h1>` per page; heading order correct; alt text audited.
- [ ] Lighthouse SEO + Core Web Vitals before/after.
- [ ] `robots.txt` verified against all 84 routes to block every staff surface.

**Task 3**
- [ ] ESLint + angular-eslint + sonarjs installed and `npm run lint` actually works.
- [ ] SpotBugs + find-sec-bugs + PMD + JaCoCo wired into Maven; configs committed.
- [ ] Baseline counts recorded, then security → bug → smell fixes applied.
- [ ] Coverage reported honestly; **no assertion-free tests written to inflate it**.
- [ ] Every issue left unfixed listed with a reason, plus what a real server would still catch.

**Cross-cutting**
- [ ] §4.1 profile/OTP exposure fixed and **proven by a test**, with the `ddl-auto`/migration coupling
      spelled out.
- [ ] §4.2 items reported, not silently changed.

---

## 8. STOP AND ASK RATHER THAN GUESS

Section 0 is settled — do not re-ask. Ask only for:
1. **SSR vs prerender**, if SSR would require changing the nginx-based OpenShift deployment.
2. **The canonical public domain** and whether staff modules should move to a separate host — the SEO
   work and the internet/internal separation both depend on it, and `robots.txt` currently asserts
   `https://cms.rbi.org.in`.
3. **Dropping `category_master`** — propose, never execute.
4. Any point where making the UI uniform would require a **functional or workflow change**.
5. Any static-analysis "fix" that would alter **workflow, authorization or statutory behaviour** — a
   Sonar rule is never a reason to change what the law-facing code does.
