import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class PublicAuthService {

  private readonly TIMEOUT_MS = (environment.sessionTimeoutMinutes || 15) * 60 * 1000;
  private readonly SESSION_KEY = 'cms_public_session';
  private readonly ACTIVITY_KEY = 'cms_public_last_activity';
  /**
   * "This browser had a citizen session that was not ended on purpose."
   *
   * In localStorage, deliberately, while the SESSION itself stays in sessionStorage. sessionStorage is
   * per-tab and is destroyed when the tab closes, so a marker kept there cannot survive the very events
   * the citizen most needs explaining — an accidentally closed tab, or a second tab that never had a
   * session of its own. In both cases the app would otherwise have no way to know a session ever
   * existed and would show a bare login form with no reason given.
   *
   * It holds no token and no identity, only the fact that a session was lost involuntarily, so widening
   * its scope to the origin costs nothing. It is cleared on a fresh login, on an explicit Logout, and
   * when the citizen dismisses the notice.
   */
  private readonly TIMED_OUT_KEY = 'cms_public_timed_out';
  private activityListeners: (() => void)[] = [];
  private countdownTimer: any = null;

  isAuthenticated = signal(false);
  remainingSeconds = signal(0);
  sessionWarning = signal(false);
  userIdentifier = signal('');
  /** True once a session was dropped for INACTIVITY, as opposed to the citizen pressing Logout. */
  timedOut = signal(false);

  constructor() {
    this.timedOut.set(localStorage.getItem(this.TIMED_OUT_KEY) === '1');
    this.restoreSession();
  }

  login(identifier: string, token: string) {
    this.clearTimedOut();
    const session = { identifier, token, startedAt: Date.now() };
    sessionStorage.setItem(this.SESSION_KEY, JSON.stringify(session));
    this.updateActivity();
    this.userIdentifier.set(identifier);
    this.isAuthenticated.set(true);
    this.registerActivityListeners();
    this.startCountdown();
  }

  /**
   * Ends the session. Called both by the citizen pressing Logout and by the inactivity timer, so it
   * works out which happened: a session still within its window was ended ON PURPOSE, an expired one
   * was dropped by the rule and the citizen is owed an explanation. That inference also covers
   * publicAuthGuard, which turns an expired session away without knowing why it is invalid.
   */
  logout() {
    if (sessionStorage.getItem(this.SESSION_KEY) && this.isExpired()) {
      this.markTimedOut();
    }
    this.clearSession();
  }

  /** Explicit sign-out: never leaves a timed-out notice behind, because nothing went wrong. */
  logoutExplicitly() {
    this.clearTimedOut();
    this.clearSession();
  }

  /** Dismisses the timed-out notice without logging in, e.g. the citizen closes the banner. */
  dismissTimedOut() {
    this.clearTimedOut();
  }

  private clearSession() {
    sessionStorage.removeItem(this.SESSION_KEY);
    sessionStorage.removeItem(this.ACTIVITY_KEY);
    this.isAuthenticated.set(false);
    this.userIdentifier.set('');
    this.remainingSeconds.set(0);
    this.stopCountdown();
    this.removeActivityListeners();
  }

  private markTimedOut() {
    localStorage.setItem(this.TIMED_OUT_KEY, '1');
    this.timedOut.set(true);
  }

  private clearTimedOut() {
    localStorage.removeItem(this.TIMED_OUT_KEY);
    this.timedOut.set(false);
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
      const remaining = this.remainingSeconds();
      if (remaining <= 60 && remaining > 0) {
        this.sessionWarning.set(true);
      } else {
        this.sessionWarning.set(false);
      }
      if (remaining <= 0) {
        this.sessionWarning.set(false);
        // Stated explicitly rather than left to logout()'s inference: remaining hits 0 up to a second
        // before isExpired() flips (one compares against TIMEOUT_MS, the other against TIMEOUT_MS-1000),
        // so the inference can miss the very timeout it exists to report.
        this.markTimedOut();
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
    /**
     * Interaction extends a LIVE session; it must never revive a dead one.
     *
     * This used to call updateActivity() unconditionally, which meant the first click on a form left
     * open past the window stamped last_activity to now and the session carried on as if nothing had
     * happened — the inactivity rule could be defeated indefinitely without ever logging in again.
     *
     * The countdown does not save us: setInterval is heavily throttled in a background tab, so the tab
     * the citizen returns to may not have ticked for a long while, and the click arrives first.
     */
    const handler = () => {
      if (this.isExpired()) {
        this.logout();
        return;
      }
      this.updateActivity();
    };
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
