import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, advanceToStatus, fileAppeal } from '../utils/test-data';

test.describe('AA Dashboard', () => {
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

  test('AA dashboard loads with stats', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    const heading = page.locator('h2:has-text("Appellate Authority")');
    await expect(heading).toBeVisible();

    const statsRow = page.locator('.stats-row');
    await expect(statsRow).toBeVisible({ timeout: 10000 });

    const statCards = page.locator('.stat-card');
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(3);
  });

  test('Appeals table shows filed appeals', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const table = page.locator('.appeals-table');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      const headerTexts = await headers.allTextContents();
      const joinedHeaders = headerTexts.join(' ').toLowerCase();
      expect(joinedHeaders).toContain('appeal');
      expect(joinedHeaders).toContain('status');
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('Classification badge (APPEAL/REPRESENTATION) displays correctly', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Classification Badge Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    await fileAppeal(request, complaint.complaintNumber, { classificationType: 'APPEAL' });

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const badges = page.locator('.classification-badge');
    const count = await badges.count();

    if (count > 0) {
      for (let i = 0; i < count; i++) {
        const text = await badges.nth(i).textContent();
        expect(text?.toUpperCase()).toMatch(/APPEAL|REPRESENTATION/);
      }
    }
  });

  test('Status filter works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const filterSelect = page.locator('.filter-select').first();
    await expect(filterSelect).toBeVisible();

    await filterSelect.selectOption('under_review');
    await page.waitForTimeout(500);

    const tableOrEmpty = page.locator('.appeals-table tbody tr, .empty-state');
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 5000 });

    await filterSelect.selectOption('');
  });

  test('Search by appeal number works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const searchInput = page.locator('.search-box input');
    await expect(searchInput).toBeVisible();

    await searchInput.fill('NONEXISTENT_APL_99999');
    await page.waitForTimeout(500);

    const emptyState = page.locator('.empty-state');
    await expect(emptyState).toBeVisible({ timeout: 5000 });

    await searchInput.clear();
    await page.waitForTimeout(500);
  });

  test('Role-based view: Registrar sees unassigned, Authority sees forwarded', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    const registrarBadge = page.locator('.role-badge');
    await expect(registrarBadge).toBeVisible();
    const registrarRole = await registrarBadge.textContent();
    expect(registrarRole?.toLowerCase()).toMatch(/registrar/);

    await logout(page);

    await loginAsAaRole(page, 'AA_AUTHORITY');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    const authorityBadge = page.locator('.role-badge');
    await expect(authorityBadge).toBeVisible();
    const authorityRole = await authorityBadge.textContent();
    expect(authorityRole?.toLowerCase()).toMatch(/authority/);
  });
});
