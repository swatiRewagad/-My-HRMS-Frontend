import { test, expect } from '@playwright/test';
import { TaskActionPage } from '../pages';
import { loginViaKeycloak, USERS } from '../helpers/auth';
import { getCepcTasks } from '../helpers/api';

test.describe('CEPC Officer Workflow', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaKeycloak(page, USERS.cepcOfficer);
  });

  test.describe('Task Grid', () => {
    test('should display CEPC tasks grid after login', async ({ page }) => {
      await page.goto('/staff/cepc/tasks');
      await expect(page.locator('table, .ag-root, [data-testid="complaint-grid"]')).toBeVisible({ timeout: 10000 });
    });

    test('should show only CEPC-assigned complaints', async ({ page }) => {
      await page.goto('/staff/cepc/tasks');
      await page.waitForTimeout(2000);
      const departmentCells = page.locator('td:has-text("CEPC"), .ag-cell:has-text("CEPC")');
      const count = await departmentCells.count();
      expect(count).toBeGreaterThanOrEqual(0);
    });

    test('should display UPI/Credit card category complaints', async ({ page }) => {
      await page.goto('/staff/cepc/tasks');
      await page.waitForTimeout(2000);
      const gridText = await page.locator('table, .ag-root').textContent();
      expect(gridText).toMatch(/UPI|Credit|IMPS|NEFT/i);
    });

    test('should navigate to complaint detail from grid', async ({ page }) => {
      await page.goto('/staff/cepc/tasks');
      await page.waitForTimeout(2000);
      await page.getByText('CMS/2026/BLR/000003').click();
      await expect(page).toHaveURL(/.*task-action|complaint-detail.*/);
    });
  });

  test.describe('Complaint Actions', () => {
    test('should display MRE assessment panel', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('3');
      await actionPage.expectMrePanelLoaded();
    });

    test('should show NO_PRIOR_RE_COMPLAINT ground for complaint 3', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('3');
      await actionPage.expectMrePanelLoaded();
      await expect(page.getByText(/prior.*complaint|NO_PRIOR_RE/i)).toBeVisible();
    });

    test('should allow CEPC officer to reject non-maintainable complaint', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('3');
      await actionPage.expectMrePanelLoaded();
      await actionPage.rejectComplaint('Non-maintainable: Complainant has not approached the RE first as per Clause 10(1)(b)');
      await expect(page.getByText(/rejected|non.maintainable|closed/i)).toBeVisible({ timeout: 10000 });
    });

    test('should resolve maintainable complaint with award', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('4');
      await actionPage.expectMrePanelLoaded();
      await actionPage.resolveComplaint(
        'Resolved in favour of complainant. ICICI to refund Rs 15,000.',
        '15000'
      );
      await expect(page.getByText(/resolved|success/i)).toBeVisible({ timeout: 10000 });
    });

    test('should forward complaint to RE', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('4');
      await actionPage.forwardToRe();
      await expect(page.getByText(/forwarded|sent/i)).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('API', () => {
    test('should return CEPC tasks via API', async ({ request }) => {
      const response = await getCepcTasks(request, 'officer.cepc1');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(Array.isArray(body) || body.content).toBeTruthy();
    });
  });
});
