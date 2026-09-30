import { test, expect, Page, APIRequestContext } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * QA "Simplify for me" battery for the POST-LOGIN statutory maintainability gate at
 * /public/file-complaint, whose questions come from ELIGIBILITY_QUESTION_MASTER.
 *
 * Deliberately a separate file from eligibility-simplify.spec.ts, which covers the PRE-LOGIN triage
 * wizard at /public/eligibility-wizard. The two screens are different components with a different
 * question source and — the trap — a DIFFERENT pair of class names: the triage wizard uses
 * .simplify-toggle / .simplified-text, this one uses .simplify-btn / .simplified-box
 * (file-complaint.component.html:74-78). A selector borrowed from the other file matches nothing here
 * and every assertion on it passes vacuously.
 *
 * ── PRODUCT DEFECT THIS SPEC WAS WRITTEN AGAINST ────────────────────────────────────────────────────
 *
 * Four plain-language texts — q_through_advocate_simple, q_pending_ombudsman_simple,
 * q_settled_ombudsman_simple, q_staff_of_re_simple — were authored and seeded in en/hi/mr, but the
 * master rows they belong to carried SIMPLIFIED_TEXT = NULL. The affordance is gated on the ROW
 * (`@if (currentQuestion?.simplifiedText)`), not on the translation, so the icon could never render and
 * the translated wording was unreachable in every locale. Only isSubJudice and alreadySettled worked.
 * Fixed in EligibilityQuestionMasterSeeder plus V104 / oracle V101 (the seeder is insert-if-absent, so
 * it cannot repair a database that already holds the rows).
 *
 * WHAT IS NOT ASSERTED, deliberately: the QA sheet quotes simplified wording that differs from the
 * seeded text — e.g. "Is your complaint about the same issue that's already being looked at by another
 * court or legal body (not including criminal cases or police matters)?" against the seeded "Have you
 * already taken this exact problem to a court, arbitrator, or another official legal authority
 * (excluding criminal cases or police investigations)?". Both say the same thing; neither is obviously
 * the authority. Pinning QA's sentence would fail against correct behaviour, and pinning the seeded
 * sentence would make the test a copy of the seeder. So these tests assert the PROPERTIES that make a
 * simplification a simplification — present, non-empty, not a raw key, materially different from the
 * formal wording, shown ALONGSIDE it, and sourced from the master row — and the wording difference is
 * raised as a question for the business owner instead.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  // fallback()/continue() chaining: per-test routes registered later must still take precedence.
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

const ENTITY_ROW = {
  questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
  questionText: 'Select Regulated Entity Name', translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false, simplifiedText: null, simplifiedTextKey: null,
};

/**
 * isQuestionVisible() derives applicability from the selected entity's department, and these six
 * questions are spread across RBIO / NON_CEPC / CEPC. Forcing applicableEntityType to ALL isolates one
 * question per test without depending on which seeded entity the dropdown happens to offer first,
 * while still running the real component logic.
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
        data: [ENTITY_ROW, ...rows],
      }),
    }));
}

async function openWizard(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500077', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // A non-existent Angular route falls through to the public home page, so confirm the gate mounted.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });
}

/** Step 1 is the mandatory entity selection; no radio question is reachable until it is answered. */
async function selectEntityAndAdvance(page: Page) {
  // The entity question is a SEARCHABLE COMBOBOX, not a native <select>. The select that used to sit
  // beside it was a SECOND control bound to the same answer and has been removed, so this is the only
  // path a citizen has.
  const box = page.locator('input.entity-search-input');
  await expect(box).toBeVisible({ timeout: 20000 });
  // The results list renders only while the dropdown is open, which focus does.
  await box.click();
  const first = page.locator('li.entity-search-option').first();
  await expect(first, 'the entity master offered nothing to select').toBeVisible({ timeout: 20000 });
  const name = ((await first.locator('.es-name').textContent()) || '').trim();
  // mousedown, not click: the option's handler is (mousedown), which fires BEFORE the input's blur
  // closes the list. A click would let blur remove the option mid-gesture.
  await first.dispatchEvent('mousedown');
  await expect(box, 'the chosen entity was not recorded').toHaveValue(name, { timeout: 10000 });
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
}

async function liveRow(request: APIRequestContext, key: string): Promise<any> {
  const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
  const row = (body.data as any[]).find(q => q.questionKey === key);
  expect(row, `${key} has no ELIGIBILITY_QUESTION_MASTER row`).toBeTruthy();
  return row;
}

/** The live row, forced visible, so each test drives the SEEDED wording rather than a fixture's. */
async function visibleLiveRow(request: APIRequestContext, key: string): Promise<any> {
  const row = await liveRow(request, key);
  return { ...row, questionNumber: 2, applicableEntityType: 'ALL', inlineSubQuestion: false };
}

/**
 * Every question QA exercises the affordance on. The two court questions are the ones QA quotes exact
 * simplified wording for; the other four are the ones whose translations existed with no master row to
 * reach them, so they are the regression guard for the fix.
 */
const SIMPLIFIABLE = [
  { key: 'isSubJudice', qa: 'is the matter sub judice' },
  { key: 'alreadySettled', qa: 'has the matter been settled by a court' },
  { key: 'throughAdvocateEligibility', qa: 'are you filing through an advocate' },
  { key: 'pendingBeforeOmbudsman', qa: 'is the matter pending before an Ombudsman' },
  { key: 'settledByOmbudsman', qa: 'has the matter been settled by an Ombudsman' },
  { key: 'staffOfRE', qa: 'are you staff of the Regulated Entity' },
];

for (const { key, qa } of SIMPLIFIABLE) {
  test.describe(`${key} — "${qa}"`, () => {

    test(`${key}: the simplify affordance is offered at the end of the question, collapsed`, async ({ page, request }) => {
      await stubMaster(page, [await visibleLiveRow(request, key)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      // Inside .q-text-wrap, i.e. at the END of the question text, not floating elsewhere on the card.
      await expect(page.locator('.question-text .q-text-wrap .simplify-btn')).toBeVisible();
      // Collapsed until asked for: a permanently-open panel is not the feature QA describes.
      await expect(page.locator('.simplified-box')).toHaveCount(0);
    });

    test(`${key}: clicking it shows a real simplification ALONGSIDE the original question`, async ({ page, request }) => {
      await stubMaster(page, [await visibleLiveRow(request, key)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const questionText = page.locator('.question-text');
      const formal = (await questionText.textContent())?.trim() ?? '';
      expect(formal.length, 'the formal question did not render').toBeGreaterThan(0);

      await page.locator('.simplify-btn').click();

      const box = page.locator('.simplified-box');
      await expect(box).toBeVisible();
      const simple = (await box.textContent())?.trim() ?? '';

      // Visibility alone would pass on an empty panel, on one echoing the formal wording, and on one
      // showing the raw key — which is exactly what an unseeded translation renders as. All three are
      // the feature not working, so all three are asserted against.
      expect(simple.length, 'the simplified panel is empty').toBeGreaterThan(0);
      expect(simple, 'the simplified panel is rendering its translation key').not.toMatch(/^eligibility\./);
      expect(simple, 'the simplified text is identical to the formal wording').not.toBe(formal);
      // "Simplified" means plainer, so it must not simply be the same statutory sentence reformatted.
      expect(simple, 'the simplified text merely repeats the formal wording')
        .not.toContain('quasi-judicial forum');

      // QA: "System should display the original question completely along with the new simplified text
      // in the same screen." The citizen's answer is recorded against the formal wording, so it must
      // remain on screen and unchanged — a panel that REPLACES it would hide what they agreed to.
      await expect(questionText).toBeVisible();
      expect((await questionText.textContent())?.trim()).toBe(formal);
    });

    test(`${key}: the simplification is the master row's, not a value compiled into the bundle`, async ({ page, request }) => {
      // Prose no frontend has ever seen: if it reaches the panel, it came from the served master row.
      const row = await visibleLiveRow(request, key);
      const marker = `SIMPLIFIED-MARKER-${key} plain wording served by the master row.`;
      await stubMaster(page, [{ ...row, simplifiedText: marker, simplifiedTextKey: null }]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      await page.locator('.simplify-btn').click();
      await expect(page.locator('.simplified-box')).toContainText(marker);
    });

    test(`${key}: clicking again collapses the panel`, async ({ page, request }) => {
      await stubMaster(page, [await visibleLiveRow(request, key)]);
      await openWizard(page);
      await selectEntityAndAdvance(page);

      const toggle = page.locator('.simplify-btn');
      await toggle.click();
      await expect(page.locator('.simplified-box')).toBeVisible();
      await toggle.click();
      await expect(page.locator('.simplified-box')).toHaveCount(0);
    });

    test(`${key}: the seeded master row actually carries simplified text`, async ({ request }) => {
      // The regression guard for the defect above. The four rows whose translations were authored but
      // never attached showed NO icon at all, in every locale, which no UI assertion above could catch
      // because the tests would simply not find a button to click.
      const row = await liveRow(request, key);
      expect(row.simplifiedText, `${key} has no simplifiedText, so the icon cannot render`).toBeTruthy();
      expect(row.simplifiedTextKey, `${key} simplified text is untranslatable`).toBeTruthy();
      expect(row.simplifiedText, `${key} simplifiedText is a key, not prose`)
        .not.toMatch(/^eligibility\./);
      expect(row.simplifiedText).not.toBe(row.questionText);
    });

    test(`${key}: the simplified text is served as translatable master data`, async ({ request }) => {
      const row = await liveRow(request, key);
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
      const t: Record<string, string> = body.data ?? body;

      // An unreachable translation is what the defect looked like from the other side: the value was
      // present in en/hi/mr the whole time, with no master row to surface it.
      expect(t[row.simplifiedTextKey], `${row.simplifiedTextKey} has no English translation`).toBeTruthy();
    });
  });
}

test.describe('Simplify battery — the affordance is offered where, and only where, there is a simplification', () => {

  test('every question carrying simplified text also carries the key that translates it', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
    const withText = (body.data as any[]).filter(q => q.simplifiedText);

    expect(withText.length, 'no question offers simplified text at all').toBeGreaterThanOrEqual(
      SIMPLIFIABLE.length
    );
    for (const q of withText) {
      expect(q.simplifiedTextKey, `${q.questionKey} simplified text cannot be translated`).toBeTruthy();
    }
  });

  test('no authored simplified translation is left with no master row to reach it', async ({ request }) => {
    // The defect, stated as an invariant: a translation key named *_simple exists to be shown on a
    // question. One with no row pointing at it is dead weight a citizen can never see, and the only
    // symptom is a missing icon — nothing errors, nothing logs.
    const questions = (await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json()).data as any[];
    const attached = new Set(questions.map(q => q.simplifiedTextKey).filter(Boolean));

    // The endpoint returns the bare key→value map, with no `data` envelope. Defaulting to {} instead
    // of the body itself made this read empty and the assertion below vacuous.
    const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const t: Record<string, string> = body.data ?? body;
    const authored = Object.keys(t).filter(k => /^eligibility\.q_.*_simple$/.test(k));
    expect(authored.length, 'no simplified translations are seeded at all').toBeGreaterThan(0);

    const orphaned = authored.filter(k => !attached.has(k));
    expect(orphaned, 'simplified wording authored but unreachable — no master row points at it').toEqual([]);
  });

  test('a question with no simplified text offers no icon to click', async ({ page, request }) => {
    // The negative case, so the positive assertions above are known to be about the row rather than
    // about an icon that renders unconditionally.
    const row = await visibleLiveRow(request, 'previouslyFiledWithCEPC');
    expect(row.simplifiedText, 'fixture assumes this row has no simplified text').toBeFalsy();

    await stubMaster(page, [row]);
    await openWizard(page);
    await selectEntityAndAdvance(page);

    await expect(page.locator('.question-text')).toBeVisible();
    await expect(page.locator('.simplify-btn')).toHaveCount(0);
    await expect(page.locator('.simplified-box')).toHaveCount(0);
  });

  test('the panel does not carry over to the next question', async ({ page, request }) => {
    // A stale panel would show one question's plain-language wording beside a different question —
    // worse than no simplification, because it misdescribes what the citizen is answering.
    const first = await visibleLiveRow(request, 'isSubJudice');
    const second = { ...(await visibleLiveRow(request, 'staffOfRE')), questionNumber: 3 };
    await stubMaster(page, [first, second]);
    await openWizard(page);
    await selectEntityAndAdvance(page);

    await page.locator('.simplify-btn').click();
    await expect(page.locator('.simplified-box')).toBeVisible();

    // Answer non-blocking so the wizard advances rather than blocking on this question.
    await page.locator('.radio-list .radio-option').nth(1).click();
    const firstText = (await page.locator('.question-text').textContent())?.trim() ?? '';
    await page.locator('button.btn-next').click();
    await expect(page.locator('.question-text')).not.toHaveText(firstText, { timeout: 10000 });

    await expect(page.locator('.simplified-box')).toHaveCount(0);
  });
});
