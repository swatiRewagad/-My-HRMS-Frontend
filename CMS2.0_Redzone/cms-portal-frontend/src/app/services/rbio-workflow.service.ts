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
  /** String, matching the server payload; it was typed `number` while the server sends String.valueOf(id). */
  id: number | string;
  name: string;
  code: string;
  contactEmail: string | null;
  address: string | null;
  jurisdiction?: string | null;
  /** UST766: whether the contact email has been verified. Only verified bodies may receive a referral. */
  emailVerified?: boolean;
  /** Whether the server will accept a referral to this body — active AND verified AND has an address. */
  forwardable?: boolean;
  /** No longer sent by the server: a body present in the response is active by definition. */
  active?: boolean;
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

  /**
   * The validated regulatory-body master (UST766).
   *
   * <p>Two corrections. The path is now {@code /masters/...}, where the master controller actually lives —
   * {@code /master-data/...} matched nothing, and the master itself did not exist until this batch. (The
   * server also serves the old prefix as an alias, so an un-shipped client keeps working.)
   *
   * <p>And {@code catchError(() => of([]))} is GONE. Swallowing the failure is what made a missing endpoint
   * indistinguishable from an empty master for as long as this screen has existed: the dropdown was empty, no
   * error appeared, and the UI went on claiming the list was validated. The error now propagates so the
   * component fails closed and says so.
   */
  getRegulatoryBodies(): Observable<RegulatoryBody[]> {
    return this.http.get<any>(
      `${this.baseUrl}/masters/regulatory-bodies`
    ).pipe(
      map(res => res?.data ?? res ?? [])
    );
  }

  /** RBI departments a complaint may be forwarded to (UST761, 534, 527-528). */
  getRbiDepartments(): Observable<Array<{ deptCode: string; deptName: string }>> {
    return this.http.get<any>(
      `${this.baseUrl}/masters/rbi-departments`
    ).pipe(
      map(res => res?.data ?? res ?? [])
    );
  }

  /**
   * Transfer destination offices for a layout (UST556, 563).
   *
   * @param layout 'RBIO' or 'CEPC'. UST563 requires the CEPC list when Transfer Office = CEPC; that list had
   *               no data source at all before, because every office row was typed 'BO'.
   */
  getTransferOffices(layout: 'RBIO' | 'CEPC' = 'RBIO'):
      Observable<Array<{ officeCode: string; officeName: string; layout: string }>> {
    return this.http.get<any>(
      `${this.baseUrl}/masters/transfer-offices?layout=${layout}`
    ).pipe(
      map(res => res?.data ?? res ?? [])
    );
  }

  forwardToRegulatoryBody(complaintId: string, body: {
    // number | string, matching RegulatoryBody.id: the server sends String.valueOf(id) and resolves a
    // code, a name or a numeric id, so narrowing this to number would reject the value the payload carries.
    regulatoryBodyId: number | string;
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
