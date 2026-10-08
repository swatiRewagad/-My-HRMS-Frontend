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
  department: 'RBIO' | 'CEPC' | 'CRPC' | 'AA' | 'RE' | 'ADMIN' | 'UNKNOWN';
  /**
   * The AA reviewer's tier, straight from the `reviewer_tier` realm claim. Absent for everyone else.
   *
   * This is declared because a consumer was already reading it and could never have found it:
   * aa-appeal-detail.component.ts casts `currentUser()` to `Record<string, unknown>` and looks up
   * `reviewer_tier`, but the object this service builds is a fixed StaffUser literal that never copied
   * the claim across. So the lookup was permanently `undefined`, the `@if (reviewerTier(); as tier)`
   * never opened, and the tier badge could not render for ANY reviewer — even though Keycloak does
   * issue the claim (verified: aa_reviewer_001 => 1, aa_reviewer_002 => 2). The cast made it a silent
   * miss rather than a compile error, which is why it survived.
   *
   * Typed as string because that is what the badge renders and what the detail component coerces to;
   * Keycloak may hand it over as either a string or a number.
   */
  reviewerTier?: string;
}

/**
 * Mirrors app.routes.ts's CEPC_ROLES (minus ADMIN, handled separately above). Kept in sync by hand:
 * getPostLoginRoute() must only aim a login at /cepc for roles the staffRoleGuard there will actually
 * admit, otherwise the user burns a redirect + a failed guard check before landing on /staff/unauthorized.
 */
const CEPC_ADMITTED_ROLES = ['CEPC_DO', 'CEPC_REVIEWER', 'CEPC_INCHARGE', 'CEPC_CLOSING_AUTHORITY', 'CEPC_ADMIN'];

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
        silentCheckSsoRedirectUri: window.location.origin + '/assets/silent-check-sso.html'
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
      redirectUri: window.location.href
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
    if (this.warningTimer) clearInterval(this.warningTimer);
    if (this.sessionTimer) clearTimeout(this.sessionTimer);

    try {
      await this.keycloak.logout({
        redirectUri: window.location.origin
      });
    } catch {
      window.location.href = '/';
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

  getPostLoginRoute(): string[] {
    const dept = this.currentUser()?.department?.toUpperCase();
    const roles = this.getRoles();

    if (roles.includes('ADMIN')) return ['/admin/dashboard'];
    if (dept === 'CRPC' || roles.includes('DEO') || roles.includes('REVIEWER') || roles.includes('CRPC_HEAD')) return ['/crpc/home'];
    if (dept === 'RBIO' || roles.some(r => r.startsWith('RBIO_'))) return ['/staff/rbio/tasks'];
    if (roles.some(r => CEPC_ADMITTED_ROLES.includes(r))) return ['/cepc'];
    if (dept === 'AA' || roles.some(r => r.startsWith('AA_'))) return ['/aa/dashboard'];
    if (dept === 'RE' || roles.some(r => r.startsWith('RE_'))) return ['/re-portal/dashboard'];
    return ['/staff/dashboard'];
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
      department: this.detectDepartment(roles),
      // Carried across from the token rather than dropped. See StaffUser.reviewerTier: the AA detail
      // screen was already reading `reviewer_tier` off this object, so without this line the tier
      // badge was unreachable for every reviewer. Left undefined when the claim is absent, which is
      // the correct state for non-reviewers and is what the badge's @if tests for.
      reviewerTier: tokenParsed?.reviewer_tier == null
        ? undefined
        : String(tokenParsed.reviewer_tier)
    });
  }

  private sessionTimer: any;
  private warningTimer: any;
  private activityDebounce: any;
  sessionExpiring = signal(false);
  sessionRemainingSeconds = signal(0);

  private setupTokenRefresh(): void {
    setInterval(async () => {
      if (this.isAuthenticated()) {
        await this.refreshToken();
      }
    }, 60000);

    this.startSessionTimer();
    this.setupActivityListeners();
  }

  private setupActivityListeners(): void {
    const onActivity = () => {
      if (this.sessionExpiring()) return;
      if (this.activityDebounce) clearTimeout(this.activityDebounce);
      this.activityDebounce = setTimeout(() => this.resetSessionTimer(), 5000);
    };
    ['click', 'keydown', 'mousemove', 'scroll'].forEach(event => {
      document.addEventListener(event, onActivity, { passive: true });
    });
  }

  private resetSessionTimer(): void {
    if (this.warningTimer) clearInterval(this.warningTimer);
    if (this.sessionTimer) clearTimeout(this.sessionTimer);
    this.startSessionTimer();
  }

  private startSessionTimer(): void {
    if (this.warningTimer) clearInterval(this.warningTimer);
    if (this.sessionTimer) clearTimeout(this.sessionTimer);

    const timeoutMs = environment.sessionTimeoutMinutes * 60 * 1000;
    const warningMs = timeoutMs - 60000;

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
    if (roles.some(r => r.startsWith('AA_'))) return 'AA';
    if (roles.some(r => r.startsWith('RE_'))) return 'RE';
    if (roles.includes('ADMIN')) return 'ADMIN';
    return 'UNKNOWN';
  }
}
