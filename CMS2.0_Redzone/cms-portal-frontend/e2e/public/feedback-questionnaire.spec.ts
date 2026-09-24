import { test, expect } from '../fixtures';
import {
  createTestComplaint,
  advanceToStatus,
  seedCitizenSession,
} from '../utils/test-data';
import { installCorsShim } from './browser-api';

const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * UST109 / FR-G-038 — Submit Feedback: the citizen questionnaire.
 *
 * EXTENDS e2e/public/feedback.spec.ts, which already covers: happy-path submit + read-back, the 409
 * duplicate message, the whole mandatory-validation message walk, and feedbackText > 500 showing a
 * limit error. Nothing here repeats those.
 *
 * ── What these tests assert, and why some of them are expected to be red ────────────────────────
 *
 * The manual cases describe a questionnaire reached by AUTHENTICATING and then PICKING a closed
 * complaint from a list. The shipped page (submit-feedback.component.html:10) is a bare text input
 * for the complaint reference — there is no list, and nothing constrains the reference to a closed
 * complaint the logged-in citizen actually owns. FeedbackService.getClosedComplaints
 * (src/app/services/feedback.service.ts:67) is the only code that would build such a list; it is
 * never called by any component, and the endpoint it targets,
 * GET /api/v1/complaints/by-phone/{phone}, does not exist in cms-backend (404).
 *
 * The cases are written as specified anyway. A green assertion invented to match the shipped page
 * would hide the gap; a red one names it.
 */

const ALNUM_500 = 'A1b2C3d4E5'.repeat(50);                    // exactly 500, no special characters
const ALNUM_510 = ALNUM_500 + 'Z9y8X7w6V5';                   // 510, still no special characters
const SPECIALS_SHORT = 'Refund of Rs. 5,000 @ 12% #ref! <ok>'; // well under 500, special chars only

/** The exact message UST109 scenarios 4 and 5 require for both over-length AND special characters. */
const LIMIT_AND_SPECIALS_MESSAGE =
  'Feedback must be within 500 characters and cannot contain special characters.';

/**
 * Lets the CREDENTIALED citizen-auth calls through as well as the ordinary ones.
 *
 * ── HARNESS, NOT PRODUCT ────────────────────────────────────────────────────────────────────────
 *
 * installCorsShim answers every request with `access-control-allow-origin: *`. That is fine for the
 * feedback endpoints, but CitizenAuthApiService sends the auth calls with `withCredentials: true`
 * (src/app/services/citizen-auth-api.service.ts:43,51,58,64), and Chromium refuses a credentialed
 * response whose allow-origin is the wildcard:
 *
 *   Access to XMLHttpRequest at '…/citizen/auth/captcha?type=VISUAL' from origin
 *   'http://localhost:4202' has been blocked by CORS policy: The value of the
 *   'Access-Control-Allow-Origin' header in the response must not be the wildcard '*' when the
 *   request's credentials mode is 'include'.
 *
 * The visible effect is "Failed to load CAPTCHA. Please try again." on the login page, so the OTP
 * tests could not even reach the OTP step. cms-backend answers this correctly by itself — verified
 * directly: `Access-Control-Allow-Origin: http://localhost:4202` plus
 * `Access-Control-Allow-Credentials: true`. It is purely the wildcard in the shim.
 *
 * Registered AFTER installCorsShim so it wins (Playwright matches handlers in reverse registration
 * order) and echoes the request's own Origin instead of the wildcard. Nothing is weakened: the
 * request, the backend and the response body are all real, exactly as installCorsShim intends.
 * Kept local rather than edited into browser-api.ts, which five other suites share.
 */
const COMPILED_BASE = 'http://localhost:8082';

async function allowCredentialedCors(page: any): Promise<void> {
  const target = (process.env['API_BASE_URL'] || COMPILED_BASE).replace(/\/+$/, '');

  for (const pattern of [`${COMPILED_BASE}/**`, `${target}/**`]) {
    await page.route(pattern, async (route: any) => {
      const request = route.request();
      const url = request.url().replace(COMPILED_BASE, target);
      const origin = request.headers()['origin'] || '';

      const cors: Record<string, string> = {
        'access-control-allow-origin': origin || '*',
        'access-control-allow-headers': '*',
        'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS',
      };
      // Only legal alongside a concrete origin — with '*' it is what Chromium rejects.
      if (origin) cors['access-control-allow-credentials'] = 'true';

      if (request.method() === 'OPTIONS') {
        await route.fulfill({ status: 204, headers: cors, body: '' });
        return;
      }

      try {
        const outbound = { ...request.headers() };
        delete outbound['origin'];
        delete outbound['referer'];
        delete outbound['sec-fetch-site'];
        delete outbound['sec-fetch-mode'];

        const response = await route.fetch({ url, headers: outbound });
        const inbound = { ...response.headers(), ...cors };
        delete inbound['set-cookie'];
        delete inbound['content-encoding'];
        delete inbound['content-length'];
        await route.fulfill({ status: response.status(), headers: inbound, body: await response.body() });
      } catch (error) {
        await route.fulfill({
          status: 502,
          headers: { ...cors, 'content-type': 'application/json' },
          body: JSON.stringify({ error: `e2e shim could not reach ${url}: ${String(error)}` }),
        });
      }
    });
  }
}

/** Every browser test here posts from the page, so the shim is mandatory. */
test.beforeEach(async ({ page }) => {
  await installCorsShim(page);
  await allowCredentialedCors(page);
});

/** Locators for the form, kept in one place so a template change breaks one line, not twelve. */
const form = {
  ref: (page: any) => page.locator('input[name="complaintId"]'),
  overall: (page: any) => page.locator('[aria-labelledby="overall-rating-label"] .star-btn'),
  ease: (page: any) => page.locator('[aria-labelledby="ease-rating-label"] .star-btn'),
  redress: (page: any) => page.locator('[aria-labelledby="redress-rating-label"] .star-btn'),
  timeliness: (page: any) => page.locator('[aria-labelledby="timeliness-rating-label"] .star-btn'),
  communication: (page: any) => page.locator('[aria-labelledby="communication-rating-label"] .star-btn'),
  satisfaction: (page: any) => page.locator('[aria-labelledby="satisfaction-rating-label"] .star-btn'),
  source: (page: any) => page.locator('select[name="sourceOfInformation"]'),
  sourceOther: (page: any) => page.locator('input[name="sourceOtherText"]'),
  feedbackText: (page: any) => page.locator('textarea[name="feedbackText"]'),
  suggestions: (page: any) => page.locator('textarea[name="suggestions"]'),
  submit: (page: any) => page.locator('button.btn-primary:has(i.pi-send), button:has-text("Submit")'),
  error: (page: any) => page.locator('.error-msg'),
  success: (page: any) => page.locator('.success-card'),
};

/** Opens the questionnaire with a pre-seeded citizen session (publicAuthGuard would else redirect). */
async function openFeedbackForm(page: any, mobile = '9876543210') {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/feedback`);
  await page.waitForLoadState('networkidle');
  await expect(form.ref(page)).toBeVisible({ timeout: 15000 });
}

/** Fills only the five mandatory answers. Optional Q4 (free text) / Q6 (suggestions) stay blank. */
async function answerMandatoryOnly(
  page: any,
  complaintNumber: string,
  opts: { source?: string; overall?: number; ease?: number; redress?: number } = {}
) {
  await form.ref(page).fill(complaintNumber);
  await form.overall(page).nth((opts.overall ?? 5) - 1).click();
  await form.ease(page).nth((opts.ease ?? 1) - 1).click();
  await form.redress(page).nth((opts.redress ?? 3) - 1).click();
  await form.source(page).selectOption(opts.source ?? 'News/Media');
  await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();
}

/**
 * Logs in through the REAL public login page with mobile number + OTP.
 *
 * The page defaults to a VISUAL CAPTCHA whose answer is deliberately never sent to the client, so it
 * cannot be solved from a test. The accessibility button (pi-volume-up → playCaptchaAudio) switches
 * the challenge to MATH, whose question IS rendered — that is the only solvable path through this
 * page, and it is a real user-facing path, not a harness bypass.
 *
 * Returns the OTP the server issued, or null when cms.auth.otp.dev-auto-populate is off (in which
 * case no test can know the code and the caller must skip rather than weaken anything).
 */
async function solveCaptchaAndRequestOtp(page: any, mobile: string): Promise<string | null> {
  await page.locator('#mobile-input').fill(mobile);

  await page.locator('button[aria-label="Listen to CAPTCHA audio"]').click();
  const mathQuestion = page.locator('.captcha-math');
  await expect(mathQuestion).toBeVisible({ timeout: 15000 });

  const text = (await mathQuestion.innerText()).trim();
  const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(text);
  expect(match, `the MATH CAPTCHA did not render a solvable question: "${text}"`).not.toBeNull();
  const [, a, op, b] = match!;
  const answer = op.toLowerCase() === 'plus' ? Number(a) + Number(b) : Number(a) - Number(b);

  await page.locator('input.captcha-input').fill(String(answer));
  await page.locator('.consent-section input[type="checkbox"]').check();

  const otpResponse = page.waitForResponse(
    (r: any) => r.url().includes('/citizen/auth/send-otp') && r.request().method() === 'POST',
    { timeout: 20000 }
  );
  await page.locator('button.send-otp-btn').click();
  const sent = await otpResponse;

  expect(
    sent.status(),
    `send-otp was refused (${sent.status()}): ${await sent.text()}`
  ).toBe(200);
  const body = await sent.json();
  return body.devOtp || null;
}

async function typeOtp(page: any, otp: string) {
  const boxes = page.locator('.otp-inputs input.otp-box');
  await expect(boxes.first()).toBeVisible({ timeout: 10000 });
  for (let i = 0; i < 6; i++) {
    await boxes.nth(i).fill('');
    await boxes.nth(i).fill(otp[i]);
  }
}

test.describe('Feedback questionnaire — citizen (UST109 / FR-G-038)', () => {

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 1 — mobile + correct OTP authenticates, then a list of EXCLUSIVELY CLOSED complaints shows
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('mobile + correct OTP authenticates and lists exclusively closed complaints', async ({ page, request }) => {
    const mobile = '9876543210';

    // Two complaints on the same mobile: one closed (must appear) and one still open (must not).
    const closed = await createTestComplaint(request, {
      subject: 'UST109-1 closed', complainantPhone: mobile,
    });
    await advanceToStatus(request, closed.complaintNumber, 'closed');
    const open = await createTestComplaint(request, {
      subject: 'UST109-1 still open', complainantPhone: mobile,
    });
    await advanceToStatus(request, open.complaintNumber, 'in_progress');

    await page.goto('/public/login?returnUrl=%2Fpublic%2Ffeedback');
    await page.waitForLoadState('networkidle');

    const otp = await solveCaptchaAndRequestOtp(page, mobile);
    test.skip(
      otp === null,
      'the backend did not return devOtp (cms.auth.otp.dev-auto-populate is off), so no test can ' +
        'know the correct OTP — this is NOT a pass'
    );

    await typeOtp(page, otp!);
    await page.locator('button.verify-btn').click();

    // Authentication really happened: the guarded route rendered rather than bouncing to login.
    await expect(page).toHaveURL(/\/public\/feedback/, { timeout: 20000 });
    await page.waitForLoadState('networkidle');

    // UST109 scenario 1: "the system should authenticate and display a list of closed complaints."
    // Asserted as a selectable entry bearing the closed complaint's own number, so it cannot be
    // satisfied by some unrelated list on the page.
    const closedEntry = page.getByText(closed.complaintNumber, { exact: false });
    await expect(
      closedEntry.first(),
      'after OTP authentication the feedback page must list the citizen\'s closed complaints for ' +
        'selection; the shipped page renders only a free-text reference input'
    ).toBeVisible({ timeout: 15000 });

    // ...and EXCLUSIVELY closed ones.
    await expect(
      page.getByText(open.complaintNumber, { exact: false }),
      'a complaint that is still in progress must not be offered for feedback'
    ).toHaveCount(0);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 2 — mobile + INCORRECT OTP does not authenticate and shows an error
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('mobile + incorrect OTP is refused and an error is displayed', async ({ page }) => {
    // A mobile of its own, so a cooloff raised here cannot bleed into the other tests.
    const mobile = '9812345678';

    await page.goto('/public/login?returnUrl=%2Fpublic%2Ffeedback');
    await page.waitForLoadState('networkidle');

    const otp = await solveCaptchaAndRequestOtp(page, mobile);
    test.skip(otp === null, 'send-otp did not succeed, so the wrong-OTP path cannot be reached');

    // A deliberately wrong code. dev-auto-populate pre-fills the right one, so it is overwritten
    // digit by digit — and derived from the real OTP so it can never collide with it by accident.
    const wrong = otp!
      .split('')
      .map((d) => String((Number(d) + 3) % 10))
      .join('');
    expect(wrong).not.toBe(otp);
    await typeOtp(page, wrong);

    await page.locator('button.verify-btn').click();

    // `.field-error` is the OTP step's own message. The page may ALSO raise the `.error-banner`
    // cooloff notice at the same time (a wrong OTP counts towards lockout), so the two are asserted
    // separately rather than through one selector that matches both and trips strict mode.
    const otpError = page.locator('p.field-error');
    await expect(otpError, 'an incorrect OTP must produce a visible error').toBeVisible({ timeout: 15000 });
    await expect(otpError).toContainText(/incorrect|invalid/i);

    // Not authenticated: still on the login page, and the guarded route stays guarded.
    await expect(page).toHaveURL(/\/public\/login/);
    await page.goto('/public/feedback');
    await expect(
      page,
      'a failed OTP must leave no session behind, so /public/feedback must still redirect to login'
    ).toHaveURL(/\/public\/login/, { timeout: 15000 });
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 3 — open / in-progress complaints are excluded from the feedback list
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('only closed complaints are offered; active ones are excluded from the list', async ({ page, request }) => {
    const mobile = '9876543210';

    const closed = await createTestComplaint(request, {
      subject: 'UST109-3 closed', complainantPhone: mobile,
    });
    await advanceToStatus(request, closed.complaintNumber, 'closed');
    const inProgress = await createTestComplaint(request, {
      subject: 'UST109-3 in progress', complainantPhone: mobile,
    });
    await advanceToStatus(request, inProgress.complaintNumber, 'in_progress');
    const untouched = await createTestComplaint(request, {
      subject: 'UST109-3 open', complainantPhone: mobile,
    });

    await openFeedbackForm(page, mobile);

    // The list must exist before "excluded from the list" can mean anything: an assertion that only
    // checked for the ABSENCE of the open complaints would pass on a page with no list at all.
    await expect(
      page.getByText(closed.complaintNumber, { exact: false }).first(),
      'the feedback page must present the citizen\'s closed complaints to choose from'
    ).toBeVisible({ timeout: 15000 });

    for (const excluded of [inProgress.complaintNumber, untouched.complaintNumber]) {
      await expect(
        page.getByText(excluded, { exact: false }),
        `${excluded} is not closed, so it must not appear in the feedback list`
      ).toHaveCount(0);
    }
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 4 — clicking an eligible closed complaint displays the questionnaire
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('clicking a closed complaint opens the questionnaire with every defined question', async ({ page, request }) => {
    const mobile = '9876543210';
    const closed = await createTestComplaint(request, {
      subject: 'UST109-4 select', complainantPhone: mobile,
    });
    await advanceToStatus(request, closed.complaintNumber, 'closed');

    await openFeedbackForm(page, mobile);

    const entry = page.getByText(closed.complaintNumber, { exact: false }).first();
    await expect(
      entry,
      'the closed complaint must be selectable before the questionnaire can be opened by clicking it'
    ).toBeVisible({ timeout: 15000 });
    await entry.click();

    // The questionnaire, with the reference already bound to the complaint that was clicked.
    await expect(form.ref(page)).toHaveValue(closed.complaintNumber);
    for (const label of ['overall-rating-label', 'ease-rating-label', 'redress-rating-label']) {
      await expect(page.locator(`#${label}`)).toBeVisible();
    }
    await expect(form.source(page)).toBeVisible();
    await expect(page.getByRole('radio', { name: 'Very Aware', exact: true })).toBeVisible();
    await expect(form.feedbackText(page)).toBeVisible();
    await expect(form.suggestions(page)).toBeVisible();
  });

  /**
   * Companion to case 4. Case 4 fails at the SELECTION step, which would leave it unknown whether the
   * questionnaire itself is complete. This reaches the same form by direct navigation and checks every
   * defined question is rendered, so the report can separate "no list" from "no questionnaire".
   */
  test('the questionnaire renders all defined questions when reached directly', async ({ page }) => {
    await openFeedbackForm(page);

    await expect(page.locator('#overall-rating-label')).toContainText(/overall/i);
    await expect(page.locator('#ease-rating-label')).toContainText(/eas/i);
    await expect(page.locator('#redress-rating-label')).toContainText(/redress|time/i);
    await expect(form.source(page)).toBeVisible();
    for (const opt of ['Very Aware', 'Somewhat Aware', 'Not Aware (first time user)']) {
      await expect(page.getByRole('radio', { name: opt, exact: true })).toBeVisible();
    }
    await expect(form.feedbackText(page)).toBeVisible();
    await expect(form.suggestions(page)).toBeVisible();

    // Every mandatory question carries the required marker, so a citizen can tell which are optional.
    const requiredMarkers = await page.locator('.required').count();
    expect(requiredMarkers, 'the five mandatory questions plus the reference must be marked required')
      .toBeGreaterThanOrEqual(6);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 5 — mandatory only (optional Q4/Q6 blank) is accepted, saved, and locked against editing
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('mandatory-only submission is accepted, linked to the complaint, and blocks further editing', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-5 mandatory only' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await answerMandatoryOnly(page, complaint.complaintNumber, {
      overall: 5, ease: 1, redress: 3, source: 'News/Media',
    });

    // Optional Q4 / Q6 deliberately left blank.
    await expect(form.feedbackText(page)).toHaveValue('');
    await expect(form.suggestions(page)).toHaveValue('');

    await form.submit(page).click();
    await expect(form.success(page)).toBeVisible({ timeout: 20000 });

    // Saved AGAINST THE COMPLAINT RECORD, read back from the server rather than trusted from the UI.
    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status()).toBe(200);
    const saved = (await stored.json()).data;
    expect(saved.complaintNumber).toBe(complaint.complaintNumber);
    expect(saved.overallRating).toBe(5);
    expect(saved.easeOfFiling).toBe(1);
    expect(saved.grievanceRedressTime).toBe(3);
    expect(saved.sourceOfInformation).toBe('News/Media');
    expect(saved.cmsPortalAwareness).toBe('Very Aware');
    expect(saved.feedbackText ?? '').toBe('');
    expect(saved.suggestions ?? '').toBe('');

    // "blocks further editing" — a second submission for the same complaint must be refused, and the
    // stored answers must be exactly what was saved the first time.
    const again = await request.post(`${API_BASE}/api/v1/feedback`, {
      data: {
        complaintNumber: complaint.complaintNumber,
        overallRating: 1, easeOfFiling: 1, grievanceRedressTime: 1,
        sourceOfInformation: 'Social Media', cmsPortalAwareness: 'Not Aware (first time user)',
      },
      headers: { 'Content-Type': 'application/json' },
    });
    expect(again.status(), 'submitted feedback must not be re-writable').toBe(409);

    const after = await (await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`)).json();
    expect(after.data.overallRating).toBe(5);
    expect(after.data.sourceOfInformation).toBe('News/Media');
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 6 — mandatory AND optional answered: the complete dataset is saved
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('complete submission saves every mandatory and optional answer against the complaint', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-6 complete' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    const freeText = 'The officer called me twice and the refund reached my account in 9 days.';
    const suggestion = 'Please add an SMS update at each stage.';

    await openFeedbackForm(page);
    await answerMandatoryOnly(page, complaint.complaintNumber, {
      overall: 4, ease: 5, redress: 2, source: 'RBI Website',
    });
    await form.timeliness(page).nth(2).click();      // 3
    await form.communication(page).nth(3).click();   // 4
    await form.satisfaction(page).nth(4).click();    // 5
    await form.feedbackText(page).fill(freeText);
    await form.suggestions(page).fill(suggestion);

    await form.submit(page).click();
    await expect(form.success(page)).toBeVisible({ timeout: 20000 });

    const saved = (await (await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`)).json()).data;
    expect(saved.overallRating).toBe(4);
    expect(saved.easeOfFiling).toBe(5);
    expect(saved.grievanceRedressTime).toBe(2);
    expect(saved.timelinessRating).toBe(3);
    expect(saved.communicationRating).toBe(4);
    expect(saved.satisfactionRating).toBe(5);
    expect(saved.sourceOfInformation).toBe('RBI Website');
    expect(saved.cmsPortalAwareness).toBe('Very Aware');
    expect(saved.feedbackText).toBe(freeText);
    expect(saved.suggestions).toBe(suggestion);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 7 — missing mandatory answers are blocked AND the unanswered questions are highlighted
  //
  // The existing spec already walks the error MESSAGES in turn. This asserts the other half of the
  // case: that the unanswered question itself is marked, which is what "validation highlights the
  // unanswered mandatory questions" means and is what a screen reader needs.
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('missing mandatory answers are blocked and the unanswered questions are highlighted', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-7 highlight' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await form.ref(page).fill(complaint.complaintNumber);
    await form.overall(page).nth(3).click();
    // easeOfFiling deliberately left unanswered.
    await form.redress(page).nth(3).click();
    await form.source(page).selectOption('RBI Website');
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    // Blocked: no request is made at all.
    let posted = false;
    page.on('request', (r) => {
      if (r.url().includes('/api/v1/feedback') && r.method() === 'POST') posted = true;
    });
    await form.submit(page).click();
    await expect(form.error(page)).toBeVisible({ timeout: 10000 });
    expect(posted, 'an incomplete form must not be submitted to the server').toBe(false);

    // Nothing was stored.
    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status()).toBe(404);

    // Highlighted: the unanswered mandatory question is marked as invalid, not merely described in a
    // single summary line at the bottom of a long form.
    const easeGroup = page.locator('.rating-group:has(#ease-rating-label)');
    await expect(
      easeGroup.locator('[aria-invalid="true"], .invalid, .field-error, .has-error').first(),
      'the unanswered mandatory question must itself be highlighted / marked invalid'
    ).toBeVisible({ timeout: 5000 });
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 8 — Q5 "Others": exactly 500 alphanumeric characters is accepted (boundary)
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('Others text of exactly 500 alphanumeric characters is accepted with no validation error', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-8 boundary 500' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await form.ref(page).fill(complaint.complaintNumber);
    await form.overall(page).nth(3).click();
    await form.ease(page).nth(2).click();
    await form.redress(page).nth(3).click();
    await form.source(page).selectOption('Others');

    const other = form.sourceOther(page);
    await expect(other, 'choosing Others must reveal the specify-your-source field').toBeVisible();
    await other.fill(ALNUM_500);
    expect(ALNUM_500.length).toBe(500);
    await expect(other).toHaveValue(ALNUM_500);

    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    // No real-time validation error at the boundary.
    await expect(form.error(page)).toHaveCount(0);

    await form.submit(page).click();
    await expect(form.success(page)).toBeVisible({ timeout: 20000 });

    const saved = (await (await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`)).json()).data;
    expect(saved.sourceOfInformation).toBe('Others');
    expect(
      saved.sourceOtherText,
      '500 characters must be stored whole — a silent truncation would lose the citizen\'s answer'
    ).toBe(ALNUM_500);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 9 — Q5 "Others" over 500 chars OR containing special characters is refused with the
  //          message UST109 scenario 5 specifies
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('Others text over 500 characters is refused with the limit-and-special-characters message', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-9 over limit' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await form.ref(page).fill(complaint.complaintNumber);
    await form.overall(page).nth(3).click();
    await form.ease(page).nth(2).click();
    await form.redress(page).nth(3).click();
    await form.source(page).selectOption('Others');

    // maxlength caps typing at 500, so the over-length value is set past it — otherwise the case
    // could never be exercised at all and the absence of a check would go unnoticed.
    await form.sourceOther(page).evaluate((el: HTMLInputElement, text: string) => {
      el.removeAttribute('maxlength');
      el.value = text;
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }, ALNUM_510);
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    await form.submit(page).click();

    await expect(form.error(page)).toBeVisible({ timeout: 15000 });
    await expect(form.error(page)).toContainText(LIMIT_AND_SPECIALS_MESSAGE);

    // Refused, therefore nothing stored.
    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status(), 'a refused submission must not have been saved').toBe(404);
  });

  test('Others text containing special characters is refused with the same message', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-9 specials' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await form.ref(page).fill(complaint.complaintNumber);
    await form.overall(page).nth(3).click();
    await form.ease(page).nth(2).click();
    await form.redress(page).nth(3).click();
    await form.source(page).selectOption('Others');
    await form.sourceOther(page).fill(SPECIALS_SHORT);
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    await form.submit(page).click();

    await expect(form.error(page)).toBeVisible({ timeout: 15000 });
    await expect(form.error(page)).toContainText(LIMIT_AND_SPECIALS_MESSAGE);

    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status(), 'a refused submission must not have been saved').toBe(404);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 10 — Q4 "Any other feedback" over 500 chars OR with special characters: same message
  //
  // The existing spec asserts only that the error mentions "500 characters". UST109 scenario 4 names
  // the whole string, including the special-character rule, so it is asserted in full here.
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('additional feedback over 500 characters is refused with the limit-and-special-characters message', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-10 over limit' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await answerMandatoryOnly(page, complaint.complaintNumber, { source: 'RBI Website' });

    await form.feedbackText(page).evaluate((el: HTMLTextAreaElement, text: string) => {
      el.removeAttribute('maxlength');
      el.value = text;
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }, ALNUM_510);

    await form.submit(page).click();

    await expect(form.error(page)).toBeVisible({ timeout: 15000 });
    await expect(form.error(page)).toContainText(LIMIT_AND_SPECIALS_MESSAGE);

    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status()).toBe(404);
  });

  test('additional feedback containing special characters is refused with the same message', async ({ page, request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-10 specials' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await openFeedbackForm(page);
    await answerMandatoryOnly(page, complaint.complaintNumber, { source: 'RBI Website' });
    await form.feedbackText(page).fill(SPECIALS_SHORT);

    await form.submit(page).click();

    await expect(form.error(page)).toBeVisible({ timeout: 15000 });
    await expect(form.error(page)).toContainText(LIMIT_AND_SPECIALS_MESSAGE);

    const stored = await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`);
    expect(stored.status()).toBe(404);
  });

  // ══════════════════════════════════════════════════════════════════════════════════════════════
  // Case 11 — no duplicate or secondary feedback
  //
  // The existing spec proves the UI shows the 409 message. This proves the record is IMMUTABLE: that
  // no other HTTP verb on the same resource can quietly overwrite an answer a citizen already gave.
  // ══════════════════════════════════════════════════════════════════════════════════════════════
  test('a submitted feedback record cannot be overwritten or removed by any subsequent request', async ({ request }) => {
    const complaint = await createTestComplaint(request, { subject: 'UST109-11 immutable' });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    const original = {
      complaintNumber: complaint.complaintNumber,
      overallRating: 5,
      easeOfFiling: 4,
      grievanceRedressTime: 5,
      sourceOfInformation: 'RBI Website',
      cmsPortalAwareness: 'Very Aware',
      feedbackText: 'Resolved quickly and courteously.',
      suggestions: 'None.',
    };
    const created = await request.post(`${API_BASE}/api/v1/feedback`, {
      data: original, headers: { 'Content-Type': 'application/json' },
    });
    expect(created.status()).toBe(201);
    const id = (await created.json()).data.id;

    const tamper = {
      complaintNumber: complaint.complaintNumber,
      overallRating: 1,
      easeOfFiling: 1,
      grievanceRedressTime: 1,
      sourceOfInformation: 'Social Media',
      cmsPortalAwareness: 'Not Aware (first time user)',
      feedbackText: 'TAMPERED',
    };

    const attempts: Array<{ label: string; status: number }> = [];
    const post = await request.post(`${API_BASE}/api/v1/feedback`, {
      data: tamper, headers: { 'Content-Type': 'application/json' },
    });
    attempts.push({ label: 'POST (duplicate)', status: post.status() });
    const put = await request.put(`${API_BASE}/api/v1/feedback/${id}`, {
      data: tamper, headers: { 'Content-Type': 'application/json' },
    });
    attempts.push({ label: `PUT /${id}`, status: put.status() });
    const patch = await request.patch(`${API_BASE}/api/v1/feedback/${id}`, {
      data: tamper, headers: { 'Content-Type': 'application/json' },
    });
    attempts.push({ label: `PATCH /${id}`, status: patch.status() });
    const del = await request.delete(`${API_BASE}/api/v1/feedback/${id}`);
    attempts.push({ label: `DELETE /${id}`, status: del.status() });

    // ── FIRST: the substance of the case. No attempt changed a single answer. ──────────────────
    const after = (await (await request.get(`${API_BASE}/api/v1/feedback/${complaint.complaintNumber}`)).json()).data;
    expect(after.overallRating, 'overallRating was overwritten').toBe(5);
    expect(after.easeOfFiling, 'easeOfFiling was overwritten').toBe(4);
    expect(after.grievanceRedressTime).toBe(5);
    expect(after.sourceOfInformation).toBe('RBI Website');
    expect(after.feedbackText).toBe(original.feedbackText);
    expect(after.suggestions).toBe(original.suggestions);

    // ── THEN: the refusal must be a deliberate one. ────────────────────────────────────────────
    // Every attempt IS blocked, so the record is genuinely immutable — but PUT/PATCH/DELETE answer
    // 500, not 405. They are blocked only incidentally, because no handler is mapped, and the
    // unmapped request then falls into the generic error handler. That reports an internal fault for
    // what is really a well-defined client mistake, and it is indistinguishable from the endpoint
    // having crashed mid-write. Verified to be a GLOBAL mapping behaviour, not specific to feedback:
    // PUT /api/v1/feedback/<any-path> and DELETE /api/v1/complaints/{n} answer 500 too, so the fix
    // belongs in the global handler, not in FeedbackController. Reported as one defect, not three.
    for (const attempt of attempts) {
      expect(
        attempt.status,
        `${attempt.label} must be refused, got ${attempt.status} (all: ${JSON.stringify(attempts)})`
      ).toBeGreaterThanOrEqual(400);
      expect(
        attempt.status,
        `${attempt.label} answered ${attempt.status}; a write that is not allowed must be refused ` +
          'with 405/404, not reported as an internal server error'
      ).toBeLessThan(500);
    }
  });
});
