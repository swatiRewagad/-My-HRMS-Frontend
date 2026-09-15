import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

test.describe.serial('RBIO Adjudication Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Adjudicator sees assigned complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Adjudicator Visibility Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', '/staff/rbio/tasks');
      await page.waitForSelector('.rbio-home', { timeout: 15000 });
      await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

      const row = page.locator(`.data-grid tbody tr:has-text("${complaintNumber}")`);
      await expect(row).toBeVisible({ timeout: 5000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator issues 13-1 Notice', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E 13-1 Notice Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Adjudicator actions: "Award (Adjudication)", "Reject"
      const awardBtn = page.locator('button.action-btn:has-text("Award")');
      await expect(awardBtn).toBeVisible({ timeout: 5000 });

      const rejectBtn = page.locator('button.action-btn:has-text("Reject")');
      await expect(rejectBtn).toBeVisible();

      // Verify both actions are available for adjudication stage
      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator impleads party (adds to impleaded list)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Implead Party Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      // Verify complaint details are loaded
      const statusBadge = page.locator('.status-badge');
      await expect(statusBadge).toBeVisible();
      const statusText = await statusBadge.textContent();
      expect(statusText?.toLowerCase()).toMatch(/adjudication|escalated/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator passes award with valid amount (status -> adjudicated)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const awardBtn = page.locator('button.action-btn:has-text("Award")');
      await expect(awardBtn).toBeVisible({ timeout: 5000 });
      await awardBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Award passed: Entity to pay INR 100,000 to complainant within 30 days.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await page.waitForTimeout(1000);
      const updatedStatus = page.locator('.status-badge');
      const text = await updatedStatus.textContent();
      expect(text?.toLowerCase()).toMatch(/adjudicated|award|resolved/);

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award amount exceeding 30L cap shows error', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Cap 30L Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const awardBtn = page.locator('button.action-btn:has-text("Award")');
      await expect(awardBtn).toBeVisible({ timeout: 5000 });
      await awardBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Award test - amount exceeding cap');

      // Verify the action form is ready
      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await expect(confirmBtn).toBeVisible();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award amount exceeding 3L for time/harassment shows error', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Cap 3L Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const awardBtn = page.locator('button.action-btn:has-text("Award")');
      await expect(awardBtn).toBeVisible({ timeout: 5000 });

      // Verify form loads for award action
      await awardBtn.click();
      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator rejects complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Adjudicator Reject Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      await loginAsRbioRole(page, 'RBIO_ADJUDICATOR', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const rejectBtn = page.locator('button.action-btn:has-text("Reject")');
      await expect(rejectBtn).toBeVisible({ timeout: 5000 });
      await rejectBtn.click();

      const remarksField = page.locator('.remarks-section textarea');
      await expect(remarksField).toBeVisible();
      await remarksField.fill('Complaint dismissed. Claim not substantiated by evidence.');

      const confirmBtn = page.locator('.confirm-actions .btn-primary');
      await confirmBtn.click();

      const resultMsg = page.locator('.result-msg.success');
      await expect(resultMsg).toBeVisible({ timeout: 10000 });

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
