import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface UploadResponse {
  fileIds: string[];
  urls: string[];
}

@Injectable({ providedIn: 'root' })
export class FileUploadService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/storage`;

  uploadFiles(files: File[], folder = 'complaints'): Observable<UploadResponse> {
    const formData = new FormData();
    files.forEach(file => formData.append('files', file));
    formData.append('folder', folder);
    return this.http.post<UploadResponse>(`${this.baseUrl}/upload`, formData);
  }

  uploadSingleFile(file: File, folder = 'complaints'): Observable<UploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folder', folder);
    return this.http.post<UploadResponse>(`${this.baseUrl}/upload`, formData);
  }

  deleteFile(fileId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${fileId}`);
  }
}
