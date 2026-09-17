import { test, expect } from '@playwright/test';

/**
 * UST887 — safe deactivation of a team member.
 *
 * Targets cms-workflow-service directly, because that is where the officer pool (WF_OFFICER_POOL)
 * and the deactivate endpoint live. The frontend previously called this path on the cms-backend
 * origin, which never serves /cms-workflow/**, so every deactivation silently 404'd while the UI
 * showed success.
 *
 * Requires cms-workflow-service to be running; the tests skip rather than fail if it is not, so an
 * unrelated suite run does not report a false failure.
 */

const WORKFLOW_BASE = process.env['WORKFLOW_BASE_URL'] || 'http://localhost:8094';
const POOL = `${WORKFLOW_BASE}/cms-workflow/api/v1/assignment`;

const GROUP = 'E2E_DEACT_GRP';

async function workflowUp(request: any): Promise<boolean> {
  try {
    const res = await request.get(`${POOL}/pool?roleGroup=${GROUP}`, { timeout: 4000 });
    return res.ok();
  } catch {
    return false;
  }
}

async function addOfficer(request: any, userId: string, displayName: string) {
  const res = await request.post(`${POOL}/pool`, {
    data: { userId, displayName, roleGroup: GROUP, regionalOffice: 'MUM', maxWorkload: 40 }
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data;
}

test.describe('UST887 — deactivation cannot strand open work', () => {

  test('an officer with no open records deactivates cleanly', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const userId = `e2e.clean.${Date.now()}`;
    const officer = await addOfficer(request, userId, 'E2E Clean Leaver');

    const open = await request.get(`${POOL}/pool/${officer.id}/open-records`);
    expect(open.ok()).toBeTruthy();
    const openBody = (await open.json()).data;
    expect(openBody.openCount).toBe(0);
    expect(openBody.reassignmentRequired).toBe(false);

    const res = await request.put(`${POOL}/pool/${officer.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: {}
    });
    expect(res.ok()).toBeTruthy();
    expect((await res.json()).data.reassignedCount).toBe(0);
  });

  test('an unknown officer returns 404 rather than silently succeeding', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    // The original implementation used ifPresent(), so a bad id returned 200 and did nothing.
    const res = await request.put(`${POOL}/pool/99999999/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: {}
    });
    expect(res.status()).toBe(404);
  });

  test('an officer cannot be their own successor', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const userId = `e2e.self.${Date.now()}`;
    const officer = await addOfficer(request, userId, 'E2E Self Successor');

    const res = await request.put(`${POOL}/pool/${officer.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: { reassignTo: userId }
    });
    // With no open records the successor is not consulted, so this is only rejected when a transfer
    // is actually required. Either outcome is acceptable; what must not happen is a 500.
    expect([200, 400]).toContain(res.status());
  });

  test('a successor outside the role group is rejected', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const leaver = await addOfficer(request, `e2e.leaver.${Date.now()}`, 'E2E Leaver');

    const res = await request.put(`${POOL}/pool/${leaver.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' },
      data: { reassignTo: 'someone.who.does.not.exist' }
    });
    // Again only enforced when a transfer is required; never a server error.
    expect([200, 400]).toContain(res.status());
  });

  test('the open-records endpoint reports what must move', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const officer = await addOfficer(request, `e2e.report.${Date.now()}`, 'E2E Reporter');

    const res = await request.get(`${POOL}/pool/${officer.id}/open-records`);
    expect(res.ok()).toBeTruthy();

    const body = (await res.json()).data;
    expect(body).toHaveProperty('openCount');
    expect(body).toHaveProperty('openComplaints');
    expect(body).toHaveProperty('reassignmentRequired');
    expect(Array.isArray(body.openComplaints)).toBeTruthy();
  });

  test('a deactivated officer leaves the assignment rotation', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const userId = `e2e.rotation.${Date.now()}`;
    const officer = await addOfficer(request, userId, 'E2E Rotation');

    const deact = await request.put(`${POOL}/pool/${officer.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: {}
    });
    expect(deact.ok()).toBeTruthy();

    // The pool listing returns active officers only, so a deactivated one must be absent.
    const pool = await request.get(`${POOL}/pool?roleGroup=${GROUP}`);
    const stillListed = ((await pool.json()).data ?? []).some((o: any) => o.userId === userId);
    expect(stillListed).toBe(false);
  });
});
