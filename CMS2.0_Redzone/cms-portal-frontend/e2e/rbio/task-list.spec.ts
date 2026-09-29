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

    // Whether deselecting actually HIDES the column is asserted separately, below.
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

    // Whether the criteria actually FILTER the grid is asserted separately, below.
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

  // ──────────────────────────────────────────────────────────────────────────
  // The four assertions below were test.fixme for as long as this screen carried its own grid. Each
  // documented a defect caused by a computed() reading a PLAIN field, which registers no dependency and
  // so never recomputes. Migrating to app-task-grid — whose filters, column visibility and sort are all
  // signals — removed the class of defect rather than patching each instance, so they now assert real
  // behaviour. They must fail if that grid is ever swapped back for a local copy.
  // ──────────────────────────────────────────────────────────────────────────

  test('the visited row is marked with the .visited class on return to the list', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    await page.evaluate(() => localStorage.removeItem('rbio_visited_ids'));
    await page.reload();
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });
    await expect(page.locator('.data-grid tbody tr.visited')).toHaveCount(0);

    await page.locator('.data-grid tbody tr').first().click();
    await page.waitForURL(/\/staff\/rbio\/task\//, { timeout: 10000 });

    await page.goBack();
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    // Exactly one, not "at least one": the id is coerced in ONE place now, so a coercion that dims the
    // wrong rows would be as wrong as one that dims none.
    await expect
      .poll(async () => page.locator('.data-grid tbody tr.visited').count(), {
        message: 'the opened row must be dimmed as visited when the officer returns',
        timeout: 8000
      })
      .toBe(1);

    await logout(page);
  });

  test('per-column search boxes filter the grid', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    const rows = page.locator('[data-testid="task-grid-row"]');
    await expect.poll(async () => rows.count()).toBeGreaterThan(0);

    // Driven by TYPING, not by calling a method: the defect this replaces was invisible to any test that
    // set state directly, because the state was set correctly and only the recomputation was missing.
    const subjectFilter = page.locator('[data-testid="task-grid-column-filter"][data-column="subject"]');
    await expect(subjectFilter).toBeVisible();
    await subjectFilter.fill('ZZZ-NO-SUCH-SUBJECT-99999');

    await expect
      .poll(async () => rows.count(), {
        message: 'an impossible column filter must empty the grid',
        timeout: 8000
      })
      .toBe(0);
    await expect(page.locator('[data-testid="task-grid-empty"]')).toBeVisible();

    // And it must come back, so the assertion above cannot pass because the grid simply broke.
    await subjectFilter.fill('');
    await expect.poll(async () => rows.count(), { timeout: 8000 }).toBeGreaterThan(0);

    await logout(page);
  });

  test('advanced search criteria filter the grid', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid tbody tr', { timeout: 15000 });

    const rows = page.locator('[data-testid="task-grid-row"]');
    await expect.poll(async () => rows.count()).toBeGreaterThan(0);

    // A term that MATCHES, asserted first. The old code emptied the grid for every input, so only a
    // matching term distinguishes a working filter from a broken one.
    const firstNumber = (await rows.first().locator('td[data-column="complaintNumber"]').textContent())?.trim() || '';
    expect(firstNumber, 'a complaint number is needed to search for').toBeTruthy();

    await page.locator('button:has-text("Advanced Search")').click();
    const dialog = page.locator('.modal-dialog.search-dialog');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill(firstNumber);
    await dialog.locator('button:has-text("Search")').click();
    await expect(dialog).not.toBeVisible({ timeout: 5000 });

    await expect
      .poll(async () => rows.count(), {
        message: 'searching for a complaint number that exists must keep exactly that row',
        timeout: 8000
      })
      .toBe(1);
    await expect(rows.first().locator('td[data-column="complaintNumber"]')).toHaveText(firstNumber);

    // Narrowing an ALREADY-ACTIVE search must re-run. advSearchActive is already true at this point, so
    // setting it again notifies nothing — only the revision bump makes the second apply take effect.
    await page.locator('button:has-text("Advanced Search")').click();
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await dialog.locator('.search-field:has(label:text-is("Complaint Number")) input').fill('ZZZ-NO-SUCH-NUMBER-99999');
    await dialog.locator('button:has-text("Search")').click();

    await expect
      .poll(async () => rows.count(), {
        message: 'a second, different search must replace the first result, not leave it on screen',
        timeout: 8000
      })
      .toBe(0);

    await logout(page);
  });

  test('deselecting a column in the column-config dialog hides it', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsRbioRole(page, 'RBIO_OFFICER', '/staff/rbio/tasks');
    await page.waitForSelector('.rbio-home', { timeout: 15000 });
    await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

    const headers = page.locator('.data-grid thead tr:first-child th');
    const before = await headers.count();
    expect(before).toBeGreaterThan(3);
    await expect(page.locator('.data-grid thead th:has-text("Subject")')).toBeVisible();

    const chooser = page.locator('[data-testid="task-grid-column-chooser"]');
    await page.locator('button.btn-icon:has(.pi-cog)').click();
    await expect(chooser).toBeVisible({ timeout: 5000 });

    const subjectOption = chooser.locator('.column-option', { hasText: 'Subject' }).first();
    await subjectOption.locator('input[type="checkbox"]').uncheck();
    await chooser.locator('button:has-text("Save")').click();
    await expect(chooser).not.toBeVisible();

    await expect
      .poll(async () => headers.count(), {
        message: 'deselecting a column must remove its header',
        timeout: 8000
      })
      .toBe(before - 1);
    await expect(page.locator('.data-grid thead th:has-text("Subject")')).toHaveCount(0);

    // Re-checking must restore it, so the assertion above cannot pass because the header row collapsed.
    await page.locator('button.btn-icon:has(.pi-cog)').click();
    await expect(chooser).toBeVisible({ timeout: 5000 });
    await chooser.locator('.column-option', { hasText: 'Subject' }).first().locator('input[type="checkbox"]').check();
    await chooser.locator('button:has-text("Save")').click();
    await expect(page.locator('.data-grid thead th:has-text("Subject")')).toBeVisible({ timeout: 8000 });

    await logout(page);
  });
});
