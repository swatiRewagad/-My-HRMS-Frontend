import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpEventType } from '@angular/common/http';
import { Observable, Subject, lastValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';

export interface ChunkUploadProgress {
  uploadId: string;
  fileName: string;
  chunksReceived: number;
  totalChunks: number;
  percent: number;
  status: 'uploading' | 'completed' | 'error';
  file?: any;
  error?: string;
}

@Injectable({ providedIn: 'root' })
export class EcmService {
  private api = environment.apiUrl;
  private headers = new HttpHeaders({ 'X-User-Id': '1' });
  private chunkSize = 1024 * 1024; // 1 MB

  constructor(private http: HttpClient) {}

  getDashboard(): Observable<any> {
    return this.http.get(`${this.api}/dashboard`);
  }

  getRootFolders(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/folders`);
  }

  getFolder(id: number): Observable<any> {
    return this.http.get(`${this.api}/folders/${id}`);
  }

  createFolder(data: { name: string; visibility: string; description?: string; parentId?: number }): Observable<any> {
    return this.http.post(`${this.api}/folders`, data, { headers: this.headers });
  }

  grantAccess(folderId: number, userId: number, permission: string): Observable<any> {
    return this.http.post(`${this.api}/folders/${folderId}/access`, { userId, permission });
  }

  revokeAccess(folderId: number, userId: number): Observable<any> {
    return this.http.delete(`${this.api}/folders/${folderId}/access/${userId}`);
  }

  getFilesByFolder(folderId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/files/folder/${folderId}`);
  }

  uploadFile(file: File, folderId: number): Observable<any> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('folderId', folderId.toString());
    return this.http.post(`${this.api}/files/upload`, formData, { headers: this.headers });
  }

  downloadFile(fileId: number): Observable<Blob> {
    return this.http.get(`${this.api}/files/${fileId}/download`, { responseType: 'blob' });
  }

  getPreviewUrl(fileId: number): string {
    return `${this.api}/files/${fileId}/preview`;
  }

  getStreamUrl(fileId: number): string {
    return `${this.api}/files/${fileId}/stream`;
  }

  previewFile(fileId: number): Observable<Blob> {
    return this.http.get(`${this.api}/files/${fileId}/preview`, { responseType: 'blob' });
  }

  deleteFile(fileId: number): Observable<any> {
    return this.http.delete(`${this.api}/files/${fileId}`);
  }

  searchFiles(query: string): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/files/search`, { params: new HttpParams().set('query', query) });
  }

  shareFile(data: { fileId: number; shareType: string; sharedWith?: number; permission?: string; expiresInHours?: number }): Observable<any> {
    return this.http.post(`${this.api}/files/share`, data, { headers: this.headers });
  }

  getSharedWithMe(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/files/shared-with-me`, { headers: this.headers });
  }

  getUsers(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/users`);
  }

  uploadFileChunked(file: File, folderId: number): Subject<ChunkUploadProgress> {
    const progress$ = new Subject<ChunkUploadProgress>();
    const totalChunks = Math.ceil(file.size / this.chunkSize);

    this.http.post<any>(`${this.api}/files/upload/init`, {
      fileName: file.name,
      contentType: file.type || 'application/octet-stream',
      totalSize: file.size,
      totalChunks,
      folderId,
    }, { headers: this.headers }).subscribe({
      next: (initResp) => {
        this.sendChunks(file, initResp.uploadId, totalChunks, progress$);
      },
      error: (err) => {
        progress$.next({ uploadId: '', fileName: file.name, chunksReceived: 0, totalChunks, percent: 0, status: 'error', error: err.message });
        progress$.complete();
      },
    });

    return progress$;
  }

  private async sendChunks(file: File, uploadId: string, totalChunks: number, progress$: Subject<ChunkUploadProgress>) {
    for (let i = 0; i < totalChunks; i++) {
      const start = i * this.chunkSize;
      const end = Math.min(start + this.chunkSize, file.size);
      const blob = file.slice(start, end);

      const formData = new FormData();
      formData.append('uploadId', uploadId);
      formData.append('chunkIndex', i.toString());
      formData.append('chunk', blob, file.name);

      try {
        const resp = await lastValueFrom(
          this.http.post<any>(`${this.api}/files/upload/chunk`, formData)
        );

        const percent = Math.round(((i + 1) / totalChunks) * 100);
        progress$.next({
          uploadId,
          fileName: file.name,
          chunksReceived: resp.chunksReceived,
          totalChunks,
          percent,
          status: resp.status === 'completed' ? 'completed' : 'uploading',
          file: resp.file,
        });
      } catch (err: any) {
        progress$.next({ uploadId, fileName: file.name, chunksReceived: i, totalChunks, percent: Math.round((i / totalChunks) * 100), status: 'error', error: err.message });
        progress$.complete();
        return;
      }
    }
    progress$.complete();
  }
}
