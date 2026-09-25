/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual block 46 (the Declaration screen of the complaint wizard).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3438-3512.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * ═══ THIS IS STEP 5, NOT STEP 7 ═══
 * The brief places Declaration & Submission at step 7 of a 7-step wizard. The running code has SIX
 * steps (`totalSteps = 6`, ts:133) and the declaration is step **5** (`@if (currentStep() === 5)`,
 * html:1022); Review & Submit is step 6. Recorded in findings-QA-C.md §0.
 *
 * ═══ WHAT IS ALREADY COVERED ELSEWHERE, AND IS NOT REPEATED HERE ═══
 * `consent-dpdp.spec.ts` (UST5, 11 tests) covers the DPDP consent at LOGIN and on the legacy form:
 * server-side rejection of an intake with no declaration, the localised notice, `consentGiven` on
 * send-otp, and the legacy form's submit gate. None of that is this screen. The wizard's step-5
 * declaration is a DIFFERENT, statutory pair of checkboxes (clause 10(2)), held in
 * `declarationChecked`/`declaration2Checked` rather than in the consent payload, and `consent-dpdp`
 * explicitly does not reach it — its one wizard test asserts only that the route needs a session.
 * So there is no duplication, and the two must not be conflated: DPDP consent is data-protection,
 * this is a statement of truth and a limitation statement.
 *
 * ═══ TWO CHECKBOXES, ONE ERROR KEY — WHICH IS THE INTERESTING PART ═══
 * `validateCurrentStep` step 5 (ts:1707-1708) is a single line:
 *     if (!declarationChecked || !declaration2Checked) validationErrors['declaration'] = '...'
 * One message for two independent statements, so a citizen who ticks one and misses the other is
 * told "You must accept all declarations" with nothing indicating WHICH. Tested as observed and
 * raised as D-C-17, because each checkbox is a separate legal assertion and the fix (per-checkbox
 * errors) is a product decision.
 *
 * ═══ THE MANUAL'S ERROR STRING IS NOT THE PRODUCT'S ═══
 * Manual: "Response is mandatory." Product: "You must accept all declarations to proceed"
 * (ts:1708). The real string is asserted; the manual's is logged with the other message
 * discrepancies. The product's wording is the better one here — it says what to do.
 *
 * ═══ THE DECLARATION TEXT IS HARDCODED ENGLISH, UNLIKE ITS OWN HEADING ═══
 * `form.decl_heading` goes through `| translate` (html:1024), but both declaration bodies are literal
 * English in the template (html:1029-1032, 1040). On a portal that serves Hindi and Punjabi — and
 * whose login consent notice IS translated, as consent-dpdp.spec.ts:105 asserts — a statutory
 * declaration the citizen must legally affirm is presented only in English. That is D-C-18 and it is
 * the most serious thing in this block: consent to a statement one cannot read is questionable
 * consent. Written as a `fixme` per ruling 2.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - The seeded draft defaults BOTH boxes to true (`helpers-submission-c.ts:120-121`), because every
 *    other spec needs to get past this screen. Every test here therefore seeds them false explicitly.
 *    Forgetting that makes the "cannot proceed while unticked" tests pass for the wrong reason.
 *  - The checkboxes are wrapped by their `<label>`, so clicking the label toggles them. Addressing by
 *    `input[name="declaration"]` and using `.check()` is unambiguous; clicking the text is not.
 *  - The back button is `.btn-go-back` labelled "Back" (html:1266) — there is no "Previous".
 */

import { test, expect } from '../fixtures';
import { openWizardAtStep, sessionCMobile } from './helpers-submission-c';
import { deleteOtpAttempts } from '../utils/test-data';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

test.afterAll(async () => {
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

const DECL_1 = 'input[name="declaration"]';
const DECL_2 = 'input[name="declaration2"]';
const DECL_ERROR = '.field-error';
const NEXT = 'button.btn-next';
const BACK = 'button.btn-go-back';

/** The step-5 error the component actually sets (ts:1708), not the manual's "Response is mandatory." */
const REQUIRED_MESSAGE = 'You must accept all declarations to proceed';

/**
 * Lands on the declaration screen with both boxes in a chosen state.
 *
 * Defaulting to UNTICKED is deliberate and the opposite of the shared helper's default: this spec is
 * about the gate, and a screen that arrives pre-accepted cannot test one.
 */
async function openDeclaration(
  page: any, opts: { first?: boolean; second?: boolean } = {},
): Promise<void> {
  await openWizardAtStep(page, nextMobile(), {
    step: 5,
    declarationChecked: opts.first ?? false,
    declaration2Checked: opts.second ?? false,
  });
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The screen and its two checkboxes
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C46 — the declaration screen presents two checkboxes that must both be accepted', () => {

  test('both checkbox fields are present on the declaration screen', async ({ page }) => {
    await openDeclaration(page);

    await expect(page.locator(DECL_1)).toBeVisible();
    await expect(page.locator(DECL_2)).toBeVisible();
    await expect(page.locator('.decl-item')).toHaveCount(2);
  });

  test('the screen is reached by completing the step before it', async ({ page }) => {
    // MANUAL: "post filling the mandatory fields in the form, the user is navigated to the declaration
    // screen". Driven from step 4 so the transition is real rather than seeded.
    await openWizardAtStep(page, nextMobile(), {
      step: 4, declarationChecked: false, declaration2Checked: false,
    });
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });

    await page.locator(NEXT).first().click();

    await expect(page.locator(DECL_1)).toBeVisible();
    await expect(page.locator('.decl-heading')).toBeVisible();
  });

  test('both checkboxes start unticked and can each be selected', async ({ page }) => {
    await openDeclaration(page);

    await expect(page.locator(DECL_1)).not.toBeChecked();
    await expect(page.locator(DECL_2)).not.toBeChecked();

    await page.locator(DECL_1).check();
    await expect(page.locator(DECL_1)).toBeChecked();
    await expect(page.locator(DECL_2), 'the two declarations are independent').not.toBeChecked();

    await page.locator(DECL_2).check();
    await expect(page.locator(DECL_2)).toBeChecked();
  });

  test('a ticked checkbox can be unticked again', async ({ page }) => {
    // Not in the manual. The boxes are two-way bound (`[(ngModel)]`, html:1027/1038) so this should
    // hold — worth proving, because a citizen who changes their mind and cannot must abandon the form.
    await openDeclaration(page, { first: true, second: true });

    await page.locator(DECL_1).uncheck();

    await expect(page.locator(DECL_1)).not.toBeChecked();
    await page.locator(NEXT).first().click();
    await expect(page.locator(DECL_ERROR)).toContainText(REQUIRED_MESSAGE);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The gate — including the case the manual does not think to try
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C46 — the step cannot be passed until both declarations are accepted', () => {

  test('with neither declaration accepted the step is blocked and says so', async ({ page }) => {
    await openDeclaration(page);

    await page.locator(NEXT).first().click();

    await expect(page.locator(DECL_ERROR)).toContainText(REQUIRED_MESSAGE);
    // Still on step 5: the review screen must not have appeared.
    await expect(page.locator(DECL_1)).toBeVisible();
    await expect(page.locator('.rs-section')).toHaveCount(0);
  });

  test('accepting only the truth declaration is not enough', async ({ page }) => {
    // The manual only tries "both unselected". A citizen realistically ticks one and misses the other,
    // and this is where a `||` written as `&&` would let a complaint through with the clause-10(2)
    // limitation statement unaffirmed.
    await openDeclaration(page, { first: true, second: false });

    await page.locator(NEXT).first().click();

    await expect(page.locator(DECL_ERROR)).toContainText(REQUIRED_MESSAGE);
    await expect(page.locator('.rs-section')).toHaveCount(0);
  });

  test('accepting only the clause 10(2) declaration is not enough', async ({ page }) => {
    await openDeclaration(page, { first: false, second: true });

    await page.locator(NEXT).first().click();

    await expect(page.locator(DECL_ERROR)).toContainText(REQUIRED_MESSAGE);
    await expect(page.locator('.rs-section')).toHaveCount(0);
  });

  test('the error does not say WHICH declaration is missing', async ({ page }) => {
    // Asserted so the defect is a recorded fact rather than an impression. One `validationErrors`
    // key serves two independent legal statements (ts:1707-1708), so the citizen who ticked (i) and
    // missed (ii) is told to "accept all declarations" with nothing pointing at (ii).
    // See findings-QA-C.md D-C-17.
    await openDeclaration(page, { first: true, second: false });

    await page.locator(NEXT).first().click();

    const error = page.locator(DECL_ERROR);
    await expect(error).toBeVisible();
    // A single message for the whole screen, not one beside the offending checkbox.
    await expect(error).toHaveCount(1);
    await expect(error).not.toContainText('10 (2)');
    await expect(error).not.toContainText('(ii)');
  });

  test.fixme('the error identifies which declaration has not been accepted', async ({ page }) => {
    // THE DEFECT MARKER for D-C-17. Required behaviour: the unaccepted declaration is identified, as
    // every other field on this wizard does. Fix is a second error key plus a span per `.decl-item`.
    // NOT applied — `src/**` is shared with Sessions A and B (ruling 6).
    await openDeclaration(page, { first: true, second: false });

    await page.locator(NEXT).first().click();

    await expect(page.locator('.decl-item').nth(1).locator('.field-error')).toBeVisible();
  });

  test('with both accepted the step advances to the review screen', async ({ page }) => {
    // MANUAL: "when user clicks on the next button, the user is navigated to the complaint preview
    // screen". Step 6 is that preview — there is no separate 7th screen.
    await openDeclaration(page, { first: true, second: true });

    await page.locator(NEXT).first().click();

    await expect(page.locator('.rs-section').first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('button.btn-submit-complaint')).toBeVisible();
  });

  test('the error clears once the missing declaration is accepted', async ({ page }) => {
    await openDeclaration(page, { first: true, second: false });

    await page.locator(NEXT).first().click();
    await expect(page.locator(DECL_ERROR)).toBeVisible();

    await page.locator(DECL_2).check();
    await page.locator(NEXT).first().click();

    await expect(page.locator('.rs-section').first()).toBeVisible({ timeout: 15000 });
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The text itself — what the citizen is actually affirming
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C46 — the declaration text states both statutory affirmations', () => {

  test('the first declaration affirms the information is true and not misrepresented', async ({ page }) => {
    // MANUAL requires "Information furnished is true and correct." The product says more than that —
    // it adds the non-concealment limb — so the assertion is on substance, not on a verbatim string a
    // legal review would rightly reword.
    await openDeclaration(page);

    const first = page.locator('.decl-item').first().locator('.decl-text');
    await expect(first).toContainText('the information furnished above is true and correct');
    await expect(first).toContainText('not concealed or misrepresented any fact');
  });

  test('the second declaration states the one-year limitation under clause 10(2)', async ({ page }) => {
    // MANUAL requires "Complaint filed within one year as per clause 10(2)." This is the limitation
    // affirmation the whole maintainability regime rests on, so the clause reference must be present
    // and must be 10(2) — a wrong clause number here misstates what the citizen is affirming.
    await openDeclaration(page);

    const second = page.locator('.decl-item').nth(1).locator('.decl-text');
    await expect(second).toContainText('before the expiry of a period of one year');
    await expect(second).toContainText('clause 10 (2)');
  });

  test('each declaration text belongs to its own checkbox', async ({ page }) => {
    // Not in the manual. Two checkboxes and two texts in separate `.decl-item`s: if the label/input
    // pairing were wrong, a citizen would tick a box believing it affirmed the other statement.
    await openDeclaration(page);

    const firstItem = page.locator('.decl-item').first();
    await expect(firstItem.locator('input[type="checkbox"]')).toHaveAttribute('name', 'declaration');
    await expect(firstItem.locator('.decl-text')).toContainText('true and correct');

    const secondItem = page.locator('.decl-item').nth(1);
    await expect(secondItem.locator('input[type="checkbox"]')).toHaveAttribute('name', 'declaration2');
    await expect(secondItem.locator('.decl-text')).toContainText('clause 10 (2)');
  });

  test('clicking the declaration text toggles its checkbox, as the wrapping label implies', async ({ page }) => {
    // Accessibility as much as convenience: the checkbox is small and the text is the obvious target.
    await openDeclaration(page);

    await page.locator('.decl-item').first().locator('.decl-text').click();

    await expect(page.locator(DECL_1)).toBeChecked();
    await expect(page.locator(DECL_2)).not.toBeChecked();
  });

  test.fixme('the declaration text is served from translations rather than hardcoded English', async ({ page }) => {
    // MANUAL does not ask, but the project's own standard does: the login consent notice is asserted
    // to come from translations (consent-dpdp.spec.ts:105) precisely so a citizen consents in their
    // own language.
    //
    // OBSERVED: `form.decl_heading` is translated (html:1024) but BOTH declaration bodies are literal
    // English in the template (html:1029-1032, 1040). On a portal serving Hindi and Punjabi, the two
    // statements the citizen must legally affirm are English-only — while the heading above them
    // localises correctly, which is the tell that this was an omission.
    //
    // Asserting the required behaviour per ruling 2. The check is for an i18n KEY rather than for
    // translated copy, because the copy is legal text nobody here should invent.
    // See findings-QA-C.md D-C-18.
    await openDeclaration(page);

    const html = await page.locator('.decl-item').first().innerHTML();
    expect(html, 'the declaration body must be bound to a translation key, not written inline')
      .not.toContain('the information furnished above is true and correct');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Navigation off the screen, and what survives it
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C46 — navigating away from the declaration preserves what the citizen accepted', () => {

  test('the back button returns to the representative screen', async ({ page }) => {
    // MANUAL: "when user clicks on back button, the user is navigated back to the previous screen".
    // The previous screen is step 4, the representative authorisation — not the complaint details the
    // 7-step reading of the wizard would predict.
    await openDeclaration(page);

    await page.locator(BACK).first().click();

    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
  });

  test('going back does not require the declarations to be accepted first', async ({ page }) => {
    // A gate on Back would trap a citizen who wants to correct an earlier answer precisely BECAUSE
    // they cannot honestly affirm the declaration yet.
    await openDeclaration(page);

    await expect(page.locator(DECL_1)).not.toBeChecked();
    await page.locator(BACK).first().click();

    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await expect(page.locator(DECL_ERROR)).toHaveCount(0);
  });

  test('an accepted declaration is still accepted after going back and forward again', async ({ page }) => {
    // `declarationChecked` is a plain component field, not part of `formData`, so it is the kind of
    // state that quietly resets on navigation. A citizen who ticks, steps back to fix a typo and
    // returns must not find the boxes cleared without being told.
    await openDeclaration(page, { first: true, second: true });

    await page.locator(BACK).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });

    await page.locator(NEXT).first().click();

    await expect(page.locator(DECL_1)).toBeChecked();
    await expect(page.locator(DECL_2)).toBeChecked();
  });

  test('the save button is present on the declaration screen', async ({ page }) => {
    // MANUAL (lines 3520-3530, the head of block 47): the save button is on every screen of the form.
    // Asserted here for this screen; block 47 covers the draft lifecycle itself.
    await openDeclaration(page);

    await expect(page.locator('button.btn-save-link')).toBeVisible();
  });
});
