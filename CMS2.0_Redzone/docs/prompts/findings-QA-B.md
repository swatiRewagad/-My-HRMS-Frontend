# QA Pass 4 — Session B findings

Scope: contact/address fields, RE-details cascading dropdowns, complaint core fields.
Environment: dev server 4200, backend **8092** (`dev-local`, Hazelcast cluster `cms-claude-8092`),
MySQL `cms_db`. Keycloak was **not** needed — the citizen portal accepts a seeded session and
`dev-local` excludes the OAuth2 resource-server autoconfig.

MySQL, the 8092 backend and the dev server were restarted on day 2 (with the user's explicit
authorization) after the machine had been rebooted; day 1's brief had forbidden this. **No `src/**`
application code was modified and no commits were made** — that constraint held throughout.

---

## 0. THE BIGGEST STRUCTURAL FINDING — the manual pack's step numbers are wrong

The pack (and the brief derived from it) describes Complainant Details / RE Details / Complaint Details
as **steps 3 / 4 / 5 of a 7-step wizard**. The product does not work that way. The eligibility
questionnaire is a **separate phase**, not numbered steps, and the form phase starts at step 1:

| Real location | Screen |
|---|---|
| phase `eligibility` | entity select + N maintainability questions (not numbered) |
| form **step 1** | Complainant Details — email, landline, pincode, state, district, address |
| form **step 2** | Regulated Entity Details — credit-card radio, entity state/district/branch |
| form **step 3** | Complaint Details — category, facts, account radio + account types |
| form step 4 | Authorised representative |
| form step 5 | Declarations |
| form step 6 | Review and Submit |

Anyone reading the pack against the product will be off by two. Worth correcting in the pack itself.

---

## 1. Coverage table

| Block | Lines | Manual cases | Spec file | Tests written | Status |
|---|---|---|---|---|---|
| 17 | 964-1081 | 16 | `e2e/public/complainant-contact-fields.spec.ts` | 9 | 7 pass, **2 fixme** (D-B1 blocking, D-B2) |
| 18 | 1082-1164 | 14 | `e2e/public/complainant-contact-fields.spec.ts` | 9 | 9 pass |
| 19 | 1165-1232 | 7 | `e2e/public/complainant-address-state-district.spec.ts` | 7 | 7 pass |
| 20 | 1233-1305 | 8 | `e2e/public/complainant-address-state-district.spec.ts` | 8 | 8 pass (incl. 1 defect proof) |
| 21 | 1306-1371 | 10 | `e2e/public/complainant-address-state-district.spec.ts` | 9 | 9 pass |
| 22 | 1372-1475 | 19 (3 dup preamble) | `e2e/public/complainant-pincode.spec.ts` | 15 | 15 pass |
| 1 | 1-33 | 4 | `e2e/public/re-details-cascading-dropdowns.spec.ts` | 4 | 4 pass (incl. 1 defect proof) |
| 23/24/26/27 | 1476-1891 | 32 (11 dup preamble) | `e2e/public/re-details-cascading-dropdowns.spec.ts` | 27 | 27 pass (incl. 1 defect proof) |
| 28 | 1892-1992 | 8 | `e2e/public/complaint-category-facts.spec.ts` | 7 | 7 pass |
| 29 | 1993-2078 | 11 | `e2e/public/complaint-category-facts.spec.ts` | 11 | 11 pass (incl. 1 undocumented-mandatory proof) |
| 30 | 2079-2151 | 6 (4 dup preamble) | `e2e/public/complaint-transaction-account.spec.ts` | 3 | 3 pass (all defect proofs — the field does not exist) |
| 31/32 | 2152-2306 | 14 (5 dup preamble + 2 replayed) | `e2e/public/complaint-transaction-account.spec.ts` | 16 | 16 pass (incl. 4 defect proofs) |
| 48 | 3578-3613 | 5 (2 dup preamble, 1 needs-scoping) | `e2e/public/form-tooltips-speech.spec.ts` | 5 | 5 pass (all defect proofs — FR-G-009 is unbuilt) |
| 53 | 3853-3865 | 2 (1 dup preamble) | `e2e/public/form-tooltips-speech.spec.ts` | 5 | 5 pass (incl. 1 defect proof) |

The "tests written" column sums to **135**, which reconciles with what the runner collects per file
(18 / 24 / 15 / 31 / 18 / 19 / 10). Test counts are lower than manual-case counts wherever the pack
repeats its 2-4 row preamble in every block, replays an earlier block verbatim (32's first two cases are
31.8/31.9 word-for-word), or states a case that cannot arise at all (block 30's field is not on the
screen). They are *higher* in two blocks, where observed behaviour needed an assertion the pack never
asked for.

Shared helper: `e2e/public/helpers-forms-b.ts` — wizard navigation (seed session → pass eligibility →
land on step 1/2/3), fail-closed master-data detection, pincode-lookup waiter, field-error reader,
truncation asserter.
No file owned by another session was edited. No `src/**` file was edited (ruling 5) — the modified
`src/**` files in `git status` predate this session's work. No `test.step` introduced. No `@tag`
annotations.

---

## 2. PRODUCT DEFECTS

### D-B1 (BLOCKING) — no organisation complainant category can leave step 1
`file-complaint.component.ts:1653` validates `formData['firstName']` unconditionally for step 1. The
First Name input is rendered **only** inside the `isIndividualCategory()` branch
(`file-complaint.component.html:495`). For all 10 organisation categories the template instead renders
`organizationName` (`html:537`) and never populates `firstName`.

Result: a fully-completed organisation complainant cannot advance. The error "First name is required"
is raised against a field that is not on the screen; `validationErrors['name']` red-styles the
Organisation Name box, so the citizen sees a message about a first name over a box asking for a company
name, with no way to satisfy it.

- Affected categories: Individual–Business, Proprietorship, Partnership, MSME, Association, Trust,
  Limited Company, Government Department, PSU — **and Person with Disabilities** (see D-B2).
- Test: `complainant-contact-fields.spec.ts` → *"DEFECT: no organisation category can leave step 1…"*
  (`test.fixme`).
- Consequence for QA: every manual case in block 17 phrased "…and proceed" is unreachable through the
  UI until this is fixed. Blocks 19-22 were therefore automated on the `individual` path.

### D-B2 — "Person with Disabilities" is classified as an organisation
`isIndividualCategory()` (`component.ts:2233-2236`) tests only `individual` and `senior_citizen`.
`pwd` falls through to the organisation branch, so a Person with Disabilities is asked for an
**Organisation Name** and an **Organisation Landline**, and is never shown a First Name field — which
then triggers D-B1, making the category unusable.

Manual case 17.3 explicitly names three categories that must not see organisation fields:
"Individual / Senior Citizen / Person with disablilities". The product excludes two.

- Verified in a real browser: with `pwd` selected, `organizationName` renders and `firstName` does not.
- Test: *"DEFECT: Person with Disabilities is treated as an organisation…"* (`test.fixme`).
- This is an accessibility-relevant defect: the category exists to serve disabled citizens and is the
  one category that cannot file.

### D-B3 — District of Residence is marked mandatory but is never validated
The template gives `district` a red asterisk (`file-complaint.component.html:590`), but `district`
appears **nowhere** in `validateCurrentStep()` (`component.ts:1652-1657` checks only firstName,
pincode, state, address, email). The `<select>` has no `disabled` binding either.

Because the pincode lookup normally pre-selects a district, a citizen rarely notices. But any path
that clears it — re-selecting the placeholder, a lookup that returns a state with no district — passes
validation, and the complaint is filed with no district of residence.

- Proven by clearing the district *after* the lookup populated it, leaving `state` satisfied: no error
  is raised and the wizard advances to step 2.
- Test: `complainant-address-state-district.spec.ts` → *"DEFECT: district is shown as mandatory but is
  not validated…"* — this test **passes**; passing *is* the proof the defect exists.

---

### D-B4 — the searchable Regulated Entity list cannot be used by keyboard
The entity search results are `<ul role="listbox">` of `<li role="option">` (`html:86-95`). Each option
binds `(mousedown)="selectEntityFromSearch(opt)"` and **nothing else**: no `(keydown)`, no
ArrowDown/ArrowUp handling, no `aria-activedescendant`, no `tabindex` on the options. Grepping the whole
component for keyboard handlers finds only `onStepKeydown` on steps 1 and 3, unrelated to this control.

A keyboard-only citizen cannot reach or activate any search result. Manual case 1.3 explicitly requires
arrow keys + Enter, so this is a documented requirement that is unbuilt, and an accessibility defect on
a government grievance portal.

- **Mitigating:** the native `<select>` beside the search box is keyboard-operable (browsers give that
  for free), so the citizen is not locked out of filing entirely — but the *searchable* control the
  pack describes is mouse-only, and with 145 entities the plain select is the worse path.
- Test: `re-details-cascading-dropdowns.spec.ts` → *"DEFECT: the searchable entity list cannot be
  driven by keyboard…"* — proves ArrowDown highlights nothing and Enter records nothing.

### D-B5 — the Entity Branch list is a dead request feeding a dead getter bound to nothing
Three independent failures stack up, any one of which alone would empty the list:

1. **The endpoint does not exist.** `onEntityDistrictChange()` calls
   `GET /api/v1/location/branches?district=X` (`component.ts:487`), but `LocationController` maps only
   `/pincode/{pincode}`, `/districts` and `/states`. Verified live: **404**, on a fresh JVM (which
   rules out the stale-JVM explanation this project has been bitten by before).
2. **The failure is swallowed.** The handler is `error: () => {}`. The citizen is shown nothing — no
   error, no empty-state, no retry.
3. **Even success would be discarded.** `entityBranches` is referenced **0 times** in the template.

Manual case 27.5 requires branch values "displayed based on the Entity state and Entity district
selected". No values are, or can be, displayed. Test: *"DEFECT: no branch list is or can be
populated…"* — asserts the 404, the silence, and the absent binding in one pass.

This matches the project's known trap of citizen features that are UI-complete against endpoints that
do not exist — except here the UI is not even complete: the field was changed to free text (see 27-C1)
while the dead fetch was left behind.

### D-B6 (HIGH) — the Date of Disputed Transaction field is not on the screen at all
All 6 of block 30's cases describe it. `disputeDate` exists in `formData` (`ts:187`), in the tooltip map
(`ts:1722`), among the **four keys the duplicate-complaint check posts** (`ts:2111-2117`) and in the
submission payload as `transactionDate` (`ts:2173`) — but it is rendered **zero times** in the template.
The only three `type="date"` inputs in the entire wizard belong to the eligibility questionnaire
(`html:153, 250, 291`).

Consequences, each asserted:
1. **The citizen can never supply it**, so `transactionDate` is always `undefined` in the registration
   payload. The complaint records no date for the transaction it is about.
2. **Duplicate detection silently runs on three keys instead of four.** `disputeDate` is permanently
   `''`, so the strongest discriminator between two genuine complaints about *different* transactions
   never reaches the check — raising the false-duplicate rate for a citizen with a recurring problem at
   the same bank in the same category.
3. Cases 30.7 (mandatory) and 30.10 ("Date cannot be greater than complaint filing date.") cannot arise;
   the step advances with no date demanded and neither message exists.

### D-B7 — the account-type list is a bundled build artefact, not configuration
`loadMasterData` requests `GET /api/v1/masters/account-types` (`ts:875`). Verified live: **404**. The
error handler is `error: () => this.loadAccountTypesFromLocal()` (`ts:880`), which substitutes
`src/assets/masters/account-types.json`. Asserted: the master 404s, the asset returns 200, and the ten
rendered options are **exactly** the asset's ten.

So adding or retiring an account type needs a **frontend release**, not a config change — and nobody is
told, because the 404 is swallowed with no error shown to the citizen.

Second, narrower finding: only **four of the ten** types reveal an account-number field (`savings`,
`loan`, `atm_debit`, `credit_card` — the fieldMap at `ts:532-537`). A citizen choosing **Current Account,
Fixed Deposit, Recurring Deposit, Wallet, PPF Account or Others** is asked for no identifier at all and
the step advances, so the complaint names an account type nobody can trace to an account.

### D-B8 — answering "No" to the account question does not discard what was already entered
`checked` lives on the component's `accountTypes` array; `hasAccountWithRE` only controls whether the
control is **rendered**. `onAccountTypeToggle` clears a per-type account number, but only on an explicit
untick. So:

1. Select Savings → type account number `00112233445566` → answer **No**. Both fields vanish from the
   screen but **neither value is cleared**.
2. Answer **Yes** again and the abandoned account number is still there — proven by reading
   `savingsAccountNumber.inputValue()`.

The citizen has stated they have no account with this entity, yet the form still holds an account number
for them. Because the review screen gates its account rows on `hasAccountWithRE === 'yes'`, a citizen who
leaves the answer at No never sees the retained data to correct it. Worth noting for scope: the account
fields appear in the **draft** payload (`formData` is persisted wholesale, `ts:1867`, `ts:1882`) but in
**no field of the registration payload** — so today the leak reaches the saved draft and the review
screen, not the submitted complaint.

### D-B9 — FR-G-009 is unbuilt: a fifteen-entry tooltip map that nothing renders
The component carries `tooltips: Record<string, string>` with fifteen entries, commented
`// FR-G-009: Tooltips` (`ts:1713-1729`). It is referenced **zero times** in the template and **zero times
anywhere else in `src/**`**.

Searched for every mechanism a tooltip could arrive by, across all three wizard steps: `pTooltip`,
`matTooltip`, `[title]` bindings, `.tooltip`, `[role="tooltip"]`, `[aria-describedby]`, `.p-tooltip`,
`.pi-info-circle`, `.info-icon`. **None exists on any field.** The only three static `title=` attributes
in the entire public area are on unrelated buttons (delete-draft, refresh-CAPTCHA, play-CAPTCHA).

All 5 of block 48's cases therefore describe a feature that is not there:
- **48.5** (tooltip on all FR-G-009 forms) — nothing to display.
- **48.6** (must state purpose, format/character limits, mandatory-ness) — asserted concretely on the
  Facts field, which has all three facts available (`maxlength="5000"`, mandatory) and surfaces none of
  them. Mandatory-ness *is* conveyed, but by a red asterisk, not a tooltip.
- **48.7** (reachable on hover, click or info icon) — hover for 1.2s, click and focus all reveal nothing.
- **48.9** (disappears on mouse-out) — unexercisable; there is nothing to hide.
- **48.8** — portal-wide consistency survey, see §4 needs-scoping.

Note the overlap with D-B6: one of the fifteen dead entries is `disputeDate`, a tooltip written for a
field that is itself never rendered. The map documents intent that was wired up on neither side.

### D-B10 — the speech-to-text button is offered where speech is unsupported, and fails silently
`speechSupported` is computed at init (`ts:801`) and then **used nowhere**; the mic button is rendered
unconditionally (`html:696`). `startRecording` returns early when no `SpeechRecognition` constructor
exists (`ts:2062-2063`).

Reproduced by deleting the constructor before boot (i.e. Firefox, or a hardened corporate browser): the
button is visible, **enabled**, and clicking it does nothing at all — no recording state, no message, no
disabled state, no fallback instruction. The citizen is left tapping a dead control. Either gate the
button on `speechSupported` or tell the citizen why it is unavailable.

---

## 3. CONTRADICTIONS — manual case vs. observed behaviour (ruling 2)

Recorded for the BA. In each case the spec asserts observed behaviour and names the case.

### Block 17 — Organisation Landline Number

The field is `<input type="tel" [(ngModel)]="formData['orgLandline']" name="orgLandline">` with **no
`maxlength`, no `pattern`, and no mention in `validateCurrentStep()`**. It accepts anything and
validates nothing.

| Manual cases | Document says | Actually |
|---|---|---|
| 17.6-17.14 (9 cases) | rejects negatives, decimals, alphanumerics, alphabetics, special chars, emoji, only-spaces, padded spaces, interior spaces — each with **"Only numbers are allowed."** | All accepted verbatim. **The string "Only numbers are allowed." does not exist anywhere in the product.** |
| 17.15 | accepts max 10 digits | accepts any length |
| 17.16 | rejects >10 digits + error | 40 digits retained in full; no `maxlength` attribute |
| 17.17 | rejects <10 digits + error | accepted |
| 17.3 | PwD must not see the field | PwD **does** see it — defect D-B2 |

Net: block 17's entire validation rule is **unimplemented**. 11 of 16 cases collapsed into one
table-driven test that asserts the real behaviour and names each case it contradicts.

### Block 18 — Email Id

Email **is** validated — `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` at `component.ts:1657` — but differs from the
pack in four ways:

| # | Document says | Actually |
|---|---|---|
| 18-C1 | error is **"Enter a valid email address."** | error is **"Invalid email format"** |
| 18-C2 | error appears on entering the value ("Observe the screen") | appears **only on Next** — no blur-time or keystroke validation |
| 18-C3 | accepts max 64 chars / rejects >64 (cases 18.14, 18.15) | **no length limit at all** — no `maxlength`, no validator check. A 112-char address is accepted and advances |
| 18-C4 | emoji rejected (18.13) | **emoji ACCEPTED.** The regex is a shape check ("not whitespace, not @"), so `😀@example.com` passes and the citizen advances with an unreachable address |
| 18-C5 | only-spaces / padded spaces rejected with an error (18.10, 18.11) | never reach the validator: `type="email"` makes the **browser** strip whitespace. `"   "` → `""` (and email is optional, so it advances); `"  a@b.com  "` → `"a@b.com"` (accepted, correctly). Mechanism differs; outcome for 18.11 is arguably better than documented |

18-C4 is the one worth acting on: it is a real validation hole, not a wording difference.

### Blocks 19 + 20 — State and District of Residence are NOT dropdowns the citizen picks from

**This single finding invalidates 15 of the 15 cases in blocks 19-20 as written.** The pack treats both
as ordinary dropdowns: "click the dropdown", "select a single value", "try to select multiple values",
"the dropdown closes after selection", "select the state, then observe district is filtered by it".

Neither field is independently selectable. Both are **derived from the pincode** by
`onPincodeInput()` (`component.ts:557-627`), which fires on every keystroke and at exactly 6 digits
calls `GET /api/v1/location/pincode/{code}`, then sets:

```
complainantStates    = unique States    of the returned PostOffice[]
complainantDistricts = unique Districts of the returned PostOffice[]
formData['state']    = states[0]
formData['district'] = districts[0]
```

On any failure it falls back to `lookupPincode()` over a 481-row compiled-in table
(`src/app/utils/pincode-data.ts`), which yields exactly **one** state and **one** district. So before a
valid pincode is typed both `<select>`s hold nothing but the placeholder, and any keystroke that makes
the pincode invalid **blanks them again**.

| # | Document says | Actually |
|---|---|---|
| 19-C1 | citizen opens the State dropdown and selects a state | the list is **empty** until a 6-digit pincode is supplied; the lookup then populates *and pre-selects* it |
| 19-C2 | blank state → **"Response is mandatory."** | state *is* enforced (step 1 is held), but the message is **"Enter valid pincode to auto-fill state"** — which is more accurate, since the citizen cannot act on the state field at all |
| 19-C3 / 20-C3 | "user is not able to select multiple values" is a behaviour to exercise | structurally guaranteed: native `<select>` with **no `multiple` attribute**. Asserted on the attribute — clicking two options could not fail, so it would prove nothing |
| 20-C1 | **case 20.2:** select a State, then District is filtered by that State | **no State→District cascade exists on this screen.** Both derive from the pincode in parallel, in one pass. A real cascade does exist on RE Details (`entityState` → `entityDistrict`) — covered separately. Rewritten here as the relationship that *does* hold: state and district are mutually consistent for the pincode that produced them |
| 20-C2 | blank district → **"Response is mandatory."** | district is **not validated at all** — defect D-B3 |

What *is* correct and now proven: changing the pincode to another region replaces **both** state and
district (no stale-child defect), and making the pincode invalid blanks both lists.

### Block 21 — Address

The textarea carries `maxlength="100"` (`html:596`) and is checked only for emptiness
(`!formData['address']?.trim()` → `'Address is required'`).

| # | Document says | Actually |
|---|---|---|
| 21-C1 | blank address → **"Response is mandatory."** | error is **"Address is required"** |
| 21-C2 | **case 21.10:** rejects >100 chars **AND** shows "Address cannot exceed 100 characters." | both cannot happen. The **browser** truncates at 100, so the over-length value never reaches Angular and no validator can fire. Truncation asserted; **the message does not exist in the product** |
| 21-C3 | **case 21.6:** leading/trailing spaces rejected | **accepted and advances.** The validator trims only to test emptiness, so a padded but non-empty address passes |
| 21-C4 | **case 21.4 contradicts itself** — its description says the field "accepts special characters", its own Expected Result says it "should not accept special characters" | special characters **are** accepted (as are emoji and interior spaces). Needs a BA ruling on which half of 21.4 was intended |

Correctly implemented and confirmed: a whitespace-only address *is* rejected (unlike the email field, a
textarea is not sanitised by the browser, so the spaces reach the model and `.trim()` rejects them) and
exactly 100 characters is accepted.

### Block 22 — Pincode is not a dropdown, and the documented messages do not exist

The pack calls it the "Pincode **dropdown** field" in all 19 rows. The product renders a plain text
input (`html:572-573`):

```html
<input type="text" name="pincode" maxlength="6" (ngModelChange)="onPincodeInput()">
```

No `<select>`, no `<datalist>`, no typeahead panel, no suggestion list. Nothing opens, nothing closes,
and there is nothing to multi-select. Four cases (22.7-22.10) describe a control that does not exist
and one (22.12, "suggestions in the dropdown are reflected") describes a feature that was never built.

**Where the lookup actually goes — the brief's proxy warning, resolved.** `proxy.conf.json` proxies
`/api/pincode` to `https://api.postalpincode.in`, which suggests this field reaches a third party. It
does not. That proxy rule is used **only** by the CRPC draft-assessment screen
(`draft-assessment.component.ts:552`). The citizen wizard calls
`${environment.apiBaseUrl}/api/v1/location/pincode/{value}` (`component.ts:591`) — our own backend.
Verified before writing the spec, so a lookup failure here can never be misread as an upstream outage.

Pincode is also the **only** step-1 field with keystroke-time validation; email, address and landline
are validated only on Next. Four distinct messages exist and **none is the pack's "Pincode must be
6 digits."**:

| Trigger | Message | When |
|---|---|---|
| non-digit present | `Pincode must contain only digits.` | live, on keystroke |
| length > 6 | `Pincode must be exactly 6 digits.` | **UNREACHABLE** — see 22-C3 |
| blank or < 6 | `Valid 6-digit pincode is required` | on Next, from `validateCurrentStep` |
| 6 digits, no match | `Invalid pincode. No location found.` | live, local-table fallback |

| # | Document says | Actually |
|---|---|---|
| 22-C1 | it is a searchable dropdown that opens, closes, and offers single/multi-select (22.7-22.10) | it is a text input — none of those mechanics exist |
| 22-C2 | typed input reflects suggestions in the dropdown (22.12) | **no suggestions of any kind.** Below 6 digits the product only blanks the derived state/district; no partial lookup fires |
| 22-C3 | **22.21:** rejects >6 digits **and** shows "Pincode must be 6 digits." | neither half. `maxlength="6"` means the browser refuses the 7th character, so `length > 6` is unsatisfiable and the branch at `component.ts:577-582` is **dead code**. Proven unreachable across four attempts including paste-length values |
| 22-C4 | **22.22:** rejects <6 digits **and** shows "Pincode must be 6 digits." | silent while typing (the under-length branch deliberately *deletes* the error — correct UX), then blocked on Next with **"Valid 6-digit pincode is required"** |
| 22-C5 | blank → **"Response is mandatory."** | **"Valid 6-digit pincode is required"**, on Next only |
| 22-C6 | **22.15:** decimals are rejected | **a decimal longer than 6 chars is truncated into a VALID pincode.** `411001.5` → `411001` (real Pune pincode), no error, lookup runs, state populated. The citizen's typo is silently converted into a plausible location with no indication anything was discarded |

22-C6 is the one worth acting on. The others are wording or a mis-specified control; this one silently
files a complaint against a location the citizen never entered.

Correctly implemented and confirmed: all six non-digit classes (negative, decimal, alphanumeric,
special chars, emoji, spaces) are rejected live **and** blank the derived state and district, so no
stale location can survive an invalid pincode.

### Blocks 24/26/27 — THE RBIO ROUTING RULE DOES NOT EXIST (asserted three times in the pack)

Each of blocks 24, 26 and 27 states: *"if the Regulated entity selected is other than RBIO then, the
user is **not** navigated to the Regulated entity details screen"*.

**There is no such branch anywhere in the product.** `nextStep()` (`component.ts:1838-1847`) increments
the step unconditionally once `validateCurrentStep()` passes; no department is consulted on the
navigation path. `selectedEntityType` (`component.ts:2223-2227`) and `isCEPCEntity` do exist and *do*
steer which eligibility questions are asked — but they do not steer step routing.

This is not a corner case. The live master holds **145 entities: 86 CEPC and 59 RBIO**, so the majority
of entities a citizen can pick are "other than RBIO". Proven by driving a real CEPC entity (read from
the master, not pinned) through to step 2 — which the pack says must not happen.

**Either the requirement was never built, or the pack is describing a rule that was dropped.** This
needs a BA/product ruling before it can be called a defect: if non-RBIO complaints genuinely should
skip RE Details, this is a missing feature affecting 86 of 145 entities. Logged as **open decision**,
not as a defect, because I cannot tell which side is wrong.

### Block 1 — Regulated Entity selection

Blocks 1's other three cases are satisfied; only the keyboard case fails (defect D-B4).

| # | Document says | Actually |
|---|---|---|
| 1-C1 | **1.2:** "navigate a large volume of entities using a dropdown scrollbar" | not observable — the native `<select>` popup is painted by the OS, outside the DOM. Tested as the substance of the case instead: all **145** master entities are present as options, none paged or truncated away |
| 1-C2 | **1.3:** arrow keys + Enter select an entity | **does not work at all** — defect D-B4 |

Confirmed working: changing a selection before proceeding replaces the first choice (and the
questionnaire then interpolates the *new* entity's name, with the abandoned one absent), and
multi-select is structurally impossible.

### Block 23 — the credit-card radio

Tested once here; blocks 24/26/27 each replay these same three rows verbatim.

| # | Document says | Actually |
|---|---|---|
| 23-C1 | blank radio → **"Response is mandatory."** | **"Please select Yes or No"** |

Confirmed working, and this is the wizard's cleanest conditional: neither option is pre-checked; Yes
removes the state/district/branch group from the DOM entirely and advances straight to Complaint
Details; No reveals all three; switching back to Yes removes them again.

### Block 24 — Entity State

| # | Document says | Actually |
|---|---|---|
| 24-C1 | blank state → **"Response is mandatory."** | **"Entity state is required"** |

Confirmed working: the options are exactly what `/api/v1/location/states` returns (23 states, asserted
against the endpoint rather than a hardcoded list), single-select is enforced by attribute, and the
field is correctly absent when credit-card = Yes.

### Block 26 — Entity District: the cascade that actually works

Worth stating plainly, because it is the counter-example to blocks 19-20: `entityState → entityDistrict`
is a **genuine server-backed cascade**, and it is correct. `onEntityStateChange()` clears district *and*
branch, then fetches `/api/v1/location/districts?state=X`.

| # | Document says | Actually |
|---|---|---|
| 26-C1 | blank district → **"Response is mandatory."** | **"Entity district is required"** |

Confirmed working: the list is empty until a state is chosen; each state's districts match the
endpoint's response exactly; two different states yield genuinely different district sets; and changing
the state clears an already-chosen district — **no stale-child defect**.

### Block 27 — Entity Branch is a free-text field, not a cascading dropdown

The pack spends 10 cases on an "Entity Branch dropdown" that opens, closes, offers single-select,
refuses multi-select, and is populated from the chosen state and district. The product renders
(`html:652`):

```html
<input type="text" name="entityBranch" placeholder="Enter Branch Name">
```

| # | Document says | Actually |
|---|---|---|
| 27-C1 | a dropdown that opens / closes / single-selects / refuses multi-select (27.6-27.10) | a **text input**. The citizen types the branch name. None of those mechanics exist; no `maxlength` either, so nothing is truncated |
| 27-C2 | **27.5:** branch values displayed based on state + district | **no values are ever displayed** — defect D-B5 (404 + swallowed error + unbound getter) |
| 27-C3 | blank branch → **"Response is mandatory."** | **"Entity branch is required"** |

Confirmed working despite the above: a whitespace-only branch is rejected (validator trims), and
changing the district clears an already-typed branch, so a branch name cannot outlive its district.

### Block 28 — Complaint Category: the pack's ten categories are not the product's ten

This is the most consequential finding of the block, because **routing keys off the category**. Both
lists have exactly ten entries, and neither is a subset of the other.

| Pack expects (case 28.4) | Product serves (`/api/categories`) | Verdict |
|---|---|---|
| ATM/CDM/Debit card | ATM / Debit Card | renamed — **CDM dropped** |
| Credit Card | Credit Card | same |
| Loans and Advances | Loan / Advances | renamed |
| Mobile/Electronic Banking | Mobile Banking / UPI **+** Internet Banking | **split into two** |
| Notes and Coins | — | **NO COUNTERPART** |
| Opening/Operation of Deposit accounts | Deposit Accounts | renamed |
| Para-Banking | — | **NO COUNTERPART** |
| Remittance and collection of instruments | Remittance / Transfer | renamed |
| Pension related | Pension | renamed |
| Other products and services | Others | renamed |
| — | Insurance | **product-only, unlisted in the pack** |

| # | Document says | Actually |
|---|---|---|
| 28-C1 | the ten categories named in case 28.4 | **a different ten.** Five renamings, one split in two, two pack categories with no counterpart at all, one product category the pack never mentions. Asserted against the live endpoint (ruling 1) with the divergence reported by name in the failure message, not against the pack's hardcoded list |
| 28-C2 | blank category → **"Response is mandatory."** | **"Complaint category is required"** |

Confirmed working: the control is a native `<select>` with nothing pre-chosen; its options are **exactly**
the active master rows with nothing added or dropped; it carries no `multiple` attribute (so cases
28.6/28.7's "cannot multi-select" are guaranteed structurally, which is stronger proof than clicking);
changing the selection replaces rather than adds; and when the master is unreachable the screen
**fails closed** — a `categories-unavailable` notice plus a retry button, and no compiled-in fallback
list is substituted. That last path is not in the pack but is what stops a citizen filing under a
category no master governs.

### Block 29 — Facts of the Complaint: two self-contradictions and an undocumented gate

| # | Document says | Actually |
|---|---|---|
| 29-C1 | blank narrative → **"Response is mandatory."** | **"Facts of the complaint is required"** |
| 29-C2 | **case 29.6:** emoji are rejected | **accepted**, and the step advances with them. There is no character filter on the field at all |
| 29-C3 | **case 29.8:** leading/trailing spaces rejected | **accepted and advances.** The validator trims only to test emptiness (`!formData['complaintText']?.trim()`) — identical to 21-C3 for Address |
| 29-C4 | **case 29.11:** the field should "accept more than 5000 characters" **AND** display "Facts of the complaint cannot exceed 5000 characters." | **self-contradictory twice over.** It cannot both accept and reject; and `maxlength="5000"` (`html:695`) means the browser truncates, so no over-length value ever reaches Angular for a validator to complain about. Truncation asserted; **the message does not exist in the product**; the truncated 5000-character narrative is valid and advances |
| 29-C5 | blocks 28-32 together describe step 3 as category + facts + the account question | **two further questions are mandatory and appear in no manual case:** "Is your complaint against a Wallet transaction?" (`isWalletComplaint`) and "Is your complaint against a Business Correspondent?" (`isBusinessCorrespondent`), both → "Please select Yes or No" (`component.ts:1675, 1681`). A citizen who answers everything the pack documents **cannot leave the step.** Proven by filling exactly the pack's fields, observing the block, then releasing it by answering only those two |

Confirmed working: the textarea starts empty and retains a narrative verbatim; alphanumerics, special
characters, emoji and interior runs of spaces are all kept exactly as typed; a whitespace-only narrative
**is** rejected (the trim catches it — unlike `type="email"`, a textarea does not sanitise, so the spaces
genuinely reach the model); and exactly 5000 characters is accepted with no error. The cap is read from
the `maxlength` attribute in every test, never hardcoded (ruling 1).

### Block 30 — every case in the block tests a field that is not on the screen

| # | Document says | Actually |
|---|---|---|
| 30-C1 | **all 6 cases (30.5-30.10):** the Date of Disputed Transaction field is present, selectable, mandatory, accepts dates ≤ the filing date and rejects later ones with "Date cannot be greater than complaint filing date." | **the field does not exist** — defect D-B6. Proven three ways (no `[name="disputeDate"]`, no `type="date"` on the step, no label naming it) on an otherwise fully-rendered step. All 6 cases are **void as written**; none can be executed manually either |

### Blocks 31 + 32 — the account question and the Type of Account multi-select

Block 32's first two cases are block 31's 31.8/31.9 verbatim, so the radio is tested once.

| # | Document says | Actually |
|---|---|---|
| 31-C1 | blank radio → **"Response is mandatory."** | **"Please select Yes or No"** |
| 31-C2 | the field is labelled "Do you have an account with **RE**?" | the product **interpolates the chosen entity's name**. A manual tester searching for the literal "RE" will not find it. Not a defect — worth correcting in the pack so the case is executable |
| 32-C1 | **case 32.6:** four values — Savings Account / Loan Account / ATM-Debit Card / Credit Card | **ten** are offered. All four the pack names are present (one as "ATM / Debit Card"), so this is an omission in the pack, not a missing feature. The six extra are Current Account, Fixed Deposit, Recurring Deposit, Wallet, PPF Account, Others — and **none of them asks for an account identifier** (D-B7) |
| 32-C2 | blank Type of Account → **"Response is Mandatory"** | **"Please select at least one account type"** |
| 32-C3 | **case 32.9** requires MULTIPLE values be selectable; **case 32.10** requires the dropdown to CLOSE after a value is selected | **mutually exclusive.** A panel that closes on the first tick cannot accept a second. The product keeps it open, which is what makes 32.9 achievable; **32.10 is unimplementable alongside 32.9** and is recorded as the behaviour that actually serves the citizen |
| 32-C4 | it is described throughout as a "dropdown" | it is a **checkbox panel** behind a trigger, not a `<select>`. The mechanics the pack describes (opens on click, closes on outside click, single and multi select) all hold, but no `<select>` exists, so `selectOption`-style manual steps do not apply |

Confirmed working: nothing is pre-selected; Yes/No are mutually exclusive; answering Yes reveals the
control and No removes it from the DOM; the panel opens on click and dismisses on an outside click (which
matters because it overlays the account-number inputs below it); one or several types can be held; the
trigger reports every chosen label; deselecting the last type restores the placeholder.

**Harness note for whoever extends this:** the template writes `[name]="'accountType_' + at.value"`, but
Angular's ngModel name binding is **not** reflected as a DOM attribute — the rendered checkboxes are
genuinely nameless, so `input[name="accountType_savings"]` matches nothing. Select by visible label.

### Blocks 48 + 53 — tooltips and speech-to-text

| # | Document says | Actually |
|---|---|---|
| 48-C1 | **all 5 cases:** a tooltip exists per field, explains purpose + format + mandatory-ness, is reachable on hover/click/info-icon, disappears on mouse-out, and is consistent portal-wide | **FR-G-009 is unbuilt** — defect D-B9. A fifteen-entry `tooltips` map exists in the component and is rendered nowhere. All 5 cases are void as written; 48.8 additionally has no pass criterion (see §4) |
| 53-C1 | the pack treats speech-to-text as a working dictation feature | the control and its toggle wiring exist and work, but the button is shown **even where the browser has no Speech API**, where it silently does nothing — defect D-B10 |

Confirmed working for block 53: the Facts field is present and accepts typed input (53.2/53.3 — block 29
owns its validation in full, so it is asserted only to the depth 53 asks for); the mic control sits inside
the Facts field's own wrapper, is `type="button"` so it cannot submit the form, and carries an
`aria-label` that flips between "Start voice recording" and "Stop voice recording"; the toggle enters and
leaves the recording state correctly; and **a dictated transcript is appended to whatever the citizen has
already typed rather than replacing it** — the one dictation behaviour provable without audio, and worth
proving because replacing would silently destroy a hand-typed narrative.

The last three tests stub the `SpeechRecognition` constructor so the **wiring** can be proven without
audio. They assert nothing about recognition quality, language or capture; that half stays deferred.

---

## 3a. OPEN DECISIONS — cannot be resolved without a BA/product ruling

These are not defects and not wording slips. In each case the pack and the product disagree about what
the requirement *is*, and I cannot tell which side is wrong.

| # | Question | Why it matters |
|---|---|---|
| OD-B1 | **Should a non-RBIO (CEPC) entity skip the Regulated Entity Details screen?** The pack asserts it three times (blocks 24/26/27); the product has no such branch. | **86 of 145** entities are CEPC. If the pack is right, this is a missing feature on the majority path. If the product is right, 3 blocks of the pack are wrong. |
| OD-B2 | **Case 21.4 contradicts itself** — its description says the Address field "accepts special characters", its Expected Result says it "should not accept special characters". | Determines whether the current behaviour (accepts them) is correct. Cannot be tested either way until resolved. |
| OD-B3 | **Is "Response is mandatory." meant to be the single global mandatory-field message?** The pack expects it for pincode, state, district, address, credit-card radio, entity state, entity district and entity branch — **8 fields**. The product uses 8 different field-specific messages and the string "Response is mandatory." exists only for the eligibility Yes/No questions. | Either the product should be standardised onto one message, or the pack should be corrected in 8 places. A consistency requirement (FR-G-009 territory) rather than a bug. |
| OD-B4 | **Should the district of residence be mandatory?** It is marked mandatory on screen but not validated (D-B3). | If it should be enforced, D-B3 is a real defect. If it should be optional, the red asterisk is the bug. |
| OD-B5 | **Which set of ten complaint categories is authoritative (28-C1)?** Specifically: is there meant to be a category for **counterfeit notes and coins** and for **para-banking**, and is **Insurance** meant to exist? | A citizen with a counterfeit-note or para-banking grievance currently has no category to file it under, and **routing keys off the category name** — so this is not cosmetic. The five renamings are a wording decision; these three are substantive. |
| OD-B6 | **Are the wallet and business-correspondent questions (29-C5) meant to be mandatory?** They are enforced but appear in no manual case in blocks 28-32. | Either the pack is missing two blocks of cases, or two questions are wrongly blocking the step. Manual testers following the pack will report "Next does nothing" as a defect. |
| OD-B7 | **Which of case 32.9 and case 32.10 is the requirement?** They cannot both hold (32-C3). | Decides whether the panel staying open is correct behaviour or a defect. I have assumed 32.9 (multi-select) is the real requirement because the field is plainly designed for it; needs confirming. |
| OD-B8 | **Should the six account types that reveal no identifier field (D-B7) be offered at all?** Current Account, Fixed Deposit, Recurring Deposit, Wallet, PPF Account, Others. | Either each needs its own account-number field, or they should be removed from the list. As it stands a citizen can name an account type with nothing to identify the account by. |
| OD-B9 | **Should Type of Account / account numbers reach the server?** They are collected, validated as mandatory and shown on the review screen, but appear in **no field of the registration payload** (`ts:2158-2196`). | If an officer is meant to see which account the complaint concerns, this is a data-loss defect on a par with D-B6. If not, the mandatory validation is asking the citizen for data nobody will ever read. |

---

## 4. Deferred / not automatable

| Lines | Subject | Reason |
|---|---|---|
| 3866-3910 | Speech-to-text: mic capture, real dictation, English-only dictation, live start/pause/stop | Web Speech API cannot be driven headlessly without fake-audio plumbing. Deliberately deferred per brief; session C is not covering it either. Only field-presence (3853-3865) is automated — plus, beyond the brief, the toggle wiring and transcript-append behaviour, which a stubbed constructor can prove without audio. |
| 3596-3613 (case 48.8) | "Verify that the consistency of the tooltip is maintained across the portal" | **NEEDS SCOPING, not deferred for technical reasons.** The case has no pass criterion and no enumerated scope — "the portal" is never defined, and neither is what "consistent" means (same trigger? same styling? same wording pattern?). It could not be automated as written even if tooltips existed (they do not — D-B9). Case 48.5's "all forms defined in FR-G-009" has the same problem: the pack never lists those forms. The spec covers every field on all three wizard steps, which is the widest defensible reading. |

---

## 5. Running counts

Final, from end-to-end runs of all seven Session B specs together (not a sum of solo runs):
`133 passed, 2 skipped, 0 failed` — reproduced twice, 10.9m and 10.7m, the second after the
`waitForEntitySelect` change below.

| | Count |
|---|---|
| Specs written | 7 (+1 helper module) |
| Tests written | 135 |
| Passing | 133 |
| Failing | 0 |
| Skipped | 2 — and **these are not passes** (ruling 4) |

The 2 skipped are the `test.fixme` cases. They are skipped *because the product is defective*, so the
behaviour the manual pack asks for is **unverified, not verified**:

| Location | Defect it is parked on |
|---|---|
| `complainant-contact-fields.spec.ts:148` | **D-B1 (BLOCKING)** — no organisation category can leave step 1: the validator demands a First Name field that is never rendered for those categories |
| `complainant-contact-fields.spec.ts:89` | **D-B2** — "Person with Disabilities" is treated as an organisation, so it is shown Organisation Name/Landline and no First Name |

Note that D-B3 (district marked mandatory but never validated) is **not** among these — it is asserted
as a passing test that proves the wrong behaviour, because the step really does advance with district
blank. Same technique as the RBIO-routing proof at `re-details-cascading-dropdowns.spec.ts:164`: where
the defect is *observable*, a passing proof is stronger than a `fixme`, and `fixme` is reserved for cases
whose documented behaviour cannot be reached at all.

Both are written out in full and will start failing — correctly — the moment the defect is fixed, which
is the point of `fixme` over a deletion. A reader tallying "133/135" should read it as *133 verified, 2
blocked on defects*, never as 135 clean.

### A harness race that looked exactly like a product bug

Worth recording because it will recur. `fillPincodeAndWaitForLookup` originally waited for "a state
option is attached" after typing a new pincode. When **replacing** one pincode with another, the
previous lookup's options are still in the DOM, so that wait was satisfied instantly and the test read
the OLD state while the new lookup was still in flight. The failure appeared only in full-file runs and
passed in isolation — the classic signature.

Fix: clear the field first and assert the option lists are empty (which drives `onPincodeInput`'s empty
branch, resetting both lists synchronously) before typing the new value. The subsequent "options
attached" wait can then only be satisfied by the new lookup. **Not a product defect** — the product
resets correctly; the test was reading too early.

### A 20-second timeout that blamed the wrong control, on a rotating cast of tests

A full-suite run reported 2 failures in `complainant-address-state-district.spec.ts`; a solo re-run of
the same file failed a **different four** tests; isolating any one of them passed in seconds, and a
third run passed 24/24. Every failure had the identical stack: a `toBeVisible` timeout on
`select.entity-select-dropdown` inside `selectFirstEntityAndAdvance`, i.e. in shared *setup*, not in any
assertion the failing tests owned. That rotating-victim pattern is the signature of a setup-phase
failure, not of a bug in the tests named.

**The diagnosis is a reporting defect in the harness, not a product defect.** The entity `<select>`
lives inside `@if (currentQuestion?.type === 'select')` (html:52-119), and `currentQuestion` comes from
the **questions** master. When `GET /api/v1/eligibility/questions` fails *or returns zero rows*,
`questionsLoadFailed` is set (ts:943-978) and the select is never rendered at all — deliberately, since
there is no hardcoded fallback (serving a stale maintainability rule could wrongly deny a citizen the
Scheme). Meanwhile `.eligibility-card`, which `openWizard` waits on, stays perfectly visible. So a
questions-master hiccup surfaced as an opaque 20s timeout naming the entity select, attributed to
whichever test happened to be running.

Note this makes the two failure modes **distinct, and previously indistinguishable**:
- questions master down → no select at all
- entity master down (`.entities-load-error`, html:58-64) → the select renders but holds only its
  disabled placeholder, so the failure lands on the *option* wait instead

Fix in `helpers-forms-b.ts`: `waitForEntitySelect` now races the select against `.questions-load-error`,
then races the select's first option against `.entities-load-error`, clicks the corresponding retry
button once (it exists precisely so a transient failure is recoverable), and otherwise throws naming
which master is unavailable. Both `selectFirstEntityAndAdvance` and `gotoComplainantDetailsWithEntity`
route through it. Verified by forcing a 500 on each endpoint in turn: each now produces
`QUESTIONS MASTER UNAVAILABLE` / `ENTITY MASTER UNAVAILABLE` instead of an opaque timeout.

Two traps found while building it, both worth knowing before writing a similar waiter:

1. **`Locator.isVisible({ timeout })` ignores the timeout** — the Playwright types mark the option
   `@deprecated This option is ignored` and the call "does not wait... and returns immediately". A poll
   built on it reports "not rendered" while Angular is still mounting, i.e. it *reintroduces* the very
   race it appears to guard. `waitFor({ state: 'visible', timeout })` is the call that waits.
2. **The two masters resolve independently**, so the select appears the moment the *questions* request
   lands — usually while the *entities* request is still in flight. Checking for the entities notice at
   that instant finds nothing even when the entity fetch is about to fail, so the failure still landed on
   the option wait. The second race is what closes that window; a sequential check does not.

**Root cause of the hiccup itself is NOT established.** Probing after the fact, both masters returned
200 on 40 sequential and 60 fully-parallel requests, with full payloads and no throttling — so the
throttle hypothesis (see the parallel-session collision notes) is not supported by any evidence I could
gather, and `test-results/` had been emptied so no error-context survived. The spec has since passed
24/24 twice. **If it recurs, the error message will now say which master failed instead of blaming the
select** — that is the actionable outcome here. A BA reading this should treat "the entity dropdown
times out intermittently" as *not yet reproduced*, not as a closed issue.
