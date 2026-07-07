import { type Locator, type Page, expect } from '@playwright/test';

export class TaskActionPage {
  readonly page: Page;
  readonly complaintNumber: Locator;
  readonly complainantName: Locator;
  readonly status: Locator;
  readonly priority: Locator;
  readonly category: Locator;
  readonly timeline: Locator;
  readonly timelineEntries: Locator;
  readonly mrePanel: Locator;
  readonly mreSignal: Locator;
  readonly mreGrounds: Locator;
  readonly mreSuggestedDetermination: Locator;
  readonly mreDraftRationale: Locator;
  readonly mreCompensationBand: Locator;
  readonly precedentCases: Locator;
  readonly acceptButton: Locator;
  readonly overrideButton: Locator;
  readonly escalateButton: Locator;
  readonly resolveButton: Locator;
  readonly rejectButton: Locator;
  readonly forwardToReButton: Locator;
  readonly remarksInput: Locator;
  readonly awardAmountInput: Locator;
  readonly confirmActionButton: Locator;
  readonly attachments: Locator;
  readonly backButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.complaintNumber = page.locator('[data-testid="complaint-number"], .complaint-number');
    this.complainantName = page.locator('[data-testid="complainant-name"]');
    this.status = page.locator('[data-testid="status"], .status-badge');
    this.priority = page.locator('[data-testid="priority"], .priority-badge');
    this.category = page.locator('[data-testid="category"]');
    this.timeline = page.locator('.timeline, [data-testid="timeline"]');
    this.timelineEntries = page.locator('.timeline-entry, [data-testid="timeline-entry"]');
    this.mrePanel = page.locator('[data-testid="mre-panel"], .mre-copilot, .copilot-panel');
    this.mreSignal = page.locator('[data-testid="mre-signal"], .triage-signal');
    this.mreGrounds = page.locator('[data-testid="mre-grounds"] li, .ground-item');
    this.mreSuggestedDetermination = page.locator('[data-testid="suggested-determination"]');
    this.mreDraftRationale = page.locator('[data-testid="draft-rationale"]');
    this.mreCompensationBand = page.locator('[data-testid="compensation-band"]');
    this.precedentCases = page.locator('[data-testid="precedent-cases"] .case-card, .precedent-item');
    this.acceptButton = page.getByRole('button', { name: /accept|approve/i });
    this.overrideButton = page.getByRole('button', { name: /override/i });
    this.escalateButton = page.getByRole('button', { name: /escalate/i });
    this.resolveButton = page.getByRole('button', { name: /resolve/i });
    this.rejectButton = page.getByRole('button', { name: /reject|non.maintainable/i });
    this.forwardToReButton = page.getByRole('button', { name: /forward.*RE|forward.*entity/i });
    this.remarksInput = page.getByLabel(/remarks|reason|rationale/i);
    this.awardAmountInput = page.getByLabel(/award.*amount|compensation/i);
    this.confirmActionButton = page.getByRole('button', { name: /confirm|yes|proceed/i });
    this.attachments = page.locator('[data-testid="attachments"] .attachment, .file-item');
    this.backButton = page.getByRole('button', { name: /back|return/i });
  }

  async goto(complaintId: string) {
    await this.page.goto(`/staff/task-action/${complaintId}`);
  }

  async expectMrePanelLoaded() {
    await expect(this.mrePanel).toBeVisible({ timeout: 15000 });
    await expect(this.mreSignal).toBeVisible();
  }

  async expectMreSignal(signal: 'GREEN' | 'RED' | 'AMBER') {
    await expect(this.mreSignal).toContainText(signal);
  }

  async expectGroundsCount(count: number) {
    await expect(this.mreGrounds).toHaveCount(count);
  }

  async acceptMreSuggestion() {
    await this.acceptButton.click();
    await this.confirmActionButton.click();
  }

  async overrideMreSuggestion(determination: string, rationale: string) {
    await this.overrideButton.click();
    await this.remarksInput.fill(rationale);
    await this.confirmActionButton.click();
  }

  async escalateComplaint(remarks: string) {
    await this.escalateButton.click();
    await this.remarksInput.fill(remarks);
    await this.confirmActionButton.click();
  }

  async resolveComplaint(remarks: string, awardAmount?: string) {
    await this.resolveButton.click();
    await this.remarksInput.fill(remarks);
    if (awardAmount) {
      await this.awardAmountInput.fill(awardAmount);
    }
    await this.confirmActionButton.click();
  }

  async rejectComplaint(remarks: string) {
    await this.rejectButton.click();
    await this.remarksInput.fill(remarks);
    await this.confirmActionButton.click();
  }

  async forwardToRe() {
    await this.forwardToReButton.click();
    await this.confirmActionButton.click();
  }

  async expectTimelineHasEntries() {
    const count = await this.timelineEntries.count();
    expect(count).toBeGreaterThan(0);
  }

  async getComplaintNumberText(): Promise<string> {
    return (await this.complaintNumber.textContent()) || '';
  }

  async expectMreError() {
    await expect(
      this.page.getByText(/unable.*assess|service.*unavailable|error/i)
    ).toBeVisible({ timeout: 10000 });
  }
}
