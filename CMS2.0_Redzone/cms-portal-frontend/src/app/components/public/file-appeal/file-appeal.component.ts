import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { UploadLimitsService } from '../../../services/upload-limits.service';

@Component({
  selector: 'app-file-appeal',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe, StatusBadgeComponent],
  templateUrl: './file-appeal.component.html',
  styleUrl: './file-appeal.component.scss'
})
export class FileAppealComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  // Public: the templates render the configured limit in their upload hints.
  uploadLimits = inject(UploadLimitsService);

  // FR-G-032: Appeal phases
  phase = signal<'search' | 'eligibility' | 'form' | 'success'>('search');
  submitting = signal(false);
  checking = signal(false);
  error = '';

  // Search
  complaintId = '';
  complaintFound = false;

  // Eligibility check result
  eligibilityResult = signal<any>(null);
  classification = signal<'APPEAL' | 'REPRESENTATION' | null>(null);

  // FR-G-042: Reason for delay (31-60 days window)
  reasonForDelay = '';
  isDelayedFiling = signal(false);

  // FR-G-033: Appeal details
  appealGround = '';
  appealGrounds = [
    'The complaint was not resolved within 30 days',
    'Dissatisfied with the resolution/award',
    'The complaint was rejected without valid reason',
    'Partial relief was granted',
    'Non-implementation of the award by Regulated Entity',
    'Other',
  ];
  appealDetails = '';
  reliefSought = '';
  appealAttachments: File[] = [];
  isDragOver = false;
  declarationChecked = false;

  // Appellant identity. None of these fields existed: the appeal was filed with appellantName
  // hardcoded to the literal "Citizen" in AppealController, so the record carried no way to contact the
  // person who filed it.
  appellantName = '';
  appellantMobile = '';
  appellantEmail = '';
  appellantComments = '';
  dateOfReceipt = '';
  dateOfReceiptDisplay = '';

  // Per-field errors, keyed by control name. The single `error` string above can only ever show one
  // message, so a form with three empty required fields reported one of them.
  fieldErrors: Record<string, string> = {};

  static readonly NAME_MAX = 100;
  static readonly COMMENTS_MAX = 500;
  static readonly REASON_MAX = 500;

  // The exact strings the acceptance criteria name. Held as constants so the same wording is used by
  // every field rather than retyped per call site.
  private static readonly MSG_REQUIRED = 'This field is required.';
  private static readonly MSG_LENGTH = 'Input must be within allowed character length.';
  private static readonly MSG_FILE = 'Invalid file type or size, please upload a valid document.';

  // Deliberately NOT a size check. The accepted TYPES are a product rule (JPG/PDF/DOCX); the accepted
  // SIZE is server configuration, read from UploadLimitsService. Hardcoding 2MB here is what made the
  // browser refuse appeals the API would have accepted.
  private static readonly ALLOWED_EXTENSIONS = ['jpg', 'jpeg', 'pdf', 'docx'];

  // File limits come from the server (cms.upload.*), not from constants in the bundle. These were
  // 2 MB per file and 10 MB total, both BELOW what the server accepts (5 MB / 25 MB), so the browser
  // refused appeals the API would have taken. A compiled-in constant cannot track a configured limit.
  private maxFileSize(): number { return this.uploadLimits.maxFileSizeBytes(); }
  private maxTotalSize(): number { return this.uploadLimits.maxTotalSizeBytes(); }
  private maxFileCount(): number { return this.uploadLimits.maxFileCount(); }

  // Success
  appealRefNumber = '';

  ngOnInit() {
    // Read query param 'complaint' to pre-fill and auto-trigger search
    const complaintParam = this.route.snapshot.queryParamMap.get('complaint');
    if (complaintParam) {
      this.complaintId = complaintParam;
      this.searchComplaint();
    }
  }

  searchComplaint() {
    if (!this.complaintId.trim()) {
      this.error = 'Please enter your complaint reference number.';
      return;
    }
    this.error = '';
    this.checking.set(true);

    this.http.get<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/check-eligibility?complaintNumber=${encodeURIComponent(this.complaintId.trim())}`
    ).subscribe({
      next: (res) => {
        this.checking.set(false);
        const data = res?.data;
        if (data) {
          this.eligibilityResult.set(data);
          this.classification.set(data.suggestedType || data.classification || null);
          this.complaintFound = true;
          this.phase.set('eligibility');
        } else {
          this.error = 'Complaint not found. Please check the reference number.';
        }
      },
      error: (err) => {
        this.checking.set(false);
        this.error = err.error?.message || 'Unable to verify complaint. Please try again.';
      }
    });
  }

  proceedToForm() {
    const result = this.eligibilityResult();
    if (!result?.eligible) {
      this.error = 'This complaint is not eligible for appeal.';
      return;
    }
    // Clause gate. `appealable` is server-derived from CLOSURE_CLAUSE_MASTER: only 15(1)(a) and
    // 15(1)(b) are appealable by a complainant. Checked separately from `eligible` because a
    // non-appealable closure may still be escalated as a REPRESENTATION — it is the appeal route
    // specifically that closes here.
    if (result.appealable === false) {
      this.error = result.appealableReason || 'This complaint is not eligible for appeal.';
      return;
    }
    this.error = '';
    // Use the delayedFiling flag from the API response directly
    const delayed = result.delayedFiling === true;
    this.isDelayedFiling.set(delayed);
    this.phase.set('form');
  }

  /**
   * True when the file's extension is one the appeal form accepts.
   *
   * Extension, not MIME type: a .docx uploaded from some browsers arrives with an empty or generic
   * type, so rejecting on MIME would refuse a valid document.
   */
  private hasAllowedType(file: File): boolean {
    const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
    return FileAppealComponent.ALLOWED_EXTENSIONS.includes(ext);
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      if (!this.hasAllowedType(file)) {
        this.error = FileAppealComponent.MSG_FILE;
        input.value = '';
        return;
      }
      if (file.size > this.maxFileSize()) {
        // Both halves of the criteria's single message: the wording is fixed, and the configured limit
        // is appended so a citizen learns what the limit actually is instead of guessing.
        this.error = `${FileAppealComponent.MSG_FILE} `
          + `File "${file.name}" exceeds the ${this.uploadLimits.maxFileSizeMb()}MB limit.`;
        input.value = '';
        return;
      }
      if (this.appealAttachments.length >= this.maxFileCount()) {
        this.error = `Maximum ${this.maxFileCount()} files allowed.`;
        input.value = '';
        return;
      }
      const currentTotal = this.appealAttachments.reduce((sum, f) => sum + f.size, 0);
      if (currentTotal + file.size > this.maxTotalSize()) {
        this.error = `Total file size exceeds ${this.uploadLimits.maxTotalSizeMb()}MB limit.`;
        input.value = '';
        return;
      }
      this.appealAttachments.push(file);
    }
    this.error = '';
    input.value = '';
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    if (!event.dataTransfer?.files?.length) return;
    for (let i = 0; i < event.dataTransfer.files.length; i++) {
      const file = event.dataTransfer.files[i];
      if (!this.hasAllowedType(file)) {
        this.error = FileAppealComponent.MSG_FILE;
        return;
      }
      if (file.size > this.maxFileSize()) {
        this.error = `${FileAppealComponent.MSG_FILE} `
          + `File "${file.name}" exceeds the ${this.uploadLimits.maxFileSizeMb()}MB limit.`;
        return;
      }
      if (this.appealAttachments.length >= this.maxFileCount()) {
        this.error = `Maximum ${this.maxFileCount()} files allowed.`;
        return;
      }
      const currentTotal = this.appealAttachments.reduce((sum, f) => sum + f.size, 0);
      if (currentTotal + file.size > this.maxTotalSize()) {
        this.error = `Total file size exceeds ${this.uploadLimits.maxTotalSizeMb()}MB limit.`;
        return;
      }
      this.appealAttachments.push(file);
    }
    this.error = '';
  }

  removeFile(index: number) {
    this.appealAttachments.splice(index, 1);
  }

  /**
   * Opens the native picker for the Date of Receipt.
   *
   * The visible input is readonly, so the picker is the ONLY way to set the value — the acceptance
   * criterion is that the date cannot be typed freehand.
   */
  openDatePicker(event: Event) {
    const btn = event.currentTarget as HTMLElement;
    const hidden = btn.parentElement?.querySelector('.date-hidden-picker') as HTMLInputElement | null;
    hidden?.showPicker();
  }

  onDateOfReceiptChange(event: Event) {
    const iso = (event.target as HTMLInputElement).value;
    if (!iso) return;
    this.dateOfReceipt = iso;
    const [y, m, d] = iso.split('-');
    this.dateOfReceiptDisplay = `${d}/${m}/${y}`;
    delete this.fieldErrors['dateOfReceipt'];
  }

  get todayISO(): string {
    return new Date().toISOString().split('T')[0];
  }

  /**
   * Validates the appellant block and returns true when it is clean.
   *
   * Every field is checked before returning, rather than short-circuiting, so a citizen sees all of
   * their mistakes at once instead of one per submit.
   */
  private validateAppellant(): boolean {
    const C = FileAppealComponent;
    this.fieldErrors = {};

    if (!this.appellantName.trim()) {
      this.fieldErrors['appellantName'] = C.MSG_REQUIRED;
    } else if (this.appellantName.length > C.NAME_MAX) {
      this.fieldErrors['appellantName'] = C.MSG_LENGTH;
    }

    if (!this.appellantMobile.trim()) {
      this.fieldErrors['appellantMobile'] = C.MSG_REQUIRED;
    } else if (!/^\d{10}$/.test(this.appellantMobile.trim())) {
      // A 9- or 11-digit mobile is a length problem, which is what the criteria call it.
      this.fieldErrors['appellantMobile'] = C.MSG_LENGTH;
    }

    // Email is optional, but must be well formed when supplied.
    const email = this.appellantEmail.trim();
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
      this.fieldErrors['appellantEmail'] = C.MSG_LENGTH;
    }

    if (this.appellantComments.length > C.COMMENTS_MAX) {
      this.fieldErrors['appellantComments'] = C.MSG_LENGTH;
    }

    if (!this.dateOfReceipt) {
      this.fieldErrors['dateOfReceipt'] = C.MSG_REQUIRED;
    }

    return Object.keys(this.fieldErrors).length === 0;
  }

  // FR-G-034: Submit appeal
  submitAppeal() {
    this.error = '';
    // Delay validation runs FIRST and keeps its own two exact strings. They are separately specified
    // and must not be folded into the generic "This field is required."
    if (this.isDelayedFiling() && !this.reasonForDelay.trim()) {
      this.error = 'Reason for delay is required.';
      return;
    }
    if (this.isDelayedFiling() && this.reasonForDelay.length > FileAppealComponent.REASON_MAX) {
      this.error = 'Reason must be within 500 characters.';
      return;
    }
    if (!this.validateAppellant()) {
      // The banner echoes the FIRST field error rather than a fixed sentence, so it cannot say
      // "required" about a field that is actually too long.
      this.error = Object.values(this.fieldErrors)[0];
      return;
    }
    if (!this.appealGround) {
      this.error = 'Please select a ground for appeal.';
      return;
    }
    if (!this.appealDetails.trim()) {
      this.error = 'Please provide details supporting your appeal.';
      return;
    }
    if (!this.declarationChecked) {
      this.error = 'Please accept the declaration before submitting.';
      return;
    }

    this.submitting.set(true);

    const formData = new FormData();
    formData.append('complaintNumber', this.complaintId);
    formData.append('ground', this.appealGround);
    formData.append('details', this.appealDetails);
    formData.append('reliefSought', this.reliefSought);
    formData.append('classification', this.classification() || '');
    formData.append('appellantName', this.appellantName.trim());
    formData.append('appellantPhone', this.appellantMobile.trim());
    formData.append('appellantEmail', this.appellantEmail.trim());
    formData.append('comments', this.appellantComments.trim());
    formData.append('dateOfReceipt', this.dateOfReceipt);
    if (this.isDelayedFiling() && this.reasonForDelay) {
      formData.append('reasonForDelay', this.reasonForDelay);
    }

    for (const file of this.appealAttachments) {
      formData.append('attachments', file);
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/file`,
      formData
    ).subscribe({
      next: (res) => {
        const appealNumber = res?.data?.appealNumber;
        if (!appealNumber) {
          this.submitting.set(false);
          this.error = 'Appeal was submitted but no appeal number was returned. Please contact support.';
          return;
        }
        this.appealRefNumber = appealNumber;
        this.submitting.set(false);
        this.phase.set('success');
      },
      error: (err) => {
        this.submitting.set(false);
        this.error = err.error?.message || 'Failed to submit appeal. Please try again.';
      }
    });
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  trackAppeal() {
    this.router.navigate(['/public/track', this.appealRefNumber]);
  }
}
