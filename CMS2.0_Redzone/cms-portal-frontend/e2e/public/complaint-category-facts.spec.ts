/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B28 / QA-B29 — Complaint Details: Complaint Category and Facts of the Complaint
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack blocks 28 (lines 1892-1992, 8 cases after its 3-row preamble) and 29 (1993-2078, 11 cases
 * after its 4-row preamble). Both fields are on form step 3 — not step 5; see helpers-forms-b.ts.
 *
 * ── WHAT IS ALREADY COVERED ELSEWHERE, AND SO IS NOT REPEATED ────────────────────────────────────
 * `complaint-categories.spec.ts` already carries 7 tests on the category MASTER: that `/api/categories`
 * serves the ten real rows each with a `labelKey`, that no row looks like test pollution, that every
 * labelKey resolves in all eleven locales, that a non-English locale renders non-English options, and
 * that the submitted value stays English while the label is localised. None of that is repeated here.
 *
 * This spec covers the dropdown's BEHAVIOUR on the form — mandatory-ness, single-select, the
 * sub-category reset, the fail-closed path — plus the entirety of block 29.
 *
 * ── THE PACK'S TEN CATEGORY NAMES ARE NOT THE PRODUCT'S TEN CATEGORY NAMES ───────────────────────
 * Case 28.4 lists the exact values it expects. The master serves ten rows too, but they are not the
 * same ten. Neither list is a subset of the other:
 *
 *   Pack expects, product does NOT have:          Product has, pack does NOT list:
 *     ATM/CDM/Debit card                            ATM / Debit Card          (no CDM)
 *     Mobile/Electronic Banking                     Mobile Banking / UPI      (split in two)
 *     Notes and Coins                               Internet Banking          (split in two)
 *     Opening/Operation of Deposit accounts         Deposit Accounts
 *     Para-Banking                                  Insurance
 *     Remittance and collection of instruments      Remittance / Transfer
 *     Pension related                               Pension
 *     Loans and Advances                            Loan / Advances
 *     Other products and services                   Others
 *
 * Five are the same concept under a different name (a wording question). But **"Notes and Coins" and
 * "Para-Banking" do not exist in any form**, and **"Insurance" exists in the product but appears
 * nowhere in the pack**. Those three are substantive: a citizen with a counterfeit-note or para-banking
 * grievance has no category to file it under, and routing keys off the category name.
 *
 * Asserted against the endpoint's actual response (ruling 1) with the divergence reported by name, not
 * against the pack's hardcoded list. Logged as contradiction 28-C1 / open decision OD-B5.
 *
 * ── FACTS IS maxlength-CAPPED AT 5000, SO CASE 29.11 CANNOT HOLD AS WRITTEN ──────────────────────
 * The textarea carries maxlength="5000" (html:695). Case 29.11's Expected Result wants the field to
 * "accept more than 5000 characters AND error message 'Facts of the complaint cannot exceed 5000
 * characters.' should be displayed" — which is self-contradictory twice over: it cannot both accept
 * more than 5000 and reject them, and the browser truncates at 5000 so no over-length value ever
 * reaches Angular for a validator to complain about. Truncation is asserted; the message's
 * non-existence is proven.
 *
 * ── BLOCK 29 CONTRADICTS ITSELF ON WHITESPACE, EXACTLY AS BLOCK 21 DID ──────────────────────────
 * Case 29.8 requires that leading and trailing spaces be REJECTED. The validator trims only to test
 * emptiness (`!formData['complaintText']?.trim()`), so a padded but non-empty narrative is accepted and
 * advances — the same behaviour block 21 documented for Address, and the same contradiction.
 *
 * ── STEP 3 ASKS TWO MANDATORY QUESTIONS NEITHER BLOCK MENTIONS ──────────────────────────────────
 * Blocks 28/29 (and 31/32) describe step 3 as category + facts + the account question. The validator
 * also requires `isWalletComplaint` and `isBusinessCorrespondent` (component.ts:1675, 1681), so a
 * citizen who answers everything the pack documents still cannot leave the step. Every "advances"
 * assertion below therefore goes through `answerRemainingStep3Questions`; the gap itself is logged as
 * contradiction 29-C5.
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. Mobiles come from Session B's
 * reserved 98765_2____ range via the helper.
 */

import { test, expect, Page } from '../fixtures';
import {
  gotoComplaintDetails, fieldError, clickNextAndCollectErrors, redirectAppApi, API_BASE,
} from './helpers-forms-b';

/** The ten categories case 28.4 demands, verbatim from the pack. Used to REPORT divergence, not to assert. */
const PACK_EXPECTED_CATEGORIES = [
  'ATM/CDM/Debit card',
  'Credit Card',
  'Loans and Advances',
  'Mobile/Electronic Banking',
  'Notes and Coins',
  'Opening/Operation of Deposit accounts',
  'Para-Banking',
  'Remittance and collection of instruments',
  'Pension related',
  'Other products and services',
];

/** The message the pack claims appears when the facts field is over-long. */
const DOCUMENTED_FACTS_MAX_ERROR = 'Facts of the complaint cannot exceed 5000 characters.';

/**
 * Answers the three OTHER mandatory step-3 questions, so a test about category or facts can prove the
 * step advances.
 *
 * Blocks 28/29 describe step 3 as if category + facts + the account question were all it asked. The
 * validator also demands `isWalletComplaint` and `isBusinessCorrespondent` (component.ts:1675, 1681) —
 * neither of which any manual case in this pack mentions. Without them a "should advance" case fails for
 * a reason that has nothing to do with the field under test.
 */
async function answerRemainingStep3Questions(page: Page) {
  await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
  await page.locator('input[name="isWalletComplaint"][value="no"]').check();
  await page.locator('input[name="isBusinessCorrespondent"][value="no"]').check();
}

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 28 — Complaint Category
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B28 — Complaint Category is a mandatory single-select sourced from the master', () => {

  test('the category dropdown is present on the Complaint Details step', async ({ page }) => {
    await gotoComplaintDetails(page);

    const category = page.getByTestId('complaint-category');
    await expect(category).toBeVisible();
    expect(await category.evaluate(el => el.tagName)).toBe('SELECT');

    // Nothing is pre-chosen: the citizen must pick.
    await expect(category).toHaveValue('');
  });

  test('the options are exactly the active rows the category master serves', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Read the truth from the endpoint rather than pinning ten strings (ruling 1).
    const res = await page.request.get(`${API_BASE}/api/categories`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    const rows = (Array.isArray(body) ? body : (body?.data ?? [])) as any[];
    const active = rows.filter(r => (r.status ?? 'active') === 'active').map(r => r.name);
    expect(active.length, 'the master must serve categories').toBeGreaterThan(0);

    const rendered = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(rendered.sort(), 'the dropdown is the master, with nothing added or dropped')
      .toEqual([...active].sort());
  });

  /**
   * Case 28.4 names ten categories. The product serves ten too, but not the same ten. Reported by
   * naming both directions of the divergence, so the BA sees exactly which grievances have no home.
   */
  test('CONTRADICTION: the ten categories the pack lists are not the ten the product offers', async ({ page }) => {
    await gotoComplaintDetails(page);

    const rendered = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    const missing = PACK_EXPECTED_CATEGORIES.filter(c => !rendered.includes(c));
    const extra = rendered.filter(c => !PACK_EXPECTED_CATEGORIES.includes(c));

    // The divergence is the finding. Asserting it is non-empty documents it; the messages carry the
    // detail into the report.
    expect(missing.length, `categories the pack requires but the product lacks: ${missing.join(', ')}`)
      .toBeGreaterThan(0);
    expect(extra.length, `categories the product offers but the pack never lists: ${extra.join(', ')}`)
      .toBeGreaterThan(0);

    // The three that are substantive rather than a renaming: no product category covers these concepts
    // at all, so these grievances cannot be categorised.
    for (const concept of ['Notes and Coins', 'Para-Banking']) {
      const covered = rendered.some(r => r.toLowerCase().includes(concept.split(' ')[0].toLowerCase()));
      expect(covered, `"${concept}" has no counterpart in the product's category list`).toBe(false);
    }
  });

  test('a single category can be selected and changing it replaces the previous choice', async ({ page }) => {
    await gotoComplaintDetails(page);
    const category = page.getByTestId('complaint-category');

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(values.length).toBeGreaterThan(1);

    await category.selectOption(values[0]);
    await expect(category).toHaveValue(values[0]);
    await category.selectOption(values[1]);
    await expect(category).toHaveValue(values[1]);
    expect(await category.evaluate((el: HTMLSelectElement) => el.selectedOptions.length)).toBe(1);
  });

  test('the category control is a single-select, so multiple categories cannot be chosen', async ({ page }) => {
    await gotoComplaintDetails(page);
    const category = page.getByTestId('complaint-category');

    // Cases 28.6/28.7 are guaranteed by the absence of `multiple`, which is the real proof.
    expect(await category.getAttribute('multiple')).toBeNull();
    expect(await category.evaluate((el: HTMLSelectElement) => el.multiple)).toBe(false);
    expect(await category.evaluate((el: HTMLSelectElement) => el.size)).toBeLessThanOrEqual(1);
  });

  test('CONTRADICTION: a blank category blocks the step, but the message is "Complaint category is required", not "Response is mandatory."', async ({ page }) => {
    await gotoComplaintDetails(page);
    await expect(page.getByTestId('complaint-category')).toHaveValue('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Complaint category is required');
    expect(await fieldError(page, 'complaintCategory')).toBe('Complaint category is required');

    // And the step is held.
    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible();
  });

  test('the category master fails closed: an unavailable master offers a retry, never a guessed list', async ({ page }) => {
    // Not in the pack, but it is the behaviour that protects the citizen from filing under a category
    // no master governs — and it is the one path where the dropdown legitimately does not exist.
    await page.route('**/api/categories', route => route.fulfill({ status: 500, body: '{}' }));
    await gotoComplaintDetails(page);

    await expect(page.getByTestId('categories-unavailable')).toBeVisible({ timeout: 20000 });
    await expect(page.getByTestId('categories-retry')).toBeVisible();
    await expect(page.getByTestId('complaint-category'),
      'no compiled-in fallback list is substituted').toHaveCount(0);
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 29 — Facts of the Complaint
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B29 — Facts of the Complaint is mandatory and hard-capped at 5000 characters by the browser', () => {

  test('the facts textarea is present on the Complaint Details step', async ({ page }) => {
    await gotoComplaintDetails(page);

    const facts = page.locator('textarea[name="complaintText"]');
    await expect(facts).toBeVisible();
    await expect(facts).toHaveValue('');
  });

  test('the citizen can type a narrative and it is retained', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    const narrative = 'On 12 August 2026 an ATM at Shivajinagar debited Rs 10,000 without dispensing cash.';
    await facts.fill(narrative);
    expect(await facts.inputValue()).toBe(narrative);
  });

  test('the facts field accepts alphanumerics, special characters, emoji and interior spaces', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    // Cases 29.3 (alphanumeric), 29.5 (special chars), 29.6 (emoji), 29.9 (interior spaces).
    // 29.6 expects emoji to be REJECTED — they are not; there is no character filter at all.
    const accepted = [
      'Txn ref 4455AB99 debited Rs 10000 on 12/08/2026',
      'Amount: Rs.10,000/- (ref #4455-AB/99) — no cash & no receipt!',
      'ATM \u{1F4B8} did not dispense \u{1F620}',
      'The   machine   printed   nothing',
    ];
    for (const value of accepted) {
      await facts.fill(value);
      expect(await facts.inputValue(), `${JSON.stringify(value)} must be retained verbatim`).toBe(value);
      expect(await fieldError(page, 'complaintText'),
        'no character class raises a live error').toBe('');
    }
  });

  test('CONTRADICTION: emoji in the narrative are accepted and advance the step, though case 29.6 requires rejection', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('ATM ate my card \u{1F621}\u{1F4B8}');
    await answerRemainingStep3Questions(page);

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'no emoji complaint is raised against the narrative')
      .not.toMatch(/emoji|invalid character/i);

    // The step is otherwise complete, so advancing is the proof that the emoji were accepted rather
    // than merely un-flagged.
    await expect(page.locator('textarea[name="complaintText"]'),
      'an emoji-bearing narrative advanced the wizard').toHaveCount(0, { timeout: 20000 });
  });

  test('CONTRADICTION: a blank narrative blocks the step, but the message is "Facts of the complaint is required", not "Response is mandatory."', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await expect(page.locator('textarea[name="complaintText"]')).toHaveValue('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Facts of the complaint is required');
    expect(await fieldError(page, 'complaintText')).toBe('Facts of the complaint is required');
  });

  test('a whitespace-only narrative is rejected — it is trimmed before the mandatory check', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);

    // Case 29.7. A textarea is not sanitised by the browser, so the spaces genuinely reach the model
    // and the validator's .trim() is what rejects them.
    await facts.fill('          ');
    expect(await facts.inputValue(), 'a textarea retains whitespace verbatim').toBe('          ');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Facts of the complaint is required');
  });

  test('CONTRADICTION: a padded narrative is accepted and advances, though case 29.8 requires rejection', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('   ATM did not dispense cash   ');
    await answerRemainingStep3Questions(page);

    // The validator trims only to test emptiness, so a padded but non-empty narrative passes — the
    // same behaviour block 21 recorded for Address.
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'padding raises no error of its own')
      .not.toMatch(/space|trailing|leading/i);
    await expect(page.locator('textarea[name="complaintText"]'),
      'the padded narrative advanced the wizard, so it was accepted').toHaveCount(0, { timeout: 20000 });
  });

  test('the facts field accepts exactly its declared maximum', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    // Case 29.10. The cap is read from the attribute, never hardcoded (ruling 1).
    const cap = Number(await facts.getAttribute('maxlength'));
    expect(cap, 'the facts textarea must declare a maxlength').toBeGreaterThan(0);

    await facts.fill('A'.repeat(cap));
    expect((await facts.inputValue()).length, `exactly ${cap} characters must be accepted`).toBe(cap);
    expect(await fieldError(page, 'complaintText')).toBe('');
  });

  test('CONTRADICTION: an over-long narrative is silently truncated — case 29.11 asks for acceptance AND rejection, and gets neither', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);

    const cap = Number(await facts.getAttribute('maxlength'));
    await facts.fill('A'.repeat(cap + 500));

    // The browser truncates, so the over-length value never exists in the model and no validator can
    // fire. Neither half of 29.11's Expected Result can be satisfied.
    expect((await facts.inputValue()).length, `the browser truncates at maxlength=${cap}`).toBe(cap);
    const error = await fieldError(page, 'complaintText');
    expect(error, 'the pack\'s over-length message does not exist in the product')
      .not.toBe(DOCUMENTED_FACTS_MAX_ERROR);
    expect(error, 'the truncated narrative is valid, so there is no error at all').toBe('');

    // And it advances, because 5000 valid characters is a valid narrative.
    await answerRemainingStep3Questions(page);
    await page.locator('button.btn-next').click();
    await expect(page.locator('textarea[name="complaintText"]'),
      'the truncated narrative advanced the wizard').toHaveCount(0, { timeout: 20000 });
  });

  test('CONTRADICTION: everything blocks 28-32 document is not enough to leave the step — the wallet and business-correspondent questions are also mandatory', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('ATM debited without dispensing cash.');
    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();

    // Exactly what the pack's cases ask for, and nothing more.
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | '), 'two undocumented questions hold the step')
      .toContain('Please select Yes or No');
    expect(await fieldError(page, 'isWalletComplaint')).toBe('Please select Yes or No');
    expect(await fieldError(page, 'isBusinessCorrespondent')).toBe('Please select Yes or No');
    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible();

    // Answering them releases it, which is what proves they were the only thing missing.
    await page.locator('input[name="isWalletComplaint"][value="no"]').check();
    await page.locator('input[name="isBusinessCorrespondent"][value="no"]').check();
    await page.locator('button.btn-next').click();
    await expect(page.locator('textarea[name="complaintText"]')).toHaveCount(0, { timeout: 20000 });
  });

  test('a category plus a narrative plus the account answer advances past Complaint Details', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]')
      .fill('On 12 August 2026 an ATM debited Rs 10,000 without dispensing cash.');
    await answerRemainingStep3Questions(page);

    await page.locator('button.btn-next').click();

    // Case 28.8 / 29's closing case: the citizen proceeds to the Representative Authorisation step.
    await expect(page.locator('textarea[name="complaintText"]')).toHaveCount(0, { timeout: 20000 });
  });
});
