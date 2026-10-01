import { test, expect } from '@playwright/test';
import { execFileSync } from 'child_process';

/**
 * UST887 — safe deactivation of a team member.
 *
 * Targets cms-workflow-service directly, because that is where the officer pool (WF_OFFICER_POOL)
 * and the deactivate endpoint live. The frontend previously called this path on the cms-backend
 * origin, which never serves /cms-workflow/**, so every deactivation silently 404'd while the UI
 * showed success.
 *
 * THE SUCCESSOR RULES ARE ONLY REACHABLE WITH OPEN WORK. OfficerDeactivationService.deactivate
 * (cms-workflow-service, ~line 102) calls validateSuccessor ONLY when findOpenComplaints returns a
 * non-empty list. Two tests here therefore used to deactivate an officer who held nothing and accept
 * `[200, 400]` — and 200 is the case where the rule never fired at all, so both tests passed whether
 * or not the safety rule existed. They now seed an OPEN complaint against the officer first, which is
 * the only state in which "cannot be their own successor" and "must be in the same role group" are
 * evaluated, and assert the single correct status plus the explanatory body.
 *
 * SEEDING. The open complaint is inserted directly into COMPLAINTS: no workflow action can produce a
 * complaint assigned to an arbitrary pool user id, and the deactivation query reads
 * COMPLAINTS.ASSIGNED_OFFICER by that id. Every row is prefixed 'S4-' and purged by prefix — cms_db
 * is shared and holds ~1,600 real complaints.
 *
 * If cms-workflow-service is not running these tests SKIP WITH A REASON rather than fail: the service
 * is a separate process on its own port and its absence is an environment gap, not a product defect.
 * A skip is reported as a skip by Playwright, never as a pass.
 */

const WORKFLOW_BASE = process.env['WORKFLOW_BASE_URL'] || 'http://localhost:8094';
const POOL = `${WORKFLOW_BASE}/cms-workflow/api/v1/assignment`;

const GROUP = 'E2E_DEACT_GRP';
/** A role group nobody in GROUP belongs to, for the out-of-group successor case. */
const OTHER_GROUP = 'E2E_DEACT_OTHER_GRP';

const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

/**
 * stderr is INHERITED, not discarded. It used to be 'ignore', so a seed that failed on schema drift
 * surfaced only as "Command failed: mysql.exe INSERT INTO …" with mysql's actual diagnosis thrown
 * away — which is how a missing record_version column read as four product failures.
 */
function sql(statement: string): string {
  return execFileSync(
    MYSQL_CLI,
    ['-u', 'cms_user', '-pcms_pass', 'cms_db', '--default-character-set=utf8mb4', '-N', '-B', '-e', statement],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'inherit'] }
  ).trim();
}

/**
 * An OPEN complaint held by `userId`, so deactivating them requires a transfer.
 *
 * 'in_progress' is deliberate: OfficerDeactivationService.CLOSED_STATUSES is
 * resolved/closed/rejected/withdrawn/adjudicated/conciliated, compared lowercase, and anything else
 * counts as open. A closed status here would leave openComplaints empty and the successor rules
 * unreachable again — the exact hole this file is fixing.
 */
function seedOpenComplaint(userId: string): string {
  const complaintNumber = `S4-DEACT-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
  sql(
    // record_version is NOT NULL with NO DEFAULT (JPA @Version, ddl-auto generated it that way), so
    // omitting it is ERROR 1364 "Field 'record_version' doesn't have a default value" and the seed
    // never lands. Every other seeder in e2e/ already passes it; this one had drifted.
    `INSERT INTO COMPLAINTS
       (complaint_number, complainant_name, complainant_email, complainant_phone, subject,
        description, status, assigned_officer, priority, filing_type, record_version,
        created_at, updated_at)
     VALUES
       ('${complaintNumber}', 'S4 Deactivation Fixture', 's4deact@example.com', '9876543210',
        'S4 open complaint held by a leaver', 'Seeded by safe-deactivation.spec.ts',
        'in_progress', '${userId}', 'MEDIUM', 'CEPC_MANUAL', 0, NOW(), NOW())`
  );
  return complaintNumber;
}

function purgeSeededComplaints(): void {
  // TRAP: '_' is a LIKE wildcard, so the prefix is matched with the literal '-' separator only.
  sql(`DELETE FROM COMPLAINT_TIMELINE WHERE COMPLAINT_ID IN
         (SELECT id FROM COMPLAINTS WHERE complaint_number LIKE 'S4-DEACT-%')`);
  sql(`DELETE FROM COMPLAINTS WHERE complaint_number LIKE 'S4-DEACT-%'`);
}

async function workflowUp(request: any): Promise<boolean> {
  try {
    const res = await request.get(`${POOL}/pool?roleGroup=${GROUP}`, { timeout: 4000 });
    return res.ok();
  } catch {
    return false;
  }
}

async function addOfficer(request: any, userId: string, displayName: string, roleGroup = GROUP) {
  const res = await request.post(`${POOL}/pool`, {
    data: { userId, displayName, roleGroup, regionalOffice: 'MUM', maxWorkload: 40 }
  });
  expect(res.ok()).toBeTruthy();
  return (await res.json()).data;
}

test.describe('UST887 — deactivation cannot strand open work', () => {

  test.afterAll(() => {
    purgeSeededComplaints();
  });

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

  test('an officer holding open work cannot be deactivated without a successor', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const userId = `e2e.stranded.${Date.now()}`;
    const officer = await addOfficer(request, userId, 'E2E Stranded Leaver');
    const complaintNumber = seedOpenComplaint(userId);

    const open = await request.get(`${POOL}/pool/${officer.id}/open-records`);
    const openBody = (await open.json()).data;
    expect(openBody.openCount, 'the seeded complaint must register as open work').toBe(1);
    expect(openBody.openComplaints).toContain(complaintNumber);
    expect(openBody.reassignmentRequired).toBe(true);

    // 409, not 200: the request was well-formed but cannot complete until a successor is named.
    // Allowing it would leave the complaint assigned to someone who can no longer act on it, which
    // is the entire stranded-work failure UST887 exists to prevent.
    const res = await request.put(`${POOL}/pool/${officer.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: {}
    });
    expect(res.status()).toBe(409);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(String(body.message)).toMatch(/open complaint/i);
    expect(body.data.openComplaints).toContain(complaintNumber);

    // Still active, and still holding the work: a refused deactivation must change nothing.
    const pool = await request.get(`${POOL}/pool?roleGroup=${GROUP}`);
    expect(((await pool.json()).data ?? []).some((o: any) => o.userId === userId)).toBe(true);
    expect(
      sql(`SELECT assigned_officer FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`)
    ).toBe(userId);
  });

  test('an officer cannot be their own successor', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const userId = `e2e.self.${Date.now()}`;
    const officer = await addOfficer(request, userId, 'E2E Self Successor');
    // Without OPEN work the successor is never consulted and this returns 200 — the rule would not be
    // under test at all. The previous `expect([200, 400]).toContain(...)` accepted exactly that.
    const complaintNumber = seedOpenComplaint(userId);

    const res = await request.put(`${POOL}/pool/${officer.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: { reassignTo: userId }
    });
    expect(res.status(), 'naming yourself as your own successor must be refused').toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(
      String(body.message),
      'the refusal must say why, or the admin screen has nothing to show the operator'
    ).toMatch(/own successor/i);

    // The transfer must not have happened, and the officer must still be active.
    expect(
      sql(`SELECT assigned_officer FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`)
    ).toBe(userId);
    const pool = await request.get(`${POOL}/pool?roleGroup=${GROUP}`);
    expect(((await pool.json()).data ?? []).some((o: any) => o.userId === userId)).toBe(true);
  });

  test('a successor outside the role group is rejected', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const leaverId = `e2e.leaver.${Date.now()}`;
    const leaver = await addOfficer(request, leaverId, 'E2E Leaver');
    const complaintNumber = seedOpenComplaint(leaverId);

    // A REAL, active officer who is simply in a different role group. A non-existent id would also
    // be refused, but by the "not found" arm rather than the group check — so the group rule itself
    // would stay untested.
    const outsiderId = `e2e.outsider.${Date.now()}`;
    await addOfficer(request, outsiderId, 'E2E Outsider', OTHER_GROUP);

    const res = await request.put(`${POOL}/pool/${leaver.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' },
      data: { reassignTo: outsiderId }
    });
    expect(res.status(), 'a successor in another role group must be refused').toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(String(body.message)).toMatch(/role group|active|available/i);

    // Reassigning to someone outside the group would recreate the stranded-work problem, so the
    // complaint must still be with the leaver.
    expect(
      sql(`SELECT assigned_officer FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`)
    ).toBe(leaverId);
  });

  test('a valid successor receives the open work and the officer leaves the rotation', async ({ request }) => {
    test.skip(!(await workflowUp(request)), 'cms-workflow-service is not running');

    const leaverId = `e2e.transfer.${Date.now()}`;
    const successorId = `e2e.successor.${Date.now()}`;
    const leaver = await addOfficer(request, leaverId, 'E2E Transferring Leaver');
    await addOfficer(request, successorId, 'E2E Valid Successor');
    const complaintNumber = seedOpenComplaint(leaverId);

    const res = await request.put(`${POOL}/pool/${leaver.id}/deactivate`, {
      headers: { 'X-User-Id': 'cms.admin' }, data: { reassignTo: successorId }
    });
    expect(res.status(), await res.text()).toBe(200);
    const data = (await res.json()).data;
    expect(data.reassignedCount).toBe(1);
    expect(data.reassignedComplaints).toContain(complaintNumber);
    expect(data.reassignedTo).toBe(successorId);

    // Read from the table, not from the service's own account of itself.
    expect(
      sql(`SELECT assigned_officer FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`)
    ).toBe(successorId);

    const pool = await request.get(`${POOL}/pool?roleGroup=${GROUP}`);
    const listed = ((await pool.json()).data ?? []).map((o: any) => o.userId);
    expect(listed).not.toContain(leaverId);
    expect(listed).toContain(successorId);
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
