import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-in-charge-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <header class="dashboard-header">
        <h1>CRPC In-Charge Dashboard</h1>
        <p class="subtitle">Pan-India Overview (Read-Only)</p>
      </header>

      @if (loading()) {
        <div class="loading-state">Loading dashboard data...</div>
      } @else {
        <div class="stats-grid">
          <div class="stat-card">
            <span class="stat-value">{{ summary()?.totalDrafts || 0 }}</span>
            <span class="stat-label">Total Drafts</span>
          </div>
          <div class="stat-card pending">
            <span class="stat-value">{{ summary()?.pendingApproval || 0 }}</span>
            <span class="stat-label">Pending Approval</span>
          </div>
          <div class="stat-card success">
            <span class="stat-value">{{ summary()?.converted || 0 }}</span>
            <span class="stat-label">Converted</span>
          </div>
          <div class="stat-card warning">
            <span class="stat-value">{{ summary()?.sentBack || 0 }}</span>
            <span class="stat-label">Sent Back</span>
          </div>
          <div class="stat-card muted">
            <span class="stat-value">{{ summary()?.notAComplaint || 0 }}</span>
            <span class="stat-label">Not-a-Complaint</span>
          </div>
          <div class="stat-card info">
            <span class="stat-value">{{ summary()?.totalComplaints || 0 }}</span>
            <span class="stat-label">Total Complaints</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ summary()?.pendingComplaints || 0 }}</span>
            <span class="stat-label">Pending Complaints</span>
          </div>
          <div class="stat-card success">
            <span class="stat-value">{{ summary()?.resolvedComplaints || 0 }}</span>
            <span class="stat-label">Resolved</span>
          </div>
        </div>

        <section class="section">
          <h2>DEO Workload</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>DEO</th>
                  <th>Total Assigned</th>
                  <th>Pending</th>
                  <th>Sent for Approval</th>
                  <th>Not-a-Complaint</th>
                </tr>
              </thead>
              <tbody>
                @for (deo of deoStats(); track deo.deo) {
                  <tr>
                    <td>{{ deo.deo }}</td>
                    <td>{{ deo.total }}</td>
                    <td>{{ deo.pending }}</td>
                    <td>{{ deo.sentForApproval }}</td>
                    <td>{{ deo.notAComplaint }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="5" class="empty">No DEO data available</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .dashboard-container { padding: 24px; max-width: 1200px; margin: 0 auto; }
    .dashboard-header { margin-bottom: 24px; }
    .dashboard-header h1 { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
    .subtitle { color: #64748b; margin: 4px 0 0; }
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; margin-bottom: 32px; }
    .stat-card { background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                 display: flex; flex-direction: column; border-left: 4px solid #e2e8f0; }
    .stat-card.pending { border-left-color: #f59e0b; }
    .stat-card.success { border-left-color: #10b981; }
    .stat-card.warning { border-left-color: #ef4444; }
    .stat-card.muted { border-left-color: #94a3b8; }
    .stat-card.info { border-left-color: #3b82f6; }
    .stat-value { font-size: 28px; font-weight: 700; color: #1e293b; }
    .stat-label { font-size: 13px; color: #64748b; margin-top: 4px; }
    .section { margin-top: 32px; }
    .section h2 { font-size: 18px; font-weight: 600; margin: 0 0 16px; }
    .table-wrap { overflow-x: auto; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f8fafc; padding: 12px 16px; text-align: left; font-size: 13px; font-weight: 600; color: #475569; border-bottom: 1px solid #e2e8f0; }
    td { padding: 12px 16px; font-size: 14px; color: #334155; border-bottom: 1px solid #f1f5f9; }
    .empty { text-align: center; color: #94a3b8; padding: 24px; }
    .loading-state { text-align: center; padding: 60px; color: #64748b; }
  `]
})
export class InChargeDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/crpc/in-charge`;

  summary = signal<any>(null);
  deoStats = signal<any[]>([]);
  loading = signal(true);

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.http.get<any>(`${this.baseUrl}/summary`).subscribe(data => {
      this.summary.set(data);
      this.loading.set(false);
    });
    this.http.get<any>(`${this.baseUrl}/deo-workload`).subscribe(data => {
      this.deoStats.set(data.deoStats || []);
    });
  }
}
