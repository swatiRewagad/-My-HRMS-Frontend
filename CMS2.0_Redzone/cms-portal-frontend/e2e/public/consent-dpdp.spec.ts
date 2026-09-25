import { test, expect } from '../fixtures';
import { buildComplaint, cleanupComplaint, loginCitizen, seedCitizenSession } from '../utils/test-data';

/**
 * UST5 — DPDP Act, 2023 consent declaration.
 *
 * Regression baseline: consent existed only as a [disabled] binding on the Send OTP / Submit
 * buttons. A direct POST filed a complaint with no consent at all, nothing was transmitted to the
 * server, nothing was recorded, and the notice was hardcoded English.
 *
 * Covers:
 *  - /api/v1/complaints rejects an ONLINE filing without declarationAccepted
 *  - offline intake channels (EMAIL / PHYSICAL_LETTER / WALK_IN) stay accepted — they never saw a checkbox
 *  - the notice on the login screen resolves from TRANSLATIONS, so it localises
 *  - send-otp carries consentGiven + locale, so the server can snapshot what was shown
 *  - the tracker does NOT claim consent, since it collects none
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const CITIZEN_PHONE = '9876500033';
// A distinct number for the wizard test: UST8's resend cooldown is keyed per mobile, so reusing
// CITIZEN_PHONE here would make loginCitizen fail and silently skip the assertion.
const WIZARD_PHONE = '9876500044';

function onlinePayload(overrides: Record<string, unknown> = {}) {
  const base = buildComplaint({ complainantPhone: CITIZEN_PHONE });
  return {
    complainantName: base.complainantName,
    complainantEmail: base.complainantEmail,
    complainantPhone: base.complainantPhone,
    complainantAddress: base.complainantAddress,
    subject: base.subject,
    description: base.description,
    entityName: base.entityName,
    entityType: 'BANK',
    category: 'GENERAL',
    filingType: 'ONLINE',
    ...overrides,
  };
}

test.describe('UST5 — Consent is enforced server-side, not just in the UI', () => {

  test('an ONLINE filing with no declaration is rejected', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: onlinePayload(),
      headers: { 'Content-Type': 'application/json' },
    });

    expect(response.status()).toBe(400);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.message).toContain('declaration');
  });

  test('an ONLINE filing that explicitly declines the declaration is rejected', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: onlinePayload({ declarationAccepted: false }),
      headers: { 'Content-Type': 'application/json' },
    });

    expect(response.status()).toBe(400);
    expect((await response.json()).message).toContain('declaration');
  });

  test('an ONLINE filing with the declaration accepted succeeds', async ({ request }) => {
    const response = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: onlinePayload({ declarationAccepted: true }),
      headers: { 'Content-Type': 'application/json' },
    });

    expect(response.status()).toBeLessThan(300);
    const body = await response.json();
    const complaintNumber = (body.data || body).complaintId || (body.data || body).complaintNumber;
    expect(complaintNumber).toBeTruthy();

    await cleanupComplaint(request, complaintNumber);
  });

  /**
   * Email / physical-letter / walk-in intake never displayed a checkbox. Rejecting those would drop
   * legitimate complaints on the floor, so the gate must remain online-only.
   */
  for (const filingType of ['EMAIL', 'PHYSICAL_LETTER', 'WALK_IN']) {
    test(`${filingType} intake is accepted without a declaration`, async ({ request }) => {
      const response = await request.post(`${API_BASE}/api/v1/complaints`, {
        data: onlinePayload({ filingType }),
        headers: { 'Content-Type': 'application/json' },
      });

      expect(response.status()).toBeLessThan(300);
      const body = await response.json();
      const complaintNumber = (body.data || body).complaintId || (body.data || body).complaintNumber;
      expect(complaintNumber).toBeTruthy();

      await cleanupComplaint(request, complaintNumber);
    });
  }
});

test.describe('UST5 — The notice is localised and recorded', () => {

  test('the login consent notice comes from TRANSLATIONS, not hardcoded English', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/login`);
    await page.waitForLoadState('networkidle');

    const notice = page.locator('.consent-section .consent-label span');
    await expect(notice).toBeVisible({ timeout: 15000 });

    // The raw key must never leak to the citizen — that would mean the seed is missing.
    await expect(notice).not.toHaveText('consent.dpdp_notice');
    await expect(notice).toContainText(/Digital Personal Data Protection Act, 2023/i);
  });

  test('the notice is served for every supported locale', async ({ request }) => {
    // Nine Indian locales plus English. A missing entry would make the snapshotted consent text
    // differ from what the citizen actually read.
    for (const locale of ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml']) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.ok(), `translations missing for ${locale}`).toBe(true);

      // TranslationController returns the key/value map directly, with no envelope.
      const map: Record<string, string> = await res.json();
      const notice = map['consent.dpdp_notice'];

      expect(notice, `consent.dpdp_notice missing for ${locale}`).toBeTruthy();
      expect(notice).not.toBe('consent.dpdp_notice');
    }
  });

  test('send-otp transmits consentGiven and locale so the server can record what was shown', async ({ page }) => {
    const payloads: string[] = [];
    page.on('request', req => {
      if (req.url().includes('/send-otp')) payloads.push(req.postData() || '');
    });

    await page.goto(`${APP_BASE}/public/login`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('button[aria-label="Get a new CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    await page.locator('#mobile-input').fill(CITIZEN_PHONE);
    await page.locator('.captcha-input').fill('WRONG1');
    await page.locator('.consent-section input[type="checkbox"]').check();
    await page.locator('button.send-otp-btn').click();

    await expect.poll(() => payloads.length, { timeout: 15000 }).toBeGreaterThan(0);
    expect(payloads[0]).toContain('consentGiven');
    expect(payloads[0]).toContain('"consentGiven":"true"');
    expect(payloads[0]).toContain('locale');
  });

  test('send-otp is blocked client-side until the declaration is accepted', async ({ page }) => {
    let sendOtpCalled = false;
    page.on('request', req => {
      if (req.url().includes('/send-otp')) sendOtpCalled = true;
    });

    await page.goto(`${APP_BASE}/public/login`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('button[aria-label="Get a new CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    await page.locator('#mobile-input').fill(CITIZEN_PHONE);
    await page.locator('.captcha-input').fill('1234');
    // Deliberately leave the consent box unchecked.
    //
    // The gate used to be `[disabled]="!consentChecked || ..."`. It is now the guard inside sendOtp(), so
    // the button is clickable and the refusal is stated — what matters for DPDP is unchanged and is what
    // this test checks: no /send-otp request is made without consent.
    await page.locator('button.send-otp-btn').click();
    await expect(page.locator('.error-banner'))
      .toContainText(/must accept the data processing declaration/i);
    expect(sendOtpCalled).toBe(false);
  });

  /**
   * The tracker shares /send-otp with the login flow but collects no consent, because it only reads
   * back a complaint the citizen already filed. It must not assert a consent that was never given.
   */
  test('the tracker sends consentGiven=false — it collects no consent', async ({ page }) => {
    const payloads: string[] = [];
    page.on('request', req => {
      if (req.url().includes('/send-otp')) payloads.push(req.postData() || '');
    });

    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    await page.locator('input[aria-label="Mobile number"]').fill(CITIZEN_PHONE);
    await page.locator('input[aria-label="CAPTCHA answer"]').fill('WRONG1');
    await page.locator('button:has-text("Send OTP")').click();

    await expect.poll(() => payloads.length, { timeout: 15000 }).toBeGreaterThan(0);
    expect(payloads[0]).toContain('"consentGiven":"false"');
  });
});

test.describe('UST5 — The wizard cannot submit without the declaration', () => {

  test('the public wizard route requires a citizen session', async ({ page }) => {
    // The wizard is guarded, so an unauthenticated caller can never reach the declaration step at all.
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');
    await expect(page).toHaveURL(/\/public\/login/);
  });

  /**
   * The legacy /file-complaint form also posts filingType: ONLINE. It had no declaration at all, so
   * the new server gate would have rejected every submission through it — it now carries the same
   * checkbox, and this test pins both the gate and the payload.
   */
  test('the legacy form blocks submit until the declaration is accepted, then sends it', async ({ page }) => {
    await page.goto(`${APP_BASE}/file-complaint`);
    await page.waitForLoadState('networkidle');

    const consentBox = page.locator('.consent-label input[type="checkbox"]');
    await expect(consentBox).toBeVisible({ timeout: 15000 });

    // The notice must localise here too — a raw key would mean the pipe is not wired.
    await expect(page.locator('.consent-label span')).not.toHaveText('consent.dpdp_notice');

    const submitBtn = page.locator('button.submit-btn');
    await expect(submitBtn).toBeDisabled();

    // Fill the fields isValid requires, and confirm the declaration alone still gates submission.
    await page.locator('input[name="name"]').fill('Asha Menon');
    await page.locator('input[name="phone"]').fill(WIZARD_PHONE);
    await page.locator('select[name="category"]').selectOption({ index: 1 });
    await page.locator('input[name="entityName"]').fill('Test Bank Ltd');
    await page.locator('input[name="subject"]').fill('Unauthorised debit');
    await page.locator('textarea[name="description"]').fill('An amount was debited without my authorisation.');

    await expect(submitBtn).toBeDisabled();

    const payloads: string[] = [];
    page.on('request', req => {
      if (req.url().endsWith('/api/v1/complaints') && req.method() === 'POST') {
        payloads.push(req.postData() || '');
      }
    });

    await consentBox.check();
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();

    await expect.poll(() => payloads.length, { timeout: 20000 }).toBeGreaterThan(0);
    expect(payloads[0]).toContain('"declarationAccepted":true');
  });
});
