import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-sidebar-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './sidebar-layout.component.html',
  styleUrl: './sidebar-layout.component.scss',
})
export class SidebarLayoutComponent {
  collapsed = signal(false);

  navItems = [
    { label: 'Dashboard', icon: 'pi pi-th-large', route: '/dashboard' },
    { label: 'Realm Configuration', icon: 'pi pi-sliders-h', route: '/config' },
    { label: 'Service Registry', icon: 'pi pi-server', route: '/services' },
  ];

  bottomItems = [
    { label: 'About', icon: 'pi pi-info-circle', route: '/about' },
  ];

  toggleCollapse() {
    this.collapsed.update(v => !v);
  }
}
