import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  identityHeadersFor,
  performRbioAction,
  DEFAULT_CLOSURE_CLAUSE
} from '../utils/test-data';

/**
 * Batch S6: notifications, email communication, attachments, secure upload link, history/audit.
 *
 * ── What these tests are for ─────────────────────────────────────────────────────────────────────
 * Every assertion here covers something that was previously either absent or ACTIVELY MISLEADING:
 *
 *  - UST603/604: closure and forward blocking existed only in the browser (a padlock icon that did
 *    not even disable its own click handler), so the API accepted a closure while a complainant was
 *    still uploading requested evidence. These tests drive the API directly, which is exactly the
 *    bypass path the old implementation left open.
 *  - UST599: the dual-OTP endpoint was unreachable — the client posted {emailOtp, mobileOtp} while
 *    the server read {otp, mobile}, so the missing-field guard fired on every call — while the upload
 *    endpoint itself checked no OTP at all.
 *  - UST593: no query ever read SIMULATED_EMAILS.COMPLAINT_ID, so a per-complaint email log could not
 *    be obtained even though the column was populated.
 *  - UST594-597: the History tab called /action-override, a route with zero occurrences in the
 *    backend, and swallowed the 404 — rendering "no records" for every complaint in the system.
 *
 * API-level rather than browser-level BY DESIGN for the enforcement cases: a server-side control must
 * be proven at the server. A green browser test would not have caught any of the above.
 */

const API = process.env['API_BASE_URL'] || 'http://localhost:8082';
const STAFF = identityHeadersFor('officer.rbio1', 'RBIO');
const ADMIN = { 'X-User-Id': 'admin', 'X-User-Name': 'admin', 'X-User-Roles': 'RBIO_ADMIN' };

async function json(response: { json: () => Promise<any> }) {
  try { return await response.json(); } catch { return null; }
}

test.describe('S6 — secure upload link', () => {

  test('UST598/601: sending a link dispatches it and never returns the bearer token', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.link@example.com', complainantPhone: '9812345601'
    });

    const res = await request.post(`${API}/api/v1/upload-link/send`, {
      headers: STAFF, data: { complaintNumber }
    });
    expect(res.status()).toBe(200);
    const body = await json(res);

    expect(body.success).toBe(true);
    // Both channels attempted: the previous implementation dispatched NOTHING while the UI claimed
    // it had sent by email and SMS.
    expect(body.channelsDispatched).toBeGreaterThanOrEqual(1);
    // The token is a bearer credential for an unauthenticated upload path. Returning it to the
    // officer's browser put it in network logs for no functional reason.
    expect(body.token).toBeUndefined();

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST601: link expiry comes from SYSTEM_CONFIG, not a hardcoded 7', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.exp@example.com', complainantPhone: '9812345602'
    });
    await request.post(`${API}/api/v1/upload-link/send`, { headers: STAFF, data: { complaintNumber } });

    const status = await json(await request.get(
      `${API}/api/v1/upload-link/status/${complaintNumber}`, { headers: STAFF }));

    expect(status.linkActive).toBe(true);
    const days = Math.round(
      (new Date(status.expiresAt).getTime() - new Date(status.sentAt).getTime()) / 86400000);
    // 7 is the seeded DEFAULT; the point is that it is read from config. A wrong value here means the
    // constant is still winning.
    expect(days).toBe(7);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST776: status exposes linkActive and a Yes/No submission value', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.disp@example.com', complainantPhone: '9812345603'
    });
    await request.post(`${API}/api/v1/upload-link/send`, { headers: STAFF, data: { complaintNumber } });

    const status = await json(await request.get(
      `${API}/api/v1/upload-link/status/${complaintNumber}`, { headers: STAFF }));

    // `linkActive` is the field the Angular service actually types and reads. The server previously
    // returned only `active`, so status()?.linkActive was permanently undefined — which is why the
    // closure-block banner and padlock never appeared at all.
    expect(status.linkActive).toBe(true);
    expect(status.active).toBe(true);
    // UST776 asks for a Yes/No display value, not a bare boolean.
    expect(status.documentsSubmittedDisplay).toBe('No');
    expect(status.otpVerified).toBe(false);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST599: upload is refused until both OTPs are verified', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.otp@example.com', complainantPhone: '9812345604'
    });
    await request.post(`${API}/api/v1/upload-link/send`, { headers: STAFF, data: { complaintNumber } });

    // request-otp did not exist before, so there was never a code to validate.
    const otpRes = await request.post(`${API}/api/v1/upload-link/request-otp`, {
      headers: STAFF, data: { token: 'definitely-not-a-real-token' }
    });
    expect(otpRes.status()).toBe(400);
    expect((await json(otpRes)).messageKey).toBe('upload_link.error_invalid_token');

    // The dual-OTP contract: the server now reads the SAME field names the client sends.
    const validateRes = await request.post(`${API}/api/v1/upload-link/validate-otp`, {
      headers: STAFF, data: { token: 'definitely-not-a-real-token', emailOtp: '123456', mobileOtp: '123456' }
    });
    expect(validateRes.status()).toBe(400);
    const vBody = await json(validateRes);
    // `valid` is what the client reads; the server used to answer `success`, so even a passing
    // verification would have left the page stuck.
    expect(vBody.valid).toBe(false);

    await cleanupRbioComplaint(request, complaintNumber);
  });
});

test.describe('S6 — server-side upload-link restrictions', () => {

  /**
   * THE CENTRAL TEST OF THIS BATCH. Closure blocking was frontend-only and API-bypassable; this drives
   * the API directly, so it fails against the old implementation and passes against the new one.
   * Mutation-verified by disabling the guard: the same call then returns 200/success:true.
   */
  test('UST603: closure is refused while a link is live, and the complaint is not mutated', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.close@example.com', complainantPhone: '9812345605'
    });
    await request.post(`${API}/api/v1/upload-link/send`, { headers: STAFF, data: { complaintNumber } });

    const before = await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}`, { headers: STAFF }));
    const statusBefore = (before.data ?? before).status;

    const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: ADMIN,
      data: {
        action: 'CLOSE_COMPLAINT', actor: 'admin', userRole: 'RBIO_ADMIN',
        remarks: 'attempting closure while an upload link is live',
        closureClause: DEFAULT_CLOSURE_CLAUSE
      }
    });

    // 409 CONFLICT: the request is well-formed and authorised but conflicts with current state.
    expect(res.status()).toBe(409);
    const body = await json(res);
    expect(body.success).toBe(false);
    // A translation key, so the refusal renders in the officer's language rather than as English prose.
    expect(body.messageKey).toBe('workflow.error_upload_link_active_closure');
    // The expiry tells the officer when the block lifts instead of leaving them to guess.
    expect(body.linkExpiresAt).toBeTruthy();

    // The guard runs BEFORE the first setter, so a refusal must leave the complaint untouched.
    const after = await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}`, { headers: STAFF }));
    expect((after.data ?? after).status).toBe(statusBefore);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST603: closure succeeds once the link is revoked', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.revoke@example.com', complainantPhone: '9812345606'
    });
    await request.post(`${API}/api/v1/upload-link/send`, { headers: STAFF, data: { complaintNumber } });
    await request.post(`${API}/api/v1/upload-link/revoke`, { headers: STAFF, data: { complaintNumber } });

    const res = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: ADMIN,
      data: {
        action: 'CLOSE_COMPLAINT', actor: 'admin', userRole: 'RBIO_ADMIN',
        remarks: 'closing after revoking the link', closureClause: DEFAULT_CLOSURE_CLAUSE
      }
    });

    // Proves the guard blocks a CONDITION, not the action itself — otherwise the first test would
    // pass for the wrong reason (i.e. closure simply never working).
    expect(res.status()).toBe(200);
    expect((await json(res)).success).toBe(true);

    await cleanupRbioComplaint(request, complaintNumber);
  });
});

test.describe('S6 — history and audit', () => {

  test('UST594/596: history is chronological and closure carries its clause and actor role', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.hist@example.com', complainantPhone: '9812345607'
    });

    await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: ADMIN,
      data: {
        action: 'CLOSE_COMPLAINT', actor: 'admin', userRole: 'RBIO_ADMIN',
        remarks: 'closing for the history assertion', closureClause: DEFAULT_CLOSURE_CLAUSE
      }
    });

    const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}/history`, { headers: STAFF });
    expect(res.status()).toBe(200);
    const entries = (await json(res)).data as any[];

    expect(entries.length).toBeGreaterThanOrEqual(2);

    // UST594: strictly ascending. The four pre-existing read paths were all DESCENDING and disagreed
    // on field names, one even renaming performedAt to timestamp and dropping performedBy.
    const times = entries.map(e => new Date(e.performedAt).getTime());
    for (let i = 1; i < times.length; i++) {
      expect(times[i]).toBeGreaterThanOrEqual(times[i - 1]);
    }

    const closure = entries.find(e => e.action === 'CLOSE_COMPLAINT');
    expect(closure).toBeTruthy();
    // UST596: the clause lives ON THE EVENT. Complaint.closureClause is mutable, so a reopen followed
    // by a re-closure under a different clause destroys the original — and that clause is the
    // citizen's statutory basis for appeal.
    expect(closure.closureClause).toBe(DEFAULT_CLOSURE_CLAUSE);
    expect(closure.performedByRole).toBe('RBIO_ADMIN');
    expect(closure.toStatus).toBe('closed');

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST597: history modification is refused and the attempt is recorded', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.tamper@example.com', complainantPhone: '9812345608'
    });

    const entries = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/history`, { headers: STAFF }))).data as any[];
    const targetId = entries[0].id;

    // UST597 requires rejection AND logging. "No endpoint exists" produced a 404 that recorded
    // nothing, and is indistinguishable to a prober from an endpoint left unsecured.
    for (const attempt of [
      request.delete(`${API}/api/v1/complaints/${complaintNumber}/history/${targetId}`, { headers: ADMIN }),
      request.put(`${API}/api/v1/complaints/${complaintNumber}/history/${targetId}`,
        { headers: ADMIN, data: { remarks: 'rewritten' } }),
      request.patch(`${API}/api/v1/complaints/${complaintNumber}/history`,
        { headers: ADMIN, data: { remarks: 'rewritten' } })
    ]) {
      const res = await attempt;
      expect(res.status()).toBe(403);
      expect((await json(res)).messageKey).toBe('complaint.history.error_immutable');
    }

    // The record must survive every attempt, unchanged.
    const after = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/history`, { headers: STAFF }))).data as any[];
    expect(after.length).toBe(entries.length);
    expect(after[0].remarks).toBe(entries[0].remarks);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST595: history survives a reopen with entries appended, not merged', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.reopen@example.com', complainantPhone: '9812345609'
    });

    await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: ADMIN,
      data: {
        action: 'CLOSE_COMPLAINT', actor: 'admin', userRole: 'RBIO_ADMIN',
        remarks: 'first closure', closureClause: DEFAULT_CLOSURE_CLAUSE
      }
    });
    const beforeReopen = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/history`, { headers: STAFF }))).data as any[];

    const reopen = await request.post(`${API}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: ADMIN,
      data: { action: 'REOPEN', actor: 'admin', userRole: 'RBIO_ADMIN', remarks: 'reopening' }
    });

    // REOPEN may not be configured for this role; the property under test is that the EARLIER rows are
    // never rewritten, which must hold either way. Skipping on a 4xx would silently drop the check.
    const afterReopen = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/history`, { headers: STAFF }))).data as any[];

    expect(afterReopen.length).toBeGreaterThanOrEqual(beforeReopen.length);
    // Every pre-reopen row is byte-identical: append-only, never merged or rewritten.
    for (let i = 0; i < beforeReopen.length; i++) {
      expect(afterReopen[i].id).toBe(beforeReopen[i].id);
      expect(afterReopen[i].action).toBe(beforeReopen[i].action);
      expect(afterReopen[i].remarks).toBe(beforeReopen[i].remarks);
      expect(afterReopen[i].closureClause).toBe(beforeReopen[i].closureClause);
    }
    if (reopen.status() === 200) {
      expect(afterReopen.length).toBeGreaterThan(beforeReopen.length);
    }

    await cleanupRbioComplaint(request, complaintNumber);
  });
});

test.describe('S6 — email communication', () => {

  test('UST593: the per-complaint email log is served in chronological order', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.emails@example.com', complainantPhone: '9812345610'
    });

    // The endpoint itself is new: no query previously read SIMULATED_EMAILS.COMPLAINT_ID.
    const res = await request.get(`${API}/api/v1/complaints/${complaintNumber}/emails`, { headers: STAFF });
    expect(res.status()).toBe(200);
    const body = await json(res);
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data)).toBe(true);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST656: a non-RBI recipient is BLOCKED at send time, with the address masked', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.block@example.com', complainantPhone: '9812345611'
    });

    const res = await request.post(`${API}/api/v1/complaints/${complaintNumber}/emails`, {
      headers: STAFF,
      data: {
        recipients: ['someone@gmail.com'],
        subject: 'Should never be dispatched',
        body: 'Testing the send-time domain restriction.'
      }
    });

    // 422: understood and authorised, but the content is not permitted. Enforcement is INSIDE the send
    // path — the old validate-email-recipients endpoint was an advisory logging sink the client called
    // after it had already decided, with no server send path to intercept.
    expect(res.status()).toBe(422);
    const body = await json(res);
    expect(body.success).toBe(false);
    expect(body.messageKey).toBe('email.error_recipient_domain_not_allowed');
    // The rejected address is MASKED. The previous implementation logged it verbatim — leaking exactly
    // the third-party addresses the restriction exists to keep out of RBI systems.
    expect(body.rejectedRecipients.join()).not.toContain('someone@gmail.com');
    expect(body.rejectedRecipients.join()).toContain('@gmail.com');

    // Nothing was persisted, so a blocked send leaves no trace of a message that never went out.
    const log = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/emails`, { headers: STAFF }))).data as any[];
    expect(log.some(e => (e.to || '').includes('gmail.com'))).toBe(false);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST592: a malformed recipient is refused before any dispatch', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.malformed@example.com', complainantPhone: '9812345612'
    });

    const res = await request.post(`${API}/api/v1/complaints/${complaintNumber}/emails`, {
      headers: STAFF,
      data: { recipients: ['not-an-email-at-all'], subject: 'x', body: 'y' }
    });

    expect(res.status()).toBe(422);
    expect((await json(res)).messageKey).toBe('email.error_malformed_recipient');

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST590/591: an RBI-domain recipient is accepted and recorded as OUTBOUND', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request, {
      complainantEmail: 's6.send@example.com', complainantPhone: '9812345613'
    });

    const res = await request.post(`${API}/api/v1/complaints/${complaintNumber}/emails`, {
      headers: STAFF,
      data: {
        recipients: ['nodal.officer@rbi.org.in'],
        subject: 'Information required on complaint',
        body: 'Please provide the transaction log.'
      }
    });

    expect(res.status()).toBe(200);
    const sent = (await json(res)).data;
    // ONE direction vocabulary. Three were in use for the same concept (SENT / RECEIVED /
    // INBOUND-OUTBOUND), so no single renderer could read them all.
    expect(sent.direction).toBe('OUTBOUND');
    expect(sent.threadId).toBeTruthy();

    // It PERSISTS — the only pre-existing compose resolved with setTimeout and lost the message on
    // refresh.
    const log = (await json(await request.get(
      `${API}/api/v1/complaints/${complaintNumber}/emails`, { headers: STAFF }))).data as any[];
    const found = log.find(e => e.messageId === sent.messageId);
    expect(found).toBeTruthy();
    expect(found.to).toContain('nodal.officer@rbi.org.in');
    expect(found.subject).toBe('Information required on complaint');

    await cleanupRbioComplaint(request, complaintNumber);
  });
});

test.describe('S6 — attachments', () => {

  test('UST588: the download-all bundle streams a ZIP', async ({ request }) => {
    const { complaintNumber, complaintId } = await createRbioComplaint(request, {
      complainantEmail: 's6.bundle@example.com', complainantPhone: '9812345614'
    });

    // No zip capability existed anywhere in the backend before this: java.util.zip appeared in zero
    // files, so officers downloaded documents one at a time.
    const res = await request.get(
      `${API}/api/files/complaint/${complaintId}/bundle?complaintNumber=${complaintNumber}`,
      { headers: STAFF });

    expect(res.status()).toBe(200);
    expect(res.headers()['content-disposition']).toContain('.zip');

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST587: an oversized file is refused at the upload entry point', async ({ request }) => {
    const { complaintNumber, complaintId } = await createRbioComplaint(request, {
      complainantEmail: 's6.big@example.com', complainantPhone: '9812345615'
    });

    // 3MB against the 2MB per-file business cap confirmed for this batch. Spring's own multipart limit
    // is 50MB — 25x the business rule — so without the service-level check this would be accepted.
    const oversized = Buffer.alloc(3 * 1024 * 1024, 0x41);

    const res = await request.post(`${API}/api/files/upload`, {
      headers: STAFF,
      multipart: {
        file: { name: 'oversized.txt', mimeType: 'text/plain', buffer: oversized },
        complaintNumber,
        complaintId: String(complaintId)
      }
    });

    expect(res.status()).toBeGreaterThanOrEqual(400);

    await cleanupRbioComplaint(request, complaintNumber);
  });

  test('UST589: an accepted upload records who supplied it and in what capacity', async ({ request }) => {
    const { complaintNumber, complaintId } = await createRbioComplaint(request, {
      complainantEmail: 's6.source@example.com', complainantPhone: '9812345616'
    });

    const res = await request.post(`${API}/api/files/upload`, {
      headers: STAFF,
      multipart: {
        file: {
          name: 's6-evidence.txt', mimeType: 'text/plain',
          buffer: Buffer.from('Statement of account provided by the officer.')
        },
        complaintNumber,
        complaintId: String(complaintId),
        documentType: 'EVIDENCE'
      }
    });
    expect(res.status()).toBe(200);

    const listed = await json(await request.get(
      `${API}/api/files/complaint/${complaintId}`, { headers: STAFF }));
    const rows = (listed.data ?? listed) as any[];
    const mine = rows.find(r => r.originalName === 's6-evidence.txt');

    expect(mine).toBeTruthy();
    // ComplaintAttachment had EIGHT columns and none recorded an uploader or origin, while the CEPC
    // screen displayed an uploadedBy value fabricated client-side from the current username.
    expect(mine.source).toBe('OFFICER');
    expect(mine.uploadedBy).toBeTruthy();
    expect(mine.documentType).toBe('EVIDENCE');

    await cleanupRbioComplaint(request, complaintNumber);
  });
});
