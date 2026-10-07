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

### 2.14 `cms-search-service` could not boot at all (defect in this session's own code, fixed)

Reported by the corpus-seeder agent and then verified directly, because it sat in the ES-migration code
this session wrote. `ElasticsearchConfig` published **two** `RestClient` beans and **two**
`ElasticsearchClient` beans, separated only by socket timeout, with **no `@Primary` between either
pair**. Three injection points take the type without qualifying it:

| Injection point | Declares | Qualified? |
|---|---|---|
| `ElasticsearchConfig.elasticsearchClient` parameter | `RestClient restClient` | **No** |
| `ComplaintIndexManager:43` | `private final ElasticsearchClient client` | **No** (Lombok ctor) |
| `ComplaintSearchService:68` | `private final ElasticsearchClient client` | **No** (Lombok ctor) |
| `ReindexJob:103` | `ElasticsearchClient bulkClient` | Yes — `bulkElasticsearchClient` |

Two candidates against an unqualified injection point is `NoUniqueBeanDefinitionException` at context
refresh, so the service never served a request. **The `RestClient` clash fires first** — before the
`ElasticsearchClient` one that was originally reported — so there were two ambiguities, not one.

Two points worth keeping:

1. **A compile is no evidence here.** The ambiguity is invisible to javac and `mvn compile` passed
   throughout; it only appears when the context refreshes. `ElasticsearchConfigWiringTest` therefore
   refreshes a real context. No Elasticsearch node is needed — `RestClient.builder(...).build()`
   resolves no host and opens no socket — so the wiring is testable with the cluster down. Removing
   either `@Primary` fails 4 of its 5 tests, which is how the test was confirmed to detect the defect
   rather than merely to pass alongside it.
2. **Which bean is primary is a safety choice, not a coin toss.** The short-timeout search pair is
   primary, so a future consumer that forgets to qualify inherits the **2 s** read timeout that guards
   user-facing search rather than the **30 s** bulk one. The cost of the wrong default is then a
   timed-out query; the other way round it is a pinned request thread, which `SearchProperties` itself
   documents as the failure mode the kill switch exists to stop.

This is the clearest instance in this brief of why "it compiles" and "the tests pass" were not
sufficient evidence: both were true of a service that could not start.

### 2.15 `Complaint.onCreate()` silently discards any caller-supplied `createdAt`

Found while seeding. `Complaint.onCreate()` (`Complaint.java:502`) assigns `createdAt`, `updatedAt`,
`filedAt` and `lastStatusChangeDate` unconditionally, so the `@PrePersist` overwrite beats anything the
caller set. This is not theoretical — it has **already broken `DemoDataSeeder`**: its 60 rows are
written with a deliberate 90-day spread, and measured in the live database all 60 land inside
**121.68 ms**:

```
n   MIN(created_at)             MAX(created_at)             spread_ms
60  2026-08-06 13:02:39.927311  2026-08-06 13:02:40.048995  121.6840
```

The consequence reaches past seeding. Any date-range reporting, ageing bucket or TAT figure computed
over demo or seeded data is measuring insert time, not complaint time, and will look plausible while
being meaningless. Worse, `closedAt` is *not* among the overwritten fields, so it survived while
`createdAt` did not — closure then preceded filing on **10 of the 11** closed-and-categorised demo
rows (`CMS-DEMO-1044`: created 2026-08-06, closed 2026-05-31, a window of **-67 days**). The
closure-window query discards non-negative-window rows, which is one of the two reasons the rail's
category prior had no sample (§5b). The same field also derives the **statutory appeal window** in
`AppealClassificationService`.

**Now fixed** (`c83f289`). I initially reported this rather than patching it, on the grounds that
`Complaint` is a shared entity on several sessions' critical path; the user directed that it be fixed,
so it was, and the shared-entity risk was discharged by running the full backend suite rather than
reasoned about (§5c). Defaulting is now per-field and conditional — an unset field still receives
`now()`, a supplied one is honoured. `ComplaintTimestampTest` covers both halves plus the
negative-window form that actually caused harm; **5 of its 8 tests fail with the fix reverted.**

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

### 3.2a The honest per-language tally (corrects an earlier 4/9 framing)

Measured by the seeder's root/inflected pairs (`fc28ab3`) across all 13 languages, not inferred from
which analyzer is configured:

| Behaviour | Count | Languages |
|---|---|---|
| **Stems** — root query finds the inflected form | **3** | `en` (english), `hi` (hindi), `bn` (bengali) |
| **Normalises only** — folds script variants, no stemming | **1** | `ur` (cms_urdu, arabic_normalization) |
| **Surface form only** — exact match or nothing | **9** | `mr`, `te`, `ta`, `ml`, `kn`, `gu`, `pa`, `or`, `as` |

An earlier draft of this document framed this as 4 stemming languages. **That was wrong, and the error
is worth naming:** `mr` routes to `.hi` and `as` routes to `.bn`, so both *borrow a subfield that has a
stemmer*. Borrowing the subfield is not the same as being stemmed — the Hindi stemmer's rules do not
describe Marathi morphology, and sharing the field buys tokenisation and folding but no reliable
stemming. Counting them as covered would have overstated coverage for two of the languages with the
largest speaker populations in scope.

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
| 3. One "find similar cases" path | **Built** | Provider on ES `more_like_this` with timeouts; wrong-index and enabled-by-default defects fixed; 5 denial tests. Shared Angular component + service landed in `262d8cd`, both phantom callers retired, `tsc --noEmit` and `ng build` clean, ESLint clean on all new files |
| 4. Assistance rail (Tier 0 + Tier 1) — backend | **Built** | `c81bc5b`, 12 files. 30 `AssistanceRailServiceTest` tests across 4 nested classes; the `/api/v1/assistance/**` guard is mutation-proven (§5b). It shipped **three priors, but only one of the six §5.3 items** (§5.3.3); the other two are useful priors the brief did not ask for. The accounting is **§5e** |
| 4a. Assistance rail — frontend, kill switch, 13 locales | **Built** | `bc7369b`…`e53e7cc`, 5 commits / 32 files (2026-10-06). 103 backend tests 0 fail; `tsc`/`ng build`/ESLint clean; 7 e2e tests enumerate. The two unwired screens and the un-executed e2e are stated in **§5d** |
| 4b. §5.3.1 next-action prediction | **Built, applied and observed on real data** | 2026-10-06 built; `V115` **applied to `cms_db` 2026-10-07** and the refresh job run for real — 38 cohorts, rail returns a signal in 6ms p50 over HTTP. Rollup table + lease lock; scheduled refresh under a DB-backed lease; one keyed read on the request path; the signal is inert and highlighted, never auto-selected; 13 locales; its own §6.2 rate-limit bucket. **109 tests, 0 failures** — 101 `Assistance*` plus 8 `RateLimitFilterAssistanceBucketTest`. Live measurements in **§5f**; the ~86% silence figure is **confirmed** against the populated table |
| 4c. §5.3.2 closure-clause recommendation | **Not built** | A *ranked* reorder of the existing `<select>` by co-occurrence with (category, ground, entity type, resolution path), honouring `restricted_to_roles`. The shipped `entity-clause-precedent` prior is **not** this: it reports a count beside the complaint and reorders nothing. **§5e** |
| 4d. §5.3.4 deadline triage | **Not built** | Skipped by agreement, not by oversight — it was the one item consciously dropped to fit §5.3.1. **§5e** |
| 4e. §5.3.5 entity pattern alert | **Not built** | Deferred by agreement. Needs a second scheduled rollup; the §5.3.1 lease lock is now reusable for it. **§5e** |
| §6.1 prerequisites | **Built** | §3a: two composite indexes chosen by the optimiser, five `findAll()` hydrates removed, 21 dashboard tests pass |
| §5.3.6 template wiring | **Built** | `comment-template-picker` component + `SimilarCasesTranslationSeeder` (`@Order(69)`, 19 keys × 11 locales) in `262d8cd` |
| Synthetic multilingual corpus seeder | **Built** | `fc28ab3`: 26 `CMS-MLCORPUS-*` rows, dev-local only; `CorpusReindexIT` carries them through the real `ReindexJob`. The measured tally is weaker than the brief implies — see §3.2a |
| cms-search-service boots | **Fixed** | `3e0c4fc`. It did **not** boot at all until this commit — see §2.14 |
| `@PrePersist` no longer discards supplied dates | **Fixed** | `c83f289`. 8 `ComplaintTimestampTest` tests; 5 fail when reverted — §2.15 |
| The category-closure prior can fire | **Fixed** | `cb65c75`. 11 `DemoDataSeederClosureCohortTest` tests; two independent causes, both measured — §5c |

**Backend suite at the gate:** the brief-specific classes are 82/82, 0 failures (48
`SecurityConfigEnforcementTest`, 30 `AssistanceRailServiceTest`, 4 `SimilarCasesServiceTest`), plus 5
`ElasticsearchConfigWiringTest` and 4 `MultilingualCorpusSeederProfileTest`.

**Full `cms-backend` suite after the shared-entity change:** **1447 tests, 0 failures, 0 errors, 0
skipped** across 358 report files. This run is the evidence that changing `Complaint.onCreate()` — a
shared entity on several sessions' critical path — regressed nothing; that risk was discharged by
measurement, not by argument. The arithmetic ties to the prior baseline exactly: **1428 + 19 new =
1447** (8 `ComplaintTimestampTest`, 11 `DemoDataSeederClosureCohortTest`).

**A counting trap worth recording.** A naive tally over `target/surefire-reports/*.xml` reported
**1482 tests and 3 failures**. All three were a *stale* report — `SimilarCasesControllerAuthzTest`,
mtime 09:16 against a run that finished 12:28, for a class that no longer exists at that path (its
authorisation coverage lives in `SecurityConfigEnforcementTest`, which `@Import`s the real filter chain
and so does not hit the 400-vs-403 trap described in §5b). `mvn test` does not clear orphaned reports,
and its own exit code was 0. **Filter reports by mtime, or `mvn clean`, before quoting a total** —
otherwise a deleted test fails your gate forever.

**What is still genuinely not built**, and should not be read as oversight:

- **Uploaded-document text extraction** (part of deliverable 2) — blocked on open ask 4.
- **Tier 1 priors §5.3.4 (deadline triage) and §5.3.5 (entity pattern alert)** — skipped and deferred
  respectively, by ruling. Neither has a stub or a TODO anywhere in the code, which is why they are
  written up in **§5e** rather than discoverable by grep.
- Everything in §5, which was out of scope by instruction.

**Verified by unit test but not observed on real data:**

- Oracle EXPLAIN plans for V109 are **reasoned, not measured** — no Oracle instance was reachable.
- ~~The **next-action rollup has never held a row.**~~ **SUPERSEDED 2026-10-07:** V115 is applied to
  `cms_db`, the refresh job has run for real under its lease, and the rail returns a next-action signal
  over HTTP. See **§5f**. Oracle `V113` remains unapplied — no Oracle instance is reachable, so the
  Oracle half of the pair is still unproven.

The rail's `category-closure-time` prior was in this list too, and is **no longer** — see §5c. The
figure I first reported for it ("79 of 2769 rows carry a `category_id`") was both stale on
re-measurement and the wrong diagnosis; §5c carries the corrected numbers and the two real causes.

**A note for whoever picks up frontend work here.** The shared Angular dev server serves the *main*
checkout, not this worktree, so a browser assertion against a component edited here renders the old
code and fails for an unrelated reason. Assert frontend source changes by reading the file from disk
and reserve DOM assertions for pages the session has not modified. Do not restart the shared dev
server — six other sessions depend on it. Consequently **no part of this UI work has been exercised in
a browser**, and the portal app has no unit-test infrastructure at all (`angular.json` has no `test`
target; `src/` contains zero `.spec.ts`). The frontend evidence above is compile-time and static only.

---

## 5b. Proving the rail's authorisation does not fail open

The brief asks for the test, not the assertion — "'the panel renders' is not evidence that
authorisation holds". A passing authorisation test is weak evidence on its own, because it passes
identically whether the guard works or whether the test never exercised it. So the guard was
**mutation-tested**: the `/api/v1/assistance/**` matcher was deleted from `SecurityConfig`, the suite
re-run, and the file restored from backup.

| | Result |
|---|---|
| Guard present | 48/48 `SecurityConfigEnforcementTest` pass |
| Guard deleted | **exactly 4 failures**, all `Status expected:<403> but was:<200>` |
| Failing cases | CITIZEN and RE_USER × both rail endpoints |
| Guard restored | 48/48 pass again |

Removing the matcher does **not** produce a 403 from some other layer — it produces a **200**. That is
the finding worth keeping: there is no second line of defence behind it. Specifically:

- `@EnableMethodSecurity` is **not enabled anywhere in this application**, so all 31 `@PreAuthorize`
  annotations in the codebase are inert. `AssistanceRailController` documents its own absence of one for
  that reason, rather than carrying an annotation that would read as protection and provide none.
- The `.anyRequest().authenticated()` fallback is **not** a safety net here: a citizen holding a tracking
  session *is* authenticated, and the rail's Tier 1 signals aggregate facts about **other** complaints
  (how many an entity closed under a clause, how long a category takes). The fallback would have been a
  disclosure hole, which is precisely why the matcher is explicit.

Tier 0 isolation is enforced **structurally** rather than procedurally:
`AssistanceRailMemoryRepository` declares exactly one finder,
`findByOwnerUserIdAndComplaintNumber`, and no `findByComplaintNumber` or `findAll` wrapper exists. An
unscoped read of another officer's memory row cannot be written by accident because the query to write
it does not exist. The caller is resolved through `RequestIdentityResolver` and **never** from a request
parameter.

One related trap worth recording for the next person writing such a test: these assertions must live in
`SecurityConfigEnforcementTest`, which `@Import`s the real filter chain. They cannot live in a
controller slice, because `GlobalExceptionHandler` maps bare `RuntimeException` to **HTTP 400** and
`AccessDeniedException` is a `RuntimeException` — so in a slice test a denial surfaces as 400 and an
assertion of 403 fails for a reason that has nothing to do with authorisation.

---

## 5c. Why the category-closure prior never fired, measured

I first reported this as a single data gap — "`category_id` is populated on 79 of 2769 rows" — and
recommended no fix. Measuring it properly before patching showed that diagnosis was wrong twice over,
and the correction is the useful part of this section.

**The count was stale, and the interpretation was misleading.** Re-measured: **117 of 3027** rows carry
a `category_id`. But the ~2,634 uncategorised rows are QA and E2E fixtures, not production filings —
"QA-CEPC Dashboard Grid Fixture" alone accounts for 134 of them. The real filing path **does** set
`category_id` (`ComplaintApiV1Controller.java:169`). So "the panel will stay silent on most
complaints" overstated it: the panel stays silent on most *test rows*.

**There were two independent causes, and fixing either alone would not have worked.**

| | Cause | Measured |
|---|---|---|
| A | Closure preceded filing, so the window was negative and the rows were discarded | **10 of 11** closed+categorised rows; worst `CMS-DEMO-1044` at **-67 days** |
| B | Even with correct dates, no category held enough closed rows | **max 3** in any one category, against `MIN_CLOSURE_SAMPLE` of **5** |

Cause A is `@PrePersist` (§2.15). Cause B is independent of it and would have survived that fix
untouched: one status in six is `"closed"`, spread across eight categories, so 60 random rows cannot
reach five per category at any seed. This is why the fix is two commits, not one.

**The floor was not lowered to fit the fixture.** Five is a statistical guard on what may be shown to
an officer as "this category closes in N days"; the fixture was what was inadequate. The cohort is
sized at six per category and the test *imports* `MIN_CLOSURE_SAMPLE` rather than hardcoding it, so
raising the guard to 10 fails the test and the fixture gets fixed — rather than the two drifting apart
silently, which is the failure mode that produced this gap.

**A third defect surfaced underneath.** The seeder's skip guard was `complaintRepo.count() >= 80` over
the whole table. The shared dev database holds ~3,000 rows, so the guard short-circuited
**permanently**: the demo series could never be rewritten or extended, which is why the corrupt
timestamps persisted as long as they did. It now counts only its own `CMS-DEMO-` series.

**One pre-existing quirk recorded rather than silently excluded.** The random generator picks
`createdAt` as little as 0 days ago and then adds up to 20 days of resolution and closure, so at seed
42 **2 of 60** rows close *after* now(). That is harmless to the prior — a future closure still yields
a positive window and is measured, not discarded — but a dashboard showing complaints closed next week
is worth knowing about before someone reports it as a bug in the rail. The test asserts the quirk
(`randomRowsCanCloseInTheFuture`) rather than filtering it out of view.

**Still outstanding:** the fix affects new inserts only. The 60 existing `CMS-DEMO-*` rows in the
shared dev database **remain corrupt** until that series is deleted and reseeded — which the new
prefix-scoped guard now makes possible. I have not deleted them, because that database is shared with
six other sessions and is not mine to truncate.

---

## 5d. The rail's frontend (2026-10-06) — and the correction that started it

### The premise this session opened with was wrong

The task was framed as "the frontend for the assistance rail is not built". It was. The components,
the service and the host wiring existed from a prior session and were sitting **uncommitted** in the
working tree. The work was therefore retargeted from *build it* to *finish and commit it*, which is a
different and much smaller job.

The belief had a traceable source: this repository's own notes asserted "the assistance rail has **no
frontend consumer** — backend-only, test by HTTP, never by browser". That was true when written and is
now false. The note has been marked superseded rather than deleted, because the sequence is the useful
part: **an uncommitted feature is indistinguishable from an unbuilt one** to everyone except the
session that wrote it. On a repository shared by several concurrent sessions that is not a filing
inconvenience, it is a duplicate-work hazard.

### What was actually missing

| Gap | Resolved by |
|---|---|
| No kill-switch endpoint, so the client could not honour §6.2 | `GET /api/v1/assistance/status` → `{"available": <bool>}`; `/rail` and `/rail/memory` refuse when off |
| `or` and `as` never seeded — 11 locales of 13 | `seedLocale("or", odia())` + `seedLocale("as", assamese())`, 19 keys each |
| Signals carried server English only | `params` on the DTO; the component localises from `kind` + `params` and keeps `title` as the fallback of record |
| Dismissal state held in the panel component | Moved into `AssistanceRailService` — see below, this was a real defect |
| Zero e2e coverage | `e2e/staff/assistance-rail.spec.ts`, 7 tests |

### The one real defect found, and why a screenshot would not have shown it

Dismissals lived in a field on the panel component. Both hosts render the panel inside
`@if (showAssistancePanel())`, so **closing the drawer destroys the component** and takes its
dismissals with it. The bulb outlives the panel, so it would light again over exactly the rows the
officer had just silenced — the brief's "never re-glow for the same payload" rule, broken.

What makes it worth recording is the shape of the failure: the panel renders correctly before the
dismissal and correctly after it. Only the *sequence* dismiss → close → look at the bulb reveals it,
and that is not a state any screenshot or static review captures. It is also the single failure mode
that makes the feature worthless rather than merely imperfect: a bulb that glows over nothing trains
officers to ignore it.

The fix puts the rule in one place, `AssistanceRailService.verdict()`, keyed by complaint number. The
bulb and the panel are separate components *by necessity*, not by choice, and a glow rule written out
in both would drift — with the bulb being the half that drifts, because a wrong bulb looks identical
to a right one. The e2e spec asserts the sequence, not the states.

Keeping the panel alive with `[hidden]` was the alternative and is wrong here: the rail holds nothing
the officer can type into, so there is no unsaved state to protect (the opposite of the assessment
fields, which is why *those* are hidden rather than destroyed), and the drawer slot is shared with the
similar-cases panel.

### Where the bulb is, and the two omissions that are deliberate

Four screens carry it: `task-action`, `crpc/draft-assessment`, and `rbio-complaint-detail` — the last
through the shared `app-context-rail`, passed the glow **as data** because that component's DOM is
encapsulated and a second rail on a screen that already has one is what the brief rules out. The glow
it receives is computed from the same `verdict()` the bulb uses.

Not wired, on purpose:

- **`physical-letter`** declares `complaintNumber` and **never assigns it** anywhere in the component
  (`grep` for `this.complaintNumber =` exits 1). Only a `draftId` exists and the route has no `:id`.
- **`reviewer-assessment`** has a `draftId` plus a post-approval `generatedComplaintNumber` that is
  sometimes a **client-fabricated** `CMP-${date}-${rand}`.

Either would glow over nothing, which is the failure above arrived at by a different route. The wiring
is present in both and activates the moment the screen can name the complaint it is about. This is a
**limitation of those screens**, not of the rail, and it should not be "fixed" by inventing an
identifier on the client.

Also deliberate: `draft-assessment`'s `.icon-sidebar` is `display:none` ("icons moved inline"), so the
bulb there is the inline `iib-btn` — an extra bulb in the hidden strip would be a duplicate testid
that fails Playwright strict mode on an invisible control.

### The glow floor, answered

§5.1 asks for a confidence floor. It exists but is **not uniform**, and that is defensible:
`MIN_CLOSURE_SAMPLE = 5` is enforced at `AssistanceRailService.java:357` for `category-closure-time`
because that signal reports a **median** — a statistical claim, which needs a sample. The other two
Tier 1 kinds (`complainant-history`, `entity-clause-precedent`) gate only on `> 0` because they are
**exact counts** of things that did happen, not inferences from them. A floor of 5 on an exact count
would suppress true statements.

### Translation failure modes are all silent, which is why there is a test for them

A locale never seeded, a key normalised to an underscore, and a `{{placeholder}}` lost in translation
all surface identically: a panel that merely looks "not translated yet". No error, no empty row,
nothing in a log. That is exactly how `or` and `as` came to be missing while the seeder read as
finished.

`AssistanceRailTranslationCoverageTest` (10 tests) drives the real `run()` against mock repositories
and asserts on what reached `translationRepo.save` — reflecting into the private maps would prove a map
exists that no `seedLocale` call ever reads. It also asserts Assamese **is not** Bengali: the two share
the Bengali-Assamese block, so `seedLocale("as", bengali())` would have satisfied every coverage count
while shipping text that reads as foreign (Bengali `র` where Assamese writes `ৰ`).

Two audit findings during verification were **false alarms**, recorded so nobody re-raises them: Latin
characters flagged in the Odia/Assamese values were EM DASH (U+2014), a genuine Assamese apostrophe in
`য'ত` (U+0027) and the hyphen in `{{section}}-ত`; and a "stray `name` placeholder" was `{{name}}`
appearing inside explanatory javadoc prose, not in a seeded value.

### Gate, stated for exactly what it covers

| Check | Result |
|---|---|
| `npx tsc --noEmit -p tsconfig.json` | exit 0 |
| `npx ng build` | "Application bundle generation complete" — 0 errors |
| ESLint on `shared/assistance-rail/` + the service | clean, no output |
| Backend `Assistance*` + `SecurityConfigEnforcementTest` | **103 tests, 0 failures, 0 errors** |
| `npx playwright test --list` on the new spec | enumerates 7 tests |

**What this gate does not cover.** The e2e spec has been enumerated, **not executed** — the shared dev
servers on 4200/4202 serve the *main* checkout, not this worktree, and restarting them would disrupt
other sessions. So the Playwright evidence is compile-and-enumerate only, and no part of this UI has
been exercised in a browser from here. The portal app still has no unit-test infrastructure
(`angular.json` has no `test` target).

Two `NG8113` warnings appear on files these changes touched. They were proven **pre-existing** via
`git diff` (`SpeechButtonComponent` and `AutoClosureComponent` were already in those `imports` arrays);
the changes only appended the assistance components.

### Still open after this session

1. **`cms.assistance.enabled` ships `false`.** The same question ruled for similar-cases (open ask 1)
   has not been ruled for the rail. Shipped off per the brief's restrictive instruction; `dev-local`
   sets it true. A feature whose failure mode is silence arguably wants to be on where someone is
   looking at it, but that is a product call.
2. **`physical-letter` and `reviewer-assessment` cannot name their complaint.** Fixing it belongs to
   those screens (a route parameter, or a draft read that returns the complaint number), not to the
   rail.
3. **`complainant-history`'s link is half-dead.** It points at `/search?complainantEmail=...`; the
   route exists but `search.component.ts` reads no query parameters at all, so the filter is silently
   dropped. The rail therefore does **not** render it as navigable. Fixing it belongs to the search
   screen.
4. **The `assistance.*` keys are seeded but were not yet present on `GET /api/v1/i18n/translations/en`
   when last probed**, which is why `title` remains the fallback of record rather than a defensive
   nicety — today that English is what actually renders.
5. **V115 / Oracle V113 are written and NOT applied.** Deliberate — the user asked to run them
   themselves. Until then the rollup table does not exist, the hourly job logs one WARN and does
   nothing, and the next-action signal is absent. The feature is built but has never rendered.
6. **§5.3.2, the closure-clause recommendation, has had no ruling.** The build/skip/defer decision
   covered items #1, #4 and #5; #2 was not put to the user because the earlier audit did not separate
   it from the `entity-clause-precedent` prior, which is a different signal. See **§5e**.

---

## 5e. Next-action prediction (§5.3.1), and the three Tier 1 priors that are NOT built

The remaining §5.3 gap was audited and the user ruled: **build #1 (next-action prediction), skip #4
(deadline triage), defer #5 (entity pattern alert).** This section records all outcomes, because most
of them are absences and an absence left unwritten reads as delivered.

**Committed as `3993451`** on `CMS_21092026` (28 files, +3196/−79), not pushed. Stated because §5d of
this same document opens with the lesson that an uncommitted feature is indistinguishable from an
unbuilt one — this one was wrongly concluded unbuilt in three consecutive sessions.

**The §5.3 scorecard, all six items.** The earlier version of this document reported deliverable 4 as
"Built" without this breakdown, which is how three missing items went a session unnoticed:

| §5.3 item | State |
|---|---|
| 1. Next-action prediction | Built this session, migration unapplied — below |
| 2. Closure-clause recommendation | **Not built**, and not covered by the ruling — below |
| 3. Complainant history / repeat-contact | Built earlier (`c81bc5b`) |
| 4. Deadline triage | Not built, skipped by ruling — below |
| 5. Entity pattern alert | Not built, deferred by ruling — below |
| 6. Template wiring | Built earlier (`262d8cd`) |

### What was built — #1, next-action prediction

`P(action | from_status, performed_by_role, category)` as a rollup table refreshed on a schedule, read
by one keyed lookup, rendered as a highlighted-but-inert signal.

| Piece | File |
|---|---|
| Rollup entity + `CATEGORY_AGNOSTIC` sentinel | `entity/AssistanceNextAction.java` |
| Lease-lock row | `entity/AssistanceJobLock.java`, `repository/AssistanceJobLockRepository.java` |
| Keyed read (one `IN` range) + stale sweep | `repository/AssistanceNextActionRepository.java` |
| Refresh job | `service/AssistanceNextActionRefreshService.java` |
| Hourly trigger | `config/AssistanceNextActionRefreshScheduler.java` |
| The signal | `AssistanceRailService#nextAction` / `#toSignal` |
| §6.2's separate rate-limit bucket | `config/RateLimitFilter.java` |
| Migrations, **written and deliberately NOT applied** | `database/V115__…sql`, `database/oracle/V113__…sql` |

**§6.2's "assistance gets its own, smaller bucket" is now real rather than nominal.**
`RateLimitFilter` keys `/api/v1/assistance/**` as `"assist|" + clientIp` into the **same** map as core
traffic — a second map would silently double the `MAX_BUCKETS` memory ceiling — and gives it
`assistance-requests-per-minute` (60 in production, 600 under `dev-local`). One limit, not the core
bucket's per-second/per-minute pair: 60 tokens cannot be a flood at any rate, whereas a per-second
ceiling small enough to matter would reject the three-call burst (`/status`, `/rail`, `/rail/memory`)
of a single legitimate screen load. **Exhausting it degrades to no bulb, never to no screen**, which is
the §5.1 silent-failure contract already in force for a transport error — and is why a bucket this
small is safe to impose on a staff screen. Note for the e2e harness: the `dev-local` value is raised
for the same reason the other two limits are, since a rail rate-limited mid-suite looks exactly like
the feature being broken.

**That bucket invalidated a documented invariant, which was corrected rather than left standing.** Both
rail endpoints were described throughout as "always HTTP 200" — true of the *controllers*, and the
stated reason `assistance-rail.service.ts` carries no `catchError`. A 429 is returned by the **filter**,
before the controller runs, so the component's `error:` branch now has two reachable causes instead of
one. Verified safe rather than assumed: that branch already sets `glow=false`, `retry()` issues a real
request (`shareReplay` defaults to `resetOnError: true`), and a 429 is **not** mapped to an empty list —
that collapse is the precise defect which hid a non-existent similar-cases endpoint for months. The
service javadoc now distinguishes controller from endpoint. Anyone adding a filter-level refusal to an
assistance path must re-read it.

**The bucket's test was mutation-checked, because filter tests pass too easily.** Forcing
`boolean assistance = false` fails **5 of the 8** `RateLimitFilterAssistanceBucketTest` tests. It also
pins that `/api/v1/complaints/assistance-requests` does *not* receive the small bucket, so the prefix
match cannot be "simplified" into a substring match later.

**The brief's prescribed key does not work on this data, and the fix is a sentinel rather than a
compromise.** `(from_status, performed_by_role, category)` yields only **5** cohorts clearing a
5-sample floor, because `category_id` reaches just **722 of 16,989** joinable timeline rows
(re-measured 2026-10-06; this figure has not moved). A `CATEGORY_KEY = 0` sentinel row alongside each
category-specific row covers **34 cohorts / 7,528 events** instead. The read asks for both keys in one
query and `BEST_COHORT` prefers the specific row when it exists, so the signal sharpens on its own as
categories get populated — no schema change, no code change, no migration. `0` rather than `NULL`
because a NULL-bearing composite UNIQUE key prevents no duplicates on MySQL *or* Oracle.

**The lock is a DB-backed lease, not Hazelcast and not a boolean.** Hazelcast clustering is off in this
deployment, so there is no leader election to borrow. The acquire is a conditional
`UPDATE … WHERE LOCKED_UNTIL <= :now` and the job proceeds only on 1 affected row. It is a *lease*
(10 minutes) specifically so a pod killed mid-refresh cannot hold it forever; `releaseLease` is a
courtesy that expires the row early, and its failure is logged and ignored because the lease expiry is
the real guarantee.

**`recompute` carries no `@Transactional`, on purpose.** It is reached through `this` from `refresh()`,
and Spring's proxy does not intercept self-invocation — the annotation would be silently inert, which
is worse than absent because it reads as a boundary that exists. Annotating `refresh()` instead is
worse still: it catches its own exceptions, so a rollback would never be triggered. The transactional
boundary lives on the repository (`deleteStale`), where the proxy *is* the bean.

**Timeouts are on the request-path query and deliberately absent from the job's.** `findRailCandidates`
carries the 5 s `jakarta.persistence.query.timeout` hint the rail's other queries use; `findCohort` does
not, because a background refresh would rather wait than silently skip a cohort and publish a rollup
that is quietly missing rows.

**"Suggest, highlight, do not auto-select"** (§5.3.1) is enforced by the signal carrying **no link** and
the component applying only a CSS class. `workflow-action-bar` commits real workflow transitions, so a
route that arrived with a transition pre-selected is exactly the harm the rule names. The icon is
`pi-chart-bar` and not an arrow for the same reason: an arrow reads as a control, and this signal
reports what historically *followed*, which the workflow may now refuse.

**One contract that will break quietly if touched.** `count` is the **numerator** in both
`Signal.count` and `params`. The seeded string is `"{{action}} followed in {{count}} of {{total}}…"`,
and the component only falls back to `Signal.count` for `params['count']` when params omits it — so
putting the denominator in `count` would render "1200 of 1200" in all 13 locales while English, which
reads `params`, stayed correct. A test pins `PARAM_COUNT = "1062"` against a 1200 cohort.

**Gate.** `mvn test -Dtest='Assistance*Test'` → **101 tests, 0 failures, 0 errors, BUILD SUCCESS** —
`AssistanceRailServiceTest` 48 (incl. `NextActionTests` 10 and `NextActionPreferenceTests` 8),
`AssistanceNextActionRefreshServiceTest` 28, `AssistanceRailKillSwitchTest` 11,
`AssistanceRailTranslationCoverageTest` 10, `AssistanceStatusAuthorizationTest` 4. Main and test
sources compile clean; `npx tsc --noEmit` clean. The WARN lines during that run ("could not take the
lease", "ASSISTANCE_NEXT_ACTION does not exist") are the degradation paths being exercised
deliberately, not failures — V115 is not applied, and the feature is required to stay quiet when its
table is absent.

Run the gate **from `cms-backend/`, not from the repo root.** `cms-backend` is not a module of the root
reactor, so `mvn test -Dtest='Assistance*Test'` at the root builds sixteen other modules, matches
nothing, and fails in `cms-common` with "No tests matching pattern" — a reactor-layout artifact that
looks exactly like a broken gate.

Count the gate from an **isolated** `-Dsurefire.reportsDirectory`. Six other sessions share this
`target/`, so `target/surefire-reports/` is not a trustworthy record of what *this* gate did.

> **Correction to an earlier revision of this section.** A 12-error `AssistanceRollupQueryIT` in those
> reports was recorded here as another session's stale artifact. It was not — it was **this** session's.
> A `@DataJpaTest` slice was written to prove the four new JPQL statements actually parse and execute
> (the one class of defect a fully-mocked suite cannot catch), and it failed at context load because
> **H2 is not a cms-backend dependency at all**: `src/test/resources/application.yml` points the suite
> at `jdbc:h2:mem:testdb`, but no `com.h2database` artifact is on the classpath in any scope, so
> `@AutoConfigureTestDatabase` cannot replace the datasource. The file was deleted rather than left
> failing, and adding a dependency to the shared `pom.xml` was not done unasked. **The JPQL is
> therefore validated by BOOT instead** — see immediately below — and that gap is real: no test in this
> repository executes these four statements against a database.
>
> **And the reports outlived the file.** Deleting the class did not delete
> `target/surefire-reports/AssistanceRollupQueryIT*`, so a later gate that never executed it printed the
> same 12 errors again and cost a full investigation. Nothing cleans this `target/`. Before believing a
> failure here, check the report's mtime against your run and confirm the `.java` still exists — and note
> that `ApplicationContext failure threshold (1) exceeded` is never a root cause, only the second and
> later attempts; the real `Caused by` is further down the trace.

**Boot verification, standing in for that integration test.** A constructor expression over an
`ON`-joined *unrelated* entity (`LEFT JOIN Complaint c ON c.id = t.complaintId`) fails at Hibernate
**bootstrap**, not at call time, so reaching "Started" is itself the assertion: had any of the four
statements been malformed, the context could not have come up. Run it on a spare port with DDL off so
nothing reaches the shared schema:

```bash
cd cms-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev-local \
  -Dspring-boot.run.jvmArguments="-Dserver.port=8096 -Dspring.jpa.hibernate.ddl-auto=none"
```

Observed: `Started CmsApplication in 16.383 seconds`, `Tomcat started on port 8096`, **zero** matches for
`QuerySyntaxException|SemanticException|Could not interpret|validation failed`, and **exactly one**
scheduler registration followed by **exactly one** refresh attempt — which is also the evidence that the
duplicate scheduler is really gone and that two beans are not racing the same lease. Afterwards neither
`ASSISTANCE_NEXT_ACTION` nor `ASSISTANCE_JOB_LOCK` existed in `cms_db`: the run left the schema
untouched, as instructed.

The same boot exercised the degradation path for real rather than through a mock:
`Table 'cms_db.assistance_job_lock' doesn't exist` → `Assistance next-action refresh skipped: could not
take the lease ... If V115 / oracle V113 has not been applied this is expected.` A missing rollup table
makes the feature quiet, not broken.

What boot does **not** prove, and an executed test would: that `findRailCandidates` keyset paging
actually advances past the first page, that `deleteStale` deletes the rows intended and no others, and
that the `0` sentinel on `CATEGORY_KEY` really does let the composite UNIQUE reject duplicates on both
engines. Those remain reasoned, not run.

**Honest limits, measured rather than assumed:**

- **33 of 34 cohorts clear BOTH floors**, so `MIN_CONFIDENCE` (0.5) is effectively untested against a
  realistic distribution. It is a guard that has not yet had to refuse anything.
- `performed_by_role` is populated on **7,531 of 17,433** timeline rows (43%). Events without a role
  cannot enter any cohort.
- Only `assigned` and `in_progress` have cohorts for front-line roles, so **the signal is silent on most
  of the register** — a data gap, not a logic one.
- The timeline's `from_status` vocabulary and `COMPLAINTS.status` overlap but are not identical, so a
  complaint sitting at a status the timeline never records a departure from gets no signal.
- The rollup has **never been populated**, because V115 is unapplied by instruction. Everything above is
  proven by unit test and by SQL measurement of the source data; no officer has seen this signal render
  against a real rollup.

### #2, closure-clause recommendation — NOT built, and NOT covered by the ruling

This one is the honest gap in this session's accounting: the user's ruling named #1, #4 and #5, and
#2 was never put to them because the earlier audit had not separated it out. It is recorded here as
outstanding rather than quietly folded into "built".

What §5.3.2 asks for is a **reordering of the closure-clause `<select>`** by how often each clause
has co-occurred with the complaint's (category, ground, entity type, resolution path), while still
honouring each clause's `restricted_to_roles`.

The shipped `entity-clause-precedent` prior is **not** this, and the resemblance is the trap: it is a
different signal that happens to mention clauses. It reports a *count beside the complaint* — "this
clause has been cited N times for this entity" — and reorders nothing. The officer still scrolls the
same alphabetical list. A reader comparing the two by name would conclude §5.3.2 shipped.

Three things a builder should know before starting it:

- It must reorder, not annotate, so unlike every other prior it **cannot live in the rail**. The rail
  is an adjacent panel; this one has to reach into the closure form's own control.
- The `restricted_to_roles` filter is already enforced when the `<select>` is populated. A reorder that
  sorts a pre-filtered list inherits that; one that sorts a *rollup* and then renders it would have to
  re-apply the filter, or it would advertise clauses the role may not cite.
- §5.1's "suggest, highlight, do not auto-select" applies with more force here than anywhere else,
  because reordering a `<select>` moves what is under the cursor. Putting a suggested clause into the
  first position is a suggestion; putting it where the officer's muscle memory expects a different one
  is closer to auto-selection. Highlight in place is the safer reading.

### #4, deadline triage — NOT built, skipped by ruling

"3 of your 14 cases breach within 48h." Not started, no scaffolding, no table, no key, no i18n. The
brief is right that it is cheap (`re_response_deadline` / `re_response_overdue` are indexed by V86), so
the reason it is absent is scope, not difficulty. Note it differs in kind from every other Tier 1
prior: it is a statement about the officer's **whole queue**, not about the complaint on screen, so it
does not fit the rail's existing per-complaint contract — `rail(complaintId, …)` has no queue-wide
input and the panel has no place to put a signal that belongs to no complaint. Whoever builds it should
expect to extend the contract, not just add a signal.

### #5, entity pattern alert — NOT built, deferred by ruling

"This entity has N open cases on this ground this quarter." Deferred, not skipped — the distinction is
that this one has a known blocker. It is a **cross-complaint disclosure**: it tells an officer about
other complainants' live cases against a named entity, which is the same question as open ask 3
(cross-office precedent visibility) and wants the same owner. Building it before that ruling would
settle a disclosure question by implementation. The scheduled-rollup machinery this session built
(lease lock, keyset paging, stale sweep) is reusable for it; the policy is not.

**None of #2, #4 or #5 has a stub, a flag or a TODO in the code.** That is deliberate per the brief's
"do not leave scaffolding", and it is why they are written down here instead. #2 additionally needs a
ruling it has never had.

---

## 5f. V115 applied, and the rail observed on real data (2026-10-07)

Everything in §5e about §5.3.1 was proven by unit test only, because the migration was deliberately
unapplied. On the user's instruction (*"please run the migration"*) it has now been applied to the
shared `cms_db` and the feature exercised for real. This section is the measurement; it supersedes
every "has never held a row" statement elsewhere in this document.

**The migration applied, and is re-runnable.** `database/V115__assistance_next_action_rollup.sql`
against `cms_db` → both tables created, `UK_ANA_COHORT (FROM_STATUS, PERFORMED_BY_ROLE, CATEGORY_KEY)`
present with `non_unique=0`, and the lease row seeded at the `1970-01-01` sentinel. The whole file was
then **re-run** to prove the hand-application story: exit 0, still exactly one lease row, no duplicate.
That matters because there is no Flyway here — idempotency has to live inside the `.sql`, and MySQL has
no `CREATE INDEX IF NOT EXISTS`. **Oracle `V113` is still unapplied**: no Oracle instance is reachable
from this environment, so the Oracle half of the pair remains unproven.

**The refresh job ran for real, under its lease.** Backend on an isolated port 8096 (`dev-local`,
`ddl-auto=none`, own Hazelcast cluster-name, `initial-delay-ms=5000`). Afterwards:

```
cohorts  last_refresh                   LOCKED_UNTIL                   LOCKED_BY
38       2026-10-07 03:37:51.775482     2026-10-07 03:37:53.263516     unknown-host
```

`LOCKED_BY` moving off `NULL` is the evidence the lease was genuinely taken and the job was not a
no-op. `unknown-host` is the documented `${HOSTNAME:unknown-host}` fallback, not a defect. The whole
recompute over ~17k timeline rows took **~1.5s** (lease acquired 03:37:51.775, released 03:37:53.263).

**38 cohorts, not the 34 predicted.** §5e's figure came from SQL run directly against the timeline;
the job's own grouping finds 38. **33 sentinel rows** (`CATEGORY_KEY = 0`) plus **5 category-specific
rows** (`CATEGORY_KEY = 1`). The 5 specific rows are the proof that the sentinel design works as
intended: the same five cohorts exist at both keys, so as `category_id` populates the prior sharpens by
itself with no schema or code change. Largest cohorts:

| from_status | role | cat | action | n / total | conf |
|---|---|---|---|---|---|
| `assigned` | `CEPC_DO` | 0 | `ACCEPT` | 1748/1748 | 1.00 |
| `in_progress` | `CEPC_DO` | 0 | `SUBMIT_FOR_REVIEW` | 1062/1200 | 0.89 |
| `reviewer_review` | `CEPC_REVIEWER` | 0 | `APPROVE_REVIEW` | 999/999 | 1.00 |
| `incharge_review` | `CEPC_INCHARGE` | 0 | `APPROVE_CLOSURE` | 967/991 | 0.98 |
| `awaiting_closure` | `CEPC_CLOSING_AUTHORITY` | 0 | `CLOSE_COMPLAINT` | 812/826 | 0.98 |

**The signal renders.** `GET /api/v1/assistance/rail?complaintId=…` with staff identity headers:

- `assigned` complaint as `CEPC_DO` → `glow: true`, one `next-action` signal,
  `"ACCEPT followed in 1748 of 1748 comparable cases (100%)"`, `link: null`,
  `params = {count: "1748", action: "ACCEPT", percent: "100", total: "1748"}`.
- `in_progress` complaint as `CEPC_DO` → `"SUBMIT_FOR_REVIEW followed in 1062 of 1200 … (89%)"`.
- `closed` complaint as `CEPC_DO` → `glow: false`, **no signals**. This is the ~86%-silence figure
  observed rather than calculated: there is no `closed`/`CEPC_DO` cohort, and the rail says nothing
  rather than reaching for a weaker one.
- `link: null` is the enforcement of "suggest, highlight, do not auto-select" — there is nothing for
  the officer to click, so the rail cannot commit a workflow transition.
- `params['count']` is the **numerator** (1748, 1062), confirming on live data the trap §5e records:
  a denominator there would render "1200 of 1200" in all 13 locales while English stayed correct.

**§6.3 obligations, now dischargeable for this endpoint.** `EXPLAIN` on both new reads:

| query | type | key | rows | extra |
|---|---|---|---|---|
| single cohort (`=` on all three) | `const` | `UK_ANA_COHORT` | 1 | — |
| rail candidates (`IN` on role + category) | `range` | `UK_ANA_COHORT` | 4 | `Using index condition` |

No full scan on either, and `rows` is 1 and 4 — so the request-path read is a keyed lookup, as §5.3's
"answerable by a single keyed lookup" requires. Latency over HTTP, 20 runs after 3 warmups:
**p50 6ms, p95 7ms, max 12ms**. That is the whole endpoint including identity resolution, not just the
query.

**What this still does not prove.** The rail was exercised with `X-User-*` dev-identity headers under
`dev-local`, so this is not an authorisation measurement — §5b remains the authority there. Keyset
paging past page 1 is still unexercised (the timeline fits in fewer pages than it takes to advance
twice meaningfully), `deleteStale` has not been observed deleting anything (nothing is stale yet), and
no officer has seen the bulb in a browser.

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
