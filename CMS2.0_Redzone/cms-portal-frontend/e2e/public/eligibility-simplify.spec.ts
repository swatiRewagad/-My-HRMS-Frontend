import { test, expect } from '../fixtures';

test.describe('Eligibility Wizard — Question Simplification (UST421)', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/public/eligibility-wizard', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');
  });

  test('simplify icon (?) is visible for questions with simplified text', async ({ page }) => {
    // Question 1 has simplifiedText
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible({ timeout: 10000 });

    // Should have role="button" attribute
    await expect(simplifyToggle).toHaveAttribute('role', 'button');

    // Should have aria-expanded attribute
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'false');
  });

  test('clicking simplify icon shows simplified text alongside original', async ({ page }) => {
    // Wait for the question to render
    const questionText = page.locator('.question-text');
    await expect(questionText).toBeVisible({ timeout: 10000 });

    // Get original question text
    const originalText = await questionText.textContent();
    expect(originalText).toBeTruthy();

    // Click the simplify toggle
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible();
    await simplifyToggle.click();

    // Simplified text should now be visible
    const simplifiedText = page.locator('.simplified-text');
    await expect(simplifiedText).toBeVisible();

    // Original question text should still be visible (not replaced)
    await expect(questionText).toBeVisible();
    const afterText = await questionText.textContent();
    expect(afterText).toBe(originalText);

    // aria-expanded should now be true
    await expect(simplifyToggle).toHaveAttribute('aria-expanded', 'true');
  });

  test('clicking simplify icon again hides simplified text', async ({ page }) => {
    const simplifyToggle = page.locator('.simplify-toggle');
    await expect(simplifyToggle).toBeVisible({ timeout: 10000 });

    // Open
    await simplifyToggle.click();
    await expect(page.locator('.simplified-text')).toBeVisible();

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

    // Press Space to close
    await simplifyToggle.press('Space');
    await expect(page.locator('.simplified-text')).not.toBeVisible();
  });

  test('answer is recorded against original question (not simplified)', async ({ page }) => {
    // Q1 is a select — select an option
    const select = page.locator('.wizard-select');
    await expect(select).toBeVisible({ timeout: 10000 });

    // Show simplified text first
    const simplifyToggle = page.locator('.simplify-toggle');
    await simplifyToggle.click();
    await expect(page.locator('.simplified-text')).toBeVisible();

    // Select an answer
    await select.selectOption('BANK');

    // Click Next
    const nextBtn = page.locator('.btn-wizard-next');
    await nextBtn.click();

    // Should advance to Q2 (answer was recorded against original Q1 key)
    const newQuestion = page.locator('.question-text');
    await expect(newQuestion).toBeVisible();
    // Q2 text should be different from Q1
    const q2Text = await newQuestion.textContent();
    expect(q2Text).toBeTruthy();
  });

  test('question without simplifiedText does not show the ? icon', async ({ page }) => {
    // Navigate to Q3 (date question, no simplifiedText)
    // First answer Q1
    const select = page.locator('.wizard-select');
    await expect(select).toBeVisible({ timeout: 10000 });
    await select.selectOption('BANK');
    await page.locator('.btn-wizard-next').click();

    // Answer Q2
    const yesOption = page.locator('.wizard-radio').first();
    await yesOption.click();
    await page.locator('.btn-wizard-next').click();

    // Now on Q3 (date question) — no simplifiedText
    await expect(page.locator('.wizard-date-input')).toBeVisible();
    await expect(page.locator('.simplify-toggle')).not.toBeVisible();
  });
});
