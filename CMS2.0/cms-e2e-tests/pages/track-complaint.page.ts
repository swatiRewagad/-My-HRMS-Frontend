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
    this.complaintNumberInput = page.getByLabel(/complaint number/i);
    this.emailInput = page.getByLabel(/email/i);
    this.phoneInput = page.getByLabel(/phone|mobile/i);
    this.searchButton = page.getByRole('button', { name: /track|search|find/i });
    this.resultCard = page.locator('.result-card, [data-testid="complaint-result"]');
    this.statusBadge = page.locator('.status-badge, [data-testid="track-status"]');
    this.timeline = page.locator('.timeline, [data-testid="track-timeline"]');
    this.noResultMessage = page.getByText(/no.*complaint.*found|not found|invalid/i);
  }

  async goto() {
    await this.page.goto('/track-complaint');
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
    await expect(this.noResultMessage).toBeVisible();
  }

  async expectStatus(status: string) {
    await expect(this.statusBadge).toContainText(new RegExp(status, 'i'));
  }
}
