import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';

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
  imports: [CommonModule, RouterLink],
  templateUrl: './complaint-detail.component.html',
  styleUrl: './complaint-detail.component.scss'
})
export class ComplaintDetailComponent implements OnInit {

  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(PublicAuthService);

  complaint = signal<any>(null);
  loading = signal(true);
  activeTab = signal<'details' | 'history'>('details');
  comments = signal<Comment[]>([]);
  statusHistory = signal<StatusHistoryItem[]>([]);

  ngOnInit() {
    const id = this.route.snapshot.paramMap.get('id');
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

  withdraw() {
    const c = this.complaint();
    if (c?.complaintId) {
      this.router.navigate(['/public/withdraw'], { queryParams: { complaintId: c.complaintId } });
    }
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
