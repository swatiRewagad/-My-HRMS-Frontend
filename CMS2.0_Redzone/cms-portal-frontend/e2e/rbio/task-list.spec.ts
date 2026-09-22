import { test, expect } from './harness';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createRbioComplaint, cleanupRbioComplaint } from '../utils/test-data';

test.describe('RBIO Task List', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const result = await createRbioComplaint(request, {
        subject: 'E2E RBIO Task List Test',
        complainantName: 'Task List Test Citizen',
      });
      complaintNumber = result.complaintNumber;
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('page loads showing RBIO task list heading', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    const heading = page.locator('h1:has-text("RBIO Complaints")');
    await expect(heading).toBeVisible();

    await logout(page);
  });

  test('stats display (total, assigned, in progress, escalated, resolved)', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    const statsBar = page.locator('.stats-bar');
    await expect(statsBar).toBeVisible({ timeout: 10000 });

    const statCards = page.locator('.stats-bar .stat-card');
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(5);

    const labels = await page.locator('.stats-bar .stat-label').allTextContents();
    const joined = labels.join(' ').toLowerCase();
    expect(joined).toContain('total');
    expect(joined).toContain('assigned');
    expect(joined).toContain('in progress');
    expect(joined).toContain('escalated');
    expect(joined).toContain('resolved');

    await logout(page);
  });

  test('task table renders with correct columns', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });

    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const table = page.locator('.data-grid');
    if (await table.isVisible()) {
      const headers = table.locator('thead tr:first-child th');
      const headerCount = await headers.count();
      expect(headerCount).toBeGreaterThan(3);
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }

    await logout(page);
  });

  test('status filter narrows the grid to the chosen status', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const dataRows = page.locator('.data-grid tbody tr:not(:has(.empty-state))');

    // Rows must exist before any filter assertion means anything. The old suite ran with the
    // browser's API calls blocked by CORS, so the grid was always empty and `expect(rowCount).toBe(0)`
    // passed with the filter doing nothing at all.
    await expect
      .poll(async () => dataRows.count(), { message: 'the task grid must load rows before filtering is meaningful' })
      .toBeGreaterThan(0);

    // `filterStatus` IS a signal, so this filter genuinely recomputes — unlike the per-column search
    // boxes (see the fixme at the bottom of this file).
    await page.locator('.queue-select').selectOption('resolved');

    // Every remaining badge must read exactly 'resolved'. Polled on the offending values, so a
    // failure names them rather than just reporting a count.
    await expect
      .poll(
        async () => {
          const badges = await page.locator('.data-grid tbody .status-badge').allTextContents();
          return badges.map(b => b.trim().toLowerCase()).filter(s => s !== 'resolved');
        },
        { message: 'filtering by resolved must leave only resolved rows', timeout: 8000 }
      )
      .toEqual([]);

    await page.locator('.queue-select').selectOption('');
    await expect.poll(async () => dataRows.count(), { timeout: 8000 }).toBeGreaterThan(0);

    await logout(page);
  });

  test('column configuration dialog opens and lists every configurable column', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const colConfigBtn = page.locator('button.btn-icon:has(.pi-cog)');
    await expect(colConfigBtn).toBeVisible({ timeout: 5000 });
    await colConfigBtn.click();

    // The old version wrapped this whole interaction in `if (configPanel.isVisible())`, so a dialog
    // that never opened still passed.
    const configPanel = page.locator('.modal-dialog.column-dialog');
    await expect(configPanel, 'clicking the cog must open the column dialog').toBeVisible({ timeout: 5000 });

    const options = configPanel.locator('.column-option');
    await expect(options.first()).toBeVisible();
    const visibleHeaders = await page.locator('.data-grid thead tr:first-child th').count();
    // Every rendered column must be offered, plus the ones hidden by default.
    expect(await options.count()).toBeGreaterThanOrEqual(visibleHeaders - 1);

    const subjectOption = configPanel.locator('.column-option', { hasText: 'Subject' }).first();
    await expect(subjectOption).toBeVisible();
    await expect(subjectOption.locator('input[type="checkbox"]')).toBeChecked();

    await configPanel.locator('button:has-text("Cancel")').click();
    await expect(configPanel).not.toBeVisible();

    // Whether deselecting actually hides the column is asserted separately — see the fixme at the
    // bottom of this file, which documents why it currently cannot.
    await logout(page);
  });

  test('advanced search dialog opens and closes on apply', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const advSearchBtn = page.locator('button:has-text("Advanced Search")');
    await expect(advSearchBtn).toBeVisible({ timeout: 5000 });
    await advSearchBtn.click();

    // The old version wrapped everything in `if (dialog.isVisible())` and never asserted that the
    // dialog opened or that anything was filtered — it could not fail.
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog, 'clicking Advanced Search must open the dialog').toBeVisible({ timeout: 5000 });

    const numberField = dialog.locator('.search-field:has(label:text-is("Complaint Number")) input');
    await expect(numberField).toBeVisible();
    await numberField.fill('S4-NO-SUCH-COMPLAINT-XYZ-99999');

    const applyBtn = dialog.locator('button:has-text("Search")');
    await expect(applyBtn, 'the dialog must offer a way to apply the filter').toBeVisible();
    await applyBtn.click();

    await expect(dialog, 'applying the filter must close the dialog').not.toBeVisible({ timeout: 5000 });

    // Whether the criteria actually filter the grid is asserted separately — see the fixme at the
    // bottom of this file. Asserting it here would be asserting a known-broken behaviour.
    await logout(page);
  });

  test('pagination controls work', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const pagination = page.locator('.pagination');
    await expect(pagination).toBeVisible({ timeout: 5000 });

    const pageInfo = page.locator('.page-info');
    await expect(pageInfo).toBeVisible();
    const infoText = await pageInfo.textContent();
    expect(infoText).toContain('Showing');

    await logout(page);
  });

  test('click task navigates to detail page', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    const firstRow = page.locator('.data-grid tbody tr').first();
    await firstRow.click();

    await page.waitForURL(/\/staff\/rbio\/task\//, { timeout: 10000 });
    expect(page.url()).toContain('/staff/rbio/task/');

    await logout(page);
  });

  test('visiting a task records it as visited in localStorage', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    await page.evaluate(() => localStorage.removeItem('rbio_visited_ids'));
    await page.reload();
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    await page.locator('.data-grid tbody tr').first().click();
    await page.waitForURL(/\/staff\/rbio\/task\//, { timeout: 10000 });

    const stored = await page.evaluate(() => localStorage.getItem('rbio_visited_ids'));
    expect(stored, 'opening a task must record it as visited').toBeTruthy();
    expect(JSON.parse(stored as string).length).toBeGreaterThanOrEqual(1);

    await logout(page);
  });

  test.fixme(
    'the visited row is marked with the .visited class on return to the list',
    async () => {
      // FIXME — PRODUCTION DEFECT (type mismatch). openTask stores the id as a STRING
      // (`visited.add(String(task.complaintId))`, rbio-tasks.component.ts:203) but the row class is
      // bound with the RAW value (`visitedIds().has(task.complaintId)`, rbio-tasks.component.html:171).
      // The queue endpoint returns complaintId as a NUMBER (verified: `"complaintId":4476`), so
      // `Set<string>.has(4476)` is always false and `.visited` is never applied. The same mismatch
      // makes the "Unread" toggle (component ts:115) treat every task as unread forever.
      //
      // The visit itself IS recorded — covered by the localStorage test above — so this is purely the
      // rendering of it. Un-fixme once both sides agree on a type.
    }
  );

  test.fixme(
    'per-column search boxes filter the grid',
    async () => {
      // FIXME — PRODUCTION DEFECT (missing reactivity), same class as the column-config one below.
      // `columnFilters` is a PLAIN OBJECT (rbio-tasks.component.ts:49) read inside the `filteredTasks`
      // computed (line 108). `[(ngModel)]="columnFilters[col.key]"` mutates a property of that object,
      // which no signal observes, so the computed never recomputes and the grid does not filter.
      // Verified in the browser: typing an impossible value into the first `.col-search` box leaves all
      // 10 rows on screen.
      //
      // The old test asserted `rowCount === 0` and passed only because the grid was empty for an
      // unrelated reason (the browser's API calls were being blocked by CORS), so a broken filter and
      // a working one were indistinguishable. Un-fixme once `columnFilters` is a signal.
    }
  );

  test.fixme(
    'advanced search criteria filter the grid',
    async () => {
      // FIXME — PRODUCTION DEFECT. applyAdvancedSearch (rbio-tasks.component.ts:230) computes a
      // filtered `result` and then THROWS IT AWAY — the local is never assigned anywhere. Instead it
      // does `this.searchText.set(JSON.stringify(q))` (line 241), so the free-text filter is handed the
      // JSON of the criteria object (e.g. `{"complaintNumber":"CMP-1",...}`). `filteredTasks` then
      // substring-matches that whole JSON blob against complaintNumber/complainantName/entityName/
      // subject, which can never match anything, so applying ANY advanced search silently empties the
      // grid regardless of the criteria.
      //
      // That is why an "impossible term filters everything out" assertion passes here for the wrong
      // reason: a valid, matching term would empty the grid too. Un-fixme once the computed result is
      // actually applied.
    }
  );

  test.fixme(
    'deselecting a column in the column-config dialog hides it',
    async () => {
      // FIXME — PRODUCTION DEFECT (missing reactivity). In rbio-tasks.component.ts `allColumns` is a
      // PLAIN ARRAY (line 72) while `visibleColumns` is a computed (line 86). toggleColumnVisibility
      // mutates `col.visible` in place (line 227), which no signal observes, so the computed never
      // recomputes and the header row does not change. Verified in the browser: the dialog opens, the
      // checkbox unchecks, Save closes it, and the grid still renders all 9 columns (before=9, after=9).
      //
      // The equivalent screen rbio-home.component.ts gets this right — `allColumns` is a signal and
      // the toggle uses `.update(...)` (line 307) — so this is a one-component regression, not a
      // design limitation. The old test hid it by capturing headersBefore and never comparing it.
    }
  );
});
