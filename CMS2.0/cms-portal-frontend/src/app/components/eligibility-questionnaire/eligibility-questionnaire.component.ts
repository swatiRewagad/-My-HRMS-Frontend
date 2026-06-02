import { Component, OnInit, inject, signal, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EligibilityService } from '../../services/eligibility.service';
import { EligibilityQuestion, EligibilityCheckResponse } from '../../models/eligibility.model';

@Component({
  selector: 'app-eligibility-questionnaire',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './eligibility-questionnaire.component.html',
  styleUrl: './eligibility-questionnaire.component.scss'
})
export class EligibilityQuestionnaireComponent implements OnInit {

  private eligibilityService = inject(EligibilityService);

  questions = signal<EligibilityQuestion[]>([]);
  answers = signal<Record<string, string>>({});
  result = signal<EligibilityCheckResponse | null>(null);
  loading = signal(false);
  error = signal('');

  eligible = output<boolean>();
  submitted = signal(false);

  ngOnInit() {
    this.loadQuestions();
  }

  private loadQuestions() {
    this.loading.set(true);
    this.eligibilityService.getQuestions().subscribe({
      next: (data) => {
        this.questions.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load eligibility questions. Please try again.');
        this.loading.set(false);
      }
    });
  }

  setAnswer(questionCode: string, value: string) {
    this.answers.update(prev => ({ ...prev, [questionCode]: value }));
  }

  isUnanswered(questionCode: string): boolean {
    return this.submitted() && !this.answers()[questionCode];
  }

  submitCheck() {
    this.submitted.set(true);

    if (!this.allMandatoryAnswered) {
      return;
    }

    this.loading.set(true);
    this.error.set('');
    this.result.set(null);

    this.eligibilityService.checkEligibility({
      channel: 'WEB_PORTAL',
      sessionId: crypto.randomUUID(),
      answers: this.answers()
    }).subscribe({
      next: (response) => {
        this.result.set(response);
        this.loading.set(false);
        if (response.outcome === 'ELIGIBLE') {
          this.eligible.emit(true);
        }
      },
      error: (err) => {
        this.error.set('Unable to verify eligibility. Please try again.');
        this.loading.set(false);
      }
    });
  }

  get allMandatoryAnswered(): boolean {
    const mandatory = this.questions().filter(q => q.mandatory);
    return mandatory.every(q => !!this.answers()[q.questionCode]);
  }
}
