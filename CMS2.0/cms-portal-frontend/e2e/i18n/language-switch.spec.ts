import { test, expect } from '@playwright/test';

test.describe('i18n Language Switching', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
  });

  test('language selector is visible with multiple options', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const options = langSelect.locator('option');
    const count = await options.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  test('default language is English', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const selectedValue = await langSelect.inputValue();
    expect(selectedValue).toBe('en');
  });

  test('switching to Hindi changes page text', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Capture current English text in nav
    const navLinks = page.locator('.main-nav a');
    const englishText = await navLinks.first().textContent();

    // Switch to Hindi
    await langSelect.selectOption('hi');
    await page.waitForTimeout(1000);

    // Nav text should change to Hindi
    const hindiText = await navLinks.first().textContent();
    expect(hindiText).not.toBe(englishText);
    // Hindi text should contain Devanagari characters
    expect(hindiText).toMatch(/[\u0900-\u097F]/);
  });

  test('Hindi locale persists across page navigation', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Switch to Hindi
    await langSelect.selectOption('hi');
    await page.waitForTimeout(1500);

    // Navigate to another page (track complaint)
    await page.goto('/public/track', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Language should still be Hindi (persisted in localStorage as cms_locale)
    const currentLang = await page.locator('.lang-select').inputValue();
    expect(currentLang).toBe('hi');

    // Nav links should contain Hindi text (Devanagari)
    const navLinks = page.locator('.main-nav a');
    const navText = await navLinks.first().textContent();
    expect(navText).toMatch(/[\u0900-\u097F]/);
  });

  test('switching to Marathi changes text', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Switch to Marathi
    await langSelect.selectOption('mr');
    await page.waitForTimeout(1000);

    // Page text should contain Devanagari (Marathi uses same script)
    const navLinks = page.locator('.main-nav a');
    const marathiText = await navLinks.first().textContent();
    expect(marathiText).toMatch(/[\u0900-\u097F]/);
  });

  test('switching back to English restores original text', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Switch to Hindi first
    await langSelect.selectOption('hi');
    await page.waitForTimeout(1000);

    // Switch back to English
    await langSelect.selectOption('en');
    await page.waitForTimeout(1000);

    // Nav text should be in English
    const navLinks = page.locator('.main-nav a');
    const englishText = await navLinks.first().textContent();
    // Should NOT contain Devanagari
    expect(englishText).not.toMatch(/[\u0900-\u097F]/);
  });

  test('RTL language (Urdu) sets dir attribute', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Check if Urdu is available
    const options = await langSelect.locator('option').allTextContents();
    const hasUrdu = options.some(o => o.includes('اردو'));

    if (hasUrdu) {
      await langSelect.selectOption('ur');
      await page.waitForTimeout(1000);

      // The navbar-bg should have dir="rtl"
      const navbarBg = page.locator('.navbar-bg');
      const dir = await navbarBg.getAttribute('dir');
      expect(dir).toBe('rtl');
    } else {
      test.info().annotations.push({
        type: 'info',
        description: 'Urdu locale not available in current configuration',
      });
    }
  });

  test('language preference stored in localStorage', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Switch to Hindi
    await langSelect.selectOption('hi');
    await page.waitForTimeout(1000);

    // Check localStorage (key is 'cms_locale')
    const storedLocale = await page.evaluate(() => localStorage.getItem('cms_locale'));
    expect(storedLocale).toBe('hi');
  });

  test('all available locales load without error', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const options = await langSelect.locator('option').evaluateAll(
      (els) => els.map(el => (el as HTMLOptionElement).value)
    );

    for (const locale of options) {
      await langSelect.selectOption(locale);
      await page.waitForTimeout(500);

      // Verify no error state
      const errorMsg = page.locator('.error-msg, .translation-error');
      await expect(errorMsg).not.toBeVisible();

      // Verify the select still shows the correct value
      const currentValue = await langSelect.inputValue();
      expect(currentValue).toBe(locale);
    }
  });
});
