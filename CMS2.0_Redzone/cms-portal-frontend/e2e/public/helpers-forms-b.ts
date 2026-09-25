/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 / SESSION B — shared navigation and field-validation helpers
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Session B automates ~180 manual cases covering the citizen wizard's contact fields, address,
 * pincode, RE-details cascades and complaint core fields. Roughly 100 of those cases are the SAME six
 * assertions applied to a different input, so they are driven from one table here instead of being
 * hand-written N times.
 *
 * ── THE STEP NUMBERS IN THE MANUAL PACK ARE WRONG, AND IT MATTERS ─────────────────────────────────
 * The QA pack (and the brief derived from it) describes Complainant Details / RE Details / Complaint
 * Details as steps 3 / 4 / 5 of a 7-step wizard. The running product does NOT work that way. The
 * eligibility questionnaire is a SEPARATE phase (`phase() === 'eligibility'`), not numbered steps, and
 * once it passes the form phase begins at step 1:
 *
 *     phase 'eligibility'  →  entity select + N maintainability questions   (NOT numbered steps)
 *     phase 'form' step 1  →  Complainant Details      (email, landline, pincode, state, district, address)
 *     phase 'form' step 2  →  Regulated Entity Details (credit-card radio, entity state/district/branch)
 *     phase 'form' step 3  →  Complaint Details        (category, facts, account radio + types)
 *     phase 'form' step 4  →  Authorised representative
 *     phase 'form' step 5  →  Declarations
 *     phase 'form' step 6  →  Review and Submit
 *
 * Every helper below is written against the REAL structure (file-complaint.component.html:468 step 1,
 * :606 step 2, :661 step 3). Recorded here because a reader holding the manual pack will otherwise
 * think these helpers are off by two.
 *
 * ── WHY SESSION SEEDING, NOT A REAL LOGIN ────────────────────────────────────────────────────────
 * publicAuthService only checks that `cms_public_session` exists and is inside the inactivity timeout;
 * the token signature is never validated (public-auth.service.ts:39-48). Driving a real OTP login for
 * every one of ~180 cases would cost the whole session AND hammer the shared OTP rate limiter that
 * sessions A and C also depend on. So we seed. `loginCitizen` is available for the rare test that
 * needs the server to authenticate the caller — none of Session B's field-validation cases do.
 *
 * ── THE ONE ORDERING CONSTRAINT THAT WILL WASTE YOUR EVENING IF YOU MISS IT ──────────────────────
 * sessionStorage is origin-scoped, so the page must already be ON the app origin before
 * seedCitizenSession writes to it. goto('/public') FIRST, seed, THEN goto the guarded route. Seeding
 * before the first navigation silently writes to about:blank and the route guard bounces you home —
 * which looks exactly like a broken auth guard.
 *
 * ── WHAT `maxlength` MEANS FOR HALF THE MANUAL CASES ─────────────────────────────────────────────
 * Address (maxlength=100), facts (5000), pincode (6) and the account-number fields are capped by the
 * BROWSER via the maxlength attribute, not by an Angular validator. A great many manual cases say
 * "should not accept more than N characters AND error message <X> should be displayed". Both halves
 * cannot be true at once: the browser truncates at N, so the over-length value never exists in the
 * model and no validator ever fires, so no message can be displayed. These helpers assert the
 * TRUNCATION (the real, observable behaviour) and every such case is logged as a ruling-2
 * contradiction in findings-QA-B.md rather than being bent to match the document.
 *
 * ── NO ROWS ARE SEEDED, NOTHING IS CLEANED UP, AND THAT IS DELIBERATE ────────────────────────────
 * Session B's scope is client-side field validation reached before submit. Nothing here POSTs a
 * complaint, writes SYSTEM_CONFIG or sends an OTP, so there is no shared state to restore and no
 * cross-session collision surface. Mobiles still come from Session B's reserved 98765_2____ range so
 * that anything that DOES reach the OTP tables cannot collide with sessions A or C.
 */

import { expect, Page } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

export const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
export const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

/** Session B's reserved mobile range — see the collision rules. Never hardcode a literal mobile. */
export function sessionBMobile(): string {
  return '987652' + Math.floor(1000 + Math.random() * 9000);
}

/**
 * The bundle's compiled apiBaseUrl is 8082. The `page` fixture already rewrites it, but specs that
 * register their own routes need this registered in beforeEach so per-test fault injection (which is
 * matched in REVERSE registration order) still wins.
 */
export async function redirectAppApi(page: Page) {
  if (API_BASE === APP_API_BASE) return;
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Getting into the wizard
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/** Seeds a citizen session and lands on the eligibility phase. */
export async function openWizard(page: Page, mobile = sessionBMobile()): Promise<string> {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // An unmatched Angular route falls through to the public home page, so prove the wizard mounted
  // rather than asserting against a page that merely looks empty.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });
  return mobile;
}

/**
 * Waits for the entity <select> to be usable, and names the reason when it never becomes usable.
 *
 * ── WHY THIS IS NOT A PLAIN toBeVisible WAIT ─────────────────────────────────────────────────────
 * The select only exists inside `@if (currentQuestion?.type === 'select')` (html:52-119), and
 * currentQuestion comes from the questions master. When GET /api/v1/eligibility/questions fails OR
 * returns zero rows, `questionsLoadFailed` is set (ts:943-978), there is no currentQuestion, and the
 * select is never rendered at all — while `.eligibility-card` (which openWizard waits for) stays
 * perfectly visible. There is deliberately NO hardcoded question fallback, because serving a stale
 * maintainability rule could wrongly deny a citizen the Scheme.
 *
 * A bare visibility wait therefore reports a 20s timeout on `select.entity-select-dropdown` for a
 * failure that has nothing to do with the select, landing on whichever test happened to run at that
 * moment. That is what produced "2 tests fail in the full run, 4 different ones fail on re-run, all 24
 * pass in isolation" — it reads as harness flake and is actually a master-data fetch failing under the
 * request load of a long suite.
 *
 * Both fail-closed notices carry a retry button that exists precisely so a transient failure is
 * recoverable, so this clicks it once before giving up, and then throws naming which master is down.
 */
async function waitForEntitySelect(page: Page) {
  const select = page.locator('select.entity-select-dropdown');
  const questionsFailed = page.locator('.questions-load-error');
  const entitiesFailed = page.locator('.entities-load-error');

  /**
   * Settles as soon as EITHER the select or the questions-failure notice appears, rather than waiting
   * out the full timeout on one of them.
   *
   * `isVisible({ timeout })` cannot be used for this: the option is documented as ignored — the call
   * returns immediately — so polling with it would report "not rendered" while Angular was still
   * mounting, reintroducing exactly the race this function exists to remove. `waitFor` does wait.
   */
  const raceSelectAgainstFailure = () => Promise.race([
    select.waitFor({ state: 'visible', timeout: 20000 }).then(() => 'select').catch(() => null),
    questionsFailed.waitFor({ state: 'visible', timeout: 20000 }).then(() => 'questions').catch(() => null),
  ]);

  /**
   * The two masters load independently, so the select appears as soon as the QUESTIONS request lands —
   * typically while the ENTITIES request is still in flight. Checking for the entities notice at that
   * instant therefore finds nothing even when the entity fetch is about to fail, and the failure lands
   * on the option wait instead. Racing the two settles on whichever actually happens.
   */
  const raceOptionsAgainstFailure = () => Promise.race([
    select.locator('option:not([disabled])').first()
      .waitFor({ state: 'attached', timeout: 20000 }).then(() => 'options').catch(() => null),
    entitiesFailed.waitFor({ state: 'visible', timeout: 20000 }).then(() => 'failed').catch(() => null),
  ]);

  for (let attempt = 0; attempt < 2; attempt++) {
    const outcome = await raceSelectAgainstFailure();

    if (outcome === 'select') {
      // The entity fetch can fail independently: the select still renders, holding only its disabled
      // placeholder. Report that as the master-data failure it is rather than as an option-wait timeout.
      if (await raceOptionsAgainstFailure() === 'options') return select;

      if (attempt === 0 && (await page.locator('.btn-retry-entities').isVisible().catch(() => false))) {
        await page.locator('.btn-retry-entities').click();
        await page.waitForTimeout(500);
        continue;
      }
      throw new Error(
        'ENTITY MASTER UNAVAILABLE: the select rendered but never gained a selectable option' +
        (await entitiesFailed.isVisible().catch(() => false)
          ? ', and the wizard is showing its entities_unavailable notice'
          : ', though no entities_unavailable notice was rendered either') +
        '. GET /api/v1/routing/entities/list is failing — this is not a harness timing problem.');
    }

    if (await questionsFailed.isVisible().catch(() => false)) {
      if (attempt === 0) {
        await page.locator('.btn-retry-questions').click();
        await page.waitForTimeout(500);
        continue;
      }
      throw new Error(
        'QUESTIONS MASTER UNAVAILABLE: GET /api/v1/eligibility/questions failed or returned zero rows, ' +
        'so no question is current and the entity select is never rendered. Retrying did not recover it. ' +
        'The eligibility card is visible, which is why this previously surfaced as an unrelated 20s ' +
        'timeout on select.entity-select-dropdown.');
    }

    throw new Error(
      'The entity select never appeared and NEITHER fail-closed notice was rendered. The eligibility ' +
      'card mounted but no question became current — the questions request is likely still in flight, ' +
      'or the component threw during init.');
  }

  return select;
}

/** Picks the first genuinely selectable entity from the native <select> and advances. */
export async function selectFirstEntityAndAdvance(page: Page) {
  // waitForEntitySelect already guarantees a selectable option, or throws naming the master that failed.
  const select = await waitForEntitySelect(page);
  const value = await select.locator('option:not([disabled])').first().getAttribute('value');
  await select.selectOption(value!);
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
}

/**
 * Fetches the regulated-entity master and returns one entity per department.
 *
 * Needed because the pack asserts RBIO-vs-other-department routing behaviour, and per ruling 1 the
 * entity IDs must come from the master rather than being pinned. `department` is the field the
 * component keys `selectedEntityType` off (component.ts:2223-2227), defaulting to RBIO when absent.
 */
export async function entityByDepartment(page: Page): Promise<Record<string, { id: string; name: string }>> {
  const res = await page.request.get(`${API_BASE}/api/v1/routing/entities/list`);
  const body = await res.json();
  const rows = (body?.data ?? body ?? []) as any[];
  const out: Record<string, { id: string; name: string }> = {};
  for (const row of rows) {
    const dept = (row.department || 'RBIO').toUpperCase();
    if (!out[dept]) out[dept] = { id: String(row.id), name: row.name };
  }
  return out;
}

/**
 * Seeds a session, selects a SPECIFIC entity by id, walks the gate and lands on step 1.
 *
 * `selectFirstEntityAndAdvance` is alphabetical-first, which is a CEPC row on this master — fine for
 * field validation, useless for proving department-dependent behaviour. This takes the id explicitly.
 */
export async function gotoComplainantDetailsWithEntity(page: Page, entityId: string, mobile?: string) {
  await openWizard(page, mobile);
  const select = await waitForEntitySelect(page);
  await select.selectOption(entityId);
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
  await walkQuestionnaire(page);
  await expect(page.locator('select[name="complainantCategory"]')).toBeVisible({ timeout: 20000 });
}

/** Lands on step 2 with the credit-card question answered 'no', so the RE cascade is rendered. */
export async function gotoReCascade(page: Page, mobile?: string) {
  await gotoReDetails(page, mobile);
  await page.locator('input[name="isCreditCardComplaint"][value="no"]').check();
  await expect(page.locator('select[name="entityState"]')).toBeVisible({ timeout: 20000 });
}

/** YES_NO_OPTIONS is [yes, no] and the template binds [value], so options are picked by order. */
export async function answerCurrentQuestion(page: Page, value: 'yes' | 'no') {
  await page.locator('.radio-list .radio-option').nth(value === 'yes' ? 0 : 1).click();
}

/**
 * Walks the maintainability questionnaire with the NON-BLOCKING answer to every question until the
 * form phase begins.
 *
 * Why it is written as a bounded loop rather than a fixed sequence: the questions are master data
 * from ELIGIBILITY_QUESTION_MASTER, so their number and order can change without a frontend release
 * (that was the entire point of D14). A fixed 5-step sequence would silently break the moment a row
 * is added. The loop answers 'no' — which is the non-blocking answer for every blocking question in
 * the master EXCEPT filedWithRE, where 'no' blocks and 'yes' demands a date plus a file upload. We
 * answer filedWithRE with 'no' too and let the block-detection below report it, because Session B's
 * scope begins AFTER the gate; if the master ever makes the gate unavoidable this throws loudly
 * instead of quietly testing the wrong screen.
 */
export async function passEligibility(page: Page) {
  await selectFirstEntityAndAdvance(page);
  await walkQuestionnaire(page);
}

/**
 * The questionnaire walk itself, with the entity already chosen and the first question on screen.
 *
 * Split out from passEligibility so callers that must select a SPECIFIC entity (to exercise
 * department-dependent behaviour) can reuse the walk without re-selecting the alphabetical-first row.
 */
export async function walkQuestionnaire(page: Page) {
  const filedWithReIso = isoDaysAgo(40);

  for (let guard = 0; guard < 25; guard++) {
    if (await page.locator('.step-content').first().isVisible().catch(() => false)) return;

    const questionText = (await page.locator('.question-text').first().textContent().catch(() => '')) || '';

    if (/complaint.*(with|to).*(bank|entity)|filed a written|electronic complaint/i.test(questionText)) {
      // The one question whose non-blocking answer is YES, and which then demands a date and a copy.
      await answerCurrentQuestion(page, 'yes');
      const dateInput = page.locator('input[name="bankComplaintDate"]');
      if (await dateInput.isVisible().catch(() => false)) {
        await dateInput.fill('');
        await dateInput.pressSequentially(isoToDisplay(filedWithReIso).replace(/\//g, ''));
      }
      const fileInput = page.locator('input#complaintFileWithRE');
      if (await fileInput.count()) {
        await fileInput.setInputFiles({
          name: 're-complaint.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 e2e'),
        });
      }
    } else {
      await answerCurrentQuestion(page, 'no');
    }

    if (await page.locator('.block-note').isVisible().catch(() => false)) {
      throw new Error(`Eligibility blocked while walking the gate, on question: ${questionText.trim()}`);
    }

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(250);
  }

  await expect(page.locator('.step-content').first()).toBeVisible({ timeout: 20000 });
}

/** Lands on step 1 — Complainant Details. */
export async function gotoComplainantDetails(page: Page, mobile?: string) {
  await openWizard(page, mobile);
  await passEligibility(page);
  await expect(page.locator('select[name="complainantCategory"]')).toBeVisible({ timeout: 20000 });
}

/**
 * Fills step 1 with the minimum the step-1 validator demands, then advances to step 2 — RE Details.
 *
 * The validator (file-complaint.component.ts:1652-1657) requires firstName, a 6-digit pincode, a
 * state (which ONLY the pincode lookup can populate — see the state findings), and an address.
 */
export async function fillComplainantMinimalAndAdvance(page: Page, opts: { pincode?: string } = {}) {
  await page.locator('select[name="complainantCategory"]').selectOption('individual');
  await page.locator('input[name="firstName"]').fill('Testy');
  await page.locator('input[name="lastName"]').fill('Citizen');
  await fillPincodeAndWaitForLookup(page, opts.pincode || '411001');
  await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
  await page.locator('button.btn-next').click();
  await expect(page.locator('input[name="isCreditCardComplaint"]').first()).toBeVisible({ timeout: 20000 });
}

/** Lands on step 2 — Regulated Entity Details. */
export async function gotoReDetails(page: Page, mobile?: string) {
  await gotoComplainantDetails(page, mobile);
  await fillComplainantMinimalAndAdvance(page);
}

/** Lands on step 3 — Complaint Details, taking the credit-card=yes branch to skip the RE cascade. */
export async function gotoComplaintDetails(page: Page, mobile?: string) {
  await gotoReDetails(page, mobile);
  await page.locator('input[name="isCreditCardComplaint"][value="yes"]').check();
  await page.locator('button.btn-next').click();
  await expect(page.locator('textarea[name="complaintText"]')).toBeVisible({ timeout: 20000 });
}

/**
 * Types a pincode and waits for the state/district derivation to settle.
 *
 * State and district are NOT independently selectable — both are derived from the pincode lookup
 * (onPincodeInput, component.ts:557-627), which tries GET /api/v1/location/pincode/{code} and falls
 * back to a compiled-in table. So "select a state" in the manual pack means "type a pincode and let
 * the product choose", and the <select> only ever holds the options that lookup returned.
 *
 * ── WHY THE BLANK-OUT IS ASSERTED BETWEEN THE TWO fill() CALLS ───────────────────────────────────
 * Waiting for "a state option is attached" is NOT sufficient when REPLACING one pincode with another:
 * the previous lookup's options are still in the DOM, so the wait is satisfied instantly and the test
 * then reads the OLD state while the new lookup is still in flight. That produced a failure that
 * appeared only in full-file runs and passed in isolation — a race in the harness, not a product bug.
 *
 * Clearing the field first drives onPincodeInput's empty branch, which resets both option lists
 * synchronously. Asserting the lists are empty before typing the new value means the subsequent
 * "options are attached" wait can only be satisfied by the NEW lookup.
 */
export async function fillPincodeAndWaitForLookup(page: Page, pincode: string) {
  const input = page.locator('input[name="pincode"]');
  const stateOptions = page.locator('select[name="state"] option:not([value=""])');

  await input.fill('');
  // The empty branch of onPincodeInput clears state/district and both option lists. Observing that
  // transition is what makes the wait below unambiguous.
  await expect(stateOptions, 'clearing the pincode must reset the derived state options')
    .toHaveCount(0, { timeout: 20000 });

  await input.fill(pincode);
  if (/^\d{6}$/.test(pincode)) {
    await expect(stateOptions.first(), 'the lookup for the new pincode must populate the state options')
      .toBeAttached({ timeout: 20000 });
    // The value is assigned in the same tick the options are, but assert it so callers reading
    // inputValue() immediately afterwards cannot observe a half-applied lookup.
    await expect(page.locator('select[name="state"]')).not.toHaveValue('', { timeout: 20000 });
  }
  await expect(page.locator('.hint', { hasText: 'Loading' })).toHaveCount(0, { timeout: 20000 });
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// Dates
// ─────────────────────────────────────────────────────────────────────────────────────────────────

export function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

export function isoToDisplay(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────
// The table-driven field validator
// ─────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * One row describes a text field and what the manual pack claims about it. The claims are separated
 * from the assertions on purpose: `documentedMaxError` records what the pack SAYS should appear, and
 * the runner reports when that message does not exist, instead of asserting the document is right.
 */
export interface FieldSpec {
  /** Playwright selector for the input/textarea. */
  selector: string;
  /** Human name, used in test titles. */
  label: string;
  /** The `maxlength` attribute the template carries, or null if it carries none. */
  maxlength: number | null;
  /** A value the field must accept and retain verbatim. */
  accepts: string[];
  /** The error message the manual pack claims appears when the field is over-long, if any. */
  documentedMaxError?: string;
}

/** Reads the rendered inline error for a field name, or '' when none is shown. */
export async function fieldError(page: Page, fieldName: string): Promise<string> {
  const group = page.locator('.form-group', {
    has: page.locator(`[name="${fieldName}"]`),
  }).first();
  const err = group.locator('.field-error');
  if (!(await err.count())) return '';
  return ((await err.first().textContent()) || '').trim();
}

/**
 * Asserts the browser's own maxlength truncation, which is what actually constrains these fields.
 * Returns the value the field ended up holding so the caller can report it.
 */
export async function assertTruncatesAt(page: Page, selector: string, limit: number): Promise<string> {
  const input = page.locator(selector);
  const overlong = 'A'.repeat(limit + 25);
  await input.fill(overlong);
  const got = await input.inputValue();
  expect(got.length, `${selector} should truncate at its maxlength of ${limit}`).toBe(limit);
  return got;
}

/** Asserts the field accepts and retains a value verbatim. */
export async function assertAccepts(page: Page, selector: string, value: string) {
  const input = page.locator(selector);
  await input.fill(value);
  expect(await input.inputValue(), `${selector} should retain ${JSON.stringify(value)} verbatim`).toBe(value);
}

/**
 * Clicks Next and returns every inline error currently rendered on the step, keyed by nothing — the
 * caller matches on content. Used to prove a field IS or IS NOT mandatory.
 */
export async function clickNextAndCollectErrors(page: Page): Promise<string[]> {
  await page.locator('button.btn-next').click();
  await page.waitForTimeout(300);
  return (await page.locator('.step-content .field-error').allTextContents()).map(t => t.trim());
}

/** True when the wizard is still on the given form step (i.e. Next did not advance). */
export async function stillOnStepWith(page: Page, selector: string): Promise<boolean> {
  return page.locator(selector).first().isVisible().catch(() => false);
}
