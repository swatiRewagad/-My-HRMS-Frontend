import { Routes } from '@angular/router';
import { authGuard, loginGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [loginGuard],
    loadComponent: () =>
      import('./components/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'portal',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./components/portal-layout/portal-layout.component').then(m => m.PortalLayoutComponent),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./components/realm-portal/realm-portal.component').then(m => m.RealmPortalComponent),
      },
      {
        path: 'ecm',
        loadComponent: () =>
          import('./components/ecm-portal/ecm-portal.component').then(m => m.EcmPortalComponent),
      },
    ],
  },
  {
    path: '',
    loadComponent: () =>
      import('./components/sidebar-layout/sidebar-layout.component').then(
        (m) => m.SidebarLayoutComponent
      ),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./components/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent
          ),
      },
      {
        path: 'config',
        loadComponent: () =>
          import('./components/wizard-layout/wizard-layout.component').then(
            (m) => m.WizardLayoutComponent
          ),
      },
      {
        path: 'services',
        loadComponent: () =>
          import('./components/service-registry/service-registry.component').then(
            (m) => m.ServiceRegistryComponent
          ),
      },
      {
        path: 'services/register',
        loadComponent: () =>
          import('./components/register-service/register-service.component').then(
            (m) => m.RegisterServiceComponent
          ),
      },
    ],
  },
];
