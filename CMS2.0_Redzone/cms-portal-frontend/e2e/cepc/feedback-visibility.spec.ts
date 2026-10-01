import { test, expect } from '../fixtures';
import {
  advanceToStatus,
  createTestComplaint,
  fileComplaintForOffice,
  identityHeadersFor,
  rbioLadderAction,
  rbioLadderHeaders,
} from '../utils/test-data';
import { execFileSync } from 'node:child_process';

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * UST110 / FR-G-039 — Submit Feedback: visibility to RBI staff.
 *
 * ── THE SHARED ROOT CAUSE BEHIND MOST OF THIS FILE ──────────────────────────────────────────────
 *
 * FeedbackController exposes three READ endpoints and NONE of them takes any notion of who is asking:
 *
 *   FeedbackController.java:46-47   GET /{complaintNumber}      (String complaintNumber)
 *   FeedbackController.java:60-61   GET /office/{officeCode}    (String officeCode)
 *   FeedbackController.java:71-72   GET /all                    ()
 *
 * There is no @RequiresAuthority, no @PreAuthorize, no role argument and no office-scope comparison
 * anywhere in the class. So the office a caller belongs to is not an input to the decision, which
 * means "visible to my office" and "restricted from another office" cannot be distinguished at all:
 * any caller who knows an office code reads that office's feedback, and /all is world-readable.
 *
 * Cases 12, 13 and 14 are therefore expected to PASS while asserting nothing about authorization —
 * they only establish that the data IS reachable. Case 15 is the one that fails, and 12-15 share ONE
 * defect, not four.
 *
 * ── A SECOND, INDEPENDENT DEFECT: officeCode is a DEPARTMENT, not an office ─────────────────────
 *
 * FeedbackService.java:95-97 derives the stored officeCode as complaint.getDepartment(), falling back
 * to getEntityCode(). department holds 'CEPC' / 'RBIO' / 'CRPC' — the whole module — while the actual
 * office lives in COMPLAINTS.rbio_office_code ('004' Bhopal, '013' Mumbai-I; OFFICE_CODE_MASTER).
 * Verified in cms_db: every one of the 65 COMPLAINT_FEEDBACK rows carries office_code 'CEPC' or
 * 'RBIO'. GET /office/004 returns [] even for a Bhopal complaint, and GET /office/RBIO returns EVERY
 * RBIO office's feedback nationwide. So even if a scope check were added, there is nothing per-office
 * to scope it ON. Asserted directly below.
 */

/** Bhopal (004) owns Madhya Pradesh; Mumbai-I (013) owns Mumbai — OMBUDSMAN_OFFICE_MASTER. */
const BHOPAL = { code: '004', state: 'Madhya Pradesh', district: 'Bhopal' };
const MUMBAI = { code: '013', state: 'Maharashtra', district: 'Mumbai' };

/** The message UST110 scenario 4 requires when a foreign office attempts a read. */
const FOREIGN_OFFICE_MESSAGE = 'Feedback not available for this office.';

interface Seeded {
  complaintNumber: string;
  officeCode: string;
  department: string;
  submitted: Record<string, unknown>;
}

function mysql(sql: string): string {
  return execFileSync(
    process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  ).trim();
}

/** Reads the office columns straight from the row, because the API response exposes neither. */
function readOfficeColumns(complaintNumber: string): { department: string; officeCode: string } {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing to use an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  const out = mysql(
    `SELECT COALESCE(department,''), COALESCE(rbio_office_code,'') FROM COMPLAINTS ` +
      `WHERE complaint_number = '${complaintNumber}';`
  );
  const [department, officeCode] = out.split('\t');
  return { department: department || '', officeCode: officeCode || '' };
}

/**
 * Files a complaint into a NAMED territorial office, drives it to a terminal status, and submits
 * feedback for it.
 *
 * The public filing endpoint is the only one that resolves an office from (state, district) — the
 * CEPC/RBIO create-complaint endpoints take neither, so a complaint seeded through those belongs to
 * no office and could not exercise office scoping at all.
 */
async function seedFeedbackForOffice(
  request: any,
  office: typeof BHOPAL,
  overrides: Record<string, unknown> = {}
): Promise<Seeded> {
  const filed = await fileComplaintForOffice(request, {
    state: office.state,
    district: office.district,
  });
  expect(
    filed.status,
    `seeding a ${office.district} complaint failed: ${filed.status} ${filed.message}`
  ).toBe(201);

  const columns = readOfficeColumns(filed.complaintNumber);
  expect(
    columns.officeCode,
    `the complaint must be routed to ${office.district} (${office.code}) or the office-scope ` +
      'assertions below would be meaningless'
  ).toBe(office.code);

  // Drive to a terminal status. Public filings land in whichever department routing chose, so the
  // matching ladder is used; feedback is refused outright for a non-terminal complaint
  // (FeedbackService.java:47-51).
  if (columns.department === 'RBIO') {
    const accepted = await rbioLadderAction(request, filed.complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
    expect(accepted.success, `ACCEPT failed: ${accepted.status} ${accepted.message}`).toBe(true);
    const resolved = await rbioLadderAction(
      request, filed.complaintNumber, 'RESOLVE', 'RBIO_DEALING_OFFICIAL', { remarks: 'E2E UST110 seed' }
    );
    expect(resolved.success, `RESOLVE failed: ${resolved.status} ${resolved.message}`).toBe(true);
  } else {
    await advanceToStatus(request, filed.complaintNumber, 'closed');
  }

  const submitted: Record<string, unknown> = {
    complaintNumber: filed.complaintNumber,
    overallRating: 5,
    easeOfFiling: 4,
    timelinessRating: 3,
    communicationRating: 2,
    satisfactionRating: 5,
    grievanceRedressTime: 4,
    sourceOfInformation: 'Print Media',
    cmsPortalAwareness: 'Somewhat Aware',
    feedbackText: `Handled by ${office.district}. The officer explained the outcome clearly.`,
    suggestions: 'Send a closure SMS as well as the email.',
    complainantPhone: '9876543210',
    ...overrides,
  };

  const created = await request.post(`${API_BASE}/api/v1/feedback`, {
    data: submitted,
    headers: { 'Content-Type': 'application/json' },
  });
  expect(
    created.status(),
    `seeding feedback failed: ${created.status()} ${await created.text()}`
  ).toBe(201);
  const stored = (await created.json()).data;

  return {
    complaintNumber: filed.complaintNumber,
    officeCode: office.code,
    department: columns.department,
    submitted: { ...submitted, storedOfficeCode: stored.officeCode },
  };
}

/** GET a feedback endpoint AS a named staff identity, returning status alongside the body. */
async function readAs(
  request: any,
  path: string,
  headers: Record<string, string>
): Promise<{ status: number; message: string; data: any }> {
  const response = await request.get(`${API_BASE}/api/v1/feedback${path}`, { headers });
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }
  return { status: response.status(), message: json.message || '', data: json.data ?? null };
}

/** Staff identities. Offices are stated explicitly, since no derivation helper carries an office. */
const BHOPAL_OMBUDSMAN = {
  ...rbioLadderHeaders('RBIO_OMBUDSMAN', 'rbio_ombudsman_001'),
  'X-Office-Code': BHOPAL.code,
  'X-User-Office': BHOPAL.code,
};
const MUMBAI_OMBUDSMAN = {
  ...rbioLadderHeaders('RBIO_OMBUDSMAN', 'rbio.supervisor.mum'),
  'X-Office-Code': MUMBAI.code,
  'X-User-Office': MUMBAI.code,
};
const CEPC_BHOPAL = {
  ...identityHeadersFor('cepc_incharge1', 'CEPC'),
  'X-Office-Code': BHOPAL.code,
  'X-User-Office': BHOPAL.code,
};
const CEPD_ADMIN = {
  'X-User-Id': 'cepd_admin_001',
  'X-User-Name': 'cepd_admin_001',
  'X-User-Roles': 'CEPD_ADMIN',
};

test.describe('Feedback visibility to staff (UST110 / FR-G-039)', () => {

  let bhopal: Seeded;
  let mumbai: Seeded;

  test.beforeAll(async ({ request }) => {
    bhopal = await seedFeedbackForOffice(request, BHOPAL);
    mumbai = await seedFeedbackForOffice(request, MUMBAI);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 12 — an RBI Ombudsman (Bhopal) can view feedback for their OWN office's complaints
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('the Bhopal Ombudsman can view feedback for a complaint their own office processed', async ({ request }) => {
    const single = await readAs(request, `/${bhopal.complaintNumber}`, BHOPAL_OMBUDSMAN);
    expect(single.status, `the owning office must be able to read the record: ${single.message}`).toBe(200);
    expect(single.data.complaintNumber).toBe(bhopal.complaintNumber);

    // ...and through the office listing, which is the screen an Ombudsman would actually use.
    const list = await readAs(request, `/office/${BHOPAL.code}`, BHOPAL_OMBUDSMAN);
    expect(list.status).toBe(200);
    const numbers = (list.data as any[]).map((row) => row.complaintNumber);
    expect(
      numbers,
      `GET /office/${BHOPAL.code} must list the Bhopal complaint. It does not, because ` +
        'FeedbackService.java:95-97 stores the DEPARTMENT ("RBIO"/"CEPC") in office_code instead of ' +
        `the office code from COMPLAINTS.rbio_office_code ("${BHOPAL.code}")`
    ).toContain(bhopal.complaintNumber);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 13 — a CEPC office (Bhopal) user can view feedback for their specific office
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('a CEPC Bhopal user can view feedback for complaints their specific office processed', async ({ request }) => {
    const single = await readAs(request, `/${bhopal.complaintNumber}`, CEPC_BHOPAL);
    expect(single.status, `a CEPC Bhopal user must be able to read the record: ${single.message}`).toBe(200);
    expect(single.data.complaintNumber).toBe(bhopal.complaintNumber);

    const list = await readAs(request, `/office/${BHOPAL.code}`, CEPC_BHOPAL);
    expect(list.status).toBe(200);
    expect(
      (list.data as any[]).map((row) => row.complaintNumber),
      `the office listing for ${BHOPAL.code} must contain the office's own feedback`
    ).toContain(bhopal.complaintNumber);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 14 — a CEPD Admin can view feedback for ANY complaint across ALL offices
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('a CEPD Admin can view feedback from every office', async ({ request }) => {
    const all = await readAs(request, '/all', CEPD_ADMIN);
    expect(all.status, `CEPD Admin must be able to read all feedback: ${all.message}`).toBe(200);

    const numbers = (all.data as any[]).map((row) => row.complaintNumber);
    expect(numbers, 'the Bhopal record must be in the CEPD-wide view').toContain(bhopal.complaintNumber);
    expect(numbers, 'the Mumbai record must be in the CEPD-wide view').toContain(mumbai.complaintNumber);

    // Per-complaint reads across offices too.
    for (const seeded of [bhopal, mumbai]) {
      const single = await readAs(request, `/${seeded.complaintNumber}`, CEPD_ADMIN);
      expect(single.status).toBe(200);
      expect(single.data.complaintNumber).toBe(seeded.complaintNumber);
    }
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 15 — an unrelated RBIO-Mumbai user is RESTRICTED from Bhopal's feedback
  //
  // This is the assertion the shared defect breaks. Nothing in FeedbackController compares the
  // caller's office to the record's, so Mumbai reads Bhopal's feedback with a plain 200.
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('an RBIO-Mumbai user is refused Bhopal feedback with "Feedback not available for this office."', async ({ request }) => {
    // Precondition: the two records really do belong to different offices.
    expect(bhopal.officeCode).not.toBe(mumbai.officeCode);

    const direct = await readAs(request, `/${bhopal.complaintNumber}`, MUMBAI_OMBUDSMAN);
    expect(
      direct.status,
      'a foreign office must be refused (403), not served Bhopal\'s feedback with 200. ' +
        'FeedbackController.java:46-53 takes no caller identity and performs no office check'
    ).toBe(403);
    expect(direct.message).toBe(FOREIGN_OFFICE_MESSAGE);
    expect(direct.data, 'a refused read must return no feedback data').toBeNull();

    // The same must hold for the office listing: naming another office's code must not disclose it.
    const list = await readAs(request, `/office/${BHOPAL.code}`, MUMBAI_OMBUDSMAN);
    expect(
      list.status,
      `a Mumbai user requesting office ${BHOPAL.code} must be refused, not served`
    ).toBe(403);
    expect(list.message).toBe(FOREIGN_OFFICE_MESSAGE);

    // And /all must not be a way around the scope for a non-CEPD office user.
    const all = await readAs(request, '/all', MUMBAI_OMBUDSMAN);
    expect(
      all.status,
      'GET /all is reserved for CEPD admin (FeedbackController.java:69-71 says so in a comment only) ' +
        'and must refuse an office-scoped user'
    ).toBe(403);
  });

  /**
   * The mechanism behind case 15, asserted separately so the report can distinguish "there is no
   * scope CHECK" from "there is nothing to scope BY". Both are true and they need different fixes.
   */
  test('a feedback record records the processing OFFICE, not merely the department', async ({ request }) => {
    const single = await readAs(request, `/${bhopal.complaintNumber}`, CEPD_ADMIN);
    expect(single.status).toBe(200);

    expect(
      single.data.officeCode,
      `feedback for a Bhopal complaint must carry office ${BHOPAL.code}. It carries ` +
        `"${single.data.officeCode}" because FeedbackService.java:95-97 uses complaint.getDepartment() ` +
        'instead of complaint.getRbioOfficeCode(), so all offices in a module collapse into one bucket'
    ).toBe(BHOPAL.code);

    // Consequence: the department bucket leaks every office to every office.
    const bucket = await readAs(request, `/office/${bhopal.department}`, MUMBAI_OMBUDSMAN);
    if (bucket.status === 200 && Array.isArray(bucket.data)) {
      const numbers = bucket.data.map((row: any) => row.complaintNumber);
      const leaks = numbers.includes(bhopal.complaintNumber) && numbers.includes(mumbai.complaintNumber);
      expect(
        leaks,
        `GET /office/${bhopal.department} returned both the Bhopal and the Mumbai record to a single ` +
          'caller, so the department bucket is a cross-office disclosure'
      ).toBe(false);
    }
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 16 — the record displays ALL responses EXACTLY as submitted: no missing fields, no
  //           truncation
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('every submitted answer is returned exactly as submitted, with nothing missing or truncated', async ({ request }) => {
    const read = await readAs(request, `/${bhopal.complaintNumber}`, BHOPAL_OMBUDSMAN);
    expect(read.status).toBe(200);
    const record = read.data;

    // Field-by-field, against what the citizen actually sent.
    const submitted = bhopal.submitted;
    for (const field of [
      'overallRating', 'easeOfFiling', 'timelinessRating', 'communicationRating',
      'satisfactionRating', 'grievanceRedressTime', 'sourceOfInformation',
      'cmsPortalAwareness', 'feedbackText', 'suggestions',
    ]) {
      expect(
        record[field],
        `${field} must be returned exactly as submitted`
      ).toEqual(submitted[field]);
    }
    expect(record.complaintNumber).toBe(bhopal.complaintNumber);
    expect(record.submittedAt, 'the submission timestamp must be present').toBeTruthy();

    // No missing fields: every field of the questionnaire is present as a key, so an answer a citizen
    // gave can never be silently dropped from the staff view.
    for (const field of [
      'id', 'complaintNumber', 'overallRating', 'easeOfFiling', 'timelinessRating',
      'communicationRating', 'satisfactionRating', 'grievanceRedressTime', 'sourceOfInformation',
      'sourceOtherText', 'cmsPortalAwareness', 'feedbackText', 'suggestions', 'officeCode',
      'submittedAt',
    ]) {
      expect(Object.keys(record), `${field} is missing from the staff view`).toContain(field);
    }

    // No truncation at the boundary. A separate 500-character record, because a short value cannot
    // detect a store that silently clips at, say, 255.
    const longText = 'B7c'.repeat(166) + 'Zy';  // exactly 500 characters, alphanumeric
    expect(longText.length).toBe(500);
    const boundary = await createTestComplaint(request, { subject: 'UST110-16 no truncation' });
    await advanceToStatus(request, boundary.complaintNumber, 'closed');
    const created = await request.post(`${API_BASE}/api/v1/feedback`, {
      data: {
        complaintNumber: boundary.complaintNumber,
        overallRating: 3, easeOfFiling: 3, grievanceRedressTime: 3,
        sourceOfInformation: 'Others', sourceOtherText: longText,
        cmsPortalAwareness: 'Very Aware',
        feedbackText: longText, suggestions: longText,
      },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(created.status(), await created.text()).toBe(201);

    const staffView = await readAs(request, `/${boundary.complaintNumber}`, CEPD_ADMIN);
    expect(staffView.status).toBe(200);
    expect(staffView.data.feedbackText, 'feedbackText was truncated').toBe(longText);
    expect(staffView.data.suggestions, 'suggestions were truncated').toBe(longText);
    expect(staffView.data.sourceOtherText, 'sourceOtherText was truncated').toBe(longText);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 17 — accessing a feedback record generates an audit log entry with user, timestamp and
  //           complaint ID
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('an office or CEPD read of a feedback record is audited with user, timestamp and complaint id', async ({ request }) => {
    // A record of its own, so the audit assertion cannot be satisfied by a row some other test wrote.
    const complaint = await createTestComplaint(request, { subject: 'UST110-17 audit' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    const created = await request.post(`${API_BASE}/api/v1/feedback`, {
      data: {
        complaintNumber: complaint.complaintNumber,
        overallRating: 4, easeOfFiling: 4, grievanceRedressTime: 4,
        sourceOfInformation: 'RBI Website', cmsPortalAwareness: 'Very Aware',
      },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(created.status()).toBe(201);

    const before = mysql(
      `SELECT COUNT(*) FROM AUDIT_LOG WHERE complaint_number = '${complaint.complaintNumber}';`
    );
    const readAt = new Date();

    const read = await readAs(request, `/${complaint.complaintNumber}`, CEPD_ADMIN);
    expect(read.status).toBe(200);

    const rows = mysql(
      `SELECT action, actor, timestamp FROM AUDIT_LOG ` +
        `WHERE complaint_number = '${complaint.complaintNumber}' ORDER BY id DESC;`
    )
      .split('\n')
      .filter((line) => line.trim().length > 0)
      .map((line) => {
        const [action, actor, timestamp] = line.split('\t');
        return { action, actor, timestamp };
      });

    expect(
      rows.length,
      `reading feedback for ${complaint.complaintNumber} as cepd_admin_001 wrote no AUDIT_LOG row ` +
        `(count was ${before} before and ${rows.length} after). FeedbackController has no audit call ` +
        'on any read path — FeedbackController.java:46-76'
    ).toBeGreaterThan(Number(before));

    const entry = rows[0];
    // The correct user id...
    expect(entry.actor, 'the audit entry must name the user who read the record').toBe('cepd_admin_001');
    // ...an action that identifies this as a feedback access, not some unrelated workflow step...
    expect(entry.action, 'the audit action must identify a feedback access').toMatch(/FEEDBACK/i);
    // ...and a timestamp at the moment of the read. Within five minutes, so clock skew between the
    // test host and the DB cannot fail it, but a stale row from an earlier action still would.
    const logged = new Date(entry.timestamp.replace(' ', 'T'));
    expect(
      Math.abs(logged.getTime() - readAt.getTime()),
      `the audit timestamp (${entry.timestamp}) must be the moment of the read`
    ).toBeLessThan(5 * 60 * 1000);
    // The associated complaint id is the row's own key, already matched by the WHERE clause above.
  });

  /**
   * Case 17, staff-UI half. There is no staff screen that reads feedback at all — no component calls
   * FeedbackService.getFeedbackByOffice / getAllFeedback / getFeedbackForComplaint
   * (src/app/services/feedback.service.ts:44-62 are dead code). So an Ombudsman cannot "open the
   * record" through the product even though the API would serve it.
   */
  test('a staff screen exists from which feedback can be opened', async () => {
    // Asserted against the SOURCE rather than by driving a staff route. Every staff route sits behind
    // staffAuthGuard, so an unauthenticated visit redirects to the login page and issues no feedback
    // request — which would make this test "fail" identically whether the screen exists or not. The
    // source answers the question unambiguously: does anything at all call the read methods?
    const callers = execFileSync(
      'node',
      [
        '-e',
        `const fs=require('fs'),p=require('path');
         const hits=[];
         (function walk(d){for(const e of fs.readdirSync(d,{withFileTypes:true})){
           const f=p.join(d,e.name);
           if(e.isDirectory()){walk(f);continue;}
           if(!/\\.(ts|html)$/.test(e.name))continue;
           if(f.endsWith('services'+p.sep+'feedback.service.ts'))continue;
           const s=fs.readFileSync(f,'utf8');
           if(/getFeedbackByOffice|getAllFeedback|getFeedbackForComplaint/.test(s))hits.push(f);
         }})('src/app');
         process.stdout.write(hits.join('\\n'));`,
      ],
      { encoding: 'utf8', cwd: process.cwd() }
    ).trim();

    expect(
      callers,
      'nothing in the application calls FeedbackService.getFeedbackByOffice / getAllFeedback / ' +
        'getFeedbackForComplaint (src/app/services/feedback.service.ts:44-62), so there is no staff ' +
        'screen from which an Ombudsman or CEPD admin can open a feedback record — the submitted ' +
        'feedback is reachable only by calling the API by hand'
    ).not.toBe('');
  });
});
