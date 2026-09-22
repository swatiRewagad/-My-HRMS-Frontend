import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { ApiResponse } from '../models/api-response.model';

export interface FileMeta {
  fileId: string;
  fileName: string;
  fileSize: number;
  viewUrl: string;
}

export interface AppealDraftPayload {
  phone: string;
  complaintNumber: string;
  classification: string;
  formData: Record<string, any>;
  currentStep: number;
  highestStepReached: number;
  phase: string;
}

export interface AppealDraftRecord extends AppealDraftPayload {
  draftId: string;
  updatedAt: string;
}

@Injectable({ providedIn: 'root' })
export class AppealService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/appeals`;
  private storageUrl = environment.storageBaseUrl;

  checkEligibility(complaintNumber: string): Observable<any> {
    return this.http
      .get<ApiResponse<any>>(`${this.baseUrl}/check-eligibility?complaintNumber=${encodeURIComponent(complaintNumber)}`)
      .pipe(map(res => res.data));
  }

  uploadFile(file: File, bucket = 'appeals'): Observable<FileMeta> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http
      .post<ApiResponse<{ storagePath: string; checksum: string }>>(`${this.storageUrl}/${bucket}`, formData)
      .pipe(map(res => ({
        fileId: res.data.storagePath,
        fileName: file.name,
        fileSize: file.size,
        viewUrl: res.data.storagePath,
      })));
  }

  deleteFile(storagePath: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(this.storageUrl, { params: { path: storagePath } })
      .pipe(map(() => undefined));
  }

  saveDraft(payload: AppealDraftPayload): Observable<{ draftId: string }> {
    return this.http
      .post<ApiResponse<{ draftId: string }>>(`${this.baseUrl}/drafts`, payload)
      .pipe(map(res => res.data));
  }

  getDraft(draftId: string): Observable<AppealDraftRecord> {
    return this.http
      .get<ApiResponse<AppealDraftRecord>>(`${this.baseUrl}/drafts/${draftId}`)
      .pipe(map(res => res.data));
  }

  deleteDraft(draftId: string): Observable<void> {
    return this.http
      .delete<ApiResponse<void>>(`${this.baseUrl}/drafts/${draftId}`)
      .pipe(map(() => undefined));
  }

  submitAppeal(payload: Record<string, any>): Observable<{ appealNumber: string }> {
    return this.http
      .post<ApiResponse<{ appealNumber: string }>>(`${this.baseUrl}/file`, payload)
      .pipe(map(res => res.data));
  }
}
