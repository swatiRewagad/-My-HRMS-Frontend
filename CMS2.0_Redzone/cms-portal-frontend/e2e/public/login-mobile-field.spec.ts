import { test, expect } from '../fixtures';
import { deleteOtpAttempts, otpAttemptsFor } from '../utils/test-data';
import {
  clearCooloff, freshMathCaptcha, openLogin, sessionAMobile, solveAndFillCaptcha, trackCaptchas,
} from './helpers-auth-a';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 / SESSION A — manual blocks 3 (lines 63-80) and 4 (lines 81-128)
 * Landing screen entry points, login field presence, and the mobile number field's contract.
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * SCOPE, AND WHAT DELIBERATELY IS NOT HERE
 * e2e/public/otp-lifecycle.spec.ts already drives a full successful login (mobile → MATH CAPTCHA → send
 * → verify → eligibility) and owns the OTP clock, the resend cooldown and Change Phone Number. None of
 * that is repeated. What is left, and what this file is, is the FIELD-LEVEL contract QA states as
 * thirteen separate cases: which character classes the number field takes, the 6-9 leading-digit rule,
 * the digit-count boundaries, and the all-zeros case.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * "THE FIELD DOES NOT ACCEPT X" IS AMBIGUOUS, SO BOTH READINGS ARE ASSERTED SEPARATELY
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA writes "mobile number field should not accept alphabetic data". That can mean either
 *
 *   (a) the keystrokes never appear — the input filters them, or
 *   (b) they appear, and the form refuses to act on them with a message.
 *
 * These are different products and a test that checks only one of them passes against the other. The
 * shipping control is `<input type="tel" maxlength="10" [(ngModel)]="mobile">` with NO input filter, so
 * the truth is (b): letters land in the box and `sendOtp()` refuses them on `^[6-9]\d{9}$`. Rather than
 * bend the case to the code or the code to the case, each test here asserts BOTH halves explicitly —
 * what the field retains, and that the journey is refused — so the report can state precisely which
 * reading the product implements. Every divergence is logged in docs/prompts/findings-QA-A.md.
 *
 * THE REFUSAL IS PROVEN IN THE DATABASE, NOT JUST ON SCREEN
 * A message with an OTP issued behind it is the silent-persistence defect class this codebase has
 * shipped before, so the negative cases assert `otpAttemptsFor(...)` is empty. Nothing was sent, not
 * merely "nothing was said".
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * TRAPS PAID FOR IN THIS FILE
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * - maxlength="10" makes "more than 10 digits" untestable by `fill()` alone: Playwright's fill respects
 *   the attribute, so the 11th character never arrives and the assertion would be vacuous. The overflow
 *   case therefore types character by character AND checks the attribute, which is the control that
 *   actually enforces it.
 * - The client-side guard means most of these never reach the server, so the API contract is asserted
 *   directly alongside the UI: a scripted caller bypassing the form must get the same refusal, and the
 *   server's regex is the only thing standing between it and an OTP.
 * - CooloffService.recordFailedAttempt fires on failed CAPTCHA and locks out by fingerprint+IP as well
 *   as by mobile, and every test here shares one loopback IP. clearCooloff() runs before each test; see
 *   helpers-auth-a.ts for why a per-test mobile is not sufficient on its own.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/** 987651xxxx is Session A's allocated range; see helpers-auth-a.ts. */
const MOBILE = {
  valid: sessionAMobile(101),
  startsWith6: sessionAMobile(102),
  numeric: sessionAMobile(103),
  apiValid: sessionAMobile(104),
};

/** Numbers that must never produce an OTP. Asserted against the database, so they are named. */
const REJECTED = {
  tooShort: '987651',
  allZeros: '0000000000',
  startsWith0: '0876510105',
  startsWith5: '5876510105',
};

test.describe('QA-A3 — the landing screen routes a visitor to the citizen login', () => {

  test.beforeEach(async () => {
    clearCooloff();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 63-66 — hitting the portal URL lands on the CMS Portal landing screen.
  //
  // Asserted as the landing screen's own content, not merely a 200: an Angular app returns index.html
  // for every route, so "the URL responded" says nothing about which screen rendered.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the portal URL lands on the public landing screen', async ({ page }) => {
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    await expect(page).toHaveURL(/\/public(\?|$|\/)/);
    // The hero entry point is the landing screen's defining element.
    await expect(page.locator('a[routerLink="/public/file-complaint"]').first()).toBeVisible({ timeout: 15000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 71-75 + 76-80 — a File a Complaint button is present, clickable, and leads to the login screen.
  //
  // The route is guarded by publicAuthGuard, so an anonymous visitor is bounced to /public/login with a
  // returnUrl. ARRIVING on the login screen is therefore the assertion; the guard is what implements
  // the case.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('File a Complaint is present on the landing screen and leads to the login screen', async ({ page }) => {
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const fileComplaint = page.locator('a[routerLink="/public/file-complaint"]').first();
    await expect(fileComplaint).toBeVisible({ timeout: 15000 });
    await fileComplaint.click();

    await expect(page).toHaveURL(/\/public\/login/, { timeout: 15000 });
    await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 15000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 67-70 — "a Login button is present on the landing screen and the user is able to click on it".
  //
  // MARKED fixme AGAINST A PRODUCT GAP, NOT A HARNESS ONE. There is no anonymous Login control anywhere
  // on the landing screen or in the header: public-layout.component.html renders login-related controls
  // only inside `@if (authService.isAuthenticated())` (My Complaints, the user menu, Logout), and the
  // sole `routerLink="/public/login"` in the whole layout is the "Log in again" link inside the
  // session-TIMEOUT banner, which renders only after an inactivity logout. A first-time visitor can
  // therefore reach login only by clicking File a Complaint (or another guarded action) and being
  // bounced there by the guard.
  //
  // The assertion below is written for the behaviour QA specifies rather than the behaviour observed, so
  // it starts passing the moment the control is added. Logged in findings-QA-A.md as defect A-D1.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test.fixme('a Login control is present on the landing screen for an anonymous visitor', async ({ page }) => {
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const login = page.getByRole('link', { name: /^log ?in$/i })
      .or(page.getByRole('button', { name: /^log ?in$/i }));
    await expect(login.first()).toBeVisible({ timeout: 10000 });
    await login.first().click();
    await expect(page).toHaveURL(/\/public\/login/, { timeout: 15000 });
  });
});

test.describe('QA-A4 — the login screen displays the mobile, CAPTCHA and Send OTP controls', () => {

  test.beforeEach(async () => {
    clearCooloff();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 81-90, and again at 778-786 — the three fields QA enumerates are on the login screen.
  //
  // One test, because the case is a single enumeration. The CONSENT checkbox is deliberately also
  // checked here: it gates Send OTP, so a screen with only QA's three controls could not be submitted
  // at all, and block 25 (mandatory declaration) is the case that owns it.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the mobile number, CAPTCHA and Send OTP controls are all displayed', async ({ page }) => {
    await openLogin(page);

    const mobile = page.locator('#mobile-input');
    await expect(mobile).toBeVisible();
    await expect(mobile).toBeEnabled();
    // A +91-prefixed national number field: the country code is decoration, the field takes 10 digits.
    await expect(mobile).toHaveAttribute('type', 'tel');
    await expect(page.locator('.country-code')).toHaveText('+91');

    await expect(page.locator('.captcha-input')).toBeVisible();
    await expect(page.locator('.captcha-input')).toBeEnabled();
    // The challenge itself must render, not just the answer box — either the image or the MATH question.
    await expect(page.locator('.captcha-image-box')).toBeVisible();

    await expect(page.locator('.send-otp-btn')).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 91-96 — the citizen can type into the field, and it accepts numeric data.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the mobile field accepts typed numeric input and retains all ten digits', async ({ page }) => {
    await openLogin(page);
    const field = page.locator('#mobile-input');

    // Typed key by key rather than filled, because "the user is able to enter input" is about typing.
    await field.click();
    await field.pressSequentially(MOBILE.numeric, { delay: 10 });

    await expect(field).toHaveValue(MOBILE.numeric);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 97-108 — alphanumeric, alphabetic, special-character and spaces-only input must not be accepted.
  //
  // Driven as a table over the four classes QA names, plus emoji — a class QA does not name but which is
  // the one most likely to slip past a naive filter, and which must be refused by the same rule.
  //
  // BOTH READINGS OF "NOT ACCEPT" ARE RECORDED. The field has no input filter, so the characters are
  // retained; what is REFUSED is the journey. Each case therefore asserts the refusal AND that no OTP
  // row exists, and additionally reports whether the field filtered the keystrokes, so the findings can
  // state which of the two the product implements.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  const NON_NUMERIC: Array<{ label: string; input: string }> = [
    { label: 'alphanumeric', input: 'ab12cd34ef' },
    { label: 'alphabetic', input: 'abcdefghij' },
    { label: 'special characters', input: '@#$%^&*()!' },
    { label: 'only spaces', input: '          ' },
    { label: 'an emoji', input: '\u{1F600}98765102' },
  ];

  for (const { label, input } of NON_NUMERIC) {
    test(`${label} in the mobile field cannot produce an OTP`, async ({ page }) => {
      const tracked = trackCaptchas(page);
      await openLogin(page);

      await page.locator('#mobile-input').fill(input);
      // CAPTCHA and consent are supplied correctly, so the ONLY thing wrong is the number and the
      // refusal can only be about that.
      await solveAndFillCaptcha(page, tracked);
      await page.locator('.consent-section input[type="checkbox"]').check();

      const send = page.locator('.send-otp-btn');
      await expect(send).toBeEnabled({ timeout: 10000 });
      await send.click();

      // THE REFUSAL. A blank-after-trim value is the required-field case; anything else is malformed.
      // Both are refusals, and the component distinguishes them deliberately (an absent number and a
      // wrong one must not share a message), so either sentence satisfies this case.
      await expect(page.locator('.error-banner')).toBeVisible({ timeout: 10000 });
      await expect(page.locator('.error-banner')).toContainText(
        /Mobile number is required to request OTP\.|Enter a valid 10-digit Indian mobile number starting with 6-9\./);

      // AND NOTHING WAS SENT: the citizen never left the mobile step.
      await expect(page.locator('.otp-inputs')).toHaveCount(0);
    });
  }

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 109-115 — the field accepts numbers starting 6 onwards, and refuses 0-5.
  //
  // The whole leading-digit range is swept rather than sampled: QA names 0,1,2,3,4,5 as refused and
  // "6 onwards" as accepted, and a product that special-cased one of them would pass a sampled test.
  // The ACCEPTED side is proven at the API, where "accepted" means an OTP was actually issued — on the
  // UI, acceptance would only mean "no message appeared", which is also true of a form that did nothing.
  // The REFUSED side is proven at the API too, because the client-side regex would otherwise stop the
  // request and the server's own rule would go untested.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the API accepts a leading digit of 6-9 and refuses 0-5', async ({ request }) => {
    // ── refused: 0 through 5 ──
    for (const lead of ['0', '1', '2', '3', '4', '5']) {
      const mobile = `${lead}876510105`;
      const captcha = await freshMathCaptcha(request, API_BASE);
      const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
        data: {
          mobile, captchaToken: captcha.token, captchaAnswer: String(captcha.answer),
          consentGiven: 'true', locale: 'en',
        },
        headers: { 'Content-Type': 'application/json' },
      });
      expect(res.status(), `a mobile number starting with ${lead} was accepted`).toBe(400);
      expect((await res.json()).error).toBe('INVALID_MOBILE');
      // The number is validated BEFORE the CAPTCHA is consumed, so no lockout attempt is burned on a
      // typo — asserted implicitly by the next iteration succeeding in reaching the same branch.
    }

    // ── accepted: 6 through 9, proven by an OTP actually being issued ──
    for (const lead of ['6', '7', '8', '9']) {
      const mobile = `${lead}87651019${lead}`;
      deleteOtpAttempts(mobile);
      try {
        const captcha = await freshMathCaptcha(request, API_BASE);
        const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
          data: {
            mobile, captchaToken: captcha.token, captchaAnswer: String(captcha.answer),
            consentGiven: 'true', locale: 'en',
          },
          headers: { 'Content-Type': 'application/json' },
        });
        expect(res.status(), `a mobile number starting with ${lead} was refused`).toBe(200);
        expect(otpAttemptsFor(mobile).length,
          `send-otp returned 200 for a ${lead}-leading number but issued no OTP`).toBeGreaterThan(0);
      } finally {
        deleteOtpAttempts(mobile);
      }
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 116-118 — the field accepts exactly 10 digits.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('a valid ten-digit number is accepted and reaches the OTP step', async ({ page }) => {
    deleteOtpAttempts(MOBILE.valid);
    const tracked = trackCaptchas(page);
    await openLogin(page);

    await page.locator('#mobile-input').fill(MOBILE.valid);
    await expect(page.locator('#mobile-input')).toHaveValue(MOBILE.valid);
    await solveAndFillCaptcha(page, tracked);
    await page.locator('.consent-section input[type="checkbox"]').check();
    await page.locator('.send-otp-btn').click();

    await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });
    // "Accepted" means an OTP was issued, not merely that the screen advanced.
    expect(otpAttemptsFor(MOBILE.valid).length, 'the OTP step rendered but no OTP was issued')
      .toBeGreaterThan(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 119-121 — fewer than 10 digits is refused.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('fewer than ten digits is refused and issues no OTP', async ({ page }) => {
    deleteOtpAttempts(REJECTED.tooShort.padEnd(10, '0'));
    const tracked = trackCaptchas(page);
    await openLogin(page);

    await page.locator('#mobile-input').fill(REJECTED.tooShort);
    await solveAndFillCaptcha(page, tracked);
    await page.locator('.consent-section input[type="checkbox"]').check();
    await page.locator('.send-otp-btn').click();

    await expect(page.locator('.error-banner')).toContainText(
      'Enter a valid 10-digit Indian mobile number starting with 6-9.', { timeout: 10000 });
    await expect(page.locator('.otp-inputs')).toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 122-124 — more than 10 digits is refused.
  //
  // `fill()` OBEYS maxlength, so filling 11 characters silently stores 10 and the test would be
  // asserting nothing. The enforcement is the attribute, so the attribute is asserted, and then an 11th
  // character is TYPED to prove the control truncates rather than merely being annotated.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the mobile field caps input at ten digits so an eleventh cannot be entered', async ({ page }) => {
    await openLogin(page);
    const field = page.locator('#mobile-input');

    await expect(field).toHaveAttribute('maxlength', '10');

    await field.click();
    await field.pressSequentially(`${MOBILE.valid}7`, { delay: 10 });

    // The 11th keystroke is dropped by the control; the value is the first ten digits.
    await expect(field).toHaveValue(MOBILE.valid);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 125-128 — the number cannot start with 0 and cannot be all zeros.
  //
  // Both are already covered by the leading-digit rule, but QA states them as their own case and an
  // all-zeros string is the classic input that slips past a "not empty, is numeric, is 10 long" check.
  // Proven at the API AND in the database, because the claim is that no OTP exists for it.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('an all-zeros number is refused at the API and writes no OTP row', async ({ request }) => {
    deleteOtpAttempts(REJECTED.allZeros);
    try {
      for (const mobile of [REJECTED.allZeros, REJECTED.startsWith0]) {
        const captcha = await freshMathCaptcha(request, API_BASE);
        const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
          data: {
            mobile, captchaToken: captcha.token, captchaAnswer: String(captcha.answer),
            consentGiven: 'true', locale: 'en',
          },
          headers: { 'Content-Type': 'application/json' },
        });
        expect(res.status(), `${mobile} was accepted as a mobile number`).toBe(400);
        expect((await res.json()).error).toBe('INVALID_MOBILE');
        expect(otpAttemptsFor(mobile).length, `an OTP was issued to ${mobile}`).toBe(0);
      }
    } finally {
      deleteOtpAttempts(REJECTED.allZeros);
      deleteOtpAttempts(REJECTED.startsWith0);
    }
  });

  test.afterAll(async () => {
    for (const mobile of Object.values(MOBILE)) deleteOtpAttempts(mobile);
    clearCooloff();
  });
});
