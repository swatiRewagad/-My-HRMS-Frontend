import { test, expect, Page, APIRequestContext } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * QA maintainability battery: the seven Yes/No questions a citizen answers before a complaint can be
 * registered, each asserted on the same four-part shape — both options selectable, the blocking answer
 * raises the master's non-maintainability message and stops the citizen advancing, the non-blocking
 * answer advances, no answer at all yields the mandatory-response notice, and rapid toggling leaves a
 * coherent state.
 *
 * Keyed to the real ELIGIBILITY_QUESTION_MASTER questionKey values, not to the QA prose.
 *
 * Deliberately NOT repeated here (owned by eligibility-sub-questions.spec.ts): stale sub-answer
 * clearing when a parent gate flips, and the master-text / translate-pipe sourcing of the two inline
 * sub-questions. This spec covers the answer-shape those tests do not.
 *
 * Clause NUMBERS are never asserted. QA's paste cites different clauses from the seeded master for
 * pendingBeforeOmbudsman, settledByOmbudsman and staffOfRE, and cites "Appendix 3"/"Appendix 4" for the
 * court questions, which are not clause references at all. Until the business owner resolves that, these
 * tests pin the PLUMBING (whatever the master holds is what the citizen is shown) exactly as
 * eligibility-clause-interpolation.spec.ts does.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  // fallback(), not continue(): per-test fault-injection routes must still chain.
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

const ENTITY_ROW = {
  questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
  questionText: 'Select Regulated Entity Name', translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

/**
 * A trailing non-blocking question, so "Next advances" is observable as arriving at a named question
 * rather than as leaving the eligibility phase.
 */
const SENTINEL_TEXT = 'SENTINEL question reached after advancing';
const SENTINEL_ROW = {
  questionNumber: 90, questionKey: 'sentinelAfter', questionType: 'radio',
  questionText: SENTINEL_TEXT, translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

/**
 * isQuestionVisible() derives applicability from the selected entity's department, and the seven
 * questions are spread across NON_CEPC / RBIO / CEPC. Stubbing the master with applicableEntityType
 * forced to ALL isolates one question at a time without depending on which seeded entity maps to which
 * department, while still exercising the real component logic.
 */
async function stubMaster(page: Page, rows: Record<string, unknown>[]) {
  await page.route('**/api/v1/eligibility/questions*', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        schemeVersion: 'RBIOS_2021',
        schemeName: 'Reserve Bank - Integrated Ombudsman Scheme, 2021',
        reWindowDays: 30,
        data: [ENTITY_ROW, ...rows, SENTINEL_ROW],
      }),
    }));
}

async function openWizard(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500055', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // A non-existent Angular route falls through to the public home page, so confirm the wizard mounted.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });
}

/** Step 1 is the mandatory entity selection; the radio questions are not reachable until it is answered. */
async function selectEntityAndAdvance(page: Page) {
  const select = page.locator('select.entity-select-dropdown');
  await expect(select).toBeVisible({ timeout: 20000 });
  await expect(select.locator('option:not([disabled])').first()).toBeAttached({ timeout: 20000 });
  const value = await select.locator('option:not([disabled])').first().getAttribute('value');
  await select.selectOption(value!);
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
}

/**
 * Answers the current top-level radio question by option order (YES_NO_OPTIONS is [yes, no]). The
 * template binds [value], which sets the DOM property and leaves no HTML attribute to select on.
 */
async function answerCurrent(page: Page, value: 'yes' | 'no') {
  await page.locator('.radio-list .radio-option').nth(value === 'yes' ? 0 : 1).click();
}

/**
 * The citizen must not be able to leave a blocked question. The wizard removes the whole action row
 * while blocked rather than disabling Next, so "cannot advance" is asserted as "no actionable Next",
 * which covers both renderings.
 */
async function expectCannotAdvance(page: Page) {
  const next = page.locator('button.btn-next');
  const count = await next.count();
  if (count > 0) await expect(next).toBeDisabled();
  await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
}

/** English block prose for a master row, with the clause placeholder stripped to its leading segment. */
async function seededBlockProse(request: APIRequestContext, blockMessageKey: string): Promise<string> {
  const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
  const t: Record<string, string> = body.data ?? body;
  const value = t[blockMessageKey];
  expect(value, `${blockMessageKey} has no English translation`).toBeTruthy();
  return value.split('{{clause}}')[0].trim();
}

async function liveRow(request: APIRequestContext, key: string): Promise<any> {
  const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
  const row = (body.data as any[]).find(q => q.questionKey === key);
  expect(row, `${key} has no ELIGIBILITY_QUESTION_MASTER row`).toBeTruthy();
  // Forced so the question is visible regardless of which entity the dropdown happens to offer first.
  return { ...row, applicableEntityType: 'ALL' };
}

/** A blocking top-level question, with test-owned prose so the assertion proves WHICH row blocked. */
function blockingRow(key: string, blockOn: 'yes' | 'no') {
  return {
    questionNumber: 2, questionKey: key, questionType: 'radio',
    questionText: `Stubbed ${key} question text`, translationKey: null,
    applicableEntityType: 'ALL', blockOn,
    blockMessage: `BLOCKED-${key} under clause {{clause}} of the Scheme.`,
    blockMessageKey: null, clauseReference: '10(9)(z)',
    nonMaintainable: true, inlineSubQuestion: false,
  };
}

/** A non-blocking gate question whose inline sub-question carries the block. */
function gateRow(key: string) {
  return {
    questionNumber: 2, questionKey: key, questionType: 'radio',
    questionText: `Stubbed ${key} gate text`, translationKey: null,
    applicableEntityType: 'ALL', blockOn: null, blockMessage: '', blockMessageKey: null,
    clauseReference: null, nonMaintainable: false, inlineSubQuestion: false,
  };
}

function subRow(key: string, blockOn: 'yes' | 'no') {
  return {
    questionNumber: 3, questionKey: key, questionType: 'radio',
    questionText: `Stubbed ${key} sub-question text`, translationKey: null,
    applicableEntityType: 'ALL', blockOn,
    blockMessage: `BLOCKED-${key} under clause {{clause}} of the Scheme.`,
    blockMessageKey: null, clauseReference: '10(9)(y)',
    nonMaintainable: true, inlineSubQuestion: true,
  };
}

/** The six questions that block on their own answer. */
const BLOCKING: { qa: string; key: string; blockOn: 'yes' | 'no' }[] = [
  { qa: 'is the matter sub judice', key: 'isSubJudice', blockOn: 'yes' },
  { qa: 'has the matter been settled by a court', key: 'alreadySettled', blockOn: 'yes' },
  { qa: 'is the matter pending before an Ombudsman', key: 'pendingBeforeOmbudsman', blockOn: 'yes' },
  { qa: 'has the matter been settled by an Ombudsman', key: 'settledByOmbudsman', blockOn: 'yes' },
  { qa: 'are you staff of the Regulated Entity', key: 'staffOfRE', blockOn: 'yes' },
  { qa: 'was this previously filed with CEPC / RBI', key: 'previouslyFiledWithCEPC', blockOn: 'yes' },
];

/** The two questions that gate an inline sub-question instead of blocking directly. */
const GATES: { qa: string; gate: string; sub: string; subBlockOn: 'yes' | 'no' }[] = [
  {
    qa: 'are you filing through an advocate', gate: 'throughAdvocateEligibility',
    sub: 'isComplainantSelf', subBlockOn: 'no',
  },
  {
    qa: 'are you an employee of the Regulated Entity', gate: 'employeeOfRE',
    sub: 'employerRelationship', subBlockOn: 'yes',
  },
];

for (const { qa, key, blockOn } of BLOCKING) {
  const passOn = blockOn === 'yes' ? 'no' : 'yes';

  test.describe(`${key} — "${qa}"`, () => {

    test(`${key}: both Yes and No are offered and each is selectable`, async ({ page }) => {
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const options = page.locator('.radio-list .radio-option');
      await expect(options).toHaveCount(2);
      await expect(options.nth(0)).toContainText('Yes');
      await expect(options.nth(1)).toContainText('No');

      await options.nth(0).click();
      await expect(page.locator(`.radio-list input[name="${key}"]:checked`)).toHaveCount(1);
      await expect(options.nth(0)).toHaveClass(/selected/);

      await options.nth(1).click();
      await expect(options.nth(1)).toHaveClass(/selected/);
      await expect(options.nth(0)).not.toHaveClass(/selected/);
    });

    test(`${key}: the blocking answer shows the master's non-maintainability message and stops the citizen`, async ({ page }) => {
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, blockOn);

      const note = page.locator('.block-note');
      await expect(note).toBeVisible();
      // Prose the frontend has never seen: it can only be here if it came from THIS master row.
      await expect(note).toContainText(`BLOCKED-${key}`);
      await expect(note, 'a raw placeholder must never reach a citizen').not.toContainText('{{clause}}');
      await expectCannotAdvance(page);
    });

    test(`${key}: the non-blocking answer lets the citizen advance`, async ({ page }) => {
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, passOn);
      await expect(page.locator('.block-note')).toHaveCount(0);

      const next = page.locator('button.btn-next');
      await expect(next).toBeEnabled();
      await next.click();
      await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
    });

    test(`${key}: answering neither and pressing Next demands a response`, async ({ page }) => {
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await expect(page.locator(`.radio-list input[name="${key}"]:checked`)).toHaveCount(0);
      await page.locator('button.btn-next').click();

      const error = page.locator('#eligibility-mandatory-error');
      await expect(error).toBeVisible();
      // QA's expected text is "Response is Mandatory"; the product emits "Response is mandatory."
      // Matched case-insensitively rather than verbatim — the casing difference is raised as a
      // wording question for the business owner, not silently asserted either way.
      await expect(error).toContainText(/response is mandatory/i);
      await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
    });

    test(`${key}: rapidly toggling Yes and No does not corrupt the flow`, async ({ page }) => {
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const options = page.locator('.radio-list .radio-option');
      for (let i = 0; i < 6; i++) await options.nth(i % 2).click();

      // Ends on the non-blocking answer: the block must be gone, exactly one option selected, and the
      // wizard must still advance — a stale block would trap the citizen on a question they answered No to.
      await options.nth(blockOn === 'yes' ? 1 : 0).click();
      await expect(page.locator('.block-note')).toHaveCount(0);
      await expect(page.locator(`.radio-list input[name="${key}"]:checked`)).toHaveCount(1);
      await expect(page.locator('#eligibility-mandatory-error')).toHaveCount(0);

      await page.locator('button.btn-next').click();
      await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
    });

    test(`${key}: toggling back to the blocking answer re-raises the block`, async ({ page }) => {
      // The other direction of the toggle: clearing a block must not make the question un-blockable.
      await stubMaster(page, [blockingRow(key, blockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, blockOn);
      await expect(page.locator('.block-note')).toBeVisible();
      await answerCurrent(page, passOn);
      await expect(page.locator('.block-note')).toHaveCount(0);
      await answerCurrent(page, blockOn);
      await expect(page.locator('.block-note')).toBeVisible();
      await expectCannotAdvance(page);
    });

    test(`${key}: the seeded master row is what a real citizen is blocked with`, async ({ page, request }) => {
      // The stubbed-prose tests above prove the plumbing; this one proves the SEEDED row blocks on the
      // expected polarity and renders its own seeded, translated prose.
      const row = await liveRow(request, key);
      expect(row.blockOn, `${key} no longer blocks on ${blockOn}`).toBe(blockOn);
      expect(row.nonMaintainable, `${key} blocks without being non-maintainable`).toBe(true);
      expect(row.blockMessageKey, `${key} blocks with no translatable message`).toBeTruthy();

      const prose = await seededBlockProse(request, row.blockMessageKey);

      await stubMaster(page, [{ ...row, questionNumber: 2 }]);
      await openWizard(page);
      await selectEntityAndAdvance(page);
      await answerCurrent(page, blockOn);

      const note = page.locator('.block-note');
      await expect(note).toBeVisible();
      await expect(note).toContainText(prose);
      await expect(note).not.toContainText('{{clause}}');
      await expectCannotAdvance(page);
    });
  });
}

for (const { qa, gate, sub, subBlockOn } of GATES) {
  const subPassOn = subBlockOn === 'yes' ? 'no' : 'yes';

  test.describe(`${gate} — "${qa}"`, () => {

    test(`${gate}: both Yes and No are offered and each is selectable`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const options = page.locator('.radio-list .radio-option');
      await expect(options).toHaveCount(2);
      await expect(options.nth(0)).toContainText('Yes');
      await expect(options.nth(1)).toContainText('No');

      await options.nth(1).click();
      await expect(options.nth(1)).toHaveClass(/selected/);
      await options.nth(0).click();
      await expect(options.nth(0)).toHaveClass(/selected/);
      await expect(page.locator(`.radio-list input[name="${gate}"]:checked`)).toHaveCount(1);
    });

    test(`${gate}: Yes branches to the sub-question rather than blocking on its own`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      await expect(page.locator('.sub-question-block')).toBeVisible();
      await expect(page.locator(`.sub-question-block input[name="${sub}"]`)).toHaveCount(2);
      // The gate itself is not a bar to maintainability — only the sub-answer is.
      await expect(page.locator('.block-note')).toHaveCount(0);
    });

    test(`${gate}: the sub-question's blocking answer shows its own master message and stops the citizen`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      await page.locator(`.sub-question-block input[name="${sub}"][value="${subBlockOn}"]`).check();

      const note = page.locator('.block-note');
      await expect(note).toBeVisible();
      await expect(note).toContainText(`BLOCKED-${sub}`);
      await expect(note).not.toContainText('{{clause}}');
      await expectCannotAdvance(page);
    });

    test(`${gate}: the sub-question's non-blocking answer lets the citizen advance`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      await page.locator(`.sub-question-block input[name="${sub}"][value="${subPassOn}"]`).check();
      await expect(page.locator('.block-note')).toHaveCount(0);

      await page.locator('button.btn-next').click();
      await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
    });

    test(`${gate}: No skips the sub-question and lets the citizen advance`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'no');
      await expect(page.locator('.sub-question-block')).toHaveCount(0);
      await expect(page.locator('.block-note')).toHaveCount(0);

      await page.locator('button.btn-next').click();
      await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
    });

    test(`${gate}: answering neither and pressing Next demands a response`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await expect(page.locator(`.radio-list input[name="${gate}"]:checked`)).toHaveCount(0);
      await page.locator('button.btn-next').click();

      const error = page.locator('#eligibility-mandatory-error');
      await expect(error).toBeVisible();
      await expect(error).toContainText(/response is mandatory/i);
      await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
    });

    test(`${gate}: Yes with the sub-question left unanswered demands a response`, async ({ page }) => {
      // The sub-answer decides maintainability, so it is as mandatory as its parent. Distinct from
      // eligibility-sub-questions.spec.ts, which covers the same check AFTER a gate flip cleared a
      // previous answer; this is the never-answered path.
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      await expect(page.locator(`.sub-question-block input[name="${sub}"]:checked`)).toHaveCount(0);
      await page.locator('button.btn-next').click();

      await expect(page.locator('#eligibility-mandatory-error')).toContainText(/response is mandatory/i);
      await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
    });

    test(`${gate}: rapidly toggling the gate does not corrupt the flow`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const options = page.locator('.radio-list .radio-option');
      for (let i = 0; i < 6; i++) await options.nth(i % 2).click();

      await options.nth(1).click();
      await expect(page.locator('.sub-question-block')).toHaveCount(0);
      await expect(page.locator('.block-note')).toHaveCount(0);
      await expect(page.locator(`.radio-list input[name="${gate}"]:checked`)).toHaveCount(1);

      await page.locator('button.btn-next').click();
      await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
    });

    test(`${gate}: rapidly toggling the sub-answer leaves the last answer in force`, async ({ page }) => {
      await stubMaster(page, [gateRow(gate), subRow(sub, subBlockOn)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      const yes = page.locator(`.sub-question-block input[name="${sub}"][value="yes"]`);
      const no = page.locator(`.sub-question-block input[name="${sub}"][value="no"]`);
      for (let i = 0; i < 3; i++) { await yes.check(); await no.check(); }

      // Ends on 'no'. Whether that blocks depends on the row's polarity — assert the state matches it
      // rather than assuming a direction.
      await expect(page.locator('.block-note')).toHaveCount(subBlockOn === 'no' ? 1 : 0);
      await expect(page.locator(`.sub-question-block input[name="${sub}"]:checked`)).toHaveCount(1);
    });

    test(`${gate}: the seeded gate and sub-question rows have the polarity the flow depends on`, async ({ page, request }) => {
      const gateRowLive = await liveRow(request, gate);
      const subRowLive = await liveRow(request, sub);

      expect(gateRowLive.blockOn, `${gate} is a gate and must not block on its own answer`).toBeFalsy();
      expect(subRowLive.inlineSubQuestion, `${sub} must render inline under its gate`).toBe(true);
      expect(subRowLive.blockOn, `${sub} no longer blocks on ${subBlockOn}`).toBe(subBlockOn);
      expect(subRowLive.blockMessageKey, `${sub} blocks with no translatable message`).toBeTruthy();

      const prose = await seededBlockProse(request, subRowLive.blockMessageKey);

      await stubMaster(page, [{ ...gateRowLive, questionNumber: 2 }, { ...subRowLive, questionNumber: 3 }]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await answerCurrent(page, 'yes');
      await page.locator(`.sub-question-block input[name="${sub}"][value="${subBlockOn}"]`).check();

      const note = page.locator('.block-note');
      await expect(note).toBeVisible();
      await expect(note).toContainText(prose);
      await expect(note).not.toContainText('{{clause}}');
      await expectCannotAdvance(page);
    });
  });
}

test.describe('Maintainability battery — master coverage of the seven QA questions', () => {

  test('every question named by QA has a master row of the right shape', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
    const byKey = new Map<string, any>((body.data as any[]).map(q => [q.questionKey, q]));

    for (const { key } of BLOCKING) {
      const q = byKey.get(key);
      expect(q, `${key} has no ELIGIBILITY_QUESTION_MASTER row`).toBeTruthy();
      expect(q.questionType).toBe('radio');
      expect(q.inlineSubQuestion, `${key} is a top-level question, not an inline sub-question`).toBe(false);
    }
    for (const { gate, sub } of GATES) {
      expect(byKey.get(gate), `${gate} has no master row`).toBeTruthy();
      expect(byKey.get(sub), `${sub} has no master row`).toBeTruthy();
    }
  });

  test('the mandatory-response notice is served as master translation data', async ({ request }) => {
    // The literal is also compiled in as a t() default; if the DB row were missing, every locale would
    // silently fall back to English.
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;
      expect(t['eligibility.response_mandatory'], `response_mandatory missing for ${locale}`).toBeTruthy();
    }
  });
});
