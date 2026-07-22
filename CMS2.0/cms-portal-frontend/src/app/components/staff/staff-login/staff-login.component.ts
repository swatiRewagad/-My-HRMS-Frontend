import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

@Component({
  selector: 'app-staff-login',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="login-container">
      <div class="login-card">
        <div class="logo-section">
          <img src="assets/rbi-logo.png" alt="RBI" class="logo" onerror="this.style.display='none'">
          <h1>CMS Staff Portal</h1>
          <p class="subtitle">Complaint Management System - Internal Access</p>
        </div>

        <div class="info-section">
          <h3>Department Access</h3>
          <div class="dept-grid">
            <div class="dept-card">
              <span class="dept-badge rbio">RBIO</span>
              <span>Officer, Supervisor, Conciliator, Adjudicator</span>
            </div>
            <div class="dept-card">
              <span class="dept-badge cepc">CEPC</span>
              <span>Officer, Supervisor, Conciliator, Adjudicator</span>
            </div>
            <div class="dept-card">
              <span class="dept-badge crpc">CRPC</span>
              <span>DEO, Reviewer, Head, Admin, Incharge</span>
            </div>
            <div class="dept-card">
              <span class="dept-badge admin">ADMIN</span>
              <span>System Administrator</span>
            </div>
          </div>
        </div>

        <button class="login-btn" (click)="login()" [disabled]="loading">
          @if (loading) {
            <span class="spinner"></span> Redirecting to SSO...
          } @else {
            Sign in with Keycloak SSO
          }
        </button>

        @if (error) {
          <div class="error-msg">{{ error }}</div>
        }

        <p class="help-text">
          You will be redirected to the secure authentication portal.
          Contact IT Admin for access credentials.
        </p>
      </div>
    </div>
  `,
  styles: [`
    .login-container {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background: linear-gradient(135deg, #1a237e 0%, #283593 50%, #3949ab 100%);
      padding: 20px;
    }
    .login-card {
      background: white;
      border-radius: 12px;
      padding: 40px;
      max-width: 500px;
      width: 100%;
      box-shadow: 0 20px 60px rgba(0,0,0,0.3);
    }
    .logo-section { text-align: center; margin-bottom: 30px; }
    .logo { width: 60px; margin-bottom: 10px; }
    .logo-section h1 { font-size: 24px; color: #1a237e; margin: 0; }
    .subtitle { color: #666; font-size: 14px; margin-top: 5px; }
    .info-section { margin-bottom: 30px; }
    .info-section h3 { font-size: 14px; color: #444; margin-bottom: 12px; text-transform: uppercase; letter-spacing: 0.5px; }
    .dept-grid { display: flex; flex-direction: column; gap: 8px; }
    .dept-card {
      display: flex; align-items: center; gap: 12px;
      padding: 8px 12px; border-radius: 6px; background: #f5f5f5;
      font-size: 13px; color: #555;
    }
    .dept-badge {
      padding: 2px 8px; border-radius: 4px; font-weight: 600;
      font-size: 11px; color: white; min-width: 50px; text-align: center;
    }
    .dept-badge.rbio { background: #2e7d32; }
    .dept-badge.cepc { background: #1565c0; }
    .dept-badge.crpc { background: #e65100; }
    .dept-badge.admin { background: #6a1b9a; }
    .login-btn {
      width: 100%; padding: 14px; border: none; border-radius: 8px;
      background: #1a237e; color: white; font-size: 16px; font-weight: 600;
      cursor: pointer; transition: background 0.2s;
      display: flex; align-items: center; justify-content: center; gap: 8px;
    }
    .login-btn:hover:not(:disabled) { background: #283593; }
    .login-btn:disabled { opacity: 0.7; cursor: not-allowed; }
    .spinner {
      width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.3);
      border-top-color: white; border-radius: 50%;
      animation: spin 0.8s linear infinite; display: inline-block;
    }
    @keyframes spin { to { transform: rotate(360deg); } }
    .error-msg { background: #fbe9e7; color: #d32f2f; padding: 10px; border-radius: 6px; margin-top: 15px; font-size: 13px; text-align: center; }
    .help-text { text-align: center; color: #999; font-size: 12px; margin-top: 15px; }
  `]
})
export class StaffLoginComponent implements OnInit {
  private auth = inject(KeycloakAuthService);
  private router = inject(Router);

  loading = false;
  error = '';

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (authenticated) {
      this.router.navigate(['/staff/dashboard']);
    }
  }

  async login() {
    this.loading = true;
    this.error = '';
    try {
      await this.auth.loginWithRedirect(window.location.origin + '/staff/dashboard');
    } catch (err) {
      this.error = 'Failed to connect to authentication server. Please try again.';
      this.loading = false;
    }
  }
}
