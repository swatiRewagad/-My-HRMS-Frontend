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

    window.addEventListener('beforeunload', () => {
      if (this.isAuthenticated() && this.keycloak.idToken) {
        sessionStorage.removeItem('crpc_user');
        sessionStorage.setItem('cms_session_ended', 'true');
        const logoutUrl = `${environment.keycloakUrl}/realms/${environment.realm}/protocol/openid-connect/logout?id_token_hint=${this.keycloak.idToken}&post_logout_redirect_uri=${encodeURIComponent(window.location.origin)}`;
        navigator.sendBeacon(logoutUrl);
      }
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
      if (sessionStorage.getItem('cms_session_ended')) {
        sessionStorage.removeItem('cms_session_ended');
      }

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
    this.initialized = false;
    this.initPromise = null;
    sessionStorage.removeItem('crpc_user');

    try {
      await this.keycloak.logout({
        redirectUri: window.location.origin + '/crpc/login'
      });
    } catch {
      window.location.href = '/crpc/login';
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

  private sessionTimer: any;
  private warningTimer: any;
  sessionExpiring = signal(false);
  sessionRemainingSeconds = signal(0);

  private setupTokenRefresh(): void {
    setInterval(async () => {
      if (this.isAuthenticated()) {
        await this.refreshToken();
      }
    }, 60000);

    this.startSessionTimer();
  }

  private startSessionTimer(): void {
    if (this.warningTimer) clearInterval(this.warningTimer);
    if (this.sessionTimer) clearTimeout(this.sessionTimer);

    const timeoutMs = environment.sessionTimeoutMinutes * 60 * 1000;
    const warningMs = timeoutMs - 60000; // Show warning 60s before expiry

    this.sessionExpiring.set(false);

    this.sessionTimer = setTimeout(() => {
      this.sessionExpiring.set(true);
      this.sessionRemainingSeconds.set(60);
      this.warningTimer = setInterval(() => {
        const remaining = this.sessionRemainingSeconds() - 1;
        this.sessionRemainingSeconds.set(remaining);
        if (remaining <= 0) {
          clearInterval(this.warningTimer);
          this.logout();
        }
      }, 1000);
    }, warningMs);

    // Reset timer on user activity
    const resetActivity = () => {
      if (!this.sessionExpiring()) {
        this.startSessionTimer();
      }
    };
    ['click', 'keydown', 'mousemove', 'scroll'].forEach(event => {
      document.removeEventListener(event, resetActivity);
      document.addEventListener(event, resetActivity, { once: true });
    });
  }

  extendSession(): void {
    this.sessionExpiring.set(false);
    if (this.warningTimer) clearInterval(this.warningTimer);
    this.refreshToken();
    this.startSessionTimer();
  }

  private detectDepartment(roles: string[]): StaffUser['department'] {
    if (roles.some(r => r.startsWith('RBIO_'))) return 'RBIO';
    if (roles.some(r => r.startsWith('CEPC_'))) return 'CEPC';
    if (roles.some(r => r.startsWith('CRPC_') || r === 'DEO' || r === 'REVIEWER')) return 'CRPC';
    if (roles.includes('ADMIN')) return 'ADMIN';
    return 'UNKNOWN';
  }
}
