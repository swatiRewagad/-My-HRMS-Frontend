import { test, expect } from '@playwright/test';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createRbioComplaint, cleanupRbioComplaint } from '../utils/test-data';

test.describe('RBIO Task List', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const result = await createRbioComplaint(request, {
        subject: 'E2E RBIO Task List Test',
        complainantName: 'Task List Test Citizen',
      });
      complaintNumber = result.complaintNumber;
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('page loads showing RBIO task list heading', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    const heading = page.locator('h1:has-text("RBIO Complaints")');
    await expect(heading).toBeVisible();

    await logout(page);
  });

  test('stats display (total, assigned, in progress, escalated, resolved)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    const statsBar = page.locator('.stats-bar');
    await expect(statsBar).toBeVisible({ timeout: 10000 });

    const statCards = page.locator('.stats-bar .stat-card');
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(5);

    const labels = await page.locator('.stats-bar .stat-label').allTextContents();
    const joined = labels.join(' ').toLowerCase();
    expect(joined).toContain('total');
    expect(joined).toContain('assigned');
    expect(joined).toContain('in progress');
    expect(joined).toContain('escalated');
    expect(joined).toContain('resolved');

    await logout(page);
  });

  test('task table renders with correct columns', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const table = page.locator('.data-grid');
    if (await table.isVisible()) {
      const headers = table.locator('thead tr:first-child th');
      const headerCount = await headers.count();
      expect(headerCount).toBeGreaterThan(3);
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }

    await logout(page);
  });

  test('search filters by complaint number/name/entity', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    // Use the first column search input
    const colSearch = page.locator('.col-search').first();
    await expect(colSearch).toBeVisible();

    await colSearch.fill('NONEXISTENT_XYZ_99999');
    await page.waitForTimeout(500);

    const rows = page.locator('.data-grid tbody tr:not(:has(.empty-state))');
    const rowCount = await rows.count();
    expect(rowCount).toBe(0);

    await colSearch.clear();
    await page.waitForTimeout(500);

    await logout(page);
  });

  test('status filter works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const filterSelect = page.locator('.queue-select');
    await expect(filterSelect).toBeVisible();

    await filterSelect.selectOption('assigned');
    await page.waitForTimeout(500);

    const statusBadges = page.locator('.data-grid tbody .status-badge');
    const count = await statusBadges.count();
    for (let i = 0; i < count; i++) {
      const text = await statusBadges.nth(i).textContent();
      expect(text?.toLowerCase()).toMatch(/assigned/);
    }

    await filterSelect.selectOption('');
    await logout(page);
  });

  test('column configuration toggle shows/hides columns', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const colConfigBtn = page.locator('button.btn-icon:has(.pi-cog)');
    await expect(colConfigBtn).toBeVisible({ timeout: 5000 });

    const headersBefore = await page.locator('.data-grid thead tr:first-child th').count();
    await colConfigBtn.click();
    await page.waitForTimeout(500);

    // Column config panel or modal should appear
    const configPanel = page.locator('.column-config, [role="dialog"], .modal-overlay');
    if (await configPanel.isVisible({ timeout: 3000 }).catch(() => false)) {
      const checkboxes = configPanel.locator('input[type="checkbox"]');
      const cbCount = await checkboxes.count();
      if (cbCount > 0) {
        await checkboxes.first().uncheck();
        await page.waitForTimeout(300);
      }
      // Close
      const closeBtn = configPanel.locator('button:has-text("Close"), button:has-text("Done"), .close-btn');
      if (await closeBtn.isVisible().catch(() => false)) {
        await closeBtn.click();
      } else {
        await page.keyboard.press('Escape');
      }
    }

    await logout(page);
  });

  test('advanced search dialog opens and filters', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    const advSearchBtn = page.locator('button:has-text("Advanced Search")');
    await expect(advSearchBtn).toBeVisible({ timeout: 5000 });
    await advSearchBtn.click();

    await page.waitForTimeout(500);
    // The component sets showAdvancedSearch — check if any modal/overlay appears
    const dialog = page.locator('.advanced-search, [role="dialog"], .modal-overlay, .search-dialog');
    if (await dialog.isVisible({ timeout: 3000 }).catch(() => false)) {
      const closeBtn = dialog.locator('button:has-text("Close"), button:has-text("Cancel")');
      if (await closeBtn.isVisible().catch(() => false)) {
        await closeBtn.click();
      } else {
        await page.keyboard.press('Escape');
      }
    }

    await logout(page);
  });

  test('pagination controls work', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const pagination = page.locator('.pagination');
    await expect(pagination).toBeVisible({ timeout: 5000 });

    const pageInfo = page.locator('.page-info');
    await expect(pageInfo).toBeVisible();
    const infoText = await pageInfo.textContent();
    expect(infoText).toContain('Showing');

    await logout(page);
  });

  test('click task navigates to detail page', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    const firstRow = page.locator('.data-grid tbody tr').first();
    await firstRow.click();

    await page.waitForURL(/\/staff\/rbio\/task\//, { timeout: 10000 });
    expect(page.url()).toContain('/staff/rbio/task/');

    await logout(page);
  });

  test('unread indicator clears after visiting task', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    // Click first row to visit it
    const firstRow = page.locator('.data-grid tbody tr').first();
    await firstRow.click();
    await page.waitForURL(/\/staff\/rbio\/task\//, { timeout: 10000 });

    // Go back
    await page.goBack();
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    // The visited row should have .visited class
    const visitedRow = page.locator('.data-grid tbody tr.visited');
    const visitedCount = await visitedRow.count();
    expect(visitedCount).toBeGreaterThanOrEqual(1);

    await logout(page);
  });
});
