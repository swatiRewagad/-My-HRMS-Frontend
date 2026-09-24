import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface UploadLimits {
  maxFileSizeBytes: number;
  maxTotalSizeBytes: number;
  maxFileSizeMb: number;
  maxTotalSizeMb: number;
  maxFileCount: number;
}

/**
 * Attachment limits, fetched from the server rather than compiled in (13.1).
 *
 * The ruling is that CONFIGURATION is authoritative and the figure is expected to move; it currently
 * stands at 2 MB per file and 25 MB per set. A constant in the bundle cannot track that, and when the
 * limit lived in `environment.maxFileSizeMB` the browser and the server disagreed in both directions:
 * files the API would have taken were refused, and files it refuses were offered.
 *
 * THIS IS THE ONLY PLACE IN THE FRONTEND ALLOWED TO NAME A SIZE, and only as the fallback for a failed
 * fetch. It must equal the server's own default in UploadLimitsService, or a citizen whose
 * /api/v1/config/upload-limits call fails is shown a limit /api/files/upload does not enforce.
 */
@Injectable({ providedIn: 'root' })
export class UploadLimitsService {
  private http = inject(HttpClient);

  private readonly fallback: UploadLimits = {
    maxFileSizeBytes: 2 * 1024 * 1024,
    maxTotalSizeBytes: 25 * 1024 * 1024,
    maxFileSizeMb: 2,
    maxTotalSizeMb: 25,
    maxFileCount: 10,
  };

  private readonly _limits = signal<UploadLimits>(this.fallback);
  readonly limits = this._limits.asReadonly();

  constructor() {
    this.load();
  }

  load(): void {
    this.http.get<UploadLimits>(`${environment.apiBaseUrl}/api/v1/config/upload-limits`).subscribe({
      next: limits => {
        if (limits && typeof limits.maxFileSizeBytes === 'number') {
          this._limits.set(limits);
        }
      },
      // Keeping the fallback is the right failure mode: it equals the server default, so a fetch
      // failure degrades to the same rule /api/files/upload enforces rather than to a guess.
      error: () => {},
    });
  }

  maxFileSizeBytes(): number {
    return this._limits().maxFileSizeBytes;
  }

  maxFileSizeMb(): number {
    return this._limits().maxFileSizeMb;
  }

  maxTotalSizeBytes(): number {
    return this._limits().maxTotalSizeBytes;
  }

  maxTotalSizeMb(): number {
    return this._limits().maxTotalSizeMb;
  }

  maxFileCount(): number {
    return this._limits().maxFileCount;
  }
}
