import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface SearchResult {
  complaintId: string;
  subject: string;
  description: string;
  category: string;
  status: string;
  priority: string;
  complainantName: string;
  entityName: string;
  assignedTeam: string;
  amountInvolved: number;
  createdAt: string;
  resolutionSummary: string;
}

export interface SearchFilters {
  q: string;
  category?: string;
  status?: string;
  priority?: string;
  team?: string;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class SearchService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/search`;

  searchComplaints(filters: SearchFilters): Observable<SearchResult[]> {
    let params = new HttpParams().set('q', filters.q);
    if (filters.category) params = params.set('category', filters.category);
    if (filters.status) params = params.set('status', filters.status);
    if (filters.priority) params = params.set('priority', filters.priority);
    if (filters.team) params = params.set('team', filters.team);
    params = params.set('page', (filters.page || 0).toString());
    params = params.set('size', (filters.size || 20).toString());

    return this.http.get<ApiResponse<SearchResult[]>>(`${this.baseUrl}/complaints`, { params })
      .pipe(map(res => res.data));
  }

  reindex(): Observable<any> {
    return this.http.post<ApiResponse<any>>(`${this.baseUrl}/reindex`, null)
      .pipe(map(res => res.data));
  }
}
