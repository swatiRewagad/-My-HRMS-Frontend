import { test, expect } from '../fixtures';
import { installCorsShim } from './browser-api';
import {
  createTestComplaint,
  loginCitizen,
  seedCitizenSession,
} from '../utils/test-data';
import { sql } from '../aa/aa-shared-fixtures';

/**
 * Citizen COMPLAINT WITHDRAWAL — abandoning the form, and what the citizen can SEE afterwards.
 *
 * ── SCOPE: only the gaps the two existing withdrawal specs leave ────────────────────────────────
 *
 * e2e/public/withdrawal.spec.ts and e2e/public/withdrawal-attachments.spec.ts already cover the
 * form defaults, the happy path, the attachment formats, the audit trail, the three ineligible
 * statuses, the blank reason and the file-validation rejections. None of that is repeated here.
 *
 * What neither covers:
 *
 *   1. ABANDONING the form. Both specs only ever drive it to completion, so nothing asserts that a
 *      citizen who changes their mind has not already withdrawn their complaint. The product has no
 *      modal — withdraw-complaint.component.html:100-106 is a phase-switched inline form whose
 *      secondary button is `phase.set('search')` — so "cancel before confirmation" means leaving the
 *      confirm phase without submitting. A weaker test (the form is gone) would pass on a form that
 *      fired the POST on its way out, so this asserts the ABSENCE of the request, via an observer on
 *      the page, AND re-reads the status from the server afterwards.
 *
 *   2. The event being VISIBLE TO THE CITIZEN. withdrawal-attachments.spec.ts case 7 asserts the
 *      STAFF audit trail (/history). That read path is role-gated and no citizen ever sees it. The
 *      citizen's own view is the Detailed Timeline on the tracker
 *      (complaint-tracker.component.html:250-267), fed from the `timeline` array of
 *      GET /api/v1/complaints/{n} (ComplaintApiV1Controller.java:438-453). An audit row that exists
 *      but never surfaces there satisfies case 7 and still fails the citizen, so this asserts what
 *      is rendered on screen, with its timestamp.
 *
 * NOT COVERED HERE, DELIBERATELY: "withdrawal without a document succeeds" is exactly
 * withdrawal-attachments.spec.ts case 2 ('withdrawal with only the mandatory reason saves the reason
 * and sets WITHDRAWN'), which posts {reason, remarks} with no document and asserts 200 + status
 * 'withdrawn' + the persisted reason. Writing it again here would be a duplicate.
 *
 * ── ZERO SKIPS ──────────────────────────────────────────────────────────────────────────────────
 *
 * The sibling specs call test.skip(!token) when the citizen login cannot complete. A skip is not a
 * pass, so this file asserts the token instead: under the dev-local profile POST
 * /api/v1/citizen/auth/send-otp returns devOtp, so a null token means the environment is wrong and
 * the run must say so loudly rather than report green.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** Withdrawal is owner-only (ComplaintApiV1Controller.java:576-590), so every complaint is filed from this number. */
const CITIZEN_PHONE = '9876543210';

/** A reason from the component's own radio list (withdraw-complaint.component.ts:35-42). */
const VALID_REASON = 'Issue resolved by the Regulated Entity';

/**
 * The label the manual case requires the citizen's timeline to carry. Asserted on the rendered text,
 * because the stored action token is the lowercase internal value 'withdrawn'
 * (ComplaintApiV1Controller.java:632) and showing that to a complainant is not the same thing.
 */
const CITIZEN_TIMELINE_LABEL = 'Complaint Withdrawn';

/** COMPLAINTS columns for one complaint number — server-side truth, not the citizen projection. */
function complaintRow(complaintNumber: string, columns: string[]): Record<string, string> {
  const out = sql(
    `SELECT ${columns.map(c => `COALESCE(${c},'')`).join(', ')} FROM COMPLAINTS ` +
    `WHERE complaint_number = '${complaintNumber}'`
  );
  const values = out.split('\t');
  const row: Record<string, string> = {};
  columns.forEach((c, i) => { row[c] = values[i] ?? ''; });
  return row;
}

/** Opens the withdrawal form for a complaint and returns once the reason radios are on screen. */
async function openWithdrawalForm(
  page: import('@playwright/test').Page,
  complaintNumber: string,
  token: string
): Promise<boolean> {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, CITIZEN_PHONE, token);
  await page.goto(`${APP_BASE}/public/withdraw`);
  await page.waitForLoadState('networkidle');

  const input = page.locator('input[name="complaintId"], input.form-input');
  await expect(input).toBeVisible({ timeout: 15000 });
  await input.fill(complaintNumber);
  await page.locator('button:has-text("Find"), button:has-text("Search"), button.btn-primary')
    .first().click();

  return page.locator('.radio-group').first()
    .waitFor({ state: 'visible', timeout: 15000 })
    .then(() => true)
    .catch(() => false);
}

/**
 * Records every withdrawal POST the PAGE attempts, so "no request was submitted" is an assertion
 * about traffic rather than about the DOM. The listener is attached before the form is touched.
 */
function watchWithdrawalRequests(page: import('@playwright/test').Page): string[] {
  const attempts: string[] = [];
  page.on('request', (req) => {
    if (req.method() === 'POST' && /\/api\/v1\/complaints\/[^/]+\/withdraw/.test(req.url())) {
      attempts.push(req.url());
    }
  });
  return attempts;
}

async function citizenLogin(request: import('@playwright/test').APIRequestContext): Promise<string> {
  const token = await loginCitizen(request, CITIZEN_PHONE);
  expect(
    token,
    'the citizen login did not complete. Under dev-local POST /api/v1/citizen/auth/send-otp returns ' +
    'devOtp, so this means the backend is not on the dev-local profile — the environment is wrong, ' +
    'and the case must not be skipped over'
  ).toBeTruthy();
  return token!;
}

test.describe('Withdrawal — cancelling before confirmation, and the citizen-visible timeline', () => {

  test.beforeEach(async ({ page }) => {
    await installCorsShim(page);
  });

  // ══ Case A: cancel before confirmation ══════════════════════════════════════════════════════
  test('cancelling the withdrawal form submits nothing and leaves the status untouched',
    async ({ page, request }) => {
      const token = await citizenLogin(request);

      const { complaintNumber } = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — cancelled before confirmation',
        complainantName: 'Changed Mind Citizen',
        complainantPhone: CITIZEN_PHONE,
      });

      const statusBefore = complaintRow(complaintNumber, ['status'])['status'];
      expect(statusBefore, 'the fixture must start in a withdrawable status').not.toBe('withdrawn');

      const attempts = watchWithdrawalRequests(page);

      const reached = await openWithdrawalForm(page, complaintNumber, token);
      expect(reached, 'the withdrawal reason form must render for an eligible complaint').toBe(true);

      // Get as far as a real citizen would before having second thoughts: a reason chosen, and the
      // confirm button armed. Cancelling from a blank form would not exercise the interesting case.
      await page.locator(`.radio-group label:has-text("${VALID_REASON}")`).first().click();
      await expect(page.locator('.radio-group input[type="radio"]:checked')).toHaveCount(1);

      // CANCEL. The product has no modal: the confirm phase's secondary button returns to search
      // (withdraw-complaint.component.html:101).
      await page.locator('.card .form-actions button.btn-secondary').first().click();

      // 1. The form is gone and the citizen is back where they started.
      await expect(
        page.locator('.radio-group').first(),
        'cancelling must dismiss the withdrawal form'
      ).toBeHidden({ timeout: 10000 });
      await expect(
        page.locator('input[name="complaintId"], input.form-input').first(),
        'cancelling must return the citizen to the search step, not a dead end'
      ).toBeVisible();
      await expect(
        page.locator('.success-card, .success-icon').first(),
        'cancelling must never show the withdrawal-confirmed screen'
      ).toBeHidden();

      // 2. NOTHING WAS SENT. This is the assertion that matters: a form which fires the POST and
      // then closes would satisfy everything above.
      expect(
        attempts,
        'no withdrawal request may be submitted when the citizen cancels — a form that posts on its ' +
        'way out has already withdrawn the complaint'
      ).toEqual([]);

      // 3. The server still holds the original status. Re-read rather than trusted from step 2, so a
      // withdrawal arriving by any other route is caught too.
      const statusAfter = complaintRow(complaintNumber, ['status', 'withdrawal_reason', 'withdrawal_date']);
      expect(
        statusAfter['status'],
        'a cancelled withdrawal must leave the complaint status unchanged'
      ).toBe(statusBefore);
      expect(
        statusAfter['withdrawal_reason'],
        'a cancelled withdrawal must not persist the reason the citizen had selected'
      ).toBe('');
      expect(
        statusAfter['withdrawal_date'],
        'a cancelled withdrawal must not stamp a withdrawal date'
      ).toBe('');

      // 4. And the complaint is still withdrawable, so cancelling did not cost the citizen the right.
      const reachedAgain = await openWithdrawalForm(page, complaintNumber, token);
      expect(
        reachedAgain,
        'after cancelling, the citizen must still be able to withdraw the complaint'
      ).toBe(true);
    });

  // ══ Case B: the citizen's own timeline must show the withdrawal, with a timestamp ════════════
  test('after withdrawal the tracker shows WITHDRAWN and a "Complaint Withdrawn" timeline event with a timestamp',
    async ({ page, request }) => {
      const token = await citizenLogin(request);

      const { complaintNumber } = await createTestComplaint(request, {
        subject: 'E2E Withdrawal — citizen timeline event',
        complainantName: 'Timeline Watching Citizen',
        complainantPhone: CITIZEN_PHONE,
      });

      // Withdrawn through the UI the citizen actually uses, so the timeline is written by the same
      // code path the manual case exercises.
      const reached = await openWithdrawalForm(page, complaintNumber, token);
      expect(reached).toBe(true);
      await page.locator(`.radio-group label:has-text("${VALID_REASON}")`).first().click();
      await page.locator('button.btn-submit, button:has-text("Confirm"), button:has-text("Withdraw")')
        .first().click();
      await expect(page.locator('.success-card, .success-icon').first()).toBeVisible({ timeout: 20000 });

      expect(
        complaintRow(complaintNumber, ['status'])['status'],
        'the withdrawal must have been applied before the timeline is read'
      ).toBe('withdrawn');

      // ── The citizen's view ──────────────────────────────────────────────────────────────────
      await page.goto(`${APP_BASE}/public/track/${complaintNumber}`);
      await page.waitForLoadState('networkidle');

      // 1. The status the citizen is shown.
      await expect(
        page.locator('.status-card').first(),
        'the tracker must render the complaint the citizen just withdrew'
      ).toBeVisible({ timeout: 20000 });
      await expect(
        page.locator('app-status-badge').first(),
        'the citizen must be shown that the complaint is WITHDRAWN'
      ).toContainText(/withdrawn/i);

      // 2. The EVENT, on the timeline, where the citizen can see it.
      const timeline = page.locator('.timeline-section');
      await expect(
        timeline,
        'the citizen-facing tracker must render a timeline — the staff /history endpoint is not ' +
        'something a complainant can read'
      ).toBeVisible({ timeout: 15000 });

      const withdrawalEntry = timeline.locator('.timeline-entry', {
        has: page.locator(`.timeline-action:text-is("${CITIZEN_TIMELINE_LABEL}")`),
      });
      await expect(
        withdrawalEntry,
        `the citizen's timeline must carry a "${CITIZEN_TIMELINE_LABEL}" event. The stored action ` +
        "token is the internal lowercase 'withdrawn' (ComplaintApiV1Controller.java:632); rendering " +
        'that token verbatim shows a complainant an internal status name, not an event they can read'
      ).toHaveCount(1);

      // 3. With a TIMESTAMP. An undated event cannot answer "when was my complaint withdrawn".
      const stamp = withdrawalEntry.locator('.timeline-date');
      await expect(stamp, 'the withdrawal event must be dated').toBeVisible();
      expect(
        (await stamp.innerText()).trim(),
        'the withdrawal timestamp must be a real rendered date and time, not an empty node or a raw ' +
        'ISO string'
      ).toMatch(/\d{2}\s\w{3}\s\d{4},\s\d{2}:\d{2}\s(AM|PM)/i);
    });
});
