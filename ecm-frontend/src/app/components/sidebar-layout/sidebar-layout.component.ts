import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';

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
    { label: 'Admin Settings', icon: 'pi pi-cog', route: '/admin' },
  ];

  collapsed = false;

  toggleSidebar() {
    this.collapsed = !this.collapsed;
  }
}
