import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface ExtractionRule {
  id: number;
  ruleName: string;
  ruleCode: string;
  description: string;
  patternType: 'REGEX' | 'KEYWORD_LIST';
  pattern: string;
  targetField: string;
  extractGroup: number;
  transform: string;
  priority: number;
  isActive: boolean;
  sourceScope: 'BODY' | 'SUBJECT' | 'BOTH';
  createdBy: string;
  createdAt: string;
  updatedBy: string;
  updatedAt: string;
}

export interface ExtractionRuleRequest {
  ruleName: string;
  ruleCode?: string;
  description?: string;
  patternType: 'REGEX' | 'KEYWORD_LIST';
  pattern: string;
  targetField: string;
  extractGroup?: number;
  transform?: string;
  priority: number;
  isActive?: boolean;
  sourceScope?: string;
}

export interface ExtractionTestRequest {
  subject: string;
  body: string;
}

export interface MatchDetail {
  ruleName: string;
  ruleCode: string;
  targetField: string;
  extractedValue: string;
  priority: number;
  matchStart: number;
  matchEnd: number;
}

export interface ExtractionTestResponse {
  extractedFields: Record<string, string>;
  matchDetails: MatchDetail[];
  unmatchedFields: string[];
}

@Injectable({ providedIn: 'root' })
export class ExtractionRulesService {

  private baseUrl = `${environment.apiBaseUrl}/api/v1/extraction-rules`;

  constructor(private http: HttpClient) {}

  getRules(activeOnly?: boolean): Observable<ExtractionRule[]> {
    const params = activeOnly != null ? `?active=${activeOnly}` : '';
    return this.http.get<any>(`${this.baseUrl}${params}`)
      .pipe(map(res => res.data));
  }

  getRule(id: number): Observable<ExtractionRule> {
    return this.http.get<any>(`${this.baseUrl}/${id}`)
      .pipe(map(res => res.data));
  }

  createRule(request: ExtractionRuleRequest): Observable<ExtractionRule> {
    return this.http.post<any>(this.baseUrl, request)
      .pipe(map(res => res.data));
  }

  updateRule(id: number, request: ExtractionRuleRequest): Observable<ExtractionRule> {
    return this.http.put<any>(`${this.baseUrl}/${id}`, request)
      .pipe(map(res => res.data));
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  toggleRule(id: number): Observable<ExtractionRule> {
    return this.http.patch<any>(`${this.baseUrl}/${id}/toggle`, {})
      .pipe(map(res => res.data));
  }

  testRules(request: ExtractionTestRequest): Observable<ExtractionTestResponse> {
    return this.http.post<any>(`${this.baseUrl}/test`, request)
      .pipe(map(res => res.data));
  }

  getTargetFields(): Observable<string[]> {
    return this.http.get<any>(`${this.baseUrl}/target-fields`)
      .pipe(map(res => res.data));
  }
}
