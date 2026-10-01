/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual blocks 38, 39, 40, 41.
 * Manual source: prompt_testcase_cms_frontemd.txt lines 2699-2770 (wallet radio), 2771-2850
 * (Name of Wallet), 2851-2943 (Transaction/Reference Number), 2944-3011 (business correspondent).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Blocks 38 and 41 are the two yes/no questions on the Complaint Details screen; blocks 39 and 40
 * are the two text fields that the wallet question reveals. They are kept in one file because the
 * fields cannot be reached without the radio, and testing the radio without checking what it
 * reveals is the gap that let the findings below survive.
 *
 * ═══ THE HEADLINE DEFECT: "Name of Wallet" HAS NO LENGTH LIMIT ═══
 *
 * Manual block 39 (line 2843) asserts the field accepts max 100 characters and shows "Wallet name
 * cannot exceed 100 characters." beyond that. The input at html:799 is:
 *
 *     <input type="text" ... [(ngModel)]="formData['walletName']" name="walletName"
 *            placeholder="Enter Value">
 *
 * There is NO `maxlength`. Compare its four siblings on the same screen — creditCardNumber (746),
 * savingsAccountNumber (759), loanAccountNumber (766), atmDebitCardNumber (773) all carry
 * `maxlength="100"` — and its immediate neighbour transactionRefNumber (804) carries
 * `maxlength="150"`. `validateCurrentStep` (ts:1678) checks walletName for PRESENCE only. So
 * walletName is the single field in this group with no cap of any kind, in a form where every
 * comparable field has one. That reads as an omission rather than a decision.
 *
 * The test asserts the REQUIRED 100-character behaviour and is `fixme`'d, per ruling 2 and 6: the
 * correct product fix is one `maxlength` attribute, but `src/**` is shared with Sessions A and B
 * tonight and a template edit that fails to compile would break their `ng serve` instantly.
 * Recorded as D-C-04 in findings-QA-C.md.
 *
 * ═══ SECOND FINDING: THE MANUAL'S OWN ERROR MESSAGE IS WRONG FOR BLOCK 40 ═══
 * Manual line 2942 demands, for the TRANSACTION/REFERENCE NUMBER field, the message "Wallet name
 * cannot exceed 150 characters." That is a copy-paste error in the test case itself — it names the
 * wrong field. Flagged rather than automated.
 *
 * ═══ THIRD FINDING: BLOCKS 39 AND 40 CONTRADICT THEMSELVES ON BLANKS ═══
 * Both manual blocks state the field "can be kept blank" (lines 2840-2842, 2930-2932), yet both
 * fields are starred mandatory in the template and `validateCurrentStep` (ts:1678-1679) refuses to
 * advance without them. The code is almost certainly right — a wallet complaint with no wallet
 * named is not actionable — so these tests assert the OBSERVED mandatory behaviour and the manual
 * cases are logged as contradictions (D-C-09) rather than being written as failing fixmes. This is
 * the one place in this session where observed behaviour is preferred over the document, because
 * the document contradicts its own "field is mandatory" marking two lines earlier.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - `.radio-inline` labels WRAP their input, and the group for each question is inside its own
 *    `.form-group`. Addressing radios by `input[name=...][value=...]` is unambiguous; addressing
 *    them by visible "Yes"/"No" text is not, because three questions on this screen all say Yes/No.
 *  - The business-correspondent question (block 41) claims selecting 'Yes' reveals "additional
 *    business correspondent fields". It reveals NOTHING — see D-C-10.
 */

import { test, expect } from '../fixtures';
import { openComplaintDetails, sessionCMobile, fieldInput, fieldError } from './helpers-submission-c';
import { deleteOtpAttempts } from '../utils/test-data';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

test.afterAll(async () => {
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

function radio(page: any, name: string, value: 'yes' | 'no') {
  return page.locator(`input[name="${name}"][value="${value}"]`);
}

function groupError(page: any, name: string) {
  return page.locator(`input[name="${name}"]`).first()
    .locator('xpath=ancestor::div[contains(@class,"form-group")][1]').locator('.field-error');
}

const NEXT = { hasText: 'Next' };

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Block 38 — "Is your complaint against the wallet of the Regulated Entity?"
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C38 — the wallet question is mandatory and controls two dependent fields', () => {

  test('the question is present on the complaint details screen with both options', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isWalletComplaint: '' });

    await expect(radio(page, 'isWalletComplaint', 'yes')).toBeVisible();
    await expect(radio(page, 'isWalletComplaint', 'no')).toBeVisible();
  });

  test('either option can be selected and the choice sticks', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isWalletComplaint: '' });

    await radio(page, 'isWalletComplaint', 'yes').check();
    await expect(radio(page, 'isWalletComplaint', 'yes')).toBeChecked();
    await expect(radio(page, 'isWalletComplaint', 'no')).not.toBeChecked();

    await radio(page, 'isWalletComplaint', 'no').check();
    await expect(radio(page, 'isWalletComplaint', 'no')).toBeChecked();
    await expect(radio(page, 'isWalletComplaint', 'yes')).not.toBeChecked();
  });

  test('the step cannot be advanced while the question is unanswered', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isWalletComplaint: '' });

    await expect(radio(page, 'isWalletComplaint', 'yes')).not.toBeChecked();
    await expect(radio(page, 'isWalletComplaint', 'no')).not.toBeChecked();

    await page.locator('button', NEXT).first().click();

    await expect(groupError(page, 'isWalletComplaint')).toContainText('Please select Yes or No');
    // Still on step 3 — the wallet question itself is proof enough that we did not advance.
    await expect(radio(page, 'isWalletComplaint', 'yes')).toBeVisible();
  });

  test('answering yes reveals both the wallet name and the transaction reference fields', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isWalletComplaint: '' });

    await expect(fieldInput(page, 'walletName')).toHaveCount(0);
    await expect(fieldInput(page, 'transactionRefNumber')).toHaveCount(0);

    await radio(page, 'isWalletComplaint', 'yes').check();

    await expect(fieldInput(page, 'walletName')).toBeVisible();
    await expect(fieldInput(page, 'transactionRefNumber')).toBeVisible();
  });

  test('answering no hides both dependent fields again', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });
    await expect(fieldInput(page, 'walletName')).toBeVisible();

    await radio(page, 'isWalletComplaint', 'no').check();

    await expect(fieldInput(page, 'walletName')).toHaveCount(0);
    await expect(fieldInput(page, 'transactionRefNumber')).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Blocks 39 and 40 — the two wallet text fields, driven from a table
// ═══════════════════════════════════════════════════════════════════════════════════════════════

interface WalletField {
  block: string;
  label: string;
  field: 'walletName' | 'transactionRefNumber';
  /** What the manual claims the cap is. */
  claimedMaxLength: number;
  /** Whether the running template actually enforces it via a native maxlength attribute. */
  capEnforced: boolean;
  requiredError: string;
}

const WALLET_FIELDS: WalletField[] = [
  {
    block: 'C39', label: 'Name of Wallet', field: 'walletName',
    claimedMaxLength: 100, capEnforced: false,
    requiredError: 'Name of wallet is required',
  },
  {
    block: 'C40', label: 'Transaction/Reference Number', field: 'transactionRefNumber',
    claimedMaxLength: 150, capEnforced: true,
    requiredError: 'Transaction/Reference number is required',
  },
];

for (const spec of WALLET_FIELDS) {

  test.describe(`QA-${spec.block} — ${spec.label} is revealed by the wallet question and is mandatory`, () => {

    test('the field appears only when the wallet question is answered yes', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'no' });
      await expect(fieldInput(page, spec.field)).toHaveCount(0);

      await radio(page, 'isWalletComplaint', 'yes').check();
      await expect(fieldInput(page, spec.field)).toBeVisible();
    });

    test('a typed alphanumeric value is retained', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      const input = fieldInput(page, spec.field);
      await input.fill('Paytm Wallet 4471');
      await expect(input).toHaveValue('Paytm Wallet 4471');
    });

    test(`a value of exactly ${spec.claimedMaxLength} characters is accepted`, async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      const atLimit = 'W'.repeat(spec.claimedMaxLength);
      const input = fieldInput(page, spec.field);
      await input.fill(atLimit);
      await expect(input).toHaveValue(atLimit);
    });

    test('the field is mandatory once the wallet question is answered yes', async ({ page }) => {
      // NOTE: the manual for BOTH these fields says they "can be kept blank" (lines 2840, 2930),
      // which contradicts the template's own `*` and ts:1678-1679. Observed behaviour asserted;
      // contradiction logged as D-C-09.
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      await fieldInput(page, spec.field).fill('');
      await page.locator('button', NEXT).first().click();

      await expect(fieldError(page, spec.field)).toContainText(spec.requiredError);
    });

    test('whitespace alone does not satisfy the mandatory check', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      await fieldInput(page, spec.field).fill('    ');
      await page.locator('button', NEXT).first().click();

      await expect(fieldError(page, spec.field)).toContainText(spec.requiredError);
    });

    if (spec.capEnforced) {
      test(`input stops at ${spec.claimedMaxLength} characters`, async ({ page }) => {
        await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

        const input = fieldInput(page, spec.field);
        await input.fill('X'.repeat(spec.claimedMaxLength + 25));
        expect((await input.inputValue()).length).toBe(spec.claimedMaxLength);
      });
    } else {
      test.fixme(`input stops at ${spec.claimedMaxLength} characters`, async ({ page }) => {
        // MANUAL SAYS (line 2843): accepts max 100 characters.
        // OBSERVED: html:799 has no maxlength and no length validator — the field is UNBOUNDED,
        // uniquely among the six comparable fields on this screen. See findings-QA-C.md D-C-04.
        await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

        const input = fieldInput(page, spec.field);
        await input.fill('X'.repeat(spec.claimedMaxLength + 25));
        expect((await input.inputValue()).length).toBe(spec.claimedMaxLength);
      });

      test('the unbounded wallet name currently accepts a value far beyond the documented limit', async ({ page }) => {
        // The companion to the fixme above: this one PASSES today and is what makes the defect
        // concrete rather than theoretical. A 500-character wallet name reaches the payload.
        // When walletName gains its maxlength this test must be deleted along with the fixme.
        await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

        const input = fieldInput(page, spec.field);
        await input.fill('X'.repeat(500));
        expect((await input.inputValue()).length).toBe(500);
      });
    }

    test.fixme(`${spec.label} rejects special characters`, async ({ page }) => {
      // MANUAL SAYS: "should not accept special characters". OBSERVED: no charset filter exists.
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      const input = fieldInput(page, spec.field);
      await input.fill('AB!@#$%^&*');
      await expect(input).not.toHaveValue('AB!@#$%^&*');
    });

    test.fixme(`${spec.label} rejects negative and decimal numbers`, async ({ page }) => {
      // MANUAL SAYS: cannot accept negative numbers / decimal values (lines 2834-2839, 2920-2929).
      // OBSERVED: accepted verbatim. For a wallet NAME this requirement is itself doubtful and is
      // flagged to the BA rather than merely implemented — see findings-QA-C.md D-C-05.
      await openComplaintDetails(page, nextMobile(), { isWalletComplaint: 'yes' });

      const input = fieldInput(page, spec.field);
      await input.fill('-12.50');
      await expect(input).not.toHaveValue('-12.50');
    });
  });
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Block 41 — "Is your complaint against a business correspondent?"
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C41 — the business correspondent question is mandatory', () => {

  test('the question is present with both options on the complaint details screen', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isBusinessCorrespondent: '' });

    await expect(radio(page, 'isBusinessCorrespondent', 'yes')).toBeVisible();
    await expect(radio(page, 'isBusinessCorrespondent', 'no')).toBeVisible();
  });

  test('either option can be selected and the choice is exclusive', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isBusinessCorrespondent: '' });

    await radio(page, 'isBusinessCorrespondent', 'yes').check();
    await expect(radio(page, 'isBusinessCorrespondent', 'yes')).toBeChecked();

    await radio(page, 'isBusinessCorrespondent', 'no').check();
    await expect(radio(page, 'isBusinessCorrespondent', 'no')).toBeChecked();
    await expect(radio(page, 'isBusinessCorrespondent', 'yes')).not.toBeChecked();
  });

  test('the step cannot be advanced while the question is unanswered', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { isBusinessCorrespondent: '' });

    await page.locator('button', NEXT).first().click();

    await expect(groupError(page, 'isBusinessCorrespondent')).toContainText('Please select Yes or No');
  });

  test.fixme('answering yes reveals additional business correspondent fields', async ({ page }) => {
    // MANUAL SAYS (lines 3002-3006): selecting 'Yes' displays "the additional business
    // correspondent fields".
    // OBSERVED: selecting Yes reveals NOTHING. The template (html:811-824) renders the question and
    // its error and stops; there is no conditional block behind it anywhere in the component, and no
    // BC-related key exists in `formData`. Either the requirement was never built or it was dropped
    // deliberately — the manual does not say which fields they would be, so this cannot be written
    // as a concrete assertion even in principle. See findings-QA-C.md D-C-10.
    await openComplaintDetails(page, nextMobile(), { isBusinessCorrespondent: '' });

    const before = await page.locator('#biz-corr-label')
      .locator('xpath=ancestor::div[contains(@class,"form-group")][1]').locator('input, select').count();

    await radio(page, 'isBusinessCorrespondent', 'yes').check();

    const after = await page.locator('#biz-corr-label')
      .locator('xpath=ancestor::div[contains(@class,"form-group")][1]').locator('input, select').count();
    expect(after).toBeGreaterThan(before);
  });

  test('answering yes does not currently reveal any further fields', async ({ page }) => {
    // The passing companion to the fixme above, so the absence is a recorded fact rather than an
    // assumption. If BC fields are later built, this test must be deleted with the fixme.
    await openComplaintDetails(page, nextMobile(), { isBusinessCorrespondent: '' });

    const group = page.locator('#biz-corr-label')
      .locator('xpath=ancestor::div[contains(@class,"form-group")][1]');
    const before = await group.locator('input').count();

    await radio(page, 'isBusinessCorrespondent', 'yes').check();

    expect(await group.locator('input').count()).toBe(before);
  });
});
