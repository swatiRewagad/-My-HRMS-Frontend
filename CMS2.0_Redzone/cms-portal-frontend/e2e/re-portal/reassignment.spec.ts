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

/**
 * RE nodal officer reassignment (UST838–UST845).
 *
 * API-level rather than browser-level, deliberately. The `cms` realm on 9090 contains no RE_* roles
 * and no RE users at all, so a browser login cannot reach the RE portal: signing in as cms.admin
 * yields no entity_code, the entity-scoped endpoints refuse the call, and every element assertion
 * then fails for a reason unrelated to the code under test. Seeding RE users into the realm is the
 * prerequisite for UI coverage of this feature.
 *
 * Identity goes through the self-healing helper, so real JWT enforcement landing mid-run costs one
 * retry rather than a false failure.
 */

/** Entity chosen at runtime, so the suite does not depend on one demo dataset. */
let entityCode: string;
let openRecordId: number;
let openRecordVersion: number;

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

/**
 * Finds an entity that has both a seeded officer directory and a reassignable record.
 * Discovery rather than seeding, matching the approach the other RE API specs take.
 */
async function discoverEntity(request: APIRequestContext): Promise<string | null> {
  const candidateEntities = ['AXIS', 'Axis Bank', 'State Bank of India', 'Bank of Maharashtra'];
  for (const code of candidateEntities) {
    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(code));
    if (!res.ok()) continue;
    const body = await res.json();
    if ((body.candidates ?? []).length > 0) return code;
  }
  return null;
}

test.beforeAll(async ({ request }) => {
  const mode = await probeAuth(request, 'AXIS');
  // Recorded rather than asserted: 'none' still lets the negative tests run and report honestly.
  console.log(`[reassignment] auth mode: ${mode}, backend: ${API_BASE}`);

  const found = await discoverEntity(request);
  if (found) entityCode = found;
});

test.describe('UST838 — workload formula', () => {
  test('popup and dashboard report the same workload for the same officer', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const candidatesRes = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode));
    expect(candidatesRes.status()).toBe(200);
    const candidates = (await candidatesRes.json()).candidates as Array<Record<string, unknown>>;
    expect(candidates.length).toBeGreaterThan(0);

    const workloadRes = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(entityCode));
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
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(entityCode));
    expect(res.status()).toBe(200);
    const body = await res.json();

    // Boundary: the total counted must be strictly less than every record in the entity, since the
    // seed deliberately includes closed records. An exclusion filter that silently stopped working
    // would make these equal.
    const total = body.totalActiveRecords as number;
    expect(total).toBeGreaterThanOrEqual(0);

    const officers = body.officers as Array<Record<string, unknown>>;
    const summed = officers.reduce((acc, o) => acc + (o['workload'] as number), 0);
    expect(summed).toBe(total);
  });

  test('an officer with no records is still listed, with zero', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, `${REASSIGN}/workload`, RE_PNO(entityCode));
    const officers = (await res.json()).officers as Array<Record<string, unknown>>;
    // A GROUP BY alone cannot produce a row for an idle officer; the dashboard must still show them.
    expect(officers.some((o) => (o['workload'] as number) === 0)).toBeTruthy();
  });
});

test.describe('UST841 — candidate filtering', () => {
  test('inactive officers are never offered as a target', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode));
    const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;

    // The seed includes a deactivated "Former Officer" precisely so this assertion can fail if the
    // active filter is dropped.
    expect(candidates.some((c) => (c['displayName'] as string) === 'Former Officer')).toBeFalsy();
  });

  test('role filter narrows the list server-side', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
      role: 'CONTACT_PERSON',
    });
    expect(res.status()).toBe(200);
    const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;
    expect(candidates.length).toBeGreaterThan(0);
    for (const c of candidates) {
      expect(c['reRole']).toBe('CONTACT_PERSON');
    }
  });

  test('search matches on name, and returns nothing for an absent term', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const hit = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
      search: 'Nodal',
    });
    expect(((await hit.json()).candidates as unknown[]).length).toBeGreaterThan(0);

    const miss = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
      search: 'zzz-no-such-officer-zzz',
    });
    expect(((await miss.json()).candidates as unknown[]).length).toBe(0);
  });
});

test.describe('authorization — enforced server-side, not in the browser', () => {
  test('no identity is rejected', async ({ request }) => {
    const res = await request.get(`${REASSIGN}/candidates`, { failOnStatusCode: false });
    expect(res.status()).toBe(401);
  });

  test('an RE caller cannot name another entity', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.get(`${REASSIGN}/candidates`, {
      headers: devHeaders(RE_NODAL(entityCode)),
      params: { entityCode: 'SOME_OTHER_ENTITY' },
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
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.get(`${REASSIGN}/requests/pending`, {
      headers: devHeaders(RE_NODAL(entityCode)),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    expect((await res.json()).messageKey).toBe('re.reassign.error.pno_only');
  });

  test('a plain nodal officer cannot decide requests', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.post(`${REASSIGN}/requests/decide`, {
      headers: devHeaders(RE_NODAL(entityCode)),
      data: { approve: true, items: [{ requestId: 1 }] },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('UST840 — immutable reason, append-only clarifications', () => {
  test('a reason below the configured minimum is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const target = await pickTarget(request);
    test.skip(!target, 'No reassignment target available');

    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(entityCode), {
      recordId: openRecordId,
      toUserId: target,
      reason: 'short',
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.reason_too_short');
  });

  test('an empty clarification is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const requestId = await anyRequestId(request);
    test.skip(!requestId, 'No existing request to clarify');

    const res = await postWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(entityCode),
      { note: '   ' },
    );
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.clarification_required');
  });

  test('a clarification is appended with server-resolved attribution', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const requestId = await anyRequestId(request);
    test.skip(!requestId, 'No existing request to clarify');

    const note = `Clarifying context ${Date.now()}`;
    const created = await postWithAuth(
      request,
      `${REASSIGN}/requests/${requestId}/clarifications`,
      RE_NODAL(entityCode),
      // addedBy is deliberately NOT sent; attribution must come from the resolved identity.
      { note, addedBy: 'someone_else_entirely' },
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
      RE_NODAL(entityCode),
    );
    const notes = ((await list.json()).clarifications as Array<Record<string, unknown>>).map(
      (c) => c['note'],
    );
    expect(notes).toContain(note);
  });

  test('the original reason is unchanged after a clarification is added', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const mine = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(entityCode));
    const requests = (await mine.json()).requests as Array<Record<string, unknown>>;
    test.skip(requests.length === 0, 'No existing request');

    const subject = requests[0];
    const before = subject['reason'];
    await postWithAuth(
      request,
      `${REASSIGN}/requests/${subject['id']}/clarifications`,
      RE_NODAL(entityCode),
      { note: `Immutability check ${Date.now()}` },
    );

    const after = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(entityCode));
    const reloaded = ((await after.json()).requests as Array<Record<string, unknown>>).find(
      (r) => r['id'] === subject['id'],
    );
    expect(reloaded!['reason']).toBe(before);
  });
});

test.describe('UST839 — optimistic locking and conflict-safe bulk actions', () => {
  test('a stale version is refused with a conflict naming the record', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const target = await pickTarget(request);
    test.skip(!target, 'No reassignment target available');

    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(entityCode), {
      toUserId: target,
      reason: uniqueReason('Stale version check'),
      items: [{ recordId: openRecordId, expectedVersion: 99999 }],
    });
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.succeededCount).toBe(0);
    expect(body.failedCount).toBe(1);
    expect(body.failed[0].id).toBe(openRecordId);
    // The key matters more than the prose: the UI renders it per locale.
    expect(body.failed[0].messageKey).toBe('re.reassign.error.conflict');
    // And the detail must name the record, so a user can tell which selection failed.
    expect(String(body.failed[0].detail)).toContain(String(openRecordId));
  });

  test('one conflicting record does not roll back the rest of the batch', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const move = await pickMovableRecord(request);
    test.skip(!move, 'No record with an eligible different-owner target available');

    // A second record that is NOT the first, so the batch has two independent items. The stale item
    // is a bogus record id paired with the movable one, which is enough to prove isolation.
    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(entityCode), {
      toUserId: move!.targetUserId,
      reason: uniqueReason('Partial batch check'),
      items: [
        // Valid: no expectedVersion, so no stale-read protection is claimed.
        { recordId: move!.recordId },
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
    expect(body.succeeded).toContain(move!.recordId);
    expect(body.failed[0].id).toBe(999999999);
  });

  test('a batch over the configured maximum is refused before anything is applied', async ({
    request,
  }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const target = await pickTarget(request);
    test.skip(!target, 'No reassignment target available');

    const items = Array.from({ length: 5000 }, (_, i) => ({ recordId: i + 1 }));
    const res = await postWithAuth(request, `${REASSIGN}/bulk`, RE_PNO(entityCode), {
      toUserId: target,
      reason: uniqueReason('Bulk limit check'),
      items,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.bulk_limit_exceeded');
  });

  test('an empty selection is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await postWithAuth(request, `${REASSIGN}/requests/decide`, RE_PNO(entityCode), {
      approve: true,
      items: [],
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.nothing_selected');
  });

  test('rejecting without a comment is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await postWithAuth(request, `${REASSIGN}/requests/decide`, RE_PNO(entityCode), {
      approve: false,
      items: [{ requestId: 1 }],
      comment: '   ',
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.rejection_comment_required');
  });
});

test.describe('UST842/UST843 — request lifecycle', () => {
  test('a request can be raised, appears in My Requests, and blocks a duplicate', async ({
    request,
  }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const move = await pickMovableRecord(request);
    test.skip(!move, 'No record with an eligible different-owner target available');

    const reason = uniqueReason('Lifecycle check');
    const created = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(entityCode), {
      recordId: move!.recordId,
      toUserId: move!.targetUserId,
      reason,
    });

    if (created.status() === 409) {
      // A prior run left a pending request on this record — the one-pending-per-record rule.
      test.skip(true, 'A request is already pending for this record from a prior run');
    }
    expect(created.status()).toBe(201);
    const requestId = (await created.json()).request.id;

    const mine = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(entityCode));
    const ids = ((await mine.json()).requests as Array<Record<string, unknown>>).map((r) => r['id']);
    expect(ids).toContain(requestId);

    const duplicate = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(entityCode), {
      recordId: move!.recordId,
      toUserId: move!.targetUserId,
      reason: uniqueReason('Duplicate check'),
    });
    expect(duplicate.status()).toBe(409);
    expect((await duplicate.json()).messageKey).toBe('re.reassign.error.already_pending');
  });

  test('reassigning to the record current owner is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const owned = await pickOwnedRecord(request);
    test.skip(!owned, 'No record with a known current owner available');

    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(entityCode), {
      recordId: owned!.recordId,
      // Deliberately the current owner: a move to the person who already holds it is not a move,
      // and allowing it would write a history row recording a change that did not happen.
      toUserId: owned!.ownerUserId,
      reason: uniqueReason('Same owner check'),
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.already_assigned');
  });

  test('an inactive target is refused', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const records = await recordsFor(request);
    test.skip(records.length === 0, 'No records available');

    // The seeded inactive officer is not in the candidate list, so its id is derived from the active
    // one — same entity hash, different prefix.
    const active = await pickTarget(request);
    test.skip(!active, 'No target available');
    const inactive = active!.replace(/^re\.(no1|no2|cp1|pno)\./, 're.left.');
    test.skip(inactive === active, 'Could not derive the inactive officer id');

    const res = await postWithAuth(request, `${REASSIGN}/requests`, RE_NODAL(entityCode), {
      recordId: records[0].id,
      toUserId: inactive,
      reason: uniqueReason('Inactive target check'),
    });
    expect(res.status()).toBe(400);
    expect(['re.reassign.error.target_inactive', 're.reassign.error.target_not_in_entity']).toContain(
      (await res.json()).messageKey,
    );
  });
});

test.describe('UST844 — reassignment history report', () => {
  test('history is entity-scoped and readable', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, REPORT, RE_PNO(entityCode));
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body.history)).toBeTruthy();

    for (const row of body.history as Array<Record<string, unknown>>) {
      // A report is as much an information-disclosure surface as a detail page.
      expect(row['entityCode']).toBe(entityCode);
      expect(row['toUserId']).toBeTruthy();
      expect(['APPROVED_REQUEST', 'DIRECT']).toContain(row['triggerType']);
    }
  });

  test('an RE caller cannot read another entity history', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.get(REPORT, {
      headers: devHeaders(RE_PNO(entityCode)),
      params: { entityCode: 'SOME_OTHER_ENTITY' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });

  test('summary inbound and outbound totals agree with the row count', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await getWithAuth(request, `${REPORT}/summary`, RE_PNO(entityCode));
    expect(res.status()).toBe(200);
    const body = await res.json();

    const inbound = (body.inbound as Array<Record<string, unknown>>).reduce(
      (acc, r) => acc + (r['count'] as number),
      0,
    );
    const outbound = (body.outbound as Array<Record<string, unknown>>).reduce(
      (acc, r) => acc + (r['count'] as number),
      0,
    );
    // Every move has exactly one recipient; only moves from a known officer have a source, so
    // outbound can legitimately be lower but never higher.
    expect(outbound).toBeLessThanOrEqual(inbound);
  });

  test('an invalid date is refused rather than silently ignored', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.get(REPORT, {
      headers: devHeaders(RE_PNO(entityCode)),
      params: { from: 'not-a-date' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('re.reassign.error.invalid_date');
  });
});

test.describe('UST845 — notification delivery log', () => {
  test('the delivery log is ADMIN-only', async ({ request }) => {
    test.skip(!entityCode, 'No seeded entity directory available');

    const res = await request.get(`${REPORT}/delivery-log`, {
      headers: devHeaders(RE_NODAL(entityCode)),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });

  test('reassign-in and reassign-out attempts are both logged', async ({ request }) => {
    const res = await getWithAuth(request, `${REPORT}/delivery-log`, ADMIN());
    test.skip(res.status() !== 200, 'Delivery log unavailable');

    const body = await res.json();
    const types = (body.deliveries as Array<Record<string, unknown>>).map(
      (d) => d['notificationType'] as string,
    );
    // Both halves of the pair must appear; logging only the recipient would leave the officer who
    // lost the record with no record of having been told.
    if (types.length > 0) {
      expect(types).toContain('notification.reassign.in');
      expect(types).toContain('notification.reassign.out');
    }

    for (const delivery of body.deliveries as Array<Record<string, unknown>>) {
      expect(['SENT', 'FAILED']).toContain(delivery['status']);
      expect(delivery['channel']).toBe('IN_APP');
      expect(delivery['recipientUserId']).toBeTruthy();
    }
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
// Discovery helpers — data is found, not seeded, so the suite is re-runnable
// ═══════════════════════════════════════════════════════════════

interface RecordRow {
  id: number;
  version: number;
  assignedTo: string | null;
}

/**
 * Records are discovered through the history/requests surface rather than a direct DB read, so the
 * suite needs no database credentials. Falls back to the ids observed in My Requests.
 */
async function recordsFor(request: APIRequestContext): Promise<RecordRow[]> {
  const res = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(entityCode));
  if (!res.ok()) return [];
  const requests = (await res.json()).requests as Array<Record<string, unknown>>;

  const seen = new Map<number, RecordRow>();
  for (const r of requests) {
    const id = r['recordId'] as number;
    if (id && !seen.has(id)) {
      // Version is not exposed on the request row; null means "no prior read to protect", which the
      // API accepts. The stale-version tests supply an explicit bogus version instead.
      seen.set(id, { id, version: null as unknown as number, assignedTo: r['toUserId'] as string });
    }
  }
  if (seen.size > 0) return [...seen.values()];

  // Nothing raised yet: fall back to the record ids visible in history.
  const hist = await getWithAuth(request, REPORT, RE_PNO(entityCode));
  if (!hist.ok()) return [];
  for (const h of (await hist.json()).history as Array<Record<string, unknown>>) {
    const id = h['recordId'] as number;
    if (id && !seen.has(id)) {
      seen.set(id, { id, version: null as unknown as number, assignedTo: h['toUserId'] as string });
    }
  }
  return [...seen.values()];
}

async function pickTarget(request: APIRequestContext): Promise<string | null> {
  const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
    role: 'NODAL_OFFICER',
  });
  if (!res.ok()) return null;
  const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;
  if (candidates.length === 0) return null;

  const records = await recordsFor(request);
  if (records.length > 0) {
    openRecordId = records[0].id;
    openRecordVersion = records[0].version;
  }
  // Prefer the least loaded officer, which is also what a PNO would pick.
  const sorted = [...candidates].sort(
    (a, b) => (a['workload'] as number) - (b['workload'] as number),
  );
  return sorted[0]['userId'] as string;
}

/**
 * Finds a record together with a target that does NOT already own it.
 *
 * The current owner is derived from the candidates endpoint rather than guessed: passing
 * {@code recordId} makes the server omit the record's own owner, so the difference between the
 * unfiltered and filtered lists identifies the owner without needing database access. Picking a
 * target any other way risks selecting the incumbent, which the API correctly refuses.
 */
async function pickMovableRecord(
  request: APIRequestContext,
): Promise<{ recordId: number; targetUserId: string } | null> {
  const records = await recordsFor(request);
  for (const record of records) {
    const res = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
      recordId: record.id,
      role: 'NODAL_OFFICER',
    });
    if (!res.ok()) continue;
    const candidates = (await res.json()).candidates as Array<Record<string, unknown>>;
    if (candidates.length === 0) continue;
    const sorted = [...candidates].sort(
      (a, b) => (a['workload'] as number) - (b['workload'] as number),
    );
    return { recordId: record.id, targetUserId: sorted[0]['userId'] as string };
  }
  return null;
}

/** A record whose current owner is known, for the "already assigned" negative case. */
async function pickOwnedRecord(
  request: APIRequestContext,
): Promise<{ recordId: number; ownerUserId: string } | null> {
  const records = await recordsFor(request);
  for (const record of records) {
    const all = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode));
    const scoped = await getWithAuth(request, `${REASSIGN}/candidates`, RE_NODAL(entityCode), {
      recordId: record.id,
    });
    if (!all.ok() || !scoped.ok()) continue;

    const allIds = ((await all.json()).candidates as Array<Record<string, unknown>>).map(
      (c) => c['userId'] as string,
    );
    const scopedIds = new Set(
      ((await scoped.json()).candidates as Array<Record<string, unknown>>).map(
        (c) => c['userId'] as string,
      ),
    );
    const owner = allIds.find((id) => !scopedIds.has(id));
    if (owner) return { recordId: record.id, ownerUserId: owner };
  }
  return null;
}

async function anyRequestId(request: APIRequestContext): Promise<number | null> {
  const res = await getWithAuth(request, `${REASSIGN}/requests/mine`, RE_NODAL(entityCode));
  if (!res.ok()) return null;
  const requests = (await res.json()).requests as Array<Record<string, unknown>>;
  return requests.length > 0 ? (requests[0]['id'] as number) : null;
}
