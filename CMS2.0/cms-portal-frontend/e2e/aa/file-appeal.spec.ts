import { test, expect } from '@playwright/test';
import { loginAsAaRole, isKeycloakAvailable, logout } from '../utils/auth';
import { createTestComplaint, advanceToStatus } from '../utils/test-data';

test.describe('AA - File Appeal', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Citizen can search for closed complaint', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a closed complaint to search for
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Search Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    // Navigate to appeal filing page (public-facing)
    await page.goto('/aa/file-appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search for the complaint
    const searchInput = page.locator(
      'input[name="complaintNumber"], input[placeholder*="complaint"], [data-testid="complaint-search"]'
    );
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    // Click search
    const searchBtn = page.locator(
      'button:has-text("Search"), button:has-text("Find"), [data-testid="search-btn"]'
    );
    await searchBtn.click();

    // Should display complaint details
    const complaintInfo = page.locator(
      `text=${complaint.complaintNumber}, [data-testid="found-complaint"]`
    );
    await expect(complaintInfo).toBeVisible({ timeout: 10000 });
  });

  test('Eligibility check shows result (eligible/ineligible)', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create and close a complaint
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Eligibility Check Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/aa/file-appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search
    const searchInput = page.locator(
      'input[name="complaintNumber"], input[placeholder*="complaint"], [data-testid="complaint-search"]'
    );
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator(
      'button:has-text("Search"), button:has-text("Find"), [data-testid="search-btn"]'
    );
    await searchBtn.click();

    // Wait for eligibility result
    const eligibilityResult = page.locator(
      '[data-testid="eligibility-result"], .eligibility-status, .eligibility-badge'
    );
    await expect(eligibilityResult).toBeVisible({ timeout: 10000 });

    const text = await eligibilityResult.textContent();
    expect(text?.toLowerCase()).toMatch(/eligible|ineligible/);
  });

  test('Filing appeal with valid complaint succeeds', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create and close a complaint
    const complaint = await createTestComplaint(request, {
      subject: 'E2E File Appeal Success Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/aa/file-appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search and find complaint
    const searchInput = page.locator(
      'input[name="complaintNumber"], input[placeholder*="complaint"], [data-testid="complaint-search"]'
    );
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator(
      'button:has-text("Search"), button:has-text("Find"), [data-testid="search-btn"]'
    );
    await searchBtn.click();

    // Wait for eligibility check to pass
    await page.waitForSelector(
      '[data-testid="eligibility-result"], .eligibility-status',
      { timeout: 10000 }
    );

    // Fill appeal form
    const groundsField = page.locator(
      'textarea[name="grounds"], [data-testid="appeal-grounds"], textarea[placeholder*="grounds"]'
    );
    await expect(groundsField).toBeVisible({ timeout: 5000 });
    await groundsField.fill('The complaint was not resolved satisfactorily. The RE failed to provide adequate compensation.');

    const reliefField = page.locator(
      'textarea[name="reliefSought"], [data-testid="relief-sought"], textarea[placeholder*="relief"]'
    );
    if (await reliefField.isVisible().catch(() => false)) {
      await reliefField.fill('Full compensation of Rs. 50000 and corrective action against the RE.');
    }

    // Appellant details (if separate from logged-in user)
    const nameField = page.locator(
      'input[name="appellantName"], [data-testid="appellant-name"]'
    );
    if (await nameField.isVisible().catch(() => false)) {
      await nameField.fill('E2E Test Appellant');
    }

    const emailField = page.locator(
      'input[name="appellantEmail"], [data-testid="appellant-email"]'
    );
    if (await emailField.isVisible().catch(() => false)) {
      await emailField.fill('e2e.appellant@test.com');
    }

    // Submit
    const submitBtn = page.locator(
      'button:has-text("File Appeal"), button:has-text("Submit"), [data-testid="submit-appeal"]'
    );
    await submitBtn.click();

    // Wait for success
    const successMsg = page.locator(
      '.success-msg, [data-testid="appeal-success"], .toast-success'
    );
    await expect(successMsg).toBeVisible({ timeout: 15000 });
  });

  test('Appeal number displayed on success', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create and close a complaint
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Appeal Number Display Test',
    });
    await advanceToStatus(request, complaint.complaintNumber, 'closed');

    await page.goto('/aa/file-appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search
    const searchInput = page.locator(
      'input[name="complaintNumber"], input[placeholder*="complaint"], [data-testid="complaint-search"]'
    );
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator(
      'button:has-text("Search"), button:has-text("Find"), [data-testid="search-btn"]'
    );
    await searchBtn.click();

    await page.waitForSelector(
      '[data-testid="eligibility-result"], .eligibility-status',
      { timeout: 10000 }
    );

    // Fill minimal appeal form
    const groundsField = page.locator(
      'textarea[name="grounds"], [data-testid="appeal-grounds"], textarea[placeholder*="grounds"]'
    );
    await expect(groundsField).toBeVisible({ timeout: 5000 });
    await groundsField.fill('Dissatisfied with complaint resolution.');

    // Submit
    const submitBtn = page.locator(
      'button:has-text("File Appeal"), button:has-text("Submit"), [data-testid="submit-appeal"]'
    );
    await submitBtn.click();

    // Appeal number should be displayed
    const appealNumber = page.locator(
      '[data-testid="appeal-number"], .appeal-number, .success-msg:has-text("AA/")'
    );
    await expect(appealNumber).toBeVisible({ timeout: 15000 });

    const numText = await appealNumber.textContent();
    expect(numText?.trim()).toMatch(/AA\/|APL/);
  });

  test('Filing when ineligible shows error message', async ({ page, request }) => {
    test.skip(!keycloakUp, 'Keycloak is not available');

    // Create a complaint that is NOT closed (still in progress)
    const complaint = await createTestComplaint(request, {
      subject: 'E2E Ineligible Appeal Test',
    });

    await page.goto('/aa/file-appeal', { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('networkidle');

    // Search for non-closed complaint
    const searchInput = page.locator(
      'input[name="complaintNumber"], input[placeholder*="complaint"], [data-testid="complaint-search"]'
    );
    await expect(searchInput).toBeVisible({ timeout: 10000 });
    await searchInput.fill(complaint.complaintNumber);

    const searchBtn = page.locator(
      'button:has-text("Search"), button:has-text("Find"), [data-testid="search-btn"]'
    );
    await searchBtn.click();

    // Should show ineligible status or error
    const errorOrIneligible = page.locator(
      '[data-testid="ineligible-msg"], .error-msg, .ineligible-status, .eligibility-badge:has-text("Ineligible")'
    );
    await expect(errorOrIneligible).toBeVisible({ timeout: 10000 });

    const text = await errorOrIneligible.textContent();
    expect(text?.toLowerCase()).toMatch(/ineligible|not eligible|cannot file|not closed/);
  });
});
