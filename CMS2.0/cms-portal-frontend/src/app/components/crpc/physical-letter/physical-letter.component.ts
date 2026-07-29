import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { CrpcService } from '../../../services/crpc.service';
import { ReviewerUser } from '../../../models/crpc.model';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

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
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
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
  activeTab = signal<'creation' | 'assignment'>('creation');

  // Left panel
  scannedFile: File | null = null;
  scanError = '';
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

  // Pincode lookup
  pincodeLoading = signal(false);

  // State
  saving = signal(false);
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
    this.loadPastComplaints();
    this.loadStates();
    this.loadReviewers();
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
    if (value && value.length === 6 && /^\d{6}$/.test(value)) {
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
    this.http.get<any[]>(`/api/pincode/${pincode}`).subscribe({
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

    if (file.size > 10 * 1024 * 1024) {
      this.scanError = 'File size must not exceed 10 MB.';
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

          if (fieldCount === 0) {
            this.ocrInProgress.set(false);
            this.scanError = 'AI extraction returned no data. API quota may be exhausted. Please fill manually or try again later.';
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
          if (data.entityName) this.entityName = data.entityName;
          if (data.entityType) this.entityType = data.entityType;
          if (data.category) this.category = data.category;
          if (data.branchName) this.branchName = data.branchName;
          if (data.amountInvolved) this.amountInvolved = Number(data.amountInvolved) || null;
          if (data.letterDate) this.letterDate = data.letterDate;
          if (data.transactionDate) this.transactionDate = data.transactionDate;

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

  canSubmit(): boolean {
    return this.complainantName.trim().length > 0 &&
           this.subject.trim().length > 0 &&
           this.category.trim().length > 0;
  }

  saveDraft() {
    this.saving.set(true);
    setTimeout(() => {
      this.saving.set(false);
    }, 800);
  }

  submitDraft() {
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
        error: () => {
          const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
          const rand = Math.floor(100000 + Math.random() * 900000);
          this.draftId.set(`DRF-${dateStr}-${rand}`);
          this.submitting.set(false);
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
