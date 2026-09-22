import { Component, inject, signal, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../services/keycloak-auth.service';
import {
  SeniorDashboardService,
  DashboardSummary,
  PipelineSummary,
  TatAnalytics,
  Bottlenecks,
  WeeklyTrend,
  EntityPerformance
} from '../../services/senior-dashboard.service';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

const RBI_COLORS = {
  navy: '#1a237e',
  blue: '#0d47a1',
  gold: '#c6922a',
  darkGold: '#9e7b24',
  green: '#1b5e20',
  lightGreen: '#388e3c',
  red: '#b71c1c',
  orange: '#e65100',
  teal: '#00695c',
  grey: '#455a64'
};

const CHART_PALETTE = ['#1a237e', '#0d47a1', '#1565c0', '#c6922a', '#1b5e20', '#00695c', '#4527a0', '#b71c1c', '#e65100', '#455a64'];

@Component({
  selector: 'app-senior-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './senior-dashboard.component.html',
  styleUrl: './senior-dashboard.component.scss'
})
export class SeniorDashboardComponent implements OnInit, OnDestroy, AfterViewInit {
  private router = inject(Router);
  private dashboardService = inject(SeniorDashboardService);
  auth = inject(KeycloakAuthService);

  loading = signal(true);
  error = signal<string | null>(null);
  pipeline = signal<PipelineSummary | null>(null);
  tat = signal<TatAnalytics | null>(null);
  bottlenecks = signal<Bottlenecks | null>(null);
  trend = signal<WeeklyTrend[]>([]);
  entityPerf = signal<EntityPerformance | null>(null);

  @ViewChild('trendChart') trendChartRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('tatPieChart') tatPieRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('deptBarChart') deptBarRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('categoryBarChart') categoryBarRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('entityVolumeChart') entityVolumeRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('stackedDeptChart') stackedDeptRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('priorityRadarChart') priorityRadarRef!: ElementRef<HTMLCanvasElement>;

  private charts: Chart[] = [];
  private refreshInterval: any;

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadData();
    this.refreshInterval = setInterval(() => this.loadData(), 120000);
  }

  ngAfterViewInit() {}

  ngOnDestroy() {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    this.charts.forEach(c => c.destroy());
  }

  private loadData() {
    this.loading.set(true);
    this.dashboardService.getFullSummary().subscribe({
      next: (data) => {
        this.pipeline.set(data.pipeline);
        this.tat.set(data.tat);
        this.bottlenecks.set(data.bottlenecks);
        this.trend.set(data.trend);
        this.entityPerf.set(data.entityPerformance);
        this.loading.set(false);
        setTimeout(() => this.renderCharts(), 50);
      },
      error: (err) => {
        this.error.set('Failed to load dashboard data.');
        this.loading.set(false);
      }
    });
  }

  private renderCharts() {
    this.charts.forEach(c => c.destroy());
    this.charts = [];

    this.renderTrendChart();
    this.renderTatPie();
    this.renderDeptBar();
    this.renderCategoryBar();
    this.renderEntityVolumeChart();
    this.renderStackedDeptChart();
    this.renderPriorityRadar();
  }

  private renderTrendChart() {
    const canvas = this.trendChartRef?.nativeElement;
    if (!canvas) return;
    const data = this.trend();

    this.charts.push(new Chart(canvas, {
      type: 'line',
      data: {
        labels: data.map(d => d.weekLabel),
        datasets: [
          {
            label: 'Filed',
            data: data.map(d => d.filed),
            borderColor: RBI_COLORS.navy,
            backgroundColor: 'rgba(26,35,126,0.08)',
            fill: true,
            tension: 0.4,
            pointBackgroundColor: RBI_COLORS.navy,
            pointRadius: 4
          },
          {
            label: 'Resolved',
            data: data.map(d => d.resolved),
            borderColor: RBI_COLORS.green,
            backgroundColor: 'rgba(27,94,32,0.08)',
            fill: true,
            tension: 0.4,
            pointBackgroundColor: RBI_COLORS.green,
            pointRadius: 4
          },
          {
            label: 'Net Backlog Growth',
            data: data.map(d => d.net),
            borderColor: RBI_COLORS.gold,
            borderDash: [5, 3],
            backgroundColor: 'transparent',
            tension: 0.4,
            pointBackgroundColor: RBI_COLORS.gold,
            pointRadius: 3
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: { y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.04)' } } }
      }
    }));
  }

  private renderTatPie() {
    const canvas = this.tatPieRef?.nativeElement;
    if (!canvas) return;
    const t = this.tat();
    if (!t) return;

    this.charts.push(new Chart(canvas, {
      type: 'doughnut',
      data: {
        labels: ['On Track', 'At Risk', 'Breached'],
        datasets: [{
          data: [t.onTrack, t.atRisk, t.breached],
          backgroundColor: [RBI_COLORS.green, RBI_COLORS.gold, RBI_COLORS.red],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        cutout: '60%',
        plugins: {
          legend: { position: 'bottom' },
          tooltip: { callbacks: { label: (ctx) => `${ctx.label}: ${ctx.raw} (${Math.round(Number(ctx.raw) / (t.totalActive || 1) * 100)}%)` } }
        }
      }
    }));
  }

  private renderDeptBar() {
    const canvas = this.deptBarRef?.nativeElement;
    if (!canvas) return;
    const b = this.bottlenecks();
    if (!b) return;

    const labels = Object.keys(b.backlogByDepartment);
    const values = Object.values(b.backlogByDepartment);

    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Active Backlog',
          data: values,
          backgroundColor: CHART_PALETTE.slice(0, labels.length),
          borderRadius: 4
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.04)' } } }
      }
    }));
  }

  private renderCategoryBar() {
    const canvas = this.categoryBarRef?.nativeElement;
    if (!canvas) return;
    const b = this.bottlenecks();
    if (!b) return;

    const labels = Object.keys(b.backlogByCategory);
    const values = Object.values(b.backlogByCategory);

    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          label: 'Backlog',
          data: values,
          backgroundColor: CHART_PALETTE.slice(0, labels.length),
          borderRadius: 3
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        indexAxis: 'y',
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.04)' } } }
      }
    }));
  }

  private renderEntityVolumeChart() {
    const canvas = this.entityVolumeRef?.nativeElement;
    if (!canvas) return;
    const ep = this.entityPerf();
    if (!ep) return;

    const entities = Object.keys(ep.volumeByEntity);
    const volume = Object.values(ep.volumeByEntity);
    const resolved = entities.map(e => ep.resolvedByEntity[e] || 0);
    const breached = entities.map(e => ep.breachByEntity[e] || 0);

    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: entities,
        datasets: [
          { label: 'Total Volume', data: volume, backgroundColor: RBI_COLORS.navy, borderRadius: 3 },
          { label: 'Resolved', data: resolved, backgroundColor: RBI_COLORS.green, borderRadius: 3 },
          { label: 'Breached SLA', data: breached, backgroundColor: RBI_COLORS.red, borderRadius: 3 }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: {
          x: { grid: { display: false } },
          y: { beginAtZero: true, grid: { color: 'rgba(0,0,0,0.04)' } }
        }
      }
    }));
  }

  private renderStackedDeptChart() {
    const canvas = this.stackedDeptRef?.nativeElement;
    if (!canvas) return;
    const ep = this.entityPerf();
    if (!ep || !ep.statusByDepartment) return;

    const departments = Object.keys(ep.statusByDepartment);
    const allStatuses = new Set<string>();
    departments.forEach(d => Object.keys(ep.statusByDepartment[d]).forEach(s => allStatuses.add(s)));
    const statuses = Array.from(allStatuses);

    const statusColors: Record<string, string> = {
      pending: RBI_COLORS.gold,
      in_progress: RBI_COLORS.blue,
      escalated: RBI_COLORS.orange,
      resolved: RBI_COLORS.green,
      closed: RBI_COLORS.grey,
      forwarded: RBI_COLORS.teal
    };

    const datasets = statuses.map(status => ({
      label: status.replace('_', ' '),
      data: departments.map(d => ep.statusByDepartment[d][status] || 0),
      backgroundColor: statusColors[status] || '#9e9e9e',
      borderRadius: 2
    }));

    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: { labels: departments, datasets },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } },
        scales: {
          x: { stacked: true, grid: { display: false } },
          y: { stacked: true, beginAtZero: true, grid: { color: 'rgba(0,0,0,0.04)' } }
        }
      }
    }));
  }

  private renderPriorityRadar() {
    const canvas = this.priorityRadarRef?.nativeElement;
    if (!canvas) return;
    const b = this.bottlenecks();
    if (!b || !b.volumeByPriority) return;

    const labels = Object.keys(b.volumeByPriority);
    const values = Object.values(b.volumeByPriority);

    this.charts.push(new Chart(canvas, {
      type: 'polarArea',
      data: {
        labels: labels.map(l => l.charAt(0).toUpperCase() + l.slice(1)),
        datasets: [{
          data: values,
          backgroundColor: [
            'rgba(183,28,28,0.7)',
            'rgba(198,146,42,0.7)',
            'rgba(27,94,32,0.7)'
          ],
          borderWidth: 2,
          borderColor: '#fff'
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom' } }
      }
    }));
  }

  getUnassignedEntries(): { key: string; value: number }[] {
    const b = this.bottlenecks();
    if (!b) return [];
    return Object.entries(b.unassignedByDepartment).map(([key, value]) => ({ key, value }));
  }

  navigate(path: string) {
    this.router.navigate([path]);
  }
}
