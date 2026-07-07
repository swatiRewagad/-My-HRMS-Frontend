import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

@Component({
  selector: 'app-re-login',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './re-login.component.html',
  styleUrl: './re-login.component.scss'
})
export class ReLoginComponent {
  private router = inject(Router);
  private auth = inject(KeycloakAuthService);

  loading = signal(false);
  error = signal('');
  entityName = signal('');
  userRole = signal('');

  async ngOnInit() {
    this.loading.set(true);
    const authenticated = await this.auth.init();
    if (authenticated) {
      const user = this.auth.currentUser();
      this.entityName.set(user?.firstName ? `${user.firstName} ${user.lastName}` : user?.username || '');
      const roles = this.auth.getRoles();
      this.userRole.set(roles.find(r => r.startsWith('RE_')) || roles[0] || '');
      this.router.navigate(['/re-portal/dashboard']);
    }
    this.loading.set(false);
  }

  async login() {
    this.loading.set(true);
    this.error.set('');
    try {
      const redirectUri = window.location.origin + '/re-portal/dashboard';
      await this.auth.loginWithRedirect(redirectUri);
    } catch (err) {
      this.error.set('Login failed. Please try again.');
      this.loading.set(false);
    }
  }
}
