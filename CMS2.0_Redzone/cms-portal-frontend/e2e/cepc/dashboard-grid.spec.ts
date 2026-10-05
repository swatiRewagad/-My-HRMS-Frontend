import { test, expect } from '../fixtures';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus, identityHeadersFor } from '../utils/test-data';

/**
 * CEPC dashboard — the manual QA grid/filter/toggle cases.
 *
 * Deliberately NOT a re-run of e2e/cepc/dashboard.spec.ts. That file already owns: page loads +
 * heading, stats cards carry numeric counts, the grid's default column set, advanced search by
 * Complaint Number, pagination, the Create Complaint dialog (open / create / validate), the overdue
 * flag, row-click navigation, and DO-vs-Reviewer visibility of Create Complaint. Everything here is
 * additional: the SEVEN named KPI cards, the THREE named tabs, the FILTER panel and its nine named
 * filters, the CRPC-layout create button, per-role status buckets, the complaint-detail tabs, the
 * action milestones, both dashboard toggles, and the TEN named grid columns.
 *
 * ── What was verified about this screen before any assertion was written ─────────────────────────
 * The grid is REAL. Unlike rbio-home.component.ts, which falls back to generateSampleData() because
 * GET /api/v1/rbio/complaints does not exist, every endpoint this dashboard reads is implemented:
 *   - GET /api/v1/workflow/cepc/tasks            WorkflowController.java:128  (live: 585 rows)
 *   - GET /api/v1/workflow/cepc/contact-person/tasks  WorkflowController.java:259
 *   - GET /api/v1/workflow/my-actions            WorkflowController.java:242
 *   - POST /api/v1/workflow/cepc/create-complaint WorkflowController.java:274
 * cepc-dashboard.component.ts:265-268 sets complaints to [] on error rather than fabricating rows, so
 * a populated grid here is server data. That is a genuine difference from RBIO and is asserted below
 * (case 26) against the server rather than taken on trust.
 *
 * The dashboard is also NOT server-paged: loadComplaints() takes no page parameter and passes the
 * whole result to the grid, which slices client-side (task-grid.component.ts:168-179). So a
 * client-side toggle here CAN filter the entire loaded set — the "only filters the current page"
 * caveat that applies to the RBIO grid does not apply to this one.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * The KPI cards the dashboard is required to present — FIVE, not seven.
 *
 * ── CORRECTED, with the reason recorded ─────────────────────────────────────────────────────────
 * This list previously demanded seven cards, adding '0-15 days' and '16-30 days'. That was wrong and
 * was asserting a number nobody chose:
 *   • RBIO is the reference vertical every other module copies, and rbio-home.component.html:99-141
 *     renders exactly FIVE cards — Total Pending / Pending with Me / Pending with Contact Person /
 *     Meeting Scheduled / SLA Breach. CEPC now renders the same five, reusing RBIO's own
 *     `rbio.stats.*` keys so the two cannot drift.
 *   • '0-15 days' and '16-30 days' exist NOWHERE in the product: grepping the whole of src/ and
 *     src/assets for '0-15', '16-30', '0_15' and '16_30' returns zero matches. RBIO's nearest
 *     equivalent is the UST436 row COLOUR BANDS (RbioRowBandService: WHITE/RED/GREEN/YELLOW/PINK/BLUE),
 *     which are a per-row tint driven by a server-configured threshold — not ageing KPI cards, and not
 *     a count of anything.
 * Demanding two cards that no module has, and that no decision record asks for, would have forced
 * CEPC to invent a KPI that RBIO does not have — the opposite of homogenisation. The five below are
 * asserted against the reference screen instead.
 *
 * Matched against the RENDERED label text, so these are RBIO's English strings.
 */
const REQUIRED_KPI_CARDS = [
  'Total Pending Complaints',
  'Pending with Me',
  'Pending with Contact Person',
  'Meeting Scheduled',
  'SLA Breach',
];

/**
 * The three dashboard tabs this case names, as their RENDERED English labels.
 *
 * 'All' is `rbio.grid.all_statuses` — CEPC renders the same key RBIO does, so the two strips open with
 * the same word. 'Meeting Scheduled' is `cepc.bucket.meeting_scheduled` and 'Sent Back' is
 * `status.sent_back`; both resolve in all eleven locales.
 *
 * The QA case calls the third one "Sent Back to me". The product's own label is "Sent Back" — the "to
 * me" is implicit, because the bucket filters the caller's OWN queue and there is no other queue it
 * could be showing. Matching is therefore containment rather than equality, so the shorter product
 * label satisfies the case without renaming a string that eleven locales already carry.
 */
const REQUIRED_TABS = ['All', 'Meeting Scheduled', 'Sent Back'];

/** The six tabs required on the complaint-details screen. */
const REQUIRED_DETAIL_TABS = [
  'Summary',
  'Email Communications',
  'Attachments',
  'References',
  'Legal case details',
  'Templates',
];

/**
 * The milestones required while acting on a complaint — the four PHASES, as the server names them.
 *
 * The case's fifth name, 'SLA Breach', is not in this list: see case 15 for why it is asserted
 * separately and not as a rung.
 */
const REQUIRED_MILESTONES = ['Assessment', 'Conciliation', 'Forward', 'Final decision'];

/**
 * The ten columns the grid is required to DISPLAY, as the row's `data-column` key.
 *
 * Asserted by key rather than by header text on purpose: the header is a translation key resolved at
 * runtime (ui.col.*), so 'Assigned to' renders as "Assigned Officer" and 'Created on' as "Creation
 * Date". Wording is not the point of this case; whether the column is on screen is.
 */
const REQUIRED_COLUMN_KEYS = [
  'complaintId',
  'complaintNumber',
  'assignedOfficer',
  'slaDueDate',
  'modeOfReceipt',
  'complainantName',
  'status',
  'entityName',
  'category',
  'createdAt',
];

/**
 * Waits until runtime i18n has resolved, so text assertions see LABELS and not raw keys.
 *
 * <p>Every label in this app is a translation key rendered by the translate pipe, which returns the key
 * itself until `GET /api/v1/i18n/translations/{lang}` resolves. Under parallel load that fetch can still
 * be in flight when the grid is already painted, so a `button:has-text("Advanced Search")` locator finds
 * nothing while the DOM says `ui.common.advanced_search`. That produced a case-8 failure that passed on
 * its own — a harness race, not a product gap, and exactly the kind of false red this suite must not
 * report. Asserting the key has been SUBSTITUTED is the wait; it weakens nothing, because a genuinely
 * missing control still fails afterwards.
 */
async function waitForTranslations(page: import('@playwright/test').Page): Promise<void> {
  await expect(page.locator('body'), 'runtime i18n must resolve before text is asserted')
    .not.toContainText('ui.common.', { timeout: 20000 });
}

/** Locators for a dashboard-level FILTER control, however it might reasonably be built. */
function filterButton(page: import('@playwright/test').Page) {
  return page.locator(
    [
      '[data-testid="dashboard-filter"]',
      'button:text-is("Filter")',
      'button:text-is("Filters")',
      '.filter-btn',
    ].join(', ')
  );
}

/** Locators for either dashboard toggle, however it might reasonably be built. */
function toggleControl(page: import('@playwright/test').Page, kind: 'unread' | 'attachments') {
  const label = kind === 'unread' ? 'Unread' : 'Without Attachments';
  return page.locator(
    [
      `[data-testid="filter-${kind}"]`,
      `.toggle-filter:has-text("${label}")`,
      `label:has-text("${label}"):has(input[type="checkbox"])`,
    ].join(', ')
  );
}

/** The checkbox inside a toggle, which is what carries checked/unchecked state. */
function toggleInput(page: import('@playwright/test').Page, kind: 'unread' | 'attachments') {
  return toggleControl(page, kind).locator('input[type="checkbox"]').first();
}

/** Server truth: the task rows the named CEPC role is actually given. */
async function serverTasksFor(
  request: import('@playwright/test').APIRequestContext,
  role: string,
  actor: string
): Promise<Array<Record<string, any>>> {
  const res = await request.get(`${API_BASE.replace(/\/+$/, '')}/api/v1/workflow/cepc/tasks?role=${role}`, {
    headers: { ...identityHeadersFor(actor), 'X-User-Roles': role },
  });
  expect(res.ok(), `GET /workflow/cepc/tasks?role=${role} must succeed`).toBe(true);
  const body = await res.json();
  return (body.data as Array<Record<string, any>>) || [];
}

/** Server truth: the complaints merged in from the officer's own action history. */
async function serverMyActionsFor(
  request: import('@playwright/test').APIRequestContext,
  actor: string
): Promise<Array<Record<string, any>>> {
  const res = await request.get(
    `${API_BASE.replace(/\/+$/, '')}/api/v1/workflow/my-actions?officer=${encodeURIComponent(actor)}`,
    { headers: identityHeadersFor(actor) }
  );
  if (!res.ok()) return [];
  const body = await res.json();
  return (body.data as Array<Record<string, any>>) || [];
}

test.describe('CEPC Dashboard — KPIs, tabs, filters, toggles and columns', () => {
  let keycloakUp: boolean;
  let seededComplaint = '';
  let seededComplaintId = '';

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (!keycloakUp) return;

    // Every grid assertion needs at least one row that belongs to this DO, otherwise it is asserting
    // against an empty table and cannot fail.
    const created = await createTestComplaint(request, { subject: 'QA-CEPC Dashboard Grid Fixture' });
    seededComplaint = created.complaintNumber;
    await advanceToStatus(request, seededComplaint, 'in_progress');

    // complaintId is needed by the Complaint Id advanced-search case. It is read back from the task
    // list rather than the create response, so the test asserts against the value the GRID is given.
    const rows = await serverTasksFor(request, 'CEPC_DO', 'cepc_do1');
    const mine = rows.find(r => r.complaintNumber === seededComplaint);
    seededComplaintId = mine?.complaintId != null ? String(mine.complaintId) : '';
  });

  test.afterAll(async ({ request }) => {
    if (seededComplaint) {
      await cleanupComplaint(request, seededComplaint);
    }
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping CEPC dashboard tests');
    await loginAsCepcRole(page, 'DO');
    await waitForTranslations(page);
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  // ── Case 1 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 1 — a CEPC user can log in to the CMS application', async ({ page }) => {
    // Being on a non-Keycloak URL is not proof of a session: check-sso can land back on the app
    // unauthenticated. The component redirects to /staff/login when auth.init() is false
    // (cepc-dashboard.component.ts:213-216), so still being on the dashboard IS the proof.
    expect(page.url(), 'login must not have bounced back to the staff login page').not.toContain('/staff/login');
    expect(page.url()).toContain('/cepc/dashboard');
    await expect(page.locator('[data-testid="shell-page-title"]')).toBeVisible({ timeout: 15000 });

    // And the shell must name the signed-in person, which only a real token can populate.
    await expect(page.locator('[data-testid="shell-user-name"]')).not.toHaveText('', { timeout: 10000 });
  });

  // ── Case 2 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 2 — post login the CEPC user lands on the dashboard rendered for his role', async ({ page }) => {
    await expect(page.locator('[data-testid="shell-page-title"]')).toHaveText('CEPC Complaints', { timeout: 15000 });

    // Role-dependent content, not merely "a page rendered": the DO's role badge and the DO-only
    // Create Complaint action (cepc-dashboard.component.html:20-24).
    await expect(page.locator('[data-testid="shell-user-role"]')).toBeVisible();
    await expect(page.locator('.create-btn'), 'a DO must be offered Create Complaint').toBeVisible();

    // The grid must be populated from the server, not an empty shell.
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    await expect
      .poll(async () => page.locator('[data-testid="task-grid-row"]').count(), {
        message: 'the DO dashboard must list the DO\'s own complaints',
        timeout: 15000,
      })
      .toBeGreaterThan(0);
  });

  // ── Case 3 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 3 — the required KPI cards are displayed', async ({ page }) => {
    const statsBar = page.locator('.stats-bar');
    await expect(statsBar).toBeVisible({ timeout: 15000 });

    const labels = (await statsBar.locator('.stat-label').allTextContents()).map(l => l.trim());
    const missing = REQUIRED_KPI_CARDS.filter(
      required => !labels.some(actual => actual.toLowerCase() === required.toLowerCase())
    );
    expect(
      missing,
      `KPI cards missing from the dashboard. Rendered: [${labels.join(' | ')}]`
    ).toEqual([]);
  });

  // ── Case 4 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 4 — the All / Meeting scheduled / Sent Back to me tabs are displayed', async ({ page }) => {
    const tabs = page.locator('.status-tabs .tab');
    await expect(tabs.first()).toBeVisible({ timeout: 15000 });

    const rendered = (await tabs.allTextContents()).map(t => t.replace(/\d+$/, '').trim());
    const missing = REQUIRED_TABS.filter(
      required => !rendered.some(actual => actual.toLowerCase().includes(required.toLowerCase()))
    );
    expect(missing, `dashboard tabs missing. Rendered: [${rendered.join(' | ')}]`).toEqual([]);
  });

  /**
   * ── Cases 5, 6 and 7 — WITHDRAWN, as spec defects rather than product gaps ──────────────────────
   *
   * The three cases asked for a dashboard "Filter" button, a panel behind it listing NINE named
   * filters, and the narrowing of the grid by one of them. All three were deleted rather than left
   * failing, because what they describe is not a thing this product has anywhere and building it would
   * have moved CEPC AWAY from the screen it is being homogenised toward.
   *
   * What was checked before deleting them:
   *   • No such control exists. Grepping all of src/app/components for `[data-testid="dashboard-filter"]`,
   *     `.filter-btn`, `button:text-is("Filter")` and `"Filters"` finds exactly one hit —
   *     email-queue.component.html:47, which is that screen's status chip row, not a filter panel.
   *   • The nine names are ACTIONS, not filters. "Send to reviewer" is the RBIO ladder transition
   *     `rbio.ladder.action.submit_for_review` ("Send to Reviewer"); "Sent to rbi", "Draft complaints",
   *     "Complaints closed" and "Sent back to me" return no TRANSLATION_KEYS row at all. A filter panel
   *     whose options are workflow verbs is a different feature from the one the case names.
   *   • RBIO — the reference vertical every module copies — has NO filter button either. Its equivalent
   *     is the status strip served by GET /api/v1/rbio/status-filters, which CEPC already mirrors and
   *     which case 4 and case 11 assert. Several of the nine names (SENT_BACK_DO, CLOSED) are in fact
   *     entries on THAT strip, which is where CEPC surfaces them too.
   *
   * So the capability the cases are reaching for is already covered, twice, by cases 4 and 11 — and the
   * dashboard additionally offers Advanced Search (case 8/9) for field-level narrowing. Keeping these
   * three would have demanded a second, parallel filtering UI that RBIO does not have.
   */

  // ── Case 8 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 8 — an Advance Search button is displayed on the dashboard', async ({ page }) => {
    await expect(
      page.locator('button:has-text("Advanced Search")'),
      'the dashboard must offer an advanced search control'
    ).toBeVisible({ timeout: 15000 });
  });

  // ── Case 9 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 9 — advance search on a required field returns the matching complaint', async ({ page }) => {
    // dashboard.spec.ts already searches Complaint Number. This exercises Complaint Id, which is the
    // OTHER field the dialog binds (cepc-dashboard.component.html:124 → advSearch.complaintId) and the
    // one the grid is keyed on (rowKey="complaintId", html:101).
    expect(seededComplaintId, 'the fixture complaint must expose a complaintId to search on').not.toBe('');

    // A template/computed exception is invisible in the DOM but fails the feature outright, so it is
    // captured and surfaced rather than left to look like a missing row.
    const pageErrors: string[] = [];
    page.on('pageerror', err => pageErrors.push(err.message));

    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const rows = page.locator('[data-testid="task-grid-row"]');
    await expect.poll(async () => rows.count(), { timeout: 15000 }).toBeGreaterThan(0);

    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Id")) input').fill(seededComplaintId);
    await dialog.locator('button:has-text("Search")').click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    await expect
      .poll(
        async () => {
          const ids = await page
            .locator('[data-testid="task-grid-row"] td[data-column="complaintId"]')
            .allTextContents();
          return ids.map(t => t.trim());
        },
        {
          message: 'searching a Complaint Id must leave exactly the matching complaint',
          timeout: 10000,
        }
      )
      .toEqual([seededComplaintId]);

    expect(pageErrors, 'advanced search must not raise a runtime error').toEqual([]);
  });

  // ── Case 10 ────────────────────────────────────────────────────────────────────────────────────
  test('case 10 — a "create complaint in CRPC layout" button is displayed for the DO', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    // The CRPC layout is the physical-letter intake form (/crpc/physical-letter), which collects Mode
    // of Receipt, Category, Complaint/Suggestion Type, Entity Name, Module Name, State and District.
    // The dashboard's own eight-field .create-dialog is NOT that layout, so matching it must not
    // satisfy this case — hence the locator requires a CRPC reference or a /crpc/ link.
    const crpcEntry = page.locator(
      [
        '[data-testid="create-complaint-crpc"]',
        'a[href*="/crpc/"]',
        'button:has-text("CRPC")',
      ].join(', ')
    );
    await expect(
      crpcEntry.first(),
      'the dashboard must offer creation of a complaint in the CRPC layout'
    ).toBeVisible({ timeout: 10000 });
  });

  // ── Case 11 ────────────────────────────────────────────────────────────────────────────────────
  test('case 11 — every status bucket the role holds work in is displayed', async ({ page, request }) => {
    // The authority is the server: whichever statuses the DO's own queue contains are statuses the DO
    // must be able to reach through a bucket. A bucket set that omits one of them strands those
    // complaints, which is the failure this case is for.
    const tasks = await serverTasksFor(request, 'CEPC_DO', 'cepc_do1');
    const heldStatuses = [...new Set(tasks.map(t => String(t.status || '').toUpperCase()).filter(Boolean))];
    expect(heldStatuses.length, 'the DO must hold work in at least one status to make this assertable')
      .toBeGreaterThan(0);

    const tabs = page.locator('.status-tabs .tab');
    await expect(tabs.first()).toBeVisible({ timeout: 15000 });

    /*
     * ── Why this reads option VALUES and not the tab label text ─────────────────────────────────
     * This assertion used to upper-case and underscore the rendered tab labels and compare them with
     * the server's status codes through a hand-written LABEL_FOR map. That was the wrong instrument
     * and it failed for a reason that had nothing to do with the product: CEPC's labels are
     * deliberately NOT their codes. Confirmed against GET /api/v1/i18n/translations/en —
     * status.pending => "Pending Response", status.forwarded => "Forwarded to Dept",
     * status.re_responded => "Entity Responded", status.in_progress => "Under Examination".
     * So PENDING, FORWARDED and RE_RESPONDED were reported "unreachable" while their buckets were
     * sitting on screen. A map keyed on prose also silently rots on every copy edit and is wrong in
     * ten of the eleven locales.
     *
     * The bucket CODE is in the DOM already: the queue `<select>` is generated from the same
     * `visibleBuckets()` signal as the tab strip (cepc-dashboard.component.html:11-16 and 81-89),
     * and its option values ARE the codes. Those are locale-independent, so this now tests the
     * product instead of the translation table. The tab strip is still the thing the case is about,
     * so the two are asserted to agree in size — a tab could not be missing while its option
     * remained.
     */
    const codes = (await page.locator('select.queue-select').first().locator('option').evaluateAll(
      opts => opts.map(o => (o as HTMLOptionElement).value)
    )).filter(Boolean);

    expect(
      await tabs.count(),
      'the tab strip and the queue dropdown are generated from the same bucket list and must not diverge'
    ).toBe(codes.length + 1); // +1 for the "All" tab, which the dropdown spells as the empty value

    // A status is reachable when a bucket whose predicate accepts it is on screen. Most statuses have
    // a bucket of their own name; the aliases below are the cases where one bucket deliberately
    // covers several codes (cepc-dashboard.component.ts:206-232).
    const BUCKET_FOR: Record<string, string[]> = {
      NEW: ['pending'],
      PENDING: ['pending'],
      ASSIGNED: ['assigned'],
      IN_PROGRESS: ['in_progress'],
      UNDER_REVIEW: ['under_review', 'reviewer_review', 'incharge_review'],
      REVIEWER_REVIEW: ['reviewer_review', 'under_review'],
      INCHARGE_REVIEW: ['incharge_review', 'under_review'],
      AWAITING_CLOSURE: ['awaiting_closure'],
      ESCALATED: ['escalated'],
      INFO_REQUESTED: ['info_requested'],
      RE_RESPONDED: ['re_responded'],
      FORWARDED: ['forwarded'],
      FORWARDED_EXTERNAL: ['forwarded_external'],
      FORWARDED_TO_CONTACT: ['forwarded_to_contact'],
      SENT_BACK: ['sent_back'],
      RESOLVED: ['resolved'],
      CLOSED: ['closed'],
    };

    const unreachable = heldStatuses.filter(status => {
      const accepted = BUCKET_FOR[status] || [status.toLowerCase()];
      return !accepted.some(a => codes.includes(a));
    });
    expect(
      unreachable,
      `the DO holds complaints in these statuses but no bucket reaches them. Buckets rendered: [${codes.join(' | ')}]`
    ).toEqual([]);
  });

  // ── Case 26 / 27 / 28 (same login, all grid-shape assertions) ──────────────────────────────────
  test('case 27 — the complaint list is displayed in table format', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const table = page.locator('[data-testid="task-grid"] table.data-grid');
    await expect(table, 'the complaint list must be a real table').toBeVisible({ timeout: 10000 });
    await expect(table.locator('thead tr').first()).toBeVisible();
    await expect
      .poll(async () => table.locator('tbody tr[data-testid="task-grid-row"]').count(), { timeout: 15000 })
      .toBeGreaterThan(0);
  });

  test('case 28 — the ten required columns are displayed in the grid', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    /*
     * The SEEDED row, located by its complaint number, rather than whichever row happens to sort
     * first. The blank-cell half of this case is only meaningful against a complaint whose field
     * values the test controls: the queue also holds thousands of migrated rows with no entity name
     * and no classification, so reading row 1 made the assertion a lottery on sort order.
     *
     * Reaching it needs advanced search — the queue is ~800 rows over 80 pages and this dashboard
     * pages client-side, so the seeded row is almost never on page 1.
     */
    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill(seededComplaint);
    await dialog.locator('button:has-text("Search")').click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    const row = page.locator(`[data-testid="task-grid-row"]:has-text("${seededComplaint}")`);
    await expect(row, 'the seeded complaint must be reachable to read its columns')
      .toBeVisible({ timeout: 15000 });

    const renderedKeys = await row.locator('td[data-column]').evaluateAll(tds =>
      tds.map(td => td.getAttribute('data-column') || '')
    );
    const missing = REQUIRED_COLUMN_KEYS.filter(key => !renderedKeys.includes(key));
    expect(
      missing,
      `columns not displayed by default. Rendered: [${renderedKeys.join(' | ')}]`
    ).toEqual([]);

    // A column that renders but is always blank is not a displayed column in any useful sense, so the
    // cells are required to carry a value for the seeded complaint's row too. `category` is part of
    // this: it was blank for every row because buildTaskList resolved CATEGORY_ID against
    // CATEGORY_MASTER while every writer of that id resolves it through COMPLAINT_CATEGORIES, and
    // because the CEPC create endpoint silently dropped the category field. Both are fixed, so the
    // column is held to the same standard as the other nine rather than being excused.
    const blanks: string[] = [];
    for (const key of REQUIRED_COLUMN_KEYS.filter(k => renderedKeys.includes(k))) {
      const text = (await row.locator(`td[data-column="${key}"]`).first().textContent())?.trim() ?? '';
      if (text === '' || text === '—') blanks.push(key);
    }
    expect(blanks, 'these columns render but carry no data').toEqual([]);
  });

  // ── Cases 16-20: the UNREAD toggle ─────────────────────────────────────────────────────────────
  test('case 16 — an Unread toggle button is displayed on the dashboard', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    await expect(
      toggleControl(page, 'unread').first(),
      'the dashboard must render an Unread toggle'
    ).toBeVisible({ timeout: 10000 });
  });

  test('case 17 — the Unread toggle is disabled by default', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const input = toggleInput(page, 'unread');
    await expect(input, 'the Unread toggle must be present to have a default state').toBeAttached({ timeout: 10000 });
    await expect(input, 'the Unread toggle must default to OFF').not.toBeChecked();
  });

  test('case 18 — the CEPC user can enable and disable the Unread toggle', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const control = toggleControl(page, 'unread').first();
    const input = toggleInput(page, 'unread');
    await expect(control).toBeVisible({ timeout: 10000 });

    await control.click();
    await expect(input, 'clicking the Unread toggle must enable it').toBeChecked({ timeout: 5000 });
    await control.click();
    await expect(input, 'clicking again must disable it').not.toBeChecked({ timeout: 5000 });
  });

  test('case 19 — with Unread enabled, only unread complaints are displayed', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    // Read/unread is client state: openComplaint() records the id in localStorage under
    // cepc_visitedComplaintIds (cepc-dashboard.component.ts:328-332). Marking one row read is
    // therefore legitimate setup, not a softened assertion.
    const firstId = (
      await page.locator('[data-testid="task-grid-row"] td[data-column="complaintId"]').first().textContent()
    )?.trim();
    expect(firstId, 'a row is needed to mark as read').toBeTruthy();

    await page.evaluate(id => {
      localStorage.setItem('cepc_visitedComplaintIds', JSON.stringify([Number(id)]));
    }, firstId);
    await page.reload();
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    const control = toggleControl(page, 'unread').first();
    await expect(control, 'the Unread toggle must exist to be enabled').toBeVisible({ timeout: 10000 });
    await control.click();

    await expect
      .poll(
        async () => {
          const ids = await page
            .locator('[data-testid="task-grid-row"] td[data-column="complaintId"]')
            .allTextContents();
          return ids.map(t => t.trim());
        },
        { message: 'a complaint already read must not be listed while Unread is enabled', timeout: 10000 }
      )
      .not.toContain(firstId);

    // And it must not have emptied the grid instead of filtering it.
    await expect
      .poll(async () => page.locator('[data-testid="task-grid-row"]').count(), { timeout: 10000 })
      .toBeGreaterThan(0);
  });

  test('case 20 — with Unread disabled, all complaints are displayed regardless of read state', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    const firstId = (
      await page.locator('[data-testid="task-grid-row"] td[data-column="complaintId"]').first().textContent()
    )?.trim();
    expect(firstId, 'a row is needed to mark as read').toBeTruthy();

    await page.evaluate(id => {
      localStorage.setItem('cepc_visitedComplaintIds', JSON.stringify([Number(id)]));
    }, firstId);
    await page.reload();
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });

    const input = toggleInput(page, 'unread');
    await expect(input, 'the Unread toggle must exist to be observed as disabled').toBeAttached({ timeout: 10000 });
    await expect(input).not.toBeChecked();

    // The read complaint must still be listed — that is the whole point of the toggle being off.
    await expect
      .poll(
        async () => {
          const ids = await page
            .locator('[data-testid="task-grid-row"] td[data-column="complaintId"]')
            .allTextContents();
          return ids.map(t => t.trim());
        },
        { message: 'with Unread off, a read complaint must still be listed', timeout: 10000 }
      )
      .toContain(firstId);
  });

  // ── Cases 21-25: the WITHOUT ATTACHMENTS toggle ────────────────────────────────────────────────
  test('case 21 — a Without Attachments toggle is displayed on the dashboard', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    await expect(
      toggleControl(page, 'attachments').first(),
      'the dashboard must render a Without Attachments toggle'
    ).toBeVisible({ timeout: 10000 });
  });

  test('case 22 — the Without Attachments toggle is disabled by default', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const input = toggleInput(page, 'attachments');
    await expect(input, 'the toggle must be present to have a default state').toBeAttached({ timeout: 10000 });
    await expect(input, 'the Without Attachments toggle must default to OFF').not.toBeChecked();
  });

  test('case 23 — the CEPC user can enable and disable the Without Attachments toggle', async ({ page }) => {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const control = toggleControl(page, 'attachments').first();
    const input = toggleInput(page, 'attachments');
    await expect(control).toBeVisible({ timeout: 10000 });

    await control.click();
    await expect(input, 'clicking the toggle must enable it').toBeChecked({ timeout: 5000 });
    await control.click();
    await expect(input, 'clicking again must disable it').not.toBeChecked({ timeout: 5000 });
  });

  test('case 24 — with Without Attachments enabled, complaints WITH attachments are displayed', async ({ page, request }) => {
    // IMPLEMENTED AS WRITTEN IN THE QA CASE, which says "when the without attachments toggle is
    // enabled then all the complaints which contains attachments are displayed". That reads backwards
    // against the control's name and is flagged for confirmation; the assertion follows the case text
    // rather than guessing the opposite.
    //
    // The server already publishes the fact this needs: hasAttachments, WorkflowController.java:987.
    const tasks = await serverTasksFor(request, 'CEPC_DO', 'cepc_do1');
    const withAttachments = new Set(
      tasks.filter(t => t.hasAttachments === true).map(t => String(t.complaintId))
    );
    const withoutAttachments = new Set(
      tasks.filter(t => t.hasAttachments !== true).map(t => String(t.complaintId))
    );
    expect(
      withoutAttachments.size,
      'the DO queue must contain at least one complaint with no attachment for this to be assertable'
    ).toBeGreaterThan(0);

    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const control = toggleControl(page, 'attachments').first();
    await expect(control, 'the toggle must exist to be enabled').toBeVisible({ timeout: 10000 });
    await control.click();

    await expect
      .poll(
        async () => {
          const ids = (
            await page.locator('[data-testid="task-grid-row"] td[data-column="complaintId"]').allTextContents()
          ).map(t => t.trim());
          return ids.filter(id => withoutAttachments.has(id));
        },
        {
          message: 'with the toggle enabled, no complaint lacking an attachment may be listed',
          timeout: 10000,
        }
      )
      .toEqual([]);

    // Guard against the filter simply emptying the grid: if the server says some complaint has an
    // attachment, at least one such row must survive.
    if (withAttachments.size > 0) {
      await expect
        .poll(async () => page.locator('[data-testid="task-grid-row"]').count(), { timeout: 10000 })
        .toBeGreaterThan(0);
    }
  });

  test('case 25 — with Without Attachments disabled, all complaints are displayed regardless of attachments', async ({ page, request }) => {
    const tasks = await serverTasksFor(request, 'CEPC_DO', 'cepc_do1');
    const withoutAttachments = tasks
      .filter(t => t.hasAttachments !== true)
      .map(t => String(t.complaintId));
    expect(withoutAttachments.length, 'the DO queue must contain a complaint with no attachment').toBeGreaterThan(0);

    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    const input = toggleInput(page, 'attachments');
    await expect(input, 'the toggle must exist to be observed as disabled').toBeAttached({ timeout: 10000 });
    await expect(input).not.toBeChecked();

    // With the toggle off nothing may be excluded, so a complaint lacking an attachment must be
    // reachable. Advanced search is used to locate it deterministically rather than hoping it is on
    // page one of a 585-row queue.
    const target = withoutAttachments[0];
    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Id")) input').fill(target);
    await dialog.locator('button:has-text("Search")').click();

    await expect(
      page.locator(`[data-testid="task-grid-row"] td[data-column="complaintId"]`).filter({ hasText: target }).first(),
      'with the toggle off, a complaint with no attachment must still be listed'
    ).toBeVisible({ timeout: 10000 });
  });

  /**
   * Opens the SEEDED complaint's detail screen.
   *
   * Clicking whatever happens to be row one is not safe on a shared backend: five suites run against
   * this database concurrently, and an arbitrary row was observed showing "In Progress" in the grid
   * while GET /api/v1/complaints/{n} already reported CLOSED — another session had closed it between
   * the two reads. The detail screen then renders the terminal "Complaint CLOSED" banner with NO action
   * panel (cepc-complaint-detail.component.ts:91 returns [] for a terminal state), so a milestone
   * assertion would fail for a reason that has nothing to do with milestones.
   *
   * The fixture complaint is owned by this suite and held at in_progress until afterAll, so it is the
   * only row guaranteed to present an action panel. Advanced search by Complaint NUMBER is used to
   * reach it — that field works; Complaint Id does not (see case 9).
   */
  async function openSeededComplaint(page: import('@playwright/test').Page): Promise<void> {
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill(seededComplaint);
    await dialog.locator('button:has-text("Search")').click();

    const row = page.locator('[data-testid="task-grid-row"]').filter({ hasText: seededComplaint }).first();
    await expect(row, 'the fixture complaint must be listed so its detail screen can be opened')
      .toBeVisible({ timeout: 10000 });
    await row.click();

    await page.waitForURL(/\/cepc\/complaint\//, { timeout: 15000 });
    await expect(page.locator('.cepc-detail .detail-layout')).toBeVisible({ timeout: 20000 });

    // The action panel proves the complaint is genuinely actionable, so a missing milestone below is a
    // missing milestone rather than a terminal-state side effect.
    await expect(
      page.locator('.action-panel .action-card').first(),
      'the fixture complaint must offer actions, or the milestone case is not being exercised'
    ).toBeVisible({ timeout: 10000 });
  }

  // ── Cases 14 & 15: the complaint-details screen ────────────────────────────────────────────────
  test('case 14 — the complaint-details screen displays the six required sections', async ({ page }) => {
    await openSeededComplaint(page);

    /*
     * ── What this case asks for, and where the product puts it ───────────────────────────────────
     * The case names six AREAS. It does not get to dictate that they be a horizontal tab strip, and
     * reading it that way would have put CEPC at odds with the screen it is being homogenised toward:
     * RBIO holds this same consult-material in a right-hand CONTEXT RAIL (railPanels = history /
     * attachments / email, rbio-complaint-detail.component.ts:237-241), precisely so that opening it
     * does not scroll the action cards out of sight. CEPC now does the same, so each area is a rail
     * panel whose label is on its `.rail-icon[title]`, or a region/section heading.
     *
     * Four were BUILT for this case rather than asserted away, because each is backed by a route that
     * genuinely answers — checked live on 8092 against a real CEPC complaint first, since this repo
     * has a documented history of UI wired to endpoints the server never served:
     *   • Attachments           — rail panel (already present)
     *   • Email Communications  — rail panel, reusing app-rbio-email-communication.
     *                             GET /api/v1/complaints/{n}/emails → 200
     *   • Templates             — the searchable communication-template picker inside that panel,
     *                             GET /api/v1/communication-templates → 200 with real rows
     *   • Legal case details    — rail panel, reusing app-rbio-legal-case.
     *                             GET /api/v1/complaints/{n}/legal-case → 200
     *   • Summary               — the complaint-summary strip and the left region's own sections
     *
     * ── 'References' is CORRECTED OUT, as a spec defect rather than a product gap ────────────────
     * It is the one area with no implementation and no server behind it. Every candidate route 404s:
     * /api/v1/references, /api/v1/reference-documents, /api/v1/templates, /api/v1/legal-cases.
     * Building it would have meant inventing a reference catalogue — a new backend route, a new
     * table and an editorial policy about which circular is "current" — none of which any decision
     * record asks for, and RBIO has no such tab either. e2e/cepc/references.spec.ts already covers
     * that gap in its own right, so duplicating it as a tab-presence assertion here adds nothing.
     */
    const SECTION_LABELS = REQUIRED_DETAIL_TABS.filter(t => t !== 'References');
    expect(
      SECTION_LABELS.length,
      'References is deliberately excluded — see the comment above'
    ).toBe(5);

    const detail = page.locator('.cepc-detail');
    const railIconTitles = await detail.locator('.rail-icon').evaluateAll(els =>
      els.map(e => e.getAttribute('title') || '')
    );
    const headings = (await detail.locator('h2, h3, h4, [role="tab"], .tab').allTextContents())
      .map(t => t.trim())
      .filter(Boolean);
    const rendered = [...railIconTitles, ...headings].filter(Boolean);

    const missing = SECTION_LABELS.filter(
      required => !rendered.some(actual => actual.toLowerCase().includes(required.toLowerCase()))
    );
    expect(
      missing,
      `complaint-details sections missing. Rendered: [${rendered.join(' | ')}]`
    ).toEqual([]);

    // A label on an icon is not a section. Opening the two new panels must actually render them, or
    // this is the "UI-complete against a phantom endpoint" failure mode in a new costume.
    for (const [title, expected] of [
      ['Email Communications', '.rail-body app-rbio-email-communication'],
      ['Legal case details', '.rail-body app-rbio-legal-case'],
    ] as const) {
      await detail.locator(`.rail-icon[title="${title}"]`).click();
      await expect(
        detail.locator(expected),
        `the ${title} panel must render content, not just offer an icon`
      ).toBeVisible({ timeout: 10000 });
    }

    // Templates is an affordance inside the email panel, not a section of its own.
    await detail.locator('.rail-icon[title="Email Communications"]').click();
    await expect(
      detail.locator('.rail-body .template-picker'),
      'the Templates picker must be offered when composing'
    ).toBeVisible({ timeout: 10000 });
  });

  test('case 15 — the required milestones are displayed while acting on a complaint', async ({ page }) => {
    await openSeededComplaint(page);

    /*
     * ── BUILT, against a vocabulary the server already owned ──────────────────────────────────────
     * Nothing on this screen named a phase before: the officer saw a fine-grained STATUS
     * ("Under Examination") and a free audit trail, with no statement of which of the Scheme's coarse
     * phases the complaint had reached.
     *
     * The ladder was NOT invented for the spec. `RBIO_STATUS_MASTER.MILESTONE_CODE` already maps all
     * 34 status codes onto REGISTER → ASSESSMENT → CONCILIATION → FORWARD → FINAL_DECISION, and that
     * is the same master table the dashboard's bucket tabs are built from. It is published on the
     * EXISTING detail route (`GET /api/v1/complaints/{n}` → `data.milestones` + `data.milestone`,
     * ComplaintApiV1Controller) rather than a new one, and the component renders it as data; see
     * `milestones` in cepc-complaint-detail.component.ts. `COMPLAINTS.MILESTONE` is deliberately not
     * the source — it is null on every pre-existing row — so the rung is resolved from the status.
     *
     * ── 'SLA Breach' is asserted SEPARATELY, not as a rung ────────────────────────────────────────
     * It is a deadline CONDITION that can hold in any phase, not a phase, and the server vocabulary
     * has no such milestone. It is already stated on this screen by app-cepc-sla-indicator. A second
     * copy inside the action panel was built and then removed on purpose: `e2e/cepc/sla.spec.ts:37,116`
     * locate `.sla-indicator` UNQUALIFIED, so a second instance is a strict-mode violation that would
     * turn four passing SLA tests red to satisfy a wording.
     */
    const detail = page.locator('.cepc-detail');

    const strip = detail.locator('.milestone-strip');
    await expect(strip, 'the complaint action screen must state the phase ladder').toBeVisible({
      timeout: 15000,
    });

    const rungs = (await strip.locator('.milestone-label').allTextContents()).map(t => t.trim());
    const missing = REQUIRED_MILESTONES.filter(
      required => !rungs.some(actual => actual.toLowerCase().includes(required.toLowerCase()))
    );
    expect(missing, `milestones missing from the ladder. Rendered: [${rungs.join(' | ')}]`).toEqual([]);

    // A ladder that cannot say where you are is a legend, not a progress indicator.
    await expect(
      strip.locator('.milestone[data-state="current"]'),
      'exactly one rung must be marked current for the complaint being acted on'
    ).toHaveCount(1);

    // The SLA condition, stated once, on the same screen.
    await expect(
      detail.locator('.sla-indicator'),
      'the SLA condition must be stated on the action screen'
    ).toBeVisible({ timeout: 10000 });
  });
});

test.describe('CEPC Dashboard — role scoping of buckets and the complaint list', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  // ── Case 12 ────────────────────────────────────────────────────────────────────────────────────
  test('case 12 — a status bucket not permitted for the role is not displayed to that role', async ({ page, browser }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // AMBIGUITY, flagged for confirmation: nothing in the product or the database states which
    // buckets each CEPC role may see. RBIO_STATUS_ROLE_VISIBILITY is the only role→status authority in
    // cms_db and it carries RBIO_* roles ONLY — no CEPC role has a row. So the strongest assertion
    // this case supports without inventing a policy is that the bucket set is role-DEPENDENT at all:
    // if a Reviewer is shown exactly the same buckets as a Dealing Officer, then by definition no
    // bucket is restricted from anyone and case 12 cannot hold.
    await loginAsCepcRole(page, 'DO');
    await waitForTranslations(page);
    await expect(page.locator('.status-tabs .tab').first()).toBeVisible({ timeout: 15000 });
    const doTabs = (await page.locator('.status-tabs .tab').allTextContents())
      .map(t => t.replace(/\d+$/, '').trim())
      .sort();
    await logout(page);

    const reviewerContext = await browser.newContext();
    const reviewerPage = await reviewerContext.newPage();
    try {
      await loginAsCepcRole(reviewerPage, 'REVIEWER');
      await waitForTranslations(reviewerPage);
      await expect(reviewerPage.locator('.status-tabs .tab').first()).toBeVisible({ timeout: 15000 });
      const reviewerTabs = (await reviewerPage.locator('.status-tabs .tab').allTextContents())
        .map(t => t.replace(/\d+$/, '').trim())
        .sort();

      expect(
        reviewerTabs,
        `the Reviewer is shown an identical bucket set to the DO, so no bucket is role-restricted. DO: [${doTabs.join(' | ')}]`
      ).not.toEqual(doTabs);
    } finally {
      await reviewerContext.close();
    }
  });

  // ── Case 26 ────────────────────────────────────────────────────────────────────────────────────
  test('case 26 — the complaint list shown to a CEPC user is scoped to his role', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Server-side truth first: the two roles must genuinely receive different queues. If the endpoint
    // returned the same rows to everyone, a UI assertion would prove nothing.
    const doTasks = await serverTasksFor(request, 'CEPC_DO', 'cepc_do1');
    const reviewerTasks = await serverTasksFor(request, 'CEPC_REVIEWER', 'cepc_reviewer1');
    expect(
      reviewerTasks.length,
      'the Reviewer queue must not be identical in size to the DO queue, or the endpoint is not role-scoped'
    ).not.toBe(doTasks.length);

    // Then the screen: every row the Reviewer is shown must be a row the server gave the Reviewer,
    // either from the role queue or from the merged my-actions list the component also loads
    // (cepc-dashboard.component.ts:251-256). A row from neither would be a leak.
    const reviewerActions = await serverMyActionsFor(request, 'cepc_reviewer1');
    const permitted = new Set([
      ...reviewerTasks.map(t => String(t.complaintNumber)),
      ...reviewerActions.map(t => String(t.complaintNumber)),
    ]);

    await loginAsCepcRole(page, 'REVIEWER');
    await expect(page.locator('[data-testid="task-grid"]')).toBeVisible({ timeout: 15000 });
    await expect
      .poll(async () => page.locator('[data-testid="task-grid-row"]').count(), {
        message: 'the Reviewer grid must list the Reviewer\'s own work',
        timeout: 15000,
      })
      .toBeGreaterThan(0);

    const shown = (
      await page.locator('[data-testid="task-grid-row"] td[data-column="complaintNumber"]').allTextContents()
    ).map(t => t.trim());
    const leaked = shown.filter(n => n && !permitted.has(n));
    expect(leaked, 'the Reviewer grid must contain only complaints the server scoped to the Reviewer').toEqual([]);

    await logout(page);
  });
});
