import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface OfficerComplaint {
  complaintId: string;
  category: string;
  priority: string;
  status: string;
  subject: string;
  complainantName: string;
  entityName: string;
  assignedTeam: string;
  assignedTo: string;
  amountInvolved: number;
  createdAt: string;
  slaDueDate: string;
  slaPercentage: number;
}

export interface ComplaintDetail {
  complaintId: string;
  category: string;
  priority: string;
  status: string;
  subject: string;
  description: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  entityName: string;
  entityType: string;
  amountInvolved: number;
  transactionDate: string;
  assignedTeam: string;
  assignedTo: string;
  createdAt: string;
  slaDueDate: string;
  resolutionSummary: string;
  resolvedAt: string;
  timeline: { fromStatus: string; toStatus: string; action: string; timestamp: string; remarks: string }[];
}

export interface TransitionRequest {
  targetStatus: string;
  remarks?: string;
  resolutionSummary?: string;
  escalationReason?: string;
}

@Injectable({ providedIn: 'root' })
export class OfficerService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1`;

  getAssignedComplaints(team: string, status?: string): Observable<OfficerComplaint[]> {
    let url = `${this.baseUrl}/workflow/tasks?team=${team}`;
    if (status) url += `&status=${status}`;
    return this.http.get<ApiResponse<OfficerComplaint[]>>(url)
      .pipe(map(res => res.data));
  }

  getComplaintDetail(complaintId: string): Observable<ComplaintDetail> {
    return this.http.get<ApiResponse<ComplaintDetail>>(`${this.baseUrl}/complaints/${complaintId}`)
      .pipe(map(res => res.data));
  }

  transitionComplaint(complaintId: string, request: TransitionRequest): Observable<void> {
    const params = new URLSearchParams();
    params.set('targetStatus', request.targetStatus);
    if (request.remarks) params.set('remarks', request.remarks);
    return this.http.post<ApiResponse<void>>(
      `${this.baseUrl}/workflow/${complaintId}/transition?${params.toString()}`,
      null
    ).pipe(map(() => undefined));
  }

  acceptComplaint(complaintId: string, officerName: string): Observable<void> {
    return this.transitionComplaint(complaintId, {
      targetStatus: 'IN_PROGRESS',
      remarks: `Accepted by ${officerName}`
    });
  }

  resolveComplaint(complaintId: string, resolution: string): Observable<void> {
    return this.transitionComplaint(complaintId, {
      targetStatus: 'RESOLVED',
      remarks: resolution,
      resolutionSummary: resolution
    });
  }

  escalateComplaint(complaintId: string, reason: string): Observable<void> {
    return this.http.post<ApiResponse<void>>(
      `${this.baseUrl}/workflow/${complaintId}/escalate?reason=${encodeURIComponent(reason)}`,
      null
    ).pipe(map(() => undefined));
  }
}
