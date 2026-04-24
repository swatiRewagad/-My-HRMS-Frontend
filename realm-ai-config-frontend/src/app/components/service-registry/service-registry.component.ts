import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { ServiceRegistryService, RegisteredServiceDto, ServiceRegistryStats } from '../../services/service-registry.service';

@Component({
  selector: 'app-service-registry',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './service-registry.component.html',
  styleUrl: './service-registry.component.scss',
})
export class ServiceRegistryComponent implements OnInit {
  private svc = inject(ServiceRegistryService);

  stats: ServiceRegistryStats = {
    totalServices: 0, activeServices: 0, inactiveServices: 0, deprecatedServices: 0, services: [],
  };
  filteredServices: RegisteredServiceDto[] = [];

  searchTerm = '';
  selectedCategory = '';
  selectedStatus = '';

  categories = ['AI & ML', 'Data & Analytics', 'Communication', 'Storage', 'Identity', 'Infrastructure', 'Authentication', 'Security'];

  ngOnInit() {
    this.loadData();
  }

  loadData() {
    const category = this.selectedCategory || undefined;
    const status = this.selectedStatus || undefined;
    const search = this.searchTerm.trim() || undefined;

    this.svc.getRegistry(search, category, status).subscribe({
      next: (data) => {
        this.stats = data;
        this.filteredServices = data.services;
      },
    });
  }

  onSearch() {
    this.loadData();
  }

  onCategoryChange() {
    this.loadData();
  }

  onStatusFilter(status: string) {
    this.selectedStatus = this.selectedStatus === status ? '' : status;
    this.loadData();
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'Active': return '#16a34a';
      case 'Inactive': return '#d97706';
      case 'Deprecated': return '#dc2626';
      default: return '#64748b';
    }
  }

  getStatusBg(status: string): string {
    switch (status) {
      case 'Active': return '#f0fdf4';
      case 'Inactive': return '#fffbeb';
      case 'Deprecated': return '#fef2f2';
      default: return '#f8fafc';
    }
  }

  getCategoryColor(category: string): string {
    const map: Record<string, string> = {
      'AI & ML': '#7c3aed',
      'Data & Analytics': '#2563eb',
      'Communication': '#0891b2',
      'Storage': '#16a34a',
      'Identity': '#6941c6',
      'Infrastructure': '#64748b',
      'Authentication': '#ea580c',
      'Security': '#dc2626',
    };
    return map[category] || '#64748b';
  }

  getCategoryBg(category: string): string {
    const map: Record<string, string> = {
      'AI & ML': '#f5f3ff',
      'Data & Analytics': '#eef4ff',
      'Communication': '#ecfeff',
      'Storage': '#f0fdf4',
      'Identity': '#f5f3ff',
      'Infrastructure': '#f8fafc',
      'Authentication': '#fff7ed',
      'Security': '#fef2f2',
    };
    return map[category] || '#f8fafc';
  }
}
