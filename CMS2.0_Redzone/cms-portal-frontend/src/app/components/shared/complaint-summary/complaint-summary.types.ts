/**
 * Configuration contract for the shared complaint summary strip.
 *
 * The complaint is the one domain entity in this system, so the band of facts identifying it — number,
 * status, owner, SLA — is the same band on every screen that opens one. Five screens hand-rolled it and
 * drifted: task-action wrote its colours as raw hex while the CRPC screens used tokens, the CRPC
 * reviewer shrank the type and swapped the icon palette for five per-item tones, and RBIO called the
 * whole thing `.complaint-strip` with no icons at all.
 */

export type ComplaintSummaryItemKind =
  /** Plain text. */
  | 'text'
  /** Renders via the shared status badge, so one status carries one label and one colour app-wide. */
  | 'status'
  /**
   * A remaining-time value rendered as a green/amber/red chip.
   *
   * Kept distinct from 'text' because RBIO's SLA cell was the one item in the five copies that carried a
   * breach treatment, and folding it into plain text would have silently dropped that signal.
   */
  | 'sla';

export interface ComplaintSummaryItem {
  /** Translation key for the caption. NEVER a literal — this strip renders in eleven locales. */
  labelKey: string;

  /** The rendered value. Pass a already-resolved string; the strip does no data lookup of its own. */
  value: string | null | undefined;

  kind?: ComplaintSummaryItemKind;

  /** A PrimeIcons class, e.g. 'pi-id-card'. Omit for no icon disc. */
  icon?: string;

  /**
   * Icon disc tone. Defaults to the indigo brand disc that three of the five copies used.
   * 'owner' is the dark slate disc that marked the assignee item.
   */
  tone?: 'brand' | 'owner' | 'info' | 'success' | 'warn' | 'muted';

  /** Only read when kind === 'sla'. Drives the chip's green/amber/red treatment. */
  severity?: 'ok' | 'warn' | 'danger';

  /** Rendered as a hint affordance beside the caption when set. */
  hintKey?: string;
}
