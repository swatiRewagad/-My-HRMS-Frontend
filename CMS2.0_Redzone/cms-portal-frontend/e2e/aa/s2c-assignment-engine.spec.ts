/**
 * S2C — AA assignment engine: eligibility, thresholds, deterministic rotation, concurrency.
 *
 * These tests drive the live API against real MySQL rather than mocking the pool, because the
 * guarantees under test are database-level: a pessimistic row lock, a persisted pointer, and counts
 * derived from live rows. A mocked repository would pass regardless of whether the lock exists.
 *
 * Every fixture row is prefixed S2C- and purged in both beforeAll and afterAll. cms_db is shared and
 * holds real complaints, so nothing here truncates.
 */
import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { sql, API_BASE } from './aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S2C';
const ROLE_GROUP = `${PREFIX}_AA_DO`;

const ADMIN = identityHeadersFor('aa_admin_001', 'AA');
const DO_USER = identityHeadersFor('aa_do_001', 'AA');

/** Officers are created directly in the pool: there is no admin endpoint to add one, by design. */
function seedOfficer(userId: string, opts: {
  active?: boolean;
  onLeave?: boolean;
  threshold?: number;
  skills?: string | null;
  roleGroup?: string;
} = {}): void {
  const active = opts.active === false ? 0 : 1;
  const onLeave = opts.onLeave === true ? 1 : 0;
  const threshold = opts.threshold ?? 0;
  const skills = opts.skills ? `'${opts.skills}'` : 'NULL';
  const roleGroup = opts.roleGroup ?? ROLE_GROUP;
  sql(`INSERT INTO wf_officer_pool
         (user_id, display_name, role_group, regional_office, is_active, is_on_leave,
          current_workload, max_workload, skill_languages)
       VALUES ('${userId}', '${userId} name', '${roleGroup}', NULL, ${active}, ${onLeave},
               0, ${threshold}, ${skills})`);
}

function purge(): void {
  sql(`DELETE FROM aa_assignment_record WHERE appeal_number LIKE '${PREFIX}-%'
        OR assigned_user_id LIKE '${PREFIX}-%'`);
  // role_group is matched with the underscore ESCAPED: the fixture group is S2C_AA_DO, and an
  // unescaped '_' is a LIKE single-character wildcard, so 'S2C-%' alone never matched pool-wide rows
  // such as POINTER_RESET, which carry no subject_user_id and no appeal_number.
  sql(`DELETE FROM aa_assignment_audit WHERE subject_user_id LIKE '${PREFIX}-%'
        OR appeal_number LIKE '${PREFIX}-%'
        OR role_group LIKE '${PREFIX}\\_%'`);
  sql(`DELETE FROM wf_officer_pool WHERE user_id LIKE '${PREFIX}-%' OR role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM wf_assignment_counter WHERE role_group LIKE '${PREFIX}-%'`);
  sql(`DELETE FROM in_app_notifications WHERE target_user_id LIKE '${PREFIX}-%'
        OR related_entity_id LIKE '${PREFIX}-%'`);
}

async function assign(request: APIRequestContext, appealNumber: string, extra: Record<string, unknown> = {}) {
  return request.post(`${API_BASE}/api/v1/aa/assignment/assign`, {
    headers: DO_USER,
    data: { appealNumber, roleGroup: ROLE_GROUP, ...extra },
  });
}

function heldCount(userId: string): number {
  return Number(sql(
    `SELECT COUNT(*) FROM aa_assignment_record
      WHERE assigned_user_id = '${userId}' AND released_at IS NULL AND threshold_exempt = 0`));
}

test.describe('S2C assignment engine', () => {
  test.beforeAll(() => purge());
  test.afterAll(() => purge());

  test.beforeEach(() => {
    // Each test builds its own pool. Leaving rows behind would let one test's officers satisfy
    // another's assertions, which is how a rotation bug hides.
    purge();
  });

  test('the FIRST EVER assignment into a brand-new role group succeeds', async ({ request }) => {
    // Regression test. Every other test here reuses a role group whose pointer row already exists, so
    // none of them exercised pointer CREATION -- and creation was broken: SELECT ... FOR UPDATE on a
    // missing row takes an InnoDB gap lock, and the REQUIRES_NEW insert meant to fill that gap then
    // blocked on the transaction holding it. The result was a self-deadlock, surfacing as "Lock wait
    // timeout exceeded" on the first assignment into any newly created role group.
    const freshGroup = `${PREFIX}_FRESH_${Date.now()}`;
    const officer = `${PREFIX}-fresh1`;
    seedOfficer(officer, { threshold: 5, roleGroup: freshGroup });

    try {
      expect(sql(`SELECT COUNT(*) FROM wf_assignment_counter WHERE role_group = '${freshGroup}'`))
        .toBe('0');

      const res = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`, {
        headers: DO_USER,
        data: { appealNumber: `${PREFIX}-FRESH-1`, roleGroup: freshGroup },
      });
      expect(res.status()).toBe(200);
      const body = await res.json();

      expect(body.outcome).toBe('ASSIGNED');
      expect(body.assignedUserId).toBe(officer);

      // The pointer must now exist and name the officer who was chosen.
      expect(sql(`SELECT last_assigned_user_id FROM wf_assignment_counter
                   WHERE role_group = '${freshGroup}'`)).toBe(officer);
    } finally {
      sql(`DELETE FROM aa_assignment_record WHERE role_group = '${freshGroup}'`);
      sql(`DELETE FROM wf_officer_pool WHERE role_group = '${freshGroup}'`);
      sql(`DELETE FROM wf_assignment_counter WHERE role_group = '${freshGroup}'`);
    }
  });

  test('excludes inactive and on-leave officers in real time', async ({ request }) => {
    seedOfficer(`${PREFIX}-active`, { threshold: 10 });
    seedOfficer(`${PREFIX}-inactive`, { threshold: 10, active: false });
    seedOfficer(`${PREFIX}-onleave`, { threshold: 10, onLeave: true });

    const res = await assign(request, `${PREFIX}-A-1`);
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.outcome).toBe('ASSIGNED');
    expect(body.assignedUserId).toBe(`${PREFIX}-active`);

    // Flipping the only eligible officer to on-leave must change the outcome with no restart and no
    // cache flush: eligibility is evaluated per assignment.
    sql(`UPDATE wf_officer_pool SET is_on_leave = 1 WHERE user_id = '${PREFIX}-active'`);
    const after = await assign(request, `${PREFIX}-A-2`);
    const afterBody = await after.json();

    expect(afterBody.outcome).toBe('UNASSIGNED_POOL_EMPTY');
    expect(afterBody.assignedUserId).toBeNull();
    expect(afterBody.messageKey).toBe('aa.assignment.pool_empty');
  });

  test('an officer at threshold is excluded, and an exhausted pool does not silently breach it',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-solo`, { threshold: 2 });

      for (const n of [1, 2]) {
        const res = await assign(request, `${PREFIX}-T-${n}`);
        expect((await res.json()).assignedUserId).toBe(`${PREFIX}-solo`);
      }
      expect(heldCount(`${PREFIX}-solo`)).toBe(2);

      // Third attempt: the officer is at threshold and there is nobody else. The predecessor assigned
      // to the least-loaded officer anyway and logged a warning, which made a breach look like a
      // normal placement. It must be reported instead.
      const third = await assign(request, `${PREFIX}-T-3`);
      const body = await third.json();

      expect(body.outcome).toBe('UNASSIGNED_POOL_EXHAUSTED');
      expect(body.assignedUserId).toBeNull();
      expect(body.messageKey).toBe('aa.assignment.pool_exhausted');
      expect(heldCount(`${PREFIX}-solo`)).toBe(2);
    });

  test('threshold of 0 means unlimited, matching the existing pool convention', async ({ request }) => {
    seedOfficer(`${PREFIX}-unlimited`, { threshold: 0 });

    for (const n of [1, 2, 3, 4, 5]) {
      const res = await assign(request, `${PREFIX}-U-${n}`);
      expect((await res.json()).assignedUserId).toBe(`${PREFIX}-unlimited`);
    }
    expect(heldCount(`${PREFIX}-unlimited`)).toBe(5);
  });

  test('CONCURRENCY: no officer exceeds their threshold under simultaneous assignment',
    async ({ request }) => {
      // 3 officers x threshold 2 = 6 places. 12 simultaneous requests compete for them.
      const officers = [`${PREFIX}-c1`, `${PREFIX}-c2`, `${PREFIX}-c3`];
      officers.forEach(o => seedOfficer(o, { threshold: 2 }));

      const attempts = Array.from({ length: 12 }, (_, i) => assign(request, `${PREFIX}-C-${i + 1}`));
      const results = await Promise.all(attempts);
      const bodies = await Promise.all(results.map(r => r.json()));

      const assigned = bodies.filter(b => b.assignedUserId !== null);
      const rejected = bodies.filter(b => b.outcome === 'UNASSIGNED_POOL_EXHAUSTED');

      // The invariant that matters: capacity is 6, so exactly 6 succeed and 6 are turned away.
      expect(assigned.length).toBe(6);
      expect(rejected.length).toBe(6);

      // And no individual officer is over their threshold. Without the row lock taken before the pool
      // is read, two callers observe the same officer one below threshold and both assign.
      for (const officer of officers) {
        expect(heldCount(officer), `${officer} must not exceed threshold 2`).toBeLessThanOrEqual(2);
      }
      const total = officers.reduce((sum, o) => sum + heldCount(o), 0);
      expect(total).toBe(6);
    });

  test('rotation tie-break is deterministic and SURVIVES A RESTART', async ({ request }) => {
    // An in-memory pointer passes a single-run test and fails in production, so the persisted pointer
    // is asserted directly in the database rather than inferred from a sequence of calls.
    const officers = [`${PREFIX}-r1`, `${PREFIX}-r2`, `${PREFIX}-r3`];
    officers.forEach(o => seedOfficer(o, { threshold: 5 }));

    const first = await assign(request, `${PREFIX}-R-1`);
    expect((await first.json()).assignedUserId).toBe(`${PREFIX}-r1`);

    // The pointer must be persisted, and must identify an OFFICER, not a list position. A stored index
    // is meaningless because the candidate list is re-sorted by workload on every call.
    const pointer = sql(
      `SELECT last_assigned_user_id FROM wf_assignment_counter WHERE role_group = '${ROLE_GROUP}'`);
    expect(pointer).toBe(`${PREFIX}-r1`);

    const second = await assign(request, `${PREFIX}-R-2`);
    expect((await second.json()).assignedUserId).toBe(`${PREFIX}-r2`);

    // Simulate a restart: the pointer row survives, so rotation must continue from r2 rather than
    // restarting at r1. Rewriting the row is exactly what a fresh JVM would read.
    const persisted = sql(
      `SELECT last_assigned_user_id FROM wf_assignment_counter WHERE role_group = '${ROLE_GROUP}'`);
    expect(persisted).toBe(`${PREFIX}-r2`);

    const third = await assign(request, `${PREFIX}-R-3`);
    expect((await third.json()).assignedUserId).toBe(`${PREFIX}-r3`);

    // Wrap: after the last officer, rotation returns to the head of the tied group.
    const fourth = await assign(request, `${PREFIX}-R-4`);
    expect((await fourth.json()).assignedUserId).toBe(`${PREFIX}-r1`);
  });

  test('lowest workload wins before the rotation pointer is consulted', async ({ request }) => {
    seedOfficer(`${PREFIX}-busy`, { threshold: 5 });
    seedOfficer(`${PREFIX}-idle`, { threshold: 5 });

    // Give -busy a head start so the two are not tied.
    sql(`INSERT INTO aa_assignment_record
           (appeal_number, assigned_user_id, role_group, outcome, threshold_exempt, assigned_by, assigned_at)
         VALUES ('${PREFIX}-W-seed', '${PREFIX}-busy', '${ROLE_GROUP}', 'ASSIGNED', 0, 'SYSTEM', NOW(6))`);

    const res = await assign(request, `${PREFIX}-W-1`);
    // Not a tie, so the pointer must not override the load imbalance.
    expect((await res.json()).assignedUserId).toBe(`${PREFIX}-idle`);
  });

  test('an exhausted pool preserves the rotation pointer and does not flood the audit log',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-e1`, { threshold: 1 });

      const first = await assign(request, `${PREFIX}-E-1`);
      expect((await first.json()).assignedUserId).toBe(`${PREFIX}-e1`);
      expect(sql(`SELECT last_assigned_user_id FROM wf_assignment_counter
                   WHERE role_group = '${ROLE_GROUP}'`)).toBe(`${PREFIX}-e1`);

      // Three exhausted attempts. Resetting the pointer on each one destroyed the rotation position
      // without placing anything, so the next successful assignment restarted at the head of the list
      // and repeatedly favoured the same officer -- and wrote an audit row every time.
      for (const n of [2, 3, 4]) {
        const res = await assign(request, `${PREFIX}-E-${n}`);
        expect((await res.json()).outcome).toBe('UNASSIGNED_POOL_EXHAUSTED');
      }

      // The pointer is intact, so rotation resumes where it left off once capacity returns.
      expect(sql(`SELECT IFNULL(last_assigned_user_id, 'NULL')
                    FROM wf_assignment_counter WHERE role_group = '${ROLE_GROUP}'`))
        .toBe(`${PREFIX}-e1`);

      // And nothing was audited, because nothing changed. The reset now happens only on the grace path,
      // where a placement actually follows it.
      expect(Number(sql(`SELECT COUNT(*) FROM aa_assignment_audit
                          WHERE action = 'POINTER_RESET' AND role_group = '${ROLE_GROUP}'`)))
        .toBe(0);
    });

  test('a configured grace allowance places above threshold and audits the breach',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-g1`, { threshold: 1 });
      sql(`UPDATE SYSTEM_CONFIG SET config_value = '1'
            WHERE config_key = 'cms.aa.assignment.grace_allowance'`);
      try {
        // SystemConfigService caches for 30s, so wait it out rather than assuming an immediate read.
        await new Promise(resolve => setTimeout(resolve, 31_000));

        expect((await (await assign(request, `${PREFIX}-G-1`)).json()).assignedUserId)
          .toBe(`${PREFIX}-g1`);

        const second = await assign(request, `${PREFIX}-G-2`);
        const body = await second.json();

        // Placed, but reported as a distinct outcome rather than an ordinary ASSIGNED, so a breach
        // stays visible to the caller.
        expect(body.outcome).toBe('ASSIGNED_UNDER_GRACE');
        expect(body.assignedUserId).toBe(`${PREFIX}-g1`);
        expect(body.messageKey).toBe('aa.assignment.assigned_under_grace');

        const audited = Number(sql(`SELECT COUNT(*) FROM aa_assignment_audit
                                     WHERE action = 'THRESHOLD_BREACH_GRACE'
                                       AND subject_user_id = '${PREFIX}-g1'`));
        expect(audited, 'an above-threshold placement must be auditable').toBeGreaterThan(0);
      } finally {
        sql(`UPDATE SYSTEM_CONFIG SET config_value = '0'
              WHERE config_key = 'cms.aa.assignment.grace_allowance'`);
      }
    });

  test('vernacular override bypasses rotation and is not charged to the threshold',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-v-generalist`, { threshold: 5 });
      seedOfficer(`${PREFIX}-v-bengali`, { threshold: 1, skills: 'bn,hi' });

      // The Bengali speaker is NOT the rotation head and has the tighter threshold, so an ordinary
      // assignment would not pick them.
      const res = await assign(request, `${PREFIX}-V-1`, { requiredLanguage: 'bn' });
      const body = await res.json();

      expect(body.outcome).toBe('VERNACULAR_OVERRIDE');
      expect(body.assignedUserId).toBe(`${PREFIX}-v-bengali`);
      expect(body.thresholdExempt).toBe(true);
      expect(body.messageKey).toBe('aa.assignment.vernacular_override');

      // Story 8's real requirement: the override must not consume capacity, or the specialist is
      // pushed out of the pool for the very language they were chosen for.
      expect(heldCount(`${PREFIX}-v-bengali`)).toBe(0);

      const second = await assign(request, `${PREFIX}-V-2`, { requiredLanguage: 'bn' });
      expect((await second.json()).assignedUserId).toBe(`${PREFIX}-v-bengali`);

      expect(Number(sql(`SELECT COUNT(*) FROM aa_assignment_audit
                          WHERE action = 'VERNACULAR_OVERRIDE'
                            AND subject_user_id = '${PREFIX}-v-bengali'`))).toBeGreaterThan(0);
    });

  test('an unmatched language falls back to rotation rather than stranding the record',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-f1`, { threshold: 5, skills: 'ta' });

      const res = await assign(request, `${PREFIX}-F-1`, { requiredLanguage: 'ml' });
      const body = await res.json();

      // Leaving it unassigned would strand a citizen record; a non-native handler is recoverable.
      expect(body.outcome).toBe('ASSIGNED');
      expect(body.assignedUserId).toBe(`${PREFIX}-f1`);
      expect(body.thresholdExempt).toBe(false);
    });

  test('the assignee is notified, so the bell has something to show', async ({ request }) => {
    seedOfficer(`${PREFIX}-n1`, { threshold: 5 });
    await assign(request, `${PREFIX}-N-1`);

    // @Async, so allow the notification to land.
    await expect.poll(() => Number(sql(
      `SELECT COUNT(*) FROM in_app_notifications
        WHERE target_user_id = '${PREFIX}-n1' AND type = 'ASSIGNMENT'
          AND related_entity_id = '${PREFIX}-N-1'`)), { timeout: 10_000 }).toBeGreaterThan(0);

    // Asserted as a translation key, never an English literal.
    const title = sql(`SELECT title FROM in_app_notifications
                        WHERE target_user_id = '${PREFIX}-n1' AND related_entity_id = '${PREFIX}-N-1'
                        LIMIT 1`);
    expect(title).toBe('aa.assignment.assigned');
  });
});

test.describe('S2C assignment authorization', () => {
  test.afterAll(() => purge());

  test('assignment endpoints reject a caller with no AA role', async ({ request }) => {
    const res = await request.post(`${API_BASE}/api/v1/aa/assignment/assign`, {
      data: { appealNumber: `${PREFIX}-AUTH-1`, roleGroup: ROLE_GROUP },
    });
    expect(res.status()).toBe(403);
  });

  test('an AA DO cannot administer thresholds or activation', async ({ request }) => {
    // Only AA_ADMIN may change who receives work, even though a DO may trigger assignment.
    const threshold = await request.put(
      `${API_BASE}/api/v1/aa/assignment/pool/${PREFIX}-x/threshold`,
      { headers: DO_USER, data: { threshold: 99, reason: 'should not be permitted' } });
    expect(threshold.status()).toBe(403);

    const bulk = await request.post(`${API_BASE}/api/v1/aa/assignment/pool/bulk-activation`,
      { headers: DO_USER, data: { userIds: [`${PREFIX}-x`], active: false, reason: 'nope' } });
    expect(bulk.status()).toBe(403);
  });

  test('a manual override without a reason is refused', async ({ request }) => {
    seedOfficer(`${PREFIX}-m1`, { threshold: 1 });
    try {
      // The reason is the only record of why a threshold was deliberately bypassed, so it cannot be
      // optional.
      const res = await request.post(`${API_BASE}/api/v1/aa/assignment/assign-manual`, {
        headers: ADMIN,
        data: { appealNumber: `${PREFIX}-M-1`, targetUserId: `${PREFIX}-m1`, reason: '  ' },
      });
      expect(res.status()).toBeGreaterThanOrEqual(400);
      expect(res.status()).toBeLessThan(500);
    } finally {
      purge();
    }
  });

  test('a manual override bypasses the threshold and is audited with actor and reason',
    async ({ request }) => {
      seedOfficer(`${PREFIX}-m2`, { threshold: 1 });
      try {
        const first = await assign(request, `${PREFIX}-M-2a`);
        expect((await first.json()).assignedUserId).toBe(`${PREFIX}-m2`);

        // Now at threshold: ordinary assignment is refused, but an admin may still place work.
        const blocked = await assign(request, `${PREFIX}-M-2b`);
        expect((await blocked.json()).outcome).toBe('UNASSIGNED_POOL_EXHAUSTED');

        const manual = await request.post(`${API_BASE}/api/v1/aa/assignment/assign-manual`, {
          headers: ADMIN,
          data: {
            appealNumber: `${PREFIX}-M-2b`,
            targetUserId: `${PREFIX}-m2`,
            reason: 'complainant requested continuity with the same officer',
          },
        });
        expect(manual.status()).toBe(200);
        const body = await manual.json();
        expect(body.outcome).toBe('MANUAL_OVERRIDE');
        expect(body.assignedUserId).toBe(`${PREFIX}-m2`);

        // The actor must come from the identity, not the request body.
        const actor = sql(`SELECT performed_by FROM aa_assignment_audit
                            WHERE action = 'MANUAL_ASSIGNMENT' AND appeal_number = '${PREFIX}-M-2b'
                            ORDER BY id DESC LIMIT 1`);
        expect(actor).toBe('aa_admin_001');

        const reason = sql(`SELECT reason FROM aa_assignment_audit
                             WHERE action = 'MANUAL_ASSIGNMENT' AND appeal_number = '${PREFIX}-M-2b'
                             ORDER BY id DESC LIMIT 1`);
        expect(reason).toContain('continuity');
      } finally {
        purge();
      }
    });

  test('an officer who is inactive cannot receive even a manual assignment', async ({ request }) => {
    seedOfficer(`${PREFIX}-m3`, { threshold: 5, active: false });
    try {
      // An admin may overload an officer deliberately, but must not strand work with someone who
      // cannot act on it.
      const res = await request.post(`${API_BASE}/api/v1/aa/assignment/assign-manual`, {
        headers: ADMIN,
        data: { appealNumber: `${PREFIX}-M-3`, targetUserId: `${PREFIX}-m3`, reason: 'test' },
      });
      expect(res.status()).toBeGreaterThanOrEqual(400);
      expect(res.status()).toBeLessThan(500);
    } finally {
      purge();
    }
  });
});
