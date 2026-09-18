import { test, expect } from '../rbio/harness';
import { loginAsCepcRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, cleanupComplaint, advanceToStatus } from '../utils/test-data';

const API = (process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '');

async function fetchComplaint(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string
): Promise<Record<string, any>> {
  const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}`, {
    headers: { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
  });
  expect(res.ok()).toBe(true);
  return (await res.json()).data;
}

/**
 * CEPC Reopen & Reassign Tests
 *
 * `.serial` is retained: these are sequential stages of one complaint's life.
 */
test.describe.serial('CEPC Reopen Complaint', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const result = await createTestComplaint(request, {
        subject: 'S4-CEPC Reopen Test Complaint',
        complainantName: 'Reopen Test Citizen',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'closed');
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('a closed complaint shows the closed banner to the Closing Authority', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const before = await fetchComplaint(request, complaintNumber);
    expect(String(before.status).toLowerCase(), 'the fixture must actually be closed').toBe('closed');

    await loginAsCepcRole(page, 'CA', `/cepc/complaint/${complaintNumber}`);
    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    await expect(page.locator('.closed-banner')).toBeVisible();
    // Whether the Reopen action is offered is asserted separately — see the fixme below.

    await logout(page);
  });

  test.fixme(
    'closed complaint offers the Reopen action to the Closing Authority',
    async () => {
      // FIXME — PRODUCTION DEFECT (unreachable branch): a closed CEPC complaint can never be reopened
      // from the UI. `availableActions` short-circuits with `if (this.isTerminalState()) return []`
      // (cepc-complaint-detail.component.ts:87) BEFORE reaching the CEPC_CLOSING_AUTHORITY branch that
      // pushes REOPEN for `status === 'closed' || status === 'resolved'` (line 134). Since 'closed' is
      // itself a terminal state, that branch is dead code. The identical CEPC_ADMIN reopen (line 145)
      // is dead for the same reason.
      //
      // Verified in the browser as CEPC_CLOSING_AUTHORITY on a closed complaint: the closed banner
      // renders, `.action-card` count is 0 and `.action-list` is absent. The REOPEN action itself works
      // server-side (verified: it returns `{"success": true, "newStatus": "in_progress"}`), so this is
      // purely the guard order in the component.
      //
      // Un-fixme once the terminal-state check runs after the reopen branches.
    }
  );

  test('REOPEN moves a closed complaint back to in_progress', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Asserted at the API, because the UI cannot reach this action at all (fixme above). The old test
    // ended with `expect(resultText).toBeTruthy()` on text it had already asserted visible — a
    // tautology; the server state is what matters.
    const res = await request.post(`${API}/api/v1/workflow/cepc/action/${complaintNumber}`, {
      data: { action: 'REOPEN', remarks: 'Complainant submitted additional evidence.', actor: 'cepc_closing1' },
      headers: {
        'Content-Type': 'application/json',
        'X-User-Id': 'cepc_closing1',
        'X-User-Roles': 'CEPC_CLOSING_AUTHORITY',
      },
      failOnStatusCode: false,
    });
    const body = await res.json();
    expect(body.success, `REOPEN was refused: ${body.message}`).toBe(true);
    expect(String(body.data?.newStatus)).toBe('in_progress');

    const after = await fetchComplaint(request, complaintNumber);
    expect(String(after.status).toLowerCase()).toBe('in_progress');
    expect(after.closedAt, 'a reopened complaint must not remain closed').toBeFalsy();

    const entry = (after.timeline as any[]).find(e => e.action === 'REOPEN');
    expect(entry, 'reopening must leave a timeline record').toBeTruthy();
    expect(entry.fromStatus).toBe('closed');
  });

  test('reopened complaint is no longer terminal and is actionable by the DO', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    await expect(page.locator('.closed-banner')).toHaveCount(0);
    await expect(page.locator('.action-list')).toBeVisible();

    // Naming the expected action makes this meaningful: any non-zero count would otherwise pass even
    // if the only offered action were unrelated.
    const actionLabels = await page.locator('.action-card').allTextContents();
    expect(actionLabels.length).toBeGreaterThan(0);
    expect(
      actionLabels.join('|'),
      'a reopened complaint must be forwardable for review again'
    ).toContain('Forward to Reviewer');

    await logout(page);
  });
});

test.describe.serial('CEPC Reassign Complaint', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;
  let originalOwner: string;
  let reassignTarget: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const result = await createTestComplaint(request, {
        subject: 'S4-CEPC Reassign Test Complaint',
        complainantName: 'Reassign Test Citizen',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'incharge_review');
      originalOwner = String((await fetchComplaint(request, complaintNumber)).assignedTo ?? '');
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('In-Charge sees the Reassign option', async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'INCHARGE', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });
    await expect(page.locator('.action-card:has-text("Reassign to Another DO")')).toBeVisible({ timeout: 5000 });

    await logout(page);
  });

  test('In-Charge reassigns to a different DO and ownership actually moves', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'INCHARGE', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    const reassignBtn = page.locator('.action-card:has-text("Reassign to Another DO")');
    await expect(reassignBtn).toBeVisible({ timeout: 5000 });
    await reassignBtn.click();

    const actionForm = page.locator('.action-form');
    await expect(actionForm).toBeVisible();

    const targetSelect = actionForm.locator('select');
    await expect(targetSelect).toBeVisible();

    // The old version read `if (optionCount > 1) { selectOption(1) }` and submitted regardless, so a
    // reassignment to nobody still expected success. A target must exist and be chosen.
    const options = await targetSelect.locator('option').all();
    expect(
      options.length,
      'the DO dropdown must be populated — reassigning to nobody is not a reassignment'
    ).toBeGreaterThan(1);

    reassignTarget = (await options[1].getAttribute('value')) ?? '';
    expect(reassignTarget).not.toBe('');
    await targetSelect.selectOption(reassignTarget);

    await actionForm.locator('textarea').fill('Reassigning to another DO with relevant domain expertise.');
    await actionForm.locator('.submit-btn').click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });
    await expect(resultMsg).toContainText('completed successfully');

    // Replaces `expect(resultText).toBeTruthy()` on text already asserted visible — a tautology. The
    // reassignment is only real if the owner changed on the server.
    const after = await fetchComplaint(request, complaintNumber);
    expect(String(after.assignedTo), 'the complaint must now be owned by the chosen DO').toBe(reassignTarget);
    expect(String(after.assignedTo), 'a reassignment must change the owner').not.toBe(originalOwner);

    await logout(page);
  });

  test('the reassigned complaint is in the new DO personal queue', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version was `if (table.isVisible()) { if (row.isVisible()) { expect(row).toBeVisible() } }`
    // — a tautology inside two guards, with the comment "It may or may not be assigned to THIS
    // particular DO". It could not fail. Queried by `officer` so paging cannot hide the row and
    // absence is a failure.
    const res = await request.get(
      `${API}/api/v1/workflow/cepc/tasks?officer=${encodeURIComponent(reassignTarget)}`,
      {
        headers: {
          'X-User-Id': reassignTarget,
          'X-User-Name': reassignTarget,
          'X-User-Roles': 'CEPC_DO',
        },
      }
    );
    expect(res.ok()).toBe(true);

    const tasks = (await res.json()).data as any[];
    const mine = tasks.find(t => t.complaintNumber === complaintNumber);
    expect(mine, `the complaint reassigned to ${reassignTarget} must appear in that DO's queue`).toBeTruthy();
    expect(String(mine.assignedOfficer)).toBe(reassignTarget);
  });

  test.fixme(
    'the reassigned complaint appears in the CEPC_DO role queue',
    async () => {
      // FIXME — PRODUCTION DEFECT (role is not moved with the owner). CepcWorkflowService's REASSIGN
      // branch (CepcWorkflowService.java:403) sets assignedOfficer from `targetUser` but only sets
      // assignedRole when a `targetRole` param is supplied — and the In-Charge "Reassign to Another DO"
      // action sends only targetUser (cepc-complaint-detail.component.ts:121, targetType 'user'). So
      // after a reassignment the complaint is owned by a DO while still carrying
      // assignedRole='CEPC_INCHARGE'.
      //
      // That matters because GET /api/v1/workflow/cepc/tasks?role=... filters on assigned_role
      // (WorkflowController.java:613). Verified: after REASSIGN to cepc_do1 the complaint has
      // assignedTo=cepc_do1 and status=ASSIGNED, yet it is absent from all 333 rows of the CEPC_DO role
      // queue. It is only reachable via the `officer` query (covered above) or "All Complaints".
      //
      // Un-fixme once REASSIGN sets assignedRole to match the target user's role.
    }
  );
});
