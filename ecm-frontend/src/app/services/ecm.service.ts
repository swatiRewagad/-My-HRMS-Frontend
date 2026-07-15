import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpEventType } from '@angular/common/http';
import { Observable, Subject, lastValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { KeycloakService } from './keycloak.service';

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
  currentUserId = '-1';
  private chunkSize = 1024 * 1024; // 1 MB
  private userResolved = false;

  constructor(private http: HttpClient, private keycloak: KeycloakService) {}

  private get headers(): HttpHeaders {
    return new HttpHeaders({ 'X-User-Id': this.currentUserId });
  }

  async resolveCurrentUser(): Promise<void> {
    if (this.userResolved) return;
    const username = this.keycloak.username;
    console.log('[ECM] Resolving user for:', username);
    if (username) {
      try {
        const resp = await fetch(`${this.api}/users/me`, {
          headers: { 'X-Username': username }
        });
        if (resp.ok) {
          const user = await resp.json();
          this.currentUserId = user.id.toString();
          console.log('[ECM] Resolved userId:', this.currentUserId);
        } else {
          console.error('[ECM] /users/me returned:', resp.status);
          this.currentUserId = '1';
        }
      } catch (e) {
        console.error('[ECM] Failed to resolve user:', e);
        this.currentUserId = '1';
      }
    } else {
      console.warn('[ECM] No username available');
      this.currentUserId = '1';
    }
    this.userResolved = true;
  }

  getDashboard(): Observable<any> {
    return this.http.get(`${this.api}/dashboard`);
  }

  getRootFolders(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/folders`, { headers: this.headers });
  }

  getFolder(id: number): Observable<any> {
    return this.http.get(`${this.api}/folders/${id}`);
  }

  createFolder(data: { name: string; visibility: string; description?: string; parentId?: number }): Observable<any> {
    return this.http.post(`${this.api}/folders`, data, { headers: this.headers });
  }

  deleteFolder(folderId: number): Observable<any> {
    return this.http.delete(`${this.api}/folders/${folderId}`, { headers: this.headers });
  }

  grantAccess(folderId: number, userId: number, permission: string): Observable<any> {
    return this.http.post(`${this.api}/folders/${folderId}/access`, { userId, permission });
  }

  grantBulkAccess(folderId: number, requests: { userId: number; permission: string }[]): Observable<any> {
    return this.http.post(`${this.api}/folders/${folderId}/access/bulk`, requests);
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

  extractInvoice(fileId: number, docTypeConfigId?: number): Observable<any> {
    const params = docTypeConfigId ? new HttpParams().set('docTypeConfigId', docTypeConfigId.toString()) : undefined;
    return this.http.post(`${this.api}/invoice/extract/${fileId}`, {}, { params });
  }

  getInvoiceExtraction(fileId: number): Observable<any> {
    return this.http.get(`${this.api}/invoice/extraction/${fileId}`);
  }

  // ───── Admin Config APIs ─────

  getProjects(): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/admin/projects`);
  }

  getProject(id: number): Observable<any> {
    return this.http.get(`${this.api}/admin/projects/${id}`);
  }

  createProject(data: { code: string; name: string; description?: string }): Observable<any> {
    return this.http.post(`${this.api}/admin/projects`, data, { headers: this.headers });
  }

  updateProject(id: number, data: { code: string; name: string; description?: string }): Observable<any> {
    return this.http.put(`${this.api}/admin/projects/${id}`, data);
  }

  deleteProject(id: number): Observable<any> {
    return this.http.delete(`${this.api}/admin/projects/${id}`);
  }

  saveUploadConfig(projectId: number, data: any): Observable<any> {
    return this.http.post(`${this.api}/admin/projects/${projectId}/upload-config`, data);
  }

  getDocTypes(projectId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/admin/projects/${projectId}/doc-types`);
  }

  createDocType(projectId: number, data: any): Observable<any> {
    return this.http.post(`${this.api}/admin/projects/${projectId}/doc-types`, data);
  }

  updateDocType(docTypeId: number, data: any): Observable<any> {
    return this.http.put(`${this.api}/admin/doc-types/${docTypeId}`, data);
  }

  deleteDocType(docTypeId: number): Observable<any> {
    return this.http.delete(`${this.api}/admin/doc-types/${docTypeId}`);
  }

  getExtractionFields(docTypeId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/admin/doc-types/${docTypeId}/fields`);
  }

  saveExtractionFields(docTypeId: number, fields: any[]): Observable<any[]> {
    return this.http.post<any[]>(`${this.api}/admin/doc-types/${docTypeId}/fields`, fields);
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
