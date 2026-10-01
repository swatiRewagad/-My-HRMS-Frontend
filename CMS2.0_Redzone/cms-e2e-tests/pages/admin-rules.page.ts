import { type Locator, type Page, expect } from '@playwright/test';

export class AdminRulesPage {
  readonly page: Page;
  readonly rulesList: Locator;
  readonly ruleRows: Locator;
  readonly categoryFilter: Locator;
  readonly statusFilter: Locator;
  readonly createRuleButton: Locator;
  readonly searchInput: Locator;
  readonly ruleEditor: Locator;
  readonly drlEditor: Locator;
  readonly saveButton: Locator;
  readonly activateButton: Locator;
  readonly deactivateButton: Locator;
  readonly deployButton: Locator;
  readonly deploymentHistory: Locator;

  constructor(page: Page) {
    this.page = page;
    this.rulesList = page.locator('[data-testid="rules-list"], .rules-table');
    this.ruleRows = page.locator('[data-testid="rule-row"], .rules-table tbody tr');
    this.categoryFilter = page.getByLabel(/category/i);
    this.statusFilter = page.getByLabel(/status/i);
    this.createRuleButton = page.getByRole('button', { name: /create.*rule|new.*rule/i });
    this.searchInput = page.getByPlaceholder(/search.*rule/i);
    this.ruleEditor = page.locator('[data-testid="rule-editor"], .rule-form');
    this.drlEditor = page.locator('[data-testid="drl-editor"], .code-editor');
    this.saveButton = page.getByRole('button', { name: /save/i });
    this.activateButton = page.getByRole('button', { name: /activate/i });
    this.deactivateButton = page.getByRole('button', { name: /deactivate/i });
    this.deployButton = page.getByRole('button', { name: /deploy/i });
    this.deploymentHistory = page.locator('[data-testid="deployment-history"]');
  }

  async goto() {
    await this.page.goto('/admin/rules');
  }

  async waitForRulesLoad() {
    await expect(this.rulesList).toBeVisible({ timeout: 10000 });
  }

  async getRuleCount(): Promise<number> {
    return this.ruleRows.count();
  }

  async filterByCategory(category: string) {
    await this.categoryFilter.selectOption(category);
    await this.page.waitForTimeout(500);
  }

  async filterByStatus(status: string) {
    await this.statusFilter.selectOption(status);
    await this.page.waitForTimeout(500);
  }

  async searchRule(query: string) {
    await this.searchInput.fill(query);
    await this.page.waitForTimeout(500);
  }

  async clickRule(ruleCode: string) {
    await this.page.getByText(ruleCode).click();
    await expect(this.ruleEditor).toBeVisible();
  }

  async deployRules() {
    await this.deployButton.click();
    await this.page.getByRole('button', { name: /confirm/i }).click();
    await expect(this.page.getByText(/deployed|success/i)).toBeVisible({ timeout: 15000 });
  }

  async expectRuleInList(ruleCode: string) {
    await expect(this.page.getByText(ruleCode)).toBeVisible();
  }
}
