import { Page, expect } from '@playwright/test';

/**
 * Makes the BROWSER's API calls actually reach the backend under test, and asserts that they did.
 *
 * ── THE PROBLEM THIS SOLVES ─────────────────────────────────────────────────────────────────────
 *
 * Every browser-driven spec in e2e/public, e2e/i18n and e2e/re-portal was failing for one shared
 * reason that had nothing to do with the features under test:
 *
 *   Access to XMLHttpRequest at 'http://localhost:8082/api/v1/i18n/locales'
 *   from origin 'http://localhost:4296' has been blocked by CORS policy:
 *   No 'Access-Control-Allow-Origin' header is present on the requested resource.
 *
 * The chain is: the app is compiled with environment.apiBaseUrl = 'http://localhost:8082'
 * (src/environments/environment.ts:3). e2e/utils/api-redirect.ts rewrites those requests to
 * API_BASE_URL via route.continue({ url }). The rewritten host then evaluates CORS against the
 * PAGE's origin — and cms-backend's allow-list is
 * `http://localhost:4200,4201,4202,4300` (application.yml:162, application-dev-local.yml:113).
 * A dev server on any other port, including the 4296 this session uses, is refused with a bare 403
 * and no Access-Control-Allow-Origin header, so every XHR fails at the network layer and the page
 * renders empty. Translation keys show through verbatim ('nav.help', 'home.hero_title'), the FAQ list
 * is empty, and the language selector has zero options.
 *
 * NOT A PRODUCT DEFECT. In the pairing the product actually ships — dev server on 4200 against a
 * backend on 8082 — CORS is configured correctly and all of this works. 4296/8096 is a parallel-test
 * artefact, so it is fixed here in the harness rather than by widening a production allow-list.
 *
 * ── HOW ─────────────────────────────────────────────────────────────────────────────────────────
 *
 * route.fetch() performs the request from the Playwright process, where no CORS policy applies, and
 * the response is fulfilled back into the page with permissive CORS headers attached. The request,
 * the backend and the response body are all real; only the browser's origin check is bypassed.
 *
 * ── WHY assertBrowserCanReachApi EXISTS ────────────────────────────────────────────────────────
 *
 * A shim that silently stopped working would put every spec back to rendering an empty page, and an
 * empty page satisfies a surprising number of assertions (`count()` loops that never iterate,
 * `not.toBeVisible()` on an element that was never rendered). So each spec asserts, once, that the
 * browser really did get a 2xx from the API before it asserts anything about the feature.
 */

const COMPILED_BASE = 'http://localhost:8082';

const CORS_HEADERS: Record<string, string> = {
  'access-control-allow-origin': '*',
  'access-control-allow-headers': '*',
  'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
};

/** Every API response the page received, in order, for the readiness assertion. */
const observed = new WeakMap<Page, Array<{ url: string; status: number }>>();

/**
 * Routes the page's API traffic through the Playwright process, attaching CORS headers.
 *
 * Call in a beforeEach, AFTER the shared fixture has installed its own redirect: Playwright matches
 * route handlers in reverse registration order, so the last one registered wins.
 */
export async function installCorsShim(page: Page): Promise<void> {
  const target = (process.env['API_BASE_URL'] || COMPILED_BASE).replace(/\/+$/, '');
  const seen: Array<{ url: string; status: number }> = [];
  observed.set(page, seen);

  for (const pattern of [`${COMPILED_BASE}/**`, `${target}/**`]) {
    await page.route(pattern, async (route) => {
      const url = route.request().url().replace(COMPILED_BASE, target);

      // Preflight never reaches the server: answering it here is what stops Chromium refusing the
      // real call before it is made.
      if (route.request().method() === 'OPTIONS') {
        await route.fulfill({ status: 204, headers: CORS_HEADERS, body: '' });
        return;
      }

      // ── FILE UPLOADS MUST NOT GO THROUGH route.fetch ──────────────────────────────────────────
      //
      // route.fetch replays the request from the Playwright process using the body Playwright can
      // see, and for a multipart form containing a FILE that body is INCOMPLETE: Chromium reports
      // the part headers (`Content-Disposition: form-data; name="documents"; filename="x.pdf"`) but
      // not the file's bytes, because the renderer never had them — the upload is streamed from
      // disk by the browser process. The replayed request therefore arrives at the server with
      // empty file parts, which Spring binds as absent, so an upload the page genuinely performed
      // looks to the server like no file at all.
      //
      // That failure is SILENT and extremely misleading: the request is a real 200, the page shows
      // its success screen, and only the database shows nothing was stored. It is exactly how the
      // withdrawal-attachment cases failed while a curl of the same endpoint and the same page with
      // the shim disabled both stored the document correctly.
      //
      // So a multipart request is handed back to the browser with only its URL rewritten. CORS then
      // applies for real, which is fine for the dev-server ports the backend already allow-lists
      // (4200/4201/4202/4300 — application.yml) and is the pairing these specs run in. On a port
      // outside the allow-list such a request now FAILS LOUDLY instead of succeeding with no file,
      // which is the outcome we want: a dropped attachment must never look like a pass.
      const contentType = route.request().headers()['content-type'] || '';
      if (contentType.toLowerCase().startsWith('multipart/form-data')) {
        await route.continue({ url });
        return;
      }

      try {
        // Origin and Referer MUST be stripped. route.fetch replays the browser's headers verbatim,
        // so the server still sees Origin: http://localhost:4296 and Spring's CorsFilter still
        // answers 403 "Invalid CORS request" — the fetch happening outside the browser changes
        // nothing on its own. Without an Origin header the request is treated as same-origin/server
        // to server, which is what it now is.
        const outbound = { ...route.request().headers() };
        delete outbound['origin'];
        delete outbound['referer'];
        delete outbound['sec-fetch-site'];
        delete outbound['sec-fetch-mode'];

        const response = await route.fetch({ url, headers: outbound });
        seen.push({ url, status: response.status() });
        const inbound = { ...response.headers(), ...CORS_HEADERS };
        // Set-Cookie cannot be replayed through fulfill and Playwright rejects it outright.
        delete inbound['set-cookie'];
        delete inbound['content-encoding'];
        delete inbound['content-length'];
        await route.fulfill({
          status: response.status(),
          headers: inbound,
          body: await response.body(),
        });
      } catch (error) {
        // Recorded as a 0 so assertBrowserCanReachApi reports "the backend was unreachable" rather
        // than letting the page render empty and the feature assertions fail somewhere else.
        seen.push({ url, status: 0 });
        await route.fulfill({
          status: 502,
          headers: { ...CORS_HEADERS, 'content-type': 'application/json' },
          body: JSON.stringify({ error: `e2e shim could not reach ${url}: ${String(error)}` }),
        });
      }
    });
  }
}

/**
 * Navigates and asserts the browser genuinely reached the API.
 *
 * Without this, a broken shim, a stopped backend or a renamed endpoint all present identically: an
 * empty page. An empty page is the single most dangerous state for a UI suite, because assertions
 * phrased as "nothing bad is displayed" all pass on it.
 */
export async function assertBrowserCanReachApi(page: Page, path: string): Promise<void> {
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');

  const seen = observed.get(page) ?? [];
  const apiCalls = seen.filter((c) => c.url.includes('/api/'));
  expect(
    apiCalls.length,
    `the page at ${path} made no API calls at all — the CORS shim is not installed, or the app ` +
      'never issued a request'
  ).toBeGreaterThan(0);

  const ok = apiCalls.filter((c) => c.status >= 200 && c.status < 400);
  expect(
    ok.length,
    `no API call from the browser succeeded. Observed: ${JSON.stringify(apiCalls.slice(0, 6))}. ` +
      `Is the backend at ${process.env['API_BASE_URL'] || COMPILED_BASE} up?`
  ).toBeGreaterThan(0);
}

/**
 * Asserts the page is showing rendered text, not raw translation keys.
 *
 * When the translation fetch fails the app falls back to echoing the key, so 'nav.help' and
 * 'home.hero_title' appear verbatim to the citizen. Any spec that reads visible text needs this
 * guard, or it silently asserts against key names.
 */
export async function assertTranslationsResolved(page: Page): Promise<void> {
  const navTexts = await page.locator('#main-nav a').allInnerTexts();
  for (const text of navTexts) {
    expect(
      text.trim(),
      'the navigation is rendering raw translation keys, so the i18n bundle never loaded'
    ).not.toMatch(/^[a-z_]+\.[a-z_.]+$/);
  }
}
