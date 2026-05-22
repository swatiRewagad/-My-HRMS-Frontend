import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CategorizationRuleService, CategorizationRule, CategorizationResult } from '../../services/categorization-rule.service';
import { CmsService } from '../../services/cms.service';

@Component({
  selector: 'app-business-rules',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './business-rules.component.html',
  styleUrl: './business-rules.component.scss',
})
export class BusinessRulesComponent implements OnInit {
  rules: CategorizationRule[] = [];
  categories: any[] = [];
  loading = true;
  showForm = false;
  editingRule: CategorizationRule | null = null;
  showTestPanel = false;
  testText = '';
  testSource = 'all';
  testResults: CategorizationResult[] = [];
  testing = false;
  deleteConfirmId: number | null = null;

  get activeCount(): number {
    return this.rules.filter(r => r.status === 'active').length;
  }

  get inactiveCount(): number {
    return this.rules.filter(r => r.status === 'inactive').length;
  }

  formData: Partial<CategorizationRule> = {
    ruleName: '',
    keywords: '',
    categoryId: 0,
    priority: 'medium',
    status: 'active',
    source: 'all',
    description: '',
    ruleOrder: 0,
  };

  sources = [
    { value: 'all', label: 'All Sources' },
    { value: 'email', label: 'Email' },
    { value: 'physical', label: 'Physical Copy' },
    { value: 'online', label: 'Online Form' },
  ];

  priorities = [
    { value: 'low', label: 'Low' },
    { value: 'medium', label: 'Medium' },
    { value: 'high', label: 'High' },
    { value: 'critical', label: 'Critical' },
  ];

  constructor(
    private ruleService: CategorizationRuleService,
    private cmsService: CmsService,
  ) {}

  ngOnInit() {
    this.loadRules();
    this.loadCategories();
  }

  loadRules() {
    this.loading = true;
    this.ruleService.getAll().subscribe({
      next: (rules) => {
        this.rules = rules;
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      },
    });
  }

  loadCategories() {
    this.cmsService.getCategories().subscribe({
      next: (cats) => this.categories = cats,
    });
  }

  getCategoryName(categoryId: number): string {
    return this.categories.find(c => c.id === categoryId)?.name || 'Unknown';
  }

  getKeywordChips(keywords: string): string[] {
    return keywords.split(',').map(k => k.trim()).filter(k => k.length > 0);
  }

  openCreateForm() {
    this.editingRule = null;
    this.formData = {
      ruleName: '',
      keywords: '',
      categoryId: 0,
      priority: 'medium',
      status: 'active',
      source: 'all',
      description: '',
      ruleOrder: this.rules.length + 1,
    };
    this.showForm = true;
  }

  openEditForm(rule: CategorizationRule) {
    this.editingRule = rule;
    this.formData = { ...rule };
    this.showForm = true;
  }

  closeForm() {
    this.showForm = false;
    this.editingRule = null;
  }

  saveRule() {
    if (!this.formData.ruleName || !this.formData.keywords || !this.formData.categoryId) return;

    if (this.editingRule?.id) {
      this.ruleService.update(this.editingRule.id, this.formData).subscribe({
        next: () => {
          this.loadRules();
          this.closeForm();
        },
      });
    } else {
      this.ruleService.create(this.formData).subscribe({
        next: () => {
          this.loadRules();
          this.closeForm();
        },
      });
    }
  }

  confirmDelete(id: number) {
    this.deleteConfirmId = id;
  }

  cancelDelete() {
    this.deleteConfirmId = null;
  }

  deleteRule(id: number) {
    this.ruleService.delete(id).subscribe({
      next: () => {
        this.deleteConfirmId = null;
        this.loadRules();
      },
    });
  }

  toggleStatus(rule: CategorizationRule) {
    const newStatus = rule.status === 'active' ? 'inactive' : 'active';
    this.ruleService.update(rule.id!, { status: newStatus }).subscribe({
      next: () => this.loadRules(),
    });
  }

  toggleTestPanel() {
    this.showTestPanel = !this.showTestPanel;
    this.testResults = [];
  }

  runTest() {
    if (!this.testText.trim()) return;
    this.testing = true;
    this.ruleService.categorize(this.testText, this.testSource).subscribe({
      next: (results) => {
        this.testResults = results;
        this.testing = false;
      },
      error: () => {
        this.testing = false;
      },
    });
  }

  getPriorityClass(priority: string): string {
    return `priority-${priority}`;
  }

  getStatusClass(status: string): string {
    return `status-${status}`;
  }
}
