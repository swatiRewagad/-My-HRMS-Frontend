import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { DashboardService, DashboardData } from '../../services/dashboard.service';
import {
  DASHBOARD_STATS, REALM_SERVICE_BARS, TOP_SERVICES,
  SERVICE_CATEGORIES, STATUS_DISTRIBUTION, DEPLOYMENT_TYPES,
  RECENT_CONFIGS, REGISTERED_SERVICES, RECENT_ACTIVITY,
} from '../../data/dashboard.data';

const CATEGORY_COLORS: Record<string, string> = {
  'AI / ML': '#2563eb',
  'Authentication': '#16a34a',
  'Document': '#ea580c',
  'Infrastructure': '#0891b2',
  'Messaging': '#d97706',
  'Security': '#dc2626',
};

const ACTIVITY_ICONS: Record<string, { icon: string; color: string; bg: string }> = {
  'Realm configured': { icon: 'pi pi-cog', color: '#1a56db', bg: '#eef4ff' },
  'Service registered': { icon: 'pi pi-check-circle', color: '#16a34a', bg: '#f0fdf4' },
  'Deployment type changed': { icon: 'pi pi-pencil', color: '#d97706', bg: '#fffbeb' },
  'Service deprecated': { icon: 'pi pi-exclamation-triangle', color: '#dc2626', bg: '#fef2f2' },
  'Version updated': { icon: 'pi pi-refresh', color: '#64748b', bg: '#f8fafc' },
  'Service deregistered': { icon: 'pi pi-times-circle', color: '#dc2626', bg: '#fef2f2' },
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private dashboardService = inject(DashboardService);

  loading = true;
  stats = DASHBOARD_STATS;
  realmBars = REALM_SERVICE_BARS;
  topServices = TOP_SERVICES;
  categories = SERVICE_CATEGORIES;
  statusDist = STATUS_DISTRIBUTION;
  deployTypes = DEPLOYMENT_TYPES;
  recentConfigs = RECENT_CONFIGS;
  registeredServices = REGISTERED_SERVICES;
  activity = RECENT_ACTIVITY;
  maxCategoryCount = Math.max(...SERVICE_CATEGORIES.map(c => c.count));

  ngOnInit() {
    this.dashboardService.getDashboard().subscribe({
      next: (data) => this.applyData(data),
      error: () => this.loading = false,
    });
  }

  private applyData(data: DashboardData) {
    this.stats = data.stats;

    this.realmBars = data.realmServiceBars.map((b, i) => ({
      ...b,
      color: ['#1a56db', '#2563eb', '#7c3aed', '#0891b2'][i % 4],
    }));

    this.topServices = data.topServices;

    this.categories = data.serviceCategories.map(c => ({
      ...c,
      color: CATEGORY_COLORS[c.category] || '#64748b',
    }));
    this.maxCategoryCount = Math.max(...this.categories.map(c => c.count), 1);

    this.statusDist = data.statusDistribution;
    this.deployTypes = data.deploymentTypes;

    this.recentConfigs = data.recentConfigs.map((c, i) => ({
      ...c,
      status: c.status as 'Active' | 'Pending' | 'Draft',
      color: ['#1a56db', '#6941c6', '#d97706', '#64748b'][i % 4],
    }));

    this.registeredServices = data.registeredServices.map(s => ({
      ...s,
      status: s.status as 'Active' | 'Inactive',
    }));

    this.activity = data.recentActivity.map(a => {
      const iconInfo = ACTIVITY_ICONS[a.action] || { icon: 'pi pi-info-circle', color: '#64748b', bg: '#f8fafc' };
      return {
        icon: iconInfo.icon,
        iconColor: iconInfo.color,
        iconBg: iconInfo.bg,
        title: a.action,
        subtitle: a.entityName,
        time: this.timeAgo(a.performedAt),
        badge: a.entityType,
        badgeBg: iconInfo.bg,
      };
    });

    this.loading = false;
  }

  private timeAgo(dateStr: string): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins < 60) return `${diffMins} minutes ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} hours ago`;
    const diffDays = Math.floor(diffHours / 24);
    return `${diffDays} days ago`;
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'Active': return '#16a34a';
      case 'Pending': return '#d97706';
      case 'Draft': return '#64748b';
      case 'Inactive': return '#dc2626';
      default: return '#64748b';
    }
  }

  getStatusBg(status: string): string {
    switch (status) {
      case 'Active': return '#f0fdf4';
      case 'Pending': return '#fffbeb';
      case 'Draft': return '#f8fafc';
      case 'Inactive': return '#fef2f2';
      default: return '#f8fafc';
    }
  }

  getDonutOffset(index: number): number {
    const values = [
      this.statusDist.active,
      this.statusDist.inactive,
      this.statusDist.deprecated,
    ];
    const total = this.statusDist.total;
    let offset = 25;
    for (let i = 0; i < index; i++) {
      offset -= (values[i] / total) * 100;
    }
    return offset;
  }

  getDonutDash(value: number): string {
    const total = this.statusDist.total || 1;
    const pct = (value / total) * 100;
    return `${pct} ${100 - pct}`;
  }
}
