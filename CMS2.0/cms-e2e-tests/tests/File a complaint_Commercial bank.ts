import { test, expect } from '@playwright/test';
import path from 'path';

test.describe('Eligibility Wizard - Full Complaint Filing Flow', () => {
  test.setTimeout(360000);

  test('complete wizard, OTP login, file complaint, and get reference number', async ({ page }) => {
    // === ELIGIBILITY WIZARD ===

    // 1. Navigate to eligibility wizard
    await page.goto('/public/eligibility-wizard', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    await page.waitForSelector('.wizard-card', { timeout: 20000 });

    // 2. Select "Commercial Bank" from entity type dropdown
    await page.locator('.wizard-select').selectOption('BANK');

    // 3. Click Next
    await page.locator('.btn-wizard-next').click();

    // 4. Select "Yes, I have complained"
    await page.locator('.wizard-radio').filter({ hasText: /yes/i }).click();
    await page.locator('.btn-wizard-next').click();

    // 5. Select date as current date
    const today = new Date().toISOString().split('T')[0];
    await page.locator('.wizard-date-input').fill(today);

    // 6. Click Next
    await page.locator('.btn-wizard-next').click();

    // 7. Select "Replied but I am not satisfied"
    await page.locator('.wizard-radio').filter({ hasText: /not satisfied|dissatisfied/i }).click();

    // 8. Click "Check Eligibility"
    await page.locator('.btn-wizard-next').click();
    await page.waitForSelector('.result-card', { timeout: 15000 });

    // 9. Click "Proceed to file complaint"
    await page.locator('.btn-wizard-primary').click();

    // === OTP LOGIN ===
    await page.waitForSelector('.verify-card', { timeout: 15000 });

    // 10. Enter mobile number
    await page.locator('#mobile-input').fill('8369945118');

    // 11. Pause for user to enter captcha manually
    await page.pause();

    // 12. Click "Send OTP"
    await page.locator('.send-otp-btn').filter({ hasText: /send otp/i }).click();

    // 13. Wait 1 minute for user to enter OTP
    await page.waitForSelector('.otp-inputs', { timeout: 30000 });
    await page.waitForTimeout(60000);

    // 14. Click "Verify OTP"
    await page.locator('.send-otp-btn').filter({ hasText: /verify/i }).click();

    // === FILE COMPLAINT - ELIGIBILITY PHASE ===
    await page.waitForSelector('.eligibility-card', { timeout: 30000 });

    // 15. Select "HDFC Bank" from "Select regulated entity name" dropdown
    await page.locator('.entity-select').selectOption({ label: 'HDFC Bank' });

    // 16. Click Next
    await page.locator('.btn-next').click();

    // 17. Select "Yes" radio button (filed with RE)
    await page.locator('.radio-option').filter({ hasText: /yes/i }).click();

    // 18. Select date as 30 days before current date
    const thirtyDaysAgo = new Date();
    thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
    const dateStr = thirtyDaysAgo.toISOString().split('T')[0];
    await page.locator('input[name="bankComplaintDate"]').fill(dateStr);

    // 19. Browse file
    const filePath = path.resolve('C:/Projects/My-HRMS-Frontend/CMS2.0/cms-e2e-tests/active.png');
    await page.locator('#eligibilityFile').setInputFiles(filePath);

    // 20. Click Next
    await page.locator('.btn-next').click();

    // 21. Select "No" radio button (received reply)
    await page.locator('.radio-option').filter({ hasText: /no/i }).click();

    // 22. Click Next
    await page.locator('.btn-next').click();

    // 23-24. Keep selecting "No" until Proceed button is displayed
    while (true) {
      const proceedBtn = page.locator('.btn-next').filter({ hasText: /proceed/i });
      if (await proceedBtn.isVisible().catch(() => false)) {
        break;
      }
      // Select "No" radio if visible
      const noRadio = page.locator('.radio-option').filter({ hasText: /no/i });
      if (await noRadio.first().isVisible().catch(() => false)) {
        await noRadio.first().click();
      }
      await page.locator('.btn-next').click();
      await page.waitForTimeout(500);
    }

    // 25. Click "Proceed"
    await page.locator('.btn-next').filter({ hasText: /proceed/i }).click();

    // === MULTI-STEP FORM ===

    // Wait for form phase to load
    await page.waitForSelector('.complaint-form-page', { timeout: 15000 });

    // 26. Step 1 - Complainant Details
    await page.locator('input[name="firstName"]').fill('test');
    await page.locator('input[name="lastName"]').fill('test');
    await page.locator('input[name="age"]').fill('25');
    await page.locator('select[name="gender"]').selectOption('female');
    await page.locator('input[name="email"]').fill('test@gmail.com');
    await page.locator('input[name="pincode"]').fill('400706');
    await page.waitForTimeout(2000); // Wait for pincode lookup
    await page.locator('input[name="state"]').fill('Maharashtra');

    // 27. Click Next
    await page.locator('.btn-next').click();

    // 28. Step 2 - Select "Yes" for credit card related
    await page.locator('.toggle-btn').filter({ hasText: /^yes$/i }).first().click();

    // 29. Click Next
    await page.locator('.btn-next').click();

    // 30. Step 3 - Complaint Details
    await page.locator('select[name="complaintCategory"]').selectOption('ATM');
    await page.locator('textarea[name="complaintText"]').fill('abcd');

    // 31. Click Next
    await page.locator('.btn-next').click();

    // 32. Step 4 - Select "No" for authorized representative
    await page.locator('.toggle-btn').filter({ hasText: /^no$/i }).first().click();

    // 33. Click Next
    await page.locator('.btn-next').click();

    // 34. Step 5 - Select all checkboxes on declaration screen
    const checkboxes = page.locator('.decl-checkbox');
    const checkboxCount = await checkboxes.count();
    for (let i = 0; i < checkboxCount; i++) {
      await checkboxes.nth(i).check();
    }

    // 35. Click Next
    await page.locator('.btn-next').click();

    // 36. Step 6 - Click "Submit Complaint"
    // Check declaration checkboxes on review page if present
    const reviewCheckboxes = page.locator('.decl-checkbox');
    const reviewCount = await reviewCheckboxes.count();
    for (let i = 0; i < reviewCount; i++) {
      const isChecked = await reviewCheckboxes.nth(i).isChecked();
      if (!isChecked) {
        await reviewCheckboxes.nth(i).check();
      }
    }
    await page.locator('.btn-submit-complaint').click();

    // 37. Fetch the reference number
    await page.waitForSelector('.success-page', { timeout: 30000 });
    const refText = await page.locator('.success-desc strong').textContent();
    console.log('=== REFERENCE NUMBER: ' + refText + ' ===');
    expect(refText).toBeTruthy();
  });
});
