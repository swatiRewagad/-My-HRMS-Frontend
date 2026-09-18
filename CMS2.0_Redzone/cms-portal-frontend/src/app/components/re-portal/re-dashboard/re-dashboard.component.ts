import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';

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
   * has no source on this endpoint, so the deadline column renders '-' rather than a wrong date.
   * Both getters already guard against a missing value. Surfacing the real forwarded-at and the
   * statutory response deadline needs a server change (see the session report).
   */
  forwardedDate?: string;
  responseDeadline?: string;
  category?: string;
}

@Component({
  selector: 'app-re-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent],
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
}
