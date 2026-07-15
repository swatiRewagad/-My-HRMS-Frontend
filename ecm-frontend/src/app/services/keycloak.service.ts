import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private _selectedRealm: string = '';
  private _token: string = '';
  private _refreshToken: string = '';
  private _tokenParsed: any = null;
  private _tokenExpiry = 0;
  private keycloakUrl = 'http://localhost:9091';
  private clientId = 'ecm-frontend';

  constructor() {
    const saved = sessionStorage.getItem('ecm_selected_realm');
    if (saved) {
      this._selectedRealm = JSON.parse(saved).name;
    }
    const tokenData = sessionStorage.getItem('ecm_token_data');
    if (tokenData) {
      const parsed = JSON.parse(tokenData);
      this._token = parsed.access_token;
      this._refreshToken = parsed.refresh_token;
      this._tokenExpiry = parsed.expiry;
      this._tokenParsed = this.decodeToken(this._token);
    }
  }

  get selectedRealm(): string {
    return this._selectedRealm;
  }

  get hasRealmSelected(): boolean {
    return !!this._selectedRealm;
  }

  get isAuthenticated(): boolean {
    return !!this._token && !!this._tokenParsed;
  }

  get token(): string | undefined {
    return this._token || undefined;
  }

  get tokenParsed(): any {
    return this._tokenParsed;
  }

  get username(): string {
    return this._tokenParsed?.['preferred_username'] ?? '';
  }

  get fullName(): string {
    const p = this._tokenParsed;
    return p ? `${p['given_name'] ?? ''} ${p['family_name'] ?? ''}`.trim() : '';
  }

  get email(): string {
    return this._tokenParsed?.['email'] ?? '';
  }

  get roles(): string[] {
    return this._tokenParsed?.['realm_access']?.['roles'] ?? [];
  }

  hasRole(role: string): boolean {
    return this.roles.includes(role);
  }

  get isAdmin(): boolean {
    return this.hasRole('ecm_admin');
  }

  async init(): Promise<boolean> {
    if (!this._selectedRealm) return false;
    if (this._token) {
      await this.updateToken(30);
      return true;
    }
    return false;
  }

  async directLogin(username: string, password: string): Promise<void> {
    const url = `${this.keycloakUrl}/realms/${this._selectedRealm}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'password',
      client_id: this.clientId,
      username,
      password,
    });

    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });

    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}));
      throw new Error(err.error_description || 'Invalid credentials');
    }

    const data = await resp.json();
    this.storeTokens(data);
  }

  async updateToken(minValidity = 30): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    if (this._tokenExpiry - now > minValidity) {
      return this._token;
    }
    if (!this._refreshToken) throw new Error('No refresh token');

    const url = `${this.keycloakUrl}/realms/${this._selectedRealm}/protocol/openid-connect/token`;
    const body = new URLSearchParams({
      grant_type: 'refresh_token',
      client_id: this.clientId,
      refresh_token: this._refreshToken,
    });

    const resp = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    });

    if (!resp.ok) {
      this.clearSession();
      window.location.href = '/realm-select';
      throw new Error('Session expired');
    }

    const data = await resp.json();
    this.storeTokens(data);
    return this._token;
  }

  switchRealm(realmName: string) {
    this._selectedRealm = realmName;
  }

  logout(): void {
    const logoutUrl = `${this.keycloakUrl}/realms/${this._selectedRealm}/protocol/openid-connect/logout`;
    const body = new URLSearchParams({
      client_id: this.clientId,
      refresh_token: this._refreshToken,
    });
    fetch(logoutUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: body.toString(),
    }).catch(() => {});
    this.clearSession();
    window.location.href = '/realm-select';
  }

  private storeTokens(data: any) {
    this._token = data.access_token;
    this._refreshToken = data.refresh_token;
    this._tokenParsed = this.decodeToken(data.access_token);
    this._tokenExpiry = Math.floor(Date.now() / 1000) + data.expires_in;
    sessionStorage.setItem('ecm_token_data', JSON.stringify({
      access_token: data.access_token,
      refresh_token: data.refresh_token,
      expiry: this._tokenExpiry,
    }));
  }

  private clearSession() {
    sessionStorage.removeItem('ecm_selected_realm');
    sessionStorage.removeItem('ecm_token_data');
    this._token = '';
    this._refreshToken = '';
    this._tokenParsed = null;
    this._tokenExpiry = 0;
  }

  private decodeToken(token: string): any {
    try {
      const payload = token.split('.')[1];
      return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    } catch {
      return null;
    }
  }
}
