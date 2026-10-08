import { Component, OnInit, inject, signal, computed, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../../environments/environment';
import { PublicAuthService } from '../../../services/public-auth.service';
import { ComplaintService } from '../../../services/complaint.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { Table, TableModule } from 'primeng/table';
import { Select } from 'primeng/select';
import { DatePicker } from 'primeng/datepicker';
import { Tooltip } from 'primeng/tooltip';
import { FilterService } from 'primeng/api';
import { ComplaintRecord } from '../models';
import { formatComplaintDate, parseComplaintDate, toDayKey } from '../../../utils/complaint-date.util';

@Component({
  selector: 'app-complaint-history',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, TranslatePipe, TableModule, Select, DatePicker, Tooltip],
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
    { label: 'Rejected', value: 'REJECTED' },
    { label: 'Withdrawn', value: 'WITHDRAWN' },
    { label: 'Portal Rejection', value: 'PORTAL_REJECTION' },
    // A status with no filter entry cannot be filtered on at all, so a citizen with only registered
    // complaints or a filed appeal had no way to narrow the list to them.
    { label: 'Complaint Registered', value: 'REGISTERED' },
    { label: 'Appeal Filed', value: 'APPEAL_FILED' }
  ];

  selectedStatus = '';
  dateFilter: Date | null = null;
  closureDateFilter: Date | null = null;

  // At the default 5 rows the table is left unconstrained so no vertical scrollbar
  // appears; larger page sizes get a capped height and scroll inside the card.
  readonly defaultRows = 5;
  rowsPerPage = signal(5);

  scrollHeight = computed(() => this.rowsPerPage() > this.defaultRows ? '440px' : undefined);

  ngOnInit() {
    this.registerDateEqualsFilter();
    this.loadComplaints();
  }

  private registerDateEqualsFilter() {
    this.filterService.register('complaintDateEquals', (value: any, filter: any): boolean => {
      if (!filter) return true;
      return toDayKey(parseComplaintDate(value)) === filter;
    });
  }

  onStatusFilter(value: string) {
    this.selectedStatus = value;
    this.dt.filter(value, 'status', 'equals');
  }

  onDateFilterChange(date: Date | null) {
    this.dateFilter = date;
    this.dt.filter(toDayKey(date) || null, 'complaintDate', 'complaintDateEquals');
  }

  onClosureDateFilterChange(date: Date | null) {
    this.closureDateFilter = date;
    this.dt.filter(toDayKey(date) || null, 'closureDate', 'complaintDateEquals');
  }

  private loadComplaints() {
    const phone = this.authService.userIdentifier();
    if (!phone) {
      this.finalizeLoad([]);
      return;
    }

    // Both lists must resolve before the signal is set. Assigning them independently loses the
    // drafts whenever the drafts response arrives first, because finalizeLoad() overwrites the
    // whole signal — and drafts is consistently the faster of the two.
    forkJoin({
      submitted: this.http
        .get<any>(`${environment.apiBaseUrl}/api/v1/complaints?phone=${phone}`)
        .pipe(catchError(() => of(null))),
      drafts: this.complaintService.getDrafts(phone).pipe(catchError(() => of([])))
    }).subscribe(({ submitted, drafts }) => {
      const data = submitted?.data ?? submitted ?? [];
      const submittedRecords: ComplaintRecord[] = (Array.isArray(data) ? data : []).map((c: any) => ({
        complaintId: c.complaintId || c.id,
        entityName: c.entityName || c.regulatedEntityName || c.complainantName || '—',
        complaintDate: c.complaintDate || c.createdAt || c.registeredDate || '—',
        status: c.status || 'PENDING',
        closureClause: c.closureClause || '',
        closureDate: c.closureDate || c.complaintClosureDate || '',
        appealable: c.appealable === true,
        // The API returns app-relative letter paths, but these are used as anchor hrefs — a plain
        // browser navigation, which does not go through the HttpClient base URL.
        acknowledgementLetterUrl: this.absoluteUrl(c.acknowledgementLetterUrl || c.acknowledgementLetter),
        closureLetterUrl: this.absoluteUrl(c.closureLetterUrl || c.closureLetter)
      }));

      const draftRecords: ComplaintRecord[] = drafts.map(d => ({
        complaintId: d.draftId,
        entityName: d.entityName || '—',
        complaintDate: d.updatedAt || '—',
        status: 'DRAFT',
        isDraft: true,
        draftId: d.draftId
      }));

      this.finalizeLoad([...draftRecords, ...submittedRecords]);
    });
  }

  private finalizeLoad(records: ComplaintRecord[]) {
    records.sort((a, b) => {
      if (a.status === 'DRAFT' && b.status !== 'DRAFT') return -1;
      if (b.status === 'DRAFT' && a.status !== 'DRAFT') return 1;
      return this.toTime(b.complaintDate) - this.toTime(a.complaintDate);
    });
    this.complaints.set(records);
    this.loading.set(false);
  }

  private toTime(value: string): number {
    return parseComplaintDate(value)?.getTime() ?? 0;
  }

  private absoluteUrl(path: string | undefined): string {
    if (!path) return '';
    return /^https?:\/\//.test(path) ? path : `${environment.apiBaseUrl}${path}`;
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
    this.router.navigate(['/public/appeal', record.complaintId]);
  }

  /** UST111: appeal is only offered for a closure clause appealable by the complainant (15(1)(a)/(b)). */
  canFileAppeal(record: ComplaintRecord): boolean {
    return record.appealable === true;
  }

  shareFeedback(record: ComplaintRecord) {
    this.router.navigate(['/public/feedback', record.complaintId]);
  }

  withdrawComplaint(record: ComplaintRecord) {
    this.router.navigate(['/public/withdraw', record.complaintId]);
  }

  isWithdrawable(status: string): boolean {
    return status === 'PENDING' || status === 'IN_PROGRESS' || status === 'INFORMATION_REQUIRED';
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
      case 'CLOSED': case 'NON_MAINTAINABLE': case 'PORTAL_REJECTION': case 'APPROVED': return 'status-closed';
      case 'IN_PROGRESS': return 'status-inprogress';
      case 'INFORMATION_REQUIRED': case 'REJECTED': return 'status-info-required';
      case 'PENDING': return 'status-pending';
      case 'DRAFT': return 'status-draft';
      case 'WITHDRAWN': return 'status-withdrawn';
      default: return 'status-pending';
    }
  }

  /** UST82: Portal Rejection is the actual status code for a Non-Maintainable closure (FR-G-013) — no complaint number was ever issued. */
  getStatusLabel(status: string): string {
    switch (status) {
      case 'IN_PROGRESS': return 'In-Progress';
      case 'CLOSED': case 'NON_MAINTAINABLE': return 'Complaint Closed';
      case 'PORTAL_REJECTION': return 'Portal Rejection';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case 'PENDING': return 'Pending';
      case 'DRAFT': return 'Draft';
      case 'APPROVED': return 'Approved';
      case 'REJECTED': return 'Rejected';
      case 'WITHDRAWN': return 'Withdrawn';
      // REGISTERED is what the filing acknowledgement returns and APPEAL_FILED what an appeal sets;
      // neither had a case, so both fell through to the default and rendered in raw upper snake case.
      case 'REGISTERED': return 'Complaint Registered';
      case 'APPEAL_FILED': return 'Appeal Filed';
      case 'RESOLVED': return 'Resolved';
      case 'ADJUDICATED': return 'Adjudicated';
      case 'CONCILIATED': return 'Conciliated';
      case 'SENT_TO_OTHER': return 'Sent to other Department';
      case 'FORWARDED_EXTERNAL': return 'Sent to other Regulatory Bodies';
      // Title-cases anything unmapped rather than shouting it at the citizen in SCREAMING_SNAKE_CASE.
      default: return status.replace(/_/g, ' ').toLowerCase()
        .replace(/\b\w/g, (ch) => ch.toUpperCase());
    }
  }

  formatDate(dateStr: string | undefined): string {
    return formatComplaintDate(dateStr);
  }
}
