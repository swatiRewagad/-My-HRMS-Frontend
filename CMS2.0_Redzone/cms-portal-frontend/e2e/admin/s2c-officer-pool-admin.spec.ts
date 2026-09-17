/**
 * S2C — officer-pool administration, reassignment requests, unclaimed-draft escalation.
 *
 * Drives the live API against real MySQL. The behaviours under test are all server-side authority
 * checks and audit guarantees, which a mocked repository cannot demonstrate.
 *
 * Fixtures are prefixed S2CP- and purged in both beforeAll and afterAll. cms_db is shared, so nothing
 * here truncates.
 */
import { test, expect, APIRequestContext } from '@playwright/test';
import { sql, API_BASE } from '../aa/aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S2CP';
const ROLE_GROUP = `${PREFIX}_AA_DO`;

const ADMIN = identityHeadersFor('aa_admin_001', 'AA');
const DO_ONE = { 'X-User-Id': `${PREFIX}-do1`, 'X-User-Roles': 'AA_DO' };
const DO_TWO = { 'X-User-Id': `${PREFIX}-do2`, 'X-User-Roles': 'AA_DO' };

function seedOfficer(userId: string, opts: {
  active?: boolean; onLeave?: boolean; threshold?: number; skills?: string | null;
} = {}): void {
  const active = opts.active === false ? 0 : 1;
  const onLeave = opts.onLeave === true ? 1 : 0;
  const threshold = opts.threshold ?? 0;
  const skills = opts.skills ? `'${opts.skills}'` : 'NULL';
  sql(`INSERT INTO wf_officer_pool
         (user_id, display_name, role_group, regional_office, is_active, is_on_leave,
          current_workload, max_workload, skill_languages)
       VALUES ('${userId}', '${userId} name', '${ROLE_GROUP}', NULL, ${active}, ${onLeave},
               0, ${threshold}, ${skills})`);
}

/** Places a record directly, so a test can set up a workload without driving the engine. */
function seedHeldRecord(appealNumber: string, userId: string, opts: {
  exempt?: boolean; assignedMinutesAgo?: number; claimed?: boolean;
} = {}): void {
  const exempt = opts.exempt ? 1 : 0;
  const ago = opts.assignedMinutesAgo ?? 0;
  const claimed = opts.claimed ? 'NOW(6)' : 'NULL';
  sql(`INSERT INTO aa_assignment_record
         (appeal_number, assigned_user_id, role_group, outcome, threshold_exempt, assigned_by,
          assigned_at, claimed_at)
       VALUES ('${appealNumber}', '${userId}', '${ROLE_GROUP}', 'ASSIGNED', ${exempt}, 'SYSTEM',
               DATE_SUB(NOW(6), INTERVAL ${ago} MINUTE), ${claimed})`);
}

function purge(): void {
  sql(`DELETE FROM aa_assignment_record WHERE appeal_number LIKE '${PREFIX}-%'
        OR assigned_user_id LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM aa_assignment_audit WHERE subject_user_id LIKE '${PREFIX}-%'
        OR appeal_number LIKE '${PREFIX}-%' OR role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM aa_reassignment_request WHERE appeal_number LIKE '${PREFIX}-%'
        OR from_user_id LIKE '${PREFIX}-%' OR requested_by LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM wf_officer_pool WHERE user_id LIKE '${PREFIX}-%' OR role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM wf_assignment_counter WHERE role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM in_app_notifications WHERE target_user_id LIKE '${PREFIX}-%'
        OR related_entity_id LIKE '${PREFIX}-%'`);
}

function auditCount(action: string, subject: string): number {
  return Number(sql(`SELECT COUNT(*) FROM aa_assignment_audit
                      WHERE action = '${action}' AND subject_user_id = '${subject}'`));
}

test.describe('S2C officer pool admin', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());
  test.beforeEach(() => purge());

  test('the pool listing reports live workload and the engine\'s own eligibility verdict',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-p1`, { threshold: 2 });
      seedOfficer(`${PREFIX}-p2`, { threshold: 2, onLeave: true });
      seedHeldRecord(`${PREFIX}-L-1`, `${PREFIX}-p1`);

      const res = await request.get(
        `${API_BASE}/api/v1/aa/assignment/pool?roleGroup=${ROLE_GROUP}`, { headers: ADMIN });
      expect(res.status()).toBe(200);
      const pool = await res.json();

      const p1 = pool.find((o: any) => o.userId === `${PREFIX}-p1`);
      const p2 = pool.find((o: any) => o.userId === `${PREFIX}-p2`);

      // Counted from live rows, not from wf_officer_pool.current_workload, which is left at 0 by the
      // fixture precisely because that stored counter drifts.
      expect(p1.currentWorkload).toBe(1);
      expect(p1.eligible).toBe(true);
      expect(p1.atThreshold).toBe(false);

      expect(p2.eligible).toBe(false);
      expect(p2.onLeave).toBe(true);
    });

  test('a threshold change is audited with actor, old value, new value and reason',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-t1`, { threshold: 5 });

      const res = await request.put(
        `${API_BASE}/api/v1/aa/assignment/pool/${PREFIX}-t1/threshold`,
        { headers: ADMIN, data: { threshold: 8, reason: 'seasonal backlog' } });
      expect(res.status()).toBe(200);
      const body = await res.json();

      expect(body.previousThreshold).toBe(5);
      expect(body.threshold).toBe(8);
      expect(body.messageKey).toBe('aa.pool.threshold_updated');

      expect(Number(sql(`SELECT max_workload FROM wf_officer_pool
                          WHERE user_id = '${PREFIX}-t1'`))).toBe(8);

      const row = sql(`SELECT CONCAT_WS('|', old_value, new_value, performed_by, reason)
                         FROM aa_assignment_audit
                        WHERE action = 'THRESHOLD_CHANGED' AND subject_user_id = '${PREFIX}-t1'
                        ORDER BY id DESC LIMIT 1`);
      expect(row).toBe('5|8|aa_admin_001|seasonal backlog');
    });

  test('a threshold change takes effect on the very next assignment', async ({ request }) => {
    seedOfficer(`${PREFIX}-t2`, { threshold: 1 });
    seedHeldRecord(`${PREFIX}-T2-1`, `${PREFIX}-t2`);

    // At threshold: assignment is refused.
    const blocked = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`,
      { headers: ADMIN, data: { appealNumber: `${PREFIX}-T2-2`, roleGroup: ROLE_GROUP } });
    expect((await blocked.json()).outcome).toBe('UNASSIGNED_POOL_EXHAUSTED');

    await request.put(`${API_BASE}/api/v1/aa/assignment/pool/${PREFIX}-t2/threshold`,
      { headers: ADMIN, data: { threshold: 3, reason: 'raise capacity' } });

    // No restart, no cache flush: eligibility is evaluated per assignment.
    const allowed = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`,
      { headers: ADMIN, data: { appealNumber: `${PREFIX}-T2-3`, roleGroup: ROLE_GROUP } });
    const body = await allowed.json();
    expect(body.outcome).toBe('ASSIGNED');
    expect(body.assignedUserId).toBe(`${PREFIX}-t2`);
  });

  test('a negative threshold is refused', async ({ request }) => {
    seedOfficer(`${PREFIX}-t3`, { threshold: 2 });
    const res = await request.put(
      `${API_BASE}/api/v1/aa/assignment/pool/${PREFIX}-t3/threshold`,
      { headers: ADMIN, data: { threshold: -1, reason: 'invalid' } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
    expect(Number(sql(`SELECT max_workload FROM wf_officer_pool
                        WHERE user_id = '${PREFIX}-t3'`))).toBe(2);
  });

  test('deactivation is PREVIEWED before it is applied, listing pending work',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-d1`, { threshold: 5 });
      seedHeldRecord(`${PREFIX}-D-1`, `${PREFIX}-d1`);
      seedHeldRecord(`${PREFIX}-D-2`, `${PREFIX}-d1`);

      const res = await request.post(`${API_BASE}/api/v1/aa/assignment/pool/deactivation-preview`,
        { headers: ADMIN, data: { userIds: [`${PREFIX}-d1`] } });
      expect(res.status()).toBe(200);
      const preview = await res.json();

      expect(preview.totalPending).toBe(2);
      expect(preview.requiresConfirmation).toBe(true);
      expect(preview.messageKey).toBe('aa.pool.deactivate_warning_pending_work');
      expect(preview.officers[0].pendingAppeals).toContain(`${PREFIX}-D-1`);

      // The preview must not have changed anything -- that is what lets the admin cancel.
      expect(sql(`SELECT CAST(is_active AS UNSIGNED) FROM wf_officer_pool WHERE user_id = '${PREFIX}-d1'`)).toBe('1');
    });

  test('deactivating an officer with pending work REQUIRES confirmation server-side',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-d2`, { threshold: 5 });
      seedHeldRecord(`${PREFIX}-D2-1`, `${PREFIX}-d2`);

      // Unconfirmed: refused per-officer, not by failing the whole request. A browser-only
      // confirmation dialog would be bypassable by calling the API directly.
      const unconfirmed = await request.post(
        `${API_BASE}/api/v1/aa/assignment/pool/bulk-activation`,
        { headers: ADMIN, data: { userIds: [`${PREFIX}-d2`], active: false, reason: 'no confirm' } });
      expect(unconfirmed.status()).toBe(200);
      const body = await unconfirmed.json();

      expect(body.failed.length).toBe(1);
      expect(body.failed[0].messageKey).toBe('aa.pool.error_confirmation_required');
      expect(sql(`SELECT CAST(is_active AS UNSIGNED) FROM wf_officer_pool WHERE user_id = '${PREFIX}-d2'`)).toBe('1');

      const confirmed = await request.post(
        `${API_BASE}/api/v1/aa/assignment/pool/bulk-activation`,
        { headers: ADMIN, data: {
            userIds: [`${PREFIX}-d2`], active: false, confirmed: true, reason: 'officer resigned' } });
      expect((await confirmed.json()).failed.length).toBe(0);
      expect(sql(`SELECT CAST(is_active AS UNSIGNED) FROM wf_officer_pool WHERE user_id = '${PREFIX}-d2'`)).toBe('0');
      expect(auditCount('ACTIVATION_CHANGED', `${PREFIX}-d2`)).toBeGreaterThan(0);
    });

  test('a bulk action isolates failures per officer', async ({ request }) => {
    seedOfficer(`${PREFIX}-b1`, { threshold: 5, active: false });
    seedOfficer(`${PREFIX}-b2`, { threshold: 5, active: false });

    const res = await request.post(`${API_BASE}/api/v1/aa/assignment/pool/bulk-activation`, {
      headers: ADMIN,
      data: {
        userIds: [`${PREFIX}-b1`, `${PREFIX}-does-not-exist`, `${PREFIX}-b2`],
        active: true,
        reason: 'reinstating team',
      },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();

    // One bad id must not abort the batch: a partially applied action the admin cannot see is worse
    // than a reported failure.
    expect(body.succeeded.length).toBe(2);
    expect(body.failed.length).toBe(1);
    expect(body.failed[0].userId).toBe(`${PREFIX}-does-not-exist`);
    expect(body.messageKey).toBe('aa.pool.bulk_activation_partial');

    expect(sql(`SELECT CAST(is_active AS UNSIGNED) FROM wf_officer_pool WHERE user_id = '${PREFIX}-b1'`)).toBe('1');
    expect(sql(`SELECT CAST(is_active AS UNSIGNED) FROM wf_officer_pool WHERE user_id = '${PREFIX}-b2'`)).toBe('1');
  });

  test('an empty selection is refused rather than treated as "all"', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/aa/assignment/pool/bulk-activation`,
      { headers: ADMIN, data: { userIds: [], active: false, reason: 'oops' } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('language skills are normalised and audited', async ({ request }) => {
    seedOfficer(`${PREFIX}-s1`, { threshold: 5 });

    const res = await request.put(`${API_BASE}/api/v1/aa/assignment/pool/${PREFIX}-s1/skills`,
      { headers: ADMIN, data: { skillLanguages: ' BN , hi,bn ', reason: 'verified proficiency' } });
    expect(res.status()).toBe(200);

    // Lower-cased, trimmed and de-duplicated, so `speaks()` can match reliably.
    expect((await res.json()).skillLanguages).toBe('bn,hi');
    expect(auditCount('SKILL_CHANGED', `${PREFIX}-s1`)).toBeGreaterThan(0);
  });
});

test.describe('S2C reassignment requests', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());
  test.beforeEach(() => purge());

  test('the holder may request reassignment and an admin approves it', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedOfficer(`${PREFIX}-do2`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-1`, `${PREFIX}-do1`);

    const raised = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`, {
      headers: DO_ONE,
      data: { appealNumber: `${PREFIX}-R-1`, reason: 'conflict of interest' },
    });
    expect(raised.status()).toBe(200);
    const body = await raised.json();
    expect(body.status).toBe('PENDING');
    expect(body.messageKey).toBe('aa.reassign.submitted');

    const pending = await request.get(`${API_BASE}/api/v1/aa/reassignment/pending`,
      { headers: ADMIN });
    expect((await pending.json()).length).toBe(1);

    const approved = await request.post(
      `${API_BASE}/api/v1/aa/reassignment/${body.id}/approve`,
      { headers: ADMIN, data: { comment: 'reallocated' } });
    expect(approved.status()).toBe(200);
    const decided = await approved.json();

    expect(decided.status).toBe('APPROVED');
    // Approval routes through the engine, so the record must actually move to the other officer.
    expect(decided.resolvedToUserId).toBe(`${PREFIX}-do2`);

    const holder = sql(`SELECT assigned_user_id FROM aa_assignment_record
                         WHERE appeal_number = '${PREFIX}-R-1' AND released_at IS NULL`);
    expect(holder).toBe(`${PREFIX}-do2`);
  });

  test('an officer cannot request reassignment of a record they do not hold', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedOfficer(`${PREFIX}-do2`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-2`, `${PREFIX}-do1`);

    // Pushing work off a colleague's queue is the admin's manual assignment, a different authority.
    const res = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`, {
      headers: DO_TWO,
      data: { appealNumber: `${PREFIX}-R-2`, reason: 'not mine to move' },
    });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(Number(sql(`SELECT COUNT(*) FROM aa_reassignment_request
                        WHERE appeal_number = '${PREFIX}-R-2'`))).toBe(0);
  });

  test('a request without a reason is refused', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-3`, `${PREFIX}-do1`);

    const res = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
      { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-3`, reason: '   ' } });
    expect(res.status()).toBeGreaterThanOrEqual(400);
    expect(res.status()).toBeLessThan(500);
  });

  test('a second pending request for the same record is refused', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-4`, `${PREFIX}-do1`);

    const first = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
      { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-4`, reason: 'first' } });
    expect(first.status()).toBe(200);

    // Two open requests would let two admins decide independently and move the record twice.
    const second = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
      { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-4`, reason: 'second' } });
    expect(second.status()).toBeGreaterThanOrEqual(400);
  });

  test('a rejected request leaves the record where it was, and notifies the requester',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
      seedOfficer(`${PREFIX}-do2`, { threshold: 5 });
      seedHeldRecord(`${PREFIX}-R-5`, `${PREFIX}-do1`);

      const raised = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
        { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-5`, reason: 'workload' } });
      const { id } = await raised.json();

      const rejected = await request.post(`${API_BASE}/api/v1/aa/reassignment/${id}/reject`,
        { headers: ADMIN, data: { comment: 'handle it yourself' } });
      expect((await rejected.json()).status).toBe('REJECTED');

      expect(sql(`SELECT assigned_user_id FROM aa_assignment_record
                   WHERE appeal_number = '${PREFIX}-R-5' AND released_at IS NULL`))
        .toBe(`${PREFIX}-do1`);

      await expect.poll(() => Number(sql(
        `SELECT COUNT(*) FROM in_app_notifications
          WHERE target_user_id = '${PREFIX}-do1' AND title = 'aa.reassign.notify_rejected'`)),
        { timeout: 10_000 }).toBeGreaterThan(0);
    });

  test('only the requester may withdraw their request', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-6`, `${PREFIX}-do1`);

    const raised = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
      { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-6`, reason: 'mine' } });
    const { id } = await raised.json();

    // An admin wanting to end it rejects instead, which records a decision rather than erasing it.
    const wrong = await request.post(`${API_BASE}/api/v1/aa/reassignment/${id}/withdraw`,
      { headers: DO_TWO });
    expect(wrong.status()).toBeGreaterThanOrEqual(400);

    const right = await request.post(`${API_BASE}/api/v1/aa/reassignment/${id}/withdraw`,
      { headers: DO_ONE });
    expect((await right.json()).status).toBe('WITHDRAWN');
  });

  test('an already-decided request cannot be decided again', async ({ request }) => {
    seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
    seedOfficer(`${PREFIX}-do2`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-R-7`, `${PREFIX}-do1`);

    const raised = await request.post(`${API_BASE}/api/v1/aa/reassignment/request`,
      { headers: DO_ONE, data: { appealNumber: `${PREFIX}-R-7`, reason: 'once' } });
    const { id } = await raised.json();

    expect((await request.post(`${API_BASE}/api/v1/aa/reassignment/${id}/approve`,
      { headers: ADMIN, data: {} })).status()).toBe(200);

    const again = await request.post(`${API_BASE}/api/v1/aa/reassignment/${id}/reject`,
      { headers: ADMIN, data: {} });
    expect(again.status()).toBeGreaterThanOrEqual(400);
  });

  test('a DO cannot see the approval queue', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/aa/reassignment/pending`, { headers: DO_ONE });
    expect(res.status()).toBe(403);
  });
});

test.describe('S2C unclaimed-draft escalation', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());
  test.beforeEach(() => purge());

  test('a draft unclaimed past the backoff escalates to the AA Admin exactly once',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-e1`, { threshold: 5 });
      // Backoff defaults to 2880 minutes (48h); this record is well past it.
      seedHeldRecord(`${PREFIX}-E-1`, `${PREFIX}-e1`, { assignedMinutesAgo: 5000 });

      const res = await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`,
        { headers: ADMIN });
      expect(res.status()).toBe(200);
      expect((await res.json()).escalated).toBeGreaterThan(0);

      expect(sql(`SELECT IF(escalated_at IS NULL, 'NULL', 'STAMPED')
                    FROM aa_assignment_record WHERE appeal_number = '${PREFIX}-E-1'`))
        .toBe('STAMPED');

      // NotificationService.send is @Async, so the row lands shortly after the sweep returns.
      await expect.poll(() => Number(sql(
        `SELECT COUNT(*) FROM in_app_notifications
          WHERE type = 'ESCALATION' AND related_entity_id = '${PREFIX}-E-1'`)),
        { timeout: 10_000 }).toBe(1);

      // A second sweep must not re-alert, or a persistently unclaimed draft buries the admin's inbox.
      await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`, { headers: ADMIN });
      expect(Number(sql(`SELECT COUNT(*) FROM in_app_notifications
                          WHERE type = 'ESCALATION' AND related_entity_id = '${PREFIX}-E-1'`)))
        .toBe(1);
    });

  test('a draft still inside the backoff window is not escalated', async ({ request }) => {
    seedOfficer(`${PREFIX}-e2`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-E-2`, `${PREFIX}-e2`, { assignedMinutesAgo: 10 });

    await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`, { headers: ADMIN });

    expect(sql(`SELECT IF(escalated_at IS NULL, 'NULL', 'STAMPED')
                  FROM aa_assignment_record WHERE appeal_number = '${PREFIX}-E-2'`)).toBe('NULL');
  });

  test('a claimed draft is never escalated however long it has been held', async ({ request }) => {
    seedOfficer(`${PREFIX}-e3`, { threshold: 5 });
    seedHeldRecord(`${PREFIX}-E-3`, `${PREFIX}-e3`, { assignedMinutesAgo: 9000, claimed: true });

    await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`, { headers: ADMIN });

    // Escalation is about drafts nobody has picked up, not about slow work.
    expect(sql(`SELECT IF(escalated_at IS NULL, 'NULL', 'STAMPED')
                  FROM aa_assignment_record WHERE appeal_number = '${PREFIX}-E-3'`)).toBe('NULL');
  });

  test('claiming a draft stops the escalation clock, and only the holder may claim',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-do1`, { threshold: 5 });
      seedHeldRecord(`${PREFIX}-E-4`, `${PREFIX}-do1`, { assignedMinutesAgo: 5000 });

      const wrongHolder = await request.post(`${API_BASE}/api/v1/aa/reassignment/claim`,
        { headers: DO_TWO, data: { appealNumber: `${PREFIX}-E-4`, userId: `${PREFIX}-do2` } });
      expect((await wrongHolder.json()).claimed).toBe(false);

      const claimed = await request.post(`${API_BASE}/api/v1/aa/reassignment/claim`,
        { headers: DO_ONE, data: { appealNumber: `${PREFIX}-E-4`, userId: `${PREFIX}-do1` } });
      expect((await claimed.json()).claimed).toBe(true);

      await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`, { headers: ADMIN });
      expect(sql(`SELECT IF(escalated_at IS NULL, 'NULL', 'STAMPED')
                    FROM aa_assignment_record WHERE appeal_number = '${PREFIX}-E-4'`)).toBe('NULL');
    });

  test('a DO cannot trigger the escalation sweep', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/aa/reassignment/escalation-sweep`,
      { headers: DO_ONE });
    expect(res.status()).toBe(403);
  });
});
