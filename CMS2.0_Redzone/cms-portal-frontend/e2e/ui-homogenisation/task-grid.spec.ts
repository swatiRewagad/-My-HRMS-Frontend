import { test, expect } from '../fixtures';
import { isKeycloakAvailable, loginAsCepcRole, logout } from '../utils/auth';

/**
 * The shared task grid, exercised through a real module.
 *
 * <h2>Why every filter assertion here TYPES</h2>
 * All eighteen hand-rolled grids stored column filters in a plain object
 * (`columnFilters: Record<string, string> = {}`) and read it inside a `computed()`. A plain field
 * registers no reactive dependency, so typing recomputed nothing and the filter silently did nothing.
 * A test that asserted on the class field would have passed against that broken code. Only driving the
 * input and observing the rendered rows can tell the difference, so that is what these do.
 */
test.describe('Shared task grid', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO');
    await expect(page.getByTestId('task-grid')).toBeVisible({ timeout: 20000 });
  });

  test.afterEach(async ({ page }) => {
    await logout(page).catch(() => { /* logout is best-effort teardown */ });
  });

  test('renders the grid with translated headers rather than raw keys', async ({ page }) => {
    // Headers live in the first row; the second row holds the per-column filter inputs and is empty of
    // text, so it is excluded rather than asserted as a blank header.
    const headers = page.locator('[data-testid="task-grid"] thead tr').first().locator('th');
    await expect(headers.first()).toBeVisible();

    const texts = (await headers.allInnerTexts()).map(t => t.trim()).filter(Boolean);
    expect(texts.length).toBeGreaterThan(0);

    // A header equal to its own key means the pipe echoed an unseeded key.
    for (const text of texts) {
      expect(text, `header "${text}" is a raw translation key`).not.toMatch(/^ui\.(col|grid)\./);
    }
  });

  test('every data table header carries a scope attribute', async ({ page }) => {
    const unscoped = await page.locator('[data-testid="task-grid"] thead th:not([scope])').count();
    expect(unscoped, 'each th needs scope for screen readers').toBe(0);
  });

  test('TYPING in a column filter actually narrows the rows', async ({ page }) => {
    const rows = page.locator('[data-testid="task-grid-row"]');
    const before = await rows.count();
    test.skip(before === 0, 'no complaints in this queue to filter');

    // A value taken from the data, so the filter is guaranteed to match at least one row.
    const firstCellText = (await rows.first().locator('td').nth(1).innerText()).trim();
    test.skip(firstCellText.length < 2, 'first cell too short to filter on meaningfully');

    const filter = page.locator('[data-testid="task-grid-column-filter"]').nth(1);
    await filter.fill(firstCellText);

    // The filter is synchronous through a signal, but allow the render to settle.
    await expect.poll(async () => rows.count(), { timeout: 5000 }).toBeLessThanOrEqual(before);

    const after = await rows.count();
    expect(after, 'a matching filter must still show at least the matching row').toBeGreaterThan(0);

    // Every surviving row must contain the term — proof the filter ran, not merely that a count changed.
    for (const row of await rows.all()) {
      expect((await row.innerText()).toLowerCase()).toContain(firstCellText.toLowerCase().slice(0, 4));
    }
  });

  test('a filter matching nothing shows the empty state, not a blank table', async ({ page }) => {
    const filter = page.locator('[data-testid="task-grid-column-filter"]').first();
    await filter.fill('zzzz-no-such-value-zzzz');

    await expect(page.getByTestId('task-grid-empty')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('[data-testid="task-grid-row"]')).toHaveCount(0);
  });

  test('the free-text search filters across columns', async ({ page }) => {
    const rows = page.locator('[data-testid="task-grid-row"]');
    const before = await rows.count();
    test.skip(before === 0, 'no complaints in this queue to search');

    await page.getByTestId('task-grid-search').fill('zzzz-no-such-value-zzzz');
    await expect(page.getByTestId('task-grid-empty')).toBeVisible({ timeout: 5000 });

    await page.getByTestId('task-grid-search').fill('');
    await expect.poll(async () => rows.count(), { timeout: 5000 }).toBe(before);
  });

  test('sorting a column reorders the rendered rows', async ({ page }) => {
    const rows = page.locator('[data-testid="task-grid-row"]');
    test.skip((await rows.count()) < 2, 'need at least two rows to observe a sort');

    const columnIndex = 1;
    const readColumn = async () =>
      Promise.all((await rows.all()).map(async r => (await r.locator('td').nth(columnIndex).innerText()).trim()));

    const header = page.locator('[data-testid="task-grid"] thead tr').first().locator('th').nth(columnIndex);

    await page.locator('[data-testid="task-grid"] thead .th-sort').nth(columnIndex).click();
    // Wait for the sort STATE rather than for a duration. Reading the rows straight after the click
    // races the re-render, which is what made an earlier version of this test intermittent.
    await expect(header).toHaveAttribute('aria-sort', 'ascending');

    // Asserted WITHIN the visible page only. The grid sorts the whole result set and then paginates —
    // which is correct — so comparing page 1 against a sort of page 1's original contents would fail
    // even on a working sort, because page 1 legitimately receives different rows after sorting.
    //
    // Polled because the rows re-render asynchronously after the signal updates; the predicate is the
    // real assertion, so a grid that never sorts still fails rather than being waited into passing.
    await expect
      .poll(async () => {
        const current = await readColumn();
        const sorted = [...current].sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
        return JSON.stringify(current) === JSON.stringify(sorted);
      }, { timeout: 10000, message: 'the visible page must itself be in ascending order' })
      .toBe(true);

    const ascending = await readColumn();

    await page.locator('[data-testid="task-grid"] thead .th-sort').nth(columnIndex).click();
    await expect(header).toHaveAttribute('aria-sort', 'descending');

    await expect
      .poll(async () => {
        const current = await readColumn();
        const sorted = [...current].sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
        return JSON.stringify(current) === JSON.stringify(sorted);
      }, { timeout: 10000, message: 'the visible page must itself be in descending order' })
      .toBe(true);

    const descending = await readColumn();

    // The direction genuinely flipped: ascending page 1 starts at the global minimum, descending page 1
    // starts at the global maximum, so the two first values must differ whenever the set has more than
    // one distinct value. Comparing the arrays themselves would be wrong — page 1 holds different rows
    // in each direction.
    if (ascending[0] !== descending[0]) {
      expect(descending[0].localeCompare(ascending[0], undefined, { numeric: true })).toBeGreaterThan(0);
    }
  });

  test('the column chooser can hide a column', async ({ page }) => {
    const headerCount = await page.locator('[data-testid="task-grid"] thead tr').first().locator('th').count();

    await page.getByTestId('task-grid-columns').click();
    await expect(page.getByTestId('task-grid-column-chooser')).toBeVisible();

    await page.locator('[data-testid="task-grid-column-chooser"] input[type="checkbox"]:checked').first().uncheck();

    await expect
      .poll(async () => page.locator('[data-testid="task-grid"] thead tr').first().locator('th').count(), { timeout: 5000 })
      .toBe(headerCount - 1);
  });

  test('clicking a row opens the complaint', async ({ page }) => {
    const rows = page.locator('[data-testid="task-grid-row"]');
    test.skip((await rows.count()) === 0, 'no complaints in this queue to open');

    await rows.first().click();
    // The task IS the complaint: a row click must navigate to its detail route.
    await expect(page).toHaveURL(/\/cepc\/complaint\//, { timeout: 15000 });
  });
});
