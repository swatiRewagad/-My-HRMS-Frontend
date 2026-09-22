import { Component, Input, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import {
  ComplaintCorrespondenceService, ComplaintAttachmentRow
} from '../../../services/complaint-correspondence.service';
import { environment } from '../../../../environments/environment';

/**
 * The Attachments tab (UST585-589).
 *
 * <p>THERE WAS NO ATTACHMENTS TAB AT ALL on the RBIO complaint screen — the tab list held eight entries
 * and none of them was attachments, and the word did not appear anywhere in the component. The closest
 * existing surface, the CEPC "Upload Document" control, was a mock: it checked the file size and pushed a
 * fabricated object into a local signal without ever calling an endpoint, and displayed an
 * {@code uploadedBy} value invented from the current username for a column that did not exist in the
 * database.
 *
 * <p>UST589 source distinguishability is now real: {@code source} and {@code uploadedBy} are persisted
 * columns, so a complainant's own submission through the secure upload link is visibly distinct from an
 * officer's upload.
 *
 * <p>The file-size limit is displayed from configuration rather than hardcoded, because five different
 * limits existed across the codebase (2/5/10/25/50 MB) and a UI that states the wrong one teaches users
 * to expect the wrong thing.
 */
@Component({
  selector: 'app-rbio-attachments',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './rbio-attachments.component.html',
  styleUrl: './rbio-attachments.component.scss'
})
export class RbioAttachmentsComponent implements OnInit {

  @Input() complaintNumber: string | null = null;
  @Input() complaintId: string | number | null = null;

  private service = inject(ComplaintCorrespondenceService);

  attachments = signal<ComplaintAttachmentRow[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  uploading = signal(false);
  uploadError = signal<string | null>(null);

  /** UST589: a source filter. A signal, because the computed below reads it. */
  sourceFilter = signal('ALL');

  readonly maxFileSizeMb = environment.maxFileSizeMB ?? 2;
  readonly maxTotalSizeMb = environment.maxTotalUploadSizeMB ?? 25;

  /** Strings, because the translate pipe takes Record<string, string>. */
  readonly limitParams: Record<string, string> = {
    maxFile: String(this.maxFileSizeMb),
    maxTotal: String(this.maxTotalSizeMb)
  };

  visibleAttachments = computed(() => {
    const f = this.sourceFilter();
    if (f === 'ALL') return this.attachments();
    return this.attachments().filter(a => (a.source || 'UNKNOWN') === f);
  });

  ngOnInit() {
    this.load();
  }

  load() {
    if (this.complaintId == null) return;
    this.loading.set(true);
    this.error.set(null);

    this.service.getAttachments(this.complaintId).subscribe({
      next: rows => { this.attachments.set(rows || []); this.loading.set(false); },
      error: () => { this.error.set('attachment.error_load_failed'); this.loading.set(false); }
    });
  }

  /** UST585: "Attach New Document". */
  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length || !this.complaintNumber || this.complaintId == null) return;

    const file = input.files[0];
    this.uploading.set(true);
    this.uploadError.set(null);

    this.service.uploadAttachment(this.complaintNumber, this.complaintId, file).subscribe({
      next: () => {
        this.uploading.set(false);
        input.value = '';
        this.load();
      },
      // A failed upload is reported. The pattern this replaces set a "uploaded" flag in the error
      // handler, so a rejected file looked like a successful one.
      error: err => {
        this.uploading.set(false);
        input.value = '';
        this.uploadError.set(err?.error?.message || 'attachment.error_upload_failed');
      }
    });
  }

  /** UST588: download-all bundle. */
  bundleUrl(): string {
    if (this.complaintId == null || !this.complaintNumber) return '';
    return this.service.bundleUrl(this.complaintId, this.complaintNumber);
  }

  downloadUrl(attachmentId: number): string {
    return `${environment.apiBaseUrl}/api/files/download/${attachmentId}`;
  }

  sourceKey(source?: string): string {
    if (!source) return 'attachment.source.unknown';
    return 'attachment.source.' + source.toLowerCase();
  }

  formatSize(bytes?: number): string {
    if (!bytes) return '—';
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / 1048576).toFixed(2)} MB`;
  }
}
