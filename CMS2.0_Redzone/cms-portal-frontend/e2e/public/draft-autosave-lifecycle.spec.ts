/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual block 47 (Save / autosave / draft lifecycle).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3513-3575.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * ═══ WHAT IS ALREADY COVERED ELSEWHERE, AND IS NOT REPEATED HERE ═══
 * `draft-save.spec.ts` (UST82/D15, 4 tests) covers exactly one thing: that a draft save which the
 * SERVER rejects is not reported to the citizen as saved. It stubs `POST /drafts` with a 500 and
 * asserts the badges. It says nothing about whether a real save persists, where it persists, who can
 * read it afterwards, or what happens on refresh — which is the whole of block 47. No overlap.
 *
 * ═══ THE TWO PLACES A DRAFT LIVES, AND WHY EVERY TEST HERE CHECKS BOTH ═══
 * `saveDraft()` (ts:1865-1871) writes sessionStorage FIRST and then fires `POST /drafts`. The
 * sessionStorage copy dies with the tab; only the server row survives a closed browser. The manual's
 * cases ("the data entered should be saved", "post browser refresh ... auto saved") do not
 * distinguish the two, so each is asserted separately: a test that only checked sessionStorage would
 * pass on a system that persists nothing.
 *
 * ═══ THE REFRESH CASE FAILS ON THE REAL ENTRY PATH — D-C-19 ═══
 * `ngOnInit` (ts:804-814) keeps the draft only when the URL carries `?resume=true` or `?draftId=`.
 * On the bare `/public/file-complaint` — which is where the home page's "File a Complaint" button
 * lands — the ELSE branch DELETES `cms_complaint_draft`, `cms_draft_id` and `cms_draft_saved_at`.
 * So pressing F5 mid-form empties the form the citizen was filling. The manual explicitly requires
 * the opposite. Written as a `fixme` per ruling 2, with passing companions that record what actually
 * happens and prove the server row survives (which is what makes it recoverable at all).
 *
 * ═══ THE DRAFT ENDPOINTS ARE UNAUTHENTICATED — D-C-20 ═══
 * `/api/v1/complaints/drafts/**` is `permitAll` (SecurityConfig:125) and the controller checks no
 * ownership whatsoever: `getDraft` returns the row for any id, `deleteDraft` deletes it. A draft holds
 * the complainant's name, address, phone, email, account numbers and complaint narrative — the same
 * PII that `GET /api/v1/complaints` refuses to serve without a citizen session (401 SESSION_EXPIRED,
 * asserted in `tracking-authz.spec.ts:29-35`). Asserted here as observed, so the gap is a recorded
 * fact, with `fixme`s carrying the required behaviour.
 *
 * ═══ ONE DRAFT PER PHONE NUMBER — D-C-21 ═══
 * The controller upserts on phone (`ComplaintDraftController:39-45`). A citizen who saves a draft
 * against one bank and then starts a complaint against another silently loses the first.
 *
 * ═══ THE SERVER DRAFT IS WRITE-ONLY — D-C-24 ═══
 * Found by automation. My Complaints always shows the sessionStorage draft, never the server one:
 * `finalizeLoad` calls `complaints.set(...)` from the `GET /api/v1/complaints` handler and clobbers
 * what the concurrent drafts subscription merged, and that complaints call returns 401 for a citizen
 * session anyway. Two consequences, both asserted: a draft held ONLY on the server (i.e. after closing
 * the browser — the one case the server copy exists for) is invisible and unresumable; and pressing
 * Delete clears only sessionStorage, leaving the citizen's PII on the server after they asked for it to
 * be gone. That makes the whole save-to-server mechanism write-only.
 *
 * ═══ THE SAVE CONFIRMATION IS INVISIBLE — D-C-23 ═══
 * The wizard's `.session-bar` — session clock, a second save button, and the only non-transient
 * "Last saved at HH:MM" stamp — is `display: none` in the component's own stylesheet (scss:8-10). The
 * stamp is set and cannot be seen; the only visible confirmation, `.auto-save-text`, is cleared after
 * 2 seconds (ts:1903). 29 tests failed on this before it was understood, which is the clearest possible
 * evidence that the element exists and the citizen cannot see it.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - `draftSaved` is cleared after 2 s (`ts:1903`), so `.draft-badge` / `.auto-save-text` are racy by
 *    construction. Persistence is asserted on `.last-saved-text` (never cleared) and on
 *    `cms_draft_saved_at`, never on the transient badge.
 *  - The save control is rendered TWICE with different classes: `.session-bar .btn-link` at the top of
 *    every screen and `button.btn-save-link` in the step actions. The manual's "on every screen" means
 *    the first; the citizen normally clicks the second.
 *  - Draft ids are `DRF-XXXXXXXX`; `phone` is the real key. Cleanup deletes by id, and the
 *    98765_3____ range keeps that from touching another session's rows.
 */

import { execFileSync } from 'child_process';
import { test, expect } from '../fixtures';
import {
  API_BASE, APP_BASE, openWizardAtStep, sessionCMobile,
} from './helpers-submission-c';
import { deleteOtpAttempts, seedCitizenSession } from '../utils/test-data';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

const SAVE_STEP = 'button.btn-save-link';
const SAVE_SESSION_BAR = '.session-bar .btn-link';
const LAST_SAVED = '.last-saved-text';
const AUTO_SAVED = '.auto-save-text';
const NEXT = 'button.btn-next';
const BACK = 'button.btn-go-back';

const DRAFT_POST = /\/api\/v1\/complaints\/drafts$/;

/**
 * Reads the server's own copy of a citizen's draft.
 *
 * Deliberately goes through the DB rather than the API for the persistence assertions: the endpoint
 * that would answer the question is the same one D-C-20 shows to be unauthenticated, and a test that
 * proved persistence by calling it would be proving two things at once.
 */
function readDraftRows(phone: string): Array<{ draftId: string; step: string; phase: string; formData: string }> {
  if (!/^[0-9]{10}$/.test(phone)) throw new Error(`Refusing to use an unexpected phone in SQL: ${phone}`);
  const sql =
    `SELECT draft_id, COALESCE(current_step,''), COALESCE(phase,''), COALESCE(form_data_json,'') ` +
    `FROM COMPLAINT_DRAFTS WHERE phone = '${phone}' ORDER BY id ASC;`;
  const out = execFileSync(
    process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
  );
  return out.trim().split('\n').filter(l => l.trim().length > 0).map(line => {
    const [draftId, step, phase, formData] = line.split('\t');
    return { draftId, step, phase, formData };
  });
}

/** Proving a complaint was NOT filed is a SQL question, not an HTTP one (ruling 3). */
function countComplaintsFor(phone: string): number {
  if (!/^[0-9]{10}$/.test(phone)) throw new Error(`Refusing to use an unexpected phone in SQL: ${phone}`);
  const out = execFileSync(
    process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e',
      `SELECT COUNT(*) FROM COMPLAINTS WHERE complainant_phone = '${phone}';`],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
  );
  return Number(out.trim());
}

function deleteDraftRows(phone: string): void {
  if (!/^[0-9]{10}$/.test(phone)) return;
  try {
    execFileSync(
      process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
      ['-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e',
        `DELETE FROM COMPLAINT_DRAFTS WHERE phone = '${phone}';`],
      { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] },
    );
  } catch { /* best effort */ }
}

test.afterAll(async () => {
  for (const m of mobiles) {
    deleteDraftRows(m);
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

/**
 * Saves and waits for the SERVER to have accepted it.
 *
 * ═══ WHY NOT WAIT ON A BADGE ═══
 * The obvious wait is `.last-saved-text` ("Last saved at HH:MM"), which unlike `draftSaved` is never
 * cleared. It is also never SEEN: it lives in the wizard's `.session-bar`, and that bar is
 * `display: none` (component scss:8-10). Playwright resolves the element and reports it hidden — 29
 * tests failed on exactly that before this was understood. See D-C-23.
 * The remaining visible indicator, `.auto-save-text`, is wiped after 2 s (ts:1903), so it is racy by
 * construction and unusable as a gate.
 * Waiting on the POST response is both honest and stable: it is the event the persistence claim is
 * actually about.
 */
async function clickSaveAndSettle(page: any): Promise<void> {
  const saved = page.waitForResponse(
    (r: any) => DRAFT_POST.test(r.url()) && r.request().method() === 'POST' && r.status() < 400,
    { timeout: 20000 },
  );
  await page.locator(SAVE_STEP).first().click();
  await saved;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The save control itself
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — a save control is available on every stage of the complaint form', () => {

  for (const step of [1, 2, 3, 4, 5, 6]) {
    test(`the save button is present on form step ${step}`, async ({ page }) => {
      // MANUAL: "On every screen/stage in the complaint form save button should be present."
      // Asserted per step rather than once, because each step renders its own action row and one that
      // omitted it would leave the citizen no way to save from that screen.
      await openWizardAtStep(page, nextMobile(), { step });

      await expect(page.locator(SAVE_STEP).first()).toBeVisible({ timeout: 20000 });
    });
  }

  test('the save button is present during the eligibility stage too', async ({ page }) => {
    // The manual says "every screen/stage". Eligibility is a stage, and `startAutoSave` (ts:1914)
    // saves in that phase as well, so a citizen abandoning mid-eligibility should also have a draft.
    const mobile = nextMobile();
    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator(SAVE_STEP).first()).toBeVisible({ timeout: 20000 });
  });

  test('the second save control at the top of the screen is never actually visible', async ({ page }) => {
    // The template renders a SECOND save button, in a `.session-bar` that also carries the session
    // clock and the "Last saved at HH:MM" stamp (html:1-15). The whole bar is `display: none`
    // (component scss:8-10), so none of it reaches the citizen — including the only non-transient
    // confirmation that a draft was saved. Recorded as observed; see D-C-23.
    await openWizardAtStep(page, nextMobile(), { step: 3 });

    await expect(page.locator(SAVE_SESSION_BAR)).toHaveCount(1);
    await expect(page.locator(SAVE_SESSION_BAR)).toBeHidden();
  });

  test('the save button is clickable and confirms the save to the citizen', async ({ page }) => {
    // MANUAL: "The user should be able to click on the save button." A button that is present but
    // reports nothing is indistinguishable from one that does nothing, so the assertion is on the
    // confirmation. `.auto-save-text` is the only confirmation the citizen can see, and it survives
    // for 2 s (ts:1903) — caught by waiting for it rather than checking after the fact.
    await openWizardAtStep(page, nextMobile(), { step: 3 });

    await page.locator(SAVE_STEP).first().click();

    await expect(page.locator(AUTO_SAVED).first()).toBeVisible({ timeout: 15000 });
  });

  test('the only durable "last saved" stamp is in the hidden bar, so the confirmation vanishes after two seconds', async ({ page }) => {
    // The consequence of D-C-23 stated as behaviour: the stamp is set (so the component believes it is
    // informing the citizen) but cannot be seen, and the visible badge is gone within 2 s. A citizen who
    // looks away has no way to tell whether the draft saved.
    await openWizardAtStep(page, nextMobile(), { step: 3 });
    await clickSaveAndSettle(page);

    await expect(page.locator(LAST_SAVED)).toContainText('Last saved at');
    await expect(page.locator(LAST_SAVED)).toBeHidden();

    await expect(page.locator(AUTO_SAVED)).toHaveCount(0, { timeout: 15000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// What "saved" means — sessionStorage and the server row
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — saving persists the entered data both locally and on the server', () => {

  test('the entered data is written to the server, not only to the tab', async ({ page }) => {
    // MANUAL: "The data entered should be saved." The distinction matters more than the manual
    // allows: the sessionStorage copy dies with the tab, so only this row makes the promise true.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3, formData: { complaintText: 'QA-C47 server persistence assertion.' },
    });

    await clickSaveAndSettle(page);

    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
    const row = readDraftRows(mobile)[0];
    expect(row.draftId).toMatch(/^DRF-[0-9A-F]{8}$/);
    expect(row.formData).toContain('QA-C47 server persistence assertion.');
  });

  test('the server records which step the citizen had reached', async ({ page }) => {
    // Resuming onto the wrong step would make the citizen re-walk screens they had finished, so the
    // step is part of what "saved" has to mean.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 4 });

    await clickSaveAndSettle(page);

    await expect.poll(() => readDraftRows(mobile)[0]?.step, { timeout: 15000 }).toBe('4');
    expect(readDraftRows(mobile)[0].phase).toBe('form');
  });

  test('a partially filled form is saved as a draft', async ({ page }) => {
    // MANUAL: "If user have partially filled the form and click on save button, then, the complaint
    // should be saved as draft." Saved from step 1 with the later screens untouched — the point is
    // that saving does NOT run the step validators.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 1,
      formData: { firstName: 'Partly', lastName: '', address: '', complaintText: '' },
    });

    await clickSaveAndSettle(page);

    const rows = readDraftRows(mobile);
    expect(rows).toHaveLength(1);
    expect(rows[0].formData).toContain('Partly');
  });

  test('typed text is captured by a later save without re-entering it', async ({ page }) => {
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    const narrative = page.locator('textarea[name="complaintText"]');
    await expect(narrative).toBeVisible({ timeout: 20000 });
    await narrative.fill('Typed after the draft was seeded, then saved.');

    await clickSaveAndSettle(page);

    await expect.poll(() => readDraftRows(mobile)[0]?.formData ?? '', { timeout: 15000 })
      .toContain('Typed after the draft was seeded, then saved.');
  });

  test('moving to the next step saves without the citizen pressing save', async ({ page }) => {
    // MANUAL: "The date entered by the user should be auto saved." `nextStep()` calls `saveDraft()`
    // (ts:1845), which is the autosave a citizen actually experiences — the 30s timer is the other one.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    await page.locator(NEXT).first().click();

    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await expect.poll(() => readDraftRows(mobile)[0]?.step, { timeout: 15000 }).toBe('4');
  });

  test('the 30-second autosave timer saves an untouched form on its own', async ({ page }) => {
    // MANUAL: "the details entered by the user should be auto saved" — this is the literal timer
    // (`startAutoSave`, ts:1912-1918). Slow by nature, so it gets a raised budget and exactly one test;
    // the navigation-triggered save above covers the same requirement cheaply.
    test.setTimeout(120000);
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    expect(readDraftRows(mobile)).toHaveLength(0);

    // No interaction at all: if this row appears, the timer wrote it.
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 70000, intervals: [2000] }).toBe(1);
  });

  test('navigating back and forth preserves what was entered', async ({ page }) => {
    // MANUAL: "When the user navigates back and forth in the complaint, the entered details should be
    // saved." `prevStep()` (ts:1849) does NOT save — the values survive because they live in the
    // component's formData, not because they were persisted. Both halves are asserted.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    const narrative = page.locator('textarea[name="complaintText"]');
    await expect(narrative).toBeVisible({ timeout: 20000 });
    await narrative.fill('Survives a back and forward trip.');

    await page.locator(NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator(BACK).first().click();

    await expect(narrative).toHaveValue('Survives a back and forward trip.');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Refresh — where the manual's requirement and the code part company
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — a draft survives a page refresh', () => {

  test('reloading the resume URL keeps the entered details', async ({ page }) => {
    // The path `?resume=true` takes, i.e. what a citizen who came back via My Complaints sees.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3, formData: { complaintText: 'Present before the reload.' },
    });
    await clickSaveAndSettle(page);

    await page.reload();
    await page.waitForLoadState('networkidle');

    await expect(page.locator('textarea[name="complaintText"]'))
      .toHaveValue('Present before the reload.', { timeout: 20000 });
  });

  test.fixme('refreshing the wizard on its own URL keeps the entered details', async ({ page }) => {
    // THE DEFECT MARKER for D-C-19, and the manual's requirement stated as required behaviour.
    // A citizen who reached the form from the home page is on `/public/file-complaint` with no query
    // string. `ngOnInit`'s else branch (ts:810-814) deletes the draft on that URL, so F5 mid-form
    // empties the screen. NOT fixed — `src/**` is shared with Sessions A and B (ruling 6).
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3, formData: { complaintText: 'Should survive an ordinary refresh.' },
    });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('textarea[name="complaintText"]'))
      .toHaveValue('Should survive an ordinary refresh.', { timeout: 20000 });
  });

  test('landing on the bare wizard URL discards the local draft, which is what breaks the refresh case', async ({ page }) => {
    // The passing companion to the `fixme` above: recorded as observed so the defect is a fact rather
    // than an impression, and so nobody "fixes" the resume path by mistake.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'About to be lost.' } });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    const local = await page.evaluate(() => sessionStorage.getItem('cms_complaint_draft'));
    expect(local, 'the bare URL wipes the local draft').toBeNull();
    const savedAt = await page.evaluate(() => sessionStorage.getItem('cms_draft_saved_at'));
    expect(savedAt).toBeNull();
  });

  test('the server copy outlives the local wipe, so the draft is still recoverable', async ({ page }) => {
    // This is the mitigation, and the reason D-C-19 is a P2 rather than outright data loss: the row is
    // untouched, so My Complaints still lists the draft and Resume still reaches it.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'Held on the server.' } });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);

    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    const rows = readDraftRows(mobile);
    expect(rows).toHaveLength(1);
    expect(rows[0].formData).toContain('Held on the server.');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The draft in My Complaints, and resuming it
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — a saved draft is listed and can be resumed and edited', () => {

  test('the draft is listed with DRAFT status in My Complaints', async ({ page }) => {
    // MANUAL: "Post logging in the CMS Portal, the complaint is draft status should be displayed to
    // the user ... under my complaints tab."
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('.status-badge.status-draft').first()).toBeVisible({ timeout: 20000 });
    await expect(page.locator('tr.draft-row').first()).toBeVisible();
  });

  test('the listed draft offers Resume and Delete rather than a read-only view', async ({ page }) => {
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('button.btn-resume').first()).toBeVisible({ timeout: 20000 });
    await expect(page.locator('button.btn-delete-draft').first()).toBeVisible();
  });

  test('resuming the draft returns the citizen to the form with the data intact', async ({ page }) => {
    // MANUAL: "The user should be able to edit the complaint details" (status draft).
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3, formData: { complaintText: 'Resumed from My Complaints.' },
    });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');
    await page.locator('button.btn-resume').first().click();

    await expect(page.locator('textarea[name="complaintText"]'))
      .toHaveValue('Resumed from My Complaints.', { timeout: 20000 });
  });

  test('a resumed draft can be edited and the edit is saved', async ({ page }) => {
    // MANUAL: "User should be able to edit and submit the form before submiting." The edit half is
    // here; the submit half is the describe below.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'First version.' } });
    await clickSaveAndSettle(page);

    const narrative = page.locator('textarea[name="complaintText"]');
    await narrative.fill('Edited second version.');
    await page.locator(SAVE_STEP).first().click();

    await expect.poll(() => readDraftRows(mobile)[0]?.formData ?? '', { timeout: 15000 })
      .toContain('Edited second version.');
    expect(readDraftRows(mobile)[0].formData).not.toContain('First version.');
  });

  test('deleting the draft from My Complaints removes it from the list', async ({ page }) => {
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');
    await page.locator('button.btn-delete-draft').first().click();

    await expect(page.locator('tr.draft-row')).toHaveCount(0);
    await expect(page.locator('button.btn-resume')).toHaveCount(0);
  });

  test('deleting the draft leaves the server copy behind, so the deletion does not stick', async ({ page }) => {
    // OBSERVED, and the citizen-facing half of D-C-24.
    //
    // The row My Complaints shows is ALWAYS the local one: `getLocalDraft()` synthesises a record with
    // `draftId: 'local'` (history ts:135-156), and `deleteDraft` for that id only clears sessionStorage
    // (ts:189-194) — it never calls `complaintService.deleteDraft`. So the citizen presses the bin, the
    // row disappears, and their complaint narrative, address, phone and account numbers remain on the
    // server indefinitely, still readable by the unauthenticated endpoint above.
    //
    // A citizen who deletes a draft is asking for it to be gone. This is a data-retention problem, not
    // just a stale list.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'Asked to be deleted.' } });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');
    await page.locator('button.btn-delete-draft').first().click();
    await expect(page.locator('tr.draft-row')).toHaveCount(0);

    const rows = readDraftRows(mobile);
    expect(rows, 'the server row survives the citizen deleting the draft').toHaveLength(1);
    expect(rows[0].formData).toContain('Asked to be deleted.');
  });

  test.fixme('deleting the draft deletes the server copy as well', async ({ page }) => {
    // Required behaviour for D-C-24. `complaintService.deleteDraft` exists and the endpoint works; the
    // 'local' branch simply never calls it. NOT applied — `src/**` is shared (ruling 6).
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');
    await page.locator('button.btn-delete-draft').first().click();

    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(0);
  });

  test('a draft held only on the server is not listed at all, so it cannot be resumed on a new device', async ({ page }) => {
    // OBSERVED, and the other half of D-C-24. The server draft exists precisely for the case the local
    // copy cannot serve — a closed browser, a different device — and that is exactly when it is
    // invisible.
    //
    // TWO causes, both in `loadComplaints` (history ts:74-130):
    //  1. `finalizeLoad` runs in the `GET /api/v1/complaints` handler and calls `complaints.set(...)`,
    //     clobbering whatever the drafts subscription had already merged. The two are concurrent, so
    //     the server drafts are dropped whenever the complaints call answers second.
    //  2. That complaints call returns 401 SESSION_EXPIRED here, and `error:` also calls
    //     `finalizeLoad` — so a citizen whose session the server rejects gets an empty list rather
    //     than an explanation, and the 401 is retried three times by the error interceptor.
    //
    // Asserted with no local copy present, which is the real-world state after closing the browser.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'Only on the server.' } });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);

    // Simulates a new tab/device: the server row is untouched, the sessionStorage copy is gone.
    await page.evaluate(() => {
      sessionStorage.removeItem('cms_complaint_draft');
      sessionStorage.removeItem('cms_draft_saved_at');
      sessionStorage.removeItem('cms_draft_id');
    });

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('tr.draft-row')).toHaveCount(0);
    await expect(page.locator('button.btn-resume')).toHaveCount(0);
    // The row is still there — nothing surfaces it.
    expect(readDraftRows(mobile)).toHaveLength(1);
  });

  test.fixme('a draft held on the server is listed and can be resumed', async ({ page }) => {
    // Required behaviour for D-C-24. Without this the server draft is write-only: saved on every step,
    // never readable, and the "your draft is safe if you close the browser" promise is not kept.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { complaintText: 'Only on the server.' } });
    await clickSaveAndSettle(page);
    await page.evaluate(() => sessionStorage.removeItem('cms_complaint_draft'));

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('button.btn-resume').first()).toBeVisible({ timeout: 20000 });
    await page.locator('button.btn-resume').first().click();
    await expect(page.locator('textarea[name="complaintText"]'))
      .toHaveValue('Only on the server.', { timeout: 20000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Draft until submitted — and not a complaint until then
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — the complaint stays a draft until it is submitted', () => {

  test('saving does not file a complaint', async ({ page }) => {
    // MANUAL: "Until user clicks on the submit button, the complaint status should be draft."
    // Proven in SQL, not by a status code (ruling 3): the absence of a COMPLAINTS row is the claim.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    await clickSaveAndSettle(page);

    expect(countComplaintsFor(mobile), 'saving must not register a complaint').toBe(0);
    expect(readDraftRows(mobile)).toHaveLength(1);
  });

  test('saving repeatedly across several steps still files nothing', async ({ page }) => {
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });

    await clickSaveAndSettle(page);
    await page.locator(NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator(SAVE_STEP).first().click();

    expect(countComplaintsFor(mobile)).toBe(0);
    // Upserted, not appended — one row per phone, however many times the citizen saves.
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
  });

  test('a successful submission clears the draft, so there is nothing left to edit', async ({ page }) => {
    // MANUAL: "Post submitting the complaint, the user should not be able to edit the complaint
    // details." The mechanism is `clearDraft()` on success (ts:2203): the draft is removed locally and
    // deleted on the server, so no Resume route to the filled form remains.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 6,
      formData: { phone: mobile, email: `qa_c47_${Date.now().toString(36)}@example.com` },
    });

    const submit = page.locator('button.btn-submit-complaint');
    await expect(submit).toBeVisible({ timeout: 20000 });
    await submit.click();

    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });
    const local = await page.evaluate(() => sessionStorage.getItem('cms_complaint_draft'));
    expect(local).toBeNull();
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(0);
  });

  test('the submitted complaint is no longer listed as a draft in My Complaints', async ({ page }) => {
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 6,
      formData: { phone: mobile, email: `qa_c47_${Date.now().toString(36)}@example.com` },
    });
    await page.locator('button.btn-submit-complaint').click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    await page.goto(`${APP_BASE}/public/history`);
    await page.waitForLoadState('networkidle');

    await expect(page.locator('button.btn-resume')).toHaveCount(0);
    await expect(page.locator('tr.draft-row')).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Who else can read the draft — not in the manual, and the most serious thing in this block
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — draft persistence and the protection of what it holds', () => {

  test('a draft holds the complainant PII that makes its protection matter', async ({ page }) => {
    // Stated first so the severity of the tests below is not a matter of opinion: this is the data set
    // an unauthenticated caller can fetch.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3,
      formData: { firstName: 'Priya', lastName: 'Sharma', address: '14 MG Road, Mumbai' },
    });
    await clickSaveAndSettle(page);

    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
    const stored = readDraftRows(mobile)[0].formData;
    expect(stored).toContain('Priya');
    expect(stored).toContain('14 MG Road, Mumbai');
    expect(stored).toContain(mobile);
  });

  test('anyone with the draft id can read it, with no citizen session at all', async ({ page, request }) => {
    // OBSERVED. `/api/v1/complaints/drafts/**` is permitAll (SecurityConfig:125) and
    // `ComplaintDraftController.getDraft` performs no ownership check. See D-C-20.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { firstName: 'Unprotected' } });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
    const draftId = readDraftRows(mobile)[0].draftId;

    // No X-Citizen-Token, no cookie, nothing.
    const res = await request.get(`${API_BASE}/api/v1/complaints/drafts/${draftId}`);

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.data.phone).toBe(mobile);
    expect(JSON.stringify(body.data.formData)).toContain('Unprotected');
  });

  test('a draft can be enumerated by mobile number with no session', async ({ page, request }) => {
    // Worse than the id case: the mobile number is guessable in a way a random draft id is not.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3, formData: { firstName: 'Enumerable' } });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);

    const res = await request.get(`${API_BASE}/api/v1/complaints/drafts?phone=${mobile}`);

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.data).toHaveLength(1);
    expect(JSON.stringify(body.data[0].formData)).toContain('Enumerable');
  });

  test('the same PII is refused on the submitted-complaint list without a session, which is the inconsistency', async ({ request }) => {
    // The discriminator. `GET /api/v1/complaints` returns 401 SESSION_EXPIRED
    // (ComplaintApiV1Controller:348-352) for the same complainant data once it is a complaint. The
    // rule exists and is enforced — it was simply never applied to the drafts controller. Without this
    // test the two tests above could be read as "this system has no citizen auth", which is not so.
    const res = await request.get(`${API_BASE}/api/v1/complaints?phone=9876530000`);

    expect(res.status()).toBe(401);
    expect((await res.json()).error).toBe('SESSION_EXPIRED');
  });

  test('anyone with the draft id can delete it, with no citizen session at all', async ({ page, request }) => {
    // OBSERVED, and the reason D-C-20 is P1 rather than P2: this is destructive, not merely a
    // disclosure. A citizen's part-written complaint can be removed by an unauthenticated caller.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
    const draftId = readDraftRows(mobile)[0].draftId;

    const res = await request.delete(`${API_BASE}/api/v1/complaints/drafts/${draftId}`);

    expect(res.status()).toBe(200);
    expect(readDraftRows(mobile)).toHaveLength(0);
  });

  test.fixme('reading a draft without a citizen session is refused', async ({ page, request }) => {
    // Required behaviour for D-C-20, stated as the fix would make it: the same 401 the complaint list
    // already returns. NOT applied — this is server `src/**` and Sessions A and B share the build.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);
    const draftId = readDraftRows(mobile)[0].draftId;

    const res = await request.get(`${API_BASE}/api/v1/complaints/drafts/${draftId}`);
    expect(res.status()).toBe(401);
  });

  test.fixme('deleting a draft belonging to another mobile number is refused', async ({ page, request }) => {
    // Required behaviour for D-C-20: ownership, as `GET /api/v1/complaints` enforces with a 403 and an
    // anomaly counter (ComplaintApiV1Controller:354-362).
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, { step: 3 });
    await clickSaveAndSettle(page);
    const draftId = readDraftRows(mobile)[0].draftId;

    const res = await request.delete(`${API_BASE}/api/v1/complaints/drafts/${draftId}`, {
      headers: { 'X-Citizen-Token': 'a-different-citizen.sig' },
    });
    expect(res.status()).toBe(403);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// One draft per citizen — a limit the manual never states
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — a citizen may hold only one draft at a time', () => {

  test('a second complaint draft replaces the first instead of joining it', async ({ page }) => {
    // OBSERVED. `ComplaintDraftController:39-45` upserts on phone. Nothing in the UI says so, and the
    // first complaint's answers are gone with no warning. See D-C-21.
    const mobile = nextMobile();

    await openWizardAtStep(page, mobile, {
      step: 3, entityName: 'First Bank Ltd', formData: { complaintText: 'Complaint against bank one.' },
    });
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);

    await openWizardAtStep(page, mobile, {
      step: 3, entityName: 'Second Bank Ltd', formData: { complaintText: 'Complaint against bank two.' },
    });
    await clickSaveAndSettle(page);

    await expect.poll(() => readDraftRows(mobile)[0]?.formData ?? '', { timeout: 15000 })
      .toContain('Complaint against bank two.');
    const rows = readDraftRows(mobile);
    expect(rows, 'the first draft was overwritten, not kept alongside').toHaveLength(1);
    expect(rows[0].formData).not.toContain('Complaint against bank one.');
  });

  test.fixme('a citizen with a complaint against two entities can hold a draft for each', async ({ page }) => {
    // Required behaviour for D-C-21. Whether to allow several drafts or to warn before replacing one
    // is a product decision; what is not defensible is silently discarding the first.
    const mobile = nextMobile();

    await openWizardAtStep(page, mobile, {
      step: 3, entityName: 'First Bank Ltd', formData: { complaintText: 'Complaint against bank one.' },
    });
    await clickSaveAndSettle(page);
    await openWizardAtStep(page, mobile, {
      step: 3, entityName: 'Second Bank Ltd', formData: { complaintText: 'Complaint against bank two.' },
    });
    await clickSaveAndSettle(page);

    expect(readDraftRows(mobile)).toHaveLength(2);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Attachments and the draft — where the wizard loses them
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C47 — what a draft does and does not carry', () => {

  test('a draft records only the NAMES of chosen files, never the files themselves', async ({ page }) => {
    // `saveDraft` stores `attachmentMeta` — name, type, size (ts:1866) — and `loadDraft` rebuilds the
    // chips with `url: ''` (ts:1952). The bytes cannot be carried in sessionStorage at any realistic
    // size, so this is a correct design; what matters is whether the citizen is TOLD.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3,
      attachmentMeta: [{ name: 'passbook.pdf', type: 'application/pdf', size: 1024 }],
    });

    await expect(page.locator('.file-chip')).toHaveCount(1);
    await expect(page.locator('.file-chip')).toContainText('passbook.pdf');

    // The server draft payload has no attachment field at all (`saveDraftToServer`, ts:1879-1885), so
    // the file names do not even reach the row — only the current tab remembers them.
    await clickSaveAndSettle(page);
    await expect.poll(() => readDraftRows(mobile).length, { timeout: 15000 }).toBe(1);
    expect(readDraftRows(mobile)[0].formData).not.toContain('passbook.pdf');
  });

  test('a restored attachment is marked as needing re-upload rather than passed off as attached', async ({ page }) => {
    // Worth asserting precisely BECAUSE the obvious implementation gets this wrong: a chip rebuilt from
    // metadata is indistinguishable from a real attachment unless the template says otherwise. It does
    // — `[class.needs-reupload]="!f.url"` plus a "(re-upload needed)" hint (html:882-887) — and the
    // preview button is withheld, so nothing offers to open a file that is not there.
    const mobile = nextMobile();
    await openWizardAtStep(page, mobile, {
      step: 3,
      attachmentMeta: [{ name: 'statement.pdf', type: 'application/pdf', size: 2048 }],
    });

    const chip = page.locator('.file-chip').first();
    await expect(chip).toContainText('statement.pdf');
    await expect(chip).toHaveClass(/needs-reupload/);
    await expect(chip.locator('.reupload-hint')).toContainText('re-upload needed');
    await expect(chip.locator('.preview-file')).toHaveCount(0);
    await expect(chip.locator('.remove-file')).toBeVisible();
  });

  test('the citizen wizard never uploads the attachments it collected, even on a clean submission', async ({ page }) => {
    // NOT in the manual, and the most serious finding in this block after D-C-20.
    //
    // OBSERVED: `performSubmit` (ts:2152-2208) posts `registerComplaint(payload)` and the payload has
    // no attachment field; nothing anywhere in this component calls
    // `complaintService.uploadAttachments`. The only caller is the OLD `complaint-form.component.ts:69`,
    // which this wizard replaced. So every document a citizen attaches — the bank's reply, the
    // statement, the disputed receipt — is validated, listed, counted against the 2 MB and 25 MB
    // limits, and then dropped on submit.
    //
    // Asserted by watching the network: a successful submission must be accompanied by an attachment
    // POST, and no such request is made. See D-C-22.
    const mobile = nextMobile();
    const attachmentPosts: string[] = [];
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/attachments/.test(req.url())) attachmentPosts.push(req.url());
    });

    await openWizardAtStep(page, mobile, {
      step: 6,
      formData: { phone: mobile, email: `qa_c47_att_${Date.now().toString(36)}@example.com` },
      attachmentMeta: [{ name: 'evidence.pdf', type: 'application/pdf', size: 4096 }],
    });

    await page.locator('button.btn-submit-complaint').click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    expect(attachmentPosts, 'no attachment upload is attempted on submission').toHaveLength(0);
  });

  test.fixme('attachments chosen on the upload step are uploaded when the complaint is filed', async ({ page }) => {
    // Required behaviour for D-C-22. The endpoint and the service method already exist
    // (`POST /api/v1/complaints/{id}/attachments`, complaint.service.ts:40); nothing calls them from
    // this wizard. NOT applied — `src/**` is shared with Sessions A and B (ruling 6).
    const mobile = nextMobile();
    const attachmentPosts: string[] = [];
    page.on('request', (req: any) => {
      if (req.method() === 'POST' && /\/attachments/.test(req.url())) attachmentPosts.push(req.url());
    });

    await openWizardAtStep(page, mobile, {
      step: 6,
      formData: { phone: mobile, email: `qa_c47_att2_${Date.now().toString(36)}@example.com` },
      attachmentMeta: [{ name: 'evidence.pdf', type: 'application/pdf', size: 4096 }],
    });

    await page.locator('button.btn-submit-complaint').click();
    await expect(page.locator('.success-heading')).toBeVisible({ timeout: 30000 });

    expect(attachmentPosts.length).toBeGreaterThan(0);
  });
});
