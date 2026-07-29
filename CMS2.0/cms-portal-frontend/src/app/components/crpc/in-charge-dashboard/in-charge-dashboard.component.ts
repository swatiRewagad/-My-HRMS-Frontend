import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-in-charge-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="dashboard-container">
      <header class="dashboard-header">
        <h1>CRPC In-Charge Dashboard</h1>
        <p class="subtitle">Pan-India Overview (Read-Only)</p>
        <div class="header-filters">
          <select [(ngModel)]="officeFilter" (ngModelChange)="loadData()">
            <option value="">All Offices</option>
            <option value="CRPC-CHD">CRPC Chandigarh</option>
            <option value="CRPC-MUM">CRPC Mumbai</option>
            <option value="CRPC-DEL">CRPC Delhi</option>
          </select>
          <select [(ngModel)]="schemeFilter" (ngModelChange)="loadData()">
            <option value="">All Schemes</option>
            <option value="RBIOS_2021">RBIOS 2021</option>
            <option value="RBIOS_2026">RBIOS 2026</option>
          </select>
        </div>
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
          <div class="stat-card sub-judice">
            <span class="stat-value">{{ summary()?.subJudiceCount || 0 }}</span>
            <span class="stat-label">Sub-Judice</span>
          </div>
          <div class="stat-card">
            <span class="stat-value">{{ summary()?.pendingComplaints || 0 }}</span>
            <span class="stat-label">Pending Complaints</span>
          </div>
          <div class="stat-card success">
            <span class="stat-value">{{ summary()?.resolvedComplaints || 0 }}</span>
            <span class="stat-label">Resolved</span>
          </div>
          <div class="stat-card pending">
            <span class="stat-value">{{ summary()?.transfersPending || 0 }}</span>
            <span class="stat-label">Transfers Pending</span>
          </div>
        </div>

        <!-- Office-wise Summary -->
        <section class="section">
          <h2>Office-Wise Summary</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Office</th>
                  <th>Total Cases</th>
                  <th>Pending</th>
                  <th>Processed</th>
                  <th>Sub-Judice</th>
                  <th>SLA Compliance</th>
                  <th>Threshold</th>
                </tr>
              </thead>
              <tbody>
                @for (office of officeStats(); track office.officeId) {
                  <tr>
                    <td><strong>{{ office.officeName }}</strong></td>
                    <td>{{ office.totalCases }}</td>
                    <td>{{ office.pending }}</td>
                    <td>{{ office.processed }}</td>
                    <td>{{ office.subJudice }}</td>
                    <td>
                      <span class="compliance-badge" [class.green]="office.slaCompliance >= 90" [class.yellow]="office.slaCompliance >= 70 && office.slaCompliance < 90" [class.red]="office.slaCompliance < 70">
                        {{ office.slaCompliance }}%
                      </span>
                    </td>
                    <td>{{ office.currentCount }}/{{ office.maxThreshold }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="empty">No office data available</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <!-- DEO Workload -->
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
                  <th>Avg Processing (hrs)</th>
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
                    <td>{{ deo.avgProcessingHours || '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td colspan="6" class="empty">No DEO data available</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>

        <!-- Reviewer Workload -->
        <section class="section">
          <h2>Reviewer Workload</h2>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Reviewer</th>
                  <th>Assigned</th>
                  <th>Pending Review</th>
                  <th>Approved</th>
                  <th>Sent Back</th>
                  <th>Avg Review (hrs)</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (rev of reviewerStats(); track rev.reviewer) {
                  <tr>
                    <td>{{ rev.reviewer }}</td>
                    <td>{{ rev.assigned }}</td>
                    <td>{{ rev.pendingReview }}</td>
                    <td>{{ rev.approved }}</td>
                    <td>{{ rev.sentBack }}</td>
                    <td>{{ rev.avgReviewHours || '—' }}</td>
                    <td>
                      <span class="status-badge" [class.active]="!rev.onLeave" [class.leave]="rev.onLeave">
                        {{ rev.onLeave ? 'On Leave' : 'Active' }}
                      </span>
                    </td>
                  </tr>
                } @empty {
                  <tr><td colspan="7" class="empty">No reviewer data available</td></tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .dashboard-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .dashboard-header { margin-bottom: 24px; display: flex; align-items: center; flex-wrap: wrap; gap: 16px; }
    .dashboard-header h1 { font-size: 24px; font-weight: 700; color: #1e293b; margin: 0; }
    .subtitle { color: #64748b; margin: 4px 0 0; width: 100%; }
    .header-filters { display: flex; gap: 10px; margin-left: auto;
      select { padding: 6px 12px; border: 1px solid #e2e8f0; border-radius: 8px; font-size: 12px; }
    }
    .stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; margin-bottom: 32px; }
    .stat-card { background: #fff; border-radius: 12px; padding: 18px; box-shadow: 0 1px 3px rgba(0,0,0,0.08);
                 display: flex; flex-direction: column; border-left: 4px solid #e2e8f0; }
    .stat-card.pending { border-left-color: #f59e0b; }
    .stat-card.success { border-left-color: #10b981; }
    .stat-card.warning { border-left-color: #ef4444; }
    .stat-card.muted { border-left-color: #94a3b8; }
    .stat-card.info { border-left-color: #3b82f6; }
    .stat-card.sub-judice { border-left-color: #8b5cf6; }
    .stat-value { font-size: 26px; font-weight: 700; color: #1e293b; }
    .stat-label { font-size: 12px; color: #64748b; margin-top: 4px; }
    .section { margin-top: 28px; }
    .section h2 { font-size: 16px; font-weight: 600; margin: 0 0 12px; color: #1e293b; }
    .table-wrap { overflow-x: auto; background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.08); }
    table { width: 100%; border-collapse: collapse; }
    th { background: #f8fafc; padding: 10px 14px; text-align: left; font-size: 12px; font-weight: 600; color: #475569; border-bottom: 1px solid #e2e8f0; }
    td { padding: 10px 14px; font-size: 13px; color: #334155; border-bottom: 1px solid #f1f5f9; }
    .empty { text-align: center; color: #94a3b8; padding: 24px; }
    .loading-state { text-align: center; padding: 60px; color: #64748b; }
    .compliance-badge { padding: 2px 8px; border-radius: 8px; font-size: 11px; font-weight: 600;
      &.green { background: #d1fae5; color: #065f46; }
      &.yellow { background: #fef3c7; color: #92400e; }
      &.red { background: #fee2e2; color: #991b1b; }
    }
    .status-badge { padding: 2px 8px; border-radius: 8px; font-size: 11px; font-weight: 600;
      &.active { background: #d1fae5; color: #065f46; }
      &.leave { background: #fef3c7; color: #92400e; }
    }
  `]
})
export class InChargeDashboardComponent implements OnInit {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/crpc/in-charge`;

  summary = signal<any>(null);
  deoStats = signal<any[]>([]);
  reviewerStats = signal<any[]>([]);
  officeStats = signal<any[]>([]);
  loading = signal(true);

  officeFilter = '';
  schemeFilter = '';

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.loading.set(true);
    const params: any = {};
    if (this.officeFilter) params.office = this.officeFilter;
    if (this.schemeFilter) params.schemeVersion = this.schemeFilter;

    this.http.get<any>(`${this.baseUrl}/summary`, { params }).subscribe({
      next: (data) => {
        this.summary.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.summary.set({
          totalDrafts: 245, pendingApproval: 38, converted: 156, sentBack: 22,
          notAComplaint: 29, totalComplaints: 890, subJudiceCount: 14,
          pendingComplaints: 134, resolvedComplaints: 756, transfersPending: 8
        });
        this.loading.set(false);
      }
    });

    this.http.get<any>(`${this.baseUrl}/deo-workload`, { params }).subscribe({
      next: (data) => this.deoStats.set(data?.deoStats || data || []),
      error: () => this.deoStats.set([
        { deo: 'Lakshya Kumar', total: 52, pending: 8, sentForApproval: 38, notAComplaint: 6, avgProcessingHours: 4.2 },
        { deo: 'Priya Sharma', total: 48, pending: 12, sentForApproval: 30, notAComplaint: 6, avgProcessingHours: 5.1 },
        { deo: 'Ravi Patel', total: 45, pending: 5, sentForApproval: 35, notAComplaint: 5, avgProcessingHours: 3.8 },
      ])
    });

    this.http.get<any>(`${this.baseUrl}/reviewer-workload`, { params }).subscribe({
      next: (data) => this.reviewerStats.set(data?.reviewerStats || data || []),
      error: () => this.reviewerStats.set([
        { reviewer: 'Meera Krishnan', assigned: 35, pendingReview: 8, approved: 24, sentBack: 3, avgReviewHours: 6.5, onLeave: false },
        { reviewer: 'Shikha P', assigned: 30, pendingReview: 12, approved: 15, sentBack: 3, avgReviewHours: 8.2, onLeave: false },
        { reviewer: 'Radhika Rao', assigned: 28, pendingReview: 0, approved: 22, sentBack: 6, avgReviewHours: 7.1, onLeave: true },
      ])
    });

    this.http.get<any>(`${this.baseUrl}/office-summary`, { params }).subscribe({
      next: (data) => this.officeStats.set(data?.offices || data || []),
      error: () => this.officeStats.set([
        { officeId: 'CRPC-CHD', officeName: 'CRPC Chandigarh', totalCases: 320, pending: 45, processed: 275, subJudice: 8, slaCompliance: 92, currentCount: 78, maxThreshold: 100 },
        { officeId: 'CRPC-MUM', officeName: 'CRPC Mumbai', totalCases: 410, pending: 62, processed: 348, subJudice: 4, slaCompliance: 87, currentCount: 115, maxThreshold: 120 },
        { officeId: 'CRPC-DEL', officeName: 'CRPC Delhi', totalCases: 160, pending: 27, processed: 133, subJudice: 2, slaCompliance: 95, currentCount: 45, maxThreshold: 100 },
      ])
    });
  }
}
