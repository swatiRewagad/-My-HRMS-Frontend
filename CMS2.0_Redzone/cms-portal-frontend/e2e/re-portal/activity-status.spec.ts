import { test, expect, APIRequestContext } from '../fixtures';
import { createForwardedComplaint } from '../utils/test-data';

/**
 * RE Activity Status ladder — UST846, UST847, UST848, UST849, UST850, UST851, UST852.
 *
 * API-level rather than browser-level, deliberately. The `cms` realm on 9090 contains no RE_* or
 * AA_* roles and no RE users at all, so a browser login cannot reach the RE portal: signing in as
 * cms.admin yields no entity_code, the detail endpoint 400s, and every element assertion then fails
 * for a reason unrelated to the code under test. Seeding RE users into the realm is the prerequisite
 * for UI-level coverage here; until then these assert the server contract using the dev identity
 * headers the backend already honours.
 *
 * Every acceptance criterion in this batch is a server-side rule anyway — "the ladder only moves
 * forward", "the RE cannot set it", "the threshold is snapshotted", "the requester cannot approve
 * their own config change". A rendered-DOM assertion would pass even if the server allowed the
 * action, which is the failure mode actually worth guarding.
 */

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';
const RE_PORTAL = `${API_BASE}/api/v1/re-portal`;
const CONFIG = `${API_BASE}/api/v1/re-activity-config`;

function reHeaders(entityCode: string, userId = 're_e2e_activity', name = 'E2E RE Nodal') {
  return {
    'X-User-Id': userId,
    'X-User-Name': name,
    'X-User-Roles': 'RE_NODAL_OFFICER',
    'X-Entity-Code': entityCode,
    'Content-Type': 'application/json',
  };
}

function adminHeaders(userId: string, name = 'E2E Admin') {
  return {
    'X-User-Id': userId,
    'X-User-Name': name,
    'X-User-Roles': 'ADMIN',
    'Content-Type': 'application/json',
  };
}

/**
 * The entity the seeded complaint belongs to. Must resolve to a REGULATED_ENTITIES row by normalised
 * name — the backend looks entities up that way, so an opaque code like 'HDFC0001' matches nothing.
 */
const RE_ENTITY = process.env['RE_ENTITY_CODE'] || 'HDFC Bank';

/**
 * Seeds a complaint forwarded to the entity, together with the ReResponseTracker row every RE-portal
 * read depends on, and returns it.
 *
 * WAS DISCOVERY, NOW SEEDING. The previous findScopedComplaint probed three hardcoded complaint
 * numbers (CMP-20260605-100001 / …604-100002 / …603-100003). All three exist in cms_db but NONE has a
 * RE_RESPONSE_TRACKER row, and their statuses are assigned/in_progress/reviewer_review rather than
 * forwarded — so GET /re-portal/complaints/{n} 404s for every one of them, discovery returned null,
 * and NINE of this file's fifteen tests skipped. The whole UST846–852 activity ladder reported green
 * having executed nothing.
 *
 * createForwardedComplaint is the shared helper that already does this correctly: create → ACCEPT →
 * FORWARD_DEPT → seed the tracker. Using it means this spec cannot drift from the forwarding path the
 * rest of the RE suite exercises.
 */
async function seedScopedComplaint(request: APIRequestContext):
    Promise<{ complaintNumber: string; entityCode: string }> {
  const complaint = await createForwardedComplaint(request, RE_ENTITY, {
    subject: `Activity ladder fixture ${Date.now()}`,
  });

  // The tracker must exist, or every detail read 404s with "No response tracker for complaint" and
  // the failure looks like a broken ladder rather than a missing fixture.
  const probe = await request.get(`${RE_PORTAL}/complaints/${complaint.complaintNumber}`, {
    headers: reHeaders(RE_ENTITY),
    failOnStatusCode: false,
  });
  expect(
    probe.status(),
    `the seeded complaint ${complaint.complaintNumber} must be readable through the RE portal — ` +
      'createForwardedComplaint also seeds its ReResponseTracker'
  ).toBe(200);
  const resolved = (await probe.json())?.data?.entityCode;
  expect(resolved, 'the seeded complaint must carry an entity code').toBeTruthy();

  return { complaintNumber: complaint.complaintNumber, entityCode: resolved };
}

const LADDER = [
  'NOT_OPENED',
  'OPENED',
  'UNDER_REVIEW',
  'RESPONSE_BEING_PREPARED',
  'DOCUMENTS_UPLOADED',
  'RESPONSE_SUBMITTED',
  'OVERDUE',
];

/**
 * A shared record for the read-only assertions, plus a per-test fresh one wherever the ladder is
 * mutated. The ladder only ever moves FORWARD, so one shared record would let an earlier test push it
 * past the state a later test needs — and the later test would then "pass" against the wrong rung.
 */
let scoped: { complaintNumber: string; entityCode: string };

test.beforeAll(async ({ request }) => {
  scoped = await seedScopedComplaint(request);
});

test.describe('RE Activity Status — derived from RE actions (UST846)', () => {

  test('opening the detail records the record as Opened', async ({ request }) => {
    // A record nobody has opened yet, so NOT_OPENED → OPENED is genuinely observed rather than
    // inferred from a record that some earlier test already advanced.
    const { complaintNumber, entityCode } = await seedScopedComplaint(request);

    const res = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, {
      headers: reHeaders(entityCode),
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.data.reActivityStatus).toBeTruthy();
    expect(LADDER).toContain(body.data.reActivityStatus);

    // The very act of fetching the detail is the "Opened" signal, so by the time the response is
    // serialised the record must be at least Opened — never still Not Opened.
    expect(body.data.reActivityStatus).not.toBe('NOT_OPENED');

    // The label travels as a translation key, not English text, so the RE and RBI sides can render
    // the same badge in the same language (UST847).
    expect(body.data.reActivityStatusKey).toMatch(/^re\.activity\./);
  });

  test('the ladder never regresses when the record is re-opened', async ({ request }) => {
    const { complaintNumber, entityCode } = await seedScopedComplaint(request);

    // Advance past OPENED first. Asserted: if the draft is refused the record never leaves OPENED and
    // the "does not regress" check below becomes vacuous.
    const draft = await request.post(`${RE_PORTAL}/complaints/${complaintNumber}/draft`, {
      headers: reHeaders(entityCode),
      data: { draftText: 'Working notes towards a response.' },
      failOnStatusCode: false,
    });
    expect(draft.status(), 'the fixture must be advanced past OPENED for this test to mean anything')
      .toBe(200);

    const after = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, {
      headers: reHeaders(entityCode),
    });
    const status = (await after.json()).data.reActivityStatus;
    expect(LADDER.indexOf(status), 'the draft must have moved the record above OPENED')
      .toBeGreaterThan(LADDER.indexOf('OPENED'));

    // Re-fetching is an "Opened" event again. A naive implementation would write OPENED back over
    // the higher level and the badge would appear to go backwards to staff.
    const reopened = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, {
      headers: reHeaders(entityCode),
    });
    const statusAfterReopen = (await reopened.json()).data.reActivityStatus;

    expect(LADDER.indexOf(statusAfterReopen)).toBeGreaterThanOrEqual(LADDER.indexOf(status));
    expect(statusAfterReopen).not.toBe('OPENED');
  });

  test('an empty draft means Under Review; a draft with text means Response Being Prepared',
      async ({ request }) => {
    // Freshly seeded, so it cannot already have been responded to. The old `if (409) test.skip` was
    // the only handling of that case, which meant a record left responded-to by a previous run
    // silently removed this assertion from the suite.
    const { complaintNumber, entityCode } = await seedScopedComplaint(request);

    const res = await request.post(`${RE_PORTAL}/complaints/${complaintNumber}/draft`, {
      headers: reHeaders(entityCode),
      data: { draftText: 'Drafting our reply on the disputed transaction.' },
      failOnStatusCode: false,
    });
    expect(res.status(), await res.text()).toBe(200);

    const body = await res.json();
    expect(body.draftSavedAt).toBeTruthy();
    // Never below RESPONSE_BEING_PREPARED, because the draft carried text.
    expect(LADDER.indexOf(body.reActivityStatus))
        .toBeGreaterThanOrEqual(LADDER.indexOf('RESPONSE_BEING_PREPARED'));
  });
});

test.describe('RE Activity Status — read-only to the RE (UST852)', () => {

  test('the RE cannot set the activity status directly', async ({ request }) => {
    const { complaintNumber, entityCode } = scoped;

    const res = await request.put(`${RE_PORTAL}/complaints/${complaintNumber}/activity-status`, {
      headers: reHeaders(entityCode),
      data: { status: 'RESPONSE_SUBMITTED' },
      failOnStatusCode: false,
    });

    // Explicitly 403, not 404: an integration that guesses this URL gets a documented refusal rather
    // than a not-found that invites trying another path.
    expect(res.status()).toBe(403);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.messageKey).toBe('re.activity.manual_set_forbidden');
  });

  test('claiming RESPONSE_SUBMITTED by hand does not change the stored status', async ({ request }) => {
    const { complaintNumber, entityCode } = scoped;

    const before = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, {
      headers: reHeaders(entityCode),
    });
    const statusBefore = (await before.json()).data.reActivityStatus;

    await request.put(`${RE_PORTAL}/complaints/${complaintNumber}/activity-status`, {
      headers: reHeaders(entityCode),
      data: { status: 'RESPONSE_SUBMITTED' },
      failOnStatusCode: false,
    });

    const after = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, {
      headers: reHeaders(entityCode),
    });
    expect((await after.json()).data.reActivityStatus).toBe(statusBefore);
  });
});

test.describe('Activity history carries an automatic/manual discriminator (UST848)', () => {

  test('timeline entries declare their source explicitly', async ({ request }) => {
    const { complaintNumber, entityCode } = scoped;

    // Guarantee at least one ladder entry exists.
    await request.get(`${RE_PORTAL}/complaints/${complaintNumber}`, { headers: reHeaders(entityCode) });

    const res = await request.get(`${RE_PORTAL}/complaints/${complaintNumber}/timeline`, {
      headers: reHeaders(entityCode),
    });
    expect(res.status()).toBe(200);

    const items = (await res.json()).data as Array<Record<string, unknown>>;
    expect(items.length).toBeGreaterThan(0);

    const ladderEntries = items.filter(i => String(i.action ?? '').startsWith('RE_ACTIVITY_'));
    expect(ladderEntries.length).toBeGreaterThan(0);

    // Both statuses are recorded, so staff can read the progression rather than a bare "changed".
    for (const entry of ladderEntries) {
      expect(entry.fromStatus).toBeTruthy();
      expect(entry.toStatus).toBeTruthy();
      expect(LADDER).toContain(String(entry.toStatus));
    }
  });
});

test.describe('Nudge threshold maker-checker (UST851)', () => {

  const NUDGE_KEY = 'cms.re.activity.nudge_days.opened';

  test('a threshold change is staged, not applied immediately', async ({ request }) => {
    // cms_db is a persistent fixture that is never emptied between runs, so a fixed value would be
    // refused as already-in-force on the second run. Deriving a value that differs from the live one
    // keeps the test order- and history-independent.
    const proposed = String(3 + (Date.now() % 20) + 1);

    const res = await request.post(`${CONFIG}/requests`, {
      headers: adminHeaders('e2e_admin_maker'),
      data: { configKey: NUDGE_KEY, proposedValue: proposed, reason: 'E2E staging check' },
      failOnStatusCode: false,
    });

    // 409 when a previous run left a pending request on this key — the one-pending-per-key rule.
    if (res.status() === 409) {
      test.skip(true, 'A change is already pending for this key from a prior run');
    }
    expect(res.status()).toBe(201);

    const body = await res.json();
    expect(body.request.status).toBe('PENDING');
    expect(body.request.proposedValue).toBe(proposed);
    expect(body.request.requestedBy).toBe('e2e_admin_maker');
    // Nothing is applied yet: the live value is still whatever it was.
    expect(body.request.currentValue).not.toBe(proposed);
  });

  test('the requester cannot approve their own change', async ({ request }) => {
    const pending = await request.get(`${CONFIG}/requests/pending`, {
      headers: adminHeaders('e2e_admin_reader'),
    });
    const requests = (await pending.json()).requests as Array<Record<string, unknown>>;
    const mine = requests.find(r => r.requestedBy === 'e2e_admin_maker');
    test.skip(!mine, 'No pending request raised by the maker identity');

    const res = await request.post(`${CONFIG}/requests/${mine!.id}/approve`, {
      headers: adminHeaders('e2e_admin_maker'),
      data: { decisionReason: 'Approving my own change' },
      failOnStatusCode: false,
    });

    expect(res.status()).toBe(409);
    const body = await res.json();
    expect(body.message).toContain('Maker-Checker');
  });

  test('a different administrator can approve, and the value then takes effect', async ({ request }) => {
    const pending = await request.get(`${CONFIG}/requests/pending`, {
      headers: adminHeaders('e2e_admin_reader'),
    });
    const requests = (await pending.json()).requests as Array<Record<string, unknown>>;
    const mine = requests.find(r => r.requestedBy === 'e2e_admin_maker');
    test.skip(!mine, 'No pending request raised by the maker identity');

    const res = await request.post(`${CONFIG}/requests/${mine!.id}/approve`, {
      headers: adminHeaders('e2e_admin_checker'),
      data: { decisionReason: 'Independent approval' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);

    const body = await res.json();
    expect(body.request.status).toBe('APPROVED');
    expect(body.request.decidedBy).toBe('e2e_admin_checker');
    expect(body.request.decidedBy).not.toBe(body.request.requestedBy);
  });

  test('an out-of-range threshold is refused at request time', async ({ request }) => {
    const res = await request.post(`${CONFIG}/requests`, {
      headers: adminHeaders('e2e_admin_maker2'),
      data: { configKey: NUDGE_KEY, proposedValue: '0', reason: 'Zero days' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toContain('between');
  });

  test('an unknown config key is refused', async ({ request }) => {
    const res = await request.post(`${CONFIG}/requests`, {
      headers: adminHeaders('e2e_admin_maker2'),
      data: { configKey: 'cms.re.activity.not_a_real_key', proposedValue: '5' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
  });

  test('an unidentified caller cannot stage a change', async ({ request }) => {
    const res = await request.post(`${CONFIG}/requests`, {
      headers: { 'Content-Type': 'application/json' },
      data: { configKey: NUDGE_KEY, proposedValue: '4' },
      failOnStatusCode: false,
    });
    // Note: this asserts the controller's own identity check. It is NOT evidence that the endpoint is
    // authenticated — SecurityConfig is still permitAll and the role guards fail open, so a caller who
    // supplies any X-User-Id passes. Real enforcement is blocked on that separate work.
    expect(res.status()).toBe(401);
  });
});

test.describe('Staff-side visibility is read-only and gated (UST852)', () => {

  test('the public tracker does not expose the entity activity status', async ({ request }) => {
    const { complaintNumber } = scoped;

    const res = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
      failOnStatusCode: false,
    });
    // ASSERTED, not skipped. A 403/404/500 from the tracker on a complaint this suite seeded itself
    // is a broken endpoint, and `test.skip(!res.ok())` reported that as a pass — the leak assertions
    // below all hold trivially on a body that was never returned.
    expect(res.status(), `GET /api/v1/complaints/${complaintNumber} must be readable`).toBe(200);

    const detail = (await res.json()).data;
    // A complainant learning that their bank "has not opened" the record invites a conversation RBI
    // has not agreed to have, and it is not their data.
    expect(detail.reActivity).toBeUndefined();
    expect(detail.reActivityHistory).toBeUndefined();
  });

  test('a bare bearer token does not by itself unlock the activity status', async ({ request }) => {
    const { complaintNumber } = scoped;

    // Guards against a regression to the old `Authorization != null` staff test, which would have
    // let any caller inventing a token read the entity's progress.
    const res = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
      headers: { Authorization: 'Bearer not-a-real-token' },
      failOnStatusCode: false,
    });
    // ASSERTED, not skipped. A 403/404/500 from the tracker on a complaint this suite seeded itself
    // is a broken endpoint, and `test.skip(!res.ok())` reported that as a pass — the leak assertions
    // below all hold trivially on a body that was never returned.
    expect(res.status(), `GET /api/v1/complaints/${complaintNumber} must be readable`).toBe(200);

    expect((await res.json()).data.reActivity).toBeUndefined();
  });

  test('a staff caller sees the status, its snapshot threshold, and a read-only marker',
      async ({ request }) => {
    const { complaintNumber } = scoped;

    // A bare bearer token is NOT enough: the endpoint resolves a real identity and treats only a
    // non-RE identity as staff, so the dev identity headers are what make this caller staff.
    const res = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
      headers: {
        'X-User-Id': 'cepc_e2e_do',
        'X-User-Name': 'E2E CEPC DO',
        'X-User-Roles': 'DO',
      },
      failOnStatusCode: false,
    });
    // ASSERTED, not skipped. A 403/404/500 from the tracker on a complaint this suite seeded itself
    // is a broken endpoint, and `test.skip(!res.ok())` reported that as a pass — the leak assertions
    // below all hold trivially on a body that was never returned.
    expect(res.status(), `GET /api/v1/complaints/${complaintNumber} must be readable`).toBe(200);

    const detail = (await res.json()).data;
    expect(detail.reActivity).toBeDefined();
    expect(LADDER).toContain(detail.reActivity.status);
    expect(detail.reActivity.labelKey).toMatch(/^re\.activity\./);
    expect(detail.reActivity.readOnly).toBe(true);
    expect(Array.isArray(detail.reActivityHistory)).toBe(true);
  });
});
