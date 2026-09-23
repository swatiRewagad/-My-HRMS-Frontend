import { test, expect } from '../fixtures';

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8092';

/** The keys added for the shared shell, navigation and common UI vocabulary. */
const SHELL_KEYS = [
  'ui.shell.cms_full',
  'ui.shell.logout',
  'ui.shell.collapse',
  'ui.nav.crpc_complaints',
  'ui.nav.rbio_workbench',
  'ui.common.search',
  'ui.common.save',
  'ui.common.cancel',
  'ui.upload.error_file_too_large',
];

/**
 * Localisation assertions for the homogenisation batch.
 *
 * Levelling UP is the requirement: CRPC and CEPC had ZERO uses of the translate pipe, so their chrome
 * could never be localised. These tests fail if a new key is missing, if it renders as a raw key, or
 * if a locale silently falls back to English.
 */
test.describe('Shell localisation', () => {
  test('every new shell key exists in English with real text, not a key echo', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/i18n/translations/en`);
    expect(res.ok()).toBeTruthy();
    const bundle = await res.json();

    for (const key of SHELL_KEYS) {
      expect(bundle[key], `${key} must be seeded`).toBeTruthy();
      // The pipe echoes the key when a bundle entry is absent, so a value equal to its own key is the
      // signature of an unseeded key rather than of a translation.
      expect(bundle[key], `${key} must not render as its own key`).not.toBe(key);
    }
  });

  /**
   * The ten locales the server serves. `pa` joined this list when PA was added to the SupportedLocale
   * enum — until then TranslationService resolved it to "en", so the 68 genuine Gurmukhi rows in the
   * database were never reachable and Punjabi appeared "identical to English" by construction.
   */
  const SERVED_LOCALES = ['hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml', 'pa'];

  for (const locale of SERVED_LOCALES) {
    test(`shell keys are genuinely translated in ${locale}, not English passed through`, async ({ request }) => {
      const [enRes, locRes] = await Promise.all([
        request.get(`${API_BASE}/api/v1/i18n/translations/en`),
        request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`),
      ]);
      expect(locRes.ok()).toBeTruthy();
      const en = await enRes.json();
      const loc = await locRes.json();

      // ui.shell.cms_short is "CMS" in every locale by design (it is an acronym), so it is not asserted.
      const translatable = SHELL_KEYS.filter(k => k !== 'ui.shell.cms_short');

      for (const key of translatable) {
        expect(loc[key], `${key} missing in ${locale}`).toBeTruthy();
        expect(
          loc[key],
          `${key} in ${locale} is byte-identical to English, i.e. untranslated passthrough`
        ).not.toBe(en[key]);
      }
    });
  }

  /**
   * Punjabi, formerly a known gap, now asserted POSITIVELY. This test was previously the inverse — it
   * asserted `pa` was absent from /locales — because `pa` was missing from the SupportedLocale enum and
   * `TranslationService` resolved it to "en". The enum member has been added, so the assertion is
   * inverted rather than deleted: a regression that drops `pa` again must fail here.
   */
  test('Punjabi is offered by the API so the switcher can present it', async ({ request }) => {
    const locales = await (await request.get(`${API_BASE}/api/v1/i18n/locales`)).json();
    const codes = locales.map((l: { code: string }) => l.code);
    expect(codes, 'pa must be a served locale').toContain('pa');

    const pa = locales.find((l: { code: string }) => l.code === 'pa');
    // The native name is what the switcher renders, so an English-only entry is a half-done job.
    expect(pa.nativeName, 'Punjabi must present its own name in Gurmukhi').toMatch(/[਀-੿]/);
  });

  /**
   * The narrow, load-bearing assertion: the served `pa` bundle must contain real Gurmukhi script.
   *
   * The generic SERVED_LOCALES loop above only proves a value differs from English, which a mojibake or
   * placeholder string would also satisfy. Checking the Unicode block proves the DB rows are actually
   * being reached rather than the English bundle being returned under a `pa` cache key.
   */
  test('the Punjabi bundle is genuinely Gurmukhi, not English passed through', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/i18n/translations/pa`);
    expect(res.ok()).toBeTruthy();
    const bundle = await res.json();

    const gurmukhi = /[਀-੿]/;
    for (const key of ['ui.shell.cms_full', 'ui.shell.logout', 'ui.common.search']) {
      expect(bundle[key], `${key} must be served in Gurmukhi for pa`).toMatch(gurmukhi);
    }
  });

  /**
   * An unseeded key must fall back to ENGLISH, never render as a raw code. Only 68 of ~1876 keys carry
   * a `pa` row, so this is the overwhelmingly common path for Punjabi and the one a citizen will hit.
   */
  test('a key with no Punjabi row falls back to English rather than a raw code', async ({ request }) => {
    const [enRes, paRes] = await Promise.all([
      request.get(`${API_BASE}/api/v1/i18n/translations/en`),
      request.get(`${API_BASE}/api/v1/i18n/translations/pa`),
    ]);
    const en = await enRes.json();
    const pa = await paRes.json();

    // "Unseeded" is derived from the response rather than hardcoded: a key whose pa value carries no
    // Gurmukhi has no pa row, so it is exercising the fallback path.
    const gurmukhi = /[਀-੿]/;
    const unseeded = Object.keys(en).filter(k => !gurmukhi.test(String(pa[k] ?? '')));
    expect(unseeded.length, 'expected most keys to have no pa row').toBeGreaterThan(0);

    for (const key of unseeded.slice(0, 200)) {
      expect(pa[key], `${key} must be present in the pa bundle`).toBeTruthy();
      expect(pa[key], `${key} must not render as its own key in pa`).not.toBe(key);
      expect(pa[key], `${key} must fall back to the English text`).toBe(en[key]);
    }
  });

  test('the upload limit message interpolates the configured size rather than baking a digit', async ({ request }) => {
    const bundle = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const message = bundle['ui.upload.error_file_too_large'];

    expect(message).toContain('{{size}}');
    // A baked digit is the defect: the hint said 5MB while enforcement was 2MB because the text
    // carried its own number and could not track configuration.
    expect(message, 'the limit must be interpolated, never literal').not.toMatch(/\d+\s*MB/i);
  });

  test('the served limit and the citizen-facing hint agree', async ({ request }) => {
    const limits = await (await request.get(`${API_BASE}/api/v1/config/upload-limits`)).json();
    expect(limits.maxFileSizeMb).toBe(5);
    expect(limits.maxTotalSizeMb).toBe(25);
    // 50MB is Spring's servlet cap and must remain strictly above the product rule, or a rejection
    // surfaces as a container error instead of a translated message.
    expect(limits.maxFileSizeBytes).toBeLessThan(50 * 1024 * 1024);
  });

  /**
   * CLAUSE LABELS MUST STAY ENGLISH-ONLY — a standing legal ruling, guarded here.
   *
   * A clause label is an operative statement of what a Scheme clause MEANS, so a machine translation of
   * one is a legal assertion nobody has signed off. The mechanism that keeps this true is subtle and
   * easy to break by accident: all `clause.*` keys carry English in TRANSLATION_KEYS.DEFAULT_VALUE and
   * have ZERO rows in TRANSLATIONS, so TranslationService's putIfAbsent fallback serves the English
   * default into every locale bundle. Adding a locale (as `pa` just was) must NOT sweep these into a
   * translation pass.
   *
   * This asserts every served locale returns byte-identical English for every clause key.
   */
  test('clause labels are English-only in every served locale', async ({ request }) => {
    const enBundle = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const clauseKeys = Object.keys(enBundle).filter(k => k.startsWith('clause.'));

    // Guards the guard: if the key prefix is ever renamed this test must fail loudly, not vacuously pass.
    expect(clauseKeys.length, 'expected clause.* keys to exist').toBeGreaterThan(0);

    for (const locale of ['en', ...SERVED_LOCALES]) {
      const bundle = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      for (const key of clauseKeys) {
        expect(
          bundle[key],
          `${key} must remain the English label in ${locale} — a translated clause label is a legal statement`
        ).toBe(enBundle[key]);
      }
    }
  });

  /**
   * The two First-Resolution clauses must be citable, because AutoClosureService now resolves its
   * citation through CLOSURE_CLAUSE_MASTER and omits it entirely when the clause is absent. A missing
   * row would silently drop the clause from citizen-facing closure text rather than erroring.
   */
  test('the FRC closure clauses carry English labels', async ({ request }) => {
    const bundle = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();

    for (const key of ['clause.10_1_e', 'clause.10_1_g']) {
      expect(bundle[key], `${key} must be seeded`).toBeTruthy();
      expect(bundle[key], `${key} must not render as its own key`).not.toBe(key);
    }
  });
});
