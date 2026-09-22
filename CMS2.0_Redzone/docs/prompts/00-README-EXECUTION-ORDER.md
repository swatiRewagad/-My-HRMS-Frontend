# RBIO Delivery — Prompt Execution Order

Baseline: HEAD **c23b899** (2026-09-18). 281 RBIO stories (UST422-675, UST754-780).
Re-verified against the three commits that landed after the initial gap analysis
(41d7670 JWT-authoritative, a5953cc award/hearing/statutory-guards, c23b899 dashboard signals).

## Files, in execution order

| Order | File | Session | Port | Runs |
|-------|------|---------|------|------|
| — | `01-STAGE0-preflight-checklist.md` | you, not Claude | — | before anything |
| 1 | `02-WAVE0-foundation.md` | W0 | 8092 | **alone** |
| 2 | `03-WAVE1-shared-contract-block.md` | — | — | pasted into every S1-S7 prompt |
| 3 | `04-WAVE1-S2-assignment-no-records.md` | S2 | 8093 | group 2a, parallel |
| 3 | `05-WAVE1-S3-workflow-ladder.md` | S3 | 8094 | group 2a, parallel |
| 3 | `06-WAVE1-S6-notifications-email-history.md` | S6 | 8097 | group 2a, parallel |
| 4 | `07-WAVE1-S1-identity-grid-admin.md` | S1 | 8092 | group 2b, ~10 min later |
| 4 | `08-WAVE1-S4-closure-clauses-letters.md` | S4 | 8095 | group 2b, ~10 min later |
| 4 | `09-WAVE1-S5-conciliation-transfers.md` | S5 | 8096 | group 2b, ~10 min later |
| 5 | `10-WAVE1B-S7-reports-ocr-locking.md` | S7 | 8098 | when a port frees |
| 6 | `11-WAVE2-integration-full-suite.md` | W2 | 8092 | **alone, last** |

## How to assemble a Wave 1 prompt

Each S1-S7 session gets, concatenated in this order:

1. Your standing BASE prompt (the `<CONTEXT>`/`<YOUR ROLE>`/`<BOUNDARIES>`… block)
2. `03-WAVE1-shared-contract-block.md`, with the 4 bracketed values substituted
3. Wave 0's **contract report** (pasted from the W0 session transcript)
4. That session's own delta file (04-10)

Wave 0 and Wave 2 need only BASE + their own file.

## Gates

**Stage 0 → Wave 0:** blockers #1 (scheme year) and #4 (role decision) answered.

**Wave 0 → Wave 1** — all four must hold:
- 4 new roles exist in Keycloak realm `cms` and the AOP guard accepts them
- `RBIO_WORKFLOW_TRANSITION` drives `performAction` (old switch cases gone)
- `RbioWorkflowServiceTest` + `RbioWorkflowControllerTest` still green
- W0 published its contract report in-conversation

**Wave 1 → Wave 2:** all seven landed, nobody editing.

## Allocations (do not overlap)

| Session | MySQL V | Oracle V | @Order | Port |
|---------|---------|----------|--------|------|
| W0 | 57-60 | 55-58 | 20-23 | 8092 |
| S1 | 61-65 | 59-63 | 24-27 | 8092 |
| S2 | 66-70 | 64-68 | 28-31 | 8093 |
| S3 | 71-75 | 69-73 | 32-35 | 8094 |
| S4 | 76-80 | 74-78 | 36-39 | 8095 |
| S5 | 81-85 | 79-83 | 40-43 | 8096 |
| S6 | 86-90 | 84-88 | 44-47 | 8097 |
| S7 | 91-95 | 89-93 | 48-51 | 8098 |
| W2 | 96-99 | 94-97 | 52-55 | 8092 |

Highest in use at c23b899: MySQL **V56**, Oracle **V54**, seeder `@Order` **19**.

## What changed after the S4 commits

1. **Migration numbers shifted** — V56/V54 were taken by `aa_order_statutory_guards`.
2. **`@Order` ranges shifted** — 19 now in use (`ClauseLabelTranslationSeeder`).
3. **Playwright `baseURL` is now overridable** via `UI_BASE_URL`; use **4202** (already
   CORS-allowed) not 4200. The 8082-compiled-in theory is refuted — the real requirement is
   CORS allowlist **and** a Keycloak redirect URI.
4. **`RbioRoleGuardAspect` no longer trusts headers blindly** — token first, headers only when
   the token yielded nothing AND `cms.security.allow-dev-identity-headers` is on. The
   "unverified base64 decode" finding is FIXED; W0 must not re-report it.
5. **A new hazard class exists: param-name mismatch.** The RBIO award defect (client sent
   `compensationAmount`, server read `awardAmount`, award persisted as 0.00 while answering
   success) is now fixed, but every session must diff sent-vs-read field names. A 200 does not
   mean the data landed.
6. **`DEFAULT_CLOSURE_CLAUSE = '15(1)(a)'`** is exported from `e2e/utils/test-data.ts`; closure
   in tests must pass a clause or appeal-filing 503s.
