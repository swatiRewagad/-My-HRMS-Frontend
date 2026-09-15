import { test, expect } from '@playwright/test';
import { createTestComplaint, cleanupComplaint, advanceToStatus, loginCitizen, seedCitizenSession } from '../utils/test-data';

/**
 * UST105/107/108: Complaint Withdrawal Flow E2E Tests
 *
 * Tests the citizen-facing withdrawal flow:
 * - Searching for a complaint
 * - Validation errors (missing reason, non-withdrawable status)
 * - Successful withdrawal
 * - Bug regression: API error must show error, not success
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || 'http://localhost:4200';

// UST105: withdraw requires a verified citizen session owning the complaint,
// so API-level tests must log in as the complainant's mobile number.
const CITIZEN_PHONE = '9876543210';

test.describe('Withdrawal Flow — Public Portal', () => {

  test('search for complaint shows withdrawal form', async ({ page, request }) => {
    // Create a fresh complaint in withdrawable status
    const result = await createTestComplaint(request, {
      subject: 'E2E Withdrawal Search Test',
      complainantName: 'Withdrawal Test Citizen',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // /public/withdraw is behind publicAuthGuard — seed a session or it redirects to login.
      await page.goto(`${APP_BASE}/public`);
      await seedCitizenSession(page, CITIZEN_PHONE, 'e2e-seeded-token.sig');
      await page.goto(`${APP_BASE}/public/withdraw`);
      await page.waitForLoadState('networkidle');

      // Enter complaint ID
      const input = page.locator('input[name="complaintId"], input.form-input');
      await expect(input).toBeVisible({ timeout: 10000 });
      await input.fill(complaintNumber);

      // Click search/find
      const searchBtn = page.locator('button:has-text("Find"), button:has-text("Search"), button.btn-primary');
      await searchBtn.first().click();

      // Should transition to confirm phase with the reason form
      const reasonSection = page.locator('.radio-group, label:has-text("reason")');
      await expect(reasonSection.first()).toBeVisible({ timeout: 15000 });

      // Complaint ID should be shown on the confirm screen
      await expect(page.locator(`text=${complaintNumber}`)).toBeVisible();
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('submit without reason shows error message', async ({ page, request }) => {
    const result = await createTestComplaint(request, {
      subject: 'E2E Withdrawal No-Reason Test',
      complainantName: 'No Reason Citizen',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Navigate directly with complaint ID in the route (guarded — seed a session first)
      await page.goto(`${APP_BASE}/public`);
      await seedCitizenSession(page, CITIZEN_PHONE, 'e2e-seeded-token.sig');
      await page.goto(`${APP_BASE}/public/withdraw/${complaintNumber}`);
      await page.waitForLoadState('networkidle');

      // Either it auto-searches (because of route param) or we manually search
      const confirmPhase = page.locator('.radio-group, label:has-text("reason")');
      const searchInput = page.locator('input[name="complaintId"], input.form-input');

      // If still on search phase, fill and search
      if (await searchInput.isVisible({ timeout: 3000 }).catch(() => false)) {
        if (!(await searchInput.inputValue())) {
          await searchInput.fill(complaintNumber);
        }
        await page.locator('button:has-text("Find"), button:has-text("Search"), button.btn-primary').first().click();
        await confirmPhase.first().waitFor({ state: 'visible', timeout: 15000 });
      }

      // Do NOT select a reason — click confirm directly
      const confirmBtn = page.locator('button:has-text("Confirm"), button:has-text("Withdraw"), button.btn-submit');
      await confirmBtn.first().click();

      // Should display an error about missing reason
      const errorMsg = page.locator('.error-msg');
      await expect(errorMsg).toBeVisible({ timeout: 5000 });
      await expect(errorMsg).toContainText('reason');
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('attempt to withdraw a closed complaint shows error', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    // Create and close a complaint
    const result = await createTestComplaint(request, {
      subject: 'E2E Withdrawal Closed Test',
      complainantName: 'Closed Complaint Citizen',
      complainantPhone: CITIZEN_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to closed status
      await advanceToStatus(request, complaintNumber, 'closed');

      // Now try to withdraw via the API directly — expect a 400
      const response = await request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
        data: { reason: 'Issue resolved by the Regulated Entity', remarks: '' },
        headers: { 'Content-Type': 'application/json', 'X-Citizen-Token': token! },
      });

      expect(response.status()).toBe(400);
      const body = await response.json();
      expect(body.success).toBe(false);
      expect(body.message).toContain('cannot be withdrawn');
    } finally {
      // Already closed, no cleanup needed
    }
  });

  test('successful withdrawal sets status to withdrawn', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const result = await createTestComplaint(request, {
      subject: 'E2E Successful Withdrawal Test',
      complainantName: 'Happy Withdrawal Citizen',
      complainantPhone: CITIZEN_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Withdraw via API
      const response = await request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
        data: { reason: 'Issue resolved by the Regulated Entity', remarks: 'Bank refunded the amount.' },
        headers: { 'Content-Type': 'application/json', 'X-Citizen-Token': token! },
      });

      expect(response.status()).toBe(200);
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.data.status).toBe('WITHDRAWN');
      expect(body.data.complaintNumber).toBe(complaintNumber);

      // Verify complaint is now WITHDRAWN via GET
      const detail = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`);
      expect(detail.status()).toBe(200);
      const detailBody = await detail.json();
      expect(detailBody.data.status).toBe('WITHDRAWN');
    } finally {
      // Complaint is withdrawn, no further cleanup
    }
  });

  test('API error displays error message, not success (regression)', async ({ page }) => {
    // This tests the bug fix: when the backend returns an error,
    // the UI must show an error message rather than transitioning to the success phase.
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, CITIZEN_PHONE, 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/withdraw`);
    await page.waitForLoadState('networkidle');

    // Use a non-existent complaint ID
    const fakeId = 'NONEXISTENT-' + Date.now();
    const input = page.locator('input[name="complaintId"], input.form-input');
    await expect(input).toBeVisible({ timeout: 10000 });
    await input.fill(fakeId);

    const searchBtn = page.locator('button:has-text("Find"), button:has-text("Search"), button.btn-primary');
    await searchBtn.first().click();

    // Should show error on the search phase, NOT transition to success
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 10000 });
    await expect(errorMsg).toContainText('No complaint found');

    // The success card should NOT be visible
    const successCard = page.locator('.success-card, .success-icon');
    await expect(successCard).not.toBeVisible();
  });
});
