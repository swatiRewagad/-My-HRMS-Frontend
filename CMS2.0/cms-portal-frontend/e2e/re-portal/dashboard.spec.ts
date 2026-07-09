import { test, expect } from '@playwright/test';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';

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
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    const welcome = page.locator('.welcome-text');
    await expect(welcome).toBeVisible();
    expect(page.url()).toContain('/re-portal/dashboard');
  });

  test('Stats cards show (Total, Pending, Responded, Breached)', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    const statsGrid = page.locator('.stats-grid');
    await expect(statsGrid).toBeVisible({ timeout: 10000 });

    await expect(page.locator('.stat-card.total')).toBeVisible();
    await expect(page.locator('.stat-card.pending')).toBeVisible();
    await expect(page.locator('.stat-card.responded')).toBeVisible();
    await expect(page.locator('.stat-card.breached')).toBeVisible();
  });

  test('Complaint table renders forwarded complaints', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const table = page.locator('.complaints-table');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      const headerTexts = await headers.allTextContents();
      const joinedHeaders = headerTexts.join(' ').toLowerCase();
      expect(joinedHeaders).toContain('complaint');
      expect(joinedHeaders).toContain('status');
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('Status filter works', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const filterSelect = page.locator('.status-filter');
    await expect(filterSelect).toBeVisible();

    await filterSelect.selectOption('pending');
    await page.waitForTimeout(500);

    const statusBadges = page.locator('tbody .status-badge');
    const count = await statusBadges.count();
    for (let i = 0; i < count; i++) {
      const text = await statusBadges.nth(i).textContent();
      expect(text?.toLowerCase()).toMatch(/pending/);
    }

    await filterSelect.selectOption('');
  });

  test('Deadline countdown shows for pending complaints', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const table = page.locator('.complaints-table');
    if (await table.isVisible()) {
      const daysRemaining = page.locator('.days-remaining').first();
      if (await daysRemaining.isVisible().catch(() => false)) {
        const text = await daysRemaining.textContent();
        expect(text?.trim().length).toBeGreaterThan(0);
      }
    }
  });

  test('Click navigates to complaint detail', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await page.waitForSelector('.complaints-table, .empty-state', { timeout: 15000 });

    const firstRow = page.locator('.complaint-row').first();
    if (await firstRow.isVisible().catch(() => false)) {
      await firstRow.click();
      await page.waitForURL(/\/re-portal\/complaints\//, { timeout: 10000 });
      expect(page.url()).toMatch(/\/re-portal\/complaints\//);
    }
  });
});
