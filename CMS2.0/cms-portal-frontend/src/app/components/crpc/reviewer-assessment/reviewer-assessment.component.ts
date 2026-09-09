import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CrpcService } from '../../../services/crpc.service';
import { CrpcWorkflowService } from '../../../services/crpc-workflow.service';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
  url?: string;
}

interface HistoryEntry {
  id: string;
  timestamp: string;
  action: string;
  performedBy: string;
  role: string;
  remarks: string;
}

@Component({
  selector: 'app-reviewer-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './reviewer-assessment.component.html',
  styleUrl: './reviewer-assessment.component.scss'
})
export class ReviewerAssessmentComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private crpcService = inject(CrpcService);
  private crpcWorkflowService = inject(CrpcWorkflowService);
  private auth = inject(KeycloakAuthService);

  draftId = '';
  draftStatus = '';
  draftAssignedTo = '';

  // A reviewer could otherwise open ANY draft URL directly (or via the "All" list) and get a
  // fully editable/actionable screen regardless of whether it's even reached the review stage
  // yet or is assigned to them — this only checks completion (submitted()), not workflow stage
  // or ownership. View-only unless the draft is actually at SENT_TO_REVIEWER and assigned to
  // the current user.
  get isReadOnly(): boolean {
    if (this.submitted()) return true;
    const currentUsername = this.auth.currentUser()?.username || '';
    return this.draftStatus !== 'SENT_TO_REVIEWER' || this.draftAssignedTo !== currentUsername;
  }
  loading = signal(true);
  editMode = signal(false);
  showAiWarning = true;
  showAttachmentsPanel = signal(false);
  showHistoryPanel = signal(false);

  toggleAttachmentsPanel() {
    this.showAttachmentsPanel.set(!this.showAttachmentsPanel());
    if (this.showAttachmentsPanel()) this.showHistoryPanel.set(false);
  }

  toggleHistoryPanel() {
    this.showHistoryPanel.set(!this.showHistoryPanel());
    if (this.showHistoryPanel()) this.showAttachmentsPanel.set(false);
  }
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);

  currentTab = signal<'summary' | 'email' | 'attachments' | 'history' | 'action'>('summary');

  sectionOpen = {
    complaint: true,
    basic: true,
    eligibility: false,
    entity: false,
    complainant: false,
  };

  // ─── Draft Fields (Read-only by default; editable after click "Edit") ───
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantAddress = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';

  modeOfReceipt = '';
  cpgramsReference = '';
  category = '';
  subCategory = '';
  entityName = '';
  entitySearchText = '';
  entitySearchResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  showEntityDropdown = signal(false);
  private entitySearchTimeout: any = null;
  entityType = '';
  entityCategory = '';
  entityTypeDetail = '';
  entityBsrCode = '';
  entityPincode = '';
  entityCountry = '';
  entityState = '';
  entityDistrict = '';
  entityCity = '';
  entityBranchName = '';
  entityBranchCategory = '';
  pincodePostOffices: { Name: string; BranchType: string }[] = [];
  showBranchDropdown = false;
  entityAddress = '';
  entityBranchCenterName = '';
  cosmosCode = '';
  assetSize = '';
  isDepositTaking = '';
  isAssetAbove100Cr = '';
  isLiquidated = '';
  subject = '';
  description = '';
  amountInvolved: number | null = null;
  transactionDate = '';
  receivedDate = '';
  vernacular = false;
  vernacularLanguage = '';
  emailType = '';
  systemSuggestion = '';

  // Basic Identification
  otherEntityName = '';
  dateOfRegistrationWithRBI = '';

  // Complaint Classification
  complaintCategory = '';
  complaintSubCategory1 = '';
  complaintSubCategory2 = '';
  dateOfFilingComplaint = '';
  complaintRegDateValid = '';

  // Reminder & Financial Details
  reminderSentByComplainant = '';
  disputedAmountInvolved: number | null = null;
  dateOfFilingForFinancial = '';
  compensationSought = '';
  loanDisposalAmount: number | null = null;

  // Additional Information
  additionalComments = '';
  crpcProposedAction = '';

  // Legal & Case Details
  legalCaseFiled = '';
  legalDateOfFiling = '';
  preEnquiryReceived = '';
  highPriorityComplaint = '';

  // Flags & Indicators
  isRegardingPension = '';
  isAgainstBusinessCorrespondent = '';
  isAtmCreditDebitCard = '';
  schemeFlag = '';

  // Complaint Linkage
  isFreeMarkedComplaint = '';
  currentComplaintNumber = '';
  receivedReplyWithin30Days = '';

  // Eligibility
  proposedComplaintType = '';
  notComplaintReason = '';
  markAllEligible = false;
  eligibilityQuestions: { key: string; label: string; answer: string }[] = [
    { key: 'isEntityRegulated', label: 'Is Entity regulated by RBI?', answer: '' },
    { key: 'notAddressedToOmbudsman', label: 'The Complaint not directly addressed to Ombudsman', answer: '' },
    { key: 'notRegisteredWithEntity', label: 'Is the Complaint not registered with Entity (FRC)?', answer: '' },
    { key: 'isFrivolous', label: 'Is the complainant frivolous, vexatious, and threatening?', answer: '' },
    { key: 'isSubJudice', label: 'Is the Complaint Sub-Judice or under arbitration?', answer: '' },
    { key: 'isAdvocate', label: 'Is the complainant an advocate?', answer: '' },
    { key: 'alreadyDealt', label: 'Has already been dealt with or is under process on the same ground with the ombudsman?', answer: '' },
    { key: 'againstManagement', label: 'Does the complaint involve general complaints against management or executives of a RE?', answer: '' },
    { key: 'disputeBetweenREs', label: 'Does it involve disputes between REs?', answer: '' },
    { key: 'staffEmployer', label: 'Is from staff of an RE and involves employer-employee relationship?', answer: '' },
  ];

  // DEO Assessment data (read-only for reviewer reference)
  deoDecision = '';
  deoRemarks = '';
  deoNonMaintainableReason = '';
  deoClosureTag = '';
  maintainabilityScore = 0;

  // ─── Attachments ───
  attachments = signal<Attachment[]>([]);

  // ─── Email Communication ───
  emailThread = signal<any[]>([]);

  // ─── History / Audit Trail ───
  history = signal<HistoryEntry[]>([]);

  // ─── Reviewer Actions ───
  reviewerDecision: 'APPROVE' | 'SENT_BACK_TO_DEO' | 'NOT_A_COMPLAINT' | 'APPROVE_SENT_TO_OTHER_DEPT' | 'APPROVE_VERNACULAR' | '' = '';
  reviewerRemarks = '';
  savedTemplateId = '';

  // Sent back to DEO
  sentBackToDeoId = '';
  sentBackAssignmentMode: 'AUTOMATIC' | 'MANUAL' = 'AUTOMATIC';
  originalDeoId = '';
  activeDeos = signal<{ id: string; name: string; active: boolean }[]>([]);

  // Not a Complaint disposition
  notComplaintDisposition: 'CLOSED' | 'SENT_TO_OTHER_DEPARTMENT' | 'SUGGESTION' | '' = '';
  targetDepartment = '';
  targetOffice = '';

  // Sent to Other Department/Entity (Reviewer approval)
  targetOtherEntity = '';

  // Vernacular routing
  vernacularTargetOffice = '';
  vernacularLanguageOfficeMap: Record<string, string> = {};

  // Draft type (from DEO decision)
  draftType: 'NEW_COMPLAINT' | 'NOT_A_COMPLAINT' | 'SENT_TO_OTHER_DEPT' | 'VERNACULAR' | '' = '';

  // Routing for approved complaints (auto-determined by entity rules, reviewer can override)
  targetRegionalOffice = '';
  autoRoutedOffice = '';
  routingReason = '';
  regionalOffices = [
    { id: 'RBIO-MUM', name: 'RBIO Mumbai', dept: 'RBIO' },
    { id: 'RBIO-DEL', name: 'RBIO Delhi', dept: 'RBIO' },
    { id: 'RBIO-CHE', name: 'RBIO Chennai', dept: 'RBIO' },
    { id: 'RBIO-KOL', name: 'RBIO Kolkata', dept: 'RBIO' },
    { id: 'CEPC', name: 'CEPC (Central)', dept: 'CEPC' },
  ];

  commentTemplates = [
    { id: 'RT1', label: 'Approved - Standard', text: 'Reviewed and approved. All fields verified. Complaint number to be generated.' },
    { id: 'RT2', label: 'Approved - With Edits', text: 'Reviewed and approved with minor corrections to entity/category fields. Complaint progressed.' },
    { id: 'RT3', label: 'Sent Back - Incomplete Fields', text: 'Returning to DEO. Mandatory fields incomplete — please verify state, district and contact details.' },
    { id: 'RT4', label: 'Sent Back - Screening Incomplete', text: 'Auto-closure screening not fully answered. Please complete all screening questions before resubmitting.' },
    { id: 'RT5', label: 'Not a Complaint - Closed', text: 'Finalized as Not a Complaint. No complaint number generated. Record closed.' },
    { id: 'RT6', label: 'Suggestion - Sent to Other Dept', text: 'Closed as Suggestion. Communications sent. Status: Sent to Other Department.' },
  ];

  showConfirmDialog = signal(false);
  submitting = signal(false);
  submitted = signal(false);
  generatedComplaintNumber = signal('');
  assignedToUser = signal('');

  getStatusLabel(status: string): string {
    switch (status) {
      case 'SENT_TO_REVIEWER': return 'Sent to Reviewer';
      case 'SENT_TO_OTHER_DEPT_FOR_APPROVAL': return 'Other Dept - Pending Approval';
      case 'VERNACULAR_FOR_APPROVAL': return 'Vernacular - Pending Approval';
      case 'APPROVED': case 'APPROVED_ROUTED': return 'Approved';
      case 'APPROVED_SENT_TO_OTHER_DEPT': return 'Sent to Other Entity';
      case 'APPROVED_VERNACULAR': return 'Sent to Language Office';
      case 'SENT_BACK_TO_DEO': case 'SENT_BACK': return 'Sent Back';
      case 'CLOSED_NM': case 'CLOSED_NOT_A_COMPLAINT': return 'Closed (NM)';
      default: return status;
    }
  }

  categories = ['ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'];
  statesList = signal<string[]>([]);
  districts = signal<string[]>([]);

  private statesFallback = [
    'Andhra Pradesh', 'Arunachal Pradesh', 'Assam', 'Bihar', 'Chhattisgarh', 'Goa', 'Gujarat',
    'Haryana', 'Himachal Pradesh', 'Jharkhand', 'Karnataka', 'Kerala', 'Madhya Pradesh',
    'Maharashtra', 'Manipur', 'Meghalaya', 'Mizoram', 'Nagaland', 'Odisha', 'Punjab',
    'Rajasthan', 'Sikkim', 'Tamil Nadu', 'Telangana', 'Tripura', 'Uttar Pradesh',
    'Uttarakhand', 'West Bengal', 'Andaman and Nicobar Islands', 'Chandigarh',
    'Dadra and Nagar Haveli and Daman and Diu', 'Delhi', 'Jammu and Kashmir', 'Ladakh',
    'Lakshadweep', 'Puducherry'
  ];

  ngOnInit() {
    // Angular reuses this same component instance when navigating between two URLs that match
    // the same route (crpc/reviewer/draft/:id) with only :id differing — e.g. clicking one
    // complaint in the queue after another. A one-time snapshot read here means ngOnInit never
    // re-fires for that second navigation, so draftId and flow-state signals like submitted()
    // (left `true` after approving a previous draft) stay stuck, showing the wrong draft's
    // stale "Complaint Approved" screen. Subscribe to paramMap instead so every navigation to
    // this route — reused instance or not — reloads fresh and resets state.
    this.route.paramMap.subscribe(params => {
      this.draftId = params.get('id') || '';
      this.submitted.set(false);
      this.editMode.set(false);
      this.draftStatus = '';
      this.draftAssignedTo = '';
      this.loadDraft();
    });
    this.loadDeos();
    this.loadStates();
    this.crpcWorkflowService.getVernacularOfficeMap().subscribe(map => {
      this.vernacularLanguageOfficeMap = map;
    });
  }

  private loadDeos() {
    this.crpcService.getDeos().subscribe(deos => {
      this.activeDeos.set(deos.map((d: any) => ({
        id: d.userId,
        name: d.displayName,
        active: d.isActive ?? true,
      })));
    });
  }

  private loadStates() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => {
        const data = res?.data || [];
        this.statesList.set(data.length > 0 ? data : this.statesFallback);
      },
      error: () => this.statesList.set(this.statesFallback)
    });
  }

  downloadAttachment(att: { id: string; name: string; url?: string }) {
    if (!att.url) return;
    this.http.get(att.url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = att.name;
        a.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => window.open(att.url, '_blank')
    });
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

  onMarkAllEligible() {
    if (this.markAllEligible) {
      for (const q of this.eligibilityQuestions) {
        q.answer = q.key === 'isEntityRegulated' ? 'YES' : 'NO';
      }
    } else {
      for (const q of this.eligibilityQuestions) {
        q.answer = '';
      }
    }
  }

  onEntitySearchInput(value: string) {
    this.entityName = value;
    this.entitySearchText = value;
    if (this.entitySearchTimeout) clearTimeout(this.entitySearchTimeout);
    if (!value || value.length < 2) {
      this.showEntityDropdown.set(false);
      return;
    }
    this.entitySearchTimeout = setTimeout(() => {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, {
        params: { search: value }
      }).subscribe({
        next: (res) => {
          this.entitySearchResults.set(res?.data || []);
          this.showEntityDropdown.set(true);
        },
        error: () => this.entitySearchResults.set([])
      });
    }, 300);
  }

  selectEntity(entity: { id: number; name: string; department: string; entityType: string }) {
    this.entityName = entity.name;
    this.entitySearchText = entity.name;
    this.entityType = entity.entityType || this.entityType;
    this.showEntityDropdown.set(false);
    this.computeAutoRouting();
  }

  onEntityBlur() {
    setTimeout(() => this.showEntityDropdown.set(false), 200);
  }

  onStateChange(state: string) {
    this.complainantState = state;
    this.complainantDistrict = '';
    this.districts.set([]);
    if (state) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state } }).subscribe({
        next: (res) => this.districts.set(res?.data || []),
        error: () => this.districts.set([])
      });
    }
    this.computeAutoRouting();
  }

  loadDraft() {
    this.loading.set(true);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`)
      .subscribe({
        next: (res) => {
          const draft = res?.data || {};
          this.draftStatus = draft.status || '';
          this.draftAssignedTo = draft.assignedTo || '';
          this.complainantName = draft.complainantName || '';
          this.complainantPhone = draft.complainantPhone || '';
          this.complainantEmail = draft.senderEmail || '';
          this.loadPastComplaints();
          this.complainantAddress = draft.complainantAddress || '';
          this.complainantState = draft.complainantState || '';
          this.complainantDistrict = draft.complainantDistrict || '';
          this.complainantPincode = draft.complainantPincode || '';

          this.modeOfReceipt = draft.modeOfReceipt || 'EMAIL';
          this.category = draft.category || '';
          this.entityName = draft.entityName || '';
          this.entityType = draft.entityType || 'BANK';
          this.subject = draft.subject || draft.complaintSummary || '';
          this.description = draft.body || '';
          this.amountInvolved = draft.amountInvolved || null;
          this.transactionDate = draft.transactionDate || '';
          this.receivedDate = draft.receivedAt ? draft.receivedAt.split('T')[0] : '';
          this.emailType = 'TO';
          this.systemSuggestion = draft.systemSuggestion || 'MAINTAINABLE';
          this.vernacular = draft.isVernacular || false;
          this.vernacularLanguage = draft.languageName || '';

          this.deoDecision = draft.deoDecision || '';
          this.deoRemarks = draft.deoRemarks || '';
          this.deoNonMaintainableReason = draft.nonMaintainableReason || '';
          this.maintainabilityScore = draft.maintainabilityScore || 0;
          this.originalDeoId = draft.processedBy || '';
          this.sentBackToDeoId = this.originalDeoId;

          // Eligibility
          this.proposedComplaintType = draft.proposedComplaintType || (draft.deoDecision === 'NON_MAINTAINABLE' ? 'NOT_A_COMPLAINT' : 'NEW_COMPLAINT');
          this.notComplaintReason = draft.notComplaintReason || draft.nonMaintainableReason || '';
          let eqs: any = null;
          if (draft.eligibilityQuestionsJson) {
            try { eqs = JSON.parse(draft.eligibilityQuestionsJson); } catch (e) {}
          }
          if (!eqs) {
            const stored = sessionStorage.getItem(`draft_eligibility_${this.draftId}`);
            if (stored) {
              const parsed = JSON.parse(stored);
              this.proposedComplaintType = parsed.proposedComplaintType || this.proposedComplaintType;
              this.notComplaintReason = parsed.notComplaintReason || this.notComplaintReason;
              eqs = parsed.eligibilityQuestions;
            }
          }
          if (eqs) {
            this.eligibilityQuestions.forEach(q => {
              if (eqs[q.key]) q.answer = eqs[q.key];
            });
          }

          // Extended fields
          this.entityCategory = draft.entityCategory || '';
          this.entityTypeDetail = draft.entityTypeDetail || '';
          this.entityBsrCode = draft.entityBsrCode || '';
          this.entityPincode = draft.entityPincode || '';
          this.entityCountry = draft.entityCountry || '';
          this.entityState = draft.entityState || '';
          this.entityDistrict = draft.entityDistrict || '';
          this.entityCity = draft.entityCity || '';
          this.entityBranchName = draft.entityBranchName || '';
          this.entityBranchCategory = draft.entityBranchCategory || '';
          this.entityAddress = draft.entityAddress || '';
          this.entityBranchCenterName = draft.entityBranchCenterName || '';
          this.cosmosCode = draft.cosmosCode || '';
          this.assetSize = draft.assetSize || '';
          this.isDepositTaking = draft.isDepositTaking === true ? 'YES' : draft.isDepositTaking === false ? 'NO' : (draft.isDepositTaking || '');
          this.isAssetAbove100Cr = draft.isAssetAbove100Cr === true ? 'YES' : draft.isAssetAbove100Cr === false ? 'NO' : (draft.isAssetAbove100Cr || '');
          this.isLiquidated = draft.isLiquidated === true ? 'YES' : draft.isLiquidated === false ? 'NO' : (draft.isLiquidated || '');
          this.otherEntityName = draft.otherEntityName || '';
          this.dateOfRegistrationWithRBI = draft.dateOfRegistrationWithRBI || '';
          this.complaintCategory = draft.complaintCategory || draft.category || '';
          this.complaintSubCategory1 = draft.complaintSubCategory1 || '';
          this.complaintSubCategory2 = draft.complaintSubCategory2 || '';
          this.dateOfFilingComplaint = draft.dateOfFilingComplaint || '';
          this.complaintRegDateValid = draft.complaintRegDateValid || '';
          this.reminderSentByComplainant = draft.reminderSentByComplainant || '';
          this.disputedAmountInvolved = draft.disputedAmountInvolved || draft.amountInvolved || null;
          this.dateOfFilingForFinancial = draft.dateOfFilingForFinancial || '';
          this.compensationSought = draft.compensationSought || '';
          this.loanDisposalAmount = draft.loanDisposalAmount || null;
          this.additionalComments = draft.additionalComments || '';
          this.crpcProposedAction = draft.crpcProposedAction || 'Maintainable';
          this.legalCaseFiled = draft.legalCaseFiled || '';
          this.legalDateOfFiling = draft.legalDateOfFiling || '';
          this.preEnquiryReceived = draft.preEnquiryReceived || '';
          this.highPriorityComplaint = draft.highPriorityComplaint || '';
          this.isRegardingPension = draft.isRegardingPension || '';
          this.isAgainstBusinessCorrespondent = draft.isAgainstBusinessCorrespondent || '';
          this.isAtmCreditDebitCard = draft.isAtmCreditDebitCard || '';
          this.schemeFlag = draft.schemeFlag || '';
          this.isFreeMarkedComplaint = draft.isFreeMarkedComplaint || '';
          this.currentComplaintNumber = draft.currentComplaintNumber || '';
          this.receivedReplyWithin30Days = draft.receivedReplyWithin30Days || '';

          const attachments = (draft.attachments || []).map((a: any, i: number) => {
            const attId = a.id || `ATT-${i + 1}`;
            const numericId = String(attId).replace('ATT-', '');
            return {
              id: attId,
              name: a.fileName || `attachment_${i + 1}`,
              size: a.fileSize ? this.formatSize(a.fileSize) : 'Unknown',
              type: a.fileType || 'application/octet-stream',
              uploadedAt: a.createdAt || new Date().toISOString(),
              uploadedBy: 'SYSTEM',
              url: `${environment.apiBaseUrl}/api/files/email-draft/${numericId}`,
            };
          });
          this.attachments.set(attachments);

          this.emailThread.set([{
            id: 'E1',
            direction: 'RECEIVED',
            from: draft.senderEmail || '',
            to: 'crpc@rbi.org.in',
            subject: draft.subject || '',
            sentAt: draft.receivedAt || '',
            body: draft.body || '',
          }]);

          this.history.set([
            { id: 'H1', timestamp: draft.receivedAt || '', action: 'DRAFT_CREATED', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: 'Email ingested and draft created automatically.' },
            { id: 'H2', timestamp: draft.createdAt || '', action: 'ASSIGNED_TO_DEO', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: `Assigned to ${draft.assignedTo || 'DEO'} via Round Robin.` },
            { id: 'H3', timestamp: draft.createdAt || '', action: 'SENT_TO_REVIEWER', performedBy: draft.assignedTo || 'DEO', role: 'DEO', remarks: `Routed to reviewer via Round Robin.` },
          ]);

          // Determine draft type from DEO decision / status
          if (draft.deoDecision === 'SENT_TO_OTHER_DEPT' || draft.status === 'SENT_TO_OTHER_DEPT_FOR_APPROVAL') {
            this.draftType = 'SENT_TO_OTHER_DEPT';
            this.targetOtherEntity = draft.targetEntity || '';
          } else if (draft.deoDecision === 'VERNACULAR' || draft.status === 'VERNACULAR_FOR_APPROVAL') {
            this.draftType = 'VERNACULAR';
            this.vernacularTargetOffice = this.vernacularLanguageOfficeMap[(draft.detectedLanguage || '').toUpperCase()] || '';
          } else if (draft.deoDecision === 'NON_MAINTAINABLE' || draft.deoDecision === 'NOT_A_COMPLAINT') {
            this.draftType = 'NOT_A_COMPLAINT';
          } else {
            this.draftType = 'NEW_COMPLAINT';
          }

          if (draft.status === 'SENT_BACK_TO_DEO') {
            this.reviewerDecision = 'SENT_BACK_TO_DEO';
            this.sentBackToDeoId = draft.processedBy || '';
            this.submitted.set(true);
          } else if (draft.status === 'CLOSED_NOT_A_COMPLAINT') {
            this.reviewerDecision = 'NOT_A_COMPLAINT';
            this.submitted.set(true);
          } else if (draft.status === 'APPROVED_SENT_TO_OTHER_DEPT') {
            // Must be checked before the generic convertedComplaintId branch below — a complaint
            // approved AND routed to another department still has a real convertedComplaintId,
            // so that broader check was matching first and showing the plain "Complaint
            // Approved" screen instead of the correct "Sent to Other Department" one.
            this.reviewerDecision = 'APPROVE_SENT_TO_OTHER_DEPT';
            this.generatedComplaintNumber.set(draft.convertedComplaintId || '');
            this.submitted.set(true);
          } else if (draft.status === 'APPROVED_VERNACULAR') {
            this.reviewerDecision = 'APPROVE_VERNACULAR';
            this.generatedComplaintNumber.set(draft.convertedComplaintId || '');
            this.submitted.set(true);
          } else if (draft.convertedComplaintId) {
            this.generatedComplaintNumber.set(draft.convertedComplaintId);
            this.reviewerDecision = 'APPROVE';
            this.targetRegionalOffice = draft.targetOffice || 'CEPC';
            this.assignedToUser.set(draft.assignedTo || '');
            this.submitted.set(true);
          }

          if (this.complainantState) {
            this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/districts`, { params: { state: this.complainantState } }).subscribe({
              next: (res) => this.districts.set(res?.data || []),
              error: () => this.districts.set([])
            });
          }

          this.computeAutoRouting();
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }

  private computeAutoRouting() {
    const entityName = this.entityName || '';

    if (!entityName.trim()) {
      const office = this.resolveRbioOffice(this.complainantState);
      this.autoRoutedOffice = office;
      this.routingReason = 'No entity name — routed by complainant state to ' + this.getOfficeLabel(office);
      this.targetRegionalOffice = this.autoRoutedOffice;
      return;
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/resolve-by-name`, {
      params: { entityName }
    }).subscribe({
      next: (res) => {
        const data = res?.data || {};
        const dept = data.department || 'RBIO';
        const matchType = data.matchType || 'NOT_FOUND';
        const matchedName = data.matchedEntityName || '';
        const reason = data.reason || '';

        if (dept === 'CEPC') {
          this.autoRoutedOffice = 'CEPC';
          this.routingReason = `${reason}${matchedName ? ' (' + matchedName + ')' : ''}`;
        } else {
          const office = this.resolveRbioOffice(this.complainantState);
          this.autoRoutedOffice = office;
          this.routingReason = `${reason} | ${this.getOfficeLabel(office)}`;
        }

        this.targetRegionalOffice = this.autoRoutedOffice;
      },
      error: () => {
        const office = this.resolveRbioOffice(this.complainantState);
        this.autoRoutedOffice = office;
        this.routingReason = 'Routed by complainant state to ' + this.getOfficeLabel(office);
        this.targetRegionalOffice = this.autoRoutedOffice;
      }
    });
  }

  private resolveRbioOffice(state: string): string {
    const s = (state || '').toUpperCase().trim();
    const westStates = ['MH', 'GJ', 'GA', 'MP', 'CT', 'MAHARASHTRA', 'GUJARAT', 'GOA', 'MADHYA PRADESH', 'CHHATTISGARH'];
    const northStates = ['DL', 'HR', 'PB', 'UP', 'UK', 'HP', 'JK', 'LA', 'RJ', 'CH',
      'DELHI', 'NEW DELHI', 'HARYANA', 'PUNJAB', 'UTTAR PRADESH', 'UTTARAKHAND', 'HIMACHAL PRADESH',
      'JAMMU AND KASHMIR', 'JAMMU & KASHMIR', 'LADAKH', 'RAJASTHAN', 'CHANDIGARH'];
    const southStates = ['TN', 'KA', 'KL', 'AP', 'TS', 'PY',
      'TAMIL NADU', 'KARNATAKA', 'KERALA', 'ANDHRA PRADESH', 'TELANGANA', 'PUDUCHERRY', 'PONDICHERRY'];
    const eastStates = ['WB', 'BR', 'JH', 'OD', 'AS', 'NL', 'MN', 'MZ', 'TR', 'ML', 'AR', 'SK', 'AN',
      'WEST BENGAL', 'BIHAR', 'JHARKHAND', 'ODISHA', 'ORISSA', 'ASSAM', 'NAGALAND', 'MANIPUR',
      'MIZORAM', 'TRIPURA', 'MEGHALAYA', 'ARUNACHAL PRADESH', 'SIKKIM', 'ANDAMAN AND NICOBAR'];

    if (westStates.includes(s)) return 'RBIO-MUM';
    if (northStates.includes(s)) return 'RBIO-DEL';
    if (southStates.includes(s)) return 'RBIO-CHE';
    if (eastStates.includes(s)) return 'RBIO-KOL';
    return 'RBIO-MUM';
  }

  private getOfficeLabel(officeId: string): string {
    const office = this.regionalOffices.find(o => o.id === officeId);
    return office ? office.name : officeId;
  }

  onEntityPincodeInput(value: string) {
    this.entityPincode = value;
    this.pincodePostOffices = [];
    this.showBranchDropdown = false;
    if (!value || value.length !== 6 || !/^\d{6}$/.test(value)) return;

    // If the entity is one of the banks we hold real IFSC-sourced branch data for, use that —
    // /location/pincode below is generic postal-circle data (post office names), not actual bank
    // branches, so it was showing the wrong thing (e.g. "Kalbadevi Post Office") as "branch name"
    // for every complaint regardless of whether the entity was even a bank.
    if (this.entityName?.trim()) {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/bank-branches`, {
        params: { entityName: this.entityName.trim(), pincode: value }
      }).subscribe({
        next: (res) => {
          if (res?.matchedBank && res.data?.length) {
            this.applyRealBankBranches(res.data);
          } else {
            this.fallbackToPostOfficeLookup(value);
          }
        },
        error: () => this.fallbackToPostOfficeLookup(value)
      });
    } else {
      this.fallbackToPostOfficeLookup(value);
    }
  }

  private applyRealBankBranches(branches: { ifsc: string; branchName: string; city: string; district: string; state: string }[]) {
    const first = branches[0];
    if (first.state) this.entityState = first.state;
    if (first.district) this.entityDistrict = first.district;
    this.entityCity = first.city || '';
    this.entityCountry = 'India';
    if (branches.length === 1) {
      this.entityBranchName = first.branchName;
      this.entityBranchCategory = first.ifsc;
    } else {
      this.pincodePostOffices = branches.map(b => ({ Name: b.branchName, BranchType: b.ifsc }));
      this.showBranchDropdown = true;
      this.entityBranchName = '';
    }
  }

  private fallbackToPostOfficeLookup(value: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
      next: (res: any) => {
        if (res?.[0]?.Status === 'Success' && res[0].PostOffice?.length) {
          const offices = res[0].PostOffice;
          const po = offices[0];
          if (po.State) this.entityState = po.State;
          if (po.District) this.entityDistrict = po.District;
          this.entityCity = po.Region || po.Division || '';
          this.entityCountry = 'India';
          if (offices.length === 1) {
            this.entityBranchName = po.Name;
            this.entityBranchCategory = po.BranchType || '';
          } else {
            this.pincodePostOffices = offices.map((o: any) => ({ Name: o.Name, BranchType: o.BranchType || '' }));
            this.showBranchDropdown = true;
            this.entityBranchName = '';
          }
        }
      },
      error: () => {}
    });
  }

  selectBranch(branch: { Name: string; BranchType: string }) {
    this.entityBranchName = branch.Name;
    this.entityBranchCategory = branch.BranchType;
    this.showBranchDropdown = false;
  }

  private formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  }

  openConfirmDialog(decision: 'APPROVE' | 'SENT_BACK_TO_DEO' | 'NOT_A_COMPLAINT' | 'APPROVE_SENT_TO_OTHER_DEPT' | 'APPROVE_VERNACULAR') {
    this.reviewerDecision = decision;
    if (decision === 'APPROVE_VERNACULAR' && !this.vernacularTargetOffice) {
      const lang = (this.vernacularLanguage || '').toUpperCase();
      this.vernacularTargetOffice = this.vernacularLanguageOfficeMap[lang] || 'CRPC-DEL';
    }
    if (decision === 'SENT_BACK_TO_DEO') {
      this.sentBackAssignmentMode = 'AUTOMATIC';
      this.sentBackToDeoId = this.originalDeoId;
    }
    this.showConfirmDialog.set(true);
  }

  toggleEditMode() {
    this.editMode.set(!this.editMode());
  }

  saveDraft() {
    const eqMap: any = {};
    this.eligibilityQuestions.forEach(q => { if (q.answer) eqMap[q.key] = q.answer; });

    const payload: any = {
      complainantName: this.complainantName,
      complainantPhone: this.complainantPhone,
      senderEmail: this.complainantEmail,
      complainantAddress: this.complainantAddress,
      complainantState: this.complainantState,
      complainantDistrict: this.complainantDistrict,
      complainantPincode: this.complainantPincode,
      category: this.category,
      entityName: this.entityName,
      entityType: this.entityType,
      entityCategory: this.entityCategory,
      entityTypeDetail: this.entityTypeDetail,
      entityBsrCode: this.entityBsrCode,
      entityPincode: this.entityPincode,
      entityCountry: this.entityCountry,
      entityState: this.entityState,
      entityDistrict: this.entityDistrict,
      entityCity: this.entityCity,
      entityBranchName: this.entityBranchName,
      entityBranchCategory: this.entityBranchCategory,
      entityAddress: this.entityAddress,
      entityBranchCenterName: this.entityBranchCenterName,
      cosmosCode: this.cosmosCode,
      assetSize: this.assetSize,
      isDepositTaking: this.isDepositTaking === 'YES' ? true : this.isDepositTaking === 'NO' ? false : null,
      isAssetAbove100Cr: this.isAssetAbove100Cr === 'YES' ? true : this.isAssetAbove100Cr === 'NO' ? false : null,
      isLiquidated: this.isLiquidated === 'YES' ? true : this.isLiquidated === 'NO' ? false : null,
      subject: this.subject,
      body: this.description,
      deoDecision: this.deoDecision,
      nonMaintainableReason: this.deoNonMaintainableReason,
      proposedComplaintType: this.proposedComplaintType,
      notComplaintReason: this.notComplaintReason,
      eligibilityQuestionsJson: JSON.stringify(eqMap),
      otherEntityName: this.otherEntityName,
      dateOfRegistrationWithRBI: this.dateOfRegistrationWithRBI,
      complaintCategory: this.complaintCategory,
      complaintSubCategory1: this.complaintSubCategory1,
      complaintSubCategory2: this.complaintSubCategory2,
      dateOfFilingComplaint: this.dateOfFilingComplaint,
      complaintRegDateValid: this.complaintRegDateValid,
      reminderSentByComplainant: this.reminderSentByComplainant,
      disputedAmountInvolved: this.disputedAmountInvolved ? String(this.disputedAmountInvolved) : '',
      dateOfFilingForFinancial: this.dateOfFilingForFinancial,
      compensationSought: this.compensationSought,
      loanDisposalAmount: this.loanDisposalAmount ? String(this.loanDisposalAmount) : '',
      additionalComments: this.additionalComments,
      crpcProposedAction: this.crpcProposedAction,
      legalCaseFiled: this.legalCaseFiled,
      legalDateOfFiling: this.legalDateOfFiling,
      preEnquiryReceived: this.preEnquiryReceived,
      highPriorityComplaint: this.highPriorityComplaint,
      isRegardingPension: this.isRegardingPension,
      isAgainstBusinessCorrespondent: this.isAgainstBusinessCorrespondent,
      isAtmCreditDebitCard: this.isAtmCreditDebitCard,
      schemeFlag: this.schemeFlag,
      isFreeMarkedComplaint: this.isFreeMarkedComplaint,
      currentComplaintNumber: this.currentComplaintNumber,
      receivedReplyWithin30Days: this.receivedReplyWithin30Days,
    };

    this.http.put(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`, payload)
      .subscribe({
        next: () => {
          this.editMode.set(false);
          this.computeAutoRouting();
        }
      });
  }

  applyTemplate(templateId: string) {
    const tmpl = this.commentTemplates.find(t => t.id === templateId);
    if (tmpl) {
      this.reviewerRemarks = tmpl.text;
      this.savedTemplateId = templateId;
    }
  }

  canSubmit(): boolean {
    if (!this.reviewerDecision) return false;
    if (!this.reviewerRemarks.trim()) return false;
    if (this.reviewerDecision === 'SENT_BACK_TO_DEO' && this.sentBackAssignmentMode === 'MANUAL' && !this.sentBackToDeoId) return false;
    if (this.reviewerDecision === 'NOT_A_COMPLAINT' && !this.notComplaintDisposition) return false;
    if (this.reviewerDecision === 'APPROVE_SENT_TO_OTHER_DEPT' && !this.targetOtherEntity.trim()) return false;
    if (this.reviewerDecision === 'APPROVE_VERNACULAR' && !this.vernacularTargetOffice.trim()) return false;
    return true;
  }

  submitDecision() {
    if (!this.reviewerDecision) return;
    this.submitting.set(true);

    let newStatus = '';
    if (this.reviewerDecision === 'APPROVE') newStatus = 'APPROVED_ROUTED';
    else if (this.reviewerDecision === 'SENT_BACK_TO_DEO') newStatus = 'SENT_BACK_TO_DEO';
    else if (this.reviewerDecision === 'NOT_A_COMPLAINT') newStatus = 'CLOSED_NOT_A_COMPLAINT';
    else if (this.reviewerDecision === 'APPROVE_SENT_TO_OTHER_DEPT') newStatus = 'APPROVED_SENT_TO_OTHER_DEPT';
    else if (this.reviewerDecision === 'APPROVE_VERNACULAR') newStatus = 'APPROVED_VERNACULAR';

    const payload: any = {
      status: newStatus,
      reviewerDecision: this.reviewerDecision,
      reviewerRemarks: this.reviewerRemarks,
      targetOffice: this.targetRegionalOffice,
      assignedTo: this.reviewerDecision === 'SENT_BACK_TO_DEO' ? this.sentBackToDeoId : this.targetRegionalOffice,
    };

    if (this.reviewerDecision === 'APPROVE_SENT_TO_OTHER_DEPT') {
      payload.targetEntity = this.targetOtherEntity;
      payload.assignedTo = this.targetOtherEntity;
    }
    if (this.reviewerDecision === 'APPROVE_VERNACULAR') {
      payload.targetOffice = this.vernacularTargetOffice;
      payload.assignedTo = this.vernacularTargetOffice;
    }

    this.http.put<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`, payload).subscribe({
      next: (res) => {
        if (this.reviewerDecision === 'APPROVE') {
          const complaintId = res?.data?.convertedComplaintId;
          const assignedTo = res?.data?.assignedTo;
          if (complaintId) {
            this.generatedComplaintNumber.set(complaintId);
          } else {
            const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
            const rand = Math.floor(100000 + Math.random() * 900000);
            this.generatedComplaintNumber.set(`CMP-${dateStr}-${rand}`);
          }
          this.assignedToUser.set(assignedTo || this.targetRegionalOffice);
        }
        this.submitting.set(false);
        this.showConfirmDialog.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.submitting.set(false);
      }
    });
  }

  goBack() {
    this.router.navigate(['/crpc/reviewer']);
  }
}
