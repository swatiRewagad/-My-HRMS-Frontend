import { Component, Input, Output, EventEmitter, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface AutoClosureQuestion {
  id: number;
  questionNumber: number;
  questionText: string;
  helpText: string;
  clauseReference: string;
  defaultAnswer: string;
  outcomeOnYes: string;
  outcomeOnNo: string;
}

interface QuestionResponse {
  questionNumber: number;
  answer: string;
  outcome?: string;
  clauseReference?: string;
}

@Component({
  selector: 'app-auto-closure',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './auto-closure.component.html',
  styleUrl: './auto-closure.component.scss'
})
export class AutoClosureComponent implements OnInit {

  @Input() schemeVersion = 'RBIOS_2026';
  @Input() entityType = 'RBIO';
  @Input() existingResponses: QuestionResponse[] = [];
  @Input() readOnly = false;

  @Output() completed = new EventEmitter<{
    responses: QuestionResponse[];
    outcome: string;
    clauseReference: string;
    subJudice: boolean;
    generateComplaintNumber: boolean;
  }>();
  @Output() cancelled = new EventEmitter<void>();

  private http = inject(HttpClient);

  questions = signal<AutoClosureQuestion[]>([]);
  responses = signal<QuestionResponse[]>([]);
  currentQuestionIndex = signal(0);
  loading = signal(true);
  finalOutcome = signal<string | null>(null);
  showConfirmPopup = signal(false);
  pendingAnswer = '';

  currentQuestion = computed(() => {
    const qs = this.questions();
    const idx = this.currentQuestionIndex();
    return idx < qs.length ? qs[idx] : null;
  });

  progress = computed(() => {
    const total = this.questions().length;
    return total ? Math.round(((this.currentQuestionIndex() + 1) / total) * 100) : 0;
  });

  isNonDefaultAnswer = computed(() => {
    const q = this.currentQuestion();
    if (!q) return false;
    return this.pendingAnswer !== q.defaultAnswer;
  });

  ngOnInit() {
    this.loadQuestions();
  }

  private loadQuestions() {
    this.http.get<AutoClosureQuestion[]>(
      `${environment.apiBaseUrl}/api/v1/crpc/auto-closure/questions`,
      { params: { schemeVersion: this.schemeVersion, entityType: this.entityType } }
    ).subscribe({
      next: (qs) => {
        this.questions.set(qs);
        if (this.existingResponses.length > 0) {
          this.responses.set([...this.existingResponses]);
          this.currentQuestionIndex.set(this.existingResponses.length);
        }
        this.loading.set(false);
      },
      error: () => {
        this.questions.set(this.getDefaultQuestions());
        this.loading.set(false);
      }
    });
  }

  selectAnswer(answer: string) {
    const q = this.currentQuestion();
    if (!q) return;

    this.pendingAnswer = answer;
    if (answer !== q.defaultAnswer) {
      this.showConfirmPopup.set(true);
    } else {
      this.confirmAnswer();
    }
  }

  confirmAnswer() {
    this.showConfirmPopup.set(false);
    const q = this.currentQuestion();
    if (!q) return;

    const answer = this.pendingAnswer;
    const outcome = answer === 'YES' ? q.outcomeOnYes : q.outcomeOnNo;

    const resp: QuestionResponse = {
      questionNumber: q.questionNumber,
      answer,
      outcome: outcome || 'NEXT',
      clauseReference: q.clauseReference
    };

    this.responses.update(r => [...r, resp]);

    if (outcome && outcome !== 'NEXT') {
      this.finalOutcome.set(outcome);
      this.emitResult(outcome, q.clauseReference);
    } else {
      const nextIdx = this.currentQuestionIndex() + 1;
      if (nextIdx >= this.questions().length) {
        this.finalOutcome.set('NEW_COMPLAINT');
        this.emitResult('NEW_COMPLAINT', '');
      } else {
        this.currentQuestionIndex.set(nextIdx);
      }
    }
    this.pendingAnswer = '';
  }

  cancelPopup() {
    this.showConfirmPopup.set(false);
    this.pendingAnswer = '';
  }

  selectAllDefaults() {
    const qs = this.questions();
    const allResponses: QuestionResponse[] = qs.map(q => ({
      questionNumber: q.questionNumber,
      answer: q.defaultAnswer || 'YES',
      outcome: 'NEXT',
      clauseReference: q.clauseReference
    }));
    this.responses.set(allResponses);
    this.currentQuestionIndex.set(qs.length);
    this.finalOutcome.set('NEW_COMPLAINT');
    this.emitResult('NEW_COMPLAINT', '');
  }

  private emitResult(outcome: string, clauseReference: string) {
    const subJudice = outcome === 'SUB_JUDICE';
    const finalOutcome = subJudice ? 'NEW_COMPLAINT' : outcome;
    this.completed.emit({
      responses: this.responses(),
      outcome: finalOutcome,
      clauseReference,
      subJudice,
      generateComplaintNumber: finalOutcome !== 'CRPC_REJECTION' && finalOutcome !== 'NOT_A_COMPLAINT'
    });
  }

  private getDefaultQuestions(): AutoClosureQuestion[] {
    if (this.schemeVersion === 'RBIOS_2026' && this.entityType === 'CEPC') {
      return [
        { id: 1, questionNumber: 1, questionText: 'Is the entity regulated by RBI?', helpText: 'Check if the entity falls under RBI regulation.', clauseReference: 'Clause 10(1)(a)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
        { id: 2, questionNumber: 2, questionText: 'Has the complainant NOT obtained First Resolution from the entity (FRC)?', helpText: 'FRC must be attempted before approaching Ombudsman.', clauseReference: 'Clause FRC', defaultAnswer: 'NO', outcomeOnYes: 'CRPC_REJECTION', outcomeOnNo: 'NEXT' },
        { id: 3, questionNumber: 3, questionText: 'Is the complaint NOT already dealt with?', helpText: '', clauseReference: 'Clause 10(1)(d)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
        { id: 4, questionNumber: 4, questionText: 'Is the complaint NOT related to employer-employee dispute?', helpText: '', clauseReference: 'Clause 10(1)(e)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
        { id: 5, questionNumber: 5, questionText: 'Is the complaint directly addressed to RBI?', helpText: '', clauseReference: 'Clause Direct', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      ];
    }
    // Default: RBIO 16-Q (abbreviated to core)
    return [
      { id: 1, questionNumber: 1, questionText: 'Is the entity regulated by RBI?', helpText: 'Check if the entity falls under RBI regulation per schedule.', clauseReference: 'Clause 10(1)(a)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 2, questionNumber: 2, questionText: 'Has the complainant approached the entity first?', helpText: 'First complaint must have been made to the entity.', clauseReference: 'Clause 10(1)(b)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 3, questionNumber: 3, questionText: 'Was the complaint filed within 1 year?', helpText: 'From date of rejection or 30 days from no reply.', clauseReference: 'Clause 10(1)(c)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 4, questionNumber: 4, questionText: 'Is the complaint NOT already dealt with?', helpText: '', clauseReference: 'Clause 10(1)(d)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 5, questionNumber: 5, questionText: 'Is the complaint NOT employer-employee dispute?', helpText: '', clauseReference: 'Clause 10(1)(e)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 6, questionNumber: 6, questionText: 'Is the complaint NOT sub-judice?', helpText: '', clauseReference: 'Clause 10(1)(f)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 7, questionNumber: 7, questionText: 'Does it relate to schedule grounds?', helpText: '', clauseReference: 'Clause 10(1)(g)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 8, questionNumber: 8, questionText: 'Is the amount within ₹2 Crore?', helpText: '', clauseReference: 'Clause 10(1)(h)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 9, questionNumber: 9, questionText: 'Not previously filed for same subject?', helpText: '', clauseReference: 'Clause 10(2)(a)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 10, questionNumber: 10, questionText: 'Not filed appeal for same subject?', helpText: '', clauseReference: 'Clause 10(2)(b)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 11, questionNumber: 11, questionText: 'Not frivolous or vexatious?', helpText: '', clauseReference: 'Clause 10(2)(c)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 12, questionNumber: 12, questionText: 'Not beyond scope of scheme?', helpText: '', clauseReference: 'Clause 10(2)(d)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 13, questionNumber: 13, questionText: 'Sufficient basis for complaint?', helpText: '', clauseReference: 'Clause 10(2)(e)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 14, questionNumber: 14, questionText: 'Relief within Ombudsman competence?', helpText: '', clauseReference: 'Clause 10(2)(f)', defaultAnswer: 'YES', outcomeOnYes: 'NEXT', outcomeOnNo: 'CRPC_REJECTION' },
      { id: 15, questionNumber: 15, questionText: 'Is the matter currently sub-judice?', helpText: 'If YES, complaint proceeds with Sub-Judice flag.', clauseReference: 'Clause 10(1)(f) Sub-Judice', defaultAnswer: 'NO', outcomeOnYes: 'SUB_JUDICE', outcomeOnNo: 'NEXT' },
      { id: 16, questionNumber: 16, questionText: 'Has complainant filed any case in court/forum?', helpText: '', clauseReference: 'Clause 10(1)(f)', defaultAnswer: 'NO', outcomeOnYes: 'SUB_JUDICE', outcomeOnNo: 'NEXT' },
    ];
  }
}
