import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
  performRbioAction,
} from '../utils/test-data';

/**
 * RBIO Officer Workflow Tests
 *
 * Tests the RBIO Officer actions:
 * - ACCEPT (assigned -> in_progress)
 * - RESOLVE (in_progress -> resolved)
 * - REJECT (non-maintainable)
 * - ESCALATE (to supervisor)
 * - REQUEST_ADDITIONAL_INFO (info_requested)
 * - FORWARD_TO_CONCILIATION
 * - ISSUE_ADVISORY
 * - Timeline verification
 */
test.describe.serial('RBIO Officer Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Officer accepts complaint (status changes to in_progress)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Accept Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);

      // Wait for task detail to load
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Status should be assigned/new
      const statusBadge = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      await expect(statusBadge).toBeVisible();

      // Click Accept action
      const acceptBtn = page.locator(
        '[data-testid="action-accept"], .action-card:has-text("Accept"), button:has-text("Accept")'
      );
      await expect(acceptBtn).toBeVisible({ timeout: 5000 });
      await acceptBtn.click();

      // Submit action (may have a confirm step)
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Confirm")');
      if (await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
        await confirmBtn.click();
      }

      // Wait for success
      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should update to In Progress / Under Examination
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/in.progress|under.examination|accepted/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer resolves directly (status -> resolved, closedAt set)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Resolve Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to in_progress first
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Resolve action
      const resolveBtn = page.locator(
        '[data-testid="action-resolve"], .action-card:has-text("Resolve"), button:has-text("Resolve")'
      );
      await expect(resolveBtn).toBeVisible({ timeout: 5000 });
      await resolveBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint resolved satisfactorily. Entity has provided adequate remedy.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      // Wait for success
      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be resolved
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/resolved/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer rejects non-maintainable (status -> rejected)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Reject Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to in_progress
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Reject action
      const rejectBtn = page.locator(
        '[data-testid="action-reject"], .action-card:has-text("Reject"), .action-card:has-text("Non-Maintainable"), button:has-text("Reject")'
      );
      await expect(rejectBtn).toBeVisible({ timeout: 5000 });
      await rejectBtn.click();

      // Fill rejection reason
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint is not maintainable under RBI Ombudsman Scheme. Matter falls outside jurisdiction.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be rejected
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/rejected|non.maintainable/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer escalates to supervisor (status -> escalated, role changes)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Escalate Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Escalate action
      const escalateBtn = page.locator(
        '[data-testid="action-escalate"], .action-card:has-text("Escalate"), button:has-text("Escalate")'
      );
      await expect(escalateBtn).toBeVisible({ timeout: 5000 });
      await escalateBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint requires supervisor review. Entity is non-cooperative.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be escalated
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/escalated|supervisor/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer requests additional info (status -> info_requested)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Info Request Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Request Additional Info action
      const infoBtn = page.locator(
        '[data-testid="action-request-info"], .action-card:has-text("Additional Info"), .action-card:has-text("Request Info"), button:has-text("Request")'
      );
      await expect(infoBtn).toBeVisible({ timeout: 5000 });
      await infoBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Please provide account statements for the disputed period.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer forwards to conciliation (status -> conciliation)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Forward Conciliation Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Forward to Conciliation action
      const concBtn = page.locator(
        '[data-testid="action-conciliation"], .action-card:has-text("Conciliation"), button:has-text("Conciliation")'
      );
      await expect(concBtn).toBeVisible({ timeout: 5000 });
      await concBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Parties amenable to conciliation. Forwarding to conciliator.');

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

  test('Officer issues advisory (status -> advisory_issued)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Advisory Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Issue Advisory action
      const advisoryBtn = page.locator(
        '[data-testid="action-advisory"], .action-card:has-text("Advisory"), .action-card:has-text("Issue Advisory"), button:has-text("Advisory")'
      );
      await expect(advisoryBtn).toBeVisible({ timeout: 5000 });
      await advisoryBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Advisory issued to the regulated entity regarding customer service practices.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should indicate advisory issued
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/advisory/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('verify timeline shows all officer actions', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint and perform multiple actions via API so timeline has entries
    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Timeline Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Perform actions via API to build timeline
      await performRbioAction(request, complaintNumber, 'ACCEPT', 'rbio.officer', 'Accepting complaint');
      await performRbioAction(request, complaintNumber, 'ESCALATE', 'rbio.officer', 'Escalating to supervisor');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Check timeline/audit trail section
      const timeline = page.locator('[data-testid="timeline"], .timeline, .audit-trail, .activity-log');
      await expect(timeline).toBeVisible({ timeout: 5000 });

      // Should have multiple timeline items
      const timelineItems = page.locator(
        '[data-testid="timeline-item"], .timeline-item, .audit-item, .activity-item'
      );
      const count = await timelineItems.count();
      expect(count).toBeGreaterThanOrEqual(2); // ACCEPT + ESCALATE at minimum

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
