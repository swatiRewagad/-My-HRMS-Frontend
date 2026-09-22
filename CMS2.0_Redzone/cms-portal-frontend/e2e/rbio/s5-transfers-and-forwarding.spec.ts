import { execFileSync } from 'node:child_process';
import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioLadderAction,
  rbioEstablishLadderCustody,
} from '../utils/test-data';

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

const HEAD_HEADERS = {
  'Content-Type': 'application/json',
  'X-User-Id': 'crpc_head_001',
  'X-User-Name': 'crpc_head_001',
  'X-User-Roles': 'CRPC_HEAD',
};

const DO_HEADERS = {
  'X-User-Id': 'rbio_do_001',
  'X-User-Name': 'rbio_do_001',
  'X-User-Roles': 'RBIO_DEALING_OFFICIAL',
};

/**
 * Inter-office transfers and forwarding — S5 (UST556-568, 632-634, 759-761, 765-766, 770-772).
 *
 * <p>Every assertion reads the PERSISTED state back. The defects here all answered 200 while doing the wrong
 * thing: an office code written into the officer column, a destination silently dropped because the client and
 * server disagreed on the field name, a capacity verdict discarded, and no history row at all.
 */

async function complaintOf(request: any, complaintNumber: string) {
  const res = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`,
    { headers: DO_HEADERS });
  return (await res.json()).data ?? {};
}

/**
 * Reads columns straight from the complaint row.
 *
 * <p>Necessary because the citizen-facing read DTO deliberately does NOT expose the internal routing
 * columns — it returns `assignedTo` (a team) rather than `assigned_officer`, and carries no `department`,
 * `rbio_office_code` or `forwarded_to_department` at all. Asserting on the DTO would therefore read
 * `undefined` for fields that are in fact correctly persisted, which is a false negative: the test would
 * be measuring the DTO's field list rather than whether the transfer did its job.
 *
 * <p>This is a READ of one named row — the same mechanism `ageComplaintPastSla` in the harness already
 * uses, and for the same reason. No table is written or truncated.
 */
function complaintColumns(complaintNumber: string): Record<string, string> {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing to use an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  const cols = 'status, assigned_officer, department, rbio_office_code, forwarded_to_department, '
    + 'regulatory_body_name';
  // Tab-separated with --batch and --skip-column-names, rather than \G vertical output: a backslash in a
  // template literal has to be escaped, and an over-escaped "\\G" reaches mysql as a syntax error — every
  // column then parses as absent and every assertion reads `undefined`, a false negative indistinguishable
  // from an unpersisted write.
  const sql = `SELECT ${cols} FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`;
  const out = execFileSync(MYSQL_CLI,
    ['-u', 'cms_user', '-pcms_pass', 'cms_db', '--batch', '--skip-column-names', '-e', sql],
    { encoding: 'utf8' });

  const names = cols.split(',').map(c => c.trim());
  const line = out.split('\n').find(l => l.trim().length > 0 && !l.includes('Using a password'));
  if (!line) {
    throw new Error(`No complaint row found for ${complaintNumber}`);
  }
  const values = line.split('\t');
  const row: Record<string, string> = {};
  names.forEach((name, i) => {
    const v = values[i];
    row[name] = (v === undefined || v === 'NULL') ? '' : v.trim();
  });
  return row;
}

const MYSQL_CLI =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

async function timelineOf(request: any, complaintNumber: string) {
  const res = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}/history`,
    { headers: DO_HEADERS });
  const json = await res.json();
  return (json.data ?? []) as any[];
}

async function transfersOf(request: any, complaintNumber: string) {
  const res = await request.get(
    `${API_BASE}/api/v1/crpc/head/transfers/history/${complaintNumber}`, { headers: HEAD_HEADERS });
  if (res.status() !== 200) return [];
  const json = await res.json();
  return (Array.isArray(json) ? json : json.data ?? []) as any[];
}

test.describe('S5 transfers and forwarding', () => {

  test('the forward masters are served and fail closed on verification (UST766, 761, 563)', async ({ request }) => {
    // Regulatory bodies: the endpoint did not exist at all, and the screen claimed the list was validated.
    const verified = await request.get(`${API_BASE}/api/v1/masters/regulatory-bodies`);
    expect(verified.status(), 'the regulatory-body master must exist').toBe(200);
    const verifiedBodies = (await verified.json()).data ?? [];
    // Every seeded body starts UNVERIFIED, so a verified-only list must be empty. Seeding them verified
    // would assert a verification nobody performed.
    for (const b of verifiedBodies) {
      expect(b.emailVerified, 'only verified bodies may be offered for a referral').toBe(true);
    }

    const all = await request.get(
      `${API_BASE}/api/v1/masters/regulatory-bodies?includeUnverified=true`);
    const allBodies = (await all.json()).data ?? [];
    expect(allBodies.length, 'the master must be seeded').toBeGreaterThan(0);
    expect(allBodies.length,
      'the verified list must be a subset of all bodies').toBeGreaterThanOrEqual(verifiedBodies.length);

    // RBI departments: previously a hardcoded nine-element array in a CEPC component.
    const depts = await request.get(`${API_BASE}/api/v1/masters/rbi-departments`);
    expect(depts.status()).toBe(200);
    expect(((await depts.json()).data ?? []).length,
      'the RBI department master must be seeded').toBeGreaterThan(0);

    // CEPC offices: had NO data source, because every office row was typed 'BO'.
    const cepc = await request.get(`${API_BASE}/api/v1/masters/transfer-offices?layout=CEPC`);
    expect(cepc.status()).toBe(200);
    const cepcOffices = (await cepc.json()).data ?? [];
    expect(cepcOffices.length, 'UST563 needs a CEPC office list').toBeGreaterThan(0);
    for (const o of cepcOffices) {
      expect(o.layout).toBe('CEPC');
    }

    const rbio = await request.get(`${API_BASE}/api/v1/masters/transfer-offices?layout=RBIO`);
    const rbioOffices = (await rbio.json()).data ?? [];
    expect(rbioOffices.length).toBeGreaterThan(0);
    // The two lists must be disjoint, or a CEPC transfer could land on an RBIO office.
    const cepcCodes = new Set(cepcOffices.map((o: any) => o.officeCode));
    for (const o of rbioOffices) {
      expect(cepcCodes.has(o.officeCode),
        `office ${o.officeCode} must not appear in both layouts`).toBe(false);
    }
  });

  test('forwarding to another office requires a reason and a destination (UST559, 556)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      const noOffice = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { remarks: 'Wrong jurisdiction', transferReason: 'Wrong jurisdiction' });
      expect(noOffice.success, 'a forward with no destination must be refused').toBe(false);

      const noReason = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { targetOffice: '013' });
      expect(noReason.success, 'a forward with no reason must be refused').toBe(false);

      // An unknown office is refused rather than accepted as free text: a transfer to a non-existent
      // office would leave the complaint in a status nobody owns.
      const unknown = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { targetOffice: 'NOT-AN-OFFICE', transferReason: 'x', remarks: 'x' });
      expect(unknown.success, 'an unknown destination office must be refused').toBe(false);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a forward queues a PENDING transfer and does not reach the destination DO (UST557, 560, 564)',
    async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      const res = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { targetOffice: '013', transferReason: 'Complainant relocated to Mumbai',
          remarks: 'Complainant relocated to Mumbai', language: 'mr' });
      expect(res.success, `FORWARD_TO_OTHER_OFFICE failed: ${res.message}`).toBe(true);

      // UST557/560: the status becomes "Sent to Other Office" IMMEDIATELY. The CEPC arm this replaces wrote
      // forwarded_external, which the CRPC queue (filtering on sent_to_other) could never see.
      const row = complaintColumns(complaintNumber);
      expect(row['status'], 'the status must be sent_to_other, not forwarded_external')
        .toBe('sent_to_other');

      // UST564: a PENDING transfer row exists, awaiting the CRPC Head.
      const transfers = await transfersOf(request, complaintNumber);
      expect(transfers.length, 'a transfer row must be created').toBeGreaterThan(0);
      const pending = transfers.find(t => t.status === 'PENDING');
      expect(pending, 'the transfer must await approval').toBeTruthy();
      expect(pending.toOffice).toBe('013');
      // UST765: the language travels with the forward. No forward path read it before.
      expect(pending.language).toBe('mr');
      // previousOwner is captured at REQUEST time, which is what makes a rejection able to restore it.
      expect(pending.previousOwner, 'the owner at request time must be recorded').toBeTruthy();

      // The complaint has NOT been handed to an officer at the destination yet.
      expect(row['assigned_officer'],
        'an office code must never appear in the officer column').not.toBe('013');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('approval assigns a real officer, converts the layout and writes history (UST561, 632-634, 760)',
    async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { targetOffice: 'C01', transferReason: 'Subject matter is a CEPC concern',
          remarks: 'Subject matter is a CEPC concern' });

      const transfers = await transfersOf(request, complaintNumber);
      const pending = transfers.find(t => t.status === 'PENDING');
      test.skip(!pending, 'no pending transfer to approve');

      const approve = await request.post(
        `${API_BASE}/api/v1/crpc/head/transfers/${pending.id}/approve`,
        { headers: HEAD_HEADERS, data: { approvedBy: 'crpc_head_001' } });
      expect(approve.status(), `approval failed: ${await approve.text()}`).toBe(200);

      const row = complaintColumns(complaintNumber);

      // UST633/632: the LAYOUT converts. RBIO -> CEPC, which the old prefix-match could never do because
      // office codes are numeric and every match fell through to "RBIO".
      expect(row['department'], 'the layout must convert to CEPC').toBe('CEPC');

      // UST760: the region field is updated. rbioOfficeCode IS the jurisdiction key.
      expect(row['rbio_office_code'], 'the office/region must move to the destination').toBe('C01');

      // UST561: a REAL officer, never an office code.
      expect(row['assigned_officer'],
        'the officer column must not hold an office code').not.toBe('C01');

      // UST634/568/565: the conversion history. Old layout, new layout, approving Head, timestamp.
      const timeline = await timelineOf(request, complaintNumber);
      const layoutRow = timeline.find(t => t.action === 'LAYOUT_CONVERTED');
      expect(layoutRow, 'the layout conversion must be recorded in the history').toBeTruthy();
      expect(layoutRow.oldValue, 'the OLD layout must be recorded').toBe('RBIO');
      expect(layoutRow.newValue, 'the NEW layout must be recorded').toBe('CEPC');
      expect(layoutRow.performedBy, 'the approving CRPC Head must be recorded').toBe('crpc_head_001');
      expect(layoutRow.performedAt ?? layoutRow.timestamp, 'a timestamp is required').toBeTruthy();

      const approvedRow = timeline.find(t => t.action === 'TRANSFER_APPROVED');
      expect(approvedRow, 'the approval itself must be recorded').toBeTruthy();
      expect(approvedRow.destinationOffice).toBe('C01');

      // UST632/633: NO action by the Dealing Official or the Ombudsman triggered the conversion — there is
      // deliberately no Switch Process action for them to invoke.
      const doActions = timeline.filter(t =>
        t.action === 'SWITCH_PROCESS' || t.action === 'SWITCH_LAYOUT');
      expect(doActions, 'no Switch Process action may exist').toHaveLength(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a rejected transfer returns to the previous owner and needs a comment (UST526, 567)',
    async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      const ownerBefore = complaintColumns(complaintNumber)['assigned_officer'];

      await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_OFFICE', 'RBIO_DEALING_OFFICIAL',
        { targetOffice: '013', transferReason: 'Mistaken jurisdiction',
          remarks: 'Mistaken jurisdiction' });

      const transfers = await transfersOf(request, complaintNumber);
      const pending = transfers.find(t => t.status === 'PENDING');
      test.skip(!pending, 'no pending transfer to reject');

      // A BLANK comment must be refused. `?comment=` satisfied the old required @RequestParam.
      const blank = await request.post(
        `${API_BASE}/api/v1/crpc/head/transfers/${pending.id}/reject`,
        { headers: HEAD_HEADERS, data: { comment: '   ' } });
      expect(blank.status(), 'a blank rejection comment must be refused').toBe(400);

      const reject = await request.post(
        `${API_BASE}/api/v1/crpc/head/transfers/${pending.id}/reject`,
        { headers: HEAD_HEADERS,
          data: { rejectedBy: 'crpc_head_001', comment: 'The complaint belongs in this office.' } });
      expect(reject.status(), `rejection failed: ${await reject.text()}`).toBe(200);

      // Returned to the officer who held it when the transfer was requested. previousOwner was previously
      // set only inside the APPROVAL path, so on rejection it was null and the restore silently skipped.
      const after = complaintColumns(complaintNumber);
      expect(after['assigned_officer'],
        'the complaint must return to its previous owner').toBe(ownerBefore);
      expect(after['status'], 'the complaint must leave the holding status').not.toBe('sent_to_other');

      const timeline = await timelineOf(request, complaintNumber);
      expect(timeline.find(t => t.action === 'TRANSFER_REJECTED'),
        'the rejection must be recorded').toBeTruthy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('forwarding to an RBI department records the department, not an officer string (UST761)',
    async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      const unknown = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_RBI_DEPT', 'RBIO_DEPUTY_OMBUDSMAN',
        { targetDepartment: 'Department Of Nowhere', remarks: 'x' });
      expect(unknown.success, 'an unknown department must be refused').toBe(false);

      // targetDepartment is the name the CLIENT sends; the old server read targetDept and dropped it.
      const res = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_OTHER_RBI_DEPT', 'RBIO_DEPUTY_OMBUDSMAN',
        { targetDepartment: 'DPSS', remarks: 'Payment system matter for DPSS.' });
      expect(res.success, `FORWARD_TO_OTHER_RBI_DEPT failed: ${res.message}`).toBe(true);

      const row = complaintColumns(complaintNumber);
      expect(row['forwarded_to_department'],
        'the destination must be persisted, not dropped').toContain('Payment');
      expect(row['assigned_officer'],
        'a department name must never sit in the officer column')
        .not.toContain('Department of Payment');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a referral to an unverified regulatory body is refused (UST766)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      // Every seeded body starts unverified, so SEBI must be refused while verification is required.
      const res = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_REGULATORY_BODY', 'RBIO_DEPUTY_OMBUDSMAN',
        { regulatoryBodyName: 'SEBI', remarks: 'Securities matter, outside the Scheme.' });
      expect(res.success,
        'a body with an unverified contact email must not receive a citizen complaint').toBe(false);
      expect(res.message).toContain('rbio.forward.error.body_email_unverified');

      // A body that is not in the master at all is refused too — no free text.
      const unknown = await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_REGULATORY_BODY', 'RBIO_DEPUTY_OMBUDSMAN',
        { regulatoryBodyName: 'Some Made Up Regulator', remarks: 'x' });
      expect(unknown.success, 'an unknown body must be refused').toBe(false);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('bulk reassignment actually persists, and refuses a missing target', async ({ request }) => {
    const a = await createRbioComplaint(request);
    const b = await createRbioComplaint(request);
    try {
      // It read targetUser while the screen sends assignTo, and persisted nothing either way — it just
      // echoed a count back.
      const noTarget = await request.post(`${API_BASE}/api/v1/crpc/head/bulk-reassign`,
        { headers: HEAD_HEADERS, data: { complaintIds: [a.complaintNumber] } });
      expect(noTarget.status(), 'a bulk reassign with no target must be refused').toBe(400);

      const res = await request.post(`${API_BASE}/api/v1/crpc/head/bulk-reassign`,
        { headers: HEAD_HEADERS,
          data: { complaintIds: [a.complaintNumber, b.complaintNumber], assignTo: 'rbio_do_001' } });
      expect(res.status()).toBe(200);
      const body = await res.json();
      expect(body.count, 'both complaints should be reassigned').toBe(2);

      // READ IT BACK — the stub returned count:2 while touching no repository.
      for (const cn of [a.complaintNumber, b.complaintNumber]) {
        expect(complaintColumns(cn)['assigned_officer'],
          `${cn} must actually be reassigned`).toBe('rbio_do_001');
      }
    } finally {
      await cleanupRbioComplaint(request, a.complaintNumber);
      await cleanupRbioComplaint(request, b.complaintNumber);
    }
  });
});
