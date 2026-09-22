import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CommentTemplate {
  id: number;
  title: string;
  description: string;
  content: string;
  modeOfReceipt: string;
  category: string;
  active: boolean;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class CommentTemplateService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/comment-templates`;

  getAll(): Observable<CommentTemplate[]> {
    return this.http.get<CommentTemplate[]>(`${this.baseUrl}`);
  }

  getActive(): Observable<CommentTemplate[]> {
    return this.http.get<CommentTemplate[]>(`${this.baseUrl}/active`);
  }

  getById(id: number): Observable<CommentTemplate> {
    return this.http.get<CommentTemplate>(`${this.baseUrl}/${id}`);
  }

  getByCategory(category: string): Observable<CommentTemplate[]> {
    return this.http.get<CommentTemplate[]>(`${this.baseUrl}/by-category`, { params: { category } });
  }

  getFiltered(category?: string, modeOfReceipt?: string): Observable<CommentTemplate[]> {
    const params: any = {};
    if (category) params.category = category;
    if (modeOfReceipt) params.modeOfReceipt = modeOfReceipt;
    return this.http.get<CommentTemplate[]>(`${this.baseUrl}/filtered`, { params });
  }

  create(template: Partial<CommentTemplate>): Observable<CommentTemplate> {
    return this.http.post<CommentTemplate>(`${this.baseUrl}`, template);
  }

  update(id: number, template: Partial<CommentTemplate>): Observable<CommentTemplate> {
    return this.http.put<CommentTemplate>(`${this.baseUrl}/${id}`, template);
  }

  deactivate(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/deactivate`, {});
  }

  activate(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/activate`, {});
  }
}
