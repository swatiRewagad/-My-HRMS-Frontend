import { test, expect } from '@playwright/test';

test.describe('Launch and Navigate to Public Portal 2', () => {
  test.setTimeout(60000);

  test('landing page loads and Public Portal 2 card is visible', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    const portal2Card = page.locator('.portal2-card');
    await expect(portal2Card).toBeVisible({ timeout: 30000 });
    await expect(portal2Card.locator('h3')).toHaveText('Public Portal 2');
  });

  test('clicking Public Portal 2 card navigates to eligibility wizard', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    const portal2Card = page.locator('.portal2-card');
    await expect(portal2Card).toBeVisible({ timeout: 30000 });
    await portal2Card.click();
    await expect(page).toHaveURL(/\/public\/eligibility-wizard/, { timeout: 15000 });
  });

  test('eligibility wizard page loads after navigation from landing', async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    const portal2Card = page.locator('.portal2-card');
    await expect(portal2Card).toBeVisible({ timeout: 30000 });
    await portal2Card.click();
    await expect(page).toHaveURL(/\/public\/eligibility-wizard/, { timeout: 15000 });
    const wizardCard = page.locator('.wizard-card');
    await expect(wizardCard).toBeVisible({ timeout: 15000 });
    const header = page.locator('.wizard-header');
    await expect(header).toBeVisible();
  });
});
