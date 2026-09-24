import { test, expect } from '../fixtures';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { identityHeadersFor } from '../utils/test-data';

/**
 * Manual QA cases 10-17 — the REPORTS section, searching reports BY NAME, and scope enforcement.
 *
 * ── Deliberately NOT duplicating e2e/admin/s7-report-operators.spec.ts ────────────────────────────
 * That spec already covers filter OPERATORS (BETWEEN/IN/LIKE/GREATER_THAN/RANGE, their bounds and
 * refusals), report ACCESS failing closed for unidentified/forged callers, export capability, and
 * drill-down existence. Nothing here repeats any of it. This spec is about a different thing: finding
 * a report BY ITS NAME, and whether the rows a report returns are bounded to the caller's territory
 * by the SERVER.
 *
 * ── The two reports surfaces, verified against source ─────────────────────────────────────────────
 *   • /staff/reports → ReportBuilderComponent (app.routes.ts:238-241, staffAuthGuard). Calls
 *     /api/v1/reports/* (ReportBuilderController.java:36). It composes a report from SUBJECT + FILTER
 *     tokens. There are no NAMED reports here at all, and the only search box on the screen is
 *     `.filter-search` "Search filters..." (report-builder.component.html:104-108), which filters the
 *     FILTER token list via filterSearch/filteredFilters (component.ts:103, 185-191). It does not
 *     search reports.
 *   • /crpc/reports → CrpcReportsComponent (app.routes.ts:295-301). It DOES have seven named reports
 *     ("Daily Intake Report" … "Entity-Wise Report", component.ts:31-61) rendered as `.report-card`
 *     tiles, but there is NO search input anywhere in its template — only From/To/Scheme/Office
 *     filters (crpc-reports.component.html:20-51). It is also CRPC-gated server-side
 *     (SecurityConfig.java:176 → /api/v1/crpc/** hasAnyRole(CRPC_ROLES)).
 *
 * So "search reports by report name" (QA 12-15) has no implementation on either screen. Also grepped
 * the whole frontend and backend for "Annual": ZERO matches, so the QA case's example report names
 * ("Annual Reports", "CEPC Complaints") do not exist as reports in this product.
 *
 * ── CEPC report access, verified live on 8092 ─────────────────────────────────────────────────────
 * REPORT_ACCESS_ROLE (seeded by V98) holds exactly 8 rows: ADMIN, RBIO_ADMIN, RBIO_OMBUDSMAN,
 * RBIO_DEPUTY_OMBUDSMAN, RBIO_SECRETARY, AA_SECRETARIAT, CEPD_ADMIN, AA_ADMIN. No CEPC_* role appears.
 * Confirmed with a REAL Keycloak JWT for cepc_admin1 (realm role CEPC_ADMIN): POST
 * /api/v1/reports/execute → 403 reports.error_access_denied. cms.admin (ADMIN) → 200.
 *
 * ── Scope, verified live ──────────────────────────────────────────────────────────────────────────
 * All 8 granted roles are ALSO in ReportAccessService.UNRESTRICTED_ROLES (ReportAccessService.java:60-62),
 * and RBIO_STAFF_PROFILE has 0 rows, so the department-scoping branch
 * (ReportAccessService.java:128-146 → QueryCompiler.buildAuthScope:342-351) is unreachable with the
 * shipped data: every role that can run a report sees all 7861 complaints. Separately,
 * /api/v1/crpc/reports takes `office` as a plain caller-supplied query parameter and scopes on it with
 * no authorisation check at all (CrpcReportService.java:265-274) — a CEPC officer can read another
 * department's figures by naming it.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

const REPORTS = `${API_BASE}/api/v1/reports`;

/** A CEPC identity that a bank would describe as "the authorised CEPC reporting user". */
const CEPC_REPORTING_ACTOR = 'cepc_admin_001';

function cepcAdminHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': CEPC_REPORTING_ACTOR,
    'X-User-Name': CEPC_REPORTING_ACTOR,
    'X-User-Roles': 'CEPC_ADMIN',
  };
}

function grantedAdminHeaders(): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': 'cms.admin',
    'X-User-Name': 'cms.admin',
    'X-User-Roles': 'ADMIN',
  };
}

/**
 * The search control for finding a report by NAME on the reports screen.
 *
 * Intentionally broad — any of a testid, an aria-label or a recognisable placeholder counts, so the
 * test fails because the CONTROL is absent rather than because a selector was guessed wrong. The
 * existing "Search filters..." box is excluded: it searches filter tokens, not reports.
 */
function reportNameSearch(page: import('@playwright/test').Page) {
  return page.locator(
    [
      '[data-testid="report-name-search"]',
      'input[aria-label*="report" i]',
      'input[placeholder*="search report" i]',
      'input[placeholder*="report name" i]',
    ].join(', ')
  );
}

/** The list of named/standard reports offered to the user. */
function reportList(page: import('@playwright/test').Page) {
  return page.locator('.report-card, [data-testid="report-list-item"], .report-list-item');
}

test.describe('CEPC reports — access, search by name, and scope (QA 10-17)', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const probe = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(probe);
    await probe.close();
  });

  // ── QA 10 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA10: the authorised CEPC user can log in to CMS', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/cepc/tasks');

    expect(page.url(), 'the browser must have returned from Keycloak').not.toContain('/realms/');
    expect(page.url(), 'the session must be authenticated').not.toContain('/staff/login');
    await expect(page.locator('h2:has-text("CEPC")')).toBeVisible({ timeout: 15000 });

    await logout(page);
  });

  // ── QA 11 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA11: a CEPC user HAS access to the reports section — the screen loads and the server serves it', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');

    // The route is reachable (staffAuthGuard only).
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });
    expect(page.url(), 'a CEPC user must not be bounced off the reports route').toContain('/staff/reports');

    // But "having access" means the server serves report data to this user, not merely that a page
    // rendered. An error banner here is a denial, and denial is a failure of this case.
    await expect(
      page.locator('.error-banner'),
      'the reports screen must not show an error banner for an authorised CEPC user'
    ).toHaveCount(0);

    await logout(page);
  });

  test('QA11b: the SERVER grants report access to the authorised CEPC role', async ({ request }) => {
    // The UI-level check above cannot distinguish "allowed" from "denied but rendered anyway", so the
    // grant is asserted directly. REPORT_ACCESS_ROLE decides this (ReportAccessService.java:96-121).
    const res = await request.post(`${REPORTS}/execute`, {
      headers: cepcAdminHeaders(),
      data: { subjectId: 'count', sentence: 'the number of complaints', filters: [] },
      failOnStatusCode: false,
    });

    expect(
      res.status(),
      `CEPC_ADMIN was refused report access: ${await res.text()}`
    ).toBe(200);
  });

  // ── QA 12 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA12: the reports section offers a search BY REPORT NAME', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });

    await expect(
      reportNameSearch(page),
      'the reports section must provide a control for searching reports by name'
    ).toHaveCount(1);
    await expect(reportNameSearch(page)).toBeEditable();

    await logout(page);
  });

  // ── QA 13 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA13: searching a report name displays the matching reports', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });

    const all = await reportList(page).allTextContents();
    expect(
      all.length,
      'the reports section must list named reports before a name search can be meaningful'
    ).toBeGreaterThan(1);

    // Search for the full name of the first listed report; only that report may remain.
    const target = (all[0] || '').split('\n')[0].trim();
    await reportNameSearch(page).fill(target);

    await expect
      .poll(async () => reportList(page).count(), {
        message: 'searching a report name must narrow the list',
        timeout: 8000,
      })
      .toBeLessThan(all.length);

    const remaining = await reportList(page).allTextContents();
    expect(remaining.length, 'the matching report must still be listed').toBeGreaterThan(0);
    for (const r of remaining) {
      expect(r.toLowerCase(), `"${r}" does not match the searched name "${target}"`)
        .toContain(target.toLowerCase().slice(0, Math.min(target.length, 12)));
    }

    await logout(page);
  });

  // ── QA 14 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA14: a PARTIAL report name also finds the report', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });

    const all = await reportList(page).allTextContents();
    expect(all.length, 'named reports must be listed').toBeGreaterThan(0);

    const fullName = (all[0] || '').split('\n')[0].trim();
    const partial = fullName.slice(0, Math.max(4, Math.floor(fullName.length / 2)));

    await reportNameSearch(page).fill(partial);

    await expect
      .poll(async () => (await reportList(page).allTextContents()).some(t => t.includes(fullName)), {
        message: `a partial name "${partial}" must still surface "${fullName}"`,
        timeout: 8000,
      })
      .toBeTruthy();

    await logout(page);
  });

  // ── QA 15 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA15: an invalid or blank report name yields a no-results state, not an error and not the full list', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });

    const total = await reportList(page).count();
    expect(total, 'named reports must be listed').toBeGreaterThan(0);

    // An invalid name: no report may match, and the screen must say so rather than erroring.
    await reportNameSearch(page).fill('zzz-no-such-report-zzz');

    await expect
      .poll(async () => reportList(page).count(), {
        message: 'an invalid report name must match no reports',
        timeout: 8000,
      })
      .toBe(0);
    await expect(
      page.locator('.empty-results, .empty-state, .no-results'),
      'an invalid report name must show a no-results state'
    ).toBeVisible();
    await expect(
      page.locator('.error-banner'),
      'an unmatched search is not an error condition'
    ).toHaveCount(0);

    // A blank search must not be treated as a match-everything query that quietly re-reveals the full
    // unfiltered catalogue while the user believes a search is applied. Per the QA case, blank is a
    // no-results state.
    await reportNameSearch(page).fill('   ');
    await expect
      .poll(async () => reportList(page).count(), {
        message: 'a blank report name must not display the full unfiltered list',
        timeout: 8000,
      })
      .toBeLessThan(total);

    await logout(page);
  });

  // ── QA 16 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA16: opening a standard report generates it for the CEPC user', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not reachable — a genuine staff login cannot be performed');

    await loginAsCepcRole(page, 'ADMIN', '/staff/reports');
    await expect(page.locator('h1:has-text("Report Builder")')).toBeVisible({ timeout: 20000 });

    // Compose and run the simplest standard report: the complaint list.
    await page.locator('.token-btn', { hasText: 'all complaints' }).first().click();

    // The server's answer is captured rather than inferred from the screen. Asserting "no error
    // banner" alone would race the in-flight request and pass before a 403 had even arrived.
    const executePromise = page.waitForResponse(
      r => r.url().includes('/api/v1/reports/execute') && r.request().method() === 'POST',
      { timeout: 25000 }
    );
    await page.locator('button:has-text("Run Report")').click();
    const execute = await executePromise;

    expect(
      execute.status(),
      `running a standard report answered ${execute.status()}: ${await execute.text()}`
    ).toBe(200);

    await expect(
      page.locator('.error-banner'),
      'running a standard report must not produce an error for an authorised CEPC user'
    ).toHaveCount(0);

    await expect(page.locator('.tab-btn:has-text("Results")')).toBeEnabled({ timeout: 20000 });
    await page.locator('.tab-btn:has-text("Results")').click();
    await expect(page.locator('.results-table')).toBeVisible({ timeout: 15000 });
    await expect
      .poll(async () => page.locator('.results-table tbody tr').count(), {
        message: 'the generated report must contain rows',
        timeout: 15000,
      })
      .toBeGreaterThan(0);

    await logout(page);
  });

  test('QA16b: the same standard report DOES generate for an access-granted role', async ({ request }) => {
    // Isolates QA16's cause. If this passes while QA16/QA11b fail, the report engine works and the
    // only thing missing is a REPORT_ACCESS_ROLE grant for the CEPC role — a configuration defect, not
    // a broken report.
    const res = await request.post(`${REPORTS}/execute`, {
      headers: grantedAdminHeaders(),
      data: { subjectId: 'list', sentence: 'all complaints', filters: [] },
      failOnStatusCode: false,
    });

    expect(res.status(), await res.text()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.results)).toBeTruthy();
    expect(body.results.length, 'the standard complaint list must return rows').toBeGreaterThan(0);
  });

  // ── QA 17 ───────────────────────────────────────────────────────────────────────────────────────
  test('QA17: a report is limited to the records the caller is authorised to see, enforced SERVER-side', async ({ request }) => {
    // Part 1 — the caller may not nominate their own territory. ReportBuilderController used to bind
    // X-User-Department and treat blank as unrestricted; that is fixed, and this pins it.
    const forged = await request.post(`${REPORTS}/execute`, {
      headers: { ...cepcAdminHeaders(), 'X-User-Department': '', 'X-User-Role': 'SENIOR' },
      data: { subjectId: 'count', sentence: 'the number of complaints', filters: [] },
      failOnStatusCode: false,
    });
    expect(
      forged.status(),
      'a caller must not obtain report data by nominating a blank department or the SENIOR pseudo-role'
    ).not.toBe(200);

    // Part 2 — a role that is granted report access must actually BE scoped to something, or "the
    // records they are authorised to see" has no meaning. Every role in REPORT_ACCESS_ROLE being
    // unrestricted makes the scope filter dead code.
    const accessRows = await request.get(`${REPORTS}/access-roles`, { headers: grantedAdminHeaders() });
    expect(accessRows.status(), await accessRows.text()).toBe(200);
    const rows: Array<{ roleName: string }> = await accessRows.json();

    const UNRESTRICTED = new Set([
      'ADMIN', 'RBIO_ADMIN', 'CEPD_ADMIN', 'AA_ADMIN', 'AA_SECRETARIAT',
      'RBIO_OMBUDSMAN', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_SECRETARY',
    ]);
    const scopedRoles = rows.filter(r => !UNRESTRICTED.has(r.roleName));
    expect(
      scopedRoles.map(r => r.roleName),
      'at least one report-access role must be territory-scoped, otherwise every report reader sees everything and the scope filter is unreachable'
    ).not.toEqual([]);
  });

  test('QA17b: the office a report is scoped to is NOT taken from a caller-supplied parameter', async ({ request }) => {
    // /api/v1/crpc/reports accepts `office` as a plain query parameter and filters on it with no
    // authorisation check (CrpcReportService.appendOffice:265-274). If a CEPC officer can name RBIO and
    // receive RBIO figures, the scope is a client-side convenience, not a control — which the QA case
    // explicitly calls out as a defect worth reporting.
    const ownDept = await request.get(`${API_BASE}/api/v1/crpc/reports`, {
      params: { reportType: 'entity-wise', office: 'CEPC' },
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });
    expect(ownDept.status(), await ownDept.text()).toBe(200);

    const otherDept = await request.get(`${API_BASE}/api/v1/crpc/reports`, {
      params: { reportType: 'entity-wise', office: 'RBIO' },
      headers: identityHeadersFor('cepc_do_001'),
      failOnStatusCode: false,
    });

    const body = await otherDept.json().catch(() => ({ rowCount: -1 }));
    const leaked = otherDept.status() === 200 && Number(body.rowCount) > 0;

    expect(
      leaked,
      `a CEPC officer received ${body.rowCount} rows of RBIO report data by naming office=RBIO; ` +
        'the office scope must come from the caller\'s posting, not from a request parameter'
    ).toBeFalsy();
  });
});
