import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface RulesetSummary {
  id: number;
  tenantId: string;
  decisionPoint: string;
  name: string;
  hitPolicy: string;
  activeVersionId: number | null;
  activeVersionNo: number | null;
  status: string;
  lastPublishedAt: string | null;
  lastPublishedBy: string | null;
}

export interface AttributeDefinition {
  code: string;
  label: string;
  description: string;
  helpText: string;
  dataType: string;
  sourcePath: string;
  required: boolean;
  valueSource: string;
  caseSensitive: boolean;
  piiFlag: boolean;
  displayOrder: number;
  allowedOperators: string[];
}

export interface RuleRow {
  id: number | null;
  ruleCode: string;
  name: string;
  description: string;
  priority: number;
  rowOrder: number;
  enabled: boolean;
  conditions: Record<string, ConditionCell>;
  outcome: OutcomeCell;
}

export interface ConditionCell {
  attributeCode: string;
  operator: string | null;
  values: string[];
  numericFrom: number | null;
  numericTo: number | null;
}

export interface OutcomeCell {
  outcomeType: string;
  targetId: string;
  assignMode: string | null;
  distributionStrategy: string | null;
}

@Injectable({ providedIn: 'root' })
export class AssignmentStudioService {

  private baseUrl = '/cms-assignment/api/v1/assignment';

  constructor(private http: HttpClient) {}

  getRulesets(): Observable<RulesetSummary[]> {
    return this.http.get<RulesetSummary[]>(`${this.baseUrl}/rulesets`);
  }

  createVersion(rulesetId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/rulesets/${rulesetId}/versions`, {});
  }

  getAttributes(): Observable<AttributeDefinition[]> {
    return this.http.get<AttributeDefinition[]>(`${this.baseUrl}/attributes`);
  }

  getAttributeValues(code: string, query?: string): Observable<{ code: string; label: string }[]> {
    const params: Record<string, string> = {};
    if (query) { params['q'] = query; }
    return this.http.get<{ code: string; label: string }[]>(
      `${this.baseUrl}/attributes/${code}/values`, { params }
    );
  }

  getVersion(rulesetId: number, versionId: number): Observable<any> {
    return this.http.get(`${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}`);
  }

  saveRules(rulesetId: number, versionId: number, body: any, etag: string): Observable<any> {
    return this.http.put(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/rules`,
      body,
      { headers: { 'If-Match': etag } }
    );
  }

  validate(rulesetId: number, versionId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/validate`, {});
  }

  submit(rulesetId: number, versionId: number, remarks: string): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/submit`,
      { remarks }
    );
  }

  approve(rulesetId: number, versionId: number, remarks: string): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/approve`,
      { remarks }
    );
  }

  reject(rulesetId: number, versionId: number, remarks: string): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/reject`,
      { remarks }
    );
  }

  publish(rulesetId: number, versionId: number, effectiveFrom?: string): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/publish`,
      { effectiveFrom }
    );
  }

  resolve(request: any): Observable<any> {
    return this.http.post(`${this.baseUrl}/resolve`, request);
  }

  getPendingApprovals(): Observable<any[]> {
    return this.http.get<any[]>(`${this.baseUrl}/approvals/pending`);
  }

  getAuditTrail(rulesetId: number, versionId: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/audit-trail`
    );
  }

  simulate(rulesetId: number, versionId: number, testCases: any[]): Observable<any> {
    return this.http.post(
      `${this.baseUrl}/rulesets/${rulesetId}/versions/${versionId}/simulate`,
      testCases
    );
  }
}
