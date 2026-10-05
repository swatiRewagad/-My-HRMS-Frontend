import { test, expect, ageComplaintPastSla } from '../rbio/harness';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus } from '../utils/test-data';

/**
 * Several tests here previously guarded their only assertion behind an `if` with no `else` — one
 * carried the comment "If no overdue complaints, that is also a valid state (just skip assertion)".
 * Those preconditions are now seeded so the assertions always run.
 *
 * The selectors were also wrong throughout: the dashboard renders `.data-grid`, `.stats-bar`,
 * `.queue-select` and `h1`, not `.complaints-table`, `.stats-row`, `.filter-select` or an
 * `h2:has-text("CEPC - Complaint Management")`. Those tests were failing for the wrong reason.
 */
test.describe('CEPC Dashboard', () => {
  let keycloakUp: boolean;
  let seededComplaint: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      // Every grid assertion below needs at least one row that belongs to this DO, otherwise it is
      // asserting against an empty table and cannot fail.
      const result = await createTestComplaint(request, { subject: 'S4-CEPC Dashboard Fixture' });
      seededComplaint = result.complaintNumber;
      await advanceToStatus(request, seededComplaint, 'in_progress');
    }
  });

  test.afterAll(async ({ request }) => {
    if (seededComplaint) {
      await cleanupComplaint(request, seededComplaint);
    }
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping CEPC dashboard tests');
    await loginAsCepcRole(page, 'DO');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('page loads and shows the CEPC complaints heading', async ({ page }) => {
    await expect(page.locator('h1:has-text("CEPC Complaints")')).toBeVisible({ timeout: 15000 });
  });

  test('stats cards display and carry numeric counts', async ({ page }) => {
    const statsBar = page.locator('.stats-bar');
    await expect(statsBar).toBeVisible({ timeout: 10000 });

    // These five are the RBIO KPI set the CEPC dashboard was homogenised onto — they are
    // role-relevant QUEUES a DO can act on, not a roll-call of statuses. The previous expectation
    // ('Total Complaints', 'Pending', 'Under Examination', 'Under Review', 'Escalated') described
    // the pre-homogenisation cards and became stale when the component adopted
    // cepc.stats.total_pending / pending_with_me / pending_contact_person / pending_meeting /
    // sla_breach (cepc-dashboard.component.html:41-73). Verified against
    // GET /api/v1/i18n/translations/en rather than hardcoded guesses.
    const labels = (await statsBar.locator('.stat-label').allTextContents()).map(l => l.trim());
    expect(labels).toEqual(
      expect.arrayContaining([
        'Total Pending Complaints',
        'Pending with Me',
        'Pending with Contact Person',
        'Meeting Scheduled',
        'SLA Breach',
      ])
    );

    // A stat card showing a blank or NaN is a failure, not a pass.
    const values = await statsBar.locator('.stat-value').allTextContents();
    expect(values.length).toBe(labels.length);
    for (const v of values) {
      expect(v.trim(), `a stat card read "${v}"`).toMatch(/^\d+$/);
    }

    // The seeded complaint must be counted.
    const total = Number((await statsBar.locator('.stat-card').first().locator('.stat-value').textContent())?.trim());
    expect(total, 'the total must include the seeded complaint').toBeGreaterThan(0);
  });

  test('complaint table renders with the expected columns', async ({ page }) => {
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    // Previously `if (await table.isVisible()) {...} else { expect(empty-state) }` — an empty grid
    // satisfied it. A row is seeded in beforeAll, so the grid must be populated.
    const rows = page.locator('.data-grid tbody tr:not(:has(.empty-state))');
    await expect
      .poll(async () => rows.count(), { message: 'the seeded complaint must appear in the grid' })
      .toBeGreaterThan(0);

    const headers = (await page.locator('.data-grid thead tr:first-child th').allTextContents())
      .map(h => h.trim())
      .filter(h => h !== '');
    expect(headers).toEqual(
      expect.arrayContaining([
        'Complaint Number', 'Complainant', 'Entity', 'Subject', 'Priority', 'Status', 'SLA Due',
      ])
    );
  });

  test('advanced search narrows the grid to the searched complaint', async ({ page }) => {
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const rows = page.locator('.data-grid tbody tr:not(:has(.empty-state))');
    await expect
      .poll(async () => rows.count(), { message: 'rows must exist before filtering is meaningful' })
      .toBeGreaterThan(0);

    // Searching the seeded number must leave exactly that row. The old test only checked that an
    // empty result showed the empty state, which an always-empty grid satisfied.
    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill(seededComplaint);
    await dialog.locator('button:has-text("Search")').click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    await expect
      .poll(
        async () => (await rows.allTextContents()).filter(t => !t.includes(seededComplaint)).length,
        { message: 'searching a complaint number must exclude every other row', timeout: 8000 }
      )
      .toBe(0);
    await expect(rows, 'the searched complaint itself must remain').toHaveCount(1);

    await page.locator('button:has-text("Clear Filters")').click();
    await expect.poll(async () => rows.count(), { timeout: 8000 }).toBeGreaterThan(1);
  });

  /**
   * Was `test.fixme` for a REAL reactivity defect: `columnFilters` on the dashboard was a plain
   * object read inside the `filteredComplaints` computed, so `[(ngModel)]` mutated a property no
   * signal observed and the computed never recomputed — typing an impossible value left every row
   * on screen.
   *
   * Fixed, and in a better place than the original fixme anticipated: the dashboard now delegates to
   * the shared `app-task-grid`, which owns the filter boxes itself
   * (task-grid.component.html:94-110 `input.col-search[data-column]`) and holds them in a genuine
   * signal updated through `setColumnFilter` → `columnFilters.update(...)`
   * (task-grid.component.ts:141,346-348), feeding its own `filtered` computed (line 227).
   * Un-fixme'd and asserted end-to-end rather than taken on trust.
   */
  test('per-column search boxes filter the grid', async ({ page }) => {
    await page.waitForSelector('.data-grid', { timeout: 15000 });

    const rows = page.locator('[data-testid="task-grid-row"]');
    await expect
      .poll(async () => rows.count(), { message: 'rows must exist before filtering is meaningful' })
      .toBeGreaterThan(0);
    const before = await rows.count();

    // An impossible value must empty the grid. Under the old defect this left `before` rows.
    const subjectFilter = page.locator('input.col-search[data-column="subject"]');
    await expect(subjectFilter, 'the grid must offer a per-column search box').toHaveCount(1);
    await subjectFilter.fill('zzz-no-such-subject-zzz');
    await expect
      .poll(async () => rows.count(), {
        message: 'an unmatchable per-column filter must leave no rows',
        timeout: 8000,
      })
      .toBe(0);

    // And a real value must narrow to the seeded row rather than merely clearing everything.
    await subjectFilter.fill('S4-CEPC Dashboard Fixture');
    await expect
      .poll(async () => rows.count(), {
        message: 'a matching per-column filter must restore the matching rows',
        timeout: 8000,
      })
      .toBeGreaterThan(0);
    expect(await rows.count(), 'the filter must still exclude non-matching rows').toBeLessThanOrEqual(before);
    for (const t of await rows.allTextContents()) {
      expect(t, 'every remaining row must match the column filter').toContain('S4-CEPC Dashboard Fixture');
    }
  });

  /**
   * Was `test.fixme` for a REAL case-mismatch defect: the status dropdown emitted lowercase values
   * while `GET /workflow/cepc/tasks` returns status UPPERCASED
   * (`task.put("status", c.getStatus().toUpperCase())`), so `'IN_PROGRESS' === 'in_progress'` was
   * false and choosing "Under Examination" emptied a grid that held such complaints.
   *
   * Fixed on BOTH sides: `mapComplaint` now lower-cases the incoming status
   * (cepc-dashboard.component.ts:450) and the dropdown no longer carries a fixed option list — it is
   * generated from `visibleBuckets()`, whose predicates match the normalised value
   * (allBuckets, component.ts:206-232). The fixed list was itself a second defect: it stranded
   * FORWARDED / RE_RESPONDED / INFO_REQUESTED complaints behind no reachable option at all.
   */
  test('status filter narrows the grid to the chosen status', async ({ page }) => {
    await page.waitForSelector('.data-grid', { timeout: 15000 });

    const rows = page.locator('[data-testid="task-grid-row"]');
    await expect
      .poll(async () => rows.count(), { message: 'rows must exist before filtering is meaningful' })
      .toBeGreaterThan(0);
    const before = await rows.count();

    // The queue select is generated from the occupied buckets, so "Under Examination" is only
    // offered when such complaints are loaded — and beforeAll advances the fixture to in_progress.
    const statusSelect = page.locator('select.queue-select').first();
    await expect(
      statusSelect.locator('option[value="in_progress"]'),
      'the bucket the fixture sits in must be offered as an option'
    ).toHaveCount(1);

    await statusSelect.selectOption('in_progress');

    // The defect's signature was an EMPTY grid, so the load-bearing assertion is that rows survive.
    await expect
      .poll(async () => rows.count(), {
        message: 'selecting an occupied status bucket must not empty the grid',
        timeout: 8000,
      })
      .toBeGreaterThan(0);
    expect(await rows.count(), 'a status filter must narrow, not widen').toBeLessThanOrEqual(before);

    // Every surviving row must actually carry that status, or the filter is decorative.
    const statuses = await rows.locator('td[data-column="status"]').allTextContents();
    for (const s of statuses) {
      expect(s.trim(), `a row in the Under Examination bucket read "${s}"`).toContain('Under Examination');
    }
  });

  test('pagination reports a page range and disables Previous on page one', async ({ page }) => {
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    // Previously the whole body was inside `if (await pagination.isVisible())`, so a missing
    // pagination bar passed.
    const pagination = page.locator('.pagination');
    await expect(pagination, 'the dashboard must render a pagination bar').toBeVisible({ timeout: 10000 });

    const infoText = (await page.locator('.page-info').textContent())?.trim() ?? '';
    expect(infoText, `page info read "${infoText}"`).toMatch(/Showing \d+ to \d+ of \d+ entries/);
    // "Showing 0 to 0 of 0 entries" would mean the grid never loaded.
    expect(infoText, 'the grid must have entries to paginate').not.toMatch(/of 0 entries/);

    const prevBtn = page.locator('.page-controls .page-btn').first();
    await expect(prevBtn, 'Previous must be disabled on the first page').toBeDisabled();
  });

  test('Create Complaint dialog opens and creates a complaint', async ({ page, request }) => {
    const createBtn = page.locator('.create-btn');
    await expect(createBtn).toBeVisible({ timeout: 15000 });
    await createBtn.click();

    const modal = page.locator('.modal-overlay');
    await expect(modal).toBeVisible();

    const dialog = page.locator('.modal-dialog.create-dialog');
    await expect(dialog).toBeVisible();

    const subject = `S4-CEPC Dashboard Create ${Date.now().toString(36)}`;
    await dialog.locator('input[placeholder="Full name"]').fill('S4 Test Citizen');
    await dialog.locator('input[placeholder*="subject" i]').fill(subject);
    await dialog.locator('input[placeholder*="Email address" i]').fill('s4@test.com');
    await dialog.locator('textarea').fill('Automated test from Playwright');

    await dialog.locator('button:has-text("Create Complaint")').click();

    // The old version accepted `successMsg.or(errorMsg)` — "both are acceptable outcomes" — so a
    // creation that failed outright passed. Only success is acceptable.
    const successMsg = page.locator('.success-msg');
    await expect(successMsg, 'creating a complaint must report success, not an error')
      .toBeVisible({ timeout: 15000 });
    await expect(page.locator('.error-msg')).toHaveCount(0);

    // And it must actually exist server-side, not merely flash a banner.
    const listRes = await request.get(
      `${(process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '')}/api/v1/workflow/cepc/tasks?role=CEPC_DO`,
      { headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' } }
    );
    expect(listRes.ok()).toBe(true);
    const created = ((await listRes.json()).data as any[]).find(c => c.subject === subject);
    expect(created, 'the newly created complaint must be persisted and visible to the DO').toBeTruthy();

    if (created?.complaintNumber) {
      await cleanupComplaint(request, created.complaintNumber);
    }
    await page.keyboard.press('Escape');
  });

  test('Create Complaint validates required fields (name, subject)', async ({ page }) => {
    const createBtn = page.locator('.create-btn');
    await expect(createBtn).toBeVisible({ timeout: 15000 });
    await createBtn.click();

    const dialog = page.locator('.modal-dialog.create-dialog');
    await expect(dialog).toBeVisible();

    await dialog.locator('button:has-text("Create Complaint")').click();

    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Complainant Name and Subject are required');
    // The dialog must stay open so the user can correct the input, and nothing may be reported created.
    await expect(dialog).toBeVisible();
    await expect(page.locator('.success-msg')).toHaveCount(0);

    await dialog.locator('.modal-close').click();
    await expect(dialog).not.toBeVisible();
  });

  test('an overdue complaint is flagged on the dashboard', async ({ page, request }) => {
    // The old version was `if (count > 0) { assert }` with the comment "If no overdue complaints,
    // that is also a valid state (just skip assertion)". The overdue complaint is now seeded.
    const result = await createTestComplaint(request, { subject: 'S4-CEPC Dashboard Overdue Test' });
    const overdueNumber = result.complaintNumber;

    try {
      await advanceToStatus(request, overdueNumber, 'in_progress');
      const seeded = ageComplaintPastSla(overdueNumber, 45);
      expect(seeded, 'could not seed an overdue complaint (MySQL client unavailable)').toBe(true);

      await page.reload();
      await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

      // Advanced search is used rather than the per-column boxes, which do not filter (see the fixme
      // above), so the seeded row can be located deterministically instead of hoping it is on page 1.
      await page.locator('button:has-text("Advanced Search")').click();
      const dialog = page.locator('.modal-dialog.search-dialog');
      await expect(dialog).toBeVisible({ timeout: 5000 });
      await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill(overdueNumber);
      await dialog.locator('button:has-text("Search")').click();

      const row = page.locator(`.data-grid tbody tr:has-text("${overdueNumber}")`);
      await expect(row, 'the overdue complaint must be listed').toBeVisible({ timeout: 10000 });

      const indicator = row.locator('.sla-indicator');
      await expect(indicator, 'an overdue complaint must be flagged red').toHaveClass(/red/);
      await expect(indicator.locator('.days-text')).toHaveText(/\d+ days? overdue/);
    } finally {
      await cleanupComplaint(request, overdueNumber);
    }
  });

  test('clicking a complaint navigates to its detail page', async ({ page }) => {
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    // Previously wrapped in `if (await firstRow.isVisible())`, so an empty grid skipped the whole test.
    const firstRow = page.locator('.data-grid tbody tr:not(:has(.empty-state))').first();
    await expect(firstRow, 'a row must be present to click').toBeVisible({ timeout: 10000 });
    await firstRow.click();

    await page.waitForURL(/\/cepc\/complaint\//, { timeout: 10000 });
    expect(page.url()).toContain('/cepc/complaint/');
    // Navigating is only useful if the target actually renders.
    await expect(page.locator('.cepc-detail .detail-layout')).toBeVisible({ timeout: 15000 });
  });
});

test.describe('CEPC Dashboard - Role-based visibility', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('DO sees Create Complaint button', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO');

    await expect(page.locator('h1:has-text("CEPC Complaints")')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.create-btn')).toBeVisible();

    await logout(page);
  });

  test('Reviewer does NOT see Create Complaint button', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'REVIEWER');

    // Asserting the page loaded first matters: `not.toBeVisible()` is trivially true on a blank page,
    // so without this the test would pass if the dashboard failed to render at all.
    await expect(page.locator('h1:has-text("CEPC Complaints")')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.create-btn')).toHaveCount(0);

    await logout(page);
  });
});
