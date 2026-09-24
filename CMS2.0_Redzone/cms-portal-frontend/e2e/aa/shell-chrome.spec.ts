import { test, expect, Page } from '@playwright/test';
import { loginAsAaRole } from '../utils/auth';

/**
 * Every routed AA screen must render the shared staff shell.
 *
 * This exists because the pre-existing `e2e/ui-homogenisation/` suite claimed to cover exactly this
 * and could not: it never logs in and only visits PUBLIC routes, and a grep for `app-shell`,
 * `shell-sidebar` or `shell-nav-item` across the whole e2e tree returned nothing. So AA kept its own
 * hand-rolled `.topbar` on six screens while a suite named "ui-homogenisation" stayed green.
 *
 * The sidebar assertion is the load-bearing one. SHELL_NAV filters items by Keycloak realm role, and
 * the list originally used `rbi-cms` role names (AA_REGISTRAR, AA_BENCH_OFFICER) that the live `cms`
 * realm does not issue -- so the nav rendered EMPTY for every real user. An empty nav looks identical
 * to a merely-permissive one, which is how that survived. Asserting at least one nav item is what
 * distinguishes the two.
 */

/** The routed AA screens. Excludes aa-hearing / aa-order, which are panels embedded in the detail page. */
const AA_PAGES = [
  { name: 'dashboard', url: '/aa/dashboard' },
  { name: 'appeal search', url: '/aa/search' },
  { name: 'admin console', url: '/aa/admin' },
] as const;

async function expectShellChrome(page: Page) {
  await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByTestId('shell-header')).toBeVisible();
  await expect(page.getByTestId('shell-sidebar')).toBeVisible();

  // A nav with zero items is the specific failure mode the wrong-realm role names produced.
  const navItems = page.getByTestId('shell-nav-item');
  expect(await navItems.count()).toBeGreaterThan(0);

  // The heading must be resolved text, not the raw translation key that a missing ui.page.* leaves behind.
  const title = page.getByTestId('shell-page-title');
  await expect(title).toBeVisible();
  const titleText = (await title.textContent())?.trim() ?? '';
  expect(titleText.length).toBeGreaterThan(0);
  expect(titleText).not.toMatch(/^(ui|aa)\./);
}

test.describe('AA screens render the shared staff shell', () => {
  for (const aaPage of AA_PAGES) {
    test(`${aaPage.name} shows shell chrome with a populated nav`, async ({ page }) => {
      await loginAsAaRole(page, 'AA_ADMIN', aaPage.url);
      await expectShellChrome(page);
    });
  }

  /**
   * The AA nav item must be OFFERED to an AA officer. This pairs with the guard in app.routes.ts:
   * a link the guard would bounce to /staff/unauthorized is worse than no link at all.
   */
  test('the AA officer is offered the Appeals nav item', async ({ page }) => {
    await loginAsAaRole(page, 'AA_DO', '/aa/dashboard');
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });

    const appealsLink = page.locator('[data-testid="shell-nav-item"][href*="/aa/dashboard"]');
    await expect(appealsLink).toBeVisible();
  });

  /** The old per-page chrome must be gone, or two headers stack and the migration is only half-done. */
  test('no page-level topbar survives alongside the shell header', async ({ page }) => {
    await loginAsAaRole(page, 'AA_DO', '/aa/dashboard');
    await expect(page.getByTestId('app-shell')).toBeVisible({ timeout: 30_000 });

    expect(await page.locator('.aa-dashboard .topbar').count()).toBe(0);
    expect(await page.getByTestId('shell-header').count()).toBe(1);
  });
});
