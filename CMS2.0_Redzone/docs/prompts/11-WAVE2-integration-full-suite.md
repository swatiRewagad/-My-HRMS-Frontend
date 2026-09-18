# WAVE 2 — INTEGRATION, RECONCILIATION & FULL-SUITE VERIFICATION

**Run alone, last.** All seven sessions have landed; nobody else is editing. Port **8092**.
MySQL **V96-V99**, Oracle **V94-V97**, @Order **52-55**. Prepend your standing BASE prompt.

---

<BATCH: WAVE 2 — INTEGRATION, RECONCILIATION & FULL-SUITE VERIFICATION. Run alone.>

You are the integrator. You may now restart shared services. You are NOT here to add features —
you are here to prove the 281 stories work TOGETHER and to find what seven parallel sessions could
not see individually.

Run items 1-11 IN ORDER. The sequence is deliberate: reconcile, hunt, secure, replay, i18n, audit,
THEN test. Running the suite first wastes a full cycle because items 1-8 change code.

 1. RECONCILE THE SEAMS. Each pair below was built by two different sessions against an agreed
    contract. Verify the contract holds AT RUNTIME, not merely at compile time:
      S1 report access-roles      <-> S7 report-side enforcement and scoping
      S1 PNO/NO mapping admin     <-> S2 NO/PNO resolution logic
      S2 config keys              <-> S6 scheduled-task consumption
      S2 durable round-robin      <-> S3 auto-assignment, S5 transfer assignment
      S3 field-override history   <-> S6 timeline history (ONE History tab, TWO data sources)
      S3 Add-Entity six-cap       <-> S4 impleading, S5 meeting participants (must be ONE cap)
      S3 last-active-officer      <-> S5 UST759 reassignment
      S4 closure clause tiering   <-> S3 proposed-clause auto-populate
      S4 letter template pipeline <-> S5 MOM letter generation
      S5 transfer timeline writes <-> S6 timeline immutability
      S6 attachment source column <-> S5 scanned MOM upload, S6 upload-link persistence

 2. HUNT DIVERGENT STATUS STRINGS. Complaint.status is a bare String with no compiler check. Diff
    every status literal now in the codebase against Wave 0's RBIO_STATUS_MASTER and the published
    registry. Any orphan is a silent bug. Confirm no session added a FIFTH hardcoded CLOSED_STATUSES
    list — there were four: WorkflowController:57, NotificationScheduledTasks:29,
    AppealWorkflowService:36, AppealController:28.

 3. HUNT DUPLICATE MECHANISMS. Before this programme there were: three in-memory round-robin
    counters plus one dead durable pointer; two state->office mappings
    (ComplaintNumberGeneratorService vs GeoLocationController, 24 offices vs 16); two transfer paths
    (CrpcHead queue vs CepcWorkflowService FORWARD_TO_OTHER_OFFICE); two clause implementations
    (CLOSURE_CLAUSE_MASTER vs WorkflowController's hand-built list); three file-size limits; and two
    compensation-cap definitions (RbioCompensationService vs RulesApiController:82). Confirm each is
    now singular.

 4. HUNT REMAINING PHANTOMS AND PARAM MISMATCHES — the two hazard classes that make a feature demo
    cleanly while persisting nothing or persisting wrong data.
    (a) PHANTOM ENDPOINTS. For every RBIO Angular service method, assert a real backend endpoint
        exists. catchError(() => of(default)) hides 404s repo-wide. Re-check the seven known at
        baseline: /api/v1/rbio/complaints, /complaints/{id}/action-override,
        /api/v1/reports/access-roles, /reports/drill-down/no-records, dateOfSending, the
        signed-letter upload, /api/v1/crpc/reports. Confirm no session ADDED a new one.
    (b) PARAM-NAME MISMATCHES. For every write path, diff the field names the component SENDS
        against the keys the service READS. A 200 does not mean the data landed. The worst instance
        found made a citizen's statutory compensation persist as 0.00 while answering success:true
        (client sent compensationAmount, server read awardAmount; fixed in a5953cc, which now also
        REFUSES a missing amount rather than defaulting — verify that refusal survived Wave 0's
        transition-table refactor). Two more were reported unverified: 13-1 notice
        ISSUE_13_1_NOTICE vs ISSUE_NOTICE_13_1, and implead impleadPartyName vs partyName. Confirm
        both. Assert NO monetary, date or identity field defaults silently anywhere.
    (c) INERT SIGNALS. A computed() reading a plain class field registers no dependency, so filters
        and search boxes silently do nothing. Fixed in the AA dashboard (c23b899) and previously
        reported in the RBIO task list and CEPC dashboard. Verify every filter/search/sort control
        added by S1-S7 actually works by DRIVING it, not by reading the code. Also confirm no label
        map is interpolated without the translate pipe (that renders a raw key to staff).

 5. SERVER-SIDE AUTHORITY SWEEP. Every check that decides eligibility, maintainability, closure,
    clause visibility, compensation caps, upload-link blocking, reviewer closure-eligibility, the
    2000-char limit, file size, report scoping, the six-entity cap, meeting-status exclusions or
    record locking must be enforced SERVER-side. Write direct-API tests that BYPASS the UI for each.
    A browser-only check is not a control.
    Also verify the JWT-authoritative pattern from commit 41d7670 holds everywhere: the token is
    decoded FIRST and X-User-* headers are consulted only when the token yielded nothing AND
    cms.security.allow-dev-identity-headers is on. That defect class has now been found SEVEN times
    (four role guard aspects, RequestIdentityResolver, and the report builder's trusted
    X-User-Role/X-User-Department headers). Confirm no session reintroduced a header-first read.

 6. MIGRATIONS. Apply ALL of MySQL V57-V99 and Oracle V55-V97 to a CLEAN database, in order, from
    scratch, and confirm they apply cleanly and are re-runnable. Seven sessions took independent
    V-numbers in two out-of-sync directories on a ddl-auto database where columns were never
    dropped — THE CLEAN-DB REPLAY IS THE ONLY PROOF THE SQL MATCHES WHAT HIBERNATE ACTUALLY CREATED.
    Then verify every change against the LIVE API, not the SQL file. MySQL 8.4 has no
    ADD COLUMN / CREATE INDEX IF NOT EXISTS — check every guard uses information_schema, and Oracle
    USER_TAB_COLUMNS / USER_INDEXES.

 7. i18n FULL SWEEP. Use the committed helper rather than ad-hoc checks:
      python scripts/qa/i18n_check.py --scan-components --evict
    It checks all ten locales AND asserts the nine non-English values DIFFER from English, which is
    what catches a seeder that ran but copied English through; --evict handles newly seeded locales
    being invisible over the API until the translation cache is evicted.
    Seven sessions added keys and keys are idempotent by existsByCode, so A DUPLICATE CODE SILENTLY
    KEPT THE FIRST TEXT — hunt for keys whose English text does not match the story that owns them.
    Confirm no seeder @Order collision across the ranges (W0 20-23, S1 24-27, S2 28-31, S3 32-35,
    S4 36-39, S5 40-43, S6 44-47, S7 48-51; 19 was the highest pre-existing). Remember Bengali
    stores digits in Bengali numerals, so an ASCII digit check silently skips it.

 8. THE SCHEME-YEAR AUDIT — HIGHEST PRIORITY OF THIS BATCH. Grep the whole repo for 2026 in any
    clause or scheme context. Confirm: WorkflowController.java:487-489's invented 16(5)/16(6)
    newIn2026 clauses are GONE; no session authored any RBIOS 2026 clause text; the date-gated
    scheme-version switching mechanism EXISTS but the 2026 set is EMPTY; and every 2026-dependent
    assertion is an explicit fixme rather than a passing test. Report every remaining occurrence with
    file:line. NEVER LET FABRICATED LEGAL TEXT SHIP.
    Also confirm the legally-uncertain guards follow the established config-OFF pattern (as
    cms.aa.order.max_award_amount = 0 and cms.aa.order.block_sub_judice = false do), so arming after
    legal sign-off is a one-row config change and the migrations are behaviour-neutral.

 9. FULL E2E SUITE, per directory, aggregated. The single full-suite command buffers indefinitely
    through tail, so run per directory:
      for d in admin public i18n aa cepc rbio re-portal; do \
        API_BASE_URL=http://localhost:8092 UI_BASE_URL=http://localhost:4202 \
        npx playwright test "e2e/$d" --project=chromium --reporter=line > /c/tmp/w2_$d.txt 2>&1; done
    Run from cms-portal-frontend or the chromium project is not found. Never --headed for bulk runs.
    BEFORE BELIEVING ANY GREEN RESULT, verify the harness — not the product:
      - Keycloak reachable on 9090. auth.ts isKeycloakAvailable() returns false on ANY throw and
        callers test.skip(), so an outage yields a GREEN VACUOUS staff suite. REPORT SKIPPED COUNTS
        SEPARATELY FROM PASSES. A skip is not a pass.
      - The dev-server port must be BOTH CORS-allowed AND a Keycloak redirect URI on the
        cms-frontend client. 4202 is already in the hardcoded CORS allowlist
        (application-dev-local.yml:113; note CMS_CORS_ORIGINS and -Dcms.cors.allowed-origins are
        SILENTLY IGNORED under dev-local because there is no ${} placeholder). Confirm with:
          curl -s -o /dev/null -w "%{http_code}" -X OPTIONS \
            http://localhost:8092/api/v1/i18n/translations/en \
            -H "Origin: http://localhost:4202" -H "Access-Control-Request-Method: GET"
        200, not 403. Missing either registration produces empty pages, raw translation keys and
        "Complaint not found" for records that demonstrably exist — indistinguishable from a real
        defect.
      - Any spec importing test from '@playwright/test' instead of '../fixtures' reads from the
        user's 8082 backend. Grep for that import across all new specs.
    BEWARE TAUTOLOGICAL TESTS. The AA backlog spec was found to be fraudulent: expect(x || true)
    tautologies, error-swallowing if (!('error' in result)) guards, and status sets that ACCEPTED 404
    AS A PASS. It "tested" two actions that do not exist in that module at all. Audit every new spec
    for these three patterns; a suite that cannot fail is worse than no suite.

10. BACKEND SUITE + BACKWARD COMPATIBILITY. Baseline was 971 tests / 0 failures (2026-09-16). A
    failure is probably OURS — do not excuse it as pre-existing without proving it on a clean
    checkout. Confirm CEPC, CRPC, RE, AA and RBIOS-2021 workflows still work: Wave 0's
    transition-table refactor of RbioWorkflowService is the single riskiest change in the programme
    and regressions will surface here, not in unit tests. Note the pre-existing failures listed
    under <KNOWN BROKEN> and report them as pre-existing.

11. FINAL REPORT:
      - Per-story coverage table for all 281 IDs: BUILT / ALREADY-EXISTED / BLOCKED-on-user with the
        blocker named. Reconcile the count against the user's stated 283 and identify the discrepancy.
      - Every assumption made by every session, consolidated.
      - Pre-existing failures listed as pre-existing.
      - A prioritised go-live defect list, separating ENGINEERING work from what needs LEGAL
        sign-off (scheme-year clause codes, compensation ceiling values, whether a closure clause
        becomes mandatory and what happens to the 1646 legacy clause-less closures, the nine
        non-English translations of clause labels — a translated clause label is a legal statement
        about what that clause means).
      - An explicit GO / CONDITIONAL-NO-GO verdict for the RBIO module, with the conditions named.

