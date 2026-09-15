import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import {
  EmailDraft,
  EmailIngestRequest,
  EmailDraftUpdateRequest,
  IgnoreListEntry,
  IgnoreListRequest,
  DeoUser,
  EmailQueueStats
} from '../models/email-syndication.model';

@Injectable({ providedIn: 'root' })
export class EmailSyndicationService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/email-syndication`;

  // Queue & Drafts
  getQueue(status?: string, assignedTo?: string): Observable<EmailDraft[]> {
    const params: Record<string, string> = {};
    if (status) params['status'] = status;
    if (assignedTo) params['assignedTo'] = assignedTo;
    return this.http.get<ApiResponse<EmailDraft[]>>(`${this.baseUrl}/queue`, { params })
      .pipe(map(res => res.data));
  }

  getDraft(draftId: string): Observable<EmailDraft> {
    return this.http.get<ApiResponse<EmailDraft>>(`${this.baseUrl}/drafts/${draftId}`)
      .pipe(map(res => res.data));
  }

  updateDraft(draftId: string, request: EmailDraftUpdateRequest): Observable<EmailDraft> {
    return this.http.put<ApiResponse<EmailDraft>>(`${this.baseUrl}/drafts/${draftId}`, request)
      .pipe(map(res => res.data));
  }

  convertToComplaint(draftId: string): Observable<EmailDraft> {
    return this.http.post<ApiResponse<EmailDraft>>(`${this.baseUrl}/drafts/${draftId}/convert`, {})
      .pipe(map(res => res.data));
  }

  reassignDraft(draftId: string, targetDeoId: string): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/drafts/${draftId}/reassign`, null, {
      params: { targetDeoId }
    }).pipe(map(res => res.data));
  }

  ingestEmail(request: EmailIngestRequest): Observable<EmailDraft> {
    return this.http.post<ApiResponse<EmailDraft>>(`${this.baseUrl}/ingest`, request)
      .pipe(map(res => res.data));
  }

  ingestEmailWithAttachment(request: EmailIngestRequest, file: File): Observable<EmailDraft> {
    const formData = new FormData();
    formData.append('senderEmail', request.senderEmail);
    formData.append('subject', request.subject);
    formData.append('body', request.body || '');
    formData.append('messageId', request.messageId || '');
    formData.append('attachment', file);
    return this.http.post<ApiResponse<EmailDraft>>(`${this.baseUrl}/ingest-with-attachment`, formData)
      .pipe(map(res => res.data));
  }

  getStats(): Observable<EmailQueueStats> {
    return this.http.get<ApiResponse<EmailQueueStats>>(`${this.baseUrl}/stats`)
      .pipe(map(res => res.data));
  }

  // Ignore List
  getIgnoreList(): Observable<IgnoreListEntry[]> {
    return this.http.get<ApiResponse<IgnoreListEntry[]>>(`${this.baseUrl}/ignore-list`)
      .pipe(map(res => res.data));
  }

  addToIgnoreList(request: IgnoreListRequest): Observable<IgnoreListEntry> {
    return this.http.post<ApiResponse<IgnoreListEntry>>(`${this.baseUrl}/ignore-list`, request)
      .pipe(map(res => res.data));
  }

  bulkAddToIgnoreList(requests: IgnoreListRequest[]): Observable<IgnoreListEntry[]> {
    return this.http.post<ApiResponse<IgnoreListEntry[]>>(`${this.baseUrl}/ignore-list/bulk`, requests)
      .pipe(map(res => res.data));
  }

  updateIgnoreEntry(id: number, request: IgnoreListRequest): Observable<IgnoreListEntry> {
    return this.http.put<ApiResponse<IgnoreListEntry>>(`${this.baseUrl}/ignore-list/${id}`, request)
      .pipe(map(res => res.data));
  }

  removeFromIgnoreList(id: number): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/ignore-list/${id}`)
      .pipe(map(res => res.data));
  }

  // DEO Management
  getDeos(): Observable<DeoUser[]> {
    return this.http.get<ApiResponse<DeoUser[]>>(`${this.baseUrl}/deo`)
      .pipe(map(res => res.data));
  }

  addDeo(deo: { userId: string; displayName: string; email: string; maxThreshold: number }): Observable<DeoUser> {
    return this.http.post<ApiResponse<DeoUser>>(`${this.baseUrl}/deo`, deo)
      .pipe(map(res => res.data));
  }

  getEligibleDeos(): Observable<DeoUser[]> {
    return this.http.get<ApiResponse<DeoUser[]>>(`${this.baseUrl}/deo/eligible`)
      .pipe(map(res => res.data));
  }

  updateDeoThreshold(userId: string, threshold: number): Observable<DeoUser> {
    return this.http.put<ApiResponse<DeoUser>>(`${this.baseUrl}/deo/${userId}/threshold`, null, {
      params: { threshold: threshold.toString() }
    }).pipe(map(res => res.data));
  }

  updateDeoStatus(userId: string, active?: boolean, onLeave?: boolean): Observable<DeoUser> {
    const params: Record<string, string> = {};
    if (active !== undefined) params['active'] = active.toString();
    if (onLeave !== undefined) params['onLeave'] = onLeave.toString();
    return this.http.put<ApiResponse<DeoUser>>(`${this.baseUrl}/deo/${userId}/status`, null, { params })
      .pipe(map(res => res.data));
  }

  removeDeo(userId: string): Observable<void> {
    return this.http.delete<ApiResponse<void>>(`${this.baseUrl}/deo/${userId}`)
      .pipe(map(res => res.data));
  }

  resetRoundRobinPointer(): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/deo/reset-pointer`, {})
      .pipe(map(res => res.data));
  }
}
