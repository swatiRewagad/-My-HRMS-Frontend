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

  /**
   * Helper to create an appeal and advance it to the point where authority can pass an order.
   */
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

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Pass Order
    const orderBtn = page.locator(
      'button:has-text("Pass Order"), button:has-text("Issue Order"), [data-testid="action-pass-order"]'
    );
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Outcome selector should be visible with options
    const outcomeSelect = page.locator(
      'select[name="outcome"], [data-testid="outcome-select"]'
    );
    await expect(outcomeSelect).toBeVisible({ timeout: 5000 });

    // Get available options
    const options = outcomeSelect.locator('option');
    const optionTexts = await options.allTextContents();
    const optionsJoined = optionTexts.join(' ').toUpperCase();

    // Should include UPHELD, MODIFIED, DISMISSED as outcome options
    expect(optionsJoined).toMatch(/UPHELD/);
    expect(optionsJoined).toMatch(/MODIFIED/);
    expect(optionsJoined).toMatch(/DISMISS/);
  });

  test('MODIFIED outcome requires amount input', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Pass Order
    const orderBtn = page.locator(
      'button:has-text("Pass Order"), button:has-text("Issue Order"), [data-testid="action-pass-order"]'
    );
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Select MODIFIED outcome
    const outcomeSelect = page.locator(
      'select[name="outcome"], [data-testid="outcome-select"]'
    );
    await expect(outcomeSelect).toBeVisible({ timeout: 5000 });
    await outcomeSelect.selectOption('MODIFIED');

    // Amount input should now be visible (conditionally rendered)
    const amountInput = page.locator(
      'input[name="compensationAmount"], input[name="amount"], [data-testid="compensation-amount"]'
    );
    await expect(amountInput).toBeVisible({ timeout: 5000 });

    // Verify it is required (try submitting without it)
    const submitBtn = page.locator(
      'button:has-text("Submit Order"), button:has-text("Pass Order"), button:has-text("Confirm"), [data-testid="submit-order"]'
    );

    // Attempt submit without amount
    await submitBtn.click();

    // Should show validation error or amount field should have required indicator
    const validationError = page.locator(
      '.error-msg, .field-error, [data-testid="amount-error"], .invalid-feedback'
    );
    const amountRequired = await amountInput.getAttribute('required');
    const hasValidation = await validationError.isVisible().catch(() => false);

    expect(hasValidation || amountRequired !== null).toBeTruthy();
  });

  test('Order submission succeeds', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const appealNumber = await setupAppealForOrder(request);

    await loginAsAaRole(page, 'AA_AUTHORITY', `/aa/appeal/${appealNumber}`);

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Click Pass Order
    const orderBtn = page.locator(
      'button:has-text("Pass Order"), button:has-text("Issue Order"), [data-testid="action-pass-order"]'
    );
    await expect(orderBtn).toBeVisible({ timeout: 5000 });
    await orderBtn.click();

    // Select UPHELD outcome
    const outcomeSelect = page.locator(
      'select[name="outcome"], [data-testid="outcome-select"]'
    );
    await expect(outcomeSelect).toBeVisible({ timeout: 5000 });
    await outcomeSelect.selectOption('UPHELD');

    // Fill remarks
    const orderRemarks = page.locator(
      'textarea[name="orderRemarks"], textarea[name="remarks"], [data-testid="order-remarks"]'
    );
    if (await orderRemarks.isVisible().catch(() => false)) {
      await orderRemarks.fill('E2E Order Test: Appeal upheld after full consideration of facts.');
    }

    // Submit order
    const submitBtn = page.locator(
      'button:has-text("Submit Order"), button:has-text("Pass Order"), button:has-text("Confirm"), [data-testid="submit-order"]'
    );
    await submitBtn.click();

    // Wait for success
    const successMsg = page.locator('.success-msg, [data-testid="action-success"], .toast-success');
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Status should update to order_passed / closed
    const statusBadge = page.locator('.status-badge, [data-testid="status-badge"]');
    const statusText = await statusBadge.textContent();
    expect(statusText?.toLowerCase()).toMatch(/order.passed|closed|disposed/);
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

    await page.waitForSelector(
      '[data-testid="appeal-detail"], .appeal-detail, .detail-layout',
      { timeout: 15000 }
    );

    // Order details section should be visible
    const orderSection = page.locator(
      '[data-testid="order-details"], .order-details, .order-section, h3:has-text("Order"), h4:has-text("Order")'
    );
    await expect(orderSection).toBeVisible({ timeout: 10000 });

    // Outcome should be displayed
    const outcomeDisplay = page.locator(
      '[data-testid="order-outcome"], .order-outcome, .outcome-badge'
    );
    await expect(outcomeDisplay).toBeVisible();
    const outcomeText = await outcomeDisplay.textContent();
    expect(outcomeText?.toUpperCase()).toMatch(/UPHELD/);

    // Order date should be present
    const orderDate = page.locator(
      '[data-testid="order-date"], .order-date'
    );
    if (await orderDate.isVisible().catch(() => false)) {
      const dateText = await orderDate.textContent();
      expect(dateText?.trim().length).toBeGreaterThan(0);
    }
  });
});
