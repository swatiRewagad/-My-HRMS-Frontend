import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
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

  // State
  saving = signal(false);
  submitting = signal(false);
  submitted = signal(false);
  draftId = signal('');

  // Reference data
  categories = [
    'ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'
  ];

  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  protected Math = Math;

  ngOnInit() {
    this.receivedDate = new Date().toISOString().split('T')[0];
    this.loadPastComplaints();
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

    setTimeout(() => {
      const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const rand = Math.floor(100000 + Math.random() * 900000);
      const newDraftId = `DRF-${dateStr}-${rand}`;
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
        receivedDate: this.receivedDate,
        modeOfReceipt: this.modeOfReceipt,
        fileName: this.scannedFile?.name || 'scanned_letter.pdf',
        fileSize: this.scannedFile ? (this.scannedFile.size / 1024 / 1024).toFixed(2) + ' MB' : '2.4 MB',
      }));
    }, 1500);
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

  goBack() {
    this.router.navigate(['/crpc/home']);
  }

  openDraft() {
    this.router.navigate(['/crpc/draft', this.draftId()]);
  }
}
