import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface PipelineSummary {
  total: number;
  pending: number;
  inProgress: number;
  resolved: number;
  escalated: number;
  closed: number;
  activeBacklog: number;
  resolutionRate: number;
  computedAt: string;
}

export interface TatAnalytics {
  totalActive: number;
  breached: number;
  atRisk: number;
  onTrack: number;
  avgElapsedDays: number;
  breachRate: number;
  computedAt: string;
}

export interface Bottlenecks {
  backlogByDepartment: Record<string, number>;
  backlogByCategory: Record<string, number>;
  volumeByPriority: Record<string, number>;
  unassignedByDepartment: Record<string, number>;
  computedAt: string;
}

export interface WeeklyTrend {
  weekLabel: string;
  filed: number;
  resolved: number;
  net: number;
}

export interface EntityPerformance {
  volumeByEntity: Record<string, number>;
  breachByEntity: Record<string, number>;
  resolvedByEntity: Record<string, number>;
  statusByDepartment: Record<string, Record<string, number>>;
}

export interface DashboardSummary {
  pipeline: PipelineSummary;
  tat: TatAnalytics;
  bottlenecks: Bottlenecks;
  trend: WeeklyTrend[];
  entityPerformance: EntityPerformance;
}

@Injectable({ providedIn: 'root' })
export class SeniorDashboardService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/senior-dashboard`;

  getFullSummary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.baseUrl}/summary`);
  }

  getPipeline(): Observable<PipelineSummary> {
    return this.http.get<PipelineSummary>(`${this.baseUrl}/pipeline`);
  }

  getTat(): Observable<TatAnalytics> {
    return this.http.get<TatAnalytics>(`${this.baseUrl}/tat`);
  }

  getBottlenecks(): Observable<Bottlenecks> {
    return this.http.get<Bottlenecks>(`${this.baseUrl}/bottlenecks`);
  }

  getTrend(): Observable<WeeklyTrend[]> {
    return this.http.get<WeeklyTrend[]>(`${this.baseUrl}/trend`);
  }
}
