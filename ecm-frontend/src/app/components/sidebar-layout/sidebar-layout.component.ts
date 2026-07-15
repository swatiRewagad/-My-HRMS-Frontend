import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { KeycloakService } from '../../services/keycloak.service';

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './sidebar-layout.component.html',
  styleUrl: './sidebar-layout.component.scss',
})
export class SidebarLayoutComponent {
  navItems = [
    { label: 'Dashboard', icon: 'pi pi-th-large', route: '/dashboard' },
    { label: 'File Manager', icon: 'pi pi-folder', route: '/files' },
    { label: 'Upload', icon: 'pi pi-cloud-upload', route: '/upload' },
    { label: 'Shared with Me', icon: 'pi pi-share-alt', route: '/shared' },
  ];

  collapsed = false;

  constructor(public keycloak: KeycloakService) {}

  get userInitials(): string {
    const name = this.keycloak.fullName || this.keycloak.username;
    return name.split(' ').map(n => n[0]).join('').toUpperCase().substring(0, 2);
  }

  toggleSidebar() {
    this.collapsed = !this.collapsed;
  }

  logout() {
    this.keycloak.logout();
  }
}
