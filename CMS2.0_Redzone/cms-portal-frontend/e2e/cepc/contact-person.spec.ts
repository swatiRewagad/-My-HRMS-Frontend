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
 * CEPC Contact Person Workflow Tests
 *
 * A Dealing Officer forwards a complaint to a Contact Person at the regulated entity, the Contact
 * Person responds, and the complaint returns to the Dealing Officer.
 *
 * `.serial` is retained deliberately here — unlike the other suites, these four tests are genuinely
 * sequential stages of ONE complaint's life and cannot be reordered.
 */
test.describe.serial('CEPC Contact Person Workflow', () => {
  let keycloakUp: boolean;
  let complaintNumber: string;
  let selectedContactPerson: string;

  test.beforeAll(async ({ browser, request }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();

    if (keycloakUp) {
      const result = await createTestComplaint(request, {
        subject: 'S4-CEPC Contact Person Flow Test',
        complainantName: 'Contact Person Test Citizen',
        entityName: 'Sample Regulated Bank',
      });
      complaintNumber = result.complaintNumber;
      await advanceToStatus(request, complaintNumber, 'in_progress');
    }
  });

  test.afterAll(async ({ request }) => {
    if (complaintNumber) {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('DO forwards the complaint to a named Contact Person', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    const forwardBtn = page.locator('.action-card:has-text("Forward to Contact Person")');
    await expect(forwardBtn).toBeVisible({ timeout: 5000 });
    await forwardBtn.click();

    const actionForm = page.locator('.action-form');
    await expect(actionForm).toBeVisible();

    const targetSelect = actionForm.locator('select');
    await expect(targetSelect).toBeVisible();

    // The old version read `if (optionCount > 1) { selectOption(1) }` and then submitted regardless,
    // so on an empty dropdown NO contact person was chosen and the test still expected success — a
    // forward to nobody would have passed. The dropdown must be populated and a person must be chosen.
    const options = await targetSelect.locator('option').all();
    expect(
      options.length,
      'the contact-person dropdown must be populated — forwarding to nobody is not a forward'
    ).toBeGreaterThan(1);

    selectedContactPerson = (await options[1].getAttribute('value')) ?? '';
    expect(selectedContactPerson, 'the chosen contact person must have a value').not.toBe('');
    await targetSelect.selectOption(selectedContactPerson);

    await actionForm.locator('textarea')
      .fill('Please provide information regarding the complaint from your branch.');
    await actionForm.locator('.submit-btn').click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });
    await expect(resultMsg).toContainText('completed successfully');

    // A banner is not evidence. The server must show the complaint actually parked with that person.
    const complaint = await fetchComplaint(request, complaintNumber);
    expect(String(complaint.status).toLowerCase()).toBe('forwarded_to_contact');
    expect(String(complaint.assignedTo)).toBe(selectedContactPerson);

    await logout(page);
  });

  test('the forwarded complaint appears in the Contact Person queue', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The old version was `if (await complaintRow.isVisible()) { assert status }` with the comment
    // "If not visible, the complaint might be on a different page" — so the test passed when the
    // complaint was ABSENT from the queue, which is the exact failure it existed to catch.
    //
    // Asserted against the queue endpoint the CP dashboard reads, so paging cannot hide the row.
    const res = await request.get(
      `${API}/api/v1/workflow/cepc/contact-person/tasks?officer=${encodeURIComponent(selectedContactPerson)}`,
      {
        headers: {
          'X-User-Id': selectedContactPerson,
          'X-User-Name': selectedContactPerson,
          'X-User-Roles': 'CEPC_CONTACT_PERSON',
        },
      }
    );
    expect(res.ok(), `the contact-person queue must be readable, got ${res.status()}`).toBe(true);

    const tasks = (await res.json()).data as any[];
    const mine = tasks.find(t => t.complaintNumber === complaintNumber);
    expect(
      mine,
      `the complaint forwarded to ${selectedContactPerson} must appear in that person's queue`
    ).toBeTruthy();
    expect(String(mine.status).toLowerCase()).toBe('forwarded_to_contact');
  });

  test('Contact Person submits a response and it is recorded', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    await loginAsCepcRole(page, 'CP', `/cepc/complaint/${complaintNumber}`);

    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    const responseBtn = page.locator('.action-card:has-text("Submit Response")');
    await expect(responseBtn).toBeVisible({ timeout: 5000 });
    await responseBtn.click();

    const remarks =
      'We have investigated the matter at our branch. The transaction was processed as per guidelines.';
    const remarksField = page.locator('.action-form textarea');
    await expect(remarksField).toBeVisible();
    await remarksField.fill(remarks);

    await page.locator('.action-form .submit-btn').click();

    const resultMsg = page.locator('.result-msg.success');
    await expect(resultMsg).toBeVisible({ timeout: 10000 });

    // The response is only real if it is persisted — there is no send capability to assert against.
    const complaint = await fetchComplaint(request, complaintNumber);
    const entry = (complaint.timeline as any[]).find(e => e.action === 'CONTACT_RESPONSE');
    expect(entry, 'a contact-person response must leave a timeline record').toBeTruthy();
    expect(entry.remarks).toContain('investigated the matter');

    await logout(page);
  });

  test('the complaint returns to the DO after the Contact Person responds', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Asserted on the server first: CONTACT_RESPONSE must hand the complaint back to the DO.
    const complaint = await fetchComplaint(request, complaintNumber);
    expect(String(complaint.status).toLowerCase()).toBe('in_progress');
    // Ownership is asserted separately — it does NOT currently move back. See the fixme below.

    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);
    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    await expect(page.locator('.complaint-header .status-badge')).toBeVisible();
    await expect(page.locator('.action-list')).toBeVisible();

    // The DO must be able to move it forward again.
    const actionLabels = await page.locator('.action-card').allTextContents();
    expect(actionLabels.length, 'the DO must have actions available again').toBeGreaterThan(0);
    expect(
      actionLabels.join('|'),
      'the DO must be able to forward the complaint onward for review'
    ).toContain('Forward to Reviewer');

    await logout(page);
  });

  test.fixme(
    'responding hands ownership of the complaint back to the Dealing Officer',
    async () => {
      // FIXME — PRODUCTION DEFECT (ownership is never returned). CepcWorkflowService's
      // CONTACT_RESPONSE branch (CepcWorkflowService.java:432) sets status='in_progress' and
      // assignedRole='CEPC_DO' but NEVER touches assignedOfficer, so the complaint stays owned by the
      // contact person at the regulated entity after they have finished with it. Verified: after the
      // response, `assignedTo` is still 'contact_person1' while the status reads IN_PROGRESS.
      //
      // Consequences: the complaint does not return to any DO's "Assigned To Me" queue, and an
      // external entity user remains its recorded owner. FORWARD_TO_CONTACT does not stash the
      // previous officer anywhere, so the DO to hand it back to is not currently recoverable.
      // Un-fixme once assignedOfficer is restored to the forwarding DO.
    }
  );

  // WAS test.fixme. The CEPC audit-trail panel was permanently empty: CepcTimelineComponent fetched
  // GET /api/v1/complaints/{n}/timeline, which does not exist (the only timeline route is
  // /api/complaints/{id}/timeline on the legacy controller, keyed by id not number) and swallowed the
  // 404 via `error: () => this.entries.set([])`. Every complaint rendered "No timeline entries yet."
  // indistinguishably from one with no history. It now reads /{n}/history — the canonical feed — through
  // app-workflow-timeline. Asserted with no `if (isVisible())` guard: the previous version of this test
  // was a tautology nested inside two such guards and so could never fail.
  test('the Contact Person response is shown in the complaint audit trail', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // On the server first, so a UI failure cannot be excused as missing data.
    const complaint = await fetchComplaint(request, complaintNumber);
    const entry = (complaint.timeline as any[]).find(e => e.action === 'CONTACT_RESPONSE');
    expect(entry, 'the contact-person response must be on the server audit trail').toBeTruthy();

    await loginAsCepcRole(page, 'DO', `/cepc/complaint/${complaintNumber}`);
    await page.waitForSelector('.cepc-detail .detail-layout', { timeout: 15000 });

    const trail = page.locator('app-cepc-timeline');
    await expect(trail, 'the complaint screen must carry an audit trail').toBeVisible({ timeout: 10000 });

    // Rows must render at all — the assertion the dead endpoint used to make impossible.
    await expect
      .poll(async () => trail.locator('.timeline-entry').count(), {
        message: 'the audit-trail panel must render the rows the server returned',
        timeout: 15000
      })
      .toBeGreaterThan(0);

    const actions = (await trail.locator('.timeline-action').allTextContents()).map(t => t.trim());
    expect(actions, 'the contact-person response must appear in the rendered audit trail')
      .toContain('CONTACT_RESPONSE');

    await logout(page);
  });
});
