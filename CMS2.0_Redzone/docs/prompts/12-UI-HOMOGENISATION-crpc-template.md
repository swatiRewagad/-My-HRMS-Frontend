# BATCH: UI HOMOGENISATION + FULL FRONTEND VERIFICATION + CLOSE THE OPEN DECISIONS. Run alone.

Two jobs, in this order:
**(A)** Make the whole frontend look and behave like ONE product — CRPC's *visual* design applied to
RBIO, AA, RE and CEPC, with AA's *localisation* standard applied everywhere.
**(B)** Prove it still works with Playwright, deliver an HTML report with screenshots, and land the
outstanding product decisions listed in item 13 so this becomes a genuinely working final copy.

You are NOT adding features. You ARE fixing the specific, already-decided gaps in item 13.

Items 0-14 are ordered deliberately. Item 0 is measured fact that contradicts the obvious reading of
this task — read it before planning. Everything was measured 2026-09-21/22 at HEAD `b46bc5d`.
**Re-verify before relying on any of it; this file ages.**

---

## 0. MEASURED FACTS — READ BEFORE PLANNING

### 0.1 ONE app, not a micro-frontend. Work ONLY in `cms-portal-frontend`.
There is **no Module Federation, native-federation or single-spa anywhere** — no `remoteEntry`, no
federation config. Every module is a lazy-loaded route group (`loadComponent()`) in a single
`app.routes.ts` (**84 routes**). So one `styles.scss`, one `shared/`, one build: a design-system change
is a one-place change.

`CMS2.0_Redzone/cms-frontend/` is **DEAD CODE — ignore it entirely.** It has 5 routes, no Keycloak, no
feature commit in its history (its only commit is the bulk import `f32a368`), and its newest source
file is 2026-09-10, versus `cms-portal-frontend` active to 2026-09-21. **Do not edit, restyle, test,
build or delete it.** Deleting it is a separate decision (its OpenShift route
`deployment/openshift/deployment-frontend.yaml` points at host `cms-staff.apps...`, which looks
mis-wired given the app is public-only — flag it in the report, change nothing).

Work in `CMS2.0_Redzone/` and never `CMS2.0/` (see `reference_cms2_redzone_repo_layout` — 2076 phantom
deletions are EXPECTED; never commit them, never `git clean`).

### 0.2 There is no design system. You must create it.
`src/styles.scss` is **105 lines with ZERO CSS custom properties**. **343 distinct hardcoded hex
colours** across `src/app/components/**`:

| module | distinct | occurrences | character |
|---|---|---|---|
| crpc | 103 | 1347 | Tailwind slate/blue — **THE REFERENCE** |
| rbio | 134 | 960 | already close to CRPC → migrate first |
| aa | 94 | 384 | **Material Indigo `#1a237e`** → biggest visual change |
| re-portal | 42 | 217 | red/blue mix (`#fef2f2`, `#1565c0`) |
| cepc | 69 | 307 | a third palette |

CRPC palette by frequency — the token set to extract: `#e2e8f0`(157 borders) `#3b82f6`(138 primary)
`#1e293b`(111 heading) `#fff`(110) `#64748b`(110 muted) `#94a3b8`(82) `#475569`(55) `#f1f5f9`(51
subtle bg) `#f8fafc`(40 page bg) `#f59e0b`(22 warning) `#eff6ff`(21) `#fef3c7`(20) `#92400e`(19)
`#334155`(18) `#dbeafe`(17) `#d1fae5`(17 success bg) `#1e40af`(17) `#065f46`(17 success fg).

### 0.3 COPYING CRPC's i18n WOULD DESTROY LOCALISATION. CRPC is the visual reference ONLY.
CRPC has **NO internationalisation**: `| translate` appears **0 times** in `crpc/**` (~389 hardcoded
strings: `>RESERVE BANK OF INDIA<`, `>Dashboard<`, `>Advanced Search<`…).

| module | `\| translate` | hardcoded candidates |
|---|---|---|
| **aa** | **344** | 30 |
| rbio | 176 | 283 |
| re-portal | 72 | 58 |
| crpc | **0** | 389 |
| cepc | **0** | 107 |

**AA is the localisation reference; CRPC is the visual reference. Different modules.** Homogenising
i18n means levelling CRPC and CEPC **UP** to AA. Any diff that reduces the repo-wide `| translate`
count is a defect in your own work.

### 0.4 Staff cannot change language at all.
`shared/language-switcher/` is referenced by **ZERO** templates outside its own folder
(`app-language-switcher` usage: 0). Every staff module is English-only regardless of what is seeded.
Wiring this into the common shell is probably the highest-value user-visible change here.

### 0.5 Only ONE module has a layout shell.
`re-portal/re-layout/` is the only `*layout*` component; crpc, rbio, aa, cepc repeat
header/sidebar/nav markup in every page. CRPC's class vocabulary (from `crpc/deo-home/…html`, 432
lines) is the de-facto template: `stat-card / stat-content / stat-value / stat-label`, `tab`,
`search-field`, `btn btn-outline`, `modal-overlay / modal-title`, `page-btn`, PrimeIcons (`pi pi-*`).

### 0.6 Already shared — extend, do not fork.
`shared/status-badge` (adopted by 10 templates across aa, cepc, rbio, re-portal, public,
complaint-tracker), `shared/internal-notes`, `shared/query-thread`, `shared/language-switcher`
(orphaned — 0.4).

### 0.7 Notifications are ad-hoc.
No toast/snackbar service; 6 components hand-roll it; **4 raw `alert()` calls** (which also HANG
Playwright unless dismissed).

### 0.8 Public and staff routes live in the SAME app.
`cms-portal-frontend` serves `public`, `track`, `file-complaint`, `eligibility-wizard`,
`public/upload/:token` **alongside** all staff modules. 49 `canActivate` guards exist and are real
(17 `staffAuthGuard`, 7 `publicAuthGuard`, plus per-role) but **a route guard is a client-side
control** — it hides UI, it does not stop staff-module JS being downloaded from the internet. The user
wants internet/internal network separation eventually. **DO NOT split the app in this batch.** Instead,
build the token layer and `shared/` so they are **extractable into a shared library later** (a
`cms-portal-ui-contracts` package already exists) — otherwise a future split creates two diverging
design systems. Note any coupling that would make the split harder.

### 0.9 Inventory: crpc 11, rbio 16, aa 8, re-portal 9, cepc 6 components.

---

## 1. SCOPE

**In:** colour/spacing/typography tokens; common shell (header, sidebar, breadcrumb, page title);
tables, grids, pagination, filters, tabs, cards, modals, buttons, form controls, empty/loading/error
states; status badges; notifications/toasts; icons; focus/hover states; the language switcher;
translating every string you touch. **CEPC is IN** (same problem, shares `status-badge`).

**Out:** workflow logic, API contracts, role permissions, new features. **Do not fix a functional bug
you stumble on — record it** (except the item-13 decisions, which ARE in scope). If a visual change is
impossible without a functional change, stop and report rather than guess.

---

## 2. BUILD THE DESIGN SYSTEM FIRST

Do not hand-edit 343 colours across ~50 components; that is unreviewable and will regress.

1. **Extract CRPC's tokens** into `:root` custom properties in `src/styles.scss` with **semantic**
   names (`--surface-page`, `--surface-card`, `--border-subtle`, `--text-heading`, `--text-muted`,
   `--brand-primary`, `--state-warning-bg`, `--state-success-fg`…). Include spacing, radius, shadow and
   font scales taken from CRPC's real values, not invented ones.
2. **Build shared primitives** under `shared/` — app shell + card/table/toolbar/modal/button — modelled
   on CRPC's markup and class names (0.5) so CRPC itself changes least. Keep them library-extractable
   (0.8): no deep imports into module code, no module-specific logic.
3. **Migrate in ascending risk: rbio → cepc → re-portal → aa** (AA last: Material Indigo, ~65 uses of
   `#1a237e`, and it is the best-localised module so its markup is the most dangerous to churn).
4. **Produce a mapping table**: every CRPC hex → token → what replaced it, so a reviewer can check any
   colour in seconds.

**Gate after EACH module:** `npx ng build` clean AND that module's specs no worse than the item-7
baseline. Do not start the next module until the current one is green.

---

## 3. LOCALISATION — LEVEL UP, NEVER DOWN

**Hard gate: the repo-wide `| translate` count must INCREASE.** Bring CRPC and CEPC up to AA's standard.

- Every string you touch gets a key: `>Dashboard<` → `{{ 'ui.crpc.dashboard' | translate }}`.
- **Never interpolate a label map without the translate pipe** — that renders a raw key to staff, a
  defect that already exists here (FAQ rows 1-10 do exactly this).
- Seeder convention (`AaRegisterTranslationSeeder.java:18`): **ONE NEW seeder class; never edit
  another session's.** `@Order` values 3-21 are in use with collisions already at 32/42-44 — take
  **@Order(60+)** and verify nothing claims it.
- Keys are idempotent by `existsByCode`, so **a duplicate code silently keeps the FIRST text.** Use a
  distinct prefix (`ui.shell.*`, `ui.crpc.*`) and grep before adding.
- **Wire `shared/language-switcher` into the shell** (0.4) and prove by test that switching locale
  changes rendered staff text.
- **Current state: `pa` (Punjabi) is 100% identical to English (1806/1806 keys); the other eight are
  14-21% English passthrough.** Do not make it worse. For new keys either supply a real translation or
  leave English and **list it in the report as needing translation**. Never machine-translate legal or
  clause text.
- **`scripts/qa/i18n_check.py` is necessary but NOT sufficient:** it defaults to **port 8096**
  (another session's backend — always pass `--base`) and `--scan-components` covers only **296 of
  1806** keys, so it returns PASS while Punjabi is entirely untranslated. Run it, then separately
  assert your new keys differ from English across all ten locales.
- Bengali stores digits as Bengali numerals — an ASCII-digit check silently skips it.

---

## 4. NOTIFICATIONS

One mechanism app-wide: success/info/warning/error with consistent placement, timing, icon, token
colour and dismissal. Replace the **4 raw `alert()` calls** and reconcile the 6 hand-rolled
implementations (0.7). Messages translated; no raw server text or PII in the UI; keep
`role="status"`/`aria-live` so screen readers and tests can observe them.

---

## 5. ACCESSIBILITY AND RESPONSIVENESS

Do not regress: visible keyboard focus on every interactive element (a token, never `outline: none`),
AA contrast on new surfaces, `<th scope>` on data tables, labels tied to inputs, Escape/focus-trap on
modals. Check each module at 1280px and 1440px; nothing clipped or horizontally scrolling at 1024px.

---

## 6. ENVIRONMENT (verified; confirm before use)

- Backend **8092** is Claude's: `./deployment/run-test-backend.sh 8092 --restart`. **Its readiness poll
  can FALSE-NEGATIVE** — it greps a log that may hold the previous run's `BUILD FAILURE` and prints
  "failed to start" on a healthy app. Confirm with `netstat` + `curl`.
- **A stale JVM is indistinguishable from a missing endpoint.** Compare process start time against
  `cms-backend/target/classes` mtimes; four endpoints 404'd on 2026-09-21 purely from a 2-day-old JVM.
- Dev server **4202**; Keycloak **9090** (realm `cms`); MySQL `cms_db`
  (`/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe`, `cms_user/cms_pass`; `root/root` for DDL).
- 4202 is already CORS-allowed and a Keycloak redirect URI. Verify → **200**:
  `curl -s -o /dev/null -w "%{http_code}" -X OPTIONS http://localhost:8092/api/v1/i18n/translations/en -H "Origin: http://localhost:4202" -H "Access-Control-Request-Method: GET"`
  Under `dev-local`, `CMS_CORS_ORIGINS` and `-Dcms.cors.allowed-origins` are **silently ignored**.
- **Never touch 8082 / 4200 (the user's) or 8096 / 8098 (other sessions').**
- Schema is Hibernate `ddl-auto`, not Flyway. New migrations: guard DDL on `information_schema` (MySQL
  8.4 has no `ADD COLUMN IF NOT EXISTS`) and add the Oracle pair under `database/oracle/`.
  **Free V-numbers: V59-70, V77-80, V88-90, V93, V94.**

---

## 7. FUNCTIONAL BASELINE YOU MUST NOT REGRESS

Measured 2026-09-21 on a fresh backend. **Re-measure BEFORE changing anything** — a regression only
means something against a baseline you took yourself.

| dir | passed | failed | skipped |
|---|---|---|---|
| admin | 106 | 0 | 9 |
| public | 107 | 4 | 0 |
| i18n | 8 | 1 | 0 |
| aa | 153 | 25 | 23 |
| cepc | 28 | 1 | 6 |
| rbio | 184 | 1 | 11 |
| re-portal | 36 | 1 | 33 |

**Totals 622 passed / 33 failed / 82 skipped** over 70 spec files / 752 tests.
Backend suite baseline: **1310 tests, 0 failures, 0 skipped** — a UI batch must not move it. Note
`mvn -q test` SUPPRESSES the summary; aggregate `target/surefire-reports/*.xml`.

**Known pre-existing failures — report as pre-existing:**
- The **25 `aa` failures are ONE cause**: unauthenticated browser context, so `/aa/file-appeal`
  redirects to `/` and `/aa/dashboard` to `/staff/login`, and `.form-input`/`.status-badge` genuinely
  do not exist. **Fixing this harness gap is worth doing** — it unlocks real AA UI coverage for exactly
  the module you are restyling most heavily. Use `loginAsAaRole` from `e2e/utils/auth.ts`.
- The **4 `public` failures are real defects**, both i18n-adjacent, so your work may fix them:
  `eligibility.block_not_filed` holds text with clause `10(1)(j)` baked in where the seeder expects a
  `{{clause}}` placeholder (the DB row predates the seeder; `seedIfAbsent` can never correct it), and
  FAQ rows 1-10 render raw translation keys. If you fix them, say so; if not, leave them.

Run per directory (a single full-suite run buffers indefinitely through `tail`), from
`cms-portal-frontend` or the chromium project is not found:

```bash
for d in admin public i18n aa cepc rbio re-portal; do \
  PW_SCREENSHOT=on PW_OUTPUT_DIR=/d/screenShots/ui-homog/$d \
  API_BASE_URL=http://localhost:8092 UI_BASE_URL=http://localhost:4202 APP_BASE_URL=http://localhost:4202 \
  npx playwright test "e2e/$d" --project=chromium --reporter=line > /c/tmp/ui_$d.txt 2>&1; done
```

**Harness traps that yield green-but-meaningless results:**
- **Set BOTH `UI_BASE_URL` AND `APP_BASE_URL`.** The config reads `UI_BASE_URL`; 10 specs in
  `e2e/public/` read `APP_BASE_URL`. Setting one caused **47 phantom `ERR_CONNECTION_REFUSED`
  failures** (51 → 4 once fixed). `e2e/rbio/harness.ts:35` hardcodes origin `:4200` **deliberately**
  to assert CORS — leave it.
- `auth.ts:isKeycloakAvailable()` returns false on ANY throw and callers `test.skip()`, so a Keycloak
  outage yields a **GREEN VACUOUS** staff suite. **Report skips separately — a skip is not a pass.**
  re-portal already skips 33 of 70.
- A spec importing `test` from `@playwright/test` instead of `../fixtures` reads the user's 8082.
- **No tautologies**: `expect(x || true)`, `if (!('error' in result))`, status sets accepting 404 as a
  pass. These were purged once; reintroducing them is worse than no test.
- Never `--headed` for bulk runs; `workers: 1` is intentional.

**Add UI-specific specs** in a NEW `e2e/ui-homogenisation/` (do not edit other sessions' specs): shell
renders on every module route; tokens truly applied (assert *computed* styles resolve to the same value
in crpc/rbio/aa/re-portal/cepc); **no raw key matching `/^[a-z]+(\.[a-z_]+)+$/` visible anywhere**;
language switcher changes rendered text; toast appears with correct role and dismisses; focus visible.
**Drive filters and search boxes by TYPING, not by reading code** — a `computed()` reading a plain
class field registers no dependency and silently does nothing. Known live instances to verify and not
replicate: `cepc-dashboard` + `crpc/deo-home` `columnSearchText`, `report-builder` `filterSearch`,
`complaint-history` `filters`, `task-action` `pastComplaintSearch`.

---

## 8. SCREENSHOTS AND THE HTML REPORT (required deliverable)

`PW_SCREENSHOT=on` and `PW_OUTPUT_DIR=<dir>` are already wired in `playwright.config.ts`. Write to
`D:\screenShots\ui-homog\<module>\`.

**Know the ceiling: only 41 of 70 specs (354 of 752 tests) ever touch `page.`. The other 29 specs (398
tests) are pure API tests and CANNOT yield a screenshot** — `e2e/admin/` produced 0 PNGs from 106
passing tests. State this plainly rather than implying every test has an image.

**Capture BEFORE images first — once tokens land they are unrecoverable.**

Deliver a **self-contained** `D:\screenShots\ui-homog\index.html` (embedded CSS, no CDN, **relative**
image paths so the folder can be zipped) containing:
1. Verdict + per-module pass/fail/**skip** vs the item-7 baseline, regressions called out.
2. **Before/after screenshots side by side**, per module and key screen.
3. The colour→token mapping table + residual un-tokenised hex count per module.
4. Localisation delta: `| translate` per module before/after, new keys, and **which keys still need
   real non-English translations**.
5. Accessibility results (contrast, focus, table semantics).
6. Functional regressions: pre-existing vs newly introduced, using item 7.
7. Defects found but deliberately NOT fixed.
8. **Item-13 decision outcomes** — what was implemented, what is still config-OFF, what needs sign-off.
If you generate it with a script, commit the script.

---

## 9. HOW TO WORK

- **Do not `git commit` or `git push`.** The user reviews the diff. (Standing rule.)
- `git status` shows ~2076 phantom deletions of `CMS2.0/` — **expected; never commit, never `git clean`**
  (454 files incl. cms-mail-intake and the assignment rules engine exist only there).
- The tree may hold other sessions' uncommitted work (17 backend files did on 2026-09-21).
  `git status --porcelain` first; **never revert or tidy files you did not touch.**
- Chokepoints, one editor at a time: `src/styles.scss`, `app.routes.ts`, `e2e/utils/test-data.ts`,
  anything under `shared/`.
- Edit existing files; no parallel "v2" components, no backwards-compat shims — change the callers.
- Default to **no code comments**; add one only where the WHY is non-obvious.

---

## 10. STOP AND ASK RATHER THAN GUESS

1. **Is CRPC's slate/blue the intended RBI brand palette**, or is there an RBI brand guideline not in
   the repo? Everything downstream depends on it — a brand guide would override CRPC.
2. **AA restyle:** flatten Material Indigo to CRPC slate/blue, or keep an AA accent colour?
3. Is one shared shell acceptable when each module has a different sidebar/nav today?
4. Do new UI strings need professional translation for the nine non-English locales?

---

## 11. DEFINITION OF DONE

- [ ] `:root` tokens extracted from CRPC; `styles.scss` is the single source of colour/spacing.
- [ ] Shared shell + primitives under `shared/`, used by crpc, rbio, aa, re-portal, cepc; extractable.
- [ ] Residual hardcoded hexes reported per module with a justification for each one left.
- [ ] Repo-wide `| translate` **increased**; CRPC and CEPC localised; no raw key renders; one new
      seeder at `@Order(60+)`.
- [ ] Language switcher in the shell, proven by test to change staff-facing text.
- [ ] One notification mechanism; **zero** raw `alert()`.
- [ ] `npx ng build` clean; backend suite still **1310/0**.
- [ ] E2E no worse than item 7 per directory, **skips reported separately**.
- [ ] AA auth-harness gap fixed (or a documented reason it was not).
- [ ] Before/after screenshots for every migrated screen.
- [ ] `D:\screenShots\ui-homog\index.html` with all eight sections.
- [ ] Item-13 decisions implemented or escalated with the blocker named.

---

## 12. FINAL REPORT (in chat, not only the HTML)

Lead with the **verdict**. Then: changes per module; token mapping summary; localisation delta and what
still needs human translation; functional regressions (pre-existing vs new, per item 7); defects found
but not fixed; every assumption; answers to item 10; item-13 outcomes. Close with a prioritised
follow-up list splitting **engineering** from **design sign-off** from **legal sign-off**.

---

## 13. OPEN DECISIONS TO CLOSE — verify each is still open, then implement

These were ruled on by the user (mostly 2026-09-18 Stage 0) but **verified STILL UNIMPLEMENTED on
2026-09-22**. They are the gap between "demo" and "working final copy". Confirm each against current
code before acting; if one is already done, say so and move on.

**13.1 File size — ALREADY DECIDED, NEVER IMPLEMENTED. Highest priority; it is also a UI bug.**
User ruling: **5 MB per file, 25 MB total per complaint, both configurable.** Actual state: still
hardcoded 2 MB in `FileStorageConfig.maxFileSize` and `application.yml:126`; `maxFileSizeMB: 2` in all
three `cms-portal-frontend/src/environments/*.ts`; **no file-size row exists in `system_config` at
all**; and the citizen-facing hint (`eligibility.upload_hint`, seeded in all 10 locales) says **5MB**,
so the UI already contradicts enforcement. Spring's 50 MB servlet cap is an outer bound, not the
product rule. Move to `system_config`, set 5 MB / 25 MB, make the frontend read it rather than a
compiled constant, and align the hint text. Verify by direct API upload, not just the UI.

**13.2 `"Clause FRC"` placeholder is live in citizen-facing text.**
`AutoClosureService.java:122` still emits the literal `"Clause FRC"`, which is not a real clause
number. **Needs the authoritative clause from the user — never invent one.** If unanswered, leave it
and list it; do not guess.

**13.3 Compensation caps are ARMED without sign-off, and there is a gazette discrepancy.**
`cms.rbio.compensation.max_consequential_loss=3000000`, `max_time_harassment=300000`,
`max_combined=3000000` are live values in `system_config`. The AA guards correctly follow the
config-OFF precedent (`cms.aa.order.max_award_amount=0`, `cms.aa.order.block_sub_judice=false`).
**The gazette says the ₹3 lakh is "in addition", implying a ₹33 lakh combined ceiling; the user ruled
2026-09-18 to keep the combined default at ₹30 lakh and FLAG it for legal.** Do not silently change it.
Also: `RulesApiController:82-88` hardcodes ₹30L/₹3L in DRL strings — display-only (no Drools session),
so it is a UI/enforcement divergence to reconcile, not a second enforcement path.

**13.4 Thirteen `clause.*` labels exist in English only.**
13 keys are seeded; **14 of 24 clause-related keys are untranslated even in Hindi, 24/24 in Punjabi.**
**A translated clause label is a legal statement about what that clause means** — it needs sign-off,
not machine translation. Surface the list; translate nothing legal without an answer.

**13.5 4604 of 5236 closed complaints have no closure clause and are therefore unappealable.**
Column is **`closure_clause`** (there is no `closure_clause_code` — querying it errors 1054). Needs a
user/legal decision: make a clause mandatory at closure, and back-fill or formally treat the legacy
rows as unappealable. **Out of scope to fix here — confirm the number and report it.**

**13.6 Migrations cannot build the schema (release blocker for any `ddl-auto: off` deploy).**
Clean-DB replay: **15 of 60 applied; 55 of 113 tables created.** 58 tables exist only because
Hibernate made them. `V1__initial_schema.sql` and `V3__mre_rules_tables.sql` contain **Oracle
`CREATE SEQUENCE … NOCACHE`**, invalid in MySQL. `V5`/`V6` are **not re-runnable** (duplicate-key on
unguarded seed INSERTs). See `reference_cms2_migration_replay_proof` for the exact replay recipe.
**Not a UI task — report it; do not attempt it inside this batch.**

**13.7 Thirteen orphan status strings on 372 live complaints** are absent from `RBIO_STATUS_MASTER`
(`forwarded` 150, `pending` 74, `awaiting_details` 59, `reviewer_review` 41, `adjudicated` 19,
`re_responded` 17, `returned` 7, + 6 singletons). This matters to YOU because status badges are being
homogenised: **an unmapped status must degrade gracefully, not render blank or raw.** Make the badge
robust; propose the vocabulary fix in the report.

**13.8 A fifth hardcoded `CLOSED_STATUSES` list** at
`cms-workflow-service/.../OfficerDeactivationService.java:36` (added by `f686180`), outside the four
that were consolidated onto `RbioStatusVocabulary`. Report it; fixing it is backend work.

**Ask the user up front** (do not block the whole batch waiting): 13.2's real clause; whether 13.3's
combined ceiling is ₹30L or ₹33L; whether 13.4's clause labels may be translated and by whom; and
13.5's mandatory-clause policy. **13.1 needs no answer — it is already decided; just implement it.**

---

## 14. "WORKING FINAL COPY" — WHAT THAT MEANS HERE

At the end, someone must be able to start the stack and use every module with a consistent UI in ten
languages. Deliver, in the report:
- Exact start commands for backend, frontend and Keycloak, and the working credentials (per
  `reference_cms2_keycloak_realm_state` — verify they still work; `cepc_do1/password` and
  `rbio.officer/Test@1234` were real as of 2026-09-18).
- A per-module smoke walkthrough a human can repeat in ten minutes.
- An explicit **GO / CONDITIONAL-NO-GO** for the frontend, with conditions named and split into
  engineering / design sign-off / legal sign-off.
- Honesty about what is demo data (RBI master data was never supplied; the DSC is a deliberate mock
  and its citizen-facing wording must keep saying "NOT signed"; SMS writes an outbox row and sends
  nothing).
