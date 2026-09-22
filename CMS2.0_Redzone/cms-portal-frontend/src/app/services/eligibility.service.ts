import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { EligibilityQuestion, EligibilityCheckRequest, EligibilityCheckResponse } from '../models/eligibility.model';

@Injectable({ providedIn: 'root' })
export class EligibilityService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/eligibility`;

  getQuestions(category?: string): Observable<EligibilityQuestion[]> {
    const params: Record<string, string> = {};
    if (category) {
      params['category'] = category;
    }
    return this.http.get<ApiResponse<EligibilityQuestion[]>>(`${this.baseUrl}/questions`, { params })
      .pipe(map(res => res.data));
  }

  checkEligibility(request: EligibilityCheckRequest): Observable<EligibilityCheckResponse> {
    return this.http.post<ApiResponse<EligibilityCheckResponse>>(`${this.baseUrl}/check`, request)
      .pipe(map(res => res.data));
  }
}
