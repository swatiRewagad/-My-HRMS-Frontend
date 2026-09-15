import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';

export interface GeoJurisdiction {
  resolved: boolean;
  state?: string;
  city?: string;
  ombudsmanOffice?: string;
}

@Injectable({ providedIn: 'root' })
export class GeoLocationService {

  private http = inject(HttpClient);

  private _jurisdiction = signal<GeoJurisdiction>({ resolved: false });
  private _loaded = signal(false);

  readonly jurisdiction = this._jurisdiction.asReadonly();
  readonly loaded = this._loaded.asReadonly();

  detectLocation(): void {
    if (this._loaded()) return;

    this.http.get<GeoJurisdiction>(`${environment.apiBaseUrl}/api/v1/geo/jurisdiction`)
      .subscribe({
        next: (result) => {
          this._jurisdiction.set(result);
          this._loaded.set(true);
        },
        error: () => {
          this._jurisdiction.set({ resolved: false });
          this._loaded.set(true);
        }
      });
  }

  getState(): string | undefined {
    return this._jurisdiction().state;
  }

  getOmbudsmanOffice(): string | undefined {
    return this._jurisdiction().ombudsmanOffice;
  }
}
