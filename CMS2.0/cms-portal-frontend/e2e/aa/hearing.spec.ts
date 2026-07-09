import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  fileAppeal,
  performAppealAction,
} from '../utils/test-data';

test.describe('AA - Hearing Management', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Schedule hearing form works (date + venue)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Hearing Schedule Form Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const scheduleBtn = page.locator('.action-card:has-text("Schedule Hearing")');
    await expect(scheduleBtn).toBeVisible({ timeout: 5000 });
    await scheduleBtn.click();

    // Hearing sub-component panel appears
    const hearingPanel = page.locator('.hearing-panel');
    await expect(hearingPanel).toBeVisible({ timeout: 5000 });

    // Fill date
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 21);
    const dateStr = futureDate.toISOString().split('T')[0];

    const dateInput = hearingPanel.locator('input[type="date"]');
    await expect(dateInput).toBeVisible();
    await dateInput.fill(dateStr);

    // Fill time
    const timeInput = hearingPanel.locator('input[type="time"]');
    if (await timeInput.isVisible().catch(() => false)) {
      await timeInput.fill('11:00');
    }

    // Select venue
    const venueSelect = hearingPanel.locator('select');
    if (await venueSelect.isVisible().catch(() => false)) {
      const options = venueSelect.locator('option');
      const optionCount = await options.count();
      if (optionCount > 1) {
        await venueSelect.selectOption({ index: 1 });
      }
    }

    // Preview and confirm
    const previewBtn = hearingPanel.locator('.preview-btn');
    await expect(previewBtn).toBeVisible();
    await previewBtn.click();

    const confirmBtn = hearingPanel.locator('.submit-btn');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    const successMsg = hearingPanel.locator('.success-msg');
    await expect(successMsg).toBeVisible({ timeout: 10000 });
  });

  test('Hearing appears in hearing history', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Hearing History Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });

    // Schedule hearing via API
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 14);
    await performAppealAction(request, appeal.appealNumber, 'SCHEDULE_HEARING', {
      actor: 'aa.bench',
      hearingDate: futureDate.toISOString().split('T')[0],
      venue: 'API-Scheduled Test Room',
    });

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    // Look for hearing history in the detail panel
    const hearingHistory = page.locator('.hearing-history');
    await expect(hearingHistory).toBeVisible({ timeout: 10000 });

    // Should show at least one hearing entry in the history table
    const hearingRows = hearingHistory.locator('.history-table tbody tr');
    const count = await hearingRows.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });

  test('Multiple hearings can be scheduled', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Multiple Hearings Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });

    // Schedule two hearings via API
    const firstDate = new Date();
    firstDate.setDate(firstDate.getDate() + 7);
    await performAppealAction(request, appeal.appealNumber, 'SCHEDULE_HEARING', {
      actor: 'aa.bench',
      hearingDate: firstDate.toISOString().split('T')[0],
      venue: 'Room A',
    });

    const secondDate = new Date();
    secondDate.setDate(secondDate.getDate() + 21);
    await performAppealAction(request, appeal.appealNumber, 'SCHEDULE_HEARING', {
      actor: 'aa.bench',
      hearingDate: secondDate.toISOString().split('T')[0],
      venue: 'Room B',
    });

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const hearingHistory = page.locator('.hearing-history');
    await expect(hearingHistory).toBeVisible({ timeout: 10000 });

    const hearingRows = hearingHistory.locator('.history-table tbody tr');
    const count = await hearingRows.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });
});
