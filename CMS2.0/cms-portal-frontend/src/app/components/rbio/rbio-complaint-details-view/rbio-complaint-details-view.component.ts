import { Component, OnInit, inject, signal, computed, WritableSignal, effect, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NavigationService } from '../../../services/navigation.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { RbioHeaderComponent } from '../rbio-header/rbio-header.component';
// import { RbioHeader } from '../rbio-header/rbio-header';



interface EligibilityQuestionItem {
  key: string;
  label: string;
  type: 'radio' | 'date';
  answer: boolean | null;
  dateValue: string | null;
}

// eligibilityQuestions[].key -> the key the `eligibility` section of /api/complaints/rbio/{id}/summary
// uses. The two vocabularies differ, and a key missing from this map would be silently dropped on save.
const ELIGIBILITY_ANSWER_KEYS: Record<string, string> = {
  entityRegulatedByRbi: 'entityRegulatedByRbi',
  notDirectlyAddressed: 'complaintNotDirectlyAddressedToOmbudsman',
  notRegisteredWithEntity: 'complaintNotRegisteredWithEntity',
  frivolousVexatious: 'frivolousVexatiousThreatening',
  subJudice: 'subJudiceOrArbitration',
  subJudicePending: 'sameGrievancePendingBeforeCourt',
  alreadySettled: 'sameGrievanceSettledBeforeCourt',
  throughAdvocate: 'complaintMadeThroughAdvocate',
  isAdvocate: 'complainantIsAdvocate',
  pendingBeforeOmbudsman: 'sameGrievancePendingBeforeOmbudsman',
  alreadyDealt: 'alreadyDealtWithByOmbudsman',
  generalAgainstManagement: 'complaintAgainstManagement',
  previouslyFiledWithCEPC: 'complaintFiledWithCEPCOrRBI',
  disputesBetweenREs: 'disputeBetweenREs',
  staffEmployerRelationship: 'staffOfREEmployerRelationship',
  incompleteInformation: 'completeInformationUnavailable',
  filedWrittenComplaint: 'writtenComplaintFiledWithRE',
  complaintFiledDate: 'firstFiledWithREDate',
  receivedReplyFromEntity: 'receivedReplyFromEntity',
  replyDate: 'replyDate',
};

interface PanelState {
  form: boolean;
  attachments: boolean;
  history: boolean;
  settings: boolean;
}

// Shape of GET /api/v1/complaints/nodal-records. Only the nodal officer contact fields are stored on
// the record itself; the rest is joined off the complaint server-side, so everything here is
// read-only as far as this screen is concerned.
interface NodalRecord {
  id: number;
  recordNumber: string;
  complaintNumber: string;
  status: string;
  statusLabel: string;
  assignedTo: string;
  slaDays: number | null;
  receiptDate: string;
  subject: string;
  complainant: string;
  mobile: string;
  email: string;
  bankName: string;
  bankCategory: string;
  branchCategory: string;
  branchName: string;
  pincode: string;
  city: string;
  district: string;
  state: string;
  country: string;
  moduleName: string;
  atmComplaint: string;
  designatedOffice: string;
  processingOffice: string;
  noName: string;
  noMobile: string;
  noEmail: string;
  noDesignation: string;
  pnoName: string;
  pnoMobile: string;
  pnoEmail: string;
  // The officer's saved assessment. Dates arrive ISO because the form binds them to native date
  // inputs; notice131ComplyDate is the exception — it is display-only, so the backend sends it
  // already formatted.
  advisoryComplianceDate: string | null;
  disputeAmount: number | null;
  compensationLoss: number | null;
  compensationMental: number | null;
  awardImplementationDate: string | null;
  awardAcceptanceDate: string | null;
  notice131ComplyDate: string | null;
  forwardedToReAt: string | null;
}

export interface Complaint {

  id: number;

  complaintNumber: string;

  subject: string;

  complainantEmail: string;

  complainantName: string;

  description: string;

  receiptDate: string;

  filingType: string;
  sla:string;

  proposedComplaintType: string;

  bankResponseDto: {

    id: number;

    entityName: string;

    bsrCode: string;

  };

  complaintClassification: {

    id: number;

    name: string;

    description: string;

  };

}

interface HistoryEntry {
  id: string;
  status: string;
  statusLabel: string;
  statusColor: string;
  modifiedOn: string;
  modifiedBy: string;
  assignedOfficer: string;
  officerAvatarColor: string;
  description: string;
}

// Shape of GET /api/v1/email-syndication/deo. Keycloak owns the roster; OFFICER_AVAILABILITY owns
// leave state and the per-officer threshold, and currentLoad is a live count of drafts already sitting
// with that DEO — which is what automatic assignment balances on.
interface DeoUser {
  id: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  leaveReason: string;
  officeCode: string;
  currentLoad: number;
  maxLoad: number;
}

// One CONCILIATION_MEETINGS row, as returned by /api/complaints/rbio/{id}/conciliation. The newest row
// is the live meeting; the earlier rows are the reschedule trail and are read-only.
interface ConciliationMeeting {
  id: number | null;
  meetingStatus: string;
  meetingDate: string | null;
  meetingTime: string | null;
  acceptedByComplainant: boolean | null;
  acceptedByEntity: boolean | null;
  conductedThroughVc: boolean | null;
  meetingComments: string | null;
  comments: string | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
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

/** A row from the entity typeahead, GET /api/v1/routing/entities/list. */
interface EntitySearchResult {
  id: number;
  name: string;
  department: string;
  /** The entity's category as the master stores it, e.g. "Public Sector Bank". */
  entityType: string;
  moduleName?: string | null;
  /** The category as the complaint screens label it, e.g. "Nationalised Bank". */
  entityCategory?: string | null;
  /** RBI's sub-classification below the category, e.g. "Loan Company". NBFCs only. */
  entityTypeDetail?: string | null;
  city?: string | null;
  state?: string | null;
}

/** One entity in full, GET /api/v1/routing/entities/{id}. */
interface EntityDetail extends EntitySearchResult {
  status?: string | null;
  portalEnabled?: boolean | null;
  nodalOfficerName?: string | null;
  nodalOfficerEmail?: string | null;
  nodalOfficerPhone?: string | null;
  nodalOfficerDesignation?: string | null;
  pnoName?: string | null;
  pnoEmail?: string | null;
  pnoPhone?: string | null;
}

@Component({
  selector: 'app-rbio-complaint-details-view',
  imports: [CommonModule, FormsModule, ButtonModule, SpeechButtonComponent,RbioHeaderComponent],
  templateUrl: './rbio-complaint-details-view.component.html',
  styleUrl: './rbio-complaint-details-view.component.scss',
})

export class RbioComplaintDetailsView implements OnInit {

  private router = inject(Router);
  private navService = inject(NavigationService);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(KeycloakAuthService);
  activatedRoute = inject(ActivatedRoute)
  private destroyRef = inject(DestroyRef);


  // Header
  complaintId: any;
  complaintNumber: string = ''; 
  loggedInUserName = '';
  complaintOffice = '';
  slaDaysRemaining = signal(30);
  userRole = signal<'DO' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN' | 'HEAD'>('DO');
  activeTab = signal<'creation' | 'assignment'>('creation');
  complaintDetails = {};
  assignedOfficer: string = ''; 

  // Section expand state
  sectionExpanded = { basic: true, eligibility: false, entity: true, complainant: false };

  // Left panel - PDF upload
  scannedFile: File | null = null;
  scanError = '';
  ocrInProgress = signal(false);
  ocrComplete = signal(false);
  pdfExpanded = signal(false);
  pdfPage = signal(1);
  pdfTotalPages = signal(1);
  pdfPreviewUrl = signal<SafeResourceUrl | null>(null);

  // Form fields - Basic Details
  subject = '';
  description = '';
  comments = '';
  modeOfReceipt = 'PHYSICAL_LETTER';
  receivedDate = '';
  isCpgram = false;
  cpgramsNumber = '';
  complaintStatusNav= "";

  // Eligibility
  proposedComplaintType = 'NEW_COMPLAINT';
  category = '';
  eligibilityEntityName = '';
  eligibilityEntitySearch = '';
  eligibilityEntityResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  showEligibilityEntityDropdown = signal(false);
  private eligibilityEntityTimeout: any = null;
  markAllEligible = false;
  eligibilityQuestions: EligibilityQuestionItem[] = [
    { key: 'entityRegulatedByRbi', label: 'Is Entity regulated by RBI?', type: 'radio', answer: null, dateValue: null },
    { key: 'notDirectlyAddressed', label: 'The Complaint not directly addressed to Ombudsman', type: 'radio', answer: null, dateValue: null },
    { key: 'notRegisteredWithEntity', label: 'Is the Complaint not registered with Entity (FRC)?', type: 'radio', answer: null, dateValue: null },
    { key: 'frivolousVexatious', label: 'Is the complainant frivolous, vexatious, and threatening?', type: 'radio', answer: null, dateValue: null },
    { key: 'subJudice', label: 'Is the Complaint Sub-Judice or under arbitration?', type: 'radio', answer: null, dateValue: null },
    { key: 'subJudicePending', label: 'Is the complaint relating to the same grievance which is already pending before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?', type: 'radio', answer: null, dateValue: null },
    { key: 'alreadySettled', label: 'Is the complaint relating to the same grievance which is already settled or dealt before any Court, Tribunal, Arbitrator or any other judicial or quasi-judicial forum (excluding criminal proceedings pending or decided before a Court/ Tribunal or any police investigation initiated in a criminal offence)?', type: 'radio', answer: null, dateValue: null },
    { key: 'throughAdvocate', label: 'Whether complaint is being made through an advocate?', type: 'radio', answer: null, dateValue: null },
    { key: 'isAdvocate', label: 'Is the complainant an advocate?', type: 'radio', answer: null, dateValue: null },
    { key: 'pendingBeforeOmbudsman', label: 'Is the complaint relating to the same grievance which is already pending before the Ombudsman?', type: 'radio', answer: null, dateValue: null },
    { key: 'alreadyDealt', label: 'Has already been dealt with or is under process on the same ground with the ombudsman?', type: 'radio', answer: null, dateValue: null },
    { key: 'generalAgainstManagement', label: 'Does the complaint involve general complaints against management or executives of a RE?', type: 'radio', answer: null, dateValue: null },
    { key: 'previouslyFiledWithCEPC', label: 'At the complainant filed a complaint on the same matter with the Consumer Education and Protection Cell (CEPC) or the Reserve Bank of India (RBI) previously?', type: 'radio', answer: null, dateValue: null },
    { key: 'disputesBetweenREs', label: 'Does it involve disputes between REs?', type: 'radio', answer: null, dateValue: null },
    { key: 'staffEmployerRelationship', label: 'Is from staff of an RE and involves employer-employee relationship?', type: 'radio', answer: null, dateValue: null },
    { key: 'incompleteInformation', label: 'Complete information not available for registering the complaint', type: 'radio', answer: null, dateValue: null },
    { key: 'filedWrittenComplaint', label: 'As the complainant filed a written / electronic complaint with the Regulated Entity?', type: 'radio', answer: null, dateValue: null },
    { key: 'complaintFiledDate', label: 'Date on which the complaint was first filed with the Regulated Entity', type: 'date', answer: null, dateValue: null },
    { key: 'receivedReplyFromEntity', label: 'Did Complainant received any reply from the Entity?', type: 'radio', answer: null, dateValue: null },
    { key: 'replyDate', label: 'Date of Reply', type: 'date', answer: null, dateValue: null },
  ];

  // Entity Details
  entityName = '';
  entityType = 'BANK';
  // The complaint's regulated entity. Sent back as entityDetails.id on save, which is what actually
  // moves the complaint to a different entity — the name alone is only a label, and leaving the id
  // behind points the nodal officer record at the previous entity.
  regulatedEntityId: number | null = null;
  entitySearchText = '';
  entitySearchResults = signal<EntitySearchResult[]>([]);
  entitySearchLoading = signal(false);
  showEntityDropdown = signal(false);
  // The selected entity's own contact details, read-only. These are what the complaint gets
  // forwarded to, so the officer needs to see whose desk a change of entity moves it to.
  entityContact = signal<EntityDetail | null>(null);
  entityContactLoading = signal(false);
  // Names the fields that were cleared because they described the previous entity, so the reset is
  // visible rather than silent.
  entityResetNotice = signal('');
  private entitySearchTimeout: any = null;
  moduleName = '';
  entityCategory = '';
  entityTypeDisplay = '';
  bsrCode = '';
  entityPincode = '';
  entityCountry = 'India';
  entityState = '';
  entityDistrict = '';
  entityCity = '';
  entityBranchName = '';
  entityBranchCategory = '';
  entityAddress = '';
  branchCenterName = '';
  cosmosCode = '';
  assetSizeInCrores: number | null = null;
  entityQuestions: { key: string; label: string; answer: boolean | null }[] = [
    { key: 'depositTaking', label: 'Whether Deposit Taking/Non-Deposit Taking Entity', answer: null },
    { key: 'assetGreater100Cr', label: 'Whether Asset Size is greater than 100 Crores', answer: null },
    { key: 'liquidatedPresent', label: 'Whether Liquidated present', answer: null },
  ];

  // Complainant Details
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';
  complainantAddress = '';

  // Complainant Details (Extended)
  otherEntityName = '';
  registrationWithRbiDate = '';
  complaintCategory = '';
  complaintSubCategory1 = '';
  complaintSubCategory2 = '';
  filingDate = '';
  registrationDateValid = true;
  reminderSent = true;
  disputedAmount: number | null = null;
  compensationSoughtYesNo = true;
  legalCaseFiled = false;
  preEnquiryReceived = false;
  highPriority = false;
  loanDisposalAmount: number | null = null;
  additionalComments = '';
  crpcProposedAction = '';
  vernacular = false;
  vernacularLanguage = '';
  vernacularLanguages = ['Hindi', 'Marathi', 'Tamil', 'Telugu', 'Kannada', 'Bengali', 'Gujarati', 'Malayalam', 'Punjabi', 'Odia', 'Urdu', 'Assamese', 'Konkani', 'Sanskrit'];
  pensionComplaint = false;
  businessCorrespondent = false;
  atmCreditDebitCard = false;
  schemeFlag = '';
  rboCgpcOld = '';
  groundsFlag = '';

  // Complaint Linkage
  freeMarkedComplaint = false;
  currentComplaintNumber = '';
  replyWithin30Days: 'Yes' | 'No' | 'Not Applicable' = 'Not Applicable';

  // Financial
  amountInvolved: number | null = null;
  transactionDate = '';

  // Assignment
  assignmentMode = 'AUTOMATIC';
  selectedDeoId = '';
  selectedDeoName = '';
  deos = signal<DeoUser[]>([]);
  loadingDeos = signal(false);
  deoLoadError = signal('');
  deoSearch = signal('');
  showDeoDropdown = signal(false);
  assignError = signal('');
  showConfirmDialog = signal(false);

  /** Only a DEO who is enabled and not on leave can take new work. */
  availableDeos = computed(() => this.deos().filter(d => d.isActive && !d.isOnLeave));

  selectedDeo = computed(() => this.deos().find(d => d.id === this.selectedDeoId) || null);

  /** Manual mode searches the live roster by name, email or office. */
  filteredDeos = computed(() => {
    const term = this.deoSearch().trim().toLowerCase();
    const pool = this.deos();
    if (!term) return pool;
    return pool.filter(d => d.displayName.toLowerCase().includes(term)
      || d.email.toLowerCase().includes(term)
      || (d.officeCode || '').toLowerCase().includes(term));
  });

  // Pincode lookup
  pincodeLoading = signal(false);

  // State
  saving = signal(false);
  saveError = signal('');
  submitting = signal(false);
  submitted = signal(false);
  draftSaved = signal(false);
  summaryActiveTab = signal<'summary' | 'email'>('summary');
  assessmentTab = signal<string>('summary');
  editMode = signal(false);
  editSnapshot: Record<string, any> | null = null;
  assessmentPanels = signal<PanelState>({ form: true, attachments: false, history: false, settings: false });
  emailPanels = signal<PanelState>({ form: false, attachments: false, history: false, settings: false });
  createPanels = signal<PanelState>({ form: true, attachments: false, history: false, settings: false });
  summarySections = { basic: true, eligibility: false, entity: false, complainant: false, declaration: false };
  declarationAccepted = false;
  createdComplaintId = signal('');

  // Right Sidebar
  rightSidebarOpen = signal(false);
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);
  sidebarAttachments = signal<Attachment[]>([]);
  loadingSidebarAttachments = signal(false);
  uploadingDocument = signal(false);
  attachmentsPanelOpen = signal(false);

  // ═══ History Panel ═══
  showHistoryPanel = signal(false);
  hideComments = signal(false);
  historyEntries = signal<HistoryEntry[]>([]);
  loadingHistory = signal(false);

  // Final Decision
  finalDecisionAction = '';
  finalDecisionRemarks = '';
  finalDecisionSubmitting = signal(false);
  showFinalDecisionPreview = signal(false);
  complaint = signal<Complaint | null>(null);
  closureClause = '';
  closureClauseSearch = '';
  closureClauseDropdownOpen = false;
  closureClauseDescription = '';
  complaintStatusOnPortal = '';
  speakingOrderGenerated = '';
  gistOfCase = '';
  gistOfCaseRegional = '';

  // Validation
  formSubmitAttempted = false;
  fieldErrors: Record<string, string> = {};

  // Assessment panel
  sendToDeputy = false;
  assessmentComment = '';
  speakingOrderContent = '';
  proposedAction = '';
  proposedClause = '';
  clauseSearch = '';
  clauseDropdownOpen = false;
  deputyOmbudsmanDecision = 'NON_MAINTAINABLE';
  deputyOmbudsmanComments = '';
  complaintStatus = signal('NEW_COMPLAINT');
  isReadOnlyViewer = signal(false);
  justActioned = signal(false);
  workflowAction = signal('');
  // Conciliation is the DO's own step; every other rung on the ladder only ever reads its outcome.
  conciliationEnabled = computed(() => {
    if (this.userRole() !== 'DO') return false;
    const action = this.workflowAction();
    const status = this.complaintStatus();
    const excludedStatuses = ['ADVISORY_COMPLIED', 'COMPLAINT_SETTLED', 'COMPLAINT_WITHDRAWN', 'COMPLAINT_REJECTED', 'AWARD_PASSED', 'OMBUDSMAN_DECISION'];
    if (excludedStatuses.includes(status)) return false;
    return action === 'MAINTAINABLE';
  });
  // ═══ Conciliation tab ═══
  readonly conciliationStatuses = [
    { value: 'SCHEDULED', label: 'Scheduled' },
    { value: 'RESCHEDULED', label: 'Rescheduled' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];
  meetingStatus = 'SCHEDULED';
  meetingDate = '';
  meetingTime = '';
  acceptedByComplainant: boolean | null = null;
  acceptedByEntity: boolean | null = null;
  conductedThroughVc: boolean | null = null;
  meetingComments = '';
  conciliationComments = '';
  conciliationCurrent = signal<ConciliationMeeting | null>(null);
  conciliationHistory = signal<ConciliationMeeting[]>([]);
  conciliationLoading = signal(false);
  conciliationSaving = signal(false);
  conciliationError = signal('');
  conciliationSaved = signal(false);
  conciliationFieldErrors = signal<Record<string, string>>({});
  private conciliationLoadedFor = '';

  // Getters, not computed(): meetingStatus is an ngModel field rather than a signal.

  /** Picking RESCHEDULED opens a new meeting server-side, preserving the current one's minutes. */
  get conciliationOpensNewMeeting(): boolean {
    const current = this.conciliationCurrent();
    if (!current) return true;
    if (this.meetingStatus === 'RESCHEDULED') return true;
    return current.meetingStatus === 'COMPLETED' || current.meetingStatus === 'CANCELLED';
  }

  /** The backend requires a date and time while the meeting is still live. */
  get conciliationRequiresSchedule(): boolean {
    return this.meetingStatus === 'SCHEDULED' || this.meetingStatus === 'RESCHEDULED';
  }

  approvalSentTo = signal('');
  showApprovalMenu = signal(false);
  showSendBackMenu = signal(false);
  approvalMenuPos = signal({ top: 0, left: 0 });
  sendBackMenuPos = signal({ top: 0, left: 0 });

  actualComplaintNumber: any;

  constructor() {
    effect(() => {
      const isOpen = this.attachmentsPanelOpen();
      console.log('--- [SIGNAL EFFECT] attachmentsPanelOpen changed to:', isOpen);
      
      if (isOpen) {
        console.log('[SIGNAL EFFECT] Panel is open. Executing initial GET API call...');
        this.fetchAttachments();
      }
    });

    // Fetched when the tab is first opened rather than with the complaint, so the panel is never
    // paid for on screens that never show it.
    effect(() => {
      if (this.assessmentTab() === 'conciliation') {
        this.loadConciliation();
      }
    });
  }

  toggleApprovalMenu(event: MouseEvent) {
    if (!this.showApprovalMenu()) {
      const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
      this.approvalMenuPos.set({ top: rect.bottom + 6, left: rect.left });
    }
    this.showApprovalMenu.set(!this.showApprovalMenu());
  }

  toggleSendBackMenu(event: MouseEvent) {
    if (!this.showSendBackMenu()) {
      const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
      this.sendBackMenuPos.set({ top: rect.bottom + 6, left: rect.left });
    }
    this.showSendBackMenu.set(!this.showSendBackMenu());
  }
  showApprovalDialog = signal(false);
  showSendBackDialog = signal(false);
  sendBackTarget = signal<'DEALING_OFFICER' | 'REVIEWER'>('DEALING_OFFICER');
  sendBackAssignmentMode = 'AUTOMATIC';
  sendBackSelectedName = '';
  sendBackTargetUsers: { id: string; name: string; officeCode?: string }[] = [];
  sendBackFilteredUsers: { id: string; name: string; officeCode?: string }[] = [];
  sendBackSubmitting = signal(false);
  approvalTarget = signal<'DEALING_OFFICER' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN'>('REVIEWER');
  approvalAssignmentMode = 'AUTOMATIC';
  approvalSelectedName = '';
  approvalTargetUsers = signal<{ id: string; name: string; officeCode?: string }[]>([]);
  approvalFilteredUsers: { id: string; name: string; officeCode?: string }[] = [];
  approvalCrpcAction = '';
  approvalCrpcClause = '';
  systemicIssue = '';
  assessmentComments = signal<{ id: number;author: string; target:string; complaintNumber:string; initials: string;  time?: string; text: string; color: string; role?: string; noRecordNumber:string; createdAt?: string }[]>([]);

  // Forward Tab
  forwardTarget = signal<'REGULATORY_BODIES' | 'RBI_DEPARTMENT' | 'OFFICE' | ''>('');
  forwardRegulatorName = '';
  forwardRegulatorEmail = '';
  forwardDepartmentName = '';
  forwardDepartmentEmail = '';
  forwardOfficeCode = '';
  forwardOfficeName = '';
  officeList = signal<{ officeCode: string; officeName: string; officeType: string }[]>([]);
  showForwardConfirm = signal(false);
  forwardSubmitting = signal(false);

  loadOfficeList() {
    if (this.officeList().length > 0) return;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/offices`).subscribe({
      next: (res) => this.officeList.set(res?.data || []),
      error: () => this.officeList.set([])
    });
  }

  onForwardOfficeSelected(officeCode: string) {
    this.forwardOfficeCode = officeCode;
    const office = this.officeList().find(o => o.officeCode === officeCode);
    this.forwardOfficeName = office?.officeName || '';
  }

  get forwardTargetLabel(): string {
    switch (this.forwardTarget()) {
      case 'REGULATORY_BODIES': return 'Other Regulatory Bodies';
      case 'RBI_DEPARTMENT': return 'Other RBI Department';
      case 'OFFICE': return 'Other Office';
      default: return '';
    }
  }

  selectForwardTarget(target: 'REGULATORY_BODIES' | 'RBI_DEPARTMENT' | 'OFFICE') {
    this.forwardTarget.set(target);
    if (target === 'OFFICE') {
      this.loadOfficeList();
    }
  }

  openForwardConfirm() {
    const target = this.forwardTarget();
    if (!target) {
      alert('Please select where to forward the complaint.');
      return;
    }
    if (target === 'OFFICE' && !this.forwardOfficeCode) {
      alert('Please select an office.');
      return;
    }
    this.showForwardConfirm.set(true);
  }

  cancelForwardConfirm() {
    this.showForwardConfirm.set(false);
  }

  confirmForward() {
    if (this.forwardSubmitting()) return;
    this.forwardSubmitting.set(true);

    this.persistComment();

    const target = this.forwardTarget();

    if (target === 'OFFICE') {
      const fromOffice = this.complaintOffice || '';
      const toOffice = this.forwardOfficeCode;
      const deptOf = (code: string) => (code || '').split('-')[0] || 'RBIO';
      const payload = {
        complaintNumber: this.complaintNumber,
        fromOffice,
        toOffice,
        transferType: `${deptOf(fromOffice)}_${deptOf(toOffice)}`,
        reason: this.assessmentComment || '',
        requestedBy: this.auth.currentUser()?.username || ''
      };
      this.http.post(`${environment.apiBaseUrl}/api/v1/crpc/head/transfers/request`, payload).subscribe({
        next: () => {
          this.forwardSubmitting.set(false);
          this.showForwardConfirm.set(false);
          this.approvalSentTo.set(this.forwardOfficeName);
          this.justActioned.set(true);
          this.complaintStatus.set('SENT_TO_OFFICE');
        },
        error: (err) => {
          this.forwardSubmitting.set(false);
          alert(err?.error?.message || 'Failed to submit transfer request.');
        }
      });
      return;
    }

    // Other Regulatory Bodies / Other RBI Department: closes the complaint immediately.
    let assignedToName = '';
    let assignedToEmail = '';
    if (target === 'REGULATORY_BODIES') {
      assignedToName = this.forwardRegulatorName;
      assignedToEmail = this.forwardRegulatorEmail;
    } else if (target === 'RBI_DEPARTMENT') {
      assignedToName = this.forwardDepartmentName;
      assignedToEmail = this.forwardDepartmentEmail;
    }

    const payload = {
      target: 'OTHER_' + target,
      assignedTo: assignedToEmail || assignedToName,
      assignedToName: assignedToName || this.forwardTargetLabel,
      assignmentMode: 'MANUAL',
      remarks: this.assessmentComment || null,
      performedBy: this.auth.currentUser()?.username || '',
      performedByRole: this.userRole()
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.forwardSubmitting.set(false);
        this.showForwardConfirm.set(false);
        this.approvalSentTo.set(this.forwardTargetLabel);
        this.justActioned.set(true);
        this.complaintStatus.set('CLOSED');
      },
      error: (err) => {
        // This used to be a copy of the success handler, so a failed forward still told the officer
        // the complaint had closed while it stayed open in the database. The status is left alone.
        this.forwardSubmitting.set(false);
        alert(err?.error?.message || 'Failed to forward this complaint. It has not been closed.');
      }
    });
  }

  loadComplaints() {
    const id = this.activatedRoute.snapshot.paramMap.get('id');
    this.http.get<Complaint>(`${environment.apiBaseUrl}/api/complaints/rbio/${id}/summary`)
      .subscribe({
        next: (res) => {
          this.complaint.set(res);
          console.log(res);
        },
        error: (err) => {
          console.error('Error loading complaints:', err);
        }
  });

  }

  // Office Head Approval (CRPC_HEAD deciding on an "Other Office" forward)
  transferOfficeCode = '';
  transferOfficeName = '';
  transferPreOfficer = '';
  transferPreRole = '';
  showOfficeHeadDialog = signal(false);
  officeHeadDecisionType = signal<'APPROVE' | 'REJECT'>('APPROVE');
  officeHeadComment = '';
  officeHeadSubmitting = signal(false);
  changeTerritory = false;
  reassignOfficeCode = '';

  get preForwardRoleLabel(): string {
    switch (this.transferPreRole) {
      case 'DO': return 'Dealing Officer';
      case 'REVIEWER': return 'Reviewer';
      case 'DEPUTY_OMBUDSMAN': return 'Deputy Ombudsman';
      case 'OMBUDSMAN': return 'Ombudsman';
      default: return this.transferPreRole || 'Previous Owner';
    }
  }

  openOfficeHeadDialog(decision: 'APPROVE' | 'REJECT') {
    this.officeHeadDecisionType.set(decision);
    this.officeHeadComment = '';
    this.showOfficeHeadDialog.set(true);
  }

  cancelOfficeHeadDialog() {
    this.showOfficeHeadDialog.set(false);
  }

  onChangeTerritoryToggle() {
    if (this.changeTerritory) {
      this.loadOfficeList();
    }
  }

  confirmOfficeHeadDecision() {
    if (this.officeHeadSubmitting()) return;
    const decision = this.officeHeadDecisionType();
    if (decision === 'REJECT' && !this.officeHeadComment.trim()) {
      alert('A rejection comment is mandatory.');
      return;
    }
    this.officeHeadSubmitting.set(true);

    const payload: any = {
      decision,
      comment: this.officeHeadComment || null,
      performedBy: this.auth.currentUser()?.username || ''
    };
    if (decision === 'APPROVE' && this.changeTerritory && this.reassignOfficeCode) {
      payload.overrideOfficeCode = this.reassignOfficeCode;
    }

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/office-head-decision`, payload).subscribe({
      next: () => {
        this.officeHeadSubmitting.set(false);
        this.showOfficeHeadDialog.set(false);
        this.approvalSentTo.set(decision === 'APPROVE' ? 'the selected office' : this.preForwardRoleLabel);
        this.justActioned.set(true);
        this.complaintStatus.set(decision === 'APPROVE' ? 'SENT' : 'SENT_BACK');
      },
      error: (err) => {
        this.officeHeadSubmitting.set(false);
        alert(err?.error?.message || 'Failed to submit decision.');
      }
    });
  }

  // Nodal Officer Record
  nodalRecords = signal<NodalRecord[]>([]);
  loadingNodalRecords = signal(false);
  nodalRecordsError = signal('');
  nodalFilterRecordNumber = '';
  nodalFilterSubject = '';
  nodalFilterBank = '';
  nodalFilterSla = '';
  nodalFilterAssigned = '';
  nodalFilterStatus = '';
  selectedNodalRecord = signal<NodalRecord | null>(null);
  nodalDetailView = signal(false);
  sendingToRE = signal(false);
  sendToREError = signal('');
  nodalStatusCode = '';
  sla=''
  nodalAdvisoryDate = '';
  nodalDisputeAmount: number | null = null;
  nodalCompensationLoss: number | null = null;
  nodalCompensationMental: number | null = null;
  nodalAwardImplementationDate = '';
  nodalAwardAcceptanceDate = '';
  nodal131ComplyDate = '';
  nodalCommentToNO = '';
  nodalCommentToPNO = '';
  nodalCommentsSubmitting = false;
  nodalComments = signal<{ id: number; initials: string; author: string; createdAt: string; text: string; target: 'NO' | 'PNO'; color: string }[]>([]);

  // Email Communication
  emailComposeMode = signal(false);
  emailOpenActivitiesExpanded = true;
  emailDraftExpanded = false;
  emailClosedExpanded = false;
  emailActivities = signal<{ id: number; subject: string; date: string; from: string; to: string; assignedTo: string; dueDate: string; body: string; attachments: { name: string; size: string }[] }[]>([]);
  emailDrafts = signal<{ id: number; subject: string; date: string; from: string; to: string; body: string }[]>([]);
  emailClosedActivities = signal<{ id: number; subject: string; date: string; from: string; to: string; assignedTo: string; dueDate: string; body: string; attachments: { name: string; size: string }[] }[]>([]);
  selectedEmailActivity = signal<any>(null);
  emailFrom = 'cmssupportngp@rbi.org.in';
  emailTo = '';
  emailCc = '';
  emailBcc = '';
  emailSubject = '';
  emailBody = '';

  // Reference data
  states = signal<string[]>([]);
  districts = signal<string[]>([]);

  protected Math = Math;

  togglePanel(panelGroup: WritableSignal<PanelState>, key: keyof PanelState) {
    if (key === 'history') {
      this.toggleHistoryPanel();
      return;
    }
    const opening = !panelGroup()[key];
    panelGroup.update(v => ({ ...v, [key]: !v[key] }));
    if (opening) {
      this.rightSidebarOpen.set(false);
      this.attachmentsPanelOpen.set(false);
      this.showHistoryPanel.set(false);
    }
  }

  toggleHistoryPanel() {
    const opening = !this.showHistoryPanel();
    this.showHistoryPanel.set(opening);
    if (opening) {
      this.rightSidebarOpen.set(false);
      this.attachmentsPanelOpen.set(false);
      this.assessmentPanels.update(v => ({ ...v, attachments: false, history: false, settings: false }));
      this.emailPanels.update(v => ({ ...v, history: false, settings: false }));
      this.createPanels.update(v => ({ ...v, history: false, settings: false }));
      if (this.historyEntries().length === 0) {
        this.loadHistory();
      }
    }
  }

  loadHistory() {
    this.loadingHistory.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/complaints/${this.complaintId}/timeline`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data) && data.length > 0) {
          this.historyEntries.set(this.mapTimelineToHistory1(data));
        } 
        this.loadingHistory.set(false);
      },
      error: () => {
        // this.historyEntries.set(this.FALLBACK_HISTORY);
        this.loadingHistory.set(false);
      }
    });
  }

  private mapTimelineToHistory1(timelineData: any[]): any[] {
    return timelineData.map(entry => {
      const rawStatus = entry.toStatus || 'NEW_COMPLAINT';
      const colorTheme = this.getStatusColor(rawStatus);
      
      return {
        id: entry.id,
        // 🟢 Mapped parameters configured explicitly to match the incoming JSON payload track architecture
        statusLabel: this.formatStatusLabel(rawStatus),
        statusColor: colorTheme,
        modifiedOn: entry.performedAt, // Passed raw to be processed by template DatePipe filter systems
        modifiedBy: entry.performedBy || 'System',
        assignedOfficer: entry.performedBy || 'System',
        description: entry.remarks || ''
      };
    });
  }

  private mapTimelineToHistory(timeline: any[]): HistoryEntry[] {
    const colorMap: Record<string, string> = {
      'SENT_TO_RBI': '#22c55e',
      'ADVISORY_ISSUED': '#f59e0b',
      'SENT_TO_RE': '#3b82f6',
      'INFORMATION_REQUIRED': '#ef4444',
      'SENT_TO_REVIEWER': '#3b82f6',
      'SENT_TO_DEPUTY_OMBUDSMAN': '#8b5cf6',
      'SENT_TO_OMBUDSMAN': '#6366f1',
      'CLOSED': '#64748b',
      'RESOLVED': '#22c55e',
      'MEETING_SCHEDULED': '#06b6d4',
      'AWARD_PASSED': '#22c55e',
      'NEW': '#3b82f6',
      'ASSIGNED': '#f59e0b',
    };
    const statusLabels: Record<string, string> = {
      'SENT_TO_RBI': 'Sent to RBI',
      'ADVISORY_ISSUED': 'Advisory Issued',
      'SENT_TO_RE': 'Sent to RE',
      'INFORMATION_REQUIRED': 'Information Required',
      'SENT_TO_REVIEWER': 'Sent to Reviewer',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'Sent to Deputy Ombudsman',
      'SENT_TO_OMBUDSMAN': 'Sent to Ombudsman',
      'CLOSED': 'Closed',
      'RESOLVED': 'Resolved',
      'MEETING_SCHEDULED': 'Meeting Scheduled',
      'AWARD_PASSED': 'Award Passed',
      'NEW': 'New Complaint',
      'ASSIGNED': 'Assigned',
    };
    const avatarColors = ['#8b5cf6', '#f97316', '#3b82f6', '#ef4444', '#22c55e', '#06b6d4'];
    return timeline.map((entry, i) => {
      const key = (entry.toStatus || entry.action || '').toUpperCase();
      return {
        id: entry.id || String(i + 1),
        status: key,
        statusLabel: statusLabels[key] || entry.toStatus || entry.action || 'In Progress',
        statusColor: colorMap[key] || '#64748b',
        modifiedOn: entry.timestamp || '',
        modifiedBy: entry.performedBy || entry.actor || '',
        assignedOfficer: entry.assignedTo || entry.performedBy || '',
        officerAvatarColor: avatarColors[i % avatarColors.length],
        description: entry.remarks || ''
      };
    });
  }

formatStatusLabel(status: string): string {
  if (!status) return '';
  return status
    .toLowerCase()
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

private getStatusColor(status: string): string {
  switch (status) {
    case 'New Complaint':
    case 'NEW_COMPLAINT':
      return '#065f46';

    case 'IN_PROGRESS':
      return '#1e40af';

    case 'Information Required':
    case 'INFORMATION_REQUIRED':
    case 'Sent Back To Do':
    case 'Sent Back To DO':
      return '#A16624'; 

    case 'MEETING_SCHEDULED':
      return '#6b21a8'; 

    case 'Complaint Closed':
    case 'COMPLAINT_CLOSED':
      return '#202124'; 

    case 'Complaint Withdrawn':
      return '#A12471';

    default:
      return '#64748b'; 
  }
}


  toggleRightSidebar() {
    const opening = !this.rightSidebarOpen();
    this.rightSidebarOpen.set(opening);
    if (opening) {
      this.attachmentsPanelOpen.set(false);
      this.showHistoryPanel.set(false);
      this.assessmentPanels.update(v => ({ ...v, history: false, settings: false }));
      this.emailPanels.update(v => ({ ...v, history: false, settings: false }));
      this.createPanels.update(v => ({ ...v, history: false, settings: false }));
      this.loadPastComplaints();
    }
  }

  toggleAttachmentsPanel() {
    const opening = !this.attachmentsPanelOpen();
    this.attachmentsPanelOpen.set(opening);
    if (opening) {
      this.rightSidebarOpen.set(false);
      this.showHistoryPanel.set(false);
      this.assessmentPanels.update(v => ({ ...v, history: false, settings: false }));
      this.emailPanels.update(v => ({ ...v, history: false, settings: false }));
      this.createPanels.update(v => ({ ...v, history: false, settings: false }));
    }
  }

  loadPastComplaints() {
    if (!this.complainantEmail && !this.complainantPhone) {
      return;
    }
    this.loadingPastComplaints.set(true);
    const params: any = { excludeId: this.complaintId };
    if (this.complainantEmail) params.email = this.complainantEmail;
    if (this.complainantPhone) params.phone = this.complainantPhone;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/past-complaints/by-complainant`, { params })
      .subscribe({
        next: (res) => {
          this.pastComplaints.set(res?.data || []);
          this.loadingPastComplaints.set(false);
        },
        error: () => {
          // Empty, not a sample pair. Invented history on this panel reads as the complainant's real
          // record and would inform a decision about repeat complaints.
          this.pastComplaints.set([]);
          this.loadingPastComplaints.set(false);
        }
      });
  }

  ngOnInit() {
    this.receivedDate = new Date().toISOString().split('T')[0];
    const user = this.auth.currentUser();
    this.loggedInUserName = user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username : '';
    this.detectUserRole();
    this.detectUserOffice(user?.username || '');
    this.loadStates();
    this.loadDeos();

    this.activatedRoute.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(params => {
        const id = params.get('id');
        if (id) {
          this.complaintId = id;
          this.loadComplaints();
          this.loadExistingComplaint(id);
        }
      });
  }

  private loadExistingComplaint(id: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/complaints/rbio/${id}/summary`).subscribe({
      next: (res) => {
        this.applySummary(res?.data || res || {});
        this.submitted.set(true);
        this.loadPastComplaints();
        this.fetchAttachments();
        this.loadComments();
        this.loadEmailThreads();
      },
      error: (err) => {
        this.saveError.set(err?.status === 403
          ? 'You do not have access to this complaint.'
          : 'Could not load this complaint.');
      }
    });
  }

  /**
   * Maps the nested summary onto the flat form fields. Shared by the initial GET and by the PUT
   * response, so a save leaves the form showing exactly what was persisted.
   */
  private applySummary(data: any) {
        this.assignedOfficer = data.assignedOfficer || '';
        // The summary is editable only by the officer holding it; everyone else reads it. canEdit comes
        // from the server so the form matches what the PUT will actually accept.
        this.isReadOnlyViewer.set(data.canEdit === false || data.navBarDto?.status === 'CLOSED');

        this.complaintNumber = data.navBarDto?.complaintNumber || '';
        this.actualComplaintNumber = data.navBarDto?.complaintNumber || '';

        this.category = data.navBarDto?.complaintCategory;

        this.sla=data.navBarDto?.slaBreachIn

          this.complaintStatusNav =
          data.navBarDto?.status

        this.subject =
          data.basicDetailsDto?.subject || '';

        this.description =
          data.basicDetailsDto?.complainDetails || '';

        this.comments =
          data.basicDetailsDto?.comments || '';

        this.complainantName =
          data.basicDetailsDto?.complainantName || '';

        this.complainantEmail =
          data.basicDetailsDto?.emailId || '';

        this.modeOfReceipt =
          data.basicDetailsDto?.modeOfReceipt || '';

        this.receivedDate =
          data.basicDetailsDto?.receiptDate || '';

        /* CPGRAM */

        this.isCpgram =
          !!data.basicDetailsDto?.complaintCpgram;

        this.cpgramsNumber =
          data.basicDetailsDto?.cpgramNumber || '';

        /* Entity Details */

        this.entityName =
          data.navBarDto?.entityName || '';

        this.entitySearchText =
          data.navBarDto?.entityName || '';

        this.eligibilityEntityName =
          data.navBarDto?.entityName || '';

        this.regulatedEntityId =
          data.entityDetails?.id ?? null;
        this.entityResetNotice.set('');
        if (this.regulatedEntityId) {
          this.loadEntityContact(this.regulatedEntityId);
        } else {
          this.entityContact.set(null);
        }

        // Not this.entityType: that carries the coarse BANK/NBFC kind the intake flow submits, whereas
        // entityDetails.entityType is the entity's sub-classification and is display-only here.
        this.entityTypeDisplay =
          data.entityDetails?.entityType || '';

        this.moduleName =
          data.entityDetails?.moduleName || '';

        this.entityCategory =
          data.entityDetails?.entityCategory || '';

        this.bsrCode =
          data.entityDetails?.bsrCode || '';

        this.entityPincode =
          data.entityDetails?.pincode || '';

        this.entityState =
          data.entityDetails?.state || '';

        this.entityDistrict =
          data.entityDetails?.district || '';

        this.entityCity =
          data.entityDetails?.city || '';

        this.entityBranchName =
          data.entityDetails?.branchName || '';

        this.entityBranchCategory =
          data.entityDetails?.branchCategory || '';

        this.entityAddress =
          data.entityDetails?.entityAddress || '';

        /* Basic Identification */

        this.otherEntityName =
          data.complainDetailsDto?.basicIdentificationDto?.otherEntityName || '';

        this.registrationWithRbiDate =
          data.complainDetailsDto?.basicIdentificationDto?.registrationWithRbiDate || '';

        /* Complaint Classification */

        this.complaintCategory =
          data.complainDetailsDto?.complaintClassification?.complaintCategory || '';

        this.complaintSubCategory1 =
          data.complainDetailsDto?.complaintClassification?.complaintSubCategory1 || '';

        this.complaintSubCategory2 =
          data.complainDetailsDto?.complaintClassification?.complaintSubCategory2 || '';

        this.filingDate =
          data.complainDetailsDto?.complaintClassification?.dateOfFilingComplaint || '';

        this.registrationDateValid =
          !!data.complainDetailsDto?.complaintClassification?.complaintRegistrationDateValid;

        /* Financial Details */

        this.reminderSent =
          !!data.complainDetailsDto?.financialDetails?.reminderSent;

        this.disputedAmount =
          data.complainDetailsDto?.financialDetails?.disputedAmount;

        this.compensationSoughtYesNo =
          !!data.complainDetailsDto?.financialDetails?.compensationSought;

        /* Legal Case Details */

        this.legalCaseFiled =
          !!data.complainDetailsDto?.legalCaseDetails?.legalCaseFiled;

        this.preEnquiryReceived =
          !!data.complainDetailsDto?.legalCaseDetails?.preEnquiryReceived;

        this.highPriority =
          !!data.complainDetailsDto?.legalCaseDetails?.highPriorityComplaint;

        this.loanDisposalAmount =
          data.complainDetailsDto?.legalCaseDetails?.loanDisposalAmount;
        /* Additional Information */

        const additionalInfo = data.complainDetailsDto?.additionalInformation;
        this.additionalComments = additionalInfo?.comments || '';
        this.crpcProposedAction = additionalInfo?.crpcProposedAction || '';
        this.vernacularLanguage = additionalInfo?.vernacularLanguage || '';
        this.vernacular = !!this.vernacularLanguage;

        /* Flags and Indicators */

        const flags = data.complainDetailsDto?.flagsAndIndicators;
        this.pensionComplaint = flags?.complaintRegardingPension === true;
        this.businessCorrespondent = flags?.complaintAgainstBusinessCorrespondent === true;
        this.atmCreditDebitCard = flags?.atmCreditDebitCard === true;
        this.schemeFlag = flags?.schemeFlag || '';
        this.rboCgpcOld = flags?.rboCgpcOld || '';
        this.groundsFlag = flags?.groundsFlag || '';

        /* Complaint Linkage */

        this.freeMarkedComplaint = data.complainDetailsDto?.complaintLinkage?.freeMarkedComplaint === true;
        this.currentComplaintNumber = data.complainDetailsDto?.complaintLinkage?.currentComplaintNumber || '';

        this.applyEligibilityAnswers(data.eligibility);
  }

  private loadEmailThreads() {
    if (!this.actualComplaintNumber) return;
    this.http.get<any>(`${environment.apiBaseUrl}/api/email-simulation/complaints/${this.actualComplaintNumber}/threads`).subscribe({
      next: (res) => {
        const emails = res?.data || res || [];
        this.emailActivities.set(emails.filter((e: any) => (e.status !== 'DRAFT' && e.status !== 'SENT')));
        this.emailDrafts.set(emails.filter((e: any) => e.status === 'DRAFT'));
        this.emailClosedActivities.set(emails.filter((e: any) => e.status === 'SENT'));
      },
      error: () => {
        this.emailActivities.set([]);
        this.emailDrafts.set([]);
        this.emailClosedActivities.set([]);
      }
    });
  }

  
  loadComments() {
    if (!this.actualComplaintNumber) {
      this.assessmentComments.set([]);
      return;
    }
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/comments`).subscribe({
      next: (res) => this.assessmentComments.set(res?.data || []),
      error: () => this.assessmentComments.set([])
    });
  }

  private detectUserRole() {
    const roles = this.auth.getRoles ? this.auth.getRoles() : [];
    if (roles.includes('CRPC_HEAD')) {
      this.userRole.set('HEAD');
    } else if (roles.includes('RBIO_OMBUDSMAN')) {
      this.userRole.set('OMBUDSMAN');
    } else if (roles.includes('RBIO_DEPUTY_OMBUDSMAN')) {
      this.userRole.set('DEPUTY_OMBUDSMAN');
    } else if (roles.includes('RBIO_REVIEWER')) {
      this.userRole.set('REVIEWER');
    } else {
      this.userRole.set('DO');
    }
  }

  private detectUserOffice(username: string) {
    // Office is assigned per-officer via Team Management (OfficerAvailability), not inferred from the username.
    if (!username) return;
    const roles = this.auth.getRoles ? this.auth.getRoles() : [];
    const rbioRole = roles.find((r: string) => r.startsWith('RBIO_')) || 'RBIO_DO';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=${rbioRole}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        const me = (Array.isArray(data) ? data : []).find((u: any) => u.userId === username);
        this.complaintOffice = me?.officeCode || '';
      },
      error: () => { this.complaintOffice = ''; }
    });
  }

  private generateComplaintId(): string {
    return String(Math.floor(10000000 + Math.random() * 90000000));
  }

  loadDeos() {
    this.loadingDeos.set(true);
    this.deoLoadError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/deo`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
      next: (res) => {
        this.deos.set((res?.data || []).map((d: any) => ({
            id: d.userId || String(d.id),
          displayName: d.displayName || d.userId,
            email: d.email || '',
          isActive: d.isActive !== false,
          isOnLeave: d.isOnLeave === true,
            leaveReason: d.leaveReason || '',
            officeCode: d.officeCode || '',
            currentLoad: d.currentLoad ?? 0,
          maxLoad: d.maxThreshold || 20
        })));
          this.loadingDeos.set(false);
          if (this.assignmentMode === 'AUTOMATIC') this.applyAutomaticDeo();
        },
        error: () => {
          this.deos.set([]);
          this.loadingDeos.set(false);
          this.deoLoadError.set('Could not load the DEO roster. Retry, or pick a DEO manually once it loads.');
        }
      });
  }

  /**
   * Automatic assignment goes to the available DEO with the most headroom, so a DEO already at their
   * threshold is not handed more work while a colleague sits idle.
   */
  private applyAutomaticDeo() {
    const pick = [...this.availableDeos()].sort((a, b) => {
      const headroom = (d: DeoUser) => (d.maxLoad || 20) - d.currentLoad;
      return headroom(b) - headroom(a) || a.displayName.localeCompare(b.displayName);
    })[0];
    this.selectedDeoId = pick?.id || '';
    this.selectedDeoName = pick?.displayName || '';
  }

  private loadStates() {
    // Sourced from distinct states in PINCODES. An empty dropdown means that table is unseeded,
    // which is worth seeing rather than papering over with a hardcoded list that would let the
    // officer pick a state the districts lookup then cannot resolve.
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => this.states.set(res?.data || []),
      error: () => this.states.set([])
    });
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
  }

  onPincodeInput(value: string) {
    this.complainantPincode = value;
    if (!value) {
      delete this.fieldErrors['complainantPincode'];
    } else if (!/^\d*$/.test(value)) {
      this.fieldErrors['complainantPincode'] = 'Pincode must contain only digits.';
    } else if (value.length !== 6) {
      this.fieldErrors['complainantPincode'] = 'Pincode must be exactly 6 digits.';
    } else {
      delete this.fieldErrors['complainantPincode'];
      this.pincodeLoading.set(true);
      this.http.get<any[]>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading.set(false);
          if (res && res[0] && res[0].Status === 'Success' && res[0].PostOffice?.length) {
            const po = res[0].PostOffice[0];
            if (po.State) {
              this.complainantState = po.State;
              this.onStateChange(po.State);
            }
            if (po.District) this.complainantDistrict = po.District;
          } else {
            this.fieldErrors['complainantPincode'] = 'No location found for this pincode.';
          }
        },
        // Both paths used to fall back to a bundled 540-row pincode table, which could name a state
        // and district the PINCODES table disagrees with and then fail the districts lookup that
        // depends on it. The lookup is the database's answer or none.
        error: () => {
          this.pincodeLoading.set(false);
          this.fieldErrors['complainantPincode'] = 'Could not look up this pincode. Please try again.';
        }
      });
    }
  }

  onEntityPincodeInput(value: string) {
    this.entityPincode = value;
    if (!value) {
      delete this.fieldErrors['entityPincode'];
    } else if (!/^\d*$/.test(value)) {
      this.fieldErrors['entityPincode'] = 'Pincode must contain only digits.';
    } else if (value.length !== 6) {
      this.fieldErrors['entityPincode'] = 'Pincode must be exactly 6 digits.';
    } else {
      delete this.fieldErrors['entityPincode'];
    }
  }

  onEligibilityEntitySearch(value: string) {
    this.eligibilityEntitySearch = value;
    if (this.eligibilityEntityTimeout) clearTimeout(this.eligibilityEntityTimeout);
    if (!value || value.length < 2) {
      this.showEligibilityEntityDropdown.set(false);
      return;
    }
    this.eligibilityEntityTimeout = setTimeout(() => {
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, {
        params: { search: value }
      }).subscribe({
        next: (res) => {
          this.eligibilityEntityResults.set(res?.data || []);
          this.showEligibilityEntityDropdown.set(true);
        },
        error: () => this.eligibilityEntityResults.set([])
      });
    }, 300);
  }

  selectEligibilityEntity(entity: { id: number; name: string; department: string; entityType: string }) {
    this.eligibilityEntityName = entity.name;
    this.eligibilityEntitySearch = entity.name;
    this.showEligibilityEntityDropdown.set(false);
  }

  onEligibilityEntityBlur() {
    setTimeout(() => this.showEligibilityEntityDropdown.set(false), 200);
  }

  onMarkAllEligible() {
    if (this.markAllEligible) {
      for (const q of this.eligibilityQuestions) {
        if (q.type === 'date') continue;
        if (q.key === 'entityRegulatedByRbi') {
          q.answer = true;
        } else {
          q.answer = false;
        }
      }
    } else {
      for (const q of this.eligibilityQuestions) {
        q.answer = null;
        q.dateValue = null;
      }
    }
  }

  onEntitySearchInput(value: string) {
    this.entitySearchText = value;
    if (this.entitySearchTimeout) clearTimeout(this.entitySearchTimeout);
    if (!value || value.length < 2) {
      this.showEntityDropdown.set(false);
      return;
    }
    this.entitySearchTimeout = setTimeout(() => this.searchEntities(value), 300);
  }

  private searchEntities(query: string) {
    this.entitySearchLoading.set(true);
    this.showEntityDropdown.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, {
      params: { search: query }
    }).subscribe({
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

  selectEntity(entity: EntitySearchResult) {
    const changed = this.regulatedEntityId !== entity.id;

    this.regulatedEntityId = entity.id;
    this.entityName = entity.name;
    this.eligibilityEntityName = entity.name;
    this.entitySearchText = entity.name;
    this.showEntityDropdown.set(false);

    // The four read-only Entity Details fields. All are properties of the entity, so the picked row is
    // their only source — the officer never types them.
    this.moduleName = entity.moduleName || '';
    this.entityCategory = entity.entityCategory || '';
    this.entityTypeDisplay = entity.entityTypeDetail || '';

    if (changed) {
      this.resetEntityDependentFields();
      // The typeahead already carries these two, so they are filled from the picked row rather than
      // waiting on the detail call.
      this.entityState = entity.state || '';
      this.entityCity = entity.city || '';
    }

    this.loadEntityContact(entity.id);
  }

  /**
   * Clears the fields that described the *previous* entity. Keeping them would silently file the new
   * entity under the old one's BSR code, branch and address, which reads as real data rather than as
   * a leftover — so they are cleared and the officer is told which ones went.
   */
  private resetEntityDependentFields() {
    const cleared: string[] = [];
    const clear = (label: string, current: string, set: () => void) => {
      if (current) {
        cleared.push(label);
        set();
      }
    };

    clear('BSR code', this.bsrCode, () => (this.bsrCode = ''));
    clear('pincode', this.entityPincode, () => (this.entityPincode = ''));
    clear('state', this.entityState, () => (this.entityState = ''));
    clear('district', this.entityDistrict, () => (this.entityDistrict = ''));
    clear('city', this.entityCity, () => (this.entityCity = ''));
    clear('branch name', this.entityBranchName, () => (this.entityBranchName = ''));
    clear('branch category', this.entityBranchCategory, () => (this.entityBranchCategory = ''));
    clear('address', this.entityAddress, () => (this.entityAddress = ''));

    this.entityResetNotice.set(cleared.length
      ? `Cleared for the new entity — please re-enter: ${cleared.join(', ')}.`
      : '');
  }

  /**
   * The entity's own nodal officer contact details, which the summary payload does not carry. Shown
   * read-only because they belong to the entity record rather than to this complaint: correcting them
   * is entity master maintenance, not complaint editing.
   */
  private loadEntityContact(entityId: number) {
    this.entityContactLoading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/${entityId}`).subscribe({
      next: (res) => {
        this.entityContact.set(res?.data || null);
        this.entityContactLoading.set(false);
      },
      error: () => {
        // The entity is still selected and still saveable; only the contact panel is unavailable.
        this.entityContact.set(null);
        this.entityContactLoading.set(false);
      }
    });
  }

  onEntityBlur() {
    setTimeout(() => this.showEntityDropdown.set(false), 200);
  }

  // File upload
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];

    if (!allowed.includes(file.type)) {
      this.scanError = 'Only PDF, JPEG, PNG, or TIFF files are accepted.';
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      this.scanError = 'File size must not exceed 2 MB.';
      return;
    }

    this.scannedFile = file;
    this.scanError = '';

    if (file.type === 'application/pdf') {
      const url = URL.createObjectURL(file);
      this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    }
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    const fakeEvent = { target: { files: event.dataTransfer.files } } as unknown as Event;
    this.onFileSelected(fakeEvent);
  }

  isDragOver = false;

  onDragOver(event: DragEvent) {
    event.preventDefault();
  }

  removeFile() {
    this.scannedFile = null;
    this.pdfPreviewUrl.set(null);
    this.ocrComplete.set(false);
  }

  runOcr() {
    if (!this.scannedFile) return;
    this.ocrInProgress.set(true);
    this.scanError = '';

    const formData = new FormData();
    formData.append('file', this.scannedFile);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/ocr/extract`, formData).subscribe({
      next: (res) => {
        const data = res?.data || {};
        if (Object.keys(data).length === 0) {
          this.ocrInProgress.set(false);
          this.scanError = 'AI extraction returned no data. Please fill manually or try again later.';
          return;
        }

        if (data.complainantName) this.complainantName = data.complainantName;
        if (data.complainantAddress) this.complainantAddress = data.complainantAddress;
        if (data.complainantState) this.complainantState = data.complainantState;
        if (data.complainantDistrict) this.complainantDistrict = data.complainantDistrict;
        if (data.complainantPincode) this.complainantPincode = data.complainantPincode;
        if (data.complainantPhone) this.complainantPhone = data.complainantPhone;
        if (data.complainantEmail) this.complainantEmail = data.complainantEmail;
        if (data.subject) this.subject = data.subject;
        if (data.description) this.description = data.description;
        if (data.entityName) {
          this.entityName = data.entityName;
          this.entitySearchText = data.entityName;
        }
        if (data.entityType) this.entityType = data.entityType;
        if (data.category) this.category = data.category;
        if (data.amountInvolved) this.amountInvolved = Number(data.amountInvolved) || null;
        if (data.transactionDate) this.transactionDate = data.transactionDate;

        this.ocrInProgress.set(false);
        this.ocrComplete.set(true);
      },
      error: (err) => {
        this.ocrInProgress.set(false);
        this.scanError = 'AI extraction failed: ' + (err.error?.message || 'Service unavailable. Please fill manually.');
      }
    });
  }

  skipOcr() {
    this.ocrComplete.set(true);
  }

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.subject.trim()) this.fieldErrors['subject'] = 'Subject is required.';
    if (!this.description.trim()) this.fieldErrors['description'] = 'Complaint Details is required.';
    if (!this.modeOfReceipt) this.fieldErrors['modeOfReceipt'] = 'Mode of Receipt is required.';
    if (this.modeOfReceipt === 'PHYSICAL_LETTER' && !this.receivedDate) this.fieldErrors['receivedDate'] = 'Receipt Date is required for Physical Letters.';

    if (!this.complainantName.trim()) this.fieldErrors['complainantName'] = 'Complainant Name is required.';
    if (this.complainantEmail && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.complainantEmail)) {
      this.fieldErrors['complainantEmail'] = 'Enter a valid email address.';
    }
    if (this.complainantPhone && !/^\d{10}$/.test(this.complainantPhone)) {
      this.fieldErrors['complainantPhone'] = 'Enter a valid 10-digit mobile number.';
    }
    if (this.complainantPincode && !/^\d{6}$/.test(this.complainantPincode)) {
      this.fieldErrors['complainantPincode'] = 'Enter a valid 6-digit pincode.';
    }

    if (this.entityPincode && !/^\d{6}$/.test(this.entityPincode)) {
      this.fieldErrors['entityPincode'] = 'Enter a valid 6-digit pincode.';
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  canSubmit(): boolean {
    return this.subject.trim().length > 0 && this.description.trim().length > 0 && this.complainantName.trim().length > 0;
  }

  saveDraft() {
    this.saving.set(true);
    const payload = this.buildPayload('DRAFT');
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/rbio/create-complaint`, payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.draftSaved.set(true);
      },
      error: () => {
        this.saving.set(false);
        this.draftSaved.set(true);
      }
    });
  }

  editDraft() {
    this.draftSaved.set(false);
    this.submitted.set(false);
  }

  sendForApproval(target: 'DEALING_OFFICER' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN') {
    if (this.userRole() === 'DO' && (!this.proposedAction || !this.proposedClause)) {
      alert('Proposed Action and Proposed Clause are mandatory.');
      return;
    }
    this.showApprovalMenu.set(false);
    this.showSendBackMenu.set(false);
    this.approvalTarget.set(target);
    this.approvalAssignmentMode = 'AUTOMATIC';
    this.approvalSelectedName = '';
    this.approvalFilteredUsers = [];
    this.approvalCrpcAction = '';
    this.approvalCrpcClause = '';
    this.systemicIssue = '';
    this.loadApprovalTargetUsers(target);
    this.showApprovalDialog.set(true);
  }

  private loadApprovalTargetUsers(target: string) {
    const roleMap: Record<string, string> = {
      'DEALING_OFFICER': 'RBIO_DO',
      'REVIEWER': 'RBIO_REVIEWER',
      'DEPUTY_OMBUDSMAN': 'RBIO_DEPUTY_OMBUDSMAN',
      'OMBUDSMAN': 'RBIO_OMBUDSMAN'
    };
    const role = roleMap[target];
    if (!role) return;

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=${role}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        const allUsers = (Array.isArray(data) ? data : []).map((u: any) => ({
          id: u.username || u.userId,
          name: u.displayName || `${u.firstName || ''} ${u.lastName || ''}`.trim(),
          officeCode: u.officeCode || ''
        }));
        // Filter by complaint's assigned office
        const officeUsers = this.complaintOffice
          ? allUsers.filter(u => u.officeCode === this.complaintOffice)
          : allUsers;
        const users = officeUsers.length > 0 ? officeUsers : allUsers;
        this.approvalTargetUsers.set(users);
        this.approvalFilteredUsers = users;
        if (this.approvalAssignmentMode === 'AUTOMATIC') {
          this.loadNextAssignee(role);
        }
      },
      error: () => this.approvalTargetUsers.set([])
    });
  }

  private loadNextAssignee(role: string) {
    const officeParam = this.complaintOffice ? `&office=${this.complaintOffice}` : '';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/next-assignee?role=${role}${officeParam}`).subscribe({
      next: (res) => {
        if (res?.success && res?.data) {
          this.approvalSelectedName = res.data.displayName || res.data.username || '';
        } else {
          const users = this.approvalTargetUsers();
          this.approvalSelectedName = users.length > 0 ? users[0].name : '';
        }
      },
      error: () => {
        const users = this.approvalTargetUsers();
        this.approvalSelectedName = users.length > 0 ? users[0].name : '';
      }
    });
  }

  onApprovalAssignmentChange() {
    if (this.approvalAssignmentMode === 'MANUAL') {
      this.approvalSelectedName = '';
      this.approvalFilteredUsers = this.approvalTargetUsers();
    } else {
      this.approvalFilteredUsers = [];
      const roleMap: Record<string, string> = {
        'DEALING_OFFICER': 'RBIO_OFFICER',
        'REVIEWER': 'RBIO_SUPERVISOR',
        'DEPUTY_OMBUDSMAN': 'RBIO_DEPUTY_OMBUDSMAN',
        'OMBUDSMAN': 'RBIO_ADJUDICATOR'
      };
      const role = roleMap[this.approvalTarget()] || '';
      if (role) {
        this.loadNextAssignee(role);
      }
    }
  }

  filterApprovalUsers() {
    const term = this.approvalSelectedName.toLowerCase();
    this.approvalFilteredUsers = this.approvalTargetUsers().filter(u =>
      u.name.toLowerCase().includes(term)
    );
  }

  selectApprovalUser(user: { id: string; name: string }) {
    this.approvalSelectedName = user.name;
    this.approvalFilteredUsers = [];
  }

  openSendBack(target: 'DEALING_OFFICER' | 'REVIEWER') {
    this.sendBackTarget.set(target);
    this.showSendBackMenu.set(false);
    this.sendBackAssignmentMode = 'AUTOMATIC';
    this.sendBackSelectedName = '';
    this.sendBackFilteredUsers = [];
    this.loadSendBackTargetUsers(target);
    this.showSendBackDialog.set(true);
  }

  private loadSendBackTargetUsers(target: string) {
    const roleMap: Record<string, string> = {
      'DEALING_OFFICER': 'RBIO_DO',
      'REVIEWER': 'RBIO_REVIEWER'
    };
    const role = roleMap[target];
    if (!role) return;

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=${role}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        const allUsers = (Array.isArray(data) ? data : []).map((u: any) => ({
          id: u.username || u.userId,
          name: u.displayName || `${u.firstName || ''} ${u.lastName || ''}`.trim(),
          officeCode: u.officeCode || ''
        }));
        const officeUsers = this.complaintOffice
          ? allUsers.filter(u => u.officeCode === this.complaintOffice)
          : allUsers;
        this.sendBackTargetUsers = officeUsers.length > 0 ? officeUsers : allUsers;
        this.sendBackFilteredUsers = this.sendBackTargetUsers;
        if (this.sendBackAssignmentMode === 'AUTOMATIC' && this.sendBackTargetUsers.length > 0) {
          this.sendBackSelectedName = this.sendBackTargetUsers[0].name;
        }
      },
      error: () => { this.sendBackTargetUsers = []; }
    });
  }

  onSendBackAssignmentChange() {
    if (this.sendBackAssignmentMode === 'AUTOMATIC' && this.sendBackTargetUsers.length > 0) {
      this.sendBackSelectedName = this.sendBackTargetUsers[0].name;
    } else {
      this.sendBackSelectedName = '';
      this.sendBackFilteredUsers = this.sendBackTargetUsers;
    }
  }

  filterSendBackUsers() {
    const term = this.sendBackSelectedName.toLowerCase();
    this.sendBackFilteredUsers = this.sendBackTargetUsers.filter(u =>
      u.name.toLowerCase().includes(term)
    );
  }

  selectSendBackUser(user: { id: string; name: string }) {
    this.sendBackSelectedName = user.name;
    this.sendBackFilteredUsers = [];
  }

  get sendBackTargetLabel(): string {
    return this.sendBackTarget() === 'DEALING_OFFICER' ? 'RBIO Dealing Officer' : 'RBIO Reviewer';
  }

  get sendBackStatusLabel(): string {
    return this.sendBackTarget() === 'DEALING_OFFICER' ? 'Sent Back to Dealing Officer' : 'Sent Back to Reviewer';
  }

  confirmSendBack() {
    if (this.sendBackSubmitting()) return;
    this.sendBackSubmitting.set(true);

    this.persistComment();

    const target = this.sendBackTarget();
    const selectedUser = this.sendBackTargetUsers.find(u => u.name === this.sendBackSelectedName);

    const payload = {
      target: target,
      assignedTo: selectedUser?.id || '',
      assignedToName: this.sendBackSelectedName,
      assignmentMode: this.sendBackAssignmentMode,
      performedBy: this.auth.currentUser()?.username || '',
      performedByRole: this.userRole()
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.sendBackSubmitting.set(false);
        this.showSendBackDialog.set(false);
        this.approvalSentTo.set(this.sendBackSelectedName);
        this.justActioned.set(true);
        this.complaintStatus.set('SENT_BACK');
      },
      error: () => {
        this.sendBackSubmitting.set(false);
        this.showSendBackDialog.set(false);
        this.approvalSentTo.set(this.sendBackSelectedName);
        this.justActioned.set(true);
        this.complaintStatus.set('SENT_BACK');
      }
    });
  }

  cancelSendBack() {
    this.showSendBackDialog.set(false);
  }

  get approvalTargetLabel(): string {
    switch (this.approvalTarget()) {
      case 'DEALING_OFFICER': return 'RBIO Dealing Officer';
      case 'REVIEWER': return 'RBIO Reviewer';
      case 'DEPUTY_OMBUDSMAN': return 'RBIO Deputy Ombudsman';
      case 'OMBUDSMAN': return 'RBIO Ombudsman';
    }
  }

  get decisionLabel(): string {
    return this.userRole() === 'OMBUDSMAN' ? 'Ombudsman Decision' : 'Deputy Ombudsman Decision';
  }

  get approvalStatusLabel(): string {
    switch (this.approvalTarget()) {
      case 'DEALING_OFFICER': return this.decisionLabel;
      case 'REVIEWER': return 'Sent to RBIO Reviewer';
      case 'DEPUTY_OMBUDSMAN': return 'Sent to Deputy Ombudsman';
      case 'OMBUDSMAN': return 'Sent to Ombudsman';
    }
  }

  get approvalFromStatus(): string {
    return 'Sent to Deputy Ombudsman';
  }

  get isOmbudsmanDecisionDialog(): boolean {
    return this.approvalTarget() === 'DEALING_OFFICER';
  }

  approvalSubmitting = signal(false);

  confirmApproval() {
    if (this.approvalSubmitting()) return;
    this.approvalSubmitting.set(true);

    this.persistComment();

    const selectedUser = this.approvalTargetUsers().find(u => u.name === this.approvalSelectedName);
    const payload = {
      complaintId: this.complaintId,
      target: this.approvalTarget(),
      assignmentMode: this.approvalAssignmentMode,
      assignedTo: selectedUser?.id || '',
      assignedToName: this.approvalSelectedName,
      crpcAction: this.approvalCrpcAction || null,
      crpcClause: this.approvalCrpcClause || null,
      systemicIssue: this.systemicIssue || null,
      performedBy: this.auth.currentUser()?.username || '',
      performedByRole: this.userRole(),
      proposedAction: this.proposedAction || null,
      proposedClause: this.proposedClause || null
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.approvalSubmitting.set(false);
        this.showApprovalDialog.set(false);
        this.approvalSentTo.set(this.approvalSelectedName);
        this.justActioned.set(true);
        this.complaintStatus.set('SENT');
      },
      error: () => {
        this.approvalSubmitting.set(false);
        this.showApprovalDialog.set(false);
        this.approvalSentTo.set(this.approvalSelectedName);
        this.justActioned.set(true);
        this.complaintStatus.set('SENT');
      }
    });
  }

  cancelApproval() {
    this.showApprovalDialog.set(false);
  }

  confirmCloseComplaint() {
    if (this.finalDecisionSubmitting()) return;
    this.finalDecisionSubmitting.set(true);

    this.persistComment();

    const payload = {
      target: 'CLOSE',
      assignedTo: this.loggedInUserName,
      assignedToName: this.loggedInUserName,
      assignmentMode: 'FINAL_DECISION',
      remarks: this.closureClauseDescription || this.finalDecisionRemarks,
      closureClause: this.closureClause,
      complaintStatusOnPortal: this.complaintStatusOnPortal,
      speakingOrderGenerated: this.speakingOrderGenerated,
      gistOfCase: this.gistOfCase,
      gistOfCaseRegional: this.gistOfCaseRegional,
      performedBy: this.auth.currentUser()?.username || ''
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.finalDecisionSubmitting.set(false);
        this.showFinalDecisionPreview.set(false);
        this.justActioned.set(true);
        this.complaintStatus.set('CLOSED');
        this.approvalSentTo.set('CLOSED');
      },
      error: () => {
        this.finalDecisionSubmitting.set(false);
        this.showFinalDecisionPreview.set(false);
        this.justActioned.set(true);
        this.complaintStatus.set('CLOSED');
        this.approvalSentTo.set('CLOSED');
      }
    });
  }

  openAssignmentDialog() {
    this.formSubmitAttempted = true;
    if (!this.validateForm()) {
      if (this.fieldErrors['subject'] || this.fieldErrors['description'] || this.fieldErrors['modeOfReceipt'] || this.fieldErrors['receivedDate']) {
        this.sectionExpanded.basic = true;
      }
      if (this.fieldErrors['complainantName'] || this.fieldErrors['complainantEmail'] || this.fieldErrors['complainantPhone'] || this.fieldErrors['complainantPincode']) {
        this.sectionExpanded.complainant = true;
      }
      return;
    }
    this.assignError.set('');
    this.showDeoDropdown.set(false);
    this.deoSearch.set('');
    // Refreshed on open so leave state and workload are current at the moment of assignment, not from
    // whenever the screen happened to load.
    this.loadDeos();
    this.showConfirmDialog.set(true);
  }

  onAssignmentModeChange() {
    this.assignError.set('');
    this.showDeoDropdown.set(false);
    this.deoSearch.set('');
    if (this.assignmentMode === 'AUTOMATIC') {
      this.applyAutomaticDeo();
    } else {
      this.selectedDeoId = '';
      this.selectedDeoName = '';
    }
  }

  selectDeo(deo: DeoUser) {
    this.selectedDeoId = deo.id;
    this.selectedDeoName = deo.displayName;
    this.deoSearch.set(deo.displayName);
    this.showDeoDropdown.set(false);
    this.assignError.set('');
  }

  deoOnLeave(): boolean {
    return this.selectedDeo()?.isOnLeave === true;
  }

  /** Percentage of the DEO's own threshold already consumed, for the load bar. */
  deoLoadPercent(deo: DeoUser): number {
    const max = deo.maxLoad || 20;
    return Math.min(100, Math.round((deo.currentLoad / max) * 100));
  }

  confirmAssignment() {
    if (!this.selectedDeoId.trim()) {
      this.assignError.set('Select a DEO to assign this complaint to.');
      return;
    }
    this.assignError.set('');
    this.submitting.set(true);

    const username = this.auth.currentUser()?.username || '';
    const formData = new FormData();
    formData.append('complainantName', this.complainantName);
    formData.append('complainantPhone', this.complainantPhone);
    formData.append('senderEmail', this.complainantEmail);
    formData.append('complainantAddress', this.complainantAddress);
    formData.append('complainantState', this.complainantState);
    formData.append('complainantDistrict', this.complainantDistrict);
    formData.append('complainantPincode', this.complainantPincode);
    formData.append('category', this.category);
    formData.append('entityName', this.entityName);
    formData.append('entityType', this.entityType);
    formData.append('subject', this.subject);
    formData.append('body', this.description);
    formData.append('comments', this.comments);
    if (this.amountInvolved) formData.append('amountInvolved', String(this.amountInvolved));
    if (this.transactionDate) formData.append('transactionDate', this.transactionDate);
    formData.append('modeOfReceipt', this.modeOfReceipt);
    formData.append('isCpgram', String(this.isCpgram));
    if (this.cpgramsNumber) formData.append('cpgramsNumber', this.cpgramsNumber);
    formData.append('status', 'DRAFT');
    formData.append('assignedTo', this.selectedDeoId);
    formData.append('processedBy', username);
    formData.append('source', 'RBIO');
    formData.append('receivedAt', (this.receivedDate || new Date().toISOString().split('T')[0]) + 'T00:00:00');

    if (this.scannedFile) {
      formData.append('attachment', this.scannedFile);
    }

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/physical-letter`, formData).subscribe({
      next: (res) => {
        this.createdComplaintId.set(res?.data?.draftId || res?.data?.id || this.complaintId);
        this.submitting.set(false);
        this.submitted.set(true);
        this.showConfirmDialog.set(false);
      },
      // The dialog stays open on failure: reporting success for a draft the server never stored loses
      // the officer's work silently.
      error: (err) => {
        this.submitting.set(false);
        this.assignError.set(err?.error?.message || err?.error?.error
          || 'Could not assign the complaint. Check your connection and try again.');
      }
    });
  }

  cancelAssignment() {
    this.showConfirmDialog.set(false);
    this.assignError.set('');
    this.showDeoDropdown.set(false);
  }

  // ═══════════════════════ Conciliation ═══════════════════════

  loadConciliation(force = false) {
    const id = this.complaintId;
    if (!id) return;
    if (!force && this.conciliationLoadedFor === String(id)) return;

    this.conciliationLoading.set(true);
    this.conciliationError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}/api/complaints/rbio/${id}/conciliation`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.conciliationLoadedFor = String(id);
          this.applyConciliation(res?.data || {});
          this.conciliationLoading.set(false);
        },
        error: (err) => {
          this.conciliationLoading.set(false);
          this.conciliationError.set(err?.status === 404
            ? 'This complaint could not be found.'
            : 'Could not load the conciliation details. Retry.');
        }
      });
  }

  private applyConciliation(data: any) {
    const current: ConciliationMeeting | null = data.current || null;
    this.conciliationCurrent.set(current);
    // Newest first, and the live meeting is edited in the form above rather than listed as history.
    this.conciliationHistory.set(((data.history || []) as ConciliationMeeting[])
      .filter(m => !current || m.id !== current.id)
      .reverse());

    this.meetingStatus = current?.meetingStatus || 'SCHEDULED';
    this.meetingDate = (current?.meetingDate || '').substring(0, 10);
    this.meetingTime = (current?.meetingTime || '').substring(0, 5);
    this.acceptedByComplainant = current?.acceptedByComplainant ?? null;
    this.acceptedByEntity = current?.acceptedByEntity ?? null;
    this.conductedThroughVc = current?.conductedThroughVc ?? null;
    this.meetingComments = current?.meetingComments || '';
    this.conciliationComments = current?.comments || '';
    this.conciliationFieldErrors.set({});
  }

  private validateConciliation(): boolean {
    const errors: Record<string, string> = {};
    if (this.conciliationRequiresSchedule) {
      if (!this.meetingDate) errors['meetingDate'] = 'Meeting date is required while the meeting is open.';
      if (!this.meetingTime) errors['meetingTime'] = 'Meeting time is required while the meeting is open.';
    }
    if (this.meetingComments.length > 4000) errors['meetingComments'] = 'Maximum 4000 characters.';
    if (this.conciliationComments.length > 4000) errors['comments'] = 'Maximum 4000 characters.';
    this.conciliationFieldErrors.set(errors);
    return Object.keys(errors).length === 0;
  }

  saveConciliation() {
    if (this.isReadOnlyViewer() || !this.complaintId) return;
    this.conciliationSaved.set(false);
    this.conciliationError.set('');
    if (!this.validateConciliation()) return;

    // Every field is sent so clearing one actually clears it: the backend treats an absent key as
    // "leave alone" and a present null as "clear".
    const payload = {
      meetingStatus: this.meetingStatus,
      meetingDate: this.meetingDate || null,
      meetingTime: this.meetingTime || null,
      acceptedByComplainant: this.acceptedByComplainant,
      acceptedByEntity: this.acceptedByEntity,
      conductedThroughVc: this.conductedThroughVc,
      meetingComments: this.meetingComments || null,
      comments: this.conciliationComments || null,
    };

    this.conciliationSaving.set(true);
    this.http.put<any>(`${environment.apiBaseUrl}/api/complaints/rbio/${this.complaintId}/conciliation`, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.applyConciliation(res?.data || {});
          this.conciliationSaving.set(false);
          this.conciliationSaved.set(true);
        },
        error: (err) => {
          this.conciliationSaving.set(false);
          this.conciliationError.set(err?.error?.message
            || 'Could not save the conciliation details. Check the fields and try again.');
        }
      });
  }

  resetConciliation() {
    this.applyConciliation({
      current: this.conciliationCurrent(),
      history: this.conciliationHistory(),
    });
    this.conciliationError.set('');
    this.conciliationSaved.set(false);
  }

  /** Appends dictated text without the stray leading space an inline concat binding leaves behind. */
  appendSpeech(field: 'meetingComments' | 'conciliationComments', text: string) {
    const spoken = (text || '').trim();
    if (!spoken) return;
    const existing = this[field];
    this[field] = existing ? `${existing} ${spoken}` : spoken;
  }

  conciliationStatusLabel(status: string | null | undefined): string {
    return this.conciliationStatuses.find(s => s.value === status)?.label || status || '—';
  }

  yesNoLabel(value: boolean | null | undefined): string {
    if (value === null || value === undefined) return 'Not answered';
    return value ? 'Yes' : 'No';
  }

  private buildPayload(status: string): Record<string, string> {
    return {
      complainantName: this.complainantName,
      complainantEmail: this.complainantEmail,
      complainantPhone: this.complainantPhone,
      complainantAddress: this.complainantAddress,
      subject: this.subject,
      description: this.description,
      entityName: this.entityName,
      priority: 'MEDIUM',
      filingType: this.modeOfReceipt,
      isCpgram: String(this.isCpgram),
      cpgramsNumber: this.cpgramsNumber,
      createdBy: this.auth.currentUser()?.username || '',
      status
    };
  }

  // A getter rather than a computed(): the filter inputs are plain ngModel-bound fields, not signals,
  // so there is nothing for a computed to track.
  get filteredNodalRecords(): NodalRecord[] {
    const matches = (value: unknown, filter: string) =>
      !filter.trim() || String(value ?? '').toLowerCase().includes(filter.trim().toLowerCase());

    return this.nodalRecords().filter(r =>
      matches(r.recordNumber, this.nodalFilterRecordNumber) &&
      matches(r.subject, this.nodalFilterSubject) &&
      matches(r.bankName, this.nodalFilterBank) &&
      matches(r.slaDays, this.nodalFilterSla) &&
      matches(r.assignedTo, this.nodalFilterAssigned) &&
      matches(r.statusLabel, this.nodalFilterStatus)
    );
  }

  openNodalOfficerTab() {
    this.assessmentTab.set('nodal-officer');
    this.loadNodalRecords();
  }

  loadNodalRecords() {
    this.loadingNodalRecords.set(true);
    this.nodalRecordsError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/nodal-records`).subscribe({
      next: (res) => {
        // statusLabel is derived here rather than server-side so the worklist and the status pills
        // elsewhere on this screen stay driven by one mapping.
        this.nodalRecords.set((res?.data || []).map((r: any) => ({
          ...r,
          statusLabel: this.formatStatusLabel(r.status)
        })));
        this.loadingNodalRecords.set(false);
      },
      error: () => {
        this.nodalRecords.set([]);
        this.nodalRecordsError.set('Could not load nodal officer records.');
        this.loadingNodalRecords.set(false);
      }
    });
  }

  openNodalDetail(record: NodalRecord) {
    this.selectedNodalRecord.set(record);
    // Every field is reset from the record rather than left as it was. These are plain component
    // fields, not per-record state, so without this the previously opened record's amounts stay
    // sitting in the boxes and look like this record's assessment.
    this.nodalStatusCode = record.status || '';
    this.nodalAdvisoryDate = record.advisoryComplianceDate || '';
    this.nodalDisputeAmount = record.disputeAmount ?? null;
    this.nodalCompensationLoss = record.compensationLoss ?? null;
    this.nodalCompensationMental = record.compensationMental ?? null;
    this.nodalAwardImplementationDate = record.awardImplementationDate || '';
    this.nodalAwardAcceptanceDate = record.awardAcceptanceDate || '';
    // Set by the backend when the 13(1) notice is actually issued, so it stays blank until then
    // instead of showing a rolling "today + 15 days" that moved on every page load.
    this.nodal131ComplyDate = record.notice131ComplyDate || '';
    this.sendToREError.set('');
    this.nodalDetailView.set(true);
    this.loadNodalComments(record.recordNumber);
  }

  private loadNodalComments(recordNumber: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/nodal-records/${recordNumber}/comments`).subscribe({
      next: (res) => this.nodalComments.set(res?.data || []),
      error: () => this.nodalComments.set([])
    });
  }

  postNodalComment(target: 'NO' | 'PNO') {
    const text = target === 'NO' ? this.nodalCommentToNO.trim() : this.nodalCommentToPNO.trim();
    if (!text || this.nodalCommentsSubmitting) return;
    const record = this.selectedNodalRecord();
    if (!record) return;

    this.nodalCommentsSubmitting = true;
    const payload = {
      complaintNumber: record.complaintNumber || '',
      text,
      author: this.loggedInUserName || 'Unknown',
      initials: (this.loggedInUserName || 'U').substring(0, 2).toUpperCase(),
      target,
      color: target === 'NO' ? '#7c3aed' : '#2563eb'
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/nodal-records/${record.recordNumber}/comments`, payload).subscribe({
      next: (res) => {
        this.nodalComments.set([res?.data, ...this.nodalComments()]);
        if (target === 'NO') this.nodalCommentToNO = ''; else this.nodalCommentToPNO = '';
        this.nodalCommentsSubmitting = false;
      },
      error: () => { this.nodalCommentsSubmitting = false; }
    });
  }

  nodalCommentTimeAgo(createdAt: string): string {
    if (!createdAt) return '';
    const then = new Date(createdAt).getTime();
    if (isNaN(then)) return createdAt;
    const diffMs = Date.now() - then;
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins} min${mins > 1 ? 's' : ''} ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs} hr${hrs > 1 ? 's' : ''} ago`;
    const days = Math.floor(hrs / 24);
    return `${days} day${days > 1 ? 's' : ''} ago`;
  }

  closeNodalDetail() {
    this.nodalDetailView.set(false);
    this.selectedNodalRecord.set(null);
    this.sendToREError.set('');
  }

  sendToRE() {
    const record = this.selectedNodalRecord();
    if (!record || !this.nodalStatusCode || this.sendingToRE()) return;

    this.sendingToRE.set(true);
    this.sendToREError.set('');

    // notice131ComplyDate is not sent: Clause 13(1)'s window is the backend's to set, once, when the
    // notice is issued.
    const payload = {
      status: this.nodalStatusCode,
      advisoryComplianceDate: this.nodalAdvisoryDate || null,
      disputeAmount: this.nodalDisputeAmount,
      compensationLoss: this.nodalCompensationLoss,
      compensationMental: this.nodalCompensationMental,
      awardImplementationDate: this.nodalAwardImplementationDate || null,
      awardAcceptanceDate: this.nodalAwardAcceptanceDate || null
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/nodal-records/${record.recordNumber}/forward-to-re`,
      payload
    ).subscribe({
      next: (res) => {
        // The response is the updated row, so the worklist is patched in place rather than refetched.
        const updated: NodalRecord = { ...res.data, statusLabel: this.formatStatusLabel(res.data.status) };
        this.nodalRecords.set(this.nodalRecords()
          .map(r => r.recordNumber === updated.recordNumber ? updated : r));
        this.sendingToRE.set(false);
        this.closeNodalDetail();
      },
      error: (err) => {
        // Shown, not swallowed: the compensation caps and the required-date rules are enforced on the
        // server, so this message is the officer's only sight of why the record was refused.
        this.sendToREError.set(err?.error?.message
          || 'Could not forward this record to the regulated entity.');
        this.sendingToRE.set(false);
      }
    });
  }

  amountToWords(amount: number | null): string {
    if (!amount || amount <= 0) return '';
    const ones = ['', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine',
      'Ten', 'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen'];
    const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

    const twoDigits = (n: number): string => {
      if (n < 20) return ones[n];
      return tens[Math.floor(n / 10)] + (n % 10 ? ' ' + ones[n % 10] : '');
    };
    const threeDigits = (n: number): string => {
      if (n < 100) return twoDigits(n);
      return ones[Math.floor(n / 100)] + ' Hundred' + (n % 100 ? ' ' + twoDigits(n % 100) : '');
    };

    let n = Math.floor(amount);
    if (n === 0) return 'Zero rupees';

    const crore = Math.floor(n / 10000000); n %= 10000000;
    const lakh = Math.floor(n / 100000); n %= 100000;
    const thousand = Math.floor(n / 1000); n %= 1000;
    const hundred = n;

    const parts: string[] = [];
    if (crore) parts.push(threeDigits(crore) + ' Crore');
    if (lakh) parts.push(threeDigits(lakh) + ' Lakh');
    if (thousand) parts.push(threeDigits(thousand) + ' Thousand');
    if (hundred) parts.push(threeDigits(hundred));

    const phrase = (parts.join(' ') + ' rupees').toLowerCase();
    return phrase.charAt(0).toUpperCase() + phrase.slice(1);
  }

  private persistComment() {
    if (!this.assessmentComment.trim()) return;
    const commentPayload = {
      text: this.assessmentComment.trim(),
      author: this.loggedInUserName || 'Unknown',
      initials: (this.loggedInUserName || 'U').substring(0, 2).toUpperCase(),
      role: this.userRole(),
      color: '#6366f1'
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/comments`, commentPayload).subscribe({
      next: (res) => {
        this.assessmentComments.set([...this.assessmentComments(), res?.data || commentPayload]);
        this.assessmentComment = '';
      },
      error: () => {
        this.assessmentComment = '';
      }
    });
  }

  formatCommentDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    const day = d.getDate().toString().padStart(2, '0');
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const mon = months[d.getMonth()];
    const year = d.getFullYear();
    const hrs = d.getHours().toString().padStart(2, '0');
    const mins = d.getMinutes().toString().padStart(2, '0');
    return `${day} ${mon} ${year}, ${hrs}:${mins}`;
  }

  getStatusLabel(): string {
    const status = this.complaintStatus();
    const labels: Record<string, string> = {
      'NEW_COMPLAINT': 'New Complaint',
      'SENT_TO_REVIEWER': 'Sent to Reviewer',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'Sent to Dy. Ombudsman',
      'SENT_TO_OMBUDSMAN': 'Sent to Ombudsman',
      'SENT_TO_DO': 'Sent to DO',
      'SENT_BACK': 'Sent Back',
      'SENT': 'Forwarded',
      'RESOLVED': 'Resolved',
      'CLOSED': 'Closed',
      'VIEW_ONLY': 'Closed',
      'REGISTERED': 'Registered'
    };
    return labels[status] || status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  getStatusClass(): string {
    const status = this.complaintStatus();
    if (['RESOLVED', 'CLOSED', 'VIEW_ONLY'].includes(status)) return 'grey';
    if (['SENT_BACK', 'SENT_TO_DO'].includes(status)) return 'orange';
    if (['SENT_TO_REVIEWER', 'SENT_TO_DEPUTY_OMBUDSMAN', 'SENT_TO_OMBUDSMAN', 'SENT'].includes(status)) return 'blue';
    return 'green';
  }

  getAvailableClauses(): string[] {
    const role = this.userRole();
    const action = this.proposedAction;

    // DO uses Ombudsman clauses
    const isOmbudsman = role === 'DO' || role === 'OMBUDSMAN';

    if (isOmbudsman) {
      switch (action) {
        case 'NON_MAINTAINABLE':
          return ['1(3)', '16(1)(a)10(2)(a)', '16(1)(a)10(2)(b)', '16(1)(a)10(2)(c)', '16(1)(a)10(2)(d)', '16(1)(a)10(2)(e)', '16(1)(a)10(2)(f)', '16(1)(a)10(2)(g)', '16(1)(a)10(2)(h)', '16(1)(a)10(2)(i)', '16(1)(a)10(1)(a)', '16(1)(a)10(1)(b)', '16(1)(a)10(1)(c)', '16(1)(a)10(1)(d)', '16(1)(a)10(1)(e)', '16(1)(a)10(1)(f)', '16(1)(a)10(1)(g)', '16(1)(a)10(1)(h)', '16(1)(a)10(1)(i)', '16(1)(a)10(1)(j)', '16(1)(a)10(1)(k)', '16(1)(a)10(1)(l)', '16(1)(b)', '16(1)(c)'];
        case 'MAINTAINABLE':
          return ['16(2)(a)', '16(2)(b)', '16(2)(c)', '16(2)(d)', '16(2)(e)', '16(2)(f)', '15(4)', '15(5)', '14(8)(a)', '14(8)(b)', '14(8)(c)', '14(8)(d)', '14(8)(e)', '15 1(a)', '15 1(b)'];
        default:
          return [];
      }
    } else {
      // Deputy Ombudsman or Reviewer
      switch (action) {
        case 'NON_MAINTAINABLE':
          return ['1(3)', '16(1)(a)10(2)(a)', '16(1)(a)10(2)(b)', '16(1)(a)10(2)(c)', '16(1)(a)10(2)(d)', '16(1)(a)10(2)(e)', '16(1)(a)10(2)(f)', '16(1)(a)10(2)(g)', '16(1)(a)10(2)(h)', '16(1)(a)10(2)(i)', '16(1)(a)10(1)(a)', '16(1)(a)10(1)(b)', '16(1)(a)10(1)(c)', '16(1)(a)10(1)(d)', '16(1)(a)10(1)(e)', '16(1)(a)10(1)(f)', '16(1)(a)10(1)(g)', '16(1)(a)10(1)(h)', '16(1)(a)10(1)(i)', '16(1)(a)10(1)(l)', '16(1)(b)'];
        case 'MAINTAINABLE':
          return ['14(8)(a)', '14(8)(b)', '14(8)(c)'];
        default:
          return [];
      }
    }
  }

  getFilteredClauses(): string[] {
    const clauses = this.getAvailableClauses();
    if (!this.clauseSearch) return clauses;
    const search = this.clauseSearch.toLowerCase();
    return clauses.filter(c => c.toLowerCase().includes(search));
  }

  getAllClosureClauses(): string[] {
    return ['1(3)', '16(1)(a)10(2)(a)', '16(1)(a)10(2)(b)', '16(1)(a)10(2)(c)', '16(1)(a)10(2)(d)', '16(1)(a)10(2)(e)', '16(1)(a)10(2)(f)', '16(1)(a)10(2)(g)', '16(1)(a)10(2)(h)', '16(1)(a)10(2)(i)', '16(1)(a)10(1)(a)', '16(1)(a)10(1)(b)', '16(1)(a)10(1)(c)', '16(1)(a)10(1)(d)', '16(1)(a)10(1)(e)', '16(1)(a)10(1)(f)', '16(1)(a)10(1)(g)', '16(1)(a)10(1)(h)', '16(1)(a)10(1)(i)', '16(1)(a)10(1)(j)', '16(1)(a)10(1)(k)', '16(1)(a)10(1)(l)', '16(1)(b)', '16(1)(c)', '16(2)(a)', '16(2)(b)', '16(2)(c)', '16(2)(d)', '16(2)(e)', '16(2)(f)', '15(4)', '15(5)', '14(8)(a)', '14(8)(b)', '14(8)(c)', '14(8)(d)', '14(8)(e)', '15 1(a)', '15 1(b)'];
  }

  getFilteredClosureClauses(): string[] {
    const clauses = this.getAllClosureClauses();
    if (!this.closureClauseSearch) return clauses;
    const search = this.closureClauseSearch.toLowerCase();
    return clauses.filter(c => c.toLowerCase().includes(search));
  }

  private getSummaryEditableFields(): string[] {
    return [
      'complainantName', 'complainantEmail', 'complainantPhone', 'complainantAddress',
      'complainantState', 'complainantDistrict', 'complainantPincode',
      'subject', 'description', 'comments', 'modeOfReceipt', 'receivedDate',
      'isCpgram', 'cpgramsNumber', 'proposedComplaintType', 'eligibilityEntityName',
      // regulatedEntityId belongs here with entityName: restoring the name but not the id would leave
      // a cancelled edit pointing at the entity the officer backed out of.
      'entityName', 'regulatedEntityId', 'entitySearchText', 'entityTypeDisplay',
      // Picking an entity rewrites the read-only Entity Details fields, so they have to be restorable.
      'entityType', 'moduleName', 'entityCategory', 'bsrCode', 'entityPincode',
      'entityState', 'entityDistrict', 'entityCity', 'entityBranchName',
      'entityBranchCategory', 'entityAddress', 'cosmosCode',
      'otherEntityName', 'registrationWithRbiDate',
      'complaintCategory', 'complaintSubCategory1', 'complaintSubCategory2',
      'filingDate', 'disputedAmount', 'loanDisposalAmount',
      'additionalComments', 'crpcProposedAction', 'vernacularLanguage',
      'schemeFlag', 'rboCgpcOld', 'groundsFlag', 'currentComplaintNumber',
      'declarationAccepted'
    ];
  }

  enterEditMode() {
    const snapshot: Record<string, any> = {};
    for (const key of this.getSummaryEditableFields()) {
      snapshot[key] = (this as any)[key];
    }
    snapshot['eligibilityQuestions'] = this.eligibilityQuestions.map(q => ({ ...q }));
    this.editSnapshot = snapshot;
    this.entityResetNotice.set('');
    this.editMode.set(true);
  }

  cancelEdit() {
    if (this.editSnapshot) {
      const pickedEntityId = this.regulatedEntityId;
      for (const key of this.getSummaryEditableFields()) {
        (this as any)[key] = this.editSnapshot[key];
      }
      this.eligibilityQuestions = (this.editSnapshot['eligibilityQuestions'] as any[]).map(q => ({ ...q }));
      this.editSnapshot = null;
      if (pickedEntityId !== this.regulatedEntityId) {
        if (this.regulatedEntityId) this.loadEntityContact(this.regulatedEntityId);
        else this.entityContact.set(null);
    }
    }
    this.entityResetNotice.set('');
    this.showEntityDropdown.set(false);
    this.editMode.set(false);
  }

  private applyEligibilityAnswers(eligibility: any) {
    if (!eligibility) return;
    this.proposedComplaintType = eligibility.proposedComplaintType || this.proposedComplaintType;
    for (const q of this.eligibilityQuestions) {
      const remoteKey = ELIGIBILITY_ANSWER_KEYS[q.key];
      if (!remoteKey) continue;
      const value = eligibility[remoteKey];
      if (q.type === 'date') {
        q.dateValue = value || null;
      } else {
        q.answer = value === null || value === undefined ? null : value === true;
      }
    }
  }

  /**
   * Mirrors the nested shape GET /api/complaints/rbio/{id}/summary returns, which is what its PUT
   * accepts. navBarDto is deliberately omitted: status is workflow-owned and sending it here would let
   * the form overwrite a transition the officer did not make.
   */
  private buildSummaryPayload(): Record<string, any> {
    const eligibility: Record<string, any> = {
      proposedComplaintType: this.proposedComplaintType || null
    };
    for (const q of this.eligibilityQuestions) {
      const remoteKey = ELIGIBILITY_ANSWER_KEYS[q.key];
      if (!remoteKey) continue;
      eligibility[remoteKey] = q.type === 'date' ? (q.dateValue || null) : q.answer;
    }

    return {
      basicDetailsDto: {
        subject: this.subject || null,
        emailId: this.complainantEmail || null,
        complainantName: this.complainantName || null,
        receiptDate: this.receivedDate || null,
        modeOfReceipt: this.modeOfReceipt || null,
        comments: this.comments || null,
        complaintCpgram: this.isCpgram,
        cpgramNumber: this.cpgramsNumber || null,
        complainDetails: this.description || null
      },
      eligibility,
      entityDetails: {
        // Without this the entity name changes but regulatedEntityId does not, so entityType keeps
        // resolving from the old entity and the nodal officer record is created against it.
        id: this.regulatedEntityId,
        entityName: this.entityName || null,
        moduleName: this.moduleName || null,
        entityCategory: this.entityCategory || null,
        bsrCode: this.bsrCode || null,
        pincode: this.entityPincode || null,
        country: this.entityCountry || null,
        state: this.entityState || null,
        district: this.entityDistrict || null,
        city: this.entityCity || null,
        branchName: this.entityBranchName || null,
        branchCategory: this.entityBranchCategory || null,
        branchCenterName: this.branchCenterName || null,
        entityAddress: this.entityAddress || null
      },
      complainDetailsDto: {
        basicIdentificationDto: {
          otherEntityName: this.otherEntityName || null,
          registrationWithRbiDate: this.registrationWithRbiDate || null
        },
        complaintClassification: {
          complaintCategory: this.complaintCategory || null,
          complaintSubCategory1: this.complaintSubCategory1 || null,
          complaintSubCategory2: this.complaintSubCategory2 || null,
          complaintRegistrationDateValid: this.registrationDateValid,
          dateOfFilingComplaint: this.filingDate || null
        },
        financialDetails: {
          reminderSent: this.reminderSent,
          disputedAmount: this.disputedAmount,
          compensationSought: this.compensationSoughtYesNo ? 1 : 0
        },
        legalCaseDetails: {
          legalCaseFiled: this.legalCaseFiled,
          preEnquiryReceived: this.preEnquiryReceived,
          highPriorityComplaint: this.highPriority,
          loanDisposalAmount: this.loanDisposalAmount
        },
        additionalInformation: {
          comments: this.additionalComments || null,
          crpcProposedAction: this.crpcProposedAction || null,
          vernacularLanguage: this.vernacular ? (this.vernacularLanguage || null) : null
        },
        flagsAndIndicators: {
          complaintRegardingPension: this.pensionComplaint,
          complaintAgainstBusinessCorrespondent: this.businessCorrespondent,
          atmCreditDebitCard: this.atmCreditDebitCard,
          schemeFlag: this.schemeFlag || null,
          rboCgpcOld: this.rboCgpcOld || null,
          groundsFlag: this.groundsFlag || null
        },
        complaintLinkage: {
          freeMarkedComplaint: this.freeMarkedComplaint
        }
      }
    };
  }

  updateComplaint() {
    this.saving.set(true);
    this.saveError.set('');
    this.http.put<any>(`${environment.apiBaseUrl}/api/complaints/rbio/${this.complaintId}/summary`,
      this.buildSummaryPayload()).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.editSnapshot = null;
        this.editMode.set(false);
        // The PUT returns the re-read summary, so the form shows what was actually stored rather than
        // what was typed - dates and amounts come back normalised.
        if (res?.data) this.applySummary(res.data);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(err?.error?.message || 'Could not save the complaint. Your changes are still here.');
      }
    });
  }

  // Email Communication methods
  selectEmailActivity(activity: any) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/email-simulation/thread/${activity?.threadId}`).subscribe({
      next: (res) => {
        this.selectedEmailActivity.set(res);
        this.emailComposeMode.set(false);
      },
      error: () => { }
    });
  }

  createNewEmail() {
    this.emailComposeMode.set(true);
    this.emailFrom = 'cmssupportngp@rbi.org.in';
    this.emailTo = '';
    this.emailCc = '';
    this.emailBcc = '';
    this.emailSubject = '';
    this.emailBody = '';
    this.selectedEmailActivity.set(null);
  }

  saveEmailDraft() {
    const payload = {
      from: this.emailFrom,
      to: this.emailTo,
      subject: this.emailSubject,
      body: this.emailBody,
      status: 'DRAFT'
    };
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/emails`, payload).subscribe({
      next: (res) => {
        const draft = { id: res?.data?.id || Date.now(), subject: this.emailSubject, date: new Date().toLocaleDateString('en-GB').replace(/\//g, '-'), from: this.emailFrom, to: this.emailTo, body: this.emailBody };
        this.emailDrafts.set([draft, ...this.emailDrafts()]);
      },
      error: () => { }
    });
    this.emailComposeMode.set(false);
  }

  sendEmail() {
    if (!this.emailTo || !this.emailSubject) return;
    const payload = {
      from: this.emailFrom,
      to: this.emailTo,
      subject: this.emailSubject,
      body: this.emailBody,
      status: 'SENT'
    };
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/emails`, payload).subscribe({
      next: (res) => {
        const newActivity = {
          id: res?.data?.id || Date.now(),
          subject: this.emailSubject,
          date: new Date().toLocaleDateString('en-GB').replace(/\//g, '-'),
          from: this.emailFrom,
          to: this.emailTo,
          assignedTo: this.loggedInUserName,
          dueDate: '',
          body: `<p>${this.emailBody}</p>`,
          attachments: [] as { name: string; size: string }[]
        };
        this.emailActivities.set([newActivity, ...this.emailActivities()]);
        this.selectedEmailActivity.set(newActivity);
      },
      error: () => { }
    });
    this.emailComposeMode.set(false);
  }

  goBack() {
    this.navService.goBack(['/rbio']);
  }

  goToDraft() {
    this.router.navigate(['/crpc/draft', this.createdComplaintId()]);
  }

  handleAttachmentsClick(): void {
    this.attachmentsPanelOpen.set(!this.attachmentsPanelOpen());
  
    console.log('Sidebar clicked. Is open state:', this.attachmentsPanelOpen());
  
    if (this.attachmentsPanelOpen()) {
      console.log('Sidebar is opening! Triggering GET API call...');
      this.fetchAttachments(); 
    }
  }
  
  fetchAttachments(): void {
    // COMPLAINT_ATTACHMENTS is keyed on the numeric complaint id, not the display complaint number.
    if (!this.complaintId) return;

    this.loadingSidebarAttachments.set(true);

    this.http.get<any[]>(`${environment.apiBaseUrl}/api/files/complaint/${this.complaintId}`)
      .subscribe({
        next: (data) => {
          const files = (data as any)?.data || data || [];

          const processed = files.map((f: any) => ({
            id: f.id,
            // originalName keeps the stored UUID filename out of the UI.
            name: f.originalName || f.fileName || 'Untitled_File',
            type: (f.contentType || '').toLowerCase(),
            size: f.fileSize ? `${Math.round(f.fileSize / 1024)} KB` : 'Unknown Size',
            uploadedAt: f.uploadedAt || '',
            uploadedBy: f.uploadedBy || ''
          }));

          this.sidebarAttachments.set(processed);
          this.loadingSidebarAttachments.set(false);
        },
        error: (err) => {
          console.error('Failed to load attachments array:', err);
          this.sidebarAttachments.set([]);
          this.loadingSidebarAttachments.set(false);
        }
      });
  }  

  onDocumentUpload(event: Event): void {
    const element = event.target as HTMLInputElement;
    const fileList: FileList | null = element.files;

    if (!fileList || fileList.length === 0) return;

    this.uploadingDocument.set(true);

   
    const formData = new FormData();
    formData.append('complaintNumber', this.complaintNumber ? this.complaintNumber.trim() : '');
    let id = this.complaintId ?? ''
    formData.append('complaintId', id as string);
    
     formData.append('file', fileList[0]); 


    this.http.post(`${environment.apiBaseUrl}/api/files/upload`, formData)
      .subscribe({
        next: (res) => {
          console.log('Upload successful!');
          this.uploadingDocument.set(false);
          
          element.value = '';

          this.fetchAttachments();
        },  
        error: (err) => {
          console.error('Upload failed:', err);
          this.uploadingDocument.set(false);
        }
      });
  }


  downloadSidebarAttachment(file: any): void {
    if (file.id) {
      window.open(`${environment.apiBaseUrl}/api/files/download/${file.id}`, '_blank');
    }
  }

  downloadAllAttachments()
   {}


previewSidebarAttachment(file: any): void {
  const fileId = file.id;
  
  if (!fileId) {
    console.warn('Cannot preview document: Missing unique file asset ID.');
    return;
  }

  const streamUrl = `${environment.apiBaseUrl}/api/files/stream/${fileId}`;
  console.log(`Routing stream pipeline for file [${fileId}] ->`, streamUrl);
  window.open(streamUrl, '_blank');
}

handleHistoryClick(): void {
  // 1. Toggle visibility panel layout display open or closed
  this.showHistoryPanel.set(!this.showHistoryPanel());

  // 2. Automatically load data from backend server if panel has been opened
  if (this.showHistoryPanel()) {
    this.fetchComplaintHistory();
  }
}


fetchComplaintHistory(): void {
  const targetId = this.complaintId ?? '';
  console.log('targeId',targetId);

  if (!targetId) {
    console.warn('History API Blocked: No valid complaintId found.');
    return;
  }

  this.loadingHistory.set(true);
  const targetUrl = `${environment.apiBaseUrl}/api/complaints/${targetId}/timeline`;

  this.http.get<any[]>(targetUrl)
    .subscribe({
      next: (data) => {
        const entries = (data as any)?.data || data || [];
        const mappedEntries = entries.map((item: any) => {
          // Dynamic configuration profile based on action status strings
          const status = (item.toStatus || item.action || 'MODIFIED').toUpperCase();
          let color = '#4e73df'; // Primary blue default color
          
          if (status === 'CLOSED' || status === 'RESOLVED') color = '#1cc88a'; 
          else if (status === 'REJECTED' || status === 'FAILED') color = '#e74a3b'; 
          else if (status === 'FORWARDED' || status === 'PENDING') color = '#f6c23e'; 

          const rawDate = item.performedAt || item.updatedAt || item.actionDate;
          const formattedDate = rawDate ? new Date(rawDate).toLocaleString() : 'N/A'

          return {
            id: item.id,
            statusLabel: item.toStatus || item.action,
            statusColor: color,
            modifiedOn: formattedDate,
            modifiedBy: item.performedBy || 'System User',
            assignedOfficer: item.assignedOfficer || item.performedBy || 'Not Assigned',
            officerAvatarColor: color,
            description: item.remarks || item.comments || item.description || ''
          };
        });

        this.historyEntries.set(mappedEntries);
        this.loadingHistory.set(false);
      },
      error: (err) => {
        console.error(`Failed to read history records tracking logs from path: ${targetUrl}`, err);
        this.historyEntries.set([]);
        this.loadingHistory.set(false);
      }
    });
}

}
