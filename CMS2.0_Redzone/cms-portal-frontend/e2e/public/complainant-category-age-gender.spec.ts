import { test, expect, type Page } from '../fixtures';
import { fillPincode, reachComplainantDetails, sessionAMobile } from './helpers-auth-a';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 / SESSION A — Complainant Category, Age and Gender
 * Manual block 8 (lines 218-314, 8 cases), block 12 (578-686, 18 cases), block 13 (687-771, 9 cases).
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * All three blocks live on step 1 of the filing wizard and all three are driven by ONE control — the
 * Complainant Category dropdown decides whether Age and Gender are rendered at all — so they are one
 * spec. Their first three cases are each the same login + eligibility preamble, which blocks 3-7 already
 * assert exhaustively; here it is a precondition, reached by `reachComplainantDetails`.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * THESE ARE NATIVE <select> ELEMENTS, WHICH SETTLES FOUR CASES BY CONSTRUCTION
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * Both dropdowns are plain `<select>` (not a PrimeNG overlay), so "the dropdown opens when clicked",
 * "closes after selecting", "a single value can be selected" and "multiple values cannot be selected"
 * are properties of the platform control, not of this product. Playwright cannot observe a native
 * dropdown's popup at all — it is drawn by the OS, outside the DOM — so asserting "it opened" is not
 * possible and pretending otherwise would be a test that passes without checking anything.
 *
 * What IS asserted instead is the thing those cases are actually protecting: the control is a single-
 * valued `<select>` with no `multiple` attribute and no `size` (so it renders as a dropdown rather than
 * a list box), and it holds exactly one value after two successive selections. That is the whole
 * observable contract. See §4 of findings-QA-A.md.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * TWO SERIOUS DEFECTS WERE FOUND ON THIS SCREEN — A-D4 AND A-D5. BOTH ARE NOW FIXED.
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * A-D4  `isIndividualCategory()` was `cat === 'individual' || cat === 'senior_citizen'` — Person with
 *       Disabilities was NOT in it, so a disabled complainant was shown the ORGANISATION form and asked
 *       for an Organisation Name, with no Age or Gender. `pwd` is now an individual category.
 * A-D5  the step-1 validator required `formData['firstName']`, but First Name renders ONLY inside
 *       `@if (isIndividualCategory())`. So for any of the ten non-individual categories the wizard could
 *       not be left — Next reported "First name is required" against a field that did not exist on the
 *       screen. Ten of the twelve documented categories could not file a complaint at all.
 *       `validateCurrentStep()` now requires the name field that is actually RENDERED for the chosen
 *       category, and requires the category itself first (A-C11/A-C11b).
 *
 * Session B found both independently from the Organisation-Landline side (QA-B17); they are recorded
 * once, here, with the category dropdown as the cause.
 */

/** The twelve values manual case 218-314 enumerates, in the order QA lists them. */
const CATEGORIES: Array<{ value: string; label: string }> = [
  { value: 'individual', label: 'Individual' },
  { value: 'pwd', label: 'Person with Disabilities' },
  { value: 'senior_citizen', label: 'Senior Citizen' },
  { value: 'individual_business', label: 'Individual – Business' },
  { value: 'proprietorship', label: 'Proprietorship' },
  { value: 'partnership', label: 'Partnership' },
  { value: 'msme', label: 'MSME' },
  { value: 'association', label: 'Association' },
  { value: 'trust', label: 'Trust' },
  { value: 'limited_company', label: 'Limited Company' },
  { value: 'government_department', label: 'Government Department' },
  { value: 'psu', label: 'PSU' },
];

/** The four values block 13 enumerates, and the whole permitted domain per the A-C10 ruling. */
const GENDERS_DOCUMENTED = ['Male', 'Female', 'Transgender', 'Do not wish to disclose'];

const CATEGORY = 'select[name="complainantCategory"]';
const AGE = 'input[name="age"]';
const GENDER = 'select[name="gender"]';

async function selectCategory(page: Page, value: string): Promise<void> {
  await page.locator(CATEGORY).selectOption(value);
}

/** The visible option labels of a native select, in document order, minus its placeholder. */
async function optionLabels(page: Page, selector: string): Promise<string[]> {
  return page.locator(`${selector} option`).evaluateAll((els) =>
    els
      .filter((el) => (el as HTMLOptionElement).value !== '')
      .map((el) => (el.textContent || '').trim()));
}

test.describe('QA-A8 — the Complainant Category dropdown', () => {

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 218-246 — the login + eligibility preamble lands on the Complainant Details screen, and the
  // Complainant Category dropdown is present on it.
  //
  // The preamble's own three cases are covered by blocks 3-7 (login) and the eligibility-* specs (the
  // gate); what is asserted here is the HANDOVER — that passing the gate produces this screen — plus
  // the field's presence and that it is mandatory in the markup.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the Complainant Category dropdown is present on the Complainant Details screen', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(501));

    const category = page.locator(CATEGORY);
    await expect(category).toBeVisible();
    await expect(category).toBeEnabled();

    // Marked mandatory to the citizen, which is what case 249-252 assumes before testing the blank.
    const label = page.locator('.form-group', { has: page.locator(CATEGORY) }).locator('label');
    await expect(label).toContainText(/complainant category/i);
    await expect(label.locator('.required')).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 253-290 — the twelve documented values are present in the dropdown.
  //
  // Asserted as an EXACT SET, not as twelve `toContain` checks: a missing value and an extra
  // undocumented value are both defects, and only an exact comparison catches the second. The en-dash in
  // "Individual – Business" is reproduced deliberately — it is what both the manual pack and the product
  // use, and a hyphen here would be a false failure.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the dropdown offers exactly the twelve documented categories', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(502));

    const labels = await optionLabels(page, CATEGORY);
    expect(labels, 'the Complainant Category options differ from the twelve QA documents')
      .toEqual(CATEGORIES.map((c) => c.label));

    // Every one must be genuinely selectable, not merely listed.
    for (const { value, label } of CATEGORIES) {
      await selectCategory(page, value);
      await expect(page.locator(CATEGORY), `${label} could not be selected`).toHaveValue(value);
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 249-252 — the dropdown cannot be kept blank, with "Response is mandatory." displayed.
  //
  // ═══ A-C11 / A-C11b, both FIXED. What they were: ═══
  // `validateCurrentStep()` for step 1 did not check `complainantCategory` at all. What stopped a blank
  // category was indirect — with nothing selected neither the individual block nor the organisation block
  // renders, so First Name was never on screen, so the firstName check failed and Next was refused (the
  // same root cause as A-D5).
  //
  // A-C11b made that a DEAD END: both renderers of `validationErrors['name']` sit INSIDE those two
  // category blocks, so when the category was blank the error actually refusing Next had nowhere to
  // appear. The citizen saw pincode/state/address complaints, fixed all three, pressed Next again — and
  // was refused a second time with NO message and nothing left to correct.
  //
  // Now: the category is validated by name, first, and its error renders OUTSIDE both blocks. QA's literal
  // "Response is mandatory." is not asserted — the product names the field it is talking about, which is
  // strictly more useful — but the requirement that the refusal be stated is.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('a blank category blocks Next and says so, against the category field', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(503));

    await expect(page.locator(CATEGORY), 'the category dropdown is pre-selected rather than blank')
      .toHaveValue('');
    // The placeholder is disabled, so a citizen cannot deliberately return to "no selection".
    await expect(page.locator(`${CATEGORY} option[value=""]`)).toHaveAttribute('disabled', '');

    await page.locator('button.btn-next').click();

    // Still on step 1 — the blank category did not get through.
    await expect(page.locator(CATEGORY), 'a blank Complainant Category advanced the wizard').toBeVisible();

    // And the citizen is told which field is the problem, by name.
    await expect(page.locator('.field-error').filter({ hasText: /categor/i }),
      'a blank Complainant Category was refused without naming the category').toHaveCount(1);

    // The dead end is gone: correcting the OTHER visible complaints does not leave a silent refusal.
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await page.locator('button.btn-next').click();

    await expect(page.locator('.field-error').filter({ hasText: /categor/i }),
      'the second refusal went silent — the A-C11b dead end has returned').toHaveCount(1);
    await expect(page.locator(CATEGORY),
      'a blank category advanced the wizard').toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 291-306 — a single value can be selected, multiple cannot, and the dropdown closes on selection.
  //
  // Asserted as the control's contract rather than as an interaction: a native <select> without
  // `multiple` cannot hold two values and its popup is OS-drawn and unobservable to Playwright (see the
  // header). Two successive selections prove the second REPLACES the first, which is what "not able to
  // select multiple values" means for this widget.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the category holds exactly one value: a second selection replaces the first', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(504));

    const category = page.locator(CATEGORY);
    await expect(category, 'the category dropdown accepts multiple values')
      .not.toHaveAttribute('multiple', /.*/);
    // No `size`, so the browser renders a closing dropdown and not a permanently-open list box.
    await expect(category).not.toHaveAttribute('size', /.*/);

    await selectCategory(page, 'trust');
    await expect(category).toHaveValue('trust');
    await selectCategory(page, 'msme');
    await expect(category, 'selecting a second category did not replace the first').toHaveValue('msme');

    const selected = await page.locator(`${CATEGORY} option:checked`).count();
    expect(selected, 'more than one category option is selected at once').toBe(1);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 307-314 — with every mandatory field filled, Next reaches the Regulated Entity Details screen.
  //
  // This was once reachable ONLY for Individual and Senior Citizen; the next test covers the other ten.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('with the mandatory fields filled, an Individual advances to Regulated Entity Details', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(505));

    await selectCategory(page, 'individual');
    await page.locator('input[name="firstName"]').fill('Ramesh');
    await page.locator('input[name="lastName"]').fill('Kumar');
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();

    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'a fully filled Individual step 1 did not reach Regulated Entity Details')
      .toBeVisible({ timeout: 20000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // ═══ A-D5, FIXED: every organisation category can now leave step 1 ═══
  //
  // `validateCurrentStep()` demanded `formData['firstName']`, but the First Name input renders only inside
  // `@if (isIndividualCategory())`. Selecting anything except Individual or Senior Citizen showed the
  // citizen an Organisation Name field; they filled everything the screen asked for, pressed Next, and
  // were told "First name is required" — about a field not on the screen and impossible to reach. Every
  // organisation, trust, MSME, PSU and government department was unable to file at all.
  //
  // The validator now requires the name field that is actually RENDERED for the chosen category. Checked
  // across all nine organisation categories rather than one, because a per-category `@if` is exactly the
  // shape of bug that gets fixed for the case someone tested and left broken for the rest. (PwD is now an
  // individual category — A-D4 — so it is covered by the individual path, not here.)
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('A-D5: every organisation category can complete step 1 and reach Regulated Entity Details', async ({ page }) => {
    const organisations = CATEGORIES.filter(
      (c) => !['individual', 'senior_citizen', 'pwd'].includes(c.value));

    // A distinct mobile per category: each iteration logs in again, and OTP cooldown/resend limits key on
    // the number, so reusing one would couple nine independent checks together.
    for (const [i, { value, label }] of organisations.entries()) {
      await reachComplainantDetails(page, sessionAMobile(530 + i));
      await selectCategory(page, value);

      // Fill everything this screen actually offers. First Name is not merely empty — it does not exist.
      expect(await page.locator('input[name="firstName"]').count(),
        `${label} renders a First Name field, so it is not on the organisation branch`).toBe(0);
      await page.locator('input[name="organizationName"]').fill(`${label} Test Body`);
      await page.locator('input[name="email"]').fill('qa.orgcat@example.com');
      await fillPincode(page, '411001');
      await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

      await page.locator('button.btn-next').click();

      await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
        `${label} filled step 1 completely and still could not advance — A-D5 has regressed for it`)
        .toBeVisible({ timeout: 20000 });
    }
  });

  // The other half of the same fix: a MISSING organisation name must still be refused, and refused
  // against the field the citizen can actually see. Without this, "every category can advance" could be
  // satisfied by dropping the name requirement altogether.
  test('an organisation category with no name is refused, naming the visible field', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(521));

    await selectCategory(page, 'trust');
    await page.locator('input[name="email"]').fill('qa.orgblank@example.com');
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    // organizationName deliberately left blank.

    await page.locator('button.btn-next').click();

    await expect(page.locator(CATEGORY), 'a nameless organisation advanced the wizard').toBeVisible();
    const errors = (await page.locator('.field-error').allTextContents()).join(' | ');
    expect(errors, 'the refusal named First Name, a field an organisation never sees')
      .not.toMatch(/first name/i);
    expect(errors, 'a blank organisation name was refused with no message at all')
      .toMatch(/name of complainant is required/i);
  });
});

test.describe('QA-A12 — the Age field', () => {

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 601-613 — Age is displayed for Individual / Senior Citizen / Person with Disabilities, and NOT for
  // the other categories.
  //
  // ═══ A-D4, FIXED: PwD was treated as an ORGANISATION ═══
  // `isIndividualCategory()` was `cat === 'individual' || cat === 'senior_citizen'`, with PwD absent — so a
  // complainant with disabilities got no Age, no Gender and no First Name, but an Organisation Name field,
  // and by A-D5 could not file at all. QA names PwD in both block 12 and block 13, so this was a
  // documented requirement, not an inference. `pwd` is now in the individual set.
  //
  // Asserted per category, so the boundary is measured rather than assumed.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Age is shown for the three individual categories and hidden for the organisation categories', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(507));

    for (const value of ['individual', 'pwd', 'senior_citizen']) {
      await selectCategory(page, value);
      await expect(page.locator(AGE), `Age is not displayed for ${value}`).toBeVisible();
    }

    for (const { value, label } of CATEGORIES) {
      if (['individual', 'senior_citizen', 'pwd'].includes(value)) continue;
      await selectCategory(page, value);
      await expect(page.locator(AGE), `Age is displayed for ${label}, which is an organisation`)
        .toHaveCount(0);
    }
  });

  test('Person with Disabilities is an individual category, with Age, Gender and First Name', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(508));

    await selectCategory(page, 'pwd');
    await expect(page.locator(AGE),
      'Person with Disabilities is back on the organisation branch — A-D4 has regressed').toBeVisible();
    await expect(page.locator(GENDER)).toBeVisible();
    await expect(page.locator('input[name="firstName"]')).toBeVisible();
    await expect(page.locator('input[name="organizationName"]')).toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 614-617 — the Age field may be left blank.
  //
  // Proven by ADVANCING with it blank, not merely by the absence of an error: an optional field that
  // silently blocks submission is optional in appearance only. `validateCurrentStep` does not mention
  // age, and the label carries no asterisk, so both the markup and the behaviour are asserted.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Age may be left blank and the wizard still advances', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(509));

    await selectCategory(page, 'individual');
    const label = page.locator('.form-group', { has: page.locator(AGE) }).locator('label');
    await expect(label.locator('.required'), 'Age is marked mandatory').toHaveCount(0);

    await page.locator('input[name="firstName"]').fill('Ramesh');
    await page.locator('input[name="lastName"]').fill('Kumar');
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await expect(page.locator(AGE)).toHaveValue('');

    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'a blank Age blocked the wizard, so Age is not optional after all')
      .toBeVisible({ timeout: 20000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 618-621 — the field accepts numeric data. 651-654 / 683-686 — Individual accepts any positive value.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Age accepts numeric data and raises no error for a plausible age', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(510));
    await selectCategory(page, 'individual');

    for (const value of ['1', '45', '150']) {
      await page.locator(AGE).fill(value);
      await expect(page.locator(AGE)).toHaveValue(value);
      await expect(page.locator('.field-error').filter({ hasText: /age/i }),
        `a valid age of ${value} was reported as an error`).toHaveCount(0);
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 622-641 — the field must reject negatives, decimals, values starting with 0, only 0, alphanumeric,
  // alphabetic, special characters, emojis, only spaces, and leading/trailing spaces — each with the
  // message "Only numbers are allowed."
  //
  // ═══ A-C12: THE FIELD HAS NO INPUT FILTER AND THAT MESSAGE DOES NOT EXIST ═══
  // `<input type="text" maxlength="3" pattern="[0-9]*" inputmode="numeric" (ngModelChange)="validateAge()">`
  //   - `type="text"`, so nothing is filtered by the browser;
  //   - `pattern` on an input outside a submitted form is inert — Angular never reads it, and the
  //     component's own validator never reads it either;
  //   - `inputmode="numeric"` only hints which on-screen keyboard a phone should show. On a desktop it
  //     does nothing at all.
  // So every one of these classes is RETAINED in the field, and `validateAge()` produces one of only
  // three messages: "Age must be a number", "Age must not exceed 3 digits", "Age must between 1 and 150"
  // [sic — see the code], or the Senior Citizen message. "Only numbers are allowed." is nowhere in the
  // product.
  //
  // The cases are therefore split into what the product DOES reject (with its own wording) and what it
  // SILENTLY ACCEPTS, because the two groups need different rulings from the BA. Each row states which.
  //
  // `maxlength="3"` also quietly decides several of these: a decimal like "45.5" and " 45 " are truncated
  // to three characters before any validator sees them, so the case cannot be tested as written. That is
  // recorded per row rather than hidden.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  const AGE_REJECTED: Array<{ label: string; typed: string; expect: RegExp }> = [
    // Number('-45') is -45, which fails `age < 1`. Note maxlength=3 keeps "-45" intact.
    { label: 'a negative number', typed: '-45', expect: /between 1 and 150/i },
    { label: 'only zero', typed: '0', expect: /between 1 and 150/i },
    { label: 'a value starting with zero that is still under 1', typed: '000', expect: /between 1 and 150/i },
    { label: 'alphabetic data', typed: 'abc', expect: /must be a number/i },
    { label: 'alphanumeric data', typed: 'a1b', expect: /must be a number/i },
    { label: 'special characters', typed: '@#$', expect: /must be a number/i },
    { label: 'an emoji', typed: '\u{1F600}', expect: /must be a number/i },
    { label: 'a value above the 150 ceiling', typed: '151', expect: /between 1 and 150/i },
  ];

  for (const { label, typed, expect: pattern } of AGE_REJECTED) {
    test(`Age rejects ${label}, in the product's own wording`, async ({ page }) => {
      await reachComplainantDetails(page, sessionAMobile(511));
      await selectCategory(page, 'individual');

      await page.locator(AGE).fill(typed);

      const error = page.locator('.field-error').filter({ hasText: /age/i });
      await expect(error, `${label} raised no age error at all`).toBeVisible({ timeout: 10000 });
      await expect(error, `the wording for ${label} has changed`).toContainText(pattern);

      // A-C12: the documented sentence is not what is shown. Asserted so that adding it is noticed.
      await expect(page.locator('.field-error').filter({ hasText: /only numbers are allowed/i }),
        'the product now says "Only numbers are allowed." — A-C12 is resolved; update the finding')
        .toHaveCount(0);
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 626-629 ("does not accept input starting from 0"), 642-650 (spaces), 630-633 (decimals).
  //
  // These four are SILENTLY ACCEPTED and each for its own reason. Grouped into one test because what
  // matters to the BA is the list, and splitting them would repeat a 20-second wizard walk four times.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('A-C12: Age silently accepts leading zeros, spaces and decimals, with no message', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(512));
    await selectCategory(page, 'individual');

    const age = page.locator(AGE);
    const error = page.locator('.field-error').filter({ hasText: /age/i });

    // "045" — QA says input starting with 0 must be refused. Number('045') is 45, which is in range.
    await age.fill('045');
    await expect(age, 'the leading zero was filtered out of the field').toHaveValue('045');
    await expect(error, 'a leading zero is now rejected — A-C12 changed').toHaveCount(0);

    // " 45" / "45 " — QA says leading and trailing spaces must be refused. Number(' 45') is 45.
    for (const spaced of [' 45', '45 ']) {
      await age.fill(spaced);
      await expect(error, `"${spaced}" is now rejected — A-C12 changed`).toHaveCount(0);
    }

    // "4 5" — a space INSIDE the value. Number('4 5') is NaN, so this one IS caught, which means the
    // product's treatment of spaces is inconsistent: outside the digits they are ignored, inside they
    // are an error.
    await age.fill('4 5');
    await expect(error, 'an interior space is no longer rejected').toBeVisible();
    await expect(error).toContainText(/must be a number/i);

    // "4.5" — a decimal. maxlength="3" admits exactly three characters, and Number('4.5') is 4.5, which
    // passes `age < 1 || age > 150`, so a fractional age is accepted outright.
    await age.fill('4.5');
    await expect(age).toHaveValue('4.5');
    await expect(error, 'a decimal age is now rejected — A-C12 changed').toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 634-637 — "does not accept more than 3 digits" is enforced by maxlength, so it is asserted as
  // truncation. `fill()` OBEYS maxlength and the overflow would never arrive, so the 4th character is
  // typed with pressSequentially — the same trap already recorded for the mobile and OTP fields.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Age is capped at three characters by the control itself', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(513));
    await selectCategory(page, 'individual');

    const age = page.locator(AGE);
    await expect(age).toHaveAttribute('maxlength', '3');

    await age.click();
    await age.pressSequentially('1234', { delay: 20 });
    expect(await age.inputValue(), 'the Age field retained a fourth character').toHaveLength(3);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 655-682 — Senior Citizen requires an age of 60 or above; below 60 must be refused with
  // "Age must be 60 or above for Senior Citizen category."
  //
  // A-C13: the product says "Age must be 60 or above for Senior Citizen" — WITHOUT the trailing word
  // "category." Same rule, one word short of the quoted string. The shipped wording is asserted so a
  // correct product is not failed over a paraphrase, and the difference is recorded for the BA.
  //
  // Both sides of the boundary are asserted, and 60 itself is asserted as ACCEPTED: the rule is
  // `age < 60`, and a test that only proved 59 fails would pass against an off-by-one that also
  // rejected 60.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Senior Citizen requires 60 or above, and 60 itself is accepted', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(514));
    await selectCategory(page, 'senior_citizen');

    const age = page.locator(AGE);
    const error = page.locator('.field-error').filter({ hasText: /age/i });

    // Below the boundary — refused.
    await age.fill('59');
    await expect(error, 'an age of 59 was accepted for Senior Citizen').toBeVisible({ timeout: 10000 });
    await expect(error).toContainText(/60 or above for senior citizen/i);

    // On the boundary — accepted. `age < 60` must not be `age <= 60`.
    await age.fill('60');
    await expect(error, 'an age of exactly 60 was refused for Senior Citizen').toHaveCount(0);

    // Above it — accepted.
    await age.fill('85');
    await expect(error).toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 683-686 — for Individual, any positive value is accepted.
  //
  // The discriminating half is that 59 — refused a moment ago as a Senior Citizen — must be ACCEPTED
  // here. The rule is conditional on the category, and only switching the category with the same value
  // in the field proves the condition is actually read.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the 60-year rule applies to Senior Citizen only: 59 is fine for an Individual', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(515));

    const age = page.locator(AGE);
    const error = page.locator('.field-error').filter({ hasText: /age/i });

    await selectCategory(page, 'senior_citizen');
    await age.fill('25');
    await expect(error, 'the Senior Citizen floor was not applied').toBeVisible({ timeout: 10000 });

    // Same value, different category. The error must clear — and it only can if validateAge re-runs on
    // the category too, which is the behaviour this case is really about.
    await selectCategory(page, 'individual');
    await age.fill('25');
    await expect(error, 'an age of 25 is refused for an Individual — the 60 rule is not category-bound')
      .toHaveCount(0);
  });
});

test.describe('QA-A13 — the Gender dropdown', () => {

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 711-723 — Gender is displayed for the individual categories and not for the others. PwD is affected
  // by A-D4 exactly as Age is; it is asserted in the A-D4 test above rather than twice.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Gender is shown for Individual and Senior Citizen and hidden for organisations', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(516));

    for (const value of ['individual', 'senior_citizen']) {
      await selectCategory(page, value);
      await expect(page.locator(GENDER), `Gender is not displayed for ${value}`).toBeVisible();
    }

    for (const { value, label } of CATEGORIES) {
      if (value === 'individual' || value === 'senior_citizen' || value === 'pwd') continue;
      await selectCategory(page, value);
      await expect(page.locator(GENDER), `Gender is displayed for ${label}`).toHaveCount(0);
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 724-727 — Gender may be left blank.
  //
  // Proven by advancing, for the same reason as the Age case. Note the placeholder here is NOT disabled
  // (unlike the category's), so returning to "no selection" is genuinely available to the citizen —
  // which is what makes this field optional in practice and not just in the validator.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Gender may be left blank, and the citizen can return to no selection', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(517));
    await selectCategory(page, 'individual');

    const gender = page.locator(GENDER);
    await expect(gender).toHaveValue('');
    const label = page.locator('.form-group', { has: gender }).locator('label');
    await expect(label.locator('.required'), 'Gender is marked mandatory').toHaveCount(0);

    // Select, then deselect — the reverse transition, which nothing else covers.
    await gender.selectOption('female');
    await expect(gender).toHaveValue('female');
    await gender.selectOption('');
    await expect(gender, 'Gender could not be returned to no selection once chosen').toHaveValue('');

    await page.locator('input[name="firstName"]').fill('Ramesh');
    await page.locator('input[name="lastName"]').fill('Kumar');
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await page.locator('button.btn-next').click();

    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'a blank Gender blocked the wizard').toBeVisible({ timeout: 20000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 732-745 — the four documented values are present.
  //
  // ═══ A-C10, RULED AND FIXED: the permitted domain is exactly these four ═══
  // The product also offered a fifth, "Other", which appears in no requirement and which a citizen could
  // submit and have stored. Ruled: male / female / transgender / do-not-wish-to-disclose only — the fifth
  // duplicated "do not wish to disclose" in practice. It has been removed from the control.
  //
  // Asserted as an EXACT set, not with `toContain`: a `toContain` check of the four passed happily while
  // the undocumented fifth sat there, which is how this was missed in the first place.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Gender offers exactly the four permitted values', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(518));
    await selectCategory(page, 'individual');

    const labels = await optionLabels(page, GENDER);

    expect(labels, 'the Gender domain has changed — an undocumented value may have been reintroduced')
      .toEqual(GENDERS_DOCUMENTED);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 746-763 — a single value can be selected, multiple cannot, and the dropdown closes on selection.
  // Asserted as the native <select> contract, per the header.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('Gender holds exactly one value: a second selection replaces the first', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(519));
    await selectCategory(page, 'individual');

    const gender = page.locator(GENDER);
    await expect(gender, 'the Gender dropdown accepts multiple values')
      .not.toHaveAttribute('multiple', /.*/);
    await expect(gender).not.toHaveAttribute('size', /.*/);

    await gender.selectOption('male');
    await expect(gender).toHaveValue('male');
    await gender.selectOption('transgender');
    await expect(gender, 'selecting a second gender did not replace the first').toHaveValue('transgender');

    expect(await page.locator(`${GENDER} option:checked`).count(),
      'more than one gender option is selected at once').toBe(1);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 764-771 — with Gender chosen and the mandatory fields filled, Next reaches RE Details, and the value
  // SURVIVES the step change.
  //
  // The survival half is the part worth testing: a value that is collected and then dropped on
  // navigation would satisfy every other case in this block. Proven by returning to step 1 through the
  // step bar and finding it still selected.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('a chosen Gender survives advancing to step 2 and coming back', async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(520));

    await selectCategory(page, 'individual');
    await page.locator(GENDER).selectOption('not_disclosed');
    await page.locator('input[name="firstName"]').fill('Ramesh');
    await page.locator('input[name="lastName"]').fill('Kumar');
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });

    // Back to step 1 via the step bar, which is only clickable for a step already reached.
    await page.locator('.step-item').first().click();
    await expect(page.locator(GENDER)).toBeVisible({ timeout: 20000 });
    await expect(page.locator(GENDER), 'the chosen Gender was lost on navigation')
      .toHaveValue('not_disclosed');
  });
});
