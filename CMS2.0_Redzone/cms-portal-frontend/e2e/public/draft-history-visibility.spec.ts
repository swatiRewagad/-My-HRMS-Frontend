import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * A saved draft must still be listed when the citizen returns in a later session.
 *
 * My Complaints and Home each fired GET /complaints and GET /complaints/drafts as two independent
 * subscriptions. The complaints handler called finalizeLoad(), which REPLACES the whole signal, so
 * whenever the drafts response landed first its rows were silently discarded. Drafts is the faster
 * endpoint of the two in practice, so the draft was usually invisible — and the citizen concluded
 * their work was lost even though the row was still in COMPLAINT_DRAFTS.
 *
 * Both routes are asserted because the same table is rendered in both places.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const PHONE = '9597854554';
const DRAFT_ID = 'DRF-HIST01';

/** Delays /complaints so /drafts always wins the race — the losing order before the fix. */
async function stubListsWithSlowComplaints(page: any) {
  await page.route('**/api/v1/complaints?phone=*', async (route: any) => {
    await new Promise(r => setTimeout(r, 600));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'Complaints retrieved',
        data: [{
          complaintId: 'CMP-SUBMITTED-1',
          entityName: 'State Bank of India',
          complaintDate: '20-09-2026',
          status: 'PENDING',
          comments: ''
        }]
      })
    });
  });

  await page.route('**/api/v1/complaints/drafts?phone=*', async (route: any) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        message: 'Drafts retrieved',
        data: [{
          draftId: DRAFT_ID,
          phone: PHONE,
          entityName: 'Airtel Payments Bank Limited',
          currentStep: 6,
          phase: 'form',
          formData: {},
          eligibilityAnswers: {},
          updatedAt: '2026-09-21T10:15:00'
        }]
      })
    });
  });
}

for (const [label, path] of [['My Complaints', '/public/history'], ['Home', '/public']]) {
  test(`${label} lists a persisted draft alongside submitted complaints`, async ({ page }) => {
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, PHONE, 'e2e-seeded-token.sig');
    await stubListsWithSlowComplaints(page);

    await page.goto(`${APP_BASE}${path}`);

    // Wait for the slower call to land, so a regression cannot pass on timing alone.
    await expect(page.getByText('CMP-SUBMITTED-1').first()).toBeVisible({ timeout: 15000 });

    // The draft must survive the later response overwriting the list.
    await expect(page.getByText(DRAFT_ID).first()).toBeVisible();
  });
}
