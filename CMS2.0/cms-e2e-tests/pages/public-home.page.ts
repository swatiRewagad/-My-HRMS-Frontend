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
    this.fileComplaintButton = page.getByRole('button', { name: /file.*complaint/i });
    this.trackComplaintButton = page.getByRole('button', { name: /track.*complaint/i });
    this.languageSelector = page.locator('[data-testid="language-selector"]');
    this.loginLink = page.getByRole('link', { name: /login|sign in/i });
    this.heroSection = page.locator('.hero-section, [data-testid="hero"]');
    this.faqSection = page.locator('.faq-section, [data-testid="faq"]');
  }

  async goto() {
    await this.page.goto('/');
  }

  async navigateToFileComplaint() {
    await this.fileComplaintButton.click();
    await this.page.waitForURL(/.*file-complaint.*/);
  }

  async navigateToTrackComplaint() {
    await this.trackComplaintButton.click();
    await this.page.waitForURL(/.*track.*/);
  }

  async switchLanguage(locale: string) {
    await this.languageSelector.click();
    await this.page.getByRole('option', { name: new RegExp(locale, 'i') }).click();
  }

  async navigateToLogin() {
    await this.loginLink.click();
  }

  async expectPageLoaded() {
    await expect(this.page).toHaveTitle(/CMS|Complaint Management|Ombudsman/i);
    await expect(this.heroSection).toBeVisible();
  }
}
