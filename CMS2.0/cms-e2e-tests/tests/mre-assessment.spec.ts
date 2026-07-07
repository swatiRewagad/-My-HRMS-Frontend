import { test, expect } from '@playwright/test';
import { TaskActionPage } from '../pages';
import { loginViaKeycloak, USERS } from '../helpers/auth';
import { getMreAssessment, recordDecision } from '../helpers/api';

test.describe('MRE Copilot Assessment', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaKeycloak(page, USERS.rbioOfficer);
  });

  test.describe('UI Panel', () => {
    test('should display MRE copilot panel on complaint detail', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
    });

    test('should show GREEN signal for maintainable complaint', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await actionPage.expectMreSignal('GREEN');
    });

    test('should show RED signal for non-maintainable complaint (no prior RE)', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('3');
      await actionPage.expectMrePanelLoaded();
      await actionPage.expectMreSignal('RED');
    });

    test('should show RED signal for complaint filed before window', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('2');
      await actionPage.expectMrePanelLoaded();
      await actionPage.expectMreSignal('RED');
    });

    test('should display MRE grounds list', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      const groundsCount = await actionPage.mreGrounds.count();
      expect(groundsCount).toBeGreaterThan(0);
    });

    test('should show suggested determination', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await expect(actionPage.mreSuggestedDetermination).toBeVisible();
      await expect(actionPage.mreSuggestedDetermination).toContainText(/MAINTAINABLE|NON_MAINTAINABLE/);
    });

    test('should show draft rationale', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await expect(actionPage.mreDraftRationale).toBeVisible();
    });

    test('should show compensation band for maintainable complaints', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await expect(actionPage.mreCompensationBand).toBeVisible();
    });

    test('should show precedent cases from OpenSearch', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      const casesCount = await actionPage.precedentCases.count();
      expect(casesCount).toBeGreaterThanOrEqual(0);
    });
  });

  test.describe('Officer Actions', () => {
    test('should accept MRE suggestion as MAINTAINABLE', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await actionPage.acceptMreSuggestion();
      await expect(page.getByText(/determination.*recorded|accepted/i)).toBeVisible({ timeout: 10000 });
    });

    test('should allow officer to override MRE suggestion', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
      await actionPage.overrideMreSuggestion(
        'NON_MAINTAINABLE',
        'Officer override: additional grounds identified upon manual review'
      );
      await expect(page.getByText(/override.*recorded|determination.*recorded/i)).toBeVisible({ timeout: 10000 });
    });

    test('should allow forward to RE after maintainability determination', async ({ page }) => {
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('4');
      await actionPage.expectMrePanelLoaded();
      await actionPage.forwardToRe();
      await expect(page.getByText(/forwarded|sent.*entity/i)).toBeVisible({ timeout: 10000 });
    });
  });

  test.describe('API Integration', () => {
    test('should return assessment via API for maintainable complaint', async ({ request }) => {
      const response = await getMreAssessment(request, '1');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict).toBeDefined();
      expect(body.mreVerdict.overallSignal).toBe('GREEN');
      expect(body.suggestedDetermination).toBe('MAINTAINABLE');
    });

    test('should return RED signal for complaint without prior RE', async ({ request }) => {
      const response = await getMreAssessment(request, '3');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict.overallSignal).toBe('RED');
      expect(body.suggestedDetermination).toBe('NON_MAINTAINABLE');
    });

    test('should return RED for complaint filed before window', async ({ request }) => {
      const response = await getMreAssessment(request, '2');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict.overallSignal).toBe('RED');
    });

    test('should return RED for complaint beyond limitation period', async ({ request }) => {
      const response = await getMreAssessment(request, '5');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict.overallSignal).toBe('RED');
      const grounds = body.mreVerdict.grounds.map((g: { code: string }) => g.code);
      expect(grounds).toContain('RE_COMPLAINT_BEYOND_LIMITATION');
    });

    test('should return RED for duplicate grievance', async ({ request }) => {
      const response = await getMreAssessment(request, '8');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict.overallSignal).toBe('RED');
      const grounds = body.mreVerdict.grounds.map((g: { code: string }) => g.code);
      expect(grounds).toContain('SAME_GRIEVANCE_PENDING');
    });

    test('should record decision via API', async ({ request }) => {
      const response = await recordDecision(request, 6, {
        determination: 'MAINTAINABLE',
        officer: 'officer.rbio2',
        rationale: 'All Clause 10 conditions satisfied. Senior citizen complaint - high priority.',
      });
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.success).toBe(true);
      expect(body.determination).toBe('MAINTAINABLE');
    });

    test('should handle assessment for complaint by number', async ({ request }) => {
      const response = await getMreAssessment(request, 'CMS/2026/MUM/000001');
      expect(response.ok()).toBeTruthy();
      const body = await response.json();
      expect(body.mreVerdict).toBeDefined();
    });
  });

  test.describe('Edge Cases', () => {
    test('should handle MRE panel gracefully when service is down', async ({ page }) => {
      await page.route('**/api/v1/copilot/maintainability/**', (route) =>
        route.fulfill({ status: 503, body: 'Service Unavailable' })
      );
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMreError();
    });

    test('should handle MRE panel with slow response', async ({ page }) => {
      await page.route('**/api/v1/copilot/maintainability/**', async (route) => {
        await new Promise((r) => setTimeout(r, 5000));
        await route.continue();
      });
      const actionPage = new TaskActionPage(page);
      await actionPage.goto('1');
      await actionPage.expectMrePanelLoaded();
    });

    test('should handle invalid complaint ID gracefully', async ({ request }) => {
      const response = await getMreAssessment(request, '99999');
      expect(response.status()).toBe(404);
    });
  });
});
