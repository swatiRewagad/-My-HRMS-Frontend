import { Component, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RulesService, RuleDefinition, RuleCategory, DeploymentResponse } from '../../../services/rules.service';

@Component({
  selector: 'app-rules-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rules-management.component.html',
  styleUrl: './rules-management.component.scss'
})
export class RulesManagementComponent implements OnInit {

  rules = signal<RuleDefinition[]>([]);
  categories = signal<RuleCategory[]>([]);
  selectedCategory = signal<string>('');
  selectedStatus = signal<string>('');
  loading = signal(false);
  deploymentResult = signal<DeploymentResponse | null>(null);
  currentUser = signal('admin');
  approverUser = signal('supervisor');
  errorMessage = signal('');
  successMessage = signal('');

  filteredRules = computed(() => {
    let result = this.rules();
    if (this.selectedCategory()) {
      result = result.filter(r => r.categoryCode === this.selectedCategory());
    }
    if (this.selectedStatus()) {
      result = result.filter(r => r.status === this.selectedStatus());
    }
    return result;
  });

  constructor(private rulesService: RulesService, private router: Router) {}

  ngOnInit() {
    this.loadCategories();
    this.loadRules();
  }

  loadCategories() {
    this.rulesService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: (err) => this.showError('Failed to load categories')
    });
  }

  loadRules() {
    this.loading.set(true);
    this.rulesService.getRules().subscribe({
      next: (rules) => {
        this.rules.set(rules);
        this.loading.set(false);
      },
      error: () => {
        this.showError('Failed to load rules');
        this.loading.set(false);
      }
    });
  }

  createRule() {
    this.router.navigate(['/admin/rules/new']);
  }

  editRule(rule: RuleDefinition) {
    this.router.navigate(['/admin/rules/edit', rule.id]);
  }

  activateRule(rule: RuleDefinition) {
    if (this.approverUser() === rule.createdBy) {
      this.showError('Maker-Checker: Approver must be different from creator');
      return;
    }
    this.rulesService.activateRule(rule.id, this.approverUser()).subscribe({
      next: () => {
        this.showSuccess(`Rule "${rule.ruleName}" activated`);
        this.loadRules();
      },
      error: () => this.showError('Failed to activate rule')
    });
  }

  deactivateRule(rule: RuleDefinition) {
    this.rulesService.deactivateRule(rule.id, this.currentUser()).subscribe({
      next: () => {
        this.showSuccess(`Rule "${rule.ruleName}" deactivated`);
        this.loadRules();
      },
      error: () => this.showError('Failed to deactivate rule')
    });
  }

  rollbackRule(rule: RuleDefinition) {
    this.rulesService.rollbackRule(rule.id, this.currentUser()).subscribe({
      next: () => {
        this.showSuccess(`Rule "${rule.ruleName}" rolled back`);
        this.loadRules();
      },
      error: () => this.showError('Failed to rollback rule')
    });
  }

  deployRules() {
    this.loading.set(true);
    this.rulesService.deployRules(this.currentUser()).subscribe({
      next: (result) => {
        this.deploymentResult.set(result);
        this.loading.set(false);
        if (result.status === 'SUCCESS') {
          this.showSuccess(`Deployed ${result.rulesDeployed} rules successfully`);
        } else {
          this.showError(`Deployment failed: ${result.error}`);
        }
      },
      error: () => {
        this.loading.set(false);
        this.showError('Deployment request failed');
      }
    });
  }

  testRules() {
    this.router.navigate(['/admin/rules/test']);
  }

  viewHistory(rule: RuleDefinition) {
    this.router.navigate(['/admin/rules/history', rule.id]);
  }

  getStatusClass(status: string): string {
    const classes: Record<string, string> = {
      'ACTIVE': 'status-active',
      'DRAFT': 'status-draft',
      'INACTIVE': 'status-inactive',
      'PENDING_REVIEW': 'status-pending',
      'ARCHIVED': 'status-archived'
    };
    return classes[status] || '';
  }

  private showError(msg: string) {
    this.errorMessage.set(msg);
    this.successMessage.set('');
    setTimeout(() => this.errorMessage.set(''), 5000);
  }

  private showSuccess(msg: string) {
    this.successMessage.set(msg);
    this.errorMessage.set('');
    setTimeout(() => this.successMessage.set(''), 5000);
  }
}
