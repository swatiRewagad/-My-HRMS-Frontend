import { test, expect, Page, APIRequestContext } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * The RE 30-day entry gate (QA UST11 / UST14 / UST18).
 *
 * Under the Scheme a citizen must complain to the Regulated Entity FIRST, and the RE is entitled to a
 * response window before the Ombudsman will take the matter. The product has TWO surfaces that
 * implement this, and they must not disagree:
 *
 *   1. /public/eligibility-wizard — a standalone 4-question advisory check, which POSTs
 *      /api/v1/eligibility/wizard-check and renders the SERVER's outcome.
 *   2. /public/file-complaint — the real 14-question gate, driven by ELIGIBILITY_QUESTION_MASTER via
 *      GET /api/v1/eligibility/questions, which is what actually stops a filing.
 *
 * Both are covered here, deliberately in one file, because the whole risk is divergence.
 *
 * ── The window is CONFIGURATION, not a constant ────────────────────────────────────────────────
 * The window is cms.mre.re-window-days, served as `reWindowDays`. So no test here asserts the number
 * 30. Every window assertion either (a) reads reWindowDays from the API and computes the boundary
 * from it, or (b) stubs a deliberately non-30 value and asserts the product obeys THAT. A test that
 * hardcoded 30 would still pass if the property were ignored, which is the exact defect at issue.
 *
 * ── The boundary, verified against the product ─────────────────────────────────────────────────
 * The window is INCLUSIVE of day N: at exactly reWindowDays elapsed the citizen may proceed. Swept
 * live against /wizard-check (day 29 -> TOO_EARLY daysRemaining 1, day 30 -> READY) and the guard in
 * applyReWindowBlock is `daysSince >= reWindowDays`, whose comment records that an earlier `<= 30`
 * wrongly closed complaints a day early. Both surfaces agree.
 *
 * ── What is NOT asserted, and why ──────────────────────────────────────────────────────────────
 * Clause NUMBERS. QA's UST14 S2 cites Appendix 8 "clause 10(1)(e)" while the seeded master holds
 * 10(1)(j) for filedWithRE. Until the business owner settles that, these tests pin the PLUMBING
 * (whatever the master holds is what the citizen is shown), exactly as
 * eligibility-clause-interpolation.spec.ts does.
 *
 * Appendix 8 AUTO-CLOSURE. The product has no auto-closure path: no endpoint persists a
 * non-maintainable determination, the NM-######## case id is generated in the browser from Date.now(),
 * and AutoClosureController only EVALUATES. So the closure is asserted as what it is — a client-side
 * presentation — and the absence of persistence is pinned explicitly below so it cannot be mistaken
 * for coverage of a feature that does not exist.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];

test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  // fallback(), not continue(): per-test fault-injection routes must still chain.
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// Shared helpers
// ══════════════════════════════════════════════════════════════════════════════════════════════

/**
 * ISO date `days` whole days before today, computed the same way the product counts (from midnight).
 *
 * Formatted from the LOCAL fields, not via toISOString(): that converts to UTC first, so east of
 * Greenwich (IST is UTC+5:30) local midnight is 18:30 the PREVIOUS day and the sliced string is
 * yesterday. Every boundary here would be off by one, in the direction that makes the inclusive
 * boundary look exclusive.
 */
function daysAgo(days: number): string {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  d.setDate(d.getDate() - days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function isoToDisplay(iso: string): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** A Date as its LOCAL calendar date. See daysAgo() for why toISOString() is wrong here. */
function toLocalIso(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** The configured RE window, from the API. Never assumed to be 30. */
async function servedReWindowDays(request: APIRequestContext): Promise<number> {
  const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
  expect(typeof body.reWindowDays, 'reWindowDays is not served, so the window is not configurable')
    .toBe('number');
  expect(body.reWindowDays, 'reWindowDays must be a positive window').toBeGreaterThan(0);
  return body.reWindowDays;
}

const ENTITY_ROW = {
  questionNumber: 1, questionKey: 'regulatedEntity', questionType: 'select',
  questionText: 'Select Regulated Entity Name', translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

/**
 * Question 2: "have you complained to the RE?" — the entry gate. blockOn 'no', so answering No is the
 * RE-first refusal. Test-owned prose, so the assertion proves WHICH row blocked.
 */
const FILED_WITH_RE_ROW = {
  questionNumber: 2, questionKey: 'filedWithRE', questionType: 'radio',
  questionText: 'Have you filed a written / electronic complaint with the Regulated Entity?',
  translationKey: null, applicableEntityType: 'ALL', blockOn: 'no',
  blockMessage: 'BLOCKED-filedWithRE: in terms of clause {{clause}} the complaint cannot be processed.',
  blockMessageKey: null, clauseReference: '10(1)(j)',
  nonMaintainable: true, inlineSubQuestion: false,
};

/** Question 3: "did the RE reply?" — answering No triggers the window assessment. */
const RECEIVED_REPLY_ROW = {
  questionNumber: 3, questionKey: 'receivedReply', questionType: 'radio',
  questionText: 'Have you received any reply from the Entity?', translationKey: null,
  applicableEntityType: 'ALL', blockOn: null, blockMessage: null, blockMessageKey: null,
  clauseReference: null, nonMaintainable: true, inlineSubQuestion: false,
};

/** A trailing non-blocking question, so "advanced past the gate" is observable as ARRIVING somewhere. */
const SENTINEL_TEXT = 'SENTINEL question reached after the RE gate';
const SENTINEL_ROW = {
  questionNumber: 90, questionKey: 'sentinelAfterReGate', questionType: 'radio',
  questionText: SENTINEL_TEXT, translationKey: null, applicableEntityType: 'ALL',
  blockOn: null, blockMessage: '', blockMessageKey: null, clauseReference: null,
  nonMaintainable: false, inlineSubQuestion: false,
};

/**
 * Stubs the question master. `reWindowDays` is a REQUIRED argument rather than defaulted to 30: these
 * tests exist to prove the window comes from this response, so no test may silently rely on 30.
 *
 * applicableEntityType is forced to ALL so the questions are visible whichever seeded entity the
 * dropdown happens to offer first (isQuestionVisible derives applicability from its department).
 */
async function stubMaster(page: Page, reWindowDays: number, rows: Record<string, unknown>[]) {
  await page.route('**/api/v1/eligibility/questions*', route =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        success: true,
        schemeVersion: 'RBIOS_2021',
        schemeName: 'Reserve Bank - Integrated Ombudsman Scheme, 2021',
        reWindowDays,
        grievanceFilingWindowDays: 310,
        filingDeadlineDays: 90,
        data: [ENTITY_ROW, ...rows, SENTINEL_ROW],
      }),
    }));
}

async function openFileComplaint(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500088', 'e2e-seeded-token.sig');
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

/** Types a dd/mm/yyyy date into the filedWithRE sub-field, which masks input as the citizen types. */
async function enterReComplaintDate(page: Page, iso: string) {
  const input = page.locator('input[name="bankComplaintDate"]');
  await expect(input).toBeVisible();
  await input.fill('');
  await input.pressSequentially(isoToDisplay(iso).replace(/\//g, ''));
  await expect(input).toHaveValue(isoToDisplay(iso));
}

/** The filedWithRE=yes branch demands a copy of the RE complaint before it will advance. */
async function attachReComplaintCopy(page: Page) {
  await page.locator('input#complaintFileWithRE').setInputFiles({
    name: 're-complaint.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 e2e'),
  });
  await expect(page.locator('.uploaded-file-name')).toContainText('re-complaint.pdf');
}

/**
 * Walks the file-complaint gate: entity -> filedWithRE=yes (+ date and copy) -> receivedReply answer.
 * Leaves the page wherever the product decided to go.
 */
async function walkReGate(page: Page, reComplaintDateIso: string, receivedReply: 'yes' | 'no') {
  await selectEntityAndAdvance(page);
  await answerCurrent(page, 'yes');
  await enterReComplaintDate(page, reComplaintDateIso);
  await attachReComplaintCopy(page);
  await page.locator('button.btn-next').click();

  await expect(page.locator('.question-text')).toContainText('reply', { timeout: 20000 });
  await answerCurrent(page, receivedReply);
}

/** POSTs the standalone wizard's check directly, so the SERVER's own answer can be asserted. */
async function wizardCheck(request: APIRequestContext, answers: Record<string, string>) {
  const res = await request.post(`${API_BASE}/api/v1/eligibility/wizard-check`, { data: answers });
  expect(res.status(), 'wizard-check should answer 200 even when refusing').toBe(200);
  return res.json();
}

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 1 — the citizen must approach the RE first (UST14 / UST18)
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('RE-first: the citizen cannot file without having complained to the entity', () => {

  test('the question is asked at all, on both surfaces', async ({ request }) => {
    // The gate cannot be enforced if it is never asked. Master-data driven, so this has to be checked
    // against the live master rather than the component.
    const body = await (await request.get(`${API_BASE}/api/v1/eligibility/questions`)).json();
    const byKey = new Map<string, any>((body.data as any[]).map(q => [q.questionKey, q]));

    const filed = byKey.get('filedWithRE');
    expect(filed, 'no ELIGIBILITY_QUESTION_MASTER row asks whether the RE was approached').toBeTruthy();
    expect(filed.blockOn, 'filedWithRE must block when the citizen answers no').toBe('no');
    expect(filed.nonMaintainable, 'the RE-first refusal must be a non-maintainability outcome').toBe(true);

    expect(byKey.get('receivedReply'),
      'no master row asks whether the RE replied, so the window cannot be assessed').toBeTruthy();
  });

  test('answering No blocks the filing with the master message and no way forward', async ({ page, request }) => {
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);
    await selectEntityAndAdvance(page);

    await answerCurrent(page, 'no');

    const note = page.locator('.block-note');
    await expect(note).toBeVisible();
    await expect(note, 'the block must come from the master row, not a string in the component')
      .toContainText('BLOCKED-filedWithRE');
    await expect(note, 'the raw placeholder must never reach a citizen').not.toContainText('{{clause}}');

    // The wizard removes the action row while blocked rather than disabling Next, so "cannot advance"
    // is asserted as "no actionable Next", which covers both renderings.
    const next = page.locator('button.btn-next');
    if (await next.count() > 0) await expect(next).toBeDisabled();
    await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
  });

  test('the block tells the citizen to approach the Regulated Entity in writing', async ({ page, request }) => {
    // QA's expected result is guidance, not merely a refusal: the citizen must learn WHAT to do next.
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [{ ...FILED_WITH_RE_ROW, blockMessageKey: 'eligibility.block_not_filed' },
                                  RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);
    await selectEntityAndAdvance(page);
    await answerCurrent(page, 'no');

    const note = page.locator('.block-note');
    await expect(note).toBeVisible();
    // eligibility.block_written_required is the "please approach the RE in writing first" line.
    const en = await (await request.get(`${API_BASE}/api/v1/i18n/translations/en`)).json();
    const t: Record<string, string> = en.data ?? en;
    const guidance = t['eligibility.block_written_required'];
    expect(guidance, 'no translated guidance line exists for the RE-first block').toBeTruthy();
    await expect(note).toContainText(guidance.replace(/\s+/g, ' ').trim().slice(0, 40));
  });

  test('leaving the RE question unanswered refuses to advance', async ({ page, request }) => {
    // UST14/UST18 scenario 3: no answer at all must be a mandatory-response failure, not a silent pass.
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);
    await selectEntityAndAdvance(page);

    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error')).toBeVisible();
    await expect(page.locator('.question-text')).not.toContainText(SENTINEL_TEXT);
  });

  test('the standalone wizard refuses with RE_FIRST and quotes the configured window', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'NO',
    });

    expect(r.outcome).toBe('RE_FIRST');
    expect(r.eligible).toBe(false);
    expect(r.reWindowDays, 'the wizard answer must carry the configured window, not a constant')
      .toBe(days);
    // The prose must quote the SAME number it enforces.
    expect(r.message, `RE_FIRST prose does not quote the configured ${days}-day window: ${r.message}`)
      .toContain(String(days));
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 2 — inside the window with no reply: wait, AND be told until when (UST11 S1/S3)
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Inside the RE window with no reply: the citizen waits and is told until when', () => {

  test('a complaint inside the window is refused and the window-open date is shown', async ({ page, request }) => {
    const days = await servedReWindowDays(request);
    const filedIso = daysAgo(Math.max(1, days - 5));
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, filedIso, 'no');

    // The component blocks as soon as receivedReply=no is selected.
    const note = page.locator('.block-note');
    await expect(note).toBeVisible({ timeout: 20000 });

    // The window opens `days` after the RE complaint — computed from the SERVED window.
    const opens = new Date(filedIso);
    opens.setHours(0, 0, 0, 0);
    opens.setDate(opens.getDate() + days);
    const opensDisplay = isoToDisplay(toLocalIso(opens));

    const notice = page.locator('.re-window-opens');
    await expect(notice, 'the citizen is told to wait but never told until WHEN').toBeVisible();
    await expect(notice).toContainText(opensDisplay);
    await expect(notice, 'the raw placeholder must never reach a citizen').not.toContainText('{{date}}');
  });

  test('the refusal prose quotes the served window, not a number compiled into the bundle', async ({ page }) => {
    // 47 is deliberately not 30 and appears nowhere in the source. It can only be rendered if the
    // component interpolated the SERVED reWindowDays.
    const SERVED = 47;
    const filedIso = daysAgo(5);
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, filedIso, 'no');

    const reason = page.locator('.block-note, .nm-reason').first();
    await expect(reason).toBeVisible({ timeout: 20000 });
    const text = await reason.innerText();
    expect(text, `the block prose ignores the served window: ${text}`).toContain(String(SERVED));
    expect(text, 'the block prose still quotes a hardcoded 30-day window').not.toMatch(/\b30 days\b/);
    expect(text, 'the raw placeholder reached the citizen').not.toContain('{{days}}');
  });

  test('the placeholder is filled every time it occurs, not only the first', async ({ page }) => {
    // The prose names the window TWICE ("not yet been given N days ... wait until N days have
    // elapsed"). TranslationService used String.replace with a string pattern, which substitutes only
    // the first occurrence, so the second placeholder leaked to the citizen.
    const SERVED = 47;
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(5), 'no');

    const reason = page.locator('.block-note, .nm-reason').first();
    await expect(reason).toBeVisible({ timeout: 20000 });
    const text = await reason.innerText();
    expect(text).not.toContain('{{days}}');
    expect((text.match(/\b47\b/g) || []).length,
      'the window is quoted twice in the prose; both occurrences must be filled')
      .toBeGreaterThanOrEqual(2);
  });

  test('the window-open date is cleared when the citizen corrects the answer', async ({ page, request }) => {
    // A stale "come back on 24/10" left on screen after the citizen says the RE DID reply would be a
    // refusal notice attached to a complaint that is no longer refused.
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(Math.max(1, days - 5)), 'no');
    await expect(page.locator('.re-window-opens')).toBeVisible({ timeout: 20000 });

    await answerCurrent(page, 'yes');
    await expect(page.locator('.re-window-opens')).toHaveCount(0);
    await expect(page.locator('.block-note')).toHaveCount(0);
  });

  test('the closure letter the citizen downloads carries the window-open date', async ({ page }) => {
    // The PDF is the citizen's only durable record of the refusal, so it must say when to come back.
    // Asserted on the generated PDF bytes rather than the DOM.
    const SERVED = 47;
    const filedIso = daysAgo(5);
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, filedIso, 'no');
    // Blocked inline; Next moves to the non-maintainable card that offers the letter.
    await expect(page.locator('.block-note')).toBeVisible({ timeout: 20000 });
    const showLetter = page.locator('button.btn-show-closure');
    if (await showLetter.count() > 0) await showLetter.click();
    await expect(page.locator('.nm-card')).toBeVisible({ timeout: 20000 });

    const opens = new Date(filedIso);
    opens.setHours(0, 0, 0, 0);
    opens.setDate(opens.getDate() + SERVED);
    const opensDisplay = isoToDisplay(toLocalIso(opens));

    await expect(page.locator('.nm-reason .re-window-opens')).toContainText(opensDisplay);

    const download = page.waitForEvent('download', { timeout: 30000 });
    await page.locator('.nm-actions button.btn-primary').click();
    const file = await download;
    expect(file.suggestedFilename()).toMatch(/^Closure_Letter_NM-\d+\.pdf$/);
  });

  test('the standalone wizard returns the window-open date and the days remaining', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const inside = Math.max(1, days - 3);
    const filedIso = daysAgo(inside);

    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: filedIso, reRespondedSatisfactorily: 'NO_REPLY',
    });

    expect(r.outcome).toBe('TOO_EARLY');
    expect(r.eligible).toBe(false);
    const opens = new Date(filedIso);
    opens.setDate(opens.getDate() + days);
    expect(r.windowOpenDate, 'the citizen is told to wait but not until when')
      .toBe(toLocalIso(opens));
    expect(r.daysRemaining).toBe(days - inside);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 3 — a reply, satisfactory or not, waives the wait (UST11 S2)
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('A reply from the RE waives the wait', () => {

  test('an unsatisfactory reply inside the window lets the citizen proceed', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(Math.max(1, days - 5)),
      reRespondedSatisfactorily: 'DISSATISFIED',
    });

    expect(r.outcome, 'a dissatisfied citizen with a reply must not be made to wait out the window')
      .toBe('READY');
    expect(r.eligible).toBe(true);
  });

  test('a resolved reply inside the window is advisory, not a bar to filing', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(Math.max(1, days - 5)),
      reRespondedSatisfactorily: 'RESOLVED',
    });

    // RANT_GATE is advisory: eligible is false but the wizard still offers "Proceed to File".
    expect(r.outcome).toBe('RANT_GATE');
    expect(r.outcome, 'a reply must never produce the TOO_EARLY wait').not.toBe('TOO_EARLY');
  });

  test('answering Yes to a reply inside the window raises no wait block in the filing wizard', async ({ page, request }) => {
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(Math.max(1, days - 5)), 'yes');

    await expect(page.locator('.block-note'),
      'a citizen who received a reply was still told to wait out the RE window').toHaveCount(0);
    await expect(page.locator('.re-window-opens')).toHaveCount(0);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 4 — the boundary is INCLUSIVE of day reWindowDays
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The RE window boundary is inclusive: at exactly reWindowDays the window is open', () => {

  test('wizard-check: one day short refuses, exactly the window is READY', async ({ request }) => {
    const days = await servedReWindowDays(request);

    const shortOfIt = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(days - 1), reRespondedSatisfactorily: 'NO_REPLY',
    });
    expect(shortOfIt.outcome, `day ${days - 1} must still be inside the window`).toBe('TOO_EARLY');
    expect(shortOfIt.daysRemaining).toBe(1);

    const exactly = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(days), reRespondedSatisfactorily: 'NO_REPLY',
    });
    expect(exactly.outcome,
      `day ${days} must be OPEN — the window is inclusive, and an exclusive boundary would hold the ` +
      `citizen an extra day`).toBe('READY');
    expect(exactly.eligible).toBe(true);

    const past = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(days + 1), reRespondedSatisfactorily: 'NO_REPLY',
    });
    expect(past.outcome).toBe('READY');
  });

  test('wizard-check: a complaint filed today is refused for the whole window', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(0), reRespondedSatisfactorily: 'NO_REPLY',
    });
    expect(r.outcome).toBe('TOO_EARLY');
    expect(r.daysRemaining, 'day 0 must owe the full window').toBe(days);
  });

  test('filing wizard: one day short of the window blocks', async ({ page, request }) => {
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(days - 1), 'no');
    await expect(page.locator('.block-note'), `day ${days - 1} should still be inside the window`)
      .toBeVisible({ timeout: 20000 });
  });

  test('filing wizard: exactly the window does NOT block', async ({ page, request }) => {
    // The same boundary on the surface that actually stops a filing. The component's guard is
    // `daysSince >= reWindowDays`, and its comment records that an earlier `<= 30` closed complaints a
    // day early — this is the regression guard for that fix.
    const days = await servedReWindowDays(request);
    await stubMaster(page, days, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(days), 'no');
    await expect(page.locator('.block-note'),
      `day ${days} must be OPEN on the filing surface too, or the two surfaces disagree`)
      .toHaveCount(0);
    await expect(page.locator('.re-window-opens')).toHaveCount(0);
  });

  test('filing wizard: the boundary moves with the configured window, not with 30', async ({ page }) => {
    // The decisive test for QA item 6. With a served window of 47, day 30 must STILL block: if the
    // component were enforcing a compiled-in 30 it would let this through.
    const SERVED = 47;
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(30), 'no');
    await expect(page.locator('.block-note'),
      'day 30 was allowed through while the configured window is 47, so the window is hardcoded')
      .toBeVisible({ timeout: 20000 });
  });

  test('filing wizard: a shorter configured window opens the gate earlier', async ({ page }) => {
    // The other direction, so the test above cannot pass merely by blocking everything. With a served
    // window of 7, day 10 must NOT block.
    const SERVED = 7;
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    await walkReGate(page, daysAgo(10), 'no');
    await expect(page.locator('.block-note'),
      'day 10 was blocked while the configured window is 7, so the window is hardcoded')
      .toHaveCount(0);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 6 — the window is configuration end to end
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The RE window is configuration, not a compiled-in constant', () => {

  test('the window is served to the portal', async ({ request }) => {
    const days = await servedReWindowDays(request);
    expect(days, 'reWindowDays must be a plausible day count').toBeLessThan(400);
  });

  test('no locale bakes the window into the block prose', async ({ request }) => {
    // The regression guard for V102 / oracle V100. Bengali is included because it stores the digits in
    // Bengali numerals (৩০), which an ASCII check would silently skip.
    const BAKED = [/\b30\b/, /৩০/];

    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;

      for (const key of ['eligibility.block_less_than_30_days',
                         'wizard.timeline_step2', 'wizard.timeline_step2_desc']) {
        const value = t[key];
        if (!value || value === key) continue;
        for (const pattern of BAKED) {
          expect(pattern.test(value),
            `${key} for ${locale} still bakes the window into the prose: ${value}`).toBe(false);
        }
      }
    }
  });

  test('the window placeholder survives translation in every locale', async ({ request }) => {
    // Removing the digits is only half the fix: a locale that lost the placeholder too would silently
    // stop telling the citizen how long the wait is.
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      const value = t['eligibility.block_less_than_30_days'];
      expect(value, `block_less_than_30_days missing for ${locale}`).toBeTruthy();
      expect(value, `${locale} lost its {{days}} placeholder`).toContain('{{days}}');
    }
  });

  test('the wizard timeline quotes the served window', async ({ page }) => {
    // The standalone wizard's "Wait Period (N days)" is the same rule on a different surface.
    const SERVED = 47;
    await page.route('**/api/v1/eligibility/questions*', route =>
      route.fulfill({
        status: 200, contentType: 'application/json',
        body: JSON.stringify({ success: true, schemeVersion: 'RBIOS_2021', reWindowDays: SERVED, data: [] }),
      }));
    await page.goto(`${APP_BASE}/public/eligibility-wizard`);
    await expect(page.locator('.wizard-card')).toBeVisible({ timeout: 20000 });

    // Drive to a READY result so the timeline renders.
    await page.locator('select.wizard-select').selectOption('BANK');
    await page.locator('button.btn-wizard-next').click();
    await page.locator('.radio-group .wizard-radio').first().click();
    await page.locator('button.btn-wizard-next').click();
    await page.locator('input.wizard-date-input').fill(daysAgo(120));
    await page.locator('button.btn-wizard-next').click();
    await page.locator('.radio-group .wizard-radio').first().click();
    await page.locator('button.btn-wizard-next').click();

    const timeline = page.locator('.timeline-section');
    await expect(timeline).toBeVisible({ timeout: 20000 });
    const text = await timeline.innerText();
    expect(text, `the timeline ignores the served window: ${text}`).toContain(String(SERVED));
    expect(text, 'the timeline still promises a hardcoded 30-day wait').not.toMatch(/\b30 days\b/);
    expect(text, 'the raw placeholder reached the citizen').not.toContain('{{days}}');
  });

  test('the TOO_EARLY prose quotes the window the server enforced', async ({ request }) => {
    const days = await servedReWindowDays(request);
    const r = await wizardCheck(request, {
      entityType: 'BANK', complainedToRE: 'YES',
      reComplaintDate: daysAgo(Math.max(1, days - 3)), reRespondedSatisfactorily: 'NO_REPLY',
    });
    expect(r.message, `TOO_EARLY prose does not quote the configured ${days}-day window: ${r.message}`)
      .toContain(String(days));
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// Fail-closed: an unreachable eligibility service must not produce an invented verdict
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('An unreachable eligibility check is reported, not replaced by a local guess', () => {

  test('the standalone wizard says the check is unavailable instead of issuing a verdict', async ({ page }) => {
    // The wizard used to fall back to a computeLocalOutcome() that re-implemented the determination
    // with the window compiled into the bundle, so a 500 produced a verdict — including "Yes, RBI can
    // help!" — that no server ever issued, with nothing on screen saying the check had failed.
    await page.route('**/api/v1/eligibility/wizard-check', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"down"}' }));

    await page.goto(`${APP_BASE}/public/eligibility-wizard`);
    await expect(page.locator('.wizard-card')).toBeVisible({ timeout: 20000 });

    await page.locator('select.wizard-select').selectOption('BANK');
    await page.locator('button.btn-wizard-next').click();
    await page.locator('.radio-group .wizard-radio').first().click();
    await page.locator('button.btn-wizard-next').click();
    await page.locator('input.wizard-date-input').fill(daysAgo(5));
    await page.locator('button.btn-wizard-next').click();
    await page.locator('.radio-group .wizard-radio').first().click();
    await page.locator('button.btn-wizard-next').click();

    await expect(page.locator('.unavailable-notice')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('.outcome-title'),
      'a verdict was rendered from a failed check').toHaveCount(0);
    // Advisory only: the citizen must still be able to go and file.
    await expect(page.locator('.unavailable-card button.btn-wizard-secondary')).toBeVisible();
  });

  test('the filing wizard refuses to guess a maintainability rule it could not load', async ({ page }) => {
    // D14: with no question master there is no question to answer, and advancing would mean inventing
    // a rule. Pinned here as well as in eligibility-master.spec.ts because the RE gate is the rule a
    // wrong guess would silently drop.
    await page.route('**/api/v1/eligibility/questions*', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"down"}' }));

    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, '9876500088', 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.questions-load-error')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('button.btn-next')).toBeDisabled();
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════
// QA 5 — Appendix 8 "auto-closure": pinning what the product ACTUALLY does
// ══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The RE-window refusal is a client-side determination, not a persisted auto-closure', () => {

  test('no endpoint accepts a non-maintainable closure, so nothing is persisted', async ({ request }) => {
    // QA UST14 S2 expects the complaint to be "restricted / auto-closed under Appendix 8". No such
    // path exists: these are the endpoints a closure would have to go through, and all of them are
    // absent. This test is the honest record of that gap — if a closure API is ever built, it fails
    // and this file must be revisited.
    for (const path of ['/api/v1/eligibility/non-maintainable',
                        '/api/v1/eligibility/closure',
                        '/api/v1/eligibility/decision']) {
      const res = await request.post(`${API_BASE}${path}`, {
        data: { questionKey: 'receivedReply', outcome: 'NON_MAINTAINABLE' },
        failOnStatusCode: false,
      });
      expect([404, 405],
        `${path} now answers ${res.status()} — a closure persistence path appears to exist, so the ` +
        `"no auto-closure" finding in this file is stale and must be re-assessed`)
        .toContain(res.status());
    }
  });

  test('the auto-closure service evaluates but never closes', async ({ request }) => {
    // AutoClosureController exposes /questions, /evaluate and /evaluate-all only. An evaluator is not
    // an auto-closure: nothing transitions a complaint, and no citizen is notified.
    const res = await request.get(`${API_BASE}/api/v1/crpc/auto-closure/questions`,
      { failOnStatusCode: false });
    expect([200, 401, 403], 'the auto-closure evaluator is unreachable').toContain(res.status());

    const close = await request.post(`${API_BASE}/api/v1/crpc/auto-closure/close`,
      { data: {}, failOnStatusCode: false });
    expect([404, 405],
      'a /close endpoint appears to exist now, so auto-closure may be implemented — re-assess')
      .toContain(close.status());
  });

  test('the case id on the closure screen is generated client-side, not issued by the server', async ({ page }) => {
    // The NM-######## id is `Date.now()` sliced in the browser. It looks like a reference number a
    // citizen could quote, but nothing can be looked up by it. Pinned so the presentation is not
    // mistaken for a record.
    const SERVED = 47;
    await stubMaster(page, SERVED, [FILED_WITH_RE_ROW, RECEIVED_REPLY_ROW]);
    await openFileComplaint(page);

    const postedPaths: string[] = [];
    await page.route('**/api/**', async route => {
      if (route.request().method() === 'POST') postedPaths.push(new URL(route.request().url()).pathname);
      return route.fallback();
    });

    await walkReGate(page, daysAgo(5), 'no');
    await expect(page.locator('.block-note')).toBeVisible({ timeout: 20000 });
    const showLetter = page.locator('button.btn-show-closure');
    if (await showLetter.count() > 0) await showLetter.click();
    await expect(page.locator('.nm-card')).toBeVisible({ timeout: 20000 });

    const caseId = (await page.locator('.nm-value').first().innerText()).trim();
    expect(caseId, 'the closure screen shows no case id at all').toMatch(/^NM-\d{8}$/);

    // Nothing was posted to record the determination. Drafts are excluded: the autosave is a draft of
    // the citizen's own input, not a closure record.
    const closurePosts = postedPaths.filter(p => !p.includes('/drafts') && !p.includes('/i18n'));
    expect(closurePosts,
      `the refusal posted ${JSON.stringify(closurePosts)} — if a closure is now recorded server-side, ` +
      `the "no auto-closure path exists" finding is stale`).toEqual([]);
  });
});
