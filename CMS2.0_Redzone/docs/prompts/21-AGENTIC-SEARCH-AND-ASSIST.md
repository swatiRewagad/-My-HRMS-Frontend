# Brief 21 — Language-aware ELK search + the assistance rail (Tier 0 / Tier 1)

**Audience:** one Claude Code session, running in parallel with others on `CMS_21092026`.
**Status of this document:** a brief, not a spec. Every measured fact below was verified on
2026-09-30 against the working tree. Facts marked **[VERIFY]** were not confirmed and you must
confirm them before building on them. Where this brief and the code disagree, **the code wins** —
say so in your report rather than bending the code to match the brief.

---

## 0. What this session delivers

Four things, in this order. Do not reorder: each later item depends on the earlier one's
guardrails.

1. **Replace the OpenSearch stack with Elasticsearch (ELK community), with real index mappings
   that are language-aware** across the 13 languages this product actually serves.
2. **Index the text that matters**: complaint text, closure/decision text, and *uploaded document
   text* — plus the additional fields identified in §3.
3. **Make user-driven "find similar cases" work on every staff screen**, from one shared component,
   replacing three competing half-implementations.
4. **Ship the assistance rail** — a subtle bulb on the right-hand rail that glows when it has
   something worth saying — carrying **Tier 0** (memory/continuity) and **Tier 1** (precomputed
   priors) features only.

Out of scope, explicitly: any LLM inference, any browser-side ML model, embeddings/kNN/vector
search, and the "front-end macro recorder". Do not build them. Do not leave scaffolding for them.

---

## 1. Read this before you touch anything

### 1.1 You are one of several concurrent sessions

Verified live at the time of writing: backend on **8092**, `ng serve` on **4202**, Keycloak on
**9090**, MySQL `cms_db` on **3306**. Six git worktrees exist. Therefore:

- **Work in your own git worktree.** `cms-backend/target/` is a single directory shared by every
  session; concurrent `mvn` runs clobber each other's class output and produce compile errors that
  blame files you never opened. If you see an error in a file you did not touch, verify with an
  isolated `javac` before believing it.
- **Your backend port is 8093.** Use `deployment/run-test-backend.sh 8093`. Never start on 8092 —
  that is another session's. The script passes the correct
  `-Dcms.hazelcast.cluster-name=cms-claude-8093`; the flag name matters, `-Dhz.cluster-name` is
  silently ignored and you will silently join the other session's cache.
- **You do NOT own `ng serve`.** You share 4202. Do not restart it — that breaks other sessions
  mid-run. Consequence you must design around: the shared dev server serves the **main checkout**,
  not your worktree, so a browser assertion about a frontend file *you* edited reports on code you
  did not change. Assert your own frontend edits by reading the source file; reserve browser
  assertions for pages your session has not modified. State this reasoning in any spec header so it
  does not read as laziness.
- **`ECONNREFUSED` on your own port is usually another session restarting something**, not a flaky
  test. Poll `/actuator/health` until 200 and re-run before concluding a feature is broken.
- **The git index is shared.** `git add` then `git commit` is a race — another session's staging can
  land in your commit. Always commit with an explicit pathspec
  (`git commit --only -F <msgfile> -- <paths>`), then verify the file count with
  `git show --name-status --format='' HEAD`.
- **Do not `git stash` to prove a failure is pre-existing.** Other sessions add callers to the files
  you are editing, so the "baseline" fails to compile and proves nothing. Prove ownership by
  inspection: grep the failing test for your own class and field names. If none of your symbols
  appear, it is not yours.

### 1.2 Reserved ranges — yours alone

- **MySQL migrations: `V111`–`V118`.** (High-water at time of writing: V110.)
- **Oracle migrations: `V108`–`V115`.** (High-water: V107.) Every MySQL migration needs its Oracle
  counterpart; that is repo convention, not optional.
- **Seeder `@Order`: 40–49.** One new seeder class per session; never edit another session's seeder.
  Translation keys are idempotent by `existsByCode`, so if two sessions use the same key code the
  first text silently wins — it looks like a UI bug, not a conflict. Namespace your keys.
- **V-numbers get stolen mid-session even inside an assigned range.** Run
  `ls database/V1*.sql database/oracle/V1*.sql` immediately before creating a migration *and again
  before finalising*.

There is **no Flyway or Liquibase**. Schema comes from `spring.jpa.hibernate.ddl-auto: update`;
`database/*.sql` files are hand-run. On a shared DB this means: **new columns must always be
nullable**, `update` never drops anything so your column is permanent for everyone, and there is no
version table to roll back. Stagger backend startups.

### 1.3 Chokepoint files — expect contention

`RbioWorkflowService.java`, `WorkflowController.java` (~935 L, every RBIO endpoint),
`Complaint.java`, `e2e/utils/test-data.ts`, `rbio-workflow.service.ts`. Touch these as little as
possible and keep each edit small and additive.

### 1.4 Licensing — settled, do not re-open

Elasticsearch is not Apache 2.0 (ELv2/SSPL, with an AGPL option added in 2024), unlike the
OpenSearch it replaces. **This has been considered and settled: the Bank holds purchased ELK
licences.** So the move is a deliberate, licensed choice, not an oversight.

Two things still fall to you:

- **Record the exact artifacts and versions you pull** (server image, plugins, client library) in
  your findings, so the deployment is auditable against the licence entitlement.
- **Check whether any feature you rely on is a paid-tier feature**, and say so if it is. Several
  Elastic capabilities sit behind Platinum/Enterprise. In particular, **confirm the licence tier
  required for `analysis-icu`** (§2.3) — the brief assumes it is a free open-source plugin, which
  needs verifying rather than assuming, because the entire non-Devanagari language story depends on
  it. If anything you need is gated above the purchased tier, stop and report rather than designing
  around it.

Do not otherwise re-litigate the licence choice in your report.

---

## 2. Replace OpenSearch with Elasticsearch

### 2.1 What exists today (measured — verify, then demolish)

Three competing similarity implementations. This is the mess you are replacing.

| # | What | Where | State |
|---|---|---|---|
| 1 | OpenSearch `more_like_this` over `subject`/`description`/`facts` | `cms-backend/.../service/OpenSearchSimilarCasesProvider.java:66-90`, exposed at `POST /api/v1/similar-cases/search` and `GET /api/v1/similar-cases/status` (`SimilarCasesController.java:21,36`) | **Dead.** `similar-cases.enabled: false` in `application-dev-local.yml`. No frontend calls it. Queries index **`complaints`** while the search service writes index **`cms-complaints`** — even enabled, it queries an empty index. Bare `new RestTemplate()` with **no connect or read timeout** (`:29`), wrapped only in `catch (Exception)`. |
| 2 | Groq LLM `llama-4-scout` ranking the last 100 complaints | `PastComplaintService.findSimilarCases:75`, `POST /api/v1/past-complaints/similar` | **Live.** This is what the CRPC draft-assessment sidebar actually uses (`crpc/draft-assessment.component.ts:664`). Has retry + a circuit breaker. Falls back to `ComplaintRepository.search` = SQL `LIKE '%q%'`. |
| 3 | Jaccard-ish subject-token overlap on email drafts | `EmailSyndicationApiController.findSuggestedRelated:1361`, `similarityScore:1390` | Live, narrow, email-intake only. |

Plus one phantom: `GET /api/v1/complaints/{id}/similar` is called by
`staff/task-action.component.ts:313` and **does not exist**. Errors are swallowed to `[]` so the
panel has always silently rendered empty.

`cms-search-service` itself is four Java files: `SearchController` (`/api/v1/search/complaints`,
`/complaints/status/{status}`, `POST /reindex`), `OpenSearchConfig`, `ComplaintSearchService`,
`ComplaintIndexingListener`. Port 8091, context path `/cms-search`. Two defects to fix, not port:

- **No index mappings, no analyzers, no template, no `createIndex` call anywhere.** The index is
  created implicitly by first write with dynamic mapping, so `term` filters on `category`/`status`
  and the `createdAt` sort hit `text` vs `.keyword` mismatches.
- **Status updates corrupt documents.** `ComplaintIndexingListener:59` calls `indexComplaint` — a
  full `IndexRequest` — with a 2-3 key partial-update map, **overwriting the document and destroying
  subject/description**. It must be an update/upsert.
- `reindexAll()` (`:103-135`) hardcodes `http://localhost:8082/cms-ingestion/...` and only reindexes
  whatever `recentComplaints` the dashboard-stats endpoint returns. It is not a reindex.

Frontend: `cms-portal-frontend/src/app/services/search.service.ts:37` points `baseUrl` at
`environment.apiBaseUrl` = `http://localhost:8082`, which is cms-backend — not the gateway (8080)
and not the search service (8091). Only the gateway maps `/api/v1/search/**` → 8091
(`cms-api-gateway/.../application.yml:89-94`). **So the `/search` screen 404s in dev-local today.**
Fix the routing as part of this work.

### 2.2 What to build

**Deployment.** Elasticsearch replaces the `opensearch` service in `cms-infra/docker-compose.yml`
(currently `opensearchproject/opensearch:2.18.0` at `:498`, port 9200, security plugin disabled,
healthcheck on `_cluster/health`; `cms-search-service` at `:465` depends on it healthy). Keep the
same shape: single node, security disabled **in dev only**, healthcheck retained. Add Kibana only if
it costs nothing; it is not a deliverable. Remove the OpenSearch client dependencies from every
`pom.xml` that carries them — do not leave both clients on the classpath.

**One index, one owner.** Exactly **one** service writes the index: `cms-search-service`. Nothing
else may hold an ES client. Kill `OpenSearchSimilarCasesProvider` and the `/api/v1/similar-cases/*`
controller; their replacement lives behind the search service. Settle on **one** index name and
delete the other from the codebase entirely — no alias shims, no compatibility constants.

**Index via an explicit template or `createIndex` on startup, checked in as JSON**, so the mapping
is reviewable and reproducible. Dynamic mapping is banned: set `"dynamic": "strict"` on the primary
document so an unmapped field fails loudly at index time instead of silently becoming `text`.

**Reindex must be real.** Replace `reindexAll()` with a paged, resumable, rate-limited walk of the
source table using the bulk API, driven by a keyset cursor (not `OFFSET`), with a configurable batch
size and an inter-batch pause. It must be safe to run against production during business hours, and
safe to kill and restart. Use an alias + build-into-a-new-index + atomic alias swap so a reindex
never leaves the live index half-populated.

**Indexing stays event-driven off Kafka** (`ComplaintIndexingListener`, group `cms-search-group`,
topics `COMPLAINT_INGESTED` and the status events). Fix the overwrite bug: partial events use a
partial update/upsert. Make the listener idempotent and give it a bounded retry with a dead-letter
path — a poison message must not wedge the consumer group.

### 2.3 Language-aware analysis — the hard part

The product serves **13 languages**, measured from language-code literals in `cms-backend`:
`en, hi, mr, bn, ur, te, ta, ml, kn, gu, pa, or, as`. Complaint and closure text can be in any of
them, and **a single complaint can mix scripts** (an English closure clause quoted inside a Hindi
narrative is the normal case, not the edge case).

Elasticsearch ships built-in language analyzers for only a subset of these — `hindi` and `bengali`
directly, and `persian`/`arabic` are the nearest fit for `ur`. **[VERIFY]** the current built-in
list against the ES version you deploy. For the remainder (`mr, te, ta, ml, kn, gu, pa, or, as`) the
`analysis-icu` plugin is expected to be required, giving `icu_analyzer`, `icu_normalizer` and
`icu_folding`. **[VERIFY]** this, and if `analysis-icu` is needed it must be installed in the Docker
image — a plugin install is a Dockerfile change, not a config line. Confirm before designing around
it; if ICU turns out unnecessary or insufficient, report that and adapt.

Design the mapping so that:

- Each searchable text field is **multi-field**: a `standard`-ish base subfield that always works,
  plus per-language analyzed subfields for the languages you can actually support. Search issues a
  `multi_match` across the base plus the subfields relevant to the query, so a query in any script
  still matches.
- Apply `indic_normalization` and `decimal_digit` token filters where applicable — Devanagari digits
  and the several Unicode spellings of the same Indic character otherwise defeat exact matching.
  **[VERIFY]** filter availability.
- Store a per-document `detectedLanguage` field. Do not guess it at query time on every request.
  Decide and document **where detection happens**: prefer the citizen's or staff user's declared
  language already captured in the domain model if one exists, and fall back to script-range
  heuristics at index time. Do not add a language-detection library without saying why.
- Never let analysis choice change what a user is *allowed* to see. Language handling is relevance
  only; authorisation is §4.

**Prove it, do not assert it.** Deliver an integration test that indexes a fixture document per
language and asserts retrieval — including a mixed-script document, and including one case where a
naive `standard` analyzer would fail but the language subfield succeeds. A passing test in English
alone is not evidence, and "the analyzer is configured" is not evidence.

### 2.4 What gets indexed

Confirmed available:

- **Complaint text** — `COMPLAINTS.subject` and `.description` (`entity/Complaint.java:67,70`),
  `advisory_text` (`:354`).
- **Closure and decision text** — `COMPLAINTS.closure_clause`, `withdrawal_reason` (500),
  `scheme_coverage_reason` (500), `reopen_reason`/`reopen_justification`;
  `APPEAL_ORDER.order_summary`, `.ground` (300), `.correction_reason` (1000), `.outcome`,
  `.clause_code` (`entity/AppealOrder.java:57-76`); and the richest source,
  **`COMPLAINT_TIMELINE.remarks`** TEXT alongside `action`, `from_status`, `to_status`,
  `closure_clause`, `performed_by_role`, `performed_at` — all `updatable=false`, i.e. immutable and
  safe to mine (`entity/ComplaintTimeline.java:36-96`).
- **Clause vocabulary** — `CLOSURE_CLAUSE_MASTER.clause_code`, `.label`, `.category`,
  `.restricted_to_roles` (`entity/ClosureClauseMaster.java:27-71`). Note `restricted_to_roles`; it
  matters in §4.
- Useful filter/facet fields already on `COMPLAINTS`: `category_id`, `entity_code`, `department`,
  `status`, `workflow_stage`, `milestone`, `rbio_office_code`, `ground_of_complaint_id`,
  `award_amount`, `compensation_type`, `maintainability_determination`, `created_at`.

**Uploaded documents are the big gap — scope this honestly.** OCR/extracted text exists *only* for
email-intake attachments: `EmailAttachment.ocrText` / `EmailDraftAttachment`
(`cms-ingestion-service/.../email/entity/EmailAttachment.java`, `OcrEligibilityService`). Documents
uploaded by citizens and staff through `cms-storage-service` have **no extracted text anywhere**.
So "index the documents" is really two jobs:

1. **Index the text that already exists** (email-intake OCR) — cheap, do it.
2. **Create an extraction path for the rest.** `cms-paddle-ocr` exists in the tree — **[VERIFY]**
   what it is wired to and whether it can serve this. Extraction must be **asynchronous and
   off-request**: a Kafka-driven worker with a bounded pool, never a synchronous call during upload.
   A 40-page scanned PDF must not block a user or a Tomcat thread.

If (2) proves larger than the rest of this brief combined — which is plausible — **stop, report the
sizing, and deliver (1) plus the extraction contract** rather than half-building an OCR pipeline.
Say so explicitly in your findings. Do not quietly skip it.

Also required for documents: index extracted text as a **child/nested document or a separate index
joined by complaint number**, never as a giant concatenated field on the complaint — otherwise one
scanned annexure destroys relevance scoring for the whole case. And **never index document bytes**.

### 2.5 Resilience — non-negotiable

Today a hung OpenSearch pins a Tomcat thread indefinitely, and there are only 50 DB connections
(`HikariCP maximum-pool-size: 50`, `connection-timeout: 5000`). Every ES call you write must have:

- An explicit **connect timeout and read/socket timeout** (order of 1s / 2s, configurable).
- A **circuit breaker**. There is **no Resilience4j anywhere in this codebase today** — you are
  introducing it. Add it narrowly, for the ES client, with a documented fallback.
- A **bulkhead** so search cannot consume the whole request pool.
- **Graceful degradation.** Search unavailable must mean "the panel says unavailable", never a 500
  and never a fallback to SQL `LIKE '%…%'`. Deleting the LIKE fallback is part of the work:
  `ComplaintRepository.search:37` returns an unpaginated `List` with three leading-wildcard LIKEs
  and no index can serve it.
- A **kill switch** in config that disables search features without a deploy, and which the frontend
  honours by hiding the affordance rather than showing a broken one.

---

## 3. Make "find similar cases" work on every staff screen

One backend endpoint. One frontend component. Every staff screen consumes it.

**Backend.** A single similarity endpoint fronted by `cms-search-service`, taking a complaint number
plus the caller's identity, returning a **bounded** list (hard cap, default 10, max 25) of
`{complaintNumber, subject snippet, closure clause + label, status, decidedAt, score, why}`. `why` is
the matched terms/fields — staff will not trust a black box, and highlighting is nearly free from ES.
Use `more_like_this` across the language subfields with `min_term_freq` / `min_doc_freq` tuned so a
two-word complaint does not match everything. Prefer **closed** cases with a closure clause, since
the user's stated purpose is "look for similar cases to decide".

**Consume it on all five high-value detail screens**, which already share a summary strip:
`crpc/draft-assessment` (html:88), `crpc/physical-letter` (:55), `crpc/reviewer-assessment` (:59),
`rbio/rbio-complaint-detail` (:67), `staff/task-action` (:72) — all using
`shared/complaint-summary`, whose `actions: TemplateRef` input (`complaint-summary.component.ts:37-45`)
is the natural slot. Also the AA and CEPC detail screens, which carry
`shared/workflow-action-bar`.

**Retire the competition as part of this.** Point `crpc/draft-assessment` at the new endpoint and
**remove the Groq path** for staff similarity — bounded latency, no external dependency, and no case
text leaving RBI infrastructure, which is the decisive argument. Delete the phantom
`GET /api/v1/complaints/{id}/similar` call site (`task-action.component.ts:313`) and its
error-swallowing `catch`. Removing the Groq dependency touches `PastComplaintService`; if another
session owns that file, coordinate rather than fight it, and report if you had to leave it.

**Frontend rules.** There is **zero `debounceTime` in the entire portal app** today, and no caching
interceptor — grid search filters on every keystroke. You are establishing the convention: RxJS
`debounceTime` + `distinctUntilChanged` + `switchMap` (so in-flight requests are cancelled, not
raced), a minimum query length, and an in-memory cache keyed by complaint number for the session.
The one existing debounce is a hand-rolled 300ms `setTimeout` at
`crpc/draft-assessment.component.ts:603-625`; replace that pattern, do not copy it.

---

## 4. Authorisation and PII — read this twice

A similarity feature is a **read-amplification** feature: it shows a user cases they did not open and
might not be entitled to see. This system has recorded history of PII leaks through exactly this
class of endpoint.

- **Filter by entitlement inside the ES query, not after.** Post-filtering a page of 10 hits leaks
  through the result count and through pagination behaviour, and it silently starves the panel.
- **Every result must be re-authorised server-side before serialisation.** Belt and braces: the query
  filter is for correctness of paging, the re-check is for correctness of access.
- **Return a snippet, never the full narrative**, and **never** complainant name, phone, email,
  address, or account/card numbers in a similarity result. The user's purpose is "what clause did we
  apply and why" — that needs the clause, the reasoning and the outcome, not the complainant.
- **Honour `CLOSURE_CLAUSE_MASTER.restricted_to_roles`.** A clause a role may not apply is a clause
  that role should not be shown as precedent.
- **Respect office and department scoping.** `department` is ANDed into every RBIO grid query
  (`RbioComplaintListService:179`) as a hard tenancy predicate; similarity must not become the hole
  in that fence. Whether cross-office precedent is permitted is a **policy question — surface it in
  your findings as an open ask and default to the restrictive answer** (same department; office
  scoping applied) until ruled otherwise.
- **Deliver a negative test per role** that asserts a case outside the caller's scope is absent from
  results. A test that only proves results *appear* is worthless here.

Identity comes from the JWT, which is authoritative over `X-User-*` headers. Never take the caller's
identity, office, role or user id from a request body or query parameter — there is a recorded defect
where an owner was client-supplied and omitting it returned every row in the system
(`EmailSyndicationApiController:451`).

---

## 5. The assistance rail

### 5.1 Interaction design

A **subtle bulb on the right-hand rail**, sitting with the comments. Dormant and quiet by default.
When the assistant has something worth saying — a likely next action, similar cases found — the bulb
**glows**. Click to open the rail panel and see what it has. That is the whole interaction. It is
ambient, never modal, never a toast, never stealing focus.

Rules that make this feel intelligent rather than annoying:

- **Glow only when there is something genuinely actionable.** A bulb that always glows is a bulb
  nobody looks at. Require a confidence floor *and* a minimum supporting-sample count before glowing.
- **Never block, never reorder under the cursor, never autofill without consent.** Every suggestion
  is an offer the user accepts explicitly. There is prior art of a rail moving fields under the
  cursor being treated as a defect — do not repeat it.
- **Always say why and how much.** "Usually *Forward to RE* here — 84% of 217 similar cases." A bare
  recommendation with no denominator will be distrusted, correctly.
- **Dismissible and rate-limited.** If a user dismisses a suggestion type, stop offering it on that
  screen for the session. Never re-glow for the same payload.
- **Off-critical-path.** The rail loads *after* the screen is interactive, and must be cancellable on
  navigation. If it never answers, the screen is still fully usable and nothing indicates an error.
  Assistance failing silently is the correct behaviour here — the opposite of §2.5, where a
  *user-initiated* search failing must say so.
- **Accessible.** Glow cannot be the only signal — colour/animation alone fails WCAG and fails the
  reduced-motion preference the citizen app already honours (`accessibility.service.ts:22-84`). Pair
  it with a count/badge and a live-region announcement, and respect `prefers-reduced-motion`.

A `context-rail` pattern already exists but only in `rbio/rbio-complaint-detail` (`.html`/`.scss`).
Extract it into `components/shared/` as a reusable rail and migrate that screen onto the shared
version; do not fork a second rail implementation. Note that `ngTemplateOutlet` breaks ancestor SCSS
selectors — verify the extracted rail still renders correctly on its original screen.

### 5.2 Tier 0 — memory and continuity

**Do not build draft autosave or draft resume. It already exists.** V99 shipped `StaffDraft` and
`ComplaintEditPresence` (UST673-675) with server-resolved ownership. **[VERIFY]** its state, and if
the *resume affordance* is missing from the UI, surfacing it in the rail is a legitimate small add —
but do not rebuild the storage layer.

What is genuinely greenfield (grep confirmed: no `user_preference` table, no `UserPreference` class,
no preferences service anywhere):

1. **Per-user UI state, persisted server-side**, keyed by `(userId, screenId)`: visible columns,
   column filters, sort, page size, density. `shared/task-grid` already holds exactly these as four
   clean signals — `search`, `columnFilters`, `sortKey`, `page`, plus `hiddenKeys:147` — all purely
   in-memory and lost on navigation. `activePageSize` is a `linkedSignal(:65)` that resets whenever
   the host changes `pageSize`; account for that. The only existing precedent is `crpc_page_size` in
   localStorage (`crpc/deo-home.component.ts:246,509`) — migrate it, don't leave two mechanisms.
2. **Saved views**: named bundles of filters + columns + sort, per user, optionally shareable to a
   role queue. This is where most of the perceived "it remembers me" value lives, and it is the
   safe 80% of the "Excel macro" idea.
3. **Recently viewed cases** for the logged-in user, in the rail. Note two existing localStorage
   visited-markers (`cepc_visitedComplaintIds`, `visitedComplaintIds`) — consolidate rather than add
   a third.

Storage: **server-side**, so it follows the user across machines; a new table in your reserved
migration range, with a **bounded** payload (cap the JSON size and the number of saved views per
user — an unbounded user-writable JSON column is an availability risk). Preferences are per-user
private data: never readable cross-user, and shareable views must be an explicit, audited act.

**Caution: the shared grid is adopted by only 2 of ~33 staff screens** (`cepc/cepc-dashboard`
html:97, `staff/rbio-tasks` html:153) — 31 HTML files still hand-roll `<table>`. So grid-level
features will **not** propagate automatically. Deliver the capability in the shared grid and migrate
a small, named set of screens; do not attempt to convert all 31, and do not claim coverage you have
not delivered.

### 5.3 Tier 1 — precomputed priors

The governing principle: **precompute, never aggregate at request time.** Every one of these must be
answerable by a single keyed lookup against a rollup table or a small static artifact.

1. **Next-action prediction.** From immutable `COMPLAINT_TIMELINE`, aggregate
   `P(action | from_status, performed_by_role, category)` into a rollup table refreshed on a
   schedule. The rail suggests the most likely action with its denominator. The shared
   `workflow-action-bar` exposes `actions` and `selectedId` (`:99-107`) — **suggest, highlight, do
   not auto-select**, because that bar commits real workflow transitions.
2. **Closure-clause recommendation**, ranked by historical co-occurrence with (category, ground,
   entity type, resolution path), honouring `restricted_to_roles`. Reorder or annotate the existing
   `<select>` (`rbio-complaint-detail.html:288`) rather than adding new UI.
3. **Repeat-complainant detector.** `complainant_email` and `complainant_phone` are **already
   indexed** (`Complaint.java:10-23`), so an exact-match count is one cheap keyed lookup: "this
   complainant has 4 prior cases; 3 closed under the same clause." Often more useful than text
   similarity and vastly cheaper. Exact match only — no LIKE, no fuzzy.
4. **Deadline triage.** `re_response_deadline` and `re_response_overdue` are indexed (V86).
   "3 of your 14 cases breach within 48h" is a count over an existing index.
5. **Entity pattern alert**, from a scheduled rollup only: "this entity has N open cases on this
   ground this quarter." Never computed live.
6. **Wire up the canned text that already exists and is connected to nothing.**
   `services/comment-template.service.ts` (`CommentTemplate{title, description, content,
   modeOfReceipt, category}`, `getActive()`, `getByCategory()`) plus
   `communication-template.service.ts` are fully built, with two admin CRUD screens — and **no staff
   remarks, closure, order or notice textarea reads a template today**. Offer role- and
   clause-filtered templates at the point of typing, starting with `workflow-action-bar`'s
   `remarks = model('')` (`:73`), a two-way `model()` that needs no component surgery. **This is the
   single cheapest high-value item in the brief and involves no ML whatsoever. Do it early.**

The long-text surfaces this serves, for reference: `rbio-adjudication` (`noticeContent`,
`awardSummary`, `rejectionGrounds`), `rbio-conciliation` (`summaryNotes`, `failureReason`),
`customClosureText` (maxlength 2000, duplicated in `rbio-complaint-detail.html:300` and
`task-action.html:499`), `crpc/draft-assessment` (`deoRemarks`, `emailBody`),
`aa-draft-assessment` (`complaintSummary`, `remarks`), and `shared/query-thread` (12 textareas).

**Do not build the n-gram typeahead model in this session.** It is deferred deliberately: a
completion model mined from case text is a PII exfiltration channel — it can complete a sentence
containing a complainant's name from a case the typist cannot open. If you want to prepare for it,
the only acceptable preparation is *writing down* the required mitigations (strip digits/names/
amounts at mine time; k-anonymity of ≥5 distinct cases and ≥3 distinct offices per retained n-gram;
artifacts partitioned by role; mine only staff-authored `remarks`, never complainant-authored
`subject`/`description`). Write no model code.

### 5.4 The corpus problem — confront it early

**There is no seed data for `COMPLAINTS`** — zero `INSERT INTO COMPLAINTS` in any migration. And
`entity_code` is documented dirty: the same entity appears as both `'Punjab National Bank'` and
`'PNB'`, and is NULL on many rows (`ComplaintRepository.java:87-90`,
`RbioComplaintListService.java:270-273`).

So on day one you have **nothing to rank, nothing to aggregate, and nothing to validate against.**
Address this in your **first** work item:

- Build a **synthetic corpus generator** as a dev-profile-only seeder in your reserved `@Order`
  range: a few thousand complaints with plausible category/ground/entity/clause distributions,
  realistic timeline chains, closure remarks **in multiple languages** (this is also your only way
  to test §2.3), and a deliberate seam of near-duplicate cases so similarity has a ground truth.
- Make it **deterministic** (fixed seed) so tests are reproducible, and **clearly synthetic** —
  never generate data that could be mistaken for a real complainant. No real names, no real phone
  numbers, no real account numbers.
- Guard it behind a dev-only profile check. Verify it cannot run in `prod` or `openshift`.
- Do **not** "fix" `entity_code` globally — that is a data-migration project owned elsewhere. Handle
  the dirt where you read it, and report it.

If a production or UAT extract turns out to be available, prefer it and say so — but do not block on
one, and do not copy production PII into a dev database.

---

## 6. The performance contract

This section is a **hard constraint**, not advice. Pre-fetching multiplies query volume against a
system that already has serious problems. Violating any bullet means the deliverable is rejected.

### 6.1 Fix these first — they are load-bearing prerequisites

1. **Add the missing indexes.** `assigned_role` has **no index in any file**, yet it is the predicate
   for every role-queue read (`RbioComplaintListService:382,208`, `WorkflowController:666,715`).
   `department` is ANDed into every RBIO grid query with **no MySQL index** — it exists only in the
   Oracle-only DDL (`database/oracle/V1__complete_schema.sql:143-144`), as does `assigned_officer`.
   Add composites matching the real access patterns: `(department, assigned_role, status)` and
   `(department, assigned_officer, status)`. Because `ddl-auto: update` never applied the Oracle
   indexes, **verify what actually exists in the live DB** rather than trusting the migration files.
2. **Eliminate the five `complaintRepo.findAll()` calls** in
   `service/dashboard/SeniorDashboardService.java:43,67,100,133,163` — full table into heap, with
   `:67` doing `findAll().stream()` and filtering in Java. The only mitigation today is a 300s cache,
   so a cold cache is five full table scans. Replace with aggregate queries.
3. **Turn on query attribution before shipping assistance.** `generate_statistics: false`
   (`application.yml:44`), no p6spy, no slow-query log, `org.hibernate.SQL` at WARN/ERROR. You can
   currently watch the connection pool saturate but **cannot attribute it to a query**. Enable
   Hibernate statistics in dev and expose the existing Micrometer/Prometheus registry (already a
   dependency in `cms-backend/pom.xml:80-84`; exposure `health,info,metrics,prometheus` at `:228`)
   so your own features are measurable. Without this, the first complaint about slowness is
   unfalsifiable and will be blamed on your code.
4. **Add the timeouts and breaker from §2.5.**

### 6.2 Rules for every new query and endpoint you write

- **A declared row cap and a query timeout on every single one.** The precedent to copy is
  `service/report/QueryCompiler.java:42-43` — `MAX_ROWS = 5000` via `setMaxResults` and
  `QUERY_TIMEOUT_SECONDS = 30` via the `jakarta.persistence.query.timeout` hint (applied at `:147,
  235, 261`). **This is the only query timeout in the entire codebase.** Yours should be far
  tighter: assistance queries should be capped in the low hundreds of rows and time out in
  single-digit seconds.
- **No unbounded result sets. Ever.** Every list returns a `Page`, and the page size is clamped
  server-side (`MAX_PAGE_SIZE=200` in `RbioComplaintListService:43` is the precedent).
- **No leading-wildcard `LIKE`.** No index can serve `LIKE '%x%'`; that is what ES is for now.
  Existing offenders you are replacing: `ComplaintRepository:37`,
  `RbioComplaintListService:186-191,195,266`.
- **Never wrap an indexed column in a function.** `UPPER(TRIM(c.entityCode))`
  (`ComplaintRepository:94`) and the `UPPER`/`LOWER`/`TRIM` wraps at
  `RbioComplaintListService:238,253,281,289` defeat `idx_complaint_entity_code` and
  `idx_complaint_status`. Normalise on write, not on read.
- **Rollups are computed on a schedule, never on request.** Scheduled jobs must be keyset-paged,
  rate-limited, cancellable, and safe to run concurrently with live traffic. In a multi-pod
  deployment exactly one pod may run them — and **Hazelcast clustering is deliberately disabled**
  (multicast, TCP/IP and auto-detection all off, `HazelcastCacheConfig:31-33`), so you **cannot**
  use it for leader election. Use a DB-backed lock; the established pattern in this codebase is a
  DB unique key as the multi-pod lock.
- **Cache with a stated TTL, and assume a cold cache.** `HazelcastCacheConfig` sets per-map TTLs
  (`dashboard` 120s, `analytics-summary` 300s, `copilot-precedent` 600s, `categories`/`banks` 3600s,
  `default` 300s) — but because clustering is off this is a **per-pod local cache whose hit rate
  degrades linearly with replica count**. Budget for N× cold misses and never let a cold cache
  trigger a full scan.
- **A separate rate-limit bucket for assistance**, so it can never starve core traffic. `bucket4j`
  already exists: `RateLimitFilter:19` `@Order(1)` over `/api/**` at 100 rps / 3000 per minute
  (`cms.rate-limit.api-requests-per-second: 100`), plus a second gateway bucket at 100/min
  (`cms-api-gateway/.../RateLimitConfig.java:22`). Assistance gets its own, smaller bucket.
- **Async work uses bounded queues.** `AsyncConfig` is `maxPoolSize=50, queueCapacity=500` —
  bounded, and correctly so. Do not add an unbounded executor. Define the rejection policy.
- **No N+1.** The anti-pattern to avoid is in front of you: `WorkflowController:659-677` loops
  issuing a query per role and de-dupes with a nested `noneMatch` *inside* the loop (O(n·m)), while
  `resolveRolesForOfficer:679-692` makes **three sequential Keycloak REST calls per request**.
  Assistance must add **zero** synchronous external calls to any page load.
- **Every feature has an independent config kill switch**, defaulting to the safe state, honoured by
  the frontend as "hide the affordance".

### 6.3 Prove the performance claims

Do not assert performance. Measure it and put numbers in your findings:

- For each new endpoint: p50/p95 latency and the query count per call, measured with the synthetic
  corpus loaded (not against an empty table, which proves nothing).
- **An `EXPLAIN` for every new or modified query**, showing the index actually used. A query plan
  showing a full scan is a defect regardless of how fast it ran on 50 rows.
- A before/after on the endpoints you touched in §6.1.
- The rail's added cost to each screen it appears on, and proof that a rail failure or timeout leaves
  the screen fully functional.

---

## 7. Testing

- **Backend baseline is green and you must keep it green.** The last full run was **1371 tests, 0
  failures**. A new failure is almost certainly yours; do not excuse it as pre-existing without
  proving it by the inspection method in §1.1.
- **Frontend E2E has ~113 known failures** that cluster into seed gaps, unbuilt features and stale
  credentials — none caused by the recent UI refactor. Check any failure against those clusters
  before debugging it. During development run **only the specs you touched**; run broader suites once
  at the end.
- **E2E harness:** use `ng serve` on **4202** (a dev-server port needs *both* a dev-local CORS
  allowlist entry *and* a Keycloak redirect URI — this is why 4202 and not another port), your
  backend on 8093, Keycloak 9090. Both `APP_BASE_URL` **and** `UI_BASE_URL` must be set; missing
  either produced ~47 phantom failures previously. `API_BASE_URL` alone does **not** redirect
  browser tests. Import `test` from `../fixtures`, **never** from `@playwright/test` — the latter
  writes to your backend but reads from 8082. Use `identityHeadersFor()` for staff identity rather
  than hardcoding credentials; read staff passwords from `credentials.env`.
- `AntiAutomationFilter` will throttle your own seeding at 100 requests per 60s per client IP,
  returning `429 SUSPICIOUS_ACTIVITY`. Send a distinct synthetic `X-Forwarded-For` per seeded record
  — keep the control enforced rather than weakening it for every session.
- **New spec files, not edits to existing ones**, wherever possible.
- Required new coverage: per-language search retrieval (§2.3); a per-role negative authorisation test
  for similarity (§4); ES-unavailable degradation for both the user-initiated path (must say so) and
  the rail (must stay silent); preference persistence across navigation and sessions; and a rollup
  correctness test against the deterministic synthetic corpus.

---

## 8. Deliverables

1. Working code for §2–§5, committed with explicit pathspecs, in small reviewable commits with
   honest messages. **Do not push.** The user pushes.
2. Paired MySQL + Oracle migrations inside your reserved ranges.
3. `docs/prompts/findings-AGENTIC-21.md`, containing:
   - **Every place this brief was wrong**, with the evidence. The brief was written from a
     time-boxed read of a large codebase; treat contradictions as expected, not as your failure.
   - Resolution of each **[VERIFY]** item.
   - The measured performance numbers and `EXPLAIN` plans from §6.3.
   - The document-extraction sizing decision from §2.4, stated plainly.
   - The licence record from §1.4.
   - **Open asks needing a human ruling**, each with your recommended default and what you did in the
     meantime. Cross-office precedent visibility (§4) is already one of these.
   - Anything you deliberately did not build, and why.
4. A short note on what Tier 2 would need, based on what you learned — **no Tier 2 code**.

---

## 9. How to work

Plan before building; this brief is large and the sequencing in §0 matters. Prefer landing §6.1
(prerequisites) and the §5.3.6 template wiring early — both are small, both de-risk everything after
them, and both are independently valuable if the session runs out of room.

When the brief and the code disagree, **the code wins**. When a requirement and a performance
constraint collide, **the constraint wins** and you report the collision — a feature that takes the
system down is worse than a feature that does not ship. When something needs a policy decision,
default to the restrictive option, build it that way, and put the question in your findings. Do not
guess at statutory, authorisation or data-retention semantics.

Measure rather than assume. "The analyzer is configured" is not evidence that Tamil search works;
"the panel renders" is not evidence that authorisation holds. Show the test.
