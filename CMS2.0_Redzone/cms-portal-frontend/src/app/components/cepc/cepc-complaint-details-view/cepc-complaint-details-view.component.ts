import { Component, OnInit, ViewChild, inject, signal, computed, WritableSignal, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ButtonModule } from 'primeng/button';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NavigationService } from '../../../services/navigation.service';
import { AssignmentTarget, CepcContextService } from '../../../services/cepc-context.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcHeaderComponent } from '../cepc-header/cepc-header.component';
import { CepcSidebarComponent } from '../cepc-sidebar/cepc-sidebar.component';
import { CepcComplaintMetaRowComponent } from '../cepc-complaint-meta-row/cepc-complaint-meta-row.component';
import { RightSidePanelComponent } from '../right-side-panel/right-side-panel.component';
import { Attachment, HistoryEntry, LegalCase, LegalCaseFormShape, RightSidePanelKey } from '../right-side-panel/right-side-panel.types';
import { CepcConciliationWorkflowComponent, ConciliationStatusChange } from '../complaint-workflow/conciliation/cepc-conciliation-workflow.component';
import { CepcForwardWorkflowComponent, ForwardCompleted } from '../complaint-workflow/forward/cepc-forward-workflow.component';
import { EligibilityQuestionItem, PanelState, NodalRecord, ContactPerson, DeoUser, EntitySearchResult, EntityDetail, ComplaintEmail, ComplaintEmailThread } from '../model/cepc.model';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';
// import { CepcHeader } from '../cepc-header/cepc-header';

// eligibilityQuestions[].key -> the key the `eligibility` section of /api/complaints/{department}/{id}/summary
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

const DEFAULT_EMAIL_FROM = 'cmssupportngp@rbi.org.in';
const EMAIL_STATUS_DRAFT = 'DRAFT';
/** Queued for dispatch: persisted, but cms-notification-service has not resolved it yet. */
const EMAIL_STATUS_PENDING = 'PENDING';
const EMAIL_STATUS_SENT = 'SENT';
const EMAIL_STATUS_FAILED = 'FAILED';

/** Re-poll delays, in ms, while a mail is still queued. Bounded: three attempts, then stop. */
const EMAIL_POLL_DELAYS_MS = [3000, 6000, 12000];

@Component({
  selector: 'app-cepc-complaint-details-view',
  standalone: true,
  imports: [CommonModule, FormsModule, ButtonModule, ToastModule, SpeechButtonComponent, CepcHeaderComponent, CepcSidebarComponent, CepcComplaintMetaRowComponent, RightSidePanelComponent, CepcConciliationWorkflowComponent, CepcForwardWorkflowComponent, TranslateOrPipe],
  providers: [MessageService],
  templateUrl: './cepc-complaint-details-view.component.html',
  styleUrl: './cepc-complaint-details-view.component.scss',
})

export class CepcComplaintDetailsView implements OnInit {

  private router = inject(Router);
  private navService = inject(NavigationService);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(KeycloakAuthService);
  readonly dept = inject(CepcContextService);
  private messageService = inject(MessageService);
  activatedRoute = inject(ActivatedRoute)
  private destroyRef = inject(DestroyRef);

  // Template refs into whichever tab child is currently rendered — bridges the header meta-row's
  // action buttons (which live far above the tab content in the template) to that child's own
  // public methods/state. Angular nulls these automatically when the owning @if branch tears down.
  @ViewChild('conciliationRef') conciliationRef?: CepcConciliationWorkflowComponent;
  @ViewChild('forwardRef') forwardRef?: CepcForwardWorkflowComponent;


  // Header
  complaintId: any;
  complaintNumber: string = ''; 
  loggedInUserName = '';
  complaintOffice = '';
  slaDaysRemaining = signal(30);
  userRole = signal<'DO' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY' | 'HEAD'>('DO');
  // userRole falls back to 'DO' for any unrecognised role, so it cannot stand in for holding CEPC_DO.
  isCepcDo = signal(false);
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
  colsSwapped = signal(false);
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
  /** `complaintStatusNav` as words, from the backend. Display only — the tab guards branch on the raw value. */
  complaintStatusNavLabel = "";
  /** Raw `navBarDto.workflowStage`, e.g. `MARKED_FOR_CLOSURE` — disambiguates statuses `statusLabel` folds together. */
  complaintWorkflowStage = "";

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
  // The selected entity's own contact details. Not displayed any more — contact persons took that
  // place — but still the thing a forward is addressed to, so it drives the "nobody to forward to"
  // warning.
  entityContact = signal<EntityDetail | null>(null);
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
  replyWithin30Days: 'Yes' | 'No' | 'Not Applicable' | '' = '';

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
  summaryLoadState = signal<'loading' | 'ready' | 'failed'>('loading');
  summaryLoadError = signal('');
  assessmentSaving = signal(false);
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

  // Right Side Panel — activePanel replaces the 4 mutually-exclusive booleans that used to gate
  // Past Complaints/Attachments/History/Legal Case; see openRightPanel()/closeRightPanel().
  activePanel = signal<RightSidePanelKey>(null);
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);
  sidebarAttachments = signal<Attachment[]>([]);
  loadingSidebarAttachments = signal(false);
  uploadingDocument = signal(false);

  // ═══ History Panel ═══
  hideComments = signal(false);
  historyEntries = signal<HistoryEntry[]>([]);
  loadingHistory = signal(false);

  // ═══ Legal Case Panel ═══
  legalCase = signal<LegalCase | null>(null);
  loadingLegalCase = signal(false);
  showLegalCaseDialog = signal(false);
  legalCaseSaving = signal(false);
  legalCaseError = signal('');
  legalCaseForm: LegalCaseFormShape = this.blankLegalCaseForm();

  // Final Decision
  finalDecisionAction = '';
  finalDecisionRemarks = '';
  finalDecisionSubmitting = signal(false);
  showFinalDecisionPreview = signal(false);
  closureClause = '';
  closureClauseDescription = '';
  complaintStatusOnPortal = '';
  speakingOrderGenerated = '';
  gistOfCase = '';
  gistOfCaseRegional = '';

  // Final Decision — Advisory fields
  advisoryComplianceDate = '';
  advisoryDisputeAmount: number | null = null;
  advisoryCompensationLoss: number | null = null;
  advisoryCompensationMental: number | null = null;

  // Final Decision — Award fields
  awardImplementationDate = '';
  awardAcceptanceDate = '';

  // Final Decision — Reject/Withdraw/Settle
  rejectWithdrawSettleSubAction: 'REJECT' | 'WITHDRAW' | 'SETTLE' | '' = '';
  rejectWithdrawSettleReason = '';

  // Final Decision — Reopen
  reopenReason = '';
  reopenedDate = new Date().toISOString().slice(0, 10);
  dealingOfficial = '';
  cepcDoOfficers = signal<{ id: string; name: string }[]>([]);

  /** The fixed set of complaint-status-on-portal values, shared by all three Final Decision outcomes. */
  readonly complaintStatusOnPortalOptions = ['Draft', 'Pending', 'Rejected', 'Withdrawn', 'Closed'];

  // Validation
  formSubmitAttempted = false;
  fieldErrors: Record<string, string> = {};
  finalDecisionFieldErrors = signal<Record<string, string>>({});

  // Assessment panel
  sendToDeputy = false;
  assessmentComment = '';
  speakingOrderContent = '';
  proposedAction = '';
  private proposedActionBeforeDeputy = '';
  proposedClause = '';
  deputyOmbudsmanDecision = 'NON_MAINTAINABLE';
  deputyOmbudsmanComments = '';
  complaintStatus = signal('NEW_COMPLAINT');
  isReadOnlyViewer = signal(false);
  // The form fields are the save payload, so a write is only safe once the summary has been read.
  // Covers the paths view-only-locked cannot reach: the right sidebar, the dialogs, and the keyboard.
  readonly canMutate = computed(() => this.summaryLoadState() === 'ready' && !this.isReadOnlyViewer());
  justActioned = signal(false);
  workflowAction = signal('');
  /**
   * Which tabs carry the meta row's Save icon.
   *
   * <p>Named rather than "every tab without its own button" so that adding a tab does not silently give it
   * a Save that saves nothing: Contact Entity and Email both persist through their own controls.
   */
  readonly SAVEABLE_TABS = ['summary', 'conciliation', 'forward', 'final-decision'];
  readonly showSaveIcon = computed(() =>
    !this.isReadOnlyViewer()
    && this.userRole() !== 'HEAD'
    && this.complaintStatus() !== 'VIEW_ONLY'
    && this.SAVEABLE_TABS.includes(this.assessmentTab()));
  // Conciliation is the DO's own step; every other rung on the ladder only ever reads its outcome.
  conciliationEnabled = computed(() => {
    if (!this.isCepcDo() || this.userRole() !== 'DO') return false;
    const status = this.complaintStatus();
    const excludedStatuses = ['ADVISORY_COMPLIED', 'COMPLAINT_SETTLED', 'COMPLAINT_WITHDRAWN', 'COMPLAINT_REJECTED', 'AWARD_PASSED', 'OMBUDSMAN_DECISION'];
    return !excludedStatuses.includes(status);
  });

  approvalSentTo = signal('');
  showApprovalMenu = signal(false);
  showSendBackMenu = signal(false);
  approvalMenuPos = signal({ top: 0, left: 0 });
  sendBackMenuPos = signal({ top: 0, left: 0 });

  actualComplaintNumber: any;
  /** The last-read forwardDraftDto, passed to CepcForwardWorkflowComponent as [initialDraft]. */
  forwardDraftRaw: any = null;

  /** Applies what a successful conciliation save on the child changes about parent-owned header/status chrome. */
  onConciliationStatusChanged(change: ConciliationStatusChange) {
    if (change.workflowStage) this.complaintStatus.set(change.workflowStage);
    if (change.statusNav !== undefined) this.complaintStatusNav = change.statusNav;
    if (change.statusNavLabel !== undefined) this.complaintStatusNavLabel = change.statusNavLabel;
  }

  /** Applies what a successful forward submission changes about parent-owned header/navigation chrome. */
  onForwardCompleted(result: ForwardCompleted) {
    this.approvalSentTo.set(result.approvalSentTo);
    this.justActioned.set(true);
    this.complaintStatus.set(result.complaintStatus);
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
  sendBackTarget = signal<'DEALING_OFFICER' | 'REVIEWER' | 'INCHARGE'>('DEALING_OFFICER');
  sendBackAssignmentMode = 'AUTOMATIC';
  sendBackSelectedName = '';
  sendBackTargetUsers: { id: string; name: string; officeCode?: string }[] = [];
  sendBackFilteredUsers: { id: string; name: string; officeCode?: string }[] = [];
  sendBackSubmitting = signal(false);
  approvalTarget = signal<'DEALING_OFFICER' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY'>('REVIEWER');
  approvalAssignmentMode = 'AUTOMATIC';
  approvalSelectedName = '';
  approvalTargetUsers = signal<{ id: string; name: string; officeCode?: string }[]>([]);
  approvalFilteredUsers: { id: string; name: string; officeCode?: string }[] = [];
  approvalCrpcAction = '';
  approvalCrpcClause = '';
  systemicIssue = '';
  /** Assignment dialog for Mark for Closure — lets the incharge/closing authority route the complaint to
   *  an automatically suggested CEPC Dealing Officer or search for one manually, instead of the plain
   *  dropdown this used to be. */
  showMarkForClosureDialog = signal(false);
  markForClosureAssignmentMode = 'AUTOMATIC';
  markForClosureSelectedName = '';
  markForClosureUsers: { id: string; name: string; officeCode?: string }[] = [];
  markForClosureFilteredUsers: { id: string; name: string; officeCode?: string }[] = [];
  /** Confirmation popup for Reopen — styled like the Mark for Closure dialog above instead of the
   *  generic Preview & Confirm modal, so its status strip can say "Reopen Complaint" rather than the
   *  internal "In Progress" status getExpectedStatusAfterDecision() returns. */
  showReopenConfirmDialog = signal(false);
  assessmentComments = signal<{ id: number;author: string; target:string; complaintNumber:string; initials: string;  time?: string; text: string; color: string; role?: string; noRecordNumber:string; createdAt?: string }[]>([]);
  expandedComments = new Set<number>();

  canUseForwardTab = computed(() => {
    const role = this.userRole();
    return role === 'REVIEWER' || role === 'INCHARGE' || role === 'CLOSING_AUTHORITY';
  });

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
  // Unrelated to the Forward tab's layout-aware officeList() — the office-head-decision override always
  // reassigns within the CEPC office list this dialog has always used.
  territoryOfficeList = signal<{ officeCode: string; officeName: string; officeType: string }[]>([]);
  legalCaseRegionOptions = computed(() => this.territoryOfficeList().filter(o => o.officeType === 'BO'));

  private loadTerritoryOfficeList() {
    if (this.territoryOfficeList().length > 0) return;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/offices`).subscribe({
      next: (res) => this.territoryOfficeList.set(res?.data || []),
      error: () => this.territoryOfficeList.set([])
    });
  }

  get preForwardRoleLabel(): string {
    switch (this.transferPreRole) {
      case 'DO': return 'Dealing Officer';
      case 'REVIEWER': return 'Reviewer';
      case 'INCHARGE': return 'Incharge';
      case 'CLOSING_AUTHORITY': return 'Closing Authority';
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
      this.loadTerritoryOfficeList();
    }
  }

  confirmOfficeHeadDecision() {
    if (this.officeHeadSubmitting()) return;
    const decision = this.officeHeadDecisionType();
    if (decision === 'REJECT' && !this.officeHeadComment.trim()) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Comment Required',
        detail: 'A rejection comment is mandatory.'
      });
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
        this.messageService.add({
          severity: 'success',
          summary: decision === 'APPROVE' ? 'Transfer Approved' : 'Transfer Rejected',
          detail: decision === 'APPROVE'
            ? 'The complaint has moved to the selected office.'
            : `The complaint has gone back to the ${this.preForwardRoleLabel}.`
        });
      },
      error: (err) => {
        this.officeHeadSubmitting.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Decision Failed',
          detail: err?.error?.message || 'Could not submit the decision. Please try again.'
        });
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
  nodalDetailTab = signal<string>('summary');
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

  // Contact Entity — Summary panel edit (DO only). Group B fields (complainant/entity) reuse the same
  // top-level properties the main Summary tab edits; these ten are the Group C fields that live only on
  // NodalOfficerRecord and have no home elsewhere, so they get their own scratch copies used for both
  // display and edit.
  nodalSummaryEditMode = signal(false);
  nodalSummarySaving = signal(false);
  private nodalSummaryEditSnapshot: Record<string, any> | null = null;
  nodalContactModuleName = '';
  nodalContactAtmComplaint = '';
  nodalContactDesignatedOffice = '';
  nodalContactProcessingOffice = '';
  nodalContactNoName = '';
  nodalContactNoMobile = '';
  nodalContactNoEmail = '';
  nodalContactPnoName = '';
  nodalContactPnoMobile = '';
  nodalContactPnoEmail = '';

  // Email Communication
  emailComposeMode = signal(false);
  emailOpenActivitiesExpanded = true;
  emailDraftExpanded = false;
  emailClosedExpanded = false;
  // Open Activities is the union of drafts and sent mail; Draft and Closed are the two halves. All three
  // are projections of the one list the server returns, so a row appears in Open and in exactly one half.
  emailActivities = signal<ComplaintEmail[]>([]);
  emailDrafts = signal<ComplaintEmail[]>([]);
  emailClosedActivities = signal<ComplaintEmail[]>([]);
  loadingEmails = signal(false);
  selectedEmailActivity = signal<ComplaintEmail | null>(null);
  emailThreadMessages = signal<ComplaintEmail[]>([]);
  loadingEmailThread = signal(false);
  emailSaving = signal(false);
  /** Set while composing means the compose form is editing this existing draft rather than a new mail. */
  editingDraftId: number | null = null;
  /** Set while composing means the mail being written is a reply to this sent mail. */
  replyToEmailId: number | null = null;
  emailFrom = DEFAULT_EMAIL_FROM;
  emailTo = '';
  emailCc = '';
  emailBcc = '';
  emailSubject = '';
  emailBody = '';
  emailFormError = signal('');
  /** Attachments already uploaded for the mail being composed/edited — cleared on reset, sent with the draft/send request. */
  composeAttachments = signal<{ id: number; name: string; size: string }[]>([]);
  uploadingEmailAttachment = signal(false);

  // Reference data
  states = signal<string[]>([]);
  districts = signal<string[]>([]);

  protected Math = Math;

  togglePanel(panelGroup: WritableSignal<PanelState>, key: keyof PanelState) {
    const opening = !panelGroup()[key];
    panelGroup.update(v => ({ ...v, [key]: !v[key] }));
    if (opening) {
      this.activePanel.set(null);
    }
  }

formatStatusLabel(status: string): string {
  if (!status) return '';
  return status
    .toLowerCase()
    .split('_')
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}

  openRightPanel(key: RightSidePanelKey): void {
    const opening = this.activePanel() !== key;
    this.activePanel.set(opening ? key : null);
    this.assessmentPanels.update(v => ({ ...v, attachments: false, history: false, settings: false }));
    this.emailPanels.update(v => ({ ...v, history: false, settings: false }));
    this.createPanels.update(v => ({ ...v, history: false, settings: false }));
    if (!opening) return;
    switch (key) {
      case 'pastComplaints': this.loadPastComplaints(); break;
      case 'attachments': this.fetchAttachments(); break;
      case 'history': this.fetchComplaintHistory(); break;
      case 'legalCase': this.loadLegalCase(); break;
    }
  }

  closeRightPanel(): void {
    this.activePanel.set(null);
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
          this.loadExistingComplaint(id);
        } else {
          this.summaryLoadError.set('No complaint id in the address.');
          this.summaryLoadState.set('failed');
        }
      });
  }

  private loadExistingComplaint(id: string) {
    this.summaryLoadState.set('loading');
    this.summaryLoadError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}${this.dept.cx(`${id}/summary`)}`).subscribe({
      next: (res) => {
        this.applySummary(res?.data || res || {});
        this.summaryLoadState.set('ready');
        this.submitted.set(true);
        this.loadPastComplaints();
        this.fetchAttachments();
        this.loadComments();
        this.loadCepcDoOfficers();
        // thenPoll: a mail queued before this screen was opened may still be in flight.
        this.loadEmailThreads(true);
      },
      error: (err) => {
        // The screen stays up so the officer can read the tabs and retry, but locked: every field is
        // empty and a save built from them would overwrite the stored complaint with blanks.
        this.isReadOnlyViewer.set(true);
        this.summaryLoadError.set(err?.status === 403
          ? 'You do not have access to this complaint.'
          : err?.status === 404
            ? 'This complaint could not be found.'
            : this.errorDetail(err, 'Could not load this complaint from the server.'));
        this.summaryLoadState.set('failed');
        // The dependent loaders are skipped, not retried: loadPastComplaints would query by-complainant
        // with no email or phone to match on and return an unrelated list, the same trap its own error
        // handler guards against. The rest key on actualComplaintNumber, which never arrived.
        this.notifyError(err, 'Could not load this complaint from the server.');
      }
    });
  }

  /**
   * Re-runs the load rather than patching state: isReadOnlyViewer has to come back from the server
   * payload, never from a guess here. Clearing it keeps the retry's spinner out from behind a stale lock.
   */
  retrySummaryLoad() {
    if (this.summaryLoadState() === 'loading' || !this.complaintId) return;
    this.isReadOnlyViewer.set(false);
    this.loadExistingComplaint(this.complaintId);
  }

  /**
   * Maps the nested summary onto the flat form fields. Shared by the initial GET and by the PUT
   * response, so a save leaves the form showing exactly what was persisted.
   */
  private applySummary(data: any) {
        this.assignedOfficer = data.assignedOfficer || '';

        // The backend only ever writes the enum value COMPLAINT_CLOSED (never bare 'CLOSED'), so these
        // normalized forms are what both the edit lock and the status badge below key off of.
        const normalizedStatus = (data.navBarDto?.status || '').toUpperCase();
        const normalizedStage = (data.navBarDto?.workflowStage || '').toUpperCase();

        // The summary is editable only by the officer holding it; everyone else reads it. canEdit comes
        // from the server so the form matches what the PUT will actually accept.
        this.isReadOnlyViewer.set(data.canEdit === false || normalizedStatus === 'COMPLAINT_CLOSED');

        this.complaintNumber = data.navBarDto?.complaintNumber || '';
        this.actualComplaintNumber = data.navBarDto?.complaintNumber || '';

        // Loaded here, not on the Contact Entity tab alone: the Summary tab shows the same contacts
        // read-only, in place of the entity's nodal officer panel, and that tab is the one the screen
        // opens on.
        this.loadContactPersons();

        this.category = data.navBarDto?.complaintCategory;

        this.sla=data.navBarDto?.slaBreachIn

          this.complaintStatusNav =
          data.navBarDto?.status

        this.complaintStatusNavLabel =
          data.navBarDto?.statusLabel || this.formatStatusLabel(data.navBarDto?.status)

        this.complaintWorkflowStage =
          data.navBarDto?.workflowStage || ''

        // complaintStatus otherwise only moves via optimistic .set() calls right after an action taken in
        // this session, so a fresh load of an already-closed complaint left it stuck at the 'NEW_COMPLAINT'
        // default — the status badge text (sourced from complaintStatusNavLabel) was correct, but its
        // color class (getStatusClass(), keyed off this signal) was not.
        if (normalizedStatus === 'COMPLAINT_CLOSED' || normalizedStage === 'CLOSED') {
          this.complaintStatus.set('CLOSED');
        }

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

        this.complainantPhone =
          data.basicDetailsDto?.mobile || '';

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

        this.entityCountry =
          data.entityDetails?.country || 'India';

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

        this.branchCenterName =
          data.entityDetails?.branchCenterName || '';

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
        // Same COMPLAINT column the officer's Proposed Action dropdown writes; it is what decides
        // whether the complaint reached the maintainable stage that conciliation belongs to.
        this.workflowAction.set(this.crpcProposedAction);
        this.proposedAction = this.crpcProposedAction;
        this.proposedClause = additionalInfo?.proposedClause || '';
        this.speakingOrderContent = additionalInfo?.speakingOrderContent || '';
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
        this.replyWithin30Days = data.complainDetailsDto?.complaintLinkage?.replyWithin30Days || '';

        this.applyEligibilityAnswers(data.eligibility);
        this.applyFinalDecisionDraft(data.finalDecisionDto);
        // The Forward child only exists once that tab is opened, so the draft is stashed here and applied
        // (and re-applied on every later change, even while the tab is already open — see its ngOnChanges)
        // by the child itself via the [initialDraft] input, rather than hydrated eagerly like the fields above.
        this.forwardDraftRaw = data.forwardDraftDto;
  }

  /**
   * Restores the Final Decision tab from a saved draft.
   *
   * <p>Assigns {@code finalDecisionAction} directly rather than through
   * {@link selectFinalDecisionAction}, which clears every field under the action — routing hydration
   * through it would blank the values being loaded in the same breath.
   */
  private applyFinalDecisionDraft(fd: any) {
    if (!fd) return;

    this.finalDecisionAction = fd.action || '';
    this.closureClause = fd.closureClause || '';
    this.closureClauseDescription = fd.closureClauseDescription || '';
    this.complaintStatusOnPortal = fd.complaintStatusOnPortal || '';
    // The radios are valued 'Yes'/'No', not true/false, so a raw String() of the stored Boolean would
    // match neither and the answer would come back blank.
    this.speakingOrderGenerated = fd.speakingOrderGenerated === null
      || fd.speakingOrderGenerated === undefined
      ? ''
      : (fd.speakingOrderGenerated ? 'Yes' : 'No');
    this.gistOfCase = fd.gistOfCase || '';
    this.gistOfCaseRegional = fd.gistOfCaseRegional || '';

    this.advisoryComplianceDate = fd.advisoryComplianceDate || '';
    this.advisoryDisputeAmount = fd.disputedAmount ?? null;
    this.advisoryCompensationLoss = fd.compensationLoss ?? null;
    this.advisoryCompensationMental = fd.compensationMental ?? null;

    this.awardImplementationDate = fd.awardImplementationDate || '';
    this.awardAcceptanceDate = fd.awardAcceptanceDate || '';

    this.rejectWithdrawSettleSubAction = fd.rejectWithdrawSettleSubAction || '';
    this.rejectWithdrawSettleReason = fd.rejectWithdrawSettleReason || '';
  }

  /**
   * @param thenPoll start the bounded re-poll once the list arrives. False when called *by* the poll, so a
   *                 chain can never restart itself.
   */
  private loadEmailThreads(thenPoll = false) {
    if (!this.actualComplaintNumber) {
      this.clearEmailLists();
      return;
    }
    this.loadingEmails.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/emails`).subscribe({
      next: (res) => {
        const emails: ComplaintEmail[] = res?.data || [];

        // Open Activities is what still needs attention - a draft to finish, a mail in flight, or one that
        // failed. Closed Activities is what has actually gone out.
        this.emailDrafts.set(emails.filter(e => e.status === EMAIL_STATUS_DRAFT));
        this.emailClosedActivities.set(emails.filter(e => e.status === EMAIL_STATUS_SENT));
        this.emailActivities.set(emails.filter(e => e.status !== EMAIL_STATUS_SENT));
        this.loadingEmails.set(false);

        // A draft that was just sent moves between buckets, so re-point the reading pane at the stored row.
        const selectedId = this.selectedEmailActivity()?.id;
        if (selectedId != null) {
          const current = emails.find(e => e.id === selectedId);
          if (current) this.selectedEmailActivity.set(current);
        }

        if (thenPoll) this.pollWhileEmailsPending();
      },
      error: (err) => {
        this.clearEmailLists();
        this.loadingEmails.set(false);
        this.notifyError(err, 'Could not load the email communication for this complaint.');
      }
    });
  }

  private clearEmailLists() {
    this.emailActivities.set([]);
    this.emailDrafts.set([]);
    this.emailClosedActivities.set([]);
  }

  /**
   * Re-reads the list a few times while anything is still queued.
   *
   * <p>A mail is PENDING the instant it is sent and only becomes SENT once cms-notification-service has
   * dispatched it, which happens after this screen has already finished loading. Without this the officer
   * would see "Sending…" until they navigated away and back.</p>
   */
  private pollWhileEmailsPending(attempt = 0) {
    if (attempt >= EMAIL_POLL_DELAYS_MS.length) return;
    if (!this.emailActivities().some(e => e.status === EMAIL_STATUS_PENDING)) return;

    const handle = setTimeout(() => {
      if (!this.actualComplaintNumber) return;
      this.loadEmailThreads();
      this.pollWhileEmailsPending(attempt + 1);
    }, EMAIL_POLL_DELAYS_MS[attempt]);

    // Leaving the screen must not leave a timer holding a reference to this component.
    this.destroyRef.onDestroy(() => clearTimeout(handle));
  }

  /** Manual re-read, for when the bounded poll has given up but the mail is still queued. */
  refreshEmails() {
    this.loadEmailThreads(true);
  }

  emailStatusLabel(status?: string | null): string {
    switch (status) {
      case EMAIL_STATUS_DRAFT: return 'Draft';
      case EMAIL_STATUS_PENDING: return 'Sending…';
      case EMAIL_STATUS_SENT: return 'Sent';
      case EMAIL_STATUS_FAILED: return 'Failed';
      default: return status || '—';
    }
  }

  /** Modifier class for the status pills and badges; keeps the mapping out of the template. */
  emailStatusClass(status?: string | null): string {
    switch (status) {
      case EMAIL_STATUS_DRAFT: return 'draft';
      case EMAIL_STATUS_PENDING: return 'pending';
      case EMAIL_STATUS_FAILED: return 'failed';
      default: return 'open';
    }
  }

  /** Label for the timestamp in the reading pane, which means something different per state. */
  emailDateLabel(status?: string | null): string {
    switch (status) {
      case EMAIL_STATUS_DRAFT: return 'Saved On';
      case EMAIL_STATUS_PENDING: return 'Queued On';
      case EMAIL_STATUS_FAILED: return 'Attempted On';
      default: return 'Sent On';
    }
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
    this.isCepcDo.set(this.dept.hasRung('DO'));
    this.userRole.set(this.dept.currentRung());
  }

  private detectUserOffice(username: string) {
    // Office is assigned per-officer via Team Management (OfficerAvailability), not inferred from the username.
    if (!username) return;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=${this.dept.primaryRole()}`).subscribe({
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
      this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/pincode/${value}`).subscribe({
        next: (res) => {
          this.pincodeLoading.set(false);
          if (res?.data?.length) {
            const po = res.data[0];
            if (po.state) {
              this.complainantState = po.state;
              this.onStateChange(po.state);
            }
            if (po.district) this.complainantDistrict = po.district;
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

    // Entity Details starts collapsed, so a pick made in Eligibility filled four fields the officer
    // could not see and had to go looking for. Opening it puts the result of the pick on screen.
    this.summarySections.entity = true;

    // Read-only Entity Details fields that really are properties of the entity, so the picked row is
    // their only source — the officer never types them.
    //
    // moduleName is deliberately NOT taken from here. It is a property of the complaint's assessment,
    // not of the entity: REGULATED_ENTITIES has no module column and the search projection cannot
    // supply one. Assigning it from the picked row set it to '' on every pick, and because the field is
    // read-only the officer had no way to put it back before the next save wrote that '' over the
    // stored value.
    this.entityCategory = entity.entityCategory || '';
    this.entityTypeDisplay = entity.entityTypeDisplay || entity.entityTypeDetail || '';

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
   * The entity's own nodal officer contact details, which the summary payload does not carry.
   *
   * <p>No longer displayed — the screen shows the complaint's contact persons instead. It is still read
   * because the absence of any address on the entity master means a forward has no recipient, and that
   * warning is the only remaining consumer.
   */
  private loadEntityContact(entityId: number) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/${entityId}`).subscribe({
      next: (res) => this.entityContact.set(res?.data || null),
      // The entity is still selected and still saveable; only the forwarding warning is unavailable.
      error: () => this.entityContact.set(null)
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
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1${this.dept.wf('create-complaint')}`, payload).subscribe({
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

  sendForApproval(target: 'DEALING_OFFICER' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY') {
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
    const role = this.dept.targetRole(target as AssignmentTarget);
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

  /** Populates the Reopen dialog's Dealing Official picker with real CEPC_DO users. */
  private loadCepcDoOfficers() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=CEPC_DO`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        const users = (Array.isArray(data) ? data : []).map((u: any) => ({
          id: u.username || u.userId,
          name: u.displayName || `${u.firstName || ''} ${u.lastName || ''}`.trim()
        }));
        this.cepcDoOfficers.set(users);
      },
      error: () => this.cepcDoOfficers.set([])
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
      const role = this.dept.targetRole(this.approvalTarget() as AssignmentTarget);
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

  openSendBack(target: 'DEALING_OFFICER' | 'REVIEWER' | 'INCHARGE') {
    this.sendBackTarget.set(target);
    this.showSendBackMenu.set(false);
    this.sendBackAssignmentMode = 'AUTOMATIC';
    this.sendBackSelectedName = '';
    this.sendBackFilteredUsers = [];
    this.loadSendBackTargetUsers(target);
    this.showSendBackDialog.set(true);
  }

  private loadSendBackTargetUsers(target: string) {
    if (target !== 'DEALING_OFFICER' && target !== 'REVIEWER' && target !== 'INCHARGE') return;
    const role = this.dept.targetRole(target);
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
    switch (this.sendBackTarget()) {
      case 'DEALING_OFFICER': return 'CEPC Dealing Officer';
      case 'INCHARGE': return 'CEPC Incharge';
      default: return 'CEPC Reviewer';
    }
  }

  get sendBackStatusLabel(): string {
    switch (this.sendBackTarget()) {
      case 'DEALING_OFFICER': return 'Sent Back to Dealing Officer';
      case 'INCHARGE': return 'Sent Back to Incharge';
      default: return 'Sent Back to Reviewer';
    }
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
      error: (err: any) => {
        this.sendBackSubmitting.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Send Back Failed',
          detail: err?.error?.message || 'Could not send back the complaint. Please try again.'
        });
      }
    });
  }

  cancelSendBack() {
    this.showSendBackDialog.set(false);
  }

  get approvalTargetLabel(): string {
    switch (this.approvalTarget()) {
      case 'DEALING_OFFICER': return 'CEPC Dealing Officer';
      case 'REVIEWER': return 'CEPC Reviewer';
      case 'INCHARGE': return 'CEPC Incharge';
      case 'CLOSING_AUTHORITY': return 'CEPC Closing Authority';
    }
  }

  get decisionLabel(): string {
    return this.userRole() === 'CLOSING_AUTHORITY' ? 'Closing Authority Decision' : 'Incharge Decision';
  }

  get approvalStatusLabel(): string {
    switch (this.approvalTarget()) {
      case 'DEALING_OFFICER': return this.decisionLabel;
      case 'REVIEWER': return 'Sent to CEPC Reviewer';
      case 'INCHARGE': return 'Sent to Incharge';
      case 'CLOSING_AUTHORITY': return 'Sent to Closing Authority';
    }
  }

  get approvalFromStatus(): string {
    return 'Sent to Incharge';
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
      error: (err: any) => {
        this.approvalSubmitting.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Approval Failed',
          detail: err?.error?.message || 'Could not send for approval. Please try again.'
        });
      }
    });
  }

  cancelApproval() {
    this.showApprovalDialog.set(false);
  }

  onSendToDeputyChange(): void {
    if (this.sendToDeputy) {
      this.proposedActionBeforeDeputy = this.proposedAction;
      this.proposedAction = this.deputyOmbudsmanDecision;
      this.proposedClause = '';
    } else {
      this.proposedAction = this.proposedActionBeforeDeputy;
    }
  }

  onDeputyDecisionChange(): void {
    if (this.sendToDeputy) {
      this.proposedAction = this.deputyOmbudsmanDecision;
    }
  }

  selectFinalDecisionAction(action: string): void {
    // Re-clicking the action already selected used to clear every field under it, discarding the officer's
    // work on a click that changed nothing.
    if (this.finalDecisionAction === action) return;
    // Close and Mark for Closure render the identical form, so switching between the two must keep what
    // the officer has already typed — only leaving the closure pair means the fields no longer apply.
    const stayingWithinClosure = this.isClosureDecision
      && (action === 'CLOSE' || action === 'MARK_FOR_CLOSURE');
    this.finalDecisionAction = action;
    if (!stayingWithinClosure) this.clearFinalDecisionFields();
  }

  /** Every field that only means something under a particular action, so changing action must reset them. */
  private clearFinalDecisionFields(): void {
    this.finalDecisionFieldErrors.set({});
    this.closureClause = '';
    this.closureClauseDescription = '';
    this.complaintStatusOnPortal = '';
    this.speakingOrderGenerated = '';
    this.gistOfCase = '';
    this.gistOfCaseRegional = '';
    this.advisoryComplianceDate = '';
    this.advisoryDisputeAmount = null;
    this.advisoryCompensationLoss = null;
    this.advisoryCompensationMental = null;
    // awardImplementationDate / awardAcceptanceDate are deliberately absent: no Final Decision action
    // edits them any more, and they belong to the Summary contract, so clearing them here would drop
    // stored summary values on an unrelated action change.
    this.rejectWithdrawSettleSubAction = '';
    this.rejectWithdrawSettleReason = '';
    this.reopenReason = '';
    this.reopenedDate = new Date().toISOString().slice(0, 10);
    this.dealingOfficial = '';
  }

  /**
   * The Final Decision tab is the ladder's own escalation step — a DO does not normally reach it. The one
   * exception is Mark for Closure handing the complaint back down: `MARKED_FOR_CLOSURE` is the raw
   * `navBarDto.workflowStage`, the one case `complaintStatusNav` alone cannot tell apart from the other
   * `COMPLAINT_SETTLED` stages (awaiting closure, advisory issued, award passed).
   */
  get finalDecisionTabEnabled(): boolean {
    // CEPC_ADMIN holds no rung on the DO/REVIEWER/INCHARGE/CLOSING_AUTHORITY ladder, so
    // currentRung() falls back to 'DO' for an admin-only user — without this check they'd be held
    // to the dealing officer's MARKED_FOR_CLOSURE-only gate and could never reach a closed complaint's
    // Reopen action.
    if (this.dept.hasRung('ADMIN')) return true;
    if (this.userRole() !== 'DO') return true;
    return (this.complaintWorkflowStage || '').trim().toUpperCase() === 'MARKED_FOR_CLOSURE';
  }

  /** Both Final Decision actions share one closure form; only the transition they trigger differs. */
  get isClosureDecision(): boolean {
    return this.finalDecisionAction === 'CLOSE' || this.finalDecisionAction === 'MARK_FOR_CLOSURE';
  }

  /** Mark for Closure is a hand-up to the closing authority, so only these two rungs may select it. */
  get canMarkForClosure(): boolean {
    return this.userRole() === 'INCHARGE' || this.userRole() === 'CLOSING_AUTHORITY';
  }

  /**
   * Whether to offer the Reopen toggle, and — since a closed complaint is read-only — whether to let its
   * Confirm through the `isReadOnlyViewer()` lock that hides every other action on this screen.
   *
   * <p>Deliberately narrow: only the closing authority or an admin, and only while the complaint sits at
   * the one state the server permits a reopen from. Everything else on a closed complaint stays locked,
   * because reopening is a transition rather than an edit of the complaint's content. The status compared
   * here is the raw `navBarDto.status`, which closure persists as lowercase `closed`. CEPC_ADMIN isn't a
   * rung on the userRole() ladder (see finalDecisionTabEnabled), so this checks dept.hasRung directly
   * rather than userRole().
   */
  get canReopenComplaint(): boolean {
    if (!this.dept.hasRung('CLOSING_AUTHORITY', 'ADMIN')) return false;
    const status = (this.complaintStatusNav || '').trim().toLowerCase();
    return status === 'closed' || status === 'resolved' || status === 'complaint_closed';
  }

  get canConfirmFinalDecision(): boolean {
    if (!this.finalDecisionAction) return false;
    if (this.finalDecisionAction === 'REJECT_WITHDRAW_SETTLE' && !this.rejectWithdrawSettleSubAction) return false;
    // A reopen overturns a closure, so the record has to say why. This is the only Final Decision action
    // whose free text is mandatory.
    if (this.finalDecisionAction === 'REOPEN') {
      return this.canReopenComplaint && !!this.reopenReason.trim();
    }
    return true;
  }

  getFinalDecisionLabel(): string {
    switch (this.finalDecisionAction) {
      case 'CLOSE': return 'Close Complaint';
      case 'MARK_FOR_CLOSURE': return 'Mark for Closure';
      case 'ADVISORY': return 'Compile Advisory';
      case 'REOPEN': return 'Reopen Complaint';
      case 'REJECT_WITHDRAW_SETTLE':
        const subLabels: Record<string, string> = {
          'REJECT': 'Reject Complaint',
          'WITHDRAW': 'Withdraw Complaint',
          'SETTLE': 'Settle Complaint'
        };
        return subLabels[this.rejectWithdrawSettleSubAction] || 'Reject/Withdraw/Settle';
      default: return 'Final Decision';
    }
  }

  getExpectedStatusAfterDecision(): string {
    switch (this.finalDecisionAction) {
      case 'CLOSE': return 'Complaint Closed';
      case 'MARK_FOR_CLOSURE': return 'Marked For Closure';
      case 'ADVISORY': return 'Advisory Complied';
      case 'REOPEN': return 'Complaint Re Open';
      case 'REJECT_WITHDRAW_SETTLE':
        const subStatuses: Record<string, string> = {
          'REJECT': 'Complaint Rejected',
          'WITHDRAW': 'Complaint Withdrawn',
          'SETTLE': 'Complaint Settled'
        };
        return subStatuses[this.rejectWithdrawSettleSubAction] || '—';
      default: return '—';
    }
  }

  confirmFinalDecision() {
    if (this.finalDecisionSubmitting()) return;
    if (!this.validateFinalDecision()) return;
    // The modal's own Confirm only checks the in-flight flag, so the mandatory-reason rule for a reopen has
    // to be re-checked here rather than resting on the disabled state of the button that opened the modal.
    if (!this.canConfirmFinalDecision) return;
    this.finalDecisionSubmitting.set(true);

    this.persistComment();

    const base: Record<string, any> = {
      assignedTo: this.loggedInUserName,
      assignedToName: this.loggedInUserName,
      assignmentMode: 'FINAL_DECISION',
      performedBy: this.auth.currentUser()?.username || ''
    };

    let payload: Record<string, any>;

    switch (this.finalDecisionAction) {
      case 'CLOSE':
        payload = {
          ...base,
          target: 'CLOSE',
          // Closure Letter Content is no longer a field on this form, so Comments is now the remarks source.
          remarks: this.closureClauseDescription || this.assessmentComment || this.finalDecisionRemarks,
          closureClause: this.closureClause,
          complaintStatusOnPortal: this.complaintStatusOnPortal,
          speakingOrderGenerated: this.speakingOrderGenerated,
          gistOfCase: this.gistOfCase,
          gistOfCaseRegional: this.gistOfCaseRegional
        };
        break;
      // Mark for Closure no longer flows through here: its own Assignment dialog
      // (confirmMarkForClosureAssignment) submits directly, since the dealing officer picked there isn't
      // read from a plain `dealingOfficial` field any more.
      case 'ADVISORY':
        payload = {
          ...base,
          target: 'ADVISORY',
          advisoryComplianceDate: this.advisoryComplianceDate || null,
          disputeAmount: this.advisoryDisputeAmount,
          compensationLoss: this.advisoryCompensationLoss,
          compensationMental: this.advisoryCompensationMental
        };
        break;
      case 'REJECT_WITHDRAW_SETTLE':
        payload = {
          ...base,
          target: this.rejectWithdrawSettleSubAction,
          remarks: this.rejectWithdrawSettleReason
        };
        break;
      case 'REOPEN': {
        // The reason rides along as `remarks`, which performAction already puts on the timeline and the
        // audit entry. `assignedTo`/`assignedToName` override the `base` defaults (the closing authority
        // performing the reopen) with the chosen Dealing Official, since the backend reads `assignedTo` as
        // the reassignment target for this action.
        const dealingOfficialName = this.cepcDoOfficers().find(o => o.id === this.dealingOfficial)?.name || '';
        payload = {
          ...base,
          assignedTo: this.dealingOfficial,
          assignedToName: dealingOfficialName,
          target: 'REOPEN',
          remarks: this.reopenReason.trim(),
          complaintStatusOnPortal: this.complaintStatusOnPortal,
          reopenedDate: this.reopenedDate
        };
        break;
      }
      default:
        this.finalDecisionSubmitting.set(false);
        return;
    }

    const statusMap: Record<string, string> = {
      'CLOSE': 'CLOSED',
      'ADVISORY': 'ADVISORY_COMPLIED',
      'REJECT': 'COMPLAINT_REJECTED',
      'WITHDRAW': 'COMPLAINT_WITHDRAWN',
      'SETTLE': 'COMPLAINT_SETTLED',
      'REOPEN': 'COMPLAINT_REOPEN'
    };
    const targetKey = this.finalDecisionAction === 'REJECT_WITHDRAW_SETTLE'
      ? this.rejectWithdrawSettleSubAction
      : this.finalDecisionAction;
    const newStatus = statusMap[targetKey] || 'CLOSED';

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.finalDecisionSubmitting.set(false);
        this.showFinalDecisionPreview.set(false);
        // A reopen is the one outcome that leaves the officer with work to do on this screen, so it shows no
        // "actioned" confirmation panel and instead re-reads the summary: the lock, the tab gating and the
        // header chip all have to come back from the server, the way retrySummaryLoad does it, rather than
        // be guessed at here.
        if (this.finalDecisionAction === 'REOPEN') {
          this.showReopenConfirmDialog.set(false);
          this.finalDecisionAction = '';
          this.clearFinalDecisionFields();
          this.isReadOnlyViewer.set(false);
          this.loadExistingComplaint(this.complaintId);
          return;
        }
        this.justActioned.set(true);
        this.complaintStatus.set(newStatus);
        this.approvalSentTo.set(newStatus);
      },
      error: (err) => {
        this.finalDecisionSubmitting.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Action Failed',
          detail: err?.error?.message || 'Could not submit the decision. Please try again.'
        });
      }
    });
  }

  /** Opens the Mark for Closure Assignment dialog, refreshing the CEPC_DO roster so the automatic
   *  suggestion reflects current workload rather than whatever happened to be cached. */
  openMarkForClosureDialog() {
    this.markForClosureAssignmentMode = 'AUTOMATIC';
    this.markForClosureSelectedName = '';
    this.markForClosureFilteredUsers = [];
    this.loadMarkForClosureUsers();
    this.showMarkForClosureDialog.set(true);
  }

  private loadMarkForClosureUsers() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/availability?role=CEPC_DO`).subscribe({
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
        this.markForClosureUsers = officeUsers.length > 0 ? officeUsers : allUsers;
        this.markForClosureFilteredUsers = this.markForClosureUsers;
        if (this.markForClosureAssignmentMode === 'AUTOMATIC') {
          this.loadMarkForClosureNextAssignee();
        }
      },
      error: () => { this.markForClosureUsers = []; }
    });
  }

  private loadMarkForClosureNextAssignee() {
    const officeParam = this.complaintOffice ? `&office=${this.complaintOffice}` : '';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/next-assignee?role=CEPC_DO${officeParam}`).subscribe({
      next: (res) => {
        if (res?.success && res?.data) {
          this.markForClosureSelectedName = res.data.displayName || res.data.username || '';
        } else if (this.markForClosureUsers.length > 0) {
          this.markForClosureSelectedName = this.markForClosureUsers[0].name;
        }
      },
      error: () => {
        if (this.markForClosureUsers.length > 0) {
          this.markForClosureSelectedName = this.markForClosureUsers[0].name;
        }
      }
    });
  }

  onMarkForClosureAssignmentChange() {
    if (this.markForClosureAssignmentMode === 'MANUAL') {
      this.markForClosureSelectedName = '';
      this.markForClosureFilteredUsers = this.markForClosureUsers;
    } else {
      this.markForClosureFilteredUsers = [];
      this.loadMarkForClosureNextAssignee();
    }
  }

  filterMarkForClosureUsers() {
    const term = this.markForClosureSelectedName.toLowerCase();
    this.markForClosureFilteredUsers = this.markForClosureUsers.filter(u =>
      u.name.toLowerCase().includes(term)
    );
  }

  selectMarkForClosureUser(user: { id: string; name: string }) {
    this.markForClosureSelectedName = user.name;
    this.markForClosureFilteredUsers = [];
  }

  cancelMarkForClosureDialog() {
    this.showMarkForClosureDialog.set(false);
  }

  confirmMarkForClosureAssignment() {
    if (this.finalDecisionSubmitting()) return;
    if (!this.validateFinalDecision()) return;
    if (!this.markForClosureSelectedName.trim()) return;
    this.finalDecisionSubmitting.set(true);

    this.persistComment();

    const selectedUser = this.markForClosureUsers.find(u => u.name === this.markForClosureSelectedName);
    const payload = {
      assignedTo: selectedUser?.id || '',
      assignedToName: this.markForClosureSelectedName,
      assignmentMode: this.markForClosureAssignmentMode,
      performedBy: this.auth.currentUser()?.username || '',
      target: 'MARK_FOR_CLOSURE',
      remarks: this.closureClauseDescription || this.assessmentComment || this.finalDecisionRemarks,
      closureClause: this.closureClause,
      complaintStatusOnPortal: this.complaintStatusOnPortal
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload).subscribe({
      next: () => {
        this.finalDecisionSubmitting.set(false);
        this.showMarkForClosureDialog.set(false);
        this.justActioned.set(true);
        this.complaintStatus.set('MARKED_FOR_CLOSURE');
        this.approvalSentTo.set(this.markForClosureSelectedName);
      },
      error: () => {
        this.finalDecisionSubmitting.set(false);
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
    formData.append('source', this.dept.cfg().code);
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

  /** Live feedback as the user types/pastes into a Final Decision field capped at maxLength. */
  onFinalDecisionFieldInput(field: string, value: string, maxLength = 2000): void {
    const errors = { ...this.finalDecisionFieldErrors() };
    if (value.length >= maxLength) {
      errors[field] = `Maximum ${maxLength} characters reached.`;
    } else {
      delete errors[field];
    }
    this.finalDecisionFieldErrors.set(errors);
  }

  private validateFinalDecision(): boolean {
    const errors: Record<string, string> = {};
    if (this.gistOfCase.length > 2000) errors['gistOfCase'] = 'Maximum 2000 characters.';
    if (this.gistOfCaseRegional.length > 2000) errors['gistOfCaseRegional'] = 'Maximum 2000 characters.';
    if (this.assessmentComment.length > 2000) errors['assessmentComment'] = 'Maximum 2000 characters.';
    this.finalDecisionFieldErrors.set(errors);
    return Object.keys(errors).length === 0;
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
    // Re-read rather than trust what the summary load fetched: the tab is where they are added and
    // edited, so it is the one place a stale list would be visible as a missing row.
    this.loadContactPersons();
  }

  // ── Contact persons ────────────────────────────────────────────────────────────────────────────
  //
  // Kept entirely separate from the nodal record above. They share the Contact Entity tab and nothing
  // else: different table, different endpoint, many per complaint rather than one, and hand-entered
  // rather than drawn from the entity master.

  contactPersons = signal<ContactPerson[]>([]);
  loadingContactPersons = signal(false);
  contactPersonsError = signal('');

  /**
   * The dialog. It has two parts: the complaint's existing contacts, and a form.
   *
   * <p>`contactPersonFormOpen` is what lets one dialog serve both buttons. "Add" opens straight onto a
   * blank form; "Edit" opens onto the list, because with several contacts recorded there is no one
   * record an Edit button could mean — the list is the picker. The one-contact case skips that step,
   * since then there is no ambiguity to resolve. `editingContactPersonId` null means the form is adding.
   */
  showContactPersonDialog = signal(false);
  contactPersonFormOpen = signal(false);
  contactPersonSaving = signal(false);
  contactPersonError = signal('');
  editingContactPersonId: number | null = null;
  contactPersonForm = { name: '', designation: '', email: '', phone: '', remarks: '' };

  loadContactPersons() {
    if (!this.actualComplaintNumber) {
      this.contactPersons.set([]);
      return;
    }
    this.loadingContactPersons.set(true);
    this.contactPersonsError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/contact-persons`, {
      // A query parameter, not a path segment: complaint numbers contain slashes, which would split the
      // segment and match no route at all.
      params: { complaintNumber: this.actualComplaintNumber }
    }).subscribe({
      next: (res) => {
        this.contactPersons.set(res?.data || []);
        this.loadingContactPersons.set(false);
      },
      error: () => {
        this.contactPersons.set([]);
        this.contactPersonsError.set('Could not load contact persons.');
        this.loadingContactPersons.set(false);
      }
    });
  }

  openAddContactPerson() {
    if (!this.canMutate()) return;
    this.editingContactPersonId = null;
    this.contactPersonForm = { name: '', designation: '', email: '', phone: '', remarks: '' };
    this.contactPersonError.set('');
    this.contactPersonFormOpen.set(true);
    this.showContactPersonDialog.set(true);
  }

  /** The "Edit" button at the top of the record. See {@link contactPersonFormOpen}. */
  openContactPersonsEditor() {
    if (!this.canMutate()) return;
    const contacts = this.contactPersons();
    if (contacts.length === 1) {
      this.openEditContactPerson(contacts[0]);
      return;
    }
    this.editingContactPersonId = null;
    this.contactPersonError.set('');
    this.contactPersonFormOpen.set(false);
    this.showContactPersonDialog.set(true);
  }

  openEditContactPerson(contact: ContactPerson) {
    if (!this.canMutate()) return;
    this.editingContactPersonId = contact.id;
    this.contactPersonForm = {
      name: contact.name || '',
      designation: contact.designation || '',
      email: contact.email || '',
      phone: contact.phone || '',
      remarks: contact.remarks || ''
    };
    this.contactPersonError.set('');
    this.contactPersonFormOpen.set(true);
    this.showContactPersonDialog.set(true);
  }

  closeContactPersonDialog() {
    this.showContactPersonDialog.set(false);
    this.contactPersonFormOpen.set(false);
    this.editingContactPersonId = null;
    this.contactPersonError.set('');
  }

  /**
   * Mirrors the server's rule: a name, and at least one way to reach them.
   *
   * <p>Either channel will do — the officer may only have been given one — but a contact nobody can
   * reach is the same defect as no contact at all, so not neither.
   */
  get canSaveContactPerson(): boolean {
    const f = this.contactPersonForm;
    return !!f.name.trim() && (!!f.email.trim() || !!f.phone.trim());
  }

  saveContactPerson() {
    if (!this.canSaveContactPerson || this.contactPersonSaving() || !this.actualComplaintNumber) return;

    this.contactPersonSaving.set(true);
    this.contactPersonError.set('');
    const payload = {
      name: this.contactPersonForm.name.trim(),
      designation: this.contactPersonForm.designation.trim(),
      email: this.contactPersonForm.email.trim(),
      phone: this.contactPersonForm.phone.trim(),
      remarks: this.contactPersonForm.remarks.trim()
    };
    const params = { complaintNumber: this.actualComplaintNumber };
    const base = `${environment.apiBaseUrl}/api/v1/complaints/contact-persons`;
    const request = this.editingContactPersonId === null
      ? this.http.post<any>(base, payload, { params })
      : this.http.put<any>(`${base}/${this.editingContactPersonId}`, payload, { params });

    request.subscribe({
      next: () => {
        this.contactPersonSaving.set(false);
        this.closeContactPersonDialog();
        // Re-read instead of splicing the response into the list: ordering is the server's (oldest
        // first) and the displayed timestamps are formatted there too.
        this.loadContactPersons();
      },
      error: (err) => {
        // The server's own message is the only text that explains which rule was broken — the email and
        // mobile formats are checked there, not on this form.
        this.contactPersonError.set(err?.error?.message || 'Could not save the contact person.');
        this.contactPersonSaving.set(false);
      }
    });
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

    // Never reopen a record already mid-edit; the previous record's snapshot would be stale for this one.
    this.nodalSummaryEditMode.set(false);
    this.nodalSummaryEditSnapshot = null;
    this.nodalContactModuleName = record.moduleName || '';
    this.nodalContactAtmComplaint = record.atmComplaint || '';
    this.nodalContactDesignatedOffice = record.designatedOffice || '';
    this.nodalContactProcessingOffice = record.processingOffice || '';
    this.nodalContactNoName = record.noName || '';
    this.nodalContactNoMobile = record.noMobile || '';
    this.nodalContactNoEmail = record.noEmail || '';
    this.nodalContactPnoName = record.pnoName || '';
    this.nodalContactPnoMobile = record.pnoMobile || '';
    this.nodalContactPnoEmail = record.pnoEmail || '';

    this.nodalDetailView.set(true);
    this.nodalDetailTab.set('summary');
    this.loadNodalComments(record.recordNumber);
  }

  private loadNodalComments(recordNumber: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/nodal-records/${recordNumber}/comments`).subscribe({
      next: (res) => this.nodalComments.set(res?.data || []),
      error: () => this.nodalComments.set([])
    });
  }

  postNodalComment(target: 'NO' | 'PNO') {
    if (!this.canMutate()) return;
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
    this.nodalDetailTab.set('summary');
    this.selectedNodalRecord.set(null);
    this.sendToREError.set('');
  }

  sendToRE() {
    if (!this.canMutate()) return;
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

  /**
   * The registration channel as a reader's phrase.
   *
   * <p>The stored vocabulary also includes WEB_PORTAL, which the summary endpoint normalises to PORTAL but
   * other writers do not, so both spellings are mapped. An unmapped value is shown as-is rather than
   * blanked — an unfamiliar channel is still information.
   */
  modeOfReceiptLabel(): string {
    const labels: Record<string, string> = {
      'PHYSICAL_LETTER': 'Physical Letter',
      'EMAIL': 'Email',
      'PORTAL': 'Portal',
      'WEB_PORTAL': 'Portal',
      'CPGRAMS': 'CPGRAMS'
    };
    return labels[this.modeOfReceipt] || this.modeOfReceipt || '—';
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

  persistComment() {
    if (!this.assessmentComment.trim()) return;
    if (!this.actualComplaintNumber) return;
    const commentPayload = {
      text: this.assessmentComment.trim(),
      author: this.loggedInUserName || 'Unknown',
      initials: (this.loggedInUserName || 'U').substring(0, 2).toUpperCase(),
      role: this.userRole(),
      color: '#6366f1'
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/comments`, commentPayload).subscribe({
      next: (res) => {
        this.assessmentComments.set([...this.assessmentComments(), res?.data || commentPayload]);
        this.assessmentComment = '';
      },
      error: () => {
        this.assessmentComment = '';
      }
    });
  }

  /**
   * The meta row's Save icon, for whichever of the four tabs carries it.
   *
   * <p>Each tab saves a different thing to a different endpoint, so the icon dispatches rather than
   * sending one payload. What they share is that none of them moves the complaint's status: Save is
   * always a draft, and the tabs' own Confirm/Forward buttons remain the only terminal actions.
   */
  saveAssessment() {
    if (this.assessmentSaving() || !this.canMutate()) return;

    switch (this.assessmentTab()) {
      case 'conciliation':
        this.conciliationRef?.saveConciliationDraft();
        return;
      case 'forward':
        this.forwardRef?.saveForwardDraft();
        return;
      case 'final-decision':
        this.saveDraftToSummary(
          { finalDecisionDto: this.finalDecisionPayload() },
          'Final decision details saved.',
          data => this.applyFinalDecisionDraft(data.finalDecisionDto));
        return;
      default:
        this.saveDraftToSummary({
          complainDetailsDto: {
            additionalInformation: {
              crpcProposedAction: this.proposedAction || null,
              proposedClause: this.proposedClause || null,
              speakingOrderContent: this.speakingOrderContent || null
            }
          }
        }, 'Assessment details saved successfully.', data => this.applySummary(data));
        return;
    }
  }

  private finalDecisionPayload() {
    return {
      action: this.finalDecisionAction || null,
      closureClause: this.closureClause || null,
      closureClauseDescription: this.closureClauseDescription || null,
      complaintStatusOnPortal: this.complaintStatusOnPortal || null,
      speakingOrderGenerated: this.speakingOrderGenerated === '' ? null : this.speakingOrderGenerated,
      gistOfCase: this.gistOfCase || null,
      gistOfCaseRegional: this.gistOfCaseRegional || null,
      advisoryComplianceDate: this.advisoryComplianceDate || null,
      disputedAmount: this.advisoryDisputeAmount,
      compensationLoss: this.advisoryCompensationLoss,
      compensationMental: this.advisoryCompensationMental,
      awardImplementationDate: this.awardImplementationDate || null,
      awardAcceptanceDate: this.awardAcceptanceDate || null,
      rejectWithdrawSettleSubAction: this.rejectWithdrawSettleSubAction || null,
      rejectWithdrawSettleReason: this.rejectWithdrawSettleReason || null,
    };
  }

  /**
   * PUTs one block of the summary patch.
   *
   * <p>The endpoint patches on key PRESENCE, so sending a single block cannot blank the others — which is
   * what lets the three tabs share it without each having to send the whole form back.
   */
  private saveDraftToSummary(payload: Record<string, unknown>, successDetail: string,
                             hydrate: (data: any) => void) {
    this.assessmentSaving.set(true);
    this.persistComment();

    this.http.put<any>(
      `${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/summary`)}`,
      payload
    ).subscribe({
      next: (res) => {
        this.assessmentSaving.set(false);
        // Only the block that was just saved is re-read. Re-applying the whole summary would overwrite
        // whatever the officer has typed on the OTHER tabs but not yet saved, because the response
        // carries every block — so saving Forward would silently discard unsaved Final Decision work.
        if (res?.data) hydrate(res.data);
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: successDetail });
      },
      error: (err) => {
        this.assessmentSaving.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Save Failed',
          detail: err?.error?.message || 'Could not save. Check the fields and try again.'
        });
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

  toggleCommentExpand(commentId: number) {
    if (this.expandedComments.has(commentId)) {
      this.expandedComments.delete(commentId);
    } else {
      this.expandedComments.add(commentId);
    }
  }

  getStatusLabel(): string {
    const status = this.complaintStatus();
    const labels: Record<string, string> = {
      'NEW_COMPLAINT': 'New Complaint',
      'SENT_TO_REVIEWER': 'Sent to Reviewer',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'Sent to Incharge',
      'SENT_TO_OMBUDSMAN': 'Sent to Closing Authority',
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
    const classes: Record<string, string> = {
      'NEW_COMPLAINT': 'status-new-complaint',
      'REGISTERED': 'status-new-complaint',
      'MEETING_SCHEDULED': 'status-meeting',
      'SENT_TO_REVIEWER': 'status-under-review',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'status-office-head',
      'SENT_TO_OMBUDSMAN': 'status-ombudsman',
      'SENT_TO_DO': 'status-back-deo',
      'SENT_BACK': 'status-back-deo',
      'SENT': 'status-sent-office',
      'SENT_TO_OFFICE': 'status-sent-office',
      'RESOLVED': 'status-settled',
      'CLOSED': 'status-closed',
      'VIEW_ONLY': 'status-closed'
    };
    return classes[status] || 'status-neutral';
  }

  getAllClosureClauses(): string[] {
    return [
      'Complaint not under purview',
      'Complaint involves a dispute between Regulated Entities',
      'Complaint against Management/ Senior Executives of RE',
      'Complaint involves employer-employee relationship',
      'Entity not regulated by RBI',
      'Others'
    ];
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
      'schemeFlag', 'rboCgpcOld', 'groundsFlag', 'currentComplaintNumber', 'replyWithin30Days',
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

  /** Group B (shared with the main Summary tab) + Group C (owned only by this record) fields. */
  private getNodalSummaryEditableFields(): string[] {
    return [
      'complainantName', 'complainantPhone', 'complainantEmail', 'subject', 'receivedDate',
      'entityPincode', 'entityCity', 'entityDistrict', 'entityState', 'entityCountry',
      'entityBranchName', 'entityBranchCategory',
      'nodalContactModuleName', 'nodalContactAtmComplaint', 'nodalContactDesignatedOffice',
      'nodalContactProcessingOffice', 'nodalContactNoName', 'nodalContactNoMobile',
      'nodalContactNoEmail', 'nodalContactPnoName', 'nodalContactPnoMobile', 'nodalContactPnoEmail'
    ];
  }

  enterNodalSummaryEditMode() {
    if (this.editMode()) {
      this.messageService.add({
        severity: 'warn', summary: 'Finish Editing',
        detail: 'Finish or cancel the Summary tab edit first — both edit the same fields.'
      });
      return;
    }
    const snapshot: Record<string, any> = {};
    for (const key of this.getNodalSummaryEditableFields()) {
      snapshot[key] = (this as any)[key];
    }
    this.nodalSummaryEditSnapshot = snapshot;
    this.nodalSummaryEditMode.set(true);
  }

  cancelNodalSummaryEdit() {
    if (this.nodalSummaryEditSnapshot) {
      for (const key of this.getNodalSummaryEditableFields()) {
        (this as any)[key] = this.nodalSummaryEditSnapshot[key];
      }
      this.nodalSummaryEditSnapshot = null;
    }
    this.nodalSummaryEditMode.set(false);
  }

  /**
   * Saves the Contact Entity Summary panel. The two halves live on different backends — the complaint's
   * own summary endpoint for the fields shared with the main Summary tab, and the nodal record's own
   * endpoint for the fields only it owns — so they are sent as two independent PUTs. Either can fail
   * without losing the other: a half-success keeps edit mode open and names which half needs a retry,
   * since both PUTs are patches and re-sending the succeeded half again is harmless.
   */
  updateNodalSummary() {
    const record = this.selectedNodalRecord();
    if (!record || this.nodalSummarySaving()) return;
    this.nodalSummarySaving.set(true);

    const basicAndEntity$ = this.http.put<any>(
      `${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/summary`)}`,
      {
        basicDetailsDto: {
          complainantName: this.complainantName || null,
          mobile: this.complainantPhone || null,
          emailId: this.complainantEmail || null,
          subject: this.subject || null,
          receiptDate: this.receivedDate || null,
        },
        entityDetails: {
          pincode: this.entityPincode || null,
          city: this.entityCity || null,
          district: this.entityDistrict || null,
          state: this.entityState || null,
          country: this.entityCountry || null,
          branchName: this.entityBranchName || null,
          branchCategory: this.entityBranchCategory || null,
        }
      }
    ).pipe(catchError(err => of({ __failed: true, err })));

    const contactFields$ = this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/nodal-records/${record.recordNumber}`,
      {
        moduleName: this.nodalContactModuleName || null,
        atmComplaint: this.nodalContactAtmComplaint || null,
        designatedOffice: this.nodalContactDesignatedOffice || null,
        processingOffice: this.nodalContactProcessingOffice || null,
        nodalOfficerName: this.nodalContactNoName || null,
        phone: this.nodalContactNoMobile || null,
        email: this.nodalContactNoEmail || null,
        pnoName: this.nodalContactPnoName || null,
        pnoMobile: this.nodalContactPnoMobile || null,
        pnoEmail: this.nodalContactPnoEmail || null,
      }
    ).pipe(catchError(err => of({ __failed: true, err })));

    forkJoin([basicAndEntity$, contactFields$]).subscribe(([basicRes, contactRes]: [any, any]) => {
      this.nodalSummarySaving.set(false);
      const basicFailed = !!basicRes?.__failed;
      const contactFailed = !!contactRes?.__failed;

      if (!basicFailed && basicRes?.data) {
        this.complainantName = basicRes.data.basicDetailsDto?.complainantName || '';
        this.complainantPhone = basicRes.data.basicDetailsDto?.mobile || '';
        this.complainantEmail = basicRes.data.basicDetailsDto?.emailId || '';
        this.subject = basicRes.data.basicDetailsDto?.subject || '';
        this.receivedDate = basicRes.data.basicDetailsDto?.receiptDate || '';
        this.entityPincode = basicRes.data.entityDetails?.pincode || '';
        this.entityCity = basicRes.data.entityDetails?.city || '';
        this.entityDistrict = basicRes.data.entityDetails?.district || '';
        this.entityState = basicRes.data.entityDetails?.state || '';
        this.entityCountry = basicRes.data.entityDetails?.country || '';
        this.entityBranchName = basicRes.data.entityDetails?.branchName || '';
        this.entityBranchCategory = basicRes.data.entityDetails?.branchCategory || '';
      }

      if (!contactFailed && contactRes?.data) {
        const updated: NodalRecord = {
          ...contactRes.data,
          statusLabel: this.formatStatusLabel(contactRes.data.status)
        };
        this.selectedNodalRecord.set(updated);
        this.nodalRecords.set(this.nodalRecords().map(r => r.recordNumber === updated.recordNumber ? updated : r));
        this.nodalContactModuleName = updated.moduleName || '';
        this.nodalContactAtmComplaint = updated.atmComplaint || '';
        this.nodalContactDesignatedOffice = updated.designatedOffice || '';
        this.nodalContactProcessingOffice = updated.processingOffice || '';
        this.nodalContactNoName = updated.noName || '';
        this.nodalContactNoMobile = updated.noMobile || '';
        this.nodalContactNoEmail = updated.noEmail || '';
        this.nodalContactPnoName = updated.pnoName || '';
        this.nodalContactPnoMobile = updated.pnoMobile || '';
        this.nodalContactPnoEmail = updated.pnoEmail || '';
      }

      if (!basicFailed && !contactFailed) {
        this.nodalSummaryEditSnapshot = null;
        this.nodalSummaryEditMode.set(false);
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Contact Entity summary updated.' });
      } else if (basicFailed && contactFailed) {
        this.messageService.add({
          severity: 'error', summary: 'Save Failed',
          detail: 'Could not save the summary. Check the fields and try again.'
        });
      } else {
        const which = basicFailed ? 'the complainant / entity fields' : 'the officer / office fields';
        this.messageService.add({
          severity: 'warn', summary: 'Partially Saved',
          detail: `Saved everything except ${which}. Fix and press Update again to retry.`
        });
      }
    });
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
   * Mirrors the nested shape GET /api/complaints/{department}/{id}/summary returns, which is what its PUT
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
          freeMarkedComplaint: this.freeMarkedComplaint,
          replyWithin30Days: this.replyWithin30Days || null
        }
      }
    };
  }

  updateComplaint() {
    // The payload is built from the form fields, so this must not run before they have been read from
    // the server: an empty payload here would blank a real complaint.
    if (!this.canMutate()) return;
    if (!this.declarationAccepted) {
      this.summarySections.declaration = true;
      this.messageService.add({
        severity: 'warn',
        summary: 'Declaration Required',
        detail: 'Please accept the declaration before updating the complaint.'
      });
      return;
    }
    this.saving.set(true);
    this.saveError.set('');
    this.http.put<any>(`${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/summary`)}`,
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

  /** Opens a row in the reading pane, fetching the whole thread so replies are shown alongside it. */
  selectEmailActivity(activity: ComplaintEmail) {
    if (!activity?.id || !this.actualComplaintNumber) return;

    this.emailComposeMode.set(false);
    this.selectedEmailActivity.set(activity);
    this.emailThreadMessages.set([]);
    this.loadingEmailThread.set(true);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/emails/${activity.id}`).subscribe({
      next: (res) => {
        const thread: ComplaintEmailThread | null = res?.data || null;
        if (thread?.email) this.selectedEmailActivity.set(thread.email);
        this.emailThreadMessages.set(thread?.messages || []);
        this.loadingEmailThread.set(false);
      },
      error: (err) => {
        this.loadingEmailThread.set(false);
        this.notifyError(err, 'Could not open this email.');
      }
    });
  }

  createNewEmail() {
    this.resetEmailForm();
    this.selectedEmailActivity.set(null);
    this.emailThreadMessages.set([]);
    this.emailComposeMode.set(true);
  }

  /** Reopens a saved draft in the compose form so it can be finished and sent. */
  editEmailDraft(draft: ComplaintEmail) {
    this.resetEmailForm();
    this.editingDraftId = draft.id;
    this.emailFrom = draft.from || DEFAULT_EMAIL_FROM;
    this.emailTo = draft.to || '';
    this.emailCc = draft.cc || '';
    this.emailBcc = draft.bcc || '';
    this.emailSubject = draft.subject || '';
    this.emailBody = draft.body || '';
    this.composeAttachments.set(
      (draft.attachments || [])
        .filter(a => a.id != null)
        .map(a => ({ id: a.id as number, name: a.name, size: a.size }))
    );
    this.emailComposeMode.set(true);
  }

  /**
   * Replying is only meaningful once a mail has actually gone out - a draft has no recipient who could
   * have received it - so an attempt on an unsent mail is refused with a toast rather than silently.
   */
  replyToEmail() {
    const email = this.selectedEmailActivity();
    if (!email) return;

    if (!email.canReply) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Cannot reply yet',
        detail: 'This email has not been sent yet. Send it before replying.',
        life: 5000
      });
      return;
    }

    this.resetEmailForm();
    this.replyToEmailId = email.id;
    this.emailFrom = email.to || DEFAULT_EMAIL_FROM;
    this.emailTo = email.from || '';
    this.emailSubject = /^re:/i.test(email.subject || '') ? email.subject : `Re: ${email.subject || ''}`;
    this.emailComposeMode.set(true);
  }

  cancelEmailCompose() {
    this.resetEmailForm();
    this.emailComposeMode.set(false);
  }

  private resetEmailForm() {
    this.editingDraftId = null;
    this.replyToEmailId = null;
    this.emailFrom = DEFAULT_EMAIL_FROM;
    this.emailTo = '';
    this.emailCc = '';
    this.emailBcc = '';
    this.emailSubject = '';
    this.emailBody = '';
    this.emailFormError.set('');
    this.composeAttachments.set([]);
  }

  /** Fired by the hidden file input behind the compose form's "Add Attachment" button. */
  onEmailAttachmentSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    if (!files || files.length === 0) return;

    Array.from(files).forEach(file => this.uploadEmailAttachment(file));
    input.value = '';
  }

  /** Uploads through the existing complaint-attachment API; only the returned id travels with the email. */
  private uploadEmailAttachment(file: File): void {
    if (!this.actualComplaintNumber || !this.complaintId) return;

    const formData = new FormData();
    formData.append('complaintNumber', this.actualComplaintNumber);
    formData.append('complaintId', String(this.complaintId));
    formData.append('file', file);

    this.uploadingEmailAttachment.set(true);
    this.http.post<any>(`${environment.apiBaseUrl}/api/files/upload`, formData).subscribe({
      next: (saved) => {
        this.uploadingEmailAttachment.set(false);
        this.composeAttachments.update(list => [...list, {
          id: saved?.id,
          name: saved?.originalName || file.name,
          size: this.formatFileSize(saved?.fileSize)
        }]);
      },
      error: (err) => {
        this.uploadingEmailAttachment.set(false);
        this.notifyError(err, 'Could not upload the attachment.');
      }
    });
  }

  private formatFileSize(bytes: number | null | undefined): string {
    if (!bytes) return '';
    return bytes >= 1024 * 1024
      ? `${(bytes / (1024 * 1024)).toFixed(1)} MB`
      : `${Math.round(bytes / 1024)} KB`;
  }

  /** Removes an attachment still being composed (new mail, or a draft reopened for edit) before it is saved. */
  removeComposeAttachment(att: { id: number }): void {
    this.http.delete(`${environment.apiBaseUrl}/api/files/${att.id}`).subscribe({
      next: () => this.composeAttachments.update(list => list.filter(a => a.id !== att.id)),
      error: (err) => this.notifyError(err, 'Could not remove the attachment.')
    });
  }

  previewEmailAttachment(att: { id?: number; previewUrl?: string }): void {
    if (att?.previewUrl) {
      window.open(`${environment.apiBaseUrl}${att.previewUrl}`, '_blank');
    } else if (att?.id != null) {
      window.open(`${environment.apiBaseUrl}/api/files/stream/${att.id}`, '_blank');
    }
  }

  downloadEmailAttachment(att: { id?: number; url?: string }): void {
    if (att?.id != null) {
      window.open(`${environment.apiBaseUrl}/api/files/download/${att.id}`, '_blank');
    } else if (att?.url) {
      window.open(`${environment.apiBaseUrl}${att.url}`, '_blank');
    }
  }

  /**
   * Removing from an already-saved draft (rather than the compose form still open) has to both delete the
   * file and re-save the draft with it detached — reachable only for a DRAFT row because
   * updateDraft() on the server refuses to touch anything else.
   */
  removeSavedAttachment(activity: ComplaintEmail, att: { id?: number }): void {
    if (att?.id == null || !this.actualComplaintNumber) return;

    this.http.delete(`${environment.apiBaseUrl}/api/files/${att.id}`).subscribe({
      next: () => {
        const remainingIds = (activity.attachments || [])
          .filter(a => a.id != null && a.id !== att.id)
          .map(a => a.id as number);

        const payload = {
          from: activity.from,
          to: activity.to,
          cc: activity.cc || '',
          bcc: activity.bcc || '',
          subject: activity.subject,
          body: activity.body || '',
          status: EMAIL_STATUS_DRAFT,
          attachmentIds: remainingIds
        };

        this.http.put<any>(
          `${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/emails/${activity.id}`,
          payload
        ).subscribe({
          next: (res) => {
            const saved: ComplaintEmail | null = res?.data || null;
            this.refreshEmails();
            if (saved) this.selectEmailActivity(saved);
          },
          error: (err) => this.notifyError(err, 'Could not update the draft after removing the attachment.')
        });
      },
      error: (err) => this.notifyError(err, 'Could not remove the attachment.')
    });
  }

  saveEmailDraft() {
    this.submitEmail(EMAIL_STATUS_DRAFT);
  }

  sendEmail() {
    this.submitEmail(EMAIL_STATUS_SENT);
  }

  /**
   * One path for both buttons: a new mail is POSTed, an existing draft is PUT. status decides whether the
   * row lands in Draft or in Closed, and the server is re-read afterwards so the lists show what persisted.
   */
  private submitEmail(status: string) {
    if (this.emailSaving()) return;

    const validationError = this.validateEmailForm();
    if (validationError) {
      this.emailFormError.set(validationError);
      this.messageService.add({
        severity: 'warn',
        summary: 'Check the email',
        detail: validationError,
        life: 5000
      });
      return;
    }
    this.emailFormError.set('');

    if (!this.actualComplaintNumber) {
      this.messageService.add({
        severity: 'error',
        summary: 'Cannot save',
        detail: 'This complaint has no complaint number yet, so email cannot be saved against it.',
        life: 5000
      });
      return;
    }

    const payload: Record<string, unknown> = {
      from: this.emailFrom.trim(),
      to: this.emailTo.trim(),
      cc: this.emailCc.trim(),
      bcc: this.emailBcc.trim(),
      subject: this.emailSubject.trim(),
      body: this.emailBody.trim(),
      status,
      attachmentIds: this.composeAttachments().map(a => a.id)
    };
    if (this.replyToEmailId != null) payload['inReplyToId'] = this.replyToEmailId;

    const base = `${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/emails`;
    const request$ = this.editingDraftId != null
      ? this.http.put<any>(`${base}/${this.editingDraftId}`, payload)
      : this.http.post<any>(base, payload);

    this.emailSaving.set(true);
    request$.subscribe({
      next: (res) => {
        this.emailSaving.set(false);
        const saved: ComplaintEmail | null = res?.data || null;
        this.resetEmailForm();
        this.emailComposeMode.set(false);

        // Expand whichever section the row actually landed in. A just-sent mail is PENDING and belongs to
        // Open Activities, not Closed - expanding the wrong one makes it look like the mail vanished.
        if (status === EMAIL_STATUS_DRAFT) {
          this.emailDraftExpanded = true;
        } else if (saved?.status === EMAIL_STATUS_SENT) {
          this.emailClosedExpanded = true;
        } else {
          this.emailOpenActivitiesExpanded = true;
        }

        this.messageService.add({
          severity: 'success',
          summary: this.submitSuccessSummary(status, saved),
          detail: res?.message || this.submitSuccessDetail(status, saved),
          life: 4000
        });

        this.loadEmailThreads(true);
        if (saved) this.selectEmailActivity(saved);
      },
      error: (err) => {
        this.emailSaving.set(false);
        const detail = this.errorDetail(err, status === EMAIL_STATUS_DRAFT
          ? 'Could not save the draft. Your text is still here.'
          : 'Could not send the email. Your text is still here.');
        this.emailFormError.set(detail);
        this.messageService.add({
          severity: 'error',
          summary: status === EMAIL_STATUS_DRAFT ? 'Draft not saved' : 'Email not sent',
          detail,
          life: 6000
        });
      }
    });
  }

  private submitSuccessSummary(status: string, saved: ComplaintEmail | null): string {
    if (status === EMAIL_STATUS_DRAFT) return 'Draft saved';
    // Never claim "sent" for a mail that is only queued.
    return saved?.status === EMAIL_STATUS_SENT ? 'Email sent' : 'Email queued';
  }

  private submitSuccessDetail(status: string, saved: ComplaintEmail | null): string {
    if (status === EMAIL_STATUS_DRAFT) {
      return 'The draft has been saved and will be here when you return.';
    }
    return saved?.status === EMAIL_STATUS_SENT
      ? 'The email has been sent.'
      : 'The email has been queued for sending. The list will update once it goes out.';
  }

  /** Queues a failed mail again. Without this a failed mail cannot be edited, replied to, or resent. */
  retryEmail(email: ComplaintEmail) {
    if (!email?.canRetry || !this.actualComplaintNumber || this.emailSaving()) return;

    this.emailSaving.set(true);
    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/${this.actualComplaintNumber}/emails/${email.id}/retry`, {}
    ).subscribe({
      next: (res) => {
        this.emailSaving.set(false);
        this.emailOpenActivitiesExpanded = true;
        this.messageService.add({
          severity: 'success',
          summary: 'Email queued',
          detail: res?.message || 'The email has been queued for another attempt.',
          life: 4000
        });
        this.loadEmailThreads(true);
        const saved: ComplaintEmail | null = res?.data || null;
        if (saved) this.selectEmailActivity(saved);
      },
      error: (err) => {
        this.emailSaving.set(false);
        this.notifyError(err, 'Could not queue this email for another attempt.');
      }
    });
  }

  /**
   * Mirrors the server's ComplaintEmailRequest constraints so the common mistakes are caught without a
   * round trip. A draft is held to the same rules: the columns it is stored in are the same either way.
   */
  private validateEmailForm(): string {
    const emailList = /^\s*[^@\s,;]+@[^@\s,;]+\.[^@\s,;]{2,}\s*([,;]\s*[^@\s,;]+@[^@\s,;]+\.[^@\s,;]{2,}\s*)*$/;

    if (!this.emailFrom?.trim()) return 'From address is required.';
    if (!emailList.test(this.emailFrom.trim())) return 'From must be a valid email address.';
    if (!this.emailTo?.trim()) return 'At least one recipient is required.';
    if (!emailList.test(this.emailTo.trim())) return 'To must be a valid email address, or several separated by commas.';
    if (this.emailTo.trim().length > 200) return 'The recipient list must be at most 200 characters.';
    if (this.emailCc?.trim() && !emailList.test(this.emailCc.trim())) return 'CC must be a valid email address, or several separated by commas.';
    if (this.emailBcc?.trim() && !emailList.test(this.emailBcc.trim())) return 'BCC must be a valid email address, or several separated by commas.';
    if (!this.emailSubject?.trim()) return 'Subject is required.';
    if (this.emailSubject.trim().length > 500) return 'The subject must be at most 500 characters.';
    if (!this.emailBody?.trim()) return 'The email body cannot be empty.';
    if (this.emailBody.trim().length > 20000) return 'The email body must be at most 20000 characters.';
    return '';
  }

  /** The server sends a readable reason in ApiResponse.message; prefer it over a generic failure line. */
  private errorDetail(err: any, fallback: string): string {
    const message = err?.error?.message;
    return typeof message === 'string' && message.trim() ? message : fallback;
  }

  private notifyError(err: any, fallback: string) {
    this.messageService.add({
      severity: 'error',
      summary: 'Something went wrong',
      detail: this.errorDetail(err, fallback),
      life: 6000
    });
  }

  goBack() {
    this.navService.goBack([this.dept.cfg().routePrefix]);
  }

  goToDraft() {
    this.router.navigate(['/crpc/draft', this.createdComplaintId()]);
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
    // The file input lives in the right sidebar, outside .left-main-area's view-only-locked CSS.
    if (!this.canMutate()) return;
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

private blankLegalCaseForm() {
  return {
    caseNumber: '', courtName: '', partiesOfCase: '', regionOfLegalTeam: '',
    rbiFirstRespondent: null as boolean | null, appearanceRequired: null as boolean | null,
    subjectMatter: '', advocateName: '', assistantLegalAdvisor: '', nextHearingDate: '',
    presentStatus: '', actionTakenSoFar: '', actionToBeTaken: '', monetaryClaimDetails: ''
  };
}

loadLegalCase(): void {
  if (!this.actualComplaintNumber) {
    this.legalCase.set(null);
    return;
  }
  this.loadingLegalCase.set(true);
  this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/legal-case`,
    { params: { complaintNumber: this.actualComplaintNumber } }).subscribe({
    next: (res) => {
      this.legalCase.set(res?.data || null);
      this.loadingLegalCase.set(false);
    },
    error: () => {
      this.legalCase.set(null);
      this.loadingLegalCase.set(false);
    }
  });
}

openLegalCaseEditor(): void {
  const lc = this.legalCase();
  this.legalCaseForm = lc ? {
    caseNumber: lc.caseNumber || '',
    courtName: lc.courtName || '',
    partiesOfCase: lc.partiesOfCase || '',
    regionOfLegalTeam: lc.regionOfLegalTeam || '',
    rbiFirstRespondent: lc.rbiFirstRespondent,
    appearanceRequired: lc.appearanceRequired,
    subjectMatter: lc.subjectMatter || '',
    advocateName: lc.advocateName || '',
    assistantLegalAdvisor: lc.assistantLegalAdvisor || '',
    nextHearingDate: lc.nextHearingDate || '',
    presentStatus: lc.presentStatus || '',
    actionTakenSoFar: lc.actionTakenSoFar || '',
    actionToBeTaken: lc.actionToBeTaken || '',
    monetaryClaimDetails: lc.monetaryClaimDetails || ''
  } : this.blankLegalCaseForm();
  this.legalCaseError.set('');
  this.showLegalCaseDialog.set(true);
  this.loadTerritoryOfficeList();
}

closeLegalCaseDialog(): void {
  this.showLegalCaseDialog.set(false);
}

/** Mirrors the backend's `@Pattern`/`@FutureOrPresent` rules on {@code CepcLegalCaseRequest} so the
 * officer sees the same complaint before a network round trip, not just after one. Region needs no
 * check here — it's a closed dropdown, so the UI can't produce an invalid value. */
private validateLegalCaseForm(): string | null {
  const form = this.legalCaseForm;
  if (form.advocateName && !/^[a-zA-Z\s]*$/.test(form.advocateName)) {
    return 'Name of the Advocate may contain only letters and spaces.';
  }
  if (form.caseNumber && !/^[A-Za-z0-9/_\- ]*$/.test(form.caseNumber)) {
    return 'Case No./WP No & Year may contain only letters, digits, spaces, and / - _.';
  }
  if (form.nextHearingDate) {
    const today = new Date().toISOString().slice(0, 10);
    if (form.nextHearingDate < today) {
      return 'Date of next hearing must be today or a later date.';
    }
  }
  return null;
}

saveLegalCase(): void {
  if (this.legalCaseSaving() || !this.actualComplaintNumber) return;

  const validationError = this.validateLegalCaseForm();
  if (validationError) {
    this.legalCaseError.set(validationError);
    return;
  }

  this.legalCaseSaving.set(true);
  this.legalCaseError.set('');
  // An empty date input is '', which the server's typed LocalDate field cannot parse — null means
  // "not set" to that field the same way it does to every other blank field here.
  const payload = { ...this.legalCaseForm, nextHearingDate: this.legalCaseForm.nextHearingDate || null };
  this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/legal-case`,
    payload, { params: { complaintNumber: this.actualComplaintNumber } }).subscribe({
    next: () => {
      this.legalCaseSaving.set(false);
      this.closeLegalCaseDialog();
      this.loadLegalCase();
    },
    error: (err) => {
      this.legalCaseSaving.set(false);
      this.legalCaseError.set(err?.error?.message || 'Could not save the legal case details.');
    }
  });
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
