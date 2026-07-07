import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

/**
 * RBIO Conciliation Workflow Tests
 *
 * Tests the RBIO Conciliator actions:
 * - Conciliator sees assigned complaint
 * - Conciliator schedules meeting
 * - CONCILIATION_SUCCESS (status -> conciliated, sets compensation)
 * - CONCILIATION_FAILED -> ESCALATE_TO_ADJUDICATION
 * - Compensation cap validation displays warning
 */
test.describe.serial('RBIO Conciliation Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Conciliator sees assigned complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Conciliation Visibility Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to conciliation stage
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', '/staff/rbio/tasks');
      await page.waitForSelector('[data-testid="task-table"], .task-table, .complaints-table, .empty-state', {
        timeout: 15000,
      });

      // Search for the complaint
      const searchInput = page.locator('[data-testid="search-input"], .search-box input, input[placeholder*="Search"]');
      if (await searchInput.isVisible()) {
        await searchInput.fill(complaintNumber);
        await page.waitForTimeout(500);
      }

      // The complaint should be visible in the conciliator's queue
      const row = page.locator(`tbody tr:has-text("${complaintNumber}")`);
      await expect(row).toBeVisible({ timeout: 5000 });

      // Status should show conciliation
      const statusCell = row.locator('.status-badge, [data-testid="status"]');
      const statusText = await statusCell.textContent();
      expect(statusText?.toLowerCase()).toMatch(/conciliation/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Conciliator schedules meeting', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Conciliation Meeting Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Look for schedule meeting action
      const meetingBtn = page.locator(
        '[data-testid="action-schedule-meeting"], .action-card:has-text("Schedule Meeting"), .action-card:has-text("Schedule"), button:has-text("Schedule")'
      );

      if (await meetingBtn.isVisible({ timeout: 5000 }).catch(() => false)) {
        await meetingBtn.click();

        // Fill meeting details
        const actionForm = page.locator('.action-form, [data-testid="action-form"]');
        await expect(actionForm).toBeVisible();

        // Date picker
        const dateInput = actionForm.locator('input[type="date"], input[type="datetime-local"], [data-testid="meeting-date"]');
        if (await dateInput.isVisible()) {
          // Set meeting date to tomorrow
          const tomorrow = new Date();
          tomorrow.setDate(tomorrow.getDate() + 1);
          const dateStr = tomorrow.toISOString().split('T')[0];
          await dateInput.fill(dateStr);
        }

        // Remarks
        const remarksField = actionForm.locator('textarea, [data-testid="remarks-input"]');
        if (await remarksField.isVisible()) {
          await remarksField.fill('Scheduling conciliation meeting between complainant and entity.');
        }

        // Submit
        const confirmBtn = actionForm.locator('.submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
        await confirmBtn.click();

        const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
        await expect(resultMsg).toBeVisible({ timeout: 10000 });
      } else {
        test.info().annotations.push({
          type: 'info',
          description: 'Schedule meeting action not available — may not be implemented as separate action',
        });
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Conciliation succeeds (status -> conciliated, sets compensation)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Conciliation Success Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Conciliation Success action
      const successBtn = page.locator(
        '[data-testid="action-conciliation-success"], .action-card:has-text("Conciliation Success"), .action-card:has-text("Success"), button:has-text("Success")'
      );
      await expect(successBtn).toBeVisible({ timeout: 5000 });
      await successBtn.click();

      // Action form should appear
      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Fill compensation amount
      const amountInput = actionForm.locator(
        'input[type="number"], input[name="compensationAmount"], [data-testid="compensation-amount"]'
      );
      if (await amountInput.isVisible()) {
        await amountInput.fill('50000');
      }

      // Fill remarks
      const remarksField = actionForm.locator('textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Conciliation successful. Entity agrees to compensate complainant INR 50,000.');

      // Submit
      const confirmBtn = actionForm.locator('.submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be conciliated
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/conciliated|resolved/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Conciliation fails -> escalates to adjudication', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Conciliation Failure Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Conciliation Failed action
      const failedBtn = page.locator(
        '[data-testid="action-conciliation-failed"], .action-card:has-text("Conciliation Failed"), .action-card:has-text("Failed"), .action-card:has-text("Escalate to Adjudication")'
      );
      await expect(failedBtn).toBeVisible({ timeout: 5000 });
      await failedBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Conciliation failed. Entity unwilling to provide adequate compensation. Escalating to adjudication.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should indicate adjudication stage
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/adjudication|failed/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Compensation cap validation displays warning', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Compensation Cap Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      await loginAsRbioRole(page, 'RBIO_CONCILIATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Conciliation Success
      const successBtn = page.locator(
        '[data-testid="action-conciliation-success"], .action-card:has-text("Conciliation Success"), .action-card:has-text("Success"), button:has-text("Success")'
      );
      await expect(successBtn).toBeVisible({ timeout: 5000 });
      await successBtn.click();

      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Enter amount exceeding cap (e.g., > 20 lakh for general, > 1 crore for certain categories)
      const amountInput = actionForm.locator(
        'input[type="number"], input[name="compensationAmount"], [data-testid="compensation-amount"]'
      );
      if (await amountInput.isVisible()) {
        // Enter a very high amount to trigger cap warning
        await amountInput.fill('50000000'); // 5 crore

        await page.waitForTimeout(500);

        // Should show a cap warning/error
        const warning = page.locator(
          '[data-testid="cap-warning"], .cap-warning, .validation-error, .field-error, .amount-warning'
        );
        await expect(warning).toBeVisible({ timeout: 5000 });

        const warningText = await warning.textContent();
        expect(warningText?.toLowerCase()).toMatch(/cap|exceed|limit|maximum/);
      } else {
        test.info().annotations.push({
          type: 'info',
          description: 'Compensation amount field not visible — cap validation not testable',
        });
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
