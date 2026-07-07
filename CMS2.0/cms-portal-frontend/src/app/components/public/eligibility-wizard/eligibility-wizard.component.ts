import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

interface WizardQuestion {
  id: number;
  key: string;
  question: string;
  hint: string;
  type: 'radio' | 'date' | 'select';
  options?: { label: string; value: string }[];
}

interface MreEligibilityResult {
  eligible: boolean;
  outcome: 'READY' | 'TOO_EARLY' | 'TOO_LATE' | 'RE_FIRST' | 'NOT_COVERED' | 'RANT_GATE';
  message: string;
  daysRemaining?: number;
  deadlineDate?: string;
  windowOpenDate?: string;
  filingDeadlineDate?: string;
  reWindowDays?: number;
  filingDeadlineDays?: number;
  compensationBand?: string;
}

@Component({
  selector: 'app-eligibility-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './eligibility-wizard.component.html',
  styleUrl: './eligibility-wizard.component.scss'
})
export class EligibilityWizardComponent implements OnInit {

  private router = inject(Router);
  private http = inject(HttpClient);

  phase = signal<'questions' | 'checking' | 'result'>('questions');
  currentStep = signal(0);
  answers = signal<Record<string, string>>({});
  result = signal<MreEligibilityResult | null>(null);
  error = signal('');

  questions: WizardQuestion[] = [
    {
      id: 1,
      key: 'entityType',
      question: 'wizard.q1_entity_type',
      hint: 'wizard.q1_hint',
      type: 'select',
      options: [
        { label: 'wizard.opt_bank', value: 'BANK' },
        { label: 'wizard.opt_nbfc', value: 'NBFC' },
        { label: 'wizard.opt_psp', value: 'PSP' },
        { label: 'wizard.opt_cic', value: 'CIC' },
        { label: 'wizard.opt_unknown', value: 'UNKNOWN' },
      ]
    },
    {
      id: 2,
      key: 'complainedToRE',
      question: 'wizard.q2_complained_to_re',
      hint: 'wizard.q2_hint',
      type: 'radio',
      options: [
        { label: 'wizard.opt_yes_complained', value: 'YES' },
        { label: 'wizard.opt_no_not_yet', value: 'NO' },
      ]
    },
    {
      id: 3,
      key: 'reComplaintDate',
      question: 'wizard.q3_complaint_date',
      hint: 'wizard.q3_hint',
      type: 'date'
    },
    {
      id: 4,
      key: 'reRespondedSatisfactorily',
      question: 'wizard.q4_re_response',
      hint: 'wizard.q4_hint',
      type: 'radio',
      options: [
        { label: 'wizard.opt_no_reply', value: 'NO_REPLY' },
        { label: 'wizard.opt_dissatisfied', value: 'DISSATISFIED' },
        { label: 'wizard.opt_resolved', value: 'RESOLVED' },
      ]
    },
  ];

  totalSteps = computed(() => {
    const a = this.answers();
    if (a['complainedToRE'] === 'NO') return 2;
    return this.questions.length;
  });

  visibleQuestions = computed(() => {
    const a = this.answers();
    if (a['complainedToRE'] === 'NO') return this.questions.slice(0, 2);
    return this.questions;
  });

  currentQuestion = computed(() => this.visibleQuestions()[this.currentStep()]);

  progressPercent = computed(() => {
    const total = this.totalSteps();
    return total > 0 ? Math.round(((this.currentStep() + 1) / total) * 100) : 0;
  });

  canProceed = computed(() => {
    const q = this.currentQuestion();
    if (!q) return false;
    return !!this.answers()[q.key];
  });

  today = new Date().toISOString().slice(0, 10);

  ngOnInit() {}

  setAnswer(key: string, value: string) {
    this.answers.update(prev => ({ ...prev, [key]: value }));
  }

  next() {
    if (this.currentStep() < this.totalSteps() - 1) {
      this.currentStep.update(s => s + 1);
    } else {
      this.checkEligibility();
    }
  }

  back() {
    if (this.currentStep() > 0) {
      this.currentStep.update(s => s - 1);
    }
  }

  private checkEligibility() {
    this.phase.set('checking');
    this.error.set('');

    const payload = this.answers();

    this.http.post<MreEligibilityResult>(
      `${environment.apiBaseUrl}/api/v1/eligibility/wizard-check`, payload
    ).subscribe({
      next: (res) => {
        this.result.set(res);
        this.phase.set('result');
      },
      error: (err) => {
        const outcome = this.computeLocalOutcome();
        this.result.set(outcome);
        this.phase.set('result');
      }
    });
  }

  private computeLocalOutcome(): MreEligibilityResult {
    const a = this.answers();

    if (a['complainedToRE'] === 'NO') {
      return {
        eligible: false,
        outcome: 'RE_FIRST',
        message: 'You must first file a complaint with your bank/financial institution and wait for their response (up to 30 days) before approaching RBI.',
        reWindowDays: 30,
        filingDeadlineDays: 90
      };
    }

    if (a['reRespondedSatisfactorily'] === 'RESOLVED') {
      return {
        eligible: false,
        outcome: 'RANT_GATE',
        message: 'Since the institution has already resolved your issue, RBI Ombudsman may not be able to take further action. If you believe the resolution is inadequate, you may still proceed.',
      };
    }

    if (a['reComplaintDate']) {
      const complaintDate = new Date(a['reComplaintDate']);
      const now = new Date();
      const daysSinceComplaint = Math.floor((now.getTime() - complaintDate.getTime()) / (1000 * 60 * 60 * 24));

      if (daysSinceComplaint < 30 && a['reRespondedSatisfactorily'] === 'NO_REPLY') {
        const windowOpenDate = new Date(complaintDate);
        windowOpenDate.setDate(windowOpenDate.getDate() + 30);
        return {
          eligible: false,
          outcome: 'TOO_EARLY',
          message: `Please wait until ${windowOpenDate.toLocaleDateString('en-IN')} (30 days from your complaint to the entity) before filing with RBI Ombudsman.`,
          daysRemaining: 30 - daysSinceComplaint,
          windowOpenDate: windowOpenDate.toISOString().slice(0, 10),
        };
      }

      if (daysSinceComplaint > 365) {
        return {
          eligible: false,
          outcome: 'TOO_LATE',
          message: 'Your complaint to the entity was filed over 1 year ago. The filing window under the Scheme has expired. You may still proceed but your complaint may be closed as time-barred.',
          filingDeadlineDate: new Date(complaintDate.getTime() + 365 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10),
        };
      }
    }

    const entityType = a['entityType'];
    if (entityType === 'UNKNOWN') {
      return {
        eligible: true,
        outcome: 'READY',
        message: 'Based on your answers, you appear eligible to file a complaint. We will help determine the correct entity type during the filing process.',
        compensationBand: 'Up to ₹20 lakh (consequential loss) + ₹1 lakh (mental agony)',
      };
    }

    return {
      eligible: true,
      outcome: 'READY',
      message: 'You are eligible to file a complaint with the RBI Ombudsman under the Integrated Ombudsman Scheme, 2021.',
      compensationBand: 'Up to ₹20 lakh (consequential loss) + ₹1 lakh (mental agony)',
    };
  }

  getOutcomeIcon(): string {
    const r = this.result();
    if (!r) return '';
    switch (r.outcome) {
      case 'READY': return 'pi pi-check-circle';
      case 'TOO_EARLY': return 'pi pi-clock';
      case 'TOO_LATE': return 'pi pi-exclamation-triangle';
      case 'RE_FIRST': return 'pi pi-arrow-right';
      case 'NOT_COVERED': return 'pi pi-ban';
      case 'RANT_GATE': return 'pi pi-info-circle';
      default: return 'pi pi-question-circle';
    }
  }

  getOutcomeClass(): string {
    const r = this.result();
    if (!r) return '';
    switch (r.outcome) {
      case 'READY': return 'outcome-ready';
      case 'TOO_EARLY': return 'outcome-early';
      case 'TOO_LATE': return 'outcome-late';
      case 'RE_FIRST': return 'outcome-re-first';
      case 'NOT_COVERED': return 'outcome-not-covered';
      case 'RANT_GATE': return 'outcome-rant';
      default: return '';
    }
  }

  getOutcomeTitle(): string {
    const r = this.result();
    if (!r) return '';
    switch (r.outcome) {
      case 'READY': return 'Yes, RBI can help!';
      case 'TOO_EARLY': return 'Not yet — please wait';
      case 'TOO_LATE': return 'Filing window may have expired';
      case 'RE_FIRST': return 'Approach your bank first';
      case 'NOT_COVERED': return 'Not covered under the Scheme';
      case 'RANT_GATE': return 'Issue already resolved';
      default: return 'Result';
    }
  }

  proceedToFile() {
    const a = this.answers();
    const queryParams: Record<string, string> = {
      portal: '2',
      entityType: a['entityType'] || '',
      complainedToRE: a['complainedToRE'] || '',
    };
    if (a['reComplaintDate']) queryParams['reComplaintDate'] = a['reComplaintDate'];
    if (a['reRespondedSatisfactorily']) queryParams['reResponse'] = a['reRespondedSatisfactorily'];

    this.router.navigate(['/public/file-complaint'], { queryParams });
  }

  startOver() {
    this.phase.set('questions');
    this.currentStep.set(0);
    this.answers.set({});
    this.result.set(null);
  }

  goHome() {
    this.router.navigate(['/public']);
  }
}
