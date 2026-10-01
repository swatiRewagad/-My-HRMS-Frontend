import { test, expect } from '../fixtures';
import type { Page, APIRequestContext } from '@playwright/test';
import {
  createTestComplaint,
  advanceToStatus,
  loginCitizen,
  seedCitizenSession,
  cleanupComplaint,
  readAppealsForComplaint,
} from '../utils/test-data';

/**
 * UST111 — "File Appeal", launched FROM THE COMPLAINT TRACKING INTERFACE.
 *
 * ── WHAT THIS FILE OWNS, AND WHAT IT DOES NOT ───────────────────────────────────────────────────────
 * The entry point is `public/track` (components/complaint-tracker), the screen
 * e2e/public/tracking-table.spec.ts identifies as the tracking interface. That spec owns the table
 * itself — paging, server-side sorting, the status filter, the empty state — and none of that is
 * re-tested here.
 *
 * e2e/aa/file-appeal.spec.ts owns the `/public/appeal` screen in isolation: the eligibility tiers, the
 * clause gates, the 30/60/61-day window boundaries, the appellant-block field messages, the read-only
 * speaking order, and persistence plus officer notification for a successful filing. Also not
 * re-tested here.
 *
 * What was left uncovered, and is what these four cases are, is the JOURNEY between the two screens
 * and its effect back on the tracking view:
 *
 *   1. whether the tracker OFFERS the appeal at all, per status;
 *   2. that the grounds field is genuinely mandatory — proved by the APPEALS table, not by a message;
 *   3. that the tracking view REFLECTS the appeal afterwards;
 *   4. that a SECOND appeal on the same complaint is refused and no second row is written.
 *
 * ── ASSERTIONS GO TO THE DATABASE, NOT TO THE TOAST ─────────────────────────────────────────────────
 * Cases 2 and 4 are both negatives: "the appeal was not created". No API a citizen can call proves
 * that — GET /api/v1/appeals/* is Appellate Authority state behind @AaRoleGuard — and this codebase has
 * a documented class of features that report success while persisting nothing, so the inverse (a
 * refusal reported while the row IS written) has to be excluded explicitly. Hence
 * readAppealsForComplaint.
 *
 * ── THE QA SHEET'S QUOTED REFUSAL STRING DOES NOT EXIST ─────────────────────────────────────────────
 * The manual sheet quotes `Appeal already filed for this complaint.` for case 4. That string is
 * nowhere in the product (repo-wide search: zero hits). The three real wordings are:
 *
 *   AppealEligibilityService:114  "An active appeal already exists for this complaint."   ← the
 *                                 citizen-facing one, delivered as HTTP 400 through the eligibility
 *                                 gate in AppealController
 *   AppealController:145          "An active appeal already exists for complaint: <n>"    ← the 409
 *                                 duplicate branch, UNREACHABLE for an active appeal because the
 *                                 eligibility gate above it fires first
 *   AaAppealRegisterService:182   "An appeal is already open against complaint <n>"       ← staff path
 *
 * These tests assert the BEHAVIOUR the sheet is really about (refused, nothing duplicated) and the
 * wording the product actually ships. Substituting the sheet's string would have meant writing a test
 * that fails for a reason no citizen experiences. The divergence is raised as an open question.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/**
 * The refusal a citizen actually reads when they try to appeal twice. See the header note: this is NOT
 * the string the QA sheet quotes, and it is deliberately not softened to match it.
 */
const MSG_DUPLICATE = 'An active appeal already exists for this complaint.';

/** A mobile no other fixture owns, so a citizen's list holds only what this file seeded. */
function freshMobile(): string {
  return '9' + String(Date.now()).slice(-8) + String(Math.floor(Math.random() * 10));
}

interface Fixture {
  mobile: string;
  token: string;
  complaintNumber: string;
}

/**
 * One citizen, one complaint, advanced to the given status.
 *
 * The status is a parameter because case 1 needs BOTH a status that permits an appeal and one that does
 * not, and the difference between them is the entire assertion.
 */
async function seedCitizenComplaint(
  request: APIRequestContext,
  subject: string,
  targetStatus: 'closed' | 'in_progress'
): Promise<Fixture> {
  const mobile = freshMobile();
  const created = await createTestComplaint(request, {
    subject,
    complainantName: 'Appeal Journey Citizen',
    complainantPhone: mobile,
  });
  // advanceToStatus supplies DEFAULT_CLOSURE_CLAUSE = 15(1)(a) on CLOSE_COMPLAINT. Without a clause the
  // closure is not appealable at all and POST /appeals/file answers 503, which would make every case
  // below fail for a setup reason rather than for the behaviour under test.
  await advanceToStatus(request, created.complaintNumber, targetStatus);

  const token = await loginCitizen(request, mobile);
  if (!token) {
    throw new Error(
      'Could not obtain a citizen session. Requires cms.auth.otp.dev-auto-populate=true (dev-local).'
    );
  }
  return { mobile, token, complaintNumber: created.complaintNumber };
}

/**
 * Opens one complaint's detail card on the tracking screen, by reference number.
 *
 * Track-by-id is used rather than clicking a table row because the subject here is the detail card and
 * its appeal affordance; which row opens which complaint is tracking-table.spec.ts's assertion. The
 * citizen session is still seeded, because that is the state a real citizen filing an appeal is in.
 */
async function openComplaintCard(page: Page, f: Fixture): Promise<void> {
  await page.goto(`${APP_BASE}/public/track`);
  await seedCitizenSession(page, f.mobile, f.token);
  await page.goto(`${APP_BASE}/public/track/${f.complaintNumber}`);
  await page.waitForLoadState('networkidle');
  await expect(page.locator('.status-card')).toBeVisible({ timeout: 15000 });
}

const appealButton = (page: Page) =>
  page.locator('.appeal-section button:has-text("File Appeal")');

/**
 * Fills everything submitAppeal() requires EXCEPT the ground and the details.
 *
 * The declaration is ticked here, not only in the happy-path test. submitAppeal() validates in order —
 * delay reason, appellant block, ground, details, declaration — so leaving the box unticked would let
 * case 2 pass on ordering alone, and it would still pass if the grounds check were deleted outright
 * (the declaration refusal would surface instead and the message assertion would be the only thing
 * standing). With the declaration accepted, the ground is genuinely the only thing missing.
 */
async function fillEverythingBarGrounds(page: Page): Promise<void> {
  await page.locator('#appellantName').fill('Ramesh Kumar');
  await page.locator('#appellantMobile').fill('9876543210');
  // The visible date input is readonly by design; the value can only arrive through the hidden picker.
  await page.locator('.date-hidden-picker').evaluate((el: HTMLInputElement) => {
    el.value = new Date().toISOString().split('T')[0];
    el.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await page.locator('.declaration-box input[type="checkbox"]').check();
}

/** Walks from the tracker's File Appeal button to the filled appeal FORM phase. */
async function walkToAppealForm(page: Page, f: Fixture): Promise<void> {
  await openComplaintCard(page, f);
  await appealButton(page).click();

  // The tracker hands the complaint number over as ?complaint=, and file-appeal's ngOnInit
  // auto-triggers the eligibility check from it — so the citizen must never have to retype it.
  await expect(page).toHaveURL(new RegExp(`/public/appeal\\?complaint=${f.complaintNumber}`));
  await expect(page.locator('.eligibility-result.eligible')).toBeVisible({ timeout: 15000 });

  await page.locator('.btn-primary:has-text("Proceed to File Appeal")').click();
  await expect(page.locator('#appellantName')).toBeVisible({ timeout: 10000 });
  await fillEverythingBarGrounds(page);
}

test.describe('UST111 - citizen files an appeal from the tracking interface', () => {
  const created: string[] = [];

  test.afterAll(async ({ request }) => {
    for (const n of created) {
      await cleanupComplaint(request, n);
    }
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  // CASE 1 — the offer is made only where the product's own rule permits it.
  //
  // The rule asserted is the TRACKER's rule, read from the code rather than guessed:
  // ComplaintTrackerComponent.isAppealEligibleStatus() admits CLOSED / RESOLVED / REJECTED, and
  // canFileAppeal() additionally requires that no appeal already exists. `in_progress` is the negative
  // case because it is reachable through advanceToStatus in one step and is unambiguously non-terminal.
  //
  // NOTE, as an open question rather than an assertion: three layers disagree about which statuses
  // permit an appeal. The tracker says CLOSED/RESOLVED/REJECTED; AppealEligibilityService.
  // TERMINAL_STATUSES also admits `adjudicated` and `conciliated`; AaParentComplaintSearchService
  // accepts "closed OR workflow_stage = REOPENED". A complaint in `adjudicated` therefore gets no
  // button on the tracker even though the server would accept the filing. Which list is correct is a
  // policy question, so current behaviour is what is asserted.
  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  test('the appeal is offered for a closed complaint and withheld for one still in progress', async ({ page, request }) => {
    const closed = await seedCitizenComplaint(request, 'UST111 offer on closed', 'closed');
    created.push(closed.complaintNumber);

    await openComplaintCard(page, closed);
    await expect(appealButton(page)).toHaveCount(1);
    await expect(appealButton(page)).toBeVisible();

    const active = await seedCitizenComplaint(request, 'UST111 offer withheld in progress', 'in_progress');
    created.push(active.complaintNumber);

    await openComplaintCard(page, active);
    // The card itself must be up before the absence means anything — otherwise this assertion would
    // also pass on a page that failed to render at all.
    await expect(page.locator('.status-card')).toBeVisible();
    await expect(appealButton(page)).toHaveCount(0);
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  // CASE 2 — grounds are mandatory, and the refusal is real.
  //
  // Proved on BOTH sides: the browser must state the problem in words a citizen can act on, and the
  // APPEALS table must be untouched. Either half alone would pass against a broken product — a message
  // with a row written behind it, or a silent no-op with no message.
  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  test('submitting with no grounds is refused with an actionable message and creates no appeal', async ({ page, request }) => {
    const f = await seedCitizenComplaint(request, 'UST111 grounds mandatory', 'closed');
    created.push(f.complaintNumber);

    await walkToAppealForm(page, f);

    // Everything EXCEPT the ground, so the refusal can only be about the ground.
    await page.locator('textarea[name="appealDetails"]').fill(
      'The closure did not address the unauthorised debit I reported.'
    );
    await expect(page.locator('input[name="appealGround"]:checked')).toHaveCount(0);

    await page.locator('.btn-primary:has-text("Submit")').click();

    const message = page.locator('.error-msg');
    await expect(message).toBeVisible({ timeout: 10000 });
    // Actionable = it names the missing thing and what to do about it, not "validation failed".
    await expect(message).toContainText(/ground/i);
    await expect(message).toContainText(/select/i);

    // Still on the form: a refused submission must not advance the citizen to the success screen.
    await expect(page.locator('.success-card')).toHaveCount(0);
    await expect(page.locator('#appellantName')).toBeVisible();

    // The load-bearing half. Nothing was written.
    expect(readAppealsForComplaint(f.complaintNumber)).toEqual([]);
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  // CASE 3 — after a successful filing the tracking view reflects it.
  //
  // The QA sheet words this as "the status shows Appeal Filed". The complaint's `status` column stays
  // `closed` on purpose — see AppealWorkflowService: overwriting it would drop the complaint out of
  // every closure filter and SLA query, and out of AppealEligibilityService.TERMINAL_STATUSES, which
  // would close the appeal route behind the citizen who had just used it. The appeal is therefore
  // recorded as an APPEAL_FILED event on the complaint's own timeline and surfaced as `appealFiled` on
  // the tracking payload. What is asserted is that the tracking view STATES the appeal, which is the
  // requirement behind the wording; the wording itself is raised as an open question.
  //
  // Before the fix this test failed on every one of the four assertions below: the filing wrote only to
  // APPEAL_TIMELINE (keyed on the appeal number, readable only behind @AaRoleGuard), the complaint
  // payload carried no appeal field at all, and the tracking view was byte-for-byte unchanged — badge
  // still "Closed", no mention of an appeal, and the File Appeal button still offered.
  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  test('the tracking view states the appeal once it has been filed', async ({ page, request }) => {
    const f = await seedCitizenComplaint(request, 'UST111 tracker reflects appeal', 'closed');
    created.push(f.complaintNumber);

    await walkToAppealForm(page, f);
    await page.locator('.radio-label:has-text("Dissatisfied with the resolution/award")').click();
    await page.locator('textarea[name="appealDetails"]').fill(
      'The award does not cover the disputed amount and I am seeking a review.'
    );
    await page.locator('.btn-primary:has-text("Submit")').click();

    await expect(page.locator('.success-card')).toBeVisible({ timeout: 20000 });
    const appealRef = (await page.locator('.ref-number').innerText()).trim();
    expect(appealRef).toMatch(/^APL-/);

    // The appeal is real, not just announced.
    const rows = readAppealsForComplaint(f.complaintNumber);
    expect(rows.map(r => r.appealNumber)).toEqual([appealRef]);

    // The API the tracker reads must now carry the appeal, or the UI has nothing to render from.
    const detail = await request.get(`${API_BASE}/api/v1/complaints/${f.complaintNumber}`);
    expect(detail.status()).toBe(200);
    const data = (await detail.json()).data;
    expect(data.appealFiled).toBe(true);
    expect(data.appealNumber).toBe(appealRef);
    expect(data.appealFiledAt).toBeTruthy();

    // And the citizen's own screen says so.
    await openComplaintCard(page, f);
    await expect(page.locator('.appeal-filed-section')).toBeVisible();
    await expect(page.locator('.appeal-filed-label')).toHaveText('Appeal Filed');
    await expect(page.locator('.appeal-filed-section')).toContainText(appealRef);

    // The timeline event is named in citizen language, not as the raw APPEAL_FILED token.
    await expect(page.locator('.timeline-action').first()).toHaveText('Appeal Filed');
  });

  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  // CASE 4 — a second appeal on the same complaint is refused.
  //
  // Two layers, because they fail independently. The TRACKER must stop offering the appeal (before the
  // fix it offered it forever, since the status stays terminal, and walked the citizen to /public/appeal
  // only to refuse them there). The SERVER must refuse the filing regardless of what any UI does, since
  // /api/v1/appeals/file is reachable without the form — and must write no second row.
  // ═════════════════════════════════════════════════════════════════════════════════════════════════
  test('a second appeal on the same complaint is refused and nothing is duplicated', async ({ page, request }) => {
    const f = await seedCitizenComplaint(request, 'UST111 duplicate appeal refused', 'closed');
    created.push(f.complaintNumber);

    // First appeal, through the API — the browser path is case 3's subject, not this one's.
    const first = await request.post(`${API_BASE}/api/v1/appeals/file`, {
      multipart: {
        complaintNumber: f.complaintNumber,
        ground: 'Dissatisfied with the resolution/award',
        details: 'First appeal against this closure.',
        classification: 'APPEAL',
        appellantName: 'Ramesh Kumar',
        appellantPhone: '9876543210',
      },
    });
    expect(first.status()).toBe(201);
    const firstNumber = (await first.json()).data.appealNumber;

    // The offer is withdrawn on the tracking screen, so the citizen is never sent to a dead end.
    await openComplaintCard(page, f);
    await expect(page.locator('.appeal-filed-section')).toBeVisible();
    await expect(appealButton(page)).toHaveCount(0);

    // And the server refuses a direct second filing.
    const second = await request.post(`${API_BASE}/api/v1/appeals/file`, {
      multipart: {
        complaintNumber: f.complaintNumber,
        ground: 'Dissatisfied with the resolution/award',
        details: 'Second appeal against the same closure.',
        classification: 'APPEAL',
        appellantName: 'Ramesh Kumar',
        appellantPhone: '9876543210',
      },
    });
    // 400, not 200-with-success:false: a refusal delivered as 200 is indistinguishable from an
    // acceptance to any HTTP client. Note this is NOT the 409 AppealController's own duplicate branch
    // would return — the eligibility gate above it answers first, making that branch unreachable.
    expect(second.status()).toBe(400);
    const body = await second.json();
    expect(body.success).toBe(false);
    expect(String(body.message)).toContain(MSG_DUPLICATE);

    // Exactly one appeal exists, and it is the first one.
    const rows = readAppealsForComplaint(f.complaintNumber);
    expect(rows).toHaveLength(1);
    expect(rows[0].appealNumber).toBe(firstNumber);

    // The eligibility endpoint the appeal screen itself calls agrees, so re-entering /public/appeal by
    // hand cannot get past the verdict either.
    const elig = await request.get(
      `${API_BASE}/api/v1/appeals/check-eligibility?complaintNumber=${f.complaintNumber}`
    );
    expect(elig.status()).toBe(200);
    const verdict = (await elig.json()).data;
    expect(verdict.eligible).toBe(false);
    expect(String(verdict.reason)).toContain(MSG_DUPLICATE);
  });
});
