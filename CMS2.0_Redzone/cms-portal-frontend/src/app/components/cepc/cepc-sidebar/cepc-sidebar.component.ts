import { Component, computed, inject, input, signal, effect } from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Tooltip } from 'primeng/tooltip';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { CepcContextService } from '../../../services/cepc-context.service';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

interface NavItem {
  labelKey: string;
  /** The English label, used verbatim whenever `labelKey` is not seeded. */
  label: string;
  icon: string;
  routerLink: string[];
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-cepc-sidebar',
  standalone: true,
  imports: [NgClass, RouterLink, RouterLinkActive, Tooltip, TranslateOrPipe],
  templateUrl: './cepc-sidebar.component.html',
  styleUrl: './cepc-sidebar.component.scss',
  animations: [
    trigger('sidebarWidth', [
      state('expanded', style({ width: '10.5rem' })),
      state('collapsed', style({ width: '3.75rem' })),
      transition('expanded <=> collapsed', animate('250ms cubic-bezier(0.4, 0, 0.2, 1)'))
    ]),
    trigger('fadeText', [
      state('visible', style({ opacity: 1, width: '*' })),
      state('hidden', style({ opacity: 0, width: '0', overflow: 'hidden' })),
      transition('visible => hidden', animate('150ms ease-out')),
      transition('hidden => visible', animate('200ms 80ms ease-in'))
    ])
  ]
})
export class CepcSidebarComponent {

  private readonly dept = inject(CepcContextService);

  readonly navItems = computed<NavItem[]>(() => [
    {
      labelKey: 'ui.cepc.nav.complaints',
      label: 'Complaints',
      icon: 'pi pi-th-large',
      routerLink: [this.dept.cfg().routePrefix]
    },
    {
      labelKey: 'ui.cepc.nav.reports',
      label: 'Reports',
      icon: 'pi pi-chart-bar',
      routerLink: ['/admin/dashboard'],
      queryParams: { department: this.dept.cfg().code }
    }
  ]);

  initialCollapsed = input(false);
  isCollapsed = signal(false);

  constructor() {
    effect(() => {
      this.isCollapsed.set(this.initialCollapsed());
    });
  }

  toggleSidebar(): void {
    this.isCollapsed.update(v => !v);
  }
}
