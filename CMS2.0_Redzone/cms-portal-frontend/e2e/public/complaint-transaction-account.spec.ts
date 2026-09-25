/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B30 / QA-B31 / QA-B32 — Date of Disputed Transaction, "Account with RE?", Type of Account
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack blocks 30 (lines 2079-2151, 6 cases after its 4-row preamble), 31 (2152-2213, 5 cases
 * after its 4-row preamble) and 32 (2214-2306, 9 cases after its 4-row preamble). All three are on form
 * step 3 — not step 5; see helpers-forms-b.ts.
 *
 * ── BLOCK 31 IS REPLAYED AS BLOCK 32'S PREAMBLE, SO THE RADIO IS TESTED ONCE ──────────────────────
 * Block 32's first two cases ("selects 'No' ⇒ Type of Account is not displayed", "selects 'Yes' ⇒ it is
 * displayed") are block 31's cases 31.4/31.5 verbatim. Written once, under block 31.
 *
 * ── BLOCK 30 TESTS A FIELD THAT IS NOT ON THE SCREEN ──────────────────────────────────────────────
 * All 6 of block 30's cases describe a "Date of Disputed Transaction" field. `disputeDate` exists in the
 * component's formData (ts:187), in the tooltip map (ts:1722), among the FOUR keys the duplicate-complaint
 * check posts (ts:2111-2117) and in the submission payload as `transactionDate` (ts:2173) — but it is
 * rendered **zero times** in the template. The only three `type="date"` inputs in the whole wizard belong
 * to the eligibility questionnaire (html:153, 250, 291).
 *
 * So the citizen can never supply it, `disputeDate` is permanently '' , and duplicate detection runs on
 * three keys instead of four. The tests below prove the absence rather than skipping the block, because
 * "the field is missing" is the finding. Logged as defect D-B6; all 6 cases are void as written.
 *
 * ── BLOCK 32 EXPECTS FOUR ACCOUNT TYPES; TEN ARE OFFERED, AND NOT FROM THE SERVER ─────────────────
 * Case 32.6 lists Savings Account / Loan Account / ATM-Debit Card / Credit Card. The control offers ten.
 * Worse, `GET /api/v1/masters/account-types` **404s**, so `loadAccountTypesFromLocal()` (ts:519-527)
 * silently substitutes the bundled `assets/masters/account-types.json`. The list is therefore a build
 * artefact, not configuration: adding an account type needs a frontend release. Asserted against the
 * served asset (ruling 1) with the divergence reported by name. Defect D-B7.
 *
 * Only four of the ten reveal an account-number field (savings, loan, atm_debit, credit_card — the
 * fieldMap at ts:532-537). A citizen choosing "Fixed Deposit" is asked for no identifier at all, so the
 * six extra options carry no account number into the complaint.
 *
 * ── ANSWERING "NO" DOES NOT RESET WHAT WAS ALREADY CHOSEN ────────────────────────────────────────
 * `checked` lives on the component's `accountTypes` array; `hasAccountWithRE` only controls whether the
 * control is RENDERED. `onAccountTypeToggle` clears a per-type account number, but only on an explicit
 * untick. So a citizen who selects Savings, types an account number, then answers "No" still holds both
 * — invisibly, because the fields are gone from the screen. Defect D-B8, proven twice below.
 *
 * ── CASE 32.9 CONTRADICTS CASE 32.8 ──────────────────────────────────────────────────────────────
 * 32.8 requires that MULTIPLE values be selectable; 32.9 requires the dropdown to CLOSE after a value is
 * selected. A panel that closes on the first tick cannot accept a second. The product keeps it open
 * (the panel stops click propagation, ts/html by design) — which is what makes 32.8 achievable. 32.9 is
 * unimplementable alongside 32.8 and is asserted as the behaviour that actually serves the citizen.
 *
 * ── THE ACCOUNT-TYPE CHECKBOXES ARE NAMELESS IN THE DOM ──────────────────────────────────────────
 * The template writes `[name]="'accountType_' + at.value"`, so `input[name="accountType_savings"]`
 * looks like the obvious selector. It matches nothing: Angular's ngModel name binding is not reflected
 * as a DOM attribute, and the rendered checkboxes carry no `name` at all. Every selector here goes
 * through the visible label via `accountTypeCheckbox`. Cost me a test run; recorded so it costs nobody
 * else one.
 *
 * ── WHAT IS ALREADY COVERED ELSEWHERE ────────────────────────────────────────────────────────────
 * `account-card-numbers.spec.ts` (session C, blocks 33/34/35/37) owns the four account-NUMBER inputs:
 * their per-type reveal, their maxlength, their mandatory-ness. None of that is repeated. This spec owns
 * the radio and the multiselect itself.
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. Mobiles come from Session B's
 * reserved 98765_2____ range via the helper.
 */

import { test, expect, Page } from '../fixtures';
import {
  gotoComplaintDetails, fieldError, clickNextAndCollectErrors, redirectAppApi, APP_BASE, API_BASE,
} from './helpers-forms-b';

/** The four account types case 32.6 demands, verbatim from the pack. Used to REPORT divergence. */
const PACK_EXPECTED_ACCOUNT_TYPES = ['Savings Account', 'Loan Account', 'ATM/Debit Card', 'Credit Card'];

/** The message the pack claims appears when a future disputed-transaction date is entered. */
const DOCUMENTED_FUTURE_DATE_ERROR = 'Date cannot be greater than complaint filing date.';

/** The only account types that reveal an account-number field (component.ts:532-537). */
const TYPES_WITH_A_NUMBER_FIELD = ['savings', 'loan', 'atm_debit', 'credit_card'];

const trigger = (page: Page) => page.locator('.multiselect-trigger');
const panel = (page: Page) => page.locator('.multiselect-panel');

/** Reveals the Type of Account control by answering the account question 'yes'. */
async function showAccountTypes(page: Page) {
  await page.locator('input[name="hasAccountWithRE"][value="yes"]').check();
  await expect(trigger(page)).toBeVisible();
}

/** Opens the multiselect panel. */
async function openAccountTypePanel(page: Page) {
  await trigger(page).click();
  await expect(panel(page)).toBeVisible();
}

/** The labels the control actually offers, read from the open panel. */
async function renderedAccountTypeLabels(page: Page): Promise<string[]> {
  return (await panel(page).locator('.multiselect-item span').allTextContents()).map(t => t.trim());
}

/**
 * Locates one checkbox in the open panel by its visible label.
 *
 * The template writes `[name]="'accountType_' + at.value"`, but Angular's ngModel name binding is NOT
 * reflected as a DOM attribute — every checkbox in the rendered panel is genuinely nameless. So a
 * `input[name="accountType_savings"]` selector matches nothing and the only stable handle is the label.
 */
function accountTypeCheckbox(page: Page, label: string) {
  return panel(page).locator('.multiselect-item')
    .filter({ has: page.getByText(label, { exact: true }) })
    .locator('input[type="checkbox"]');
}

/** The account-type master's value→label pairs, as actually served to the control. */
async function servedAccountTypes(page: Page): Promise<Array<{ value: string; label: string }>> {
  const res = await page.request.get(`${APP_BASE}/assets/masters/account-types.json`);
  return ((await res.json()) as any[]).map(a => ({
    value: (a.value || a.code) as string,
    label: ((a.label || a.name) as string).trim(),
  }));
}

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 30 — Date of Disputed Transaction
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B30 — the Date of Disputed Transaction field does not exist on the Complaint Details step', () => {

  test('DEFECT D-B6: no Date of Disputed Transaction control is rendered anywhere on the step', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Case 30.5 asks for the field's presence. Proven absent three ways, because a single locator
    // could plausibly just be the wrong selector.
    await expect(page.locator('[name="disputeDate"]'),
      'disputeDate is in formData but bound to no control').toHaveCount(0);
    await expect(page.locator('.step-content input[type="date"]'),
      'the step carries no date input at all').toHaveCount(0);
    await expect(page.locator('.step-content label', { hasText: /disputed transaction/i }),
      'no label names a disputed-transaction date').toHaveCount(0);

    // The step is otherwise fully rendered, so this is absence, not a failure to load.
    await expect(page.getByTestId('complaint-category')).toBeVisible();
    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible();
  });

  test('DEFECT D-B6: the step advances with no disputed-transaction date, so cases 30.6-30.10 cannot arise', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('ATM debited without dispensing cash.');
    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
    await page.locator('input[name="isWalletComplaint"][value="no"]').check();
    await page.locator('input[name="isBusinessCorrespondent"][value="no"]').check();

    const errors = await clickNextAndCollectErrors(page);
    // Case 30.7 wants a mandatory-field error. There is no field, so there is no error — and the
    // pack's future-date message (30.10) cannot exist either.
    expect(errors.join(' | '), 'no date is demanded').not.toMatch(/date/i);
    expect(errors.join(' | ')).not.toContain(DOCUMENTED_FUTURE_DATE_ERROR);
    await expect(page.locator('textarea[name="complaintText"]'),
      'the step advanced with no transaction date supplied').toHaveCount(0, { timeout: 20000 });
  });

  test('DEFECT D-B6: the missing field is one of the four keys duplicate detection runs on', async ({ page }) => {
    // Why this matters beyond a missing input: checkDuplicate (ts:2111-2117) posts phone, email,
    // entityName, category AND disputeDate. With no control the date is permanently '', so the
    // strongest discriminator between two genuine complaints about different transactions never
    // reaches the check. Probed directly rather than by submitting, so nothing is seeded.
    await page.goto(`${APP_BASE}/public`);

    const withDate = await page.request.post(`${API_BASE}/api/v1/complaints/check-duplicate`, {
      data: {
        phone: '9876520001', email: 'b30@example.com', entityName: 'Test Bank',
        category: 'Credit Card', disputeDate: '2026-08-12',
      },
    });
    const withoutDate = await page.request.post(`${API_BASE}/api/v1/complaints/check-duplicate`, {
      data: {
        phone: '9876520001', email: 'b30@example.com', entityName: 'Test Bank',
        category: 'Credit Card', disputeDate: '',
      },
    });

    // The endpoint must accept the shape the component sends; the finding is that the product can only
    // ever send the second form.
    expect([200, 201], `check-duplicate answered ${withDate.status()}`).toContain(withDate.status());
    expect([200, 201], `check-duplicate answered ${withoutDate.status()}`).toContain(withoutDate.status());
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 31 (and block 32's duplicated first two cases) — "Do you have an account with RE?"
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B31 — the account question is a mandatory Yes/No that gates the Type of Account control', () => {

  test('the radio pair is present on the Complaint Details step and nothing is pre-selected', async ({ page }) => {
    await gotoComplaintDetails(page);

    await expect(page.locator('input[name="hasAccountWithRE"][value="yes"]')).toBeVisible();
    await expect(page.locator('input[name="hasAccountWithRE"][value="no"]')).toBeVisible();
    await expect(page.locator('input[name="hasAccountWithRE"]:checked'),
      'the citizen must answer: neither option is pre-chosen').toHaveCount(0);
  });

  test('the label names the chosen entity rather than saying "RE"', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Not in the pack, but the pack calls the field "Do you have an account with RE?" — the product
    // interpolates the actual entity name, which is what the citizen sees and what any manual tester
    // will search for in vain.
    const label = ((await page.locator('#has-account-label').textContent()) || '').trim();
    expect(label.length, 'the question must be labelled').toBeGreaterThan(0);
    expect(label, 'the literal string "RE" is not shown to the citizen').not.toMatch(/\bRE\b\?/);
  });

  test('either Yes or No can be chosen, and choosing one clears the other', async ({ page }) => {
    await gotoComplaintDetails(page);

    await page.locator('input[name="hasAccountWithRE"][value="yes"]').check();
    await expect(page.locator('input[name="hasAccountWithRE"][value="yes"]')).toBeChecked();
    await expect(page.locator('input[name="hasAccountWithRE"][value="no"]')).not.toBeChecked();

    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
    await expect(page.locator('input[name="hasAccountWithRE"][value="no"]')).toBeChecked();
    await expect(page.locator('input[name="hasAccountWithRE"][value="yes"]')).not.toBeChecked();
  });

  test('CONTRADICTION: a blank answer blocks the step, but the message is "Please select Yes or No", not "Response is mandatory."', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('ATM debited without dispensing cash.');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Please select Yes or No');
    expect(await fieldError(page, 'hasAccountWithRE')).toBe('Please select Yes or No');
    await expect(page.locator('textarea[name="complaintText"]'), 'the step is held').toBeVisible();
  });

  test('answering Yes reveals the Type of Account control; answering No hides it again', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Cases 31.8/31.9, which block 32 replays as its own first two cases.
    await expect(trigger(page), 'nothing is shown before the question is answered').toHaveCount(0);

    await page.locator('input[name="hasAccountWithRE"][value="yes"]').check();
    await expect(trigger(page)).toBeVisible();

    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
    await expect(trigger(page), 'answering No removes the control from the DOM').toHaveCount(0);
  });

  test('DEFECT D-B8: switching to No and back again does NOT discard the account type already chosen', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    const first = panel(page).locator('.multiselect-item').first();
    const label = ((await first.locator('span').textContent()) || '').trim();
    await first.locator('input[type="checkbox"]').check();
    await expect(trigger(page)).toContainText(label);

    // Answering No removes the control from the DOM — but `checked` lives on the component's
    // `accountTypes` array, which `hasAccountWithRE` does not touch. Only the per-type account NUMBER
    // is cleared, and only on an explicit untick (onAccountTypeToggle, ts:529-543).
    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
    await expect(trigger(page)).toHaveCount(0);
    await page.locator('input[name="hasAccountWithRE"][value="yes"]').check();

    await expect(trigger(page), `"${label}" survived answering No — the selection is not reset`)
      .toContainText(label);
  });

  test('DEFECT D-B8: the account number typed before answering No is retained, invisibly', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    // Savings is chosen explicitly because it is one of the four types with an identifier field, which
    // is what turns the stale flag into stale DATA rather than a cosmetic label.
    await accountTypeCheckbox(page, 'Savings Account').check();
    await trigger(page).click();
    await expect(panel(page)).toHaveCount(0);

    const accountNumber = '00112233445566';
    await page.locator('input[name="savingsAccountNumber"]').fill(accountNumber);

    // The citizen changes their mind: they have no account with this entity after all.
    await page.locator('input[name="hasAccountWithRE"][value="no"]').check();
    await expect(page.locator('input[name="savingsAccountNumber"]'),
      'the number field is hidden, which is what makes the retained value invisible').toHaveCount(0);

    // Changing back reveals that neither the type nor the number was ever cleared.
    await page.locator('input[name="hasAccountWithRE"][value="yes"]').check();
    expect(await page.locator('input[name="savingsAccountNumber"]').inputValue(),
      'the account number the citizen abandoned is still held').toBe(accountNumber);
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 32 — Type of Account with RE
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B32 — Type of Account is a multi-select checkbox panel, and the ten options are a build artefact', () => {

  test('the control opens when clicked and closes when the citizen clicks away', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);

    // Case 32.7.
    await expect(panel(page)).toHaveCount(0);
    await openAccountTypePanel(page);

    // Not a pack case, but the panel overlays the account-number inputs below it, so a panel that
    // cannot be dismissed would make those fields unreachable.
    await page.locator('.step-content').first().click({ position: { x: 5, y: 5 } });
    await expect(panel(page), 'clicking outside dismisses the panel').toHaveCount(0);
  });

  test('CONTRADICTION: the pack lists four account types; ten are offered', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    const rendered = await renderedAccountTypeLabels(page);
    expect(rendered.length, 'the control must offer options').toBeGreaterThan(0);

    // Reported by name in both directions, so the BA sees exactly what diverged (ruling 1: the truth
    // is what the product serves, not the pack's hardcoded four).
    const extra = rendered.filter(r => !PACK_EXPECTED_ACCOUNT_TYPES.some(p => looselySame(p, r)));
    expect(extra.length, `types the product offers but the pack never lists: ${extra.join(', ')}`)
      .toBeGreaterThan(0);

    // All four the pack names are present in some form, so this is an omission in the pack rather
    // than a missing product feature.
    for (const expected of PACK_EXPECTED_ACCOUNT_TYPES) {
      expect(rendered.some(r => looselySame(expected, r)),
        `"${expected}" must be offered — rendered: ${rendered.join(', ')}`).toBe(true);
    }
  });

  test('DEFECT D-B7: the account-type master 404s, so the list comes from a bundled asset instead of the server', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);
    const rendered = await renderedAccountTypeLabels(page);

    // The master the component asks for first.
    const master = await page.request.get(`${API_BASE}/api/v1/masters/account-types`);
    expect(master.status(), 'the account-type master has no controller mapping').toBe(404);

    // ...and the file it silently falls back to, which is what the citizen actually sees.
    const asset = await page.request.get(`${APP_BASE}/assets/masters/account-types.json`);
    expect(asset.status(), 'the bundled fallback is what serves the control').toBe(200);
    expect(rendered, 'the rendered options are exactly the bundled asset')
      .toEqual((await servedAccountTypes(page)).map(t => t.label));

    // The consequence: adding or retiring an account type needs a frontend build, and the 404 is
    // swallowed (`error: () => this.loadAccountTypesFromLocal()`), so nobody is told.
    await expect(page.locator('.field-error', { hasText: /account type/i }),
      'the failed master is never surfaced to the citizen').toHaveCount(0);
  });

  test('a single account type can be selected', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    // Case 32.8.
    const first = panel(page).locator('.multiselect-item').first();
    const label = ((await first.locator('span').textContent()) || '').trim();
    await first.locator('input[type="checkbox"]').check();

    await expect(trigger(page), 'the trigger reports the chosen type').toContainText(label);
    expect(await panel(page).locator('input[type="checkbox"]:checked').count()).toBe(1);
  });

  test('multiple account types can be held at once, and the panel stays open to allow it', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    // Case 32.9. This is also the proof that case 32.10 ("closes after selecting") is unimplementable:
    // a panel that closed on the first tick could never accept the second.
    const items = panel(page).locator('.multiselect-item');
    const firstLabel = ((await items.nth(0).locator('span').textContent()) || '').trim();
    const secondLabel = ((await items.nth(1).locator('span').textContent()) || '').trim();

    await items.nth(0).locator('input[type="checkbox"]').check();
    await expect(panel(page), 'the panel remains open after the first tick').toBeVisible();
    await items.nth(1).locator('input[type="checkbox"]').check();

    expect(await panel(page).locator('input[type="checkbox"]:checked').count()).toBe(2);
    await expect(trigger(page)).toContainText(firstLabel);
    await expect(trigger(page)).toContainText(secondLabel);
  });

  test('CONTRADICTION: case 32.10 requires the panel to close on selection, which would make case 32.9 impossible', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    await panel(page).locator('.multiselect-item').first().locator('input[type="checkbox"]').check();

    // The observed behaviour, asserted as the one that serves the citizen. The pack's two cases cannot
    // both be satisfied; this records which way the product resolved it.
    await expect(panel(page), 'the panel deliberately stays open so a second type can be added')
      .toBeVisible();
  });

  test('an account type can be deselected, and the last one removed empties the trigger', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    const first = panel(page).locator('.multiselect-item').first();
    await first.locator('input[type="checkbox"]').check();
    await expect(trigger(page)).not.toContainText('Select Value');

    await first.locator('input[type="checkbox"]').uncheck();
    await expect(trigger(page), 'with nothing chosen the placeholder returns')
      .toContainText('Select Value');
  });

  test('CONTRADICTION: choosing no account type blocks the step, but the message is "Please select at least one account type", not "Response is Mandatory"', async ({ page }) => {
    await gotoComplaintDetails(page);

    const values = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values[0]);
    await page.locator('textarea[name="complaintText"]').fill('ATM debited without dispensing cash.');
    await showAccountTypes(page);
    await page.locator('input[name="isWalletComplaint"][value="no"]').check();
    await page.locator('input[name="isBusinessCorrespondent"][value="no"]').check();

    // Case 32.5. `fieldError` is not usable here: it locates the .form-group by the `[name=]` of its
    // input, and the account-type checkboxes only exist while the panel is open. The error is read from
    // the group that holds the trigger instead.
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Please select at least one account type');
    const group = page.locator('.form-group', { has: page.locator('.multiselect-trigger') }).first();
    expect(((await group.locator('.field-error').textContent()) || '').trim())
      .toBe('Please select at least one account type');
    await expect(page.locator('textarea[name="complaintText"]'), 'the step is held').toBeVisible();
  });

  test('DEFECT D-B7: six of the ten account types ask the citizen for no account identifier at all', async ({ page }) => {
    await gotoComplaintDetails(page);
    await showAccountTypes(page);
    await openAccountTypePanel(page);

    const served = await servedAccountTypes(page);
    const withoutNumberField = served.filter(t => !TYPES_WITH_A_NUMBER_FIELD.includes(t.value));
    expect(withoutNumberField.length,
      `types that reveal no identifier field: ${withoutNumberField.map(t => t.label).join(', ')}`)
      .toBeGreaterThan(0);

    // Pick one and prove it: the step advances with the type recorded and no identifier captured, so
    // the complaint names an account nobody can trace.
    const orphan = withoutNumberField[0];
    await accountTypeCheckbox(page, orphan.label).check();
    await trigger(page).click();
    await expect(panel(page)).toHaveCount(0);

    await expect(page.locator('input[name="savingsAccountNumber"]')).toHaveCount(0);
    await expect(page.locator('input[name="loanAccountNumber"]')).toHaveCount(0);
    await expect(page.locator('input[name="atmDebitCardNumber"]')).toHaveCount(0);
    await expect(page.locator('input[name="creditCardNumber"]')).toHaveCount(0);

    const values2 = await page.locator('[data-testid="complaint-category"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await page.getByTestId('complaint-category').selectOption(values2[0]);
    await page.locator('textarea[name="complaintText"]').fill('Fixed deposit was not renewed on maturity.');
    await page.locator('input[name="isWalletComplaint"][value="no"]').check();
    await page.locator('input[name="isBusinessCorrespondent"][value="no"]').check();
    await page.locator('button.btn-next').click();

    await expect(page.locator('textarea[name="complaintText"]'),
      `"${orphan.label}" advanced with no account identifier captured`).toHaveCount(0, { timeout: 20000 });
  });
});

/**
 * The pack writes "ATM/Debit Card", the asset writes "ATM / Debit Card". Comparing on collapsed
 * punctuation keeps a spacing difference from being reported as a missing account type.
 */
function looselySame(a: string, b: string): boolean {
  const norm = (s: string) => s.toLowerCase().replace(/[^a-z]/g, '');
  return norm(a) === norm(b);
}
