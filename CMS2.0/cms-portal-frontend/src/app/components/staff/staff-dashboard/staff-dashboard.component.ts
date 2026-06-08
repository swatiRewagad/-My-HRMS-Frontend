import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, NavigationEnd } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { Subscription, filter } from 'rxjs';
import { KeycloakAuthService, StaffUser } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-staff-dashboard',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="dashboard-container">
      <header class="topbar">
        <div class="topbar-left">
          <h2>CMS Staff Portal</h2>
        </div>
        <div class="topbar-right">
          @if (user()) {
            <span class="user-info">
              <span class="dept-badge" [class]="user()!.department.toLowerCase()">{{ user()!.department }}</span>
              {{ user()!.firstName }} {{ user()!.lastName }}
            </span>
          }
          <button class="logout-btn" (click)="logout()">Logout</button>
        </div>
      </header>

      <main class="content">
        @if (user()) {
          <div class="welcome-section">
            <h1>Welcome, {{ user()!.firstName }}!</h1>
            <p>Role: <strong>{{ user()!.roles.join(', ') }}</strong></p>
            <p>Department: <strong>{{ user()!.department }}</strong></p>
            <p>Email: {{ user()!.email }}</p>
          </div>

          <div class="action-grid">
            @if (user()!.department === 'RBIO') {
              <div class="action-card" (click)="navigate('/staff/rbio/tasks')">
                <h3>My Tasks</h3>
                <p>View assigned complaints pending your action</p>
                @if (pendingCount() > 0) { <span class="badge">{{ pendingCount() }}</span> }
              </div>
              <div class="action-card" (click)="navigate('/staff/rbio/history')">
                <h3>Completed</h3>
                <p>View complaints you've processed</p>
                @if (completedCount() > 0) { <span class="badge completed">{{ completedCount() }}</span> }
              </div>
              @if (auth.hasAnyRole(['RBIO_SUPERVISOR', 'RBIO_ADJUDICATOR'])) {
                <div class="action-card" (click)="navigate('/staff/rbio/escalations')">
                  <h3>Escalations</h3>
                  <p>Complaints escalated for your review</p>
                </div>
              }
            }

            @if (user()!.department === 'CEPC') {
              <div class="action-card" (click)="navigate('/staff/cepc/tasks')">
                <h3>My Tasks</h3>
                <p>View assigned complaints pending your action</p>
                @if (pendingCount() > 0) { <span class="badge">{{ pendingCount() }}</span> }
              </div>
              <div class="action-card" (click)="navigate('/staff/cepc/history')">
                <h3>Completed</h3>
                <p>View complaints you've processed</p>
                @if (completedCount() > 0) { <span class="badge completed">{{ completedCount() }}</span> }
              </div>
              @if (auth.hasAnyRole(['CEPC_SUPERVISOR', 'CEPC_ADJUDICATOR'])) {
                <div class="action-card" (click)="navigate('/staff/cepc/escalations')">
                  <h3>Escalations</h3>
                  <p>Complaints escalated for your review</p>
                </div>
              }
            }

            @if (user()!.department === 'CRPC') {
              <div class="action-card" (click)="navigate('/crpc/home')">
                <h3>CRPC Dashboard</h3>
                <p>Go to CRPC data entry / review</p>
              </div>
            }

            @if (user()!.department === 'ADMIN') {
              <div class="action-card" (click)="navigate('/admin/dashboard')">
                <h3>Admin Dashboard</h3>
                <p>System monitoring and management</p>
              </div>
              <div class="action-card" (click)="navigate('/admin/rules')">
                <h3>Rules Engine</h3>
                <p>Manage routing and assignment rules</p>
              </div>
            }
          </div>

          <div class="token-section">
            <h3>Session Info</h3>
            <div class="token-info">
              <p><strong>Token valid:</strong> Yes</p>
              <p><strong>Username:</strong> {{ user()!.username }}</p>
              <p><strong>Realm roles:</strong> {{ user()!.roles.join(', ') }}</p>
            </div>
            <button class="refresh-btn" (click)="refreshToken()">Refresh Token</button>
          </div>
        }
      </main>
    </div>
  `,
  styles: [`
    .dashboard-container { min-height: 100vh; background: #f4f6f9; }
    .topbar {
      background: #1a237e; color: white; padding: 12px 24px;
      display: flex; justify-content: space-between; align-items: center;
      box-shadow: 0 2px 8px rgba(0,0,0,0.15);
    }
    .topbar h2 { margin: 0; font-size: 18px; }
    .topbar-right { display: flex; align-items: center; gap: 16px; }
    .user-info { display: flex; align-items: center; gap: 8px; font-size: 14px; }
    .dept-badge {
      padding: 2px 8px; border-radius: 4px; font-weight: 600;
      font-size: 11px; color: white;
    }
    .dept-badge.rbio { background: #2e7d32; }
    .dept-badge.cepc { background: #1565c0; }
    .dept-badge.crpc { background: #e65100; }
    .dept-badge.admin { background: #6a1b9a; }
    .dept-badge.unknown { background: #757575; }
    .logout-btn {
      padding: 6px 16px; border: 1px solid rgba(255,255,255,0.5); border-radius: 4px;
      background: transparent; color: white; cursor: pointer; font-size: 13px;
    }
    .logout-btn:hover { background: rgba(255,255,255,0.1); }
    .content { max-width: 1000px; margin: 0 auto; padding: 30px 20px; }
    .welcome-section {
      background: white; padding: 24px; border-radius: 8px;
      margin-bottom: 24px; box-shadow: 0 2px 4px rgba(0,0,0,0.05);
    }
    .welcome-section h1 { margin: 0 0 10px; color: #1a237e; font-size: 22px; }
    .welcome-section p { margin: 4px 0; color: #555; font-size: 14px; }
    .action-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 16px; margin-bottom: 24px; }
    .action-card {
      background: white; padding: 20px; border-radius: 8px; cursor: pointer;
      border: 1px solid #e0e0e0; transition: all 0.2s; position: relative;
    }
    .action-card:hover { border-color: #1a237e; box-shadow: 0 4px 12px rgba(26,35,126,0.1); transform: translateY(-2px); }
    .action-card h3 { margin: 0 0 8px; color: #1a237e; font-size: 16px; }
    .action-card p { margin: 0; color: #666; font-size: 13px; }
    .action-card .badge {
      position: absolute; top: 12px; right: 12px;
      background: #d32f2f; color: white; min-width: 24px; height: 24px; padding: 0 6px;
      border-radius: 12px; display: flex; align-items: center; justify-content: center;
      font-size: 12px; font-weight: 600;
    }
    .action-card .badge.completed { background: #2e7d32; }
    .token-section {
      background: white; padding: 20px; border-radius: 8px;
      border: 1px solid #e0e0e0;
    }
    .token-section h3 { margin: 0 0 12px; color: #444; font-size: 14px; text-transform: uppercase; }
    .token-info p { margin: 4px 0; font-size: 13px; color: #555; }
    .refresh-btn {
      margin-top: 12px; padding: 6px 16px; border: 1px solid #1a237e;
      border-radius: 4px; background: white; color: #1a237e; cursor: pointer;
      font-size: 13px;
    }
    .refresh-btn:hover { background: #e8eaf6; }
  `]
})
export class StaffDashboardComponent implements OnInit, OnDestroy {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);
  private http = inject(HttpClient);
  private routeSub?: Subscription;

  user = this.auth.currentUser;
  pendingCount = signal(0);
  completedCount = signal(0);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    this.loadCounts();

    this.routeSub = this.router.events
      .pipe(filter(e => e instanceof NavigationEnd))
      .subscribe((e) => {
        if ((e as NavigationEnd).urlAfterRedirects === '/staff/dashboard') {
          this.loadCounts();
        }
      });
  }

  ngOnDestroy() {
    this.routeSub?.unsubscribe();
  }

  private loadCounts() {
    const dept = this.auth.currentUser()?.department?.toLowerCase();
    if (!dept || dept === 'unknown') return;

    const role = this.auth.getRoles().find(r => r.startsWith(dept.toUpperCase() + '_')) || '';

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/${dept}/tasks?role=${role}`)
      .subscribe({ next: (res) => this.pendingCount.set(res?.data?.length || 0) });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/${dept}/completed`)
      .subscribe({ next: (res) => this.completedCount.set(res?.data?.length || 0) });
  }

  navigate(path: string) {
    this.router.navigate([path]);
  }

  async logout() {
    await this.auth.logout();
  }

  async refreshToken() {
    await this.auth.refreshToken();
  }
}
