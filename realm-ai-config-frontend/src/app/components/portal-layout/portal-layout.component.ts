import { Component, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-portal-layout',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './portal-layout.component.html',
  styleUrl: './portal-layout.component.scss',
})
export class PortalLayoutComponent {
  readonly auth = inject(AuthService);
  readonly collapsed = signal(false);

  readonly user = this.auth.currentUser;
  readonly realm = this.auth.currentRealm;
  readonly services = this.auth.configuredServices;

  readonly userInitials = computed(() => {
    const name = this.user()?.userName ?? '';
    return name.split(' ').map(n => n[0]).join('').toUpperCase();
  });

  readonly navItems = computed(() => {
    const items: { label: string; icon: string; route: string }[] = [
      { label: 'Service Portal', icon: 'pi pi-th-large', route: '/portal' },
    ];
    const svcs = this.services();
    if (svcs.some(s => s.id === 'ecm')) {
      items.push({ label: 'ECM - File Manager', icon: 'pi pi-folder-open', route: '/portal/ecm' });
    }
    return items;
  });

  toggleCollapse(): void {
    this.collapsed.update(v => !v);
  }

  logout(): void {
    this.auth.logout();
  }
}
