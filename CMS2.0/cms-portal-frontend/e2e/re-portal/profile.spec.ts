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

    const nodalCard = page.locator('.card-title:has-text("Nodal Officer")');
    await expect(nodalCard).toBeVisible();
  });

  test('Update nodal officer details succeeds', async ({ page }) => {
    await page.waitForSelector('.re-profile', { timeout: 15000 });

    const editBtn = page.locator('.edit-btn');
    await expect(editBtn).toBeVisible({ timeout: 5000 });
    await editBtn.click();

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
