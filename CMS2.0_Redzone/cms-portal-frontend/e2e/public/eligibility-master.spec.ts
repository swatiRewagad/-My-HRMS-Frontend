import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * UST-public / FR-G-007 (D14): the citizen wizard's maintainability questions come from
 * ELIGIBILITY_QUESTION_MASTER via GET /api/v1/eligibility/questions.
 *
 * They used to be a 157-line hardcoded array in file-complaint.component.ts, which meant a Scheme
 * amendment needed a frontend release — and the hardcoded copy had already drifted, citing
 * "Integrated Ombudsman Scheme, 2026" for clauses that belong to the 2021 Scheme. That text is
 * shown verbatim to a citizen whose complaint is closed as non-maintainable, so these tests also
 * pin the Scheme year across every supported locale.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

// Bengali stores the year in Bengali numerals, so an ASCII-only check would pass a wrong year.
const WRONG_YEARS = ['2026', '২০২৬'];

/**
 * Blocking questions whose Scheme clause has NOT been verified against the published Scheme text.
 * They are allowed to carry a null clauseReference precisely because the alternative — inventing a
 * plausible-looking citation — would put unverified legal text in front of a citizen being denied
 * statutory recourse. Remove a key from this list as soon as the authoritative clause is supplied.
 *
 * isComplainantSelf (BRD row 15): the clause barring an advocate-filed complaint where the filer is
 * not the complainant is still to be confirmed by the business owner.
 */
const CLAUSE_PENDING_VERIFICATION = ['isComplainantSelf'];

/**
 * The dev server's environment.ts API base is fixed at build time. When API_BASE_URL points
 * somewhere else, redirect the app's calls so the UI tests exercise the intended backend.
 * Registered in beforeEach so per-test fault-injection routes still take precedence.
 */
const APP_API_BASE = 'http://localhost:8082';

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

async function openEligibilityWizard(page: any) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500001', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
}

test.describe('FR-G-007 — Eligibility questions are master data (D14)', () => {

  test('the endpoint serves the questions with their clause references', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/eligibility/questions`);

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.schemeVersion).toBe('RBIOS_2021');
    expect(Array.isArray(body.data)).toBe(true);
    expect(body.data.length).toBeGreaterThan(0);

    // Every blocking question must cite the Scheme clause it blocks under — the citizen is told
    // which provision denied them, so a block with no clause is not defensible.
    for (const q of body.data) {
      expect(q.questionKey, 'questionKey is the answer key and radio input name').toBeTruthy();
      expect(['select', 'radio']).toContain(q.questionType);
      expect(q.questionText).toBeTruthy();
      if (q.blockOn) {
        expect(q.blockMessage, `${q.questionKey} blocks with no message`).toBeTruthy();
        if (!CLAUSE_PENDING_VERIFICATION.includes(q.questionKey)) {
          expect(q.clauseReference, `${q.questionKey} blocks but cites no clause`).toBeTruthy();
        }
      }
    }
  });

  test('the clause-pending exceptions are still genuinely uncited, not quietly guessed', async ({ request }) => {
    // Guards the exception list from both directions: a guessed clause must not appear on a row whose
    // clause is unverified, and once the authoritative clause IS supplied the row must be removed from
    // CLAUSE_PENDING_VERIFICATION so the main assertion covers it again.
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
    const byKey = new Map<string, any>(body.data.map((q: any) => [q.questionKey, q]));

    for (const key of CLAUSE_PENDING_VERIFICATION) {
      const q = byKey.get(key);
      if (!q) continue;
      expect(q.clauseReference,
        `${key} now cites a clause — supply it deliberately and drop ${key} from CLAUSE_PENDING_VERIFICATION`)
        .toBeFalsy();
    }
  });

  test('the Scheme name is served so the closure letter does not hardcode a year', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

    expect(body.schemeName).toBeTruthy();
    expect(body.schemeName).toContain('2021');
    for (const wrong of WRONG_YEARS) expect(body.schemeName).not.toContain(wrong);
  });

  test('applicability is expressed as data, not as hardcoded key lists', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

    // isQuestionVisible() reads these columns, so an unknown value would silently show a
    // CEPC-only question to an RBIO complainant.
    const allowed = ['ALL', 'RBIO', 'CEPC', 'NON_CEPC'];
    for (const q of body.data) {
      expect(allowed, `${q.questionKey} has an unhandled applicableEntityType`)
        .toContain(q.applicableEntityType);
    }
    // The set must actually be used, otherwise the column is decorative.
    const types = new Set(body.data.map((q: any) => q.applicableEntityType));
    expect(types.size).toBeGreaterThan(1);
  });

  test('a question ordering is stable and starts with the entity selection', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

    const numbers = body.data.map((q: any) => q.questionNumber);
    expect(numbers).toEqual([...numbers].sort((a: number, b: number) => a - b));
    expect(body.data[0].questionType).toBe('select');
  });

  test('an unknown scheme version returns no questions rather than a wrong Scheme', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/eligibility/questions?schemeVersion=RBIOS_1999`);

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.schemeVersion).toBe('RBIOS_1999');
    expect(body.data).toHaveLength(0);
  });
});

test.describe('FR-G-007 — Citizen-facing Scheme year (D14)', () => {

  test('no locale cites the wrong Scheme year', async ({ request }) => {
    // The seeders are insert-if-absent, so a pre-existing wrong row survives a restart. This is the
    // regression guard for the corrective migration in V10 / oracle V9.
    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const translations: Record<string, string> = body.data ?? body;

      const offenders = Object.entries(translations)
        .filter(([, v]) => typeof v === 'string' && WRONG_YEARS.some(w => v.includes(w)))
        .map(([k]) => k);

      expect(offenders, `${locale} still cites a wrong Scheme year`).toEqual([]);
    }
  });

  test('the master block messages cite the 2021 Scheme', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

    for (const q of body.data) {
      if (!q.blockMessage) continue;
      for (const wrong of WRONG_YEARS) {
        expect(q.blockMessage, `${q.questionKey} block message cites ${wrong}`).not.toContain(wrong);
      }
    }
  });
});

test.describe('FR-G-007 — The wizard renders the master, and fails closed without it (D14)', () => {

  test('the first step renders the entity question served by the master', async ({ page }) => {
    let servedFirst = '';
    page.on('response', async (res: any) => {
      if (res.url().includes('/api/v1/eligibility/questions') && res.ok()) {
        try {
          const body = await res.json();
          if (body?.data?.length) servedFirst = body.data[0].questionText;
        } catch { /* non-JSON error bodies are covered by the fail-closed tests */ }
      }
    });

    await openEligibilityWizard(page);

    const heading = page.locator('.select-heading');
    await expect(heading).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.questions-load-error')).toHaveCount(0);

    // The rendered heading must be the served text, not a compiled-in string.
    expect(servedFirst).toBeTruthy();
    await expect(heading).toContainText(servedFirst);
  });

  test('a failing question master blocks the wizard instead of guessing the rules', async ({ page }) => {
    // Deliberate: there is no hardcoded fallback. Serving a stale maintainability rule can wrongly
    // close a complaint as non-maintainable, which denies the citizen the Scheme.
    await page.route('**/api/v1/eligibility/questions*', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    await openEligibilityWizard(page);

    await expect(page.locator('.questions-load-error')).toBeVisible({ timeout: 20000 });
    // No question may be rendered, and there must be nothing to answer or advance past.
    await expect(page.locator('.select-heading')).toHaveCount(0);
    await expect(page.locator('.radio-option')).toHaveCount(0);
    await expect(page.locator('button.btn-next')).toBeDisabled();
  });

  test('an empty question master is treated as a failure, not as "no questions to ask"', async ({ page }) => {
    await page.route('**/api/v1/eligibility/questions*', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, schemeVersion: 'RBIOS_2021', data: [] }),
      }));

    await openEligibilityWizard(page);

    // An empty list must never be read as "every complaint is maintainable".
    await expect(page.locator('.questions-load-error')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.radio-option')).toHaveCount(0);
  });

  test('Retry re-fetches the master and recovers the wizard', async ({ page }) => {
    // Gated on an explicit flag rather than an attempt count: the component may issue more than one
    // initial fetch, and a count-based stub would let one of those succeed and clear the notice.
    let outageOver = false;
    let attemptsAfterRecovery = 0;
    await page.route('**/api/v1/eligibility/questions*', async route => {
      if (!outageOver) {
        return route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' });
      }
      attemptsAfterRecovery++;
      // fallback(), not continue(): the retry must reach the beforeEach API redirect.
      return route.fallback();
    });

    await openEligibilityWizard(page);
    await expect(page.locator('.questions-load-error')).toBeVisible({ timeout: 20000 });

    outageOver = true;
    await page.locator('button.btn-retry-questions').click();

    await expect.poll(() => attemptsAfterRecovery, { timeout: 20000 }).toBeGreaterThan(0);
    await expect(page.locator('.questions-load-error')).toHaveCount(0);
    await expect(page.locator('.select-heading')).toBeVisible({ timeout: 20000 });
  });

  test('an operator-authored question is rendered verbatim, with no frontend release', async ({ page }) => {
    // The point of D14: text the frontend has never seen must render. If any hardcoded copy were
    // still in play, the wizard would show the compiled-in wording instead of this.
    const authored = 'Operator authored this question in ELIGIBILITY_QUESTION_MASTER';
    await page.route('**/api/v1/eligibility/questions*', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          success: true,
          schemeVersion: 'RBIOS_2021',
          schemeName: 'Reserve Bank - Integrated Ombudsman Scheme, 2021',
          data: [{
            questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
            questionText: authored, translationKey: null, applicableEntityType: 'ALL',
            blockOn: null, blockMessage: '', nonMaintainable: false, inlineSubQuestion: false,
          }],
        }),
      }));

    await openEligibilityWizard(page);

    await expect(page.locator('.select-heading')).toHaveText(authored, { timeout: 20000 });
    await expect(page.locator('.questions-load-error')).toHaveCount(0);
  });
});
