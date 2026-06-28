import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './complaint-history.component.html',
  styleUrl: './complaint-history.component.scss'
})
export class ComplaintHistoryComponent implements OnInit {

  private http = inject(HttpClient);
  private authService = inject(PublicAuthService);

  complaints = signal<ComplaintRecord[]>([
    { complaintId: 'N20262700700001', entityName: 'Adarsh Bank', complaintDate: '2026-08-15', status: 'IN_PROGRESS', comments: 'Missing details ple...' },
    { complaintId: 'N20262700717363', entityName: 'Varada Bank', complaintDate: '2026-02-03', status: 'CLOSED', comments: 'Missing details ple...' },
    { complaintId: 'N20262700673827', entityName: 'Kaveri Bank', complaintDate: '2026-11-22', status: 'INFORMATION_REQUIRED', comments: 'Missing details ple...' },
  ]);
  loading = signal(false);

  filters = { complaintId: '', entityName: '', date: '', status: '', comments: '' };

  filteredComplaints = computed(() => {
    return this.complaints().filter(c => {
      const f = this.filters;
      return (!f.complaintId || c.complaintId.toLowerCase().includes(f.complaintId.toLowerCase()))
        && (!f.entityName || c.entityName.toLowerCase().includes(f.entityName.toLowerCase()))
        && (!f.status || c.status.toLowerCase().includes(f.status.toLowerCase()))
        && (!f.comments || c.comments.toLowerCase().includes(f.comments.toLowerCase()));
    });
  });

  ngOnInit() {
    this.loadComplaints();
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data) && data.length > 0) {
          this.complaints.set(data.map((c: any) => ({
            complaintId: c.complaintId || c.id,
            entityName: c.entityName || c.complainantName || '—',
            complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
            status: c.status || 'PENDING',
            comments: c.comments || c.description?.substring(0, 50) || '—'
          })));
        }
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'CLOSED': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': return 'status-info-required';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In Progress';
      case 'CLOSED': return 'Closed';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      default: return status.replace(/_/g, ' ');
    }
  }

  formatDate(dateStr: string): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      return new Date(dateStr).toLocaleDateString('en-IN', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-');
    } catch { return dateStr; }
  }
}
