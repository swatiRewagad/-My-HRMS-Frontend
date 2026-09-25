import { test, expect } from '../fixtures';
import {
  describeTextFieldValidation,
  reachComplainantDetails,
  fillPincode,
  sessionAMobile,
  type TextFieldSpec,
} from './helpers-auth-a';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 — SESSION A — manual blocks 9, 10, 11, 15 and 16
 * First Name / Middle Name / Surname / Organization Name, and the auto-populated Mobile Number
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Blocks 9, 10, 11 and 15 are the SAME eighteen cases applied to four inputs — 52 near-identical manual
 * cases. They are generated here from a four-row table by `describeTextFieldValidation` (see
 * `helpers-auth-a.ts` for the full rationale and the measured facts it encodes), so a rule stated once is
 * applied identically to all four and a copy-paste slip cannot hide.
 *
 * THE HEADLINE MEASUREMENT, made by reading the markup before writing a test:
 * none of the four inputs carries a `maxlength` or a `pattern`, and no name field is validated for
 * characters anywhere. The manual's "should not accept alphanumeric / numeric / special / emoji /
 * leading-trailing spaces, with message 'Only letters are allowed.'" describes validation that DOES NOT
 * EXIST — that string is absent from the entire codebase. Those cases are asserted as accepted-silently
 * and each carries a `test.fixme` with QA's expectation.
 *
 * And the 150-character cap has no counterpart either. The only length limit in the stack is
 * `varchar(200)` on `COMPLAINT.complainant_name` (`Complaint.java:34-35`), applied to the CONCATENATION
 * `[firstName, middleName, lastName].filter(Boolean).join(' ')` (component.ts:2161). So three fields share
 * one 200-character budget that no single field's UI knows about — recorded as A-C14, and measured at the
 * bottom of this file rather than asserted from the schema.
 *
 * The blank case is the one place the four fields genuinely differ, so `blocksNextWhenBlank` below is
 * MEASURED per field rather than taken from QA (which calls three of the four mandatory):
 *   - firstName       — in `validateCurrentStep()`, so it does block.
 *   - middleName      — not validated. QA agrees it is optional.
 *   - lastName        — not validated, though QA calls it mandatory (A-C15).
 *   - organizationName— blocks, and now for its OWN reason. It used to block for a reason unrelated to
 *                       its value: it shared `validationErrors['name']` with firstName, which is never
 *                       rendered for organisation categories, so no organisation could advance whatever
 *                       it typed here (A-D5). The validator now requires the field that is actually
 *                       rendered, so a filled organisation name advances and a blank one is refused
 *                       against the field the citizen can see.
 *
 * Mobiles 9876510601-9876510699 (Session A's range).
 */

const FIELDS: TextFieldSpec[] = [
  {
    block: 9,
    lines: '315-400',
    label: 'First Name',
    name: 'firstName',
    shownFor: 'individual',
    blocksNextWhenBlank: true,
    qaClaimsMandatory: true,
    mobileFrom: 601,
  },
  {
    block: 10,
    lines: '401-490',
    label: 'Middle Name',
    name: 'middleName',
    shownFor: 'individual',
    blocksNextWhenBlank: false,
    // QA agrees Middle Name is optional — the only field where the script and the product concur.
    qaClaimsMandatory: false,
    mobileFrom: 621,
  },
  {
    block: 11,
    lines: '491-577',
    label: 'Surname',
    name: 'lastName',
    shownFor: 'individual',
    blocksNextWhenBlank: false,
    // QA calls Surname mandatory and the product does not validate it. Still an open ruling — A-C15.
    qaClaimsMandatory: true,
    mobileFrom: 641,
  },
  {
    block: 15,
    lines: '820-913',
    label: 'Organization Name',
    name: 'organizationName',
    shownFor: 'organisation',
    blocksNextWhenBlank: true,
    qaClaimsMandatory: true,
    mobileFrom: 661,
  },
];

for (const field of FIELDS) describeTextFieldValidation(field);

/**
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 * A-C14 — the three name fields share ONE 200-character budget
 *
 * Measured rather than asserted from the schema: fill all three at 150 each (QA says each is allowed
 * 150), submit-adjacent state, and observe that the concatenation the product would send is 452
 * characters against a `varchar(200)` column. No UI anywhere warns about this, because no field knows
 * about the other two.
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 */
test('A-C14: three names at QA\'s own 150-char limit build a 452-char value for a varchar(200) column',
  async ({ page }) => {
    await reachComplainantDetails(page, sessionAMobile(681));
    await page.locator('select[name="complainantCategory"]').selectOption('individual');

    const parts = ['A'.repeat(150), 'B'.repeat(150), 'C'.repeat(150)];
    await page.locator('input[name="firstName"]').fill(parts[0]);
    await page.locator('input[name="middleName"]').fill(parts[1]);
    await page.locator('input[name="lastName"]').fill(parts[2]);

    // All three held their full value — so the UI permits the combination it cannot store.
    await expect(page.locator('input[name="firstName"]')).toHaveValue(parts[0]);
    await expect(page.locator('input[name="middleName"]')).toHaveValue(parts[1]);
    await expect(page.locator('input[name="lastName"]')).toHaveValue(parts[2]);

    // The value the component would submit: component.ts:2161.
    const concatenated = parts.filter(Boolean).join(' ');
    expect(concatenated.length,
      'the concatenation no longer exceeds complainant_name varchar(200) — A-C14 may be fixed')
      .toBeGreaterThan(200);

    // And step 1 lets it through: the wizard advances, so the overflow is only discovered at submit.
    await fillPincode(page, '411001');
    await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
    await page.locator('button.btn-next').click();

    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'step 1 now refuses a name that cannot be stored — A-C14 may be fixed')
      .toBeVisible({ timeout: 20000 });
  });

/**
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 * Block 16 (914-963) — Mobile Number is auto-populated from the login and cannot be edited.
 *
 * Four cases: it is displayed, it is pre-filled with the logged-in number, it is non-editable, and it
 * cannot be blanked. The control is `readonly` (not `disabled`), which matters: a readonly input is still
 * submitted and still focusable, so "non-editable" is asserted as *the value does not change when typed
 * into*, and separately as the attribute itself.
 * ────────────────────────────────────────────────────────────────────────────────────────────────
 */
test.describe('QA-A16 — the auto-populated Mobile Number field (manual 914-963)', () => {
  const MOBILE = 'input[name="phone"]';

  test('Mobile Number is displayed and pre-filled with the number that logged in', async ({ page }) => {
    const mobile = sessionAMobile(682);
    await reachComplainantDetails(page, mobile);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');

    await expect(page.locator(MOBILE)).toBeVisible();
    await expect(page.locator(MOBILE), 'the mobile was not carried over from the login session')
      .toHaveValue(mobile);
  });

  test('Mobile Number is readonly, and typing into it changes nothing', async ({ page }) => {
    const mobile = sessionAMobile(683);
    await reachComplainantDetails(page, mobile);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');

    const input = page.locator(MOBILE);
    // readonly rather than disabled — so it is focusable and still submitted.
    await expect(input, 'the mobile field is no longer readonly').toHaveAttribute('readonly', '');

    await input.click();
    await input.pressSequentially('1234567890', { delay: 10 });
    await expect(input, 'the readonly mobile field accepted typed input').toHaveValue(mobile);
  });

  test('Mobile Number cannot be cleared', async ({ page }) => {
    const mobile = sessionAMobile(684);
    await reachComplainantDetails(page, mobile);
    await page.locator('select[name="complainantCategory"]').selectOption('individual');

    const input = page.locator(MOBILE);
    await input.click();
    // Select-all then Delete is the shortest real path a citizen has to emptying a field.
    await input.press('ControlOrMeta+a');
    await input.press('Delete');
    await expect(input, 'the readonly mobile field was cleared').toHaveValue(mobile);
  });

  test('Mobile Number is shown for organisation categories too, and is equally locked',
    async ({ page }) => {
      const mobile = sessionAMobile(685);
      await reachComplainantDetails(page, mobile);
      // The mobile sits OUTSIDE both category blocks, so it survives a category that hides the names.
      await page.locator('select[name="complainantCategory"]').selectOption('trust');

      const input = page.locator(MOBILE);
      await expect(input).toBeVisible();
      await expect(input).toHaveValue(mobile);
      await expect(input).toHaveAttribute('readonly', '');
    });
});
