import { test, expect } from '@playwright/test';
import { createTestComplaint, advanceToStatus, seedCitizenSession } from '../utils/test-data';

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';

/**
 * Submit feedback via the backend API directly.
 */
async function submitFeedbackViaApi(
  request: any,
  complaintNumber: string,
  overrides: Record<string, unknown> = {}
) {
  const payload = {
    complaintNumber,
    overallRating: 4,
    easeOfFiling: 3,
    grievanceRedressTime: 4,
    sourceOfInformation: 'RBI Website',
    cmsPortalAwareness: 'Very Aware',
    feedbackText: 'Good service',
    ...overrides,
  };
  return request.post(`${API_BASE}/api/v1/feedback`, {
    data: payload,
    headers: { 'Content-Type': 'application/json' },
  });
}

test.describe('Feedback Flow (Public)', () => {

  // ────────────────────────────────────────────────────────────────────
  // Test 1: Submit feedback for a closed complaint — success
  // ────────────────────────────────────────────────────────────────────
  test('submit feedback for closed complaint shows success', async ({ page, request }) => {
    // Setup: create a complaint and advance it to closed status
    const complaint = await createTestComplaint(request, {
      subject: 'Feedback E2E — closed complaint',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    // Navigate to feedback page
    // /public/feedback is behind publicAuthGuard — seed a session or it redirects to login.
    await page.goto('/public');
    await seedCitizenSession(page, '9876543210', 'e2e-seeded-token.sig');
    await page.goto('/public/feedback');
    await page.waitForLoadState('networkidle');

    // Fill the complaint reference number
    const refInput = page.locator('input[name="complaintId"]');
    await expect(refInput).toBeVisible({ timeout: 10000 });
    await refInput.fill(complaint.complaintNumber);

    // Set required ratings by clicking stars
    // Overall rating — 4 stars
    const overallStars = page.locator('[aria-labelledby="overall-rating-label"] .star-btn');
    await overallStars.nth(3).click();

    // Ease of filing — 3 stars
    const easeStars = page.locator('[aria-labelledby="ease-rating-label"] .star-btn');
    await easeStars.nth(2).click();

    // Grievance redress time — 4 stars
    const redressStars = page.locator('[aria-labelledby="redress-rating-label"] .star-btn');
    await redressStars.nth(3).click();

    // Source of information
    await page.locator('select[name="sourceOfInformation"]').selectOption('RBI Website');

    // CMS Portal awareness
    // Angular binds [value] as a property, so there is no value attribute to match on.
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    // Optional feedback text
    await page.locator('textarea[name="feedbackText"]').fill('Excellent resolution of my complaint.');

    // Submit
    await page.locator('button:has-text("Submit"), button.btn-primary:has(i.pi-send)').click();

    // Wait for success phase
    const successCard = page.locator('.success-card');
    await expect(successCard).toBeVisible({ timeout: 15000 });
    await expect(successCard).toContainText(complaint.complaintNumber);
  });

  // ────────────────────────────────────────────────────────────────────
  // Test 2: Submit duplicate feedback — error
  // ────────────────────────────────────────────────────────────────────
  test('submit duplicate feedback shows already submitted error', async ({ page, request }) => {
    // Setup: create and close a complaint, then submit feedback via API
    const complaint = await createTestComplaint(request, {
      subject: 'Feedback E2E — duplicate test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    // Submit feedback via API first
    const apiResp = await submitFeedbackViaApi(request, complaint.complaintNumber);
    expect(apiResp.status()).toBe(201);

    // Now try via the UI
    // /public/feedback is behind publicAuthGuard — seed a session or it redirects to login.
    await page.goto('/public');
    await seedCitizenSession(page, '9876543210', 'e2e-seeded-token.sig');
    await page.goto('/public/feedback');
    await page.waitForLoadState('networkidle');

    const refInput = page.locator('input[name="complaintId"]');
    await expect(refInput).toBeVisible({ timeout: 10000 });
    await refInput.fill(complaint.complaintNumber);

    // Fill required fields
    const overallStars = page.locator('[aria-labelledby="overall-rating-label"] .star-btn');
    await overallStars.nth(3).click();
    const easeStars = page.locator('[aria-labelledby="ease-rating-label"] .star-btn');
    await easeStars.nth(2).click();
    const redressStars = page.locator('[aria-labelledby="redress-rating-label"] .star-btn');
    await redressStars.nth(3).click();
    await page.locator('select[name="sourceOfInformation"]').selectOption('RBI Website');
    // Angular binds [value] as a property, so there is no value attribute to match on.
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    // Submit
    await page.locator('button:has-text("Submit"), button.btn-primary:has(i.pi-send)').click();

    // Should show duplicate error
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 10000 });
    await expect(errorMsg).toContainText('Feedback already submitted for this complaint');
  });

  // ────────────────────────────────────────────────────────────────────
  // Test 3: Submit without mandatory fields — validation errors
  // ────────────────────────────────────────────────────────────────────
  test('submit without mandatory fields shows validation errors', async ({ page }) => {
    // /public/feedback is behind publicAuthGuard — seed a session or it redirects to login.
    await page.goto('/public');
    await seedCitizenSession(page, '9876543210', 'e2e-seeded-token.sig');
    await page.goto('/public/feedback');
    await page.waitForLoadState('networkidle');

    const submitBtn = page.locator('button:has-text("Submit"), button.btn-primary:has(i.pi-send)');
    await expect(submitBtn).toBeVisible({ timeout: 10000 });

    // Submit with empty form — complaint reference is required
    await submitBtn.click();
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible();
    await expect(errorMsg).toContainText('Complaint reference number is required');

    // Fill complaint ID but skip overall rating
    await page.locator('input[name="complaintId"]').fill('CMP-NONEXIST-000001');
    await submitBtn.click();
    await expect(errorMsg).toContainText('Please provide an overall rating');

    // Set overall rating but skip ease of filing
    const overallStars = page.locator('[aria-labelledby="overall-rating-label"] .star-btn');
    await overallStars.nth(3).click();
    await submitBtn.click();
    await expect(errorMsg).toContainText('Please rate the ease of filing');

    // Set ease of filing but skip grievance redress time
    const easeStars = page.locator('[aria-labelledby="ease-rating-label"] .star-btn');
    await easeStars.nth(2).click();
    await submitBtn.click();
    await expect(errorMsg).toContainText('Please rate the grievance redress time');

    // Set grievance redress time but skip source of information
    const redressStars = page.locator('[aria-labelledby="redress-rating-label"] .star-btn');
    await redressStars.nth(3).click();
    await submitBtn.click();
    await expect(errorMsg).toContainText('Please select source of information');

    // Set source of information but skip awareness
    await page.locator('select[name="sourceOfInformation"]').selectOption('RBI Website');
    await submitBtn.click();
    await expect(errorMsg).toContainText('Please select CMS Portal awareness level');
  });

  // ────────────────────────────────────────────────────────────────────
  // Test 4: feedbackText exceeding 500 chars — validation error
  // ────────────────────────────────────────────────────────────────────
  test('feedback text exceeding 500 characters shows validation error', async ({ page }) => {
    // /public/feedback is behind publicAuthGuard — seed a session or it redirects to login.
    await page.goto('/public');
    await seedCitizenSession(page, '9876543210', 'e2e-seeded-token.sig');
    await page.goto('/public/feedback');
    await page.waitForLoadState('networkidle');

    // Fill all mandatory fields
    await page.locator('input[name="complaintId"]').fill('CMP-TEST-CHARLIMIT');

    const overallStars = page.locator('[aria-labelledby="overall-rating-label"] .star-btn');
    await overallStars.nth(3).click();
    const easeStars = page.locator('[aria-labelledby="ease-rating-label"] .star-btn');
    await easeStars.nth(2).click();
    const redressStars = page.locator('[aria-labelledby="redress-rating-label"] .star-btn');
    await redressStars.nth(3).click();
    await page.locator('select[name="sourceOfInformation"]').selectOption('RBI Website');
    // Angular binds [value] as a property, so there is no value attribute to match on.
    await page.getByRole('radio', { name: 'Very Aware', exact: true }).check();

    // Fill feedback text exceeding 500 chars (bypass maxlength via JS)
    const longText = 'A'.repeat(501);
    await page.locator('textarea[name="feedbackText"]').evaluate(
      (el: HTMLTextAreaElement, text: string) => {
        el.removeAttribute('maxlength');
        el.value = text;
        el.dispatchEvent(new Event('input', { bubbles: true }));
      },
      longText
    );

    // Submit — should show character limit error
    const submitBtn = page.locator('button:has-text("Submit"), button.btn-primary:has(i.pi-send)');
    await submitBtn.click();

    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });
    await expect(errorMsg).toContainText('500 characters');
  });
});
