import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { UploadLinkService } from '../../../services/upload-link.service';
import { environment } from '../../../../environments/environment';

type Phase = 'loading' | 'otp' | 'upload' | 'success' | 'expired' | 'error';

interface SelectedFile {
  file: File;
  name: string;
  size: number;
  valid: boolean;
  error?: string;
}

@Component({
  selector: 'app-complainant-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="upload-page">
      <header class="upload-header">
        <img src="assets/corporates-rbi-logo.jpg" alt="RBI" class="rbi-logo" onerror="this.style.display='none'" />
        <div class="header-text">
          <span class="header-title">Reserve Bank of India</span>
          <span class="header-subtitle">Complaint Management System — Document Upload</span>
        </div>
      </header>

      <main class="upload-main">
        <!-- Loading -->
        @if (phase() === 'loading') {
          <div class="phase-card">
            <div class="loading-spinner">Verifying link...</div>
          </div>
        }

        <!-- Expired / Error -->
        @if (phase() === 'expired') {
          <div class="phase-card phase-card--error">
            <i class="pi pi-times-circle error-icon"></i>
            <h2>Link Expired</h2>
            <p>This document upload link has expired or is no longer valid. Please contact the assigned officer for a new link.</p>
          </div>
        }

        @if (phase() === 'error') {
          <div class="phase-card phase-card--error">
            <i class="pi pi-exclamation-circle error-icon"></i>
            <h2>Something went wrong</h2>
            <p>{{ errorMessage() }}</p>
          </div>
        }

        <!-- Phase 1: OTP Verification -->
        @if (phase() === 'otp') {
          <div class="phase-card">
            <h2>Verify Your Identity</h2>
            <p class="phase-desc">OTP has been sent to your registered email and mobile number. Please enter both OTPs to proceed.</p>

            <div class="otp-form">
              <div class="otp-field">
                <label for="emailOtp">Email OTP</label>
                <input
                  id="emailOtp"
                  type="text"
                  maxlength="6"
                  placeholder="Enter 6-digit OTP"
                  [(ngModel)]="emailOtp"
                  [class.invalid]="otpError()"
                />
              </div>
              <div class="otp-field">
                <label for="mobileOtp">Mobile OTP</label>
                <input
                  id="mobileOtp"
                  type="text"
                  maxlength="6"
                  placeholder="Enter 6-digit OTP"
                  [(ngModel)]="mobileOtp"
                  [class.invalid]="otpError()"
                />
              </div>

              @if (otpError()) {
                <div class="otp-error">{{ otpError() }}</div>
              }

              <button class="btn-verify" (click)="verifyOtp()" [disabled]="verifying()">
                @if (verifying()) {
                  Verifying...
                } @else {
                  Verify & Continue
                }
              </button>
            </div>
          </div>
        }

        <!-- Phase 2: File Upload -->
        @if (phase() === 'upload') {
          <div class="phase-card">
            <h2>Upload Documents</h2>
            <p class="phase-desc">
              Upload supporting documents for complaint <strong>{{ complaintNumber() }}</strong>.
              Accepted formats: PDF, JPG, PNG, DOCX. Max {{ maxFileSizeMB }}MB per file.
            </p>

            <!-- Drop zone -->
            <div
              class="drop-zone"
              [class.drag-over]="dragOver()"
              (dragover)="onDragOver($event)"
              (dragleave)="onDragLeave($event)"
              (drop)="onDrop($event)"
            >
              <i class="pi pi-cloud-upload drop-icon"></i>
              <p>Drag and drop files here, or</p>
              <label class="btn-browse" for="fileInput">Browse Files</label>
              <input
                id="fileInput"
                type="file"
                multiple
                [accept]="acceptedTypes"
                (change)="onFileSelect($event)"
                hidden
              />
              <span class="drop-hint">PDF, JPG, PNG, DOCX up to {{ maxFileSizeMB }}MB each</span>
            </div>

            <!-- Selected files list -->
            @if (selectedFiles().length > 0) {
              <div class="file-list">
                <h4>Selected Files ({{ selectedFiles().length }})</h4>
                @for (f of selectedFiles(); track f.name; let i = $index) {
                  <div class="file-item" [class.file-invalid]="!f.valid">
                    <i class="pi pi-file"></i>
                    <span class="file-name">{{ f.name }}</span>
                    <span class="file-size">{{ formatSize(f.size) }}</span>
                    @if (!f.valid) {
                      <span class="file-error">{{ f.error }}</span>
                    }
                    <button class="btn-remove" (click)="removeFile(i)" title="Remove">
                      <i class="pi pi-times"></i>
                    </button>
                  </div>
                }
              </div>
            }

            @if (uploadError()) {
              <div class="upload-error">{{ uploadError() }}</div>
            }

            <div class="upload-actions">
              <button
                class="btn-upload"
                (click)="submitFiles()"
                [disabled]="uploading() || !hasValidFiles()"
              >
                @if (uploading()) {
                  Uploading...
                } @else {
                  Upload Documents
                }
              </button>
            </div>
          </div>
        }

        <!-- Phase 3: Success -->
        @if (phase() === 'success') {
          <div class="phase-card phase-card--success">
            <i class="pi pi-check-circle success-icon"></i>
            <h2>Documents Uploaded Successfully</h2>
            <p>Your documents have been submitted for complaint <strong>{{ complaintNumber() }}</strong>. The assigned officer has been notified.</p>
            <p class="success-count">{{ uploadedCount() }} file(s) uploaded</p>
          </div>
        }
      </main>

      <footer class="upload-footer">
        <p>&copy; Reserve Bank of India. All rights reserved.</p>
      </footer>
    </div>
  `,
  styles: [`
    .upload-page {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background: #f4f6f9;
      font-family: 'Segoe UI', sans-serif;
    }
    .upload-header {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 16px 32px;
      background: #fff;
      border-bottom: 2px solid #1565c0;
    }
    .rbi-logo { height: 48px; }
    .header-text { display: flex; flex-direction: column; }
    .header-title { font-weight: 700; font-size: 16px; color: #1a237e; }
    .header-subtitle { font-size: 13px; color: #555; }

    .upload-main {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 40px 20px;
    }

    .phase-card {
      background: #fff;
      border-radius: 8px;
      padding: 40px;
      max-width: 520px;
      width: 100%;
      box-shadow: 0 2px 12px rgba(0,0,0,0.08);
    }
    .phase-card h2 {
      margin: 0 0 8px;
      font-size: 20px;
      color: #1a237e;
    }
    .phase-desc {
      color: #555;
      font-size: 14px;
      margin-bottom: 24px;
    }
    .phase-card--error {
      text-align: center;
    }
    .phase-card--success {
      text-align: center;
    }
    .error-icon { font-size: 48px; color: #e53935; margin-bottom: 16px; }
    .success-icon { font-size: 48px; color: #4caf50; margin-bottom: 16px; }
    .success-count { color: #666; font-size: 14px; margin-top: 8px; }

    .loading-spinner {
      text-align: center;
      padding: 32px;
      color: #666;
      font-size: 15px;
    }

    /* OTP Form */
    .otp-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .otp-field label {
      display: block;
      font-size: 13px;
      font-weight: 600;
      color: #333;
      margin-bottom: 4px;
    }
    .otp-field input {
      width: 100%;
      padding: 10px 12px;
      border: 1px solid #ccc;
      border-radius: 4px;
      font-size: 16px;
      letter-spacing: 4px;
      text-align: center;
    }
    .otp-field input.invalid {
      border-color: #e53935;
    }
    .otp-error {
      color: #e53935;
      font-size: 13px;
    }
    .btn-verify {
      padding: 12px;
      background: #1565c0;
      color: #fff;
      border: none;
      border-radius: 4px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-verify:disabled {
      background: #90caf9;
      cursor: not-allowed;
    }
    .btn-verify:hover:not(:disabled) {
      background: #0d47a1;
    }

    /* Drop Zone */
    .drop-zone {
      border: 2px dashed #bbb;
      border-radius: 8px;
      padding: 32px;
      text-align: center;
      cursor: pointer;
      transition: border-color 0.2s, background 0.2s;
      margin-bottom: 16px;
    }
    .drop-zone.drag-over {
      border-color: #1565c0;
      background: #e3f2fd;
    }
    .drop-icon { font-size: 36px; color: #90caf9; margin-bottom: 8px; }
    .drop-zone p { margin: 8px 0; color: #555; font-size: 14px; }
    .drop-hint { font-size: 12px; color: #999; }
    .btn-browse {
      display: inline-block;
      padding: 8px 16px;
      background: #1565c0;
      color: #fff;
      border-radius: 4px;
      font-size: 13px;
      cursor: pointer;
    }
    .btn-browse:hover { background: #0d47a1; }

    /* File List */
    .file-list {
      margin-bottom: 16px;
    }
    .file-list h4 {
      font-size: 13px;
      color: #333;
      margin: 0 0 8px;
    }
    .file-item {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 8px 12px;
      background: #f9f9f9;
      border-radius: 4px;
      margin-bottom: 4px;
      font-size: 13px;
    }
    .file-item.file-invalid {
      background: #fff3f3;
      border: 1px solid #ffcdd2;
    }
    .file-item i { color: #1565c0; }
    .file-name { flex: 1; font-weight: 500; }
    .file-size { color: #888; }
    .file-error { color: #e53935; font-size: 11px; }
    .btn-remove {
      background: none;
      border: none;
      color: #999;
      cursor: pointer;
      padding: 4px;
    }
    .btn-remove:hover { color: #e53935; }

    .upload-error {
      padding: 8px 12px;
      background: #ffebee;
      color: #c62828;
      border-radius: 4px;
      font-size: 13px;
      margin-bottom: 12px;
    }

    .upload-actions {
      display: flex;
      justify-content: flex-end;
    }
    .btn-upload {
      padding: 12px 24px;
      background: #2e7d32;
      color: #fff;
      border: none;
      border-radius: 4px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
    }
    .btn-upload:disabled {
      background: #a5d6a7;
      cursor: not-allowed;
    }
    .btn-upload:hover:not(:disabled) {
      background: #1b5e20;
    }

    .upload-footer {
      text-align: center;
      padding: 16px;
      font-size: 12px;
      color: #999;
      border-top: 1px solid #e0e0e0;
    }
  `]
})
export class ComplainantUploadComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private uploadLinkService = inject(UploadLinkService);

  readonly maxFileSizeMB = environment.maxFileSizeMB;
  readonly acceptedTypes = '.pdf,.jpg,.jpeg,.png,.docx';
  private readonly allowedExtensions = ['.pdf', '.jpg', '.jpeg', '.png', '.docx'];

  phase = signal<Phase>('loading');
  token = signal('');
  complaintNumber = signal('');
  errorMessage = signal('');

  // OTP
  emailOtp = '';
  mobileOtp = '';
  otpError = signal('');
  verifying = signal(false);

  // Upload
  selectedFiles = signal<SelectedFile[]>([]);
  dragOver = signal(false);
  uploading = signal(false);
  uploadError = signal('');
  uploadedCount = signal(0);

  ngOnInit() {
    const tokenParam = this.route.snapshot.paramMap.get('token');
    if (!tokenParam) {
      this.phase.set('error');
      this.errorMessage.set('Invalid upload link. No token provided.');
      return;
    }
    this.token.set(tokenParam);
    // Link is valid until OTP is verified; show OTP phase directly
    this.phase.set('otp');
  }

  verifyOtp() {
    this.otpError.set('');

    if (!this.emailOtp || this.emailOtp.length !== 6) {
      this.otpError.set('Please enter a valid 6-digit email OTP.');
      return;
    }
    if (!this.mobileOtp || this.mobileOtp.length !== 6) {
      this.otpError.set('Please enter a valid 6-digit mobile OTP.');
      return;
    }

    this.verifying.set(true);
    this.uploadLinkService.validateOtp(this.token(), this.emailOtp, this.mobileOtp).subscribe({
      next: (res) => {
        this.verifying.set(false);
        if (res.valid) {
          this.complaintNumber.set(res.complaintNumber);
          this.phase.set('upload');
        } else {
          this.otpError.set('Invalid OTP. Please try again.');
        }
      },
      error: (err) => {
        this.verifying.set(false);
        if (err.status === 410 || err.status === 404) {
          this.phase.set('expired');
        } else {
          this.otpError.set(err?.error?.message || 'OTP verification failed. Please try again.');
        }
      }
    });
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(true);
  }

  onDragLeave(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    event.stopPropagation();
    this.dragOver.set(false);

    const files = event.dataTransfer?.files;
    if (files) {
      this.addFiles(Array.from(files));
    }
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  private addFiles(files: File[]) {
    const maxSize = this.maxFileSizeMB * 1024 * 1024;
    const newFiles: SelectedFile[] = files.map(file => {
      const ext = '.' + file.name.split('.').pop()?.toLowerCase();
      const validExt = this.allowedExtensions.includes(ext);
      const validSize = file.size <= maxSize;
      return {
        file,
        name: file.name,
        size: file.size,
        valid: validExt && validSize,
        error: !validExt ? 'Unsupported format' : !validSize ? `Exceeds ${this.maxFileSizeMB}MB` : undefined
      };
    });

    this.selectedFiles.update(current => [...current, ...newFiles]);
    this.uploadError.set('');
  }

  removeFile(index: number) {
    this.selectedFiles.update(current => current.filter((_, i) => i !== index));
  }

  hasValidFiles(): boolean {
    return this.selectedFiles().some(f => f.valid);
  }

  submitFiles() {
    const validFiles = this.selectedFiles().filter(f => f.valid);
    if (validFiles.length === 0) {
      this.uploadError.set('Please select at least one valid file.');
      return;
    }

    this.uploading.set(true);
    this.uploadError.set('');

    const formData = new FormData();
    validFiles.forEach(f => formData.append('files', f.file, f.name));

    this.uploadLinkService.uploadDocuments(this.token(), formData).subscribe({
      next: (res) => {
        this.uploading.set(false);
        this.uploadedCount.set(res.uploaded);
        this.phase.set('success');
      },
      error: (err) => {
        this.uploading.set(false);
        if (err.status === 410) {
          this.phase.set('expired');
        } else {
          this.uploadError.set(err?.error?.message || 'Upload failed. Please try again.');
        }
      }
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }
}
