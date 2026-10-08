import { test, expect, Page } from '@playwright/test';
import { loginAsCepcRole, isKeycloakAvailable } from '../utils/auth';

/**
 * Covers the CEPC dashboard (KPI cards + all six tabs) and the complaint detail view's tab bar
 * against the cms-backend search API — the path that replaced the old cms-search-service/OpenSearch
 * endpoint.
 *
 * Why this is a separate file from dashboard.spec.ts: that spec asserts `.stats-bar`,
 * `.data-grid` and columns like "Under Examination", none of which exist in any CEPC component —
 * they belong to the crpc/rbio home screens. `/cepc/dashboard` redirects to `/cepc`, which renders
 * CepcHomeComponent wrapping CepcDashboardComponent, and that renders `.kpi-card-surface`,
 * `p-tab` + `.tab-count-badge`, and `tr.clickable-row`. Those are the selectors used here.
 *
 * DETAIL_COMPLAINT_ID is a dev-local seeded complaint. The seeder assigns ids 139-156
 * (N202526BLR000001-000018); 139 is the one with every summary block populated.
 */

const DETAIL_COMPLAINT_ID = 139;

const TAB_LABELS = [
  'All',
  'Draft',
  'Meeting Scheduled',
  'Sent Back to Me',
  'Sent to RE',
  'Contact Person',
];

/**
 * Records every failed request for the life of the page. The detail view makes ~45 calls and the
 * original defect was that 19 of them 404'd while the screen still rendered, so "the page looks
 * fine" is not evidence. Asserting on this list is.
 */
function trackFailedRequests(page: Page): string[] {
  const failures: string[] = [];
  page.on('response', res => {
    const status = res.status();
    if (status === 404 || status >= 500) {
      failures.push(`${status} ${res.request().method()} ${res.url()}`);
    }
  });
  return failures;
}

async function readTabBadges(page: Page): Promise<Record<string, string>> {
  const badges: Record<string, string> = {};
  for (const label of TAB_LABELS) {
    const tab = page.locator('p-tab', { hasText: label }).first();
    const badge = tab.locator('.tab-count-badge');
    // "All" carries no badge by design — the grid's paginator reports its total instead.
    badges[label] = (await badge.count()) > 0 ? ((await badge.first().textContent()) || '').trim() : '';
  }
  return badges;
}

test.describe('CEPC dashboard — KPI cards and all seven tabs', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', '/cepc');
  });

  test('all seven tabs are rendered', async ({ page }) => {
    const tabs = page.locator('.dashboard-tabs-container p-tab');
    await expect(tabs).toHaveCount(TAB_LABELS.length, { timeout: 20000 });

    const rendered = (await tabs.allTextContents()).map(t => t.replace(/\d+\s*$/, '').trim());
    for (const label of TAB_LABELS) {
      expect(rendered.join(' | '), `tab "${label}" must be in the tab bar`).toContain(label);
    }
  });

  test('every KPI card shows numeric metrics and the aggregate is non-zero', async ({ page }) => {
    const cards = page.locator('.kpi-card-surface');
    await expect(cards.first()).toBeVisible({ timeout: 20000 });
    const cardCount = await cards.count();
    expect(cardCount, 'the dashboard must render KPI cards').toBeGreaterThan(0);

    const values = (await page.locator('.kpi-card-surface .metric-col-value').allTextContents())
      .map(v => v.trim());
    expect(values.length, 'KPI cards must expose metric values').toBeGreaterThan(0);

    for (const v of values) {
      expect(v, `a KPI metric read "${v}" — blank or NaN is a failure, not a pass`).toMatch(/^\d+$/);
    }
    const total = values.reduce((sum, v) => sum + Number(v), 0);
    expect(total, 'the seeded dataset must make at least one KPI non-zero').toBeGreaterThan(0);
  });

  test('the grid renders enriched rows with a coloured SLA capsule', async ({ page }) => {
    const rows = page.locator('tr.clickable-row');
    await expect(rows.first()).toBeVisible({ timeout: 20000 });

    // Enrichment is a batch lookup over Bank / ComplaintCategory / NodalOfficerRecord. If it silently
    // no-ops the cells render empty while the page still looks healthy.
    const firstRowCells = (await rows.first().locator('.cell-text-wrap').allTextContents())
      .map(c => c.trim())
      .filter(c => c !== '');
    expect(firstRowCells.length, 'the first row must carry populated cells').toBeGreaterThan(3);

    // slaColor is interpolated into color-mix(); a CSS *class* name there renders transparent, which
    // is the specific failure mode this asserts against.
    const capsule = page.locator('.sla-capsule').first();
    const colour = await capsule.evaluate(el => getComputedStyle(el).color);
    expect(colour, 'the SLA capsule must resolve to a real colour').toMatch(/^rgba?\(/);
    expect(colour, 'a fully transparent SLA capsule means slaColor was not a colour word')
      .not.toMatch(/rgba\([^)]*,\s*0\)$/);
  });

  test('tab badge counts are filter-independent across all seven tabs', async ({ page }) => {
    await expect(page.locator('.dashboard-tabs-container p-tab').first()).toBeVisible({ timeout: 20000 });
    const baseline = await readTabBadges(page);

    for (const label of TAB_LABELS) {
      await page.locator('p-tab', { hasText: label }).first().click();
      // The tab change debounces at 400ms before it re-queries.
      await page.waitForTimeout(1200);

      // Each tab must resolve to a grid or the explicit empty state — never a spinner that never ends.
      await expect(
        page.locator('tr.clickable-row, .empty-table-state').first(),
        `tab "${label}" must resolve to rows or the empty state`
      ).toBeVisible({ timeout: 15000 });

      const current = await readTabBadges(page);
      expect(current, `badges must not move when tab "${label}" is active`).toEqual(baseline);
    }
  });

  test('selecting a KPI card filters the grid without moving the tab badges', async ({ page }) => {
    await expect(page.locator('.kpi-card-surface').first()).toBeVisible({ timeout: 20000 });
    const baseline = await readTabBadges(page);

    await page.locator('.kpi-card-surface').first().click();
    await page.waitForTimeout(1500);

    await expect(page.locator('.kpi-card-surface.kpi-selected').first()).toBeVisible();
    await expect(page.locator('tr.clickable-row, .empty-table-state').first()).toBeVisible({ timeout: 15000 });

    expect(await readTabBadges(page), 'a KPI filter must not change the tab badges').toEqual(baseline);
  });

  test('read state survives a reload with localStorage cleared', async ({ page }) => {
    const rows = page.locator('tr.clickable-row');
    await expect(rows.first()).toBeVisible({ timeout: 20000 });

    // Read state moved from localStorage to COMPLAINT_READ_STATE precisely so it is per-user and
    // server-held. Clearing localStorage is what distinguishes the two implementations.
    const unread = page.locator('tr.clickable-row.row-unread');
    const unreadBefore = await unread.count();
    expect(unreadBefore, 'the seed leaves some complaints unread for this officer').toBeGreaterThan(0);

    const target = unread.first();
    const complaintNumber = ((await target.locator('.cell-text-wrap').first().textContent()) || '').trim();
    expect(complaintNumber, 'need a complaint number to re-find the row').not.toBe('');

    await target.click();
    await page.waitForURL(/\/cepc\/complaint\//, { timeout: 20000 });

    await page.evaluate(() => window.localStorage.clear());
    await page.goto('/cepc', { waitUntil: 'networkidle' });
    await expect(page.locator('tr.clickable-row').first()).toBeVisible({ timeout: 20000 });

    const reopened = page.locator('tr.clickable-row', { hasText: complaintNumber }).first();
    await expect(
      reopened,
      `${complaintNumber} must still be marked read after localStorage was cleared`
    ).toHaveClass(/row-read/);
  });
});

test.describe('CEPC complaint detail view — tab bar and per-role conditions', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('every tab a DO can open renders, and no request 404s or 500s', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    const failures = trackFailedRequests(page);

    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${DETAIL_COMPLAINT_ID}`);
    await expect(page.locator('.page-tabs .page-tab').first()).toBeVisible({ timeout: 25000 });

    // Forward is deliberately gated to CLOSING_AUTHORITY / INCHARGE, so a DO cannot open it.
    const openableForDo = ['Summary', 'Conciliation', 'Final Decision', 'Contact Entity', 'Email Communication'];

    for (const label of openableForDo) {
      const tab = page.locator('.page-tabs .page-tab', { hasText: label }).first();
      await expect(tab, `tab "${label}" must exist`).toBeVisible();
      await expect(tab, `tab "${label}" must be enabled for a DO`).toBeEnabled();
      await tab.click();
      await page.waitForTimeout(1500);
      await expect(tab, `tab "${label}" must become active when clicked`).toHaveClass(/active/);
    }

    expect(failures, `detail view made failing requests:\n${failures.join('\n')}`).toEqual([]);
  });

  test('Forward is disabled for a DO', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${DETAIL_COMPLAINT_ID}`);
    await expect(page.locator('.page-tabs .page-tab').first()).toBeVisible({ timeout: 25000 });

    await expect(
      page.locator('.page-tabs .page-tab', { hasText: 'Forward' }).first(),
      'Forward is scoped to CLOSING_AUTHORITY / INCHARGE'
    ).toBeDisabled();
  });

  test('Contact Entity and Email Communication are disabled for a Reviewer', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'REVIEWER', `/cepc/complaint/${DETAIL_COMPLAINT_ID}`);
    await expect(page.locator('.page-tabs .page-tab').first()).toBeVisible({ timeout: 25000 });

    for (const label of ['Contact Entity', 'Email Communication']) {
      await expect(
        page.locator('.page-tabs .page-tab', { hasText: label }).first(),
        `"${label}" must be disabled for a Reviewer`
      ).toBeDisabled();
    }
  });
});
