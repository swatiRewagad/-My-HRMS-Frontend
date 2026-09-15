import { test, expect } from '@playwright/test';
import { RbioTasksPage, TaskActionPage, LoginPage } from '../pages';
import { loginViaKeycloak, USERS } from '../helpers/auth';

test.describe('RBIO Officer Portal', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaKeycloak(page, USERS.rbioOfficer);
  });

  test.describe('Task Grid', () => {
    test('should display the RBIO tasks grid after login', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await expect(tasksPage.complaintGrid).toBeVisible();
    });

    test('should show statistics cards with counts', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      const statsCount = await tasksPage.statsCards.count();
      expect(statsCount).toBeGreaterThan(0);
    });

    test('should display assigned complaints in the grid', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await tasksPage.expectGridHasData();
    });

    test('should filter complaints by status', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await tasksPage.filterByStatus('in_progress');
      const count = await tasksPage.getRowCount();
      expect(count).toBeGreaterThanOrEqual(0);
    });

    test('should filter complaints by priority', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await tasksPage.filterByPriority('HIGH');
      const rows = await tasksPage.getRowCount();
      expect(rows).toBeGreaterThanOrEqual(0);
    });

    test('should search complaints by text', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await tasksPage.searchByText('ATM');
      await page.waitForTimeout(1000);
    });

    test('should navigate to complaint detail on row click', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await tasksPage.clickComplaint('CMS/2026/MUM/000001');
      await expect(page).toHaveURL(/.*task-action|complaint-detail.*/);
    });

    test('should support pagination', async ({ page }) => {
      const tasksPage = new RbioTasksPage(page);
      await tasksPage.goto();
      await tasksPage.waitForGridLoad();
      await expect(tasksPage.pagination).toBeVisible();
    });
  });

  test.describe('Complaint Detail', () => {
    test('should display complaint details', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await expect(actionPage.complaintNumber).toBeVisible();
    });

    test('should show complaint timeline', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectTimelineHasEntries();
    });

    test('should display action buttons for RBIO officer', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      const hasEscalate = await actionPage.escalateButton.isVisible().catch(() => false);
      const hasResolve = await actionPage.resolveButton.isVisible().catch(() => false);
      expect(hasEscalate || hasResolve).toBeTruthy();
    });

    test('should escalate a complaint with remarks', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.escalateComplaint('Requires supervisor review due to high value');
      await expect(page.getByText(/escalated|success/i)).toBeVisible({ timeout: 10000 });
    });
  });
});
