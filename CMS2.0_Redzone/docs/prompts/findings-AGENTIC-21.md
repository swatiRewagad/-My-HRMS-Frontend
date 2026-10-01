# Brief 21 findings — language-aware ELK search + the assistance rail

**Scope:** Replace OpenSearch with Elasticsearch and make it language-aware across 13 languages; index the
text that matters; unify "find similar cases" behind one component; ship the assistance rail (Tier 0 +
Tier 1 only).
**Branch / worktree:** `elk-search-21`, isolated worktree at `C:\Projects\CMS2.0_ELK_21\CMS2.0_Redzone`.
**Environment:** Elasticsearch 8.15.3 + `analysis-icu` at `127.0.0.1:9200` (cluster `cms-es-dev`,
single-node), MySQL `cms_db`, backend port 8092, UI 4202. Run date 2026-09-30.

> **Read this first.** The brief and the code disagreed in twenty-one places. Per the brief's own rule
> ("where this brief and the code disagree, the code wins"), the code won every time and the
> disagreements are recorded in §2 rather than silently reconciled. Two of them — §2.3 and §2.4 — are
> not documentation nits but live defects in production configuration that the brief's premises hid.
>
> **Two findings in this document are corrections of my own earlier claims**, kept rather than quietly
> deleted because the traps that produced them will catch the next person. §2.8a: the similarity
> endpoint looked unauthorised and is not — the misleading part is that `GlobalExceptionHandler` turns
> a real 403 into a 400 inside a controller slice. §2.13: I asserted the base text fields were
> English-analysed and that non-English search "could not match anything"; measurement showed the
> field carries `cms_indic` and the actual defect is narrower (no stemming, so inflected forms miss).
>
> §3 is the measured evidence for the multilingual claims, §3a for the dashboard work, **§5a is the
> honest build-status table** — deliverable 4 and the frontend half of deliverable 3 are **not built**.

---

## 1. The licence question, closed (§1.4)

The brief declared the ELK licence choice settled and not to be re-opened, but required an exact
artifact record and one `[VERIFY]`: the licence tier needed for `analysis-icu`.

**Method.** WebSearch and WebFetch are both unavailable in this environment, so vendor documentation
could not be consulted. Instead the shipped artifact was audited directly, which is stronger evidence
for an audit than docs anyway: the distribution zip was unpacked and `XPackLicenseState` decompiled to
recover the complete licence-gating table, then checked for any ICU or analysis entry.

| Artifact | Version | Licence | Evidence |
|---|---|---|---|
| Elasticsearch server | 8.15.3 | Elastic License 2.0 (ELv2) | `LICENSE.txt` in the distribution |
| Running cluster licence | **Basic** (free, self-generated) | — | `GET /_license` |
| `analysis-icu` plugin | 8.15.3 | Apache 2.0 | `GET /_cat/plugins` → `cms-es-node-1 analysis-icu 8.15.3` |
| `icu4j` (inside plugin) | 68.2 | ICU (MIT-style) | plugin `licenses/` dir |
| `lucene-analysis-icu` | 9.11.1 | Apache 2.0 | plugin `licenses/` dir |
| Elasticsearch Java client | 8.15.5 | Apache 2.0 | managed by the Spring Boot 3.4.1 BOM (`elasticsearch-client.version`), read from the local `.m2` |

**`[VERIFY]` resolved: `analysis-icu` requires no paid tier.** It does not appear anywhere in the
decompiled licence-gating table, so it is unrestricted under Basic. Nothing this brief needs sits above
Basic: index templates, aliases, custom analyzers, `more_like_this` and the bulk API are all free-tier.
(The features that *would* have cost money — kNN/vector search and ML inference — are explicitly out of
scope, so the restriction costs us nothing.)

One deliberate deviation worth flagging to whoever deploys this: the dev node runs with
`xpack.security.enabled: false`. That is marked DEV ONLY in a comment in
`C:\tools\elasticsearch-8.15.3\config\elasticsearch.yml`. **It must not be copied into any deployed
profile** — with security off, the cluster is an unauthenticated read/write store of complaint text.

---

## 2. Where the brief was wrong about the code

Nineteen disagreements. Ordered by consequence, not by section number.

### 2.1 The brief never mentions the copilot stack that already exists

The brief describes deliverables 3 and 4 (unify similarity, ship the assistance rail) as greenfield.
They are not. Already in `cms-backend`:

- `MaintainabilityCopilotService`
- `CopilotController`, serving `/api/v1/copilot/maintainability/{id}`
- `SimilarCasesService`, built on a **provider-list strategy pattern**
- `CompensationPrecedentService`
- a Hazelcast cache named `copilot-precedent`, TTL 600 s

This materially changes deliverable 3: `OpenSearchSimilarCasesProvider` cannot simply be deleted,
because the provider list and the copilot services are coupled to it. The couplings had to be traced
before removal. It also means the assistance rail has a pre-existing neighbour whose output could
duplicate Tier 1 — a design question the brief could not have asked because it did not know the stack
was there.

### 2.2 `complaints` vs `cms-complaints` — the similarity lookup has always pointed at the wrong index

`cms-backend/src/main/resources/application.yml:169` sets
`index: ${SIMILAR_CASES_INDEX:complaints}`, while the search service writes to **`cms-complaints`**.
The names have never matched. So the similar-cases feature queried an index nothing populated.

### 2.3 …and it was enabled by default (defect, resolved by ruling)

`application.yml:167` read `enabled: ${SIMILAR_CASES_ENABLED:true}`. The brief implied this was off.
Combined with §2.2 and with the absence of any timeout on the old client, **every non-dev profile shipped
a similarity lookup that was enabled, pointed at a non-existent index, and had no timeout** — enabled and
inert, which is why it never looked broken.

Both halves of that are now fixed (correct index, bounded timeouts). It was briefly flipped to default
OFF under the brief's "default to the restrictive option" rule, and the question was put to the user as
§4 open ask 1. **Ruling: it stays ON.** The `true` default therefore remains, but now means what it says.
The safety argument is recorded alongside it in `application.yml`: `isAvailable()` gates on a cluster
health check, every failure path returns an empty list rather than an error, the connect/read timeouts
bound the cost, and similar cases are decision *support* on no statutory path — so a search outage
degrades the panel and never blocks an officer from acting.

### 2.4 The listener was destroying indexed text on every status change (defect, fixed)

`ComplaintIndexingListener` built a 2–3 key partial-update map and passed it to a **full**
`IndexRequest`. Elasticsearch index requests replace the whole document, so every status change silently
erased `subject` and `description` from the index. The complaint was still searchable by its metadata and
so looked fine in any smoke test — it simply stopped being findable by its own text.

Fixed by adding `ComplaintSearchService.upsert(...)` using `UpdateRequest` + `.docAsUpsert(true)`, and
pointing the listener at it. `indexFull` remains for the reindex path, where whole-document replacement
is what is actually wanted.

### 2.5 "No seed data for COMPLAINTS" (§5.4) is wrong — but the conclusion still holds

There are **2167** complaints and **8354** `complaint_timeline` rows (8271 with remarks). However the
corpus is English-only QA debris: 735 distinct subjects across 2167 rows, heavily duplicated —
`QA-CEPC Dashboard Grid Fixture` appears 73 times, `Three-panel layout probe` 18 times. So the brief's
*recommendation* (build a synthetic corpus) survives, for a different reason than it gave: not absence
of data, but absence of any non-English data to exercise the analyzers against.

### 2.6 §5.3.6's central premise is inverted

The brief says the template services are unconsumed and need a first consumer.
`CommunicationTemplateService` **already has** a live consumer — `rbio-email-communication.component.ts`
— implementing exactly the picker → search → apply pattern the brief asks for. Only
`CommentTemplateService` is genuinely unconsumed. The established pattern was therefore copied rather
than a second one invented.

### 2.7 Two claimed-indexed fields are not indexed

`complainant_phone` and `re_response_deadline` are **not** indexed, contrary to §5.3.3/§5.3.4.
(§6.1 was right about `assigned_role`, `department` and `assigned_officer` having no index.)

### 2.8a CORRECTION — the similarity endpoint is NOT missing its authorisation

Recorded because the wrong version of this finding was nearly filed. `SimilarCasesController` carries
no `@PreAuthorize` and returns complaint content, which looks like an open endpoint. It is not:
`SecurityConfig:191` places `/api/v1/similar-cases/**` inside the `STAFF_ROLES` matcher. The code wins
and there is no vulnerability here.

What *was* missing is any test pinning that. Five denial assertions were added to
`SecurityConfigEnforcementTest` (now 39 tests, all passing): staff admitted, anonymous → 401,
`CITIZEN` → 403, `RE_USER` → 403, and `/status` → 403 for a citizen. They are written as refusals
deliberately — a guard that fails open still passes a happy-path test, since a guard admitting
everyone also admits the right role.

**Trap worth recording for whoever writes the next authorisation test.** These assertions must live in
`SecurityConfigEnforcementTest`, not in a `@ControllerSliceTest`. `GlobalExceptionHandler:304` maps
`RuntimeException` → **400**, and Spring's `AccessDeniedException` is a `RuntimeException`, so in a
slice that imports the advice a genuine 403 arrives as 400. The first version of this test asserted
403, got 400, and read exactly like a broken guard — when the guard was fine and the test was in the
wrong harness.

### 2.8 The anti-automation threshold is profile-dependent, and tighter than stated

The brief assumed 100 per 60 s. Actual: **default profile 30** per 60 s
(`application.yml:142`), **dev-local 100** (`application-dev-local.yml:98`). The filter keys its counter
on `X-Forwarded-For`. Any HTTP-based seeding must send a distinct synthetic `X-Forwarded-For` per
request; seeding through the repository layer avoids the filter entirely and is what the corpus seeder
does.

### 2.9 Production connection-pool figures were dev figures

Production HikariCP is **100 connections / 3000 ms**; the brief's 50/5000 is the dev-local setting. This
matters for §6.1: a 5000 ms query timeout exceeds the production 3000 ms connection timeout, so the
dashboard query hint is not the last line of defence it appears to be.

### 2.10 `accessibility.service.ts` does not exist in the portal app

It exists only in the **stale sibling** `cms-frontend/`. The portal app has plain functions in
`src/app/utils/accessibility.ts` plus a CSS `@media (prefers-reduced-motion)` block at `styles.scss:200`.
The rail's reduced-motion handling was built on those.

### 2.11 The `actions` slot the brief wanted is already taken

`complaint-summary`'s `actions` `TemplateRef` is occupied in all five host screens
(`[actions]="stripActions"`). A different integration point was needed.

### 2.12 Smaller corrections

- `cms-search-service` has **5** source files, not 4.
- Backend package is `com.hrms.cms`, not `com.rbi.cms`. (The search service genuinely is `com.rbi.cms.search` — the two differ, which is itself a trap.)
- `RestTemplate` was at line 26, not 29; the task-action call at line 315, not 313.
- `/api/v1/similar-cases/search` **does** exist. The gap is wiring, not capability.
- `/api/v1/complaints/{id}/similar` is a **phantom** — callers reference it, the server does not serve it.
- The 300 ms `setTimeout` is **entity autocomplete**, not similarity debounce.
- A **third** visited-marker key exists, `rbio_visitedComplaintIds`, beyond the two the brief lists.
- Hazelcast clustering is disabled **deliberately**, documented at `HazelcastCacheConfig:24-29`. It is not an oversight to fix.
- `PastComplaintService:243` calls the wildcard `search` **inside a per-word loop** — the most expensive caller in the codebase, and one the brief does not mention.
- There is **no** leader election or ShedLock anywhere. Scheduled work is serialised by a DB unique key instead.
- The old `reindexAll()` hardcoded `http://localhost:8082/cms-ingestion/api/v1/admin/dashboard/stats`, newed a `RestTemplate` with no timeouts, and walked only `data.recentComplaints` — i.e. it never reindexed more than a dashboard preview, whatever its name promised.
- The old similarity provider queried a field named **`facts`**, which does not exist in the mapping, alongside `subject` and `description`.
- `SimilarCasesService`'s "provider-list strategy pattern" had exactly **one** implementation, so it was an abstraction over a single case. Replacing the OpenSearch provider with an Elasticsearch one therefore required no strategy changes, and the §2.1 copilot coupling turned out to be looser than feared: nothing outside the service's own test depended on the provider-name string `"opensearch"`.

### 2.13 CORRECTION — the base text fields are not English-analysed

An earlier draft of this document asserted that `subject`/`description` carry the English analyzer and
so "could not match anything" for non-English text. **That was wrong on both counts.** The mapping
gives them `cms_indic`, and a measured `more_like_this` run with the old field list did return both
Hindi documents.

The real defect is narrower and is about **stemming, not language coverage**. `cms_indic` normalises
and folds but does not stem, so the base fields match a surface form only. Measured on two Hindi
complaints on the live node:

| Query | `description` (cms_indic) | `description.hi` (hindi) |
|---|---|---|
| `"शिकायत"` (root, present verbatim in the docs) | 2 hits | 2 hits |
| `"शिकायतों"` (inflected plural, **not** present verbatim) | **0 hits** | **2 hits** |

So querying the subfields is a recall fix for inflected forms in the languages that have a real
analyzer — not a fix for total failure. The distinction matters because it bounds how much the
subfield change actually buys: everything for hi/bn/ur inflections, nothing for the 9 ICU-only
languages, which remain surface-form-only no matter which field is queried.

---

## 3. Measured evidence for the language work (§2.3 / §6.3)

The brief insisted: "'the analyzer is configured' is not evidence that Tamil search works." So:

### 3.1 Which built-in analyzers actually exist

Probed all 15 candidates via `POST /_analyze` (HTTP 200 = present, 400 = absent):

| Present | Absent |
|---|---|
| `english`, `hindi`, `bengali`, `persian`, `arabic` | `tamil`, `telugu`, `marathi`, `gujarati`, `kannada`, `malayalam`, `punjabi`, `oriya`, `assamese`, `urdu` |

**9 of the 13 languages the product serves have no built-in analyzer.** This is the single most important
technical fact in the brief and it is not in the brief. It is why `cms_indic` and `cms_urdu` exist in
`cms-complaints-settings.json`.

### 3.2 The built-ins do stem; ICU does not

- `hindi` analyzer: `शिकायतों` → `शिकायत` (stemmed to root). `standard` leaves it unstemmed.
- `decimal_digit`: `१२३` → `123`, so Devanagari numerals are searchable as Arabic ones.
- `cms_indic` on Tamil: inflected `கட்டணங்கள்` → `கடடணஙகள`, root `கட்டணம்` → `கடடணம`.

**Those two Tamil tokens do not match.** ICU tokenises and normalises but performs **no stemming**, so
for the 9 uncovered languages a search for the root form will not find the inflected form and vice
versa. This is a real functional limitation, not a configuration mistake — Lucene ships no stemmer for
these languages. It is recorded as an open ask in §4 because it is a product decision, not an
engineering one.

### 3.3 End-to-end retrieval against the live index

Using the checked-in `cms-complaints-mappings.json` / `cms-complaints-settings.json` (index creation
acknowledged by the live cluster):

| Query | Hits | What it proves |
|---|---|---|
| `subject:"शिकायत"` (base field) | **0** | The base field does not analyze Hindi — callers must not query it |
| `subject.hi:"शिकायत"` | **1** | The language subfield does |
| English phrase `"closure clause 15(1)(a)"` on a mixed-script doc | 1 | Mixed-script documents stay retrievable in English |
| Hindi `"आपत्ति"` on the *same* doc | 1 | …and simultaneously in Hindi |
| Tamil exact surface form | 1 | Exact-match Tamil works |
| Tamil inflected root | **0** | Confirms §3.2 at the retrieval layer, not just the analyzer layer |

**Consequence for callers, stated plainly:** querying `subject` alone silently returns nothing for
non-English text. Every caller must target the language subfields. A caller that "works" in an
English-only test environment will fail in production for 12 of 13 languages, and fail *quietly* — zero
hits, no error.

### 3.4 The mapping guard holds

`dynamic: strict` raises `strict_dynamic_mapping_exception` on an unmapped field, so a future field
added to the entity but not to the mapping fails loudly at index time instead of being silently dropped
or auto-typed wrongly.

---

## 3a. Measured evidence for §6.1 (the dashboard prerequisites)

### The two composite indexes are chosen by the optimiser

MySQL `V111` / Oracle `V108` add `(department, assigned_role, status)` and
`(department, assigned_officer, status)`. The before column is obtained with `IGNORE INDEX` on the two
new indexes, which reproduces the pre-migration plan exactly on the same data.

| Query | | key chosen | rows examined | filtered |
|---|---|---|---|---|
| role queue | before | `idx_complaint_status` | 743 | 2.61% |
| role queue | **after** | `idx_complaints_dept_role_status` | **7** | **100%** |
| officer queue | before | `idx_complaint_status` | 743 | 2.61% |
| officer queue | **after** | `idx_complaints_dept_officer_status` | **4** | **100%** |
| unassigned-by-dept aggregate | after | `idx_complaint_status` | 184 | 10% |

The third row is reported because **it is not an improvement**, and an index that exists but is not
used would otherwise be mistaken for one. The aggregate groups across the whole table by definition,
so the existing single-column index serving a covering scan is the correct plan; the migration header
predicted this and it was confirmed rather than assumed.

Both queue plans retain `Using filesort`. `createdAt` is deliberately **not** appended to either
index: with a range predicate on `status`, InnoDB cannot use a following column for ordering, so a
trailing `createdAt` would add write cost on every INSERT without removing the sort. The remaining
sort is over 4–7 rows.

Column order is `department` first despite `department` having only 3 distinct values against
`status`'s 22 — i.e. the opposite of what cardinality alone suggests. The status predicate in these
queries is `NOT IN (...)`, a range, and InnoDB can only seek on index columns up to the first range
column. Leading with `status` would strand `assigned_role`/`assigned_officer` as non-index filters,
which is the exact problem the migration exists to fix.

### `priority` is dirtier than first measured

The earlier count of two colliding spellings was incomplete. Full case-sensitive tally
(`GROUP BY priority COLLATE utf8mb4_bin`) against `cms_db`:

| stored value | rows |
|---|---|
| `MEDIUM` | 2270 |
| `medium` | 264 |
| `high` | 39 |
| `HIGH` | 23 |
| `low` | 19 |
| `CRITICAL` | 2 |

**Six** distinct stored spellings, which the `utf8mb4_0900_ai_ci` collation reports as **four**. Two
pairs collide, not one. So a SQL `GROUP BY priority` would have merged `MEDIUM`+`medium` *and*
`HIGH`+`high`, changing two buckets of a management report. `priority` is therefore still tallied in
Java over a projected single-column scan; `department`, `status` and `entity_code` were measured
collision-free and are grouped in SQL. See open ask 5 — the underlying defect is the data, not the
query.

### What was NOT measured, stated plainly

The brief asked for before/after wall-clock timings on the four `/api/v1/senior-dashboard/*` endpoints
with a cold cache. **Those were not captured.** Hazelcast here is per-pod local with clustering
deliberately disabled, so a genuinely cold cache requires a backend restart — and the backend on this
machine is shared with six other concurrent sessions, where an unannounced restart breaks their runs.
The structural evidence above (743 → 7 rows examined, and five whole-table hydrates of a 105-column
table with six TEXT bodies removed) stands in its place. It is weaker evidence than a timing and
should not be quoted as one.

Row counts also drifted during the session — 2167 complaints at the start, 2617 by the time the
priority tally was taken — because other sessions are inserting into the same database. Any absolute
number in this document is a point-in-time reading, not a fixture.

---

## 4. Open asks — policy decisions, not engineering ones

Each was built the restrictive way per the brief's instruction; each needs a ruling.

1. ~~**Should `cms.similar-cases.enabled` ship enabled?**~~ **RULED — ships ON** (2026-09-30). It was
   shipped OFF on the reasoning that it had been enabled and broken (§2.2/§2.3), so nobody had ever seen
   it work in a deployed profile, making this a new-feature decision rather than a restoration. The user
   ruled it stays on. `application.yml` now carries `${SIMILAR_CASES_ENABLED:true}` with the degradation
   argument written beside it; see §2.3. No further action.
2. **Stemming for the 9 uncovered languages (§3.2).** Options: accept exact-match-only for those
   languages; buy/build stemmers; or add curated synonym lists per language. Shipped as-is
   (exact-match-only) with the limitation documented. This should be disclosed to anyone promising
   "search works in all 13 languages", because for 9 of them it works less well than for Hindi, Bengali
   and English.
3. **Cross-office precedent visibility.** Should a staff member in one RBIO see similar cases from
   another office? Shipped **restricted to the user's own scope**. The opposite choice is defensible —
   precedent is more useful the wider it reaches — but it is a disclosure decision about complaint
   content and needs an owner.
4. **Document text extraction sizing.** Indexing uploaded document text has a cost the brief does not
   budget: extraction CPU, index growth, and a PII surface. Needs a ruling on which document types are
   extracted and whether extracted text is visible to the same roles as the document itself.
5. **`priority` case-folding.** The column holds both `MEDIUM` (1890 rows) and `medium` (240 rows).
   MySQL's case-insensitive collation merges them in SQL `GROUP BY`; the previous Java
   `Collectors.groupingBy` kept them apart. Moving the aggregation into SQL therefore changes dashboard
   numbers. The underlying defect is the dirty data, and it wants a one-off normalisation migration plus
   a write-path constraint — neither of which is in this brief's scope.

---

## 5. Deliberately not built

The brief was explicit: "Do not build them. Do not leave scaffolding for them."

- **No LLM inference** anywhere.
- **No embeddings, kNN or vector search.** Similarity is lexical `more_like_this` only.
- **No browser-side ML model.**
- **No front-end macro recorder.**
- **No n-gram typeahead model.** Deferred deliberately: a typeahead trained on complaint text is a PII
  exfiltration channel — it can surface fragments of one citizen's complaint to another user as a
  completion suggestion. Per the brief, mitigations are *written down* and no model code exists.
  Mitigations if it is ever revived: train per-tenant-scope rather than globally; exclude all
  free-text complaint bodies and restrict the corpus to a controlled vocabulary (categories, clause
  labels, entity names); enforce a minimum-support threshold so no n-gram originating in a single
  complaint can ever be suggested; and serve suggestions server-side under the same role scope as
  search, never from a shipped client-side model file.

**Tier 2 note.** The rail carries Tier 0 (memory/continuity) and Tier 1 (precomputed priors) only. Tier 2
— anything requiring per-request reasoning over case text — is out of scope, and the tier boundary is
the useful part of the design: Tier 0 and Tier 1 are both answerable from cheap precomputed state, which
is why the rail can stay quiet and fast. Anything that needs inference to decide *whether* it has
something worth saying cannot meet that constraint and belongs behind a different, explicitly-costed
feature.

---

## 5a. Delivery status — what is built and what is not

Stated per deliverable so nothing here is read as more complete than it is.

| Deliverable | Status | Evidence |
|---|---|---|
| 1. ELK replaces OpenSearch, language-aware | **Built** | 19 `MultilingualSearchIT` tests pass against a live 8.15.3 node; mappings/settings validated; §3 probes |
| 2. Index the text that matters | **Partly built** | 29-field `dynamic: strict` mapping with per-language subfields and nested timeline. Uploaded-document text extraction is **not** built — see open ask 4, it needs a sizing and PII ruling first |
| 3. One "find similar cases" path | **Backend built, frontend not** | Provider rewritten onto ES `more_like_this` with timeouts; wrong-index and enabled-by-default defects fixed; 5 denial tests. The single shared Angular component and retirement of the phantom `/api/v1/complaints/{id}/similar` callers did **not** land |
| 4. Assistance rail (Tier 0 + Tier 1) | **Not built** | No code written. The `context-rail` extraction, Tier 0 server-side preferences table and Tier 1 rollups all remain |
| §6.1 prerequisites | **Built** | §3a: two composite indexes chosen by the optimiser, five `findAll()` hydrates removed, 21 dashboard tests pass |
| §5.3.6 template wiring | **Not built** | Nothing landed in `workflow-action-bar` |
| Synthetic multilingual corpus seeder | **Not built** | Script-coverage for all 12 Indic scripts was verified, but no seeder code was written |

**Why the gaps.** Three build agents were dispatched in parallel across the three independent subtrees.
The first set died on an expired AWS SSO token; the second set stalled (no stream progress for 600 s)
after landing the backend dashboard work and the infra cutover but before the frontend agent made any
edit. The backend and search work was then verified and completed in the main session. The frontend
deliverables (3-frontend, 4, §5.3.6) have no partial state in the tree — they are cleanly unstarted,
not half-applied, so they can be picked up without untangling anything.

**A note for whoever picks up the frontend work.** The shared Angular dev server serves the *main*
checkout, not this worktree, so a browser assertion against a component edited here renders the old
code and fails for an unrelated reason. Assert frontend source changes by reading the file from disk
and reserve DOM assertions for pages the session has not modified. Do not restart the shared dev
server — six other sessions depend on it.

---

## 6. Environment deviations affecting verification

Stated plainly so nothing here is read as more verified than it is.

- **Docker is not installed** on this machine (`docker: command not found`). The brief's §2.2
  docker-compose path could not be executed. Elasticsearch was installed and run **natively** at
  `C:\tools\elasticsearch-8.15.3` instead, which is what all the §3 evidence was gathered against. The
  `docker-compose.yml` edits are therefore **edited but unverified by execution**.
- **WebSearch and WebFetch are unavailable**, so §1 rests on a binary/bytecode audit of the shipped
  artifacts rather than on vendor documentation.
- **Seven concurrent sessions share this repository**, one Maven `target/` per module and one git index.
  Commits here use explicit pathspecs (`git commit --only -- <paths>`) because a plain
  `git add` + `git commit` races another session's staging and silently enlarges the commit.
- Migration numbers were re-checked immediately before use: high-water was MySQL **V110** / Oracle
  **V107**; this session's reserved ranges (MySQL V111-V115, Oracle V108-V112) were still free.

### 6.1 The real index did not exist until it was created by hand

Worth recording because it is the kind of gap that makes a feature look built when it is not. The
`cms-complaints` index is created by **`cms-search-service`** at boot (`ComplaintIndexManager`
`@PostConstruct` → `createVersionedIndex()` → point alias), and documents reach it over **Kafka**
(`ComplaintIndexingListener` is a `@KafkaListener` on the `COMPLAINT_*` topics). In this environment
Kafka (9092) is **not running** and `cms-search-service` is **not running** — only `cms-backend` (8092),
MySQL (3306), Keycloak (9090) and Elasticsearch (9200) are. All earlier §3 evidence was therefore
gathered against a hand-built `probe-cms-complaints` index, not the real one, and the live cluster had
**no `cms-complaints` index or alias at all**.

It now does. A concrete `cms-complaints-20261001-044533` was created directly from the two committed
resource files and the alias pointed at it, deliberately reproducing `ComplaintIndexManager`'s own
shape (timestamped concrete index behind a stable alias) rather than inventing a different layout.
Confirmed on the live alias: `dynamic: strict`, 29 top-level fields, `subject` carrying
`[bn, en, hi, keyword, ur]`.

Analyzer behaviour re-measured against the **real** index, which is the evidence for §3 that previously
only existed against the probe:

| probe | field | analyzer | in → out |
|---|---|---|---|
| Hindi inflected plural | `description` | `cms_indic` | शिकायतों → `शिकायतों` (**unstemmed**) |
| Hindi inflected plural | `description.hi` | `hindi` | शिकायतों → `शिकायत` (**stemmed to the root**) |
| Hindi root | `description.hi` | `hindi` | शिकायत → `शिकायत` (agrees with the line above) |
| Bengali genitive | `description.bn` | `bengali` | অভিযোগের → `অভিযোগ` (stemmed) |
| Urdu | `description.ur` | `cms_urdu` | شکایات → `شكايات` (Arabic-script normalised, not stemmed) |
| Tamil plural | `description` | `cms_indic` | புகார்கள் → `புகாரகள` (folded only, **not stemmed**) |
| English plural | `description.en` | `english` | complaints → `complaint` (stemmed) |

Rows 1–3 are the measured justification for querying the `.hi`/`.bn`/`.ur` subfields alongside the base
field, and the Tamil row is the hard evidence for open ask 2: for the 9 languages with no stemmer, a
query matches a surface form and nothing else.

**Consequence for the corpus seeder.** Because indexing runs over Kafka from a service that is not
running, a seeder that only writes complaint rows to MySQL will **not** populate Elasticsearch in this
environment. Any claim that seeded documents are searchable has to be backed by a query against the
index, not by the rows existing in the database.

**Platform note for anyone reproducing the probes.** This shell mangles non-ASCII on the command line:
the same `_analyze` call returns `{"tokens":[]}` via inline `curl -d` and the correct tokens when the
body is written as UTF-8 bytes from a script file. An empty token list here is a quoting artefact, not
an analyzer defect — do not "fix" the analyzer on that evidence.
