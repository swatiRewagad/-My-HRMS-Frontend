import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

interface ComplaintTask {
  complaintId: string;
  complaintNumber: string;
  subject: string;
  complainantName: string;
  priority: string;
  status: string;
  assignedAt: string;
  slaDueDate: string;
  entityName: string;
  department: string;
  assignedRole: string;
  assignedOfficer: string;
  hasAttachments?: boolean;
  triageSignal?: string;
}

@Component({
  selector: 'app-rbio-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-tasks.component.html',
  styleUrl: './rbio-tasks.component.scss'
})
export class RbioTasksComponent implements OnInit {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private http = inject(HttpClient);

  tasks = signal<ComplaintTask[]>([]);
  loading = signal(false);
  selectedIds = signal<Set<string>>(new Set());
  visitedIds = signal<Set<string>>(new Set(JSON.parse(localStorage.getItem('rbio_visited_ids') || '[]')));

  // Filters
  filterStatus = signal('');
  searchText = signal('');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
  filterSatisfiesRules = signal(false);
  // EVERY piece of filter/sort/paging state is a signal. A computed() only re-evaluates when a SIGNAL it
  // read changes; reading a plain class field registers no dependency at all. These four were plain fields,
  // which is why the per-column search boxes, the column sorting and the page-size selector did nothing —
  // the values changed and no view ever recomputed.
  columnFilters = signal<Record<string, string>>({});
  columnSearchText = signal('');

  // Sorting
  sortColumn = signal('');
  sortDirection = signal<'asc' | 'desc'>('asc');

  // Pagination
  currentPage = signal(1);
  pageSize = signal(25);

  // Dialogs
  showColumnConfig = signal(false);
  showAdvancedSearch = signal(false);

  // Advanced Search
  advSearch = {
    complaintNumber: '', complaintId: '', statusCode: '',
    complainantName: '', entityName: '', subject: '',
    priority: '', assignedOfficer: ''
  };

  /** Whether the advance-search criteria are being applied. */
  advSearchActive = signal(false);

  /**
   * Bumped whenever a criterion changes, so the filter computed re-runs.
   *
   * {@code advSearch} is a plain object, and a computed() reading it registers no dependency — the same
   * defect that made the per-column filters inert. This revision counter is the signal the computed
   * actually depends on.
   */
  advSearchRevision = signal(0);

  // Column configuration
  // A signal, so toggling visibility actually repaints. As a plain array, toggleColumnVisibility mutated
  // an object in place and visibleColumns() never re-ran, so the column chooser was inert.
  allColumns = signal([
    { key: 'complaintNumber', label: 'Complaint Number', visible: true },
    { key: 'subject', label: 'Subject', visible: true },
    { key: 'complainantName', label: 'Complainant Name', visible: true },
    { key: 'entityName', label: 'Entity Name', visible: true },
    { key: 'priority', label: 'Priority', visible: true },
    { key: 'status', label: 'Status', visible: true },
    { key: 'assignedOfficer', label: 'Assigned To', visible: true },
    { key: 'slaDueDate', label: 'SLA Due Date', visible: true },
    { key: 'assignedAt', label: 'Assigned At', visible: false },
    { key: 'department', label: 'Department', visible: false },
    { key: 'assignedRole', label: 'Role', visible: false },
  ]);

  visibleColumns = computed(() => this.allColumns().filter(c => c.visible));

  filteredColumns = computed(() => {
    const q = this.columnSearchText().toLowerCase();
    if (!q) return this.allColumns();
    return this.allColumns().filter(c => c.label.toLowerCase().includes(q));
  });

  filteredTasks = computed(() => {
    let result = this.tasks();
    const status = this.filterStatus();
    const search = this.searchText();
    if (status) result = result.filter(t => t.status?.toLowerCase() === status.toLowerCase());
    if (search) {
      const q = search.toLowerCase();
      result = result.filter(t =>
        t.complaintNumber?.toLowerCase().includes(q) ||
        t.complainantName?.toLowerCase().includes(q) ||
        t.entityName?.toLowerCase().includes(q) ||
        t.subject?.toLowerCase().includes(q)
      );
    }
    // Advance-search criteria (UST439). Reading advSearchRevision() is what registers the dependency —
    // without it this block would read a plain object and never re-run, which is exactly how the dialog
    // came to compute a filtered list and discard it.
    if (this.advSearchActive()) {
      this.advSearchRevision();
      const a = this.advSearch;
      if (a.complaintNumber) result = result.filter(t => t.complaintNumber?.toLowerCase().includes(a.complaintNumber.toLowerCase()));
      if (a.complaintId) result = result.filter(t => String(t.complaintId ?? '').includes(a.complaintId));
      if (a.statusCode) result = result.filter(t => t.status?.toLowerCase() === a.statusCode.toLowerCase());
      if (a.complainantName) result = result.filter(t => t.complainantName?.toLowerCase().includes(a.complainantName.toLowerCase()));
      if (a.entityName) result = result.filter(t => t.entityName?.toLowerCase().includes(a.entityName.toLowerCase()));
      if (a.subject) result = result.filter(t => t.subject?.toLowerCase().includes(a.subject.toLowerCase()));
      if (a.priority) result = result.filter(t => t.priority?.toLowerCase() === a.priority.toLowerCase());
      if (a.assignedOfficer) result = result.filter(t => t.assignedOfficer?.toLowerCase().includes(a.assignedOfficer.toLowerCase()));
    }

    for (const [key, val] of Object.entries(this.columnFilters())) {
      if (val) {
        const q = val.toLowerCase();
        result = result.filter(t => String((t as any)[key] || '').toLowerCase().includes(q));
      }
    }
    if (this.filterUnread()) {
      result = result.filter(t => !this.visitedIds().has(String(t.complaintId)));
    }
    if (this.filterWithoutAttachments()) {
      result = result.filter(t => !t.hasAttachments);
    }
    if (this.filterSatisfiesRules()) {
      result = result.filter(t => t.triageSignal === 'OBJECTIVELY_CLEAR');
    }
    const sortBy = this.sortColumn();
    if (sortBy) {
      const dir = this.sortDirection();
      result = [...result].sort((a, b) => {
        const av = (a as any)[sortBy] || '';
        const bv = (b as any)[sortBy] || '';
        const cmp = String(av).localeCompare(String(bv), undefined, { numeric: true });
        return dir === 'asc' ? cmp : -cmp;
      });
    }
    return result;
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.filteredTasks().length / this.pageSize())));

  paginatedTasks = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize();
    return this.filteredTasks().slice(start, start + this.pageSize());
  });

  paginationStart = computed(() => this.filteredTasks().length === 0 ? 0 : (this.currentPage() - 1) * this.pageSize() + 1);
  paginationEnd = computed(() => Math.min(this.currentPage() * this.pageSize(), this.filteredTasks().length));

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
    const all = this.tasks();
    return {
      total: all.length,
      assigned: all.filter(t => t.status?.toLowerCase() === 'assigned').length,
      inProgress: all.filter(t => t.status?.toLowerCase() === 'in_progress').length,
      escalated: all.filter(t => t.status?.toLowerCase() === 'escalated').length,
      resolved: all.filter(t => t.status?.toLowerCase() === 'resolved').length,
      rejected: all.filter(t => t.status?.toLowerCase() === 'rejected').length,
    };
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadTasks();
  }

  /** Surfaced so the template can tell an empty queue apart from a failed load. */
  loadError = signal<string | null>(null);

  private loadTasks() {
    this.loading.set(true);
    this.loadError.set(null);
    const officer = this.auth.currentUser()?.username || '';

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/rbio/all-tasks?officer=${officer}`)
      .subscribe({
        next: (res) => {
          this.tasks.set(res?.data || []);
          this.loading.set(false);
        },
        error: (err) => {
          // The error was previously swallowed into an empty array, so a 403 or an outage was
          // indistinguishable from "you have no tasks" — an officer would close the tab believing their
          // queue was clear. Surfaced with a retry instead.
          this.tasks.set([]);
          this.loadError.set(
            err?.status === 403
              ? 'rbio.grid.error_forbidden'
              : 'rbio.grid.error_load_failed'
          );
          this.loading.set(false);
        }
      });
  }

  sortBy(column: string) {
    if (this.sortColumn() === column) {
      this.sortDirection.update(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortColumn.set(column);
      this.sortDirection.set('asc');
    }
    // Re-sorting reorders the whole result, so staying on page 5 would show an arbitrary slice of it.
    this.currentPage.set(1);
  }

  openTask(task: ComplaintTask) {
    const visited = new Set(this.visitedIds());
    visited.add(String(task.complaintId));
    this.visitedIds.set(visited);
    localStorage.setItem('rbio_visited_ids', JSON.stringify([...visited]));
    this.router.navigate(['/staff/rbio/task', task.complaintNumber]);
  }

  toggleSelect(id: string) {
    const ids = new Set(this.selectedIds());
    if (ids.has(id)) ids.delete(id);
    else ids.add(id);
    this.selectedIds.set(ids);
  }

  toggleSelectAll() {
    const filtered = this.filteredTasks();
    if (this.selectedIds().size === filtered.length) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(filtered.map(t => t.complaintId)));
    }
  }

  toggleColumnVisibility(key: string) {
    this.allColumns.update(cols => cols.map(c => c.key === key ? { ...c, visible: !c.visible } : c));
  }

  setColumnFilter(key: string, value: string) {
    this.columnFilters.update(f => ({ ...f, [key]: value }));
  }

  changePageSize(size: number) {
    this.pageSize.set(Math.min(size, 100));
    this.currentPage.set(1);
  }

  /**
   * Activates the advance-search criteria (UST439).
   *
   * The previous version computed a filtered `result` into a LOCAL VARIABLE and discarded it, then stuffed
   * the criteria as JSON into `searchText` — so the free-text filter matched a JSON blob against complaint
   * fields and nothing ever matched. The criteria now drive the `filteredTasks` computed through a signal,
   * which is what makes the dialog actually filter.
   */
  applyAdvancedSearch() {
    this.advSearchActive.set(true);
    this.currentPage.set(1);
    // Deliberately NOT written into searchText: that field is the free-text box, and putting JSON in it
    // made the two filters fight each other.
    this.searchText.set('');
    this.showAdvancedSearch.set(false);
  }

  clearAdvancedSearch() {
    this.advSearch = {
      complaintNumber: '', complaintId: '', statusCode: '', complainantName: '',
      entityName: '', subject: '', priority: '', assignedOfficer: ''
    };
    this.advSearchActive.set(false);
    this.currentPage.set(1);
  }

  setAdvSearchField(key: string, value: string) {
    this.advSearch = { ...this.advSearch, [key]: value };
    // The criteria object is a plain field, so bump a signal to make the computed re-run. Without this the
    // dialog's inputs would be as inert as the column filters were.
    this.advSearchRevision.update(v => v + 1);
  }

  getCellValue(task: ComplaintTask, key: string): string {
    const val = (task as any)[key];
    if (val === null || val === undefined) return '—';
    if (key === 'assignedAt' || key === 'slaDueDate') {
      try { return new Date(val).toLocaleDateString('en-IN'); } catch { return val; }
    }
    return String(val);
  }

  navigateToCreateComplaint() {
    this.router.navigate(['/rbio/create-complaint']);
  }

  async logout() {
    await this.auth.logout();
  }
}
