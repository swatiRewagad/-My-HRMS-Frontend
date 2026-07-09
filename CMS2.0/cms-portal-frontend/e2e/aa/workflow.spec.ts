import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  fileAppeal,
  performAppealAction,
} from '../utils/test-data';

test.describe.serial('AA Workflow', () => {
  let keycloakUp: boolean;
  let appealNumber: string;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const complaint = await createTestComplaint(request, {
        subject: 'E2E AA Workflow Chain Test',
      });
      complaintNumber = complaint.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'closed');

      const appeal = await fileAppeal(request, complaintNumber);
      appealNumber = appeal.appealNumber;
    }
  });

  test('Registrar accepts appeal (status -> under_review)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const acceptBtn = page.locator('.action-card:has-text("Accept Appeal")');
    await expect(acceptBtn).toBeVisible({ timeout: 5000 });
    await acceptBtn.click();

    // Generic action form appears (Accept does not require remarks)
    const submitBtn = page.locator('.action-form .submit-btn');
    await expect(submitBtn).toBeVisible({ timeout: 5000 });
    await submitBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    const statusBadge = page.locator('.status-badge');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/under.review|accepted|in.review/);

    await logout(page);
  });

  test('Registrar rejects appeal (status -> rejected)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA Rejection Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const rejectBtn = page.locator('.action-card:has-text("Reject Appeal")');
    await expect(rejectBtn).toBeVisible({ timeout: 5000 });
    await rejectBtn.click();

    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill('E2E Test: Appeal does not meet eligibility criteria.');

    const submitBtn = page.locator('.action-form .submit-btn');
    await submitBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    const statusBadge = page.locator('.status-badge');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/rejected|dismissed/);

    await logout(page);
  });

  test('Registrar assigns to bench (role change)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const assignBtn = page.locator('.action-card:has-text("Assign to Bench")');
    await expect(assignBtn).toBeVisible({ timeout: 5000 });
    await assignBtn.click();

    const actionForm = page.locator('.action-form');
    await expect(actionForm).toBeVisible();

    // Select officer from dropdown (requiresTarget: true)
    const targetSelect = actionForm.locator('select');
    if (await targetSelect.isVisible({ timeout: 3000 }).catch(() => false)) {
      const options = targetSelect.locator('option');
      const optionCount = await options.count();
      if (optionCount > 1) {
        await targetSelect.selectOption({ index: 1 });
      }
    }

    const remarksField = actionForm.locator('textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill('Assigning to bench officer for processing.');

    const submitBtn = actionForm.locator('.submit-btn');
    await submitBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Bench officer schedules hearing (date set)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const scheduleBtn = page.locator('.action-card:has-text("Schedule Hearing")');
    await expect(scheduleBtn).toBeVisible({ timeout: 5000 });
    await scheduleBtn.click();

    // The hearing sub-component panel appears
    const hearingPanel = page.locator('.hearing-panel');
    await expect(hearingPanel).toBeVisible({ timeout: 5000 });

    // Fill hearing date
    const hearingDate = new Date();
    hearingDate.setDate(hearingDate.getDate() + 14);
    const dateStr = hearingDate.toISOString().split('T')[0];

    const dateInput = hearingPanel.locator('input[type="date"]');
    await expect(dateInput).toBeVisible();
    await dateInput.fill(dateStr);

    // Fill time
    const timeInput = hearingPanel.locator('input[type="time"]');
    if (await timeInput.isVisible().catch(() => false)) {
      await timeInput.fill('10:30');
    }

    // Select venue from dropdown
    const venueSelect = hearingPanel.locator('select');
    if (await venueSelect.isVisible().catch(() => false)) {
      const options = venueSelect.locator('option');
      const optionCount = await options.count();
      if (optionCount > 1) {
        await venueSelect.selectOption({ index: 1 });
      }
    }

    // Click Preview Notice
    const previewBtn = hearingPanel.locator('.preview-btn');
    await expect(previewBtn).toBeVisible();
    await previewBtn.click();

    // Confirm from preview
    const confirmBtn = hearingPanel.locator('.submit-btn');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    const successMsg = hearingPanel.locator('.success-msg');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Bench forwards to authority', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_BENCH_OFFICER', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const forwardBtn = page.locator('.action-card:has-text("Forward to Authority")');
    await expect(forwardBtn).toBeVisible({ timeout: 5000 });
    await forwardBtn.click();

    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill('E2E Test: Hearing completed, forwarding for final order.');

    const submitBtn = page.locator('.action-form .submit-btn');
    await submitBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority passes order with UPHELD outcome', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA UPHELD Order Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const orderBtn = page.locator('.action-card:has-text("Pass Order")');
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Order sub-component panel appears
    const orderPanel = page.locator('.order-panel');
    await expect(orderPanel).toBeVisible({ timeout: 5000 });

    // Select UPHELD outcome (radio button)
    const upheldOption = orderPanel.locator('.outcome-option:has-text("Upheld"), .outcome-option:has-text("UPHELD")');
    await expect(upheldOption).toBeVisible();
    await upheldOption.locator('input[type="radio"]').check();

    // Fill order summary
    const summaryField = orderPanel.locator('textarea');
    await expect(summaryField).toBeVisible();
    await summaryField.fill('E2E Test: Appeal upheld. Original complaint resolution stands.');

    // Preview then confirm
    const previewBtn = orderPanel.locator('.preview-btn');
    await previewBtn.click();

    const confirmBtn = orderPanel.locator('.submit-btn');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    const successMsg = orderPanel.locator('.success-msg');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority passes order with MODIFIED amount', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA MODIFIED Order Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber, {
      compensationClaimed: 100000,
    });

    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const orderBtn = page.locator('.action-card:has-text("Pass Order")');
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    const orderPanel = page.locator('.order-panel');
    await expect(orderPanel).toBeVisible({ timeout: 5000 });

    // Select MODIFIED outcome (radio button)
    const modifiedOption = orderPanel.locator('.outcome-option:has-text("Modified"), .outcome-option:has-text("MODIFIED")');
    await expect(modifiedOption).toBeVisible();
    await modifiedOption.locator('input[type="radio"]').check();

    // Fill modified amount
    const amountInput = orderPanel.locator('input[type="number"]');
    await expect(amountInput).toBeVisible({ timeout: 5000 });
    await amountInput.fill('75000');

    // Fill order summary
    const summaryField = orderPanel.locator('textarea');
    await summaryField.fill('E2E Test: Appeal partially allowed. Compensation modified to Rs. 75000.');

    // Preview then confirm
    const previewBtn = orderPanel.locator('.preview-btn');
    await previewBtn.click();

    const confirmBtn = orderPanel.locator('.submit-btn');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    const successMsg = orderPanel.locator('.success-msg');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    await logout(page);
  });

  test('Authority dismisses appeal', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E AA Dismiss Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);

    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appeal.appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const dismissBtn = page.locator('.action-card:has-text("Dismiss Appeal")');
    await expect(dismissBtn).toBeVisible({ timeout: 5000 });
    await dismissBtn.click();

    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill('E2E Test: Appeal is without merit and is hereby dismissed.');

    const submitBtn = page.locator('.action-form .submit-btn');
    await submitBtn.click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    const statusBadge = page.locator('.status-badge');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/dismissed|closed/);

    await logout(page);
  });
});
