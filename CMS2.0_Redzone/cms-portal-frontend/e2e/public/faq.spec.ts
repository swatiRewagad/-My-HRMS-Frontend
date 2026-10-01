import { test, expect } from '../fixtures';
import { installCorsShim, assertBrowserCanReachApi } from './browser-api';

/**
 * FAQ, public portal.
 *
 * ── WHY THESE TESTS WERE FAILING ────────────────────────────────────────────────────────────────
 *
 * TWO separate causes, one environmental and one a real product defect. They were indistinguishable
 * before, because both present as an FAQ page that shows nothing useful.
 *
 * 1. ENVIRONMENTAL. The browser's API calls were blocked by CORS whenever the dev server runs on a
 *    port outside cms-backend's allow-list (4200/4201/4202/4300). See the header comment in
 *    ./browser-api.ts for the full chain. Fixed here in the harness; installCorsShim +
 *    assertBrowserCanReachApi now make the failure mode loud instead of empty.
 *
 * 2. PRODUCT DEFECT, still open. FAQ rows 1–10 (question keys faq.q1.question … faq.q10.question)
 *    have NO rows in TRANSLATION_KEYS at all, so the portal renders the literal string
 *    'faq.q1.question' to citizens. Their categories are uppercase (GENERAL, FILING, TRACKING,
 *    APPEAL, WITHDRAWAL) and the category label keys faq.cat_GENERAL etc. are equally absent, so the
 *    filter bar shows 'faq.cat_APPEAL' as a button label. Rows 11–28 are correct: keys present, with
 *    hi and mr translations. The defect is the ten V8-era seed rows, which point at keys that were
 *    never created. VERIFIED by SQL against cms_db, not inferred.
 *
 * The tests below assert the truth, so the two that cover this are RED until the ten rows are either
 * deactivated or given their translation keys. That is the correct outcome: a statutory portal that
 * shows 'faq.q1.question' to a citizen must not report green.
 *
 * ── BARE `return`s REMOVED ──────────────────────────────────────────────────────────────────────
 *
 * `if (count < 2) return;` and `if (allCount === 0) return;` used to end two of these tests with zero
 * assertions executed, and `if (count > 1) { ... }` wrapped the whole of the category-filter test.
 * An FAQ table with fewer than two rows, or a page that rendered none, is a defect in the feature —
 * so the precondition is asserted rather than used as an exit.
 */

test.describe('FAQ — Public Home Section', () => {

  test.beforeEach(async ({ page }) => {
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public');
  });

  test('FAQ section on home page shows questions from API', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const faqItems = faqSection.locator('.faq-item');
    await expect(faqItems.first()).toBeVisible({ timeout: 10000 });
    const count = await faqItems.count();
    expect(count).toBeGreaterThan(0);
    expect(count).toBeLessThanOrEqual(5);
  });

  test('first FAQ item is open by default', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const firstItem = faqSection.locator('.faq-item').first();
    await expect(firstItem).toBeVisible({ timeout: 10000 });
    await expect(firstItem).toHaveClass(/open/);
  });

  test('clicking a FAQ toggles its answer', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const faqItems = faqSection.locator('.faq-item');
    await expect(faqItems.first()).toBeVisible({ timeout: 10000 });
    // Asserted, not `if (count < 2) return`. The home section shows up to five FAQs and the table
    // holds 28 active rows, so fewer than two is the accordion or the API failing — the very thing
    // this test is here to catch.
    expect(
      await faqItems.count(),
      'the home FAQ accordion needs at least two items for a toggle to be meaningful'
    ).toBeGreaterThanOrEqual(2);

    // Second FAQ should be closed initially
    const secondItem = faqItems.nth(1);
    const secondButton = secondItem.locator('.faq-question');
    await expect(secondButton).toHaveAttribute('aria-expanded', 'false');

    // Click to open
    await secondButton.click();
    await expect(secondButton).toHaveAttribute('aria-expanded', 'true');
    await expect(secondItem.locator('.faq-answer')).toBeVisible();

    // Click again to close
    await secondButton.click();
    await expect(secondButton).toHaveAttribute('aria-expanded', 'false');
    await expect(secondItem.locator('.faq-answer')).not.toBeVisible();
  });

  test('"View All" navigates to /public/faq', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const viewAllLink = faqSection.locator('.view-all');
    await expect(viewAllLink).toBeVisible();
    await viewAllLink.click();

    await page.waitForURL('**/public/faq', { timeout: 10000 });
    expect(page.url()).toContain('/public/faq');
  });
});

test.describe('FAQ — Dedicated Page', () => {

  test.beforeEach(async ({ page }) => {
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public/faq');
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });
  });

  test('FAQ page displays all FAQs', async ({ page }) => {
    const faqItems = page.locator('.faq-list .faq-item');
    await expect(faqItems.first()).toBeVisible({ timeout: 10000 });

    // The dedicated page is unpaginated, so it must show MORE than the home page's cap of five —
    // `> 0` would also pass if the page silently rendered the same truncated list.
    expect(await faqItems.count()).toBeGreaterThan(5);
  });

  test('category filter works', async ({ page }) => {
    const categoryBtns = page.locator('.category-btn');
    await expect(categoryBtns.first()).toBeVisible({ timeout: 10000 });

    // Asserted. The whole body of this test used to sit inside `if (count > 1)`, so a page rendering
    // only the "All" button — i.e. categories failing to load — exercised nothing.
    expect(
      await categoryBtns.count(),
      'the filter bar must offer at least one real category besides "All"'
    ).toBeGreaterThan(1);

    // "All" is active by default
    await expect(categoryBtns.first()).toHaveClass(/active/);

    const allCount = await page.locator('.faq-item').count();
    expect(allCount).toBeGreaterThan(0);

    // Click a specific category
    await categoryBtns.nth(1).click();
    await expect(categoryBtns.nth(1)).toHaveClass(/active/);
    await expect(categoryBtns.first()).not.toHaveClass(/active/);

    const filteredCount = await page.locator('.faq-item').count();
    expect(filteredCount).toBeLessThanOrEqual(allCount);
    // A filter that narrows to nothing, or does not narrow at all, is not filtering.
    expect(filteredCount, 'selecting a category must leave at least one FAQ').toBeGreaterThan(0);
    expect(filteredCount, 'selecting a category must actually narrow the list').toBeLessThan(
      allCount
    );

    // Click "All" again to restore. The list count has to be polled rather than read straight
    // after the click: toHaveClass resolves on the button's own re-render, which can land before
    // the @for over filteredFaqs() has been re-projected.
    await categoryBtns.first().click();
    await expect(categoryBtns.first()).toHaveClass(/active/);
    await expect.poll(() => page.locator('.faq-item').count()).toBe(allCount);
  });

  test('search filter works', async ({ page }) => {
    const searchInput = page.locator('.faq-search-input');
    await expect(searchInput).toBeVisible();

    const allCount = await page.locator('.faq-item').count();
    // Asserted, not `if (allCount === 0) return`. Zero rendered FAQs is the failure this test would
    // otherwise exit past without asserting anything at all.
    expect(allCount, 'the FAQ page rendered no items, so there is nothing to search').toBeGreaterThan(
      0
    );

    // Type a search term that should filter results
    await searchInput.fill('complaint');
    await page.waitForTimeout(300);

    const filteredCount = await page.locator('.faq-item').count();
    // Filtered count should be less than or equal to all
    expect(filteredCount).toBeLessThanOrEqual(allCount);
    expect(filteredCount, "'complaint' appears in the seeded FAQ text and must match").toBeGreaterThan(
      0
    );

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(300);

    const clearedCount = await page.locator('.faq-item').count();
    expect(clearedCount).toBe(allCount);
  });

  test('empty state shows when no results match search', async ({ page }) => {
    const searchInput = page.locator('.faq-search-input');
    await searchInput.fill('zzz_no_match_xyz_999');
    await page.waitForTimeout(300);

    await expect(page.locator('.faq-empty')).toBeVisible();
    expect(await page.locator('.faq-item').count()).toBe(0);
  });

  test('back to home link navigates to /public', async ({ page }) => {
    const backLink = page.locator('.back-link');
    await expect(backLink).toBeVisible();
    await backLink.click();

    await page.waitForURL('**/public', { timeout: 10000 });
  });

  /**
   * RED — OPEN PRODUCT DEFECT, not a test defect. Do not "fix" this by narrowing the selector.
   *
   * FAQ rows 1–10 in cms_db carry question_key/answer_key values ('faq.q1.question' …
   * 'faq.q10.question') for which no TRANSLATION_KEYS row exists, so the portal renders the key
   * itself to the citizen. Verified:
   *   SELECT f.id, f.question_key,
   *          (SELECT COUNT(*) FROM translations t JOIN translation_keys k
   *             ON t.translation_key_id = k.id WHERE k.code = f.question_key)
   *   FROM faq f WHERE f.is_active = 1;   -- rows 1–10 return 0, rows 11–28 return 2
   *
   * Fix is one of: deactivate the ten V8-era rows (they are duplicated in content by rows 11–28), or
   * seed their translation keys. Both are data/product changes, outside this suite's ownership.
   */
  test('no FAQ renders a raw translation key', async ({ page }) => {
    const questions = await page.locator('.faq-question span').allInnerTexts();
    expect(questions.length).toBeGreaterThan(0);
    for (const q of questions) {
      expect(
        q.trim(),
        'an FAQ is displaying its translation key instead of its text — FAQ rows 1-10 have no ' +
          'TRANSLATION_KEYS entries'
      ).not.toMatch(/^faq\./);
    }

    // Category buttons resolve via faq.cat_<code>; an unmapped code surfaces the bare key. The
    // uppercase categories (GENERAL/FILING/TRACKING/APPEAL/WITHDRAWAL) used by rows 1-10 have no
    // faq.cat_* key, while the lowercase ones used by rows 11-28 do.
    const cats = await page.locator('.category-btn').allInnerTexts();
    for (const c of cats) {
      expect(c.trim(), 'a category button is showing its raw translation key').not.toMatch(/^faq\./);
      expect(c.trim()).not.toMatch(/^(filing|eligibility|tracking|privacy)$/);
    }
  });

  test('search matches the translated text, not the key', async ({ page }) => {
    // 'fee' appears in the answer text but in no questionKey/answerKey, so a key-based filter
    // would return nothing here.
    await page.locator('.faq-search-input').fill('fee');
    await page.waitForTimeout(300);

    expect(
      await page.locator('.faq-item').count(),
      "'fee' appears only in the rendered text, so a match proves the filter reads resolved " +
        'translations rather than key names'
    ).toBeGreaterThan(0);
  });
});

test.describe('FAQ — Help navigation', () => {

  test('the header Help link reaches the FAQ page, not the home wildcard', async ({ page }) => {
    // Regression: the link pointed at /public/help, which has no route, so the '**' wildcard
    // silently redirected the citizen back to the home page. Now /public/faq — verified at
    // src/app/components/public/public-layout/public-layout.component.html:51, which also renders
    // the label through `'nav.help' | translate` rather than a hardcoded string.
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public');

    await page.locator('#main-nav a', { hasText: /help|सहायता|मदत/i }).first().click();

    await page.waitForURL('**/public/faq', { timeout: 10000 });
    await expect(page.locator('.faq-list')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.faq-list .faq-item').first()).toBeVisible({ timeout: 10000 });
  });

  test('the Help link label is translated, not a hardcoded English string', async ({ page }) => {
    // The nav label is the one piece of chrome a Hindi-only citizen must be able to read to find
    // help at all. A hardcoded "Help" would leave it English in every locale.
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public');

    const helpLink = page.locator('#main-nav a[href="/public/faq"]');
    await expect(helpLink).toHaveText(/help/i);

    await page.locator('.lang-select').selectOption('hi');
    await expect(helpLink).toHaveText(/[ऀ-ॿ]/, { timeout: 5000 });
  });
});
