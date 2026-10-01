import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { NotificationBellComponent } from '../../../shared/notification-bell/notification-bell.component';
import { SessionTimeoutComponent } from '../../../shared/session-timeout/session-timeout.component';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';
import { environment } from '../../../../environments/environment';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { AppShellComponent } from '../../shared/app-shell/app-shell.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TaskGridComponent } from '../../shared/task-grid/task-grid.component';
import { TaskGridColumn } from '../../shared/task-grid/task-grid.types';

interface CepcComplaint {
  /**
   * The numeric primary key, as a STRING.
   *
   * <p>The server sends `complaintId` as a number (Complaint#getId). It is normalised to a string on
   * arrival because this field is the grid's rowKey and the advanced-search filter calls
   * `.toLowerCase()` on it — on a raw number that threw a TypeError inside a computed(), which left
   * the Advanced Search dialog stuck open and the grid frozen on every Complaint Id search.
   */
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  entityName: string;
  entityType: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  department: string;
  assignedRole: string;
  assignedOfficer: string;
  workflowStage: string;
  modeOfReceipt: string;
  category: string;
  createdAt: string;
  /** True when an attachment exists. Published by WorkflowController.buildTaskList. */
  hasAttachments: boolean;
  /** >0 once the complaint has been reopened at least once. */
  reopenCount: number;
}

/**
 * One selectable queue bucket.
 *
 * <p>A bucket is a PREDICATE, not a status string, because three of the buckets the DO needs are not
 * statuses: "Meeting Scheduled" is a workflow stage, "Reopened" is a counter, and "Pending" folds four
 * statuses together. Modelling them all as status equality is what left 445 of the DO's 641 complaints
 * with no bucket that reached them.
 */
interface CepcBucket {
  /** Stable code, used as the selected value and in the URL-free component state. */
  code: string;
  labelKey: string;
  match: (c: CepcComplaint) => boolean;
  /**
   * Offered even when the role currently holds nothing in it.
   *
   * <p>Occupancy-derivation alone is NOT what the reference vertical does. GET
   * /api/v1/rbio/status-filters returns a per-role MASTER list (15 entries for RBIO_OFFICER, 17 for
   * the Supervisor, 34 for the Admin) and rbio-home.component.html:148 renders every one of them
   * unconditionally — MEETING_SCHEDULED and SENT_BACK_DO are on the strip whether or not a single
   * complaint sits in them. "Has anything been sent back to me?" is a question whose legitimate
   * answer is zero, and a strip that hides the bucket cannot answer it.
   *
   * <p>So these two are pinned while the rest stay occupancy-derived. The strip is still
   * role-DEPENDENT, because the derived part differs: the DO holds ASSIGNED / FORWARDED /
   * INFO_REQUESTED / RE_RESPONDED, the Reviewer holds REVIEWER_REVIEW.
   */
  alwaysShow?: boolean;
}

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

@Component({
  selector: 'app-cepc-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule, SessionTimeoutComponent, SpeechButtonComponent, CepcSlaIndicatorComponent, StatusBadgeComponent, AppShellComponent, TranslatePipe, TaskGridComponent],
  templateUrl: './cepc-dashboard.component.html',
  styleUrl: './cepc-dashboard.component.scss'
})
export class CepcDashboardComponent implements OnInit {
  router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaints = signal<CepcComplaint[]>([]);
  loading = signal(true);
  userRole = signal<CepcRole>('CEPC_DO');

  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set());

  filterStatus = signal('');
  filterQueue = signal<'ASSIGNED_TO_ME' | 'ALL'>('ASSIGNED_TO_ME');
  filterUnread = signal(false);
  /**
   * The "Without Attachments" toggle's state.
   *
   * <p>Named for what the filter DOES rather than for the label, because the two disagree: the manual
   * QA case requires the toggle to show complaints that HAVE attachments. See filteredComplaints().
   */
  filterWithAttachments = signal(false);
  /**
   * Per-column text filters.
   *
   * <p>A SIGNAL, not the plain object this used to be: `filteredComplaints` is a computed(), and a
   * plain field read inside one registers no dependency, so typing in a column filter recomputed
   * nothing. The shared task-grid owns its own column filtering now, but this is still read by the
   * component's own filter chain and had the same defect as RBIO's did.
   */
  columnFilters = signal<Record<string, string>>({});

  sortColumn = '';
  sortDirection: 'asc' | 'desc' = 'asc';

  currentPage = signal(1);
  pageSize = 10;

  showAdvancedSearch = signal(false);
  advSearchActive = signal(false);
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', email: '', entityName: '',
    subject: '', priority: ''
  };


  loggedInUser: { id: string; name: string; role: string } | null = null;

  roleLabels: Record<CepcRole, string> = {
    'CEPC_DO': 'Dealing Officer',
    'CEPC_REVIEWER': 'Reviewer',
    'CEPC_INCHARGE': 'In Charge',
    'CEPC_CLOSING_AUTHORITY': 'Closing Authority',
    'CEPC_ADMIN': 'Admin',
    'CEPC_CONTACT_PERSON': 'Contact Person'
  };

  // ─── Create Complaint Dialog ───
  showCreateDialog = signal(false);
  newComplaint = {
    complainantName: '', complainantEmail: '', complainantPhone: '',
    complainantAddress: '', subject: '', description: '',
    entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
  };
  creating = signal(false);
  createSuccess = signal('');
  createError = signal('');

  // ─── Column Config ───
  /**
   * Column configuration for the shared task grid.
   *
   * Headers are translation KEYS, not literals — CEPC had zero localisation before, and hardcoding a
   * header here would put it straight back. The grid owns sorting, per-column filtering, the column
   * chooser, empty/loading states and pagination, so none of that is re-implemented in this component.
   */
  /**
   * All twelve columns are default-visible.
   *
   * <p>Four of them — category, modeOfReceipt, assignedOfficer, createdAt — were `visible: false`, and
   * three carried no server data at all, so un-hiding them alone would have produced four permanently
   * blank columns. WorkflowController.buildTaskList now publishes category, modeOfReceipt and createdAt,
   * which is what made this safe. RBIO's grid default-shows the same field set by name, and RBIO is the
   * reference every module copies; a column still hideable through the chooser for an officer who does
   * not want it.
   */
  readonly gridColumns: TaskGridColumn<CepcComplaint>[] = [
    { key: 'complaintId', labelKey: 'ui.col.complaint_id' },
    { key: 'complaintNumber', labelKey: 'ui.col.complaint_number' },
    { key: 'complainantName', labelKey: 'ui.col.complainant_name' },
    { key: 'entityName', labelKey: 'ui.col.entity_name' },
    { key: 'subject', labelKey: 'ui.col.subject' },
    { key: 'priority', labelKey: 'ui.col.priority', kind: 'priority' },
    { key: 'status', labelKey: 'ui.col.status', kind: 'status' },
    { key: 'slaDueDate', labelKey: 'ui.col.deadline', kind: 'custom', template: 'sla' },
    { key: 'category', labelKey: 'ui.col.category' },
    { key: 'modeOfReceipt', labelKey: 'ui.col.mode_of_receipt' },
    { key: 'assignedOfficer', labelKey: 'ui.col.assigned_officer' },
    { key: 'createdAt', labelKey: 'ui.col.created_at' },
  ];

  /**
   * The queue buckets, built from the statuses the role ACTUALLY holds.
   *
   * <p>The old six were a fixed list (All / Pending / Under Examination / Under Review / Awaiting
   * Closure / Escalated) that reached none of FORWARDED, RE_RESPONDED or INFO_REQUESTED — 145 of the
   * DO's 641 complaints were unreachable through any bucket, and ASSIGNED's 320 were silently folded
   * into "Pending" with no way to separate them. Deriving the list from the loaded rows means a bucket
   * appears exactly when the role has work in it, which is also why the Reviewer and the DO no longer
   * see the same strip: their queues hold different statuses.
   *
   * <p>Server statuses arrive UPPERCASE; the comparison lowercases both sides, which the previous
   * `c.status === status` did not — another reason several buckets matched nothing.
   */
  readonly allBuckets: readonly CepcBucket[] = [
    { code: 'pending', labelKey: 'status.pending', match: c => ['pending', 'new'].includes(c.status) },
    { code: 'assigned', labelKey: 'status.assigned', match: c => c.status === 'assigned' },
    { code: 'in_progress', labelKey: 'status.in_progress', match: c => c.status === 'in_progress' },
    { code: 'reviewer_review', labelKey: 'status.reviewer_review', match: c => c.status === 'reviewer_review' },
    { code: 'incharge_review', labelKey: 'status.incharge_review', match: c => c.status === 'incharge_review' },
    { code: 'under_review', labelKey: 'status.under_review', match: c => c.status === 'under_review' },
    { code: 'info_requested', labelKey: 'status.info_requested', match: c => c.status === 'info_requested' },
    { code: 're_responded', labelKey: 'status.re_responded', match: c => c.status === 're_responded' },
    { code: 'forwarded', labelKey: 'status.forwarded', match: c => c.status === 'forwarded' },
    { code: 'forwarded_external', labelKey: 'status.forwarded_external', match: c => c.status === 'forwarded_external' },
    { code: 'forwarded_to_contact', labelKey: 'status.forwarded_to_contact', match: c => c.status === 'forwarded_to_contact' },
    { code: 'sent_back', labelKey: 'status.sent_back', match: c => c.status === 'sent_back', alwaysShow: true },
    { code: 'awaiting_closure', labelKey: 'status.awaiting_closure', match: c => c.status === 'awaiting_closure' },
    { code: 'escalated', labelKey: 'status.escalated', match: c => c.status === 'escalated' },
    { code: 'resolved', labelKey: 'status.resolved', match: c => c.status === 'resolved' },
    { code: 'closed', labelKey: 'status.closed', match: c => c.status === 'closed' },
    // Not statuses. A scheduled meeting is a workflow STAGE and a reopen is a counter, so neither can
    // be reached by any status bucket — which is why both needed modelling as a predicate.
    {
      code: 'meeting_scheduled',
      labelKey: 'cepc.bucket.meeting_scheduled',
      match: c => c.workflowStage === 'MEETING_SCHEDULED',
      alwaysShow: true,
    },
    { code: 'reopened', labelKey: 'cepc.bucket.reopened', match: c => c.reopenCount > 0 },
  ];

  /**
   * The buckets this role holds work in, PLUS the ones pinned by `alwaysShow`.
   *
   * <p>Occupancy decides the long tail so no bucket is offered that returns nothing, which is what
   * stopped the DO's 445 unreachable complaints. The pinned pair is the deliberate exception — see
   * {@link CepcBucket.alwaysShow} for why the reference vertical shows them unconditionally.
   */
  visibleBuckets = computed(() => {
    const all = this.complaints();
    return this.allBuckets.filter(b => b.alwaysShow || all.some(c => b.match(c)));
  });

  bucketCount(bucket: CepcBucket): number {
    return this.complaints().filter(c => bucket.match(c)).length;
  }

  /**
   * The KPI cards, matching RBIO's five.
   *
   * <p>RBIO is the reference vertical, its cards read from `rbio.stats.*`, and those keys already carry
   * all eleven locales — so CEPC reuses them rather than minting a parallel `cepc.stats.*` set that
   * could drift. Unlike RBIO these counts are over the WHOLE loaded queue, not one page, because this
   * dashboard is not server-paged: loadComplaints() takes no page parameter.
   */
  stats = computed(() => {
    const all = this.complaints();
    const isOpen = (s: string) => ['new', 'pending', 'assigned', 'in_progress'].includes(s);
    const now = Date.now();
    return {
      totalPending: all.filter(c => !['closed', 'resolved', 'withdrawn', 'rejected'].includes(c.status)).length,
      pendingWithMe: all.filter(c => isOpen(c.status)).length,
      pendingContactPerson: all.filter(c => c.status === 'forwarded_to_contact' || c.status === 'info_requested').length,
      pendingMeeting: all.filter(c => c.workflowStage === 'MEETING_SCHEDULED').length,
      // An SLA breach is a deadline in the past on a complaint that is still open. A breach on a closed
      // complaint is history, not a queue a DO can act on.
      slaBreach: all.filter(c => {
        if (['closed', 'resolved', 'withdrawn', 'rejected'].includes(c.status)) return false;
        const due = c.slaDueDate ? Date.parse(c.slaDueDate) : NaN;
        return !Number.isNaN(due) && due < now;
      }).length,
    };
  });

  filteredComplaints = computed(() => {
    let result = this.complaints();

    const bucketCode = this.filterStatus();
    if (bucketCode) {
      const bucket = this.allBuckets.find(b => b.code === bucketCode);
      if (bucket) result = result.filter(c => bucket.match(c));
    }

    if (this.advSearchActive()) {
      const q = this.advSearch;
      if (q.complaintNumber) result = result.filter(d => d.complaintNumber?.toLowerCase().includes(q.complaintNumber.toLowerCase()));
      // String(...) rather than a bare .toLowerCase(): complaintId arrives from the server as a NUMBER,
      // so the bare call raised a TypeError inside this computed() and froze the grid mid-search.
      // mapComplaint normalises it too; this is belt-and-braces because the exception was silent.
      if (q.complaintId) result = result.filter(d => String(d.complaintId ?? '').toLowerCase().includes(q.complaintId.toLowerCase()));
      if (q.statusCode) result = result.filter(d => d.status === q.statusCode);
      if (q.complainantName) result = result.filter(d => d.complainantName?.toLowerCase().includes(q.complainantName.toLowerCase()));
      if (q.email) result = result.filter(d => d.complainantEmail?.toLowerCase().includes(q.email.toLowerCase()));
      if (q.entityName) result = result.filter(d => d.entityName?.toLowerCase().includes(q.entityName.toLowerCase()));
      if (q.subject) result = result.filter(d => d.subject?.toLowerCase().includes(q.subject.toLowerCase()));
      if (q.priority) result = result.filter(d => d.priority === q.priority);
    }

    for (const [key, val] of Object.entries(this.columnFilters())) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(d => String((d as any)[key] || '').toLowerCase().includes(q));
      }
    }

    if (this.filterUnread()) {
      result = result.filter(d => !this.visitedIds().has(d.complaintId));
    }

    // Narrows to complaints that HAVE an attachment, which is what the manual QA case specifies for
    // this toggle ("...then all the complaints which contains attachments are displayed"). That reads
    // backwards against the control's own name and is flagged for confirmation in the report rather
    // than quietly reversed here.
    //
    // RBIO deleted its equivalent toggle because its list response carries no attachment fact, so the
    // control could only ever animate and do nothing. CEPC is genuinely different: GET
    // /workflow/cepc/tasks publishes hasAttachments per row (WorkflowController.buildTaskList), so
    // this filter has real data behind it.
    if (this.filterWithAttachments()) {
      result = result.filter(d => d.hasAttachments);
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

  async ngOnInit() {
    try {
      const visited = localStorage.getItem('cepc_visitedComplaintIds');
      // Each id is coerced to a string on the way IN. complaintId is a string everywhere in this
      // component, but ids persisted by earlier builds (and the numeric complaintId the server sends)
      // are JSON numbers, and Set<string>.has(4724) is false for a stored 4724 — so the Unread filter
      // silently treated every already-read complaint as still unread.
      if (visited) {
        const parsed = JSON.parse(visited);
        if (Array.isArray(parsed)) {
          this.visitedIds.set(new Set(parsed.map((id: unknown) => String(id))));
        }
      }
    } catch {}

    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('CEPC_ADMIN')) this.userRole.set('CEPC_ADMIN');
    else if (roles.includes('CEPC_CLOSING_AUTHORITY')) this.userRole.set('CEPC_CLOSING_AUTHORITY');
    else if (roles.includes('CEPC_INCHARGE')) this.userRole.set('CEPC_INCHARGE');
    else if (roles.includes('CEPC_REVIEWER')) this.userRole.set('CEPC_REVIEWER');
    else if (roles.includes('CEPC_CONTACT_PERSON')) this.userRole.set('CEPC_CONTACT_PERSON');
    else this.userRole.set('CEPC_DO');

    const user = this.auth.currentUser();
    if (user) {
      this.loggedInUser = { id: user.username, name: `${user.firstName} ${user.lastName}`.trim() || user.username, role: this.userRole() };
    }

    this.loadComplaints();
  }

  loadComplaints() {
    this.loading.set(true);
    const officer = this.auth.currentUser()?.username || '';
    const role = this.userRole();

    let url: string;
    if (role === 'CEPC_ADMIN') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks`;
    } else if (role === 'CEPC_CONTACT_PERSON') {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/contact-person/tasks?officer=${officer}`;
    } else {
      url = `${environment.apiBaseUrl}/api/v1/workflow/cepc/tasks?role=${role}`;
    }

    this.http.get<any>(url).subscribe({
      next: (res) => {
        const roleTasks = (res?.data || []).map((c: any) => this.mapComplaint(c));
        this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/my-actions?officer=${officer}`).subscribe({
          next: (actionsRes) => {
            const actionTasks = (actionsRes?.data || []).map((c: any) => this.mapComplaint(c));
            const existingIds = new Set(roleTasks.map((t: any) => t.complaintNumber));
            const merged = [...roleTasks, ...actionTasks.filter((t: any) => !existingIds.has(t.complaintNumber))];
            this.complaints.set(merged);
            this.loading.set(false);
          },
          error: () => {
            this.complaints.set(roleTasks);
            this.loading.set(false);
          }
        });
      },
      error: () => {
        this.complaints.set([]);
        this.loading.set(false);
      }
    });
  }

  private mapComplaint(c: any): CepcComplaint {
    return {
      // Coerced to a string HERE, at the boundary. The server sends a number; every downstream use —
      // the grid rowKey, the visited-ids Set, the advanced-search filter — assumes a string, and the
      // mismatch both threw inside a computed() and made the read/unread Set never match.
      complaintId: c.complaintId != null ? String(c.complaintId) : (c.complaintNumber || ''),
      complaintNumber: c.complaintNumber || '',
      subject: c.subject || '',
      complainantName: c.complainantName || '',
      complainantEmail: c.complainantEmail || '',
      complainantPhone: c.complainantPhone || '',
      entityName: c.entityName || '',
      entityType: c.entityType || '',
      priority: c.priority || 'MEDIUM',
      // LOWERCASED at the boundary. GET /workflow/cepc/tasks publishes status in UPPER CASE
      // ('IN_PROGRESS', 'ASSIGNED'), while every bucket predicate, the stats() counters and the
      // advanced-search statusCode option all compare against lower-case codes. Without this
      // normalisation not one bucket matched, so visibleBuckets() was empty and every KPI counted 0
      // on a 759-row queue.
      status: (c.status || 'pending').toLowerCase(),
      assignedAt: c.assignedAt || '',
      slaDueDate: c.slaDueDate || '',
      department: c.department || 'CEPC',
      assignedRole: c.assignedRole || '',
      assignedOfficer: c.assignedOfficer || '',
      workflowStage: c.workflowStage || '',
      modeOfReceipt: c.modeOfReceipt || c.filingType || '',
      category: c.category || '',
      createdAt: c.createdAt || '',
      hasAttachments: c.hasAttachments === true,
      reopenCount: Number(c.reopenCount) || 0,
    };
  }

  // ─── Table Features ───
  sortBy(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }
  }

  getCellValue(item: CepcComplaint, key: string): string {
    return (item as any)[key] || '—';
  }

  toggleSelectAll() {
    const all = this.filteredComplaints();
    if (this.selectedIds().size === all.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(all.map(c => c.complaintId)));
    }
  }

  toggleSelect(id: string) {
    this.selectedIds.update(ids => {
      const s = new Set(ids);
      if (s.has(id)) s.delete(id); else s.add(id);
      return s;
    });
  }

  openComplaint(complaint: CepcComplaint) {
    this.visitedIds.update(ids => {
      const s = new Set(ids);
      s.add(complaint.complaintId);
      localStorage.setItem('cepc_visitedComplaintIds', JSON.stringify([...s]));
      return s;
    });
    this.router.navigate(['/cepc/complaint', complaint.complaintNumber]);
  }

  changePageSize(size: number) {
    this.pageSize = size;
    this.currentPage.set(1);
  }

  // ─── Column Config ───

  // ─── Advanced Search ───
  applyAdvancedSearch() {
    this.advSearchActive.set(true);
    this.showAdvancedSearch.set(false);
    this.currentPage.set(1);
  }

  clearAdvancedSearch() {
    this.advSearch = { complaintNumber: '', complaintId: '', statusCode: '', complainantName: '', email: '', entityName: '', subject: '', priority: '' };
    this.advSearchActive.set(false);
    this.currentPage.set(1);
  }

  // ─── Create Complaint ───
  openCreateDialog() {
    this.showCreateDialog.set(true);
    this.createSuccess.set('');
    this.createError.set('');
    this.newComplaint = {
      complainantName: '', complainantEmail: '', complainantPhone: '',
      complainantAddress: '', subject: '', description: '',
      entityName: '', priority: 'MEDIUM', filingType: 'CEPC_MANUAL'
    };
  }

  closeCreateDialog() {
    this.showCreateDialog.set(false);
  }

  submitNewComplaint() {
    if (!this.newComplaint.complainantName || !this.newComplaint.subject) {
      this.createError.set('Complainant Name and Subject are required.');
      return;
    }
    this.creating.set(true);
    this.createError.set('');

    const payload = {
      ...this.newComplaint,
      createdBy: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/create-complaint`, payload)
      .subscribe({
        next: (res) => {
          this.creating.set(false);
          this.createSuccess.set(`Complaint ${res?.data?.complaintNumber} created successfully.`);
          setTimeout(() => {
            this.showCreateDialog.set(false);
            this.loadComplaints();
          }, 1500);
        },
        error: (err) => {
          this.creating.set(false);
          this.createError.set(err.error?.message || 'Failed to create complaint.');
        }
      });
  }

  async logout() {
    await this.auth.logout();
  }
}
