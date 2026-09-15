import { test, expect } from '@playwright/test';

test.describe('Eligibility Wizard - Full Flow with OTP Verification', () => {
  test.setTimeout(360000);

  test('complete wizard, login with OTP, and proceed to file complaint', async ({ page }) => {
    // 1. Navigate to eligibility wizard
    await page.goto('/public/eligibility-wizard', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.wizard-card', { timeout: 20000 });

    // 2. Select "Commercial Bank" from entity type dropdown
    await page.locator('.wizard-select').selectOption('BANK');

    // 3. Click Next
    await page.locator('.btn-wizard-next').click();

    // 4. Select radio button "Yes, I have complained"
    await page.locator('.wizard-radio').filter({ hasText: /yes/i }).click();
    await page.locator('.btn-wizard-next').click();

    // 5. Select date as current date
    const today = new Date().toISOString().split('T')[0];
    await page.locator('.wizard-date-input').fill(today);

    // 6. Click Next
    await page.locator('.btn-wizard-next').click();

    // 7. Select radio button "Replied but I am not satisfied"
    await page.locator('.wizard-radio').filter({ hasText: /not satisfied|dissatisfied/i }).click();

    // 8. Click "Check Eligibility" button
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });

    // 9. Click "Proceed to file complaint"
    await page.locator('.btn-wizard-primary').click();
    await page.waitForSelector('.verify-card', { timeout: 15000 });

    // 10. Enter mobile number
    await page.locator('#mobile-input').fill('8369945118');

    // 11. Pause for user to enter captcha manually, then click Resume in Playwright Inspector
    await page.pause();

    // 12. Click "Send OTP" button
    await page.locator('.send-otp-btn').filter({ hasText: /send otp/i }).click();

    // 13. Wait for OTP input to appear, then wait 1 minute for user to enter OTP
    await page.waitForSelector('.otp-inputs', { timeout: 30000 });
    await page.waitForTimeout(60000);

    // 14. Click "Verify" button
    await page.locator('.send-otp-btn').filter({ hasText: /verify/i }).click();
  });
});
