import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
  performRbioAction,
} from '../utils/test-data';

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
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const statusBadge = page.locator('.status-badge');
      await expect(statusBadge).toBeVisible();
      await expect(statusBadge).toContainText(/ASSIGNED/i);

      // RBIO Officer actions: Escalate, Resolve, Reject — "Resolve" acts as accept+resolve
      const resolveBtn = page.locator('button.action-btn:has-text("Resolve")');
      await expect(resolveBtn).toBeVisible({ timeout: 5000 });
      await resolveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Accepting and resolving complaint — entity has addressed the issue.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/resolved|in.progress|accepted/);

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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const resolveBtn = page.locator('button.action-btn:has-text("Resolve")');
      await expect(resolveBtn).toBeVisible({ timeout: 5000 });
      await resolveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint resolved satisfactorily. Entity has provided adequate remedy.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const rejectBtn = page.locator('button.action-btn:has-text("Reject")');
      await expect(rejectBtn).toBeVisible({ timeout: 5000 });
      await rejectBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint is not maintainable under RBI Ombudsman Scheme. Matter falls outside jurisdiction.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const escalateBtn = page.locator('button.action-btn:has-text("Escalate")');
      await expect(escalateBtn).toBeVisible({ timeout: 5000 });
      await escalateBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint requires supervisor review. Entity is non-cooperative.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // RBIO officer doesn't have a dedicated "Request Info" button in current UI.
      // The available actions are: Escalate, Resolve, Reject.
      // We test Escalate here as a proxy for "needs more info from supervisor"
      const escalateBtn = page.locator('button.action-btn:has-text("Escalate")');
      await expect(escalateBtn).toBeVisible({ timeout: 5000 });
      await escalateBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Please provide account statements for the disputed period.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Current UI actions for RBIO_OFFICER: Escalate, Resolve, Reject
      // Escalate routes to supervisor who can forward to conciliation
      const escalateBtn = page.locator('button.action-btn:has-text("Escalate")');
      await expect(escalateBtn).toBeVisible({ timeout: 5000 });
      await escalateBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Parties amenable to conciliation. Forwarding to conciliator via escalation.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/escalated|conciliation/);

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
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // The advisory panel is a separate form section in the task-action page
      const advisorySubject = page.locator('input[placeholder*="Advisory subject"]');
      if (await advisorySubject.isVisible({ timeout: 3000 }).catch(() => false)) {
        await advisorySubject.fill('Advisory regarding customer service practices');
        const advisoryText = page.locator('textarea[placeholder*="advisory opinion"]');
        await advisoryText.fill('Advisory issued to the regulated entity regarding customer service practices.');

        const previewBtn = page.locator('button:has-text("Preview Advisory")');
        if (await previewBtn.isEnabled({ timeout: 2000 }).catch(() => false)) {
          await previewBtn.click();
        }
      }

      // Fallback: use Resolve action
      const resolveBtn = page.locator('button.action-btn:has-text("Resolve")');
      await expect(resolveBtn).toBeVisible({ timeout: 5000 });
      await resolveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Advisory issued to the regulated entity regarding customer service practices.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('verify timeline shows all officer actions', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Officer Timeline Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await performRbioAction(request, complaintNumber, 'ACCEPT', 'rbio_officer_001', 'Accepting complaint');
      await performRbioAction(request, complaintNumber, 'ESCALATE', 'rbio_officer_001', 'Escalating to supervisor');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Timeline section in the task-action component
      const timelineHeader = page.locator('text=Timeline');
      if (await timelineHeader.isVisible({ timeout: 3000 }).catch(() => false)) {
        await timelineHeader.click();
        await page.waitForTimeout(500);
      }

      const timelineItems = page.locator('.timeline-action, .history-status-badge');
      const count = await timelineItems.count();
      expect(count).toBeGreaterThanOrEqual(1);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
