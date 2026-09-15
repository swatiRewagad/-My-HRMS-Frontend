import { test, expect, Page } from '@playwright/test';
import { seedCitizenSession } from '../utils/test-data';

/**
 * ELIGIBILITY_QUESTION_MASTER.clauseReference is the single source of truth for Scheme clause
 * citations. The block-message prose carries a {{clause}} placeholder which the portal interpolates,
 * so correcting a citation is one DB UPDATE instead of ten locale edits across six scripts.
 *
 * Previously the digits were baked into the translated prose in all ten locales, and Bengali stored
 * them in Bengali numerals (১০(১)(জে)) — so an ASCII find/replace silently skipped it.
 *
 * Clause VALUES are still unverified against the published Scheme, and the repo contains two
 * contradictory mappings (EligibilityQuestionMasterSeeder vs V3__mre_and_pincode_seed). These tests
 * therefore assert the PLUMBING — that whatever clause the master holds is what the citizen is shown —
 * and deliberately do NOT assert any particular clause number.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

/** A blocking question whose clause and message are supplied entirely by the test. */
function blockingQuestion(clauseReference: string | null) {
  return {
    questionNumber: 2, questionKey: 'filedWithRE', questionType: 'radio',
    questionText: 'Have you filed a complaint with the Regulated Entity?',
    applicableEntityType: 'ALL', blockOn: 'no',
    // No translationKey, so this prose is what renders — isolating the interpolation from the seeds.
    blockMessage: 'Closed as non-maintainable under clause {{clause}} of the Scheme.',
    blockMessageKey: null,
    clauseReference,
    nonMaintainable: true, inlineSubQuestion: false,
  };
}

async function stubMaster(page: Page, question: Record<string, unknown>) {
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
          question,
        ],
      }),
    }));
}

async function openAndBlock(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500077', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');

  const select = page.locator('select.entity-select-dropdown');
  await expect(select).toBeVisible({ timeout: 20000 });
  await expect(select.locator('option:not([disabled])').first()).toBeAttached({ timeout: 20000 });
  const value = await select.locator('option:not([disabled])').first().getAttribute('value');
  await select.selectOption(value!);
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });

  // YES_NO_OPTIONS is [yes, no]; the template binds [value] so there is no value attribute to select on.
  await page.locator('.radio-list .radio-option').nth(1).click();
  await expect(page.locator('.block-note')).toBeVisible();
}

test.describe('Scheme clause is interpolated from the master, not baked into prose', () => {

  test('the clause held by the master is what the citizen is shown', async ({ page }) => {
    // A value the frontend has never seen: it can only appear if it came from clauseReference.
    await stubMaster(page, blockingQuestion('99(9)(z)'));
    await openAndBlock(page);

    const note = page.locator('.block-note');
    await expect(note).toContainText('clause 99(9)(z)');
    await expect(note, 'the raw placeholder must never reach a citizen').not.toContainText('{{clause}}');
  });

  test('correcting the clause in the master changes the citizen-facing text with no code change', async ({ page }) => {
    // This is the whole point of the refactor — one data change, no locale edits, no release.
    await stubMaster(page, blockingQuestion('10(2)(b)(ii)'));
    await openAndBlock(page);
    await expect(page.locator('.block-note')).toContainText('clause 10(2)(b)(ii)');
  });

  test('a missing clause omits the citation instead of showing a raw placeholder', async ({ page }) => {
    // Fail closed on presentation: some rows legitimately have no verified clause yet. Showing a
    // literal "{{clause}}" in a legal determination is worse than omitting the citation.
    await stubMaster(page, blockingQuestion(null));
    await openAndBlock(page);

    const note = page.locator('.block-note');
    await expect(note).not.toContainText('{{clause}}');
    await expect(note).toContainText('non-maintainable');
  });

  test('the placeholder is replaced everywhere it occurs, not only the first time', async ({ page }) => {
    // TranslationService.translate() interpolates with String.replace and a STRING pattern, which
    // substitutes only the first occurrence. The component therefore uses a global regex instead —
    // this pins that, because a leaked second placeholder would be citizen-visible.
    await stubMaster(page, {
      ...blockingQuestion('10(1)(j)'),
      blockMessage: 'Non-maintainable under clause {{clause}}; see clause {{clause}} of the Scheme.',
    });
    await openAndBlock(page);

    const text = await page.locator('.block-note').innerText();
    expect(text).not.toContain('{{clause}}');
    expect(text.match(/10\(1\)\(j\)/g)?.length, 'both placeholders should be filled').toBe(2);
  });

  test('the served prose carries the placeholder rather than baked-in digits', async ({ request }) => {
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

    for (const q of body.data) {
      if (!q.blockMessage) continue;
      // A clause-bearing message must cite via the placeholder, never via literal digits.
      const hasBakedClause = /\b\d+\(\d+\)(\([a-z]+\))*/i.test(q.blockMessage);
      expect(hasBakedClause, `${q.questionKey} still bakes a clause into its prose: ${q.blockMessage}`)
        .toBe(false);
    }
  });

  test('no locale bakes a clause into the block-message prose', async ({ request }) => {
    // The regression guard for the corrective migration (V15 / oracle V14). Bengali is included
    // because it stores the digits in Bengali numerals, which an ASCII check would skip.
    const CLAUSE_KEYS = ['eligibility.block_not_filed', 'eligibility.block_sub_judice'];
    const BAKED = [/10\(1\)\(j\)/, /10\(2\)\(b\)\(ii\)/, /১০\(১\)/, /১০\(২\)/];

    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;

      for (const key of CLAUSE_KEYS) {
        const value = t[key];
        if (!value) continue;
        for (const pattern of BAKED) {
          expect(pattern.test(value), `${key} for ${locale} still bakes a clause: ${value}`).toBe(false);
        }
      }
    }
  });

  test('the clause placeholder survives translation in every locale that cites one', async ({ request }) => {
    // block_not_filed cites a clause in all ten locales, so every one must carry the placeholder —
    // otherwise that locale silently drops the citation after the migration.
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      const value = t['eligibility.block_not_filed'];
      expect(value, `block_not_filed missing for ${locale}`).toBeTruthy();
      expect(value, `${locale} lost its clause placeholder`).toContain('{{clause}}');
    }
  });
});
