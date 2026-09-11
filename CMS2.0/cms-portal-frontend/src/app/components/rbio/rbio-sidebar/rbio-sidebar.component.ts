import { Component, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TooltipModule } from 'primeng/tooltip';
import { ButtonModule } from 'primeng/button';

interface NavItem {
  label: string;
  icon: string;
  routerLink: string[];
  queryParams?: Record<string, string>;
}

@Component({
  selector: 'app-rbio-sidebar',
  standalone: true,
  imports: [NgClass, RouterLink, RouterLinkActive,
    TooltipModule, ButtonModule],
  templateUrl: './rbio-sidebar.component.html',
  styleUrl: './rbio-sidebar.component.scss',
})
export class RbioSidebarComponent {

  readonly navItems: NavItem[] = [
    {
      label: 'Complaints',
      icon: 'pi pi-list',
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
      icon: 'pi pi-chart-bar',
      routerLink: ['/admin/dashboard'],
      queryParams: { department: 'RBIO' }
    }
  ];

  isCollapsed = signal<boolean>(false);

  toggleSidebar(): void {
    this.isCollapsed.update(state => !state);
  }
}
