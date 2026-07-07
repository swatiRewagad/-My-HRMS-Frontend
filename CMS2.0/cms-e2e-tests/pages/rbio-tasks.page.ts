import { type Locator, type Page, expect } from '@playwright/test';

export class RbioTasksPage {
  readonly page: Page;
  readonly statsCards: Locator;
  readonly complaintGrid: Locator;
  readonly gridRows: Locator;
  readonly searchInput: Locator;
  readonly statusFilter: Locator;
  readonly priorityFilter: Locator;
  readonly dateRangeFilter: Locator;
  readonly refreshButton: Locator;
  readonly exportButton: Locator;
  readonly pagination: Locator;
  readonly nextPageButton: Locator;
  readonly prevPageButton: Locator;
  readonly rowsPerPageSelector: Locator;
  readonly loadingSpinner: Locator;
  readonly emptyState: Locator;

  constructor(page: Page) {
    this.page = page;
    this.statsCards = page.locator('.stats-card, .stat-card, [data-testid="stats"] .card');
    this.complaintGrid = page.locator('table, .ag-root, [data-testid="complaint-grid"]');
    this.gridRows = page.locator('table tbody tr, .ag-row, [data-testid="grid-row"]');
    this.searchInput = page.getByPlaceholder(/search|filter/i);
    this.statusFilter = page.getByLabel(/status/i);
    this.priorityFilter = page.getByLabel(/priority/i);
    this.dateRangeFilter = page.locator('[data-testid="date-filter"]');
    this.refreshButton = page.getByRole('button', { name: /refresh/i });
    this.exportButton = page.getByRole('button', { name: /export|download/i });
    this.pagination = page.locator('.pagination, mat-paginator, [data-testid="paginator"]');
    this.nextPageButton = page.getByRole('button', { name: /next page/i });
    this.prevPageButton = page.getByRole('button', { name: /previous page/i });
    this.rowsPerPageSelector = page.locator('mat-paginator select, [data-testid="page-size"]');
    this.loadingSpinner = page.locator('.loading, mat-spinner, [data-testid="loading"]');
    this.emptyState = page.locator('.empty-state, [data-testid="empty"]');
  }

  async goto() {
    await this.page.goto('/staff/rbio/tasks');
  }

  async waitForGridLoad() {
    await this.loadingSpinner.waitFor({ state: 'hidden', timeout: 15000 }).catch(() => {});
    await expect(this.complaintGrid).toBeVisible({ timeout: 10000 });
  }

  async getRowCount(): Promise<number> {
    return this.gridRows.count();
  }

  async clickComplaint(complaintNumber: string) {
    await this.page.getByText(complaintNumber).click();
    await this.page.waitForURL(/.*task-action|complaint-detail.*/);
  }

  async searchByText(query: string) {
    await this.searchInput.fill(query);
    await this.page.waitForTimeout(500);
  }

  async filterByStatus(status: string) {
    await this.statusFilter.selectOption(status);
    await this.page.waitForTimeout(500);
  }

  async filterByPriority(priority: string) {
    await this.priorityFilter.selectOption(priority);
    await this.page.waitForTimeout(500);
  }

  async getStatsCount(cardIndex: number): Promise<string> {
    return (await this.statsCards.nth(cardIndex).textContent()) || '0';
  }

  async expectGridHasData() {
    const count = await this.getRowCount();
    expect(count).toBeGreaterThan(0);
  }

  async expectComplaintInGrid(complaintNumber: string) {
    await expect(this.page.getByText(complaintNumber)).toBeVisible();
  }
}
