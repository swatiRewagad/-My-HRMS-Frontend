import { test, expect } from '@playwright/test';
import { isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, advanceToStatus } from '../utils/test-data';

test.describe('AA - File Appeal', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: <30 days — eligible
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint <30 days shows eligible', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Eligible <30d',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const text = await eligibilityResult.textContent();
    expect(text?.toLowerCase()).toContain('eligible');
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: 31-60 days — delayed eligible, reason required
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint 31-60 days shows delayed eligible with reason required', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Delayed 31-60d',
    });
    // Advance to closed and backdate closure to ~45 days ago
    await advanceToStatus(request, complaint.complaintNumber, 'closed', { backdateDays: 45 });

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Should show eligible (delayed filing is still eligible)
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    // Proceed to form
    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await expect(proceedBtn).toBeVisible();
    await proceedBtn.click();

    // The delay notice should appear
    const delayNotice = page.locator('.delay-notice');
    await expect(delayNotice).toBeVisible({ timeout: 5000 });

    // Reason for delay field should be visible
    const reasonField = page.locator('textarea[name="reasonForDelay"]');
    await expect(reasonField).toBeVisible();
  });

  // ═══════════════════════════════════════════════════════════
  // Eligibility: >60 days — not eligible
  // ═══════════════════════════════════════════════════════════
  test('Eligibility check for complaint >60 days shows not eligible', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Ineligible >60d',
    });
    // Advance to closed and backdate closure to ~90 days ago
    await advanceToStatus(request, complaint.complaintNumber, 'closed', { backdateDays: 90 });

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const ineligible = page.locator('.eligibility-result.ineligible');
    await expect(ineligible).toBeVisible({ timeout: 10000 });

    const text = await ineligible.textContent();
    expect(text?.toLowerCase()).toMatch(/not eligible|exceeded|ineligible/);
  });

  // ═══════════════════════════════════════════════════════════
  // Validation: Submit without required fields
  // ═══════════════════════════════════════════════════════════
  test('Submit appeal without required fields shows validation errors', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Validation Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search phase
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Eligibility phase
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // Form phase — try to submit without filling anything
    const submitBtn = page.locator('.btn-primary:has-text("Submit Appeal")');
    await expect(submitBtn).toBeVisible({ timeout: 5000 });
    await submitBtn.click();

    // Should show validation error
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });

    const errorText = await errorMsg.textContent();
    expect(errorText?.toLowerCase()).toMatch(/required|select|provide|accept/);
  });

  // ═══════════════════════════════════════════════════════════
  // File upload: >2MB shows error (not silently dropped)
  // ═══════════════════════════════════════════════════════════
  test('File >2MB shows error message instead of being silently dropped', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal File Size Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Navigate to form phase
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // Create a large file buffer (3MB) and upload via file input
    const fileInput = page.locator('input#appealFiles');
    await expect(fileInput).toBeAttached({ timeout: 5000 });

    // Create a 3MB test file
    const buffer = Buffer.alloc(3 * 1024 * 1024, 'x');
    await fileInput.setInputFiles({
      name: 'large-file.pdf',
      mimeType: 'application/pdf',
      buffer,
    });

    // Error message should appear about file size
    const errorMsg = page.locator('.error-msg');
    await expect(errorMsg).toBeVisible({ timeout: 5000 });

    const errorText = await errorMsg.textContent();
    expect(errorText).toContain('exceeds 2MB limit');
  });

  // ═══════════════════════════════════════════════════════════
  // Successful submission: appeal number displayed
  // ═══════════════════════════════════════════════════════════
  test('Successful submission displays appeal number', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E File Appeal Success',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Wait for eligible
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    // Proceed
    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await proceedBtn.click();

    // Fill form
    const groundRadio = page.locator('.radio-label').first();
    await expect(groundRadio).toBeVisible({ timeout: 5000 });
    await groundRadio.click();

    const detailsField = page.locator('.form-textarea').first();
    await expect(detailsField).toBeVisible();
    await detailsField.fill('The complaint was not resolved satisfactorily. The RE failed to provide adequate compensation.');

    // Relief sought
    const reliefField = page.locator('.form-textarea').nth(1);
    if (await reliefField.isVisible().catch(() => false)) {
      await reliefField.fill('Full compensation of Rs. 50000 and corrective action against the RE.');
    }

    // Declaration checkbox
    const declaration = page.locator('.declaration-box input[type="checkbox"]');
    await declaration.check();

    // Submit
    const submitBtn = page.locator('.btn-primary:has-text("Submit Appeal")');
    await submitBtn.click();

    // Success phase — appeal reference number should be displayed
    const successCard = page.locator('.success-card');
    await expect(successCard).toBeVisible({ timeout: 15000 });

    const refNumber = page.locator('.ref-number');
    await expect(refNumber).toBeVisible({ timeout: 5000 });

    const numText = await refNumber.textContent();
    expect(numText?.trim()).toBeTruthy();
    expect(numText?.trim()).toMatch(/^APL-/);
  });

  // ═══════════════════════════════════════════════════════════
  // Legacy tests (backward compat)
  // ═══════════════════════════════════════════════════════════
  test('Citizen can search for closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Search Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    const eligibilityResult = page.locator('.eligibility-result');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });
  });

  test('Filing when ineligible (active complaint) shows error message', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint that is NOT closed (still in progress)
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Ineligible Appeal Test',
    });

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Should show ineligible result or error
    const ineligible = page.locator('.eligibility-result.ineligible, .error-msg');
    await expect(ineligible).toBeVisible({ timeout: 10000 });

    const text = await ineligible.textContent();
    expect(text?.toLowerCase()).toMatch(/ineligible|not eligible|not closed|error|active/);
  });
});
