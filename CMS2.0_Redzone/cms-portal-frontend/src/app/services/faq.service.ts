import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Faq {
  id: number;
  questionKey: string;
  answerKey: string;
  category: string;
  sortOrder: number;
}

@Injectable({ providedIn: 'root' })
export class FaqService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/faq`;

  getAll(): Observable<Faq[]> {
    return this.http.get<Faq[]>(this.baseUrl);
  }

  getByCategory(category: string): Observable<Faq[]> {
    return this.http.get<Faq[]>(this.baseUrl, { params: { category } });
  }
}
