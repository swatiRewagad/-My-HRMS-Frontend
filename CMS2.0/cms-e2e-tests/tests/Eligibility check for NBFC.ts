import { test, expect } from '@playwright/test';

test.describe('Eligibility Wizard - Three Passes with Different RE Responses', () => {
  test.setTimeout(90000);

  test('test all three RE response options with Start Over between each', async ({ page }) => {
    // === PASS 1: "No Reply received" ===

    // 1. Navigate to eligibility wizard
    await page.goto('/public/eligibility-wizard', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.wizard-card', { timeout: 20000 });

    // 2. Select "Non-Banking Financial Company (NBFC)"
    await page.locator('.wizard-select').selectOption('NBFC');

    // 3. Click Next
    await page.locator('.btn-wizard-next').click();

    // 4. Select "Yes, I have complained"
    await page.locator('.wizard-radio').filter({ hasText: /yes/i }).click();

    // 5. Click Next
    await page.locator('.btn-wizard-next').click();

    // 6. Select date as current date
    const today = new Date().toISOString().split('T')[0];
    await page.locator('.wizard-date-input').fill(today);

    // 7. Click Next
    await page.locator('.btn-wizard-next').click();

    // 8. Select "No, Reply received" (no reply from entity)
    await page.locator('.wizard-radio').filter({ hasText: /no.*(reply|response)/i }).click();

    // 9. Click "Check Eligibility"
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });
    await expect(page.locator('.outcome-title')).toBeVisible();

    // 10. Click "Start Over"
    await page.locator('button').filter({ hasText: /start over/i }).click();
    await expect(page.locator('.wizard-select')).toBeVisible({ timeout: 10000 });

    // === PASS 2: "Replied but I am not satisfied" ===

    // Steps 1-2
    await page.locator('.wizard-select').selectOption('NBFC');

    // Step 3
    await page.locator('.btn-wizard-next').click();

    // Step 4
    await page.locator('.wizard-radio').filter({ hasText: /yes/i }).click();

    // Step 5
    await page.locator('.btn-wizard-next').click();

    // Step 6
    await page.locator('.wizard-date-input').fill(today);

    // Step 7
    await page.locator('.btn-wizard-next').click();

    // Step 8 - Select "Replied but I am not satisfied"
    await page.locator('.wizard-radio').filter({ hasText: /not satisfied|dissatisfied/i }).click();

    // Step 9
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });
    await expect(page.locator('.outcome-title')).toBeVisible();

    // 12. Click "Start Over"
    await page.locator('button').filter({ hasText: /start over/i }).click();
    await expect(page.locator('.wizard-select')).toBeVisible({ timeout: 10000 });

    // === PASS 3: "Replied and issue is resolved" ===

    // Steps 1-2
    await page.locator('.wizard-select').selectOption('NBFC');

    // Step 3
    await page.locator('.btn-wizard-next').click();

    // Step 4
    await page.locator('.wizard-radio').filter({ hasText: /yes/i }).click();

    // Step 5
    await page.locator('.btn-wizard-next').click();

    // Step 6
    await page.locator('.wizard-date-input').fill(today);

    // Step 7
    await page.locator('.btn-wizard-next').click();

    // Step 8 - Select "Replied and issue is resolved"
    await page.locator('.wizard-radio').filter({ hasText: /resolved/i }).click();

    // Step 9
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });
    await expect(page.locator('.outcome-title')).toBeVisible();
  });
});
