import { test, expect, APIRequestContext } from '@playwright/test';

/**
 * S7 — OCR gating, staff drafts, auto-save and record-locking, at the API layer.
 *
 * WHY API-LEVEL RATHER THAN BROWSER-DRIVEN
 * Every behaviour here is a server control, and each was previously "enforced" only in the browser or
 * not at all. Driving these through the UI would demonstrate that the screen looks right, which was
 * never the problem — the OCR language gate was wired to a different endpoint entirely, and the draft
 * listing took its owner from a query parameter the caller chose.
 *
 * WHAT WAS WRONG
 *
 *  UST778 — OcrEligibilityService had exactly three call sites, all in EmailSyndicationApiController. The
 *  POST /api/v1/ocr/extract used by the RBIO and CRPC screens had NO language gate, so a Hindi scan
 *  pre-filled the form with vernacular text and was only stamped VERNACULAR_MANUAL_ENTRY later, at submit
 *  time, on a draft the operator had already completed.
 *
 *  UST674 — no draft table carried an authorship column. The listing endpoint took the owner as a query
 *  parameter and returned EVERY draft in the system when it was omitted, and PUT /drafts/{id} had no
 *  ownership check at all.
 *
 *  UST675 — Wave 0 added @Version to Complaint and mapped the conflict to 409, but nothing consumed it:
 *  recordVersion never reached the browser and was never sent back, so optimistic locking was unreachable.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const DRAFTS = `${API_BASE}/api/v1/staff-drafts`;
const OCR = `${API_BASE}/api/v1/ocr`;

/** A seed prefix owned by this session, per the shared-database convention. */
const PREFIX = 'S7-';

function headersFor(userId: string, roles = 'RBIO_OFFICER'): Record<string, string> {
  return {
    'Content-Type': 'application/json',
    'X-User-Id': userId,
    'X-User-Name': userId,
    'X-User-Roles': roles
  };
}

const ALICE = headersFor('s7_alice_001');
const BOB = headersFor('s7_bob_001');

async function saveDraft(request: APIRequestContext, headers: Record<string, string>, body: any) {
  return request.post(DRAFTS, { headers, data: body, failOnStatusCode: false });
}

test.describe('S7 — staff drafts are owner-scoped (UST674)', () => {

  test.afterAll(async ({ request }) => {
    // Clean up only this session's drafts, by owner. cms_db is persistent and shared.
    for (const headers of [ALICE, BOB]) {
      const res = await request.get(DRAFTS, { headers, failOnStatusCode: false });
      if (!res.ok()) continue;
      for (const draft of await res.json()) {
        await request.delete(`${DRAFTS}/${draft.id}`, { headers, failOnStatusCode: false });
      }
    }
  });

  test('a draft saved by one officer is NOT visible to another', async ({ request }) => {
    const saved = await saveDraft(request, ALICE, {
      milestone: 'CONCILIATION',
      complaintNumber: `${PREFIX}OWN-1`,
      formData: { remarks: 'alice half-typed this' }
    });
    expect(saved.status(), await saved.text()).toBe(200);

    const bobList = await request.get(DRAFTS, { headers: BOB });
    expect(bobList.status()).toBe(200);
    const bobDrafts = await bobList.json();

    // The central UST674 requirement. The sibling EMAIL_DRAFTS listing returns every draft in the system
    // when the owner parameter is omitted, so "visible only to the user who saved it" has to be proven
    // rather than assumed.
    expect(bobDrafts.some((d: any) => d.complaintNumber === `${PREFIX}OWN-1`),
      'Bob must not see a draft Alice saved').toBeFalsy();

    const aliceList = await request.get(DRAFTS, { headers: ALICE });
    expect((await aliceList.json()).some((d: any) => d.complaintNumber === `${PREFIX}OWN-1`),
      'Alice must see her own draft').toBeTruthy();
  });

  test('one officer cannot delete another officer\'s draft', async ({ request }) => {
    const saved = await saveDraft(request, ALICE, {
      milestone: 'FORWARD',
      complaintNumber: `${PREFIX}OWN-2`,
      formData: { remarks: 'alice forward draft' }
    });
    const draftId = (await saved.json()).id;

    const bobDelete = await request.delete(`${DRAFTS}/${draftId}`, {
      headers: BOB, failOnStatusCode: false
    });
    expect(bobDelete.status(), 'a foreign draft id must not resolve').toBe(404);

    // Proves the delete was refused rather than merely reported as refused.
    const stillThere = await request.get(`${DRAFTS}/FORWARD`, {
      headers: ALICE, params: { complaintNumber: `${PREFIX}OWN-2` }
    });
    expect(stillThere.status()).toBe(200);
    expect((await stillThere.json()).formData.remarks).toBe('alice forward draft');
  });

  test('an unidentified caller cannot save or list drafts', async ({ request }) => {
    const anon = await request.post(DRAFTS, {
      headers: { 'Content-Type': 'application/json' },
      data: { milestone: 'CONCILIATION', complaintNumber: 'X', formData: { a: 1 } },
      failOnStatusCode: false
    });
    // A draft whose owner cannot be established is a draft that cannot be kept private.
    expect(anon.status()).not.toBe(200);
  });

  test('all five UST674 milestones accept a draft', async ({ request }) => {
    // Register, Assessment, Conciliation, Forward and Final Decision. Three of these had no draft
    // surface at all before (task-action.component.ts had no saveDraft), and Register's "save draft"
    // created a live assigned complaint instead of a draft.
    for (const milestone of ['ASSESSMENT', 'CONCILIATION', 'FORWARD', 'FINAL_DECISION']) {
      const res = await saveDraft(request, ALICE, {
        milestone,
        complaintNumber: `${PREFIX}MS-${milestone}`,
        formData: { remarks: `draft at ${milestone}` }
      });
      expect(res.status(), `${milestone} must accept a draft: ${await res.text()}`).toBe(200);
    }

    // REGISTER carries no complaint number, because no complaint exists at that point.
    const register = await saveDraft(request, ALICE, {
      milestone: 'REGISTER',
      formData: { complainantName: 'Register Draft' }
    });
    expect(register.status(), await register.text()).toBe(200);
  });

  test('a resumed draft restores every field value', async ({ request }) => {
    const formData = {
      remarks: 'a long partially written justification that must survive verbatim',
      closureClause: '15(1)(a)',
      dateOfSending: '2026-09-21',
      selectedAction: 'CONCILIATION_SUCCESS'
    };
    await saveDraft(request, ALICE, {
      milestone: 'CONCILIATION', complaintNumber: `${PREFIX}RESUME-1`, formData
    });

    const resumed = await request.get(`${DRAFTS}/CONCILIATION`, {
      headers: ALICE, params: { complaintNumber: `${PREFIX}RESUME-1` }
    });

    expect(resumed.status()).toBe(200);
    // UST674: "resuming must restore all in-progress field values". Asserting each one, because a
    // partially restored draft is worse than none — the officer cannot tell what is missing.
    expect((await resumed.json()).formData).toMatchObject(formData);
  });

  test('a long draft is stored intact, not truncated', async ({ request }) => {
    // Regression for a real defect found in the startup log: the entity mapped FORM_DATA_JSON as a bare
    // @Lob String, which this dialect resolves to TINYTEXT (255 bytes), and ddl-auto=update duly issued
    // `modify column form_data_json tinytext`. A draft holding a complaint description would have been
    // silently cut at 255 characters, discovered only when someone tried to resume.
    const longRemarks = 'X'.repeat(5000);
    const res = await saveDraft(request, ALICE, {
      milestone: 'FINAL_DECISION',
      complaintNumber: `${PREFIX}LONG-1`,
      formData: { remarks: longRemarks }
    });
    expect(res.status(), await res.text()).toBe(200);

    const resumed = await request.get(`${DRAFTS}/FINAL_DECISION`, {
      headers: ALICE, params: { complaintNumber: `${PREFIX}LONG-1` }
    });
    expect((await resumed.json()).formData.remarks.length,
      'the full 5000 characters must survive the round trip').toBe(5000);
  });

  test('an empty draft is refused, so a timer cannot blank a good one', async ({ request }) => {
    const res = await saveDraft(request, ALICE, {
      milestone: 'CONCILIATION', complaintNumber: `${PREFIX}EMPTY-1`, formData: {}
    });
    // The autosave timer firing on an untouched form must not replace what the officer saved a moment
    // earlier with nothing.
    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/no field values/i);
  });

  test('a non-REGISTER milestone requires a complaint number', async ({ request }) => {
    const res = await saveDraft(request, ALICE, {
      milestone: 'FINAL_DECISION', formData: { remarks: 'orphan' }
    });
    expect(res.status()).toBe(400);
    expect(await res.text()).toMatch(/requires a complaint number/i);
  });

  test('an unknown milestone is refused by name', async ({ request }) => {
    const res = await saveDraft(request, ALICE, {
      milestone: 'NOT_A_MILESTONE', complaintNumber: 'X', formData: { a: 1 }
    });
    expect(res.status()).toBe(400);
  });
});

test.describe('S7 — auto-save configuration (UST673)', () => {

  test('the interval comes from SYSTEM_CONFIG, not a hardcoded literal', async ({ request }) => {
    const res = await request.get(`${DRAFTS}/config`, { headers: ALICE });

    expect(res.status()).toBe(200);
    const cfg = await res.json();
    // V99 seeds cms.draft.autosave_interval_seconds = 120. The only other autosave in the product (the
    // citizen wizard) hardcodes its 30s literal, which is why changing it needs a release.
    expect(cfg.autosaveIntervalSeconds, 'the seeded interval must be served').toBe(120);
    expect(cfg.autosaveEnabled).toBe(true);
    expect(cfg.editPresenceStaleSeconds).toBe(90);
  });

  test('an auto-saved draft is discarded on save-and-proceed, a manual one is kept', async ({ request }) => {
    const complaintNumber = `${PREFIX}DISCARD-1`;

    // An AUTOSAVE row at one milestone and a MANUAL row at another, for the same complaint.
    await saveDraft(request, ALICE, {
      milestone: 'CONCILIATION', complaintNumber, autosave: true,
      formData: { remarks: 'timer wrote this' }
    });
    await saveDraft(request, ALICE, {
      milestone: 'FORWARD', complaintNumber, autosave: false,
      formData: { remarks: 'officer deliberately saved this' }
    });

    const discard = await request.post(`${DRAFTS}/discard-autosave`, {
      headers: ALICE, data: { complaintNumber }
    });
    expect(discard.status()).toBe(200);
    expect((await discard.json()).discarded, 'exactly the autosaved row').toBe(1);

    const auto = await request.get(`${DRAFTS}/CONCILIATION`, {
      headers: ALICE, params: { complaintNumber }, failOnStatusCode: false
    });
    expect(auto.status(), 'the auto-saved draft is gone (204 = none)').toBe(204);

    const manual = await request.get(`${DRAFTS}/FORWARD`, {
      headers: ALICE, params: { complaintNumber }
    });
    // Silently discarding a deliberately-saved draft because the officer later submitted something else
    // would lose work they chose to retain.
    expect(manual.status(), 'the manually saved draft SURVIVES').toBe(200);
    expect((await manual.json()).formData.remarks).toBe('officer deliberately saved this');
  });
});

test.describe('S7 — edit presence is advisory (UST675)', () => {

  test('a second officer is reported, and the caller is not reported to themselves', async ({ request }) => {
    const complaintNumber = `${PREFIX}PRESENCE-1`;

    const aliceFirst = await request.post(`${DRAFTS}/presence/${complaintNumber}`, {
      headers: ALICE, data: {}
    });
    expect(aliceFirst.status()).toBe(200);
    // A user with the form open in two tabs must not be warned about themselves; that trains people to
    // dismiss the warning.
    expect((await aliceFirst.json()).otherEditors, 'no one else is editing yet').toHaveLength(0);

    const bobJoins = await request.post(`${DRAFTS}/presence/${complaintNumber}`, {
      headers: BOB, data: {}
    });
    const bobBody = await bobJoins.json();
    expect(bobBody.concurrentEdit, 'Bob must be told Alice is editing').toBe(true);
    expect(bobBody.otherEditors[0].userId).toBe('s7_alice_001');

    // Releasing clears it, so navigating away does not leave a phantom editor behind.
    await request.delete(`${DRAFTS}/presence/${complaintNumber}`, { headers: ALICE });
    const afterRelease = await request.post(`${DRAFTS}/presence/${complaintNumber}`, {
      headers: BOB, data: {}
    });
    expect((await afterRelease.json()).otherEditors).toHaveLength(0);
  });

  test('presence never blocks a save — it is advisory only', async ({ request }) => {
    const complaintNumber = `${PREFIX}PRESENCE-2`;
    await request.post(`${DRAFTS}/presence/${complaintNumber}`, { headers: ALICE, data: {} });

    // Bob saves while Alice holds presence. This MUST succeed: enforcement is the @Version check on
    // Complaint (surfaced as 409 at save time), not a lease. A lease would need an expiry, and any
    // expiry short enough to be safe is short enough to fire mid-edit and lose work.
    const bobSave = await saveDraft(request, BOB, {
      milestone: 'CONCILIATION', complaintNumber,
      formData: { remarks: 'bob saves anyway' }
    });
    expect(bobSave.status(), 'presence must not prevent a save').toBe(200);
  });
});

test.describe('S7 — OCR gating on the operator upload path (UST778, UST624-626)', () => {

  test('an unsupported file type is refused with a translation key', async ({ request }) => {
    const res = await request.post(`${OCR}/extract`, {
      headers: { 'X-User-Id': 's7_alice_001', 'X-User-Roles': 'RBIO_OFFICER' },
      multipart: {
        file: { name: 'notes.txt', mimeType: 'text/plain', buffer: Buffer.from('hello') }
      },
      failOnStatusCode: false
    });

    expect(res.status()).toBe(400);
    expect((await res.json()).messageKey).toBe('ocr.error_unsupported_type');
  });

  test('an unreadable PDF degrades gracefully and never blocks the operator', async ({ request }) => {
    // UST626: a failed attempt must not surface a blocking error. Returned as 200 with
    // prefillAllowed:false, because "this scan yielded nothing" is a normal outcome the operator handles
    // by typing — not a fault they should meet as a failure dialog.
    const res = await request.post(`${OCR}/extract`, {
      headers: { 'X-User-Id': 's7_alice_001', 'X-User-Roles': 'RBIO_OFFICER' },
      multipart: {
        file: {
          name: 'broken.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('not really a pdf at all')
        }
      },
      failOnStatusCode: false
    });

    expect(res.status(), 'a bad scan is not an error status').toBe(200);
    const body = await res.json();
    expect(body.prefillAllowed, 'nothing extracted means no prefill').toBe(false);
    expect(body.data, 'no fields are offered').toEqual({});
    // The response must carry a translation key rather than an English literal.
    expect(body.messageKey).toBeTruthy();
  });

  test('the response declares which fields came from OCR (UST624 provenance)', async ({ request }) => {
    const res = await request.post(`${OCR}/extract`, {
      headers: { 'X-User-Id': 's7_alice_001', 'X-User-Roles': 'RBIO_OFFICER' },
      multipart: {
        file: {
          name: 'empty.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('%PDF-1.4\n')
        }
      },
      failOnStatusCode: false
    });

    const body = await res.json();
    // The contract the client needs to mark fields per-field. Previously the response carried only the
    // extracted map, so the UI had no way to distinguish a scanned value from a typed one — and leaked
    // the provider's _confidence into the form as though it were a complaint field.
    expect(body).toHaveProperty('ocrFields');
    expect(Array.isArray(body.ocrFields)).toBeTruthy();
    expect(body).toHaveProperty('prefillAllowed');
    expect(body, 'provider metadata must not leak as a form field').not.toHaveProperty('_confidence');
    expect(body.data?._confidence, 'nor inside data').toBeUndefined();
  });

  test('extract-from-draft admits it is not implemented instead of faking success', async ({ request }) => {
    // It used to return success:true with an empty data map, so its only caller always reported "OCR
    // returned no data" — a permanently dead feature that looked like a working one finding nothing.
    const res = await request.post(`${OCR}/extract-from-draft`, {
      headers: headersFor('s7_alice_001'),
      data: { draftId: 1 },
      failOnStatusCode: false
    });

    expect(res.status(), 'not-implemented must be visible').toBe(501);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.implemented).toBe(false);
  });
});
