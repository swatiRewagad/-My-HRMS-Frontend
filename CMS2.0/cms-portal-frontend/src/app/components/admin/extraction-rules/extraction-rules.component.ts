import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ExtractionRulesService, ExtractionRule, ExtractionRuleRequest, ExtractionTestRequest, ExtractionTestResponse } from '../../../services/extraction-rules.service';

@Component({
  selector: 'app-extraction-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './extraction-rules.component.html',
  styleUrl: './extraction-rules.component.scss'
})
export class ExtractionRulesComponent implements OnInit {

  private rulesService = inject(ExtractionRulesService);
  private router = inject(Router);

  rules = signal<ExtractionRule[]>([]);
  targetFields = signal<string[]>([]);
  loading = signal(false);
  error = signal('');
  success = signal('');

  // Filters
  filterByName = '';
  filterByField = '';
  showActiveOnly = false;

  // Editor
  showEditor = signal(false);
  editingRule = signal<ExtractionRule | null>(null);
  form: ExtractionRuleRequest = this.getEmptyForm();

  // Tester
  showTester = signal(false);
  testSubject = '';
  testBody = '';
  testResult = signal<ExtractionTestResponse | null>(null);
  testing = signal(false);

  // Delete confirmation
  showDeleteConfirm = signal(false);
  ruleToDelete = signal<ExtractionRule | null>(null);

  ngOnInit() {
    this.loadRules();
    this.loadTargetFields();
  }

  loadRules() {
    this.loading.set(true);
    this.rulesService.getRules().subscribe({
      next: (data) => {
        this.rules.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load extraction rules');
        this.loading.set(false);
      }
    });
  }

  loadTargetFields() {
    this.rulesService.getTargetFields().subscribe({
      next: (fields) => this.targetFields.set(fields)
    });
  }

  get filteredRules(): ExtractionRule[] {
    return this.rules().filter(r => {
      if (this.showActiveOnly && !r.isActive) return false;
      if (this.filterByName && !r.ruleName.toLowerCase().includes(this.filterByName.toLowerCase())) return false;
      if (this.filterByField && r.targetField !== this.filterByField) return false;
      return true;
    });
  }

  openNewRule() {
    this.form = this.getEmptyForm();
    this.editingRule.set(null);
    this.showEditor.set(true);
    this.error.set('');
  }

  openEditRule(rule: ExtractionRule) {
    this.form = {
      ruleName: rule.ruleName,
      ruleCode: rule.ruleCode,
      description: rule.description,
      patternType: rule.patternType,
      pattern: rule.pattern,
      targetField: rule.targetField,
      extractGroup: rule.extractGroup,
      transform: rule.transform,
      priority: rule.priority,
      isActive: rule.isActive,
      sourceScope: rule.sourceScope
    };
    this.editingRule.set(rule);
    this.showEditor.set(true);
    this.error.set('');
  }

  saveRule() {
    this.error.set('');
    const editing = this.editingRule();

    if (editing) {
      this.rulesService.updateRule(editing.id, this.form).subscribe({
        next: () => {
          this.success.set('Rule updated successfully');
          this.showEditor.set(false);
          this.loadRules();
          this.clearSuccessAfterDelay();
        },
        error: (err) => this.error.set(err.error?.message || 'Failed to update rule')
      });
    } else {
      this.rulesService.createRule(this.form).subscribe({
        next: () => {
          this.success.set('Rule created successfully');
          this.showEditor.set(false);
          this.loadRules();
          this.clearSuccessAfterDelay();
        },
        error: (err) => this.error.set(err.error?.message || 'Failed to create rule')
      });
    }
  }

  toggleRule(rule: ExtractionRule) {
    this.rulesService.toggleRule(rule.id).subscribe({
      next: () => this.loadRules()
    });
  }

  confirmDelete(rule: ExtractionRule) {
    this.ruleToDelete.set(rule);
    this.showDeleteConfirm.set(true);
  }

  deleteRule() {
    const rule = this.ruleToDelete();
    if (!rule) return;
    this.rulesService.deleteRule(rule.id).subscribe({
      next: () => {
        this.success.set('Rule deleted');
        this.showDeleteConfirm.set(false);
        this.loadRules();
        this.clearSuccessAfterDelay();
      },
      error: () => this.error.set('Failed to delete rule')
    });
  }

  openTester() {
    this.showTester.set(true);
    this.testResult.set(null);
  }

  runTest() {
    this.testing.set(true);
    this.testResult.set(null);
    const req: ExtractionTestRequest = { subject: this.testSubject, body: this.testBody };
    this.rulesService.testRules(req).subscribe({
      next: (result) => {
        this.testResult.set(result);
        this.testing.set(false);
      },
      error: () => {
        this.error.set('Test failed');
        this.testing.set(false);
      }
    });
  }

  cancelEditor() {
    this.showEditor.set(false);
  }

  cancelTester() {
    this.showTester.set(false);
  }

  // Pattern Templates for non-technical users
  patternTemplates = [
    { label: '-- Select a template --', value: '', targetField: '', group: 0, name: '', description: '', type: 'REGEX' as const },
    { label: '6-digit Pincode', value: '\\b(\\d{6})\\b', targetField: 'complainantPincode', group: 1, name: 'Extract Pincode', description: 'Extracts 6-digit Indian PIN code', type: 'REGEX' as const },
    { label: '10-digit Phone Number', value: '\\b([6-9]\\d{9})\\b', targetField: 'complainantPhone', group: 1, name: 'Extract Phone', description: 'Extracts Indian 10-digit mobile number', type: 'REGEX' as const },
    { label: 'Email Address', value: '\\b([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})\\b', targetField: 'complainantEmail', group: 1, name: 'Extract Email', description: 'Extracts email address', type: 'REGEX' as const },
    { label: 'Amount (₹ / Rs.)', value: '(?:Rs\\.?|₹|INR)\\s*([\\d,]+(?:\\.\\d{2})?)', targetField: 'amountInvolved', group: 1, name: 'Extract Amount', description: 'Extracts amount after ₹, Rs., or INR', type: 'REGEX' as const },
    { label: 'Date (DD/MM/YYYY or DD-MM-YYYY)', value: '\\b(\\d{2}[/-]\\d{2}[/-]\\d{4})\\b', targetField: 'transactionDate', group: 1, name: 'Extract Date', description: 'Extracts date in DD/MM/YYYY or DD-MM-YYYY format', type: 'REGEX' as const },
    { label: 'Person Name (after "I am" / "My name is")', value: '(?:I am|My name is|I,)\\s+([A-Z][a-z]+(?: [A-Z][a-z]+){1,3})', targetField: 'complainantName', group: 1, name: 'Extract Name', description: 'Extracts name after "I am" or "My name is"', type: 'REGEX' as const },
    { label: 'CPGRAMS Reference Number', value: '(?:CPGRAMS?|cpgrams?)[:\\s]*([A-Z0-9/-]+)', targetField: 'cpgramsNumber', group: 1, name: 'Extract CPGRAMS', description: 'Extracts CPGRAMS reference number', type: 'REGEX' as const },
    { label: 'Bank Names (SBI, HDFC, ICICI, PNB...)', value: 'SBI, State Bank of India, HDFC, HDFC Bank, ICICI, ICICI Bank, PNB, Punjab National Bank, Bank of Baroda, Axis Bank, Kotak, Yes Bank, IndusInd, Canara Bank, Union Bank', targetField: 'entityName', group: 0, name: 'Detect Bank Name', description: 'Detects common Indian bank names', type: 'KEYWORD_LIST' as const },
    { label: 'Category - ATM related', value: 'ATM, cash withdrawal, ATM machine, ATM transaction, cash not dispensed, ATM debit', targetField: 'category', group: 0, name: 'Category ATM', description: 'Detects ATM-related complaints', type: 'KEYWORD_LIST' as const },
    { label: 'Category - UPI related', value: 'UPI, PhonePe, GPay, Google Pay, BHIM, Paytm, UPI transaction, UPI payment', targetField: 'category', group: 0, name: 'Category UPI', description: 'Detects UPI-related complaints', type: 'KEYWORD_LIST' as const },
    { label: 'Category - Credit Card', value: 'credit card, VISA, MasterCard, card payment, card charge, annual fee, card bill, card statement', targetField: 'category', group: 0, name: 'Category Credit Card', description: 'Detects credit card-related complaints', type: 'KEYWORD_LIST' as const },
    { label: 'Category - Loan / EMI', value: 'loan, EMI, mortgage, personal loan, home loan, loan recovery, loan interest, loan closure', targetField: 'category', group: 0, name: 'Category Loan', description: 'Detects loan-related complaints', type: 'KEYWORD_LIST' as const },
  ];

  applyTemplate(event: Event) {
    const select = event.target as HTMLSelectElement;
    const template = this.patternTemplates[select.selectedIndex];
    if (!template || !template.value) return;

    this.form.pattern = template.value;
    this.form.targetField = template.targetField;
    this.form.extractGroup = template.group;
    this.form.patternType = template.type;
    if (!this.form.ruleName) this.form.ruleName = template.name;
    if (!this.form.description) this.form.description = template.description;
  }

  private getEmptyForm(): ExtractionRuleRequest {
    return {
      ruleName: '',
      ruleCode: '',
      description: '',
      patternType: 'REGEX',
      pattern: '',
      targetField: '',
      extractGroup: 0,
      transform: '',
      priority: 5,
      isActive: true,
      sourceScope: 'BOTH'
    };
  }

  private clearSuccessAfterDelay() {
    setTimeout(() => this.success.set(''), 3000);
  }
}
