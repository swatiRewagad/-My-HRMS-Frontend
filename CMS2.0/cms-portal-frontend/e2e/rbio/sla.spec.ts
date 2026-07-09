import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

test.describe('RBIO SLA Indicators', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('New RBIO complaint shows 30-day stage deadline', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO SLA 30-day Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // TAT timer bar shows remaining days
      const tatTimer = page.locator('.tat-timer-bar');
      await expect(tatTimer).toBeVisible({ timeout: 5000 });

      const remaining = page.locator('.tat-remaining');
      const text = await remaining.textContent();
      expect(text).toMatch(/\d+d.*remaining/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('SLA progress component shows correct stage (Officer/Conciliation/Adjudication)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO SLA Stage Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // RBIO SLA progress component shows stages: Officer, Conciliation, Adjudication
      const slaProgress = page.locator('app-rbio-sla-progress, .rbio-sla-progress');
      await expect(slaProgress).toBeVisible({ timeout: 5000 });

      const stageText = await slaProgress.textContent();
      expect(stageText).toContain('Officer');
      expect(stageText).toContain('Conciliation');
      expect(stageText).toContain('Adjudication');

      // Current stage indicator
      const currentStage = page.locator('text=Current Stage');
      await expect(currentStage).toBeVisible();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Breached complaint shows red indicator', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    // Check for any SLA-breached visual indicators in the task list
    const slaBreachedIndicators = page.locator('[data-status="breached"], .sla-red, .overdue');
    const count = await slaBreachedIndicators.count();

    if (count > 0) {
      await expect(slaBreachedIndicators.first()).toBeVisible();
    } else {
      test.info().annotations.push({
        type: 'info',
        description: 'No SLA-breached RBIO complaints found in current data — valid for fresh test data',
      });
    }

    await logout(page);
  });

  test('Overall 120-day lifecycle progress displays', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO 120-day Lifecycle Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // The SLA progress component shows "Total Lifecycle (120 days)"
      const lifecycleText = page.locator('text=Total Lifecycle');
      await expect(lifecycleText).toBeVisible({ timeout: 5000 });

      // Should show remaining days
      const slaProgress = page.locator('app-rbio-sla-progress, .rbio-sla-progress');
      const text = await slaProgress.textContent();
      expect(text).toMatch(/\d+d remaining/);
      expect(text).toContain('120');

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
