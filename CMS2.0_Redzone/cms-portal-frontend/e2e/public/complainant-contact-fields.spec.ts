/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B17 / QA-B18 — Complainant Details: Organisation Landline Number, and Email Id
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack blocks 17 (lines 964-1081, 16 cases) and 18 (lines 1082-1164, 14 cases). Both fields sit
 * on form step 1 — NOT step 3; see the step-numbering note in helpers-forms-b.ts.
 *
 * ── THE CENTRAL FINDING, AND WHY MOST OF THESE TESTS ASSERT SOMETHING ELSE ────────────────────────
 * Block 17 asserts eleven times that the landline field rejects an input class and displays
 * "Only numbers are allowed." That message does not exist anywhere in the product. The field is:
 *
 *     <input type="tel" [(ngModel)]="formData['orgLandline']" name="orgLandline">
 *
 * with NO maxlength, NO pattern, and no mention of `orgLandline` in validateCurrentStep() — which
 * validates only firstName, pincode, state, address and email on step 1 (component.ts:1652-1657). So
 * the field accepts absolutely anything, including letters, emoji and 500 characters, and never
 * displays any message at all. `type="tel"` does not filter input; unlike type="number" it is a free
 * text field with a telephone keypad hint on mobile.
 *
 * These tests therefore assert the OBSERVED behaviour (accepts everything, validates nothing) and each
 * one names the manual case it contradicts. That list is the deliverable — it tells the BA that block
 * 17's 10-digit rule and its error message are UNIMPLEMENTED, not merely mis-worded. Writing eleven
 * tests that "pass" by asserting no error appears would hide exactly that.
 *
 * Case 17.1 (the field is hidden for Individual/Senior Citizen and shown for organisation categories)
 * IS implemented and is asserted properly below — the template gates it behind
 * `!isIndividualCategory() && formData['complainantCategory']`.
 *
 * ── EMAIL IS REAL, BUT WEAKER AND LATER THAN THE PACK CLAIMS ─────────────────────────────────────
 * Email IS validated: /^[^\s@]+@[^\s@]+\.[^\s@]+$/ at component.ts:1657. Three divergences from the
 * pack, all asserted and logged:
 *   1. The message is "Invalid email format", not "Enter a valid email address."
 *   2. It fires only on Next, not on blur or as you type — so "error message should be displayed"
 *      after merely entering a value is false; nothing appears until you try to advance.
 *   3. There is NO 64-character limit. The input carries no maxlength and the validator has no length
 *      check, so a 200-character address is accepted. Block 18's last two cases are unimplemented.
 * The regex also accepts things a stricter reading would reject (consecutive dots, a trailing
 * hyphen); the tests pin what it actually does so a future tightening shows up as a deliberate change.
 *
 * ── WHY `fill()` AND NOT `pressSequentially()` ───────────────────────────────────────────────────
 * Neither field has an input mask or a keystroke handler, so fill() and per-key typing are equivalent
 * here and fill() is an order of magnitude faster across ~30 cases. The masked date fields elsewhere
 * in this wizard DO need pressSequentially; these do not.
 *
 * ── NO CLEANUP ───────────────────────────────────────────────────────────────────────────────────
 * Nothing is submitted and no OTP is sent, so no rows exist to delete. Mobiles come from Session B's
 * reserved 98765_2____ range anyway.
 */

import { test, expect } from '../fixtures';
import {
  gotoComplainantDetails, fieldError, clickNextAndCollectErrors, redirectAppApi,
} from './helpers-forms-b';

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 17 — Organisation Landline Number
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B17 — the Organisation Landline field appears only for organisation categories, and validates nothing at all', () => {

  test('the landline field is hidden for Individual and Senior Citizen', async ({ page }) => {
    await gotoComplainantDetails(page);
    const landline = page.locator('input[name="orgLandline"]');

    for (const category of ['individual', 'senior_citizen']) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(landline, `${category} is an individual category and must not show a landline field`)
        .toHaveCount(0);
    }
  });

  /**
   * DEFECT. Manual case 17.3 names three categories that must NOT see organisation fields:
   * "Individual / Senior Citizen / Person with disablilities". The product only excludes two —
   * isIndividualCategory() (component.ts:2233-2236) tests `individual` and `senior_citizen` and omits
   * `pwd`. So a Person with Disabilities is classed as an ORGANISATION: they are asked for an
   * Organisation Name and an Organisation Landline, and are never shown a First Name field.
   *
   * That is not cosmetic. The step-1 validator requires formData['firstName'] (component.ts:1653),
   * and for `pwd` no First Name input is ever rendered — so the field it demands cannot be filled.
   * See the companion test below: this category cannot complete step 1 at all. Filed as a blocking
   * defect, not merely a mislabelled section.
   */
  test.fixme('DEFECT: Person with Disabilities is treated as an organisation — it is shown Organisation Name/Landline and no First Name', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('pwd');

    await expect(page.locator('input[name="orgLandline"]'),
      'case 17.3: PwD must not be shown the Organisation Landline field').toHaveCount(0);
    await expect(page.locator('input[name="organizationName"]'),
      'case 17.3: PwD must not be shown an Organisation Name field').toHaveCount(0);
    await expect(page.locator('input[name="firstName"]'),
      'PwD is an individual and must be able to give a first name').toBeVisible();
  });

  test('the landline field appears for every non-individual category', async ({ page }) => {
    await gotoComplainantDetails(page);
    const landline = page.locator('input[name="orgLandline"]');

    // Driven from the options the product actually renders, so a new category cannot silently escape.
    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    const individual = ['individual', 'senior_citizen', 'pwd'];
    const orgCategories = categories.filter(c => !individual.includes(c));
    expect(orgCategories.length, 'expected several organisation categories').toBeGreaterThan(0);

    for (const category of orgCategories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(landline, `${category} is an organisation category and must show a landline field`)
        .toBeVisible();
    }
  });

  test('the landline field may be left blank — it is never reported as an error', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    await expect(page.locator('input[name="orgLandline"]')).toHaveValue('');

    await page.locator('input[name="organizationName"]').fill('Test Partners LLP');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'a blank landline must not be reported as an error').not.toMatch(/landline/i);
  });

  /**
   * DEFECT — and the reason the test above asserts only "no landline error" rather than "it advances".
   *
   * No organisation category can get past step 1. The validator requires formData['firstName']
   * (component.ts:1653) unconditionally, but the First Name input is rendered only inside the
   * `isIndividualCategory()` branch (template:495). For every organisation category the template
   * instead renders `organizationName` (template:537) and never writes firstName, so the step fails
   * with "First name is required" pointing at a field that is not on the screen — an unsatisfiable,
   * invisible requirement. The citizen sees a red Organisation Name box (validationErrors['name']
   * styles it) with a message about a first name.
   *
   * This blocks ALL 10 organisation categories from filing, which makes block 17's remaining
   * "then proceed" cases untestable through the UI. Reported rather than worked around.
   */
  test.fixme('DEFECT: no organisation category can leave step 1 — the validator requires a First Name field that is never rendered', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    await page.locator('input[name="organizationName"]').fill('Test Partners LLP');
    await page.locator('input[name="orgLandline"]').fill('2026123456');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();

    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'a fully-filled organisation complainant must reach step 2').toBeVisible({ timeout: 20000 });
  });

  test('the landline field accepts a 10-digit number', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    const landline = page.locator('input[name="orgLandline"]');

    await landline.fill('2026123456');
    await expect(landline).toHaveValue('2026123456');
    expect(await fieldError(page, 'orgLandline')).toBe('');
  });

  /**
   * The eleven rejection cases (17.6-17.16), collapsed into one table-driven test.
   *
   * Every row is a manual case asserting the field REJECTS the input and shows "Only numbers are
   * allowed." Not one of them holds: the field has no validator. Asserting the real behaviour in a
   * single test named for what it found is more honest, and more useful to the BA, than eleven
   * green tests that each quietly assert an absence.
   */
  test('CONTRADICTION: the landline field accepts every input class the pack says it rejects, and never shows "Only numbers are allowed."', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    const landline = page.locator('input[name="orgLandline"]');

    const rejectedByTheDocument: Array<[string, string]> = [
      ['negative number (case 17.6)', '-2026123456'],
      ['decimal (17.7)', '2026.123456'],
      ['alphanumeric (17.8)', 'abc1234567'],
      ['alphabetic (17.9)', 'abcdefghij'],
      ['special characters (17.10)', '!@#$%^&*()'],
      ['emoji (17.11)', '\u{1F600}\u{1F4DE}'],
      ['only spaces (17.12)', '     '],
      ['leading and trailing spaces (17.13)', '  2026123456  '],
      ['spaces in between (17.14)', '2026 123 456'],
      ['more than 10 digits (17.16)', '20261234567890'],
      ['fewer than 10 digits (17.17)', '12345'],
    ];

    const accepted: string[] = [];
    for (const [caseName, value] of rejectedByTheDocument) {
      await landline.fill(value);
      const held = await landline.inputValue();
      const error = await fieldError(page, 'orgLandline');
      if (held === value && error === '') accepted.push(caseName);
    }

    // Asserted as a whole so the failure message, if the product is ever fixed, names exactly which
    // rules started being enforced.
    expect(accepted, 'orgLandline has no validator: every one of these is accepted verbatim with no error')
      .toEqual(rejectedByTheDocument.map(([name]) => name));

    expect(await fieldError(page, 'orgLandline')).toBe('');
  });

  test('CONTRADICTION: the landline field has no maxlength, so it does not stop at 10 digits', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    const landline = page.locator('input[name="orgLandline"]');

    expect(await landline.getAttribute('maxlength'),
      'cases 17.16/17.17 presuppose a 10-digit cap; the input carries no maxlength').toBeNull();

    await landline.fill('9'.repeat(40));
    await expect(landline, 'forty digits are retained in full').toHaveValue('9'.repeat(40));
  });

  test('rubbish in the landline field is never validated on Next — no landline error is ever raised', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('partnership');
    await page.locator('input[name="organizationName"]').fill('Test Partners LLP');
    await page.locator('input[name="orgLandline"]').fill('not-a-phone-number-at-all');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    // Cannot assert "it advances" — the organisation-category defect above blocks step 1 for every
    // organisation category. What IS provable, and is the point of block 17, is that the landline
    // itself contributes no error however invalid its contents.
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'an obviously invalid landline raises no error of its own')
      .not.toMatch(/landline|number|digit/i);
    expect(await fieldError(page, 'orgLandline')).toBe('');
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 18 — Email Id
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B18 — the Email Id field is validated on Next with a format regex, but has no length cap', () => {

  test('the email field is present for every complainant category', async ({ page }) => {
    await gotoComplainantDetails(page);

    const categories = await page.locator('select[name="complainantCategory"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    for (const category of categories) {
      await page.locator('select[name="complainantCategory"]').selectOption(category);
      await expect(page.locator('input[name="email"]'), `email must be present for ${category}`)
        .toBeVisible();
    }
  });

  test('the email field accepts a valid address, alphanumerics, and the permitted special characters', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const email = page.locator('input[name="email"]');

    // Covers pack cases 18.5 (alphanumeric), 18.6 (valid email), 18.7 (special chars), 18.8 (domain).
    const valid = [
      'citizen123@example.com',
      'first.last@example.co.in',
      'user+tag_name-x@sub.domain.example.org',
    ];
    for (const value of valid) {
      await email.fill(value);
      await expect(email).toHaveValue(value);
    }

    // Prove the validator genuinely passes these, not merely that the input retained them.
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await email.fill('citizen123@example.com');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).not.toMatch(/email/i);
  });

  test('the email field may be left blank — it is optional', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="email"]').fill('');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });

  /**
   * Cases 18.9 and 18.12: a plainly invalid address and interior spaces. Both reach the Angular
   * validator and are rejected by /^[^\s@]+@[^\s@]+\.[^\s@]+$/ — though with a different message than
   * documented, and only on Next. See the CONTRADICTION tests below.
   *
   * Case 18.13 (emoji) is NOT here: the regex accepts it. See the dedicated test further down.
   *
   * Cases 18.10 and 18.11 (only-spaces, leading/trailing spaces) are handled separately: they never
   * reach the validator at all, because `type="email"` makes the BROWSER strip whitespace before
   * Angular sees a value. Verified in a real browser: "   " is held as "", and
   * "  citizen@example.com  " is held as "citizen@example.com". Asserting them here would have been a
   * false negative attributed to the wrong layer.
   */
  test('a plainly invalid address and interior spaces are rejected on Next', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    // Everything else step 1 requires, so the only possible error is the email one.
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    const invalid: Array<[string, string]> = [
      ['plainly invalid (18.9)', 'not-an-email'],
      ['no TLD (18.9)', 'citizen@example'],
      ['interior spaces (18.12)', 'citi zen@example.com'],
    ];

    for (const [caseName, value] of invalid) {
      await page.locator('input[name="email"]').fill(value);
      // Guard the premise: if the browser sanitised the value, the validator is not what we are testing.
      expect(await page.locator('input[name="email"]').inputValue(),
        `${caseName} must survive browser sanitisation to reach the validator`).toBe(value);

      await page.locator('button.btn-next').click();
      await page.waitForTimeout(250);

      // Still on step 1 — the email error held the step.
      await expect(page.locator('select[name="complainantCategory"]'), `${caseName} should hold step 1`)
        .toBeVisible();
      expect(await fieldError(page, 'email'), `${caseName} must report an email error`).toBeTruthy();
    }
  });

  /**
   * Cases 18.10 / 18.11. The pack expects an error message for whitespace-only and padded addresses.
   * No message is possible, because no invalid value ever exists: the input is `type="email"`, so the
   * browser's own value sanitisation strips leading/trailing whitespace before Angular reads it.
   *
   * The OUTCOME the pack wants is met for 18.11 — a padded valid address is accepted, correctly, as
   * the trimmed address. For 18.10 whitespace-only becomes empty, and email is optional, so it
   * advances with no email rather than erroring. Recorded as a contradiction in the mechanism, not a
   * defect: this is the browser behaving well.
   */
  test('CONTRADICTION: whitespace in the email field is stripped by the browser, so no validator ever sees it', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const email = page.locator('input[name="email"]');

    await email.fill('   ');
    expect(await email.inputValue(), 'only-spaces (18.10) collapses to empty, so email is simply absent').toBe('');

    await email.fill('  citizen@example.com  ');
    expect(await email.inputValue(), 'leading/trailing spaces (18.11) are trimmed, not rejected')
      .toBe('citizen@example.com');

    // And the trimmed address then passes validation rather than raising the documented error.
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).not.toMatch(/email/i);
  });

/**
   * Case 18.13 expects emoji to be rejected. They are ACCEPTED. The validator regex is
   * /^[^\s@]+@[^\s@]+\.[^\s@]+$/ — "any run of characters that is not whitespace and not @". An emoji
   * is neither, so "😀@example.com" is a structurally valid address as far as this check is concerned,
   * and the citizen advances with an address that can never receive mail.
   *
   * Worth being precise about for the BA: the regex is a shape check, not a character-set check. The
   * same hole admits any non-ASCII codepoint. A stricter pattern (or server-side deliverability
   * validation) is the fix; this test pins the current hole so closing it is a visible change.
   */
  test('CONTRADICTION: an emoji address passes email validation and advances the wizard', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const email = page.locator('input[name="email"]');

    await email.fill('\u{1F600}@example.com');
    expect(await email.inputValue(), 'the browser does not strip emoji from a type=email field')
      .toBe('\u{1F600}@example.com');

    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();

    // It advances — case 18.13's expectation is not met.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'the emoji address was accepted and the wizard advanced to step 2').toBeVisible({ timeout: 20000 });
  });

  test('CONTRADICTION: the rejection message is "Invalid email format", not the documented "Enter a valid email address."', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await page.locator('input[name="email"]').fill('not-an-email');

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(250);

    // Pinned exactly, so that localising or rewording this string is a deliberate, visible change.
    expect(await fieldError(page, 'email')).toBe('Invalid email format');
  });

  test('CONTRADICTION: nothing is reported until Next is pressed — entering an invalid address shows no error on its own', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');

    await page.locator('input[name="email"]').fill('not-an-email');
    await page.locator('input[name="firstName"]').click();   // blur the email field
    await page.waitForTimeout(250);

    // The pack's blur-time expectation ("2. Observe the screen") is not met.
    expect(await fieldError(page, 'email'),
      'there is no blur-time or keystroke-time email validation, only on Next').toBe('');
  });

  test('CONTRADICTION: there is no 64-character email limit — a 100-character local part is accepted and advances', async ({ page }) => {
    await gotoComplainantDetails(page);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');
    const email = page.locator('input[name="email"]');

    expect(await email.getAttribute('maxlength'),
      'cases 18.14/18.15 presuppose a 64-character cap; the input carries no maxlength').toBeNull();

    const longButValid = 'a'.repeat(100) + '@example.com';
    await email.fill(longButValid);
    await expect(email, 'the full 112-character address is retained').toHaveValue(longButValid);

    await page.locator('input[name="firstName"]').fill('Testy');
    await page.locator('input[name="pincode"]').fill('411001');
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');

    await page.locator('button.btn-next').click();
    // It advances: the over-length address is not merely un-truncated, it is accepted.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });
});
