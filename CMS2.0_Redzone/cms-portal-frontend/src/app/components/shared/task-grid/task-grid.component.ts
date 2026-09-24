import { Component, TemplateRef, computed, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { StatusBadgeComponent } from '../status-badge/status-badge.component';
import { TaskGridAction, TaskGridColumn } from './task-grid.types';

/**
 * The one task grid.
 *
 * Every role in this application meets the same screen: a grid of tasks, filtered by column, where
 * clicking a row opens the complaint. Eighteen hand-rolled tables implemented that separately and
 * disagreed about sorting, filtering, empty states, pagination and row hover. Modelled on CRPC's grid,
 * which was the de-facto reference, so CRPC changes least.
 *
 * <h2>Column filters live in a SIGNAL, deliberately</h2>
 * Every existing grid stored them in a plain object (`columnFilters: Record<string, string> = {}`) and
 * read that object inside a `computed()`. A plain field registers no reactive dependency, so typing in
 * a column filter recomputed nothing and the filter silently did nothing at all — live in
 * crpc/deo-home, cepc-dashboard, report-builder, complaint-history and task-action. Holding the map in
 * a signal is what makes the feature actually work, which is why tests must drive it BY TYPING rather
 * than by asserting on the class field.
 */
@Component({
  selector: 'app-task-grid',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe, StatusBadgeComponent],
  templateUrl: './task-grid.component.html',
  styleUrl: './task-grid.component.scss'
})
export class TaskGridComponent<T extends Record<string, any> = Record<string, any>> {
  rows = input.required<T[]>();
  columns = input.required<TaskGridColumn<T>[]>();

  /** Property uniquely identifying a row; used for tracking, selection and the row-click payload. */
  rowKey = input<string>('id');

  /** Translation key for the grid's heading. */
  titleKey = input<string>('ui.grid.tasks');

  loading = input<boolean>(false);
  /** Shown instead of rows when set — never a raw server message. */
  errorKey = input<string | null>(null);

  selectable = input<boolean>(false);
  /** Actions operating on the current selection. */
  bulkActions = input<TaskGridAction<T>[]>([]);

  pageSize = input<number>(10);

  /**
   * Total row count ON THE SERVER, for grids whose API pages for them.
   *
   * <p>When null (the default) the grid owns paging and slices `rows` itself. When supplied, the caller
   * owns paging: `rows` is understood to be ONE PAGE, the grid stops slicing, and `pageChange` asks the
   * caller to fetch another. That distinction has to be explicit — a grid that slices an already-paged
   * array shows 10 of the 25 rows the server sent and calls it the whole result.
   *
   * <p>It must be the SERVER'S total, never the loaded row count. A previous grid's footer read "of 20"
   * because 20 was the backend page size, which looks like a plausible total and silently understates
   * a queue of thousands.
   */
  totalCount = input<number | null>(null);

  /** 1-based page the caller should load. Only emitted when `totalCount` is supplied. */
  pageChange = output<number>();

  /** True when the caller pages server-side, so this grid must not slice or re-count. */
  readonly serverPaged = computed(() => this.totalCount() !== null);

  /**
   * Cell templates for columns with kind 'custom', keyed by the column's `template` name.
   *
   * Needed because a few cells are genuinely module-specific components (CEPC's SLA indicator). Without
   * it, consolidation would have downgraded those cells to plain text and lost working behaviour.
   */
  cellTemplates = input<Record<string, TemplateRef<{ $implicit: T }>>>({});

  /**
   * Replaces the generic "no records" body.
   *
   * <p>RBIO's empty state echoes the search criteria back, so the user can see WHAT returned nothing.
   * Flattening that to "no records" would lose working behaviour, which is the kind of regression that
   * consolidation is supposed to avoid rather than cause.
   */
  emptyState = input<TemplateRef<unknown> | null>(null);

  /** Offers a retry button on the error state. Error text alone leaves the user only a page reload. */
  retryable = input<boolean>(false);

  /**
   * Per-row CSS class, for grids that signal urgency on the whole row rather than in one cell.
   *
   * <p>RE's dashboard tints a row by how close its response deadline is. Without this the migration
   * would silently drop that cue, which is the difference between a nodal officer seeing what is about
   * to breach and not.
   */
  rowClass = input<((row: T) => string) | null>(null);

  rowClick = output<T>();
  retry = output<void>();

  /** Free-text search across every filterable column. */
  search = signal('');
  columnFilters = signal<Record<string, string>>({});
  sortKey = signal('');
  sortDir = signal<'asc' | 'desc'>('asc');
  page = signal(1);
  selectedKeys = signal<Set<unknown>>(new Set());
  showColumnChooser = signal(false);
  hiddenKeys = signal<Set<string>>(new Set());

  readonly visibleColumns = computed(() => {
    const hidden = this.hiddenKeys();
    return this.columns().filter(c => (c.visible ?? true) && !hidden.has(c.key));
  });

  /** Single source of truth for a cell's text, so filter, sort and display can never disagree. */
  private cellText(row: T, col: TaskGridColumn<T>): string {
    if (col.formatter) {
      return col.formatter(row);
    }
    const raw = row[col.key];
    return raw === null || raw === undefined ? '' : String(raw);
  }

  readonly filtered = computed(() => {
    let out = [...this.rows()];
    const cols = this.visibleColumns();

    const term = this.search().trim().toLowerCase();
    if (term) {
      out = out.filter(row =>
        cols.some(col => this.cellText(row, col).toLowerCase().includes(term))
      );
    }

    const filters = this.columnFilters();
    for (const [key, value] of Object.entries(filters)) {
      const needle = (value ?? '').trim().toLowerCase();
      if (!needle) continue;
      const col = cols.find(c => c.key === key);
      if (!col) continue;
      out = out.filter(row => this.cellText(row, col).toLowerCase().includes(needle));
    }

    const key = this.sortKey();
    if (key) {
      const col = cols.find(c => c.key === key);
      if (col) {
        const dir = this.sortDir() === 'asc' ? 1 : -1;
        out.sort((a, b) =>
          this.cellText(a, col).localeCompare(this.cellText(b, col), undefined, { numeric: true }) * dir
        );
      }
    }

    return out;
  });

  /** Rows the footer is counting: the server's total when it pages, else what filtering produced. */
  readonly effectiveTotal = computed(() =>
    this.serverPaged() ? (this.totalCount() ?? 0) : this.filtered().length
  );

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.effectiveTotal() / this.pageSize())));

  readonly paged = computed(() => {
    // When the caller pages server-side, `rows` IS the page — slicing it again would hide rows the
    // server deliberately sent.
    if (this.serverPaged()) {
      return this.filtered();
    }
    // Clamped rather than trusted: filtering down to fewer pages while sitting on a high page number
    // would otherwise render an empty grid over a non-empty result set.
    const page = Math.min(this.page(), this.totalPages());
    const start = (page - 1) * this.pageSize();
    return this.filtered().slice(start, start + this.pageSize());
  });

  readonly rangeStart = computed(() =>
    this.effectiveTotal() === 0 ? 0 : (Math.min(this.page(), this.totalPages()) - 1) * this.pageSize() + 1
  );

  readonly rangeEnd = computed(() =>
    Math.min(Math.min(this.page(), this.totalPages()) * this.pageSize(), this.effectiveTotal())
  );

  /**
   * True when a page-local column filter is narrowing what the server sent.
   *
   * <p>Server-paged callers filter columns client-side over one page only, so the footer must not present
   * a refined count against a server-wide total as if they were comparable.
   */
  readonly refinementActive = computed(() =>
    this.serverPaged() && this.filtered().length !== this.rows().length
  );

  /**
   * Interpolation params for the translate pipe, which takes Record<string, string>. Built here rather
   * than in the template so the numbers are stringified once, in typed code.
   */
  readonly countParams = computed(() => ({
    shown: this.refinementActive()
      ? String(this.filtered().length)
      : `${this.rangeStart()} to ${this.rangeEnd()}`,
    total: String(this.effectiveTotal()),
  }));

  readonly pageParams = computed(() => ({
    current: String(Math.min(this.page(), this.totalPages())),
    total: String(this.totalPages()),
  }));

  readonly selectedRows = computed(() => {
    const keys = this.selectedKeys();
    return this.rows().filter(r => keys.has(r[this.rowKey()]));
  });

  readonly allOnPageSelected = computed(() => {
    const page = this.paged();
    if (page.length === 0) return false;
    const keys = this.selectedKeys();
    return page.every(r => keys.has(r[this.rowKey()]));
  });

  text(row: T, col: TaskGridColumn<T>): string {
    return this.cellText(row, col);
  }

  setColumnFilter(key: string, value: string): void {
    this.columnFilters.update(current => ({ ...current, [key]: value }));
    this.resetToFirstPage();
  }

  setSearch(value: string): void {
    this.search.set(value);
    this.resetToFirstPage();
  }

  /**
   * Returns to page 1 after a filter change.
   *
   * <p>A server-paged caller must be TOLD, or it keeps showing the page it last fetched while the footer
   * claims page 1. Guarded on already being there so filtering on page 1 does not refetch on every
   * keystroke.
   */
  private resetToFirstPage(): void {
    if (this.page() === 1) return;
    this.page.set(1);
    if (this.serverPaged()) {
      this.pageChange.emit(1);
    }
  }

  toggleSort(col: TaskGridColumn<T>): void {
    if (col.sortable === false) return;
    if (this.sortKey() === col.key) {
      this.sortDir.update(d => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortKey.set(col.key);
      this.sortDir.set('asc');
    }
  }

  toggleRow(row: T): void {
    const id = row[this.rowKey()];
    this.selectedKeys.update(keys => {
      const next = new Set(keys);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  toggleAllOnPage(): void {
    const page = this.paged();
    const allSelected = this.allOnPageSelected();
    this.selectedKeys.update(keys => {
      const next = new Set(keys);
      for (const row of page) {
        const id = row[this.rowKey()];
        allSelected ? next.delete(id) : next.add(id);
      }
      return next;
    });
  }

  isSelected(row: T): boolean {
    return this.selectedKeys().has(row[this.rowKey()]);
  }

  classFor(row: T): string {
    const fn = this.rowClass();
    return fn ? fn(row) : '';
  }

  toggleColumn(key: string): void {
    this.hiddenKeys.update(keys => {
      const next = new Set(keys);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  }

  goToPage(page: number): void {
    const target = Math.min(Math.max(1, page), this.totalPages());
    if (target === this.page()) return;
    this.page.set(target);
    // Only the server-paged caller needs telling; a client-paged grid already has every row.
    if (this.serverPaged()) {
      this.pageChange.emit(target);
    }
  }

  onRowClick(row: T): void {
    this.rowClick.emit(row);
  }

  severityClass(row: T, col: TaskGridColumn<T>): string {
    return col.severity ? col.severity(row) : 'ok';
  }

  /** Resolves a custom column's template, or null so the cell falls back to text. */
  templateFor(col: TaskGridColumn<T>): TemplateRef<{ $implicit: T }> | null {
    return (col.template && this.cellTemplates()[col.template]) || null;
  }

  visibleActions(): TaskGridAction<T>[] {
    const rows = this.selectedRows();
    return this.bulkActions().filter(a => (a.visible ? a.visible(rows) : true));
  }

  actionEnabled(action: TaskGridAction<T>): boolean {
    const rows = this.selectedRows();
    return action.enabled ? action.enabled(rows) : rows.length > 0;
  }

  runAction(action: TaskGridAction<T>): void {
    action.run(this.selectedRows());
  }
}
