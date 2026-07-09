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

  test('Citizen can search for closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Search Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Phase: search — the form-input for complaint number
    const searchInput = page.locator('.form-input');
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    // Click "Check Eligibility" button
    const searchBtn = page.locator('.btn-primary:has-text("Check Eligibility")');
    await searchBtn.click();

    // Should move to eligibility phase showing complaint info
    const eligibilityResult = page.locator('.eligibility-result');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });
  });

  test('Eligibility check shows result (eligible/ineligible)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Eligibility Check Test',
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

    // Should have either .eligible or .ineligible class
    const text = await eligibilityResult.textContent();
    expect(text?.toLowerCase()).toMatch(/eligible|not eligible/);
  });

  test('Filing appeal with valid complaint succeeds', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E File Appeal Success Test',
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

    // Eligibility phase — wait for eligible result
    const eligibilityResult = page.locator('.eligibility-result.eligible');
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    // Click "Proceed to File Appeal"
    const proceedBtn = page.locator('.btn-primary:has-text("Proceed to File Appeal")');
    await expect(proceedBtn).toBeVisible();
    await proceedBtn.click();

    // Form phase — fill appeal details
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

    // Success phase
    const successCard = page.locator('.success-card');
    await expect(successCard).toBeVisible({ timeout: 15000 });
  });

  test('Appeal number displayed on success', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Number Display Test',
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

    // Fill minimal form
    const groundRadio = page.locator('.radio-label').first();
    await groundRadio.click();

    const detailsField = page.locator('.form-textarea').first();
    await detailsField.fill('Dissatisfied with complaint resolution.');

    const declaration = page.locator('.declaration-box input[type="checkbox"]');
    await declaration.check();

    // Submit
    const submitBtn = page.locator('.btn-primary:has-text("Submit Appeal")');
    await submitBtn.click();

    // Success — appeal reference number should be displayed
    const refNumber = page.locator('.ref-number');
    await expect(refNumber).toBeVisible({ timeout: 15000 });

    const numText = await refNumber.textContent();
    expect(numText?.trim()).toBeTruthy();
    expect(numText?.trim().length).toBeGreaterThan(0);
  });

  test('Filing when ineligible shows error message', async ({ page, request }) => {
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
    expect(text?.toLowerCase()).toMatch(/ineligible|not eligible|not closed|error/);
  });
});
