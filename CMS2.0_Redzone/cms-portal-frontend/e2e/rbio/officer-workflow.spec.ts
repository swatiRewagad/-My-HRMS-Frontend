import { test, expect } from './harness';
import { loginAsRbioRole, isKeycloakAvailable, logout } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  performRbioAction,
} from '../utils/test-data';

const API = (process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '');

const OFFICER_HEADERS = {
  'Content-Type': 'application/json',
  'X-User-Id': 'rbio_officer_001',
  'X-User-Name': 'rbio_officer_001',
  'X-User-Roles': 'RBIO_OFFICER',
};

async function postOfficerAction(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  body: Record<string, unknown>
): Promise<{ success: boolean; message: string; data: Record<string, unknown> | null }> {
  const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
    data: body,
    headers: OFFICER_HEADERS,
    failOnStatusCode: false,
  });
  const json = await res.json();
  return { success: json.success === true, message: json.message ?? '', data: json.data ?? null };
}

async function fetchComplaint(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string
): Promise<Record<string, any>> {
  const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}`, { headers: OFFICER_HEADERS });
  expect(res.ok()).toBe(true);
  return (await res.json()).data;
}

/**
 * Drives one action through the officer's real task screen and waits for the SERVER to reach
 * `expectedStatus` exactly.
 *
 * `expectedStatus` is one exact value, never an alternation. The previous suite accepted
 * /escalated|conciliation/ and /resolved|in.progress|accepted/, so the wrong transition satisfied the
 * test.
 *
 * The outcome is read from the API rather than from `.result-msg`, because that banner is inside the
 * template's action branch: as soon as the complaint reaches a terminal state the action list empties,
 * the branch swaps to the closed banner, and the banner unmounts. Asserting on it would be timing
 * -dependent, and asserting on the on-screen badge would let a stale view mask a failed write. The
 * status is polled because the action triggers @Async notification work.
 */
async function performActionInUi(
  page: import('@playwright/test').Page,
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  buttonLabel: string,
  remarks: string,
  expectedStatus: string
): Promise<void> {
  const btn = page.locator(`button.action-btn:has-text("${buttonLabel}")`);
  await expect(btn, `the "${buttonLabel}" action must be offered to this role`).toBeVisible({ timeout: 5000 });
  await btn.click();

  const remarksField = page.locator('.remarks-section textarea');
  await expect(remarksField).toBeVisible();
  await remarksField.fill(remarks);

  const confirmBtn = page.locator('.confirm-actions .btn-primary');
  await expect(confirmBtn).toBeEnabled();
  await confirmBtn.click();

  await expect
    .poll(
      async () => String((await fetchComplaint(request, complaintNumber)).status).toLowerCase(),
      { message: `"${buttonLabel}" from the UI must move the complaint to '${expectedStatus}'`, timeout: 15000 }
    )
    .toBe(expectedStatus);
}

test.describe('RBIO Officer Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Officer resolves a complaint through the UI (status -> resolved)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Resolve Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });
      await expect(page.locator('.status-badge').first()).toContainText(/ASSIGNED/i);

      await performActionInUi(
        page,
        request,
        complaintNumber,
        'Resolve',
        'Complaint resolved satisfactorily. Entity has provided adequate remedy.',
        'resolved'
      );

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(complaint.resolvedAt, 'resolving must set resolvedAt').toBeTruthy();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer rejects a non-maintainable complaint through the UI (status -> rejected)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Reject Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      await performActionInUi(
        page,
        request,
        complaintNumber,
        'Reject',
        'Complaint is not maintainable under the RBI Ombudsman Scheme. Matter falls outside jurisdiction.',
        'rejected'
      );

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer escalates to supervisor through the UI (status -> escalated, role -> supervisor)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Escalate Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      await performActionInUi(
        page,
        request,
        complaintNumber,
        'Escalate',
        'Complaint requires supervisor review. Entity is non-cooperative.',
        'escalated'
      );

      // Escalation is only meaningful if the complaint actually acquires an owner downstream.
      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.assignedTeam ?? ''), 'escalation must leave the complaint owned').not.toBe('');

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer requests additional info (status -> info_requested)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Info Request Test' });
    const complaintNumber = result.complaintNumber;

    try {
      // The old version of this test was named "requests additional info" but CLICKED ESCALATE and
      // never asserted info_requested — it tested a different action than its own name claimed and
      // would have passed no matter what REQUEST_INFO did.
      await postOfficerAction(request, complaintNumber, {
        action: 'ACCEPT',
        remarks: 'Accepting for examination.',
        actor: 'rbio_officer_001',
      });

      const requested = await postOfficerAction(request, complaintNumber, {
        action: 'REQUEST_INFO',
        remarks: 'Please provide account statements for the disputed period.',
        actor: 'rbio_officer_001',
      });
      expect(requested.success, `REQUEST_INFO was refused: ${requested.message}`).toBe(true);

      const complaint = await fetchComplaint(request, complaintNumber);
      const status = String(complaint.status).toLowerCase();
      expect(
        status,
        `REQUEST_INFO must park the complaint awaiting information, got '${status}'`
      ).toBe('info_requested');

      const entry = (complaint.timeline as any[]).find(e => e.action === 'REQUEST_INFO');
      expect(entry, 'requesting info must leave a timeline record').toBeTruthy();
      expect(entry.remarks).toContain('account statements');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer forwards to conciliation (status -> conciliation, role -> conciliator)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Forward Conciliation Test' });
    const complaintNumber = result.complaintNumber;

    try {
      // The old version also clicked ESCALATE and then accepted /escalated|conciliation/, so an
      // escalation to a supervisor satisfied a test named "forwards to conciliation". The real
      // FORWARD_TO_CONCILIATION action is exercised here, and only 'conciliation' is accepted.
      await postOfficerAction(request, complaintNumber, {
        action: 'ACCEPT',
        remarks: 'Accepting for examination.',
        actor: 'rbio_officer_001',
      });

      const forwarded = await postOfficerAction(request, complaintNumber, {
        action: 'FORWARD_TO_CONCILIATION',
        remarks: 'Parties amenable to conciliation. Forwarding to the conciliator.',
        actor: 'rbio_officer_001',
      });
      expect(forwarded.success, `FORWARD_TO_CONCILIATION was refused: ${forwarded.message}`).toBe(true);
      expect(String(forwarded.data?.['newStatus'])).toBe('conciliation');
      expect(String(forwarded.data?.['assignedRole'])).toBe('RBIO_CONCILIATOR');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('conciliation');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Officer issues an advisory (advisory recorded against the complaint)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Advisory Test' });
    const complaintNumber = result.complaintNumber;

    try {
      // The old version wrapped the advisory form in `if (await advisorySubject.isVisible())`, then
      // fell through to clicking Resolve. No advisory was ever asserted issued: the test passed
      // whether the advisory feature worked, silently failed, or was absent.
      await postOfficerAction(request, complaintNumber, {
        action: 'ACCEPT',
        remarks: 'Accepting for examination.',
        actor: 'rbio_officer_001',
      });

      const issued = await postOfficerAction(request, complaintNumber, {
        action: 'ISSUE_ADVISORY',
        remarks: 'Advisory issued to the regulated entity regarding customer service practices.',
        actor: 'rbio_officer_001',
      });
      expect(issued.success, `ISSUE_ADVISORY was refused: ${issued.message}`).toBe(true);

      // An advisory is only real if it is persisted — there is no send capability to assert against.
      const complaint = await fetchComplaint(request, complaintNumber);
      const entry = (complaint.timeline as any[]).find(e => e.action === 'ISSUE_ADVISORY');
      expect(entry, 'issuing an advisory must leave a timeline record').toBeTruthy();
      expect(entry.remarks).toContain('customer service practices');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('timeline records every officer action in order', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Officer Timeline Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await performRbioAction(request, complaintNumber, 'ACCEPT', 'rbio_officer_001', 'Accepting complaint');
      await performRbioAction(request, complaintNumber, 'ESCALATE', 'rbio_officer_001', 'Escalating to supervisor');

      // Asserted on the server record first: this is the audit trail, and it must contain both
      // actions and the creation event regardless of what any screen chooses to render.
      const complaint = await fetchComplaint(request, complaintNumber);
      const actions = (complaint.timeline as any[]).map(e => e.action);
      expect(actions).toContain('CREATED');
      expect(actions).toContain('ACCEPT');
      expect(actions).toContain('ESCALATE');

      const accept = (complaint.timeline as any[]).find(e => e.action === 'ACCEPT');
      const escalate = (complaint.timeline as any[]).find(e => e.action === 'ESCALATE');
      expect(accept.fromStatus).toBe('assigned');
      expect(accept.toStatus).toBe('in_progress');
      expect(escalate.fromStatus).toBe('in_progress');
      expect(escalate.toStatus).toBe('escalated');

      // Then that the officer's own screen actually shows it. The old test wrapped this in
      // `if (timelineHeader.isVisible())`, so a missing timeline section skipped the assertion.
      await loginAsRbioRole(page, 'RBIO_OFFICER', `/staff/rbio/task/${complaintNumber}`);
      await page.waitForSelector('.task-action-page', { timeout: 15000 });

      const timelineSection = page.locator('.form-section:has(.section-header:has-text("Timeline"))');
      await expect(timelineSection, 'the task screen must show a Timeline section').toBeVisible({ timeout: 10000 });
      await timelineSection.locator('.section-header').click();

      const renderedActions = timelineSection.locator('.timeline-action');
      await expect(renderedActions.filter({ hasText: 'ACCEPT' }).first()).toBeVisible({ timeout: 5000 });
      await expect(renderedActions.filter({ hasText: 'ESCALATE' }).first()).toBeVisible();

      await logout(page);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
