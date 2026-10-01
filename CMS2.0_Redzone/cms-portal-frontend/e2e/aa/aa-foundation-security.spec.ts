import { test, expect, APIRequestContext } from '../fixtures';
import { execFileSync } from 'child_process';
import { identityHeadersFor } from '../utils/test-data';
import { mintToken, purgeByPrefix } from './aa-shared-fixtures';

/**
 * Server-side enforcement tests for the AA foundation.
 *
 * These are API tests, not UI tests, deliberately: every control here has to hold against a raw HTTP
 * call. A UI test can only prove the Angular client does not offer a forbidden button, which is
 * exactly the class of "protection" the four fixes below replaced.
 *
 * Each area asserts BOTH directions — the forbidden call is refused with a specific status AND the
 * equivalent permitted call succeeds. A backend that had regressed into blanket-403 (or blanket-200)
 * would fail one half of every pair, so no assertion here can pass for the wrong reason.
 *
 * The four holes under test:
 *   1. AA role bypass       — the role/action matrix used to be skipped whenever the client omitted
 *                             `actorRole`, which the Angular client always did.
 *   2. Actor spoofing       — the recorded actor came from a request-body `actor` field.
 *   3. RE entity spoofing   — X-Entity-Code was trusted ahead of the JWT claim, defaulting to
 *                             "UNKNOWN_ENTITY" when neither resolved.
 *   4. Client classification — POST /appeals/file honoured a client `classification` param and
 *                             defaulted to APPEAL, letting the browser decide a legal question.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
// Keycloak's base URL now lives in aa-shared-fixtures alongside the mintToken that uses it.
const MYSQL =
  process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

/**
 * Seed prefix. Every row this suite creates carries it, so beforeAll can delete the previous run's
 * data unconditionally and the suite is re-runnable without a manual reset.
 */
const PREFIX = 'AATEST';

// Parent complaints, keyed by the closure clause under test. The clause drives classification, so
// each case needs its own parent — an appeal cannot be re-filed against a complaint that already has
// an active one.
const C_APPEALABLE_A = `${PREFIX}-CL-15-1-A`; // complainant-appealable, entity NOT
const C_APPEALABLE_B = `${PREFIX}-CL-15-1-B`; // appealable by both parties
const C_NOT_APPEALABLE = `${PREFIX}-CL-16-2-A`; // appealable by neither
const C_UNMAPPED = `${PREFIX}-CL-UNMAPPED`; // clause absent from CLOSURE_CLAUSE_MASTER
const C_ROLE_MATRIX = `${PREFIX}-CL-ROLE-MATRIX`;
const C_SPOOF_HEADERS = `${PREFIX}-CL-SPOOF-HDR`;
const C_SPOOF_JWT = `${PREFIX}-CL-SPOOF-JWT`;
const C_QUEUE = `${PREFIX}-CL-QUEUE`;
const C_OVERRIDE = `${PREFIX}-CL-OVERRIDE`;

// Entity-scope fixtures. re_pno_001's JWT carries entity_code='HDFC Bank', so a complaint belonging
// to another entity is the one the claim must keep out of reach no matter what header is sent.
//
// These are entity NAMES, not codes, because COMPLAINTS.entity_code actually stores the normalised
// entity name — RePortalService resolves entities by normalised name, and the live table holds values
// like 'HDFC Bank' and 'Test Bank Ltd'. A code-shaped fixture ('HDFC0001') matches no complaint, so
// the queue would come back empty and the scoping assertion would pass while testing nothing.
const RE_OWN_ENTITY = 'HDFC Bank';
const RE_OTHER_ENTITY = 'State Bank of India';
const C_RE_OWN = `${PREFIX}-RE-OWN`;
const C_RE_OTHER = `${PREFIX}-RE-OTHER`;

function sql(statement: string): string {
  return execFileSync(
    MYSQL,
    ['-u', 'cms_user', '-pcms_pass', 'cms_db', '--default-character-set=utf8mb4', '-N', '-B', '-e', statement],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  ).trim();
}

/**
 * Inserts a closed parent complaint directly.
 *
 * Direct INSERT rather than driving the CEPC workflow: the closure clause is the whole input to
 * classification, and the CEPC close path will not accept an arbitrary clause (nor one absent from
 * the Clause Master, which is precisely the fail-closed case under test). status is stored lowercase
 * in COMPLAINTS.
 */
function seedClosedComplaint(complaintNumber: string, closureClause: string | null, entityCode = RE_OWN_ENTITY): void {
  const clause = closureClause === null ? 'NULL' : `'${closureClause}'`;
  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, closure_clause, entity_code,
         priority, filing_type, scheme_version, record_version,
         created_at, updated_at, filed_at, closed_at)
       VALUES
        ('${complaintNumber}', 'AA Foundation Test', 'aafoundation@example.com', '9876543210',
         'AA foundation security fixture', 'Seeded by aa-foundation-security.spec.ts',
         'closed', ${clause}, '${entityCode}',
         'MEDIUM', 'CEPC_MANUAL', 'RBIOS_2021', 0,
         NOW(), NOW(), NOW(), NOW())`);
}

/** An entity-scoped complaint that is forwarded, not closed — the RE queue only shows live work. */
function seedForwardedComplaint(complaintNumber: string, entityCode: string): void {
  sql(`INSERT INTO COMPLAINTS
        (complaint_number, complainant_name, complainant_email, complainant_phone,
         subject, description, status, entity_code,
         priority, filing_type, record_version, created_at, updated_at, filed_at)
       VALUES
        ('${complaintNumber}', 'AA Foundation RE Test', 'aare@example.com', '9876543210',
         'RE entity scope fixture', 'Seeded by aa-foundation-security.spec.ts',
         'forwarded', '${entityCode}',
         'MEDIUM', 'CEPC_MANUAL', 0, NOW(), NOW(), NOW())`);
}

/**
 * Purge via the SHARED helper, which COLLATEs both sides of the `appeal_number IN (...)` comparison.
 *
 * This file carried its own copy that did not. appeal_number is utf8mb4_unicode_ci on appeals but
 * utf8mb4_0900_ai_ci on appeal_attachments, so the uncollated DELETE died with errno 1267
 * "Illegal mix of collations", threw in beforeAll, and took the rest of the serial file with it.
 * aa-shared-fixtures.purgeByPrefix already fixed exactly this; the duplicate had simply drifted.
 */
function purgeSeedData(): void {
  purgeByPrefix(PREFIX);
}

// mintToken lived here as a private copy with `password: 'test123'` hardcoded, which 401s for
// aa_do_001 and re_pno_001 (both Test@123) and took the whole serial file down from beforeAll.
// It now comes from aa-shared-fixtures, which resolves the password per username. Do not re-add a
// local copy — that is how the two versions drifted apart in the first place.

function decodeClaims(token: string): Record<string, unknown> {
  const payload = token.split('.')[1];
  return JSON.parse(Buffer.from(payload, 'base64').toString('utf8'));
}

interface FileAppealResult {
  status: number;
  body: Record<string, any>;
}

/**
 * Files an appeal, always sending a `classification` field so every call doubles as a check that the
 * client-supplied value is ignored.
 */
async function fileAppealRaw(
  request: APIRequestContext,
  complaintNumber: string,
  requestedClassification: string,
  identity: Record<string, string>
): Promise<FileAppealResult> {
  const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    multipart: {
      complaintNumber,
      ground: 'AA foundation security test ground',
      details: 'Filed by the AA foundation security spec to assert server-side classification.',
      reliefSought: 'Restoration of the disputed amount',
      classification: requestedClassification,
    },
    headers: identity,
    failOnStatusCode: false,
  });
  return { status: res.status(), body: await res.json() };
}

async function fileAppealExpectingSuccess(
  request: APIRequestContext,
  complaintNumber: string,
  requestedClassification: string,
  identity: Record<string, string>
): Promise<{ appealNumber: string; classificationType: string }> {
  const result = await fileAppealRaw(request, complaintNumber, requestedClassification, identity);
  expect(result.status, `filing against ${complaintNumber} — ${JSON.stringify(result.body)}`).toBe(201);
  return {
    appealNumber: result.body.data.appealNumber as string,
    classificationType: result.body.data.classificationType as string,
  };
}

async function performAction(
  request: APIRequestContext,
  appealNumber: string,
  body: Record<string, unknown>,
  headers: Record<string, string>
): Promise<{ status: number; body: Record<string, any> }> {
  const res = await request.post(`${API_BASE}/api/v1/appeals/${appealNumber}/action`, {
    data: body,
    headers: { 'Content-Type': 'application/json', ...headers },
    failOnStatusCode: false,
  });
  return { status: res.status(), body: await res.json() };
}

async function getTimeline(
  request: APIRequestContext,
  appealNumber: string,
  headers: Record<string, string>
): Promise<Array<Record<string, any>>> {
  const res = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}/timeline`, {
    headers,
    failOnStatusCode: false,
  });
  expect(res.status(), `timeline fetch for ${appealNumber}`).toBe(200);
  const body = await res.json();
  expect(body.success, `timeline lookup for ${appealNumber}: ${body.message}`).toBe(true);
  return body.data.timeline as Array<Record<string, any>>;
}

const AA_DO = identityHeadersFor('aa_do_001', 'AA');
const AA_REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');
const AA_SECRETARIAT = identityHeadersFor('aa_secretariat_001', 'AA');
const AA_ADMIN = identityHeadersFor('aa_admin_001', 'AA');

test.describe.serial('AA foundation — server-side enforcement', () => {
  test.beforeAll(async ({ request }) => {
    // The identity helper is the same one the other suites use; if it stopped mapping these accounts
    // to AA roles, every assertion below would be exercising the wrong user, so pin it here.
    expect(AA_DO['X-User-Roles']).toBe('AA_DO');
    expect(AA_REVIEWER['X-User-Roles']).toBe('AA_REVIEWER');
    expect(AA_SECRETARIAT['X-User-Roles']).toBe('AA_SECRETARIAT');
    expect(AA_ADMIN['X-User-Roles']).toBe('AA_ADMIN');

    const probe = await request.get(`${API_BASE}/api/v1/appeals/stats`, {
      headers: AA_DO,
      failOnStatusCode: false,
    });
    expect(probe.status(), `backend at ${API_BASE} must be up and admit AA_DO`).toBe(200);

    purgeSeedData();
    seedClosedComplaint(C_APPEALABLE_A, '15(1)(a)');
    seedClosedComplaint(C_APPEALABLE_B, '15(1)(b)');
    seedClosedComplaint(C_NOT_APPEALABLE, '16(2)(a)');
    seedClosedComplaint(C_UNMAPPED, '99(9)(z)');
    seedClosedComplaint(C_ROLE_MATRIX, '15(1)(a)');
    seedClosedComplaint(C_SPOOF_HEADERS, '15(1)(a)');
    seedClosedComplaint(C_SPOOF_JWT, '15(1)(a)');
    seedClosedComplaint(C_QUEUE, '15(1)(a)');
    seedClosedComplaint(C_OVERRIDE, '16(2)(a)');
    seedForwardedComplaint(C_RE_OWN, RE_OWN_ENTITY);
    seedForwardedComplaint(C_RE_OTHER, RE_OTHER_ENTITY);
  });

  test.afterAll(() => {
    purgeSeedData();
  });

  // ══════════════════════════════════════════════════════════════════
  // 1. AA role bypass — the role/action matrix is enforced unconditionally
  // ══════════════════════════════════════════════════════════════════

  test('role matrix: each AA role is refused another role\'s action with 403 but allowed its own', async ({
    request,
  }) => {
    const { appealNumber } = await fileAppealExpectingSuccess(request, C_ROLE_MATRIX, 'APPEAL', AA_DO);

    // Forbidden direction. ACCEPT belongs to AA_DO alone, so the three other roles must be refused —
    // and refused with 403, not a 200-with-success=false, which is how a validation failure reports.
    for (const [label, identity] of [
      ['AA_REVIEWER', AA_REVIEWER],
      ['AA_SECRETARIAT', AA_SECRETARIAT],
      ['AA_ADMIN', AA_ADMIN],
    ] as const) {
      const denied = await performAction(request, appealNumber, { action: 'ACCEPT', remarks: 'probe' }, identity);
      expect(denied.status, `${label} must not perform ACCEPT`).toBe(403);
      expect(denied.body.success).toBe(false);
      expect(denied.body.message).toContain(label);
      expect(denied.body.message).toMatch(/not permitted/i);
    }

    // A caller with an entirely foreign role is refused by the guard before the matrix is reached.
    const foreign = await performAction(
      request,
      appealNumber,
      { action: 'ACCEPT', remarks: 'probe' },
      { 'X-User-Id': 'cepc_do1', 'X-User-Roles': 'CEPC_DO' }
    );
    expect(foreign.status, 'a non-AA role must not reach the AA workflow').toBe(403);

    // NEGATIVE CONTROL. Without this, a backend that 403'd everything would pass the block above.
    const allowed = await performAction(
      request,
      appealNumber,
      { action: 'ACCEPT', remarks: 'AA_DO accepting, as the matrix permits' },
      AA_DO
    );
    expect(allowed.status, `AA_DO must be allowed ACCEPT — ${JSON.stringify(allowed.body)}`).toBe(200);
    expect(allowed.body.data.newStatus).toBe('under_review');

    // Reciprocal pair: CLOSE belongs to AA_ADMIN only. Asserted in both directions on the same
    // appeal so the matrix is proven to discriminate per-action, not merely to favour AA_DO.
    const doClose = await performAction(request, appealNumber, { action: 'CLOSE', remarks: 'probe' }, AA_DO);
    expect(doClose.status, 'AA_DO must not perform CLOSE').toBe(403);
    expect(doClose.body.message).toContain('AA_DO');

    const adminClose = await performAction(
      request,
      appealNumber,
      { action: 'CLOSE', remarks: 'Administrative closure by AA_ADMIN' },
      AA_ADMIN
    );
    expect(adminClose.status, `AA_ADMIN must be allowed CLOSE — ${JSON.stringify(adminClose.body)}`).toBe(200);
    expect(adminClose.body.data.newStatus).toBe('closed');
  });

  test('role matrix is enforced for a real JWT, and the token role beats a conflicting header', async ({
    request,
  }) => {
    const token = await mintToken(request, 'aa_do_001');
    const claims = decodeClaims(token);
    expect((claims['realm_access'] as any).roles, 'aa_do_001 must actually hold AA_DO').toContain('AA_DO');

    const { appealNumber } = await fileAppealExpectingSuccess(request, C_SPOOF_JWT, 'APPEAL', {
      Authorization: `Bearer ${token}`,
    });

    // The dev headers claim AA_ADMIN; the token says AA_DO. The token has to win, so CLOSE (AA_ADMIN
    // only) must still be refused. This is the escalation path a compromised client would take.
    const escalation = await performAction(
      request,
      appealNumber,
      { action: 'CLOSE', remarks: 'escalation attempt via header' },
      { Authorization: `Bearer ${token}`, 'X-User-Roles': 'AA_ADMIN', 'X-User-Id': 'aa_admin_001' }
    );
    expect(escalation.status, 'a header must not upgrade the token role').toBe(403);
    expect(escalation.body.message).toContain('AA_DO');

    // NEGATIVE CONTROL: the same token performing its own action succeeds, so the 403 above is the
    // matrix at work and not a broken token.
    const permitted = await performAction(
      request,
      appealNumber,
      { action: 'ACCEPT', remarks: 'AA_DO accepting under a real token' },
      { Authorization: `Bearer ${token}` }
    );
    expect(permitted.status, `real AA_DO token must be allowed ACCEPT — ${JSON.stringify(permitted.body)}`).toBe(200);
  });

  // ══════════════════════════════════════════════════════════════════
  // 2. Actor spoofing — attribution comes from the resolver, not the body
  // ══════════════════════════════════════════════════════════════════

  test('the persisted actor is the authenticated user, not the body\'s `actor` field', async ({ request }) => {
    const { appealNumber } = await fileAppealExpectingSuccess(request, C_SPOOF_HEADERS, 'APPEAL', AA_DO);

    const spoofed = await performAction(
      request,
      appealNumber,
      {
        action: 'ACCEPT',
        remarks: 'attribution probe',
        actor: 'attacker',
        actorRole: 'AA_ADMIN',
        performedBy: 'attacker',
      },
      AA_DO
    );
    expect(spoofed.status, `${JSON.stringify(spoofed.body)}`).toBe(200);

    const timeline = await getTimeline(request, appealNumber, AA_DO);
    const accept = timeline.find((t) => t.action === 'ACCEPT');
    expect(accept, 'the ACCEPT must be recorded on the timeline').toBeTruthy();
    expect(accept!['performedBy'], 'attribution must be the authenticated user').toBe('aa_do_001');
    expect(accept!['performedByRole'], 'role must be the resolved role, not the body\'s claim').toBe('AA_DO');
    expect(accept!['performedBy']).not.toBe('attacker');
    expect(accept!['performedByRole']).not.toBe('AA_ADMIN');

    // The DB is the record of truth; an API projection could in principle mask a spoofed row.
    const persisted = sql(
      `SELECT performed_by, performed_by_role FROM appeal_timeline
        WHERE appeal_number = '${appealNumber}' AND action = 'ACCEPT'`
    );
    expect(persisted, 'APPEAL_TIMELINE must attribute the row to the authenticated user').toBe('aa_do_001\tAA_DO');
    expect(sql(`SELECT COUNT(*) FROM appeal_timeline WHERE performed_by = 'attacker'`)).toBe('0');
  });

  test('attribution under a real JWT ignores both the body actor and the X-User-Id header', async ({ request }) => {
    const token = await mintToken(request, 'aa_secretariat_001');
    const { appealNumber } = await fileAppealExpectingSuccess(request, C_QUEUE, 'APPEAL', AA_DO);

    // Reach a state AA_SECRETARIAT can act on, then have the secretariat DISMISS while claiming to be
    // someone else in both the body and the header.
    const accepted = await performAction(request, appealNumber, { action: 'ACCEPT', remarks: 'setup' }, AA_DO);
    expect(accepted.status).toBe(200);

    const dismissed = await performAction(
      request,
      appealNumber,
      { action: 'DISMISS', remarks: 'Dismissed for the attribution test', actor: 'ghost_user' },
      { Authorization: `Bearer ${token}`, 'X-User-Id': 'ghost_user' }
    );
    expect(dismissed.status, `${JSON.stringify(dismissed.body)}`).toBe(200);

    const timeline = await getTimeline(request, appealNumber, AA_DO);
    const dismiss = timeline.find((t) => t.action === 'DISMISS');
    expect(dismiss!['performedBy'], 'the JWT preferred_username must win').toBe('aa_secretariat_001');
    expect(dismiss!['performedByRole']).toBe('AA_SECRETARIAT');
  });

  // ══════════════════════════════════════════════════════════════════
  // 3. RE entity scope — the JWT claim wins over X-Entity-Code
  // ══════════════════════════════════════════════════════════════════

  test('RE queue is scoped by the JWT entity_code claim; a conflicting X-Entity-Code header is ignored', async ({
    request,
  }) => {
    const token = await mintToken(request, 're_pno_001');
    const claims = decodeClaims(token);
    expect(claims['entity_code'], 're_pno_001 must carry the entity claim under test').toBe(RE_OWN_ENTITY);

    const fetchQueue = async (headers: Record<string, string>) => {
      const res = await request.get(`${API_BASE}/api/v1/re-portal/complaints?size=100`, {
        headers,
        failOnStatusCode: false,
      });
      expect(res.status(), `RE queue fetch — ${await res.text()}`).toBe(200);
      const body = await res.json();
      return (body.data as Array<Record<string, any>>).map((c) => c.complaintNumber as string);
    };

    // Baseline: the claim alone yields the caller's own entity and nothing else.
    const claimOnly = await fetchQueue({ Authorization: `Bearer ${token}` });
    expect(claimOnly, 'own-entity complaint must be visible').toContain(C_RE_OWN);
    expect(claimOnly, 'another entity\'s complaint must never be visible').not.toContain(C_RE_OTHER);

    // The spoof: a conflicting header must change nothing.
    const spoofed = await fetchQueue({
      Authorization: `Bearer ${token}`,
      'X-Entity-Code': RE_OTHER_ENTITY,
      'X-User-Entity': RE_OTHER_ENTITY,
    });
    expect(spoofed, 'a spoofed header must not reveal another entity').not.toContain(C_RE_OTHER);
    expect(spoofed, 'the claim scope must be unchanged by the header').toContain(C_RE_OWN);
    expect(spoofed.sort()).toEqual(claimOnly.sort());

    // NEGATIVE CONTROL. C_RE_OTHER is reachable by a caller genuinely scoped to SBI0001 (via the
    // dev-header path, since no SBI account exists), so the exclusion above is entity scoping and not
    // a missing fixture or an empty table.
    const otherEntityQueue = await fetchQueue({
      ...identityHeadersFor('re_pno_002', 'RE'),
      'X-Entity-Code': RE_OTHER_ENTITY,
    });
    expect(otherEntityQueue, 'the fixture must exist and be reachable by its real owner').toContain(C_RE_OTHER);
    expect(otherEntityQueue, 'SBI scope must not see HDFC work').not.toContain(C_RE_OWN);
  });

  test('an RE caller whose entity cannot be resolved gets 403, not a default scope', async ({ request }) => {
    // A valid RE role but no entity anywhere: the old code returned "UNKNOWN_ENTITY" and ran the query.
    const noEntity = await request.get(`${API_BASE}/api/v1/re-portal/complaints`, {
      headers: { 'X-User-Id': 're_pno_001', 'X-User-Roles': 'RE_PNO' },
      failOnStatusCode: false,
    });
    expect(noEntity.status(), 'unresolvable entity must be refused').toBe(403);
    const body = await noEntity.json();
    expect(JSON.stringify(body)).toMatch(/regulated entity could not be determined/i);

    // And it must not have silently produced a payload.
    expect(body['data']).toBeUndefined();

    // NEGATIVE CONTROL: the identical request WITH an entity succeeds, so the 403 is entity
    // resolution failing closed and not the endpoint being unreachable.
    const withEntity = await request.get(`${API_BASE}/api/v1/re-portal/complaints`, {
      headers: { 'X-User-Id': 're_pno_001', 'X-User-Roles': 'RE_PNO', 'X-Entity-Code': RE_OWN_ENTITY },
      failOnStatusCode: false,
    });
    expect(withEntity.status(), 'the same call with an entity must succeed').toBe(200);
  });

  // ══════════════════════════════════════════════════════════════════
  // 4. Clause-derived classification — the client cannot choose it
  // ══════════════════════════════════════════════════════════════════

  test('classification is derived from the closure clause and the client\'s value is ignored', async ({
    request,
  }) => {
    // The headline case: a non-appealable clause yields REPRESENTATION even though the request asks
    // for APPEAL in as many words.
    const representation = await fileAppealExpectingSuccess(request, C_NOT_APPEALABLE, 'APPEAL', AA_DO);
    expect(
      representation.classificationType,
      '16(2)(a) is appealable by nobody, so the requested APPEAL must be overridden'
    ).toBe('REPRESENTATION');
    expect(
      sql(`SELECT classification_type FROM appeals WHERE appeal_number = '${representation.appealNumber}'`),
      'the persisted row must match the derived value'
    ).toBe('REPRESENTATION');

    // NEGATIVE CONTROL, and the mirror image: an appealable clause yields APPEAL even though the
    // request asks for REPRESENTATION. Without this, a server that hardcoded REPRESENTATION would
    // pass the assertion above.
    const appeal = await fileAppealExpectingSuccess(request, C_APPEALABLE_A, 'REPRESENTATION', AA_DO);
    expect(
      appeal.classificationType,
      '15(1)(a) is complainant-appealable, so the requested REPRESENTATION must be overridden'
    ).toBe('APPEAL');
    expect(sql(`SELECT classification_type FROM appeals WHERE appeal_number = '${appeal.appealNumber}'`)).toBe(
      'APPEAL'
    );

    // The clause is carried onto the appeal, which is the audit trail for the decision.
    expect(sql(`SELECT closure_clause FROM appeals WHERE appeal_number = '${appeal.appealNumber}'`)).toBe('15(1)(a)');
  });

  test('appealability depends on the raising party: an ENTITY may appeal 15(1)(b) but not 15(1)(a)', async ({
    request,
  }) => {
    const reIdentity = {
      ...identityHeadersFor('re_pno_001', 'RE'),
      'X-Entity-Code': RE_OWN_ENTITY,
    };
    expect(reIdentity['X-User-Roles']).toBe('RE_PNO');

    // 15(1)(a) is appealable by the complainant only, so the same clause classifies differently for
    // an entity than it did for the AA_DO-filed case above.
    const dedicatedA = `${PREFIX}-CL-ENTITY-A`;
    const dedicatedB = `${PREFIX}-CL-ENTITY-B`;
    seedClosedComplaint(dedicatedA, '15(1)(a)');
    seedClosedComplaint(dedicatedB, '15(1)(b)');

    const asEntityOnA = await fileAppealExpectingSuccess(request, dedicatedA, 'APPEAL', reIdentity);
    expect(
      asEntityOnA.classificationType,
      'an entity may not appeal 15(1)(a), so this is a REPRESENTATION'
    ).toBe('REPRESENTATION');

    const asEntityOnB = await fileAppealExpectingSuccess(request, dedicatedB, 'REPRESENTATION', reIdentity);
    expect(asEntityOnB.classificationType, 'an entity MAY appeal 15(1)(b)').toBe('APPEAL');

    // The party is recorded from the caller's roles, not from a request field.
    expect(sql(`SELECT appeal_filed_by FROM appeals WHERE appeal_number = '${asEntityOnB.appealNumber}'`)).toBe(
      'ENTITY'
    );
    expect(sql(`SELECT appeal_filed_by FROM appeals WHERE appeal_number = '${asEntityOnA.appealNumber}'`)).toBe(
      'ENTITY'
    );
  });

  test('an unmapped closure clause fails closed and does not default to APPEAL', async ({ request }) => {
    // 503 + a translation key, NOT a bare 500. Failing closed is correct — guessing could deny a
    // citizen statutory recourse — but the citizen must be told something actionable, and the flow is
    // genuinely retryable once an AA Admin configures the clause. A 500 read as a transient fault the
    // citizen should retry identically forever.
    const unmapped = await fileAppealRaw(request, C_UNMAPPED, 'APPEAL', AA_DO);
    expect(
      unmapped.status,
      `99(9)(z) is absent from CLOSURE_CLAUSE_MASTER, so filing must fail closed — got ${JSON.stringify(unmapped.body)}`
    ).toBe(503);
    expect(unmapped.body.success).toBe(false);
    expect(unmapped.body.messageKey, 'the citizen-facing message must be translatable, not an English literal')
      .toBe('appeal.error_clause_not_configured');
    expect(unmapped.body.retryable).toBe(true);
    expect(unmapped.body.clauseCode).toBe('99(9)(z)');

    // The point of failing closed: no appeal row at all, and certainly not an APPEAL one.
    expect(
      sql(`SELECT COUNT(*) FROM appeals WHERE original_complaint_number = '${C_UNMAPPED}'`),
      'nothing may be persisted for an unclassifiable complaint'
    ).toBe('0');

    // The story requires an AA Admin configuration alert, not a silent block: a citizen's escalation
    // is stuck until someone adds the clause.
    expect(
      Number(sql(`SELECT COUNT(*) FROM in_app_notifications WHERE type = 'CONFIGURATION_ALERT' AND related_entity_id = '99(9)(z)'`)),
      'an unmapped clause must alert AA Admin'
    ).toBeGreaterThan(0);

    // Same for a complaint with no clause recorded at all.
    const noClause = `${PREFIX}-CL-NULL`;
    seedClosedComplaint(noClause, null);
    const missing = await fileAppealRaw(request, noClause, 'APPEAL', AA_DO);
    expect(missing.status, `a complaint with no closure clause must not be classifiable`).toBe(503);
    expect(sql(`SELECT COUNT(*) FROM appeals WHERE original_complaint_number = '${noClause}'`)).toBe('0');

    // NEGATIVE CONTROL: a mapped clause on an otherwise identical fixture files successfully, so the
    // 503s above are the clause lookup failing closed and not filing being broken outright.
    const mapped = `${PREFIX}-CL-CONTROL`;
    seedClosedComplaint(mapped, '15(1)(b)');
    const ok = await fileAppealExpectingSuccess(request, mapped, 'APPEAL', AA_DO);
    expect(ok.classificationType).toBe('APPEAL');
  });

  // ══════════════════════════════════════════════════════════════════
  // 5. New endpoints — `me` resolves server-side; override is guarded
  // ══════════════════════════════════════════════════════════════════

  test('GET /appeals resolves `me` to the caller and cannot be pointed at another user', async ({ request }) => {
    const listFor = async (query: string, headers: Record<string, string>) => {
      const res = await request.get(`${API_BASE}/api/v1/appeals${query}`, { headers, failOnStatusCode: false });
      expect(res.status(), `list ${query} — ${await res.text()}`).toBe(200);
      const body = await res.json();
      return (body.data as Array<Record<string, any>>).map((a) => a.appealNumber as string);
    };

    const mine = `${PREFIX}-CL-MINE`;
    seedClosedComplaint(mine, '15(1)(a)');
    const own = await fileAppealExpectingSuccess(request, mine, 'APPEAL', AA_DO);

    // `me` as aa_do_001 sees its own filing.
    const asDo = await listFor('?createdBy=me', AA_DO);
    expect(asDo, 'aa_do_001 filed this, so `me` must include it').toContain(own.appealNumber);

    // `me` as a different user does NOT — the literal is resolved server-side per caller.
    const asAdmin = await listFor('?createdBy=me', AA_ADMIN);
    expect(asAdmin, 'aa_admin_001 did not file it, so `me` must exclude it').not.toContain(own.appealNumber);

    // Same for the assignment view: the appeal is assigned to an AA_DO officer, so an AA_ADMIN asking
    // for "assigned to me" must not receive it.
    const assignedToAdmin = await listFor('?assignedOfficer=me', AA_ADMIN);
    expect(assignedToAdmin).not.toContain(own.appealNumber);

    // A role with no AA membership cannot read the queue at all.
    const noRole = await request.get(`${API_BASE}/api/v1/appeals`, { failOnStatusCode: false });
    expect(noRole.status(), 'the list endpoint must require an AA role').toBe(403);

    // Filters actually filter, so the `me` assertions above are not passing on an empty result set.
    const representations = await request.get(`${API_BASE}/api/v1/appeals?classification=REPRESENTATION`, {
      headers: AA_DO,
      failOnStatusCode: false,
    });
    expect(representations.status()).toBe(200);
    const repBody = await representations.json();
    const repTypes = new Set((repBody.data as Array<Record<string, any>>).map((a) => a.classification));
    expect(repTypes.size, 'the REPRESENTATION filter must return something to filter').toBeGreaterThan(0);
    expect([...repTypes], 'the classification filter must exclude APPEALs').toEqual(['REPRESENTATION']);
  });

  test('classification override requires an allowed role, a real reason, and an actual change', async ({
    request,
  }) => {
    const target = await fileAppealExpectingSuccess(request, C_OVERRIDE, 'APPEAL', AA_DO);
    expect(target.classificationType, '16(2)(a) derives to REPRESENTATION').toBe('REPRESENTATION');

    const override = async (headers: Record<string, string>, data: Record<string, unknown>) => {
      const res = await request.put(`${API_BASE}/api/v1/appeals/${target.appealNumber}/classification`, {
        data,
        headers: { 'Content-Type': 'application/json', ...headers },
        failOnStatusCode: false,
      });
      return { status: res.status(), body: await res.json() };
    };

    // AA_SECRETARIAT is outside the override role set.
    const wrongRole = await override(AA_SECRETARIAT, {
      classificationType: 'APPEAL',
      reason: 'A sufficiently long stated reason',
    });
    expect(wrongRole.status, 'AA_SECRETARIAT must not override the classification').toBe(403);

    // A too-short reason is a 400, distinct from the 403 above — an unauditable override is refused
    // as bad input, not as a permission problem.
    const shortReason = await override(AA_DO, { classificationType: 'APPEAL', reason: 'short' });
    expect(shortReason.status).toBe(400);
    expect(shortReason.body.message).toMatch(/at least 10 characters/i);

    // A no-op is refused, so the audit trail cannot be padded with meaningless entries.
    const noop = await override(AA_DO, {
      classificationType: 'REPRESENTATION',
      reason: 'Restating the value it already has',
    });
    expect(noop.status).toBe(400);
    expect(noop.body.message).toMatch(/already classified/i);

    // Nothing above may have taken effect.
    expect(sql(`SELECT classification_type FROM appeals WHERE appeal_number = '${target.appealNumber}'`)).toBe(
      'REPRESENTATION'
    );

    // NEGATIVE CONTROL: a well-formed override by an allowed role succeeds and is audited
    // field-level, so the refusals above are the individual rules and not a dead endpoint.
    const ok = await override(AA_DO, {
      classificationType: 'APPEAL',
      reason: 'Reclassified after a review of the closure clause and the award',
    });
    expect(ok.status, `${JSON.stringify(ok.body)}`).toBe(200);
    expect(ok.body.data.previousClassification).toBe('REPRESENTATION');
    expect(ok.body.data.classificationType).toBe('APPEAL');
    expect(ok.body.data.overriddenBy, 'the override must be attributed to the authenticated user').toBe('aa_do_001');

    const timeline = await getTimeline(request, target.appealNumber, AA_DO);
    const audit = timeline.find((t) => t.action === 'CLASSIFICATION_OVERRIDE');
    expect(audit, 'the override must leave a timeline entry').toBeTruthy();
    expect(audit!['fieldName']).toBe('classificationType');
    expect(audit!['oldValue']).toBe('REPRESENTATION');
    expect(audit!['newValue']).toBe('APPEAL');
    expect(audit!['performedBy']).toBe('aa_do_001');

    // classification_overridden is a bit(1); CAST so the CLI emits a readable 0/1 rather than a raw byte.
    expect(
      sql(`SELECT classification_type, CAST(classification_overridden AS UNSIGNED), classification_overridden_by
             FROM appeals WHERE appeal_number = '${target.appealNumber}'`)
    ).toBe('APPEAL\t1\taa_do_001');
  });
});
