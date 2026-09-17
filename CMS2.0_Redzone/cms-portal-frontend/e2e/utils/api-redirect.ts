import { Page } from '@playwright/test';

/**
 * Points the BROWSER's API calls at the same backend the test seeds against.
 *
 * `API_BASE_URL` only affects Playwright's `request` fixture. The Angular app reads
 * `environment.apiBaseUrl` (compiled in as http://localhost:8082) and `proxy.conf.json` proxies only
 * `/api/pincode`, so a browser test would fetch from a DIFFERENT backend than the one its own fixture
 * just seeded — the record exists in one database and the page renders "not found" from the other.
 *
 * This rewrites matching requests at the network layer, which is the only interception point that
 * works without rebuilding the app for each run.
 *
 * A no-op when API_BASE_URL is unset or already equals the app's compiled base, so normal local runs
 * against 8082 are unaffected.
 */
export async function redirectBrowserApiCalls(page: Page): Promise<void> {
  const target = process.env['API_BASE_URL'];
  const compiledBase = 'http://localhost:8082';

  if (!target || target === compiledBase) {
    return;
  }

  const normalisedTarget = target.replace(/\/+$/, '');

  await page.route(`${compiledBase}/**`, async route => {
    const original = route.request().url();
    await route.continue({ url: original.replace(compiledBase, normalisedTarget) });
  });
}
