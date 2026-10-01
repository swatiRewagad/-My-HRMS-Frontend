import { test, expect, Page } from '@playwright/test';

/**
 * Credentials come from e2e/review/credentials.env, never from literals here.
 *
 * All three logins were hardcoded and all three were wrong: aa_do_001 and re_pno_001 are Test@123
 * rather than test123, and cepc_do1 has no working password at all — the crawl uses cepc_do2, which
 * holds the same CEPC_DO role. Each test therefore failed on waitForURL after a silent Keycloak
 * refusal, which reads as a broken department redirect rather than a bad password.
 */
function credential(userVar: string, passVar: string): { username: string; password: string } {
  const username = process.env[userVar];
  const password = process.env[passVar];
  if (!username || !password) {
    throw new Error(
      `${userVar}/${passVar} are not set. Source e2e/review/credentials.env before running this spec.`
    );
  }
  return { username, password };
}

async function ssoLogin(page: Page, userVar: string, passVar: string) {
  const { username, password } = credential(userVar, passVar);
  await page.goto('/staff/login');
  await page.getByRole('button', { name: /Sign in with Keycloak SSO/i }).click();
  await page.waitForURL(/openid-connect\/auth/, { timeout: 60000 });
  await page.fill('#username', username);
  await page.fill('#password', password);
  await page.click('#kc-login');
}

test.describe('staff/dashboard department redirect', () => {
  test('AA_DO reaches the AA dashboard', async ({ page }) => {
    await ssoLogin(page, 'AA_DO_USER', 'AA_DO_PASS');
    await page.waitForURL(/\/aa\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/aa/dashboard');
  });

  test('RE_PNO reaches the RE portal dashboard', async ({ page }) => {
    await ssoLogin(page, 'RE_PNO_USER', 'RE_PNO_PASS');
    await page.waitForURL(/\/re-portal\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/re-portal/dashboard');
  });

  test('CEPC_DO still reaches the CEPC dashboard', async ({ page }) => {
    await ssoLogin(page, 'CEPC_DO_USER', 'CEPC_DO_PASS');
    await page.waitForURL(/\/cepc\/dashboard/, { timeout: 60000 });
    expect(page.url()).toContain('/cepc/dashboard');
  });
});
