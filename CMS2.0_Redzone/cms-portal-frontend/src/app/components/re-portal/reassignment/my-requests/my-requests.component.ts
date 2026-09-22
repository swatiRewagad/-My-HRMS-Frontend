import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { TranslatePipe } from '../../../../pipes/translate.pipe';
import {
  ReassignmentService,
  ReassignmentRequestRow,
  ReassignmentClarificationRow
} from '../../../../services/reassignment.service';

/**
 * My reassignment requests (UST842) with append-only clarifications (UST840).
 *
 * The original reason is rendered read-only and there is deliberately no edit affordance for it: the
 * server refuses a change, so offering one would only produce a failure the user could not act on.
 * Context is added as a clarification instead, which is why the notice sits beside the reason rather
 * than beside the clarification box — it explains an absence, and an absence needs explaining where
 * the user looks for the missing control.
 *
 * Errors are surfaced as the server's `messageKey`, not its prose, so a Hindi reader sees the reason
 * in Hindi. A key that fails to resolve falls back to the local load-failure key rather than to raw
 * English.
 */
@Component({
  selector: 'app-re-my-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, StatusBadgeComponent, TranslatePipe],
  templateUrl: './my-requests.component.html',
  styleUrl: './my-requests.component.scss'
})
export class MyRequestsComponent implements OnInit {
  private reassignment = inject(ReassignmentService);

  loading = signal(true);
  /** Holds a translation key, never prose — the template renders it through the pipe. */
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

  /** Only one row is expanded at a time, so the clarification thread is unambiguous. */
  expandedId = signal<number | null>(null);
  clarifications = signal<ReassignmentClarificationRow[]>([]);
  clarificationsLoading = signal(false);
  clarificationDraft = signal('');
  savingClarification = signal(false);

  /**
   * Two-click confirmation. There is no ConfirmDialog in this codebase, and a native confirm() is
   * unlocalisable — it would show an English browser string to every locale.
   */
  withdrawConfirmId = signal<number | null>(null);
  withdrawing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadError.set('');
    this.reassignment.myRequests({ page: this.page(), size: this.size() }).subscribe({
      next: (res) => {
        this.requests.set(res.requests ?? []);
        this.totalElements.set(res.totalElements ?? 0);
        this.totalPages.set(res.totalPages ?? 0);
        this.page.set(res.page ?? 0);
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
    this.collapse();
    this.load();
  }

  nextPage(): void {
    if (!this.hasNext()) {
      return;
    }
    this.page.update(p => p + 1);
    this.collapse();
    this.load();
  }

  toggleExpanded(request: ReassignmentRequestRow): void {
    if (this.expandedId() === request.id) {
      this.collapse();
      return;
    }
    this.expandedId.set(request.id);
    this.clarificationDraft.set('');
    this.actionError.set('');
    this.actionSuccess.set('');
    this.loadClarifications(request.id);
  }

  private collapse(): void {
    this.expandedId.set(null);
    this.clarifications.set([]);
    this.clarificationDraft.set('');
  }

  private loadClarifications(requestId: number): void {
    this.clarificationsLoading.set(true);
    this.clarifications.set([]);
    this.reassignment.getClarifications(requestId).subscribe({
      next: (res) => {
        this.clarifications.set(res.clarifications ?? []);
        this.clarificationsLoading.set(false);
      },
      error: (err: unknown) => {
        this.actionError.set(this.messageKeyOf(err));
        this.clarificationsLoading.set(false);
      }
    });
  }

  addClarification(requestId: number): void {
    const note = this.clarificationDraft().trim();
    if (!note || this.savingClarification()) {
      return;
    }
    this.savingClarification.set(true);
    this.actionError.set('');
    this.actionSuccess.set('');
    this.reassignment.addClarification(requestId, note).subscribe({
      next: (res) => {
        // Appended locally as well as reloaded, so the thread never appears to lose the note that
        // was just written if the list call is slower than the write.
        this.clarifications.update(list => [...list, res.clarification]);
        this.clarificationDraft.set('');
        this.savingClarification.set(false);
        this.loadClarifications(requestId);
      },
      error: (err: unknown) => {
        this.actionError.set(this.messageKeyOf(err));
        this.savingClarification.set(false);
      }
    });
  }

  canWithdraw(request: ReassignmentRequestRow): boolean {
    return request.status === 'PENDING';
  }

  askWithdraw(request: ReassignmentRequestRow): void {
    this.actionError.set('');
    this.actionSuccess.set('');
    this.withdrawConfirmId.set(request.id);
  }

  cancelWithdraw(): void {
    this.withdrawConfirmId.set(null);
  }

  confirmWithdraw(request: ReassignmentRequestRow): void {
    if (this.withdrawing()) {
      return;
    }
    this.withdrawing.set(true);
    this.actionError.set('');
    this.reassignment.withdraw(request.id).subscribe({
      next: (res) => {
        // The server returns the updated row; trusting it rather than assuming WITHDRAWN keeps the
        // badge honest if the status ends up being something else.
        this.requests.update(list => list.map(r => (r.id === request.id ? res.request : r)));
        this.withdrawConfirmId.set(null);
        this.withdrawing.set(false);
        this.load();
      },
      error: (err: unknown) => {
        this.actionError.set(this.messageKeyOf(err));
        this.withdrawConfirmId.set(null);
        this.withdrawing.set(false);
      }
    });
  }

  /**
   * Pulls the server's translation key out of an error body. Falls back to the generic load-failure
   * key so a transport error (no body at all) still renders localised text.
   */
  private messageKeyOf(err: unknown): string {
    const body = (err as { error?: { messageKey?: string } | null } | null)?.error;
    const key = body?.messageKey;
    return key && key.length > 0 ? key : 're.reassign.error.load_failed';
  }
}
