# QA PASS 4 — SESSION A: Login / OTP / Captcha surface + Complainant identity fields

You are automating manual QA test cases into Playwright E2E specs for the CMS 2.0 citizen portal
(RBI complaint management, Angular frontend + Spring Boot backend).

**You are ONE OF THREE sessions running concurrently tonight.** Sessions B and C are working the same
repo at the same time. Everything in the "SHARED INFRASTRUCTURE" and "COLLISION RULES" sections is
there to stop you destroying their work or them destroying yours. Read those sections before you
write a single line of code.

Working directory for every command in this brief:
`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\cms-portal-frontend`

---

## 0. YOUR SCOPE — 166 manual test cases

Source file: `C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\prompt_testcase_cms_frontemd.txt`
(4266 lines, tab-separated: Description / Steps / Expected Result, with wrapped continuation lines).
It is 260KB — it EXCEEDS the Read tool's single-read limit, so read it with `offset`/`limit` in chunks.

**Read ONLY your line ranges. Do not read or automate anything outside them — B and C own the rest.**

| Block | Lines | Cases | Subject |
|---|---|---|---|
| 3 | 63-80 | 4 | Landing screen: Login / File-a-Complaint buttons → login screen |
| 4 | 81-128 | 13 | Login: field presence + mobile number validation (numeric only, must start 6-9, exactly 10 digits, not all zeros) |
| 5 | 129-146 | 7 | Login: captcha field (valid/invalid/blank, refresh icon, speaker icon) |
| 6 | 147-155 | 3 | Login: consent checkbox + Send OTP button enablement |
| 7 | 156-217 | 18 | OTP screen: masked number, OTP field validation, expiry, Verify button |
| 14 | 772-819 | 16 | Captcha deep-dive: text/image generation, case-sensitivity, timeout, expired, audio icon |
| 25 | 1658-1678 | 6 | Login: mandatory declaration checkbox |
| 36 | 2571-2612 | 8 | Negative login: blank/invalid/expired captcha & OTP block login |
| 8 | 218-314 | 8 | Complainant Details: Complainant Category dropdown (12 values, single-select) |
| 9 | 315-400 | 13 | Complainant Details: First Name (conditional display, 150 char) |
| 10 | 401-490 | 13 | Complainant Details: Middle Name |
| 11 | 491-577 | 13 | Complainant Details: Surname |
| 12 | 578-686 | 18 | Complainant Details: Age (+ Senior Citizen >= 60 rule) |
| 13 | 687-771 | 9 | Complainant Details: Gender dropdown (4 values) |
| 15 | 820-913 | 13 | Complainant Details: Organization Name |
| 16 | 914-963 | 4 | Complainant Details: Mobile Number (auto-populated, non-editable) |
| 60-62 | 4177-4266 | 25 | Track a complaint — **VERIFICATION TASK ONLY, see §6** |

**Known duplication inside your scope — do not write it twice.** Block 5 (129-146) and Block 14
(772-819) are the same captcha feature; 14 is the superset. Write ONE captcha spec covering the union
and note in the file header that both ranges map to it.

### Not automatable — put these in the deferred bucket, do NOT burn hours on them
- `144-146` and `817-819`: speaker icon *pronounces* the captcha. Audio output cannot be asserted.
  Automate only that the button exists, is focusable, and has an accessible label.
- `800-802`, `806-807`, `2589-2593`: captcha **expiry by wall clock**. Do not `waitForTimeout` for
  minutes. Either drive it via a config override + `backdateOtpAttempt`-style manipulation, or defer.
- `197-199`, `2609-2612`: OTP 5-minute expiry. There IS a proper tool for this — see §4.

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

**All three sessions SHARE the backend on 8092 and the dev server.** This is a deliberate decision,
not an oversight:
- `cms-backend/target/` is ONE directory shared by the whole repo. Three concurrent
  `mvn spring-boot:run` invocations clobber each other's class output and produce phantom compile
  errors in files you never touched.
- Your workload is UI field validation — typing into inputs and asserting error messages. It barely
  writes to the database. It does not need an isolated backend.

**Therefore: you must NOT run `mvn`, must NOT run `deployment/run-test-backend.sh`, must NOT run
`ng serve`, and must NOT restart anything.** If you believe the backend is broken, verify with
`curl` first (see §7) and report it rather than restarting — a restart kills sessions B and C too.

### Your session's environment — export these in EVERY shell you run tests from
```bash
export UI_BASE_URL=http://localhost:4202
export APP_BASE_URL=http://localhost:4202
export API_BASE_URL=http://localhost:8092
export PW_OUTPUT_DIR=/c/tmp/pw_A/test-results
export PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_A/report
```
All three variables matter and are read in three different places:
- `playwright.config.ts:13` reads **`UI_BASE_URL`** for `baseURL`.
- 33 specs under `e2e/public/` read **`APP_BASE_URL`**, falling back to `UI_BASE_URL` then 4200.
- `e2e/utils/test-data.ts` and the `request` fixture read **`API_BASE_URL`**, defaulting to 8082 —
  which is DOWN tonight, so forgetting it gives you a wall of connection errors, not a product bug.

`PW_OUTPUT_DIR`/`PLAYWRIGHT_HTML_REPORT` are per-session because the default `test-results/` and
`playwright-report/` are shared and three sessions would overwrite each other's failure screenshots.

Port 4202 is yours. **B uses 4200, C uses 4202 as well — if you see cross-talk, both of you are on a
port in the backend CORS allowlist (`4200,4201,4202,4300`) so either works; just never switch to a
port outside that list**, or every request 403s at Spring's CorsFilter.

---

## 2. COLLISION RULES — three sessions, one repo

**Create NEW spec files. Never edit a file you did not create**, with the single exception in §6.

Your files live under `e2e/public/` and must be named with your own facet suffixes:
```
e2e/public/login-mobile-field.spec.ts
e2e/public/login-captcha.spec.ts
e2e/public/login-consent-declaration.spec.ts
e2e/public/otp-field-validation.spec.ts
e2e/public/complainant-category.spec.ts
e2e/public/complainant-name-fields.spec.ts
e2e/public/complainant-age-gender.spec.ts
e2e/public/complainant-org-mobile.spec.ts
```
(Adjust as your reading dictates, but keep one spec per coherent feature and keep the names distinct
from everything listed in §3.)

**Chokepoint files — DO NOT EDIT. Sessions B and C need them unchanged:**
- `e2e/utils/test-data.ts` (1589 lines, every session depends on it)
- `e2e/fixtures.ts`, `e2e/utils/api-redirect.ts`, `e2e/utils/auth.ts`, `e2e/public/browser-api.ts`
- `playwright.config.ts`
- any `src/**` application code — see §5
If you genuinely need a new shared helper, put it in **your own** new file
`e2e/public/helpers-auth-a.ts` and import it only from your specs.

**Mobile numbers are shared global state.** OTP cooldown, hourly send limits and
`invalidateActiveOtps` all key on the mobile number, and `cms_db` is shared. Session A owns the
range **`98765_1____`** — i.e. generate numbers as `'987651' + 4 random digits`. Never reuse a
literal mobile that appears in an existing spec. Call `deleteOtpAttempts(mobile)` in `afterAll`.

**SYSTEM_CONFIG rows are shared global state.** If you `setSystemConfig` to arm a scenario, you MUST
restore it with `clearSystemConfig`/`setSystemConfig` in `afterAll`, even on failure. A left-behind
row silently breaks B and C.

---

## 3. WHAT ALREADY EXISTS — do not duplicate it

There are 101 spec files and ~1300 tests. A session before you just added ~30 more. **Before writing
any spec, run `git status --short e2e/` and `ls e2e/public/`** — more files may have appeared after
this brief was written.

Existing citizen-journey coverage most likely to overlap YOUR scope:

| Spec | Tests | Already covers |
|---|---|---|
| `public/otp-lifecycle.spec.ts` | 16 | **Heavy overlap with your blocks 6, 7, 36.** Real UI login (mobile → MATH captcha → send → verify) landing on eligibility; 6-box OTP length from markup; SMS template interpolation; expiry proven on both sides of the server-reported window via `backdateOtpAttempt`; resend cooldown. **Read this file FIRST, in full, before writing anything.** Your job is the field-level validation it does NOT do (alphabetic/special/emoji/space rejection, digit-count boundaries, 6-9 start rule, masked-number display). |
| `public/session-timeout.spec.ts` | 9 | Real login screen, 15-min inactivity, `publicAuthGuard` bounce, timed-out banner |
| `public/tracking-authz.spec.ts` | 19 | Tracking authz, PII masking, withdraw ownership, tracker CAPTCHA is real |
| `public/tracking-table.spec.ts` | 19 | Tracking table + empty state |
| `public/consent-dpdp.spec.ts` | 11 | Server-side DPDP consent; declaration enforcement — **check before writing block 25** |
| `public/re-entity-search.spec.ts` | 43 | RE selection/search — not yours, but it shows the house style for a big spec |
| `public/eligibility-*.spec.ts` | ~100 | Eligibility questions/windows — session B's neighbour, not yours |

If an existing spec covers 80% of a block, **append your cases to it only if it is a file YOU
created**; otherwise create the facet-suffixed sibling (`-field-validation`) and say so in the header.

---

## 4. THE HARNESS — verified facts, use exactly these

### Every browser spec must import from the fixture, not from Playwright
```ts
import { test, expect } from '../fixtures';   // NOT '@playwright/test'
```
`e2e/fixtures.ts:14-19` overrides the `page` fixture to rewrite the browser's hardcoded
`http://localhost:8082/**` calls to `API_BASE_URL`. The Angular bundle has 8082 compiled in. A spec
that imports raw Playwright will seed data on 8092 and then READ from 8082 — which is down tonight,
so you will chase a phantom. Four existing specs (`seo`, `seo-headings`, `signal-filters`,
`upload-limits`) import raw Playwright; they are source-scan/static specs. Do not copy them.

### Getting past the citizen login gate — this is the one thing that must work
The citizen journey is mobile + captcha + OTP, **not** Keycloak. Three mechanisms:

**(1) Seed the session directly — DEFAULT. Fastest, used by 16 specs.** `publicAuthService` only
checks that `cms_public_session` exists in sessionStorage and is not past the inactivity timeout
(`src/app/services/public-auth.service.ts:39-48`); it never validates the token signature. So:
```ts
await page.goto(`${APP_BASE}/public`);                    // MUST land on the app origin FIRST
await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
await page.goto(`${APP_BASE}/public/file-complaint`);     // guard now passes
await page.waitForLoadState('networkidle');
```
The ordering is load-bearing: sessionStorage is origin-scoped, so the first `goto` is mandatory.
Guarded routes: `/public/file-complaint`, `/public/withdraw`, `/public/withdraw/:id`,
`/public/feedback` (`src/app/app.routes.ts:416-419`). **This is what you want for blocks 8-16** —
you need to be sitting on the Complainant Details step, not re-proving login every test.

**(2) `loginCitizen(request, mobile)` for a REAL token** (`test-data.ts:138-168`): solves the MATH
captcha, posts send-otp, reads `devOtp` out of the response, verifies, returns the token. Returns
`null` if the backend is not exposing `devOtp` — callers `test.skip()` on that.

**I verified against 8092 at 17:00 today that `devOtp` IS exposed:**
```
POST /api/v1/citizen/auth/send-otp  →
{"sessionId":"...","message":"OTP sent...","devOtp":"123456","expiresInSeconds":600,
 "resendAfterSeconds":0,"success":true}
```
So mechanism 2 works tonight. Note `expiresInSeconds: 600` — the OTP window is **10 minutes, not the
5 minutes the manual test case at line 197-199 claims.** Read the real value from the response and
assert against that; do not hardcode 5. (See the standing ruling in §5.)

**(3) Drive the real login UI** — only when the assertion IS about the login UI, which for blocks
3-7, 14, 25, 36 it *is*. Canonical selectors, from `otp-lifecycle.spec.ts:141-168`:
```
goto('/public/login')
// the captcha starts VISUAL (answer never reaches the client). Flip it to MATH by clicking
//   .icon-btn[aria-label="Listen to CAPTCHA audio"]     ← wrap in expect().toPass()
#mobile-input                      .fill(mobile)
.captcha-input                     .fill(answer)
.consent-section input[type=checkbox]  .check()
.send-otp-btn                      ← assert enabled state; it gates on mobile+captcha+consent
.otp-inputs                        ← becomes visible
.otp-box  (6 of them)              ← auto-populated with '123456' in dev
.verify-btn
```
`environment.ts:11-12` sets `devAutoPopulateOtp: true, devDefaultOtp: '123456'`, and
`public-login.component.ts:150` prefers `res.devOtp` then falls back to that constant.
**MATH is the only solvable captcha type** — `generateVisualCaptcha` deliberately returns
`audioQuestion = null`. Always request `type=MATH`. There is no captcha bypass; the challenge is
genuinely solved.

Backend requirement: `cms.auth.otp.dev-auto-populate` is false by default and true only in
`dev-local`; `OtpExposureGuard.java:65` refuses to boot if it is true under any other profile. 8092
is on `dev-local`, which is why this works. Do not change profiles.

### Clock-dependent tests: never sleep
`backdateOtpAttempt(mobile, seconds)` (`test-data.ts:1502`) shifts **both** `created_at` and
`expires_at`, which is how `otp-lifecycle.spec.ts` proves expiry on both sides of the boundary
without waiting. Use it for your expiry cases (197-199, 2609-2612). Other SQL helpers:
`otpAttemptsFor(mobile)`, `deleteOtpAttempts(mobile)`, `readSystemConfig`/`setSystemConfig`/
`clearSystemConfig`, `solveMathCaptcha(request)`.
These shell out to the MySQL CLI (`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`, override
with `MYSQL_CLI`). Mobiles are validated `/^\d{10}$/` or the helper throws.

### Running your tests
```bash
npx playwright test e2e/public/login-captcha.spec.ts --project=chromium --reporter=line
```
Must be run from `cms-portal-frontend` or the `chromium` project is not found. **Always
`--reporter=line`**: the config's default is `html`, which can try to open a browser. There is
exactly one project (`chromium`) and `workers: 1`, `timeout: 60000`, `actionTimeout: 15000`,
`retries: 0` locally. Never `--headed`.

---

## 5. STANDING RULINGS — these are project law, not suggestions

1. **Never pin a configured number.** Read limits, windows and caps from the server response or
   `readSystemConfig` and compute boundaries from that. `e2e/ui-homogenisation/upload-limits.spec.ts`
   actively fails any source file that hardcodes a byte limit. This applies directly to you: OTP
   expiry is 600s not 300s, and the 150-char name limit and age-60 threshold must come from config
   or the API if they are configurable there.
2. **The manual test case is a claim, not the truth.** Where a manual case contradicts the running
   code (e.g. "OTP expires after 5 minutes" vs the measured 600s), **do not bend the test to match
   the document and do not silently follow the code.** Write the spec against observed behaviour,
   mark it clearly, and log the contradiction in your findings file (§8). I need the list.
3. **Prove negatives in SQL, not by HTTP status.** "The row was not written" needs a DB read;
   silent persistence is a known defect class in this codebase.
4. **A skip is not a pass.** Report skipped counts separately and always explain them.
5. **You are writing TESTS, not fixing the product.** If a spec fails because of a genuine product
   defect, do NOT go change `src/**` to make it green — that is how three parallel sessions corrupt
   a shared dev server for everyone. Write the test to assert correct behaviour, mark it
   `test.fixme()` with a one-line reason, and record the defect in your findings file. The ONLY
   exception: a trivial, provably-safe fix in a file no other session touches, and only if you state
   it explicitly in your summary.
6. **Never `git commit`, `git push`, or `git reset`.** I commit. Leave the tree dirty.
7. **Do not run the full suite.** Run only the spec you are working on. A single full-suite run costs
   ~40 minutes and three concurrent ones will thrash the shared backend into 429 anti-automation
   throttling. There is one full-suite run at the very end of the night and it is not yours.
8. **`test.step` is not used anywhere in this repo (0 occurrences). Do not introduce it.**

### House style, so your files look like the other 101
- Heavy `═══`-ruled file header comment explaining *why* this approach, what cannot be proven and
  why, and any trap you paid for. Reviewers expect this.
- Describe titles: `'QA-A4 — the mobile number field rejects everything that is not a 10-digit Indian mobile'`
  (em-dash separating ID from a behavioural statement). Multiple describes per spec, one per cluster.
- Test titles: full lowercase sentences stating the outcome.
- `test.skip(cond, 'reason')` for absent preconditions; `test.fixme(` for known-unbuilt product
  behaviour; `test.describe.serial(` where order matters. No `@tag` annotations exist — do not add any.
- **Self-seed everything.** Never rely on a pre-existing row. Clean up in `afterAll`.

---

## 6. YOUR ONE VERIFICATION TASK — blocks 60-62, Track a complaint (lines 4177-4266)

These 25 cases are *probably* already covered by `tracking-table.spec.ts` (19) and
`tracking-authz.spec.ts` (19), which together cover the list fields, empty state, cross-account
isolation and session expiry. **Do not rewrite them.** Instead, spend at most 30 minutes mapping the
25 manual cases onto the existing tests and produce a gap list. Only write new tests for genuine
gaps, in a new file `e2e/public/tracking-field-validation.spec.ts`. Likely real gaps: invalid/blank
complaint-number input handling, the case-id-only-is-not-trackable rule (4224-4227), and the audit
log assertion (4234-4235, which is a DB check not a UI one).

---

## 7. IF SOMETHING LOOKS BROKEN — diagnose before believing it

These traps have each cost a previous session hours:

- **A 404 may be a stale JVM, not a missing endpoint.** The backend on 8092 may have been running
  since before the latest recompile. Check its start time against `cms-backend/target/classes`
  mtimes before concluding an endpoint does not exist. **Do not restart it** — B and C are on it.
  Report it instead.
- **`ERR_CONNECTION_REFUSED at localhost:4200` or `:8082`** means you dropped an env var from §1,
  not that the app is broken.
- **A mass of identical UI-locator failures is usually ONE cause, not N defects.** A previous
  session's 25 `aa` failures were all a single unauthenticated-browser-context problem. Before
  filing N defects, drive ONE of them and look at the screenshot.
- **Read the failure screenshots.** `$PW_OUTPUT_DIR/<test>/test-failed-1.png`, with the Read tool.
  They have repeatedly overturned wrong hypotheses faster than log-reading (revealing a login page, a
  validation banner, an authenticated-but-empty profile).
- **Strict-mode locator violations**: `:has-text("Nodal Officer")` also matches "Principal Nodal
  Officer". Use `.first()` or an exact match.
- **CORS 403 on a non-allowlisted port**: only `4200,4201,4202,4300` are allowed. If you must use
  another port, `installCorsShim(page)` from `e2e/public/browser-api.ts` — call it in `beforeEach`
  AFTER the fixture's route, because Playwright matches route handlers in reverse registration order.
- **429 responses** are the backend's anti-automation throttle reacting to three concurrent sessions.
  Slow down; do not "fix" it by disabling the throttle.
- If a `python -c` one-liner feels necessary: **do not.** Use the committed helpers
  `python scripts/qa/json_field.py <dotted.path>` and `python scripts/qa/i18n_check.py`. `python -c`
  is deliberately not permitted and will stall the run on a permission prompt.

---

## 8. DELIVERABLES — write these to disk as you go, not at the end

1. **The spec files** under `e2e/public/`, each passing or explicitly `fixme`'d with a reason.
2. **`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\findings-QA-A.md`** — append to it
   continuously. It must contain:
   - a coverage table: manual block → spec file → tests written → cases covered / deferred
   - **every contradiction** between a manual test case and observed behaviour (ruling 2). This is
     the highest-value output of the night; I need to take these to the BA.
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
  three finished specs in your head intending to write them all at the end. A session that dies with
  nothing on disk delivered nothing.
- **Update `findings-QA-A.md` after every block**, not at the end.
- **Never issue a single blocking command longer than ~10 minutes.** Run one spec file at a time.
  Long silent commands are both a timeout risk and how a session ends up with no evidence of what it
  was doing. Frequent, short tool calls keep the session healthy; a 40-minute blocking run does not.
- **No full-suite runs** (ruling 7) — that is the main way sessions blow their whole budget.
- Suggested pacing: ~45 min on login/captcha/OTP field validation (blocks 3-7, 14, 25, 36), ~2h on
  the Complainant Details fields (8-16, the bulk of your cases, and highly repetitive so build one
  good parametrised pattern and reuse it), ~30 min on the tracking gap analysis (§6), ~30 min
  writing up findings. If you are behind at the 3-hour mark, **stop writing new specs and spend the
  remaining time making what exists pass and writing the findings file.** Half the cases automated
  and documented beats all of them half-written.
- Blocks 9, 10, 11, 15 (First/Middle/Surname, Organization Name) are near-identical 13-case field
  validations. **Write one parametrised helper in `e2e/public/helpers-auth-a.ts` and drive all four
  from a table.** Do not hand-write 52 near-duplicate tests.

Start by reading `e2e/public/otp-lifecycle.spec.ts` in full, then your manual-test line ranges, then
`git status --short e2e/` to see what the previous session left. Then begin.
