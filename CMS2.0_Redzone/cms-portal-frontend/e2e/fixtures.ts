import { test as base, expect } from '@playwright/test';
import { redirectBrowserApiCalls } from './utils/api-redirect';

/**
 * Shared test fixture that keeps the browser and the test's `request` fixture pointed at the SAME
 * backend.
 *
 * Without this, a browser test seeds data through `request` (honours `API_BASE_URL`) but the page
 * fetches through the app's compiled `environment.apiBaseUrl` — two different backends, so the page
 * legitimately reports "not found" for a record that was just created.
 *
 * Import `test` from here instead of '@playwright/test' in any spec that drives the UI.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await redirectBrowserApiCalls(page);
    await use(page);
  },
});

export { expect };
