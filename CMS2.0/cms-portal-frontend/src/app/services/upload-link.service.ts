import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface UploadLinkStatus {
  complaintNumber: string;
  linkActive: boolean;
  sentAt: string | null;
  expiresAt: string | null;
  documentsSubmitted: boolean;
  uploadedFiles: { name: string; size: number; uploadedAt: string }[];
}

@Injectable({ providedIn: 'root' })
export class UploadLinkService {
  private http = inject(HttpClient);
  private apiUrl = `${environment.apiBaseUrl}/api/v1/upload-link`;

  sendLink(complaintNumber: string) {
    return this.http.post<{ success: boolean; expiresAt: string }>(`${this.apiUrl}/send`, { complaintNumber });
  }

  getStatus(complaintNumber: string) {
    return this.http.get<UploadLinkStatus>(`${this.apiUrl}/status/${complaintNumber}`);
  }

  validateOtp(token: string, emailOtp: string, mobileOtp: string) {
    return this.http.post<{ valid: boolean; complaintNumber: string }>(`${this.apiUrl}/validate-otp`, { token, emailOtp, mobileOtp });
  }

  uploadDocuments(token: string, files: FormData) {
    return this.http.post<{ uploaded: number }>(`${this.apiUrl}/upload/${token}`, files);
  }

  revokeLink(complaintNumber: string) {
    return this.http.post<{ success: boolean }>(`${this.apiUrl}/revoke`, { complaintNumber });
  }
}
