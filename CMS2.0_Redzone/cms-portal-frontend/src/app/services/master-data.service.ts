import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

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

  // ─── Categories ───
  getCategories(schemeVersion?: string, entityType?: string): Observable<CategoryMaster[]> {
    const params: any = {};
    if (schemeVersion) params.schemeVersion = schemeVersion;
    if (entityType) params.entityType = entityType;
    return this.http.get<CategoryMaster[]>(`${this.baseUrl}/categories`, { params })
      .pipe(catchError(() => of([])));
  }

  createCategory(category: Partial<CategoryMaster>): Observable<CategoryMaster> {
    return this.http.post<CategoryMaster>(`${this.baseUrl}/categories`, category);
  }

  updateCategory(id: number, category: Partial<CategoryMaster>): Observable<CategoryMaster> {
    return this.http.put<CategoryMaster>(`${this.baseUrl}/categories/${id}`, category);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/categories/${id}`);
  }

  // ─── Department Routing ───
  getRoutingRules(department?: string): Observable<DepartmentRoutingRule[]> {
    const params: any = {};
    if (department) params.department = department;
    return this.http.get<DepartmentRoutingRule[]>(`${this.baseUrl}/department-routing`, { params })
      .pipe(catchError(() => of([])));
  }

  createRoutingRule(rule: Partial<DepartmentRoutingRule>): Observable<DepartmentRoutingRule> {
    return this.http.post<DepartmentRoutingRule>(`${this.baseUrl}/department-routing`, rule);
  }

  updateRoutingRule(id: number, rule: Partial<DepartmentRoutingRule>): Observable<DepartmentRoutingRule> {
    return this.http.put<DepartmentRoutingRule>(`${this.baseUrl}/department-routing/${id}`, rule);
  }

  deleteRoutingRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/department-routing/${id}`);
  }

  checkCancelledEntity(entityName: string): Observable<{ cancelled: boolean; message?: string }> {
    return this.http.get<{ cancelled: boolean; message?: string }>(
      `${this.baseUrl}/department-routing/check-cancelled/${encodeURIComponent(entityName)}`
    ).pipe(catchError(() => of({ cancelled: false })));
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
