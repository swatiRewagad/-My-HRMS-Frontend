import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

/**
 * RE nodal officer reassignment (UST838–UST845).
 *
 * The entity a caller may act on is never sent from here — the server derives it from the token (or
 * the dev headers) and refuses a mismatch. Passing an entity code from the browser would be refused
 * with `re.reassign.error.cross_entity`, so the optional entityCode below is for ADMIN callers only,
 * who legitimately have no entity of their own.
 *
 * Error bodies carry a `messageKey`, not English prose, so the UI renders the reason in the reader's
 * locale rather than showing a server-language string.
 */

export type ReRole = 'NODAL_OFFICER' | 'CONTACT_PERSON' | 'PNO';
export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN';
export type TriggerType = 'APPROVED_REQUEST' | 'DIRECT';

export interface ReassignmentCandidate {
  userId: string;
  displayName: string;
  email: string | null;
  designation: string | null;
  reRole: ReRole;
  territory: string | null;
  /** Open records held by this officer, excluding drafts and closed work (UST838). */
  workload: number;
}

export interface ReassignmentRequestRow {
  id: number;
  recordId: number;
  complaintNumber: string;
  entityCode: string;
  fromUserId: string | null;
  fromUserName: string | null;
  toUserId: string;
  toUserName: string | null;
  /** Immutable once submitted (UST840); corrections are appended as clarifications. */
  reason: string | null;
  status: RequestStatus;
  requestedBy: string;
  requestedByName: string | null;
  requestedAt: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  decisionComment: string | null;
  toUserWorkloadAtRequest: number | null;
}

export interface ReassignmentClarificationRow {
  id: number;
  requestId: number;
  note: string;
  addedBy: string;
  addedByName: string | null;
  addedBySide: 'RE' | 'RBI';
  addedAt: string | null;
}

export interface OfficerWorkload {
  userId: string;
  workload: number;
}

export interface ReassignmentHistoryRow {
  id: number;
  recordId: number;
  complaintNumber: string;
  entityCode: string;
  fromUserId: string | null;
  fromUserName: string | null;
  toUserId: string;
  toUserName: string | null;
  triggerType: TriggerType;
  requestId: number | null;
  reason: string | null;
  performedBy: string;
  performedByName: string | null;
  reassignedAt: string | null;
}

/**
 * Per-item outcome of a bulk action. A conflict on one record is reported here rather than as the
 * status of the whole call, because the action genuinely partially succeeded (UST839).
 */
export interface BulkFailure {
  id: number;
  messageKey: string;
  detail: string;
}

export interface BulkResult {
  success: boolean;
  succeededCount: number;
  failedCount: number;
  succeeded: number[];
  failed: BulkFailure[];
}

export interface BulkItem {
  requestId?: number;
  recordId?: number;
  /** The version read with the row; a mismatch is refused as a conflict. */
  expectedVersion?: number | null;
}

@Injectable({ providedIn: 'root' })
export class ReassignmentService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/api/v1/re-portal/reassignment`;
  private reportBase = `${environment.apiBaseUrl}/api/v1/re-portal/reassignment-report`;

  // ═══ Candidates and workload ═══

  getCandidates(options: {
    recordId?: number;
    role?: ReRole | '';
    search?: string;
    entityCode?: string;
  } = {}): Observable<{ success: boolean; count: number; candidates: ReassignmentCandidate[] }> {
    return this.http.get<{ success: boolean; count: number; candidates: ReassignmentCandidate[] }>(
      `${this.base}/candidates`,
      { params: this.params(options) },
    );
  }

  getWorkload(entityCode?: string): Observable<{
    success: boolean;
    entityCode: string;
    officers: OfficerWorkload[];
    totalActiveRecords: number;
  }> {
    return this.http.get<{
      success: boolean;
      entityCode: string;
      officers: OfficerWorkload[];
      totalActiveRecords: number;
    }>(`${this.base}/workload`, { params: this.params({ entityCode }) });
  }

  // ═══ Requests ═══

  raiseRequest(body: {
    recordId: number;
    toUserId: string;
    reason: string;
    expectedVersion?: number | null;
    entityCode?: string;
  }): Observable<{ success: boolean; applied: boolean; request: ReassignmentRequestRow }> {
    return this.http.post<{ success: boolean; applied: boolean; request: ReassignmentRequestRow }>(
      `${this.base}/requests`,
      body,
    );
  }

  myRequests(options: { status?: RequestStatus | ''; page?: number; size?: number } = {}): Observable<{
    success: boolean;
    requests: ReassignmentRequestRow[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }> {
    return this.http.get<{
      success: boolean;
      requests: ReassignmentRequestRow[];
      totalElements: number;
      totalPages: number;
      page: number;
      size: number;
    }>(`${this.base}/requests/mine`, { params: this.params(options) });
  }

  pendingApprovals(options: { page?: number; size?: number; entityCode?: string } = {}): Observable<{
    success: boolean;
    requests: ReassignmentRequestRow[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }> {
    return this.http.get<{
      success: boolean;
      requests: ReassignmentRequestRow[];
      totalElements: number;
      totalPages: number;
      page: number;
      size: number;
    }>(`${this.base}/requests/pending`, { params: this.params(options) });
  }

  withdraw(requestId: number, note?: string): Observable<{ success: boolean; request: ReassignmentRequestRow }> {
    return this.http.post<{ success: boolean; request: ReassignmentRequestRow }>(
      `${this.base}/requests/${requestId}/withdraw`,
      { note: note ?? null },
    );
  }

  // ═══ Clarifications (UST840) ═══

  addClarification(requestId: number, note: string): Observable<{
    success: boolean;
    clarification: ReassignmentClarificationRow;
  }> {
    return this.http.post<{ success: boolean; clarification: ReassignmentClarificationRow }>(
      `${this.base}/requests/${requestId}/clarifications`,
      { note },
    );
  }

  getClarifications(requestId: number): Observable<{
    success: boolean;
    count: number;
    clarifications: ReassignmentClarificationRow[];
  }> {
    return this.http.get<{
      success: boolean;
      count: number;
      clarifications: ReassignmentClarificationRow[];
    }>(`${this.base}/requests/${requestId}/clarifications`);
  }

  // ═══ Bulk actions (UST839, UST843) ═══

  decide(body: {
    approve: boolean;
    items: BulkItem[];
    comment?: string;
    entityCode?: string;
  }): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/requests/decide`, body);
  }

  bulkReassign(body: {
    toUserId: string;
    reason: string;
    items: BulkItem[];
    entityCode?: string;
  }): Observable<BulkResult> {
    return this.http.post<BulkResult>(`${this.base}/bulk`, body);
  }

  // ═══ History report (UST844) ═══

  history(options: {
    fromUserId?: string;
    toUserId?: string;
    from?: string;
    to?: string;
    page?: number;
    size?: number;
    entityCode?: string;
  } = {}): Observable<{
    success: boolean;
    history: ReassignmentHistoryRow[];
    totalElements: number;
    totalPages: number;
    page: number;
    size: number;
  }> {
    return this.http.get<{
      success: boolean;
      history: ReassignmentHistoryRow[];
      totalElements: number;
      totalPages: number;
      page: number;
      size: number;
    }>(this.reportBase, { params: this.params(options) });
  }

  historySummary(options: { from?: string; to?: string; entityCode?: string } = {}): Observable<{
    success: boolean;
    entityCode: string;
    outbound: Array<{ userId: string; displayName: string; count: number }>;
    inbound: Array<{ userId: string; displayName: string; count: number }>;
    totalMoves: number;
  }> {
    return this.http.get<{
      success: boolean;
      entityCode: string;
      outbound: Array<{ userId: string; displayName: string; count: number }>;
      inbound: Array<{ userId: string; displayName: string; count: number }>;
      totalMoves: number;
    }>(`${this.reportBase}/summary`, { params: this.params(options) });
  }

  /** Drops empty values so an unset filter is absent rather than sent as an empty string. */
  private params(source: Record<string, unknown>): Record<string, string> {
    const params: Record<string, string> = {};
    for (const [key, value] of Object.entries(source)) {
      if (value !== undefined && value !== null && value !== '') {
        params[key] = String(value);
      }
    }
    return params;
  }
}
