import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface TatResult {
  filedAt: string;
  dueDate: string;
  resolvedAt: string;
  slaBusinessHours: number;
  elapsedBusinessHours: number;
  remainingBusinessHours: number;
  businessDaysElapsed: number;
  businessDaysRemaining: number;
  percentUsed: number;
  breached: boolean;
  status: string;
}

@Injectable({ providedIn: 'root' })
export class TatService {

  private http = inject(HttpClient);

  getComplaintTat(complaintNumber: string): Observable<TatResult> {
    return this.http.get<TatResult>(
      `${environment.apiBaseUrl}/api/v1/tat/complaint/${complaintNumber}`
    );
  }
}
