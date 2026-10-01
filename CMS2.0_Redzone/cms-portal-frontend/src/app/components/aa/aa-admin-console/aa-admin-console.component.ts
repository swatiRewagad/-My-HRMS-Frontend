import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AppShellComponent } from '../../shared/app-shell/app-shell.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

interface PoolOfficer {
  id: number;
  userId: string;
  displayName: string;
  roleGroup: string;
  regionalOffice: string | null;
  active: boolean;
  onLeave: boolean;
  threshold: number;
  unlimited: boolean;
  currentWorkload: number;
  skillLanguages: string | null;
  eligible: boolean;
  atThreshold: boolean;
}

interface DeactivationPreview {
  officers: { userId: string; displayName: string; pendingCount: number; pendingAppeals: string[] }[];
  totalPending: number;
  requiresConfirmation: boolean;
  messageKey: string;
}

interface PendingRequest {
  id: number;
  appealNumber: string;
  fromUserId: string;
  toUserId: string | null;
  reason: string;
  requestedBy: string;
  requestedAt: string;
}

/**
 * AA Admin console: officer pool, per-officer workload limits, bulk activation, reassignment approvals.
 *
 * Class name, selector and file paths are unchanged from the pre-created placeholder because
 * app.routes.ts already points at them and that file must not be edited.
 *
 * The route is guarded to AA_ADMIN, but this screen is only the presentation of that rule -- every
 * action here is authorised again server-side, since hiding a button is not a control.
 *
 * Style follows team-management.component: signals, inject(), hand-rolled table and modal markup, and a
 * data-testid on every interactive element. Deliberately no PrimeNG: exactly one component in this
 * frontend imports it, so introducing p-table/p-dialog here would invent a new convention.
 */
@Component({
  selector: 'app-aa-admin-console',
  standalone: true,
  imports: [CommonModule, FormsModule, AppShellComponent, TranslatePipe],
  templateUrl: './aa-admin-console.component.html',
  styleUrl: './aa-admin-console.component.scss'
})
export class AaAdminConsoleComponent implements OnInit {
  private http = inject(HttpClient);

  private readonly base = `${environment.apiBaseUrl}/api/v1/aa`;

  heading = signal('aa.admin.console_title');
  officers = signal<PoolOfficer[]>([]);
  pendingRequests = signal<PendingRequest[]>([]);
  loading = signal(true);
  error = signal('');
  notice = signal('');
  roleGroup = signal('AA_DO');

  selectedIds = signal<Set<string>>(new Set());

  thresholdTarget = signal<PoolOfficer | null>(null);
  thresholdValue = signal(0);
  thresholdReason = signal('');
  savingThreshold = signal(false);

  // Story 10: the warning is shown BEFORE deactivation is finalised, and the admin can cancel.
  deactivatePreview = signal<DeactivationPreview | null>(null);
  deactivateReason = signal('');
  applyingBulk = signal(false);

  roleGroups = ['AA_DO', 'AA_REVIEWER', 'AA_SECRETARIAT'];

  eligibleCount = computed(() => this.officers().filter(o => o.eligible).length);
  atThresholdCount = computed(() => this.officers().filter(o => o.atThreshold).length);
  onLeaveCount = computed(() => this.officers().filter(o => o.onLeave).length);
  totalWorkload = computed(() =>
    this.officers().reduce((sum, o) => sum + o.currentWorkload, 0));

  allSelected = computed(() => {
    const list = this.officers();
    return list.length > 0 && list.every(o => this.selectedIds().has(o.userId));
  });

  selectedCount = computed(() => this.selectedIds().size);

  ngOnInit(): void {
    this.loadPool();
    this.loadPendingRequests();
  }

  loadPool(): void {
    this.loading.set(true);
    this.error.set('');
    this.http.get<PoolOfficer[]>(`${this.base}/assignment/pool?roleGroup=${this.roleGroup()}`)
      .subscribe({
        next: officers => {
          this.officers.set(officers);
          // Selections are cleared on reload: a stale selection could apply a bulk action to an
          // officer the admin can no longer see.
          this.selectedIds.set(new Set());
          this.loading.set(false);
        },
        error: err => {
          // No mock-data fallback. team-management silently falls back to fixtures on error, which
          // makes a broken endpoint look like a working screen with wrong numbers -- dangerous here,
          // where the numbers decide who receives citizen work.
          this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown');
          this.officers.set([]);
          this.loading.set(false);
        }
      });
  }

  loadPendingRequests(): void {
    this.http.get<PendingRequest[]>(`${this.base}/reassignment/pending`).subscribe({
      next: requests => this.pendingRequests.set(requests),
      error: () => this.pendingRequests.set([])
    });
  }

  changeRoleGroup(group: string): void {
    this.roleGroup.set(group);
    this.loadPool();
  }

  toggleSelection(userId: string): void {
    const next = new Set(this.selectedIds());
    if (next.has(userId)) {
      next.delete(userId);
    } else {
      next.add(userId);
    }
    this.selectedIds.set(next);
  }

  toggleSelectAll(): void {
    this.selectedIds.set(this.allSelected()
      ? new Set()
      : new Set(this.officers().map(o => o.userId)));
  }

  isSelected(userId: string): boolean {
    return this.selectedIds().has(userId);
  }

  openThresholdDialog(officer: PoolOfficer): void {
    this.thresholdTarget.set(officer);
    this.thresholdValue.set(officer.threshold);
    this.thresholdReason.set('');
    this.notice.set('');
  }

  closeThresholdDialog(): void {
    this.thresholdTarget.set(null);
    this.savingThreshold.set(false);
  }

  saveThreshold(): void {
    const officer = this.thresholdTarget();
    if (!officer) {
      return;
    }
    this.savingThreshold.set(true);
    this.http.put<{ messageKey: string }>(
      `${this.base}/assignment/pool/${officer.userId}/threshold`,
      { threshold: this.thresholdValue(), reason: this.thresholdReason() }
    ).subscribe({
      next: result => {
        this.notice.set(result.messageKey);
        this.closeThresholdDialog();
        this.loadPool();
      },
      error: err => {
        this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown');
        this.savingThreshold.set(false);
      }
    });
  }

  /** Story 5 + 10: deactivation is previewed first so pending work is visible before it is applied. */
  startBulkDeactivate(): void {
    if (this.selectedCount() === 0) {
      this.error.set('aa.pool.error_no_officers_selected');
      return;
    }
    this.error.set('');
    this.deactivateReason.set('');
    this.http.post<DeactivationPreview>(`${this.base}/assignment/pool/deactivation-preview`,
      { userIds: [...this.selectedIds()] }
    ).subscribe({
      next: preview => this.deactivatePreview.set(preview),
      error: err => this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown')
    });
  }

  cancelDeactivate(): void {
    this.deactivatePreview.set(null);
    this.applyingBulk.set(false);
  }

  confirmDeactivate(): void {
    const preview = this.deactivatePreview();
    if (!preview) {
      return;
    }
    this.applyingBulk.set(true);
    this.http.post<{ messageKey: string }>(`${this.base}/assignment/pool/bulk-activation`, {
      userIds: [...this.selectedIds()],
      active: false,
      // The server requires this when pending work exists; the dialog the admin just saw IS the
      // confirmation, and the server re-checks rather than trusting the flag blindly.
      confirmed: true,
      reason: this.deactivateReason()
    }).subscribe({
      next: result => {
        this.notice.set(result.messageKey);
        this.cancelDeactivate();
        this.loadPool();
      },
      error: err => {
        this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown');
        this.applyingBulk.set(false);
      }
    });
  }

  bulkActivate(): void {
    if (this.selectedCount() === 0) {
      this.error.set('aa.pool.error_no_officers_selected');
      return;
    }
    this.applyingBulk.set(true);
    this.http.post<{ messageKey: string }>(`${this.base}/assignment/pool/bulk-activation`, {
      userIds: [...this.selectedIds()],
      active: true,
      confirmed: true,
      reason: 'bulk activation from AA admin console'
    }).subscribe({
      next: result => {
        this.notice.set(result.messageKey);
        this.applyingBulk.set(false);
        this.loadPool();
      },
      error: err => {
        this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown');
        this.applyingBulk.set(false);
      }
    });
  }

  /** Story 7: admin-triggered rebalance, for when auto-rebalance is off. */
  rebalance(): void {
    this.http.post<{ messageKey: string; recordsMoved: number }>(
      `${this.base}/assignment/pool/rebalance`,
      { roleGroup: this.roleGroup(), reason: 'manual rebalance from AA admin console' }
    ).subscribe({
      next: result => {
        this.notice.set(result.messageKey);
        this.loadPool();
      },
      error: err => this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown')
    });
  }

  decideRequest(requestId: number, approve: boolean): void {
    const action = approve ? 'approve' : 'reject';
    this.http.post<{ messageKey: string }>(
      `${this.base}/reassignment/${requestId}/${action}`, { comment: '' }
    ).subscribe({
      next: result => {
        this.notice.set(result.messageKey);
        this.loadPendingRequests();
        this.loadPool();
      },
      error: err => this.error.set(err?.error?.messageKey || 'aa.pool.error_unknown')
    });
  }

  statusKeyFor(officer: PoolOfficer): string {
    if (!officer.active) {
      return 'aa.admin.status_inactive';
    }
    if (officer.onLeave) {
      return 'aa.admin.status_on_leave';
    }
    return 'aa.admin.status_active';
  }

  dismissNotice(): void {
    this.notice.set('');
    this.error.set('');
  }
}
