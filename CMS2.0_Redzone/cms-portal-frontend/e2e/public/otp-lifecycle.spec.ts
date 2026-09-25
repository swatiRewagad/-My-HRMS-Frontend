import { test, expect } from '../fixtures';
import { solveMathCaptcha, otpAttemptsFor, backdateOtpAttempt, deleteOtpAttempts } from '../utils/test-data';

/**
 * Citizen OTP lifecycle — manual QA cases 1-16 (login, validity, resend, change number).
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * HOW THE 5-MINUTE AND 2-MINUTE CLOCKS ARE PROVEN WITHOUT WAITING FOR THEM
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * Cases 4-6 and 9-12 turn on an OTP being 4:59 or 5:01 old, and on a resend being inside or outside a
 * 2-minute gap. The suite runs `workers: 1` with a 60s per-test timeout, so sleeping to those edges is
 * not merely slow, it is impossible — and a test that sleeps to 4:59 and asserts "accepted" proves
 * nothing about 5:01 anyway.
 *
 * The clock is therefore DRIVEN, not waited out, exactly as e2e/public/session-timeout.spec.ts drives
 * the session clock and as backdateComplaintClosure drives the appeal window:
 *
 *   EXPIRY   OtpService.verifyOtp selects on `used = false AND expires_at > now()`. Rewinding a row's
 *            created_at/expires_at by N seconds makes the OTP exactly N seconds old as far as every
 *            line of shipping code is concerned. Nothing is stubbed: the same query, the same
 *            comparison, the same 410 branch.
 *   COOLDOWN OtpService.resendCooldownRemaining is
 *            `cooldown - secondsBetween(lastAttempt.created_at, now())`. Rewinding created_at is the
 *            only input that function has.
 *
 * INCLUSIVITY IS PROVEN ON BOTH SIDES OF THE SAME BOUNDARY, not by sleeping up to it. For expiry the
 * comparison is `expires_at > now()`, so the test ages one OTP to one second INSIDE the window and
 * another to one second OUTSIDE it and requires opposite outcomes. A single-sided test would pass
 * against a product that never expires anything at all.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * NO FIGURE IN THIS FILE IS A LITERAL THE PRODUCT DOES NOT ALSO READ
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * QA states "5 minutes", "2 minutes" and "3 times". The product reads every one of them from
 * configuration — cms.auth.otp.expiry-minutes, cms.auth.otp.resend-cooldown-seconds and
 * cms.auth.otp.max-resend-per-hour, each overridable by a SYSTEM_CONFIG row — and dev-local still runs
 * the cooldown at 0, even though the expiry now matches prod at 5. Pinning 5 and 2 here would assert the
 * wrong side of the product's own rule and break at the next retune (the standing ruling from the
 * upload-limit work).
 *
 * So the windows are read from the SERVER, out of send-otp's own `expiresInSeconds` and
 * `resendAfterSeconds`, and every boundary is computed from those. A test that says "one second past
 * whatever the server says the window is" stays correct when the window changes; a test that says
 * "301 seconds" does not.
 *
 * The cooldown is 0 in dev-local, which means the 2-minute REFUSAL is unreachable by simply clicking
 * twice. Rather than skip cases 9, 10 and 12, the cooldown is narrowed to a real value with a
 * SYSTEM_CONFIG row for the duration of those tests and RESTORED in afterAll — cms_db is shared with
 * other sessions, and a cooldown left armed would refuse resends in every other suite. The value
 * chosen is deliberately not 120: the assertion is on the message the server RENDERS from whatever is
 * configured, so a test that only passes at 120 would be testing the literal, not the rule.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * WHAT CANNOT BE PROVEN HERE, AND WHY IT IS NOT PRETENDED OTHERWISE
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * Case 3 quotes the SMS body verbatim. There is NO SMS gateway in this repository; the only transport
 * is LoggingOutboundMessageAdapter, which delivers nothing and — by PII policy — logs the body's
 * LENGTH, not its text. So no browser-observable artefact carries the message. What IS asserted here
 * is the part that is observable: the template the server will interpolate, fetched from
 * /api/v1/i18n/translations/en, and that its rendering with real values equals QA's sentence. The
 * composition itself is asserted in OtpServiceTest.OtpMessage (renderOtpMessage is package-private for
 * that purpose), and dispatch-to-handset is asserted NOWHERE, because nothing in this product delivers
 * it. See the report.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * TRAPS PAID FOR ELSEWHERE IN THIS SUITE
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * - Citizen routes are CHILDREN of `public`: /public/login, not /login.
 * - Each test uses its OWN mobile number. OTP state is per-mobile (invalidateActiveOtps, the cooldown
 *   and the hourly limit all key on it), so sharing one number across tests makes each test's result
 *   depend on which ran before it.
 * - CooloffService locks out on repeated FAILED captcha/OTP attempts by fingerprint+IP as well as by
 *   mobile, so tests that deliberately fail verification use a fresh mobile and do not loop.
 * - The suite must run with API_BASE_URL, APP_BASE_URL and UI_BASE_URL all exported; `fixtures.ts`
 *   reroutes the browser's calls, without which the page would talk to a different backend.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const CFG_RESEND_COOLDOWN = 'cms.auth.otp.resend_cooldown_seconds';

/** A distinct mobile per test, so no test inherits another's OTP rows, cooldown or hourly count. */
const MOBILE = {
  uiLogin: '9876540101',
  sixDigits: '9876540102',
  smsBody: '9876540103',
  withinWindow: '9876540104',
  boundary: '9876540105',
  expired: '9876540106',
  resendVisible: '9876540107',
  resendDelivers: '9876540108',
  resendTooSoon: '9876540109',
  resendRapid: '9876540110',
  regeneratedExpired: '9876540111',
  networkLag: '9876540112',
  changeNumberPresent: '9876540113',
  changeNumberClears: '9876540114',
  blankMobile: '9876540115',
  newNumber: '9876540116',
  // QA16 abandons one number for another, so it needs BOTH of its own. Re-using QA14's
  // `changeNumberClears` meant its opening send-otp was that number's SECOND request of the run, which
  // the cooldown correctly refuses — and the test then reported "no OTP step" as though changing the
  // number were broken. Per-test numbers are the invariant this map exists to keep; the abandoned one
  // has to be per-test too.
  changedFrom: '9876540117',
};

type Api = import('@playwright/test').APIRequestContext;

/** Issues an OTP the way the portal does: MATH captcha → send-otp. Returns the parsed body. */
async function sendOtp(request: Api, mobile: string) {
  const captcha = await solveMathCaptcha(request);
  const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
    data: {
      mobile,
      captchaToken: captcha.token,
      captchaAnswer: String(captcha.answer),
      consentGiven: 'true',
      locale: 'en',
    },
    headers: { 'Content-Type': 'application/json' },
  });
  return { status: res.status(), body: await res.json() };
}

async function resendOtp(request: Api, mobile: string) {
  const res = await request.post(`${API_BASE}/api/v1/citizen/auth/resend-otp`, {
    data: { mobile, locale: 'en' },
    headers: { 'Content-Type': 'application/json' },
  });
  return { status: res.status(), body: await res.json() };
}

async function verifyOtp(request: Api, mobile: string, otp: string, sessionId: string) {
  const res = await request.post(`${API_BASE}/api/v1/citizen/auth/verify-otp`, {
    data: { mobile, otp, sessionId },
    headers: { 'Content-Type': 'application/json' },
  });
  return { status: res.status(), body: await res.json() };
}

type Pg = import('@playwright/test').Page;

/**
 * Starts recording every CAPTCHA challenge the page is served, newest last.
 *
 * ── Why a test needs this ───────────────────────────────────────────────────────────────────────
 * The component holds ONE captcha token and renders the question that came with it, but the two are not
 * updated atomically as far as a test is concerned: while a refresh is in flight (loadCaptcha() — fired
 * on entering the screen, on the refresh button, and on Change Phone Number) Angular still renders the
 * PREVIOUS challenge. A test that reads `.captcha-math` at that moment computes the answer to a question
 * the server has already replaced, posts it against the new token, and is correctly rejected — which
 * surfaces as "Invalid CAPTCHA" and reads exactly like a product defect. It is not one; it is the test
 * racing the refresh. (It cost a real failure in QA16 and a LOGIN_COOLOFF row to find.)
 *
 * Recording the responses makes the sync point checkable: the rendered question is trustworthy only once
 * a NEW challenge has arrived AND the DOM is showing it. Comparing the DOM against "the newest response
 * so far" is not sufficient by itself — until the refresh lands, the newest response IS the old
 * challenge and the comparison agrees with itself. The COUNT is what the caller waits on.
 */
function trackCaptchas(page: Pg): string[] {
  const questions: string[] = [];
  page.on('response', async (res) => {
    if (!res.url().includes('/citizen/auth/captcha')) return;
    try {
      const body = await res.json();
      if (body?.audioQuestion) questions.push(body.audioQuestion);
    } catch {
      // A captcha response that is not JSON is not a challenge this helper can track; the caller's
      // wait below will time out and say so, rather than being silently answered wrong here.
    }
  });
  return questions;
}

/**
 * Solves the MATH captcha CURRENTLY bound to the component's token.
 *
 * MATH is the only variant a test can solve: generateVisualCaptcha deliberately withholds the answer
 * from the client (it is only legible from the rendered PNG), and the accessible MATH question is plain
 * text by design. So this exercises a real citizen path rather than weakening the control.
 *
 * `seen` is how many challenges had already arrived BEFORE the action that triggers this refresh. The
 * wait is then for challenge seen+1 to exist and for the DOM to be showing it — the only state in which
 * the answer computed here belongs to the token the component is actually holding.
 */
async function solveRenderedCaptcha(page: Pg, tracked: string[], seen = 0): Promise<number> {
  const captchaQuestion = page.locator('.captcha-math');
  let answer = 0;

  await expect(async () => {
    if (!(await captchaQuestion.isVisible().catch(() => false))) {
      // The screen may be showing the VISUAL variant; the audio control is how an accessible citizen
      // switches to MATH, and it is the same switch a test uses. (It fetches a challenge of its own,
      // hence `toBeGreaterThan` below rather than an exact count.)
      await page.locator('.icon-btn[aria-label="Listen to CAPTCHA audio"]').click();
    }
    await expect(captchaQuestion).toBeVisible({ timeout: 3000 });

    // A challenge issued AFTER the action being waited on has landed. Without this the two assertions
    // that follow compare the stale DOM against the stale response and trivially agree — which is
    // exactly how QA16 came to answer the abandoned attempt's question against the new token.
    expect(tracked.length,
      'no CAPTCHA challenge arrived after the action that should have refreshed it')
      .toBeGreaterThan(seen);

    const shown = ((await captchaQuestion.textContent()) || '').trim();
    const latest = (tracked[tracked.length - 1] || '').trim();
    // THE SYNC POINT: the question on screen is the one that arrived with the token now held.
    expect(shown, 'the rendered CAPTCHA is still the previous challenge').toBe(latest);
    // And the refresh has finished. loadCaptcha() CLEARS captchaInput when its response lands, so an
    // answer typed while one is in flight is wiped — the Send OTP button then stays disabled on
    // `!captchaInput` and the step never advances. Waiting for the control to settle is what makes the
    // subsequent fill stick.
    await expect(page.locator('.captcha-input')).toBeEnabled({ timeout: 3000 });
    await expect(page.locator('.captcha-input')).toHaveValue('');

    const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(shown);
    expect(match, `captcha question was not solvable: ${shown}`).toBeTruthy();
    const [, a, op, b] = match!;
    answer = op.toLowerCase() === 'plus' ? Number(a) + Number(b) : Number(a) - Number(b);
  }).toPass({ timeout: 20000 });

  return answer;
}

/**
 * Solves the CAPTCHA and types the answer, retrying if a refresh lands between the two.
 *
 * solveRenderedCaptcha syncs the RENDERED question against the LAST challenge seen on the wire, which is
 * sound only once the refresh in flight has arrived. While one is still outstanding, the newest tracked
 * challenge is the OUTGOING one and it matches what is still on screen — so the sync point is satisfied,
 * the stale question is solved, and loadCaptcha() then blanks captchaInput as its response lands. Send OTP
 * is disabled on `!captchaInput`, so the click became a no-op with nothing on screen to explain it.
 *
 * Counting responses instead would need every caller to know how many refreshes its own actions triggered.
 * Confirming the typed answer SURVIVED needs no such bookkeeping and closes the window whatever caused it.
 *
 * Returns the answer actually sitting in the field.
 */
async function solveAndFillCaptcha(page: Pg, tracked: string[], seen = 0): Promise<number> {
  let answer = 0;
  await expect(async () => {
    answer = await solveRenderedCaptcha(page, tracked, seen);
    await page.locator('.captcha-input').fill(String(answer));
    // A wipe here means a refresh landed after the solve: the answer belongs to a challenge the server
    // has already discarded, so it must be re-solved rather than re-typed.
    await expect(page.locator('.captcha-input')).toHaveValue(String(answer), { timeout: 2000 });
  }).toPass({ timeout: 25000 });
  return answer;
}

/**
 * Fills in the login screen up to (and including) Send OTP, leaving the page on the OTP step.
 */
async function reachOtpStep(page: Pg, mobile: string) {
  const tracked = trackCaptchas(page);
  await page.goto('/public/login', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
  await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 15000 });

  await page.locator('#mobile-input').fill(mobile);
  await solveAndFillCaptcha(page, tracked);
  await page.locator('.consent-section input[type="checkbox"]').check();

  const sendOtpBtn = page.locator('.send-otp-btn');
  await expect(sendOtpBtn).toBeEnabled({ timeout: 10000 });
  await sendOtpBtn.click();

  await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });
}

test.describe('Citizen OTP lifecycle', () => {

  /**
   * Every run starts from NO OTP history on any of these numbers.
   *
   * This is not tidiness, it is correctness. OTP state is keyed on the MOBILE NUMBER, and three separate
   * controls read that history: the resend cooldown (measured from the newest row), hasRecentOtpRequest,
   * and the per-mobile hourly request limit. A row surviving from a previous run — the cooldown tests
   * leave one by construction — makes the NEXT run's first send-otp for that number a 429
   * RESEND_COOLDOWN, which is the product behaving exactly as specified while the suite reports a
   * failure. beforeAll rather than afterAll alone, because the state that breaks a run is the state it
   * INHERITS, and a crashed or cancelled run never reaches its teardown.
   */
  test.beforeAll(async () => {
    for (const mobile of Object.values(MOBILE)) deleteOtpAttempts(mobile);
  });

  // Nothing is left behind for the next session either: these mobiles are only ever used by this spec.
  test.afterAll(async () => {
    for (const mobile of Object.values(MOBILE)) deleteOtpAttempts(mobile);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 1 — Launch the portal, go to login, valid mobile + valid captcha, Send OTP, enter the OTP,
  // Verify → the citizen lands on the eligibility-question page.
  //
  // Driven in a real browser from the home page, because "launch the portal and click File a
  // Complaint" is the case. /public/file-complaint is behind publicAuthGuard, so ARRIVING there is
  // itself the proof that the session the OTP created is real.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA1: valid mobile, captcha and OTP log the citizen in and land on the eligibility questions', async ({ page }) => {
    const tracked = trackCaptchas(page);
    await page.goto('/public', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // "File a Complaint" from the landing page. The guard bounces an anonymous visitor to the login
    // screen with a returnUrl, which is the journey QA describes.
    await page.locator('a[routerLink="/public/file-complaint"]').first().click();
    await expect(page).toHaveURL(/\/public\/login/, { timeout: 15000 });

    await page.locator('#mobile-input').fill(MOBILE.uiLogin);
    await solveAndFillCaptcha(page, tracked);
    await page.locator('.consent-section input[type="checkbox"]').check();

    const sendOtpBtn = page.locator('.send-otp-btn');
    await expect(sendOtpBtn).toBeEnabled({ timeout: 10000 });
    await sendOtpBtn.click();

    await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });

    const boxes = page.locator('.otp-box');
    if (!(await boxes.first().inputValue())) {
      for (let i = 0; i < 6; i++) await boxes.nth(i).fill('123456'[i]);
    }
    await page.locator('.verify-btn').click();

    // The guarded route renders, which no unauthenticated visitor can reach.
    await expect(page).toHaveURL(/\/public\/file-complaint/, { timeout: 20000 });
    await expect(page.locator('.entity-select-dropdown')).toBeVisible({ timeout: 20000 });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 2 — The OTP is 6 digits.
  //
  // Asserted on the SCREEN and on the ISSUED CODE, because they are two separate claims and either
  // can be wrong alone: six input boxes accepting one character each, and a code of six digits.
  // The length is cms.auth.otp.length, so the count is taken from the markup the product renders
  // rather than written as a 6 in two places.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA2: the OTP is six digits and the screen collects exactly six', async ({ page, request }) => {
    const { status, body } = await sendOtp(request, MOBILE.sixDigits);
    expect(status, `send-otp failed: ${JSON.stringify(body)}`).toBe(200);
    expect(body.devOtp, 'dev-local did not return the OTP, so its length cannot be checked').toBeTruthy();
    expect(body.devOtp).toMatch(/^\d{6}$/);

    await reachOtpStep(page, MOBILE.sixDigits);
    const boxes = page.locator('.otp-box');
    await expect(boxes).toHaveCount(6);
    for (let i = 0; i < 6; i++) {
      await expect(boxes.nth(i)).toHaveAttribute('maxlength', '1');
    }
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 3 — SMS body: "Your OTP for Mobile Number authentication on RBI CMS is 211067. This is valid
  // only for 5 minutes".
  //
  // WHAT IS AND IS NOT PROVEN. There is no SMS gateway in this product and the no-op transport logs
  // the body's LENGTH, not its text, so nothing observable to a browser carries the message. What is
  // asserted is the template the server will interpolate and the sentence it produces — the two
  // things that decide what a citizen would read if a gateway existed. Delivery is asserted nowhere,
  // deliberately.
  //
  // The validity is NOT pinned to 5 even though every environment now enforces 5: it is taken from the
  // same send-otp response the portal reads, so this survives a retune and fails if the prose and the
  // enforced window ever disagree — which is the defect this case actually guards.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA3: the OTP message is composed in QA\'s exact wording, quoting the ENFORCED validity', async ({ request }) => {
    const bundleRes = await request.get(`${API_BASE}/api/v1/i18n/translations/en`);
    expect(bundleRes.ok(), 'the English translation bundle could not be fetched').toBeTruthy();
    const bundle = await bundleRes.json();

    const template = bundle['login.otp_sms_body'];
    expect(template,
      'login.otp_sms_body is absent — no OTP message exists for the citizen to receive').toBeTruthy();

    // The figures must be placeholders, not literals, or the message drifts from what is enforced.
    expect(template).toContain('{{otp}}');
    expect(template).toContain('{{minutes}}');

    const { status, body } = await sendOtp(request, MOBILE.smsBody);
    expect(status, `send-otp failed: ${JSON.stringify(body)}`).toBe(200);
    const otp = body.devOtp as string;
    const minutes = Number(body.expiresInSeconds) / 60;

    const rendered = template
      .replace('{{otp}}', otp)
      .replace('{{minutes}}', String(minutes));

    expect(rendered).toBe(
      `Your OTP for Mobile Number authentication on RBI CMS is ${otp}. ` +
      `This is valid only for ${minutes} minutes`);
    // Nothing unresolved may survive: a raw {{brace}} reaching a citizen is a defect this product has
    // already shipped once.
    expect(rendered).not.toContain('{{');
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 4 — An OTP entered within the validity window verifies successfully.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA4: an OTP entered inside the validity window verifies successfully', async ({ request }) => {
    const { body } = await sendOtp(request, MOBILE.withinWindow);
    expect(body.devOtp).toBeTruthy();

    const verified = await verifyOtp(request, MOBILE.withinWindow, body.devOtp, body.sessionId);
    expect(verified.status, `verify failed: ${JSON.stringify(verified.body)}`).toBe(200);
    expect(verified.body.success).toBe(true);
    expect(verified.body.token, 'verification succeeded but issued no session token').toBeTruthy();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 5 — The boundary: at 4:59 the OTP is ACCEPTED, at 5:01 it is REJECTED with
  // "OTP has expired. Please request a new one".
  //
  // Both sides of ONE boundary, computed from the window the server itself reports, and reached by
  // rewinding the row rather than by sleeping to the edge. A single-sided version of this test would
  // pass against a product that never expires an OTP at all.
  //
  // Each side uses its own mobile: generating the second OTP for the same number would retire the
  // first (UST8, invalidateActiveOtps), so they cannot share one.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA5: one second inside the window is accepted, one second past it is refused as expired', async ({ request }) => {
    // ── inside ──
    const inside = await sendOtp(request, MOBILE.boundary);
    expect(inside.body.devOtp).toBeTruthy();
    const windowSeconds = Number(inside.body.expiresInSeconds);
    expect(windowSeconds, 'send-otp did not report the validity window').toBeGreaterThan(0);

    // Age it to one second short of the window: verifyOtp's predicate is `expires_at > now()`.
    backdateOtpAttempt(MOBILE.boundary, windowSeconds - 1);
    const accepted = await verifyOtp(request, MOBILE.boundary, inside.body.devOtp, inside.body.sessionId);
    expect(accepted.status,
      `an OTP one second inside the ${windowSeconds}s window was refused: ${JSON.stringify(accepted.body)}`)
      .toBe(200);
    expect(accepted.body.success).toBe(true);

    // ── outside ──
    const outside = await sendOtp(request, MOBILE.expired);
    expect(outside.body.devOtp).toBeTruthy();
    backdateOtpAttempt(MOBILE.expired, windowSeconds + 1);

    const refused = await verifyOtp(request, MOBILE.expired, outside.body.devOtp, outside.body.sessionId);
    expect(refused.status,
      `an OTP one second PAST the ${windowSeconds}s window was still accepted`).toBe(410);
    expect(refused.body.error).toBe('OTP_EXPIRED');
    // QA quotes the sentence without its closing full stop; the product terminates it. Asserted as a
    // prefix so both readings are satisfied, with the punctuation raised as an open question rather
    // than silently decided by this test.
    expect(refused.body.message).toContain('OTP has expired. Please request a new one');
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 6 — An expired OTP shows the expiry message and the citizen cannot proceed.
  //
  // "Cannot proceed" is asserted as the absence of a SESSION, not merely as an error on screen: a
  // product that displayed the message and still issued a token would pass a message-only check.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA6: an expired OTP is refused with the expiry message and issues no session', async ({ page, request }) => {
    const { body } = await sendOtp(request, MOBILE.expired);
    expect(body.devOtp).toBeTruthy();
    const windowSeconds = Number(body.expiresInSeconds);

    await reachOtpStep(page, MOBILE.expired);

    // Expire the code the page is now holding, then submit it exactly as the citizen would.
    backdateOtpAttempt(MOBILE.expired, windowSeconds + 60);

    const boxes = page.locator('.otp-box');
    const shown = await boxes.first().inputValue();
    if (!shown) {
      for (let i = 0; i < 6; i++) await boxes.nth(i).fill('123456'[i]);
    }
    await page.locator('.verify-btn').click();

    // `.error-banner`, not `.field-error`. An expiry refusal returns the citizen to the mobile step, and
    // `.field-error` is inside the OTP step's @if — so the message HAS to live on the banner that renders
    // on both steps or it is destroyed in the same tick it is set. (That was the defect this case found:
    // the screen reverted with nothing said. Fixed in the component; the selector follows the fix.)
    await expect(page.locator('.error-banner'))
      .toContainText('OTP has expired. Please request a new one', { timeout: 15000 });

    // No session was created, so the citizen genuinely cannot proceed.
    const session = await page.evaluate(() => sessionStorage.getItem('cms_public_session'));
    expect(session, 'an expired OTP still created a citizen session').toBeNull();
    await expect(page).toHaveURL(/\/public\/login/);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 7 — A Resend OTP button is visible below the OTP input.
  //
  // "Below" is asserted geometrically, not by DOM order: a control that exists but renders above the
  // input, or off-screen, is not what the case describes.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA7: a Resend OTP control is visible below the OTP input', async ({ page }) => {
    await reachOtpStep(page, MOBILE.resendVisible);

    const resend = page.locator('.otp-resend .link-btn');
    await expect(resend).toBeVisible();
    await expect(resend).toContainText(/Resend/i);

    const inputs = await page.locator('.otp-inputs').boundingBox();
    const button = await resend.boundingBox();
    expect(inputs, 'the OTP input group has no box').toBeTruthy();
    expect(button, 'the Resend control has no box').toBeTruthy();
    expect(button!.y, 'Resend OTP does not render below the OTP input')
      .toBeGreaterThan(inputs!.y);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 8 — Clicking Resend delivers a NEW OTP to the registered mobile.
  //
  // "A NEW OTP" is the whole case, so what is asserted is a new OTP_ATTEMPTS row for that mobile and
  // the previous one retired — not merely that the click produced a 200. Before this batch the button
  // called no API at all: it reset the form to step 1, so a check that the screen "reacted" would have
  // passed against a product that issued nothing.
  //
  // dev-local returns a constant 123456, so the CODE cannot distinguish old from new. The ROW can.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA8: Resend issues a new OTP to the same mobile and retires the previous one', async ({ page, request }) => {
    const first = await sendOtp(request, MOBILE.resendDelivers);
    expect(first.status).toBe(200);
    const before = otpAttemptsFor(MOBILE.resendDelivers);
    expect(before.length, 'send-otp recorded no OTP at all').toBeGreaterThan(0);

    await reachOtpStep(page, MOBILE.resendDelivers);

    const resendCalls: string[] = [];
    page.on('request', req => {
      if (req.url().includes('/resend-otp')) resendCalls.push(req.url());
    });

    const resend = page.locator('.otp-resend .link-btn');
    await expect(resend).toBeEnabled({ timeout: 10000 });
    await resend.click();

    // The click must actually ask the server for a code.
    await expect.poll(() => resendCalls.length, { timeout: 15000 }).toBeGreaterThan(0);

    // And the server must have issued one: a new live row for this mobile, with every earlier row
    // retired so a superseded code cannot still be used.
    await expect.poll(() => otpAttemptsFor(MOBILE.resendDelivers).filter(r => r.used === '0').length,
      { timeout: 15000 }).toBe(1);

    const after = otpAttemptsFor(MOBILE.resendDelivers);
    expect(after.length, 'Resend did not create a new OTP row').toBeGreaterThan(before.length);
    // The citizen stays on the OTP screen — Resend is not "start over".
    await expect(page.locator('.otp-inputs')).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 9-10-12 — The resend cooldown.
  //
  //   9  Resend inside the gap → refused with "OTP can only be regenerated after 2 minutes."
  //   10 Resend clicked rapidly / more than 3 times → restricted, SAME message
  //   12 Multiple OTPs from network lag → the citizen must wait and use Resend
  //
  // One control answers all three: the server-side gap. There is no separate click counter, and
  // inventing one would be a second rule to keep in step with the first.
  //
  // dev-local configures the gap as 0, so it is narrowed here with a SYSTEM_CONFIG row and RESTORED
  // in afterAll — cms_db is shared, and a cooldown left armed would refuse resends in every other
  // session's suite. The value is not 120: the assertion is on the sentence the server RENDERS from
  // whatever is configured, so a test that only passed at 120 would be pinning the literal rather
  // than the rule. 180s renders "after 3 minutes", which proves the interpolation is live.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test.describe('resend cooldown (QA 9, 10, 12)', () => {
    const COOLDOWN_SECONDS = 180;
    let previousCooldown = '';

    // setSystemConfig waits out SystemConfigService's 30-second cache TTL (it sleeps 35s), and a hook
    // inherits the 60s test timeout — too close to race, and a timed-out afterAll would leave the
    // narrowed cooldown behind in a database every other session shares. Both hooks get headroom.
    test.beforeAll(async () => {
      test.setTimeout(120_000);
      const { readSystemConfig, setSystemConfig } = await import('../utils/test-data');
      previousCooldown = readSystemConfig(CFG_RESEND_COOLDOWN);
      // A value equal to the one THIS block installs is not a pre-existing operator setting, it is the
      // debris of a run that died before its teardown. Treating it as "the original" is how the row
      // perpetuated itself: the next run dutifully restored 180, and QA8's Resend button stayed disabled
      // behind a countdown for a gap nobody had configured. Debris is restored as ABSENCE.
      if (previousCooldown === String(COOLDOWN_SECONDS)) previousCooldown = '';
      await setSystemConfig(CFG_RESEND_COOLDOWN, String(COOLDOWN_SECONDS));
    });

    test.afterAll(async () => {
      test.setTimeout(120_000);
      const { setSystemConfig, clearSystemConfig } = await import('../utils/test-data');
      // Restored to exactly what was there, INCLUDING absence — leaving a row behind would change
      // the default every other session reads.
      if (previousCooldown) {
        // setSystemConfig already waits out the TTL.
        await setSystemConfig(CFG_RESEND_COOLDOWN, previousCooldown);
      } else {
        clearSystemConfig(CFG_RESEND_COOLDOWN);
        // The TTL wait is REQUIRED here, not merely tidy. SystemConfigService caches for 30s, so
        // deleting the row does not un-arm the cooldown immediately: the very next test (QA11, which
        // needs dev-local's cooldown of 0 so its resend is accepted) read the stale 180 and was
        // refused with RESEND_COOLDOWN — a harness failure that looked like a product one.
        await new Promise((resolve) => setTimeout(resolve, 35_000));
      }
    });

    test('QA9: a resend inside the cooldown is refused, quoting the CONFIGURED gap in minutes', async ({ request }) => {
      const first = await sendOtp(request, MOBILE.resendTooSoon);
      expect(first.status, `send-otp failed: ${JSON.stringify(first.body)}`).toBe(200);
      // The server now reports a gap, so the portal can show an honest countdown.
      expect(Number(first.body.resendAfterSeconds)).toBe(COOLDOWN_SECONDS);

      const second = await resendOtp(request, MOBILE.resendTooSoon);
      expect(second.status, 'a resend inside the cooldown was accepted').toBe(429);
      expect(second.body.error).toBe('RESEND_COOLDOWN');
      // The sentence is QA's, with the figure rendered from configuration rather than hardcoded:
      // at 180s it must say 3, which is what proves it is not a literal.
      expect(second.body.message).toBe('OTP can only be regenerated after 3 minutes.');
      expect(Number(second.body.retryAfterSeconds)).toBeGreaterThan(0);
      expect(Number(second.body.retryAfterSeconds)).toBeLessThanOrEqual(COOLDOWN_SECONDS);
    });

    test('QA10: repeated rapid resends stay restricted with the same message', async ({ request }) => {
      const first = await sendOtp(request, MOBILE.resendRapid);
      expect(first.status).toBe(200);

      // Four in quick succession — past QA's "more than 3 times". Every one must be refused
      // identically; a control that relented on the 4th would be no control.
      for (let attempt = 1; attempt <= 4; attempt++) {
        const res = await resendOtp(request, MOBILE.resendRapid);
        expect(res.status, `rapid resend ${attempt} was accepted`).toBe(429);
        expect(res.body.error).toBe('RESEND_COOLDOWN');
        expect(res.body.message).toBe('OTP can only be regenerated after 3 minutes.');
      }

      // And no extra OTP was minted by any of them: exactly the one live code from the original send.
      expect(otpAttemptsFor(MOBILE.resendRapid).filter(r => r.used === '0').length).toBe(1);
    });

    test('QA12: once the configured gap has elapsed, Resend issues a fresh OTP', async ({ request }) => {
      // QA12's scenario is a citizen who has triggered several sends through network lag and has no
      // countdown to go by: the remedy is to wait out the gap and resend. Waiting is simulated by
      // rewinding the last attempt — resendCooldownRemaining has no other input.
      const first = await sendOtp(request, MOBILE.networkLag);
      expect(first.status).toBe(200);

      const blocked = await resendOtp(request, MOBILE.networkLag);
      expect(blocked.status, 'the gap was not being enforced, so elapsing it proves nothing').toBe(429);

      backdateOtpAttempt(MOBILE.networkLag, COOLDOWN_SECONDS + 1);

      const allowed = await resendOtp(request, MOBILE.networkLag);
      expect(allowed.status,
        `a resend after the ${COOLDOWN_SECONDS}s gap was still refused: ${JSON.stringify(allowed.body)}`)
        .toBe(200);
      expect(allowed.body.devOtp).toBeTruthy();
      expect(allowed.body.message).toContain('new OTP');
    });
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 11 — A REGENERATED OTP, entered after the validity window, is also refused as expired.
  //
  // Worth its own case because a resent code takes a different code path (the resend endpoint) and
  // could plausibly be written with no expiry at all.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA11: a regenerated OTP expires on the same rule as the original', async ({ request }) => {
    const first = await sendOtp(request, MOBILE.regeneratedExpired);
    expect(first.status).toBe(200);
    const windowSeconds = Number(first.body.expiresInSeconds);

    // The cooldown is 0 in dev-local outside the describe block above, so this resend is accepted.
    const again = await resendOtp(request, MOBILE.regeneratedExpired);
    expect(again.status, `resend failed: ${JSON.stringify(again.body)}`).toBe(200);
    expect(again.body.devOtp).toBeTruthy();

    backdateOtpAttempt(MOBILE.regeneratedExpired, windowSeconds + 1);

    const refused = await verifyOtp(
      request, MOBILE.regeneratedExpired, again.body.devOtp, again.body.sessionId);
    expect(refused.status, 'a regenerated OTP never expired').toBe(410);
    expect(refused.body.error).toBe('OTP_EXPIRED');
    expect(refused.body.message).toContain('OTP has expired. Please request a new one');
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 13 — A Change Phone Number control is present on the OTP screen.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA13: a Change Phone Number control is present on the OTP screen', async ({ page }) => {
    await reachOtpStep(page, MOBILE.changeNumberPresent);

    const change = page.locator('.link-btn.change-number');
    await expect(change).toBeVisible();
    await expect(change).toHaveText('Change Phone Number');
    await expect(change).toBeEnabled();
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 14 — Clicking it returns to the mobile + captcha screen, and the OLD number must NOT be
  // pre-filled or visible.
  //
  // The second half is the defect this case catches: the handler used to leave `mobile` populated, so
  // a citizen who clicked BECAUSE they had mistyped their number was handed the mistake back.
  // "Not visible" is checked against the whole page body, not just the input, because the OTP step
  // also printed the last four digits.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA14: Change Phone Number returns to the mobile screen with the old number cleared', async ({ page }) => {
    await reachOtpStep(page, MOBILE.changeNumberClears);

    await page.locator('.link-btn.change-number').click();

    // Back on step 1, with a captcha to solve again.
    await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.otp-inputs')).toHaveCount(0);

    await expect(page.locator('#mobile-input')).toHaveValue('');
    await expect(page.locator('.captcha-input')).toHaveValue('');

    // The old number must not survive anywhere on the screen, including the masked ****NNNN form.
    const bodyText = (await page.locator('body').textContent()) || '';
    expect(bodyText).not.toContain(MOBILE.changeNumberClears);
    expect(bodyText).not.toContain(MOBILE.changeNumberClears.slice(-4));
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 15 — Send OTP with the mobile field BLANK → "Mobile number is required to request OTP."
  //
  // Asserted on BOTH sides. On the screen, because that is where a citizen reads it; and on the API,
  // because a client-side message alone leaves a scripted caller with the wrong error. The server used
  // to answer a blank number with "Enter a valid 10-digit Indian mobile number…", which tells someone
  // who typed nothing that what they typed was wrong.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA15: Send OTP with a blank mobile is refused with the required-field message', async ({ page, request }) => {
    // ── the API ──
    const captcha = await solveMathCaptcha(request);
    const res = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
      data: {
        mobile: '',
        captchaToken: captcha.token,
        captchaAnswer: String(captcha.answer),
        consentGiven: 'true',
        locale: 'en',
      },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.error).toBe('MOBILE_REQUIRED');
    expect(body.message).toBe('Mobile number is required to request OTP.');

    // ── the screen ──
    await page.goto('/public/login', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 15000 });

    // Captcha and consent supplied, mobile deliberately left blank, so the ONLY thing missing is the
    // number and the message can only be about that.
    await page.locator('.captcha-input').fill('1234');
    await page.locator('.consent-section input[type="checkbox"]').check();

    const sendOtpBtn = page.locator('.send-otp-btn');
    // A disabled button explains nothing to the citizen — the control must be reachable for the
    // message to exist at all.
    await expect(sendOtpBtn).toBeEnabled({ timeout: 10000 });
    await sendOtpBtn.click();

    // `.error-banner` is where the mobile step reports a refusal (loginError); a guessed
    // `.error-message, .form-error` matched nothing, which is a HARNESS error, not a missing message.
    await expect(page.locator('.error-banner'))
      .toContainText('Mobile number is required to request OTP.', { timeout: 10000 });
    // And nothing was sent: no OTP row for a request that never named a number.
    await expect(page.locator('.otp-inputs')).toHaveCount(0);
  });

  // ═══════════════════════════════════════════════════════════════════════════════════════════
  // QA 16 — After changing the number, the OTP goes to the NEW number, not the old one.
  //
  // Asserted in the database, per mobile: the new number gets a live OTP row and the old number gets
  // NO new row. A check that merely watched the request payload would miss a server that addressed
  // the wrong recipient.
  // ═══════════════════════════════════════════════════════════════════════════════════════════
  test('QA16: after changing the number the OTP is issued to the NEW mobile, not the old one', async ({ page }) => {
    const tracked = trackCaptchas(page);
    await reachOtpStep(page, MOBILE.changedFrom);
    const oldBefore = otpAttemptsFor(MOBILE.changedFrom).length;
    expect(oldBefore, 'the first send recorded nothing, so this test would prove nothing').toBeGreaterThan(0);

    // Counted BEFORE the click that triggers the refresh, so the solve waits for the challenge that
    // click causes rather than the abandoned attempt's, which is still rendered for a moment after it.
    const seen = tracked.length;
    await page.locator('.link-btn.change-number').click();
    await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 10000 });

    await page.locator('#mobile-input').fill(MOBILE.newNumber);

    // A fresh captcha is required — the previous challenge belonged to the abandoned attempt. Change
    // Number triggers loadCaptcha(), so a refresh is in flight when this runs: the solve waits for the
    // NEW challenge to be both received and rendered, and the fill is retried until it survives.
    const answer = await solveAndFillCaptcha(page, tracked, seen);
    await page.locator('.consent-section input[type="checkbox"]').check();
    // Both entries must still be there when the click happens: the button is disabled on
    // `!captchaInput`, so a refresh landing between fill and click leaves it inert and the click is a
    // no-op with nothing on screen to say why.
    await expect(page.locator('.captcha-input')).toHaveValue(String(answer));
    const sendOtpBtn = page.locator('.send-otp-btn');
    await expect(sendOtpBtn).toBeEnabled({ timeout: 10000 });
    await sendOtpBtn.click();

    await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });

    // The NEW number has a live code.
    await expect.poll(() => otpAttemptsFor(MOBILE.newNumber).filter(r => r.used === '0').length,
      { timeout: 15000 }).toBe(1);
    // The OLD number gained nothing.
    expect(otpAttemptsFor(MOBILE.changedFrom).length,
      'an OTP was issued to the OLD number after the citizen changed it').toBe(oldBefore);
    // And the screen is addressed to the new number.
    await expect(page.locator('.otp-instruction'))
      .toContainText(MOBILE.newNumber.slice(-4));
  });
});
