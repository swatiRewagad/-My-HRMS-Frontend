import { test, expect, Page } from '@playwright/test';

async function ssoLogin(page: Page, username: string, password: string) {
  await page.goto('/staff/login');
  await page.getByRole('button', { name: /Sign in with Keycloak SSO/i }).click();
  await page.waitForURL(/openid-connect\/auth/, { timeout: 60000 });
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
}

test.describe('staff/dashboard department redirect', () => {
  test('AA_DO reaches the AA dashboard', async ({ page }) => {
    await ssoLogin(page, 'aa_do_001', 'test123');
    await page.waitForURL(/\/aa\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/aa/dashboard');
  });

  test('RE_PNO reaches the RE portal dashboard', async ({ page }) => {
    await ssoLogin(page, 're_pno_001', 'test123');
    await page.waitForURL(/\/re-portal\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/re-portal/dashboard');
  });

  test('CEPC_DO still reaches the CEPC dashboard', async ({ page }) => {
    await ssoLogin(page, 'cepc_do1', 'password');
    await page.waitForURL(/\/cepc\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/cepc/dashboard');
  });
});
