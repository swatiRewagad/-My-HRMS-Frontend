import { test, expect } from './harness';
import { isKeycloakAvailable } from '../utils/auth';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  advanceRbioToStatus,
} from '../utils/test-data';

const API = (process.env['API_BASE_URL'] || 'http://localhost:8082').replace(/\/+$/, '');

const ADMIN_HEADERS = {
  'Content-Type': 'application/json',
  'X-User-Id': 'admin_001',
  'X-User-Name': 'admin_001',
  'X-User-Roles': 'RBIO_ADMIN',
};

async function postAdminAction(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  body: Record<string, unknown>
): Promise<{ success: boolean; message: string; data: Record<string, unknown> | null }> {
  const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
    data: body,
    headers: ADMIN_HEADERS,
    failOnStatusCode: false,
  });
  const json = await res.json();
  return { success: json.success === true, message: json.message ?? '', data: json.data ?? null };
}

async function fetchComplaint(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string
): Promise<Record<string, any>> {
  const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}`, { headers: ADMIN_HEADERS });
  expect(res.ok()).toBe(true);
  return (await res.json()).data;
}

test.describe('RBIO Admin — Reopen & Reassign', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test('Admin reopens a closed complaint (status leaves the closed state)', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, {
      subject: 'E2E RBIO Admin Reopen Test',
      complainantName: 'Reopen Test Citizen',
    });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');
      const before = await fetchComplaint(request, complaintNumber);
      expect(String(before.status).toLowerCase()).toBe('closed');

      const reopened = await postAdminAction(request, complaintNumber, {
        action: 'REOPEN',
        remarks: 'Reopening complaint based on new evidence submitted by the complainant.',
        actor: 'admin_001',
      });
      expect(reopened.success, `REOPEN was refused: ${reopened.message}`).toBe(true);

      const after = await fetchComplaint(request, complaintNumber);
      expect(String(after.status).toLowerCase()).not.toBe('closed');
      const entry = (after.timeline as any[]).find(e => e.action === 'REOPEN');
      expect(entry, 'reopening must leave a timeline record').toBeTruthy();
      expect(entry.fromStatus).toBe('closed');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Reopened complaint returns to in_progress and is actionable again', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO Reopen Status Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'closed');
      const reopened = await postAdminAction(request, complaintNumber, {
        action: 'REOPEN',
        remarks: 'Reopening for verification.',
        actor: 'admin_001',
      });
      expect(reopened.success, `REOPEN was refused: ${reopened.message}`).toBe(true);

      // Tightened from /in.progress|assigned|reopened|active/, which accepted four different
      // statuses — including 'assigned', which would mean the complaint had lost its examination
      // history. Only in_progress is correct.
      expect(String(reopened.data?.['newStatus'])).toBe('in_progress');

      const complaint = await fetchComplaint(request, complaintNumber);
      expect(String(complaint.status).toLowerCase()).toBe('in_progress');
      expect(complaint.closedAt, 'a reopened complaint must no longer be closed').toBeFalsy();

      // Being out of the closed state is only useful if actions are available again.
      const actionsRes = await request.get(
        `${API}/api/v1/workflow/rbio/available-actions/${complaintNumber}?userRole=RBIO_OFFICER`,
        { headers: ADMIN_HEADERS }
      );
      expect(actionsRes.ok()).toBe(true);
      const actions = (await actionsRes.json()).data?.availableActions as string[];
      expect(Array.isArray(actions), 'available-actions must return a list').toBe(true);
      expect(actions.length, 'a reopened complaint must offer actions again').toBeGreaterThan(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('Admin reassigns a complaint to a different officer', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const result = await createRbioComplaint(request, { subject: 'E2E RBIO Admin Reassign Test' });
    const complaintNumber = result.complaintNumber;

    try {
      await advanceRbioToStatus(request, complaintNumber, 'in_progress');
      const before = await fetchComplaint(request, complaintNumber);
      const originalOwner = String(before.assignedTo ?? '');

      const target = 'rbio.supervisor';
      const reassigned = await postAdminAction(request, complaintNumber, {
        action: 'REASSIGN',
        remarks: 'Admin reassigning to an officer with the relevant domain expertise.',
        actor: 'admin_001',
        targetUser: target,
      });
      expect(reassigned.success, `REASSIGN was refused: ${reassigned.message}`).toBe(true);

      // A reassignment that does not change the owner is not a reassignment.
      const after = await fetchComplaint(request, complaintNumber);
      expect(String(after.assignedTo)).toBe(target);
      expect(String(after.assignedTo)).not.toBe(originalOwner);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test.fixme(
    'Admin performs bulk assign from the task list',
    async () => {
      // FIXME — THE FEATURE DOES NOT EXIST. There is no bulk-assign control anywhere in the RBIO task
      // list: rbio-tasks.component.html has row checkboxes and a `selectedIds` signal, but no
      // "Bulk Assign" / "Assign Selected" button and no handler (verified — no match for
      // /Bulk Assign|Assign Selected/ in the component, and the button count is 0 in the browser).
      // BULK_ASSIGN is listed among RBIO_ADMIN's allowed actions server-side
      // (RbioWorkflowService.java:53) but no case handles it in performAction, so it falls through to
      // "Unknown action".
      //
      // The old test was three nested `if (isVisible)` blocks whose else branches only pushed
      // annotations, so it passed whether or not the feature existed — and its inner assertion
      // (`expect(dialog).toBeVisible()` immediately after checking the trigger was visible) was a
      // tautology that could never fail. Un-fixme when bulk assign is actually built.
    }
  );

  test.fixme(
    'Admin drives reopen and reassign through the browser UI',
    async () => {
      // FIXME — no usable RBIO_ADMIN browser login. The RBIO_ADMIN key maps to `cms.admin`, whose only
      // realm role is `ADMIN` (verified from its token: ["default-roles-cms", "offline_access",
      // "uma_authorization", "ADMIN"]) — not RBIO_ADMIN. The task screen therefore offers it no
      // actions at all (verified: 0 `.action-btn`, 0 `.action-card`, and the page renders the closed
      // banner with "No actions available for your role"), so the Reopen and Reassign cards the old
      // test clicked never appear.
      //
      // Both behaviours ARE covered above against the API, which is where the transition is enforced.
      // Un-fixme once an account holding the RBIO_ADMIN realm role can log in.
    }
  );
});
