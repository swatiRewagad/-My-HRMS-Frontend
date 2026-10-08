import { Component, computed, DestroyRef, inject, input, output, signal, effect } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { Tooltip } from 'primeng/tooltip';
import { ColumnDefinition, ComplaintColumn, ComplaintColumnFilters, TableQueryMetadata } from '../../../models/cepc.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { CepcContextService } from '../../../services/cepc-context.service';
import { Router } from '@angular/router';
import { NavigationService } from '../../../services/navigation.service';
import { ApiService } from '../../../services/api.service';
import { CepcColumnPickerComponent } from '../cepc-column-picker/cepc-column-picker.component';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';
import { TranslationService } from '../../../services/translation.service';

@Component({
  selector: 'app-cepc-dashboard-table',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    TagModule,
    ButtonModule,
    InputTextModule,
    ToggleSwitchModule,
    Tooltip,
    CepcColumnPickerComponent,
    TranslateOrPipe
  ],
  templateUrl: './cepc-dashboard-table.component.html',
  styleUrl: './cepc-dashboard-table.component.scss'
})
export class CepcDashboardTableComponent {
  readonly auth = inject(KeycloakAuthService);
  private readonly router = inject(Router);
  private readonly navService = inject(NavigationService);
  private readonly apiService = inject(ApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly i18n = inject(TranslationService);

  readonly dept = inject(CepcContextService);

  readonly isNotDoUser = computed(() => !this.dept.hasRung('DO'));
  readonly tabConfig = input.required<any>();
  readonly complaints = input<ComplaintColumn[]>([]);
  readonly isSearching = input<boolean>(false);
  readonly totalRecords = input<number>(0);
  readonly activeTabValue = input<string | number>('0');
  readonly queryChanged = output<TableQueryMetadata>();

  readonly showUnreadOnly = signal<boolean>(false);
  readonly showWithoutAttachments = signal<boolean>(false);
  readonly currentPage = signal<number>(1);
  readonly rowsPerPage = signal<number>(10);

  // 🎯 Dynamic Standalone Picker State Controllers
  readonly isPickerOpen = signal<boolean>(false);

  sortField: string | null = 'createdDate';
  sortOrder: number = 1;
  private currentActiveFilters: ComplaintColumnFilters | null = null;

  /**
   * `header` is the English this grid has always shown and is what renders unless `labelKey` is seeded, so
   * selecting English can never read differently from before this screen was localised.
   *
   * <p>The shared `ui.col.*` vocabulary is reused only where its English already matches `header` exactly.
   * Six columns use `ui.cepc.col.*` instead, because the shared keys word them differently ("Complainant",
   * "Entity", "Category", "Creation Date", "Last Updated", "SLA (hrs)").
   */
  readonly allColumns: ColumnDefinition[] = [
    { field: 'complaintId', labelKey: 'ui.col.complaint_id', header: 'Complaint Id' },
    { field: 'complaintNumber', labelKey: 'ui.col.complaint_number', header: 'Complaint Number' },
    { field: 'assignedTo', labelKey: 'ui.col.assigned_to', header: 'Assigned To' },
    { field: 'slaBreachIn', labelKey: 'ui.cepc.col.sla_breach_in', header: 'SLA Breach In' },
    { field: 'mode', labelKey: 'ui.col.mode_of_receipt', header: 'Mode' },
    { field: 'complainantName', labelKey: 'ui.cepc.col.complainant_name', header: 'Complainant Name' },
    { field: 'status', labelKey: 'ui.col.status', header: 'Status' },
    { field: 'entityName', labelKey: 'ui.cepc.col.entity_name', header: 'Entity Name' },
    { field: 'complaintCategory', labelKey: 'ui.cepc.col.complaint_category', header: 'Complaint Category' },
    { field: 'createdDate', labelKey: 'ui.cepc.col.created_date', header: 'Created Date' },
    { field: 'lastUpdatedDate', labelKey: 'ui.cepc.col.updated_date', header: 'Updated Date' },
    { field: 'priority', labelKey: 'ui.col.priority', header: 'Priority' },
    { field: 'subject', labelKey: 'ui.col.subject', header: 'Subject' },
    { field: 'contactPerson', labelKey: 'ui.col.contact_person', header: 'Contact Person' }
  ];

  /** Identity columns: always rendered first and never draggable. */
  private readonly pinnedFields = new Set<string>(['complaintId', 'complaintNumber']);

  private readonly tabColumns = computed<ColumnDefinition[]>(() => {
    const config = this.tabConfig();
    if (config?.visibleFields) {
      return this.allColumns.filter(col => config.visibleFields.includes(col.field));
    }
    return this.allColumns;
  });

  readonly pinnedColumns = computed<ColumnDefinition[]>(() =>
    this.tabColumns().filter(col => this.pinnedFields.has(col.field))
  );

  readonly columnsToDisplay = computed<ColumnDefinition[]>(() =>
    this.tabColumns().filter(col => !this.pinnedFields.has(col.field))
  );

  readonly pickableColumns = this.allColumns.filter(col => !this.pinnedFields.has(col.field));

  readonly selectedColumns = signal<ColumnDefinition[]>([]);

  /** Pinned columns lead; `[columns]` must match the rendered order for reorder indices to line up. */
  readonly displayedColumns = computed<ColumnDefinition[]>(() => [
    ...this.pinnedColumns(),
    ...this.selectedColumns()
  ]);

  private columnsInitialized = false;

  isPinned(field: string): boolean {
    return this.pinnedFields.has(field);
  }

  /** Columns the API sends as ISO-8601 timestamps, rendered as dd/MM/yyyy rather than raw. */
  private readonly dateFields = new Set<string>(['createdDate', 'lastUpdatedDate']);

  isDateColumn(field: string): boolean {
    return this.dateFields.has(field);
  }

  /**
   * The status chip's text in the current locale, falling back to the server's English `statusLabel`.
   *
   * <p>The fallback is what keeps English unchanged: an unseeded key, or a status this release has no key
   * for at all, renders the same words the server has always sent rather than a raw `ui.status.*` token.
   */
  statusText(row: ComplaintColumn): string {
    const fallback = row.statusLabel || row.status || '';
    if (!row.statusLabelKey) return fallback;
    return this.i18n.translateOr(row.statusLabelKey, fallback);
  }

  constructor() {
    effect(() => {
      const defaults = this.columnsToDisplay();
      if (!this.columnsInitialized) {
        this.columnsInitialized = true;
        this.selectedColumns.set([...defaults]);
      }
    });
  }

  applyPickedColumns(updatedColumns: ColumnDefinition[]): void {
    this.selectedColumns.set(updatedColumns);
  }

  resetColumnsToDefault(): void {
    this.selectedColumns.set([...this.columnsToDisplay()]);
  }

  // p-table reorders the array it was handed in place, so re-emitting the same reference would leave
  // the signal unnotified and the header stale. Slice off the pinned prefix into a fresh array instead.
  onColumnReorder(event: { columns?: ColumnDefinition[] }): void {
    if (!event?.columns) return;
    this.selectedColumns.set(event.columns.slice(this.pinnedColumns().length));
  }

  onLazyLoadTable(event: TableLazyLoadEvent): void {
    const rows = event.rows ?? this.rowsPerPage();
    const first = event.first ?? 0;
    const computedPage = Math.floor(first / rows) + 1;
    this.currentPage.set(computedPage);
    this.rowsPerPage.set(rows);
    this.sortField = (event.sortField as string) ?? 'createdDate';
    this.sortOrder = event.sortOrder ?? 1;

    const direction: 'asc' | 'desc' | null = this.sortOrder === 1 ? 'asc' : this.sortOrder === -1 ? 'desc' : null;
    this.queryChanged.emit({
      page: computedPage,
      size: rows,
      sortField: this.sortField,
      sortOrder: direction,
      filters: this.currentActiveFilters,
      showUnreadOnly: this.showUnreadOnly(),
      showWithoutAttachments: this.showWithoutAttachments(),
      tabValue: this.activeTabValue()
    });
  }

  onToggleChange(value: boolean, type: 'unread' | 'attachments'): void {
    if (type === 'unread') {
      this.showUnreadOnly.set(value);
    } else {
      this.showWithoutAttachments.set(value);
    }
    this.executeExplicitReload();
  }

  onTableFilterChange(event: any): void {
    const activeFilters: Record<string, string> = {};

    if (event.filters) {
      Object.keys(event.filters).forEach((key) => {
        const meta = event.filters[key];
        const token = Array.isArray(meta) ? meta[0]?.value : meta?.value;
        if (token !== null && token !== undefined && String(token).trim() !== '') {
          activeFilters[key] = String(token).trim();
        }
      });
    }

    this.currentActiveFilters = activeFilters;
    this.executeExplicitReload();
  }

  private executeExplicitReload(): void {
    this.currentPage.set(1);
    this.onLazyLoadTable({
      first: 0,
      rows: this.rowsPerPage(),
      sortField: this.sortField,
      sortOrder: this.sortOrder as 1 | -1
    });
  }

  /**
   * Marks the complaint read for this user, then opens it.
   *
   * <p>The mark lives here rather than on the dashboard because this is the only place a complaint is
   * actually opened from — the dashboard's own copy of this was unreachable, which is why rows stayed
   * bold forever no matter how many times they were read.
   *
   * <p>Navigation does not wait on the response: the mark is a side effect of opening, and a slow or
   * failed write should not leave an officer staring at the grid. `isRead` is set locally as well so the
   * row loses its unread styling immediately on return, before the next search answers.
   */
  navigateToDetail(complaint: any): void {
    const id = complaint.complaintId || complaint.complaintNumber;

    if (complaint.complaintId && !complaint.isRead) {
      complaint.isRead = true;
      this.apiService
        .post(`/complaints/${complaint.complaintId}/read`, null)
        .pipe(catchError(() => of(null)), takeUntilDestroyed(this.destroyRef))
        .subscribe();
    }

    const routeKey = complaint.status === 'DRAFT' ? 'draft-complaint' : 'complaint';
    const route = this.dept.route(routeKey, id);

    // A refused navigation leaves the grid on screen looking like the click never landed, so say so
    // explicitly: this separates "the handler never ran" from "the router declined the route".
    this.navService.navigate(this.dept.route('complaint', id));
  }
}
