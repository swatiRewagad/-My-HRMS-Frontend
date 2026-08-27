import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface ActionOverride {
  id: string;
  complaintId: string;
  fieldName: string;
  oldValue: string;
  newValue: string;
  overriddenBy: string;
  overriddenByRole: string;
  timestamp: string;
}

export interface RegulatoryBody {
  id: number;
  name: string;
  code: string;
  contactEmail: string;
  address: string;
  active: boolean;
}

export interface AdditionalEntity {
  id?: string;
  complaintId: string;
  entityName: string;
  entityBranch: string;
  entityType: string;
  entityCategory: string;
  nodalOfficerRecord?: any;
  createdBy: string;
  createdAt?: string;
}

export interface LegalCase {
  id?: string;
  complaintId: string;
  caseNumber: string;
  courtName: string;
  caseStatus: string;
  filingDate: string;
  nextHearingDate: string;
  remarks?: string;
  updatedBy?: string;
  updatedAt?: string;
}

export interface ReassignmentCandidate {
  userId: string;
  name: string;
  role: string;
  lastActiveDate: string;
  isActive: boolean;
  isOnLeave: boolean;
}

@Injectable({ providedIn: 'root' })
export class RbioWorkflowService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1`;

  // --- Dealing Official Workflow Action ---
  submitWorkflowAction(complaintId: string, body: {
    action: string;
    assignTo: string;
    assigneeName: string;
    assignmentMode: string;
    remarks: string;
    actor: string;
    systemicIssue?: boolean;
  }): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/workflow/rbio/action/${complaintId}`,
      body
    );
  }

  // --- Deputy Ombudsman Decision ---
  submitDeputyDecision(complaintId: string, body: {
    maintainability: 'MAINTAINABLE' | 'NON_MAINTAINABLE';
    decision: 'FACILITATION' | 'REJECTION';
    remarks: string;
    actor: string;
  }): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/workflow/rbio/action/${complaintId}`,
      { action: 'DEPUTY_OMBUDSMAN_DECISION', ...body }
    );
  }

  // --- Auto-Reassign to Last-Active ---
  getLastActiveOfficer(complaintId: string): Observable<ReassignmentCandidate | null> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/last-active-officer`
    ).pipe(
      map(res => res.data || res),
      catchError(() => of(null))
    );
  }

  reassignComplaint(complaintId: string, targetUserId: string, actor: string): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/workflow/rbio/action/${complaintId}`,
      { action: 'REASSIGN', targetUserId, actor }
    );
  }

  // --- Action Override History ---
  recordActionOverride(complaintId: string, override: {
    fieldName: string;
    oldValue: string;
    newValue: string;
    overriddenBy: string;
    overriddenByRole: string;
  }): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/complaints/${complaintId}/action-override`,
      override
    );
  }

  getActionOverrides(complaintId: string): Observable<ActionOverride[]> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/action-override`
    ).pipe(
      map(res => res.data || res || []),
      catchError(() => of([]))
    );
  }

  // --- Additional Entities ---
  getAdditionalEntities(complaintId: string): Observable<AdditionalEntity[]> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/additional-entities`
    ).pipe(
      map(res => res.data || res || []),
      catchError(() => of([]))
    );
  }

  addEntity(complaintId: string, entity: Partial<AdditionalEntity>): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/complaints/${complaintId}/additional-entities`,
      entity
    );
  }

  // --- Legal Case ---
  getLegalCase(complaintId: string): Observable<LegalCase | null> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/legal-case`
    ).pipe(
      map(res => res.data || res),
      catchError(() => of(null))
    );
  }

  saveLegalCase(complaintId: string, legalCase: Partial<LegalCase>): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/complaints/${complaintId}/legal-case`,
      legalCase
    );
  }

  updateLegalCase(complaintId: string, legalCase: Partial<LegalCase>): Observable<any> {
    return this.http.put<any>(
      `${this.baseUrl}/complaints/${complaintId}/legal-case`,
      legalCase
    );
  }

  // --- Regulatory Bodies Master List ---
  getRegulatoryBodies(): Observable<RegulatoryBody[]> {
    return this.http.get<any>(
      `${this.baseUrl}/master-data/regulatory-bodies`
    ).pipe(
      map(res => res.data || res || []),
      catchError(() => of([]))
    );
  }

  forwardToRegulatoryBody(complaintId: string, body: {
    regulatoryBodyId: number;
    regulatoryBodyName: string;
    remarks: string;
    actor: string;
  }): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/workflow/rbio/action/${complaintId}`,
      { action: 'FORWARD_TO_REGULATORY_BODY', ...body }
    );
  }

  // --- Closure Validation ---
  checkComplaintHasEmail(complaintId: string): Observable<{ hasEmail: boolean; email?: string }> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}`
    ).pipe(
      map(res => {
        const data = res.data || res;
        const email = data?.complainantEmail;
        return { hasEmail: !!(email && email.trim()), email };
      }),
      catchError(() => of({ hasEmail: false }))
    );
  }

  // --- Check if final decision exists upstream ---
  checkFinalDecision(complaintId: string): Observable<{ hasFinalDecision: boolean; decidedBy?: string; decidedByRole?: string }> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/final-decision-status`
    ).pipe(
      map(res => res.data || res || { hasFinalDecision: false }),
      catchError(() => of({ hasFinalDecision: false }))
    );
  }

  // --- Impleading: create NO/PNO record ---
  createImpleadNodalRecord(complaintId: string, body: {
    impleadPartyName: string;
    impleadPartyType: string;
    actor: string;
  }): Observable<any> {
    return this.http.post<any>(
      `${this.baseUrl}/complaints/${complaintId}/implead-nodal-record`,
      body
    );
  }

  // --- Validate impleaded entities completeness ---
  validateImpleadedEntities(complaintId: string): Observable<{ valid: boolean; incomplete: string[] }> {
    return this.http.get<any>(
      `${this.baseUrl}/complaints/${complaintId}/implead-validation`
    ).pipe(
      map(res => res.data || res || { valid: true, incomplete: [] }),
      catchError(() => of({ valid: true, incomplete: [] }))
    );
  }
}
