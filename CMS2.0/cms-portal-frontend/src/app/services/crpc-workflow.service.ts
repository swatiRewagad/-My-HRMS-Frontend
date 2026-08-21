import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface EmailDraft {
  id: number;
  draftId: string;
  threadId: string;
  senderEmail: string;
  subject: string;
  body: string;
  complainantName: string;
  status: string;
  assignedTo: string;
  deoDecision: string;
  deoRemarks: string;
  nonMaintainableReason: string;
  reviewerAssignedTo: string;
  reviewerDecision: string;
  reviewerRemarks: string;
  convertedComplaintId: string;
  detectedLanguage: string;
  isVernacular: boolean;
  targetEntity: string;
  targetOffice: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CrpcWorkflowService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/crpc/workflow`;

  sendForApproval(draftId: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/send-for-approval`, null, {
      params: { draftId, ...(remarks ? { remarks } : {}) }
    });
  }

  sendToOtherDeptForApproval(draftId: string, targetEntity: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/send-to-other-dept-for-approval`, null, {
      params: { draftId, targetEntity, ...(remarks ? { remarks } : {}) }
    });
  }

  sendVernacularForApproval(draftId: string, language: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/vernacular-for-approval`, null, {
      params: { draftId, language, ...(remarks ? { remarks } : {}) }
    });
  }

  markNotAComplaint(draftId: string, reason: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/not-a-complaint`, null, {
      params: { draftId, reason, ...(remarks ? { remarks } : {}) }
    });
  }

  bulkMarkNotAComplaint(draftIds: string[], reason: string, remarks?: string): Observable<EmailDraft[]> {
    return this.http.post<EmailDraft[]>(`${this.baseUrl}/bulk-not-a-complaint`, {
      draftIds, reason, remarks
    });
  }

  approve(draftId: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/approve`, null, {
      params: { draftId, ...(remarks ? { remarks } : {}) }
    });
  }

  approveNotAComplaint(draftId: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/approve-not-a-complaint`, null, {
      params: { draftId, ...(remarks ? { remarks } : {}) }
    });
  }

  approveSentToOtherDept(draftId: string, targetEntity: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/approve-sent-to-other-dept`, null, {
      params: { draftId, targetEntity, ...(remarks ? { remarks } : {}) }
    });
  }

  approveVernacular(draftId: string, remarks?: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/approve-vernacular`, null, {
      params: { draftId, ...(remarks ? { remarks } : {}) }
    });
  }

  getVernacularOfficeMap(): Observable<Record<string, string>> {
    return this.http.get<Record<string, string>>(`${this.baseUrl}/vernacular-office-map`);
  }

  sendBack(draftId: string, remarks: string): Observable<EmailDraft> {
    return this.http.post<EmailDraft>(`${this.baseUrl}/send-back`, null, {
      params: { draftId, remarks }
    });
  }

  convertToComplaint(draftId: string): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/convert-to-complaint`, null, {
      params: { draftId }
    });
  }

  getNotAComplaintReasons(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/not-a-complaint-reasons`);
  }

  getClosureLetter(complaintNumber: string, schemeVersion = 'RBIOS_2026'): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/closure-letter`, {
      params: { complaintNumber, schemeVersion },
      responseType: 'blob'
    });
  }

  previewClosureLetter(complaintNumber: string, templateId: number): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/closure-letter/preview`, {
      params: { complaintNumber, templateId: templateId.toString() },
      responseType: 'blob'
    });
  }
}
