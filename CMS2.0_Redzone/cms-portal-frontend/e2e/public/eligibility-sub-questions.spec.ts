import { test, expect, Page } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * Two defects in the eligibility wizard's gate/sub-question pairs:
 *
 * 1. A dependent sub-answer was never cleared when its parent gate flipped back. Answering
 *    employeeOfRE=yes then employerRelationship=yes blocks; changing employeeOfRE to no clears the
 *    block and lets the wizard advance, but eligibilityAnswers['employerRelationship'] stayed 'yes'
 *    and was persisted into both the session draft and the server draft. Same shape for
 *    throughAdvocateEligibility -> isComplainantSelf, which also wrote formData['isComplainantSelf'].
 *    A withdrawn maintainability answer the citizen can no longer see or correct must not survive.
 *
 * 2. Both inline sub-questions bypassed ELIGIBILITY_QUESTION_MASTER and the translate pipe: their
 *    text was hardcoded English in the template, so all ten locales rendered English. isComplainantSelf
 *    had no master row at all, and onAdvocateSubAnswer() hardcoded its block message in English.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  // fallback(), not continue(): per-test fault-injection routes must still chain.
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

/**
 * The gate/sub-question pairs live on different entity departments (employeeOfRE is CEPC-scoped,
 * throughAdvocateEligibility is RBIO-scoped), and isQuestionVisible() derives that from the selected
 * entity. Stubbing the master keeps the test independent of which seeded entity maps to which
 * department, while still exercising the real component logic.
 */
async function stubMaster(page: Page, extra: Record<string, unknown>[] = []) {
  await page.route('**/api/v1/eligibility/questions*', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        schemeVersion: 'RBIOS_2021',
        schemeName: 'Reserve Bank - Integrated Ombudsman Scheme, 2021',
        reWindowDays: 30,
        data: [
          {
            questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
            questionText: 'Select Regulated Entity Name', applicableEntityType: 'ALL',
            blockOn: null, blockMessage: '', nonMaintainable: false, inlineSubQuestion: false,
          },
          ...extra,
        ],
      }),
    }));
}

const GATE_EMPLOYEE = {
  questionNumber: 2, questionKey: 'employeeOfRE', questionType: 'radio',
  questionText: 'Are / were you an employee of the Regulated Entity?',
  applicableEntityType: 'ALL', blockOn: null, blockMessage: '',
  nonMaintainable: true, inlineSubQuestion: false,
};

const SUB_EMPLOYER = {
  questionNumber: 3, questionKey: 'employerRelationship', questionType: 'radio',
  questionText: 'MASTER employer-relationship text',
  translationKey: 'eligibility.q_employer_relationship',
  applicableEntityType: 'ALL', blockOn: 'yes',
  blockMessage: 'Blocked on the employee-employer relationship.',
  blockMessageKey: 'eligibility.block_employer_relationship',
  clauseReference: '10(1)(g)', nonMaintainable: true, inlineSubQuestion: true,
};

const GATE_ADVOCATE = {
  questionNumber: 2, questionKey: 'throughAdvocateEligibility', questionType: 'radio',
  questionText: 'Is your complaint being made through an advocate?',
  applicableEntityType: 'ALL', blockOn: null, blockMessage: '',
  nonMaintainable: false, inlineSubQuestion: false,
};

const SUB_COMPLAINANT = {
  questionNumber: 3, questionKey: 'isComplainantSelf', questionType: 'radio',
  questionText: 'MASTER are-you-the-complainant text',
  translationKey: 'eligibility.sub_are_you_complainant',
  applicableEntityType: 'ALL', blockOn: 'no',
  blockMessage: 'Blocked because an advocate filed for a non-complainant.',
  blockMessageKey: 'eligibility.block_advocate_not_complainant',
  nonMaintainable: true, inlineSubQuestion: true,
};

async function openWizard(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500042', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
}

/**
 * Step 1 is the entity selection and it is mandatory, so the wizard will not advance to the radio
 * questions until an entity is chosen. Picks the first real option from the live entity list.
 */
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
 * Answers the current top-level radio question. Selected by option order (YES_NO_OPTIONS is
 * [yes, no]) rather than by a value attribute: the template binds [value], which sets the DOM
 * property and leaves no HTML attribute for a CSS selector to match.
 */
async function answerCurrent(page: Page, value: 'yes' | 'no') {
  await page.locator('.radio-list .radio-option').nth(value === 'yes' ? 0 : 1).click();
}

/** Reads the draft the component persists to sessionStorage. */
async function readDraft(page: Page): Promise<any> {
  return page.evaluate(() => {
    const raw = sessionStorage.getItem('cms_complaint_draft');
    return raw ? JSON.parse(raw) : null;
  });
}

test.describe('Eligibility sub-answers are cleared when their parent gate flips back', () => {

  test('employerRelationship is dropped when employeeOfRE flips to no', async ({ page }) => {
    await stubMaster(page, [GATE_EMPLOYEE, SUB_EMPLOYER]);
    await openWizard(page);

    // Step to the gate question.
    await selectEntityAndAdvance(page);

    // Gate = yes reveals the inline sub-question; answering yes blocks the complaint.
    await answerCurrent(page, 'yes');
    const sub = page.locator('.sub-question-block input[name="employerRelationship"][value="yes"]');
    await expect(sub).toBeVisible();
    await sub.check();
    await expect(page.locator('.block-note')).toBeVisible();

    // Flip the gate back. The sub-question disappears, so its answer is no longer correctable.
    await answerCurrent(page, 'no');
    await expect(page.locator('.sub-question-block')).toHaveCount(0);
    await expect(page.locator('.block-note')).toHaveCount(0);

    // The withdrawn answer must not survive into the persisted draft. Save explicitly so the
    // assertion does not depend on the 30s autosave timer.
    await page.locator('button.btn-save-link').click();
    const draft = await readDraft(page);
    expect(draft, 'wizard should have saved a draft').toBeTruthy();
    expect(draft.eligibilityAnswers['employeeOfRE']).toBe('no');
    expect(draft.eligibilityAnswers['employerRelationship'],
      'stale sub-answer persisted after its parent gate flipped').toBeUndefined();
  });

  test('isComplainantSelf is dropped from both answers and formData when the advocate gate flips', async ({ page }) => {
    await stubMaster(page, [GATE_ADVOCATE, SUB_COMPLAINANT]);
    await openWizard(page);

    await selectEntityAndAdvance(page);

    await answerCurrent(page, 'yes');
    const sub = page.locator('.sub-question-block input[name="isComplainantSelf"][value="no"]');
    await expect(sub).toBeVisible();
    await sub.check();
    await expect(page.locator('.block-note')).toBeVisible();

    await answerCurrent(page, 'no');
    await expect(page.locator('.sub-question-block')).toHaveCount(0);
    await expect(page.locator('.block-note')).toHaveCount(0);

    await page.locator('button.btn-save-link').click();
    const draft = await readDraft(page);
    expect(draft, 'wizard should have saved a draft').toBeTruthy();
    expect(draft.eligibilityAnswers['isComplainantSelf'],
      'stale advocate sub-answer persisted in eligibilityAnswers').toBeUndefined();
    // This one is also mirrored into formData, which is what reaches the complaint record.
    expect(draft.formData['isComplainantSelf'],
      'stale advocate sub-answer persisted in formData').toBeFalsy();
  });

  test('the server draft does not carry a withdrawn sub-answer', async ({ page }) => {
    await stubMaster(page, [GATE_EMPLOYEE, SUB_EMPLOYER]);

    const savedPayloads: any[] = [];
    await page.route('**/api/v1/complaints/drafts*', async route => {
      if (route.request().method() === 'POST') {
        try { savedPayloads.push(route.request().postDataJSON()); } catch { /* ignore */ }
        return route.fulfill({
          status: 200, contentType: 'application/json',
          body: JSON.stringify({ success: true, draftId: 'e2e-draft-1' }),
        });
      }
      return route.fallback();
    });

    await openWizard(page);
    await selectEntityAndAdvance(page);

    await answerCurrent(page, 'yes');
    await page.locator('.sub-question-block input[name="employerRelationship"][value="yes"]').check();
    await answerCurrent(page, 'no');

    // Force a save after the gate flipped.
    await page.locator('button.btn-save-link').click();

    await expect.poll(() => savedPayloads.length, { timeout: 20000 }).toBeGreaterThan(0);
    const last = savedPayloads[savedPayloads.length - 1];
    expect(last.eligibilityAnswers['employerRelationship'],
      'server draft received a withdrawn sub-answer').toBeUndefined();
  });

  test('re-answering the gate yes leaves the sub-question genuinely unanswered', async ({ page }) => {
    // Boundary: clearing must not merely hide the old value — the mandatory check has to fire again,
    // otherwise a stale 'yes' would silently satisfy it.
    await stubMaster(page, [GATE_EMPLOYEE, SUB_EMPLOYER]);
    await openWizard(page);

    await selectEntityAndAdvance(page);

    await answerCurrent(page, 'yes');
    await page.locator('.sub-question-block input[name="employerRelationship"][value="no"]').check();
    await answerCurrent(page, 'no');
    await answerCurrent(page, 'yes');

    // No radio may be pre-selected.
    await expect(page.locator('.sub-question-block input[name="employerRelationship"]:checked'))
      .toHaveCount(0);

    // And Next must refuse to advance.
    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error')).toBeVisible();
  });
});

test.describe('Inline sub-questions are master data, not hardcoded English', () => {

  test('the employer-relationship sub-question renders operator-authored master text', async ({ page }) => {
    // No translationKey, so questionText is the only source. Operator-authored text the frontend has
    // never seen must render — if the hardcoded literal were still in the template, this would show
    // the compiled-in wording instead.
    await stubMaster(page, [GATE_EMPLOYEE, { ...SUB_EMPLOYER, translationKey: null }]);
    await openWizard(page);

    await selectEntityAndAdvance(page);
    await answerCurrent(page, 'yes');

    await expect(page.locator('.sub-question-block .sub-question-label'))
      .toContainText('MASTER employer-relationship text');
  });

  test('the advocate sub-question renders operator-authored master text and its master block', async ({ page }) => {
    await stubMaster(page, [GATE_ADVOCATE, { ...SUB_COMPLAINANT, translationKey: null }]);
    await openWizard(page);

    await selectEntityAndAdvance(page);
    await answerCurrent(page, 'yes');

    await expect(page.locator('.sub-question-block .sub-question-label'))
      .toContainText('MASTER are-you-the-complainant text');

    // The block must come from the master row, not from a string compiled into the component.
    await page.locator('.sub-question-block input[name="isComplainantSelf"][value="no"]').check();
    await expect(page.locator('.block-note')).toBeVisible();
  });

  test('a seeded translation key takes precedence over the master English text', async ({ page }) => {
    // The localization path: when the master row carries a translationKey that resolves, the citizen
    // sees the translated text, not the English questionText column.
    await stubMaster(page, [GATE_EMPLOYEE, SUB_EMPLOYER]);
    await openWizard(page);

    await selectEntityAndAdvance(page);
    await answerCurrent(page, 'yes');

    const label = page.locator('.sub-question-block .sub-question-label');
    await expect(label).toBeVisible();
    await expect(label, 'the translated value must win over the master questionText')
      .not.toContainText('MASTER employer-relationship text');
  });

  test('a sub-question with no master row is not rendered as English filler', async ({ page }) => {
    // Fail closed: without a master row there is no authored text and no clause, so the wizard must
    // not invent a maintainability question.
    await stubMaster(page, [GATE_ADVOCATE]);
    await openWizard(page);

    await selectEntityAndAdvance(page);
    await answerCurrent(page, 'yes');

    await expect(page.locator('.sub-question-block')).toHaveCount(0);
  });

  test('both sub-questions have a master row and a translation key', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
    const byKey = new Map<string, any>(body.data.map((q: any) => [q.questionKey, q]));

    for (const key of ['employerRelationship', 'isComplainantSelf']) {
      const q = byKey.get(key);
      expect(q, `${key} has no ELIGIBILITY_QUESTION_MASTER row`).toBeTruthy();
      expect(q.inlineSubQuestion, `${key} should be an inline sub-question`).toBe(true);
      expect(q.translationKey, `${key} has no translation key, so it cannot be localized`).toBeTruthy();
      expect(q.blockMessageKey, `${key} blocks with no translatable message`).toBeTruthy();
    }
  });

  test('every sub-question translation key resolves in all ten locales', async ({ request }) => {
    const keys = [
      'eligibility.q_employer_relationship',
      'eligibility.q_employee_of_re',
      'eligibility.sub_are_you_complainant',
      'eligibility.block_advocate_not_complainant',
      'eligibility.block_employer_relationship',
    ];

    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const translations: Record<string, string> = body.data ?? body;

      for (const key of keys) {
        expect(translations[key], `${key} missing for ${locale}`).toBeTruthy();
      }
    }
  });

  test('the nine Indian locales are not silently serving the English text', async ({ request }) => {
    // The regression this guards: a key seeded only in English falls back to the English defaultValue,
    // so the citizen sees English while believing the portal is localized.
    const keys = [
      'eligibility.q_employer_relationship',
      'eligibility.sub_are_you_complainant',
      'eligibility.block_advocate_not_complainant',
    ];

    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const enT: Record<string, string> = en.data ?? en;

    for (const locale of LOCALES.filter(l => l !== 'en')) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;

      for (const key of keys) {
        expect(t[key], `${key} for ${locale} is identical to the English text`).not.toBe(enT[key]);
      }
    }
  });
});
