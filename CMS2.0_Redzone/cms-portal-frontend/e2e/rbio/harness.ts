import { execFileSync } from 'node:child_process';
import { Page, test as base, expect } from '@playwright/test';
import { redirectBrowserApiCalls } from '../utils/api-redirect';

/**
 * Staff-suite browser harness.
 *
 * ── Why this exists ──────────────────────────────────────────────────────────────────────────────
 * Two separate things have to line up before a staff browser test can assert anything real:
 *
 *  1. The URL. The Angular bundle has `apiBaseUrl: 'http://localhost:8082'` compiled in, while the
 *     test's `request` fixture honours API_BASE_URL. `redirectBrowserApiCalls` (e2e/utils) already
 *     rewrites the URL so both hit the same backend.
 *
 *  2. The Origin. That URL rewrite is not enough. DevLocalSecurityConfig.corsConfigurationSource
 *     allow-lists ONLY ports 4200/4201/4202/4300, so every browser request from the dev server on
 *     4296 is rejected with `403 Invalid CORS request` before it reaches a controller. The page then
 *     renders "Complaint not found" for a record that demonstrably exists, and every UI assertion
 *     fails for a reason that has nothing to do with the feature under test.
 *
 *     Verified directly: GET /api/v1/complaints/{n} returns 200 with no Origin, 200 with
 *     `Origin: http://localhost:4200`, and 403 with `Origin: http://localhost:4296`. The Origin
 *     header is the only variable.
 *
 * So the harness presents an allow-listed Origin on the browser's API calls. This is a HARNESS
 * concession to a port that is not in the server allow-list — it does not stub, fake or soften any
 * response. Every status code, body and `{"success": false}` the server produces still reaches the
 * page unmodified, so an assertion that would fail against a same-origin deployment still fails here.
 *
 * NOTE FOR THE RELEASE OWNER: 4296 not being allow-listed is a real environment gap. The permanent
 * fix belongs in the backend config (add the port) or in the dev server (proxy /api so the browser is
 * same-origin), not in test code.
 */
const COMPILED_API_BASE = 'http://localhost:8082';
const ALLOWED_ORIGIN = 'http://localhost:4200';

export async function useAllowedApiOrigin(page: Page): Promise<void> {
  const target = (process.env['API_BASE_URL'] || COMPILED_API_BASE).replace(/\/+$/, '');

  await page.route(`${COMPILED_API_BASE}/**`, async route => {
    const request = route.request();
    const url = request.url().replace(COMPILED_API_BASE, target);

    // Preflight never reaches a controller, so it is answered here with the same permissive shape
    // dev-local would have produced for an allow-listed port.
    if (request.method() === 'OPTIONS') {
      await route.fulfill({
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': page.url().split('/').slice(0, 3).join('/'),
          'Access-Control-Allow-Credentials': 'true',
          'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,PATCH,OPTIONS',
          'Access-Control-Allow-Headers': '*',
          'Access-Control-Max-Age': '3600',
        },
      });
      return;
    }

    try {
      const response = await route.fetch({
        url,
        headers: { ...request.headers(), origin: ALLOWED_ORIGIN, referer: `${ALLOWED_ORIGIN}/` },
      });

      // The real status and body are forwarded untouched; only the CORS echo is rewritten so the
      // browser will surface them instead of discarding them as a cross-origin violation.
      const headers = { ...response.headers() };
      headers['access-control-allow-origin'] = page.url().split('/').slice(0, 3).join('/');
      headers['access-control-allow-credentials'] = 'true';

      await route.fulfill({ status: response.status(), headers, body: await response.body() });
    } catch {
      // A request still in flight when the page navigates cannot be fulfilled. Aborting it mirrors
      // what the browser does to a cancelled request; it never masks a server response, because a
      // response that arrived would have been forwarded above.
      await route.abort().catch(() => {});
    }
  });
}

/**
 * `test` for staff browser specs: applies the URL redirect and the allow-listed Origin.
 */
export const test = base.extend({
  page: async ({ page }, use) => {
    await redirectBrowserApiCalls(page);
    await useAllowedApiOrigin(page);
    await use(page);
  },
});

export { expect };

/**
 * Ages ONE complaint so that it is unambiguously past its SLA, and returns whether it worked.
 *
 * SLA-breach tests previously read `if (count > 0) assert; else annotate`, which passes on a database
 * containing no breach — the assertion simply never runs, so the test reported green while proving
 * nothing. An annotation does not affect exit status. The precondition therefore has to be
 * established rather than hoped for.
 *
 * No API can do it: no workflow action accepts a deadline and there is no test-support endpoint
 * (verified — `/api/v1/test-support/**` 404s). So the row is aged directly through the MySQL client.
 *
 * `created_at`/`filed_at` are moved back, not just `sla_deadline`, because the two SLA readers in
 * production derive the due date from the filing date and ignore the stored deadline:
 *   - TatCalculationService.calculateTat computes elapsed/remaining business hours from `filedAt`
 *     and never reads slaDeadline, so the task screen's TAT timer is filing-date driven.
 *   - ComplaintApiV1Controller:401 returns `slaDueDate = createdAt.plusDays(30)`, so the CEPC
 *     SLA indicator is too.
 * Setting only `sla_deadline` therefore changes nothing on screen (verified: the timer still read
 * "26d 6h remaining"). `sla_deadline` is set as well so the queue endpoint, which does read it
 * (WorkflowController:895), agrees.
 *
 * Only the single named complaint is touched — no table is ever truncated.
 */
export function ageComplaintPastSla(complaintNumber: string, daysOld = 45): boolean {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing to use an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  const days = Number(daysOld);
  const sql =
    `UPDATE complaints SET ` +
    `created_at = DATE_SUB(NOW(), INTERVAL ${days} DAY), ` +
    `filed_at = DATE_SUB(NOW(), INTERVAL ${days} DAY), ` +
    `sla_deadline = DATE_SUB(NOW(), INTERVAL ${days - 30} DAY) ` +
    `WHERE complaint_number = '${complaintNumber}';`;
  try {
    execFileSync(MYSQL_CLI, ['-u', 'cms_user', '-pcms_pass', 'cms_db', '-e', sql], {
      stdio: ['ignore', 'ignore', 'ignore'],
    });
    return true;
  } catch {
    return false;
  }
}

const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';
