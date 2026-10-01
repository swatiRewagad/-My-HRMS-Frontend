/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B22 — Complainant Details: Pincode
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack block 22 (lines 1372-1475, 19 cases after its 3 duplicated login/navigation preamble
 * rows). The field is on form step 1 — not step 3; see helpers-forms-b.ts.
 *
 * ── THE PINCODE IS NOT A DROPDOWN, SO 6 OF THE 19 CASES DESCRIBE A CONTROL THAT DOES NOT EXIST ────
 * The pack calls it the "Pincode dropdown field" throughout and spends six cases on dropdown
 * mechanics: it "opens when clicked", the user "selects a single value", "cannot select multiple
 * values", it "closes after selecting the value", and "based on input provided by the user, the
 * suggestions in the dropdown are reflected".
 *
 * The product renders a plain text input (template:572-573):
 *
 *     <input type="text" name="pincode" maxlength="6" (ngModelChange)="onPincodeInput()">
 *
 * There is no `<select>`, no `<datalist>`, no typeahead panel and no suggestion list — nothing opens,
 * nothing closes, and there is nothing to multi-select. The citizen TYPES six digits and the product
 * then derives state and district from them. Each of those six cases is asserted below as the fact
 * that replaces it (the control's identity, and the absence of any suggestion mechanism), because
 * asserting "the dropdown does not open" against an element that is not a dropdown would be theatre.
 *
 * ── WHERE THE LOOKUP ACTUALLY GOES (the brief's proxy warning, resolved) ─────────────────────────
 * `proxy.conf.json` proxies `/api/pincode` to https://api.postalpincode.in, which invites the
 * conclusion that this field reaches a third party. It does not. That proxy rule is used ONLY by the
 * CRPC draft-assessment screen (`draft-assessment.component.ts:552`). The citizen wizard calls
 * `${environment.apiBaseUrl}/api/v1/location/pincode/{value}` (`component.ts:591`) — our own backend,
 * through the `page` fixture's rewrite like every other call. Verified before writing a line of this
 * spec, so that a lookup failure here is never mistaken for an upstream outage.
 *
 * ── VALIDATION IS LIVE HERE, UNLIKE EVERY OTHER FIELD ON THE STEP ────────────────────────────────
 * `onPincodeInput()` (component.ts:557-612) writes `validationErrors['pincode']` on EVERY keystroke.
 * That makes pincode the only step-1 field with keystroke-time feedback — email, address and landline
 * are validated only when Next is pressed. Two distinct messages exist, and NEITHER is the pack's
 * "Pincode must be 6 digits.":
 *
 *     non-digit present      → 'Pincode must contain only digits.'      (live, on keystroke)
 *     length > 6             → 'Pincode must be exactly 6 digits.'     (UNREACHABLE — see below)
 *     blank / < 6 / on Next  → 'Valid 6-digit pincode is required'     (from validateCurrentStep)
 *     6 digits, no match     → 'Invalid pincode. No location found.'   (local-table fallback)
 *
 * ── A DEAD BRANCH: 'Pincode must be exactly 6 digits.' CAN NEVER BE SEEN ─────────────────────────
 * `maxlength="6"` means the browser refuses the 7th character, so `formData['pincode'].length > 6` is
 * unsatisfiable through the UI and the branch at component.ts:577-582 is dead code. The pack's case
 * "cannot accept more than 6 digits AND error message ... should be displayed" therefore cannot be
 * satisfied in both halves, exactly like the address and landline length cases. Asserted as
 * truncation, with the message's unreachability proven rather than assumed.
 *
 * ── A CONSEQUENCE WORTH FLAGGING: TRUNCATION CAN MANUFACTURE A VALID PINCODE ─────────────────────
 * Because the cap is applied by the browser before Angular ever sees the value, typing a decimal like
 * `411001.5` leaves `411001` in the model — a perfectly valid Pune pincode, silently accepted with no
 * indication that anything was discarded. The pack expects decimals to be rejected. They are not
 * rejected; they are trimmed into something plausible. See findings-QA-B.md §3.
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. Pincode lookups are read-only
 * GETs. Mobiles come from Session B's reserved 98765_2____ range via the helper.
 */

import { test, expect } from '../fixtures';
import {
  gotoComplainantDetails, fillPincodeAndWaitForLookup, fieldError,
  clickNextAndCollectErrors, redirectAppApi, API_BASE,
} from './helpers-forms-b';

/** Verified against this backend; resolves to Pune, Maharashtra. */
const PINCODE_A = '411001';

/** The message the pack expects for both the over-long and under-long cases. */
const DOCUMENTED_LENGTH_ERROR = 'Pincode must be 6 digits.';

/** The message the dead `length > 6` branch would render if it were reachable. */
const UNREACHABLE_LENGTH_ERROR = 'Pincode must be exactly 6 digits.';

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

test.describe('QA-B22 — Pincode is a 6-character text input with live validation, not a searchable dropdown', () => {

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // Presence and identity — replaces the pack's six dropdown-mechanics cases
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  test('the pincode field is present for every complainant category', async ({ page }) => {
    await gotoComplainantDetails(page);

    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(categories.length, 'the category dropdown must offer options').toBeGreaterThan(0);

    for (const category of categories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(page.locator('input[name="pincode"]'), `pincode must be present for ${category}`)
        .toBeVisible();
    }
  });

  /**
   * Cases 22.7-22.10 (dropdown opens / single value selected / no multi-select / dropdown closes) are
   * all answered by one fact: there is no dropdown. Proven on the element's own identity plus the
   * absence of every suggestion mechanism Angular or HTML could use.
   */
  test('CONTRADICTION: the pincode control is a text input — there is no dropdown to open, close or multi-select', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    expect(await pincode.evaluate(el => el.tagName), 'the pack calls this a dropdown').toBe('INPUT');
    expect(await pincode.getAttribute('type')).toBe('text');
    expect(await pincode.getAttribute('multiple'), 'nothing to multi-select on a text input').toBeNull();

    // No <select> and no native suggestion list is associated with the field.
    expect(await pincode.getAttribute('list'), 'no <datalist> is wired to the field').toBeNull();
    const pincodeGroup = page.locator('.form-group', { has: pincode }).first();
    await expect(pincodeGroup.locator('select')).toHaveCount(0);
    await expect(pincodeGroup.locator('datalist')).toHaveCount(0);
  });

  /**
   * Case 22.12: "based on input provided by the user, the suggestions in the dropdown are reflected".
   * Typing a partial pincode produces no suggestions of any kind — the product does nothing at all
   * below six digits except blank the derived state and district.
   */
  test('CONTRADICTION: typing a partial pincode produces no suggestions — nothing is offered until all 6 digits are present', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    await pincode.pressSequentially('4110', { delay: 60 });
    await page.waitForTimeout(500);

    // Every mechanism a suggestion panel could plausibly use.
    const pincodeGroup = page.locator('.form-group', { has: pincode }).first();
    await expect(pincodeGroup.locator('[role="listbox"], [role="option"], .suggestions, .autocomplete, .dropdown-panel, ul li'))
      .toHaveCount(0);
    expect(await pincode.getAttribute('aria-expanded'), 'no combobox state is exposed').toBeNull();

    // And no partial lookup has fired: state and district are still empty.
    await expect(page.locator('select[name="state"]')).toHaveValue('');
    await expect(page.locator('select[name="district"]')).toHaveValue('');
  });

  test('the citizen is able to type into the pincode field and the value is retained', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    // Case 22.11. Typed character-by-character rather than filled, so this also proves the field is
    // not read-only and accepts real keyboard input.
    await pincode.pressSequentially(PINCODE_A, { delay: 40 });
    expect(await pincode.inputValue()).toBe(PINCODE_A);
  });

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // Mandatory
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  test('CONTRADICTION: a blank pincode blocks the step, but the message is "Valid 6-digit pincode is required", not "Response is mandatory."', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await expect(page.locator('input[name="pincode"]')).toHaveValue('');

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);

    await expect(page.locator('select[name="complainantCategory"]'), 'the step must be held').toBeVisible();
    expect(await fieldError(page, 'pincode')).toBe('Valid 6-digit pincode is required');
  });

  test('a blank pincode raises no live error — the mandatory message waits for Next', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    // The empty branch of onPincodeInput DELETES the error rather than setting one, so an untouched
    // (or cleared) field is quiet until the citizen tries to advance.
    await pincode.fill(PINCODE_A);
    await pincode.fill('');
    expect(await fieldError(page, 'pincode'), 'clearing the field must not scold the citizen mid-edit').toBe('');
  });

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // Accepted input
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  test('a 6-digit numeric pincode is accepted and drives the location lookup', async ({ page }) => {
    await gotoComplainantDetails(page);

    // Cases 22.13 (accepts numeric) and 22.20 (accepts max 6 digits) — the same assertion, since the
    // cap and the lookup threshold are both 6.
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    expect(await page.locator('input[name="pincode"]').inputValue()).toBe(PINCODE_A);
    expect(await fieldError(page, 'pincode'), 'a valid pincode raises no error').toBe('');

    // Proven by its effect, not merely by the absence of a complaint: the lookup populated state.
    await expect(page.locator('select[name="state"]')).not.toHaveValue('');
  });

  test('the maxlength is 6, matching the length the validator demands', async ({ page }) => {
    await gotoComplainantDetails(page);

    // Read from the attribute, never hardcoded (ruling 1).
    const cap = Number(await page.locator('input[name="pincode"]').getAttribute('maxlength'));
    expect(cap, 'the pincode input must declare a maxlength').toBe(6);
  });

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // Rejected input — one table over the pack's six "should not accept" cases
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  /**
   * Cases 22.14-22.19. All six are the same mechanism: any non-digit character present in the value
   * fails `/^\d*$/`, which sets 'Pincode must contain only digits.' LIVE and blanks the derived state
   * and district. The pack names no message for these cases, so there is nothing to contradict on
   * wording — but the behaviour is worth pinning as one table rather than six near-identical tests.
   *
   * Note the values are chosen to survive maxlength=6, because a longer value would be truncated and
   * would then be testing truncation instead of the digit check.
   */
  test('non-digit input is flagged live with "Pincode must contain only digits." and blanks the derived location', async ({ page }) => {
    await gotoComplainantDetails(page);

    // Establish a good lookup first, so each bad value must be seen to DESTROY a populated state.
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await expect(page.locator('select[name="state"]')).not.toHaveValue('');

    const nonDigitInputs: Array<[string, string]> = [
      ['negative number (case 22.14)', '-41100'],
      ['decimal (22.15)', '411.00'],
      ['alphanumeric (22.16)', '4110ab'],
      ['special characters (22.17)', '41#$%^'],
      ['emoji (22.18)', '\u{1F600}'],
      ['only spaces (22.19)', '      '],
      // 22.13 (accepts numeric) is the positive case and is asserted separately above.
    ];

    const results: Array<[string, string]> = [];
    for (const [caseName, value] of nonDigitInputs) {
      await page.locator('input[name="pincode"]').fill(value);
      await page.waitForTimeout(150);
      results.push([caseName, await fieldError(page, 'pincode')]);

      // The derived location must not survive an invalid pincode.
      await expect(page.locator('select[name="state"]'), `${caseName}: state must be blanked`)
        .toHaveValue('');
      await expect(page.locator('select[name="district"]'), `${caseName}: district must be blanked`)
        .toHaveValue('');
    }

    expect(results, 'every non-digit class is rejected live with the same message').toEqual(
      nonDigitInputs.map(([name]) => [name, 'Pincode must contain only digits.']));
  });

  /**
   * Case 22.15 in detail. The pack expects decimals to be rejected. A decimal LONGER than six
   * characters is not rejected — maxlength discards the tail and leaves a valid pincode behind, so the
   * citizen's typo is silently converted into a real location.
   */
  test('CONTRADICTION: a decimal longer than 6 characters is truncated into a VALID pincode instead of being rejected', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    await pincode.pressSequentially('411001.5', { delay: 40 });
    await page.waitForTimeout(400);

    expect(await pincode.inputValue(), 'the browser kept only the first 6 characters').toBe(PINCODE_A);
    expect(await fieldError(page, 'pincode'), 'no rejection: the truncated value is a real pincode').toBe('');
    await expect(page.locator('select[name="state"]'),
      'the lookup ran on the truncated value and populated a state').not.toHaveValue('', { timeout: 20000 });
  });

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // Length
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  /**
   * Case 22.21: "should not accept more than 6 digits AND error message 'Pincode must be 6 digits.'
   * should be displayed". Neither half holds as written. The browser enforces the cap, so the
   * over-length value never reaches Angular; and the message the component WOULD show for an
   * over-length value is different anyway, and is unreachable.
   */
  test('CONTRADICTION: more than 6 digits is truncated by the browser, so neither the pack\'s message nor the component\'s own over-length message can appear', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    const cap = Number(await pincode.getAttribute('maxlength'));
    await pincode.pressSequentially('4110019999', { delay: 30 });
    await page.waitForTimeout(400);

    expect((await pincode.inputValue()).length, `the browser refuses characters past maxlength=${cap}`)
      .toBe(cap);
    expect(await pincode.inputValue()).toBe(PINCODE_A);

    const error = await fieldError(page, 'pincode');
    expect(error, 'the pack\'s message does not exist in the product').not.toBe(DOCUMENTED_LENGTH_ERROR);
    expect(error, `component.ts:578 sets ${JSON.stringify(UNREACHABLE_LENGTH_ERROR)} for length > 6, which maxlength makes unreachable`)
      .not.toBe(UNREACHABLE_LENGTH_ERROR);
    expect(error, 'the surviving 6 digits are valid, so there is no error at all').toBe('');
  });

  /**
   * Proves the dead branch is dead rather than merely asserting it. `length > 6` is the only path to
   * 'Pincode must be exactly 6 digits.', and no sequence of keystrokes or paste can produce a value
   * longer than the cap.
   */
  test('the component\'s "Pincode must be exactly 6 digits." branch is unreachable through the UI', async ({ page }) => {
    await gotoComplainantDetails(page);
    const pincode = page.locator('input[name="pincode"]');

    const attempts = ['41100199', '1234567', '411001411001', '99999999999999'];
    for (const attempt of attempts) {
      await pincode.fill('');
      await pincode.fill(attempt);
      await page.waitForTimeout(120);
      expect((await pincode.inputValue()).length, `${attempt} must be capped at 6`).toBeLessThanOrEqual(6);
      expect(await fieldError(page, 'pincode'),
        `${attempt}: the over-length branch must never render`).not.toBe(UNREACHABLE_LENGTH_ERROR);
    }
  });

  /**
   * Case 22.22: fewer than 6 digits, expecting 'Pincode must be 6 digits.'. The under-length branch
   * (component.ts:571-576) deliberately DELETES the error instead of setting one — the citizen is
   * mid-typing, so scolding them at 3 digits would be wrong. The complaint arrives only on Next, and
   * with different wording.
   */
  test('CONTRADICTION: fewer than 6 digits is silent while typing and only blocked on Next, with a different message', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('input[name="pincode"]').fill('4110');
    await page.waitForTimeout(200);
    expect(await fieldError(page, 'pincode'), 'no live error while the citizen is still typing').toBe('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'the pack\'s message does not exist').not.toContain(DOCUMENTED_LENGTH_ERROR);
    expect(await fieldError(page, 'pincode')).toBe('Valid 6-digit pincode is required');
    await expect(page.locator('select[name="complainantCategory"]'), 'the step is held').toBeVisible();
  });

  // ───────────────────────────────────────────────────────────────────────────────────────────────
  // The lookup's own failure mode, and the exit
  // ───────────────────────────────────────────────────────────────────────────────────────────────

  /**
   * Not in the pack, but it is the behaviour a citizen in an unmapped area will meet, and it closes
   * the loop on "the pincode field accepts numeric values" — a 6-digit number is not automatically a
   * usable pincode. The backend returns no match and the compiled-in 481-row fallback has no entry,
   * so the field reports it and step 1 cannot be left.
   */
  test('a well-formed pincode that matches no location is reported and blocks the step', async ({ page }) => {
    await gotoComplainantDetails(page);

    // Confirm with the backend that this really is unmatched, rather than trusting a made-up value.
    const probe = await page.request.get(`${API_BASE}/api/v1/location/pincode/000000`);
    const body = await probe.json().catch(() => null);
    const matched = Array.isArray(body) && body[0]?.Status === 'Success' && body[0]?.PostOffice?.length;
    test.skip(!!matched, 'fixture pincode 000000 unexpectedly resolves on this backend');

    await page.locator('input[name="pincode"]').fill('000000');
    await expect(page.locator('.field-error', { hasText: 'Invalid pincode. No location found.' }))
      .toBeVisible({ timeout: 20000 });
    await expect(page.locator('select[name="state"]')).toHaveValue('');
  });

  test('a valid pincode with the other mandatory fields advances to Regulated Entity Details', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="lastName"]').fill('Citizen');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();

    // Case 22.23, the closing case shared by blocks 19-22.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });
});
