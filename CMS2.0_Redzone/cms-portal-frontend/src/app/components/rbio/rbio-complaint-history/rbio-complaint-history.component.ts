import { Component, Input, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import {
  ComplaintCorrespondenceService, TimelineEntry
} from '../../../services/complaint-correspondence.service';

/**
 * The complaint History tab: every status and milestone change, oldest first (UST594-596).
 *
 * <p>REPLACES A TAB THAT COULD NEVER SHOW ANYTHING. The History tab rendered only
 * {@code <app-rbio-action-override-history>}, whose service requested
 * {@code /api/v1/complaints/{id}/action-override} — a route with ZERO occurrences in cms-backend — and
 * swallowed the resulting 404 via {@code catchError(() => of([]))}. So it displayed "No action overrides
 * recorded for this complaint" for every complaint in the system, indistinguishably from a genuinely
 * empty history.
 *
 * <p>Reads COMPLAINT_TIMELINE, where status changes have always been written.
 *
 * <p>A NEW component rather than reusing {@code cepc-timeline.component.ts}: that one hardcodes English
 * labels with no translate pipe, binds a {@code documents[]} field no endpoint returns, and has no slot
 * for the old/new owner, closure clause or destination office that UST596 requires.
 */
@Component({
  selector: 'app-rbio-complaint-history',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './rbio-complaint-history.component.html',
  styleUrl: './rbio-complaint-history.component.scss'
})
export class RbioComplaintHistoryComponent implements OnInit {

  /** The complaint number; the history endpoint is keyed by number, not id. */
  @Input() complaintNumber: string | null = null;

  private service = inject(ComplaintCorrespondenceService);

  entries = signal<TimelineEntry[]>([]);
  loading = signal(false);
  /** A translation key, never English prose — this renders directly to staff. */
  error = signal<string | null>(null);

  /** Signals, not plain fields: a computed() that reads a plain field never re-evaluates. */
  showAutomatic = signal(true);

  visibleEntries = computed(() => this.showAutomatic()
    ? this.entries()
    : this.entries().filter(e => e.eventSource !== 'AUTOMATIC'));

  ngOnInit() {
    this.load();
  }

  load() {
    if (!this.complaintNumber) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);

    this.service.getHistory(this.complaintNumber).subscribe({
      next: rows => {
        this.entries.set(rows);
        this.loading.set(false);
      },
      // Surfaced, not swallowed. An officer must be able to tell an empty history from a failed load —
      // "this complaint has no recorded actions" and "we could not read the audit trail" are very
      // different facts when deciding whether to act.
      error: () => {
        this.error.set('complaint.history.error_load_failed');
        this.loading.set(false);
      }
    });
  }

  toggleAutomatic() {
    this.showAutomatic.update(v => !v);
  }

  /**
   * The translation key for an action.
   *
   * <p>Derived from the action code rather than held in a hardcoded English map, so a new workflow
   * action added as a table row needs no frontend change to display — and falls back to the raw code
   * rather than to a blank cell.
   */
  actionKey(action: string): string {
    if (!action) return 'complaint.history.action_unknown';
    return 'complaint.history.action.' + action.toLowerCase();
  }

  /** True when this entry records a change of owner, so the old→new line is worth rendering. */
  hasOwnerChange(e: TimelineEntry): boolean {
    return !!e.fieldName && (!!e.oldValue || !!e.newValue);
  }
}
