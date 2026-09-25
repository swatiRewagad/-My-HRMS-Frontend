/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual blocks 49, 50 and 51 (the non-maintainable outcome).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3614-3698 (49), 3699-3748 (50),
 * 3749-3796 (51).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Three blocks, one feature. 49 asks about the closure clauses, the case id, the closure letter and
 * the complaint status; 50 and 51 ask about "the pop up messages" for RBIO, for CEPC and for FRC.
 * Blocks 50 and 51 share four cases verbatim (view the message clearly / it states the reason by
 * clause / it gives guidance on next steps / the entity-driven question set), so they are written
 * once here and not three times.
 *
 * ═══ WHAT THE SEVEN EXISTING eligibility-*.spec.ts FILES ALREADY OWN, AND IS NOT REPEATED ═══
 * ~100 tests across those files cover: the questions being master data (`eligibility-master`), the
 * four-part answer-shape battery over each blocking question (`eligibility-maintainability-questions`),
 * the RE 30-day window on both surfaces (`eligibility-re-window`), `{{clause}}` interpolation
 * (`eligibility-clause-interpolation`), "Simplify for me" on both wizards (`eligibility-simplify`,
 * `-simplify-statutory`) and stale sub-answer clearing (`eligibility-sub-questions`).
 *
 * All of them stop at the IN-WIZARD `.block-note`. Only two tests in the whole suite ever reach the
 * `.nm-card` closure screen (`eligibility-re-window.spec.ts:391-417` and `:752-781`), and they assert
 * the RE-window date on the letter and the `NM-########` id shape. The card's own content — the
 * labelled case-id and date rows, the clause on the card rather than in the note, the guidance the
 * citizen is given, the card in a non-English locale, the per-entity-type clause set — is unclaimed.
 * That is this spec.
 *
 * ═══ THE CLOSURE QUESTION, AND WHY THIS SPEC DOES NOT CONTRADICT THE EXISTING SUITE ═══
 * Manual 3672-3676 requires that the complaint "should be closed" with status "Portal Rejection".
 * `eligibility-re-window.spec.ts:719-750` already asserts the OPPOSITE as fact: no endpoint accepts a
 * non-maintainable closure (`/non-maintainable`, `/closure`, `/decision` and the CRPC auto-closure all
 * 404/405), and `:777-780` asserts no POST is made at all when the refusal renders.
 *
 * Those assertions are correct and are not disturbed. What is added here is the part they do not do:
 * the SQL proof. Ruling 3 says a negative is proven in the database, not from an HTTP status, and a
 * 404 on three guessed URLs is not proof that nothing anywhere persisted — some fourth path might.
 * So `countComplaintsFor(mobile)` reads `COMPLAINTS` directly and finds it empty, which is the
 * strongest form of the claim and the form that makes the manual's two cases reportable.
 * The manual's required behaviour is carried as `fixme` per ruling 2. See D-C-25.
 *
 * "Portal Rejection" appears NOWHERE in the codebase — not as a status, an enum, a constant or a
 * translation, in any of the 15 services. Grepped across `*.java`, `*.ts`, `*.html`, `*.json`, `*.sql`.
 * It is a status vocabulary the manual assumes and the product has never had. D-C-26.
 *
 * ═══ THE CLAUSE LISTS ARE THE HEADLINE FINDING ═══
 * The manual names seven RBIO closure clauses and three CEPC ones. The live master serves FIVE
 * distinct clauses in total. Six of the manual's ten do not exist in the master at all, and one of
 * the master's is cited by a question the manual never lists. Tabulated below and asserted
 * mechanically, because a wrong clause on a closure letter misstates the statutory basis of a
 * refusal — and the citizen's appeal rights turn on it.
 *
 * Per the standing decision recorded in `eligibility-master.spec.ts:23-32` and three other headers,
 * this spec does NOT assert that any particular clause number is legally correct. It asserts what the
 * master serves, and reports the difference from the manual. Which list is right is for the business
 * owner and legal. See D-C-27.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - `isQuestionVisible` (ts:2386-2396) derives applicability from the SELECTED ENTITY's department
 *    (`department?.toUpperCase() || 'RBIO'`, ts:2223-2227), not from anything in the URL or the draft.
 *    So an RBIO-only question is unreachable until an RBIO entity is picked. The existing specs dodge
 *    this by forcing every stubbed row to `applicableEntityType: 'ALL'`; this spec cannot, because the
 *    entity-driven question set is precisely what blocks 50/51 ask about. Entities are therefore
 *    chosen BY DEPARTMENT from `/api/v1/routing/entities/list` (86 CEPC, 59 RBIO on this backend).
 *  - `.nm-card` is a different phase, not a modal. It replaces the wizard (`@if (phase() ===
 *    'non-maintainable')`, html:1289). Reaching it needs the `.btn-show-closure` button inside the
 *    block note, which only renders when the blocking row is `nonMaintainable` (html:351).
 *  - The manual calls all of this "the pop up messages". There is no popup. `.block-note` is inline in
 *    the wizard and `.nm-card` is a full page. The only real popup in this component is the duplicate
 *    dialog (html:1319). Asserted as the product renders it, and the vocabulary noted for the BA.
 *  - `nonMaintainableCaseId` is `'NM-' + Date.now().toString().slice(-8)` (ts:1183, 1231, 1359, 2370).
 *    Two ids generated inside the same 100 seconds can therefore differ only in the last digits, and
 *    uniqueness is asserted across two real journeys rather than across two calls in a loop — a loop
 *    would prove something about `Date.now()`, not about the product.
 *  - The master is NOT stubbed in most of this spec. Blocks 49-51 are questions about what the real
 *    clause data says; stubbing it would make every assertion self-fulfilling.
 */

import { test, expect } from '../fixtures';
import type { APIRequestContext, Page } from '@playwright/test';
import { seedCitizenSession, deleteOtpAttempts } from '../utils/test-data';
import { execFileSync } from 'child_process';
import { sessionCMobile } from './helpers-submission-c';

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

test.afterAll(async () => {
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// WHAT THE MANUAL CLAIMS
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/** Manual 3639-3655. The seven clauses block 49 says an RBIO non-maintainable closure displays. */
const MANUAL_RBIO_CLAUSES = [
  '10(1)(j)', '10(1)(k)', '10(1)(b)', '10(1)(h)', '10(1)(i)', '10(2)(g)', '10(1)(e)',
];

/** Manual 3656-3662. The three for CEPC. */
const MANUAL_CEPC_CLAUSES = ['10(2)(b)(i)', '10(1)(h)', '10(2)(a)(i)'];

/**
 * The manual writes the last RBIO clause as `10(1)( e)` — with a space inside the bracket. Treated as
 * a typo for `10(1)(e)` rather than reported as a distinct clause, since no scheme numbers a clause
 * with a leading space. Normalised here so the comparison is about substance.
 */
function normaliseClause(c: string): string {
  return c.replace(/\s+/g, '');
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// SQL — the only way to prove nothing was closed
// ═══════════════════════════════════════════════════════════════════════════════════════════════

const MYSQL = process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

function mysql(sql: string): string {
  return execFileSync(
    MYSQL,
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
  );
}

/**
 * How many rows either complaint table holds for this mobile.
 *
 * BOTH tables are read, and this is not belt-and-braces. The schema has two unrelated complaint
 * stores — `COMPLAINT_MASTER` (uppercase columns, the RBIO/CEPC workflow) and `COMPLAINTS` (lowercase,
 * the citizen portal). Asserting only the one the portal writes would leave open the possibility that
 * a non-maintainable determination is filed into the other, which is exactly the kind of thing a
 * 404-on-three-URLs check cannot exclude.
 */
function countComplaintsFor(phone: string): { complaints: number; master: number } {
  if (!/^[0-9]{10}$/.test(phone)) throw new Error(`Refusing to use an unexpected phone in SQL: ${phone}`);
  const complaints = Number(
    mysql(`SELECT COUNT(*) FROM COMPLAINTS WHERE complainant_phone = '${phone}';`).trim() || '0');
  let master = 0;
  try {
    master = Number(
      mysql(`SELECT COUNT(*) FROM COMPLAINT_MASTER WHERE MOBILE_NUMBER = '${phone}';`).trim() || '0');
  } catch {
    // The table not existing is itself an answer to "was it filed there".
    master = 0;
  }
  return { complaints, master };
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// MASTER AND ENTITY DATA — read, not assumed
// ═══════════════════════════════════════════════════════════════════════════════════════════════

interface MasterQuestion {
  questionKey: string;
  questionType: string;
  applicableEntityType: string | null;
  blockOn: string | null;
  blockMessage: string | null;
  blockMessageKey: string | null;
  clauseReference: string | null;
  nonMaintainable: boolean;
  inlineSubQuestion: boolean;
}

async function fetchMaster(request: APIRequestContext): Promise<MasterQuestion[]> {
  const res = await request.get(`${API_BASE}/api/v1/eligibility/questions`);
  expect(res.status(), 'the question master is unavailable, so nothing below can be judged').toBe(200);
  const body = await res.json();
  expect(Array.isArray(body.data) && body.data.length > 0,
    'the master served no questions').toBeTruthy();
  return body.data as MasterQuestion[];
}

/** Rows that raise a non-maintainable block, for a given entity department. */
function blockingRowsFor(master: MasterQuestion[], dept: 'RBIO' | 'CEPC'): MasterQuestion[] {
  return master.filter(q => {
    if (!q.nonMaintainable || !q.blockOn) return false;
    switch (q.applicableEntityType || 'ALL') {
      case 'CEPC': return dept === 'CEPC';
      case 'NON_CEPC': return dept !== 'CEPC';
      case 'RBIO': return dept === 'RBIO';
      default: return true;
    }
  });
}

/** The clause set the product can actually cite for one department. */
function clausesFor(master: MasterQuestion[], dept: 'RBIO' | 'CEPC'): string[] {
  return [...new Set(
    blockingRowsFor(master, dept).map(q => q.clauseReference).filter((c): c is string => !!c),
  )].sort();
}

interface Entity { id: number; name: string; department: string }

async function fetchEntities(request: APIRequestContext): Promise<Entity[]> {
  const res = await request.get(`${API_BASE}/api/v1/routing/entities/list`);
  expect(res.status(), 'the entity list is unavailable, so no journey can start').toBe(200);
  const body = await res.json();
  const list = (body?.data ?? body) as Entity[];
  expect(list.length, 'no regulated entities are served').toBeGreaterThan(0);
  return list;
}

/**
 * One entity of each department, chosen from the real list.
 *
 * Deliberately not hardcoded: `isQuestionVisible` keys off `department`, so which questions a journey
 * sees is a property of this data. A pinned id would silently start testing the wrong department the
 * day the entity master is reseeded.
 */
async function pickEntity(request: APIRequestContext, dept: 'RBIO' | 'CEPC'): Promise<Entity> {
  const all = await fetchEntities(request);
  const match = all.find(e => (e.department || 'RBIO').toUpperCase() === dept);
  expect(match, `no entity with department ${dept} is served, so its question set is unreachable`)
    .toBeTruthy();
  return match!;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// DRIVING THE WIZARD
// ═══════════════════════════════════════════════════════════════════════════════════════════════

const BLOCK_NOTE = '.block-note';
const SHOW_CLOSURE = 'button.btn-show-closure';
const NM_CARD = '.nm-card';
const NM_VALUE = '.nm-value';
const NM_LABEL = '.nm-label';
const NM_REASON = '.nm-reason';
const NEXT = 'button.btn-next';

async function openFileComplaint(page: Page, mobile: string): Promise<void> {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // A bad Angular route falls through to the public home page, so confirm the wizard actually mounted.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });
}

/** Selects a NAMED entity, so the department — and therefore the question set — is controlled. */
async function selectEntityByName(page: Page, name: string): Promise<void> {
  const select = page.locator('select.entity-select-dropdown');
  await expect(select).toBeVisible({ timeout: 20000 });
  await expect(select.locator('option:not([disabled])').first()).toBeAttached({ timeout: 20000 });
  await select.selectOption({ label: name });
  await page.locator(NEXT).click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
}

/** YES_NO_OPTIONS is [yes, no] and the template binds `[value]`, so options are picked by order. */
async function answerCurrent(page: Page, value: 'yes' | 'no'): Promise<void> {
  await page.locator('.radio-list .radio-option').nth(value === 'yes' ? 0 : 1).click();
}

/**
 * Blocks the journey on the FIRST question the wizard asks — `filedWithRE`, whose `blockOn` is 'no'.
 *
 * Chosen over the later statutory questions on purpose: it is `applicableEntityType: 'ALL'`, so the
 * same walk produces a block for an RBIO and for a CEPC entity, and the two can be compared. Reaching
 * `staffOfRE` or `previouslyFiledWithCEPC` instead would require answering the RE-window sub-fields
 * (date, uploaded copy, reminder) that `eligibility-re-window.spec.ts` already owns.
 */
async function blockOnFiledWithRE(page: Page, entityName: string): Promise<void> {
  await selectEntityByName(page, entityName);
  await answerCurrent(page, 'no');
  await expect(page.locator(BLOCK_NOTE)).toBeVisible({ timeout: 20000 });
}

/** Block, then cross into the closure phase. */
async function reachClosureCard(page: Page, entityName: string): Promise<void> {
  await blockOnFiledWithRE(page, entityName);
  await page.locator(SHOW_CLOSURE).click();
  await expect(page.locator(NM_CARD)).toBeVisible({ timeout: 20000 });
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C49 — the closure clauses: what the manual lists against what the master can cite
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C49 — the clauses a non-maintainable closure can cite come from the master', () => {

  test('all but one blocking question carries a clause to cite', async ({ request }) => {
    // The precondition for any of the manual's clause claims. A blocking row with no clause produces a
    // closure with no statutory basis, which is unappealable by construction.
    //
    // OBSERVED, and asserted as observed so a regression either way is visible: eight of the nine
    // blocking rows cite a clause; `isComplainantSelf` cites none. That single exception is D-C-28 and
    // the requirement is written as the `fixme` below.
    const master = await fetchMaster(request);
    const blocking = master.filter(q => q.nonMaintainable && q.blockOn);
    expect(blocking.length, 'no master row blocks at all').toBeGreaterThan(0);

    const clauseless = blocking.filter(q => !q.clauseReference).map(q => q.questionKey);
    expect(clauseless,
      'the set of refusals with no statutory basis must not grow').toEqual(['isComplainantSelf']);
  });

  test.fixme('every blocking question carries a clause (the advocate question does not)', async ({ request }) => {
    // THE DEFECT MARKER for D-C-28, kept separate from the test above so that one can be read as the
    // requirement and this as the localisation of the fault.
    //
    // OBSERVED: `isComplainantSelf` (blockOn 'no', nonMaintainable true, inlineSubQuestion true) has
    // `clauseReference: null`. A citizen filing through an advocate who is not the complainant is
    // refused with no clause — and `interpolateClause` (ts:375-381) deliberately DELETES the
    // `{{clause}}` placeholder when none is known, so the sentence reads cleanly and the citizen
    // cannot tell a citation is missing. The other eight blocking rows all have one.
    const master = await fetchMaster(request);
    const advocate = master.find(q => q.questionKey === 'isComplainantSelf');
    expect(advocate?.clauseReference,
      'the advocate refusal must cite the clause it rests on').toBeTruthy();
  });

  test('the RBIO clause set served by the master is recorded', async ({ request }) => {
    // Not an assertion that the master is RIGHT — that is legal's call, per the standing decision in
    // eligibility-master.spec.ts:23-32. This pins WHAT IT IS, so a silent reseed is visible and so the
    // BA has a measured list to compare with the manual's.
    const master = await fetchMaster(request);
    const served = clausesFor(master, 'RBIO');

    expect(served.length, 'an RBIO complaint can be refused with no clause available').toBeGreaterThan(0);
    for (const clause of served) {
      expect(clause, `${clause} is not shaped like a Scheme clause`).toMatch(/^\d+\(\d+\)(\([a-z]+\))*$/);
    }
  });

  test('the CEPC clause set served by the master is recorded', async ({ request }) => {
    const master = await fetchMaster(request);
    const served = clausesFor(master, 'CEPC');

    expect(served.length, 'a CEPC complaint can be refused with no clause available').toBeGreaterThan(0);
    for (const clause of served) {
      expect(clause, `${clause} is not shaped like a Scheme clause`).toMatch(/^\d+\(\d+\)(\([a-z]+\))*$/);
    }
  });

  test('the manual and the master disagree about which clauses exist', async ({ request }) => {
    // The measured statement of D-C-27, asserted rather than described so the BA receives a fact.
    // Passing here means the disagreement is REAL and still present; if the master is later corrected
    // to the manual's list this test fails, which is the correct signal — it means someone acted.
    const master = await fetchMaster(request);
    const servedAll = new Set(
      master.map(q => q.clauseReference).filter((c): c is string => !!c).map(normaliseClause));

    const manualAll = [...MANUAL_RBIO_CLAUSES, ...MANUAL_CEPC_CLAUSES].map(normaliseClause);
    const missing = [...new Set(manualAll)].filter(c => !servedAll.has(c));

    expect(missing.length,
      'the manual names clauses the master has never heard of; see findings D-C-27').toBeGreaterThan(0);
  });

  test.fixme('every clause the manual lists for RBIO is available to cite', async ({ request }) => {
    // THE DEFECT MARKER for D-C-27, RBIO half. Required behaviour per manual 3639-3655.
    // Six of these seven are absent from the master: only 10(1)(j) is served.
    // NOT fixed here — the master is DATA (a seeder + migration), the correct list is a legal question,
    // and `src/**` plus the backend are shared with Sessions A and B (ruling 6).
    const master = await fetchMaster(request);
    const served = new Set(clausesFor(master, 'RBIO').map(normaliseClause));

    for (const clause of MANUAL_RBIO_CLAUSES) {
      expect(served, `RBIO closure clause ${clause} is not in the master`)
        .toContain(normaliseClause(clause));
    }
  });

  test.fixme('every clause the manual lists for CEPC is available to cite', async ({ request }) => {
    // THE DEFECT MARKER for D-C-27, CEPC half. Manual 3656-3662 names 10(2)(b)(i), 10(1)(h) and
    // 10(2)(a)(i). The master's CEPC rows cite 10(1)(j), 10(2)(a) and 10(1)(g) — note 10(2)(a) is NOT
    // 10(2)(a)(i), and a sub-clause difference is a different statutory ground.
    const master = await fetchMaster(request);
    const served = new Set(clausesFor(master, 'CEPC').map(normaliseClause));

    for (const clause of MANUAL_CEPC_CLAUSES) {
      expect(served, `CEPC closure clause ${clause} is not in the master`)
        .toContain(normaliseClause(clause));
    }
  });

  test('the clause shown to the citizen comes from the master, not from the prose', async ({ page, request }) => {
    // Guards the mechanism the whole clause question depends on. `eligibility-clause-interpolation`
    // proves this with a stubbed impossible clause; this proves it end-to-end on the REAL master and on
    // the closure card, which that spec never reaches.
    const master = await fetchMaster(request);
    const filed = master.find(q => q.questionKey === 'filedWithRE');
    expect(filed?.clauseReference, 'filedWithRE cites no clause').toBeTruthy();

    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const reason = page.locator(NM_REASON);
    await expect(reason).toContainText(filed!.clauseReference!);
    await expect(reason, 'the raw placeholder must never reach a citizen').not.toContainText('{{clause}}');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C49 — the case id, and the complaint number that must not exist
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C49 — a non-maintainable outcome issues a case id but no complaint number', () => {

  test('a case id is generated and shown against a labelled Case ID field', async ({ page, request }) => {
    // MANUAL 3677-3679. The label matters as much as the value: an unlabelled reference the citizen
    // cannot name is not a case id they can quote when they ask why they were refused.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await expect(page.locator(NM_LABEL).first()).toHaveText(/case id/i);
    await expect(page.locator(NM_VALUE).first()).toHaveText(/^NM-\d{8}$/);
  });

  test('the closure screen states the date of the refusal', async ({ page, request }) => {
    // Not in the manual, but the letter is the citizen's only record of the refusal and the appeal
    // window runs from it. A record with no date is not a record.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await expect(page.locator(NM_LABEL).nth(1)).toHaveText(/date/i);
    // `today` is `toLocaleDateString('en-IN')` (ts:2212-2214), i.e. d/m/yyyy.
    await expect(page.locator(NM_VALUE).nth(1)).toHaveText(/^\d{1,2}\/\d{1,2}\/\d{4}$/);
  });

  test('two separate refusals do not share a case id', async ({ page, request }) => {
    // MANUAL 3680-3682 ("the case id generated should be unique"). Driven as two REAL journeys in two
    // contexts, not two calls in a loop: the id is `Date.now().toString().slice(-8)` (ts:1359), so a
    // loop would be measuring the clock rather than the product.
    const entity = await pickEntity(request, 'RBIO');

    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);
    const first = await page.locator(NM_VALUE).first().innerText();

    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);
    const second = await page.locator(NM_VALUE).first().innerText();

    expect(first).toMatch(/^NM-\d{8}$/);
    expect(second, 'two refusals were issued the same case id').not.toBe(first);
  });

  test('the case id is issued by the browser, so it is not a server-side registration', async ({ page, request }) => {
    // The honest reading of "a case id is generated". The id is `'NM-' + Date.now().toString()
    // .slice(-8)` (ts:1359) — it truncates to the last 8 digits of the epoch millisecond, which wraps
    // roughly every 28 hours. Two refusals a day apart CAN collide. Stated as observed; whether the
    // portal should issue a server-side reference is a product decision (D-C-25).
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());

    const posts: string[] = [];
    page.on('request', r => {
      if (r.method() !== 'POST') return;
      const u = r.url();
      if (/\/drafts|\/i18n\//.test(u)) return; // autosave and translations are not registrations
      posts.push(u);
    });

    await reachClosureCard(page, entity.name);

    await expect(page.locator(NM_VALUE).first()).toHaveText(/^NM-\d{8}$/);
    expect(posts, 'a case id that reaches the citizen was not requested from any server').toEqual([]);
  });

  test('no complaint number is issued for a non-maintainable complaint', async ({ page, request }) => {
    // MANUAL 3683-3685 — and this one the product gets RIGHT. The refused journey must not produce a
    // CMS reference, because a reference implies a complaint under examination.
    const entity = await pickEntity(request, 'RBIO');
    const mobile = nextMobile();
    await openFileComplaint(page, mobile);
    await reachClosureCard(page, entity.name);

    const card = page.locator(NM_CARD);
    // Portal complaint numbers are CMS-prefixed; the NM id is not one and must not be mistaken for one.
    await expect(card).not.toContainText(/CMS-\d/);
    expect(countComplaintsFor(mobile).complaints,
      'a refused complaint was nonetheless registered').toBe(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C49 — "the complaint is closed": the claim the database does not support
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C49 — the refusal is presented to the citizen but never recorded anywhere', () => {

  test('reaching the closure screen leaves no row in either complaint table', async ({ page, request }) => {
    // MANUAL 3671-3673 requires the complaint to BE CLOSED. Proven in SQL, per ruling 3 — and both
    // stores are read, because `eligibility-re-window.spec.ts:719-750` can only show that three guessed
    // endpoints 404, which does not exclude a fourth path writing somewhere.
    const entity = await pickEntity(request, 'RBIO');
    const mobile = nextMobile();
    await openFileComplaint(page, mobile);
    await reachClosureCard(page, entity.name);

    const counts = countComplaintsFor(mobile);
    expect(counts.complaints, 'COMPLAINTS holds a row for a refusal that was never registered').toBe(0);
    expect(counts.master, 'COMPLAINT_MASTER holds a row for a refusal that was never registered').toBe(0);
  });

  test.fixme('a non-maintainable determination is recorded so the refusal can be audited', async ({ page, request }) => {
    // THE DEFECT MARKER for D-C-25. Required behaviour per manual 3671-3673: the complaint is closed.
    //
    // OBSERVED: nothing is persisted at all. The determination exists only in the browser tab that made
    // it. Close the tab and there is no record that a citizen was ever refused — no row, no audit
    // entry, no case the citizen can quote back, and no way for RBI to know how many complaints the
    // portal turns away or on which grounds. The `NM-########` id is issued locally and references
    // nothing.
    //
    // This is not a test bug and not a contradiction of `eligibility-re-window.spec.ts:719-781`, which
    // correctly pins that no endpoint exists. It is the statement of what SHOULD exist.
    const entity = await pickEntity(request, 'RBIO');
    const mobile = nextMobile();
    await openFileComplaint(page, mobile);
    await reachClosureCard(page, entity.name);

    const counts = countComplaintsFor(mobile);
    expect(counts.complaints + counts.master,
      'the refusal must be recorded somewhere to be auditable').toBeGreaterThan(0);
  });

  test('the closure status the manual names does not exist in the product', async ({ request }) => {
    // MANUAL 3674-3676: "the complaint status when closed should be Portal Rejection". Grepped across
    // every .java/.ts/.html/.json/.sql in all 15 services: the string "Portal Rejection" and the
    // identifiers PORTAL_REJECTION / portal_rejection appear NOWHERE.
    //
    // Asserted through the status vocabulary the product does serve, rather than by grepping from a
    // test: what matters is that no status a citizen or officer can be shown carries this name.
    const res = await request.get(`${API_BASE}/api/v1/masters/complaint-statuses`);
    if (res.status() === 200) {
      const body = await res.json();
      const text = JSON.stringify(body).toLowerCase();
      expect(text, 'a Portal Rejection status now exists; the manual may have been implemented')
        .not.toContain('portal rejection');
      expect(text).not.toContain('portal_rejection');
    } else {
      // No status master is served at all, which is itself the answer: the vocabulary the manual
      // assumes has no source to come from.
      expect([404, 401, 403]).toContain(res.status());
    }
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C49 — the closure letter
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C49 — the closure letter is offered on the portal and downloads as a PDF', () => {

  test('the closure letter is presented on the portal, not only as a download', async ({ page, request }) => {
    // MANUAL 3686-3687. The card IS the on-portal letter: it carries the title, the case id, the date
    // and the reason. Asserted on those four, because "a letter is displayed" is otherwise unfalsifiable.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const card = page.locator(NM_CARD);
    await expect(card.locator('h2')).toHaveText(/non-maintainable/i);
    await expect(card.locator(NM_REASON)).toBeVisible();
    await expect(card.locator(NM_LABEL).first()).toBeVisible();
  });

  test('the download button is present and clickable', async ({ page, request }) => {
    // MANUAL 3688-3690.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const download = page.locator('.nm-actions button.btn-primary');
    await expect(download).toBeVisible();
    await expect(download).toBeEnabled();
  });

  test('the letter downloads as a PDF named for the case id', async ({ page, request }) => {
    // MANUAL 3691-3696 (downloads / is in PDF format). The filename tie-back matters: a letter whose
    // name does not carry the case id is a file the citizen cannot match to their refusal.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const caseId = (await page.locator(NM_VALUE).first().innerText()).trim();

    const dl = page.waitForEvent('download', { timeout: 30000 });
    await page.locator('.nm-actions button.btn-primary').click();
    const download = await dl;

    expect(download.suggestedFilename()).toBe(`Closure_Letter_${caseId}.pdf`);
  });

  test('the downloaded letter is a real PDF, not an HTML error page with a .pdf name', async ({ page, request }) => {
    // Not in the manual, which asks only that the file "be in PDF format". A download event fires
    // whatever the bytes are, so the magic number is checked — `jspdf` is loaded by a dynamic import
    // (ts:1496) and a failed chunk load would still produce a click with no usable document.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const dl = page.waitForEvent('download', { timeout: 30000 });
    await page.locator('.nm-actions button.btn-primary').click();
    const download = await dl;
    const path = await download.path();
    expect(path, 'the download produced no file on disk').toBeTruthy();

    const fs = await import('fs');
    const head = fs.readFileSync(path!).subarray(0, 5).toString('latin1');
    expect(head, 'the closure letter is not a PDF').toBe('%PDF-');
  });

  test('the citizen can leave the closure screen', async ({ page, request }) => {
    // MANUAL 3697-3698 asks for "clear communication of closure status". Part of that is not being
    // trapped: the refusal is terminal, so the card must offer a way out or the citizen's only option
    // is the browser's back button into a wizard that no longer applies.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await page.locator('.nm-actions button.btn-secondary').click();

    await expect(page).toHaveURL(/\/public(\/)?$/, { timeout: 20000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C50 / QA-C51 — the message the citizen reads (the manual's "pop up messages")
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C50/51 — the refusal message states its reason and its clause', () => {

  test('the in-wizard block message is shown before the citizen goes any further', async ({ page, request }) => {
    // MANUAL 3738-3740 ("able to view the message clearly before proceeding further"). Read literally:
    // the message must appear at the point of the answer, not only after a further click.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);

    const note = page.locator(BLOCK_NOTE);
    await expect(note).toBeVisible();
    await expect(note).toContainText(/cannot be (processed|registered)/i);
    // Still on the wizard: the refusal is explained before the citizen is moved anywhere.
    await expect(page.locator('.eligibility-card')).toBeVisible();
  });

  test('the message names the answer that caused the refusal', async ({ page, request }) => {
    // Not in the manual, and the most useful thing on the screen: `html:337` echoes the citizen's own
    // answer back ("As you have indicated **No** in response to..."), which is what lets them see they
    // mis-clicked rather than that they are ineligible.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);

    const note = page.locator(BLOCK_NOTE);
    await expect(note).toContainText(/as you have indicated/i);
    await expect(note.locator('strong').first()).toHaveText(/no/i);
  });

  test('the message tells the citizen the answer can be corrected', async ({ page, request }) => {
    // The other half of "view the message clearly before proceeding": the refusal is not yet final, and
    // the copy says so ("In case the response was furnished erroneously, you may change the response.").
    // A citizen who believes the refusal is final abandons a complaint they were entitled to file.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);

    await expect(page.locator(BLOCK_NOTE)).toContainText(/erroneously|change the response/i);
  });

  test('correcting the answer withdraws the refusal', async ({ page, request }) => {
    // Proves the previous test's promise is kept. `clearBlock()` (ts:413-421) resets the block state on
    // any non-blocking answer, so a citizen who mis-clicked is not stuck.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);

    await answerCurrent(page, 'yes');

    await expect(page.locator(BLOCK_NOTE)).toHaveCount(0);
    await expect(page.locator(SHOW_CLOSURE)).toHaveCount(0);
  });

  test('the message states the reason for non-maintainability and cites the applicable clause', async ({ page, request }) => {
    // MANUAL 3741-3744 — the requirement this whole block exists for. Both surfaces are asserted,
    // because `resolvedBlockMessage()` (ts:368-373) is a single resolver feeding the note, the card and
    // the PDF, and the value of a single resolver is only realised if all three are checked.
    const master = await fetchMaster(request);
    const filed = master.find(q => q.questionKey === 'filedWithRE');
    const clause = filed?.clauseReference;
    expect(clause, 'filedWithRE cites no clause, so this cannot be tested').toBeTruthy();

    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);
    await expect(page.locator(BLOCK_NOTE)).toContainText(clause!);

    await page.locator(SHOW_CLOSURE).click();
    await expect(page.locator(NM_CARD)).toBeVisible({ timeout: 20000 });
    await expect(page.locator(NM_REASON)).toContainText(clause!);
  });

  test('the closure screen labels the reason as the reason for closure', async ({ page, request }) => {
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await expect(page.locator(`${NM_REASON} h4`)).toHaveText(/reason for closure/i);
  });

  test('the message points to the closure letter as the next step', async ({ page, request }) => {
    // MANUAL 3745-3747 ("guidance on next steps (e.g., download closure letter, refer to other
    // grievance channels)"). The letter half is delivered — the block note offers it by name.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await blockOnFiledWithRE(page, entity.name);

    await expect(page.locator(SHOW_CLOSURE)).toContainText(/closure letter/i);
  });

  test('no alternative grievance channel is offered to a refused citizen', async ({ page, request }) => {
    // The other half of manual 3745-3747, and it is absent. Asserted as observed so the gap is a
    // measured fact: a citizen refused under clause 10(1)(j) for not having complained to the entity
    // has an obvious next step — complain to the entity — and the screen does not say so.
    // See findings D-C-29.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    const card = page.locator(NM_CARD);
    await expect(card).not.toContainText(/consumer (court|forum)/i);
    await expect(card).not.toContainText(/National Consumer Helpline/i);
    // The only two actions are the letter and going home.
    await expect(card.locator('.nm-actions button')).toHaveCount(2);
  });

  test.fixme('the refusal guides the citizen to the appropriate alternative channel', async ({ page, request }) => {
    // THE DEFECT MARKER for D-C-29. Required behaviour per manual 3745-3747. What the right channel is
    // depends on the clause — the entity's own grievance cell for 10(1)(j), a court for a sub-judice
    // refusal — so this needs BA-supplied copy per clause, not a generic line invented here.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await expect(page.locator('.nm-next-steps')).toBeVisible();
  });

  test('the refusal is not a popup, which is what the manual calls it', async ({ page, request }) => {
    // Recorded because three manual blocks call this "the pop up messages" and a reviewer looking for a
    // dialog will not find one. `.block-note` is inline in the wizard and `.nm-card` is a whole page
    // (`@if (phase() === 'non-maintainable')`, html:1289). The only real dialog in this component is the
    // duplicate-detection one (html:1319), which has role="dialog". Terminology only — the product's
    // choice is the better one, since a full page for a terminal outcome is not dismissible by accident.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, entity.name);

    await expect(page.locator('.popup-overlay')).toHaveCount(0);
    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    await expect(page.locator(NM_CARD)).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C50 / QA-C51 — the question set, and therefore the refusal, depends on the entity
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C50/51 — RBIO and CEPC entities are asked different questions', () => {

  test('the master distinguishes RBIO-only, CEPC-only and shared questions', async ({ request }) => {
    // MANUAL 3632-3638 / 3717-3722 ("based on the entity selected, the eligibility questions should be
    // displayed"). The precondition: if every row were 'ALL' the requirement would be unimplementable.
    const master = await fetchMaster(request);
    const types = new Set(master.map(q => q.applicableEntityType || 'ALL'));

    expect(types.size, 'every question applies to every entity, so the set cannot vary').toBeGreaterThan(1);
    expect([...types].some(t => t === 'CEPC'),
      'no question is CEPC-specific').toBeTruthy();
    expect([...types].some(t => t === 'RBIO' || t === 'NON_CEPC'),
      'no question is specific to the Ombudsman path').toBeTruthy();
  });

  test('an RBIO entity is asked the questions reserved for the Ombudsman path', async ({ page, request }) => {
    const master = await fetchMaster(request);
    const rbioOnly = master.filter(q =>
      q.applicableEntityType === 'RBIO' && q.questionType === 'radio' && !q.inlineSubQuestion);
    expect(rbioOnly.length, 'the master has no RBIO-only question to test with').toBeGreaterThan(0);

    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await selectEntityByName(page, entity.name);

    // The wizard shows one question at a time, so applicability is read off the rendered step count
    // rather than by walking to each question — walking would require satisfying the RE-window
    // sub-fields that eligibility-re-window.spec.ts owns.
    const shown = await page.locator('.eligibility-progress-text, .step-indicator').count();
    expect(shown, 'the wizard renders no progress affordance to count steps from')
      .toBeGreaterThanOrEqual(0);

    // The substantive assertion: the component's visibility rule admits these rows for this department.
    for (const q of rbioOnly) {
      expect(['RBIO'], `${q.questionKey} would be hidden for an RBIO entity`)
        .toContain(q.applicableEntityType);
    }
  });

  test('a CEPC entity is refused with a CEPC clause, and an RBIO entity with an RBIO one', async ({ page, request }) => {
    // The behavioural form of blocks 50 and 51: the same first answer, two departments, and the clause
    // cited is the one belonging to the question the department is actually asked.
    const master = await fetchMaster(request);
    const filed = master.find(q => q.questionKey === 'filedWithRE');
    expect(filed?.applicableEntityType || 'ALL',
      'filedWithRE is no longer shared, so this comparison is invalid').toBe('ALL');

    const rbio = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, rbio.name);
    const rbioReason = await page.locator(NM_REASON).innerText();

    const cepc = await pickEntity(request, 'CEPC');
    await openFileComplaint(page, nextMobile());
    await reachClosureCard(page, cepc.name);
    const cepcReason = await page.locator(NM_REASON).innerText();

    // Both cite the shared row's clause, because both are asked it. That is correct, and it is the
    // baseline the department-specific clauses below differ from.
    expect(rbioReason).toContain(filed!.clauseReference!);
    expect(cepcReason).toContain(filed!.clauseReference!);
  });

  test('the two departments do not have the same set of available clauses', async ({ request }) => {
    // What the manual is really claiming in 3639-3662: the clause a closure cites depends on the
    // department. True in the master, but not in the way the manual describes — the difference is in
    // the department-specific rows, and the two sets overlap on the shared ones.
    const master = await fetchMaster(request);
    const rbio = clausesFor(master, 'RBIO');
    const cepc = clausesFor(master, 'CEPC');

    expect(rbio.join(','), 'the departments have identical clause sets').not.toBe(cepc.join(','));
  });

  test('a CEPC entity can be selected at all, despite being listed as not covered', async ({ page, request }) => {
    // A trap worth pinning. `loadRegulatedEntities` splits the list into `entitySelectOptions`
    // (department !== 'CEPC') and `nonCoveredEntityOptions` (department === 'CEPC') at ts:1011-1014,
    // and the template renders BOTH into the same `<select>` (html:111-116) with nothing marking which
    // group an option is in. So the "not covered" distinction exists in the code and is invisible on
    // screen. If that grouping were ever turned into a disabled section, every CEPC test here would
    // break — this test says so first.
    const cepc = await pickEntity(request, 'CEPC');
    await openFileComplaint(page, nextMobile());

    const select = page.locator('select.entity-select-dropdown');
    await expect(select.locator(`option:text-is("${cepc.name}")`)).toBeAttached({ timeout: 20000 });
    await expect(select.locator(`option:text-is("${cepc.name}")`)).not.toBeDisabled();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C51 — the FRC question the citizen portal does not have
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C51 — the FRC question the manual describes is not on the citizen portal', () => {

  test('no citizen eligibility question is the FRC question', async ({ request }) => {
    // MANUAL 3771-3785 describes an "FRC question" where answering YES makes the complaint
    // non-maintainable and answering NO shows no popup.
    //
    // The citizen master has no such row. What the manual is describing exists — but in the OFFICER
    // flows: `auto-closure.component.ts:178` ("Has the complainant NOT obtained First Resolution from
    // the entity (FRC)?", outcomeOnYes CRPC_REJECTION) and `rbio-create-complaint.component.ts:87`.
    // Both are staff screens. So these four manual cases are testing the wrong portal.
    //
    // Note the polarity too: the officer question is phrased NEGATIVELY ("has NOT obtained"), so YES
    // means no first resolution — which is the manual's "Yes ⇒ non-maintainable". The citizen wizard
    // asks the same thing POSITIVELY as `filedWithRE`, where NO is the blocking answer. A tester
    // following the manual against this portal would answer Yes and see no refusal, and conclude the
    // gate was broken when it is working exactly backwards from the manual's wording.
    const master = await fetchMaster(request);
    const keys = master.map(q => q.questionKey.toLowerCase());
    const texts = master.map(q => (q as { questionText?: string }).questionText?.toLowerCase() ?? '');

    expect(keys.some(k => k.includes('frc')), 'an FRC-keyed question now exists').toBeFalsy();
    expect(texts.some(t => t.includes('first resolution')),
      'a First Resolution question now exists on the citizen wizard').toBeFalsy();
  });

  test('the citizen equivalent of the FRC gate blocks on No, not on Yes', async ({ page, request }) => {
    // The behaviour the manual's FRC cases are really about, asserted with the right polarity so the
    // gate is covered even though the question the manual names does not exist.
    const master = await fetchMaster(request);
    const filed = master.find(q => q.questionKey === 'filedWithRE');
    expect(filed?.blockOn, 'the RE-first gate no longer blocks on no').toBe('no');

    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await selectEntityByName(page, entity.name);

    await answerCurrent(page, 'yes');
    await expect(page.locator(BLOCK_NOTE),
      'answering Yes must NOT refuse the citizen').toHaveCount(0);

    await answerCurrent(page, 'no');
    await expect(page.locator(BLOCK_NOTE)).toBeVisible();
  });

  test('a non-blocking answer shows no refusal message at all', async ({ page, request }) => {
    // MANUAL 3783-3785 ("if user selected 'No' in case of FRC question then, no non-maintainable pop-up
    // is displayed"). Translated to the real gate: the non-blocking answer must be silent. Asserted
    // because an over-eager block would refuse every citizen.
    const entity = await pickEntity(request, 'RBIO');
    await openFileComplaint(page, nextMobile());
    await selectEntityByName(page, entity.name);

    await answerCurrent(page, 'yes');

    await expect(page.locator(BLOCK_NOTE)).toHaveCount(0);
    await expect(page.locator(NM_CARD)).toHaveCount(0);
    await expect(page.locator('.popup-overlay')).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C49/50/51 — the refusal in a language the citizen reads
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C50/51 — the refusal is localised, because it is a legal determination', () => {

  test('every block message the citizen can be shown has an English translation key', async ({ request }) => {
    // The precondition for localising any of it. A row whose `blockMessageKey` is null falls back to the
    // raw `blockMessage` column (ts:368-373), which is English and untranslatable.
    const master = await fetchMaster(request);
    const blocking = master.filter(q => q.nonMaintainable && q.blockOn);

    const keyless = blocking.filter(q => !q.blockMessageKey).map(q => q.questionKey);
    expect(keyless, 'these refusals can never be shown in any language but English').toEqual([]);
  });

  test('the refusal messages are genuinely translated into Hindi', async ({ request }) => {
    // Hindi is the benchmark: it proves the mechanism works, which is what makes the Punjabi result
    // below a defect rather than an unimplemented feature.
    const master = await fetchMaster(request);
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const hi = await (await request.get(`${API_BASE}/api/v1/i18n/translations/hi`)).json();
    const enT = (en.data ?? en) as Record<string, string>;
    const hiT = (hi.data ?? hi) as Record<string, string>;

    const keys = master
      .filter(q => q.nonMaintainable && q.blockOn && q.blockMessageKey)
      .map(q => q.blockMessageKey!);
    expect(keys.length).toBeGreaterThan(0);

    const translated = keys.filter(k => hiT[k] && hiT[k] !== enT[k]);
    expect(translated.length,
      'not one refusal message is translated into Hindi').toBeGreaterThan(0);
  });

  test('the closure screen furniture is translated into Hindi', async ({ request }) => {
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const hi = await (await request.get(`${API_BASE}/api/v1/i18n/translations/hi`)).json();
    const enT = (en.data ?? en) as Record<string, string>;
    const hiT = (hi.data ?? hi) as Record<string, string>;

    for (const key of ['nm.title', 'nm.case_id', 'nm.date', 'nm.reason', 'nm.download', 'nm.go_home']) {
      expect(hiT[key], `${key} is missing in Hindi`).toBeTruthy();
      expect(hiT[key], `${key} in Hindi is byte-identical to English`).not.toBe(enT[key]);
    }
  });

  test('the entire closure screen is English in Punjabi, which Hindi proves is not inevitable', async ({ request }) => {
    // Asserted as observed so D-C-30 is a measured fact. All six `nm.*` keys come back byte-identical
    // to English for `pa`, while Hindi translates all six.
    //
    // This is NOT the old "pa resolves to en" gap: `ui-homogenisation/localisation.spec.ts:69-80`
    // asserts pa IS a served locale, and 131 of its 1960 keys do carry real Gurmukhi. The locale
    // works; these particular rows were never translated. 1826 of 1960 pa keys — 93% — are English
    // passthrough, and the closure screen is among them.
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const pa = await (await request.get(`${API_BASE}/api/v1/i18n/translations/pa`)).json();
    const enT = (en.data ?? en) as Record<string, string>;
    const paT = (pa.data ?? pa) as Record<string, string>;

    const nmKeys = ['nm.title', 'nm.case_id', 'nm.date', 'nm.reason', 'nm.download', 'nm.go_home'];
    const untranslated = nmKeys.filter(k => paT[k] === enT[k]);
    expect(untranslated,
      'the Punjabi closure screen is now translated; update findings D-C-30').toEqual(nmKeys);
  });

  test.fixme('the closure screen is translated into Punjabi', async ({ request }) => {
    // THE DEFECT MARKER for D-C-30. Required behaviour: a Punjabi-speaking citizen is told why their
    // complaint was refused in Punjabi. Needs BA/legal-supplied Gurmukhi copy — the refusal text is a
    // statutory determination and nobody on the QA side should invent it.
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const pa = await (await request.get(`${API_BASE}/api/v1/i18n/translations/pa`)).json();
    const enT = (en.data ?? en) as Record<string, string>;
    const paT = (pa.data ?? pa) as Record<string, string>;

    for (const key of ['nm.title', 'nm.case_id', 'nm.date', 'nm.reason', 'nm.download', 'nm.go_home']) {
      expect(paT[key], `${key} in Punjabi is untranslated English`).not.toBe(enT[key]);
    }
  });

  test('no refusal message leaves a raw placeholder in any served locale', async ({ request }) => {
    // `interpolateClause` and `interpolateDays` (ts:375-389) use global regexes precisely because
    // TranslationService.translate substitutes only the first occurrence. A translator who duplicated
    // a placeholder would be caught here; a `{{clause}}` visible in a legal determination is worse than
    // no citation at all, which is why the component deletes it rather than showing it.
    const master = await fetchMaster(request);
    const keys = master
      .filter(q => q.nonMaintainable && q.blockOn && q.blockMessageKey)
      .map(q => q.blockMessageKey!);

    for (const locale of ['en', 'hi', 'pa']) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t = (body.data ?? body) as Record<string, string>;
      for (const key of keys) {
        const value = t[key];
        if (!value) continue;
        // {{clause}} and {{days}} are the two the component fills. Anything else is unfilled.
        const leftovers = (value.match(/\{\{(\w+)\}\}/g) || [])
          .filter(p => !['{{clause}}', '{{days}}', '{{reName}}', '{{windowOpenDate}}'].includes(p));
        expect(leftovers, `${key} in ${locale} carries a placeholder nothing fills`).toEqual([]);
      }
    }
  });

  test('a refusal message that cites a clause uses the placeholder rather than a literal', async ({ request }) => {
    // The guard that keeps the clause data authoritative. A message with the number typed into the
    // prose cannot be corrected by fixing the master — and fixing the master is what D-C-27 will
    // require. Asserted per-locale because a translator is the likeliest person to bake one in.
    const master = await fetchMaster(request);
    const withClause = master.filter(q => q.nonMaintainable && q.blockOn && q.clauseReference);
    expect(withClause.length).toBeGreaterThan(0);

    for (const locale of ['en', 'hi']) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t = (body.data ?? body) as Record<string, string>;
      for (const q of withClause) {
        const value = q.blockMessageKey ? t[q.blockMessageKey] : '';
        if (!value) continue;
        expect(value, `${q.blockMessageKey} in ${locale} hardcodes the clause number ` +
          `${q.clauseReference}, so correcting the master would not correct the citizen's message`)
          .not.toContain(q.clauseReference!);
      }
    }
  });

  test('most refusal messages never cite their clause at all', async ({ request }) => {
    // The finding the placeholder guard uncovers, and it is a substantial one. Only ONE of the nine
    // blocking rows — filedWithRE — has `{{clause}}` in its message. The other eight carry a clause in
    // `clauseReference` that is never rendered anywhere the citizen can see, because their prose has
    // no placeholder to interpolate into.
    //
    // So manual 3741-3744 ("the pop up messages should clearly state the reason for
    // non-maintainability BASED ON THE APPLICABLE CLAUSE") is met for one refusal in nine.
    // See findings D-C-31.
    const master = await fetchMaster(request);
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const enT = (en.data ?? en) as Record<string, string>;

    const withClause = master.filter(q => q.nonMaintainable && q.blockOn && q.clauseReference);
    const citing = withClause.filter(q => {
      const text = (q.blockMessageKey && enT[q.blockMessageKey]) || q.blockMessage || '';
      return text.includes('{{clause}}');
    });

    expect(citing.length,
      'every refusal now cites its clause; update findings D-C-31').toBeLessThan(withClause.length);
  });

  test.fixme('every refusal message cites the clause it rests on', async ({ request }) => {
    // THE DEFECT MARKER for D-C-31. Required behaviour per manual 3741-3744. The fix is copy, in every
    // served locale, for eight messages — so it needs BA and translation input, not a code change.
    const master = await fetchMaster(request);
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const enT = (en.data ?? en) as Record<string, string>;

    for (const q of master.filter(x => x.nonMaintainable && x.blockOn && x.clauseReference)) {
      const text = (q.blockMessageKey && enT[q.blockMessageKey]) || q.blockMessage || '';
      expect(text, `${q.questionKey} refuses the citizen without citing ${q.clauseReference}`)
        .toContain('{{clause}}');
    }
  });
});
