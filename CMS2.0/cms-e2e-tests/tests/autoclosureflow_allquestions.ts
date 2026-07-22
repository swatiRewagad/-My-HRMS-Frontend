import { test, expect } from '@playwright/test';

test.describe('Auto Closure Flow - All Eligibility Questions', () => {
  test.setTimeout(360000);

  test('complete wizard, OTP login, and answer No then Yes for all eligibility questions', async ({ page }) => {
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

    // 11. Pause for user to enter captcha manually
    await page.pause();

    // 12. Click "Send OTP" button
    await page.locator('.send-otp-btn').filter({ hasText: /send otp/i }).click();

    // 13. Wait 30 seconds for user to enter OTP
    await page.waitForSelector('.otp-inputs', { timeout: 30000 });
    await page.waitForTimeout(60000);

    // 14. Click "Verify OTP" button
    await page.locator('.send-otp-btn').filter({ hasText: /verify/i }).click();

    // === ELIGIBILITY CHECKS ===
    await page.waitForSelector('.eligibility-card', { timeout: 30000 });

    // 15. Select "HDFC Bank" from "Select regulated entity name" dropdown
    await page.locator('.entity-select').selectOption({ label: 'HDFC Bank' });

    // 16. Click Next
    await page.locator('.btn-next').click();

    // 17-21. Answer eligibility questions with specific pattern:
    // Questions 1-3: select "No" first, then "Yes" to proceed
    // Questions 4+: select "Yes" first, then "No" to proceed
    let questionIndex = 0;

    while (true) {
      await page.waitForTimeout(500);
      const noRadio = page.locator('.radio-option').filter({ hasText: /^No$/i });
      const yesRadio = page.locator('.radio-option').filter({ hasText: /^Yes$/i });

      // Check if radio options are visible (if not, we've left the eligibility phase)
      if (!(await yesRadio.first().isVisible().catch(() => false))) {
        break;
      }

      if (questionIndex < 3) {
        // Questions 1-3: select "No" first, then "Yes"
        await noRadio.first().click();
        await page.waitForTimeout(500);
        await yesRadio.first().click();
        await page.waitForTimeout(500);
      } else {
        // Questions 4+: select "Yes" first, then "No"
        await yesRadio.first().click();
        await page.waitForTimeout(500);
        await noRadio.first().click();
        await page.waitForTimeout(500);
      }

      questionIndex++;

      // After answering, check if button says "Proceed" (last question)
      const proceedBtn = page.locator('.btn-next').filter({ hasText: /proceed/i });
      if (await proceedBtn.isVisible().catch(() => false)) {
        await proceedBtn.click();
        break;
      }

      // Otherwise click Next
      await page.locator('.btn-next').click();
      await page.waitForTimeout(1000);
    }
  });
});
