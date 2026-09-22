import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { ReassignmentService, OfficerWorkload } from '../../../services/reassignment.service';

/**
 * The Principal Nodal Officer's view of their own entity (UST838 + UST844).
 *
 * The team workload numbers are rendered exactly as the API returns them, with no client-side
 * arithmetic. UST838 requires the figure here to equal the one in the reassignment popup, and the two
 * are read side by side when a PNO decides where a record should go. Both surfaces are served by the
 * same backend service method, so the only way they can disagree is if a caller re-derives the count
 * locally — hence nothing here adds, filters or adjusts a workload. Even the total is the server's
 * `totalActiveRecords` rather than a sum of the rows.
 *
 * The workload endpoint returns user ids and no display names, so the officer column shows the id.
 * That is honest; inventing a name, or borrowing one from the history summary for a partially
 * overlapping set of officers, would attach the wrong person to a number a PNO acts on.
 *
 * Every failure is held as a translation key — either the server's own `messageKey` or the generic
 * load-failure key — so a non-PNO who reaches this page reads a reason in their own locale instead of
 * seeing an empty screen.
 */

/** Row shape of the history summary, whose arrays the service types inline. */
interface HistorySummaryRow {
  userId: string;
  displayName: string;
  count: number;
}

/** The inbound and outbound halves of the summary joined into one row per officer. */
interface HistorySummaryRow2Way {
  userId: string;
  displayName: string;
  inbound: number;
  outbound: number;
}

@Component({
  selector: 'app-re-pno-dashboard',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './pno-dashboard.component.html',
  styleUrl: './pno-dashboard.component.scss'
})
export class PnoDashboardComponent implements OnInit {
  private reassignment = inject(ReassignmentService);

  // ═══ Team workload (UST838) ═══
  workloadLoading = signal(true);
  /** Holds a translation key, never server prose. */
  workloadError = signal('');
  officers = signal<OfficerWorkload[]>([]);
  totalActiveRecords = signal(0);

  /** Most loaded first — that is the officer a PNO is looking for. Ties keep a stable order. */
  sortedOfficers = computed(() =>
    [...this.officers()].sort((a, b) => b.workload - a.workload || a.userId.localeCompare(b.userId)));

  // ═══ Approvals awaiting this PNO (UST843) ═══
  approvalsLoading = signal(true);
  approvalsError = signal('');
  pendingApprovalCount = signal(0);

  // ═══ Reassignment history summary (UST844) ═══
  historyLoading = signal(true);
  historyError = signal('');
  outbound = signal<HistorySummaryRow[]>([]);
  inbound = signal<HistorySummaryRow[]>([]);
  totalMoves = signal(0);

  hasHistory = computed(() => this.outbound().length > 0 || this.inbound().length > 0);

  /**
   * One row per officer with both directions, joined on userId.
   *
   * The two arrays are independent — an officer who only ever received records appears in `inbound`
   * only — so a naive parallel render would misalign the columns. An officer absent from one side has
   * genuinely moved nothing that way, which is a real zero rather than missing data.
   */
  historyRows = computed<HistorySummaryRow2Way[]>(() => {
    const rows = new Map<string, HistorySummaryRow2Way>();
    const upsert = (row: HistorySummaryRow): HistorySummaryRow2Way => {
      const existing = rows.get(row.userId);
      if (existing) {
        return existing;
      }
      const created: HistorySummaryRow2Way = {
        userId: row.userId,
        displayName: row.displayName,
        inbound: 0,
        outbound: 0
      };
      rows.set(row.userId, created);
      return created;
    };
    for (const row of this.inbound()) {
      upsert(row).inbound = row.count;
    }
    for (const row of this.outbound()) {
      upsert(row).outbound = row.count;
    }
    // Busiest officer first, matching the workload table's ordering habit.
    return [...rows.values()].sort(
      (a, b) => (b.inbound + b.outbound) - (a.inbound + a.outbound) || a.userId.localeCompare(b.userId));
  });

  ngOnInit(): void {
    this.loadWorkload();
    this.loadPendingApprovals();
    this.loadHistorySummary();
  }

  loadWorkload(): void {
    this.workloadLoading.set(true);
    this.workloadError.set('');
    this.reassignment.getWorkload().subscribe({
      next: (res) => {
        this.officers.set(res.officers ?? []);
        // The server's own total, not a sum of the rows: re-adding them here would be a second,
        // divergable definition of the same number.
        this.totalActiveRecords.set(res.totalActiveRecords ?? 0);
        this.workloadLoading.set(false);
      },
      error: (err: unknown) => {
        this.officers.set([]);
        this.totalActiveRecords.set(0);
        this.workloadError.set(this.messageKeyOf(err));
        this.workloadLoading.set(false);
      }
    });
  }

  loadPendingApprovals(): void {
    this.approvalsLoading.set(true);
    this.approvalsError.set('');
    // Only the tally is wanted here, so the smallest page is fetched and `totalElements` is read —
    // counting the returned rows would report the page size, not the queue depth.
    this.reassignment.pendingApprovals({ page: 0, size: 1 }).subscribe({
      next: (res) => {
        this.pendingApprovalCount.set(res.totalElements ?? 0);
        this.approvalsLoading.set(false);
      },
      error: (err: unknown) => {
        this.pendingApprovalCount.set(0);
        this.approvalsError.set(this.messageKeyOf(err));
        this.approvalsLoading.set(false);
      }
    });
  }

  loadHistorySummary(): void {
    this.historyLoading.set(true);
    this.historyError.set('');
    this.reassignment.historySummary().subscribe({
      next: (res) => {
        this.outbound.set(res.outbound ?? []);
        this.inbound.set(res.inbound ?? []);
        this.totalMoves.set(res.totalMoves ?? 0);
        this.historyLoading.set(false);
      },
      error: (err: unknown) => {
        this.outbound.set([]);
        this.inbound.set([]);
        this.totalMoves.set(0);
        this.historyError.set(this.messageKeyOf(err));
        this.historyLoading.set(false);
      }
    });
  }

  /**
   * A 403 for a non-PNO carries its own `messageKey` (`re.reassign.error.pno_only`); anything without
   * one falls back to the generic load-failure key so the page never renders blank.
   */
  private messageKeyOf(err: unknown): string {
    const body = (err as { error?: { messageKey?: string } | null } | null)?.error;
    const key = body?.messageKey;
    return key && key.length > 0 ? key : 're.reassign.error.load_failed';
  }
}
