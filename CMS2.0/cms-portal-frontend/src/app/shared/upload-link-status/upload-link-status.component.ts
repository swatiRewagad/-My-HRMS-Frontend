import { Component, Input, Output, EventEmitter, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UploadLinkService, UploadLinkStatus } from '../../services/upload-link.service';

@Component({
  selector: 'app-upload-link-status',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="upload-link-status">
      @if (loading()) {
        <div class="uls-loading">Checking upload link status...</div>
      }

      <!-- No link sent yet -->
      @if (!loading() && !status()) {
        <div class="uls-no-link">
          <button class="btn-send-link" (click)="sendUploadLink()">
            <i class="pi pi-link"></i> Send Document Upload Link to Complainant
          </button>
          <span class="uls-hint">Sends a secure link via email and SMS for document upload</span>
        </div>
      }

      <!-- Link active -->
      @if (!loading() && status()?.linkActive && !status()?.documentsSubmitted) {
        <div class="uls-banner uls-banner--active">
          <div class="uls-banner-content">
            <i class="pi pi-exclamation-triangle"></i>
            <div class="uls-banner-text">
              <strong>Upload link active</strong> — expires {{ formatExpiry(status()!.expiresAt) }}.
              Closure is blocked while link is active.
            </div>
          </div>
          <div class="uls-banner-actions">
            <span class="uls-countdown">{{ getCountdown(status()!.expiresAt) }}</span>
            <button class="btn-revoke" (click)="revokeUploadLink()">Revoke Link</button>
          </div>
        </div>
      }

      <!-- Documents submitted -->
      @if (!loading() && status()?.documentsSubmitted) {
        <div class="uls-banner uls-banner--success">
          <div class="uls-banner-content">
            <i class="pi pi-check-circle"></i>
            <div class="uls-banner-text">
              <strong>Documents received from complainant</strong>
            </div>
          </div>
          <div class="uls-file-list">
            @for (file of status()!.uploadedFiles; track file.name) {
              <div class="uls-file-item">
                <i class="pi pi-file"></i>
                <span class="uls-file-name">{{ file.name }}</span>
                <span class="uls-file-size">{{ formatFileSize(file.size) }}</span>
                <span class="uls-file-date">{{ file.uploadedAt | date:'dd-MM-yyyy HH:mm' }}</span>
              </div>
            }
          </div>
        </div>
      }

      <!-- Link expired without upload -->
      @if (!loading() && status() && !status()!.linkActive && !status()!.documentsSubmitted && status()!.sentAt) {
        <div class="uls-banner uls-banner--expired">
          <div class="uls-banner-content">
            <i class="pi pi-clock"></i>
            <div class="uls-banner-text">
              <strong>Link expired</strong> — no documents were received from complainant.
            </div>
          </div>
          <button class="btn-send-link btn-send-link--small" (click)="sendUploadLink()">
            <i class="pi pi-refresh"></i> Resend Link
          </button>
        </div>
      }

      @if (errorMessage()) {
        <div class="uls-error">{{ errorMessage() }}</div>
      }
    </div>
  `,
  styles: [`
    .upload-link-status {
      margin: 12px 0;
    }
    .uls-loading {
      padding: 8px 12px;
      color: #666;
      font-size: 13px;
    }
    .uls-no-link {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .btn-send-link {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      background: #1565c0;
      color: #fff;
      border: none;
      border-radius: 4px;
      font-size: 13px;
      cursor: pointer;
      transition: background 0.2s;
    }
    .btn-send-link:hover {
      background: #0d47a1;
    }
    .btn-send-link--small {
      padding: 6px 12px;
      font-size: 12px;
    }
    .uls-hint {
      font-size: 12px;
      color: #888;
    }
    .uls-banner {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px 16px;
      border-radius: 6px;
      font-size: 13px;
    }
    .uls-banner--active {
      background: #fff8e1;
      border: 1px solid #ffcc02;
    }
    .uls-banner--success {
      background: #e8f5e9;
      border: 1px solid #4caf50;
    }
    .uls-banner--expired {
      background: #f5f5f5;
      border: 1px solid #bdbdbd;
    }
    .uls-banner-content {
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .uls-banner--active .uls-banner-content i { color: #f9a825; }
    .uls-banner--success .uls-banner-content i { color: #4caf50; }
    .uls-banner--expired .uls-banner-content i { color: #9e9e9e; }
    .uls-banner-text {
      flex: 1;
    }
    .uls-banner-actions {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-left: 28px;
    }
    .uls-countdown {
      font-weight: 600;
      color: #e65100;
      font-size: 12px;
    }
    .btn-revoke {
      padding: 4px 12px;
      background: #fff;
      border: 1px solid #e53935;
      color: #e53935;
      border-radius: 4px;
      font-size: 12px;
      cursor: pointer;
    }
    .btn-revoke:hover {
      background: #ffebee;
    }
    .uls-file-list {
      margin-left: 28px;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .uls-file-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 12px;
      color: #333;
    }
    .uls-file-item i { color: #1565c0; }
    .uls-file-name { font-weight: 500; }
    .uls-file-size { color: #888; }
    .uls-file-date { color: #888; margin-left: auto; }
    .uls-error {
      margin-top: 8px;
      padding: 6px 12px;
      background: #ffebee;
      color: #c62828;
      border-radius: 4px;
      font-size: 12px;
    }
  `]
})
export class UploadLinkStatusComponent implements OnInit {
  @Input({ required: true }) complaintNumber!: string;
  @Output() linkStatusChange = new EventEmitter<{ linkActive: boolean; documentsSubmitted: boolean }>();

  private uploadLinkService = inject(UploadLinkService);

  loading = signal(false);
  status = signal<UploadLinkStatus | null>(null);
  errorMessage = signal('');

  ngOnInit() {
    this.loadStatus();
  }

  loadStatus() {
    if (!this.complaintNumber) return;
    this.loading.set(true);
    this.errorMessage.set('');

    this.uploadLinkService.getStatus(this.complaintNumber).subscribe({
      next: (res) => {
        this.status.set(res);
        this.loading.set(false);
        this.emitStatus(res);
      },
      error: () => {
        // No link found — show "send link" button
        this.status.set(null);
        this.loading.set(false);
        this.emitStatus(null);
      }
    });
  }

  sendUploadLink() {
    this.loading.set(true);
    this.errorMessage.set('');

    this.uploadLinkService.sendLink(this.complaintNumber).subscribe({
      next: () => {
        this.loadStatus();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || 'Failed to send upload link. Please try again.');
      }
    });
  }

  revokeUploadLink() {
    this.loading.set(true);
    this.errorMessage.set('');

    this.uploadLinkService.revokeLink(this.complaintNumber).subscribe({
      next: () => {
        this.loadStatus();
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err?.error?.message || 'Failed to revoke link. Please try again.');
      }
    });
  }

  formatExpiry(expiresAt: string | null): string {
    if (!expiresAt) return '';
    const d = new Date(expiresAt);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  getCountdown(expiresAt: string | null): string {
    if (!expiresAt) return '';
    const now = new Date().getTime();
    const exp = new Date(expiresAt).getTime();
    const diff = exp - now;
    if (diff <= 0) return 'Expired';
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    if (days > 0) return `${days}d ${hours}h remaining`;
    return `${hours}h remaining`;
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  private emitStatus(status: UploadLinkStatus | null) {
    this.linkStatusChange.emit({
      linkActive: status?.linkActive ?? false,
      documentsSubmitted: status?.documentsSubmitted ?? false
    });
  }
}
