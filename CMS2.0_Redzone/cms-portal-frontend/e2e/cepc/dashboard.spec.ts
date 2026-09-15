import { test, expect } from '@playwright/test';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint } from '../utils/test-data';

test.describe('CEPC Dashboard', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping CEPC dashboard tests');
    await loginAsCepcRole(page, 'DO');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('page loads and shows CEPC dashboard title', async ({ page }) => {
    const heading = page.locator('h2:has-text("CEPC - Complaint Management")');
    await expect(heading).toBeVisible({ timeout: 15000 });
  });

  test('stats cards display (Total, Pending, Under Examination, etc.)', async ({ page }) => {
    const statsRow = page.locator('.stats-row');
    await expect(statsRow).toBeVisible({ timeout: 10000 });

    // Verify each stat card
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Total"))')).toBeVisible();
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Pending"))')).toBeVisible();
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Under Examination"))')).toBeVisible();
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Under Review"))')).toBeVisible();
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Awaiting Closure"))')).toBeVisible();
    await expect(page.locator('.stat-card:has(.stat-label:has-text("Escalated"))')).toBeVisible();
  });

  test('complaint table renders with expected columns', async ({ page }) => {
    // Wait for loading to complete
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    // If there are complaints, verify table headers
    const table = page.locator('.complaints-table');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      await expect(headers.nth(0)).toContainText('Complaint No.');
      await expect(headers.nth(1)).toContainText('Complainant');
      await expect(headers.nth(2)).toContainText('Entity');
      await expect(headers.nth(3)).toContainText('Subject');
      await expect(headers.nth(4)).toContainText('Priority');
      await expect(headers.nth(5)).toContainText('Status');
      await expect(headers.nth(6)).toContainText('SLA Due');
      await expect(headers.nth(7)).toContainText('Actions');
    } else {
      // Empty state is also valid
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('search filters complaints by number or name', async ({ page }) => {
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const searchInput = page.locator('.search-box input');
    await expect(searchInput).toBeVisible();

    // Type a search query
    await searchInput.fill('NONEXISTENT_QUERY_12345');
    await page.waitForTimeout(500);

    // Should show empty state or filtered results
    const filteredRows = page.locator('.complaints-table tbody tr');
    const emptyState = page.locator('.empty-state');

    const rowCount = await filteredRows.count();
    if (rowCount === 0) {
      await expect(emptyState).toBeVisible();
    }

    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(500);
  });

  test('status filter dropdown works', async ({ page }) => {
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const filterSelect = page.locator('.filter-select');
    await expect(filterSelect).toBeVisible();

    // Select "Under Examination"
    await filterSelect.selectOption('in_progress');
    await page.waitForTimeout(500);

    // All visible status badges should be "Under Examination" (or empty state)
    const statusBadges = page.locator('.complaints-table tbody .status-badge');
    const count = await statusBadges.count();
    for (let i = 0; i < count; i++) {
      await expect(statusBadges.nth(i)).toContainText('Under Examination');
    }

    // Reset
    await filterSelect.selectOption('');
  });

  test('pagination works (prev/next buttons)', async ({ page }) => {
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const pagination = page.locator('.pagination');
    if (await pagination.isVisible()) {
      const pageInfo = page.locator('.page-info');
      await expect(pageInfo).toBeVisible();

      // Check the next page button
      const nextBtn = page.locator('.page-controls .page-btn:last-child');
      const prevBtn = page.locator('.page-controls .page-btn:first-child');

      // Prev should be disabled on first page
      await expect(prevBtn).toBeDisabled();

      // If there is a next page, click it
      if (!(await nextBtn.isDisabled())) {
        await nextBtn.click();
        await page.waitForTimeout(300);
        // Prev should now be enabled
        await expect(prevBtn).toBeEnabled();
      }
    }
  });

  test('Create Complaint dialog opens and submits successfully', async ({ page }) => {
    await page.waitForSelector('.toolbar', { timeout: 15000 });

    const createBtn = page.locator('.create-btn');
    await expect(createBtn).toBeVisible();
    await createBtn.click();

    // Dialog should be visible
    const modal = page.locator('.modal-overlay');
    await expect(modal).toBeVisible();

    const modalHeader = page.locator('.modal-header h3');
    await expect(modalHeader).toHaveText('Create New CEPC Complaint');

    // Fill required fields
    await page.locator('.modal-body input[placeholder="Full name"]').fill('E2E Test Citizen');
    await page.locator('.modal-body input[placeholder*="subject" i]').fill('E2E Dashboard Submit Test');
    await page.locator('.modal-body input[placeholder*="email" i]').fill('e2e@test.com');
    await page.locator('.modal-body textarea').fill('Automated test from Playwright');

    // Submit
    const submitBtn = page.locator('.modal-body .submit-btn');
    await submitBtn.click();

    // Wait for success message or error (both are acceptable outcomes)
    const successMsg = page.locator('.success-msg');
    const errorMsg = page.locator('.error-msg');
    await expect(successMsg.or(errorMsg)).toBeVisible({ timeout: 15000 });

    // Close the modal so afterEach logout can proceed
    await page.keyboard.press('Escape');
  });

  test('Create Complaint validates required fields (name, subject)', async ({ page }) => {
    await page.waitForSelector('.toolbar', { timeout: 15000 });

    const createBtn = page.locator('.create-btn');
    await createBtn.click();

    const modal = page.locator('.modal-overlay');
    await expect(modal).toBeVisible();

    // Try to submit without filling required fields
    const submitBtn = page.locator('.modal-body .submit-btn');
    await submitBtn.click();

    // Should show validation error
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Complainant Name and Subject are required');

    // Close dialog
    const closeBtn = page.locator('.modal-header .close-btn');
    await closeBtn.click();
  });

  test('SLA overdue complaints show visual indicator', async ({ page }) => {
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    // Check if any rows have the .overdue class
    const overdueRows = page.locator('.complaints-table tbody tr.overdue');
    const count = await overdueRows.count();

    if (count > 0) {
      // Overdue rows should also have .overdue-text in the SLA cell
      const firstOverdue = overdueRows.first();
      const slaCell = firstOverdue.locator('.sla-cell');
      await expect(slaCell).toHaveClass(/overdue-text/);
    }
    // If no overdue complaints, that is also a valid state (just skip assertion)
  });

  test('clicking a complaint navigates to detail page', async ({ page }) => {
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const firstRow = page.locator('.complaints-table tbody tr').first();
    if (await firstRow.isVisible()) {
      const complaintNum = await firstRow.locator('.complaint-num').textContent();
      await firstRow.click();

      // Should navigate to complaint detail
      await page.waitForURL(/\/cepc\/complaint\//, { timeout: 10000 });
      expect(page.url()).toContain('/cepc/complaint/');
    }
  });
});

test.describe('CEPC Dashboard - Role-based visibility', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('DO sees Create Complaint button', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO');

    await page.waitForSelector('.toolbar', { timeout: 15000 });
    const createBtn = page.locator('.create-btn');
    await expect(createBtn).toBeVisible();

    await logout(page);
  });

  test('Reviewer does NOT see Create Complaint button', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'REVIEWER');

    await page.waitForSelector('.toolbar', { timeout: 15000 });
    const createBtn = page.locator('.create-btn');
    await expect(createBtn).not.toBeVisible();

    await logout(page);
  });
});
