import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  ComplaintRegistrationRequest,
  ComplaintAcknowledgement,
  ComplaintStatus,
  NonMaintainableRegistrationRequest,
  NonMaintainableRegistrationResponse,
} from '../models/complaint.model';

export interface DraftPayload {
  phone: string;
  draftId?: string;
  entityName: string;
  formData: Record<string, any>;
  eligibilityFormData: Record<string, any>;
  eligibilityAnswers: Record<string, any>;
  currentStep: number;
  highestStepReached?: number;
  eligibilityStep?: number;
  phase: string;
  checkedAccountTypes?: string[];
  dateDisplay?: Record<string, string>;
  declarationChecked?: boolean;
  declaration2Checked?: boolean;
  attachmentMeta?: { name: string; type: string; size: number }[];
  draftVersion?: number;
}

export interface DraftRecord {
  draftId: string;
  phone: string;
  entityName: string;
  formData: Record<string, any>;
  eligibilityFormData?: Record<string, any>;
  eligibilityAnswers: Record<string, any>;
  currentStep: number;
  highestStepReached?: number;
  eligibilityStep?: number;
  phase: string;
  checkedAccountTypes?: string[];
  dateDisplay?: Record<string, string>;
  declarationChecked?: boolean;
  declaration2Checked?: boolean;
  attachmentMeta?: { name: string; type: string; size: number }[];
  draftVersion?: number;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class ComplaintService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/complaints`;

  registerComplaint(request: ComplaintRegistrationRequest): Observable<ComplaintAcknowledgement> {
    return this.http.post<ApiResponse<ComplaintAcknowledgement>>(this.baseUrl, request)
      .pipe(map(res => res.data));
  }

  // FR-G-013: issues a real backend Case ID the moment the eligibility wizard determines a
  // complaint Non-Maintainable, in place of the complaint-number flow above.
  registerNonMaintainable(request: NonMaintainableRegistrationRequest): Observable<NonMaintainableRegistrationResponse> {
    return this.http.post<ApiResponse<NonMaintainableRegistrationResponse>>(`${this.baseUrl}/non-maintainable`, request)
      .pipe(map(res => res.data));
  }

  uploadAttachments(complaintId: string, files: File[]): Observable<void> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/${complaintId}/attachments`, formData)
      .pipe(map(() => undefined));
  }

  trackComplaint(complaintNumber: string): Observable<ComplaintStatus> {
    return this.http.get<ApiResponse<ComplaintStatus>>(
      `${this.baseUrl}/${encodeURIComponent(complaintNumber)}`
    ).pipe(map(res => res.data));
  }

  /**
   * Withdraws a complaint, carrying the supporting documents when the citizen attached any.
   *
   * `phone` is authorisation, not metadata: the endpoint rejects the request unless it matches
   * the complaint's registered mobile number.
   *
   * Two request shapes on purpose. With no documents this posts JSON exactly as it always has, so
   * nothing about the common case changes. With documents it posts multipart — and the files are
   * actually SENT, which they previously were not: the withdrawal form collected them, showed a
   * chip per file, then posted a JSON body with no document field at all, so every supporting
   * document a citizen attached to a withdrawal was discarded in the browser.
   *
   * `documents` is the multipart field name the server's multipart arm binds.
   */
  withdrawComplaint(
    complaintId: string,
    reason: string,
    remarks: string,
    phone: string,
    documents: File[] = [],
  ): Observable<void> {
    const url = `${this.baseUrl}/${complaintId}/withdraw`;

    if (!documents.length) {
      return this.http.post<ApiResponse<void>>(url, { reason, remarks, phone })
        .pipe(map(() => undefined));
    }

    const formData = new FormData();
    formData.append('reason', reason);
    formData.append('remarks', remarks ?? '');
    formData.append('phone', phone);
    documents.forEach(file => formData.append('documents', file, file.name));

    // No explicit Content-Type: the browser must set it so the multipart boundary is included.
    return this.http.post<ApiResponse<void>>(url, formData).pipe(map(() => undefined));
  }

  saveDraft(payload: DraftPayload): Observable<{ draftId: string }> {
    return this.http.post<ApiResponse<{ draftId: string }>>(`${this.baseUrl}/drafts`, payload)
      .pipe(map(res => res.data));
  }

  getDrafts(phone: string): Observable<DraftRecord[]> {
    return this.http.get<ApiResponse<DraftRecord[]>>(`${this.baseUrl}/drafts?phone=${phone}`)
      .pipe(map(res => res.data || []));
  }

  getDraft(draftId: string): Observable<DraftRecord> {
    return this.http.get<ApiResponse<DraftRecord>>(`${this.baseUrl}/drafts/${draftId}`)
      .pipe(map(res => res.data));
  }

  deleteDraft(draftId: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/drafts/${draftId}`)
      .pipe(map(() => undefined));
  }
}
