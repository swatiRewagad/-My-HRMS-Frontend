import { Component, OnInit, inject, signal, computed, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { LanguageSelectComponent } from '../../../shared/language-select/language-select.component';
import { FontSizeControlsComponent } from '../../../shared/font-size-controls/font-size-controls.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

interface ReviewDraft {
  draftId: string;
  complaintNumber: string;
  complainantName: string;
  fromEmailId: string;
  subject: string;
  modeOfReceipt: string;
  status: string;
  category: string;
  entityName: string;
  proposedComplaint: string;
  deoDecision: string;
  deoName: string;
  assignedAt: string;
  creationDate: string;
  ageing: number;
  priority: string;
  vernacular: boolean;
}

interface ColumnDef {
  key: string;
  label: string;
  labelKey: string;
  visible: boolean;
}

@Component({
  selector: 'app-reviewer-home',
  standalone: true,
  imports: [CommonModule, FormsModule, NotificationBellComponent, LanguageSelectComponent, FontSizeControlsComponent, TranslatePipe],
  templateUrl: './reviewer-home.component.html',
  styleUrl: './reviewer-home.component.scss'
})
export class ReviewerHomeComponent implements OnInit {

  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  drafts = signal<ReviewDraft[]>([]);
  loading = signal(false);

  filterStatus = signal('');

  // Advanced Search — same fields/behavior as the working implementation in
  // draft-assessment's sibling deo-home.component.ts, which this screen's "Advanced Search"
  // button previously did nothing at all (no click handler, no dialog).
  showAdvancedSearch = signal(false);
  advSearchActive = signal(false);
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', mobileNumber: '', email: '',
    fromEmailId: '', modeOfReceipt: '', entityName: '',
    subject: '', ndiContactPerson: '', category: ''
  };

  applyAdvancedSearch() {
    this.searchText.set('');
    this.advSearchActive.set(true);
    this.showAdvancedSearch.set(false);
  }

  clearAdvancedSearch() {
    this.advSearch = {
      complaintNumber: '', complaintId: '', statusCode: '',
      complainantName: '', mobileNumber: '', email: '',
      fromEmailId: '', modeOfReceipt: '', entityName: '',
      subject: '', ndiContactPerson: '', category: ''
    };
    this.advSearchActive.set(false);
  }
  filterDeoDecision = signal('');
  searchText = signal('');
  sidebarCollapsed = signal(false);
  assignmentFilter = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');

  // Pagination
  currentPage = signal(1);

  // Switching tabs/filters never reset currentPage — a user on page 2+ of a larger list would
  // then see an empty table after filtering down to fewer items (or even a non-empty
  // filteredDrafts()/stats() count), because paginatedDrafts() slices a page range that no
  // longer exists. Reset to page 1 whenever any filter criteria changes.
  private resetPageOnFilterChange = effect(() => {
    this.filterStatus();
    this.assignmentFilter();
    this.advSearchActive();
    this.searchText();
    this.currentPage.set(1);
  });
  pageSize = 10;

  // Sort
  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  // Column filters
  columnFilters = signal<Record<string, string>>({});

  setColumnFilter(key: string, value: string) {
    this.columnFilters.update(f => ({ ...f, [key]: value }));
  }

  // Columns
  columns: ColumnDef[] = [
    { key: 'draftId', label: 'Complaint Id', labelKey: 'officer.search.complaint_id', visible: true },
    { key: 'complaintNumber', label: 'Complaint Number', labelKey: 'officer.search.complaint_number', visible: true },
    { key: 'fromEmailId', label: 'From', labelKey: 'officer.column.from', visible: true },
    { key: 'ageing', label: 'Pending...', labelKey: 'officer.stat.pending', visible: true },
    { key: 'modeOfReceipt', label: 'Mode', labelKey: 'officer.column.mode', visible: true },
    { key: 'complainantName', label: 'Complainant Name', labelKey: 'officer.search.complainant_name', visible: true },
    { key: 'status', label: 'Status', labelKey: 'officer.column.status', visible: true },
    { key: 'entityName', label: 'Entity Name', labelKey: 'officer.search.entity_name', visible: true },
    { key: 'proposedComplaint', label: 'Proposed Com...', labelKey: 'officer.column.proposed_complaint', visible: true },
    { key: 'creationDate', label: 'Creation Date', labelKey: 'officer.column.creation_date', visible: true },
  ];

  visibleColumns = computed(() => this.columns.filter(c => c.visible));

  stats = computed(() => {
    const all = this.drafts();
    return {
      total: all.length,
      pending: all.filter(d => this.matchesStatusGroup(d.status, 'SENT_TO_REVIEWER')).length,
      approved: all.filter(d => this.matchesStatusGroup(d.status, 'APPROVED')).length,
      sentBack: all.filter(d => this.matchesStatusGroup(d.status, 'SENT_BACK')).length,
      closedNm: all.filter(d => this.matchesStatusGroup(d.status, 'CLOSED_NM')).length,
      inProgress: all.filter(d => this.matchesStatusGroup(d.status, 'IN_PROGRESS')).length,
      // Direct/AGR/RBI-Domain/CC-BCC describe how a complaint was RECEIVED (intake channel),
      // not its workflow status — the template previously bound these labels to the
      // status-based counts above (e.g. "Complaints via AGR" showed the approved-count), which
      // is a different, unrelated metric that only coincidentally isn't always zero. There's no
      // channel classification in the data model yet (mode_of_receipt only distinguishes
      // EMAIL/PHYSICAL_LETTER, nothing AGR/RBI-domain/CC-BCC specific), so these report 0
      // honestly rather than a wrong non-zero number, until that classification exists.
      direct: 0,
      viaAgr: 0,
      viaRbiDomain: 0,
      ccBcc: 0,
    };
  });

  // Single source of truth for "what counts as X" — the real workflow has many more granular
  // statuses (APPROVED_ROUTED, APPROVED_SENT_TO_OTHER_DEPT, APPROVED_VERNACULAR, ...) than the
  // 5 named tabs/status-code options shown to the user. Both the tabs and the Advanced Search
  // "Status Code" dropdown need to group by these same buckets, not do a naive exact-string
  // match against a raw status — that was exactly why picking "Approved" in Advanced Search
  // found nothing (real status is APPROVED_ROUTED, never the literal string "APPROVED").
  private statusGroups: Record<string, string[]> = {
    SENT_TO_REVIEWER: ['SENT_TO_REVIEWER', 'SENT_TO_OTHER_DEPT_FOR_APPROVAL', 'VERNACULAR_FOR_APPROVAL'],
    APPROVED: ['APPROVED', 'APPROVED_ROUTED', 'APPROVED_SENT_TO_OTHER_DEPT', 'APPROVED_VERNACULAR'],
    SENT_BACK: ['SENT_BACK_TO_DEO', 'SENT_BACK'],
    CLOSED_NM: ['CLOSED_NM', 'CLOSED_NOT_A_COMPLAINT'],
    DRAFT: ['DRAFT'],
  };

  private matchesStatusGroup(actualStatus: string, groupCode: string): boolean {
    if (groupCode === 'IN_PROGRESS') {
      const namedElsewhere = Object.values(this.statusGroups).flat()
        .concat(['SENT_TO_OTHER_DEPT_FOR_APPROVAL', 'VERNACULAR_FOR_APPROVAL']);
      return !namedElsewhere.includes(actualStatus);
    }
    const group = this.statusGroups[groupCode];
    return group ? group.includes(actualStatus) : actualStatus === groupCode;
  }

  filteredDrafts = computed(() => {
    let result = this.drafts();
    const status = this.filterStatus();
    const search = this.searchText();

    if (status) {
      result = result.filter(d => this.matchesStatusGroup(d.status, status));
    }

    if (this.advSearchActive()) {
      const q = this.advSearch;
      if (q.complaintNumber) result = result.filter(d => d.complaintNumber.toLowerCase().includes(q.complaintNumber.toLowerCase()));
      if (q.complaintId) result = result.filter(d => d.draftId.toLowerCase().includes(q.complaintId.toLowerCase()));
      if (q.statusCode) result = result.filter(d => this.matchesStatusGroup(d.status, q.statusCode));
      if (q.complainantName) result = result.filter(d => d.complainantName.toLowerCase().includes(q.complainantName.toLowerCase()));
      if (q.mobileNumber) result = result.filter(d => d.fromEmailId.includes(q.mobileNumber));
      if (q.email) result = result.filter(d => d.fromEmailId.toLowerCase().includes(q.email.toLowerCase()));
      if (q.fromEmailId) result = result.filter(d => d.fromEmailId.toLowerCase().includes(q.fromEmailId.toLowerCase()));
      if (q.modeOfReceipt) result = result.filter(d => d.modeOfReceipt === q.modeOfReceipt);
      if (q.entityName) result = result.filter(d => d.entityName.toLowerCase().includes(q.entityName.toLowerCase()));
      if (q.subject) result = result.filter(d => d.subject.toLowerCase().includes(q.subject.toLowerCase()));
      if (q.category) result = result.filter(d => d.category === q.category);
    } else if (search) {
      const q = search.toLowerCase();
      result = result.filter(d =>
        d.draftId.toLowerCase().includes(q) ||
        d.complainantName.toLowerCase().includes(q) ||
        d.subject.toLowerCase().includes(q) ||
        d.fromEmailId.toLowerCase().includes(q)
      );
    }

    // Column filters
    const columnFilters = this.columnFilters();
    for (const key of Object.keys(columnFilters)) {
      const val = columnFilters[key]?.toLowerCase();
      if (val) {
        result = result.filter(d => {
          const cellVal = String((d as any)[key] || '').toLowerCase();
          return cellVal.includes(val);
        });
      }
    }

    return result;
  });

  paginatedDrafts = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filteredDrafts().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredDrafts().length / this.pageSize)));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];
    const start = Math.max(1, current - 2);
    const end = Math.min(total, start + 4);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  });

  paginationStart = computed(() => {
    if (this.filteredDrafts().length === 0) return 0;
    return (this.currentPage() - 1) * this.pageSize + 1;
  });

  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize, this.filteredDrafts().length));

  loggedInUser: { id: string; name: string; role: string } | null = null;

  // effect() re-runs whenever auth.currentUser() changes — including the moment it first
  // resolves after a browser refresh, when Keycloak hasn't finished re-initializing yet at
  // ngOnInit time. Without this, the very first loadDrafts() call could fire with no username
  // and (per the bug documented in loadDrafts) leak every reviewer's queue into this one's view.
  private syncUserEffect = effect(() => {
    const user = this.auth.currentUser();
    if (!user) return;

    const stored = sessionStorage.getItem('crpc_user');
    const parsed = stored ? JSON.parse(stored) : null;
    // A cached crpc_user surviving from a different account in the same browser tab (e.g.
    // switching test logins without a full logout) would silently keep filtering "assigned to
    // me" by the wrong username forever — only trust the cache when it matches who's actually
    // logged in right now.
    if (parsed && parsed.id === user.username) {
      this.loggedInUser = parsed;
    } else {
      const role = this.auth.getRoles().find(r => ['REVIEWER', 'CRPC_HEAD', 'DEO'].includes(r)) || 'REVIEWER';
      this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role };
      sessionStorage.setItem('crpc_user', JSON.stringify(this.loggedInUser));
    }
    this.loadDrafts();
  });

  ngOnInit() {
    this.loadDrafts();
  }

  logout() {
    sessionStorage.removeItem('crpc_user');
    this.auth.logout();
  }

  loadDrafts() {
    const username = this.loggedInUser?.id || this.auth.currentUser()?.username || '';

    // The backend treats an empty (but present) assignedTo param as "no filter" and returns
    // every complaint — see EmailSyndicationApiController: `assignedTo != null &&
    // !assignedTo.isEmpty()` gates the filter, so "" silently falls through to unfiltered. On a
    // browser refresh Keycloak re-initializes asynchronously and loggedInUser/currentUser can
    // still be empty the instant this fires, which was leaking every reviewer's entire queue
    // into "Assigned to Me". Skip the request entirely rather than risk sending that.
    if (this.assignmentFilter() === 'ASSIGNED_TO_ME' && !username) {
      this.drafts.set([]);
      this.loading.set(false);
      return;
    }

    this.loading.set(true);
    const params: any = {};
    if (this.assignmentFilter() === 'ASSIGNED_TO_ME') {
      params.assignedTo = username;
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/queue`, { params }).subscribe({
      next: (res) => {
        const queueDrafts = (res?.data || []).map((d: any) => this.mapToDraft(d));
        this.drafts.set(queueDrafts);
        this.loading.set(false);
      },
      error: () => {
        this.drafts.set([]);
        this.loading.set(false);
      }
    });
  }

  onAssignmentFilterChange(value: string) {
    this.assignmentFilter.set(value as 'ASSIGNED_TO_ME' | 'ALL');
    this.loadDrafts();
  }

  private mapToDraft(d: any): ReviewDraft {
    const hours = (Date.now() - new Date(d.receivedAt || d.createdAt).getTime()) / 3600000;
    return {
      draftId: d.draftId || '',
      complaintNumber: d.convertedComplaintId || d.complaintNumber || d.draftId || '',
      complainantName: d.complainantName || '',
      fromEmailId: d.senderEmail || '',
      subject: d.subject || '',
      modeOfReceipt: d.modeOfReceipt || 'EMAIL',
      status: d.status || 'SENT_TO_REVIEWER',
      category: d.category || 'GENERAL',
      entityName: d.entityName || '',
      proposedComplaint: d.category || '',
      deoDecision: d.deoDecision || 'MAINTAINABLE',
      deoName: d.processedBy || d.assignedTo || '',
      assignedAt: d.createdAt || new Date().toISOString(),
      creationDate: d.createdAt ? d.createdAt.split('T')[0] : new Date().toISOString().split('T')[0],
      ageing: Math.max(0, Math.floor(hours / 24)),
      priority: hours > 48 ? 'HIGH' : hours > 24 ? 'MEDIUM' : 'LOW',
      vernacular: d.isVernacular || false,
    };
  }

  sortBy(col: string) {
    if (this.sortColumn === col) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = col;
      this.sortDirection = 'asc';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'SENT_TO_REVIEWER': return 'Sent to Reviewer';
      case 'SENT_TO_OTHER_DEPT_FOR_APPROVAL': return 'Other Dept - Pending Approval';
      case 'VERNACULAR_FOR_APPROVAL': return 'Vernacular - Pending Approval';
      case 'APPROVED': case 'APPROVED_ROUTED': return 'Approved';
      case 'APPROVED_SENT_TO_OTHER_DEPT': return 'Sent to Other Entity';
      case 'APPROVED_VERNACULAR': return 'Sent to Language Office';
      case 'SENT_BACK_TO_DEO': case 'SENT_BACK': return 'Sent Back';
      case 'CLOSED_NM': case 'CLOSED_NOT_A_COMPLAINT': return 'Closed (NM)';
      default: return status;
    }
  }

  getCellValue(draft: ReviewDraft, key: string): string {
    return String((draft as any)[key] || '—');
  }

  openDraft(draftId: string) {
    this.router.navigate(['/crpc/reviewer/draft', draftId]);
  }

  navigateTo(route: string) {
    this.router.navigate([route]);
  }
}
