import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { CrpcService } from '../../../services/crpc.service';
import { ReviewerUser } from '../../../models/crpc.model';
import { lookupPincode } from '../../../utils/pincode-data';
import { environment } from '../../../../environments/environment';
interface Suggestion {
  id: string;
  field: string;
  value: string;
}

interface PastComplaint {
  complaintNumber: string;
  subject: string;
  entityName: string;
  date: string;
}

@Component({
  selector: 'app-physical-letter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physical-letter.component.html',
  styleUrl: './physical-letter.component.scss'
})
export class PhysicalLetterComponent implements OnInit {

  private router = inject(Router);
  private http = inject(HttpClient);
  private sanitizer = inject(DomSanitizer);
  private auth = inject(KeycloakAuthService);
  private crpcService = inject(CrpcService);

  // Header
  complaintNumber = '';
  assignedOfficer = '';
  activeStep = signal<'creation' | 'assignment'>('creation');
  loggedInUser: { id: string; name: string; role: string } | null = null;

  // Left panel
  scannedFile: File | null = null;
  scanError = '';
  isDragOver = false;
  ocrInProgress = signal(false);
  ocrComplete = signal(false);
  pdfExpanded = signal(false);
  pdfPage = signal(1);
  pdfPreviewUrl = signal<SafeResourceUrl | null>(null);
  imagePreviewUrl = signal<string | null>(null);

  // Form fields
  subject = '';
  description = '';
  comments = '';
  modeOfReceipt = 'PHYSICAL_LETTER';
  receivedDate = '';
  letterDate = '';
  category = '';
  proposedComplaintType: 'NEW_COMPLAINT' | 'NOT_A_COMPLAINT' = 'NEW_COMPLAINT';
  notComplaintReason = '';
  complaintType = 'COMPLAINT';
  isRbiEComplaint = 'NO';
  nonEComplaintReason = '';

  // Eligibility questions
  eligibilityQuestions = [
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
  markAllEligible = false;
  entityName = '';
  entityType = 'BANK';
  entityCategory = '';
  entityTypeDetail = '';
  entitySearchText = '';
  entitySearchResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  entitySearchLoading = signal(false);
  showEntityDropdown = signal(false);
  private entitySearchTimeout: any = null;
  branchName = '';
  branchPincode = '';
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
  assetSize: number | null = null;
  isDepositTaking = '';
  isAssetAbove100Cr = '';
  isLiquidated = '';
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantState = '';
  complainantAddress = '';
  complainantDistrict = '';
  complainantPincode = '';
  amountInvolved: number | null = null;
  transactionDate = '';

  // Basic Identification
  otherEntityName = '';
  dateOfRegistrationWithRBI = '';

  // Complaint Classification
  complaintCategory = '';
  complaintSubCategory1 = '';
  complaintSubCategory2 = '';
  dateOfFilingComplaint = '';
  complaintRegDateValid = 'YES';

  // Reminder & Financial Details
  reminderSentByComplainant = 'YES';
  disputedAmountInvolved: number | null = null;
  dateOfFilingForFinancial = '';
  compensationSought = 'YES';

  // Legal & Case Details
  legalCaseFiled = 'YES';
  legalDateOfFiling = '';
  preEnquiryReceived = 'YES';
  highPriorityComplaint = 'NO';

  // Additional Information
  additionalComments = '';
  crpcProposedAction = 'Maintainable';
  vernacularLanguageDetail = '';
  additionalDateOfFiling = '';

  // Loan / Disposal Account
  loanDisposalAmount: number | null = null;

  // Flags & Indicators
  isRegardingPension = 'YES';
  isAgainstBusinessCorrespondent = 'YES';
  isAtmCreditDebitCard = 'NO';
  schemeFlag = '';

  // Complaint Linkage
  isFreeMarkedComplaint = 'YES';
  currentComplaintNumber = '';
  receivedReplyWithin30Days = 'NOT_APPLICABLE';

  // Declaration
  declarationAccepted = false;

  // Right panel
  suggestions = signal<Suggestion[]>([]);
  pastComplaints = signal<PastComplaint[]>([]);
  pastSearch = '';

  // Past Complaint Detail Modal
  showPastComplaintDetail = signal(false);
  pastComplaintDetail = signal<any>(null);
  loadingPastDetail = signal(false);

  // Assignment
  assignmentMode = 'AUTOMATIC';
  selectedReviewerId = '';
  selectedReviewerName = 'CRPC Reviewer';
  reviewers = signal<ReviewerUser[]>([]);
  showAssignmentDialog = signal(false);

  // Section collapse state
  collapsedSections: Record<string, boolean> = {};

  // Pincode lookup
  pincodeLoading = signal(false);

  // State
  saving = signal(false);
  submitting = signal(false);
  submitted = signal(false);
  draftSaved = signal(false);
  editMode = signal(true);
  draftId = signal('');

  // Reference data
  categories = [
    'ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'
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
    this.receivedDate = new Date().toISOString().split('T')[0];
    const stored = sessionStorage.getItem('crpc_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r => ['REVIEWER', 'CRPC_HEAD', 'DEO'].includes(r)) || 'DEO';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
      }
    }
    this.loadPastComplaints();
    this.loadStates();
    this.loadReviewers();
  }

  goToAssignment() {
    this.activeStep.set('assignment');
  }

  goToCreation() {
    this.activeStep.set('creation');
  }

  private loadReviewers() {
    this.crpcService.getReviewers().subscribe(data => {
      if (data.length > 0) {
        this.reviewers.set(data);
      } else {
        this.reviewers.set([
          { id: 'reviewer.user', displayName: 'A.K. Singh', email: '', isActive: true, isOnLeave: false, maxLoad: 25, currentLoad: 0, region: '', sortOrder: 1 },
        ]);
      }
      const auto = this.reviewers().find(r => r.isActive && !r.isOnLeave);
      if (auto) {
        this.selectedReviewerId = auto.id;
        this.selectedReviewerName = auto.displayName;
      }
    });
  }

  loadStates() {
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
            if (po.District) {
              this.complainantDistrict = po.District;
            }
          } else {
            this.fallbackPincodeLookup(value);
          }
        },
        error: () => {
          this.fallbackPincodeLookup(value);
        }
      });
    }
  }

  private fallbackPincodeLookup(pincode: string) {
    this.pincodeLoading.set(false);
    const entry = lookupPincode(pincode);
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
    this.pincodePostOffices = [];
    this.showBranchDropdown = false;
    if (!value || value.length !== 6 || !/^\d{6}$/.test(value)) return;

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

  toggleSection(section: string) {
    this.collapsedSections[section] = !this.collapsedSections[section];
  }

  toggleMarkAllEligible() {
    this.markAllEligible = !this.markAllEligible;
    if (this.markAllEligible) {
      this.eligibilityQuestions.forEach((q, i) => q.answer = i === 0 ? 'YES' : 'NO');
    } else {
      this.eligibilityQuestions.forEach(q => q.answer = '');
    }
  }

  areRemainingQuestionsOptional(): boolean {
    return this.eligibilityQuestions[0]?.answer === 'YES' && this.eligibilityQuestions[1]?.answer === 'YES';
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
    this.setFilePreview(file);
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    const file = event.dataTransfer.files[0];
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
    this.setFilePreview(file);
  }

  private setFilePreview(file: File) {
    if (file.type === 'application/pdf') {
      const url = URL.createObjectURL(file);
      this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
      this.imagePreviewUrl.set(null);
    } else if (file.type.startsWith('image/')) {
      const url = URL.createObjectURL(file);
      this.imagePreviewUrl.set(url);
      this.pdfPreviewUrl.set(null);
    }
  }

  removeFile() {
    this.scannedFile = null;
    this.pdfPreviewUrl.set(null);
    this.imagePreviewUrl.set(null);
    this.ocrComplete.set(false);
  }

  runOcr() {
    if (!this.scannedFile) return;
    this.ocrInProgress.set(true);
    this.scanError = '';

    const formData = new FormData();
    formData.append('file', this.scannedFile);

    this.http.post<any>(`${environment.ocrServiceUrl}/v1/documents`, formData)
      .subscribe({
        next: (res) => {
          const envelope = res?.result;
          if (!envelope) {
            this.ocrInProgress.set(false);
            this.scanError = 'AI extraction returned no data. Please fill manually or try again later.';
            return;
          }

          if (envelope.bounce_decision?.decision === 'bounce') {
            this.ocrInProgress.set(false);
            this.scanError = `Document rejected: ${envelope.bounce_decision.detail || envelope.bounce_decision.reason || 'Quality too low'}`;
            return;
          }

          const fields = envelope.document?.fields || {};
          const summary = envelope.document?.complaint_summary;

          if (fields.complainant_name?.value) this.complainantName = fields.complainant_name.value_en || fields.complainant_name.value;
          if (fields.bank_name?.value) this.entityName = fields.bank_name.value;
          if (fields.amount?.normalized) this.amountInvolved = Number(fields.amount.normalized) || null;
          if (fields.transaction_date?.normalized) this.transactionDate = fields.transaction_date.normalized;
          if (fields.complainant_address?.value) this.complainantAddress = fields.complainant_address.value_en || fields.complainant_address.value;
          if (fields.complainant_phone?.value) this.complainantPhone = fields.complainant_phone.value;
          if (fields.complainant_email?.value) this.complainantEmail = fields.complainant_email.value;
          if (fields.complainant_state?.value) this.complainantState = fields.complainant_state.value;
          if (fields.complainant_district?.value) this.complainantDistrict = fields.complainant_district.value;
          if (fields.complainant_pincode?.value) this.complainantPincode = fields.complainant_pincode.value;
          if (fields.prior_complaint_reference?.value) this.subject = `Ref: ${fields.prior_complaint_reference.value}`;
          if (summary?.text) this.description = summary.text;

          const suggs: Suggestion[] = [];
          if (fields.bank_name?.value) suggs.push({ id: '1', field: 'Entity', value: fields.bank_name.value });
          if (fields.amount?.normalized) suggs.push({ id: '2', field: 'Amount', value: `₹${fields.amount.normalized}` });
          if (fields.ifsc_code?.value) suggs.push({ id: '3', field: 'IFSC', value: fields.ifsc_code.value });
          if (fields.account_number?.value) suggs.push({ id: '4', field: 'Account', value: fields.account_number.value });
          this.suggestions.set(suggs);

          this.ocrInProgress.set(false);
          this.ocrComplete.set(true);
        },
        error: (err) => {
          console.error('OCR extraction failed:', err);
          this.ocrInProgress.set(false);
          this.scanError = 'AI extraction failed: ' + (err.error?.detail || err.error?.message || 'Service unavailable. Please fill manually.');
        }
      });
  }

  skipOcr() {
    this.ocrComplete.set(true);
  }

  applySuggestion(s: Suggestion) {
    switch (s.field) {
      case 'Entity': this.entityName = s.value; break;
      case 'Category': this.category = 'CREDIT_CARD'; break;
      case 'Amount': this.amountInvolved = 15000; break;
    }
  }

  formSubmitAttempted = false;
  fieldErrors: Record<string, string> = {};

  validateForm(): boolean {
    this.fieldErrors = {};

    if (!this.subject.trim()) this.fieldErrors['subject'] = 'Subject is required.';
    if (!this.description.trim()) this.fieldErrors['description'] = 'Complaint Details is required.';
    if (!this.modeOfReceipt) this.fieldErrors['modeOfReceipt'] = 'Mode of Receipt is required.';
    if (!this.category.trim()) this.fieldErrors['category'] = 'Category is required.';
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
    if (this.branchPincode && !/^\d{6}$/.test(this.branchPincode)) {
      this.fieldErrors['branchPincode'] = 'Enter a valid 6-digit pincode.';
    }

    // Eligibility questions validation
    if (this.proposedComplaintType === 'NEW_COMPLAINT') {
      const first = this.eligibilityQuestions[0]?.answer;
      const second = this.eligibilityQuestions[1]?.answer;
      if (!first) this.fieldErrors['eq_0'] = 'First eligibility question is required.';
      if (!second) this.fieldErrors['eq_1'] = 'Second eligibility question is required.';
      if (!this.areRemainingQuestionsOptional()) {
        this.eligibilityQuestions.forEach((q, i) => {
          if (i >= 2 && !q.answer) {
            this.fieldErrors[`eq_${i}`] = `"${q.label}" is required.`;
          }
        });
      }
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  canSubmit(): boolean {
    return this.complainantName.trim().length > 0 &&
           this.subject.trim().length > 0 &&
           this.category.trim().length > 0;
  }

  saveDraft() {
    this.saving.set(true);
    setTimeout(() => {
      this.saving.set(false);
      this.draftSaved.set(true);
      this.editMode.set(false);
    }, 800);
  }

  enterEditMode() {
    this.editMode.set(true);
  }

  submitDraft() {
    this.formSubmitAttempted = true;
    if (!this.validateForm()) return;
    this.submitting.set(true);

    const loggedInUser = JSON.parse(sessionStorage.getItem('crpc_user') || '{}');
    const username = loggedInUser?.id || this.auth.currentUser()?.username || '';

    const formData = new FormData();
    this.appendAllFieldsToFormData(formData, 'DRAFT', username);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/physical-letter`, formData)
      .subscribe({
        next: (res) => {
          const newDraftId = res?.data?.draftId || res?.data?.id || '';
          this.draftId.set(newDraftId);
          this.submitting.set(false);
          this.submitted.set(true);

          sessionStorage.setItem('physicalLetterDraft', JSON.stringify({
            complainantName: this.complainantName,
            complainantPhone: this.complainantPhone,
            complainantEmail: this.complainantEmail,
            complainantAddress: this.complainantAddress,
            complainantState: this.complainantState,
            complainantDistrict: this.complainantDistrict,
            complainantPincode: this.complainantPincode,
            category: this.category,
            entityName: this.entityName,
            entityType: this.entityType,
            subject: this.subject,
            description: this.description,
            amountInvolved: this.amountInvolved,
            transactionDate: this.transactionDate,
            letterDate: this.letterDate,
            modeOfReceipt: this.modeOfReceipt,
            draftId: newDraftId,
            fileName: this.scannedFile?.name || 'scanned_letter.pdf',
            fileSize: this.scannedFile ? (this.scannedFile.size / 1024 / 1024).toFixed(2) + ' MB' : '2.4 MB',
          }));
        },
        error: () => {
          const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
          const rand = Math.floor(100000 + Math.random() * 900000);
          const fallbackId = `DRF-${dateStr}-${rand}`;
          this.draftId.set(fallbackId);
          this.submitting.set(false);
          this.submitted.set(true);

          sessionStorage.setItem('physicalLetterDraft', JSON.stringify({
            complainantName: this.complainantName,
            complainantPhone: this.complainantPhone,
            complainantEmail: this.complainantEmail,
            subject: this.subject,
            description: this.description,
            draftId: fallbackId,
            fileName: this.scannedFile?.name || 'scanned_letter.pdf',
            fileSize: this.scannedFile ? (this.scannedFile.size / 1024 / 1024).toFixed(2) + ' MB' : '2.4 MB',
          }));
        }
      });
  }

  private loadPastComplaints() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/recent?limit=10`)
      .subscribe({
        next: (res) => {
          const items = (res?.data || []).map((c: any) => ({
            complaintNumber: c.complaintNumber,
            subject: c.subject || 'N/A',
            entityName: c.entityName || 'N/A',
            date: c.date || '',
          }));
          this.pastComplaints.set(items);
        },
        error: () => {
          this.pastComplaints.set([]);
        }
      });
  }

  openPastComplaintDetail(complaintNumber: string) {
    this.showPastComplaintDetail.set(true);
    this.loadingPastDetail.set(true);
    this.pastComplaintDetail.set(null);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/past-complaints/detail/${complaintNumber}`)
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

  private storeEligibilityData(draftId: string) {
    const eqMap: Record<string, string> = {};
    this.eligibilityQuestions.forEach(q => { if (q.answer) eqMap[q.key] = q.answer; });
    sessionStorage.setItem(`draft_eligibility_${draftId}`, JSON.stringify({
      proposedComplaintType: this.proposedComplaintType,
      notComplaintReason: this.notComplaintReason,
      eligibilityQuestions: eqMap,
    }));
  }

  onAssignmentModeChange() {
    if (this.assignmentMode === 'AUTOMATIC') {
      const auto = this.reviewers().find(r => r.isActive && !r.isOnLeave);
      this.selectedReviewerId = auto?.id || '';
      this.selectedReviewerName = auto?.displayName || 'CRPC Reviewer';
    } else {
      this.selectedReviewerId = '';
      this.selectedReviewerName = '';
    }
  }

  onReviewerSelect(reviewerId: string) {
    this.selectedReviewerId = reviewerId;
    const rev = this.reviewers().find(r => r.id === reviewerId);
    this.selectedReviewerName = rev?.displayName || '';
  }

  reviewerOnLeave(): boolean {
    if (!this.selectedReviewerId) return false;
    const rev = this.reviewers().find(r => r.id === this.selectedReviewerId);
    return rev?.isOnLeave || false;
  }

  private appendAllFieldsToFormData(formData: FormData, status: string, assignedTo: string) {
    const loggedInUser = JSON.parse(sessionStorage.getItem('crpc_user') || '{}');
    const username = loggedInUser?.id || this.auth.currentUser()?.username || '';

    // Basic complainant
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
    if (this.amountInvolved) formData.append('amountInvolved', String(this.amountInvolved));
    if (this.transactionDate) formData.append('transactionDate', this.transactionDate);
    if (this.letterDate) formData.append('letterDate', this.letterDate);
    formData.append('modeOfReceipt', this.modeOfReceipt || 'PHYSICAL_LETTER');
    formData.append('status', status);
    formData.append('assignedTo', assignedTo);
    formData.append('processedBy', username);
    formData.append('receivedAt', (this.receivedDate || new Date().toISOString().split('T')[0]) + 'T00:00:00');

    // Eligibility
    formData.append('proposedComplaintType', this.proposedComplaintType);
    formData.append('notComplaintReason', this.proposedComplaintType === 'NOT_A_COMPLAINT' ? this.notComplaintReason : '');
    const eqMap: Record<string, string> = {};
    this.eligibilityQuestions.forEach(q => { if (q.answer) eqMap[q.key] = q.answer; });
    formData.append('eligibilityQuestions', JSON.stringify(eqMap));

    // Entity details
    formData.append('entityCategory', this.entityCategory);
    formData.append('entityTypeDetail', this.entityTypeDetail);
    formData.append('entityBsrCode', this.entityBsrCode);
    formData.append('entityPincode', this.entityPincode);
    formData.append('entityCountry', this.entityCountry);
    formData.append('entityState', this.entityState);
    formData.append('entityDistrict', this.entityDistrict);
    formData.append('entityCity', this.entityCity);
    formData.append('entityBranchName', this.entityBranchName);
    formData.append('entityBranchCategory', this.entityBranchCategory);
    formData.append('entityAddress', this.entityAddress);
    formData.append('entityBranchCenterName', this.entityBranchCenterName);
    formData.append('cosmosCode', this.cosmosCode);
    formData.append('assetSize', this.assetSize != null ? String(this.assetSize) : '');
    formData.append('isDepositTaking', this.isDepositTaking === 'YES' ? 'true' : this.isDepositTaking === 'NO' ? 'false' : '');
    formData.append('isAssetAbove100Cr', this.isAssetAbove100Cr === 'YES' ? 'true' : this.isAssetAbove100Cr === 'NO' ? 'false' : '');
    formData.append('isLiquidated', this.isLiquidated === 'YES' ? 'true' : this.isLiquidated === 'NO' ? 'false' : '');

    // Complainant extended - Basic Identification
    formData.append('otherEntityName', this.otherEntityName);
    formData.append('dateOfRegistrationWithRBI', this.dateOfRegistrationWithRBI);

    // Complaint Classification
    formData.append('complaintCategory', this.complaintCategory);
    formData.append('complaintSubCategory1', this.complaintSubCategory1);
    formData.append('complaintSubCategory2', this.complaintSubCategory2);
    formData.append('dateOfFilingComplaint', this.dateOfFilingComplaint);
    formData.append('complaintRegDateValid', this.complaintRegDateValid);

    // Reminder & Financial
    formData.append('reminderSentByComplainant', this.reminderSentByComplainant);
    formData.append('disputedAmountInvolved', this.disputedAmountInvolved != null ? String(this.disputedAmountInvolved) : '');
    formData.append('dateOfFilingForFinancial', this.dateOfFilingForFinancial);
    formData.append('compensationSought', this.compensationSought);
    formData.append('loanDisposalAmount', this.loanDisposalAmount != null ? String(this.loanDisposalAmount) : '');

    // Additional Information
    formData.append('additionalComments', this.additionalComments);
    formData.append('crpcProposedAction', this.crpcProposedAction);
    formData.append('vernacularLanguageDetail', this.vernacularLanguageDetail);

    // Legal & Case
    formData.append('legalCaseFiled', this.legalCaseFiled);
    formData.append('legalDateOfFiling', this.legalDateOfFiling);
    formData.append('preEnquiryReceived', this.preEnquiryReceived);

    // Flags
    formData.append('highPriorityComplaint', this.highPriorityComplaint);
    formData.append('isRegardingPension', this.isRegardingPension);
    formData.append('isAgainstBusinessCorrespondent', this.isAgainstBusinessCorrespondent);
    formData.append('isAtmCreditDebitCard', this.isAtmCreditDebitCard);
    formData.append('schemeFlag', this.schemeFlag);
    formData.append('isFreeMarkedComplaint', this.isFreeMarkedComplaint);

    // Linkage
    formData.append('currentComplaintNumber', this.currentComplaintNumber);
    formData.append('receivedReplyWithin30Days', this.receivedReplyWithin30Days);

    // Declaration
    formData.append('declarationAccepted', this.declarationAccepted ? 'true' : 'false');

    // Attachment
    if (this.scannedFile) {
      formData.append('attachment', this.scannedFile);
    }
  }

  confirmAssignment() {
    if (!this.selectedReviewerId.trim()) return;
    this.submitting.set(true);

    const formData = new FormData();
    this.appendAllFieldsToFormData(formData, 'SENT_TO_REVIEWER', this.selectedReviewerId);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/physical-letter`, formData)
      .subscribe({
        next: (res) => {
          const newDraftId = res?.data?.draftId || res?.data?.id || '';
          this.draftId.set(newDraftId);
          this.storeEligibilityData(newDraftId);
          this.submitting.set(false);
          this.showAssignmentDialog.set(false);
          this.submitted.set(true);
        },
        error: () => {
          const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
          const rand = Math.floor(100000 + Math.random() * 900000);
          this.draftId.set(`DRF-${dateStr}-${rand}`);
          this.storeEligibilityData(`DRF-${dateStr}-${rand}`);
          this.submitting.set(false);
          this.showAssignmentDialog.set(false);
          this.submitted.set(true);
        }
      });
  }

  goBack() {
    this.router.navigate(['/crpc/home']);
  }

  openDraft() {
    this.router.navigate(['/crpc/draft', this.draftId()]);
  }
}
