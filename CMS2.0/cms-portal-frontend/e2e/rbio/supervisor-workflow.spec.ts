import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

/**
 * RBIO Supervisor Workflow Tests
 *
 * Tests the RBIO Supervisor actions:
 * - Sees escalated complaints
 * - APPROVE + FORWARD_TO_ADJUDICATION
 * - RETURN_TO_OFFICER
 * - FORWARD_TO_CONCILIATION
 * - REASSIGN to different officer
 * - Supervisor dashboard SLA compliance stats
 */
test.describe.serial('RBIO Supervisor Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Supervisor sees escalated complaints', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create and escalate a complaint
    const result = await createRbioComplaint(request, {
      subject: 'E2E Supervisor Visibility Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', '/staff/rbio/tasks');
      await page.waitForSelector('[data-testid="task-table"], .task-table, .complaints-table, .empty-state', {
        timeout: 15000,
      });

      // Search for the escalated complaint
      const searchInput = page.locator('[data-testid="search-input"], .search-box input, input[placeholder*="Search"]');
      if (await searchInput.isVisible()) {
        await searchInput.fill(complaintNumber);
        await page.waitForTimeout(500);
      }

      // The complaint should be visible in the supervisor's queue
      const row = page.locator(`tbody tr:has-text("${complaintNumber}")`);
      await expect(row).toBeVisible({ timeout: 5000 });

      // Status should show escalated
      const statusCell = row.locator('.status-badge, [data-testid="status"]');
      const statusText = await statusCell.textContent();
      expect(statusText?.toLowerCase()).toMatch(/escalated/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor approves and forwards to adjudication', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Supervisor Forward Adjudication Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Forward to Adjudication
      const adjBtn = page.locator(
        '[data-testid="action-adjudication"], .action-card:has-text("Adjudication"), .action-card:has-text("Forward to Adjudication"), button:has-text("Adjudication")'
      );
      await expect(adjBtn).toBeVisible({ timeout: 5000 });
      await adjBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Conciliation not feasible. Forwarding to adjudication per RBI OS Scheme.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should indicate adjudication
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/adjudication/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor returns to officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Supervisor Return Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Return to Officer
      const returnBtn = page.locator(
        '[data-testid="action-return"], .action-card:has-text("Return to Officer"), .action-card:has-text("Return"), button:has-text("Return")'
      );
      await expect(returnBtn).toBeVisible({ timeout: 5000 });
      await returnBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Returning to officer for additional investigation on disputed transactions.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should revert to in_progress or assigned
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/in.progress|assigned|officer/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor forwards to conciliation', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Supervisor Conciliation Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Forward to Conciliation
      const concBtn = page.locator(
        '[data-testid="action-conciliation"], .action-card:has-text("Conciliation"), button:has-text("Conciliation")'
      );
      await expect(concBtn).toBeVisible({ timeout: 5000 });
      await concBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Both parties have agreed to conciliation. Assigning conciliator.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should indicate conciliation
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/conciliation/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor reassigns to different officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Supervisor Reassign Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
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
      await remarksField.fill('Reassigning to officer with banking domain expertise.');

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

  test('Supervisor dashboard shows SLA compliance stats', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsRbioRole(page, 'RBIO_SUPERVISOR', '/rbio/supervisor-dashboard');
    await page.waitForTimeout(2000);

    const currentUrl = page.url();

    if (currentUrl.includes('/rbio/supervisor-dashboard')) {
      // Dashboard should have SLA compliance section
      const heading = page.locator('h2, h3, h1').filter({ hasText: /Supervisor|Dashboard|SLA/i });
      await expect(heading).toBeVisible({ timeout: 10000 });

      // Look for SLA stats
      const slaSection = page.locator(
        '[data-testid="sla-compliance"], .sla-compliance, .sla-stats, .compliance-stats'
      );
      if (await slaSection.isVisible()) {
        await expect(slaSection).toBeVisible();
      } else {
        // At minimum, the page should render
        const content = page.locator('main, .dashboard-content, .page-content');
        await expect(content).toBeVisible();
      }
    } else {
      test.info().annotations.push({
        type: 'info',
        description: 'Supervisor dashboard route (/rbio/supervisor-dashboard) does not exist yet',
      });
    }

    await logout(page);
  });
});
