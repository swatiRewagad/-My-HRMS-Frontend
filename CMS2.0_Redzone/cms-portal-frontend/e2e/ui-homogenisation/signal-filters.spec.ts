import { test, expect, Page } from '@playwright/test';
import { redirectBrowserApiCalls } from '../utils/api-redirect';
import { seedCitizenSession, loginCitizen } from '../utils/test-data';
import { loginAsCepcRole } from '../utils/auth';

/**
 * Column and search filters must actually filter when a user TYPES in them.
 *
 * <h2>The bug</h2>
 *
 * <p>Each affected screen read a PLAIN CLASS FIELD inside a {@code computed()}:
 *
 * <pre>
 *   columnSearchText = '';                                    // plain field
 *   filteredColumns = computed(() => ... this.columnSearchText ... );
 * </pre>
 *
 * <p>A computed() re-evaluates only when a SIGNAL it read changes. A plain field registers no
 * dependency, so the value was captured once and the filter never recomputed: the user typed and
 * nothing happened. `[(ngModel)]` dutifully updated the field, which is why this survives code review
 * and why the class field always holds the right value.
 *
 * <h2>Why these tests type instead of setting the field</h2>
 *
 * <p>That last point is the trap. A test that sets the component field and asserts on the field — or on
 * a `computed()` read in the same tick — PASSES against the broken code. The defect is only observable
 * as "the DOM did not change after input", so every assertion here is made on RENDERED ROWS after real
 * keystrokes.
 *
 * <p>FIVE components were affected, not the four reported: `cepc-dashboard` had the identical defect
 * and had been recorded as already fixed. Its column dialog turned out to be unreachable dead code
 * after the task-grid migration (nothing set `showColumnConfig` to true), so it was deleted rather than
 * fixed — `shared/task-grid` supplies a working column chooser, which the last test here covers.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** Seeds the CRPC session this screen reads, so the test does not depend on Keycloak SSO. */
async function seedCrpcSession(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('crpc_user', JSON.stringify({
      id: 'e2e_deo', name: 'E2E DEO', role: 'DEO',
    }));
  });
}

function columnOptions(page: Page) {
  return page.locator('.column-list .column-option');
}

test.describe('Signal-backed filters re-render when typed into', () => {

  test.describe('CRPC DEO home — column search', () => {

    async function openColumnDialog(page: Page) {
      await redirectBrowserApiCalls(page);
      await seedCrpcSession(page);
      await page.goto(`${APP_BASE}/crpc/home`, { waitUntil: 'domcontentloaded' });

      const toggle = page.locator('button[title="Column Selection"]');
      await toggle.waitFor({ state: 'visible', timeout: 20000 });
      await toggle.click();

      const search = page.locator('input.column-search');
      await search.waitFor({ state: 'visible', timeout: 10000 });
      await expect.poll(async () => columnOptions(page).count(), { timeout: 10000 })
        .toBeGreaterThan(1);
      return search;
    }

    test('typing narrows the column list, and clearing restores it', async ({ page }) => {
      const search = await openColumnDialog(page);
      const before = await columnOptions(page).count();

      await search.fill('date');

      // Wait on OBSERVABLE STATE, not a fixed timeout: the assertion IS that the DOM changed.
      await expect.poll(async () => columnOptions(page).count(), { timeout: 5000 })
        .toBeLessThan(before);

      const narrowed = await columnOptions(page).allTextContents();
      expect(narrowed.length, 'a matching column should survive').toBeGreaterThan(0);
      for (const label of narrowed) {
        expect(label.toLowerCase()).toContain('date');
      }

      await search.fill('');
      await expect.poll(async () => columnOptions(page).count(), { timeout: 5000 })
        .toBe(before);
    });

    test('a fragment matching nothing empties the list', async ({ page }) => {
      const search = await openColumnDialog(page);
      await search.fill('zzzz-no-such-column');

      await expect.poll(async () => columnOptions(page).count(), { timeout: 5000 }).toBe(0);
    });
  });

  test.describe('Public complaint history — five object-backed column filters', () => {

    /**
     * The only affected filter whose state is an OBJECT behind a signal. Mutating a property of a
     * signal's value does not notify, so this also proves the immutable `setFilter` update works.
     */
    test('typing a complaint number that matches nothing empties the table', async ({ page, request }) => {
      await redirectBrowserApiCalls(page);

      // A REAL token from the auth endpoints. A fabricated one passes the client-side route guard but
      // the API answers 401, so the table renders empty and the test would skip while proving nothing.
      // 9876543210 is the seeded citizen that actually has complaints.
      const token = await loginCitizen(request, '9876543210');
      if (!token) {
        test.skip(true, 'citizen login unavailable (backend not exposing devOtp under dev-local)');
      }

      // seedCitizenSession writes the keys PublicAuthService actually reads (cms_public_session +
      // cms_public_last_activity). Inventing key names silently produces an unauthenticated page.
      await page.goto(`${APP_BASE}/public`, { waitUntil: 'domcontentloaded' });
      await seedCitizenSession(page, '9876543210', token!);
      // The route is /public/history -- NOT /public/complaint-history. A wrong path silently
      // redirects to the landing page and every assertion below would skip rather than fail.
      await page.goto(`${APP_BASE}/public/history`, { waitUntil: 'domcontentloaded' });

      // Poll for ATTACHMENT rather than isVisible(): the filter inputs live inside <th> cells of a wide
      // table and are not always in the viewport, so a visibility probe returns false while the control
      // is perfectly present and typable. An isVisible() guard here silently skipped the whole test.
      const input = page.locator('input[aria-label="Filter by complaint number"]');
      await expect.poll(async () => input.count(), { timeout: 20000 }).toBe(1);
      await input.scrollIntoViewIfNeeded();

      // DATA rows only: the empty state renders its own <tr>, so counting every tbody row would read
      // 1 for an empty table and make a broken filter look like a working one.
      const rows = page.locator('table tbody tr.tbody-row');
      await expect.poll(async () => rows.count(), { timeout: 15000 }).toBeGreaterThan(0);
      const before = await rows.count();

      await input.fill('ZZZ-NO-SUCH-COMPLAINT');
      await expect.poll(async () => rows.count(), { timeout: 5000 }).toBe(0);
      await expect(page.locator('td.empty-cell')).toBeVisible();

      await input.fill('');
      await expect.poll(async () => rows.count(), { timeout: 5000 }).toBe(before);
    });
  });

  test.describe('shared/task-grid — the column chooser CEPC now uses', () => {

    /**
     * CEPC's own column dialog was deleted as unreachable dead code, so the behaviour that matters for
     * that screen is the shared grid's chooser. This proves it toggles a column's visibility, which is
     * what the dead dialog was pretending to offer.
     */
    test('toggling a column in the shared chooser changes the rendered header row', async ({ page }) => {
      await redirectBrowserApiCalls(page);
      await loginAsCepcRole(page, 'DO', '/cepc/dashboard');

      const toggle = page.getByTestId('task-grid-columns');
      if (!(await toggle.isVisible({ timeout: 20000 }).catch(() => false))) {
        test.skip(true, 'CEPC dashboard requires an authenticated staff session');
      }

      await toggle.click();
      const chooser = page.getByTestId('task-grid-column-chooser');
      await expect(chooser).toBeVisible();

      const headersBefore = await page.locator('thead th').count();
      expect(headersBefore).toBeGreaterThan(1);

      await chooser.locator('input[type="checkbox"]:checked').first().uncheck();

      await expect.poll(async () => page.locator('thead th').count(), { timeout: 5000 })
        .toBeLessThan(headersBefore);
    });
  });
});
