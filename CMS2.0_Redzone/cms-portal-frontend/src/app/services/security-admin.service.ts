import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface SecurityAlert {
  id: number;
  alertType: string;
  severity: string;
  subject: string;
  subjectType: string;
  eventCount: number;
  threshold: number;
  windowLabel: string;
  details: string;
  ipAddress: string;
  status: string;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  raisedAt: string;
}

export interface RevealAuditEntry {
  id: number;
  userId: string;
  displayName: string;
  roles: string;
  complaintNumber: string;
  fieldsRevealed: string;
  justification: string;
  ipAddress: string;
  revealedAt: string;
}

export interface RetentionPolicyView {
  id: number;
  category: string;
  targetTable: string;
  retentionDays: number;
  auditCategory: boolean;
  redactInsteadOfDelete: boolean;
  enabled: boolean;
  description: string;
  rowsPastRetention: number | null;
}

/**
 * Client for the ADMIN-only security console.
 *
 * Every endpoint here is enforced server-side; this service only shapes the requests. Hiding a
 * control in the UI is not a control, so nothing here is treated as an authorization decision.
 */
@Injectable({ providedIn: 'root' })
export class SecurityAdminService {
  private http = inject(HttpClient);
  private base = `${environment.apiBaseUrl}/api/v1/admin/security`;

  getAlerts(status?: string, page = 0, size = 20): Observable<any> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) {
      query.set('status', status);
    }
    return this.http.get<any>(`${this.base}/alerts?${query.toString()}`);
  }

  acknowledgeAlert(id: number, note?: string): Observable<any> {
    return this.http.post<any>(`${this.base}/alerts/${id}/acknowledge`, { note: note ?? '' });
  }

  getEvents(subject?: string, page = 0, size = 50): Observable<any> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (subject) {
      query.set('subject', subject);
    }
    return this.http.get<any>(`${this.base}/events?${query.toString()}`);
  }

  getPiiReveals(userId?: string, complaintNumber?: string, page = 0, size = 50): Observable<any> {
    const query = new URLSearchParams({ page: String(page), size: String(size) });
    if (userId) {
      query.set('userId', userId);
    }
    if (complaintNumber) {
      query.set('complaintNumber', complaintNumber);
    }
    return this.http.get<any>(`${this.base}/pii-reveals?${query.toString()}`);
  }

  revoke(username: string, reason: string, notes?: string): Observable<any> {
    return this.http.post<any>(`${this.base}/revocations`, { username, reason, notes });
  }

  restore(username: string): Observable<any> {
    return this.http.delete<any>(`${this.base}/revocations/${encodeURIComponent(username)}`);
  }

  getRevocations(page = 0, size = 50): Observable<any> {
    return this.http.get<any>(`${this.base}/revocations?page=${page}&size=${size}`);
  }

  getRetentionPolicies(): Observable<any> {
    return this.http.get<any>(`${this.base}/retention/policies`);
  }

  updateRetentionPolicy(category: string, body: { retentionDays?: number; enabled?: boolean }): Observable<any> {
    return this.http.put<any>(`${this.base}/retention/policies/${encodeURIComponent(category)}`, body);
  }

  runRetention(category?: string): Observable<any> {
    const suffix = category ? `?category=${encodeURIComponent(category)}` : '';
    return this.http.post<any>(`${this.base}/retention/run${suffix}`, {});
  }

  getDeletionLog(page = 0, size = 50): Observable<any> {
    return this.http.get<any>(`${this.base}/retention/deletion-log?page=${page}&size=${size}`);
  }

  getConfig(): Observable<any> {
    return this.http.get<any>(`${this.base}/config`);
  }

  updateConfig(key: string, value: string): Observable<any> {
    return this.http.put<any>(`${this.base}/config/${encodeURIComponent(key)}`, { value });
  }
}
