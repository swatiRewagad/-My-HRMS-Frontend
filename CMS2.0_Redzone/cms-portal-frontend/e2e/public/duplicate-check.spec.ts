import { test, expect } from '../fixtures';
import { advanceToStatus, seedCitizenSession } from '../utils/test-data';

/**
 * UST87 / FR-G-020: duplicate pre-check before a public complaint is submitted (D4).
 *
 * A duplicate is the FULL combination — (mobile OR email) + complainant name + entity name +
 * date of disputed transaction + category — against a complaint that is still live. Any one
 * parameter differing makes the complaint unique (Scenario 3).
 *
 * Two regressions are pinned here. The endpoint used not to exist, so every call errored and the
 * client auto-submitted anyway. Then it existed but resolved entityName/category to BANKS and
 * CATEGORIES ids that public filing never populates; a null id meant "ignore this field", so the
 * check collapsed to "has this phone filed anything in 90 days" and flagged every repeat filer.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

const ENTITY = 'Test Bank Ltd';
const CATEGORY = 'REMITTANCES';
const DISPUTE_DATE = '2026-01-15';

function uniqueContact() {
  const n = Math.floor(Math.random() * 9000) + 1000;
  return {
    phone: `91111${String(Date.now()).slice(-5)}`.slice(0, 10),
    email: `dupcheck_${Date.now().toString(36)}_${n}@example.com`,
    name: `Dup Check ${Date.now().toString(36)}_${n}`,
  };
}

async function checkDuplicate(request: any, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/complaints/check-duplicate`, {
    data: payload,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Seeds through the public filing endpoint, not the CEPC one: only this path writes entityName,
 * categoryName and the disputed-transaction date the way a citizen's complaint carries them, and
 * those are the three columns the check now compares.
 */
async function fileComplaint(request: any, c: { phone: string; email: string; name: string }) {
  const res = await request.post(`${API_BASE}/api/v1/complaints`, {
    data: {
      filingType: 'ONLINE',
      complainantName: c.name,
      complainantEmail: c.email,
      complainantPhone: c.phone,
      complainantAddress: '1 Test Street, Mumbai',
      entityName: ENTITY,
      entityType: 'BANK',
      category: CATEGORY,
      subject: 'Duplicate check seed',
      description: 'Seeded for the FR-G-020 duplicate pre-check regression tests.',
      transactionDate: DISPUTE_DATE,
    },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) throw new Error(`Seed filing failed: ${res.status()} - ${await res.text()}`);
  return (await res.json()).data;
}

/** The full FR-G-020 combination, with individual parameters overridable per test. */
function fullCombination(c: { phone: string; email: string; name: string }, overrides: Record<string, unknown> = {}) {
  return {
    phone: c.phone,
    email: c.email,
    complainantName: c.name,
    entityName: ENTITY,
    category: CATEGORY,
    disputeDate: DISPUTE_DATE,
    ...overrides,
  };
}

test.describe('UST87 — Duplicate pre-check endpoint (D4)', () => {

  test('the endpoint exists and reports no duplicate for an unknown complainant', async ({ request }) => {
    const c = uniqueContact();

    const res = await checkDuplicate(request, fullCombination(c));

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.success).toBe(true);
    expect(body.duplicate).toBe(false);
  });

  test('a request without a phone or an email is rejected', async ({ request }) => {
    const res = await checkDuplicate(request, { entityName: ENTITY });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.success).toBe(false);
    expect(body.error).toBe('MISSING_IDENTIFIER');
  });

  test('Scenario 1 — the full combination on the same mobile is a duplicate', async ({ request }) => {
    const c = uniqueContact();
    const seeded = await fileComplaint(request, c);

    const res = await checkDuplicate(request, fullCombination(c, { email: undefined }));

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn).toBe('phone');
    expect(body.complaintNumber).toBe(seeded.complaintId);
  });

  test('Scenario 2 — an email-only match is reported as matchedOn=email', async ({ request }) => {
    const c = uniqueContact();
    await fileComplaint(request, c);

    const res = await checkDuplicate(request, fullCombination(c, { phone: undefined }));

    const body = await res.json();
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn).toBe('email');
  });

  /**
   * Scenario 3, one parameter at a time. This is the regression that mattered: each of these
   * used to come back duplicate:true because the differing parameter was never compared, so a
   * citizen's second unrelated complaint was always blocked.
   */
  test.describe('Scenario 3 — one differing parameter makes the complaint unique', () => {
    const variations: Array<[string, Record<string, unknown>]> = [
      ['a different entity', { entityName: 'Some Other Bank Ltd' }],
      ['a different category', { category: 'CREDIT_CARD' }],
      ['a different dispute date', { disputeDate: '2026-02-20' }],
      ['a different complainant name', { complainantName: 'Someone Else Entirely' }],
    ];

    for (const [label, override] of variations) {
      test(label, async ({ request }) => {
        const c = uniqueContact();
        await fileComplaint(request, c);

        // Same contact, so the candidate query still returns the seeded complaint — only the
        // narrowing comparison can rule it out.
        expect((await (await checkDuplicate(request, fullCombination(c))).json()).duplicate).toBe(true);

        const res = await checkDuplicate(request, fullCombination(c, override));
        expect((await res.json()).duplicate).toBe(false);
      });
    }
  });

  test('an incomplete combination reports no duplicate rather than matching on contact alone', async ({ request }) => {
    const c = uniqueContact();
    await fileComplaint(request, c);

    // Omitting a narrowing parameter must not be read as "ignore this field" — that is exactly
    // what degraded the check into "anyone who filed recently".
    for (const missing of ['complainantName', 'entityName', 'category', 'disputeDate']) {
      const res = await checkDuplicate(request, fullCombination(c, { [missing]: undefined }));
      expect((await res.json()).duplicate, `omitting ${missing} must not report a duplicate`).toBe(false);
    }
  });

  test('a non-ISO dispute date is rejected rather than silently ignored', async ({ request }) => {
    const c = uniqueContact();

    const res = await checkDuplicate(request, fullCombination(c, { disputeDate: '15/01/2026' }));

    expect(res.status()).toBe(400);
    expect((await res.json()).error).toBe('INVALID_DISPUTE_DATE');
  });

  test('a closed complaint no longer blocks a fresh filing', async ({ request }) => {
    const c = uniqueContact();
    const seeded = await fileComplaint(request, c);

    // While live it is a duplicate.
    expect((await (await checkDuplicate(request, fullCombination(c))).json()).duplicate).toBe(true);

    await advanceToStatus(request, seeded.complaintId, 'closed');

    // Once closed the citizen must be free to raise the matter again. The close path writes
    // uppercase CLOSED while the configured terminal statuses are lowercase, so this also pins
    // the case-insensitive status comparison.
    const after = await checkDuplicate(request, fullCombination(c));
    expect((await after.json()).duplicate).toBe(false);
  });
});

/**
 * The duplicate check only runs from the review step's Submit button, and reaching step 6 by
 * clicking through the wizard means filling six screens of master-data-driven fields. Drafts are
 * server-side, so these tests POST one to /api/v1/complaints/drafts and open it with `?draftId=`,
 * which hydrates the real component at the review step and exercises the real button.
 *
 * The draft shape must match ComplaintFacadeService.buildDraftPayload()/hydrateFromDraft(): the
 * reactive form is patched from `formData`, so the two declaration checkboxes have to be in there
 * rather than as top-level flags, or Submit stays disabled.
 */
async function seedDraft(request: any, phone: string, email: string): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/complaints/drafts`, {
    data: {
      phone,
      phase: 'form',
      currentStep: 6,
      highestStepReached: 6,
      eligibilityAnswers: { filedWithRE: 'yes', receivedReply: 'yes' },
      // filedWithRE: 'yes' maps to priorReComplaint, which the server requires a date and
      // reference for — without them registration 400s before the duplicate check is reached.
      eligibilityFormData: {
        filedWithRE: 'yes', receivedReply: 'yes',
        bankComplaintDate: '2026-01-15', bankComplaintRef: 'RE-DUP-REF-001',
      },
      // Mirrors complaintStepperForm.getRawValue() — hydrateFromDraft patches the nested reactive
      // form from this, so a flat object would leave every control (and Submit) untouched.
      formData: {
        complainantDetails: {
          complaintCategory: 'individual',
          firstName: 'Duplicate', middleName: '', lastName: 'Check',
          age: '', gender: '', email, phone,
          pincode: '400001', state: 'Maharashtra', city: 'Mumbai',
          addressDetails: '1 Test Street, Mumbai',
        },
        regulatedEntity: {
          isCreditCardComplaint: 'no', entityState: 'Maharashtra',
          entityDistrict: 'Mumbai', entityBranch: 'Fort',
        },
        complaintDetails: {
          complaintCategory: CATEGORY, complaintText: 'Duplicate pre-check regression test.',
          hasAccountWithRE: 'no', accountTypeSelection: '',
          savingsAccountNumber: '', loanAccountNumber: '', atmDebitCardNumber: '', creditCardNumber: '',
          isWalletComplaint: 'no', walletName: '', transactionRefNumber: '',
          isBusinessCorrespondent: 'no',
          // The check scores on the disputed-transaction date, so the draft must carry one —
          // without it the request is incomplete and could never report a duplicate.
          disputeDate: DISPUTE_DATE,
          disputeAmount: '', compensationSought: '', reliefSought: '',
          fileUpload: [{ fileId: 'seed-1', fileName: 'seed.pdf', fileSize: 1024, viewUrl: '/seed.pdf' }],
        },
        repAuthorization: { hasAuthRep: 'no' },
        declaration: { declaration1: true, declaration2: true },
      },
    },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!res.ok()) throw new Error(`Draft seed failed: ${res.status()} - ${await res.text()}`);
  const body = await res.json();
  return (body.data || body).draftId;
}

async function openReviewStep(page: any, request: any, phone: string, email: string) {
  const draftId = await seedDraft(request, phone, email);

  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, phone, 'e2e-seeded-token.sig');

  await page.goto(`${APP_BASE}/public/file-complaint?draftId=${draftId}`);

  // Not networkidle: these tests stub /complaints with page.route(), and a fulfilled route leaves
  // the dev server's HMR socket open, so the network never goes idle and the wait burns the whole
  // test timeout. Waiting on the button itself is the condition that actually matters.
  const submit = page.locator('button.btn-submit-complaint');
  await expect(submit).toBeAttached({ timeout: 30000 });
  // The review step is long, so Submit sits well below the fold and a click would otherwise wait
  // for stability that never comes.
  await submit.scrollIntoViewIfNeeded();
  await expect(submit).toBeVisible();
  await expect(submit).toBeEnabled();
  return submit;
}

test.describe('UST87/UST88 — Duplicate popup and fail-closed behaviour in the UI (D4)', () => {

  test('the wizard sends every FR-G-020 parameter', async ({ page, request }) => {
    // The server cannot score a combination it is not given. The name in particular was never
    // sent, so the check could only ever compare four of the five parameters.
    let sent: any = null;
    await page.route('**/api/v1/complaints/check-duplicate', route => {
      sent = route.request().postDataJSON();
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true,"duplicate":false}' });
    });
    await page.route('**/api/v1/complaints', route =>
      route.fulfill({ status: 201, contentType: 'application/json', body: '{"success":true,"data":{"complaintId":"N-TEST-0001"}}' }));

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, request, phone, email);
    await submit.click();

    await expect.poll(() => sent, { timeout: 15000 }).not.toBeNull();
    expect(sent.phone).toBe(phone);
    expect(sent.email).toBe(email);
    expect(sent.complainantName).toBe('Duplicate Check');
    expect(sent.category).toBe(CATEGORY);
    expect(sent.disputeDate).toBe(DISPUTE_DATE);
    // entityName comes from master data the draft cannot preselect, so only assert it is carried.
    expect(sent).toHaveProperty('entityName');
  });

  test('a failing duplicate check blocks submission instead of filing anyway', async ({ page, request }) => {
    // Regression: the error handler used to set duplicateCheckDone = true and call submit(),
    // so any outage silently disabled the control and filed the complaint regardless.
    await page.route('**/api/v1/complaints/check-duplicate', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' }));

    let registerCalled = false;
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/api\/v1\/complaints$/.test(req.url())) registerCalled = true;
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, request, phone, email);
    await submit.click();

    await expect(page.locator('.error-msg')).toContainText(
      'We could not verify whether this complaint has already been filed', { timeout: 15000 });

    // The complaint must NOT have been registered as a side effect of the failed check.
    expect(registerCalled).toBe(false);
    // Nor may a duplicate warning be fabricated from a failure.
    await expect(page.locator('.duplicate-dialog')).toHaveCount(0);
    // The button must return to a usable state so the citizen can retry.
    await expect(submit).toBeEnabled();
  });

  test('retrying after the outage clears succeeds', async ({ page, request }) => {
    let attempt = 0;
    await page.route('**/api/v1/complaints/check-duplicate', route => {
      attempt++;
      return attempt === 1
        ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"success":false}' })
        : route.fulfill({ status: 200, contentType: 'application/json', body: '{"success":true,"duplicate":false}' });
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, request, phone, email);

    await submit.click();
    await expect(page.locator('.error-msg')).toBeVisible({ timeout: 15000 });

    // Clicking Submit again re-runs the check — no separate retry affordance is needed.
    await submit.click();
    await expect.poll(() => attempt, { timeout: 15000 }).toBe(2);
    await expect(page.locator('.error-msg')).toHaveCount(0);
  });

  test('a detected duplicate warns the citizen and does not file until they confirm', async ({ page, request }) => {
    await page.route('**/api/v1/complaints/check-duplicate', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, duplicate: true, matchedOn: 'phone', complaintNumber: 'CMP-20260101-000001' }),
      }));

    let registerCalled = false;
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/api\/v1\/complaints$/.test(req.url())) registerCalled = true;
    });

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, request, phone, email);
    await submit.click();

    const popup = page.locator('.duplicate-dialog');
    await expect(popup).toBeVisible({ timeout: 15000 });
    await expect(popup).toContainText('Duplicate complaint detected based on mobile number');
    expect(registerCalled).toBe(false);

    // UST88 Scenario 3 — Cancel leaves the citizen on the review step with nothing filed.
    await popup.getByRole('button', { name: /cancel/i }).click();
    await expect(popup).toBeHidden();
    expect(registerCalled).toBe(false);
    await expect(submit).toBeEnabled();
  });

  test('UST88 Scenario 4 — Proceed Anyway files the complaint and returns a number', async ({ page, request }) => {
    await page.route('**/api/v1/complaints/check-duplicate', route =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ success: true, duplicate: true, matchedOn: 'email', complaintNumber: 'N-EXISTING-0001' }),
      }));
    await page.route('**/api/v1/complaints', route =>
      route.fulfill({ status: 201, contentType: 'application/json', body: '{"success":true,"data":{"complaintId":"N-NEW-0002","status":"REGISTERED"}}' }));

    const { phone, email } = uniqueContact();
    const submit = await openReviewStep(page, request, phone, email);
    await submit.click();

    const popup = page.locator('.duplicate-dialog');
    await expect(popup).toBeVisible({ timeout: 15000 });
    await expect(popup).toContainText('Duplicate complaint detected based on email');

    await popup.getByRole('button', { name: /proceed/i }).click();

    await expect(page.locator('.success-page')).toBeVisible({ timeout: 15000 });
    await expect(page.locator('.success-page')).toContainText('N-NEW-0002');
  });
});
