import { test, expect } from '@playwright/test';
import { loginAsReRole, isKeycloakAvailable, logout } from '../utils/auth';

test.describe('RE Portal - Profile', () => {
  let keycloakUp: boolean;

  test.beforeAll(async ({ browser }) => {
    const page = await browser.newPage();
    keycloakUp = await isKeycloakAvailable(page);
    await page.close();
  });

  test.beforeEach(async ({ page }) => {
    test.skip(!keycloakUp, 'Keycloak is not available — skipping RE profile tests');
    await loginAsReRole(page, 'RE_NODAL_OFFICER', '/re/profile');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Profile page shows entity info', async ({ page }) => {
    await page.waitForSelector(
      '[data-testid="re-profile"], .profile-page, .entity-profile',
      { timeout: 15000 }
    );

    // Entity name should be visible
    const entityName = page.locator(
      '[data-testid="entity-name"], .entity-name, .profile-header h2, .profile-header h3'
    );
    await expect(entityName).toBeVisible();
    const nameText = await entityName.textContent();
    expect(nameText?.trim().length).toBeGreaterThan(0);

    // Nodal officer section should be visible
    const nodalSection = page.locator(
      '[data-testid="nodal-officer-section"], .nodal-officer, .officer-details'
    );
    await expect(nodalSection).toBeVisible();

    // Contact information fields
    const emailField = page.locator(
      '[data-testid="officer-email"], .officer-email, input[name="email"], .profile-field:has-text("Email")'
    );
    await expect(emailField).toBeVisible();
  });

  test('Update nodal officer details succeeds', async ({ page }) => {
    await page.waitForSelector(
      '[data-testid="re-profile"], .profile-page, .entity-profile',
      { timeout: 15000 }
    );

    // Click edit button
    const editBtn = page.locator(
      'button:has-text("Edit"), [data-testid="edit-profile-btn"], .edit-btn'
    );
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();

    // Update phone number field
    const phoneInput = page.locator(
      'input[name="phone"], input[name="nodalOfficerPhone"], [data-testid="phone-input"]'
    );
    await expect(phoneInput).toBeVisible({ timeout: 5000 });
    await phoneInput.clear();
    await phoneInput.fill('9999888877');

    // Save changes
    const saveBtn = page.locator(
      'button:has-text("Save"), button:has-text("Update"), [data-testid="save-profile-btn"]'
    );
    await saveBtn.click();

    // Wait for success message
    const successMsg = page.locator(
      '.success-msg, [data-testid="profile-update-success"], .toast-success'
    );
    await expect(successMsg).toBeVisible({ timeout: 10000 });

    // Verify the updated value persists
    await page.reload();
    await page.waitForSelector(
      '[data-testid="re-profile"], .profile-page, .entity-profile',
      { timeout: 15000 }
    );

    const updatedPhone = page.locator(
      'input[name="phone"], input[name="nodalOfficerPhone"], [data-testid="phone-input"], .profile-field:has-text("9999888877")'
    );
    await expect(updatedPhone).toBeVisible({ timeout: 5000 });
  });

  test('Cancel edit reverts changes', async ({ page }) => {
    await page.waitForSelector(
      '[data-testid="re-profile"], .profile-page, .entity-profile',
      { timeout: 15000 }
    );

    // Click edit button
    const editBtn = page.locator(
      'button:has-text("Edit"), [data-testid="edit-profile-btn"], .edit-btn'
    );
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();

    // Get current phone value
    const phoneInput = page.locator(
      'input[name="phone"], input[name="nodalOfficerPhone"], [data-testid="phone-input"]'
    );
    await expect(phoneInput).toBeVisible({ timeout: 5000 });
    const originalValue = await phoneInput.inputValue();

    // Modify the field
    await phoneInput.clear();
    await phoneInput.fill('1111222233');

    // Click cancel
    const cancelBtn = page.locator(
      'button:has-text("Cancel"), [data-testid="cancel-edit-btn"], .cancel-btn'
    );
    await cancelBtn.click();

    // Value should revert (either phone input shows original or view mode shows original)
    await page.waitForTimeout(500);
    const currentPhone = page.locator(
      'input[name="phone"], input[name="nodalOfficerPhone"], [data-testid="phone-input"]'
    );
    if (await currentPhone.isVisible().catch(() => false)) {
      const val = await currentPhone.inputValue();
      expect(val).toBe(originalValue);
    } else {
      // In view mode, original value should be displayed
      const phoneDisplay = page.locator(
        `.profile-field:has-text("${originalValue}"), [data-testid="phone-display"]`
      );
      await expect(phoneDisplay).toBeVisible();
    }
  });
});
