import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * UST81 / FR-G-005: the authorised representative block must be validated before the citizen can
 * advance past step 4 (D7).
 *
 * Before this fix validateCurrentStep() checked `formData['authorizeRepresentative']`, but the
 * template binds the radio to `formData['hasAuthRep']`. Nothing ever wrote the former, so the whole
 * step-4 branch was dead code: a citizen could tick "Yes, I am filing through a representative",
 * leave every representative field blank, and still reach the declaration step.
 *
 * The representative makes submissions to the Ombudsman on the complainant's behalf, so an
 * unidentifiable one makes the authorisation impossible to verify.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** Must match DRAFT_VERSION in file-complaint.component.ts or the draft is discarded on load. */
const DRAFT_VERSION = 4;

function uniquePhone() {
  return `91333${String(Date.now()).slice(-5)}`.slice(0, 10);
}

/** Lands on step 4 (Representative Authorisation) via the app's own `?resume=true` draft path. */
async function openRepStep(page: any, phone: string, hasAuthRep: string) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, phone, 'e2e-seeded-token.sig');

  await page.evaluate(([version, ph, rep]) => {
    sessionStorage.setItem('cms_complaint_draft', JSON.stringify({
      version,
      phase: 'form',
      currentStep: 4,
      highestStepReached: 4,
      eligibilityAnswers: { filedWithRE: 'yes', receivedReply: 'yes' },
      formData: {
        firstName: 'Rep', lastName: 'Validation', phone: ph, email: 'rep.validation@example.com',
        pincode: '400001', state: 'Maharashtra', district: 'Mumbai',
        address: '1 Test Street, Mumbai', complainantCategory: 'individual',
        complaintCategory: 'GENERAL', complaintText: 'Representative validation regression test.',
        isCreditCardComplaint: 'no', entityState: 'Maharashtra',
        entityDistrict: 'Mumbai', entityBranch: 'Fort',
        hasAccountWithRE: 'no', isWalletComplaint: 'no', isBusinessCorrespondent: 'no',
        hasAuthRep: rep,
      },
    }));
  }, [DRAFT_VERSION, phone, hasAuthRep] as const);

  await page.goto(`${APP_BASE}/public/file-complaint?resume=true`);
  await page.waitForLoadState('networkidle');

  const stepTitle = page.locator('.step-content');
  await expect(stepTitle.first()).toBeVisible({ timeout: 20000 });
  return page.locator('button.btn-next').last();
}

/** Step 4 is the only step that renders the representative radio. */
function onRepStep(page: any) {
  return page.locator('input[name="hasAuthRep"]').first();
}

test.describe('UST81 — Authorised representative details are validated (D7)', () => {

  test('an empty representative block blocks the step instead of advancing', async ({ page }) => {
    const next = await openRepStep(page, uniquePhone(), 'yes');
    await expect(onRepStep(page)).toBeVisible({ timeout: 20000 });

    await next.click();

    // The regression: step 4 used to advance with every representative field blank.
    await expect(onRepStep(page)).toBeVisible();
    await expect(page.locator('.field-error').first()).toBeVisible({ timeout: 10000 });
  });

  test('every starred representative field is reported, not just the name', async ({ page }) => {
    const next = await openRepStep(page, uniquePhone(), 'yes');
    await expect(onRepStep(page)).toBeVisible({ timeout: 20000 });

    await page.fill('input[name="repName"]', 'Adv. A. Sharma');
    await next.click();

    // Name alone was the only field the old code checked, so it must not be enough on its own.
    await expect(onRepStep(page)).toBeVisible();
    const errors = page.locator('.field-error');
    await expect(errors.first()).toBeVisible({ timeout: 10000 });
    expect(await errors.count()).toBeGreaterThan(1);
  });

  test('a malformed representative mobile number is rejected', async ({ page }) => {
    const next = await openRepStep(page, uniquePhone(), 'yes');
    await expect(onRepStep(page)).toBeVisible({ timeout: 20000 });

    await page.fill('input[name="repName"]', 'Adv. A. Sharma');
    await page.fill('input[name="repPhone"]', '12345');
    await page.fill('input[name="repPincode"]', '400001');
    await page.fill('textarea[name="repAddress"]', '2 Rep Street, Mumbai');
    await next.click();

    await expect(onRepStep(page)).toBeVisible();
    await expect(page.locator('.field-error', { hasText: 'Valid 10-digit mobile number is required' }))
      .toBeVisible({ timeout: 10000 });
  });

  test('answering No skips the representative fields and advances', async ({ page }) => {
    // The fix must not make step 4 impassable for the overwhelmingly common case of a citizen
    // filing on their own behalf.
    const next = await openRepStep(page, uniquePhone(), 'no');
    await expect(onRepStep(page)).toBeVisible({ timeout: 20000 });
    await expect(page.locator('input[name="repName"]')).toHaveCount(0);

    await next.click();

    await expect(onRepStep(page)).toHaveCount(0, { timeout: 10000 });
  });

  test('leaving the representative question unanswered blocks the step', async ({ page }) => {
    const next = await openRepStep(page, uniquePhone(), '');
    await expect(onRepStep(page)).toBeVisible({ timeout: 20000 });

    await next.click();

    await expect(onRepStep(page)).toBeVisible();
    await expect(page.locator('.field-error', { hasText: 'Please select Yes or No' }))
      .toBeVisible({ timeout: 10000 });
  });
});
