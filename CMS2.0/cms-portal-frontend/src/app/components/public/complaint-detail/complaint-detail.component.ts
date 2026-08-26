import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface Comment {
  author: string;
  authorType: 'CMS' | 'USER';
  message: string;
  link?: string;
  date: string;
}

interface StatusHistoryItem {
  status: string;
  date: string;
  remarks?: string;
}

@Component({
  selector: 'app-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SpeechButtonComponent, TranslatePipe],
  templateUrl: './complaint-detail.component.html',
  styleUrl: './complaint-detail.component.scss'
})
export class ComplaintDetailComponent implements OnInit {

  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(PublicAuthService);
  private complaintService = inject(ComplaintService);

  complaint = signal<any>(null);
  loading = signal(true);
  activeTab = signal<'details' | 'history'>('details');
  comments = signal<Comment[]>([]);
  statusHistory = signal<StatusHistoryItem[]>([]);

  mode: 'view' | 'withdraw' = 'view';

  // Withdrawal state
  showWithdrawModal = signal(false);
  reason = '';
  additionalRemarks = '';
  withdrawalDocs: File[] = [];
  isDragOver = false;
  fileUploadError = signal('');
  withdrawError = signal('');
  withdrawnRef = '';
  withdrawSuccess = signal(false);

  reasons: string[] = [
    'Issue resolved by the Regulated Entity',
    'Complaint filed by mistake',
    'Duplicate complaint filed',
    'Want to approach a different forum',
    'Personal reasons',
    'Other',
  ];

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
    this.mode = this.route.snapshot.data['mode'] || 'view';
    if (id) {
      this.loadComplaint(id);
    }
  }

  private loadComplaint(id: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const data = res?.data ?? res;
        this.complaint.set(data);
        this.comments.set(data?.comments ?? []);
        this.statusHistory.set(data?.statusHistory ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  switchTab(tab: 'details' | 'history') {
    this.activeTab.set(tab);
  }

  getStatusClass(status: string): string {
    switch (status?.toUpperCase()) {
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'status-closed';
      case 'IN_PROGRESS': case 'INPROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': return 'status-info-required';
      case 'DRAFT': return 'status-draft';
      case 'SENT_BACK': case 'REQUEST_SENT_BACK': return 'status-sent-back';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status?.toUpperCase()) {
      case 'IN_PROGRESS': case 'INPROGRESS': return 'In Progress';
      case 'CLOSED': return 'Closed';
      case 'NON_MAINTAINABLE': return 'Non Maintainable';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'DRAFT': return 'Draft';
      case 'SENT_BACK': case 'REQUEST_SENT_BACK': return 'Request Sent Back';
      case 'PENDING': return 'Pending';
      default: return status?.replace(/_/g, ' ') || '—';
    }
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-');
    } catch { return dateStr; }
  }

  goBack() {
    this.router.navigate(['/public/history']);
  }

  // Withdraw actions
  openWithdrawModal() {
    if (this.mode === 'withdraw') {
      this.showWithdrawModal.set(true);
    } else {
      const c = this.complaint();
      if (c?.complaintId) {
        this.router.navigate(['/public/withdraw', c.complaintId]);
      }
    }
  }

  closeWithdrawModal() {
    this.showWithdrawModal.set(false);
    this.reason = '';
    this.additionalRemarks = '';
    this.withdrawalDocs = [];
    this.fileUploadError.set('');
    this.withdrawError.set('');
  }

  confirmWithdraw() {
    if (!this.reason) {
      this.withdrawError.set('Please select a reason for withdrawal.');
      return;
    }
    this.withdrawError.set('');
    this.loading.set(true);

    const c = this.complaint();
    const remarks = this.reason === 'Other' ? this.additionalRemarks : this.reason;

    this.complaintService.withdrawComplaint(c.complaintId, this.reason, remarks).subscribe({
      next: () => {
        this.withdrawnRef = c.complaintId;
        this.loading.set(false);
        this.showWithdrawModal.set(false);
        this.withdrawSuccess.set(true);
      },
      error: () => {
        this.withdrawnRef = c.complaintId;
        this.loading.set(false);
        this.showWithdrawModal.set(false);
        this.withdrawSuccess.set(true);
      }
    });
  }

  onWithdrawalFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    this.fileUploadError.set('');
    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      if (file.size > 2 * 1024 * 1024) {
        this.fileUploadError.set('File size exceeds limit (2MB).');
        continue;
      }
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
        this.fileUploadError.set('Invalid file type. Supported: PDF, JPG, PNG.');
        continue;
      }
      this.withdrawalDocs.push(file);
    }
    input.value = '';
  }

  onFileDrop(event: DragEvent) {
    event.preventDefault();
    this.isDragOver = false;
    if (!event.dataTransfer?.files?.length) return;
    this.fileUploadError.set('');
    for (let i = 0; i < event.dataTransfer.files.length; i++) {
      const file = event.dataTransfer.files[i];
      if (file.size > 2 * 1024 * 1024) {
        this.fileUploadError.set('File size exceeds limit (2MB).');
        continue;
      }
      if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)) {
        this.fileUploadError.set('Invalid file type. Supported: PDF, JPG, PNG.');
        continue;
      }
      this.withdrawalDocs.push(file);
    }
  }

  removeWithdrawalDoc(index: number) {
    this.withdrawalDocs.splice(index, 1);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  downloadPdf() {
    const c = this.complaint();
    if (!c?.complaintId) return;
    this.http.get(`${environment.apiBaseUrl}/api/v1/complaints/${c.complaintId}/pdf`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `complaint-${c.complaintId}.pdf`;
        a.click();
        window.URL.revokeObjectURL(url);
      },
      error: () => {}
    });
  }
}
