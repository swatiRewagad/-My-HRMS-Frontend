import { Component, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Tooltip } from 'primeng/tooltip';
import { animate, state, style, transition, trigger } from '@angular/animations';

interface NavItem {
  label: string;
  icon: string;
  routerLink: string[];
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-rbio-sidebar',
  standalone: true,
  imports: [NgClass, RouterLink, RouterLinkActive, Tooltip],
  templateUrl: './rbio-sidebar.component.html',
  styleUrl: './rbio-sidebar.component.scss',
  animations: [
    trigger('sidebarWidth', [
      state('expanded', style({ width: '16rem' })),
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
export class RbioSidebarComponent {

  readonly navItems: NavItem[] = [
    {
      label: 'Complaints',
      icon: 'pi pi-th-large',
      routerLink: ['/rbio']
    },
    {
      label: 'Reports',
      icon: 'pi pi-chart-bar',
      routerLink: ['/admin/dashboard'],
      queryParams: { department: 'RBIO' }
    },
    {
      label: 'Appeals and Representations',
      icon: 'pi pi-file',
      routerLink: ['/admin/dashboard'],
      queryParams: { department: 'RBIO' }
    }
  ];

  isCollapsed = signal(false);

  toggleSidebar(): void {
    this.isCollapsed.update(v => !v);
  }
}
