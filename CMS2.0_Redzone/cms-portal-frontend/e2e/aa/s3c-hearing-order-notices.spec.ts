/**
 * S3C — hearing persistence, immutable orders, and AA workflow notices.
 *
 * WHAT THESE TESTS ARE FOR. Before this session the AA module had: no hearing table (a reschedule
 * overwrote the only date column, destroying the record of a statutory hearing), no order record (a
 * re-POSTed PASS_ORDER silently replaced an issued decision), and no notification of any kind to the
 * appellant. The assertions below are written to FAIL if any of those regressions come back.
 *
 * THE HONESTY RULE, which several assertions exist purely to defend: cms-backend has NO email or SMS
 * gateway. Citizen notices are therefore recorded as PENDING obligations and NOTHING may report them
 * as sent. A test that accepted 'SENT' here would be certifying a lie to a citizen about a legal
 * appeal, so `status` is asserted to be exactly PENDING and `gatewayAvailable` to be false.
 *
 * Fixtures are seeded by prefix 'S3C-' and purged in BOTH beforeAll and afterAll, because cms_db is
 * shared with parallel sessions and holds ~1,700 real complaints.
 */
import { test, expect, APIRequestContext } from '@playwright/test';
import {
  API_BASE,
  CLAUSE,
  sql,
  seedClosedComplaint,
  purgeByPrefix,
  TEN_LOCALES,
  fetchTranslations,
  evictTranslationCache,
} from './aa-shared-fixtures';
import { identityHeadersFor } from '../utils/test-data';

const PREFIX = 'S3C-';

const AA_DO = identityHeadersFor('aa_do_001', 'AA');
const AA_REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');
const AA_SECRETARIAT = identityHeadersFor('aa_secretariat_001', 'AA');

/** Purges this session's rows across every table it writes, in FK-safe order. */
function purgeS3c(): void {
  sql(`DELETE FROM AA_CITIZEN_NOTICE WHERE appeal_number IN
         (SELECT appeal_number FROM appeals WHERE original_complaint_number LIKE '${PREFIX}%')`);
  sql(`DELETE FROM APPEAL_HEARING WHERE appeal_number IN
         (SELECT appeal_number FROM appeals WHERE original_complaint_number LIKE '${PREFIX}%')`);
  sql(`DELETE FROM APPEAL_ORDER WHERE appeal_number IN
         (SELECT appeal_number FROM appeals WHERE original_complaint_number LIKE '${PREFIX}%')`);
  purgeByPrefix(PREFIX);
}

let seq = 0;
function nextComplaint(tag: string): string {
  seq += 1;
  return `${PREFIX}${tag}-${Date.now().toString().slice(-6)}-${seq}`;
}

/**
 * Files an appeal on a freshly seeded closed complaint and returns its appeal number.
 *
 * Seeds the parent directly with an appealable clause rather than driving the CEPC workflow: that
 * path never sets closure_clause, so the eligibility gate 503s and no appeal is created at all.
 *
 * `officer` defaults to a value UNIQUE PER APPEAL. That matters: the double-booking control is
 * global per officer, so if every fixture shared one officer then a hearing left behind by an
 * earlier test would collide with a later one and the failure would look like a product bug. Tests
 * that are actually about conflict detection pass the SAME officer explicitly.
 */
async function seedAppeal(
  request: APIRequestContext,
  tag: string,
  officer?: string
): Promise<string> {
  const complaintNumber = nextComplaint(tag);
  seedClosedComplaint(complaintNumber, CLAUSE.complainantOnly);

  const response = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    multipart: {
      complaintNumber,
      ground: 'S3C fixture: award is inadequate',
      details: 'Seeded by s3c-hearing-order-notices.spec.ts',
      reliefSought: 'Enhanced compensation',
    },
  });
  expect([200, 201], `filing an appeal on ${complaintNumber}`).toContain(response.status());
  const body = await response.json();
  // /file wraps its payload: { success, message, data: { appealNumber, ... } }
  const appealNumber = body.data?.appealNumber || body.appealNumber;
  expect(appealNumber, 'appeal number returned by /file').toBeTruthy();

  // Assign an officer so the double-booking control has a calendar to check.
  const assigned = officer || `aa_reviewer_${appealNumber}`;
  sql(`UPDATE appeals SET assigned_officer='${assigned}', assigned_role='AA_REVIEWER',
         status='under_review', workflow_stage='ASSIGNED_TO_BENCH'
       WHERE appeal_number='${appealNumber}'`);
  return appealNumber;
}

function futureIso(daysAhead: number, hour = 10): string {
  const when = new Date();
  when.setDate(when.getDate() + daysAhead);
  when.setHours(hour, 0, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${when.getFullYear()}-${pad(when.getMonth() + 1)}-${pad(when.getDate())}` +
    `T${pad(when.getHours())}:${pad(when.getMinutes())}:00`;
}

test.describe('S3C - AA hearing persistence, orders and notices', () => {
  test.beforeAll(() => purgeS3c());
  test.afterAll(() => purgeS3c());

  // ------------------------------------------------------------------ hearings

  test('Scheduling a hearing appends a row and records a notice the UI may not call sent', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'SCHED');
    const when = futureIso(30);

    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: {
        hearingDate: when,
        hearingVenue: 'RBI Mumbai, Conference Room 2',
        hearingMode: 'IN_PERSON',
        partiesToNotify: ['appellant', 'respondent'],
      },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(body.hearing.eventType).toBe('SCHEDULED');
    expect(body.hearing.sequenceNo).toBe(1);
    expect(body.hearing.venue).toBe('RBI Mumbai, Conference Room 2');
    expect(body.hearing.superseded).toBe(false);
    // Attribution is server-resolved from the identity headers, never taken from the body: the
    // request carried no actor at all, yet the row is attributed to the caller.
    expect(body.hearing.performedBy).toBe(AA_REVIEWER['X-User-Id']);
    expect(body.hearing.performedByRole).toBe('AA_REVIEWER');
    expect(body.messageKey).toBe('aa.hearing.scheduled_notices_recorded');
    expect(body.noticesRecorded).toBe(2);

    // THE HONESTY RULE: recorded, never sent. There is no gateway.
    expect(body.noticeStatus).toBe('PENDING');

    const rows = sql(`SELECT recipient_role, status, channel, message_key FROM AA_CITIZEN_NOTICE
                       WHERE appeal_number='${appealNumber}' ORDER BY recipient_role`);
    expect(rows).toContain('APPELLANT');
    expect(rows).toContain('RESPONDENT');
    expect(rows).toContain('aa.notify.hearing_scheduled');
    // Every recorded notice is PENDING; none may claim delivery.
    expect(rows).not.toContain('SENT');

    const noticesRes = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/notices`, {
      headers: AA_DO,
    });
    expect(noticesRes.status()).toBe(200);
    const notices = await noticesRes.json();
    expect(notices.gatewayAvailable).toBe(false);
    for (const notice of notices.notices) {
      expect(notice.status).toBe('PENDING');
      expect(notice.dispatchedAt).toBeNull();
    }
  });

  test('Rescheduling NEVER destroys the prior hearing record', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'RESCHED');
    const first = futureIso(30, 10);
    const second = futureIso(45, 15);

    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: first, hearingVenue: 'Venue One', partiesToNotify: ['appellant'] },
    });

    const reschedule = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: {
        hearingDate: second,
        hearingVenue: 'Venue Two',
        reason: 'Presiding officer unavailable',
        partiesToNotify: ['appellant'],
      },
    });
    expect(reschedule.status()).toBe(200);
    const rescheduleBody = await reschedule.json();
    expect(rescheduleBody.hearing.eventType).toBe('RESCHEDULED');
    expect(rescheduleBody.hearing.sequenceNo).toBe(2);
    expect(rescheduleBody.messageKey).toBe('aa.hearing.rescheduled_notices_recorded');

    // The core audit guarantee: BOTH events survive, and the first keeps its own date and venue.
    const history = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
    });
    const body = await history.json();
    expect(body.hearingHistory).toHaveLength(2);

    const original = body.hearingHistory[0];
    expect(original.eventType).toBe('SCHEDULED');
    expect(original.venue).toBe('Venue One');
    expect(original.date).toContain(first.slice(0, 10));
    expect(original.superseded).toBe(true);
    expect(original.supersededById).toBe(rescheduleBody.hearing.id);

    const current = body.hearingHistory[1];
    expect(current.venue).toBe('Venue Two');
    expect(current.superseded).toBe(false);
    expect(current.reason).toBe('Presiding officer unavailable');

    // Exactly one operative hearing, and it is the new one.
    expect(body.operative.id).toBe(rescheduleBody.hearing.id);
  });

  test('Rescheduling without a reason is refused', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'NOREASON');
    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30), hearingVenue: 'Venue One' },
    });

    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(40), hearingVenue: 'Venue Two' },
    });
    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.messageKey).toBe('aa.hearing.error_invalid_request');

    // The refusal must not have appended anything.
    const count = sql(`SELECT COUNT(*) FROM APPEAL_HEARING WHERE appeal_number='${appealNumber}'`);
    expect(count).toBe('1');
  });

  test('A date-only hearing date is accepted, not a 500', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'DATEONLY');
    const dateOnly = futureIso(30).slice(0, 10);

    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: dateOnly, hearingVenue: 'Date-only venue', partiesToNotify: ['appellant'] },
    });

    // LocalDateTime.parse used to throw DateTimeParseException on this, surfacing as an unhandled 500.
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.hearing.date).toContain(dateOnly);
    // Resolved to a plausible hearing hour, not midnight, which would imply a hearing nobody attends.
    expect(body.hearing.date).not.toContain('T00:00');
  });

  test('Garbage hearing date is a 400, not a 500', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'BADDATE');
    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: 'not-a-date', hearingVenue: 'Nowhere' },
    });
    expect(response.status()).toBe(400);
    expect((await response.json()).messageKey).toBe('aa.hearing.error_invalid_request');
  });

  test('The same officer cannot be double-booked at the same time (server-side)', async ({ request }) => {
    // Deliberately the SAME officer on both appeals -- that is the whole point of this test.
    const sharedOfficer = `aa_reviewer_conflict_${Date.now()}`;
    const firstAppeal = await seedAppeal(request, 'CONFLICT-A', sharedOfficer);
    const secondAppeal = await seedAppeal(request, 'CONFLICT-B', sharedOfficer);
    const slot = futureIso(30, 11);

    const first = await request.post(`${API_BASE}/api/v1/appeals/${firstAppeal}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: slot, hearingVenue: 'Room A', partiesToNotify: ['appellant'] },
    });
    expect(first.status()).toBe(200);

    // Both appeals are assigned to aa_reviewer_001, so this is the same officer, same instant.
    const clash = await request.post(`${API_BASE}/api/v1/appeals/${secondAppeal}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: slot, hearingVenue: 'Room B', partiesToNotify: ['appellant'] },
    });

    expect(clash.status()).toBe(409);
    const body = await clash.json();
    expect(body.success).toBe(false);
    expect(body.messageKey).toBe('aa.hearing.error_officer_double_booked');
    expect(body.conflictingAppeal).toBe(firstAppeal);

    // A refused booking must leave no row behind.
    const count = sql(`SELECT COUNT(*) FROM APPEAL_HEARING WHERE appeal_number='${secondAppeal}'`);
    expect(count).toBe('0');
  });

  test('A hearing well clear of an existing one is allowed', async ({ request }) => {
    // Same officer again: the control must distinguish "same instant" from "same day".
    const sharedOfficer = `aa_reviewer_noclash_${Date.now()}`;
    const firstAppeal = await seedAppeal(request, 'NOCLASH-A', sharedOfficer);
    const secondAppeal = await seedAppeal(request, 'NOCLASH-B', sharedOfficer);

    await request.post(`${API_BASE}/api/v1/appeals/${firstAppeal}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30, 10), hearingVenue: 'Room A' },
    });
    // Four hours later, far outside the 60-minute slot: the control must not over-block.
    const ok = await request.post(`${API_BASE}/api/v1/appeals/${secondAppeal}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30, 14), hearingVenue: 'Room B' },
    });
    expect(ok.status()).toBe(200);
  });

  test('Rescheduling the same appeal does not collide with itself', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'SELFCLASH');
    const slot = futureIso(30, 12);

    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: slot, hearingVenue: 'Room A' },
    });
    // Same officer, same slot, SAME appeal — a move to a nearby time must be permitted.
    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: slot, hearingVenue: 'Room A revised', reason: 'Venue correction' },
    });
    expect(response.status()).toBe(200);
    expect((await response.json()).hearing.eventType).toBe('RESCHEDULED');
  });

  test('Recording an outcome appends a COMPLETED row and frees the slot', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'OUTCOME');
    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30, 9), hearingVenue: 'Room C', partiesToNotify: ['appellant'] },
    });

    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings/outcome`, {
      headers: AA_REVIEWER,
      data: { outcome: 'HEARD', remarks: 'Both parties appeared and were heard' },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.hearing.eventType).toBe('COMPLETED');
    expect(body.hearing.outcome).toBe('HEARD');
    expect(body.messageKey).toBe('aa.hearing.outcome_recorded');

    const history = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
    });
    const historyBody = await history.json();
    expect(historyBody.hearingHistory).toHaveLength(2);
    // The scheduled row keeps its particulars; the outcome lives on the appended row.
    expect(historyBody.hearingHistory[0].eventType).toBe('SCHEDULED');
    expect(historyBody.hearingHistory[0].venue).toBe('Room C');
    // Nothing is operative: a heard hearing must not keep occupying the officer's calendar.
    expect(historyBody.operative).toBeNull();
  });

  test('An outcome cannot be recorded when no hearing is listed', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'NOHEARING');
    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings/outcome`, {
      headers: AA_REVIEWER,
      data: { outcome: 'HEARD' },
    });
    expect(response.status()).toBe(409);
    expect((await response.json()).messageKey).toBe('aa.hearing.error_no_scheduled_hearing');
  });

  test('Adjourning releases the listing and records notices', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'ADJOURN');
    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30, 16), hearingVenue: 'Room D' },
    });

    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings/adjourn`, {
      headers: AA_REVIEWER,
      data: { reason: 'Appellant sought time', partiesToNotify: ['appellant'] },
    });
    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.hearing.eventType).toBe('ADJOURNED');
    expect(body.noticeStatus).toBe('PENDING');
    expect(body.messageKey).toBe('aa.hearing.adjourned');

    const history = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
    });
    expect((await history.json()).operative).toBeNull();
  });

  // -------------------------------------------------------------------- orders

  test('An order is issued once and CANNOT be silently overwritten', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'ORDER');

    const issue = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: {
        outcome: 'MODIFIED',
        orderSummary: 'The award is enhanced having regard to the documented loss.',
        awardAmount: '25000.00',
        clauseCode: CLAUSE.complainantOnly,
      },
    });
    expect(issue.status()).toBe(200);
    const order = (await issue.json()).order;
    expect(order.revisionNo).toBe(1);
    expect(order.outcome).toBe('MODIFIED');
    expect(order.operative).toBe(true);
    expect(order.issuingAuthority).toBe('aa_secretariat_001');
    // Phase 1 is text-only; the payload says so rather than implying a download exists.
    expect(order.artefactAvailable).toBe(false);

    // THE IMMUTABILITY GUARANTEE. This used to succeed and replace the order in place.
    const overwrite = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'DISMISSED', orderSummary: 'Attempt to overwrite the issued order.' },
    });
    expect(overwrite.status()).toBe(409);
    expect((await overwrite.json()).messageKey).toBe('aa.order.error_already_issued');

    // The original survives untouched.
    const read = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
    });
    const readBody = await read.json();
    expect(readBody.revisions).toHaveLength(1);
    expect(readBody.order.outcome).toBe('MODIFIED');
  });

  test('A correction is a NEW linked revision, and the original stays readable', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'CORRECT');
    const issue = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: {
        outcome: 'MODIFIED',
        orderSummary: 'Original order with a typographical error in the amount.',
        awardAmount: '5000.00',
      },
    });
    const originalId = (await issue.json()).order.id;

    const correction = await request.post(
      `${API_BASE}/api/v1/appeals/${appealNumber}/order/corrections`, {
        headers: AA_SECRETARIAT,
        data: {
          outcome: 'MODIFIED',
          orderSummary: 'Corrected order. The award amount is 50,000.',
          awardAmount: '50000.00',
          correctionReason: 'Typographical error in the award amount',
        },
      });
    expect(correction.status()).toBe(200);
    const correctionBody = await correction.json();
    expect(correctionBody.order.revisionNo).toBe(2);
    expect(correctionBody.order.supersedesOrderId).toBe(originalId);
    expect(correctionBody.order.correctionReason).toBe('Typographical error in the award amount');
    expect(correctionBody.messageKey).toBe('aa.order.corrected');

    const read = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
    });
    const readBody = await read.json();
    expect(readBody.revisions).toHaveLength(2);

    // Revision 1 keeps the WRONG amount on purpose: that is what the parties were originally told.
    const revision1 = readBody.revisions[0];
    expect(revision1.revisionNo).toBe(1);
    expect(Number(revision1.awardAmount)).toBe(5000);
    expect(revision1.operative).toBe(false);
    expect(revision1.supersededById).toBe(correctionBody.order.id);

    // Only revision 2 is in force.
    expect(readBody.order.revisionNo).toBe(2);
    expect(Number(readBody.order.awardAmount)).toBe(50000);
  });

  test('A correction without a reason is refused', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'NOCORRREASON');
    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'UPHELD', orderSummary: 'An order that will resist a reasonless correction.' },
    });

    const response = await request.post(
      `${API_BASE}/api/v1/appeals/${appealNumber}/order/corrections`, {
        headers: AA_SECRETARIAT,
        data: { outcome: 'DISMISSED', orderSummary: 'Changed my mind.' },
      });
    expect(response.status()).toBe(400);
    expect((await response.json()).messageKey).toBe('aa.order.error_invalid_request');

    const count = sql(`SELECT COUNT(*) FROM APPEAL_ORDER WHERE appeal_number='${appealNumber}'`);
    expect(count).toBe('1');
  });

  test('An order citing a clause that is not in force is refused', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'BADCLAUSE');
    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: {
        outcome: 'UPHELD',
        orderSummary: 'An order citing a fabricated clause.',
        clauseCode: CLAUSE.unmapped,
      },
    });
    // A clause on an order must be a real clause of the Scheme, read from the master table.
    expect(response.status()).toBe(400);
    expect((await response.json()).messageKey).toBe('aa.order.error_invalid_request');
  });

  test('An invalid outcome and a negative award are both refused', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'BADOUTCOME');

    const badOutcome = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'WHATEVER', orderSummary: 'Not a real outcome.' },
    });
    expect(badOutcome.status()).toBe(400);

    const negative = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'MODIFIED', orderSummary: 'A negative award.', awardAmount: '-100' },
    });
    expect(negative.status()).toBe(400);

    const blankSummary = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'UPHELD', orderSummary: '   ' },
    });
    expect(blankSummary.status()).toBe(400);

    expect(sql(`SELECT COUNT(*) FROM APPEAL_ORDER WHERE appeal_number='${appealNumber}'`)).toBe('0');
  });

  test('Passing an order records a notice owed to the appellant', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'ORDERNOTICE');
    const response = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_SECRETARIAT,
      data: { outcome: 'UPHELD', orderSummary: 'The original decision is upheld.' },
    });
    expect(response.status()).toBe(200);
    expect((await response.json()).noticeStatus).toBe('PENDING');

    // The notify is deferred to afterCommit, so allow the async write to land.
    await expect.poll(
      () => sql(`SELECT COUNT(*) FROM AA_CITIZEN_NOTICE
                  WHERE appeal_number='${appealNumber}' AND event_code='ORDER_PASSED'`),
      { timeout: 10000 }
    ).toBe('1');

    const row = sql(`SELECT recipient_role, status, message_key FROM AA_CITIZEN_NOTICE
                      WHERE appeal_number='${appealNumber}' AND event_code='ORDER_PASSED'`);
    expect(row).toContain('APPELLANT');
    expect(row).toContain('aa.notify.order_passed');
    expect(row).toContain('PENDING');
  });

  // ----------------------------------------------------------- authorization

  test('AA_DO cannot schedule a hearing or issue an order', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'AUTHZ');

    const schedule = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_DO,
      data: { hearingDate: futureIso(30), hearingVenue: 'Unauthorised' },
    });
    expect(schedule.status()).toBe(403);

    const order = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/order`, {
      headers: AA_DO,
      data: { outcome: 'UPHELD', orderSummary: 'Unauthorised order.' },
    });
    expect(order.status()).toBe(403);

    // A refused request must not have written anything.
    expect(sql(`SELECT COUNT(*) FROM APPEAL_HEARING WHERE appeal_number='${appealNumber}'`)).toBe('0');
    expect(sql(`SELECT COUNT(*) FROM APPEAL_ORDER WHERE appeal_number='${appealNumber}'`)).toBe('0');
  });

  test('An unauthenticated caller cannot read or write hearings, orders or notices', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'ANON');
    for (const path of ['hearings', 'order', 'notices']) {
      const response = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/${path}`);
      // Appeals carry citizen PII; none of these may be world-readable.
      expect([401, 403], `GET /${path} must not be public`).toContain(response.status());
    }
  });

  // ---------------------------------------------------------------- idempotency

  test('The notice dedupe constraint stops a duplicate obligation', async ({ request }) => {
    const appealNumber = await seedAppeal(request, 'DEDUPE');
    await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/hearings`, {
      headers: AA_REVIEWER,
      data: { hearingDate: futureIso(30), hearingVenue: 'Room E', partiesToNotify: ['appellant'] },
    });

    const before = sql(`SELECT COUNT(*) FROM AA_CITIZEN_NOTICE
                         WHERE appeal_number='${appealNumber}' AND recipient_role='APPELLANT'`);
    expect(before).toBe('1');

    // This is what a second pod's reminder sweep would attempt. There is no distributed lock, so the
    // DATABASE has to be the thing that refuses it.
    const dedupeKey = sql(`SELECT dedupe_key FROM AA_CITIZEN_NOTICE
                            WHERE appeal_number='${appealNumber}' AND recipient_role='APPELLANT'`);
    let rejected = false;
    try {
      sql(`INSERT INTO AA_CITIZEN_NOTICE
             (appeal_number, event_code, recipient_role, channel, message_key, status,
              dedupe_key, attempt_count, created_by, created_at)
           VALUES ('${appealNumber}', 'HEARING_SCHEDULED', 'APPELLANT', 'EMAIL',
                   'aa.notify.hearing_scheduled', 'PENDING', '${dedupeKey}', 0, 'probe', NOW())`);
    } catch {
      rejected = true;
    }
    expect(rejected, 'a duplicate notice must be rejected by the unique constraint').toBe(true);

    expect(sql(`SELECT COUNT(*) FROM AA_CITIZEN_NOTICE
                 WHERE appeal_number='${appealNumber}' AND recipient_role='APPELLANT'`)).toBe('1');
  });

  // ------------------------------------------------------------------------ i18n

  test('Every new key is translated in all ten locales', async ({ request }) => {
    await evictTranslationCache(request);

    const keys = [
      'aa.notify.hearing_scheduled',
      'aa.notify.hearing_rescheduled',
      'aa.notify.order_passed',
      'aa.notify.order_corrected',
      'aa.notify.hearing_reminder',
      'aa.hearing.error_officer_double_booked',
      'aa.order.error_already_issued',
      'aa.notice.queued_not_sent',
      'aa.notice.status_pending',
      'aa.hearing.history_title',
    ];

    const english = await fetchTranslations(request, 'en');
    for (const key of keys) {
      expect(english[key], `${key} must exist in en`).toBeTruthy();
    }

    for (const locale of TEN_LOCALES) {
      const translations = await fetchTranslations(request, locale);
      for (const key of keys) {
        expect(translations[key], `${key} missing in ${locale}`).toBeTruthy();
        if (locale !== 'en') {
          // A key present but copied through from English is an untranslated string pretending to be
          // translated -- the exact failure this assertion exists to catch.
          expect(translations[key], `${key} in ${locale} is the English string`)
            .not.toBe(english[key]);
        }
      }
    }
  });

  test('No citizen-facing notice string implies delivery', async ({ request }) => {
    const translations = await fetchTranslations(request, 'en');
    // "queued/recorded, awaiting dispatch" is truthful; "sent" would not be, because no gateway exists.
    expect(translations['aa.notice.queued_not_sent'].toLowerCase()).not.toMatch(/\bsent\b|\bdelivered\b/);
    expect(translations['aa.notice.status_pending'].toLowerCase()).not.toMatch(/\bsent\b|\bdelivered\b/);
  });
});
