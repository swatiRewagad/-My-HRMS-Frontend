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

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re/complaint/${complaintNumber}`);

    // Verify detail page loaded
    await page.waitForSelector(
      '[data-testid="complaint-detail"], .complaint-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Should display complaint number
    const numDisplay = page.locator(
      `text=${complaintNumber}, [data-testid="complaint-number"]`
    );
    await expect(numDisplay).toBeVisible({ timeout: 5000 });

    // Should display complaint subject
    const subjectEl = page.locator(
      '.complaint-subject, [data-testid="complaint-subject"], .detail-subject'
    );
    await expect(subjectEl).toBeVisible();

    // Should show status
    const statusBadge = page.locator(
      '.status-badge, [data-testid="status-badge"], .complaint-status'
    );
    await expect(statusBadge).toBeVisible();
  });

  test('Response form shows when within 15-day window', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re/complaint/${complaintNumber}`);

    await page.waitForSelector(
      '[data-testid="complaint-detail"], .complaint-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Response form/button should be visible for fresh forwarded complaints
    const responseForm = page.locator(
      '[data-testid="response-form"], .response-form, .respond-section, button:has-text("Respond"), button:has-text("Submit Response")'
    );
    await expect(responseForm).toBeVisible({ timeout: 5000 });
  });

  test('Submit response succeeds (status changes)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re/complaint/${complaintNumber}`);

    await page.waitForSelector(
      '[data-testid="complaint-detail"], .complaint-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click respond button if needed to open form
    const respondBtn = page.locator(
      'button:has-text("Respond"), button:has-text("Submit Response"), [data-testid="respond-btn"]'
    );
    if (await respondBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await respondBtn.click();
    }

    // Fill response textarea
    const responseTextarea = page.locator(
      'textarea[name="response"], textarea[placeholder*="response"], [data-testid="response-text"], .response-form textarea'
    );
    await expect(responseTextarea).toBeVisible({ timeout: 5000 });
    await responseTextarea.fill('E2E test response: Issue has been investigated and resolved. Compensation of Rs.5000 credited to customer account.');

    // Submit the response
    const submitBtn = page.locator(
      '.response-form button[type="submit"], button:has-text("Submit Response"), [data-testid="submit-response"]'
    );
    await submitBtn.click();

    // Wait for success indication
    const successMsg = page.locator(
      '.success-msg, [data-testid="response-success"], .toast-success'
    );
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Status should update
    const statusBadge = page.locator(
      '.status-badge, [data-testid="status-badge"], .complaint-status'
    );
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/responded|response_submitted|re_responded/);
  });

  test('Response disabled after window expiry', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint that has already been responded to (simulating expired window)
    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    // Submit response via API to close the window
    await respondToComplaint(request, complaintNumber, 'Pre-submitted response to close window');

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re/complaint/${complaintNumber}`);

    await page.waitForSelector(
      '[data-testid="complaint-detail"], .complaint-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Response form should not be available (already responded / window expired)
    const responseForm = page.locator(
      '[data-testid="response-form"], .response-form, .respond-section'
    );
    const respondBtn = page.locator(
      'button:has-text("Respond"):not([disabled]), button:has-text("Submit Response"):not([disabled])'
    );

    // Either the form is hidden entirely or the button is disabled
    const formVisible = await responseForm.isVisible().catch(() => false);
    const btnVisible = await respondBtn.isVisible().catch(() => false);

    expect(formVisible || btnVisible).toBeFalsy();
  });

  test('Raise query/clarification works', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createForwardedComplaint(request, 'TEST_BANK_001');
    const complaintNumber = complaint.complaintNumber;

    await loginAsReRole(page, 'RE_NODAL_OFFICER', `/re/complaint/${complaintNumber}`);

    await page.waitForSelector(
      '[data-testid="complaint-detail"], .complaint-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Find and click the query/clarification button
    const queryBtn = page.locator(
      'button:has-text("Raise Query"), button:has-text("Clarification"), button:has-text("Request Info"), [data-testid="raise-query-btn"]'
    );
    await expect(queryBtn).toBeVisible({ timeout: 5000 });
    await queryBtn.click();

    // Fill query text
    const queryTextarea = page.locator(
      'textarea[name="query"], textarea[placeholder*="query"], textarea[placeholder*="clarification"], [data-testid="query-text"]'
    );
    await expect(queryTextarea).toBeVisible({ timeout: 5000 });
    await queryTextarea.fill('E2E test: Please provide additional details regarding the transaction date and amount.');

    // Submit query
    const submitQueryBtn = page.locator(
      'button:has-text("Submit Query"), button:has-text("Send"), [data-testid="submit-query"]'
    );
    await submitQueryBtn.click();

    // Wait for success
    const successMsg = page.locator(
      '.success-msg, [data-testid="query-success"], .toast-success'
    );
    await expect(successMsg).toBeVisible({ timeout: 10000 });
  });
});
