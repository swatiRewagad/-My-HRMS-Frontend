import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
  performRbioAction,
} from '../utils/test-data';

/**
 * RBIO Admin — Reopen & Reassign Tests
 *
 * Tests:
 * - Admin reopens a closed complaint
 * - Reopened complaint returns to in_progress
 * - Admin reassigns complaint to a different officer
 * - Admin performs bulk assign (if available)
 */
test.describe('RBIO Admin — Reopen & Reassign', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Admin reopens closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create and close a complaint
    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Admin Reopen Test',
      complainantName: 'Reopen Test Citizen',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');

      await loginAsRbioRole(page, 'RBIO_ADMIN', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Should show the closed/resolved banner
      const closedBanner = page.locator(
        '[data-testid="closed-banner"], .closed-banner, .resolved-banner, .status-badge:has-text("Closed")'
      );
      await expect(closedBanner).toBeVisible({ timeout: 5000 });

      // Admin should have Reopen action available
      const reopenBtn = page.locator(
        '[data-testid="action-reopen"], .action-card:has-text("Reopen"), button:has-text("Reopen")'
      );
      await expect(reopenBtn).toBeVisible({ timeout: 5000 });
      await reopenBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Reopening complaint based on new evidence submitted by complainant.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });
      await expect(resultMsg).toContainText(/completed|success/i);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Reopened complaint returns to in_progress', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create, close, then reopen via API
    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Reopen Status Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');

      // Reopen via API
      await performRbioAction(request, complaintNumber, 'REOPEN', 'rbio.admin', 'Reopening for verification');

      // Log in as officer to verify the complaint is back in active state
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Closed banner should NOT be visible
      const closedBanner = page.locator('[data-testid="closed-banner"], .closed-banner, .resolved-banner');
      await expect(closedBanner).not.toBeVisible();

      // Action panel should show available actions
      const actionList = page.locator(
        '[data-testid="action-list"], .action-list, .action-panel, .actions-container'
      );
      await expect(actionList).toBeVisible();

      // Officer should be able to act on it
      const actions = page.locator('[data-testid^="action-"], .action-card');
      const actionCount = await actions.count();
      expect(actionCount).toBeGreaterThan(0);

      // Status should indicate active state
      const statusBadge = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await statusBadge.textContent();
      expect(statusText?.toLowerCase()).toMatch(/in.progress|assigned|reopened|active/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Admin reassigns complaint to different officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Admin Reassign Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to in_progress so there is an assigned officer
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_ADMIN', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Reassign action
      const reassignBtn = page.locator(
        '[data-testid="action-reassign"], .action-card:has-text("Reassign"), button:has-text("Reassign")'
      );
      await expect(reassignBtn).toBeVisible({ timeout: 5000 });
      await reassignBtn.click();

      // Action form should appear with target user dropdown
      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Select a different officer from dropdown
      const targetSelect = actionForm.locator('select, [data-testid="officer-select"]');
      if (await targetSelect.isVisible()) {
        const options = targetSelect.locator('option');
        const optionCount = await options.count();
        if (optionCount > 1) {
          await targetSelect.selectOption({ index: 1 });
        }
      }

      // Fill remarks
      const remarksField = actionForm.locator('textarea, [data-testid="remarks-input"]');
      await remarksField.fill('Admin reassigning to officer with relevant domain expertise.');

      // Submit
      const confirmBtn = actionForm.locator('.submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Admin performs bulk assign (if available)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create multiple complaints for bulk assign
    const complaints: string[] = [];
    for (let i = 0; i < 2; i++) {
      const result = await createRbioComplaint(request, {
        subject: `E2E RBIO Bulk Assign Test ${i + 1}`,
      });
      complaints.push(result.complaintNumber);
    }

    try {
      await loginAsRbioRole(page, 'RBIO_ADMIN', '/staff/rbio/tasks');
      await page.waitForSelector('[data-testid="task-table"], .task-table, .complaints-table, .empty-state', {
        timeout: 15000,
      });

      // Look for bulk selection mechanism (checkboxes)
      const checkboxes = page.locator(
        'tbody input[type="checkbox"], tbody [data-testid="row-select"]'
      );

      if (await checkboxes.first().isVisible({ timeout: 3000 }).catch(() => false)) {
        // Select first two rows
        const checkCount = Math.min(await checkboxes.count(), 2);
        for (let i = 0; i < checkCount; i++) {
          await checkboxes.nth(i).check();
        }

        // Look for bulk action button
        const bulkBtn = page.locator(
          '[data-testid="bulk-assign"], button:has-text("Bulk Assign"), button:has-text("Assign Selected")'
        );

        if (await bulkBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
          await bulkBtn.click();

          // Should show assignment dialog
          const dialog = page.locator('[role="dialog"], .modal-overlay, [data-testid="bulk-assign-dialog"]');
          await expect(dialog).toBeVisible({ timeout: 5000 });

          // Select target officer
          const targetSelect = dialog.locator('select, [data-testid="officer-select"]');
          if (await targetSelect.isVisible()) {
            const options = targetSelect.locator('option');
            const optionCount = await options.count();
            if (optionCount > 1) {
              await targetSelect.selectOption({ index: 1 });
            }
          }

          // Confirm
          const confirmBtn = dialog.locator('button:has-text("Confirm"), button:has-text("Assign"), .submit-btn');
          await confirmBtn.click();

          const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
          await expect(resultMsg).toBeVisible({ timeout: 10000 });
        } else {
          test.info().annotations.push({
            type: 'info',
            description: 'Bulk assign button not visible — feature may not be implemented',
          });
        }
      } else {
        test.info().annotations.push({
          type: 'info',
          description: 'Row checkboxes not available — bulk assign feature may not be implemented',
        });
      }

      await logout(page);
    } finally {
      for (const num of complaints) {
        await cleanupRbioComplaint(request, num);
      }
    }
  });
});
