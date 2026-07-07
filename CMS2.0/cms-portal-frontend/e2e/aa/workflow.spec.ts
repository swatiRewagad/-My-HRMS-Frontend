import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  fileAppeal,
  performAppealAction,
} from '../utils/test-data';

/**
 * AA Workflow Tests (serial — tests depend on shared appeal state)
 *
 * Covers the full appeal lifecycle:
 *   filed → under_review → assigned_to_bench → hearing_scheduled → forwarded_to_authority → order_passed
 */
test.describe.serial('AA Workflow', () => {
  let keycloakUp: boolean;
  let appealNumber: string;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      // Create a closed complaint and file an appeal as test setup
      const complaint = await createTestComplaint(request, {
        subject: 'E2E AA Workflow Chain Test',
      });
      complaintNumber = complaint.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'closed');

      const appeal = await fileAppeal(request, complaintNumber);
      appealNumber = appeal.appealNumber;
    }
  });

  test('Registrar accepts appeal (status -> under_review)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Accept action
    const acceptBtn = page.locator(
      'button:has-text("Accept"), [data-testid="action-accept"], .action-card:has-text("Accept")'
    );
    await expect(acceptBtn).toBeVisible({ timeout: 5000 });
    await acceptBtn.click();

    // Confirm if needed
    const confirmBtn = page.locator(
      'button:has-text("Confirm"), [data-testid="confirm-action"]'
    );
    if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      await confirmBtn.click();
    }

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Status should update
    const statusBadge = page.locator('.status-badge, [data-testid="status-badge"]');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/under.review|accepted|in.review/);

    await logout(page);
  });

  test('Registrar rejects appeal (status -> rejected)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create separate appeal for rejection test
    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA Rejection Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Reject action
    const rejectBtn = page.locator(
      'button:has-text("Reject"), [data-testid="action-reject"], .action-card:has-text("Reject")'
    );
    await expect(rejectBtn).toBeVisible({ timeout: 5000 });
    await rejectBtn.click();

    // Fill rejection reason
    const reasonField = page.locator(
      'textarea[name="reason"], textarea[name="remarks"], [data-testid="rejection-reason"]'
    );
    if (await reasonField.isVisible({ timeout: 3000 }).catch(() => false)) {
      await reasonField.fill('E2E Test: Appeal does not meet eligibility criteria.');
    }

    // Confirm
    const confirmBtn = page.locator(
      'button:has-text("Confirm"), button:has-text("Submit"), [data-testid="confirm-action"]'
    );
    await confirmBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Status should be rejected
    const statusBadge = page.locator('.status-badge, [data-testid="status-badge"]');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/rejected|dismissed/);

    await logout(page);
  });

  test('Registrar assigns to bench (role change)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Assign to Bench action
    const assignBtn = page.locator(
      'button:has-text("Assign"), button:has-text("Assign to Bench"), [data-testid="action-assign-bench"]'
    );
    await expect(assignBtn).toBeVisible({ timeout: 5000 });
    await assignBtn.click();

    // Select bench officer if dropdown appears
    const benchSelect = page.locator(
      'select[name="benchOfficer"], [data-testid="bench-officer-select"]'
    );
    if (await benchSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      await benchSelect.selectOption({ index: 1 });
    }

    // Confirm assignment
    const confirmBtn = page.locator(
      'button:has-text("Confirm"), button:has-text("Assign"), [data-testid="confirm-action"]'
    );
    await confirmBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Bench officer schedules hearing (date set)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Schedule Hearing action
    const scheduleBtn = page.locator(
      'button:has-text("Schedule Hearing"), button:has-text("Schedule"), [data-testid="action-schedule-hearing"]'
    );
    await expect(scheduleBtn).toBeVisible({ timeout: 5000 });
    await scheduleBtn.click();

    // Fill hearing date (14 days from now)
    const hearingDate = new Date();
    hearingDate.setDate(hearingDate.getDate() + 14);
    const dateStr = hearingDate.toISOString().split('T')[0];

    const dateInput = page.locator(
      'input[type="date"], input[name="hearingDate"], [data-testid="hearing-date"]'
    );
    await expect(dateInput).toBeVisible({ timeout: 5000 });
    await dateInput.fill(dateStr);

    // Fill venue
    const venueInput = page.locator(
      'input[name="venue"], [data-testid="hearing-venue"], textarea[name="venue"]'
    );
    if (await venueInput.isVisible().catch(() => false)) {
      await venueInput.fill('E2E Test Hearing Room, Floor 3');
    }

    // Submit
    const submitBtn = page.locator(
      'button:has-text("Schedule"), button:has-text("Confirm"), [data-testid="confirm-hearing"]'
    );
    await submitBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Bench forwards to authority', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Forward to Authority action
    const forwardBtn = page.locator(
      'button:has-text("Forward to Authority"), button:has-text("Forward"), [data-testid="action-forward-authority"]'
    );
    await expect(forwardBtn).toBeVisible({ timeout: 5000 });
    await forwardBtn.click();

    // Add remarks
    const remarksField = page.locator(
      'textarea[name="remarks"], [data-testid="forward-remarks"]'
    );
    if (await remarksField.isVisible({ timeout: 3000 }).catch(() => false)) {
      await remarksField.fill('E2E Test: Hearing completed, forwarding for final order.');
    }

    // Confirm
    const confirmBtn = page.locator(
      'button:has-text("Confirm"), button:has-text("Forward"), [data-testid="confirm-action"]'
    );
    await confirmBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority passes order with UPHELD outcome', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a separate appeal for UPHELD test (so it does not conflict with MODIFIED test)
    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA UPHELD Order Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    // Advance through workflow via API
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Pass Order action
    const orderBtn = page.locator(
      'button:has-text("Pass Order"), button:has-text("Issue Order"), [data-testid="action-pass-order"]'
    );
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Select UPHELD outcome
    const outcomeSelect = page.locator(
      'select[name="outcome"], [data-testid="outcome-select"], input[value="UPHELD"]'
    );
    await expect(outcomeSelect).toBeVisible({ timeout: 5000 });
    if (await outcomeSelect.evaluate((el) => el.tagName === 'SELECT').catch(() => false)) {
      await outcomeSelect.selectOption('UPHELD');
    } else {
      await outcomeSelect.click();
    }

    // Add order remarks
    const orderRemarks = page.locator(
      'textarea[name="orderRemarks"], textarea[name="remarks"], [data-testid="order-remarks"]'
    );
    if (await orderRemarks.isVisible().catch(() => false)) {
      await orderRemarks.fill('E2E Test: Appeal upheld. Original complaint resolution stands.');
    }

    // Submit order
    const submitBtn = page.locator(
      'button:has-text("Submit Order"), button:has-text("Pass Order"), button:has-text("Confirm"), [data-testid="submit-order"]'
    );
    await submitBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority passes order with MODIFIED amount', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a separate appeal for MODIFIED test
    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA MODIFIED Order Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber, {
      compensationClaimed: 100000,
    });

    // Advance through workflow via API
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Pass Order action
    const orderBtn = page.locator(
      'button:has-text("Pass Order"), button:has-text("Issue Order"), [data-testid="action-pass-order"]'
    );
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Select MODIFIED outcome
    const outcomeSelect = page.locator(
      'select[name="outcome"], [data-testid="outcome-select"]'
    );
    await expect(outcomeSelect).toBeVisible({ timeout: 5000 });
    await outcomeSelect.selectOption('MODIFIED');

    // Fill compensation amount (required for MODIFIED)
    const amountInput = page.locator(
      'input[name="compensationAmount"], input[name="amount"], [data-testid="compensation-amount"]'
    );
    await expect(amountInput).toBeVisible({ timeout: 5000 });
    await amountInput.fill('75000');

    // Add order remarks
    const orderRemarks = page.locator(
      'textarea[name="orderRemarks"], textarea[name="remarks"], [data-testid="order-remarks"]'
    );
    if (await orderRemarks.isVisible().catch(() => false)) {
      await orderRemarks.fill('E2E Test: Appeal partially allowed. Compensation modified to Rs. 75000.');
    }

    // Submit order
    const submitBtn = page.locator(
      'button:has-text("Submit Order"), button:has-text("Pass Order"), button:has-text("Confirm"), [data-testid="submit-order"]'
    );
    await submitBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority dismisses appeal', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a separate appeal for dismissal test
    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA Dismiss Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    // Advance through workflow via API
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Dismiss action
    const dismissBtn = page.locator(
      'button:has-text("Dismiss"), [data-testid="action-dismiss"]'
    );
    await expect(dismissBtn).toBeVisible({ timeout: 5000 });
    await dismissBtn.click();

    // Fill dismissal reason
    const reasonField = page.locator(
      'textarea[name="reason"], textarea[name="remarks"], [data-testid="dismiss-reason"]'
    );
    if (await reasonField.isVisible({ timeout: 3000 }).catch(() => false)) {
      await reasonField.fill('E2E Test: Appeal is without merit and is hereby dismissed.');
    }

    // Confirm
    const confirmBtn = page.locator(
      'button:has-text("Confirm"), button:has-text("Dismiss"), [data-testid="confirm-action"]'
    );
    await confirmBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Status should be dismissed
    const statusBadge = page.locator('.status-badge, [data-testid="status-badge"]');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/dismissed|closed/);

    await logout(page);
  });
});
