import { type Locator, type Page, expect } from '@playwright/test';

export class PublicHomePage {
  readonly page: Page;
  readonly fileComplaintButton: Locator;
  readonly trackComplaintButton: Locator;
  readonly languageSelector: Locator;
  readonly loginLink: Locator;
  readonly heroSection: Locator;
  readonly faqSection: Locator;

  constructor(page: Page) {
    this.page = page;
    this.fileComplaintButton = page.locator('a.hero-btn');
    this.trackComplaintButton = page.locator('a.hero-outlined-btn').first();
    this.languageSelector = page.locator('select.lang-select');
    this.loginLink = page.locator('a.login-btn');
    this.heroSection = page.locator('section.hero');
    this.faqSection = page.locator('section.faq-section');
  }

  async goto() {
    await this.page.goto('/public', { waitUntil: 'networkidle' });
    await this.page.waitForLoadState('domcontentloaded');
  }

  async navigateToFileComplaint() {
    await this.fileComplaintButton.click({ timeout: 15000 });
    await this.page.waitForURL(/.*file-complaint.*/);
  }

  async navigateToTrackComplaint() {
    await this.trackComplaintButton.click({ force: true, timeout: 15000 });
    await this.page.waitForURL(/.*track.*/);
  }

  async switchLanguage(locale: string) {
    await this.languageSelector.selectOption(locale);
  }

  async navigateToLogin() {
    await this.loginLink.click();
  }

  async expectPageLoaded() {
    await expect(this.page).toHaveTitle(/CMS|Complaint Management|Ombudsman/i);
    await expect(this.heroSection).toBeVisible({ timeout: 15000 });
  }
}
