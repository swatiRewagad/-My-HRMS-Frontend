# QA Pass 4 — Session C findings

Scope: manual blocks 33-35, 37-47, 49-52, 54-57 (account/card numbers, wallet/BC, amounts &
compensation caps, uploads, declaration, draft lifecycle, non-maintainable closure, review preview,
duplicate detection, wizard tabs).

Environment verified at start of session (2026-09-24):
- cms-backend `dev-local` on **8092** — `/actuator/health` 200
- Angular dev server on **4202** — 200
- `GET /api/v1/config/upload-limits` →
  `{"maxFileSizeBytes":2097152,"maxTotalSizeBytes":26214400,"maxFileSizeMb":2,"maxTotalSizeMb":25,"maxFileCount":10}`
- `GET /api/v1/config/compensation-limits` → **404 Not Found** (no such endpoint)

---

## 0. STRUCTURAL CORRECTION TO THE BRIEF (affects every block below)

The brief describes a **7-step** wizard with Complaint Details at step 5, Review at step 6 and
Declaration & Submission at step 7. The running code has **6 steps**
(`file-complaint.component.ts:133` `totalSteps = 6`):

| Step | Screen | Source |
|---|---|---|
| 1 | Complainant details | `file-complaint.component.html:468` |
| 2 | Regulated entity details | `:606` |
| 3 | **Complaint details** — all account/card/wallet/amount/upload fields | `:661` |
| 4 | Representative authorisation | `:902` |
| 5 | **Declaration** (2 checkboxes) | `:1022` |
| 6 | **Review & Submit** (includes preview + submit button) | `:1054` |

So: blocks 33-45 are step **3**, block 46 is step **5**, blocks 52/56/57 are step **6**.
Block 56's "7 wizard sections + preview" is **6 sections**, with Review being the preview —
there is no separate 7th section. Manual block 56 therefore over-counts the tabs by one.

---

## 1. HARD NUMBERS: WHAT THE MANUAL ASSERTS vs WHAT THE CODE/CONFIG ACTUALLY DOES

**This is the top deliverable — for the BA and for legal.**

| # | Manual case asserts | Reality in the running system | Verdict |
|---|---|---|---|
| 1 | Upload limit is **2 MB** per file (block 45, lines 3415-3437) | 2 MB is the **current configured value**, served by `GET /api/v1/config/upload-limits` from `system_config`, held in `UploadLimitsService`. It is expected to move. | **Manual is a snapshot, not a rule.** Test reads the limit from the server. Per the standing ruling, no spec may pin 2 MB. |
| 2 | Upload total limit — manual is silent | Server says **25 MB total**, **10 files max**. The UI prints only the *total* (`Maximum Size of All Files: {{ maxTotalSizeMb() }}MB`, html:871) and never shows the per-file limit. | **Gap in the manual suite AND a UX gap** — a citizen is told 25 MB but is refused at 2 MB per file with no prior warning. See defect D-C-02. |
| 3 | Consequential-loss cap is **₹30 lakh** (block 43) | **Hardcoded** as the literal `3000000` in TWO places: `file-complaint.component.ts:1632` (`validateCompensationSought`) and `:1684` (`validateCurrentStep` step 3). No config key, no endpoint. `/api/v1/config/compensation-limits` is **404**. | **CONTRADICTS the standing ruling** that configured numbers must not be compiled in. Upload limits were remediated this way; compensation caps were not. See defect D-C-01. |
| 4 | Expenses/harassment cap is **₹3 lakh** (block 44) | **Hardcoded** as `300000` at `file-complaint.component.ts:1641` and `:1689`. Same finding as #3. | Same as #3. |
| 5 | Both caps are enforced at submission | Enforced **client-side only**, in the Angular component. No server-side rejection was found for either figure. | **Needs legal/BA confirmation**: a crafted request bypasses both statutory caps. See defect D-C-01. |
| 6 | Account/card number max length **100 chars**, with error message "Account number cannot exceed 100 characters." (blocks 33, 34, 35, 37) | The four inputs carry `maxlength="100"` (html:746, 759, 766, 773). The browser **silently truncates** at 100. The quoted error message **does not exist anywhere in the codebase**. | **The specified error message is unreachable.** Cap is real; message is fiction. See D-C-03. |
| 7 | "Name of Wallet" max **100 chars**, with error "Wallet name cannot exceed 100 characters." (block 39, line 2843-2850) | `walletName` input at html:799 has **NO `maxlength` attribute** and no length validation in `validateCurrentStep`. Unbounded. | **REAL DEFECT — the only one of the six text fields with no cap at all.** See D-C-04. |
| 8 | Transaction/Reference Number max **150 chars**, error "**Wallet name** cannot exceed 150 characters." (block 40, line 2938-2943) | `maxlength="150"` present (html:804); silent truncation. | Cap is real, message is fiction, **and the manual's own message text is a copy-paste error** (says "Wallet name" for the reference-number field). |
| 9 | Account/card number fields "cannot accept special characters / negative numbers / decimal values" (blocks 33-35, 37) | Plain `[(ngModel)]` text inputs. **No charset filter of any kind.** `!@#$`, `-500`, `1.5` are all accepted and submitted. | **CONTRADICTION — manual describes filtering that does not exist.** See D-C-05. |
| 10 | Wallet name "cannot accept negative numbers / decimal values" (block 39) | Same — no filter. Also questionable as a requirement: a *name* field rejecting digits is a doubtful rule the BA should confirm. | Contradiction + requirement query. |
| 11 | Amount fields "cannot accept alphanumeric / alphabetic / special / negative / decimal" (blocks 42, 43, 44) | **This one the code does honour**, but by *stripping* not by erroring: `onAmountInput` (ts:1585) does `value.replace(/[^\d]/g,'')`. Typing `12a` yields `12`; `-500` yields `500`; `1.5` yields `15`. No error message is shown. | **Behaviour is correct in spirit, wrong in the manual's letter** — the manual demands the ₹30/₹3 lakh error message be displayed for alphabetic input (lines 3165-3180, 3277-3299), which is impossible since letters are stripped before validation ever runs. See D-C-06. |
| 12 | Amount Involved has a max character count (block 42, lines 3097-3106) | The manual's own figure is **blank** ("accepts max ___ characters") — no requirement stated. The code applies no cap to `disputeAmount`. | **Manual case is unfinished.** Cannot be automated; nothing to assert. Deferred as under-specified. |
| 12a | — (not in the manual; found by automating blocks 42-44) | When an amount entry is **entirely** non-digits, the model is cleared but the **input is not** — `abcdef` stays visible in the rupee box with no error. One-way `[ngModel]` (html:832/841/850) only writes back on a *change*, and `'' → ''` is not one. `12a34b` → `1234` works because the value differs. | **REAL DEFECT the manual never asked about.** Display-only — the model is clean, so nothing invalid is submitted. See D-C-13. |
| 13 | Supported upload formats are **JPG, PDF, DOCX** (block 45, lines 3402-3411) | `ALLOWED_EXTENSIONS = ['.pdf','.doc','.jpg','.jpeg','.png']` (`file-validator.ts:36`); accept attribute `.pdf,.doc,.jpg,.jpeg,.png` (html:866); UI hint says "PDF, DOC, JPG, PNG". **`.docx` is NOT allowed; `.doc` and `.png` are.** | **DIRECT CONTRADICTION.** A citizen with a modern Word document (`.docx`, the default since Office 2007) is refused. See D-C-07 — highest citizen-impact finding in this session. |
| 14 | Error message on bad type is "**Unsupported file type**" (line 3411) and on oversize "**File size exceeds limit**" (3422, 3437) | Actual strings: `File type ".xyz" is not allowed. Allowed: PDF, DOC, JPG, PNG` and `File size (N.NMB) exceeds the 2MB limit.` (`file-validator.ts:45,55`) | Messages differ from the manual. Specs assert the real strings. |
| 15 | Closure letter / acknowledgement carries a **digital signature** and is **PKI compliant** (deferred block, lines 4137-4176) | Project ruling is the DSC is a **MOCK**. `public/pdf-signature.spec.ts` (UST97/D5) actively asserts downloaded PDFs must **NOT** claim a digital signature, and `file-complaint.component.ts:1570` stamps closure letters `SYSTEM-GENERATED LETTER — NOT A DIGITALLY SIGNED DOCUMENT`. | **The manual cases demand the exact opposite of the delivered-and-tested behaviour.** Not a test failure — a requirements conflict needing legal sign-off. See D-C-08. |
| 16 | Declaration & Submission is **step 7 of 7** (block 46, line 3438) | `totalSteps = 6` (`ts:133`); the declaration is `currentStep() === 5` (`html:1022`) and Review & Submit is step 6. There is no 7th screen. | **Manual/brief mis-numbers the wizard by one.** See §0. Specs drive step 5. |
| 17 | Declaration error is "**Response is mandatory.**" (block 46) | Actual string: `You must accept all declarations to proceed` (`ts:1708`), and it is **one** message for **two** independent declarations. | Message differs; the product's wording is the better one (it says what to do). The shared key is a real defect — see D-C-17. |
| 18 | Declaration (ii) affirms filing "within one year as per **clause 10(2)**" (block 46) | Present and correct: `html:1040` states "before the expiry of a period of one year" and cites `clause 10 (2)`. | **Matches** — the clause reference the whole limitation regime rests on is right. Asserted verbatim on the clause number. |
| 19 | Declaration text language — manual is silent | Heading is translated (`html:1024`); **both declaration bodies are hardcoded English** (`html:1029-1032, 1040`) on a portal serving English, Hindi and Punjabi. | **Gap in the manual AND a P1 finding.** The citizen legally affirms two statements they may be unable to read. See D-C-18. |
| 20 | Autosave interval — manual says only "auto saved", with no figure (block 47, lines 3513-3564) | **30 000 ms**, hardcoded: `startAutoSave()` → `setInterval(..., 30000)` (`ts:1912-1918`). Not in `SYSTEM_CONFIG`, not served by any config endpoint, not in `config.json`. | **Nothing contradicts, because the manual states no number — but the number is unreachable by configuration.** Retuning the interval is a frontend rebuild. Asserted behaviourally (an unprompted save occurs), never as the literal 30000. |
| 21 | Save confirmation is shown to the citizen (block 47: "the data entered should be saved") | Two confirmations exist and **neither is usable.** `.auto-save-text` appears then clears itself after **2 000 ms** (`onDraftSaved`, `ts:1897-1904`); the durable `.last-saved-text` lives in `.session-bar`, which the stylesheet sets to `display: none` (`scss:8-10`). | **Requirement not met.** The save itself works; the citizen is left with no lasting evidence of it. See D-C-23. |
| 22 | Draft retention period | Neither the manual nor the code states one: no TTL, no purge job, no expiry column consulted. `COMPLAINT_DRAFTS` rows holding full complainant PII persist indefinitely. | **Gap on both sides.** Needs a retention figure from the BA and legal — a DPDP storage-limitation question, not a test. See D-C-20. |
| 23 | How many drafts a citizen may hold | Manual is silent. The code enforces **exactly one per mobile number**: `ComplaintDraftController` upserts on `phone` (`:39-45`), so a second draft silently overwrites the first. | **Undocumented product rule with silent data loss attached.** A citizen with complaints against two entities cannot hold a draft for each. See D-C-21. |
| 24 | "On every screen/stage of the complaint form the save button is present" (block 47) | `button.btn-save-link` is present on **all six form steps and the eligibility stage** — seven stages. | **Matches the requirement**, once §0's six-step correction is applied. Asserted per stage, table-driven. |
| 25 | **The non-maintainability clause lists** (blocks 49/50/51). Manual names **7 for RBIO** — 10(1)(j), 10(1)(k), 10(1)(b), 10(1)(h), 10(1)(i), 10(2)(g), 10(1)(e) — and **3 for CEPC** — 10(2)(b)(i), 10(1)(h), 10(2)(a)(i). | `GET /api/v1/eligibility/questions` (scheme `RBIOS_2021`, 14 rows) serves **five distinct clauses in total**: `10(1)(g)`, `10(1)(j)`, `10(2)(a)`, `10(2)(b)(i)`, `10(2)(b)(ii)`. By department: RBIO can cite `10(1)(g)`, `10(1)(j)`, `10(2)(a)`, `10(2)(b)(i)`, `10(2)(b)(ii)`; CEPC can cite `10(1)(j)`, `10(2)(a)`, `10(2)(b)(ii)`. | **THE HEADLINE CONTRADICTION. Six of the manual's ten clauses do not exist in the product at all**: 10(1)(k), 10(1)(b), 10(1)(h), 10(1)(i), 10(2)(g), 10(1)(e). Conversely `10(1)(g)` and `10(2)(b)(ii)` are cited by the product and named by neither list. And the manual's CEPC `10(2)(a)(i)` is **not** the product's `10(2)(a)` — a sub-clause apart, which for a limitation refusal is a different legal basis. Needs the BA and legal, not a test. Asserted as *what the master serves*, per the standing decision that clause numbers await the business owner. See D-C-27. |
| 26 | The closure status the manual names: **"Portal Rejection"** (block 49) | **Zero occurrences** of `Portal Rejection`, `PORTAL_REJECTION` or `portal_rejection` across every `*.java`, `*.ts`, `*.html`, `*.json` and `*.sql` in the repository. No such status, enum value, or column value exists. | **The requirement names a status the product has never had.** Compounded by the fact that there is nothing for a status to be set *on* — no row is written (row 27). Either the term is stale vocabulary or the whole persistence path is unbuilt. See D-C-26. |
| 27 | The non-maintainable case reference the citizen is given | Format `NM-` + `Date.now().toString().slice(-8)` (`nextEligibility`, `ts:1354-1363`) — generated **in the browser**, never sent to any server. Proven: reaching the closure screen leaves **0 rows in `COMPLAINTS` and 0 in `COMPLAINT_MASTER`**, and no POST is issued. | **The manual treats this as a case number; it is a display string.** The last 8 digits of an epoch-millisecond value **wrap every ~27.8 hours**, so two refusals a day apart can collide; and because nothing is stored, a collision is undetectable and the reference cannot be looked up by anyone. No audit trail of a legal refusal exists. See D-C-25. |
| 28 | How many blocking messages tell the citizen which clause they were refused under | **1 of 9.** Only `filedWithRE`'s message contains `{{clause}}` (verified in the `en` bundle as served, not just in the master). The other eight blocking rows carry a `clauseReference` that is never interpolated into any sentence the citizen reads. | **Requirement in blocks 50/51 ("the message states the reason and the clause") is met for one question in nine.** Worse, `interpolateClause` (`ts:375-381`) *deletes* the placeholder when no clause is known, so the omission is invisible — the sentence always reads cleanly. See D-C-31, and D-C-28 for the null-clause row. |
| 29 | The FRC question in block 51 ("was a First Response received from the RE — **Yes** ⇒ non-maintainable") | No FRC question exists on the citizen portal. The nearest equivalent is `filedWithRE`, which blocks on **`no`** (`blockOn: 'no'`). "FRC" appears only in `auto-closure.component.ts:178` and `rbio-create-complaint.component.ts:87` — **officer** screens. | **The manual case targets the wrong portal and states the polarity backwards.** A tester following it would answer Yes, see the complaint proceed, and report a broken gate. The citizen gate is correct: you must have approached the RE first. Recorded so QA does not raise a false defect. |
| 30 | Where the document preview lives (blocks 52 and 57 both open with "on the complaint details **review** screen") | `button.preview-file` is on **step 3**, the upload field (`html:894`). The review screen (step 6) lists attachments as **names only** — a `.rs-row-full` per file with `f.name` and an icon (`html:1202-1207`) — with no preview, download or remove control. | **Requirement not met.** The one screen whose purpose is checking what you are about to submit cannot open any of it. See D-C-32. |
| 31 | What the preview is (block 52: "inline or modal pop up viewer"; block 57: "inline viewer") | `previewAttachment` (`ts:2061-2063`) is one line: `window.open(objectUrl, '_blank')`. **No viewer exists.** It is a browser tab. Verified: no `[role="dialog"]`, no `iframe`/`embed`/`object`, no overlay element is added to the page. | **Neither inline nor modal.** This single fact defeats four further cases both blocks ask for — a close control, zoom, a per-document download button, and screen-reader support — because all four belong to the browser, not the product. On a popup-blocking browser the citizen gets no preview and no error. See D-C-33. |
| 32 | Per-document download control (block 57, three cases) | **Does not exist anywhere in the portal.** The only download controls on this component are the acknowledgement PDF (`html:1277`) and the closure letter (`html:1317`) — neither is a citizen's own attachment. | **Unbuilt.** What block 57 describes is the browser's own PDF toolbar inside the tab `window.open` produced. See D-C-33. |
| 33 | The preview error message block 52 specifies: **"Preview not available for this file type."** | Does not exist in the portal. The sole `Preview not available` in the repository is `crpc/draft-assessment.component.html:231`, an **officer** screen. An unsupported file is refused by the *upload* validator and never becomes an attachment, so it never acquires a preview button — the case's precondition cannot be established. | **The requirement is satisfied, but not by the mechanism specified, and the message does not exist.** "Unreachable" not "broken" — the BA should know which, because the fix differs. See D-C-34. |
| 34 | Previewable file types (both blocks: "PDF, JPG, PNG, **DOCX**") | Attachable types are `.pdf .doc .jpg .jpeg .png` (`file-validator.ts:36`). **`.docx` cannot be attached, so it cannot be previewed.** | **Same contradiction as D-C-07**, surfacing again in this block. Not re-raised as a new defect, but carried as a `fixme` here so a reader of block 52/57 does not see the preview tests pass and conclude the requirement is met. |
| 35 | **How many parameters detect a duplicate.** Blocks 54/55 name **five**: Complainant Mobile / Email + Complainant **Name** + **Entity Name** + **Date of disputed transaction** + **Complaint Category** | **Two work, one works backwards, two are dead.** Measured against the running server: mobile ✅ (exact match, and wins the `matchedOn` report), email ✅ (case-insensitive), category ✅ (both sides resolve the name to `complaint_categories.id`, so they agree), **entity ❌ inverted** (row 36), **name ❌ never even sent** — `checkDuplicate` (`ts:2119-2124`) posts only `{phone, email, entityName, category, disputeDate}` — **date ❌ sent and then ignored** (it is on the wire; no server code reads `disputeDate`). | **The manual's "combination of five" is a combination of two.** Verified by probe: a *different complainant name* is still reported as a duplicate, and a transaction date seven years apart is still reported as a duplicate. The BA must decide whether name and date *should* narrow the match — if so, three of the five legs need **building**, not fixing. See D-C-35 and D-C-37. |
| 36 | The entity leg: "Entity Name … is used to detect the duplicate complaint" | **It is used backwards.** `resolveBankId` (`controller:321`) resolves the name to a `BANKS.id` and the query filters `c.bankId = :bankId` (`ComplaintRepository:164`) — but **the public filing path never writes `bank_id`**. `fileComplaint` copies `req.getBankId()` (`ComplaintService:133`) while the wizard sends `regulatedEntityId`, which the controller reads (`controller:120`) and never transfers to `bankId`; the entity survives only as free-text `entity_code`, which the query does not look at. Because an *unresolvable* name yields `bankId = null` and the query reads null as "any entity", the polarity inverts. Measured on one seeded live complaint filed against State Bank of India: entity **omitted → duplicate: true**; entity **"Bajaj Finance Limited" (not in `BANKS`) → duplicate: true**; entity **"State Bank of India" (the correct one) → duplicate: FALSE**. Population: **203 of 203** `filing_type='ONLINE'` rows have `bank_id` NULL; **13 of 8522** rows repo-wide have it at all. | **P1, and the most serious finding in Session C.** The control is *strongest* for a citizen who picks an entity `BANKS` has never heard of and *weakest* for one who picks a major bank — accuracy defeats detection. Reproduced end-to-end in the UI: choosing an entity in the wizard makes the popup **disappear** for a complaint that genuinely is a duplicate, and a second live complaint is filed with no warning at all. See D-C-35. |
| 37 | The entity master the check resolves against | `resolveBankId` iterates `bankRepository.findAll()` — the **`BANKS` table, 12 rows** (SBI, PNB, BOB, Canara, Union, HDFC, ICICI, Axis, Kotak, IndusInd, Yes, IDBI), matching on name **or** code. The portal offers **145** entities from `GET /api/v1/routing/entities/list`. | **133 of 145 entity names silently widen the check.** A second, independent narrowing that survives even if `bank_id` starts being written: an NBFC complainant's entity never resolves, so their duplicate check is entity-blind. Same 12-vs-145 master split already recorded for CEPC routing. See D-C-36. |
| 38 | The lookback window — the manual is **silent** on how long a complaint keeps blocking a re-filing | **90 days**, from `cms.duplicate-check.lookback-days` (`application.yml:162`; default `90` in `DuplicateCheckProperties:15`). **No endpoint exposes it**, unlike upload limits. Per standing ruling 1 the spec does not pin it: it bisects the boundary at runtime and logs `measured duplicate-check lookback window: 90 days (blocks at 90, releases at 91)`, asserting only that the boundary is sharp (exactly one day wide) and not absurdly short. | **Gap in the manual suite**, and a configuration gap: a citizen-visible window with no config endpoint, so no UI can display it and no spec may assert it as a constant. |
| 39 | Which statuses release the block — the manual is **silent** | **Three**, from `cms.duplicate-check.terminal-statuses` (`application.yml:163`): `closed`, `rejected`, `withdrawn`. Verified all three release the block, and that `pending` / `assigned` / `in_progress` / `resolved` / `escalated` all still block. | **Configured and correct**, with one point for the BA: **`resolved` still blocks.** A resolved-but-not-yet-closed complaint prevents a re-filing. Defensible, but it is a policy choice nobody has written down. |
| 40 | The wizard's own default complaint category | `validFormData` and `performSubmit` both default to **`'GENERAL'`** (`ts:2172`) — and **`GENERAL` is not one of the 10 rows in `complaint_categories`** (ATM/Debit Card, Credit Card, Internet Banking, Mobile Banking/UPI, Loan/Advances, Deposit Accounts, Pension, Remittance/Transfer, Insurance, Others). It resolves to `null`. | **The category leg is silently disabled for any complaint filed without an explicit category**, because `null` means "any category". Not citizen-facing today (category is mandatory on step 3), but the API default and the master disagree — and it changes which code path the control takes, which matters for anyone reading these tests. |
| 41 | "The new complaint **should be tagged as a duplicate complaint to the previous identified complaint**" (block 54, last case) | **Nothing is tagged, and there is nowhere for a tag to live.** `proceedDespiteDuplicate` (`ts:2159-2163`) sets a boolean and calls `submit()`; `performSubmit` builds a payload with **no duplicate marker of any kind** and drops `res.complaintNumber` — the only place the matched complaint's identity ever existed. Proven in SQL: **0 columns** in `COMPLAINTS` or `COMPLAINT_MASTER` match `%dup%`/`%parent%`/`%related%`, and **0 tables** match `%DUPLICATE%`/`%COMPLAINT_LINK%`. | **Unbuilt, structurally** — not a missing write but a missing place to write to. The server is never told the citizen was warned, so an officer triaging the second complaint cannot know a related one is live, which is the entire point of having warned the citizen. See D-C-37. |
| 42 | The popup's four strings in the citizen's language | `duplicate.title` / `.note` / `.cancel` / `.proceed`, all served from `GET /api/v1/i18n/translations/{locale}`. **Hindi translates all four. Punjabi translates none** — all four fall through to the English string, in a `pa` bundle that is otherwise populated with >100 keys. | **Same defect class as D-C-30** (the closure screen), and the same proof that it is not inevitable: Hindi is complete. A citizen who chose Punjabi is asked to make an irreversible decision — proceed, or cancel — in a language they did not choose. See D-C-38. |
| 43 | **How many sections the form navigation has.** Block 56 lists **seven**: Regulated Entity Name, Complaint Eligibility Checks, Complainant Details, Regulated Entity Details, Complaint Details, Authorised Representative, Declaration & Submission | **Six**, from `stepTitles` (`ts:134-141`): Complainant Details, Regulated Entity Details, Complaint Details, Representative Authorisation, Declaration, **Review and Submit**. And the six are **not a subset of the seven** — measured delta: the manual claims four that are absent (`Regulated Entity Name`, `Complaint Eligibility Checks`, `Authorised Representative`, `Declaration & Submission`) and the bar shows three the manual never names (`Representative Authorisation`, `Declaration`, `Review and Submit`). | **Two different models of the same journey.** The manual's first two entries are the **eligibility phase**, which is a different phase with no step bar at all (`html:419` gates it on `phase() === 'form'`); its last entry is **two** steps here; and the product's final step, *Review and Submit* — the very screen block 56 says the navigation is previewed on — appears nowhere in its list. A tester working block 56 literally would raise six false defects. See D-C-40. |
| 44 | Block 56 calls this a **"tabbed interface"** and asserts the citizen "is able to click on **ANY** tabs" | **Neither half holds.** It is `role="navigation" aria-label="Form progress"` (`html:439`) — asserted absent and confirmed: **0** `tablist`, **0** `tab`, **0** `aria-selected` in the rendered page. And clicking is deliberately restricted: `goToStep` (`ts:1868-1872`) refuses any index above `highestStepReached()`, so from step 3 exactly **3 of 6** items carry `.clickable`; from step 6, all 6 do. | **The "any tab" requirement is wrong, not unimplemented** — jumping from step 3 to Declaration would submit a form nobody filled in. Automated as the **guard** so the day it is loosened the test names what was lost. The tab-semantics half is a genuine accessibility gap but a *smaller* one than the manual implies: a progress nav is a legitimate pattern, it is simply not the pattern block 56 describes, so block 56's tab-selection cases have no product to verify against. See D-C-39. |
| 45 | The step labels in the citizen's language — the manual is **silent**, and this is the finding it never asked for | `form.step1_title` … `form.step6_title` **plus `form.step_label`** all exist in the served bundle, and **Hindi translates all seven** (measured: 0 of 7 fall through to English). The component renders the **hardcoded English `stepTitles` array** (`ts:134-141`) and a literal `Step {{ i + 1 }}` (`html:458`) instead. Separately, **all 7 are still English in the `pa` bundle**, which otherwise carries >100 keys. | **A wiring gap, and the cheapest fix in Session C's findings** — the strings are bought and paid for and simply not used. Distinct from D-C-30 / D-C-38, where the Punjabi strings genuinely do not exist. Note the ordering trap for whoever fixes it: wiring the component up today serves Hindi correctly and changes **nothing** for Punjabi, because the `pa` strings must be written too. See D-C-41. |

---

## 2. Coverage table

| Manual block | Lines | Cases | Spec file | Tests | Pass | Fixme | Notes |
|---|---|---|---|---|---|---|---|
| 33 Savings Acct No | 2307-2395 | 10 | `public/account-card-numbers.spec.ts` | 11 | 7 | 4 | 3 login/nav preamble cases not re-automated (covered by `otp-lifecycle`/eligibility specs) |
| 34 Loan Acct No | 2396-2482 | 10 | same | 11 | 7 | 4 | as above |
| 35 ATM/Debit Card No | 2483-2570 | 10 | same | 11 | 7 | 4 | as above |
| 37 Credit Card No | 2613-2698 | 10 | same | 11 | 7 | 4 | as above |
| — (net new) | — | — | same | 3 | 3 | 0 | multi-select interactions the manual never covers |
| 38 Wallet radio | 2699-2770 | 5 | `public/wallet-bc-reference.spec.ts` | 5 | 5 | 0 | full coverage |
| 39 Name of Wallet | 2771-2850 | 10 | same | 9 | 6 | 3 | includes the unbounded-length defect pair |
| 40 Txn/Ref Number | 2851-2943 | 10 | same | 8 | 6 | 2 | |
| 41 BC radio | 2944-3011 | 5 | same | 5 | 4 | 1 | the "additional BC fields" case is unbuildable as written |
| 42 Amount Involved | 3012-3107 | 11 | `public/amount-compensation-caps.spec.ts` | 11 | 9 | 2 | uncapped field; 2 of its manual cases are the blank max-char figure (deferred) |
| 43 Compensation — Consequential Loss | 3108-3223 | 11 | same | 17 | 13 | 4 | 11 shared field cases + 6 cap-boundary cases |
| 44 Compensation — Expenses/Harassment | 3224-3346 | 12 | same | 17 | 13 | 4 | as above |
| — (net new) | — | — | same | 3 | 3 | 0 | caps are mutually independent; harassment cap < loss cap; disputeAmount uncapped by either |
| 45 Uploads | 3347-3437 | 13 | `public/wizard-upload-documents.spec.ts` | 38 | 35 | 3 | 4 login/nav preamble cases not re-automated; every size computed from `GET /api/v1/config/upload-limits` |
| 46 Declaration & Submission | 3438-3512 | 8 | `public/declaration-submission.spec.ts` | 20 | 18 | 2 | the manual's "step 7" is really step **5** (§0); DPDP consent is a *different* screen and stays in `consent-dpdp.spec.ts` |
| 47 Save / autosave / draft lifecycle | 3513-3564 | 13 | `public/draft-autosave-lifecycle.spec.ts` | 47 | 40 | 7 | 2 login/nav preamble cases not re-automated; 3 cases (browser-close, session-expiry) are in the deferred bucket; **6 defects found, D-C-19 to D-C-24** |
| — (net new) | — | — | same | — | — | — | the draft endpoints' authorisation, the one-draft-per-phone rule, and the never-uploaded attachments are all absent from the manual |
| 49 Non-maintainable closure | 3576-3684 | 12 | `public/non-maintainable-closure.spec.ts` | 21 | 18 | 3 | blocks 49/50/51 are **one feature** and share one spec; the 7 existing eligibility specs all stop at the in-wizard `.block-note`, so the closure screen itself was uncovered; **7 defects found, D-C-25 to D-C-31** |
| 50 Non-maintainable message / entity types | 3685-3762 | 9 | same | 15 | 13 | 2 | 6 rows are verbatim duplicates of block 51 and were collapsed; the refusal is a **phase**, not the popup the manual describes |
| 51 FRC / clause-wise refusal | 3763-3824 | 9 | same | 12 | 10 | 2 | the manual's FRC cases target an **officer** screen and state the polarity backwards (hard number 29) |
| — (net new) | — | — | same | — | — | — | the SQL proof that no row is written, the epoch-wrap case id, the Punjabi passthrough, and the "1 of 9 messages cites its clause" count are all absent from the manual |
| 52 Document preview (+ close/remove/re-upload) | 3801-3861 | 12 | `public/review-document-preview.spec.ts` | 32 | 27 | 5 | blocks 52 and 57 are the **same feature** and share one spec — 7 of block 57's 12 cases are verbatim block 52; the preview is **not on the review screen** and is **not a viewer**; **3 defects found, D-C-32 to D-C-34** |
| 57 Document preview (+ download/label/multiple) | 4068-4108 | 12 | same | — | — | — | attributed to the same 32 tests; its download group is entirely unbuilt |
| — (net new) | — | — | same | — | — | — | the resumed-draft state (metadata without bytes) that neither block tests, plus the per-document `aria-label` identity assertions — **already inside the 32 above**, not additional to them |
| 54 Duplicate detection / message | 3917-4013 | 12 | `public/duplicate-detection-popup.spec.ts` | 27 | 25 | 2 | blocks 54 and 55 are the **same control** and share one spec; 2 login/nav preamble cases not re-automated; the existing `duplicate-check.spec.ts` (UST76) **mocks** the endpoint in all 3 of its UI tests and is not superseded; **4 defects found, D-C-35 to D-C-38** |
| 55 Duplicate popup / cancel / proceed | 4014-4020 | 10 | same | 18 | 17 | 1 | 6 of its 10 cases are verbatim block 54 and were collapsed; **Proceed was never tested by any existing spec** — `proceedDespiteDuplicate` (`ts:2159`) had zero coverage |
| — (net new) | — | — | same | — | — | — | the five-parameter measurement (which legs are live, dead, and inverted), the 90-day window bisection, the terminal-status matrix, the SQL proof that no duplicate link can be recorded, and the Punjabi passthrough are all absent from the manual |
| 56 Form section navigation | 4021-4067 | 8 | `public/wizard-step-navigation.spec.ts` | 13 | 13 | 0 | the manual's "tabbed interface" of **seven** sections is a progress **nav** of **six** (hard numbers 43, 44); its "click ANY tab" case is inverted into a guard, because the product refuses it by design; **3 defects found, D-C-39 to D-C-41** |
| — (net new) | — | — | same | 6 | 4 | 2 | the six-vs-seven delta measured in both directions, the absence of tab semantics asserted rather than assumed, the connector count, and the **seven unused translated step-label keys** — none of which the manual asks for |

**Run results (chromium, workers 1):**
- 2026-09-24: `account-card-numbers` + `wallet-bc-reference` = **52 passed, 22 fixme/skipped, 0 failed** (3.4 min).
- 2026-09-25: `amount-compensation-caps` = **41 passed, 10 fixme/skipped, 0 failed** (1.4 min).
- 2026-09-25: `wizard-upload-documents` = **35 passed, 3 fixme/skipped, 0 failed** (1.1 min).
- 2026-09-25: `declaration-submission` = **18 passed, 2 fixme/skipped, 0 failed** (0.5 min) — green first run.
- 2026-09-25: `draft-autosave-lifecycle` = **40 passed, 7 fixme/skipped, 0 failed** (2.5 min). Took three runs, and *both* earlier failure clusters turned out to be product defects rather than test faults — see the note below.
- 2026-09-25: `non-maintainable-closure` = **41 passed, 7 fixme/skipped, 0 failed** (1.4 min). One failure on the first run, and unlike block 47 it was **mine**: I had written the "every blocking question cites a clause" requirement *twice* — once as a passing assertion and once as the `fixme`. Standing ruling 2 puts the requirement in the `fixme` and reality in the passing test, so the passing one now asserts `['isComplainantSelf']` — which also makes it a regression guard on the set of clause-less refusals growing.

- 2026-09-25: `review-document-preview` (blocks 52+57) = **27 passed, 5 fixme/skipped, 0 failed** (0.7 min). Five failures on the first run, **all one harness artefact, none a product fault** — see the note below.

- 2026-09-25: `duplicate-detection-popup` (blocks 54+55) = **42 passed, 3 fixme/skipped, 0 failed** (0.9 min). Took five runs to stabilise. One failure was mine (a helper returning `status` as a field, called as a function); the other three runs failed on **two environment traps that both look exactly like flake and are not** — see the note below.

- 2026-09-25: `wizard-step-navigation` (block 56) = **17 passed, 2 fixme/skipped, 0 failed** (1.0 min). One failure on the first run, **mine**: `address` is a `textarea` (`html:604`), not an `input`, and I had written `input[name="address"]`. Worth naming because of how it reads — Playwright reports `element(s) not found`, which looks like a field missing from the step rather than a wrong tag in the selector. The concern that the eligibility-phase test would bounce to login did **not** materialise: `/public/file-complaint` renders the eligibility phase for an unseeded session, which is exactly the state that test needs.

On the blocks 54/55 spec, for whoever maintains it:
- **The mobile range fills up, and that makes the suite fail MORE the longer it lives.** Session C draws
  from `987653____` — 9000 numbers. This spec seeds ~40 complaints per run and nothing closes them, so
  the occupied share grows monotonically (measured: **374 of 9000** used, **124 of them live**). A draw
  landing on an occupied number starts a "this complainant has no history" test against one that has
  history. This presented twice and both times the obvious reading was "flake":
  1. Four tests in the lookback describe passed, failed on the next run with no code change, then passed
     in isolation — a *live* seeded complaint had been redrawn.
  2. After adding a liveness-only guard, `proceed closes the popup and files the complaint` failed with
     `Expected: 2, Received: 3` — the mobile had no *live* complaint but did have a **closed** one, left
     by the terminal-status tests, and the count assertions count every status.
  Resolution: `freshMobile()` rejects any mobile with **any** complaint history, verified by the same SQL
  the assertions use. **Do not relax it back to "no live complaint"** — a guard weaker than the assertion
  it protects only relocates the collision. If it ever throws after 40 draws, purge the old QA seeds;
  do not widen the range into another session's block.
- **HTTP 429 on an immediate re-run is the server, not the spec.** Anti-automation permits
  `velocity-threshold: 30` filings per `velocity-window-seconds: 60` (`application.yml:141-142`). This
  spec seeds ~40 complaints in under a minute, so *one* run fits the budget and two back-to-back do not:
  the second run's seeds return 429 and the failure surfaces on the seeding assertion, which makes it
  look like a broken test. **Leave a minute between runs.** Do not relax the `201` expectation, retry the
  seed, or cut the seed count — that assertion is what makes the 429 legible instead of yielding a run
  where half the duplicates were never seeded and every popup test "passes" because no popup was due.
- One run died with `worker process exited unexpectedly (code=3221226505)` — a Windows access violation in
  the browser process, unrelated to any assertion. It did not recur. Nothing was restarted.
- **The seeded draft's `entityName` is never read back.** `loadDraft` (`ts:1937`) reads only formData /
  eligibilityAnswers / declarations / attachmentMeta, and `getSelectedBankName()` (`ts:2414`) derives the
  name from `eligibilityAnswers['regulatedEntity']` against the loaded entity list. So the **only** way to
  make the wizard send an entity name is to seed that answer with a numeric id from
  `/api/v1/routing/entities/list`. Seeding `entityName` alone sends `''` and silently exercises the
  entity-omitted case while looking like the entity-supplied one — which would have hidden D-C-35 entirely.
- The earlier complaint is seeded through `POST /api/v1/complaints` (the **public** path), not
  `createTestComplaint` (the CEPC officer path). That is load-bearing: the defect under test is a mismatch
  between how the *public* path stores an entity and how the check reads it, so an officer-created row
  would measure the wrong thing.
- `checkDuplicate` is a **pre-flight, not a guard**: `submit()` (`ts:2106`) calls it, returns, and is
  re-entered by the callback with `duplicateCheckDone = true`. One click on a clean check therefore yields
  **two** `submit()` entries and exactly **one** check call, and Proceed yields no second check at all.
  Counting calls is meaningful; assuming one call per click is not.
- The popup's buttons are addressed by **class** — cancel `.btn-secondary`, proceed `.btn-primary`
  (`html:1334-1335`). Both labels come from the translation bundle, so matching on the English text would
  couple the tests to the `en` locale (and D-C-38 means Punjabi would still pass, for the wrong reason).

On the blocks 52/57 spec, for whoever maintains it:
- **A blob PDF popup reports `url() === ''` in HEADLESS chromium and the real `blob:` URL when HEADED.**
  This cost five apparent failures. Diagnosed rather than guessed: the same fixture gave
  `blob:http://localhost:4202/<uuid>` with `--headed` and `''` without, while PNG and JPG fixtures gave
  the blob URL in both, and `opener()` was truthy and the tab open in every case. Headless chromium
  ships no PDF plugin, so the tab has nothing to commit a navigation to. **The product opened the tab
  correctly every time.** Resolution: `openPreview` asserts only that a tab opened; the tests that must
  compare two previews use image fixtures, guarded by `blobUrlOf()` which fails loudly if anyone later
  points one at a `.pdf`. **Do not "simplify" that back to `expect(url).toMatch(/^blob:/)` on a PDF** —
  it passes headed and fails in CI.
- A throwaway diagnostic spec established the above and was deleted afterwards.
- Preview and Remove are icon-only buttons in the same `.file-chip`; address them by class, never by
  `button` ordinal.

On the blocks 49/50/51 spec, for whoever maintains it:
- **Every clause and entity comes from the live master or `/api/v1/routing/entities/list`; nothing is
  hardcoded.** `MANUAL_RBIO_CLAUSES` / `MANUAL_CEPC_CLAUSES` are the *manual's* claims, present only so
  the delta can be computed and reported — they are not expectations.
- The refusal is a **phase** (`phase() === 'non-maintainable'`), not a modal. There *is* a
  `role="dialog"` at `html:1319-1334`, but it is the duplicate-complaint popup and belongs to blocks
  54/55. A test asserting the closure is a dialog would find that one and pass for the wrong reason.
- `filedWithRE` is the blocking question used for every UI journey because it is
  `applicableEntityType: 'ALL'` — the only row that makes an RBIO and a CEPC journey comparable
  without re-treading the RE-window sub-fields that `eligibility-re-window.spec.ts` already owns.
- The SQL closure proof does **not** contradict `eligibility-re-window.spec.ts:719-781`, which asserts
  that no closure endpoint exists. That spec proves it by 404-ing three guessed URLs; ruling 3 wants the
  negative proven in SQL. These are the same finding at two levels.
- `.nm-label`/`.nm-value` are ordinal: Case ID is `.first()`, Date is `.nth(1)`. Adding a third pair to
  the card will silently shift the date assertions.

On the block-47 spec, for whoever maintains it:
- The run is ~2.5 min because one test waits out the real 30-second autosave timer
  (`test.setTimeout(120000)`). That is deliberate: faking the clock would prove only that
  `setInterval` exists, not that a save reaches the server unprompted.
- **29 of 41 tests failed on the first run, all on `expect('.last-saved-text').toBeVisible()`.** The
  element resolved and read `Last saved at 12:24 pm` — and was `hidden`, because `.session-bar` is
  `display: none` (`scss:8-10`). That is D-C-23, not a selector mistake. The gate was rewritten to
  wait on the draft `POST` response, which is both stable and honest about what "saved" means.
- One test then failed expecting the server draft row to be gone after Delete. It is not: that is
  D-C-24. The single test became five (list-removal passes, "the server copy survives" passes, proper
  deletion is a `fixme`, plus the server-only-draft pair).
- A throwaway diagnostic spec was used to establish both of those and was deleted afterwards.

Two notes on the amount spec, for whoever maintains it:
- Cap discovery is a binary search costing ~28 probes per field. Running it inside every test blew the
  60s per-test budget on the first attempt, so the measurement is memoised per worker
  (`measuredCaps`) — sound, because a cap is a property of the bundle and not of the page.
  **Do not "simplify" it to `expect(cap).toBe(3000000)`**; see D-C-01 and standing ruling 1.
- One run reported all 51 tests as skipped with `ERR_CONNECTION_REFUSED` on 4202. That was the shared
  dev server mid-recompile (a concurrent session touched `src/**`), not a product or test fault. Nothing
  was restarted; a `curl` confirmed 4202 back at 200 and the re-run was clean.

## 3. Defects and contradictions

Severity: **P1** = citizen-blocking or statutory risk; **P2** = real defect, workaround exists;
**P3** = documentation/requirement defect (the manual is wrong, not the code).

### D-C-01 — P1 — Compensation caps are hardcoded in the Angular bundle and enforced client-side only
- **Where:** `file-complaint.component.ts:1632` and `:1684` (₹30 lakh as `3000000`);
  `:1641` and `:1689` (₹3 lakh as `300000`). Four literals, two per cap, duplicated between the
  live `onAmountInput` validator and the step-3 gate.
- **Why it matters:** these are **statutory award limits**. The project already ruled that
  configured numbers must not be compiled in and remediated the upload limit accordingly
  (`UploadLimitsService` + `GET /api/v1/config/upload-limits` + a source-scan guard). The
  compensation caps got no such treatment: there is no config key, and
  `GET /api/v1/config/compensation-limits` returns **404**. When the scheme retunes a cap, the fix
  is a frontend rebuild and redeploy.
- **Second half of the defect:** no server-side enforcement of either figure was found. A crafted
  POST bypasses both caps. Needs BA/legal confirmation of whether the server is *supposed* to
  refuse, or whether the cap is advisory at filing and binding only at award.
- **Related memory:** "the statutory award cap is BUILT but config-gated and currently OFF pending
  legal sign-off" — that is the AWARD-side cap. This finding is about the FILING-side cap the
  citizen sees, which is a different, hardcoded code path.

### D-C-02 — P2 — The per-file upload limit is never shown to the citizen
- **Where:** `file-complaint.component.html:871` prints only
  `Maximum Size of All Files: {{ uploadLimits.maxTotalSizeMb() }}MB` → "25MB".
- **Behaviour:** the citizen is told 25 MB, then refused at 2 MB per file with
  `File size (N.NMB) exceeds the 2MB limit.` *after* choosing the file. The 10-file count limit is
  likewise unannounced until breached.
- Not in the manual suite at all; found while automating block 45.

### D-C-03 — P3 — The documented over-length error message for account/card numbers cannot ever appear
- Manual blocks 33/34/35/37 all demand `"Account number cannot exceed 100 characters."`
- The four inputs use the native `maxlength="100"`, which truncates silently; the 101st character
  never reaches Angular, so no validator can fire. The string exists nowhere in the codebase.
- Either the manual should say "input stops at 100 characters" (matching delivered behaviour), or
  the product should switch to a validated cap that can report. **BA decision needed.**

### D-C-04 — P2 — "Name of Wallet" has no length limit at all
- **Where:** `file-complaint.component.html:799` — no `maxlength`, and `ts:1678` validates presence
  only.
- **Why it stands out:** every comparable field on the same screen has a cap —
  `creditCardNumber` (746), `savingsAccountNumber` (759), `loanAccountNumber` (766),
  `atmDebitCardNumber` (773) at 100, and its own row-neighbour `transactionRefNumber` (804) at 150.
  walletName is the only one with none. Reads as an omission.
- **Proven:** `wallet-bc-reference.spec.ts` "the unbounded wallet name currently accepts a value far
  beyond the documented limit" **passes today** with a 500-character value.
- **Fix is one attribute** (`maxlength="100"` on html:799). NOT applied: `src/**` is shared with
  Sessions A and B tonight and a template edit breaks their dev server (ruling 6).

### D-C-05 — P3 — The charset restrictions in blocks 33-35, 37, 39, 40 describe validation that does not exist
- Manual asserts each of the six text fields "cannot accept special characters / negative numbers /
  decimal values". All six are plain `[(ngModel)]` text inputs with no pattern, no filter, and no
  per-character validator. `!@#$%`, `-500` and `1.50` are accepted and submitted.
- Written as `test.fixme()` asserting the REQUIRED behaviour (ruling 2), 14 tests across the two
  specs. **Do not resolve these by asserting that anything is accepted.**
- Note for the BA: for *account numbers* the restriction is reasonable and worth building. For
  *Name of Wallet*, "cannot accept negative numbers or decimals" applied to a **name** field is a
  doubtful requirement and should probably be struck from the manual instead.

### D-C-06 — P3 — Blocks 42/43/44 demand the compensation-cap error for alphabetic input, which cannot occur
- Manual lines 3165-3201 and 3277-3323 specify that typing letters into an amount field displays the
  ₹30 lakh / ₹3 lakh cap message.
- That is a **category error in the test case**. `onAmountInput` (ts:1585) strips non-digits *before*
  either validator runs, so "abc" becomes `''` — there is no amount left to exceed a cap. The two
  requirements ("reject letters" and "show the cap error") cannot both be satisfied by the same input.
- The substance of the restriction IS honoured: nothing but digits reaches the model. Only the
  specified *mechanism* is impossible. The manual should say "non-digits are discarded"; the cap
  message belongs only to the over-cap numeric cases, which are automated and pass.

### D-C-12 — P2 (BA decision) — A decimal amount is silently changed by a factor of 100
- `onAmountInput` **strips** rather than rejects, so the decimal separator is discarded and the digits
  close up: a citizen typing `1.50` gets `150`, and `-500` becomes `500`.
- The only on-screen signal is the amount-in-words line ("one hundred fifty rupees"), which the citizen
  must notice and read. There is no error, no warning, and nothing blocks the step.
- For a **compensation claim** a hundredfold error in the citizen's favour or against it is material.
- Asserted as OBSERVED rather than `fixme`'d, deliberately: "reject the input" vs "strip and normalise"
  is a BA/UX decision, not a self-evident bug. **BA ruling needed.** If the ruling is "reject", the
  three `a decimal point is discarded…` tests invert and the handler needs a guard.

### D-C-13 — P2 — When every character is stripped, the input keeps the rejected text on screen
- **Found by automation**, not in the manual. Typing `abcdef` or `!@#$%^` into an empty amount field
  leaves the text **visible in the box** while `formData[field]` is correctly `''`.
- **Cause:** html:832/841/850 bind ONE-WAY (`[ngModel]` + `(ngModelChange)`), so Angular rewrites the
  DOM only when the model value *changes*. From an empty field, `'' → ''` is not a change, no writeback
  occurs, and the rejected characters survive.
- `12a34b` → `1234` works because the result *differs* from the previous value. This is the **same
  regression the comment at ts:1581-1584 claims to have fixed** — that fix cured the partial-strip case
  and left the total-strip case behind.
- **Impact:** the citizen sees letters in a rupee field with no error, the amount-in-words line and the
  Review screen both show nothing, and there is no indication which is authoritative. The model is
  clean, so nothing invalid can be submitted — this is a display defect, not a data-integrity one.
- **Proven three ways** in `amount-compensation-caps.spec.ts`: a `fixme` per field asserting the
  required behaviour (6 total), a passing companion showing the model *is* clean, and a passing
  discriminator (`12!@34` → `1,234`) that localises the fault to the writeback so nobody rewrites
  `onAmountInput` itself.
- **Fix is one character** — `[(ngModel)]` on the three inputs, or an explicit
  `ngModel.control.setValue()` in the handler. NOT applied: `src/**` is shared with Sessions A and B
  (ruling 6).

### D-C-07 — P1 — `.docx` is refused, while `.doc` and `.png` — which the manual never names — are accepted
- **Manual** (lines 3402-3411, three separate cases): supported formats are **JPG, PDF, DOCX**.
- **Product:** `ALLOWED_EXTENSIONS = ['.pdf','.doc','.jpg','.jpeg','.png']` (`file-validator.ts:36`),
  the same list in the `accept` attribute (`html:865`), and the on-screen hint "Support formats: PDF,
  DOC, JPG, PNG" (`html:869`). `.docx` is refused with
  `File type ".docx" is not allowed. Allowed: PDF, DOC, JPG, PNG`.
- **Why this is the highest citizen-impact finding in the session:** `.docx` has been Word's default
  format since Office 2007. The typical citizen attaching a written complaint is turned away, and the
  formats that *are* accepted include two the manual never mentions.
- **BA/product decision needed**, one of: add `.docx` to the allow-list (and to the server's sniffer),
  or correct the manual and the citizen-facing guidance. The current state fails whichever way the
  requirement is read.
- Both a `fixme` (asserting the manual's requirement) and a passing companion (recording today's
  refusal) are in `wizard-upload-documents.spec.ts`.

### D-C-14 — P3 (needs disambiguation) — "does not accept blank file" is unenforced and ambiguous
- Manual line 3416 requires that a **blank file** be refused. `validateFile` has no minimum-size
  check, so a 0-byte `.pdf` passes every gate — allowed extension, allowed MIME, under every limit.
- **The requirement is ambiguous**, which is why this is not written as a `fixme`: a 0-byte file is
  checkable and unchecked; "a valid PDF with no content" needs content inspection nobody has
  specified. The spec asserts the observed acceptance and names the question.
- **BA to clarify** which is meant before anything is built.

### D-C-15 — P3 — Manual's "2.1 MB" case contradicts itself in its own expected result
- Line 3433 reads "the field **should accept** file with 2.1 MB file size **and** error message 'File
  size exceeds limit' should be displayed". It cannot do both. The error is plainly the intent —
  copy-paste from the preceding accept case.
- Automated as a refusal, expressed as 105% of the configured limit rather than as 2.1 MB.

### D-C-16 — P2 — One bad file in a batch silently discards every valid file chosen with it
- **Found by automation**; not in the manual.
- **Where:** `onFilesSelected` (`ts:1990`) and `onFileDrop` (`ts:2018`) call `validateFileSet` FIRST
  and `return` on failure. `validateFileSet` loops the batch and **returns on the first invalid
  member** (`file-validator.ts:76-77`). So selecting four documents with one over the per-file limit
  attaches **none** of them.
- **The intent was the opposite:** the per-file loop that follows (`ts:1998-2008`) `continue`s past a
  bad file so the good ones survive. For validity failures that loop is **unreachable — dead code.**
- **Citizen impact:** the single error names only the size problem. Nothing says the other three files
  were dropped, and the file list stays empty, so a citizen who does not re-check believes all four
  are attached.
- Asserted as OBSERVED (batch-reject) with a `fixme` carrying the required behaviour and a passing
  control proving multi-select itself works. "Reject the batch" vs "keep the valid ones" is a UX call,
  but the dead `continue` is evidence of which was intended.
- **Fix:** validate per file before the batch gate, or have `validateFileSet` report per-file instead
  of returning on first failure. Not applied — `src/**` is shared (ruling 6).

### D-C-17 — P2 — One error message serves two independent legal declarations
- **Where:** `validateCurrentStep` step 5 is a single line (`file-complaint.component.ts:1707-1708`):
  `if (!this.declarationChecked || !this.declaration2Checked) this.validationErrors['declaration'] =
  'You must accept all declarations to proceed';`
- Two checkboxes, two *separate* statutory assertions — (i) the information is true and nothing is
  concealed, (ii) the complaint is filed within one year per clause 10 (2) — share one
  `validationErrors` key and one message rendered once for the whole screen (`html:1046`).
- **Citizen impact:** ticking (i) and missing (ii) produces "You must accept all declarations" with
  nothing indicating *which*. Every other field on this wizard marks its own error; this screen does
  not. On a screen whose whole purpose is a per-statement affirmation, that is the wrong place to
  economise.
- Automated as observed (the error is a single node and names neither `(ii)` nor `10 (2)`), with a
  `fixme` asserting the required per-checkbox error. Fix is a second error key plus a `.field-error`
  span inside each `.decl-item`. Not applied — `src/**` is shared (ruling 6).

### D-C-18 — P1 — The statutory declarations are hardcoded English on a trilingual portal
- **Where:** `html:1024` renders the heading through `| translate` (`form.decl_heading`), but **both
  declaration bodies are literal English in the template** (`html:1029-1032` and `html:1040`). No
  translation key exists for either.
- The portal serves **English, Hindi and Punjabi**, and the project already treats consent copy as
  requiring localisation: `consent-dpdp.spec.ts:105` asserts the login DPDP notice comes from
  translations precisely so a citizen consents in their own language.
- **Why this is P1 and not cosmetic:** these are not labels. They are the two statements the citizen
  legally affirms — a statement of truth (which carries consequences if false) and the clause 10 (2)
  limitation affirmation on which the whole maintainability regime rests. A Hindi- or Punjabi-only
  complainant is required to tick a box asserting something they cannot read. Consent to a statement
  one cannot read is questionable consent, and a complaint rejected as non-maintainable on a
  limitation ground the complainant never understood is an appealable grievance in itself.
- The localised heading directly above the English bodies is the tell that this is an omission, not a
  decision.
- Automated as a `fixme` asserting the body must be bound to a translation key. The test checks for
  the **absence of the inline English**, not for translated copy — the copy is legal text and nobody
  on the QA side should invent it. **Needs a legal/BA-supplied Hindi and Punjabi translation**, then
  three keys and a template change.

### D-C-19 — P2 — Refreshing the wizard on its own URL destroys the draft it just saved
- **Where:** `ngOnInit` (`ts:796-817`). When the URL carries neither `?resume=true` nor `?draftId=`,
  the else branch at `ts:804-814` **deletes** `cms_complaint_draft`, `cms_draft_id` and
  `cms_draft_saved_at` from sessionStorage.
- The manual requires (line 3555): *"Post browser refresh, the details entered by the user should be
  auto saved."* The citizen is on `/public/file-complaint` — no query string, because that is the URL
  the wizard is normally entered on — presses F5, and the local draft is wiped on the way back in.
  They land on step 1 of an empty form.
- **The saving grace, which is also the proof that this is a bug and not a policy:** the *server*
  copy survives, because only sessionStorage is cleared. So the data is not actually lost — it is
  merely unreachable from the screen the citizen is looking at. A treatment that discarded the draft
  deliberately would have cleared the server row too.
- Automated as: a passing test on the resume URL (works), a `fixme` on the bare URL asserting the
  manual's required behaviour, a **passing companion** proving the wipe is real (so the defect is a
  recorded fact, not an inference from a skipped test), and a passing test proving the server copy
  outlives the local wipe.
- Fix is small — on entry, load the local draft when one exists rather than clearing it — but it is a
  `src/**` change and so not applied (ruling 6).

### D-C-20 — P1 — The draft endpoints have no authentication and no ownership check at all
- **Where:** `SecurityConfig.java:125` makes `/api/v1/complaints/drafts/**` `permitAll`, and
  `ComplaintDraftController.java` performs **no ownership check on any verb** — not on read by id,
  not on read by phone, not on delete.
- **What a draft holds:** the complainant's full name, mobile, email, postal address, account or card
  numbers, the amount in dispute and the complaint narrative. Asserted in SQL rather than assumed.
- **Proven with no citizen session whatsoever** (three passing tests):
  - `GET /api/v1/complaints/drafts/{draftId}` returns the whole draft including the PII.
  - `GET /api/v1/complaints/drafts?phone=98765XXXXX` returns it too — so the store is
    **enumerable by mobile number**, and mobile numbers are ten digits.
  - `DELETE /api/v1/complaints/drafts/{draftId}` succeeds, verified gone in SQL.
- **The discriminator that makes this a defect rather than a design choice:** the *same* PII on the
  submitted-complaint list is refused. `GET /api/v1/complaints?phone=...` returns **401
  SESSION_EXPIRED** (`ComplaintApiV1Controller.java:336-364`, which also increments an anomaly
  counter). Citizen authorisation exists, is implemented, and was simply never applied to drafts.
  That test passes and is the strongest single line in this block.
- Two `fixme`s carry the required behaviour: 401 on a read with no session, 403 on a delete of a draft
  belonging to another mobile number. Precedent for both is `tracking-authz.spec.ts:29-45`.
- **Also unresolved (hard-numbers row 22):** there is no retention period. These rows persist
  indefinitely, which is a DPDP storage-limitation exposure on top of the access one.

### D-C-21 — P2 — A citizen may hold only one draft, and the second silently destroys the first
- **Where:** `ComplaintDraftController.java:39-45` upserts on `phone`. A second `POST` for the same
  mobile overwrites the existing row.
- Neither the manual nor any BRD states this rule. It is visible only in the SQL: after drafting a
  complaint against one entity and then another, `COMPLAINT_DRAFTS` holds **one** row, carrying the
  second entity. The first draft is gone, with no warning and no confirmation prompt.
- A citizen with grievances against two regulated entities — not an exotic case — cannot prepare both.
- Automated as observed (one row survives, and it is the second) plus a `fixme` asserting a draft per
  complaint. **Needs a BA ruling** on whether the limit is intended; if it is, the product must at
  minimum warn before overwriting.

### D-C-22 — P1 — The citizen wizard never uploads the attachments it collects
- **Where:** `performSubmit` (`ts:2152-2208`) builds the registration payload and **never calls
  `uploadAttachments`**. `complaint.service.ts:40-45` defines it; its only caller is
  `complaint-form.component.ts:69` — the *legacy* form, not the wizard.
- So the upload step of the six-step wizard validates the files (type, size, count — all of block 45's
  behaviour is real and tested), records their **names** in the draft, shows them as chips, and then
  drops every one of them on submit. The complaint is registered with no evidence attached.
- Proven by a passing test that installs a `page.on('request')` listener over a clean submission and
  asserts **no request to any attachment endpoint is made** — an absence, which is the only honest way
  to state this.
- **Why P1:** under the Scheme the evidence is often what makes a complaint maintainable. A citizen who
  attached a bank statement, saw it listed, and submitted has every reason to believe it was filed.
  The officer receives nothing and cannot know anything is missing.
- Carried as a `fixme` asserting the attachments are uploaded on submission. This is a feature gap, not
  a one-line fix, and belongs with the phantom-endpoint family of findings.

### D-C-23 — P2 — The save works; the confirmation of it is invisible
- **Where:** two confirmations exist and neither reaches the citizen.
  - `.auto-save-text` is set on success and **cleared by `setTimeout(..., 2000)`**
    (`onDraftSaved`, `ts:1897-1904`).
  - `.last-saved-text` — the durable "Last saved at HH:MM" stamp, and the only non-transient one —
    lives inside `.session-bar`, which `file-complaint.component.scss:8-10` sets to
    **`display: none`**. The hidden bar also contains the draft badge and a second save button.
- **How this was found, which is the evidence:** 29 of 41 tests failed on the first run of this spec,
  all on `expect('.last-saved-text').toBeVisible()`. The element resolved and its text read
  `Last saved at 12:24 pm`. It was `hidden`. An element that exists, holds the right value, and cannot
  be seen is the defect stated as precisely as it can be.
- The manual requires (block 47) that the citizen see their data was saved. Two seconds of a badge is
  not that, and the thing built to be that is switched off in CSS.
- Automated as: a passing test that the top save control is **never visible**, and a passing test that
  the durable stamp is hidden while `.auto-save-text` vanishes. No `fixme` — the required behaviour
  ("show a lasting confirmation") is a design decision, not a single assertion.
- Whether `.session-bar` was hidden deliberately (it also holds a session clock) or by accident needs
  a one-line answer from whoever wrote it. Either way the save confirmation went with it.

### D-C-24 — P1 — The server draft is write-only: it cannot be seen, resumed, or deleted
- Two bugs in `complaint-history.component.ts` combine so that the server-side draft — the copy that
  survives everything, per D-C-19 — is unreachable from the UI.
- **(a) The list clobbers the server drafts it just fetched.** `loadComplaints` (`:74-130`) fires
  `GET /api/v1/complaints` and `getDrafts()` concurrently. `finalizeLoad` (`:162-169`) does
  `complaints.set(records)`, and it is called from the complaints subscription's `next:` **and** its
  `error:` — so whichever way that call lands, it overwrites whatever the drafts subscription had
  already merged in. On this environment the complaints call returns **401 SESSION_EXPIRED** (retried
  3× by `error-handler.interceptor.ts:13`), so the `error:` path runs and the list ends up empty
  despite a live server draft row. Proven: `tr.draft-row` count 0, `btn-resume` count 0,
  `readDraftRows(mobile)` length 1.
- **Consequence:** the row My Complaints displays is *always* the local one. `getLocalDraft()`
  (`:135-156`) synthesises it with the literal id `'local'` — the diagnostic showed `DRAFT-LOCAL`
  every time. A citizen on a new device or after a cleared session sees no draft at all, which
  defeats the entire point of saving one server-side.
- **(b) Delete does not delete.** `deleteDraft` (`:187-201`) branches on that `'local'` id and, for it,
  **only clears sessionStorage** (`:189-194`). `complaintService.deleteDraft` is never called. The row
  disappears from the list, the citizen believes the draft is gone, and the server row — with the full
  PII of D-C-20 — remains. Proven in SQL: after Delete, one row survives and still contains the text
  typed before deleting.
- **Why P1:** (b) is a DPDP problem, not a UX one. A citizen exercising what they reasonably read as
  erasure gets none, and the surviving row is readable by anyone, unauthenticated, per D-C-20. The two
  defects compound: the data cannot be reached by its owner and can be reached by everyone else.
- Automated as five tests: list-removal passes (the visible behaviour is as the manual describes),
  "the server copy survives the deletion" passes, "the server-only draft is not listed and cannot be
  resumed" passes, and two `fixme`s carry the required behaviour — Delete removes the server row, and
  a server-held draft is listed and resumable.

### D-C-25 — P1 — A non-maintainable refusal is recorded nowhere, and its "case id" is a browser clock reading
- Blocks 49/50/51 describe an outcome with a case reference, a date, a closure letter and a status. The
  first three are rendered; **nothing is persisted.**
- **Observed:** `nextEligibility` (`ts:1354-1363`) sets
  `nonMaintainableCaseId = 'NM-' + Date.now().toString().slice(-8)` and then `phase.set('non-maintainable')`.
  No HTTP call accompanies it. Proven two ways: a `page.on('request')` listener records no POST while the
  card renders, and SQL shows **0 rows in `COMPLAINTS` and 0 in `COMPLAINT_MASTER`** for the mobile
  afterwards. (Both tables are counted because the schema has two unrelated complaint stores.)
- **Two independent problems.** (a) *No audit trail.* A statutory refusal — the citizen is told their
  complaint is not maintainable under a named clause — leaves no trace on any server. It cannot be
  reviewed, counted, appealed against, or produced if the citizen disputes it. The closure letter the
  citizen holds is the only artefact in existence, and it was generated client-side.
  (b) *The reference is not unique.* The last 8 digits of an epoch-millisecond value roll over every
  100 000 000 ms — **about 27.8 hours**. Two refusals a day apart can be issued the same `NM-` id, and
  since nothing is stored there is no constraint to catch it. The spec asserts uniqueness across two
  refusals in the same run (which passes, being seconds apart) and records the wrap as the real risk.
- **Why P1:** a legal determination with no record. Also blocks D-C-26 — there is nothing for a status
  to be set on.
- Automated as: the SQL both-tables-empty proof (passes), the no-POST proof (passes), "no complaint
  number is issued" (passes, with SQL), and a `fixme` requiring the determination be persisted.

### D-C-26 — P2 — The closure status "Portal Rejection" does not exist anywhere in the product
- Block 49 requires the complaint be marked with status **"Portal Rejection"**.
- **Observed:** searched every `*.java`, `*.ts`, `*.html`, `*.json` and `*.sql` in the repository for
  `Portal Rejection`, `PORTAL_REJECTION` and `portal_rejection` — **zero matches.** No enum constant, no
  status column value, no translation key, no seed row.
- Cannot be a simple naming drift, because per D-C-25 there is no row to carry a status at all. Either
  the term is vocabulary from a superseded design, or the entire persistence path for a refusal is
  unbuilt and this requirement describes it. **The BA must say which**, since the fix differs
  completely: rename a constant, or build the path.
- Automated as a passing test that asserts the status is absent from what the product can produce, so
  the gap is a recorded fact rather than an impression.

### D-C-27 — P1 (requirements conflict, needs legal) — Six of the ten clauses the manual says a refusal may cite do not exist
- See hard number 25 for the full lists. In short: the manual names **7 RBIO + 3 CEPC clauses**; the live
  master serves **5 distinct clauses in total**, and **six of the manual's ten are absent**: 10(1)(k),
  10(1)(b), 10(1)(h), 10(1)(i), 10(2)(g), 10(1)(e). Two the product *does* cite — `10(1)(g)` and
  `10(2)(b)(ii)` — appear in neither manual list.
- **The subtlest item is not an absence.** The manual's CEPC clause `10(2)(a)(i)` versus the product's
  `10(2)(a)` differ by a sub-clause. For a limitation refusal that is a different legal basis, not a
  typo, and a citizen refused under the wrong one has been refused on a ground that does not apply.
  Normalising whitespace before comparing (which the spec does) does *not* make these equal, deliberately.
- **Not ruled on.** Per the standing decision recorded in `eligibility-master.spec.ts:23-32` and three
  other spec headers, clause numbers are the business owner's call pending legal. The spec therefore
  asserts **what the master serves** (so a silent reseed is visible), asserts **that the two lists
  disagree** (passing — the disagreement is the finding), and carries the manual's lists as two
  `fixme`s that will go green if and when the master is corrected to match.
- Interacts with the RB-IOS 2026 cutover: if the scheme in force at go-live is not `RBIOS_2021`, the
  served clause numbers change again. Whoever answers this should answer it for the 2026 clause set.

### D-C-28 — P2 — One refusal cites no clause at all, and the code hides the omission
- **Observed:** `isComplainantSelf` (`blockOn: 'no'`, `nonMaintainable: true`, `inlineSubQuestion: true`)
  has `clauseReference: null`. The other eight blocking rows all carry one. A citizen filing through an
  advocate who is not the complainant is therefore refused with **no statutory basis stated**.
- **Why it is invisible:** `interpolateClause` (`ts:375-381`) deletes the `{{clause}}` placeholder
  outright when no clause is known, rather than leaving a gap or failing loudly. The sentence reads
  perfectly. Neither the citizen nor a tester reading the screen can tell a citation is missing.
- A refusal with no clause is unappealable by construction — there is nothing to appeal against.
- Automated as a passing test asserting the clause-less set is exactly `['isComplainantSelf']` (which
  doubles as a guard against that set growing) plus a `fixme` requiring the advocate refusal to cite a
  clause.

### D-C-29 — P3 — A refused citizen is offered no alternative channel
- Blocks 50/51 ask that the refusal explain what the citizen may do instead.
- **Observed:** the `.nm-card` (`html:1288-1317`) offers exactly two actions — download the closure
  letter, and go home. No alternative forum, no consumer-court pointer, no RE-grievance-cell address,
  nothing conditioned on *why* the complaint was refused. A citizen refused for being time-barred and
  one refused for being sub-judice get the identical dead end.
- The signal is strongest for the refusals that are pure jurisdiction: the matter belongs somewhere, and
  the portal knows which clause excluded it, so it knows enough to say where.
- **Product decision, not a bug** — what the alternative channels *are* is for the BA. Automated as a
  passing test recording that none is offered, plus a `fixme` carrying the requirement.

### D-C-30 — P1 — The entire closure screen is English for Punjabi users, and Hindi proves that is not inevitable
- **Observed:** all **6 of 6** `nm.*` translation keys are byte-identical to their English values in the
  `pa` bundle. In `hi`, **0 of 6** are — every one is genuinely translated into Devanagari.
- Not a "Punjabi isn't loaded" artefact: `pa` **is** served (`ui-homogenisation/localisation.spec.ts`
  asserts it), all three bundles hold the same 1960 keys, and **131 `pa` keys do carry Gurmukhi**. The
  translator's work exists; the closure screen was simply not part of it. Repo-wide, 1826 of 1960 `pa`
  keys (93%) are English passthrough versus 284 of 1960 (14%) in `hi`.
- **Why P1 and not a cosmetic gap:** this screen is where a citizen is told, in law, that their
  complaint will not be heard and under which clause. A Punjabi speaker is told that in English. It is
  the same objection as D-C-18 (the hardcoded statutory declarations) at the other end of the journey,
  and the two together mean the portal's trilingual claim does not hold at either legally significant
  moment.
- Automated as: Hindi-is-translated (passing, the benchmark that removes the "it's just not done yet"
  defence), Punjabi-is-English (passing, the measurement), and a `fixme` requiring the screen be
  translated. Also asserted: no served locale leaves a raw `{{...}}` placeholder in any refusal message.

### D-C-31 — P2 — Eight of nine refusal messages never tell the citizen which clause they cite
- Blocks 50/51 require the message to state the reason **and** the clause.
- **Observed:** of the 9 blocking rows, only `filedWithRE`'s message contains `{{clause}}` — confirmed
  against the served `en` bundle, not merely the master. The remaining eight carry a `clauseReference`
  in the master that is never interpolated into any sentence the citizen reads. The clause is known to
  the system and withheld from the person it is used against.
- Distinct from D-C-28: there the clause does not exist; here it exists and is not shown.
- Also asserted (passing): no refusal message hardcodes a clause number in its prose — the one message
  that cites a clause does so through the placeholder. So the fix is additive and low-risk: add
  `{{clause}}` to eight strings in three bundles. That it is this cheap is part of the finding.
- Automated as a passing test counting the messages that cite a clause, plus a `fixme` requiring all of
  them to.

### D-C-32 — P2 — The review screen lists the attached documents but cannot open any of them
- Both blocks 52 and 57 open with the same case: "on the complaint details **review** screen, the user
  has the option to preview the uploaded documents."
- **Observed:** the preview control is on **step 3**, beside the upload field (`button.preview-file`,
  `html:894`). The review screen — step 6 — renders attachments as names only: a `.rs-row-full` per file
  carrying `f.name` and a file icon (`html:1202-1207`). No preview button, no download button, no remove
  button. Asserted by scoping the absence to `.rs-section` so it cannot be satisfied by step-3 controls.
- **Why it matters:** the review screen exists so a citizen can check what they are about to submit. A
  citizen who attached last year's statement by mistake cannot find out here — they must go back two
  steps to look, and the product gives them no reason to think they need to.
- Cheap to fix: a preview button on the `.rs-row-full` at `html:1205`, reusing `previewAttachment(i)`.
- Automated as: the review screen lists names (passing), the review screen offers no controls (passing,
  the measurement), and a `fixme` requiring preview from the review screen.

### D-C-33 — P2 — The "preview viewer" is a browser tab, so four specified behaviours have no product to live in
- `previewAttachment` (`ts:2061-2063`) is one line: `window.open(URL.createObjectURL(file), '_blank')`.
- **Observed:** no viewer element is added to the page at all — verified after the tab opens, so a
  late-rendering viewer would still be caught: no `[role="dialog"]`, no `iframe`/`embed`/`object`, no
  `.preview-overlay`/`.preview-modal`/`.document-viewer`. Block 52's "inline **or** modal pop up viewer"
  is false on both limbs; block 57's "inline viewer" likewise.
- **The consequence is four further requirements with nowhere to be implemented**, all of which both
  blocks ask for and all of which currently belong to the browser rather than the product:
  - *close control* (block 52, three cases) — there is no preview screen to close; the citizen closes a
    tab. Note the third of those cases, "post closing the preview the form is still editable", **does**
    hold and is a passing test — the form was never covered, so nothing had to be restored.
  - *zoom options* (lines 3825-3827) — the browser's.
  - *per-document download* (block 57, three cases) — **does not exist anywhere in the portal.** The only
    download controls on this component are the acknowledgement PDF (`html:1277`) and the closure letter
    (`html:1317`). What block 57 describes is the browser's PDF toolbar inside the opened tab.
  - *screen-reader compatibility of the preview* — deferred, and with no product surface there is nothing
    to audit.
- **Also a functional risk, not only a spec mismatch:** `window.open` is what popup blockers block. On a
  browser with popups blocked the citizen clicks Preview, nothing happens, and **no error is shown** —
  there is no `if (!win)` branch. That is a silent dead end on a government portal.
- Automated as: the tab opens and is opened by the form (passing), nothing is added to the page
  (passing, the measurement), and three `fixme`s — an in-page viewer, a close control, a per-document
  download control.

### D-C-34 — P3 (unreachable, not broken) — The unsupported-file preview case cannot be run, and its message does not exist
- Both blocks require that an unsupported file cannot be previewed; block 52 names the message
  **"Preview not available for this file type."**
- **Two findings, and the distinction matters.** (a) The message does not exist in the portal — the only
  `Preview not available` in the repository is `crpc/draft-assessment.component.html:231`, an officer
  screen in a different component. (b) The requirement is nevertheless *satisfied*, but one gate earlier
  and by a different mechanism: an unsupported file is refused by the **upload** validator, so it never
  becomes an attachment and never acquires a preview button to press. Proven by trying to establish the
  case's own precondition — a `.xlsx` is offered, refused with `File type ".xlsx" is not allowed`, and no
  chip appears.
- `previewAttachment` itself applies **no type check** — it opens whatever blob it is handed. So there is
  no state in which a chip exists and its preview fails, which is why this is "unreachable" rather than
  "broken". Asserted as the corollary across all three attachable types, so a type added to the upload
  allow-list without a matching preview path would surface here.
- **The BA should be told which it is**, because the fix differs: delete the case as duplicative of the
  upload validation (block 45), or build a preview-time check and the message to go with it.

### D-C-35 — P1 — The duplicate check's entity filter is inverted: naming the right bank makes it miss
- **The single most serious finding in Session C.** The duplicate check narrows on an entity id that the
  public filing path never writes, and treats an *unrecognised* entity name as "match any entity". The two
  together invert the control: the more accurately a citizen describes their complaint, the less likely
  the check is to find its duplicate.
- Mechanism, both halves needed to see it:
  - `resolveBankId` (`ComplaintApiV1Controller:321`) turns the submitted `entityName` into a `BANKS.id`, and
    `findPotentialDuplicates` filters `(:bankId IS NULL OR c.bankId = :bankId)` (`ComplaintRepository:164`).
  - But `fileComplaint` (`ComplaintService:133`) sets `bankId` from `req.getBankId()`, and the wizard sends
    **`regulatedEntityId`**, not `bankId`. The controller reads `regulatedEntityId` into the request object
    (`controller:120`) and **nothing ever transfers it to `bankId`**. The entity survives only as the
    free-text `entity_code` column, which the duplicate query does not consult.
  - So a *resolvable* name produces a non-null `bankId` that is compared against a column that is always
    NULL → **no match**. An *unresolvable* name produces `bankId = null` → **matches every entity**.
- Measured, not inferred. One live complaint seeded through the public endpoint against State Bank of India,
  then the check asked about it three ways:
  | `entityName` sent | Result |
  |---|---|
  | *(omitted)* | `duplicate: true` |
  | `Bajaj Finance Limited` — not in `BANKS` | `duplicate: true` |
  | `State Bank of India` — the entity it was actually filed against | **`duplicate: false`** |
- Population, so this is not an edge case: **203 of 203** `filing_type='ONLINE'` complaints have `bank_id`
  NULL; **13 of 8522** rows repo-wide have it populated at all (and those come from the email-simulation
  path, `EmailSimulationService:168`, which does set it).
- **It reaches the citizen.** Reproduced end to end: with the same seeded duplicate, a wizard journey that
  *chooses an entity* gets **no popup at all** and lands straight on the success screen — two live
  complaints for one grievance, with no warning ever shown. A journey that chooses no entity gets the popup
  correctly. That is the opposite of the intended behaviour.
- Covered by a `fixme` asserting the requirement (the correct entity name must find the duplicate) plus
  three passing companions that pin the present reality — including one asserting `bank_id` is unpopulated
  across all ONLINE filings, so the day the write is fixed the companion fails and names itself.
- **Fix is a one-line mapping** (`regulatedEntityId` → `bankId`, or resolve `entity_code` in the query),
  but it must be decided alongside D-C-36 — fixing the write without widening the master would make the
  check entity-blind for 133 of 145 entities instead of inverted for 12.

### D-C-36 — P2 — The duplicate check resolves entity names against a 12-row master while the portal offers 145
- `resolveBankId` matches the submitted name (or code) against `bankRepository.findAll()` — the **`BANKS`
  table, which holds 12 rows**: SBI, PNB, BOB, Canara, Union, HDFC, ICICI, Axis, Kotak, IndusInd, Yes, IDBI.
- The wizard's entity picker is populated from `GET /api/v1/routing/entities/list`, which serves **145**
  entities including NBFCs, payment banks and credit information companies.
- So **133 of 145 selectable entities cannot resolve**, and per D-C-35's null-widening each of them turns
  the entity leg off. The javadoc at `controller:320` states the intent plainly — *"an unknown name widens
  the check rather than failing it"* — so the widening is deliberate; what is not deliberate is that it is
  the **common** case rather than the exception.
- This is an independent defect from D-C-35 and survives it: repairing the `bank_id` write would leave every
  NBFC complainant with an entity-blind duplicate check.
- Same 12-vs-145 master split already recorded against CEPC routing, so the fix is probably shared: resolve
  against the routing entity master, not `BANKS`.
- Covered by a passing test asserting the portal master is strictly larger than `BANKS` (reported, not
  pinned — the figures belong here, not in an assertion).

### D-C-37 — P1 — A citizen who proceeds past the duplicate warning files an untagged complaint, and there is nowhere to tag it
- Block 54's final case requires that proceeding creates a complaint **"tagged as a duplicate complaint to
  the previous identified complaint."** Nothing is tagged, and the absence is structural rather than a
  missing write.
- `proceedDespiteDuplicate` (`ts:2159-2163`) sets `duplicateCheckDone = true` and calls `submit()`.
  `performSubmit` (`ts:2165`) then builds its payload with **no duplicate marker of any kind** and discards
  `res.complaintNumber` — the only place the matched complaint's identity ever existed, held in a local
  callback variable and never persisted. **The server is never told the citizen was warned.**
- Proven in SQL rather than by an API probe, because an endpoint returning nothing looks identical either
  way: **0 columns** in `COMPLAINTS` or `COMPLAINT_MASTER` match `%dup%` / `%parent%` / `%related%`, and
  **0 tables** in the schema match `%DUPLICATE%` / `%COMPLAINT_LINK%`.
- **Consequence.** An officer triaging the second complaint has no way to know a related one is already
  live — which is the entire purpose of warning the citizen. The warning currently protects nothing but the
  citizen's own patience: they are told, they accept, and the information dies in the browser.
- Also note the manual's three dead parameters here (hard-number row 35): `complainantName` is never sent
  and `disputeDate` is sent and ignored, so the "combination of five" the BA signed off is a combination of
  two. Verified by probe — a different complainant name and a transaction date seven years apart are both
  still reported as the same complaint.
- Covered by a `fixme` asserting the requirement plus a passing companion that asserts the schema has
  nowhere to record it, so the `fixme` can be promoted the moment a column appears.

### D-C-38 — P1 — The duplicate popup is English for Punjabi users, and Hindi proves that is not inevitable
- All four strings the popup shows come from the translation bundle: `duplicate.title`, `duplicate.note`,
  `duplicate.cancel`, `duplicate.proceed` (`html:1330-1336`).
- Measured against `GET /api/v1/i18n/translations/{locale}` as served: **Hindi translates all four.
  Punjabi translates none** — every one falls through to the English string, in a `pa` bundle that is
  otherwise populated with over a hundred keys. So this is a per-key gap, not a missing locale.
- **Same defect class as D-C-30** (the non-maintainable closure screen, 6 keys) and the same severity
  reasoning: this is not decorative text. It is the point at which a citizen decides whether to file a
  second complaint against the same entity — an irreversible action — and both the question and the two
  buttons are in a language they explicitly did not choose.
- Covered by a `fixme` asserting Punjabi must be translated plus a passing companion asserting all four
  keys are presently identical to English *and* that the bundle is substantial, so a 404 on the `pa`
  endpoint cannot masquerade as "translated".

### D-C-39 — P2 — The "tabbed interface" is a progress nav, so block 56's tab-selection cases have no product to verify
- Block 56 calls the step display a "tabbed interface" in every one of its eight cases. It is not one.
  `html:439` renders `<div class="step-header" role="navigation" aria-label="Form progress">` and the
  spec asserts the tab semantics **absent** rather than assuming it: **0** elements with `role="tablist"`,
  **0** with `role="tab"`, **0** step items carrying `aria-selected`.
- Why this matters beyond vocabulary: a tab set tells assistive technology *which panel is currently
  selected* and that the others are selectable siblings. A navigation landmark says none of that. A
  screen-reader user gets the six labels and the visual `.current` highlight, and the highlight is
  conveyed by CSS class only — there is no accessible "you are on step 3 of 6" for anything but a
  sighted user. (`Step {{ i + 1 }}` is rendered as text, so the *number* is readable; the *current*
  one is not distinguishable.)
- **This is the smaller half of the manual's complaint.** A progress nav is a legitimate, arguably
  better pattern for a linear wizard than tabs. The defect is not "it should be tabs" — it is that
  the current-step state has no accessible expression, and that block 56's cases cannot be executed
  as written because the thing they describe does not exist. The manual needs rewriting against the
  real control (see D-C-40) and the component needs `aria-current="step"` on the current item.
- Asserted as a live guard, so if tab semantics *are* added later the assertion fails and the manual's
  wording becomes correct rather than silently drifting further apart.

### D-C-40 — P3 (requirements defect) — The manual's seven sections are not the product's six, and neither is a subset of the other
- Measured in both directions from the rendered step bar:
  - the manual claims four sections the bar does not have — `Regulated Entity Name`,
    `Complaint Eligibility Checks`, `Authorised Representative`, `Declaration & Submission`;
  - the bar shows three the manual never names — `Representative Authorisation`, `Declaration`,
    `Review and Submit`.
- The causes are three distinct modelling differences, not typos:
  1. The manual's **first two** entries are the *eligibility phase* — real screens, in a different
     phase, with **no step bar at all** (`html:419` gates the bar on `phase() === 'form'`). A tester
     looking for seven tabs on those screens finds zero, not five.
  2. The manual's **last** entry, "Declaration & Submission", is **two** steps here (`Declaration`,
     then `Review and Submit`).
  3. "Authorised Representative" vs "Representative Authorisation" is a genuine name divergence, not
     just word order — one names the person, the other names the document.
- And the product's final step, **Review and Submit**, is absent from the manual's list — which is odd
  precisely because block 56's own "visible while previewing the form" case is *about that screen*.
- **Fix belongs in the manual, not the code.** Recorded with the delta asserted in both directions so
  the day either list changes, the test says which one moved.
- Corollary already noted as hard number 44: block 56's "user is able to click on **ANY** tabs" is
  **false by design**. `goToStep` (`ts:1868-1872`) refuses any index above `highestStepReached()`, so
  from step 3 exactly 3 of 6 items are clickable. Jumping to Declaration from step 3 would present a
  submit button for a form nobody filled in. This is automated as the guard, inverting the manual's
  case, because the requirement is what is wrong here.

### D-C-41 — P2 — Six translated step labels and a translated "Step" prefix exist, and the component renders hardcoded English instead
- `form.step1_title` … `form.step6_title` **plus `form.step_label`** — seven keys — all exist in the
  bundle served by `GET /api/v1/i18n/translations/{locale}`, and **Hindi translates every one**
  (measured: 0 of 7 fall through to English).
- The component ignores all seven. `stepTitles` (`ts:134-141`) is a hardcoded English string array and
  `html:458` renders the literal `Step {{ i + 1 }}`. Measured: on a Hindi session the step bar renders
  the English array verbatim.
- **This is a wiring gap, and the cheapest fix in Session C's findings** — distinct from D-C-30 and
  D-C-38, where the Punjabi strings genuinely do not exist. Here the strings are written, served, and
  unused. A Hindi citizen fills a Hindi form while navigating it through an English contents list.
- **Ordering trap for whoever picks this up:** wiring the component to the bundle serves Hindi
  correctly and changes **nothing** for Punjabi — all seven `pa` keys are still English (asserted, in a
  `pa` bundle of >100 keys, so this is per-key and not a missing locale). Both halves are needed, and
  they are separate pieces of work: one is Angular, one is content.
- Covered by two `fixme`s (render the translations; translate them into Punjabi) and two passing
  companions (the English array is what renders; all seven `pa` strings are presently English) — so
  the pair distinguishes "not translated" from "translated and ignored", which is the whole finding.

### D-C-09 — P3 — Blocks 39 and 40 contradict themselves on whether the field may be blank
- Manual lines 2840-2842 and 2930-2932 say the field "can be kept blank", while the same blocks
  mark it mandatory and the template stars it. `ts:1678-1679` refuses to advance without either.
- Specs assert the **observed mandatory** behaviour: a wallet complaint naming no wallet is not
  actionable, so the code is judged right and the manual wrong. Flagged for correction.

### D-C-10 — P2 — "Additional business correspondent fields" do not exist
- Manual lines 3002-3006: selecting 'Yes' for the business-correspondent question should display
  additional fields.
- **Observed:** selecting Yes reveals nothing. `html:811-824` renders the question and its error and
  stops; no conditional block exists anywhere in the component and no BC-related key exists in
  `formData`.
- The manual never names which fields these would be, so this cannot be written as a concrete
  assertion even in principle — the requirement is under-specified as well as unbuilt.
- Both the `fixme` (required behaviour) and a passing companion test (recording the current absence)
  are in `wallet-bc-reference.spec.ts`.

### D-C-11 — P3 — Manual block 40's error message names the wrong field
- Line 2942 demands, for the Transaction/Reference Number field, `"Wallet name cannot exceed 150
  characters."` Copy-paste error in the test case itself.

## 4. Deferred / not automatable

| Manual range | Cases | Subject | Why deferred |
|---|---|---|---|
| 4110-4136 | 7 | SMS notification on submission | Real SMS delivery, template rendering, once-only send, multi-language content. Outbox-row assertion attempted only if time permits; actual delivery is manual. |
| 4137-4176 | 11 | Email acknowledgement / closure letter + digital signature + PKI compliance | Real email delivery **and** a requirements conflict — see D-C-08. Not automatable and not correct as written. |
| 3565-3575 | 3 | Autosave after direct browser close / after session expiry | Not reliably drivable — Playwright's context close is not a user closing a window, and a forced session expiry needs a clock the test cannot move. Autosave-after-refresh IS automated (and failing — D-C-19). Note that `ngOnDestroy` (`ts:1036-1041`) does save, so the close case is likely *implemented*; it is the proof that is out of reach, which is why this is deferred rather than reported. |
| 3825-3827 | 1 | Preview is screen-reader compatible with zoom options | Needs an assistive-technology audit; `axe` covers only part. |
| 3097-3106 | 2 | "Amount Involved accepts max ___ characters" | **The manual's own figure is blank.** No requirement stated; nothing to assert. Under-specified. |
| login/nav preamble | ~45 | "user can log in", "user lands on Select RE screen", "user reaches Complaint Details" | Repeated verbatim at the head of all 16 blocks in this session's scope. Already covered by `otp-lifecycle.spec.ts` and the 7 `eligibility-*.spec.ts` files. Re-automating 45 copies of an existing test adds runtime, not coverage. |

### D-C-08 — P1 (requirements conflict, needs legal) — the manual demands a digital signature the project has ruled must not be claimed
- Manual lines 4137-4176 require the acknowledgement/closure letter to carry a **digital signature**
  and be **PKI compliant**.
- Delivered behaviour is the exact opposite, deliberately: the project ruling is that the DSC is a
  **MOCK**; `public/pdf-signature.spec.ts` (UST97/D5) asserts downloaded PDFs must **NOT** claim a
  digital signature; and `file-complaint.component.ts:1570` stamps closure letters
  `SYSTEM-GENERATED LETTER — NOT A DIGITALLY SIGNED DOCUMENT`.
- **This is not a test failure and must not be "fixed" by writing a test that demands a real
  signature.** It is a requirement that conflicts with a ruling. Legal sign-off needed on which
  wins before either the manual or the code changes.

---

## 5. Final counts

**Scope delivered.** Every block in Session C's scope is automated — 33, 34, 35, 37, 38, 39, 40, 41,
42, 43, 44, 45, 46, 47, 49, 50, 51, 52+57, 54+55, 56 — in **10** spec files rather than 20, because
three groups turned out to be one feature each and were collapsed: 49/50/51 (the non-maintainable
closure is one *phase*, not three screens), 52/57 (7 of block 57's 12 cases are verbatim block 52),
and 54/55 (one control, 6 shared rows).

| | Count |
|---|---|
| Manual cases attributed to tests | **222** |
| Manual cases deferred (§4) | 24, plus ~45 login/nav preamble cases already covered by existing specs |
| **Tests written** | **371** |
| **Passing** | **310** |
| **`fixme` — required behaviour the product does not have** | **61** |
| **Failing** | **0** |
| New spec files, all under `e2e/public/` | **10** |
| Hard numbers recorded (§1) | **45** |
| Defects raised | **41** — D-C-01 … D-C-41 |

**Per spec, as last run (chromium, `workers: 1`, `retries: 0`):**

| Spec | Tests | Pass | Fixme | Blocks |
|---|---|---|---|---|
| `account-card-numbers.spec.ts` | 47 | 31 | 16 | 33, 34, 35, 37 |
| `wallet-bc-reference.spec.ts` | 27 | 21 | 6 | 38, 39, 40, 41 |
| `amount-compensation-caps.spec.ts` | 48 | 38 | 10 | 42, 43, 44 |
| `wizard-upload-documents.spec.ts` | 38 | 35 | 3 | 45 |
| `declaration-submission.spec.ts` | 20 | 18 | 2 | 46 |
| `draft-autosave-lifecycle.spec.ts` | 47 | 40 | 7 | 47 |
| `non-maintainable-closure.spec.ts` | 48 | 41 | 7 | 49, 50, 51 |
| `review-document-preview.spec.ts` | 32 | 27 | 5 | 52, 57 |
| `duplicate-detection-popup.spec.ts` | 45 | 42 | 3 | 54, 55 |
| `wizard-step-navigation.spec.ts` | 19 | 17 | 2 | 56 |
| **Total** | **371** | **310** | **61** | |

Each spec was run to green on its own; per standing ruling 8 no full-suite run was performed. The
static `test(` count in a file is lower than its test count in several cases — the specs are
table-driven and those loops expand at runtime.

**Defects by severity: 13 P1, 18 P2, 10 P3.**

**The thirteen P1s, for triage:**

| # | One line |
|---|---|
| D-C-35 | **The duplicate check's entity filter is inverted — naming the right bank makes the check miss** |
| D-C-37 | Proceeding past the duplicate warning files an untagged complaint, and there is nowhere to tag it |
| D-C-20 | The draft endpoints have no authentication and no ownership check at all |
| D-C-22 | The citizen wizard never uploads the attachments it collects |
| D-C-24 | The server draft is write-only: it cannot be seen, resumed, or deleted |
| D-C-25 | A non-maintainable refusal is recorded nowhere, and its "case id" is a browser clock reading |
| D-C-01 | Compensation caps are hardcoded in the Angular bundle and enforced client-side only |
| D-C-07 | `.docx` is refused while `.doc` and `.png` — which the manual never names — are accepted |
| D-C-18 | The statutory declarations are hardcoded English on a trilingual portal |
| D-C-30 | The whole non-maintainable closure screen is English for Punjabi users |
| D-C-38 | The duplicate popup is English for Punjabi users |
| D-C-27 | **Needs legal.** Six of the ten clauses the manual says a refusal may cite do not exist |
| D-C-08 | **Needs legal.** The manual demands a real digital signature; the project ruling is a mock |

**If the BA reads only four things:** hard number **36** (D-C-35 — the inverted entity leg, the most
serious finding in this session, and reproduced end-to-end in the UI, not just against the API); hard
number **35** (blocks 54/55's "combination of five parameters" is really a combination of **two** —
one leg works backwards and two are dead on the wire); and **D-C-27** with **D-C-08**, the two items
engineering cannot resolve at all because they need a ruling on whether the manual or the code is
authoritative.

**Three Punjabi defects, one root cause.** D-C-18, D-C-30 and D-C-38 are all "the `pa` bundle is
missing these keys while `hi` has them", and D-C-41 is the near-miss version (keys present in both
`en` and `hi`, ignored by the component). Worth fixing as one content+wiring task rather than four
tickets. **Cheapest single fix in this document: D-C-41.**

**State of the tree:** left dirty and uncommitted per standing ruling 7 — 10 new files under
`cms-portal-frontend/e2e/public/` plus this document. No file under `src/**` was modified, no shared
harness file (`test-data.ts`, `fixtures.ts`, `api-redirect.ts`, `auth.ts`, `browser-api.ts`,
`playwright.config.ts`) was edited, and nothing was restarted, rebuilt, or killed at any point.
