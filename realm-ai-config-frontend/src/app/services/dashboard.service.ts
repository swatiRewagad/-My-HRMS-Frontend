import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface DashboardData {
  stats: {
    realmsConfigured: number;
    realmsActive: number;
    servicesRegistered: number;
    servicesActive: number;
    deprecatedServices: number;
    platformVersion: string;
    platformReleaseDate: string;
  };
  realmServiceBars: {
    realmName: string;
    realmInitials: string;
    servicesConfigured: number;
    capacity: number;
  }[];
  topServices: string[];
  serviceCategories: {
    category: string;
    count: number;
  }[];
  statusDistribution: {
    total: number;
    active: number;
    inactive: number;
    deprecated: number;
  };
  deploymentTypes: {
    independent: number;
    dependent: number;
    modeBreakdown: {
      appDesigner: number;
      nonAppDesigner: number;
    };
  };
  recentConfigs: {
    realmName: string;
    realmInitials: string;
    version: string;
    deployType: string;
    status: string;
    configuredBy: string;
    configuredAt: string;
  }[];
  registeredServices: {
    name: string;
    slug: string;
    version: string;
    category: string;
    status: string;
  }[];
  recentActivity: {
    action: string;
    entityType: string;
    entityName: string;
    performedBy: string;
    performedAt: string;
  }[];
}

@Injectable({ providedIn: 'root' })
export class DashboardService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>(`${this.apiUrl}/dashboard`);
  }
}
