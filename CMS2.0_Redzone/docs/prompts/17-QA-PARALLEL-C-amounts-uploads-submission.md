# QA PASS 4 — SESSION C: Account/card numbers, compensation caps, uploads, review & submission, duplicates

You are automating manual QA test cases into Playwright E2E specs for the CMS 2.0 citizen portal
(RBI complaint management, Angular frontend + Spring Boot backend).

**You are ONE OF THREE sessions running concurrently tonight.** Sessions A and B are working the same
repo at the same time. Everything in the "SHARED INFRASTRUCTURE" and "COLLISION RULES" sections is
there to stop you destroying their work or them destroying yours. Read those sections before you
write a single line of code.

Working directory for every command in this brief:
`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\cms-portal-frontend`

---

## 0. YOUR SCOPE — 207 manual test cases (before collapsing the duplicates noted below)

Source file: `C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\prompt_testcase_cms_frontemd.txt`
(4266 lines, tab-separated: Description / Steps / Expected Result, with wrapped continuation lines).
It is 260KB — it EXCEEDS the Read tool's single-read limit, so read it with `offset`/`limit` in chunks.

**Read ONLY your line ranges. Do not read or automate anything outside them — A and B own the rest.**

| Block | Lines | Cases | Subject |
|---|---|---|---|
| 33 | 2307-2395 | 10 | Savings Account Number (100 char) |
| 34 | 2396-2482 | 10 | Loan Account Number |
| 35 | 2483-2570 | 10 | ATM/Debit Card Number |
| 37 | 2613-2698 | 10 | Credit Card Number |
| 38 | 2699-2770 | 5 | "Is the complaint against an RE wallet?" radio |
| 39 | 2771-2850 | 10 | Name of Wallet (100 char) |
| 40 | 2851-2943 | 10 | Transaction / Reference Number (150 char) |
| 41 | 2944-3011 | 5 | "Complaint against a business correspondent?" radio |
| 42 | 3012-3107 | 11 | Amount Involved in Transaction / Dispute |
| 43 | 3108-3223 | 12 | Compensation Sought — Consequential Loss (**cap claimed as 30 lakh**) |
| 44 | 3224-3346 | 12 | Compensation Sought — Expenses / Harassment (**cap claimed as 3 lakh**) |
| 45 | 3347-3438 | 12 | Upload additional documents (JPG/PDF/DOCX, **size claimed as 2 MB**, drag-drop, multiple) |
| 46 | 3439-3505 | 7 | Declaration & Submission screen: checkboxes, clause 10(2) text, back/next |
| 47 | 3506-3577 | 15 | Save / auto-save / draft lifecycle (refresh, browser close, session expiry, edit-after-submit) |
| 49 | 3614-3698 | 13 | Eligibility → non-maintainable closure clauses, case-id, closure-letter PDF download |
| 50 | 3699-3748 | 6 | Non-maintainable pop-up messages (RBIO vs CEPC wording) |
| 51 | 3749-3796 | 6 | FRC eligibility question → non-maintainable pop-up |
| 52 + 57 | 3797-3852 **and** 4069-4109 | 15 net | Review screen: uploaded-document preview (**merge these two, see below**) |
| 54 | 3911-3978 | 10 | Duplicate complaint detection (mobile-combo & email-combo, tagging as duplicate) |
| 55 | 3979-4016 | 8 | Duplicate complaint pop-up (cancel / proceed) |
| 56 | 4017-4068 | 10 | Tabbed interface across the 7 wizard sections + preview |

### Known duplication inside your scope — do not write it twice
- **Blocks 52 (3797-3852) and 57 (4069-4109) are the same document-preview feature**, 6 descriptions
  identical. 52's unique tail is remove / re-upload / close; 57's unique tail is download / label /
  open-multiple-one-by-one. **Write ONE preview spec covering the union** and note both ranges in the
  header.
- **Blocks 50 and 51 share 6 identical rows** (view message clearly / states reason per clause /
  guidance on next steps), replayed across the eligibility and FRC blocks. Write them once.
- **Blocks 54 and 55 share 4 identical rows** (the pop-up cancel-button cases). Write them once.

### Not automatable — deferred bucket, do NOT burn hours
- `4110-4136` (7 cases) **SMS notification on submission** — real SMS delivery, template rendering,
  once-only send, multi-language content. At best assert a row in the communication outbox
  (`readCommunicationOutbox` exists in `test-data.ts`). Actual delivery is manual. **Do this outbox
  assertion only if time permits; the delivery cases are deferred.**
- `4137-4176` (11 cases) **email acknowledgement / closure letter + digital signature + PKI
  compliance** — real email delivery and a legal/document compliance judgement. **Important context:
  the project ruling is that the DSC is a MOCK, and there is already a spec
  `public/pdf-signature.spec.ts` asserting that downloaded PDFs must NOT claim a digital signature.**
  So the manual cases demanding a digital signature *contradict* the delivered behaviour. **Do not
  "fix" this by writing a test that demands a real signature — log it as a ruling-2 contradiction
  (§5) and move on.** This is a high-value finding.
- `3565-3575` (3 cases) autosave after **the user directly closes the browser** and after **session
  expiry** — not reliably drivable. Autosave-after-refresh IS automatable; do that one.
- `3825-3827` (1 case) "preview is screen-reader compatible with zoom options" — needs an assistive
  technology / manual audit. An `axe` pass covers only part of it.

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
`cms-backend/target/` is ONE directory shared by the whole repo, and three concurrent
`mvn spring-boot:run` invocations clobber each other's class output, producing phantom compile errors
in files you never touched.

**Therefore: you must NOT run `mvn`, must NOT run `deployment/run-test-backend.sh`, must NOT run
`ng serve`, and must NOT restart anything.** If you believe the backend is broken, verify with `curl`
first (see §7) and report it rather than restarting — a restart kills sessions A and B too.

**You are the session that writes most to the database** (complaint submissions, duplicates, drafts,
uploads). Be disciplined about cleanup (§2) — A and B are reading the same `cms_db`.

### Your session's environment — export these in EVERY shell you run tests from
```bash
export UI_BASE_URL=http://localhost:4202
export APP_BASE_URL=http://localhost:4202
export API_BASE_URL=http://localhost:8092
export PW_OUTPUT_DIR=/c/tmp/pw_C/test-results
export PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_C/report
```
All three base-URL variables matter, read in three different places:
- `playwright.config.ts:13` reads **`UI_BASE_URL`** for `baseURL`.
- 33 specs under `e2e/public/` read **`APP_BASE_URL`**, falling back to `UI_BASE_URL` then 4200.
- `test-data.ts` and the `request` fixture read **`API_BASE_URL`**, defaulting to 8082 — DOWN
  tonight, so forgetting it gives a wall of connection errors, not a product bug.
Setting only one previously produced 47 fake failures. Export all three, every shell.

`PW_OUTPUT_DIR`/`PLAYWRIGHT_HTML_REPORT` are per-session because the default `test-results/` and
`playwright-report/` are shared and three sessions would overwrite each other's failure screenshots.

Stay on a port in the backend CORS allowlist (`4200,4201,4202,4300`) or every request 403s at
Spring's CorsFilter.

---

## 2. COLLISION RULES — three sessions, one repo

**Create NEW spec files. Never edit a file you did not create.**

Your files live under `e2e/public/`. Suggested names — keep them distinct from everything in §3:
```
e2e/public/account-card-numbers.spec.ts        (33, 34, 35, 37)
e2e/public/wallet-bc-reference.spec.ts         (38, 39, 40, 41)
e2e/public/amount-compensation-caps.spec.ts    (42, 43, 44)
e2e/public/wizard-upload-documents.spec.ts     (45)
e2e/public/declaration-submission.spec.ts      (46)
e2e/public/draft-autosave-lifecycle.spec.ts    (47)
e2e/public/non-maintainable-closure.spec.ts    (49, 50, 51)
e2e/public/review-document-preview.spec.ts     (52 + 57 merged)
e2e/public/duplicate-detection-popup.spec.ts   (54, 55)
e2e/public/wizard-tabs.spec.ts                 (56)
```

**Chokepoint files — DO NOT EDIT. Sessions A and B need them unchanged:**
- `e2e/utils/test-data.ts` (1589 lines, every session depends on it)
- `e2e/fixtures.ts`, `e2e/utils/api-redirect.ts`, `e2e/utils/auth.ts`, `e2e/public/browser-api.ts`
- `playwright.config.ts`
- any `src/**` application code — see §5
Need a shared helper? Put it in **your own** new file `e2e/public/helpers-submission-c.ts`.

**Mobile numbers are shared global state.** OTP cooldown, hourly send limits and
`invalidateActiveOtps` key on the mobile number, and `cms_db` is shared. Session C owns the range
**`98765_3____`** — generate as `'987653' + 4 random digits`. This matters more for you than for the
others: **duplicate detection keys on mobile + name + entity + transaction date + category**, so a
collision with A's or B's data will make your duplicate tests lie in both directions. Use a fresh
mobile per test and call `deleteOtpAttempts(mobile)` plus `cleanupComplaint` in `afterAll`.

**SYSTEM_CONFIG rows are shared global state.** You will likely need to arm upload limits and
compensation caps via config. **Restore every row in `afterAll`, even on failure** — a left-behind
upload-limit row silently breaks A and B and corrupts tomorrow's full-suite run.

---

## 3. WHAT ALREADY EXISTS — do not duplicate it

101 spec files, ~1300 tests; a session before you just added ~30 more. **Before writing any spec, run
`git status --short e2e/` and `ls e2e/public/`** — more files may have appeared after this brief.

Coverage most likely to overlap YOUR scope:

| Spec | Tests | Already covers |
|---|---|---|
| `ui-homogenisation/upload-limits.spec.ts` | 7 | **Directly constrains your block 45.** It is a *source-scan* spec (no browser, no request) that FAILS any source file hardcoding a byte limit, forbids "MB" in templates, requires the validator to take limits as arguments, and asserts the FE fallback equals the server fallback. **Read it before writing upload tests — and note it will fail if you introduce a hardcoded size anywhere.** |
| `public/duplicate-check.spec.ts` | **11** | **Heavy overlap with your blocks 54, 55.** The duplicate pre-check endpoint plus the UI **failing closed** when the check errors. **Read in full first.** Your net-new work is likely the two specific parameter combinations (mobile-combo vs email-combo), the exact error strings, the cancel/proceed pop-up behaviour, and the "new complaint is tagged as a duplicate of the earlier one" persistence assertion. |
| `public/draft-save.spec.ts` | 4 | **Overlaps block 47.** Draft save surfaces the real server outcome — no fake success toast. Your net-new work is the autosave lifecycle (refresh, timing) and edit-after-submit. |
| `public/pdf-signature.spec.ts` | 2 | **Critical for your deferred block (4137-4176).** Asserts downloaded PDFs must NOT claim a digital signature (UST97/D5). |
| `public/consent-dpdp.spec.ts` | 11 | **Overlaps block 46.** Server-side DPDP consent: ONLINE rejected without declaration, PHYSICAL/EMAIL intake exempt, notice localised, wizard cannot submit unticked. Read before writing declaration tests. |
| `public/eligibility-*.spec.ts` | ~100 over 7 files | **Overlaps blocks 49, 50, 51.** Notably `eligibility-re-window.spec.ts` (31 tests) proves the RE-window refusal is **client-side and NOT a persisted auto-closure**; `eligibility-maintainability-questions.spec.ts` (19) covers the 7 maintainability questions with clause refs; `eligibility-clause-interpolation.spec.ts` (9) proves clauses come from the master, never baked into prose. **Your non-maintainable closure work must not contradict these.** |
| `public/complainant-upload` component | — | There is a `src/app/components/public/complainant-upload` component — find its selectors before guessing. |
| `public/withdrawal-attachments.spec.ts` | 9 | File-attachment patterns you can crib for uploads |

If an existing spec covers 80% of a block, create a facet-suffixed sibling and say so in your header.
Do not edit theirs.

---

## 4. THE HARNESS — verified facts, use exactly these

### Every browser spec must import from the fixture, not from Playwright
```ts
import { test, expect } from '../fixtures';   // NOT '@playwright/test'
```
`e2e/fixtures.ts:14-19` overrides `page` to rewrite the browser's hardcoded `http://localhost:8082/**`
calls to `API_BASE_URL`. The Angular bundle has 8082 compiled in. A spec importing raw Playwright
seeds on 8092 and READS from 8082 — down tonight, so you will chase a phantom. The four specs that do
import raw Playwright (`seo`, `seo-headings`, `signal-filters`, `upload-limits`) are static/source-scan
specs. Do not copy them.

### Getting to YOUR screens
Your fields are on **Complaint Details** (step 5), **Review** and **Declaration & Submission** (step 7)
— the deepest part of a 7-step wizard. Seed the session rather than re-driving login:
```ts
await page.goto(`${APP_BASE}/public`);                   // MUST land on the app origin FIRST
await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
await page.goto(`${APP_BASE}/public/file-complaint`);     // guard now passes
await page.waitForLoadState('networkidle');
```
`publicAuthService` only checks that `cms_public_session` exists and is within the inactivity timeout
(`src/app/services/public-auth.service.ts:39-48`); the token signature is never validated. The
ordering is load-bearing — sessionStorage is origin-scoped, so the first `goto` is mandatory. Guarded
routes: `/public/file-complaint`, `/public/withdraw`, `/public/withdraw/:id`, `/public/feedback`
(`src/app/app.routes.ts:416-419`).

**Session B is building a wizard-navigation helper for the same journey. You are NOT sharing it** (it
will not exist when you start, and cross-importing between sessions' files creates a race). Build your
own minimal walk-to-step-5 helper in `helpers-submission-c.ts`. Crib the entry pattern from
`eligibility-master.spec.ts:28-33`.

**For a REAL server-side citizen token** (needed for submission endpoints the backend authenticates):
`loginCitizen(request, mobile)` (`test-data.ts:138-168`) solves the MATH captcha, posts send-otp, reads
`devOtp` from the response, verifies, returns the token; `null` if `devOtp` is not exposed. **I verified
against 8092 at 17:00 today that it IS exposed:**
```
POST /api/v1/citizen/auth/send-otp → {"sessionId":"...","devOtp":"123456","expiresInSeconds":600,...}
```
MATH is the only solvable captcha type — always request `type=MATH`. No captcha bypass exists.
Pair `loginCitizen` with `seedCitizenSession` to put a real token in the browser (see
`citizen-appeal-filing.spec.ts:100,118`).

**To create a complaint fast without the UI**, use `createTestComplaint(request, overrides?, token?)`
and `buildComplaint(overrides?)`, then `advanceToStatus(request, complaintNumber, status)`. This is the
right tool for your duplicate-detection setup: create the FIRST complaint via the API, then drive the
SECOND one through the UI to trigger the duplicate pop-up. Far faster and less flaky than filing twice
through the wizard.

### Helpers you may use (read-only — do not edit `test-data.ts`)
`buildComplaint`, `createTestComplaint`, `advanceToStatus`, `getComplaint`, `cleanupComplaint`,
`loginCitizen`, `seedCitizenSession`, `performAction`, `readSystemConfig`/`setSystemConfig`/
`clearSystemConfig`, `readCommunicationOutbox`, `backdateComplaintClosure`,
`DEFAULT_CLOSURE_CLAUSE = '15(1)(a)'`, `solveMathCaptcha`, `deleteOtpAttempts`.
SQL helpers shell out to the MySQL CLI (`C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe`,
override `MYSQL_CLI`). Mobiles validated `/^\d{10}$/` or the helper throws.

**Closure-clause trap:** 1646 of 1647 closed complaints in this DB have no closure clause, which makes
them un-appealable (503). `DEFAULT_CLOSURE_CLAUSE` exists for that reason. If a closure-related test
behaves oddly, check whether the complaint actually has a clause.

### Running your tests
```bash
npx playwright test e2e/public/amount-compensation-caps.spec.ts --project=chromium --reporter=line
```
Must run from `cms-portal-frontend` or the `chromium` project is not found. **Always
`--reporter=line`** — the config default is `html`, which can try to open a browser. One project
(`chromium`), `workers: 1`, `timeout: 60000`, `actionTimeout: 15000`, `retries: 0` locally. Never
`--headed`.

---

## 5. STANDING RULINGS — project law, and your scope collides with them hardest

1. **NEVER PIN A CONFIGURED NUMBER. This is the ruling most relevant to you.** Three of your blocks
   quote hard figures the project has explicitly made configurable:
   - Upload size: the manual case says **2 MB**. The current ruling is that **configuration decides**,
     with values around **2MB per file / 25MB total / 10 files**, and there were three separate
     enforcement paths that had drifted apart. **Guards must name no size.**
   - Compensation: the manual cases say **30 lakh** and **3 lakh**. These caps were hardcoded and are
     flagged as needing confirmation; the statutory award cap is BUILT but config-gated and currently
     OFF pending legal sign-off.
   **So: read the limit from the server (`readSystemConfig` or the API response), then compute your
   boundary cases from it — at the limit, one over, one under.** Do not write `expect(...).toBe(2097152)`.
   `ui-homogenisation/upload-limits.spec.ts` will actively fail your work if you hardcode a byte limit.
2. **The manual test case is a claim, not the truth.** Where a manual case contradicts the running
   code, **do not bend the test to match the document and do not silently follow the code.** Write the
   spec against observed behaviour, mark it clearly, and log the contradiction in your findings file
   (§8). **You will hit several: the 2MB figure, the two compensation caps, and the digital-signature
   cases that contradict `pdf-signature.spec.ts`.** This list is the single most valuable thing you
   produce tonight — I take it to the BA and to legal.
3. **Prove negatives in SQL, not by HTTP status.** "The complaint was not created", "the draft was not
   saved", "the duplicate was tagged" — all need a DB read. Silent persistence is a known defect class
   here. **Also relevant: six RBIO frontend features are UI-complete against endpoints that DO NOT
   EXIST, with errors swallowed so demos look clean while nothing persists.** If a form appears to
   succeed but you cannot find the row, you may have found one. Verify in SQL before recording a pass.
4. **Appendix-8 auto-closure does NOT exist** — the code evaluates it but never closes anything.
   Similarly the RE-window refusal is client-side, not a persisted auto-closure. **So for blocks 49-51,
   assert what actually happens (a case-id, a pop-up, a clause shown), NOT that the system closed the
   complaint** — unless you prove the closure in SQL.
5. **A skip is not a pass.** Report skipped counts separately and explain them.
6. **You are writing TESTS, not fixing the product.** If a spec fails on a genuine product defect, do
   NOT edit `src/**` to make it green — a compile error in the shared `ng serve` breaks A and B
   instantly. Write the test to assert correct behaviour, `test.fixme()` it with a one-line reason, and
   record the defect. Only exception: a trivial, provably-safe fix in a file no other session touches,
   stated explicitly in your summary.
7. **Never `git commit`, `git push`, or `git reset`.** I commit. Leave the tree dirty.
8. **Do not run the full suite.** Only the spec you are working on. A full-suite run costs ~40 minutes
   and three concurrent ones thrash the shared backend into 429 throttling.
9. **`test.step` is not used anywhere in this repo (0 occurrences). Do not introduce it.**

### House style, so your files look like the other 101
- Heavy `═══`-ruled file header comment: why this approach, what cannot be proven and why, traps paid
  for. Reviewers expect this.
- Describe titles: `'QA-C43 — the consequential-loss cap is whatever configuration says, and the boundary is enforced'`
  (em-dash separating ID from a behavioural statement). Multiple describes per spec.
- Test titles: full lowercase sentences stating the outcome.
- `test.skip(cond,'reason')` / `test.fixme(` / `test.describe.serial(` where order matters. No `@tag`
  annotations exist — do not add any.
- **Self-seed everything.** Never rely on a pre-existing row. Clean up in `afterAll`.

---

## 6. BUILD ONE PATTERN, REUSE IT

Blocks 33, 34, 35, 37, 39, 40 are six near-identical 10-case field validations (account numbers, card
numbers, wallet name, reference number). Blocks 42, 43, 44 are three near-identical numeric/amount
validations. **Write two table-driven helpers in `e2e/public/helpers-submission-c.ts`** — one for text
fields (charset, max length, spaces, emoji, mandatory) and one for amount fields (numeric only,
negative, zero, decimal, cap boundary read from config) — and drive all nine blocks from tables. **Do
not hand-write 90 near-duplicate tests**; you will run out of night and no reviewer can read them.

For uploads, real fixture files are needed. Check whether the repo already has test fixtures before
creating any (`find e2e -iname '*fixture*' -o -iname '*.pdf' -o -iname '*.docx'`). Generate any new
ones into `/c/tmp/pw_C/fixtures/`, **not** into the repo, and build the over-limit file
programmatically from the configured limit rather than committing a large binary.

---

## 7. IF SOMETHING LOOKS BROKEN — diagnose before believing it

- **A 404 may be a stale JVM, not a missing endpoint.** 8092 may predate the latest recompile. Check
  its start time against `cms-backend/target/classes` mtimes first. **Do not restart it** — A and B are
  on it. Report it.
- **`ERR_CONNECTION_REFUSED at localhost:4200` or `:8082`** means you dropped an env var from §1.
- **A "successful" submission that persists nothing** — see ruling 3. Verify in SQL.
- **A mass of identical UI-locator failures is usually ONE cause, not N defects.** Drive one in a
  browser and read the screenshot before filing N defects.
- **Read the failure screenshots**: `$PW_OUTPUT_DIR/<test>/test-failed-1.png`, with the Read tool.
  They have repeatedly overturned wrong hypotheses faster than log-reading.
- **Strict-mode locator violations**: `:has-text("X")` matching a longer superstring. Use `.first()` or
  an exact match.
- **CORS 403**: only `4200,4201,4202,4300` are allowlisted. On another port use `installCorsShim(page)`
  from `e2e/public/browser-api.ts` — in `beforeEach`, AFTER the fixture's route, because Playwright
  matches route handlers in reverse registration order.
- **429** is the backend's anti-automation throttle reacting to three concurrent sessions. Slow down;
  do not disable the throttle.
- If a `python -c` one-liner feels necessary: **do not.** Use `python scripts/qa/json_field.py
  <dotted.path>` and `python scripts/qa/i18n_check.py`. `python -c` is deliberately not permitted and
  will stall the run on a permission prompt.

---

## 8. DELIVERABLES — write these to disk as you go, not at the end

1. **The spec files** under `e2e/public/`, each passing or explicitly `fixme`'d with a reason.
2. **`C:\Projects\My-HRMS-Frontend\CMS2.0_Redzone\docs\prompts\findings-QA-C.md`** — append
   continuously. Must contain:
   - a coverage table: manual block → spec file → tests written → cases covered / deferred
   - **a dedicated section listing every hard number the manual cases assert versus what configuration
     actually says** (2MB upload, 30 lakh, 3 lakh, 100/150 char limits, digital signature). This is the
     top deliverable — it goes to the BA and to legal.
   - every product defect found, with the failing assertion and a screenshot path
   - the deferred/not-automatable bucket with reasons (SMS delivery, email/PKI, browser-close autosave,
     screen-reader preview)
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
- **Update `findings-QA-C.md` after every block**, not at the end. Your contradiction list is more
  valuable than your last spec; do not let it die with the session.
- **Never issue a single blocking command longer than ~10 minutes.** One spec file per run. Long silent
  commands are both a timeout risk and how a session ends up with no evidence of its work.
- **No full-suite runs** (ruling 8) — the main way sessions blow their entire budget.
- You have the largest scope (213 cases) and the most write-heavy one, so **prioritise ruthlessly in
  this order**: (a) the two table-driven helpers plus the account/card/amount blocks — ~1h15, the
  highest case-count-per-hour work; (b) uploads and compensation caps — ~45 min, where the
  configuration contradictions live; (c) duplicate detection and the review/preview screen — ~1h,
  highest business risk; (d) declaration/submission, draft lifecycle, non-maintainable closure, wizard
  tabs — whatever remains.
- If you are behind at the 3-hour mark, **stop writing new specs and spend the remaining time making
  what exists pass and writing the findings file.** Half the cases automated and documented beats all
  of them half-written.

Start by reading `e2e/public/duplicate-check.spec.ts` and
`e2e/ui-homogenisation/upload-limits.spec.ts` in full (they constrain you most), then your manual-test
line ranges, then `git status --short e2e/` to see what the previous session left. Build the two
table-driven helpers first. Then begin.
