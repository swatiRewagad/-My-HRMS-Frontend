# QA PASS 4 — SESSION B: Contact / address fields + RE-details cascading dropdowns + complaint core fields

You are automating manual QA test cases into Playwright E2E specs for the CMS 2.0 citizen portal
(RBI complaint management, Angular frontend + Spring Boot backend).

**You are ONE OF THREE sessions running concurrently tonight.** Sessions A and C are working the same
repo at the same time. Everything in the "SHARED INFRASTRUCTURE" and "COLLISION RULES" sections is
there to stop you destroying their work or them destroying yours. Read those sections before you
write a single line of code.

Working directory for every command in this brief:
`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\cms-portal-frontend`

---

## 0. YOUR SCOPE — 156 manual test cases (before collapsing the duplicates noted below)

Source file: `C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\prompt_testcase_cms_frontemd.txt`
(4266 lines, tab-separated: Description / Steps / Expected Result, with wrapped continuation lines).
It is 260KB — it EXCEEDS the Read tool's single-read limit, so read it with `offset`/`limit` in chunks.

**Read ONLY your line ranges. Do not read or automate anything outside them — A and C own the rest.**

| Block | Lines | Cases | Subject |
|---|---|---|---|
| 17 | 964-1081 | 16 | Complainant: Organization Landline Number (10 digits) |
| 18 | 1082-1164 | 14 | Complainant: Email Id (64 char, format validation) |
| 19 | 1165-1232 | 7 | Complainant: State of Residence dropdown |
| 20 | 1233-1305 | 8 | Complainant: District of Residence dropdown (**cascades from State**) |
| 21 | 1306-1371 | 10 | Complainant: Address (100 char) |
| 22 | 1372-1475 | 19 | Complainant: Pincode searchable dropdown (6 digits, typeahead) |
| 1 | 1-33 | 4 | Select Regulated Entity: dropdown behaviour (change selection, scrollbar, arrow-key select, no multi-select) |
| 23 | 1476-1556 | 3 | RE Details: "Is complaint related to credit card?" radio |
| 24 | 1557-1657 | 9 | RE Details: Entity State dropdown (conditional on credit-card = No) |
| 26 | 1679-1784 | 10 | RE Details: Entity District dropdown (**cascades from Entity State**) |
| 27 | 1785-1891 | 10 | RE Details: Entity Branch dropdown (**cascades from State + District**) |
| 28 | 1892-1992 | 8 | Complaint Details: Complaint Category dropdown (10 values) |
| 29 | 1993-2078 | 11 | Complaint Details: Facts of the Complaint text field (5000 char) |
| 30 | 2079-2151 | 6 | Complaint Details: Date of Disputed Transaction (no future dates) |
| 31 | 2152-2213 | 5 | Complaint Details: "Do you have an account with RE?" radio |
| 32 | 2214-2306 | 9 | Complaint Details: Type of Account with RE dropdown (**multi-select IS allowed here**) |
| 48 | 3578-3613 | 5 | Tooltips across all forms (FR-G-009) |
| 53 | 3853-3865 | 2 | Facts of complaint: speech-to-text **field presence only** (see exclusions) |

### Known duplication inside your scope — do not write it twice
- **Blocks 23, 24, 26, 27 share 11 identical preamble rows.** Block 23 (1476-1556) is *entirely
  contained* in the other three and contributes only 3 net-unique cases. Fold it into your Entity
  State spec rather than giving it its own file.
- **Block 31 (2152-2213) is replayed verbatim as the preamble to block 32** (2214-2306); 5 rows are
  identical. Write the radio cases once.

### Not automatable — deferred bucket, do NOT burn hours
- `3866-3910` (speech-to-text: mic capture, real dictation, English-only dictation, live
  start/pause/stop transcription). The Web Speech API cannot be driven headlessly without fake-audio
  plumbing. **Session C is NOT covering this either — it is deliberately deferred.** You automate
  ONLY lines 3853-3865: the field exists, the mic control exists, is focusable, has an accessible
  label, and typed text still works.
- `3596-3613` — the tooltip cases "consistency maintained across the portal" and "displayed for all
  forms defined in FR-G-009" are open-ended surveys, not deterministic specs. Automate the specific,
  checkable ones (a named field has a tooltip, it is keyboard-reachable, it has the right ARIA
  wiring); record the survey ones as needs-scoping in your findings.

---

## 1. SHARED INFRASTRUCTURE — already running, DO NOT restart, rebuild or kill it

I verified all of this myself at 17:00 on 2026-09-24, immediately before writing this brief:

| Service | Port | State |
|---|---|---|
| cms-backend (`dev-local` profile) | **8092** | UP, `/actuator/health` = 200 |
| Angular dev server | **4200** and **4202** | both UP, 200 |
| Keycloak (realm `cms`) | 9090 | UP |
| MySQL `cms_db` | 3306 | UP |
| The user's own backend 8082 | — | **DOWN. Never assume 8082.** |

**All three sessions SHARE the backend on 8092 and the dev server.** This is deliberate:
- `cms-backend/target/` is ONE directory shared by the whole repo. Three concurrent
  `mvn spring-boot:run` invocations clobber each other's class output and produce phantom compile
  errors in files you never touched.
- Your workload is UI field validation and dropdown behaviour. It barely writes to the database.

**Therefore: you must NOT run `mvn`, must NOT run `deployment/run-test-backend.sh`, must NOT run
`ng serve`, and must NOT restart anything.** If you believe the backend is broken, verify with `curl`
first (see §7) and report it rather than restarting — a restart kills sessions A and C too.

### Your session's environment — export these in EVERY shell you run tests from
```bash
export UI_BASE_URL=http://localhost:4200
export APP_BASE_URL=http://localhost:4200
export API_BASE_URL=http://localhost:8092
export PW_OUTPUT_DIR=/c/tmp/pw_B/test-results
export PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_B/report
```
All three base-URL variables matter and are read in three different places:
- `playwright.config.ts:13` reads **`UI_BASE_URL`** for `baseURL`.
- 33 specs under `e2e/public/` read **`APP_BASE_URL`**, falling back to `UI_BASE_URL` then 4200.
- `e2e/utils/test-data.ts` and the `request` fixture read **`API_BASE_URL`**, defaulting to 8082 —
  which is DOWN tonight, so forgetting it gives you a wall of connection errors, not a product bug.
Setting only one of them previously produced 47 fake failures. Export all three, every shell.

`PW_OUTPUT_DIR`/`PLAYWRIGHT_HTML_REPORT` are per-session because the default `test-results/` and
`playwright-report/` are shared and three sessions would overwrite each other's failure screenshots.

Port 4200 is yours. Do not move to a port outside the backend's CORS allowlist
(`4200,4201,4202,4300`) or every request 403s at Spring's CorsFilter.

---

## 2. COLLISION RULES — three sessions, one repo

**Create NEW spec files. Never edit a file you did not create.**

Your files live under `e2e/public/`. Suggested names — keep them distinct from everything in §3:
```
e2e/public/complainant-contact-fields.spec.ts      (blocks 17, 18)
e2e/public/complainant-address-state-district.spec.ts  (19, 20, 21)
e2e/public/complainant-pincode.spec.ts             (22)
e2e/public/re-details-cascading-dropdowns.spec.ts   (1, 23, 24, 26, 27)
e2e/public/complaint-category-facts.spec.ts        (28, 29)
e2e/public/complaint-transaction-account.spec.ts   (30, 31, 32)
e2e/public/form-tooltips.spec.ts                   (48)
```

**Chokepoint files — DO NOT EDIT. Sessions A and C need them unchanged:**
- `e2e/utils/test-data.ts` (1589 lines, every session depends on it)
- `e2e/fixtures.ts`, `e2e/utils/api-redirect.ts`, `e2e/utils/auth.ts`, `e2e/public/browser-api.ts`
- `playwright.config.ts`
- any `src/**` application code — see §5
If you need a new shared helper, put it in **your own** new file `e2e/public/helpers-forms-b.ts`
and import it only from your specs.

**Mobile numbers are shared global state.** OTP cooldown, hourly send limits and
`invalidateActiveOtps` key on the mobile number, and `cms_db` is shared. Session B owns the range
**`98765_2____`** — generate as `'987652' + 4 random digits`. Never reuse a literal mobile from an
existing spec. Call `deleteOtpAttempts(mobile)` in `afterAll`.

**SYSTEM_CONFIG rows are shared global state.** If you `setSystemConfig` to arm a scenario, restore
it in `afterAll` even on failure. A left-behind row silently breaks A and C.

---

## 3. WHAT ALREADY EXISTS — do not duplicate it

There are 101 spec files and ~1300 tests. A session before you just added ~30 more. **Before writing
any spec, run `git status --short e2e/` and `ls e2e/public/`** — more files may have appeared after
this brief was written.

Coverage most likely to overlap YOUR scope:

| Spec | Tests | Already covers |
|---|---|---|
| `public/re-entity-search.spec.ts` | **43** | **Heavy overlap with your block 1.** Entity picked from the RE list, search by name and by type, none-selected names the field, no-match message, special chars inert, clear restores, unavailable master is reported not silently empty. **Read this in full before touching block 1.** Your net-new work there is probably only: changing an already-made selection, scrollbar navigation of a long list, arrow-key + Enter selection, and no-multi-select. |
| `public/complaint-categories.spec.ts` | 7 | **Overlaps block 28.** 10 real categories each with a labelKey; resolves in 11 locales incl. `pa`; the submitted VALUE stays English while the LABEL localises. Your net-new work is the dropdown *behaviour* (opens, single-select, closes on select, cannot be blank). |
| `public/eligibility-*.spec.ts` | ~100 across 7 files | Eligibility master data, RE-first window, clause interpolation, sub-questions, simplification. **You must pass through the eligibility step to reach your screens — read `eligibility-master.spec.ts:28-33` for the canonical way in, but do not add eligibility tests.** |
| `public/filing-windows.spec.ts` | 24 | Grievance window accept/reject, 90-day-after-RE-response, same-day, earlier-than-filing. **Relevant to your block 30 (date of disputed transaction).** It deliberately pins three disagreeing window rules as-is. Check it before writing date validation. |
| `public/consent-dpdp.spec.ts` | 11 | Declaration/consent enforcement |
| `public/draft-save.spec.ts` | 4 | Draft save surfaces the real server outcome |
| `ui-homogenisation/upload-limits.spec.ts` | 7 | Source-scan: fails any file hardcoding a byte limit |

If an existing spec covers 80% of a block, create a facet-suffixed sibling and say so in your header.
Do not edit theirs.

---

## 4. THE HARNESS — verified facts, use exactly these

### Every browser spec must import from the fixture, not from Playwright
```ts
import { test, expect } from '../fixtures';   // NOT '@playwright/test'
```
`e2e/fixtures.ts:14-19` overrides `page` to rewrite the browser's hardcoded `http://localhost:8082/**`
calls to `API_BASE_URL`. The Angular bundle has 8082 compiled in. A spec importing raw Playwright will
seed on 8092 and READ from 8082 — down tonight, so you will chase a phantom. Four existing specs
(`seo`, `seo-headings`, `signal-filters`, `upload-limits`) import raw Playwright because they are
static/source-scan specs. Do not copy them.

### Getting to YOUR screens — you need to be deep in the wizard
Your fields live on **Complainant Details**, **RE Details** and **Complaint Details** — steps 3, 4
and 5 of a 7-step wizard. Re-driving login + eligibility for every test will consume your whole
night. Do this instead:

**Seed the citizen session directly — the default, used by 16 specs.** `publicAuthService` only
checks that `cms_public_session` exists in sessionStorage and is within the inactivity timeout
(`src/app/services/public-auth.service.ts:39-48`); the token signature is never validated:
```ts
await page.goto(`${APP_BASE}/public`);                    // MUST land on the app origin FIRST
await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
await page.goto(`${APP_BASE}/public/file-complaint`);      // guard now passes
await page.waitForLoadState('networkidle');
```
The ordering is load-bearing — sessionStorage is origin-scoped, so the first `goto` is mandatory.
Guarded routes: `/public/file-complaint`, `/public/withdraw`, `/public/withdraw/:id`,
`/public/feedback` (`src/app/app.routes.ts:416-419`).

**Then build ONE `beforeEach` helper in `helpers-forms-b.ts` that walks from a seeded session to the
step under test** (select RE → answer eligibility gates with the non-blocking answers → fill
Complainant Details minimally → land on RE Details / Complaint Details). Every one of your ~180 cases
reuses it. Getting this helper right in the first 30 minutes is the single highest-leverage thing you
will do tonight. Crib the entry pattern from `eligibility-master.spec.ts:28-33` and the eligibility
answering pattern from `eligibility-re-window.spec.ts`.

**If you need a REAL server-side citizen token** (for calls the backend authenticates):
`loginCitizen(request, mobile)` (`test-data.ts:138-168`) solves the MATH captcha, posts send-otp,
reads `devOtp` from the response, verifies, and returns the token; it returns `null` if `devOtp` is
not exposed. **I verified against 8092 at 17:00 today that it IS exposed:**
```
POST /api/v1/citizen/auth/send-otp → {"sessionId":"...","devOtp":"123456","expiresInSeconds":600,...}
```
MATH is the only solvable captcha type (`generateVisualCaptcha` returns `audioQuestion = null`), so
always request `type=MATH`. There is no captcha bypass; the challenge is genuinely solved.

### Cascading dropdowns — the heart of your scope
Blocks 20, 26, 27 are State→District→Branch cascades, and block 22 is a typeahead. These need real
care:
- Assert the child is **disabled or empty until the parent is chosen**, that choosing a parent
  **populates** the child, and — the case most often missed — that **re-choosing the parent CLEARS a
  previously-selected child.** A stale child value after a parent change is a real, common defect.
- Pincode is a *searchable* dropdown with typeahead. Note `proxy.conf.json` proxies `/api/pincode`
  specifically — so pincode lookup may take a different network path from everything else. Verify
  where it actually goes before concluding a failure is a product bug.
- Do not assert a hardcoded list of states/districts. Read the options from the API response or the
  DOM and assert *relationships* (child options are a subset consistent with the parent), per
  ruling 1.

### Helpers you may use (read-only, in `test-data.ts` — do not edit that file)
`buildComplaint(overrides?)`, `createTestComplaint(request, overrides?, token?)`,
`loginCitizen`, `seedCitizenSession`, `getComplaint`, `cleanupComplaint`,
`readSystemConfig`/`setSystemConfig`/`clearSystemConfig`, `solveMathCaptcha(request)`,
`otpAttemptsFor`/`backdateOtpAttempt`/`deleteOtpAttempts`.
SQL helpers shell out to the MySQL CLI (`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`,
override `MYSQL_CLI`). Mobiles are validated `/^\d{10}$/` or the helper throws.

### Running your tests
```bash
npx playwright test e2e/public/complainant-pincode.spec.ts --project=chromium --reporter=line
```
Must run from `cms-portal-frontend` or the `chromium` project is not found. **Always
`--reporter=line`** — the config default is `html`, which can try to open a browser. One project
(`chromium`), `workers: 1`, `timeout: 60000`, `actionTimeout: 15000`, `retries: 0` locally. Never
`--headed`.

---

## 5. STANDING RULINGS — project law, not suggestions

1. **Never pin a configured number.** Read limits, caps and windows from the server response or
   `readSystemConfig` and compute boundaries from that. `ui-homogenisation/upload-limits.spec.ts`
   actively fails any source file that hardcodes a byte limit. Directly relevant to you: the 64-char
   email limit, 100-char address, 5000-char facts, 150-char reference number — if the server exposes
   them, read them; if you must use a literal, say so in your header and log it in findings.
2. **The manual test case is a claim, not the truth.** Where a manual case contradicts the running
   code, **do not bend the test to match the document and do not silently follow the code.** Write
   the spec against observed behaviour, mark it clearly, and log the contradiction in your findings
   file (§8). Your scope is field limits and cascades — expect several of these. The list is the
   most valuable thing you produce tonight.
3. **Prove negatives in SQL, not by HTTP status.** "The row was not written" needs a DB read; silent
   persistence is a known defect class here.
4. **A skip is not a pass.** Report skipped counts separately and explain them.
5. **You are writing TESTS, not fixing the product.** If a spec fails on a genuine product defect, do
   NOT edit `src/**` to make it green — that is how three parallel sessions corrupt a shared dev
   server for everyone (and a compile error in the shared `ng serve` breaks A and C instantly). Write
   the test to assert correct behaviour, mark it `test.fixme()` with a one-line reason, and record the
   defect. The ONLY exception: a trivial, provably-safe fix in a file no other session touches, stated
   explicitly in your summary.
6. **Never `git commit`, `git push`, or `git reset`.** I commit. Leave the tree dirty.
7. **Do not run the full suite.** Run only the spec you are working on. A full-suite run costs ~40
   minutes and three concurrent ones will thrash the shared backend into 429 throttling.
8. **`test.step` is not used anywhere in this repo (0 occurrences). Do not introduce it.**

### House style, so your files look like the other 101
- Heavy `═══`-ruled file header comment: why this approach, what cannot be proven and why, traps paid
  for. Reviewers expect this.
- Describe titles: `'QA-B20 — the district dropdown is driven by the selected state, and clears when the state changes'`
  (em-dash separating ID from a behavioural statement). Multiple describes per spec, one per cluster.
- Test titles: full lowercase sentences stating the outcome.
- `test.skip(cond, 'reason')` for absent preconditions; `test.fixme(` for known-unbuilt behaviour;
  `test.describe.serial(` where order matters. No `@tag` annotations exist — do not add any.
- **Self-seed everything.** Never rely on a pre-existing row. Clean up in `afterAll`.

---

## 6. BUILD ONE PATTERN, REUSE IT ~150 TIMES

Your scope is the most repetitive of the three: roughly 100 of your 182 cases are the same six
assertions applied to a different field (rejects numeric / rejects special chars / rejects emoji /
rejects only-spaces / rejects leading-trailing spaces / enforces max length / mandatory-when-blank).

**Write a single table-driven helper in `e2e/public/helpers-forms-b.ts`:**
```ts
// one row per field: selector, label, maxLength, allowed charset, expected error message
```
then drive Organization Landline, Email, Address, Facts, and the account/reference number fields from
it. **Do not hand-write 100 near-duplicate tests** — you will run out of night, and a reviewer cannot
read them. The error strings that already appear in the manual cases are
`"Response is mandatory."`, `"Only letters are allowed."`, `"Enter a valid email address."` — assert
the real rendered message, and if it differs from the document, that is a ruling-2 contradiction.

---

## 7. IF SOMETHING LOOKS BROKEN — diagnose before believing it

- **A 404 may be a stale JVM, not a missing endpoint.** 8092 may predate the latest recompile. Check
  its start time against `cms-backend/target/classes` mtimes before concluding an endpoint is absent.
  **Do not restart it** — A and C are on it. Report it.
- **`ERR_CONNECTION_REFUSED at localhost:4200` or `:8082`** means you dropped an env var from §1.
- **Beware: six RBIO frontend features are UI-complete against endpoints that DO NOT EXIST**, with
  errors swallowed so demos look clean while nothing persists. If a form appears to accept input and
  "succeed" but you cannot find the row in the DB, you have probably found one of these. Verify in SQL
  before recording a pass. This is exactly why ruling 3 exists.
- **A mass of identical UI-locator failures is usually ONE cause, not N defects.** Drive one in a
  browser and look at the screenshot before filing N defects.
- **Read the failure screenshots**: `$PW_OUTPUT_DIR/<test>/test-failed-1.png`, with the Read tool.
  They have repeatedly overturned wrong hypotheses faster than log-reading.
- **Strict-mode locator violations**: `:has-text("Nodal Officer")` also matches "Principal Nodal
  Officer"; a comma selector can match both a host tag and its own class. Use `.first()` or an exact
  match.
- **CORS 403**: only `4200,4201,4202,4300` are allowlisted. On another port use
  `installCorsShim(page)` from `e2e/public/browser-api.ts` — in `beforeEach`, AFTER the fixture's
  route, because Playwright matches route handlers in reverse registration order.
- **429** is the backend's anti-automation throttle reacting to three concurrent sessions. Slow down.
- If a `python -c` one-liner feels necessary: **do not.** Use `python scripts/qa/json_field.py
  <dotted.path>` and `python scripts/qa/i18n_check.py`. `python -c` is deliberately not permitted and
  will stall the run on a permission prompt.

---

## 8. DELIVERABLES — write these to disk as you go, not at the end

1. **The spec files** under `e2e/public/`, each passing or explicitly `fixme`'d with a reason.
2. **`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\findings-QA-B.md`** — append
   continuously. Must contain:
   - a coverage table: manual block → spec file → tests written → cases covered / deferred
   - **every contradiction** between a manual test case and observed behaviour (ruling 2) — field
     length limits and cascade behaviour especially. I need these for the BA.
   - every product defect found, with the failing assertion and a screenshot path
   - the deferred/not-automatable bucket with reasons
   - final counts: written / passing / failing / skipped / fixme
3. Leave the git tree dirty and uncommitted.

---

## 9. TIME BUDGET AND SESSION LONGEVITY — read this carefully

You have roughly **4 hours** and the machine is unattended. The auth token behind this session can
expire on wall-clock time regardless of activity, so **work in a way that survives being cut off
mid-flight**:

- **Checkpoint constantly.** Write each spec to disk and run it the moment it is coherent. Never hold
  three finished specs in your head to write at the end — a session that dies with nothing on disk
  delivered nothing.
- **Update `findings-QA-B.md` after every block**, not at the end.
- **Never issue a single blocking command longer than ~10 minutes.** One spec file per run. Long
  silent commands are both a timeout risk and how a session ends up with no evidence of its work.
- **No full-suite runs** (ruling 7) — the main way sessions blow their entire budget.
- Suggested pacing: **first 30 min building the wizard-navigation helper** (§4) and the table-driven
  field helper (§6) — everything else depends on them; ~1h15 on contact/address/pincode (17-22); ~1h15
  on RE-details cascades and complaint core fields (1, 23, 24, 26-32); ~20 min tooltips and the
  speech-to-text field-presence cases; ~30 min findings write-up.
- If you are behind at the 3-hour mark, **stop writing new specs and spend the remaining time making
  what exists pass and writing the findings file.** Half the cases automated and documented beats all
  of them half-written.

Start by reading `e2e/public/re-entity-search.spec.ts` and `eligibility-master.spec.ts:1-60` (for the
entry pattern), then your manual-test line ranges, then `git status --short e2e/` to see what the
previous session left. Build the navigation helper first. Then begin.
