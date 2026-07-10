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
    this.nameInput = page.locator('input[name="firstName"]');
    this.emailInput = page.locator('input[name="email"]');
    this.phoneInput = page.locator('input[name="phone"]');
    this.addressInput = page.locator('textarea[name="address"]');
    this.entityTypeSelect = page.locator('select.entity-select');
    this.bankSelect = page.locator('input[name="entityName"]');
    this.branchInput = page.locator('input[name="entityBranch"]');
    this.accountInput = page.locator('input[name="savingsAccountNumber"]');
    this.categorySelect = page.locator('select[name="complaintCategory"]');
    this.subjectInput = page.locator('input[name="subject"]');
    this.descriptionInput = page.locator('textarea[name="complaintText"]');
    this.reliefInput = page.locator('input[name="reliefSought"]');
    this.priorComplaintYes = page.getByRole('radio', { name: /yes/i });
    this.priorComplaintNo = page.getByRole('radio', { name: /no/i });
    this.reComplaintDateInput = page.locator('input[name="reComplaintDate"]');
    this.reReferenceInput = page.locator('input[name="reReference"]');
    this.reRepliedYes = page.getByRole('radio', { name: /yes/i });
    this.reRepliedNo = page.getByRole('radio', { name: /no/i });
    this.submitButton = page.locator('button.btn-submit-complaint');
    this.captchaInput = page.locator('input[name="captcha"]');
    this.pincodeInput = page.locator('input[name="pincode"]');
    this.stepIndicator = page.locator('.step-header');
    this.nextButton = page.locator('button.btn-next');
    this.prevButton = page.locator('button.btn-go-back');
  }

  async goto() {
    await this.page.goto('/public/file-complaint', { waitUntil: 'networkidle' });
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
