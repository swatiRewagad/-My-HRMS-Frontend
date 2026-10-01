import { BrowserContext, Page } from '@playwright/test';

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
 *
 * Accepts a BrowserContext as well as a Page, and the CONTEXT is what callers should pass. A route
 * registered on a page covers only that page, so a tab opened later with `context.newPage()` gets no
 * rewrite and silently talks to the compiled 8082 — which under this harness is a stale or absent JVM.
 * That surfaced as "Failed to load CAPTCHA" on a reopened tab, a login screen that looked broken while
 * nothing about login was wrong.
 *
 * <h2>Why the rewrite also stamps a synthetic X-Forwarded-For</h2>
 *
 * <p>`AntiAutomationFilter` (backend, @Order(2)) counts requests per client IP over a sliding window
 * and answers 429 SUSPICIOUS_ACTIVITY past the threshold — dev-local 100 / 60 s — for every path
 * under `/api/v1/citizen/`, `/api/v1/complaints` and `/api/complaints`. It keys the counter on
 * X-Forwarded-For, falling back to the remote address, which for every browser in this harness is
 * loopback. So a WHOLE DIRECTORY of tests is counted as ONE abusive client, and once the bucket is
 * full it stays full: `checkVelocityAnomaly` increments the counter before comparing it, so blocked
 * requests keep the window alive and the 429s continue for as long as traffic continues.
 *
 * <p>The visible symptom is nothing like a rate limit. The citizen wizard's `saveDraftToServer`
 * swallows its error (`error: () => this.onDraftSaveFailed()`), so the page simply never completes a
 * draft POST and any spec waiting on that response times out. That is exactly how
 * draft-autosave-lifecycle.spec.ts produced 16 failures on ONE helper in the 2026-10-01 run while
 * passing cleanly in isolation — the specs and the product were both correct.
 *
 * <p>Each browser CONTEXT therefore presents its own synthetic X-Forwarded-For, so one test's
 * traffic cannot exhaust another's budget. This does NOT weaken the control: the per-client
 * threshold is enforced exactly as configured, the IP is STABLE within a context (so a spec that
 * deliberately hammers an endpoint inside one test still trips the filter, and cooloff rows keyed on
 * IP+fingerprint keep their within-test meaning), and a real client is still blocked. The
 * alternative — raising velocity-threshold in application-dev-local.yml — would soften a live
 * control for every session sharing this backend. The same reasoning already governs
 * `fileComplaintForOffice` in test-data.ts.
 *
 * <p>Set E2E_SYNTHETIC_CLIENT_IP=0 to send no header, for a test that genuinely needs loopback.
 */
export async function redirectBrowserApiCalls(target: Page | BrowserContext): Promise<void> {
  const configured = process.env['API_BASE_URL'];
  const compiledBase = 'http://localhost:8082';

  if (!configured || configured === compiledBase) {
    return;
  }

  const normalisedTarget = configured.replace(/\/+$/, '');

  // One address per context, chosen once so it is stable for every request this context makes.
  // 10/8 is private and cannot collide with a real client of a deployed environment.
  const octet = () => Math.floor(Math.random() * 254) + 1;
  const syntheticIp = `10.${octet()}.${octet()}.${octet()}`;
  const stampIp = process.env['E2E_SYNTHETIC_CLIENT_IP'] !== '0';

  await target.route(`${compiledBase}/**`, async route => {
    const original = route.request().url();
    const headers = stampIp
      ? { ...route.request().headers(), 'x-forwarded-for': syntheticIp }
      : undefined;
    await route.continue({ url: original.replace(compiledBase, normalisedTarget), headers });
  });
}
