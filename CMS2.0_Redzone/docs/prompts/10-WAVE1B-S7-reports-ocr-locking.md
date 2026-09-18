# WAVE 1B / S7 — REPORTS, FILTER OPERATORS, DRILL-DOWN, OCR, DRAFT/AUTOSAVE/LOCKING

Starts when **any** Wave 1 session finishes and frees a port (six concurrent is the tested ceiling).
Port **8098**. MySQL **V91-V95**, Oracle **V89-V93**, @Order **48-51**.
Stories: UST613-628, 671-675, 777-778 (~40).

Assemble: BASE prompt → `03-WAVE1-shared-contract-block.md` (values S7 / 8098 / V91-V95 / V89-V93 /
@Order 48-51) → Wave 0 contract report → this file.

**Prerequisite:** blocker #5 (the 62-column BRD Format and Logic Sheet) — without it, build only the
column-registry mechanism. Also agree the `reports/access-roles` contract with **S1** before building
that part; if S1 hasn't landed it, do your OCR and draft/autosave/locking work first.

---

<BATCH: S7 — REPORTS, FILTER OPERATORS, DRILL-DOWN, OCR, DRAFT/AUTOSAVE/RECORD LOCKING>

*** THE HEADLINE FINDING: REPORT FILTER OPERATORS ARE COSMETIC. ***
The frontend has a full operator catalogue (report-builder.service.ts:30 —
EQUAL|BETWEEN|GREATER_THAN|LESS_THAN|LIKE|IN, with per-field-type gating at :107-112 that ALREADY
correctly excludes LIKE from PICKER fields, so UST619 is half-met), the 365-day cap and auto-cap
(report-builder.component.ts:31-32, 504-529) and IN-max-100 truncation (:33, :483-490).
BUT QueryCompiler.buildPredicates (:177-213) HANDLES ONLY `RANGE` AND IMPLICIT EQUALITY. BETWEEN,
GREATER_THAN, LESS_THAN, LIKE and IN ALL FALL INTO AN `else` THAT BECOMES cb.equal(...). The frontend
sends BETWEEN as the literal string "from|to" (component.ts:551), which the server compares as a
string. QueryCompiler.validate (:40-54) checks field allow-listing only — never the operator, never
the range. So UST616-619 is a SERVER REWRITE.
Mirror EVERY client-side rule server-side: UST617's sub-1-day rejection and >1-year auto-cap to 1
year from the From Date, UST618's LIKE-exactly-one-parameter and IN-max-100, UST619's partial-match
behaviour under Equal/IN (e.g. Draft IDs starting with a given prefix). A client cap is not a control.
UST671 needs Equal/Between/Greater than/Less than with a 1-year max and 1-day min, auto-capped;
UST672 needs the Complaint-Closed-On filter to return only non-null closed dates and to combine with
other filters.

REPORTS (UST613-615, 669-670, 777, 614):
 - ReportBuilderController.java:20-183 (GET /semantic-model, POST /compile, POST /execute, dashboard
   widgets max 3 at :68, off-hours email schedules :98-123) and SemanticModelRegistry.java:19-103
   exist — but the model is HARDCODED in a Java @PostConstruct (4 subjects, ~50 fixed filter phrases,
   8 group-bys), not DB-driven and not configurable. QueryCompiler caps at 5000 rows (:20) and 30s
   (:21).
 - UST613's 62 COLUMNS: QueryCompiler.executeList emits exactly TEN (:96-106 — complaintNumber,
   subject, status, priority, department, entityCode, createdAt, filedAt, resolvedAt, triageSignal).
   Gap is ~52 columns, and there is no "BRD Format and Logic Sheet" anywhere in the repo. Build the
   column-registry MECHANISM so columns are DATA; leave the 62-column assertion fixme until the sheet
   arrives. UST613 requires each column source data exactly per the defined Field Name mapping — that
   mapping is the missing document.
 - UST670 CONFIGURABLE ACCESS LIST: the frontend calls GET/POST/DELETE /api/v1/reports/access-roles
   (report-builder.service.ts:196-208) with a Super-Admin UI (report-builder.component.html:470-520,
   component.ts:673-720, route admin/report-access guarded to ADMIN/RBIO_ADMIN at app.routes.ts:183-186)
   BUT THE BACKEND DOES NOT EXIST — no endpoint, no ReportAccessRole entity, no table, and errors are
   swallowed at component.ts:248 so the screen silently no-ops. S1 ALSO TOUCHES THIS: they own the
   role/user side, you own report-side binding and enforcement. AGREE THE CONTRACT FIRST.
 - ENFORCEMENT TODAY IS HARDCODED AND SPOOFABLE: export denied to AA_ADMIN/CEPD_ADMIN
   (component.ts:118-131); server scoping is a hardcoded ADMIN/SENIOR bypass plus department equality
   (QueryCompiler.java:215-222) driven by TRUSTED X-User-Role / X-User-Department HEADERS
   (ReportBuilderController.java:52-53). That is a go-live security defect — and note commit 41d7670
   already established the correct pattern repo-wide by making the JWT authoritative over X-User-*
   headers in all four role guard aspects (token decoded FIRST, headers consulted only when the token
   yielded nothing AND cms.security.allow-dev-identity-headers is on). APPLY THAT SAME PATTERN HERE;
   this is the seventh instance of the class.
 - UST615/669 role lists (Secretary, Deputy Ombudsman, Ombudsman, Ombudsman Admin, CEPD Admin, AA
   Admin; CEPD Admin and AA Admin view-only; AA Secretariat full view+export and distinct from AA
   Admin) must be DATA in that access list, not code. CAUTION: UST615 lists Secretary as a report
   viewer while UST655 (S3) removes Secretary as an assignment target — FLAG the apparent
   contradiction, do not resolve it silently.
 - DRILL-DOWN (UST620-623): the NO-record pop-up UI is COMPLETE with exactly the required columns —
   Entity Name, NO Record ID, NO Record Created On, Nodal Office Name, NO Record Status, NO Record
   Last Modified On (report-builder.service.ts:89-96; modal report-builder.component.html:420-467;
   handler component.ts:628-660) — BUT GET /api/v1/reports/drill-down/no-records DOES NOT EXIST, so
   the pop-up always errors. Build it, loading only the records relevant to the clicked complaint.
   UST622 requires drill-down filtered by the SAME role/territory rules as the parent report and
   unreachable by any other path — enforce server-side. UST620's complaint drill-down is currently a
   route navigation (component.ts:601-624), not a pop-up preserving the originating filter context for
   return navigation. UST623 requires drill-down to load within the standard performance threshold and
   not block or slow the parent report view.
 - UST614 (values match live data at generation, no stale cached values beyond a defined T-1 cycle)
   and UST777 (three scope conditions applied exactly, no manual reconfiguration of base filters):
   no snapshot table, no as-of date and no staleness concept exist. Build deliberately and state the
   T-1 semantics you chose.
 - A SECOND report surface to reconcile: crpc-reports.component.ts:32-60 has 7 HARDCODED report
   configs (3-6 columns each) whose backend GET /api/v1/crpc/reports DOES NOT EXIST — it falls back
   to getMockData() (:118).

OCR (UST624-626, 778):
 - OcrController.java: POST /extract (multipart, PDF/JPEG/PNG/TIFF only at :62), GET /provider, and
   POST /extract-from-draft WHICH IS A STUB RETURNING AN EMPTY MAP (:36-47). Provider chain
   (Groq -> Gemini -> OpenAI -> Azure -> HuggingFace -> Paddle) at OcrExtractionService.java:38-64,
   rule-based PDF-text fallback at OcrController.java:76-85, 16 extracted fields at
   OcrPrompts.java:19-36. cms-paddle-ocr/ is a 3-file Python service and is NOT started locally
   (cms.ocr.paddle-ocr-url is empty, chain is groq).
   Pre-population WORKS: rbio-create-complaint.component.ts:453-487 assigns 14 OCR fields onto the
   form; also draft-assessment.component.ts:936-937, 1118, 1156 via applyOcrFields.
 - UST624 VISUAL DISTINCTION: NOT FOUND. OCR values are written into the same plain form fields with
   no per-field provenance flag or styling — only a global banner/dismiss exists
   (rbio-create-complaint.component.html:639-643). Add per-field provenance so pre-filled fields are
   visually distinguishable from manually entered ones.
 - UST625 override-wins is PARTLY met server-side for the ingest path only
   (EmailSyndicationApiController firstNonBlank(preferred, fallback), "Operator-keyed values win over
   OCR"). Extend it to this path so an overridden value saves as the DO's input, not the OCR output.
 - UST626 graceful degradation IS met (:483-486, draft-assessment :1124-1126, skipOcr at :490) and
   failures are log.error'd (OcrExtractionService.java:85-92) with ocrSkipReason="OCR_FAILED"
   persisted (EmailSyndicationApiController.java:315) — but there is no audit row. Add one, keep it
   non-blocking, and ensure a failed attempt never surfaces a blocking error to the DO.
 - UST778 ENGLISH-ONLY / NO HANDWRITING: OcrEligibilityService.java:1-70 is a fail-closed script and
   confidence gate (non-Latin -> VERNACULAR_MANUAL_ENTRY, low confidence ->
   LOW_CONFIDENCE_MANUAL_ENTRY, threshold cms.ocr.min-confidence-for-prefill:70 at :26). Its own
   comment at :14-16 states "this is not a handwriting classifier. No handwriting detection exists in
   this system." CRITICALLY, it is wired into EMAIL INGEST ONLY
   (EmailSyndicationApiController.java:254-323) — the direct POST /ocr/extract path used by the
   RBIO/CRPC UIs BYPASSES IT ENTIRELY, so operator uploads have NO language gate. Wire the gate into
   that path. Handwriting exclusion is BLOCKED on a classifier: implement the routing-to-manual seam,
   flag the detection gap, do not fake it.

DRAFT / AUTOSAVE / LOCKING (UST673-675):
 - UST674 STAFF SAVE-IN-DRAFT exists at only 3 of the 5 required milestones — Register
   (rbio-create-complaint.component.ts:524-538, physical-letter.component.ts:441) and Assessment
   (draft-assessment.component.ts:1413, reviewer-assessment.component.ts:325). MISSING at
   Conciliation, Forward and Final Decision (task-action.component.ts has no saveDraft at all).
   "Visible only to the user who saved it" is NOT implemented — there is no owner filter, and
   rbio-home.component.ts:242 shows all DRAFT rows to the role. Draft/In Progress labelling DOES
   exist (:334, deo-home.component.ts:442). Resuming must restore all in-progress field values.
   BUG TO FIX: rbio-create-complaint.component.ts:534 sets draftSaved(true) in BOTH the next AND
   error handlers — A FAILED SAVE REPORTS SUCCESS. Same family as physical-letter.component.ts:515,
   which fabricates a draft ID client-side when the server errors.
 - UST673 AUTO-SAVE every 2 minutes, configurable: NOT FOUND. No interval-driven save on any staff
   form (the only staff intervals are display refreshers — task-action.component.ts:187 TAT tick 60s,
   senior-dashboard.component.ts:71 reload 120s), and no cms.*.autosave property exists. The interval
   must come from SYSTEM_CONFIG (reuse the TimelineConfigService mechanism). Persist silently with no
   intrusive prompt, and DISCARD the auto-saved draft automatically on a successful save-and-proceed.
 - UST675 RECORD LOCKING: nothing exists — no record_lock table, no lockedBy/lockedAt, no pessimistic
   locks, no concurrency warning, no save prevention. Two users editing the same complaint is silent
   last-write-wins. Wave 0 added @Version to Complaint and surfaced the conflict as a distinct HTTP
   status — build the warning and save-prevention on that. PREFER optimistic detection over a lock
   table unless the story's "prevents the second user from saving until the first completes or
   releases the record" forces a lease; if it does, the lease NEEDS an expiry, or a crashed browser
   locks a complaint forever. State your choice and why.

