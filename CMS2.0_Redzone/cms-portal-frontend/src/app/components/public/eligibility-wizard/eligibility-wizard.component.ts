import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';
import { scrollToTop } from '../../../utils/accessibility';

interface WizardQuestion {
  id: number;
  key: string;
  question: string;
  hint: string;
  type: 'radio' | 'date' | 'select';
  options?: { label: string; value: string }[];
  /** Translation key for simplified version of the question (e.g. 'wizard.q4_hint_simple') */
  simplifiedText?: string;
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

  phase = signal<'questions' | 'checking' | 'result' | 'unavailable'>('questions');
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
      simplifiedText: 'wizard.q1_entity_type_simple',
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
      simplifiedText: 'wizard.q2_complained_to_re_simple',
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
      simplifiedText: 'wizard.q4_re_response_simple',
      options: [
        { label: 'wizard.opt_no_reply', value: 'NO_REPLY' },
        { label: 'wizard.opt_dissatisfied', value: 'DISSATISFIED' },
        { label: 'wizard.opt_resolved', value: 'RESOLVED' },
      ]
    },
  ];

  /** Tracks which question IDs have their simplified text shown */
  simplifiedVisible = signal<Set<number>>(new Set());

  toggleSimplified(questionId: number) {
    this.simplifiedVisible.update(set => {
      const next = new Set(set);
      if (next.has(questionId)) {
        next.delete(questionId);
      } else {
        next.add(questionId);
      }
      return next;
    });
  }

  isSimplifiedVisible(questionId: number): boolean {
    return this.simplifiedVisible().has(questionId);
  }

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

  /**
   * The [max] of the RE-complaint-date input, as a LOCAL calendar date. toISOString() converts to UTC
   * first, so in IST (UTC+5:30) local midnight becomes 18:30 the previous day and the sliced string is
   * yesterday — the citizen could not enter today's date, and the server counts the window in local
   * dates, so the two disagreed on where the window starts.
   */
  today = (() => {
    const d = new Date();
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
  })();

  /**
   * UST11/6: the RE response window is cms.mre.re-window-days, read from
   * GET /api/v1/eligibility/questions. It is only quoted in the citizen-facing prose the SERVER
   * returns, but the wizard also needs it to render the "why you must wait" copy, so it is fetched
   * rather than compiled in. The initialiser is the pre-response placeholder, not the rule.
   */
  reWindowDays = signal(30);
  /** TranslatePipe params are strings. */
  reWindowDaysText = computed(() => String(this.reWindowDays()));

  ngOnInit() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/eligibility/questions`).subscribe({
      next: res => { if (res?.reWindowDays) this.reWindowDays.set(res.reWindowDays); },
      // Non-fatal: /wizard-check returns its own prose carrying the server's window, so a failure
      // here degrades a label, not the rule.
      error: () => { /* keep the placeholder */ },
    });
  }

  setAnswer(key: string, value: string) {
    this.answers.update(prev => ({ ...prev, [key]: value }));
  }

  next() {
    if (this.currentStep() < this.totalSteps() - 1) {
      this.currentStep.update(s => s + 1);
      scrollToTop();
    } else {
      this.checkEligibility();
    }
  }

  back() {
    if (this.currentStep() > 0) {
      this.currentStep.update(s => s - 1);
      scrollToTop();
    }
  }

  private checkEligibility() {
    this.phase.set('checking');
    this.error.set('');
    scrollToTop();

    const payload = this.answers();

    this.http.post<MreEligibilityResult>(
      `${environment.apiBaseUrl}/api/v1/eligibility/wizard-check`, payload
    ).subscribe({
      next: (res) => {
        this.result.set(res);
        this.phase.set('result');
        scrollToTop();
      },
      // UST11/6: this used to fall back to a computeLocalOutcome() that re-implemented the whole
      // eligibility determination with the window, the limitation period and every message baked
      // into this file. Two consequences, both worse than an error: raising
      // cms.mre.re-window-days left the fallback enforcing 30 days and quoting "30 days", and the
      // citizen was handed a verdict — including "you are eligible" — that no server ever issued,
      // with no indication the check had failed. A maintainability determination is a legal
      // statement about the Scheme, so when the authority for it is unreachable the wizard now says
      // so and offers a retry.
      error: () => {
        this.error.set('');
        this.phase.set('unavailable');
        scrollToTop();
      }
    });
  }

  /** Re-runs the server check after an 'unavailable' result. */
  retryCheck() {
    this.checkEligibility();
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
    scrollToTop();
  }

  goHome() {
    this.router.navigate(['/public']);
  }
}
