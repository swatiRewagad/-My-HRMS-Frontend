import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./components/sidebar-layout/sidebar-layout.component').then(m => m.SidebarLayoutComponent),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', loadComponent: () => import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'files', loadComponent: () => import('./components/file-manager/file-manager.component').then(m => m.FileManagerComponent) },
      { path: 'upload', loadComponent: () => import('./components/file-upload/file-upload.component').then(m => m.FileUploadComponent) },
      { path: 'shared', loadComponent: () => import('./components/shared-files/shared-files.component').then(m => m.SharedFilesComponent) },
    ],
  },
];
