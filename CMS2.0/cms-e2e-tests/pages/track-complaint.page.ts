import { type Locator, type Page, expect } from '@playwright/test';

export class TrackComplaintPage {
  readonly page: Page;
  readonly complaintNumberInput: Locator;
  readonly emailInput: Locator;
  readonly phoneInput: Locator;
  readonly searchButton: Locator;
  readonly resultCard: Locator;
  readonly statusBadge: Locator;
  readonly timeline: Locator;
  readonly noResultMessage: Locator;

  constructor(page: Page) {
    this.page = page;
    this.complaintNumberInput = page.locator('.search-box input');
    this.emailInput = page.getByPlaceholder(/email/i);
    this.phoneInput = page.getByPlaceholder(/phone|mobile/i);
    this.searchButton = page.locator('.search-box button');
    this.resultCard = page.locator('.status-card');
    this.statusBadge = page.locator('.status-badge');
    this.timeline = page.locator('.timeline');
    this.noResultMessage = page.locator('.error-message');
  }

  async goto() {
    await this.page.goto('/public/track');
  }

  async trackByNumber(complaintNumber: string) {
    await this.complaintNumberInput.fill(complaintNumber);
    await this.searchButton.click();
  }

  async trackByEmail(email: string) {
    await this.emailInput.fill(email);
    await this.searchButton.click();
  }

  async expectResultFound() {
    await expect(this.resultCard).toBeVisible({ timeout: 10000 });
  }

  async expectNoResult() {
    await expect(this.noResultMessage).toBeVisible({ timeout: 10000 });
  }

  async expectStatus(status: string) {
    await expect(this.statusBadge).toContainText(new RegExp(status, 'i'));
  }
}
