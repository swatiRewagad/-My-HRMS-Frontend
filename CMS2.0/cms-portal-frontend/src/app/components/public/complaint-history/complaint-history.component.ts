import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';

interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  comments: string;
}

@Component({
  selector: 'app-complaint-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './complaint-history.component.html',
  styleUrl: './complaint-history.component.scss'
})
export class ComplaintHistoryComponent implements OnInit {

  private http = inject(HttpClient);
  private authService = inject(PublicAuthService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  ngOnInit() {
    this.loadComplaints();
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        this.complaints.set(Array.isArray(data) ? data.map((c: any) => ({
          complaintId: c.complaintId || c.id,
          entityName: c.entityName || c.complainantName || '—',
          complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
          status: c.status || 'PENDING',
          comments: c.comments || c.description?.substring(0, 50) || '—'
        })) : []);
        this.loading.set(false);
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'RESOLVED': case 'CLOSED': return 'status-resolved';
      case 'UNDER_REVIEW': case 'IN_PROGRESS': return 'status-review';
      case 'REJECTED': case 'NON_MAINTAINABLE': return 'status-rejected';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    return status.replace(/_/g, ' ');
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
    } catch {
      return dateStr;
    }
  }
}
