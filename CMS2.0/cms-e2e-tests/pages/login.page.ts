import { type Locator, type Page, expect } from '@playwright/test';

export class LoginPage {
  readonly page: Page;
  readonly usernameInput: Locator;
  readonly passwordInput: Locator;
  readonly loginButton: Locator;
  readonly errorMessage: Locator;
  readonly forgotPasswordLink: Locator;

  constructor(page: Page) {
    this.page = page;
    this.usernameInput = page.getByLabel(/username|user/i);
    this.passwordInput = page.getByLabel(/password/i);
    this.loginButton = page.getByRole('button', { name: /sign in|log in|login/i });
    this.errorMessage = page.locator('.kc-feedback-text, .alert-error, [data-testid="error"]');
    this.forgotPasswordLink = page.getByRole('link', { name: /forgot/i });
  }

  async login(username: string, password: string) {
    await this.usernameInput.fill(username);
    await this.passwordInput.fill(password);
    await this.loginButton.click();
  }

  async expectError() {
    await expect(this.errorMessage).toBeVisible();
  }

  async expectRedirectToDashboard() {
    await expect(this.page).toHaveURL(/.*\/(dashboard|tasks|home).*/);
  }
}
