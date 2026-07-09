import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
  performRbioAction,
} from '../utils/test-data';

test.describe('RBIO Admin — Reopen & Reassign', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Admin reopens closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Admin Reopen Test',
      complainantName: 'Reopen Test Citizen',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');

      await loginAsRbioRole(page, 'RBIO_ADMIN', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const closedBanner = page.locator('.closed-banner');
      await expect(closedBanner).toBeVisible({ timeout: 5000 });

      const reopenBtn = page.locator('.action-card:has-text("Reopen")');
      await expect(reopenBtn).toBeVisible({ timeout: 5000 });
      await reopenBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Reopening complaint based on new evidence submitted by complainant.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Reopened complaint returns to in_progress', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Reopen Status Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');
      await performRbioAction(request, complaintNumber, 'REOPEN', 'rbio.admin', 'Reopening for verification');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const closedBanner = page.locator('.closed-banner');
      await expect(closedBanner).not.toBeVisible();

      const actionSection = page.locator('.action-section');
      await expect(actionSection).toBeVisible();

      const actions = page.locator('button.action-btn');
      const actionCount = await actions.count();
      expect(actionCount).toBeGreaterThan(0);

      const statusBadge = page.locator('.status-badge');
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
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_ADMIN', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const reassignBtn = page.locator('.action-card:has-text("Reassign")');
      await expect(reassignBtn).toBeVisible({ timeout: 5000 });
      await reassignBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Admin reassigning to officer with relevant domain expertise.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Admin performs bulk assign (if available)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaints: string[] = [];
    for (let i = 0; i < 2; i++) {
      const result = await createRbioComplaint(request, {
        subject: `E2E RBIO Bulk Assign Test ${i + 1}`,
      });
      complaints.push(result.complaintNumber);
    }

    try {
      await loginAsRbioRole(page, 'RBIO_ADMIN', '/staff/rbio/tasks');
      await page.waitForSelector('.rbio-home', { timeout: 15000 });
      await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

      const checkboxes = page.locator('tbody input[type="checkbox"]');

      if (await checkboxes.first().isVisible({ timeout: 3000 }).catch(() => false)) {
        const checkCount = Math.min(await checkboxes.count(), 2);
        for (let i = 0; i < checkCount; i++) {
          await checkboxes.nth(i).check();
        }

        const bulkBtn = page.locator('button:has-text("Bulk Assign"), button:has-text("Assign Selected")');

        if (await bulkBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
          await bulkBtn.click();

          const dialog = page.locator('[role="dialog"], .modal-overlay');
          await expect(dialog).toBeVisible({ timeout: 5000 });

          const confirmBtn = dialog.locator('button:has-text("Confirm"), button:has-text("Assign")');
          await confirmBtn.click();

          const resultMsg = page.locator('.result-msg.success');
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
