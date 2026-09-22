# WAVE 1 — SHARED PARALLEL SESSION CONTRACT

Paste this into **every** S1-S7 prompt, between your BASE prompt and the session's own delta file.
Substitute the four bracketed values from the table in `00-README-EXECUTION-ORDER.md`.

Assembly order per session: BASE prompt → this block → Wave 0's contract report → session delta.

---

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
