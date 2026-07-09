import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  fileAppeal,
  performAppealAction,
} from '../utils/test-data';

test.describe('AA - Order Management', () => {
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

  async function setupAppealForOrder(request: import('@playwright/test').APIRequestContext): Promise<string> {
    const complaint = await createTestComplaint(request, {
      subject: `E2E AA Order Test ${Date.now().toString(36)}`,
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber);
    await performAppealAction(request, appeal.appealNumber, 'ACCEPT', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'ASSIGN_TO_BENCH', { actor: 'aa.registrar' });
    await performAppealAction(request, appeal.appealNumber, 'FORWARD_TO_AUTHORITY', { actor: 'aa.bench' });
    return appeal.appealNumber;
  }

  test('Order form shows outcome options', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const orderBtn = page.locator('.action-card:has-text("Pass Order")');
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Order sub-component panel appears
    const orderPanel = page.locator('.order-panel');
    await expect(orderPanel).toBeVisible({ timeout: 5000 });

    // Outcome options are radio buttons
    const outcomeOptions = orderPanel.locator('.outcome-option');
    const optionCount = await outcomeOptions.count();
    expect(optionCount).toBeGreaterThanOrEqual(3);

    const optionTexts = await outcomeOptions.allTextContents();
    const joined = optionTexts.join(' ').toUpperCase();
    expect(joined).toMatch(/UPHELD/);
    expect(joined).toMatch(/MODIFIED/);
    expect(joined).toMatch(/DISMISS/);
  });

  test('MODIFIED outcome requires amount input', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const orderBtn = page.locator('.action-card:has-text("Pass Order")');
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    const orderPanel = page.locator('.order-panel');
    await expect(orderPanel).toBeVisible({ timeout: 5000 });

    // Select MODIFIED outcome
    const modifiedOption = orderPanel.locator('.outcome-option:has-text("Modified"), .outcome-option:has-text("MODIFIED")');
    await expect(modifiedOption).toBeVisible();
    await modifiedOption.locator('input[type="radio"]').check();

    // Amount input should now be visible
    const amountInput = orderPanel.locator('input[type="number"]');
    await expect(amountInput).toBeVisible({ timeout: 5000 });
  });

  test('Order submission succeeds', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    const orderBtn = page.locator('.action-card:has-text("Pass Order")');
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    const orderPanel = page.locator('.order-panel');
    await expect(orderPanel).toBeVisible({ timeout: 5000 });

    // Select UPHELD outcome
    const upheldOption = orderPanel.locator('.outcome-option:has-text("Upheld"), .outcome-option:has-text("UPHELD")');
    await upheldOption.locator('input[type="radio"]').check();

    // Fill order summary
    const summaryField = orderPanel.locator('textarea');
    await expect(summaryField).toBeVisible();
    await summaryField.fill('E2E Order Test: Appeal upheld after full consideration of facts.');

    // Preview then confirm
    const previewBtn = orderPanel.locator('.preview-btn');
    await previewBtn.click();

    const confirmBtn = orderPanel.locator('.submit-btn');
    await expect(confirmBtn).toBeVisible({ timeout: 5000 });
    await confirmBtn.click();

    const successMsg = orderPanel.locator('.success-msg');
    await expect(successMsg).toBeVisible({ timeout: 10000 });
  });

  test('Order details display after submission', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    // Pass order via API
    await performAppealAction(request, appealNumber, 'PASS_ORDER', {
      actor: 'aa.authority',
      outcome: 'UPHELD',
      orderRemarks: 'E2E: Order passed via API for display verification.',
    });

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);
    await page.waitForSelector('.aa-detail .detail-layout', { timeout: 15000 });

    // Order section should be visible (rendered when appeal().order exists)
    const orderSection = page.locator('.order-section');
    await expect(orderSection).toBeVisible({ timeout: 10000 });

    // Outcome badge should be displayed
    const outcomeBadge = orderSection.locator('.outcome-badge');
    await expect(outcomeBadge).toBeVisible();
    const outcomeText = await outcomeBadge.textContent();
    expect(outcomeText?.toUpperCase()).toMatch(/UPHELD/);
  });
});
