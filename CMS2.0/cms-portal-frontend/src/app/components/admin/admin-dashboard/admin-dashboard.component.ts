import { Component, inject, signal, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AdminService, DashboardStats } from '../../../services/admin.service';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrl: './admin-dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit, OnDestroy {

  private adminService = inject(AdminService);
  private router = inject(Router);

  stats = signal<DashboardStats | null>(null);
  loading = signal(true);
  error = signal('');
  lastRefreshed = signal('');

  private refreshInterval: any;

  ngOnInit() {
    this.loadStats();
    this.refreshInterval = setInterval(() => this.loadStats(), 30000);
  }

  ngOnDestroy() {
    if (this.refreshInterval) clearInterval(this.refreshInterval);
  }

  loadStats() {
    this.adminService.getDashboardStats().subscribe({
      next: (data) => {
        this.stats.set(data);
        this.loading.set(false);
        this.lastRefreshed.set(new Date().toLocaleTimeString());
      },
      error: () => {
        this.error.set('Failed to load dashboard stats.');
        this.loading.set(false);
      }
    });
  }

  getStatusKeys(): string[] {
    return Object.keys(this.stats()?.statusBreakdown || {});
  }

  getCategoryKeys(): string[] {
    return Object.keys(this.stats()?.categoryBreakdown || {});
  }

  getTeamKeys(): string[] {
    return Object.keys(this.stats()?.teamWorkload || {});
  }

  getPriorityKeys(): string[] {
    return Object.keys(this.stats()?.priorityBreakdown || {});
  }

  getStatusColor(status: string): string {
    const colors: Record<string, string> = {
      'NEW': '#3498db', 'ASSIGNED': '#9b59b6', 'IN_PROGRESS': '#f39c12',
      'UNDER_REVIEW': '#e67e22', 'ESCALATED': '#e74c3c', 'RESOLVED': '#27ae60', 'CLOSED': '#95a5a6'
    };
    return colors[status] || '#bdc3c7';
  }

  getPriorityColor(priority: string): string {
    const colors: Record<string, string> = { 'HIGH': '#e74c3c', 'MEDIUM': '#f39c12', 'LOW': '#27ae60' };
    return colors[priority] || '#bdc3c7';
  }

  getBarWidth(value: number): number {
    const total = this.stats()?.totalComplaints || 1;
    return Math.max((value / total) * 100, 2);
  }

  navigateToComplaint(id: string) {
    this.router.navigate(['/officer/complaint', id]);
  }

  goBack() {
    this.router.navigate(['/']);
  }
}
