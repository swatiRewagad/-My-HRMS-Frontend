import { test, expect } from '../fixtures';
import { createTestComplaint, cleanupComplaint, loginCitizen } from '../utils/test-data';

/**
 * UST98 / UST99: Complaint tracking authorization (D1) + real CAPTCHA OTP flow (D2/D3).
 *
 * Covers:
 *  - GET /api/v1/complaints (list) requires a valid citizen session token
 *  - A citizen session may only list its own mobile's complaints
 *  - Complainant PII in the detail response is masked for anonymous callers
 *  - Withdraw requires ownership
 *  - The tracker sends a real CAPTCHA (no 'track-bypass') to the correct
 *    /api/v1/citizen/auth/* path
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || 'http://localhost:4200';

const OWNER_PHONE = '9876500011';
const OTHER_PHONE = '9876500022';
// UST4/UST6 cool-off is keyed on fingerprint+IP *and* on the mobile number, so any test that
// deliberately fails a CAPTCHA must use a number no other test relies on — otherwise the next
// send-otp for that number returns 429 COOLOFF_ACTIVE instead of the status under test.
const CAPTCHA_PROBE_PHONE = '9876500077';
const UI_CAPTCHA_PROBE_PHONE = '9876500088';

test.describe('UST98 — Tracking authorization (D1)', () => {

  test('listing complaints without a citizen session returns 401', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/v1/complaints?phone=${OWNER_PHONE}`);

    expect(response.status()).toBe(401);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.error).toBe('SESSION_EXPIRED');
  });

  test('listing complaints with an invalid/forged token returns 401', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/v1/complaints?phone=${OWNER_PHONE}`, {
      headers: { 'X-Citizen-Token': 'forged-token.forged-signature' },
    });

    expect(response.status()).toBe(401);
    expect((await response.json()).error).toBe('SESSION_EXPIRED');
  });

  test('a citizen cannot list another mobile number\'s complaints', async ({ request }) => {
    const token = await loginCitizen(request, OWNER_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const response = await request.get(`${API_BASE}/api/v1/complaints?phone=${OTHER_PHONE}`, {
      headers: { 'X-Citizen-Token': token! },
    });

    expect(response.status()).toBe(403);
    const body = await response.json();
    expect(body.error).toBe('FORBIDDEN');
    expect(body.message).toContain('your own mobile number');
  });

  test('a citizen sees only its own complaints, even with no phone param', async ({ request }) => {
    const token = await loginCitizen(request, OWNER_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const response = await request.get(`${API_BASE}/api/v1/complaints`, {
      headers: { 'X-Citizen-Token': token! },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.success).toBe(true);
    expect(Array.isArray(body.data.content)).toBe(true);
    expect(typeof body.data.totalElements).toBe('number');
  });

  test('page size is capped at 50 regardless of the requested size', async ({ request }) => {
    const token = await loginCitizen(request, OWNER_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const response = await request.get(`${API_BASE}/api/v1/complaints?size=5000`, {
      headers: { 'X-Citizen-Token': token! },
    });

    expect(response.status()).toBe(200);
    const body = await response.json();
    expect(body.data.content.length).toBeLessThanOrEqual(50);
  });
});

test.describe('UST99 — Detail endpoint PII masking (D1)', () => {

  test('anonymous tracking by reference number works but masks complainant PII', async ({ request }) => {
    const result = await createTestComplaint(request, {
      subject: 'E2E Tracking PII Mask Test',
      complainantName: 'Rajesh Kumar',
      complainantPhone: OWNER_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`);
      expect(response.status()).toBe(200);

      const detail = (await response.json()).data;
      // Status tracking still works for the anonymous citizen (UST99)
      expect(detail.complaintId).toBe(complaintNumber);
      expect(detail.status).toBeTruthy();

      // ...but the complainant's identity must not be readable
      expect(detail.complainantName).not.toBe('Rajesh Kumar');
      expect(detail.complainantName).toBe('R*****');
      expect(detail.complainantPhone).not.toBe(OWNER_PHONE);
      expect(detail.complainantPhone).toBe('******0011');
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('the owning citizen sees unmasked PII on their own complaint', async ({ request }) => {
    const token = await loginCitizen(request, OWNER_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const result = await createTestComplaint(request, {
      subject: 'E2E Tracking Owner PII Test',
      complainantName: 'Rajesh Kumar',
      complainantPhone: OWNER_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
        headers: { 'X-Citizen-Token': token! },
      });
      expect(response.status()).toBe(200);

      const detail = (await response.json()).data;
      expect(detail.complainantName).toBe('Rajesh Kumar');
      expect(detail.complainantPhone).toBe(OWNER_PHONE);
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('an unknown reference number returns the AC error message', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/v1/complaints/NONEXISTENT-${Date.now()}`);

    expect(response.status()).toBe(404);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.message).toBe('Complaint number not found, please check and try again.');
  });
});

test.describe('UST105 — Withdraw requires ownership', () => {

  test('withdrawing without a citizen session returns 401', async ({ request }) => {
    const result = await createTestComplaint(request, {
      subject: 'E2E Withdraw Authz Test',
      complainantPhone: OWNER_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      const response = await request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
        data: { reason: 'Issue resolved by the Regulated Entity', remarks: '' },
        headers: { 'Content-Type': 'application/json' },
      });

      expect(response.status()).toBe(401);
      expect((await response.json()).error).toBe('SESSION_EXPIRED');
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });

  test('a citizen cannot withdraw another complainant\'s complaint', async ({ request }) => {
    const token = await loginCitizen(request, OTHER_PHONE);
    test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

    const result = await createTestComplaint(request, {
      subject: 'E2E Cross-Citizen Withdraw Test',
      complainantPhone: OWNER_PHONE,
    });
    const complaintNumber = result.complaintNumber;

    try {
      const response = await request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
        data: { reason: 'Issue resolved by the Regulated Entity', remarks: '' },
        headers: { 'Content-Type': 'application/json', 'X-Citizen-Token': token! },
      });

      expect(response.status()).toBe(403);
      expect((await response.json()).error).toBe('FORBIDDEN');
    } finally {
      await cleanupComplaint(request, complaintNumber);
    }
  });
});

test.describe('UST98 — Tracker OTP flow uses a real CAPTCHA (D2/D3)', () => {

  test('the mobile tracking tab renders a CAPTCHA challenge', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');

    await page.locator('.mode-tab:has-text("Mobile")').click();

    const captchaSection = page.locator('.captcha-section');
    await expect(captchaSection).toBeVisible({ timeout: 15000 });
    await expect(captchaSection.locator('input[aria-label="CAPTCHA answer"]')).toBeVisible();
    await expect(captchaSection.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible();
  });

  test('the CAPTCHA is fetched from /api/v1/citizen/auth/captcha, not citizen-auth', async ({ page }) => {
    const captchaUrls: string[] = [];
    page.on('request', req => {
      if (req.url().includes('captcha')) captchaUrls.push(req.url());
    });

    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    // The refresh button only renders once the challenge has arrived; the section
    // itself renders immediately, and loadCaptcha() clears any answer typed early.
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    expect(captchaUrls.length).toBeGreaterThan(0);
    expect(captchaUrls.some(u => u.includes('/api/v1/citizen/auth/captcha'))).toBe(true);
    expect(captchaUrls.some(u => u.includes('/api/v1/citizen-auth/'))).toBe(false);
  });

  test('send-otp posts a real CAPTCHA answer — never the track-bypass token', async ({ page }) => {
    const payloads: string[] = [];
    page.on('request', req => {
      if (req.url().includes('/send-otp')) {
        payloads.push(req.postData() || '');
      }
    });

    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    // The refresh button only renders once the challenge has arrived; the section
    // itself renders immediately, and loadCaptcha() clears any answer typed early.
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    await page.locator('input[aria-label="Mobile number"]').fill(UI_CAPTCHA_PROBE_PHONE);
    await page.locator('input[aria-label="CAPTCHA answer"]').fill('WRONG1');
    await page.locator('button:has-text("Send OTP")').click();

    // The request must reach the correct endpoint with the typed answer.
    await expect.poll(() => payloads.length, { timeout: 15000 }).toBeGreaterThan(0);
    expect(payloads[0]).not.toContain('track-bypass');
    expect(payloads[0]).toContain('WRONG1');

    // A wrong CAPTCHA must be rejected — the OTP box must not appear.
    await expect(page.locator('.error-message')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('input[placeholder="Enter 6-digit OTP"]')).not.toBeVisible();
  });

  test('send-otp is blocked client-side without a CAPTCHA answer', async ({ page }) => {
    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    // The refresh button only renders once the challenge has arrived; the section
    // itself renders immediately, and loadCaptcha() clears any answer typed early.
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    await page.locator('input[aria-label="Mobile number"]').fill(OWNER_PHONE);
    await page.locator('button:has-text("Send OTP")').click();

    await expect(page.locator('.error-message')).toContainText('CAPTCHA', { timeout: 10000 });
  });

  test('an invalid mobile number is rejected before any network call', async ({ page }) => {
    let sendOtpCalled = false;
    page.on('request', req => {
      if (req.url().includes('/send-otp')) sendOtpCalled = true;
    });

    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    // The refresh button only renders once the challenge has arrived; the section
    // itself renders immediately, and loadCaptcha() clears any answer typed early.
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    // Starts with 5 — outside the 6-9 range mandated for Indian mobiles
    await page.locator('input[aria-label="Mobile number"]').fill('5123456789');
    await page.locator('input[aria-label="CAPTCHA answer"]').fill('1234');
    await page.locator('button:has-text("Send OTP")').click();

    await expect(page.locator('.error-message')).toContainText('valid 10-digit', { timeout: 10000 });
    expect(sendOtpCalled).toBe(false);
  });

  test('a VISUAL CAPTCHA never returns its own answer (D16)', async ({ request }) => {
    // Regression: the response used to carry the plaintext answer in audioQuestion, so any
    // script could solve the challenge without ever rendering the image.
    // Issuing challenges is free, so the shape is checked across several to catch a leak that
    // only shows up for some generated images.
    let lastToken = '';
    for (let i = 0; i < 3; i++) {
      const res = await request.get(`${API_BASE}/api/v1/citizen/auth/captcha?type=VISUAL`);
      expect(res.status()).toBe(200);
      const body = await res.json();

      expect(body.type).toBe('VISUAL');
      expect(body.imageData).toContain('data:image/png;base64,');
      expect(body.audioQuestion).toBe('');

      // No extra field may be smuggled in alongside the four known keys.
      expect(Object.keys(body).sort()).toEqual(['audioQuestion', 'imageData', 'token', 'type']);
      lastToken = body.token;
    }

    // The token must not be usable as the answer either. UST4/UST6 made a wrong CAPTCHA count
    // towards the lockout, so this is attempted exactly once — a second attempt would return 429
    // from the cool-off rather than 400, and would prove nothing about the leak.
    const otp = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
      data: { mobile: CAPTCHA_PROBE_PHONE, captchaToken: lastToken, captchaAnswer: lastToken },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(otp.status()).toBe(400);
    expect((await otp.json()).error).toBe('INVALID_CAPTCHA');
  });

  test('a MATH CAPTCHA returns a speakable question but not the answer (D16)', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/v1/citizen/auth/captcha?type=MATH`);
    expect(res.status()).toBe(200);
    const body = await res.json();

    expect(body.type).toBe('MATH');
    // The question is safe to speak; the solution must still be computed by the caller.
    expect(body.audioQuestion).toMatch(/^What is \d+ (plus|minus) \d+\?$/);
    expect(body.imageData).toBe('');
  });

  test('the audio button falls back to a MATH challenge instead of reading the image answer (D16)', async ({ page }) => {
    const captchaTypes: string[] = [];
    page.on('request', req => {
      const url = req.url();
      if (url.includes('/citizen/auth/captcha')) {
        captchaTypes.push(new URL(url).searchParams.get('type') || '');
      }
    });

    await page.goto(`${APP_BASE}/public/track`);
    await page.waitForLoadState('networkidle');
    await page.locator('.mode-tab:has-text("Mobile")').click();
    await expect(page.locator('button[aria-label="Refresh CAPTCHA"]')).toBeVisible({ timeout: 15000 });

    expect(captchaTypes[0]).toBe('VISUAL');
    // The VISUAL challenge renders as an image with no readable text alternative.
    await expect(page.locator('img.captcha-image')).toBeVisible();

    await page.locator('button[aria-label="Play CAPTCHA audio"]').click();

    // Pressing play must re-fetch as MATH — that is the accessible, speakable challenge.
    await expect.poll(() => captchaTypes.includes('MATH'), { timeout: 15000 }).toBe(true);
    await expect(page.locator('span.captcha-math')).toContainText(/What is \d+ (plus|minus) \d+\?/);
  });

  test('the complaint list request carries the X-Citizen-Token header', async ({ page }) => {
    const listHeaders: Record<string, string>[] = [];
    page.on('request', req => {
      if (req.url().includes('/api/v1/complaints?') && req.method() === 'GET') {
        listHeaders.push(req.headers());
      }
    });

    // Seed a session the way the app does, then load a page that lists complaints.
    await page.goto(`${APP_BASE}/public/track`);
    await page.evaluate(() => {
      sessionStorage.setItem('cms_public_session', JSON.stringify({
        identifier: '9876500011', token: 'e2e-seeded-token.sig', startedAt: Date.now(),
      }));
      sessionStorage.setItem('cms_public_last_activity', String(Date.now()));
    });

    await page.goto(`${APP_BASE}/public`);
    await page.waitForLoadState('networkidle');

    await expect.poll(() => listHeaders.length, { timeout: 15000 }).toBeGreaterThan(0);
    expect(listHeaders[0]['x-citizen-token']).toBe('e2e-seeded-token.sig');
  });
});
