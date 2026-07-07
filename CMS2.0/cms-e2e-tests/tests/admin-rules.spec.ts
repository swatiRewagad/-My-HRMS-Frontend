import { test, expect } from '@playwright/test';
import { AdminRulesPage } from '../pages';
import { loginViaKeycloak, USERS } from '../helpers/auth';

test.describe('Admin Rules Management', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaKeycloak(page, USERS.admin);
  });

  test.describe('Rules List', () => {
    test('should display rules management page', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
    });

    test('should list all seeded rules', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      const count = await rulesPage.getRuleCount();
      expect(count).toBeGreaterThan(0);
    });

    test('should filter rules by category - MAINTAINABILITY', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.filterByCategory('MAINTAINABILITY');
      await rulesPage.expectRuleInList('MRE-001');
    });

    test('should filter rules by category - ASSIGNMENT', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.filterByCategory('ASSIGNMENT');
      await rulesPage.expectRuleInList('ASGN-001');
    });

    test('should filter rules by status - ACTIVE', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.filterByStatus('ACTIVE');
      const count = await rulesPage.getRuleCount();
      expect(count).toBeGreaterThan(0);
    });

    test('should search rules by name', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.searchRule('entity coverage');
      await rulesPage.expectRuleInList('MRE-001');
    });
  });

  test.describe('Rule Detail', () => {
    test('should open rule editor on click', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.clickRule('MRE-001');
      await expect(rulesPage.ruleEditor).toBeVisible();
    });

    test('should display DRL content in editor', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.clickRule('MRE-001');
      await expect(rulesPage.drlEditor).toBeVisible();
      await expect(rulesPage.drlEditor).toContainText(/rule.*Entity coverage/);
    });

    test('should show rule metadata (salience, version, status)', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.clickRule('MRE-001');
      await expect(page.getByText(/salience|priority/i)).toBeVisible();
      await expect(page.getByText(/version/i)).toBeVisible();
      await expect(page.getByText(/ACTIVE/)).toBeVisible();
    });
  });

  test.describe('Rule Deployment', () => {
    test('should have deploy button on admin page', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await expect(rulesPage.deployButton).toBeVisible();
    });

    test('should deploy rules successfully', async ({ page }) => {
      const rulesPage = new AdminRulesPage(page);
      await rulesPage.goto();
      await rulesPage.waitForRulesLoad();
      await rulesPage.deployRules();
    });
  });

  test.describe('Access Control', () => {
    test('should deny non-admin users from rules page', async ({ page }) => {
      await page.context().clearCookies();
      await loginViaKeycloak(page, USERS.rbioOfficer);
      await page.goto('/admin/rules');
      await expect(page.getByText(/unauthorized|forbidden|access denied/i)).toBeVisible();
    });
  });
});
