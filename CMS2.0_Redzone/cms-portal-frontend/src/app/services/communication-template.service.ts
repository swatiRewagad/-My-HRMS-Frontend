import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CommunicationTemplate {
  id: number;
  templateName: string;
  mode: string;
  triggerCondition: string;
  schemeVersion: string;
  subjectTemplate: string;
  bodyTemplate: string;
  description: string;
  category: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CommunicationTemplateService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/communication-templates`;

  getAll(): Observable<CommunicationTemplate[]> {
    return this.http.get<CommunicationTemplate[]>(`${this.baseUrl}`);
  }

  getActive(): Observable<CommunicationTemplate[]> {
    return this.http.get<CommunicationTemplate[]>(`${this.baseUrl}/active`);
  }

  getById(id: number): Observable<CommunicationTemplate> {
    return this.http.get<CommunicationTemplate>(`${this.baseUrl}/${id}`);
  }

  getByTrigger(triggerCondition: string, mode?: string): Observable<CommunicationTemplate[]> {
    const params: any = { triggerCondition };
    if (mode) params.mode = mode;
    return this.http.get<CommunicationTemplate[]>(`${this.baseUrl}/by-trigger`, { params });
  }

  getForScheme(triggerCondition: string, schemeVersion: string): Observable<CommunicationTemplate[]> {
    return this.http.get<CommunicationTemplate[]>(`${this.baseUrl}/for-scheme`, {
      params: { triggerCondition, schemeVersion }
    });
  }

  create(template: Partial<CommunicationTemplate>): Observable<CommunicationTemplate> {
    return this.http.post<CommunicationTemplate>(`${this.baseUrl}`, template);
  }

  update(id: number, template: Partial<CommunicationTemplate>): Observable<CommunicationTemplate> {
    return this.http.put<CommunicationTemplate>(`${this.baseUrl}/${id}`, template);
  }

  deactivate(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  activate(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/activate`, {});
  }

  render(templateId: number, variables: Record<string, string>): Observable<{ subject: string; body: string }> {
    return this.http.post<{ subject: string; body: string }>(`${this.baseUrl}/render`, variables, {
      params: { templateId: templateId.toString() }
    });
  }
}
