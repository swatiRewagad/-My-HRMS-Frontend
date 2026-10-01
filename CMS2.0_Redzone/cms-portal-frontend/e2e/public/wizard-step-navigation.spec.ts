/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4, Session C — manual block 56: the "tabbed interface" for the complaint form sections.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * ═══ THE BLOCK DESCRIBES A CONTROL THAT DOES NOT EXIST IN THE FORM IT NAMES ═══
 * Block 56 asks for a **tabbed interface** on the **preview screen**, listing **seven** sections:
 *   Regulated Entity Name / Complaint Eligibility Checks / Complainant Details /
 *   Regulated Entity Details / Complaint Details / Authorised Representative /
 *   Declaration & Submission
 *
 * What the product has is a **step progress bar** with **six** steps (`stepTitles`, ts:134-141):
 *   Complainant Details / Regulated Entity Details / Complaint Details /
 *   Representative Authorisation / Declaration / Review and Submit
 *
 * Three separate mismatches, and they pull in different directions, so they are recorded separately
 * rather than as one "off by one":
 *   1. The manual's first two sections — "Regulated Entity Name" and "Complaint Eligibility Checks"
 *      — are the **eligibility phase**, a different phase of the component
 *      (`@if (phase() === 'eligibility')`, html:18) which the step bar is not rendered in at all
 *      (`@if (phase() === 'form')`, html:419). They are real screens; they are simply not steps.
 *   2. The manual's last section, "Declaration & Submission", is **two** steps here: 5 Declaration
 *      and 6 Review and Submit.
 *   3. Net: 7 claimed, 6 real, and the 6 are not a subset of the 7 — "Review and Submit" has no
 *      counterpart in the manual's list at all.
 * So the manual's count is not wrong by one item; its model of the journey differs from the
 * product's. See D-C-40. This is a requirements defect, not a code defect, and the tests below
 * assert the SIX steps that exist while recording the manual's seven as the claim.
 *
 * ═══ IT IS ALSO NOT A TAB INTERFACE, WHICH CHANGES WHAT "CLICK ANY TAB" CAN MEAN ═══
 * The container is `role="navigation" aria-label="Form progress"` (html:439). There is no
 * `role="tablist"`, no `role="tab"`, no `aria-selected` and no `aria-controls` anywhere in the step
 * bar. Assistive technology is told this is a set of links, not a tab set — so the manual's
 * "tabbed interface" cases cannot be verified as written, and a test asserting `getByRole('tab')`
 * would find nothing. Recorded as D-C-39; the tests assert the navigation semantics that DO exist.
 *
 * And the manual's "the user is able to click on **any** tabs" is false by design: `goToStep`
 * (ts:1868-1872) refuses any step above `highestStepReached()`, and only reached steps carry the
 * `.clickable` class. That is correct behaviour — jumping to Declaration without filling
 * Complainant Details would submit an empty complaint — so the requirement is the thing that is
 * wrong, and the test asserts the guard rather than the manual's claim.
 *
 * ═══ THE FINDING NEITHER BLOCK ASKS FOR, AND THE REASON THIS SPEC EXISTS AT ALL ═══
 * `form.step1_title` … `form.step6_title` and `form.step_label` **all exist in the translation
 * bundle and Hindi translates every one of them** — and the component ignores them, rendering the
 * hardcoded English `stepTitles` array plus a literal `Step {{ i + 1 }}` (html:458-459). So the
 * navigation is English for a Hindi user even though the Hindi strings are sitting in the bundle,
 * already written, already served. That is a different and cheaper defect than D-C-30/D-C-38 (where
 * the Punjabi strings are genuinely missing): here the translation exists and is simply not wired
 * up. See D-C-41.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - `openWizardAtStep` sets BOTH `currentStep` and `highestStepReached` to the seeded step
 *    (ts:1965-1967), so seeding step 6 makes all six steps clickable and seeding step 3 makes only
 *    1-3 clickable. That is what makes the forward-guard testable without clicking through the
 *    whole wizard — but it also means a test that seeds step 6 can never observe the guard.
 *  - The step bar renders on EVERY form step including step 6, so the manual's "tabs are visible
 *    while previewing" case passes. Its "visible while filling the form" case passes too. Both are
 *    asserted, because the two together are the only part of block 56 the product satisfies exactly
 *    as written.
 *  - `.step-label-num` is the literal "Step N" and `.step-label-title` is the section name. Both are
 *    inside the same `.step-item`; address them separately or the text assertions read "Step
 *    1Complainant Details".
 *  - There is no `.step-connector` after the last step (`@if (i < stepTitles.length - 1)`,
 *    html:455), so connectors are one fewer than steps. Asserting an equal count fails.
 *
 * WHAT CANNOT BE PROVEN HERE:
 *  - The login preamble (block 56's first case) is the citizen OTP journey, owned by the login spec.
 *  - "Tabs visible on the preview screen" is asserted for the step bar. Whether the BA meant the
 *    step bar or a genuine tab set inside the review screen cannot be resolved from the manual; both
 *    readings are recorded in the findings so they can answer it.
 */

import { test, expect } from '../fixtures';
import { API_BASE, openWizardAtStep, sessionCMobile, validFormData } from './helpers-submission-c';

const STEP_BAR = '.step-bar';
const STEP_ITEM = '.step-item';
const STEP_NUM = '.step-label-num';
const STEP_TITLE = '.step-label-title';
const STEP_HEADER = '.step-header';
const CONNECTOR = '.step-connector';

/**
 * The six steps the component declares, in order (ts:134-141).
 *
 * Written here as the EXPECTATION rather than read from the server, unlike every limit in the other
 * Session C specs — deliberately, and the difference is worth stating. A configured number must be
 * read from configuration (standing ruling 1). A journey's step order is not configuration: it is
 * the shape of the form, and a silent change to it is exactly what this should catch.
 */
const STEPS = [
  'Complainant Details',
  'Regulated Entity Details',
  'Complaint Details',
  'Representative Authorisation',
  'Declaration',
  'Review and Submit',
];

/** What block 56 claims the sections are. Present only so the delta can be reported. */
const MANUAL_SECTIONS = [
  'Regulated Entity Name',
  'Complaint Eligibility Checks',
  'Complainant Details',
  'Regulated Entity Details',
  'Complaint Details',
  'Authorised Representative',
  'Declaration & Submission',
];

async function openAtStep(page: any, step: number) {
  await openWizardAtStep(page, sessionCMobile(), { step, formData: validFormData() });
  await expect(page.locator(STEP_BAR)).toBeVisible({ timeout: 20000 });
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C56 — what the navigation is, and what it is not
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C56 — the form section navigation and the sections it offers', () => {

  test('the navigation offers exactly the six steps the component declares, in order', async ({ page }) => {
    await openAtStep(page, 3);

    const titles = await page.locator(`${STEP_ITEM} ${STEP_TITLE}`).allTextContents();
    expect(titles.map((t: string) => t.trim()),
      'the journey has six sections and their order is the form itself').toEqual(STEPS);
  });

  test('the navigation does not offer the seven sections the manual lists', async ({ page }) => {
    // Not a failure of the product — a divergence between two models of the journey, recorded so a
    // tester following block 56 does not raise six false defects. See D-C-40.
    await openAtStep(page, 3);

    const titles = (await page.locator(`${STEP_ITEM} ${STEP_TITLE}`).allTextContents())
      .map((t: string) => t.trim());

    const claimedButAbsent = MANUAL_SECTIONS.filter(s => !titles.includes(s));
    const presentButUnclaimed = titles.filter((t: string) => !MANUAL_SECTIONS.includes(t));

    // The manual's first two are the eligibility phase; its last is two steps here; and the
    // product's "Review and Submit" appears nowhere in the manual's list.
    expect(claimedButAbsent.sort(), 'the manual claims four sections the step bar does not have')
      .toEqual([
        'Authorised Representative',
        'Complaint Eligibility Checks',
        'Declaration & Submission',
        'Regulated Entity Name',
      ]);
    expect(presentButUnclaimed.sort(), 'and the step bar has three the manual never names')
      .toEqual(['Declaration', 'Representative Authorisation', 'Review and Submit']);
  });

  test('each step is numbered, and the numbers run 1 to 6 without a gap', async ({ page }) => {
    await openAtStep(page, 3);

    const numbers = await page.locator(`${STEP_ITEM} ${STEP_NUM}`).allTextContents();
    expect(numbers.map((n: string) => n.trim()))
      .toEqual(STEPS.map((_, i) => `Step ${i + 1}`));
  });

  test('the steps are joined by one fewer connector than there are steps', async ({ page }) => {
    // The sequence is drawn, not merely listed — which is the visual part of "displayed in
    // sequence". One fewer, because nothing follows the last step (html:455).
    await openAtStep(page, 3);

    await expect(page.locator(STEP_ITEM)).toHaveCount(STEPS.length);
    await expect(page.locator(CONNECTOR)).toHaveCount(STEPS.length - 1);
  });

  test('the navigation is announced as navigation, not as a tab set', async ({ page }) => {
    // Block 56 calls this a "tabbed interface" throughout. It is not one, and the difference is
    // real for assistive technology: a tab set announces which panel is selected, a nav does not.
    await openAtStep(page, 3);

    await expect(page.locator(STEP_HEADER)).toHaveAttribute('role', 'navigation');
    await expect(page.locator(STEP_HEADER)).toHaveAttribute('aria-label', 'Form progress');

    // Asserted as absent rather than assumed: if tab semantics are added later this fails and the
    // manual's wording becomes correct.
    await expect(page.getByRole('tablist')).toHaveCount(0);
    await expect(page.getByRole('tab')).toHaveCount(0);
    await expect(page.locator(`${STEP_ITEM}[aria-selected]`)).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C56 — visibility through the journey
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C56 — where the navigation is visible', () => {

  test('the navigation is visible on every step of the form, including the review step', async ({ page }) => {
    // Block 56's "visible while filling the form" and "visible while previewing the form" are the
    // two cases the product satisfies exactly as written. Table-driven over all six steps rather
    // than spot-checked, because "on every step" is the actual requirement.
    for (const step of [1, 2, 3, 4, 5, 6]) {
      await openAtStep(page, step);
      await expect(page.locator(STEP_BAR), `the step bar must be visible on step ${step}`).toBeVisible();
      await expect(page.locator(STEP_ITEM)).toHaveCount(STEPS.length);
    }
  });

  test('the navigation is absent during the eligibility phase, which is where the manual puts two of its sections', async ({ page }) => {
    // The mechanism behind D-C-40: "Regulated Entity Name" and "Complaint Eligibility Checks" are
    // real screens in a DIFFERENT phase, and the step bar is gated on `phase() === 'form'`
    // (html:419) so it is not drawn there at all. A tester looking for seven tabs on the
    // eligibility screens finds none, not five.
    await page.goto(`${process.env['APP_BASE_URL'] || 'http://localhost:4202'}/public/file-complaint`);
    await page.waitForLoadState('networkidle');

    // The eligibility phase is the landing state of the component for a fresh session.
    await expect(page.locator(STEP_BAR)).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C56 — clicking between sections
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C56 — navigating by clicking a step', () => {

  test('the current step is highlighted, and only the current step', async ({ page }) => {
    await openAtStep(page, 3);

    const current = page.locator(`${STEP_ITEM}.current`);
    await expect(current, 'exactly one step may be current').toHaveCount(1);
    await expect(current.locator(STEP_TITLE)).toHaveText(STEPS[2]);
  });

  test('clicking an earlier step navigates to it and moves the highlight', async ({ page }) => {
    await openAtStep(page, 3);

    await page.locator(STEP_ITEM).nth(0).click();

    const current = page.locator(`${STEP_ITEM}.current`);
    await expect(current).toHaveCount(1);
    await expect(current.locator(STEP_TITLE)).toHaveText(STEPS[0]);
  });

  test('navigating between steps shows the data already entered on each', async ({ page }) => {
    const mobile = sessionCMobile();
    await openWizardAtStep(page, mobile, {
      step: 3,
      formData: validFormData({ firstName: 'Navigable', lastName: 'Citizen', address: '9 Sequence Road, Mumbai' }),
    });
    await expect(page.locator(STEP_BAR)).toBeVisible({ timeout: 20000 });

    // Step 1 holds the complainant's name; step 3 holds the complaint text.
    await page.locator(STEP_ITEM).nth(0).click();
    await expect(page.locator('input[name="firstName"]')).toHaveValue('Navigable');
    // `address` is a textarea (html:604), not an input — the first draft of this test assumed an input
    // and failed with "element(s) not found", which reads like a missing field but was a wrong selector.
    await expect(page.locator('textarea[name="address"]')).toHaveValue('9 Sequence Road, Mumbai');

    await page.locator(STEP_ITEM).nth(2).click();
    await expect(page.locator('textarea[name="complaintText"]'))
      .toHaveValue(validFormData()['complaintText'] as string);
  });

  test('a citizen can go back to an earlier step, change an answer, and return with the change kept', async ({ page }) => {
    await openAtStep(page, 3);

    await page.locator(STEP_ITEM).nth(0).click();
    const firstName = page.locator('input[name="firstName"]');
    await firstName.fill('Edited');

    await page.locator(STEP_ITEM).nth(2).click();
    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible();

    await page.locator(STEP_ITEM).nth(0).click();
    await expect(firstName, 'an edit made after navigating back must survive navigating away again')
      .toHaveValue('Edited');
  });

  test('steps not yet reached are neither clickable nor navigable', async ({ page }) => {
    // Block 56 says the user "is able to click on ANY tabs". They cannot, and must not: `goToStep`
    // (ts:1868) refuses anything above `highestStepReached()`. The requirement is what is wrong
    // here — jumping to Declaration from step 3 would submit a form nobody filled in. Asserted as
    // the guard, so the day it is loosened this test names what was lost.
    await openAtStep(page, 3);

    const reached = page.locator(`${STEP_ITEM}.clickable`);
    await expect(reached, 'the three reached steps are the clickable ones').toHaveCount(3);

    const future = page.locator(STEP_ITEM).nth(5);
    await expect(future).not.toHaveClass(/clickable/);

    await future.click();
    // Still on step 3: the click was accepted by the DOM and refused by the component.
    const current = page.locator(`${STEP_ITEM}.current`);
    await expect(current.locator(STEP_TITLE)).toHaveText(STEPS[2]);
  });

  test('from the review step every earlier step is reachable, so nothing is a dead end', async ({ page }) => {
    await openAtStep(page, 6);
    await expect(page.locator(`${STEP_ITEM}.clickable`)).toHaveCount(STEPS.length);

    for (const index of [0, 1, 2, 3, 4]) {
      await page.locator(STEP_ITEM).nth(index).click();
      await expect(page.locator(`${STEP_ITEM}.current`).locator(STEP_TITLE)).toHaveText(STEPS[index]);
    }
  });

  test('steps already completed are marked as completed rather than merely visited', async ({ page }) => {
    await openAtStep(page, 3);

    // `.active` is set for `i < highestStepReached()`, and a tick replaces the number on any
    // completed step that is not the current one.
    await expect(page.locator(`${STEP_ITEM}.active`)).toHaveCount(3);
    await expect(page.locator(`${STEP_ITEM} .pi-check`).first()).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// QA-C56 — the navigation in the citizen's own language
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C56 — the step labels in Hindi and Punjabi', () => {

  async function bundle(request: any, locale: string): Promise<Record<string, string>> {
    const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
    expect(res.ok(), `the ${locale} bundle must be served`).toBeTruthy();
    const payload = await res.json();
    return (payload?.data ?? payload ?? {}) as Record<string, string>;
  }

  const KEYS = [1, 2, 3, 4, 5, 6].map(n => `form.step${n}_title`).concat('form.step_label');

  test('a translated label exists for every step, and Hindi translates all of them', async ({ request }) => {
    const en = await bundle(request, 'en');
    const hi = await bundle(request, 'hi');

    for (const key of KEYS) {
      expect(en[key], `${key} must exist`).toBeTruthy();
      expect(hi[key], `${key} must exist in Hindi`).toBeTruthy();
    }
    expect(KEYS.filter(k => hi[k] === en[k]),
      'every step label is translated into Hindi — so the strings are not the missing piece').toEqual([]);
  });

  test.fixme('the step navigation renders the translated labels', async ({ page }) => {
    // THE REQUIREMENT. The translations exist, are served, and Hindi is complete — and the component
    // renders the hardcoded English `stepTitles` array (ts:134-141) plus a literal `Step {{ i + 1 }}`
    // (html:458-459) instead. So a Hindi citizen navigates a Hindi form through an English
    // contents list, for no reason other than six unused bundle keys.
    //
    // Distinct from D-C-30 and D-C-38, where the Punjabi strings genuinely do not exist: this is a
    // wiring gap, and the cheapest fix in Session C's findings.
    await openAtStep(page, 3);
    await page.evaluate(() => localStorage.setItem('cms_locale', 'hi'));
    await page.reload();
    await expect(page.locator(STEP_BAR)).toBeVisible({ timeout: 20000 });

    const titles = await page.locator(`${STEP_ITEM} ${STEP_TITLE}`).allTextContents();
    expect(titles.map((t: string) => t.trim()),
      'the Hindi bundle has all six of these and they must be used').not.toEqual(STEPS);
  });

  test('the step labels are hardcoded English, and the unused translated keys prove it is a wiring gap', async ({ page, request }) => {
    // The companion recording reality. Two assertions, because either one alone is ambiguous: the
    // rendered text is English AND the Hindi strings exist. Together they distinguish "not
    // translated" from "translated and ignored", which is the whole finding.
    await openAtStep(page, 3);

    const titles = (await page.locator(`${STEP_ITEM} ${STEP_TITLE}`).allTextContents())
      .map((t: string) => t.trim());
    expect(titles, 'MEASURED: the hardcoded English array is what renders').toEqual(STEPS);

    const hi = await bundle(request, 'hi');
    const translated = KEYS.filter(k => hi[k] && hi[k] !== '');
    expect(translated.length,
      'and all seven Hindi strings exist, unused — if these disappear the finding changes shape')
      .toBe(KEYS.length);
  });

  test.fixme('Punjabi translates the step labels', async ({ request }) => {
    // The second half of the same story, and the reason fixing the wiring is not sufficient on its
    // own: all seven keys are English in the `pa` bundle. Wire the component up today and a Hindi
    // citizen is served correctly while a Punjabi citizen sees no change at all.
    const en = await bundle(request, 'en');
    const pa = await bundle(request, 'pa');

    expect(KEYS.filter(k => pa[k] === en[k]),
      'the form navigation must be readable in Punjabi').toEqual([]);
  });

  test('all seven Punjabi step-label strings are presently English', async ({ request }) => {
    const en = await bundle(request, 'en');
    const pa = await bundle(request, 'pa');

    expect(KEYS.filter(k => pa[k] === en[k]).sort(),
      'MEASURED: every step label falls through to English in Punjabi').toEqual([...KEYS].sort());
    expect(Object.keys(pa).length,
      'the pa bundle is real, so this is a per-key gap and not a missing locale').toBeGreaterThan(100);
  });
});
