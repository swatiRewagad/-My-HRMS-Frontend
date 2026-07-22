import { Injectable } from '@angular/core';
import { environment } from '../../environments/environment';

export interface RuntimeConfig {
  apiBaseUrl: string;
  keycloakUrl: string;
  keycloakRealm: string;
  keycloakClientId: string;
  production: boolean;
}

@Injectable({ providedIn: 'root' })
export class RuntimeConfigService {

  private config: RuntimeConfig = {
    apiBaseUrl: environment.apiBaseUrl,
    keycloakUrl: environment.keycloakUrl,
    keycloakRealm: environment.realm,
    keycloakClientId: 'cms-frontend',
    production: environment.production
  };

  get apiBaseUrl(): string { return this.config.apiBaseUrl; }
  get keycloakUrl(): string { return this.config.keycloakUrl; }
  get keycloakRealm(): string { return this.config.keycloakRealm; }
  get keycloakClientId(): string { return this.config.keycloakClientId; }

  async load(): Promise<void> {
    if (!environment.production) return;

    try {
      const response = await fetch('/assets/config.json');
      if (response.ok) {
        const json = await response.json();
        this.config = {
          apiBaseUrl: json.apiBaseUrl || this.config.apiBaseUrl,
          keycloakUrl: json.keycloakUrl || this.config.keycloakUrl,
          keycloakRealm: json.keycloakRealm || this.config.keycloakRealm,
          keycloakClientId: json.keycloakClientId || this.config.keycloakClientId,
          production: true
        };
        (environment as any).apiBaseUrl = this.config.apiBaseUrl;
        (environment as any).keycloakUrl = this.config.keycloakUrl;
        (environment as any).realm = this.config.keycloakRealm;
      }
    } catch {
      // In dev mode or if config.json doesn't exist, fall back to environment.ts
    }
  }
}
