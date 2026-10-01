import { test, expect } from '../fixtures';
import { isKeycloakAvailable, logout } from '../utils/auth';
import {
  createTestComplaint,
  advanceToStatus,
  loginCitizen,
  seedCitizenSession,
  backdateComplaintClosure,
  readAppealNotifications,
  readSystemConfig,
  setSystemConfig,
} from '../utils/test-data';

/**
 * These eight tests shared ONE cause, and it was not the appeal screen.
 *
 * 1. THE URL DID NOT EXIST. Every test navigated to `/appeal`. The real route is `/public/appeal` —
 *    it is a CHILD of the `public` path. A non-existent Angular route falls through to the public home
 *    page, so `.form-input` was legitimately absent and the failure looked like a broken form. The
 *    rendered page in the failure context was the home page, which is what gave it away.
 *
 * 2. IT NEEDS A CITIZEN SESSION, NOT A STAFF LOGIN. `/public/appeal` sits behind publicAuthGuard,
 *    which reads a citizen OTP session from sessionStorage. A Keycloak staff token does not satisfy it,
 *    so loginAsAaRole would not have helped either. seedCitizenSession must run on a page already
 *    served from the app origin, hence the bare goto before it.
 */
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * The exact strings the acceptance criteria name for the appellant block. Held here so a reworded
 * message fails the test rather than quietly passing a paraphrase.
 */
const MSG_REQUIRED = 'This field is required.';
const MSG_LENGTH = 'Input must be within allowed character length.';
const MSG_FILE = 'Invalid file type or size, please upload a valid document.';
const MSG_DELAY_REQUIRED = 'Reason for delay is required.';
const MSG_DELAY_LENGTH = 'Reason must be within 500 characters.';

/** The refusal a citizen is shown once the filing window has closed. Fixed wording. */
const MSG_WINDOW_CLOSED = 'Not eligible for filing appeal.';

test.describe('AA - File Appeal', () => {
  let keycloakUp: boolean;
  const CITIZEN_MOBILE = '9876500011';

  /**
   * Creates a complaint, closes it under a named clause, and optionally ages the closure.
   *
   * `closureClause` rides on advanceToStatus's `extras`, which is merged into the FINAL step's params —
   * CLOSE_COMPLAINT — so a non-appealable clause such as 16(2)(a) can be recorded without restating
   * the five-step transition path.
   */
  async function closedComplaint(
    request: import('@playwright/test').APIRequestContext,
    opts: { subject: string; clause?: string; ageDays?: number } = { subject: 'E2E Appeal' }
  ): Promise<string> {
    const complaint = await createTestComplaint(request, { subject: opts.subject });
    const extras: Record<string, string> = {};
    if (opts.clause) extras['closureClause'] = opts.clause;
    await advanceToStatus(request, complaint.complaintNumber, 'closed', undefined, extras);
    if (opts.ageDays != null) {
      backdateComplaintClosure(complaint.complaintNumber, opts.ageDays);
    }
    return complaint.complaintNumber;
  }

  /** Walks the search phase and returns once an eligibility verdict is on screen. */
  async function checkEligibility(page: import('@playwright/test').Page, complaintNumber: string) {
    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaintNumber);
    await page.locator('.btn-primary:has-text("Check Eligibility")').click();
    await expect(page.locator('.eligibility-result')).toBeVisible({ timeout: 10000 });
  }

  /** Fills the appellant identity block. The date goes through the picker, never the text input. */
  async function fillAppellant(
    page: import('@playwright/test').Page,
    over: { name?: string; mobile?: string; email?: string; comments?: string; skipDate?: boolean } = {}
  ) {
    await page.locator('#appellantName').fill(over.name ?? 'Ramesh Kumar');
    await page.locator('#appellantMobile').fill(over.mobile ?? '9876543210');
    if (over.email !== undefined) await page.locator('#appellantEmail').fill(over.email);
    if (over.comments !== undefined) await page.locator('#appellantComments').fill(over.comments);
    if (!over.skipDate) {
      // The visible input is readonly, so the value can only arrive through the hidden native picker.
      // `fill` on a readonly input throws, which is what makes the readonly guard testable below.
      await page.locator('.date-hidden-picker').evaluate((el: HTMLInputElement) => {
        el.value = new Date().toISOString().split('T')[0];
        el.dispatchEvent(new Event('change', { bubbles: true }));
      });
    }
  }

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page, request }) => {
    const token = await loginCitizen(request, CITIZEN_MOBILE);
    test.skip(!token, 'Citizen OTP login unavailable — cannot reach a publicAuthGuard route');
    await page.goto('/', { waitUntil: 'domcontentloaded' });
    await seedCitizenSession(page, CITIZEN_MOBILE, token!);
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: <30 days — eligible
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint <30 days shows eligible', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Eligible <30d',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const text = await eligibilityResult.textContent();
    expect(text?.toLowerCase()).toContain('eligible');
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: 31-60 days — delayed eligible, reason required
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint 31-60 days shows delayed eligible with reason required', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Delayed 31-60d',
    });
    // Advance to closed, THEN backdate the closure to 45 days ago.
    //
    // This used to read `advanceToStatus(..., 'closed', { backdateDays: 45 })`. The 4th positional
    // parameter of advanceToStatus is `token`, so that object was passed as a bearer token, ignored,
    // and the complaint stayed 0 days old — this test was asserting the 31-60 day behaviour against a
    // same-day closure and failing for that reason alone.
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    backdateComplaintClosure(complaint.complaintNumber, 45);

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Should show eligible (delayed filing is still eligible)
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    // Proceed to form
    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await expect(proceedBtn).toBeVisible();
    await proceedBtn.click();

    // The delay notice should appear
    const delayNotice = page.locator('.delay-notice');
    await expect(delayNotice).toBeVisible({ timeout: 5000 });

    // Reason for delay field should be visible
    const reasonField = page.locator('textarea[name="reasonForDelay"]');
    await expect(reasonField).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: >60 days — not eligible
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint >60 days shows not eligible', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Ineligible >60d',
    });
    // Same mis-positioned-argument bug as above: the backdate never happened, so this test was
    // asserting ">60 days is refused" against a complaint closed today.
    await advanceToStatus(request, complaint.complaintNumber, 'closed');
    backdateComplaintClosure(complaint.complaintNumber, 90);

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const ineligible = page.locator('.eligibility-result.ineligible');
    await expect(ineligible).toBeVisible({ timeout: 10000 });

    const text = await ineligible.textContent();
    expect(text?.toLowerCase()).toMatch(/not eligible|exceeded|ineligible/);
  });

  // ═══════════════════════════════════════════════════════════
  // Validation: Submit without required fields
  // ═══════════════════════════════════════════════════════════
  test('Submit appeal without required fields shows validation errors', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Validation Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search phase
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Eligibility phase
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // Form phase — try to submit without filling anything
    const submitBtn = page.locator('.btn-primary:has-text("Submit Appeal")');
    await expect(submitBtn).toBeVisible({ timeout: 5000 });
    await submitBtn.click();

    // Should show validation error
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });

    const errorText = await errorMsg.textContent();
    expect(errorText?.toLowerCase()).toMatch(/required|select|provide|accept/);
  });

  // ═══════════════════════════════════════════════════════════
  // File upload: oversize file shows error (not silently dropped)
  // ═══════════════════════════════════════════════════════════
  test('File over the configured size limit shows an error instead of being silently dropped', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal File Size Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Navigate to form phase
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // ── The 2 MB vs 5 MB collision, NARROWED rather than loosened ─────────────────────────────────
    //
    // This assertion was `expect(errorText).toContain('exceeds 2MB limit')`, i.e. it asserted a
    // HARDCODED number. The component no longer holds one: the limit is read from
    // /api/v1/config/upload-limits, which is backed by SYSTEM_CONFIG key
    // cms.attachments.max_file_size_bytes. Re-hardcoding 2 in the test would pin the suite to today's
    // configured value and go red the moment an administrator changes the row — which is the exact
    // failure mode the server-driven limit was introduced to remove.
    //
    // So the assertion is narrowed BY TYPE, not weakened: it still demands a size refusal naming a
    // limit in MB, and it demands the limit named is the CONFIGURED one, read from the same endpoint
    // the browser read. It does not accept a generic error, and it does not accept silence.
    //
    // The oversize file is derived from that configured limit (limit + 1 MB) for the same reason — a
    // fixed 3 MB buffer is under the limit if the row is ever raised to 5 MB, and the test would then
    // pass by accepting a file it meant to see refused.
    const limitsRes = await request.get(`${API_BASE}/api/v1/config/upload-limits`);
    expect(limitsRes.ok()).toBeTruthy();
    const limits = await limitsRes.json();
    const maxMb: number = limits.maxFileSizeMb;
    expect(maxMb).toBeGreaterThan(0);

    const fileInput = page.locator('input#appealFiles');
    await expect(fileInput).toBeAttached({ timeout: 5000 });

    const buffer = Buffer.alloc((maxMb + 1) * 1024 * 1024, 'x');
    await fileInput.setInputFiles({
      name: 'large-file.pdf',
      mimeType: 'application/pdf',
      buffer,
    });

    // Error message should appear about file size
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });

    const errorText = await errorMsg.textContent();
    expect(errorText).toContain(`exceeds the ${maxMb}MB limit`);
    // The exact wording the acceptance criteria specify must survive alongside the number.
    expect(errorText).toContain('Invalid file type or size, please upload a valid document.');
  });

  // ═══════════════════════════════════════════════════════════
  // Successful submission: appeal number displayed
  // ═══════════════════════════════════════════════════════════
  test('Successful submission displays appeal number', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E File Appeal Success',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Wait for eligible
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    // Proceed
    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // Fill form. The appellant identity block is new and its fields are required: this appeal used to
    // be persisted with appellantName hardcoded to the literal "Citizen" and no contact details.
    await fillAppellant(page, { email: 'ramesh@example.com' });

    const groundRadio = page.locator('.radio-label').first();
    await expect(groundRadio).toBeVisible({ timeout: 5000 });
    await groundRadio.click();

    const detailsField = page.locator('textarea[name="appealDetails"]');
    await expect(detailsField).toBeVisible();
    await detailsField.fill('The complaint was not resolved satisfactorily. The RE failed to provide adequate compensation.');

    // Relief sought. Targeted by name, not by `.form-textarea` index: the appellant block added a
    // Comments textarea ahead of this one, so nth(1) now selects a different field.
    await page.locator('textarea[name="reliefSought"]')
      .fill('Full compensation of Rs. 50000 and corrective action against the RE.');

    // Declaration checkbox
    const declaration = page.locator('.declaration-box input[type="checkbox"]');
    await declaration.check();

    // Submit
    const submitBtn = page.locator('.btn-primary:has-text("Submit Appeal")');
    await submitBtn.click();

    // Success phase — appeal reference number should be displayed
    const successCard = page.locator('.success-card');
    await expect(successCard).toBeVisible({ timeout: 15000 });

    const refNumber = page.locator('.ref-number');
    await expect(refNumber).toBeVisible({ timeout: 5000 });

    const numText = await refNumber.textContent();
    expect(numText?.trim()).toBeTruthy();
    expect(numText?.trim()).toMatch(/^APL-/);
  });

  // ═══════════════════════════════════════════════════════════
  // Legacy tests (backward compat)
  // ═══════════════════════════════════════════════════════════
  test('Citizen can search for closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Search Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });
  });

  test('Filing when ineligible (active complaint) shows error message', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint that is NOT closed (still in progress)
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Ineligible Appeal Test',
    });

    await page.goto('/public/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Should show ineligible result or error
    const ineligible = page.locator('.eligibility-result.ineligible, .error-msg');
    await expect(ineligible).toBeVisible({ timeout: 10000 });

    const text = await ineligible.textContent();
    expect(text?.toLowerCase()).toMatch(/ineligible|not eligible|not closed|error|active/);
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════
  // A — CLAUSE GATE. Only closures under 15(1)(a) or 15(1)(b) may be appealed by a complainant.
  //
  // This requirement did NOT exist in the product. AppealEligibilityService gated on TERMINAL_STATUSES
  // and nothing else, so a complaint closed under 16(2)(a) — "complaint is non-maintainable" — was
  // offered for appeal and POST /appeals/file accepted it.
  //
  // `appealable` is a SEPARATE result flag from `eligible` on purpose, and these tests assert that
  // separation: the appeal route must close while `eligible` stays true, because the filing endpoint
  // gates the REPRESENTATION route on eligible==true and a non-appealable closure would otherwise have
  // no escalation path at all.
  //
  // No appealable-complaint LIST screen was built. Which complaints a citizen may browse, and whether
  // they may see one belonging to somebody else, is a question rather than an inference — so only the
  // refusal path is implemented and tested.
  // ═════════════════════════════════════════════════════════════════════════════════════════════
  test('A: closure under a non-appealable clause is refused on the appeal screen', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // 16(2)(a) exists in CLOSURE_CLAUSE_MASTER with appealable_by_complainant = 0.
    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Clause 16(2)(a)',
      clause: '16(2)(a)',
    });

    await checkEligibility(page, complaintNumber);

    // The verdict must READ as a refusal, not merely carry a flag.
    await expect(page.locator('.eligibility-result.ineligible')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.clause-refusal')).toContainText('16(2)(a)');
    await expect(page.locator('.clause-refusal')).toContainText('not appealable');

    // And the route must actually be closed, not just labelled.
    await expect(page.locator('.btn-primary:has-text("Proceed to File Appeal")')).toHaveCount(0);
  });

  test('A: an appealable clause 15(1)(b) is offered for appeal', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The positive half of the gate. Without this, a gate that refused EVERYTHING would pass the test
    // above.
    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Clause 15(1)(b)',
      clause: '15(1)(b)',
    });

    await checkEligibility(page, complaintNumber);

    await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.btn-primary:has-text("Proceed to File Appeal")')).toBeVisible();
  });

  test('A: the API refuses a non-appealable clause even when the browser check is skipped', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // The eligibility read and the filing call are two independent requests. A caller can skip the
    // first, so the gate has to exist server-side too — a browser-only refusal is a UI courtesy, not a
    // control, and this record is statutory.
    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Clause API Gate',
      clause: '16(2)(b)',
    });

    const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
      multipart: {
        complaintNumber,
        ground: 'Dissatisfied with the resolution/award',
        details: 'Attempting to appeal a non-maintainable closure directly through the API.',
        classification: 'APPEAL',
        appellantName: 'Ramesh Kumar',
        appellantPhone: '9876543210',
      },
    });

    // 400, not 200-with-success:false: WorkflowController maps IllegalArgumentException onto HTTP 200
    // and a refusal that arrives as 200 is indistinguishable from an acceptance to any HTTP client.
    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(String(body.message)).toContain('not appealable');
  });

  test('A: eligibility stays true for a non-appealable clause so the representation route survives', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Clause Axis Separation',
      clause: '16(3)',
    });

    const res = await request.get(
      `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${complaintNumber}`
    );
    expect(res.ok()).toBeTruthy();
    const data = (await res.json()).data;

    // The two axes must not be collapsed. Setting eligible=false to express "not appealable" would also
    // block REPRESENTATION, which the filing endpoint gates on eligible==true.
    expect(data.eligible).toBe(true);
    expect(data.appealable).toBe(false);
    expect(String(data.appealableReason)).toContain('16(3)');
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════
  // B — FILING WINDOW BOUNDARIES
  //
  // The window is `DAYS.between(closureDate, today)` against SYSTEM_CONFIG
  // timeline.appeal.filing_window_days (30) and timeline.appeal.extended_window_days (60).
  //
  // Measured behaviour of the implemented boundaries, which the brief asked to be verified rather than
  // assumed: 30 days is eligible WITHOUT a delay reason (the comparison is `> filingWindow`), 60 days
  // is still eligible WITH one (`> extendedWindow` is false at exactly 60), and 61 is refused. The
  // statutory windows are therefore INCLUSIVE on both boundaries.
  // ═════════════════════════════════════════════════════════════════════════════════════════════
  test('B: day 30 is eligible and does NOT demand a reason for delay', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Boundary Day 30',
      clause: '15(1)(a)',
      ageDays: 30,
    });

    await checkEligibility(page, complaintNumber);
    await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 10000 });
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    // The boundary is INCLUSIVE of the standard window: day 30 must not be treated as a delayed filing.
    await expect(page.locator('.delay-notice')).toHaveCount(0);
    await expect(page.locator('textarea[name="reasonForDelay"]')).toHaveCount(0);
  });

  test('B: day 60 is still eligible and DOES demand a reason for delay', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Boundary Day 60',
      clause: '15(1)(a)',
      ageDays: 60,
    });

    await checkEligibility(page, complaintNumber);
    await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 10000 });
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    await expect(page.locator('.delay-notice')).toBeVisible({ timeout: 5000 });
    await expect(page.locator('textarea[name="reasonForDelay"]')).toBeVisible();
  });

  test('B: day 61 is refused with the exact message "Not eligible for filing appeal."', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Boundary Day 61',
      clause: '15(1)(a)',
      ageDays: 61,
    });

    await checkEligibility(page, complaintNumber);

    const ineligible = page.locator('.eligibility-result.ineligible');
    await expect(ineligible).toBeVisible({ timeout: 10000 });

    // EXACT wording, asserted on the reason line rather than the whole card. The card also contains the
    // heading, so a substring match against it would pass on the heading alone.
    await expect(ineligible.locator('.eligibility-info p')).toHaveText(MSG_WINDOW_CLOSED);

    // The refusal must not leak the diagnostic arithmetic the operator sees on `reasonDetail`.
    expect(await ineligible.textContent()).not.toContain('Days elapsed');
  });

  test('B / FR-G-043: a 70-day configured window makes a 65-day-old closure appealable', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');
    // The whole point of the requirement is that the boundary MOVES, so the test changes the row and
    // then asserts the new boundary — a test that only read the default would pass against a hardcoded
    // 60 and prove nothing.
    test.setTimeout(180_000);

    const KEY = 'timeline.appeal.extended_window_days';
    const previous = readSystemConfig(KEY) || '60';

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal FR-G-043 Configurable Window',
      clause: '15(1)(a)',
      ageDays: 65,
    });

    // Refused at the default 60-day window first. Without this the test could pass against a product
    // that simply never refuses anything.
    let res = await request.get(
      `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${complaintNumber}`
    );
    let data = (await res.json()).data;
    expect(data.eligible).toBe(false);
    expect(data.reason).toBe(MSG_WINDOW_CLOSED);

    try {
      // cms_db is shared and persistent. setSystemConfig waits out SystemConfigService's 30s TTL; the
      // appeal window itself is read straight from SystemConfigRepository with no cache, but the wait
      // is kept because this helper is the one contract every other session relies on.
      await setSystemConfig(KEY, '70');

      res = await request.get(
        `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${complaintNumber}`
      );
      data = (await res.json()).data;
      expect(data.eligible).toBe(true);
      expect(data.delayedFiling).toBe(true);
      // The configured value must be what the API reports back, not a constant that happens to agree.
      expect(data.extendedWindowDays).toBe(70);

      // And the browser must agree with the API it just read from.
      await checkEligibility(page, complaintNumber);
      await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 10000 });
    } finally {
      // Restored unconditionally: a 70-day window left behind would silently change the verdict of
      // every other suite sharing this database.
      await setSystemConfig(KEY, previous);
    }
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════
  // C — ORIGINAL COMPLAINT AND SPEAKING ORDER, READ-ONLY
  // ═════════════════════════════════════════════════════════════════════════════════════════════
  test('C: the speaking order and complaint details are shown and are not editable', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Speaking Order',
      description: 'The bank debited an ATM withdrawal that never dispensed cash.',
    });
    // customClosureText is what AppealEligibilityService surfaces as `speakingOrder`.
    await advanceToStatus(request, complaint.complaintNumber, 'closed', undefined, {
      closureClause: '15(1)(a)',
      customClosureText: 'SPEAKING ORDER: the complaint is closed as the RE has refunded the amount.',
      closureCause: 'RESOLVED',
    });

    await checkEligibility(page, complaint.complaintNumber);

    const speakingOrder = page.locator('.speaking-order-section .readonly-box');
    await expect(speakingOrder).toBeVisible({ timeout: 10000 });
    await expect(speakingOrder).toContainText('SPEAKING ORDER');

    // Read-only means STRUCTURALLY read-only, not "styled to look calm". Asserted as the absence of any
    // editable control inside the box — a div showing the text cannot be typed into, whereas an input
    // or a contenteditable can.
    await expect(speakingOrder.locator('input, textarea, select, [contenteditable="true"]')).toHaveCount(0);

    const details = page.locator('.complaint-details-readonly .readonly-box');
    await expect(details).toBeVisible();
    await expect(details).toContainText('ATM withdrawal');
    await expect(details.locator('input, textarea, select, [contenteditable="true"]')).toHaveCount(0);
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════
  // D — FIELD VALIDATION, EXACT MESSAGES
  //
  // The banner (.error-msg) can only ever show ONE message, so a form with three empty required fields
  // reported one of them. These assert the PER-FIELD spans, keyed by data-field.
  // ═════════════════════════════════════════════════════════════════════════════════════════════
  test('D: every empty required appellant field reports "This field is required." at once', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Required Fields',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();

    // All three at once. One-at-a-time reporting is the defect being guarded against.
    await expect(page.locator('[data-field="appellantName"]')).toHaveText(MSG_REQUIRED);
    await expect(page.locator('[data-field="appellantMobile"]')).toHaveText(MSG_REQUIRED);
    await expect(page.locator('[data-field="dateOfReceipt"]')).toHaveText(MSG_REQUIRED);
  });

  test('D: an over-length name and a short mobile both report the length message', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Length Validation',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    // maxlength=100 stops the browser at the cap, so the over-length value has to be set on the control
    // directly — otherwise the test would silently assert against a truncated 100-character name and
    // the length rule would never be exercised.
    await page.locator('#appellantName').evaluate((el: HTMLInputElement) => {
      el.value = 'x'.repeat(101);
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.locator('#appellantMobile').fill('98765');

    await page.locator('.btn-primary:has-text("Submit Appeal")').click();

    await expect(page.locator('[data-field="appellantName"]')).toHaveText(MSG_LENGTH);
    // A 5-digit mobile is a length problem, which is what the criteria call it.
    await expect(page.locator('[data-field="appellantMobile"]')).toHaveText(MSG_LENGTH);
  });

  test('D: email is optional but a malformed one is rejected', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Email Validation',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    await fillAppellant(page, { email: 'not-an-email' });
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();
    await expect(page.locator('[data-field="appellantEmail"]')).toHaveText(MSG_LENGTH);

    // Optional: cleared, it must not complain at all.
    await page.locator('#appellantEmail').fill('');
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();
    await expect(page.locator('[data-field="appellantEmail"]')).toHaveCount(0);
  });

  test('D: Date of Receipt cannot be typed and only the picker sets it', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Date Picker Only',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    const visible = page.locator('#dateOfReceipt');
    await expect(visible).toBeVisible();
    // The criterion is "selectable via date-picker ONLY (not free text)". `readonly` is what enforces
    // that, and it is asserted on the attribute rather than by trying to type: a fill() that throws
    // would also throw for a hundred unrelated reasons.
    await expect(visible).toHaveAttribute('readonly', '');

    // The native picker must be genuinely non-interactive as a field in its own right, or the citizen
    // is looking at a second, typeable date input sitting next to the readonly one.
    //
    // NOT `toBeVisible()`: Playwright's visibility is "has a non-empty bounding box and is not
    // display:none", and this element deliberately keeps its box so it can host the native popup. It is
    // hidden by opacity and made untouchable by pointer-events, which is what the computed style below
    // asserts. Checking Playwright visibility would fail against a correct implementation.
    const hidden = page.locator('.date-hidden-picker');
    await expect(hidden).toHaveCount(1);
    const pickerStyle = await hidden.evaluate((el) => {
      const s = getComputedStyle(el);
      return { opacity: s.opacity, pointerEvents: s.pointerEvents };
    });
    expect(pickerStyle.opacity).toBe('0');
    expect(pickerStyle.pointerEvents).toBe('none');

    // And the picker's value must actually land, formatted, in the readonly display.
    await fillAppellant(page);
    const todayDisplay = await page.evaluate(() => {
      const d = new Date();
      const p = (n: number) => String(n).padStart(2, '0');
      return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
    });
    await expect(visible).toHaveValue(todayDisplay);
  });

  test('D: a disallowed file type is refused with the exact upload message', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal File Type',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    // .exe is not in the accepted set (JPG/JPEG/PDF/DOCX). The extension decides, not the MIME type:
    // a .docx from some browsers arrives with an empty type, so a MIME check would refuse valid files.
    await page.locator('input#appealFiles').setInputFiles({
      name: 'payload.exe',
      mimeType: 'application/octet-stream',
      buffer: Buffer.from('MZ'),
    });

    await expect(page.locator('.error-msg')).toContainText(MSG_FILE, { timeout: 5000 });
    await expect(page.locator('.file-chip')).toHaveCount(0);
  });

  test('D: a delayed filing demands its own two exact delay messages', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Delay Messages',
      clause: '15(1)(a)',
      ageDays: 45,
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();
    await expect(page.locator('textarea[name="reasonForDelay"]')).toBeVisible({ timeout: 5000 });

    // Blank. These two strings are separately specified and must NOT be folded into the generic
    // "This field is required." — which is why the delay checks run before the appellant block.
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();
    await expect(page.locator('.error-msg')).toContainText(MSG_DELAY_REQUIRED);

    // Over 500. maxlength caps typing, so the value is set directly.
    await page.locator('textarea[name="reasonForDelay"]').evaluate((el: HTMLTextAreaElement) => {
      el.value = 'y'.repeat(501);
      el.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();
    await expect(page.locator('.error-msg')).toContainText(MSG_DELAY_LENGTH);
  });

  test('D: the API rejects a bad appellant payload even though the form validated it', async ({ request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Server Validation',
      clause: '15(1)(a)',
    });

    // A client-side check is a convenience, not a control. These values land on a statutory record and
    // /api/v1/appeals/file is reachable without the form.
    const res = await request.post(`${API_BASE}/api/v1/appeals/file`, {
      multipart: {
        complaintNumber,
        ground: 'Dissatisfied with the resolution/award',
        details: 'Bypassing the browser validation entirely.',
        classification: 'APPEAL',
        appellantName: 'Ramesh Kumar',
        appellantPhone: '12345',
      },
    });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(String(body.message)).toContain('10 digits');
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════
  // E — THE APPEAL IS SAVED **AND** THE DESIGNATED OFFICER IS NOTIFIED
  //
  // The notification half did NOT work. Two independent bugs:
  //   1. AaWorkflowNotifierImpl.map mapped AaWorkflowEvent.FILED to null, so notifyOfficer returned
  //      before writing anything — proven by SQL: every AA_WORKFLOW row in this database was a
  //      hearing_* event and not one was appeal_filed.
  //   2. AppealWorkflowService only notified when the assignment engine produced an officer, and the
  //      engine has no eligible AA_DO, so every appeal in this database has assigned_officer NULL and
  //      the branch never ran at all.
  //
  // The assertion reads in_app_notifications directly. GET /api/v1/notifications is scoped to the
  // CALLER, and a citizen cannot read an officer's inbox — so no API visible to this test can prove
  // the notification exists, and the filing response returns 201 either way.
  // ═════════════════════════════════════════════════════════════════════════════════════════════
  test('E: filing an appeal persists it AND notifies the designated officer', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaintNumber = await closedComplaint(request, {
      subject: 'E2E Appeal Officer Notification',
      clause: '15(1)(a)',
    });

    await checkEligibility(page, complaintNumber);
    await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();

    await fillAppellant(page, {
      name: 'Sunita Devi',
      mobile: '9812345678',
      email: 'sunita@example.com',
      comments: 'Please consider the enclosed statement.',
    });
    await page.locator('.radio-label').first().click();
    await page.locator('textarea[name="appealDetails"]')
      .fill('The award does not cover the loss actually suffered.');
    await page.locator('.declaration-box input[type="checkbox"]').check();
    await page.locator('.btn-primary:has-text("Submit Appeal")').click();

    await expect(page.locator('.success-card')).toBeVisible({ timeout: 15000 });
    const appealNumber = (await page.locator('.ref-number').textContent())!.trim();
    expect(appealNumber).toMatch(/^APL-/);

    // SAVED — with the citizen's own identity, not the literal "Citizen" the controller used to
    // hardcode, and with contact details that were previously dropped entirely.
    const detail = await request.get(`${API_BASE}/api/v1/appeals/${appealNumber}`, {
      headers: { 'X-User-Id': 'aa_do_001', 'X-User-Name': 'aa_do_001', 'X-User-Roles': 'AA_DO' },
    });
    expect(detail.ok()).toBeTruthy();
    const appeal = (await detail.json()).data;
    expect(appeal.appellantName).toBe('Sunita Devi');
    expect(appeal.appellantPhone).toBe('9812345678');
    expect(appeal.appellantEmail).toBe('sunita@example.com');

    // NOTIFIED. Notifications are deferred to afterCommit, so the row appears just after the response.
    await expect
      .poll(() => readAppealNotifications(appealNumber).map((n) => n.title), { timeout: 15000 })
      .toContain('aa.notify.officer.appeal_filed');

    // Addressed to a real recipient: either the placed officer or, when the engine can place nobody,
    // the AA_DO role queue the appeal is actually sitting in. An empty or null target is the bug.
    const filed = readAppealNotifications(appealNumber)
      .find((n) => n.title === 'aa.notify.officer.appeal_filed')!;
    expect(filed.targetUserId).toBeTruthy();
    expect(filed.targetUserId).toMatch(/AA_DO|aa_/i);
  });
});
