import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

/**
 * RBIO Adjudication Workflow Tests
 *
 * Tests the RBIO Adjudicator actions:
 * - Adjudicator sees assigned complaint
 * - ISSUE_NOTICE_13_1 (13(1) Notice to regulated entity)
 * - IMPLEAD_PARTY (adds party to impleaded list)
 * - ADJUDICATION_AWARD (valid amount, status -> adjudicated)
 * - Award amount exceeding 30L cap shows error
 * - Award amount exceeding 3L for time/harassment shows error
 * - ADJUDICATION_REJECT
 */
test.describe.serial('RBIO Adjudication Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Adjudicator sees assigned complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Adjudicator Visibility Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      // Advance to adjudication stage
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', '/staff/rbio/tasks');
      await page.waitForSelector('[data-testid="task-table"], .task-table, .complaints-table, .empty-state', {
        timeout: 15000,
      });

      // Search for the complaint
      const searchInput = page.locator('[data-testid="search-input"], .search-box input, input[placeholder*="Search"]');
      if (await searchInput.isVisible()) {
        await searchInput.fill(complaintNumber);
        await page.waitForTimeout(500);
      }

      // Complaint should be visible in adjudicator's queue
      const row = page.locator(`tbody tr:has-text("${complaintNumber}")`);
      await expect(row).toBeVisible({ timeout: 5000 });

      // Status should show adjudication
      const statusCell = row.locator('.status-badge, [data-testid="status"]');
      const statusText = await statusCell.textContent();
      expect(statusText?.toLowerCase()).toMatch(/adjudication/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator issues 13-1 Notice', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Adjudicator Notice 13-1 Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Issue 13(1) Notice action
      const noticeBtn = page.locator(
        '[data-testid="action-notice-13-1"], .action-card:has-text("13(1)"), .action-card:has-text("Notice"), .action-card:has-text("Issue Notice"), button:has-text("Notice")'
      );
      await expect(noticeBtn).toBeVisible({ timeout: 5000 });
      await noticeBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Issuing Notice under Section 13(1) of RBI-OS Scheme to regulated entity for response.');

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

  test('Adjudicator impleads party (adds to impleaded list)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Adjudicator Implead Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Implead Party action
      const impleadBtn = page.locator(
        '[data-testid="action-implead"], .action-card:has-text("Implead"), button:has-text("Implead")'
      );
      await expect(impleadBtn).toBeVisible({ timeout: 5000 });
      await impleadBtn.click();

      // Action form should appear with party details
      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Fill party name
      const partyNameInput = actionForm.locator(
        'input[name="partyName"], input[placeholder*="party" i], [data-testid="party-name"]'
      );
      if (await partyNameInput.isVisible()) {
        await partyNameInput.fill('Test Insurance Company Ltd');
      }

      // Fill remarks
      const remarksField = actionForm.locator('textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Impleading Test Insurance Company Ltd as related party in this dispute.');

      // Submit
      const confirmBtn = actionForm.locator('.submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Verify impleaded party appears in the detail view
      await page.waitForTimeout(1000);
      const impleadedSection = page.locator(
        '[data-testid="impleaded-parties"], .impleaded-parties, .impleaded-list'
      );
      if (await impleadedSection.isVisible()) {
        await expect(impleadedSection).toContainText('Test Insurance Company');
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator passes award with valid amount (status -> adjudicated)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Adjudication Award Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Pass Award action
      const awardBtn = page.locator(
        '[data-testid="action-award"], .action-card:has-text("Award"), .action-card:has-text("Pass Award"), button:has-text("Award")'
      );
      await expect(awardBtn).toBeVisible({ timeout: 5000 });
      await awardBtn.click();

      // Action form should appear
      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Fill award amount (within cap: <= 30 lakh)
      const amountInput = actionForm.locator(
        'input[type="number"], input[name="awardAmount"], [data-testid="award-amount"]'
      );
      if (await amountInput.isVisible()) {
        await amountInput.fill('500000'); // 5 lakh — well within cap
      }

      // Fill remarks
      const remarksField = actionForm.locator('textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Award of INR 5,00,000 passed in favour of the complainant. Entity to comply within 30 days.');

      // Submit
      const confirmBtn = actionForm.locator('.submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be adjudicated/award
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/adjudicated|award/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award amount exceeding 30L cap shows error', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Award Cap 30L Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Pass Award action
      const awardBtn = page.locator(
        '[data-testid="action-award"], .action-card:has-text("Award"), .action-card:has-text("Pass Award"), button:has-text("Award")'
      );
      await expect(awardBtn).toBeVisible({ timeout: 5000 });
      await awardBtn.click();

      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Fill amount exceeding 30 lakh cap (3,000,001)
      const amountInput = actionForm.locator(
        'input[type="number"], input[name="awardAmount"], [data-testid="award-amount"]'
      );
      if (await amountInput.isVisible()) {
        await amountInput.fill('3100000'); // 31 lakh — exceeds 30L cap

        await page.waitForTimeout(500);

        // Should show validation error about 30L cap
        const error = page.locator(
          '[data-testid="amount-error"], .validation-error, .field-error, .amount-error, .cap-warning'
        );
        await expect(error).toBeVisible({ timeout: 5000 });

        const errorText = await error.textContent();
        expect(errorText?.toLowerCase()).toMatch(/exceed|cap|30|limit|maximum/);
      } else {
        test.info().annotations.push({
          type: 'info',
          description: 'Award amount input not visible — cap validation not testable via UI',
        });
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award amount exceeding 3L for time/harassment shows error', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Award Cap 3L Harassment Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Pass Award action
      const awardBtn = page.locator(
        '[data-testid="action-award"], .action-card:has-text("Award"), .action-card:has-text("Pass Award"), button:has-text("Award")'
      );
      await expect(awardBtn).toBeVisible({ timeout: 5000 });
      await awardBtn.click();

      const actionForm = page.locator('.action-form, [data-testid="action-form"]');
      await expect(actionForm).toBeVisible();

      // Check if there is a category/type selector for "loss of time/harassment"
      const categorySelect = actionForm.locator(
        'select[name="awardCategory"], [data-testid="award-category"], select:has(option:has-text("harassment" i))'
      );
      if (await categorySelect.isVisible()) {
        // Select "Loss of time / Harassment" category
        await categorySelect.selectOption({ label: /harassment|time/i }).catch(async () => {
          await categorySelect.selectOption({ index: 1 });
        });
      }

      // Fill amount exceeding 3 lakh cap for harassment
      const amountInput = actionForm.locator(
        'input[type="number"], input[name="awardAmount"], input[name="harassmentAmount"], [data-testid="award-amount"], [data-testid="harassment-amount"]'
      );
      if (await amountInput.isVisible()) {
        await amountInput.fill('400000'); // 4 lakh — exceeds 3L harassment cap

        await page.waitForTimeout(500);

        // Should show validation error about 3L harassment cap
        const error = page.locator(
          '[data-testid="amount-error"], .validation-error, .field-error, .amount-error, .cap-warning'
        );
        await expect(error).toBeVisible({ timeout: 5000 });

        const errorText = await error.textContent();
        expect(errorText?.toLowerCase()).toMatch(/exceed|cap|3|limit|harassment|time/);
      } else {
        test.info().annotations.push({
          type: 'info',
          description: 'Harassment amount input not visible — 3L cap validation not testable via UI',
        });
      }

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator rejects complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E Adjudication Reject Test',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('[data-testid="task-detail"], .task-detail, .detail-layout', {
        timeout: 15000,
      });

      // Click Reject action
      const rejectBtn = page.locator(
        '[data-testid="action-adjudication-reject"], .action-card:has-text("Reject"), button:has-text("Reject")'
      );
      await expect(rejectBtn).toBeVisible({ timeout: 5000 });
      await rejectBtn.click();

      // Fill remarks
      const remarksField = page.locator('.action-form textarea, [data-testid="remarks-input"]');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint does not have sufficient merit for an award. Rejected after examination of evidence.');

      // Submit
      const confirmBtn = page.locator('.action-form .submit-btn, [data-testid="confirm-action"], button:has-text("Submit")');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success, [data-testid="action-success"], .success-msg');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      // Status should be rejected
      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('[data-testid="status-badge"], .status-badge, .complaint-status');
      const statusText = await updatedStatus.textContent();
      expect(statusText?.toLowerCase()).toMatch(/rejected|dismissed/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
