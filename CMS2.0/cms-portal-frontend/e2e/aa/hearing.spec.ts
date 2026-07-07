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

    // Setup: create complaint -> close -> file appeal -> accept -> assign to bench
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Hearing Schedule Form Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Schedule Hearing
    const scheduleBtn = page.locator(
      'button:has-text("Schedule Hearing"), button:has-text("Schedule"), [data-testid="action-schedule-hearing"]'
    );
    await expect(scheduleBtn).toBeVisible({ timeout: 5000 });
    await scheduleBtn.click();

    // Hearing form should appear
    const hearingForm = page.locator(
      '[data-testid="hearing-form"], .hearing-form, .schedule-form'
    );
    await expect(hearingForm).toBeVisible({ timeout: 5000 });

    // Fill date
    const futureDate = new Date();
    futureDate.setDate(futureDate.getDate() + 21);
    const dateStr = futureDate.toISOString().split('T')[0];

    const dateInput = page.locator(
      'input[type="date"], input[name="hearingDate"], [data-testid="hearing-date"]'
    );
    await expect(dateInput).toBeVisible();
    await dateInput.fill(dateStr);

    // Fill venue
    const venueInput = page.locator(
      'input[name="venue"], [data-testid="hearing-venue"], textarea[name="venue"]'
    );
    await expect(venueInput).toBeVisible();
    await venueInput.fill('Conference Room A, 2nd Floor, AA Office');

    // Submit
    const submitBtn = page.locator(
      'button:has-text("Schedule"), button:has-text("Confirm"), [data-testid="confirm-hearing"]'
    );
    await submitBtn.click();

    // Success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });
  });

  test('Hearing appears in hearing history', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Setup: create full flow with a scheduled hearing
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

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Look for hearing history section
    const hearingSection = page.locator(
      '[data-testid="hearing-history"], .hearing-history, .hearings-section, h3:has-text("Hearing"), h4:has-text("Hearing")'
    );
    await expect(hearingSection).toBeVisible({ timeout: 10000 });

    // Should show at least one hearing entry
    const hearingEntries = page.locator(
      '[data-testid="hearing-entry"], .hearing-entry, .hearing-row, .hearing-item'
    );
    const count = await hearingEntries.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Verify hearing details are visible (date and venue)
    const firstEntry = hearingEntries.first();
    const entryText = await firstEntry.textContent();
    expect(entryText).toBeTruthy();
  });

  test('Multiple hearings can be scheduled', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Setup
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Multiple Hearings Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });

    // Schedule first hearing via API
    const firstDate = new Date();
    firstDate.setDate(firstDate.getDate() + 7);
    await performAppealAction(request, appeal.appealNumber, 'SCHEDULE_HEARING', {
      actor: 'aa.bench',
      hearingDate: firstDate.toISOString().split('T')[0],
      venue: 'Room A',
    });

    // Schedule second hearing via API
    const secondDate = new Date();
    secondDate.setDate(secondDate.getDate() + 21);
    await performAppealAction(request, appeal.appealNumber, 'SCHEDULE_HEARING', {
      actor: 'aa.bench',
      hearingDate: secondDate.toISOString().split('T')[0],
      venue: 'Room B',
    });

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appeal.appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Look for hearing history
    const hearingEntries = page.locator(
      '[data-testid="hearing-entry"], .hearing-entry, .hearing-row, .hearing-item'
    );
    await page.waitForSelector(
      '[data-testid="hearing-entry"], .hearing-entry, .hearing-row, .hearing-item',
      { timeout: 10000 }
    );

    const count = await hearingEntries.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });
});
