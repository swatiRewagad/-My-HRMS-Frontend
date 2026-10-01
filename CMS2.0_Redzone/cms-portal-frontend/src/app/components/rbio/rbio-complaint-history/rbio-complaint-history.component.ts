import { Component, Input, OnInit, inject, signal, computed } from '@angular/core';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { TranslationService } from '../../../services/translation.service';
import {
  ComplaintCorrespondenceService, TimelineEntry
} from '../../../services/complaint-correspondence.service';
import { WorkflowTimelineComponent } from '../../shared/workflow-timeline/workflow-timeline.component';

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
 * <p>The trail itself is rendered by app-workflow-timeline, which is also what the staff task screen and
 * the CEPC audit panel now use. This component keeps only what is specific to the RBIO History TAB: the
 * show-automatic filter, the load/error states, and the localised action labels it passes down.
 */
@Component({
  selector: 'app-rbio-complaint-history',
  standalone: true,
  imports: [TranslatePipe, WorkflowTimelineComponent],
  templateUrl: './rbio-complaint-history.component.html',
  styleUrl: './rbio-complaint-history.component.scss'
})
export class RbioComplaintHistoryComponent implements OnInit {

  /** The complaint number; the history endpoint is keyed by number, not id. */
  @Input() complaintNumber: string | null = null;

  private service = inject(ComplaintCorrespondenceService);
  private translations = inject(TranslationService);

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

  /**
   * Passed to app-workflow-timeline as its label resolver.
   *
   * <p>An arrow property, not a method: the template binds the function itself, so a prototype method
   * would lose `this` when the child invoked it. Falls back to the raw action code rather than to the
   * unresolved key, because an unseeded key renders as the key text — which reads as a bug to staff,
   * whereas the bare code at least matches the audit export.
   */
  translateAction = (action: string): string => {
    if (!action) return this.translations.translate('complaint.history.action_unknown');
    const key = this.actionKey(action);
    const label = this.translations.translate(key);
    return label === key ? action : label;
  };
}
