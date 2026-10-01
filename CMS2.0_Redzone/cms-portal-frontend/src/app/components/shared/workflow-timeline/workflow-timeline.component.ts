import { Component, computed, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { WorkflowTimelineEntry } from './workflow-timeline.types';

/**
 * The one renderer for a complaint's workflow audit trail.
 *
 * Four screens hand-rolled this list and drifted: task-action printed a raw ISO timestamp, cepc-timeline
 * hardcoded English labels in a map, rbio-complaint-history translated them, and only one of the four
 * ever hid the remarks. This component carries both presentations the product actually needs, so the
 * drift cannot recur:
 *
 * - `inline` — the expandable section inside the complaint body. Emits `.timeline-entry`/`.timeline-item`
 *   so it satisfies the specs written against both of the old class names.
 * - `history` — the right-hand slide-in panel. Emits `.history-entry` and hides remarks until asked,
 *   because staff read this panel for the SEQUENCE of actions; the prose is the exception, not the rule.
 *
 * The action code is rendered verbatim in both modes. A humanised label would be friendlier, but the
 * codes are what appear in the audit export, in Kafka events and in support tickets, so showing anything
 * else forces staff to translate between two vocabularies when reconciling an incident.
 */
@Component({
  selector: 'app-workflow-timeline',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './workflow-timeline.component.html',
  styleUrl: './workflow-timeline.component.scss'
})
export class WorkflowTimelineComponent {

  readonly entries = input<readonly WorkflowTimelineEntry[] | null | undefined>([]);

  readonly mode = input<'inline' | 'history'>('inline');

  /**
   * Whether to offer the hide-comments checkbox. Off for `inline`, where the remarks are the reason the
   * section is expanded in the first place.
   */
  readonly commentsToggle = input(false);

  /** Empty-state copy. Passed in rather than translated here so each host keeps its own wording. */
  readonly emptyText = input('No history available');

  /**
   * Optional resolver from action code to display label.
   *
   * Defaults to the raw code, which is what the audit export, the Kafka events and support tickets all
   * use — so showing anything else by default forces staff to translate between two vocabularies while
   * reconciling an incident. RBIO passes a translating resolver because that screen already established
   * localised labels for its officers; a host that wants them opts in rather than every host inheriting
   * an English-only map, which is how the four old copies diverged.
   */
  readonly labelFor = input<((action: string) => string) | null>(null);

  /** Starts hidden. See the class doc: the panel is read for the sequence, not the prose. */
  readonly hideComments = signal(true);

  /**
   * Oldest first. The server returns the rows in insertion order, but two of the four read paths sort
   * differently, and a trail whose order depends on which endpoint served it is unreadable.
   * A row with no usable timestamp sorts last rather than being dropped.
   */
  readonly rows = computed<readonly WorkflowTimelineEntry[]>(() => {
    const list = this.entries() ?? [];
    return [...list].sort((a, b) => this.stampOf(a) - this.stampOf(b));
  });

  /** True when remarks should render — always in inline mode, only when un-hidden in the panel. */
  readonly remarksVisible = computed(() => !this.commentsToggle() || !this.hideComments());

  toggleComments() {
    this.hideComments.update(v => !v);
  }

  /** `performedAt` or `timestamp`: see WorkflowTimelineEntry — the server emits both names. */
  stamp(entry: WorkflowTimelineEntry): string | null {
    return entry.performedAt || entry.timestamp || null;
  }

  /** An automatic row is the system acting, not a blank person. */
  actorOf(entry: WorkflowTimelineEntry): string | null {
    if (entry.performedBy) return entry.performedBy;
    return entry.eventSource === 'AUTOMATIC' ? 'System' : null;
  }

  transitionOf(entry: WorkflowTimelineEntry): string {
    return `${entry.fromStatus || '—'} → ${entry.toStatus || '—'}`;
  }

  actionLabel(entry: WorkflowTimelineEntry): string {
    const code = entry.action || '';
    const resolver = this.labelFor();
    return resolver ? resolver(code) : code;
  }

  private stampOf(entry: WorkflowTimelineEntry): number {
    const raw = this.stamp(entry);
    const parsed = raw ? Date.parse(raw) : NaN;
    return Number.isNaN(parsed) ? Number.MAX_SAFE_INTEGER : parsed;
  }
}
