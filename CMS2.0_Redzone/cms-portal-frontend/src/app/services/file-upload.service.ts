import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface StorageUploadResponse {
  success: boolean;
  data: { storagePath: string; checksum: string };
}

@Injectable({ providedIn: 'root' })
export class FileUploadService {
  private http = inject(HttpClient);
  private baseUrl = environment.storageBaseUrl;

  uploadSingleFile(file: File, bucket = 'complaints'): Observable<StorageUploadResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<StorageUploadResponse>(`${this.baseUrl}/${bucket}`, formData);
  }

  deleteFile(storagePath: string): Observable<void> {
    return this.http.delete<void>(this.baseUrl, { params: { path: storagePath } });
  }

  getViewUrl(storagePath: string): string {
    return `${this.baseUrl}/view?path=${encodeURIComponent(storagePath)}`;
  }

  getDownloadUrl(storagePath: string): string {
    return `${this.baseUrl}/download?path=${encodeURIComponent(storagePath)}`;
  }
}
