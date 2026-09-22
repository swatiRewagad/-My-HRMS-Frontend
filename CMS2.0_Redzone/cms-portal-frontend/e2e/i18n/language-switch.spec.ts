import { test, expect } from '../fixtures';
import { installCorsShim, assertBrowserCanReachApi } from '../public/browser-api';

/**
 * Public-portal language switching.
 *
 * ── WHY THE WHOLE FILE WAS FAILING ──────────────────────────────────────────────────────────────
 *
 * Seven of nine tests were red for one environmental reason, not a product one: the browser's calls
 * to /api/v1/i18n/locales and /api/v1/i18n/translations/{locale} were blocked by CORS whenever the
 * dev server runs on a port outside cms-backend's allow-list. The selector then had ZERO options and
 * every locale assertion failed. See ./../public/browser-api.ts for the full chain. Fixed in the
 * harness.
 *
 * ── ANNOTATIONS ARE NOT ASSERTIONS ──────────────────────────────────────────────────────────────
 *
 * The RTL test ended in `test.info().annotations.push({ type: 'info', ... })` when Urdu was absent.
 * An annotation does not affect exit status: the test passed, reporting that RTL support was fine,
 * having verified nothing. Urdu IS configured (GET /api/v1/i18n/locales returns 10 locales including
 * ur with rtl: true), so the branch was dead code hiding a real assertion. The locale list is now
 * asserted and `dir` is checked unconditionally.
 */

/** Every locale the portal claims to support. Asserted, so a silently dropped locale fails. */
const EXPECTED_LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

/** Locales whose script is Devanagari, used by the "text actually changed" assertions. */
const DEVANAGARI = /[ऀ-ॿ]/;

test.describe('i18n Language Switching', () => {

  test.beforeEach(async ({ page }) => {
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public');
  });

  test('language selector is visible with multiple options', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const values = await langSelect.locator('option').evaluateAll((els) =>
      els.map((el) => (el as HTMLOptionElement).value)
    );
    // Named, not merely counted. `>= 2` passed on a selector offering English and one other, which
    // would mean eight statutory languages had silently vanished from a citizen-facing portal.
    for (const locale of EXPECTED_LOCALES) {
      expect(values, `locale ${locale} is missing from the selector`).toContain(locale);
    }
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
    const navLinks = page.locator('#main-nav a');
    const englishText = await navLinks.first().textContent();
    expect(englishText?.trim(), 'the nav must render text before the switch').toBeTruthy();
    expect(englishText).not.toMatch(DEVANAGARI);

    // Switch to Hindi
    await langSelect.selectOption('hi');

    // Nav text should change to Hindi. Polled rather than slept on: a fixed waitForTimeout either
    // flakes or hides a slow bundle load behind a passing assertion.
    await expect(navLinks.first()).toHaveText(DEVANAGARI, { timeout: 10000 });
    const hindiText = await navLinks.first().textContent();
    expect(hindiText).not.toBe(englishText);
  });

  test('Hindi locale persists across page navigation', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    await langSelect.selectOption('hi');
    await expect(page.locator('#main-nav a').first()).toHaveText(DEVANAGARI, { timeout: 10000 });

    // Navigate to another page (track complaint)
    await page.goto('/public/track', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Language should still be Hindi (persisted in localStorage as cms_locale)
    await expect(page.locator('.lang-select')).toHaveValue('hi', { timeout: 10000 });
    await expect(page.locator('#main-nav a').first()).toHaveText(DEVANAGARI, { timeout: 10000 });
    expect(await page.evaluate(() => localStorage.getItem('cms_locale'))).toBe('hi');
  });

  test('switching to Marathi changes text', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const englishText = await page.locator('#main-nav a').first().textContent();

    await langSelect.selectOption('mr');

    // Page text should contain Devanagari (Marathi uses same script)
    await expect(page.locator('#main-nav a').first()).toHaveText(DEVANAGARI, { timeout: 10000 });
    // And it must differ from the English, or Devanagari alone would not prove a switch happened.
    expect(await page.locator('#main-nav a').first().textContent()).not.toBe(englishText);
  });

  test('switching back to English restores original text', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    const navFirst = page.locator('#main-nav a').first();
    const original = await navFirst.textContent();

    await langSelect.selectOption('hi');
    await expect(navFirst).toHaveText(DEVANAGARI, { timeout: 10000 });

    await langSelect.selectOption('en');
    // Restored to the exact original string, not merely "no longer Devanagari" — a blank label also
    // satisfies not.toMatch.
    await expect(navFirst).toHaveText(String(original).trim(), { timeout: 10000 });
    expect(await navFirst.textContent()).not.toMatch(DEVANAGARI);
  });

  test('RTL language (Urdu) sets dir attribute', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    // Urdu's presence is ASSERTED. The previous version branched on it and, when absent, pushed an
    // annotation instead — which does not fail a test, so a portal with no RTL support at all
    // reported green.
    const values = await langSelect.locator('option').evaluateAll((els) =>
      els.map((el) => (el as HTMLOptionElement).value)
    );
    expect(values, 'Urdu must be offered — it is the only RTL locale in the scheme').toContain('ur');

    await langSelect.selectOption('ur');

    // The navbar-bg should have dir="rtl"
    await expect(page.locator('.navbar-bg')).toHaveAttribute('dir', 'rtl', { timeout: 10000 });

    // And switching away must clear it, or every subsequent page stays mirrored. The attribute is
    // REMOVED rather than set to 'ltr': public-layout.component.html:2 binds
    // [attr.dir]="translationService.isRtl() ? 'rtl' : null", and a null attr binding removes the
    // attribute so direction inherits normally. Asserting 'ltr' here would be the test being wrong
    // about the product, not a defect.
    await langSelect.selectOption('en');
    await expect(page.locator('.navbar-bg')).not.toHaveAttribute('dir', 'rtl', { timeout: 10000 });
    expect(await page.locator('.navbar-bg').getAttribute('dir')).toBeNull();
  });

  test('language preference stored in localStorage', async ({ page }) => {
    const langSelect = page.locator('.lang-select');
    await expect(langSelect).toBeVisible({ timeout: 10000 });

    await langSelect.selectOption('hi');
    await expect(page.locator('#main-nav a').first()).toHaveText(DEVANAGARI, { timeout: 10000 });

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
    expect(options.length, 'no locales to iterate').toBeGreaterThan(1);

    for (const locale of options) {
      await langSelect.selectOption(locale);

      // Verify the select still shows the correct value
      await expect(langSelect).toHaveValue(locale, { timeout: 10000 });

      // Verify no error state
      const errorMsg = page.locator('.error-msg, .translation-error');
      await expect(errorMsg).not.toBeVisible();

      // And the nav actually rendered SOMETHING — an unresolved bundle leaves the raw key visible,
      // which is not an "error state" the app reports but is a broken page for the citizen.
      const navText = (await page.locator('#main-nav a').first().textContent())?.trim() ?? '';
      expect(navText.length, `nav is empty in locale ${locale}`).toBeGreaterThan(0);
      expect(navText, `locale ${locale} is rendering a raw translation key`).not.toMatch(
        /^[a-z_]+\.[a-z_.]+$/
      );
    }
  });
});
