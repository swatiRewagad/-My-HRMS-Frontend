import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';

interface ExclusionRule {
  id: number | null;
  exclusionType: string;
  description: string;
  conditionJson: string;
  active: boolean;
  createdAt: string;
}

@Component({
  selector: 'app-fallback-config',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './fallback-config.component.html',
  styleUrl: './fallback-config.component.scss'
})
export class FallbackConfigComponent implements OnInit {

  exclusionRules = signal<ExclusionRule[]>([]);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');

  newRule: Partial<ExclusionRule> = { exclusionType: 'USER_LIST', description: '', conditionJson: '' };
  showAddForm = signal(false);

  private baseUrl = '/cms-assignment/api/v1/assignment/exclusions';

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadRules();
  }

  loadRules() {
    this.loading.set(true);
    this.http.get<ExclusionRule[]>(this.baseUrl).subscribe({
      next: (rules) => {
        this.exclusionRules.set(rules);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Failed to load exclusion rules');
      }
    });
  }

  createRule() {
    if (!this.newRule.exclusionType || !this.newRule.description) {
      this.errorMessage.set('Type and description are required');
      return;
    }

    this.http.post<ExclusionRule>(this.baseUrl, {
      exclusionType: this.newRule.exclusionType,
      description: this.newRule.description,
      conditionJson: this.newRule.conditionJson || null
    }).subscribe({
      next: () => {
        this.showSuccess('Exclusion rule created');
        this.newRule = { exclusionType: 'USER_LIST', description: '', conditionJson: '' };
        this.showAddForm.set(false);
        this.loadRules();
      },
      error: () => this.errorMessage.set('Failed to create exclusion rule')
    });
  }

  toggleActive(rule: ExclusionRule) {
    this.http.put<ExclusionRule>(`${this.baseUrl}/${rule.id}`, { active: !rule.active }).subscribe({
      next: () => this.loadRules(),
      error: () => this.errorMessage.set('Failed to update rule')
    });
  }

  deleteRule(rule: ExclusionRule) {
    if (!confirm(`Delete exclusion rule "${rule.description}"?`)) return;
    this.http.delete(`${this.baseUrl}/${rule.id}`).subscribe({
      next: () => {
        this.showSuccess('Rule deleted');
        this.loadRules();
      },
      error: () => this.errorMessage.set('Failed to delete rule')
    });
  }

  private showSuccess(msg: string) {
    this.successMessage.set(msg);
    this.errorMessage.set('');
  }
}
