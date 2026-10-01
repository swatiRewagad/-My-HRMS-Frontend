# RB-IOS QA Session F — workflow ladder, assessment, entities, closure clause, final decision

You are one of **three parallel Claude sessions** (D, E, F) converting a set of ~250 manual QA test
cases for the RB-IOS (RBIO / Ombudsman office) module into Playwright E2E specs in the CMS 2.0
repository, and **fixing the product where a case legitimately fails**.

Repo: `c:/Projects/My-HRMS-Frontend/CMS2.0_Redzone`
Frontend: `cms-portal-frontend/` (Angular). Backend: `cms-backend/` (Spring Boot, package
`com.hrms.cms`).

Sessions D and E are running at the same time against the same database and the same Keycloak.
**Read "Shared-state rules" before you touch anything.**

---

## Your scope

You own the **case lifecycle**: draft creation by the Dealing Official, the assessment milestone, the
DO → Reviewer → Deputy Ombudsman → Ombudsman ladder with its auto/manual assignment choice,
maintainability, additional entities, the Nodal Officer record, compensation fields, the proposed
closure clause, and the final-decision "Comply Advisory" action.

You own the ladder's authoritative source files. Several of your manual cases contradict what the code
actually does, and in this module **the transition table is the authority** — not the manual sheet, and
not the UI.

### What the audit found (verify before trusting — it is a point-in-time reading)

**"Sent to Reviewer": the mandatory comment is NOT enforced.**
- The action is `SUBMIT_FOR_REVIEW` in `service/RbioLadderActions.java`:
  ```java
  SPECS.put(SUBMIT_FOR_REVIEW, Spec.of("reviewer_review", "REVIEWER_REVIEW", "ASSESSMENT")
          .assign(RbioRoles.REVIEWER, RbioTransitionRegistry.ROUND_ROBIN)
          .sla("REVIEWER_REVIEW")
          .from("in_progress", "assigned", "returned")
          .roles(RbioRoles.DEALING_OFFICIAL, RbioRoles.OFFICER));
  ```
- There is **no `.comment()`** on this spec. Compare the send-backs in the same file, which all call
  `.comment()`. The enforcement mechanism is real — `RbioWorkflowService.requireComment()` throws
  `ResponseStatusException(BAD_REQUEST)` and accepts `remarks` **or** `comments` as aliases — it is
  simply not switched on here. **The manual case asserting "comments mandatory at Assessment → Sent to
  Reviewer" will FAIL.** Adding `.comment()` is a one-line fix but it will change behaviour that other
  specs may rely on: write the failing test, then **ask me** before flipping it.
- **Blocking is a whitelist, not a blacklist:** `.from("in_progress", "assigned", "returned")`,
  enforced by `RbioLadderActions.validForState()`. So closed and conciliation *are* blocked — but so
  are `deputy_review`, `ombudsman_review`, `escalated`, `forwarded`. `sent_to_other_dept` maps to
  legacy status `forwarded_external` and is also blocked. The manual case's *outcome* matches, but for
  a broader reason than it states; an "allowed from X" case may surprise you.

**A Dealing Official CANNOT mark Non-Maintainable.**
- `DECIDE_MAINTAINABLE`: `toStatus = null` (records the finding, status unchanged), stage
  `MAINTAINABILITY_DECIDED`, milestone `ASSESSMENT`, **comment mandatory**. Roles: `DEALING_OFFICIAL,
  REVIEWER, DEPUTY_OMBUDSMAN, OMBUDSMAN, OFFICER, SUPERVISOR`.
- `DECIDE_NON_MAINTAINABLE`: `toStatus = "closed"`, **terminal**, **requires `closureClause`**
  (validated against `CLOSURE_CLAUSE_MASTER`, never a compiled literal), comment mandatory. Roles:
  **`DEPUTY_OMBUDSMAN, OMBUDSMAN, SUPERVISOR` only.**
- So manual cases asserting the DO may mark *either* value fail on the non-maintainable branch.
  Deputy Ombudsman and Ombudsman can do both, which does satisfy "visible to and editable by".
- `DEPUTY_OMBUDSMAN_DECISION` carries **both** `maintainability` and `decision` as required params.
- Inbound spelling normalisation in `RbioWorkflowService`: `"NON_MAINTAINABLE"` and
  `"NOT_MAINTAINABLE"` both map to `NON_MAINTAINABLE`.
- Seeded into `RBIO_WORKFLOW_TRANSITION` by `config/RbioLadderTransitionSeeder.java` (`@Order(32)`),
  insert-if-absent on `(action, role, from-status)`.

**Auto vs Manual assignment: the manual option is SILENTLY IGNORED, not blocked.**
- All three handovers declare `ROUND_ROBIN`, which is what "Automatic (Round Robin) pre-selected"
  means server-side. A caller naming `targetUser` is meant to be the Manual option.
- `FORWARD_TO_OMBUDSMAN` is intended auto-only ("there is one Ombudsman per office, so round-robin
  over that role resolves to them"). **But `RbioWorkflowService` handles `ROUND_ROBIN` by calling
  `assignByRole(...)` and never consults `targetUser`** — so a manual target is silently ignored, not
  rejected. There is no 400 or 422.
  - Consequence: a manual case asserting "Manual is unavailable when sending to Ombudsman" **passes
    only if it checks the resulting assignee**, not if it expects an error.
- **`FORWARD_TO_DEPUTY_OMBUDSMAN` uses `ROUND_ROBIN` too** — so "Manual available for Deputy
  Ombudsman", which the manual sheet asserts in several cases, is **effectively NOT IMPLEMENTED on the
  server.** Only `TARGET_PARAM` and `PREVIOUS_HOLDER` honour `targetUser`.
- Silently-ignored-parameter is precisely the defect class `e2e/rbio/reopen-param-integrity.spec.ts`
  was written to catch (*"the defect family that answers 200 OK while storing the wrong data"*) — read
  that spec and follow its pattern.
- **Frontend has no auto/manual radio.** What exists is
  `rbio-complaint-detail.component.ts` `approvalOptions`:
  ```
  { key: 'reviewer',  label: 'Send to RBIO Reviewer',           nameLabel: 'Name of RBIO Reviewer' },
  { key: 'deputy',    label: 'Send to RBIO Deputy Ombudsman',   nameLabel: 'Name of RBIO Deputy Ombudsman' },
  { key: 'ombudsman', label: 'Send to RBIO Ombudsman',          nameLabel: 'Name of RBIO Ombudsman' },
  ```
  **All three carry a `nameLabel`, including `ombudsman`** — the UI offers a name picker exactly where
  the story says auto-only. Good negative-test target.
- The officer dropdown is fed by `GET /api/v1/workflow/assignable-users`, which filters neither
  inactive nor on-leave users. **That endpoint is session D's to fix** — do not edit
  `WorkflowController.java`.

**"Comply Advisory" CLOSES the complaint; it does not reassign to a DO.**
- `ADVISORY_COMPLIED` in `RbioLadderActions.java`:
  ```java
  Spec.of("closed", "ADVISORY_COMPLIED", "FINAL_DECISION")
      .closure("ADVISORY_COMPLIED").comment()
      .fx(FX_ADVISORY_COMPLIED).fx(FX_RESOLVED_AT).fx(FX_CLOSED_AT)
      .terminal()
      .from("advisory_issued")
      .roles(DEALING_OFFICIAL, REVIEWER, DEPUTY_OMBUDSMAN, OMBUDSMAN, OFFICER, SUPERVISOR);
  ```
- Milestone is correctly `FINAL_DECISION`, comment **is** mandatory, valid only
  `from("advisory_issued")`. But there is **no `.assign(...)`** and it is **`.terminal()`** with
  `toStatus = "closed"`. **Every manual case asserting "Comply Advisory reassigns the case to an
  active DO", the DO-receives-notification case, and the DO-initiates-NO-communication case will
  FAIL.**
- Corroborating signal: `ADVISORY_COMPLIED` is **not** visible to the Dealing Official in
  `RbioStatusMasterSeeder.java`'s role-visibility grants (visible to Reviewer, Deputy, Ombudsman) —
  so it was never designed as a DO-facing handoff.
- This is the biggest spec-vs-code conflict in your scope. **Bring it to me as a decision**: either
  the manual cases are wrong, or `ADVISORY_COMPLIED` needs to stop being terminal and gain an
  `.assign(DEALING_OFFICIAL, ...)`. Do not unilaterally change a terminal closing action.
- Frontend: `src/app/components/rbio/rbio-advisory/`. No dedicated E2E spec exists.

**Proposed closure clause: FRONTEND-ONLY. No backend at all.**
- Frontend has the control: `rbio-complaint-detail.component.ts` `proposedClause = signal('')`, and
  `rbio-complaint-detail.component.html` renders an input with
  `[readonly]="isFieldReadOnlyDueToDecision()"` and `(ngModelChange)="onProposedClauseChange($event)"`.
  Also on the create screen.
- **Auto-population from the DO's selections: NOT FOUND.** Two places in that component hardcode
  `proposedClause: ''` when mapping the server response. Nothing reads a DO-proposed clause from the
  API.
- **Backend: grep for `proposedClosureClause|proposed_closure_clause|proposedClause` in `cms-backend`
  → zero hits.** No column, no endpoint, no persistence. A textbook frontend-only field: the UI is
  complete, errors are swallowed, demos look clean, and nothing persists.
- What *does* exist is the **final** clause: `Complaint.closureClause` / `closureCause` /
  `customClosureText`, `GET /api/v1/workflow/closure-status/{complaintNumber}`, the clause master
  (`entity/ClosureClauseMaster.java`, `config/ClosureClauseMasterSeeder.java`),
  `GET /api/v1/workflow/closure-clauses`, and `DECIDE_NON_MAINTAINABLE` / `NOT_A_COMPLAINT` requiring
  `closureClause`.
- **History retaining the original DO-proposed value: NOT FOUND.** A generic field-level override
  history does exist (`GET|POST /api/v1/complaints/{complaintNumber}/action-override` plus
  `src/app/components/rbio/rbio-action-override-history/`) — but it is not wired to `proposedClause`.
  **Wire it rather than building a parallel mechanism.**
- Beware: **1646 of 1647 closed complaints have no closure clause**, so they cannot be appealed (the
  appeal endpoint 503s). Do not assume a seeded closed complaint has a clause.

**"Add Entity", max 6: FULLY EXISTS — frontend, backend, and E2E. Read before writing.**
- `src/app/components/rbio/rbio-add-entity/rbio-add-entity.component.ts`:
  `MAX_ADDITIONAL_ENTITIES = 6`, `hasReachedMax`, and `openForm()` blocks with
  `` `Maximum of ${MAX_ADDITIONAL_ENTITIES} additional entities reached.` ``
- Fields: `entityName, entityBranch, entityType, entityCategory`. **The client-side mandatory check is
  only `entityName` + `entityType`** — `entityBranch` and `entityCategory` are **not** enforced. So the
  manual case "all mandatory primary-entity fields are mandatory on additional entities" is likely a
  genuine mismatch worth asserting.
- Frontend role gate `canAddEntity`: `['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN']`.
  **The backend POST guard lists `RBIO_DEALING_OFFICIAL` first.** The manual sheet has a whole block of
  DO-adds-entity cases — so the frontend gate probably excludes the very role the cases exercise.
  Write the negative test and report it.
- Assessment-stage gate `isAssessmentStage`:
  `['assigned','in_progress','assessment','deputy_review','reviewer_review']`.
- Backend `controller/RbioCaseFileController.java` (`/api/v1/complaints`):
  `GET|POST /{complaintNumber}/additional-entities`, `DELETE /{id}`. Service
  `RbioAdditionalEntityService.assertCapAllowsOneMore` — **the cap is enforced server-side** and is
  shared with impleading and meeting participants.
- Existing coverage: `e2e/rbio/case-file.spec.ts` (302 lines, "S3 — additional entities, field-level
  override history, and legal case"; its header notes all three endpoints were previously phantoms).
  Helpers `rbioCaseFileGet` / `rbioCaseFilePost` in `e2e/utils/test-data.ts`.

**Compensation: `compensationAwarded` does not exist under that name.**
- `compensationSought` exists as a **citizen filing** field (`crpc/draft-assessment`, i18n key
  `form.compensation_sought` in 9 locales). **Grep for `compensationSought` in
  `src/app/components/rbio/**` → zero hits.** Not on the NO layout, not on the Ombudsman layout.
- **Grep for `compensationAwarded|compensation_awarded` repo-wide → zero source hits.** What exists
  instead is award *validation*: `POST /api/v1/workflow/rbio/validate-award` →
  `service/RbioCompensationService` (`validateAward`, `calculateCompensationBand`, `getMaxAllowed`),
  surfaced by `src/app/components/rbio/rbio-adjudication/`.
- So the manual cases "Compensation Sought and Compensation Awarded visible on both the NO and
  Ombudsman layouts / editable / reflected in all layouts" are a **build item**. Reuse
  `RbioCompensationService` for the award cap; do not invent a second cap.
- `e2e/rbio/adjudication.spec.ts`'s header is important: *"This API answers a refused write with HTTP
  200 and `{"success": false}` — an over-cap award returns 200 with the cap message in the body."*
  **Assert on the body, never the status.**
- Award caps were **hardcoded** and are a known go-live item; the award cap and sub-judice guards are
  BUILT but config-OFF, armed by a single `SYSTEM_CONFIG` row pending legal sign-off. **Do not arm
  them.**

**Nodal Officer tab: EXISTS, with a role gap.**
- Tab list in `rbio-complaint-detail.component.ts`: `summary`, **`nodal` ("Nodal Officer Record")**,
  `conciliation`, `forward`, `email`, `attachments`, `final`, `legal`, `history`.
- `controller/NodalOfficerRecordController.java` — `@RequestMapping("/api/nodal-officer-records")`
  (**note: no `/api/v1` prefix**), `POST /` and `GET /by-complaint/{complaintNumber}`, both
  `@RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})`. **DEALING_OFFICIAL,
  REVIEWER, DEPUTY_OMBUDSMAN and OMBUDSMAN are NOT in that list** — yet the manual cases assert "NO
  communication is viewable to all users". Likely a real gap.
- NO auto-creation is tied to Add Entity — the component toasts `"Entity "X" added. NO record
  created."`
- Communication log: `src/app/components/rbio/rbio-email-communication/` (tab `email`), backend
  `ComplaintCorrespondenceController.java`, outbox readable in tests via `readCommunicationOutbox` in
  `test-data.ts`. **Citizen notices are PENDING obligations, never "sent"** in this codebase — assert
  accordingly.
- Existing coverage: `e2e/rbio/s6-correspondence-and-upload-link.spec.ts` (487 lines) and
  `e2e/rbio/closure-clause-and-communication.spec.ts` (545 lines).

**Draft creation + scanned letter + OCR: EXISTS. But "CRPC layout" is not a thing, and TIFF is
accepted.**
- **"CRPC layout" does not exist as a named concept.** Grep for `CRPC layout|crpcLayout|crpc-layout|
  CRPC_LAYOUT|layout` in `rbio-create-complaint.component.ts` → zero hits. There are two separate
  screens: RBIO's `rbio/create-complaint`, and the CRPC ones under `src/app/components/crpc/`
  (`deo-home`, `draft-assessment`, `physical-letter`, `reviewer-assessment`). There is no shared
  layout toggle. **Decide which concrete screen each manual case means and say which you chose.**
  (A prior ruling in this project settled that "layout" means *department + field-set*, not a
  template — apply that reading.)
- Draft backend: `StaffDraftController.java` (`/api/v1/staff-drafts`) — `GET /config` (autosave
  interval + presence-stale from `SYSTEM_CONFIG`), `GET /`, `GET /{milestone}` (**returns 204, not
  404, when absent**), `POST /`, `DELETE /{id}`, `POST /discard-autosave`,
  `POST|DELETE /presence/{complaintNumber}` (record locking). Also `ComplaintDraftController.java`
  (`/api/v1/complaints/drafts`).
- **Attachment types: the allowed set is WIDER than the manual case.** Frontend rejects with
  `'Only PDF, JPEG, PNG, or TIFF files are accepted.'`; `OcrController.java`
  `ALLOWED_CONTENT_TYPES = Set.of("application/pdf", "image/jpeg", "image/png", "image/tiff")`.
  **TIFF is accepted**, so a case asserting "only .pdf/.jpg/.png" and expecting TIFF to be rejected
  will fail. Bring it to me — TIFF is plausibly correct for scanned letters.
- **Size limits are configuration, not constants.** A settled ruling in this project: config decides
  (currently 2MB / 25MB / 10 files), three enforcement paths were previously split, and **guards must
  name no size**. The frontend reads `cms.upload.max_file_size`. Do not hardcode a size in a test
  message or a guard.
- OCR: `OcrController.java` (`/api/v1/ocr`) — `GET /provider`, `POST /extract-from-draft`,
  `POST /extract`; PDF text via PDFBox; a separate `cms-paddle-ocr` module. Real per-field provenance
  tracking exists: `src/app/shared/ocr-provenance.ts`, `ocrProvenance`, `shouldApply/markFromOcr/
  markEdited` (override-wins), `ocrManualEntryReason`, PDF preview.
- Existing coverage: `e2e/rbio/s7-ocr-and-drafts.spec.ts` (373 lines, deliberately API-level). It also
  covers a real past bug: the component set `draftSaved(true)` in **both** the success and the error
  handler.

### Your case list (condensed from the manual sheet — ~105 cases)

Boilerplate "user is able to login" / "list of complaints is displayed" / "clicking the complaint opens
details" preamble rows repeat across almost every block. **Collapse them into one shared helper**; do
not write 40 identical login tests. Session E owns login testing proper.

**F1 — Draft complaint by Dealing Official**
1. "Create Draft Complaint" button on the DO dashboard, and it is clickable.
2. Clicking it opens a **blank** draft form in the CRPC layout.
3. All mandatory fields marked **in red with an asterisk**.
4. Cannot proceed with mandatory fields blank; **Save/Submit stays disabled** until every mandatory
   field is complete.
5. DO creates a draft successfully; it then appears in the dashboard complaint list.
6. DO can attach a scanned copy of a physical letter.
7. Field data is **fetched from the scanned letter** (OCR).
8. Attachment accepts `.pdf`, `.jpg`, `.png`.
9. Attachment **rejects** other formats. *(Mind TIFF — see above.)*
10. Uploaded scan is stored against the draft record and viewable from the draft's **Attachments** tab.

**F2 — Maintainability**
11. DO can edit complaint details.
12. DO can mark the complaint **Maintainable or Non-Maintainable**. *(DO cannot do Non-Maintainable —
    see above.)*
13. DO cannot leave the Maintainable/Non-Maintainable section blank; an appropriate error appears.
14. DO sends the complaint to the **Deputy Ombudsman**; status updates accordingly.
15. Deputy Ombudsman sees the complaint and the selected maintainability value, and **can edit it**.
16. Same chain for DO → **Ombudsman**: comments field and complaint details cannot be blank, comments
    save, status updates, Ombudsman sees the complaint and the saved comments, and can edit the
    maintainability value.

**F3 — Assessment milestone: "Sent to Reviewer"**
17. The action is visible **only** at the assessment milestone.
18. DO can select "Sent to Reviewer" **with** mandatory comments captured.
19. DO **cannot** select it with comments blank. *(Currently not enforced — see above.)*
20. The mapped Reviewer is notified and can view the complaint details and comments.
21. "Sent to Reviewer" is **refused** when status is closed / sent to other departments /
    conciliation.
22. Complaint status updates to "Sent to Reviewer".
23. Reviewer can **edit** fields the DO added, and save.
24. History captures every change made by DO and Reviewer.
25. Reviewer **cannot** blank out the DO's proposed clause and save.
26. Reviewer B cannot view or edit a complaint assigned to Reviewer A.

**F4 — Proposed closure clause**
27. For a non-maintainable complaint with resolution "Rejected", the proposed closure clause is
    **auto-populated**.
28. DO can save the auto-populated clause and then send to Reviewer.
29. DO **cannot skip** the clause and send to Reviewer.
30. Reviewer sees all DO-entered details.
31. Clause is editable by the Reviewer for both maintainable and non-maintainable complaints.
32. On the Ombudsman's detail screen, the proposed closure clause field is displayed, auto-populated
    from the DO's selections, editable, and saveable.
33. The **original DO-proposed value is retained in complaint history**.
    *(27, 32, 33 are a build item — no backend exists.)*

**F5 — Auto / Manual assignment on handover**
34. Assignment defaults to **Automatic (round robin)** on the assessment tab; the **Manual option is
    hidden** while Automatic is selected.
35. Automatic assigns an **active** Reviewer respecting that office's threshold, in real time, and the
    Reviewer is notified.
36. User can select **Manual**; selecting it **blanks the reviewer-name field**.
37. Manual name field type-aheads and sorts on the letters entered; an active reviewer can be selected.
38. Manual assignment routes to the selected active reviewer.
39. An **inactive** reviewer is neither shown in the dropdown nor selectable.
40. A reviewer **from another office** is not shown.
41. Submitting Manual **without** a reviewer selected is refused.
42. Toggling Automatic ↔ Manual shows the right fields with no lag, and round-robin re-resolves on
    switching back.
43. Sending to **Deputy Ombudsman**: Automatic is the default; both Automatic and Manual are
    selectable; Automatic uses round-robin; Manual lets the Reviewer pick a named Deputy Ombudsman;
    status updates. *(Manual is silently ignored — see above.)*
44. Sending to **Ombudsman**: Automatic available, **Manual NOT available**; on save the complaint goes
    to the office's Ombudsman automatically. *(Assert the resulting assignee, not an error.)*
45. Every role (DO, Reviewer, Deputy Ombudsman, Ombudsman) is shown red asterisk indicators when
    skipping mandatory fields while forwarding the assessment.

**F6 — Add Entity (Assessment milestone)**
46. "Add Entity" is present on the Assessment milestone tab for Deputy Ombudsman **and** for Dealing
    Official. *(Frontend gate excludes DO — see above.)*
47. The field may be left blank and saved.
48. An entity can be added.
49. Adding an entity **does not affect the primary entity** already captured.
50. The additional entity record captures the **same attributes** as the primary entity, and all
    mandatory primary-entity fields are mandatory on additional entities. *(Only 2 of 4 enforced.)*
51. Cannot proceed with those mandatory fields blank.
52. Maximum **six** additional entities per complaint; the field is **disabled** after six; a seventh
    is refused with a clear validation message.
    *(Test this for both Deputy Ombudsman and Dealing Official — the sheet has both blocks.)*

**F7 — Nodal Officer and compensation**
53. Nodal Officer tab present on the detail screen, clickable, and the NO record displays.
54. NO communication is **logged against the NO record**, with timestamp and content reference.
55. NO communication is **viewable to all users**. *(Role guard lists only 3 roles — see above.)*
56. Log entries visible to PNO/NO users viewing the record.
57. **Compensation Sought** and **Compensation Awarded** visible on **both** the NO and the Ombudsman
    layouts, editable, and updated values reflected in all layouts. *(Build item.)*

**F8 — Final decision: Comply Advisory**
58. "Comply Advisory" is available to the Ombudsman at the **Final Decision** milestone.
59. Selecting it and saving **reassigns the case to the assigned Dealing Official**, via a confirm
    dialog offering active DOs. *(Currently closes the complaint instead — see above.)*
60. That DO can then access the case and initiate **Nodal Officer communication**.
61. "Comply Advisory" is hidden/disabled **before** the case reaches Final Decision; attempting it
    early gives a validation error.
62. Skipping mandatory fields while saving Comply Advisory gives a validation error.
63. Reassignment **skips inactive DOs** and picks an active one.
64. An entry is recorded in the audit trail/history when Advisory Complied is actioned, and the history
    shows the full chain from complaint creation to advisory compliance.

---

## Shared-state rules — read before writing anything

Three sessions share one database (`cms_db` on 3306), one Keycloak (9090), and the repo.

- **Your git worktree:** work in an isolated worktree. `cms-backend/target/` is a single directory;
  concurrent `mvn` in the same checkout produces **phantom compile errors blaming files you never
  touched**. If you see one, verify with an isolated `javac` (JDK 17, `-encoding UTF-8`, `-proc:full`)
  before believing it. Relatedly, a **stale `target/classes` yml** and
  `src/test/resources/application.yml` **shadowing** the real config have both wasted time here.
- **Your backend port is 8096.** Start it with `deployment/run-test-backend.sh 8096`, which passes
  `-Dcms.hazelcast.cluster-name=cms-claude-8096` and `-Dhazelcast.discovery.enabled=false`. Note
  `-Dhz.cluster-name` is the **wrong** property name and is silently ignored — the node then joins the
  user's cluster and cache assertions become meaningless. **Port 8092 belongs to the user — never
  restart or stop it.** 8094 is `cms-workflow-service` (E owns it). D has 8093, E has 8095.
- **The script lies about failure.** `run-test-backend.sh <port> --restart` prints
  `ERROR: backend failed to start` and exits 1 **while the app is healthy**, because its readiness poll
  greps a log still holding the previous run's `BUILD FAILURE`. Verify with `netstat` + `curl`.
  Also: `ECONNREFUSED` is **not** flakiness — it means nothing is listening. Do not retry past it.
- **Keycloak on 9090 appeared DOWN when this brief was written.** Exactly one session may start it and
  exactly one may run `deployment/provision-aa-roles.sh` (read-modify-write on
  `unmanagedAttributePolicy`). **Coordinate through me — do not run provisioning on your own
  initiative.** Most of your work can run API-level with dev identity headers
  (`cms.security.allow-dev-identity-headers`), so prefer that over browser login where the case allows.
- **There is no Flyway.** Schema comes from `ddl-auto: update`; `database/*.sql` is hand-run.
  **New columns must always be nullable**, stagger backend startups, one session per entity. Note
  `database/*.sql` builds only **55 of 113 tables** on a clean DB and V5/V6 are not re-runnable — do
  not attempt a clean rebuild.
- **Your reserved numbers** (highest currently in use: MySQL V109, Oracle V106, seeder `@Order` 66):
  - MySQL migrations **V120-V124**, Oracle **V117-V121**. Write both; Oracle variants are not optional
    here. **V-numbers have been stolen mid-session before** — re-check the highest number immediately
    before you create a file.
  - Seeder `@Order` **80-84**. One new seeder class per session — repo convention, stated in
    `AaRegisterTranslationSeeder.java`. **Never edit another session's seeder.** Translation keys are
    idempotent by `existsByCode`, so two sessions using the same code silently keep the first text,
    which looks like a UI bug rather than a conflict. Prefix yours `rbio.assessment.*` /
    `rbio.entity.*`.
  - New spec files named `e2e/rbio/rbios-f-*.spec.ts`. **Do not edit the existing 19 specs under
    `e2e/rbio/`** except to fix a test you provably broke. `case-file.spec.ts`,
    `ladder-sendback.spec.ts`, `s7-ocr-and-drafts.spec.ts` and
    `closure-clause-and-communication.spec.ts` overlap your scope heavily — **read them first, extend
    in your own file.**
  - Seeding helpers: create `e2e/utils/helpers-rbios-f.ts`. **Do not edit `e2e/utils/test-data.ts`**
    (~1589 lines, a known chokepoint) — import from it.
  - Complaint subjects you seed must start with `RBIOS-F `. Mobile numbers in the range `98765_3____`.
- **Chokepoint files owned by ONE session — these are yours:** `RbioWorkflowService.java` (~599 lines,
  two giant switches), `RbioTransitionRegistry.java`, `RbioLadderActions.java`,
  `RbioLadderTransitionSeeder.java`, `RbioCaseFileController.java`, `RbioAdditionalEntityService.java`,
  `NodalOfficerRecordController.java`, `rbio-complaint-detail.component.*`, `rbio-add-entity.*`,
  `rbio-advisory/*`, `rbio-workflow.service.ts`, `Complaint.java`.
  **NOT yours:** `WorkflowController.java` (D), `RbioComplaintListController/Service.java` and
  `RbioStatusMasterSeeder.java` and `cms-workflow-service/` (E), `admin/master-data/*` (D). Ask me
  before crossing — and note that a "compat shim" added to one of these once broke a working caller.
- **A torn shared `target/` directory causes phantom 500s** at runtime, not just compile errors. If
  endpoints start 500ing for no reason, check whether another session is mid-compile.

## Running the suite

From `cms-portal-frontend`, or the `chromium` project is not found:

```
API_BASE_URL=http://localhost:8096 \
UI_BASE_URL=http://localhost:4202 APP_BASE_URL=http://localhost:4202 \
WORKFLOW_BASE_URL=http://localhost:8094 \
PW_OUTPUT_DIR=/c/tmp/pw_F PLAYWRIGHT_HTML_REPORT=/c/tmp/pw_F_report \
npx playwright test e2e/rbio/rbios-f-... --project=chromium --reporter=line
```

- **Both `UI_BASE_URL` and `APP_BASE_URL` are required.** `playwright.config.ts` reads only
  `UI_BASE_URL`; ten specs under `e2e/public/` read `APP_BASE_URL` and default to 4200. Setting one
  gave 47 fake `ERR_CONNECTION_REFUSED` failures.
- **Use dev-server port 4202.** A dev-server port must be in **both** the `dev-local` CORS allowlist
  **and** the Keycloak `cms-frontend` client's `redirectUris`. `application-dev-local.yml` hardcodes
  `cms.cors.allowed-origins: http://localhost:4200,4201,4202,4300` **with no `${}` placeholder**, so
  `CMS_CORS_ORIGINS` and `-Dcms.cors.allowed-origins` are both silently ignored. An unlisted origin
  gets `403 Invalid CORS request` on every XHR: pages render empty, translation keys show raw, and
  "Complaint not found" appears for records that demonstrably exist. Ports 4200 and 4202 are already
  up; **do not run `ng serve` yourself.**
- **Import `test` and `expect` from `../rbio/harness`**, **never from `@playwright/test`**. A spec
  importing raw Playwright writes to your backend but reads from the user's 8092, because the Angular
  bundle has `apiBaseUrl: 'http://localhost:8082'` compiled in and only the custom `page` fixture
  rewrites it.
- **Never `--headed` for bulk runs.** Run per directory and aggregate.
- **Wizard/form E2E traps in this codebase:** `validationErrors` recomputes **only on click**, so
  assert after clicking, not after typing. A `"Track"` text locator also matches the mode tab. Assert
  that refusals are **VISIBLE**, not merely present in the DOM. `/api/v1/complaints/{n}` is the correct
  detail path. A `:has-text("X")` locator matching a longer superstring ("Nodal Officer" vs "Principal
  Nodal Officer") trips strict mode — use `.first()` or an exact match. Your F1/F5 blocks are full of
  mandatory-field and asterisk assertions, so all of these apply.
- **Roughly half the suite cannot produce screenshots** — 29 specs (398 tests) are pure API tests using
  the `request` fixture. When a browser test fails, read `test-results/<test>/test-failed-1.png` with
  the Read tool.

## Assertion rules specific to this module

- **RBIO workflow writes frequently answer HTTP 200 with `{"success": false}`.** A refused write is
  *not* a 4xx. **Assert on the body.** `WorkflowController.performAction` catches
  `IllegalArgumentException` and converts it to 200 + `success:false`, which Angular's error branch
  never sees — which is exactly why `requireComment` deliberately throws `ResponseStatusException` to
  get a real 400. **When you add a refusal, do NOT raise `IllegalArgumentException`.** Only
  `requireComment` (400) and `PREVIOUS_HOLDER`-unavailable (422) give real error statuses today.
- **The transition table is the authority.** Before asserting a transition, read
  `RbioLadderActions.java` / `RbioTransitionRegistry.java` — not the UI and not the manual sheet.
- **Never hardcode vocabularies.** Read clauses from `GET /api/v1/workflow/closure-clauses` and status
  filters from `GET /api/v1/rbio/status-filters?role=`. Closure clauses are validated against
  `CLOSURE_CLAUSE_MASTER`; never compile a clause literal into a test or a guard.
- **A 404 may be a stale JVM, not a missing endpoint.** Check the JVM start time against
  `target/classes` mtimes first.
- **A skip is not a pass.** Report passed / failed / skipped separately.
  `e2e/utils/auth.ts:isKeycloakAvailable()` returns false on any throw and callers `test.skip()`, so a
  Keycloak outage yields a green but vacuous staff suite.
- `rbioEstablishLadderCustody` in `test-data.ts` seeds custody history so `PREVIOUS_HOLDER` send-backs
  resolve — you will need it for the send-back cases. Other helpers: `createRbioComplaint`,
  `performRbioAction`, `advanceRbioToStatus`, `rbioLadderAction`, `rbioLadderHeaders`,
  `RBIO_LADDER_ACTORS`, `rbioCaseFileGet`, `rbioCaseFilePost`, `rbioGetComplaint`,
  `rbioReadComplaintColumns`, `readCommunicationOutbox`, `readImpleadedParties`,
  `cleanupRbioComplaint`.
- Watch for a `@Transactional(REQUIRES_NEW)` **self-invocation** trap in the assignment code — a
  self-call does not open a new transaction.
- Seeded users: `_001`-style accounts use password `test123`; `cms.admin` / `rbio.*` use `Test@1234`;
  legacy snake/dotted users use `password`. Relevant to you: `rbio_do_001`
  (RBIO_DEALING_OFFICIAL), `rbio_reviewer_001` (RBIO_REVIEWER), `rbio_dyombudsman_001`,
  `rbio_ombudsman_001`, `orbio_admin_001` (RBIO_ADMIN+SUPER_ADMIN). For Reviewer-A-vs-Reviewer-B
  (case 26) note `rbio_officer_002` and `rbio_officer_003` have **no password credential** — use dev
  identity headers for the second identity instead. `identityHeadersFor(actor, scope)` needs an
  explicit scope: `rbio_reviewer_001` returns `CEPC_REVIEWER` without one.

## How to work

1. **Verify the audit above against today's code first.** Report anything that has changed. A
   contradicted finding is a valuable result, not a setback.
2. Then work in order: F2/F3 (maintainability + ladder, where the contradictions live), F6 (Add Entity,
   mostly already built), F1 (draft + OCR), F5 (auto/manual), F4 + F7 + F8 (the build items).
3. **Write the failing test before the fix.** Your scope has five spec-vs-code conflicts —
   the non-mandatory comment, DO-cannot-mark-non-maintainable, silently-ignored manual assignment,
   Comply-Advisory-closes-instead-of-reassigning, and TIFF. **Bring each to me as a decision.** Do not
   change a terminal closing action, a role grant, or a transition's `from` set unilaterally: D and E
   depend on this ladder.
4. **Run only the specs you touched while you work.** Run the full `e2e/rbio` directory once at the
   end. Do not run the whole suite between increments.
5. **Never `git push`, never `git stash`, never `rm -rf`, never `taskkill`.** Do not stash to "prove a
   baseline" — it has destroyed work here before.
6. The backend test baseline was **971 tests, 0 failures** as of 2026-09-16 and ~1310 by 2026-09-21.
   **If a backend test fails, assume it is yours.** Do not excuse it as pre-existing without verifying
   on a clean tree.
7. Write findings continuously to `docs/prompts/findings-RBIOS-F.md`: per case —
   **PASS / FAIL / NOT-BUILT / CONTRADICTS-SPEC**, the evidence, and what you changed. The
   contradictions list is the highest-value output of this session.

Report at the end: cases automated, cases passing, defects fixed, build items completed, and the list
of open decisions you need from me.
