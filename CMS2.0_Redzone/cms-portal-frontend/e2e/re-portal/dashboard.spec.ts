import { test, expect } from '../fixtures';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createForwardedComplaint } from '../utils/test-data';
import { installCorsShim } from '../public/browser-api';

/**
 * RE portal dashboard.
 *
 * ── THE `if (await table.isVisible())` PATTERN IS GONE ───────────────────────────────────────────
 *
 * Three tests here were shaped `if (something is visible) { assert } ` with no else, so an empty
 * dashboard satisfied them without executing a single assertion:
 *
 *   • 'Deadline countdown shows for pending complaints' — a nested `if (table) { if (countdown) { … } }`,
 *     so BOTH an empty table and a table with no countdown column passed. The deadline is the one
 *     number that tells an entity it is about to breach a statutory response window, and it was
 *     effectively untested.
 *   • 'Click navigates to complaint detail' — `if (firstRow) { click; assert URL }`, so a dashboard
 *     with no rows never navigated and never asserted. Row navigation is how an RE reaches a
 *     complaint at all.
 *   • 'Complaint table renders forwarded complaints' — an if/else that accepted `.empty-state` as an
 *     equally good outcome, which it is not when a complaint has just been forwarded to this entity.
 *
 * The precondition is now SEEDED: a complaint is forwarded to the signed-in entity in beforeAll, so
 * the table genuinely has a row and the assertions above have something to act on. The signed-in
 * account is re_pno_001, whose entity_code claim is what RE_ENTITY must match.
 *
 * ── TWO SEPARATE CAUSES OF THE EMPTY DASHBOARD ──────────────────────────────────────────────────
 *
 * 1. ENVIRONMENTAL, fixed here. GET /re-portal/dashboard was blocked by CORS in the browser whenever
 *    the dev server runs on a port outside cms-backend's allow-list, so the page rendered
 *    `.empty-state` with Total Forwarded = 0 even though the API returns 33 complaints to a direct
 *    call with the same re_pno_001 token. installCorsShim fixes it; see ../public/browser-api.ts.
 *
 * 2. PRODUCT DEFECT, still open — and this is why four tests below are RED. With CORS fixed the stat
 *    cards populate (Total Forwarded = 37) but the complaint TABLE is still never rendered, because
 *    the component and the endpoint disagree about the payload:
 *
 *      re-dashboard.component.ts:69   this.complaints.set(data.complaints || []);
 *      RePortalService.java:226-229   the stats map contains totalForwarded / pending / responded /
 *                                     breached / avgResponseDays — and NO `complaints` key.
 *
 *    GET /re-portal/dashboard is a statistics endpoint. The complaint list lives on a different
 *    endpoint, GET /re-portal/complaints, which this component never calls. So filteredComplaints()
 *    is permanently empty, the @else branch at re-dashboard.component.html:66 always wins, and an RE
 *    nodal officer sees "No complaints found" no matter how much work is forwarded to them. The
 *    deadline countdown, the status filter and row navigation are all unreachable as a result.
 *
 *    A SECOND, SMALLER MISMATCH in the same handler: the component reads `data.pendingResponse`
 *    (re-dashboard.component.ts:65) but the service emits `pending` (RePortalService.java:228), so the
 *    Pending card always shows 0.
 *
 *    Fix is one of: have the component also call GET /re-portal/complaints, or have the dashboard
 *    endpoint include the list. Both are production changes, outside this suite's ownership.
 */

/**
 * Must match the entity_code on the RE account these tests sign in as (re_pno_001), and must resolve
 * to a REGULATED_ENTITIES row by normalised name — the backend looks entities up that way, so an
 * opaque code matches nothing.
 */
const RE_ENTITY = process.env['RE_ENTITY_CODE'] || 'HDFC Bank';

test.describe('RE Portal Dashboard', () => {
  let keycloakUp: boolean;
  let seededComplaint: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
    if (!keycloakUp) return;

    // A row of our own, so "the table renders complaints" and "clicking a row opens it" are testable
    // rather than conditional on whatever the shared database happens to hold.
    const complaint = await createForwardedComplaint(request, RE_ENTITY, {
      subject: `RE dashboard fixture ${Date.now()}`,
    });
    seededComplaint = complaint.complaintNumber;
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping RE portal tests');
    // Installed BEFORE the login navigation, so the dashboard's own first fetch goes through it.
    await installCorsShim(page);
    await loginAsReRole(page, 'RE_NODAL_OFFICER');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('RE portal login and dashboard loads', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    const welcome = page.locator('.welcome-text');
    await expect(welcome).toBeVisible();
    expect(page.url()).toContain('/re-portal/dashboard');
  });

  test('Stats cards show (Total, Pending, Responded, Breached)', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    const statsGrid = page.locator('.stats-grid');
    await expect(statsGrid).toBeVisible({ timeout: 10000 });

    await expect(page.locator('.stat-card.total')).toBeVisible();
    await expect(page.locator('.stat-card.pending')).toBeVisible();
    await expect(page.locator('.stat-card.responded')).toBeVisible();
    await expect(page.locator('.stat-card.breached')).toBeVisible();

    // The total must reflect real work. Zero with a complaint just forwarded to this entity means the
    // dashboard is not reading the entity's own caseload, which the previous presence-only assertions
    // could not distinguish from a healthy but idle entity.
    const totalText = (await page.locator('.stat-card.total').innerText()).replace(/\D+/g, '');
    expect(Number(totalText || '0'), 'the seeded forwarded complaint must be counted').toBeGreaterThan(
      0
    );
  });

  /**
   * RED — OPEN PRODUCT DEFECT (payload key mismatch). Do not relax this to `toBeVisible()`.
   *
   * re-dashboard.component.ts:65 reads `data.pendingResponse`; RePortalService.java:228 emits
   * `pending`. The Pending card therefore always displays 0, so an RE officer cannot see how much work
   * is awaiting their response — while Total Forwarded, whose key does match, displays correctly.
   */
  test('the Pending card reflects the pending count from the API', async ({ page, request }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator('.stat-card.pending')).toBeVisible({ timeout: 10000 });

    // The authoritative figure, straight from the endpoint the page itself calls.
    const api = await request.get(`${process.env['API_BASE_URL'] || 'http://localhost:8082'}/api/v1/re-portal/dashboard`, {
      headers: {
        'X-User-Id': 're_pno_001',
        'X-User-Roles': 'RE_PNO',
        'X-Entity-Code': RE_ENTITY,
      },
    });
    expect(api.status()).toBe(200);
    const apiPending = (await api.json()).data.pending as number;
    expect(apiPending, 'the fixture leaves at least one complaint pending').toBeGreaterThan(0);

    const shown = Number(
      (await page.locator('.stat-card.pending').innerText()).replace(/\D+/g, '') || '0'
    );
    expect(
      shown,
      'the Pending card shows 0 because the component reads data.pendingResponse while the API ' +
        'emits data.pending'
    ).toBe(apiPending);
  });

  /**
   * RED — OPEN PRODUCT DEFECT. The complaint list is never fetched.
   *
   * re-dashboard.component.ts:60 calls only GET /re-portal/dashboard and then reads
   * `data.complaints` (line 69), which that endpoint does not return — it is a stats endpoint
   * (RePortalService.java:226-229). The list endpoint GET /re-portal/complaints is never called, so
   * filteredComplaints() is always empty and the @else at re-dashboard.component.html:66 renders
   * "No complaints found" for every entity regardless of their caseload.
   *
   * The previous version of this test accepted `.empty-state` as an equally valid outcome via an
   * if/else, which is exactly how this shipped unnoticed.
   */
  test('Complaint table renders forwarded complaints', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });

    // The table, NOT the empty state. A complaint was forwarded to this entity in beforeAll, so an
    // empty state here is the dashboard failing to show work that exists.
    const table = page.locator('.complaints-table');
    await expect(table).toBeVisible({ timeout: 15000 });

    const headerTexts = await table.locator('thead th').allTextContents();
    const joinedHeaders = headerTexts.join(' ').toLowerCase();
    expect(joinedHeaders).toContain('complaint');
    expect(joinedHeaders).toContain('status');

    await expect(page.locator('.complaint-row').first()).toBeVisible();
  });

  /**
   * The dropdown expresses the RE's OWN view of the work ("pending a reply from us"), which is not
   * the complaint's stored status: the options are pending/responded/breached while the API returns
   * forwarded/re_responded/closed. The component therefore maps the option onto the statuses it
   * covers — `pending` means `forwarded`.
   *
   * Asserting the badge text matched /pending/ would re-encode the very confusion that caused the
   * bug (a direct `c.status === status` comparison that could never match). What matters is that the
   * filter NARROWS the list and that every surviving row is one the option legitimately covers.
   */
  test('Status filter works', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator('.complaints-table')).toBeVisible({ timeout: 15000 });

    const filterSelect = page.locator('.status-filter');
    await expect(filterSelect).toBeVisible();

    const unfiltered = await page.locator('.complaint-row').count();
    expect(unfiltered).toBeGreaterThan(0);

    await filterSelect.selectOption('pending');
    await page.waitForTimeout(500);

    const statusBadges = page.locator('tbody .status-badge');
    const count = await statusBadges.count();
    // A filter that empties the table is not evidence it filtered correctly, so both the survivors
    // and the fact that there ARE survivors are asserted.
    expect(count, "selecting 'pending' must leave the forwarded complaints visible").toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const text = (await statusBadges.nth(i).textContent())?.toLowerCase() ?? '';
      // 'pending' covers exactly the forwarded-awaiting-reply state.
      expect(text, 'a row kept by the pending filter must be awaiting the entity’s reply')
        .toMatch(/forwarded|pending/);
    }
    // And it must genuinely EXCLUDE something, otherwise "filtering" is a no-op.
    expect(count, 'the pending filter must exclude responded/closed rows').toBeLessThan(unfiltered);

    await filterSelect.selectOption('');
    await expect.poll(() => page.locator('.complaint-row').count()).toBe(unfiltered);
  });

  /**
   * PENDING on a SERVER gap, not a frontend one — and the most consequential item left on this screen.
   *
   * The grid is now populated (the list endpoint is fetched), but GET /re-portal/complaints returns no
   * deadline: a row carries complaintNumber, subject, complainantName, status, priority, createdAt,
   * filingType and the reActivity* fields only. RePortalService computes the window internally
   * (forwardedAt + windowDays, RePortalService.java:332) but never emits either value, so there is
   * nothing for the countdown to render and the column shows '-'.
   *
   * Deliberately NOT "fixed" by asserting '-': the deadline countdown is how a regulated entity
   * learns a statutory response window is closing, so a passing test here must mean a real date is
   * shown. Fixing it properly means adding forwardedAt and responseDeadline to the list row, which is
   * a server contract change and is reported as an outstanding item rather than guessed at now.
   */
  test.fixme(
    'Deadline countdown shows for pending complaints',
    async ({ page }) => {
      await page.waitForSelector('.re-dashboard', { timeout: 15000 });
      await expect(page.locator('.complaints-table')).toBeVisible({ timeout: 15000 });

      const daysRemaining = page.locator('.days-remaining').first();
      await expect(
        daysRemaining,
        'the dashboard must show a response deadline for a pending complaint'
      ).toBeVisible({ timeout: 10000 });

      const text = (await daysRemaining.textContent())?.trim() ?? '';
      // A real countdown, not a placeholder: 'NaN', '-' and an empty span must all fail.
      expect(text, `deadline countdown reads "${text}"`).toMatch(/\d/);
      expect(text).not.toMatch(/NaN|undefined|null/i);
    }
  );

  /**
   * RED — blocked by the same defect. With no rows there is nothing to click, so an RE officer cannot
   * reach a complaint from the dashboard at all.
   */
  test('Click navigates to complaint detail', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator('.complaints-table')).toBeVisible({ timeout: 15000 });

    // Unconditional: `if (firstRow.isVisible())` made this a no-op on an empty dashboard.
    const firstRow = page.locator('.complaint-row').first();
    await expect(firstRow).toBeVisible({ timeout: 10000 });

    const rowComplaintNumber = (
      await firstRow.locator('td').first().textContent()
    )?.trim();

    await firstRow.click();
    await page.waitForURL(/\/re-portal\/complaints\//, { timeout: 10000 });
    expect(page.url()).toMatch(/\/re-portal\/complaints\//);

    // The row must open ITS OWN complaint. A navigation that always lands on the same record would
    // satisfy the URL pattern above while being badly wrong.
    if (rowComplaintNumber) {
      expect(page.url()).toContain(rowComplaintNumber);
    }
  });
});
