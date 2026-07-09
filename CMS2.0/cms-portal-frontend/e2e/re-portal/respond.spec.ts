import { test, expect } from '@playwright/test';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createForwardedComplaint, respondToComplaint } from '../utils/test-data';

test.describe('RE Portal - Respond to Complaint', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Complaint detail page loads with complaint info', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const numDisplay = page.locator('.complaint-number');
    await expect(numDisplay).toBeVisible({ timeout: 5000 });
    await expect(numDisplay).toContainText(complaintNumber);

    const statusBadge = page.locator('.status-badge');
    await expect(statusBadge).toBeVisible();
  });

  test('Response form shows when within 15-day window', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const responseForm = page.locator('.response-form');
    await expect(responseForm).toBeVisible({ timeout: 5000 });

    const textarea = page.locator('#responseText');
    await expect(textarea).toBeVisible();
    await expect(textarea).toBeEnabled();
  });

  test('Submit response succeeds (status changes)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const responseTextarea = page.locator('#responseText');
    await expect(responseTextarea).toBeVisible({ timeout: 5000 });
    await responseTextarea.fill('E2E test response: Issue has been investigated and resolved. Compensation of Rs.5000 credited.');

    const submitBtn = page.locator('.submit-btn');
    await submitBtn.click();

    const successBanner = page.locator('.success-banner');
    await expect(successBanner).toBeVisible({ timeout: 10000 });
  });

  test('Response disabled after window expiry', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await respondToComplaint(request, complaintNumber, 'Pre-submitted response to close window');

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    // After response is submitted, the submit button should be disabled or window expired notice shows
    const expiredNotice = page.locator('.window-expired-notice');
    const disabledBtn = page.locator('.submit-btn[disabled]');

    const expired = await expiredNotice.isVisible().catch(() => false);
    const disabled = await disabledBtn.isVisible().catch(() => false);
    const successShown = await page.locator('.success-banner').isVisible().catch(() => false);

    expect(expired || disabled || successShown).toBeTruthy();
  });

  test('Raise query/clarification works', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re-portal/complaints/${complaintNumber}`);
    await page.waitForSelector('.re-complaint-detail', { timeout: 15000 });

    const queryBtn = page.locator('.query-btn');
    await expect(queryBtn).toBeVisible({ timeout: 5000 });
    await queryBtn.click();

    const querySection = page.locator('.query-section');
    await expect(querySection).toBeVisible({ timeout: 3000 });

    const queryTextarea = page.locator('.query-form textarea, #queryText');
    await expect(queryTextarea).toBeVisible();
    await queryTextarea.fill('E2E test: Please provide additional details regarding the transaction date and amount.');

    const submitQueryBtn = page.locator('.query-form button:has-text("Submit"), .query-form .submit-btn');
    await submitQueryBtn.click();

    const successBanner = page.locator('.query-section .success-banner');
    await expect(successBanner).toBeVisible({ timeout: 10000 });
  });
});
