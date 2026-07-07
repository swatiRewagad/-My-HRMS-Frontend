# Citizen Complaint Portal — Audit & Enhancement Prompt (for Claude Code)

*System: Spring Boot + Angular + Oracle. Citizens lodge complaints (mobile + SMS OTP login); an internal RBI officer workflow then assigns, tracks, and closes them. The application is already built and in use — every change below must be additive and non-breaking.*

## How to use this document
Paste this entire document to Claude Code as one task brief. It is deliberately organized as a sequence of phases with explicit checkpoints — do not treat it as one unbroken task. Implement a phase, test it, verify it, commit it, then move to the next. If a session's context runs out mid-task, the living documents described below are the recovery mechanism: a fresh session should read them before resuming.

**Phases at a glance:** Phase 0 Discovery & Baseline → Phase 1 OTP/CAPTCHA/Cooloff → Phase 2 Anti-Automation → Phase 3 PII Encryption → Phase 4 Multi-Language (10 languages) → Phase 5 Asset Performance → Phase 6 Mobile-Responsive → Phase 7 Geo-Location Notice → Phase 8 Citizen-Facing Performance Audit → Phase 9 TAT Timer → Phase 10 Officer Dashboard → Phase 11 Similar-Cases Widget → Phase 12 WCAG 2.1 AA Audit → Phase 13 Final Adversarial Verification.

---

## Prime Directives (apply to every phase)

1. **This is a live system.** At the end of every phase, the application must build, deploy, and pass its full test suite, and every pre-existing citizen and officer journey must still work end-to-end. No exceptions.
2. **Audit before you touch anything.** For each phase, before writing code: read the relevant existing code paths and reuse existing conventions (naming, package structure, migration tooling, auth/session handling, error handling, logging, config). Don't introduce a second, competing pattern for something that already has one.
3. **No silent scope creep, no silent scope-shrinking.** If a requirement is ambiguous, pick the safest reasonable default, implement it, and log the decision + rationale in `DECISIONS.md`. Do not quietly skip or reinterpret a requirement. The **"Decisions to confirm"** list at the end of this document are hard stop-and-ask points — not defaults.
4. **Configuration over hardcoding.** Every tunable value below (cooloff durations, refresh intervals, thresholds, cache TTLs) lives in one consistent, centrally-managed place — never a magic number in code.
5. **All schema changes go through the existing DB migration tool** (Flyway/Liquibase/other — identify in Phase 0). No hand-run DDL.
6. **Test everything you add**, with real automated tests, especially for the adversarial scenarios called out per-phase.
7. **Checkpoint discipline:** commit in small logical units within a phase; tag the end of each completed-and-verified phase (e.g. `git tag phase-3-complete`); never force-push or rewrite shared history.
8. **WCAG 2.1 level AA** is the target conformance level for every new or modified UI element, built in as you go — not deferred. Phase 12 is a final sweep and regression gate, not the first time accessibility is considered.

## Suggested tooling (use what's already in place first; only add new dependencies where nothing already fits)
- Backend unit/integration: JUnit 5 + Mockito
- Frontend unit: Jasmine/Karma (Angular CLI default) or Jest if already in use
- End-to-end: Playwright or Cypress
- Accessibility: axe-core / pa11y / Lighthouse CI
- Load/soak testing: JMeter or Gatling (needed for Phase 8 and the Phase 10 cache-under-load check)

## Living documents — create at repo root in Phase 0, update continuously
- `AUDIT.md` — what you found about the existing system, as you touch new areas.
- `PLAN.md` — the phase breakdown below plus a live status column (Not started / In progress / Complete & verified).
- `DECISIONS.md` — every judgment call/assumption, what you chose, and why. Gaurav reads this, not the full diff.
- `VERIFICATION.md` — what was tested per phase and the result; the full adversarial report lives here at the end (Phase 13).

**Before starting Phase 1: present the full `PLAN.md` (phases, dependencies, risk flags, and the "Decisions to confirm" list) and stop for explicit approval.** After that, proceed phase by phase autonomously, but pause and ask if you hit a listed stop-and-ask point or anything of comparable weight (a new datastore, a breaking API change, a security tradeoff not covered below).

---

## Phase 0 — Discovery & Baseline

**Objective:** Understand the system well enough that every later phase is additive, not destructive.

**Do:**
- Map: citizen OTP auth flow, officer auth (likely SSO/AD/LDAP — confirm, don't assume), session management, the existing workflow/case-assignment engine, DB schema + migration tooling, test suite + CI, Angular build/deploy pipeline, logging/audit conventions, config mechanism.
- Specifically check whether **TAT/SLA-by-category is already defined anywhere** in the workflow engine — for regulatory reasons this is likely, even if not yet surfaced in the UI. This materially affects Phase 9.
- Run the existing test suite and record the baseline pass/fail state (pre-existing failures aren't yours to fix, just don't obscure them).
- Capture baseline performance metrics (bundle size, Lighthouse/perf score, key page load times) for later comparison in Phase 8.
- Set up a git branching strategy for this work.

**Definition of done:** `AUDIT.md` written, `PLAN.md` drafted, baseline metrics recorded, plan presented for approval.

---

## Phase 1 — Citizen Login Security: OTP, Self-Hosted CAPTCHA, Cooloff, Email Fallback

**Objective:** Harden citizen OTP login without breaking the existing flow.

- Audit the current OTP flow end-to-end before changing it.
- OTP **resend** gated by a CAPTCHA generated entirely server-side — no third-party service, no client-only check that can be bypassed by calling the API directly.
- **The CAPTCHA needs a non-visual accessible alternative** (audio challenge, or a simple server-generated logic/arithmetic challenge with audio). A visual-only CAPTCHA would lock blind citizens out of resending an OTP at all — not optional given the global accessibility directive.
- On incorrect OTP, apply a **cooloff keyed on (IP + browser fingerprint)** — use a server-issued, signed, HttpOnly cookie as the fingerprint, never a client-supplied value. Handle X-Forwarded-For correctly if there's a load balancer in front (audit actual infra).
- Cooloff must **extend progressively** (exponential backoff, configurable ceiling) and be **stored centrally in Oracle, not in-memory** — if this runs on more than one instance/pod (check in Phase 0), an in-memory lockout is trivially bypassed.
- Also rate-limit by **mobile number independently** of IP/browser — you need both: one control for an attacker targeting a single number across rotating IPs, another for an attacker spraying many numbers from one origin.
- Add a **"send OTP to email instead"** option. First audit how citizen email is currently captured/verified in the data model — if it's only entered fresh and unverified at complaint time, that's a decision point (see stop-and-ask list), not something to silently trust as an auth channel.
- Apply the same rate-limiting to the email-OTP path too, or it becomes the bypass for everything you just built.
- While you're in this code, audit and fix if needed: OTP expiry window, one-time-use enforcement, resend cap per rolling window, cryptographically secure random generation.

**Definition of done:** existing login flow still works; tests cover correct OTP, incorrect OTP → cooloff triggers and extends, cooloff expiry, CAPTCHA required on resend, CAPTCHA accessible alternative works, email fallback works and is itself rate-limited; multi-instance correctness verified.

---

## Phase 2 — Anti-Automation / Scripted-Attack Defense

**Objective:** Make the public login + complaint-lodging surface resistant to bot abuse, on top of Phase 1's infrastructure.

- Reuse Phase 1's fingerprint + rate-limit infrastructure rather than building a parallel system.
- Layer in: request-velocity anomalies, honeypot form fields, submission-timing checks (filled faster than humanly possible), header/user-agent anomaly checks.
- On a heuristic trip, escalate to Phase 1's CAPTCHA/step-up challenge before hard-blocking — minimize false positives against real citizens on shared/NAT'd mobile IPs.
- Log flagged activity for security review, not just a silent drop.
- **Check first whether a WAF/API gateway already sits in front of this app** (likely in a bank environment) — some of this may belong there instead of, or in addition to, the app layer. Note the finding either way.

**Definition of done:** automated tests simulate rapid-fire OTP requests, honeypot-field submission, and abnormally fast form completion, and confirm each is caught without blocking a normal human-paced session.

---

## Phase 3 — PII Field-Level Encryption (Per-Session Envelope Encryption)

**Objective:** Encrypt citizen-entered PII (name, email, phone, account number) at the application layer, transparently, on top of HTTPS.

Context worth being explicit about: HTTPS already encrypts the wire. This control is defense-in-depth beyond TLS — e.g. plaintext visibility once TLS terminates at a load balancer, or in request logs — not a replacement for TLS or for normal input validation, both of which still apply.

- Audit the existing session mechanism first and reuse it — don't build a second one alongside whatever JWT/SAML patterns are already in place elsewhere in the bank's systems.
- Design: a per-session AES-256-GCM key established at session start. Recommended: **derive it deterministically server-side from (session ID + a server-side secret) via HKDF**, rather than generating and storing a separate secret per session — avoids a new secret-storage/lifecycle problem. The client fetches it once at session bootstrap over the already-authenticated channel; it never has to be "managed."
- Client-side (Angular): use the browser's native Web Crypto API to encrypt the PII fields before submission.
- Server-side: a filter/interceptor early in the request pipeline detects the encrypted fields, resolves the session's key, decrypts, and hands plain values to the controllers exactly as today — **zero changes below that layer**, per your requirement.
- Invalidate/rotate the key on logout and session expiry. Decryption failures must fail safely — no stack traces or key material in any response or log.
- **Flag, don't silently skip:** confirm whether PII also needs encryption **at rest** in Oracle (TDE or column-level) — a natural companion to in-transit encryption and likely already expected in a banking-adjacent context. Record the current at-rest state in `AUDIT.md`.
- **Flag for compliance, not something to resolve in code:** India's DPDP Act rules were notified in November 2025 and are phasing in on an 18-month timeline, with full compliance expected by mid-2027 — 2026 is broadly treated as the year organizations need their data-protection posture in shape. Worth a quick check with legal/compliance that this encryption approach, and the retention/logging choices here and in Phase 7's geo-log, line up with however DPDP compliance is being tracked elsewhere in the bank. A pointer, not legal advice.

**Definition of done:** PII fields are ciphertext on the wire (verify in a network tab, not just by reading the code); a valid session decrypts its own data; ciphertext from another session fails to decrypt; tests include a tamper test (mutate ciphertext, expect clean rejection) and a cross-session isolation test.

---

## Phase 4 — Multi-Language Platform (10 Languages, DB-Driven i18n)

**Objective:** English + Hindi, Bengali, Marathi, Telugu, Tamil, Gujarati, Urdu, Kannada, Malayalam — fully DB-driven, cached, covering labels/messages/tooltips/static text on both front end and back end.

- **Schema:** you described "adding a language column." Recommended instead: a **normalized schema** — a message-key table (key, module/context, description) plus a translations table (key, language_code, value) with a unique constraint on (key, language_code). Adding a language then means *inserting rows*, not an `ALTER TABLE` — zero downtime, zero code change, and it satisfies "adding a language should just work" more directly than a wide table would. If you want the *editing experience* to look like a spreadsheet with one column per language, build that as an admin UI view over the normalized tables. This is a recommendation — flag it as a decision to confirm if you'd rather keep the literal wide-table design.
- Use structured keys with a module prefix (`LOGIN.OTP_PROMPT`, `DASHBOARD.WEEKLY_STATS_TITLE`), not raw English text as the key — avoids collisions and makes missing-translation audits meaningful.
- **Cover both layers.** Angular-side labels/tooltips/messages AND Spring Boot-side messages (validation errors, exceptions, anything the API returns as text). Implement a custom `MessageSource` backed by the same DB table so backend-originated text is localized too — this is the piece most i18n retrofits miss.
- **Cache**, loaded from DB — but define invalidation explicitly. If this runs on more than one instance, a purely local cache means different users see different text after an edit until each instance's cache separately expires. Plan for it (short TTL, or an admin "publish" action that triggers refresh across instances).
- **Urdu is right-to-left.** Not just text direction — mirrored layout, `dir="rtl"`, form alignment, icon placement. Treat it as a distinct layout mode to test, not just another translation set.
- **Fonts:** several of these scripts (Devanagari, Bengali, Tamil, Telugu, Kannada, Malayalam, Gujarati, Urdu/Nastaliq) need their own web fonts to render at all — load the font for the active language rather than shipping all ten upfront (matters for Phase 5 too).
- **Locale-aware date/number formatting** is separate from label translation — register Angular locale data for all ten locales.
- **Missing-translation fallback:** selected language → English → visible key marker in non-prod (for QA), never a blank label.
- **Scope boundary, stated explicitly:** this covers UI chrome (labels, messages, tooltips, static text, backend-originated messages) — it does **not** mean machine-translating citizen-entered free-text complaint descriptions. Different feature, out of scope here.
- Draft translations for all ten languages to the best of your ability, but **mark this content as a first draft requiring native-speaker review** before being treated as authoritative — especially anything touching legal, banking, or procedural terminology, where a wrong translation is worse than none. Log the keys you're least confident about in `DECISIONS.md`.
- Language switcher: accessible (keyboard-operable, proper ARIA), remembers the citizen's choice, applies without a jarring full reload where feasible.

**Definition of done:** switching language changes every label, message, tooltip, and backend-originated error string on every touched page; adding a translation row for a dummy eleventh language requires zero code deployment; RTL verified visually for Urdu; missing-key fallback verified.

---

## Phase 5 — Static Asset Performance (Compression, Minification, Caching)

**Objective:** Faster loads, correctly cached, without ever serving a stale asset after a deploy.

- Verify the Angular build actually used in deployment is the production configuration (minified, tree-shaken, no source maps, **output hashing enabled** on filenames) — a surprisingly common real-world gap is a dev-mode build reaching production.
- Enable gzip/brotli at whatever serves the app (embedded Tomcat config and/or any reverse proxy/gateway — audit what's actually there).
- **Correction to the literal ask, worth being upfront about:** a blanket 30-day cache on everything is dangerous — if `index.html` is cached for 30 days, citizens can get stuck on a broken or stale version for a month after every release. The safe pattern: long-cache (30 days, immutable) only for content-hashed assets (JS/CSS/fonts/images with a hash in the filename, which Angular's production build already supports), and **short/no-cache on `index.html`** so it always points at the latest hashed bundle. This delivers the performance win without the deployment foot-gun.
- Modern image formats with fallback (WebP/AVIF), responsive `srcset`, lazy-loading below the fold.
- Confirm HTTP/2 or later wherever this is served in production.

**Definition of done:** repeat visits show cache hits for hashed assets (verify in network tab) while a fresh deploy is picked up immediately via `index.html`; Lighthouse/perf score improved over the Phase 0 baseline.

---

## Phase 6 — Mobile-Responsive Citizen Experience

**Objective:** On mobile, citizens see only login and complaint-lodging — the desktop header/hero/imagery shouldn't compete for the screen.

- You floated two approaches — a genuinely separate mobile experience, or one page adapting by device signal. **Recommendation: responsive CSS (mobile-first breakpoints) over server-side header/user-agent sniffing** — UA sniffing is brittle (breaks on new devices, misses tablets/foldables) and fights the grain of a modern Angular SPA. Treat header-based detection as a decision point if you have a specific reason to prefer it; default to responsive otherwise.
- On small viewports, collapse/hide the hero imagery and secondary navigation entirely; keep login and complaint-lodging as the only prominent flows.
- **Cross-check against Phase 4:** Indian-language strings often run longer than their English equivalents. Test mobile layouts with the *longest* expected translated string per label, not just English, or you'll get overflow surprises only in specific languages.
- Standard hygiene: correct viewport meta tag, no horizontal scroll, adequate touch target sizing.

**Definition of done:** verified on real narrow viewports in English and at least one long-script language; desktop experience unaffected.

---

## Phase 7 — Geo-Location Notice & Logging

**Objective:** Non-blocking notice if traffic looks like it's from outside India; log it.

- **Recommendation:** a locally-hosted/offline IP-geolocation database queried server-side, refreshed periodically, rather than calling a third-party geo-IP API on every page load — avoids sending citizen IPs to an external party on a regulator-adjacent platform, and removes a runtime dependency. Flag as a decision point if you'd rather use an external API for simplicity.
- **Informational only** — a small notice, never a block. Make this explicit in the implementation so it can't quietly evolve into a hard geofence.
- Log IP, resolved location, and timestamp with the same access-control and retention discipline as your other PII/security logs — this is itself sensitive data.
- Note that geo-IP accuracy is inherently best-effort (VPNs/proxies) — acceptable for a soft notice.

**Definition of done:** notice appears for simulated non-India traffic, doesn't appear for India traffic, never blocks either way, log entries are queryable.

---

## Phase 8 — Citizen-Facing Performance Audit

**Objective:** Only after Phases 1–7 are complete and verified — profile the whole citizen-facing surface.

- Compare against the Phase 0 baseline (bundle size, Lighthouse/perf score, page load, TTFB).
- Bundle analysis: heavy/duplicate dependencies, lazy-loaded Angular feature modules, `OnPush` change detection where applicable.
- **Specifically re-verify Phase 4's translation cache is actually preventing a DB round-trip per label per request** — a broken or misconfigured cache here is a classic, easy-to-miss regression from this exact project.
- Index review on every table added in Phases 1–7 (OTP attempt log, session-key material, geo-log, translation tables) — new tables without the right indexes are the other classic self-inflicted regression.
- Written before/after report in `VERIFICATION.md`.

---

## Phase 9 — Per-Task TAT Timer (Officer Workspace)

**Objective:** When an officer opens an assigned case, show a live timer against the average time for that type of case, warning as it nears average.

- **Before building anything: check whether TAT/SLA-by-category already exists in the workflow engine.** RBI's own grievance-redress framework is explicitly time-bound by complaint type — some categories are meant to resolve within a single working day, more technical ones get a longer window. Given this system already knows its own workflow, a notion of TAT likely exists somewhere for regulatory/reporting reasons. If so, **reuse that definition — don't invent a second, competing one** that could quietly diverge from what the compliance/reporting side already relies on. Record what you find in `AUDIT.md`; treat a conflict here as a stop-and-ask point.
- Worth knowing while you're auditing this: RBI's new Reserve Bank – Integrated Ombudsman Scheme (RB-IOS), 2026 takes effect today, 1 July 2026, replacing the scheme that's been in place since 2021. If this platform's complaint categories or TAT figures are meant to track the regulatory scheme at all, it's worth flagging to Gaurav whether anything needs to move in step with the new scheme rather than the old one — surface it, don't silently reconcile it.
- Compute "average" **per complaint category/type**, not one global figure — different categories plausibly have very different typical durations. Use a rolling window of recently closed cases with basic outlier trimming so a few extreme delays don't distort the threshold for everyone else.
- **Flag as a decision to confirm:** wall-clock time or business-hours/working-days only? Time-bound frameworks in this space often run on working days, which materially changes both the average calculation and what "nearing average" means — don't default silently either way.
- Warning thresholds should be configurable, not hardcoded.
- Implementation: a lightweight client-side ticking display computed from a fixed server-provided start timestamp — no per-second server polling.
- **Accessibility:** don't signal the warning state with color alone (e.g. just turning the timer red) — pair it with an icon and text.

**Definition of done:** timer displays correctly, warning triggers at the configured threshold, category-specific averages verified against seeded historical data, no server polling under the hood.

---

## Phase 10 — Officer Dashboard

**Objective:** A low-level officer, on login, sees a dashboard oriented around what they need to do next.

- Direct link to their last draft/WIP case.
- Direct link to their most urgent priority/TAT-nearing-expiry case (reuses Phase 9's per-category TAT logic).
- Link to any newly assigned case.
- Weekly performance stats: cases assigned, in progress, closed. Define the week boundary explicitly (calendar week vs. rolling 7 days) and be consistent.
- **Refresh every 10 minutes (configurable), no manual refresh.** Read this as intent, not just a UI constraint — if you only hide the refresh button but the dashboard still fetches fresh data on every plain browser reload (F5), an officer can bypass the limit trivially, defeating the actual goal (reducing server/DB load across a large distributed officer base). **Implement server-side response caching per officer for the configured interval**, so even a manual reload gets the same cached data until the interval elapses, in addition to not exposing a refresh control in the UI.
- **Accessibility:** the dashboard updates in the background on a timer — make sure a screen-reader user is actually told when content refreshed (a live region/status message), since a silent data swap is itself an accessibility gap.

**Definition of done:** all four widgets show correct, officer-scoped data; refresh cadence verified to be genuinely server-enforced, not just UI-hidden; changing the configured interval changes the observed cadence.

---

## Phase 11 — Similar-Cases Widget (Case Detail View)

**Objective:** A space-constrained widget with two distinct purposes. Given the limited space, prioritize a compact, glanceable design — push detail into the click-through interactions rather than cramming it into the panel itself.

### 11A — Same-Citizen History
- Match other cases by the current case's citizen on **exact name + email**, normalized for whitespace/case before comparing, so trivial formatting differences don't cause false negatives.
- Compact list, each entry with an open/closed indicator dot — don't rely on color alone; pair it with a text label or icon for screen readers.
- Click → an animated dialog with only the essentials: case ID, filed date, status, resolving action/outcome, closed date if applicable. Respect `prefers-reduced-motion` for the animation.

### 11B — Algorithmic Similar Cases + "More…"
- You asked for this without AI/ML — that maps cleanly onto classical, non-ML information retrieval: term-frequency-based document similarity. If Elasticsearch (ELK community edition) is feasible here, its built-in `more_like_this` query does exactly this (TF/IDF similarity, no embeddings, no model). **Flag before building:** Elasticsearch means new infrastructure in a bank environment — a new datastore to secure, back up, and get through whatever review process exists — confirm feasibility with Gaurav before committing, don't treat it as a pure coding decision.
- **No-new-infrastructure fallback:** since the backend is already Oracle, **Oracle Text** can provide similar full-text scoring within the existing database, avoiding a new datastore entirely. Use this if ELK isn't approved or available, and log the choice either way.
- Decide which fields feed the similarity match (category/subcategory, description text, structured fields like product type or region) based on what actually exists and is meaningful — audit the schema rather than guessing, and log the choice.
- Configurable minimum score threshold; a compact visual score meter (not just a number) plus an accessible text equivalent ("High match") for screen readers; cap at 5 results.
- **"More…"** opens a modal with a left/right split — left shows up to 10 similar cases, right shows case detail. **Reuse the existing case-detail modal component**, don't rebuild it. Keyboard navigation between list and detail, proper focus trap, focus returned to the trigger element on close.

**Definition of done:** same-citizen matches verified against seeded duplicate-citizen data; similarity scores verified against seeded near-duplicate and unrelated cases, correct at the threshold boundary; "More…" modal fully keyboard-navigable.

---

## Phase 12 — WCAG 2.1 AA Comprehensive Audit

**Objective:** A final sweep and permanent regression gate — not the first time accessibility was considered; it was built into every phase above.

- Automated scanning (axe-core / pa11y / Lighthouse accessibility) across citizen and officer surfaces, wired into CI so this doesn't silently regress later.
- Manual pass: full keyboard-only walkthrough of the citizen complaint flow and officer dashboard/case flow; a screen-reader spot check; site-wide color-contrast audit (4.5:1 body text, 3:1 large text/UI components); visible focus indicators everywhere; every form input correctly labeled with errors correctly associated to their field.
- Specifically re-verify: Phase 1's CAPTCHA accessible alternative works end-to-end for a screen-reader-only user; Urdu RTL doesn't break anything built in later phases; Phase 10's live-region announcement actually fires; Phase 11's modal focus-trap actually works.

**Definition of done:** no critical/serious automated findings outstanding; manual walkthroughs documented in `VERIFICATION.md`; CI gate in place.

---

## Phase 13 (Final) — Adversarial End-to-End Verification & Sign-off

**Objective:** Actively try to break what you built — don't just confirm it works when used as intended.

- Full regression: existing suite + everything added in Phases 1–12.
- End-to-end coverage (Playwright/Cypress, or whatever e2e setup already exists): citizen happy path in at least three languages including Urdu; the OTP-failure → cooloff → email-fallback path; officer dashboard, TAT timer, and similar-cases flows; a mobile-viewport pass.
- **Adversarial self-review — actually attempt each of these, don't just reason about them:**
  - Bypass the CAPTCHA by calling the resend endpoint directly.
  - Submit unencrypted PII straight to the API, bypassing the Angular client — confirm the server rejects it rather than silently accepting plaintext where ciphertext is expected.
  - Tamper with or replay the browser-fingerprint cookie to evade the cooloff.
  - Use one session's decrypted PII/key material against another session.
  - Basic XSS/SQLi payloads through every newly-touched PII field, even though they're encrypted in transit — encryption is not input validation.
  - Clear cookies / rotate within the same subnet to evade rate limiting; confirm the mobile-number-based limit from Phase 1 still catches it.
  - Load/soak-test the dashboard refresh mechanism to confirm Phase 10's server-side cache actually holds under concurrent officer load.
- Performance re-check against the Phase 0 and Phase 8 baselines.
- Final accessibility automated run.
- Write the final `VERIFICATION.md`: what was tested, results, known limitations, and a consolidated pull of everything logged in `DECISIONS.md` for Gaurav's review in one place.

---

## Decisions to confirm with Gaurav — do not silently default on these

1. i18n schema: normalized key/language table (recommended) vs. the literal wide, column-per-language design.
2. TAT clock basis: wall-clock vs. business-hours/working-days — and whether a TAT/SLA definition already exists elsewhere in the workflow engine that must be reused instead of built fresh.
3. Mobile detection: responsive CSS (recommended) vs. server-side header/UA-based detection.
4. Geo-IP data source: local/offline database (recommended) vs. third-party API.
5. Whether introducing Elasticsearch is feasible/approved in this environment, or the Oracle Text fallback should be the primary approach from the start.
6. How citizen email is currently sourced/verified, before designing the email-OTP fallback UX around it.
7. Whether PII needs encryption at rest (Oracle TDE/column encryption) in addition to the in-transit, per-session encryption in Phase 3.
8. Confirmation that "no manual refresh" on the officer dashboard should be enforced server-side (cache-backed), not just via a hidden UI button.

---

## A note on running this across sessions

Phases 0–8 (citizen-facing) and Phases 9–13 (officer-facing + final verification) touch almost entirely different parts of the codebase. If a single session risks running long enough to lose context, it's safe to split these into two parallel sessions/branches — as long as both start from the same Phase 0 audit findings and are reconciled and regression-tested together before Phase 13.
