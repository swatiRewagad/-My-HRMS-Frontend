import { test, expect } from '../fixtures';
import { installCorsShim, assertBrowserCanReachApi } from './browser-api';

/**
 * Eligibility wizard — question simplification (UST421).
 *
 * ── ON THE AUDIT FINDING "does not test the live wizard" ────────────────────────────────────────
 *
 * REFUTED, with one important caveat. /public/eligibility-wizard is a real route
 * (app.routes.ts:347 → EligibilityWizardComponent) and these tests do drive it: the toggle is
 * .simplify-toggle at eligibility-wizard.component.html:25, the panel is .simplified-text at :39, and
 * the wizard advances through its four steps under test. Verified by driving it end to end.
 *
 * THE CAVEAT, which is what made the tests weak rather than absent: nothing asserted that the
 * simplified text DIFFERED from the original. `.simplified-text` becoming visible while containing
 * the same sentence — or the raw key 'wizard.q1_entity_type_simple', which is what an unseeded
 * translation renders as — satisfied every assertion. Simplification exists so a citizen who cannot
 * parse the formal wording can still answer, so "it is different, and it is not a key" IS the feature.
 * Those assertions are now present.
 *
 * TWO DIFFERENT WIZARDS EXIST, deliberately not conflated here:
 *   • /public/eligibility-wizard — the pre-login triage wizard. Its four questions and their
 *     simplifiedText keys are declared in eligibility-wizard.component.ts:52-98. This is what this
 *     spec covers.
 *   • /public/file-complaint — the post-login statutory maintainability gate, whose questions come
 *     from ELIGIBILITY_QUESTION_MASTER. Covered by eligibility-master.spec.ts and
 *     eligibility-sub-questions.spec.ts. Its simplify affordance is .simplify-btn/.simplified-box
 *     (file-complaint.component.html:74-78) — a DIFFERENT pair of class names, so a selector from
 *     this file would silently match nothing there.
 */

/** Q1's simplified wording, per wizard.q1_entity_type_simple in TRANSLATION_KEYS. */
const Q1_SIMPLE_FRAGMENT = /bank, NBFC, or payment company/i;

test.describe('Eligibility Wizard — Question Simplification (UST421)', () => {

  test.beforeEach(async ({ page }) => {
    await installCorsShim(page);
    await assertBrowserCanReachApi(page, '/public/eligibility-wizard');
    // The wizard renders its own hardcoded question set, but the LABELS come from the translation
    // bundle. Without this the questions render as 'wizard.q1_entity_type' and the assertions below
    // would compare one key against another.
    await expect(page.locator('.question-text')).not.toHaveText(/^wizard\./, { timeout: 10000 });
  });

  test('simplify icon (?) is visible for questions with simplified text', async ({ page }) => {
    // Question 1 has simplifiedText
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible({ timeout: 10000 });

    // Should have role="button" attribute
    await expect(simplifyToggle).toHaveAttribute('role', 'button');

    // Should have aria-expanded attribute
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'false');

    // Collapsed to begin with: the panel must not be showing before it is asked for.
    await expect(page.locator('.simplified-text')).not.toBeVisible();
  });

  test('clicking simplify icon shows simplified text alongside original', async ({ page }) => {
    // Wait for the question to render
    const questionText = page.locator('.question-text');
    await expect(questionText).toBeVisible({ timeout: 10000 });

    // Get original question text
    const originalText = (await questionText.textContent())?.trim() ?? '';
    expect(originalText).toBeTruthy();

    // Click the simplify toggle
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible();
    await simplifyToggle.click();

    // Simplified text should now be visible
    const simplifiedText = page.locator('.simplified-text');
    await expect(simplifiedText).toBeVisible();

    // AND it must actually be a simplification. Visibility alone was the whole assertion before,
    // which passed on an empty panel, on a panel echoing the formal wording, and on one showing the
    // raw key 'wizard.q1_entity_type_simple' because the translation was never seeded.
    const simple = (await simplifiedText.textContent())?.trim() ?? '';
    expect(simple.length, 'the simplified panel is empty').toBeGreaterThan(0);
    expect(simple, 'the simplified text is rendering its translation key').not.toMatch(/^wizard\./);
    expect(simple, 'the simplified text is identical to the formal wording').not.toBe(originalText);
    expect(simple, 'the simplified wording does not match the seeded plain-language text').toMatch(
      Q1_SIMPLE_FRAGMENT
    );

    // Original question text should still be visible (not replaced) — the citizen must be able to
    // see the wording their answer is recorded against.
    await expect(questionText).toBeVisible();
    expect((await questionText.textContent())?.trim()).toBe(originalText);

    // aria-expanded should now be true
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'true');
  });

  test('clicking simplify icon again hides simplified text', async ({ page }) => {
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible({ timeout: 10000 });

    // Open
    await simplifyToggle.click();
    await expect(page.locator('.simplified-text')).toBeVisible();
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'true');

    // Close
    await simplifyToggle.click();
    await expect(page.locator('.simplified-text')).not.toBeVisible();
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'false');
  });

  test('keyboard toggle (Enter/Space) works on simplify icon', async ({ page }) => {
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible({ timeout: 10000 });

    // Focus and press Enter
    await simplifyToggle.focus();
    await simplifyToggle.press('Enter');
    await expect(page.locator('.simplified-text')).toBeVisible();
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'true');

    // Press Space to close
    await simplifyToggle.press('Space');
    await expect(page.locator('.simplified-text')).not.toBeVisible();
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'false');
  });

  test('answer is recorded against original question (not simplified)', async ({ page }) => {
    // Q1 is a select — select an option
    const select = page.locator('.wizard-select');
    await expect(select).toBeVisible({ timeout: 10000 });

    const q1Text = (await page.locator('.question-text').textContent())?.trim() ?? '';

    // Show simplified text first
    const simplifyToggle = page.locator('.simplify-toggle');
    await simplifyToggle.click();
    await expect(page.locator('.simplified-text')).toBeVisible();

    // Select an answer
    await select.selectOption('BANK');
    expect(await select.inputValue()).toBe('BANK');

    // Click Next
    const nextBtn = page.locator('.btn-wizard-next');
    await expect(nextBtn).toBeEnabled();
    await nextBtn.click();

    // Should advance to Q2 (answer was recorded against original Q1 key). Asserted as "the question
    // CHANGED", polled: reading textContent straight after the click can catch the pre-render DOM and
    // report Q1's text as Q2's, which is how a wizard that never advanced would look like one that did.
    await expect(page.locator('.question-text')).not.toHaveText(q1Text, { timeout: 10000 });
    const q2Text = (await page.locator('.question-text').textContent())?.trim() ?? '';
    expect(q2Text.length).toBeGreaterThan(0);
    expect(q2Text).not.toMatch(/^wizard\./);

    // The simplified panel must not carry over: it belongs to the question it was opened on, and a
    // stale panel would show Q1's plain-language wording beside Q2.
    await expect(page.locator('.simplified-text')).not.toBeVisible();
    await expect(page.locator('.simplify-toggle')).toHaveAttribute('aria-expanded', 'false');

    // Going Back must restore Q1 with the answer still selected, or the recorded answer was lost.
    await page.locator('.btn-wizard-back').click();
    await expect(page.locator('.question-text')).toHaveText(q1Text, { timeout: 10000 });
    expect(await page.locator('.wizard-select').inputValue()).toBe('BANK');
  });

  test('question without simplifiedText does not show the ? icon', async ({ page }) => {
    // Navigate to Q3 (date question, no simplifiedText)
    // First answer Q1
    const select = page.locator('.wizard-select');
    await expect(select).toBeVisible({ timeout: 10000 });
    const q1Text = (await page.locator('.question-text').textContent())?.trim() ?? '';
    await select.selectOption('BANK');
    await page.locator('.btn-wizard-next').click();
    await expect(page.locator('.question-text')).not.toHaveText(q1Text, { timeout: 10000 });

    // Answer Q2
    const q2Text = (await page.locator('.question-text').textContent())?.trim() ?? '';
    // Q2 DOES have a simplified version, so the icon is present here — asserted, so that the absence
    // check below is known to be about Q3 rather than about the icon never rendering at all.
    await expect(page.locator('.simplify-toggle')).toBeVisible();
    await page.locator('.wizard-radio').first().click();
    await page.locator('.btn-wizard-next').click();
    await expect(page.locator('.question-text')).not.toHaveText(q2Text, { timeout: 10000 });

    // Now on Q3 (date question) — no simplifiedText
    await expect(page.locator('.wizard-date-input')).toBeVisible();
    await expect(page.locator('.simplify-toggle')).toHaveCount(0);
    await expect(page.locator('.simplified-text')).not.toBeVisible();
  });
});
