import { test, expect } from '../fixtures';
import { loginCitizen, seedCitizenSession } from '../utils/test-data';

/**
 * Citizen login + session timeout (manual QA cases 1-8).
 *
 * ── Why no test here idles for 14 real minutes ──────────────────────────────────────────────────
 * The suite runs `workers: 1` with a 60s per-test timeout, so a literal 14-minute idle is not merely
 * slow, it is impossible. The clock is driven instead of waited out.
 *
 * PublicAuthService decides everything from one number: `Date.now() - parseInt(cms_public_last_activity)`
 * compared against `environment.sessionTimeoutMinutes * 60 * 1000` (15 min). Backdating that
 * sessionStorage value via page.evaluate makes the session AS OLD as the test needs, and the real
 * production 1-second setInterval then reads it on its next tick and reaches its own conclusion.
 *
 * Nothing is stubbed: the timer, the comparison, the `<= 60 && > 0` warning window and the logout() call
 * are all the shipping code. Only the INPUT clock is controlled — the same thing a 14-minute wait would
 * do, minus the 14 minutes. An assertion here is therefore as real as one made after waiting.
 *
 * Landmarks used below:
 *   14 min old → remaining = 60s → inside the warning window (QA case 4's "warning at the 14th minute")
 *   15 min old → remaining = 0   → the logout tick fires (QA cases 5, 6, 8)
 *
 * ── Route and session traps (already paid for elsewhere in this suite) ──────────────────────────
 * Citizen routes are CHILDREN of `public`: /public/login, /public/file-complaint. A bare path silently
 * falls through to the landing page and every locator then fails for the wrong reason.
 * seedCitizenSession writes sessionStorage, so it must run on a page already served from the app
 * origin — hence a bare goto before it.
 */

const CITIZEN_MOBILE = '9876512388';
const TIMEOUT_MINUTES = 15;

/** Ages the citizen session so the live countdown sees `minutes` of inactivity. */
async function ageSessionBy(page: import('@playwright/test').Page, minutes: number, extraMs = 0) {
  await page.evaluate(([mins, extra]) => {
    const backdated = Date.now() - (mins * 60 * 1000 + extra);
    sessionStorage.setItem('cms_public_last_activity', String(backdated));
  }, [minutes, extraMs]);
}

/** Reads what the citizen session currently looks like in the browser. */
async function sessionState(page: import('@playwright/test').Page) {
  return page.evaluate(() => ({
    session: sessionStorage.getItem('cms_public_session'),
    lastActivity: sessionStorage.getItem('cms_public_last_activity'),
    // localStorage, not sessionStorage: the marker has to outlive the tab. See QA8b.
    timedOut: localStorage.getItem('cms_public_timed_out'),
  }));
}

/**
 * Logs in through the REAL login screen: mobile → math captcha → Send OTP → Verify.
 *
 * Returns only once the session actually exists. That wait is not cosmetic — navigating straight after
 * clicking Verify ABORTS the in-flight verify-otp request, the component's error branch resets the form
 * to step 1, and the resulting failure looks like a broken login screen rather than a racing test.
 */
async function loginThroughUi(page: import('@playwright/test').Page, mobile: string) {
  await page.goto('/public/login', { waitUntil: 'domcontentloaded' });
  // The router can still settle a guard redirect from the PREVIOUS (expired-session) navigation after
  // this goto resolves. That remounts PublicLoginComponent and wipes anything already typed, so the
  // form is only touched once the page has gone quiet.
  await page.waitForLoadState('networkidle');

  await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 15000 });

  // Switch to the MATH captcha: it is the only variant whose answer the test can compute, since the
  // VISUAL one deliberately never sends its answer to the client.
  //
  // Wrapped in toPass because the component can be remounted underneath this (a guard redirect from the
  // previous expired-session navigation settling), which resets captchaType to VISUAL and discards the
  // click. Retrying the switch is the honest fix; the alternative is a fixed sleep that still races.
  const captchaQuestion = page.locator('.captcha-math');
  await expect(async () => {
    if (!(await captchaQuestion.isVisible().catch(() => false))) {
      await page.locator('.icon-btn[aria-label="Listen to CAPTCHA audio"]').click();
    }
    await expect(captchaQuestion).toBeVisible({ timeout: 3000 });
  }).toPass({ timeout: 20000 });

  const question = (await captchaQuestion.textContent()) || '';
  const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(question);
  expect(match, `captcha question was not solvable: ${question}`).toBeTruthy();
  const [, a, op, b] = match!;
  const answer = op.toLowerCase() === 'plus' ? Number(a) + Number(b) : Number(a) - Number(b);

  await page.locator('#mobile-input').fill(mobile);
  await page.locator('.captcha-input').fill(String(answer));
  // Consent gates Send OTP (DPDP declaration), so it is part of "valid credentials" here.
  await page.locator('.consent-section input[type="checkbox"]').check();

  // Send OTP is disabled until mobile + captcha + consent are all present. Asserting that here turns a
  // silently-remounted form into a clear failure instead of a click that does nothing.
  const sendOtp = page.locator('.send-otp-btn');
  await expect(sendOtp).toBeEnabled({ timeout: 10000 });
  await sendOtp.click();

  // Reaching the OTP step proves the mobile + captcha pair was accepted.
  await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });

  // dev-local auto-populates the OTP boxes; type it when it did not.
  const boxes = page.locator('.otp-box');
  if (!(await boxes.first().inputValue())) {
    for (let i = 0; i < 6; i++) await boxes.nth(i).fill('123456'[i]);
  }
  await page.locator('.verify-btn').click();

  await expect
    .poll(async () => (await sessionState(page)).session, { timeout: 15000 })
    .not.toBeNull();
}

test.describe('Citizen portal — login and session timeout', () => {

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 1 — Login with valid credentials through the real UI.
  // Mobile → captcha → Send OTP → Verify. Drives the actual screen, not the API,
  // because "click Send OTP / click Verify" is the case.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA1: citizen logs in with valid mobile, captcha and OTP', async ({ page }) => {
    await loginThroughUi(page, CITIZEN_MOBILE);

    // Login succeeded = a session exists (asserted in the helper) and the layout shows the citizen.
    await expect(page.locator('.nav-user-phone')).toBeVisible({ timeout: 10000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 2 — After login the citizen reaches the eligible-questions page and the
  // entity-selection section is viewable and clickable.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA2: logged-in citizen reaches eligibility questions and can use entity selection', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token — cannot reach a guarded route').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);

    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });

    // publicAuthGuard sends an unauthenticated visitor to /public/login, so staying here IS the
    // "navigation works" half of the case.
    await expect(page).toHaveURL(/\/public\/file-complaint/);

    const entitySelect = page.locator('.entity-select-dropdown');
    await expect(entitySelect).toBeVisible({ timeout: 20000 });

    // "Clickable" for a <select> means it is enabled and has real options to choose, not just present.
    await expect(entitySelect).toBeEnabled();
    await expect
      .poll(async () => entitySelect.locator('option:not([disabled])').count(), { timeout: 20000 })
      .toBeGreaterThan(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 3 — An ACTIVE session is never interrupted: answer questions in sequence,
  // no warning banner, no logout.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA3: active session shows no warning while the citizen answers questions in sequence', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });

    const entitySelect = page.locator('.entity-select-dropdown');
    await expect(entitySelect).toBeVisible({ timeout: 20000 });

    const firstEntity = await entitySelect.locator('option:not([disabled])').first().getAttribute('value');
    expect(firstEntity, 'no selectable regulated entity was offered').toBeTruthy();
    await entitySelect.selectOption(firstEntity!);

    // Advance through the Yes/No questions the way a citizen would. Three steps is enough to show the
    // sequence is not interrupted; the point of the case is the absence of a break, not the full wizard.
    for (let step = 0; step < 3; step++) {
      const next = page.locator('.btn-next');
      if (!(await next.isVisible().catch(() => false))) break;
      await next.click();
      await page.waitForTimeout(500);

      const options = page.locator('.radio-list .radio-option');
      if (await options.count() === 0) break;
      await options.first().click();
      await page.waitForTimeout(300);
    }

    // The 1-second countdown has been running throughout. Give it several more ticks and require that
    // it still has not warned or signed the citizen out.
    await page.waitForTimeout(3000);
    await expect(page.locator('.session-warning-banner')).toHaveCount(0);
    await expect(page.locator('.session-timeout-banner')).toHaveCount(0);
    expect((await sessionState(page)).session).not.toBeNull();
    await expect(page).toHaveURL(/\/public\/file-complaint/);
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 4 — 14 minutes idle → warning saying the session expires in 1 minute;
  // then the system logs the citizen out automatically.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA4: warning appears at the 14th idle minute, then the system logs out automatically', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    // 14 minutes + 1s old → remaining 59s → the last minute of the window.
    await ageSessionBy(page, 14, 1000);

    const warning = page.locator('.session-warning-banner');
    await expect(warning).toBeVisible({ timeout: 10000 });

    // The warning must actually say the session is about to expire within a minute, not merely exist.
    const warningText = (await warning.textContent()) || '';
    expect(warningText.toLowerCase()).toContain('expire');
    const secondsShown = Number(/(\d+)\s*seconds/.exec(warningText)?.[1] ?? -1);
    expect(secondsShown).toBeGreaterThan(0);
    expect(secondsShown).toBeLessThanOrEqual(60);

    // It is announced, so a screen-reader user is told too.
    await expect(warning).toHaveAttribute('role', 'alert');

    // Now push past the window and require the logout to happen on its own — no click.
    await ageSessionBy(page, TIMEOUT_MINUTES, 1000);
    await expect
      .poll(async () => (await sessionState(page)).session, { timeout: 15000 })
      .toBeNull();
    await expect(warning).toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 5 — After the automatic logout the citizen is shown the re-login prompt
  // "Your session has timed out. Please log in again."
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA5: automatic logout shows the re-login prompt with the required wording', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    await ageSessionBy(page, TIMEOUT_MINUTES, 1000);

    const prompt = page.locator('.session-timeout-banner');
    await expect(prompt).toBeVisible({ timeout: 15000 });

    // The QA case quotes this sentence, so it is asserted verbatim.
    await expect(prompt).toContainText('Your session has timed out. Please log in again.');
    await expect(prompt).toHaveAttribute('role', 'alert');

    // A "re-login prompt" has to offer the way back, not just state the problem.
    await expect(prompt.locator('.session-timeout-login')).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 6 — Session time is captured correctly (idle 9:00 → warning 9:14), and a
  // browser refresh at the 15th minute still shows a warning.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA6: idle time is measured correctly and a refresh at the 15th minute still warns', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    // Timing precision: at 13 minutes idle there must be NO warning, because a banner that appears
    // early is just as wrong as one that never appears.
    await ageSessionBy(page, 13);
    await page.waitForTimeout(2500);
    await expect(page.locator('.session-warning-banner')).toHaveCount(0);

    // 9:00 idle → 9:14 warning: at exactly 14 minutes the banner must be up, counting down inside
    // the final minute.
    await ageSessionBy(page, 14, 500);
    const warning = page.locator('.session-warning-banner');
    await expect(warning).toBeVisible({ timeout: 10000 });
    const remaining = Number(/(\d+)\s*seconds/.exec((await warning.textContent()) || '')?.[1] ?? -1);
    expect(remaining).toBeGreaterThan(0);
    expect(remaining).toBeLessThanOrEqual(60);

    // Now the refresh half of the case. The session is aged past the window and the browser reloaded;
    // the citizen must still be warned that their session ended rather than silently losing it.
    await ageSessionBy(page, TIMEOUT_MINUTES, 2000);
    await page.reload({ waitUntil: 'domcontentloaded' });

    await expect(page.locator('.session-timeout-banner')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.session-timeout-banner'))
      .toContainText('Your session has timed out. Please log in again.');
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 7 — After the automatic logout the citizen can log in again and get a
  // fresh ACTIVE session.
  // ═══════════════════════════════════════════════════════════════════════════
  test('QA7: citizen can log in again after an automatic logout and gets a fresh active session', async ({ page, request }) => {
    const first = await loginCitizen(request, CITIZEN_MOBILE);
    expect(first, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, first!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    await ageSessionBy(page, TIMEOUT_MINUTES, 1000);
    await expect
      .poll(async () => (await sessionState(page)).session, { timeout: 15000 })
      .toBeNull();
    await expect(page.locator('.session-timeout-banner')).toBeVisible({ timeout: 10000 });

    // Log in again through the UI, which is what the citizen does.
    await loginThroughUi(page, CITIZEN_MOBILE);

    // FRESH means all three things: a new session exists, the timeout notice is gone, and the full
    // 15-minute window is available again — a session inherited from the expired one would not be.
    await expect(page.locator('.session-timeout-banner')).toHaveCount(0);
    await expect(page.locator('.session-warning-banner')).toHaveCount(0);

    const state = await sessionState(page);
    expect(state.timedOut).toBeNull();
    const idleMs = Date.now() - Number(state.lastActivity);
    expect(idleMs).toBeLessThan(60_000);

    // And the fresh session actually works: a guarded route renders instead of bouncing to login.
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/public\/file-complaint/);
  });

  // ═══════════════════════════════════════════════════════════════════════════
  // QA 8 — A suddenly-disrupted session must produce the same re-login prompt and
  // let the citizen continue. The case lists two disruptions, and they fail in
  // different ways, so they are separate tests: folded into one, a passing arm
  // would be hidden behind a failing one.
  // ═══════════════════════════════════════════════════════════════════════════

  // Arm A — a form left open past the window, with Submit/Next clicked at the 15th minute.
  test('QA8a: acting on a stale form at the 15th minute shows the re-login prompt and the citizen can continue', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    await ageSessionBy(page, TIMEOUT_MINUTES, 2000);

    // Click Next the way a citizen returning to a forgotten tab would.
    const next = page.locator('.btn-next');
    if (await next.isVisible().catch(() => false)) {
      await next.click().catch(() => undefined);
    }

    await expect(page.locator('.session-timeout-banner')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.session-timeout-banner'))
      .toContainText('Your session has timed out. Please log in again.');

    // ...and the citizen can log back in and carry on with the filing.
    await loginThroughUi(page, CITIZEN_MOBILE);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page).toHaveURL(/\/public\/file-complaint/);
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.session-timeout-banner')).toHaveCount(0);
  });

  /**
   * Arm B — the tab was closed accidentally and the citizen reopens the portal in a NEW tab.
   *
   * This is the arm that justified moving the timed-out marker out of sessionStorage. A newly opened tab
   * does NOT inherit sessionStorage (verified directly: a key written in one tab reads back null in a
   * tab opened afterwards), so while the session and its marker both lived there, a reopened tab could
   * not know a session had ever existed and the citizen got a bare login form. The marker now lives in
   * localStorage, which is shared across tabs of the origin, so the reason survives the tab that died.
   */
  test('QA8b: reopening the portal in a new tab after a timeout still explains why', async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    expect(token, 'citizen OTP login returned no token').toBeTruthy();

    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
    await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });

    // Let the session time out in this tab, then lose the tab.
    await ageSessionBy(page, TIMEOUT_MINUTES, 2000);
    await expect(page.locator('.session-timeout-banner')).toBeVisible({ timeout: 15000 });
    await page.close();

    const reopened = await page.context().newPage();
    await reopened.goto(
      `${process.env['UI_BASE_URL'] || 'http://localhost:4202'}/public/file-complaint`,
      { waitUntil: 'domcontentloaded' }
    );

    // The guard correctly bounces to login; the QA case requires the REASON to be on that screen too.
    await expect(reopened).toHaveURL(/\/public\/login/, { timeout: 15000 });
    await expect(reopened.locator('.session-timeout-banner')).toBeVisible({ timeout: 10000 });
    await expect(reopened.locator('.session-timeout-banner'))
      .toContainText('Your session has timed out. Please log in again.');

    // And logging in from the reopened tab clears the notice and restores access.
    await loginThroughUi(reopened, CITIZEN_MOBILE);
    await expect(reopened.locator('.session-timeout-banner')).toHaveCount(0);
    await reopened.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
    await expect(reopened).toHaveURL(/\/public\/file-complaint/);
    await reopened.close();
  });
});
