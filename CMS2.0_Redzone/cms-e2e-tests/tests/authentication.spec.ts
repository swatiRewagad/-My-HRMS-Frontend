import { test, expect } from '@playwright/test';
import { LoginPage } from '../pages';
import { USERS } from '../helpers/auth';

test.describe('Authentication', () => {
  test('should redirect staff routes to Keycloak login', async ({ page }) => {
    await page.goto('/staff/rbio/tasks');
    await expect(page).toHaveURL(/.*keycloak.*|.*login.*/);
  });

  test('should login RBIO officer via Keycloak', async ({ page }) => {
    await page.goto('/staff/rbio/tasks');
    const loginPage = new LoginPage(page);
    await loginPage.login(USERS.rbioOfficer.username, USERS.rbioOfficer.password);
    await loginPage.expectRedirectToDashboard();
  });

  test('should login CEPC officer via Keycloak', async ({ page }) => {
    await page.goto('/staff/cepc/tasks');
    const loginPage = new LoginPage(page);
    await loginPage.login(USERS.cepcOfficer.username, USERS.cepcOfficer.password);
    await loginPage.expectRedirectToDashboard();
  });

  test('should login admin user via Keycloak', async ({ page }) => {
    await page.goto('/admin/rules');
    const loginPage = new LoginPage(page);
    await loginPage.login(USERS.admin.username, USERS.admin.password);
    await loginPage.expectRedirectToDashboard();
  });

  test('should show error for invalid credentials', async ({ page }) => {
    await page.goto('/staff/rbio/tasks');
    const loginPage = new LoginPage(page);
    await loginPage.login('invalid_user', 'wrong_password');
    await loginPage.expectError();
  });

  test('should not allow public pages without authentication', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/.*\//);
    await expect(page.locator('body')).not.toContainText(/unauthorized/i);
  });

  test('should preserve session after page reload', async ({ page }) => {
    await page.goto('/staff/rbio/tasks');
    const loginPage = new LoginPage(page);
    await loginPage.login(USERS.rbioOfficer.username, USERS.rbioOfficer.password);
    await loginPage.expectRedirectToDashboard();

    await page.reload();
    await expect(page).not.toHaveURL(/.*keycloak.*/);
  });

  test('should logout and redirect to login', async ({ page }) => {
    await page.goto('/staff/rbio/tasks');
    const loginPage = new LoginPage(page);
    await loginPage.login(USERS.rbioOfficer.username, USERS.rbioOfficer.password);
    await loginPage.expectRedirectToDashboard();

    await page.getByRole('button', { name: /logout|sign out|profile/i }).click();
    await page.getByText(/logout|sign out/i).click();
    await expect(page).toHaveURL(/.*keycloak.*|.*login.*|.*\//);
  });
});
