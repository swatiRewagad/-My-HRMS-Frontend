import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';

@Component({
  selector: 'app-crpc-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './crpc-login.component.html',
  styleUrl: './crpc-login.component.scss'
})
export class CrpcLoginComponent {

  private router = inject(Router);
  private auth = inject(KeycloakAuthService);

  username = '';
  password = '';
  loginError = '';
  loading = signal(false);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (authenticated) {
      this.routeByRole();
    }
  }

  async login() {
    this.loginError = '';
    this.loading.set(true);
    if (!this.auth.isAuthenticated()) {
      await this.auth.init();
    }
    await this.auth.loginWithRedirect(window.location.origin + '/crpc/login');
  }

  private routeByRole() {
    const roles = this.auth.getRoles();
    const user = this.auth.currentUser();

    if (user) {
      sessionStorage.setItem('crpc_user', JSON.stringify({
        id: user.username,
        name: `${user.firstName} ${user.lastName}`,
        role: roles.find(r => ['DEO', 'REVIEWER', 'CRPC_HEAD', 'CRPC_ADMIN', 'CRPC_INCHARGE', 'TOLL_FREE_HELPDESK'].includes(r)) || 'DEO',
        username: user.username,
        loginTime: new Date().toISOString()
      }));
    }

    if (roles.includes('REVIEWER')) {
      this.router.navigate(['/crpc/reviewer']);
    } else {
      this.router.navigate(['/crpc/home']);
    }
  }
}
