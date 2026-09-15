import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';

@Component({
  selector: 'app-session-timeout',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (showWarning()) {
      <div class="timeout-overlay">
        <div class="timeout-modal">
          <div class="timeout-icon">&#9203;</div>
          <h3>Session Expiring Soon</h3>
          <p>Your session will expire in <strong>{{ remainingSeconds() }}</strong> seconds.</p>
          <p class="sub">Any unsaved changes will be lost.</p>
          <div class="timeout-actions">
            <button class="btn-primary" (click)="extendSession()">Continue Session</button>
            <button class="btn-secondary" (click)="logout()">Logout</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .timeout-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); z-index: 10000;
                       display: flex; align-items: center; justify-content: center; }
    .timeout-modal { background: #fff; border-radius: 16px; padding: 32px; width: 400px; text-align: center; }
    .timeout-icon { font-size: 48px; margin-bottom: 12px; }
    .timeout-modal h3 { margin: 0 0 8px; font-size: 20px; font-weight: 700; color: #1e293b; }
    .timeout-modal p { margin: 0 0 4px; color: #475569; }
    .timeout-modal .sub { font-size: 13px; color: #94a3b8; margin-bottom: 20px; }
    .timeout-actions { display: flex; gap: 12px; justify-content: center; }
    .btn-primary { background: #3b82f6; color: #fff; border: none; padding: 10px 20px; border-radius: 8px;
                   font-size: 14px; font-weight: 600; cursor: pointer; }
    .btn-secondary { background: #e2e8f0; color: #334155; border: none; padding: 10px 20px; border-radius: 8px;
                     font-size: 14px; cursor: pointer; }
  `]
})
export class SessionTimeoutComponent implements OnInit, OnDestroy {
  private router = inject(Router);

  showWarning = signal(false);
  remainingSeconds = signal(300);

  private sessionDuration = 15 * 60 * 1000; // 15 minutes
  private warningBefore = 5 * 60 * 1000; // show warning 5 minutes before
  private idleTimer: any;
  private countdownTimer: any;
  private lastActivity = Date.now();

  private readonly activityEvents = ['mousedown', 'keydown', 'scroll', 'touchstart'];

  ngOnInit(): void {
    this.activityEvents.forEach(e => document.addEventListener(e, this.onActivity));
    this.startIdleCheck();
  }

  ngOnDestroy(): void {
    this.activityEvents.forEach(e => document.removeEventListener(e, this.onActivity));
    clearInterval(this.idleTimer);
    clearInterval(this.countdownTimer);
  }

  private onActivity = (): void => {
    this.lastActivity = Date.now();
    if (this.showWarning()) {
      this.showWarning.set(false);
      clearInterval(this.countdownTimer);
    }
  };

  private startIdleCheck(): void {
    this.idleTimer = setInterval(() => {
      const idle = Date.now() - this.lastActivity;
      const timeUntilExpiry = this.sessionDuration - idle;

      if (timeUntilExpiry <= 0) {
        this.logout();
      } else if (timeUntilExpiry <= this.warningBefore && !this.showWarning()) {
        this.showWarning.set(true);
        this.remainingSeconds.set(Math.ceil(timeUntilExpiry / 1000));
        this.startCountdown();
      }
    }, 1000);
  }

  private startCountdown(): void {
    this.countdownTimer = setInterval(() => {
      this.remainingSeconds.update(s => s - 1);
      if (this.remainingSeconds() <= 0) {
        this.logout();
      }
    }, 1000);
  }

  extendSession(): void {
    this.lastActivity = Date.now();
    this.showWarning.set(false);
    clearInterval(this.countdownTimer);
  }

  logout(): void {
    this.showWarning.set(false);
    clearInterval(this.idleTimer);
    clearInterval(this.countdownTimer);
    this.router.navigate(['/crpc/login']);
  }
}
