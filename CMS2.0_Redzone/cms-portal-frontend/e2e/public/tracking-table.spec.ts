import { execFileSync } from 'node:child_process';
import { test, expect } from '../fixtures';
import type { Page } from '@playwright/test';
import {
  createTestComplaint,
  advanceToStatus,
  loginCitizen,
  seedCitizenSession,
} from '../utils/test-data';

/**
 * UST100 / FR-G-031 — the citizen complaint TRACKING TABLE.
 *
 * ── WHICH SCREEN THIS IS ────────────────────────────────────────────────────────────────────────────
 * Three routes could plausibly be "the tracking interface", and only one of them is:
 *
 *   public/track          → components/complaint-tracker/complaint-tracker.component  ← THIS ONE
 *   public/history        → components/public/complaint-history/complaint-history.component
 *   public/complaint/:id  → components/public/complaint-detail/complaint-detail.component
 *
 * `public/track` is the tracking interface. It is what "Track Your Complaint" links to (public-home,
 * file-complaint, withdraw and file-appeal all navigate to /public/track), it is the screen UST98/UST99
 * describe, and — decisively for these cases — it is the only one that presents complaints as a SORTABLE,
 * FILTERABLE table and that shows a complaint's detailed status and 4-stage TIMELINE in place when a row
 * is clicked (FR-G-031 S2). It has no auth guard because it must also serve anonymous
 * track-by-reference-number, but when a citizen session exists ngOnInit switches straight into
 * mobile mode and lists that citizen's complaints without a second OTP (UST99 S1).
 *
 * `public/history` ("My Complaints", reached from the nav bar) is a different, adjacent feature: a
 * five-column per-column text-search grid whose real job is DRAFTS — it merges sessionStorage and
 * server-side drafts with submitted complaints and offers Resume/Delete. It has no sortable columns at
 * all, and clicking a row navigates AWAY to public/complaint/:id. `public/complaint/:id` is that
 * destination: a single complaint's detail/history tabs. Neither is the subject of these cases, and
 * neither is re-tested here.
 *
 * ── SORTING AND FILTERING ARE SERVER-SIDE, AND REALLY ISSUE REQUESTS ────────────────────────────────
 * Verified in the browser, not assumed. The p-table is `[lazy]="true"`, and both the sort headers and
 * the status dropdown re-issue GET /api/v1/complaints with `sortBy`/`sortDir`/`status`. The tests below
 * assert on the REQUEST as well as on the rendered order, precisely because this repo has a class of
 * features that look like they work while issuing no request and persisting nothing.
 *
 * ── AUTHORIZATION IS NOT RE-TESTED ──────────────────────────────────────────────────────────────────
 * e2e/public/tracking-authz.spec.ts already owns 401/403/PII-masking/page-size-cap and the CAPTCHA+OTP
 * flow. This spec is about the table, and it seeds a session directly rather than repeating the OTP UI.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

/**
 * Spreads the registration dates of seeded complaints, so a date sort has something to sort.
 *
 * Every complaint a test can create through the API is stamped `now()`, so all of them land on the same
 * day and a date-ordering assertion would pass against a table that never reordered anything. No
 * endpoint accepts a registration date — CepcWorkflowService stamps it — so the column is written
 * directly, the way {@link backdateComplaintClosure} already does for closure dates.
 */
function backdateRegistration(complaintNumber: string, days: number): void {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing to use an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  if (!Number.isInteger(days) || days < 0 || days > 3650) {
    throw new Error(`Refusing an implausible backdate: ${days}`);
  }
  execFileSync(
    MYSQL_CLI,
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e',
      `UPDATE COMPLAINTS SET created_at = DATE_SUB(NOW(), INTERVAL ${days} DAY) ` +
      `WHERE complaint_number = '${complaintNumber}';`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  );
}

/** A mobile number no other fixture owns, so the list under test contains only what this file seeded. */
function freshMobile(): string {
  // 9 + 9 digits derived from the clock, kept inside the 6-9 first-digit rule the portal enforces.
  return '9' + String(Date.now()).slice(-8) + String(Math.floor(Math.random() * 10));
}

interface SeededCitizen {
  mobile: string;
  token: string;
  /** complaint numbers, in the order seeded: [oldest, middle, newest] */
  numbers: string[];
  closed: string;
}

/**
 * One citizen with three complaints on three DIFFERENT days, exactly one of which is closed.
 *
 * Three is the minimum that makes an ordering assertion meaningful (two rows cannot distinguish a real
 * sort from a coincidence) and one closed row is what lets the Closed filter be shown to REMOVE rows
 * rather than merely to leave them alone.
 */
async function seedCitizenWithComplaints(request: any): Promise<SeededCitizen> {
  const mobile = freshMobile();
  const numbers: string[] = [];

  for (let i = 0; i < 3; i++) {
    const created = await createTestComplaint(request, {
      subject: `UST100 tracking table row ${i}`,
      complainantName: 'Tracking Table Citizen',
      complainantPhone: mobile,
    });
    numbers.push(created.complaintNumber);
  }

  // The FIRST complaint is closed, and is also the OLDEST — so "sorted by date" and "sorted by status"
  // produce DIFFERENT orders. If the closed row were also the newest, a status sort and a date sort
  // would agree and either assertion would pass while the other feature was broken.
  await advanceToStatus(request, numbers[0], 'closed');

  backdateRegistration(numbers[0], 30);
  backdateRegistration(numbers[1], 15);
  backdateRegistration(numbers[2], 1);

  const token = await loginCitizen(request, mobile);
  if (!token) {
    throw new Error(
      'Could not obtain a citizen session. Requires cms.auth.otp.dev-auto-populate=true (dev-local).'
    );
  }

  return { mobile, token, numbers, closed: numbers[0] };
}

/** Opens the tracking table as a logged-in citizen and waits for the first list to land. */
async function openTrackingTable(page: Page, mobile: string, token: string): Promise<void> {
  // The session lives in sessionStorage, which can only be written from the app's own origin — hence
  // the first navigation before seeding it.
  await page.goto(`${APP_BASE}/public/track`);
  await seedCitizenSession(page, mobile, token);
  await page.goto(`${APP_BASE}/public/track`);
  await page.waitForLoadState('networkidle');
}

const listRequests = (page: Page): string[] => {
  const urls: string[] = [];
  page.on('request', req => {
    if (req.method() === 'GET' && /\/api\/v1\/complaints\?/.test(req.url())) {
      urls.push(req.url());
    }
  });
  return urls;
};

const rows = (page: Page) => page.locator('p-table tbody tr');
const cells = (page: Page, nth: number) => page.locator(`p-table tbody tr td:nth-child(${nth})`);

/**
 * The status column once two consecutive reads agree.
 *
 * A lazy p-table repaints when its response lands, so reading immediately after a sort click can capture
 * the PREVIOUS order and assert against it. Waiting for the reading to stabilise avoids that without
 * presuming what the new order should be.
 */
async function settledStatuses(page: Page): Promise<string[]> {
  let previous = '';
  for (let attempt = 0; attempt < 30; attempt++) {
    const current = (await cells(page, COL_STATUS).allInnerTexts()).map(t => t.trim());
    const serialised = current.join('|');
    if (serialised === previous && current.length > 0) return current;
    previous = serialised;
    await page.waitForTimeout(300);
  }
  throw new Error('The status column never stopped changing.');
}

// Column positions in the tracking table, 1-based.
const COL_NUMBER = 1;
const COL_DATE = 2;
const COL_STATUS = 5;

test.describe('UST100 (FR-G-031) — Citizen complaint tracking table', () => {

  let citizen: SeededCitizen;

  test.beforeAll(async ({ request }) => {
    test.setTimeout(180000);
    citizen = await seedCitizenWithComplaints(request);
  });

  test('QA1 — every complaint the complainant raised appears as one row in a single table, with the key columns', async ({ page }) => {
    await openTrackingTable(page, citizen.mobile, citizen.token);

    // ONE table, not one panel per complaint.
    await expect(page.locator('p-table')).toHaveCount(1);

    // One row per complaint, and every seeded complaint present.
    await expect(rows(page)).toHaveCount(citizen.numbers.length);
    const rendered = await cells(page, COL_NUMBER).allInnerTexts();
    for (const number of citizen.numbers) {
      expect(rendered.map(t => t.trim())).toContain(number);
    }

    // The key attributes are visible as labelled columns. Asserted as header text rather than by index
    // so a reordered table still passes and a REMOVED column still fails.
    const headers = (await page.locator('p-table thead th').allInnerTexts()).map(h => h.trim());
    expect(headers).toEqual([
      'Complaint Number',
      'Date of Registration',
      'Category',
      'RE/Bank Name',
      'Status',
      'Closure Clause',
      'Closure Date',
      'Action',
    ]);

    // Data accuracy, not just presence: each row's own cells must be populated, and the closed
    // complaint must be the one showing a closure date.
    for (let i = 0; i < citizen.numbers.length; i++) {
      const row = rows(page).nth(i);
      await expect(row.locator(`td:nth-child(${COL_DATE})`)).not.toHaveText('--');
      await expect(row.locator(`td:nth-child(${COL_STATUS})`)).not.toBeEmpty();
    }
  });

  test('QA2 — clicking a row shows that complaint\'s detailed status and timeline immediately, with no confirmation step', async ({ page }) => {
    await openTrackingTable(page, citizen.mobile, citizen.token);

    const targetNumber = (await cells(page, COL_NUMBER).nth(0).innerText()).trim();

    // Nothing is shown before the click, so what appears after it cannot be pre-existing.
    await expect(page.locator('.status-card')).toBeHidden();

    await rows(page).nth(0).click();

    // "Immediately": no dialog, no "are you sure", no second click. The detail must simply be there.
    const statusCard = page.locator('.status-card');
    await expect(statusCard).toBeVisible({ timeout: 15000 });
    await expect(statusCard.locator('h3')).toHaveText(targetNumber);

    // No confirmation surface of any kind intervened.
    await expect(page.locator('p-confirmdialog, [role="dialog"], .confirm-dialog')).toHaveCount(0);

    // Detailed STATUS and TIMELINE, which is what FR-G-031 S2 asks for — not merely the row expanding.
    await expect(statusCard.locator('app-status-badge')).toBeVisible();
    await expect(page.locator('app-complaint-timeline')).toBeVisible();
  });

  test('QA3 — sorting by DATE reorders the rows chronologically, in both directions', async ({ page }) => {
    const requests = listRequests(page);
    await openTrackingTable(page, citizen.mobile, citizen.token);

    // Default order is newest-first, which is the order the seeded dates were spread to expose.
    const expectedNewestFirst = [citizen.numbers[2], citizen.numbers[1], citizen.numbers[0]];
    expect((await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim()))
      .toEqual(expectedNewestFirst);

    // ── ascending ──
    requests.length = 0;
    await page.locator('th:has-text("Date of Registration")').click();
    await expect.poll(() => requests.some(u => /sortBy=createdAt&sortDir=asc/.test(u)), { timeout: 15000 })
      .toBe(true);
    await expect.poll(
      async () => (await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim()),
      { timeout: 15000 }
    ).toEqual([...expectedNewestFirst].reverse());

    // ── descending again ──
    requests.length = 0;
    await page.locator('th:has-text("Date of Registration")').click();
    await expect.poll(() => requests.some(u => /sortBy=createdAt&sortDir=desc/.test(u)), { timeout: 15000 })
      .toBe(true);
    await expect.poll(
      async () => (await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim()),
      { timeout: 15000 }
    ).toEqual(expectedNewestFirst);
  });

  test('QA4 — sorting by STATUS reorders the rows by status grouping, in both directions', async ({ page }) => {
    const requests = listRequests(page);
    await openTrackingTable(page, citizen.mobile, citizen.token);

    // ── ascending: the server sorts on the stored lowercase value, so 'assigned' precedes 'closed' ──
    requests.length = 0;
    await page.locator('th:has-text("Status")').click();
    await expect.poll(() => requests.some(u => /sortBy=status&sortDir=asc/.test(u)), { timeout: 15000 })
      .toBe(true);

    // The rows are read only once the re-sorted page has actually rendered. Polling for "three rows whose
    // statuses have stopped changing" waits for the render without waiting for the expected ORDER, which
    // is the thing under test.
    await expect(rows(page)).toHaveCount(3);
    const asc = await settledStatuses(page);

    // Grouping, asserted as a property rather than as one hardcoded permutation: equal statuses must be
    // adjacent, and the order must be a genuine sort of the values present.
    expect(asc).toEqual([...asc].sort());
    expect(asc[asc.length - 1]).toBe('Closed');

    // ── descending ──
    requests.length = 0;
    await page.locator('th:has-text("Status")').click();
    await expect.poll(() => requests.some(u => /sortBy=status&sortDir=desc/.test(u)), { timeout: 15000 })
      .toBe(true);
    await expect.poll(
      async () => (await cells(page, COL_STATUS).allInnerTexts()).map(t => t.trim()),
      { timeout: 15000 }
    ).toEqual([...asc].reverse());
  });

  test('QA5 — filtering for Closed shows only Closed rows; every other row is removed', async ({ page }) => {
    const requests = listRequests(page);
    await openTrackingTable(page, citizen.mobile, citizen.token);

    await expect(rows(page)).toHaveCount(3);

    requests.length = 0;
    await page.selectOption('#statusFilter', 'CLOSED');

    // The filter must reach the SERVER. A dropdown that only hid rows locally would still "look right"
    // on page one and silently lie about totals and later pages.
    await expect.poll(() => requests.some(u => /[?&]status=CLOSED/.test(u)), { timeout: 15000 }).toBe(true);

    await expect(rows(page)).toHaveCount(1);
    expect((await cells(page, COL_STATUS).allInnerTexts()).map(t => t.trim())).toEqual(['Closed']);
    // The removed rows are GONE from the view, not merely visually de-emphasised.
    expect((await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim())).toEqual([citizen.closed]);
  });

  test('QA6 — filtering for Open shows only open complaints; every other row is removed', async ({ page }) => {
    const requests = listRequests(page);
    await openTrackingTable(page, citizen.mobile, citizen.token);

    await expect(rows(page)).toHaveCount(3);

    // The complainant must be able to isolate the complaints still being worked on. This is the
    // counterpart of the Closed filter in QA5 and is stated as its own scenario, so it is asserted as
    // its own capability and not inferred from Closed working.
    const openOption = page.locator('#statusFilter option', { hasText: /^\s*Open\s*$/ });
    await expect(openOption,
      'The tracking table offers no way to filter for OPEN complaints — see the note on this test.'
    ).toHaveCount(1);

    requests.length = 0;
    await page.selectOption('#statusFilter', { label: 'Open' });
    await expect.poll(() => requests.length, { timeout: 15000 }).toBeGreaterThan(0);

    // Only complaints that are still live remain, and the closed one is gone.
    const remaining = (await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim());
    expect(remaining).not.toContain(citizen.closed);
    expect(remaining.sort()).toEqual([citizen.numbers[1], citizen.numbers[2]].sort());
    expect((await cells(page, COL_STATUS).allInnerTexts()).map(t => t.trim()))
      .not.toContain('Closed');
  });

  test('QA7 — clearing all filters restores the full unfiltered list', async ({ page }) => {
    const requests = listRequests(page);
    await openTrackingTable(page, citizen.mobile, citizen.token);

    const unfiltered = (await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim());
    expect(unfiltered).toHaveLength(3);

    await page.selectOption('#statusFilter', 'CLOSED');
    await expect(rows(page)).toHaveCount(1);

    requests.length = 0;
    await page.selectOption('#statusFilter', '');

    // Clearing must re-ask the server WITHOUT a status, not replay a cached page.
    await expect.poll(
      () => requests.some(u => /\/api\/v1\/complaints\?/.test(u) && !/[?&]status=/.test(u)),
      { timeout: 15000 }
    ).toBe(true);

    await expect(rows(page)).toHaveCount(3);
    expect((await cells(page, COL_NUMBER).allInnerTexts()).map(t => t.trim())).toEqual(unfiltered);
  });
});

test.describe('UST100 (FR-G-031) S5 — a complainant who has raised nothing', () => {

  /**
   * A FRESH citizen, never a fixture whose complaints have been deleted.
   *
   * Deleting another citizen's rows would both corrupt a shared, persistent cms_db for every other
   * session and prove the wrong thing: "rows removed from under a live account" is not the state this
   * case describes.
   */
  let emptyMobile: string;
  let emptyToken: string;

  test.beforeAll(async ({ request }) => {
    emptyMobile = freshMobile();
    const token = await loginCitizen(request, emptyMobile);
    if (!token) {
      throw new Error(
        'Could not obtain a citizen session. Requires cms.auth.otp.dev-auto-populate=true (dev-local).'
      );
    }
    emptyToken = token;

    // The precondition is asserted, not assumed. If this citizen somehow HAS complaints the empty-state
    // test below would pass or fail for a reason that has nothing to do with the empty state.
    const listed = await request.get(
      `${process.env['API_BASE_URL'] || 'http://localhost:8082'}/api/v1/complaints?phone=${emptyMobile}`,
      { headers: { 'X-Citizen-Token': token } }
    );
    expect(listed.status()).toBe(200);
    expect((await listed.json()).data.totalElements).toBe(0);
  });

  test('QA8a — the table area displays exactly "No complaints found." and no complaint rows', async ({ page }) => {
    // A "No complaints found." produced by a FAILED fetch is not this case passing, so the response is
    // observed: it must be a 200 carrying an empty page.
    let listStatus = 0;
    let listBody = '';
    page.on('response', async res => {
      if (res.request().method() === 'GET' && /\/api\/v1\/complaints\?/.test(res.url())) {
        listStatus = res.status();
        listBody = await res.text().catch(() => '');
      }
    });

    await openTrackingTable(page, emptyMobile, emptyToken);

    await expect.poll(() => listStatus, { timeout: 15000 }).toBe(200);
    expect(JSON.parse(listBody).data.totalElements).toBe(0);

    // The table itself renders — the empty message belongs to the table area, and hiding the table in
    // the one state its empty message describes is what made that message unreachable before.
    await expect(page.locator('p-table')).toHaveCount(1);

    const emptyCell = page.locator('p-table tbody tr td');
    await expect(emptyCell).toHaveCount(1);
    // EXACT text, not a substring: UST100 S5 states the wording, and toHaveText trims surrounding
    // whitespace only.
    await expect(emptyCell).toHaveText('No complaints found.');

    // No complaint rows. Every seeded row carries .complaint-row; the empty placeholder does not.
    await expect(page.locator('p-table tbody tr.complaint-row')).toHaveCount(0);

    // The citizen must not additionally be shown a failure banner for a perfectly normal state.
    await expect(page.locator('.error-message')).toBeHidden();
  });

  test('QA8b — clicking in the empty state triggers no action and no error, and the message is unchanged', async ({ page }) => {
    const pageErrors: string[] = [];
    const consoleErrors: string[] = [];
    page.on('pageerror', err => pageErrors.push(err.message));
    page.on('console', msg => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

    await openTrackingTable(page, emptyMobile, emptyToken);

    const emptyCell = page.locator('p-table tbody tr td');
    await expect(emptyCell).toHaveText('No complaints found.');

    const urlBefore = page.url();
    const navigations: string[] = [];
    page.on('framenavigated', frame => { if (frame === page.mainFrame()) navigations.push(frame.url()); });

    // Click the row in the empty state — which is the placeholder row, the only row there is.
    await page.locator('p-table tbody tr').click();
    // Deliberately a fixed settle rather than a poll: the assertion is that NOTHING happens, and there
    // is no positive condition to wait for.
    await page.waitForTimeout(2000);

    // No action: no navigation, and no complaint detail opened.
    expect(navigations).toEqual([]);
    expect(page.url()).toBe(urlBefore);
    await expect(page.locator('.status-card')).toBeHidden();

    // No error: nothing thrown, nothing logged, no error banner.
    expect(pageErrors).toEqual([]);
    expect(consoleErrors.filter(e => !/favicon|\[vite\]/i.test(e))).toEqual([]);
    await expect(page.locator('.error-message')).toBeHidden();

    // And the message is unchanged.
    await expect(emptyCell).toHaveText('No complaints found.');
  });
});
