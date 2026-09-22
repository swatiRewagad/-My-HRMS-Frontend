/**
 * S2A — RE Principal Nodal Officer appeal creation (stories 5, 14, 15, 16).
 *
 * API-LEVEL BY DESIGN. The PNO's entity scope is the whole point of these stories, and a scope is only
 * a control if the SERVER enforces it: a browser test could only show that the RE portal hid a button,
 * which a direct POST ignores. Every test here therefore drives the API with a REAL re_pno_001 token.
 *
 * Run against this session's backend:
 *   API_BASE_URL=http://localhost:8092 npx playwright test e2e/re-portal/s2a-pno-appeal.spec.ts \
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
  decodeClaims,
  mintToken,
  purgeByPrefix,
  seedClosedComplaint,
  seedComplaint,
  seedReopenedComplaint,
  sql,
} from '../aa/aa-shared-fixtures';

const PREFIX = 'S2A-PNO-';

const AA_DO = identityHeadersFor('aa_do_001', 'AA');

const C_OWN_APPEALABLE = `${PREFIX}OWN-15-1-B`;
const C_OWN_REPRESENTATION = `${PREFIX}OWN-15-1-A`;
const C_OWN_REOPENED = `${PREFIX}OWN-REOPENED`;
const C_OWN_OPEN = `${PREFIX}OWN-OPEN`;
const C_OTHER = `${PREFIX}OTHER-ENTITY`;

function esc(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

function registerForm(request: APIRequestContext, complaintNumber: string, headers: Record<string, string>) {
  return request.get(`${API_BASE}/api/v1/aa/parent-complaints/${complaintNumber}/register-form`, {
    headers,
    failOnStatusCode: false,
  });
}

function appealRowCount(complaintNumber: string): number {
  return Number(
    sql(`SELECT COUNT(*) FROM appeals WHERE original_complaint_number = '${esc(complaintNumber)}'`).trim()
  );
}

test.describe.serial('S2A — PNO appeal creation and entity scope', () => {
  let pnoToken: string;

  test.beforeAll(async ({ request }) => {
    await assertBackendReady(request, AA_DO);
    purgeByPrefix(PREFIX);

    pnoToken = await mintToken(request, 're_pno_001');
    // Pin the fixture assumption. Every scoping test below is vacuous if the token is not scoped to
    // the entity the fixtures use.
    expect(decodeClaims(pnoToken)['entity_code'], 're_pno_001 entity_code claim').toBe(RE_OWN_ENTITY);

    seedClosedComplaint(C_OWN_APPEALABLE, CLAUSE.bothParties, RE_OWN_ENTITY);
    seedClosedComplaint(C_OWN_REPRESENTATION, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedReopenedComplaint(C_OWN_REOPENED, CLAUSE.bothParties, RE_OWN_ENTITY);
    seedClosedComplaint(C_OTHER, CLAUSE.bothParties, RE_OTHER_ENTITY);
    seedComplaint(C_OWN_OPEN, { closureClause: null, entityCode: RE_OWN_ENTITY, status: 'in_progress' });
  });

  test.afterAll(async () => {
    purgeByPrefix(PREFIX);
  });

  test('story 14 — a PNO may open the register form for its OWN closed complaint', async ({ request }) => {
    const res = await registerForm(request, C_OWN_APPEALABLE, bearer(pnoToken));
    expect(res.status()).toBe(200);

    const data = (await res.json()).data;
    expect(data.appealEligible).toBe(true);
    // Story 10: the channel decides the mode of receipt, and the client cannot declare it.
    expect(data.modeOfReceipt).toBe('RE_PNO');
    expect(data.modeOfReceiptReadOnly).toBe(true);
    // Story 15: the ED approval block is required for this channel only.
    expect(data.edApprovalRequired).toBe(true);
  });

  test('story 14 — a PNO is refused another entity\'s complaint, and gets 404 not 403', async ({ request }) => {
    const res = await registerForm(request, C_OTHER, bearer(pnoToken));

    // 404 deliberately: a 403 would confirm the complaint number exists, making this endpoint an
    // oracle for probing other banks' complaint numbers.
    expect(res.status()).toBe(404);
  });

  test('story 14 — a conflicting X-Entity-Code header cannot widen the PNO scope', async ({ request }) => {
    const res = await registerForm(request, C_OTHER, {
      ...bearer(pnoToken),
      'X-Entity-Code': RE_OTHER_ENTITY,
      'X-User-Entity': RE_OTHER_ENTITY,
    });
    expect(res.status()).toBe(404);
  });

  test('NEGATIVE CONTROL — the other entity\'s complaint IS reachable by AA staff', async ({ request }) => {
    // Proves the 404s above are entity scoping and not a broken fixture or a blanket denial.
    const res = await registerForm(request, C_OTHER, AA_DO);
    expect(res.status()).toBe(200);
    expect((await res.json()).data.complaintNumber).toBe(C_OTHER);
  });

  test('story 14 — a REOPENED own complaint is appealable; an OPEN one is not', async ({ request }) => {
    const reopened = await registerForm(request, C_OWN_REOPENED, bearer(pnoToken));
    expect(reopened.status()).toBe(200);
    expect((await reopened.json()).data.appealEligible).toBe(true);

    const open = await registerForm(request, C_OWN_OPEN, bearer(pnoToken));
    expect(open.status()).toBe(200);
    const openData = (await open.json()).data;
    expect(openData.appealEligible).toBe(false);
    expect(openData.appealEligibilityKey).toBe('aa.register.error_parent_not_appealable');
  });

  test('story 14 — clause appealability by ENTITY is exposed so the label is Appeal vs Representation', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/masters/closure-clauses`,
      { headers: bearer(pnoToken), failOnStatusCode: false }
    );
    expect(res.status()).toBe(200);
    const clauses = (await res.json()).data;

    // The RBI ruling: a complainant may appeal 15(1)(a) and 15(1)(b); an ENTITY may appeal only
    // 15(1)(b). The UI label follows this flag rather than a hardcoded clause list.
    const bothParties = clauses.find((c: any) => c.clauseCode === CLAUSE.bothParties);
    const complainantOnly = clauses.find((c: any) => c.clauseCode === CLAUSE.complainantOnly);
    expect(bothParties.appealableByEntity).toBe(true);
    expect(complainantOnly.appealableByEntity).toBe(false);
  });

  test('story 15 — a PNO registration without the ED approval answer is refused', async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/aa/parent-complaints/${C_OWN_APPEALABLE}/appeals`,
      {
        headers: { ...bearer(pnoToken), 'Content-Type': 'application/json' },
        data: {
          appealFiledBy: 'ENTITY',
          sourceOfAppeal: 'RE_PORTAL',
          appealGround: 'The award exceeds the documented loss.',
          appellantName: 'Entity Appellant',
          appellantPhone: '9822223333',
          appellantAddress1: '9 Bank Road',
          appellantCity: 'Mumbai',
          appellantState: 'Maharashtra',
          categoryId: 1,
          isComplainantAdvocate: false,
          hasRelatedCourtTrial: false,
          // edApprovalGiven deliberately omitted.
        },
        failOnStatusCode: false,
      }
    );

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.messageKey).toBe('aa.register.error_mandatory_incomplete');
    expect(body.data.missingFields).toContain('edApprovalGiven');
    expect(appealRowCount(C_OWN_APPEALABLE)).toBe(0);
  });

  test('story 14+15 — a complete PNO registration succeeds and records ENTITY as the party', async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/aa/parent-complaints/${C_OWN_APPEALABLE}/appeals`,
      {
        headers: { ...bearer(pnoToken), 'Content-Type': 'application/json' },
        data: {
          appealFiledBy: 'COMPLAINANT', // Deliberately WRONG: the server must override this.
          sourceOfAppeal: 'RE_PORTAL',
          appealGround: 'The award exceeds the documented loss.',
          appellantName: 'Entity Appellant',
          appellantPhone: '9822223333',
          appellantAddress1: '9 Bank Road',
          appellantCity: 'Mumbai',
          appellantState: 'Maharashtra',
          categoryId: 1,
          isComplainantAdvocate: false,
          hasRelatedCourtTrial: false,
          edApprovalGiven: true,
          edApprovalDate: '2026-09-10',
          edApprovalComments: 'Approved by the Executive Director.',
        },
        failOnStatusCode: false,
      }
    );

    expect(res.status()).toBe(201);
    const data = (await res.json()).data;

    // 15(1)(b) is appealable by an entity, so this is an APPEAL rather than a REPRESENTATION.
    expect(data.classificationType).toBe('APPEAL');
    expect(data.modeOfReceipt).toBe('RE_PNO');

    // The stored party is the SERVER's, not the body's. appealFiledBy decides legal standing, so a
    // client-supplied value would let the caller choose which clauses it may appeal.
    const party = sql(
      `SELECT appeal_filed_by FROM appeals WHERE original_complaint_number = '${esc(C_OWN_APPEALABLE)}'`
    ).trim();
    expect(party).toBe('ENTITY');
  });

  test('story 14 — an entity may NOT appeal a complainant-only clause; it becomes a REPRESENTATION', async ({ request }) => {
    const res = await request.post(
      `${API_BASE}/api/v1/aa/parent-complaints/${C_OWN_REPRESENTATION}/appeals`,
      {
        headers: { ...bearer(pnoToken), 'Content-Type': 'application/json' },
        data: {
          appealFiledBy: 'ENTITY',
          sourceOfAppeal: 'RE_PORTAL',
          appealGround: 'Escalating the closure under 15(1)(a).',
          appellantName: 'Entity Appellant',
          appellantPhone: '9822223333',
          appellantAddress1: '9 Bank Road',
          appellantCity: 'Mumbai',
          appellantState: 'Maharashtra',
          categoryId: 1,
          isComplainantAdvocate: false,
          hasRelatedCourtTrial: false,
          edApprovalGiven: true,
        },
        failOnStatusCode: false,
      }
    );

    expect(res.status()).toBe(201);
    // 15(1)(a) is appealable by a COMPLAINANT only. An entity escalating it gets a REPRESENTATION —
    // this is the party-dependent half of the rule, and the reason appealability is two flags not one.
    expect((await res.json()).data.classificationType).toBe('REPRESENTATION');
  });
});
