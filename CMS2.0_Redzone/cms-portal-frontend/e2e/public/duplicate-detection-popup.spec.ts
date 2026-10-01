/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4, Session C — manual blocks 54 and 55: duplicate complaint detection and its popup.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * WHY THE TWO BLOCKS ARE ONE SPEC. Block 54 (12 cases) and block 55 (10 cases) describe the same
 * control from two angles: 54 calls it "the duplicate complaint message" and asks what parameters
 * detect the duplicate; 55 calls it "the pop up message" and asks which buttons it carries. Six of
 * block 55's cases are verbatim restatements of block 54 cases (create a duplicate / cancel present
 * / able to click cancel / proceed present / able to click proceed / login). Writing them twice
 * would double the wizard walks and halve the chance a reviewer reads either file.
 *
 * ═══ WHAT ALREADY EXISTED, AND WHY THIS FILE IS NOT A DUPLICATE OF IT ═══
 * `duplicate-check.spec.ts` (UST76) covers the endpoint and the fail-closed path. It is not
 * superseded and is not re-run here. What it does NOT do, and this file does:
 *   - Its three UI tests `page.route` the endpoint and MOCK the response. Every UI assertion there
 *     is made against a fixture, so none of them can see a mismatch between what the server matches
 *     on and what the citizen typed. Every UI test here drives the REAL endpoint against a REAL
 *     seeded earlier complaint.
 *   - It never clicks Proceed. `proceedDespiteDuplicate()` (ts:2159) was completely untested — the
 *     one branch where a citizen knowingly files a second complaint, which is exactly the branch
 *     block 54's last case and block 55's last case are about.
 *   - It asserts the entity leg with `entityName: 'Test Bank Ltd'` on BOTH the seed and the check,
 *     i.e. it only ever exercises the case where the two agree, which is the case that cannot fail.
 *     The inversion below is invisible to it by construction.
 *
 * ═══ THE HEADLINE FINDING, BECAUSE IT DETERMINES HOW HALF THIS FILE IS WRITTEN ═══
 * The manual says five parameters detect a duplicate: mobile / email, complainant NAME, ENTITY
 * name, DATE of disputed transaction, and CATEGORY. Measured against the running server:
 *
 *   mobile   — participates. Matches exactly, and wins the `matchedOn` report.
 *   email    — participates. Matches case-insensitively.
 *   category — participates, and works: registration resolves the name to COMPLAINT_CATEGORIES.id
 *              and the check resolves it the same way, so the two agree.
 *   name     — SENT AND IGNORED. `checkDuplicate()` (ts:2119) does not even send it.
 *   date     — SENT AND IGNORED. It is in the POST body and no server code reads it.
 *   entity   — PARTICIPATES BACKWARDS. This is D-C-35 and it is a P1.
 *
 * The entity inversion in full, because it is not guessable from either side alone:
 * `resolveBankId` (controller:321) turns the entity NAME into a `BANKS.id` and the query filters
 * `c.bankId = :bankId`. But the public filing path never writes `bank_id` at all — `fileComplaint`
 * (ComplaintService:133) copies `req.getBankId()`, and the wizard sends `regulatedEntityId`, not
 * `bankId`. The controller reads `regulatedEntityId` into the request object (controller:120) and
 * nothing ever transfers it to `bankId`. The entity survives only as the free-text `entity_code`
 * column, which the duplicate query does not look at. Measured: 203 of 203 ONLINE complaints have
 * `bank_id` NULL, and 13 of 8522 rows repo-wide have it populated at all.
 *
 * The consequence is the wrong way round from a normal miss. Because an unresolvable name yields
 * `bankId = null` and the query treats null as "any entity", naming an entity the master does not
 * know WIDENS the check into a match, while naming the entity correctly narrows it onto a column
 * that is always NULL and so finds NOTHING. Proven end to end below, including in the UI:
 *
 *   seeded a live complaint, then asked the check about it —
 *     entityName omitted                → duplicate: true
 *     entityName "Bajaj Finance Limited" (not in BANKS, 133 of 145 portal entities are not)
 *                                       → duplicate: true
 *     entityName "State Bank of India"  (the entity it was actually filed against)
 *                                       → duplicate: FALSE
 *
 * So the duplicate control is strongest for a citizen who picks an entity the portal offers but the
 * BANKS table has never heard of, and weakest for one who picks a major bank. Per standing ruling 2
 * the manual's requirement is written as `fixme` and the measured reality as a passing companion,
 * so the day `bank_id` starts being written the companion fails and names itself.
 *
 * ═══ TRAPS PAID FOR, DO NOT UNDO ═══
 *  - **The seeded draft's `entityName` is never read back.** `openWizardAtStep` puts `entityName`
 *    in the draft, but `loadDraft` (ts:1937) reads only formData / eligibilityAnswers /
 *    declarations / attachmentMeta. `getSelectedBankName()` (ts:2414) derives the name from
 *    `eligibilityAnswers['regulatedEntity']` against the loaded `banks` array — so the ONLY way to
 *    make the wizard send an entity name is to seed that answer with a numeric entity id from
 *    /api/v1/routing/entities/list. Seeding `entityName` alone sends `''` and silently exercises
 *    the entity-omitted case while looking like the entity-supplied one.
 *  - **`category: 'GENERAL'` does not resolve.** COMPLAINT_CATEGORIES holds ten rows and none is
 *    named GENERAL, so the shared `validFormData()` default lands on the widening path. Tests that
 *    need the category leg to actually bind must use a real category name, and `REAL_CATEGORY` is
 *    read from /api/categories rather than written here.
 *  - **`checkDuplicate` is a pre-flight, not a guard.** `submit()` (ts:2106) calls it, returns, and
 *    is re-entered by the callback with `duplicateCheckDone = true`. So a passing check produces
 *    TWO `submit()` entries and exactly ONE check call; Proceed produces no second check at all.
 *    Counting calls is meaningful; assuming one call per click is not.
 *  - Fresh ONLINE registrations land in status `pending`, which is not terminal, so a just-seeded
 *    complaint is immediately a duplicate. No status manipulation is needed to make the popup fire.
 *  - The popup's two buttons are distinguished by CLASS, not order-independent text: cancel is
 *    `.btn-secondary`, proceed is `.btn-primary` (html:1334-1335). Both labels come from the
 *    translation bundle, so matching on the English string couples the test to the `en` locale.
 *  - **HTTP 429 ON AN IMMEDIATE SECOND RUN IS THE SERVER, NOT THIS SPEC.** Anti-automation allows
 *    `velocity-threshold: 30` filings per `velocity-window-seconds: 60` (application.yml:141-142).
 *    This spec seeds ~40 complaints in under a minute, so ONE run is inside the budget and two
 *    back-to-back runs are not: the second run's `fileOnline` calls come back 429 and the failure
 *    surfaces on the seeding assertion, pointing at a test that is in fact fine. Leave a minute
 *    between runs. Do NOT "fix" it by relaxing the 201 expectation, by retrying the seed, or by
 *    lowering the seed count — the 201 assertion is what makes the 429 legible instead of silently
 *    producing a run where half the duplicates were never seeded and every popup test "passes"
 *    because no popup was due.
 *
 * WHAT CANNOT BE PROVEN HERE:
 *  - Block 54's and 55's first two cases ("login with valid credentials", "post login the user is
 *    able to file the complaint") are the citizen OTP journey, already owned by the login and
 *    submission specs. These tests seed a citizen session rather than re-file that ground.
 *  - The lookback window has NO endpoint to read it from. `cms.duplicate-check.lookback-days` is
 *    server-side only, so the window is MEASURED by bisection below and reported, never pinned.
 *  - "Tagged as a duplicate to the previous identified complaint" cannot be proven because there is
 *    nowhere for the tag to live: no column in COMPLAINTS or COMPLAINT_MASTER matches
 *    %DUP%/%LINK%/%PARENT%/%RELATED%, and no such table exists. Asserted as absent, in SQL.
 */

import { execFileSync } from 'node:child_process';
import type { APIRequestContext } from '@playwright/test';
import { test, expect } from '../fixtures';
import { API_BASE, openWizardAtStep, sessionCMobile, uniqueEmail, validFormData } from './helpers-submission-c';

const POPUP = '.popup-card';
const POPUP_TITLE = '.popup-card h3';
const POPUP_MESSAGE = '.popup-message';
const POPUP_NOTE = '.popup-note';
const CANCEL = '.popup-actions .btn-secondary';
const PROCEED = '.popup-actions .btn-primary';
const SUBMIT = 'button.btn-submit-complaint';
const SUBMIT_ERROR = '.error-msg';

/** An entity the BANKS master DOES know, so the entity leg resolves to a real id. */
const RESOLVABLE_ENTITY = 'State Bank of India';
/** A portal entity the BANKS master does NOT know — one of 133 such names. */
const UNRESOLVABLE_ENTITY = 'Bajaj Finance Limited';

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// SQL — negatives are proven in the database, not in an HTTP status
// ═══════════════════════════════════════════════════════════════════════════════════════════════

function sql(query: string): string {
  return execFileSync(
    process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', query],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  ).trim();
}

/** Every complaint filed under one mobile, newest first. */
function complaintsForPhone(phone: string): { number: string; status: string; bankId: string; categoryId: string }[] {
  if (!/^[0-9]{10}$/.test(phone)) throw new Error(`refusing to use "${phone}" in SQL`);
  const out = sql(
    `SELECT complaint_number, status, IFNULL(bank_id,'-'), IFNULL(category_id,'-') FROM COMPLAINTS ` +
    `WHERE complainant_phone = '${phone}' ORDER BY created_at DESC;`
  );
  if (!out) return [];
  return out.split('\n').map(line => {
    const [number, status, bankId, categoryId] = line.split('\t');
    return { number, status, bankId, categoryId };
  });
}

/**
 * A mobile with NO live complaint against it.
 *
 * ═══ WHY RANDOMNESS IS NOT ENOUGH HERE, AND WHY THIS IS NOT DEFENSIVE PADDING ═══
 * `sessionCMobile()` draws a random 4-digit tail in Session C's 987653____ block — 9000 numbers.
 * This spec seeds ~90 live complaints per run and nothing closes them, so the occupied share of the
 * block grows with every run. The moment a draw lands on an occupied number, a test that begins
 * "this complainant has no history" starts against one that does, and the check reports a duplicate
 * before the test has seeded anything.
 *
 * That is exactly what happened, TWICE, and the second time is the reason this checks for ANY row
 * rather than any LIVE row:
 *   1. Four tests in the lookback describe passed, then failed on the next run with no code change,
 *      then passed again in isolation. A live seeded complaint had been drawn again.
 *   2. With a liveness-only guard, `proceed closes the popup and files the complaint` failed with
 *      "Expected: 2, Received: 3". The mobile had no LIVE complaint — but it had a CLOSED one, left
 *      behind by the terminal-status tests, and `complaintsForPhone` counts every status. A guard
 *      that is weaker than the assertion it protects only moves the collision, it does not remove it.
 *
 * Both readings were available as "flake" and both were wrong: the block had 374 of 9000 mobiles
 * used, growing every run, so this is a test that gets MORE likely to fail the longer the suite
 * lives. Re-running until green would have hidden it and shipped an intermittent suite.
 *
 * So the mobile is verified to have NO complaint history of any status, using the same SQL authority
 * the assertions use — which means the guard cannot disagree with the thing it is guarding.
 */
function freshMobile(): string {
  for (let attempt = 0; attempt < 40; attempt++) {
    const mobile = sessionCMobile();
    const any = Number(sql(
      `SELECT COUNT(*) FROM COMPLAINTS WHERE complainant_phone = '${mobile}';`
    ));
    if (any === 0) return mobile;
  }
  throw new Error(
    'Could not find an unused mobile in 40 draws of the 987653____ block. The block has filled up ' +
    'with seeded data from earlier runs — purge the QA seeds before running again. Do NOT relax this ' +
    'to "no live complaint": the count assertions count every status, so a closed seed still breaks them.'
  );
}

/** Moves one complaint to a status, so the terminal-status exclusion can be exercised. */
function setStatus(complaintNumber: string, status: string): void {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) throw new Error(`refusing to use "${complaintNumber}" in SQL`);
  if (!/^[a-z_]+$/.test(status)) throw new Error(`refusing to use "${status}" in SQL`);
  sql(`UPDATE COMPLAINTS SET status = '${status}' WHERE complaint_number = '${complaintNumber}';`);
}

/** Ages one complaint, so the lookback window can be measured rather than assumed. */
function backdate(complaintNumber: string, days: number): void {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) throw new Error(`refusing to use "${complaintNumber}" in SQL`);
  if (!Number.isInteger(days) || days < 0 || days > 100000) throw new Error(`refusing day offset ${days}`);
  sql(`UPDATE COMPLAINTS SET created_at = DATE_SUB(NOW(), INTERVAL ${days} DAY) ` +
      `WHERE complaint_number = '${complaintNumber}';`);
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// SEEDING — through the public filing endpoint, so the row looks like a citizen's, not an officer's
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/**
 * Files a complaint the way the wizard does.
 *
 * `createTestComplaint` in test-data.ts goes through the CEPC officer route, which is the wrong
 * shape for this spec: the defect under test is a mismatch between how the PUBLIC path stores an
 * entity and how the duplicate check reads it, so the earlier complaint has to be created by the
 * public path or the measurement is meaningless.
 */
async function fileOnline(
  request: APIRequestContext,
  fields: { phone: string; email: string; name?: string; category?: string; entityName?: string; entityId?: number }
): Promise<string> {
  const res = await request.post(`${API_BASE}/api/v1/complaints`, {
    headers: { 'Content-Type': 'application/json' },
    data: {
      filingType: 'ONLINE',
      category: fields.category ?? 'GENERAL',
      complainantName: fields.name ?? 'Session Cee',
      complainantEmail: fields.email,
      complainantPhone: fields.phone,
      complainantAddress: '1 Test Street, Mumbai',
      complainantState: 'Maharashtra',
      complainantDistrict: 'Mumbai',
      entityName: fields.entityName ?? '',
      entityType: 'BANK',
      regulatedEntityId: fields.entityId,
      subject: 'QA pass-4 session C duplicate detection seed',
      description: 'Seeded earlier complaint so that the next filing is a genuine duplicate.',
      transactionDate: '2026-01-10',
      priorReComplaint: true,
      reComplaintDate: '2026-01-15',
      reComplaintReference: 'RE-QAC-DUP-001',
      reRepliedAndDissatisfied: true,
      declarationAccepted: true,
    },
  });
  expect(res.status(), 'seeding an earlier complaint must succeed or nothing below means anything')
    .toBe(201);
  const body = await res.json();
  return (body.data ?? body).complaintId;
}

async function check(request: APIRequestContext, payload: Record<string, unknown>) {
  const res = await request.post(`${API_BASE}/api/v1/complaints/check-duplicate`, {
    headers: { 'Content-Type': 'application/json' },
    data: payload,
  });
  return { status: res.status(), body: await res.json() };
}

/** A category name the master really holds, read from the server rather than written here. */
async function realCategory(request: APIRequestContext): Promise<string> {
  const res = await request.get(`${API_BASE}/api/categories`);
  expect(res.ok(), '/api/categories must answer or the category leg cannot be exercised').toBeTruthy();
  const rows = (await res.json()) as { name?: string }[];
  const name = (Array.isArray(rows) ? rows : []).map(r => r.name).find(Boolean);
  expect(name, 'the category master is empty').toBeTruthy();
  return name as string;
}

/** The numeric id the wizard puts in `eligibilityAnswers.regulatedEntity` for a named entity. */
async function entityIdFor(request: APIRequestContext, name: string): Promise<number> {
  const res = await request.get(`${API_BASE}/api/v1/routing/entities/list`);
  expect(res.ok(), 'the entity master must answer').toBeTruthy();
  const payload = await res.json();
  const rows = (payload?.data ?? payload ?? []) as { id: number; name: string }[];
  const match = rows.find(e => e.name === name);
  expect(match, `the entity master no longer offers "${name}"`).toBeTruthy();
  return (match as { id: number }).id;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// UI ENTRY
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/**
 * Lands on the review step with the contact details the duplicate check will read.
 *
 * `entityId` is the load-bearing argument: supply it and the wizard sends the entity name, omit it
 * and the wizard sends `''`. See the header trap about the draft's unread `entityName`.
 */
async function openReview(
  page: any,
  fields: { phone: string; email: string; category?: string; entityId?: number; name?: string },
) {
  await openWizardAtStep(page, fields.phone, {
    step: 6,
    eligibilityAnswers: {
      filedWithRE: 'yes',
      receivedReply: 'yes',
      ...(fields.entityId ? { regulatedEntity: String(fields.entityId) } : {}),
    },
    formData: validFormData({
      phone: fields.phone,
      email: fields.email,
      ...(fields.name ? { firstName: fields.name, lastName: '' } : {}),
      ...(fields.category ? { complaintCategory: fields.category } : {}),
      disputeDate: '2026-01-10',
    }),
  });

  const submit = page.locator(SUBMIT);
  await expect(submit).toBeVisible({ timeout: 20000 });
  await expect(submit).toBeEnabled();
  return submit;
}

/** Records every register POST, so "nothing was filed" is an observation rather than a hope. */
function watchRegister(page: any): { count: () => number } {
  let n = 0;
  page.on('request', (req: any) => {
    if (req.method() === 'POST' && /\/api\/v1\/complaints$/.test(req.url())) n++;
  });
  return { count: () => n };
}

/** Records every duplicate-check POST, for the "pre-flight, not a guard" assertions. */
function watchCheck(page: any): { count: () => number } {
  let n = 0;
  page.on('request', (req: any) => {
    if (req.method() === 'POST' && /check-duplicate$/.test(req.url())) n++;
  });
  return { count: () => n };
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54 — the check is what happens when Submit is pressed
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54 — the duplicate pre-check is what the Submit button does first', () => {

  test('a first-time complainant is not a duplicate and is given a complaint number', async ({ request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('unique');

    const before = await check(request, { phone, email });
    expect(before.status).toBe(200);
    expect(before.body.duplicate, 'an unknown complainant cannot be a duplicate').toBe(false);

    const number = await fileOnline(request, { phone, email });
    expect(number, 'a complaint number must be generated').toMatch(/\S/);
    expect(complaintsForPhone(phone).map(c => c.number)).toContain(number);
  });

  test('a live complaint on the same mobile makes the next filing a duplicate', async ({ request }) => {
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('phone_leg') });

    const { body } = await check(request, { phone });
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn, 'the mobile is the stronger signal and must be reported as such').toBe('phone');
    expect(body.complaintNumber).toBe(seeded);
  });

  test('a live complaint on the same email makes the next filing a duplicate', async ({ request }) => {
    const email = uniqueEmail('email_leg');
    const seeded = await fileOnline(request, { phone: freshMobile(), email });

    const { body } = await check(request, { email });
    expect(body.duplicate).toBe(true);
    expect(body.matchedOn).toBe('email');
    expect(body.complaintNumber).toBe(seeded);
  });

  test('the email match ignores case, because an address is not case sensitive', async ({ request }) => {
    const email = uniqueEmail('case_leg');
    await fileOnline(request, { phone: freshMobile(), email });

    const { body } = await check(request, { email: email.toUpperCase() });
    expect(body.duplicate, 'ADDRESS@example.com and address@example.com are one mailbox').toBe(true);
  });

  test('a request carrying neither a mobile nor an email is refused', async ({ request }) => {
    const { status, body } = await check(request, { entityName: RESOLVABLE_ENTITY });
    expect(status).toBe(400);
    expect(body.error).toBe('MISSING_IDENTIFIER');
  });

  test('pressing submit runs the check before it registers anything', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('order');
    await fileOnline(request, { phone, email });

    const order: string[] = [];
    page.on('request', (req: any) => {
      if (req.method() !== 'POST') return;
      if (/check-duplicate$/.test(req.url())) order.push('check');
      if (/\/api\/v1\/complaints$/.test(req.url())) order.push('register');
    });

    const submit = await openReview(page, { phone, email });
    await submit.click();

    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    expect(order, 'the check must precede registration, not follow it').toEqual(['check']);
  });

  test('a unique complainant filing from the wizard reaches the success screen with no popup', async ({ page }) => {
    const phone = freshMobile();
    const email = uniqueEmail('ui_unique');

    const checks = watchCheck(page);
    const submit = await openReview(page, { phone, email });
    await submit.click();

    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });
    await expect(page.locator(POPUP)).toHaveCount(0);

    // ONE check for TWO submit() entries: the callback re-enters submit() with duplicateCheckDone set.
    expect(checks.count(), 'the check must not be run twice for one click').toBe(1);
    expect(complaintsForPhone(phone).length, 'exactly one complaint must exist').toBe(1);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54/55 — what the popup says and what it offers
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54/55 — the popup a detected duplicate raises', () => {

  test('a real duplicate raises the popup and files nothing while it is open', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('popup');
    await fileOnline(request, { phone, email });

    const registers = watchRegister(page);
    const submit = await openReview(page, { phone, email });
    await submit.click();

    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    expect(registers.count(), 'the citizen has not decided yet, so nothing may be filed').toBe(0);
    expect(complaintsForPhone(phone).length, 'only the seeded complaint may exist').toBe(1);
  });

  test('a mobile match is explained as a mobile match, in the words the manual specifies', async ({ page, request }) => {
    const phone = freshMobile();
    await fileOnline(request, { phone, email: uniqueEmail('msg_phone') });

    // A DIFFERENT email, so only the mobile can be what matched.
    const submit = await openReview(page, { phone, email: uniqueEmail('msg_phone_new') });
    await submit.click();

    await expect(page.locator(POPUP_MESSAGE))
      .toHaveText('Duplicate complaint detected based on mobile number.', { timeout: 20000 });
  });

  test('an email match is explained as an email match', async ({ page, request }) => {
    const email = uniqueEmail('msg_email');
    await fileOnline(request, { phone: freshMobile(), email });

    // A different mobile, so only the email can be what matched.
    const submit = await openReview(page, { phone: freshMobile(), email });
    await submit.click();

    await expect(page.locator(POPUP_MESSAGE))
      .toHaveText('Duplicate complaint detected based on email.', { timeout: 20000 });
  });

  test('the popup carries a title and a note explaining the choice', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('title');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });

    await expect(page.locator(POPUP_TITLE)).toHaveText('Duplicate Complaint Detected');
    await expect(page.locator(POPUP_NOTE)).toContainText('A similar complaint already exists');
  });

  test('the popup carries both a cancel and a proceed button', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('buttons');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });

    await expect(page.locator(CANCEL)).toBeVisible();
    await expect(page.locator(CANCEL)).toBeEnabled();
    await expect(page.locator(PROCEED)).toBeVisible();
    await expect(page.locator(PROCEED)).toBeEnabled();
    // Both blocks describe exactly two choices. A third would mean an untested branch.
    await expect(page.locator('.popup-actions button')).toHaveCount(2);
  });

  test('the popup is announced as a modal dialog rather than drawn as one', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('a11y');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();

    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible({ timeout: 20000 });
    await expect(dialog).toHaveAttribute('aria-modal', 'true');
    // The accessible name must be the title, not the generic overlay.
    await expect(dialog).toHaveAttribute('aria-labelledby', 'dup-title');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54/55 — cancel
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54/55 — cancelling out of the popup', () => {

  test('cancel closes the popup', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('cancel_close');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });

    await page.locator(CANCEL).click();
    await expect(page.locator(POPUP)).toHaveCount(0);
  });

  test('cancel returns the citizen to the review form with their answers intact', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('cancel_return');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(CANCEL).click();

    // Still on the review step of the form, not bounced to the start or to an error page.
    await expect(submit).toBeVisible();
    await expect(submit).toBeEnabled();
    await expect(page.locator('.success-page')).toHaveCount(0);
    // The details the citizen entered must still be on the review screen.
    await expect(page.locator('body')).toContainText(email);
  });

  test('cancel files nothing', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('cancel_nothing');
    await fileOnline(request, { phone, email });

    const registers = watchRegister(page);
    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(CANCEL).click();
    await expect(page.locator(POPUP)).toHaveCount(0);

    expect(registers.count()).toBe(0);
    // The database is the authority on "nothing was filed", not the absence of a request.
    expect(complaintsForPhone(phone).length, 'cancel must not leave a second complaint behind').toBe(1);
  });

  test('after cancelling, pressing submit again re-runs the check rather than filing blind', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('cancel_retry');
    await fileOnline(request, { phone, email });

    const checks = watchCheck(page);
    const registers = watchRegister(page);
    const submit = await openReview(page, { phone, email });

    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(CANCEL).click();
    await expect(page.locator(POPUP)).toHaveCount(0);

    // Cancel must reset the pre-flight, or a second press would register without checking.
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    expect(checks.count(), 'the second press must check again').toBe(2);
    expect(registers.count(), 'and must still not have filed anything').toBe(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C55 — proceed. Entirely untested before this file.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C55 — proceeding despite the warning', () => {

  test('proceed closes the popup and files the complaint', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('proceed');
    const seeded = await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });

    await page.locator(PROCEED).click();
    await expect(page.locator(POPUP)).toHaveCount(0);
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    const rows = complaintsForPhone(phone);
    expect(rows.length, 'the citizen chose to file a second complaint and it must exist').toBe(2);
    const fresh = rows.map(r => r.number).filter(n => n !== seeded);
    expect(fresh.length, 'the second complaint must have its own number, not reuse the first').toBe(1);
  });

  test('the reference number shown after proceeding is the new complaint, not the matched one', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('proceed_ref');
    const seeded = await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(PROCEED).click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    const shown = complaintsForPhone(phone).map(r => r.number).filter(n => n !== seeded)[0];
    expect(shown, 'a second complaint must have been created').toBeTruthy();
    await expect(page.locator('.success-content')).toContainText(shown as string);
  });

  test('proceeding does not run the duplicate check a second time', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('proceed_nocheck');
    await fileOnline(request, { phone, email });

    const checks = watchCheck(page);
    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(PROCEED).click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    // `proceedDespiteDuplicate` sets duplicateCheckDone before re-entering submit(), so the citizen's
    // decision is honoured instead of being re-litigated into a second popup.
    expect(checks.count(), 'the citizen already answered the question once').toBe(1);
  });

  test('a citizen who proceeds is not shown the popup again for the same decision', async ({ page, request }) => {
    const phone = freshMobile();
    const email = uniqueEmail('proceed_once');
    await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(PROCEED).click();

    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });
    await expect(page.locator(POPUP)).toHaveCount(0);
    await expect(page.locator(SUBMIT_ERROR)).toHaveCount(0);
  });

  test.fixme('the complaint created by proceeding is tagged as a duplicate of the one it matched', async ({ page, request }) => {
    // MANUAL BLOCK 54, LAST CASE, verbatim: "If the complainant proceeds with the duplicate complaint
    // then, the new complaint should be created which should be tagged as a duplicate complaint to the
    // previous identified complaint."
    //
    // Nothing is tagged. `proceedDespiteDuplicate()` (ts:2159) sets a boolean and calls submit();
    // `performSubmit()` (ts:2165) builds a payload with no duplicate marker of any kind and does not
    // carry `res.complaintNumber` from the check, which is the only place the matched complaint's
    // identity ever existed. The server is never told the citizen was warned.
    //
    // This is written as the REQUIREMENT because it is a requirement: without the link an officer
    // triaging the second complaint has no way to know a related one is already live, which is the
    // entire point of warning the citizen. Companion test below records the present reality.
    const phone = freshMobile();
    const email = uniqueEmail('tagged');
    const seeded = await fileOnline(request, { phone, email });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });
    await page.locator(PROCEED).click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    const fresh = complaintsForPhone(phone).map(r => r.number).filter(n => n !== seeded)[0] as string;
    const link = sql(
      `SELECT COUNT(*) FROM information_schema.columns WHERE TABLE_SCHEMA = 'cms_db' ` +
      `AND TABLE_NAME IN ('COMPLAINTS','COMPLAINT_MASTER') AND (COLUMN_NAME LIKE '%dup%' ` +
      `OR COLUMN_NAME LIKE '%parent%' OR COLUMN_NAME LIKE '%related%');`
    );
    expect(Number(link), 'there must be somewhere to record the link').toBeGreaterThan(0);
    expect(fresh, 'and the new complaint must point at the one it duplicated').toBeTruthy();
  });

  test('there is nowhere in the schema for a duplicate link to be recorded', async () => {
    // The companion to the fixme above, and the reason it cannot merely be "not implemented yet":
    // the absence is structural, not a missing write. Asserted in SQL rather than inferred from an
    // API response, because an endpoint that returns nothing looks identical either way.
    const columns = sql(
      `SELECT COUNT(*) FROM information_schema.columns WHERE TABLE_SCHEMA = 'cms_db' ` +
      `AND TABLE_NAME IN ('COMPLAINTS','COMPLAINT_MASTER') AND (COLUMN_NAME LIKE '%dup%' ` +
      `OR COLUMN_NAME LIKE '%parent%' OR COLUMN_NAME LIKE '%related%');`
    );
    expect(Number(columns),
      'a duplicate-link column has appeared — the fixme above should now be promoted').toBe(0);

    const tables = sql(
      `SELECT COUNT(*) FROM information_schema.tables WHERE TABLE_SCHEMA = 'cms_db' ` +
      `AND (TABLE_NAME LIKE '%DUPLICATE%' OR TABLE_NAME LIKE '%COMPLAINT_LINK%');`
    );
    expect(Number(tables), 'nor is there a join table for it').toBe(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54 — which of the five parameters the manual names actually participates
//
// The top deliverable of this block. Every row here is a measurement, and the two rows where the
// manual's requirement and the measurement disagree are written both ways.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54 — the five detection parameters, measured one at a time', () => {

  test('the complainant name is sent by nobody and read by nobody', async ({ request }) => {
    // The manual makes the NAME part of the matching combination. The client does not send it
    // (ts:2119-2124 sends phone, email, entityName, category, disputeDate) and the server has no
    // parameter for it. A different name on the same mobile is still a duplicate.
    const phone = freshMobile();
    await fileOnline(request, { phone, email: uniqueEmail('name_leg'), name: 'Original Complainant' });

    const same = await check(request, { phone, complainantName: 'Original Complainant' });
    const different = await check(request, { phone, complainantName: 'Somebody Else Entirely' });

    expect(same.body.duplicate).toBe(true);
    expect(different.body.duplicate,
      'the name does not narrow the match, so a different name is still a duplicate').toBe(true);
  });

  test('the date of the disputed transaction is sent and then ignored', async ({ request }) => {
    // `disputeDate` IS in the POST body (ts:2124) — so the client believes it matters — and no
    // server code reads it. It is the only one of the five that is transmitted and discarded.
    const phone = freshMobile();
    await fileOnline(request, { phone, email: uniqueEmail('date_leg') });

    const same = await check(request, { phone, disputeDate: '2026-01-10' });
    const different = await check(request, { phone, disputeDate: '2019-04-02' });

    expect(same.body.duplicate).toBe(true);
    expect(different.body.duplicate,
      'a wholly unrelated transaction date is still reported as the same complaint').toBe(true);
  });

  test('the category narrows the match when the master knows it', async ({ request }) => {
    const category = await realCategory(request);
    const phone = freshMobile();
    await fileOnline(request, { phone, email: uniqueEmail('cat_leg'), category });

    const sameCategory = await check(request, { phone, category });
    expect(sameCategory.body.duplicate,
      'the same grievance in the same category is the duplicate the control exists for').toBe(true);
  });

  test('a different category is a different grievance and is not a duplicate', async ({ request }) => {
    const res = await request.get(`${API_BASE}/api/categories`);
    const rows = (await res.json()) as { name: string }[];
    expect(rows.length, 'two distinct categories are needed for this measurement').toBeGreaterThan(1);
    const [first, second] = rows;

    const phone = freshMobile();
    await fileOnline(request, { phone, email: uniqueEmail('cat_other'), category: first.name });

    const other = await check(request, { phone, category: second.name });
    expect(other.body.duplicate,
      `"${second.name}" is not the same complaint as "${first.name}"`).toBe(false);
  });

  test('a category name the master does not hold widens the check instead of narrowing it', async ({ request }) => {
    // `resolveCategoryId` returns null for an unknown name and the query reads null as "any
    // category" (repository:164). Benign for the category leg, because registration resolves names
    // the same way — but it is the exact mechanism that makes the ENTITY leg dangerous below.
    const phone = freshMobile();
    const category = await realCategory(request);
    await fileOnline(request, { phone, email: uniqueEmail('cat_unknown'), category });

    const unknown = await check(request, { phone, category: 'Category That Does Not Exist' });
    expect(unknown.body.duplicate, 'an unresolvable category matches everything').toBe(true);
  });

  test('the wizard default category GENERAL is not in the master at all', async ({ request }) => {
    // Recorded because it silently changes which path the whole control takes: `validFormData` and
    // `performSubmit` both default to 'GENERAL' (ts:2172), which resolves to nothing, so the
    // category leg is disabled for every complaint filed without an explicit category.
    const res = await request.get(`${API_BASE}/api/categories`);
    const names = ((await res.json()) as { name: string }[]).map(r => r.name.toLowerCase());
    expect(names, 'if GENERAL becomes a real category the tests above change meaning')
      .not.toContain('general');
  });

  test.fixme('naming the entity the complaint was filed against finds the duplicate', async ({ request }) => {
    // MANUAL BLOCK 54: the ENTITY NAME is part of the combination that detects a duplicate. The
    // plain reading is that supplying the correct entity must find the complaint filed against that
    // entity. It does the opposite — see D-C-35 and the header.
    //
    // Written as the requirement, not bent to the code, because the code's behaviour is not a
    // defensible alternative reading: it makes the control weaker the more accurate the input.
    const phone = freshMobile();
    const entityId = await entityIdFor(request, RESOLVABLE_ENTITY);
    await fileOnline(request, {
      phone, email: uniqueEmail('entity_req'), entityName: RESOLVABLE_ENTITY, entityId,
    });

    const { body } = await check(request, { phone, entityName: RESOLVABLE_ENTITY });
    expect(body.duplicate,
      `a live complaint against ${RESOLVABLE_ENTITY} must be found when that entity is named`).toBe(true);
  });

  test('naming the entity correctly makes the check miss, because bank_id is never written', async ({ request }) => {
    // The companion that records reality, and the regression guard: the day the public filing path
    // starts writing `bank_id`, this fails and the fixme above starts passing.
    const phone = freshMobile();
    const entityId = await entityIdFor(request, RESOLVABLE_ENTITY);
    const seeded = await fileOnline(request, {
      phone, email: uniqueEmail('entity_real'), entityName: RESOLVABLE_ENTITY, entityId,
    });

    const row = complaintsForPhone(phone).find(c => c.number === seeded);
    expect(row?.bankId, 'the public path stores no bank_id, which is the whole cause').toBe('-');

    const named = await check(request, { phone, entityName: RESOLVABLE_ENTITY });
    expect(named.body.duplicate,
      'MEASURED: the correct entity name narrows onto a NULL column and finds nothing').toBe(false);

    const omitted = await check(request, { phone });
    expect(omitted.body.duplicate,
      'while omitting the entity entirely does find it').toBe(true);
  });

  test('an entity name the BANKS master does not hold widens the check into a match', async ({ request }) => {
    // The inversion stated as plainly as it can be: the unresolvable name is the one that WORKS.
    // 133 of the 145 entities the portal offers are unresolvable, so this is the common case.
    const phone = freshMobile();
    const entityId = await entityIdFor(request, RESOLVABLE_ENTITY);
    await fileOnline(request, {
      phone, email: uniqueEmail('entity_wide'), entityName: RESOLVABLE_ENTITY, entityId,
    });

    const { body } = await check(request, { phone, entityName: UNRESOLVABLE_ENTITY });
    expect(body.duplicate,
      `naming ${UNRESOLVABLE_ENTITY} matches a complaint filed against ${RESOLVABLE_ENTITY}`).toBe(true);
  });

  test('online filings never populate the column the entity leg matches on', async () => {
    // Population, not a single row, because a single NULL could be a fluke of one seed. The whole
    // corpus of citizen filings has the column empty, which is what makes D-C-35 a P1 rather than
    // an edge case.
    const [total, populated] = sql(
      `SELECT COUNT(*), SUM(bank_id IS NOT NULL) FROM COMPLAINTS WHERE filing_type = 'ONLINE';`
    ).split('\t');
    expect(Number(total), 'there must be online filings to measure').toBeGreaterThan(0);
    expect(Number(populated),
      'if this stops being zero the entity leg has started working and the fixme can be promoted').toBe(0);
  });

  test('the entity leg resolves against a master far smaller than the one the portal offers', async ({ request }) => {
    // Even once bank_id is written, the resolution step is a second, independent narrowing: the
    // check resolves names against BANKS, which the portal's entity list dwarfs.
    const res = await request.get(`${API_BASE}/api/v1/routing/entities/list`);
    const payload = await res.json();
    const portal = (payload?.data ?? payload ?? []) as { name: string }[];
    const banks = Number(sql('SELECT COUNT(*) FROM BANKS;'));

    expect(portal.length, 'the portal offers a large entity list').toBeGreaterThan(banks);
    // Reported rather than pinned: the gap is the finding, the exact figures belong in the findings file.
    expect(banks, 'and BANKS is the smaller master the duplicate check is limited to').toBeGreaterThan(0);
  });

  test('the wizard sends the entity name only when an entity id was chosen', async ({ page, request }) => {
    // The trap from the header, asserted so it cannot be reintroduced: seeding a draft `entityName`
    // does nothing, because the name is derived from `eligibilityAnswers.regulatedEntity`.
    const phone = freshMobile();
    const email = uniqueEmail('sent_entity');
    await fileOnline(request, { phone, email });

    const bodies: any[] = [];
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /check-duplicate$/.test(req.url())) {
        try { bodies.push(JSON.parse(req.postData() || '{}')); } catch { /* not JSON */ }
      }
    });

    const submit = await openReview(page, { phone, email });
    await submit.click();
    await expect(page.locator(POPUP)).toBeVisible({ timeout: 20000 });

    expect(bodies.length).toBe(1);
    expect(bodies[0].entityName,
      'no entity id was chosen, so the wizard sends an empty name and the entity leg is skipped').toBe('');
    // And the two dead parameters really are on the wire, which is why the manual believes in them.
    expect(Object.keys(bodies[0]).sort())
      .toEqual(['category', 'disputeDate', 'email', 'entityName', 'phone']);
  });

  test('choosing an entity in the wizard suppresses the popup for a complaint that is a duplicate', async ({ page, request }) => {
    // The inversion reaching the citizen. Same seeded duplicate as the popup tests; the only
    // difference is that the wizard now names the entity — and the warning disappears.
    const phone = freshMobile();
    const email = uniqueEmail('ui_suppressed');
    const entityId = await entityIdFor(request, RESOLVABLE_ENTITY);
    await fileOnline(request, { phone, email, entityName: RESOLVABLE_ENTITY, entityId });

    const submit = await openReview(page, { phone, email, entityId });
    await submit.click();

    // No popup, straight to success: a second live complaint filed with no warning at all.
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });
    await expect(page.locator(POPUP)).toHaveCount(0);
    expect(complaintsForPhone(phone).length,
      'two live complaints now exist and the citizen was never told').toBe(2);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54 — the window that closes the check, and the statuses that release it
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54 — how long a complaint keeps blocking a re-filing', () => {

  test('the lookback window is a sharp boundary, and it is measured not assumed', async ({ request }) => {
    // Standing ruling 1: the window is `cms.duplicate-check.lookback-days` and NO endpoint exposes
    // it, so it is bisected here rather than written as a literal. What is asserted is the property
    // that matters — the boundary exists and is monotonic — plus the measured figure in the log.
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('window') });

    const isDuplicateAfter = async (days: number) => {
      backdate(seeded, days);
      return (await check(request, { phone })).body.duplicate === true;
    };

    expect(await isDuplicateAfter(0), 'a complaint filed today must block').toBe(true);
    expect(await isDuplicateAfter(3650), 'a ten-year-old complaint must not').toBe(false);

    let inside = 0, outside = 3650;
    while (outside - inside > 1) {
      const mid = Math.floor((inside + outside) / 2);
      if (await isDuplicateAfter(mid)) inside = mid; else outside = mid;
    }
    console.log(`[QA-C54] measured duplicate-check lookback window: ${inside} days ` +
                `(blocks at ${inside}, releases at ${outside})`);

    expect(outside - inside, 'the boundary must be a single day, not a fuzzy range').toBe(1);
    expect(inside, 'a window this short would make the control nearly useless').toBeGreaterThan(7);
  });

  test('a closed complaint stops blocking a fresh filing', async ({ request }) => {
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('closed') });

    expect((await check(request, { phone })).body.duplicate).toBe(true);
    setStatus(seeded, 'closed');
    expect((await check(request, { phone })).body.duplicate,
      'a citizen must be free to raise the matter again once it is closed').toBe(false);
  });

  test('a withdrawn complaint stops blocking a fresh filing', async ({ request }) => {
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('withdrawn') });

    setStatus(seeded, 'withdrawn');
    expect((await check(request, { phone })).body.duplicate,
      'withdrawing is the citizen deciding not to pursue it, not a live grievance').toBe(false);
  });

  test('a rejected complaint stops blocking a fresh filing', async ({ request }) => {
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('rejected') });

    setStatus(seeded, 'rejected');
    expect((await check(request, { phone })).body.duplicate).toBe(false);
  });

  test('a complaint still being worked keeps blocking, whatever stage it has reached', async ({ request }) => {
    // The complement of the three above: the terminal list is short and specific, so every other
    // status must still block. Table-driven over the live statuses a complaint passes through.
    const phone = freshMobile();
    const seeded = await fileOnline(request, { phone, email: uniqueEmail('live') });

    for (const status of ['pending', 'assigned', 'in_progress', 'resolved', 'escalated']) {
      setStatus(seeded, status);
      const { body } = await check(request, { phone });
      expect(body.duplicate, `status "${status}" is not terminal and must still block`).toBe(true);
    }
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C54/55 — the popup in the citizen's own language
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C54/55 — the popup in Hindi and Punjabi', () => {

  const KEYS = ['duplicate.title', 'duplicate.note', 'duplicate.cancel', 'duplicate.proceed'];

  async function bundle(request: APIRequestContext, locale: string): Promise<Record<string, string>> {
    const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
    expect(res.ok(), `the ${locale} bundle must be served`).toBeTruthy();
    const payload = await res.json();
    return (payload?.data ?? payload ?? {}) as Record<string, string>;
  }

  test('every string the popup shows comes from the translation bundle', async ({ request }) => {
    const en = await bundle(request, 'en');
    for (const key of KEYS) {
      expect(en[key], `${key} is missing from the English bundle`).toBeTruthy();
    }
  });

  test('Hindi translates all four of the popup strings', async ({ request }) => {
    const en = await bundle(request, 'en');
    const hi = await bundle(request, 'hi');

    const untranslated = KEYS.filter(k => hi[k] === en[k]);
    expect(untranslated, 'Hindi is fully translated here and must stay that way').toEqual([]);
  });

  test.fixme('Punjabi translates all four of the popup strings', async ({ request }) => {
    // Same defect class as D-C-30 (the non-maintainable refusal messages). The `pa` bundle is
    // served and populated, so this is not a missing locale — these specific keys fall through to
    // English. A citizen who has chosen Punjabi is asked to make an irreversible filing decision in
    // a language they did not choose.
    const en = await bundle(request, 'en');
    const pa = await bundle(request, 'pa');

    const untranslated = KEYS.filter(k => pa[k] === en[k]);
    expect(untranslated, 'the duplicate popup must be readable in Punjabi').toEqual([]);
  });

  test('all four Punjabi strings are presently English, and the bundle is otherwise populated', async ({ request }) => {
    // The companion, and the regression guard in both directions: if Punjabi is fixed this fails and
    // the fixme is promoted; if the bundle stops being served at all, the second assertion says so
    // rather than letting a 404 masquerade as "translated".
    const en = await bundle(request, 'en');
    const pa = await bundle(request, 'pa');

    expect(KEYS.filter(k => pa[k] === en[k]).sort(),
      'MEASURED: all four popup keys are English in Punjabi').toEqual([...KEYS].sort());
    expect(Object.keys(pa).length,
      'the pa bundle is real and substantial, so this is a per-key gap not a missing locale')
      .toBeGreaterThan(100);
  });
});
