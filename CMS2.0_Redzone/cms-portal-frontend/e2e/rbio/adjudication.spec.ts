import { test, expect } from './harness';
import { isKeycloakAvailable } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

const API = (process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '');

const ADJUDICATOR_HEADERS = {
  'Content-Type': 'application/json',
  'X-User-Id': 'rbio_adjudicator_001',
  'X-User-Name': 'rbio_adjudicator_001',
  'X-User-Roles': 'RBIO_ADJUDICATOR',
};

/**
 * Posts an RBIO action and returns the PARSED BODY, not just the status.
 *
 * This API answers a refused write with HTTP 200 and `{"success": false}` — verified against the
 * running backend, where an over-cap award returns 200 with the cap message in the body. Asserting on
 * `response.status()` would therefore pass on a rejection, so every assertion here reads `success`.
 */
async function postAdjudicationAction(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  body: Record<string, unknown>
): Promise<{ success: boolean; message: string; data: Record<string, unknown> | null }> {
  const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
    data: body,
    headers: ADJUDICATOR_HEADERS,
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
    headers: ADJUDICATOR_HEADERS,
  });
  expect(res.ok(), `complaint ${complaintNumber} should be readable`).toBe(true);
  return (await res.json()).data;
}

/**
 * Not `.serial`: each test seeds and cleans up its own complaint, and serial mode meant one failure
 * silently converted the remaining nine into "did not run" — which reads as green in a summary.
 */
test.describe('RBIO Adjudication Workflow', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Adjudicator sees the complaint assigned to the adjudication queue', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Adjudicator Visibility Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      // Asserted against the queue endpoint the UI reads, because no usable RBIO_ADJUDICATOR browser
      // login exists (see the fixme below). This still proves the complaint is routed to the
      // adjudicator role rather than merely that some row rendered.
      const res = await request.get(`${API}/api/v1/workflow/rbio/tasks?role=RBIO_ADJUDICATOR`, {
        headers: ADJUDICATOR_HEADERS,
      });
      expect(res.ok()).toBe(true);
      const tasks = (await res.json()).data as any[];
      const mine = tasks.find(t => t.complaintNumber === complaintNumber);
      expect(mine, 'complaint in adjudication must appear in the adjudicator queue').toBeTruthy();
      expect(String(mine.assignedRole)).toBe('RBIO_ADJUDICATOR');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test.fixme(
    'Adjudicator sees the assigned complaint in the browser task list',
    async () => {
      // FIXME — no usable RBIO_ADJUDICATOR browser login. Verified against the realm:
      // `rbio.adjudicator` cannot authenticate ("Account is not fully set up", no password
      // credential), and the substitute `cepc.adjudicator` holds only CEPC_ADJUDICATOR, so the RBIO
      // adjudicator queue is empty for it and no RBIO adjudication screen renders.
      // Un-fixme once an account holding the RBIO_ADJUDICATOR realm role can log in.
    }
  );

  test('Adjudicator issues 13-1 Notice (notice recorded against the complaint)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E 13-1 Notice Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      // The old version of this test only asserted that an "Award" and a "Reject" BUTTON were
      // visible; no notice was ever issued, so a wholly broken notice path would still report green.
      // The notice is now actually issued and the resulting record asserted.
      const issued = await postAdjudicationAction(request, complaintNumber, {
        action: 'ISSUE_NOTICE_13_1',
        remarks: 'Notice under clause 13(1) issued to the regulated entity.',
        actor: 'rbio_adjudicator_001',
        targetParty: 'Test Bank Ltd',
      });
      expect(issued.success, `ISSUE_NOTICE_13_1 was refused: ${issued.message}`).toBe(true);

      // A notice is only real if it is persisted. There is no SMS/email capability in this system, so
      // the only honest evidence is the stored workflow record: the timeline entry and the stage.
      const complaint = await fetchComplaint(request, complaintNumber);
      const noticeEntry = (complaint.timeline as any[]).find(e => e.action === 'ISSUE_NOTICE_13_1');
      expect(noticeEntry, 'a 13-1 notice must leave a timeline record').toBeTruthy();
      expect(noticeEntry.remarks).toContain('13(1)');
      expect(String(complaint.status).toLowerCase()).toBe('adjudication');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator impleads party (party appears in the impleaded list)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Implead Party Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      // The old version asserted only that a status badge matched /adjudication|escalated/. Nothing
      // was impleaded and no list was checked, so the impleading feature was never exercised at all.
      const partyName = 'S4-Impleaded Guarantor Ltd';
      const impleaded = await postAdjudicationAction(request, complaintNumber, {
        action: 'IMPLEAD_PARTY',
        remarks: `Impleading ${partyName} as a necessary party.`,
        actor: 'rbio_adjudicator_001',
        partyName,
      });
      expect(impleaded.success, `IMPLEAD_PARTY was refused: ${impleaded.message}`).toBe(true);

      const complaint = await fetchComplaint(request, complaintNumber);
      const entry = (complaint.timeline as any[]).find(e => e.action === 'IMPLEAD_PARTY');
      expect(entry, 'impleading must leave a timeline record').toBeTruthy();
      expect(entry.remarks).toContain(partyName);

      // Tightened from /adjudication|escalated/: after impleading, the complaint stays in
      // adjudication. Accepting 'escalated' too would have let a wrong transition pass.
      expect(String(complaint.status).toLowerCase()).toBe('adjudication');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('partyName is mandatory for impleading', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Implead Validation Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const refused = await postAdjudicationAction(request, complaintNumber, {
        action: 'IMPLEAD_PARTY',
        remarks: 'Impleading with no party named.',
        actor: 'rbio_adjudicator_001',
      });
      expect(refused.success, 'impleading with no partyName must be refused').toBe(false);
      expect(refused.message).toContain('partyName');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Adjudicator passes award with valid amount (status -> adjudicated, amount persisted)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const awarded = await postAdjudicationAction(request, complaintNumber, {
        action: 'ADJUDICATION_AWARD',
        remarks: 'Award passed: entity to pay the complainant within 30 days.',
        actor: 'rbio_adjudicator_001',
        awardAmount: '100000',
        compensationType: 'CONSEQUENTIAL_LOSS',
      });
      expect(awarded.success, `a within-cap award was refused: ${awarded.message}`).toBe(true);

      // Tightened from /adjudicated|award|resolved/, which passed on three different outcomes — a
      // complaint that merely became 'resolved' without an award would have satisfied it.
      expect(String(awarded.data?.['newStatus'])).toBe('adjudicated');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('adjudicated');
      expect(complaint.resolvedAt, 'an award must set resolvedAt').toBeTruthy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award exceeding the 30L consequential-loss cap is refused and nothing is persisted', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Cap 30L Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      // The old version filled a remarks box and then asserted only that a Confirm button was
      // visible. No amount was entered and no error was asserted, so this statutory cap — the whole
      // point of the test — was never exercised.
      const refused = await postAdjudicationAction(request, complaintNumber, {
        action: 'ADJUDICATION_AWARD',
        remarks: 'Award of Rs 50,00,000 attempted.',
        actor: 'rbio_adjudicator_001',
        awardAmount: '5000000',
        compensationType: 'CONSEQUENTIAL_LOSS',
      });

      expect(refused.success, 'an award above the 30L cap must be refused').toBe(false);
      expect(refused.message).toMatch(/exceeds the maximum permitted cap/i);
      expect(refused.message).toContain('3000000');

      // A refusal that still mutated the complaint would be worse than no cap at all.
      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('adjudication');
      expect(complaint.resolvedAt, 'a refused award must not resolve the complaint').toBeFalsy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Award exceeding the 3L time/harassment cap is refused and nothing is persisted', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Cap 3L Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const refused = await postAdjudicationAction(request, complaintNumber, {
        action: 'ADJUDICATION_AWARD',
        remarks: 'Award of Rs 4,00,000 for time and harassment attempted.',
        actor: 'rbio_adjudicator_001',
        awardAmount: '400000',
        compensationType: 'TIME_HARASSMENT',
      });

      expect(refused.success, 'an award above the 3L time/harassment cap must be refused').toBe(false);
      expect(refused.message).toMatch(/exceeds the maximum permitted cap/i);
      expect(refused.message).toContain('300000');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('adjudication');
      expect(complaint.resolvedAt).toBeFalsy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('An award just at the 3L time/harassment cap is allowed', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Award Cap Boundary Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const awarded = await postAdjudicationAction(request, complaintNumber, {
        action: 'ADJUDICATION_AWARD',
        remarks: 'Award exactly at the statutory cap.',
        actor: 'rbio_adjudicator_001',
        awardAmount: '300000',
        compensationType: 'TIME_HARASSMENT',
      });
      expect(awarded.success, `an award exactly at the cap must be allowed: ${awarded.message}`).toBe(true);
      expect(String(awarded.data?.['newStatus'])).toBe('adjudicated');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test.fixme(
    'Award passed through the adjudicator UI is capped',
    async () => {
      // FIXME — PRODUCTION DEFECT, not a test gap. The cap is enforced server-side (verified: an
      // `awardAmount` above the cap is refused with `{"success": false}`), but the adjudicator UI
      // cannot reach that check:
      //
      //   1. rbio-adjudication.component.ts:211 sends the amount as `compensationAmount`, while
      //      RbioWorkflowService reads `params.get("awardAmount")` (RbioWorkflowService.java:405).
      //      The amount is therefore never bound, defaults to "0", passes the cap check, and the
      //      award is stored with NO amount. Verified end to end: posting `compensationAmount:
      //      5000000` returns `{"success": true, "newStatus": "adjudicated"}`.
      //   2. The 13-1 notice button sends `ISSUE_13_1_NOTICE` (component ts:121) but the server only
      //      accepts `ISSUE_NOTICE_13_1`; posting the UI's name returns "Unknown action".
      //   3. Impleading sends `impleadPartyName` where the server requires `partyName`, so every
      //      implead from the UI is refused with "partyName is required".
      //   4. The panel is unreachable anyway: showRbioAdjudication() requires the RBIO_ADJUDICATOR
      //      role, and the only adjudicator account that can log in (cepc.adjudicator) holds
      //      CEPC_ADJUDICATOR. rbio.adjudicator cannot log in at all ("Account is not fully set up").
      //
      // Un-fixme once the UI sends awardAmount/partyName/ISSUE_NOTICE_13_1 and an RBIO_ADJUDICATOR
      // login exists. The cap itself is covered above at the API level, which is where it is enforced.
    }
  );

  test('Adjudicator rejects complaint (status -> rejected)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E Adjudicator Reject Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const rejected = await postAdjudicationAction(request, complaintNumber, {
        action: 'ADJUDICATION_REJECT',
        remarks: 'Complaint dismissed. Claim not substantiated by evidence.',
        actor: 'rbio_adjudicator_001',
      });
      expect(rejected.success, `ADJUDICATION_REJECT was refused: ${rejected.message}`).toBe(true);
      expect(String(rejected.data?.['newStatus'])).toBe('rejected');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('rejected');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});

test.describe('RBIO Adjudication — role restriction', () => {
  test('a conciliator cannot pass an adjudication award', async ({ request }) => {
    const result = await createRbioComplaint(request, { subject: 'E2E Award Role Guard Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'adjudication');

      const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
        data: {
          action: 'ADJUDICATION_AWARD',
          remarks: 'Conciliator attempting an award.',
          actor: 'rbio_conciliator_001',
          userRole: 'RBIO_CONCILIATOR',
          awardAmount: '50000',
          compensationType: 'CONSEQUENTIAL_LOSS',
        },
        headers: {
          'Content-Type': 'application/json',
          'X-User-Id': 'rbio_conciliator_001',
          'X-User-Name': 'rbio_conciliator_001',
          'X-User-Roles': 'RBIO_CONCILIATOR',
        },
        failOnStatusCode: false,
      });

      // Either the HTTP guard rejects it, or the service refuses it in the body. Both are correct;
      // silently allowing it is not.
      if (res.ok()) {
        const json = await res.json();
        expect(json.success, 'a conciliator must not be able to pass an award').toBe(false);
      } else {
        expect(res.status()).toBeGreaterThanOrEqual(400);
      }

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('adjudication');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
