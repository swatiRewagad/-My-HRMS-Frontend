import { test, expect } from '../fixtures';
import { installCorsShim } from './browser-api';
import {
  createTestComplaint,
  advanceToStatus,
  performAction,
  loginCitizen,
  seedCitizenSession,
} from '../utils/test-data';
import { sql } from '../aa/aa-shared-fixtures';
import { mkdirSync, writeFileSync, rmSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

/**
 * Citizen COMPLAINT WITHDRAWAL — form defaults, supporting documents, file validation and status
 * eligibility. Manual QA cases 1-14.
 *
 * EXTENDS e2e/public/withdrawal.spec.ts, which already covers search→form, the missing-reason error,
 * the closed-complaint 400, the happy path status flip, and the "API error must not show success"
 * regression. Nothing from that file is repeated here.
 *
 * ── GROUND TRUTH established before these were written ──────────────────────────────────────────
 *
 * POST /api/v1/complaints/{n}/withdraw — ComplaintApiV1Controller.java:554-651. Body is
 * {reason, remarks} ONLY; the handler reads no document field and has no MultipartFile arm, so a
 * supporting document cannot reach the server through the withdrawal call at all.
 *
 * WITHDRAWABLE_STATUSES (ComplaintApiV1Controller.java:551-552) is
 * {pending,new,assigned,in_progress,under_review,escalated}. That is an ALLOW-list, so the server
 * also refuses reviewer_review / incharge_review / awaiting_closure / forwarded / info_requested,
 * every one of which is an ACTIVE status the QA cases require withdrawal to be permitted in.
 *
 * Attachment limits are CONFIGURATION, read from GET /api/v1/config/upload-limits at run time rather
 * than restated here: the ruling is that the figure is expected to move, and a spec that names one
 * fails on a legitimate retune (FileStorageConfig/application.yml hold only the no-row fallback).
 * Server allowed extensions: pdf,png,jpg,jpeg,doc,docx,xls,xlsx,txt,csv,zip.
 *
 * ── WHY EVERY FILE/STATUS CASE IS TESTED TWICE ─────────────────────────────────────────────────
 *
 * This repo has a documented pattern of UI-only enforcement (the 2000-char closure limit is a client
 * `maxlength`) and of uploads held in a client-side field and never sent anywhere (the RBIO signed
 * closure letter). Both hazards apply directly here, so each file-validation and status-eligibility
 * case asserts the UI behaviour AND the server's own answer to a direct call. Where they disagree,
 * the server's answer is the product's real behaviour.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** The withdrawal session must own the complaint, so every complaint here is filed from this number. */
const CITIZEN_PHONE = '9876543210';

/** A reason from the component's own radio list (withdraw-complaint.component.ts:35-42). */
const VALID_REASON = 'Issue resolved by the Regulated Entity';

/** The copy the manual cases require. Asserted verbatim — a wording drift is a reportable defect. */
const REQUIRED_REASON_MESSAGE = 'Reason for withdrawal is required.';
const REQUIRED_FILE_MESSAGE = 'Invalid file type or size, please upload a valid document.';

/** Seeded so FORWARD_TO_REGULATORY_BODY is reachable: every master row ships email_verified='N' and
 *  ForwardTargetService.java:245-250 answers 409 for an unverified body. Removed in afterAll. */
const TEST_BODY_CODE = 'WDATT';
const TEST_BODY_NAME = 'Withdrawal Attachment E2E Regulator';

let FIXTURE_DIR = '';

/** Real magic bytes, because FileUploadValidator sniffs the container (FileUploadValidator.java:32-53). */
const MAGIC: Record<string, Buffer> = {
  pdf: Buffer.from([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a]),
  doc: Buffer.from([0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1]),
  jpg: Buffer.from([0xff, 0xd8, 0xff, 0xe0]),
  png: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  xlsx: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
  exe: Buffer.from([0x4d, 0x5a]),
  mp3: Buffer.from([0x49, 0x44, 0x33, 0x03]),
};

function fixture(name: string, ext: string, padBytes = 1024): string {
  const path = join(FIXTURE_DIR, name);
  writeFileSync(path, Buffer.concat([MAGIC[ext], Buffer.alloc(padBytes, 0x20)]));
  return path;
}

/** Attachments actually persisted against a complaint id — the only proof a document was kept. */
function attachmentsFor(complaintId: number | string): Array<{ name: string; type: string }> {
  const out = sql(
    `SELECT original_name, COALESCE(document_type,'') FROM complaint_attachments ` +
    `WHERE complaint_id = ${Number(complaintId)} ORDER BY id ASC`
  );
  if (!out) return [];
  return out.split('\n').filter(l => l.trim()).map(line => {
    const [name, type] = line.split('\t');
    return { name, type };
  });
}

/** COMPLAINTS columns for one complaint number. Server-side truth, not the citizen projection. */
function complaintRow(complaintNumber: string, columns: string[]): Record<string, string> {
  const out = sql(
    `SELECT ${columns.map(c => `COALESCE(${c},'')`).join(', ')} FROM COMPLAINTS ` +
    `WHERE complaint_number = '${complaintNumber}'`
  );
  const values = out.split('\t');
  const row: Record<string, string> = {};
  columns.forEach((c, i) => { row[c] = values[i] ?? ''; });
  return row;
}

async function withdrawViaApi(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  token: string,
  body: Record<string, unknown>
) {
  return request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
    data: body,
    headers: { 'Content-Type': 'application/json', 'X-Citizen-Token': token },
  });
}

/** Opens the withdrawal form for a complaint and returns once the reason radios are on screen. */
async function openWithdrawalForm(
  page: import('@playwright/test').Page,
  complaintNumber: string,
  token: string
): Promise<boolean> {
  await installCorsShim(page);
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, CITIZEN_PHONE, token);
  await page.goto(`${APP_BASE}/public/withdraw`);
  await page.waitForLoadState('networkidle');

  const input = page.locator('input[name="complaintId"], input.form-input');
  await expect(input).toBeVisible({ timeout: 15000 });
  await input.fill(complaintNumber);
  await page.locator('button:has-text("Find"), button:has-text("Search"), button.btn-primary')
    .first().click();

  // waitFor, NOT isVisible: locator.isVisible() is an immediate check and its timeout option does
  // not make it poll, so it answers false while the search XHR is still in flight.
  return page.locator('.radio-group').first()
    .waitFor({ state: 'visible', timeout: 15000 })
    .then(() => true)
    .catch(() => false);
}

test.describe('Withdrawal — form, supporting documents and status eligibility', () => {

  test.beforeAll(() => {
    FIXTURE_DIR = join(tmpdir(), `cms-withdrawal-fixtures-${Date.now()}`);
    mkdirSync(FIXTURE_DIR, { recursive: true });
    sql(
      `INSERT INTO regulatory_body_master ` +
      `(BODY_CODE, BODY_NAME, CONTACT_EMAIL, email_verified, is_active, CREATED_BY, CREATED_AT) ` +
      `VALUES ('${TEST_BODY_CODE}', '${TEST_BODY_NAME}', 'wdatt@example.com', 'Y', 'Y', 'e2e', NOW())`
    );
  });

  test.afterAll(() => {
    sql(`DELETE FROM regulatory_body_master WHERE BODY_CODE = '${TEST_BODY_CODE}'`);
    if (FIXTURE_DIR) rmSync(FIXTURE_DIR, { recursive: true, force: true });
  });

  // ══ Case 1 ══════════════════════════════════════════════════════════════════════════════════
  test('case 1: the reason field is displayed and empty by default', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const { complaintNumber } = await createTestComplaint(request, {
      subject: 'E2E Withdrawal — default form state',
      complainantName: 'Default Form Citizen',
      complainantPhone: CITIZEN_PHONE,
    });

    const reached = await openWithdrawalForm(page, complaintNumber, token!);
    expect(reached, 'the withdrawal reason form must render for an eligible complaint').toBe(true);

    // Displayed.
    const radios = page.locator('.radio-group input[type="radio"]');
    await expect(radios.first()).toBeVisible();
    expect(await radios.count(), 'every configured withdrawal reason must be offered')
      .toBeGreaterThan(1);

    // EMPTY by default — nothing preselected, and no free-text carried over.
    expect(
      await page.locator('.radio-group input[type="radio"]:checked').count(),
      'no reason may be preselected: the citizen must choose one deliberately'
    ).toBe(0);

    const freeText = page.locator('textarea[name="additionalRemarks"]');
    if (await freeText.isVisible().catch(() => false)) {
      expect(await freeText.inputValue(), 'the reason text box must start empty').toBe('');
    }
  });

  // ══ Case 2 ══════════════════════════════════════════════════════════════════════════════════
  test('case 2: withdrawal with only the mandatory reason saves the reason and sets WITHDRAWN',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — reason only',
        complainantName: 'Reason Only Citizen',
        complainantPhone: CITIZEN_PHONE,
      });

      const res = await withdrawViaApi(request, complaintNumber, token!, {
        reason: VALID_REASON,
        remarks: '',
      });
      expect(res.status()).toBe(200);

      // Server-side truth, not the action's own response body.
      const row = complaintRow(complaintNumber, ['status', 'withdrawal_reason', 'withdrawal_date']);
      expect(row['status']).toBe('withdrawn');
      expect(row['withdrawal_reason'], 'the reason the citizen gave must be persisted verbatim')
        .toBe(VALID_REASON);
      expect(row['withdrawal_date'], 'the date of withdrawal must be recorded').not.toBe('');
    });

  // ══ Cases 3-6 ═══════════════════════════════════════════════════════════════════════════════
  // One test per format, because a format-specific rejection must be attributable to its format.
  for (const [label, ext] of [['PDF', 'pdf'], ['DOC', 'doc'], ['JPG', 'jpg'], ['PNG', 'png']] as const) {
    test(`cases 3-6: withdrawal with a reason and a valid ${label} attachment saves BOTH`,
      async ({ page, request }) => {
        const token = await loginCitizen(request, CITIZEN_PHONE);
        test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

        const created = await createTestComplaint(request, {
          subject: `E2E Withdrawal — ${label} attachment`,
          complainantName: `${label} Attachment Citizen`,
          complainantPhone: CITIZEN_PHONE,
        });
        const complaintNumber = created.complaintNumber;
        const complaintId = created.complaintId;

        const reached = await openWithdrawalForm(page, complaintNumber, token!);
        expect(reached, 'the withdrawal reason form must render for an eligible complaint').toBe(true);

        // Attach a valid, in-limit document through the form the product offers.
        const filePath = fixture(`withdrawal-support.${ext}`, ext);
        await page.locator('input#withdrawDocs').setInputFiles(filePath);

        // The chip proves the client accepted the file, so a later absence is a transport gap and
        // not a client-side rejection.
        await expect(
          page.locator('.file-chip'),
          `the form must accept a valid ${label} within the configured size limit`
        ).toBeVisible({ timeout: 10000 });

        await page.locator(`.radio-group label:has-text("${VALID_REASON}")`).first().click();
        await page.locator('button.btn-submit, button:has-text("Confirm"), button:has-text("Withdraw")')
          .first().click();

        await expect(page.locator('.success-card, .success-icon').first()).toBeVisible({ timeout: 20000 });

        // BOTH must be in the record. The reason is checked first so a failure on the document is
        // unambiguously about the document.
        const row = complaintRow(complaintNumber, ['status', 'withdrawal_reason']);
        expect(row['status']).toBe('withdrawn');
        expect(row['withdrawal_reason']).toContain(VALID_REASON);

        const attachments = attachmentsFor(complaintId);
        expect(
          attachments.map(a => a.name),
          `the ${label} the citizen attached to the withdrawal must be persisted against the ` +
          'complaint — a document accepted by the form and kept only in browser memory is lost'
        ).toContain(`withdrawal-support.${ext}`);
      });
  }

  // ══ Case 7 ══════════════════════════════════════════════════════════════════════════════════
  test('case 7: the audit trail records the withdrawal action, the reason and the document link',
    async ({ page, request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const created = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — audit trail',
        complainantName: 'Audited Withdrawal Citizen',
        complainantPhone: CITIZEN_PHONE,
      });
      const complaintNumber = created.complaintNumber;
      const complaintId = created.complaintId;

      const reached = await openWithdrawalForm(page, complaintNumber, token!);
      expect(reached).toBe(true);

      const filePath = fixture('audit-evidence.pdf', 'pdf');
      await page.locator('input#withdrawDocs').setInputFiles(filePath);
      await expect(page.locator('.file-chip')).toBeVisible({ timeout: 10000 });
      await page.locator(`.radio-group label:has-text("${VALID_REASON}")`).first().click();
      await page.locator('button.btn-submit, button:has-text("Confirm"), button:has-text("Withdraw")')
        .first().click();
      await expect(page.locator('.success-card, .success-icon').first()).toBeVisible({ timeout: 20000 });

      // The audit trail a caseworker reads. /history is the canonical, immutable read path
      // (ComplaintCorrespondenceController.java:152-164).
      const history = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}/history`, {
        headers: { 'X-User-Id': 'cepc_do1', 'X-User-Name': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
      });
      expect(history.status()).toBe(200);
      const entries = (await history.json()).data as Array<Record<string, string>>;

      const withdrawal = entries.find(e => (e['action'] || '').toLowerCase().includes('withdraw'));
      expect(withdrawal, 'the audit trail must show the withdrawal action explicitly').toBeTruthy();
      expect(
        `${withdrawal!['remarks'] || ''}`,
        'the audit entry must carry the reason the citizen gave'
      ).toContain(VALID_REASON);

      // The document must be reachable FROM the withdrawal record, not merely exist somewhere.
      const attachments = attachmentsFor(complaintId);
      expect(
        attachments.map(a => a.name),
        'the audit trail must link to the document submitted with the withdrawal'
      ).toContain('audit-evidence.pdf');
    });

  // ══ Case 8 ══════════════════════════════════════════════════════════════════════════════════
  // Every ACTIVE status except the three excluded ones must permit withdrawal. Each status needs
  // its own complaint, and each is asserted against the server rather than the UI.
  const activeStatuses: Array<{ label: string; reach: (r: any, n: string) => Promise<void> }> = [
    { label: 'assigned (freshly created)', reach: async () => { /* already assigned */ } },
    {
      label: 'in_progress',
      reach: (r, n) => advanceToStatus(r, n, 'in_progress'),
    },
    {
      label: 'info_requested',
      reach: async (r, n) => {
        await performAction(r, n, 'ACCEPT', 'cepc_do_001');
        await performAction(r, n, 'REQUEST_INFO', 'cepc_do_001');
      },
    },
    {
      label: 'forwarded (comments sought from the RE)',
      reach: async (r, n) => {
        await performAction(r, n, 'ACCEPT', 'cepc_do_001');
        await performAction(r, n, 'FORWARD_DEPT', 'cepc_do_001');
      },
    },
    {
      label: 'escalated',
      reach: (r, n) => performAction(r, n, 'ESCALATE', 'cepc_admin_001'),
    },
    {
      label: 'reviewer_review',
      reach: (r, n) => advanceToStatus(r, n, 'reviewer_review'),
    },
    {
      label: 'incharge_review',
      reach: (r, n) => advanceToStatus(r, n, 'incharge_review'),
    },
    {
      label: 'awaiting_closure',
      reach: (r, n) => advanceToStatus(r, n, 'awaiting_closure'),
    },
  ];

  for (const { label, reach } of activeStatuses) {
    test(`case 8: withdrawal is allowed while the complaint is active — ${label}`,
      async ({ request }) => {
        const token = await loginCitizen(request, CITIZEN_PHONE);
        test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

        const { complaintNumber } = await createTestComplaint(request, {
          subject: `E2E Withdrawal — eligible status ${label}`,
          complainantName: 'Active Status Citizen',
          complainantPhone: CITIZEN_PHONE,
        });
        await reach(request, complaintNumber);

        const before = complaintRow(complaintNumber, ['status'])['status'];
        const res = await withdrawViaApi(request, complaintNumber, token!, {
          reason: VALID_REASON,
          remarks: '',
        });

        expect(
          res.status(),
          `${label} is an active status and is not one of the three the scheme excludes ` +
          `(Complaint Closed / Sent to other Department / Sent to other Regulatory Bodies), so a ` +
          `withdrawal must be accepted. Server status was '${before}'. Body: ${await res.text()}`
        ).toBe(200);
        expect(complaintRow(complaintNumber, ['status'])['status']).toBe('withdrawn');
      });
  }

  // ══ Cases 9-11 ══════════════════════════════════════════════════════════════════════════════
  // The server must REFUSE, and the UI must not offer the option. Both are asserted, server first,
  // so a failure is attributable.
  const ineligible: Array<{ caseNo: number; label: string; reach: (r: any, n: string) => Promise<void> }> = [
    {
      caseNo: 9,
      label: 'Sent to other Department',
      reach: async (r, n) => {
        await advanceToStatus(r, n, 'awaiting_closure');
        await performAction(r, n, 'FORWARD_TO_OTHER_RBI_DEPT', 'cepc_closing_001',
          'E2E forward to another RBI department', { targetDepartment: 'DPSS' });
      },
    },
    {
      caseNo: 10,
      label: 'Complaint Closed',
      reach: (r, n) => advanceToStatus(r, n, 'closed'),
    },
    {
      caseNo: 11,
      label: 'Sent to other Regulatory Bodies',
      reach: async (r, n) => {
        await advanceToStatus(r, n, 'awaiting_closure');
        await performAction(r, n, 'FORWARD_TO_REGULATORY_BODY', 'cepc_closing_001',
          'E2E referral to an outside regulator', { targetBody: TEST_BODY_CODE });
      },
    },
  ];

  for (const { caseNo, label, reach } of ineligible) {
    test(`case ${caseNo}: withdrawal is unavailable for a complaint in "${label}"`,
      async ({ page, request }) => {
        const token = await loginCitizen(request, CITIZEN_PHONE);
        test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

        const { complaintNumber } = await createTestComplaint(request, {
          subject: `E2E Withdrawal — ineligible ${label}`,
          complainantName: 'Ineligible Status Citizen',
          complainantPhone: CITIZEN_PHONE,
        });
        await reach(request, complaintNumber);

        // 1. THE SERVER must block it. A UI that merely hides the button is bypassed by this call.
        const res = await withdrawViaApi(request, complaintNumber, token!, {
          reason: VALID_REASON,
          remarks: '',
        });
        expect(
          res.status(),
          `the server must refuse a withdrawal for "${label}" — hiding the option in the UI is not ` +
          'enforcement'
        ).toBe(400);
        expect((await res.json()).success).toBe(false);
        expect(
          complaintRow(complaintNumber, ['status'])['status'],
          'a refused withdrawal must not change the status'
        ).not.toBe('withdrawn');

        // 2. THE UI must not offer the form either, or the citizen is walked into a dead end.
        const formShown = await openWithdrawalForm(page, complaintNumber, token!);
        expect(
          formShown,
          `the withdrawal option must be UNAVAILABLE for "${label}": the portal must not present a ` +
          'reason form for a complaint the server will refuse to withdraw'
        ).toBe(false);
      });
  }

  // ══ Case 12 ═════════════════════════════════════════════════════════════════════════════════
  test('case 12: an empty reason is blocked with "Reason for withdrawal is required."',
    async ({ page, request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — empty reason',
        complainantName: 'Empty Reason Citizen',
        complainantPhone: CITIZEN_PHONE,
      });

      // (a) THE SERVER. A client-side-only mandatory check is bypassed by this call.
      const res = await withdrawViaApi(request, complaintNumber, token!, { reason: '', remarks: '' });
      expect(res.status(), 'the server must reject a blank reason').toBe(400);
      expect(
        complaintRow(complaintNumber, ['status'])['status'],
        'a blank-reason withdrawal must not be applied'
      ).not.toBe('withdrawn');
      expect(
        (await res.json()).message,
        'the mandatory-reason refusal must use the specified wording'
      ).toContain(REQUIRED_REASON_MESSAGE);

      // (b) THE UI.
      const reached = await openWithdrawalForm(page, complaintNumber, token!);
      expect(reached).toBe(true);
      await page.locator('button.btn-submit, button:has-text("Confirm"), button:has-text("Withdraw")')
        .first().click();

      const error = page.locator('.error-msg');
      await expect(error.first()).toBeVisible({ timeout: 10000 });
      await expect(error.first(), 'the form must state the specified mandatory-reason message')
        .toContainText(REQUIRED_REASON_MESSAGE);
    });

  // ══ Case 13 ═════════════════════════════════════════════════════════════════════════════════
  for (const ext of ['exe', 'mp3', 'xlsx'] as const) {
    test(`case 13: an unsupported .${ext} attachment is rejected with the specified message`,
      async ({ page, request }) => {
        const token = await loginCitizen(request, CITIZEN_PHONE);
        test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

        const created = await createTestComplaint(request, {
          subject: `E2E Withdrawal — unsupported .${ext}`,
          complainantName: 'Bad Format Citizen',
          complainantPhone: CITIZEN_PHONE,
        });
        const complaintNumber = created.complaintNumber;
        const complaintId = created.complaintId;
        const filePath = fixture(`unsupported-evidence.${ext}`, ext);

        // (a) THE SERVER. The withdrawal call itself carries no document, so the only server-side
        // gate a citizen document could pass through is the attachment endpoint. It must refuse the
        // format regardless of what the UI allows.
        const upload = await request.post(
          `${API_BASE}/api/files/upload?complaintNumber=${complaintNumber}` +
          `&complaintId=${complaintId}&documentType=WITHDRAWAL_SUPPORT`,
          {
            multipart: { file: { name: `unsupported-evidence.${ext}`, mimeType: 'application/octet-stream', buffer: readFileSync(filePath) } },
            headers: { 'X-User-Id': 'cepc_do1', 'X-User-Name': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
          }
        );
        expect(
          upload.status(),
          `the server must refuse a .${ext} upload — client-side format filtering is bypassed by a ` +
          `direct call. Body: ${await upload.text()}`
        ).toBe(400);
        expect(
          attachmentsFor(complaintId).map(a => a.name),
          `a rejected .${ext} must not be persisted`
        ).not.toContain(`unsupported-evidence.${ext}`);

        // (b) THE UI.
        const reached = await openWithdrawalForm(page, complaintNumber, token!);
        expect(reached).toBe(true);
        await page.locator('input#withdrawDocs').setInputFiles(filePath);

        const error = page.locator('.error-msg');
        await expect(error.first(), `the form must reject a .${ext}`).toBeVisible({ timeout: 10000 });
        await expect(error.first(), 'the rejection must use the specified message')
          .toContainText(REQUIRED_FILE_MESSAGE);
        expect(
          await page.locator('.file-chip').count(),
          `a rejected .${ext} must not appear in the attached-file list`
        ).toBe(0);
      });
  }

  // ══ Case 14 ═════════════════════════════════════════════════════════════════════════════════
  test('case 14: a valid-format PDF over the maximum size is blocked with the same message',
    async ({ page, request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const created = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — oversize PDF',
        complainantName: 'Oversize File Citizen',
        complainantPhone: CITIZEN_PHONE,
      });
      const complaintNumber = created.complaintNumber;
      const complaintId = created.complaintId;

      // The limit is whatever the server publishes, so the fixture cannot drift from the product
      // rule the way any hardcoded figure would.
      const limits = await request.get(`${API_BASE}/api/v1/config/upload-limits`);
      expect(limits.status()).toBe(200);
      const maxBytes = (await limits.json()).maxFileSizeBytes as number;
      expect(maxBytes, 'the server must publish a per-file limit').toBeGreaterThan(0);

      const oversize = fixture('oversize-evidence.pdf', 'pdf', maxBytes + 1024);

      // (a) THE SERVER.
      const upload = await request.post(
        `${API_BASE}/api/files/upload?complaintNumber=${complaintNumber}` +
        `&complaintId=${complaintId}&documentType=WITHDRAWAL_SUPPORT`,
        {
          multipart: { file: { name: 'oversize-evidence.pdf', mimeType: 'application/pdf', buffer: readFileSync(oversize) } },
          headers: { 'X-User-Id': 'cepc_do1', 'X-User-Name': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' },
        }
      );
      expect(
        upload.status(),
        `the server must refuse a file above its own published limit of ${maxBytes} bytes. ` +
        `Body: ${await upload.text()}`
      ).toBe(400);
      expect(attachmentsFor(complaintId).map(a => a.name)).not.toContain('oversize-evidence.pdf');

      // (b) THE UI.
      const reached = await openWithdrawalForm(page, complaintNumber, token!);
      expect(reached).toBe(true);
      await page.locator('input#withdrawDocs').setInputFiles(oversize);

      const error = page.locator('.error-msg');
      await expect(error.first(), 'the form must reject an oversize file').toBeVisible({ timeout: 20000 });
      await expect(
        error.first(),
        'an oversize file must be refused with the SAME message as an invalid format'
      ).toContainText(REQUIRED_FILE_MESSAGE);
      expect(
        await page.locator('.file-chip').count(),
        'an oversize file must not appear in the attached-file list'
      ).toBe(0);
    });
});
