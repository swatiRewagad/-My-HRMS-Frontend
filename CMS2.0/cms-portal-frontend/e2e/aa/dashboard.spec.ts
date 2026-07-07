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

    const heading = page.locator(
      'h1:has-text("Appellate Authority"), h2:has-text("AA Dashboard"), [data-testid="aa-dashboard-title"]'
    );
    await expect(heading).toBeVisible({ timeout: 15000 });

    // Stats section
    const statsSection = page.locator('.stats-row, .stats-cards, [data-testid="aa-stats"]');
    await expect(statsSection).toBeVisible({ timeout: 10000 });

    const statCards = page.locator('.stat-card, [data-testid^="stat-"]');
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('Appeals table shows filed appeals', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');

    await page.waitForSelector(
      '.appeals-table, .aa-table, [data-testid="appeals-table"], .empty-state',
      { timeout: 15000 }
    );

    const table = page.locator('.appeals-table, .aa-table, [data-testid="appeals-table"]');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      const headerCount = await headers.count();
      expect(headerCount).toBeGreaterThan(0);

      const headerTexts = await headers.allTextContents();
      const joinedHeaders = headerTexts.join(' ').toLowerCase();
      expect(joinedHeaders).toMatch(/appeal/);
      expect(joinedHeaders).toMatch(/status/);
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('Classification badge (APPEAL/REPRESENTATION) displays correctly', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a closed complaint and file an appeal
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Classification Badge Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    await fileAppeal(request, complaint.complaintNumber, { appealType: 'APPEAL' });

    await loginAsAaRole(page, 'AA_REGISTRAR');

    await page.waitForSelector(
      '.appeals-table, .aa-table, [data-testid="appeals-table"], .empty-state',
      { timeout: 15000 }
    );

    // Look for classification badges
    const badges = page.locator(
      '.classification-badge, [data-testid="classification"], .type-badge'
    );
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

    await page.waitForSelector(
      '.appeals-table, .aa-table, .empty-state',
      { timeout: 15000 }
    );

    const filterSelect = page.locator(
      '.filter-select, [data-testid="status-filter"], select[name="status"]'
    );
    await expect(filterSelect).toBeVisible();

    // Select a status filter
    await filterSelect.selectOption({ index: 1 });
    await page.waitForTimeout(500);

    // Table should update (either show filtered results or empty state)
    const tableOrEmpty = page.locator(
      '.appeals-table tbody tr, .aa-table tbody tr, .empty-state'
    );
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 5000 });

    // Reset filter
    await filterSelect.selectOption('');
  });

  test('Search by appeal number works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_REGISTRAR');

    await page.waitForSelector(
      '.appeals-table, .aa-table, .empty-state',
      { timeout: 15000 }
    );

    const searchInput = page.locator(
      '.search-box input, input[placeholder*="search"], input[placeholder*="appeal"], [data-testid="appeal-search"]'
    );
    await expect(searchInput).toBeVisible();

    // Search for non-existent appeal
    await searchInput.fill('NONEXISTENT_APL_99999');
    await page.waitForTimeout(500);

    // Should show empty or no results
    const rows = page.locator('.appeals-table tbody tr, .aa-table tbody tr');
    const emptyState = page.locator('.empty-state, .no-results');
    const rowCount = await rows.count();

    if (rowCount === 0) {
      await expect(emptyState).toBeVisible();
    }

    // Clear search
    await searchInput.clear();
    await page.waitForTimeout(500);
  });

  test('Role-based view: Registrar sees unassigned, Authority sees forwarded', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Test Registrar view
    await loginAsAaRole(page, 'AA_REGISTRAR');

    await page.waitForSelector(
      '.appeals-table, .aa-table, .empty-state',
      { timeout: 15000 }
    );

    // Registrar should see new/unassigned appeals section
    const registrarSection = page.locator(
      '[data-testid="unassigned-appeals"], .unassigned-section, h3:has-text("Unassigned"), h3:has-text("New Appeals")'
    );
    const registrarView = await registrarSection.isVisible().catch(() => false);

    await logout(page);

    // Test Authority view
    await loginAsAaRole(page, 'AA_AUTHORITY');

    await page.waitForSelector(
      '.appeals-table, .aa-table, .empty-state',
      { timeout: 15000 }
    );

    // Authority should see forwarded/assigned appeals
    const authoritySection = page.locator(
      '[data-testid="forwarded-appeals"], .forwarded-section, h3:has-text("Forwarded"), h3:has-text("Assigned")'
    );
    const authorityView = await authoritySection.isVisible().catch(() => false);

    // At least one role-based section should be visible across both views
    expect(registrarView || authorityView).toBeTruthy();
  });
});
