# CMS 2.0 — State Transition Matrix (the "Excel" view)

> Companion to **`CMS-Workflow-Transition-Diagram.md`**. Same facts, rotated 90°.
> **Code is the truth** — every cell is read out of the Java sources, not from documentation.
> Generated 2026-09-28.

---

## §A — How to read the matrices

A transition in CMS 2.0 is **not** a 2-tuple `(state, action)`. The code resolves a 3-tuple:

```
          ( FROM_STATUS ,  ACTION ,  ROLE )  ──▶  effect
```

`RBIO_WORKFLOW_TRANSITION`'s primary lookup is literally
`findByActionCodeAndRoleNameAndFromStatus`. So a flat 2-D grid must put the **role** inside the
cell. That is what these matrices do:

```
   ┌─────────────────────────────────────────────────────────────────────┐
   │  ROWS    = FROM_STATUS  (the state the complaint is in)             │
   │  COLUMNS = ACTION       (the ACTION_CODE)                           │
   │  CELL    = who may fire it + what it demands + where it lands       │
   └─────────────────────────────────────────────────────────────────────┘
```

### Cell notation

| Token | Meaning |
|---|---|
| `DO RV DyO OM` | RANK ladder: Dealing Official, Reviewer, Deputy Ombudsman, Ombudsman |
| `OF SU CN AJ` | LEGACY ladder: Officer, Supervisor, Conciliator, Adjudicator |
| `AD` | `RBIO_ADMIN` |
| `→x` | resulting `TO_STATUS` |
| `→=` | **`TO_STATUS = NULL` — status UNCHANGED** (not unset) |
| `✖` | `IS_TERMINAL = 'Y'` |
| `!c` | `REQUIRES_COMMENT = 'Y'` |
| `!p(…)` | `REQUIRED_PARAMS`; `\|` = at-least-one-of, `,` = AND |
| `@X` | `ASSIGN_STRATEGY`: `@A`=ACTOR `@T`=TARGET_PARAM `@R`=ROUND_ROBIN `@P`=PREVIOUS_HOLDER `@–`=owner unchanged |
| `⊕FX` | side effect |
| `ADV` | offered by the UI only — **not enforced on execute** |
| `EXEC` | enforced on execute (refusal raised) |
| `·` | not available in this state |
| `⚠` | code/documentation divergence, or a live defect |

### The three availability classes — the key insight for the matrix

Because RBIO advertises and enforces from *different* code, a cell has one of **three** values, not
two:

```
   ✔ ADV+EXEC   offered AND accepted
   ◐ EXEC-only  NOT offered by the UI, but a direct POST SUCCEEDS   ← the hole
   ✖ blocked    refused on execute
```

`◐` is the whole reason a matrix is worth building: it is invisible in any UI walkthrough.
Source: `RbioTransitionRegistry.VALID_FROM` is consulted only by `validForState` (advertisement);
`performAction` never calls it.

### Enforcement depth per domain (which matrix can be trusted as a fence)

| Domain | Role on execute | From-status on execute | Matrix is a… |
|---|---|---|---|
| **AA** | ✔ 403 | ✔ **409 + availableActions** | real fence |
| **CRPC** | ✔ | ✔ | real fence |
| **RBIO** | ✔ DB-first *(unless role blank)* | **✖ advertisement only** | advisory grid |
| **RE** | ✔ | linear only | real fence |
| **CEPC** | ✖ not per-action | ✖ | **UI hint only** |

---

## §B — MATRIX 1 · RBIO core (Wave 0 actions × status)

`OWNED_BY = WAVE0` · source `RbioTransitionRegistry`

Columns 1-9 of 24. Rows are `Complaint.status` legacy values.

| FROM ↓ \ ACTION → | ACCEPT | TAKE_ACTION | APPROVE | RETURN_TO_OFFICER | ESCALATE | REQUEST_INFO | RESOLVE | REJECT | REASSIGN |
|---|---|---|---|---|---|---|---|---|---|
| **pending** | · | · | · | · | · | · | · | · | ✔ `AD SU DyO OM` `→=` `@T` ⊕REASSIGN_ROLE |
| **assigned** | ✔ `DO OF` `→in_progress` `@A` sla OFFICER_ASSESSMENT | ✔ `DO OF` `→in_progress` `@A` | ◐ | · | ✔ `DO OF SU DyO OM AD` `→escalated` `@R __NEXT__` ⊕STAMP_ESCALATED | ✔ `DO OF` `→info_requested` | ◐ | ✔ `OF DO OM` `→rejected` ✖ ⊕RESOLVED_AT | ✔ |
| **in_progress** | ◐ | ◐ | ✔ `SU DyO OM` `→` **rank-computed** `@R __NEXT__` ⊕APPROVE_LADDER | ✔ `SU DyO OM` `→returned` | ✔ | ✔ | ✔ `DO OF SU DyO OM` `→resolved` ⊕CLOSURE_FROM_PARAM ⊕RESOLVED_AT | ✔ | ✔ |
| **info_requested** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **returned** | ✔ `DO OF` `→in_progress` `@A` | ✔ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **escalated** | ◐ | ◐ | ◐ | ✔ `SU DyO OM` `→returned` | ◐ | ◐ | ◐ | ◐ | ✔ |
| **conciliation** | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ | ◐ | ◐ | ✔ |
| **adjudication** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **reviewer_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **deputy_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **ombudsman_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **advisory_issued** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **sent_to_other** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **forwarded_external** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **reopened** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ |
| **resolved** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |
| **closed** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |
| **rejected** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |
| **withdrawn** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |
| **adjudicated** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |
| **conciliated** ✖ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | **✖ NOT-IN** |

★ **The `◐` column is the finding.** Only `REASSIGN` / `BULK_ASSIGN` / `CLOSE_COMPLAINT` /
`SCHEDULE_MEETING` carry a genuine execute-time state fence (the `EXCLUDED_FROM` NOT-IN sets and
`BLOCKS_MEETING`). Every other Wave 0 action will execute from **any** status if POSTed directly.

Columns 10-18:

| FROM ↓ \ ACTION → | FWD_CONCILIATION | FWD_ADJUDICATION | ESCALATE_TO_ADJ | CONCILIATION_SUCCESS | CONCILIATION_FAILED | ADJUDICATION_AWARD | ADJUDICATION_REJECT | ISSUE_ADVISORY | SCHEDULE_MEETING |
|---|---|---|---|---|---|---|---|---|---|
| **assigned** | ✔ `DO OF SU DyO OM` | · | · | · | · | · | · | ✔ `→=` ⊕ADVISORY_TEXT | ✔ **S5** `→=` !p(meetingDate\|hearingDate) ⊕MEETING_SCHEDULED |
| **in_progress** | ✔ `→conciliation` | ✔ `SU DyO OM` `→adjudication` | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ `→advisory_issued` | ✔ |
| **escalated** | ✔ | ✔ | ✔ `CN` | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ **S5** |
| **conciliation** | ◐ | ◐ | ✔ `CN` `→adjudication` ⊕CONCILIATION_FAILED *(no date)* ⚠ | ✔ `CN` `→conciliated` ⊕CONCILIATION_SUCCESS | ✔ `CN` ⊕CONCILIATION_FAILED *(+date)* | ◐ | ◐ | ◐ | ✔ |
| **adjudication** | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ `AJ OM` **not DyO** !p(awardAmount\|compensationAmount) `→adjudicated` ⊕AWARD | ✔ `AJ OM` ⊕ADJUDICATION_REJECT | ◐ | ◐ |
| **reviewer_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ **S5** |
| **deputy_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ **S5** |
| **ombudsman_review** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ **S5** |
| **returned** | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ◐ | ✔ **S5** |
| any **BLOCKS_MEETING='Y'** | — | — | — | — | — | — | — | — | **✖ EXEC** `assertMeetingAllowedForStatus` |

Columns 19-24:

| FROM ↓ \ ACTION → | ISSUE_NOTICE_13_1 | IMPLEAD_PARTY | CLOSE_COMPLAINT | REOPEN | BULK_ASSIGN |
|---|---|---|---|---|---|
| **in_progress** | ✔ **S3 seeder** `DO` `→=` stage NOTICE_13_1_ISSUED ⊕NOTICE_13_1 | ◐ | ✔ `AD OM` `→closed` ✖ | · | ✔ `AD` `→=` ⊕BULK_ROLE |
| **info_requested** | ✔ **S3 seeder** `DO` | ◐ | ✔ | · | ✔ |
| **sent_to_do** | ✔ **S3 seeder** `DO` | ◐ | ✔ | · | ✔ |
| **sent_back_do** | ✔ **S3 seeder** `DO` | ◐ | ✔ | · | ✔ |
| **reopened** | ✔ **S3 seeder** `DO` | ◐ | ✔ | · | ✔ |
| **escalated** | ✖ *(excluded — file is with a senior)* | ◐ | ✔ | · | ✔ |
| **adjudication** | ✔ `AJ OM` (Wave 0) | ✔ `AJ OM` !p(partyName\|impleadPartyName) ⊕IMPLEAD | ✔ | · | ✔ |
| **closed** ✖ | ◐ | ◐ | **✖ NOT-IN {closed}** | ✔ `OM AD` ⊕REOPEN | **✖ NOT-IN** |
| **resolved** ✖ | ◐ | ◐ | ✔ | ✔ `OM AD` | **✖ NOT-IN** |
| **adjudicated** ✖ | ◐ | ◐ | ✔ | ✔ `OM AD` | **✖ NOT-IN** |
| **conciliated** ✖ | ◐ | ◐ | ✔ | ✔ `OM AD` | **✖ NOT-IN** |
| **rejected / withdrawn** ✖ | ◐ | ◐ | ✔ | ◐ | **✖ NOT-IN** |

---

## §C — MATRIX 2 · RBIO ladder (S3) — the review spine

`OWNED_BY = S3` · source `RbioLadderActions` · **these rows carry real `.from()` lists**

| FROM ↓ \ ACTION → | SUBMIT_FOR_REVIEW | FWD_TO_DEPUTY_OMB | FWD_TO_OMBUDSMAN | SEND_BACK_DO | SEND_BACK_REVIEWER | SEND_BACK_DEPUTY / _INCHARGE |
|---|---|---|---|---|---|---|
| **assigned** | ✔ `DO OF` `→reviewer_review` stage REVIEWER_REVIEW `@R RV` | · | · | · | · | · |
| **in_progress** | ✔ `DO OF` | ✔ `RV SU` `→deputy_review` | ✔ `DyO RV` `→ombudsman_review` FINAL_DECISION | ✔ !c `@P` | · | · |
| **returned** | ✔ `DO OF` | · | · | · | · | · |
| **reviewer_review** | · | ✔ `RV SU` `→deputy_review` | ✔ `DyO RV` | ✔ !c `→returned` `@P` | · | · |
| **deputy_review** | · | · | ✔ `DyO RV` `→ombudsman_review` | ✔ !c `@P` | ✔ !c `→reviewer_review` `@P` | · |
| **ombudsman_review** | · | · | · | ✔ !c `@P` | ✔ !c `@P` | ✔ !c `→deputy_review` `@P` |
| **escalated** | · | · | · | ✔ !c `@P` | · | · |

`@P` refusals — the two that the UI must distinguish:

```
   explicit targetUser present      ──▶ used, wins outright
   previousHolderFor → INACTIVE     ──▶ 422 "…no longer active…"
   previousHolderFor → NO_HISTORY   ──▶ 422 "…no previous holder…"
```
`422` not `400`, so the UI can tell "malformed request" from "you must now pick an officer".

### S3 outcome & maintainability actions

| ACTION | FROM | ROLES | TO | RULES |
|---|---|---|---|---|
| `DECIDE_MAINTAINABLE` | *(any)* | 6 roles | `→=` stage MAINTAINABILITY_DECIDED | `!c` · ⊕MAINTAINABILITY *(determination from the ACTION, not a param)* |
| `DECIDE_NON_MAINTAINABLE` | *(any)* | `DyO OM SU` | `→closed` ✖ | `!c` `!p(closureClause)` ⊕MAINTAINABILITY |
| `DEPUTY_OMBUDSMAN_DECISION` | *(any)* | `DyO` | per row | `!c` `!p(maintainability, decision)` ⊕DEPUTY_DECISION · 400 unless maintainability ∈ {MAINTAINABLE, NON_MAINTAINABLE} **and** decision ∈ {FACILITATION, REJECTION} |
| `ADVISORY_COMPLIED` | **`advisory_issued` ONLY** | — | `→closed` ✖ | ⊕ADVISORY_COMPLIED |
| `FACILITATION` | in_progress, conciliation, deputy_review, ombudsman_review | — | `→resolved` | `!c` |
| `SETTLED` | *same four* | — | `→resolved` | `!c` |
| `WITHDRAWN` | *(any)* | — | `→withdrawn` ✖ | — |
| `NOT_A_COMPLAINT` | *(any)* | — | `→closed` ✖ | `!p(closureClause)` |
| `FORWARD_TO_REGULATORY_BODY` | *(any)* | — | `→forwarded_external` | `!p(regulatoryBodyName\|regulatoryBodyId)` ⊕REGULATORY_BODY → resolved against the validated master + citizen awareness email |
| `ISSUE_13_1_NOTICE` | **`adjudication`** | — | `→=` | alias of `ISSUE_NOTICE_13_1` |

---

## §D — MATRIX 3 · RBIO meetings & transfers (S5)

`OWNED_BY = S5` · source `RbioMeetingTransferActions`

`MEETING_ROLES = { DO, RV, DyO, OM, OF, SU, CN }`
`MEETING_FROM  = { in_progress, assigned, conciliation, escalated, returned, reviewer_review, deputy_review, ombudsman_review }`

| FROM ↓ \ ACTION → | SCHEDULE_MEETING | RESCHEDULE_MEETING | COMPLETE_MEETING | FORWARD_TO_OTHER_OFFICE | FORWARD_TO_OTHER_RBI_DEPT |
|---|---|---|---|---|---|
| **in_progress** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **assigned** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **conciliation** | ✔ | ✔ | ✔ | ✔ | **·** ⚠ omitted |
| **escalated** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **returned** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **reviewer_review** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **deputy_review** | ✔ | ✔ | ✔ | ✔ | ✔ |
| **ombudsman_review** | ✔ | ✔ | ✔ | ✔ | ✔ |
| — | — | — | — | — | — |
| **TO_STATUS** | `→=` UNCHANGED | `→=` UNCHANGED | `→=` UNCHANGED | `→sent_to_other` ★ | `→forwarded_external` |
| **TO_STAGE** | MEETING_SCHEDULED | MEETING_RESCHEDULED | MEETING_COMPLETED | SENT_TO_OTHER_OFFICE | SENT_TO_OTHER_DEPT |
| **MILESTONE** | CONCILIATION | CONCILIATION | CONCILIATION | FORWARD | FORWARD |
| **REQUIRED_PARAMS** | `meetingDate\|hearingDate` | + `rescheduleReason\|reason` | `entityAccepted\|entityAcceptance` , `minutesOfMeeting\|mom` | `targetOffice\|toOffice` , `transferReason\|reason` | `targetDepartment\|targetDept` |
| **REQUIRES_COMMENT** | N | N | N | **Y** | **Y** |
| **ASSIGN** | `@–` | `@–` | `@–` | **`@–` deliberately** — destination officer resolved only AFTER CRPC Head approval | `@–` |
| **SIDE EFFECT** | ⊕MEETING_SCHEDULED | ⊕MEETING_RESCHEDULED | ⊕MEETING_COMPLETED | ⊕TRANSFER_REQUEST | ⊕FORWARD_DEPARTMENT |
| **ROLES** | MEETING_ROLES (7) | MEETING_ROLES | MEETING_ROLES | `DO RV DyO OM OF SU` | `DO RV DyO OM OF SU` |
| **Refusal shape** | **400 + i18n key** `rbio.meeting.error.date_required` | 400 + i18n key | 400 + i18n key | **400** `rbio.forward.error.office_required` | 400 + i18n key |

★ `sent_to_other`, **not** `forwarded_external` — that substitution is the bug that hid every
transferred complaint from the CRPC Head's queue.

★ These five actions are the **only** RBIO actions whose param refusals reach the browser as a real
HTTP error. Everything in Wave 0 throws `IllegalArgumentException`, which the controller renders as
**HTTP 200 `{success:false}`** — and Angular's error branch never fires.

**Orthogonal execute-time fence, not expressible as rows:**

```
   RBIO_STATUS_MASTER.BLOCKS_MEETING = 'Y'
        ⇒ RbioMeetingService.assertMeetingAllowedForStatus REFUSES
        (six named statuses; a NOT-IN, so a status added later is covered automatically)
```

---

## §E — MATRIX 4 · RBIO role × action grant (authorisation plane)

The second dimension of the same 3-tuple: this is the `ROLE_NAME` axis with `FROM_STATUS`
collapsed. `✔` = granted, `—` = not granted, `+S3`/`+S5` = granted by an additive layer.

| ACTION \ ROLE | DO/OF | RV/SU | CN | AJ | DyO | OM | AD |
|---|---|---|---|---|---|---|---|
| ACCEPT | ✔ | — | — | — | — | — | — |
| TAKE_ACTION | ✔ | — | — | — | — | — | — |
| APPROVE | — | ✔ | — | — | ✔ | ✔ | — |
| RETURN_TO_OFFICER | — | ✔ | — | — | ✔ | ✔ | — |
| ESCALATE | ✔ | ✔ | — | — | ✔ | ✔ | ✔ |
| REQUEST_INFO | ✔ | — | — | — | — | — | — |
| RESOLVE | ✔ | ✔ | — | — | ✔ | ✔ | — |
| REJECT | ✔ | — | — | — | **—** | ✔ | — |
| FORWARD_TO_CONCILIATION | ✔ | ✔ | — | — | ✔ | ✔ | — |
| FORWARD_TO_ADJUDICATION | — | ✔ | — | — | ✔ | ✔ | — |
| ESCALATE_TO_ADJUDICATION | — | — | ✔ | — | — | — | — |
| CONCILIATION_SUCCESS | — | — | ✔ | — | — | — | — |
| CONCILIATION_FAILED | — | — | ✔ | — | — | — | — |
| **ADJUDICATION_AWARD** | — | — | — | ✔ | **★ WITHHELD** | ✔ | — |
| ADJUDICATION_REJECT | — | — | — | ✔ | — | ✔ | — |
| ISSUE_NOTICE_13_1 | **+S3** | — | — | ✔ | — | ✔ | — |
| IMPLEAD_PARTY | — | — | — | ✔ | — | ✔ | — |
| ISSUE_ADVISORY | ✔ | ✔ | — | — | ✔ | ✔ | — |
| SCHEDULE_MEETING | ✔ | **+S5** ★ | ✔ | — | ✔ | ✔ | — |
| RESCHEDULE_MEETING | +S5 | +S5 | +S5 | — | +S5 | +S5 | — |
| COMPLETE_MEETING | +S5 | +S5 | +S5 | — | +S5 | +S5 | — |
| REASSIGN | — | ✔ | — | — | ✔ | — | ✔ |
| CLOSE_COMPLAINT | — | — | — | — | — | ✔ | ✔ |
| REOPEN | — | — | — | — | — | ✔ | ✔ |
| BULK_ASSIGN | — | — | — | — | — | — | ✔ |
| SUBMIT_FOR_REVIEW | +S3 | — | — | — | — | — | — |
| FORWARD_TO_DEPUTY_OMBUDSMAN | — | +S3 | — | — | — | — | — |
| FORWARD_TO_OMBUDSMAN | — | +S3 | — | — | +S3 | — | — |
| SEND_BACK_DO | — | +S3 | — | — | +S3 | +S3 | — |
| SEND_BACK_REVIEWER | — | — | — | — | +S3 | +S3 | — |
| SEND_BACK_DEPUTY / _INCHARGE | — | — | — | — | — | +S3 | — |
| DECIDE_MAINTAINABLE | +S3 | +S3 | — | — | +S3 | +S3 | — |
| DECIDE_NON_MAINTAINABLE | — | +S3 *(SU)* | — | — | +S3 | +S3 | — |
| DEPUTY_OMBUDSMAN_DECISION | — | — | — | — | +S3 | — | — |
| FORWARD_TO_OTHER_OFFICE | +S5 | +S5 | — | — | +S5 | +S5 | — |
| FORWARD_TO_OTHER_RBI_DEPT | +S5 | +S5 | — | — | +S5 | +S5 | — |

★ Two cells worth naming:
- `ADJUDICATION_AWARD` is **deliberately withheld** from the Deputy Ombudsman — only the Ombudsman
  awards compensation. The negative cell is asserted by a unit test precisely to catch privilege
  creep.
- `SCHEDULE_MEETING` for `RV/SU` exists **only** because S5 added it; the Reviewer mirrors the
  Supervisor's Wave 0 set, which omits it.

**And the authorisation bypass, in matrix terms:** when `userRole` is blank, `performAction`
**skips this entire table** and resolves by action alone. That is a whole-matrix `✔` row.

---

## §F — MATRIX 5 · AA (appellate) — the enforced matrix

Source `AaWorkflowTransition` + `AppealWorkflowService`. From-status IS checked; a violation is a
**409 carrying `availableActions`**.

| FROM ↓ \ ACTION → | ACCEPT | REJECT | ASSIGN_TO_BENCH | REQUEST_DOCUMENTS | PREPARE_BRIEF | ESCALATE_TO_TIER2 | FORWARD_TO_AUTHORITY | SEND_BACK_REGISTRAR | SCHEDULE_HEARING | PASS_ORDER | REMAND_TO_OMBUDSMAN | DISMISS | REASSIGN | CLOSE | REOPEN |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| **filed** | ✔ `→under_review` | ✔ `→rejected` ✖ | · | · | · | · | · | · | · | · | · | · | ✔ | ✔ | · |
| **under_review** | · | ✔ `→rejected` ✖ | ✔ `→=` | ✔ `→=` | ✔ `→=` | ✔ `→=` tier 2 | ✔ | ✔ | ✔ `→hearing_scheduled` | · | · | ✔ `→rejected` ✖ | ✔ | ✔ | · |
| **hearing_scheduled** | · | ✔ | · | ✔ `→=` | ✔ `→=` | · | · | ✔ | ✔ *(re-schedule)* | ✔ `→order_passed` ✖ | ✔ → back to RBIO | ✔ | ✔ | ✔ | · |
| **order_passed** ✖ | · | · | · | · | · | · | · | · | · | · | · | · | · | ✔ `→closed` ✖ | ✔ |
| **rejected** ✖ | · | · | · | · | · | · | · | · | · | · | · | · | · | · | ✔ |
| **closed** ✖ | · | · | · | · | · | · | · | · | · | · | · | · | · | · | ✔ |

```
   OPEN     = { filed, under_review, hearing_scheduled }
   TERMINAL = { closed, rejected, order_passed }
   ★ NO draft status, deliberately: "an unreachable status in the authoritative
     vocabulary is worse than an absent one."

   Refusals:  403 AaAccessDeniedException (role)
              409 AaIllegalTransitionException (state) + availableActions
              200+success:false  IllegalArgumentException (legacy, still reachable)
   Events:    appeal.<pastParticiple> → OUTBOX_EVENT (singular)
```

⚠ An appeal against an RBIO closure needs a persisted `closureClause`. Historical rows closed before
`applyClosureCommunication` existed have none and fail with
`appeal.error_clause_not_configured` — **they are permanently unappealable** until backfilled.

---

## §G — MATRIX 6 · CRPC email-draft intake — strict and small

Source `CrpcWorkflowService` — a literal `Map<String, Set<String>>`, enforced.

| FROM ↓ \ TO → | SENT_FOR_APPROVAL | APPROVED | SENT_BACK | NOT_A_COMPLAINT | NEW_COMPLAINT |
|---|---|---|---|---|---|
| **ASSIGNED** | ✔ `DEO` | ✖ | ✖ | ✔ `DEO` | ✖ |
| **SENT_FOR_APPROVAL** | ✖ | ✔ `REVIEWER CRPC_HEAD` | ✔ `REVIEWER CRPC_HEAD` | ✖ | ✖ |
| **SENT_BACK** | ✔ `DEO` | ✖ | ✖ | ✔ `DEO` | ✖ |
| **APPROVED** | ✖ | ✖ | ✖ | ✖ | ✔ **creates the Complaint** |
| **NOT_A_COMPLAINT** ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |

`APPROVED → NEW_COMPLAINT` is the system's single intake handoff: it is what produces the
`Complaint` row that Matrices 1-4 then operate on.

---

## §H — MATRIX 7 · RE / PNO activity ladder

Source `ReActivityStatusService`.

| FROM ↓ \ TO → | OPENED | UNDER_REVIEW | RESPONSE_BEING_PREPARED | DOCUMENTS_UPLOADED | RESPONSE_SUBMITTED | OVERDUE |
|---|---|---|---|---|---|---|
| **NOT_OPENED** | ✔ `RE_PNO` | ✖ | ✖ | ✖ | ✖ | ⚙ auto |
| **OPENED** | ✖ | ✔ | ✔ | ✖ | ✖ | ⚙ auto |
| **UNDER_REVIEW** | ✖ | ✖ | ✔ | ✔ | ✖ | ⚙ auto |
| **RESPONSE_BEING_PREPARED** | ✖ | ✖ | ✖ | ✔ | ✖ | ⚙ auto |
| **DOCUMENTS_UPLOADED** | ✖ | ✖ | ✖ | ✖ | ✔ | ⚙ auto |
| **RESPONSE_SUBMITTED** | ✖ | ✖ | ✖ | ✖ | ✖ | ✖ |

`⚙ auto` = set by the **15-minute scheduled sweep**, not by any user action — the only
system-driven transition in the matrix set. `RE_PNO` is scoped by the Keycloak `entity_code`
attribute.

---

## §I — MATRIX 8 · Citizen projection (lossy, and that is the defect)

Source `CitizenStageMapper`. This is a **function, not a transition table** — but it is what the
complainant sees, so a matrix of it is the fastest way to see the damage.

| Internal status | Citizen stage shown |
|---|---|
| pending, new | **1 — Registered** |
| assigned, in_progress, under_review, escalated | **2 — Under Review (RBI)** |
| approved, sent_back | **3 — With Bank** |
| resolved, rejected, closed, withdrawn | **4 — Closed** |
| **conciliation** | ⚠ 1 — Registered |
| **adjudication** | ⚠ 1 — Registered |
| **adjudicated** *(award passed & closed!)* | ⚠ 1 — Registered |
| **conciliated** *(settled & closed!)* | ⚠ 1 — Registered |
| **reviewer_review / deputy_review / ombudsman_review** | ⚠ 1 — Registered |
| **sent_to_other / forwarded_external** | ⚠ 1 — Registered |
| **advisory_issued** | ⚠ 1 — Registered |
| **info_requested** | ⚠ 1 — Registered |
| **reopened / returned** | ⚠ 1 — Registered |

13 statuses — including **two closed ones** — fall through the default branch to Stage 1. A citizen
whose complaint was awarded compensation and closed sees "Registered".

---

## §J — MATRIX 9 · Rules & conditions plane (the "why" behind a cell)

The cells above answer *may this fire*. This matrix answers *what else must hold*. These are
guards applied by `applyTransition` **before any field is written** — they are the real
"rules & conditions" layer, and none of them is expressible as a `(state, action)` cell.

| Order | Guard | Scope | Refusal | Consequence of passing |
|---|---|---|---|---|
| 1 | `requireParams` | rows with `REQUIRED_PARAMS` | Wave 0 → **200+false**; S5 → **400 + i18n key** | — |
| 2 | `requireComment` | rows with `REQUIRES_COMMENT='Y'` | 400 | accepts `remarks` OR `comments` |
| 3 | `uploadLinkGuardService.assertActionAllowed` | all | refuse | upload-link state consistent |
| 4 | `closureCommunicationGuardService.assertClosureCommunicationComplete` | closing actions | refuse | closure letter data present |
| 5 | `impleadService.findIncompleteParties` | all | `ImpleadedPartyIncompleteException` | **no transition at all proceeds while an impleaded party lacks mandatory details** |
| — | *nothing above has written a single field* | | | |
| 6 | `resolveAssignedRole` | `__NEXT__` / `__PREVIOUS__` | — | ladder sentinel expanded |
| 7 | `applySideEffects` | `SIDE_EFFECT` | award cap, RE deadline, maintainability, partyName, regulatoryBody | see §K |
| 8 | status / stage write | **skipped for `FX_APPROVE_LADDER`** | — | — |
| 9 | milestone write | all | — | independent of status |
| 10 | `applyAssignee` | `ASSIGN_STRATEGY` | **422** on `@P` inactive / no-history | owner set |
| 11 | closureCause | `FX_CLOSURE_FROM_PARAM` | — | from param |
| 12 | `rbioSlaService.applyStageSla` | all | — | business-day due date |
| 13 | `applyClosureCommunication` | closing actions | `assertRoleMayUseClause`; unparseable `dateOfSending` ⇒ **refuse** | clause persisted; `dateOfSending` **write-once**; `customClosureText` ≤ 2000; email + SMS queued **idempotently per (complaint, channel)** |

**Post-commit, unconditional:**
```
   save → recordAssignment (EVERY action, even no owner change)
        → addDetailedTimeline(prev→new, owner?, closureClause, destinationOffice, MANUAL)
        → auditService.logActionAsync
        → triggerRbioNotifications
```
★ `recordAssignment` runs even when the owner did not change, because a gap in the custody chain
silently degrades a later `SEND_BACK_*` (`@P`) into a manual officer pick.

---

## §K — MATRIX 10 · Side effect × what it may refuse

| SIDE_EFFECT | Writes | Can refuse? |
|---|---|---|
| `FX_RESOLVED_AT` / `FX_CLOSED_AT` / `FX_STAMP_ESCALATED` | one timestamp | no |
| `FX_CLOSURE_FROM_PARAM` | nothing here | no |
| `FX_APPROVE_LADDER` | status+stage from **landing rank**: CN→conciliation, AJ→adjudication, **else→escalated** ⚠ | no |
| `FX_MEETING_*` | append-only `RBIO_MEETING` + mirror `conciliation_date` | **503** if service absent ⚠ *(no-ops in unit tests)* |
| `FX_TRANSFER_REQUEST` | transfer request → CRPC Head approval queue | yes |
| `FX_FORWARD_DEPARTMENT` | department in its **own** column | yes |
| `FX_ADVISORY_TEXT` | advisoryText *(falls back to `remarks`)*, advisoryIssuedAt | no |
| **`FX_AWARD`** | amount, type, adjudicationDate, outcome, `awardPassedDate` | **yes — `validateAward` cap runs BEFORE any status write, so an over-cap award leaves the complaint untouched and UNSAVED** |
| `FX_ADJUDICATION_REJECT` | adjudicationDate, outcome=REJECTED | no |
| `FX_CONCILIATION_SUCCESS` | outcome + date | no |
| `FX_CONCILIATION_FAILED` | outcome; **date only when action == CONCILIATION_FAILED** ⚠ | no |
| `FX_NOTICE_13_1` | issuedAt, RE response deadline, targetParty | **yes — a bad date is refused, never substituted** |
| `FX_IMPLEAD` | CSV append + `IMPLEADED_PARTY` row | yes, missing partyName |
| `FX_REASSIGN_ROLE` | `assignedRole = params.targetRole` *(overrides the row)* | no |
| `FX_BULK_ROLE` | `assignedRole`, default `RBIO_OFFICER` | no |
| `FX_REOPEN` | nulls resolvedAt/closedAt, reopenCount++, returns file to **original** holder | **yes — authority/reason/justification validated FIRST; `RBIO_ADMIN` exempt from reason** |
| `FX_MAINTAINABILITY` | determination **from the ACTION** for DECIDE_* | 400 otherwise |
| `FX_DEPUTY_DECISION` | maintainability + decision | 400 unless both in-vocabulary |
| `FX_ADVISORY_COMPLIED` | advisoryCompliedAt | no |
| `FX_REGULATORY_BODY` | **canonical** name from the master + citizen awareness email | yes, unresolvable body |
| *unknown effect* | — | **no — logs a warning and COMMITS the transition anyway** ⚠ |

---

## §L — MATRIX 11 · Layer ownership (which declaration owns a cell)

The `OWNED_BY` column of `RBIO_WORKFLOW_TRANSITION`, and the resolution order that decides ties.

| Layer | `OWNED_BY` | Seeder `@Order` | Actions | Wins when… |
|---|---|---|---|---|
| **DB rows** | *(any)* | — | whatever is in the table | **always first** |
| Wave 0 | `WAVE0` | `RbioWorkflowTransitionSeeder` | 24 core | no DB row |
| S3 ladder | `S3` | `RbioLadderTransitionSeeder` @32 | 16 ladder/outcome | no DB row, not in Wave 0 |
| S3 notice | `S3` | `RbioNoticeDeadlineTransitionSeeder` @32 | `ISSUE_NOTICE_13_1` × 5 statuses for `DO` | insert-if-absent on the natural key |
| S5 | `S5` | `RbioMeetingTransferTransitionSeeder` | 5 meeting/forward | no DB row, not in Wave 0/S3 |
| *(fallback)* | — | — | **by ACTION ALONE, role ignored** | everything else failed ⚠ |

```
   resolveTransition:
     DB(action, role, active='Y')
       └─ miss ▶ RbioTransitionRegistry.resolve|effectOf
            └─ miss ▶ RbioLadderActions
                 └─ miss ▶ RbioMeetingTransferActions
                      └─ miss ▶ BY ACTION ALONE        ← erases the role dimension
```

⚠ `SCHEDULE_MEETING` is the one genuinely **contested** cell: declared by Wave 0 *and* S5. The
seeded S5 row wins at runtime; Wave 0's pinned unit tests still resolve their own compiled-in copy.
S5 avoided a split by attaching its validation to **Wave 0's effect name** (`FX_MEETING_DATE`) as
well as its own, so both entry paths validate.

★ Insert-if-absent on the natural key `(ACTION_CODE, ROLE_NAME, FROM_STATUS)` means a restart never
overwrites a row an operator adjusted. Correcting live behaviour is a deliberate `UPDATE` — and an
INSERT grants a capability while `IS_ACTIVE='N'` revokes it, **with no code release**. This table
is the intended administration surface.

---

## §M — What a spreadsheet of this must not lose

If these matrices are exported to Excel, five things break silently:

1. **`◐` is not `✔` and not `✖`.** A two-colour sheet will render RBIO as a fence it is not.
2. **`→=` is not a blank cell.** `TO_STATUS = NULL` means UNCHANGED; blank means "no transition".
3. **Role is a third axis.** Collapsing Matrix 4 into Matrices 1-3 is the privilege-creep hazard the
   `ADJUDICATION_AWARD`/`DyO` negative cell exists to catch.
4. **The NOT-IN guards must stay NOT-IN.** Expanding `REASSIGN`'s exclusion into positive rows means
   a status added next month is silently ungated — the exact trap the code documents twice.
5. **Guard *order* is load-bearing** (§J). Nothing writes a field before step 6, which is why an
   over-cap award or a bad notice date leaves the complaint entirely unmodified.

---

## §N — Matrix index

| # | Matrix | Axes | Enforced? |
|---|---|---|---|
| 1 | RBIO core (Wave 0) | status × 24 actions | role yes, state **no** |
| 2 | RBIO ladder (S3) | status × 6 ladder actions + outcome table | role yes, state declared |
| 3 | RBIO meetings/transfers (S5) | status × 5 actions | role yes, state declared, `BLOCKS_MEETING` enforced |
| 4 | RBIO authorisation | **role × action** | yes — unless `userRole` is blank |
| 5 | AA appellate | status × 15 actions | **role 403 + state 409** |
| 6 | CRPC intake | draft status × draft status | yes |
| 7 | RE activity | activity status × activity status | linear + auto OVERDUE |
| 8 | Citizen projection | internal status → 4 stages | read-only, lossy |
| 9 | Rules & conditions | guard × scope × refusal | ordered pipeline |
| 10 | Side effects | effect × writes/refusals | — |
| 11 | Layer ownership | layer × precedence | resolution order |
| — | **CEPC** | 24 actions × 10 roles | **neither — UI hint only** ⚠ |

CEPC is deliberately *not* given a matrix here: publishing one would imply a fence that
`CepcWorkflowService` does not implement. Its `ROLE_ACTIONS` and `isActionValidForState` build the
UI's button list and nothing more.
