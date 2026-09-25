/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual blocks 33, 34, 35, 37 (account and card number fields).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 2307-2570 and 2613-2698.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Four blocks of ten cases each, differing ONLY in which input they name: Savings Account Number,
 * Loan Account Number, ATM/Debit Card Number, Credit Card Number. They are driven from one table.
 * Wallet Name and Transaction/Reference Number (blocks 39, 40) share the same ten assertions but
 * are gated on a different question, so they live in wallet-bc-reference.spec.ts.
 *
 * Of the 40 manual cases, 12 are the login/navigation preamble repeated verbatim in all four
 * blocks and are already covered by otp-lifecycle.spec.ts and the eligibility specs — they are not
 * re-automated here (recorded in findings-QA-C.md §2).
 *
 * ═══ WHAT THESE TESTS PROVED, AND WHERE THE MANUAL IS WRONG ═══
 *
 * 1. THE CHARSET CASES DESCRIBE VALIDATION THAT DOES NOT EXIST. Every block asserts the field
 *    "cannot accept special characters", "cannot accept negative numbers" and "cannot accept
 *    decimal values". All four inputs are plain `[(ngModel)]` text inputs (component html:746,
 *    759, 766, 773) with no charset filter, no pattern, and no per-character validator anywhere in
 *    `validateCurrentStep` (ts:1671-1674 checks presence only). `!@#$%`, `-500` and `1.50` are all
 *    accepted and carried into the submission.
 *    These are written as `test.fixme()` asserting the REQUIRED behaviour, not the observed one:
 *    ruling 2 says do not bend the test to the code, and an account number containing `<>&` is a
 *    genuine downstream risk. If the BA rules the manual wrong, delete the fixmes — do not
 *    "fix" them by asserting that anything is accepted.
 *
 * 2. THE MAX-LENGTH ERROR MESSAGE IS UNREACHABLE. The manual demands "Account number cannot
 *    exceed 100 characters." on over-length input. The inputs use the NATIVE `maxlength="100"`,
 *    which truncates silently — the browser never delivers the 101st character, so no validator
 *    can fire and that string exists nowhere in the codebase. The tests assert the cap is real
 *    (101 chars in ⇒ 100 stored) and separately fixme the missing message.
 *
 * 3. The mandatory-field case IS honoured, with the component's own wording ("Savings account
 *    number is required"), not the manual's "Response is mandatory.".
 *
 * ═══ TRAPS PAID FOR ═══
 *  - These inputs are inside `@if (isAccountTypeSelected(...))`. The gate is the component's
 *    `accountTypes[].checked` array, NOT formData, so seeding a draft cannot reveal them; the
 *    multiselect must be opened and the checkbox clicked. `revealAccountField` does this.
 *  - The multiselect panel OVERLAYS the inputs below it, so it must be collapsed again before
 *    typing or the click lands on the panel.
 *  - Credit Card Number renders in a DIFFERENT row from the other three (html:743 vs 754), which
 *    is why the error locator walks up two levels rather than assuming a shared container.
 */

import { test, expect } from '../fixtures';
import {
  openComplaintDetails, sessionCMobile, fieldInput, fieldError, revealAccountField,
  hideAccountField, type TextFieldSpec,
} from './helpers-submission-c';
import { deleteOtpAttempts } from '../utils/test-data';

const FIELDS: TextFieldSpec[] = [
  {
    block: 'C33', label: 'Savings Account Number', field: 'savingsAccountNumber',
    claimedMaxLength: 100, accountType: 'savings',
    reveal: { hasAccountWithRE: 'yes' },
    requiredError: 'Savings account number is required',
  },
  {
    block: 'C34', label: 'Loan Account Number', field: 'loanAccountNumber',
    claimedMaxLength: 100, accountType: 'loan',
    reveal: { hasAccountWithRE: 'yes' },
    requiredError: 'Loan account number is required',
  },
  {
    block: 'C35', label: 'ATM/Debit Card Number', field: 'atmDebitCardNumber',
    claimedMaxLength: 100, accountType: 'atm_debit',
    reveal: { hasAccountWithRE: 'yes' },
    requiredError: 'ATM/Debit card number is required',
  },
  {
    block: 'C37', label: 'Credit Card Number', field: 'creditCardNumber',
    claimedMaxLength: 100, accountType: 'credit_card',
    reveal: { hasAccountWithRE: 'yes' },
    requiredError: 'Credit card number is required',
  },
];

const mobiles: string[] = [];

function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

test.afterAll(async () => {
  // OTP attempt rows key on the mobile and are shared global state across all three sessions.
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort — never fail cleanup */ }
  }
});

for (const spec of FIELDS) {

  test.describe(`QA-${spec.block} — ${spec.label} appears only for its own account type and caps its length`, () => {

    test(`the field is shown once ${spec.accountType} is selected as a type of RE account`, async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), spec.reveal);

      // Before selecting the account type the field must not exist at all.
      await expect(fieldInput(page, spec.field)).toHaveCount(0);

      await revealAccountField(page, spec.accountType!);
      await expect(fieldInput(page, spec.field)).toBeVisible();
    });

    test('the field is not shown when a different account type is selected', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), spec.reveal);

      // Pick any account type that is NOT this field's own.
      const other = FIELDS.find(f => f.accountType !== spec.accountType)!;
      await revealAccountField(page, other.accountType!);

      await expect(fieldInput(page, other.field)).toBeVisible();
      await expect(fieldInput(page, spec.field)).toHaveCount(0);
    });

    test('the field is not shown at all when the citizen has no account with the entity', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), { hasAccountWithRE: 'no' });
      await expect(fieldInput(page, spec.field)).toHaveCount(0);
    });

    test('the citizen can type an alphanumeric value and it is retained', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      const input = fieldInput(page, spec.field);
      await input.fill('AB1234567890');
      await expect(input).toHaveValue('AB1234567890');
    });

    test(`the field stops accepting characters at ${spec.claimedMaxLength}`, async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      const input = fieldInput(page, spec.field);
      const atLimit = 'A'.repeat(spec.claimedMaxLength);
      await input.fill(atLimit);
      await expect(input).toHaveValue(atLimit);

      // One over. The native maxlength truncates rather than rejecting, so the assertion is on the
      // resulting length, not on an error appearing.
      await input.fill('B'.repeat(spec.claimedMaxLength + 1));
      const value = await input.inputValue();
      expect(value.length).toBe(spec.claimedMaxLength);
    });

    test('leaving the field blank blocks the step with a mandatory-field error', async ({ page }) => {
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      await fieldInput(page, spec.field).fill('');
      await page.locator('button', { hasText: 'Next' }).first().click();

      // The step must not advance, and the field must say why.
      await expect(fieldError(page, spec.field)).toContainText(spec.requiredError!);
      await expect(fieldInput(page, spec.field)).toBeVisible();
    });

    test('a value of only spaces is rejected as blank rather than accepted as a value', async ({ page }) => {
      // `validateCurrentStep` uses `?.trim()` (ts:1671-1674), so whitespace must not satisfy the
      // mandatory check. This case is not in the manual; it is the obvious hole beside its blank case.
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      await fieldInput(page, spec.field).fill('     ');
      await page.locator('button', { hasText: 'Next' }).first().click();

      await expect(fieldError(page, spec.field)).toContainText(spec.requiredError!);
    });

    test.fixme(`special characters are rejected in the ${spec.label}`, async ({ page }) => {
      // MANUAL SAYS: "should not accept special characters".
      // OBSERVED: accepted verbatim — no charset filter exists on this input.
      // Asserting the REQUIRED behaviour per ruling 2. See findings-QA-C.md D-C-05.
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      const input = fieldInput(page, spec.field);
      await input.fill('AB!@#$%^&*()');
      await expect(input).not.toHaveValue('AB!@#$%^&*()');
    });

    test.fixme(`a negative number is rejected in the ${spec.label}`, async ({ page }) => {
      // MANUAL SAYS: "cannot accept negative numbers". OBSERVED: "-500" is accepted and submitted.
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      const input = fieldInput(page, spec.field);
      await input.fill('-500');
      await expect(input).not.toHaveValue('-500');
    });

    test.fixme(`a decimal value is rejected in the ${spec.label}`, async ({ page }) => {
      // MANUAL SAYS: "cannot accept decimal values". OBSERVED: "1.50" is accepted and submitted.
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      const input = fieldInput(page, spec.field);
      await input.fill('1.50');
      await expect(input).not.toHaveValue('1.50');
    });

    test.fixme(`exceeding ${spec.claimedMaxLength} characters shows the over-length error message`, async ({ page }) => {
      // MANUAL SAYS: error "Account number cannot exceed 100 characters." is displayed.
      // OBSERVED: the native maxlength truncates silently; that string exists nowhere in the
      // codebase, so the message is unreachable by construction. See findings-QA-C.md D-C-03.
      await openComplaintDetails(page, nextMobile(), spec.reveal);
      await revealAccountField(page, spec.accountType!);

      await fieldInput(page, spec.field).fill('C'.repeat(spec.claimedMaxLength + 5));
      await expect(fieldError(page, spec.field))
        .toContainText(`cannot exceed ${spec.claimedMaxLength} characters`);
    });
  });
}

test.describe('QA-C33/34/35/37 — several account types can be held at once, each with its own number', () => {

  test('selecting savings and loan together shows both numbers and validates them independently', async ({ page }) => {
    // The manual treats each account type in isolation, which never exercises the multiselect as a
    // MULTI-select. The four fields share one `accountTypes` array and one validationErrors map, so
    // this is where a cross-field bug would live.
    await openComplaintDetails(page, nextMobile(), { hasAccountWithRE: 'yes' });

    await revealAccountField(page, 'savings');
    await revealAccountField(page, 'loan');

    await expect(fieldInput(page, 'savingsAccountNumber')).toBeVisible();
    await expect(fieldInput(page, 'loanAccountNumber')).toBeVisible();

    // Fill only one; the other must be the one that complains.
    await fieldInput(page, 'savingsAccountNumber').fill('SAV123456');
    await page.locator('button', { hasText: 'Next' }).first().click();

    await expect(fieldError(page, 'loanAccountNumber')).toContainText('Loan account number is required');
    await expect(fieldError(page, 'savingsAccountNumber')).toHaveCount(0);
  });

  test('deselecting an account type removes its number field from the form', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { hasAccountWithRE: 'yes' });

    await revealAccountField(page, 'savings');
    await fieldInput(page, 'savingsAccountNumber').fill('SAV999888');

    // Untick it again. A field that stays behind would keep a stale account number in the payload;
    // `onAccountTypeToggle` (ts:529-546) exists specifically to clear it, so this proves that too.
    await hideAccountField(page, 'savings');
    await expect(fieldInput(page, 'savingsAccountNumber')).toHaveCount(0);

    // Re-selecting must offer an EMPTY field, not the number the citizen just withdrew.
    await revealAccountField(page, 'savings');
    await expect(fieldInput(page, 'savingsAccountNumber')).toHaveValue('');
  });

  test('no account number is required when the citizen selects no account type', async ({ page }) => {
    await openComplaintDetails(page, nextMobile(), { hasAccountWithRE: 'yes' });

    await page.locator('button', { hasText: 'Next' }).first().click();

    // With 'yes' answered but nothing ticked, the multiselect itself is the error — not the numbers.
    await expect(page.locator('.field-error', { hasText: 'at least one account type' })).toBeVisible();
  });
});
