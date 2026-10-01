/**
 * One workflow transition the current user may trigger on the open complaint or appeal.
 *
 * Reconciles three hand-rolled shapes. `staff/task-action` keyed its actions on `value` and carried a
 * literal `label`; `cepc-complaint-detail` keyed on `id` with a literal `label` + `description`;
 * `aa-appeal-detail` keyed on `id` with `labelKey` + `descriptionKey` resolved through the translate
 * pipe. The key is now always `id`, and the caption is either a translation key or a literal — a key
 * wins when both are set, so a host can migrate one action at a time.
 */
export interface WorkflowAction {
  /** The transition code sent to the server. `staff/task-action` called this `value`. */
  id: string;

  /** Literal caption. Ignored when `labelKey` is set. Falls back to `id` when both are absent. */
  label?: string;

  /** Translation key for the caption. Wins over `label`. */
  labelKey?: string;

  /** Literal sub-caption. Ignored when `descriptionKey` is set. Omit for no sub-caption. */
  description?: string;

  /** Translation key for the sub-caption. Wins over `description`. */
  descriptionKey?: string;

  style?: WorkflowActionStyle;

  /**
   * Whether the confirm step demands remarks before it will commit.
   *
   * Defaults to TRUE when absent. `staff/task-action` made remarks mandatory for every action and
   * stated it nowhere, so the fail-safe default reproduces that host without forty repetitions of the
   * same flag. cepc and aa state it on every action, which is the habit to keep: an action that omits
   * it gets the stricter behaviour, never the looser one.
   */
  requiresRemarks?: boolean;
}

/**
 * The union of both style vocabularies, because neither host set was a superset of the other.
 *
 * `staff/task-action` used approve | escalate | reject | resolve | return; cepc and aa used
 * primary | info | review | forward | escalate | return | close. Every one of those ten still gets a
 * distinct treatment in the component stylesheet — dropping one would silently flatten an action that
 * a host is currently colouring.
 */
export type WorkflowActionStyle =
  | 'approve'
  | 'escalate'
  | 'reject'
  | 'resolve'
  | 'return'
  | 'primary'
  | 'info'
  | 'review'
  | 'forward'
  | 'close';

/**
 * Pill row (staff/task-action) or stacked cards (cepc, aa).
 *
 * Both emit the same class names and test ids — see the component template for why — so this chooses
 * only which of the two visual treatments the stylesheet applies.
 */
export type WorkflowActionLayout = 'pills' | 'cards';
