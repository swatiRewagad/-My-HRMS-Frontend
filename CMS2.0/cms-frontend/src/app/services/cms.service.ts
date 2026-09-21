import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { FormSchema } from '../models/form-schema.model';

/** The standard backend response envelope. `data` is absent on error responses. */
interface ApiEnvelope<T> {
  success: boolean;
  message?: string;
  data?: T;
  correlationId?: string;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class CmsService {
  private api = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<any> {
    return this.http.get(`${this.api}/dashboard`);
  }

  // ───── Complaints ─────

  getComplaints(params?: { status?: string; search?: string }): Observable<any[]> {
    let httpParams = new HttpParams();
    if (params?.status) httpParams = httpParams.set('status', params.status);
    if (params?.search) httpParams = httpParams.set('search', params.search);
    return this.http.get<any[]>(`${this.api}/complaints`, { params: httpParams });
  }

  getComplaint(id: number): Observable<any> {
    return this.http.get(`${this.api}/complaints/${id}`);
  }

  trackComplaint(complaintNumber: string): Observable<any> {
    return this.http.get(`${this.api}/complaints/track/${complaintNumber}`);
  }

  fileComplaint(data: any): Observable<any> {
    return this.http.post(`${this.api}/complaints`, data);
  }

  updateComplaint(id: number, data: any): Observable<any> {
    return this.http.put(`${this.api}/complaints/${id}`, data);
  }

  deleteComplaint(id: number): Observable<any> {
    return this.http.delete(`${this.api}/complaints/${id}`);
  }

  getTimeline(complaintId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/complaints/${complaintId}/timeline`);
  }

  // ───── Categories ─────

  getCategories(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/categories`);
  }

  // ───── Banks ─────

  getBanks(type?: string): Observable<any[]> {
    let params = new HttpParams();
    if (type) params = params.set('type', type);
    return this.http.get<any[]>(`${this.api}/banks`, { params });
  }

  // ───── Form Config ─────

  getFormSchema(formKey: string): Observable<FormSchema> {
    return this.http.get<FormSchema>(`${this.api}/form-config/${formKey}`);
  }

  // ───── Email Simulation ─────
  // These endpoints wrap their payload in the standard { success, data, timestamp } envelope,
  // so each method unwraps here and hands callers the payload directly.

  simulateIncomingEmail(data: { fromEmail: string; fromName: string; subject: string; body: string }): Observable<any> {
    return this.http.post<ApiEnvelope<any>>(`${this.api}/email-simulation/receive`, data)
      .pipe(map(res => res.data));
  }

  replyWithForm(data: any): Observable<any> {
    return this.http.post<ApiEnvelope<any>>(`${this.api}/email-simulation/reply-with-form`, data)
      .pipe(map(res => res.data));
  }

  getEmailThreads(): Observable<any[]> {
    return this.http.get<ApiEnvelope<any[]>>(`${this.api}/email-simulation/threads`)
      .pipe(map(res => res.data ?? []));
  }

  getEmailThread(threadId: string): Observable<any> {
    return this.http.get<ApiEnvelope<any>>(`${this.api}/email-simulation/threads/${threadId}`)
      .pipe(map(res => res.data));
  }

  getEmailStats(): Observable<any> {
    return this.http.get<ApiEnvelope<any>>(`${this.api}/email-simulation/stats`)
      .pipe(map(res => res.data));
  }
}
