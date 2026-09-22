import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ComplaintEmail {
  id: number;
  messageId: string;
  threadId: string;
  /** INBOUND | OUTBOUND — the one vocabulary the server now emits. */
  direction: string;
  from: string;
  to: string;
  cc?: string;
  subject: string;
  body: string;
  templateUsed?: string;
  sentAt: string;
  status: string;
}

export interface TimelineEntry {
  id: number;
  action: string;
  performedBy?: string;
  performedByRole?: string;
  remarks?: string;
  fromStatus?: string;
  toStatus?: string;
  performedAt: string;
  timestamp: string;
  eventSource?: string;
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  closureClause?: string;
  destinationOffice?: string;
}

export interface ComplaintAttachmentRow {
  id: number;
  originalName: string;
  contentType?: string;
  fileSize?: number;
  uploadedAt: string;
  uploadedBy?: string;
  /** OFFICER | COMPLAINANT | REGULATED_ENTITY | SYSTEM */
  source?: string;
  documentType?: string;
}

/**
 * Email log, history and attachments for one complaint (UST585-597).
 *
 * <p>NO SILENT catchError HERE, deliberately. The pattern used elsewhere in this service layer —
 * `catchError(() => of([]))` — is what hid the fact that the History tab called
 * `/api/v1/complaints/{id}/action-override`, an endpoint that does not exist: the 404 became an empty
 * array and the tab rendered "no records" for every complaint ever created. Errors propagate to the
 * subscriber so the component can distinguish "nothing to show" from "the request failed".
 */
@Injectable({ providedIn: 'root' })
export class ComplaintCorrespondenceService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/complaints`;
  private filesUrl = `${environment.apiBaseUrl}/api/files`;

  getEmails(complaintNumber: string): Observable<ComplaintEmail[]> {
    return this.http.get<any>(`${this.baseUrl}/${complaintNumber}/emails`)
      .pipe(map(res => res?.data ?? []));
  }

  sendEmail(complaintNumber: string, payload: {
    recipients: string[]; subject?: string; body?: string;
    templateId?: number | null; inReplyToId?: number | null;
  }): Observable<any> {
    return this.http.post<any>(`${this.baseUrl}/${complaintNumber}/emails`, payload)
      .pipe(map(res => res?.data ?? res));
  }

  getHistory(complaintNumber: string): Observable<TimelineEntry[]> {
    return this.http.get<any>(`${this.baseUrl}/${complaintNumber}/history`)
      .pipe(map(res => res?.data ?? []));
  }

  getAttachments(complaintId: number | string): Observable<ComplaintAttachmentRow[]> {
    return this.http.get<any>(`${this.filesUrl}/complaint/${complaintId}`)
      .pipe(map(res => res?.data ?? res ?? []));
  }

  /** The bundle URL (UST588). A plain link, so the browser handles the download natively. */
  bundleUrl(complaintId: number | string, complaintNumber: string): string {
    return `${this.filesUrl}/complaint/${complaintId}/bundle`
      + `?complaintNumber=${encodeURIComponent(complaintNumber)}`;
  }

  uploadAttachment(complaintNumber: string, complaintId: number | string,
                   file: File, documentType?: string): Observable<any> {
    const form = new FormData();
    form.append('file', file, file.name);
    form.append('complaintNumber', complaintNumber);
    form.append('complaintId', String(complaintId));
    if (documentType) form.append('documentType', documentType);
    return this.http.post<any>(`${this.filesUrl}/upload`, form);
  }
}
