import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PublicAuthService {

  private readonly TIMEOUT_MS = (environment.sessionTimeoutMinutes || 15) * 60 * 1000;
  private readonly SESSION_KEY = 'cms_public_session';
  private readonly ACTIVITY_KEY = 'cms_public_last_activity';
  private activityListeners: (() => void)[] = [];
  private countdownTimer: any = null;

  isAuthenticated = signal(false);
  remainingSeconds = signal(0);
  userIdentifier = signal('');

  constructor() {
    this.restoreSession();
  }

  login(identifier: string, token: string) {
    const session = { identifier, token, startedAt: Date.now() };
    sessionStorage.setItem(this.SESSION_KEY, JSON.stringify(session));
    this.updateActivity();
    this.userIdentifier.set(identifier);
    this.isAuthenticated.set(true);
    this.registerActivityListeners();
    this.startCountdown();
  }

  logout() {
    sessionStorage.removeItem(this.SESSION_KEY);
    sessionStorage.removeItem(this.ACTIVITY_KEY);
    this.isAuthenticated.set(false);
    this.userIdentifier.set('');
    this.remainingSeconds.set(0);
    this.stopCountdown();
    this.removeActivityListeners();
  }

  getToken(): string | null {
    const raw = sessionStorage.getItem(this.SESSION_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw).token;
    } catch {
      return null;
    }
  }

  isSessionValid(): boolean {
    const raw = sessionStorage.getItem(this.SESSION_KEY);
    if (!raw) return false;
    return !this.isExpired();
  }

  getFormattedTime(): string {
    const secs = this.remainingSeconds();
    const m = Math.floor(secs / 60);
    const s = secs % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  }

  private isExpired(): boolean {
    const lastActivity = sessionStorage.getItem(this.ACTIVITY_KEY);
    if (!lastActivity) return true;
    return Date.now() - parseInt(lastActivity, 10) > this.TIMEOUT_MS;
  }

  private updateActivity() {
    sessionStorage.setItem(this.ACTIVITY_KEY, String(Date.now()));
  }

  private restoreSession() {
    const raw = sessionStorage.getItem(this.SESSION_KEY);
    if (raw && !this.isExpired()) {
      try {
        const session = JSON.parse(raw);
        this.userIdentifier.set(session.identifier || '');
        this.isAuthenticated.set(true);
        this.registerActivityListeners();
        this.startCountdown();
      } catch {
        this.logout();
      }
    } else if (raw) {
      this.logout();
    }
  }

  private startCountdown() {
    this.stopCountdown();
    this.updateRemainingSeconds();
    this.countdownTimer = setInterval(() => {
      this.updateRemainingSeconds();
      if (this.remainingSeconds() <= 0) {
        this.logout();
      }
    }, 1000);
  }

  private stopCountdown() {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = null;
    }
  }

  private updateRemainingSeconds() {
    const lastActivity = sessionStorage.getItem(this.ACTIVITY_KEY);
    if (!lastActivity) {
      this.remainingSeconds.set(0);
      return;
    }
    const elapsed = Date.now() - parseInt(lastActivity, 10);
    this.remainingSeconds.set(Math.max(0, Math.floor((this.TIMEOUT_MS - elapsed) / 1000)));
  }

  private registerActivityListeners() {
    const events = ['mousedown', 'keydown', 'scroll', 'touchstart'];
    const handler = () => this.updateActivity();
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
