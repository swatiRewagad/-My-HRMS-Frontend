import {
  Component, inject, input, output, signal, computed, effect, untracked, OnDestroy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '../../../../pipes/translate.pipe';
import { ReassignmentService, ReassignmentCandidate, ReRole } from '../../../../services/reassignment.service';

/**
 * RE nodal officer reassignment request dialog (UST838, UST840, UST841).
 *
 * The workload shown against each candidate is the server's count of open records, not a local
 * guess, so the officer choosing a colleague sees the same number the approver will see.
 *
 * The reason is immutable once submitted (UST840) — the notice next to the field says so, and the
 * server refuses an edit, so there is no in-place correction to offer here. Server rejections come
 * back as a `messageKey`, which is rendered through the translate pipe rather than shown raw.
 */

interface RoleOption {
  value: ReRole;
  labelKey: string;
}

interface ServerErrorBody {
  messageKey?: string;
}

@Component({
  selector: 'app-reassignment-popup',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './reassignment-popup.component.html',
  styleUrl: './reassignment-popup.component.scss'
})
export class ReassignmentPopupComponent implements OnDestroy {
  private reassignmentService = inject(ReassignmentService);

  recordId = input.required<number>();
  open = input<boolean>(false);

  closed = output<void>();
  submitted = output<void>();

  loading = signal(false);
  submitting = signal(false);
  /** Holds a translation key, never server prose. */
  error = signal('');

  candidates = signal<ReassignmentCandidate[]>([]);
  roleFilter = signal<ReRole | ''>('');
  search = signal('');
  selectedUserId = signal('');
  reason = signal('');

  readonly roleOptions: readonly RoleOption[] = [
    { value: 'NODAL_OFFICER', labelKey: 're.reassign.role.nodal_officer' },
    { value: 'CONTACT_PERSON', labelKey: 're.reassign.role.contact_person' },
    { value: 'PNO', labelKey: 're.reassign.role.pno' }
  ];

  private readonly roleLabelKeys: Record<ReRole, string> = {
    NODAL_OFFICER: 're.reassign.role.nodal_officer',
    CONTACT_PERSON: 're.reassign.role.contact_person',
    PNO: 're.reassign.role.pno'
  };

  canSubmit = computed(() =>
    !this.submitting() && this.selectedUserId() !== '' && this.reason().trim() !== '');

  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  /**
   * Opening is the trigger to load. The filters are read with `untracked` so that changing a filter
   * does not re-run this effect as well as the explicit reload the filter handler already does.
   */
  private readonly openEffect = effect(() => {
    const isOpen = this.open();
    const recordId = this.recordId();
    untracked(() => {
      if (isOpen) {
        this.resetForm();
        this.load(recordId);
      }
    });
  });

  ngOnDestroy(): void {
    this.clearSearchTimer();
  }

  load(recordId: number): void {
    this.loading.set(true);
    this.error.set('');
    this.reassignmentService.getCandidates({
      recordId,
      role: this.roleFilter(),
      search: this.search().trim()
    }).subscribe({
      next: (res) => {
        this.candidates.set(res.candidates ?? []);
        // A candidate that dropped out of the filtered list must not stay selected.
        if (!this.candidates().some(c => c.userId === this.selectedUserId())) {
          this.selectedUserId.set('');
        }
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.candidates.set([]);
        this.selectedUserId.set('');
        this.error.set(this.messageKeyOf(err));
        this.loading.set(false);
      }
    });
  }

  onRoleChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.roleFilter.set(select.value as ReRole | '');
    this.clearSearchTimer();
    this.load(this.recordId());
  }

  /** Debounced so typing a name does not fire a request per keystroke. */
  onSearchInput(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.search.set(input.value);
    this.clearSearchTimer();
    this.searchTimer = setTimeout(() => {
      this.searchTimer = null;
      this.load(this.recordId());
    }, 300);
  }

  onReasonInput(event: Event): void {
    const textarea = event.target as HTMLTextAreaElement;
    this.reason.set(textarea.value);
  }

  select(candidate: ReassignmentCandidate): void {
    this.selectedUserId.set(candidate.userId);
  }

  roleLabelKey(role: ReRole): string {
    return this.roleLabelKeys[role];
  }

  submit(): void {
    const toUserId = this.selectedUserId();
    const reason = this.reason().trim();
    if (toUserId === '' || reason === '' || this.submitting()) {
      return;
    }
    this.submitting.set(true);
    this.error.set('');
    this.reassignmentService.raiseRequest({ recordId: this.recordId(), toUserId, reason }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.submitted.emit();
        this.close();
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(this.messageKeyOf(err));
      }
    });
  }

  close(): void {
    this.clearSearchTimer();
    this.closed.emit();
  }

  private resetForm(): void {
    this.candidates.set([]);
    this.selectedUserId.set('');
    this.reason.set('');
    this.search.set('');
    this.roleFilter.set('');
    this.error.set('');
    this.submitting.set(false);
  }

  private clearSearchTimer(): void {
    if (this.searchTimer !== null) {
      clearTimeout(this.searchTimer);
      this.searchTimer = null;
    }
  }

  private messageKeyOf(err: HttpErrorResponse): string {
    const body = err.error as ServerErrorBody | null;
    return body?.messageKey ?? 're.reassign.error.load_failed';
  }
}
