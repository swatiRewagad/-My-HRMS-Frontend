import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CrpcService } from '../../../services/crpc.service';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { ReviewerUser } from '../../../models/crpc.model';
import { environment } from '../../../../environments/environment';

interface MaintainabilityQuestion {
  id: string;
  question: string;
  answer: 'YES' | 'NO' | 'NA' | null;
  weight: number;
}

interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
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
  imports: [CommonModule, FormsModule],
  templateUrl: './draft-assessment.component.html',
  styleUrl: './draft-assessment.component.scss'
})
export class DraftAssessmentComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private crpcService = inject(CrpcService);
  private auth = inject(KeycloakAuthService);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);

  // ─── Stepper ───
  activeStep = signal<'creation' | 'assignment'>('creation');

  // ─── PDF Preview (Physical Letter) ───
  pdfPreviewUrl = signal<SafeResourceUrl | null>(null);
  pdfExpanded = signal(false);

  // ─── Suggestions (AI-extracted) ───
  suggestions = signal<{ field: string; value: string; applied: boolean }[]>([]);

  // ─── Confirmation Dialog ───
  showConfirmDialog = signal(false);

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
  historyEntries = signal<any[]>([]);
  loadingHistory = signal(false);

  // ─── Assignment Mode ───
  assignmentMode = 'Manual';

  // ─── Collapsible Sections ───
  sectionOpen = {
    complaint: true,
    eligibility: false,
    entity: true,
    complainant: false,
  };

  draftId = '';
  draftStatus = 'DRAFT';
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
  category = '';
  subCategory = '';
  entityName = '';
  entityType = 'BANK';
  subject = '';
  description = '';
  amountInvolved: number | null = null;
  transactionDate = '';
  letterDate = '';
  receivedDate = '';
  vernacular = false;
  vernacularLanguage = '';
  emailType: 'TO' | 'CC_BCC' | '' = '';
  systemSuggestion: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | 'PENDING' = 'PENDING';

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

  // ─── Maintainability Assessment ───
  maintainabilityQuestions: MaintainabilityQuestion[] = [
    { id: 'MQ1', question: 'Is the complaint against a Regulated Entity (Bank/NBFC/Payment System)?', answer: null, weight: 3 },
    { id: 'MQ2', question: 'Does the complaint fall under the grounds specified in the RBI Ombudsman Scheme?', answer: null, weight: 3 },
    { id: 'MQ3', question: 'Has the complainant first approached the RE and waited the stipulated period?', answer: null, weight: 2 },
    { id: 'MQ4', question: 'Is the complaint within the limitation period (1 year)?', answer: null, weight: 2 },
    { id: 'MQ5', question: 'Is the matter NOT sub-judice before any court/tribunal/forum?', answer: null, weight: 3 },
    { id: 'MQ6', question: 'Is the complainant identifiable (not anonymous)?', answer: null, weight: 1 },
    { id: 'MQ7', question: 'Does the complaint contain sufficient details to proceed?', answer: null, weight: 1 },
    { id: 'MQ8', question: 'Is the relief sought quantifiable or specific?', answer: null, weight: 1 },
  ];

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
  closureTag = '';
  selectedReviewer = '';
  deoRemarks = '';
  savedTemplateId = '';

  nonMaintainableReasons = [
    { value: 'TIME_BARRED', label: 'Time Barred (beyond 1 year)' },
    { value: 'SUB_JUDICE', label: 'Matter is Sub-Judice' },
    { value: 'ALREADY_SETTLED', label: 'Already Settled by Agreement' },
    { value: 'NOT_APPROACHED_RE', label: 'Complainant has not approached RE' },
    { value: 'ANONYMOUS', label: 'Anonymous Complaint' },
    { value: 'INSUFFICIENT_DETAILS', label: 'Insufficient Details' },
    { value: 'OUT_OF_JURISDICTION', label: 'Outside Jurisdiction' },
    { value: 'FRIVOLOUS', label: 'Frivolous/Vexatious' },
    { value: 'DUPLICATE', label: 'Duplicate Complaint' },
    { value: 'OTHER', label: 'Other (specify in remarks)' },
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

  maintainabilityScore = computed(() => {
    let score = 0;
    let maxScore = 0;
    for (const q of this.maintainabilityQuestions) {
      if (q.answer === 'NA') continue;
      maxScore += q.weight;
      if (q.answer === 'YES') score += q.weight;
    }
    return maxScore > 0 ? Math.round((score / maxScore) * 100) : 0;
  });

  allQuestionsAnswered = computed(() => {
    return this.maintainabilityQuestions.every(q => q.answer !== null);
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

  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  loggedInUser: { id: string; name: string; role: string } | null = null;

  // ─── Past & Similar Complaints ───
  pastComplaints = signal<any[]>([]);
  similarCases = signal<any[]>([]);
  loadingPastComplaints = signal(false);
  loadingSimilarCases = signal(false);

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
    this.crpcService.getReviewers().subscribe(data => this.reviewers.set(data));
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

  loadSimilarCases() {
    if (!this.subject) return;
    this.loadingSimilarCases.set(true);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/past-complaints/similar`, {
      subject: this.subject,
      description: this.description,
      category: this.category,
      excludeId: this.draftId
    }).subscribe({
      next: (res) => {
        this.similarCases.set(res?.data || []);
        this.loadingSimilarCases.set(false);
      },
      error: () => this.loadingSimilarCases.set(false)
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
  }

  togglePdfExpand() {
    this.pdfExpanded.set(!this.pdfExpanded());
  }

  openAttachmentsPanel() {
    this.showAttachmentsPanel.set(true);
  }

  closeAttachmentsPanel() {
    this.showAttachmentsPanel.set(false);
    this.viewingAttachment.set(null);
  }

  viewAttachment(att: Attachment) {
    this.viewingAttachment.set(att);
    this.pdfCurrentPage.set(1);
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
      this.showAttachmentsPanel.set(false);
      this.loadHistory();
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
          // Fallback mock data for demo
          this.historyEntries.set([
            { status: 'Sent to RBI', statusType: 'sent', modifiedOn: new Date().toISOString(), modifiedBy: 'RBI NO Chandigarh', assignedTo: 'Sharmila Thakur', assignedInitials: 'ST', assignedColor: '#7c3aed' },
            { status: 'Advisory Issued', statusType: 'advisory', modifiedOn: new Date(Date.now() - 86400000).toISOString(), modifiedBy: 'Lakshya Kumar Chd', assignedTo: 'Bhag Singh NO-Chd', assignedInitials: 'BS', assignedColor: '#f59e0b', description: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, deposits and withdrawals to loan payments and...' },
            { status: 'Sent to RBI', statusType: 'sent', modifiedOn: new Date(Date.now() - 172800000).toISOString(), modifiedBy: 'RBI NO Chandigarh', assignedTo: 'Sharmila Thakur', assignedInitials: 'ST', assignedColor: '#7c3aed' },
            { status: 'Information Required', statusType: 'info', modifiedOn: new Date(Date.now() - 259200000).toISOString(), modifiedBy: 'Lakshya Kumar Chd', assignedTo: 'Bhag Singh NO-Chd', assignedInitials: 'BS', assignedColor: '#f59e0b' },
            { status: 'Sent to RBI', statusType: 'sent', modifiedOn: new Date(Date.now() - 345600000).toISOString(), modifiedBy: 'Sharmila Thakur', assignedTo: 'Bhag Singh NO-Chd', assignedInitials: 'BS', assignedColor: '#f59e0b' },
          ]);
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
      this.contactPreference = 'POST';

      this.modeOfReceipt = 'PHYSICAL_LETTER';
      this.category = draft?.category || '';
      this.entityName = draft?.entityName || '';
      this.entityType = draft?.entityType || 'BANK';
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

      // Load PDF preview from sessionStorage blob if available
      const pdfBlob = sessionStorage.getItem('physicalLetterPdfUrl');
      if (pdfBlob) {
        this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(pdfBlob));
      }

      this.emailCorrespondence.set([]);
      this.loading.set(false);
      this.loadPastComplaints();
      this.loadSimilarCases();
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
            this.contactPreference = 'EMAIL';

            this.modeOfReceipt = (draft.modeOfReceipt as any) || 'EMAIL';
            this.cpgramsReference = draft.cpgramsNumber || '';
            this.category = draft.category || '';
            this.entityName = draft.entityName || '';
            this.entityType = draft.entityType || 'BANK';
            this.subject = draft.subject || draft.complaintSummary || '';
            this.description = draft.body || '';
            this.amountInvolved = draft.amountInvolved || null;
            this.transactionDate = draft.transactionDate || '';
            this.receivedDate = draft.receivedAt ? draft.receivedAt.split('T')[0] : '';
            this.emailType = 'TO';
            this.draftStatus = draft.status || 'ASSIGNED';
            this.selectedReviewer = draft.assignedTo || '';
            this.deoAssessmentDecision = draft.deoDecision || '';
            this.deoAssessmentRemarks = draft.deoRemarks || '';
            this.systemSuggestion = draft.systemSuggestion || 'PENDING';
            this.vernacular = draft.isVernacular || false;
            this.vernacularLanguage = draft.languageName || '';

            const attachments = (draft.attachments || []).map((a: any, i: number) => ({
              id: a.id || `ATT-${i + 1}`,
              name: a.fileName || `attachment_${i + 1}`,
              size: a.fileSize ? this.formatFileSize(a.fileSize) : 'Unknown',
              type: a.fileType || 'application/octet-stream',
              uploadedAt: a.createdAt || new Date().toISOString(),
              uploadedBy: 'SYSTEM',
            }));
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
            this.loadPastComplaints();
            this.loadSimilarCases();
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
    if (file.size > 10 * 1024 * 1024) {
      this.uploadError = 'File size must not exceed 10 MB.';
      return;
    }

    this.uploadError = '';
    const newAtt: Attachment = {
      id: 'ATT-' + Date.now(),
      name: file.name,
      size: (file.size / 1024).toFixed(0) + ' KB',
      type: file.type,
      uploadedAt: new Date().toISOString(),
      uploadedBy: 'DEO'
    };
    this.attachments.set([...this.attachments(), newAtt]);
    input.value = '';
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
          const newAtt: Attachment = {
            id: 'ATT-' + Date.now(),
            name: file.name,
            size: (file.size / 1024).toFixed(0) + ' KB',
            type: file.type,
            uploadedAt: new Date().toISOString(),
            uploadedBy: 'DEO'
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
    if (data['complainantPincode'] && !this.complainantPincode) this.complainantPincode = data['complainantPincode'];
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
    this.emailSubject = `Re: ${this.subject} — Additional Information Required [${this.draftId}]`;
    this.emailBody = '';
    this.showEmailComposer.set(true);
  }

  sendEmail() {
    if (!this.emailTo || !this.emailBody) return;
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

  // ─── Validation for Send for Approval ───
  canSendForApproval(): boolean {
    if (!this.selectedReviewer) return false;
    return true;
  }

  sendForApproval() {
    if (!this.canSendForApproval()) return;
    this.submitting.set(true);

    const payload = {
      draftId: this.draftId,
      status: 'SENT_TO_REVIEWER',
      assignedTo: this.selectedReviewer,
      processedBy: this.loggedInUser?.name || 'DEO',
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
      receivedAt: this.receivedDate ? this.receivedDate + 'T00:00:00' : new Date().toISOString(),
    };

    const isPhysicalLetter = this.draftId.startsWith('DRF-');

    if (isPhysicalLetter) {
      // Physical letter drafts don't exist in DB yet — create them
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
      // Email drafts already exist — update them
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

  goBack() {
    this.router.navigate(['/crpc/home']);
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }
}
