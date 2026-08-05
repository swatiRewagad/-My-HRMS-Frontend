import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(KeycloakAuthService);

  // Header
  complaintId = '';
  loggedInUserName = '';
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
  summarySections = { basic: true, eligibility: false, entity: false, complainant: false };
  createdComplaintId = signal('');

  // Validation
  formSubmitAttempted = false;
  fieldErrors: Record<string, string> = {};

  // Assessment panel
  sendToDeputy = false;
  assessmentComment = '';
  proposedAction = '';
  proposedClause = '';
  showApprovalMenu = signal(false);
  showApprovalDialog = signal(false);
  approvalTarget = signal<'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN'>('REVIEWER');
  approvalAssignmentMode = 'AUTOMATIC';
  approvalCrpcAction = '';
  approvalCrpcClause = '';
  assessmentComments: { id: number; initials: string; author: string; time: string; text: string; color: string }[] = [
    { id: 1, initials: 'ST', author: 'Full Name BO DO', time: '8 hrs ago', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers. These systems provide a centralized platform', color: '#6366f1' },
    { id: 2, initials: 'ST', author: 'Full Name', time: '1 hrs ago', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers.', color: '#f59e0b' },
  ];

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
    this.loadStates();
    this.loadDeos();
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

  sendForApproval(target: 'REVIEWER' | 'DEPUTY_OMBUDSMAN' | 'OMBUDSMAN') {
    this.showApprovalMenu.set(false);
    this.approvalTarget.set(target);
    this.approvalAssignmentMode = 'AUTOMATIC';
    this.approvalCrpcAction = '';
    this.approvalCrpcClause = '';
    this.showApprovalDialog.set(true);
  }

  get approvalTargetLabel(): string {
    switch (this.approvalTarget()) {
      case 'REVIEWER': return 'RBIO Reviewer';
      case 'DEPUTY_OMBUDSMAN': return 'RBIO Deputy Ombudsman';
      case 'OMBUDSMAN': return 'RBIO Ombudsman';
    }
  }

  get approvalStatusLabel(): string {
    switch (this.approvalTarget()) {
      case 'REVIEWER': return 'Sent to RBIO Reviewer';
      case 'DEPUTY_OMBUDSMAN': return 'Sent to RBIO Deputy Ombudsman';
      case 'OMBUDSMAN': return 'Sent to RBIO Ombudsman';
    }
  }

  confirmApproval() {
    this.showApprovalDialog.set(false);
  }

  cancelApproval() {
    this.showApprovalDialog.set(false);
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

  goBack() {
    this.router.navigate(['/staff/rbio/tasks']);
  }

  goToDraft() {
    this.router.navigate(['/crpc/draft', this.createdComplaintId()]);
  }
}
