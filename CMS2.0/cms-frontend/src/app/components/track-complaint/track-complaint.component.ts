import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { CmsService } from '../../services/cms.service';
import { ComplaintStoreService } from '../../services/complaint-store.service';
import { FileUploadService } from '../../services/file-upload.service';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { FileCacheService } from '../../services/file-cache.service';
import { jsPDF } from 'jspdf';

interface ComplaintRecord {
  id: string;
  complaintAgainst: string;
  complaintDate: string;
  status: 'in_progress' | 'request_sent_back' | 'rejected' | 'resolved';
  statusLabel: string;
  comments: string;
  action: 'withdraw' | 'appeal' | 'act';
}

@Component({
  selector: 'app-track-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './track-complaint.component.html',
  styleUrl: './track-complaint.component.scss',
})
export class TrackComplaintComponent implements OnInit {
  complaints: ComplaintRecord[] = [
    { id: 'CMS-20260415-100234', complaintAgainst: 'Adarsh Bank', complaintDate: '15-08-2026', status: 'in_progress', statusLabel: 'In Progress', comments: 'Missing details ple...', action: 'withdraw' },
    { id: 'CMS-20260203-200456', complaintAgainst: 'Varada Bank', complaintDate: '03-02-2026', status: 'request_sent_back', statusLabel: 'Request Sent Back', comments: 'Missing details ple...', action: 'appeal' },
    { id: 'CMS-20251122-300789', complaintAgainst: 'Kaveri Bank', complaintDate: '22-11-2025', status: 'rejected', statusLabel: 'Rejected', comments: 'Missing details ple...', action: 'act' },
  ];

  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // Filters
  filterIdSearch = '';
  filterAgainstSearch = '';
  filterDateSearch = '';
  filterStatusSearch = '';
  filterCommentsSearch = '';

  // Detail view
  selectedComplaint: ComplaintRecord | null = null;
  showDetail = false;

  // Track detail view
  complaintNumber = '';
  loading = false;
  searched = false;
  complaint: any = null;
  timeline: any[] = [];
  error = '';

  attachments: any[] = [];
  loadingAttachments = false;
  downloadingId: number | null = null;

  // Preview modal
  previewOpen = false;
  previewUrl: SafeResourceUrl = '';
  private rawPreviewUrl = '';
  previewType: 'image' | 'video' | 'audio' | 'pdf' | 'other' = 'other';
  previewFileName = '';

  constructor(
    private cms: CmsService,
    private complaintStore: ComplaintStoreService,
    private fileUpload: FileUploadService,
    private sanitizer: InputSanitizerService,
    private domSanitizer: DomSanitizer,
    private fileCache: FileCacheService,
  ) {}

  ngOnInit() {
    this.cms.getComplaints().subscribe({
      next: (data: any[]) => {
        if (data && data.length > 0) {
          this.complaints = data.map(c => ({
            id: c.complaintNumber || c.id || '',
            complaintAgainst: c.bankName || c.complaintAgainst || '',
            complaintDate: c.createdAt ? new Date(c.createdAt).toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-') : '',
            status: this.mapStatus(c.status),
            statusLabel: c.status || '',
            comments: c.comments || c.description?.substring(0, 20) + '...' || '',
            action: this.mapAction(c.status),
          }));
        }
        this.mergeLocalComplaints();
      },
      error: () => {
        this.mergeLocalComplaints();
      },
    });
  }

  private mergeLocalComplaints() {
    const local = this.complaintStore.complaints;
    const existingIds = new Set(this.complaints.map(c => c.id));
    for (const lc of local) {
      if (!existingIds.has(lc.id)) {
        this.complaints.unshift({
          id: lc.id,
          complaintAgainst: lc.complaintAgainst,
          complaintDate: lc.complaintDate,
          status: lc.status,
          statusLabel: lc.statusLabel,
          comments: lc.comments,
          action: lc.action,
        });
      }
    }
  }

  private mapStatus(status: string): ComplaintRecord['status'] {
    const s = (status || '').toLowerCase();
    if (s.includes('reject')) return 'rejected';
    if (s.includes('sent back') || s.includes('request')) return 'request_sent_back';
    if (s.includes('resolve')) return 'resolved';
    return 'in_progress';
  }

  private mapAction(status: string): ComplaintRecord['action'] {
    const s = (status || '').toLowerCase();
    if (s.includes('reject')) return 'act';
    if (s.includes('sent back') || s.includes('request')) return 'appeal';
    return 'withdraw';
  }

  get filteredComplaints(): ComplaintRecord[] {
    return this.complaints.filter(c => {
      if (this.filterIdSearch && !c.id.toLowerCase().includes(this.filterIdSearch.toLowerCase())) return false;
      if (this.filterAgainstSearch && !c.complaintAgainst.toLowerCase().includes(this.filterAgainstSearch.toLowerCase())) return false;
      if (this.filterDateSearch && !c.complaintDate.includes(this.filterDateSearch)) return false;
      if (this.filterStatusSearch && c.status !== this.filterStatusSearch) return false;
      if (this.filterCommentsSearch && !c.comments.toLowerCase().includes(this.filterCommentsSearch.toLowerCase())) return false;
      return true;
    });
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  viewComplaint(complaint: ComplaintRecord) {
    this.selectedComplaint = complaint;
    this.showDetail = true;
    this.attachments = [];
    this.loadingAttachments = true;

    this.cms.trackComplaint(complaint.id).subscribe({
      next: (res) => {
        if (res?.id) {
          this.fileUpload.getAttachments(res.id).subscribe({
            next: (files) => {
              if (files && files.length > 0) {
                this.attachments = files;
                this.loadingAttachments = false;
              } else {
                this.loadCachedFiles(complaint.id);
              }
            },
            error: () => this.loadCachedFiles(complaint.id),
          });
          this.cms.getTimeline(res.id).subscribe(t => this.timeline = t);
        } else {
          this.loadCachedFiles(complaint.id);
        }
      },
      error: () => this.loadCachedFiles(complaint.id),
    });
  }

  private async loadCachedFiles(complaintId: string) {
    const cachedFiles = await this.fileCache.get(complaintId);

    if (cachedFiles.length > 0) {
      this.attachments = cachedFiles.map((f: File, i: number) => ({
        id: -(i + 1),
        fileName: f.name,
        originalFileName: f.name,
        fileSize: f.size,
        size: f.size,
        contentType: f.type,
        isLocal: true,
        hasCachedFile: true,
        cachedFile: f,
      }));
      this.loadingAttachments = false;
      return;
    }

    const localComplaint = this.complaintStore.getById(complaintId);
    if (localComplaint?.files?.length) {
      this.attachments = localComplaint.files.map((f: any, i: number) => ({
        id: -(i + 1),
        fileName: f.name,
        originalFileName: f.name,
        fileSize: f.size,
        size: f.size,
        contentType: f.type,
        isLocal: true,
        hasCachedFile: false,
      }));
    }
    this.loadingAttachments = false;
  }

  goBack() {
    this.showDetail = false;
    this.selectedComplaint = null;
    this.attachments = [];
  }

  downloadFile(attachment: any) {
    if (attachment.hasCachedFile && attachment.cachedFile) {
      this.downloadCachedFile(attachment.cachedFile);
      return;
    }

    this.downloadingId = attachment.id;
    this.fileUpload.downloadAttachment(attachment.id).subscribe({
      next: (blob) => {
        this.triggerDownload(blob, attachment.fileName || attachment.originalFileName || 'download');
        this.downloadingId = null;
      },
      error: () => this.downloadingId = null,
    });
  }

  previewFile(attachment: any) {
    const fileName = attachment.fileName || attachment.originalFileName || '';
    this.previewFileName = fileName;
    this.previewType = this.getPreviewType(fileName, attachment.contentType);

    if (attachment.hasCachedFile && attachment.cachedFile) {
      this.previewFromFile(attachment.cachedFile);
      return;
    }

    if (this.previewType === 'video' || this.previewType === 'audio') {
      const streamUrl = this.fileUpload.getStreamUrl(attachment.id);
      this.rawPreviewUrl = streamUrl;
      this.previewUrl = this.domSanitizer.bypassSecurityTrustResourceUrl(streamUrl);
      this.previewOpen = true;
    } else if (this.previewType === 'image' || this.previewType === 'pdf') {
      this.fileUpload.downloadAttachment(attachment.id).subscribe({
        next: (blob) => {
          this.openBlobPreview(blob);
        },
      });
    } else {
      this.fileUpload.downloadAttachment(attachment.id).subscribe({
        next: (blob) => {
          const url = URL.createObjectURL(blob);
          window.open(url, '_blank');
        },
      });
    }
  }

  private previewFromFile(file: File) {
    const blobUrl = URL.createObjectURL(file);
    this.rawPreviewUrl = blobUrl;
    this.previewUrl = this.domSanitizer.bypassSecurityTrustResourceUrl(blobUrl);
    this.previewOpen = true;
  }

  private openBlobPreview(blob: Blob) {
    const blobUrl = URL.createObjectURL(blob);
    this.rawPreviewUrl = blobUrl;
    this.previewUrl = this.domSanitizer.bypassSecurityTrustResourceUrl(blobUrl);
    this.previewOpen = true;
  }

  private downloadCachedFile(file: File) {
    this.triggerDownload(file, file.name);
  }

  private triggerDownload(blob: Blob, fileName: string) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  }

  closePreview() {
    this.previewOpen = false;
    if (this.rawPreviewUrl && this.rawPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(this.rawPreviewUrl);
    }
    this.previewUrl = '';
    this.rawPreviewUrl = '';
    this.previewFileName = '';
  }

  private getPreviewType(fileName: string, contentType?: string): 'image' | 'video' | 'audio' | 'pdf' | 'other' {
    const ext = (fileName.split('.').pop() || '').toLowerCase();
    const mime = (contentType || '').toLowerCase();

    if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'svg'].includes(ext) || mime.startsWith('image/')) return 'image';
    if (['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv'].includes(ext) || mime.startsWith('video/')) return 'video';
    if (['mp3', 'wav', 'ogg', 'aac', 'flac', 'wma'].includes(ext) || mime.startsWith('audio/')) return 'audio';
    if (ext === 'pdf' || mime === 'application/pdf') return 'pdf';
    return 'other';
  }

  formatFileSize(bytes: number): string {
    if (!bytes) return '—';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getFileIcon(fileName: string): string {
    if (!fileName) return 'pi-file';
    const ext = fileName.split('.').pop()?.toLowerCase() || '';
    if (['pdf'].includes(ext)) return 'pi-file-pdf';
    if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext)) return 'pi-image';
    if (['doc', 'docx'].includes(ext)) return 'pi-file-word';
    if (['xls', 'xlsx', 'csv'].includes(ext)) return 'pi-file-excel';
    if (['zip', 'rar', '7z'].includes(ext)) return 'pi-box';
    return 'pi-file';
  }

  search() {
    if (!this.complaintNumber.trim()) return;

    const sanitized = this.sanitizer.sanitizeComplaintNumber(this.complaintNumber.trim());
    if (!sanitized || sanitized.length < 5) {
      this.error = 'Invalid complaint number format.';
      return;
    }
    if (this.sanitizer.hasXssPayload(this.complaintNumber) || this.sanitizer.hasSqlInjectionPattern(this.complaintNumber)) {
      this.error = 'Invalid characters in complaint number.';
      return;
    }

    this.complaintNumber = sanitized;
    this.loading = true;
    this.error = '';
    this.searched = true;

    this.cms.trackComplaint(sanitized).subscribe({
      next: (res) => {
        this.complaint = res;
        this.loading = false;
        if (res?.id) {
          this.cms.getTimeline(res.id).subscribe(t => this.timeline = t);
        }
      },
      error: () => {
        this.complaint = null;
        this.error = 'Complaint not found. Please check the reference number and try again.';
        this.loading = false;
      },
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'pending': return 'status-pending';
      case 'in_progress': return 'status-progress';
      case 'resolved': return 'status-resolved';
      case 'escalated': return 'status-escalated';
      case 'closed': return 'status-closed';
      default: return '';
    }
  }

  formatStatus(status: string): string {
    if (!status) return '—';
    return status.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  }

  downloadPDF() {
    if (!this.complaint) return;
    const c = this.complaint;
    const doc = new jsPDF();
    const margin = 20;
    let y = 20;

    doc.setFontSize(18);
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(43, 57, 144);
    doc.text('RBI - Complaint Management System', margin, y);
    y += 8;
    doc.setFontSize(13);
    doc.setTextColor(0);
    doc.text('Complaint Registration Form', margin, y);
    y += 10;

    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.setTextColor(100);
    doc.text(`Reference Number: ${c.complaintNumber}`, margin, y);
    y += 5;
    doc.text(`Date Filed: ${c.createdAt ? new Date(c.createdAt).toLocaleDateString('en-IN') : new Date().toLocaleDateString('en-IN')}`, margin, y);
    y += 5;
    doc.text(`Status: ${this.formatStatus(c.status)}`, margin, y);
    y += 10;

    doc.setDrawColor(43, 57, 144);
    doc.setLineWidth(0.5);
    doc.line(margin, y, 190, y);
    y += 10;

    const fields = [
      { label: 'Complainant Name', value: c.complainantName },
      { label: 'Email', value: c.complainantEmail },
      { label: 'Phone', value: c.complainantPhone },
      { label: 'Address', value: c.complainantAddress },
      { label: 'Bank / Institution', value: c.bank?.name || c.bankId || '—' },
      { label: 'Branch', value: c.bankBranch },
      { label: 'Account Number', value: c.accountNumber },
      { label: 'Category', value: c.category?.name || c.categoryId || '—' },
      { label: 'Subject', value: c.subject },
      { label: 'Priority', value: c.priority },
      { label: 'Filing Type', value: c.filingType },
      { label: 'Assigned Officer', value: c.assignedOfficer },
    ];

    doc.setTextColor(0);
    for (const field of fields) {
      if (y > 265) { doc.addPage(); y = 20; }
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(9);
      doc.setTextColor(80);
      doc.text(`${field.label}:`, margin, y);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(0);
      doc.text(String(field.value || '—'), margin + 50, y);
      y += 7;
    }

    y += 6;
    doc.setDrawColor(200);
    doc.line(margin, y, 190, y);
    y += 8;

    if (c.description) {
      if (y > 240) { doc.addPage(); y = 20; }
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(10);
      doc.setTextColor(43, 57, 144);
      doc.text('Facts of the Complaint:', margin, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9);
      doc.setTextColor(0);
      const lines = doc.splitTextToSize(c.description, 160);
      doc.text(lines, margin, y);
      y += lines.length * 4.5 + 6;
    }

    if (c.reliefSought) {
      if (y > 250) { doc.addPage(); y = 20; }
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(10);
      doc.setTextColor(43, 57, 144);
      doc.text('Relief Sought:', margin, y);
      y += 6;
      doc.setFont('helvetica', 'normal');
      doc.setFontSize(9);
      doc.setTextColor(0);
      const lines = doc.splitTextToSize(c.reliefSought, 160);
      doc.text(lines, margin, y);
      y += lines.length * 4.5 + 6;
    }

    if (this.timeline.length) {
      y += 4;
      if (y > 240) { doc.addPage(); y = 20; }
      doc.setDrawColor(200);
      doc.line(margin, y, 190, y);
      y += 8;
      doc.setFont('helvetica', 'bold');
      doc.setFontSize(10);
      doc.setTextColor(43, 57, 144);
      doc.text('Activity Timeline:', margin, y);
      y += 7;

      doc.setFontSize(9);
      for (const entry of this.timeline) {
        if (y > 270) { doc.addPage(); y = 20; }
        doc.setFont('helvetica', 'bold');
        doc.setTextColor(0);
        const date = entry.performedAt ? new Date(entry.performedAt).toLocaleString('en-IN') : '';
        doc.text(`• ${entry.action}`, margin, y);
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(100);
        doc.text(date, margin + 60, y);
        y += 5;
        if (entry.remarks) {
          doc.setTextColor(60);
          doc.text(`  ${entry.remarks}`, margin + 4, y);
          y += 5;
        }
        y += 2;
      }
    }

    y += 10;
    if (y > 270) { doc.addPage(); y = 20; }
    doc.setDrawColor(200);
    doc.line(margin, y, 190, y);
    y += 8;
    doc.setFontSize(8);
    doc.setTextColor(120);
    doc.text('This is a system-generated document from the Complaint Management System (CMS).', margin, y);
    y += 4;
    doc.text('Reserve Bank of India - All Rights Reserved.', margin, y);

    doc.save(`Complaint_${c.complaintNumber}.pdf`);
  }
}
