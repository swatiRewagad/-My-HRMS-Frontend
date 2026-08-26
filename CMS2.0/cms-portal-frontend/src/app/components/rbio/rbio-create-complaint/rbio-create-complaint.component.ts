import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { lookupPincode } from '../../../utils/pincode-data';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

interface DeoUser {
  id: string;
  displayName: string;
  isActive: boolean;
  isOnLeave: boolean;
  currentLoad: number;
  maxLoad: number;
}

@Component({
  selector: 'app-rbio-create-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './rbio-create-complaint.component.html',
  styleUrl: './rbio-create-complaint.component.scss'
})
export class RbioCreateComplaintComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(KeycloakAuthService);

  // Header
  complaintId = '';
  loggedInUserName = '';
  complaintOffice = '';
  slaDaysRemaining = signal(30);
  userRole = signal<'DO' | 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN' | 'HEAD'>('DO');
  activeTab = signal<'creation' | 'assignment'>('creation');

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

  // Eligibility
  proposedComplaintType = 'NEW_COMPLAINT';
  category = '';
  eligibilityEntityName = '';
  eligibilityEntitySearch = '';
  eligibilityEntityResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  showEligibilityEntityDropdown = signal(false);
  private eligibilityEntityTimeout: any = null;
  markAllEligible = false;
  eligibilityQuestions: { key: string; label: string; answer: boolean | null }[] = [
    { key: 'entityRegulatedByRbi', label: 'Is Entity regulated by RBI?', answer: null },
    { key: 'notDirectlyAddressed', label: 'The Complaint not directly addressed to Ombudsman', answer: null },
    { key: 'notRegisteredWithEntity', label: 'Is the Complaint not registered with Entity (FRC)?', answer: null },
    { key: 'frivolousVexatious', label: 'Is the complainant frivolous, vexatious, and threatening?', answer: null },
    { key: 'subJudice', label: 'Is the Complaint Sub-Judice or under arbitration?', answer: null },
    { key: 'isAdvocate', label: 'Is the complainant an advocate?', answer: null },
    { key: 'alreadyDealt', label: 'Has already been dealt with or is under process on the same ground with the ombudsman?', answer: null },
    { key: 'generalAgainstManagement', label: 'Does the complaint involve general complaints against management or executives of a RE?', answer: null },
    { key: 'disputesBetweenREs', label: 'Does it involve disputes between REs?', answer: null },
    { key: 'staffEmployerRelationship', label: 'Is from staff of an RE and involves employer-employee relationship?', answer: null },
    { key: 'incompleteInformation', label: 'Complete information not available for registering the complaint', answer: null },
  ];

  // Entity Details
  entityName = '';
  entityType = 'BANK';
  entitySearchText = '';
  entitySearchResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  entitySearchLoading = signal(false);
  showEntityDropdown = signal(false);
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

  // Financial
  amountInvolved: number | null = null;
  transactionDate = '';

  // Assignment
  assignmentMode = 'AUTOMATIC';
  selectedDeoId = '';
  selectedDeoName = 'CRPC DEO';
  deos = signal<DeoUser[]>([]);
  showConfirmDialog = signal(false);

  // Pincode lookup
  pincodeLoading = signal(false);

  // State
  saving = signal(false);
  submitting = signal(false);
  submitted = signal(false);
  draftSaved = signal(false);
  summaryActiveTab = signal<'summary' | 'email'>('summary');
  assessmentTab = signal<string>('summary');
  editMode = signal(false);
  rightPanelTab = signal<'assessment' | 'attachments' | 'history' | 'settings'>('assessment');
  summarySections = { basic: true, eligibility: false, entity: false, complainant: false };
  createdComplaintId = signal('');

  // Final Decision
  finalDecisionAction = '';
  finalDecisionRemarks = '';
  finalDecisionSubmitting = signal(false);
  showFinalDecisionPreview = signal(false);
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
  // True when the complaint has moved on to a different officer than the one viewing it —
  // full details still render, but action buttons are disabled/hidden.
  isReadOnlyViewer = signal(false);
  // True only right after this session performed an action (forward/close/send back/etc.) —
  // gates the one-time success banner so simply loading/revisiting an already-closed or
  // already-forwarded complaint shows the full read-only detail view instead.
  justActioned = signal(false);
  workflowAction = signal('');
  conciliationEnabled = computed(() => {
    if (this.userRole() !== 'DO') return true;
    const action = this.workflowAction();
    const status = this.complaintStatus();
    const excludedStatuses = ['ADVISORY_COMPLIED', 'COMPLAINT_SETTLED', 'COMPLAINT_WITHDRAWN', 'COMPLAINT_REJECTED', 'AWARD_PASSED', 'OMBUDSMAN_DECISION'];
    if (excludedStatuses.includes(status)) return false;
    return action === 'MAINTAINABLE';
  });
  approvalSentTo = signal('');
  showApprovalMenu = signal(false);
  showSendBackMenu = signal(false);
  // Menus render fixed-position (computed from the trigger button) so nested scroll/overflow
  // containers in the layout can never clip an option out of view.
  approvalMenuPos = signal({ top: 0, left: 0 });
  sendBackMenuPos = signal({ top: 0, left: 0 });

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
  assessmentComments = signal<{ id: number; initials: string; author: string; time?: string; text: string; color: string; role?: string; createdAt?: string }[]>([]);

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
      // Goes through the real inter-office transfer queue (/crpc/ops-head), which the
      // CRPC Head actually works from — not the generic send-for-approval endpoint.
      const fromOffice = this.complaintOffice || '';
      const toOffice = this.forwardOfficeCode;
      const deptOf = (code: string) => (code || '').split('-')[0] || 'RBIO';
      const payload = {
        complaintNumber: this.complaintId,
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

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/send-for-approval`, payload).subscribe({
      next: () => {
        this.forwardSubmitting.set(false);
        this.showForwardConfirm.set(false);
        this.approvalSentTo.set(this.forwardTargetLabel);
        this.justActioned.set(true);
        this.complaintStatus.set('CLOSED');
      },
      error: () => {
        this.forwardSubmitting.set(false);
        this.showForwardConfirm.set(false);
        this.approvalSentTo.set(this.forwardTargetLabel);
        this.justActioned.set(true);
        this.complaintStatus.set('CLOSED');
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
  nodalRecords = signal<{ id: number; recordNumber: string; subject: string; bankName: string; slaDays: number; assignedTo: string; status: string; statusLabel: string; complaintNumber: string; receiptDate: string; complainant: string; mobile: string; email: string; moduleName: string; bankCategory: string; branchCategory: string; branchName: string; pincode: string; city: string; district: string; state: string; country: string; noName: string; noMobile: string; noEmail: string; pnoName: string; pnoMobile: string; pnoEmail: string; atmComplaint: string; designatedOffice: string; processingOffice: string }[]>([
    { id: 1, recordNumber: '1146110', subject: 'Account debited but no credit', bankName: 'State Bank of India', slaDays: 2, assignedTo: 'Priya Gupta', status: 'INFORMATION_REQUIRED', statusLabel: 'Information Required', complaintNumber: 'N20223317000005', receiptDate: '19-05-2026', complainant: 'Raj Shah', mobile: '9876543210', email: 'raj.shah@email.com', moduleName: 'Deposit', bankCategory: 'Scheduled Commercial Bank', branchCategory: 'Metro', branchName: 'Andheri West', pincode: '400058', city: 'Mumbai', district: 'Mumbai Suburban', state: 'Maharashtra', country: 'India', noName: 'Deepak Verma', noMobile: '9112233445', noEmail: 'deepak.verma@sbi.co.in', pnoName: 'Suresh Kumar', pnoMobile: '9998877665', pnoEmail: 'suresh.kumar@sbi.co.in', atmComplaint: 'No', designatedOffice: 'Mumbai', processingOffice: 'RBIO Mumbai' },
    { id: 2, recordNumber: '1146111', subject: 'Excess interest charged on loan', bankName: 'HDFC Bank', slaDays: 5, assignedTo: 'A.K. Singh', status: 'PENDING', statusLabel: 'Pending', complaintNumber: 'N20223317000012', receiptDate: '22-05-2026', complainant: 'Meena Kumari', mobile: '9123456780', email: 'meena.k@email.com', moduleName: 'Loan', bankCategory: 'Private Sector Bank', branchCategory: 'Urban', branchName: 'Connaught Place', pincode: '110001', city: 'New Delhi', district: 'Central Delhi', state: 'Delhi', country: 'India', noName: 'Rahul Sharma', noMobile: '9887766554', noEmail: 'rahul.sharma@hdfc.com', pnoName: 'Anita Desai', pnoMobile: '9776655443', pnoEmail: 'anita.desai@hdfc.com', atmComplaint: 'No', designatedOffice: 'Delhi', processingOffice: 'RBIO Delhi' },
    { id: 3, recordNumber: '1146112', subject: 'ATM withdrawal failed but debited', bankName: 'ICICI Bank', slaDays: 35, assignedTo: 'Meera Krishnan', status: 'INFORMATION_REQUIRED', statusLabel: 'Information Required', complaintNumber: 'N20223317000018', receiptDate: '10-05-2026', complainant: 'Sunil Patil', mobile: '9234567890', email: 'sunil.p@email.com', moduleName: 'ATM/Debit Card', bankCategory: 'Private Sector Bank', branchCategory: 'Semi-Urban', branchName: 'Baner Road', pincode: '411045', city: 'Pune', district: 'Pune', state: 'Maharashtra', country: 'India', noName: 'Vikram Joshi', noMobile: '9665544332', noEmail: 'vikram.joshi@icici.com', pnoName: 'Kavita Nair', pnoMobile: '9554433221', pnoEmail: 'kavita.nair@icici.com', atmComplaint: 'Yes', designatedOffice: 'Pune', processingOffice: 'RBIO Mumbai' },
  ]);
  nodalFilterRecordNumber = '';
  nodalFilterSubject = '';
  nodalFilterBank = '';
  nodalFilterSla = '';
  nodalFilterAssigned = '';
  nodalFilterStatus = '';
  selectedNodalRecord = signal<any>(null);
  nodalDetailView = signal(false);
  nodalStatusCode = '';
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
  categories = [
    'ATM/Debit Card', 'Credit Card', 'UPI/Mobile Banking', 'Internet Banking',
    'Loan', 'Deposit', 'Insurance (Mis-selling)', 'NEFT/RTGS/IMPS',
    'Pension', 'Account Opening/Closure', 'CIBIL/Credit Score',
    'Cheque/DD', 'Locker', 'General Banking'
  ];

  states = signal<string[]>([]);
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

  protected Math = Math;

  ngOnInit() {
    this.complaintId = this.generateComplaintId();
    this.receivedDate = new Date().toISOString().split('T')[0];
    const user = this.auth.currentUser();
    this.loggedInUserName = user ? `${user.firstName || ''} ${user.lastName || ''}`.trim() || user.username : '';
    this.detectUserRole();
    this.detectUserOffice(user?.username || '');
    this.loadStates();
    this.loadDeos();

    const taskId = this.route.snapshot.paramMap.get('id');
    if (taskId) {
      this.loadExistingComplaint(taskId);
    }
  }

  private loadExistingComplaint(id: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const data = res?.data || res || {};
        this.complaintId = data.complaintNumber || data.id || id;
        this.subject = data.subject || '';
        this.description = data.description || data.body || '';
        this.comments = data.comments || '';
        this.complainantName = data.complainantName || '';
        this.complainantEmail = data.complainantEmail || data.senderEmail || '';
        this.complainantPhone = data.complainantPhone || '';
        this.complainantState = data.complainantState || '';
        this.complainantDistrict = data.complainantDistrict || '';
        this.complainantPincode = data.complainantPincode || '';
        this.complainantAddress = data.complainantAddress || '';
        this.entityName = data.entityName || '';
        this.entityType = data.entityType || 'BANK';
        this.entityCategory = data.entityCategory || '';
        this.bsrCode = data.bsrCode || '';
        this.entityPincode = data.entityPincode || '';
        this.entityState = data.entityState || '';
        this.entityDistrict = data.entityDistrict || '';
        this.entityCity = data.entityCity || '';
        this.entityBranchName = data.entityBranchName || '';
        this.entityBranchCategory = data.entityBranchCategory || '';
        this.entityAddress = data.entityAddress || '';
        this.cosmosCode = data.cosmosCode || '';
        this.category = data.category || '';
        this.modeOfReceipt = data.modeOfReceipt || data.filingType || 'Email';
        this.receivedDate = data.receivedAt ? data.receivedAt.split('T')[0] : '';
        const status = (data.status || '').toUpperCase();
        this.workflowAction.set((data.workflowAction || data.proposedAction || '').toUpperCase());
        this.proposedAction = data.proposedAction || '';
        this.proposedClause = data.proposedClause || '';
        this.clauseSearch = data.proposedClause || '';
        this.transferOfficeCode = data.forwardedOfficeCode || '';
        this.transferOfficeName = data.forwardedOfficeName || '';
        this.transferPreOfficer = data.preForwardOfficer || '';
        this.transferPreRole = data.preForwardRole || '';
        if (data.officeCode || data.assignedOffice) {
          this.complaintOffice = data.officeCode || data.assignedOffice;
        }
        if (data.slaDueDate) {
          const due = new Date(data.slaDueDate);
          const now = new Date();
          this.slaDaysRemaining.set(Math.max(0, Math.ceil((due.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))));
        }
        const currentUsername = this.auth.currentUser()?.username || '';
        const assignedTo = data.assignedTo || '';
        const stillOwner = !assignedTo || !currentUsername || assignedTo === currentUsername;
        // Complaint has moved on to someone else (e.g. forwarded to Reviewer) — the officer
        // who handled it earlier still sees the full complaint, just without action buttons.
        // A CLOSED complaint is read-only for everyone, regardless of who it's assigned to.
        this.isReadOnlyViewer.set(!stillOwner || status === 'CLOSED');
        // Loading (not actioning) a complaint never shows the one-time success banner.
        this.justActioned.set(false);
        if (status === 'CLOSED') {
          this.complaintStatus.set('CLOSED');
        } else if (['RESOLVED', 'REJECTED'].includes(status)) {
          this.complaintStatus.set('VIEW_ONLY');
        } else {
          this.complaintStatus.set(status || 'NEW_COMPLAINT');
        }
        this.submitted.set(true);
      },
      error: () => {
        this.complaintId = id;
        this.subject = 'ATM_DEBIT_CARD';
        this.description = 'Complaint loaded from task';
        this.complainantName = 'Complainant';
        this.complaintStatus.set('NEW_COMPLAINT');
        this.submitted.set(true);
      }
    });

    // Load comments from API
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}/comments`).subscribe({
      next: (res) => this.assessmentComments.set(res?.data || []),
      error: () => this.assessmentComments.set([])
    });

    // Load email communications from API
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}/emails`).subscribe({
      next: (res) => {
        const emails = res?.data || res || [];
        this.emailActivities.set(emails.filter((e: any) => e.status === 'OPEN' || e.status === 'SENT'));
        this.emailDrafts.set(emails.filter((e: any) => e.status === 'DRAFT'));
        this.emailClosedActivities.set(emails.filter((e: any) => e.status === 'CLOSED'));
      },
      error: () => {}
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
    } else if (roles.includes('RBIO_SUPERVISOR') || roles.includes('RBIO_REVIEWER')) {
      this.userRole.set('REVIEWER');
    } else {
      this.userRole.set('DO');
    }
  }

  private detectUserOffice(username: string) {
    // Office is assigned per-officer via Team Management (OfficerAvailability), not inferred from the username.
    if (!username) return;
    const roles = this.auth.getRoles ? this.auth.getRoles() : [];
    const rbioRole = roles.find((r: string) => r.startsWith('RBIO_')) || 'RBIO_OFFICER';
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

  private loadDeos() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/deo-pool`).subscribe({
      next: (res) => {
        const data = (res?.data || []).map((d: any) => ({
          id: d.userId || d.id,
          displayName: d.displayName || d.userId,
          isActive: d.isActive !== false,
          isOnLeave: d.isOnLeave === true,
          currentLoad: d.currentLoad || 0,
          maxLoad: d.maxLoad || d.maxThreshold || 20
        }));
        if (data.length > 0) {
          this.deos.set(data);
        } else {
          this.deos.set([
            { id: 'deo.user', displayName: 'Siddharth Joshi', isActive: true, isOnLeave: false, currentLoad: 0, maxLoad: 20 }
          ]);
        }
        const auto = this.deos().find(d => d.isActive && !d.isOnLeave);
        if (auto) {
          this.selectedDeoId = auto.id;
          this.selectedDeoName = auto.displayName;
        }
      },
      error: () => {
        this.deos.set([
          { id: 'deo.user', displayName: 'Siddharth Joshi', isActive: true, isOnLeave: false, currentLoad: 0, maxLoad: 20 }
        ]);
        this.selectedDeoId = 'deo.user';
        this.selectedDeoName = 'Siddharth Joshi';
      }
    });
  }

  private loadStates() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/location/states`).subscribe({
      next: (res) => {
        const data = res?.data || [];
        this.states.set(data.length > 0 ? data : this.statesFallback);
      },
      error: () => this.states.set(this.statesFallback)
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
            this.applyLocalPincode(value);
          }
        },
        error: () => {
          this.pincodeLoading.set(false);
          this.applyLocalPincode(value);
        }
      });
    }
  }

  private applyLocalPincode(value: string) {
    const entry = lookupPincode(value);
    if (entry) {
      this.complainantState = entry.state;
      this.onStateChange(entry.state);
      this.complainantDistrict = entry.district;
      delete this.fieldErrors['complainantPincode'];
    } else {
      this.fieldErrors['complainantPincode'] = 'Invalid pincode. No location found.';
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
        if (q.key === 'entityRegulatedByRbi') {
          q.answer = true;
        } else {
          q.answer = false;
        }
      }
    } else {
      for (const q of this.eligibilityQuestions) {
        q.answer = null;
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

  selectEntity(entity: { id: number; name: string; department: string; entityType: string }) {
    this.entityName = entity.name;
    this.entitySearchText = entity.name;
    this.entityType = entity.entityType || 'BANK';
    this.showEntityDropdown.set(false);
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
      'DEALING_OFFICER': 'RBIO_OFFICER',
      'REVIEWER': 'RBIO_SUPERVISOR',
      'DEPUTY_OMBUDSMAN': 'RBIO_DEPUTY_OMBUDSMAN',
      'OMBUDSMAN': 'RBIO_ADJUDICATOR'
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
      'DEALING_OFFICER': 'RBIO_OFFICER',
      'REVIEWER': 'RBIO_SUPERVISOR'
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
      performedBy: this.auth.currentUser()?.username || ''
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/send-for-approval`, payload).subscribe({
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

  get approvalStatusLabel(): string {
    switch (this.approvalTarget()) {
      case 'DEALING_OFFICER': return 'Deputy Ombudsman Decision';
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
      proposedAction: this.proposedAction || null,
      proposedClause: this.proposedClause || null
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/send-for-approval`, payload).subscribe({
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

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintId}/send-for-approval`, payload).subscribe({
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
    this.showConfirmDialog.set(true);
  }

  onAssignmentModeChange() {
    if (this.assignmentMode === 'AUTOMATIC') {
      const auto = this.deos().find(d => d.isActive && !d.isOnLeave);
      this.selectedDeoId = auto?.id || '';
      this.selectedDeoName = auto?.displayName || 'CRPC DEO';
    } else {
      this.selectedDeoId = '';
      this.selectedDeoName = '';
    }
  }

  onDeoSelect(deoId: string) {
    this.selectedDeoId = deoId;
    const deo = this.deos().find(d => d.id === deoId);
    this.selectedDeoName = deo?.displayName || '';
  }

  deoOnLeave(): boolean {
    if (!this.selectedDeoId) return false;
    const deo = this.deos().find(d => d.id === this.selectedDeoId);
    return deo?.isOnLeave || false;
  }

  confirmAssignment() {
    if (!this.selectedDeoId.trim()) return;
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
      error: () => {
        this.createdComplaintId.set(this.complaintId);
        this.submitting.set(false);
        this.submitted.set(true);
        this.showConfirmDialog.set(false);
      }
    });
  }

  cancelAssignment() {
    this.showConfirmDialog.set(false);
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
      createdBy: this.auth.currentUser()?.username || '',
      status
    };
  }

  addNodalRecord() {
    const newId = this.nodalRecords().length + 1;
    const newRecord = {
      id: newId, recordNumber: `114611${newId + 2}`, subject: 'New Record', bankName: '', slaDays: 30, assignedTo: this.loggedInUserName || 'Unassigned', status: 'PENDING', statusLabel: 'Pending', complaintNumber: `N${this.complaintId}`, receiptDate: new Date().toLocaleDateString('en-GB').replace(/\//g, '-'), complainant: this.complainantName || '', mobile: this.complainantPhone || '', email: this.complainantEmail || '', moduleName: '', bankCategory: '', branchCategory: '', branchName: '', pincode: '', city: '', district: '', state: '', country: 'India', noName: '', noMobile: '', noEmail: '', pnoName: '', pnoMobile: '', pnoEmail: '', atmComplaint: 'No', designatedOffice: '', processingOffice: ''
    };
    this.nodalRecords.set([...this.nodalRecords(), newRecord]);
  }

  openNodalDetail(record: any) {
    this.selectedNodalRecord.set(record);
    this.nodalStatusCode = record.status === 'INFORMATION_REQUIRED' ? 'INFORMATION_REQUIRED' : '';
    const complyBy = new Date();
    complyBy.setDate(complyBy.getDate() + 15);
    this.nodal131ComplyDate = `${String(complyBy.getDate()).padStart(2, '0')}-${String(complyBy.getMonth() + 1).padStart(2, '0')}-${complyBy.getFullYear()}`;
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
  }

  sendToRE() {
    this.closeNodalDetail();
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

  toggleEditMode() {
    this.editMode.set(!this.editMode());
  }

  // Email Communication methods
  selectEmailActivity(activity: any) {
    this.selectedEmailActivity.set(activity);
    this.emailComposeMode.set(false);
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
      error: () => {}
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
      error: () => {}
    });
    this.emailComposeMode.set(false);
  }

  goBack() {
    this.router.navigate(['/staff/rbio/tasks']);
  }

  goToDraft() {
    this.router.navigate(['/crpc/draft', this.createdComplaintId()]);
  }
}
