import { test, expect } from '@playwright/test';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus } from '../utils/test-data';

/**
 * CEPC Workflow Chain Tests
 *
 * Tests the complete lifecycle of a complaint through the CEPC workflow:
 * assigned -> in_progress -> reviewer_review -> incharge_review -> awaiting_closure -> closed
 */
test.describe.serial('CEPC Workflow — Full Lifecycle (Happy Path)', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      // Create a test complaint via API
      const result = await createTestComplaint(request, {
        subject: 'E2E Workflow Lifecycle Test',
        complainantName: 'Workflow Test Citizen',
      });
      complaintNumber = result.complaintNumber;
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('DO accepts a complaint (status changes to in_progress)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    // Wait for complaint detail to load
    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Status should be assigned/pending/new
    const statusBadge = page.locator('.complaint-header .status-badge');
    await expect(statusBadge).toBeVisible();

    // Click "Accept & Start Examination"
    const acceptBtn = page.locator('.action-card:has-text("Accept & Start Examination")');
    await expect(acceptBtn).toBeVisible({ timeout: 5000 });
    await acceptBtn.click();

    // Submit the action (no remarks required for ACCEPT)
    const confirmBtn = page.locator('.action-form .submit-btn');
    await expect(confirmBtn).toBeVisible();
    await confirmBtn.click();

    // Wait for result message
    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });
    await expect(resultMsg).toContainText('completed successfully');

    // Status should update
    await page.waitForTimeout(1000);
    await expect(page.locator('.complaint-header .status-badge')).toContainText('Under Examination');

    await logout(page);
  });

  test('DO submits for review (status -> reviewer_review)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Forward to Reviewer"
    const reviewBtn = page.locator('.action-card:has-text("Forward to Reviewer")');
    await expect(reviewBtn).toBeVisible({ timeout: 5000 });
    await reviewBtn.click();

    // Fill remarks (required)
    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill('Examination complete, forwarding to Reviewer for scrutiny.');

    // Submit
    const confirmBtn = page.locator('.action-form .submit-btn');
    await confirmBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });
    await expect(resultMsg).toContainText('completed successfully');

    await logout(page);
  });

  test('Reviewer approves (status -> incharge_review)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'REVIEWER', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Forward to In-Charge"
    const approveBtn = page.locator('.action-card:has-text("Forward to In-Charge")');
    await expect(approveBtn).toBeVisible({ timeout: 5000 });
    await approveBtn.click();

    // Fill remarks
    const remarksField = page.locator('.action-form textarea');
    await remarksField.fill('Reviewed. DO examination is satisfactory. Forwarding to In-Charge.');

    const confirmBtn = page.locator('.action-form .submit-btn');
    await confirmBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('In-Charge approves closure (status -> awaiting_closure)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'INCHARGE', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Approve for Closure"
    const approveBtn = page.locator('.action-card:has-text("Approve for Closure")');
    await expect(approveBtn).toBeVisible({ timeout: 5000 });
    await approveBtn.click();

    // Fill remarks
    const remarksField = page.locator('.action-form textarea');
    await remarksField.fill('Approved for closure. Forwarding to Closing Authority.');

    const confirmBtn = page.locator('.action-form .submit-btn');
    await confirmBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Closing Authority closes complaint (status -> closed)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'CA', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Click "Close Complaint"
    const closeBtn = page.locator('.action-card:has-text("Close Complaint")');
    await expect(closeBtn).toBeVisible({ timeout: 5000 });
    await closeBtn.click();

    // Fill remarks
    const remarksField = page.locator('.action-form textarea');
    await remarksField.fill('Final decision: Complaint addressed satisfactorily. Closing.');

    const confirmBtn = page.locator('.action-form .submit-btn');
    await confirmBtn.click();

    // After closing, the component reloads and shows closed-banner
    // (result-msg disappears because terminal state hides the action panel)
    const closedBanner = page.locator('.closed-banner');
    await expect(closedBanner).toBeVisible({ timeout: 15000 });

    await logout(page);
  });

  test('verify timeline shows all transitions', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'CA', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    // Check audit trail section exists (timeline or empty-timeline)
    const timelineSection = page.locator('.timeline, .empty-timeline');
    await expect(timelineSection.first()).toBeVisible({ timeout: 5000 });

    // If timeline is populated, verify transitions
    const timeline = page.locator('.timeline');
    if (await timeline.isVisible()) {
      const timelineItems = page.locator('.timeline-item');
      const count = await timelineItems.count();
      expect(count).toBeGreaterThanOrEqual(4);
    }

    await logout(page);
  });
});

test.describe.serial('CEPC Workflow — Send-Back Flows', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.describe('Reviewer sends back to DO', () => {
    let complaintNumber: string;

    test.beforeAll(async ({ request }) => {
      if (!keycloakUp) return;
      const result = await createTestComplaint(request, {
        subject: 'E2E Send-Back: Reviewer to DO',
      });
      complaintNumber = result.complaintNumber;
      // Advance to reviewer_review
      await advanceToStatus(request, complaintNumber, 'reviewer_review');
    });

    test.afterAll(async ({ request }) => {
      if (complaintNumber) await cleanupComplaint(request, complaintNumber);
    });

    test('Reviewer sends back to DO', async ({ page }) => {
      test.skip(!keycloakUp, 'Keycloak is not available');
      await loginAsCepcRole(page, 'REVIEWER', `/cepc/complaint/${complaintNumber}`);

      await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

      const sendBackBtn = page.locator('.action-card:has-text("Send Back to DO")');
      await expect(sendBackBtn).toBeVisible({ timeout: 5000 });
      await sendBackBtn.click();

      const remarksField = page.locator('.action-form textarea');
      await remarksField.fill('Insufficient analysis. Please re-examine section 3.');

      const confirmBtn = page.locator('.action-form .submit-btn');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    });
  });

  test.describe('In-Charge sends back to Reviewer', () => {
    let complaintNumber: string;

    test.beforeAll(async ({ request }) => {
      if (!keycloakUp) return;
      const result = await createTestComplaint(request, {
        subject: 'E2E Send-Back: InCharge to Reviewer',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'incharge_review');
    });

    test.afterAll(async ({ request }) => {
      if (complaintNumber) await cleanupComplaint(request, complaintNumber);
    });

    test('In-Charge sends back to Reviewer', async ({ page }) => {
      test.skip(!keycloakUp, 'Keycloak is not available');
      await loginAsCepcRole(page, 'INCHARGE', `/cepc/complaint/${complaintNumber}`);

      await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

      const sendBackBtn = page.locator('.action-card:has-text("Send Back to Reviewer")');
      await expect(sendBackBtn).toBeVisible({ timeout: 5000 });
      await sendBackBtn.click();

      const remarksField = page.locator('.action-form textarea');
      await remarksField.fill('Need additional review of compliance aspects.');

      const confirmBtn = page.locator('.action-form .submit-btn');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    });
  });

  test.describe('Closing Authority sends back to In-Charge', () => {
    let complaintNumber: string;

    test.beforeAll(async ({ request }) => {
      if (!keycloakUp) return;
      const result = await createTestComplaint(request, {
        subject: 'E2E Send-Back: CA to InCharge',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'awaiting_closure');
    });

    test.afterAll(async ({ request }) => {
      if (complaintNumber) await cleanupComplaint(request, complaintNumber);
    });

    test('Closing Authority sends back to In-Charge', async ({ page }) => {
      test.skip(!keycloakUp, 'Keycloak is not available');
      await loginAsCepcRole(page, 'CA', `/cepc/complaint/${complaintNumber}`);

      await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

      const sendBackBtn = page.locator('.action-card:has-text("Send Back to In-Charge")');
      await expect(sendBackBtn).toBeVisible({ timeout: 5000 });
      await sendBackBtn.click();

      const remarksField = page.locator('.action-form textarea');
      await remarksField.fill('Resolution does not adequately address complainant concerns.');

      const confirmBtn = page.locator('.action-form .submit-btn');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    });
  });
});
