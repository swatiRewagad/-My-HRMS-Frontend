# QA Pass 4 — Session A findings

**Scope:** Login / OTP / CAPTCHA surface + Complainant identity fields (166 manual cases).
**Source:** `docs/prompts/prompt_testcase_cms_frontemd.txt`, line ranges per block below.
**Environment:** backend 8092 (`dev-local`), UI 4202, MySQL `cms_db`, automation run `2026-09-24`.

> **Read this first.** This document has two layers. The automation pass (2026-09-24) recorded what the
> product *did*; the rulings pass (2026-09-25) then decided what it *should* do, and **8 of the findings
> were fixed in code** — see the ruling table in §6. Entries carry **FIXED**, **DECLINED** or **STILL
> AWAITING A RULING** in their headings, and where something changed the *old* behaviour is kept as "Was:"
> because the reasoning is the deliverable, not the diff. **One finding, A-D6, is RETRACTED** — it was a
> wrong diagnosis, and §3d explains the trap that produced it rather than quietly deleting it.

All specs run with `UI_BASE_URL`/`APP_BASE_URL`=4202, `API_BASE_URL`=8092,
`PW_OUTPUT_DIR=/c/tmp/pw_A/test-results`.

---

## 1. Coverage table

**159 tests across 7 spec files** (73 of them generated inside `helpers-auth-a.ts`), 0 failing.
Counts below are post-ruling: the 2026-09-25 rulings turned several fixmes into passing tests and deleted
others outright.

| Block | Lines | Cases | Spec file | Tests | Status |
|---|---|---|---|---|---|
| 3 | 63-80 | 4 | `e2e/public/login-mobile-field.spec.ts` | 3 | 2 pass, 1 **fixme** (A-D1, unruled) |
| 4 | 81-128 | 13 | `e2e/public/login-mobile-field.spec.ts` | 12 | 12 pass |
| 5 + 14 | 129-146, 772-819 | 7 + 16 | `e2e/public/login-captcha.spec.ts` | 14 | 13 pass, 1 **fixme** (A-D2, unruled) |
| 6 + 25 | 147-155, 1658-1678 | 3 + 6 | `e2e/public/login-consent-declaration.spec.ts` | 6 | **6 pass** — A-C8 fixme un-fixmed |
| 7 | 156-217 | 18 | `e2e/public/otp-field-validation.spec.ts` | 15 | 14 pass, 1 **fixme** (A-D3); A-C9 fixme deleted (declined) |
| 36 | 2571-2612 | 8 | split: CAPTCHA half in `login-captcha.spec.ts`, OTP half in `otp-field-validation.spec.ts` | — | covered, no new spec |
| 8 + 12 + 13 | 218-314, 578-686, 687-771 | 8 + 18 + 9 | `e2e/public/complainant-category-age-gender.spec.ts` | 28 | **28 pass** — A-C11 and A-D4 fixmes resolved; A-D5 inverted to 9 organisation categories |
| 9 | 315-400 | 13 | generated in `helpers-auth-a.ts` | ~19 | pass + **fixme** for A-C16/A-C17 (both unruled) |
| 10 | 401-490 | 13 | generated in `helpers-auth-a.ts` | ~19 | pass + **fixme** for A-C16/A-C17 |
| 11 | 491-577 | 13 | generated in `helpers-auth-a.ts` | ~19 | pass + **fixme** for A-C16/A-C17/**A-C15** |
| 15 | 820-913 | 13 | generated in `helpers-auth-a.ts` | ~19 | pass + **fixme** for A-C16/A-C17 |
| 16 | 914-963 | 4 | `e2e/public/complainant-name-fields.spec.ts` | 4 | 4 pass |
| — | — | — | A-C14 cross-field length probe | 1 | 1 pass |
| 60-62 | 4177-4266 | 25 | 22 mapped to existing `tracking-table`/`tracking-authz`; 3 gaps in `e2e/public/tracking-field-validation.spec.ts` | 3 | 3 pass (+ mapping table in §3d) |

**Every remaining `fixme` is gated on one of the three unruled items** — A-C15 (is Surname mandatory),
A-C16 (letters-only names), A-C17/A-C14 (length caps) — plus A-D1 and A-D2/A-D3, which are defects awaiting
a fix rather than a ruling. Nothing is fixmed for a reason that has already been decided.

---

## 2. Contradictions between the manual cases and observed behaviour

> These are the highest-value output of this session. Each was verified against running code on 8092,
> not inferred. Per standing ruling 2 the spec asserts **observed** behaviour and the contradiction is
> recorded here rather than silently resolved either way.

### A-C1 — "OTP expires after configured time - 5 minutes" (line 197-199) — **RULED: 5 minutes. FIXED.**
- **Manual claim:** 5 minutes. **The manual was right and the environment was wrong.**
- **What was actually observed:** `POST /api/v1/citizen/auth/send-otp` returned `expiresInSeconds: 600` on
  8092 — but only there. `application.yml:181` has always been `${OTP_EXPIRY_MINUTES:5}` and
  `AuthSecurityProperties.java:24` defaults to `5`. **Prod always enforced 5.** The `10` was local-only, in
  `application-dev-local.yml`, sitting next to genuine throttling relaxations
  (`max-resend-per-hour: 99`, zero cooldown) and evidently swept up with them.
- **User ruling:** *"It should be 5 minutes."* `application-dev-local.yml` now sets `expiry-minutes: 5`.
- **The line drawn, for future dev-local edits:** a **stated rule the citizen is shown** ("valid only for
  5 minutes") must not differ between environments — otherwise dev and prod disagree about *behaviour under
  test*, not merely about throughput. **Throttling counters may** be relaxed locally; they are invisible to
  the citizen and only exist to slow abuse. The resend/verify limits were therefore left loose.
- **Specs unaffected:** every expiry assertion reads `expiresInSeconds` from the response and computes both
  sides of the boundary from it. No `300` or `600` literal appears in any spec, so the config change moved
  nothing. (That was the point of writing them that way.)

### A-C2 — "Captcha should be case-sensitive" (line 800) — **RULED: manual case DECLINED. No code change.**
- **Manual claim:** the CAPTCHA is case-sensitive.
- **Observed:** `CaptchaService.verifyCaptcha` (line 94) hashes `userAnswer.trim().toLowerCase()` and
  `saveCaptchaSession` (line 109) stores `hashValue(answer.toLowerCase())`, compared with
  `MessageDigest.isEqual`. The comparison is **case-INSENSITIVE by deliberate construction on both sides.**
- **User ruling:** *"case-sensitive should not be there"* — the shipped behaviour is correct and **manual
  case 800 is declined.** No code changed.
- **Reasoning recorded for the BA:** lower-casing a visual CAPTCHA is standard accessibility practice; the
  rendered glyphs are intentionally ambiguous between cases, so a case-sensitive check fails citizens for
  a distinction the image does not reliably convey. (Note also that this deployment serves a **MATH**
  captcha, where case is meaningless in the first place.)
- **Test:** `login-captcha.spec.ts` asserts case-insensitive acceptance, retitled "as ruled", with the
  declined manual case named in the comment so nobody re-raises it.

### A-C3 — "Captcha field should not accept extra spaces along with correct captcha" (803-805) — CONTRADICTED
- **Manual claim:** a correct answer with extra surrounding spaces must be rejected.
- **Observed:** the same `userAnswer.trim()` at `CaptchaService.java:94` means leading/trailing
  whitespace is **stripped and the answer is accepted.**
- **Assessment:** trimming user input is correct behaviour; a citizen penalised for a trailing space
  they cannot see is a usability defect, not a security control. The manual expectation looks wrong.
- **Needs a decision** from the BA, same as A-C2.

### A-C4 — "Captcha field should accept only spaces" (808-810) — SELF-CONTRADICTORY CASE
- The Description says *"To verify if captcha field accepts only spaces"* and the Expected Result says
  *"Captcha field **should accept** only spaces"*, while the neighbouring case at 797-799 says the field
  **should not be kept blank**. A whitespace-only answer is blank after trim, so these two expectations
  are mutually exclusive.
- **Observed:** `verifyCaptcha` returns false for `userAnswer.isBlank()` (line 77), and the client
  refuses on `!this.captchaInput.trim()` with "Please enter the CAPTCHA." — i.e. a spaces-only answer is
  **rejected**, which matches 797-799 and contradicts 808-810.
- **Almost certainly a typo in the manual case** (missing "not"). The spec asserts rejection.

### A-C5 — "does not accept alphabetic/special/… data" is ambiguous across ~30 cases
- Blocks 4, 7, 9, 10, 11, 15 all state "field should not accept X". This has two incompatible readings:
  (a) the keystrokes are filtered and never appear, or (b) they appear and submission is refused.
- **Observed for the mobile field:** `<input type="tel" maxlength="10" [(ngModel)]="mobile">` has **no
  input filter**, so reading (b) holds — the characters are retained and `sendOtp()` refuses them on
  `^[6-9]\d{9}$` with "Enter a valid 10-digit Indian mobile number starting with 6-9."
- **Observed for the OTP boxes:** these DO filter — `onOtpInput` applies `value.replace(/\D/g, '')`,
  so reading (a) holds there. **The two fields behave differently** and the manual cases use identical
  wording for both.
- **Consequence:** every "does not accept" case needs to say which it means, or QA and dev will
  disagree about whether the product passes. The specs assert both halves explicitly and say which one
  the product implements.

---

## 3. Product defects found

### A-D1 — There is no anonymous "Login" control anywhere on the landing screen
- **Manual cases affected:** 67-70 (block 3), and the entry step of 772-777, 1658-1662, 2571-2575.
- **Evidence:** `public-layout.component.html` renders every login-related control inside
  `@if (authService.isAuthenticated())` (My Complaints, user menu, Logout). The only
  `routerLink="/public/login"` in the layout is the "Log in again" link inside the session-**timeout**
  banner at line 104, which renders solely after an inactivity logout
  (`@if (authService.timedOut() && !authService.isAuthenticated())`).
  `public-home.component.html` has `file-complaint`, `track`, `appeal` and `feedback` entry points and
  **no login button**.
- **So:** a first-time visitor can only reach the login screen by clicking a guarded action (File a
  Complaint) and being bounced by `publicAuthGuard`. Every manual case that begins "click on Login /
  File a complaint button" is only half-satisfiable.
- **Test:** `login-mobile-field.spec.ts` — *"a Login control is present on the landing screen for an
  anonymous visitor"*, marked `test.fixme()`. It is written against QA's expectation, so it starts
  passing the moment the control is added.
- **Not fixed even after ruling 5 was lifted, deliberately.** Unlike A-D4/A-D5, this is not a bug with an
  obvious correct value — it is a **navigation design question**: where should a Login entry point sit on
  the landing page, what should it be labelled, and does it belong beside the four existing action cards or
  in the header beside the authenticated menu? Guessing an answer would put an unreviewed control on the
  portal's front door. **Needs a design/BA call**, not a code change.

---

### A-C6 — CAPTCHA error wording differs from the quoted string (line 2586-2588)
- **Manual claim:** *"Captcha entered is invalid. Please try again."*
- **Observed:** the server and component both say *"Invalid CAPTCHA. Please try again."*
- Same meaning, different words. The spec asserts the **shipped** wording so a correct product is not
  failed over a paraphrase. Low priority, but the manual script should be aligned so testers don't raise
  it as a defect.

### A-C7 — Invalid-OTP wording differs, and the quoted string is malformed (line 2606-2608)
- **Manual claim:** *`error message Invalid OTP ? "The OTP you entered is incorrect. Should be displayed`*
  — an unbalanced quote and a stray `?`, so the intended text is ambiguous.
- **Observed:** the component shows *"Incorrect OTP. Please try again."*
  (`public-login.component.ts:298`).
- The spec asserts the shipped wording. The manual case needs cleaning up regardless.

### A-C8 — "Consent declaration is mandatory." (1670-1672) — **RULED: standardise. FIXED.**
- **Manual claim:** leaving the declaration unticked must display *"Consent declaration is mandatory."*
- **Was:** the rule **was** enforced, but by disabling the button, and nothing was ever said.
  `send-otp-btn` was `[disabled]="!captchaInput || !consentChecked || cooloffActive() || loading()"`, so
  with the box unticked the control was inert and no submit handler ran. `sendOtp()` *did* carry a
  consent guard — *"You must accept the data processing declaration to continue."*
  (`public-login.component.ts:137-140`) — but it was **unreachable from the UI** for exactly that reason.
  The string "Consent declaration is mandatory." appears nowhere in the product, and still does not.
- **Why this was more than a wording nit:** it contradicted the team's own documented decision one field
  over. The comment at `public-login.component.ts:81-85` explains that `!mobile` was **deliberately
  removed** from the disable condition because "a disabled button explains nothing: a citizen who has not
  filled the number in gets no message at all, just a control that will not respond." The identical
  argument applies to the consent box, which — being the last control above the button — is the one most
  likely to be missed.
- **User ruling (A-C8/A-C9 jointly):** *"please standardise it."* Standardised on **enable-and-explain**,
  the pattern the team already argued for and which the OTP field already used:
  > **The rule, now stated once in the template:** no mandatory **field** is a disable condition. Only
  > **transient** states — `cooloffActive()`, `loading()` — disable, because those are not something the
  > citizen can correct by typing. Every omission is refused *inside the handler*, with its own wording,
  > before any request is sent.
- **Now:** `[disabled]="cooloffActive() || loading()"` on both `send-otp-btn` and `verify-btn`. The three
  guards already in `sendOtp()` (blank mobile, bad format, blank CAPTCHA, no consent) became **reachable**
  without being rewritten — this fix deleted conditions, it did not add logic. Nothing is submitted and
  the lockout counter is not burned either.
- **Tests:** `login-consent-declaration.spec.ts` — the `test.fixme('an explicit message explains that the
  declaration is mandatory')` is **un-fixmed and passing**; the toggle test now clicks Send OTP and asserts
  the banner both ways; the no-request test asserts the banner *plus* zero `otp-inputs` *plus* zero DB rows.
  `consent-dpdp.spec.ts:154-169` likewise swapped `toBeDisabled()` for a click + banner, keeping
  `expect(sendOtpCalled).toBe(false)` as the DPDP-relevant check.
- **Note for whoever reads the diff:** assertions flipped from `toBeDisabled()` to *enabled-and-refused*.
  **That is the fix landing, not a regression.** A note to that effect is in the spec header.
- **Manual script still needs one edit:** the quoted string *"Consent declaration is mandatory."* is not
  what the product says. The shipped wording is *"You must accept the data processing declaration to
  continue."* — which is better (it names the action), so the **case should be reworded**, not the code.

### A-N1 — `send-otp` deliberately does NOT require consent (noted so it is not "fixed")
- A reader of block 25 would assume the API refuses a consent-less OTP request. It does not:
  `CitizenAuthController.sendOtp` records consent only `if (consentGiven(body))` and issues the OTP
  regardless. This is intentional and documented (UST5) — **the same endpoint serves the complaint
  tracker**, which processes no new personal data and therefore collects no consent. Enforcement lives at
  complaint *submission* (`declarationAccepted`), which `consent-dpdp.spec.ts` asserts.
- So the declaration gates **filing**, not **authentication**, which is why every block-25 case is
  UI-level. A test pins this distinction so that "consent should be enforced at send-otp" is never added
  on the assumption it was an oversight — doing so would break the tracker.

---

## 3a. Additional product defects

### A-D2 — A single CAPTCHA typo shows "Too many attempts" and locks the form for 5s
- **Manual cases affected:** 795-796, 134-135, 2582-2588 — all of which require the *CAPTCHA* error to be
  displayed.
- **Severity:** this is the most user-visible defect found so far. Every citizen who mistypes the CAPTCHA
  once — a common, expected event — is told they have made too many attempts and is locked out of the
  form.
- **Root cause, two separable problems:**
  1. **Wrong message.** `public-login.component.ts:173-178` sets
     `loginError = 'Invalid CAPTCHA. Please try again.'` and then calls
     `startCooloff(body.retryAfterSeconds)` when `body.cooloffActive` is true. `startCooloff` (line 411)
     **unconditionally reassigns** `loginError = 'Too many attempts. Please wait N seconds.'`, destroying
     the CAPTCHA message in the same tick it was set.
  2. **Lockout arms on the FIRST failure.** `CitizenAuthController.sendOtp` calls
     `cooloffService.recordFailedAttempt` on every rejected CAPTCHA, and
     `CooloffService.calculateCooloffSeconds` indexes the progression at `failedAttempts - 1` — so
     attempt #1 already yields a non-zero penalty (dev-local 5s; **production default 30s**, from
     `AuthSecurityProperties.Cooloff.progressionSeconds = [30, 60, 120, 300, 600]`). The response to a
     first typo therefore carries `cooloffActive: true`, which is what makes (1) reachable at all.
- **Verified directly against 8092** with `login_cooloffs` cleared first — the very first wrong answer
  returns:
  `{"error":"INVALID_CAPTCHA","message":"Invalid CAPTCHA. Please try again.","cooloffActive":true,"retryAfterSeconds":4}`
- **Observed banner:** `"Too many attempts. Please wait 5 seconds. (5s remaining)"`, and the mobile,
  CAPTCHA and Send OTP controls are all disabled meanwhile
  (`[disabled]="cooloffActive() || loading()"`). **In production that is a 30-second lockout for one
  typo.**
- **Test:** `login-captcha.spec.ts` — *"the screen shows the CAPTCHA error, not a lockout message, after a
  single wrong answer"*, `test.fixme()`, written against QA's expectation. The API-level refusal is
  asserted and passing in the sibling test; only the on-screen message is broken.
- **Suggested fix, still not applied after ruling 5 was lifted:** have `startCooloff` take the message to
  display rather than overwriting it, and/or start the progression at attempt 2 so a single typo carries
  no penalty.
- **Why it was left alone:** the first half (stop clobbering the message) is safe and local. The second half
  changes an **anti-abuse threshold** in shared security code (`CooloffService` keys on fingerprint + IP and
  is used by the OTP path too), and the BA must confirm the intended attempt count — QA's lockout cases all
  describe *repeated* failures, which suggests the progression should start at 2, but shifting it also
  weakens brute-force protection on a public endpoint by one free attempt. That is a security decision, not
  a bug fix. **Recommendation: fix the message clobbering now, decide the threshold separately.**

### A-D3 — The OTP boxes filter the MODEL but not the SCREEN: rejected characters stay visible
- **Manual cases affected:** 177-192 (OTP does not accept alphanumeric / alphabetic / special / spaces).
- **Observed, measured not assumed.** `onOtpInput` (`public-login.component.ts:261-269`) does
  `const val = input.value.replace(/\D/g, ''); this.otpDigits[index] = val ? val[0] : '';` — so the
  **code** is clean and a letter never becomes part of the OTP. But the boxes are bound **one-way**,
  `[value]="otpDigits[$index]"`, and when the filter reduces a keystroke to `''` the bound value is
  *unchanged* (already `''`), so Angular has no diff to write back and **the character the browser
  already painted stays in the box.**
- **What the citizen sees:** type `abcdef` — six visibly filled boxes — click Verify, and the screen says
  *"Enter all 6 digits"*. No indication that anything was rejected, or which character. Spaces are worse:
  six boxes that look empty but are not, with the same message.
- **Verified:** typing `a1b2c3`, `abcdef`, `@#$%^&` and six spaces each leaves the typed text on screen
  while `verifyOtp()` refuses on `code.length < 6` and **no `/verify-otp` request is made at all**
  (asserted). So the security half is sound; the feedback half is broken.
- **Test:** `otp-field-validation.spec.ts` — four passing tests assert the observed behaviour (not
  accepted as a code, nothing sent, no session, **and** the characters still displayed), plus
  `test.fixme('rejected characters never appear in the OTP boxes')` written against QA's reading. The
  passing tests carry a message telling the next reader to delete the "still displayed" assertion and
  un-fixme the other when this is fixed.
- **Suggested fix:** write the filtered value back (`input.value = val`), or bind the boxes with `ngModel`
  so the model is the single source of truth.
- **Not applied, but only because it was outside the rulings.** Unlike A-D1 and A-D2 this needs no decision —
  the intended behaviour is unambiguous and the change is one line in one handler. It is the **cheapest
  remaining win in this batch** and is recommended for the next pass; the fixme is already written against
  the correct behaviour, so it will go green with no test edits.

### A-C9 — "when the OTP field is blank the Verify button should be disabled" (212-215) — **RULED: manual case DECLINED. No code change.**
- **Manual claim:** blank OTP ⇒ Verify disabled.
- **Observed:** `verify-btn` is `[disabled]="cooloffActive() || loading()"` — **the OTP's emptiness is not
  in that condition.** The button stays enabled and `verifyOtp()` refuses on `code.length < 6` with
  *"Enter all 6 digits"* (`public-login.component.ts:280`). No request is sent.
- **The interesting part:** this was the **opposite mechanism** to the consent checkbox one card earlier
  (A-C8), where the rule *was* in the disable condition and therefore no message was ever shown. The same
  screen enforced two adjacent mandatory fields two different ways, and the manual script expected, in
  each case, the mechanism the product used for the *other* one. That symmetry is why the two were ruled
  together.
- **User ruling (A-C8/A-C9 jointly):** *"please standardise it"* — standardised **towards this field**, not
  away from it. The OTP field was already correct, so **no code changed here**; it is now the reference
  implementation and A-C8/A-C18 were brought into line with it. Manual case 212-215 is **declined**.
- **Why this direction and not the other:** it is the behaviour the team argued FOR in the comment at
  `public-login.component.ts:81-85` ("a disabled button explains nothing"). Disabling on a blank field also
  makes the failure *undiagnosable* — a citizen cannot tell an unmet precondition from a broken page.
- **Test:** the observed behaviour was already asserted (enabled, then refused with the message); the
  `test.fixme('the Verify button is disabled while the OTP field is blank')` is **deleted**, since it
  encoded an expectation that has now been ruled against and would otherwise read as outstanding work.

---

## 3b. Complainant Details — Category, Age, Gender (blocks 8, 12, 13)

Spec: `e2e/public/complainant-category-age-gender.spec.ts` — 29 tests, 27 pass, 2 `fixme`.
Mobiles `9876510501`–`9876510520`.

**Preliminary — both dropdowns are native `<select>`.** ~12 manual cases across blocks 8 and 13 read
"dropdown should open when clicked" / "should close after selecting a value". A native select's popup is
drawn by the OS, not the DOM, and is invisible to Playwright. Those cases are asserted as the control's
real contract instead: no `multiple` attribute, no `size` attribute, and exactly one `option:checked` after
two successive selections — i.e. *the second selection replaces the first*, which is what "cannot select
multiple values" means for this widget. Not deferred; re-expressed.

### A-C10 — Gender offered a FIFTH, undocumented value — **RULED: remove it. FIXED.**
- **Manual claim (687-771):** the dropdown holds four values — Male, Female, Transgender, Do not wish to
  disclose.
- **Was:** there were **five**. `file-complaint.component.html` added `<option value="other">Other` after
  `not_disclosed`. All four documented values were present and selectable, so no case *failed* — but any
  test written to "assert the dropdown contains exactly the listed values" would, and a UAT reviewer
  counting options would query it.
- **User ruling:** *"Only male female transgender and do not wish to disclose."* The `other` option is
  **removed** from the template, and the matching `other: 'Other'` entry is removed from
  `getGenderLabel()`'s map so no stored value can render a label the dropdown can no longer produce.
- **Why the manual was right:** the RB-IOS gender vocabulary is the three-plus-decline set. "Do not wish to
  disclose" already covers the case `other` was presumably reaching for, so the fifth value only
  fragmented the data.
- **Test:** now `toEqual(GENDERS_DOCUMENTED)` — an **exact** four-value comparison, not a containment
  check. A re-added fifth option fails on the array, and so does a reordering, which is the correct
  sensitivity for a documented vocabulary.

### A-C11 — "Response is mandatory." does not exist, and the category was not validated at all — **FIXED**
- **Manual claim (249-252):** leaving Complainant Category blank shows *"Response is mandatory."*
- **Observed:** that string appears nowhere in the codebase, and still does not — the shipped wording is
  *"Complainant category is required"*, so the **manual case should be reworded** rather than the code.
- **Was:** `validateCurrentStep()` for step 1 checked `firstName`, `pincode`, `state`, `address` and
  `email` — **`complainantCategory` was not in the list.**
- **Why a blank category nevertheless could not get through:** indirectly and by accident. With nothing
  selected, *neither* conditional block rendered (`@if (isIndividualCategory())` and
  `@if (!isIndividualCategory() && formData['complainantCategory'])`), so `firstName` was never on screen,
  so the `firstName` check failed and Next was refused — **with the error attached to a field nobody could
  see.** The outcome was right for a reason the citizen could not discover. That dead end is A-C11b below.
- **Now:** the category is validated **first and explicitly**, because it decides *which* name field exists:
  a blank category sets `validationErrors['complainantCategory']`, and a `@if` for that key was added
  **outside** both category-conditional blocks — it is the one error reachable while neither block renders.
- **Test:** the blank-category test now asserts `.field-error` filtered by `/categor/i` has count **1**,
  checked twice, which is what proves the dead end is gone rather than merely that Next was refused. The
  `test.fixme` for "Response is mandatory." is **deleted** — the rule is enforced and visible; only the
  wording differs, and that is a manual-script edit tracked in §6.

### A-C11b — the error that blocked a blank category was never displayed (DEFECT, dead end) — **FIXED**
Found by a failing test, then confirmed with an instrumented probe.

- **Was:** **both** renderers of `validationErrors['name']` sat *inside* the two category blocks — under
  First Name and under Organisation Name. With the category blank neither block existed, so the message
  that was actually refusing Next **had nowhere on the page to appear.**
- **Measured citizen journey** (probe output, verbatim):
  - press Next → `["Valid 6-digit pincode is required", "Enter valid pincode to auto-fill state", "Address is required"]`
  - correct all three → press Next → `[]` — **zero messages, still on step 1.**
- So the citizen fixed everything they were told about, pressed Next, and the form silently refused with
  nothing left on screen to act on. A hard dead end reachable by simply not touching the first dropdown —
  and the dropdown's placeholder is `disabled`, so this was the state every citizen *starts* in.
- **Severity:** highest in this batch. It is the very first field of the wizard and the failure mode was
  silence, not a wrong message.
- **Fixed under the lifted ruling 5, together with A-D5, since both come from the one overloaded key:**
  `validateCurrentStep()` now validates `complainantCategory` **before** any name, and the template renders
  its error immediately after `</select>`, **outside** both conditional blocks, with a comment saying why it
  must live there ("this is the one error a citizen can hit while neither block is rendered"). The select
  also gained `[class.invalid]`.
- **Test:** the former *"...and the error that blocks it is never displayed"* is now the inverse — the
  category error is asserted `toHaveCount(1)` on **both** successive refusals, which is the assertion that
  actually proves the dead end is gone (a single check could be satisfied by a message that appears once and
  is then wiped).
- **Harness note (still true, and the main trap in this area):** `validationErrors` is only recomputed on
  click, so the *previous* messages stay painted until then. A snapshot read with `allTextContents()`
  immediately after a click sees stale text. Use auto-retrying `expect(locator).toHaveCount(...)`, never a
  bare array read.

### A-C12 — Age has no input filter; `pattern` and `inputmode` are inert
- **Manual claim (578-686):** the Age field "should not accept" alphabetic / alphanumeric / special /
  emoji data.
- **Observed:** the control is `type="text"` with `maxlength="3"`, `pattern="[0-9]*"` and
  `inputmode="numeric"`. `pattern` does nothing outside native form submission and `inputmode` is a
  soft-keyboard hint only — so **every character is accepted into the box.** Rejection happens afterwards,
  via `validateAge()` on `ngModelChange`, as a message. Same "does not accept" ambiguity as A-C5.
- **Silently accepted with no message at all:** `045`, `" 45"`, `"45 "`, `4.5`. `Number()` coerces all four,
  so `isNaN` is false and the range check passes. Only an *interior* space (`4 5`) is rejected.
- **Test:** each rejected class asserted by the product's own wording (`/must be a number/`,
  `/between 1 and 150/`); the silently-accepted set asserted as accepted, in one test named for A-C12 so
  the entry is findable from the run output.

### A-C13 — the Senior Citizen message never says which category it means
- `validateAge()` emits *"Age must be 60 or above for Senior Citizen"* when
  `complainantCategory === 'senior_citizen' && age < 60`. Correct rule, but the wording reads as a property
  of the person rather than a consequence of the dropdown above. Cosmetic; recorded because the manual
  quotes a message naming the category.
- Verified both ways: 60 is accepted for Senior Citizen; 59 raises no error for Individual.

### A-D4 — Person with Disabilities was shown the ORGANISATION form — **FIXED**
- **Was:** `isIndividualCategory()` returned `cat === 'individual' || cat === 'senior_citizen'`.
  **`pwd` was missing.**
- So selecting *Person with Disabilities* — unambiguously a natural person, and a category RB-IOS
  singles out for priority handling — rendered Organisation Name and Organisation Landline, and **no First
  Name, no Age, no Gender.** The complainant's own name could not be captured, and the age/gender data the
  PwD priority rules read was silently lost.
- **Now:** the list is a named constant so the membership is stated once and greppable:
  `private static readonly INDIVIDUAL_CATEGORIES = ['individual', 'pwd', 'senior_citizen'];`
  `getCategoryLabel()` already mapped `pwd: 'Person with Disabilities'`, so no other consumer needed
  touching.
- **Test:** the observed-defect test and its `test.fixme` **collapsed into one passing test**; the Age
  coverage now loops `['individual', 'pwd', 'senior_citizen']` so the three individual categories are held
  to the same contract rather than `individual` standing in for all of them.

### A-D5 — ten of the twelve categories could not leave step 1 at all — **FIXED**
- **Was:** step 1 required `firstName`, but First Name only renders when `isIndividualCategory()` is true.
  For the other ten categories the field was **never on screen**, so `validationErrors['name']` was set on
  every Next and could never be cleared. Organisation Name was *not* accepted in its place, despite sharing
  the same `validationErrors['name']` key.
- **Measured across all ten** rather than asserted for one, because "which categories are affected" is the
  first question the BA will ask. Result: **all ten** — pwd, individual_business, proprietorship,
  partnership, msme, association, trust, limited_company, government_department, psu.
- Only `individual` and `senior_citizen` could complete the wizard. **No organisation could file a complaint
  through this portal at all.** With A-D4 folded in, that included every complainant with a disability.
- Same root cause as A-C11b: one `validationErrors['name']` key doing duty for two fields that are never
  both present.
- **Now:** the validator branches on what is actually **rendered** — individual categories require
  `firstName`, everything else requires `organizationName` ("Name of complainant is required"). So a filled
  organisation name advances, and a blank one is refused **against the field the citizen can see**. Two
  defects, one edit, because both were the overloaded key.
- **Tests, deliberately two-sided** — a fix that merely stops blocking would be indistinguishable from
  deleting the validation:
  1. The A-D5 test is **inverted**: it loops the nine organisation categories (`sessionAMobile(530 + i)` —
     a distinct number per iteration, because the OTP cooldown keys on the number) and asserts each one
     reaches `input[name="isCreditCardComplaint"]` on step 2.
  2. A **new** test at `sessionAMobile(521)` proves a blank organisation name is *still* refused, and that
     the message does **not** mention "first name" — i.e. refused for its own reason, not the old one.

---

## 3c. Complainant Details — the four name fields and Mobile (blocks 9, 10, 11, 15, 16)

Spec: `e2e/public/complainant-name-fields.spec.ts` — **78 tests, 0 failing** (73 generated in
`helpers-auth-a.ts` + 5 declared in the spec file itself: the A-C14 probe and block 16's four).
Mobiles `9876510601`–`9876510685`.

**Why generated.** Blocks 9/10/11/15 are the same 13 assertions applied to First Name, Middle Name,
Surname and Organization Name — 52 manual cases whose text differs only in the field label. Hand-writing
them yields 52 near-identical tests in which a copy-paste slip is invisible. The driver takes each field's
contract as data and derives the cases, so a rule stated once is applied identically to all four.

**The spec now carries `qaClaimsMandatory` alongside `blocksNextWhenBlank`** — two separate columns, so the
two can disagree. Where they do, *that disagreement is the finding*, which is the whole point of keeping
them apart rather than collapsing them into one "is it required" flag. Current state:
`firstName` true/true, `middleName` false/false, `lastName` **false/true** (A-C15, still open),
`organizationName` true/true.

The blocking branch also now asserts the refusal is **visible**
(`.field-error` filtered by `/name/i`, `not.toHaveCount(0)`), not merely that Next was refused — the
A-C11b lesson applied to the generated cases.

### A-C16 — "Only letters are allowed." does not exist; NO name field validates characters at all
- **Manual claim, ×20 cases** across the four blocks: each field "should not accept" alphanumeric /
  numeric / special-character / emoji / leading-and-trailing-space input, each with the message
  *"Only letters are allowed."*
- **Observed:** that string appears **nowhere in the codebase** (the only near-match is an unrelated
  reference-number message at `file-complaint.component.ts:2345`). None of the four inputs carries a
  `pattern`, and none has an `(ngModelChange)` validator — contrast Age, which does. **Every character
  class is accepted verbatim**, with no message and no filtering: `Ram3sh9`, `9876543210`, `@#$%^&*!`,
  `Ram😀esh` and `"  Ramesh  "` all survive into the model unchanged.
- **Consequence:** a complainant's name may be submitted as pure digits or emoji. Only `firstName` is
  checked at all, and only for `.trim()` emptiness.
- **Test:** accepted-silently asserted for all 7 classes × 4 fields; QA's expectation carried as
  `test.fixme('<Field> rejects <class> with "Only letters are allowed."')` × 5 × 4 = 20 fixmes, so the day
  validation lands the fixmes start passing and name this entry.
- **STILL AWAITING A RULING** — the one item in this section not settled by the 2026-09-25 rulings, and it
  gates 20 fixmes. Should names be letters-only? Note that a letters-only rule would reject legitimate
  Indian names containing `.`, `'` or `-` (e.g. *D'Souza*, *Jr.*), so the manual's rule as worded is
  probably too strict — which is worth settling before it is implemented, not after. Recommended wording if
  it is to be enforced: letters, spaces, apostrophe, hyphen and period.

### A-C17 — no name field has a `maxlength`; the 150-character cases have no counterpart
- **Manual claim, ×8 cases:** each field "accepts max 150 characters" and "does not accept more than 150".
- **Observed:** none of the four inputs declares `maxlength` (verified per field by an explicit
  `not.toHaveAttribute` assertion, so it will fail the day one is added). Measured: 150, **151 and 400**
  characters all go in intact with no message.
- **Test:** the accept-anything behaviour asserted; `test.fixme('<Field> refuses more than 150
  characters')` × 4 carries QA's expectation, probing with `pressSequentially` because `fill()` obeys
  `maxlength` and would mask a real cap.
- **STILL AWAITING A RULING**, jointly with A-C14 below — a per-field cap cannot be chosen without deciding
  what happens to the shared 200-character column, so the two must be answered together.

### A-C14 — the three names share ONE 200-character budget that no field knows about
- The only length limit anywhere in the stack is `varchar(200)` on `COMPLAINT.complainant_name`
  (`Complaint.java:34-35`; confirmed against MySQL `information_schema`), and it applies to the
  **concatenation** `[firstName, middleName, lastName].filter(Boolean).join(' ')`
  (`file-complaint.component.ts:2161`) — **not** to any single field.
- So QA's own limit is internally inconsistent: three fields at the permitted 150 each produce a
  **452-character** value for a 200-character column. Measured, not inferred from the schema: the test
  fills all three at 150, confirms all three hold their value, computes the concatenation the component
  would send, and then **advances past step 1** — proving the overflow is not caught anywhere in the UI
  and would surface only at submit, as a database error, after the citizen has completed the wizard.
- **Recommendation:** derive per-field caps from the column (e.g. 66/66/66, or widen the column), and
  validate the concatenation rather than the parts.
- **STILL AWAITING A RULING**, jointly with A-C17. Note the two options are not equivalent: per-field caps
  are a UI change only, widening the column is a migration. Whichever is chosen, the validation belongs on
  the **concatenation** — capping the three parts independently still permits 3×66+2 separators to exceed a
  narrower column later.

### A-C15 — Surname is not mandatory, though the manual says it is — **STILL AWAITING A RULING**
- **Manual claim (491-577):** blank Surname ⇒ refused with *"Response is mandatory."*
- **Observed:** `lastName` is absent from `validateCurrentStep()`. A blank Surname **advances to step 2**
  normally. Measured per field via the driver's `blocksNextWhenBlank` flag rather than assumed:
  - `firstName` — validated, blocks Next, **and the refusal is now visible** (A-C11b fixed).
  - `middleName` — not validated, permits Next. **QA agrees** it is optional.
  - `lastName` — not validated, permits Next. **QA disagrees.** ← the open question.
  - `organizationName` — blocks Next, and **now for its own reason**. It used to block because it shared
    `validationErrors['name']` with `firstName` (A-D5); the validator now requires the field that is
    actually rendered, so a filled organisation name advances and a blank one is refused against the field
    the citizen can see.
- **Needs a ruling:** is a surname required? RB-IOS forms generally require it. If so this is a one-line
  addition — and the place to add it is now unambiguous, since the individual branch of the step-1 validator
  already exists and already renders inside the block where Surname lives. The A-C11b hazard is retired.
- The spec records the disagreement explicitly as `lastName: blocksNextWhenBlank: false,
  qaClaimsMandatory: true`, which generates a `test.fixme('a blank Surname is refused as mandatory')` that
  starts passing the moment the rule is added.

### Block 16 — Mobile Number: all four cases pass, no contradictions
- Auto-populated from the login session, rendered **outside** both category blocks so it survives every
  category, and `readonly` rather than `disabled` — which matters and is asserted deliberately: a
  `readonly` input is still focusable and still submitted, so "non-editable" is asserted as *the value is
  unchanged after clicking and typing*, and separately as the attribute. Clearing it via select-all +
  Delete also leaves the value intact.
- The one behaviour worth flagging as intentional-looking-odd: because it is readonly and not disabled, a
  screen reader will still announce it as an editable text box. Cosmetic; not raised as a defect.

---

## 3d. Track a complaint — blocks 60-62 (lines 4177-4266, 25 cases) — VERIFICATION TASK

Per §6 of the brief these blocks were to be *mapped onto existing coverage*, not automated afresh.
Existing owners: `e2e/public/tracking-table.spec.ts` (UST100/FR-G-031, 9 tests) and
`e2e/public/tracking-authz.spec.ts` (UST98/UST99/UST105, 19 tests).

**Which screen.** `/public/track` → `components/complaint-tracker/complaint-tracker.component`, which has
two modes: *Track by Complaint ID* (anonymous, reference-number search box — the default) and *Track by
Mobile Number* (OTP, lists that citizen's complaints). Manual blocks 60-62 mix both.

### Mapping — 22 of 25 already covered

| Manual case | Covered by |
|---|---|
| "Track a complaint" present on the portal; button clickable | `tracking-table.spec.ts` navigation setup |
| Track without logging in again / direct access for a session holder (×2) | `tracking-authz.spec.ts` UST99 S1 — session ⇒ mobile mode, no second OTP |
| Login with valid mobile + captcha + OTP | `tracking-authz.spec.ts` "send-otp posts a real CAPTCHA answer" + the tracker-OTP describe (5 tests) |
| List of all complaints linked to the verified mobile displayed | `tracking-authz.spec.ts` "a citizen sees only its own complaints"; `tracking-table.spec.ts` QA1 |
| Columns: Complaint Number, Entity Name, Submission Date (×2) | `tracking-table.spec.ts` QA1 |
| Clicking a listed complaint opens its detail + current status (×2) | `tracking-table.spec.ts` QA2 |
| Status updated after every action on the complaint | `tracking-table.spec.ts` QA3-QA7 (status sort/filter over `advanceToStatus`) |
| Error message for an invalid complaint number | `tracking-authz.spec.ts` "an unknown reference number returns the AC error message" (API) + **new** UI test below |
| Error message for an invalid OTP | `tracking-authz.spec.ts` tracker-OTP describe |
| A logged-in user with no complaints sees an empty state | `tracking-table.spec.ts` QA8a/QA8b |
| Cannot list / withdraw another mobile's complaints; 401 without a session; forged token | `tracking-authz.spec.ts` (6 tests) |
| PII masked for anonymous tracking, unmasked for the owner | `tracking-authz.spec.ts` UST99 (2 tests) |

**Not duplicated on purpose.** Re-asserting the table, the empty state and the authz matrix in a
Session-A spec would double the runtime of a suite three sessions share and create two places to edit
when the table changes.

### The 3 genuine gaps — `e2e/public/tracking-field-validation.spec.ts`, 3 tests, all passing

The reference-number **search box** itself: the existing specs exercise the endpoint behind it for
authorization and masking, but never the field.

### A-C18 — a blank complaint number was refused silently — **FIXED**
- **Manual claim (4253-4256):** blank complaint number ⇒ cannot track. True, but **nothing was said.**
- **Was:** `track()` opened `if (!id) return;`, and the button was `[disabled]="!searchId() || loading()"`.
  Whitespace-only was equivalent because `track()` trims first.
- Third instance of the disable-instead-of-explain pattern (cf. A-C8 consent, A-C9 OTP). Same objection:
  the team's own argument at `public-login.component.ts:81-85` is that a disabled control explains nothing.
- **Now:** standardised with A-C8/A-C9 on the user's ruling *"please standardise it"*. The button disables
  only on `loading()`; `track()` sets `error` to *"Complaint number is required to track a complaint."* and
  clears any stale status. Both `''` and `'   '` are refused, and **no** request is issued.
- **Test:** `tracking-field-validation.spec.ts` asserts the button is enabled, the message renders for both
  inputs, and `requests` is empty — so a regression to the silent `return` fails on the message, and a
  regression to "submits anyway" fails on the request count.

### A-C19 — the format IS implemented; the defect was the **placeholder** — **FIXED**
- **Manual claim (4232-4235, 4241-4248):** only complaint numbers in the FR-G-024 format are trackable; a
  case id alone must not be.
- **User ruling:** *"It is already implemented."* Confirmed —
  `ComplaintNumberGeneratorService` issues `String.format("N%s%s%06d", financialYear, officeCode,
  nextSequence)`, e.g. `N202627013001025`. The format exists and is authoritative server-side; the lookup
  is an exact match on that column, so a case id alone genuinely cannot resolve.
- **The real defect, found by taking the ruling seriously:** the search box advertised
  `placeholder="Enter Complaint Number (e.g., CMS-20260710-ABC123)"` — a shape **nothing has ever
  generated.** A citizen who trusted the hint typed something guaranteed to 404, and was then told
  *"not found"*, which reads as "your complaint does not exist" rather than "that is not the number".
- **Now:** the placeholder is `e.g., N202627013001025`, matching what the generator actually issues.
- Deliberately **not** added: a client-side regex gate. The server owns the format (office code and
  financial year are both server-side facts), and a UI regex would become a second, drifting definition of
  it. The hint is the right place to teach the shape.

### A-D6 — **RETRACTED.** The tracker calls the correct endpoint.
An earlier entry in this document claimed the tracker called a by-ID handler taking a numeric `Long` and
therefore 404'd for every input, and proposed repointing `trackComplaint` at `/api/v1/complaints/track/${id}`.
**That was wrong on both counts, and the change was reverted before it shipped.** Recorded in full rather
than deleted, because the mistake was easy to make and the next reader deserves the trap:

- The repo has **two** complaint controllers on confusingly similar paths:
  - `ComplaintController` → `/api/complaints` — has `getById(@PathVariable Long id)` *and* `track/{complaintNumber}`
  - `ComplaintApiV1Controller` → `/api/v1/complaints` — has `@GetMapping("/{complaintNumber}")`, a **String**
- The frontend uses the **v1** one. Its handler resolves via `getByComplaintNumber`, masks PII for a
  non-owner, and audits `TRACK_VIEWED`. **There is no `/api/v1/complaints/track/{n}` at all** — the
  proposed "fix" would have pointed the public tracker at a route that does not exist, breaking the one
  screen it was meant to repair.
- The 404s that seemed to confirm the diagnosis came from a **stale JVM on 8092** that predated the
  routes being present. After a restart, `/api/v1/complaints/{complaintNumber}` and `/recent` both return
  200. Curl-verified with masked PII in the response.
- **Lesson, generalised:** a 404 from a long-running local backend is a claim about *that process*, not
  about the code. Confirm the mapping exists by reading the controller before concluding the caller is
  wrong — and when two controllers differ only by an `/api/v1` prefix, name which one you read.
- The spec carries the same retraction inline, and now asserts
  `expect(requests[0]).toContain('/api/v1/complaints/CASE-00012345')` — pinning the correct wiring so a
  future "fix" in the retracted direction fails immediately.

### Tracking activity **IS** audited (an earlier "also verified" claim here was wrong)
- **Manual claim (4257-4260):** "tracking activity should be logged for audit purposes." **Satisfied.**
- `ComplaintApiV1Controller.getComplaintDetail` calls
  `auditService.logActionAsync(complaintNumber, "TRACK_VIEWED", ...)`.
- The earlier entry inspected `ComplaintController.track`/`getById` — the **other** controller, the one the
  tracker does not call — found no audit write there, and concluded tracking was unaudited. Same
  two-controller confusion as A-D6 above.
- Not turned into a Session-A test: the write is `...Async`, so asserting it from a browser spec means
  racing a fire-and-forget call. It belongs in a backend test, and is left to whoever owns audit coverage.

---

## 4. Deferred / not automatable

| Manual lines | Case | Why, and what IS asserted instead |
|---|---|---|
| 144-146, 817-819 | Speaker/audio icon *pronounces* the CAPTCHA | The output is a `SpeechSynthesisUtterance` handed to the browser's TTS engine (`public-login.component.ts:99-107`). There is no observable artefact in the DOM, no network call, and Playwright cannot capture audio. **Asserted instead:** the control exists, is focusable, carries an accessible label, and clicking it causes a MATH challenge (the speakable variant) to be loaded — which is the whole of its testable contract. |

*(CAPTCHA wall-clock expiry, 800-802/806-807/2589-2593, was expected to land here per the brief but is
NOT deferred — see §5.)*

---

## 5. Notes on technique

- **CAPTCHA expiry is driven, not waited out.** The brief allowed deferring 800-802, 806-807 and
  2589-2593. They are instead automated with a new `expireCaptcha(token)` helper in
  `e2e/public/helpers-auth-a.ts`, which rewinds `captcha_sessions.created_at`/`expires_at` by SQL —
  exactly as `backdateOtpAttempt` does for the OTP clock. `verifyCaptcha` selects on
  `used = false AND expires_at > now()`, so the rewound row is genuinely expired to every line of
  shipping code. Nothing is stubbed and no test sleeps.
- **`login_cooloffs` is shared global state and poisons negative tests.**
  `CooloffService.recordFailedAttempt` fires on every rejected CAPTCHA and every wrong OTP, and the row
  is keyed on **fingerprint + client IP** as well as on the mobile (dev-local escalation: 5s, 10s, 20s).
  All tests run from one loopback IP, so a fresh mobile per test is *not* sufficient isolation — the
  third negative test in a file would receive `COOLOFF_ACTIVE` (429) instead of the refusal it asserts
  and would report a defect that is not there. `clearCooloff()` runs in `beforeEach` of every negative
  describe.
- **`fill()` obeys `maxlength`,** so "does not accept more than N characters" cannot be proven by
  filling N+1 — the overflow never arrives and the assertion is vacuous. Those cases assert the
  attribute and additionally type the N+1th character with `pressSequentially` to prove truncation.
- **THE JVM WRITES UTC, MYSQL `NOW()` IS IST — a 5h30m skew.** Worth recording for every future session
  that touches a timestamp. Measured on this machine: `SELECT NOW()` returned `2026-09-24 17:53:55` while
  rows the backend had written one second earlier carried `2026-09-24 12:23:46`. The backend's
  `LocalDateTime.now()` resolves to UTC, so **every** `datetime` in `captcha_sessions` and `otp_attempts`
  is UTC while the database's own clock functions are IST.
  - Consequence: `TIMESTAMPDIFF(SECOND, NOW(), expires_at)` on a freshly-issued challenge returns
    **-19200**, which reads exactly like "the product expires challenges instantly" and cost a real
    failure before it was diagnosed.
  - Parsing the value in Node is no better: `datetime(6)` is naive and its 6-digit fraction is not a form
    V8 treats as local, so it is read as UTC and skewed the other way.
  - **The robust measure is the span between two columns written by the same clock in the same
    statement** — `TIMESTAMPDIFF(SECOND, created_at, expires_at)` is exactly the configured window in any
    timezone. Combined with backdating *both* columns by the same delta, the boundary math never needs to
    know what "now" is. `backdateOtpAttempt` already works this way; `captchaSessionRow`/`expireCaptcha`
    now do too.
  - Note this is a *test-harness* hazard, not necessarily a product bug: the product compares
    `expires_at > now()` where both sides come from the JVM, so it is self-consistent. But any SQL-based
    assertion that mixes the two clocks is wrong.

---

## 6. Running counts

| | |
|---|---|
| Specs written | 7 — all blocks in scope closed |
| Tests written | **159** (73 generated in `helpers-auth-a.ts`) |
| **Final run, 2026-09-25** | **131 passed, 28 skipped, 0 failed** (8.5 min, `workers: 1`, chromium) |
| `fixme` remaining | 28 — all gated on the 3 unruled items (A-C15, A-C16, A-C17/A-C14) + the 3 open defects (A-D1, A-D2, A-D3) |
| Contradictions recorded | 19 — A-C1…A-C19 (+ A-C11b) |
| Defects recorded | 5 real — A-D1…A-D5 (+ A-C11b). **A-D6 retracted** |
| Manual cases in scope | 166 — 141 automated, 25 verified by mapping (§3d) |
| Manual cases **declined** | 2 — line 800 (captcha case-sensitivity, A-C2) and 212-215 (Verify disabled on blank OTP, A-C9) |

### Rulings received 2026-09-25, and what they changed

| Ruling | Given as | Outcome |
|---|---|---|
| A-C19 | *"It is already implemented"* | Confirmed. Real defect was the **placeholder** advertising a format nothing generates — fixed. |
| A-C10 | *"Only male female transgender and do not wish to disclose"* | 5th value `other` **removed** from template and label map. |
| A-C8 / A-C9 | *"please standardise it"* | Standardised on **enable-and-explain**; A-C9's field was already correct, so A-C8 and A-C18 were brought to it. Manual case 212-215 declined. |
| A-C1 | *"It should be 5 minutes"* | `application-dev-local.yml` `expiry-minutes: 10 → 5`. Prod was always 5; dev-local was the outlier. |
| A-C2 | *"case-sensitive should not be there"* | No code change — already case-insensitive. Manual case 800 declined. |
| — | *"go-ahead to edit `src/**`"* (ruling 5 lifted) | Unblocked A-D4, A-D5, A-C11b and A-C18, all now fixed. |

### Still blocked on a ruling — these are the only open items

| # | Finding | The question |
|---|---|---|
| 1 | **A-C15** | Is Surname mandatory? QA says yes, the product does not validate it. One-line fix either way; the A-C11b hazard that complicated it is retired. |
| 2 | **A-C16** | Should names be letters-only? The manual's *"Only letters are allowed."* would reject *D'Souza* and *Jr.* — recommend letters, space, `'`, `-`, `.` if enforced. 20 fixmes ride on this. |
| 3 | **A-C17 + A-C14** | Per-field length cap, versus the shared `varchar(200)` on the concatenation. Must be answered together; per-field caps are UI-only, widening the column is a migration. |

### Fixed this pass

| # | Finding | Fix |
|---|---|---|
| 1 | **A-D5** — ten of twelve categories could not leave step 1 | Validator branches on the field that is actually rendered. No organisation could file at all before this. |
| 2 | **A-C11b** — blank category was a silent dead end | Category validated first; its error renders **outside** both conditional blocks. Same edit as A-D5 — one overloaded key caused both. |
| 3 | **A-D4** — PwD was shown the organisation form | `pwd` added to a named `INDIVIDUAL_CATEGORIES` constant. |
| 4 | **A-C18** — blank complaint number refused silently | `track()` explains; only `loading()` disables. |
| 5 | **A-C19** — misleading placeholder | Now `N202627013001025`, what the generator actually issues. |
| 6 | **A-C10** — undocumented 5th gender | Removed; test now asserts the exact four-value array. |
| 7 | **A-C8** — consent had no message | Enable-and-explain; existing guards became reachable. |
| 8 | **A-C1** — dev/prod expiry divergence | dev-local now 5 minutes. |

**A-D6 is retracted** — it previously sat at #2 on this list. See §3d for the full retraction and the
two-controller trap that produced it.

### Deliberately not done
- **The three unruled items above** — no name-field validation was added, because implementing the manual's
  wording as written would reject legitimate names and lock in a length rule that contradicts the schema.
- **No client-side complaint-number regex** (A-C19) — the server owns the format; a UI copy would drift.
- **No backend test for the `TRACK_VIEWED` audit write** — it is `...Async` and belongs in a backend test,
  not a browser spec.
- **The tree is left dirty and uncommitted**, as instructed.

### One rule worth carrying out of this session
Where a mandatory field is concerned, **disable nothing the citizen can fix by typing.** Only transient
states (`loading`, cool-off) disable a control; every omission is refused inside the handler with wording
that names the omission. This was the team's own position, stated in a comment and then applied to only one
of four comparable controls — the four are now consistent, and the reasoning is recorded in the templates
so the next person to add a field knows which pattern to follow.
