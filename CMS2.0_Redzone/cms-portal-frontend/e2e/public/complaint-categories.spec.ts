import { test, expect } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * The citizen's category list must come from COMPLAINT_CATEGORIES and must be localised.
 *
 * <p>TWO DEFECTS ARE GUARDED HERE.
 *
 * <p>1. THE WRONG MASTER. `public/file-complaint` once read `/api/v1/masters/categories`
 * (CATEGORY_MASTER), a table whose only rows were inactive E2E probes named "S1 authority e2e probe"
 * left behind by a run that never cleaned up. A citizen filing a complaint was therefore sourcing the
 * category list from test pollution, and when the table was empty the wizard quietly substituted a
 * compiled-in array no master governed. The authoritative source is `/api/categories`.
 *
 * <p>2. ENGLISH-ONLY LABELS. COMPLAINT_CATEGORIES had a `name` column and nothing else, so the first
 * substantive choice in an eleven-locale product was English for everyone. `labelKey` now names a
 * translation key per category. `name` deliberately stays English: it is the value submitted, stored
 * and matched by routing, so translating it would change what is recorded.
 *
 * <p>The probe assertion is deliberately made against the RENDERED OPTIONS as well as the API. An API
 * check alone would pass if the component were repointed back at the polluted endpoint.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

/** Anything that looks like leftover test data has no business in a citizen-facing dropdown. */
const POLLUTION = /e2e|probe|test|dummy|sample|foo|bar/i;

/** The ten real RBI grievance categories, as seeded on 2026-06-26. */
const EXPECTED = [
  'ATM / Debit Card',
  'Credit Card',
  'Internet Banking',
  'Mobile Banking / UPI',
  'Loan / Advances',
  'Deposit Accounts',
  'Pension',
  'Remittance / Transfer',
  'Insurance',
  'Others',
];

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

/**
 * Opens the wizard directly on step 3 of the FORM phase, which is where the category select lives.
 *
 * <p>Reaching it by clicking would mean driving the whole eligibility questionnaire first — a long,
 * brittle path through master-data-driven questions that has nothing to do with what these tests
 * assert. The component restores a sessionStorage draft instead, but TWO conditions must both hold or
 * the page silently reopens at the eligibility phase with no category select on it:
 *
 * <ul>
 *   <li>`?resume=true` must be present. Without it `ngOnInit` does not merely skip the draft, it
 *       DELETES it (`sessionStorage.removeItem`), so the seeded state is gone before the form renders.
 *   <li>`version` must equal the component's DRAFT_VERSION, or the draft is discarded as stale.
 * </ul>
 */
const DRAFT_VERSION = 4;

async function openCategoryStep(page: any, lang?: string) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500001', 'e2e-seeded-token.sig');

  // The locale is read from localStorage, NOT from a ?lang= query parameter — there is no such
  // parameter. Seeding it before navigation is what makes TranslationService pick it up on construction.
  await page.addInitScript(([version, locale]: [number, string | undefined]) => {
    sessionStorage.setItem('cms_complaint_draft', JSON.stringify({
      version,
      phase: 'form',
      currentStep: 3,
      formData: {},
      eligibilityAnswers: {},
    }));
    if (locale) localStorage.setItem('cms_locale', locale);
  }, [DRAFT_VERSION, lang] as [number, string | undefined]);

  await page.goto(`${APP_BASE}/public/file-complaint?resume=true`);

  // Wait on the control itself, not on networkidle: the select renders only after both the draft
  // restore and the category fetch have resolved, and a load-state wait can win that race.
  await page.getByTestId('complaint-category').waitFor({ state: 'visible' });
}

test.describe('Complaint categories — authoritative master and localised labels', () => {

  test('the authoritative endpoint serves the ten real categories, each with a labelKey', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/categories`);
    expect(res.status()).toBe(200);

    const body = await res.json();
    const rows = Array.isArray(body) ? body : (body.data ?? []);
    const active = rows.filter((r: any) => (r.status ?? 'active') === 'active');

    expect(active.length, 'COMPLAINT_CATEGORIES should hold the ten seeded categories').toBe(10);
    expect(active.map((r: any) => r.name).sort()).toEqual([...EXPECTED].sort());

    for (const row of active) {
      expect(row.labelKey, `${row.name} has no labelKey, so it cannot be localised`).toBeTruthy();
      expect(row.labelKey).toMatch(/^category\./);
    }
  });

  test('no category name looks like test pollution', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/categories`);
    const body = await res.json();
    const rows = Array.isArray(body) ? body : (body.data ?? []);

    for (const row of rows) {
      expect(row.name, `category "${row.name}" looks like leftover test data`).not.toMatch(POLLUTION);
    }
  });

  test('every labelKey resolves in all eleven locales, including Punjabi', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/categories`);
    const rows = await res.json();
    const keys: string[] = (Array.isArray(rows) ? rows : rows.data ?? [])
      .map((r: any) => r.labelKey)
      .filter(Boolean);

    expect(keys.length).toBe(10);

    for (const locale of ['en', 'hi', 'bn', 'mr', 'te', 'ta', 'gu', 'ur', 'kn', 'ml', 'pa']) {
      const bundleRes = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(bundleRes.status(), `${locale} bundle did not load`).toBe(200);
      const payload = await bundleRes.json();
      const bundle = payload.translations ?? payload;

      for (const key of keys) {
        const value = bundle[key];
        expect(value, `${key} is missing from the ${locale} bundle`).toBeTruthy();
        // A bundle that echoes the key back is a miss, not a translation, and would put
        // "category.atm_debit_card" in front of a citizen.
        expect(value, `${key} resolves to itself in ${locale}`).not.toBe(key);
      }
    }
  });

  test('a non-English locale renders non-English category options', async ({ request }) => {
    // Proves the keys carry genuinely different text rather than ten copies of the English.
    const [enRes, paRes] = await Promise.all([
      request.get(`${API_BASE}/api/v1/i18n/translations/en`),
      request.get(`${API_BASE}/api/v1/i18n/translations/pa`),
    ]);
    const en = (await enRes.json()).translations ?? await enRes.json();
    const pa = (await paRes.json()).translations ?? await paRes.json();

    const differing = Object.keys(en)
      .filter(k => k.startsWith('category.'))
      .filter(k => pa[k] && pa[k] !== en[k]);

    expect(differing.length, 'Punjabi category labels are identical to English').toBe(10);
    // Gurmukhi must actually be present, not transliterated Latin.
    expect(pa['category.atm_debit_card']).toMatch(/[਀-੿]/);
  });

  test('the citizen form renders the real categories and no probe rows', async ({ page }) => {
    await openCategoryStep(page);

    const select = page.getByTestId('complaint-category');
    await expect(select).toBeVisible();

    // The fail-closed error state must NOT be showing: it would mean the master could not be read.
    await expect(page.getByTestId('categories-unavailable')).toHaveCount(0);

    const options = await select.locator('option').allTextContents();
    const real = options.filter(o => o.trim().length > 0).slice(1);

    expect(real.length, 'the dropdown should offer the ten categories').toBe(10);
    for (const option of real) {
      expect(option, `rendered option "${option}" looks like leftover test data`).not.toMatch(POLLUTION);
    }
  });

  test('the submitted VALUE stays the English name even when the label is localised', async ({ page }) => {
    // The payload sends `category` as a string and derives `subject` from it, so localising the value
    // would change what is stored and what routing matches. Only the label may be translated.
    await openCategoryStep(page, 'pa');

    const values = await page.getByTestId('complaint-category')
      .locator('option')
      .evaluateAll(els => els.map(e => (e as HTMLOptionElement).value).filter(Boolean));

    expect(values.sort()).toEqual([...EXPECTED].sort());
  });

  test('the rendered LABEL is localised even though the value is not', async ({ page }) => {
    // The counterpart to the test above, and the one that actually proves labelKey is piped through
    // to the view. Without it, the whole localisation change could be inert and every other test here
    // would still pass: the API serves the keys and the bundles resolve regardless of what the
    // component does with them.
    await openCategoryStep(page, 'pa');

    const options = await page.getByTestId('complaint-category')
      .locator('option')
      .evaluateAll(els => els
        .map(e => ({ value: (e as HTMLOptionElement).value, label: (e.textContent ?? '').trim() }))
        .filter(o => o.value));

    expect(options.length).toBe(10);
    for (const option of options) {
      expect(option.label, `"${option.value}" rendered its English name, not a Gurmukhi label`)
        .toMatch(/[਀-੿]/);
      expect(option.label, `"${option.value}" rendered a raw translation key`).not.toMatch(/^category\./);
    }
  });
});
