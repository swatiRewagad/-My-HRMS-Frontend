import { Component, inject, signal, OnInit, OnDestroy, AfterViewInit, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RulesService } from '../../../services/rules.service';
import { environment } from '../../../../environments/environment';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit, OnDestroy, AfterViewInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private rulesService = inject(RulesService);
  auth = inject(KeycloakAuthService);

  loading = signal(true);
  sidebarItem = signal('dashboard');
  stats = signal<any>(null);
  showFilters = signal(false);
  ruleStats = signal({ totalRules: 0, activeRules: 0, pendingReview: 0, draftRules: 0, firedToday: 0, categories: 0 });

  // Filter fields
  filterFromDate = '';
  filterToDate = '';
  filterDepartment = '';
  filterMode = '';
  filterStatus = '';

  private charts: Chart[] = [];
  private refreshInterval: any;

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    // Read initial filters from query params
    const params = this.route.snapshot.queryParams;
    if (params['department']) this.filterDepartment = params['department'];
    if (params['fromDate']) this.filterFromDate = params['fromDate'];
    if (params['toDate']) this.filterToDate = params['toDate'];
    if (params['mode']) this.filterMode = params['mode'];
    if (params['status']) this.filterStatus = params['status'];

    this.loadDashboardData();
    this.loadRuleStats();
    this.refreshInterval = setInterval(() => this.loadDashboardData(), 60000);
  }

  ngAfterViewInit() {
    setTimeout(() => this.renderCharts(), 500);
  }

  ngOnDestroy() {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
    this.charts.forEach(c => c.destroy());
  }

  toggleFilters() {
    this.showFilters.set(!this.showFilters());
  }

  applyFilters() {
    this.loadDashboardData();
    this.showFilters.set(false);
  }

  resetFilters() {
    this.filterFromDate = '';
    this.filterToDate = '';
    this.filterDepartment = '';
    this.filterMode = '';
    this.filterStatus = '';
    this.loadDashboardData();
    this.showFilters.set(false);
  }

  loadDashboardData() {
    const params: string[] = [];
    if (this.filterFromDate) params.push(`fromDate=${this.filterFromDate}`);
    if (this.filterToDate) params.push(`toDate=${this.filterToDate}`);
    if (this.filterDepartment) params.push(`department=${this.filterDepartment}`);
    if (this.filterMode) params.push(`mode=${this.filterMode}`);
    if (this.filterStatus) params.push(`status=${this.filterStatus}`);
    const query = params.length ? '?' + params.join('&') : '';

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/admin/dashboard-stats${query}`).subscribe({
      next: (res) => {
        this.stats.set(res);
        this.loading.set(false);
        setTimeout(() => this.renderCharts(), 100);
      },
      error: () => {
        this.stats.set(this.getEmptyData());
        this.loading.set(false);
        setTimeout(() => this.renderCharts(), 100);
      }
    });
  }

  private getEmptyData() {
    const months = ['Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar'];
    const zeros = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0];
    return {
      complaintsByType: { CRPC: [...zeros], RBIO: [...zeros], CEPC: [...zeros], AppellateAuthority: [...zeros] },
      months,
      maintainability: { total: [...zeros], email: [...zeros], physical: [...zeros] },
      maintainabilityMonths: months,
      modeOfReceipt: { email: [...zeros], physical: [...zeros] },
      modeMonths: months,
      actionsTaken: { closed: [...zeros], nac: [...zeros], sentToRegulatory: [...zeros], sentToRbi: [...zeros] },
      actionMonths: months,
      tat: { crpc: [...zeros], rbio: [...zeros], cepc: [...zeros], average: [...zeros] },
      tatMonths: months,
      totalComplaints: 0,
      openComplaints: 0,
      resolvedComplaints: 0,
      escalatedComplaints: 0,
      slaBreached: 0
    };
  }

  private renderCharts() {
    this.charts.forEach(c => c.destroy());
    this.charts = [];

    const data = this.stats();
    if (!data) return;

    this.renderComplaintsByType(data);
    this.renderMaintainabilityPercentage(data);
    this.renderModeOfReceipt(data);
    this.renderMaintainabilityDetailed(data);
    this.renderActionsTaken(data);
    this.renderTAT(data);
  }

  private renderComplaintsByType(data: any) {
    const canvas = document.getElementById('chart-complaints-type') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: data.months,
        datasets: [
          { label: 'CRPC', data: data.complaintsByType.CRPC, backgroundColor: '#3b82f6' },
          { label: 'RBIO', data: data.complaintsByType.RBIO, backgroundColor: '#22c55e' },
          { label: 'CEPC', data: data.complaintsByType.CEPC, backgroundColor: '#f59e0b' },
          { label: 'Appellate Authority', data: data.complaintsByType.AppellateAuthority, backgroundColor: '#ef4444' },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } }
    }));
  }

  private renderMaintainabilityPercentage(data: any) {
    const canvas = document.getElementById('chart-maintainability') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'line',
      data: {
        labels: data.maintainabilityMonths,
        datasets: [
          { label: 'Total', data: data.maintainability.total, borderColor: '#22c55e', backgroundColor: 'transparent', tension: 0.3 },
          { label: 'Email', data: data.maintainability.email, borderColor: '#f59e0b', backgroundColor: 'transparent', tension: 0.3, borderDash: [5, 5] },
          { label: 'Physical Letter', data: data.maintainability.physical, borderColor: '#3b82f6', backgroundColor: 'transparent', tension: 0.3, borderDash: [5, 5] },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
    }));
  }

  private renderModeOfReceipt(data: any) {
    const canvas = document.getElementById('chart-mode-receipt') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: data.modeMonths,
        datasets: [
          { label: 'Email', data: data.modeOfReceipt.email, backgroundColor: '#1e293b' },
          { label: 'Physical Letter', data: data.modeOfReceipt.physical, backgroundColor: '#22c55e' },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } }
    }));
  }

  private renderMaintainabilityDetailed(data: any) {
    const canvas = document.getElementById('chart-maintainability-detail') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'line',
      data: {
        labels: data.maintainabilityMonths,
        datasets: [
          { label: 'Email', data: data.maintainability.email, borderColor: '#f59e0b', backgroundColor: 'transparent', tension: 0.3, borderDash: [5, 5] },
          { label: 'Physical Letter', data: data.maintainability.physical, borderColor: '#3b82f6', backgroundColor: 'transparent', tension: 0.3 },
          { label: 'Total', data: data.maintainability.total, borderColor: '#22c55e', backgroundColor: 'transparent', tension: 0.3 },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
    }));
  }

  private renderActionsTaken(data: any) {
    const canvas = document.getElementById('chart-actions') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'bar',
      data: {
        labels: data.actionMonths,
        datasets: [
          { label: 'Closed', data: data.actionsTaken.closed, backgroundColor: '#22c55e' },
          { label: 'NAC', data: data.actionsTaken.nac, backgroundColor: '#1e293b' },
          { label: 'Sent to Regulatory', data: data.actionsTaken.sentToRegulatory, backgroundColor: '#f59e0b' },
          { label: 'Sent to RBI Dept', data: data.actionsTaken.sentToRbi, backgroundColor: '#ef4444' },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } }, scales: { y: { beginAtZero: true } } }
    }));
  }

  private renderTAT(data: any) {
    const canvas = document.getElementById('chart-tat') as HTMLCanvasElement;
    if (!canvas) return;
    this.charts.push(new Chart(canvas, {
      type: 'line',
      data: {
        labels: data.tatMonths,
        datasets: [
          { label: 'CRPC', data: data.tat.crpc, borderColor: '#1e293b', backgroundColor: 'transparent', tension: 0.3 },
          { label: 'RBIO', data: data.tat.rbio, borderColor: '#ef4444', backgroundColor: 'transparent', tension: 0.3 },
          { label: 'CEPC', data: data.tat.cepc, borderColor: '#22c55e', backgroundColor: 'transparent', tension: 0.3 },
          { label: 'Average', data: data.tat.average, borderColor: '#f59e0b', backgroundColor: 'transparent', tension: 0.3, borderDash: [5, 5] },
        ]
      },
      options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }
    }));
  }

  loadRuleStats() {
    this.rulesService.getRules().subscribe({
      next: (rules) => {
        this.ruleStats.set({
          totalRules: rules.length,
          activeRules: rules.filter(r => r.status === 'ACTIVE').length,
          pendingReview: rules.filter(r => r.status === 'PENDING_REVIEW').length,
          draftRules: rules.filter(r => r.status === 'DRAFT').length,
          firedToday: 0,
          categories: new Set(rules.map(r => r.categoryCode)).size
        });
      },
      error: () => {}
    });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/rules/stats/fired-today`).subscribe({
      next: (res) => this.ruleStats.update(s => ({ ...s, firedToday: res.count || 0 })),
      error: () => {}
    });
  }

  navigateTo(item: string) {
    this.sidebarItem.set(item);
    if (item === 'complaints') {
      this.router.navigate(['/crpc/home']);
    } else if (item === 'rules') {
      this.router.navigate(['/admin/rules']);
    } else if (item === 'extraction-rules') {
      this.router.navigate(['/admin/extraction-rules']);
    }
  }

  async logout() {
    await this.auth.logout();
  }
}
