import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { ComplaintRegistrationRequest, ComplaintAcknowledgement, ComplaintStatus } from '../models/complaint.model';

export interface DraftPayload {
  phone: string;
  entityName: string;
  formData: Record<string, any>;
  eligibilityAnswers: Record<string, any>;
  currentStep: number;
  phase: string;
}

export interface DraftRecord {
  draftId: string;
  phone: string;
  entityName: string;
  formData: Record<string, any>;
  eligibilityAnswers: Record<string, any>;
  currentStep: number;
  phase: string;
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

  uploadAttachments(complaintId: string, files: File[]): Observable<void> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/${complaintId}/attachments`, formData)
      .pipe(map(() => undefined));
  }

  trackComplaint(complaintId: string): Observable<ComplaintStatus> {
    return this.http.get<ApiResponse<ComplaintStatus>>(`${this.baseUrl}/${complaintId}`)
      .pipe(map(res => res.data));
  }

  withdrawComplaint(complaintId: string, reason: string, remarks: string): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/${complaintId}/withdraw`, { reason, remarks })
      .pipe(map(() => undefined));
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
