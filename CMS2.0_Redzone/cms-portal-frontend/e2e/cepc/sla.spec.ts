import { test, expect, ageComplaintPastSla } from '../rbio/harness';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus } from '../utils/test-data';

/**
 * CEPC SLA (Service Level Agreement) Tests
 *
 * Validates that the UI correctly displays SLA status indicators:
 * - Within SLA: "N days remaining"
 * - Breached: "N days overdue" plus the red colour class
 *
 * The previous version of this file used `if (count > 0) { assert } else { annotate }` in three
 * places, one of them carrying the comment "No breached complaints in current data — test passes (no
 * assertion to fail)". An annotation does not affect exit status, so those tests could not fail. The
 * preconditions are seeded here instead.
 */
test.describe('CEPC SLA Indicators', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('new complaint shows a concrete remaining-days SLA figure', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createTestComplaint(request, { subject: 'E2E SLA Due Date Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceToStatus(request, complaintNumber, 'in_progress');
      await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);
      await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

      const slaIndicator = page.locator('.sla-indicator');
      await expect(slaIndicator).toBeVisible();

      // `expect(text?.trim()).toBeTruthy()` passed on "NaN", "undefined" and "—" — every failure mode
      // the indicator has. A brand-new complaint gets a 30-day SLA (slaDueDate = createdAt + 30 days),
      // so the exact expected content is asserted instead.
      const text = (await page.locator('.sla-indicator .days-text').textContent())?.trim() ?? '';
      expect(text, `SLA indicator read "${text}"`).toMatch(/^\d+ days? remaining$/);
      expect(text).not.toMatch(/NaN|undefined|Invalid/i);

      const days = Number(/^(\d+)/.exec(text)?.[1]);
      expect(days, `a new complaint should be near its 30-day SLA, got ${days}`).toBeGreaterThan(25);
      expect(days).toBeLessThanOrEqual(31);

      // Within SLA must not be coloured as a breach.
      await expect(slaIndicator).not.toHaveClass(/red/);

      await logout(page);
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('dashboard SLA cells show real figures, never a placeholder', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version read `if (count > 0) { expect(text?.trim()).toBeTruthy() }` — it skipped
    // entirely on an empty grid, and even when it ran, "NaN"/"—" satisfied it. A complaint is seeded
    // so there is always at least one row, and the content is checked properly.
    const result = await createTestComplaint(request, { subject: 'S4-CEPC SLA Dashboard Cell Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceToStatus(request, complaintNumber, 'in_progress');
      await loginAsCepcRole(page, 'DO', '/cepc/dashboard');
      await page.waitForSelector('.data-grid, .empty-state', { timeout: 15000 });

      const indicators = page.locator('.data-grid tbody .sla-indicator .days-text');
      await expect
        .poll(async () => indicators.count(), { message: 'the dashboard must list SLA cells to assert on' })
        .toBeGreaterThan(0);

      const texts = await indicators.allTextContents();
      for (const raw of texts) {
        const t = raw.trim();
        expect(t, 'an SLA cell must never be blank').not.toBe('');
        expect(t, `SLA cell read "${t}"`).not.toMatch(/NaN|undefined|Invalid/i);
        expect(t, `SLA cell read "${t}"`).toMatch(/^(\d+ days? (remaining|overdue)|Due today|Completed|—)$/);
      }

      await logout(page);
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('breached complaint shows the red overdue indicator', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version carried the comment "No breached complaints in current data — test passes (no
    // assertion to fail)". The breach is now seeded so the assertion always runs.
    const result = await createTestComplaint(request, { subject: 'S4-CEPC SLA Breach Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceToStatus(request, complaintNumber, 'in_progress');

      // Aged 45 days against the 30-day CEPC SLA. `created_at` is moved because
      // ComplaintApiV1Controller:401 derives slaDueDate as `createdAt.plusDays(30)` and ignores the
      // stored sla_deadline entirely.
      const seeded = ageComplaintPastSla(complaintNumber, 45);
      expect(
        seeded,
        'could not seed an SLA breach (MySQL client unavailable) — this test cannot honestly pass without one'
      ).toBe(true);

      await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);
      await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

      const slaIndicator = page.locator('.sla-indicator');
      await expect(slaIndicator).toBeVisible();

      const text = (await page.locator('.sla-indicator .days-text').textContent())?.trim() ?? '';
      expect(text, `a complaint 45 days past a 30-day SLA reads "${text}"`).toMatch(/^\d+ days? overdue$/);

      const daysOverdue = Number(/^(\d+)/.exec(text)?.[1]);
      expect(daysOverdue, 'roughly 15 days overdue was expected').toBeGreaterThanOrEqual(10);

      // colorClass() returns 'red' once daysRemaining < 0 (cepc-sla-indicator.component.ts:42).
      await expect(slaIndicator, 'a breached SLA must be flagged red').toHaveClass(/red/);

      await logout(page);
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('SLA dashboard route exists and reports compliance stats', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version was `if (url still contains the route) { assert } else { annotate }`, so a
    // MISSING ROUTE passed. The route does exist (app.routes.ts:172 -> CepcSlaDashboardComponent), so
    // its absence is now a failure.
    const result = await createTestComplaint(request, { subject: 'S4-CEPC SLA Dashboard Stats Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceToStatus(request, complaintNumber, 'in_progress');
      const seeded = ageComplaintPastSla(complaintNumber, 45);
      expect(seeded, 'could not seed an SLA breach (MySQL client unavailable)').toBe(true);

      await loginAsCepcRole(page, 'DO', '/cepc/sla-dashboard');

      expect(page.url(), 'the /cepc/sla-dashboard route must exist and not redirect away')
        .toContain('/cepc/sla-dashboard');
      await expect(page.locator('h2:has-text("CEPC - SLA Compliance Dashboard")')).toBeVisible({ timeout: 15000 });

      // The four compliance buckets must be present and must carry numbers, not blanks.
      const labels = (await page.locator('.stat-label').allTextContents()).map(l => l.trim());
      expect(labels).toEqual(
        expect.arrayContaining(['Total Active', 'On Track', 'At Risk (<2 days)', 'Breached'])
      );

      const values = await page.locator('.stat-value').allTextContents();
      for (const v of values) {
        expect(v.trim(), `a compliance stat read "${v}"`).toMatch(/^\d+$/);
      }

      // With a breach seeded, the dashboard must count at least one — a dashboard that always
      // reports zero breaches is worse than none.
      const breached = page.locator('.stat-card.breached .stat-value');
      await expect
        .poll(async () => Number((await breached.textContent())?.trim() ?? '0'), {
          message: 'a seeded breach must be reflected in the Breached count',
          timeout: 10000,
        })
        .toBeGreaterThan(0);

      await logout(page);
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });
});
