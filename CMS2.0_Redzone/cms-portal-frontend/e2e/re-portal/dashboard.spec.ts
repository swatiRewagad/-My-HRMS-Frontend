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
 * ── TWO SEPARATE CAUSES OF THE EMPTY DASHBOARD, BOTH NOW CLOSED ─────────────────────────────────
 *
 * 1. ENVIRONMENTAL. GET /re-portal/dashboard was blocked by CORS in the browser whenever the dev
 *    server runs on a port outside cms-backend's allow-list, so the page rendered an empty state with
 *    Total Forwarded = 0 even though the API returns rows to a direct call with the same re_pno_001
 *    token. installCorsShim fixes it; see ../public/browser-api.ts. Still required.
 *
 * 2. PRODUCT DEFECT, now FIXED. The component called only GET /re-portal/dashboard and then read
 *    `data.complaints` — a key that statistics endpoint never returns (RePortalService.java:199-233
 *    emits totalForwarded / pending / responded / breached / avgResponseDays and nothing else). The
 *    list lives on GET /re-portal/complaints, which the component now calls in loadComplaints(). The
 *    sibling mismatch — reading `data.pendingResponse` where the service emits `pending` — is fixed
 *    too, with both keys read so a future server rename does not silently zero the card again.
 *
 * ── THE QUEUE IS THE SHARED GRID ────────────────────────────────────────────────────────────────
 *
 * The hand-rolled `.complaints-table` is gone; the queue is app-task-grid, as on every RBI-side
 * screen. Two consequences for anyone editing this file:
 *
 *   • SELECTORS are the grid's `data-testid`s (see QUEUE / QUEUE_ROW below), not RE-private classes.
 *   • COUNTS come from the footer total via queueTotal(), never from counting rendered rows — the
 *     grid pages at ten, so row counts cannot distinguish 54 complaints from 39.
 */

/**
 * Must match the entity_code on the RE account these tests sign in as (re_pno_001), and must resolve
 * to a REGULATED_ENTITIES row by normalised name — the backend looks entities up that way, so an
 * opaque code matches nothing.
 */
const RE_ENTITY = process.env['RE_ENTITY_CODE'] || 'HDFC Bank';

/**
 * The queue is the shared app-task-grid now, not a hand-rolled `.complaints-table`.
 *
 * <p>The selectors below are the grid's own published `data-testid`s, which is what the RBIO and CEPC
 * suites already assert against. Pinning `.complaints-table` / `.complaint-row` here again would
 * recreate the per-module selector vocabulary the homogenisation pass exists to remove: the RE queue
 * would be the one screen whose test contract could not be shared.
 */
const QUEUE = '[data-testid="task-grid"]';
const QUEUE_ROW = '[data-testid="task-grid-row"]';

/**
 * The grid's footer total, which is the number these tests actually mean when they say "how many
 * complaints".
 *
 * <p>WHY NOT COUNT ROWS. The grid PAGES, and the RE queue carries more rows than one page. Counting
 * `QUEUE_ROW` returns the page size, not the result size — so a filter that genuinely narrows 54
 * complaints to 39 leaves both counts at 10 and a `toBeLessThan` assertion on rendered rows fails
 * while the product is correct. The footer reads "Showing 1 to 10 of 54 entries"; the trailing total
 * is the whole result set.
 */
async function queueTotal(page: import('@playwright/test').Page): Promise<number> {
  const text = (await page.locator('[data-testid="task-grid-range"]').innerText()).trim();
  const match = /of\s+([\d,]+)/i.exec(text);
  expect(match, `could not read a total out of the grid footer: "${text}"`).not.toBeNull();
  return Number(match![1].replace(/,/g, ''));
}

/** Shows as much of the result set as the grid offers, so row-level assertions cover more than ten. */
async function showLargestPage(page: import('@playwright/test').Page): Promise<void> {
  await page.locator('[data-testid="task-grid-page-size"]').selectOption('50');
}

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
   * The Pending card must equal the API's own figure. Do not relax this to `toBeVisible()`.
   *
   * <p>It once read `data.pendingResponse` while the service emitted `pending`, so the card showed 0
   * however much work was awaiting a reply — while Total Forwarded, whose key did match, displayed
   * correctly. That asymmetry is why a presence-only assertion is not enough here.
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
   * The queue must show the entity's real work, on the shared grid.
   *
   * <p>This was the test that caught the original defect: the component called only GET
   * /re-portal/dashboard — a statistics endpoint with no `complaints` key — so the list was
   * permanently empty and every entity read "No complaints found" regardless of caseload. It now
   * calls GET /re-portal/complaints, and the rows below prove it.
   *
   * <p>The assertion is deliberately still "rows, NOT the empty state": a complaint was forwarded to
   * this entity in beforeAll, so `task-grid-empty` here would mean the dashboard is hiding work that
   * exists. An earlier version accepted the empty state through an if/else, which is exactly how the
   * defect shipped unnoticed.
   */
  test('Complaint table renders forwarded complaints', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });

    const grid = page.locator(QUEUE);
    await expect(grid).toBeVisible({ timeout: 15000 });
    await expect(page.locator('[data-testid="task-grid-empty"]')).toHaveCount(0);

    // The grid's captions come from the shared ui.col.* vocabulary, so these are the same headings an
    // RBIO officer reads. Matching the rendered text keeps the assertion about what the entity sees
    // rather than about which key supplied it. `thead tr` first() is the caption row — the second is
    // the grid's per-column filter row.
    const headerTexts = await grid.locator('thead tr').first().locator('th').allTextContents();
    const joinedHeaders = headerTexts.join(' ').toLowerCase();
    expect(joinedHeaders).toContain('complaint');
    expect(joinedHeaders).toContain('status');

    await expect(page.locator(QUEUE_ROW).first()).toBeVisible();
  });

  /**
   * The grid mechanics an RE could not reach while its queue was a hand-rolled table: per-column
   * filtering and sorting. These are the point of the migration — an entity with 54 forwarded
   * complaints previously had to scroll all of them — so they are asserted rather than assumed to
   * arrive free with the component.
   */
  test('the queue offers column filtering and sorting', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator(QUEUE)).toBeVisible({ timeout: 15000 });
    await expect(page.locator(QUEUE_ROW).first()).toBeVisible({ timeout: 10000 });

    // Filter on a value taken FROM the grid, so the expected survivor is real data rather than a
    // literal that goes stale with the fixtures.
    const target = (
      await page.locator(`${QUEUE_ROW} td[data-column="complaintNumber"]`).first().textContent()
    )?.trim();
    expect(target, 'the first row must carry a complaint number').toBeTruthy();

    const filter = page.locator(
      '[data-testid="task-grid-column-filter"][data-column="complaintNumber"]'
    );
    const before = await queueTotal(page);
    await filter.fill(target!);
    await expect.poll(() => queueTotal(page)).toBeLessThan(before);
    await expect(page.locator(`${QUEUE_ROW} td[data-column="complaintNumber"]`).first()).toHaveText(
      target!
    );

    // Clearing restores the full set — a filter that cannot be undone reads as a broken control.
    await filter.fill('');
    await expect.poll(() => queueTotal(page)).toBe(before);

    // Sorting: the header button must set aria-sort, which is also how a screen-reader user learns
    // the order changed.
    const subjectHeader = page
      .locator(`${QUEUE} thead th`)
      .filter({ hasText: /subject/i })
      .first();
    await subjectHeader.locator('.th-sort').click();
    await expect(subjectHeader).toHaveAttribute('aria-sort', 'ascending');
    await subjectHeader.locator('.th-sort').click();
    await expect(subjectHeader).toHaveAttribute('aria-sort', 'descending');
  });

  /**
   * The KPI cards filter the queue they count.
   *
   * <p>Previously all four were inert tiles: an entity could read "39 pending" and then had to find
   * the dropdown to learn WHICH 39. Clicking a card now applies the matching filter, so the number
   * and the rows below it can never describe different sets.
   */
  test('a KPI card filters the queue to the rows it counts', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator(QUEUE)).toBeVisible({ timeout: 15000 });

    const total = await queueTotal(page);
    expect(total, 'the seeded complaint must be in the queue').toBeGreaterThan(0);

    const pendingCard = page.locator('button.stat-card.pending');
    await expect(pendingCard).toHaveAttribute('aria-pressed', 'false');
    await pendingCard.click();

    // aria-pressed, not just the tint: a screen-reader user otherwise cannot tell the queue is
    // filtered at all.
    await expect(pendingCard).toHaveAttribute('aria-pressed', 'true');
    await expect(pendingCard).toHaveClass(/active/);
    const pendingOnly = await queueTotal(page);
    expect(pendingOnly, 'pending must narrow the queue').toBeLessThan(total);
    expect(pendingOnly, 'and must not empty it, with a complaint just forwarded').toBeGreaterThan(0);

    // The card and the dropdown are two views of ONE filter signal, so a card click must move the
    // dropdown. If they diverged an entity could see "pending" highlighted and "All Status" selected.
    await expect(page.locator('.status-filter')).toHaveValue('pending');

    // Clicking the active card clears it, so the full list is reachable without the dropdown.
    await pendingCard.click();
    await expect(pendingCard).toHaveAttribute('aria-pressed', 'false');
    await expect.poll(() => queueTotal(page)).toBe(total);
  });

  /**
   * The Breached tile is deliberately NOT a filter, and this test exists so that stays a decision
   * rather than decaying into an oversight.
   *
   * <p>`breached` is a DEADLINE property on the response tracker, not a complaint status, and GET
   * /re-portal/complaints emits no deadline — so no row predicate could select the breached rows. A
   * card that filtered to an empty grid would tell the entity it had nothing overdue, which is the
   * opposite of what its own number says. It stays a read-only indicator until the list endpoint
   * carries the deadline; at that point this test should be REPLACED by a filtering assertion, not
   * deleted.
   */
  test('the Breached tile is a read-only indicator, not a filter', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    const breached = page.locator('.stat-card.breached');
    await expect(breached).toBeVisible({ timeout: 10000 });

    // Not a button at all, so it is not reachable as a control and offers no click affordance.
    await expect(page.locator('button.stat-card.breached')).toHaveCount(0);
    // And it explains itself rather than looking broken.
    await expect(breached).toHaveAttribute('title', /deadline/i);
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
    await expect(page.locator(QUEUE)).toBeVisible({ timeout: 15000 });

    // Projected into the grid's headerActions slot, so the screen has one header bar rather than the
    // grid's plus its own. The select itself is unchanged by the migration.
    const filterSelect = page.locator('.status-filter');
    await expect(filterSelect).toBeVisible();

    // The grid PAGES, so counts come from the footer total, not from rendered rows: with a page size
    // of ten, 54 complaints and 39 pending both render ten rows and a row-count comparison would
    // fail against a product that is behaving correctly.
    const unfiltered = await queueTotal(page);
    expect(unfiltered).toBeGreaterThan(0);

    await filterSelect.selectOption('pending');
    await expect.poll(() => queueTotal(page)).toBeLessThan(unfiltered);

    const filtered = await queueTotal(page);
    // A filter that empties the queue is not evidence it filtered correctly, so both the survivors
    // and the fact that there ARE survivors are asserted.
    expect(filtered, "selecting 'pending' must leave the forwarded complaints visible").toBeGreaterThan(0);

    // Every VISIBLE survivor must be one the option legitimately covers. Asserting the badge read
    // /pending/ would re-encode the very confusion that caused the original bug (a direct
    // `c.status === status` comparison that could never match): the dropdown speaks the RE's view of
    // the work while the API stores forwarded/re_responded/closed.
    await showLargestPage(page);
    const statusBadges = page.locator(`${QUEUE} tbody .status-badge`);
    const count = await statusBadges.count();
    expect(count).toBeGreaterThan(0);
    for (let i = 0; i < count; i++) {
      const text = (await statusBadges.nth(i).textContent())?.toLowerCase() ?? '';
      // 'pending' covers exactly the forwarded-awaiting-reply state. The badge renders the seeded
      // label for status.forwarded ("Forwarded to Dept"), so the match is on the word, not the code.
      expect(text, 'a row kept by the pending filter must be awaiting the entity’s reply')
        .toMatch(/forwarded|pending/);
    }

    await filterSelect.selectOption('');
    await expect.poll(() => queueTotal(page)).toBe(unfiltered);
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
   * Deliberately NOT "fixed" by asserting an empty cell: the deadline countdown is how a regulated
   * entity learns a statutory response window is closing, so a passing test here must mean a real
   * date is shown. Fixing it properly means adding forwardedAt and responseDeadline to the list row,
   * which is a server contract change and is reported as an outstanding item rather than guessed at.
   *
   * <p>On the shared grid the deadline is the `responseDeadline` column, present but empty, and the
   * row tint (urgency-*) that the grid applies through `rowClass` is dormant for the same reason. The
   * column and the tint are both KEPT so they populate the day the server emits the field, rather
   * than having to be rediscovered.
   */
  test.fixme(
    'Deadline countdown shows for pending complaints',
    async ({ page }) => {
      await page.waitForSelector('.re-dashboard', { timeout: 15000 });
      await expect(page.locator(QUEUE)).toBeVisible({ timeout: 15000 });

      const deadline = page.locator(`${QUEUE_ROW} td[data-column="responseDeadline"]`).first();
      await expect(
        deadline,
        'the dashboard must show a response deadline for a pending complaint'
      ).toBeVisible({ timeout: 10000 });

      const text = (await deadline.textContent())?.trim() ?? '';
      // A real date, not a placeholder: '', '-' and 'NaN' must all fail.
      expect(text, `deadline cell reads "${text}"`).toMatch(/\d/);
      expect(text).not.toMatch(/NaN|undefined|null/i);

      // And the urgency cue must be live, since that is what an officer reads at a glance.
      await expect(page.locator(`${QUEUE_ROW}[class*="urgency-"]`).first()).toBeVisible();
    }
  );

  /**
   * Row navigation, now through the grid's `rowClick` output rather than a hand-rolled `(click)` on a
   * `<tr>`. This is how an RE reaches a complaint at all, so it is asserted unconditionally.
   */
  test('Click navigates to complaint detail', async ({ page }) => {
    await page.waitForSelector('.re-dashboard', { timeout: 15000 });
    await expect(page.locator(QUEUE)).toBeVisible({ timeout: 15000 });

    // Unconditional: `if (firstRow.isVisible())` made this a no-op on an empty dashboard.
    const firstRow = page.locator(QUEUE_ROW).first();
    await expect(firstRow).toBeVisible({ timeout: 10000 });

    // Addressed by column rather than by position: the grid's column chooser can hide columns, so
    // `td` first() is not reliably the complaint number.
    const rowComplaintNumber = (
      await firstRow.locator('td[data-column="complaintNumber"]').textContent()
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
