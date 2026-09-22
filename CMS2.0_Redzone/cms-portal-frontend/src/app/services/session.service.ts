import { Injectable, signal } from '@angular/core';

/**
 * NFR-005: Configurable inactivity timeout (default 15 minutes).
 * Tracks user activity and auto-expires session.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {

  private readonly TIMEOUT_MS = 15 * 60 * 1000; // 15 minutes
  private readonly STORAGE_KEY = 'cms_session';
  private readonly LAST_ACTIVITY_KEY = 'cms_last_activity';
  private activityListeners: (() => void)[] = [];

  isActive = signal(false);

  constructor() {
    this.checkExistingSession();
  }

  startSession(token: string) {
    const session = { token, startedAt: Date.now() };
    sessionStorage.setItem(this.STORAGE_KEY, JSON.stringify(session));
    this.updateLastActivity();
    this.isActive.set(true);
    this.registerActivityListeners();
  }

  getToken(): string | null {
    const raw = sessionStorage.getItem(this.STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw).token;
    } catch {
      return null;
    }
  }

  isExpired(): boolean {
    const lastActivity = sessionStorage.getItem(this.LAST_ACTIVITY_KEY);
    if (!lastActivity) return true;
    return Date.now() - parseInt(lastActivity, 10) > this.TIMEOUT_MS;
  }

  resetTimer() {
    this.updateLastActivity();
  }

  clearSession() {
    sessionStorage.removeItem(this.STORAGE_KEY);
    sessionStorage.removeItem(this.LAST_ACTIVITY_KEY);
    this.isActive.set(false);
    this.removeActivityListeners();
  }

  getRemainingSeconds(): number {
    const lastActivity = sessionStorage.getItem(this.LAST_ACTIVITY_KEY);
    if (!lastActivity) return 0;
    const elapsed = Date.now() - parseInt(lastActivity, 10);
    return Math.max(0, Math.floor((this.TIMEOUT_MS - elapsed) / 1000));
  }

  private updateLastActivity() {
    sessionStorage.setItem(this.LAST_ACTIVITY_KEY, String(Date.now()));
  }

  private checkExistingSession() {
    const raw = sessionStorage.getItem(this.STORAGE_KEY);
    if (raw && !this.isExpired()) {
      this.isActive.set(true);
      this.registerActivityListeners();
    } else if (raw) {
      this.clearSession();
    }
  }

  private registerActivityListeners() {
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    const handler = () => this.updateLastActivity();
    events.forEach(e => {
      document.addEventListener(e, handler, { passive: true });
      this.activityListeners.push(() => document.removeEventListener(e, handler));
    });
  }

  private removeActivityListeners() {
    this.activityListeners.forEach(fn => fn());
    this.activityListeners = [];
  }
}
