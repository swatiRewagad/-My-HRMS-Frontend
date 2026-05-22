import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { Realm, ExistingConfig } from '../models/realm.model';
import { REALMS, EXISTING_CONFIGS } from '../data/realms.data';
import { ALL_SERVICES } from '../data/services.data';

export interface RealmUser {
  realm: Realm;
  config: ExistingConfig;
  userName: string;
  role: 'admin' | 'user';
  loginTime: string;
}

const REALM_CREDENTIALS: Record<string, { password: string; users: { name: string; role: 'admin' | 'user' }[] }> = {
  ngcb: { password: 'ngcb@123', users: [{ name: 'Anita Kumar', role: 'admin' }, { name: 'Suresh Mehta', role: 'user' }] },
  'central-fin-literacy': { password: 'cfl@123', users: [{ name: 'Rahul Jain', role: 'admin' }, { name: 'Priya Sharma', role: 'user' }] },
  pdm: { password: 'pdm@123', users: [{ name: 'Vikram Singh', role: 'admin' }] },
  niass: { password: 'niass@123', users: [{ name: 'Priya Nair', role: 'admin' }] },
  ips: { password: 'ips@123', users: [{ name: 'Amit Roy', role: 'admin' }] },
  bcms: { password: 'bcms@123', users: [{ name: 'Neha Gupta', role: 'admin' }] },
  master: { password: 'admin@123', users: [{ name: 'System Admin', role: 'admin' }] },
};

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly _currentUser = signal<RealmUser | null>(null);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoggedIn = computed(() => !!this._currentUser());
  readonly currentRealm = computed(() => this._currentUser()?.realm ?? null);
  readonly currentConfig = computed(() => this._currentUser()?.config ?? null);

  readonly configuredServices = computed(() => {
    const config = this._currentUser()?.config;
    if (!config) return [];
    const serviceLabels = config.services;
    return ALL_SERVICES.filter(s => serviceLabels.includes(s.label));
  });

  constructor(private router: Router) {
    this.restoreSession();
  }

  getConfiguredRealms(): Realm[] {
    return REALMS.filter(r => r.status === 'active' && !!EXISTING_CONFIGS[r.id]);
  }

  getRealmUsers(realmId: string): { name: string; role: 'admin' | 'user' }[] {
    return REALM_CREDENTIALS[realmId]?.users ?? [];
  }

  login(realmId: string, userName: string, password: string): { success: boolean; error?: string } {
    const realm = REALMS.find(r => r.id === realmId);
    if (!realm) return { success: false, error: 'Realm not found' };

    const config = EXISTING_CONFIGS[realmId];
    if (!config) return { success: false, error: 'Realm is not configured. Please configure it first.' };

    const creds = REALM_CREDENTIALS[realmId];
    if (!creds) return { success: false, error: 'No credentials found for this realm' };

    if (password !== creds.password) return { success: false, error: 'Invalid password' };

    const user = creds.users.find(u => u.name === userName);
    if (!user) return { success: false, error: 'User not found in this realm' };

    const now = new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    const loginTime = `${pad(now.getDate())}/${pad(now.getMonth() + 1)}/${now.getFullYear().toString().slice(-2)} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`;

    const realmUser: RealmUser = { realm, config, userName: user.name, role: user.role, loginTime };
    this._currentUser.set(realmUser);
    sessionStorage.setItem('realm_session', JSON.stringify({ realmId, userName: user.name, role: user.role, loginTime }));

    return { success: true };
  }

  logout(): void {
    this._currentUser.set(null);
    sessionStorage.removeItem('realm_session');
    this.router.navigate(['/login']);
  }

  getServiceConfig(serviceId: string): Record<string, string> {
    const config = this._currentUser()?.config;
    if (!config?.serviceDetails) return {};
    const result: Record<string, string> = {};
    for (const [key, val] of Object.entries(config.serviceDetails)) {
      if (key.startsWith(serviceId) || key.includes(serviceId)) {
        result[key] = val.schemaName || val.connectionString || '';
      }
    }
    return result;
  }

  private restoreSession(): void {
    const stored = sessionStorage.getItem('realm_session');
    if (!stored) return;
    try {
      const session = JSON.parse(stored);
      const realm = REALMS.find(r => r.id === session.realmId);
      const config = EXISTING_CONFIGS[session.realmId];
      if (realm && config) {
        this._currentUser.set({
          realm, config,
          userName: session.userName,
          role: session.role,
          loginTime: session.loginTime,
        });
      }
    } catch { /* ignore corrupt session */ }
  }
}
