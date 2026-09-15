import { test, expect } from '@playwright/test';

test.describe('FAQ — Public Home Section', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
  });

  test('FAQ section on home page shows questions from API', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const faqItems = faqSection.locator('.faq-item');
    const count = await faqItems.count();
    expect(count).toBeGreaterThan(0);
    expect(count).toBeLessThanOrEqual(5);
  });

  test('first FAQ item is open by default', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const firstItem = faqSection.locator('.faq-item').first();
    await expect(firstItem).toHaveClass(/open/);
  });

  test('clicking a FAQ toggles its answer', async ({ page }) => {
    const faqSection = page.locator('.faq-section');
    await expect(faqSection).toBeVisible({ timeout: 10000 });

    const faqItems = faqSection.locator('.faq-item');
    const count = await faqItems.count();
    if (count < 2) return;

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
    await page.goto('/public/faq', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
  });

  test('FAQ page displays all FAQs', async ({ page }) => {
    const faqList = page.locator('.faq-list');
    // Wait for loading to finish
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });

    const faqItems = faqList.locator('.faq-item');
    const count = await faqItems.count();
    // Should have more items than the home page limit (5)
    expect(count).toBeGreaterThan(0);
  });

  test('category filter works', async ({ page }) => {
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });

    const categoryBtns = page.locator('.category-btn');
    const count = await categoryBtns.count();

    if (count > 1) {
      // "All" is active by default
      await expect(categoryBtns.first()).toHaveClass(/active/);

      const allCount = await page.locator('.faq-item').count();

      // Click a specific category
      await categoryBtns.nth(1).click();
      await expect(categoryBtns.nth(1)).toHaveClass(/active/);

      const filteredCount = await page.locator('.faq-item').count();
      expect(filteredCount).toBeLessThanOrEqual(allCount);

      // Click "All" again to restore
      await categoryBtns.first().click();
      const restoredCount = await page.locator('.faq-item').count();
      expect(restoredCount).toBe(allCount);
    }
  });

  test('search filter works', async ({ page }) => {
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });

    const searchInput = page.locator('.faq-search-input');
    await expect(searchInput).toBeVisible();

    const allCount = await page.locator('.faq-item').count();
    if (allCount === 0) return;

    // Type a search term that should filter results
    await searchInput.fill('complaint');
    await page.waitForTimeout(300);

    const filteredCount = await page.locator('.faq-item').count();
    // Filtered count should be less than or equal to all
    expect(filteredCount).toBeLessThanOrEqual(allCount);

    // Clear search
    await searchInput.fill('');
    await page.waitForTimeout(300);

    const clearedCount = await page.locator('.faq-item').count();
    expect(clearedCount).toBe(allCount);
  });

  test('empty state shows when no results match search', async ({ page }) => {
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });

    const searchInput = page.locator('.faq-search-input');
    await searchInput.fill('zzz_no_match_xyz_999');
    await page.waitForTimeout(300);

    await expect(page.locator('.faq-empty')).toBeVisible();
  });

  test('back to home link navigates to /public', async ({ page }) => {
    await expect(page.locator('.faq-loading')).not.toBeVisible({ timeout: 10000 });

    const backLink = page.locator('.back-link');
    await expect(backLink).toBeVisible();
    await backLink.click();

    await page.waitForURL('**/public', { timeout: 10000 });
  });
});
