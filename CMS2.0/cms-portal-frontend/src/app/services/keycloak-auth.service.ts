import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';

export interface StaffUser {
  id: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  roles: string[];
  department: 'RBIO' | 'CEPC' | 'CRPC' | 'ADMIN' | 'UNKNOWN';
}

@Injectable({ providedIn: 'root' })
export class KeycloakAuthService {

  private keycloak: Keycloak;
  private initialized = false;
  private initPromise: Promise<boolean> | null = null;

  isAuthenticated = signal(false);
  currentUser = signal<StaffUser | null>(null);
  token = signal<string>('');

  constructor() {
    this.keycloak = new Keycloak({
      url: environment.keycloakUrl,
      realm: environment.realm,
      clientId: 'cms-frontend'
    });
  }

  async init(): Promise<boolean> {
    if (this.initialized) {
      return this.isAuthenticated();
    }

    if (this.initPromise) {
      return this.initPromise;
    }

    this.initPromise = this.doInit();
    return this.initPromise;
  }

  private async doInit(): Promise<boolean> {
    try {
      const authenticated = await this.keycloak.init({
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
        redirectUri: window.location.origin + '/staff/dashboard'
      });

      this.initialized = true;

      if (authenticated) {
        this.updateAuthState();
        this.setupTokenRefresh();
      }
      return authenticated;
    } catch (err) {
      console.error('Keycloak init failed:', err);
      this.initialized = true;
      return false;
    }
  }

  async login(): Promise<void> {
    if (!this.initialized) {
      await this.init();
    }
    await this.keycloak.login({
      redirectUri: window.location.origin + '/staff/dashboard'
    });
  }

  async loginWithRedirect(redirectUri: string): Promise<void> {
    if (!this.initialized) {
      await this.init();
    }
    await this.keycloak.login({ redirectUri });
  }

  async logout(): Promise<void> {
    this.isAuthenticated.set(false);
    this.currentUser.set(null);
    this.token.set('');

    const wasInitialized = this.initialized;
    this.initialized = false;
    this.initPromise = null;

    if (wasInitialized && this.keycloak.authenticated) {
      await this.keycloak.logout({
        redirectUri: window.location.origin + '/staff/login'
      });
    } else {
      window.location.href = window.location.origin + '/staff/login';
    }
  }

  async refreshToken(): Promise<boolean> {
    try {
      const refreshed = await this.keycloak.updateToken(30);
      if (refreshed) {
        this.token.set(this.keycloak.token || '');
      }
      return true;
    } catch {
      this.isAuthenticated.set(false);
      return false;
    }
  }

  getToken(): string {
    return this.keycloak.token || '';
  }

  getRoles(): string[] {
    return this.keycloak.realmAccess?.roles || [];
  }

  hasRole(role: string): boolean {
    return this.getRoles().includes(role);
  }

  hasAnyRole(roles: string[]): boolean {
    return roles.some(r => this.hasRole(r));
  }

  private updateAuthState(): void {
    this.isAuthenticated.set(true);
    this.token.set(this.keycloak.token || '');

    const tokenParsed = this.keycloak.tokenParsed as any;
    const roles = this.getRoles();

    this.currentUser.set({
      id: tokenParsed?.sub || '',
      username: tokenParsed?.preferred_username || '',
      firstName: tokenParsed?.given_name || '',
      lastName: tokenParsed?.family_name || '',
      email: tokenParsed?.email || '',
      roles,
      department: this.detectDepartment(roles)
    });
  }

  private setupTokenRefresh(): void {
    setInterval(async () => {
      if (this.isAuthenticated()) {
        await this.refreshToken();
      }
    }, 60000);
  }

  private detectDepartment(roles: string[]): StaffUser['department'] {
    if (roles.some(r => r.startsWith('RBIO_'))) return 'RBIO';
    if (roles.some(r => r.startsWith('CEPC_'))) return 'CEPC';
    if (roles.some(r => r.startsWith('CRPC_') || r === 'DEO' || r === 'REVIEWER')) return 'CRPC';
    if (roles.includes('ADMIN')) return 'ADMIN';
    return 'UNKNOWN';
  }
}
