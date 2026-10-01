import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface DashboardStats {
  totalComplaints: number;
  openComplaints: number;
  resolvedComplaints: number;
  escalatedComplaints: number;
  slaBreached: number;
  avgResolutionHours: number;
  statusBreakdown: Record<string, number>;
  categoryBreakdown: Record<string, number>;
  priorityBreakdown: Record<string, number>;
  teamWorkload: Record<string, number>;
  recentComplaints: RecentComplaint[];
  slaBreachedComplaints: SlaBreachedComplaint[];
}

export interface RecentComplaint {
  complaintId: string;
  subject: string;
  category: string;
  status: string;
  priority: string;
  assignedTeam: string;
  createdAt: string;
}

export interface SlaBreachedComplaint {
  complaintId: string;
  subject: string;
  status: string;
  assignedTeam: string;
  slaDueDate: string;
  overdueDays: number;
}

@Injectable({ providedIn: 'root' })
export class AdminService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/admin/dashboard`;

  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<ApiResponse<DashboardStats>>(`${this.baseUrl}/stats`)
      .pipe(map(res => res.data));
  }
}
