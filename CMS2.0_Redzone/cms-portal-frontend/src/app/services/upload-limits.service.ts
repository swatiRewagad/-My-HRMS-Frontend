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
 * `environment.maxFileSizeMB` said 2 in all three environment files while the server's own hint text
 * promised 5 MB in ten locales. A constant in the bundle cannot track a configuration change, so the
 * two were guaranteed to drift; this asks the server what the limit is.
 *
 * The fallback matches the server's own default so a failed fetch degrades to the correct product rule
 * rather than to the stale 2 MB.
 */
@Injectable({ providedIn: 'root' })
export class UploadLimitsService {
  private http = inject(HttpClient);

  private readonly fallback: UploadLimits = {
    maxFileSizeBytes: 5 * 1024 * 1024,
    maxTotalSizeBytes: 25 * 1024 * 1024,
    maxFileSizeMb: 5,
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
      // Keeping the product default is the right failure mode; showing 2 MB would recreate the
      // contradiction this service exists to remove.
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
