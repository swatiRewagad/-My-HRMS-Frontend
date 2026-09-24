import { test, expect } from '@playwright/test';

/**
 * Heading structure and image alt text on the INDEXABLE public pages.
 *
 * <h2>Why this is asserted in the browser, not by scanning templates</h2>
 *
 * <p>A static scan of the templates reports false positives on both counts, and acting on them would
 * have been wrong:
 *
 * <ul>
 *   <li>`public-home.component.html` contains TWO {@code <h1>} elements, but they sit in mutually
 *       exclusive {@code @if (authenticated)} / {@code @else} branches, so exactly one ever renders.
 *       "Fixing" that by deleting one would have broken a page state.
 *   <li>One {@code <img>} appears to have no {@code alt}, but it uses the {@code [alt]="card.title"}
 *       property binding, which a regex for {@code alt=} does not see.
 * </ul>
 *
 * <p>So this counts what the DOM actually contains. Exactly one h1 per page is what a crawler uses to
 * identify the page's subject, and a missing alt fails both accessibility obligations and image search —
 * both of which matter for a government service.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** The indexable public routes, from src/app/seo/seo-routes.ts. */
const INDEXABLE_PAGES = [
  '/public',
  '/public/faq',
  '/public/eligibility-wizard',
  '/public/track',
  '/public/login',
];

test.describe('SEO — heading structure and alt text', () => {

  for (const path of INDEXABLE_PAGES) {
    test(`${path} renders exactly one h1`, async ({ page }) => {
      await page.goto(`${APP_BASE}${path}`, { waitUntil: 'domcontentloaded' });

      // Wait for the app to render rather than asserting on an empty <app-root>.
      await expect.poll(async () => page.locator('h1, h2').count(), { timeout: 45000 })
        .toBeGreaterThan(0);

      const h1s = await page.locator('h1').allInnerTexts();
      expect(h1s.length, `${path} has ${h1s.length} h1 elements: ${JSON.stringify(h1s)}`).toBe(1);
      expect(h1s[0].trim().length, `${path} has an empty h1`).toBeGreaterThan(0);
      // A raw translation key as the page's main heading is worse than a wrong one: it is visibly broken.
      expect(h1s[0]).not.toMatch(/^[a-z_]+\.[a-z_]+$/);
    });

    test(`${path} gives every meaningful image an alt attribute`, async ({ page }) => {
      await page.goto(`${APP_BASE}${path}`, { waitUntil: 'domcontentloaded' });
      await expect.poll(async () => page.locator('h1, h2').count(), { timeout: 45000 })
        .toBeGreaterThan(0);

      const missing = await page.locator('img:not([alt])').evaluateAll(
        els => els.map(e => (e as HTMLImageElement).getAttribute('src') ?? '(no src)'));

      // alt="" is CORRECT for a decorative image — it tells a screen reader to skip it. The defect is an
      // absent attribute, which makes a reader announce the filename instead.
      expect(missing, `${path} has images with no alt attribute: ${JSON.stringify(missing)}`)
        .toEqual([]);
    });
  }

  test('heading levels do not skip on the FAQ page', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/faq`, { waitUntil: 'domcontentloaded' });
    await expect.poll(async () => page.locator('h1').count(), { timeout: 45000 }).toBe(1);

    const levels = await page.locator('h1, h2, h3, h4, h5, h6').evaluateAll(
      els => els.map(e => Number(e.tagName.substring(1))));

    expect(levels[0], 'the first heading on the page must be the h1').toBe(1);

    // A jump from h1 straight to h3 breaks the outline a screen-reader user navigates by.
    for (let i = 1; i < levels.length; i++) {
      const jump = levels[i] - levels[i - 1];
      expect(jump, `heading level jumped from h${levels[i - 1]} to h${levels[i]}`)
        .toBeLessThanOrEqual(1);
    }
  });
});
