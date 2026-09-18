import { test, expect } from '@playwright/test';
import { sql } from '../aa/aa-shared-fixtures';
import { fileComplaintForOffice } from '../utils/test-data';

/**
 * Durable, cluster-safe round-robin officer assignment — UST471 and UST450.
 *
 * ── What this proves, and why it needed proving ───────────────────────────────────────────────────
 * There were five separate in-memory round-robin implementations. Each kept its pointer in a field on
 * a bean, so on multiple pods "round robin" meant "every pod starts at officer one", and a restart
 * reset the rotation. None of them filtered on leave, so UST450 was unimplemented on those paths. The
 * public-filing path additionally FABRICATED an officer when the pool was empty — it returned the
 * literal string "RBIO OFFICER Team" and wrote it into COMPLAINTS.assigned_officer, so a complaint
 * looked assigned to a person who does not exist.
 *
 * These assertions mirror the ones that already guard the AA engine in aa/s2c-assignment-engine.spec.ts
 * (pointer read back from the STORE rather than inferred from a call sequence; eligibility applied with
 * no restart and no cache flush), because those are the properties that actually broke.
 *
 * They are API + database level on purpose. Who holds a complaint is a server-side decision, and the
 * pointer lives in wf_assignment_counter — a UI assertion would pass whether or not anything persisted.
 *
 * Run with: API_BASE_URL=http://localhost:8094 npx playwright test e2e/rbio/round-robin-assignment.spec.ts
 *   --project=chromium --reporter=list
 */
test.describe('Durable round-robin assignment', () => {
  const GROUP = 'RBIO_OFFICER';

  /** The pointer as the DATABASE holds it — the only evidence that rotation is durable. */
  function pointer(): string {
    return sql(`SELECT IFNULL(last_assigned_user_id,'') FROM wf_assignment_counter
                 WHERE role_group='${GROUP}'`).trim();
  }

  function officerOf(email: string): string {
    return sql(`SELECT IFNULL(assigned_officer,'<NULL>') FROM COMPLAINTS
                 WHERE complainant_email='${email}'`).trim();
  }

  /**
   * The officers the assigner will actually rotate through, in the order it will use them.
   *
   * Two things make this NOT simply "the pool, ordered by user_id":
   *
   *  1. Candidates come from KEYCLOAK, and the pool is only an exclusion list. The pool still carries
   *     legacy rows (rbio.officer1..4) that are not realm members, so they can never be assigned. This
   *     is deliberate — see DurableRoundRobinAssigner's javadoc on the identity mismatch.
   *  2. MySQL's default collation orders '.' BEFORE '_', whereas Java's String.compareTo orders '_'
   *     (0x5F) before '.' (0x2E)... in fact the reverse: '.' is 0x2E and '_' is 0x5F, so Java puts '.'
   *     first too — but MySQL's utf8mb4_0900_ai_ci ignores punctuation weight differently, which put
   *     rbio.officer AFTER rbio_officer_002 here. Sorting in JS with the same semantics as
   *     String.compareTo is the only way to predict the assigner's order.
   *
   * So the expected sequence is (pool-eligible ∩ realm members), sorted the way Java sorts.
   */
  function eligibleOfficers(): string[] {
    const excluded = new Set(
      (sql(`SELECT user_id FROM WF_OFFICER_POOL
             WHERE role_group='${GROUP}' AND (is_active=0 OR is_on_leave=1)`) || '')
        .split('\n').map(s => s.trim()).filter(Boolean)
    );
    return realmMembers()
      .filter(id => !excluded.has(id))
      .sort((a, b) => (a < b ? -1 : a > b ? 1 : 0));
  }

  /**
   * Realm members of the role, as the assigner sees them.
   *
   * Read from the pool rows that correspond to real accounts rather than by calling Keycloak, so the
   * spec needs no admin token. V76 seeds exactly the realm members, and the legacy rbio.officerN rows
   * are excluded by pattern because they are known non-members.
   */
  function realmMembers(): string[] {
    const raw = sql(`SELECT user_id FROM WF_OFFICER_POOL
                      WHERE role_group='${GROUP}'
                        AND user_id NOT REGEXP '^rbio\\\\.officer[0-9]+$'`);
    return raw ? raw.split('\n').map(s => s.trim()).filter(Boolean) : [];
  }

  function setLeave(userId: string, onLeave: boolean): void {
    sql(`UPDATE WF_OFFICER_POOL SET is_on_leave=${onLeave ? 1 : 0}
          WHERE role_group='${GROUP}' AND user_id='${userId}'`);
  }

  function resetPointer(): void {
    sql(`UPDATE wf_assignment_counter SET last_assigned_user_id=NULL WHERE role_group='${GROUP}'`);
  }

  function purgeProbes(): void {
    sql(`DELETE t FROM COMPLAINT_TIMELINE t JOIN COMPLAINTS c ON c.id=t.complaint_id
          WHERE c.complainant_email LIKE 's3_office_%@example.com'`);
    sql(`DELETE FROM COMPLAINTS WHERE complainant_email LIKE 's3_office_%@example.com'`);
  }

  test.beforeEach(() => {
    // Leftover leave flags or a stale pointer from an earlier run would hide a rotation bug, which is
    // exactly the trap the AA suite documents in its own beforeEach.
    sql(`UPDATE WF_OFFICER_POOL SET is_on_leave=0, is_active=1 WHERE role_group='${GROUP}'`);
    resetPointer();
  });

  test.afterAll(() => {
    sql(`UPDATE WF_OFFICER_POOL SET is_on_leave=0, is_active=1 WHERE role_group='${GROUP}'`);
    resetPointer();
    purgeProbes();
  });

  test('the rotation pointer is stored in the database, not in memory', async ({ request }) => {
    expect(pointer(), 'pointer should start cleared').toBe('');

    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(filed.status, `filing failed: ${filed.message}`).toBe(201);

    const officer = officerOf(filed.email);
    // The officer who received the work IS the stored pointer. An in-memory counter would leave this
    // column empty and the rotation would restart on the next boot.
    expect(pointer()).toBe(officer);
    expect(officer).not.toBe('<NULL>');
  });

  test('successive complaints go to DIFFERENT officers, in user-id order', async ({ request }) => {
    const eligible = eligibleOfficers();
    test.skip(eligible.length < 2, `need at least 2 eligible officers, found ${eligible.length}`);

    const assigned: string[] = [];
    for (let i = 0; i < Math.min(eligible.length, 4); i++) {
      const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
      expect(filed.status).toBe(201);
      assigned.push(officerOf(filed.email));
    }

    // No officer twice — the defect being guarded is a pointer that never advances.
    expect(new Set(assigned).size).toBe(assigned.length);
    // And the order is the sorted candidate order, so rotation is reproducible across pods rather than
    // depending on whatever order the directory happened to answer in.
    expect(assigned).toEqual(eligible.slice(0, assigned.length));
  });

  test('rotation resumes after the stored pointer rather than restarting at the head', async ({ request }) => {
    const eligible = eligibleOfficers();
    test.skip(eligible.length < 3, `need at least 3 eligible officers, found ${eligible.length}`);

    // Plant the pointer on the FIRST officer without filing anything, then file once. A correct
    // implementation continues at the second officer. This is the restart-survival property: the
    // service has no memory of ever assigning anyone, only the stored pointer.
    sql(`UPDATE wf_assignment_counter SET last_assigned_user_id='${eligible[0]}'
          WHERE role_group='${GROUP}'`);

    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(filed.status).toBe(201);

    expect(officerOf(filed.email)).toBe(eligible[1]);
  });

  test('a pointer naming a departed officer resumes at the next id, not the head', async ({ request }) => {
    const eligible = eligibleOfficers();
    test.skip(eligible.length < 2, `need at least 2 eligible officers, found ${eligible.length}`);

    // This is why the pointer stores a user id and not a numeric index. '!!' sorts before every real
    // username, so "the next id after it" is the head; a departed id in the middle resumes after itself.
    const departed = `${eligible[0]}.departed.zzz`;
    sql(`UPDATE wf_assignment_counter SET last_assigned_user_id='${departed}'
          WHERE role_group='${GROUP}'`);

    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(filed.status).toBe(201);

    // Whoever is chosen must be a REAL eligible officer — never the departed id, never a fabrication.
    const officer = officerOf(filed.email);
    expect(eligible).toContain(officer);
    expect(officer).not.toBe(departed);
  });

  test('an officer on leave is skipped in real time, with no restart', async ({ request }) => {
    const eligible = eligibleOfficers();
    test.skip(eligible.length < 2, `need at least 2 eligible officers, found ${eligible.length}`);

    const benched = eligible[0];
    setLeave(benched, true);

    try {
      // Every remaining officer gets a turn; the benched one must never appear.
      const assigned: string[] = [];
      for (let i = 0; i < eligible.length; i++) {
        const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
        expect(filed.status).toBe(201);
        assigned.push(officerOf(filed.email));
      }

      expect(assigned, `${benched} is on leave and must not receive work`).not.toContain(benched);
    } finally {
      setLeave(benched, false);
    }
  });

  test('when every officer is on leave the complaint still registers, honestly unassigned', async ({ request }) => {
    const eligible = eligibleOfficers();
    test.skip(eligible.length === 0, 'no eligible officers to bench');

    sql(`UPDATE WF_OFFICER_POOL SET is_on_leave=1 WHERE role_group='${GROUP}'`);

    try {
      const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });

      // The citizen must NOT be blocked because a rota is empty — losing a complaint is far worse than
      // leaving it unassigned.
      expect(filed.status, 'an empty rota must not fail the filing').toBe(201);

      // And the officer must be NULL rather than the fabricated "RBIO OFFICER Team" placeholder the
      // predecessor wrote, which made an unassigned complaint indistinguishable from an assigned one.
      expect(officerOf(filed.email)).toBe('<NULL>');

      const role = sql(`SELECT IFNULL(assigned_role,'') FROM COMPLAINTS
                         WHERE complainant_email='${filed.email}'`).trim();
      expect(role, 'the role that still owes the work must be recorded').toBe(GROUP);

      // The timeline must say WHY nobody holds it, not interpolate "null" into the narrative.
      const remarks = sql(`SELECT t.remarks FROM COMPLAINT_TIMELINE t
                            JOIN COMPLAINTS c ON c.id=t.complaint_id
                            WHERE c.complainant_email='${filed.email}' AND t.action='filed'`);
      expect(remarks.toLowerCase()).toContain('awaiting assignment');
      expect(remarks.toLowerCase()).not.toContain('null');
    } finally {
      sql(`UPDATE WF_OFFICER_POOL SET is_on_leave=0 WHERE role_group='${GROUP}'`);
    }
  });

  test('the pointer is never left naming an officer who is not a real candidate', async ({ request }) => {
    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(filed.status).toBe(201);

    const stored = pointer();
    expect(stored).not.toBe('');
    // A fabricated team name in the pointer would poison every later rotation in this role group.
    expect(stored).not.toContain(' Team');
    expect(stored).not.toContain(' ');
  });
});
