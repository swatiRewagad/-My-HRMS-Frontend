import { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  cleanupComplaint,
  advanceToStatus,
  performAction,
  identityHeadersFor,
  DEFAULT_CLOSURE_CLAUSE,
} from '../utils/test-data';

/**
 * CEPC History tab — manual QA cases 11-21.
 *
 * ── Where the History tab actually is, verified before these assertions were written ─────────────
 *
 * The staff complaint screen is staff/task-action.component. Its History panel is opened by the
 * icon-sidebar button `title="History"` (task-action.component.html:786 → `toggleHistoryPanel()`) and
 * renders at lines 753-777 from `complaint()!.timeline`, i.e. from the `timeline` array embedded in
 * GET /api/v1/complaints/{complaintNumber} (ComplaintApiV1Controller.java:438). The panel is
 * `position: fixed; right: 0` (task-action.component.scss:630-632), so "right-hand side" is testable
 * geometrically rather than by taking the CSS on trust.
 *
 * Entry markup: `.history-entry` > `.history-status-badge` (entry.action),
 * `.history-description` (fromStatus → toStatus), `.history-date` (entry.timestamp),
 * `.history-remarks` (entry.remarks). Empty state: `.empty-text` "No history available".
 *
 * ── Two gaps these tests will expose, named here so a failure is not mistaken for flake ──────────
 *
 * 1. NO "hide comments" CONTROL EXISTS. Grepping the whole frontend for hideComments / showComments /
 *    toggleComment / hide-comment returns nothing, and the panel template has no toggle of any kind.
 *    Cases 17-19 assert the behaviour the test cases describe and will fail against the product.
 *
 * 2. The panel embeds `timeline` from the /{complaintNumber} payload, which serialises only
 *    fromStatus / toStatus / action / timestamp / remarks / eventSource and DROPS performedBy
 *    (ComplaintApiV1Controller.java:438-451). The canonical feed
 *    GET /api/v1/complaints/{n}/history (ComplaintCorrespondenceController.java:152) does return
 *    performedBy and performedByRole. Cases 16 and 20 assert on the acting person and role, so they
 *    test both: the server feed AND what the panel renders.
 */

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/** The canonical history feed, which carries performedBy and performedByRole. */
async function readHistory(
  request: APIRequestContext,
  complaintNumber: string
): Promise<Array<Record<string, any>>> {
  const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}/history`, {
    headers: identityHeadersFor('cepc_do_001'),
  });
  expect(response.status(), 'GET /history must serve the complaint audit trail').toBe(200);
  return (await response.json()).data || [];
}

/** Opens the complaint on the staff screen and reveals the History panel. */
async function openHistoryPanel(page: any, complaintNumber: string) {
  await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${complaintNumber}`);
  await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });

  const toggle = page.locator('.icon-sidebar-btn[title="History"]');
  await expect(toggle, 'the History control must exist on the complaint screen').toHaveCount(1);
  await toggle.click();

  const panel = page.locator('.history-panel').first();
  await expect(panel).toBeVisible({ timeout: 10000 });
  return panel;
}

test.describe('CEPC history tab', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  // ── Case 11 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 11 — the History panel is displayed on the RIGHT-hand side of the screen', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-11 right-hand panel' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const panel = await openHistoryPanel(page, created.complaintNumber);

    // Geometry, not CSS on trust: the panel must occupy the right half of the viewport and reach the
    // right edge.
    const box = await panel.boundingBox();
    expect(box, 'the history panel must be laid out').toBeTruthy();
    const viewport = page.viewportSize()!;
    expect(box!.x, 'the panel must start in the right half of the screen')
      .toBeGreaterThanOrEqual(viewport.width / 2);
    expect(box!.x + box!.width, 'the panel must reach the right edge of the screen')
      .toBeGreaterThanOrEqual(viewport.width - 2);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 12 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 12 — the History tab opens when clicked, and closes again', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-12 toggle opens' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    await loginAsCepcRole(page, 'DO', `/staff/cepc/task/${created.complaintNumber}`);
    await expect(page.locator('.task-action-page')).toBeVisible({ timeout: 20000 });

    // Closed to begin with, so the click is what opens it.
    await expect(page.locator('.history-panel')).toHaveCount(0);

    const toggle = page.locator('.icon-sidebar-btn[title="History"]');
    await toggle.click();
    await expect(page.locator('.history-panel').first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.history-panel-header h3')).toContainText('History');

    // And it closes, so the control is a real toggle rather than a one-way reveal.
    await toggle.click();
    await expect(page.locator('.history-panel')).toHaveCount(0);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 13 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 13 — every action is listed with its description and status codes', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-13 descriptions and statuses' });
    await advanceToStatus(request, created.complaintNumber, 'incharge_review');

    // What the server says happened.
    const history = await readHistory(request, created.complaintNumber);
    const serverActions = history.map(e => e.action);
    expect(serverActions).toEqual(
      expect.arrayContaining(['CREATED', 'ACCEPT', 'SUBMIT_FOR_REVIEW', 'APPROVE_REVIEW'])
    );

    const panel = await openHistoryPanel(page, created.complaintNumber);
    const entries = panel.locator('.history-entry');
    await expect(entries.first()).toBeVisible({ timeout: 10000 });

    // Every server row must be represented — the panel must not silently drop actions.
    expect(await entries.count(), 'the panel must render one entry per recorded action')
      .toBe(history.length);

    const badges = (await panel.locator('.history-status-badge').allTextContents()).map(t => t.trim());
    for (const action of ['CREATED', 'ACCEPT', 'SUBMIT_FOR_REVIEW', 'APPROVE_REVIEW']) {
      expect(badges, `${action} must appear in the history tab`).toContain(action);
    }

    // The description carries the status codes of the transition.
    const descriptions = (await panel.locator('.history-description').allTextContents()).map(t => t.trim());
    expect(descriptions.some(d => d.includes('assigned') && d.includes('in_progress')),
      'the transition assigned → in_progress must be described with its status codes').toBe(true);
    expect(descriptions.some(d => d.includes('reviewer_review') && d.includes('incharge_review')),
      'the transition reviewer_review → incharge_review must be described').toBe(true);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 14 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 14 — the modified date is displayed for each history entry', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-14 date displayed' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const panel = await openHistoryPanel(page, created.complaintNumber);
    const dates = panel.locator('.history-date');
    const entryCount = await panel.locator('.history-entry').count();

    expect(await dates.count(), 'every history entry must carry a modified date').toBe(entryCount);
    for (const text of await dates.allTextContents()) {
      expect(text.trim(), 'a history entry must not show a blank date').not.toBe('');
    }

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 15 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 15 — the modified date is formatted DD-MM-YYYY', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-15 date format' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const panel = await openHistoryPanel(page, created.complaintNumber);
    const dates = (await panel.locator('.history-date').allTextContents()).map(t => t.trim());
    expect(dates.length).toBeGreaterThan(0);

    // DD-MM-YYYY. A time component after the date is acceptable; an ISO timestamp
    // (2026-09-24T11:32:26.561091) or any other ordering is not.
    for (const text of dates) {
      expect(
        text,
        `the modified date must be formatted DD-MM-YYYY, but the panel shows "${text}"`
      ).toMatch(/^\d{2}-\d{2}-\d{4}\b/);
    }

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 16 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 16 — the modifying person is identified in the history tab', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-16 modified by whom' });
    await advanceToStatus(request, created.complaintNumber, 'reviewer_review');

    // The server does know who acted.
    const history = await readHistory(request, created.complaintNumber);
    const accept = history.find(e => e.action === 'ACCEPT');
    expect(accept, 'the ACCEPT action must be on the audit trail').toBeTruthy();
    expect(accept!['performedBy'], 'the server must record who performed the action')
      .toBe('cepc_do_001');

    // And the history tab must show it — an audit trail the user cannot attribute is not one.
    const panel = await openHistoryPanel(page, created.complaintNumber);
    await expect(panel.locator('.history-entry').first()).toBeVisible({ timeout: 10000 });
    const panelText = await panel.innerText();
    expect(
      panelText,
      'the history tab must name the person who made each change; the panel text was:\n' + panelText
    ).toContain('cepc_do_001');

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 17 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 17 — a "hide comments" option is displayed in the history tab', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-17 hide comments control' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    const panel = await openHistoryPanel(page, created.complaintNumber);

    const control = panel.locator(
      'button:has-text("comment"), button:has-text("Comment"), ' +
      'label:has-text("comment"), label:has-text("Comment"), ' +
      '[data-testid="hide-comments"], [data-testid="toggle-comments"], ' +
      'input[type="checkbox"]'
    );
    expect(
      await control.count(),
      'the history tab must offer a "hide comments" option'
    ).toBeGreaterThan(0);
    await expect(control.first()).toBeVisible();

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 18 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 18 — comments are hidden by default in the history tab', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-18 comments hidden by default' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');

    // Seed a remark that is unmistakably a comment, so "hidden" is about visibility, not emptiness.
    await performAction(
      request,
      created.complaintNumber,
      'SUBMIT_FOR_REVIEW',
      'cepc_do_001',
      'QA-HIST-18 this comment must be hidden until the user asks for it'
    );

    const panel = await openHistoryPanel(page, created.complaintNumber);
    await expect(panel.locator('.history-entry').first()).toBeVisible({ timeout: 10000 });

    // The remark is in the data (so the assertion is meaningful) but must not be rendered on open.
    const history = await readHistory(request, created.complaintNumber);
    expect(history.some(e => (e.remarks || '').includes('QA-HIST-18')),
      'the comment must exist on the server for this test to mean anything').toBe(true);

    expect(
      await panel.locator('.history-remarks:visible').count(),
      'comments must be hidden when the history tab first opens'
    ).toBe(0);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 19 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 19 — the hide-comments control toggles comments off and on', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-19 toggle comments' });
    await advanceToStatus(request, created.complaintNumber, 'in_progress');
    await performAction(
      request,
      created.complaintNumber,
      'SUBMIT_FOR_REVIEW',
      'cepc_do_001',
      'QA-HIST-19 toggleable comment'
    );

    const panel = await openHistoryPanel(page, created.complaintNumber);
    await expect(panel.locator('.history-entry').first()).toBeVisible({ timeout: 10000 });

    const control = panel.locator(
      'button:has-text("comment"), button:has-text("Comment"), ' +
      'label:has-text("comment"), label:has-text("Comment"), ' +
      '[data-testid="hide-comments"], [data-testid="toggle-comments"], ' +
      'input[type="checkbox"]'
    ).first();
    await expect(control, 'a comments toggle must exist before it can be exercised').toBeVisible();

    const before = await panel.locator('.history-remarks:visible').count();
    await control.click();
    await page.waitForTimeout(400);
    const after = await panel.locator('.history-remarks:visible').count();

    expect(after, 'clicking the control must change whether comments are shown').not.toBe(before);

    // And back again — a toggle, not a one-way switch.
    await control.click();
    await page.waitForTimeout(400);
    expect(await panel.locator('.history-remarks:visible').count()).toBe(before);

    await logout(page);
    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 20 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 20 — every action creates a role-wise audit entry attributing the acting role', async ({ request }) => {
    const created = await createTestComplaint(request, { subject: 'QA-HIST-20 role-wise audit' });

    // Four actions, four different roles.
    const performed: Array<{ action: string; actor: string; role: string }> = [
      { action: 'ACCEPT', actor: 'cepc_do_001', role: 'CEPC_DO' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001', role: 'CEPC_DO' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer_001', role: 'CEPC_REVIEWER' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge_001', role: 'CEPC_INCHARGE' },
    ];
    for (const step of performed) {
      await performAction(
        request,
        created.complaintNumber,
        step.action,
        step.actor,
        `QA-HIST-20 ${step.action} by ${step.role}`
      );
    }

    const history = await readHistory(request, created.complaintNumber);

    // One entry per action performed (plus CREATED), no more and no fewer.
    for (const step of performed) {
      const matches = history.filter(e => e.action === step.action);
      expect(matches.length, `exactly one audit entry must exist for ${step.action}`).toBe(1);
      expect(matches[0]['performedBy'], `${step.action} must name its actor`).toBe(step.actor);

      // "Role-wise" is the point of the case: the entry must attribute the ROLE, not only the username.
      expect(
        matches[0]['performedByRole'],
        `${step.action} must record the acting role ${step.role}, ` +
          `but performedByRole is ${JSON.stringify(matches[0]['performedByRole'])}`
      ).toBe(step.role);
    }

    await cleanupComplaint(request, created.complaintNumber);
  });

  // ── Case 21 ─────────────────────────────────────────────────────────────────────────────────────
  test('case 21 — a closed complaint\'s full lifecycle is reconstructable from its history', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const created = await createTestComplaint(request, { subject: 'QA-HIST-21 full lifecycle' });

    const lifecycle: Array<{ action: string; actor: string; from: string; to: string }> = [
      { action: 'ACCEPT', actor: 'cepc_do_001', from: 'assigned', to: 'in_progress' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001', from: 'in_progress', to: 'reviewer_review' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer_001', from: 'reviewer_review', to: 'incharge_review' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge_001', from: 'incharge_review', to: 'awaiting_closure' },
      { action: 'CLOSE_COMPLAINT', actor: 'cepc_closing_001', from: 'awaiting_closure', to: 'closed' },
    ];
    for (const step of lifecycle) {
      await performAction(
        request,
        created.complaintNumber,
        step.action,
        step.actor,
        `QA-HIST-21 ${step.action}`,
        step.action === 'CLOSE_COMPLAINT' ? { closureClause: DEFAULT_CLOSURE_CLAUSE } : {}
      );
    }

    const history = await readHistory(request, created.complaintNumber);

    // The trail must open with creation and contain every transition, in the order they happened.
    expect(history[0]['action'], 'the trail must begin with the complaint being created').toBe('CREATED');

    const actions = history.map(e => e.action);
    let cursor = actions.indexOf('CREATED');
    for (const step of lifecycle) {
      const position = actions.indexOf(step.action, cursor + 1);
      expect(
        position,
        `${step.action} must appear in the history after ${actions[cursor]}; actual order: ${actions.join(' → ')}`
      ).toBeGreaterThan(cursor);
      cursor = position;

      const entry = history[position];
      expect(entry['fromStatus'], `${step.action} must record its from-status`).toBe(step.from);
      expect(entry['toStatus'], `${step.action} must record its to-status`).toBe(step.to);
      expect(entry['performedBy'], `${step.action} must record who performed it`).toBe(step.actor);
    }

    // The chain has to join up: each transition starts where the previous one ended.
    const chain = history.filter(e => e.fromStatus && e.toStatus && e.fromStatus !== e.toStatus);
    for (let i = 1; i < chain.length; i++) {
      expect(
        chain[i]['fromStatus'],
        'each transition must begin at the status the previous one ended at, but ' +
          `${chain[i]['action']} starts at ${chain[i]['fromStatus']} after ` +
          `${chain[i - 1]['action']} ended at ${chain[i - 1]['toStatus']}`
      ).toBe(chain[i - 1]['toStatus']);
    }

    // Timestamps must be non-decreasing, or the trail cannot be read as a sequence.
    const times = history.map(e => new Date(e.performedAt || e.timestamp).getTime());
    for (let i = 1; i < times.length; i++) {
      expect(times[i], 'history must be in chronological order').toBeGreaterThanOrEqual(times[i - 1]);
    }

    // And the same lifecycle must be visible to the user on the closed complaint.
    const panel = await openHistoryPanel(page, created.complaintNumber);
    const badges = (await panel.locator('.history-status-badge').allTextContents()).map(t => t.trim());
    for (const step of lifecycle) {
      expect(badges, `${step.action} must be visible in the history tab of a closed complaint`)
        .toContain(step.action);
    }

    await logout(page);
  });
});
