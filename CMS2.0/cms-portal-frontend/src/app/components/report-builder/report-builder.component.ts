import { Component, inject, signal, OnInit, computed, ElementRef, ViewChildren, QueryList, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Chart, registerables } from 'chart.js';
import {
  ReportBuilderService,
  SemanticModel,
  SubjectToken,
  FilterToken,
  GroupByToken,
  ReportQuery,
  ReportExecutionResult,
  ReportDefinition
} from '../../services/report-builder.service';

Chart.register(...registerables);

const MAX_WIDGETS = 3;
const WIDGET_REFRESH_COOLDOWN_MS = 30000;

@Component({
  selector: 'app-report-builder',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './report-builder.component.html',
  styleUrl: './report-builder.component.scss'
})
export class ReportBuilderComponent implements OnInit, AfterViewChecked {
  private reportService = inject(ReportBuilderService);

  // Semantic model tokens
  subjects = signal<SubjectToken[]>([]);
  filters = signal<FilterToken[]>([]);
  groupBys = signal<GroupByToken[]>([]);

  // Composer state
  selectedSubject = signal<SubjectToken | null>(null);
  selectedFilters = signal<FilterToken[]>([]);
  selectedGroupBy = signal<GroupByToken | null>(null);

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
  filterSearch = '';

  maxWidgets = MAX_WIDGETS;

  // Computed sentence
  sentence = computed(() => {
    const subject = this.selectedSubject();
    if (!subject) return '';

    let s = `Show ${subject.label}`;
    const filters = this.selectedFilters();
    if (filters.length > 0) {
      s += ' where ' + filters.map(f => f.label).join(' and ');
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
    const search = this.filterSearch.toLowerCase();
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
  }

  ngAfterViewChecked() {
    if (this.activeTab() === 'widgets') {
      this.renderPendingWidgetCharts();
    }
  }

  private loadSemanticModel() {
    this.modelLoading.set(true);
    this.reportService.getSemanticModel().subscribe({
      next: (model) => {
        this.subjects.set(model.subjects);
        this.filters.set(model.filters);
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
    this.reportService.getMyWidgets().subscribe({
      next: (widgets) => {
        this.myWidgets.set(widgets);
        if (widgets.length > 0) {
          this.executeAllWidgets();
        }
      },
      error: () => {}
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

      this.reportService.execute(query).subscribe({
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
    this.reportService.deleteWidget(widgetId).subscribe({
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
    } else {
      this.selectedFilters.set([...current, filter]);
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
    this.results.set(null);
    this.error.set(null);
  }

  removeFilter(filter: FilterToken) {
    this.selectedFilters.set(this.selectedFilters().filter(f => f.id !== filter.id));
    this.results.set(null);
  }

  executeReport() {
    const subject = this.selectedSubject();
    if (!subject) return;

    const query: ReportQuery = {
      subjectId: subject.id,
      filters: this.selectedFilters().map(f => ({
        field: f.field,
        operator: f.operator,
        value: f.value
      })),
      groupByField: this.selectedGroupBy()?.field,
      sentence: this.sentence()
    };

    this.loading.set(true);
    this.error.set(null);

    this.reportService.execute(query).subscribe({
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

    const query: ReportQuery = {
      subjectId: subject.id,
      filters: this.selectedFilters().map(f => ({
        field: f.field,
        operator: f.operator,
        value: f.value
      })),
      groupByField: this.selectedGroupBy()?.field,
      sentence: this.sentence()
    };

    this.reportService.saveWidget(this.sentence(), query, this.widgetChartType, this.widgetTitle).subscribe({
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

    this.reportService.schedule(widgetId, this.scheduleFrequency, this.scheduleSlot).subscribe({
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
