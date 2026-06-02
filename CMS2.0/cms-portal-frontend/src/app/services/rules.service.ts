import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface RuleCategory {
  id: number;
  code: string;
  name: string;
  description: string;
}

export interface RuleDefinition {
  id: number;
  ruleCode: string;
  ruleName: string;
  categoryCode: string;
  categoryName: string;
  drlContent: string;
  salience: number;
  version: number;
  status: 'DRAFT' | 'PENDING_REVIEW' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
  effectiveFrom: string | null;
  effectiveTo: string | null;
  createdBy: string;
  createdAt: string;
  updatedBy: string | null;
  updatedAt: string | null;
  approvedBy: string | null;
  approvedAt: string | null;
}

export interface RuleRequest {
  ruleCode: string;
  ruleName: string;
  categoryCode: string;
  drlContent: string;
  salience: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  changeReason?: string;
}

export interface RuleHistory {
  id: number;
  version: number;
  drlContent: string;
  changeReason: string;
  changedBy: string;
  changedAt: string;
  action: string;
}

export interface RuleTestRequest {
  categoryCode: string;
  inputFacts: Record<string, any>;
}

export interface RuleTestResponse {
  executed: boolean;
  rulesFireCount: number;
  outputFacts: Record<string, any>;
  rulesFired: string[];
  error: string | null;
}

export interface DeploymentResponse {
  deploymentId: string;
  rulesDeployed: number;
  status: string;
  deployedAt: string;
  error: string | null;
}

export interface ValidationResponse {
  valid: boolean;
  errors: string[];
  warnings: string[];
}

interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class RulesService {

  private baseUrl = 'http://localhost:8080/api/v1/rules';

  constructor(private http: HttpClient) {}

  private getHeaders(user: string = 'admin'): HttpHeaders {
    return new HttpHeaders({ 'X-User': user });
  }

  getCategories(): Observable<RuleCategory[]> {
    return this.http.get<ApiResponse<RuleCategory[]>>(`${this.baseUrl}/categories`)
      .pipe(map(res => res.data));
  }

  getRules(category?: string, status?: string): Observable<RuleDefinition[]> {
    let url = this.baseUrl;
    const params: string[] = [];
    if (category) params.push(`category=${category}`);
    if (status) params.push(`status=${status}`);
    if (params.length) url += '?' + params.join('&');

    return this.http.get<ApiResponse<RuleDefinition[]>>(url)
      .pipe(map(res => res.data));
  }

  getRule(id: number): Observable<RuleDefinition> {
    return this.http.get<ApiResponse<RuleDefinition>>(`${this.baseUrl}/${id}`)
      .pipe(map(res => res.data));
  }

  createRule(request: RuleRequest, user: string): Observable<RuleDefinition> {
    return this.http.post<ApiResponse<RuleDefinition>>(this.baseUrl, request,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }

  updateRule(id: number, request: RuleRequest, user: string): Observable<RuleDefinition> {
    return this.http.put<ApiResponse<RuleDefinition>>(`${this.baseUrl}/${id}`, request,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }

  activateRule(id: number, approver: string): Observable<RuleDefinition> {
    return this.http.post<ApiResponse<RuleDefinition>>(`${this.baseUrl}/${id}/activate`, null,
      { headers: this.getHeaders(approver) })
      .pipe(map(res => res.data));
  }

  deactivateRule(id: number, user: string, reason?: string): Observable<RuleDefinition> {
    const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.http.post<ApiResponse<RuleDefinition>>(`${this.baseUrl}/${id}/deactivate${params}`, null,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }

  rollbackRule(id: number, user: string): Observable<RuleDefinition> {
    return this.http.post<ApiResponse<RuleDefinition>>(`${this.baseUrl}/${id}/rollback`, null,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }

  archiveRule(id: number, user: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/${id}`,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }

  getRuleHistory(id: number): Observable<RuleHistory[]> {
    return this.http.get<ApiResponse<RuleHistory[]>>(`${this.baseUrl}/${id}/history`)
      .pipe(map(res => res.data));
  }

  validateDrl(drlContent: string): Observable<ValidationResponse> {
    return this.http.post<ApiResponse<ValidationResponse>>(`${this.baseUrl}/validate`, { drlContent })
      .pipe(map(res => res.data));
  }

  testRules(request: RuleTestRequest): Observable<RuleTestResponse> {
    return this.http.post<ApiResponse<RuleTestResponse>>(`${this.baseUrl}/test`, request)
      .pipe(map(res => res.data));
  }

  deployRules(user: string): Observable<DeploymentResponse> {
    return this.http.post<ApiResponse<DeploymentResponse>>(`${this.baseUrl}/deploy`, null,
      { headers: this.getHeaders(user) })
      .pipe(map(res => res.data));
  }
}
