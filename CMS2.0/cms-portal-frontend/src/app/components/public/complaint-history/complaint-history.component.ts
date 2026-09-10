import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { Table, TableModule } from 'primeng/table';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { FilterService } from 'primeng/api';
import { ComplaintRecord } from '../models';

@Component({
  selector: 'app-complaint-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, TableModule, Select, DatePicker],
  templateUrl: './complaint-history.component.html',
  styleUrl: './complaint-history.component.scss'
})
export class ComplaintHistoryComponent implements OnInit {

  @ViewChild('dt') dt!: Table;

  private http = inject(HttpClient);
  private router = inject(Router);
  private authService = inject(PublicAuthService);
  private complaintService = inject(ComplaintService);
  private filterService = inject(FilterService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

  statusOptions = [
    { label: 'All', value: '' },
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Pending', value: 'PENDING' },
    { label: 'In-Progress', value: 'IN_PROGRESS' },
    { label: 'Complaint Closed', value: 'CLOSED' },
    { label: 'Rejected', value: 'REJECTED' }
  ];

  selectedStatus = '';
  dateFilter: Date | null = null;

  ngOnInit() {
    this.registerDateEqualsFilter();
    this.loadComplaints();
  }

  private registerDateEqualsFilter() {
    this.filterService.register('dateEquals', (value: any, filter: any): boolean => {
      if (!filter) return true;
      if (!value || value === '—') return false;
      const rowDate = new Date(value);
      if (isNaN(rowDate.getTime())) return false;
      const filterDate = new Date(filter);
      return rowDate.getFullYear() === filterDate.getFullYear()
        && rowDate.getMonth() === filterDate.getMonth()
        && rowDate.getDate() === filterDate.getDate();
    });
  }

  onStatusFilter(value: string) {
    this.selectedStatus = value;
    this.dt.filter(value, 'status', 'equals');
  }

  onDateFilterChange(date: Date | null) {
    this.dateFilter = date;
    if (date) {
      this.dt.filter(date.toISOString(), 'complaintDate', 'dateEquals');
    } else {
      this.dt.filter(null, 'complaintDate', 'dateEquals');
    }
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    const allRecords: ComplaintRecord[] = [];

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data)) {
          allRecords.push(...data.map((c: any) => ({
            complaintId: c.complaintId || c.id,
            entityName: c.entityName || c.regulatedEntityName || c.complainantName || '—',
            complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
            status: c.status || 'PENDING',
            closureClause: c.closureClause || '',
            closureDate: c.closureDate || c.complaintClosureDate || '',
            acknowledgementLetterUrl: c.acknowledgementLetterUrl || c.acknowledgementLetter || '',
            closureLetterUrl: c.closureLetterUrl || c.closureLetter || '',
          })));
        }
        this.finalizeLoad(allRecords);
      },
      error: () => {
        this.finalizeLoad(allRecords);
      }
    });

    this.complaintService.getDrafts(phone).subscribe({
      next: (drafts) => {
        const serverDrafts = drafts.map(d => ({
          complaintId: d.draftId,
          entityName: d.entityName || '—',
          complaintDate: d.updatedAt || '—',
          status: 'DRAFT',
          isDraft: true,
          draftId: d.draftId
        }));
        if (serverDrafts.length) {
          this.complaints.update(list => {
            const merged = [...serverDrafts.filter(sd => !list.some(l => l.draftId === sd.draftId)), ...list];
            merged.sort((a, b) => {
              if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
              if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
              return 0;
            });
            return merged;
          });
        }
      },
      error: () => {}
    });
  }

  private finalizeLoad(records: ComplaintRecord[]) {
    records.sort((a, b) => {
      if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
      if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
      return new Date(b.complaintDate).getTime() - new Date(a.complaintDate).getTime();
    });
    this.complaints.set(records);
    this.loading.set(false);
  }

  trackComplaint(record: ComplaintRecord) {
    this.router.navigate(['/public/complaint', record.complaintId]);
  }

  viewComplaint(record: ComplaintRecord) {
    if (record.isDraft) {
      this.resumeDraft(record);
    } else {
      this.router.navigate(['/public/complaint', record.complaintId]);
    }
  }

  fileAppeal(record: ComplaintRecord) {
    this.router.navigate(['/public/file-appeal', record.complaintId]);
  }

  shareFeedback(record: ComplaintRecord) {
    this.router.navigate(['/public/feedback', record.complaintId]);
  }

  resumeDraft(record: ComplaintRecord) {
    if (record.draftId) {
      this.router.navigate(['/public/file-complaint'], { queryParams: { draftId: record.draftId } });
    }
  }

  deleteDraft(record: ComplaintRecord) {
    if (!record.draftId) return;
    this.complaintService.deleteDraft(record.draftId).subscribe({
      next: () => {
        this.complaints.update(list => list.filter(c => c.draftId !== record.draftId));
      },
      error: () => {}
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'CLOSED': case 'NON_MAINTAINABLE': case 'APPROVED': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': case 'REJECTED': return 'status-info-required';
      case 'PENDING': return 'status-pending';
      case 'DRAFT': return 'status-draft';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In-Progress';
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Complaint Closed';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'PENDING': return 'Pending';
      case 'DRAFT': return 'Draft';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
      default: return status.replace(/_/g, ' ');
    }
  }

  formatDate(dateStr: string | undefined): string {
    if (!dateStr || dateStr === '—') return '—';
    try {
      const d = new Date(dateStr);
      if (isNaN(d.getTime())) return '—';
      return [
        String(d.getDate()).padStart(2, '0'),
        String(d.getMonth() + 1).padStart(2, '0'),
        String(d.getFullYear())
      ].join('-');
    } catch { return dateStr; }
  }
}
