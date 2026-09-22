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
   * The nine locales that the server actually serves. Punjabi is deliberately excluded here and
   * asserted separately below: it is absent from the SupportedLocale enum, so the API resolves it to
   * English no matter what the database holds.
   */
  const SERVED_LOCALES = ['hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

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
   * Punjabi is a KNOWN GAP, asserted so it cannot be mistaken for working.
   *
   * The rows exist in the database and are real Gurmukhi, but `pa` is not a member of SupportedLocale,
   * so TranslationService resolves it to "en" and serves English. This is why Punjabi appeared to be
   * "100% identical to English" — the comparison was against a bundle that was English by construction.
   * Adding the enum member is a backend change outside this batch; this test documents the state.
   */
  test('Punjabi is not served by the API even though rows exist — known gap', async ({ request }) => {
    const locales = await (await request.get(`${API_BASE}/api/v1/i18n/locales`)).json();
    const codes = locales.map((l: { code: string }) => l.code);
    expect(
      codes,
      'If pa has been added to SupportedLocale, delete this test and add pa to SERVED_LOCALES above'
    ).not.toContain('pa');
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
});
