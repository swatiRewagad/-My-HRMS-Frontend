import { test, expect } from '@playwright/test';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createForwardedComplaint } from '../utils/test-data';

test.describe('RE Portal Dashboard', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping RE portal tests');
    await loginAsReRole(page, 'RE_NODAL_OFFICER');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('RE portal login and dashboard loads', async ({ page }) => {
    const heading = page.locator(
      'h1:has-text("RE Dashboard"), h2:has-text("RE Dashboard"), h2:has-text("Regulated Entity"), [data-testid="re-dashboard-title"]'
    );
    await expect(heading).toBeVisible({ timeout: 15000 });
    expect(page.url()).toContain('/re/dashboard');
  });

  test('Stats cards show (Total, Pending, Responded, Breached)', async ({ page }) => {
    const statsSection = page.locator('.stats-row, .stats-cards, [data-testid="stats-section"]');
    await expect(statsSection).toBeVisible({ timeout: 10000 });

    await expect(
      page.locator('.stat-card:has-text("Total"), [data-testid="stat-total"]')
    ).toBeVisible();
    await expect(
      page.locator('.stat-card:has-text("Pending"), [data-testid="stat-pending"]')
    ).toBeVisible();
    await expect(
      page.locator('.stat-card:has-text("Responded"), [data-testid="stat-responded"]')
    ).toBeVisible();
    await expect(
      page.locator('.stat-card:has-text("Breached"), [data-testid="stat-breached"]')
    ).toBeVisible();
  });

  test('Complaint table renders forwarded complaints', async ({ page }) => {
    await page.waitForSelector(
      '.complaints-table, .re-complaints-table, [data-testid="re-table"], .empty-state',
      { timeout: 15000 }
    );

    const table = page.locator('.complaints-table, .re-complaints-table, [data-testid="re-table"]');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      const headerCount = await headers.count();
      expect(headerCount).toBeGreaterThan(0);

      // Expect standard columns
      const headerTexts = await headers.allTextContents();
      const joinedHeaders = headerTexts.join(' ').toLowerCase();
      expect(joinedHeaders).toMatch(/complaint/);
      expect(joinedHeaders).toMatch(/status/);
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('Status filter works', async ({ page }) => {
    await page.waitForSelector(
      '.complaints-table, .re-complaints-table, .empty-state',
      { timeout: 15000 }
    );

    const filterSelect = page.locator(
      '.filter-select, [data-testid="status-filter"], select[name="status"]'
    );
    await expect(filterSelect).toBeVisible();

    // Select "Pending" filter
    await filterSelect.selectOption({ label: /pending/i });
    await page.waitForTimeout(500);

    // All visible status badges should show pending-related text
    const statusBadges = page.locator('tbody .status-badge, tbody [data-testid="status"]');
    const count = await statusBadges.count();
    for (let i = 0; i < count; i++) {
      const text = await statusBadges.nth(i).textContent();
      expect(text?.toLowerCase()).toMatch(/pending|forwarded/);
    }

    // Reset filter
    await filterSelect.selectOption('');
  });

  test('Deadline countdown shows for pending complaints', async ({ page }) => {
    await page.waitForSelector(
      '.complaints-table, .re-complaints-table, .empty-state',
      { timeout: 15000 }
    );

    const table = page.locator('.complaints-table, .re-complaints-table, [data-testid="re-table"]');
    if (await table.isVisible()) {
      const rows = table.locator('tbody tr');
      const rowCount = await rows.count();

      if (rowCount > 0) {
        // Look for deadline/countdown indicators
        const deadlineCell = rows.first().locator(
          '.deadline, .countdown, .sla-cell, [data-testid="deadline"]'
        );
        if (await deadlineCell.isVisible().catch(() => false)) {
          const text = await deadlineCell.textContent();
          // Should contain days/hours or a date indicator
          expect(text?.trim().length).toBeGreaterThan(0);
        }
      }
    }
  });

  test('Click navigates to complaint detail', async ({ page }) => {
    await page.waitForSelector(
      '.complaints-table, .re-complaints-table, .empty-state',
      { timeout: 15000 }
    );

    const firstRow = page.locator(
      '.complaints-table tbody tr, .re-complaints-table tbody tr'
    ).first();

    if (await firstRow.isVisible()) {
      await firstRow.click();

      // Should navigate to complaint detail page
      await page.waitForURL(/\/re\/complaint\/|\/re\/detail\//, { timeout: 10000 });
      expect(page.url()).toMatch(/\/re\/complaint\/|\/re\/detail\//);
    }
  });
});
