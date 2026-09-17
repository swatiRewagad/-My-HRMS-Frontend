import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * UST82 / FR-G-008: draft save must tell the truth about whether the draft persisted (D15).
 *
 * Before this fix saveDraft() wrote sessionStorage, immediately flipped draftSaved to true and
 * stamped "Last saved at HH:MM", then fired the server call with `error: () => {}`. The citizen was
 * told the draft was saved even when the server rejected it — and because the sessionStorage copy
 * dies with the tab, closing the browser lost everything the badge had just promised was safe.
 *
 * The auto-save timer runs every 30s, so a silent failure could repeat for an entire session
 * without ever surfacing.
 */

const APP_BASE = process.env['APP_BASE_URL'] || 'http://localhost:4200';

/** Must match DRAFT_VERSION in file-complaint.component.ts or the draft is discarded on load. */
const DRAFT_VERSION = 4;

const DRAFT_ENDPOINT = '**/api/v1/complaints/drafts';

function uniquePhone() {
  return `91222${String(Date.now()).slice(-5)}`.slice(0, 10);
}

/** The badge is rendered at every save-indicator site, so more than one is on screen at once. */
function failedBadge(page: any) {
  return page.locator('.draft-failed-badge').first();
}

/** Lands on the wizard's review step via the app's own `?resume=true` draft path. */
async function openFormStep(page: any, phone: string) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, phone, 'e2e-seeded-token.sig');

  await page.evaluate(([version, ph]) => {
    sessionStorage.setItem('cms_complaint_draft', JSON.stringify({
      version,
      phase: 'form',
      currentStep: 6,
      declarationChecked: true,
      declaration2Checked: true,
      eligibilityAnswers: { filedWithRE: 'yes', receivedReply: 'yes' },
      formData: {
        firstName: 'Draft', lastName: 'Save', phone: ph, email: 'draft.save@example.com',
        pincode: '400001', state: 'Maharashtra', district: 'Mumbai',
        address: '1 Test Street, Mumbai', complainantCategory: 'individual',
        complaintCategory: 'GENERAL', complaintText: 'Draft save honesty regression test.',
        isCreditCardComplaint: 'no', entityState: 'Maharashtra',
        entityDistrict: 'Mumbai', entityBranch: 'Fort',
        hasAccountWithRE: 'no', isWalletComplaint: 'no', isBusinessCorrespondent: 'no',
        hasAuthRep: 'no',
      },
    }));
  }, [DRAFT_VERSION, phone] as const);

  await page.goto(`${APP_BASE}/public/file-complaint?resume=true`);
  await page.waitForLoadState('networkidle');

  const saveBtn = page.locator('button.btn-save-link').first();
  await expect(saveBtn).toBeVisible({ timeout: 20000 });
  return saveBtn;
}

test.describe('UST82 — Draft save reports the server outcome (D15)', () => {

  test('a rejected draft save is surfaced, not silently swallowed', async ({ page }) => {
    await page.route(DRAFT_ENDPOINT, route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    const saveBtn = await openFormStep(page, uniquePhone());
    await saveBtn.click();

    await expect(failedBadge(page)).toBeVisible({ timeout: 15000 });
    await expect(failedBadge(page)).toContainText('Could not save your draft');
  });

  test('a rejected draft save never claims the draft was saved', async ({ page }) => {
    await page.route(DRAFT_ENDPOINT, route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    const saveBtn = await openFormStep(page, uniquePhone());
    await saveBtn.click();
    await expect(failedBadge(page)).toBeVisible({ timeout: 15000 });

    // The regression: "Auto saved a few seconds ago" / "Draft Saved" used to appear regardless.
    await expect(page.locator('.auto-save-text')).toHaveCount(0);
    await expect(page.locator('.draft-badge')).toHaveCount(0);

    // "Last saved at" is a persistence claim, so it must not be stamped either.
    const savedAt = await page.evaluate(() => sessionStorage.getItem('cms_draft_saved_at'));
    expect(savedAt).toBeNull();
  });

  test('a successful draft save confirms and clears any earlier failure', async ({ page }) => {
    let attempt = 0;
    await page.route(DRAFT_ENDPOINT, route => {
      attempt++;
      return attempt === 1
        ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' })
        : route.fulfill({
            status: 201,
            contentType: 'application/json',
            body: JSON.stringify({ success: true, data: { draftId: 'DRF-E2ETEST' } }),
          });
    });

    const saveBtn = await openFormStep(page, uniquePhone());

    await saveBtn.click();
    await expect(failedBadge(page)).toBeVisible({ timeout: 15000 });

    await saveBtn.click();
    await expect(page.locator('.auto-save-text').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.draft-failed-badge')).toHaveCount(0);

    // The server's draft id is what lets Complaint History resume this draft on a new device.
    await expect.poll(() => page.evaluate(() => sessionStorage.getItem('cms_draft_id')),
      { timeout: 10000 }).toBe('DRF-E2ETEST');
    const savedAt = await page.evaluate(() => sessionStorage.getItem('cms_draft_saved_at'));
    expect(savedAt).toBeTruthy();
  });

  test('a failed save still keeps the typed answers recoverable in the current tab', async ({ page }) => {
    // The fix removes the false "saved" claim, not the local copy. If the sessionStorage write were
    // also made conditional on the server, an outage would additionally wipe the citizen's typing on
    // the next step navigation — a worse bug than the one being fixed.
    await page.route(DRAFT_ENDPOINT, route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    const saveBtn = await openFormStep(page, uniquePhone());
    await saveBtn.click();
    await expect(failedBadge(page)).toBeVisible({ timeout: 15000 });

    const local = await page.evaluate(() => sessionStorage.getItem('cms_complaint_draft'));
    expect(local).toBeTruthy();
    expect(JSON.parse(local!).formData.complaintText).toContain('Draft save honesty regression test');
  });
});
