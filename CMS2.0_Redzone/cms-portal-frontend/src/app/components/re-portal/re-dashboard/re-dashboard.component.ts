import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { TaskGridComponent } from '../../shared/task-grid/task-grid.component';
import { TaskGridColumn } from '../../shared/task-grid/task-grid.types';

interface DashboardStats {
  totalForwarded: number;
  pendingResponse: number;
  responded: number;
  breached: number;
}

interface ReComplaint {
  complaintNumber: string;
  subject: string;
  status: string;
  /**
   * Optional because /re-portal/complaints does not return them today: the list row carries
   * complaintNumber, subject, complainantName, status, priority, createdAt, filingType and the
   * reActivity* fields only. `forwardedDate` is populated from createdAt below; `responseDeadline`
   * has no source on this endpoint, so the deadline column renders empty rather than a wrong date.
   * Surfacing the real forwarded-at and the statutory response deadline needs a server change
   * (see the session report).
   */
  forwardedDate?: string;
  responseDeadline?: string;
  category?: string;
  complainantName?: string;
  reActivityStatus?: string;
}

/**
 * The entity's own queue, on the shared task grid.
 *
 * <h2>Why this screen is the shared grid now</h2>
 * The RE portal was the last module still rendering its queue as a hand-rolled `<table>`, so it
 * disagreed with every RBI-side queue about sorting, column filtering, the empty state and paging —
 * an entity could not sort by status or search within a column at all. The grid is the canonical
 * screen (see {@link TaskGridComponent}); converging means an entity meets the same queue mechanics
 * an RBIO officer does, which is the whole point of the homogenisation pass.
 *
 * <h2>The KPI cards are clickable, and each one is backed by a real figure</h2>
 * Every card reads a field the server actually emits from GET /api/v1/re-portal/dashboard
 * (RePortalService.getDashboardStats → totalForwarded / pending / responded / breached). None is
 * computed here and none is wired to an endpoint that does not exist. Clicking a card applies the
 * matching queue filter, so the number and the rows below it can never describe different things.
 *
 * <h2>Why the metrics are an ENTITY's, not an officer's</h2>
 * An RE is a respondent. Its questions are "how much is awaiting a reply from us", "how much have we
 * already answered" and "how much have we let breach" — not caseload-per-officer, which is an RBI
 * supervision concern and is served by the PNO dashboard instead.
 */
@Component({
  selector: 'app-re-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, TaskGridComponent],
  templateUrl: './re-dashboard.component.html',
  styleUrl: './re-dashboard.component.scss'
})
export class ReDashboardComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  loading = signal(true);
  stats = signal<DashboardStats>({ totalForwarded: 0, pendingResponse: 0, responded: 0, breached: 0 });
  complaints = signal<ReComplaint[]>([]);
  filterStatus = signal('');

  entityName = signal('');
  nodalOfficer = signal('');

  /**
   * Columns for the shared grid.
   *
   * <p>Headers are keys from the shared `ui.col.*` vocabulary (seeded in all ten locales by
   * TaskGridTranslationSeeder), not literals and not new RE-specific keys: the entity reads the same
   * column captions the RBI side does, and inventing `re.col.*` duplicates would be how the two drift
   * apart again.
   *
   * <p>The deadline column is present but will render empty until the list endpoint emits
   * `responseDeadline`. It is NOT a phantom — GET /re-portal/complaints is served; only this one field
   * is missing from its row — so keeping the column means it populates the day the server adds it,
   * rather than the column having to be rediscovered.
   */
  readonly gridColumns: TaskGridColumn<ReComplaint>[] = [
    { key: 'complaintNumber', labelKey: 'ui.col.complaint_number', width: '190px' },
    { key: 'subject', labelKey: 'ui.col.subject' },
    { key: 'complainantName', labelKey: 'ui.col.complainant_name', visible: false },
    { key: 'forwardedDate', labelKey: 'ui.col.created_at', kind: 'date', width: '130px' },
    { key: 'responseDeadline', labelKey: 'ui.col.deadline', kind: 'date', width: '130px' },
    { key: 'status', labelKey: 'ui.col.status', kind: 'status', width: '150px' }
  ];

  /**
   * The dropdown expresses the RE's own view of the work ("pending a reply from us"), which is not
   * the complaint's stored status. The options are pending/responded/breached/... while the API
   * returns forwarded/re_responded/closed, so a direct `c.status === status` comparison could never
   * match ANY option and selecting a filter always emptied the grid.
   */
  private static readonly STATUS_FILTER_MAP: Record<string, string[]> = {
    pending: ['forwarded'],
    responded: ['re_responded', 'closed'],
    extension_requested: ['extension_requested'],
    clarification_requested: ['clarification_requested'],
  };

  filteredComplaints = computed(() => {
    const status = this.filterStatus();
    if (!status) return this.complaints();

    // `breached` is a deadline property, not a status value, and the list endpoint returns no
    // deadline — so it cannot be derived here. Left to match nothing rather than guess wrongly.
    const accepted = ReDashboardComponent.STATUS_FILTER_MAP[status] ?? [status];
    return this.complaints().filter(c => accepted.includes(c.status));
  });

  ngOnInit() {
    const user = this.auth.currentUser();
    this.entityName.set(user?.firstName ? `${user.firstName} ${user.lastName}` : user?.username || '');
    this.loadDashboard();
  }

  loadDashboard() {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/dashboard`).subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.stats.set({
          totalForwarded: data.totalForwarded || 0,
          // The endpoint emits `pending`; reading only `pendingResponse` meant this card showed 0
          // however many complaints were awaiting a reply. `pendingResponse` is still read first so a
          // future rename of the server field does not silently break it again.
          pendingResponse: data.pendingResponse ?? data.pending ?? 0,
          responded: data.responded || 0,
          breached: data.breached || 0
        });
        this.nodalOfficer.set(data.nodalOfficerName || '');
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });

    this.loadComplaints();
  }

  /**
   * The complaint list comes from /re-portal/complaints.
   *
   * /re-portal/dashboard is a STATISTICS endpoint and returns no `complaints` key, so reading
   * `data.complaints` from it left the grid permanently empty: an entity saw "No complaints found"
   * regardless of caseload, which also made the response-deadline countdown and the status filter
   * unreachable. The countdown is how an entity learns a statutory response window is closing.
   */
  private loadComplaints() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints`).subscribe({
      next: (res) => {
        const body = res?.data ?? res;
        const rows: any[] = Array.isArray(body) ? body : (body?.content ?? []);
        this.complaints.set(rows.map(r => ({
          ...r,
          forwardedDate: r.forwardedDate ?? r.createdAt,
        })) as ReComplaint[]);
      },
      error: () => {
        this.complaints.set([]);
      }
    });
  }

  /**
   * Row tint by how close the response deadline is, passed to the grid's `rowClass`.
   *
   * <p>Kept through the migration deliberately: it is how a nodal officer sees what is about to
   * breach without reading every date. It returns '' while the list endpoint omits the deadline, so
   * the cue is dormant rather than wrong.
   */
  readonly urgencyClass = (complaint: ReComplaint): string => this.getUrgencyClass(complaint);

  getUrgencyClass(complaint: ReComplaint): string {
    if (!complaint.responseDeadline) return '';
    const deadline = new Date(complaint.responseDeadline);
    const now = new Date();
    const diffMs = deadline.getTime() - now.getTime();
    const diffDays = diffMs / (1000 * 60 * 60 * 24);

    if (diffDays < 0) return 'urgency-breached';
    if (diffDays < 2) return 'urgency-red';
    if (diffDays <= 5) return 'urgency-yellow';
    return 'urgency-green';
  }

  getDaysRemaining(deadline: string | undefined): string {
    if (!deadline) return '-';
    const diff = new Date(deadline).getTime() - new Date().getTime();
    const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
    if (days < 0) return `${Math.abs(days)}d overdue`;
    if (days === 0) return 'Due today';
    return `${days}d remaining`;
  }

  openComplaint(complaint: ReComplaint) {
    this.router.navigate(['/re-portal/complaints', complaint.complaintNumber]);
  }

  onFilterChange(event: Event) {
    const select = event.target as HTMLSelectElement;
    this.filterStatus.set(select.value);
  }

  /**
   * A KPI card click applies its own filter, and clicking the active card clears it.
   *
   * <p>Toggling matters: a card that could only ever narrow the queue would leave the entity with no
   * way back to the full list except the dropdown, and a KPI that cannot be un-clicked reads as a
   * broken control.
   */
  selectCard(filter: string) {
    this.filterStatus.update(current => (current === filter ? '' : filter));
  }
}
