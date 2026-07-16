import { type Locator, type Page, expect } from '@playwright/test';

export class RebitHomePage {
  readonly page: Page;
  readonly knowMoreButton: Locator;
  readonly ourVisionHeading: Locator;

  constructor(page: Page) {
    this.page = page;
    this.knowMoreButton = page.locator('a:has-text("Know More")').first();
    this.ourVisionHeading = page.getByRole('heading', { name: /Our Vision/i }).or(
      page.locator('.orange-txt:visible:has-text("Our Vision")')
    ).first();
  }

  async goto() {
    await this.page.goto('https://rebit.org.in/', { waitUntil: 'domcontentloaded' });
  }

  async clickKnowMore() {
    await this.knowMoreButton.waitFor({ state: 'visible', timeout: 15000 });
    await this.knowMoreButton.click();
  }

  async expectOurVisionPageLoaded() {
    await this.page.waitForLoadState('domcontentloaded');
    await expect(this.page.locator('.orange-txt:has-text("Our Vision"):not(.d-none)')).toBeVisible({ timeout: 15000 });
  }
}
