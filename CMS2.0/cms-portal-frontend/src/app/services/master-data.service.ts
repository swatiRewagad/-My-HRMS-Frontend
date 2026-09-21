import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface CategoryMaster {
  id: number;
  categoryName: string;
  subCategory: string;
  schemeVersion: string;
  entityType: string;
  active: boolean;
  sortOrder: number;
}

export interface DepartmentRoutingRule {
  id: number;
  entityName: string;
  department: string;
  targetOffice: string;
  registrationStatus: string;
  active: boolean;
}

export interface OfficeThreshold {
  id: number;
  officeId: string;
  officeName: string;
  department: string;
  maxThreshold: number;
  currentCount: number;
  overflowSequenceOrder: number;
  active: boolean;
}

@Injectable({ providedIn: 'root' })
export class MasterDataService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/masters`;
  private headUrl = `${environment.apiBaseUrl}/api/v1/crpc/head`;

  // Everything under /api/v1/masters returns the standard { success, data, timestamp } envelope, so
  // each method below unwraps it and hands callers the payload directly. The office-threshold methods
  // at the bottom talk to a different controller and are deliberately left alone.

  // ─── Categories ───
  getCategories(schemeVersion?: string, entityType?: string): Observable<CategoryMaster[]> {
    const params: any = {};
    if (schemeVersion) params.schemeVersion = schemeVersion;
    if (entityType) params.entityType = entityType;
    return this.http.get<ApiResponse<CategoryMaster[]>>(`${this.baseUrl}/categories`, { params })
      .pipe(map(res => res.data), catchError(() => of([])));
  }

  createCategory(category: Partial<CategoryMaster>): Observable<CategoryMaster> {
    return this.http.post<ApiResponse<CategoryMaster>>(`${this.baseUrl}/categories`, category)
      .pipe(map(res => res.data));
  }

  updateCategory(id: number, category: Partial<CategoryMaster>): Observable<CategoryMaster> {
    return this.http.put<ApiResponse<CategoryMaster>>(`${this.baseUrl}/categories/${id}`, category)
      .pipe(map(res => res.data));
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/categories/${id}`)
      .pipe(map(() => undefined));
  }

  // ─── Department Routing ───
  getRoutingRules(department?: string): Observable<DepartmentRoutingRule[]> {
    const params: any = {};
    if (department) params.department = department;
    return this.http.get<ApiResponse<DepartmentRoutingRule[]>>(`${this.baseUrl}/department-routing`, { params })
      .pipe(map(res => res.data), catchError(() => of([])));
  }

  createRoutingRule(rule: Partial<DepartmentRoutingRule>): Observable<DepartmentRoutingRule> {
    return this.http.post<ApiResponse<DepartmentRoutingRule>>(`${this.baseUrl}/department-routing`, rule)
      .pipe(map(res => res.data));
  }

  updateRoutingRule(id: number, rule: Partial<DepartmentRoutingRule>): Observable<DepartmentRoutingRule> {
    return this.http.put<ApiResponse<DepartmentRoutingRule>>(`${this.baseUrl}/department-routing/${id}`, rule)
      .pipe(map(res => res.data));
  }

  deleteRoutingRule(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/department-routing/${id}`)
      .pipe(map(() => undefined));
  }

  checkCancelledEntity(entityName: string): Observable<{ cancelled: boolean; message?: string }> {
    return this.http.get<ApiResponse<{ cancelled: boolean; message?: string }>>(
      `${this.baseUrl}/department-routing/check-cancelled/${encodeURIComponent(entityName)}`
    ).pipe(map(res => res.data), catchError(() => of({ cancelled: false })));
  }

  // ─── Office Thresholds ───
  getOfficeThresholds(): Observable<OfficeThreshold[]> {
    return this.http.get<OfficeThreshold[]>(`${this.headUrl}/office-thresholds`)
      .pipe(catchError(() => of([])));
  }

  updateOfficeThreshold(officeId: string, threshold: number): Observable<any> {
    return this.http.put(`${this.headUrl}/office-thresholds/${officeId}`, null, {
      params: { threshold: threshold.toString() }
    });
  }

  resetOfficeCounters(department: string): Observable<any> {
    return this.http.post(`${this.headUrl}/office-thresholds/reset`, null, {
      params: { department }
    });
  }
}
