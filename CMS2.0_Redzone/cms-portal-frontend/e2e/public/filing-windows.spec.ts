import { test, expect, Page, APIRequestContext } from '../fixtures';
import { seedCitizenSession, cleanupComplaint } from '../utils/test-data';

/**
 * Complaint FILING-WINDOW boundaries — the 310-day window after the citizen complained to the
 * Regulated Entity, and the 90-day window after the RE's response.
 *
 * ── The windows are CONFIGURATION, not constants ────────────────────────────────────────────────
 * 310 is cms.eligibility.grievance-filing-window-days, served as `grievanceFilingWindowDays`; 90 is
 * cms.mre.filing-deadline-days, served as `filingDeadlineDays`. Both come from
 * GET /api/v1/eligibility/questions. So NO test here pins 310 or 90 as a literal in an assertion of
 * the rule: every boundary case reads the served value and computes its fixture from it, or stubs a
 * deliberately different value and asserts the product obeys THAT. A test hardcoding 310 would still
 * pass if the property were ignored, which is the exact defect at issue.
 *
 * The QA sheet writes fixed calendar dates (2026-01-01 filing, 2026-03-15 response). Those are NOT
 * used: a fixed date drifts out of every window as the clock advances, so the cases are expressed as
 * offsets from today and day 310 is always day 310.
 *
 * ── TWO windows, TWO anchor dates, and a REFUSAL ────────────────────────────────────────────────
 * Ruled by the business owner: a filing past the window is REFUSED, not merely flagged. And which
 * window applies depends on whether the RE replied, so this is two rules and not one:
 *
 *   RE never replied → the window runs from the RE COMPLAINT date, and its length is
 *                      grievanceFilingWindowDays.
 *   RE did reply     → the window runs from the REPLY date, and its length is filingDeadlineDays.
 *                      The RE complaint date then stops mattering: a reply on day 300 still leaves a
 *                      full filingDeadlineDays, which is why one window could not express both.
 *
 * Both limbs are enforced TWICE, and both layers are asserted here:
 *   - file-complaint.component.ts applyFilingWindowBlock, which now sets eligibilityBlocked and sends
 *     the citizen to phase 'non-maintainable' (rendered in .nm-card) instead of warning and letting
 *     them through. `.time-barred-warning` no longer exists — that block was deleted with the signal.
 *   - ComplaintApiV1Controller.filingWindowRefusal, on POST /api/v1/complaints, because the browser
 *     check is bypassable. Probed live before the fix: an RE date 400 days old was accepted with 201.
 *
 * Still NOT reconciled, and deliberately pinned as-is at the foot of this file:
 *   EligibilityWizardController:108 `long limitDays = 365L` — a hardcoded 365 on the STANDALONE
 *   advisory wizard (POST /wizard-check), contradicting both the config and limitationPeriodYears=3.
 *   An open business question; changing it silently would settle a question nobody has answered.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

/** QA's expected refusal text, quoted exactly. Asserted, never softened. */
const EXPECTED_REJECTION = 'Complaint filing period has expired.';

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  // fallback(), not continue(): per-test stub routes must still chain.
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════════════════════════

/**
 * ISO date `days` whole days before today, counted the way the product counts (from midnight).
 *
 * Formatted from LOCAL fields, not via toISOString(): that converts to UTC first, so in IST (UTC+5:30)
 * local midnight is 18:30 the PREVIOUS day and the sliced string is yesterday. Every boundary here
 * would be off by one, in the direction that makes an inclusive boundary look exclusive.
 */
function daysAgo(days: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - days);
  return toLocalIso(d);
}

function toLocalIso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function isoToDisplay(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** ISO date `days` whole days AFTER a given ISO date. */
function isoPlusDays(iso: string, days: number): string {
  const d = new Date(iso);
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() + days);
  return toLocalIso(d);
}

interface ServedWindows {
  grievanceFilingWindowDays: number;
  filingDeadlineDays: number;
  reWindowDays: number;
}

/** The configured windows, from the API. Never assumed to be 310 / 90 / 30. */
async function servedWindows(request: APIRequestContext): Promise<ServedWindows> {
  const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();

  for (const field of ['grievanceFilingWindowDays', 'filingDeadlineDays', 'reWindowDays'] as const) {
    expect(typeof body[field], `${field} is not served, so that window is not configurable`)
      .toBe('number');
    expect(body[field], `${field} must be a positive window`).toBeGreaterThan(0);
  }
  return body as ServedWindows;
}

const ENTITY_ROW = {
  questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
  questionText: 'Select Regulated Entity Name', translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

const FILED_WITH_RE_ROW = {
  questionNumber: 2, questionKey: 'filedWithRE', questionType: 'radio',
  questionText: 'Have you filed a written / electronic complaint with the Regulated Entity?',
  translationKey: null, applicableEntityType: 'ALL', blockOn: 'no',
  blockMessage: 'BLOCKED-filedWithRE: in terms of clause {{clause}} the complaint cannot be processed.',
  blockMessageKey: null, clauseReference: '10(1)(j)',
  nonMaintainable: true, inlineSubQuestion: false,
};

const RECEIVED_REPLY_ROW = {
  questionNumber: 3, questionKey: 'receivedReply', questionType: 'radio',
  questionText: 'Have you received any reply from the Entity?', translationKey: null,
  applicableEntityType: 'ALL', blockOn: null, blockMessage: null, blockMessageKey: null,
  clauseReference: null, nonMaintainable: true, inlineSubQuestion: false,
};

/** A trailing non-blocking question, so "advanced past the gate" is observable as ARRIVING somewhere. */
const SENTINEL_TEXT = 'SENTINEL question reached after the filing-window gate';
const SENTINEL_ROW = {
  questionNumber: 90, questionKey: 'sentinelAfterFilingWindow', questionType: 'radio',
  questionText: SENTINEL_TEXT, translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

/**
 * Stubs the question master. The three windows are REQUIRED arguments rather than defaulted: these
 * tests exist to prove the windows come from this response, so no test may silently rely on 310/90/30.
 *
 * applicableEntityType is forced to ALL so the questions are visible whichever seeded entity the
 * dropdown offers first (isQuestionVisible derives applicability from the entity's department).
 */
async function stubMaster(page: Page, windows: ServedWindows, rows: Record<string, unknown>[]) {
  await page.route('**/api/v1/eligibility/questions*', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        schemeVersion: 'RBIOS_2021',
        schemeName: 'Reserve Bank - Integrated Ombudsman Scheme, 2021',
        reWindowDays: windows.reWindowDays,
        grievanceFilingWindowDays: windows.grievanceFilingWindowDays,
        filingDeadlineDays: windows.filingDeadlineDays,
        limitationPeriodYears: 3,
        data: [ENTITY_ROW, ...rows, SENTINEL_ROW],
      }),
    }));
}

async function openFileComplaint(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500091', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // A non-existent Angular route falls through to the public home page, so confirm the wizard mounted.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });
}

async function selectEntityAndAdvance(page: Page) {
  // The entity question is a SEARCHABLE COMBOBOX, not a native <select>. The select that used to sit
  // beside it was a SECOND control bound to the same answer and has been removed, so this is the only
  // path a citizen has.
  const box = page.locator('input.entity-search-input');
  await expect(box).toBeVisible({ timeout: 20000 });
  // The results list renders only while the dropdown is open, which focus does.
  await box.click();
  const first = page.locator('li.entity-search-option').first();
  await expect(first, 'the entity master offered nothing to select').toBeVisible({ timeout: 20000 });
  const name = ((await first.locator('.es-name').textContent()) || '').trim();
  // mousedown, not click: the option's handler is (mousedown), which fires BEFORE the input's blur
  // closes the list. A click would let blur remove the option mid-gesture.
  await first.dispatchEvent('mousedown');
  await expect(box, 'the chosen entity was not recorded').toHaveValue(name, { timeout: 10000 });
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
}

/** YES_NO_OPTIONS is [yes, no]; the template binds [value], so options are picked by order. */
async function answerCurrent(page: Page, value: 'yes' | 'no') {
  await page.locator('.radio-list .radio-option').nth(value === 'yes' ? 0 : 1).click();
}

/** Types a dd/mm/yyyy date into a masked date sub-field, the way a citizen does. */
async function enterDate(page: Page, name: string, iso: string) {
  const input = page.locator(`input[name="${name}"]`);
  await expect(input).toBeVisible();
  await input.fill('');
  await input.pressSequentially(isoToDisplay(iso).replace(/\//g, ''));
  await expect(input).toHaveValue(isoToDisplay(iso));
}

async function attachFile(page: Page, inputId: string, filename: string) {
  await page.locator(`input#${inputId}`).setInputFiles({
    name: filename, mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 e2e'),
  });
}

/**
 * Walks the gate to the point where the filing window is assessed: entity -> filedWithRE=yes
 * (+ RE-complaint date and copy) -> the receivedReply question.
 */
async function walkToReplyQuestion(page: Page, reComplaintDateIso: string) {
  await selectEntityAndAdvance(page);
  await answerCurrent(page, 'yes');
  await enterDate(page, 'bankComplaintDate', reComplaintDateIso);
  await attachFile(page, 'complaintFileWithRE', 're-complaint.pdf');
  await expect(page.locator('.uploaded-file-name')).toContainText('re-complaint.pdf');
  await page.locator('button.btn-next').click();
  await expect(page.locator('.question-text')).toContainText('reply', { timeout: 20000 });
}

/**
 * The full path a citizen takes when the RE DID reply: also supplies the reply date and its copy.
 * Leaves the page wherever the product decided to go after Next.
 */
async function walkWithReply(page: Page, reComplaintDateIso: string, replyDateIso: string) {
  await walkToReplyQuestion(page, reComplaintDateIso);
  await answerCurrent(page, 'yes');
  await enterDate(page, 'replyDate', replyDateIso);
  await attachFile(page, 'replyFileInput', 're-reply.pdf');
  await page.locator('button.btn-next').click();
}

/** Walks the no-reply path, which is the branch that assesses the 310-day window. */
async function walkWithoutReply(page: Page, reComplaintDateIso: string) {
  await walkToReplyQuestion(page, reComplaintDateIso);
  await answerCurrent(page, 'no');
  await page.locator('button.btn-next').click();
}

/** POSTs the standalone advisory wizard's check, so the SERVER's own answer can be asserted. */
async function wizardCheck(request: APIRequestContext, answers: Record<string, string>) {
  const res = await request.post(`${API_BASE}/api/v1/eligibility/wizard-check`, { data: answers });
  expect(res.status(), 'wizard-check should answer 200 even when refusing').toBe(200);
  return res.json();
}

/** POSTs the MRE evaluator, which is where the 90-day deadline actually lives. */
async function mreEvaluate(request: APIRequestContext, facts: Record<string, unknown>) {
  const res = await request.post(`${API_BASE}/api/v1/mre/evaluate`, { data: facts });
  expect(res.status(), 'the MRE evaluator should answer 200 even when finding a ground failed')
    .toBe(200);
  return res.json();
}

function groundStatus(verdict: any, ground: string): string | undefined {
  return (verdict.groundVerdicts as any[]).find(g => g.ground === ground)?.status;
}

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 1 & 3 — ACCEPTED within, and exactly ON, the 310th day after the RE filing
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Within the grievance filing window the complaint is accepted', () => {

  test('QA1: a complaint well inside the filing window advances with no time-bar raised', async ({ page, request }) => {
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    // Inside BOTH windows: past the RE window (so no wait block) and short of the filing window.
    const inside = Math.floor((w.reWindowDays + w.grievanceFilingWindowDays) / 2);
    await walkWithoutReply(page, daysAgo(inside));

    await expect(page.locator('.nm-card'),
      `day ${inside} is inside the ${w.grievanceFilingWindowDays}-day filing window but was refused ` +
      `as time-barred`).toHaveCount(0);
    await expect(page.locator('.block-note'),
      'a complaint inside both windows must not be blocked').toHaveCount(0);
    await expect(page.locator('.question-text'),
      'the citizen was not allowed past the filing-window gate').toContainText(SENTINEL_TEXT);
  });

  test('QA3: exactly the 310th day is INSIDE the window — the boundary is inclusive', async ({ page, request }) => {
    // The guard is `daysSince(filedDate) <= grievanceFilingWindowDays`, so day N is inside. An
    // exclusive boundary would time-bar a citizen a day early, on their last lawful day.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkWithoutReply(page, daysAgo(w.grievanceFilingWindowDays));

    await expect(page.locator('.nm-card'),
      `day ${w.grievanceFilingWindowDays} must be INSIDE the window — the boundary is inclusive`)
      .toHaveCount(0);
    await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
  });

  test('QA3: the boundary moves with the configured window, not with 310', async ({ page, request }) => {
    // The decisive configurability test. With a served window of 120, day 200 must be time-barred: if
    // the component enforced a compiled-in 310 it would let this through silently.
    const w = await servedWindows(request);
    const SERVED = 120;
    await stubMaster(page, { ...w, grievanceFilingWindowDays: SERVED }, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkWithoutReply(page, daysAgo(200));

    await expect(page.locator('.nm-card'),
      `day 200 was not time-barred against a configured ${SERVED}-day window, so the window is ` +
      `hardcoded rather than read from grievanceFilingWindowDays`).toBeVisible({ timeout: 20000 });
  });

  test('QA3: a longer configured window accepts a filing that 310 would have barred', async ({ page, request }) => {
    // The other direction, so the test above cannot pass merely by flagging everything. With a served
    // window of 500, day 400 must NOT be time-barred.
    const w = await servedWindows(request);
    const SERVED = 500;
    await stubMaster(page, { ...w, grievanceFilingWindowDays: SERVED }, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkWithoutReply(page, daysAgo(400));

    await expect(page.locator('.nm-card'),
      `day 400 was time-barred against a configured ${SERVED}-day window, so the window is hardcoded`)
      .toHaveCount(0);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 4 — the 311th day must be REJECTED
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Beyond the grievance filing window the complaint is rejected', () => {

  test('QA4: the day after the window is recognised as out of time, and the prose quotes the served window', async ({ page, request }) => {
    // The refusal must quote the CONFIGURED window, not a compiled-in figure.
    const w = await servedWindows(request);
    const SERVED = 120;
    await stubMaster(page, { ...w, grievanceFilingWindowDays: SERVED }, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkWithoutReply(page, daysAgo(SERVED + 1));

    const reason = page.locator('.nm-reason');
    await expect(reason, `day ${SERVED + 1} must be recognised as beyond the filing window`)
      .toBeVisible({ timeout: 20000 });

    const text = await reason.innerText();
    // 120 appears nowhere in the source or the seeded prose, so it can only be rendered if the
    // component interpolated the SERVED window. This is the regression guard for V104.
    expect(text, `the time-bar prose ignores the served window: ${text}`).toContain(String(SERVED));
    expect(text, 'the time-bar prose still quotes a hardcoded 310-day window').not.toContain('310');
    expect(text, 'the raw placeholder reached the citizen').not.toContain('{{days}}');
  });

  test('QA4: the 311th day is REJECTED with "Complaint filing period has expired."', async ({ page, request }) => {
    // The business ruling: past the window the filing is REFUSED, not flagged. applyFilingWindowBlock
    // now returns true and nextEligibility() routes to phase 'non-maintainable', so the citizen cannot
    // reach the form at all.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const pastWindow = w.grievanceFilingWindowDays + 1;
    await walkWithoutReply(page, daysAgo(pastWindow));

    // An auto-retrying locator assertion, not innerText(): the click triggers an Angular re-render, and
    // an immediate read would capture the pre-render DOM and report the wrong reason for the failure.
    await expect(page.locator('.nm-card'),
      `day ${pastWindow} is beyond the ${w.grievanceFilingWindowDays}-day filing window and must be ` +
      `refused with "${EXPECTED_REJECTION}"`)
      .toContainText(EXPECTED_REJECTION, { timeout: 15000 });

    // Absence of the TEXT, not `.not.toContainText` on `.question-text`: a refusal replaces the whole
    // wizard with the refusal card, so the question element is gone and a not-contains assertion on a
    // missing locator fails — reporting the desired outcome as a defect.
    await expect(page.getByText(SENTINEL_TEXT),
      'a rejected complaint must not advance to the next question').toHaveCount(0);
  });

  test('QA4: a time-barred complaint is not accepted by the registration API either', async ({ request }) => {
    // The authoritative layer: the browser check is bypassable, so the server must refuse too.
    // ComplaintApiV1Controller.filingWindowRefusal runs after bean validation and before persistence.
    // Probed live BEFORE this was built: an RE date 400 days old was accepted with 201.
    const w = await servedWindows(request);
    const pastWindow = w.grievanceFilingWindowDays + 1;
    const unique = Date.now().toString(36);

    const res = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: {
        filingType: 'ONLINE', category: 'GENERAL',
        complainantName: 'Filing Window Boundary',
        complainantEmail: `filingwindow_${unique}@example.com`,
        complainantPhone: '9876500091',
        complainantAddress: 'E2E address',
        entityName: 'Test Bank Ltd', entityType: 'BANK',
        subject: 'Filing window boundary',
        description: 'A complaint filed beyond the configured grievance filing window.',
        priorReComplaint: true,
        reComplaintDate: daysAgo(pastWindow),
        declarationAccepted: true,
      },
      failOnStatusCode: false,
    });

    const text = await res.text();

    // Should the guard ever regress this call SUCCEEDS, leaving a real complaint behind on every run.
    // Closed before the assertion, or a failing test would litter cms_db indefinitely.
    if (res.status() === 201) {
      const created = (JSON.parse(text).data ?? {}).complaintId;
      if (created) await cleanupComplaint(request, created);
    }

    expect(res.status(),
      `an RE complaint dated ${pastWindow} days ago is beyond the configured ` +
      `${w.grievanceFilingWindowDays}-day window, so POST /api/v1/complaints must refuse it — it ` +
      `answered ${res.status()}. A browser-only check is bypassable.`).toBe(400);

    expect(text,
      'the refusal must name the filing window, or a citizen cannot tell a timing bar from a ' +
      'validation error').toMatch(/filing period has expired|filing window|time-barred/i);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 2, 5 & 6 — the 90-day window after the RE's response
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The 90-day window after the RE response', () => {

  test('the response window is served to the portal as configuration', async ({ request }) => {
    const w = await servedWindows(request);
    expect(w.filingDeadlineDays, 'filingDeadlineDays must be a plausible day count')
      .toBeLessThan(400);
  });

  test('QA2: a complaint filed inside the response window is accepted', async ({ page, request }) => {
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    // Reply received partway through the response window; RE complaint older still.
    const replyDaysAgo = Math.floor(w.filingDeadlineDays / 2);
    const filedDaysAgo = replyDaysAgo + w.reWindowDays;
    await walkWithReply(page, daysAgo(filedDaysAgo), daysAgo(replyDaysAgo));

    await expect(page.locator('.replyDateError, .field-error'),
      `a reply received ${replyDaysAgo} days ago is inside the ${w.filingDeadlineDays}-day response ` +
      `window and must be accepted`).toHaveCount(0);
    await expect(page.locator('.question-text'),
      'a complaint inside the response window was not allowed to advance').toContainText(SENTINEL_TEXT);
  });

  test('QA5: exactly the 90th day after the response is accepted', async ({ page, request }) => {
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const replyDaysAgo = w.filingDeadlineDays;
    const filedDaysAgo = replyDaysAgo + w.reWindowDays;
    await walkWithReply(page, daysAgo(filedDaysAgo), daysAgo(replyDaysAgo));

    await expect(page.locator('.field-error'),
      `day ${w.filingDeadlineDays} after the response must be INSIDE the window — the boundary is ` +
      `inclusive`).toHaveCount(0);
    await expect(page.locator('.question-text')).toContainText(SENTINEL_TEXT);
  });

  test('QA6: the 91st day after the response is REJECTED with "Complaint filing period has expired."', async ({ page, request }) => {
    // The second limb of the ruling: once the RE has replied the citizen has filingDeadlineDays FROM
    // THE REPLY, and past that the filing is refused. Note the RE complaint date here is far outside
    // grievanceFilingWindowDays too, but it is the reply date that governs — which is precisely why
    // applyFilingWindowBlock picks its anchor from `receivedReply` rather than always using the RE date.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const replyDaysAgo = w.filingDeadlineDays + 1;
    const filedDaysAgo = replyDaysAgo + w.reWindowDays;
    await walkWithReply(page, daysAgo(filedDaysAgo), daysAgo(replyDaysAgo));

    // Auto-retrying, for the same reason as the QA4 case: an immediate innerText() read would capture
    // the DOM before Angular re-rendered and blame the wrong thing.
    await expect(page.locator('.nm-card'),
      `a reply received ${replyDaysAgo} days ago is beyond the ${w.filingDeadlineDays}-day response ` +
      `window and must be refused with "${EXPECTED_REJECTION}"`)
      .toContainText(EXPECTED_REJECTION, { timeout: 15000 });

    await expect(page.getByText(SENTINEL_TEXT),
      'a complaint filed beyond the response window must not advance').toHaveCount(0);
  });

  test('QA6: the response-window refusal quotes the served response window, not the filing window', async ({ page, request }) => {
    // The two limbs must not be confusable. A deliberately distinctive response window of 47 days is
    // served: 47 appears in no source file and no seeded string, so it can only be rendered if the
    // component interpolated filingDeadlineDays — and NOT grievanceFilingWindowDays, which is stubbed
    // to a different value here so a mixed-up anchor would show the wrong number.
    const w = await servedWindows(request);
    const RESPONSE = 47;
    const FILING = 700;
    await stubMaster(page, { ...w, filingDeadlineDays: RESPONSE, grievanceFilingWindowDays: FILING },
      [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    // Inside the (large) filing window, outside the (small) response window — so only the post-reply
    // limb can be the thing that refuses this.
    await walkWithReply(page, daysAgo(200), daysAgo(RESPONSE + 1));

    const reason = page.locator('.nm-reason');
    await expect(reason, `a reply ${RESPONSE + 1} days old is beyond the served ${RESPONSE}-day window`)
      .toBeVisible({ timeout: 20000 });

    const text = await reason.innerText();
    expect(text, `the refusal ignores the served response window: ${text}`).toContain(String(RESPONSE));
    expect(text, 'the refusal quoted the FILING window, so the wrong limb fired').not.toContain(String(FILING));
    expect(text, 'the raw placeholder reached the citizen').not.toContain('{{days}}');
  });

  test('QA6: the registration API refuses a filing beyond the response window', async ({ request }) => {
    // Server-side, and the case that could not even be expressed before: reReplyDate now exists on the
    // entity, the DTO and the submit payload. The RE complaint date sent here is INSIDE
    // grievanceFilingWindowDays, so only the post-reply limb can refuse it — if the server collapsed
    // both limbs into the RE-date window this call would be accepted.
    const w = await servedWindows(request);
    const unique = Date.now().toString(36);
    const replyDaysAgo = w.filingDeadlineDays + 1;

    const res = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: {
        filingType: 'ONLINE', category: 'GENERAL',
        complainantName: 'Response Window Boundary',
        complainantEmail: `responsewindow_${unique}@example.com`,
        complainantPhone: '9876500091',
        complainantAddress: 'E2E address',
        entityName: 'Test Bank Ltd', entityType: 'BANK',
        subject: 'Response window boundary',
        description: 'A complaint filed beyond the configured post-reply filing window.',
        priorReComplaint: true,
        reRepliedAndDissatisfied: true,
        reComplaintDate: daysAgo(replyDaysAgo + 5),
        reReplyDate: daysAgo(replyDaysAgo),
        declarationAccepted: true,
      },
      failOnStatusCode: false,
    });

    const text = await res.text();
    if (res.status() === 201) {
      const created = (JSON.parse(text).data ?? {}).complaintId;
      if (created) await cleanupComplaint(request, created);
    }

    expect(res.status(),
      `a reply dated ${replyDaysAgo} days ago is beyond the configured ${w.filingDeadlineDays}-day ` +
      `response window, so POST /api/v1/complaints must refuse it — it answered ${res.status()}`)
      .toBe(400);
    expect(text, 'the refusal must name the window rather than reading as a validation error')
      .toMatch(/filing period has expired/i);
  });

  test('a filing well inside the response window is still accepted by the API', async ({ request }) => {
    // The other direction, so the guard above cannot pass by refusing everything with a reply date.
    // The RE complaint date here is deliberately OUTSIDE grievanceFilingWindowDays: the ruling is that
    // once the RE replies the reply date governs, so this must be ACCEPTED even though the RE-date
    // limb would have barred it. This is the case that proves the two limbs are genuinely separate.
    const w = await servedWindows(request);
    const unique = Date.now().toString(36);

    const res = await request.post(`${API_BASE}/api/v1/complaints`, {
      data: {
        filingType: 'ONLINE', category: 'GENERAL',
        complainantName: 'Response Window Inside',
        complainantEmail: `responseinside_${unique}@example.com`,
        complainantPhone: '9876500091',
        complainantAddress: 'E2E address',
        entityName: 'Test Bank Ltd', entityType: 'BANK',
        subject: 'Response window inside',
        description: 'A complaint filed inside the post-reply window but late by the RE-date window.',
        priorReComplaint: true,
        reRepliedAndDissatisfied: true,
        reComplaintDate: daysAgo(w.grievanceFilingWindowDays + 30),
        reReplyDate: daysAgo(1),
        declarationAccepted: true,
      },
      failOnStatusCode: false,
    });

    const text = await res.text();
    expect(res.status(),
      `a reply received yesterday is inside the ${w.filingDeadlineDays}-day response window, so the ` +
      `filing is timely however old the RE complaint is — the API answered ${res.status()}: ${text}`)
      .toBe(201);

    const created = (JSON.parse(text).data ?? {}).complaintId;
    if (created) await cleanupComplaint(request, created);
  });

  test('QA6: the response window is enforced somewhere in the product, on the served value', async ({ request }) => {
    // Where the 90-day rule DOES live, asserted against the served config rather than the number 90.
    //
    // The MRE measures from the reference date — reLastCommunicationDate (the RE's response) when
    // present. This is the only place in the product that applies the response window, and it is
    // reachable only through /api/v1/mre/evaluate, never from the citizen filing path.
    //
    // NOTE cms.mre.window-basis is BUSINESS on this environment, so the engine counts BUSINESS days.
    // The boundary is therefore swept rather than asserted at a single offset: the test proves the
    // rule EXISTS and is monotonic in the served window, without asserting a calendar-day boundary
    // the engine does not use.
    const w = await servedWindows(request);
    const filed = daysAgo(w.filingDeadlineDays * 3);

    const inside = await mreEvaluate(request, {
      entityCode: 'HDFC', priorReComplaint: true, reComplaintDate: filed,
      reLastCommunicationDate: daysAgo(1), filingDate: daysAgo(0),
    });
    expect(groundStatus(inside, 'FILED_BEYOND_DEADLINE'),
      'a response received yesterday is plainly inside the response window').toBe('PASS');

    // Far outside by any basis: calendar OR business days.
    const outside = await mreEvaluate(request, {
      entityCode: 'HDFC', priorReComplaint: true, reComplaintDate: filed,
      reLastCommunicationDate: daysAgo(w.filingDeadlineDays * 3), filingDate: daysAgo(0),
    });
    expect(groundStatus(outside, 'FILED_BEYOND_DEADLINE'),
      `a response received ${w.filingDeadlineDays * 3} days ago is beyond the configured ` +
      `${w.filingDeadlineDays}-day response window, so the deadline ground must FAIL — otherwise ` +
      `the response window is not enforced anywhere at all`).toBe('FAIL');

    expect(JSON.stringify(outside.groundVerdicts),
      'the refusal must quote the configured window rather than a literal')
      .toContain(String(w.filingDeadlineDays));
  });

  test('the response window served to the portal is the one the portal enforces', async ({ page, request }) => {
    // filingDeadlineDays used to be dead configuration: fetched into the component and read by nothing.
    // This is the regression guard. A deliberately tiny response window of 2 days is served, and a reply
    // received 60 days ago is then massively outside it — so the wizard MUST refuse. If it advances, the
    // served value is being ignored again and the 90 days is back to being hardcoded or unused.
    const w = await servedWindows(request);
    await stubMaster(page, { ...w, filingDeadlineDays: 2 }, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkWithReply(page, daysAgo(60 + w.reWindowDays), daysAgo(60));

    await expect(page.locator('.nm-card'),
      'a reply 60 days old was accepted against a served 2-day response window, so the wizard is not ' +
      'reading filingDeadlineDays').toBeVisible({ timeout: 20000 });
    await expect(page.getByText(SENTINEL_TEXT),
      'a complaint beyond the served response window must not advance').toHaveCount(0);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 7 — the RE responds the SAME DAY as the RE filing
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('A response on the same day as the RE filing is a valid date', () => {

  test('QA7: a same-day response is accepted and the date field is not marked invalid', async ({ page, request }) => {
    // QA's example is filing 2026-06-15 / response 2026-06-15. Expressed as an offset so it stays
    // inside every window: the guard is `new Date(replyDate) < new Date(complaintDate)`, which is
    // strictly-less-than, so equal dates must pass. A `<=` here would reject a same-day reply, and a
    // same-day reply is entirely ordinary — REs frequently acknowledge and reject on the spot.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const sameDay = daysAgo(Math.floor(w.filingDeadlineDays / 2));
    await walkWithReply(page, sameDay, sameDay);

    await expect(page.locator('.field-error'),
      'a response received on the SAME DAY as the RE complaint was rejected as an invalid date')
      .toHaveCount(0);
    await expect(page.locator('input[name="replyDate"].invalid'),
      'the response-date field was marked invalid for a same-day response').toHaveCount(0);
    await expect(page.locator('.question-text'),
      'a same-day response must let the citizen proceed').toContainText(SENTINEL_TEXT);
  });

  test('QA7: the same-day response survives to the review screen as the date entered', async ({ page, request }) => {
    // "Treated as a valid date" has to mean the value is KEPT, not merely that no error appeared.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const sameDay = daysAgo(Math.floor(w.filingDeadlineDays / 2));
    await walkWithReply(page, sameDay, sameDay);

    await expect(page.locator('input[name="replyDate"]'),
      'the same-day response date was silently discarded').toHaveValue(isoToDisplay(sameDay));
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 8 — a response EARLIER than the RE filing date
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('A response earlier than the RE filing date is rejected', () => {

  test('QA8: the response date is refused and the field is marked invalid', async ({ page, request }) => {
    // QA's example is filing 2026-06-01 / response 2026-05-01 — a reply one month BEFORE the complaint
    // it answers, which is impossible. Expressed as offsets so both dates stay in the past (a future
    // date would trip a different rule and the test would pass for the wrong reason).
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const filed = daysAgo(60);
    const replyBefore = daysAgo(90);

    await walkToReplyQuestion(page, filed);
    await answerCurrent(page, 'yes');
    await enterDate(page, 'replyDate', replyBefore);
    await attachFile(page, 'replyFileInput', 're-reply.pdf');
    await page.locator('button.btn-next').click();

    const error = page.locator('.field-error');
    await expect(error.first(),
      'a response dated BEFORE the complaint it answers was accepted without complaint')
      .toBeVisible({ timeout: 20000 });
    await expect(error.first()).toContainText(/earlier than|before/i);

    // QA requires the FIELD to be marked invalid, not merely a message somewhere on the page.
    await expect(page.locator('input[name="replyDate"].invalid'),
      'the response-date field was not marked invalid, so the citizen cannot see WHICH date is wrong')
      .toBeVisible();

    await expect(page.locator('.question-text'),
      'an impossible response date must not let the citizen advance').not.toContainText(SENTINEL_TEXT);
  });

  test('QA8: correcting the response date clears the invalid state', async ({ page, request }) => {
    // A validation that latches would leave the citizen stuck on a date they have already fixed.
    const w = await servedWindows(request);
    await stubMaster(page, w, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const filed = daysAgo(60);
    await walkToReplyQuestion(page, filed);
    await answerCurrent(page, 'yes');
    await enterDate(page, 'replyDate', daysAgo(90));
    await attachFile(page, 'replyFileInput', 're-reply.pdf');
    await page.locator('button.btn-next').click();
    await expect(page.locator('input[name="replyDate"].invalid')).toBeVisible({ timeout: 20000 });

    // Correct it to a date after the RE complaint.
    await enterDate(page, 'replyDate', daysAgo(30));

    await expect(page.locator('input[name="replyDate"].invalid'),
      'the response-date field stayed marked invalid after the citizen corrected it').toHaveCount(0);
    await page.locator('button.btn-next').click();
    await expect(page.locator('.question-text'),
      'a corrected response date must let the citizen proceed').toContainText(SENTINEL_TEXT);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// What the product ACTUALLY enforces — pinning the three disagreeing rules
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The three disagreeing filing-window rules, pinned as they are', () => {

  test('the standalone advisory wizard still enforces a hardcoded 365 days', async ({ request }) => {
    // EligibilityWizardController:108 `long limitDays = 365L`, contradicting both the 310 config it
    // serves from the very same class and the limitationPeriodYears=3 it serves alongside it.
    //
    // This is a KNOWN OPEN ITEM awaiting a business ruling, so it is pinned AS IT IS rather than
    // corrected: silently changing 365 to 310 would settle a question the user has not answered. If
    // the ruling lands and the constant moves, this test fails and must be updated deliberately.
    const w = await servedWindows(request);

    // Day 310 — inside the served window, and also inside the wizard's 365 — is READY.
    const atConfiguredWindow = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(w.grievanceFilingWindowDays),
      reRespondedSatisfactorily: 'DISSATISFIED',
    });
    expect(atConfiguredWindow.outcome).toBe('READY');

    // One day past the CONFIGURED window the wizard does NOT consider the complaint late, because it
    // measures against 365 instead. This is the divergence, recorded.
    const pastConfigured = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(w.grievanceFilingWindowDays + 1),
      reRespondedSatisfactorily: 'DISSATISFIED',
    });
    expect(pastConfigured.outcome,
      `day ${w.grievanceFilingWindowDays + 1} is past the served ${w.grievanceFilingWindowDays}-day ` +
      `window, yet the advisory wizard still answers READY because ` +
      `EligibilityWizardController:108 measures against a hardcoded 365. Recorded, not corrected: ` +
      `the 365-vs-310 contradiction is an open business question.`).toBe('READY');

    // And past 365 it flips to TOO_LATE — proving 365 is the number in force there.
    const past365 = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(366), reRespondedSatisfactorily: 'DISSATISFIED',
    });
    expect(past365.outcome,
      'the advisory wizard measures lateness against 365 days').toBe('TOO_LATE');
  });

  test('the advisory wizard does not quote the configured filing window in its refusal', async ({ request }) => {
    // The prose a citizen reads must name the window actually applied. It names "1 year".
    const w = await servedWindows(request);
    const late = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(400), reRespondedSatisfactorily: 'DISSATISFIED',
    });
    expect(late.outcome).toBe('TOO_LATE');
    expect(late.message,
      `the TOO_LATE prose quotes neither the configured ${w.grievanceFilingWindowDays}-day window ` +
      `nor any configurable figure: "${late.message}". Recorded as part of the 365-vs-310 open item.`)
      .not.toContain(String(w.grievanceFilingWindowDays));
  });

  test('the refusal the citizen reads is a translated string, in both limbs', async ({ request }) => {
    // The refusal is rendered through resolvedBlockMessage(), which looks the key up in the served
    // bundle and falls back to the English literal in the component. A missing key would therefore
    // still LOOK right in English while silently serving English to all ten other locales, so the rows
    // are asserted directly rather than inferred from the screen.
    const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const t: Record<string, string> = body.data ?? body;

    for (const key of ['eligibility.block_filing_window', 'eligibility.block_post_reply_window']) {
      expect(t[key], `${key} is not served, so the refusal cannot be localised`).toBeTruthy();
      expect(t[key], `${key} must carry QA's refusal wording`).toContain(EXPECTED_REJECTION);
      expect(t[key], `${key} must interpolate the configured window rather than quote a literal`)
        .toContain('{{days}}');
    }
  });

  test('both refusal keys exist in every served locale', async ({ request }) => {
    // The seeder is insert-if-absent, so a newly added key never reaches an already-seeded database —
    // hence V109. This is the guard that the migration actually ran everywhere it must.
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      for (const key of ['eligibility.block_filing_window', 'eligibility.block_post_reply_window']) {
        expect(t[key], `${key} missing for ${locale}`).toBeTruthy();
        expect(t[key], `${locale}/${key} lost its {{days}} placeholder`).toContain('{{days}}');
      }
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// The filing window is configuration end to end
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The grievance filing window is configuration, not a compiled-in constant', () => {

  test('the window is served to the portal', async ({ request }) => {
    const w = await servedWindows(request);
    expect(w.grievanceFilingWindowDays, 'grievanceFilingWindowDays must be a plausible day count')
      .toBeLessThan(2000);
  });

  test('no locale bakes the window into the time-bar prose', async ({ request }) => {
    // The regression guard for V107 / oracle V102. Bengali is included because it stores the digits in
    // Bengali numerals (৩১০), which an ASCII-only check would silently skip.
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    const BAKED = [/\b310\b/, /৩১০/];

    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;

      const value = t['eligibility.time_barred_warning'];
      expect(value, `eligibility.time_barred_warning missing for ${locale}`).toBeTruthy();
      for (const pattern of BAKED) {
        expect(pattern.test(value),
          `${locale} still bakes the filing window into the time-bar prose: ${value}`).toBe(false);
      }
    }
  });

  test('the window placeholder survives translation in every locale', async ({ request }) => {
    // Removing the digits is only half the fix: a locale that lost the placeholder too would stop
    // telling the citizen how long the window is at all.
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      const value = t['eligibility.time_barred_warning'];
      expect(value, `${locale} lost its {{days}} placeholder`).toContain('{{days}}');
    }
  });
});
