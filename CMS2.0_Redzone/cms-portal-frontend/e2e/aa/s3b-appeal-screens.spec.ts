/**
 * S3B — the AA officer screens, driven through a real browser.
 *
 * These are BROWSER tests on purpose. Every other AA suite asserts at the API level, and the defects
 * this session fixed were invisible there: the API was answering correctly while the UI gated action
 * cards on statuses the backend cannot produce, bound to response keys that do not exist, and reported
 * success for writes the server had refused. Only rendering the page catches that class of bug.
 *
 * Requires a dev server. Port 4200 belongs to the developer, so the suite is skipped unless
 * S3B_UI_BASE_URL points at one (start your own: npx ng serve --port 4297).
 *
 * Fixtures are prefixed S3B- and purged in beforeAll and afterAll. cms_db is shared and holds real
 * complaints, so nothing here truncates.
 */
import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { sql, API_BASE } from './aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';
import { loginAsAaRole, KEYCLOAK_REALM_URL } from '../utils/auth';

const PREFIX = 'S3B';
const UI_BASE = process.env['S3B_UI_BASE_URL'] || '';

const DO = identityHeadersFor('aa_do_001', 'AA');
const REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');
const SECRETARIAT = identityHeadersFor('aa_secretariat_001', 'AA');

function seedClosedParent(complaintNumber: string, clause = '15(1)(a)'): void {
  purgeComplaint(complaintNumber);
  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, workflow_stage, closure_clause,
         entity_code, entity_name, priority, filing_type, scheme_version,
         created_at, updated_at, filed_at, closed_at)
       VALUES ('${complaintNumber}', 'S3B Appellant', 's3b@example.com', '9876543210',
         'S3B fixture', 'Seeded by the S3B UI suite', 'closed', NULL, '${clause}',
         'HDFC Bank', 'Fixture Entity', 'MEDIUM', 'CEPC_MANUAL', 'RBIOS_2021',
         NOW(), NOW(), NOW(), NOW())`);
}

/**
 * Releases placements so the officer pool cannot silently exhaust.
 *
 * Deleting an appeal does not release its assignment record, and aa_do_001 has a threshold of 20 — once
 * held records accumulate the engine correctly refuses to assign, and every filing test then fails for a
 * reason unrelated to the UI.
 */
function releasePlacements(): void {
  sql(`DELETE r FROM aa_assignment_record r
        WHERE r.released_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM appeals a
                           WHERE a.appeal_number COLLATE utf8mb4_unicode_ci
                                 = r.appeal_number COLLATE utf8mb4_unicode_ci)`);
}

function purgeComplaint(complaintNumber: string): void {
  // aa_assignment_record is utf8mb4_unicode_ci while appeals is utf8mb4_0900_ai_ci, so joining the two
  // without an explicit COLLATE raises "Illegal mix of collations" and fails the statement outright.
  const appealsOf = `SELECT appeal_number COLLATE utf8mb4_unicode_ci FROM appeals
                      WHERE original_complaint_number = '${complaintNumber}'`;
  sql(`DELETE FROM appeal_timeline
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM aa_assignment_record
        WHERE appeal_number COLLATE utf8mb4_unicode_ci IN (${appealsOf})`);
  sql(`DELETE FROM appeals WHERE original_complaint_number = '${complaintNumber}'`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`);
}

function purge(): void {
  sql(`SELECT complaint_number FROM COMPLAINTS WHERE complaint_number LIKE '${PREFIX}-%'`)
    .split('\n').map(s => s.trim()).filter(Boolean)
    .forEach(purgeComplaint);
  releasePlacements();
}

async function fileAppeal(request: APIRequestContext, complaintNumber: string): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    headers: DO,
    multipart: {
      complaintNumber, ground: 'S3B ground', details: 'S3B details', reliefSought: 'S3B relief',
    },
  });
  const raw = await res.text();
  expect(res.status(), `filing must succeed — server said: ${raw}`).toBe(201);
  return JSON.parse(raw).data.appealNumber;
}

async function act(request: APIRequestContext, appealNumber: string,
                   headers: Record<string, string>, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/action`, {
    headers, data: payload, failOnStatusCode: false,
  });
}

test.describe('S3B AA screens (browser)', () => {
  test.skip(!UI_BASE,
    'Set S3B_UI_BASE_URL to a running dev server. Port 4200 is the developer\'s and is never started here.');

  // Resolved ONCE, at module scope, and asserted rather than silently skipped. A per-test
  // test.skip(!keycloakUp) reads the flag at collection time — before beforeAll can set it — so every
  // test skipped even with Keycloak up, producing a green run that asserted nothing.
  test.beforeAll(async ({ request }) => {
    const realm = await request.get(KEYCLOAK_REALM_URL, { failOnStatusCode: false });
    expect(realm.status(),
      'Keycloak must be reachable for the AA staff screens — a skipped run proves nothing').toBe(200);
    purge();
  });

  test.afterAll(() => purge());
  test.beforeEach(() => releasePlacements());

  test('an AA_DO sees the actions the SERVER offers on a newly filed appeal', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-1`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    // What the server says this role may do. The UI must render exactly this and nothing else.
    const offered = (await (await request.get(
      `${API_BASE}/api/v1/appeals/${appeal}/available-actions`, { headers: DO })).json())
      .data.availableActions as string[];
    expect(offered, 'the server must offer a filed appeal some action').not.toEqual([]);

    await loginAsAaRole(page, 'AA_DO', `${UI_BASE}/aa/appeal/${appeal}`);
    await expect(page.getByTestId('action-list')).toBeVisible({ timeout: 20_000 });

    for (const action of offered) {
      await expect(page.getByTestId(`action-${action}`),
        `the UI must render the server-offered action ${action}`).toBeVisible();
    }

    // And nothing the server did NOT offer. A client-derived list drifting from the server is exactly
    // the defect that dead-ended this workflow.
    const rendered = await page.getByTestId('action-list').locator('button').count();
    expect(rendered).toBe(offered.length);
  });

  test('an AA_REVIEWER is NOT dead-ended after the appeal is accepted', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-2`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    // ACCEPT sets under_review — the status the old UI did not gate on, so a reviewer saw ZERO cards
    // and the workflow could not proceed past step two. This is the regression test for that.
    expect((await act(request, appeal, DO, { action: 'ACCEPT' })).status()).toBe(200);
    expect((await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' })).status()).toBe(200);

    await loginAsAaRole(page, 'AA_REVIEWER_1', `${UI_BASE}/aa/appeal/${appeal}`);
    await expect(page.getByTestId('action-list')).toBeVisible({ timeout: 20_000 });

    const count = await page.getByTestId('action-list').locator('button').count();
    expect(count, 'a reviewer on an accepted appeal must have at least one action').toBeGreaterThan(0);
    await expect(page.getByTestId('action-SCHEDULE_HEARING')).toBeVisible();
  });

  test('a disposed appeal offers a non-admin nothing, and says so', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-3`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, SECRETARIAT,
      { action: 'PASS_ORDER', orderOutcome: 'UPHELD', orderSummary: 'disposed for UI test' });

    await loginAsAaRole(page, 'AA_DO', `${UI_BASE}/aa/appeal/${appeal}`);

    // An explicit statement, not an empty panel: a blank action area is indistinguishable from a
    // loading failure.
    await expect(page.getByTestId('terminal-banner')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('action-list')).toHaveCount(0);
  });

  test('the order section renders from the FLAT response keys', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-4`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    const order = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/order`, {
      headers: SECRETARIAT,
      data: { outcome: 'MODIFIED', orderSummary: 'award revised on appeal', awardAmount: 7500 },
      failOnStatusCode: false,
    });
    expect(order.status()).toBeLessThan(400);

    await loginAsAaRole(page, 'AA_DO', `${UI_BASE}/aa/appeal/${appeal}`);

    // The template bound appeal().order.* while the payload is flat, so this whole section could never
    // render regardless of what had been ordered.
    await expect(page.getByTestId('order-section')).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId('order-outcome')).toContainText('MODIFIED');
  });

  test('SLA is rendered from the server, and overdue is unmistakable', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-5`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);

    await loginAsAaRole(page, 'AA_DO', `${UI_BASE}/aa/appeal/${appeal}`);
    await expect(page.getByTestId('sla-status')).toBeVisible({ timeout: 20_000 });

    // Backdate the stage clock so the server computes a breach. The deadline is server-side working-day
    // maths against the holiday master; the browser must not recompute it.
    sql(`UPDATE appeals SET updated_at = DATE_SUB(NOW(), INTERVAL 120 DAY)
          WHERE appeal_number = '${appeal}'`);
    const sla = (await (await request.get(`${API_BASE}/api/v1/appeals/${appeal}`,
      { headers: DO })).json()).data.sla;
    expect(sla.breached, 'the server must report this stage as breached').toBe(true);

    await page.reload();
    await expect(page.getByTestId('sla-overdue')).toBeVisible({ timeout: 20_000 });
  });

  test('a refused write is reported as a failure, never as success', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-6`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });

    await loginAsAaRole(page, 'AA_SECRETARIAT', `${UI_BASE}/aa/appeal/${appeal}`);
    await expect(page.getByTestId('action-list')).toBeVisible({ timeout: 20_000 });

    // Intercept the write and answer the way this API really refuses one: HTTP 200 with success:false.
    // Angular does not treat that as an error, which is why the screen used to report "Order passed
    // successfully" for orders the server had thrown away.
    await page.route('**/api/v1/appeals/*/order', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: false, messageKey: 'aa.order.error_failed' }),
      }));

    await page.getByTestId('action-PASS_ORDER').click();
    await page.getByTestId('outcome-UPHELD').click();
    await page.getByTestId('order-summary').fill('this write will be refused');
    await page.getByTestId('preview-order').click();
    await page.getByTestId('confirm-order').click();

    await expect(page.getByTestId('order-error')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('order-success')).toHaveCount(0);
  });

  test('the hearing screen shows real history and never claims a notice was sent',
    async ({ page, request }) => {
        const parent = `${PREFIX}-UI-7`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);
      await act(request, appeal, DO, { action: 'ACCEPT' });
      await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

      // A real hearing, through the endpoint that actually persists one.
      const scheduled = await request.post(`${API_BASE}/api/v1/appeals/${appeal}/hearings`, {
        headers: REVIEWER,
        data: {
          hearingDate: '2026-11-20T11:00:00', hearingVenue: 'Other',
          hearingMode: 'VIDEO', partiesToNotify: ['appellant'],
        },
        failOnStatusCode: false,
      });
      expect(scheduled.status()).toBeLessThan(400);

      await loginAsAaRole(page, 'AA_REVIEWER_1', `${UI_BASE}/aa/appeal/${appeal}`);
      await expect(page.getByTestId('action-list')).toBeVisible({ timeout: 20_000 });
      await page.getByTestId('action-SCHEDULE_HEARING').click();

      // The history table was bound to a field no endpoint returned, so it was permanently empty.
      await expect(page.getByTestId('hearing-history')).toBeVisible({ timeout: 15_000 });
      await expect(page.getByTestId('hearing-history').locator('tbody tr')).toHaveCount(1);

      // A sitting already exists, so this is a reschedule and the officer must be warned and give a
      // reason before vacating it.
      await expect(page.getByTestId('reschedule-warning')).toBeVisible();

      // And the notice copy must not promise delivery: there is no gateway in this deployment.
      await page.getByTestId('hearing-date').fill('2026-12-01');
      await page.getByTestId('hearing-time').fill('11:00');
      await page.getByTestId('hearing-venue').selectOption('Other');
      await page.getByTestId('hearing-reason').fill('presiding officer unavailable');
      await page.getByTestId('preview-notice').click();

      const caveat = page.getByTestId('notice-caveat');
      await expect(caveat).toBeVisible();
      await expect(caveat).not.toContainText(/will be sent|has been sent|notices sent/i);
    });

  test('a reviewer sees their tier', async ({ page, request }) => {
    const parent = `${PREFIX}-UI-8`;
    seedClosedParent(parent);
    const appeal = await fileAppeal(request, parent);
    await act(request, appeal, DO, { action: 'ACCEPT' });
    await act(request, appeal, DO, { action: 'ASSIGN_TO_BENCH' });

    // reviewer_tier is a real claim that nothing had ever surfaced, so a tier-1 reviewer had no way to
    // know escalation was open to them.
    await loginAsAaRole(page, 'AA_REVIEWER_1', `${UI_BASE}/aa/appeal/${appeal}`);
    await expect(page.getByTestId('reviewer-tier')).toBeVisible({ timeout: 20_000 });
  });

  test('every rendered label resolves — no raw translation keys leak to the screen',
    async ({ page, request }) => {
        const parent = `${PREFIX}-UI-9`;
      seedClosedParent(parent);
      const appeal = await fileAppeal(request, parent);

      await loginAsAaRole(page, 'AA_DO', `${UI_BASE}/aa/appeal/${appeal}`);
      await expect(page.getByTestId('action-list')).toBeVisible({ timeout: 20_000 });

      // An unseeded key renders as its own id. Catching "aa.detail.foo" on screen is the only way to
      // notice a key that was referenced but never seeded.
      const body = await page.locator('body').innerText();
      const leaked = body.match(/\baa\.[a-z_]+\.[a-z_0-9]+\b/g) ?? [];
      expect(leaked, `raw translation keys rendered: ${[...new Set(leaked)].join(', ')}`).toEqual([]);
    });
});
