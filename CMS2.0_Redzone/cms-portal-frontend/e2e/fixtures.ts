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
 *
 * Registered on the CONTEXT, not the page, so a tab a spec opens itself with `context.newPage()`
 * inherits the rewrite. Patching only the injected page left such tabs pointed at the compiled
 * 8082 — see the note in api-redirect.ts.
 */
export const test = base.extend({
  context: async ({ context }, use) => {
    await redirectBrowserApiCalls(context);
    await use(context);
  },
});

export { expect };
