import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { environment } from '../../environments/environment';
import { Realm, ExistingConfig, SchemaConfig } from '../models/realm.model';
import { REALMS, EXISTING_CONFIGS } from '../data/realms.data';

@Injectable({ providedIn: 'root' })
export class RealmConfigService {
  private apiUrl = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getRealms(): Observable<Realm[]> {
    return of(REALMS);
  }

  getExistingConfig(realmId: string): ExistingConfig | null {
    return EXISTING_CONFIGS[realmId] ?? null;
  }

  saveConfiguration(payload: {
    realmId: string;
    mode: string;
    platformVersion: string;
    services: string[];
    subServices: Record<string, string[]>;
    deploymentType: string;
    deploySchema: SchemaConfig | null;
    serviceConfigs: Record<string, SchemaConfig>;
  }): Observable<{ success: boolean }> {
    return this.http.post<{ success: boolean }>(
      `${this.apiUrl}/realm-config`,
      payload
    );
  }
}
