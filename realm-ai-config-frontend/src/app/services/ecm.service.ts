import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpRequest, HttpEventType, HttpEvent } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface EcmFileDto {
  id: number;
  realmId: string;
  fileName: string;
  filePath: string;
  folderPath: string;
  fileSize: number;
  fileType: string;
  uploadedBy: string;
  uploadedAt: string;
  status: string;
}

export interface ExtractedFieldDto {
  id: number;
  ecmFileId: number;
  fieldName: string;
  fieldValue: string;
  confidence: number;
  ocrEngine: string;
  extractedAt: string;
}

@Injectable({ providedIn: 'root' })
export class EcmService {
  private baseUrl = `${environment.apiUrl}/ecm`;

  constructor(private http: HttpClient) {}

  uploadFiles(realmId: string, folderPath: string, uploadedBy: string, files: File[]): Observable<EcmFileDto[]> {
    const formData = new FormData();
    formData.append('realmId', realmId);
    formData.append('folderPath', folderPath);
    formData.append('uploadedBy', uploadedBy);
    files.forEach(f => formData.append('files', f));
    return this.http.post<EcmFileDto[]>(`${this.baseUrl}/upload`, formData);
  }

  getFiles(realmId: string, folderPath?: string): Observable<EcmFileDto[]> {
    let params = new HttpParams().set('realmId', realmId);
    if (folderPath) params = params.set('folderPath', folderPath);
    return this.http.get<EcmFileDto[]>(`${this.baseUrl}/files`, { params });
  }

  deleteFile(id: number): Observable<{ message: string }> {
    return this.http.delete<{ message: string }>(`${this.baseUrl}/files/${id}`);
  }

  getPreviewUrl(id: number): string {
    return `${this.baseUrl}/preview/${id}`;
  }

  uploadFilesWithProgress(realmId: string, folderPath: string, uploadedBy: string, files: File[]): Observable<HttpEvent<EcmFileDto[]>> {
    const formData = new FormData();
    formData.append('realmId', realmId);
    formData.append('folderPath', folderPath);
    formData.append('uploadedBy', uploadedBy);
    files.forEach(f => formData.append('files', f));
    const req = new HttpRequest('POST', `${this.baseUrl}/upload`, formData, { reportProgress: true });
    return this.http.request(req) as Observable<HttpEvent<EcmFileDto[]>>;
  }

  getExtractedFields(fileId: number): Observable<ExtractedFieldDto[]> {
    return this.http.get<ExtractedFieldDto[]>(`${this.baseUrl}/extracted-fields/${fileId}`);
  }

  getSpreadsheetHtml(fileId: number, sheet?: string): Observable<{ sheets: string[]; activeSheet: string; html: string; rows: string[][] }> {
    let params = new HttpParams();
    if (sheet) params = params.set('sheet', sheet);
    return this.http.get<{ sheets: string[]; activeSheet: string; html: string; rows: string[][] }>(`${this.baseUrl}/spreadsheet/${fileId}`, { params });
  }

  updateSpreadsheet(fileId: number, rows: string[][], sheet: string, editedBy: string): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.baseUrl}/spreadsheet/${fileId}`, { rows, sheet, editedBy });
  }

  createFolder(folderPath: string): Observable<{ message: string; path: string }> {
    return this.http.post<{ message: string; path: string }>(`${this.baseUrl}/folder`, { folderPath });
  }

  getFileContent(fileId: number): Observable<string> {
    return this.http.get(`${this.baseUrl}/content/${fileId}`, { responseType: 'text' });
  }

  updateFileContent(fileId: number, content: string, editedBy: string): Observable<{ message: string }> {
    return this.http.put<{ message: string }>(`${this.baseUrl}/content/${fileId}`, { content, editedBy });
  }

  replaceFile(fileId: number, file: File, editedBy: string): Observable<EcmFileDto> {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('editedBy', editedBy);
    return this.http.put<EcmFileDto>(`${this.baseUrl}/files/${fileId}/replace`, formData);
  }
}
