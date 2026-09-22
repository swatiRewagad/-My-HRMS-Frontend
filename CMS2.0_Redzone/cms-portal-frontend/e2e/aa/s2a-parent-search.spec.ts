/**
 * S2A — AA parent-complaint search, register-form autofill and appeal registration.
 *
 * API-LEVEL BY DESIGN. Every assertion here is about a SERVER-SIDE control: entity scoping,
 * classification derivation, mandatory-field validation, appeal eligibility, PII masking and the
 * NFR-006 upload caps. A browser test can only prove the client hid a button, which is exactly the
 * class of non-protection this module has had to replace before. The UI is covered separately.
 *
 * Run against this session's backend:
 *   API_BASE_URL=http://localhost:8092 npx playwright test e2e/aa/s2a-parent-search.spec.ts \
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
  assertKeysTranslatedInAllLocales,
  bearer,
  decodeClaims,
  mintToken,
  purgeByPrefix,
  seedClosedComplaint,
  seedComplaint,
  seedReopenedComplaint,
  sql,
} from './aa-shared-fixtures';

const PREFIX = 'S2A-';

const AA_DO = identityHeadersFor('aa_do_001', 'AA');
const AA_REVIEWER = identityHeadersFor('aa_reviewer_001', 'AA');

/** Parents seeded for this suite. Every number carries the session prefix so cleanup is scoped. */
const C_OWN_CLOSED = `${PREFIX}OWN-CLOSED`;
const C_OWN_REOPENED = `${PREFIX}OWN-REOPENED`;
const C_OWN_OPEN = `${PREFIX}OWN-OPEN`;
const C_OTHER_CLOSED = `${PREFIX}OTHER-CLOSED`;
const C_OFFICE_013 = `${PREFIX}OFFICE-013`;
const C_OFFICE_014 = `${PREFIX}OFFICE-014`;
const C_UNMAPPED = `${PREFIX}UNMAPPED-CLAUSE`;
const C_REPRESENTATION = `${PREFIX}REPRESENTATION`;

const SEARCH = `${API_BASE}/api/v1/aa/parent-complaints/search`;

/** A distinctive name so a partial-name search cannot match unrelated live data. */
const UNIQUE_NAME = 'Zarnaaq Testfixture';

function esc(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
}

/** Sets columns the shared fixture does not cover (office, ground). Additive, per-session only. */
function setSearchColumns(
  complaintNumber: string,
  columns: { rbioOfficeCode?: string; groundOfComplaintId?: number | null; categoryId?: number }
): void {
  const sets: string[] = [];
  if (columns.rbioOfficeCode !== undefined) {
    sets.push(`rbio_office_code = '${esc(columns.rbioOfficeCode)}'`);
  }
  if (columns.groundOfComplaintId !== undefined) {
    sets.push(
      `ground_of_complaint_id = ${columns.groundOfComplaintId === null ? 'NULL' : columns.groundOfComplaintId}`
    );
  }
  if (columns.categoryId !== undefined) {
    sets.push(`category_id = ${columns.categoryId}`);
  }
  if (sets.length === 0) return;
  sql(`UPDATE COMPLAINTS SET ${sets.join(', ')} WHERE complaint_number = '${esc(complaintNumber)}'`);
}

function firstGroundId(): number {
  const raw = sql(
    `SELECT id FROM GROUND_OF_COMPLAINT_MASTER WHERE scheme_version = 'RBIOS_2021' ORDER BY sort_order LIMIT 1`
  );
  return Number(raw.trim());
}

async function searchAs(
  request: APIRequestContext,
  headers: Record<string, string>,
  query: string
) {
  const res = await request.get(`${SEARCH}?${query}`, { headers, failOnStatusCode: false });
  return { status: res.status(), body: await res.json().catch(() => null) };
}

function numbersOf(body: any): string[] {
  return (body?.data?.results ?? []).map((r: any) => r.complaintNumber);
}

test.describe.serial('S2A — AA parent-complaint search and appeal registration', () => {
  let groundId: number;

  test.beforeAll(async ({ request }) => {
    // Pin the identity mapping. identityHeadersFor infers a role from the actor name, and a wrong
    // inference makes a guard rejection look like a broken assertion.
    expect(AA_DO['X-User-Roles']).toBe('AA_DO');
    expect(AA_REVIEWER['X-User-Roles']).toBe('AA_REVIEWER');

    await assertBackendReady(request, AA_DO);

    // Purge FIRST as well as last, so a crashed previous run cannot poison this one.
    purgeByPrefix(PREFIX);

    groundId = firstGroundId();
    expect(groundId, 'GROUND_OF_COMPLAINT_MASTER must be seeded by V36').toBeGreaterThan(0);

    seedClosedComplaint(C_OWN_CLOSED, CLAUSE.complainantOnly, RE_OWN_ENTITY);
    seedReopenedComplaint(C_OWN_REOPENED, CLAUSE.bothParties, RE_OWN_ENTITY);
    seedComplaint(C_OWN_OPEN, {
      closureClause: null,
      entityCode: RE_OWN_ENTITY,
      status: 'in_progress',
    });
    seedClosedComplaint(C_OTHER_CLOSED, CLAUSE.bothParties, RE_OTHER_ENTITY);
    seedClosedComplaint(C_UNMAPPED, CLAUSE.unmapped, RE_OWN_ENTITY);
    seedClosedComplaint(C_REPRESENTATION, CLAUSE.neither, RE_OWN_ENTITY);

    seedComplaint(C_OFFICE_013, {
      closureClause: CLAUSE.complainantOnly,
      entityCode: RE_OWN_ENTITY,
      status: 'closed',
      complainantName: UNIQUE_NAME,
      complainantEmail: 'zarnaaq.013@example.com',
      complainantPhone: '9700000013',
    });
    seedComplaint(C_OFFICE_014, {
      closureClause: CLAUSE.complainantOnly,
      entityCode: RE_OWN_ENTITY,
      status: 'closed',
      complainantName: UNIQUE_NAME,
      complainantEmail: 'zarnaaq.014@example.com',
      complainantPhone: '9700000014',
    });
    setSearchColumns(C_OFFICE_013, { rbioOfficeCode: '013', groundOfComplaintId: groundId, categoryId: 1 });
    setSearchColumns(C_OFFICE_014, { rbioOfficeCode: '014', groundOfComplaintId: null, categoryId: 2 });
  });

  test.afterAll(async () => {
    purgeByPrefix(PREFIX);
  });

  // ── Story 1: search by complaint number ───────────────────────────────────────

  test('story 1 — exact complaint number returns the single parent', async ({ request }) => {
    const { status, body } = await searchAs(request, AA_DO, `complaintNumber=${C_OWN_CLOSED}`);
    expect(status).toBe(200);
    expect(numbersOf(body)).toEqual([C_OWN_CLOSED]);
    expect(body.data.totalElements).toBe(1);
  });

  test('story 1 — an unknown complaint number yields a no-results translation key', async ({ request }) => {
    const { status, body } = await searchAs(request, AA_DO, `complaintNumber=${PREFIX}DOES-NOT-EXIST`);
    expect(status).toBe(200);
    expect(numbersOf(body)).toEqual([]);
    // A key, not an English literal — the message must be renderable in all ten locales.
    expect(body.data.emptyMessageKey).toBe('aa.search.no_results');
  });

  test('a filterless search is refused rather than returning the whole table', async ({ request }) => {
    const { status, body } = await searchAs(request, AA_DO, '');
    expect(status).toBe(400);
    expect(body.messageKey).toBe('aa.search.error_no_filter');
  });

  // ── Story 2: name / mobile / email ────────────────────────────────────────────

  test('story 2 — name is a PARTIAL match, mobile and email are EXACT', async ({ request }) => {
    // Partial name: a fragment matches both fixtures sharing the unique name.
    const partial = await searchAs(request, AA_DO, `appellantName=${encodeURIComponent('Zarnaaq')}`);
    expect(partial.status).toBe(200);
    expect(numbersOf(partial.body).sort()).toEqual([C_OFFICE_013, C_OFFICE_014].sort());

    // Exact mobile: returns only its own row.
    const mobile = await searchAs(request, AA_DO, 'appellantMobile=9700000013');
    expect(numbersOf(mobile.body)).toEqual([C_OFFICE_013]);

    // A mobile PREFIX must NOT match. Partial mobile search is a PII enumeration primitive: it would
    // let a caller harvest complainant numbers by walking prefixes.
    const prefixOnly = await searchAs(request, AA_DO, 'appellantMobile=97000000');
    expect(numbersOf(prefixOnly.body)).toEqual([]);

    // Exact email, case-insensitive.
    const email = await searchAs(request, AA_DO, 'appellantEmail=ZARNAAQ.014@example.com');
    expect(numbersOf(email.body)).toEqual([C_OFFICE_014]);

    // An email fragment must not match either.
    const emailFragment = await searchAs(request, AA_DO, 'appellantEmail=zarnaaq');
    expect(numbersOf(emailFragment.body)).toEqual([]);
  });

  test('story 2 — a one-character name term is rejected instead of scanning the table', async ({ request }) => {
    const { status, body } = await searchAs(request, AA_DO, 'appellantName=Z');
    expect(status).toBe(400);
    expect(body.messageKey).toBe('aa.search.error_term_too_short');
  });

  // ── Story 3: office / clause / category / ground, independent and combined ────

  test('story 3 — each filter works independently', async ({ request }) => {
    const byOffice = await searchAs(request, AA_DO, `rbioOfficeCode=013&appellantName=Zarnaaq`);
    expect(numbersOf(byOffice.body)).toEqual([C_OFFICE_013]);

    const byClause = await searchAs(
      request,
      AA_DO,
      `closureClause=${encodeURIComponent(CLAUSE.neither)}&appellantName=AA%20Fixture`
    );
    expect(numbersOf(byClause.body)).toContain(C_REPRESENTATION);

    const byCategory = await searchAs(request, AA_DO, 'categoryId=2&appellantName=Zarnaaq');
    expect(numbersOf(byCategory.body)).toEqual([C_OFFICE_014]);

    const byGround = await searchAs(request, AA_DO, `groundOfComplaintId=${groundId}&appellantName=Zarnaaq`);
    expect(numbersOf(byGround.body)).toEqual([C_OFFICE_013]);
  });

  test('story 3 — filters COMBINE as AND, and a contradictory combination returns nothing', async ({ request }) => {
    // Office 013 AND category 1 both describe C_OFFICE_013.
    const matching = await searchAs(request, AA_DO, 'rbioOfficeCode=013&categoryId=1');
    expect(numbersOf(matching.body)).toContain(C_OFFICE_013);

    // Office 013 AND category 2 describe different rows, so the AND must be empty. If the predicates
    // were OR-ed, this would return two rows — which is the bug this asserts against.
    const contradictory = await searchAs(request, AA_DO, 'rbioOfficeCode=013&categoryId=2');
    expect(numbersOf(contradictory.body)).toEqual([]);
  });

  // ── Story 4: AA_REVIEWER is identical to AA_DO ────────────────────────────────

  test('story 4 — AA_REVIEWER sees exactly what AA_DO sees', async ({ request }) => {
    const query = 'appellantName=Zarnaaq';
    const asDo = await searchAs(request, AA_DO, query);
    const asReviewer = await searchAs(request, AA_REVIEWER, query);

    expect(asReviewer.status).toBe(200);
    expect(numbersOf(asReviewer.body).sort()).toEqual(numbersOf(asDo.body).sort());
    expect(asReviewer.body.data.totalElements).toBe(asDo.body.data.totalElements);
  });

  // ── Story 5: PNO entity scoping, server-enforced ──────────────────────────────

  test('story 5 — a PNO JWT claim beats a conflicting X-Entity-Code header', async ({ request }) => {
    const token = await mintToken(request, 're_pno_001');
    const claims = decodeClaims(token);
    // Pin the fixture assumption: this test is meaningless if the token is not scoped to RE_OWN_ENTITY.
    expect(claims['entity_code'], 're_pno_001 must carry the entity_code claim').toBe(RE_OWN_ENTITY);

    // The header names ANOTHER entity and points at that entity's complaint. The claim must win.
    const spoofed = await request.get(`${SEARCH}?complaintNumber=${C_OTHER_CLOSED}`, {
      headers: { ...bearer(token), 'X-Entity-Code': RE_OTHER_ENTITY },
      failOnStatusCode: false,
    });
    expect(spoofed.status()).toBe(200);
    expect(numbersOf(await spoofed.json())).toEqual([]);

    // Its OWN entity's parent is still reachable, so the empty result above is scoping and not a
    // blanket denial.
    const own = await request.get(`${SEARCH}?complaintNumber=${C_OWN_CLOSED}`, {
      headers: bearer(token),
      failOnStatusCode: false,
    });
    expect(numbersOf(await own.json())).toEqual([C_OWN_CLOSED]);
  });

  test('story 5 — NEGATIVE CONTROL: the other entity IS reachable by a properly scoped caller', async ({ request }) => {
    // Without this, a backend that denied everything would pass the spoofing test above while
    // proving nothing at all.
    const { status, body } = await searchAs(request, AA_DO, `complaintNumber=${C_OTHER_CLOSED}`);
    expect(status).toBe(200);
    expect(numbersOf(body)).toEqual([C_OTHER_CLOSED]);
  });

  test('story 5 — a PNO sees only appeal-eligible parents (closed or reopened)', async ({ request }) => {
    const token = await mintToken(request, 're_pno_001');

    const closed = await request.get(`${SEARCH}?complaintNumber=${C_OWN_CLOSED}`, {
      headers: bearer(token), failOnStatusCode: false,
    });
    expect(numbersOf(await closed.json())).toEqual([C_OWN_CLOSED]);

    // Reopen is workflow_stage='REOPENED' with status back to in_progress, so it is NOT expressible
    // as a status filter — this row is the regression guard for that.
    const reopened = await request.get(`${SEARCH}?complaintNumber=${C_OWN_REOPENED}`, {
      headers: bearer(token), failOnStatusCode: false,
    });
    expect(numbersOf(await reopened.json())).toEqual([C_OWN_REOPENED]);

    // An open complaint has no closure to appeal.
    const open = await request.get(`${SEARCH}?complaintNumber=${C_OWN_OPEN}`, {
      headers: bearer(token), failOnStatusCode: false,
    });
    expect(numbersOf(await open.json())).toEqual([]);
  });

  // ── Story 6: eligibility flag drives the affordance ───────────────────────────

  test('story 6 — appealEligible is true for closed/reopened and false for open', async ({ request }) => {
    const closed = await searchAs(request, AA_DO, `complaintNumber=${C_OWN_CLOSED}`);
    expect(closed.body.data.results[0].appealEligible).toBe(true);

    const reopened = await searchAs(request, AA_DO, `complaintNumber=${C_OWN_REOPENED}`);
    expect(reopened.body.data.results[0].appealEligible).toBe(true);

    const open = await searchAs(request, AA_DO, `complaintNumber=${C_OWN_OPEN}`);
    expect(open.body.data.results[0].appealEligible).toBe(false);
  });

  // ── PII masking ───────────────────────────────────────────────────────────────

  test('PII is masked in search results, server-side', async ({ request }) => {
    const { body } = await searchAs(request, AA_DO, `complaintNumber=${C_OFFICE_013}`);
    const row = body.data.results[0];

    // The full values must never reach the wire.
    expect(row.complainantName).not.toBe(UNIQUE_NAME);
    expect(row.complainantPhone).not.toBe('9700000013');
    expect(row.complainantPhone).toMatch(/^\*+0013$/);
    expect(row.complainantEmail).not.toContain('zarnaaq.013');
    expect(row.piiMasked).toBe(true);
  });

  // ── Story 8/9/10: register-form autofill ──────────────────────────────────────

  test('stories 8-10 — autofill carries what exists and FLAGS what has no source', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/${C_OWN_CLOSED}/register-form`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    expect(res.status()).toBe(200);
    const data = (await res.json()).data;

    // Present in the parent, so carried over.
    expect(data.complainant.appellantName).toBeTruthy();
    expect(data.complainant.appellantEmail).toBeTruthy();

    // NO SOURCE anywhere in the schema. These must be present-but-null AND flagged — never defaulted,
    // and never borrowed from the representative's address, who is frequently a different person.
    //
    // The key must EXIST with a null value rather than be omitted: an absent key is indistinguishable
    // from a field the API forgot, whereas an explicit null is the server stating "no source". Hence
    // `in` plus toBeNull, and deliberately NOT `a ?? b` — null ?? undefined collapses to undefined and
    // would let an omitted key pass.
    const unresolved: string[] = data.unresolvedFields.map((u: any) => u.field);
    const complainantBlanks = ['appellantCity', 'appellantCountry', 'appellantPincode'];
    const entityBlanks = ['entityRegion', 'bsrIfscCode', 'cardNumber'];

    for (const field of complainantBlanks) {
      expect(field in data.complainant, `${field} must be present as an explicit null`).toBe(true);
      expect(data.complainant[field], `${field} must not be guessed`).toBeNull();
      expect(unresolved, `${field} must be reported unresolved`).toContain(field);
    }
    for (const field of entityBlanks) {
      expect(field in data.entity, `${field} must be present as an explicit null`).toBe(true);
      expect(data.entity[field], `${field} must not be guessed`).toBeNull();
      expect(unresolved, `${field} must be reported unresolved`).toContain(field);
    }

    // Every unresolved field explains itself with a translation key, not an English literal.
    for (const u of data.unresolvedFields) {
      expect(u.reasonKey).toMatch(/^aa\.register\.unresolved_/);
    }

    // Story 10: mode of receipt is server-derived and read-only.
    expect(data.modeOfReceipt).toBe('AA_MANUAL');
    expect(data.modeOfReceiptReadOnly).toBe(true);
  });

  test('story 6 — the register form reports an OPEN parent as not appealable', async ({ request }) => {
    const res = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/${C_OWN_OPEN}/register-form`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    const data = (await res.json()).data;
    expect(data.appealEligible).toBe(false);
    expect(data.appealEligibilityKey).toBe('aa.register.error_parent_not_appealable');
  });

  // ── Masters come from the database, not a hardcoded list ──────────────────────

  test('dropdown masters are served from their master tables', async ({ request }) => {
    const grounds = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/masters/grounds`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    expect(grounds.status()).toBe(200);
    const groundRows = (await grounds.json()).data;
    expect(groundRows.length).toBeGreaterThan(0);
    // Every row must expose a translation key, or the dropdown cannot be localised.
    for (const row of groundRows) {
      expect(row.labelKey).toMatch(/^aa\.ground\./);
    }

    const offices = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/masters/offices`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    expect((await offices.json()).data.length).toBeGreaterThan(0);

    const clauses = await request.get(
      `${API_BASE}/api/v1/aa/parent-complaints/masters/closure-clauses`,
      { headers: AA_DO, failOnStatusCode: false }
    );
    const clauseRows = (await clauses.json()).data;
    // Party-dependent appealability must be visible: 15(1)(a) complainant-only, 15(1)(b) both.
    const a = clauseRows.find((c: any) => c.clauseCode === CLAUSE.complainantOnly);
    const b = clauseRows.find((c: any) => c.clauseCode === CLAUSE.bothParties);
    expect(a.appealableByComplainant).toBe(true);
    expect(a.appealableByEntity).toBe(false);
    expect(b.appealableByComplainant).toBe(true);
    expect(b.appealableByEntity).toBe(true);
  });

  // ── i18n ──────────────────────────────────────────────────────────────────────

  test('every new user-facing key is genuinely translated in all ten locales', async ({ request }) => {
    // assertKeysTranslatedInAllLocales also proves the nine non-English values are not the English
    // string copied through — an unseeded key falls back to English, so a presence-only check would
    // pass on a wholly untranslated feature.
    await assertKeysTranslatedInAllLocales(request, [
      'aa.search.heading',
      'aa.search.no_results',
      'aa.search.error_no_filter',
      'aa.search.error_term_too_short',
      'aa.search.no_office_recorded',
      'aa.register.heading',
      'aa.register.error_mandatory_incomplete',
      'aa.register.error_parent_not_appealable',
      'aa.register.unresolved_no_source',
      'aa.register.unresolved_not_in_master',
      'aa.register.prompt_log_legal_case',
      'aa.register.button_retry',
      'aa.register.appellant_country',
      'aa.register.entity_region',
      'aa.register.bsr_ifsc_code',
      'aa.ground.atm_debit_card',
      'aa.ground.others',
      'aa.upload.error_file_too_large',
      'aa.upload.error_total_too_large',
      'aa.upload.error_too_many_files',
    ]);
  });
});
