import { test, expect } from './harness';
import { isKeycloakAvailable } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

const API = (process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '');

const CONCILIATOR_HEADERS = {
  'Content-Type': 'application/json',
  'X-User-Id': 'rbio_conciliator_001',
  'X-User-Name': 'rbio_conciliator_001',
  'X-User-Roles': 'RBIO_CONCILIATOR',
};

/**
 * Posts a conciliation action and returns the PARSED BODY.
 *
 * A refused write comes back as HTTP 200 with `{"success": false}`, so asserting on the status code
 * would pass on a rejection. Every assertion below reads the body.
 */
async function postConciliationAction(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  body: Record<string, unknown>
): Promise<{ success: boolean; message: string; data: Record<string, unknown> | null }> {
  const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
    data: body,
    headers: CONCILIATOR_HEADERS,
    failOnStatusCode: false,
  });
  const json = await res.json();
  return { success: json.success === true, message: json.message ?? '', data: json.data ?? null };
}

async function fetchComplaint(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string
): Promise<Record<string, any>> {
  const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}`, {
    headers: CONCILIATOR_HEADERS,
  });
  expect(res.ok()).toBe(true);
  return (await res.json()).data;
}

test.describe('RBIO Conciliation Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('complaint forwarded to conciliation lands in the conciliator queue', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Conciliation Visibility Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      const res = await request.get(`${API}/api/v1/workflow/rbio/tasks?role=RBIO_CONCILIATOR`, {
        headers: CONCILIATOR_HEADERS,
      });
      expect(res.ok()).toBe(true);
      const mine = ((await res.json()).data as any[]).find(t => t.complaintNumber === complaintNumber);
      expect(mine, 'a complaint in conciliation must appear in the conciliator queue').toBeTruthy();
      expect(String(mine.assignedRole)).toBe('RBIO_CONCILIATOR');
      expect(String(mine.status).toLowerCase()).toBe('conciliation');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Conciliator schedules a meeting (meeting recorded against the complaint)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Conciliation Meeting Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      // The old version asserted only that the "Conciliation Success" and "Conciliation Failed"
      // BUTTONS were visible. No meeting was ever scheduled, so a broken scheduling path shipped green.
      const scheduled = await postConciliationAction(request, complaintNumber, {
        action: 'SCHEDULE_MEETING',
        remarks: 'Conciliation hearing scheduled with both parties at RBIO Mumbai.',
        actor: 'rbio_conciliator_001',
        meetingDate: '2026-10-01T10:00:00',
      });
      expect(scheduled.success, `SCHEDULE_MEETING was refused: ${scheduled.message}`).toBe(true);

      // Scheduling a meeting must not itself decide the conciliation.
      expect(String(scheduled.data?.['newStatus']).toLowerCase()).toBe('conciliation');

      // There is no SMS or email capability here, so the only honest evidence a meeting was
      // scheduled is the persisted record.
      const complaint = await fetchComplaint(request, complaintNumber);
      const entry = (complaint.timeline as any[]).find(e => e.action === 'SCHEDULE_MEETING');
      expect(entry, 'scheduling a meeting must leave a timeline record').toBeTruthy();
      expect(entry.remarks).toContain('hearing scheduled');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Conciliation succeeds (status -> conciliated, outcome and compensation persisted)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Conciliation Success Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      const settled = await postConciliationAction(request, complaintNumber, {
        action: 'CONCILIATION_SUCCESS',
        remarks: 'Conciliation successful. Entity agrees to compensate the complainant.',
        actor: 'rbio_conciliator_001',
        compensationAmount: 50000,
        compensationType: 'CONSEQUENTIAL_LOSS',
      });
      expect(settled.success, `CONCILIATION_SUCCESS was refused: ${settled.message}`).toBe(true);

      // Tightened from /conciliated|resolved|success/, which passed on three different outcomes.
      expect(String(settled.data?.['newStatus'])).toBe('conciliated');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('conciliated');
      expect(complaint.resolvedAt, 'a successful conciliation must set resolvedAt').toBeTruthy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test.fixme(
    'Conciliation success persists the agreed compensation amount',
    async () => {
      // FIXME — PRODUCTION GAP. CONCILIATION_SUCCESS accepts a compensationAmount but never stores or
      // validates it: RbioWorkflowService's CONCILIATION_SUCCESS branch (RbioWorkflowService.java:381)
      // sets status, resolvedAt, conciliationOutcome, conciliationDate, workflowStage and closureCause
      // and nothing else — no setCompensationAmount, and no call to
      // RbioCompensationService.validateAward. Verified: posting compensationAmount 99,99,999 (well
      // above the 30L statutory cap) returns `{"success": true, "newStatus": "conciliated"}` and the
      // complaint detail exposes no compensation figure at all.
      //
      // The test name in the old suite claimed 'sets compensation' while no amount was ever entered or
      // verified, which is exactly what hid this. Un-fixme once the amount is persisted and capped.
    }
  );

  test.fixme(
    'Conciliation compensation above the statutory cap is refused',
    async () => {
      // FIXME — PRODUCTION DEFECT: the compensation cap is NOT enforced on the conciliation path.
      // RbioCompensationService.validateAward is called only from the ADJUDICATION_AWARD branch.
      // Verified against the running backend: CONCILIATION_SUCCESS with compensationAmount 99,99,999
      // returns `{"success": true}` and the complaint becomes 'conciliated'. A conciliator can
      // therefore settle for any amount, with no cap and no record of it.
      //
      // The old test filled a remarks box and ended without asserting a warning, so it passed while
      // this hole was open. This must fail or stay fixme until the cap is applied to conciliation.
    }
  );

  test('Conciliation fails -> complaint moves on to adjudication', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Conciliation Failure Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'conciliation');

      const failed = await postConciliationAction(request, complaintNumber, {
        action: 'CONCILIATION_FAILED',
        remarks: 'Conciliation failed. Entity unwilling to provide adequate compensation.',
        actor: 'rbio_conciliator_001',
      });
      expect(failed.success, `CONCILIATION_FAILED was refused: ${failed.message}`).toBe(true);
      expect(String(failed.data?.['newStatus'])).toBe('escalated');
      expect(String(failed.data?.['assignedRole'])).toBe('RBIO_ADJUDICATOR');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('escalated');
      expect(complaint.resolvedAt, 'a failed conciliation must not resolve the complaint').toBeFalsy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test.fixme(
    'Conciliator drives the conciliation panel in the browser',
    async () => {
      // FIXME — no usable RBIO_CONCILIATOR browser login. Verified against the realm:
      // `rbio.conciliator` cannot authenticate ("Account is not fully set up"), and the substitute
      // `cepc.conciliator` holds only CEPC_CONCILIATOR — so showRbioConciliation() is false and the
      // panel never renders (confirmed: `.rbio-conciliation` count 0 while the complaint status is
      // CONCILIATION).
      // Un-fixme once an account holding the RBIO_CONCILIATOR realm role can log in.
    }
  );
});
