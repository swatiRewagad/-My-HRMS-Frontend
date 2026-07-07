# RBI CMS 2.0 — Intelligence & Analytics Enhancement Prompt (for Claude Code)

*Stack: Java / Spring Boot / JPA / Oracle, with Hazelcast already in place for distributed caching. The complaint portal, the 10-language DB-driven i18n platform, and the officer workflow already exist and are live. Every change below is additive and must not degrade the existing system or its performance.*

*Regulatory basis: the **Reserve Bank – Integrated Ombudsman Scheme (RB-IOS), 2026**, effective 1 July 2026, replacing RB-IOS 2021. Several features below encode rules directly from this Scheme — treat the Scheme text (and legal/compliance sign-off) as the source of truth, not developer assumption. The public FAQ is guidance only and explicitly cannot be relied on as legal authority.*

---

## How to use this document
This is one task brief covering six capabilities, organized as checkpointed phases. Implement a phase, test it, verify non-breakage, commit and tag it, then continue. Reuse the living-document discipline from the earlier build work: keep `AUDIT.md`, `PLAN.md`, `DECISIONS.md`, and `VERIFICATION.md` current throughout. A companion file, **`citizen-eligibility-wizard-mock.html`**, is the behavioural/visual spec for Phase 3 — read it before building that phase.

**Demo constraint:** there is a stakeholder demo in ~9 days (Friday). Build in the phase order given — it is deliberately sequenced so the highest-impact, most demo-worthy features (the wizard, the maintainability co-pilot, the eligibility triage) come out of a shared foundation early, and the largest build (the report engine) can be shown as a working vertical slice even if not yet complete. A **"Demo path"** note at the end says exactly what to prioritize if time runs short.

## Prime directives (unchanged from prior work — restated because they still bind)
1. **Live system.** After every phase: builds, deploys, full test suite green, every existing citizen and officer journey still works end to end.
2. **Audit before touching.** Read existing code paths and reuse existing conventions (entity/repo structure, i18n, config, caching, security, workflow states) rather than introducing a parallel pattern.
3. **Performance is a hard constraint, not a nice-to-have.** No feature here may add per-request DB load in business hours that wasn't there before. Heavy analytics run off-hours and/or against pre-aggregated data; hot paths read from Hazelcast; every new query is indexed, parameterized, bounded, and reviewed. State the performance impact of each phase in `VERIFICATION.md`.
4. **WCAG 2.1 AA** on every new or changed UI element, built in as you go.
5. **Language support honored throughout.** All **data entry remains English-only**. But every **label, message, tooltip and static string** goes through the existing DB-driven i18n platform. Citizen-facing surfaces (the new form fields' labels, the eligibility wizard) must render in all 10 languages including Urdu RTL. Internal officer/senior surfaces are English UI but still use the i18n platform for their chrome — no hardcoded strings anywhere.
6. **Schema changes via the existing migration tool only.** No hand-run DDL.
7. **Config over hardcoding** for every tunable (windows, thresholds, slot times, cache TTLs, result caps).
8. **Checkpoint discipline:** small commits within a phase; tag each verified phase (`git tag cms2-phase-2-complete`); never rewrite shared history.

**Before Phase 1: present `PLAN.md` (phases, dependencies, the shared-component design, risk flags, and the Decisions-to-confirm list) and stop for approval.** Then proceed phase by phase, pausing only at listed stop-and-ask points or anything of comparable weight.

---

## The architectural spine — read this before planning

Three of the six requested features (the citizen wizard, the officer maintainability suggestion, and the intake eligibility triage) are the **same rules applied at three different moments**. Do not implement the RB-IOS maintainability logic three times. Build it **once** as a shared, versioned, auditable component and consume it everywhere:

> **Maintainability Rules Engine (MRE)** — a deterministic Java service that, given a complaint's structured facts, evaluates the *objective* maintainability grounds from RB-IOS 2026 and returns a structured verdict (per-ground pass/fail/needs-review) plus the specific clause each finding rests on. No probability, no ML — these are legal date-and-category rules and must be crisp, explainable, and defensible before a quasi-judicial process.

Consumed by:
- **Phase 3** (citizen wizard) — self-check before filing.
- **Phase 4** (officer co-pilot) — objective verdict, combined there with precedent for the *subjective* grounds.
- **Phase 5** (intake triage) — auto-flag at the point of registration.

When RBI amends the Scheme, the rules change in one place and all three surfaces stay consistent. Cache the compiled rule set in Hazelcast; invalidate on rule-version change. This one decision is what will make the system feel coherent and trustworthy to the seniors — foreground it in the demo.

**Objective grounds the MRE owns** (all computable, from RB-IOS 2026): entity not covered by the Scheme (Q13); no prior complaint to the Regulated Entity (Q16); complaint to RBI filed before the 30-day RE window — or the higher NPCI/card-network window — has elapsed, unless already replied-and-dissatisfied (Q17); complaint filed beyond 90 days of the timeline expiry / last RE communication (Q16/Q17); complaint to the RE made after the Limitation Act 1963 period (Q16); same grievance already pending/decided by the Ombudsman or a court/tribunal (Q16, checkable against your own data plus a declared field).

**Subjective grounds the MRE does *not* decide** (these need human judgment, handled by precedent in Phase 4): no deficiency in service / mere suggestion or rant (Q16/Q26/Q27); commercial judgment of the RE; against management/executives; vendor, inter-RE, or employer-employee disputes; abusive/frivolous/vexatious.

---

## Phase 0 — Discovery & Baseline
Map, in `AUDIT.md`: the complaint entity/aggregate and every field on it; how maintainability is currently recorded and decided (the manual step today); the workflow states and transitions (registered → maintainability check → forwarded to RE → examined → settled/award/rejected → closed, per Q24 — confirm the real state names); the i18n platform's key/table structure and cache; the Hazelcast configuration (what's cached, invalidation model, near-cache vs distributed); the existing case-detail modal and any similarity/index infra from prior work; the scheduler and email/notification infrastructure; attachment handling; the config mechanism; the test/CI setup; existing dashboards and how they read data.
Capture **baseline performance** (key query timings, dashboard load, business-hours DB load profile) for later comparison. Record the migration tool and branching strategy.
**Done when:** `AUDIT.md` written, `PLAN.md` drafted and approved.

---

## Phase 1 — New complaint fields: prior-RE-complaint details
**Item 1.** Add the fields the whole eligibility spine depends on, and that RB-IOS Q18 lists as minimum details.
- Add: **whether the complainant has already complained to the Regulated Entity** (yes/no), the **date of that RE complaint**, and the **RE complaint reference / acknowledgement number**. Check the schema first — if partial versions exist, extend rather than duplicate.
- Full i18n for every new label, hint and validation message across all 10 languages (citizen-facing → all languages incl. Urdu RTL); English data entry.
- Server-side validation: date not in the future; reference format sane; consistency (if "yes", date and reference required).
- Migration via the existing tool; add the index that Phase 5's triage and Phase 6/7 analytics will need on the RE-complaint-date.
**Done when:** fields captured, validated, localized on every touched surface; existing form submission unaffected; migration reversible.

---

## Phase 2 — Maintainability Rules Engine (the shared keystone)
**Foundation for Phases 3, 4, 5 — build and harden it before them.**
- Implement the MRE as described in the spine above: a deterministic service taking a complaint's structured facts, returning a per-ground structured verdict + the RB-IOS clause reference for each finding + an overall "objectively-clear / needs-human-review" signal.
- Encode **only the objective grounds**. Every rule is **configurable and versioned** (the 30-day window, the per-category NPCI/card-network overrides, the 90-day window, entity-coverage lists per Q13) — no magic numbers. Business-hours vs calendar-days basis for the windows is a **decision to confirm** (see list); do not default silently.
- Pure, side-effect-free, exhaustively **unit-tested against scenarios drawn from the Scheme**: too-early, replied-and-dissatisfied-so-eligible-anyway, just-past-90-days, entity-not-covered, no-prior-RE-complaint, boundary dates on both windows. These tests are also the living proof to compliance that the logic matches the circular — keep them readable.
- Cache the compiled rule set in Hazelcast; invalidate on version change; expose the current rule version in the verdict for auditability.
- **Advisory by contract:** document and enforce that the MRE never *decides* maintainability — it informs. Final determination stays with a human. This is both legally necessary and the safe default.
- **Compliance checkpoint (flag, don't resolve in code):** the encoded rules must be validated against the actual RB-IOS 2026 Scheme text and signed off by legal/compliance before the wizard or triage act on them. Log this as an open item.
**Done when:** MRE returns correct, clause-referenced verdicts across the full scenario suite; rule set is config-driven and hot-swappable; nothing consumes it yet.

---

## Phase 3 — Citizen "Can RBI help?" eligibility wizard
**Item 2 — my proposed innovative feature. Behavioural spec provided: `citizen-eligibility-wizard-mock.html`.**
- Build the wizard as specified in the mock: a short, plain-language, mobile-first pre-filing self-check that consumes the **MRE** (Phase 2) — it must not re-implement any rule.
- **The signature is the eligibility timeline** (see mock): it plots the RE-complaint date, the point the RBI window opens, today, and the file-by deadline, so the citizen *sees* why the verdict is what it is. This is what makes the abstract date rules feel fair and clear.
- Outcome states from the mock: ready-to-file; too-early (show the exact date the window opens, offer an SMS/email reminder for that date); likely-too-late (advisory); complain-to-RE-first; entity-not-covered (redirect/guide); and the gentle subjective gate that catches feedback/rants/commercial-decisions and steers them — this is what attacks the non-maintainable-intake problem *at source*.
- **Never a hard block.** Every terminal state keeps a way to proceed to the form — the citizen's right to file is absolute; the wizard only informs. Reinforce with the standing footer in the mock.
- Fully multilingual (all 10 languages, Urdu RTL) via the i18n platform; strings shown in the mock are English placeholders. Full WCAG AA: keyboard-operable choices, focus management on step change, `aria-live` announcements on step/outcome, not-colour-alone outcomes (icon + label + text), `prefers-reduced-motion` respected, large touch targets.
- Carry the wizard's captured answers into the complaint form as prefilled/known values where they map (entity type, RE-complaint date/reference from Phase 1) so the citizen isn't asked twice.
- **Efficiency dividend to measure:** instrument how many sessions are redirected or self-corrected before submission, and the change in non-maintainable intake — this is a headline metric for the demo.
**Done when:** wizard runs on real narrow viewports in ≥3 languages incl. Urdu; every outcome reachable and correct against seeded date scenarios; MRE is the only source of the logic; never blocks; answers flow into the form.

---

## Phase 4 — Officer maintainability co-pilot (rules + precedent, no black box)
**Item 3.** When a complaint reaches the manual maintainability step, *suggest* the likely determination and why — learning from past decided cases — while a human still decides.
- **Recommended approach: hybrid, fully explainable, human-in-the-loop — not an opaque AI.** For a regulator's quasi-judicial screening, an auditable suggestion that can be defended is worth far more than a black-box score.
  - **Objective grounds:** show the **MRE** verdict (Phase 2) — crisp, clause-referenced.
  - **Subjective grounds:** use **case-based precedent retrieval** over past *decided* complaints — reuse the similarity index already built (Oracle Text within the existing DB, or the ELK path if that was taken previously). Retrieve the k most similar prior cases and surface, for each: outcome (settled/award/rejected/non-maintainable), the ground/reason recorded, the RE, and the closure action taken. The suggestion is: *MRE verdict + "cases like this were closed as X for reason Y" with the actual precedent cases shown.* It "learns" as the corpus of closed cases grows — no model retraining.
  - Assemble a **templated draft rationale** the officer can accept/edit rather than write from scratch, citing the specific ground and precedent — this is where the time saving is.
- **Optional, clearly secondary, only if there's appetite:** a calibrated confidence score from a *transparent* classifier (logistic regression / shallow tree over structured + TF-IDF-of-narrative features) trained on historical maintainable/non-maintainable labels, presented with feature attributions ("flagged because: filed 4 days after RE complaint; narrative resembles feedback cluster"). If pursued, use **Tribuo (Oracle's Java ML library)** to stay in-stack and avoid a Python sidecar and a polyglot deployment. Keep it advisory and explainable — never a bare number, never auto-actioning. Treat introducing even this as a **decision to confirm**.
- **Hard guardrails:** the co-pilot suggests; the officer decides and records the decision. Log suggestion-vs-final-decision for later quality review and to detect drift. No auto-rejection, ever.
- Performance: precedent retrieval must hit the index/cache, not scan the complaints table live; cap k; bound latency.
**Done when:** for a seeded case, the co-pilot shows a correct MRE verdict, relevant real precedents with outcomes/reasons, and an editable draft rationale; the officer's final decision is always required and recorded; retrieval is index-backed.

---

## Phase 5 — Intake eligibility triage & timeline (flagship, circular-grounded)
**Item 5 — my recommended "wow" feature.** At the moment a complaint is registered, automatically compute its eligibility timeline and flag objective maintainability issues *before a human screens it*, using the **MRE**. This removes real manual toil on the objective, enumerable grounds while leaving judgment to people.
- On registration, run the MRE and attach to the case: the **eligibility timeline** (RE-complaint date → window-opens → file-by deadline → where "today"/filing-date sits), and any **objective flags** (filed too early, past 90 days, entity not covered, no prior RE complaint) with the clause reference.
- Surface this to the human screener as a **triage banner** on the case — green/clear, amber/needs-attention, with the timeline visual — so screening the objective grounds becomes a glance, not a manual date calculation. The screener still confirms; the system pre-computes.
- Feed these flags into the officer worklist as a filter/sort ("objectively questionable maintainability") so screeners can batch the clear-cut ones.
- **This is the demo centrepiece:** it visibly ties the new circular's hard rules to a measurable efficiency gain, and it reuses the exact same engine as the citizen wizard — one rule set, citizen-to-closure. Show that lineage.

**Two additional high-value options grounded in RB-IOS 2026** — offer these to Gaurav; build if time allows, otherwise log as roadmap:
- **Compensation-precedent band.** RB-IOS caps compensation at ₹30 lakh (consequential loss) + ₹3 lakh (time/harassment) (Q22/Q23), and Q30 explicitly acknowledges similar cases can diverge. A tool that, for a case being decided, shows the award band from *similar past awarded cases* ("comparable cases were awarded ₹X–₹Y") helps defensible, consistent awards — a genuine regulator concern. Reuses the Phase 4 similarity infra. Needs historical award-amount data — confirm it exists.
- **RE-responsiveness radar.** Once maintainable, a complaint is forwarded to the RE for response (Q24); slow REs are a systemic problem, and the Ombudsman can proceed ex-parte when an RE misses its window. Track each RE's response time against its window, auto-flag breaches, and offer the ex-parte pathway. Feeds directly into the Phase 7 senior dashboard.
**Done when:** every newly registered case carries a correct MRE-derived timeline and objective flags; the screener banner renders and is accessible; worklist filtering works; efficiency instrumentation in place.

---

## Phase 6 — Natural-language report & widget builder (the niche piece)
**Item 4.** Let internal users compose reports over the complaint domain in near-English, see a grid, save a result as a dashboard widget, or schedule it to email — without a clunky query builder. This is the largest build; treat safety and performance as first-class, not afterthoughts.

**Interaction model (my recommended improvement on the raw idea).** Rather than free-text English parsed loosely, build a **guided, token-based query composer** — the modern "structured search-as-you-type" pattern (think how Linear/GitHub issue search or Splunk compose filters). The user types in plain language and gets **live autocomplete of only valid next tokens** — fields, operators, values drawn from a governed semantic model — assembling a readable sentence like *"show me complaints from **banks** closed in the **last month** in **the Western region**."* The assembled sentence is what's saved and displayed; underneath it is a validated structured query, never raw user SQL. This is more intuitive *and* far safer than parsing arbitrary prose, and it still reads as English. (If you want a pure free-text entry box too, it must parse into the same validated structure and reject anything it can't map — same safety floor.)

**Semantic model.** Define, from the actual complaint aggregate (Phase 0), a governed set of **dimensions** (RE, RE-category, region/Ombudsman office, product/deficiency type, status, maintainability, dates) and **measures** (count, average/median/p90 TAT, etc.), each mapped to real columns with display labels via i18n. Users query the semantic model, never tables directly.

**Compilation & safety (non-negotiable).**
- The composed query becomes a **validated intermediate representation**, checked against the semantic-model registry (only whitelisted fields/operators/values), then compiled to a **parameterized JPA Criteria / query-DSL query — never string-concatenated SQL, never dynamic SQL, never any write path.** Read-only by construction.
- **Authorization is enforced in the compiled query**, not trusted from the input: a user sees only complaints their role permits (a low-level officer's scope ≠ a senior's). Row-scoping is injected server-side.
- **Bound everything:** max rows returned, statement timeout, max columns, pagination. A report cannot be a denial-of-service vector.
- Injection, authorization-bypass, and oversized-result attempts are explicit test cases in Phase 8.

**Results → widget.** Execute → show a grid. "Add to my dashboard" → prompt for chart type (table, bar, stacked bar, line, pie/donut, KPI number, region breakdown) → persist the *query definition + chart type* (not a data snapshot) as a widget. Widgets honor the existing dashboard refresh discipline and read from cache/pre-aggregation where possible.

**Results → scheduled email.** "Send me this daily at 11 PM." Schedule slots are **fixed off-hours only: 10 PM, 11 PM, 12 AM, 1 AM, 2 AM** — chosen to protect business-hours performance; make that rationale explicit so it can't drift. At each slot a batch job loads all report definitions bound to that slot, executes each against current data, generates an **Excel workbook (Apache POI)**, and emails it to the user.
- **Batch robustness:** per-report timeout and row cap; one failing report must not abort the batch; dedupe identical definitions; full audit log of what ran and what was sent; retry/failure notification policy.
- **Performance:** these run off-hours by design, but still bound resource use; prefer reading pre-aggregated summaries (see Phase 7) over heavy live scans; consider a read replica / governed resources if available (decision to confirm).
- Never email data beyond the requesting user's authorization scope — re-check scope at send time, not just at save time.

**Done when:** a non-technical user composes a query via guided tokens, runs it, saves it as a chart widget, and schedules it to a fixed slot; the scheduled job produces a correct Excel and emails it; every query is parameterized, authorization-scoped, bounded; the safety test cases (Phase 8) pass.

---

## Phase 7 — Senior TAT / pipeline / bottleneck dashboard
**Item 6.** A dashboard for seniors answering: where are complaints coming from (which REs/regions), what TAT is being taken to close them, and where the bottlenecks are — with rich filters and clear charts.
- **Build it on the Phase 6 engine + pre-aggregation**, not bespoke live queries. Analytics must read from **pre-aggregated summary data** (Oracle materialized views or summary tables) refreshed on the off-hour schedule and cached in Hazelcast — so a senior slicing charts never hits the live transactional tables during business hours. This is the single most important performance decision in this phase; state it in `VERIFICATION.md`.
- Metrics: inflow by RE / RE-category / region / product / deficiency type / time; **TAT distribution** (average, median, p90 — not just a mean) by RE, department, category, and stage; pipeline funnel across the RB-IOS stages (registered → maintainability → forwarded to RE → examined → settled/award/rejected → closed); aging buckets and cases breaching TAT; maintainable-vs-non-maintainable ratios; RE response time (from the Phase 5 radar, if built); trends over time.
- Filters: date range, RE, RE-category, region/Ombudsman office, department, officer, category, status, maintainability. Charts: bar/stacked, line/trend, percentile bars or box for TAT, funnel for the pipeline, heatmap (region × RE, or aging), leaderboard tables — with drill-down to the underlying case list (respecting authorization).
- **Framing judgment (raise with Gaurav):** "who are the bottlenecks" is real signal, but an individual-officer naming-and-shaming board is a morale and fairness risk. Lead with **process bottlenecks** (which stage/queue cases sit in longest, aging by category, RE-side delays), and treat individual drill-down as a gated, workload-and-attention view ("cases needing attention," capacity balancing) rather than a ranking. This protects the tool's credibility with the people it reports on. Flag it as a design choice rather than deciding unilaterally.
- **Beautiful, not just functional:** considered palette, clear hierarchy, meaningful colour (not decorative), readable at a glance, drill-down that feels natural. Full WCAG AA (charts need text/table equivalents and non-colour-alone encodings).
**Done when:** the dashboard renders all metric groups from pre-aggregated data with working filters and drill-down; verified to *not* add business-hours load to transactional tables; charts are accessible; framing choice confirmed.

---

## Phase 8 — Cross-cutting hardening & adversarial verification
Only after Phases 1–7 are complete and individually verified.
- **Full regression:** existing suite + everything added; every pre-existing citizen and officer journey re-walked.
- **Performance re-check vs the Phase 0 baseline:** confirm no business-hours DB-load regression; confirm hot paths read from Hazelcast; confirm every new table/query is indexed and bounded; confirm analytics read pre-aggregated data; confirm the report batch is isolated and capped and runs only in the off-hour slots.
- **Adversarial — actually attempt, don't just reason:**
  - Report builder: injection payloads through the composer and any free-text entry; attempt to reach non-whitelisted tables/columns; attempt to exceed row/column/time bounds; attempt to retrieve or email data outside the user's authorization scope (at both save time and send time); attempt to smuggle a write.
  - MRE / wizard / triage: feed boundary and adversarial dates to confirm the objective verdicts are exactly right at both window edges; confirm the wizard never hard-blocks and never *decides*; confirm the co-pilot never auto-rejects.
  - Scheduler: many reports on one slot (batch survives a failing report), oversized result handling, duplicate definitions.
- **Accessibility sweep** across wizard, co-pilot, triage banner, report composer, and both dashboards (automated axe/pa11y/Lighthouse + a keyboard-only and screen-reader pass); wire the automated a11y check into CI as a regression gate. Re-verify Urdu RTL didn't break later-built screens.
- **i18n coverage check:** every new citizen-facing string resolves in all 10 languages; internal chrome has no hardcoded strings.
- Write the final `VERIFICATION.md`: what was tested, results, performance before/after, known limitations, and a consolidated pull of `DECISIONS.md` for review.

---

## Decisions to confirm with Gaurav — do not silently default
1. **MRE window basis:** calendar days vs business/working days for the 30-day and 90-day windows — materially changes both the citizen wizard's dates and the intake triage. Confirm against the Scheme.
2. **Legal/compliance sign-off** that the encoded objective grounds match RB-IOS 2026 before the wizard and triage act on them (the FAQ is not legal authority).
3. **Phase 4 optional classifier:** rules + precedent only (recommended), or also add a transparent Tribuo confidence score.
4. **Report builder input:** guided token composer only (recommended), or also a free-text English box (which must still compile to the same validated structure).
5. **Read replica / governed resources** for the off-hour report batch and analytics pre-aggregation, if available in the environment.
6. **Phase 5 stretch features:** whether to build the compensation-precedent band (needs historical award-amount data) and/or the RE-responsiveness radar for the demo, or defer to roadmap.
7. **Phase 7 bottleneck framing:** process-first with gated individual drill-down (recommended) vs a direct individual ranking.
8. **Similarity backend** for Phase 4/5 precedent: confirm whether Oracle Text (in-stack, no new infra) or the previously-considered ELK path is the basis.

## Demo path — if the 9 days get tight, prioritize in this order
The foundation makes the first three cheap once it exists:
1. **Phase 1 + Phase 2** (fields + MRE) — the keystone; nothing demo-worthy works without it, but it's compact.
2. **Phase 3 (citizen wizard)** — highest visual impact, spec already built, directly shows the circular reducing wasted effort.
3. **Phase 5 (intake triage + timeline)** — the "wow," and it reuses Phase 2 entirely, so it comes fast.
4. **Phase 4 (co-pilot)** — strong, reuses existing similarity infra.
5. **Phase 6 (report builder)** — show a **working vertical slice**: a handful of dimensions, the token composer, a grid, one chart type saved as a widget, and one scheduled slot producing an Excel. The full dimension matrix and all chart types can follow after the demo.
6. **Phase 7 (senior dashboard)** — even a few pre-aggregated charts (inflow by RE, TAT percentiles, pipeline funnel) land the message; breadth can grow later.
Narrative for the room: *one rule set from the new circular, applied from the citizen's first click through to the officer's closure, plus analytics the seniors can drive themselves — with no hit to performance.*
