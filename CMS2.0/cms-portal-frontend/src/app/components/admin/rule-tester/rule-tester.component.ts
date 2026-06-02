import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { RulesService, RuleTestResponse } from '../../../services/rules.service';

@Component({
  selector: 'app-rule-tester',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-tester.component.html',
  styleUrl: './rule-tester.component.scss'
})
export class RuleTesterComponent {

  selectedCategory = signal('ASSIGNMENT');
  loading = signal(false);
  result = signal<RuleTestResponse | null>(null);
  errorMessage = signal('');

  assignmentInput = {
    complaintId: 'TEST-001',
    category: 'ATM',
    priority: 'MEDIUM',
    amountInvolved: 5000
  };

  escalationInput = {
    complaintId: 'TEST-001',
    category: 'ATM',
    priority: 'MEDIUM',
    slaPercentElapsed: 50,
    amountInvolved: 500000,
    currentDaysOpen: 15
  };

  assignmentCategories = ['ATM', 'UPI', 'NEFT_RTGS', 'LOAN', 'CREDIT_CARD', 'DIGITAL_LENDING', 'GOLD_LOAN', 'INSURANCE', 'GENERAL'];
  priorities = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

  constructor(private rulesService: RulesService, private router: Router) {}

  runTest() {
    this.loading.set(true);
    this.result.set(null);
    this.errorMessage.set('');

    const inputFacts = this.selectedCategory() === 'ASSIGNMENT'
      ? { ...this.assignmentInput }
      : { ...this.escalationInput };

    this.rulesService.testRules({
      categoryCode: this.selectedCategory(),
      inputFacts
    }).subscribe({
      next: (res) => {
        this.result.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Test execution failed');
        this.loading.set(false);
      }
    });
  }

  back() {
    this.router.navigate(['/admin/rules']);
  }
}
