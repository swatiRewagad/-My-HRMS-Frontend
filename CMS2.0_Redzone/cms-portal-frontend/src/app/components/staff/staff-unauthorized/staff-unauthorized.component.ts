import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

@Component({
  selector: 'app-staff-unauthorized',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="container">
      <div class="card">
        <div class="icon">&#9888;</div>
        <h1>Access Denied</h1>
        <p>You do not have the required role to access this resource.</p>
        @if (auth.currentUser()) {
          <p class="roles">Your roles: <strong>{{ auth.currentUser()!.roles.join(', ') }}</strong></p>
        }
        <div class="actions">
          <button (click)="goBack()">Go to Dashboard</button>
          <button class="secondary" (click)="logout()">Logout & Switch User</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .container { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: #f4f6f9; }
    .card { background: white; padding: 40px; border-radius: 12px; text-align: center; max-width: 450px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
    .icon { font-size: 48px; margin-bottom: 16px; }
    h1 { color: #d32f2f; margin: 0 0 10px; }
    p { color: #666; font-size: 14px; }
    .roles { background: #fff3e0; padding: 8px 12px; border-radius: 4px; margin-top: 12px; }
    .actions { display: flex; gap: 12px; justify-content: center; margin-top: 20px; }
    button { padding: 10px 20px; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; background: #1a237e; color: white; }
    button.secondary { background: white; border: 1px solid #d32f2f; color: #d32f2f; }
  `]
})
export class StaffUnauthorizedComponent {
  auth = inject(KeycloakAuthService);
  private router = inject(Router);

  goBack() { this.router.navigate(['/staff/dashboard']); }
  async logout() { await this.auth.logout(); }
}
