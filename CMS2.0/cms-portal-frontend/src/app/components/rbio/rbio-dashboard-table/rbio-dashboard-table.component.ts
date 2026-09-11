import { Component, computed, inject, input, output, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableLazyLoadEvent, TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { ColumnDefinition, ComplaintColumn, ComplaintColumnFilters, TableQueryMetadata } from '../../../models/rbio.model';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { Router } from '@angular/router';
import { RbioColumnPickerComponent } from '../rbio-column-picker/rbio-column-picker.component';

@Component({
  selector: 'app-rbio-dashboard-table',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TableModule,
    TagModule,
    ButtonModule,
    InputTextModule,
    ToggleSwitchModule,
    RbioColumnPickerComponent
  ],
  templateUrl: './rbio-dashboard-table.component.html',
  styleUrl: './rbio-dashboard-table.component.scss'
})
export class RbioDashboardTableComponent {
  readonly auth = inject(KeycloakAuthService);
  private readonly router = inject(Router);

  readonly isNotDoUser = computed(() => !this.auth?.currentUser()?.roles?.includes('RBIO_DO'));
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

  readonly allColumns: ColumnDefinition[] = [
    { field: 'complaintId', header: 'Complaint Id' },
    { field: 'complaintNumber', header: 'Complaint Number' },
    { field: 'assignedTo', header: 'Assigned To' },
    { field: 'slaBreachIn', header: 'SLA Breach In' },
    { field: 'mode', header: 'Mode' },
    { field: 'complainantName', header: 'Complainant Name' },
    { field: 'status', header: 'Status' },
    { field: 'entityName', header: 'Entity Name' },
    { field: 'complaintCategory', header: 'Complaint Category' },
    { field: 'createdDate', header: 'Created Date' },
    { field: 'lastUpdatedDate', header: 'Updated Date' },
    { field: 'priority', header: 'Priority' },
    { field: 'subject', header: 'Subject' },
    { field: 'principalNodalOfficer', header: 'Principal Nodal Officer' },
    { field: 'nodalOfficer', header: 'Nodal Officer' }
  ];

  readonly columnsToDisplay = computed<ColumnDefinition[]>(() => {
    const config = this.tabConfig();
    if (config?.visibleFields) {
      return this.allColumns.filter(col => config.visibleFields.includes(col.field));
    }
    return this.allColumns;
  });

  readonly selectedColumns = signal<ColumnDefinition[]>([]);

  constructor() {
    console.log(this.auth.currentUser());

    effect(() => {
      this.selectedColumns.set([...this.columnsToDisplay()]);
    });
  }

  applyPickedColumns(updatedColumns: ColumnDefinition[]): void {
    this.selectedColumns.set(updatedColumns);
  }

  onColumnReorder(event: any): void {
    if (event && event.columns) {
      this.selectedColumns.set(event.columns);
    }
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

  navigateToDetail(complaint: any): void {
    const id = complaint.complaintId || complaint.complaintNumber;
    this.router.navigate(['/rbio/complaint', id]);
  }
}
