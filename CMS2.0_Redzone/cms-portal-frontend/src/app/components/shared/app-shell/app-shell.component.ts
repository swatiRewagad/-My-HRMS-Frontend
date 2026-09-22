import { Component, computed, inject, input, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { LanguageSwitcherComponent } from '../language-switcher/language-switcher.component';
import { ToastHostComponent } from '../toast-host/toast-host.component';
import { SHELL_NAV, ShellNavItem } from './shell-nav';

/**
 * The common staff chrome: RBI masthead, unified role-filtered sidebar, page title, language
 * switcher and the single toast surface.
 *
 * Deliberately free of any module's data loading, so it can be lifted into a shared UI library when
 * the public and staff bundles are split. It takes content by slot and configuration by input; it
 * never imports from a sibling module folder.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, TranslatePipe, LanguageSwitcherComponent, ToastHostComponent],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.scss'
})
export class AppShellComponent {
  /** Translation key for the page heading. */
  titleKey = input.required<string>();

  /** Translation key describing the signed-in user's role, shown under their name. */
  roleKey = input<string | null>(null);

  private auth = inject(KeycloakAuthService);
  private router = inject(Router);

  collapsed = signal(false);

  readonly user = this.auth.currentUser;

  readonly navItems = computed<ShellNavItem[]>(() => {
    const roles = this.auth.getRoles();
    return SHELL_NAV.filter(item => item.roles.length === 0 || item.roles.some(r => roles.includes(r)));
  });

  readonly initial = computed(() => {
    const u = this.user();
    const source = u?.firstName || u?.username || '';
    return source ? source.charAt(0).toUpperCase() : 'U';
  });

  readonly displayName = computed(() => {
    const u = this.user();
    if (!u) return '';
    const full = `${u.firstName ?? ''} ${u.lastName ?? ''}`.trim();
    return full || u.username;
  });

  isActive(route: string): boolean {
    return this.router.url === route || this.router.url.startsWith(`${route}/`);
  }

  toggleSidebar(): void {
    this.collapsed.update(v => !v);
  }

  async logout(): Promise<void> {
    await this.auth.logout();
  }
}
