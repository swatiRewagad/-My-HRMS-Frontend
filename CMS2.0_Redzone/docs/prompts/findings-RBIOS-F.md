# Findings — RB-IOS QA Session F (workflow ladder, assessment, entities, closure clause, final decision)

Brief: `docs/prompts/20-RBIOS-QA-F-workflow-ladder-assessment-decision.md`
Session started 2026-09-25. Backend port 8096. Spec prefix `e2e/rbio/rbios-f-*.spec.ts`.

Status key: **PASS** / **FAIL** / **NOT-BUILT** / **CONTRADICTS-SPEC**.

---

## Step 1 — Audit verification (read-only, before any test was written)

The brief's audit is a point-in-time reading. Every claim below was re-checked against today's code.

### Confirmed exactly as the brief states

| Audit claim | Evidence |
|---|---|
| `SUBMIT_FOR_REVIEW` has **no `.comment()`** | `service/RbioLadderActions.java:106-110`. Spec is `Spec.of("reviewer_review","REVIEWER_REVIEW","ASSESSMENT").assign(REVIEWER, ROUND_ROBIN).sla(...).from("in_progress","assigned","returned").roles(DEALING_OFFICIAL, OFFICER)`. No `.comment()`. Every send-back in the same file (`:131-160`) does call `.comment()`. |
| `requireComment` is real and throws a genuine 400 | `service/RbioWorkflowService.java:861-876` — `ResponseStatusException(BAD_REQUEST)`, accepts `remarks` **or** `comments` via `firstNonBlankParam`. |
| Blocking is a **whitelist** (`validForState`) | `RbioLadderActions.java:287-292` — returns false unless `fromStatuses.contains(status)`. So `SUBMIT_FOR_REVIEW` is blocked from `deputy_review`, `ombudsman_review`, `escalated`, `forwarded`, `closed`, `conciliation` alike. |
| DO **cannot** `DECIDE_NON_MAINTAINABLE` | `:175-184` — roles are `DEPUTY_OMBUDSMAN, OMBUDSMAN, SUPERVISOR` only. `DECIDE_MAINTAINABLE` (`:165-170`) does include `DEALING_OFFICIAL`. |
| `DECIDE_MAINTAINABLE` records only (`toStatus = null`), comment mandatory | `:165-170` — `Spec.of(null, "MAINTAINABILITY_DECIDED", "ASSESSMENT").comment().fx(FX_MAINTAINABILITY)`. |
| `DECIDE_NON_MAINTAINABLE` is terminal, closes, requires `closureClause` | `:175-184` — `.closure("NON_MAINTAINABLE").require("closureClause").comment()...terminal()`. |
| `DEPUTY_OMBUDSMAN_DECISION` requires **both** `maintainability` and `decision` | `:190-196`. |
| `"NOT_MAINTAINABLE"` normalises to `"NON_MAINTAINABLE"` | `RbioWorkflowService.java:1633`. |
| All three handovers declare `ROUND_ROBIN` | `:106-124` — `SUBMIT_FOR_REVIEW`, `FORWARD_TO_DEPUTY_OMBUDSMAN`, `FORWARD_TO_OMBUDSMAN`. |
| **`ROUND_ROBIN` never consults `targetUser`** | `RbioWorkflowService.java:913-920` — the branch calls `assignByRole(...)` only. Only `TARGET_PARAM` (`:906-911`) and `PREVIOUS_HOLDER` (`:929-934`) read `targetUser`/`targetUserId`. So a manual target on any handover is **silently ignored — no 400, no 422**. |
| `ADVISORY_COMPLIED` closes and is terminal, with no `.assign(...)` | `:199-208` — `Spec.of("closed","ADVISORY_COMPLIED","FINAL_DECISION").closure(...).comment()...terminal().from("advisory_issued")`. No assign. |
| `ADVISORY_COMPLIED` is **not** granted to the Dealing Official | `config/RbioStatusMasterSeeder.java` — present in the Reviewer (`:153`), Deputy (`:160`) and Ombudsman (`:168`) grants; **absent** from the DEALING_OFFICIAL grant (`:364-367`). Corroborates that it was never a DO-facing handoff. |
| `proposedClause` is **frontend-only** | `grep -rn 'proposedClosureClause\|proposed_closure_clause\|proposedClause' cms-backend/src/main/java/` → **zero hits**. Frontend has it: `rbio-complaint-detail.component.ts:40,80` (`proposedClause = signal('')`), `.html:239` (input with `[readonly]="isFieldReadOnlyDueToDecision()"`). |
| Auto-population of the proposed clause does not exist | `rbio-complaint-detail.component.ts:167` and `:196` both hardcode `proposedClause: ''` when mapping the server response. |
| `compensationAwarded` does not exist under that name | `grep -rn 'compensationAwarded\|compensation_awarded'` across `cms-backend/src` and `cms-portal-frontend/src` → **zero hits**. |
| Frontend `approvalOptions` gives all three rungs a `nameLabel`, **including `ombudsman`** | `rbio-complaint-detail.component.ts:128-132`. The UI offers a name picker exactly where the story says auto-only. |
| Add Entity cap is 6 and enforced server-side | FE `rbio-add-entity.component.ts:20,42-44,76-84`; BE `RbioAdditionalEntity.MAX_PER_COMPLAINT = 6` (`:32`) and `RbioAdditionalEntityService.assertCapAllowsOneMore` (`:67-74`) → **HTTP 409**, counted from persisted rows. |
| FE `canAddEntity` excludes the Dealing Official | `rbio-add-entity.component.ts:36-40` — `['RBIO_OFFICER','RBIO_SUPERVISOR','RBIO_DEPUTY_OMBUDSMAN']`. Backend POST guard **does** list `DEALING_OFFICIAL` first (`RbioCaseFileController.java:84-85`). Confirmed mismatch. |
| FE `isAssessmentStage` list | `:46-49` — `['assigned','in_progress','assessment','deputy_review','reviewer_review']`. |
| Nodal Officer tab exists; controller has a role gap | Tab at `rbio-complaint-detail.component.ts:116` (`{ key: 'nodal', label: 'Nodal Officer Record' }`). `NodalOfficerRecordController.java:21` = `@RequestMapping("/api/nodal-officer-records")` (**no `/api/v1`**); both `POST` (`:32-33`) and `GET /by-complaint/{n}` (`:53-54`) are `@RbioRoleGuard(roles = {"RBIO_OFFICER","RBIO_SUPERVISOR","RBIO_ADMIN"})`. DO / REVIEWER / DEPUTY_OMBUDSMAN / OMBUDSMAN are **not** listed. |
| Add Entity toasts that an NO record was created | `rbio-add-entity.component.ts:109` — ``Entity "${this.entityName}" added. NO record created.`` |
| TIFF is accepted | `OcrController.java:42-43` — `ALLOWED_CONTENT_TYPES = Set.of("application/pdf","image/jpeg","image/png","image/tiff")`. |

### CORRECTIONS to the audit — findings that do NOT hold today

1. **"The client-side mandatory check is only `entityName` + `entityType`" is true of the browser, but
   the brief's inference — that `entityBranch`/`entityCategory` are "not enforced" — is WRONG for the
   server.** `RbioAdditionalEntityService.add` (`:91-101`) calls `requireField` on **all four**:
   `entityName`, `entityBranch`, `entityType`, `entityCategory`. Its own comment says the mandatory set
   "mirror[s] the primary entity's set (UST487)... validated on the SERVER because UST478 requires
   mandatory-ness to be enforced, not merely indicated in red."
   - So manual case **50** ("all mandatory primary-entity fields are mandatory on additional entities")
     **PASSES at the API level** and the real defect is narrower: the browser's `submitEntity()`
     (`:91-93`) gates only on `entityName` + `entityType`, so the UI will POST a blank branch/category
     and surface the server's 400 as a generic failure instead of marking the fields.
   - This changes case 50 from "genuine mismatch worth asserting" to **FE-only gap**. Asserted both ways.
2. **`RbioLadderActions.java:102-105` carries a code comment that is factually wrong.** It claims
   *"A caller who names targetUser overrides it — that is the Manual option — and TARGET_PARAM is not
   needed as a separate row because applyAssignee treats an explicit target as winning for
   PREVIOUS_HOLDER and TARGET_PARAM alike."* The second half is true; the first half is not, because
   these three rows declare `ROUND_ROBIN`, and `applyAssignee`'s `ROUND_ROBIN` branch never reads
   `targetUser`. The comment documents an intent the code does not implement — which is very likely
   how the silent-ignore defect survived review.
3. **`RbioAdditionalEntityService.add` also refuses a duplicate entity name** (`:103-106`, HTTP 409,
   `existsByComplaintNumberAndEntityNameIgnoreCase`). Not mentioned in the brief; it affects how a
   cap test must seed six entities (they need distinct names).

### Environment facts at session start

- Keycloak 9090: **DOWN** (nothing listening). Not started — brief requires coordination.
- Listening: user's backend **8092** only; dev servers on **4200** and **4202**. No D (8093) or E (8095)
  backend up yet, so no other session had started when F began.
- Highest migration in use: MySQL **V109** (`database/V109__filing_window_refusal_wording.sql`),
  Oracle **V106** (`database/oracle/V106__filing_window_refusal_wording.sql`). F's reserved
  V120-124 / V117-121 are free.
- Highest seeder `@Order` in use: **66**. F's reserved 80-84 are free.
- `cms-backend/target/classes` mtime 2026-09-16 — stale relative to the tree; a 404 or a phantom 500
  from a reused JVM is expected until 8096 is started fresh.
