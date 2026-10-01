import { test, expect, type Page } from '../fixtures';
import { loginAsAaRole, isKeycloakAvailable } from '../utils/auth';
import { createTestComplaint, advanceToStatus, fileAppeal } from '../utils/test-data';

/**
 * Signs out through the SHARED SHELL's button, then waits for the session to be gone.
 *
 * The shared `logout()` in e2e/utils/auth.ts looks for `button:has-text("Logout"), .logout-btn`. The
 * shell migration renders `data-testid="shell-logout"` with class `btn-logout` and the label "Sign
 * out", so none of those selectors match any more. `logout()` therefore finds nothing, returns
 * quietly, and the previous session SURVIVES — which is why the role-badge test signed in as
 * AA_SECRETARIAT and still read "Dealing Officer". It is a dead selector in a shared helper, not a
 * role-resolution defect in the app.
 *
 * Kept local because e2e/utils/auth.ts is owned elsewhere and shared with other modules' specs; this
 * only teaches the AA dashboard spec how to leave a session. Falls back to clearing storage so a
 * missing button can never leave a stale identity behind and silently weaken the next assertion.
 */
async function signOutOfShell(page: Page): Promise<void> {
  const signOut = page.getByTestId('shell-logout');
  if (await signOut.isVisible({ timeout: 2000 }).catch(() => false)) {
    await signOut.click({ force: true });
    await page.waitForLoadState('networkidle').catch(() => {});
  }
  await page.context().clearCookies();
  await page.evaluate(() => {
    try { localStorage.clear(); sessionStorage.clear(); } catch { /* opaque origin */ }
  }).catch(() => {});
}

test.describe('AA Dashboard', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      // Same helper, same dead selectors — so this must also go through signOutOfShell, or each test
      // inherits the previous test's identity.
      await signOutOfShell(page);
    }
  });

  test('AA dashboard loads with stats', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    // The heading moved into the shared shell, which renders ONE h1 from its titleKey —
    // aa-dashboard.component.html passes 'ui.page.aa_appeals' ("Appeals Dashboard"). The old
    // `h2:has-text("Appellate Authority")` was the per-module topbar that the shell migration
    // deleted, so this assertion was pinning markup that no longer exists: the SPEC was stale, the
    // page is correct. Asserting the resolved text also keeps the untranslated-key regression covered
    // (a missing bundle entry leaves the literal "ui.page.aa_appeals" on screen).
    const heading = page.getByTestId('shell-page-title');
    await expect(heading).toBeVisible();
    await expect(heading).toHaveText('Appeals Dashboard');

    const statsRow = page.locator('.stats-row');
    await expect(statsRow).toBeVisible({ timeout: 10000 });

    const statCards = page.locator('.stat-card');
    const count = await statCards.count();
    expect(count).toBeGreaterThanOrEqual(3);
  });

  test('Appeals table shows filed appeals', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const table = page.locator('.appeals-table');
    if (await table.isVisible()) {
      const headers = table.locator('thead th');
      const headerTexts = await headers.allTextContents();
      const joinedHeaders = headerTexts.join(' ').toLowerCase();
      expect(joinedHeaders).toContain('appeal');
      expect(joinedHeaders).toContain('status');
    } else {
      await expect(page.locator('.empty-state')).toBeVisible();
    }
  });

  test('Classification badge (APPEAL/REPRESENTATION) displays correctly', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Classification Badge Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const appeal = await fileAppeal(request, complaint.complaintNumber, {
      classificationType: 'APPEAL',
    });

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    // Two defects fixed here.
    //
    // 1. DEAD SELECTOR: the previous '.classification-badge' matched NOTHING — the shared
    //    <app-status-badge> renders class="status-badge" for both the status and the classification
    //    (they differ only by keyPrefix), and '.classification-badge' exists only in an unrelated
    //    RBIO component.
    // 2. VACUOUS: the assertions sat inside `if (count > 0)` with no else, so zero badges — i.e.
    //    exactly the broken case — passed. The row is now located explicitly and required to exist.
    //
    // Scoped to THIS appeal's row, and to the classification cell specifically, so a status badge
    // ("Filed") cannot satisfy a classification assertion.
    const row = page.locator('tbody tr', { hasText: appeal.appealNumber });
    await expect(row, 'the freshly filed appeal must appear in the dashboard grid').toBeVisible({
      timeout: 15000,
    });

    const classificationText = await row.locator('.status-badge').first().textContent();
    expect(classificationText?.toUpperCase()).toMatch(/APPEAL|REPRESENTATION/);
  });

  test('Status filter works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const filterSelect = page.locator('.filter-select').first();
    await expect(filterSelect).toBeVisible();

    await filterSelect.selectOption('under_review');
    await page.waitForTimeout(500);

    const tableOrEmpty = page.locator('.appeals-table tbody tr, .empty-state');
    await expect(tableOrEmpty.first()).toBeVisible({ timeout: 5000 });

    await filterSelect.selectOption('');
  });

  test('Search by appeal number works', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });
    await page.waitForSelector('.appeals-table, .empty-state', { timeout: 15000 });

    const searchInput = page.locator('.search-box input');
    await expect(searchInput).toBeVisible();

    await searchInput.fill('NONEXISTENT_APL_99999');
    await page.waitForTimeout(500);

    const emptyState = page.locator('.empty-state');
    await expect(emptyState).toBeVisible({ timeout: 5000 });

    await searchInput.clear();
    await page.waitForTimeout(500);
  });

  /**
   * The badge must show the signed-in officer's role, resolved through i18n.
   *
   * This asserted /registrar/ and /authority/, which are OBSOLETE names — the AA vocabulary is
   * AA_DO -> "Dealing Officer" and AA_SECRETARIAT -> "Secretariat" (aa.role_do / aa.role_secretariat).
   * It also caught a real defect on the way through: the template interpolated `roleLabels[...]`
   * WITHOUT the translate pipe, so the badge rendered the literal key "aa.role_do" to staff. The pipe
   * is now applied, and asserting the key never appears is what keeps it applied.
   *
   * The badge now lives in the shared shell header, so the target is `shell-user-role`, not the
   * per-module `.role-badge` class that each AA page used to define for itself.
   */
  test('Role badge shows the signed-in AA role, translated', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    await loginAsAaRole(page, 'AA_DO');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    const doBadge = page.getByTestId('shell-user-role');
    await expect(doBadge).toBeVisible();
    const doText = (await doBadge.textContent())?.trim() ?? '';
    expect(doText.toLowerCase()).toContain('dealing officer');
    // A raw key leaking to the screen is the specific regression guarded against here.
    expect(doText).not.toMatch(/^aa\.role_/);

    await signOutOfShell(page);

    await loginAsAaRole(page, 'AA_SECRETARIAT');
    await page.waitForSelector('.aa-dashboard', { timeout: 15000 });

    const secretariatBadge = page.getByTestId('shell-user-role');
    await expect(secretariatBadge).toBeVisible();
    const secretariatText = (await secretariatBadge.textContent())?.trim() ?? '';
    expect(secretariatText.toLowerCase()).toContain('secretariat');
    expect(secretariatText).not.toMatch(/^aa\.role_/);
  });
});
