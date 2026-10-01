import { test, expect, APIRequestContext } from '../fixtures';

/**
 * Query/correspondence threads between an RE and RBI, plus entity-side internal notes.
 * Covers UST853, UST854, UST855, UST856, UST857 and UST860.
 *
 * These are API-level tests. They deliberately assert the SERVER's behaviour rather than the
 * rendered UI, because every acceptance criterion in this batch is an authorisation or integrity
 * rule — "RE cannot approve its own extension", "a note locks after the window", "another entity
 * cannot read the thread". A browser-only assertion would pass even if the server allowed the
 * action, which is exactly the failure mode worth guarding against.
 */

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';
const QUERIES = `${API_BASE}/api/v1/complaint-queries`;

/** Dev-header identity. The server prefers a JWT claim when one is present. */
function reHeaders(entityCode: string, userId = 're_e2e_nodal', name = 'E2E RE Nodal') {
  return {
    'X-User-Id': userId,
    'X-User-Name': name,
    'X-User-Roles': 'RE_NODAL_OFFICER',
    'X-Entity-Code': entityCode,
    'Content-Type': 'application/json',
  };
}

function rbiHeaders(userId = 'cepc_e2e_do', name = 'E2E CEPC DO') {
  return {
    'X-User-Id': userId,
    'X-User-Name': name,
    'X-User-Roles': 'DO',
    'Content-Type': 'application/json',
  };
}

/**
 * Finds a complaint that already carries an entity code, discovering it rather than hardcoding a
 * number that may not exist in a given database.
 *
 * The RE-portal detail endpoint is used because it is the one that actually returns entityCode —
 * /api/v1/complaints/{n} exposes entityName/entityType but no code. It is probed with an entity
 * code taken from the candidate's own subject line, so a wrong guess yields 403 and we move on.
 */
async function findScopedComplaint(request: APIRequestContext):
    Promise<{ complaintNumber: string; entityCode: string } | null> {
  const candidates: Array<[string, string]> = [
    ['CMP-20260605-100001', 'Bajaj Finance Limited'],
    ['CMP-20260604-100002', 'Muthoot Finance Ltd'],
    ['CMP-20260603-100003', 'PhonePe Private Limited'],
  ];

  for (const [complaintNumber, entityCode] of candidates) {
    const res = await request.get(
      `${API_BASE}/api/v1/re-portal/complaints/${complaintNumber}`,
      {
        headers: {
          'X-User-Id': 'e2e_probe',
          'X-User-Roles': 'RE_NODAL_OFFICER',
          'X-Entity-Code': entityCode,
        },
        failOnStatusCode: false,
      });
    if (!res.ok()) continue;
    const body = await res.json().catch(() => null);
    const resolved = body?.data?.entityCode;
    if (resolved) return { complaintNumber, entityCode: resolved };
  }
  return null;
}

let scoped: { complaintNumber: string; entityCode: string } | null = null;

test.beforeAll(async ({ request }) => {
  scoped = await findScopedComplaint(request);
});

test.describe('Query threads — creation and listing', () => {

  test('RE raises a clarification; thread is pending with RBI (UST856)', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const res = await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: {
        queryType: 'CLARIFICATION',
        subject: `E2E clarification ${Date.now()}`,
        body: 'Please confirm the disputed transaction date.',
      },
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body.success).toBe(true);

    // The raiser is awaiting the other side, so the thread must not come back as awaiting them.
    expect(body.thread.pendingWith).toBe('RBI');
    expect(body.thread.raisedBySide).toBe('RE');
    expect(body.thread.direction).toBe('RE_TO_RBI');
    expect(body.thread.awaitingMe).toBe(false);
  });

  test('the same thread reads as awaiting-me for the RBI side (UST856)', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const subject = `E2E awaiting-flip ${Date.now()}`;
    await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: { queryType: 'CLARIFICATION', subject, body: 'Body text.' },
    });

    const res = await request.get(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: rbiHeaders(),
    });
    expect(res.ok()).toBeTruthy();
    const threads = (await res.json()).threads as any[];
    const mine = threads.find(t => t.subject === subject);
    expect(mine).toBeTruthy();
    expect(mine.awaitingMe).toBe(true);
  });

  test('the awaiting-my-response count matches the filtered list (UST856)', async ({ request }) => {
    const listRes = await request.get(`${QUERIES}/awaiting-my-response`, { headers: rbiHeaders() });
    expect(listRes.ok()).toBeTruthy();
    const list = await listRes.json();

    const countRes = await request.get(
      `${QUERIES}/awaiting-my-response/count`, { headers: rbiHeaders() });
    const count = (await countRes.json()).count;

    // The badge and the filter must be derived from one source, or the two disagree in the UI.
    expect(count).toBe(list.count);
    expect(list.threads.length).toBe(list.count);
  });

  test('a request with no resolvable identity is refused', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.get(`${QUERIES}/complaint/${scoped!.complaintNumber}`,
      { failOnStatusCode: false });
    expect(res.status()).toBe(401);
  });

  test('an RE user of another entity cannot read the thread list', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.get(`${QUERIES}/complaint/${scoped!.complaintNumber}`, {
      headers: reHeaders('SOME_OTHER_ENTITY_CODE', 'rival_user'),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });
});

test.describe('Tamper-evident history (UST857)', () => {

  test('a reply appends a message and flips who the thread awaits', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: { queryType: 'CLARIFICATION', subject: `E2E reply ${Date.now()}`, body: 'Opening.' },
    });
    const queryId = (await created.json()).queryId;

    const before = await request.get(`${QUERIES}/${queryId}`, { headers: rbiHeaders() });
    const beforeCount = (await before.json()).messages.length;

    const reply = await request.post(`${QUERIES}/${queryId}/messages`, {
      headers: rbiHeaders(),
      data: { body: 'RBI reply text.' },
    });
    expect(reply.status()).toBe(201);

    const after = await request.get(`${QUERIES}/${queryId}`, { headers: rbiHeaders() });
    const messages = (await after.json()).messages;
    expect(messages.length).toBe(beforeCount + 1);

    // Attribution is server-derived, never taken from the request body.
    const last = messages[messages.length - 1];
    expect(last.authorSide).toBe('RBI');
    expect(last.authorName).toBe('E2E CEPC DO');
    expect(last.postedAt).toBeTruthy();
  });

  test('there is no endpoint that edits or deletes a posted message', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: { queryType: 'CLARIFICATION', subject: `E2E immutable ${Date.now()}`, body: 'Original.' },
    });
    const queryId = (await created.json()).queryId;
    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const messageId = (await detail.json()).messages[0].id;

    // Neither verb is routed. Anything other than a 2xx proves the message cannot be mutated.
    const put = await request.put(`${QUERIES}/messages/${messageId}`,
      { headers: reHeaders(entityCode), data: { body: 'tampered' }, failOnStatusCode: false });
    expect(put.ok()).toBeFalsy();

    const del = await request.delete(`${QUERIES}/messages/${messageId}`,
      { headers: reHeaders(entityCode), failOnStatusCode: false });
    expect(del.ok()).toBeFalsy();

    const still = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    expect((await still.json()).messages[0].body).toBe('Original.');
  });
});

test.describe('Extension requests (UST853)', () => {

  function futureDate(daysAhead: number): string {
    const d = new Date();
    d.setDate(d.getDate() + daysAhead);
    return d.toISOString().slice(0, 10);
  }

  async function raiseExtension(request: APIRequestContext, days = 10) {
    const { complaintNumber, entityCode } = scoped!;
    const res = await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: {
        queryType: 'EXTENSION_REQUEST',
        subject: `E2E extension ${Date.now()}`,
        body: 'Awaiting branch records.',
        proposedDeadline: futureDate(days),
        extensionReason: 'Records are being retrieved from archive.',
      },
      failOnStatusCode: false,
    });
    return res;
  }

  test('an extension request opens as PENDING with the proposed deadline recorded', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await raiseExtension(request);
    expect(res.status()).toBe(201);
    const thread = (await res.json()).thread;
    expect(thread.decision).toBe('PENDING');
    expect(thread.proposedDeadline).toBeTruthy();
    // The clock keeps running while pending: nothing is granted yet.
    expect(thread.grantedDeadline).toBeNull();
  });

  test('the RE side cannot decide its own extension request', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseExtension(request)).json()).queryId;

    const res = await request.post(`${QUERIES}/${queryId}/extension-decision`, {
      headers: reHeaders(entityCode),
      data: { approve: true },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });

  test('approval records the granted deadline', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const queryId = (await (await raiseExtension(request)).json()).queryId;
    const granted = futureDate(12);

    const res = await request.post(`${QUERIES}/${queryId}/extension-decision`, {
      headers: rbiHeaders(),
      data: { approve: true, grantedDeadline: granted, decisionReason: 'Approved for archive retrieval.' },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.decision).toBe('APPROVED');
    expect(body.grantedDeadline).toContain(granted);
  });

  test('rejection requires a reason', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const queryId = (await (await raiseExtension(request)).json()).queryId;

    const res = await request.post(`${QUERIES}/${queryId}/extension-decision`, {
      headers: rbiHeaders(),
      data: { approve: false },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toContain('reason');
  });

  test('an already-decided request cannot be decided twice', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const queryId = (await (await raiseExtension(request)).json()).queryId;

    const first = await request.post(`${QUERIES}/${queryId}/extension-decision`, {
      headers: rbiHeaders(),
      data: { approve: true, grantedDeadline: futureDate(11), decisionReason: 'ok' },
    });
    expect(first.ok()).toBeTruthy();

    const second = await request.post(`${QUERIES}/${queryId}/extension-decision`, {
      headers: rbiHeaders(),
      data: { approve: false, decisionReason: 'changed my mind' },
      failOnStatusCode: false,
    });
    expect(second.status()).toBe(409);
  });

  test('a past-dated deadline is refused', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;
    const res = await request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: reHeaders(entityCode),
      data: {
        queryType: 'EXTENSION_REQUEST', subject: 'Backdated', body: 'x',
        proposedDeadline: '2020-01-01', extensionReason: 'y',
      },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
  });

  test('a deadline beyond the configured maximum is refused', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    // The cap comes from SYSTEM_CONFIG cms.query.max_extension_days, not a compiled-in constant.
    const res = await raiseExtension(request, 400);
    expect(res.status()).toBe(400);
    expect((await res.json()).message).toContain('maximum extension');
  });
});

test.describe('Document requests (UST854)', () => {

  async function raiseDocRequest(request: APIRequestContext) {
    const { complaintNumber } = scoped!;
    return request.post(`${QUERIES}/complaint/${complaintNumber}`, {
      headers: rbiHeaders(),
      data: {
        queryType: 'DOCUMENT_REQUEST',
        subject: `E2E documents ${Date.now()}`,
        body: 'Please provide the following.',
        checklistItems: [
          { label: 'Account statement Jan-Mar' },
          { label: 'KYC record' },
        ],
      },
      failOnStatusCode: false,
    });
  }

  test('a document request carries its checklist', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseDocRequest(request)).json()).queryId;

    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const checklist = (await detail.json()).checklist;
    expect(checklist.length).toBe(2);
    expect(checklist.every((i: any) => i.resolved === false)).toBe(true);
  });

  test('a checklist item cannot be marked provided without an attachment', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseDocRequest(request)).json()).queryId;
    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const itemId = (await detail.json()).checklist[0].id;

    const res = await request.post(`${QUERIES}/checklist-items/${itemId}/resolve`, {
      headers: reHeaders(entityCode),
      data: {},
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);

    const after = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    expect((await after.json()).checklist[0].resolved).toBe(false);
  });

  test('a checklist item resolves once a document is attached', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseDocRequest(request)).json()).queryId;
    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const itemId = (await detail.json()).checklist[0].id;

    const res = await request.post(`${QUERIES}/checklist-items/${itemId}/resolve`, {
      headers: reHeaders(entityCode),
      data: { attachmentId: 4242 },
    });
    expect(res.ok()).toBeTruthy();
    expect((await res.json()).resolved).toBe(true);
  });

  test('a document request must name at least one document', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.post(`${QUERIES}/complaint/${scoped!.complaintNumber}`, {
      headers: rbiHeaders(),
      data: { queryType: 'DOCUMENT_REQUEST', subject: 'Empty', body: 'x', checklistItems: [] },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('Meeting requests (UST855)', () => {

  function futureDateTime(daysAhead: number, hour: number): string {
    const d = new Date();
    d.setDate(d.getDate() + daysAhead);
    d.setHours(hour, 0, 0, 0);
    return d.toISOString().slice(0, 19);
  }

  async function raiseMeeting(request: APIRequestContext) {
    return request.post(`${QUERIES}/complaint/${scoped!.complaintNumber}`, {
      headers: rbiHeaders(),
      data: {
        queryType: 'MEETING_REQUEST',
        subject: `E2E meeting ${Date.now()}`,
        body: 'Proposing times.',
        meetingPurpose: 'Discuss a settlement.',
        proposedSlots: [
          { start: futureDateTime(5, 10) },
          { start: futureDateTime(6, 15) },
        ],
      },
      failOnStatusCode: false,
    });
  }

  test('a meeting request records its purpose and slots', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const created = await raiseMeeting(request);
    expect(created.status()).toBe(201);
    const queryId = (await created.json()).queryId;

    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const slots = (await detail.json()).slots;
    expect(slots.length).toBe(2);
    expect(slots.every((s: any) => s.slotStatus === 'PROPOSED')).toBe(true);
  });

  test('accepting a slot settles the thread and supersedes the alternatives', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseMeeting(request)).json()).queryId;
    const detail = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const slots = (await detail.json()).slots;

    const res = await request.post(`${QUERIES}/${queryId}/meeting-response`, {
      headers: reHeaders(entityCode),
      data: { action: 'ACCEPT', acceptedSlotId: slots[0].id },
    });
    expect(res.ok()).toBeTruthy();
    const body = await res.json();
    expect(body.meetingOutcome).toBe('ACCEPTED');
    expect(body.status).toBe('RESOLVED');

    const after = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const afterSlots = (await after.json()).slots;
    expect(afterSlots.find((s: any) => s.id === slots[0].id).slotStatus).toBe('ACCEPTED');
    expect(afterSlots.find((s: any) => s.id === slots[1].id).slotStatus).toBe('SUPERSEDED');
  });

  test('declining requires a reason', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseMeeting(request)).json()).queryId;

    const res = await request.post(`${QUERIES}/${queryId}/meeting-response`, {
      headers: reHeaders(entityCode),
      data: { action: 'DECLINE' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
  });

  test('a counter-proposal supersedes the original slots and keeps the thread open', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { entityCode } = scoped!;
    const queryId = (await (await raiseMeeting(request)).json()).queryId;

    const res = await request.post(`${QUERIES}/${queryId}/meeting-response`, {
      headers: reHeaders(entityCode),
      data: { action: 'COUNTER', counterSlots: [{ start: futureDateTime(9, 11) }] },
    });
    expect(res.ok()).toBeTruthy();

    const after = await request.get(`${QUERIES}/${queryId}`, { headers: reHeaders(entityCode) });
    const slots = (await after.json()).slots;
    // The original proposal stays visible as SUPERSEDED rather than being edited away.
    expect(slots.filter((s: any) => s.slotStatus === 'SUPERSEDED').length).toBe(2);
    expect(slots.filter((s: any) => s.slotStatus === 'PROPOSED').length).toBe(1);
  });

  test('a meeting request must propose at least one time', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.post(`${QUERIES}/complaint/${scoped!.complaintNumber}`, {
      headers: rbiHeaders(),
      data: {
        queryType: 'MEETING_REQUEST', subject: 'No slots', body: 'x',
        meetingPurpose: 'y', proposedSlots: [],
      },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('Entity-only internal notes (UST860)', () => {

  test('an RE user can add and read a note', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;
    const body = `E2E internal note ${Date.now()}`;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode),
      data: { body },
    });
    expect(created.status()).toBe(201);

    const list = await request.get(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode),
    });
    const payload = await list.json();
    expect(payload.notes.some((n: any) => n.body === body)).toBe(true);
    // The window is configuration-driven, not a compiled-in 5.
    expect(payload.editWindowMinutes).toBeGreaterThan(0);
  });

  test('an RBI user cannot read internal notes', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.get(
      `${QUERIES}/complaint/${scoped!.complaintNumber}/internal-notes`,
      { headers: rbiHeaders(), failOnStatusCode: false });
    expect(res.status()).toBe(403);
  });

  test('an RBI user cannot post an internal note', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.post(
      `${QUERIES}/complaint/${scoped!.complaintNumber}/internal-notes`,
      { headers: rbiHeaders(), data: { body: 'should not be allowed' }, failOnStatusCode: false });
    expect(res.status()).toBe(403);
  });

  test('an RE user of another entity cannot read the notes', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const res = await request.get(
      `${QUERIES}/complaint/${scoped!.complaintNumber}/internal-notes`,
      { headers: reHeaders('SOME_OTHER_ENTITY_CODE', 'rival_user'), failOnStatusCode: false });
    expect(res.status()).toBe(403);
  });

  test('only the author may edit their note', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode, 'author_user', 'Author User'),
      data: { body: `Authored ${Date.now()}` },
    });
    const noteId = (await created.json()).noteId;

    const res = await request.put(`${QUERIES}/internal-notes/${noteId}`, {
      headers: reHeaders(entityCode, 'other_user', 'Other User'),
      data: { body: 'hijacked' },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
  });

  test('the author may edit inside the window, and the edit is marked', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode, 'editing_author', 'Editing Author'),
      data: { body: 'first version' },
    });
    const noteId = (await created.json()).noteId;

    const res = await request.put(`${QUERIES}/internal-notes/${noteId}`, {
      headers: reHeaders(entityCode, 'editing_author', 'Editing Author'),
      data: { body: 'second version' },
    });
    expect(res.ok()).toBeTruthy();
    expect((await res.json()).editCount).toBe(1);

    const list = await request.get(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode, 'editing_author', 'Editing Author'),
    });
    const note = (await list.json()).notes.find((n: any) => n.id === noteId);
    expect(note.body).toBe('second version');
    expect(note.editCount).toBe(1);
  });

  test('the server decides editability, and it is per-author', async ({ request }) => {
    test.skip(!scoped, 'No entity-scoped complaint available');
    const { complaintNumber, entityCode } = scoped!;

    const created = await request.post(`${QUERIES}/complaint/${complaintNumber}/internal-notes`, {
      headers: reHeaders(entityCode, 'note_owner', 'Note Owner'),
      data: { body: `Ownership check ${Date.now()}` },
    });
    const noteId = (await created.json()).noteId;

    const asOwner = await request.get(`${QUERIES}/complaint/${complaintNumber}/internal-notes`,
      { headers: reHeaders(entityCode, 'note_owner', 'Note Owner') });
    expect((await asOwner.json()).notes.find((n: any) => n.id === noteId).editable).toBe(true);

    // A colleague can read the note but must not be offered the edit affordance.
    const asColleague = await request.get(`${QUERIES}/complaint/${complaintNumber}/internal-notes`,
      { headers: reHeaders(entityCode, 'colleague', 'Colleague') });
    expect((await asColleague.json()).notes.find((n: any) => n.id === noteId).editable).toBe(false);
  });
});
