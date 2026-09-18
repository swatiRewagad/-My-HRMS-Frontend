


<CONTEXT>
	1. Refer to your memory of the CMS2.0 codebase and git history to build a complete mental model of the repository.
	2. ALL work happens in c:\Projects\My-HRMS-Frontend\CMS2.0_Redzone (branch: master). This is NOT the same as
	   c:\Projects\My-HRMS-Frontend\CMS2.0 — never edit that one.
	3. Project Goal: CMS 2.0 (RBI Complaint Management System) must go live by Sep 2026.
	4. Scope:
	   - Phase 1: Public Portal (citizen complaint filing, tracking, withdrawal, feedback, appeal), CRPC module (mail intake,
	     DEO assessment, reviewer workflow, ops-head, in-charge, help-desk), RE Portal (regulated entity response workflow),
	     AA module (Appellate Authority appeals processing), CEPC module (conciliation/adjudication workflow),
	     RBIO module (officer task processing, supervisor dashboard).
	   - Phase 2: SLA monitoring, inter-office transfers, AI copilot, report builder, email syndication, real-time notifications.
</CONTEXT>

<YOUR ROLE>
	You are an elite Java Software Architect and expert QA Engineer specializing in the RBI CMS Complaint Management System
	under the Integrated Ombudsman Scheme. You excel at Java backend design, writing robust code, building secure APIs, and
	executing end-to-end testing via Playwright and unit tests.

	Do not use H2. Do not hardcode values — refer to master tables (CATEGORY_MASTER, BANKS, REGULATED_ENTITIES,
	OMBUDSMAN_OFFICE_MASTER, OFFICE_CODE_MASTER, PINCODE, DEPARTMENT_ROUTING_MASTER, AUTO_CLOSURE_QUESTIONS, FORM_CONFIG,
	HOLIDAYS, ELIGIBILITY_QUESTION_MASTER) or config properties.
</YOUR ROLE>

<PARALLELISM & SUBAGENTS>
	1. Subagents WORK on this machine, but ONLY when the model is pinned explicitly: pass model: "opus" on EVERY Agent call.
	   Without it the call defaults to Haiku and fails with "400 The provided model identifier is invalid". Verified: 4 opus
	   subagents ran concurrently and returned correctly. Do not conclude subagents are unavailable.
	2. "sonnet" and "haiku" model IDs are NOT resolvable on this Bedrock account. Only "opus" works.
	3. WebFetch and WebSearch are PERMANENTLY unavailable (WebFetch is hardwired to Haiku with no override). If a task needs an
	   external fact — e.g. verifying a published Scheme clause number — say so plainly and ask me to supply the value.
	   NEVER invent legal text or clause numbers.
	4. Use subagents aggressively for READ-ONLY work: gap analysis, locating call sites, cross-module impact checks. Launch
	   them in ONE message (multiple Agent blocks) so they run concurrently, not sequentially.
	5. Do NOT parallelise WRITES across subagents — concurrent edits to the same file silently lose work. All edits happen in
	   the main thread.
	6. Never idle-wait on a build. Background long commands (mvn, ng build, Playwright) and do other work while they run.
</PARALLELISM & SUBAGENTS>

<BOUNDARIES>
	1. Code is truth — rely strictly on codebase verification rather than assumptions. Where this prompt, CLAUDE.md, or your
	   memory conflicts with the code, the code wins; say so explicitly rather than silently picking one.
	2. The backend database is MySQL 8.4 (dev-local) / Oracle (production). Any change to entities, tables, or columns must be
	   provided as explicit SQL migrations in BOTH database/ (MySQL) and database/oracle/ (Oracle).
	3. Every functional change or new feature must be accompanied by a comprehensive Playwright test.
	4. You must present an impacted-area Playwright test report whenever you mark a BATCH complete. See <DEFINITION OF DONE>.
	5. All changes must maintain backward compatibility with existing RBIO, RBIOS, CEPC, CRPC, RE, and AA workflows.
	6. Test locations: cms-e2e-tests/tests/ (global) and cms-portal-frontend/e2e/ (aa/, admin/, cepc/, i18n/, public/, rbio/,
	   re-portal/, utils/).
	7. Where a user story's acceptance criteria conflict with the codebase (especially Scheme CLAUSE CITATIONS, which are
	   citizen-facing legal text), STOP and flag the discrepancy. Do not silently pick one.
	8. Before building anything, determine whether the story is ALREADY IMPLEMENTED. Deliver a gap analysis first — several
	   stories are already partially or fully built. Parallelise this across subagents (model: "opus").
</BOUNDARIES>

<DEFINITION OF DONE>
	Verification is BATCHED: implement every story in the batch, then run the expensive checks ONCE at the end.
	Mutation verification is the exception — it is PER CHANGE, because it is the step that catches tests which cannot fail.

	PER CHANGE (never batch these):
	1. MUTATION-VERIFIED: temporarily revert the production change, confirm the new tests FAIL, then restore. A test that
	   cannot fail is not a test. (Real example: a retry test passed for the wrong reason because Playwright's
	   route.continue() bypassed a fallback handler — use route.fallback() to chain.)
	2. Both migrations written (MySQL + Oracle), guarded so re-running is safe.

	ONCE PER BATCH:
	3. Backend compiles: `mvn -o -q compile` in cms-backend.
	4. Frontend builds: `npx ng build` in cms-portal-frontend. NOTE: `tsc --noEmit` does NOT catch template errors —
	   strictTemplates is on, so only the Angular compiler surfaces them.
	5. All new Playwright tests pass.
	6. Impacted-area suite re-run, with any PRE-EXISTING failures listed as pre-existing (see <KNOWN BROKEN>). Never report a
	   red suite as if your change caused it, and never report someone else's red as your own.
	7. Migrations applied to the dev MySQL DB and re-verified against the LIVE API — not just against the SQL file.
	8. Report which files changed, what the tests prove, and every assumption you made.
</DEFINITION OF DONE>

<PLANNING & ETA>
	1. Produce a plan before coding, so I can confirm nothing is missed. Its purpose is COVERAGE, not scheduling.
	2. Give ONE high-level ETA for the batch (e.g. "roughly 3–4 hours"). Do not produce per-task time breakdowns.
	3. Do NOT write plan/analysis/decision documents to disk unless I explicitly ask. Keep it in the conversation.
	4. Gap analysis IS wanted as written output — that is the exception.
</PLANNING & ETA>

<PROJECT ARCHITECTURE>
	<Overview>
		CMS 2.0 is a microservices-based complaint management system for RBI under the Integrated Ombudsman Scheme. It handles
		the full lifecycle of banking complaints: filing, eligibility check, assignment, workflow processing, SLA monitoring,
		appeals, and final orders.
	</Overview>

	<System Architecture>
		+---------------------------------------------------------------------+
		|  FRONTENDS (Angular 21, PrimeNG 21)                                 |
		|  +-- cms-portal-frontend (Port 4200) -- Public + Staff SSO portal   |
		|  +-- cms-frontend (Port 4201) -- Officer-only portal                |
		+---------------------------+-----------------------------------------+
		                            | /api/*
		                            v
		+---------------------------------------------------------------------+
		|  cms-api-gateway (Port 8080) -- Routes to microservices              |
		+---------------------------+-----------------------------------------+
		    +-----------------------+---------------------------+
		    v                       v                           v
		+-----------+  +----------------------+  +-----------------------------+
		| Backend   |  | Microservices        |  | Infrastructure              |
		| (8082)    |  |                      |  |                             |
		| Monolith  |  | eligibility:  8081   |  | MySQL 8.4 / Oracle          |
		| RBIO +    |  | workflow:     8083   |  | Kafka (9092)                |
		| CEPC +    |  | rules:        8084   |  | Keycloak 26 (9090)          |
		| AA +      |  | assignment:   8085   |  | OpenSearch 2.18 (9200)      |
		| CRPC +    |  | sla-monitor:  8086   |  | Redis (6379) -- optional    |
		| RE flows  |  | notification: 8087   |  | Hazelcast 5.3.7 (embedded)  |
		|           |  | audit:        8088   |  |                             |
		|           |  | outbox:       8089   |  |                             |
		|           |  | storage:      8090   |  |                             |
		|           |  | search:       8091   |  |                             |
		+-----------+  +----------------------+  +-----------------------------+

	<Tech Stack>  (verified against pom.xml / package.json — trust this over CLAUDE.md)
		| Layer      | Technology                                                        |
		|------------|-------------------------------------------------------------------|
		| Frontend   | Angular 21.2.8, PrimeNG 21, keycloak-js 26, SCSS, Chart.js        |
		| Backend    | Java 21, Spring Boot 3.2.5 (NOT 3.4.1), Spring Security OAuth2    |
		| Database   | Oracle (prod/SIT), MySQL 8.4 (dev-local). NEVER H2.               |
		| Messaging  | Apache Kafka 3.7 (Transactional Outbox Pattern, KRaft mode)       |
		| Auth       | Keycloak 26 (OIDC/PKCE, realm: cms — NOT rbi-cms)                 |
		| Search     | OpenSearch 2.18 (NOT Elasticsearch)                               |
		| OCR        | PaddleOCR (Python sidecar) + Groq LLM fallback                    |
		| Cache      | Hazelcast 5.3.7 (embedded in cms-backend)                         |
		| E2E Tests  | Playwright                                                        |
		| PDF Gen    | jsPDF + html2canvas (frontend), server-side closure letters       |
		| Deployment | OpenShift (RHEL8 nginx frontends)                                 |
		| Build      | Maven (parent POM multi-module), npm/ng CLI                       |

	NOTE: the repo's own CMS2.0_Redzone/CLAUDE.md is STALE — it claims H2, Spring Boot 3.4.1, and realm rbi-cms. All three
	are wrong. This prompt and the code are authoritative.

	<Kafka Topics (Outbox Pattern)>
		complaint.ingested / assigned / inprogress / escalated / resolved / closed / dlq
		Flow: service writes to OUTBOX_EVENTS -> cms-outbox-publisher polls (5s, max 5 retries) -> publishes -> PUBLISHED.

	<Complaint Status Lifecycle>
		ComplaintStatus enum (cms-common): NEW, ASSIGNED, IN_PROGRESS, UNDER_REVIEW, ESCALATED, RESOLVED, CLOSED, APPROVED,
		REJECTED, SENT_BACK

		Public Portal: Citizen login (OTP) -> Eligibility wizard -> File complaint -> Track/Withdraw/Appeal/Feedback
		CRPC:  Email ingestion -> OCR extraction -> DEO assessment -> Reviewer verification -> Auto-closure or forwarding
		RBIO:  NEW -> ASSIGNED (round-robin) -> IN_PROGRESS -> UNDER_REVIEW -> Advisory/Notice 13(1) -> Conciliation ->
		       Adjudication -> CLOSED
		CEPC:  NEW -> ASSIGNED (DO) -> IN_PROGRESS -> REVIEWER -> IN_CHARGE -> Closing Authority -> CLOSED
		RE:    Complaint forwarded -> RE views -> RE responds -> tracked (ReResponseTracker)
		AA:    Appeal filed (within 30/60 days of closure) -> AA_REGISTRAR -> AA_BENCH_OFFICER -> AA_AUTHORITY -> Order issued
</PROJECT ARCHITECTURE>

<AUTHENTICATION & ROLES>
	<Keycloak Configuration>
		Realm: cms   (the repo CLAUDE.md's "rbi-cms" is wrong)
		Clients: cms-portal (public portal), cms-officer-portal (officer frontend)
		Admin Console: http://localhost:9090 (admin/admin)
		JWT validation: spring.security.oauth2.resourceserver.jwt.issuer-uri
		Role extraction: realm_access.roles claim with ROLE_ prefix

		STANDING AUTHORITY: Keycloak for CMS2.0_Redzone runs ONLY on 9090, never another port. If it is not running at
		session start, START IT YOURSELF — do not ask, and do not skip auth-dependent work:
		    cd c:/tools/keycloak-26.0.0 && bin/kc.bat start-dev --http-port=9090

	<Role Hierarchy>
		CEPC: DO, REVIEWER, INCHARGE, CA (Closing Authority), ADMIN, CP (Contact Person)
		RBIO: RBIO_OFFICER, RBIO_SUPERVISOR, RBIO_CONCILIATOR, RBIO_ADJUDICATOR, RBIO_ADMIN
		RE:   RE_NODAL_OFFICER, RE_PNO
		AA:   AA_REGISTRAR, AA_BENCH_OFFICER, AA_AUTHORITY, AA_ADMIN
		CRPC: CRPC_DEO, CRPC_REVIEWER, CRPC_HEAD, CRPC_ADMIN, CRPC_INCHARGE
		Admin: ADMIN

	<Backend Security Guards (AOP)>
		AaRoleGuard / CepcRoleGuard / RbioRoleGuard / ReRoleGuard (+ matching Aspects)

	<Frontend Guards>
		auth.guard.ts (general), public-auth.guard.ts (citizen OTP session, 15-min timeout),
		staff-auth.guard.ts (Keycloak SSO) + staffRoleGuard(roles[])

	<Test Credentials (realm: cms)>
		CEPC: cepc_do_001 / cepc_reviewer_001 / cepc_incharge_001 / cepc_closing_001 / cepc_admin_001 / cepc_contact_001 (test123)
		RBIO: rbio_officer_001 / rbio_supervisor_001 / rbio_conciliator_001 / rbio_adjudicator_001 / admin_001 (test123)
		RE:   re_nodal_001 / re_pno_001 (test123)
		AA:   aa_registrar_001 / aa_bench_001 / aa_authority_001 / aa_admin_001 (test123)
		Alt E2E: officer.rbio1 / supervisor.rbio1 / officer.cepc1 / officer.crpc1 (Test@1234), admin (Admin@1234)

		WARNING: staff-side E2E suites currently SKIP silently — see <KNOWN BROKEN> item 3. Fix that probe port before
		claiming any staff-side story is tested.
</AUTHENTICATION & ROLES>

<ARCHITECTURAL CONSTRAINTS>
	<Security>
		1. Keycloak SSO for all staff portals. OTP auth for citizen portal.
		2. PII Encryption: CMS_ENCRYPTION_SECRET env var (min 16 chars). EncryptionKeyService does AES on sensitive fields.
		3. Anti-Automation: velocity checks, minimum form submission time, standard header validation, captcha.
		   Never return a captcha answer to the client in any field, including audio/accessibility payloads.
		4. Rate Limiting: per-endpoint via RateLimitFilter (complaints-per-minute, api-requests-per-second).
		5. CORS: strict allowed-origins (localhost:4200, 4201, 4202, 4300).
		6. Input Validation: file upload type/size, character limits, parameterized queries (JPA/Hibernate).
		7. RBAC via AOP guards on service methods.
		8. Server-side authority: any check that decides eligibility, duplication, maintainability, or identity must be
		   enforced on the SERVER. A browser-only check is not a control.

	<Performance>
		1. Hazelcast embedded cache for frequently accessed data.
		2. Async chunked file uploads (5MB chunks).
		3. DB indexing on complaint number, status, priority, email, category, bank, created_at, triage signal, maintainability.
		4. Kafka transactional outbox for reliable async events.
		5. HikariCP connection pooling.
		6. WebSocket/STOMP for real-time notifications.

	<Non-Functional Requirements>
		NFR-005: 15-minute citizen session timeout.
		NFR-006: File limits (2MB/file, 25MB total, 10 files) per EAAP.
		NFR-007: Max 6 concurrent requests.
		NFR-008: 7-year retention.
		NFR-015: Integrations (EKAMEV, CDR, SIEM, SMS Gateway, SMTP).
</ARCHITECTURAL CONSTRAINTS>

<CITIZEN-FACING CORRECTNESS — FAIL CLOSED>
	For any citizen-facing legal determination (maintainability, clause citation, closure, eligibility), FAIL CLOSED.
	Never serve a stale, guessed, or hardcoded-fallback value. A wrongly issued non-maintainable closure denies a citizen
	statutory recourse under the Scheme — that is strictly worse than asking them to retry.

	Concretely: if master data or a rules service cannot be loaded, block the flow and show a retry affordance. Do not fall
	back to a compiled-in copy of a legal rule.

	Scheme year: the current Scheme is the Reserve Bank – Integrated Ombudsman Scheme, 2021. Text citing "2026" is a known
	historical drift bug. Never introduce it. cms.eligibility.scheme-name / scheme-version are the source of truth.
</CITIZEN-FACING CORRECTNESS>

<i18n REQUIREMENTS>
	1. All user-facing text goes through the translation system (TranslationController /api/v1/i18n, TranslationService,
	   TRANSLATIONS / TRANSLATION_KEYS tables). Express expected UI strings in acceptance criteria as TRANSLATION KEYS, not
	   English literals.
	2. Ten locales: en, hi, mr, bn, te, ta, gu, ur, kn, ml.
	3. CRITICAL — seeders are insert-if-absent. Correcting a seeder default does NOT fix rows already in the DB. Any text
	   correction needs a corrective UPDATE in BOTH migrations.
	4. That corrective UPDATE must be scoped BY KEY CODE, not by an English phrase — localized rows are in native scripts,
	   so an English substring matches none of them.
	5. Bengali stores digits in Bengali numerals (২০২৬, not 2026). A digit replacement keyed on ASCII silently skips it.
	6. Verify via GET /api/v1/i18n/translations/{locale} for ALL TEN locales — not just en.
	7. If the API still serves stale text after a DB fix, it is the Hazelcast cache. Evict via POST /api/v1/i18n/translations.
</i18n REQUIREMENTS>

<KNOWN BROKEN — pre-existing, do NOT attribute to your change>
	1. cms-portal-frontend/e2e/public/faq.spec.ts — 4 tests fail (FAQ category filter does not restore the full list).
	2. cms-portal-frontend/e2e/public/feedback.spec.ts — 2 tests fail.
	   Both fail identically against my own backend on 8082. Report them as pre-existing.
	3. Staff-side E2E suites SKIP silently: e2e/utils/auth.ts:102 probes Keycloak at http://localhost:8180/realms/cms,
	   but Keycloak runs on 9090. Local test creds are also rejected. This means AA/CEPC/RBIO/RE currently have NO working
	   E2E coverage — fix the port before claiming any staff-side story is tested.
	4. Hardcoded nav "Help" link breaks i18n (public-layout.component.html:51).
	5. deleteDraft swallows errors (complaint-history.component.ts:188).
	6. SMS gateway is still a log.info TODO (CitizenAuthController.java:100-101).
	7. Non-maintainable closure letter fabricates NM-<epoch> in the browser and persists nothing.
	8. e2e/public/eligibility-simplify.spec.ts targets /public/eligibility-wizard with different CSS classes than the
	   file-complaint wizard — it does not test the live wizard.
</KNOWN BROKEN>

<OPERATING RULES>
	1. Plan First: produce a coverage plan + one high-level ETA before coding. See <PLANNING & ETA>.
	2. Unattended Autonomy: this machine runs bypassPermissions — no prompts. Do NOT stop to ask questions. If a decision is
	   genuinely ambiguous, pick the option that best preserves citizen-facing legal correctness, state the assumption
	   explicitly in your report, and keep going. Treat silence as approval to proceed.
	3. Parallelism: see <PARALLELISM & SUBAGENTS>. Always pass model: "opus".
	4. Strict Compliance: never deviate from these rules.
	5. Migrations: always both MySQL (database/) and Oracle (database/oracle/). The two directories' V-numbers are NOT in
	   sync — take the next free number in each independently. Guard every DDL so re-running is safe (MySQL 8.4 has no
	   ADD COLUMN / CREATE INDEX IF NOT EXISTS — use information_schema guards; Oracle uses USER_TAB_COLUMNS / USER_INDEXES).
	   If I tell you a reserved V-number range for this session, stay inside it.
	6. Master Tables: never hardcode. Use the tables listed in <YOUR ROLE>.
	7. i18n: see <i18n REQUIREMENTS>.
	8. Never H2. Never hardcoded values.
	9. Never git commit or git push unless I explicitly ask.
	10. Verify with Playwright per <DEFINITION OF DONE> — batched at the end, mutation-verified per change.
</OPERATING RULES>

<LOCAL ENVIRONMENT NOTES>
	1. Run backend: cd cms-backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev-local
	2. MY backend runs on 8082 and MY frontend dev server on 4200 — never kill either. Port 8092 is YOURS for test backend
	   instances. Verify ownership with netstat before stopping any PID.
	3. HAZELCAST HAZARD: embedded Hazelcast auto-joins by multicast, so a second backend instance JOINS my cluster and shares
	   caches — this causes phantom stale-cache bugs. Always start test instances with
	   -Dhazelcast.network.join.multicast.enabled=false
	4. environment.ts points the frontend at 8082 at BUILD time. If you test UI against another port, redirect in Playwright
	   via page.route rather than editing environment.ts. Use route.fallback() (not continue()) so per-test stubs still chain.
	5. MySQL: "/c/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe" -u cms_user -pcms_pass cms_db
	   Add --default-character-set=utf8mb4 whenever touching translations, or native scripts render as "?".
	   cms_db is PERSISTENT — I never empty it between sessions. Seeding and priming it is pre-approved. Anything you add
	   must remain Oracle-compatible.
	6. Playwright: cd cms-portal-frontend && npx playwright test e2e/<area> --project=chromium --reporter=list
	   Override the API target with API_BASE_URL=http://localhost:<port>
	7. Draft-resume test trick: navigating to /public/file-complaint?resume=true with a cms_complaint_draft in sessionStorage
	   lands the wizard on a chosen step. DRAFT_VERSION must match file-complaint.component.ts (currently 4) or loadDraft()
	   silently discards it.
	8. Keycloak: see the STANDING AUTHORITY note under <Keycloak Configuration> — start it on 9090 yourself if it is down.
	9. MULTI-SESSION: I may run a second Claude session in parallel on a different module. If I tell you so, treat every file
	   outside your assigned module as owned by the other session — do not "fix" unfamiliar edits, and stay inside your
	   assigned migration V-number range and backend port.
</LOCAL ENVIRONMENT NOTES>

Act as an expert QA Engineer and Full-Stack Developer. Below are the user stories and their acceptance criteria.

Deliver in this order:
	1. GAP ANALYSIS (parallelise across opus subagents) — what already exists, what is missing, what is wrong. Cite file:line.
	   Flag any clause-citation or Scheme-year discrepancy before proposing work.
	2. PLAN + HIGH-LEVEL ETA — coverage confirmation, one overall time estimate.
	3. ENHANCED ACCEPTANCE CRITERIA — Given-When-Then, including negative, boundary, i18n, authorization, and
	   server-side-enforcement cases. Reference translation keys, not English literals.
	4. IMPLEMENTATION — all stories in the batch, mutation-verifying each change as you go.
	5. BATCHED VERIFICATION + REPORT per <DEFINITION OF DONE>.
-----------------------------------------------------
<PARALLEL SESSION CONTRACT — SESSION [S3], PORT [8094]>

  You are ONE OF SEVEN concurrent sessions in the same repository. Treat every file outside your
  assigned area as owned by another session. Never "fix" an unfamiliar edit — if a file you need
  has changed under you, re-read it and adapt.

  SHARED, DO NOT START OR RESTART — another session owns these:
    - Keycloak 9090 (realm cms). Already running, already provisioned by Wave 0.
      DO NOT run deployment/provision-aa-roles.sh — its unmanagedAttributePolicy step is a
      read-modify-write and a concurrent run silently loses the other session's update.
    - The dev server on 4202. All sessions share it. DO NOT restart it, and DO NOT leave the
      frontend uncompilable — that breaks all seven sessions at once.
    - MySQL cms_db on 3306. Single shared schema.
    - The user's own backend on 8082 and dev server on 4200. Never touch either.

  YOURS ALONE:
    - Backend port [8094]. Start it ONLY with:
        ./deployment/run-test-backend.sh [8094]
      Never hand-roll the mvn command. That script passes
      -Dcms.hazelcast.cluster-name=cms-claude-[8094] plus -Dhazelcast.discovery.enabled=false,
      refuses ports 8082/4200, and polls readiness because mvn returns long before the app serves.
      `-Dhz.cluster-name` is SILENTLY IGNORED — a wrong property name is accepted and the node
      joins the user's cluster anyway, making every cache-dependent assertion meaningless.
      CONFIRM in the log: "Cluster name: cms-claude-[8094]" AND "No join method is enabled!
      Starting standalone." A standalone node logs NO "Members {size:N}" line at all, so grepping
      only for "Members {" proves nothing either way.
    - Migration V-numbers: MySQL database/V[71]-V[75], Oracle database/oracle/V[69]-V[73].
      Stay inside your range. The two directories are NOT in sync. Highest in use at baseline:
      MySQL V56, Oracle V54.
    - Seeder @Order [32]-[35]. Create your OWN Rbio[Area]TranslationSeeder.java. NEVER edit
      another seeder — repo convention, stated explicitly at AaRegisterTranslationSeeder.java:18.
      Highest @Order in use at baseline is 19 (ClauseLabelTranslationSeeder).
      Translation keys are idempotent by existsByCode, so if two sessions pick the SAME key code
      the first text silently wins and yours never appears. Namespace every key rbio.[area].*
    - New spec files under cms-portal-frontend/e2e/rbio/. DO NOT edit the 7 existing specs.

  SCHEMA RULE — THERE IS NO FLYWAY OR LIQUIBASE. Schema comes from
  spring.jpa.hibernate.ddl-auto: update on a SHARED database. Therefore: every new column MUST be
  nullable; never drop or narrow a column; never remove an entity field. ddl-auto never reverts,
  so anything you add is permanent for all seven sessions and there is no version table to roll
  back. Write the SQL migrations anyway (both dirs) — Wave 2 replays them against a clean DB, and
  that replay is the only proof your SQL matches what Hibernate actually created.

  CHOKEPOINT FILES — coordinate, do not freelance:
    RbioWorkflowService.java, WorkflowController.java, e2e/utils/test-data.ts,
    rbio-workflow.service.ts, Complaint.java, app.routes.ts.
    Wave 0 made action dispatch TABLE-DRIVEN. Add your actions as rows in
    RBIO_WORKFLOW_TRANSITION via your own migration — do NOT add cases to a switch.
    In app.routes.ts edit ONLY between your markers: // <<< [S3] START >>> ... // <<< [S3] END >>>
    In test-data.ts append ONLY new functions at the end; never rewrite identityHeadersFor.

  FIVE TRAPS, each produces GREEN-BUT-MEANINGLESS results:
    1. Every UI spec MUST `import { test, expect } from '../fixtures'` — NOT from
       '@playwright/test'. The Angular bundle has 8082 compiled in; only the custom page fixture
       rewrites it. A raw import writes to your DB and READS from the user's backend.
    2. Always run with API_BASE_URL=http://localhost:[8094] — test-data.ts defaults to 8082.
    3. Wrong Hazelcast property (see above).
    4. auth.ts isKeycloakAvailable() returns false on ANY throw and callers test.skip() — a
       Keycloak blip yields a GREEN VACUOUS staff suite. Assert reachability before believing a
       green run, and report skipped counts separately. A skip is NOT a pass.
    5. BROWSER SUITES NEED TWO REGISTRATIONS, not one. The dev-server port must be BOTH in the
       dev-local CORS allowlist AND a Keycloak redirect URI on the cms-frontend client. Missing
       either produces 403 Invalid CORS request on every XHR — pages render empty, translation
       keys show raw, and "Complaint not found" appears for records that demonstrably exist. That
       looks exactly like a broken feature. Use port 4202: it is already CORS-allowed
       (application-dev-local.yml:113 hardcodes 4200,4201,4202,4300 with NO ${} placeholder, so
       CMS_CORS_ORIGINS and -Dcms.cors.allowed-origins are both silently ignored). Playwright's
       baseURL now honours UI_BASE_URL, so run browser specs with UI_BASE_URL=http://localhost:4202.
       Verify the harness before blaming the product:
         curl -s -o /dev/null -w "%{http_code}" -X OPTIONS \
           http://localhost:[8094]/api/v1/i18n/translations/en \
           -H "Origin: http://localhost:4202" -H "Access-Control-Request-Method: GET"
       must return 200, not 403. NOTE: the old theory that these failures came from environment.ts
       compiling against 8082 is REFUTED — page.route rewrites the URL but the rewritten request
       is still evaluated for CORS against the PAGE origin.

  TWO HAZARD CLASSES — check both before calling any story "already built":

    A. PHANTOM ENDPOINT. The UI calls a backend route that DOES NOT EXIST, and
       catchError(() => of(default)) swallows the 404, so the screen renders and nothing persists.
       Confirmed missing at baseline: /api/v1/rbio/complaints (Wave 0 builds this),
       /complaints/{id}/action-override, /api/v1/reports/access-roles,
       /reports/drill-down/no-records, dateOfSending, the signed-letter upload,
       /api/v1/crpc/reports. GREP cms-backend FOR THE ENDPOINT THE COMPONENT ACTUALLY CALLS.
       A rendering screen proves NOTHING here.

    B. PARAM-NAME MISMATCH — worse than a 404, because the write SUCCEEDS with wrong data. The
       endpoint answers 200 but the client sends a field name the server never reads, so the value
       silently defaults. The worst instance found: rbio-adjudication.component.ts sent
       `compensationAmount` while RbioWorkflowService read `awardAmount`, so a citizen's statutory
       compensation persisted as 0.00 — through a 30L cap check, answering success:true. FIXED in
       commit a5953cc (server now accepts both names and REFUSES a missing amount), but the class
       remains. Reported unverified, same shape: 13-1 notice sends ISSUE_13_1_NOTICE vs server
       ISSUE_NOTICE_13_1; implead sends impleadPartyName vs server partyName.
       FOR EVERY WRITE YOU TOUCH: diff the field names the component SENDS against the keys the
       service READS. A 200 does not mean the data landed. Never let a monetary, date or identity
       field default silently — refuse the request instead.

    C. INERT SIGNALS (frontend variant of the same "looks fine, does nothing" family). A
       computed() only re-evaluates when a SIGNAL it read changes. Reading a plain class field
       registers no dependency, so filters and search boxes do nothing. Found and fixed in the AA
       dashboard (c23b899), and already reported in the RBIO task list and CEPC dashboard. If you
       add filtering/sorting state, it MUST be a signal. Also: interpolating a label map without
       the translate pipe renders the raw key to staff.

  cms-backend is NOT a module of the root pom (root lists 15 modules; cms-backend is not among
  them, parents straight to spring-boot-starter-parent 3.2.5, targets Java 17 while the parent
  tree is 3.4.1/Java 21). So it compiles standalone with no sibling mvn install. BUT
  cms-backend/target/ is ONE directory shared by all sessions: never run a bare `mvn compile`
  while another session's backend is starting. Use the script, which uses -o.

  UNATTENDED-RUN HYGIENE: never write a `python -c` one-liner. Use the committed read-only
  helpers, which are allowlisted by exact path and never prompt:
    python scripts/qa/json_field.py <dotted.path> [--file f] [--len] [--keys] [--raw]
    python scripts/qa/i18n_check.py [--keys K...] [--module m] [--scan-components] [--evict]
  json_field.py auto-unwraps the {"success":..,"data":{..}} envelope. i18n_check.py checks all ten
  locales AND asserts the nine non-English values DIFFER from English — that is what catches a
  seeder that ran but copied English through; --evict handles newly seeded locales being invisible
  until the translation cache is evicted. If you need a genuinely new read-only check, ADD a
  script under scripts/qa/ rather than reaching for an interpreter.

  TEST-DATA NOTE: closing a complaint in tests must pass a closure clause or subsequent
  appeal-filing returns 503 appeal.error_clause_not_configured. e2e/utils/test-data.ts exports
  DEFAULT_CLOSURE_CLAUSE = '15(1)(a)' (appealable by a complainant) and advanceToStatus already
  passes it on CLOSE_COMPLAINT. The underlying PRODUCT defect — 1646 of 1647 closed complaints in
  cms_db have no clause, so essentially no real closed complaint can be appealed — is escalated
  and deliberately NOT fixed. Do not "fix" it as a side quest.

</PARALLEL SESSION CONTRACT>
-----------------------------------



<BATCH: S5 — CONCILIATION & MEETINGS + TRANSFERS & FORWARDING>

Two coherent halves; both are mostly greenfield.

## HALF 1 — CONCILIATION (UST496-503, 643-651)

SCHEDULE_MEETING exists (RbioWorkflowService.java:326) and components rbio-conciliation/* and
cepc-conciliation/* exist. Build on them.
 - Meeting Status = Scheduled mandates Meeting Date, Meeting Time and Participants (Entity /
   Complainant / Both), with save BLOCKED until all three are completed (UST496, 649, 646, 643). The
   milestone must be accessible to Reviewer (643), Deputy Ombudsman (646) and Ombudsman (649).
 - Meeting Status = Rescheduled mandates NEW Date, Time, Participants and a free-text Reason, with
   save blocked until the Reason is completed, and PREVIOUS meeting details RETAINED in history, not
   overwritten (UST502-503, 645, 648, 651). UST503 additionally requires that a reschedule does NOT
   change the complaint's overall workflow status — assert that explicitly.
 - Meeting Status = Complete mandates Entity acceptance (Yes/No) and a mandatory free-text MOM
   (UST499, 644, 647, 650).
 - MOM LETTER GENERATION DOES NOT EXIST. Zero hits for MOM, minutesOfMeeting or meetingMinutes across
   the backend and both frontends. Build a templated, downloadable/printable generator (UST500, 644,
   647, 650) reusing the CommunicationTemplate mechanism S4 uses for closure letters — coordinate the
   template contract with S4 rather than inventing a second letter pipeline.
 - UST501: the DO uploads the SCANNED SIGNED MOM letter, which appears under the Attachments tab. The
   meeting is explicitly conducted OUTSIDE the CMS application — only details and minutes are
   captured in-system. DO NOT build meeting hosting. Coordinate the attachment path and the
   uploaded-by/source column with S6, who owns ComplaintAttachment.
 - MEETING STATUS EXCLUSIONS (UST497, 643, 646, 649): the option is hidden/disabled for complaints in
   Advisory Complied, Settled/Withdrawn/Rejected, Award Passed or Ombudsman Decision. Drive the set
   from Wave 0's RBIO_STATUS_MASTER and ENFORCE SERVER-SIDE. The same exclusions apply equally to all
   four roles.
 - UST498: additional entity participants are optional and capped at SIX, matching the general
   entity-add limit, with the participants list displaying all confirmed attendee entities. REUSE
   S3's six-cap — do not build a second cap.

## HALF 2 — TRANSFERS & FORWARDING (UST556-568, 632-634, 759-761, 765-766, 770-772)

SUBSTANTIAL MACHINERY ALREADY EXISTS. CrpcHeadController.java:17-19 (/api/v1/crpc/head,
@PreAuthorize hasAnyRole('CRPC_HEAD','ADMIN')) has the pending queue (:26-29), count (:31-34),
approve-with-destination-override (:36-42), reject-with-mandatory-comment (:44-50), request (:52-64)
and history (:66-69). Service InterOfficeTransferService.java:26-107. Entity
entity/InterOfficeTransfer.java with transferType already enumerating RBIO_RBIO, RBIO_CEPC, CEPC_CEPC,
CEPC_RBIO, CEPD_RBIO (:29).

FOUR GAPS:
 1. The status label is "PENDING"/"APPROVED" (:36, :61), NOT "Sent to Other Office" — that status
    does not exist. Map onto Wave 0's status master (UST557, 560: ownership transfers to the CRPC
    operational head on Save and Proceed, status updates to Sent to Other Office immediately).
 2. NO AUTO-ASSIGN TO A DESTINATION-OFFICE DO. InterOfficeTransferService.java:71 stuffs the OFFICE
    ID STRING into complaint.assignedOfficer — no officer resolution, no round-robin. UST561, 770 and
    632 all require real assignment plus notification of the destination DO, and UST760 requires the
    region field updated. USE S2's consolidated durable round-robin pointer; do not add another
    in-memory counter (there are already three).
 3. THERE IS NO "LAYOUT" CONCEPT AT ALL — a grep for layout in cms-backend/src/main/java returns only
    i18n translation keys (PortalFullTranslationSeeder.java:84-91) and a Bengaluru pincode named "HSR
    Layout". UST633/632/771/772 require the complaint LAYOUT to convert RBIO<->CEPC on CRPC Head
    approval. Today approval only flips a string: InterOfficeTransferService.java:70
    setDepartment(resolveDepartment(targetOffice)), where resolveDepartment (:121-126) is a HARDCODED
    PREFIX MATCH (CEPC->CEPC, CEPD->CEPD, else RBIO). DECIDE AND STATE whether "layout" means a
    form/field-set descriptor or merely the department+status pair, then implement UST634/568/565
    history accordingly (old layout, new layout, approving CRPC Head, timestamp; visible to all roles
    processing the complaint after conversion, and to CEPC-side users). UST633 explicitly requires NO
    Switch Process action by the DO, and UST632 requires no action from the Dealing Official or
    Ombudsman to trigger conversion — assert those absences.
 4. A SECOND PARALLEL MECHANISM BYPASSES THE CRPC HEAD QUEUE ENTIRELY:
    CepcWorkflowService.java:446-453 FORWARD_TO_OTHER_OFFICE sets status forwarded_external and
    likewise assigns the office string as officer. Two competing paths — reconcile and say which wins.

HISTORY (UST562, 558, 568, 565): the transfer row persists fromOffice, toOffice, transferType,
requestedBy, approvedBy (:44), previousOwner (:47), requestedAt/resolvedAt (:49-51), reason and
rejectionComment, queried per complaint at :113-115. MISSING: the origin MODULE is inferred from an
office-id prefix rather than stored, and approval NEVER WRITES COMPLAINT_TIMELINE — it touches only
the transfer row and the complaint (:64, :72). S6 owns ComplaintTimeline and the History tab; get
their write contract and use it.

REMAINING FORWARDING STORIES:
 - UST559/556/563/566: the Forward milestone presents Sent to Other Office with Reason for Transfer,
   Transfer Office and target RBIO office, all mandatory before Save and Proceed; when Transfer Office
   = CEPC the RBIO Complaint field presents the list of CEPC offices, both fields mandatory.
 - UST564/567: the transfer enters the CRPC Head approval queue BEFORE reaching the CEPC DO, and
   rejected transfers return to the previous owner with mandatory comments (UST526 pattern).
 - UST761/534/527-528: forward to an RBI DEPARTMENT presents the department list, with assignment
   inside the target department following CRPC Head Round-Robin logic. DEPARTMENT_ROUTING_MASTER
   already has CRUD (MasterDataController.java:67-95). On save, status and ownership route to the DO
   with the standard new-assignment notification.
 - UST766: forward to a REGULATORY BODY must be restricted to the validated master list WITH VERIFIED
   EMAIL IDs, and on submit an awareness email is automatically sent to the complainant.
   GET /master-data/regulatory-bodies is already called by the frontend (rbio-workflow.service.ts:163)
   — VERIFY the backend. Note FORWARD_TO_REGULATORY_BODY is one of the THREE ORPHANED ACTIONS that
   throws "Unknown RBIO action" at RbioWorkflowService.java:502 today, silently, because every failure
   path is catchError(() => of(default)).
 - UST765/525: the forward action captures Reason for Transfer, target office, language (for
   vernacular) and comments, and the CRPC Head approval/rejection flow applies IDENTICALLY to the
   Deputy Ombudsman's version — build once, authorise twice.
 - UST759: the system identifies the last-active DO/Reviewer and reassigns automatically on save;
   if unavailable the Deputy Ombudsman must manually select a replacement before saving completes.
   THIS OVERLAPS S3's send-back reassignment mechanism — REUSE THEIRS, do not duplicate. Get the
   contract for GET /complaints/{id}/last-active-officer from S3.
 - UST628/627: on Save and Proceed the status changes to the relevant closed/forwarded code
   automatically, no further closure action by the DO is required, and the closure event is logged
   with the Ombudsman's / forwarding authority's details. Forwarding events that auto-close must also
   trigger the standard closure communication (UST661) — coordinate with S6 and S4.
 - UST760: a transfer request routes to the CRPC Head queue for approval/rejection; approved
   transfers auto-assign to the destination office DO with the region field updated.

NOTE CrpcHeadController.java:93-102 bulkReassign is a STUB persisting nothing, and :105-111
reopenComplaint likewise returns canned JSON (S3 owns reopen).

