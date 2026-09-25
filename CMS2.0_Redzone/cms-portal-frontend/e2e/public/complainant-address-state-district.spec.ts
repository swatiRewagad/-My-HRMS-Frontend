/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B19 / QA-B20 / QA-B21 — Complainant Details: State of Residence, District of Residence, Address
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack blocks 19 (lines 1165-1232, 7 cases), 20 (1233-1305, 8 cases) and 21 (1306-1371, 10
 * cases). All three fields are on form step 1 — not step 3; see helpers-forms-b.ts.
 *
 * ── THE FINDING THAT REWRITES BLOCKS 19 AND 20 ENTIRELY ───────────────────────────────────────────
 * The pack treats State of Residence and District of Residence as ordinary dropdowns the citizen picks
 * from: "click the dropdown", "select a single value", "try to select multiple values", "the dropdown
 * closes after selecting". Blocks 19 and 20 spend 15 cases on that model.
 *
 * That is not what the product does. **Neither field is independently selectable.** Both are DERIVED
 * from the pincode:
 *
 *   onPincodeInput() (component.ts:557-627) fires on every keystroke of the pincode field. At exactly
 *   6 digits it calls GET /api/v1/location/pincode/{code}, then sets
 *       complainantStates    = unique States    of the returned PostOffice[]
 *       complainantDistricts = unique Districts of the returned PostOffice[]
 *       formData['state']    = states[0]
 *       formData['district'] = districts[0]
 *   and on any failure falls back to lookupPincode() over a 481-row compiled-in table
 *   (src/app/utils/pincode-data.ts), which yields exactly ONE state and ONE district.
 *
 * So the <select> elements are populated and pre-selected by the lookup. Before a valid pincode is
 * typed they hold nothing but the placeholder, and every keystroke that makes the pincode invalid
 * BLANKS them again (the `else if` branches each reset state, district and both option lists).
 *
 * Consequences, each asserted below rather than asserted around:
 *   • "The dropdown opens when clicked" — vacuous on a native <select> with no options. Tested as
 *     "the control exists and is a single-select", which is the checkable part.
 *   • "The user is not able to select multiple values" — structurally guaranteed: it is a native
 *     <select> WITHOUT the `multiple` attribute. Asserted on the attribute, which is the real proof,
 *     rather than by trying to click two options (which cannot fail and so proves nothing).
 *   • "Select the value in State, then observe District is filtered by it" (case 20.2) — impossible as
 *     written. District does not cascade FROM state; both cascade from pincode, in parallel. A real
 *     State→District cascade exists on the RE Details screen (entityState → entityDistrict) and is
 *     covered in re-details-cascading-dropdowns.spec.ts. Tested here as the relationship that DOES
 *     hold: state and district are mutually consistent for the pincode that produced them.
 *   • Per ruling 1 / the brief's cascade note, no hardcoded list of states or districts is asserted.
 *     Every test reads what the lookup returned and asserts a RELATIONSHIP.
 *
 * ── A REAL DEFECT: DISTRICT IS MANDATORY ON SCREEN BUT NOT VALIDATED ─────────────────────────────
 * District carries a red asterisk in the template but appears NOWHERE in validateCurrentStep()
 * (component.ts:1652-1657, which checks firstName, pincode, state, address, email only). The <select>
 * also has no `disabled` binding. Case 19.2's and 20.3's "Response is mandatory." message does not
 * exist for either field: state is validated with 'Enter valid pincode to auto-fill state' and
 * district is not validated at all. Documented in §3 of findings-QA-B.md.
 *
 * ── ADDRESS IS maxlength-CAPPED, SO HALF OF BLOCK 21 IS UNPROVABLE AS WRITTEN ────────────────────
 * The textarea carries maxlength="100" (template:596). Case 21.10 wants BOTH "should not accept more
 * than 100 characters" AND the message "Address cannot exceed 100 characters." Those cannot both
 * happen: the browser truncates at 100, so an over-length value never reaches the model and no
 * validator can fire. The truncation is asserted; the message's non-existence is logged.
 *
 * Block 21 also contains a self-contradiction the BA should resolve: case 21.4's description says the
 * field "accepts special characters" while its own Expected Result says it "should not accept special
 * characters". Observed behaviour (accepts them) is asserted and the conflict is logged.
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. Pincodes used are read-only
 * lookups. Mobiles come from Session B's reserved 98765_2____ range via the helper.
 */

import { test, expect } from '../fixtures';
import {
  gotoComplainantDetails, fillPincodeAndWaitForLookup, fieldError,
  clickNextAndCollectErrors, redirectAppApi, API_BASE,
} from './helpers-forms-b';

/** Pincodes verified against this backend at the time of writing; each resolves to one state+district. */
const PINCODE_A = '411001';   // Pune, Maharashtra
const PINCODE_B = '682001';   // Ernakulam, Kerala

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 19 — State of Residence
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B19 — State of Residence is derived from the pincode, not chosen by the citizen', () => {

  test('the state control is present for every complainant category', async ({ page }) => {
    await gotoComplainantDetails(page);

    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    for (const category of categories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(page.locator('select[name="state"]'), `state must be present for ${category}`)
        .toBeVisible();
    }
  });

  test('the state control is a single-select, so multiple states cannot be chosen', async ({ page }) => {
    await gotoComplainantDetails(page);
    const state = page.locator('select[name="state"]');

    // Cases 19.4/19.5 (single value at a time / no multi-select) are guaranteed by the ABSENCE of the
    // `multiple` attribute. Asserting the attribute proves it; clicking two options could not fail.
    expect(await state.getAttribute('multiple'), 'a native select without [multiple] is single-select')
      .toBeNull();
    expect(await state.evaluate((el: HTMLSelectElement) => el.multiple)).toBe(false);
    expect(await state.evaluate((el: HTMLSelectElement) => el.size)).toBeLessThanOrEqual(1);
  });

  test('the state list is empty until a valid 6-digit pincode is entered', async ({ page }) => {
    await gotoComplainantDetails(page);
    const stateOptions = page.locator('select[name="state"] option:not([value=""])');

    // This is the fact that invalidates the pack's "click the dropdown and select a state" model.
    await expect(stateOptions, 'no state is offered before a pincode is supplied').toHaveCount(0);
    await expect(page.locator('select[name="state"]')).toHaveValue('');
  });

  test('entering a valid pincode populates the state and selects it automatically', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);

    // Read the truth from the API rather than hardcoding "Maharashtra" (ruling 1).
    const lookup = await (await page.request.get(`${API_BASE}/api/v1/location/pincode/${PINCODE_A}`)).json();
    const expectedStates = [...new Set(
      (lookup[0]?.PostOffice ?? []).map((po: any) => po.State).filter(Boolean))] as string[];
    expect(expectedStates.length, 'fixture pincode must resolve to at least one state').toBeGreaterThan(0);

    const rendered = await page.locator('select[name="state"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(rendered.sort(), 'the rendered states are exactly those the lookup returned')
      .toEqual(expectedStates.sort());

    // And it is pre-selected, not merely offered.
    await expect(page.locator('select[name="state"]')).toHaveValue(expectedStates[0]);
  });

  test('changing the pincode to a different region replaces the state', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    const first = await page.locator('select[name="state"]').inputValue();

    await fillPincodeAndWaitForLookup(page, PINCODE_B);
    const second = await page.locator('select[name="state"]').inputValue();

    expect(second, 'a pincode in another state must change the derived state').not.toBe(first);
    expect(second.length, 'the new state is populated, not blanked').toBeGreaterThan(0);
  });

  test('making the pincode invalid again blanks the state that had been derived', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await expect(page.locator('select[name="state"]')).not.toHaveValue('');

    // Deleting a digit drops it to 5 characters, which resets the derived values.
    await page.locator('input[name="pincode"]').fill(PINCODE_A.slice(0, 5));
    await expect(page.locator('select[name="state"]'),
      'an incomplete pincode must not leave a stale state behind').toHaveValue('');
    await expect(page.locator('select[name="state"] option:not([value=""])')).toHaveCount(0);
  });

  /**
   * Case 19.2 wants "Response is mandatory." when state is blank. State IS enforced — you cannot leave
   * step 1 without one — but the message is 'Enter valid pincode to auto-fill state', which is both
   * different from the document and more accurate, since the citizen cannot act on the state field
   * directly at all.
   */
  test('CONTRADICTION: a blank state blocks the step, but the message names the pincode, not "Response is mandatory."', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    // Pincode deliberately left blank, so no state can be derived.

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);

    await expect(page.locator('select[name="complainantCategory"]'), 'the step must be held').toBeVisible();
    expect(await fieldError(page, 'state')).toBe('Enter valid pincode to auto-fill state');
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 20 — District of Residence
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B20 — District of Residence is derived from the pincode in parallel with state, and is not validated', () => {

  test('the district control is present for every complainant category', async ({ page }) => {
    await gotoComplainantDetails(page);

    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    for (const category of categories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(page.locator('select[name="district"]'), `district must be present for ${category}`)
        .toBeVisible();
    }
  });

  test('the district control is a single-select, so multiple districts cannot be chosen', async ({ page }) => {
    await gotoComplainantDetails(page);
    const district = page.locator('select[name="district"]');

    expect(await district.getAttribute('multiple')).toBeNull();
    expect(await district.evaluate((el: HTMLSelectElement) => el.multiple)).toBe(false);
  });

  test('the district list is empty until a valid pincode is entered', async ({ page }) => {
    await gotoComplainantDetails(page);
    await expect(page.locator('select[name="district"] option:not([value=""])')).toHaveCount(0);
    await expect(page.locator('select[name="district"]')).toHaveValue('');
  });

  test('entering a valid pincode populates the district and selects it automatically', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);

    const lookup = await (await page.request.get(`${API_BASE}/api/v1/location/pincode/${PINCODE_A}`)).json();
    const expectedDistricts = [...new Set(
      (lookup[0]?.PostOffice ?? []).map((po: any) => po.District).filter(Boolean))] as string[];
    expect(expectedDistricts.length).toBeGreaterThan(0);

    const rendered = await page.locator('select[name="district"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(rendered.sort(), 'the rendered districts are exactly those the lookup returned')
      .toEqual(expectedDistricts.sort());
    await expect(page.locator('select[name="district"]')).toHaveValue(expectedDistricts[0]);
  });

  /**
   * Case 20.2, rewritten to what is actually true. The pack expects District to be filtered BY the
   * chosen State. It is not — both come from the same pincode lookup in one pass. What IS assertable,
   * and is the property the citizen depends on, is that the pair is mutually consistent: the district
   * shown belongs to the state shown, for that pincode.
   */
  test('state and district are mutually consistent for the pincode that produced them', async ({ page }) => {
    await gotoComplainantDetails(page);

    for (const pincode of [PINCODE_A, PINCODE_B]) {
      await fillPincodeAndWaitForLookup(page, pincode);

      const lookup = await (await page.request.get(`${API_BASE}/api/v1/location/pincode/${pincode}`)).json();
      const postOffices = (lookup[0]?.PostOffice ?? []) as any[];
      const state = await page.locator('select[name="state"]').inputValue();
      const district = await page.locator('select[name="district"]').inputValue();

      // There must be at least one post office pairing exactly this state with this district.
      const consistent = postOffices.some(po => po.State === state && po.District === district);
      expect(consistent,
        `${pincode}: derived state "${state}" and district "${district}" must co-occur in the lookup`)
        .toBe(true);
    }
  });

  test('changing the pincode replaces the district rather than leaving a stale one', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    const first = await page.locator('select[name="district"]').inputValue();

    await fillPincodeAndWaitForLookup(page, PINCODE_B);
    const second = await page.locator('select[name="district"]').inputValue();

    // The classic stale-child defect the brief warns about. It does NOT occur here, because
    // onPincodeInput resets both lists before re-populating.
    expect(second, 'the district from the previous pincode must not survive').not.toBe(first);
    expect(second.length).toBeGreaterThan(0);
  });

  test('making the pincode invalid blanks the district too', async ({ page }) => {
    await gotoComplainantDetails(page);
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await expect(page.locator('select[name="district"]')).not.toHaveValue('');

    await page.locator('input[name="pincode"]').fill(PINCODE_A.slice(0, 5));
    await expect(page.locator('select[name="district"]')).toHaveValue('');
    await expect(page.locator('select[name="district"] option:not([value=""])')).toHaveCount(0);
  });

  /**
   * DEFECT. District is marked mandatory in the UI (red asterisk, template:590) but is absent from
   * validateCurrentStep(). Because the pincode lookup normally pre-selects it, a citizen rarely
   * notices — but any path that leaves it blank while state is set will pass validation, and the
   * complaint is submitted with no district. Proven by clearing the district AFTER the lookup filled
   * it, which leaves state satisfied and district empty.
   */
  test('DEFECT: district is shown as mandatory but is not validated — the step advances with it blank', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    // The field is marked required in the template.
    const districtGroup = page.locator('.form-group', { has: page.locator('select[name="district"]') }).first();
    await expect(districtGroup.locator('.required'), 'district is presented as mandatory').toBeVisible();

    // Blank it back out, leaving state populated.
    await page.locator('select[name="district"]').selectOption('');
    await expect(page.locator('select[name="district"]')).toHaveValue('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'no district error is raised').not.toMatch(/district/i);

    // It advanced to step 2 with no district at all.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'the wizard advanced despite a blank mandatory district').toBeVisible({ timeout: 20000 });
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 21 — Address
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B21 — the Address textarea is mandatory and hard-capped at 100 characters by the browser', () => {

  test('the address field is present for every complainant category', async ({ page }) => {
    await gotoComplainantDetails(page);

    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    for (const category of categories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(page.locator('textarea[name="address"]'), `address must be present for ${category}`)
        .toBeVisible();
    }
  });

  test('a blank address blocks the step', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await page.locator('textarea[name="address"]').fill('');

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);

    await expect(page.locator('select[name="complainantCategory"]'), 'the step must be held').toBeVisible();
    expect(await fieldError(page, 'address')).toBeTruthy();
  });

  test('CONTRADICTION: the blank-address message is "Address is required", not "Response is mandatory."', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await page.locator('textarea[name="address"]').fill('');

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);

    expect(await fieldError(page, 'address')).toBe('Address is required');
  });

  test('a whitespace-only address is rejected — it is trimmed before the mandatory check', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);

    // Case 21.7. Unlike the email field, a textarea is NOT sanitised by the browser, so the spaces
    // genuinely reach the model and the validator's .trim() is what rejects them.
    await page.locator('textarea[name="address"]').fill('     ');
    expect(await page.locator('textarea[name="address"]').inputValue(),
      'a textarea retains whitespace verbatim').toBe('     ');

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);
    expect(await fieldError(page, 'address')).toBe('Address is required');
  });

  test('the address accepts alphanumerics, interior spaces, special characters and emoji', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const address = page.locator('textarea[name="address"]');

    // Cases 21.3 (alphanumeric), 21.4 (special chars), 21.5 (emoji), 21.8 (interior spaces).
    const accepted = [
      'Flat 4B, 221 Baker Street, Pune 411001',
      'C/o R.K. Sharma & Sons (Near St. Mary\'s) #12-A/3',
      'Ghar \u{1F3E0} Pune',
      'Plot 7   Sector 22',
    ];
    for (const value of accepted) {
      await address.fill(value);
      expect(await address.inputValue(), `${JSON.stringify(value)} must be retained verbatim`).toBe(value);
    }
  });

  test('a padded address is accepted and its surrounding spaces are not treated as an error', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);

    // Case 21.6 expects leading/trailing spaces to be rejected. They are not: the validator trims
    // only to test emptiness, so a padded but non-empty address passes and advances.
    await page.locator('textarea[name="address"]').fill('  12 Test Street, Pune  ');

    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });

  test('the address accepts exactly 100 characters', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const address = page.locator('textarea[name="address"]');

    // The cap is read from the attribute rather than hardcoded, so a configuration change to the
    // template does not silently invalidate this test (ruling 1).
    const cap = Number(await address.getAttribute('maxlength'));
    expect(cap, 'the address textarea must declare a maxlength').toBeGreaterThan(0);

    const exact = 'A'.repeat(cap);
    await address.fill(exact);
    expect((await address.inputValue()).length, `exactly ${cap} characters must be accepted`).toBe(cap);
  });

  test('CONTRADICTION: an over-long address is silently truncated by the browser — no "Address cannot exceed 100 characters." is ever shown', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    const address = page.locator('textarea[name="address"]');

    const cap = Number(await address.getAttribute('maxlength'));
    await address.fill('A'.repeat(cap + 50));

    // Case 21.10 asks for truncation AND a message. Only truncation happens: the over-length value
    // never exists in the model, so no validator can fire.
    expect((await address.inputValue()).length, `the browser truncates at maxlength=${cap}`).toBe(cap);
    expect(await fieldError(page, 'address'),
      'no over-length message exists, because no over-length value ever reaches Angular').toBe('');

    // And the truncated value is perfectly valid, so the step advances.
    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });

  test('a filled address together with a derived state advances to Regulated Entity Details', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="lastName"]').fill('Citizen');
    await fillPincodeAndWaitForLookup(page, PINCODE_A);
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();

    // Covers the trailing "user is navigated to the Regulated Entity details screen" case that closes
    // blocks 19, 20, 21 and 22 alike.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });
});
