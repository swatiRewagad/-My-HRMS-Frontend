/**
 * Configuration contract for the shared task grid.
 *
 * This application is task-based: every role sees a grid of tasks, filters it by column, and clicks a
 * row to open the task — which is the complaint. So the grid is the same screen everywhere and only
 * the DATA differs. Before this, 18 hand-rolled tables each re-implemented sorting, column filtering,
 * empty states and pagination, and disagreed on all of them.
 */

export type TaskGridCellKind =
  /** Plain text, run through the formatter if one is supplied. */
  | 'text'
  /** Renders via the shared status badge, so status colour is consistent app-wide. */
  | 'status'
  /** An ageing/SLA value rendered as a green/amber/red chip. */
  | 'ageing'
  /**
   * A priority value rendered as a coloured dot beside its label.
   *
   * Every grid that shows priority renders exactly this, and four of them re-declared the dot's colours
   * locally with different palettes — so HIGH was red in one queue and orange in the next.
   */
  | 'priority'
  /** A date, rendered dd/mm/yyyy. Also sorted and filtered as an instant, not as the rendered text. */
  | 'date'
  /**
   * Rendered by a caller-supplied template rather than by the grid.
   *
   * The escape hatch exists because some cells are genuinely module-specific components — CEPC's SLA
   * cell renders app-cepc-sla-indicator, which no generic renderer can reproduce. Without this,
   * consolidating the grids would have silently DOWNGRADED that cell to plain text and lost a working
   * feature, which is a worse outcome than keeping one narrow extension point.
   *
   * Use it sparingly: a column that could be 'text' or 'status' must not be a custom template, or the
   * grids drift apart again through the back door.
   */
  | 'custom';

export interface TaskGridColumn<T = Record<string, unknown>> {
  /** Property on the row object. Also the sort key and the column-filter key. */
  key: string;

  /**
   * Translation key for the header. NEVER a literal: this grid renders in eleven locales, and a
   * hardcoded header is exactly the defect that left CRPC and CEPC untranslated.
   */
  labelKey: string;

  kind?: TaskGridCellKind;

  /** Columns start hidden when false, and can be revealed through the column chooser. */
  visible?: boolean;

  sortable?: boolean;

  /** Shows a per-column search input in the filter row. */
  filterable?: boolean;

  /** Optional CSS width, e.g. '140px'. */
  width?: string;

  /**
   * Derives the displayed string. Also used when filtering and sorting that column, so what the user
   * searches is what the user sees — filtering the raw value while displaying a formatted one is a
   * subtle trap ("Letter" visible, "PHYSICAL_LETTER" matched).
   */
  formatter?: (row: T) => string;

  /** Supplies the ageing chip's severity. Only read when kind === 'ageing'. */
  severity?: (row: T) => 'ok' | 'warn' | 'danger';

  /**
   * Name of a caller-supplied cell template, matched against the templates passed to the grid.
   * Only read when kind === 'custom'.
   */
  template?: string;
}

export interface TaskGridAction<T = Record<string, unknown>> {
  /** Translation key for the button label. */
  labelKey: string;
  icon?: string;
  /** 'danger' renders the destructive treatment. */
  style?: 'primary' | 'outline' | 'danger';
  /** Hidden entirely when this returns false. */
  visible?: (rows: T[]) => boolean;
  /** Disabled but visible when this returns false. */
  enabled?: (rows: T[]) => boolean;
  run: (rows: T[]) => void;
}
