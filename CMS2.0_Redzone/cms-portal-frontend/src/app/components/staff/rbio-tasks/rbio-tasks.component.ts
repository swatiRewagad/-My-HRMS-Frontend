import { Component, inject, OnInit, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TaskGridComponent } from '../../shared/task-grid/task-grid.component';
import { TaskGridColumn } from '../../shared/task-grid/task-grid.types';
import { environment } from '../../../../environments/environment';

interface ComplaintTask {
  /** A NUMBER over the wire, despite the name. See `visitedIds`. */
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

/**
 * The RBIO officer's queue.
 *
 * <h2>The grid itself is the SHARED grid</h2>
 * Search, per-column filters, sorting, the column chooser, pagination, page sizing, the status badge and
 * the empty/loading/error states all live in app-task-grid. They used to be re-implemented here, and the
 * local copies were subtly broken in ways the shared component is not — column filters held in a plain
 * object recomputed nothing, and the column chooser mutated an array in place so the header row never
 * changed. Consolidating removes the possibility of that divergence rather than fixing it once.
 *
 * <h2>What stays here, and why</h2>
 * Everything above the grid is genuinely RBIO's: the KPI cards and status tabs count RBIO statuses, the
 * queue select filters them, the three toggles (Unread / Without Attachments / Satisfies Rules) read
 * RBIO's own fields, and advance search covers RBIO's eight criteria. Those are filters over this
 * module's data, not grid mechanics, so pushing them into the shared grid would make it a dumping ground.
 * The toggles reach the grid's header bar through its `headerActions` slot, so this screen does not end
 * up with two rows of controls.
 */
@Component({
  selector: 'app-rbio-tasks',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, TaskGridComponent],
  templateUrl: './rbio-tasks.component.html',
  styleUrl: './rbio-tasks.component.scss'
})
export class RbioTasksComponent implements OnInit {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private http = inject(HttpClient);

  tasks = signal<ComplaintTask[]>([]);
  loading = signal(false);

  /**
   * Ids of tasks this officer has already opened, so the queue can de-emphasise them.
   *
   * <p>Keyed by STRING throughout. The endpoint returns `complaintId` as a number, and the previous code
   * stored `String(id)` while the template tested `has(id)` with the raw number — so `.visited` never
   * applied and the Unread toggle considered every task unread forever. One coercion helper now owns the
   * conversion so the two sides cannot disagree again.
   */
  visitedIds = signal<Set<string>>(new Set(JSON.parse(localStorage.getItem('rbio_visited_ids') || '[]')));

  // Filters. All signals: a computed() reading a plain field registers no dependency, which is the exact
  // defect that made this screen's column filters and column chooser inert.
  filterStatus = signal('');
  filterUnread = signal(false);
  filterWithoutAttachments = signal(false);
  filterSatisfiesRules = signal(false);

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
   * Bumped every time the criteria are APPLIED, so the filter computed re-runs.
   *
   * <p>{@code advSearch} is a plain object bound with ngModel, and a computed() reading it registers no
   * dependency — the same class of defect that made the per-column filters inert. This counter is the
   * signal the computed actually depends on.
   *
   * <p>It must be bumped on apply and not only on first activation: setting {@code advSearchActive} to
   * true when it is already true notifies nothing, so narrowing an active search to different criteria
   * would leave the previous result on screen.
   */
  advSearchRevision = signal(0);

  /**
   * The queue's columns.
   *
   * <p>Headers are translation KEYS from the shared `ui.col.*` vocabulary, not literals: the same
   * complaint field must read identically in CEPC's and CRPC's grids, and the hardcoded English strings
   * this screen used to carry are why those grids had no localisation at all.
   *
   * <p>`visible: false` puts a column behind the chooser rather than removing it.
   */
  readonly gridColumns: TaskGridColumn<ComplaintTask>[] = [
    { key: 'complaintNumber', labelKey: 'ui.col.complaint_number' },
    { key: 'subject', labelKey: 'ui.col.subject' },
    { key: 'complainantName', labelKey: 'ui.col.complainant_name' },
    { key: 'entityName', labelKey: 'ui.col.entity_name' },
    { key: 'priority', labelKey: 'ui.col.priority', kind: 'priority' },
    { key: 'status', labelKey: 'ui.col.status', kind: 'status' },
    { key: 'assignedOfficer', labelKey: 'ui.col.assigned_officer' },
    { key: 'slaDueDate', labelKey: 'ui.col.deadline', kind: 'date' },
    { key: 'assignedAt', labelKey: 'ui.col.created_at', kind: 'date', visible: false },
    { key: 'department', labelKey: 'ui.col.office', visible: false },
    { key: 'assignedRole', labelKey: 'ui.col.assigned_role', visible: false },
  ];

  /**
   * Rows handed to the grid: RBIO's own filters applied, nothing else.
   *
   * <p>Free-text search, per-column filters and sorting are deliberately NOT here — the grid owns those,
   * and duplicating them would mean two filters fighting over the same rows.
   */
  filteredTasks = computed(() => {
    let result = this.tasks();
    const status = this.filterStatus();
    if (status) result = result.filter(t => t.status?.toLowerCase() === status.toLowerCase());

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

    if (this.filterUnread()) {
      result = result.filter(t => !this.hasVisited(t));
    }
    if (this.filterWithoutAttachments()) {
      result = result.filter(t => !t.hasAttachments);
    }
    if (this.filterSatisfiesRules()) {
      result = result.filter(t => t.triageSignal === 'OBJECTIVELY_CLEAR');
    }
    return result;
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

  /**
   * Dims rows the officer has already opened.
   *
   * <p>Passed to the grid rather than bound in a template, because the grid owns the row element now.
   */
  readonly rowClass = (task: ComplaintTask): string => (this.hasVisited(task) ? 'visited' : '');

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

  loadTasks() {
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

  /** The single place the id is coerced, so what is stored and what is tested cannot drift apart. */
  private visitKey(task: ComplaintTask): string {
    return String(task.complaintId);
  }

  private hasVisited(task: ComplaintTask): boolean {
    return this.visitedIds().has(this.visitKey(task));
  }

  openTask(task: ComplaintTask) {
    const visited = new Set(this.visitedIds());
    visited.add(this.visitKey(task));
    this.visitedIds.set(visited);
    localStorage.setItem('rbio_visited_ids', JSON.stringify([...visited]));
    this.router.navigate(['/staff/rbio/task', task.complaintNumber]);
  }

  /**
   * Activates the advance-search criteria (UST439).
   *
   * The previous version computed a filtered `result` into a LOCAL VARIABLE and discarded it, then stuffed
   * the criteria as JSON into the free-text search box — so the free-text filter matched a JSON blob
   * against complaint fields and nothing ever matched. The criteria now drive the `filteredTasks` computed
   * through a signal, which is what makes the dialog actually filter.
   */
  applyAdvancedSearch() {
    this.advSearchActive.set(true);
    // Bumped here, not in a per-field handler: the fields are ngModel-bound to a plain object, so this is
    // the only point at which the computed can be told the criteria changed.
    this.advSearchRevision.update(v => v + 1);
    this.showAdvancedSearch.set(false);
  }

  clearAdvancedSearch() {
    this.advSearch = {
      complaintNumber: '', complaintId: '', statusCode: '', complainantName: '',
      entityName: '', subject: '', priority: '', assignedOfficer: ''
    };
    this.advSearchActive.set(false);
    this.advSearchRevision.update(v => v + 1);
  }

  navigateToCreateComplaint() {
    this.router.navigate(['/rbio/create-complaint']);
  }

  async logout() {
    await this.auth.logout();
  }
}
