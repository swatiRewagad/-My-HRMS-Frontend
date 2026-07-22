import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService, DraftRecord } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  comments: string;
  isDraft?: boolean;
  draftId?: string;
}

@Component({
  selector: 'app-complaint-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe],
  templateUrl: './complaint-history.component.html',
  styleUrl: './complaint-history.component.scss'
})
export class ComplaintHistoryComponent implements OnInit {

  private http = inject(HttpClient);
  private router = inject(Router);
  private authService = inject(PublicAuthService);
  private complaintService = inject(ComplaintService);

  complaints = signal<ComplaintRecord[]>([]);
  loading = signal(true);

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
    const allRecords: ComplaintRecord[] = [];

    // Load local draft from localStorage
    const localDraft = this.getLocalDraft();
    if (localDraft) {
      allRecords.push(localDraft);
    }

    // Load submitted complaints from server
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`).subscribe({
      next: (res) => {
        const data = res?.data || res || [];
        if (Array.isArray(data)) {
          allRecords.push(...data.map((c: any) => ({
            complaintId: c.complaintId || c.id,
            entityName: c.entityName || c.complainantName || '—',
            complaintDate: c.createdAt || c.complaintDate || c.registeredDate || '—',
            status: c.status || 'PENDING',
            comments: c.comments || c.description?.substring(0, 50) || '—'
          })));
        }
        this.finalizeLoad(allRecords);
      },
      error: () => {
        this.finalizeLoad(allRecords);
      }
    });

    // Also try server-side drafts (if backend available)
    this.complaintService.getDrafts(phone).subscribe({
      next: (drafts) => {
        const serverDrafts = drafts.map(d => ({
          complaintId: d.draftId,
          entityName: d.entityName || '—',
          complaintDate: d.updatedAt || '—',
          status: 'DRAFT',
          comments: `Step ${d.currentStep} — ${d.phase === 'eligibility' ? 'Eligibility Check' : 'Form in progress'}`,
          isDraft: true,
          draftId: d.draftId
        }));
        if (serverDrafts.length) {
          this.complaints.update(list => {
            // Replace local draft with server draft
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
    const saved = localStorage.getItem('cms_complaint_draft');
    if (!saved) return null;
    try {
      const draft = JSON.parse(saved);
      const entityName = draft.formData?.['selectedEntity']
        ? this.getEntityNameFromId(draft.formData['selectedEntity'])
        : (draft.eligibilityAnswers?.['selectedEntity']
          ? this.getEntityNameFromId(draft.eligibilityAnswers['selectedEntity'])
          : '—');
      const savedAt = localStorage.getItem('cms_draft_saved_at') || new Date().toISOString();
      return {
        complaintId: 'DRAFT-LOCAL',
        entityName,
        complaintDate: savedAt,
        status: 'DRAFT',
        comments: draft.formData?.['complaintText']?.substring(0, 50) || 'Complaint in progress',
        isDraft: true,
        draftId: 'local'
      };
    } catch {
      return null;
    }
  }

  private getEntityNameFromId(id: string): string {
    const banks: Record<string, string> = {
      '1': 'State Bank of India', '2': 'HDFC Bank', '3': 'ICICI Bank',
      '4': 'Punjab National Bank', '5': 'Bank of Baroda', '6': 'Axis Bank',
      '7': 'Kotak Mahindra Bank', '8': 'ABC Bank'
    };
    return banks[id] || '—';
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

  resumeDraft(record: ComplaintRecord) {
    if (record.draftId === 'local') {
      this.router.navigate(['/public/file-complaint']);
    } else if (record.draftId) {
      this.router.navigate(['/public/file-complaint'], { queryParams: { draftId: record.draftId } });
    }
  }

  deleteDraft(record: ComplaintRecord) {
    if (!record.draftId) return;
    if (record.draftId === 'local') {
      localStorage.removeItem('cms_complaint_draft');
      localStorage.removeItem('cms_draft_saved_at');
      localStorage.removeItem('cms_draft_id');
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
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': return 'status-info-required';
      case 'DRAFT': return 'status-draft';
      default: return 'status-pending';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In Progress';
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Closed';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'DRAFT': return 'Draft';
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
