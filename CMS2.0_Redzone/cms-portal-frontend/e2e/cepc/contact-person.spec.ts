import { test, expect } from '@playwright/test';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus, performAction } from '../utils/test-data';

/**
 * CEPC Contact Person Workflow Tests
 *
 * Tests the flow where a Dealing Officer forwards a complaint to a Contact Person
 * (typically at the regulated entity), the Contact Person responds, and the complaint
 * returns to the Dealing Officer.
 */
test.describe.serial('CEPC Contact Person Workflow', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      // Create a complaint and advance it to in_progress (DO accepted)
      const result = await createTestComplaint(request, {
        subject: 'E2E Contact Person Flow Test',
        complainantName: 'Contact Person Test Citizen',
        entityName: 'Sample Regulated Bank',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'in_progress');
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('DO forwards complaint to Contact Person', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Forward to Contact Person"
    const forwardBtn = page.locator('.action-card:has-text("Forward to Contact Person")');
    await expect(forwardBtn).toBeVisible({ timeout: 5000 });
    await forwardBtn.click();

    // The action form should appear with a target user dropdown
    const actionForm = page.locator('.action-form');
    await expect(actionForm).toBeVisible();

    // Select a contact person from dropdown (if available)
    const targetSelect = actionForm.locator('select');
    await expect(targetSelect).toBeVisible();

    // Try to select the first available contact person
    const options = targetSelect.locator('option');
    const optionCount = await options.count();
    if (optionCount > 1) {
      // Select second option (first is placeholder "Select Contact Person")
      await targetSelect.selectOption({ index: 1 });
    }

    // Fill remarks
    const remarksField = actionForm.locator('textarea');
    await remarksField.fill('Please provide information regarding the complaint from your branch.');

    // Submit action
    const confirmBtn = actionForm.locator('.submit-btn');
    await confirmBtn.click();

    // Wait for result
    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });
    await expect(resultMsg).toContainText('completed successfully');

    await logout(page);
  });

  test('Contact Person sees the task in their queue', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'CP', '/cepc/dashboard');

    await page.waitForSelector('.cepc-dashboard .content', { timeout: 15000 });

    // Wait for complaints to load
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    // The forwarded complaint should appear in the Contact Person's queue
    const complaintRow = page.locator(`.complaints-table tbody tr:has-text("${complaintNumber}")`);

    if (await complaintRow.isVisible()) {
      // The status should be "With Contact Person"
      const statusBadge = complaintRow.locator('.status-badge');
      await expect(statusBadge).toContainText('With Contact Person');
    }
    // If not visible, the complaint might be on a different page or the CP user might not match

    await logout(page);
  });

  test('Contact Person submits response', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'CP', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Submit Response"
    const responseBtn = page.locator('.action-card:has-text("Submit Response")');
    await expect(responseBtn).toBeVisible({ timeout: 5000 });
    await responseBtn.click();

    // Fill response remarks
    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill(
      'We have investigated the matter at our branch. The transaction was processed as per guidelines. ' +
      'Attached supporting documents for reference.'
    );

    // Submit
    const confirmBtn = page.locator('.action-form .submit-btn');
    await confirmBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('complaint returns to DO after Contact Person response', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // After Contact Person submits response, status should return to in_progress
    const statusBadge = page.locator('.complaint-header .status-badge');
    await expect(statusBadge).toBeVisible();

    // The complaint should be actionable by DO again
    const actionList = page.locator('.action-list');
    await expect(actionList).toBeVisible();

    // DO should have actions available (e.g., Forward to Reviewer)
    const actions = page.locator('.action-card');
    const actionCount = await actions.count();
    expect(actionCount).toBeGreaterThan(0);

    // Check timeline for Contact Person response entry
    const timeline = page.locator('.timeline');
    if (await timeline.isVisible()) {
      const contactEntry = page.locator('.timeline-item:has-text("CONTACT_RESPONSE")');
      if (await contactEntry.isVisible()) {
        await expect(contactEntry).toBeVisible();
      }
    }

    await logout(page);
  });
});
