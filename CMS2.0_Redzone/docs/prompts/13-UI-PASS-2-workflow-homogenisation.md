# BATCH: UI PASS 2 — ONE TASK-GRID/COMPLAINT EXPERIENCE, PA LOCALE, MASTER-DATA SEEDING, CONFIGURABLE STATUTORY LIMITS. Run alone.

Pass 1 built the token layer, the shared shell, one toast mechanism and made the upload limit
configurable. It did **not** finish the job: only 2 of ~50 screens actually adopt the shell, and the
product still looks like five applications wearing one stylesheet.

This pass is about the **application model**, not paint. Read item 0 before planning — it is the
single most important framing in this document, and it came from the product owner.

Everything below was measured **2026-09-22** against `cms_db` and HEAD `bd95982`
(branch `CMS_21092026`, also now force-pushed to `development`). **Re-verify before relying on any
of it; this file ages.** Where pass 1's own report was wrong, that is called out explicitly —
do not trust the previous report over the code.

---

## 0. THE PRODUCT MODEL — READ THIS FIRST, IT CHANGES WHAT "HOMOGENISATION" MEANS

**This is a workflow / task-based application.** Stated by the product owner:

> Users of various roles see a **task grid**; they filter tasks by various columns; they click a task
> and **the task is nothing but the complaint**. So the DATA in the grid changes by role and module,
> but the UI must **look and feel the same**.

So every module is the same two screens:

```
   ┌─────────────────────────────┐        ┌──────────────────────────────┐
   │  TASK GRID                  │ click  │  COMPLAINT / TASK DETAIL     │
   │  - role-filtered rows       │ ─────▶ │  - same layout everywhere    │
   │  - column filters + search  │        │  - same section order        │
   │  - status chips, ageing     │        │  - same action bar           │
   │  - pagination               │        │  - module supplies the DATA  │
   └─────────────────────────────┘        └──────────────────────────────┘
```

**CRPC already does this correctly and is THE REFERENCE** — its grid, its complaint form, its
filters, its detail layout. RBIO, AA, CEPC and RE must become the same two screens with different
data, not five different interpretations.

### What this means concretely (and what pass 1 got wrong)

Pass 1 treated homogenisation as *colour plus a header*. That was too shallow. The real work is:

1. **One grid component.** Today there are **18 separate hand-rolled tables**
   (crpc 5, aa 5, rbio 3, re-portal 3, cepc 2). They disagree on sorting, column filtering, empty
   state, pagination, row hover, ageing badges and how a row is opened. Extract CRPC's grid into a
   **shared, configuration-driven** component: columns, filters, row actions and the row-click target
   are inputs; no module-specific logic inside it.
2. **One complaint/task detail shell.** Six detail components exist
   (`aa/aa-appeal-detail`, `cepc/cepc-complaint-detail`, `rbio/rbio-complaint-detail`,
   `re-portal/re-complaint-detail`, `public/complaint-detail`, `email-syndication/draft-detail`).
   Each invents its own section order, tab strip and action placement. Standardise on CRPC's:
   header/identity block → status + SLA → tabbed sections → action bar. The **sections differ by
   module; the frame must not.**
3. **The shell is barely adopted.** `app-shell` is used by **exactly 2 templates**
   (`crpc/deo-home`, `cepc/cepc-dashboard`). Every other screen still repeats its own header/sidebar
   markup. Roll it out.

**Do not redesign CRPC.** If a shared component forces a change, change the OTHER module. CRPC
changing appearance is the signal you have got it backwards.

---

## 1. MEASURED STATE — VERIFY, THEN USE

### 1.1 Shell and grid adoption
| Fact | Value |
|---|---|
| Templates using `app-shell` | **2** (`crpc/deo-home`, `cepc/cepc-dashboard`) |
| Hand-rolled tables/grids | **18** across 5 modules |
| Detail ("task opened") components | **6** |
| Only pre-existing layout component | `re-portal/re-layout` |

### 1.2 Design tokens (pass 1 delivered; extend, do not re-invent)
`src/styles.scss` has a `:root` token block; `src/_primitives.scss` holds the shared class
vocabulary (`.btn`, `.stat-card`, `.data-grid`, `.ui-tabs`, `.modal-dialog`, `.ui-pagination`…).
Residual raw hex **after** pass 1:

| module | distinct | occurrences |
|---|---|---|
| crpc | 53 | 110 |
| rbio | 72 | 178 |
| aa | 44 | 85 |
| cepc | 23 | 37 |
| re-portal | 11 | 14 |
| shared | 11 | 28 |
| **public** | **169** | **1069** |
| **admin** | **74** | **520** |

`public/` and `admin/` were **out of scope in pass 1 and are IN SCOPE now** — they are the largest
remaining source of visual drift. Reuse `scripts/qa/tokenise_colours.py` (committed) and extend its
mapping rather than hand-editing; run `--report` before and after for the delta.

### 1.3 Localisation
`| translate` repo-wide: **1122**. Per module: aa 344, public 302, rbio 176, shared 83, re-portal 72,
admin 20, **crpc 10**, **cepc 9**. CRPC and CEPC were zero before pass 1 and are still nearly
untranslated — only their shell chrome and toolbars were done. **The grids, forms and detail views
of crpc and cepc are still hardcoded English.**

`UiShellTranslationSeeder` at `@Order(60)` holds 70 keys under `ui.*` / `cepc.*` / `crpc.*` /
`common.skip_to_content`. **Do not edit it — take a NEW seeder at `@Order(61+)`** and verify the
number is unclaimed first (`@Order` 3-22, 32-34, 38, 42-44, 60 are in use).

---

## 2. ITEM-BY-ITEM WORK, WITH THE DECISIONS NOW SETTLED

All ten items below were **answered by the product owner**. No item here needs a further question.

### 2.1 Streamline the UI further — the main body of work (item 0)
Deliver, in this order, gating on `ng build` + that module's specs after each step:

1. **Shared task-grid component** under `shared/`, extracted from CRPC. Config-driven: column defs
   (key, label key, sortable, filterable, width, formatter), row-click handler, optional row actions,
   optional bulk selection, empty/loading/error states, pagination. **Every label is a translation
   key, never a literal.**
2. **Shared task/complaint detail frame** under `shared/`: identity header, status + SLA strip,
   tab strip, content slots, action bar. Modules pass sections and actions in.
3. **Migrate in ascending risk: cepc → re-portal → rbio → aa.** AA last: it is the best-localised
   module (344 uses) so its markup is the most dangerous to churn.
4. **Roll `app-shell` out to every staff screen**, not just the 2 current ones.
5. Tokenise `public/` and `admin/`.

**Watch for these live bugs when you touch the grids** — filters that read a plain class field from a
`computed()` register no dependency and silently do nothing. Known instances:
`cepc-dashboard` + `crpc/deo-home` `columnSearchText`, `report-builder` `filterSearch`,
`complaint-history` `filters`, `task-action` `pastComplaintSearch`. **Drive filters by TYPING in the
test, never by asserting on code.** Fix them as you migrate — a shared grid that inherits a dead
filter is worse than the five broken ones.

### 2.2 Add Punjabi (`pa`) — DO IT
**Root cause is known and narrow.** `cms-backend/src/main/java/com/hrms/cms/entity/SupportedLocale.java`
lists only 10 entries and **`PA` is absent** — the last is `ML("ml", "Malayalam", "മലയാളം", false);`.
`TranslationService.getTranslationsForLocale` does
`SupportedLocale.isSupported(locale) ? locale : "en"`, so **`/translations/pa` silently returns
English** regardless of the database. Pass 1's claim that "pa is 100% identical to English" was an
artifact of comparing English to English.

- Add `PA("pa", "Punjabi", "ਪੰਜਾਬੀ", false)` to the enum.
- **65 `ui.*` keys already hold genuine Gurmukhi** seeded by pass 1 — verify they now surface.
- The other ~1806 pre-existing keys have **no `pa` rows at all** (only 65 of them do). Decide and
  state the fallback behaviour: an unseeded key must fall back to English, never render a raw code.
- There is an existing spec asserting `pa` is NOT served, at
  `e2e/ui-homogenisation/localisation.spec.ts` ("Punjabi is not served by the API even though rows
  exist — known gap"). **That test must be inverted, not deleted**, and `pa` added to
  `SERVED_LOCALES` in the same file.
- Confirm the frontend language switcher offers Punjabi once the API lists it.

### 2.3 Fix the FAQ raw-key defect — APPROVED
**Exactly 10 of 28 rows are broken.** Table is `faq` (NOT `faq_content`), columns `question_key` /
`answer_key`. Rows **id 1-10** store `faq.q1.question` … `faq.q10.question`; **zero** matching
`translation_keys` rows exist, so the pipe echoes the raw code to citizens. Rows 11-28 use
descriptive keys (`faq.q_what_is_scheme`, `faq.q_fee`, …) which **do** resolve — there are 48 `faq.*`
keys seeded.

Either seed the 10 missing keys or repoint the 10 rows at descriptive keys that exist. Whichever you
choose: **a corrective UPDATE is required in both MySQL and Oracle**, because seeders are
insert-if-absent by key code and can never fix rows that already exist. Then remove the
`faq.q<N>.question` allowance in `e2e/ui-homogenisation/shell-and-toast.spec.ts` so the raw-key
assertion becomes absolute. There is a pre-existing failing spec, `e2e/public/faq.spec.ts:223`
("no FAQ renders a raw translation key") — it must go green.

### 2.4 Complete status + complaint-category seeding, MySQL AND Oracle — APPROVED
**Status.** `rbio_status_master` has **34 rows**, but **13 status strings on 383 live complaints are
absent from it**: `forwarded` 156, `pending` 76, `awaiting_details` 59, `reviewer_review` 41,
`adjudicated` 20, `re_responded` 18, `returned` 7, plus 6 singletons (`awaiting_closure`,
`conciliated`, `forwarded_external`, `incharge_review`, `sent_to_other`, `under_review` — 1 each).
Seed them with correct `is_closed` semantics and role visibility. Pass 1 only made the *badge*
degrade gracefully; the vocabulary itself is still wrong.

**Categories — there are TWO tables and the citizen form reads the WRONG one. Fix that first.**

Measured 2026-09-22:

| table | rows | contents | entity / endpoint |
|---|---|---|---|
| `complaint_categories` | 10 | **The real RBI grievance categories** — ATM / Debit Card, Credit Card, Internet Banking, Mobile Banking / UPI, Loan / Advances, Deposit Accounts, Pension, Remittance / Transfer, Insurance, Others. Seeded 2026-06-26, all `active`. | `ComplaintCategory` → `GET /api/categories` |
| `category_master` | 14 | **100% test pollution.** Every row is named `S1 authority e2e probe`, every one `active = 0`, all created 2026-09-19 by an E2E run that did not clean up. **Not one real category.** | `CategoryMaster` → `GET /api/v1/masters/categories` |

Both are live code paths with their own entity, repository and controller, and **the frontend calls
both**: `public/file-complaint` uses `masters/categories` (the polluted table), `aa/aa-appeal-search`
calls both. So the citizen-facing complaint form currently sources its category list from a table
containing only test probes.

**Ruling to apply: `complaint_categories` is authoritative.** It holds the real data and its ten
values already line up with the ten `aa.ground.*` translation keys that are seeded. Therefore:
- Repoint `public/file-complaint` (and any other caller) at the authoritative source.
- **Purge the 14 test rows** from `category_master`, then either retire the table or make it a view of
  the same data. Do **not** leave two writable category tables — that is how they drift.
- Deleting a table is a schema change: propose it in the report, do not execute it unasked.
- **Never seed real master data alongside test residue.** Clean first, then seed.

Category labels are plain `name` / `category_name` strings with **no `label_key`**, so categories are
**not localisable today**. Add translation keys and wire them through the pipe as part of this item —
otherwise the ten categories stay English-only in an eleven-locale product.

Both changes need a MySQL migration **and** its Oracle counterpart. Free MySQL V-numbers:
**V60-V70, V77-V80, V88-V90, V93, V94**. Free Oracle: **V58, V60-V68** (Oracle tree numbers
independently; V57 was used by pass 1). Guard DDL on `information_schema` (MySQL 8.4 has no
`ADD COLUMN IF NOT EXISTS`) / `USER_TABLES` (Oracle), and make **every INSERT re-runnable** with
`WHERE NOT EXISTS` — `V5`/`V6` are already broken precisely because they lack this.

### 2.5 Two design aspects
**(a) Flow, look and feel** — item 0 above. This is the larger half.
**(b) Colour: use the RBI palette, referencing CRPC.** The token layer already carries CRPC's
measured values, so this is mostly done — **verify the tokens genuinely match the official RBI
palette** and correct the token values if they diverge. Because everything resolves through
`:root`, a palette correction is a **one-file change**; do not reintroduce per-module hex.

### 2.6 Navigation: left panel, menus by role — CONFIRMED
The shared shell already renders a left sidebar filtered by Keycloak realm role via
`shared/app-shell/shell-nav.ts`. Required now:
- Roll it out to **every** staff screen (currently 2).
- Verify each role sees **only** its permitted menus, and prove it by test per role.
- **State plainly in the report:** this is presentation only. A client-side menu decides what a user
  is *offered*, never what they may *do* — route guards and server `@PreAuthorize` remain the
  enforcement. Do not let a hidden menu item become the access control.

### 2.7 Compensation: ₹33 lakh combined — RULED, AND THIS IS A BEHAVIOUR CHANGE
**The ruling:** combined cap **₹33,00,000**, comprising **₹30,00,000 financial/consequential loss**
plus **₹3,00,000 for mental harassment**, all values **configurable**.

Current live state in `system_config` (enforced by
`cms-backend/.../service/RbioCompensationService.java`):

| key | current | required |
|---|---|---|
| `cms.rbio.compensation.max_consequential_loss` | 3000000 | 3000000 (unchanged) |
| `cms.rbio.compensation.max_time_harassment` | 300000 | 300000 (unchanged) |
| `cms.rbio.compensation.max_combined` | **3000000** | **3300000** |

So only the combined cap changes — but note the **two caps already sum to 3300000**, meaning the
current combined value of 3000000 makes the harassment allowance partly unusable: a complainant
awarded the full ₹30L financial could receive **nothing** for harassment. That is the defect the
ruling fixes. Change the config row **and** `DEFAULT_MAX_COMBINED` in the service (currently
`new BigDecimal("3000000")`, commented "Rs 30 Lakh"), in **both** MySQL and Oracle migrations, and
correct the misleading comments. **Add a test that the combined cap equals the sum of the two
component caps**, so this cannot silently drift again.

Also reconcile `RulesApiController:82-88`, which hardcodes ₹30L/₹3L into DRL strings. It is
**display-only** (no Drools session executes it), so it is a UI/enforcement divergence, not a second
enforcement path — but it will now display a figure that contradicts the configured cap.

### 2.8 FRC closure clauses — RULED, AND THE CLAUSES DO NOT EXIST YET
**The ruling:**
- Complainant has **not** filed with the RE first → close under **`10(1)(e)`**
- Complainant filed with the RE but raised the complaint **before 30 days** had elapsed →
  **`10(1)(g)`**
- The clause master must be **configurable**.

**Measured:** the table is **`closure_clause_master`** (there is no `clause_master`). It holds 13
rows — `15(1)(a)`, `15(1)(b)`, `16(1)`, `16(2)(a)`…`16(2)(h)`, `16(3)`, `16(4)` — and **no `10(1)(*)`
row of any kind.** Both required clauses must be **created**, not merely referenced. Useful columns
already present: `clause_code`, `label`, `label_key`, `category`, `scheme_version`,
`appealable_by_complainant`, `appealable_by_entity`, `effective_from` / `effective_to`, `active`.

Then replace the literal `"Clause FRC"` at
`cms-backend/src/main/java/com/hrms/cms/service/AutoClosureService.java:122` — it currently emits
that placeholder into **citizen-facing** text. Drive the clause from the master by the two conditions
above; do not hardcode either code in Java.

### 2.9 Clause labels must NOT be translated — RULED
Leave all 13 `clause.*` labels English-only. Do not machine-translate, and do not "helpfully" add
Hindi. A translated clause label is a legal statement about what the clause means.
**Ensure Punjabi support (2.2) does not sweep clause labels into a translation pass** — that is the
obvious way this ruling gets violated by accident. Add a guard test asserting `clause.*` keys have no
non-English translations.

### 2.10 AA award cap and sub-judice block — RULED: ARM THE CAP AT ₹30L + ₹3L
**The ruling:** the AA award cap mirrors RBIO exactly — **₹30,00,000 financial compensation** and
**₹3,00,000 mental-harassment compensation**, and both **must remain configurable** for future change.

Currently in `system_config`, deliberately inert: `cms.aa.order.max_award_amount = 0` (zero means
"unenforced") and `cms.aa.order.block_sub_judice = false`. Enforced by
`cms-backend/.../service/AaAppealOrderService.java` (`CFG_MAX_AWARD_AMOUNT` line 43,
`CFG_BLOCK_SUB_JUDICE` line 45).

Required:
- **Split the single cap into the same two components as RBIO**, because one `max_award_amount` cannot
  express a ₹30L + ₹3L rule. Mirror the RBIO key structure so the two tiers cannot drift apart:
  `cms.aa.order.max_financial_compensation = 3000000`,
  `cms.aa.order.max_harassment_compensation = 300000`, and a combined
  `cms.aa.order.max_combined = 3300000`.
- **Arm them.** The previous default of `0` meant no ceiling was enforced at all, so an AA order could
  exceed the statutory limit. Shipping `0` is no longer acceptable.
- Same mechanism as 2.7 throughout: `system_config` rows, documented Java defaults, MySQL **and**
  Oracle migrations, admin-visible configurability.
- **Add the same sum-consistency test** as 2.7 (`combined == financial + harassment`), and a test that
  an order above either component cap is refused.

**`block_sub_judice` is NOT covered by this ruling.** It remains `false` and config-OFF. Whether the
AA may proceed on a matter already before a court is a legal question that has not been answered —
do not arm it, and list it in the report as still awaiting legal sign-off. State explicitly in the
report what each of these values ships as, so nothing is armed by inference.

---

## 3. ENVIRONMENT (verified 2026-09-22; confirm before use)

- Backend **8092** is yours: `./deployment/run-test-backend.sh 8092 --restart`.
  **Two traps, both hit during pass 1:**
  - `--restart` stopped the old JVM and then reported *"a backend is already listening on 8092;
    reusing it"* against the dying process, leaving **nothing running**. Always confirm with
    `netstat -ano | grep 8092` **and** `curl /actuator/health`.
  - `target/classes` was **6 days older than 270 source files**. A stale JVM is indistinguishable
    from a missing endpoint — a new endpoint 404s until a real rebuild.
- Dev server **4202**; Keycloak **9090** (realm `cms`); MySQL `cms_db`
  (`/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe`, `cms_user/cms_pass`; `root/root` for DDL).
- **Never touch 8082 / 4200 (the user's) or 8096 / 8098 (other sessions').**
- **Run Playwright from `cms-portal-frontend`** or the chromium project is not found. A `cd` for a
  backend restart is enough to break it — this happened in pass 1.
- Set **BOTH** `UI_BASE_URL` and `APP_BASE_URL`; the config reads the former, 10 public specs read
  the latter. Setting only one produced 47 phantom `ERR_CONNECTION_REFUSED` failures.
- **Never restart the backend while a suite is running** — it voided pass 1's re-portal baseline.
- Schema is Hibernate `ddl-auto`, not Flyway; migrations are applied by hand.

---

## 4. BASELINE YOU MUST NOT REGRESS

Measured by pass 1 **after** its changes, on a fresh backend. **Re-measure before changing anything**
— a regression only means something against a baseline you took yourself.

| dir | passed | failed | skipped |
|---|---|---|---|
| admin | 106 | 0 | 9 |
| public | 107 | 4 | 0 |
| i18n | 9 | 0 | 0 |
| aa | 159 | 19 | 23 |
| cepc | 27 | 2 | 6 |
| rbio | 182 | 3 | 11 |
| re-portal | 29 | 2 | 39 |
| ui-homogenisation | 21 | 0 | 0 |

**Backend: 1310 tests, 0 failures, 0 skipped.** `mvn -q test` suppresses the summary — aggregate
`target/surefire-reports/*.xml`.

**Known pre-existing failures, and which of them THIS batch should fix:**
- `public` 4: three are `eligibility.block_not_filed` baking clause `10(1)(j)` into prose where the
  seeder expects a `{{clause}}` placeholder; one is the FAQ raw-key defect. **The FAQ one must go
  green (2.3).** The clause-interpolation ones are adjacent to 2.8 — fix if the clause work reaches
  them, and say so either way.
- `aa` 19: cluster in `backlog-traceable`, `order`, `hearing`. **2 of them** are `file-appeal` tests
  passing a `backdateDays` option that `advanceToStatus` **does not implement**, so closure is never
  backdated and the eligibility window never elapses. Implementing that option recovers both.
- `re-portal` **skips 39 of 70** — the worst ratio in the repo. A skip is **not** a pass. Report
  skips separately in every table.

**Harness traps that produce green-but-meaningless results:**
- `auth.ts:isKeycloakAvailable()` returns false on ANY throw and callers `test.skip()`, so a Keycloak
  outage yields a **GREEN VACUOUS** staff suite.
- A spec importing `test` from `@playwright/test` instead of `../fixtures` reads the user's 8082.
- `e2e/rbio/harness.ts:35` hardcodes origin `:4200` **deliberately** to assert CORS — leave it.
- **No tautologies**: `expect(x || true)`, `if (!('error' in result))`, status sets accepting 404 as a
  pass. These were purged once; reintroducing them is worse than having no test.
- Never `--headed` for bulk runs; `workers: 1` is intentional.

**Extend `e2e/ui-homogenisation/`** (21 tests today, all passing) with: the shared grid renders and
its column filters work **when driven by typing**; the detail frame renders identically across
modules; role-based menus show only permitted items **per role**; `pa` returns Gurmukhi not English;
no raw translation key renders anywhere (no FAQ exemption); the combined compensation cap equals the
sum of its components; `clause.*` keys have no non-English translations.

---

## 5. REPORTING — PASS 1'S REPORT WAS THE WEAKEST DELIVERABLE. FIX IT.

Pass 1 produced `D:\screenShots\ui-homog\index.html` with **only 36 before/after pairs from 240
screenshots**, because pairing keyed on the Playwright output-directory name and any renamed or
newly-passing test had no counterpart. **Most screens have no image.** That is the single biggest
complaint about the last deliverable.

**Requirements this time:**

1. **Capture screenshots deliberately, do not scavenge them.** Add an explicit visual-capture spec
   that navigates to **every migrated screen** — each module's task grid, each module's complaint
   detail, each modal — and saves a **named, stable** screenshot (e.g. `grid-rbio.png`,
   `detail-aa.png`). Stable names are what make before/after pairing reliable. Run it **before** any
   change for BEFORE images, and again after.
2. **Capture at 1280px and 1440px**, and verify nothing clips or scrolls horizontally at 1024px.
   Pass 1 claimed responsive behaviour without photographing it.
3. **Authenticate for staff screens.** Most staff grids were never photographed in pass 1 because the
   browser was unauthenticated and redirected. Use `loginAsCepcRole` / `loginAsRbioRole` /
   `loginAsReRole` / `loginAsAaRole` from `e2e/utils/auth.ts`. For **citizen** routes use
   `loginCitizen` + `seedCitizenSession` — and note `/appeal` is really **`/public/appeal`**, a child
   of `public`; pass 1 lost 8 tests to that single wrong URL.
4. **State the ceiling honestly.** Only ~354 of 752 tests touch `page.`; the other ~398 are pure API
   tests and **cannot** yield a screenshot (`e2e/admin/` produced 0 PNGs from 106 passing tests). Say
   so plainly rather than implying every test has an image.
5. **Report a coverage number**: "N of M migrated screens have both a before and an after image", and
   **list the ones that do not, with the reason.** A missing screenshot must be explained, not
   omitted.

Deliver a **self-contained** `D:\screenShots\ui-homog-pass2\index.html`: embedded CSS, no CDN,
**relative** image paths so the folder zips. Sections:
1. Verdict; per-module pass/fail/**skip** vs the item-4 baseline, regressions called out.
2. Before/after side by side, per module and per screen, at both widths.
3. Screenshot coverage table (point 5) — **required**.
4. Grid/detail consolidation: components before → after, what is now shared.
5. Colour: residual hex per module incl. `public`/`admin`; confirmation tokens match the RBI palette.
6. Localisation delta: `| translate` per module, new keys, **`pa` proven working**, and confirmation
   clause labels remain untranslated.
7. Role-menu matrix: role × visible menus, with the enforcement caveat from 2.6.
8. Master data: statuses and categories seeded, MySQL **and** Oracle, replay-verified.
9. Statutory config: compensation ₹33L split, AA cap/sub-judice, what is default vs armed.
10. Accessibility: contrast on new surfaces, focus, `<th scope>`, modal focus-trap/Escape.
11. Regressions: pre-existing vs newly introduced, per item 4.
12. Defects found and NOT fixed, with reasons.
13. Run commands, working credentials, and a 10-minute per-module smoke walkthrough.

**Commit the generator script.** Extend `scripts/qa/ui_homog_report.py` (already committed) rather
than writing a second one.

---

## 6. HOW TO WORK

- **Do not `git commit` or `git push`.** The user reviews the diff, then pushes. (Standing rule; a
  hook enforces it regardless.)
- Work in **`CMS2.0_Redzone/`**, never `CMS2.0/`. `git status` shows **~2076 phantom deletions** of
  `CMS2.0/` — **expected; never commit them, never `git clean`.** 454 files (incl. `cms-mail-intake`
  and the assignment rules engine) exist only there. Stage your files **by name**; never `git add -A`.
- The tree may hold other sessions' uncommitted work. `git status --porcelain` first; **never revert
  or tidy files you did not touch.**
- Chokepoints, one editor at a time: `src/styles.scss`, `src/_primitives.scss`, `app.routes.ts`,
  `e2e/utils/test-data.ts`, anything under `shared/`.
- Edit existing files; no parallel "v2" components, no backwards-compat shims — change the callers.
- Default to **no code comments**; add one only where the WHY is non-obvious.
- **When a test catches your regression, fix the code, not the test.** Pass 1 changed a CEPC heading
  and its test caught it; the heading was reverted. Conversely, when a test asserts a value the
  product owner has now overruled (as with the 2 MB upload default), **update the assertion and say
  so in the report** — do not revert the ruling to keep a test green.

---

## 7. DEFINITION OF DONE

- [ ] One shared task-grid component, used by crpc, cepc, rbio, aa, re-portal.
- [ ] One shared task/complaint detail frame, used by all module detail views.
- [ ] `app-shell` adopted by **every** staff screen (from 2 today), left-panel nav filtered by role.
- [ ] `public/` and `admin/` tokenised; residual hex reported per module with justification.
- [ ] Dead `computed()` column filters fixed, proven by typing in tests.
- [ ] `pa` in `SupportedLocale`; Gurmukhi served; the "not served" spec inverted; switcher offers it.
- [ ] FAQ rows 1-10 resolve; `e2e/public/faq.spec.ts:223` green; raw-key assertion absolute.
- [ ] 13 missing statuses seeded; category master resolved and de-polluted; MySQL **and** Oracle,
      both replay-verified re-runnable.
- [ ] Combined compensation ₹33,00,000 (₹30L + ₹3L), configurable, with a sum-consistency test.
- [ ] `10(1)(e)` and `10(1)(g)` created in `closure_clause_master`; `"Clause FRC"` literal gone.
- [ ] Clause labels still English-only, guarded by a test.
- [ ] AA award cap + sub-judice block configurable like compensation; default stated explicitly.
- [ ] `ng build` clean; backend **1310/0**; E2E no worse per directory, skips reported separately.
- [ ] Named before/after screenshots for every migrated screen at 1280 and 1440, with a coverage
      table listing any gaps and why.
- [ ] `D:\screenShots\ui-homog-pass2\index.html` with all 13 sections; generator committed.

---

## 8. STOP AND ASK RATHER THAN GUESS

Items 1-10 are settled — **do not re-ask them.** In particular: the category question is answered
(`complaint_categories` is authoritative, 2.4), and the AA cap is answered (arm at ₹30L + ₹3L,
configurable; `block_sub_judice` stays OFF, 2.10).

Ask only if you hit:
1. A case where the RBI palette and CRPC's measured colours **genuinely conflict** — the palette wins,
   but confirm the intended value rather than inventing one.
2. **Retiring or dropping `category_master`** — propose it, do not execute a schema drop unasked.
3. Any point where making the UI uniform would require a **functional/workflow change** — stop and
   report rather than changing behaviour to suit the layout.
4. Anything that would require translating a **clause label** (2.9 forbids it) or arming
   `block_sub_judice` (2.10 forbids it).
