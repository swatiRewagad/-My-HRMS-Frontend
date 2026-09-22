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
import { OcrProvenance } from '../../../shared/ocr-provenance';
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

  // Form fields
  subject = '';
  description = '';
  comments = '';
  modeOfReceipt = 'PHYSICAL_LETTER';
  receivedDate = '';
  letterDate = '';
  category = '';
  complaintType = 'COMPLAINT';
  isRbiEComplaint = 'NO';
  nonEComplaintReason = '';
  entityName = '';
  entityType = 'BANK';
  entitySearchText = '';
  entitySearchResults = signal<{ id: number; name: string; department: string; entityType: string }[]>([]);
  entitySearchLoading = signal(false);
  showEntityDropdown = signal(false);
  private entitySearchTimeout: any = null;
  branchName = '';
  branchPincode = '';
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantState = '';
  complainantAddress = '';
  complainantDistrict = '';
  complainantPincode = '';
  amountInvolved: number | null = null;
  transactionDate = '';

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

  // Section collapse state
  collapsedSections: Record<string, boolean> = {};

  // Pincode lookup
  pincodeLoading = signal(false);

  // State
  saving = signal(false);
  /** Per-field OCR provenance (UST624) and the override-wins rule (UST625). */
  ocrProvenance = new OcrProvenance();
  /** Set when the server refused prefill (vernacular or low-confidence scan) — UST778. */
  ocrManualEntryReason = signal<string | null>(null);
  /** A failed draft save. Never shown together with a success indicator. */
  draftError = signal<string | null>(null);
  /** The server's save timestamp, or null when the last save did not demonstrably succeed. */
  draftSavedAt = signal<string | null>(null);
  /** A failed submission. Replaces the fabricated draft id that used to stand in for one. */
  submitError = signal<string | null>(null);
  submitting = signal(false);
  submitted = signal(false);
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

  toggleSection(section: string) {
    this.collapsedSections[section] = !this.collapsedSections[section];
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

    if (file.type === 'application/pdf') {
      const url = URL.createObjectURL(file);
      this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    }
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
    if (file.type === 'application/pdf') {
      const url = URL.createObjectURL(file);
      this.pdfPreviewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    }
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

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/ocr/extract`, formData)
      .subscribe({
        next: (res) => {
          const data = res?.data || {};
          const fieldCount = Object.keys(data).length;

          // UST778: the server gates vernacular and low-confidence scans and returns no data for them.
          // A correct outcome, not a fault — so it is not reported as an error.
          if (res?.prefillAllowed === false) {
            this.ocrInProgress.set(false);
            this.ocrComplete.set(true);
            this.ocrManualEntryReason.set(res?.message || 'This document must be entered manually.');
            return;
          }
          this.ocrManualEntryReason.set(null);

          if (fieldCount === 0) {
            this.ocrInProgress.set(false);
            this.scanError = 'AI extraction returned no data. API quota may be exhausted. Please fill manually or try again later.';
            return;
          }

          // Every assignment goes through the provenance helper: it refuses to overwrite a field the
          // operator has edited (UST625) and records which fields hold scan output (UST624). These 16
          // assignments previously clobbered the form on every extraction.
          this.applyOcr('complainantName', data.complainantName, v => this.complainantName = v);
          this.applyOcr('complainantAddress', data.complainantAddress, v => this.complainantAddress = v);
          this.applyOcr('complainantState', data.complainantState, v => this.complainantState = v);
          this.applyOcr('complainantDistrict', data.complainantDistrict, v => this.complainantDistrict = v);
          this.applyOcr('complainantPincode', data.complainantPincode, v => this.complainantPincode = v);
          this.applyOcr('complainantPhone', data.complainantPhone, v => this.complainantPhone = v);
          this.applyOcr('complainantEmail', data.complainantEmail, v => this.complainantEmail = v);
          this.applyOcr('subject', data.subject, v => this.subject = v);
          this.applyOcr('description', data.description, v => this.description = v);
          this.applyOcr('entityName', data.entityName, v => this.entityName = v);
          this.applyOcr('entityType', data.entityType, v => this.entityType = v);
          this.applyOcr('category', data.category, v => this.category = v);
          this.applyOcr('branchName', data.branchName, v => this.branchName = v);
          this.applyOcr('amountInvolved', data.amountInvolved,
            v => this.amountInvolved = Number(v) || null, this.amountInvolved);
          this.applyOcr('letterDate', data.letterDate, v => this.letterDate = v);
          this.applyOcr('transactionDate', data.transactionDate, v => this.transactionDate = v);

          // Build suggestions from extracted data
          const suggs: Suggestion[] = [];
          if (data.entityName) suggs.push({ id: '1', field: 'Entity', value: data.entityName });
          if (data.category) suggs.push({ id: '2', field: 'Category', value: data.category });
          if (data.amountInvolved) suggs.push({ id: '3', field: 'Amount', value: `₹${data.amountInvolved}` });
          if (data.subject) suggs.push({ id: '4', field: 'Subject', value: data.subject });
          this.suggestions.set(suggs);

          this.ocrInProgress.set(false);
          this.ocrComplete.set(true);
        },
        error: (err) => {
          console.error('OCR extraction failed:', err);
          this.ocrInProgress.set(false);
          this.scanError = 'AI extraction failed: ' + (err.error?.message || 'Service unavailable. Please fill manually.');
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

    return Object.keys(this.fieldErrors).length === 0;
  }

  canSubmit(): boolean {
    return this.complainantName.trim().length > 0 &&
           this.subject.trim().length > 0 &&
           this.category.trim().length > 0;
  }

  /**
   * Writes an OCR value only when the provenance helper permits it, then records the provenance.
   *
   * The `currentValue` override is for non-string fields: amountInvolved is number|null, so the
   * emptiness check needs the real current value rather than the incoming string.
   */
  private applyOcr(field: string, incoming: any, assign: (value: any) => void, currentValue?: any) {
    if (!incoming) return;
    const current = currentValue !== undefined ? currentValue : (this as any)[field];
    if (!this.ocrProvenance.shouldApply(field, current)) return;
    assign(incoming);
    this.ocrProvenance.markFromOcr(field);
  }

  /** Call from a field's change handler so the operator's edit survives later extractions. */
  onFieldEdited(field: string) {
    this.ocrProvenance.markEdited(field);
  }

  /**
   * Saves the letter as a real draft (UST674).
   *
   * <p>This was an 800ms {@code setTimeout} and nothing else — no endpoint, no payload, no storage. The
   * button showed a spinner and discarded the operator's work. It now persists an owner-scoped draft and
   * reports a failure as a failure.
   */
  saveDraft(autosave = false) {
    this.saving.set(true);
    this.draftError.set(null);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts`, {
      milestone: 'REGISTER',
      autosave,
      formData: {
        complainantName: this.complainantName,
        complainantPhone: this.complainantPhone,
        complainantEmail: this.complainantEmail,
        complainantAddress: this.complainantAddress,
        complainantState: this.complainantState,
        complainantDistrict: this.complainantDistrict,
        complainantPincode: this.complainantPincode,
        subject: this.subject,
        description: this.description,
        category: this.category,
        entityName: this.entityName,
        letterDate: this.letterDate,
        modeOfReceipt: this.modeOfReceipt
      }
    }).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.draftSavedAt.set(res?.savedAt ?? null);
      },
      error: (err) => {
        this.saving.set(false);
        this.draftSavedAt.set(null);
        this.draftError.set(err?.error?.message
          || 'Your draft could not be saved. Your entries are still on screen — please try again.');
      }
    });
  }

  submitDraft() {
    this.formSubmitAttempted = true;
    if (!this.validateForm()) return;
    this.submitting.set(true);

    const loggedInUser = JSON.parse(sessionStorage.getItem('crpc_user') || '{}');
    const username = loggedInUser?.id || this.auth.currentUser()?.username || '';

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
    if (this.amountInvolved) formData.append('amountInvolved', String(this.amountInvolved));
    if (this.transactionDate) formData.append('transactionDate', this.transactionDate);
    if (this.letterDate) formData.append('letterDate', this.letterDate);
    formData.append('modeOfReceipt', this.modeOfReceipt || 'PHYSICAL_LETTER');
    formData.append('status', 'DRAFT');
    formData.append('assignedTo', username);
    formData.append('processedBy', username);
    formData.append('receivedAt', (this.receivedDate || new Date().toISOString().split('T')[0]) + 'T00:00:00');

    if (this.scannedFile) {
      formData.append('attachment', this.scannedFile);
    }

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
        error: (err) => {
          // The client used to INVENT a draft id here (DRF-<date>-<random>), set submitted = true, and
          // write it to sessionStorage. The DEO walked away with a reference number for a physical letter
          // that was never persisted — and this.scannedFile, the only digital copy of a citizen's
          // posted letter, was discarded. For a physical letter that is unrecoverable data loss.
          //
          // The form and the attached scan are now left exactly as they were so the submission can be
          // retried.
          this.submitting.set(false);
          this.submitted.set(false);
          this.draftId.set('');
          this.submitError.set(err?.error?.message
            || 'The letter could not be submitted. Nothing has been saved — the scan is still attached, '
               + 'please try again.');
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

  confirmAssignment() {
    if (!this.selectedReviewerId.trim()) return;
    this.submitting.set(true);

    const loggedInUser = JSON.parse(sessionStorage.getItem('crpc_user') || '{}');
    const username = loggedInUser?.id || this.auth.currentUser()?.username || '';

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
    if (this.amountInvolved) formData.append('amountInvolved', String(this.amountInvolved));
    if (this.transactionDate) formData.append('transactionDate', this.transactionDate);
    if (this.letterDate) formData.append('letterDate', this.letterDate);
    formData.append('modeOfReceipt', this.modeOfReceipt || 'PHYSICAL_LETTER');
    formData.append('status', 'SENT_TO_REVIEWER');
    formData.append('assignedTo', this.selectedReviewerId);
    formData.append('processedBy', username);
    formData.append('receivedAt', (this.receivedDate || new Date().toISOString().split('T')[0]) + 'T00:00:00');

    if (this.scannedFile) {
      formData.append('attachment', this.scannedFile);
    }

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/physical-letter`, formData)
      .subscribe({
        next: (res) => {
          const newDraftId = res?.data?.draftId || res?.data?.id || '';
          this.draftId.set(newDraftId);
          this.submitting.set(false);
          this.submitted.set(true);
        },
        error: (err) => {
          // Second site of the same invented-reference bug: a random DRF-<date>-<random> id plus
          // submitted = true on failure. this.scannedFile is the only digital copy of a citizen's posted
          // letter, so claiming a submission that did not happen loses it irrecoverably. The form and the
          // attachment are preserved so the operator can retry.
          this.submitting.set(false);
          this.submitted.set(false);
          this.draftId.set('');
          this.submitError.set(err?.error?.message
            || 'The letter could not be submitted. Nothing has been saved — the scan is still attached, '
               + 'please try again.');
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
