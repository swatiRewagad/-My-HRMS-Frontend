import { Component, OnInit, OnDestroy, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CrpcService } from '../../../services/crpc.service';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { ReviewerUser } from '../../../models/crpc.model';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { AutoClosureComponent } from '../auto-closure/auto-closure.component';
import { highlightEmailText, escapeHtml } from '../../../utils/highlight-text.util';
import { UploadLimitsService } from '../../../services/upload-limits.service';
import { ComplaintSummaryComponent } from '../../shared/complaint-summary/complaint-summary.component';
import { ComplaintSummaryItem } from '../../shared/complaint-summary/complaint-summary.types';
import { SimilarCasesComponent } from '../../shared/similar-cases/similar-cases.component';
import { AssistanceRailComponent } from '../../shared/assistance-rail/assistance-rail.component';
import { AssistanceBulbComponent } from '../../shared/assistance-rail/assistance-bulb.component';
import { AssistanceRailService } from '../../../services/assistance-rail.service';

interface EligibilityQuestion {
  id: string;
  question: string;
  answer: 'YES' | 'NO' | null;
  /**
   * The answer that makes the complaint non-maintainable on its own. Omitted for questions
   * that only feed a further rule — a "no reply from the Entity" is not fatal by itself,
   * it is fatal only while the Entity's 30-day window has not elapsed.
   */
  closeOn?: 'YES' | 'NO';
  /** Plain-language reason shown to the DEO when this answer closes the complaint. */
  closureReason: string;
  /** Which department's question set this belongs to; ANY shows for both. */
  scope: 'RBIO' | 'CEPC' | 'ANY';
  /** Shown only when the parent question carries this answer. */
  dependsOn?: { id: string; answer: 'YES' | 'NO' };
  closureClause: string;
}

interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
  url?: string;
}

interface EmailCorrespondence {
  id: string;
  direction: 'SENT' | 'RECEIVED';
  subject: string;
  to: string;
  sentAt: string;
  body: string;
}

@Component({
  selector: 'app-draft-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, AutoClosureComponent, ComplaintSummaryComponent, SimilarCasesComponent, AssistanceRailComponent, AssistanceBulbComponent],
  templateUrl: './draft-assessment.component.html',
  styleUrl: './draft-assessment.component.scss'
})
export class DraftAssessmentComponent implements OnInit, OnDestroy {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private crpcService = inject(CrpcService);
  private auth = inject(KeycloakAuthService);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private uploadLimits = inject(UploadLimitsService);
  private assistanceRail = inject(AssistanceRailService);

  // ─── View Mode ───
  editMode = signal(false);
  activeStep = signal<'creation' | 'assignment'>('creation');
  showAiWarning = true;

  // Auto-Closure (new question engine)
  showAutoClosureEngine = signal(false);
  autoClosureCompleted = signal(false);
  autoClosureOutcome = signal('');
  autoClosureSubJudice = signal(false);
  autoClosureClauseRef = signal('');
  autoClosureResponses = signal<any[]>([]);
  schemeVersion = 'RBIOS_2026';

  // ─── PDF Preview (Physical Letter) ───
  pdfPreviewUrl = signal<SafeResourceUrl | null>(null);
  pdfExpanded = signal(false);

  // ─── Suggestions (AI-extracted) ───
  suggestions = signal<{ field: string; value: string; applied: boolean }[]>([]);
  showSuggestionsPanel = signal(false);
  selectedSuggestionIdx = signal<number>(0);

  // ─── Confirmation Dialog ───
  showConfirmDialog = signal(false);
  confirmAssignmentMode = 'Automatic';
  selectedReviewerName = 'CRPC Reviewer';

  // ─── Blob URL cache: maps attachment id → safe blob URL (avoids X-Frame-Options block) ───
  private blobUrlCache = new Map<string, string>();
  attachmentBlobUrls = signal<Record<string, SafeResourceUrl>>({});
  /** Attachments whose blob fetch failed, so the tab reports it instead of rendering an untrusted URL. */
  attachmentLoadFailed = signal<Set<string>>(new Set());

  // ─── Attachments Side Panel ───
  showAttachmentsPanel = signal(false);
  viewingAttachment = signal<Attachment | null>(null);
  pdfCurrentPage = signal(1);
  pdfTotalPages = signal(5);

  // ─── Email Communication Tab ───
  emailSections = { open: true, drafts: true, closed: false };
  selectedEmail = signal<EmailCorrespondence | null>(null);

  // ─── History Panel ───
  showHistoryPanel = signal(false);
  showSimilarPanel = signal(false);
  showAssistancePanel = signal(false);
  historyEntries = signal<any[]>([]);
  loadingHistory = signal(false);

  // ─── Assignment Mode ───
  assignmentMode = 'Manual';

  // ─── Panel Expand/Collapse ───
  expandedPanel = signal<'email' | 'complaint' | ''>('');

  toggleExpand(panel: 'email' | 'complaint') {
    this.expandedPanel.set(this.expandedPanel() === panel ? '' : panel);
  }

  // ─── Email Highlight on Field Focus ───
  focusedFieldValue = signal<string>('');
  emailBodyHtml = signal<string>('');
  emailSubjectHtml = signal<string>('');
  emailSenderHtml = signal<string>('');

  isEmailMode(): boolean {
    return (this.modeOfReceipt || '').toUpperCase() === 'EMAIL';
  }

  receiptDateDisplay(): string {
    if (!this.receivedDate) return '—';
    const [y, m, d] = this.receivedDate.split('-');
    return y && m && d ? `${d}-${m}-${y}` : this.receivedDate;
  }

  modeOfReceiptLabel(): string {
    switch (this.modeOfReceipt) {
      case 'PHYSICAL_LETTER': return 'Physical Letter';
      case 'PORTAL': return 'Portal';
      case 'CPGRAMS': return 'CPGRAMS';
      default: return 'Email';
    }
  }

  onIsCpgramChange(checked: boolean) {
    this.isCpgram = checked;
    if (!checked) this.cpgramsReference = '';
  }

  onCpgramsNumberInput(raw: string) {
    this.cpgramsReference = (raw || '').replace(/\D/g, '').slice(0, 16);
  }

  get cpgramsNumberError(): string | null {
    if (!this.isCpgram || !this.cpgramsReference) return null;
    return this.cpgramsReference.length === 16 ? null : 'CPGRAMS number must be exactly 16 digits.';
  }

  onFieldFocus(fieldValue: string | undefined | null) {
    if (!this.isEmailMode()) return;
    this.focusedFieldValue.set(fieldValue?.trim() || '');
    this.updateEmailHighlight();
  }

  onFieldBlur() {
    this.focusedFieldValue.set('');
    this.updateEmailHighlight();
  }

  onFieldClick(fieldValue: string | undefined | null) {
    if (!this.isEmailMode()) return;
    this.focusedFieldValue.set(fieldValue?.trim() || '');
    this.updateEmailHighlight();
  }

  updateEmailHighlight() {
    const fieldVal = this.focusedFieldValue();

    // Subject highlight
    const subj = this.subject || '';
    if (!fieldVal) {
      this.emailSubjectHtml.set(escapeHtml(subj));
    } else {
      this.emailSubjectHtml.set(highlightEmailText(subj, fieldVal));
    }

    // Sender info highlight
    const senderText = `${this.complainantName || 'Unknown'} <${this.complainantEmail || ''}>`;
    if (!fieldVal) {
      this.emailSenderHtml.set(escapeHtml(senderText));
    } else {
      this.emailSenderHtml.set(highlightEmailText(senderText, fieldVal));
    }

    // Body highlight
    if (!this.description) {
      this.emailBodyHtml.set('');
      return;
    }
    if (!fieldVal) {
      this.emailBodyHtml.set(escapeHtml(this.description));
      return;
    }
    this.emailBodyHtml.set(highlightEmailText(this.description, fieldVal));
  }

  // ─── Collapsible Sections ───
  sectionOpen = {
    complaint: true,
    eligibility: false,
    entity: true,
    complainant: false,
    classification: false,
    financial: false,
    legal: false,
    flags: false,
    linkage: false,
    declaration: false,
  } as any;

  draftId = '';
  draftStatus = 'DRAFT';

  /**
   * The real complaint NUMBER once this draft has been converted, else blank.
   *
   * Taken straight from the draft payload's `convertedComplaintId`, which the server sets from
   * `Complaint.getComplaintNumber()`. Only the assistance rail reads it; nothing else on this screen
   * needs it, and it is deliberately not merged into `cpgramsReference`, which is a different number.
   */
  convertedComplaintNumber = '';

  /**
   * The strip's facts, for the shared app-complaint-summary.
   *
   * A getter, not a {@code computed()}: the fields it reads are plain class properties, and a computed
   * over a non-signal registers no dependency, so the strip would render once and never update.
   *
   * Status now goes through the shared badge. The old markup tested for SENT_TO_REVIEWER and printed
   * "Draft" for everything else, so an approved or routed draft still called itself a draft.
   */
  get summaryItems(): ComplaintSummaryItem[] {
    return [
      { labelKey: 'ui.col.complaint_id', value: this.draftId, icon: 'pi-id-card' },
      { labelKey: 'ui.col.complaint_number', value: this.cpgramsReference || 'Not Generated', icon: 'pi-file' },
      {
        labelKey: 'ui.col.mode_of_receipt',
        value: this.modeOfReceipt === 'PHYSICAL_LETTER' ? 'Physical Letter' : 'Email',
        icon: 'pi-envelope'
      },
      { labelKey: 'ui.col.status', value: this.draftStatus, kind: 'status', icon: 'pi-flag' },
      {
        labelKey: 'ui.col.assigned_officer',
        value: this.loggedInUser?.name || 'DEO',
        icon: 'pi-user',
        tone: 'owner'
      }
    ];
  }

  deoAssessmentRemarks = '';
  deoAssessmentDecision = '';
  loading = signal(true);

  currentTab = signal<'details' | 'attachments' | 'assessment' | 'screening' | 'route'>('details');

  // ─── Editable Draft Fields (DEO can modify) ───
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantAddress = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';
  contactPreference: 'EMAIL' | 'PHONE' | 'POST' = 'EMAIL';

  modeOfReceipt: 'EMAIL' | 'PHYSICAL_LETTER' | 'PORTAL' | 'CPGRAMS' = 'EMAIL';
  cpgramsReference = '';
  isCpgram = false;
  category = '';
  subCategory = '';
  entityName = '';
  entityType = 'BANK';
  entityState = '';
  entityCategory = '';
  entityBsrCode = '';
  entityPincode = '';
  entityCountry = '';
  entityDistrict = '';
  entityCity = '';
  entityBranchName = '';
  entityBranchCategory = '';
  entityAddress = '';
  branchCenterName = '';
  cosmosCode = '';
  assetSizeInCrores: number | null = null;
  depositTakingEntity = false;
  assetSizeGreater100Crores = false;
  liquidatedPresent = false;
  subject = '';
  description = '';
  amountInvolved: number | null = null;
  transactionDate = '';
  letterDate = '';
  receivedDate = '';
  vernacular = false;
  vernacularLanguage = '';
  vernacularLanguages = ['Hindi', 'Marathi', 'Tamil', 'Telugu', 'Kannada', 'Bengali', 'Gujarati', 'Malayalam', 'Punjabi', 'Odia', 'Urdu', 'Assamese', 'Konkani', 'Sanskrit'];
  emailType: 'TO' | 'CC_BCC' | '' = '';
  systemSuggestion: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | 'PENDING' = 'PENDING';

  otherEntityName = '';
  registrationWithRbiDate = '';

  // ─── Complaint Classification ───
  complaintCategory = '';
  complaintSubCategory1 = '';
  complaintSubCategory2 = '';
  filingDate = '';

  // ─── Reminder & Financial Details ───
  reminderSent = true;
  disputedAmount: number | null = null;
  compensationSought = '';
  compensationSoughtYesNo = true;

  // ─── Legal & Case Details ───
  legalCaseFiled = false;
  preEnquiryReceived = false;
  highPriority = false;
  loanDisposalAmount: number | null = null;

  // ─── Flags & Indicators ───
  pensionComplaint = false;
  businessCorrespondent = false;
  atmCreditDebitCard = false;
  atmCardNumber = '';
  schemeFlag = '';
  rboCgpcOld = '';
  groundsFlag = '';

  // ─── Complaint Linkage ───
  freeMarkedComplaint = false;
  complaintReferenceNumber = '';
  currentComplaintNumber = '';
  replyWithin30Days: 'YES' | 'NO' | 'NA' = 'NA';

  // ─── Additional Info ───
  crpcProposedAction = '';
  additionalComments = '';

  // ─── Declaration ───
  declarationChecked = false;

  // ─── Attachments ───
  attachments = signal<Attachment[]>([]);
  uploadError = '';

  // ─── Email Correspondence (Request Additional Info) ───
  emailCorrespondence = signal<EmailCorrespondence[]>([]);
  showEmailComposer = signal(false);
  emailTo = '';
  emailSubject = '';
  emailBody = '';
  sendingEmail = signal(false);
  attachFormPdf = false;
  formPdfFields = [
    { key: 'name', label: 'Full Name', include: true },
    { key: 'phone', label: 'Phone Number', include: true },
    { key: 'email', label: 'Email Address', include: true },
    { key: 'address', label: 'Complete Address', include: true },
    { key: 'state', label: 'State', include: false },
    { key: 'district', label: 'District', include: false },
    { key: 'pincode', label: 'Pincode', include: false },
    { key: 'accountNumber', label: 'Account Number', include: false },
    { key: 'transactionId', label: 'Transaction ID / Reference', include: true },
    { key: 'transactionDate', label: 'Transaction Date', include: true },
    { key: 'amount', label: 'Amount Involved (₹)', include: true },
    { key: 'branchName', label: 'Branch Name', include: false },
    { key: 'description', label: 'Detailed Description', include: true },
    { key: 'supportingDocs', label: 'List of Supporting Documents', include: false },
  ];

  // ─── Eligibility Check (RBIO / CEPC question sets) ───
  eligibilityQuestions = signal<EligibilityQuestion[]>([
    { id: 'EQ1', scope: 'RBIO', closeOn: 'YES', closureClause: 'PENDING_BEFORE_FORUM', closureReason: 'the same grievance is already pending before a Court, Tribunal, Arbitrator or other judicial / quasi-judicial forum', answer: null,
      question: 'Is the same grievance already pending before any Court, Tribunal, Arbitrator or any other judicial / quasi-judicial forum? (excluding criminal proceedings / police investigation)' },
    { id: 'EQ2', scope: 'RBIO', closeOn: 'YES', closureClause: 'SETTLED_BEFORE_FORUM', closureReason: 'the same grievance has already been settled or dealt with by a Court, Tribunal, Arbitrator or other judicial / quasi-judicial forum', answer: null,
      question: 'Is the same grievance already settled or dealt with before any Court, Tribunal, Arbitrator or any other judicial / quasi-judicial forum? (excluding criminal proceedings / police investigation)' },
    { id: 'EQ3', scope: 'RBIO', closeOn: 'YES', closureClause: 'THROUGH_ADVOCATE', closureReason: 'the complaint is made through an advocate who is not the Complainant', answer: null,
      question: 'Is the complaint being made through an advocate?' },
    { id: 'EQ4', scope: 'RBIO', closeOn: 'NO', closureClause: 'NOT_THE_COMPLAINANT', closureReason: 'the complaint is made through an advocate who is not the Complainant', answer: null,
      dependsOn: { id: 'EQ3', answer: 'YES' },
      question: 'If yes, then is it the Complainant?' },
    { id: 'EQ5', scope: 'RBIO', closeOn: 'YES', closureClause: 'PENDING_BEFORE_OMBUDSMAN', closureReason: 'the same grievance is already pending before the Ombudsman', answer: null,
      question: 'Is the same grievance already pending before the Ombudsman?' },
    { id: 'EQ6', scope: 'RBIO', closeOn: 'YES', closureClause: 'SETTLED_BY_OMBUDSMAN', closureReason: 'the same grievance has already been settled or dealt with on merits by the Ombudsman', answer: null,
      question: 'Is the same grievance already settled or dealt with on merits by the Ombudsman?' },
    { id: 'EQ7', scope: 'RBIO', closeOn: 'YES', closureClause: 'STAFF_EMPLOYER_MATTER', closureReason: 'the Complainant is staff of the Regulated Entity and the matter concerns the employer-employee relationship', answer: null,
      question: 'Is the Complainant a staff of the Regulated Entity and the complaint involves an employer-employee relationship?' },
    { id: 'EQ8', scope: 'CEPC', closeOn: 'YES', closureClause: 'ALREADY_FILED_WITH_CEPC', closureReason: 'the Complainant has already filed a complaint on the same matter with CEPC or RBI', answer: null,
      question: 'Has the Complainant previously filed a complaint on the same matter with CEPC or RBI?' },
    { id: 'EQ9', scope: 'CEPC', closeOn: 'YES', closureClause: 'EMPLOYEE_OF_RE', closureReason: 'the Complainant is an employee of the Regulated Entity complained against', answer: null,
      question: 'Is the Complainant an employee of the Regulated Entity against whom this complaint is filed?' },
    { id: 'EQ10', scope: 'CEPC', closeOn: 'YES', closureClause: 'EMPLOYEE_EMPLOYER_MATTER', closureReason: 'the matter concerns the employee-employer relationship of the Regulated Entity', answer: null,
      dependsOn: { id: 'EQ9', answer: 'YES' },
      question: 'If yes, does the complaint involve the employee-employer relationship of the Regulated Entity?' },
    { id: 'EQ11', scope: 'ANY', closeOn: 'NO', closureClause: 'NOT_APPROACHED_RE', closureReason: 'the Complainant has not first filed a complaint with the Regulated Entity', answer: null,
      question: 'Has the Complainant filed a written / electronic complaint with the Regulated Entity?' },
    // No closeOn: a missing reply is fatal only while the Entity's 30-day window is still
    // running, which registrationDateValid() decides from the filing date.
    { id: 'EQ13', scope: 'ANY', closureClause: 'NO_RE_REPLY', closureReason: 'the Regulated Entity has not replied and its 30-day window has not elapsed', answer: null,
      dependsOn: { id: 'EQ11', answer: 'YES' },
      question: 'Did the Complainant receive any reply from the Entity?' },
  ]);

  /** Date the complaint was first filed with the RE (EQ12 in the spec). */
  reFiledDate = '';
  /** Date of the RE's reply (EQ14 in the spec). */
  reReplyDate = '';

  /** Department whose question set applies, derived from the selected entity. */
  eligibilityDepartment = signal<'RBIO' | 'CEPC'>('RBIO');

  /** Questions visible for the current department, with unmet dependencies filtered out. */
  visibleEligibilityQuestions = computed(() => {
    const dept = this.eligibilityDepartment();
    const all = this.eligibilityQuestions();
    return all.filter(q => {
      if (q.scope !== 'ANY' && q.scope !== dept) return false;
      if (!q.dependsOn) return true;
      const parent = all.find(p => p.id === q.dependsOn!.id);
      return !!parent && parent.answer === q.dependsOn.answer;
    });
  });

  /** True once every visible question has an answer and both required dates are filled. */
  eligibilityComplete(): boolean {
    const answered = this.visibleEligibilityQuestions().every(q => q.answer !== null);
    if (!answered) return false;
    if (this.showReFiledDate() && !this.reFiledDate) return false;
    if (this.showReReplyDate() && !this.reReplyDate) return false;
    return true;
  }

  /** The first question whose answer makes the complaint non-maintainable on its own. */
  eligibilityFailure = computed(() => {
    return this.visibleEligibilityQuestions()
      .find(q => !!q.closeOn && q.answer !== null && q.answer === q.closeOn) || null;
  });

  private answerOf(id: string): 'YES' | 'NO' | null {
    return this.eligibilityQuestions().find(q => q.id === id)?.answer ?? null;
  }

  showReFiledDate(): boolean {
    return this.answerOf('EQ11') === 'YES';
  }

  showReReplyDate(): boolean {
    return this.answerOf('EQ13') === 'YES';
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10);
  }

  get maxEligibilityDate(): string {
    return this.todayIso();
  }

  /**
   * Scheme clause 10(1)(a): the RE gets 30 days to respond before the complaint is
   * admissible, so a complaint filed with the RE less than 30 days ago is premature
   * unless the RE has already replied.
   */
  // Not computed(): reFiledDate / reReplyDate are plain ngModel properties, not signals, so a
  // computed() would cache its first value and never see a date change.
  reWindowDays(): number | null {
    if (!this.reFiledDate) return null;
    const filed = new Date(this.reFiledDate + 'T00:00:00').getTime();
    if (Number.isNaN(filed)) return null;
    const today = new Date(this.todayIso() + 'T00:00:00').getTime();
    return Math.floor((today - filed) / 86400000);
  }

  registrationDateValid(): boolean {
    const days = this.reWindowDays();
    if (days === null) return false;
    if (days < 0) return false;
    if (days >= 30) return true;
    return this.answerOf('EQ13') === 'YES';
  }

  get reFiledDateError(): string | null {
    if (!this.reFiledDate) return null;
    if (this.reFiledDate > this.todayIso()) return 'A future date is not allowed.';
    return null;
  }

  get reReplyDateError(): string | null {
    if (!this.reReplyDate) return null;
    if (this.reReplyDate > this.todayIso()) return 'A future date is not allowed.';
    if (this.reFiledDate && this.reReplyDate < this.reFiledDate) {
      return 'The reply date cannot be before the date the complaint was filed with the Entity.';
    }
    return null;
  }

  /** Reason the complaint would be auto-closed, or null when it is eligible so far. */
  eligibilityAutoCloseReason(): string | null {
    const failed = this.eligibilityFailure();
    if (failed) return failed.closureClause;
    if (this.reFiledDate && !this.reFiledDateError && !this.registrationDateValid()) return 'NO_RE_REPLY';
    return null;
  }

  eligibilityAutoCloseMessage(): string {
    const failed = this.eligibilityFailure();
    if (failed) {
      return `The complaint will be closed as non-maintainable because ${failed.closureReason}.`;
    }
    if (this.eligibilityAutoCloseReason() === 'NO_RE_REPLY') {
      return 'The Entity has had fewer than 30 days to respond and has not replied, so the complaint is premature and will be closed on submission.';
    }
    return '';
  }

  /** True when every visible question already carries its maintainable answer. */
  allMarkedEligible = computed(() =>
    this.visibleEligibilityQuestions().every(q => q.answer !== null && q.answer !== q.closeOn)
  );

  nonMaintainableReasonLabel(): string {
    if (!this.nonMaintainableReason) return '—';
    return this.nonMaintainableReasons.find(r => r.value === this.nonMaintainableReason)?.label
      || this.nonMaintainableReason;
  }

  displayDate(iso: string): string {
    if (!iso) return '—';
    const [y, m, d] = iso.split('-');
    return y && m && d ? `${d}-${m}-${y}` : iso;
  }

  onMarkAllEligibleChange(checked: boolean) {
    if (checked) this.markAllAsEligible();
    else this.clearEligibilityAnswers();
  }

  /**
   * The eligibility answers ride on the existing auto-closure columns rather than new ones:
   * `closureClause` already drives closure downstream, and `autoClosureResponsesJson` is the
   * only place the individual answers can be replayed when the screen is reopened.
   */
  private eligibilityPayload() {
    const answered = this.eligibilityQuestions().filter(q => q.answer !== null);
    return {
      closureClause: this.eligibilityAutoCloseReason() || '',
      autoClosureResponsesJson: JSON.stringify({
        department: this.eligibilityDepartment(),
        reFiledDate: this.reFiledDate,
        reReplyDate: this.reReplyDate,
        answers: answered.map(q => ({ id: q.id, answer: q.answer })),
      }),
    };
  }

  /** Restores eligibility answers saved by {@link eligibilityPayload}. */
  private hydrateEligibility(json: string | null | undefined) {
    if (!json) return;
    let saved: any;
    try {
      saved = JSON.parse(json);
    } catch {
      return;
    }
    if (!Array.isArray(saved?.answers)) return;
    if (saved.department === 'CEPC' || saved.department === 'RBIO') {
      this.eligibilityDepartment.set(saved.department);
    }
    this.reFiledDate = saved.reFiledDate || '';
    this.reReplyDate = saved.reReplyDate || '';
    const byId = new Map<string, 'YES' | 'NO'>(
      saved.answers
        .filter((a: any) => a?.answer === 'YES' || a?.answer === 'NO')
        .map((a: any) => [a.id, a.answer])
    );
    this.eligibilityQuestions.update(list => list.map(q => ({ ...q, answer: byId.get(q.id) ?? null })));
  }

  // ─── Auto-Closure Screening (Sequential) ───
  screeningQuestions = signal([
    { id: 'SQ1', question: 'Is the complaint time-barred (filed beyond 1 year from cause of action)?', answer: null as ('YES' | 'NO' | null), closureClause: 'TIME_BARRED' },
    { id: 'SQ2', question: 'Is the matter currently sub-judice before a court/tribunal/forum?', answer: null as ('YES' | 'NO' | null), closureClause: 'SUB_JUDICE' },
    { id: 'SQ3', question: 'Is the complaint regarding a matter already settled by agreement?', answer: null as ('YES' | 'NO' | null), closureClause: 'ALREADY_SETTLED' },
    { id: 'SQ4', question: 'Has the complainant not approached the RE before filing with the Ombudsman?', answer: null as ('YES' | 'NO' | null), closureClause: 'NOT_APPROACHED_RE' },
    { id: 'SQ5', question: 'Is the complaint anonymous (no identifiable complainant)?', answer: null as ('YES' | 'NO' | null), closureClause: 'ANONYMOUS' },
    { id: 'SQ6', question: 'Does the complaint lack specifics required for investigation?', answer: null as ('YES' | 'NO' | null), closureClause: 'INSUFFICIENT_DETAILS' },
    { id: 'SQ7', question: 'Is the subject matter outside the jurisdiction of RBI Ombudsman?', answer: null as ('YES' | 'NO' | null), closureClause: 'OUT_OF_JURISDICTION' },
    { id: 'SQ8', question: 'Is the complaint frivolous or vexatious in nature?', answer: null as ('YES' | 'NO' | null), closureClause: 'FRIVOLOUS' },
  ]);

  currentScreeningIndex = signal(0);

  autoClosureTriggered = computed(() => {
    return this.screeningQuestions().some(q => q.answer === 'YES');
  });

  autoClosureClause = computed(() => {
    const triggered = this.screeningQuestions().find(q => q.answer === 'YES');
    return triggered?.closureClause || '';
  });

  allScreeningComplete = computed(() => {
    return this.screeningQuestions().every(q => q.answer !== null);
  });

  // ─── Decision & Routing ───
  deoDecision: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | '' = '';
  nonMaintainableReason = '';
  notAComplaintOthersReason = '';
  suggestionDepartment = '';
  suggestionNature = '';
  closureTag = '';
  selectedReviewer = '';
  assignmentType = 'MANUAL';
  deoRemarks = '';
  savedTemplateId = '';

  nonMaintainableReasons = [
    { value: 'APPEAL', label: 'Appeal' },
    { value: 'BROADCAST_MESSAGE', label: 'Broadcast Message' },
    { value: 'PASSWORD_CHANGE', label: 'Password Change Request' },
    { value: 'SUGGESTION', label: 'Suggestion' },
    { value: 'OTHERS', label: 'Others' },
    { value: 'TIME_BARRED', label: 'Time Barred (beyond 1 year)' },
    { value: 'SUB_JUDICE', label: 'Matter is Sub-Judice' },
    { value: 'ALREADY_SETTLED', label: 'Already Settled by Agreement' },
    { value: 'NOT_APPROACHED_RE', label: 'Complainant has not approached RE' },
    { value: 'ANONYMOUS', label: 'Anonymous Complaint' },
    { value: 'INSUFFICIENT_DETAILS', label: 'Insufficient Details' },
    { value: 'OUT_OF_JURISDICTION', label: 'Outside Jurisdiction' },
    { value: 'FRIVOLOUS', label: 'Frivolous/Vexatious' },
    { value: 'DUPLICATE', label: 'Duplicate Complaint' },
  ];

  closureTags = [
    { value: 'NM_CLAUSE_1', label: 'NM Clause 1 - Time Barred' },
    { value: 'NM_CLAUSE_2', label: 'NM Clause 2 - Sub Judice' },
    { value: 'NM_CLAUSE_3', label: 'NM Clause 3 - Outside Jurisdiction' },
    { value: 'NM_CLAUSE_4', label: 'NM Clause 4 - No Pecuniary Loss' },
    { value: 'NM_CLAUSE_5', label: 'NM Clause 5 - HR/Service Matter' },
    { value: 'VERNACULAR', label: 'Vernacular - Translation Required' },
  ];

  commentTemplates = [
    { id: 'T1', label: 'Standard Maintainable', text: 'Complaint meets all criteria for processing under the RBIOS scheme. All mandatory fields verified. Forwarding to Reviewer for action.' },
    { id: 'T2', label: 'Non-Maintainable - Time Barred', text: 'Complaint is time-barred (filed beyond 1 year from cause of action). Auto-closure screening confirmed. Recommended for closure under NM Clause 1.' },
    { id: 'T3', label: 'Non-Maintainable - Sub Judice', text: 'Matter is currently before a court/tribunal. Cannot be processed under RBIOS. Closure under NM Clause 2.' },
    { id: 'T4', label: 'Non-Maintainable - Outside Jurisdiction', text: 'Subject matter falls outside the jurisdiction of RBI Ombudsman. Closure under NM Clause 3.' },
    { id: 'T5', label: 'Incomplete - Awaiting Info', text: 'Complaint lacks sufficient details. Additional information requested from complainant via email. Pending response.' },
    { id: 'T6', label: 'CPGRAMS Referral', text: 'Complaint received via CPGRAMS. Reference verified. Processing as per CPGRAMS SLA guidelines.' },
    { id: 'T7', label: 'Vernacular - Translation Pending', text: 'Complaint received in regional language. Flagged for translation. Assessment pending translated content.' },
  ];

  reviewers = signal<ReviewerUser[]>([]);

  activeReviewers = computed(() => this.reviewers().filter(r => r.isActive && !r.isOnLeave));

  suggestedReviewer = computed(() => {
    const active = this.activeReviewers();
    const sorted = [...active].sort((a, b) => a.currentLoad - b.currentLoad);
    return sorted[0]?.id || '';
  });

  reviewerOnLeave = computed(() => {
    if (!this.selectedReviewer) return false;
    const rev = this.reviewers().find(r => r.id === this.selectedReviewer);
    return rev?.isOnLeave || false;
  });

  // ─── Read-Only Mode (view-only for statuses beyond DRAFT/ASSIGNED) ───
  get isReadOnly(): boolean {
    return this.draftStatus === 'SENT_TO_REVIEWER' || this.draftStatus === 'APPROVED' || this.draftStatus === 'APPROVED_ROUTED';
  }

  // ─── Mandatory Field Validation ───
  mandatoryFieldsComplete(): boolean {
    return this.complainantName.trim().length > 0 &&
           this.complainantState.trim().length > 0 &&
           this.complainantDistrict.trim().length > 0 &&
           (this.complainantPhone.trim().length > 0 || this.complainantEmail.trim().length > 0) &&
           this.category.trim().length > 0 &&
           this.entityName.trim().length > 0 &&
           this.subject.trim().length > 0;
  }

  missingMandatoryFields(): string[] {
    const missing: string[] = [];
    if (!this.complainantName.trim()) missing.push('Complainant Name');
    if (!this.complainantState.trim()) missing.push('State');
    if (!this.complainantDistrict.trim()) missing.push('District');
    if (!this.complainantPhone.trim() && !this.complainantEmail.trim()) missing.push('Phone or Email');
    if (!this.category.trim()) missing.push('Category');
    if (!this.entityName.trim()) missing.push('Entity Name');
    if (!this.subject.trim()) missing.push('Subject');
    return missing;
  }

  submitting = signal(false);
  submitted = signal(false);
  savingDraft = signal(false);
  draftSaved = signal(false);

  categories = ['ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'];

  states = signal<string[]>([]);
  districts = signal<string[]>([]);
  pincodeLoading = signal(false);

  loggedInUser: { id: string; name: string; role: string } | null = null;

  // ─── Entity Search (from REGULATED_ENTITIES table) ───
  entitySearchText = '';
  entitySearchResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  entitySearchLoading = signal(false);
  showEntityDropdown = signal(false);
  private entitySearchTimeout: any = null;
  entityDropdownTop = 0;
  entityDropdownLeft = 0;
  entityDropdownWidth = 300;

  // ─── Past Complaints ───
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);

  /**
   * What the shared similar-cases panel searches on.
   *
   * Replaces a POST to `/api/v1/past-complaints/similar`, which is a Groq LLM prompt with a keyword
   * fallback — out of scope for Brief 21, which bans LLM inference from this feature.
   *
   * `subject` and `description` are plain fields rather than signals on this screen, so they are read
   * through `draftRevision()` — bumped by loadDraft and by applySuggestion — to give the computed
   * something to depend on. Without that the panel would search whatever the fields held the first
   * time it was opened and never notice an edit.
   *
   * No category is sent: `this.category` is a category NAME typed by the officer, and the server
   * filters on the indexed `categoryId` term, so passing it would filter out every document and look
   * exactly like a genuine no-match.
   */
  similarSearchText = computed(() => {
    this.draftRevision();
    return [this.subject, this.description].filter(Boolean).join(' ').trim();
  });

  /** Bumped whenever the assessed fields are replaced wholesale, so similarSearchText recomputes. */
  private draftRevision = signal(0);

  /**
   * What the assistance rail is asked about on this screen.
   *
   * <p>This screen holds a DRAFT, not yet necessarily a complaint, so there are two cases and they are
   * kept distinct rather than merged:
   * <ul>
   *   <li>once the draft has been converted, `convertedComplaintId` is the real complaint NUMBER
   *       (`CrpcWorkflowService` sets it from `Complaint.complaintNumber`) and the rail can answer with
   *       both tiers;
   *   <li>before conversion there is no complaint number in existence, so the key is `draftId`
   *       (`DRF-000123`). Tier 1 joins on COMPLAINTS.complaint_number and will therefore find nothing,
   *       but tier 0 is an upsert keyed on (owner, this string) and so still carries the officer's own
   *       continuity across visits to the same draft — which is the half this screen can have.
   * </ul>
   *
   * <p>NOT `complaintReferenceNumber`: that field is free text the officer may type, and neither
   * `complaintReferenceNumber` nor `acknowledgementNumber` is emitted by
   * `/api/v1/email-syndication/drafts/{id}`, so it is blank unless hand-entered. A rail keyed on it
   * would silently change identity mid-session.
   *
   * <p>Read through `draftRevision()` for the same reason as `similarSearchText`: these are plain
   * fields, and a computed over a non-signal registers no dependency.
   */
  assistanceComplaintNumber = computed(() => {
    this.draftRevision();
    return (this.convertedComplaintNumber || this.draftId || '').trim();
  });

  /**
   * Where on this screen the DEO was, for the rail's `last-section` signal.
   *
   * <p>Derived from the three signals this screen ALREADY keeps — `editMode`, `activeStep` and
   * `currentTab` — rather than from `sectionOpen`, which is a plain object a `computed()` cannot
   * track and which describes its own defaults more than the officer's position. Those three ARE the
   * navigation on this screen: the two page tabs in read mode, and the two-step
   * creation/assignment stepper that replaces them in edit mode.
   *
   * <p>The label is PROSE, not a key: the server stores it verbatim and the rail prints it back as
   * "You were last in {section}", so `assignment` would reach an officer as that bare word.
   */
  private assistanceSection = computed(() => {
    if (this.editMode()) {
      return this.activeStep() === 'assignment'
        ? 'Draft assessment — Assignment'
        : 'Draft assessment — Complaint creation';
    }
    return this.currentTab() === 'attachments'
      ? 'Draft assessment — Email Communication'
      : 'Draft assessment — Summary';
  });

  // ─── Past Complaint Detail Modal ───
  showPastComplaintDetail = signal(false);
  pastComplaintDetail = signal<any>(null);
  loadingPastDetail = signal(false);

  ngOnInit() {
    const stored = sessionStorage.getItem('crpc_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r => ['DEO', 'REVIEWER', 'CRPC_HEAD'].includes(r)) || 'DEO';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
        sessionStorage.setItem('crpc_user', JSON.stringify(this.loggedInUser));
      }
    }
    this.draftId = this.route.snapshot.paramMap.get('id') || '';
    this.loadDraft();
    this.loadStates();
    this.crpcService.getReviewers().subscribe(data => {
      if (data.length > 0) {
        this.reviewers.set(data);
      } else {
        this.reviewers.set([
          { id: 'reviewer1', displayName: 'Meera Krishnan', email: 'meera@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 6, region: 'South', sortOrder: 1 },
          { id: 'reviewer.user', displayName: 'Shikha P', email: 'shikha@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 10, region: 'North', sortOrder: 2 },
          { id: 'REV003', displayName: 'Radhika Rao', email: 'radhika@rbi.org.in', isActive: true, isOnLeave: true, maxLoad: 25, currentLoad: 12, region: 'North', sortOrder: 3 },
          { id: 'REV004', displayName: 'Priya Sharma', email: 'priya@rbi.org.in', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 15, region: 'West', sortOrder: 4 },
        ]);
      }
    });
  }

  loadStates() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => this.states.set(res?.data || []),
      error: () => {}
    });
  }

  onStateChange(state: string) {
    this.complainantState = state;
    this.complainantDistrict = '';
    this.districts.set([]);
    if (state) {
      this.loadDistrictsForState(state);
    }
  }

  loadDistrictsForState(state: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state } }).subscribe({
      next: (res) => this.districts.set(res?.data || []),
      error: () => {}
    });
  }

  onPincodeInput(value: string) {
    this.complainantPincode = value;
    if (value && value.length === 6 && /^\d{6}$/.test(value)) {
      this.pincodeLoading.set(true);
      this.http.get<any[]>(`/api/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading.set(false);
          if (res && res[0] && res[0].Status === 'Success' && res[0].PostOffice?.length) {
            const po = res[0].PostOffice[0];
            if (po.State) {
              this.complainantState = po.State;
              this.onStateChange(po.State);
            }
            if (po.District) {
              this.complainantDistrict = po.District;
            }
          }
        },
        error: () => this.pincodeLoading.set(false)
      });
    }
  }

  onEntitySearchInput(value: string) {
    this.entitySearchText = value;
    if (this.entitySearchTimeout) clearTimeout(this.entitySearchTimeout);
    this.entitySearchTimeout = setTimeout(() => this.searchEntities(value), 300);
  }

  searchEntities(query: string) {
    this.entitySearchLoading.set(true);
    this.showEntityDropdown.set(true);
    const params: any = {};
    if (query) params.search = query;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, { params }).subscribe({
      next: (res) => {
        this.entitySearchResults.set(res?.data || []);
        this.entitySearchLoading.set(false);
      },
      error: () => {
        this.entitySearchResults.set([]);
        this.entitySearchLoading.set(false);
      }
    });
  }

  selectEntity(entity: { id: number; name: string; department: string; entityType: string; state?: string }) {
    this.entityName = entity.name;
    this.entityType = entity.entityType || 'BANK';
    this.entityState = (entity as any).state || '';
    this.entitySearchText = entity.name;
    this.showEntityDropdown.set(false);
    this.eligibilityDepartment.set(entity.department === 'CEPC' ? 'CEPC' : 'RBIO');
  }

  onEntityFocus(inputEl: HTMLInputElement) {
    const rect = inputEl.getBoundingClientRect();
    this.entityDropdownTop = rect.bottom + 2;
    this.entityDropdownLeft = rect.left;
    this.entityDropdownWidth = rect.width;
    this.showEntityDropdown.set(true);
    this.searchEntities(this.entitySearchText || '');
  }

  onEntityBlur() {
    setTimeout(() => this.showEntityDropdown.set(false), 200);
  }

  loadPastComplaints() {
    if (!this.complainantEmail && !this.complainantPhone) return;
    this.loadingPastComplaints.set(true);

    const params: any = { excludeId: this.draftId };
    if (this.complainantEmail) params.email = this.complainantEmail;
    if (this.complainantPhone) params.phone = this.complainantPhone;

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/past-complaints/by-complainant`, { params })
      .subscribe({
        next: (res) => {
          this.pastComplaints.set(res?.data || []);
          this.loadingPastComplaints.set(false);
        },
        error: () => this.loadingPastComplaints.set(false)
      });
  }

  openPastComplaintDetail(complaintId: string) {
    this.showPastComplaintDetail.set(true);
    this.loadingPastDetail.set(true);
    this.pastComplaintDetail.set(null);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/past-complaints/detail/${complaintId}`)
      .subscribe({
        next: (res) => {
          this.pastComplaintDetail.set(res?.data || null);
          this.loadingPastDetail.set(false);
        },
        error: () => {
          this.loadingPastDetail.set(false);
          this.showPastComplaintDetail.set(false);
        }
      });
  }

  closePastComplaintDetail() {
    this.showPastComplaintDetail.set(false);
    this.pastComplaintDetail.set(null);
  }

  applySuggestion(index: number) {
    const suggs = this.suggestions();
    const s = suggs[index];
    if (!s || s.applied) return;

    switch (s.field) {
      case 'Entity': this.entityName = s.value; break;
      case 'Category': this.category = s.value; break;
      case 'Subject': this.subject = s.value; break;
      case 'Amount': this.amountInvolved = Number(s.value.replace(/[^\d.]/g, '')) || null; break;
      case 'State': this.complainantState = s.value; break;
      case 'District': this.complainantDistrict = s.value; break;
    }

    const updated = suggs.map((sg, i) => i === index ? { ...sg, applied: true } : sg);
    this.suggestions.set(updated);
    // Accepting a Subject suggestion changes what "similar" means, and `subject` is a plain field the
    // computed cannot observe, so the revision is bumped to invalidate the panel's cached search.
    this.draftRevision.update(n => n + 1);
  }

  applyAllSuggestions() {
    const suggs = this.suggestions();
    suggs.forEach((_, i) => {
      if (!suggs[i].applied) this.applySuggestion(i);
    });
  }

  togglePdfExpand() {
    this.pdfExpanded.set(!this.pdfExpanded());
  }

  openAttachmentsPanel() {
    const atts = this.attachments();
    console.log('[Attachments] Opening panel, count:', atts.length, atts.map(a => ({ id: a.id, name: a.name, url: a.url })));
    if (atts.length === 0) { this.showAttachmentsPanel.set(true); return; }
    atts.forEach(att => {
      if (!this.openedDocTabs().find(t => t.id === att.id)) {
        this.openedDocTabs.update(tabs => [...tabs, att]);
      }
      this.loadBlobUrl(att);
    });
    this.activeDocTabId.set(atts[0].id);
    this.showAttachmentsPanel.set(true);
  }

  closeAttachmentsPanel() {
    this.showAttachmentsPanel.set(false);
    this.viewingAttachment.set(null);
    // Reset tab viewer back to pinned email/document tab
    this.openedDocTabs.set([]);
    this.activeDocTabId.set(null);
  }

  viewAttachment(att: Attachment) {
    this.viewingAttachment.set(att);
    this.pdfCurrentPage.set(1);
  }

  viewAttachmentInNewTab(att: Attachment) {
    if (!att.url) return;
    const cached = this.blobUrlCache.get(att.id);
    if (cached) {
      window.open(cached, '_blank');
    } else {
      this.http.get(att.url, { responseType: 'blob' }).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank');
        },
        error: () => {
          window.open(att.url, '_blank');
        }
      });
    }
  }

  downloadAttachment(att: Attachment) {
    if (!att.url) return;
    const cached = this.blobUrlCache.get(att.id);
    if (cached) {
      const a = document.createElement('a');
      a.href = cached;
      a.download = att.name;
      a.click();
    } else {
      this.http.get(att.url, { responseType: 'blob' }).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          const a = document.createElement('a');
          a.href = url;
          a.download = att.name;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
        },
        error: () => {
          window.open(att.url, '_blank');
        }
      });
    }
  }

  closeViewingAttachment() {
    this.viewingAttachment.set(null);
  }

  pdfNextPage() {
    if (this.pdfCurrentPage() < this.pdfTotalPages()) {
      this.pdfCurrentPage.set(this.pdfCurrentPage() + 1);
    }
  }

  pdfPrevPage() {
    if (this.pdfCurrentPage() > 1) {
      this.pdfCurrentPage.set(this.pdfCurrentPage() - 1);
    }
  }

  toggleHistoryPanel() {
    if (this.showHistoryPanel()) {
      this.showHistoryPanel.set(false);
    } else {
      this.showHistoryPanel.set(true);
      this.showSimilarPanel.set(false);
      this.showAttachmentsPanel.set(false);
      this.showAssistancePanel.set(false);
      this.loadHistory();
    }
  }

  /**
   * Opens the drawer. No fetch here: the shared panel searches on first open and caches until the
   * subject changes, which is also why re-opening it no longer re-queries.
   */
  toggleSimilarPanel() {
    if (this.showSimilarPanel()) {
      this.showSimilarPanel.set(false);
    } else {
      this.showSimilarPanel.set(true);
      this.showHistoryPanel.set(false);
      this.showAttachmentsPanel.set(false);
      this.showAssistancePanel.set(false);
    }
  }

  /**
   * Opens the assistance drawer. Mutually exclusive with the other three, which share the slot.
   * No fetch here: the rail reads itself on first open and caches until the complaint changes.
   */
  toggleAssistancePanel() {
    if (this.showAssistancePanel()) {
      this.showAssistancePanel.set(false);
    } else {
      this.showAssistancePanel.set(true);
      this.showSimilarPanel.set(false);
      this.showHistoryPanel.set(false);
      this.showAttachmentsPanel.set(false);
    }
  }

  loadHistory() {
    this.loadingHistory.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.draftId}/timeline`)
      .subscribe({
        next: (res) => {
          this.historyEntries.set(res?.data || []);
          this.loadingHistory.set(false);
        },
        error: () => {
          this.historyEntries.set([]);
          this.loadingHistory.set(false);
        }
      });
  }

  loadDraft() {
    this.loading.set(true);
    const isPhysicalLetter = this.draftId.startsWith('DRF-2026');

    if (isPhysicalLetter) {
      const saved = sessionStorage.getItem('physicalLetterDraft');
      const draft = saved ? JSON.parse(saved) : null;

      this.complainantName = draft?.complainantName || '';
      this.complainantPhone = draft?.complainantPhone || '';
      this.complainantEmail = draft?.complainantEmail || '';
      this.complainantAddress = draft?.complainantAddress || '';
      this.complainantState = draft?.complainantState || '';
      this.complainantDistrict = draft?.complainantDistrict || '';
      this.complainantPincode = draft?.complainantPincode || '';
      if (this.complainantPincode && !this.complainantState) {
        this.onPincodeInput(this.complainantPincode);
      }
      this.contactPreference = 'POST';

      this.modeOfReceipt = 'PHYSICAL_LETTER';
      this.category = draft?.category || '';
      this.entityName = draft?.entityName || '';
      this.entitySearchText = this.entityName;
      this.entityType = draft?.entityType || 'BANK';
      this.resolveEligibilityDepartment(this.entityName);
      this.subject = draft?.subject || '';
      this.description = draft?.description || '';
      this.amountInvolved = draft?.amountInvolved || null;
      this.transactionDate = draft?.transactionDate || '';
      this.letterDate = draft?.letterDate || '';
      this.receivedDate = draft?.receivedDate || new Date().toISOString().split('T')[0];
      this.emailType = '';
      this.systemSuggestion = 'PENDING';
      this.vernacular = draft?.vernacular || false;
      this.vernacularLanguage = draft?.vernacularLanguage || '';

      this.attachments.set([
        { id: 'ATT-001', name: draft?.fileName || 'scanned_letter.pdf', size: draft?.fileSize || '2.4 MB', type: 'application/pdf', uploadedAt: new Date().toISOString(), uploadedBy: 'DEO' },
      ]);

      // Build suggestions from OCR-extracted data
      const suggs: { field: string; value: string; applied: boolean }[] = [];
      if (draft?.entityName) suggs.push({ field: 'Entity', value: draft.entityName, applied: false });
      if (draft?.category) suggs.push({ field: 'Category', value: draft.category, applied: false });
      if (draft?.amountInvolved) suggs.push({ field: 'Amount', value: `₹${draft.amountInvolved}`, applied: false });
      if (draft?.subject) suggs.push({ field: 'Subject', value: draft.subject, applied: false });
      this.suggestions.set(suggs);

      // PDF preview handed over from the physical-letter screen via sessionStorage.
      //
      // VALIDATED BEFORE TRUSTING. The value is only ever a blob: URL this application minted for a file
      // the officer selected, and that is all we accept. sessionStorage is writable by any script on the
      // origin, so passing whatever it holds to bypassSecurityTrustResourceUrl would turn a stored string
      // into a trusted iframe source — a javascript: or data: payload would execute in the officer's
      // session. Blob URLs cannot carry script this way, so pinning the scheme closes it.
      const pdfBlob = sessionStorage.getItem('physicalLetterPdfUrl');
      if (pdfBlob?.startsWith('blob:')) {
        this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(pdfBlob));
      } else if (pdfBlob) {
        console.warn('Ignoring non-blob physicalLetterPdfUrl in sessionStorage');
        sessionStorage.removeItem('physicalLetterPdfUrl');
      }

      this.emailCorrespondence.set([]);
      this.loading.set(false);
      this.updateEmailHighlight();
      if (this.complainantState) this.loadDistrictsForState(this.complainantState);
      this.loadPastComplaints();
      // Similar cases are NOT searched here any more: the shared panel fetches on first open. The old
      // eager call ran a search on every screen load whose result most officers never looked at.
      this.draftRevision.update(n => n + 1);
    } else {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`)
        .subscribe({
          next: (res) => {
            const draft = res?.data || {};
            this.complainantName = draft.complainantName || '';
            this.complainantPhone = draft.complainantPhone || '';
            this.complainantEmail = draft.senderEmail || '';
            this.complainantAddress = draft.complainantAddress || '';
            this.complainantState = draft.complainantState || '';
            this.complainantDistrict = draft.complainantDistrict || '';
            this.complainantPincode = draft.complainantPincode || '';
            if (this.complainantPincode && !this.complainantState) {
              this.onPincodeInput(this.complainantPincode);
            }
            this.contactPreference = 'EMAIL';

            this.modeOfReceipt = (draft.modeOfReceipt as any) || 'EMAIL';
            this.cpgramsReference = draft.cpgramsNumber || '';
            this.isCpgram = !!this.cpgramsReference || this.modeOfReceipt === 'CPGRAMS';
            this.complaintReferenceNumber = draft.complaintReferenceNumber || draft.acknowledgementNumber || '';
            this.convertedComplaintNumber = draft.convertedComplaintId || '';
            this.category = draft.category || '';
            this.entityName = draft.entityName || '';
            this.entitySearchText = this.entityName;
            this.entityType = draft.entityType || 'BANK';
            this.resolveEligibilityDepartment(this.entityName);
            this.subject = draft.subject || draft.complaintSummary || '';
            this.description = draft.body || '';
            this.amountInvolved = draft.amountInvolved || null;
            this.transactionDate = draft.transactionDate || '';
            this.receivedDate = draft.receivedAt ? draft.receivedAt.split('T')[0] : '';
            this.emailType = 'TO';
            this.draftStatus = draft.status || 'ASSIGNED';
            this.selectedReviewer = draft.assignedTo || '';
            this.deoAssessmentDecision = draft.deoDecision || '';
            this.deoDecision = draft.deoDecision || '';
            this.nonMaintainableReason = draft.nonMaintainableReason || '';
            this.deoAssessmentRemarks = draft.deoRemarks || '';
            this.systemSuggestion = draft.systemSuggestion || 'PENDING';
            this.vernacular = draft.isVernacular || false;
            this.vernacularLanguage = draft.languageName || '';
            this.hydrateEligibility(draft.autoClosureResponsesJson);

            const attachments = (draft.attachments || []).map((a: any, i: number) => {
              const attId = a.id || `ATT-${i + 1}`;
              const numericId = String(attId).replace('ATT-', '');
              return {
                id: attId,
                name: a.fileName || `attachment_${i + 1}`,
                size: a.fileSize ? this.formatFileSize(a.fileSize) : 'Unknown',
                type: a.fileType || 'application/octet-stream',
                uploadedAt: a.createdAt || new Date().toISOString(),
                uploadedBy: 'SYSTEM',
                url: `${environment.apiBaseUrl}/api/files/email-draft/${numericId}`
              };
            });
            this.attachments.set(attachments);

            // Apply OCR-extracted fields if available from ingest
            if (draft.ocrExtractedFields) {
              this.applyOcrFields(draft.ocrExtractedFields);
              this.ocrApplied.set(true);
            } else if (draft.ocrProcessed && draft.ocrConfidence > 0) {
              this.ocrApplied.set(true);
            }

            // Build suggestions from draft data
            const suggs: { field: string; value: string; applied: boolean }[] = [];
            if (draft.entityName) suggs.push({ field: 'Entity', value: draft.entityName, applied: false });
            if (draft.category) suggs.push({ field: 'Category', value: draft.category, applied: false });
            if (draft.amountInvolved) suggs.push({ field: 'Amount', value: `₹${draft.amountInvolved}`, applied: false });
            if (draft.ocrExtractedFields?.subject) suggs.push({ field: 'Subject', value: draft.ocrExtractedFields.subject, applied: false });
            this.suggestions.set(suggs);

            if (draft.senderEmail) {
              this.emailCorrespondence.set([{
                id: 'EC-001',
                direction: 'RECEIVED',
                subject: draft.subject || '',
                to: 'crpc@rbi.org.in',
                sentAt: draft.receivedAt || '',
                body: draft.body || 'Original complaint email received.',
              }]);
            } else {
              this.emailCorrespondence.set([]);
            }

            this.loading.set(false);
            this.updateEmailHighlight();
            if (this.complainantState) this.loadDistrictsForState(this.complainantState);
            this.loadPastComplaints();
            // See the physical-letter branch: the similar-cases search is lazy now.
            this.draftRevision.update(n => n + 1);
          },
          error: () => {
            this.loading.set(false);
          }
        });
    }
  }

  private formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  // ─── Attachment Management ───
  onFileUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff', 'message/rfc822'];
    if (!allowed.includes(file.type) && !file.name.endsWith('.eml')) {
      this.uploadError = 'Only PDF, JPEG, PNG, TIFF, or EML files are accepted.';
      return;
    }
    // The limit is CONFIGURED (cms.upload.max_file_size), not compiled in: a hardcoded 2 MB here
    // rejected files the server accepts, and hardcoding 5 would drift the next time it is retuned.
    if (file.size > this.uploadLimits.maxFileSizeBytes()) {
      this.uploadError = `File size must not exceed ${this.uploadLimits.maxFileSizeMb()} MB.`;
      return;
    }

    this.uploadError = '';
    const objectUrl = URL.createObjectURL(file);
    const newAtt: Attachment = {
      id: 'ATT-' + Date.now(),
      name: file.name,
      size: (file.size / 1024).toFixed(0) + ' KB',
      type: file.type,
      uploadedAt: new Date().toISOString(),
      uploadedBy: 'DEO',
      url: objectUrl
    };
    this.attachments.set([...this.attachments(), newAtt]);
    input.value = '';
  }

  activePreviewIndex = signal(0);

  activePreviewAttachment = computed(() => {
    const atts = this.attachments();
    const idx = this.activePreviewIndex();
    return atts.length > 0 ? atts[Math.min(idx, atts.length - 1)] : null;
  });

  // ─── Attachment Tabs ───
  openedDocTabs = signal<Attachment[]>([]);
  activeDocTabId = signal<string | null>(null);

  openAttachmentTab(att: Attachment) {
    if (!this.openedDocTabs().find(t => t.id === att.id)) {
      this.openedDocTabs.update(tabs => [...tabs, att]);
    }
    this.activeDocTabId.set(att.id);
    this.loadBlobUrl(att);
  }

  private loadBlobUrl(att: Attachment) {
    if (!att.url || this.blobUrlCache.has(att.id)) return;
    this.http.get(att.url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const objectUrl = URL.createObjectURL(blob);
        this.blobUrlCache.set(att.id, objectUrl);
        this.attachmentBlobUrls.update(map => ({
          ...map,
          [att.id]: this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl)
        }));
      },
      error: (err) => {
        // NO SANITISER BYPASS ON THE FAILURE PATH. This previously fell back to trusting `att.url`
        // verbatim, which is server-supplied data reaching bypassSecurityTrustResourceUrl — the fetch
        // having just failed is the least safe moment to start trusting it. A blob URL we minted
        // ourselves is safe to trust; an arbitrary URL from a record is not.
        //
        // The tab now shows a load error instead. Nothing is silently swallowed, and the officer is told
        // the document could not be fetched rather than being shown a frame pointed at an unvetted URL.
        console.error('Failed to load attachment blob:', att.id, err);
        this.attachmentLoadFailed.update(ids => new Set(ids).add(att.id));
      }
    });
  }

  ngOnDestroy() {
    this.blobUrlCache.forEach(url => URL.revokeObjectURL(url));
    this.blobUrlCache.clear();
    if (this.assistanceWriteTimer) clearTimeout(this.assistanceWriteTimer);
    this.rememberAssistanceVisit();
  }

  /**
   * Pending debounce for the tier-0 write.
   *
   * <p>A plain timer rather than the rxjs `Subject` + `debounceTime` that staff/task-action uses: this
   * component holds no other rxjs pipeline and is not built in an injection context that
   * `takeUntilDestroyed()` could hook, so a timer cleared in `ngOnDestroy` is the smaller change. The
   * debounce itself is the point either way — a PUT per keystroke on a textarea an officer types
   * paragraphs into would be hundreds of writes for one visit, to a row whose only meaningful state is
   * its last value.
   */
  private assistanceWriteTimer: ReturnType<typeof setTimeout> | null = null;

  /**
   * How long the officer must pause before the tier-0 write goes out. Long enough that a sentence is
   * one write rather than forty; the navigate-away flush in `ngOnDestroy` covers the remainder.
   */
  private static readonly ASSISTANCE_WRITE_DEBOUNCE_MS = 2000;

  /**
   * Officer activity worth recording: a keystroke in the Comments box, or a move between the page
   * tabs / stepper steps. Coalesces a burst into one write.
   *
   * <p>Deliberately does NOT skip an empty box: the rail records "the Comments box is now empty" as a
   * fact, so clearing the text has to be written too, or the panel would go on offering to restore
   * text the officer deleted.
   */
  onAssistanceActivity() {
    if (this.assistanceWriteTimer) clearTimeout(this.assistanceWriteTimer);
    this.assistanceWriteTimer = setTimeout(
      () => this.rememberAssistanceVisit(),
      DraftAssessmentComponent.ASSISTANCE_WRITE_DEBOUNCE_MS);
  }

  /**
   * The assistance rail's tier-0 write (Brief 21).
   *
   * <p>Reached two ways, and both are needed. The debounce above covers an officer still on the screen;
   * the `ngOnDestroy` call is the navigate-away FLUSH, because the timer is cleared with the component
   * and an officer who types and leaves inside the debounce window would otherwise lose exactly those
   * keystrokes — the text the `unsaved-draft` signal exists to offer back.
   *
   * <p>`section` comes from {@link assistanceSection}, which names where on the screen the officer
   * actually is; the rail reads it back verbatim as "You were last in ...".
   *
   * <p>`deoRemarks` is the free-text box on this screen that is not persisted until the draft is saved
   * or routed, so it is the text the officer can actually lose. It is sent even when blank — the server
   * overwrites with null on purpose, because "the remarks box is now empty" is a fact, and treating
   * blank as "leave the old value" would keep offering to restore text already submitted.
   *
   * <p>Fire-and-forget, with no confirmation UI in either branch. This is background continuity, not a
   * save the officer asked for, so there is nothing to announce — and a confirmation invented here
   * would repeat the recorded defect where an error handler called `draftSaved(true)` for a save that
   * never happened. `success` is the server's verdict and this caller does not second-guess it.
   */
  private rememberAssistanceVisit() {
    const complaintNumber = this.assistanceComplaintNumber();
    if (!complaintNumber) return;
    this.assistanceRail
      .rememberVisit(complaintNumber, this.assistanceSection(), this.deoRemarks)
      .subscribe({ next: () => {}, error: () => {} });
  }

  closeAttachmentTab(id: string, event: Event) {
    event.stopPropagation();
    const remaining = this.openedDocTabs().filter(t => t.id !== id);
    this.openedDocTabs.set(remaining);
    if (this.activeDocTabId() === id) {
      this.activeDocTabId.set(remaining.length > 0 ? remaining[remaining.length - 1].id : null);
    }
  }

  activeDocTabAttachment = computed(() => {
    const id = this.activeDocTabId();
    return id ? this.openedDocTabs().find(t => t.id === id) ?? null : null;
  });

  prevDocument() {
    const idx = this.activePreviewIndex();
    if (idx > 0) this.activePreviewIndex.set(idx - 1);
  }

  nextDocument() {
    const idx = this.activePreviewIndex();
    if (idx < this.attachments().length - 1) this.activePreviewIndex.set(idx + 1);
  }

  removeAttachment(id: string) {
    this.attachments.set(this.attachments().filter(a => a.id !== id));
  }

  // ─── OCR Scanning ───
  ocrScanning = signal(false);
  ocrApplied = signal(false);
  ocrError = signal('');
  ocrFieldsExtracted = signal<Record<string, string>>({});

  scanAttachmentOcr(att: Attachment) {
    this.ocrScanning.set(true);
    this.ocrError.set('');

    // For attachments already on the server, call OCR endpoint with the attachment reference
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/ocr/extract-from-draft`, {
      draftId: this.draftId,
      attachmentId: att.id,
      fileName: att.name
    }).subscribe({
      next: (res) => {
        const data = res?.data || {};
        if (Object.keys(data).length === 0) {
          this.ocrError.set('OCR extraction returned no data. Try uploading a clearer scan.');
          this.ocrScanning.set(false);
          return;
        }
        this.applyOcrFields(data);
        this.ocrFieldsExtracted.set(data);
        this.ocrApplied.set(true);
        this.ocrScanning.set(false);
      },
      error: (err) => {
        this.ocrError.set(err.error?.message || 'OCR extraction failed. Please fill fields manually.');
        this.ocrScanning.set(false);
      }
    });
  }

  scanUploadedFileOcr(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];
    if (!allowed.includes(file.type)) {
      this.ocrError.set('Only PDF, JPEG, PNG, or TIFF files can be scanned.');
      return;
    }

    this.ocrScanning.set(true);
    this.ocrError.set('');

    const formData = new FormData();
    formData.append('file', file);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/ocr/extract`, formData)
      .subscribe({
        next: (res) => {
          const data = res?.data || {};
          if (Object.keys(data).length === 0) {
            this.ocrError.set('AI extraction returned no data. API quota may be exhausted.');
            this.ocrScanning.set(false);
            return;
          }
          this.applyOcrFields(data);
          this.ocrFieldsExtracted.set(data);
          this.ocrApplied.set(true);
          this.ocrScanning.set(false);

          // Also add to attachments list
          const objectUrl = URL.createObjectURL(file);
          const newAtt: Attachment = {
            id: 'ATT-' + Date.now(),
            name: file.name,
            size: (file.size / 1024).toFixed(0) + ' KB',
            type: file.type,
            uploadedAt: new Date().toISOString(),
            uploadedBy: 'DEO',
            url: objectUrl
          };
          this.attachments.set([...this.attachments(), newAtt]);
        },
        error: (err) => {
          this.ocrError.set(err.error?.message || 'OCR failed. Please fill fields manually.');
          this.ocrScanning.set(false);
        }
      });

    input.value = '';
  }

  private applyOcrFields(data: Record<string, string>) {
    if (data['complainantName'] && !this.complainantName) this.complainantName = data['complainantName'];
    if (data['complainantPhone'] && !this.complainantPhone) this.complainantPhone = data['complainantPhone'];
    if (data['complainantEmail'] && !this.complainantEmail) this.complainantEmail = data['complainantEmail'];
    if (data['complainantAddress'] && !this.complainantAddress) this.complainantAddress = data['complainantAddress'];
    if (data['complainantState'] && !this.complainantState) this.complainantState = data['complainantState'];
    if (data['complainantDistrict'] && !this.complainantDistrict) this.complainantDistrict = data['complainantDistrict'];
    if (data['complainantPincode'] && !this.complainantPincode) {
      this.complainantPincode = data['complainantPincode'];
      this.onPincodeInput(data['complainantPincode']);
    }
    if (data['subject'] && !this.subject) this.subject = data['subject'];
    if (data['description'] && !this.description) this.description = data['description'];
    if (data['entityName'] && !this.entityName) this.entityName = data['entityName'];
    if (data['entityType'] && !this.entityType) this.entityType = data['entityType'];
    if (data['category'] && !this.category) this.category = data['category'];
    if (data['amountInvolved'] && !this.amountInvolved) this.amountInvolved = Number(data['amountInvolved']) || null;
    if (data['transactionDate'] && !this.transactionDate) this.transactionDate = data['transactionDate'];
    if (data['letterDate'] && !this.letterDate) this.letterDate = data['letterDate'];
  }

  // ─── Email: Request Additional Information ───
  openEmailComposer() {
    this.emailTo = this.complainantEmail;
    this.emailSubject = `RE: ${this.subject}`;
    this.emailBody = '';
    this.showEmailComposer.set(true);
  }

  // ═══ UST656: Email Restriction - RBI Domain Only ═══
  emailDomainError = '';

  sendEmail() {
    if (!this.emailTo || !this.emailBody) return;

    // UST656: Validate recipient against RBI domain
    const recipients = this.emailTo.split(/[,;]/).map(e => e.trim()).filter(e => e);
    const invalidEmails = recipients.filter(email =>
      !email.toLowerCase().endsWith('@rbi.org.in') && !email.toLowerCase().endsWith('@rbi.gov.in')
    );
    if (invalidEmails.length > 0) {
      this.emailDomainError = 'Only official RBI email addresses can be used for outbound complaint emails';
      // Log the rejected attempt
      this.http.post(`${environment.apiBaseUrl}/api/v1/workflow/validate-email-recipients`, {
        recipients: invalidEmails,
        actor: this.loggedInUser?.id || 'unknown'
      }).subscribe();
      return;
    }
    this.emailDomainError = '';

    this.sendingEmail.set(true);

    if (this.attachFormPdf) {
      this.generateAndAttachFormPdf();
    }

    setTimeout(() => {
      const attachNote = this.attachFormPdf ? '\n\n[Attached: Additional_Information_Form.pdf (editable)]' : '';
      const newEmail: EmailCorrespondence = {
        id: 'EC-' + Date.now(),
        direction: 'SENT',
        subject: this.emailSubject,
        to: this.emailTo,
        sentAt: new Date().toISOString(),
        body: this.emailBody + attachNote,
      };
      this.emailCorrespondence.set([...this.emailCorrespondence(), newEmail]);
      this.sendingEmail.set(false);
      this.showEmailComposer.set(false);
      this.attachFormPdf = false;
    }, 1000);
  }

  cancelEmail() {
    this.showEmailComposer.set(false);
    this.attachFormPdf = false;
  }

  async downloadFormPdf() {
    const { jsPDF } = await import('jspdf');
    const doc = this.buildFormPdf(jsPDF);
    doc.save('Additional_Information_Form.pdf');
  }

  private async generateAndAttachFormPdf() {
    const { jsPDF } = await import('jspdf');
    this.buildFormPdf(jsPDF);
  }

  private getFieldPrefillValue(key: string): string {
    const map: Record<string, string> = {
      name: this.complainantName || '',
      phone: this.complainantPhone || '',
      email: this.complainantEmail || '',
      address: this.complainantAddress || '',
      state: this.complainantState || '',
      district: this.complainantDistrict || '',
      pincode: this.complainantPincode || '',
      accountNumber: '',
      transactionId: '',
      transactionDate: this.transactionDate || '',
      amount: this.amountInvolved ? String(this.amountInvolved) : '',
      branchName: '',
      description: this.description || '',
      supportingDocs: '',
    };
    return map[key] || '';
  }

  private buildFormPdf(jsPDF: any): any {
    const doc = new jsPDF();
    const selectedFields = this.formPdfFields.filter(f => f.include);
    const pageWidth = doc.internal.pageSize.getWidth();
    const fieldWidth = pageWidth - 28;

    // Header
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text('Reserve Bank of India - Integrated Ombudsman Scheme', pageWidth / 2, 20, { align: 'center' });

    doc.setFontSize(11);
    doc.setFont('helvetica', 'normal');
    doc.text('ADDITIONAL INFORMATION REQUEST FORM', pageWidth / 2, 28, { align: 'center' });

    doc.setFontSize(9);
    doc.text(`Reference: ${this.draftId}`, 14, 38);
    doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, 14, 44);
    doc.text(`To: ${this.complainantName || 'Complainant'}`, 14, 50);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'italic');
    doc.text('Please fill in / correct the details below and return this form to crpc@rbi.org.in', 14, 60);
    doc.text('Fields are editable - click on any field to type or modify the pre-filled value.', 14, 66);
    doc.setFont('helvetica', 'normal');

    // Editable AcroForm fields
    let y = 76;

    for (const field of selectedFields) {
      if (y > 260) {
        doc.addPage();
        y = 20;
      }

      doc.setFontSize(9);
      doc.setFont('helvetica', 'bold');
      doc.text(`${field.label}:`, 14, y);
      y += 2;

      const isMultiline = field.key === 'description' || field.key === 'address';
      const fieldHeight = isMultiline ? 25 : 10;
      const prefill = this.getFieldPrefillValue(field.key);

      const textField = new doc.AcroFormTextField();
      textField.fieldName = field.key;
      textField.x = 14;
      textField.y = y;
      textField.width = fieldWidth;
      textField.height = fieldHeight;
      textField.fontSize = 10;
      textField.value = prefill;
      textField.defaultValue = prefill;
      textField.multiline = isMultiline;
      textField.readOnly = false;
      doc.addField(textField);

      y += fieldHeight + 8;
    }

    // Footer
    y += 5;
    if (y > 270) {
      doc.addPage();
      y = 20;
    }
    doc.setFontSize(8);
    doc.setFont('helvetica', 'italic');
    doc.text('Signature / Date: _______________________________', 14, y);
    y += 8;
    doc.text('Note: Failure to provide the requested information within 15 days may result in closure of the complaint.', 14, y);

    return doc;
  }

  // ─── Auto-Closure Screening (Sequential) ───
  answerScreening(answer: 'YES' | 'NO') {
    const idx = this.currentScreeningIndex();
    const updated = this.screeningQuestions().map((q, i) =>
      i === idx ? { ...q, answer } : q
    );
    this.screeningQuestions.set(updated);

    if (idx < updated.length - 1) {
      this.currentScreeningIndex.set(idx + 1);
    }
  }

  resetScreening() {
    this.screeningQuestions.set(this.screeningQuestions().map(q => ({ ...q, answer: null })));
    this.currentScreeningIndex.set(0);
  }

  // ─── Comment Templates ───
  applyTemplate(templateId: string) {
    const tmpl = this.commentTemplates.find(t => t.id === templateId);
    if (tmpl) {
      this.deoRemarks = tmpl.text;
      this.savedTemplateId = templateId;
    }
  }

  // ─── Reviewer Routing ───
  useRoundRobin() {
    this.selectedReviewer = this.suggestedReviewer();
  }

  onAssignmentModeChange(mode: string) {
    if (mode === 'RoundRobin') {
      this.useRoundRobin();
    } else {
      this.selectedReviewer = '';
    }
  }

  getReviewerName(id: string): string {
    const rev = this.reviewers().find(r => r.id === id);
    return rev?.displayName || id || '';
  }

  // ─── Save Draft (without sending) ───
  saveDraft() {
    this.savingDraft.set(true);

    this.http.put<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`, {
      complainantName: this.complainantName,
      complainantPhone: this.complainantPhone,
      complainantAddress: this.complainantAddress,
      complainantState: this.complainantState,
      complainantDistrict: this.complainantDistrict,
      complainantPincode: this.complainantPincode,
      subject: this.subject,
      body: this.description,
      category: this.category,
      entityName: this.entityName,
      entityType: this.entityType,
      cpgramsNumber: this.cpgramsReference,
      complaintReferenceNumber: this.complaintReferenceNumber,
      // Without the decision the eligibility answers cannot be shown again on reload: the
      // question set only renders for a New Complaint.
      deoDecision: this.deoDecision,
      nonMaintainableReason: this.nonMaintainableReason,
      ...this.eligibilityPayload(),
    }).subscribe({
      next: () => {
        this.savingDraft.set(false);
        this.draftSaved.set(true);
        setTimeout(() => this.draftSaved.set(false), 3000);
      },
      error: () => {
        this.savingDraft.set(false);
      }
    });
  }

  // ─── Confirmation Dialog Methods ───
  openConfirmDialog() {
    this.confirmAssignmentMode = 'Automatic';
    const active = this.reviewers().filter(r => r.isActive && !r.isOnLeave);
    const lowestLoad = [...active].sort((a, b) => a.currentLoad - b.currentLoad)[0];
    this.selectedReviewerName = lowestLoad?.displayName || active[0]?.displayName || '';
    this.showConfirmDialog.set(true);
  }

  onConfirmAssignmentModeChange(mode: string) {
    if (mode === 'Automatic') {
      const active = this.reviewers().filter(r => r.isActive && !r.isOnLeave);
      const lowestLoad = [...active].sort((a, b) => a.currentLoad - b.currentLoad)[0];
      this.selectedReviewerName = lowestLoad?.displayName || active[0]?.displayName || '';
    } else {
      this.selectedReviewerName = '';
    }
  }

  confirmReviewerOnLeave(): boolean {
    if (!this.selectedReviewerName.trim()) return false;
    const onLeaveNames = this.reviewers().filter(r => r.isOnLeave).map(r => r.displayName.toLowerCase());
    return onLeaveNames.includes(this.selectedReviewerName.toLowerCase());
  }

  // ─── Validation for Send for Approval ───
  canSendForApproval(): boolean {
    if (!this.selectedReviewerName.trim()) return false;
    if (this.deoDecision === 'NON_MAINTAINABLE') return !!this.nonMaintainableReason;
    if (this.deoDecision === 'MAINTAINABLE') {
      return this.eligibilityComplete() && !this.reFiledDateError && !this.reReplyDateError;
    }
    return false;
  }

  sendForApproval() {
    if (!this.canSendForApproval()) return;
    this.submitting.set(true);

    let assignedTo = this.selectedReviewerName;
    const matchedReviewer = this.reviewers().find(r => r.displayName === this.selectedReviewerName);
    if (matchedReviewer) {
      assignedTo = matchedReviewer.id;
    }

    const payload = {
      draftId: this.draftId,
      status: 'SENT_TO_REVIEWER',
      assignedTo,
      processedBy: this.loggedInUser?.id || 'DEO',
      complainantName: this.complainantName,
      complainantPhone: this.complainantPhone,
      complainantAddress: this.complainantAddress,
      complainantState: this.complainantState,
      complainantDistrict: this.complainantDistrict,
      complainantPincode: this.complainantPincode,
      senderEmail: this.complainantEmail,
      subject: this.subject,
      body: this.description,
      category: this.category,
      entityName: this.entityName,
      entityType: this.entityType,
      modeOfReceipt: this.modeOfReceipt,
      deoDecision: this.deoDecision,
      deoRemarks: this.deoRemarks,
      nonMaintainableReason: this.nonMaintainableReason,
      complaintReferenceNumber: this.complaintReferenceNumber,
      receivedAt: this.receivedDate ? this.receivedDate + 'T00:00:00' : new Date().toISOString(),
      ...this.eligibilityPayload(),
    };

    const isPhysicalLetter = this.draftId.startsWith('DRF-');

    if (isPhysicalLetter) {
      this.http.post<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts`, payload)
        .subscribe({
          next: () => {
            this.submitting.set(false);
            this.submitted.set(true);
            this.showConfirmDialog.set(false);
          },
          error: () => {
            this.submitting.set(false);
          }
        });
    } else {
      this.http.put<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`, payload)
        .subscribe({
          next: () => {
            this.submitting.set(false);
            this.submitted.set(true);
            this.showConfirmDialog.set(false);
          },
          error: () => {
            this.submitting.set(false);
          }
        });
    }
  }

  confirmAndSend() {
    this.sendForApproval();
  }

  enterEditMode() {
    this.editMode.set(true);
    this.activeStep.set('creation');
    this.sectionOpen.complaint = true;
    // Edit mode swaps the tabs for the stepper, so assistanceSection() names something different now.
    this.onAssistanceActivity();
  }

  onDeoDecisionChange(decision: string) {
    this.deoDecision = decision as any;
    if (decision !== 'MAINTAINABLE') this.clearEligibilityAnswers();
    if (decision !== 'NON_MAINTAINABLE') this.nonMaintainableReason = '';
  }

  setEligibilityAnswer(id: string, answer: 'YES' | 'NO') {
    this.eligibilityQuestions.update(list => list.map(q => q.id === id ? { ...q, answer } : q));
    if (!this.showReFiledDate()) {
      this.reFiledDate = '';
      this.reReplyDate = '';
    } else if (!this.showReReplyDate()) {
      this.reReplyDate = '';
    }
  }

  /** The answer that keeps the complaint maintainable. Questions with no closeOn take Yes. */
  private eligibleAnswer(q: EligibilityQuestion): 'YES' | 'NO' {
    return q.closeOn === 'YES' ? 'NO' : 'YES';
  }

  /** Answers every visible question with the value that keeps the complaint maintainable. */
  markAllAsEligible() {
    const fill = (ids: Set<string>) => this.eligibilityQuestions.update(list => list.map(q =>
      ids.has(q.id) ? { ...q, answer: this.eligibleAnswer(q) } : q
    ));
    fill(new Set(this.visibleEligibilityQuestions().map(q => q.id)));
    // Answering the gate questions "Yes" reveals dependents the pass above could not see.
    fill(new Set(this.visibleEligibilityQuestions().filter(q => q.answer === null).map(q => q.id)));
  }

  clearEligibilityAnswers() {
    this.eligibilityQuestions.update(list => list.map(q => ({ ...q, answer: null })));
    this.reFiledDate = '';
    this.reReplyDate = '';
  }

  /**
   * The RBIO and CEPC question sets are mutually exclusive, so the entity decides which
   * one the DEO is asked. Falls back to RBIO, matching the backend's routing default.
   */
  private resolveEligibilityDepartment(entityName: string) {
    if (!entityName?.trim()) {
      this.eligibilityDepartment.set('RBIO');
      return;
    }
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/resolve-by-name`, { params: { entityName } })
      .subscribe({
        next: (res) => {
          const dept = res?.data?.department;
          this.eligibilityDepartment.set(dept === 'CEPC' ? 'CEPC' : 'RBIO');
        },
        error: () => this.eligibilityDepartment.set('RBIO')
      });
  }

  goToAssignment() {
    this.activeStep.set('assignment');
    // Moving between stepper steps changes assistanceSection(); debounced like every other nudge.
    this.onAssistanceActivity();
  }

  goToCreation() {
    this.activeStep.set('creation');
    this.onAssistanceActivity();
  }

  goBack() {
    if (this.editMode() && this.activeStep() === 'assignment') {
      this.activeStep.set('creation');
      return;
    }
    if (this.editMode()) {
      this.editMode.set(false);
      return;
    }
    this.router.navigate(['/crpc/home']);
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }

  startAutoClosureScreening() {
    this.showAutoClosureEngine.set(true);
  }

  onAutoClosureCompleted(result: { responses: any[]; outcome: string; clauseReference: string; subJudice: boolean; generateComplaintNumber: boolean }) {
    this.autoClosureCompleted.set(true);
    this.autoClosureOutcome.set(result.outcome);
    this.autoClosureSubJudice.set(result.subJudice);
    this.autoClosureClauseRef.set(result.clauseReference);
    this.autoClosureResponses.set(result.responses);
    this.showAutoClosureEngine.set(false);

    // Update decision based on outcome
    if (result.outcome === 'CRPC_REJECTION' || result.outcome === 'NOT_A_COMPLAINT') {
      this.deoDecision = 'NON_MAINTAINABLE';
      this.nonMaintainableReason = result.clauseReference;
    } else {
      this.deoDecision = 'MAINTAINABLE';
    }
  }

  onAutoClosureCancelled() {
    this.showAutoClosureEngine.set(false);
  }

  amountInWords(num: number | null): string {
    if (!num || num === 0) return '';
    const ones = ['', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine',
      'ten', 'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen'];
    const tens = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety'];

    const convert = (n: number): string => {
      if (n === 0) return '';
      if (n < 20) return ones[n];
      if (n < 100) return tens[Math.floor(n / 10)] + (n % 10 ? '-' + ones[n % 10] : '');
      if (n < 1000) return ones[Math.floor(n / 100)] + ' hundred' + (n % 100 ? ' ' + convert(n % 100) : '');
      if (n < 100000) return convert(Math.floor(n / 1000)) + ' thousand' + (n % 1000 ? ' ' + convert(n % 1000) : '');
      if (n < 10000000) return convert(Math.floor(n / 100000)) + ' lakh' + (n % 100000 ? ' ' + convert(n % 100000) : '');
      return convert(Math.floor(n / 10000000)) + ' crore' + (n % 10000000 ? ' ' + convert(n % 10000000) : '');
    };

    return convert(Math.round(num)).replace(/^\w/, c => c.toUpperCase()) + ' rupees';
  }
}
