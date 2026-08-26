import { Component, OnInit, inject, signal, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService, DraftRecord } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { Table, TableModule } from 'primeng/table';
import { Select } from 'primeng/select';
import { ComplaintRecord } from '../models';

@Component({
  selector: 'app-complaint-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, TableModule, Select],
  templateUrl: './complaint-history.component.html',
  styleUrl: './complaint-history.component.scss'
})
export class ComplaintHistoryComponent implements OnInit {

  @ViewChild('dt') dt!: Table;

  private http = inject(HttpClient);
  private router = inject(Router);
  private authService = inject(PublicAuthService);
  private complaintService = inject(ComplaintService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);
  private entityMap: Record<string, string> = {};

  statusOptions = [
    { label: 'All', value: '' },
    { label: 'Draft', value: 'DRAFT' },
    { label: 'Rejected', value: 'REJECTED' },
    { label: 'Approved', value: 'APPROVED' }
  ];

  selectedStatus = '';
  dateFrom = '';

  ngOnInit() {
    this.loadEntities();
    this.loadComplaints();
  }

  onStatusFilter(value: string) {
    this.selectedStatus = value;
    this.dt.filter(value, 'status', 'equals');
  }

  onDateFromChange(event: Event) {
    this.dateFrom = (event.target as HTMLInputElement).value;
    if (this.dateFrom) {
      this.dt.filter(this.dateFrom, 'complaintDate', 'dateAfter');
    } else {
      this.dt.filter('', 'complaintDate', 'contains');
    }
  }

  private loadEntities() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`).subscribe({
      next: (res) => {
        const entities = res?.data ?? res ?? [];
        entities.forEach((e: any) => { this.entityMap[String(e.id)] = e.name; });
      },
      error: () => {}
    });
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    const allRecords: ComplaintRecord[] = [];

    const localDraft = this.getLocalDraft();
    if (localDraft) {
      allRecords.push(localDraft);
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data)) {
          allRecords.push(...data.map((c: any) => ({
            complaintId: c.complaintId || c.id,
            entityName: c.entityName || c.complainantName || '—',
            complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
            status: c.status || 'PENDING',
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
            const withoutLocal = list.filter(l => l.draftId !== 'local');
            const merged = [...serverDrafts.filter(sd => !withoutLocal.some(l => l.draftId === sd.draftId)), ...withoutLocal];
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

  private getLocalDraft(): ComplaintRecord | null {
    const saved = sessionStorage.getItem('cms_complaint_draft');
    if (!saved) return null;
    try {
      const draft = JSON.parse(saved);
      const entityName = draft.entityName
        || (draft.eligibilityAnswers?.['regulatedEntity'] ? this.getEntityNameFromId(draft.eligibilityAnswers['regulatedEntity']) : null)
        || '—';
      const savedAt = sessionStorage.getItem('cms_draft_saved_at') || new Date().toISOString();
      return {
        complaintId: 'DRAFT-LOCAL',
        entityName,
        complaintDate: savedAt,
        status: 'DRAFT',
        isDraft: true,
        draftId: 'local'
      };
    } catch {
      return null;
    }
  }

  private getEntityNameFromId(id: string): string {
    return this.entityMap[id] || '—';
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

  resumeDraft(record: ComplaintRecord) {
    if (record.draftId === 'local') {
      this.router.navigate(['/public/file-complaint'], { queryParams: { resume: 'true' } });
    } else if (record.draftId) {
      this.router.navigate(['/public/file-complaint'], { queryParams: { draftId: record.draftId } });
    }
  }

  deleteDraft(record: ComplaintRecord) {
    if (!record.draftId) return;
    if (record.draftId === 'local') {
      sessionStorage.removeItem('cms_complaint_draft');
      sessionStorage.removeItem('cms_draft_saved_at');
      sessionStorage.removeItem('cms_draft_id');
      this.complaints.update(list => list.filter(c => c.draftId !== 'local'));
    } else {
      this.complaintService.deleteDraft(record.draftId).subscribe({
        next: () => {
          this.complaints.update(list => list.filter(c => c.draftId !== record.draftId));
        },
        error: () => {}
      });
    }
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
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Closed';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'PENDING': return 'Pending';
      case 'DRAFT': return 'Draft';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
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
