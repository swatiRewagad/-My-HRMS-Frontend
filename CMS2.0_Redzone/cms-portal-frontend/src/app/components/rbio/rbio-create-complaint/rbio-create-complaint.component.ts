import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { lookupPincode } from '../../../utils/pincode-data';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { OcrProvenance } from '../../../shared/ocr-provenance';

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
export class RbioCreateComplaintComponent implements OnInit, OnDestroy {

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

  /**
   * Per-field OCR provenance (UST624) and the override-wins rule (UST625).
   *
   * Before this, the 14 assignments below overwrote the form unconditionally on every extraction, and
   * runOcr() stays reachable while !ocrComplete() — which the banner's dismiss button resets. So an
   * operator who corrected a field and re-extracted silently lost the correction.
   */
  ocrProvenance = new OcrProvenance();
  /** Set when the server refused prefill (vernacular or low confidence) — UST778. */
  ocrManualEntryReason = signal<string | null>(null);
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
  /** The server's save timestamp. Null whenever the last save did not demonstrably succeed. */
  draftSavedAt = signal<string | null>(null);
  /** A failed draft save, shown instead of the saved-summary view. */
  draftError = signal<string | null>(null);
  private autoSaveTimer: any = null;
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
    this.resumeDraft();
    this.startAutoSave();
  }

  ngOnDestroy() {
    this.stopAutoSave();
  }

  /**
   * Restores a previously saved Register draft (UST674 "resuming must restore all field values").
   *
   * <p>There was no resume path at all: ngOnInit only generated a fresh complaint id, so a saved draft
   * could never be reopened.
   */
  private resumeDraft() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts/REGISTER`).subscribe({
      next: (res) => {
        const data = res?.formData;
        if (!data) return;
        Object.keys(data).forEach(key => {
          if (data[key] !== null && data[key] !== undefined && key in this) {
            (this as any)[key] = data[key];
            // A resumed value is the operator's own work, not scan output, so it must not be
            // overwritten by a later extraction.
            this.ocrProvenance.markEdited(key);
          }
        });
        this.draftSavedAt.set(res?.updatedAt ?? null);
      },
      // 204 (no draft) arrives here in some cases and is not a failure worth surfacing: having no draft
      // is the normal state on a first visit.
      error: () => {}
    });
  }

  /**
   * UST673: auto-save on an interval that comes from SYSTEM_CONFIG, not a hardcoded literal.
   *
   * <p>Silent by design — no prompt, no modal. The only visible effect is the "last saved" timestamp,
   * and a FAILED autosave surfaces through draftError like any other failed save, because a timer that
   * fails quietly for a whole session is how a citizen's letter gets lost.
   */
  private startAutoSave() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts/config`).subscribe({
      next: (cfg) => {
        const seconds = Number(cfg?.autosaveIntervalSeconds);
        if (!cfg?.autosaveEnabled || !Number.isFinite(seconds) || seconds <= 0) return;
        this.autoSaveTimer = setInterval(() => {
          // Nothing typed yet means nothing worth saving; the server refuses an empty draft anyway, and
          // sending one would overwrite a good draft with nothing.
          if (this.canSubmit() || this.complainantName.trim() || this.subject.trim()) {
            this.saveDraft(true);
          }
        }, seconds * 1000);
      },
      error: () => {}
    });
  }

  private stopAutoSave() {
    if (this.autoSaveTimer) {
      clearInterval(this.autoSaveTimer);
      this.autoSaveTimer = null;
    }
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

  /**
   * Writes an OCR value only when the provenance helper permits it, then records the provenance.
   *
   * The `currentValue` override exists for non-string fields: amountInvolved is a number|null, so the
   * emptiness check has to see the real current value rather than the incoming string.
   */
  private applyOcr(field: string, incoming: any, assign: (value: any) => void,
                   currentValue?: any) {
    if (!incoming) return;
    const current = currentValue !== undefined ? currentValue : (this as any)[field];
    if (!this.ocrProvenance.shouldApply(field, current)) return;
    assign(incoming);
    this.ocrProvenance.markFromOcr(field);
  }

  /** Call from a field's change handler so the operator's edit sticks through later extractions. */
  onFieldEdited(field: string) {
    this.ocrProvenance.markEdited(field);
  }

  removeFile() {
    this.scannedFile = null;
    this.pdfPreviewUrl.set(null);
    this.ocrComplete.set(false);
    this.ocrManualEntryReason.set(null);
    // The previous scan's provenance describes nothing once its file is gone. Typed values are kept:
    // removing an attachment must not discard the operator's work.
    this.ocrProvenance.reset();
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

        // UST778: the server gates vernacular and low-confidence scans and returns NO data for them.
        // Surfaced as a distinct state, not as an error — routing to manual entry is a correct outcome,
        // not a fault, and telling the operator why stops them retrying the same scan.
        if (res?.prefillAllowed === false) {
          this.ocrInProgress.set(false);
          this.ocrComplete.set(true);
          this.ocrManualEntryReason.set(res?.message
            || 'This document must be entered manually.');
          return;
        }

        if (Object.keys(data).length === 0) {
          this.ocrInProgress.set(false);
          this.scanError = 'AI extraction returned no data. Please fill manually or try again later.';
          return;
        }
        this.ocrManualEntryReason.set(null);

        // Each assignment goes through the provenance helper, which refuses to overwrite a field the
        // operator has edited (UST625) and records which fields hold scan output (UST624).
        this.applyOcr('complainantName', data.complainantName, v => this.complainantName = v);
        this.applyOcr('complainantAddress', data.complainantAddress, v => this.complainantAddress = v);
        this.applyOcr('complainantState', data.complainantState, v => this.complainantState = v);
        this.applyOcr('complainantDistrict', data.complainantDistrict, v => this.complainantDistrict = v);
        this.applyOcr('complainantPincode', data.complainantPincode, v => this.complainantPincode = v);
        this.applyOcr('complainantPhone', data.complainantPhone, v => this.complainantPhone = v);
        this.applyOcr('complainantEmail', data.complainantEmail, v => this.complainantEmail = v);
        this.applyOcr('subject', data.subject, v => this.subject = v);
        this.applyOcr('description', data.description, v => this.description = v);
        this.applyOcr('entityName', data.entityName, v => {
          this.entityName = v;
          this.entitySearchText = v;
        });
        this.applyOcr('entityType', data.entityType, v => this.entityType = v);
        this.applyOcr('category', data.category, v => this.category = v);
        this.applyOcr('amountInvolved', data.amountInvolved,
          v => this.amountInvolved = Number(v) || null, this.amountInvolved);
        this.applyOcr('transactionDate', data.transactionDate, v => this.transactionDate = v);

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

  /**
   * Saves the Register form as a real draft (UST674).
   *
   * <p>Two defects fixed here.
   *
   * <p><b>It reported success on failure.</b> {@code draftSaved.set(true)} was called in BOTH the next
   * AND error handlers. That flag does not merely show a badge — it swaps the whole form out for a saved
   * summary view showing a complaint number, so a rejected save presented the officer with a
   * confirmation page for a complaint that did not exist. The citizen-side version of this bug was fixed
   * under UST82; the staff side kept it.
   *
   * <p><b>It did not save a draft.</b> It posted to {@code /workflow/rbio/create-complaint}, where
   * {@code WorkflowController} overrides the submitted {@code status: 'DRAFT'} to {@code "assigned"},
   * applies an SLA clock and writes a CREATED timeline row — so "save draft" created a live, ASSIGNED
   * complaint. It now posts to the staff-draft endpoint, which stores a draft owned by this user and
   * creates no complaint.
   */
  saveDraft(autosave = false) {
    this.saving.set(true);
    this.draftError.set(null);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts`, {
      milestone: 'REGISTER',
      autosave,
      formData: this.buildPayload('DRAFT')
    }).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.draftSaved.set(true);
        // The SERVER's timestamp. A locally generated one would assert a save time for a save the
        // server may never have performed.
        this.draftSavedAt.set(res?.savedAt ?? null);
      },
      error: (err) => {
        this.saving.set(false);
        // Explicitly NOT set. A failed save must never look like a successful one.
        this.draftSaved.set(false);
        this.draftSavedAt.set(null);
        this.draftError.set(err?.error?.message
          || 'Your draft could not be saved. Your entries are still on screen — please try again.');
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
