import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface CategorizationRule {
  id?: number;
  ruleName: string;
  keywords: string;
  categoryId: number;
  priority: string;
  status: string;
  source: string;
  description: string;
  ruleOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CategorizationResult {
  categoryId: number;
  categoryName: string;
  matchedRule: string;
  matchedKeywords: string[];
  priority: string;
  confidence: number;
}

@Injectable({ providedIn: 'root' })
export class CategorizationRuleService {
  private api = `${environment.apiUrl}/categorization-rules`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<CategorizationRule[]> {
    return this.http.get<CategorizationRule[]>(this.api);
  }

  getActive(): Observable<CategorizationRule[]> {
    return this.http.get<CategorizationRule[]>(`${this.api}/active`);
  }

  getById(id: number): Observable<CategorizationRule> {
    return this.http.get<CategorizationRule>(`${this.api}/${id}`);
  }

  create(rule: Partial<CategorizationRule>): Observable<CategorizationRule> {
    return this.http.post<CategorizationRule>(this.api, rule);
  }

  update(id: number, rule: Partial<CategorizationRule>): Observable<CategorizationRule> {
    return this.http.put<CategorizationRule>(`${this.api}/${id}`, rule);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/${id}`);
  }

  categorize(text: string, source: string = 'all'): Observable<CategorizationResult[]> {
    return this.http.post<CategorizationResult[]>(`${this.api}/categorize`, { text, source });
  }
}
