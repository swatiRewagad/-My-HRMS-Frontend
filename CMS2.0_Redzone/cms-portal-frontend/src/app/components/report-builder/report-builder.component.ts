import { Component, inject, signal, OnInit, computed, ElementRef, ViewChildren, QueryList, AfterViewChecked, DestroyRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject } from 'rxjs';
import { finalize, takeUntil } from 'rxjs/operators';
import { Chart, registerables } from 'chart.js';
import {
  ReportBuilderService,
  SemanticModel,
  SubjectToken,
  FilterToken,
  FilterFieldType,
  FilterOperator,
  GroupByToken,
  ReportQuery,
  ReportExecutionResult,
  ReportDefinition,
  ReportAccessRole,
  NoRecordDrillDownRow,
  OPERATORS_BY_FIELD_TYPE,
  OPERATOR_LABELS
} from '../../services/report-builder.service';
import { KeycloakAuthService } from '../../services/keycloak-auth.service';

Chart.register(...registerables);

const MAX_WIDGETS = 3;
const WIDGET_REFRESH_COOLDOWN_MS = 30000;
const MAX_DATE_RANGE_DAYS = 365;
const MIN_DATE_RANGE_DAYS = 1;
const MAX_IN_VALUES = 100;

/** Advanced filter state for each selected filter */
export interface AdvancedFilterState {
  filterId: string;
  operator: FilterOperator;
  value: string;
  valueTo: string; // for BETWEEN operator
  fieldType: FilterFieldType;
}

@Component({
  selector: 'app-report-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './report-builder.component.html',
  styleUrl: './report-builder.component.scss'
})
export class ReportBuilderComponent implements OnInit, AfterViewChecked {
  private reportService = inject(ReportBuilderService);
  private keycloakAuth = inject(KeycloakAuthService);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  // Cancel tokens for drill-down requests
  private drillDownCancel$ = new Subject<void>();

  // Semantic model tokens
  subjects = signal<SubjectToken[]>([]);
  filters = signal<FilterToken[]>([]);
  groupBys = signal<GroupByToken[]>([]);

  // Composer state
  selectedSubject = signal<SubjectToken | null>(null);
  selectedFilters = signal<FilterToken[]>([]);
  selectedGroupBy = signal<GroupByToken | null>(null);

  // Advanced filter states (UST617-619)
  advancedFilterStates = signal<AdvancedFilterState[]>([]);

  // UI state
  loading = signal(false);
  modelLoading = signal(true);
  error = signal<string | null>(null);
  activeTab = signal<'compose' | 'results' | 'widgets'>('compose');

  // Results
  results = signal<ReportExecutionResult | null>(null);
  resultColumns = signal<string[]>([]);

  // Widget / Schedule dialogs
  showWidgetDialog = signal(false);
  showScheduleDialog = signal(false);
  widgetTitle = '';
  widgetChartType = 'TABLE';
  scheduleFrequency = 'DAILY';
  scheduleSlot = '23:00';
  savedWidgetId = signal<number | null>(null);

  // Saved widgets
  myWidgets = signal<ReportDefinition[]>([]);
  widgetData = signal<Map<number, any[]>>(new Map());
  widgetLoading = signal<Set<number>>(new Set());
  private widgetCharts: Map<number, Chart> = new Map();
  private widgetChartsRendered: Set<number> = new Set();
  private lastWidgetRefresh: number = 0;

  // Filter search
  // Signal-backed: `filteredFilters` is a computed() and a plain field read inside one registers
  // no dependency, so the filter search box was inert.
  filterSearch = signal('');

  maxWidgets = MAX_WIDGETS;

  // NO Record drill-down (UST621)
  showNoRecordDrillDown = signal(false);
  noRecordDrillDownLoading = signal(false);
  noRecordDrillDownData = signal<NoRecordDrillDownRow[]>([]);
  noRecordDrillDownComplaintId = signal<string>('');

  // Report access roles (UST615, UST622, UST670)
  reportAccessRoles = signal<ReportAccessRole[]>([]);
  /** True when the access list could not be read, so an empty list means "unknown" not "none". */
  accessRolesLoadFailed = signal(false);
  showAccessRoleAdmin = signal(false);
  accessRoleAdminLoading = signal(false);
  newAccessRole: Partial<ReportAccessRole> = { reportType: '', roleName: '', canExport: true };

  /**
   * Whether to OFFER export. The server decides whether to permit it.
   *
   * Two changes from the previous version:
   *
   * 1. No fall-through to `return true`. That default meant an unconfigured — or unreachable —
   *    access list read as "everyone may export", and because the GET silently 404'd against a
   *    backend that had no such endpoint, that was the permanent state. Absence of a permission list
   *    now hides the button instead of showing it.
   * 2. The hardcoded AA_ADMIN/CEPD_ADMIN pair is gone. Both are rows in REPORT_ACCESS_ROLE with
   *    CAN_EXPORT='N', so the view-only rule is data. A code copy would let an admin grant export in
   *    the UI and have it silently overruled here.
   *
   * Presentation only: /api/v1/reports/export enforces the same rule server-side, because hiding a
   * button stops nobody from calling the endpoint.
   */
  canExport = computed(() => {
    const roles = this.keycloakAuth.getRoles();
    const accessRoles = this.reportAccessRoles();
    if (accessRoles.length === 0) {
      return false;
    }
    return accessRoles.some(ar => roles.includes(ar.roleName) && ar.canExport);
  });

  isAdmin = computed(() => {
    const roles = this.keycloakAuth.getRoles();
    return roles.includes('ADMIN') || roles.includes('RBIO_ADMIN');
  });

  // Expose constants to template
  operatorLabels = OPERATOR_LABELS;
  operatorsByFieldType = OPERATORS_BY_FIELD_TYPE;

  // Computed sentence
  sentence = computed(() => {
    const subject = this.selectedSubject();
    if (!subject) return '';

    let s = `Show ${subject.label}`;
    const filters = this.selectedFilters();
    if (filters.length > 0) {
      s += ' where ' + filters.map(f => {
        const state = this.getAdvancedFilterState(f.id);
        if (state) {
          return `${f.label} ${OPERATOR_LABELS[state.operator]} ${state.value}${state.operator === 'BETWEEN' ? ' to ' + state.valueTo : ''}`;
        }
        return f.label;
      }).join(' and ');
    }
    const groupBy = this.selectedGroupBy();
    if (groupBy) {
      s += ` grouped by ${groupBy.label}`;
    }
    return s;
  });

  // Filter categories
  filterCategories = computed(() => {
    const cats = new Set<string>();
    this.filters().forEach(f => cats.add(f.category));
    return Array.from(cats).sort();
  });

  filteredFilters = computed(() => {
    const search = this.filterSearch().toLowerCase();
    if (!search) return this.filters();
    return this.filters().filter(f =>
      f.label.toLowerCase().includes(search) ||
      f.category.toLowerCase().includes(search)
    );
  });

  @ViewChildren('widgetCanvas') widgetCanvases!: QueryList<ElementRef<HTMLCanvasElement>>;

  ngOnInit() {
    this.loadSemanticModel();
    this.loadMyWidgets();
    this.loadReportAccessRoles();
  }

  ngAfterViewChecked() {
    if (this.activeTab() === 'widgets') {
      this.renderPendingWidgetCharts();
    }
  }

  private loadSemanticModel() {
    this.modelLoading.set(true);
    this.reportService.getSemanticModel().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (model) => {
        this.subjects.set(model.subjects);
        // Inject the "Complaint Closed On" filter (UST671-672)
        const closedOnFilter: FilterToken = {
          id: 'RBIO-complaint_closed_on-closedOn-synthetic',
          label: 'Complaint Closed On',
          field: 'complaint_closed_on',
          operator: 'BETWEEN',
          value: '',
          category: 'RBIO',
          fieldType: 'DATE'
        };
        const existingFilters = model.filters;
        const hasClosedOn = existingFilters.some(f => f.field === 'complaint_closed_on');
        if (!hasClosedOn) {
          existingFilters.push(closedOnFilter);
        }
        this.filters.set(existingFilters);
        this.groupBys.set(model.groupBys);
        this.modelLoading.set(false);
      },
      error: (err) => {
        this.error.set('Failed to load report model. Please try again.');
        this.modelLoading.set(false);
      }
    });
  }

  private loadMyWidgets() {
    this.reportService.getMyWidgets().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (widgets) => {
        this.myWidgets.set(widgets);
        if (widgets.length > 0) {
          this.executeAllWidgets();
        }
      },
      error: () => {}
    });
  }

  /**
   * Loads the report access list.
   *
   * The error handler was `() => {}`, which is why nobody noticed the endpoint did not exist: the
   * list stayed empty, the old canExport fell through to true, and the screen showed a reassuring
   * "No access roles configured" empty state while granting export to everyone. A failure to load a
   * PERMISSION list is not non-critical — it is exactly the case that must be visible, because
   * canExport now denies on an empty list and the user needs to know whether that means "not
   * permitted" or "could not check".
   */
  private loadReportAccessRoles() {
    this.reportService.getReportAccessRoles().pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (roles) => {
        this.reportAccessRoles.set(roles);
        this.accessRolesLoadFailed.set(false);
      },
      error: (err) => {
        this.reportAccessRoles.set([]);
        // 403 is a legitimate answer for a non-admin: they may run reports without being allowed to
        // read the permission table. Only a genuine failure is surfaced as one.
        this.accessRolesLoadFailed.set(err?.status !== 403);
      }
    });
  }

  refreshWidgets() {
    const now = Date.now();
    if (now - this.lastWidgetRefresh < WIDGET_REFRESH_COOLDOWN_MS) {
      this.error.set(`Rate limit: please wait ${Math.ceil((WIDGET_REFRESH_COOLDOWN_MS - (now - this.lastWidgetRefresh)) / 1000)}s before refreshing.`);
      return;
    }
    this.lastWidgetRefresh = now;
    this.widgetChartsRendered.clear();
    this.widgetCharts.forEach(c => c.destroy());
    this.widgetCharts.clear();
    this.executeAllWidgets();
  }

  private executeAllWidgets() {
    this.lastWidgetRefresh = Date.now();
    const widgets = this.myWidgets();
    widgets.forEach(w => this.executeWidget(w));
  }

  private executeWidget(widget: ReportDefinition) {
    const loadingSet = new Set(this.widgetLoading());
    loadingSet.add(widget.id);
    this.widgetLoading.set(loadingSet);

    try {
      const queryDef = JSON.parse(widget.queryDefinition);
      const query: ReportQuery = {
        subjectId: queryDef.subjectId,
        filters: queryDef.filters || [],
        groupByField: queryDef.groupByField,
        sentence: widget.sentence
      };

      this.reportService.execute(query).pipe(
        takeUntilDestroyed(this.destroyRef)
      ).subscribe({
        next: (result) => {
          const dataMap = new Map(this.widgetData());
          dataMap.set(widget.id, result.results);
          this.widgetData.set(dataMap);
          const ls = new Set(this.widgetLoading());
          ls.delete(widget.id);
          this.widgetLoading.set(ls);
        },
        error: () => {
          const ls = new Set(this.widgetLoading());
          ls.delete(widget.id);
          this.widgetLoading.set(ls);
        }
      });
    } catch {
      const ls = new Set(this.widgetLoading());
      ls.delete(widget.id);
      this.widgetLoading.set(ls);
    }
  }

  private renderPendingWidgetCharts() {
    if (!this.widgetCanvases) return;
    this.widgetCanvases.forEach((canvasRef) => {
      const widgetId = Number(canvasRef.nativeElement.getAttribute('data-widget-id'));
      if (this.widgetChartsRendered.has(widgetId)) return;
      const data = this.widgetData().get(widgetId);
      if (!data || data.length === 0) return;
      const widget = this.myWidgets().find(w => w.id === widgetId);
      if (!widget) return;

      this.widgetChartsRendered.add(widgetId);
      this.renderWidgetChart(canvasRef.nativeElement, widget, data);
    });
  }

  private renderWidgetChart(canvas: HTMLCanvasElement, widget: ReportDefinition, data: Record<string, any>[]) {
    const existing = this.widgetCharts.get(widget.id);
    if (existing) existing.destroy();

    const rbiColors = ['#1a237e', '#0d47a1', '#1565c0', '#1976d2', '#1e88e5', '#2196f3', '#42a5f5', '#64b5f6', '#90caf9'];
    const hasGroupValue = data.length > 0 && 'group' in data[0] && 'value' in data[0];

    if (widget.chartType === 'TABLE' || !hasGroupValue) return;

    const labels = data.map(d => String(d['group'] || ''));
    const values = data.map(d => Number(d['value'] || 0));

    let chartConfig: any;
    switch (widget.chartType) {
      case 'BAR':
        chartConfig = {
          type: 'bar',
          data: { labels, datasets: [{ label: widget.title, data: values, backgroundColor: rbiColors.slice(0, labels.length) }] },
          options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
        };
        break;
      case 'PIE':
        chartConfig = {
          type: 'doughnut',
          data: { labels, datasets: [{ data: values, backgroundColor: rbiColors.slice(0, labels.length) }] },
          options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
        };
        break;
      case 'LINE':
        chartConfig = {
          type: 'line',
          data: { labels, datasets: [{ label: widget.title, data: values, borderColor: '#1a237e', backgroundColor: 'rgba(26,35,126,0.1)', fill: true, tension: 0.3 }] },
          options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true } } }
        };
        break;
      default:
        return;
    }

    const chart = new Chart(canvas, chartConfig);
    this.widgetCharts.set(widget.id, chart);
  }

  getWidgetData(widgetId: number): any[] {
    return this.widgetData().get(widgetId) || [];
  }

  isWidgetLoading(widgetId: number): boolean {
    return this.widgetLoading().has(widgetId);
  }

  canAddMoreWidgets(): boolean {
    return this.myWidgets().length < MAX_WIDGETS;
  }

  deleteWidget(widgetId: number) {
    this.reportService.deleteWidget(widgetId).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        const existing = this.widgetCharts.get(widgetId);
        if (existing) existing.destroy();
        this.widgetCharts.delete(widgetId);
        this.widgetChartsRendered.delete(widgetId);
        this.loadMyWidgets();
      },
      error: () => this.error.set('Failed to delete widget.')
    });
  }

  selectSubject(subject: SubjectToken) {
    this.selectedSubject.set(subject);
    this.results.set(null);
  }

  toggleFilter(filter: FilterToken) {
    const current = this.selectedFilters();
    const idx = current.findIndex(f => f.id === filter.id);
    if (idx >= 0) {
      this.selectedFilters.set(current.filter(f => f.id !== filter.id));
      // Remove advanced filter state
      this.advancedFilterStates.set(
        this.advancedFilterStates().filter(s => s.filterId !== filter.id)
      );
    } else {
      this.selectedFilters.set([...current, filter]);
      // Add default advanced filter state
      const defaultOp = this.getDefaultOperator(filter.fieldType);
      this.advancedFilterStates.set([
        ...this.advancedFilterStates(),
        {
          filterId: filter.id,
          operator: defaultOp,
          value: filter.value || '',
          valueTo: '',
          fieldType: filter.fieldType
        }
      ]);
    }
    this.results.set(null);
  }

  isFilterSelected(filter: FilterToken): boolean {
    return this.selectedFilters().some(f => f.id === filter.id);
  }

  selectGroupBy(gb: GroupByToken | null) {
    this.selectedGroupBy.set(gb);
    this.results.set(null);
  }

  clearAll() {
    this.selectedSubject.set(null);
    this.selectedFilters.set([]);
    this.selectedGroupBy.set(null);
    this.advancedFilterStates.set([]);
    this.results.set(null);
    this.error.set(null);
  }

  removeFilter(filter: FilterToken) {
    this.selectedFilters.set(this.selectedFilters().filter(f => f.id !== filter.id));
    this.advancedFilterStates.set(
      this.advancedFilterStates().filter(s => s.filterId !== filter.id)
    );
    this.results.set(null);
  }

  // ─── Advanced Filter Methods (UST617-619) ───

  getAdvancedFilterState(filterId: string): AdvancedFilterState | undefined {
    return this.advancedFilterStates().find(s => s.filterId === filterId);
  }

  getOperatorsForFilter(filter: FilterToken): FilterOperator[] {
    return OPERATORS_BY_FIELD_TYPE[filter.fieldType] || OPERATORS_BY_FIELD_TYPE['TEXT'];
  }

  getDefaultOperator(fieldType: FilterFieldType): FilterOperator {
    switch (fieldType) {
      case 'DATE': return 'BETWEEN';
      case 'COMPLAINT_NUMBER': return 'LIKE';
      case 'PICKER': return 'EQUAL';
      default: return 'EQUAL';
    }
  }

  updateFilterOperator(filterId: string, operator: FilterOperator) {
    const states = [...this.advancedFilterStates()];
    const idx = states.findIndex(s => s.filterId === filterId);
    if (idx >= 0) {
      states[idx] = { ...states[idx], operator, value: states[idx].value, valueTo: '' };
      this.advancedFilterStates.set(states);
    }
  }

  updateFilterValue(filterId: string, value: string) {
    const states = [...this.advancedFilterStates()];
    const idx = states.findIndex(s => s.filterId === filterId);
    if (idx >= 0) {
      // Validate IN operator max 100 values
      if (states[idx].operator === 'IN') {
        const parts = value.split(',').map(v => v.trim()).filter(v => v.length > 0);
        if (parts.length > MAX_IN_VALUES) {
          this.error.set(`IN operator supports a maximum of ${MAX_IN_VALUES} comma-separated values.`);
          value = parts.slice(0, MAX_IN_VALUES).join(', ');
        }
      }
      states[idx] = { ...states[idx], value };
      this.advancedFilterStates.set(states);
    }
  }

  updateFilterValueTo(filterId: string, valueTo: string) {
    const states = [...this.advancedFilterStates()];
    const idx = states.findIndex(s => s.filterId === filterId);
    if (idx >= 0) {
      states[idx] = { ...states[idx], valueTo };
      this.advancedFilterStates.set(states);
    }
  }

  /** Validate date range for BETWEEN operator: min 1 day, max 1 year. Auto-caps if exceeds. */
  validateDateRange(filterId: string): string | null {
    const state = this.getAdvancedFilterState(filterId);
    if (!state || state.operator !== 'BETWEEN' || state.fieldType !== 'DATE') return null;
    if (!state.value || !state.valueTo) return null;

    const from = new Date(state.value);
    const to = new Date(state.valueTo);

    if (isNaN(from.getTime()) || isNaN(to.getTime())) return 'Invalid date';

    const diffDays = Math.abs((to.getTime() - from.getTime()) / (1000 * 60 * 60 * 24));

    if (diffDays < MIN_DATE_RANGE_DAYS) {
      return 'Minimum range is 1 day';
    }

    if (diffDays > MAX_DATE_RANGE_DAYS) {
      // Auto-cap to 1 year
      const cappedTo = new Date(from);
      cappedTo.setFullYear(cappedTo.getFullYear() + 1);
      this.updateFilterValueTo(filterId, cappedTo.toISOString().split('T')[0]);
      return 'Range auto-capped to 1 year';
    }

    return null;
  }

  // ─── Execute Report ───

  executeReport() {
    const subject = this.selectedSubject();
    if (!subject) return;

    // Build filters from advanced states
    const queryFilters = this.selectedFilters().map(f => {
      const state = this.getAdvancedFilterState(f.id);
      if (state) {
        let value = state.value;
        if (state.operator === 'BETWEEN') {
          value = `${state.value}|${state.valueTo}`;
        }
        return {
          field: f.field,
          operator: state.operator,
          value
        };
      }
      return {
        field: f.field,
        operator: f.operator,
        value: f.value
      };
    });

    const query: ReportQuery = {
      subjectId: subject.id,
      filters: queryFilters,
      groupByField: this.selectedGroupBy()?.field,
      sentence: this.sentence()
    };

    this.loading.set(true);
    this.error.set(null);

    // Store filter context for back navigation (UST620)
    sessionStorage.setItem('reportBuilder_filterContext', JSON.stringify({
      subject: this.selectedSubject(),
      filters: this.selectedFilters(),
      advancedStates: this.advancedFilterStates(),
      groupBy: this.selectedGroupBy()
    }));

    this.reportService.execute(query).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (result) => {
        this.results.set(result);
        if (result.results.length > 0) {
          this.resultColumns.set(Object.keys(result.results[0]));
        } else {
          this.resultColumns.set([]);
        }
        this.activeTab.set('results');
        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Report execution failed. Check your query and try again.');
        this.loading.set(false);
      }
    });
  }

  // ─── Drill-down: Complaint Number (UST620) ───

  isComplaintNumberColumn(col: string): boolean {
    const lower = col.toLowerCase().replace(/[\s_-]/g, '');
    return lower === 'complaintnumber' || lower === 'complaintno' || lower === 'complaintid';
  }

  isNoRecordCountColumn(col: string): boolean {
    const lower = col.toLowerCase().replace(/[\s_-]/g, '');
    return lower.includes('norecord') && lower.includes('count');
  }

  onComplaintNumberClick(row: Record<string, any>) {
    // Find the complaint number value
    const complaintCol = this.resultColumns().find(c => this.isComplaintNumberColumn(c));
    if (!complaintCol) return;

    const complaintId = row[complaintCol];
    if (!complaintId) return;

    // Store context for back navigation
    sessionStorage.setItem('reportBuilder_drillDownOrigin', 'true');

    // Navigate to complaint detail based on user role
    const roles = this.keycloakAuth.getRoles();
    if (roles.some(r => r.startsWith('RBIO_'))) {
      this.router.navigate(['/rbio/complaint', complaintId]);
    } else {
      this.router.navigate(['/staff/rbio/task', complaintId]);
    }
  }

  // ─── Drill-down: NO Record Count (UST621) ───

  onNoRecordCountClick(row: Record<string, any>) {
    // Cancel any previous in-flight request
    this.drillDownCancel$.next();

    const complaintCol = this.resultColumns().find(c => this.isComplaintNumberColumn(c));
    const complaintId = complaintCol ? row[complaintCol] : row['id'] || row['complaintId'];
    if (!complaintId) return;

    this.noRecordDrillDownComplaintId.set(String(complaintId));
    this.showNoRecordDrillDown.set(true);
    this.noRecordDrillDownLoading.set(true);
    this.noRecordDrillDownData.set([]);

    this.reportService.getNoRecordDrillDown(String(complaintId)).pipe(
      takeUntil(this.drillDownCancel$),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.noRecordDrillDownLoading.set(false))
    ).subscribe({
      next: (data) => this.noRecordDrillDownData.set(data),
      error: () => this.error.set('Failed to load NO Record drill-down data.')
    });
  }

  closeNoRecordDrillDown() {
    this.drillDownCancel$.next();
    this.showNoRecordDrillDown.set(false);
    this.noRecordDrillDownData.set([]);
  }

  // ─── Report Access Roles Admin (UST615, UST622, UST670) ───

  openAccessRoleAdmin() {
    this.showAccessRoleAdmin.set(true);
  }

  closeAccessRoleAdmin() {
    this.showAccessRoleAdmin.set(false);
  }

  addAccessRole() {
    if (!this.newAccessRole.reportType || !this.newAccessRole.roleName) {
      this.error.set('Report type and role name are required.');
      return;
    }
    const currentRoles = this.reportAccessRoles();
    const newRole: ReportAccessRole = {
      id: 0,
      reportType: this.newAccessRole.reportType!,
      roleName: this.newAccessRole.roleName!,
      canExport: this.newAccessRole.canExport ?? true
    };

    this.accessRoleAdminLoading.set(true);
    this.reportService.saveReportAccessRoles([...currentRoles, newRole]).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.accessRoleAdminLoading.set(false))
    ).subscribe({
      next: (roles) => {
        this.reportAccessRoles.set(roles);
        this.newAccessRole = { reportType: '', roleName: '', canExport: true };
      },
      error: () => this.error.set('Failed to save access role.')
    });
  }

  deleteAccessRole(roleId: number) {
    this.accessRoleAdminLoading.set(true);
    this.reportService.deleteReportAccessRole(roleId).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.accessRoleAdminLoading.set(false))
    ).subscribe({
      next: () => {
        this.reportAccessRoles.set(this.reportAccessRoles().filter(r => r.id !== roleId));
      },
      error: () => this.error.set('Failed to delete access role.')
    });
  }

  // ─── Widget & Schedule Dialog ───

  openWidgetDialog() {
    if (!this.canAddMoreWidgets()) {
      this.error.set(`Maximum ${MAX_WIDGETS} widgets allowed. Remove an existing widget before adding a new one.`);
      return;
    }
    this.widgetTitle = this.sentence();
    this.widgetChartType = this.selectedGroupBy() ? 'BAR' : 'TABLE';
    this.showWidgetDialog.set(true);
  }

  saveWidget() {
    const subject = this.selectedSubject();
    if (!subject) return;

    const queryFilters = this.selectedFilters().map(f => {
      const state = this.getAdvancedFilterState(f.id);
      if (state) {
        let value = state.value;
        if (state.operator === 'BETWEEN') {
          value = `${state.value}|${state.valueTo}`;
        }
        return { field: f.field, operator: state.operator, value };
      }
      return { field: f.field, operator: f.operator, value: f.value };
    });

    const query: ReportQuery = {
      subjectId: subject.id,
      filters: queryFilters,
      groupByField: this.selectedGroupBy()?.field,
      sentence: this.sentence()
    };

    this.reportService.saveWidget(this.sentence(), query, this.widgetChartType, this.widgetTitle).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: (widget) => {
        this.savedWidgetId.set(widget.id);
        this.showWidgetDialog.set(false);
        this.loadMyWidgets();
      },
      error: (err) => {
        this.error.set('Failed to save widget.');
      }
    });
  }

  openScheduleDialog() {
    this.showScheduleDialog.set(true);
  }

  saveSchedule() {
    const widgetId = this.savedWidgetId();
    if (!widgetId) {
      this.error.set('Save as widget first before scheduling.');
      this.showScheduleDialog.set(false);
      return;
    }

    this.reportService.schedule(widgetId, this.scheduleFrequency, this.scheduleSlot).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: () => {
        this.showScheduleDialog.set(false);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to schedule report.');
        this.showScheduleDialog.set(false);
      }
    });
  }

  getFiltersByCategory(category: string): FilterToken[] {
    return this.filteredFilters().filter(f => f.category === category);
  }

  trackByFilterId(_: number, filter: FilterToken): string {
    return filter.id;
  }

  trackByColumnName(_: number, col: string): string {
    return col;
  }
}
