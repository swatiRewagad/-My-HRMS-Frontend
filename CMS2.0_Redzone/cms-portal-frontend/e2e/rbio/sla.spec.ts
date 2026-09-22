import { test, expect, ageComplaintPastSla } from './harness';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
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

      const tatTimer = page.locator('.tat-timer-bar');
      await expect(tatTimer).toBeVisible({ timeout: 5000 });

      const text = await page.locator('.tat-remaining').textContent();
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

      // The comma selector matches the component's host tag AND its own class on that same
      // element, so it resolves to 2 nodes and trips Playwright strict mode.
      const slaProgress = page.locator('app-rbio-sla-progress, .rbio-sla-progress').first();
      await expect(slaProgress).toBeVisible({ timeout: 5000 });

      const stageText = await slaProgress.textContent();
      expect(stageText).toContain('Officer');
      expect(stageText).toContain('Conciliation');
      expect(stageText).toContain('Adjudication');

      const currentStage = page.locator('text=Current Stage');
      await expect(currentStage).toBeVisible();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Breached complaint shows an overdue indicator', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version of this test read:
    //     if (count > 0) { assert } else { test.info().annotations.push(...) }
    // so with zero breached complaints in the database it passed having asserted nothing. An
    // annotation does not affect exit status; it was standing in for an assertion. The breach is now
    // SEEDED so the assertion always runs.
    const result = await createRbioComplaint(request, { subject: 'S4-RBIO SLA Breach Test' });
    const complaintNumber = result.complaintNumber;

    try {
      // Aged 45 days against a 30-business-day SLA, so it is past due by any reading.
      const seeded = ageComplaintPastSla(complaintNumber, 45);
      expect(
        seeded,
        'could not seed an SLA breach (MySQL client unavailable) — this test cannot honestly pass without one'
      ).toBe(true);

      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const tatTimer = page.locator('.tat-timer-bar');
      await expect(tatTimer).toBeVisible({ timeout: 10000 });

      // The component sets the literal string 'SLA BREACHED' once TatResult.breached is true
      // (task-action.component.ts:197). A complaint 45 days old against a 30-day SLA must show it and
      // must not report time still remaining.
      const remaining = (await page.locator('.tat-remaining').textContent()) ?? '';
      expect(
        remaining,
        `a complaint aged 45 days past a 30-day SLA still reports "${remaining}"`
      ).toMatch(/BREACHED/i);
      expect(remaining).not.toMatch(/remaining/i);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Overall 120-day lifecycle progress displays', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO 120-day Lifecycle Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const lifecycleText = page.locator('text=Total Lifecycle');
      await expect(lifecycleText).toBeVisible({ timeout: 5000 });

      const slaProgress = page.locator('app-rbio-sla-progress, .rbio-sla-progress').first();
      const text = await slaProgress.textContent();
      expect(text).toMatch(/\d+d remaining/);
      expect(text).toContain('120');

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
