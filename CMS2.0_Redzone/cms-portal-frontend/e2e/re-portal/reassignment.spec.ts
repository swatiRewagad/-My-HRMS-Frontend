import { test, expect, APIRequestContext } from '../fixtures';
import {
  API_BASE,
  REASSIGN,
  REPORT,
  RE_NODAL,
  RE_PNO,
  ADMIN,
  devHeaders,
  getWithAuth,
  postWithAuth,
  probeAuth,
} from './reassignment-auth';
import {
  ENTITY,
  OTHER_ENTITY,
  OFFICER,
  RECORD,
  EXPECTED_WORKLOAD,
  EXPECTED_TOTAL_ACTIVE,
  SEEDED_RECORDS_IN_ENTITY,
  SeededRecord,
  seed,
  purge,
  readSeededRecords,
  recordFor,
  versionOf,
  ownerOf,
} from './reassignment-seed';

/**
 * RE nodal officer reassignment (UST838–UST845).
 *
 * API-level rather than browser-level, deliberately. The `cms` realm on 9090 contains no RE_* roles
 * and no RE users at all, so a browser login cannot reach the RE portal: signing in as cms.admin
 * yields no entity_code, the entity-scoped endpoints refuse the call, and every element assertion
 * then fails for a reason unrelated to the code under test. Seeding RE users into the realm is the
 * prerequisite for UI coverage of this feature.
 *
 * SUBJECT MATTER IS SEEDED, NOT DISCOVERED. This suite used to probe four hardcoded entity names for
 * a pre-existing officer directory and, on finding none, `test.skip` 26 of its 30 tests. Both tables
 * it depends on (ENTITY_USERS, NODAL_OFFICER_RECORDS) are empty in cms_db, so the entire feature
 * reported green having executed nothing. Everything now comes from reassignment-seed.ts, prefixed
 * 'S4-' and purged by prefix, and a missing fixture FAILS — a skip over absent data is
 * indistinguishable from a pass over broken code.
 *
 * Identity goes through the self-healing helper, so real JWT enforcement landing mid-run costs one
 * retry rather than a false failure.
 */

let records: SeededRecord[] = [];

const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

/** Keys the API can actually return or the UI renders — asserted in every locale. */
const REQUIRED_KEYS = [
  're.reassign.title',
  're.reassign.candidate_label',
  're.reassign.workload_label',
  're.reassign.reason_label',
  're.reassign.reason_immutable_notice',
  're.reassign.clarification_title',
  're.reassign.my_requests_title',
  're.reassign.approvals_title',
  're.reassign.history_title',
  're.pno.dashboard_title',
  're.pno.workload_definition_notice',
  'notification.reassign.in',
  'notification.reassign.out',
  're.reassign.error.conflict',
  're.reassign.error.cross_entity',
  're.reassign.error.pno_only',
  're.reassign.error.reason_too_short',
  're.reassign.error.target_inactive',
  're.reassign.error.bulk_limit_exceeded',
  're.reassign.status.pending',
];

function uniqueReason(label: string): string {
  return `${label} — e2e run ${Date.now()}`;
}

test.beforeAll(async ({ request }) => {
  // Asserted, not logged. 'none' means neither dev headers nor a minted token is accepted, i.e. a
  // total auth outage — every subsequent test would fail for a reason unrelated to reassignment, and
  // the previous console.log let that outage read as 30 unexplained failures.
  const mode = await probeAuth(request, ENTITY);
  expect(
    mode,
    `no identity is accepted by ${API_BASE} — neither dev headers nor a Keycloak token. ` +
      'Start the backend with the dev-local profile, or check Keycloak on 9090.'
  ).not.toBe('none');

  records = seed();
  expect(
    records.length,
    'the S4- fixture directory and records must be seeded into cms_db before this suite runs'
  ).toBe(6);

  // The seed is worthless if the API cannot see it, and that failure mode (entity-code mismatch,
  // wrong table, cached read) is exactly what used to hide behind `if (candidates.length > 0)`.
  const probe = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY));
  expect(probe.status(), `candidates for the seeded entity ${ENTITY}`).toBe(200);
  const seenCandidates = (await probe.json()).candidates as Array<Record<string, unknown>>;
  expect(
    seenCandidates.length,
    `the seeded ENTITY_USERS rows for ${ENTITY} are not visible through the API`
  ).toBeGreaterThan(0);
});

test.afterAll(() => {
  purge();
});

test.describe('UST838 — workload formula', () => {
  test('popup and dashboard report the same workload for the same officer', async ({ request }) => {
    const candidatesRes = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY));
    expect(candidatesRes.status()).toBe(200);
    const candidates = (await candidatesRes.json()).candidates as Array<Record<string, unknown>>;
    expect(candidates.length).toBeGreaterThan(0);

    const workloadRes = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(ENTITY));
    expect(workloadRes.status()).toBe(200);
    const officers = (await workloadRes.json()).officers as Array<Record<string, unknown>>;

    const dashboard = new Map(officers.map((o) => [o['userId'] as string, o['workload'] as number]));

    // The story requires these two surfaces to agree. They share one service method, and this is the
    // assertion that would catch a future caller re-implementing the count.
    for (const candidate of candidates) {
      const userId = candidate['userId'] as string;
      expect(dashboard.has(userId), `${userId} missing from dashboard workload`).toBeTruthy();
      expect(candidate['workload'], `workload disagrees for ${userId}`).toBe(dashboard.get(userId));
    }
  });

  test('workload excludes closed records', async ({ request }) => {
    const res = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(ENTITY));
    expect(res.status()).toBe(200);
    const body = await res.json();

    // Exact, from the seed: 3 open records against 5 seeded in the entity. The previous
    // `toBeGreaterThanOrEqual(0)` is true of every conceivable count, including one produced by an
    // exclusion filter that had stopped working entirely.
    const total = body.totalActiveRecords as number;
    expect(total, 'only the three non-closed seeded records may be counted').toBe(
      EXPECTED_TOTAL_ACTIVE
    );
    expect(
      total,
      'counting all seeded records would mean CLOSED/RESOLVED are no longer excluded'
    ).toBeLessThan(SEEDED_RECORDS_IN_ENTITY);

    const officers = body.officers as Array<Record<string, unknown>>;
    const summed = officers.reduce((acc, o) => acc + (o['workload'] as number), 0);
    expect(summed).toBe(total);
  });

  test('an officer with no records is still listed, with zero', async ({ request }) => {
    const res = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(ENTITY));
    const officers = (await res.json()).officers as Array<Record<string, unknown>>;
    const byUser = new Map(officers.map((o) => [o['userId'] as string, o['workload'] as number]));

    // Named officers with exact figures. `officers.some(o => o.workload === 0)` passed on any pool
    // containing a single idle person and proved nothing about the seeded one.
    for (const [userId, expected] of Object.entries(EXPECTED_WORKLOAD)) {
      expect(byUser.has(userId), `${userId} is absent from the dashboard entirely`).toBeTruthy();
      expect(byUser.get(userId), `workload for ${userId}`).toBe(expected);
    }
    // A GROUP BY alone cannot produce a row for an idle officer; the dashboard must still show them.
    expect(byUser.get(OFFICER.idle), 'the deliberately idle officer must appear with zero').toBe(0);
  });
});

test.describe('UST841 — candidate filtering', () => {
  test('inactive officers are never offered as a target', async ({ request }) => {
    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY));
    const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;

    // The seed includes a deactivated "Former Officer" precisely so this assertion can fail if the
    // active filter is dropped.
    expect(candidates.some((c) => (c['displayName'] as string) === 'Former Officer')).toBeFalsy();
    expect(candidates.some((c) => (c['userId'] as string) === OFFICER.left)).toBeFalsy();
    // And the active ones ARE there, so an empty list cannot satisfy the assertion above.
    expect(candidates.some((c) => (c['userId'] as string) === OFFICER.no1)).toBeTruthy();
  });

  test('role filter narrows the list server-side', async ({ request }) => {
    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY), {
      role: 'CONTACT_PERSON',
    });
    expect(res.status()).toBe(200);
    const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;
    expect(candidates.length).toBeGreaterThan(0);
    for (const c of candidates) {
      expect(c['reRole']).toBe('CONTACT_PERSON');
    }
    // The seeded contact person is the one that must come back, and the nodal officers must not.
    expect(candidates.map((c) => c['userId'])).toContain(OFFICER.cp1);
    expect(candidates.map((c) => c['userId'])).not.toContain(OFFICER.no1);
  });

  test('search matches on name, and returns nothing for an absent term', async ({ request }) => {
    const hit = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY), {
      search: 'Nodal Officer One',
    });
    const hits = (await hit.json()).candidates as Array<Record<string, unknown>>;
    expect(hits.length).toBeGreaterThan(0);
    expect(hits.map((c) => c['userId'])).toContain(OFFICER.no1);

    const miss = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY), {
      search: 'zzz-no-such-officer-zzz',
    });
    expect(((await miss.json()).candidates as unknown[]).length).toBe(0);
  });

  test('candidates from another entity are never visible', async ({ request }) => {
    // The seed puts a real, active officer in OTHER_ENTITY. Without one, an entity-scoping test has
    // nothing to leak and passes on any implementation.
    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(ENTITY));
    const ids = ((await res.json()).candidates as Array<Record<string, unknown>>).map(
      (c) => c['userId']
    );
    expect(ids).not.toContain(OFFICER.otherEntity);

    // And the other entity sees its own officer, proving the row exists and is active.
    const other = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(OTHER_ENTITY));
    const otherIds = ((await other.json()).candidates as Array<Record<string, unknown>>).map(
      (c) => c['userId']
    );
    expect(otherIds).toContain(OFFICER.otherEntity);
  });
});

test.describe('authorization — enforced server-side, not in the browser', () => {
  test('no identity is rejected', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/candidates`, { failOnStatusCode: false });
    expect(res.status()).toBe(401);
  });

  test('an RE caller cannot name another entity', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/candidates`, {
      headers: devHeaders(RE_NODAL(ENTITY)),
      params: { entityCode: OTHER_ENTITY },
      failOnStatusCode: false,
    });
    // Refused outright rather than silently ignored: ignoring it would let a caller believe they had
    // scoped a query that they had not.
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.cross_entity');
  });

  test('an RE caller with no entity code is refused, not defaulted', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/candidates`, {
      headers: devHeaders({ userId: 'no_entity_user', roles: 'RE_NODAL_OFFICER' }),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.entity_unresolved');
  });

  test('an ADMIN must name an entity rather than defaulting to all', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/candidates`, {
      headers: devHeaders(ADMIN()),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.entity_required');
  });

  test('a plain nodal officer cannot read the PNO approval queue', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/requests/pending`, {
      headers: devHeaders(RE_NODAL(ENTITY)),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.pno_only');
  });

  test('a plain nodal officer cannot decide requests', async ({ request }) => {
    const res = await request.post(`${REASSIGN}/requests/decide`, {
      headers: devHeaders(RE_NODAL(ENTITY)),
      data: { approve: true, items: [{ requestId: 1 }] },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.pno_only');
  });

  test('an RE caller cannot act on another entity record', async ({ request }) => {
    // The seeded OTHER_ENTITY record is a real row with a real id, so a missing scope check has
    // something to actually move rather than merely 404.
    const foreign = recordFor(records, RECORD.otherEntity);
    // Raw request, NOT postWithAuth. postWithAuth treats any 403 as "enforcement just landed", mints
    // a cms.admin bearer token and retries — so a correct 403 is replaced by whatever cms.admin gets
    // (here a 400 entity_required, because an ADMIN must name an entity). Every test in this suite
    // that asserts a refusal therefore has to bypass the self-healing helper, or the helper answers
    // the second call and the assertion describes the wrong caller.
    const res = await request.post(`${REASSIGN}/requests`, {
      headers: devHeaders(RE_NODAL(ENTITY)),
      data: {
        recordId: foreign.id,
        toUserId: OFFICER.no1,
        reason: uniqueReason('Cross-entity write check'),
      },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.cross_entity');
    // And it did not move.
    expect(ownerOf(foreign.id)).toBe(OFFICER.otherEntity);
  });
});

test.describe('UST840 — immutable reason, append-only clarifications', () => {
  test('a reason below the configured minimum is refused', async ({ request }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open1);

    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
      recordId: subject.id,
      toUserId: OFFICER.no2,
      reason: 'short',
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.reason_too_short');
    // No request was written, so nothing downstream can act on it.
    expect(await pendingCountFor(request, subject.id)).toBe(0);
  });

  test('an empty clarification is refused', async ({ request }) => {
    const requestId = await raiseRequestOn(request, RECORD.open2, OFFICER.no2);

    const res = await postWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(ENTITY),
      { note: '   ' }
    );
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.clarification_required');
  });

  test('a clarification is appended with server-resolved attribution', async ({ request }) => {
    const requestId = await raiseRequestOn(request, RECORD.open2, OFFICER.no2);

    const note = `Clarifying context ${Date.now()}`;
    const created = await postWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(ENTITY),
      // addedBy is deliberately NOT sent; attribution must come from the resolved identity.
      { note, addedBy: 'someone_else_entirely' }
    );
    expect(created.status()).toBe(201);
    const clarification = (await created.json()).clarification;
    expect(clarification.note).toBe(note);
    expect(clarification.addedBy).toBe('re_e2e_nodal');
    expect(clarification.addedBy).not.toBe('someone_else_entirely');
    expect(clarification.addedBySide).toBe('RE');

    const list = await getWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(ENTITY)
    );
    const notes = ((await list.json()).clarifications as Array<Record<string, unknown>>).map(
      (c) => c['note']
    );
    expect(notes).toContain(note);
  });

  test('the original reason is unchanged after a clarification is added', async ({ request }) => {
    const reason = uniqueReason('Immutable reason subject');
    const requestId = await raiseRequestOn(request, RECORD.open2, OFFICER.no2, reason);

    await postWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(ENTITY),
      { note: `Immutability check ${Date.now()}` }
    );

    const after = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(ENTITY));
    const reloaded = ((await after.json()).requests as Array<Record<string, unknown>>).find(
      (r) => r['id'] === requestId
    );
    expect(reloaded, `request ${requestId} must still be listed in My Requests`).toBeTruthy();
    expect(reloaded!['reason']).toBe(reason);
  });
});

test.describe('UST839 — optimistic locking and conflict-safe bulk actions', () => {
  test('a stale version is refused with a conflict naming the record', async ({ request }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open1);
    const ownerBefore = ownerOf(subject.id);

    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(ENTITY), {
      toUserId: OFFICER.idle,
      reason: uniqueReason('Stale version check'),
      items: [{ recordId: subject.id, expectedVersion: 99999 }],
    });
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.succeededCount).toBe(0);
    expect(body.failedCount).toBe(1);
    expect(body.failed[0].id).toBe(subject.id);
    // The key matters more than the prose: the UI renders it per locale.
    expect(body.failed[0].messageKey).toBe('re.reassign.error.conflict');
    // And the detail must name the record, so a user can tell which selection failed.
    expect(String(body.failed[0].detail)).toContain(String(subject.id));
    // The refusal has to have actually prevented the write. The seed sets version=0 rather than NULL
    // precisely so this check is meaningful: a NULL version makes the executor skip the comparison.
    expect(ownerOf(subject.id), 'a conflicting move must not have been applied').toBe(ownerBefore);
  });

  test('one conflicting record does not roll back the rest of the batch', async ({ request }) => {
    const movable = recordFor(readSeededRecords(), RECORD.open3);
    // A target that does not already own it, so the move is a genuine change of hands.
    const target = movable.assignedTo === OFFICER.idle ? OFFICER.cp1 : OFFICER.idle;

    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(ENTITY), {
      toUserId: target,
      reason: uniqueReason('Partial batch check'),
      items: [
        // Valid: no expectedVersion, so no stale-read protection is claimed.
        { recordId: movable.id },
        // Invalid: a record id that cannot exist, so this item must fail on its own.
        { recordId: 999999999 },
      ],
    });
    expect(res.status()).toBe(200);
    const body = await res.json();

    // The first item commits; the second fails. This is the assertion that catches a surrounding
    // transaction being reintroduced — with one, the successful item would be discarded at commit and
    // succeededCount would be 0.
    expect(body.succeededCount).toBe(1);
    expect(body.failedCount).toBe(1);
    expect(body.succeeded).toContain(movable.id);
    expect(body.failed[0].id).toBe(999999999);
    // Read back from the database, not from the response the service wrote about itself. A
    // rollback-only transaction returns exactly the body above and still discards the row.
    expect(ownerOf(movable.id), 'the surviving item must genuinely have survived commit').toBe(
      target
    );
  });

  test('a batch over the configured maximum is refused before anything is applied', async ({
    request,
  }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open1);
    const ownerBefore = ownerOf(subject.id);

    // The real record is put FIRST in the oversized batch, so "refused before anything is applied"
    // is verifiable rather than asserted only through the status code.
    const items = [
      { recordId: subject.id },
      ...Array.from({ length: 4999 }, (_, i) => ({ recordId: i + 1 })),
    ];
    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(ENTITY), {
      toUserId: OFFICER.idle,
      reason: uniqueReason('Bulk limit check'),
      items,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.bulk_limit_exceeded');
    expect(ownerOf(subject.id), 'nothing in an over-limit batch may be applied').toBe(ownerBefore);
  });

  test('an empty selection is refused', async ({ request }) => {
    const res = await postWithAuth(request, `${REASSIGN}/requests/decide`, RE_PNO(ENTITY), {
      approve: true,
      items: [],
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.nothing_selected');
  });

  test('rejecting without a comment is refused', async ({ request }) => {
    const requestId = await raiseRequestOn(request, RECORD.open2, OFFICER.no2);

    const res = await postWithAuth(request, `${REASSIGN}/requests/decide`, RE_PNO(ENTITY), {
      approve: false,
      items: [{ requestId }],
      comment: '   ',
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.rejection_comment_required');

    // The request must still be PENDING: a refused rejection that quietly closed it anyway would
    // strand the requester.
    const mine = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(ENTITY));
    const row = ((await mine.json()).requests as Array<Record<string, unknown>>).find(
      (r) => r['id'] === requestId
    );
    expect(row!['status']).toBe('PENDING');
  });
});

test.describe('UST842/UST843 — request lifecycle', () => {
  test('a request can be raised, appears in My Requests, and blocks a duplicate', async ({
    request,
  }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open1);
    const target = subject.assignedTo === OFFICER.no2 ? OFFICER.idle : OFFICER.no2;

    const reason = uniqueReason('Lifecycle check');
    const created = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
      recordId: subject.id,
      toUserId: target,
      reason,
    });
    // Fixtures are seeded fresh in beforeAll, so a 409 here is the one-pending-per-record rule firing
    // against a request THIS run created — a real defect, not a leftover to skip over.
    expect(created.status(), await created.text()).toBe(201);
    const requestId = (await created.json()).request.id;

    const mine = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(ENTITY));
    const ids = ((await mine.json()).requests as Array<Record<string, unknown>>).map((r) => r['id']);
    expect(ids).toContain(requestId);

    const duplicate = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
      recordId: subject.id,
      toUserId: target,
      reason: uniqueReason('Duplicate check'),
    });
    expect(duplicate.status()).toBe(409);
    expect((await duplicate.json()).messageKey).toBe('re.reassign.error.already_pending');
  });

  test('reassigning to the record current owner is refused', async ({ request }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open3);
    const owner = subject.assignedTo;

    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
      recordId: subject.id,
      // Deliberately the current owner: a move to the person who already holds it is not a move,
      // and allowing it would write a history row recording a change that did not happen.
      toUserId: owner,
      reason: uniqueReason('Same owner check'),
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.already_assigned');
  });

  test('an inactive target is refused', async ({ request }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open1);

    // The deactivated officer is a named fixture, not derived by regex from an active id. The old
    // string-replace silently `test.skip`ped the whole control the moment a naming convention moved.
    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
      recordId: subject.id,
      toUserId: OFFICER.left,
      reason: uniqueReason('Inactive target check'),
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.target_inactive');
  });

  test('an approved request moves the record and writes history', async ({ request }) => {
    const subject = recordFor(readSeededRecords(), RECORD.open2);
    const target = subject.assignedTo === OFFICER.cp1 ? OFFICER.idle : OFFICER.cp1;
    const reason = uniqueReason('Approval applies the move');

    const requestId = await raiseRequestOn(request, RECORD.open2, target, reason);

    const decide = await postWithAuth(request, `${REASSIGN}/requests/decide`, RE_PNO(ENTITY), {
      approve: true,
      items: [{ requestId }],
      comment: 'E2E approval',
    });
    expect(decide.status()).toBe(200);
    const body = await decide.json();
    expect(body.succeededCount, JSON.stringify(body.failed)).toBe(1);

    // Persisted ownership, read from the table. The API returns 200 with success:true on refusal
    // paths elsewhere in this codebase, so the response alone proves nothing about the write.
    expect(ownerOf(subject.id)).toBe(target);

    const history = await getWithAuth(request, REPORT, RE_PNO(ENTITY));
    const rows = (await history.json()).history as Array<Record<string, unknown>>;
    const row = rows.find((h) => h['recordId'] === subject.id && h['reason'] === reason);
    expect(row, 'an applied move must leave a history row carrying its reason').toBeTruthy();
    expect(row!['toUserId']).toBe(target);
    expect(row!['triggerType']).toBe('APPROVED_REQUEST');
  });
});

test.describe('UST844 — reassignment history report', () => {
  test('history is entity-scoped and readable', async ({ request }) => {
    // A move is performed first, so the loop below has rows to examine. An empty history satisfied
    // every assertion in the previous version of this test.
    await performDirectMove(request, RECORD.open1);

    const res = await getWithAuth(request, REPORT, RE_PNO(ENTITY));
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.history)).toBeTruthy();
    expect(
      (body.history as unknown[]).length,
      'the move just performed must appear in the report'
    ).toBeGreaterThan(0);

    for (const row of body.history as Array<Record<string, unknown>>) {
      // A report is as much an information-disclosure surface as a detail page.
      expect(row['entityCode']).toBe(ENTITY);
      expect(row['toUserId']).toBeTruthy();
      expect(['APPROVED_REQUEST', 'DIRECT']).toContain(row['triggerType']);
    }
    // And the other entity's move is not in this entity's report.
    const complaintNumbers = (body.history as Array<Record<string, unknown>>).map(
      (r) => r['complaintNumber']
    );
    expect(complaintNumbers).not.toContain(RECORD.otherEntity);
  });

  test('an RE caller cannot read another entity history', async ({ request }) => {
    const res = await request.get(REPORT, {
      headers: devHeaders(RE_PNO(ENTITY)),
      params: { entityCode: OTHER_ENTITY },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.cross_entity');
  });

  test('summary inbound and outbound totals agree with the row count', async ({ request }) => {
    await performDirectMove(request, RECORD.open3);

    const res = await getWithAuth(request, `${REPORT}/summary`, RE_PNO(ENTITY));
    expect(res.status()).toBe(200);
    const body = await res.json();

    const inbound = (body.inbound as Array<Record<string, unknown>>).reduce(
      (acc, r) => acc + (r['count'] as number),
      0
    );
    const outbound = (body.outbound as Array<Record<string, unknown>>).reduce(
      (acc, r) => acc + (r['count'] as number),
      0
    );
    // Non-zero, or the inequality below holds trivially on an empty report.
    expect(inbound, 'every seeded move has a recipient, so inbound cannot be zero').toBeGreaterThan(
      0
    );
    // Every move has exactly one recipient; only moves from a known officer have a source, so
    // outbound can legitimately be lower but never higher.
    expect(outbound).toBeLessThanOrEqual(inbound);
    // Every seeded record already had an owner, so each move has a source too.
    expect(outbound).toBe(inbound);
  });

  test('an invalid date is refused rather than silently ignored', async ({ request }) => {
    const res = await request.get(REPORT, {
      headers: devHeaders(RE_PNO(ENTITY)),
      params: { from: 'not-a-date' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.invalid_date');
  });
});

test.describe('UST845 — notification delivery log', () => {
  test('the delivery log is ADMIN-only', async ({ request }) => {
    const res = await request.get(`${REPORT}/delivery-log`, {
      headers: devHeaders(RE_NODAL(ENTITY)),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });

  test('reassign-in and reassign-out attempts are both logged', async ({ request }) => {
    // A move of our own, so the log has something in it that this test caused. Previously the test
    // read whatever happened to be there and, on an EMPTY log, passed — which is precisely the
    // "notifications were never delivered" bug it exists to catch.
    const moved = await performDirectMove(request, RECORD.open1);

    // Delivery is written inside the executor's transaction, but the surrounding commit is not
    // observable from here, so the read is polled rather than assumed.
    const deliveriesFor = async () => {
      const res = await getWithAuth(request, `${REPORT}/delivery-log`, ADMIN(), {
        complaintNumber: moved.complaintNumber,
      });
      // A 403/500 is a defect in an ADMIN-only diagnostic surface, not a reason to skip.
      expect(res.status(), 'the delivery log must be readable by an ADMIN').toBe(200);
      return (await res.json()).deliveries as Array<Record<string, unknown>>;
    };

    await expect
      .poll(async () => (await deliveriesFor()).map((d) => d['notificationType'] as string), {
        timeout: 10000,
      })
      .toEqual(
        expect.arrayContaining(['notification.reassign.in', 'notification.reassign.out'])
      );

    const deliveries = await deliveriesFor();
    // Both halves of the pair must appear; logging only the recipient would leave the officer who
    // lost the record with no record of having been told. Unconditional: an empty log is a failure.
    const types = deliveries.map((d) => d['notificationType'] as string);
    expect(types).toContain('notification.reassign.in');
    expect(types).toContain('notification.reassign.out');

    for (const delivery of deliveries) {
      expect(['SENT', 'FAILED']).toContain(delivery['status']);
      expect(delivery['channel']).toBe('IN_APP');
      expect(delivery['recipientUserId']).toBeTruthy();
      expect(delivery['relatedComplaintNumber']).toBe(moved.complaintNumber);
    }

    // The recipient of the move must be the recipient of the reassign-in row. There is no SMS/email
    // capability in this system, so a persisted delivery row is the only assertable evidence.
    const inbound = deliveries.find(
      (d) => d['notificationType'] === 'notification.reassign.in'
    );
    expect(inbound!['recipientUserId']).toBe(moved.toUserId);
  });
});

test.describe('i18n — all ten locales', () => {
  test('every reassignment key resolves in every locale', async ({ request }) => {
    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`, {
        failOnStatusCode: false,
      });
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const translations: Record<string, string> = body.data ?? body;

      for (const key of REQUIRED_KEYS) {
        expect(translations[key], `${key} missing for ${locale}`).toBeTruthy();
      }
    }
  });

  test('non-English text is genuinely translated, not English fallback', async ({ request }) => {
    const enRes = await request.get(`${API_BASE}/api/v1/i18n/translations/en`);
    const enBody = await enRes.json();
    const en: Record<string, string> = enBody.data ?? enBody;

    for (const locale of LOCALES.filter((l) => l !== 'en')) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;

      for (const key of REQUIRED_KEYS) {
        // Catches the defaultValue-fallback trap: a key present but untranslated would pass a
        // presence-only check while showing English to a Hindi reader.
        expect(t[key], `${key} for ${locale} is identical to the English text`).not.toBe(en[key]);
      }
    }
  });
});

// ═══════════════════════════════════════════════════════════════
// Helpers — all operate on the seeded fixture, and throw rather than skip
// ═══════════════════════════════════════════════════════════════

/**
 * Raises a request on a seeded record, withdrawing any request left pending on it first.
 *
 * The one-pending-per-record rule is a real constraint, so a test that needs a fresh PENDING request
 * has to clear the previous one. The alternative the old suite used — `test.skip` on 409 — turned the
 * constraint into a reason to stop testing.
 */
async function raiseRequestOn(
  request: APIRequestContext,
  complaintNumber: string,
  toUserId: string,
  reason = uniqueReason('Fixture request')
): Promise<number> {
  const subject = recordFor(readSeededRecords(), complaintNumber);
  await clearPending(request, subject.id);

  const target = subject.assignedTo === toUserId ? OFFICER.idle : toUserId;
  const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(ENTITY), {
    recordId: subject.id,
    toUserId: target,
    reason,
  });
  expect(res.status(), `raising a fixture request on ${complaintNumber}: ${await res.text()}`).toBe(
    201
  );
  return (await res.json()).request.id as number;
}

/** Withdraws every PENDING request on a record, so a fresh one can be raised. */
async function clearPending(request: APIRequestContext, recordId: number): Promise<void> {
  const mine = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(ENTITY), {
    status: 'PENDING',
    size: 100,
  });
  if (!mine.ok()) return;
  for (const row of (await mine.json()).requests as Array<Record<string, unknown>>) {
    if (row['recordId'] !== recordId) continue;
    await postWithAuth(
      request,
      `${REASSIGN}/requests/${row['id']}/withdraw`,
      RE_NODAL(ENTITY),
      { note: 'E2E fixture reset' }
    );
  }
}

/** How many PENDING requests exist against a record, per the PNO queue. */
async function pendingCountFor(request: APIRequestContext, recordId: number): Promise<number> {
  const res = await getWithAuth(request, `${REASSIGN}/requests/pending`, RE_PNO(ENTITY), {
    size: 100,
  });
  expect(res.status()).toBe(200);
  return ((await res.json()).requests as Array<Record<string, unknown>>).filter(
    (r) => r['recordId'] === recordId
  ).length;
}

/**
 * Performs a direct PNO move on a seeded record and asserts it landed.
 *
 * Returns the record and its new owner so a caller can assert on the consequences (history rows,
 * delivery-log rows) rather than on whatever pre-existing data happened to be present.
 */
async function performDirectMove(
  request: APIRequestContext,
  complaintNumber: string
): Promise<{ recordId: number; complaintNumber: string; toUserId: string }> {
  const subject = recordFor(readSeededRecords(), complaintNumber);
  const rotation = [OFFICER.no1, OFFICER.no2, OFFICER.idle, OFFICER.cp1];
  const toUserId = rotation.find((u) => u !== subject.assignedTo)!;

  const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(ENTITY), {
    toUserId,
    reason: uniqueReason(`Direct move of ${complaintNumber}`),
    items: [{ recordId: subject.id, expectedVersion: versionOf(subject.id) }],
  });
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.succeededCount, `direct move failed: ${JSON.stringify(body.failed)}`).toBe(1);
  expect(ownerOf(subject.id)).toBe(toUserId);

  return { recordId: subject.id, complaintNumber: subject.complaintNumber, toUserId };
}
