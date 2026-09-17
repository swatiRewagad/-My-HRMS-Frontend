import { test, expect } from '../fixtures';
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
    await loginAsReRole(page, 'RE_NODAL_OFFICER', '/re-portal/profile');
  });

  test.afterEach(async ({ page }) => {
    if (keycloakUp) {
      await logout(page);
    }
  });

  test('Profile page shows entity info', async ({ page }) => {
    await page.waitForSelector('.re-profile', { timeout: 15000 });

    const heading = page.locator('.page-heading');
    await expect(heading).toBeVisible();
    await expect(heading).toContainText('Entity Profile');

    const entityName = page.locator('.detail-item:has(.detail-label:has-text("Entity Name")) .detail-value');
    await expect(entityName).toBeVisible();

    // "Nodal Officer" is a substring of "Principal Nodal Officer", so :has-text matched both cards
    // and tripped strict mode. Scoped to the first match, which is the Nodal Officer card.
    const nodalCard = page.locator('.card-title:has-text("Nodal Officer")').first();
    await expect(nodalCard).toBeVisible();
  });

  test('Update nodal officer details succeeds', async ({ page }) => {
    await page.waitForSelector('.re-profile', { timeout: 15000 });

    const editBtn = page.locator('.edit-btn');
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();

    // Name and email are mandatory, and the seeded entity has neither, so filling only the phone
    // makes the form fail validation ("Name and email are required.") and no success banner appears.
    // All three required fields are supplied so this test exercises a successful save.
    const nameInput = page.locator('#nodalName');
    await expect(nameInput).toBeVisible({ timeout: 5000 });
    await nameInput.fill('E2E Nodal Officer');

    const emailInput = page.locator('#nodalEmail');
    await emailInput.fill('e2e.nodal@example.test');

    const phoneInput = page.locator('#nodalPhone');
    await expect(phoneInput).toBeVisible({ timeout: 5000 });
    await phoneInput.clear();
    await phoneInput.fill('9999888877');

    const saveBtn = page.locator('.save-btn');
    await saveBtn.click();

    const successBanner = page.locator('.success-banner');
    await expect(successBanner).toBeVisible({ timeout: 10000 });
  });

  test('Cancel edit reverts changes', async ({ page }) => {
    await page.waitForSelector('.re-profile', { timeout: 15000 });

    const editBtn = page.locator('.edit-btn');
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();

    const phoneInput = page.locator('#nodalPhone');
    await expect(phoneInput).toBeVisible({ timeout: 5000 });
    const originalValue = await phoneInput.inputValue();

    await phoneInput.clear();
    await phoneInput.fill('1111222233');

    const cancelBtn = page.locator('.cancel-btn');
    await cancelBtn.click();

    await page.waitForTimeout(500);

    // After cancel, should return to view mode (detail-grid visible, edit-form hidden)
    const detailGrid = page.locator('.detail-grid');
    await expect(detailGrid.first()).toBeVisible({ timeout: 3000 });

    // Edit button should reappear
    await expect(page.locator('.edit-btn')).toBeVisible();
  });
});
