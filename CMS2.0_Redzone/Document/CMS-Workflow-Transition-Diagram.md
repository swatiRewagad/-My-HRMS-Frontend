# CMS 2.0 — Workflow Transition Diagram (derived from code)

> **Provenance rule applied throughout: code is the truth.**
> Every state, role, action, guard and side effect below was read out of the Java/TypeScript
> sources in `CMS2.0_Redzone`. Where the code and the written documentation disagree, the code is
> recorded and the disagreement is called out in a `⚠` note. Nothing here is taken from a design
> doc, a backlog row, or prior session memory.
>
> Generated 2026-09-28. Primary sources cited inline as `file:symbol`.

---

## §0 — How to read this document

CMS 2.0 does not have *one* workflow. It has **five independent state machines** that share one
`COMPLAINT` row, plus a sixth read-only projection for the citizen. They were built by different
sessions, use different vocabularies for the same concept, and — critically — **enforce their
transition rules to different depths.**

```
                         ┌───────────────────────────────────────────────┐
                         │          ONE Complaint row / COMPLAINTS       │
                         │  status · workflowStage · milestone · officer │
                         └───────────────────────────────────────────────┘
                              ▲          ▲         ▲        ▲        ▲
              ┌───────────────┘          │         │        │        └────────────┐
              │                          │         │        │                     │
    ┌─────────┴────────┐      ┌──────────┴──┐  ┌───┴────┐  ┌┴──────────┐  ┌───────┴───────┐
    │  CRPC            │      │   CEPC      │  │  RBIO  │  │    AA     │  │   RE / PNO    │
    │  email intake    │      │  cell       │  │ombuds- │  │ appellate │  │ entity portal │
    │  (EmailDraft)    │      │             │  │ man    │  │ authority │  │ (activity)    │
    │  free-string map │      │ 24 actions  │  │ TABLE- │  │ 15 consts │  │ linear ladder │
    │  ENFORCES from   │      │ ADVERTISE   │  │ DRIVEN │  │ ENFORCES  │  │ + auto OVERDUE│
    │                  │      │ only        │  │        │  │ (409)     │  │               │
    └──────────────────┘      └─────────────┘  └────────┘  └───────────┘  └───────────────┘
                                          │
                                          ▼
                              ┌───────────────────────────┐
                              │ CitizenStageMapper        │
                              │ read-only 4-stage         │
                              │ projection, else → Stage 1│
                              └───────────────────────────┘
```

### §0.1 The single most important structural fact

**"What the UI offers" and "what the server accepts" are computed by different code, and in three of
the five domains they deliberately disagree.**

| Domain | Role check on execute? | From-status check on execute? | Refusal shape |
|---|---|---|---|
| **AA** | yes | **yes** | `409` + `availableActions` |
| **RBIO** | yes (DB-first) | **no** — `validForState` is advertisement only | `200 + success:false` (mostly) |
| **CEPC** | **no** per-action | **no** | varies |
| **CRPC** | yes | **yes** | exception |
| **RE** | yes | linear only | — |

Consequence: on RBIO you can POST an action from a status the UI would never have offered, and it
will execute. This is documented as deliberate in `RbioTransitionRegistry` (the `VALID_FROM` map is
named for advertisement), and it is the reason the matrix in the companion document has a separate
**ADV** vs **EXEC** column.

### §0.2 The four-field state, not one

```
   status        ── legacy lowercase string on Complaint.status   ("in_progress")
   workflowStage ── coarse engineering stage                      ("EXAMINATION")
   milestone     ── citizen/officer-facing checkpoint             ("ASSESSMENT")
   assignedRole  ── which ROLE holds the file  ─┐
   assignedOfficer ── which USER holds the file ┘ resolved by ASSIGN_STRATEGY
```

`TO_STATUS = NULL` **does not mean "unset"** — it means **UNCHANGED**. Ten RBIO actions rely on
this (every meeting action, `DECIDE_MAINTAINABLE`, both 13(1) notices). Source:
`RbioWorkflowService.applyTransition`, which only writes status when `toStatus != null`.

### §0.3 The RBIO resolution stack (why a cell can have two answers)

RBIO alone is **table-driven**. For any `(action, role, fromStatus)` the effect is resolved in this
order and the **first hit wins**:

```
  1. DB   RBIO_WORKFLOW_TRANSITION  WHERE action AND role AND isActive='Y'     ← authoritative
  2.      RbioTransitionRegistry     (Wave 0, compiled-in)      OWNED_BY = WAVE0
  3.      RbioLadderActions          (S3,     compiled-in)      OWNED_BY = S3
  4.      RbioMeetingTransferActions (S5,     compiled-in)      OWNED_BY = S5
  5.      by ACTION ALONE, ignoring role  ← drift-tolerant last resort
```

Source: `RbioWorkflowService.resolveTransition` / `.validateRoleAuthorization` / `.validForState`.
The three compiled-in classes are **additive, never edited** — each session seeded its own rows via
its own `@Order(3x)` `CommandLineRunner` so it could not disturb another session's pinned tests.
Seeders: `RbioWorkflowTransitionSeeder`, `RbioLadderTransitionSeeder`,
`RbioMeetingTransferTransitionSeeder`, `RbioNoticeDeadlineTransitionSeeder`.

⚠ `SCHEDULE_MEETING` is declared **twice** — Wave 0 (no required params) and S5 (mandatory date).
At runtime the seeded S5 row wins; Wave 0's unit tests still see Wave 0's. The stricter
declaration is reached from *both* paths because S5 attached its validation to **Wave 0's effect
name** `FX_MEETING_DATE` as well as its own.

### §0.4 Step 5 is a real hole, not a nicety

Because `resolveTransition` falls back to *action alone*, and because `performAction` **skips the
role check entirely when `userRole` is blank**:

```java
if (!userRole.isBlank() && !validateRoleAuthorization(action, userRole)) throw ...
```

…a caller that sends **no role at all** executes any RBIO action it can name. Source:
`RbioWorkflowService.performAction`.

---

## §1 — Role inventory (the actors)

### §1.1 RBIO — two ladders, deliberately

`RbioRoles` holds **two** seniority ladders and inverts them for send-backs rather than keeping a
third map.

```
   CORRECTED "RANK" LADDER  (current vocabulary)
   ──────────────────────────────────────────────────────────────────────
   RBIO_DEALING_OFFICIAL ──▶ RBIO_REVIEWER ──▶ RBIO_DEPUTY_OMBUDSMAN ──▶ RBIO_OMBUDSMAN
        (DO)                  (RV)               (DyO)                     (OM)
        ◀── previousRank is the INVERSE of the same map, rank checked first

   PRESERVED "LEGACY" LADDER  (older rows, still live)
   ──────────────────────────────────────────────────────────────────────
   RBIO_OFFICER ──▶ RBIO_SUPERVISOR ──▶ RBIO_CONCILIATOR ──▶ RBIO_ADJUDICATOR

   nextRank(null) == RBIO_OFFICER          ← the default entry point
   CASE_HOLDING_ROLES = 8 roles above      ← RBIO_ADMIN is EXCLUDED (never holds a file)
```

Sentinels `__NEXT__` / `__PREVIOUS__` appear in `ASSIGN_TO_ROLE` and are expanded at execution.

### §1.2 Role → action grants, RBIO Wave 0 (`RbioTransitionRegistry.ROLE_ACTIONS`)

```
 OFFICER  (= DEALING_OFFICIAL, aliased)
   ACCEPT · TAKE_ACTION · RESOLVE · REJECT · ESCALATE · REQUEST_INFO
   SCHEDULE_MEETING · FORWARD_TO_CONCILIATION · ISSUE_ADVISORY

 SUPERVISOR  (= REVIEWER, aliased)
   APPROVE · RETURN_TO_OFFICER · RESOLVE · ESCALATE · FORWARD_TO_ADJUDICATION
   FORWARD_TO_CONCILIATION · REASSIGN · ISSUE_ADVISORY
   ⚠ SCHEDULE_MEETING absent here — S5 adds it for REVIEWER (UST643)

 CONCILIATOR
   CONCILIATION_SUCCESS · CONCILIATION_FAILED · SCHEDULE_MEETING · ESCALATE_TO_ADJUDICATION

 ADJUDICATOR
   ADJUDICATION_AWARD · ADJUDICATION_REJECT · ISSUE_NOTICE_13_1 · IMPLEAD_PARTY

 DEPUTY_OMBUDSMAN
   APPROVE · RETURN_TO_OFFICER · RESOLVE · ESCALATE · REASSIGN · ISSUE_ADVISORY
   FORWARD_TO_CONCILIATION · FORWARD_TO_ADJUDICATION · SCHEDULE_MEETING
   ★ ADJUDICATION_AWARD is DELIBERATELY WITHHELD — only the Ombudsman awards

 OMBUDSMAN
   = DEPUTY_OMBUDSMAN's set  PLUS
   REJECT · ADJUDICATION_AWARD · ADJUDICATION_REJECT · ISSUE_NOTICE_13_1
   IMPLEAD_PARTY · CLOSE_COMPLAINT · REOPEN

 ADMIN
   REASSIGN · ESCALATE · CLOSE_COMPLAINT · REOPEN · BULK_ASSIGN
   (holds no file; REASSIGN/BULK_ASSIGN are NOT-IN guarded, see §4.7)
```

### §1.3 Other domains

| Domain | Roles (from code) |
|---|---|
| CEPC | `CEPC_DO`, `CEPC_REVIEWER`, `CEPC_INCHARGE`, `CEPC_CLOSING_AUTHORITY`, `CEPC_ADMIN`, `CEPC_OFFICER`, `CEPC_SUPERVISOR`, `CEPC_CONCILIATOR`, `CEPC_ADJUDICATOR`, `CEPC_CONTACT_PERSON` |
| CRPC | `DEO`, `REVIEWER`, `CRPC_HEAD`, `CRPC_INCHARGE`, `CRPC_ADMIN` |
| AA | `AA_DO`, `AA_REVIEWER` (tiered via `reviewer_tier` attr), `AA_SECRETARIAT`, `AA_ADMIN` |
| RE | `RE_PNO` (scoped by `entity_code` attribute) |
| Global | `ADMIN`, `SUPER_ADMIN`, `OFFICER`, `REVIEWER`, `TOLL_FREE_HELPDESK` |

⚠ Authorisation is enforced by **AOP aspects** (`@RbioRoleGuard`, `@CepcRoleGuard`,
`@AaRoleGuard`), *not* by Spring method security. `@PreAuthorize` annotations in this tree are
**inert** — `@EnableMethodSecurity` is not present.

---

## §2 — The RBIO state vocabulary

From `RbioStatusMasterSeeder` (seeds `RBIO_STATUS_MASTER`) and `RbioStatusVocabulary`.

Three row *kinds* live in that one table: **SCOPE** (queue filters), **QUEUE** (send-to/send-back
buckets) and **STATUS** (real complaint states).

```
 STATUS_CODE            LEGACY_VALUE         MILESTONE        CLOSED  CITIZEN
 ──────────────────────────────────────────────────────────────────────────────
 NEW_COMPLAINT          pending              REGISTER           ·       Y
 ASSIGNED               assigned             REGISTER           ·       Y
 IN_PROGRESS            in_progress          ASSESSMENT         ·       Y
 INFO_REQUESTED         info_requested       ASSESSMENT         ·       Y
 MEETING_SCHEDULED      (null)               CONCILIATION       ·       Y
 ESCALATED              escalated            ASSESSMENT         ·       N   ← hidden
 CONCILIATION           conciliation         CONCILIATION       ·       Y
 ADJUDICATION           adjudication         FINAL_DECISION     ·       Y
 SENT_TO_OTHER_OFFICE   sent_to_other        FORWARD            ·       Y
 SENT_TO_OTHER_DEPT     forwarded_external   FORWARD            ·       Y
 SENT_TO_OTHER_RE       (null)               FORWARD            ·       Y
 ADVISORY_COMPLIED      advisory_issued      FINAL_DECISION     ·       Y
 FACILITATION           (null)               —                  ·       N   ← hidden
 NOT_A_COMPLAINT        (null)               REGISTER           ·       Y
 DY_OMB_DECISION        (null)               —                  ·       Y
 OMBUDSMAN_DECISION     (null)               —                  ·       Y
 ── closed set ───────────────────────────────────────────────────────────────
 AWARD_PASSED           adjudicated          FINAL_DECISION     Y       Y
 SETTLED                conciliated          FINAL_DECISION     Y       Y
 RESOLVED               resolved             FINAL_DECISION     Y       Y
 REJECTED               rejected             FINAL_DECISION     Y       Y
 WITHDRAWN              withdrawn            FINAL_DECISION     Y  TERMINAL
 CLOSED                 closed               FINAL_DECISION     Y       Y
 APPEAL_CLOSED          (null)               —                  Y  TERMINAL
 ── and the trap ─────────────────────────────────────────────────────────────
 REOPENED               (null)               ASSESSMENT         ·       Y
        ★ REOPENED is an OPEN state despite the name
```

Fallback when the table is empty (`RbioStatusVocabulary.LEGACY_CLOSED`):

```
   resolved · closed · rejected · withdrawn · adjudicated · conciliated
```
`closedStatuses()` re-reads the table **on every call** and only falls back to these six.

### §2.1 Milestone lattice

```
   REGISTER ──▶ ASSESSMENT ──▶ CONCILIATION ──▶ FINAL_DECISION
                     │                              ▲
                     └──────────▶ FORWARD ──────────┘
```
Milestone is set independently of status, so a status-preserving action (`TO_STATUS=NULL`) can
still move the milestone. `SCHEDULE_MEETING` does exactly that: stage → `MEETING_SCHEDULED`,
milestone → `CONCILIATION`, status **unchanged**.

### §2.2 Per-role queue visibility (`RbioStatusMasterSeeder.seedVisibility`)

```
 SCOPES:  ALL · ASSIGNED_TO_ME · CREATED_BY_ME
 QUEUES:  SENT_TO_DO      SENT_TO_REVIEWER      SENT_TO_DY_OMB   SENT_TO_OMBUDSMAN
          SENT_BACK_DO    SENT_BACK_REVIEWER    SENT_BACK_DY_OMB
          (all seven isCitizenVisible = N)

 ROLE                  DEFAULT FILTER
 ─────────────────────────────────────────
 RBIO_ADMIN            ALL (everything)
 DEALING_OFFICIAL      ASSIGNED_TO_ME
 REVIEWER              SENT_TO_REVIEWER
 DEPUTY_OMBUDSMAN      SENT_TO_DY_OMB
 OMBUDSMAN             SENT_TO_OMBUDSMAN
 legacy OFFICER / SUPERVISOR / CONCILIATOR / ADJUDICATOR — working lists, no queue default
```

---

## §3 — RBIO: the main flow

### §3.1 Intake → assignment → assessment

```
                     complaint created (portal / CRPC approval / email)
                                        │
                                   status = pending
                                   milestone = REGISTER
                                        │
                        ┌───────────────┴────────────────┐
                        │  assignment (round-robin /     │
                        │  office-capacity routing)      │
                        └───────────────┬────────────────┘
                                        ▼
                                  status = assigned
                                  assignedRole = DEALING_OFFICIAL
                                        │
                 ┌──────────────────────┼──────────────────────┐
                 │ ACCEPT               │ TAKE_ACTION          │ (either)
                 │ assign ACTOR         │ assign ACTOR         │
                 │ sla OFFICER_ASSESSMENT                      │
                 ▼                      ▼                      ▼
                        status = in_progress · stage = EXAMINATION
                                  milestone = ASSESSMENT
```

### §3.2 The four-rung review ladder (S3, `RbioLadderActions`)

This is the spine of the RBIO workflow. **Up** the ladder is a forward; **down** is a send-back,
and every send-back **requires a comment** and returns the file to the `PREVIOUS_HOLDER`.

```
                         ┌───────────────────────────────────────┐
                         │             OMBUDSMAN                 │
                         │      status = ombudsman_review        │
                         │      milestone = FINAL_DECISION       │
                         └──▲──────────────┬───────────┬─────────┘
       FORWARD_TO_OMBUDSMAN │              │           │ SEND_BACK_REVIEWER
       from{deputy_review,  │              │           │ SEND_BACK_DO
            reviewer_review,│  SEND_BACK_DEPUTY        │ (comment required)
            in_progress}    │  (comment required)     │
       roles{DyO, RV}       │  ▼                      │
                         ┌──┴──────────────────────┐  │
                         │    DEPUTY OMBUDSMAN     │  │
                         │  status = deputy_review │  │
                         │  milestone = ASSESSMENT │  │
                         └──▲──────────────┬───────┘  │
    FORWARD_TO_DEPUTY_OMB.. │              │          │
    from{reviewer_review,   │  SEND_BACK_REVIEWER     │
         in_progress}       │  (comment required)     │
    roles{RV, SUPERVISOR}   │  ▼                      │
                         ┌──┴─────────────────────────┴───────┐
                         │             REVIEWER                │
                         │     status = reviewer_review        │
                         │     stage  = REVIEWER_REVIEW        │
                         │     milestone = ASSESSMENT          │
                         └──▲──────────────────────────┬───────┘
       SUBMIT_FOR_REVIEW    │                          │
       from{in_progress,    │        SEND_BACK_DO      │
            assigned,       │        (comment required,│
            returned}       │         PREVIOUS_HOLDER) │
       roles{DO, OFFICER}   │                          ▼
       assign REVIEWER      │            ┌──────────────────────────┐
             ROUND_ROBIN    └────────────┤  DEALING OFFICIAL        │
                                         │  status = returned       │
                                         │  (or in_progress)        │
                                         └──────────────────────────┘
```

`SEND_BACK_INCHARGE` is an **alias** of `SEND_BACK_DEPUTY` carrying CEPC's vocabulary, so the same
row serves both UIs.

#### The `PREVIOUS_HOLDER` resolution and its two distinct refusals

```
  PREVIOUS_HOLDER:
     explicit targetUser param  ──▶ wins outright
     else previousHolderFor(complaint, role)
              ├─ found & active      ──▶ assign
              ├─ found but INACTIVE  ──▶ 422 "…no longer active…"
              └─ no custody row      ──▶ 422 "…no previous holder…"
```
`422` not `400`, deliberately: the UI must distinguish "your request was malformed" from "now pick
an officer yourself". Source: `RbioWorkflowService.applyAssignee`.

★ This is why `performAction` calls `recordAssignment` **after save, on EVERY action, even when the
owner did not change** — a gap in the custody chain silently degrades a later send-back into a
manual pick.

### §3.3 The legacy `APPROVE` ladder (Wave 0) — a status computed from the *landing rank*

`APPROVE` declares `toStatus = null`, `toStage = null`, `toMilestone = null` and defers everything
to the side effect `FX_APPROVE_LADDER`. The status/stage write in `applyTransition` is **skipped**
for this action.

```
   APPROVE  (roles: SUPERVISOR, DEPUTY_OMBUDSMAN, OMBUDSMAN)
   from{in_progress}          assign __NEXT__ · ROUND_ROBIN
        │
        ├─ landed on CONCILIATOR  ──▶ status=conciliation  stage=CONCILIATION  +SLA CONCILIATION
        ├─ landed on ADJUDICATOR  ──▶ status=adjudication  stage=ADJUDICATION  +SLA ADJUDICATION
        └─ anything else          ──▶ status=escalated     stage=ESCALATED     (no SLA)
```
⚠ Reproduced verbatim from the pre-table implementation, **including** that a landing outside
conciliation/adjudication yields `escalated` rather than any kind of `approved`.

### §3.4 Conciliation and adjudication

```
   ┌────────────────────── status = conciliation ──────────────────────┐
   │ roles: CONCILIATOR (+ meeting roles)                              │
   │                                                                   │
   │  CONCILIATION_SUCCESS ─▶ conciliated  FX_CONCILIATION_SUCCESS     │
   │                           outcome=SUCCESS, conciliationDate=now   │
   │  CONCILIATION_FAILED  ─▶ (per row)    FX_CONCILIATION_FAILED      │
   │                           outcome=FAILED, conciliationDate=now    │
   │  ESCALATE_TO_ADJUDICATION ─▶ adjudication                         │
   │                           outcome=FAILED, date NOT set  ⚠         │
   └───────────────────────────────────────────────────────────────────┘
        ⚠ the asymmetry is intentional & reproduced: ESCALATE_TO_ADJUDICATION
          sets the outcome but NOT the date; CONCILIATION_FAILED sets both.

   ┌────────────────────── status = adjudication ──────────────────────┐
   │ roles: ADJUDICATOR, OMBUDSMAN  (NOT Deputy Ombudsman)             │
   │                                                                   │
   │  ADJUDICATION_AWARD  require awardAmount|compensationAmount       │
   │       ─▶ adjudicated / AWARD_ISSUED / FINAL_DECISION  TERMINAL-ish │
   │       FX_AWARD:                                                   │
   │          parse amount → NumberFormatException ⇒ refuse            │
   │          rbioCompensationService.validateAward(amount, type)      │
   │            ★ CAP CHECKED BEFORE ANY STATUS WRITE — an over-cap    │
   │              award leaves the complaint untouched and UNSAVED     │
   │          then awardAmount, compensationType, adjudicationDate,    │
   │               adjudicationOutcome=AWARD_ISSUED, awardPassedDate   │
   │  ADJUDICATION_REJECT ─▶ FX_ADJUDICATION_REJECT                    │
   │          adjudicationDate=now, outcome=REJECTED                   │
   │  ISSUE_NOTICE_13_1  (see §3.6)                                    │
   │  IMPLEAD_PARTY      (see §3.7)                                    │
   └───────────────────────────────────────────────────────────────────┘
```
`awardPassedDate` exists separately because `adjudicationDate` is shared with *rejection* and so
cannot answer "when was an award passed" (UST543).

### §3.5 Meetings (S5) — status-preserving by design

```
   MEETING_FROM = { in_progress, assigned, conciliation, escalated, returned,
                    reviewer_review, deputy_review, ombudsman_review }
   MEETING_ROLES = { DEALING_OFFICIAL, REVIEWER, DEPUTY_OMBUDSMAN, OMBUDSMAN,
                     OFFICER, SUPERVISOR, CONCILIATOR }

   SCHEDULE_MEETING     toStatus=NULL  stage=MEETING_SCHEDULED    milestone=CONCILIATION
        require meetingDate|hearingDate
   RESCHEDULE_MEETING   toStatus=NULL  stage=MEETING_RESCHEDULED  milestone=CONCILIATION
        require meetingDate|hearingDate  AND  rescheduleReason|reason
   COMPLETE_MEETING     toStatus=NULL  stage=MEETING_COMPLETED    milestone=CONCILIATION
        require entityAccepted|entityAcceptance  AND  minutesOfMeeting|mom
```
All three keep the status untouched — that is precisely what satisfies "a reschedule must not move
the complaint's overall workflow status".

**The six-status exclusion is NOT a from-status list.** Enumerating "every status except six" as
positive rows would silently omit any status a later session adds. Instead:

```
   RBIO_STATUS_MASTER.BLOCKS_MEETING = 'Y'
        ▲ enforced on EXECUTION by RbioMeetingService.assertMeetingAllowedForStatus
        ▲ not merely on advertisement
```

Meeting side effects delegate to `recordMeetingEvent` → append-only `RBIO_MEETING`, and mirror the
operative date onto `conciliation_date` for existing readers.

⚠ `recordMeetingEvent` **returns silently** when `rbioMeetingService` is null (unit-test
construction only) so Wave 0's pinned "stage only" test still passes. In production the bean always
exists, so the mandatory-field rules cannot be bypassed — but the branch is a latent
silent-success if that ever changes.

### §3.6 Notices and the response clock

```
   ISSUE_NOTICE_13_1 / ISSUE_13_1_NOTICE (alias)   toStatus = NULL
   ───────────────────────────────────────────────────────────────────────
   Wave 0 grant:  ADJUDICATOR, OMBUDSMAN     from{ adjudication }
   S3   grant:    DEALING_OFFICIAL           from{ in_progress, info_requested,
                                                  sent_to_do, sent_back_do, reopened }
                  stage=NOTICE_13_1_ISSUED  milestone=ASSESSMENT
                  no comment · no approval · no assignee change

   FX_NOTICE_13_1:
      notice131IssuedAt = now
      ReResponseDeadlineService.setDeadline(complaint, chosenDate|configured, "13(1) Notice")
           └─ NOT accepted ⇒ IllegalArgumentException   ← refuse, never substitute a date
      notice131TargetParty = targetParty|noticeTargetParty
```
The S3 grant spans five statuses because "Assessment" is a **milestone, not a status** — granting
only `in_progress` would make the action vanish the moment the officer requested information from
the entity, i.e. exactly when a 13(1) notice becomes relevant. `ESCALATED` is excluded: an
escalated file is with a senior officer, so the DO is no longer the correspondent.

### §3.7 Impleading

```
   IMPLEAD_PARTY  roles{ADJUDICATOR, OMBUDSMAN}  from{adjudication}
        require partyName  (alias impleadPartyName accepted)
   FX_IMPLEAD:
        impleadedParties CSV  ← appended (kept authoritative for six other sessions)
        IMPLEADED_PARTY row   ← authoritative for per-party facts (name, type, remarks, actor)
```
And the **gate**, applied in `applyTransition` *before* any write:
```
   impleadService.findIncompleteParties(complaint)
        non-empty ⇒ ImpleadedPartyIncompleteException     (UST546)
```
So no transition at all proceeds while an impleaded party is missing mandatory details.

### §3.8 Forwarding out

```
   FORWARD_TO_OTHER_OFFICE   ─▶ status = sent_to_other   ★ NOT forwarded_external
        stage=SENT_TO_OTHER_OFFICE  milestone=FORWARD
        require targetOffice|toOffice  AND  transferReason|reason  · comment required
        ★ NO ASSIGN STRATEGY — the destination officer is deliberately NOT chosen yet
        FX_TRANSFER_REQUEST → requestOfficeTransfer(): enters the CRPC Head APPROVAL queue
        roles{DO, RV, DyO, OM, OFFICER, SUPERVISOR}

   FORWARD_TO_OTHER_RBI_DEPT ─▶ status = forwarded_external
        stage=SENT_TO_OTHER_DEPT  milestone=FORWARD
        require targetDepartment|targetDept · comment required
        FX_FORWARD_DEPARTMENT → forwardToDepartment(): department in its OWN column

   FORWARD_TO_REGULATORY_BODY ─▶ status = forwarded_external          (S3)
        require regulatoryBodyName|regulatoryBodyId
        FX_REGULATORY_BODY:
           forwardTargetService.resolveRegulatoryBodyName(body)
                ← resolved against the VALIDATED master, not merely checked, so the
                  recorded name is canonical and not the caller's spelling
           queueRegulatoryBodyAwarenessEmail(complaint, body)
                ← the citizen is now actually told their complaint left RBI's jurisdiction
```
⚠ The bug this fixed: CEPC's arm wrote `forwarded_external` for an office transfer, and
`sent_to_other` is the legacy value the status master declares for `SENT_TO_OTHER_OFFICE` — so
transferred complaints **never appeared in the CRPC Head's queue**. Separately, the CEPC external
forwards write the organisation's name **into `assignedOfficer`**, leaving a non-user string in a
user column; the RBIO arms use dedicated columns instead.

### §3.9 Maintainability (S3)

```
   DECIDE_MAINTAINABLE       toStatus = NULL (unchanged)  stage=MAINTAINABILITY_DECIDED
        comment required · 6 roles
   DECIDE_NON_MAINTAINABLE   ─▶ closed   require closureClause · comment · TERMINAL
        roles{DEPUTY_OMBUDSMAN, OMBUDSMAN, SUPERVISOR}
   DEPUTY_OMBUDSMAN_DECISION require maintainability AND decision · comment

   FX_MAINTAINABILITY — the determination is taken from the ACTION, not a param, for the two
   DECIDE_* actions, so a contradicting param cannot record the opposite of the act performed.
   DEPUTY_OMBUDSMAN_DECISION is the exception: one action expresses both outcomes, so it reads
   the param, and refuses anything that is not MAINTAINABLE|NON_MAINTAINABLE (400).
   FX_DEPUTY_DECISION additionally requires decision ∈ {FACILITATION, REJECTION} (400).
```

### §3.10 Closure paths

```
   RESOLVE                 ─▶ resolved     closure RESOLVED         FX_CLOSURE_FROM_PARAM
   REJECT                  ─▶ rejected     closure REJECTED         terminal
   ADJUDICATION_AWARD      ─▶ adjudicated  closure ADJUDICATION_AWARD
   CONCILIATION_SUCCESS    ─▶ conciliated
   FACILITATION / SETTLED  ─▶ resolved     comment required         (S3)
   WITHDRAWN               ─▶ withdrawn    terminal                 (S3)
   NOT_A_COMPLAINT         ─▶ closed       require closureClause · terminal
   ADVISORY_COMPLIED       ─▶ closed       from{advisory_issued} ONLY · terminal
   DECIDE_NON_MAINTAINABLE ─▶ closed       require closureClause · terminal
   CLOSE_COMPLAINT         ─▶ closed       closure ADMIN_CLOSED · terminal
                                           NOT-IN { closed }
```

Every closure runs the **closure-communication chain**:

```
   applyTransition
     ├─ closureCommunicationGuardService.assertClosureCommunicationComplete(...)   ← BEFORE writes
     └─ applyClosureCommunication
          ├─ closureClauseAccessService.assertRoleMayUseClause(role, clause)   (UST584)
          ├─ setClosureClause(clause)
          │     ★ the RBIO path NEVER persisted the clause before this.
          │       Consequence: appeals against RBIO closures failed closed with
          │       appeal.error_clause_not_configured.
          ├─ customClosureText  max 2000 chars
          ├─ dateOfSending      WRITE-ONCE; unparseable ⇒ REFUSE (never silently dropped)
          └─ queueClosureCommunications  — idempotent per (complaint, channel):
                   email letter  +  SMS
```

### §3.11 Reopen — the only action that *undoes*

```
   REOPEN   roles{ OMBUDSMAN, ADMIN }
            from{ closed, resolved, adjudicated, conciliated }  (advertisement)
   FX_REOPEN, in this order — validation FIRST, so a refused reopen leaves the closure intact:

     1. requireReopenAuthority(params)        ⇒ reopenedByRole
     2. reasonMandatory = (role != RBIO_ADMIN)
        reasonSupplied  = reopenReason|reason present
        if (reasonMandatory || reasonSupplied):
              requireReopenReason(...)                    ⇒ else refuse
              reopenJustification|justification required  ⇒ else 400
     3. resolvedAt = null · closedAt = null
        reopenCount++ · lastReopenedAt = now · reopenedAt = now
     4. return the file to the ORIGINAL processing official:
              assignmentHistoryService.originalHolderFor(complaintNumber, DEALING_OFFICIAL)
                  └─ not assignable? retry under legacy OFFICER  ← old files were held there
              ★ EARLIEST custody row for the dealing rank, not the most recent
        timeline, attachments and closure clause are untouched
```
⚠ The asymmetry is deliberate: the **Ombudsman's** reopen reverses a concluded statutory
proceeding and must say on whose authority and why. **`RBIO_ADMIN`'s** reopen is an administrative
correction of a mis-closure that predates the requirement and has no caller supplying a reason —
demanding one would break every existing administrative reopen. When an admin *does* supply one it
is still validated and recorded.

---

## §4 — RBIO: the execution pipeline

### §4.1 `performAction` — the whole path, in order

```
 performAction(complaintNumber, action, params)                       @Transactional
 ────────────────────────────────────────────────────────────────────────────────────
  1  load Complaint by complaintNumber
  2  capture previousStatus, previousStage, previousOfficer   ★ BEFORE any mutation
  3  if (!userRole.isBlank() && !validateRoleAuthorization(action, userRole))
           throw IllegalArgumentException            ← blank role SKIPS the check entirely
  4  transition = resolveTransition(action.toUpperCase(), userRole)
           null ⇒ IllegalArgumentException("Unknown RBIO action: …")
  5  applyTransition(complaint, transition, params)        ← see §4.2
  6  save
  7  recordAssignment(...)                  ★ AFTER save, on EVERY action, even no-owner-change
  8  addDetailedTimeline(prev→new, ownerChanged ? assignedOfficer : null,
                         params.closureClause, params.destinationOffice, MANUAL)
  9  auditService.logActionAsync(...)
 10  triggerRbioNotifications(...)
 11  return { complaintNumber, action, newStatus, assignedRole, assignedOfficer }
```

### §4.2 `applyTransition` — the guard order is load-bearing

```
  1  requireParams(transition, params)                     ← REQUIRED_PARAMS
         comma = AND-separated groups
         pipe  = "at least one of" within a group
         special message for awardAmount
         ★ S5 meeting/forward actions throw ResponseStatusException(400) with i18n keys
           (rbio.meeting.error.date_required, rbio.forward.error.office_required, …)
           BECAUSE IllegalArgumentException surfaces as HTTP 200 (see §7)
  2  requireComment(transition, params)                    ← REQUIRES_COMMENT = 'Y'
         accepts remarks OR comments · ResponseStatusException(BAD_REQUEST)
  3  uploadLinkGuardService.assertActionAllowed(...)       (UST603-604)
  4  closureCommunicationGuardService.assertClosureCommunicationComplete(...)
  5  impleadService.findIncompleteParties(...)  non-empty ⇒ ImpleadedPartyIncompleteException
  ── nothing above has written a single field ───────────────────────────────────────
  6  resolveAssignedRole(...)      __NEXT__ / __PREVIOUS__ expanded here
  7  applySideEffects(...)         ← may still refuse (award cap, deadline, maintainability)
  8  status / stage                ★ SKIPPED when FX_APPROVE_LADDER owns them
  9  milestone
 10  applyAssignee(...)            ACTOR | TARGET_PARAM | ROUND_ROBIN | PREVIOUS_HOLDER
 11  closureCause                  from param when FX_CLOSURE_FROM_PARAM
 12  rbioSlaService.applyStageSla(...)
 13  applyClosureCommunication(...)
```

### §4.3 Assignment strategies

```
 ACTOR           ─▶ the acting user                       (ACCEPT, TAKE_ACTION)
 TARGET_PARAM    ─▶ params targetUser | targetUserId      (REASSIGN)
 ROUND_ROBIN     ─▶ assignByRole(role)                    (SUBMIT_FOR_REVIEW, APPROVE, ESCALATE)
                    null result LEAVES the existing officer in place
 PREVIOUS_HOLDER ─▶ explicit target wins, else previousHolderFor  ⇒ 422 on inactive / no history
 (none)          ─▶ owner unchanged                       (FORWARD_TO_OTHER_OFFICE)
```

Durable round-robin uses a DB pointer with `SELECT … FOR UPDATE`; ⚠ **four in-memory
`ConcurrentHashMap` rotas survive elsewhere** in the tree and do not survive a restart or a second
pod.

### §4.4 Complete side-effect catalogue

| Effect | What it writes / refuses |
|---|---|
| `FX_RESOLVED_AT` | `resolvedAt = now` |
| `FX_CLOSED_AT` | `closedAt = now` |
| `FX_STAMP_ESCALATED` | `escalatedAt = now` |
| `FX_CLOSURE_FROM_PARAM` | no-op here; read in `applyTransition` step 11 |
| `FX_APPROVE_LADDER` | status/stage from landing rank (§3.3); owns steps 8 |
| `FX_MEETING_DATE`, `FX_MEETING_SCHEDULED` | `recordMeetingEvent(SCHEDULED)` |
| `FX_MEETING_RESCHEDULED` / `FX_MEETING_COMPLETED` | `recordMeetingEvent(…)` |
| `FX_TRANSFER_REQUEST` | `requestOfficeTransfer` → CRPC Head approval queue |
| `FX_FORWARD_DEPARTMENT` | `forwardToDepartment` → own column |
| `FX_ADVISORY_TEXT` | `advisoryText` (falls back to `remarks`), `advisoryIssuedAt` |
| `FX_AWARD` | parse → **cap validation before any write** → amount, type, adjudicationDate, outcome=AWARD_ISSUED, awardPassedDate |
| `FX_ADJUDICATION_REJECT` | adjudicationDate, outcome=REJECTED |
| `FX_CONCILIATION_SUCCESS` | outcome=SUCCESS + date |
| `FX_CONCILIATION_FAILED` | outcome=FAILED; date **only** when action == CONCILIATION_FAILED |
| `FX_NOTICE_13_1` | issuedAt, RE response deadline (refuses a bad date), targetParty |
| `FX_IMPLEAD` | CSV append + `IMPLEADED_PARTY` row; refuses missing partyName |
| `FX_REASSIGN_ROLE` | `assignedRole = params.targetRole` (overrides the row) |
| `FX_BULK_ROLE` | `assignedRole = params.targetRole` default `RBIO_OFFICER` |
| `FX_REOPEN` | the eight steps in §3.11 |
| `FX_MAINTAINABILITY` | determination from ACTION for DECIDE_*; 400 otherwise |
| `FX_DEPUTY_DECISION` | maintainability + decision ∈ {FACILITATION, REJECTION}; 400 otherwise |
| `FX_ADVISORY_COMPLIED` | `advisoryCompliedAt = now` |
| `FX_REGULATORY_BODY` | resolve against master, persist canonical name, queue citizen awareness email |
| *unknown* | `log.warn("Unknown RBIO side effect …")` — **executes the transition anyway** ⚠ |

### §4.5 The NOT-IN guards (`EXCLUDED_FROM`)

Expressed as exclusions, never as positive from-status lists, precisely so a status added by a
later session is covered automatically:

```
 REASSIGN      NOT IN { closed, resolved, rejected, withdrawn, adjudicated, conciliated }
 BULK_ASSIGN   NOT IN { closed, resolved, rejected, withdrawn, adjudicated, conciliated }
 CLOSE_COMPLAINT NOT IN { closed }
 SCHEDULE/RESCHEDULE/COMPLETE_MEETING  ← RBIO_STATUS_MASTER.BLOCKS_MEETING = 'Y'
```

---

## §5 — CRPC: email-draft intake (the only *strict* string machine)

`CrpcWorkflowService` holds a plain `Map<String, Set<String>>` over `EmailDraftStatus` and
**enforces it on execution**.

```
        ┌──────────┐   SENT_FOR_APPROVAL   ┌────────────────────┐
        │ ASSIGNED  ├──────────────────────▶│ SENT_FOR_APPROVAL  │
        │  (DEO)    │                       │                    │
        └────┬──────┘◀──────────────────────┤  (REVIEWER /       │
             │          SENT_BACK           │   CRPC_HEAD)       │
             │                              └────────┬───────────┘
             │ NOT_A_COMPLAINT                       │ APPROVED
             │                                       ▼
             │                              ┌────────────────────┐
             │                              │     APPROVED       │
             │                              └────────┬───────────┘
             │                                       │ NEW_COMPLAINT
             ▼                                       ▼
        ┌──────────────────┐                ┌────────────────────┐
        │ NOT_A_COMPLAINT  │                │   NEW_COMPLAINT    │
        │   ✖ TERMINAL     │                │ → a real Complaint │
        └──────────────────┘                │   enters RBIO/CEPC │
                                            └────────────────────┘

   SENT_BACK ──▶ { SENT_FOR_APPROVAL, NOT_A_COMPLAINT }
```

This is the **handoff point**: `APPROVED → NEW_COMPLAINT` is what creates the `Complaint` that
every other state machine in this document then operates on.

---

## §6 — CEPC: 24 actions, advertisement-only rules

`CepcWorkflowService` (698 lines) declares `ROLE_ACTIONS` and `isActionValidForState`, and **both
are consulted only to build the UI's action list.** The execute path checks neither the
per-action role grant nor the from-status.

```
   CEPC_DO ──▶ CEPC_REVIEWER ──▶ CEPC_INCHARGE ──▶ CEPC_CLOSING_AUTHORITY
       ▲            │                 │                     │
       └────────────┴── send-back ────┴─────────────────────┘

   also: CEPC_CONCILIATOR · CEPC_ADJUDICATOR · CEPC_CONTACT_PERSON (entity-side)
         CEPC_ADMIN (reassign / close / reopen)
```

⚠ Two concrete defects in the CEPC arms, both fixed only on the RBIO side:
1. external forwards write the **organisation name into `assignedOfficer`** — a non-user string in
   a user column, so the complaint appears to be owned by an organisation;
2. an office transfer writes **`forwarded_external`** instead of `sent_to_other`, hiding it from
   the CRPC Head's queue.

`SEND_BACK_INCHARGE` in `RbioLadderActions` exists so CEPC's vocabulary reaches the RBIO table
without duplicating the row.

---

## §7 — AA: appellate authority (the only domain that enforces from-status)

### §7.1 States (`AppealStatus`)

```
        filed ──▶ under_review ──▶ hearing_scheduled ──▶ order_passed ✖
          │            │                  │                   │
          └────────────┴──────────────────┴──▶ rejected ✖     └──▶ closed ✖

   OPEN     = { filed, under_review, hearing_scheduled }
   TERMINAL = { closed, rejected, order_passed }
   ★ there is NO draft status — deliberately. "An unreachable status in the authoritative
     vocabulary is worse than an absent one."
```

### §7.2 The 15 transitions (`AaWorkflowTransition`)

Each constant carries **allowed roles + allowed-from statuses + to-status + Kafka event**, and
`AppealWorkflowService` checks all of them:

```
 ACCEPT · REJECT · ASSIGN_TO_BENCH · REQUEST_DOCUMENTS · PREPARE_BRIEF
 ESCALATE_TO_TIER2 · FORWARD_TO_AUTHORITY · SEND_BACK_REGISTRAR
 SCHEDULE_HEARING · PASS_ORDER · REMAND_TO_OMBUDSMAN · DISMISS
 REASSIGN · CLOSE · REOPEN
```

```
   filed ──ACCEPT──▶ under_review ──ASSIGN_TO_BENCH──▶ under_review
                          │
                          ├─REQUEST_DOCUMENTS──▶ (unchanged)
                          ├─PREPARE_BRIEF──────▶ (unchanged)
                          ├─ESCALATE_TO_TIER2──▶ reviewer_tier=2 bench
                          ├─SEND_BACK_REGISTRAR▶ back down the ladder
                          ├─SCHEDULE_HEARING───▶ hearing_scheduled
                          └─DISMISS / REJECT ──▶ rejected ✖
   hearing_scheduled ──PASS_ORDER──▶ order_passed ✖
                     ──REMAND_TO_OMBUDSMAN──▶ back to the RBIO domain
   any ──CLOSE──▶ closed ✖ ;  terminal ──REOPEN──▶ OPEN
```

### §7.3 Refusal semantics — the best in the codebase

```
   403  AaAccessDeniedException       role not granted the action
   409  AaIllegalTransitionException  from-status not allowed
             ★ carries availableActions so the UI can correct itself
   200 + success:false  IllegalArgumentException     ← the legacy shape, still reachable
```

Events publish to Kafka as `appeal.<pastParticiple>` through the **outbox** table
`OUTBOX_EVENT` (singular).

⚠ Appeals against **RBIO** closures used to fail with `appeal.error_clause_not_configured` because
the RBIO path never persisted `closureClause`. Fixed in `applyClosureCommunication` (§3.10) — but
historical rows closed before that remain unappealable.

---

## §8 — RE / PNO: the regulated-entity ladder

`ReActivityStatusService` — linear, with one automatic state.

```
   NOT_OPENED ──▶ OPENED ──▶ ┌ UNDER_REVIEW              ┐
                             └ RESPONSE_BEING_PREPARED   ┘ ──▶ DOCUMENTS_UPLOADED
                                                               ──▶ RESPONSE_SUBMITTED
                    │
                    └────────────────────── OVERDUE  ◀── AUTOMATIC, 15-minute sweep
```

`RE_PNO` users are scoped by the Keycloak `entity_code` attribute.
⚠ `REGULATED_ENTITIES` has no `entity_code` column and there is no per-officer RE column, so the
scoping is attribute-only.

---

## §9 — Citizen projection (read-only, and it hides failure)

`CitizenStageMapper` collapses every internal status into four stages:

```
 Stage 1  Registered        { pending, new }
 Stage 2  Under Review (RBI){ assigned, in_progress, under_review, escalated }
 Stage 3  With Bank         { approved, sent_back }
 Stage 4  Closed            { resolved, rejected, closed, withdrawn }

 ★ ANY OTHER STATUS → Stage 1
```

That default is the dangerous part: `conciliation`, `adjudication`, `adjudicated`, `conciliated`,
`reviewer_review`, `deputy_review`, `ombudsman_review`, `sent_to_other`, `forwarded_external`,
`advisory_issued`, `info_requested`, `reopened`, `returned` — **all of them display to the citizen
as "Registered".** A complaint that has been awarded compensation and closed under `adjudicated`
shows as Stage 1.

---

## §10 — Cross-cutting layers

### §10.1 Refusal semantics across the whole system

```
 IllegalArgumentException      ──▶ HTTP 200  { success: false, message }
        ★ Angular's HttpClient error branch NEVER FIRES for these.
          A refusal rendered as a success is the single most repeated defect class here.
 ResponseStatusException(400)  ──▶ real 400   (params, comment, maintainability, decision)
 ResponseStatusException(422)  ──▶ PREVIOUS_HOLDER inactive / no history
 AaAccessDeniedException       ──▶ 403
 AaIllegalTransitionException  ──▶ 409 + availableActions
 ImpleadedPartyIncompleteException ──▶ dedicated refusal
 503                           ──▶ meeting service unavailable
```
This is why S5's meeting and forward validations use `ResponseStatusException` while Wave 0's use
`IllegalArgumentException`: the two generations of code disagree, in the same class.

### §10.2 SLA

`rbioSlaService.applyStageSla(complaint, stage)` runs at step 12 of `applyTransition`, and again
inside `FX_APPROVE_LADDER` for the conciliation/adjudication landings. Business-day arithmetic via
`BusinessHoursService` + `HOLIDAYS`.

⚠ Two complaint tables exist: `COMPLAINT_MASTER` (uppercase columns) and `COMPLAINTS` (lowercase).
The SLA monitor reads the former, so **complaints in `COMPLAINTS` are invisible to it**.

### §10.3 Schedulers

16 `@Scheduled` classes. **6 mutate state; 10 only notify.**

```
 MUTATING                              NOTIFY-ONLY
 ──────────────────────────────────    ─────────────────────────────────
 RE activity OVERDUE sweep (15 min)    SLA breach re-publisher (15 min)
 processed-event cleanup                   ★ writes nothing and re-publishes
 outbox publisher                            EVERY breach EVERY run —
 …                                            non-idempotent
                                       various reminder mailers
```

⚠ `AutoClosureService` **evaluates but never closes**: it injects no `ComplaintRepository` and
calls no `save`. The auto-closure described in the requirements does not exist.

---

## §11 — Defect map: where the code disagrees with itself

| # | Finding | Evidence |
|---|---|---|
| 1 | RBIO does not enforce from-status on execute | `VALID_FROM` is advertisement-only by name; `performAction` never consults it |
| 2 | Blank `userRole` bypasses authorisation entirely | `if (!userRole.isBlank() && …)` |
| 3 | `resolveTransition` falls back to action-alone | drift tolerance that also erases the role check |
| 4 | Unknown side effect logs a warning and **still commits** the transition | `default -> log.warn(...)` |
| 5 | `IllegalArgumentException` refusals render as HTTP 200 success | Angular error branch never fires |
| 6 | CEPC writes organisation names into `assignedOfficer` | fixed only on the RBIO side |
| 7 | CEPC office transfer writes `forwarded_external`, hiding it from the CRPC Head | `sent_to_other` is the declared legacy value |
| 8 | 13 statuses collapse to citizen "Stage 1" | `CitizenStageMapper` default branch |
| 9 | `AutoClosureService` never closes anything | no repository, no save |
| 10 | SLA monitor cannot see the `COMPLAINTS` table | two schemas, uppercase vs lowercase |
| 11 | SLA breach events are re-published every 15 minutes | no idempotency key |
| 12 | 4 in-memory round-robin rotas do not survive a restart or a 2nd pod | `ConcurrentHashMap` |
| 13 | `@PreAuthorize` annotations are inert | no `@EnableMethodSecurity` |
| 14 | Historical RBIO closures have no `closureClause`, so they cannot be appealed | clause persisted only after the `applyClosureCommunication` fix |
| 15 | `recordMeetingEvent` silently no-ops when the service is null | latent silent-success |

---

## §12 — One-page composite

```
 ┌──────────────┐  APPROVED→NEW_COMPLAINT   ┌─────────────────────────────────────┐
 │ CRPC intake  │──────────────────────────▶│            Complaint                │
 │ EmailDraft   │                           │        status = pending              │
 │ strict map   │                           └──────────────┬──────────────────────┘
 └──────────────┘   portal ──────────────────────────────▶ │
                    email  ──────────────────────────────▶ │
                                                            ▼  assignment
                                        ┌───────────────────────────────────┐
                                        │        status = assigned          │
                                        └────┬──────────────────────┬───────┘
                                  ACCEPT /   │                      │  (CEPC route)
                                  TAKE_ACTION▼                      ▼
                       ┌──────────────────────────────┐   ┌──────────────────┐
                       │       in_progress            │   │  CEPC ladder     │
                       │       ASSESSMENT             │   │  DO→RV→INCHARGE  │
                       └──┬────┬────┬────┬────┬───────┘   │  →CLOSING_AUTH   │
       SUBMIT_FOR_REVIEW  │    │    │    │    │           └────────┬─────────┘
                          ▼    │    │    │    │                    │
              reviewer_review  │    │    │    │                    │
                          │    │    │    │    └─ISSUE_ADVISORY─▶ advisory_issued
       FORWARD_TO_DEPUTY  ▼    │    │    │                         │ADVISORY_COMPLIED
                deputy_review  │    │    │                         ▼
                          │    │    │    └─FORWARD_TO_CONCILIATION─┐
       FORWARD_TO_OMB.    ▼    │    │                              ▼
             ombudsman_review   │    │                       conciliation
                          │     │    │                         │      │
       (SEND_BACK_* down  │     │    │        CONCILIATION_SUCCESS    │ESCALATE_TO_
        the whole ladder, │     │    │                ▼               │ADJUDICATION
        comment required) │     │    │           conciliated ✖        ▼
                          │     │    └─ESCALATE─▶ escalated ──▶ adjudication
                          │     │                                │        │
                          │     │              ADJUDICATION_AWARD│        │ADJUDICATION
                          │     │              (cap checked 1st) ▼        ▼  _REJECT
                          │     │                        adjudicated ✖   …
                          ▼     ▼
                   RESOLVE / REJECT / FACILITATION / SETTLED / WITHDRAWN /
                   NOT_A_COMPLAINT / DECIDE_NON_MAINTAINABLE / CLOSE_COMPLAINT
                                     │
                                     ▼
                 ┌──────────────────────────────────────────────┐
                 │  CLOSED  { resolved closed rejected withdrawn │
                 │            adjudicated conciliated }         │
                 │  + closureClause (now persisted)             │
                 │  + queued email letter & SMS (idempotent)    │
                 └────────┬────────────────────────┬────────────┘
                   REOPEN │                        │ appeal
                          ▼                        ▼
                 back to ORIGINAL holder     ┌─────────────────┐
                 reopenCount++                │ AA: filed →     │
                                              │ under_review →  │
                                              │ hearing_sched → │
                                              │ order_passed ✖  │
                                              │ (409-enforced)  │
                                              └─────────────────┘

   ── in parallel, entity side ──
   RE/PNO:  NOT_OPENED → OPENED → UNDER_REVIEW|RESPONSE_BEING_PREPARED
            → DOCUMENTS_UPLOADED → RESPONSE_SUBMITTED   ( + OVERDUE, automatic )

   ── what the citizen sees ──
   Stage 1 Registered | Stage 2 Under Review | Stage 3 With Bank | Stage 4 Closed
   ★ everything unmapped falls to Stage 1
```

---

### Source index

| Concern | File |
|---|---|
| RBIO row shape | `cms-backend/.../entity/RbioWorkflowTransition.java` |
| RBIO Wave 0 declarations | `.../service/RbioTransitionRegistry.java` |
| RBIO role ladders | `.../service/RbioRoles.java` |
| RBIO S3 ladder actions | `.../service/RbioLadderActions.java` |
| RBIO S5 meetings/transfers | `.../service/RbioMeetingTransferActions.java` |
| RBIO enforcement engine | `.../service/RbioWorkflowService.java` |
| RBIO state vocabulary | `.../config/RbioStatusMasterSeeder.java`, `.../service/RbioStatusVocabulary.java` |
| RBIO seeders | `.../config/Rbio*TransitionSeeder.java` |
| AA transitions | `.../service/AaWorkflowTransition.java`, `.../entity/AppealStatus.java` |
| AA enforcement | `.../service/AppealWorkflowService.java` |
| CEPC | `.../service/CepcWorkflowService.java` |
| CRPC | `.../service/CrpcWorkflowService.java` |
| Citizen projection | `.../service/CitizenStageMapper.java` |
| RE activity | `.../service/ReActivityStatusService.java` |
| Routing | `.../service/ComplaintRoutingService.java` |

Companion document: **`CMS-Workflow-Transition-Matrix.md`** — the same content as a
two-dimensional state × action matrix.
