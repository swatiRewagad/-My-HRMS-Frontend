import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../services/translation.service';
import { RbioStatusFilterService, RbioStatusFilter } from '../../../services/rbio-status-filter.service';
import { RbioRowBandService } from '../../../services/rbio-row-band.service';
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
  milestone: string;
  workflowStage: string;
  officeCode: string;
  crpcReceivedAt: string;
  reResponseOverdue: boolean;
}

/** Page sizes offered. UST435 caps a page at 100 rows. */
const PAGE_SIZES = [10, 25, 50, 100];

/** Session key for UST438 — filter selection survives navigation but not logout. */
const FILTER_STATE_KEY = 'rbio_home_filter_state';

@Component({
  selector: 'app-rbio-home',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, NotificationBellComponent, SessionTimeoutComponent],
  templateUrl: './rbio-home.component.html',
  styleUrl: './rbio-home.component.scss'
})
export class RbioHomeComponent implements OnInit {

  router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private statusFilterService = inject(RbioStatusFilterService);
  private bandService = inject(RbioRowBandService);
  private translation = inject(TranslationService);

  complaints = signal<RbioComplaint[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  // ── Filter state ──
  //
  // EVERY piece of filter, sort and paging state below is a signal. A computed() only re-evaluates when
  // a SIGNAL it read changes; reading a plain class field registers no dependency at all. Five of these
  // were plain fields, which is why the per-column search boxes, the column sorting and the page-size
  // selector did nothing whatsoever — the values changed and no view ever recomputed.
  filterStatus = signal('');
  filterQueue = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');
  filterUnread = signal(false);
  // filterWithoutAttachments is deliberately gone: it was bound in the template but no filter ever read
  // it, and the list response carries no attachment count to filter on.
  columnFilters = signal<Record<string, string>>({});
  columnSearchText = signal('');
  sortColumn = signal('createdAt');
  sortDirection = signal<'asc' | 'desc'>('desc');

  currentPage = signal(1);
  pageSize = signal(25);
  pageSizes = PAGE_SIZES;

  // Server-reported totals. The grid used to page 20 fetched rows locally at 10 per page and then report
  // "Showing 1 to 10 of 20 entries" — a confident count of a window, not of the result set.
  totalElements = signal(0);
  totalPages = signal(1);

  showColumnConfig = signal(false);
  showAdvancedSearch = signal(false);
  showCreateDropdown = signal(false);

  // ── Per-role status filters (UST426-433) ──
  //
  // Driven entirely by RBIO_STATUS_MASTER via /api/v1/rbio/status-filters. The five hardcoded <select>
  // option lists this screen used to carry disagreed with each other AND with the database: four of the
  // codes offered (DRAFT, SENT_BACK, ASSESSMENT_COMPLETE, AWAITING_RESPONSE) do not exist in
  // RBIO_STATUS_MASTER at all, so selecting them hit the server's unknown-code branch and silently
  // returned zero rows.
  statusFilters = signal<RbioStatusFilter[]>([]);
  filterLoadError = signal<string | null>(null);

  /** UST433: a status outside the caller's role list must not even be selectable. */
  private allowedStatusCodes = computed(() => new Set(this.statusFilters().map(f => f.statusCode)));

  /** The subset shown as tabs — scopes and queues first, then the role's statuses, in DISPLAY_ORDER. */
  statusTabs = computed(() => this.statusFilters());

  advSearchActive = signal(false);
  advSearch = signal<Record<string, string>>(this.emptyAdvSearch());

  /** UST442: the criteria that produced the current empty result, preserved so the user can adjust. */
  lastSearchSummary = signal<string>('');

  allColumns = signal([
    { key: 'complaintId', label: 'rbio.grid.col_complaint_id', visible: true },
    { key: 'complaintNumber', label: 'rbio.grid.col_complaint_number', visible: true },
    { key: 'fromEmail', label: 'rbio.grid.col_from', visible: true },
    { key: 'slaBreachDays', label: 'rbio.grid.col_sla_breach_in', visible: true },
    { key: 'modeOfReceipt', label: 'rbio.grid.col_mode', visible: true },
    { key: 'complainantName', label: 'rbio.grid.col_complainant_name', visible: true },
    { key: 'status', label: 'rbio.grid.col_status', visible: true },
    { key: 'entityName', label: 'rbio.grid.col_entity_name', visible: true },
    { key: 'category', label: 'rbio.grid.col_category', visible: true },
    { key: 'createdAt', label: 'rbio.grid.col_creation_date', visible: true },
    { key: 'subject', label: 'rbio.grid.col_subject', visible: false },
    { key: 'priority', label: 'rbio.grid.col_priority', visible: false },
    { key: 'assignedTo', label: 'rbio.grid.col_assigned_to', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  /**
   * Columns matching the picker's search box.
   *
   * Matches on the TRANSLATED label, not the translation key. `label` now holds a key like
   * `rbio.grid.col_status`, so filtering on the raw key would make typing "Status" match by coincidence
   * and typing a Hindi label match nothing at all.
   */
  filteredColumns = computed(() => {
    const q = this.columnSearchText().toLowerCase();
    if (!q) return this.allColumns();
    return this.allColumns().filter(c => this.translation.translate(c.label).toLowerCase().includes(q));
  });

  /**
   * Client-side REFINEMENT of the rows on the current server page.
   *
   * Status, advance search, sorting and paging are all decided by the server now. What remains here are
   * the per-column quick filters and the unread toggle, which have no server parameter. That makes them a
   * refinement of the fetched page and nothing more, which is why {@link refinementActive} exists: the
   * footer must not report a refined count against a server-wide total as though the two were comparable.
   */
  displayedComplaints = computed(() => {
    let result = this.complaints();

    for (const [key, val] of Object.entries(this.columnFilters())) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(d => String((d as any)[key] ?? '').toLowerCase().includes(q));
      }
    }

    if (this.filterUnread()) {
      result = result.filter(d => !this.visitedIds().has(d.complaintId));
    }

    return result;
  });

  /** True when a page-local filter is narrowing what the server sent. */
  refinementActive = computed(() =>
    this.filterUnread() || Object.values(this.columnFilters()).some(v => !!v));

  paginationStart = computed(() =>
    this.totalElements() === 0 ? 0 : (this.currentPage() - 1) * this.pageSize() + 1);

  paginationEnd = computed(() =>
    Math.min(this.currentPage() * this.pageSize(), this.totalElements()));

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    const pages: number[] = [];
    const start = Math.max(1, current - 2);
    const end = Math.min(total, current + 2);
    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  });

  /**
   * Counts shown on the cards and tabs.
   *
   * These describe the CURRENT PAGE, not the whole queue, because the server does not return per-status
   * totals. They are labelled accordingly in the template — a card that looks like a queue total but
   * counts one page is the kind of confidently wrong number this screen was full of.
   */
  stats = computed(() => {
    const all = this.complaints();
    const isOpen = (s: string) => ['new', 'pending', 'assigned', 'in_progress'].includes(s?.toLowerCase());
    return {
      totalPending: this.totalElements(),
      pendingWithMe: all.filter(d => isOpen(d.status)).length,
      pendingContactPerson: all.filter(d => d.status?.toLowerCase() === 'info_requested').length,
      pendingMeeting: all.filter(d => d.milestone === 'MEETING_SCHEDULED').length,
      slaBreach: all.filter(d => d.slaBreachDays > 0).length,
      slaOverdue: all.filter(d => d.reResponseOverdue).length,
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
        // The role is read from the token, and the RBIO rank roles are checked BEFORE the legacy ones:
        // a Dealing Official who also holds legacy RBIO_OFFICER must get the Dealing Official filter
        // list, not the officer one. The previous order could not see the four rank roles at all.
        const roles = this.auth.getRoles();
        const role = this.statusFilterService.primaryRbioRole(roles);
        this.loggedInUser = {
          id: user.username,
          name: `${user.firstName} ${user.lastName}`.trim() || user.username,
          role
        };
        sessionStorage.setItem('rbio_user', JSON.stringify(this.loggedInUser));
      }
    }

    this.restoreFilterState();
    this.loadStatusFilters();
  }

  logout() {
    // UST438: the filter selection resets on logout, which is why it lives in sessionStorage and is
    // cleared here rather than being left for the next user of this browser.
    sessionStorage.removeItem(FILTER_STATE_KEY);
    sessionStorage.removeItem('rbio_user');
    this.auth.logout();
  }

  loadError = signal<string | null>(null);

  /**
   * Fetches the caller's permitted status filters, then loads the grid.
   *
   * Fails CLOSED: if the filter list cannot be loaded, no status filter is offered and the error is
   * shown. Falling back to a compiled-in list is what produced the four phantom status codes — a
   * hardcoded copy of a database-owned vocabulary drifts and then lies.
   */
  private loadStatusFilters() {
    this.statusFilterService.filtersFor(this.loggedInUser?.role).subscribe({
      next: (filters) => {
        this.statusFilters.set(filters);
        this.filterLoadError.set(null);

        // Land on the role's default tab unless the session already had a choice (UST438), and drop a
        // restored status the role may no longer hold (UST433).
        const current = this.filterStatus();
        if (current && !this.allowedStatusCodes().has(current)) {
          this.filterStatus.set('');
        }
        if (!this.filterStatus()) {
          this.filterStatus.set(filters.find(f => f.isDefault)?.statusCode ?? '');
        }
        this.loadComplaints();
      },
      error: () => {
        this.statusFilters.set([]);
        this.filterLoadError.set('rbio.grid.error_filters_unavailable');
        // The grid still loads unfiltered: the officer can work, they just cannot filter by status.
        this.loadComplaints();
      }
    });
  }

  loadComplaints() {
    this.loading.set(true);
    this.loadError.set(null);

    let params = new HttpParams()
      .set('page', String(this.currentPage() - 1))   // the API is 0-based; the UI is 1-based
      .set('size', String(this.pageSize()))
      .set('sortBy', this.sortColumn())
      .set('sortDir', this.sortDirection());

    // UST427: the Dealing Official's "Complaints Assigned to Me" view. ASSIGNED_TO_ME is a SCOPE filter
    // in RBIO_STATUS_MASTER resolved server-side against the caller's own id — the client does not get to
    // say whose queue it is looking at.
    const status = this.filterStatus();
    if (status) {
      params = params.set('status', status);
    } else if (this.filterQueue() === 'ASSIGNED_TO_ME') {
      params = params.set('status', 'ASSIGNED_TO_ME');
    }

    const adv = this.advSearchActive() ? this.advSearch() : {};
    for (const [key, value] of Object.entries(adv)) {
      if (value) params = params.set(key, value);
    }

    const url = `${environment.apiBaseUrl}/api/v1/rbio/complaints`;

    this.http.get<any>(url, { params }).subscribe({
      next: (res) => {
        const page = res?.data ?? res;
        const rows = Array.isArray(page) ? page : (page?.content ?? []);
        this.complaints.set(rows.map((c: any) => this.mapComplaint(c)));
        this.totalElements.set(page?.totalElements ?? rows.length);
        this.totalPages.set(Math.max(1, page?.totalPages ?? 1));
        this.loading.set(false);
      },
      error: (err) => {
        // Never fabricate rows. An officer shown invented case data cannot tell a broken backend from an
        // empty queue, and this screen used to render ten fake complaints on any failure.
        this.complaints.set([]);
        this.totalElements.set(0);
        this.totalPages.set(1);
        this.loadError.set(
          err?.status === 403
            ? 'rbio.grid.error_forbidden'
            : 'rbio.grid.error_load_failed'
        );
        this.loading.set(false);
      }
    });
  }

  private mapComplaint(c: any): RbioComplaint {
    return {
      // assignedOfficer is the key the server actually emits; assignedTo was never in the response.
      complaintId: String(c.complaintId ?? c.complaintNumber ?? ''),
      complaintNumber: c.complaintNumber || '',
      complainantName: c.complainantName || '',
      fromEmail: c.complainantEmail || c.fromEmail || '',
      subject: c.subject || '',
      modeOfReceipt: c.modeOfReceipt || '',
      status: c.status || '',
      category: c.category || '',
      entityName: c.entityName || '',
      priority: c.priority || 'MEDIUM',
      assignedTo: c.assignedOfficer || c.assignedTo || '',
      createdAt: c.createdAt || '',
      slaDueDate: c.slaDueDate || '',
      slaBreachDays: this.breachDays(c.slaDueDate),
      description: c.description || '',
      milestone: c.milestone || '',
      workflowStage: c.workflowStage || '',
      officeCode: c.officeCode || '',
      crpcReceivedAt: c.crpcReceivedAt || '',
      reResponseOverdue: c.reResponseOverdue === true,
    };
  }

  /**
   * Days past the SLA deadline, or a negative count of days remaining.
   *
   * The old version clamped this with Math.max(0, ...), so "3 days left" and "due today" both rendered as
   * 0 days and the colour helper could never show anything but breached. A missing deadline yields 0
   * rather than a breach against today's date — an absent SLA is not an overdue SLA.
   */
  private breachDays(slaDueDate: string | null | undefined): number {
    if (!slaDueDate) return 0;
    const due = new Date(slaDueDate);
    if (isNaN(due.getTime())) return 0;
    const msPerDay = 1000 * 60 * 60 * 24;
    return Math.ceil((Date.now() - due.getTime()) / msPerDay);
  }

  // ── Server-driven interactions ──

  /** UST434: selecting a status refreshes the grid immediately. */
  selectStatus(code: string) {
    // UST433: refuse a code the role does not hold, even if it arrives from a stale session or a
    // hand-edited URL. Hiding an option is not the same as rejecting it.
    if (code && !this.allowedStatusCodes().has(code)) {
      return;
    }
    this.filterStatus.set(code);
    this.currentPage.set(1);
    this.persistFilterState();
    this.loadComplaints();
  }

  setQueue(queue: 'ASSIGNED_TO_ME' | 'ALL') {
    this.filterQueue.set(queue);
    this.currentPage.set(1);
    this.persistFilterState();
    this.loadComplaints();
  }

  sortBy(column: string) {
    if (this.sortColumn() === column) {
      this.sortDirection.update(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
    this.currentPage.set(1);
    this.loadComplaints();
  }

  goToPage(page: number) {
    if (page < 1 || page > this.totalPages()) return;
    this.currentPage.set(page);
    this.loadComplaints();
  }

  changePageSize(size: number) {
    // UST435 caps a page at 100. A larger value arriving from a tampered option is clamped rather than
    // forwarded, so the client cannot ask the server for an unbounded page.
    this.pageSize.set(Math.min(size, 100));
    this.currentPage.set(1);
    this.persistFilterState();
    this.loadComplaints();
  }

  setColumnFilter(key: string, value: string) {
    this.columnFilters.update(f => ({ ...f, [key]: value }));
  }

  // ── Advance search (UST439-442) ──

  private emptyAdvSearch(): Record<string, string> {
    return {
      complaintNumber: '', complainantName: '', complainantMobile: '', complainantEmail: '',
      statusCode: '', complaintId: '', fromEmailId: '', subject: '', modeOfReceipt: '',
      entityName: '', nodalOfficerName: '', categoryId: '', reportedFrom: '', reportedTo: ''
    };
  }

  setAdvSearchField(key: string, value: string) {
    this.advSearch.update(s => ({ ...s, [key]: value }));
  }

  advSearchFieldCount = computed(() => Object.values(this.advSearch()).filter(v => !!v).length);

  applyAdvancedSearch() {
    this.advSearchActive.set(true);
    this.currentPage.set(1);
    this.showAdvancedSearch.set(false);
    // UST442: keep a readable record of what was asked for, so an empty result can show the criteria
    // instead of a bare "no complaints found".
    this.lastSearchSummary.set(
      Object.entries(this.advSearch())
        .filter(([, v]) => !!v)
        .map(([k, v]) => `${k}: ${v}`)
        .join(', ')
    );
    this.persistFilterState();
    this.loadComplaints();
  }

  clearAdvancedSearch() {
    this.advSearch.set(this.emptyAdvSearch());
    this.advSearchActive.set(false);
    this.lastSearchSummary.set('');
    this.currentPage.set(1);
    this.persistFilterState();
    this.loadComplaints();
  }

  // ── UST438: filter selection persists across navigation within the session ──

  private persistFilterState() {
    try {
      sessionStorage.setItem(FILTER_STATE_KEY, JSON.stringify({
        status: this.filterStatus(),
        queue: this.filterQueue(),
        pageSize: this.pageSize(),
        bands: [...this.selectedBands()],
        advSearch: this.advSearchActive() ? this.advSearch() : null
      }));
    } catch {}
  }

  private restoreFilterState() {
    try {
      const raw = sessionStorage.getItem(FILTER_STATE_KEY);
      if (!raw) return;
      const s = JSON.parse(raw);
      if (s.status) this.filterStatus.set(s.status);
      if (s.queue) this.filterQueue.set(s.queue);
      if (s.pageSize) this.pageSize.set(Math.min(s.pageSize, 100));
      if (Array.isArray(s.bands)) this.selectedBands.set(new Set(s.bands));
      if (s.advSearch) {
        this.advSearch.set({ ...this.emptyAdvSearch(), ...s.advSearch });
        this.advSearchActive.set(true);
      }
    } catch {}
  }

  // ── UST436/437: row colour bands ──

  selectedBands = signal<Set<string>>(new Set());
  availableBands = computed(() => this.bandService.bands());

  clearBands() {
    this.selectedBands.set(new Set());
    this.persistFilterState();
  }

  toggleBand(code: string) {
    this.selectedBands.update(b => {
      const next = new Set(b);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return next;
    });
    this.persistFilterState();
  }

  /**
   * The band for one row, derived at render time from current state and the CRPC receipt date.
   *
   * Not persisted: a stored band would be a second copy of the complaint's state and would go stale the
   * moment the complaint moved. The thresholds and colours come from configuration, not literals.
   */
  bandFor(item: RbioComplaint): string {
    return this.bandService.bandFor({
      status: item.status,
      workflowStage: item.workflowStage,
      milestone: item.milestone,
      createdAt: item.createdAt,
      crpcReceivedAt: item.crpcReceivedAt
    });
  }

  /**
   * UST437: colour and status filters combine as an INTERSECTION.
   *
   * The status filter is applied by the server; this narrows the returned page by band. Treating the two
   * as a union would show rows the status filter had already excluded.
   */
  bandVisible(item: RbioComplaint): boolean {
    const selected = this.selectedBands();
    if (selected.size === 0) return true;
    return selected.has(this.bandFor(item));
  }

  visibleRows = computed(() => this.displayedComplaints().filter(d => this.bandVisible(d)));

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
    const rows = this.visibleRows();
    if (this.selectedIds().size === rows.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(rows.map(d => d.complaintId)));
    }
  }

  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  /** The label for a status code, from RBIO_STATUS_MASTER rather than a compiled-in map. */
  statusLabelKey(status: string): string {
    const match = this.statusFilters().find(f =>
      f.statusCode === status?.toUpperCase() || f.legacyValue === status?.toLowerCase());
    return match?.translationKey ?? `rbio.status.${(status || 'unknown').toLowerCase()}`;
  }

  getCellValue(complaint: RbioComplaint, key: string): string {
    const val = (complaint as any)[key];
    if (val === null || val === undefined || val === '') return '—';
    if (key === 'createdAt') return new Date(val).toLocaleDateString('en-IN');
    return String(val);
  }

  getSlaLabel(days: number): string {
    return days > 0 ? `${days} overdue` : `${Math.abs(days)} left`;
  }

  getSlaClass(days: number): string {
    if (days > 20) return 'sla-red';
    if (days > 5) return 'sla-orange';
    return 'sla-green';
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
