import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';
import { ComplaintRegistrationRequest, ComplaintAcknowledgement, ComplaintStatus } from '../models/complaint.model';

@Injectable({ providedIn: 'root' })
export class ComplaintService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/complaints`;

  registerComplaint(request: ComplaintRegistrationRequest): Observable<ComplaintAcknowledgement> {
    return this.http.post<ApiResponse<ComplaintAcknowledgement>>(this.baseUrl, request)
      .pipe(map(res => res.data));
  }

  uploadAttachments(complaintId: string, files: File[]): Observable<void> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/${complaintId}/attachments`, formData)
      .pipe(map(() => undefined));
  }

  trackComplaint(complaintId: string): Observable<ComplaintStatus> {
    return this.http.get<ApiResponse<ComplaintStatus>>(`${this.baseUrl}/${complaintId}`)
      .pipe(map(res => res.data));
  }

  withdrawComplaint(complaintId: string, reason: string, remarks: string): Observable<void> {
    return this.http.post<ApiResponse<void>>(`${this.baseUrl}/${complaintId}/withdraw`, { reason, remarks })
      .pipe(map(() => undefined));
  }
}
