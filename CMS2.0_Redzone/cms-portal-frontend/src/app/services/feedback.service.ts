import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface FeedbackPayload {
  complaintNumber: string;
  overallRating: number;
  easeOfFiling: number;
  timelinessRating?: number;
  communicationRating?: number;
  satisfactionRating?: number;
  grievanceRedressTime: number;
  sourceOfInformation: string;
  sourceOtherText?: string;
  cmsPortalAwareness: string;
  feedbackText?: string;
  suggestions?: string;
  complainantPhone?: string;
}

export interface FeedbackResponse {
  success: boolean;
  message: string;
  data: any;
  timestamp: string;
}

@Injectable({ providedIn: 'root' })
export class FeedbackService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/feedback`;

  /**
   * Submit feedback for a closed/resolved/rejected complaint.
   */
  submitFeedback(data: FeedbackPayload): Observable<FeedbackResponse> {
    return this.http.post<FeedbackResponse>(this.baseUrl, data);
  }

  /**
   * Get feedback for a specific complaint number.
   */
  getFeedbackForComplaint(complaintNumber: string): Observable<FeedbackResponse> {
    return this.http.get<FeedbackResponse>(`${this.baseUrl}/${complaintNumber}`);
  }

  /**
   * Get all feedback for a given office code (staff view).
   */
  getFeedbackByOffice(officeCode: string): Observable<FeedbackResponse> {
    return this.http.get<FeedbackResponse>(`${this.baseUrl}/office/${officeCode}`);
  }

  /**
   * Get all feedback (CEPD admin view).
   */
  getAllFeedback(): Observable<FeedbackResponse> {
    return this.http.get<FeedbackResponse>(`${this.baseUrl}/all`);
  }

  /**
   * Get closed complaints for a given phone number (for feedback selection).
   */
  getClosedComplaints(phone: string): Observable<any[]> {
    return this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/by-phone/${phone}`)
      .pipe(
        map(res => {
          const complaints = res.data || res || [];
          return (Array.isArray(complaints) ? complaints : []).filter(
            (c: any) => ['closed', 'resolved', 'rejected'].includes(c.status)
          );
        })
      );
  }
}
