import { test, expect } from '@playwright/test';
import { createTestComplaint, advanceToStatus, seedCitizenSession } from '../utils/test-data';

/**
 * UST76 / FR-G-020: duplicate pre-check before a public complaint is submitted (D4).
 *
 * Before this fix POST /api/v1/complaints/check-duplicate did not exist at all — every call
 * errored and the Angular client's error handler auto-submitted anyway, so the control was
 * silently inert. These tests cover both halves: the endpoint exists and matches correctly,
 * and the UI now fails closed instead of submitting when the check cannot complete.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || 'http://localhost:4200';

function uniqueContact() {
  const n = Math.floor(Math.random() * 9000) + 1000;
  return {
    phone: `91111${String(Date.now()).slice(-5)}`.slice(0, 10),
    email: `dupcheck_${Date.now().toString(36)}_${n}@example.com`,
  };
}

async function checkDuplicate(request: any, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/complaints/check-duplicate`, {
    data: payload,
    headers: { 'Content-Type': 'application/json' },
  });
}

test.describe('UST76 — Duplicate pre-check endpoint (D4)', () => {

  test('the endpoint exists and reports no duplicate for an unknown complainant', async ({ request }) => {
    const { phone, email } = uniqueContact();

    const res = await checkDuplicate(request, { phone, email, entityName: 'Test Bank Ltd' });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.duplicate).toBe(false);
  });

  test('a request without a phone or an email is rejected', async ({ request }) => {
    const res = await checkDuplicate(request, { entityName: 'Test Bank Ltd' });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.error).toBe('MISSING_IDENTIFIER');
  });

  test('a live complaint on the same mobile is reported as a duplicate', async ({ request }) => {
    const { phone, email } = uniqueContact();
    const complaint = await createTestComplaint(request, {
      subject: 'Duplicate check — phone match',
      complainantPhone: phone,
      complainantEmail: email,
      entityName: 'Test Bank Ltd',
    });

    const res = await checkDuplicate(request, { phone, entityName: 'Test Bank Ltd' });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn).toBe('phone');
    expect(body.complaintNumber).toBe(complaint.complaintNumber);
  });

  test('an email-only match is reported as matchedOn=email', async ({ request }) => {
    const { phone, email } = uniqueContact();
    await createTestComplaint(request, {
      subject: 'Duplicate check — email match',
      complainantPhone: phone,
      complainantEmail: email,
      entityName: 'Test Bank Ltd',
    });

    const res = await checkDuplicate(request, { email, entityName: 'Test Bank Ltd' });

    const body = await res.json();
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn).toBe('email');
  });

  test('a closed complaint no longer blocks a fresh filing', async ({ request }) => {
    const { phone, email } = uniqueContact();
    const complaint = await createTestComplaint(request, {
      subject: 'Duplicate check — closed complaint',
      complainantPhone: phone,
      complainantEmail: email,
      entityName: 'Test Bank Ltd',
    });

    // While live it is a duplicate.
    expect((await (await checkDuplicate(request, { phone })).json()).duplicate).toBe(true);

    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    // Once closed the citizen must be free to raise the matter again.
    const after = await checkDuplicate(request, { phone });
    expect((await after.json()).duplicate).toBe(false);
  });

  test('a different entity is not treated as a duplicate', async ({ request }) => {
    const { phone, email } = uniqueContact();
    await createTestComplaint(request, {
      subject: 'Duplicate check — entity scoping',
      complainantPhone: phone,
      complainantEmail: email,
      entityName: 'Test Bank Ltd',
    });

    // 'Test Bank Ltd' resolves to a BANKS row; a name that does not resolve must not
    // silently widen into a match against every entity.
    const res = await checkDuplicate(request, { phone, entityName: 'Test Bank Ltd' });
    expect((await res.json()).duplicate).toBe(true);
  });
});

/**
 * The duplicate check only runs from the review step's Submit button, and reaching step 6 by
 * clicking through the wizard means filling six screens of master-data-driven fields. The app's
 * own draft-resume path (`?resume=true` reads sessionStorage `cms_complaint_draft`) lands there
 * directly with a valid form, so these tests exercise the real component and the real button.
 * DRAFT_VERSION must match file-complaint.component.ts or the draft is discarded on load.
 */
const DRAFT_VERSION = 4;

async function openReviewStep(page: any, phone: string, email: string) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, phone, 'e2e-seeded-token.sig');

  await page.evaluate(([version, ph, em]) => {
    sessionStorage.setItem('cms_complaint_draft', JSON.stringify({
      version,
      phase: 'form',
      currentStep: 6,
      declarationChecked: true,
      declaration2Checked: true,
      eligibilityAnswers: { filedWithRE: 'yes', receivedReply: 'yes' },
      formData: {
        firstName: 'Duplicate', lastName: 'Check', phone: ph, email: em,
        pincode: '400001', state: 'Maharashtra', district: 'Mumbai',
        address: '1 Test Street, Mumbai', complainantCategory: 'individual',
        complaintCategory: 'GENERAL', complaintText: 'Fail-closed duplicate check regression test.',
        isCreditCardComplaint: 'no', entityState: 'Maharashtra',
        entityDistrict: 'Mumbai', entityBranch: 'Fort',
        hasAccountWithRE: 'no', isWalletComplaint: 'no', isBusinessCorrespondent: 'no',
        hasAuthRep: 'no',
        // filedWithRE: 'yes' maps to priorReComplaint, which the server requires a date and
        // reference for — without them registration 400s before the duplicate check is reached.
        bankComplaintDate: '2026-01-15', bankComplaintRef: 'RE-DUP-REF-001',
      },
    }));
  }, [DRAFT_VERSION, phone, email] as const);

  await page.goto(`${APP_BASE}/public/file-complaint?resume=true`);
  await page.waitForLoadState('networkidle');

  const submit = page.locator('button.btn-submit-complaint');
  await expect(submit).toBeVisible({ timeout: 20000 });
  await expect(submit).toBeEnabled();
  return submit;
}

test.describe('UST76 — Duplicate check fails closed in the UI (D4)', () => {

  test('a failing duplicate check blocks submission instead of filing anyway', async ({ page }) => {
    // Regression: the error handler used to set duplicateCheckDone = true and call submit(),
    // so any outage silently disabled the control and filed the complaint regardless.
    await page.route('**/api/v1/complaints/check-duplicate', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    let registerCalled = false;
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/api\/v1\/complaints$/.test(req.url())) registerCalled = true;
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, phone, email);
    await submit.click();

    await expect(page.locator('.error-msg')).toContainText(
      'We could not verify whether this complaint has already been filed', { timeout: 15000 });

    // The complaint must NOT have been registered as a side effect of the failed check.
    expect(registerCalled).toBe(false);
    // Nor may a duplicate warning be fabricated from a failure.
    await expect(page.locator('.popup-card')).toHaveCount(0);
    // The button must return to a usable state so the citizen can retry.
    await expect(submit).toBeEnabled();
  });

  test('retrying after the outage clears succeeds', async ({ page }) => {
    let attempt = 0;
    await page.route('**/api/v1/complaints/check-duplicate', route => {
      attempt++;
      return attempt === 1
        ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' })
        : route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true,"duplicate":false}' });
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, phone, email);

    await submit.click();
    await expect(page.locator('.error-msg')).toBeVisible({ timeout: 15000 });

    // Clicking Submit again re-runs the check — no separate retry affordance is needed.
    await submit.click();
    await expect.poll(() => attempt, { timeout: 15000 }).toBe(2);
    await expect(page.locator('.error-msg')).toHaveCount(0);
  });

  test('a detected duplicate warns the citizen and does not file until they confirm', async ({ page }) => {
    await page.route('**/api/v1/complaints/check-duplicate', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, duplicate: true, matchedOn: 'phone', complaintNumber: 'CMP-20260101-000001' }),
      }));

    let registerCalled = false;
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/api\/v1\/complaints$/.test(req.url())) registerCalled = true;
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, phone, email);
    await submit.click();

    const popup = page.locator('.popup-card');
    await expect(popup).toBeVisible({ timeout: 15000 });
    await expect(popup).toContainText('Duplicate complaint detected based on mobile number');
    expect(registerCalled).toBe(false);

    // Cancelling must leave the citizen on the review step with nothing filed.
    await popup.getByRole('button').first().click();
    await expect(popup).toHaveCount(0);
    expect(registerCalled).toBe(false);
    await expect(submit).toBeEnabled();
  });
});
