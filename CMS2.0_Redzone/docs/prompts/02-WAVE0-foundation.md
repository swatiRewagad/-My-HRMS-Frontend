# WAVE 0 — RBIO FOUNDATION

**Run alone. Sessions S1-S7 are blocked on you.** Prepend your standing BASE prompt.
Baseline HEAD: c23b899.

---

<BATCH: WAVE 0 — RBIO FOUNDATION. Run alone.>

Your output is the SUBSTRATE six parallel sessions will build on. Optimise for a stable contract
over feature completeness. You own the chokepoint files; nobody else may touch them after you
land.

CONFIRMED GROUND TRUTH (verified at HEAD c23b899 — re-verify before editing, but do not
re-litigate):
 - RbioWorkflowService.java:37-55 ROLE_ACTIONS knows only RBIO_OFFICER / RBIO_SUPERVISOR /
   RBIO_CONCILIATOR / RBIO_ADJUDICATOR / RBIO_ADMIN — function-named, not rank-named.
   DEALING_OFFICIAL, RBIO_REVIEWER and RBIO_OMBUDSMAN have ZERO matches in cms-backend.
   RBIO_DEPUTY_OMBUDSMAN / RBIO_OMBUDSMAN appear ONLY in TypeScript
   (rbio-complaint-detail.component.ts:246,354; task-action.component.ts:377,631), so the AOP
   guard 403s them today.
 - Action dispatch is two hardcoded switches: performAction (~:82, cases :143-190) and
   executeAction (~:246-514). WorkflowController.java (~935 lines) holds every RBIO endpoint with
   inline @RbioRoleGuard role lists.
 - GET /api/v1/rbio/complaints DOES NOT EXIST (re-confirmed at c23b899). rbio-home.component.ts
   :205 calls it and falls through to generateSampleData() at :212,241-265. THE MAIN RBIO LIST
   SCREEN IS MOCK DATA.
 - Complaint.status is a bare String (entity/Complaint.java:74). RBIO writes ~13 lowercase
   literals; the ComplaintStatus enum in cms-common (11 UPPERCASE values) is bypassed entirely.
   No STATUS_MASTER table exists anywhere.
 - No milestone concept exists in RBIO. The word appears only as prose in AA files. AA has the
   pattern to copy: database/V51__aa_workflow_state_machine.sql + entity/AaWorkflowTransition.java.
 - Complaint.java has NO @Version (only scheme_version matches a grep for "Version").
   NodalOfficerRecord.java:35 remains the only @Version in cms-backend.
 - The escalation ladder is hardcoded OFFICER->SUPERVISOR->CONCILIATOR->ADJUDICATOR
   (RbioWorkflowService.java:577-581) and CONTRADICTS the story ladder.
 - CEPC ALREADY HAS the DO/Reviewer/Incharge/ClosingAuthority ladder the stories want
   (CepcWorkflowService.java:49-71, with SEND_BACK_DO / SEND_BACK_REVIEWER /
   SEND_BACK_INCHARGE / FORWARD_TO_OTHER_OFFICE / FORWARD_TO_OTHER_RBI_DEPT). Study it — it is
   your best in-repo reference for what you are building in RBIO.

ALREADY FIXED SINCE THE GAP ANALYSIS — DO NOT RE-REPORT AS DEFECTS:
 - Commit 41d7670 made the JWT AUTHORITATIVE over X-User-* headers in all four role guard
   aspects. RbioRoleGuardAspect now decodes the token FIRST (:79) and consults headers only when
   the token yielded nothing AND cms.security.allow-dev-identity-headers is true (:118, :34-35).
   The earlier "unverified base64 JWT decode / trusts headers in every profile" finding is CLOSED.
   Preserve this precedence when you touch the aspect — six instances of that bug have now been
   found, so do not reintroduce a header-first read.
 - Commit a5953cc fixed the RBIO award-recorded-as-0.00 defect. RbioWorkflowService.java:413 now
   reads firstNonBlankParam(params, "awardAmount", "compensationAmount") and REFUSES a missing
   amount (:418) instead of defaulting to zero. Do not regress this in the refactor: your
   transition-table rewrite must keep the refusal, and any generic param plumbing you introduce
   must not silently default a monetary field.

DELIVER, in this order:

 1. ROLE LADDER. Introduce RBIO_DEALING_OFFICIAL, RBIO_REVIEWER, RBIO_DEPUTY_OMBUDSMAN and
    RBIO_OMBUDSMAN as first-class roles alongside the existing five. Conciliator and Adjudicator
    remain STAGE roles, not ranks — conflating stages with ranks is the mistake to avoid.
    Wire RbioRoleGuardAspect (preserving the token-first precedence above). Provision the roles
    and test users into Keycloak realm cms by extending deployment/provision-aa-roles.sh
    idempotently. YOU ARE THE ONLY SESSION PERMITTED TO RUN PROVISIONING — its
    unmanagedAttributePolicy step is a read-modify-write and a concurrent run loses the other
    session's update.
    Delete or redirect the two lossy role bridges once real roles exist:
    task-action.component.ts:548 maps RBIO_ADJUDICATOR->'OMBUDSMAN' and RBIO_SUPERVISOR->
    'DEPUTY_OMBUDSMAN'; WorkflowController.java:479 accepts a literal "DEPUTY_OMBUDSMAN" param.

 2. STATUS MASTER. Create RBIO_STATUS_MASTER seeded with the ~25 story statuses: All Complaints,
    New Complaint, Sent to DO, Sent to Reviewer, Sent to Deputy Ombudsman, Sent to Ombudsman,
    Sent Back to DO, Sent Back to Reviewer, Sent Back to Deputy Ombudsman, Award Passed, Advisory
    Complied, Ombudsman Decision, Deputy Ombudsman Decision, Complaint Closed, Complaint Re-Open,
    Sent to Other Office, Sent to Other Departments, Sent to Other Regulated Bodies, Meeting
    Scheduled, Complaints Rejected/Withdrawn/Settled, Facilitation/Rejection, Not a Complaint,
    Appeal Closed, Complaint Assigned to Me, Complaint Created by Me.
    Columns MUST include the per-role visibility needed by UST426-433 so S1 drives its five
    different filter lists from DATA, never a hardcoded array.
    Reconcile with the lowercase strings RBIO writes today and with ComplaintStatus. Maintain
    backward compatibility and state your mapping explicitly in the contract report.
    ALSO: there are FOUR disagreeing hardcoded CLOSED_STATUSES lists — WorkflowController:57,
    NotificationScheduledTasks:29, AppealWorkflowService:36, AppealController:28. DO NOT ADD A
    FIFTH. Consolidate, or at minimum document which is authoritative and why.

 3. MILESTONE MASTER + TRANSITION TABLE. Create RBIO_MILESTONE_MASTER (Register, Assessment,
    Conciliation, Forward, Final Decision) and RBIO_WORKFLOW_TRANSITION (from_status, action,
    role, to_status, to_milestone, assign_to_role, requires_comment, is_terminal, ...).
    Add a NULLABLE Complaint.milestone column.

 4. REPLACE THE SWITCH. Refactor performAction/executeAction to resolve from
    RBIO_WORKFLOW_TRANSITION. Existing behaviour must be preserved EXACTLY. This is the
    highest-risk change in the whole programme: mutation-verify aggressively and keep
    RbioWorkflowServiceTest (847 lines) and RbioWorkflowControllerTest (1131 lines) green.
    Sessions S1-S7 will add actions as TABLE ROWS — that is the entire point of this task.

 5. REAL LIST ENDPOINT. Implement GET /api/v1/rbio/complaints with server-side paging, sorting,
    filtering and role scoping. Delete generateSampleData(). S1 builds the grid on it.

 6. @Version on Complaint, with the optimistic-lock conflict surfaced as a distinct HTTP status.
    S7 builds UST675 record-locking UX on this.

 7. ROUTE MARKERS. In cms-portal-frontend/src/app/app.routes.ts insert paired comment markers per
    session: // <<< S1 START >>> / // <<< S1 END >>> through S7. Sessions edit only inside theirs.
    Note /rbio at app.routes.ts:124 currently has NO canActivate guard — the RBIO home is
    UNAUTHENTICATED. Fix it here; it is a go-live security defect and S1 should not have to.

 8. CONTRACT REPORT (in conversation, NOT a file). Publish: the status-string registry, the
    transition table schema, the RBIO_STATUS_MASTER role-visibility contract, the list-endpoint
    request/response shape, the new role names, and the allocation table below. S1-S7 prompts
    will quote this verbatim, so it must be complete and unambiguous.

ALLOCATIONS: MySQL database/V57-V60, Oracle database/oracle/V55-V58, seeder @Order 20-23,
port 8092. Highest currently in use at c23b899: MySQL V56, Oracle V54, @Order 19.
Start your backend ONLY with: ./deployment/run-test-backend.sh 8092

BLOCKERS — do not guess; state the assumption and continue:
 - Do NOT invent RBIOS 2026 clause numbers. Model CLOSURE_CLAUSE_MASTER so it CAN carry two
   scheme versions (it is already scheme-versioned and date-bounded), but seed ONLY RBIOS_2021.
   FLAG WorkflowController.java:487-489's invented 16(5)/16(6) newIn2026 clauses as a
   citizen-facing legal-correctness defect. Do not extend them. S4 will remove them.
   Precedent to follow: S4 built the AA award ceiling and sub-judice block but left both
   config-OFF pending legal sign-off, so arming is a one-row config change rather than a release.
 - Role naming: if you disagree with the 4-new-roles call, say so BEFORE implementing.

