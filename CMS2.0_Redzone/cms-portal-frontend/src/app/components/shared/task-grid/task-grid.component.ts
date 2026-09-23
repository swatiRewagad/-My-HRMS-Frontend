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
   * Cell templates for columns with kind 'custom', keyed by the column's `template` name.
   *
   * Needed because a few cells are genuinely module-specific components (CEPC's SLA indicator). Without
   * it, consolidation would have downgraded those cells to plain text and lost working behaviour.
   */
  cellTemplates = input<Record<string, TemplateRef<{ $implicit: T }>>>({});

  rowClick = output<T>();

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

  readonly totalPages = computed(() => Math.max(1, Math.ceil(this.filtered().length / this.pageSize())));

  readonly paged = computed(() => {
    // Clamped rather than trusted: filtering down to fewer pages while sitting on a high page number
    // would otherwise render an empty grid over a non-empty result set.
    const page = Math.min(this.page(), this.totalPages());
    const start = (page - 1) * this.pageSize();
    return this.filtered().slice(start, start + this.pageSize());
  });

  readonly rangeStart = computed(() =>
    this.filtered().length === 0 ? 0 : (Math.min(this.page(), this.totalPages()) - 1) * this.pageSize() + 1
  );

  readonly rangeEnd = computed(() =>
    Math.min(Math.min(this.page(), this.totalPages()) * this.pageSize(), this.filtered().length)
  );

  /**
   * Interpolation params for the translate pipe, which takes Record<string, string>. Built here rather
   * than in the template so the numbers are stringified once, in typed code.
   */
  readonly countParams = computed(() => ({
    shown: `${this.rangeStart()} to ${this.rangeEnd()}`,
    total: String(this.filtered().length),
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
    this.page.set(1);
  }

  setSearch(value: string): void {
    this.search.set(value);
    this.page.set(1);
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

  toggleColumn(key: string): void {
    this.hiddenKeys.update(keys => {
      const next = new Set(keys);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  }

  goToPage(page: number): void {
    this.page.set(Math.min(Math.max(1, page), this.totalPages()));
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
