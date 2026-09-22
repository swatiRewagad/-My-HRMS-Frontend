import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { TranslatePipe } from '../../../../pipes/translate.pipe';
import {
  ReassignmentService,
  ReassignmentRequestRow,
  BulkFailure,
  BulkItem
} from '../../../../services/reassignment.service';

/**
 * The PNO approval queue (UST843) over the conflict-safe bulk endpoint (UST839).
 *
 * The endpoint answers 200 even when individual items failed, because the action genuinely partially
 * succeeded. Collapsing that into "saved" would hide records that did not move, and collapsing it
 * into "failed" would tell the PNO to redo work that is already done — either way they would act on a
 * false picture. So the per-item outcome is kept and rendered: the succeeded ids drop out of the
 * queue on reload, and each failure is listed with its own `messageKey` translated.
 *
 * Selection survives a partial failure on purpose: the still-failing rows stay ticked so a retry is
 * one click, while the succeeded ones are dropped since they are no longer pending.
 */
@Component({
  selector: 'app-re-pno-approvals',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent, TranslatePipe],
  templateUrl: './pno-approvals.component.html',
  styleUrl: './pno-approvals.component.scss'
})
export class PnoApprovalsComponent implements OnInit {
  private reassignment = inject(ReassignmentService);

  loading = signal(true);
  /** Every one of these holds a translation key, never server prose. */
  loadError = signal('');
  actionError = signal('');
  actionSuccess = signal('');

  requests = signal<ReassignmentRequestRow[]>([]);

  page = signal(0);
  size = signal(10);
  totalElements = signal(0);
  totalPages = signal(0);

  hasPrev = computed(() => this.page() > 0);
  hasNext = computed(() => this.page() + 1 < this.totalPages());

  selectedIds = signal<number[]>([]);
  selectedCount = computed(() => this.selectedIds().length);
  hasSelection = computed(() => this.selectedIds().length > 0);

  allSelected = computed(() => {
    const rows = this.requests();
    if (rows.length === 0) {
      return false;
    }
    const selected = new Set(this.selectedIds());
    return rows.every(r => selected.has(r.id));
  });

  decisionComment = signal('');
  deciding = signal(false);

  /** Inline confirmation for the destructive half; approve needs none. */
  confirmingBulkReject = signal(false);

  /** Per-item outcome of the last bulk call, kept so a partial result stays visible. */
  lastFailures = signal<BulkFailure[]>([]);
  lastSucceededCount = signal(0);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.reassignment.pendingApprovals({ page: this.page(), size: this.size() }).subscribe({
      next: (res) => {
        const rows = res.requests ?? [];
        this.requests.set(rows);
        this.totalElements.set(res.totalElements ?? 0);
        this.totalPages.set(res.totalPages ?? 0);
        this.page.set(res.page ?? 0);
        // Anything no longer in the queue was decided, so keeping it selected would let a retry
        // target a request that is already resolved.
        const visible = new Set(rows.map(r => r.id));
        this.selectedIds.update(ids => ids.filter(id => visible.has(id)));
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.requests.set([]);
        this.loadError.set(this.messageKeyOf(err));
        this.loading.set(false);
      }
    });
  }

  prevPage(): void {
    if (!this.hasPrev()) {
      return;
    }
    this.page.update(p => p - 1);
    this.load();
  }

  nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update(p => p + 1);
    this.load();
  }

  isSelected(id: number): boolean {
    return this.selectedIds().includes(id);
  }

  toggleRow(id: number): void {
    this.selectedIds.update(ids =>
      ids.includes(id) ? ids.filter(existing => existing !== id) : [...ids, id]);
    this.confirmingBulkReject.set(false);
  }

  toggleAll(): void {
    this.selectedIds.set(this.allSelected() ? [] : this.requests().map(r => r.id));
    this.confirmingBulkReject.set(false);
  }

  /** Rejection without a comment is refused server-side; the button is blocked to match. */
  canReject = computed(() => this.hasSelection() && this.decisionComment().trim().length > 0);

  approveSelected(): void {
    if (!this.hasSelection()) {
      return;
    }
    this.decide(true);
  }

  askRejectSelected(): void {
    if (!this.canReject()) {
      // Surfaces the same key the server would return, so the user reads one explanation for the
      // rule rather than a different local wording.
      this.actionError.set('re.reassign.error.rejection_comment_required');
      return;
    }
    this.actionError.set('');
    this.confirmingBulkReject.set(true);
  }

  cancelRejectSelected(): void {
    this.confirmingBulkReject.set(false);
  }

  confirmRejectSelected(): void {
    if (!this.canReject()) {
      return;
    }
    this.confirmingBulkReject.set(false);
    this.decide(false);
  }

  /** Single-row approve/reject goes through the same bulk endpoint — one code path, one result shape. */
  approveOne(request: ReassignmentRequestRow): void {
    this.selectedIds.set([request.id]);
    this.decide(true);
  }

  private decide(approve: boolean): void {
    if (this.deciding()) {
      return;
    }
    const items: BulkItem[] = this.selectedIds().map(id => ({ requestId: id }));
    if (items.length === 0) {
      return;
    }

    const comment = this.decisionComment().trim();
    if (!approve && comment.length === 0) {
      this.actionError.set('re.reassign.error.rejection_comment_required');
      return;
    }

    this.deciding.set(true);
    this.actionError.set('');
    this.actionSuccess.set('');
    this.lastFailures.set([]);
    this.lastSucceededCount.set(0);

    this.reassignment.decide({ approve, items, comment: comment.length > 0 ? comment : undefined }).subscribe({
      next: (result) => {
        const failures = result.failed ?? [];
        this.lastFailures.set(failures);
        this.lastSucceededCount.set(result.succeededCount ?? 0);

        // Reported exactly as it happened: a mixed outcome gets the partial key AND the failure list,
        // never the all-succeeded key.
        if (failures.length === 0) {
          this.actionSuccess.set('re.reassign.bulk_all_succeeded');
          this.actionError.set('');
        } else {
          this.actionSuccess.set('');
          this.actionError.set('re.reassign.bulk_partial_success');
        }

        // Retrying the failures should not re-send the ones that worked.
        const succeeded = new Set(result.succeeded ?? []);
        this.selectedIds.update(ids => ids.filter(id => !succeeded.has(id)));
        if (failures.length === 0) {
          this.decisionComment.set('');
        }

        this.deciding.set(false);
        this.load();
      },
      error: (err: unknown) => {
        // A non-200 is a whole-call failure: nothing was applied, so no per-item summary is shown.
        this.actionError.set(this.messageKeyOf(err));
        this.deciding.set(false);
      }
    });
  }

  /** The complaint number reads better than a bare id when naming a failed item. */
  labelFor(id: number): string {
    const match = this.requests().find(r => r.id === id);
    return match ? match.complaintNumber : String(id);
  }

  private messageKeyOf(err: unknown): string {
    const body = (err as { error?: { messageKey?: string } | null } | null)?.error;
    const key = body?.messageKey;
    return key && key.length > 0 ? key : 're.reassign.error.load_failed';
  }
}
