import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-file-appeal',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe],
  templateUrl: './file-appeal.component.html',
  styleUrl: './file-appeal.component.scss'
})
export class FileAppealComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);

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

  // File limits
  private readonly MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB per file
  private readonly MAX_TOTAL_SIZE = 10 * 1024 * 1024; // 10MB cumulative
  private readonly MAX_FILE_COUNT = 5;

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
    this.error = '';
    // Use the delayedFiling flag from the API response directly
    const delayed = result.delayedFiling === true;
    this.isDelayedFiling.set(delayed);
    this.phase.set('form');
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      if (file.size > this.MAX_FILE_SIZE) {
        this.error = 'File "' + file.name + '" exceeds 2MB limit.';
        input.value = '';
        return;
      }
      if (this.appealAttachments.length >= this.MAX_FILE_COUNT) {
        this.error = 'Maximum ' + this.MAX_FILE_COUNT + ' files allowed.';
        input.value = '';
        return;
      }
      const currentTotal = this.appealAttachments.reduce((sum, f) => sum + f.size, 0);
      if (currentTotal + file.size > this.MAX_TOTAL_SIZE) {
        this.error = 'Total file size exceeds 10MB limit.';
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
      if (file.size > this.MAX_FILE_SIZE) {
        this.error = 'File "' + file.name + '" exceeds 2MB limit.';
        return;
      }
      if (this.appealAttachments.length >= this.MAX_FILE_COUNT) {
        this.error = 'Maximum ' + this.MAX_FILE_COUNT + ' files allowed.';
        return;
      }
      const currentTotal = this.appealAttachments.reduce((sum, f) => sum + f.size, 0);
      if (currentTotal + file.size > this.MAX_TOTAL_SIZE) {
        this.error = 'Total file size exceeds 10MB limit.';
        return;
      }
      this.appealAttachments.push(file);
    }
    this.error = '';
  }

  removeFile(index: number) {
    this.appealAttachments.splice(index, 1);
  }

  // FR-G-034: Submit appeal
  submitAppeal() {
    this.error = '';
    if (this.isDelayedFiling() && !this.reasonForDelay.trim()) {
      this.error = 'Reason for delay is required.';
      return;
    }
    if (this.isDelayedFiling() && this.reasonForDelay.length > 500) {
      this.error = 'Reason must be within 500 characters.';
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
