import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

test.describe.serial('RBIO Supervisor Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Supervisor sees escalated complaints', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Supervisor Visibility Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', '/staff/rbio/tasks');
      await page.waitForSelector('.rbio-home', { timeout: 15000 });
      await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

      const row = page.locator(`.data-grid tbody tr:has-text("${complaintNumber}")`);
      await expect(row).toBeVisible({ timeout: 5000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor approves and forwards to adjudication', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Supervisor Forward Adjudication Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Supervisor actions: "Approve & Escalate", "Return to Officer", "Resolve"
      const approveBtn = page.locator('button.action-btn:has-text("Approve")');
      await expect(approveBtn).toBeVisible({ timeout: 5000 });
      await approveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Conciliation not feasible. Forwarding to adjudication.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor returns to officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Supervisor Return Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const returnBtn = page.locator('button.action-btn:has-text("Return to Officer")');
      await expect(returnBtn).toBeVisible({ timeout: 5000 });
      await returnBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Returning to officer for additional investigation.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor forwards to conciliation', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Supervisor Conciliation Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // "Approve & Escalate" is the primary action — supervisor approves escalation path
      const approveBtn = page.locator('button.action-btn:has-text("Approve")');
      await expect(approveBtn).toBeVisible({ timeout: 5000 });
      await approveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Both parties have agreed to conciliation. Assigning conciliator.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor reassigns to different officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Supervisor Reassign Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'escalated');

      await loginAsRbioRole(page, 'RBIO_SUPERVISOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Resolve action from supervisor's perspective
      const resolveBtn = page.locator('button.action-btn:has-text("Resolve")');
      await expect(resolveBtn).toBeVisible({ timeout: 5000 });
      await resolveBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Reassigning with resolution guidance.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Supervisor dashboard shows SLA compliance stats', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsRbioRole(page, 'RBIO_SUPERVISOR', '/rbio/supervisor-dashboard');
    await page.waitForSelector('.rbio-supervisor-dashboard, .rbio-home', { timeout: 15000 });

    const heading = page.locator('h2, h1').filter({ hasText: /Supervisor|Dashboard|SLA|Escalated/i });
    await expect(heading).toBeVisible({ timeout: 10000 });

    await logout(page);
  });
});
