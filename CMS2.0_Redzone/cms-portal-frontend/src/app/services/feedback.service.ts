import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface FeedbackPayload {
  complaintNumber: string;
  easeOfFiling: number;
  grievanceRedressTime: number;
  overallRating: number;
  feedbackText: string;
  sourceOfInformation: string;
  sourceOtherText: string;
  cmsPortalAwareness: string;
}

export interface FeedbackResponse {
  feedbackId: string;
}

@Injectable({ providedIn: 'root' })
export class FeedbackService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/feedback`;

  submitFeedback(payload: FeedbackPayload): Observable<FeedbackResponse> {
    return this.http
      .post<ApiResponse<FeedbackResponse>>(this.baseUrl, payload)
      .pipe(map(res => res.data));
  }
}
