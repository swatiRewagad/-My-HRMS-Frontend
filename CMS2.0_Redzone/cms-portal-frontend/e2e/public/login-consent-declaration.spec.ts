import { test, expect } from '../fixtures';
import { deleteOtpAttempts, otpAttemptsFor } from '../utils/test-data';
import {
  clearCooloff, freshMathCaptcha, openLogin, sessionAMobile, solveAndFillCaptcha, trackCaptchas,
} from './helpers-auth-a';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA PASS 4 / SESSION A — the consent / mandatory declaration checkbox
 * Manual blocks 6 (lines 147-155) and 25 (lines 1658-1678).
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * WHAT IS ALREADY COVERED ELSEWHERE AND IS NOT REPEATED HERE
 * e2e/public/consent-dpdp.spec.ts (UST5, 11 tests) already proves: the notice text comes from
 * TRANSLATIONS rather than hardcoded English and is served for every locale; send-otp transmits
 * `consentGiven` and `locale`; Send OTP is blocked client-side until the box is ticked; the tracker sends
 * `consentGiven=false`; and the server rejects an ONLINE *filing* without `declarationAccepted`.
 *
 * So this file is only the residue of blocks 6 and 25 that UST5 does not assert:
 *   - the checkbox is displayed and starts UNTICKED (a pre-ticked consent box is not consent at all,
 *     and under the DPDP Act it is the defect that matters most here);
 *   - it can be ticked AND UN-ticked, and un-ticking re-blocks the journey — nothing anywhere tests the
 *     reverse transition, and a checkbox whose state is latched one-way would pass every existing test;
 *   - the declaration is what gates progression, isolated from the CAPTCHA and mobile (so the test
 *     cannot pass for the wrong reason);
 *   - that an unticked declaration is REFUSED WITH A MESSAGE — case 1670-1672.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * A-C8 — RESOLVED. Mandatory-field enforcement is now standardised on enable-and-explain.
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * Case 1670-1672 requires that an unticked declaration produce a displayed error. It previously could
 * not: `send-otp-btn` was `[disabled]="!captchaInput || !consentChecked || ..."`, so the control was
 * inert, no handler ran, and the consent guard inside `sendOtp()` was dead code from the UI's point of
 * view. That contradicted the team's own decision one field over, where `!mobile` had been deliberately
 * removed from the disable condition because "a disabled button explains nothing".
 *
 * Ruled and applied: no mandatory FIELD gates the button. Only transient states (cool-off, an in-flight
 * request) disable it, because those are not something the citizen can correct by typing. Each omission
 * is refused inside `sendOtp()` with its own wording, before any request is sent. The wording is the
 * product's own — "You must accept the data processing declaration to continue." — not the manual's
 * invented "Consent declaration is mandatory."; the requirement is that the citizen be told, not that a
 * particular sentence be used.
 *
 * NOTE for whoever reads a diff of this file: the assertions below flipped from `toBeDisabled()` to
 * enabled-and-refused. That is the fix landing, not a regression.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8092';

const MOBILE = {
  toggle: sessionAMobile(301),
  gates: sessionAMobile(302),
  proceeds: sessionAMobile(303),
  apiNoConsent: sessionAMobile(304),
};

test.describe('QA-A6/A25 — the mandatory declaration checkbox gates the OTP request', () => {

  test.beforeEach(async () => {
    clearCooloff();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 1663-1665 — the mandatory declaration checkbox is displayed on the login screen.
  //
  // AND that it starts UNTICKED. QA does not state it, but a pre-ticked consent box is not consent under
  // the DPDP Act — it is the single most consequential thing that could be wrong with this control, and
  // every other case in both blocks would still pass if it were.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the declaration checkbox is displayed, labelled, and starts unticked', async ({ page }) => {
    await openLogin(page);

    const box = page.locator('.consent-section input[type="checkbox"]');
    await expect(box).toBeVisible();
    await expect(box).toBeEnabled();
    // Consent must be a positive act by the citizen, never a default.
    await expect(box, 'the consent box is pre-ticked, so consent is assumed rather than given')
      .not.toBeChecked();

    // The notice it consents TO must actually be rendered beside it — a bare checkbox consents to nothing.
    const notice = page.locator('.consent-section .consent-label span');
    await expect(notice).toBeVisible();
    expect(((await notice.textContent()) || '').trim().length,
      'the consent box carries no notice text').toBeGreaterThan(20);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 1666-1667 + 147-148 — the citizen can select the checkbox.
  // 1668-1669 — and can UNSELECT it.
  //
  // The reverse transition is the half nothing else in the suite covers, and it is not hypothetical: a
  // control bound one-way, or one that latches after the first tick, would satisfy every other consent
  // test in this repository. Withdrawal of consent is also a DPDP requirement, so "it can be turned off
  // again" is a real rule and not just input hygiene.
  //
  // Asserted through the LABEL as a citizen clicks it, not by setting the input's checked property, and
  // the effect on Send OTP is asserted in both directions — the box must gate, not merely toggle.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('the declaration can be selected and unselected, and it gates the OTP request both ways', async ({ page }) => {
    const tracked = trackCaptchas(page);
    await openLogin(page);

    // A solved CAPTCHA and a valid number, so the declaration is the ONLY variable and any refusal can
    // only be attributable to it.
    await page.locator('#mobile-input').fill(MOBILE.toggle);
    await solveAndFillCaptcha(page, tracked);

    const box = page.locator('.consent-section input[type="checkbox"]');
    const send = page.locator('.send-otp-btn');
    const banner = page.locator('.error-banner');

    // The button is ALWAYS clickable now — the gate is the refusal, not an inert control.
    await expect(box).not.toBeChecked();
    await expect(send).toBeEnabled();
    await send.click();
    await expect(banner, 'an unticked declaration was not refused with a message')
      .toContainText(/must accept the data processing declaration/i);

    // ── select ──
    await box.check();
    await expect(box).toBeChecked();

    // ── unselect: the citizen changes their mind, and the gate closes again ──
    await box.uncheck();
    await expect(box, 'the declaration could not be unticked once ticked').not.toBeChecked();
    await send.click();
    await expect(banner, 'withdrawing the declaration no longer blocks the request')
      .toContainText(/must accept the data processing declaration/i);

    // ── and select again, so the control is not one-shot ──
    await box.check();
    await expect(box).toBeChecked();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 149-150 + 1670-1672 — the declaration cannot be left unselected; the citizen is restricted.
  //
  // "Restricted" is proven as NO REQUEST AND NO OTP, never as a message alone: a screen that shows an
  // error while the form still posts would pass a wording check. The network is watched and the
  // database is read.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('with the declaration unticked no OTP request is made and no OTP is issued', async ({ page }) => {
    deleteOtpAttempts(MOBILE.gates);
    const tracked = trackCaptchas(page);

    const sendOtpCalls: string[] = [];
    await openLogin(page);
    page.on('request', (req) => {
      if (req.url().includes('/send-otp')) sendOtpCalls.push(req.url());
    });

    await page.locator('#mobile-input').fill(MOBILE.gates);
    await solveAndFillCaptcha(page, tracked);
    // The declaration is deliberately NOT ticked. Everything else is valid.

    const send = page.locator('.send-otp-btn');
    await expect(send).toBeEnabled();
    await send.click();

    // The refusal is client-side and must not reach the wire: an OTP request with no consent would create
    // a CITIZEN_CONSENTS gap AND burn one of the citizen's lockout attempts.
    await expect(page.locator('.error-banner'))
      .toContainText(/must accept the data processing declaration/i);
    await expect(page.locator('.otp-inputs')).toHaveCount(0);
    expect(sendOtpCalls, 'an OTP was requested without the declaration being accepted').toHaveLength(0);
    expect(otpAttemptsFor(MOBILE.gates).length,
      'an OTP was issued without the declaration being accepted').toBe(0);

    deleteOtpAttempts(MOBILE.gates);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 1670-1672, the MESSAGE half — an unticked declaration must be EXPLAINED, not silently refused.
  //
  // A-C8, now fixed. This was a fixme while the button was disabled on `!consentChecked`, which made the
  // consent guard in `sendOtp()` unreachable and left a citizen with an inert control and no explanation.
  // The manual's literal string "Consent declaration is mandatory." is NOT asserted: the product's own
  // wording is used, and the requirement is that the citizen be told, not which sentence is used.
  //
  // Isolated from the other two mandatory fields on purpose — the CAPTCHA is solved and the number is
  // valid — so this can only pass because of the consent guard specifically.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('an explicit message explains that the declaration is mandatory', async ({ page }) => {
    const tracked = trackCaptchas(page);
    await openLogin(page);

    await page.locator('#mobile-input').fill(MOBILE.gates);
    await solveAndFillCaptcha(page, tracked);

    // The citizen attempts to proceed without ticking the box and must be TOLD why they cannot.
    await page.locator('.send-otp-btn').click();

    await expect(page.locator('.error-banner')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.error-banner'))
      .toContainText(/declaration is mandatory|must accept the data processing declaration/i);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // 151-155 + 1673-1678 — with a valid mobile, a valid CAPTCHA and the declaration ticked, the citizen
  // can click Send OTP and reaches the OTP screen.
  //
  // This is the positive path of both blocks, and "proceeds" is proven by an OTP actually existing —
  // the screen advancing is necessary but not sufficient.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('with the declaration accepted the citizen proceeds to the OTP screen', async ({ page }) => {
    deleteOtpAttempts(MOBILE.proceeds);
    const tracked = trackCaptchas(page);
    await openLogin(page);

    await page.locator('#mobile-input').fill(MOBILE.proceeds);
    await solveAndFillCaptcha(page, tracked);
    await page.locator('.consent-section input[type="checkbox"]').check();

    const send = page.locator('.send-otp-btn');
    await expect(send).toBeEnabled();
    await send.click();

    await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });
    expect(otpAttemptsFor(MOBILE.proceeds).length).toBeGreaterThan(0);

    deleteOtpAttempts(MOBILE.proceeds);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // The API's treatment of consent, recorded because it is NOT what a reader of block 25 would assume.
  //
  // send-otp does NOT refuse a request that withholds consent: `CitizenAuthController.sendOtp` records
  // consent only `if (consentGiven(body))` and issues the OTP regardless. That is DELIBERATE and
  // documented in the controller (UST5) — the same endpoint serves the complaint TRACKER, where no new
  // personal data is processed and therefore no consent is collected; enforcement lives at complaint
  // SUBMISSION, which consent-dpdp.spec.ts asserts.
  //
  // So the declaration is a gate on FILING, not on authentication. This test pins that distinction, so
  // that "consent is enforced at send-otp" is never added on the assumption it was an oversight — doing
  // so would break the tracker. It is the reason the block-25 cases are all UI-level.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('send-otp itself does not require consent, because the tracker shares it', async ({ request }) => {
    deleteOtpAttempts(MOBILE.apiNoConsent);
    try {
      const captcha = await freshMathCaptcha(request, API_BASE);
      const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
        data: {
          mobile: MOBILE.apiNoConsent,
          captchaToken: captcha.token,
          captchaAnswer: String(captcha.answer),
          consentGiven: 'false',
          locale: 'en',
        },
        headers: { 'Content-Type': 'application/json' },
      });

      expect(res.status(),
        'send-otp now refuses a request without consent — this BREAKS the complaint tracker, which ' +
        'shares this endpoint and collects no consent. See UST5 in CitizenAuthController.').toBe(200);
      expect(otpAttemptsFor(MOBILE.apiNoConsent).length).toBeGreaterThan(0);
    } finally {
      deleteOtpAttempts(MOBILE.apiNoConsent);
    }
  });

  test.afterAll(async () => {
    for (const mobile of Object.values(MOBILE)) deleteOtpAttempts(mobile);
    clearCooloff();
  });
});
