import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SemanticModel {
  subjects: SubjectToken[];
  filters: FilterToken[];
  groupBys: GroupByToken[];
}

export interface SubjectToken {
  id: string;
  label: string;
  description: string;
}

export interface FilterToken {
  id: string;
  label: string;
  field: string;
  operator: string;
  value: string;
  category: string;
  fieldType: FilterFieldType;
}

export type FilterFieldType = 'DATE' | 'COMPLAINT_NUMBER' | 'PICKER' | 'TEXT';

export type FilterOperator = 'EQUAL' | 'BETWEEN' | 'GREATER_THAN' | 'LESS_THAN' | 'LIKE' | 'IN';

export interface GroupByToken {
  id: string;
  label: string;
  field: string;
}

interface RawSemanticModel {
  subjects: { id: string; phrase: string; aggregate: boolean; column: string }[];
  filters: { category: string; phrase: string; field: string; operator: string; value: string; fieldType?: string }[];
  groupBys: { phrase: string; field: string }[];
}

export interface ReportQuery {
  subjectId: string;
  filters: QueryFilter[];
  groupByField?: string;
  sentence: string;
}

export interface QueryFilter {
  field: string;
  operator: string;
  value: string;
}

export interface ReportExecutionResult {
  sentence: string;
  resultCount: number;
  maxRows: number;
  bounded: boolean;
  readOnly: boolean;
  results: Record<string, any>[];
}

export interface ReportDefinition {
  id: number;
  ownerUsername: string;
  sentence: string;
  queryDefinition: string;
  chartType: string;
  title: string;
  dashboardWidget: boolean;
  displayOrder: number;
}

export interface ReportSchedule {
  id: number;
  reportDefinitionId: number;
  ownerUsername: string;
  recipientEmail: string;
  frequency: string;
  deliverySlot: string;
  active: boolean;
  lastSentAt: string | null;
}

/** NO Record drill-down row */
export interface NoRecordDrillDownRow {
  entityName: string;
  noRecordId: string;
  noRecordCreatedOn: string;
  nodalOfficeName: string;
  noRecordStatus: string;
  noRecordLastModifiedOn: string;
}

/** Report access role configuration */
export interface ReportAccessRole {
  id: number;
  reportType: string;
  roleName: string;
  canExport: boolean;
}

/** Operators allowed per field type */
export const OPERATORS_BY_FIELD_TYPE: Record<FilterFieldType, FilterOperator[]> = {
  DATE: ['EQUAL', 'BETWEEN', 'GREATER_THAN', 'LESS_THAN'],
  COMPLAINT_NUMBER: ['LIKE', 'IN'],
  PICKER: ['EQUAL', 'IN'],
  TEXT: ['EQUAL', 'LIKE', 'IN']
};

/** Human-readable operator labels */
export const OPERATOR_LABELS: Record<FilterOperator, string> = {
  EQUAL: 'Equal',
  BETWEEN: 'Between',
  GREATER_THAN: 'Greater than',
  LESS_THAN: 'Less than',
  LIKE: 'Like',
  IN: 'IN (comma-separated)'
};

@Injectable({ providedIn: 'root' })
export class ReportBuilderService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/reports`;

  getSemanticModel(): Observable<SemanticModel> {
    return this.http.get<RawSemanticModel>(`${this.baseUrl}/semantic-model`).pipe(
      map(raw => ({
        subjects: raw.subjects.map(s => ({
          id: s.id,
          label: s.phrase,
          description: s.aggregate ? `Aggregate: ${s.column}` : 'List of complaints'
        })),
        filters: raw.filters.map((f, i) => ({
          id: `${f.category}-${f.field}-${f.value}-${i}`,
          label: f.phrase,
          field: f.field,
          operator: f.operator,
          value: f.value,
          category: f.category.toUpperCase(),
          fieldType: this.inferFieldType(f.field, f.fieldType)
        })),
        groupBys: raw.groupBys.map(g => ({
          id: `gb-${g.field}`,
          label: g.phrase.replace('grouped by ', ''),
          field: g.field
        }))
      }))
    );
  }

  compile(query: ReportQuery): Observable<{ valid: boolean; sentence: string }> {
    return this.http.post<{ valid: boolean; sentence: string }>(`${this.baseUrl}/compile`, query);
  }

  execute(query: ReportQuery): Observable<ReportExecutionResult> {
    return this.http.post<ReportExecutionResult>(`${this.baseUrl}/execute`, query);
  }

  saveWidget(sentence: string, query: ReportQuery, chartType: string, title: string): Observable<ReportDefinition> {
    return this.http.post<ReportDefinition>(`${this.baseUrl}/save-widget`, {
      sentence, query, chartType, title
    });
  }

  schedule(reportDefinitionId: number, frequency: string, deliverySlot: string): Observable<ReportSchedule> {
    return this.http.post<ReportSchedule>(`${this.baseUrl}/schedule`, {
      reportDefinitionId, frequency, deliverySlot
    });
  }

  getMyWidgets(): Observable<ReportDefinition[]> {
    return this.http.get<ReportDefinition[]>(`${this.baseUrl}/my-widgets`);
  }

  deleteWidget(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/widget/${id}`);
  }

  getMySchedules(): Observable<ReportSchedule[]> {
    return this.http.get<ReportSchedule[]>(`${this.baseUrl}/my-schedules`);
  }

  /** UST621: Drill-down for NO Record count */
  getNoRecordDrillDown(complaintId: string): Observable<NoRecordDrillDownRow[]> {
    return this.http.get<NoRecordDrillDownRow[]>(
      `${this.baseUrl}/drill-down/no-records`, { params: { complaintId } }
    );
  }

  /** UST615/622/670: Load report access roles */
  getReportAccessRoles(): Observable<ReportAccessRole[]> {
    return this.http.get<ReportAccessRole[]>(`${this.baseUrl}/access-roles`);
  }

  /** UST615/622/670: Save report access roles (admin) */
  saveReportAccessRoles(roles: ReportAccessRole[]): Observable<ReportAccessRole[]> {
    return this.http.post<ReportAccessRole[]>(`${this.baseUrl}/access-roles`, roles);
  }

  /** UST615/622/670: Delete a report access role (admin) */
  deleteReportAccessRole(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/access-roles/${id}`);
  }

  /** Infer field type from field name or explicit backend type */
  private inferFieldType(field: string, explicitType?: string): FilterFieldType {
    if (explicitType) {
      const upper = explicitType.toUpperCase();
      if (upper === 'DATE' || upper === 'COMPLAINT_NUMBER' || upper === 'PICKER' || upper === 'TEXT') {
        return upper as FilterFieldType;
      }
    }
    const lower = field.toLowerCase();
    if (lower.includes('date') || lower.includes('_on') || lower === 'closed_on' || lower === 'created_on' || lower === 'complaint_closed_on') {
      return 'DATE';
    }
    if (lower.includes('complaint_number') || lower === 'complaintNumber') {
      return 'COMPLAINT_NUMBER';
    }
    if (lower.includes('entity') || lower.includes('status') || lower.includes('category') || lower.includes('type')) {
      return 'PICKER';
    }
    return 'TEXT';
  }
}
