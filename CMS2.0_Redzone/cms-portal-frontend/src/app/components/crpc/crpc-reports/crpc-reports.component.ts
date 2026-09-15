import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface ReportRow {
  [key: string]: any;
}

interface ReportConfig {
  id: string;
  name: string;
  description: string;
  columns: { key: string; label: string }[];
}

@Component({
  selector: 'app-crpc-reports',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './crpc-reports.component.html',
  styleUrl: './crpc-reports.component.scss'
})
export class CrpcReportsComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);

  // Report Selection
  selectedReport = signal<string>('daily-intake');
  reportConfigs: ReportConfig[] = [
    { id: 'daily-intake', name: 'Daily Intake Report', description: 'Complaints received per day by mode of receipt', columns: [
      { key: 'date', label: 'Date' }, { key: 'email', label: 'Email' }, { key: 'physical', label: 'Physical' },
      { key: 'cpgrams', label: 'CPGRAMS' }, { key: 'portal', label: 'Portal' }, { key: 'total', label: 'Total' }
    ]},
    { id: 'deo-productivity', name: 'DEO Productivity Report', description: 'Complaints processed per DEO', columns: [
      { key: 'deoName', label: 'DEO Name' }, { key: 'processed', label: 'Processed' }, { key: 'pending', label: 'Pending' },
      { key: 'avgTime', label: 'Avg Time (hrs)' }, { key: 'nmCount', label: 'Not Maintainable' }, { key: 'maintainable', label: 'Maintainable' }
    ]},
    { id: 'reviewer-workload', name: 'Reviewer Workload Report', description: 'Current and completed workload per reviewer', columns: [
      { key: 'reviewerName', label: 'Reviewer' }, { key: 'assigned', label: 'Assigned' }, { key: 'completed', label: 'Completed' },
      { key: 'pendingReview', label: 'Pending' }, { key: 'approved', label: 'Approved' }, { key: 'sentBack', label: 'Sent Back' }
    ]},
    { id: 'closure-analysis', name: 'Closure Analysis Report', description: 'Auto-closure and rejection breakdown', columns: [
      { key: 'clauseRef', label: 'Clause Reference' }, { key: 'count', label: 'Count' }, { key: 'percentage', label: '%' },
      { key: 'schemeVersion', label: 'Scheme' }, { key: 'entityType', label: 'Entity Type' }
    ]},
    { id: 'sla-compliance', name: 'SLA Compliance Report', description: 'SLA adherence across offices', columns: [
      { key: 'office', label: 'Office' }, { key: 'totalCases', label: 'Total Cases' }, { key: 'withinSla', label: 'Within SLA' },
      { key: 'breached', label: 'Breached' }, { key: 'complianceRate', label: 'Compliance %' }
    ]},
    { id: 'transfer-summary', name: 'Transfer Summary Report', description: 'Inter-office transfer statistics', columns: [
      { key: 'fromOffice', label: 'From' }, { key: 'toOffice', label: 'To' }, { key: 'count', label: 'Count' },
      { key: 'approved', label: 'Approved' }, { key: 'rejected', label: 'Rejected' }, { key: 'pending', label: 'Pending' }
    ]},
    { id: 'entity-wise', name: 'Entity-Wise Report', description: 'Complaints grouped by regulated entity', columns: [
      { key: 'entityName', label: 'Entity' }, { key: 'totalComplaints', label: 'Total' }, { key: 'resolved', label: 'Resolved' },
      { key: 'pending', label: 'Pending' }, { key: 'avgResolutionDays', label: 'Avg Days' }
    ]},
  ];

  // Filters
  dateFrom = '';
  dateTo = '';
  schemeVersionFilter = '';
  officeFilter = '';
  statusFilter = '';

  // Data
  reportData = signal<ReportRow[]>([]);
  loading = signal(false);
  totalRecords = signal(0);

  // Pagination
  currentPage = signal(1);
  pageSize = 20;

  activeConfig = computed(() => this.reportConfigs.find(r => r.id === this.selectedReport()) || this.reportConfigs[0]);

  paginatedData = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.reportData().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.max(1, Math.ceil(this.reportData().length / this.pageSize)));

  ngOnInit() {
    const today = new Date();
    this.dateTo = today.toISOString().split('T')[0];
    const thirtyDaysAgo = new Date(today.getTime() - 30 * 86400000);
    this.dateFrom = thirtyDaysAgo.toISOString().split('T')[0];
    this.generateReport();
  }

  onReportChange(reportId: string) {
    this.selectedReport.set(reportId);
    this.currentPage.set(1);
    this.generateReport();
  }

  generateReport() {
    this.loading.set(true);
    const params: any = { reportType: this.selectedReport() };
    if (this.dateFrom) params.dateFrom = this.dateFrom;
    if (this.dateTo) params.dateTo = this.dateTo;
    if (this.schemeVersionFilter) params.schemeVersion = this.schemeVersionFilter;
    if (this.officeFilter) params.office = this.officeFilter;

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/crpc/reports`, { params }).subscribe({
      next: (res) => {
        const data = Array.isArray(res) ? res : (res?.data || []);
        this.reportData.set(data);
        this.totalRecords.set(data.length);
        this.loading.set(false);
      },
      error: () => {
        this.reportData.set(this.getMockData());
        this.totalRecords.set(this.reportData().length);
        this.loading.set(false);
      }
    });
  }

  exportCsv() {
    const config = this.activeConfig();
    const headers = config.columns.map(c => c.label).join(',');
    const rows = this.reportData().map(row =>
      config.columns.map(c => `"${(row[c.key] ?? '').toString().replace(/"/g, '""')}"`).join(',')
    );
    const csv = [headers, ...rows].join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${config.id}_${this.dateFrom}_${this.dateTo}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  exportExcel() {
    const params: any = { reportType: this.selectedReport(), format: 'excel' };
    if (this.dateFrom) params.dateFrom = this.dateFrom;
    if (this.dateTo) params.dateTo = this.dateTo;
    if (this.schemeVersionFilter) params.schemeVersion = this.schemeVersionFilter;

    this.http.get(`${environment.apiBaseUrl}/api/v1/crpc/reports/export`, {
      params, responseType: 'blob'
    }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${this.activeConfig().id}_report.xlsx`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.exportCsv()
    });
  }

  goBack() {
    this.router.navigate(['/crpc/home']);
  }

  private getMockData(): ReportRow[] {
    const config = this.activeConfig();
    const rows: ReportRow[] = [];
    for (let i = 0; i < 15; i++) {
      const row: ReportRow = {};
      config.columns.forEach(col => {
        if (col.key.includes('date') || col.key === 'date') row[col.key] = new Date(Date.now() - i * 86400000).toISOString().split('T')[0];
        else if (col.key.includes('Name') || col.key.includes('office') || col.key === 'entityName' || col.key === 'clauseRef') row[col.key] = `Sample ${col.label} ${i + 1}`;
        else if (col.key.includes('percentage') || col.key.includes('Rate')) row[col.key] = (70 + Math.random() * 25).toFixed(1) + '%';
        else row[col.key] = Math.floor(Math.random() * 50) + 1;
      });
      rows.push(row);
    }
    return rows;
  }
}
