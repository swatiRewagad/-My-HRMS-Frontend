import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet, RouterLink, RouterLinkActive, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-re-layout',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './re-layout.component.html',
  styleUrl: './re-layout.component.scss'
})
export class ReLayoutComponent implements OnInit {
  private router = inject(Router);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  entityName = signal('');
  roleBadge = signal('');
  pendingCount = signal(0);
  sidebarCollapsed = signal(false);
  mobileMenuOpen = signal(false);
  notificationCount = signal(0);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/re-portal/login']);
      return;
    }

    const user = this.auth.currentUser();
    this.entityName.set(user?.firstName ? `${user.firstName} ${user.lastName}` : user?.username || 'RE User');

    const roles = this.auth.getRoles();
    if (roles.includes('RE_NODAL_OFFICER')) {
      this.roleBadge.set('Nodal Officer');
    } else if (roles.includes('RE_PNO')) {
      this.roleBadge.set('Principal Nodal Officer');
    } else if (roles.includes('RE_ADMIN')) {
      this.roleBadge.set('Admin');
    } else {
      this.roleBadge.set('RE User');
    }

    this.loadPendingCount();
  }

  loadPendingCount() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/dashboard`).subscribe({
      next: (res) => {
        this.pendingCount.set(res?.data?.pendingResponse || 0);
        this.notificationCount.set(res?.data?.pendingResponse || 0);
      },
      error: () => {}
    });
  }

  toggleSidebar() {
    this.sidebarCollapsed.set(!this.sidebarCollapsed());
  }

  toggleMobileMenu() {
    this.mobileMenuOpen.set(!this.mobileMenuOpen());
  }

  async logout() {
    await this.auth.logout();
    this.router.navigate(['/re-portal/login']);
  }
}
