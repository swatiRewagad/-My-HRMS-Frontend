import { type Locator, type Page, expect } from '@playwright/test';

export class FileComplaintPage {
  readonly page: Page;
  readonly nameInput: Locator;
  readonly emailInput: Locator;
  readonly phoneInput: Locator;
  readonly addressInput: Locator;
  readonly entityTypeSelect: Locator;
  readonly bankSelect: Locator;
  readonly branchInput: Locator;
  readonly accountInput: Locator;
  readonly categorySelect: Locator;
  readonly subjectInput: Locator;
  readonly descriptionInput: Locator;
  readonly reliefInput: Locator;
  readonly priorComplaintYes: Locator;
  readonly priorComplaintNo: Locator;
  readonly reComplaintDateInput: Locator;
  readonly reReferenceInput: Locator;
  readonly reRepliedYes: Locator;
  readonly reRepliedNo: Locator;
  readonly submitButton: Locator;
  readonly captchaInput: Locator;
  readonly pincodeInput: Locator;
  readonly stepIndicator: Locator;
  readonly nextButton: Locator;
  readonly prevButton: Locator;

  constructor(page: Page) {
    this.page = page;
    this.nameInput = page.getByLabel(/full name|name/i);
    this.emailInput = page.getByLabel(/email/i);
    this.phoneInput = page.getByLabel(/phone|mobile/i);
    this.addressInput = page.getByLabel(/address/i);
    this.entityTypeSelect = page.getByLabel(/entity type/i);
    this.bankSelect = page.getByLabel(/entity name|bank/i);
    this.branchInput = page.getByLabel(/branch/i);
    this.accountInput = page.getByLabel(/account|card number/i);
    this.categorySelect = page.getByLabel(/nature.*complaint|category/i);
    this.subjectInput = page.getByLabel(/subject/i);
    this.descriptionInput = page.getByLabel(/description/i);
    this.reliefInput = page.getByLabel(/relief/i);
    this.priorComplaintYes = page.getByLabel(/did you.*complain.*entity/i).locator('..').getByText('Yes');
    this.priorComplaintNo = page.getByLabel(/did you.*complain.*entity/i).locator('..').getByText('No');
    this.reComplaintDateInput = page.getByLabel(/date.*complaint.*entity/i);
    this.reReferenceInput = page.getByLabel(/reference number/i);
    this.reRepliedYes = page.getByLabel(/entity reply.*dissatisfied/i).locator('..').getByText('Yes');
    this.reRepliedNo = page.getByLabel(/entity reply.*dissatisfied/i).locator('..').getByText('No');
    this.submitButton = page.getByRole('button', { name: /submit|file complaint/i });
    this.captchaInput = page.getByLabel(/captcha/i);
    this.pincodeInput = page.getByLabel(/pincode|pin code/i);
    this.stepIndicator = page.locator('.step-indicator, mat-step-header');
    this.nextButton = page.getByRole('button', { name: /next|continue/i });
    this.prevButton = page.getByRole('button', { name: /previous|back/i });
  }

  async goto() {
    await this.page.goto('/file-complaint');
  }

  async fillPersonalDetails(data: {
    name: string;
    email: string;
    phone: string;
    address?: string;
  }) {
    await this.nameInput.fill(data.name);
    await this.emailInput.fill(data.email);
    await this.phoneInput.fill(data.phone);
    if (data.address) {
      await this.addressInput.fill(data.address);
    }
  }

  async fillEntityDetails(data: {
    entityType?: string;
    bank: string;
    branch?: string;
    account?: string;
  }) {
    if (data.entityType) {
      await this.entityTypeSelect.selectOption(data.entityType);
    }
    await this.bankSelect.fill(data.bank);
    await this.page.getByRole('option').first().click();
    if (data.branch) await this.branchInput.fill(data.branch);
    if (data.account) await this.accountInput.fill(data.account);
  }

  async fillComplaintDetails(data: {
    category: string;
    subject: string;
    description: string;
    relief: string;
  }) {
    await this.categorySelect.selectOption(data.category);
    await this.subjectInput.fill(data.subject);
    await this.descriptionInput.fill(data.description);
    await this.reliefInput.fill(data.relief);
  }

  async fillPriorComplaint(data: {
    priorComplaint: boolean;
    date?: string;
    reference?: string;
    replied?: boolean;
  }) {
    if (data.priorComplaint) {
      await this.priorComplaintYes.click();
      if (data.date) await this.reComplaintDateInput.fill(data.date);
      if (data.reference) await this.reReferenceInput.fill(data.reference);
      if (data.replied) await this.reRepliedYes.click();
      else await this.reRepliedNo.click();
    } else {
      await this.priorComplaintNo.click();
    }
  }

  async fillPincode(pincode: string) {
    await this.pincodeInput.fill(pincode);
    await this.page.waitForTimeout(500);
  }

  async submit() {
    await this.submitButton.click();
  }

  async expectSuccess() {
    await expect(
      this.page.getByText(/complaint.*registered|successfully.*filed|CMS\/\d{4}/i)
    ).toBeVisible({ timeout: 10000 });
  }

  async expectValidationError(field: string) {
    await expect(
      this.page.locator(`[formcontrolname="${field}"]`).locator('..').getByText(/required|invalid/i)
    ).toBeVisible();
  }
}
