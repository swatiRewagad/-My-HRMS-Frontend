import { test, expect } from '../fixtures';
import { loginCitizen, seedCitizenSession } from '../utils/test-data';

/**
 * Walks the citizen journey that starts at the "File an Appeal" button in the My Complaints table,
 * against a complaint already closed in the DB (CLOSED_COMPLAINT below).
 *
 * The route is `/public/appeal/:id`. It did not exist until this flow was wired up — the table
 * navigated to `/public/file-appeal/:id`, which fell through to `**` and redirected home.
 */
const CITIZEN_MOBILE = '9597854554';
const CLOSED_COMPLAINT = process.env.APPEAL_COMPLAINT || '';

// Each run files a real appeal, and a complaint may only have one active appeal — so a rerun
// needs a freshly closed complaint in APPEAL_COMPLAINT or the second test hits "not eligible".

test.describe('Public — File an Appeal from My Complaints', () => {
  test.beforeEach(async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    test.skip(!token, 'Citizen OTP login unavailable — cannot reach a publicAuthGuard route');
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
  });

  test('the closed row offers File an Appeal and it opens the wizard prefilled', async ({ page }) => {
    test.skip(!CLOSED_COMPLAINT, 'Set APPEAL_COMPLAINT to a closed complaint number');

    await page.goto('/public/history', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const row = page.locator('tr', { hasText: CLOSED_COMPLAINT });
    await expect(row).toBeVisible({ timeout: 15000 });
    await expect(row.locator('.status-pill')).toHaveText(/Complaint Closed/);

    await row.locator('.btn-appeal').click();

    await expect(page).toHaveURL(new RegExp(`/public/appeal/${CLOSED_COMPLAINT}`));
    // The route param only prefills the reference — the citizen must click Check Eligibility,
    // so the result must NOT already be on screen.
    await expect(page.locator('.form-input')).toHaveValue(CLOSED_COMPLAINT, { timeout: 15000 });
    await expect(page.locator('.eligibility-result')).toHaveCount(0);
  });

  test('the wizard files an appeal and reports a reference number', async ({ page }) => {
    test.skip(!CLOSED_COMPLAINT, 'Set APPEAL_COMPLAINT to a closed complaint number');

    await page.goto(`/public/appeal/${CLOSED_COMPLAINT}`, { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.form-input')).toHaveValue(CLOSED_COMPLAINT, { timeout: 15000 });
    await page.locator('.btn-primary:has-text("Check Eligibility")').click();
    await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 15000 });

    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    await page.locator('input[type="radio"]').first().check();
    await page.locator('textarea').first().fill('The closure did not address the disputed charge reversal.');

    await page.locator('.btn-primary:has-text("Next")').click();
    await page.locator('.btn-primary:has-text("Next")').click();

    await page.locator('input[type="checkbox"]').first().check();
    await page.locator('.btn-primary:has-text("Submit")').click();

    await expect(page.locator('text=/APL-/')).toBeVisible({ timeout: 20000 });
  });
});
