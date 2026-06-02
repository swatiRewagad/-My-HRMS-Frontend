import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { RulesService, RuleDefinition, RuleCategory, RuleRequest, ValidationResponse } from '../../../services/rules.service';

@Component({
  selector: 'app-rule-editor',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rule-editor.component.html',
  styleUrl: './rule-editor.component.scss'
})
export class RuleEditorComponent implements OnInit {

  isEdit = signal(false);
  ruleId = signal<number | null>(null);
  categories = signal<RuleCategory[]>([]);
  loading = signal(false);
  validating = signal(false);
  validation = signal<ValidationResponse | null>(null);
  errorMessage = signal('');
  successMessage = signal('');
  currentUser = signal('admin');

  form = {
    ruleCode: '',
    ruleName: '',
    categoryCode: 'ASSIGNMENT',
    drlContent: '',
    salience: 100,
    changeReason: ''
  };

  drlTemplate = `package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "Rule Name Here"
    salience 100
    when
        $fact : AssignmentFact(category == "YOUR_CATEGORY")
    then
        $fact.setAssignedTeam("YOUR_TEAM");
        update($fact);
end`;

  constructor(
    private rulesService: RulesService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit() {
    this.loadCategories();

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit.set(true);
      this.ruleId.set(+id);
      this.loadRule(+id);
    } else {
      this.form.drlContent = this.drlTemplate;
    }
  }

  loadCategories() {
    this.rulesService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats)
    });
  }

  loadRule(id: number) {
    this.loading.set(true);
    this.rulesService.getRule(id).subscribe({
      next: (rule) => {
        this.form.ruleCode = rule.ruleCode;
        this.form.ruleName = rule.ruleName;
        this.form.categoryCode = rule.categoryCode;
        this.form.drlContent = rule.drlContent;
        this.form.salience = rule.salience;
        this.loading.set(false);
      },
      error: () => {
        this.showError('Failed to load rule');
        this.loading.set(false);
      }
    });
  }

  validateDrl() {
    this.validating.set(true);
    this.validation.set(null);
    this.rulesService.validateDrl(this.form.drlContent).subscribe({
      next: (result) => {
        this.validation.set(result);
        this.validating.set(false);
      },
      error: () => {
        this.validating.set(false);
        this.showError('Validation request failed');
      }
    });
  }

  save() {
    if (!this.form.ruleCode || !this.form.ruleName || !this.form.drlContent) {
      this.showError('Please fill all required fields');
      return;
    }

    const request: RuleRequest = {
      ruleCode: this.form.ruleCode,
      ruleName: this.form.ruleName,
      categoryCode: this.form.categoryCode,
      drlContent: this.form.drlContent,
      salience: this.form.salience,
      changeReason: this.form.changeReason
    };

    this.loading.set(true);

    if (this.isEdit()) {
      this.rulesService.updateRule(this.ruleId()!, request, this.currentUser()).subscribe({
        next: () => {
          this.showSuccess('Rule updated (new version created)');
          this.loading.set(false);
          setTimeout(() => this.router.navigate(['/admin/rules']), 1500);
        },
        error: () => {
          this.showError('Failed to update rule');
          this.loading.set(false);
        }
      });
    } else {
      this.rulesService.createRule(request, this.currentUser()).subscribe({
        next: () => {
          this.showSuccess('Rule created in DRAFT status');
          this.loading.set(false);
          setTimeout(() => this.router.navigate(['/admin/rules']), 1500);
        },
        error: () => {
          this.showError('Failed to create rule');
          this.loading.set(false);
        }
      });
    }
  }

  cancel() {
    this.router.navigate(['/admin/rules']);
  }

  onCategoryChange() {
    if (!this.isEdit() && this.form.drlContent === this.drlTemplate) {
      const templates: Record<string, string> = {
        'ASSIGNMENT': `package com.rbi.cms.rules.assignment;\nimport com.rbi.cms.common.dto.AssignmentFact;\n\nrule "Rule Name Here"\n    salience 100\n    when\n        $fact : AssignmentFact(category == "YOUR_CATEGORY")\n    then\n        $fact.setAssignedTeam("YOUR_TEAM");\n        update($fact);\nend`,
        'ESCALATION': `package com.rbi.cms.rules.escalation;\nimport com.rbi.cms.common.dto.EscalationFact;\n\nrule "Rule Name Here"\n    salience 100\n    when\n        $fact : EscalationFact(slaPercentElapsed >= 80)\n    then\n        $fact.setEscalationLevel(1);\n        $fact.setEscalationAction("NOTIFY_OFFICER");\n        $fact.setEscalationMessage("Message here");\n        update($fact);\nend`
      };
      this.form.drlContent = templates[this.form.categoryCode] || this.drlTemplate;
    }
  }

  private showError(msg: string) {
    this.errorMessage.set(msg);
    this.successMessage.set('');
    setTimeout(() => this.errorMessage.set(''), 5000);
  }

  private showSuccess(msg: string) {
    this.successMessage.set(msg);
    this.errorMessage.set('');
  }
}
