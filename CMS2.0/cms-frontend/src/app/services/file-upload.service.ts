import { Injectable, signal, inject } from '@angular/core';
import { HttpClient, HttpEventType, HttpRequest } from '@angular/common/http';
import { Observable, Subject, from, concatMap, tap, last, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { InputSanitizerService } from './input-sanitizer.service';

export interface UploadProgress {
  fileName: string;
  uploadId: string;
  progress: number;       // 0-100
  chunksUploaded: number;
  totalChunks: number;
  status: 'pending' | 'uploading' | 'assembling' | 'complete' | 'error';
  error?: string;
  attachmentId?: number;
}

const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB — matches backend default

@Injectable({ providedIn: 'root' })
export class FileUploadService {
  private api = environment.apiUrl;
  private sanitizer = inject(InputSanitizerService);

  activeUploads = signal<UploadProgress[]>([]);

  constructor(private http: HttpClient) {}

  uploadFile(
    file: File,
    complaintNumber: string,
    complaintId: number
  ): Observable<UploadProgress> {
    const sanitizedComplaintNumber = this.sanitizer.sanitizeComplaintNumber(complaintNumber);

    if (!sanitizedComplaintNumber || complaintId <= 0) {
      const err$ = new Subject<UploadProgress>();
      err$.next({
        fileName: file.name,
        uploadId: crypto.randomUUID(),
        progress: 0, chunksUploaded: 0, totalChunks: 0,
        status: 'error', error: 'Invalid complaint reference',
      });
      err$.complete();
      return err$.asObservable();
    }

    const totalChunks = Math.ceil(file.size / CHUNK_SIZE);

    if (totalChunks <= 1) {
      return this.singleUpload(file, sanitizedComplaintNumber, complaintId);
    }

    return this.chunkedUpload(file, sanitizedComplaintNumber, complaintId, totalChunks);
  }

  private singleUpload(
    file: File,
    complaintNumber: string,
    complaintId: number
  ): Observable<UploadProgress> {
    const progress$ = new Subject<UploadProgress>();
    const uploadId = crypto.randomUUID();

    const state: UploadProgress = {
      fileName: file.name,
      uploadId,
      progress: 0,
      chunksUploaded: 0,
      totalChunks: 1,
      status: 'uploading',
    };

    this.addUpload(state);

    const sanitizedFileName = this.sanitizer.sanitizeFileName(file.name);
    const safeFile = new File([file], sanitizedFileName, { type: file.type });

    const formData = new FormData();
    formData.append('file', safeFile);
    formData.append('complaintNumber', complaintNumber);
    formData.append('complaintId', complaintId.toString());

    const req = new HttpRequest('POST', `${this.api}/files/upload`, formData, {
      reportProgress: true,
    });

    this.http.request(req).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          state.progress = Math.round((event.loaded / event.total) * 100);
          this.updateUpload(state);
          progress$.next({ ...state });
        } else if (event.type === HttpEventType.Response) {
          const body = event.body as any;
          state.status = 'complete';
          state.progress = 100;
          state.chunksUploaded = 1;
          state.attachmentId = body?.id;
          this.updateUpload(state);
          progress$.next({ ...state });
          progress$.complete();
        }
      },
      error: (err) => {
        state.status = 'error';
        state.error = err?.error?.message || err?.message || 'Upload failed';
        this.updateUpload(state);
        progress$.next({ ...state });
        progress$.complete();
      },
    });

    return progress$.asObservable();
  }

  private chunkedUpload(
    file: File,
    complaintNumber: string,
    complaintId: number,
    totalChunks: number
  ): Observable<UploadProgress> {
    const progress$ = new Subject<UploadProgress>();
    const uploadId = crypto.randomUUID();

    const state: UploadProgress = {
      fileName: file.name,
      uploadId,
      progress: 0,
      chunksUploaded: 0,
      totalChunks,
      status: 'uploading',
    };

    this.addUpload(state);

    const chunks: { index: number; blob: Blob }[] = [];
    for (let i = 0; i < totalChunks; i++) {
      const start = i * CHUNK_SIZE;
      const end = Math.min(start + CHUNK_SIZE, file.size);
      chunks.push({ index: i, blob: file.slice(start, end) });
    }

    const sanitizedFileName = this.sanitizer.sanitizeFileName(file.name);

    from(chunks)
      .pipe(
        concatMap((chunk) => {
          const formData = new FormData();
          formData.append('file', chunk.blob, `chunk_${chunk.index}`);
          formData.append('uploadId', uploadId);
          formData.append('chunkIndex', chunk.index.toString());
          formData.append('totalChunks', totalChunks.toString());
          formData.append('fileName', sanitizedFileName);
          formData.append('complaintNumber', complaintNumber);
          formData.append('complaintId', complaintId.toString());
          formData.append('totalFileSize', file.size.toString());

          return this.http.post<any>(`${this.api}/files/upload/chunk`, formData).pipe(
            tap((res) => {
              state.chunksUploaded = chunk.index + 1;
              state.progress = Math.round(((chunk.index + 1) / totalChunks) * 95);

              if (res.complete) {
                state.status = 'complete';
                state.progress = 100;
                state.attachmentId = res.attachmentId;
              } else if (chunk.index === totalChunks - 2) {
                state.status = 'assembling';
              }

              this.updateUpload(state);
              progress$.next({ ...state });
            })
          );
        }),
        last()
      )
      .subscribe({
        complete: () => {
          if (state.status !== 'complete') {
            state.status = 'complete';
            state.progress = 100;
            this.updateUpload(state);
            progress$.next({ ...state });
          }
          progress$.complete();
        },
        error: (err) => {
          state.status = 'error';
          state.error = err?.error?.message || err?.message || 'Chunk upload failed';
          this.updateUpload(state);
          progress$.next({ ...state });
          progress$.complete();
        },
      });

    return progress$.asObservable();
  }

  cancelUpload(uploadId: string) {
    this.activeUploads.update((uploads) =>
      uploads.filter((u) => u.uploadId !== uploadId)
    );
  }

  clearCompleted() {
    this.activeUploads.update((uploads) =>
      uploads.filter((u) => u.status !== 'complete' && u.status !== 'error')
    );
  }

  getAttachments(complaintId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.api}/files/complaint/${complaintId}`);
  }

  downloadAttachment(attachmentId: number): Observable<Blob> {
    return this.http.get(`${this.api}/files/download/${attachmentId}`, {
      responseType: 'blob',
    });
  }

  getStreamUrl(attachmentId: number): string {
    return `${this.api}/files/stream/${attachmentId}`;
  }

  deleteAttachment(attachmentId: number): Observable<void> {
    return this.http.delete<void>(`${this.api}/files/${attachmentId}`);
  }

  private addUpload(state: UploadProgress) {
    this.activeUploads.update((list) => [...list, { ...state }]);
  }

  private updateUpload(state: UploadProgress) {
    this.activeUploads.update((list) =>
      list.map((u) => (u.uploadId === state.uploadId ? { ...state } : u))
    );
  }
}
