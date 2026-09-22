import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { LanguageSelectComponent } from '../../../shared/language-select/language-select.component';
import { FontSizeControlsComponent } from '../../../shared/font-size-controls/font-size-controls.component';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

interface RbioComplaint {
  complaintId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmail: string;
  subject: string;
  modeOfReceipt: string;
  status: string;
  category: string;
  entityName: string;
  priority: string;
  assignedTo: string;
  createdAt: string;
  slaDueDate: string;
  slaBreachDays: number;
  description: string;
  hasAttachments: boolean;
}

@Component({
  selector: 'app-rbio-home',
  standalone: true,
  imports: [CommonModule, FormsModule, NotificationBellComponent, SessionTimeoutComponent, LanguageSelectComponent, FontSizeControlsComponent, TranslatePipe],
  templateUrl: './rbio-home.component.html',
  styleUrl: './rbio-home.component.scss'
})
export class RbioHomeComponent implements OnInit {

  router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaints = signal<RbioComplaint[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  filterStatus = signal('');
  // SLA breach is a cross-cutting, time-based filter (not a status value), so it needs
  // its own toggle rather than being folded into filterStatus.
  filterSlaBreached = signal(false);
  filterQueue = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');
  searchText = signal('');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
  // Both must be signals, not plain properties - computed() only re-runs when a signal it
  // read changes, so mutating a plain object/string in place never triggered a refilter.
  columnFilters = signal<Record<string, string>>({});
  columnSearchText = signal('');

  setColumnFilter(key: string, value: string) {
    this.columnFilters.update(f => ({ ...f, [key]: value }));
  }

  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  currentPage = signal(1);
  pageSize = 10;

  showColumnConfig = signal(false);
  showAdvancedSearch = signal(false);
  showCreateDropdown = signal(false);

  advSearchActive = signal(false);
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', mobileNumber: '', email: '',
    fromEmailId: '', modeOfReceipt: '', entityName: '',
    subject: '', category: ''
  };

  allColumns = signal([
    { key: 'complaintId', label: 'Complaint Id', labelKey: 'officer.search.complaint_id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', labelKey: 'officer.search.complaint_number', visible: true },
    { key: 'assignedTo', label: 'Assigned To', labelKey: 'officer.column.assigned_to', visible: true },
    { key: 'slaBreachDays', label: 'SLA Breach In', labelKey: 'officer.column.sla_breach_in', visible: true },
    { key: 'modeOfReceipt', label: 'Mode', labelKey: 'officer.column.mode', visible: true },
    { key: 'complainantName', label: 'Complainant Name', labelKey: 'officer.search.complainant_name', visible: true },
    { key: 'status', label: 'Status', labelKey: 'officer.column.status', visible: true },
    { key: 'entityName', label: 'Entity Name', labelKey: 'officer.search.entity_name', visible: true },
    { key: 'category', label: 'Complaint Category', labelKey: 'officer.column.complaint_category', visible: true },
    { key: 'createdAt', label: 'Creation Date', labelKey: 'officer.column.creation_date', visible: true },
    { key: 'fromEmail', label: 'From', labelKey: 'officer.column.from', visible: false },
    { key: 'subject', label: 'Subject', labelKey: 'officer.search.subject', visible: false },
    { key: 'priority', label: 'Priority', labelKey: 'officer.column.priority', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  filteredColumns = computed(() => {
    const text = this.columnSearchText();
    if (!text) return this.allColumns();
    const q = text.toLowerCase();
    return this.allColumns().filter(c => c.label.toLowerCase().includes(q));
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();
    const status = this.filterStatus();
    if (status) result = result.filter(d => d.status === status);
    if (this.filterSlaBreached()) result = result.filter(d => d.slaBreachDays < 0);

    if (this.advSearchActive()) {
      const q = this.advSearch;
      if (q.complaintNumber) result = result.filter(d => d.complaintNumber?.toLowerCase().includes(q.complaintNumber.toLowerCase()));
      if (q.complaintId) result = result.filter(d => d.complaintId.toLowerCase().includes(q.complaintId.toLowerCase()));
      if (q.statusCode) result = result.filter(d => d.status === q.statusCode);
      if (q.complainantName) result = result.filter(d => d.complainantName?.toLowerCase().includes(q.complainantName.toLowerCase()));
      if (q.email) result = result.filter(d => d.fromEmail?.toLowerCase().includes(q.email.toLowerCase()));
      if (q.entityName) result = result.filter(d => d.entityName?.toLowerCase().includes(q.entityName.toLowerCase()));
      if (q.subject) result = result.filter(d => d.subject?.toLowerCase().includes(q.subject.toLowerCase()));
      if (q.category) result = result.filter(d => d.category === q.category);
    }

    if (this.filterWithoutAttachments()) result = result.filter(d => !d.hasAttachments);

    for (const [key, val] of Object.entries(this.columnFilters())) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(d => String((d as any)[key] || '').toLowerCase().includes(q));
      }
    }

    if (this.filterUnread()) {
      result = result.filter(d => !this.visitedIds().has(d.complaintId));
    }

    if (this.sortColumn) {
      result = [...result].sort((a, b) => {
        const av = (a as any)[this.sortColumn] || '';
        const bv = (b as any)[this.sortColumn] || '';
        const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true });
        return this.sortDirection === 'asc' ? cmp : -cmp;
      });
    }
    return result;
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredComplaints().length / this.pageSize)));

  paginatedComplaints = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredComplaints().slice(start, start + this.pageSize);
  });

  paginationStart = computed(() => this.filteredComplaints().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize + 1);
  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredComplaints().length));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];
    const start = Math.max(1, current - 2);
    const end = Math.min(total, current + 2);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  });

  stats = computed(() => {
    const all = this.complaints();
    return {
      totalPending: all.length,
      pendingWithMe: all.filter(d => d.status === 'NEW' || d.status === 'ASSIGNED' || d.status === 'IN_PROGRESS').length,
      pendingWithRE: all.filter(d => d.status === 'AWAITING_RESPONSE').length,
      pendingMeeting: all.filter(d => d.status === 'MEETING_SCHEDULED').length,
      // slaBreachDays is now signed days-remaining-until-due (negative = already overdue),
      // so "breached" means the value has gone negative, not positive as before.
      slaBreach: all.filter(d => d.slaBreachDays < 0).length,
      slaBreached0to15: all.filter(d => d.slaBreachDays < 0 && d.slaBreachDays >= -15).length,
      slaBreached16to30: all.filter(d => d.slaBreachDays < -15 && d.slaBreachDays >= -30).length,
      // No backend status exists yet for these two - honestly 0 until that workflow stage exists.
      responseFromRE: all.filter(d => d.status === 'RESPONSE_FROM_RE').length,
      withdrawn: all.filter(d => d.status === 'WITHDRAWN').length,
      draft: all.filter(d => d.status === 'DRAFT' || d.status === 'NEW').length,
      meetingScheduled: all.filter(d => d.status === 'MEETING_SCHEDULED').length,
      sentBack: all.filter(d => d.status === 'SENT_BACK').length,
      assessmentComplete: all.filter(d => d.status === 'ASSESSMENT_COMPLETE').length,
    };
  });

  loggedInUser: { id: string; name: string; role: string } | null = null;

  ngOnInit() {
    try {
      const visited = localStorage.getItem('rbio_visitedComplaintIds');
      if (visited) this.visitedIds.set(new Set(JSON.parse(visited)));
    } catch {}

    const stored = sessionStorage.getItem('rbio_user');
    if (stored) {
      this.loggedInUser = JSON.parse(stored);
    } else {
      const user = this.auth.currentUser();
      if (user) {
        const role = this.auth.getRoles().find(r =>
          ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_ADJUDICATOR', 'RBIO_CONCILIATOR'].includes(r)
        ) || 'RBIO_OFFICER';
        this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
        sessionStorage.setItem('rbio_user', JSON.stringify(this.loggedInUser));
      }
    }

    this.loadComplaints();
  }

  logout() {
    sessionStorage.removeItem('rbio_user');
    this.auth.logout();
  }

  loadComplaints() {
    this.loading.set(true);
    const username = this.loggedInUser?.id || '';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/rbio/all-tasks?officer=${username}`).subscribe({
      next: (res) => {
        const items = (res.data || res || []).map((c: any) => this.mapComplaint(c));
        this.complaints.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  private mapComplaint(c: any): RbioComplaint {
    const slaDue = c.slaDueDate ? new Date(c.slaDueDate) : new Date();
    const now = new Date();
    // Signed days remaining until the SLA due date - negative once it's overdue.
    const daysRemaining = Math.ceil((slaDue.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
    const id = c.complaintId || c.complaintNumber || '';
    const cachedStatus = sessionStorage.getItem(`rbio_status_${id}`);
    return {
      complaintId: id,
      complaintNumber: c.complaintNumber || '',
      complainantName: c.complainantName || '',
      fromEmail: c.complainantEmail || c.fromEmail || '',
      subject: c.subject || '',
      modeOfReceipt: c.modeOfReceipt || c.filingType || 'Email',
      status: cachedStatus || c.status || 'DRAFT',
      category: c.category || 'General',
      entityName: c.entityName || '',
      priority: c.priority || 'MEDIUM',
      assignedTo: c.assignedTo || c.assignedOfficer || '',
      createdAt: c.createdAt || c.assignedAt || '',
      slaDueDate: c.slaDueDate || '',
      slaBreachDays: daysRemaining,
      description: c.description || '',
      hasAttachments: !!c.hasAttachments,
    };
  }

  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  openComplaint(complaintId: string) {
    this.visitedIds.update(ids => {
      const s = new Set(ids);
      s.add(complaintId);
      localStorage.setItem('rbio_visitedComplaintIds', JSON.stringify([...s]));
      return s;
    });
    this.router.navigate(['/rbio/complaint', complaintId]);
  }

  navigateToCreateComplaint() {
    this.router.navigate(['/rbio/create-complaint']);
  }

  toggleSelect(id: string) {
    const ids = new Set(this.selectedIds());
    if (ids.has(id)) ids.delete(id);
    else ids.add(id);
    this.selectedIds.set(ids);
  }

  toggleSelectAll() {
    const filtered = this.filteredComplaints();
    if (this.selectedIds().size === filtered.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(filtered.map(d => d.complaintId)));
    }
  }

  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  applyAdvancedSearch() {
    this.searchText.set('');
    this.advSearchActive.set(true);
    this.currentPage.set(1);
    this.showAdvancedSearch.set(false);
  }

  clearAdvancedSearch() {
    this.advSearch = {
      complaintNumber: '', complaintId: '', statusCode: '',
      complainantName: '', mobileNumber: '', email: '',
      fromEmailId: '', modeOfReceipt: '', entityName: '',
      subject: '', category: ''
    };
    this.advSearchActive.set(false);
  }

  changePageSize(size: number) {
    this.pageSize = size;
    this.currentPage.set(1);
  }

  getStatusLabel(status: string): string {
    const map: Record<string, string> = {
      'DRAFT': 'New Complaint', 'NEW': 'New Complaint', 'IN_PROGRESS': 'In Progress',
      'SENT_BACK': 'Sent Back to DO', 'MEETING_SCHEDULED': 'Meeting Scheduled',
      'ASSESSMENT_COMPLETE': 'Assessment Complete', 'AWAITING_RESPONSE': 'Pending',
      'SENT_TO_REVIEWER': 'Sent to Reviewer',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'Sent to Deputy Ombudsman',
      'SENT_TO_OMBUDSMAN': 'Sent to Ombudsman',
      'REVIEWER_REVIEW': 'Reviewer Review',
      'DEPUTY_REVIEW': 'Deputy Ombudsman Review',
      'CLOSED': 'Closed', 'RESOLVED': 'Resolved',
      'INFORMATION_REQUIRED': 'Information Required',
      'MEETING_COMPLETED': 'Meeting Completed',
      'AWARD_PASSED': 'Award Passed',
    };
    return map[status] || status;
  }

  getStatusIcon(status: string): string {
    const map: Record<string, string> = {
      'DRAFT': 'pi-send', 'NEW': 'pi-send',
      'SENT_BACK': 'pi-reply',
      'AWAITING_RESPONSE': 'pi-arrow-up-right',
      'MEETING_SCHEDULED': 'pi-calendar',
      'ASSESSMENT_COMPLETE': 'pi-verified',
    };
    return map[status] || 'pi-circle-fill';
  }

  getCellValue(complaint: RbioComplaint, key: string): string {
    const val = (complaint as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'createdAt') return val ? new Date(val).toLocaleDateString('en-IN') : '—';
    return String(val);
  }

  // days is signed days-remaining-until-due (negative = already overdue). Close to the
  // deadline either way, switch to an hours display to match the "5 Hrs" / "48 Hrs" style.
  getSlaLabel(days: number): string {
    if (Math.abs(days) < 2) {
      const hours = Math.round(days * 24);
      return `${hours} Hrs`;
    }
    return `${days} Days`;
  }

  // Pure SLA-breach severity for the "SLA Breach In" pill itself: red = already overdue
  // or breaching within 24h, orange = breaching within 3 days, green = plenty of time left.
  getSlaClass(days: number): string {
    if (days < 1) return 'sla-red';
    if (days <= 3) return 'sla-orange';
    return 'sla-green';
  }

  // Row left-border: a couple of statuses always get a fixed color regardless of SLA
  // (a sent-back or withdrawn item needs attention/awareness independent of its due date),
  // everything else follows the same severity as getSlaClass above.
  getRowClass(days: number, status?: string): string {
    if (status === 'SENT_BACK' || status === 'SENT_BACK_TO_DEO') return 'sla-yellow';
    if (status === 'WITHDRAWN' || status === 'COMPLAINT_WITHDRAWN') return 'sla-pink';
    return this.getSlaClass(days);
  }

  dragIndex: number | null = null;
  dragOverIndex: number | null = null;

  onColumnDragStart(index: number) { this.dragIndex = index; }
  onColumnDragOver(event: DragEvent, index: number) { event.preventDefault(); this.dragOverIndex = index; }
  onColumnDrop(index: number) {
    if (this.dragIndex !== null && this.dragIndex !== index) {
      this.allColumns.update(cols => {
        const updated = [...cols];
        const [item] = updated.splice(this.dragIndex!, 1);
        updated.splice(index, 0, item);
        return updated;
      });
    }
    this.dragIndex = null;
    this.dragOverIndex = null;
  }
  onColumnDragEnd() { this.dragIndex = null; this.dragOverIndex = null; }
}
