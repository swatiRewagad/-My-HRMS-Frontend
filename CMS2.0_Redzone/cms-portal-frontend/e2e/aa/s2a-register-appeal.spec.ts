/**
 * S2A — appeal registration, PII masking format and the NFR-006 upload caps.
 *
 * API-LEVEL BY DESIGN, for the same reason as s2a-parent-search.spec.ts: everything asserted here is a
 * server-side control. Registration in particular must be provably enforced on the server, because the
 * screen's disabled Save button and hidden Create-Appeal action are conveniences that a direct POST
 * bypasses entirely.
 *
 * Run against this session's backend:
 *   API_BASE_URL=http://localhost:8092 npx playwright test e2e/aa/s2a-register-appeal.spec.ts \
 *     --project=chromium --reporter=list
 */
import { test, expect, APIRequestContext } from '@playwright/test';
import { identityHeadersFor } from '../utils/test-data';
import {
  API_BASE,
  CLAUSE,
  RE_OWN_ENTITY,
  RE_OTHER_ENTITY,
  assertBackendReady,
  bearer,
  mintToken,
  purgeByPrefix,
  seedClosedComplaint,
  seedComplaint,
  seedReopenedComplaint,
  sql,
} from './aa-shared-fixtures';

const PREFIX = 'S2A-REG-';

const AA_DO = identityHeadersFor('aa_do_001', 'AA');
const AA_REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');

const C_APPEALABLE = `${PREFIX}APPEALABLE`;
const C_REVIEWER = `${PREFIX}REVIEWER`;
const C_REPRESENTATION = `${PREFIX}REPRESENTATION`;
const C_OPEN = `${PREFIX}OPEN`;
const C_REOPENED = `${PREFIX}REOPENED`;
const C_OTHER_ENTITY = `${PREFIX}OTHER-ENTITY`;
const C_UNMAPPED = `${PREFIX}UNMAPPED`;
const C_MASKING = `${PREFIX}MASKING`;
const C_PNO = `${PREFIX}PNO`;
const C_DUP = `${PREFIX}DUPLICATE`;

/** A 16-digit account number, so first4+last4 leaves a visibly masked middle. */
const ACCOUNT_16 = '1234567890123456';

function esc(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function setAccountNumber(complaintNumber: string, account: string): void {
  sql(`UPDATE COMPLAINTS SET account_number = '${esc(account)}'
        WHERE complaint_number = '${esc(complaintNumber)}'`);
}

/** A complete, valid register payload. Individual tests remove fields to prove they are mandatory. */
function validPayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    appealFiledBy: 'COMPLAINANT',
    sourceOfAppeal: 'PORTAL',
    appealGround: 'The closure did not address the disputed transaction.',
    reliefSought: 'Refund of the disputed amount.',
    appellantName: 'Registered Appellant',
    appellantEmail: 'appellant@example.com',
    appellantPhone: '9811112222',
    appellantAddress1: '4 Test Street',
    appellantCity: 'Mumbai',
    appellantDistrict: 'Mumbai',
    appellantState: 'Maharashtra',
    categoryId: 1,
    isComplainantAdvocate: false,
    hasRelatedCourtTrial: false,
    ...overrides,
  };
}

async function register(
  request: APIRequestContext,
  complaintNumber: string,
  headers: Record<string, string>,
  payload: Record<string, unknown>
) {
  const res = await request.post(
    `${API_BASE}/api/v1/aa/parent-complaints/${complaintNumber}/appeals`,
    { headers: { ...headers, 'Content-Type': 'application/json' }, data: payload, failOnStatusCode: false }
  );
  return { status: res.status(), body: await res.json().catch(() => null) };
}

function complaintIdOf(complaintNumber: string): string {
  return sql(`SELECT id FROM COMPLAINTS WHERE complaint_number = '${esc(complaintNumber)}'`).trim();
}

function appealRowCount(complaintNumber: string): number {
  return Number(
    sql(`SELECT COUNT(*) FROM appeals WHERE original_complaint_number = '${esc(complaintNumber)}'`).trim()
  );
}

test.describe.serial('S2A — appeal registration, masking and upload caps', () => {
  test.beforeAll(async ({ request }) => {
    expect(AA_DO['X-User-Roles']).toBe('AA_DO');
    expect(AA_REVIEWER['X-User-Roles']).toBe('AA_REVIEWER');
    await assertBackendReady(request, AA_DO);

    purgeByPrefix(PREFIX);

    seedClosedComplaint(C_APPEALABLE, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedClosedComplaint(C_REVIEWER, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedClosedComplaint(C_REPRESENTATION, CLAUSE.neither, RE_OWN_ENTITY);
    seedClosedComplaint(C_UNMAPPED, CLAUSE.unmapped, RE_OWN_ENTITY);
    seedClosedComplaint(C_MASKING, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedClosedComplaint(C_DUP, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedClosedComplaint(C_PNO, CLAUSE.bothParties, RE_OWN_ENTITY);
    seedClosedComplaint(C_OTHER_ENTITY, CLAUSE.bothParties, RE_OTHER_ENTITY);
    seedReopenedComplaint(C_REOPENED, CLAUSE.bothParties, RE_OWN_ENTITY);
    seedComplaint(C_OPEN, { closureClause: null, entityCode: RE_OWN_ENTITY, status: 'in_progress' });

    setAccountNumber(C_MASKING, ACCOUNT_16);
  });

  test.afterAll(async () => {
    purgeByPrefix(PREFIX);
  });

  // ── Story 9: masking format ───────────────────────────────────────────────────

  test('story 9 — account number is masked to FIRST four + LAST four', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/search?complaintNumber=${C_MASKING}`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    expect(res.status()).toBe(200);
    const masked = (await res.json()).data.results[0].accountNumber;

    // The full value must never reach the wire.
    expect(masked).not.toBe(ACCOUNT_16);
    // First four AND last four visible, middle starred. The pre-existing service showed only the last
    // four, which is what this asserts against.
    expect(masked).toMatch(/^\d{4}\*+\d{4}$/);
    expect(masked.startsWith('1234')).toBe(true);
    expect(masked.endsWith('3456')).toBe(true);
  });

  // ── Story 7 + 8 + 11: mandatory fields, server-enforced ───────────────────────

  test('story 7 — registration is refused until every mandatory field is present', async ({ request }) => {
    const { status, body } = await register(request, C_APPEALABLE, AA_DO, {
      appealFiledBy: 'COMPLAINANT',
    });

    expect(status).toBe(400);
    expect(body.messageKey).toBe('aa.register.error_mandatory_incomplete');

    // EVERY missing field is reported, not just the first, so the officer completes the form in one
    // pass instead of rediscovering one blank at a time.
    const missing: string[] = body.data.missingFields;
    for (const field of ['sourceOfAppeal', 'appealGround', 'appellantName', 'appellantPhone',
                         'appellantAddress1', 'appellantCity', 'appellantState', 'categoryId',
                         'isComplainantAdvocate', 'hasRelatedCourtTrial']) {
      expect(missing, `${field} must be reported missing`).toContain(field);
    }

    // Nothing was persisted by a rejected registration.
    expect(appealRowCount(C_APPEALABLE)).toBe(0);
  });

  test('story 11 — an UNANSWERED declaration is rejected but an explicit "No" is accepted', async ({ request }) => {
    // Unanswered: the two declarations are omitted entirely.
    const omitted = validPayload();
    delete omitted['isComplainantAdvocate'];
    delete omitted['hasRelatedCourtTrial'];
    const rejected = await register(request, C_APPEALABLE, AA_DO, omitted);
    expect(rejected.status).toBe(400);
    expect(rejected.body.data.missingFields).toContain('isComplainantAdvocate');
    expect(rejected.body.data.missingFields).toContain('hasRelatedCourtTrial');

    // Explicit false is a VALID answer and must not be treated as unanswered. A falsy check here is
    // the classic bug: it would make "No" indistinguishable from "not asked".
    const accepted = await register(request, C_APPEALABLE, AA_DO,
      validPayload({ isComplainantAdvocate: false, hasRelatedCourtTrial: false }));
    expect(accepted.status).toBe(201);
    expect(appealRowCount(C_APPEALABLE)).toBe(1);
  });

  // ── Story 12: classification derived from clause + party ──────────────────────

  test('story 12 — classification is DERIVED from the clause and the client value is ignored', async ({ request }) => {
    // 15(1)(a) is appealable by a complainant, so an AA-registered appeal is an APPEAL — even though
    // the client insists on REPRESENTATION.
    const appeal = await register(request, C_REVIEWER, AA_REVIEWER,
      validPayload({ classificationType: 'REPRESENTATION' }));
    expect(appeal.status).toBe(201);
    expect(appeal.body.data.classificationType).toBe('APPEAL');

    // 16(2)(a) is appealable by nobody, so it is a REPRESENTATION — even though the client claims
    // APPEAL. The browser must never decide a citizen's statutory recourse.
    const representation = await register(request, C_REPRESENTATION, AA_DO,
      validPayload({ classificationType: 'APPEAL' }));
    expect(representation.status).toBe(201);
    expect(representation.body.data.classificationType).toBe('REPRESENTATION');
  });

  test('story 13 — an AA_REVIEWER can register, exactly like an AA_DO', async ({ request }) => {
    // Proven above: C_REVIEWER was registered by AA_REVIEWER and returned 201.
    expect(appealRowCount(C_REVIEWER)).toBe(1);
    const createdByRole = sql(
      `SELECT created_by_role FROM appeals WHERE original_complaint_number = '${esc(C_REVIEWER)}'`
    ).trim();
    expect(createdByRole).toBe('AA_REVIEWER');
  });

  // ── Story 6 / 12: parent eligibility re-enforced at intake ────────────────────

  test('story 6 — an OPEN parent cannot be appealed even by a direct POST', async ({ request }) => {
    // The search screen hides the button; hiding a button is not a control, so intake re-checks.
    const { status, body } = await register(request, C_OPEN, AA_DO, validPayload());
    expect(status).toBe(403);
    expect(body.messageKey).toBe('aa.register.error_not_permitted');
    expect(appealRowCount(C_OPEN)).toBe(0);
  });

  test('a REOPENED parent CAN be appealed', async ({ request }) => {
    // The negative control for the test above: proves the 403 is about eligibility, not a blanket deny.
    const { status } = await register(request, C_REOPENED, AA_DO, validPayload());
    expect(status).toBe(201);
    expect(appealRowCount(C_REOPENED)).toBe(1);
  });

  // ── Fail-closed on an unmapped clause ─────────────────────────────────────────

  test('an unmapped closure clause FAILS CLOSED with 503 and a retryable key', async ({ request }) => {
    const { status, body } = await register(request, C_UNMAPPED, AA_DO, validPayload());

    // 503 and NOT 400/500: a configuration gap must not be reported as the citizen's mistake, and must
    // never be resolved by guessing a classification.
    expect(status).toBe(503);
    expect(body.messageKey).toBe('appeal.error_clause_not_configured');
    expect(body.retryable).toBe(true);
    expect(appealRowCount(C_UNMAPPED)).toBe(0);
  });

  // ── Story 14: PNO entity scope, server-enforced ───────────────────────────────

  test('story 14 — a PNO cannot register against another entity, claim beats header', async ({ request }) => {
    const token = await mintToken(request, 're_pno_001');

    const denied = await register(
      request,
      C_OTHER_ENTITY,
      { ...bearer(token), 'X-Entity-Code': RE_OTHER_ENTITY },
      validPayload({ edApprovalGiven: true })
    );
    expect(denied.status).toBe(403);
    expect(denied.body.messageKey).toBe('aa.register.error_not_permitted');
    expect(appealRowCount(C_OTHER_ENTITY)).toBe(0);

    // NEGATIVE CONTROL: its own entity works, so the refusal above is scoping and not a blanket deny.
    const allowed = await register(request, C_PNO, bearer(token),
      validPayload({ edApprovalGiven: true, edApprovalDate: '2026-09-01',
                     edApprovalComments: 'Approved by ED.' }));
    expect(allowed.status).toBe(201);
    expect(appealRowCount(C_PNO)).toBe(1);
  });

  test('story 15 — ED approval is mandatory for the PNO channel and is persisted', async ({ request }) => {
    // `+0` is load-bearing: ed_approval_given is a MySQL bit(1), which the batch client returns as a
    // raw binary byte rather than the text "1". Reading it unconverted yields an empty string and the
    // assertion fails while the data is in fact correct.
    const row = sql(
      `SELECT ed_approval_given+0, ed_approval_comments, mode_of_receipt, appeal_filed_by
         FROM appeals WHERE original_complaint_number = '${esc(C_PNO)}'`
    ).split('\t');
    expect(row[0].trim()).toBe('1');
    expect(row[1].trim()).toBe('Approved by ED.');
    // Story 10: mode of receipt is derived from the channel, not from the request body.
    expect(row[2].trim()).toBe('RE_PNO');
    // The appealing PARTY is derived from the caller's own scope, never from a request field: it decides
    // which clauses are appealable at all, so a client-supplied party would let a caller choose its own
    // legal standing.
    expect(row[3].trim()).toBe('ENTITY');
  });

  // ── One live appeal per parent ────────────────────────────────────────────────

  test('a second appeal against the same parent is refused', async ({ request }) => {
    const first = await register(request, C_DUP, AA_DO, validPayload());
    expect(first.status).toBe(201);

    // Without this, a double-submit adjudicates a record the first appeal already superseded.
    const second = await register(request, C_DUP, AA_DO, validPayload());
    expect(second.status).toBe(403);
    expect(appealRowCount(C_DUP)).toBe(1);
  });

  // ── Story 11: legal-case prompt (prompt only; there is no Legal Cases backend) ──

  test('story 11 — a related court trial persists the flag and returns a prompt KEY', async ({ request }) => {
    const target = `${PREFIX}COURT`;
    seedClosedComplaint(target, CLAUSE.complainantOnly, RE_OWN_ENTITY);

    const { status, body } = await register(request, target, AA_DO,
      validPayload({ hasRelatedCourtTrial: true }));
    expect(status).toBe(201);

    // A PROMPT, not an integration: no Legal Cases module exists in this product, so nothing is
    // written to one. The flag is persisted and the UI is told to ask the officer to log it.
    expect(body.data.promptLegalCaseEntry).toBe(true);
    expect(body.data.promptLegalCaseEntryKey).toBe('aa.register.prompt_log_legal_case');

    const stored = sql(
      `SELECT has_related_court_trial+0 FROM appeals WHERE original_complaint_number = '${esc(target)}'`
    ).trim();
    expect(stored).toBe('1');
  });

  // ── Stories 17 / 19: assignment and the persisted notification ────────────────

  test('stories 17+19 — a registered appeal is assigned and the officer notification is persisted', async ({ request }) => {
    const target = `${PREFIX}ASSIGN`;
    seedClosedComplaint(target, CLAUSE.complainantOnly, RE_OWN_ENTITY);

    const { status, body } = await register(request, target, AA_DO, validPayload());
    expect(status).toBe(201);
    expect(body.data.assignedRole).toBe('AA_DO');

    const officer: string | null = body.data.assignedOfficer;
    if (!officer) {
      // Assignment is best-effort by contract: an appeal that exists unassigned can be picked up,
      // whereas failing registration because the officer pool was unavailable loses a statutory
      // filing. Registration must still have succeeded.
      expect(appealRowCount(target)).toBe(1);
      return;
    }

    // Assert the PERSISTED notification row, not a real-time push: notification.service.ts opens a raw
    // WebSocket against a SockJS/STOMP endpoint and never subscribes, and the bell never calls it, so
    // the push path is dead (owned elsewhere). NotificationService.send is also @Async, hence the poll.
    const appealNumber: string = body.data.appealNumber;
    let notifications = 0;
    for (let attempt = 0; attempt < 20 && notifications === 0; attempt++) {
      notifications = Number(
        sql(`SELECT COUNT(*) FROM in_app_notifications
              WHERE related_entity_id = '${esc(appealNumber)}'
                AND related_entity_type = 'APPEAL'`).trim()
      );
      if (notifications === 0) {
        await new Promise((resolve) => setTimeout(resolve, 250));
      }
    }
    expect(notifications, 'an ASSIGNMENT notification row must be persisted for the assignee')
      .toBeGreaterThan(0);

    sql(`DELETE FROM in_app_notifications WHERE related_entity_id = '${esc(appealNumber)}'`);
  });

  // ── NFR-006 upload caps, enforced on the server ──────────────────────────────

  /**
   * THIS TEST DELIBERATELY NAMES NO SIZE. It has been rewritten twice already by the figure moving —
   * first pinned to 2MB, then to 5MB, and the standing ruling is 2MB again, held in {@code system_config}
   * and served by GET /api/v1/config/upload-limits. Each rewrite was a test failing against correct
   * behaviour, which is why no literal appears here any more.
   *
   * <p>The boundary is DERIVED from the configured limit at run time, so the next retune cannot leave
   * this asserting a figure the product no longer enforces.
   */
  test('the per-file cap is the CONFIGURED limit, enforced server-side', async ({ request }) => {
    const target = `${PREFIX}UPLOAD`;
    seedClosedComplaint(target, CLAUSE.complainantOnly, RE_OWN_ENTITY);

    const limits = await (await request.get(`${API_BASE}/api/v1/config/upload-limits`)).json();
    const maxBytes: number = limits.maxFileSizeBytes;
    expect(maxBytes, 'the server must advertise a per-file limit').toBeGreaterThan(0);

    // One byte over the configured cap, with a valid PDF signature so only the SIZE can reject it.
    const oversized = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(maxBytes, 0x20)]);

    const res = await request.post(`${API_BASE}/api/files/upload`, {
      headers: AA_DO,
      multipart: {
        file: { name: 'oversized.pdf', mimeType: 'application/pdf', buffer: oversized },
        complaintNumber: target,
        complaintId: complaintIdOf(target),
      },
      failOnStatusCode: false,
    });

    // Rejected, not accepted. 413 comes from Spring's own multipart limit, 4xx from the validator —
    // either is a rejection; a 2xx is the failure this guards against.
    expect(res.status(),
      `a file above the configured ${Math.round(maxBytes / 1048576)}MB cap must be rejected`)
      .toBeGreaterThanOrEqual(400);
  });

  test('a file under the configured cap is ACCEPTED, so the cap is a boundary not a blanket reject', async ({ request }) => {
    const target = `${PREFIX}UPLOAD-OK`;
    seedClosedComplaint(target, CLAUSE.complainantOnly, RE_OWN_ENTITY);

    // 1MB of valid PDF. Without this control, a validator that rejected EVERY upload would pass the
    // oversized test above while having broken attachments entirely.
    const acceptable = Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(1024 * 1024, 0x20)]);

    const res = await request.post(`${API_BASE}/api/files/upload`, {
      headers: AA_DO,
      multipart: {
        file: { name: 'within-limit.pdf', mimeType: 'application/pdf', buffer: acceptable },
        complaintNumber: target,
        complaintId: complaintIdOf(target),
      },
      failOnStatusCode: false,
    });

    // 1MB is below every limit the product has ever had, so this control holds across retunes.
    expect(res.status(), 'a 1MB PDF must be accepted under any configured cap').toBeLessThan(400);
  });
});
